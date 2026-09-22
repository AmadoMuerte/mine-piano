package dev.amado.minepiano.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent client settings. */
public final class PianoConfig {
    public static final Path DEFAULT_PATH = Path.of("config", "mine-piano", "config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public Map<String, Integer> keymap = defaultKeymap();
    public int octave = 4;
    public int velocity = 100;
    public float masterVolume = 1.0F;
    public float localVolume = 1.0F;
    public float svcDistance = 16.0F;
    public String soundfont = "";
    public int preset = 0;

    public static PianoConfig load() {
        return load(DEFAULT_PATH);
    }

    public static PianoConfig load(Path path) {
        try {
            if (Files.exists(path)) {
                PianoConfig config = GSON.fromJson(Files.readString(path), PianoConfig.class);
                if (config != null && config.valid()) return config;
            }
        } catch (IOException | RuntimeException ignored) {
            // Defaults below replace unreadable or malformed files.
        }

        PianoConfig config = new PianoConfig();
        try {
            config.save(path);
        } catch (IOException ignored) {
            // Configuration remains usable if disk is unavailable.
        }
        return config;
    }

    public void save() throws IOException {
        save(DEFAULT_PATH);
    }

    public void save(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(path, GSON.toJson(this));
    }

    public static Map<String, Integer> defaultKeymap() {
        Map<String, Integer> map = new LinkedHashMap<>();
        String[] keys = {"A", "W", "S", "E", "D", "F", "T", "G", "Y", "H", "U", "J", "K"};
        for (int offset = 0; offset < keys.length; offset++) map.put(keys[offset], offset);
        return map;
    }

    private boolean valid() {
        return keymap != null
            && keymap.entrySet().stream().allMatch(entry -> entry.getKey() != null && entry.getValue() != null)
            && velocity >= 0 && velocity <= 127
            && soundfont != null;
    }
}
