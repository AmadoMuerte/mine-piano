package dev.amado.minepiano.audio;

/** Produces 48 kHz mono 20 ms (960-sample) frames. addFrameConsumer is safe from another thread. */
public interface PianoEngine {
    void noteOn(int midi, int velocity); // 0..127
    void noteOff(int midi);
    void setSustain(boolean on);
    void allNotesOff();
    void setMasterGain(float gain);
    boolean isNoteActive(int midi);
    void addFrameConsumer(FrameConsumer consumer);
    void start();
    void stop();
}
