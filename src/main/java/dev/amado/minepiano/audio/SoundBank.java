package dev.amado.minepiano.audio;

/** Supplies voices rendered into 48 kHz mono 20 ms (960-sample) frames. */
public interface SoundBank {
    Voice newVoice(int midi, int velocity);
}
