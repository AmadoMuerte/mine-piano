package dev.amado.minepiano.audio.sf2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Minimal RIFF SoundFont 2 parser for sample-based playback. */
public final class Sf2Parser {
    private static final int OUTPUT_RATE = 48_000;
    private static final int KEY_RANGE = 43;
    private static final int VEL_RANGE = 44;
    private static final int INSTRUMENT = 41;
    private static final int SAMPLE_ID = 53;
    private static final int ROOT_KEY = 58;
    private static final int COARSE_TUNE = 51;
    private static final int FINE_TUNE = 52;
    private static final int SAMPLE_MODES = 54;
    private static final int ATTACK = 34;
    private static final int HOLD = 35;
    private static final int DECAY = 36;
    private static final int SUSTAIN = 37;
    private static final int RELEASE = 38;
    private static final int ATTENUATION = 48;

    public Parsed parse(Path path) throws IOException {
        return parse(path, 0, 0);
    }

    public Parsed parse(Path path, int bank, int program) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return parse(input, bank, program);
        }
    }

    public Parsed parse(InputStream input) throws IOException {
        return parse(input, 0, 0);
    }

    public Parsed parse(InputStream input, int bank, int program) throws IOException {
        byte[] data = input.readAllBytes();
        require(data.length >= 12 && fourcc(data, 0).equals("RIFF") && fourcc(data, 8).equals("sfbk"),
                "Not a RIFF sfbk file");

        Chunk smpl = null;
        Chunk phdr = null, pbag = null, pgen = null, inst = null, ibag = null, igen = null, shdr = null;
        for (int p = 12; p + 8 <= data.length;) {
            int size = checkedSize(data, p + 4);
            int body = p + 8;
            require((long) body + size <= data.length, "Truncated RIFF chunk");
            String id = fourcc(data, p);
            if (id.equals("LIST") && size >= 4) {
                String list = fourcc(data, body);
                for (int q = body + 4, limit = body + size; q + 8 <= limit;) {
                    int childSize = checkedSize(data, q + 4);
                    int childBody = q + 8;
                    require((long) childBody + childSize <= limit, "Truncated LIST chunk");
                    Chunk chunk = new Chunk(childBody, childSize);
                    switch (fourcc(data, q)) {
                        case "smpl" -> { if (list.equals("sdta")) smpl = chunk; }
                        case "phdr" -> { if (list.equals("pdta")) phdr = chunk; }
                        case "pbag" -> { if (list.equals("pdta")) pbag = chunk; }
                        case "pgen" -> { if (list.equals("pdta")) pgen = chunk; }
                        case "inst" -> { if (list.equals("pdta")) inst = chunk; }
                        case "ibag" -> { if (list.equals("pdta")) ibag = chunk; }
                        case "igen" -> { if (list.equals("pdta")) igen = chunk; }
                        case "shdr" -> { if (list.equals("pdta")) shdr = chunk; }
                        default -> { /* INFO, pmod, imod and unknown chunks are not needed for rendering. */ }
                    }
                    q = childBody + childSize + (childSize & 1);
                }
            }
            p = body + size + (size & 1);
        }
        require(smpl != null && phdr != null && pbag != null && pgen != null && inst != null
                && ibag != null && igen != null && shdr != null, "Incomplete SoundFont tables");

        float[] pcm = decodePcm(data, smpl);
        Sf2Sample[] samples = readSamples(data, shdr, pcm);
        Header[] presets = readHeaders(data, phdr, 38, true);
        Header[] instruments = readHeaders(data, inst, 22, false);
        Bag[] presetBags = readBags(data, pbag);
        Bag[] instrumentBags = readBags(data, ibag);
        Generator[] presetGenerators = readGenerators(data, pgen);
        Generator[] instrumentGenerators = readGenerators(data, igen);

        int preset = -1;
        for (int i = 0; i < presets.length - 1; i++) {
            if (presets[i].bank == bank && presets[i].program == program) {
                preset = i;
                break;
            }
        }
        require(preset >= 0, "Preset bank " + bank + " program " + program + " not found");

        List<Sf2Zone> zones = new ArrayList<>();
        GeneratorSet presetGlobal = new GeneratorSet();
        for (int pb = presets[preset].bag; pb < presets[preset + 1].bag; pb++) {
            GeneratorSet localPreset = generators(presetBags, presetGenerators, pb);
            if (!localPreset.has(INSTRUMENT)) {
                presetGlobal = merge(presetGlobal, localPreset);
                continue;
            }
            GeneratorSet resolvedPreset = merge(presetGlobal, localPreset);
            int instrumentId = resolvedPreset.unsigned(INSTRUMENT);
            require(instrumentId < instruments.length - 1, "Invalid instrument index");
            GeneratorSet instrumentGlobal = new GeneratorSet();
            for (int ib = instruments[instrumentId].bag; ib < instruments[instrumentId + 1].bag; ib++) {
                GeneratorSet localInstrument = generators(instrumentBags, instrumentGenerators, ib);
                if (!localInstrument.has(SAMPLE_ID)) {
                    instrumentGlobal = merge(instrumentGlobal, localInstrument);
                    continue;
                }
                GeneratorSet resolvedInstrument = merge(instrumentGlobal, localInstrument);
                int sampleId = resolvedInstrument.unsigned(SAMPLE_ID);
                require(sampleId < samples.length, "Invalid sample index");
                Sf2Zone zone = makeZone(samples[sampleId], resolvedPreset, resolvedInstrument);
                if (zone.lowKey <= zone.highKey && zone.lowVelocity <= zone.highVelocity) zones.add(zone);
            }
        }
        require(!zones.isEmpty(), "Preset contains no playable zones");
        return new Parsed(samples, zones.toArray(Sf2Zone[]::new), bank, program);
    }

    private static Sf2Zone makeZone(Sf2Sample sample, GeneratorSet preset, GeneratorSet instrument) {
        int lowKey = Math.max(preset.rangeLow(KEY_RANGE), instrument.rangeLow(KEY_RANGE));
        int highKey = Math.min(preset.rangeHigh(KEY_RANGE), instrument.rangeHigh(KEY_RANGE));
        int lowVelocity = Math.max(preset.rangeLow(VEL_RANGE), instrument.rangeLow(VEL_RANGE));
        int highVelocity = Math.min(preset.rangeHigh(VEL_RANGE), instrument.rangeHigh(VEL_RANGE));
        int root = instrument.has(ROOT_KEY) ? instrument.unsigned(ROOT_KEY)
                : preset.has(ROOT_KEY) ? preset.unsigned(ROOT_KEY) : sample.originalPitch;
        int coarse = preset.signedOr(COARSE_TUNE, 0) + instrument.signedOr(COARSE_TUNE, 0);
        int fine = preset.signedOr(FINE_TUNE, 0) + instrument.signedOr(FINE_TUNE, 0);
        int modes = instrument.has(SAMPLE_MODES) ? instrument.unsigned(SAMPLE_MODES)
                : preset.unsignedOr(SAMPLE_MODES, 0);
        int attackTc = combinedTimecents(preset, instrument, ATTACK);
        int holdTc = combinedTimecents(preset, instrument, HOLD);
        int decayTc = combinedTimecents(preset, instrument, DECAY);
        int releaseTc = combinedTimecents(preset, instrument, RELEASE);
        int sustainCb = Math.max(0, preset.signedOr(SUSTAIN, 0) + instrument.signedOr(SUSTAIN, 0));
        int attenuationCb = Math.max(0,
                preset.signedOr(ATTENUATION, 0) + instrument.signedOr(ATTENUATION, 0));
        float sustainGain = (float) Math.pow(10.0, -Math.min(1440, sustainCb) / 200.0);
        int decayFrames = frames(decayTc, 0.0);
        float decayMultiplier = decayFrames == 0 || sustainGain == 1.0f ? 1.0f
                : (float) Math.pow(sustainGain, 1.0 / decayFrames);
        return new Sf2Zone(sample, lowKey, highKey, lowVelocity, highVelocity, root, coarse, fine,
                modes, (float) Math.pow(10.0, -attenuationCb / 200.0),
                frames(attackTc, 0.005), frames(holdTc, 0.0), decayFrames, sustainGain,
                decayMultiplier, Math.max(1, frames(releaseTc, 0.0)));
    }

    private static int combinedTimecents(GeneratorSet preset, GeneratorSet instrument, int operator) {
        int value = instrument.signedOr(operator, -12000);
        if (preset.has(operator) && value != Short.MIN_VALUE) value += preset.signed(operator);
        return value;
    }

    private static int frames(int timecents, double minimumSeconds) {
        double seconds = timecents == Short.MIN_VALUE ? 0.0 : Math.pow(2.0, timecents / 1200.0);
        seconds = Math.max(minimumSeconds, Math.min(seconds, 3600.0));
        return (int) Math.min(Integer.MAX_VALUE, Math.round(seconds * OUTPUT_RATE));
    }

    private static float[] decodePcm(byte[] data, Chunk chunk) throws IOException {
        require((chunk.size & 1) == 0, "Odd smpl chunk size");
        float[] pcm = new float[chunk.size / 2];
        for (int i = 0, p = chunk.offset; i < pcm.length; i++, p += 2) {
            pcm[i] = (short) u16(data, p) / 32768.0f;
        }
        return pcm;
    }

    private static Sf2Sample[] readSamples(byte[] data, Chunk chunk, float[] pcm) throws IOException {
        require(chunk.size % 46 == 0 && chunk.size >= 92, "Invalid shdr table");
        int count = chunk.size / 46 - 1;
        Sf2Sample[] samples = new Sf2Sample[count];
        for (int i = 0; i < count; i++) {
            int p = chunk.offset + i * 46;
            long start = u32(data, p + 20), end = u32(data, p + 24);
            long loopStart = u32(data, p + 28), loopEnd = u32(data, p + 32);
            long rate = u32(data, p + 36);
            require(start < end && end <= pcm.length && loopStart >= start && loopStart <= loopEnd && loopEnd <= end
                    && rate > 0 && rate <= Integer.MAX_VALUE, "Invalid sample header");
            samples[i] = new Sf2Sample(name(data, p), pcm, (int) start, (int) end,
                    (int) loopStart, (int) loopEnd, (int) rate, data[p + 40] & 255, data[p + 41]);
        }
        return samples;
    }

    private static Header[] readHeaders(byte[] data, Chunk chunk, int recordSize, boolean preset)
            throws IOException {
        require(chunk.size % recordSize == 0 && chunk.size >= recordSize * 2, "Invalid header table");
        Header[] headers = new Header[chunk.size / recordSize];
        for (int i = 0; i < headers.length; i++) {
            int p = chunk.offset + i * recordSize;
            headers[i] = preset
                    ? new Header(u16(data, p + 22), u16(data, p + 20), u16(data, p + 24))
                    : new Header(0, 0, u16(data, p + 20));
        }
        return headers;
    }

    private static Bag[] readBags(byte[] data, Chunk chunk) throws IOException {
        require(chunk.size % 4 == 0 && chunk.size >= 8, "Invalid bag table");
        Bag[] bags = new Bag[chunk.size / 4];
        for (int i = 0; i < bags.length; i++) bags[i] = new Bag(u16(data, chunk.offset + i * 4));
        return bags;
    }

    private static Generator[] readGenerators(byte[] data, Chunk chunk) throws IOException {
        require(chunk.size % 4 == 0, "Invalid generator table");
        Generator[] generators = new Generator[chunk.size / 4];
        for (int i = 0; i < generators.length; i++) {
            int p = chunk.offset + i * 4;
            generators[i] = new Generator(u16(data, p), u16(data, p + 2));
        }
        return generators;
    }

    private static GeneratorSet generators(Bag[] bags, Generator[] generators, int bag)
            throws IOException {
        require(bag >= 0 && bag + 1 < bags.length, "Invalid bag index");
        int start = bags[bag].generator;
        int end = bags[bag + 1].generator;
        require(start <= end && end <= generators.length, "Invalid generator index");
        GeneratorSet result = new GeneratorSet();
        for (int i = start; i < end; i++) {
            int operator = generators[i].operator;
            if (operator < result.values.length) result.set(operator, generators[i].amount);
        }
        return result;
    }

    private static GeneratorSet merge(GeneratorSet global, GeneratorSet local) {
        GeneratorSet result = new GeneratorSet();
        for (int i = 0; i < result.values.length; i++) {
            if (global.present[i]) result.set(i, global.values[i]);
            if (local.present[i]) {
                if ((i == KEY_RANGE || i == VEL_RANGE) && global.present[i]) {
                    int low = Math.max(global.rangeLow(i), local.rangeLow(i));
                    int high = Math.min(global.rangeHigh(i), local.rangeHigh(i));
                    result.set(i, low | (high << 8));
                } else result.set(i, local.values[i]);
            }
        }
        return result;
    }

    private static String name(byte[] data, int offset) {
        int end = offset;
        while (end < offset + 20 && data[end] != 0) end++;
        return new String(data, offset, end - offset, StandardCharsets.US_ASCII);
    }

    private static String fourcc(byte[] data, int offset) {
        return new String(data, offset, 4, StandardCharsets.US_ASCII);
    }

    private static int checkedSize(byte[] data, int offset) throws IOException {
        long value = u32(data, offset);
        require(value <= Integer.MAX_VALUE, "Chunk too large");
        return (int) value;
    }

    private static int u16(byte[] data, int offset) {
        return (data[offset] & 255) | ((data[offset + 1] & 255) << 8);
    }

    private static long u32(byte[] data, int offset) {
        return Integer.toUnsignedLong(u16(data, offset) | (u16(data, offset + 2) << 16));
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }

    public record Parsed(Sf2Sample[] samples, Sf2Zone[] zones, int bank, int program) {
        public Parsed {
            samples = samples.clone();
            zones = zones.clone();
        }

        @Override public Sf2Sample[] samples() { return samples.clone(); }
        @Override public Sf2Zone[] zones() { return zones.clone(); }
    }

    private record Chunk(int offset, int size) {}
    private record Header(int bank, int program, int bag) {}
    private record Bag(int generator) {}
    private record Generator(int operator, int amount) {}

    private static final class GeneratorSet {
        private final int[] values = new int[61];
        private final boolean[] present = new boolean[61];

        void set(int operator, int value) { values[operator] = value; present[operator] = true; }
        boolean has(int operator) { return present[operator]; }
        int unsigned(int operator) { return values[operator]; }
        int unsignedOr(int operator, int fallback) { return present[operator] ? values[operator] : fallback; }
        int signed(int operator) { return (short) values[operator]; }
        int signedOr(int operator, int fallback) { return present[operator] ? signed(operator) : fallback; }
        int rangeLow(int operator) { return present[operator] ? values[operator] & 255 : 0; }
        int rangeHigh(int operator) { return present[operator] ? values[operator] >>> 8 & 255 : 127; }
    }
}
