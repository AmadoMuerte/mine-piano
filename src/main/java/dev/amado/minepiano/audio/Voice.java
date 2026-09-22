package dev.amado.minepiano.audio;

/** Renders into 48 kHz mono 20 ms (960-sample) frames. */
public interface Voice {
    boolean renderAdd(float[] out, int frames);
    void release();
    boolean isFinished();
}
