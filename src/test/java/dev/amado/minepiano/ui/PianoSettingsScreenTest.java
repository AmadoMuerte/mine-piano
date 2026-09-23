package dev.amado.minepiano.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amado.minepiano.audio.PianoEngine;
import dev.amado.minepiano.audio.PianoPreset;
import dev.amado.minepiano.config.PianoConfig;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PianoSettingsScreenTest {
    @Test
    void flowRowsNeverOverlap() {
        for (int height : new int[] {70, 120, 230, 390}) {
            PianoSettingsScreen.Layout layout = PianoSettingsScreen.layout(5, height);
            int[] starts = {layout.volumeY(), layout.presetY(), layout.voiceChatY()};
            for (int i = 1; i < starts.length; i++) assertTrue(starts[i] >= starts[i - 1] + 28);
            assertTrue(layout.keymapLabelY() >= layout.voiceChatY() + 28);
            assertTrue(layout.bindingsY() >= layout.keymapLabelY() + 9);
        }
    }

    @Test
    void midiRowsPreserveKeymapSpace() {
        for (int height : new int[] {70, 120, 230, 249, 390}) {
            PianoSettingsScreen.Layout baseline = PianoSettingsScreen.layout(5, height, false);
            PianoSettingsScreen.Layout layout = PianoSettingsScreen.layout(5, height, true);
            assertEquals(-1, baseline.midiToggleY());
            assertEquals(-1, baseline.midiDeviceY());
            assertEquals(layout.midiToggleY() >= 0, layout.midiDeviceY() >= 0);
            if (layout.midiToggleY() >= 0) {
                assertTrue(layout.bindingsY() < layout.doneY());
                assertTrue(layout.keymapLabelY() >= layout.midiDeviceY() + 28);
                assertTrue(layout.midiDeviceY() >= layout.midiToggleY() + 28);
            } else {
                assertEquals(-1, layout.midiDeviceY());
            }
        }
        assertEquals(-1, PianoSettingsScreen.layout(5, 230, true).midiToggleY());
    }

    @Test
    void settingsAndConfigHaveNoVelocityOrSoundfontRows() {
        assertFalse(Arrays.stream(PianoSettingsScreen.Layout.class.getRecordComponents())
                .anyMatch(component -> component.getName().contains("velocity") || component.getName().contains("soundfont")));
        assertFalse(Arrays.stream(PianoConfig.class.getFields())
                .anyMatch(field -> field.getName().equals("velocity") || field.getName().equals("soundfont")));
    }

    @Test
    void presetSelectionReachesEngineImmediately() {
        PianoConfig config = new PianoConfig();
        AtomicReference<PianoPreset> selected = new AtomicReference<>();
        PianoEngine engine = (PianoEngine) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {PianoEngine.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("setPreset")) selected.set((PianoPreset) arguments[0]);
                    return method.getReturnType() == boolean.class ? false : null;
                });

        PianoSettingsScreen.changePreset(config, engine, 1);

        assertEquals(PianoPreset.GRAND.name(), config.presetName);
        assertEquals(PianoPreset.GRAND, selected.get());
    }
}
