package dev.amado.minepiano.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.locks.LockSupport;

/** Blocking local PCM output, with clock pacing when Java Sound is unavailable. */
public final class LocalOutput implements AutoCloseable {
    private static final int SOURCE_RATE = 48_000;
    private static final int SOURCE_FRAMES = 960;
    private static final long BLOCK_NANOS = 20_000_000L;

    private volatile SourceDataLine line;
    private float outputRate = SOURCE_RATE;
    private int outputFrames = SOURCE_FRAMES;
    private byte[] bytes = new byte[SOURCE_FRAMES * 2];
    private long deadline;

    public LocalOutput() {
        line = openLine();
    }

    private LocalOutput(boolean clockOnly) {
        // Separate constructor makes headless behavior explicit and never probes a device.
    }

    public static LocalOutput clockPaced() {
        return new LocalOutput(true);
    }

    void write(short[] frame, int length) {
        if (length != SOURCE_FRAMES) throw new IllegalArgumentException("Expected 960 samples");
        SourceDataLine current = line;
        if (current == null) {
            paceClock();
            return;
        }

        encode(frame);
        try {
            int offset = 0;
            while (offset < bytes.length) {
                int written = current.write(bytes, offset, bytes.length - offset);
                if (written <= 0) throw new IllegalStateException("Audio line stopped accepting data");
                offset += written;
            }
        } catch (RuntimeException failure) {
            line = null;
            current.close();
            deadline = 0L;
            paceClock();
        }
    }

    private void encode(short[] frame) {
        if (outputFrames == SOURCE_FRAMES) {
            for (int i = 0, b = 0; i < SOURCE_FRAMES; i++) {
                short sample = frame[i];
                bytes[b++] = (byte) sample;
                bytes[b++] = (byte) (sample >>> 8);
            }
            return;
        }

        double step = (SOURCE_FRAMES - 1.0) / (outputFrames - 1.0);
        for (int i = 0, b = 0; i < outputFrames; i++) {
            double position = i * step;
            int lower = (int) position;
            int upper = Math.min(lower + 1, SOURCE_FRAMES - 1);
            double fraction = position - lower;
            short sample = (short) Math.round(frame[lower] + (frame[upper] - frame[lower]) * fraction);
            bytes[b++] = (byte) sample;
            bytes[b++] = (byte) (sample >>> 8);
        }
    }

    private void paceClock() {
        long now = System.nanoTime();
        if (deadline == 0L) deadline = now;
        deadline += BLOCK_NANOS;
        if (deadline <= now) deadline = now + BLOCK_NANOS; // Never catch up with an output burst.
        long remaining;
        while ((remaining = deadline - System.nanoTime()) > 0L) LockSupport.parkNanos(remaining);
    }

    private SourceDataLine openLine() {
        SourceDataLine preferred = tryOpen(null, SOURCE_RATE);
        if (preferred != null) return preferred;

        for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            for (Line.Info info : mixer.getSourceLineInfo()) {
                if (!(info instanceof DataLine.Info dataInfo)) continue;
                for (AudioFormat format : dataInfo.getFormats()) {
                    float rate = format.getSampleRate();
                    if (rate <= 0 || rate == SOURCE_RATE || format.getChannels() != 1
                            || format.getSampleSizeInBits() != 16
                            || !format.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED)) continue;
                    SourceDataLine fallback = tryOpen(mixer, rate);
                    if (fallback != null) return fallback;
                }
            }
        }
        return tryOpen(null, 44_100);
    }

    private SourceDataLine tryOpen(Mixer mixer, float rate) {
        AudioFormat format = new AudioFormat(rate, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        try {
            SourceDataLine candidate = mixer == null
                    ? AudioSystem.getSourceDataLine(format)
                    : (SourceDataLine) mixer.getLine(info);
            int frames = Math.max(2, Math.round(rate / 50.0f));
            candidate.open(format, frames * 2);
            candidate.start();
            outputRate = rate;
            outputFrames = frames;
            bytes = new byte[frames * 2];
            return candidate;
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException failure) {
            return null;
        }
    }

    float outputRate() {
        return outputRate;
    }

    @Override
    public void close() {
        SourceDataLine current = line;
        line = null;
        if (current != null) current.close();
    }
}
