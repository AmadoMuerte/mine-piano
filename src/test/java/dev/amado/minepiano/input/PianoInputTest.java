package dev.amado.minepiano.input;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amado.minepiano.config.KeyMap;
import dev.amado.minepiano.config.PianoConfig;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

class PianoInputTest {
    @Test
    void initializesAndUpdatesSustain() {
        PianoInput input = new PianoInput(new KeyMap(new PianoConfig()), 4, true);

        assertTrue(input.sustain());
        input.setSustain(false);
        assertFalse(input.sustain());
        assertTrue(input.toggleSustain());
        assertTrue(input.press(GLFW.GLFW_KEY_SPACE).isEmpty());
        assertFalse(input.sustain());
    }
}
