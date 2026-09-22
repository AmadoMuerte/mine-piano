package dev.amado.minepiano.audio.sf2;

/** One sample header backed by the SoundFont's preloaded PCM array. */
public final class Sf2Sample {
    final float[] pcm;
    final int start;
    final int end;
    final int loopStart;
    final int loopEnd;
    final int sampleRate;
    final int originalPitch;
    final int pitchCorrection;
    private final String name;

    Sf2Sample(String name, float[] pcm, int start, int end, int loopStart, int loopEnd,
              int sampleRate, int originalPitch, int pitchCorrection) {
        this.name = name;
        this.pcm = pcm;
        this.start = start;
        this.end = end;
        this.loopStart = loopStart;
        this.loopEnd = loopEnd;
        this.sampleRate = sampleRate;
        this.originalPitch = originalPitch;
        this.pitchCorrection = pitchCorrection;
    }

    public String name() { return name; }
    public int length() { return end - start; }
    public int sampleRate() { return sampleRate; }
}
