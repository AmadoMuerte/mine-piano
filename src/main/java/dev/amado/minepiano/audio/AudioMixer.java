package dev.amado.minepiano.audio;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLongArray;

/** One-thread 48 kHz mono mixer. */
public final class AudioMixer {
    public static final int SAMPLE_RATE = 48_000;
    public static final int BLOCK_FRAMES = 960;
    private static final int MAX_VOICES = 64;
    private static final int RETIRED_VOICES = 128;
    private static final int STEAL_FADE_FRAMES = 192; // 4 ms
    private static final int PANIC_FADE_FRAMES = 480; // 10 ms
    private static final int CONSUMER_TAIL_BLOCKS = 25;

    private final SoundBank soundBank;
    private final LocalOutput localOutput;
    private final PianoEngineImpl.CommandQueue commands;
    private final AtomicLongArray logicalNotes;
    private final AtomicBoolean allNotesOffPending;
    private final Thread thread;
    private final float[] mix = new float[BLOCK_FRAMES];
    private final float[] scratch = new float[BLOCK_FRAMES];
    private final short[] pcm = new short[BLOCK_FRAMES];
    private final Voice[] voices = new Voice[MAX_VOICES];
    private final Voice[] retired = new Voice[RETIRED_VOICES];
    private final int[] notes = new int[MAX_VOICES];
    private final int[] pendingNotes = new int[MAX_VOICES];
    private final int[] pendingVelocities = new int[MAX_VOICES];
    private final boolean[] held = new boolean[MAX_VOICES];
    private final boolean[] deferred = new boolean[MAX_VOICES];
    private final boolean[] releasing = new boolean[MAX_VOICES];
    private final boolean[] stealing = new boolean[MAX_VOICES];
    private final boolean[] panic = new boolean[MAX_VOICES];
    private final long[] ages = new long[MAX_VOICES];
    private final float[] amplitudes = new float[MAX_VOICES];

    private volatile FrameConsumer[] consumers = new FrameConsumer[0];
    private volatile boolean running;
    private boolean started;
    private boolean sustain;
    private float masterGain = 1.0f;
    private long nextAge;
    private int tailBlocks;

    AudioMixer(SoundBank soundBank, LocalOutput localOutput,
               PianoEngineImpl.CommandQueue commands, AtomicLongArray logicalNotes,
               AtomicBoolean allNotesOffPending) {
        this.soundBank = soundBank;
        this.localOutput = localOutput;
        this.commands = commands;
        this.logicalNotes = logicalNotes;
        this.allNotesOffPending = allNotesOffPending;
        Arrays.fill(pendingNotes, -1);
        thread = new Thread(this::run, "MinePiano-Audio");
        thread.setDaemon(true);
    }

    synchronized void addFrameConsumer(FrameConsumer consumer) {
        FrameConsumer[] current = consumers;
        for (FrameConsumer existing : current) if (existing == consumer) return;
        FrameConsumer[] updated = Arrays.copyOf(current, current.length + 1);
        updated[current.length] = consumer;
        consumers = updated;
    }

    public synchronized void start() {
        if (started) return;
        started = true;
        running = true;
        thread.start();
    }

    public void stop() {
        running = false;
        localOutput.close();
        if (Thread.currentThread() == thread) return;
        try {
            thread.join(1_000L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void run() {
        while (running) renderBlock();
    }

    void renderBlock() {
        drainCommands();
        Arrays.fill(mix, 0.0f);
        renderRetired();
        boolean rendered = false;
        for (int slot = 0; slot < MAX_VOICES; slot++) {
            if (voices[slot] == null && pendingNotes[slot] >= 0) tryStart(slot);
            Voice voice = voices[slot];
            if (voice == null) continue;
            rendered = true;
            if (panic[slot]) renderFading(slot, PANIC_FADE_FRAMES, true);
            else if (stealing[slot]) renderFading(slot, STEAL_FADE_FRAMES, false);
            else renderNormal(slot);
        }

        for (int i = 0; i < BLOCK_FRAMES; i++) {
            float value = Math.max(-1.0f, Math.min(1.0f, mix[i] * masterGain));
            pcm[i] = (short) Math.round(value * (value < 0.0f ? 32768.0f : 32767.0f));
        }

        if (rendered) tailBlocks = CONSUMER_TAIL_BLOCKS;
        boolean notifyConsumers = rendered || tailBlocks > 0;
        if (!rendered && tailBlocks > 0) tailBlocks--;
        if (notifyConsumers) {
            FrameConsumer[] blockConsumers = consumers;
            for (FrameConsumer consumer : blockConsumers) {
                try {
                    consumer.accept(pcm, BLOCK_FRAMES);
                } catch (RuntimeException ignored) {
                    // A remote sink must not stop local audio or other consumers.
                }
            }
        }
        localOutput.write(pcm, BLOCK_FRAMES);
    }

    private void drainCommands() {
        for (int count = 0; count < 4096; count++) {
            long command = commands.poll();
            if (command == 0L) return;
            int type = (int) (command >>> 56);
            long value = command & 0x00ff_ffff_ffff_ffffL;
            switch (type) {
                case 1 -> noteOn((int) value & 127, (int) (value >>> 7) & 127);
                case 2 -> noteOff((int) value & 127);
                case 3 -> setSustain(value != 0L);
                case 4 -> allNotesOff();
                case 5 -> masterGain = Float.intBitsToFloat((int) value);
                default -> { }
            }
        }
    }

    private void noteOn(int midi, int velocity) {
        setLogical(midi, true);
        int empty = emptySlot();
        if (empty >= 0) {
            pendingNotes[empty] = midi;
            pendingVelocities[empty] = velocity;
            if (tryStart(empty)) return;
            pendingNotes[empty] = -1;
        }
        int victim = stealCandidate();
        if (victim < 0) {
            if (empty >= 0) {
                pendingNotes[empty] = midi;
                pendingVelocities[empty] = velocity;
            }
            return;
        }
        pendingNotes[victim] = midi;
        pendingVelocities[victim] = velocity;
        stealing[victim] = true;
        held[victim] = false;
        deferred[victim] = false;
        voices[victim].release();
    }

    private void noteOff(int midi) {
        setLogical(midi, false);
        for (int slot = 0; slot < MAX_VOICES; slot++) {
            if (pendingNotes[slot] == midi) pendingNotes[slot] = -1;
            if (voices[slot] == null || notes[slot] != midi || !held[slot]) continue;
            held[slot] = false;
            if (sustain) deferred[slot] = true;
            else {
                releasing[slot] = true;
                voices[slot].release();
            }
        }
    }

    private void setSustain(boolean on) {
        sustain = on;
        if (on) return;
        for (int slot = 0; slot < MAX_VOICES; slot++) {
            if (!deferred[slot] || voices[slot] == null) continue;
            deferred[slot] = false;
            releasing[slot] = true;
            voices[slot].release();
        }
    }

    private void allNotesOff() {
        sustain = false;
        logicalNotes.set(0, 0L);
        logicalNotes.set(1, 0L);
        for (int slot = 0; slot < MAX_VOICES; slot++) {
            pendingNotes[slot] = -1;
            if (voices[slot] == null) continue;
            held[slot] = false;
            deferred[slot] = false;
            releasing[slot] = true;
            stealing[slot] = false;
            panic[slot] = true;
            voices[slot].release();
        }
        allNotesOffPending.set(false);
    }

    private void renderNormal(int slot) {
        Arrays.fill(scratch, 0.0f);
        boolean produced = voices[slot].renderAdd(scratch, BLOCK_FRAMES);
        float peak = 0.0f;
        for (int i = 0; i < BLOCK_FRAMES; i++) {
            float value = scratch[i];
            mix[i] += value;
            peak = Math.max(peak, Math.abs(value));
        }
        amplitudes[slot] = peak;
        if (!produced || voices[slot].isFinished()) clearSlot(slot);
    }

    private void renderFading(int slot, int fadeFrames, boolean discardPending) {
        Voice old = voices[slot];
        Arrays.fill(scratch, 0.0f);
        old.renderAdd(scratch, BLOCK_FRAMES);
        float peak = 0.0f;
        for (int i = 0; i < fadeFrames; i++) {
            float value = scratch[i] * (fadeFrames - 1 - i) / (fadeFrames - 1);
            mix[i] += value;
            peak = Math.max(peak, Math.abs(value));
        }
        amplitudes[slot] = peak;
        retire(old);
        voices[slot] = null;
        held[slot] = deferred[slot] = releasing[slot] = stealing[slot] = panic[slot] = false;
        if (discardPending) pendingNotes[slot] = -1;
        else if (tryStart(slot)) renderStartedVoice(slot, fadeFrames, BLOCK_FRAMES - fadeFrames);
    }

    private void renderStartedVoice(int slot, int offset, int frames) {
        Arrays.fill(scratch, 0, frames, 0.0f);
        boolean produced = voices[slot].renderAdd(scratch, frames);
        float peak = 0.0f;
        for (int i = 0; i < frames; i++) {
            float value = scratch[i];
            mix[offset + i] += value;
            peak = Math.max(peak, Math.abs(value));
        }
        amplitudes[slot] = peak;
        if (!produced || voices[slot].isFinished()) clearSlot(slot);
    }

    private void renderRetired() {
        for (int i = 0; i < retired.length; i++) {
            Voice voice = retired[i];
            if (voice == null) continue;
            Arrays.fill(scratch, 0.0f);
            voice.renderAdd(scratch, BLOCK_FRAMES);
            if (voice.isFinished()) retired[i] = null;
        }
    }

    private void retire(Voice voice) {
        if (voice.isFinished()) return;
        for (int i = 0; i < retired.length; i++) {
            if (retired[i] == null) {
                retired[i] = voice;
                return;
            }
        }
        // ponytail: retirement slots match the default bank's 128-voice pool; make capacity a SoundBank contract if larger banks arrive.
    }

    private boolean tryStart(int slot) {
        int midi = pendingNotes[slot];
        if (midi < 0) return false;
        try {
            Voice voice = soundBank.newVoice(midi, pendingVelocities[slot]);
            if (voice == null) throw new IllegalStateException("SoundBank returned null voice");
            voices[slot] = voice;
            notes[slot] = midi;
            held[slot] = true;
            deferred[slot] = releasing[slot] = stealing[slot] = panic[slot] = false;
            ages[slot] = nextAge++;
            amplitudes[slot] = 0.0f;
            pendingNotes[slot] = -1;
            return true;
        } catch (IllegalStateException exhausted) {
            return false;
        }
    }

    private int emptySlot() {
        for (int i = 0; i < MAX_VOICES; i++) {
            if (voices[i] == null && pendingNotes[i] < 0) return i;
        }
        return -1;
    }

    private int stealCandidate() {
        int best = -1;
        for (int i = 0; i < MAX_VOICES; i++) {
            if (voices[i] == null || stealing[i] || panic[i]) continue;
            if (best < 0
                    || releasing[i] && !releasing[best]
                    || releasing[i] == releasing[best] && amplitudes[i] < amplitudes[best]
                    || releasing[i] == releasing[best] && amplitudes[i] == amplitudes[best]
                    && ages[i] < ages[best]) best = i;
        }
        return best;
    }

    private void clearSlot(int slot) {
        voices[slot] = null;
        held[slot] = deferred[slot] = releasing[slot] = stealing[slot] = panic[slot] = false;
        amplitudes[slot] = 0.0f;
    }

    private void setLogical(int midi, boolean active) {
        int word = midi >>> 6;
        long bit = 1L << (midi & 63);
        for (;;) {
            long current = logicalNotes.get(word);
            long updated = active ? current | bit : current & ~bit;
            if (current == updated || logicalNotes.compareAndSet(word, current, updated)) return;
        }
    }

}
