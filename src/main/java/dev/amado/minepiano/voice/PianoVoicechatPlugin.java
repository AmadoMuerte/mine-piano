package dev.amado.minepiano.voice;

import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;

public final class PianoVoicechatPlugin implements VoicechatPlugin {
    private final VoiceChatOutput output = new VoiceChatOutput();

    public PianoVoicechatPlugin() {
        VoiceChatOutputHolder.install(output);
    }

    @Override
    public String getPluginId() {
        return "minepiano";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(ClientVoicechatConnectionEvent.class, this::onConnection);
    }

    private void onConnection(ClientVoicechatConnectionEvent event) {
        VoicechatClientApi api = event.getVoicechat();
        if (event.isConnected() && !api.isDisconnected() && !api.isDisabled()) {
            output.connect(api);
        } else {
            output.disconnect();
        }
    }
}
