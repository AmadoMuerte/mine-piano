package dev.amado.minepiano.ui;

import dev.amado.minepiano.audio.PianoEngine;
import dev.amado.minepiano.config.KeyMap;
import dev.amado.minepiano.config.PianoConfig;
import dev.amado.minepiano.input.PianoInput;
import java.util.OptionalInt;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Compact piano controls and keyboard input. */
public final class PianoScreen extends Screen {
    private final PianoEngine engine;
    private final PianoConfig config;
    private final PianoInput input;

    public PianoScreen(PianoEngine engine, PianoConfig config) {
        super(Component.translatable("gui.minepiano.title"));
        this.engine = engine;
        this.config = config;
        input = new PianoInput(new KeyMap(config), config.octave);
    }

    @Override
    protected void init() {
        int bottom = height - 30;
        addRenderableWidget(new PianoKeyboard(12, 32, Math.max(70, width - 24), Math.max(40, bottom - 42), engine,
                input.octave(), config.velocity));
        addRenderableWidget(new Action(width - 76, 8, 68, 20, Component.translatable("gui.minepiano.settings"),
                () -> minecraft.gui.setScreen(new PianoSettingsScreen(this, config, engine))));
        addRenderableWidget(new Action(12, bottom, 76, 20, Component.translatable("gui.minepiano.octave_down"), this::octaveDown));
        addRenderableWidget(new Action(94, bottom, 52, 20, Component.literal("C" + input.octave()), () -> { }));
        addRenderableWidget(new Action(152, bottom, 76, 20, Component.translatable("gui.minepiano.octave_up"), this::octaveUp));
        addRenderableWidget(new Action(width - 92, bottom, 80, 20, Component.translatable("gui.minepiano.sustain"), this::toggleSustain));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font, title, 12, 12, 0xFFFFFFFF, true);
        graphics.text(font, Component.literal(input.sustain() ? "ON" : "OFF"), width - 36, height - 25, 0xFFFFFFFF, false);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        OptionalInt note = input.press(event.key());
        note.ifPresent(value -> engine.noteOn(value, config.velocity));
        if (event.key() == GLFW.GLFW_KEY_Z || event.key() == GLFW.GLFW_KEY_X) syncControls();
        else if (event.key() == GLFW.GLFW_KEY_SPACE) engine.setSustain(input.sustain());
        return note.isPresent() || input.heldKeys().contains(event.key()) || super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        OptionalInt note = input.release(event.key());
        note.ifPresent(engine::noteOff);
        return note.isPresent() || super.keyReleased(event);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        engine.allNotesOff();
        engine.setSustain(false);
        try { config.save(); } catch (java.io.IOException ignored) { }
        minecraft.gui.setScreen(null);
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

    private void syncControls() {
        config.octave = input.octave();
        engine.setSustain(input.sustain());
        rebuildWidgets();
    }

    static final class Action extends AbstractWidget {
        private final Runnable action;

        Action(int x, int y, int width, int height, Component message, Runnable action) {
            super(x, y, width, height, message);
            this.action = action;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            int color = isHoveredOrFocused() ? 0xFF6A6A6A : 0xFF444444;
            graphics.fill(getX(), getY(), getRight(), getBottom(), color);
            graphics.text(net.minecraft.client.Minecraft.getInstance().font, getMessage(), getX() + 4,
                    getY() + (getHeight() - 9) / 2, 0xFFFFFFFF, false);
        }

        @Override public void onClick(MouseButtonEvent event, boolean doubleClick) { action.run(); }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }
}
