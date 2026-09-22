package dev.amado.minepiano.voice;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MergeClientSoundEvent;

public final class PianoVoicechatPlugin implements VoicechatPlugin {
    @Override
    public String getPluginId() {
        return "minepiano";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(ClientVoicechatConnectionEvent.class, this::onConnection);
        registration.registerEvent(MergeClientSoundEvent.class, this::onMergeSound);
    }

    private void onConnection(ClientVoicechatConnectionEvent event) {
        if (!event.isConnected()) VoiceChatOutputHolder.clear();
    }

    private void onMergeSound(MergeClientSoundEvent event) {
        short[] frame = VoiceChatOutputHolder.poll();
        if (frame != null) event.mergeAudio(frame);
    }
}
