package dev.amado.minepiano.audio.sf2;

import dev.amado.minepiano.audio.SoundBank;
import dev.amado.minepiano.audio.PianoPreset;
import dev.amado.minepiano.audio.Voice;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/** Preloaded SoundFont bank with allocation-free pooled voices. */
public final class Sf2SoundBank implements SoundBank {
    private static final String RESOURCE = "assets/minepiano/soundfont/piano.sf2";
    private static final int POOL_SIZE = 128;
    private final Sf2Zone[] zones;
    private final Sf2Voice[] voices;
    private final PianoPreset preset;

    public Sf2SoundBank() { this(0, 0, PianoPreset.REALISTIC); }

    public Sf2SoundBank(int bank, int program) {
        this(bank, program, PianoPreset.REALISTIC);
    }

    public Sf2SoundBank(int bank, int program, PianoPreset preset) {
        this(loadDefault(bank, program), preset);
    }

    public Sf2SoundBank(Path path) { this(path, 0, 0, PianoPreset.REALISTIC); }

    public Sf2SoundBank(Path path, int bank, int program) {
        this(path, bank, program, PianoPreset.REALISTIC);
    }

    public Sf2SoundBank(Path path, int bank, int program, PianoPreset preset) {
        this(parse(path, bank, program), preset);
    }

    private Sf2SoundBank(Sf2Parser.Parsed parsed, PianoPreset preset) {
        this(parsed.zones(), preset);
    }

    private Sf2SoundBank(Sf2Zone[] zones, PianoPreset preset) {
        this.zones = zones;
        this.preset = java.util.Objects.requireNonNull(preset);
        int maxLayers = 1;
        for (int key = 0; key < 128; key++) {
            for (int velocity = 0; velocity < 128; velocity++) {
                int layers = 0;
                for (Sf2Zone zone : zones) if (matches(zone, key, velocity)) layers++;
                maxLayers = Math.max(maxLayers, layers);
            }
        }
        voices = new Sf2Voice[POOL_SIZE];
        for (int i = 0; i < voices.length; i++) voices[i] = new Sf2Voice(maxLayers, preset);
    }

    /** Builds a fresh voice pool while sharing immutable decoded samples. */
    public Sf2SoundBank withPreset(PianoPreset preset) {
        if (preset.soundfontResource() != null) return new Sf2SoundBank(loadResource(
                preset.soundfontResource(), preset.bank(), preset.program()), preset);
        return new Sf2SoundBank(zones, preset);
    }

    public PianoPreset preset() { return preset; }

    @Override
    public Voice newVoice(int midi, int velocity) {
        if (midi < 0 || midi > 127 || velocity < 0 || velocity > 127) {
            throw new IllegalArgumentException("MIDI note and velocity must be in 0..127");
        }
        for (Sf2Voice voice : voices) {
            if (voice.isFinished()) {
                voice.start(zones, midi, velocity);
                return voice;
            }
        }
        // ponytail: fixed 128-voice pool; add deterministic voice stealing if real workloads exhaust it.
        throw new IllegalStateException("SoundFont voice pool exhausted");
    }

    public int sampleCount() {
        int count = 0;
        for (int zoneIndex = 0; zoneIndex < zones.length; zoneIndex++) {
            boolean seen = false;
            for (int i = 0; i < zoneIndex; i++) {
                if (zones[i].sample == zones[zoneIndex].sample) { seen = true; break; }
            }
            if (!seen) count++;
        }
        return count;
    }

    public int zoneCount() { return zones.length; }

    private static boolean matches(Sf2Zone zone, int midi, int velocity) {
        return midi >= zone.lowKey && midi <= zone.highKey
                && velocity >= zone.lowVelocity && velocity <= zone.highVelocity;
    }

    private static Sf2Parser.Parsed loadDefault(int bank, int program) {
        try (InputStream input = Sf2SoundBank.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing classpath resource " + RESOURCE);
            return new Sf2Parser().parse(input, bank, program);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load SoundFont", e);
        }
    }

    private static Sf2Parser.Parsed loadResource(String resource, int bank, int program) {
        try (InputStream input = Sf2SoundBank.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing classpath resource " + resource);
            return new Sf2Parser().parse(input, bank, program);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load SoundFont", e);
        }
    }

    private static Sf2Parser.Parsed parse(Path path, int bank, int program) {
        try {
            return new Sf2Parser().parse(path, bank, program);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot load SoundFont " + path, e);
        }
    }

    private static final class Sf2Voice implements Voice {
        private static final float RELEASE_FLOOR = 0.000015848932f; // -96 dB
        private static final int ATTACK = 0, HOLD = 1, DECAY = 2, SUSTAIN = 3, RELEASE = 4, DONE = 5;
        private final Sf2Zone[] activeZones;
        private final double[] phases;
        private final double[] steps;
        private final float[] envelopes;
        private final float[] releaseMultipliers;
        private final int[] stages;
        private final int[] stageFrames;
        private final int[] attackFrames;
        private final int[] releaseFrames;
        private final float[] sustainGains;
        private final float[] decayMultipliers;
        private final PianoPreset preset;
        private int layerCount;
        private float voiceGain;
        private float lowPassState;
        private volatile boolean released;
        private volatile boolean finished = true;

        Sf2Voice(int maxLayers, PianoPreset preset) {
            activeZones = new Sf2Zone[maxLayers];
            phases = new double[maxLayers];
            steps = new double[maxLayers];
            envelopes = new float[maxLayers];
            releaseMultipliers = new float[maxLayers];
            stages = new int[maxLayers];
            stageFrames = new int[maxLayers];
            attackFrames = new int[maxLayers];
            releaseFrames = new int[maxLayers];
            sustainGains = new float[maxLayers];
            decayMultipliers = new float[maxLayers];
            this.preset = preset;
        }

        void start(Sf2Zone[] zones, int midi, int velocity) {
            layerCount = 0;
            released = false;
            lowPassState = 0.0f;
            voiceGain = preset.gain() * (float) Math.pow(velocity / 127.0,
                    PianoPreset.DEFAULT_VELOCITY_EXPONENT);
            for (Sf2Zone zone : zones) {
                if (!matches(zone, midi, velocity)) continue;
                int i = layerCount++;
                activeZones[i] = zone;
                phases[i] = zone.sample.start;
                steps[i] = zone.sample.sampleRate / 48_000.0 * Math.pow(2.0,
                        (midi - zone.rootKey + zone.coarseTune
                                + (zone.fineTune + zone.sample.pitchCorrection
                                + preset.detuneCents() * (i % 2 == 0 ? -1.0 : 1.0)) / 100.0) / 12.0);
                envelopes[i] = 0.0f;
                attackFrames[i] = Math.max(1, Math.round(zone.attackFrames * preset.attackScale()));
                releaseFrames[i] = Math.max(1, Math.round(zone.releaseFrames * preset.releaseScale()));
                sustainGains[i] = Math.min(1.0f, zone.sustainGain * preset.sustainLevel());
                decayMultipliers[i] = zone.decayFrames == 0 || sustainGains[i] == 1.0f ? 1.0f
                        : (float) Math.pow(sustainGains[i], 1.0 / zone.decayFrames);
                stages[i] = ATTACK;
                stageFrames[i] = 0;
            }
            finished = layerCount == 0;
        }

        @Override
        public boolean renderAdd(float[] out, int frames) {
            if (frames < 0 || frames > out.length) throw new IllegalArgumentException("Invalid frame count");
            if (finished) return false;
            boolean any = false;
            for (int frame = 0; frame < frames; frame++) {
                float mixed = 0.0f;
                for (int layer = 0; layer < layerCount; layer++) {
                    if (stages[layer] == DONE) continue;
                    Sf2Zone zone = activeZones[layer];
                    Sf2Sample sample = zone.sample;
                    boolean looping = (zone.sampleModes & 1) != 0
                            && ((zone.sampleModes & 2) == 0 || !released);
                    double phase = phases[layer];
                    if (looping && sample.loopEnd > sample.loopStart + 1 && phase >= sample.loopEnd) {
                        phase = sample.loopStart + (phase - sample.loopEnd)
                                % (sample.loopEnd - sample.loopStart);
                    }
                    int index = (int) phase;
                    if (index < sample.start || index >= sample.end) {
                        stages[layer] = DONE;
                        continue;
                    }
                    int next = index + 1;
                    if (looping && next >= sample.loopEnd) next = sample.loopStart;
                    else if (next >= sample.end) next = index;
                    float fraction = (float) (phase - index);
                    float value = sample.pcm[index] + (sample.pcm[next] - sample.pcm[index]) * fraction;
                    mixed += value * envelopes[layer] * zone.gain * voiceGain;
                    phases[layer] = phase + steps[layer];
                    advanceEnvelope(layer, zone);
                    any = true;
                }
                if (preset.lowPass() > 0.0f) {
                    lowPassState += (mixed - lowPassState) * (1.0f - preset.lowPass());
                    mixed = lowPassState;
                }
                out[frame] = Math.max(-1.0f, Math.min(1.0f, out[frame] + mixed));
            }
            boolean allDone = true;
            for (int i = 0; i < layerCount; i++) if (stages[i] != DONE) { allDone = false; break; }
            if (allDone) finished = true;
            return any;
        }

        private void advanceEnvelope(int layer, Sf2Zone zone) {
            switch (stages[layer]) {
                case ATTACK -> {
                    envelopes[layer] = Math.min(1.0f, envelopes[layer] + 1.0f / attackFrames[layer]);
                    if (++stageFrames[layer] >= attackFrames[layer]) enter(layer, HOLD, 1.0f);
                }
                case HOLD -> {
                    if (++stageFrames[layer] >= zone.holdFrames) enter(layer, DECAY, 1.0f);
                }
                case DECAY -> {
                    envelopes[layer] *= decayMultipliers[layer];
                    if (++stageFrames[layer] >= zone.decayFrames) enter(layer, SUSTAIN, sustainGains[layer]);
                }
                case SUSTAIN -> { }
                case RELEASE -> {
                    envelopes[layer] *= releaseMultipliers[layer];
                    if (++stageFrames[layer] >= releaseFrames[layer]
                            || envelopes[layer] <= RELEASE_FLOOR * RELEASE_FLOOR) {
                        stages[layer] = DONE;
                        envelopes[layer] = 0.0f;
                    }
                }
                default -> { }
            }
            if (released && stages[layer] < RELEASE) beginRelease(layer);
        }

        private void enter(int layer, int stage, float envelope) {
            stages[layer] = stage;
            stageFrames[layer] = 0;
            envelopes[layer] = envelope;
        }

        private void beginRelease(int layer) {
            stages[layer] = RELEASE;
            stageFrames[layer] = 0;
            releaseMultipliers[layer] = (float) Math.pow(RELEASE_FLOOR, 1.0 / releaseFrames[layer]);
        }

        @Override
        public void release() {
            released = true;
        }

        @Override
        public boolean isFinished() { return finished; }
    }
}
