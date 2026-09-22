package dev.amado.minepiano.audio.sf2;

/** Fully resolved preset/instrument zone. */
public final class Sf2Zone {
    final Sf2Sample sample;
    final int lowKey;
    final int highKey;
    final int lowVelocity;
    final int highVelocity;
    final int rootKey;
    final int coarseTune;
    final int fineTune;
    final int sampleModes;
    final float gain;
    final int attackFrames;
    final int holdFrames;
    final int decayFrames;
    final float sustainGain;
    final float decayMultiplier;
    final int releaseFrames;

    Sf2Zone(Sf2Sample sample, int lowKey, int highKey, int lowVelocity, int highVelocity,
            int rootKey, int coarseTune, int fineTune, int sampleModes, float gain,
            int attackFrames, int holdFrames, int decayFrames, float sustainGain,
            float decayMultiplier, int releaseFrames) {
        this.sample = sample;
        this.lowKey = lowKey;
        this.highKey = highKey;
        this.lowVelocity = lowVelocity;
        this.highVelocity = highVelocity;
        this.rootKey = rootKey;
        this.coarseTune = coarseTune;
        this.fineTune = fineTune;
        this.sampleModes = sampleModes;
        this.gain = gain;
        this.attackFrames = attackFrames;
        this.holdFrames = holdFrames;
        this.decayFrames = decayFrames;
        this.sustainGain = sustainGain;
        this.decayMultiplier = decayMultiplier;
        this.releaseFrames = releaseFrames;
    }

    public Sf2Sample sample() { return sample; }
    public int lowKey() { return lowKey; }
    public int highKey() { return highKey; }
    public int lowVelocity() { return lowVelocity; }
    public int highVelocity() { return highVelocity; }
}
