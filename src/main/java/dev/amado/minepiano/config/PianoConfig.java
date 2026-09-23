package dev.amado.minepiano.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import dev.amado.minepiano.audio.PianoPreset;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent client settings. */
public final class PianoConfig {
    public static final Path DEFAULT_PATH = Path.of("config", "mine-piano", "config.json");
    public static final int MIN_OCTAVE = -1;
    public static final int MAX_OCTAVE = 6;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public Map<String, Integer> keymap = defaultKeymap();
    public int layoutVersion = 3;
    public int octave = 4;
    public float masterVolume = 1.0F;
    public float localVolume = 1.0F;
    public float svcDistance = 16.0F;
    public boolean transmitToVoiceChat = true;
    public boolean sustain = false;
    public String presetName = PianoPreset.REALISTIC.name();
    public boolean midiEnabled = true;
    public String midiDevice = "";

    public static PianoConfig load() {
        return load(DEFAULT_PATH);
    }

    public static PianoConfig load(Path path) {
        try {
            if (Files.exists(path)) {
                String json = Files.readString(path);
                PianoConfig config = GSON.fromJson(json, PianoConfig.class);
                var object = JsonParser.parseString(json).getAsJsonObject();
                boolean migrated = false;
                if (config != null && !object.has("layoutVersion")) config.layoutVersion = 0;
                if (config != null && !object.has("presetName")) {
                    int oldPreset = object.has("preset") ? object.get("preset").getAsInt() : 0;
                    PianoPreset[] presets = PianoPreset.values();
                    config.presetName = presets[Math.max(0, Math.min(presets.length - 1, oldPreset))].name();
                    migrated = true;
                }
                if (config != null) {
                    config.presetName = PianoPreset.fromName(config.presetName).name();
                    if (config.midiDevice == null) config.midiDevice = "";
                    config.octave = clampOctave(config.octave);
                }
                if (config != null && config.valid()) {
                    if (config.layoutVersion < 3) {
                        config.keymap = defaultKeymap();
                        config.layoutVersion = 3;
                        migrated = true;
                    }
                    if (migrated) {
                        try {
                            config.save(path);
                        } catch (IOException ignored) {
                            // Migrated configuration remains usable if disk is unavailable.
                        }
                    }
                    return config;
                }
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

    public static int clampOctave(int octave) {
        return Math.max(MIN_OCTAVE, Math.min(MAX_OCTAVE, octave));
    }

    public static Map<String, Integer> defaultKeymap() {
        Map<String, Integer> map = new LinkedHashMap<>();
        String[] keys = {"Z", "S", "X", "D", "C", "V", "G", "B", "H", "N", "J", "M", ",",
                "Q", "2", "W", "3", "E", "R", "5", "T", "6", "Y", "7", "U", "I",
                "9", "O", "0", "P", "[", "]", "\\", "L", ".", ";", "/"};
        int[] offsets = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12,
                12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24,
                25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35};
        for (int index = 0; index < keys.length; index++)
            map.put(keys[index], offsets[index]);
        return map;
    }

    private boolean valid() {
        return keymap != null
            && keymap.entrySet().stream().allMatch(entry -> entry.getKey() != null && entry.getValue() != null)
            && presetName != null;
    }
}
