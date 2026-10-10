package dev.amado.minepiano.config;

import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Optional;
import com.mojang.blaze3d.platform.InputConstants;

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
            new Key(InputConstants.KEY_Z, "Z"), new Key(InputConstants.KEY_S, "S"),
            new Key(InputConstants.KEY_X, "X"), new Key(InputConstants.KEY_D, "D"),
            new Key(InputConstants.KEY_C, "C"), new Key(InputConstants.KEY_V, "V"),
            new Key(InputConstants.KEY_G, "G"), new Key(InputConstants.KEY_B, "B"),
            new Key(InputConstants.KEY_H, "H"), new Key(InputConstants.KEY_N, "N"),
            new Key(InputConstants.KEY_J, "J"), new Key(InputConstants.KEY_M, "M"),
            new Key(InputConstants.KEY_COMMA, ","), new Key(InputConstants.KEY_Q, "Q"),
            new Key(InputConstants.KEY_2, "2"), new Key(InputConstants.KEY_W, "W"),
            new Key(InputConstants.KEY_3, "3"), new Key(InputConstants.KEY_E, "E"),
            new Key(InputConstants.KEY_R, "R"), new Key(InputConstants.KEY_5, "5"),
            new Key(InputConstants.KEY_T, "T"), new Key(InputConstants.KEY_6, "6"),
            new Key(InputConstants.KEY_Y, "Y"), new Key(InputConstants.KEY_7, "7"),
            new Key(InputConstants.KEY_U, "U"), new Key(InputConstants.KEY_I, "I"),
            new Key(InputConstants.KEY_9, "9"), new Key(InputConstants.KEY_O, "O"),
            new Key(InputConstants.KEY_0, "0"), new Key(InputConstants.KEY_P, "P"),
            new Key(InputConstants.KEY_LBRACKET, "["), new Key(InputConstants.KEY_RBRACKET, "]"),
            new Key(InputConstants.KEY_BACKSLASH, "\\"), new Key(InputConstants.KEY_L, "L"),
            new Key(InputConstants.KEY_PERIOD, "."), new Key(InputConstants.KEY_SEMICOLON, ";"),
            new Key(InputConstants.KEY_SLASH, "/")
    };
}
