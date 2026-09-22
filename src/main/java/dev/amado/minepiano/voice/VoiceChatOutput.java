package dev.amado.minepiano.voice;

import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.api.audiochannel.ClientLocationalAudioChannel;
import dev.amado.minepiano.audio.FrameConsumer;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class VoiceChatOutput implements FrameConsumer, VoiceChatOutputHolder.Output {
    private static final int FRAME_SAMPLES = 960;

    private final AtomicReference<Connection> connection = new AtomicReference<>();
    private volatile Location location = new Location(0D, 0D, 0D);
    private volatile float distance = Float.NaN;

    synchronized void connect(VoicechatClientApi api) {
        Location currentLocation = location;
        ClientLocationalAudioChannel channel = api.createLocationalAudioChannel(
                UUID.randomUUID(),
                api.createPosition(currentLocation.x, currentLocation.y, currentLocation.z)
        );
        channel.setDistance(configuredDistance(api));
        connection.set(new Connection(api, channel));
    }

    synchronized void disconnect() {
        connection.set(null);
    }

    @Override
    public void accept(short[] frame, int length) {
        if (length != FRAME_SAMPLES || frame == null || frame.length < FRAME_SAMPLES) {
            return;
        }

        Connection current = connection.get();
        if (current == null || current.api.isDisconnected() || current.api.isDisabled()) {
            connection.compareAndSet(current, null);
            return;
        }

        current.channel.play(frame.length == FRAME_SAMPLES ? frame : Arrays.copyOf(frame, FRAME_SAMPLES));
    }

    @Override
    public synchronized void setLocation(double x, double y, double z) {
        Location newLocation = new Location(x, y, z);
        location = newLocation;
        Connection current = connection.get();
        if (current != null) {
            Position position = current.api.createPosition(x, y, z);
            current.channel.setLocation(position);
        }
    }

    @Override
    public synchronized void setDistance(float distance) {
        this.distance = distance;
        Connection current = connection.get();
        if (current != null) {
            current.channel.setDistance(configuredDistance(current.api));
        }
    }

    private float configuredDistance(VoicechatClientApi api) {
        return Float.isFinite(distance) && distance > 0F ? distance : (float) api.getVoiceChatDistance();
    }

    private record Location(double x, double y, double z) {
    }

    private record Connection(VoicechatClientApi api, ClientLocationalAudioChannel channel) {
    }
}
