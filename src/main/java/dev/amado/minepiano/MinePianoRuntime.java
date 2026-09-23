package dev.amado.minepiano;

import dev.amado.minepiano.audio.PianoEngine;
import dev.amado.minepiano.audio.PianoEngineImpl;
import dev.amado.minepiano.config.PianoConfig;
import dev.amado.minepiano.ui.PianoScreen;
import dev.amado.minepiano.voice.VoiceChatOutputHolder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/** Client-side owner for piano audio and its screen. */
public final class MinePianoRuntime {
    private static final PianoConfig CONFIG = PianoConfig.load();
    private static PianoEngine engine;
    private static boolean initialized;

    private MinePianoRuntime() {
    }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        MinePianoClient.setScreenOpener(() -> new PianoScreen(engine(), CONFIG));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            MinePianoClient.openPianoIfRequested();
            if (client.player == null || client.level == null) {
                if (engine != null) engine.allNotesOff();
                return;
            }
        });
    }

    public static PianoEngine engine() {
        if (engine == null) {
            engine = new PianoEngineImpl();
            engine.setPreset(dev.amado.minepiano.audio.PianoPreset.fromName(CONFIG.presetName));
            VoiceChatOutputHolder.setEnabled(CONFIG.transmitToVoiceChat);
            engine.addFrameConsumer(VoiceChatOutputHolder.getFeeder());
            engine.setMasterGain(CONFIG.masterVolume);
            engine.setSustain(CONFIG.sustain);
            engine.start();
        }
        return engine;
    }

    public static PianoConfig config() {
        return CONFIG;
    }
}
