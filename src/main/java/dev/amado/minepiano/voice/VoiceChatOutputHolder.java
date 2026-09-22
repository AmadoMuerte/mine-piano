package dev.amado.minepiano.voice;

import dev.amado.minepiano.audio.FrameConsumer;

/** SVC-free feeder holder used by the client runtime, even when Simple Voice Chat is absent. */
public final class VoiceChatOutputHolder {
    private static final VoiceChatMergeFeeder FEEDER = new VoiceChatMergeFeeder();

    private VoiceChatOutputHolder() {
    }

    public static FrameConsumer getFeeder() {
        return FEEDER;
    }

    public static void setEnabled(boolean enabled) {
        FEEDER.setEnabled(enabled);
    }

    static short[] poll() {
        return FEEDER.poll();
    }

    static void clear() {
        FEEDER.clear();
    }
}
