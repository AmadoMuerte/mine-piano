package dev.amado.minepiano.ui;

import dev.amado.minepiano.audio.PianoEngine;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Two-octave mouse-playable keyboard. */
public final class PianoKeyboard extends AbstractWidget {
    private static final int[] BLACKS = {1, 3, 6, 8, 10};
    private static final String[] NAMES = {"C", "D", "E", "F", "G", "A", "B"};
    private final PianoEngine engine;
    private final int baseNote;
    private final int fallbackVelocity;
    private final Map<String, Integer> keymap;
    private final boolean showHints;
    private int mouseNote = -1;

    public PianoKeyboard(int x, int y, int width, int height, PianoEngine engine, int octave, int fallbackVelocity,
                         Map<String, Integer> keymap, boolean showHints) {
        super(x, y, width, height, Component.translatable("gui.minepiano.keyboard"));
        this.engine = engine;
        baseNote = (octave + 1) * 12;
        this.fallbackVelocity = fallbackVelocity;
        this.keymap = keymap;
        this.showHints = showHints;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        PianoScreen.roundedRect(graphics, getX() - 3, getY() - 3, getWidth() + 6, getHeight() + 6, 0xFF0D0C0B);
        for (int white = 0; white < 14; white++) {
            int note = baseNote + whiteToSemitone(white);
            int left = whiteLeft(white);
            int right = whiteLeft(white + 1);
            int color = engine.isNoteActive(note) ? 0xFFFFC46E : 0xFFF3F0EA;
            graphics.fill(left, getY(), right - 1, getBottom(), color);
            graphics.fill(right - 1, getY(), right, getBottom(), 0xFF5B5650);
            String name = NAMES[white % 7];
            graphics.centeredText(Minecraft.getInstance().font, name, (left + right) / 2, getBottom() - 14, 0xFF413E39);
            drawHint(graphics, note - baseNote, (left + right) / 2, getBottom() - 28, 0xFF7A7167);
        }
        int blackHeight = getHeight() * 3 / 5;
        for (int octave = 0; octave < 2; octave++) for (int black : BLACKS) {
            int white = octave * 7 + whiteBefore(black);
            int boundary = whiteLeft(white + 1);
            int blackWidth = Math.max(5, getWidth() / 22);
            int left = boundary - blackWidth / 2;
            int note = baseNote + octave * 12 + black;
            graphics.fill(left - 1, getY(), left + blackWidth + 1, getY() + blackHeight + 2, 0xFF090909);
            graphics.fill(left, getY(), left + blackWidth, getY() + blackHeight,
                    engine.isNoteActive(note) ? PianoScreen.ACCENT : 0xFF22211F);
            drawHint(graphics, note - baseNote, left + blackWidth / 2, getY() + blackHeight - 15, 0xFFD4CDC5);
        }
        int hovered = noteAt(mouseX, mouseY);
        if (hovered >= 0) outlineNote(graphics, hovered);
    }

    private void drawHint(GuiGraphicsExtractor graphics, int offset, int centerX, int y, int color) {
        if (!showHints) return;
        keymap.entrySet().stream().filter(entry -> entry.getValue() == offset).findFirst()
                .ifPresent(entry -> graphics.centeredText(Minecraft.getInstance().font, entry.getKey(), centerX, y, color));
    }

    private void outlineNote(GuiGraphicsExtractor graphics, int note) {
        int offset = note - baseNote;
        boolean black = false;
        for (int value : BLACKS) if (Math.floorMod(offset, 12) == value) black = true;
        if (black) {
            int octave = offset / 12;
            int semitone = Math.floorMod(offset, 12);
            int boundary = whiteLeft(octave * 7 + whiteBefore(semitone) + 1);
            int width = Math.max(5, getWidth() / 22);
            graphics.outline(boundary - width / 2, getY(), width, getHeight() * 3 / 5, PianoScreen.ACCENT);
        } else {
            int white = semitoneToWhite(offset);
            if (white >= 0 && white < 14)
                graphics.outline(whiteLeft(white), getY(), whiteLeft(white + 1) - whiteLeft(white), getHeight(), PianoScreen.ACCENT);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) { play(event.x(), event.y()); }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        int note = noteAt(event.x(), event.y());
        if (note != mouseNote) {
            releaseMouse();
            if (note >= 0) play(event.x(), event.y());
        }
    }

    @Override
    public void onRelease(MouseButtonEvent event) { releaseMouse(); }

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
        if (y < getY() + getHeight() * 3 / 5) for (int octave = 0; octave < 2; octave++) for (int black : BLACKS) {
            int boundary = whiteLeft(octave * 7 + whiteBefore(black) + 1);
            int width = Math.max(5, getWidth() / 22);
            if (x >= boundary - width / 2 && x < boundary - width / 2 + width) return baseNote + octave * 12 + black;
        }
        int white = Math.min(13, Math.max(0, (int) ((x - getX()) * 14 / getWidth())));
        return baseNote + whiteToSemitone(white);
    }

    private int whiteLeft(int white) { return getX() + white * getWidth() / 14; }

    private static int whiteToSemitone(int white) {
        return (white / 7) * 12 + new int[] {0, 2, 4, 5, 7, 9, 11}[white % 7];
    }

    private static int semitoneToWhite(int semitone) {
        int octave = semitone / 12;
        return octave * 7 + switch (Math.floorMod(semitone, 12)) {
            case 0 -> 0; case 2 -> 1; case 4 -> 2; case 5 -> 3; case 7 -> 4; case 9 -> 5; case 11 -> 6;
            default -> -100;
        };
    }

    private static int whiteBefore(int black) {
        return switch (black) { case 1 -> 0; case 3 -> 1; case 6 -> 3; case 8 -> 4; default -> 5; };
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
