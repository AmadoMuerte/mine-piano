package dev.amado.minepiano.audio;

/** Bounded lock-free single-producer/single-consumer PCM ring. */
public final class PcmRingBuffer {
    private final short[] samples;
    private final int mask;
    private volatile long readPosition;
    private volatile long writePosition;

    public PcmRingBuffer(int capacity) {
        if (capacity <= 0 || (capacity & (capacity - 1)) != 0) {
            throw new IllegalArgumentException("Capacity must be a positive power of two");
        }
        samples = new short[capacity];
        mask = capacity - 1;
    }

    public int capacity() {
        return samples.length;
    }

    public int availableToRead() {
        return (int) (writePosition - readPosition);
    }

    public int availableToWrite() {
        return samples.length - availableToRead();
    }

    public int write(short[] source, int offset, int length) {
        checkRange(source.length, offset, length);
        long write = writePosition;
        int count = Math.min(length, samples.length - (int) (write - readPosition));
        int index = (int) write & mask;
        int first = Math.min(count, samples.length - index);
        System.arraycopy(source, offset, samples, index, first);
        System.arraycopy(source, offset + first, samples, 0, count - first);
        writePosition = write + count;
        return count;
    }

    public int read(short[] destination, int offset, int length) {
        checkRange(destination.length, offset, length);
        long read = readPosition;
        int count = Math.min(length, (int) (writePosition - read));
        int index = (int) read & mask;
        int first = Math.min(count, samples.length - index);
        System.arraycopy(samples, index, destination, offset, first);
        System.arraycopy(samples, 0, destination, offset + first, count - first);
        readPosition = read + count;
        return count;
    }

    private static void checkRange(int arrayLength, int offset, int length) {
        if (offset < 0 || length < 0 || offset > arrayLength - length) {
            throw new IndexOutOfBoundsException("Invalid offset or length");
        }
    }
}
