package dev.amado.minepiano.ui;

import dev.amado.minepiano.audio.PianoEngine;
import dev.amado.minepiano.audio.PianoPreset;
import dev.amado.minepiano.config.KeyMap;
import dev.amado.minepiano.config.PianoConfig;
import dev.amado.minepiano.midi.MidiInput;
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
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

/** Audio controls and key rebinding. */
public final class PianoSettingsScreen extends Screen {
    private final Screen parent;
    private final PianoConfig config;
    private final PianoEngine engine;
    private final List<String> keys;
    private final List<String> midiDevices;
    private int binding = -1;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int keymapLabelY = -1;

    public PianoSettingsScreen(Screen parent, PianoConfig config, PianoEngine engine) {
        super(Component.translatable("gui.minepiano.settings"));
        this.parent = parent;
        this.config = config;
        this.engine = engine;
        keys = new ArrayList<>(config.keymap.keySet());
        keys.sort(Comparator.comparingInt(config.keymap::get));
        List<String> devices;
        try {
            devices = MidiInput.deviceNames();
        } catch (RuntimeException ignored) {
            devices = List.of();
        }
        midiDevices = devices;
    }

    @Override
    protected void init() {
        keymapLabelY = -1;
        panelWidth = Math.min(640, Math.max(1, width - 10));
        panelHeight = Math.min(390, Math.max(1, height - 10));
        panelX = (width - panelWidth) / 2;
        panelY = Math.max(5, (height - panelHeight) / 2);
        int left = panelX + 14;
        int contentWidth = Math.max(1, panelWidth - 28);
        boolean controlsFit = contentWidth >= 120;
        Layout layout = layout(panelY, panelHeight, controlsFit && contentWidth >= 120);
        int doneY = layout.doneY();

        if (controlsFit && fits(layout.volumeY(), 24, doneY)) addRenderableWidget(new Stepper(left, layout.volumeY(), contentWidth, 24, Component.translatable("gui.minepiano.master_volume"),
                () -> Math.round(config.masterVolume * 100) + "%", amount -> setVolume(config.masterVolume + amount * .05F)));
        if (controlsFit && fits(layout.presetY(), 24, doneY)) addRenderableWidget(new Stepper(left, layout.presetY(), contentWidth, 24,
                Component.translatable("gui.minepiano.preset"),
                () -> Component.translatable(PianoPreset.fromName(config.presetName).translationKey()).getString(),
                amount -> changePreset(config, engine, amount)));
        if (controlsFit && fits(layout.voiceChatY(), 24, doneY)) addRenderableWidget(new Toggle(left, layout.voiceChatY(), contentWidth, 24,
                Component.translatable("gui.minepiano.transmit_voice_chat"), () -> config.transmitToVoiceChat,
                () -> {
                    config.transmitToVoiceChat = !config.transmitToVoiceChat;
                    VoiceChatOutputHolder.setEnabled(config.transmitToVoiceChat);
                }));
        if (controlsFit && layout.midiToggleY() >= 0 && fits(layout.midiToggleY(), 24, doneY))
            addRenderableWidget(new Toggle(left, layout.midiToggleY(), contentWidth, 24,
                    Component.translatable("gui.minepiano.midi_input"), () -> config.midiEnabled,
                    () -> config.midiEnabled = !config.midiEnabled));
        if (controlsFit && layout.midiDeviceY() >= 0 && fits(layout.midiDeviceY(), 24, doneY))
            addRenderableWidget(new Stepper(left, layout.midiDeviceY(), contentWidth, 24,
                    Component.translatable("gui.minepiano.midi_device"), this::midiDeviceName, this::changeMidiDevice));
        int availableRows = Math.max(0, (doneY - layout.bindingsY() - 4) / 20);
        if (availableRows > 0) {
            keymapLabelY = layout.keymapLabelY();
            int columns = Math.max(1, (keys.size() + availableRows - 1) / availableRows);
            int columnWidth = Math.max(1, contentWidth / columns);
            for (int i = 0; i < keys.size(); i++) {
                int column = i / availableRows;
                int row = i % availableRows;
                addRenderableWidget(new Binding(left + column * columnWidth, layout.bindingsY() + row * 20,
                        Math.max(1, columnWidth - 4), 18, i));
            }
        }
        if (doneY >= panelY + 34) {
            int doneWidth = Math.min(90, contentWidth);
            addRenderableWidget(new PianoScreen.Action(panelX + panelWidth - 14 - doneWidth, doneY, doneWidth, 24,
                    Component.translatable("gui.minepiano.done"), this::onClose));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x77000000);
        PianoScreen.roundedRect(graphics, panelX - 3, panelY + 4, panelWidth + 6, panelHeight, 0x44000000);
        PianoScreen.roundedBorder(graphics, panelX, panelY, panelWidth, panelHeight,
                PianoScreen.BORDER, PianoScreen.PANEL);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font, title.copy().withStyle(ChatFormatting.BOLD), panelX + 14, panelY + 13, PianoScreen.TEXT, false);
        if (keymapLabelY >= 0) graphics.text(font, Component.translatable("gui.minepiano.keymap"),
                panelX + 14, keymapLabelY, PianoScreen.MUTED, false);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (binding >= 0) {
            String name = InputConstants.getKey(event).getDisplayName().getString();
            if (name != null && !name.isBlank() && name.codePointCount(0, name.length()) == 1) {
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
        try { config.save(); } catch (IOException ignored) { }
        minecraft.gui.setScreen(parent);
    }

    private void setVolume(float volume) {
        config.masterVolume = Math.max(0, Math.min(2, volume));
        engine.setMasterGain(config.masterVolume);
    }

    static void changePreset(PianoConfig config, PianoEngine engine, int amount) {
        PianoPreset preset = PianoPreset.fromName(config.presetName).offset(amount);
        config.presetName = preset.name();
        engine.setPreset(preset);
    }

    private static boolean fits(int y, int height, int doneY) {
        return y >= 0 && y + height + 4 <= doneY;
    }

    static Layout layout(int panelY, int panelHeight, boolean withMidi) {
        int cursor = panelY + 38;
        int volume = cursor; cursor += 28;
        int preset = cursor; cursor += 28;
        int voiceChat = cursor; cursor += 28;
        int midiToggle = -1, midiDevice = -1;
        int done = panelY + panelHeight - 34;
        if (withMidi && cursor + 56 + 13 + 20 + 4 <= done) {
            midiToggle = cursor; cursor += 28;
            midiDevice = cursor; cursor += 28;
        }
        int keymapLabel = cursor;
        return new Layout(volume, preset, voiceChat, midiToggle, midiDevice, keymapLabel,
                keymapLabel + 13, done);
    }

    static Layout layout(int panelY, int panelHeight) { return layout(panelY, panelHeight, false); }

    static record Layout(int volumeY, int presetY, int voiceChatY, int midiToggleY, int midiDeviceY,
                         int keymapLabelY, int bindingsY, int doneY) { }

    private String midiDeviceName() {
        if (midiDevices.isEmpty()) return Component.translatable("gui.minepiano.midi_none").getString();
        if (config.midiDevice == null || config.midiDevice.isBlank() || !midiDevices.contains(config.midiDevice))
            return Component.translatable("gui.minepiano.midi_default").getString();
        return config.midiDevice;
    }

    private void changeMidiDevice(int amount) {
        if (midiDevices.isEmpty()) return;
        int index = midiDevices.indexOf(config.midiDevice) + 1;
        index = Math.floorMod(index + amount, midiDevices.size() + 1);
        config.midiDevice = index == 0 ? "" : midiDevices.get(index - 1);
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
            var font = Minecraft.getInstance().font;
            String labelText = label.getString();
            int labelWidth = Math.max(0, getWidth() - 116);
            if (font.width(labelText) > labelWidth) labelText = font.plainSubstrByWidth(labelText, labelWidth);
            graphics.text(font, labelText, getX() + 8, getY() + 8, PianoScreen.TEXT, false);
            int right = getRight() - 7;
            control(graphics, right - 20, "+");
            control(graphics, right - 92, "−");
            String text = value.get();
            if (font.width(text) > 44) text = font.plainSubstrByWidth(text, 38) + "…";
            graphics.centeredText(font, text, right - 46, getY() + 8, PianoScreen.ACCENT);
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
            var font = Minecraft.getInstance().font;
            String labelText = label.getString();
            int labelWidth = Math.max(0, getWidth() - 52);
            if (font.width(labelText) > labelWidth) labelText = font.plainSubstrByWidth(labelText, labelWidth);
            graphics.text(font, labelText, getX() + 8, getY() + 8,
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
