package dev.amado.minepiano.audio.sf2;

import dev.amado.minepiano.audio.Voice;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Sf2SoundBankTest {
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
}
