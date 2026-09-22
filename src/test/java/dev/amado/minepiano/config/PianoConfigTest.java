package dev.amado.minepiano.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amado.minepiano.input.PianoInput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.glfw.GLFW;

class PianoConfigTest {
    @Test
    void savesAndLoads(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("config.json");
        PianoConfig saved = new PianoConfig();
        saved.octave = 5;
        saved.velocity = 80;
        saved.soundfont = "custom.sf2";
        saved.keymap.put("L", 13);
        saved.save(path);

        PianoConfig loaded = PianoConfig.load(path);
        assertEquals(saved.octave, loaded.octave);
        assertEquals(saved.velocity, loaded.velocity);
        assertEquals(saved.soundfont, loaded.soundfont);
        assertEquals(saved.keymap, loaded.keymap);
    }

    @Test
    void defaultLayoutIsSpecified() {
        assertEquals(Map.ofEntries(
            Map.entry("A", 0), Map.entry("W", 1), Map.entry("S", 2), Map.entry("E", 3),
            Map.entry("D", 4), Map.entry("F", 5), Map.entry("T", 6), Map.entry("G", 7),
            Map.entry("Y", 8), Map.entry("H", 9), Map.entry("U", 10), Map.entry("J", 11), Map.entry("K", 12)),
            new PianoConfig().keymap);
    }

    @Test
    void translatesKeysToMidiNotes() {
        PianoInput input = new PianoInput(new KeyMap(new PianoConfig()), 4);
        assertEquals(60, input.press(GLFW.GLFW_KEY_A).getAsInt());
        assertEquals(61, input.press(GLFW.GLFW_KEY_W).getAsInt());
        assertEquals(72, input.press(GLFW.GLFW_KEY_K).getAsInt());
        assertTrue(input.press(GLFW.GLFW_KEY_K).isEmpty());
    }
}
