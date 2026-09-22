package dev.amado.minepiano.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PianoSettingsScreenTest {
    @Test
    void flowRowsNeverOverlap() {
        for (int height : new int[] {70, 120, 230, 390}) {
            PianoSettingsScreen.Layout layout = PianoSettingsScreen.layout(5, height);
            int[] starts = {layout.velocityY(), layout.volumeY(), layout.presetY(), layout.voiceChatY()};
            for (int i = 1; i < starts.length; i++) assertTrue(starts[i] >= starts[i - 1] + 28);
            assertTrue(layout.soundfontLabelY() >= layout.voiceChatY() + 28);
            assertTrue(layout.soundfontFieldY() >= layout.soundfontLabelY() + 12);
            assertTrue(layout.keymapLabelY() >= layout.soundfontFieldY() + 22);
            assertTrue(layout.bindingsY() >= layout.keymapLabelY() + 9);
        }
    }
}
