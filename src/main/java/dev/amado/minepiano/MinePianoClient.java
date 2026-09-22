package dev.amado.minepiano;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Supplier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

public final class MinePianoClient implements ClientModInitializer {
    public static final KeyMapping OPEN_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.minepiano.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, KeyMapping.Category.MISC));
    private static Supplier<Screen> screenOpener;

    @Override
    public void onInitializeClient() {
        MinePianoRuntime.initialize();
    }

    public static void setScreenOpener(Supplier<Screen> opener) {
        screenOpener = opener;
    }

    /** Called by T6's client lifecycle owner. */
    public static Screen openPianoIfRequested() {
        if (!OPEN_KEY.consumeClick() || screenOpener == null) return null;
        Screen screen = screenOpener.get();
        Minecraft.getInstance().setScreenAndShow(screen);
        return screen;
    }
}
