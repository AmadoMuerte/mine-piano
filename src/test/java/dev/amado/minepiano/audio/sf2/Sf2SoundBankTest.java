package dev.amado.minepiano.audio.sf2;

import dev.amado.minepiano.audio.Voice;
import dev.amado.minepiano.audio.PianoPreset;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Sf2SoundBankTest {
    @Test
    void velocityProducesStrictlyIncreasingLoudnessAndZeroIsSilent() {
        Sf2SoundBank bank = new Sf2SoundBank();
        float low = peak(bank.newVoice(69, 30));
        float medium = peak(bank.newVoice(69, 70));
        float high = peak(bank.newVoice(69, 127));
        assertTrue(low > 0.0f);
        assertTrue(low < medium && medium < high, low + " < " + medium + " < " + high);
        assertEquals(0.0f, peak(bank.newVoice(69, 0)));
    }

    @Test
    void everyPresetLoadsItsInstrumentAndRendersNonSilent() {
        Sf2SoundBank base = new Sf2SoundBank();
        for (PianoPreset preset : PianoPreset.values()) {
            Sf2SoundBank bank = base.withPreset(preset);
            assertEquals(preset, bank.preset());
            assertTrue(peak(bank.newVoice(69, 100)) > 0.0001f, preset + " is silent");
        }
    }

    @Test
    void differentInstrumentPresetsRenderMeaningfullyDifferentAudio() {
        Sf2SoundBank base = new Sf2SoundBank();
        float[] grand = render(base.withPreset(PianoPreset.GRAND).newVoice(69, 100));
        float[] electric = render(base.withPreset(PianoPreset.ELECTRIC).newVoice(69, 100));
        double signal = 0.0;
        double difference = 0.0;
        for (int i = 0; i < grand.length; i++) {
            signal += grand[i] * grand[i] + electric[i] * electric[i];
            double delta = grand[i] - electric[i];
            difference += delta * delta;
        }
        assertTrue(signal > 0.0);
        assertTrue(Math.sqrt(difference / signal) > 0.25, "instrument waveforms must differ audibly");
        assertNotEquals(peak(grand), peak(electric));
    }

    @Test
    void bundledPianoParsesAndRendersSmoothly() throws Exception {
        Sf2Parser.Parsed parsed;
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("assets/minepiano/soundfont/piano.sf2")) {
            assertNotNull(input);
            parsed = new Sf2Parser().parse(input, 0, 0);
        }
        assertEquals(0, parsed.bank());
        assertEquals(0, parsed.program());
        assertTrue(parsed.zones().length > 0, "preset found");
        assertTrue(parsed.samples().length > 0, "samples decoded");
        for (Sf2Sample sample : parsed.samples()) assertTrue(sample.length() > 0);

        Sf2SoundBank bank = new Sf2SoundBank();
        assertTrue(bank.sampleCount() > 0);
        Voice voice = bank.newVoice(69, 100);
        float[] frame = new float[960];
        float peak = 0.0f;
        float maxDelta = 0.0f;
        float previous = 0.0f;
        for (int block = 0; block < 50; block++) {
            voice.renderAdd(frame, frame.length);
            for (float value : frame) {
                peak = Math.max(peak, Math.abs(value));
                maxDelta = Math.max(maxDelta, Math.abs(value - previous));
                previous = value;
                assertTrue(Math.abs(value) <= 1.0f, "unclipped float output");
            }
            java.util.Arrays.fill(frame, 0.0f);
        }
        assertTrue(peak > 0.001f, "A4 is non-silent");
        assertTrue(maxDelta < 0.5f, "sustained note has no click-sized discontinuity: " + maxDelta);

        Voice attack = bank.newVoice(69, 100);
        float[] beginning = new float[16];
        attack.renderAdd(beginning, beginning.length);
        assertEquals(0.0f, beginning[0]);
        for (float value : beginning) assertTrue(Math.abs(value) < 0.05f, "attack starts near zero");

        voice.release();
        for (int block = 0; block < 3_000 && !voice.isFinished(); block++) {
            java.util.Arrays.fill(frame, 0.0f);
            voice.renderAdd(frame, frame.length);
        }
        assertTrue(voice.isFinished(), "release reaches -96 dB and returns voice to pool");
        java.util.Arrays.fill(frame, 0.0f);
        assertTrue(!voice.renderAdd(frame, frame.length));
        for (float value : frame) assertTrue(Math.abs(value) < 0.0001f, "released voice decays to zero");
    }

    private static float peak(Voice voice) {
        return peak(render(voice));
    }

    private static float[] render(Voice voice) {
        float[] frame = new float[960 * 8];
        voice.renderAdd(frame, frame.length);
        return frame;
    }

    private static float peak(float[] frame) {
        float peak = 0.0f;
        for (float value : frame) peak = Math.max(peak, Math.abs(value));
        return peak;
    }
}
