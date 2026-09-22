package dev.amado.minepiano.audio;

/** Accepts 48 kHz mono 20 ms (960-sample) frames. */
public interface FrameConsumer {
    void accept(short[] frame, int length);
}
