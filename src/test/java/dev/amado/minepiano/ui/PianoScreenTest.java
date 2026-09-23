package dev.amado.minepiano.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PianoScreenTest {
    @Test
    void panelStaysBottomDockedAndSectionsNeverOverlap() {
        int[][] sizes = {{1920, 1080}, {1280, 720}, {854, 480}, {320, 240}, {240, 135}, {160, 120}};
        for (int[] size : sizes) for (boolean collapsed : new boolean[] {false, true}) {
            PianoScreen.PanelLayout layout = PianoScreen.layout(size[0], size[1], collapsed);
            PianoScreen.Rect panel = layout.panel();
            assertTrue(panel.x() >= 0 && panel.y() >= 0, size[0] + "x" + size[1]);
            assertTrue(panel.right() <= size[0] && panel.bottom() <= size[1]);
            assertTrue(size[1] - panel.bottom() <= 10);
            assertInside(layout.header(), panel);
            assertInside(layout.keyboard(), panel);
            assertInside(layout.bottomBar(), panel);
            assertTrue(layout.header().bottom() <= layout.keyboard().y());
            assertTrue(layout.keyboard().bottom() <= layout.bottomBar().y());
            assertEquals(panel.height(), layout.header().height() + layout.keyboard().height()
                    + layout.bottomBar().height());
        }
    }

    @Test
    void desktopLayoutUsesRequestedCompactDimensions() {
        PianoScreen.PanelLayout layout = PianoScreen.layout(1920, 1080, false);
        assertEquals(1000, layout.panel().width());
        assertEquals(256, layout.panel().height());
        assertEquals(160, layout.keyboard().height());
        assertEquals(10, 1080 - layout.panel().bottom());
    }

    private static void assertInside(PianoScreen.Rect child, PianoScreen.Rect parent) {
        assertTrue(child.x() >= parent.x() && child.y() >= parent.y());
        assertTrue(child.right() <= parent.right() && child.bottom() <= parent.bottom());
    }
}
