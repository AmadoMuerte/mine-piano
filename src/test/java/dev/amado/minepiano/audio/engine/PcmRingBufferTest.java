package dev.amado.minepiano.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PcmRingBufferTest {
    @Test
    void wrapsAndHonorsFullAndEmptyEdges() {
        PcmRingBuffer ring = new PcmRingBuffer(4);
        short[] first = {1, 2, 3, 4, 5};
        short[] output = new short[6];

        assertEquals(4, ring.write(first, 0, first.length));
        assertEquals(0, ring.availableToWrite());
        assertEquals(0, ring.write(first, 4, 1));
        assertEquals(3, ring.read(output, 0, 3));
        assertEquals(3, ring.write(new short[]{5, 6, 7}, 0, 3));
        assertEquals(4, ring.read(output, 0, output.length));
        assertArrayEquals(new short[]{4, 5, 6, 7, 0, 0}, output);
        assertEquals(0, ring.read(output, 0, 1));
        assertEquals(0, ring.availableToRead());
    }
}
