package dev.amado.minepiano.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class VoiceChatMergeFeederTest {
    @Test
    void acceptsExactFramesInOrderAndReturnsNothingWhenEmpty() {
        VoiceChatMergeFeeder feeder = new VoiceChatMergeFeeder();

        short[] first = frame((short) 1);
        feeder.accept(first, VoiceChatMergeFeeder.FRAME_SAMPLES);
        first[0] = 99;
        feeder.accept(frame((short) 2), VoiceChatMergeFeeder.FRAME_SAMPLES);

        assertEquals(1, feeder.poll()[0]);
        assertEquals(2, feeder.poll()[0]);
        assertNull(feeder.poll());
    }

    @Test
    void dropsOldestFrameOnOverflow() {
        VoiceChatMergeFeeder feeder = new VoiceChatMergeFeeder();
        for (short value = 0; value <= VoiceChatMergeFeeder.CAPACITY; value++)
            feeder.accept(frame(value), VoiceChatMergeFeeder.FRAME_SAMPLES);

        for (short value = 1; value <= VoiceChatMergeFeeder.CAPACITY; value++)
            assertEquals(value, feeder.poll()[0]);
        assertNull(feeder.poll());
    }

    @Test
    void rejectsWrongLengthAndStopsWhenDisabled() {
        VoiceChatMergeFeeder feeder = new VoiceChatMergeFeeder();
        feeder.accept(frame((short) 1), VoiceChatMergeFeeder.FRAME_SAMPLES - 1);
        assertNull(feeder.poll());

        feeder.accept(frame((short) 2), VoiceChatMergeFeeder.FRAME_SAMPLES);
        feeder.setEnabled(false);
        assertNull(feeder.poll());
        feeder.accept(frame((short) 3), VoiceChatMergeFeeder.FRAME_SAMPLES);
        feeder.setEnabled(true);
        assertNull(feeder.poll());
    }

    private static short[] frame(short value) {
        short[] frame = new short[VoiceChatMergeFeeder.FRAME_SAMPLES];
        Arrays.fill(frame, value);
        return frame;
    }
}
