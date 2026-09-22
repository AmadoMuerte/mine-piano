package dev.amado.minepiano.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandQueueTest {
    @Test
    @Timeout(30)
    void multipleProducersPublishMillionsInOrderWithoutLoss() throws Exception {
        int producers = 2;
        int perProducer = 1_000_000;
        PianoEngineImpl.CommandQueue queue = new PianoEngineImpl.CommandQueue(4096);
        CountDownLatch start = new CountDownLatch(1);
        Thread[] threads = new Thread[producers];
        for (int producer = 0; producer < producers; producer++) {
            int id = producer;
            threads[producer] = Thread.ofPlatform().start(() -> {
                try {
                    start.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int sequence = 0; sequence < perProducer; sequence++) {
                    queue.offer(((long) (id + 1) << 32) | (sequence + 1L));
                }
            });
        }

        start.countDown();
        int[] expected = new int[producers];
        int received = 0;
        while (received < producers * perProducer) {
            long value = queue.poll();
            if (value == 0L) {
                Thread.onSpinWait();
                continue;
            }
            int producer = (int) (value >>> 32) - 1;
            int sequence = (int) value - 1;
            assertEquals(expected[producer]++, sequence, "per-producer order");
            received++;
        }
        for (Thread thread : threads) thread.join();
        assertEquals(perProducer, expected[0]);
        assertEquals(perProducer, expected[1]);
        assertEquals(0L, queue.poll());
    }
}
