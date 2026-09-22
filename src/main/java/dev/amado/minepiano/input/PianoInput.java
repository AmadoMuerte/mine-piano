package dev.amado.minepiano.input;

import dev.amado.minepiano.config.KeyMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import org.lwjgl.glfw.GLFW;

/** Converts keyboard input into held MIDI notes. */
public final class PianoInput {
    private final KeyMap keyMap;
    private final Set<Integer> heldKeys = new HashSet<>();
    private final Map<Integer, Integer> heldNotes = new HashMap<>();
    private int octave;
    private boolean sustain;

    public PianoInput(KeyMap keyMap, int octave) {
        this.keyMap = keyMap;
        this.octave = octave;
    }

    public OptionalInt press(int keyCode) {
        if (!heldKeys.add(keyCode)) return OptionalInt.empty();
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            toggleSustain();
            return OptionalInt.empty();
        }
        OptionalInt offset = keyMap.offset(keyCode);
        if (offset.isEmpty()) {
            heldKeys.remove(keyCode);
            return OptionalInt.empty();
        }
        int note = (octave + 1) * 12 + offset.getAsInt();
        if (note < 0 || note > 127) {
            heldKeys.remove(keyCode);
            return OptionalInt.empty();
        }
        heldNotes.put(keyCode, note);
        return OptionalInt.of(note);
    }

    public OptionalInt release(int keyCode) {
        if (!heldKeys.remove(keyCode)) return OptionalInt.empty();
        Integer note = heldNotes.remove(keyCode);
        return note == null ? OptionalInt.empty() : OptionalInt.of(note);
    }

    public void octaveUp() {
        octave++;
    }

    public void octaveDown() {
        octave--;
    }

    public int octave() {
        return octave;
    }

    public boolean toggleSustain() {
        return sustain = !sustain;
    }

    public boolean sustain() {
        return sustain;
    }

    public Set<Integer> heldKeys() {
        return Set.copyOf(heldKeys);
    }

    public Set<Integer> heldNotes() {
        return Set.copyOf(heldNotes.values());
    }
}
