package dev.amado.minepiano.input;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amado.minepiano.config.KeyMap;
import dev.amado.minepiano.config.PianoConfig;
import org.junit.jupiter.api.Test;
import com.mojang.blaze3d.platform.InputConstants;

class PianoInputTest {
    @Test
    void initializesAndUpdatesSustain() {
        PianoInput input = new PianoInput(new KeyMap(new PianoConfig()), 4, true);

        assertTrue(input.sustain());
        input.setSustain(false);
        assertFalse(input.sustain());
        assertTrue(input.toggleSustain());
        assertTrue(input.press(InputConstants.KEY_SPACE).isEmpty());
        assertFalse(input.sustain());
    }

    @Test
    void clampsOctaveChangesAndConstruction() {
        PianoInput input = new PianoInput(new KeyMap(new PianoConfig()), 4);
        for (int i = 0; i < 50; i++) input.octaveUp();
        assertEquals(PianoConfig.MAX_OCTAVE, input.octave());
        for (int i = 0; i < 50; i++) input.octaveDown();
        assertEquals(PianoConfig.MIN_OCTAVE, input.octave());

        input.setOctave(99);
        assertEquals(PianoConfig.MAX_OCTAVE, input.octave());
        input.setOctave(-99);
        assertEquals(PianoConfig.MIN_OCTAVE, input.octave());
        assertEquals(PianoConfig.MAX_OCTAVE, new PianoInput(new KeyMap(new PianoConfig()), 99).octave());
        assertEquals(PianoConfig.MIN_OCTAVE, new PianoInput(new KeyMap(new PianoConfig()), -99).octave());
    }
}
