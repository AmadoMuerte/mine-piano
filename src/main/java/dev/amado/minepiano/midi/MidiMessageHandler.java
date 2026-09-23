package dev.amado.minepiano.midi;

import dev.amado.minepiano.audio.PianoEngine;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import java.util.Objects;
import java.util.function.IntConsumer;

/** Translates MIDI short messages into piano engine commands. */
public final class MidiMessageHandler implements Receiver {
    private final PianoEngine engine;
    private final IntConsumer noteListener;
    private boolean closed;

    public MidiMessageHandler(PianoEngine engine, IntConsumer noteListener) {
        this.engine = Objects.requireNonNull(engine);
        this.noteListener = Objects.requireNonNull(noteListener);
    }

    @Override
    public synchronized void send(MidiMessage message, long timeStamp) {
        if (closed || !(message instanceof ShortMessage shortMessage)) return;
        switch (shortMessage.getCommand()) {
            case ShortMessage.NOTE_ON -> {
                int note = shortMessage.getData1();
                int velocity = shortMessage.getData2();
                if (velocity == 0) engine.noteOff(note);
                else {
                    engine.noteOn(note, velocity);
                    noteListener.accept(note);
                }
            }
            case ShortMessage.NOTE_OFF -> engine.noteOff(shortMessage.getData1());
            case ShortMessage.CONTROL_CHANGE -> {
                int controller = shortMessage.getData1();
                if (controller == 64) engine.setSustain(shortMessage.getData2() >= 64);
                else if (controller == 120 || controller == 123) engine.allNotesOff();
            }
            default -> { }
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
    }
}
