package dev.amado.minepiano.config;

import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
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
        return offset(keyName(keyCode));
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

    private static String keyName(int keyCode) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_A -> "A";
            case GLFW.GLFW_KEY_W -> "W";
            case GLFW.GLFW_KEY_S -> "S";
            case GLFW.GLFW_KEY_E -> "E";
            case GLFW.GLFW_KEY_D -> "D";
            case GLFW.GLFW_KEY_F -> "F";
            case GLFW.GLFW_KEY_T -> "T";
            case GLFW.GLFW_KEY_G -> "G";
            case GLFW.GLFW_KEY_Y -> "Y";
            case GLFW.GLFW_KEY_H -> "H";
            case GLFW.GLFW_KEY_U -> "U";
            case GLFW.GLFW_KEY_J -> "J";
            case GLFW.GLFW_KEY_K -> "K";
            default -> "";
        };
    }
}
