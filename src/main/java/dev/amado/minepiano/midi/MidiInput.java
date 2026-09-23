package dev.amado.minepiano.midi;

import dev.amado.minepiano.audio.PianoEngine;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Transmitter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Owns a screen-scoped MIDI input device connection. */
public final class MidiInput {
    private final PianoEngine engine;
    private MidiDevice device;
    private Transmitter transmitter;
    private MidiMessageHandler handler;
    private final AtomicInteger lastNote = new AtomicInteger(-1);

    public MidiInput(PianoEngine engine) {
        this.engine = Objects.requireNonNull(engine);
    }

    public static List<String> deviceNames() {
        Set<String> names = new LinkedHashSet<>();
        try {
            for (MidiDevice candidate : candidates()) names.add(candidate.getDeviceInfo().getName());
        } catch (RuntimeException ignored) {
            // MIDI subsystem unavailable.
        }
        return new ArrayList<>(names);
    }

    public boolean start(String preferred) {
        stop();
        MidiDevice candidate = null;
        try {
            for (MidiDevice available : candidates()) {
                if (candidate == null) candidate = available;
                if (Objects.equals(available.getDeviceInfo().getName(), preferred)) {
                    candidate = available;
                    break;
                }
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        if (candidate == null) return false;

        try {
            device = candidate;
            candidate.open();
            Transmitter opened = candidate.getTransmitter();
            if (opened == null) {
                closeQuietly();
                return false;
            }
            transmitter = opened;
            MidiMessageHandler openedHandler = new MidiMessageHandler(engine, lastNote::set);
            handler = openedHandler;
            opened.setReceiver(openedHandler);
            return true;
        } catch (MidiUnavailableException | IllegalArgumentException | IllegalStateException | SecurityException failure) {
            closeQuietly();
            return false;
        }
    }

    public void stop() {
        MidiMessageHandler currentHandler = handler;
        boolean wasLive = currentHandler != null;
        if (currentHandler != null) currentHandler.close();
        // A no-hardware retry must not cut live keyboard or mouse notes.
        if (wasLive) {
            try {
                engine.allNotesOff();
            } catch (RuntimeException ignored) {
            }
        }
        lastNote.set(-1);
        closeQuietly(transmitter);
        closeQuietly(device);
        handler = null;
        transmitter = null;
        device = null;
    }

    public boolean isConnected() {
        return device != null && device.isOpen();
    }

    public boolean canConnect() {
        try {
            return !candidates().isEmpty();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public int pollNote() {
        return lastNote.getAndSet(-1);
    }

    private static List<MidiDevice> candidates() {
        List<MidiDevice> candidates = new ArrayList<>();
        try {
            for (MidiDevice.Info info : MidiSystem.getMidiDeviceInfo()) {
                try {
                    MidiDevice candidate = MidiSystem.getMidiDevice(info);
                    if (candidate instanceof Sequencer || candidate instanceof Synthesizer
                            || candidate.getMaxTransmitters() == 0) continue;
                    candidates.add(candidate);
                } catch (MidiUnavailableException | IllegalArgumentException ignored) {
                    // MIDI port vanished during enumeration.
                }
            }
        } catch (SecurityException | IllegalStateException ignored) {
            // MIDI subsystem unavailable.
        }
        return candidates;
    }

    private void closeQuietly() {
        stop();
    }

    private static void closeQuietly(Transmitter transmitter) {
        if (transmitter == null) return;
        try {
            transmitter.close();
        } catch (RuntimeException ignored) {
        }
    }

    private static void closeQuietly(MidiDevice device) {
        if (device == null) return;
        try {
            if (device.isOpen()) device.close();
        } catch (RuntimeException ignored) {
        }
    }
}
