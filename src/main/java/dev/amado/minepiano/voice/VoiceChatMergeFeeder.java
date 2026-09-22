package dev.amado.minepiano.voice;

import dev.amado.minepiano.audio.FrameConsumer;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/** Lock-free queue between the piano audio thread and SVC microphone thread. */
final class VoiceChatMergeFeeder implements FrameConsumer {
    static final int FRAME_SAMPLES = 960;
    static final int CAPACITY = 8;

    private final short[][] frames = new short[CAPACITY][FRAME_SAMPLES];
    private final short[] mergeFrame = new short[FRAME_SAMPLES];
    private final AtomicLongArray sequences = new AtomicLongArray(CAPACITY);
    private final AtomicLong write = new AtomicLong();
    private final AtomicLong read = new AtomicLong();
    private volatile boolean enabled = true;

    VoiceChatMergeFeeder() {
        for (int index = 0; index < CAPACITY; index++) sequences.set(index, index);
    }

    @Override
    public void accept(short[] frame, int length) {
        if (!enabled || frame == null || length != FRAME_SAMPLES || frame.length < FRAME_SAMPLES) return;
        while (!offer(frame)) discardOldest();
        if (!enabled) clear();
    }

    short[] poll() {
        return enabled && pollInto(mergeFrame) ? mergeFrame : null;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) clear();
    }

    void clear() {
        while (discardOldest()) { }
    }

    private boolean offer(short[] frame) {
        while (true) {
            long position = write.get();
            int index = (int) (position & (CAPACITY - 1));
            long difference = sequences.get(index) - position;
            if (difference < 0) return false;
            if (difference == 0 && write.compareAndSet(position, position + 1)) {
                System.arraycopy(frame, 0, frames[index], 0, FRAME_SAMPLES);
                sequences.set(index, position + 1);
                return true;
            }
        }
    }

    private boolean discardOldest() {
        return pollInto(null);
    }

    private boolean pollInto(short[] destination) {
        while (true) {
            long position = read.get();
            int index = (int) (position & (CAPACITY - 1));
            long difference = sequences.get(index) - (position + 1);
            if (difference < 0) return false;
            if (difference == 0 && read.compareAndSet(position, position + 1)) {
                if (destination != null)
                    System.arraycopy(frames[index], 0, destination, 0, FRAME_SAMPLES);
                sequences.set(index, position + CAPACITY);
                return true;
            }
        }
    }
}
