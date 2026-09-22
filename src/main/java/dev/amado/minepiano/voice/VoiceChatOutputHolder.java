package dev.amado.minepiano.voice;

import dev.amado.minepiano.audio.FrameConsumer;

/** SVC-free bridge used by the client runtime, even when Simple Voice Chat is absent. */
public final class VoiceChatOutputHolder {
    private static final Bridge OUTPUT = new Bridge();

    private VoiceChatOutputHolder() {
    }

    public static FrameConsumer getOutput() {
        return OUTPUT;
    }

    public static void setLocation(double x, double y, double z) {
        OUTPUT.setLocation(x, y, z);
    }

    public static void setDistance(float distance) {
        OUTPUT.setDistance(distance);
    }

    static void install(Output output) {
        OUTPUT.install(output);
    }

    interface Output extends FrameConsumer {
        void setLocation(double x, double y, double z);

        void setDistance(float distance);
    }

    private static final class Bridge implements Output {
        private volatile Output delegate;
        private volatile double x;
        private volatile double y;
        private volatile double z;
        private volatile float distance = Float.NaN;

        private synchronized void install(Output output) {
            output.setLocation(x, y, z);
            output.setDistance(distance);
            delegate = output;
        }

        @Override
        public void accept(short[] frame, int length) {
            Output output = delegate;
            if (output != null) {
                output.accept(frame, length);
            }
        }

        @Override
        public synchronized void setLocation(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            Output output = delegate;
            if (output != null) {
                output.setLocation(x, y, z);
            }
        }

        @Override
        public synchronized void setDistance(float distance) {
            this.distance = distance;
            Output output = delegate;
            if (output != null) {
                output.setDistance(distance);
            }
        }
    }
}
