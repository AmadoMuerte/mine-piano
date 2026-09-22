package dev.amado.minepiano.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.management.ManagementFactory;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PianoEngineImplTest {
    @Test
    @Timeout(5)
    void crossThreadCommandsRunOnAudioThreadAndFramesKeepContract() throws Exception {
        TestBank bank = new TestBank(64);
        PianoEngineImpl engine = new PianoEngineImpl(bank, LocalOutput.clockPaced());
        CountDownLatch frames = new CountDownLatch(2);
        short[][] references = new short[2][];
        AtomicInteger calls = new AtomicInteger();
        engine.addFrameConsumer((frame, length) -> {
            int call = calls.getAndIncrement();
            if (call < references.length) references[call] = frame;
            assertEquals(960, length);
            frames.countDown();
        });
        long caller = Thread.currentThread().threadId();

        try {
            engine.start();
            engine.noteOn(60, 100);
            assertTrue(frames.await(2, TimeUnit.SECONDS));
            TestVoice voice = bank.voices.getFirst();
            assertNotEquals(caller, voice.renderThread);
            assertEquals("MinePiano-Audio", voice.renderThreadName);
            assertSame(references[0], references[1], "same preallocated frame is fanned out");
            assertTrue(engine.isNoteActive(60));

            engine.noteOff(60);
            assertTrue(voice.released.await(1, TimeUnit.SECONDS));
            assertEquals(voice.renderThread, voice.releaseThread);
            assertFalse(engine.isNoteActive(60));
        } finally {
            engine.stop();
        }
    }

    @Test
    @Timeout(5)
    void sustainDefersReleaseUntilPedalUp() throws Exception {
        TestBank bank = new TestBank(64);
        PianoEngineImpl engine = new PianoEngineImpl(bank, LocalOutput.clockPaced());
        CountDownLatch firstFrame = new CountDownLatch(1);
        engine.addFrameConsumer((frame, length) -> firstFrame.countDown());
        try {
            engine.start();
            engine.noteOn(64, 90);
            assertTrue(firstFrame.await(1, TimeUnit.SECONDS));
            TestVoice voice = bank.voices.getFirst();
            engine.setSustain(true);
            engine.noteOff(64);
            Thread.sleep(80L);
            assertEquals(1L, voice.released.getCount(), "pedal holds release");
            engine.setSustain(false);
            assertTrue(voice.released.await(1, TimeUnit.SECONDS));
        } finally {
            engine.stop();
        }
    }

    @Test
    @Timeout(5)
    void allNotesOffClearsEveryVoiceInOneBlock() throws Exception {
        TestBank bank = new TestBank(64);
        PianoEngineImpl engine = new PianoEngineImpl(bank, LocalOutput.clockPaced());
        AtomicInteger blocks = new AtomicInteger();
        engine.addFrameConsumer((frame, length) -> blocks.incrementAndGet());
        try {
            engine.start();
            for (int note = 40; note < 50; note++) engine.noteOn(note, 100);
            awaitBlocks(blocks, 1);
            int before = blocks.get();
            engine.allNotesOff();
            assertFalse(engine.isNoteActive(40), "logical state clears immediately");
            awaitBlocks(blocks, before + 1);
            assertTrue(bank.voices.stream().allMatch(voice -> voice.finished));
            for (int note = 0; note < 128; note++) assertFalse(engine.isNoteActive(note));
        } finally {
            engine.stop();
        }
    }

    @Test
    @Timeout(5)
    void stealsBeyondSixtyFourVoicePoolWithoutStuckNotes() throws Exception {
        TestBank bank = new TestBank(64);
        PianoEngineImpl engine = new PianoEngineImpl(bank, LocalOutput.clockPaced());
        AtomicInteger blocks = new AtomicInteger();
        engine.addFrameConsumer((frame, length) -> blocks.incrementAndGet());
        try {
            for (int i = 0; i < 65; i++) engine.noteOn(20 + i, 100);
            engine.start();
            awaitBlocks(blocks, 1);
            assertEquals(64, bank.voices.size(), "SoundBank pool is reused, not expanded");
            assertTrue(bank.starts.get() >= 65, "stolen voice restarts after four millisecond fade");

            int before = blocks.get();
            engine.allNotesOff();
            awaitBlocks(blocks, before + 1);
            assertTrue(bank.voices.stream().allMatch(voice -> voice.finished));
        } finally {
            engine.stop();
        }
    }

    @Test
    @Timeout(5)
    void hotAudioThreadAllocatesNothingPerBlockAfterWarmupWhenMBeanAvailable() throws Exception {
        java.lang.management.ThreadMXBean platformBean = ManagementFactory.getThreadMXBean();
        if (!(platformBean instanceof com.sun.management.ThreadMXBean bean)
                || !bean.isThreadAllocatedMemorySupported()) return;
        bean.setThreadAllocatedMemoryEnabled(true);

        TestBank bank = new TestBank(64);
        PianoEngineImpl engine = new PianoEngineImpl(bank, LocalOutput.clockPaced());
        AtomicInteger blocks = new AtomicInteger();
        engine.addFrameConsumer((frame, length) -> blocks.incrementAndGet());
        try {
            engine.noteOn(69, 100);
            engine.start();
            awaitBlocks(blocks, 15);
            long threadId = bank.voices.getFirst().renderThread;
            int firstBlock = blocks.get();
            long before = bean.getThreadAllocatedBytes(threadId);
            awaitBlocks(blocks, firstBlock + 30);
            long allocated = bean.getThreadAllocatedBytes(threadId) - before;
            int measuredBlocks = blocks.get() - firstBlock;
            long bytesPerBlock = allocated / measuredBlocks;
            System.out.println("audio-thread allocation: " + bytesPerBlock + " bytes/block");
            assertEquals(0L, bytesPerBlock);
        } finally {
            engine.stop();
        }
    }

    private static void awaitBlocks(AtomicInteger blocks, int target) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (blocks.get() < target && System.nanoTime() < deadline) Thread.sleep(2L);
        assertTrue(blocks.get() >= target, "timed out waiting for audio blocks");
    }

    private static final class TestBank implements SoundBank {
        final CopyOnWriteArrayList<TestVoice> voices = new CopyOnWriteArrayList<>();
        final AtomicInteger starts = new AtomicInteger();
        private final int capacity;

        TestBank(int capacity) {
            this.capacity = capacity;
        }

        @Override
        public Voice newVoice(int midi, int velocity) {
            for (TestVoice voice : voices) {
                if (voice.finished) {
                    voice.start();
                    starts.incrementAndGet();
                    return voice;
                }
            }
            if (voices.size() >= capacity) throw new IllegalStateException("pool exhausted");
            TestVoice voice = new TestVoice();
            voices.add(voice);
            starts.incrementAndGet();
            return voice;
        }
    }

    private static final class TestVoice implements Voice {
        volatile boolean finished;
        volatile boolean releaseRequested;
        volatile long renderThread;
        volatile long releaseThread;
        volatile String renderThreadName;
        volatile CountDownLatch released = new CountDownLatch(1);

        void start() {
            finished = false;
            releaseRequested = false;
            released = new CountDownLatch(1);
        }

        @Override
        public boolean renderAdd(float[] output, int frames) {
            renderThread = Thread.currentThread().threadId();
            renderThreadName = Thread.currentThread().getName();
            for (int i = 0; i < frames; i++) output[i] += 0.01f;
            if (releaseRequested) finished = true;
            return true;
        }

        @Override
        public void release() {
            releaseThread = Thread.currentThread().threadId();
            releaseRequested = true;
            released.countDown();
        }

        @Override
        public boolean isFinished() {
            return finished;
        }
    }
}
