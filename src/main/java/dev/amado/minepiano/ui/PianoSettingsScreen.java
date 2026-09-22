package dev.amado.minepiano.ui;

import dev.amado.minepiano.audio.PianoEngine;
import dev.amado.minepiano.config.KeyMap;
import dev.amado.minepiano.config.PianoConfig;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Settings kept in one screen: values, SoundFont path, and key rebinding. */
public final class PianoSettingsScreen extends Screen {
    private final Screen parent;
    private final PianoConfig config;
    private final PianoEngine engine;
    private final List<String> keys;
    private EditBox soundfont;
    private int binding = -1;

    public PianoSettingsScreen(Screen parent, PianoConfig config, PianoEngine engine) {
        super(Component.translatable("gui.minepiano.settings"));
        this.parent = parent;
        this.config = config;
        this.engine = engine;
        keys = new ArrayList<>(config.keymap.keySet());
        keys.sort(Comparator.comparingInt(config.keymap::get));
    }

    @Override
    protected void init() {
        soundfont = addRenderableWidget(new EditBox(font, 12, 74, Math.max(80, width - 24), 20,
                Component.translatable("gui.minepiano.soundfont")));
        soundfont.setValue(config.soundfont);
        soundfont.setResponder(value -> config.soundfont = value);
        addRenderableWidget(new PianoScreen.Action(12, height - 28, 50, 20, Component.literal("Done"), this::onClose));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font, title, 12, 12, 0xFFFFFFFF, true);
        graphics.text(font, Component.translatable("gui.minepiano.velocity").getString() + ": " + config.velocity + "  [- / +]", 12, 30, 0xFFFFFFFF, false);
        graphics.text(font, Component.translatable("gui.minepiano.master_volume").getString() + ": " + Math.round(config.masterVolume * 100) + "%  [, / .]", 12, 43, 0xFFFFFFFF, false);
        graphics.text(font, Component.translatable("gui.minepiano.svc_distance").getString() + ": " + config.svcDistance + "  [; / ']", 12, 56, 0xFFFFFFFF, false);
        graphics.text(font, Component.translatable("gui.minepiano.soundfont"), 12, 64, 0xFFFFFFFF, false);
        int y = 101;
        graphics.text(font, Component.translatable("gui.minepiano.keymap"), 12, 88, 0xFFFFFFFF, true);
        for (int i = 0; i < keys.size() && y < height - 34; i++, y += 12) {
            String key = keys.get(i);
            String text = (i == binding ? "> press key <" : key) + " = " + config.keymap.get(key);
            graphics.text(font, text, 12, y, i == binding ? 0xFFFFFF55 : 0xFFFFFFFF, false);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.x() >= 12 && event.x() < 160 && event.y() >= 101) {
            int row = (int) ((event.y() - 101) / 12);
            if (row >= 0 && row < keys.size()) {
                binding = row;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (binding >= 0) {
            String name = GLFW.glfwGetKeyName(event.key(), event.scancode());
            if (name != null) {
                String old = keys.get(binding);
                int offset = config.keymap.remove(old);
                new KeyMap(config).bind(name, offset);
                keys.set(binding, name.toUpperCase(java.util.Locale.ROOT));
                binding = -1;
            }
            return true;
        }
        switch (event.key()) {
            case GLFW.GLFW_KEY_MINUS -> config.velocity = Math.max(0, config.velocity - 5);
            case GLFW.GLFW_KEY_EQUAL -> config.velocity = Math.min(127, config.velocity + 5);
            case GLFW.GLFW_KEY_COMMA -> setVolume(config.masterVolume - .05F);
            case GLFW.GLFW_KEY_PERIOD -> setVolume(config.masterVolume + .05F);
            case GLFW.GLFW_KEY_SEMICOLON -> config.svcDistance = Math.max(1, config.svcDistance - 1);
            case GLFW.GLFW_KEY_APOSTROPHE -> config.svcDistance += 1;
            default -> { return super.keyPressed(event); }
        }
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() { return true; }

    @Override
    public void onClose() {
        config.soundfont = soundfont.getValue();
        try { config.save(); } catch (IOException ignored) { }
        minecraft.gui.setScreen(parent);
    }

    private void setVolume(float volume) {
        config.masterVolume = Math.max(0, Math.min(2, volume));
        engine.setMasterGain(config.masterVolume);
    }
}
