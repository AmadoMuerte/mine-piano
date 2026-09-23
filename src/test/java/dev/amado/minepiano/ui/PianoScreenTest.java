package dev.amado.minepiano.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PianoScreenTest {
    @Test
    void panelStaysBottomDockedAndSectionsNeverOverlap() {
        int[][] sizes = {{640, 339}, {960, 508}, {1920, 1017}, {854, 480}, {320, 240}, {240, 135}, {160, 120}};
        for (int[] size : sizes) for (boolean collapsed : new boolean[] {false, true}) {
            PianoScreen.PanelLayout layout = PianoScreen.layout(size[0], size[1], collapsed);
            PianoScreen.Rect panel = layout.panel();
            assertTrue(panel.x() >= 0 && panel.y() >= 0, size[0] + "x" + size[1]);
            assertTrue(panel.right() <= size[0] && panel.bottom() <= size[1]);
            assertEquals(bottomMargin(size[1]), size[1] - panel.bottom());
            assertInside(layout.header(), panel);
            assertInside(layout.keyboard(), panel);
            assertInside(layout.bottomBar(), panel);
            assertTrue(layout.header().bottom() <= layout.keyboard().y());
            assertTrue(layout.keyboard().bottom() <= layout.bottomBar().y());
            assertEquals(panel.height(), layout.header().height() + layout.keyboard().height()
                    + layout.bottomBar().height());
            if (size[0] >= 320 && size[1] >= 240) {
                assertTrue(panel.width() <= Math.round(size[0] * 0.55f), size[0] + "x" + size[1]);
                assertTrue(panel.height() <= Math.round(size[1] * 0.40f), size[0] + "x" + size[1]);
            }
        }
    }

    @Test
    void compactDimensionsScaleWithScreen() {
        PianoScreen.PanelLayout compact = PianoScreen.layout(640, 339, false);
        assertEquals(320, compact.panel().width());
        assertEquals(121, compact.panel().height());
        assertEquals(58, compact.keyboard().height());
        assertTrue(compact.panel().height() <= 130);

        PianoScreen.PanelLayout desktop = PianoScreen.layout(1920, 1017, false);
        assertEquals(520, desktop.panel().width());
        assertEquals(190, desktop.panel().height());
        assertEquals(120, desktop.keyboard().height());
        System.out.printf("640x339: panel=%dx%d keyboard=%d header=%d bar=%d%n",
                compact.panel().width(), compact.panel().height(), compact.keyboard().height(),
                compact.header().height(), compact.bottomBar().height());
        System.out.printf("1920x1017: panel=%dx%d keyboard=%d header=%d bar=%d%n",
                desktop.panel().width(), desktop.panel().height(), desktop.keyboard().height(),
                desktop.header().height(), desktop.bottomBar().height());
    }

    private static void assertInside(PianoScreen.Rect child, PianoScreen.Rect parent) {
        assertTrue(child.x() >= parent.x() && child.y() >= parent.y());
        assertTrue(child.right() <= parent.right() && child.bottom() <= parent.bottom());
    }

    private static int bottomMargin(int height) {
        return Math.max(4, Math.min(10, Math.round(height * 0.02f)));
    }
}
