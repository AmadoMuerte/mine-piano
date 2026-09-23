package dev.amado.minepiano.midi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.amado.minepiano.audio.PianoEngine;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.ShortMessage;
import org.junit.jupiter.api.Test;

class MidiMessageHandlerTest {
    @Test
    void translatesNotesAndVelocityZero() throws Exception {
        List<String> calls = new ArrayList<>();
        List<Integer> listenedNotes = new ArrayList<>();
        MidiMessageHandler handler = new MidiMessageHandler(engine(calls), listenedNotes::add);

        handler.send(new ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), -1);
        handler.send(new ShortMessage(ShortMessage.NOTE_ON, 0, 61, 0), -1);
        handler.send(new ShortMessage(ShortMessage.NOTE_OFF, 0, 62, 64), -1);

        assertEquals(List.of("noteOn:60:100", "noteOff:61", "noteOff:62"), calls);
        assertEquals(List.of(60), listenedNotes);
    }

    @Test
    void translatesSustainAndPanicControllers() throws Exception {
        List<String> calls = new ArrayList<>();
        MidiMessageHandler handler = new MidiMessageHandler(engine(calls), ignored -> { });

        handler.send(new ShortMessage(ShortMessage.CONTROL_CHANGE, 0, 64, 127), -1);
        handler.send(new ShortMessage(ShortMessage.CONTROL_CHANGE, 0, 64, 0), -1);
        handler.send(new ShortMessage(ShortMessage.CONTROL_CHANGE, 0, 120, 0), -1);
        handler.send(new ShortMessage(ShortMessage.CONTROL_CHANGE, 0, 123, 0), -1);

        assertEquals(List.of("setSustain:true", "setSustain:false", "allNotesOff", "allNotesOff"), calls);
    }

    @Test
    void ignoresUnsupportedMessagesAndMessagesAfterClose() throws Exception {
        List<String> calls = new ArrayList<>();
        List<Integer> listenedNotes = new ArrayList<>();
        MidiMessageHandler handler = new MidiMessageHandler(engine(calls), listenedNotes::add);
        MetaMessage meta = new MetaMessage();
        meta.setMessage(1, new byte[] {1}, 1);

        handler.send(new ShortMessage(ShortMessage.PROGRAM_CHANGE, 0, 1, 0), -1);
        handler.send(new ShortMessage(ShortMessage.PITCH_BEND, 0, 0, 64), -1);
        handler.send(new ShortMessage(ShortMessage.CONTROL_CHANGE, 0, 7, 100), -1);
        handler.send(meta, -1);
        handler.close();
        handler.send(new ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), -1);

        assertEquals(List.of(), calls);
        assertEquals(List.of(), listenedNotes);
    }

    private static PianoEngine engine(List<String> calls) {
        return (PianoEngine) Proxy.newProxyInstance(MidiMessageHandlerTest.class.getClassLoader(),
                new Class<?>[] {PianoEngine.class}, (proxy, method, arguments) -> {
                    switch (method.getName()) {
                        case "noteOn" -> calls.add("noteOn:" + arguments[0] + ":" + arguments[1]);
                        case "noteOff" -> calls.add("noteOff:" + arguments[0]);
                        case "setSustain" -> calls.add("setSustain:" + arguments[0]);
                        case "allNotesOff" -> calls.add("allNotesOff");
                        default -> { }
                    }
                    return method.getReturnType() == boolean.class ? false : null;
                });
    }
}
