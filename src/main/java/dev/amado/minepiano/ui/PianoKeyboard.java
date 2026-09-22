package dev.amado.minepiano.ui;

import dev.amado.minepiano.audio.PianoEngine;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Two-octave mouse-playable keyboard. */
public final class PianoKeyboard extends AbstractWidget {
    private static final int[] BLACKS = {1, 3, 6, 8, 10};
    private final PianoEngine engine;
    private final int baseNote;
    private final int fallbackVelocity;
    private int mouseNote = -1;

    public PianoKeyboard(int x, int y, int width, int height, PianoEngine engine, int octave, int fallbackVelocity) {
        super(x, y, width, height, Component.literal("Piano keyboard"));
        this.engine = engine;
        baseNote = (octave + 1) * 12;
        this.fallbackVelocity = fallbackVelocity;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int whiteWidth = Math.max(1, getWidth() / 14);
        for (int white = 0; white < 14; white++) {
            int note = baseNote + whiteToSemitone(white);
            int x = getX() + white * whiteWidth;
            graphics.fill(x, getY(), x + whiteWidth - 1, getBottom(), engine.isNoteActive(note) ? 0xFFFFD66B : 0xFFF4F4F4);
            graphics.fill(x + whiteWidth - 1, getY(), x + whiteWidth, getBottom(), 0xFF333333);
        }
        int blackHeight = getHeight() * 3 / 5;
        for (int octave = 0; octave < 2; octave++) for (int black : BLACKS) {
            int white = octave * 7 + whiteBefore(black);
            int x = getX() + (white + 1) * whiteWidth - whiteWidth / 3;
            int note = baseNote + octave * 12 + black;
            graphics.fill(x, getY(), x + Math.max(3, whiteWidth * 2 / 3), getY() + blackHeight,
                    engine.isNoteActive(note) ? 0xFFB8711A : 0xFF171717);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        play(event.x(), event.y());
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        int note = noteAt(event.x(), event.y());
        if (note != mouseNote) {
            releaseMouse();
            if (note >= 0) play(event.x(), event.y());
        }
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        releaseMouse();
    }

    private void play(double x, double y) {
        int note = noteAt(x, y);
        if (note < 0 || note == mouseNote) return;
        mouseNote = note;
        int velocity = getHeight() <= 1 ? fallbackVelocity : Math.max(1, Math.min(127,
                (int) (127 - (y - getY()) * 80 / getHeight())));
        engine.noteOn(note, velocity);
    }

    private void releaseMouse() {
        if (mouseNote >= 0) engine.noteOff(mouseNote);
        mouseNote = -1;
    }

    private int noteAt(double x, double y) {
        if (!isMouseOver(x, y)) return -1;
        int whiteWidth = Math.max(1, getWidth() / 14);
        if (y < getY() + getHeight() * 3 / 5) for (int octave = 0; octave < 2; octave++) for (int black : BLACKS) {
            int white = octave * 7 + whiteBefore(black);
            int blackX = getX() + (white + 1) * whiteWidth - whiteWidth / 3;
            if (x >= blackX && x < blackX + Math.max(3, whiteWidth * 2 / 3)) return baseNote + octave * 12 + black;
        }
        int white = Math.min(13, (int) ((x - getX()) / whiteWidth));
        return baseNote + whiteToSemitone(white);
    }

    private static int whiteToSemitone(int white) {
        return (white / 7) * 12 + new int[] {0, 2, 4, 5, 7, 9, 11}[white % 7];
    }

    private static int whiteBefore(int black) {
        return switch (black) { case 1 -> 0; case 3 -> 1; case 6 -> 3; case 8 -> 4; default -> 5; };
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
