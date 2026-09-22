package dev.amado.minepiano.ui;

import dev.amado.minepiano.audio.PianoEngine;
import dev.amado.minepiano.config.KeyMap;
import dev.amado.minepiano.config.PianoConfig;
import dev.amado.minepiano.voice.VoiceChatOutputHolder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Mouse-driven values, SoundFont path, and key rebinding. */
public final class PianoSettingsScreen extends Screen {
    private final Screen parent;
    private final PianoConfig config;
    private final PianoEngine engine;
    private final List<String> keys;
    private EditBox soundfont;
    private int binding = -1;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

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
        panelWidth = Math.min(640, Math.max(300, width - 20));
        panelHeight = Math.min(390, Math.max(230, height - 20));
        panelX = (width - panelWidth) / 2;
        panelY = Math.max(5, (height - panelHeight) / 2);
        int left = panelX + 14;
        int contentWidth = panelWidth - 28;
        int y = panelY + 42;

        addRenderableWidget(new Stepper(left, y, contentWidth, 24, Component.translatable("gui.minepiano.velocity"),
                () -> Integer.toString(config.velocity), amount -> config.velocity = clamp(config.velocity + amount * 5, 0, 127)));
        y += 28;
        addRenderableWidget(new Stepper(left, y, contentWidth, 24, Component.translatable("gui.minepiano.master_volume"),
                () -> Math.round(config.masterVolume * 100) + "%", amount -> setVolume(config.masterVolume + amount * .05F)));
        y += 28;
        addRenderableWidget(new Toggle(left, y, contentWidth, 24,
                Component.translatable("gui.minepiano.transmit_voice_chat"), () -> config.transmitToVoiceChat,
                () -> {
                    config.transmitToVoiceChat = !config.transmitToVoiceChat;
                    VoiceChatOutputHolder.setEnabled(config.transmitToVoiceChat);
                }));
        y += 40;

        soundfont = addRenderableWidget(new EditBox(font, left, y, contentWidth, 22,
                Component.translatable("gui.minepiano.soundfont")));
        soundfont.setValue(config.soundfont);
        soundfont.setResponder(value -> config.soundfont = value);
        soundfont.setHint(Component.translatable("gui.minepiano.soundfont_hint"));
        y += 42;

        int doneY = panelY + panelHeight - 34;
        int rows = Math.max(1, (doneY - y - 4) / 20);
        int columns = Math.max(1, (keys.size() + rows - 1) / rows);
        int columnWidth = contentWidth / columns;
        for (int i = 0; i < keys.size(); i++) {
            int column = i / rows;
            int row = i % rows;
            addRenderableWidget(new Binding(left + column * columnWidth, y + row * 20,
                    columnWidth - 4, 18, i));
        }
        addRenderableWidget(new PianoScreen.Action(panelX + panelWidth - 104, doneY, 90, 24,
                Component.translatable("gui.minepiano.done"), this::onClose));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x77000000);
        PianoScreen.roundedRect(graphics, panelX - 3, panelY + 4, panelWidth + 6, panelHeight, 0x44000000);
        PianoScreen.roundedBorder(graphics, panelX, panelY, panelWidth, panelHeight,
                PianoScreen.BORDER, PianoScreen.PANEL);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font, title.copy().withStyle(ChatFormatting.BOLD), panelX + 14, panelY + 13, PianoScreen.TEXT, false);
        graphics.text(font, Component.translatable("gui.minepiano.soundfont"), panelX + 14, panelY + 121,
                PianoScreen.MUTED, false);
        graphics.text(font, Component.translatable("gui.minepiano.keymap"), panelX + 14, panelY + 163,
                PianoScreen.MUTED, false);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (binding >= 0) {
            String name = GLFW.glfwGetKeyName(event.key(), event.scancode());
            if (name != null && !name.isBlank()) {
                String old = keys.get(binding);
                String normalized = name.toUpperCase(Locale.ROOT);
                int offset = config.keymap.remove(old);
                Integer displaced = normalized.equals(old) ? null : config.keymap.remove(normalized);
                if (displaced != null) {
                    new KeyMap(config).bind(old, displaced);
                    for (int i = 0; i < keys.size(); i++)
                        if (i != binding && keys.get(i).equals(normalized)) keys.set(i, old);
                }
                new KeyMap(config).bind(normalized, offset);
                keys.set(binding, normalized);
                binding = -1;
                rebuildWidgets();
            }
            return true;
        }
        return super.keyPressed(event);
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

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class Stepper extends AbstractWidget {
        private final Component label;
        private final Supplier<String> value;
        private final Consumer<Integer> changed;

        Stepper(int x, int y, int width, int height, Component label, Supplier<String> value,
                Consumer<Integer> changed) {
            super(x, y, width, height, label);
            this.label = label;
            this.value = value;
            this.changed = changed;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            PianoScreen.roundedBorder(graphics, getX(), getY(), getWidth(), getHeight(), PianoScreen.BORDER,
                    isHoveredOrFocused() ? 0xFF302D29 : PianoScreen.CONTROL);
            graphics.text(Minecraft.getInstance().font, label, getX() + 8, getY() + 8, PianoScreen.TEXT, false);
            int right = getRight() - 7;
            control(graphics, right - 20, "+");
            control(graphics, right - 92, "−");
            String text = value.get();
            graphics.centeredText(Minecraft.getInstance().font, text, right - 46, getY() + 8, PianoScreen.ACCENT);
            setMessage(label.copy().append(": " + text));
        }

        private void control(GuiGraphicsExtractor graphics, int x, String text) {
            PianoScreen.roundedBorder(graphics, x, getY() + 3, 20, getHeight() - 6,
                    PianoScreen.BORDER, 0xFF201F1D);
            graphics.centeredText(Minecraft.getInstance().font, text, x + 10, getY() + 8, PianoScreen.TEXT);
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            int right = getRight() - 7;
            if (event.x() >= right - 24) changed.accept(1);
            else if (event.x() >= right - 96 && event.x() < right - 68) changed.accept(-1);
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }

    private static final class Toggle extends AbstractWidget {
        private final Component label;
        private final BooleanSupplier value;
        private final Runnable changed;

        Toggle(int x, int y, int width, int height, Component label, BooleanSupplier value, Runnable changed) {
            super(x, y, width, height, label);
            this.label = label;
            this.value = value;
            this.changed = changed;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            boolean on = value.getAsBoolean();
            PianoScreen.roundedBorder(graphics, getX(), getY(), getWidth(), getHeight(),
                    isHoveredOrFocused() ? PianoScreen.ACCENT : PianoScreen.BORDER, PianoScreen.CONTROL);
            int switchX = getRight() - 33;
            int switchY = getY() + 5;
            PianoScreen.roundedRect(graphics, switchX, switchY, 28, 14, on ? PianoScreen.ACCENT : 0xFF55514C);
            PianoScreen.roundedRect(graphics, switchX + (on ? 16 : 2), switchY + 2, 10, 10, 0xFFF8F4EF);
            Component state = Component.translatable(on ? "gui.minepiano.on" : "gui.minepiano.off");
            graphics.text(Minecraft.getInstance().font, label, getX() + 8, getY() + 8,
                    on ? PianoScreen.ACCENT : PianoScreen.TEXT, false);
            setMessage(label.copy().append(": ").append(state));
        }

        @Override public void onClick(MouseButtonEvent event, boolean doubleClick) { changed.run(); }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }

    private final class Binding extends AbstractWidget {
        private final int index;

        Binding(int x, int y, int width, int height, int index) {
            super(x, y, width, height, Component.translatable("gui.minepiano.binding", keys.get(index)));
            this.index = index;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            boolean selected = binding == index;
            PianoScreen.roundedBorder(graphics, getX(), getY(), getWidth(), getHeight(),
                    selected || isHoveredOrFocused() ? PianoScreen.ACCENT : PianoScreen.BORDER,
                    selected ? 0xFF443426 : PianoScreen.CONTROL);
            String text = selected ? Component.translatable("gui.minepiano.press_key").getString()
                    : noteName(config.keymap.get(keys.get(index))) + "  •  " + keys.get(index);
            if (font.width(text) > getWidth() - 8)
                text = font.plainSubstrByWidth(text, Math.max(0, getWidth() - 14)) + "…";
            graphics.text(font, text, getX() + 5, getY() + 5,
                    selected ? PianoScreen.ACCENT : PianoScreen.TEXT, false);
        }

        @Override public void onClick(MouseButtonEvent event, boolean doubleClick) { binding = index; }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }

    private static String noteName(int offset) {
        String[] notes = {"C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B"};
        return notes[Math.floorMod(offset, 12)] + (offset / 12 + 4);
    }
}
