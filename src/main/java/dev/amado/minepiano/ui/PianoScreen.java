package dev.amado.minepiano.ui;

import dev.amado.minepiano.audio.PianoEngine;
import dev.amado.minepiano.config.KeyMap;
import dev.amado.minepiano.config.PianoConfig;
import dev.amado.minepiano.input.PianoInput;
import dev.amado.minepiano.midi.MidiInput;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.BooleanSupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Bottom-docked piano window, mouse controls, and keyboard input. */
public final class PianoScreen extends Screen {
    static final int FIXED_VELOCITY = 100;
    static final int ACCENT = 0xFFF0A64A;
    static final int TEXT = 0xFFF4F1ED;
    static final int MUTED = 0xFFA9A39C;
    static final int PANEL = 0xEE171614;
    static final int CONTROL = 0xFF292724;
    static final int BORDER = 0xFF45413C;

    private final PianoEngine engine;
    private final PianoConfig config;
    private final KeyMap keyMap;
    private final PianoInput input;
    private final MidiInput midi;
    private final Map<Integer, Integer> heldNotes = new HashMap<>();
    private PianoKeyboard keyboard;
    private boolean midiStarted;
    private int midiTicks;
    private boolean showHints;
    private boolean controlsCollapsed;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int headerHeight;
    private int keyboardBottom;
    private int titleRight;
    private int octaveDisplayX;
    private int octaveDisplayWidth;
    private int bottomControlY;
    private int bottomControlHeight;

    public PianoScreen(PianoEngine engine, PianoConfig config) {
        super(Component.translatable("gui.minepiano.title"));
        this.engine = engine;
        this.midi = new MidiInput(engine);
        this.config = config;
        keyMap = new KeyMap(config);
        input = new PianoInput(keyMap, config.octave, config.sustain);
    }

    @Override
    protected void init() {
        if (keyboard != null) keyboard.releaseMouse();
        PanelLayout layout = layout(width, height, controlsCollapsed);
        Rect panel = layout.panel();
        Rect header = layout.header();
        Rect keyboard = layout.keyboard();
        Rect bottomBar = layout.bottomBar();
        panelX = panel.x();
        panelY = panel.y();
        panelWidth = panel.width();
        panelHeight = panel.height();
        headerHeight = header.height();
        keyboardBottom = keyboard.bottom();

        int inset = Math.min(12, Math.max(0, (header.width() - 1) / 10));
        int available = Math.max(1, header.width() - inset * 2);
        int gap = available >= 21 ? Math.min(6, (available - 3) / 3) : 0;
        int buttonWidth = Math.min(22, Math.max(1, (available - gap * 2) / 4));
        int sustainWidth = Math.max(1, Math.min(134, available - buttonWidth * 2 - gap * 2));
        int right = header.right() - inset;
        int controlHeight = Math.max(1, Math.min(22, header.height() - 4));
        int controlY = header.y() + (header.height() - controlHeight) / 2;
        if (available < 3) {
            titleRight = panelX;
            addRenderableWidget(new Action(right - available, controlY, available, controlHeight,
                    Component.literal("×"), this::onClose));
        } else {
            addRenderableWidget(new Action(right - buttonWidth, controlY, buttonWidth, controlHeight,
                    Component.literal("×"), this::onClose));
            right -= buttonWidth + gap;
            addRenderableWidget(new Action(right - buttonWidth, controlY, buttonWidth, controlHeight,
                    Component.literal("⚙"), this::openSettings));
            right -= buttonWidth + gap;
            int sustainX = right - sustainWidth;
            titleRight = sustainX - 4;
            addRenderableWidget(new Toggle(sustainX, controlY, sustainWidth, controlHeight,
                    Component.translatable("gui.minepiano.sustain"), input::sustain, this::toggleSustain, true));
        }

        this.keyboard = addRenderableWidget(new PianoKeyboard(keyboard.x(), keyboard.y(), keyboard.width(), keyboard.height(),
                engine, input.octave(), keyMap, showHints));

        int barHeight = Math.min(26, bottomBar.height());
        int barY = bottomBar.y() + Math.min(7, Math.max(0, (bottomBar.height() - barHeight) / 2));
        bottomControlY = barY;
        bottomControlHeight = barHeight;
        int barInset = Math.min(10, Math.max(0, (bottomBar.width() - 1) / 10));
        int collapseWidth = Math.min(20, Math.max(1, bottomBar.width() - barInset * 2));
        int collapseX = bottomBar.right() - barInset - collapseWidth;
        int itemGap = bottomBar.width() >= 60 ? 4 : 0;
        octaveDisplayWidth = 0;
        if (!controlsCollapsed) {
            int x = bottomBar.x() + barInset;
            int controlsWidth = collapseX - itemGap - x - itemGap * 3;
            if (controlsWidth >= 4) {
                int[] widths = {144, 42, 144, 180};
                int desired = 510;
                if (controlsWidth < desired) {
                    int used = 0;
                    for (int i = 0; i < 3; i++) {
                        widths[i] = Math.max(1, controlsWidth * widths[i] / desired);
                        used += widths[i];
                    }
                    widths[3] = Math.max(1, controlsWidth - used);
                }
                addRenderableWidget(new Action(x, barY, widths[0], barHeight,
                        Component.translatable("gui.minepiano.octave_down_short"), this::octaveDown));
                x += widths[0] + itemGap;
                octaveDisplayX = x;
                octaveDisplayWidth = widths[1];
                x += widths[1] + itemGap;
                addRenderableWidget(new Action(x, barY, widths[2], barHeight,
                        Component.translatable("gui.minepiano.octave_up_short"), this::octaveUp));
                int hintX = controlsWidth >= desired ? collapseX - itemGap - widths[3]
                        : x + widths[2] + itemGap;
                addRenderableWidget(new Toggle(hintX, barY, widths[3], barHeight,
                        controlsWidth < desired ? Component.empty() : Component.translatable("gui.minepiano.show_hints"), () -> showHints,
                        this::toggleHints, false));
            }
        }
        addRenderableWidget(new Action(collapseX, barY, collapseWidth, barHeight,
                Component.literal(controlsCollapsed ? "⌃" : "⌄"), this::toggleControls));

        if (!midiStarted) {
            midiStarted = true;
            engine.setSustain(input.sustain());
            if (config.midiEnabled) midi.start(config.midiDevice);
        }
    }

    static PanelLayout layout(int screenWidth, int screenHeight, boolean collapsed) {
        int panelWidth = Math.min(clamp(Math.round(screenWidth * 0.50f), 240, 520),
                Math.max(1, Math.round(screenWidth * 0.55f)));
        int bottomMargin = Math.min(clamp(Math.round(screenHeight * 0.02f), 4, 10),
                Math.max(0, screenHeight - 1));
        int headerHeight = clamp(Math.round(screenHeight * 0.10f), 26, 40);
        int bottomHeight = clamp(Math.round(screenHeight * 0.085f), 20, 30);
        int keyboardHeight = clamp(Math.round(screenHeight * 0.17f), 44, 120);
        int availableHeight = Math.max(1, screenHeight - bottomMargin);
        if (headerHeight + keyboardHeight + bottomHeight > availableHeight) {
            keyboardHeight = Math.max(1, availableHeight - headerHeight - bottomHeight);
            if (keyboardHeight == 1) {
                headerHeight = Math.max(1, availableHeight - bottomHeight - keyboardHeight);
                bottomHeight = Math.max(1, availableHeight - headerHeight - keyboardHeight);
            }
        }
        int panelHeight = headerHeight + keyboardHeight + bottomHeight;
        int panelX = Math.max(0, (screenWidth - panelWidth) / 2);
        int panelY = Math.max(0, screenHeight - bottomMargin - panelHeight);
        int keyboardInset = Math.min(12, Math.max(0, (panelWidth - 1) / 2));
        Rect panel = new Rect(panelX, panelY, panelWidth, panelHeight);
        Rect header = new Rect(panelX, panelY, panelWidth, headerHeight);
        Rect keyboard = new Rect(panelX + keyboardInset, panelY + headerHeight,
                panelWidth - keyboardInset * 2, keyboardHeight);
        Rect bar = new Rect(panelX, keyboard.bottom(), panelWidth, bottomHeight);
        return new PanelLayout(panel, header, keyboard, bar);
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    static record PanelLayout(Rect panel, Rect header, Rect keyboard, Rect bottomBar) { }

    static record Rect(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }
    }

    @Override
    protected void extractBlurredBackground(GuiGraphicsExtractor graphics) { }

    @Override
    protected void extractMenuBackground(GuiGraphicsExtractor graphics) { }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        roundedBorder(graphics, panelX, panelY, panelWidth, panelHeight, BORDER, PANEL);
        if (panelWidth > 20)
            graphics.fill(panelX + 10, keyboardBottom + 1, panelX + panelWidth - 10,
                    keyboardBottom + 2, 0x554F4A44);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        if (titleRight > panelX + 32) drawNote(graphics, panelX + 15, panelY + 14);
        int titleWidth = Math.max(0, titleRight - panelX - 35);
        if (titleWidth > 4) {
            String titleText = font.plainSubstrByWidth(title.getString(), titleWidth);
            graphics.text(font, Component.literal(titleText).withStyle(ChatFormatting.BOLD), panelX + 35, panelY + 11, TEXT, false);
            if (headerHeight >= 32) {
                String subtitle = font.plainSubstrByWidth(Component.translatable("gui.minepiano.subtitle").getString(), titleWidth);
                graphics.text(font, subtitle, panelX + 35, panelY + 27, MUTED, false);
            }
        }
        if (!controlsCollapsed && octaveDisplayWidth > 0) {
            roundedBorder(graphics, octaveDisplayX, bottomControlY, octaveDisplayWidth, bottomControlHeight,
                    BORDER, 0xFF201F1D);
            Component octave = Component.literal("C" + input.octave());
            graphics.centeredText(font, octave, octaveDisplayX + octaveDisplayWidth / 2,
                    bottomControlY + (bottomControlHeight - 8) / 2, TEXT);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_SPACE) {
            input.press(event.key());
            config.sustain = input.sustain();
            engine.setSustain(config.sustain);
            return true;
        }
        OptionalInt offset = keyMap.offset(event.key());
        int note = offset.isEmpty() ? -1 : (input.octave() + 1) * 12 + offset.getAsInt();
        if (note >= 0 && note <= 127) {
            if (heldNotes.putIfAbsent(event.key(), note) == null) engine.noteOn(note, FIXED_VELOCITY);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        Integer note = heldNotes.remove(event.key());
        if (note != null) {
            engine.noteOff(note);
            return true;
        }
        OptionalInt released = input.release(event.key());
        return released.isPresent() || super.keyReleased(event);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        stopNotes();
        try { config.save(); } catch (IOException ignored) { }
        minecraft.gui.setScreen(null);
    }

    @Override
    public void removed() {
        try { midi.stop(); } finally {
            midiStarted = false;
            stopNotes();
            super.removed();
        }
    }

    @Override
    public void tick() {
        super.tick();
        int note = midi.pollNote();
        if (note >= 0) autoScroll(note);
        if (++midiTicks >= 40) {
            midiTicks = 0;
            if (config.midiEnabled && !midi.isConnected() && midi.canConnect()) midi.start(config.midiDevice);
        }
    }

    private void openSettings() {
        stopNotes();
        minecraft.gui.setScreen(new PianoSettingsScreen(this, config, engine));
    }

    private void stopNotes() {
        engine.allNotesOff();
        heldNotes.clear();
        input.heldKeys().forEach(input::release);
    }

    private void octaveDown() {
        input.octaveDown();
        syncControls();
    }

    private void octaveUp() {
        input.octaveUp();
        syncControls();
    }

    private void toggleSustain() {
        config.sustain = input.toggleSustain();
        engine.setSustain(config.sustain);
    }

    private void toggleHints() {
        showHints = !showHints;
        rebuildWidgets();
    }

    private void toggleControls() {
        controlsCollapsed = !controlsCollapsed;
        rebuildWidgets();
    }

    private void syncControls() {
        config.octave = input.octave();
        rebuildWidgets();
    }

    private void autoScroll(int note) {
        int base = (input.octave() + 1) * 12;
        if (note >= base && note <= base + 35) return;
        int target = note < base ? note / 12 - 1 : note / 12 - 3;
        target = Math.max(PianoConfig.MIN_OCTAVE, Math.min(PianoConfig.MAX_OCTAVE, target));
        if (target == input.octave()) return;
        input.setOctave(target);
        rebuildWidgets();
    }

    static void roundedBorder(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                              int border, int fill) {
        roundedRect(graphics, x, y, width, height, border);
        roundedRect(graphics, x + 1, y + 1, width - 2, height - 2, fill);
    }

    static void roundedRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;
        if (width < 4 || height < 4) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        graphics.fill(x + 2, y, x + width - 2, y + height, color);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, color);
        graphics.fill(x, y + 2, x + width, y + height - 2, color);
    }

    private static void drawNote(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x + 9, y, x + 11, y + 13, ACCENT);
        graphics.fill(x + 9, y, x + 16, y + 2, ACCENT);
        roundedRect(graphics, x + 2, y + 10, 9, 7, ACCENT);
    }

    static class Action extends AbstractWidget {
        private final Runnable action;

        Action(int x, int y, int width, int height, Component message, Runnable action) {
            super(x, y, width, height, message);
            this.action = action;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            roundedBorder(graphics, getX(), getY(), getWidth(), getHeight(),
                    isHoveredOrFocused() ? ACCENT : BORDER, isHoveredOrFocused() ? 0xFF37322C : CONTROL);
            var font = Minecraft.getInstance().font;
            String text = font.plainSubstrByWidth(getMessage().getString(), Math.max(0, getWidth() - 6));
            graphics.centeredText(font, text, getX() + getWidth() / 2,
                    getY() + (getHeight() - 8) / 2, TEXT);
        }

        @Override public void onClick(MouseButtonEvent event, boolean doubleClick) { action.run(); }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }

    private static final class Toggle extends Action {
        private final Component label;
        private final BooleanSupplier value;
        private final boolean showState;

        Toggle(int x, int y, int width, int height, Component label, BooleanSupplier value, Runnable action,
               boolean showState) {
            super(x, y, width, height, label, action);
            this.label = label;
            this.value = value;
            this.showState = showState;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            boolean on = value.getAsBoolean();
            roundedBorder(graphics, getX(), getY(), getWidth(), getHeight(),
                    isHoveredOrFocused() ? ACCENT : BORDER, CONTROL);
            int switchWidth = Math.min(28, Math.max(1, getWidth() / 3));
            int switchInset = Math.min(5, Math.max(0, getWidth() - switchWidth));
            int switchX = getRight() - switchWidth - switchInset;
            int switchHeight = Math.min(14, Math.max(1, getHeight() - 4));
            int switchY = getY() + (getHeight() - switchHeight) / 2;
            roundedRect(graphics, switchX, switchY, switchWidth, switchHeight, on ? ACCENT : 0xFF55514C);
            int knobWidth = Math.min(switchWidth, Math.max(1, switchWidth - 4));
            int knobHeight = Math.min(switchHeight, Math.max(1, switchHeight - 4));
            int knobInset = Math.min(2, Math.max(0, switchWidth - knobWidth));
            roundedRect(graphics, switchX + (on ? switchWidth - knobWidth - knobInset : knobInset),
                    switchY + (switchHeight - knobHeight) / 2, knobWidth, knobHeight, 0xFFF8F4EF);
            Component state = Component.translatable(on ? "gui.minepiano.on" : "gui.minepiano.off");
            String text = showState ? label.getString() + ": " + state.getString() : label.getString();
            int room = switchX - getX() - 8;
            if (Minecraft.getInstance().font.width(text) > room)
                text = Minecraft.getInstance().font.plainSubstrByWidth(text, Math.max(0, room - 6)) + "…";
            graphics.text(Minecraft.getInstance().font, text, getX() + 5,
                    getY() + (getHeight() - 8) / 2, on ? ACCENT : TEXT, false);
            setMessage(label.copy().append(": ").append(state));
        }
    }
}
