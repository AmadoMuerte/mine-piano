package dev.amado.minepiano.config;

import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Optional;
import org.lwjgl.glfw.GLFW;

/** Maps named keyboard keys to semitone offsets. */
public final class KeyMap {
    private final PianoConfig config;

    public KeyMap(PianoConfig config) {
        this.config = config;
    }

    public OptionalInt offset(String keyName) {
        Integer offset = config.keymap.get(normalize(keyName));
        return offset == null ? OptionalInt.empty() : OptionalInt.of(offset);
    }

    public OptionalInt offset(int keyCode) {
        Optional<String> name = name(keyCode);
        return name.isPresent() ? offset(name.get()) : OptionalInt.empty();
    }

    /** Persisted key name for a physical FL-layout key. */
    public Optional<String> name(int keyCode) {
        for (Key key : KEYS) if (key.code == keyCode) return Optional.of(key.name);
        return Optional.empty();
    }

    /** Bound physical key name for a semitone offset. */
    public Optional<String> nameForOffset(int semitoneOffset) {
        for (Key key : KEYS) {
            Integer offset = config.keymap.get(key.name);
            if (offset != null && offset == semitoneOffset) return Optional.of(key.name);
        }
        return Optional.empty();
    }

    public void bind(String keyName, int semitoneOffset) {
        if (semitoneOffset < 0) throw new IllegalArgumentException("semitoneOffset must be non-negative");
        config.keymap.put(normalize(keyName), semitoneOffset);
    }

    public void unbind(String keyName) {
        config.keymap.remove(normalize(keyName));
    }

    public Map<String, Integer> bindings() {
        return Map.copyOf(config.keymap);
    }

    private static String normalize(String keyName) {
        if (keyName == null || keyName.isBlank()) throw new IllegalArgumentException("keyName must not be blank");
        return keyName.toUpperCase(Locale.ROOT);
    }

    private record Key(int code, String name) { }

    private static final Key[] KEYS = {
            new Key(GLFW.GLFW_KEY_Z, "Z"), new Key(GLFW.GLFW_KEY_S, "S"),
            new Key(GLFW.GLFW_KEY_X, "X"), new Key(GLFW.GLFW_KEY_D, "D"),
            new Key(GLFW.GLFW_KEY_C, "C"), new Key(GLFW.GLFW_KEY_V, "V"),
            new Key(GLFW.GLFW_KEY_G, "G"), new Key(GLFW.GLFW_KEY_B, "B"),
            new Key(GLFW.GLFW_KEY_H, "H"), new Key(GLFW.GLFW_KEY_N, "N"),
            new Key(GLFW.GLFW_KEY_J, "J"), new Key(GLFW.GLFW_KEY_M, "M"),
            new Key(GLFW.GLFW_KEY_COMMA, ","), new Key(GLFW.GLFW_KEY_Q, "Q"),
            new Key(GLFW.GLFW_KEY_2, "2"), new Key(GLFW.GLFW_KEY_W, "W"),
            new Key(GLFW.GLFW_KEY_3, "3"), new Key(GLFW.GLFW_KEY_E, "E"),
            new Key(GLFW.GLFW_KEY_R, "R"), new Key(GLFW.GLFW_KEY_5, "5"),
            new Key(GLFW.GLFW_KEY_T, "T"), new Key(GLFW.GLFW_KEY_6, "6"),
            new Key(GLFW.GLFW_KEY_Y, "Y"), new Key(GLFW.GLFW_KEY_7, "7"),
            new Key(GLFW.GLFW_KEY_U, "U"), new Key(GLFW.GLFW_KEY_I, "I"),
            new Key(GLFW.GLFW_KEY_9, "9"), new Key(GLFW.GLFW_KEY_O, "O"),
            new Key(GLFW.GLFW_KEY_0, "0"), new Key(GLFW.GLFW_KEY_P, "P"),
            new Key(GLFW.GLFW_KEY_LEFT_BRACKET, "["), new Key(GLFW.GLFW_KEY_RIGHT_BRACKET, "]"),
            new Key(GLFW.GLFW_KEY_BACKSLASH, "\\"), new Key(GLFW.GLFW_KEY_L, "L"),
            new Key(GLFW.GLFW_KEY_PERIOD, "."), new Key(GLFW.GLFW_KEY_SEMICOLON, ";"),
            new Key(GLFW.GLFW_KEY_SLASH, "/")
    };
}
