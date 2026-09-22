package dev.amado.minepiano.ui;

import dev.amado.minepiano.audio.PianoEngine;
import dev.amado.minepiano.config.KeyMap;
import dev.amado.minepiano.config.PianoConfig;
import dev.amado.minepiano.input.PianoInput;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
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

/** Centered piano window, mouse controls, and keyboard input. */
public final class PianoScreen extends Screen {
    static final int ACCENT = 0xFFF0A64A;
    static final int TEXT = 0xFFF4F1ED;
    static final int MUTED = 0xFFA9A39C;
    static final int PANEL = 0xEE171614;
    static final int CONTROL = 0xFF292724;
    static final int BORDER = 0xFF45413C;

    private final PianoEngine engine;
    private final PianoConfig config;
    private final PianoInput input;
    private final Map<Integer, Integer> heldNotes = new HashMap<>();
    private boolean showHints;
    private boolean controlsCollapsed;
    private boolean hintsKeyHeld;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int keyboardBottom;

    public PianoScreen(PianoEngine engine, PianoConfig config) {
        super(Component.translatable("gui.minepiano.title"));
        this.engine = engine;
        this.config = config;
        input = new PianoInput(new KeyMap(config), config.octave);
    }

    @Override
    protected void init() {
        panelWidth = Math.min(760, Math.max(280, width - 20));
        int keyboardHeight = Math.max(82, Math.min(170, height - (controlsCollapsed ? 104 : 122)));
        panelHeight = 54 + keyboardHeight + (controlsCollapsed ? 24 : 42);
        panelX = (width - panelWidth) / 2;
        panelY = Math.max(6, (height - panelHeight) / 2);
        keyboardBottom = panelY + 54 + keyboardHeight;

        int right = panelX + panelWidth - 12;
        addRenderableWidget(new Action(right - 22, panelY + 15, 22, 22, Component.literal("×"), this::onClose));
        addRenderableWidget(new Action(right - 52, panelY + 15, 24, 22, Component.literal("⚙"), this::openSettings));
        int sustainWidth = Math.min(134, Math.max(104, panelWidth - 175));
        addRenderableWidget(new Toggle(right - 58 - sustainWidth, panelY + 15, sustainWidth, 22,
                Component.translatable("gui.minepiano.sustain"), input::sustain, this::toggleSustain, true));

        addRenderableWidget(new PianoKeyboard(panelX + 12, panelY + 54, panelWidth - 24, keyboardHeight,
                engine, input.octave(), config.velocity, config.keymap, showHints));

        int barY = keyboardBottom + 7;
        if (!controlsCollapsed) {
            int available = panelWidth - 44;
            int displayWidth = 42;
            int buttonWidth = Math.max(64, Math.min(112, (available - displayWidth) / 3));
            int x = panelX + 10;
            addRenderableWidget(new Action(x, barY, buttonWidth, 26,
                    Component.translatable("gui.minepiano.octave_down_short"), this::octaveDown));
            x += buttonWidth + 4;
            x += displayWidth + 4;
            addRenderableWidget(new Action(x, barY, buttonWidth, 26,
                    Component.translatable("gui.minepiano.octave_up_short"), this::octaveUp));
            x += buttonWidth + 4;
            int hintWidth = panelX + panelWidth - 36 - x;
            if (hintWidth > 30) addRenderableWidget(new Toggle(x, barY, hintWidth, 26,
                    Component.translatable("gui.minepiano.show_hints"), () -> showHints, this::toggleHints, false));
        }
        addRenderableWidget(new Action(panelX + panelWidth - 30, barY, 20, 26,
                Component.literal(controlsCollapsed ? "⌃" : "⌄"), this::toggleControls));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x66000000);
        roundedRect(graphics, panelX - 3, panelY + 4, panelWidth + 6, panelHeight, 0x44000000);
        roundedBorder(graphics, panelX, panelY, panelWidth, panelHeight, BORDER, PANEL);
        graphics.fill(panelX + 10, keyboardBottom + 1, panelX + panelWidth - 10, keyboardBottom + 2, 0x554F4A44);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        drawNote(graphics, panelX + 15, panelY + 14);
        graphics.text(font, title.copy().withStyle(ChatFormatting.BOLD), panelX + 35, panelY + 11, TEXT, false);
        graphics.text(font, Component.translatable("gui.minepiano.subtitle"), panelX + 35, panelY + 27, MUTED, false);
        if (!controlsCollapsed) {
            int available = panelWidth - 44;
            int displayWidth = 42;
            int buttonWidth = Math.max(64, Math.min(112, (available - displayWidth) / 3));
            int displayX = panelX + 14 + buttonWidth;
            roundedBorder(graphics, displayX, keyboardBottom + 7, displayWidth, 26, BORDER, 0xFF201F1D);
            Component octave = Component.literal("C" + input.octave());
            graphics.centeredText(font, octave, displayX + displayWidth / 2, keyboardBottom + 16, TEXT);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_Q) {
            if (!hintsKeyHeld) toggleHints();
            hintsKeyHeld = true;
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_Z || event.key() == GLFW.GLFW_KEY_X
                || event.key() == GLFW.GLFW_KEY_SPACE) {
            input.press(event.key());
            if (event.key() == GLFW.GLFW_KEY_SPACE) engine.setSustain(input.sustain());
            else syncControls();
            return true;
        }
        String name = GLFW.glfwGetKeyName(event.key(), event.scancode());
        Integer offset = name == null ? null : config.keymap.get(name.toUpperCase(Locale.ROOT));
        int note = offset == null ? -1 : (input.octave() + 1) * 12 + offset;
        if (note >= 0 && note <= 127) {
            if (heldNotes.putIfAbsent(event.key(), note) == null) engine.noteOn(note, config.velocity);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_Q) {
            hintsKeyHeld = false;
            return true;
        }
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
        silence();
        try { config.save(); } catch (IOException ignored) { }
        minecraft.gui.setScreen(null);
    }

    @Override
    public void removed() {
        silence();
        super.removed();
    }

    private void openSettings() {
        silence();
        minecraft.gui.setScreen(new PianoSettingsScreen(this, config, engine));
    }

    private void silence() {
        engine.allNotesOff();
        engine.setSustain(false);
        heldNotes.clear();
        input.heldKeys().forEach(input::release);
        if (input.sustain()) input.toggleSustain();
        hintsKeyHeld = false;
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
        engine.setSustain(input.toggleSustain());
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
        engine.setSustain(input.sustain());
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
            graphics.centeredText(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2,
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
            int switchWidth = 28;
            int switchX = getRight() - switchWidth - 5;
            int switchY = getY() + (getHeight() - 14) / 2;
            roundedRect(graphics, switchX, switchY, switchWidth, 14, on ? ACCENT : 0xFF55514C);
            roundedRect(graphics, switchX + (on ? 16 : 2), switchY + 2, 10, 10, 0xFFF8F4EF);
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
