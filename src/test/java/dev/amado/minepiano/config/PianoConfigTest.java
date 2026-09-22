package dev.amado.minepiano.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amado.minepiano.input.PianoInput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
        assertEquals(3, loaded.layoutVersion);
    }

    @Test
    void defaultLayoutIsSpecified() {
        Map<String, Integer> keymap = new PianoConfig().keymap;
        assertEquals(37, keymap.size());
        assertEquals(Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17,
                18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35),
                keymap.values().stream().collect(Collectors.toSet()));
        assertEquals(2, keymap.values().stream().filter(offset -> offset == 12).count());
        assertEquals(0, keymap.get("Z"));
        assertEquals(4, keymap.get("C"));
        assertEquals(11, keymap.get("M"));
        assertEquals(12, keymap.get(","));
        assertEquals(12, keymap.get("Q"));
        assertEquals(23, keymap.get("U"));
        assertEquals(24, keymap.get("I"));
        assertEquals(28, keymap.get("P"));
        assertEquals(29, keymap.get("["));
        assertEquals(31, keymap.get("\\"));
        assertEquals(32, keymap.get("L"));
        assertEquals(35, keymap.get("/"));
    }

    @Test
    void handlesSustain() {
        PianoInput input = new PianoInput(new KeyMap(new PianoConfig()), 4);
        assertTrue(input.press(GLFW.GLFW_KEY_SPACE).isEmpty());
        assertTrue(input.sustain());
    }

    @Test
    void migratesOldLayout(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("config.json");
        Files.writeString(path, """
                {"keymap":{"A":0,"W":1,"S":2,"E":3,"D":4,"F":5,"T":6,"G":7,"Y":8,"H":9,"U":10,"J":11,"K":12},
                 "octave":5,"velocity":80,"masterVolume":0.75,"svcDistance":24.0,"soundfont":"custom.sf2"}
                """);

        PianoConfig loaded = PianoConfig.load(path);

        assertEquals(PianoConfig.defaultKeymap(), loaded.keymap);
        assertEquals(3, loaded.layoutVersion);
        assertEquals(5, loaded.octave);
        assertEquals(80, loaded.velocity);
        assertEquals(0.75F, loaded.masterVolume);
        assertEquals(24.0F, loaded.svcDistance);
        assertEquals("custom.sf2", loaded.soundfont);
        assertTrue(Files.readString(path).contains("\"layoutVersion\": 3"));
    }

    @Test
    void resolvesPhysicalKeysWithoutKeyboardLayout() {
        PianoConfig config = new PianoConfig();
        KeyMap keyMap = new KeyMap(config);
        assertEquals(0, keyMap.offset(GLFW.GLFW_KEY_Z).getAsInt());
        assertEquals(35, keyMap.offset(GLFW.GLFW_KEY_SLASH).getAsInt());
        keyMap.bind("Z", 7);
        assertEquals(7, keyMap.offset(GLFW.GLFW_KEY_Z).getAsInt());
        config.keymap.remove("Z");
        config.keymap.put("UNKNOWN", 0);
        assertTrue(keyMap.offset(GLFW.GLFW_KEY_Z).isEmpty());
        assertDoesNotThrow(() -> keyMap.offset(GLFW.GLFW_KEY_UNKNOWN));
    }
}
