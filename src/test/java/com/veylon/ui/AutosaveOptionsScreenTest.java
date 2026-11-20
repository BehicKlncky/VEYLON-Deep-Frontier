package com.veylon.ui;

import com.veylon.gfx.ResourceManager;
import com.veylon.save.AutosaveSettings;
import org.junit.jupiter.api.Test;
import org.lwjgl.stb.STBTTFontinfo;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.stb.STBTruetype.*;

/** The autosave options (0.9.1 milestone 05): what each row changes, BACK, and that every line fits. */
class AutosaveOptionsScreenTest {

    @Test
    void theSwitchFlipsAndTheIntervalStepsThroughTheOfferedOnes() {
        AutosaveSettings settings = new AutosaveSettings();
        assertEquals("On", AutosaveOptionsScreen.valueOf(settings, 0));
        assertEquals("10 minutes", AutosaveOptionsScreen.valueOf(settings, 1));
        AutosaveOptionsScreen.adjust(settings, 0, 1);
        assertEquals("Off", AutosaveOptionsScreen.valueOf(settings, 0));
        AutosaveOptionsScreen.adjust(settings, 0, -1);
        assertTrue(settings.enabled, "left and right both flip the switch");
        AutosaveOptionsScreen.adjust(settings, 1, 1);
        assertEquals("15 minutes", AutosaveOptionsScreen.valueOf(settings, 1));
        AutosaveOptionsScreen.adjust(settings, 1, -1);
        AutosaveOptionsScreen.adjust(settings, 1, -1);
        assertEquals("5 minutes", AutosaveOptionsScreen.valueOf(settings, 1));
    }

    @Test
    void backRestoresWhatTheScreenOpenedWith() {
        AutosaveSettings live = new AutosaveSettings();
        AutosaveOptionsScreen screen = new AutosaveOptionsScreen();
        screen.open(live);
        AutosaveOptionsScreen.adjust(live, 0, 1);
        AutosaveOptionsScreen.adjust(live, 1, 1);
        screen.cancel(live);
        assertTrue(live.enabled);
        assertEquals(10, live.intervalMinutes());
    }

    /** The panel is 680 wide at most; its rows are 84 narrower, the buttons 190. */
    @Test
    void everyLineFitsItsPanelTheTitleAndThePauseMenu() {
        ToDoubleFunction<String> hint = regular(1.05f), subtitle = regular(1.08f), row = regular(1.28f);
        assertTrue(subtitle.applyAsDouble(AutosaveOptionsScreen.SUBTITLE) <= 680 - 40, AutosaveOptionsScreen.SUBTITLE);
        assertTrue(hint.applyAsDouble(AutosaveOptionsScreen.WAITS) <= 680 - 40, AutosaveOptionsScreen.WAITS);
        assertTrue(hint.applyAsDouble(AutosaveOptionsScreen.KEYS) <= 680 - 40, AutosaveOptionsScreen.KEYS);
        AutosaveSettings widest = new AutosaveSettings();
        widest.setIntervalMinutes(30);
        for (String label : AutosaveOptionsScreen.LABELS) {
            double value = row.applyAsDouble(AutosaveOptionsScreen.valueOf(widest, 1));
            assertTrue(row.applyAsDouble(label) + value + 40 <= 680 - 84, "a row holds its label and value: " + label);
        }
        for (String button : List.of("APPLY [F5]", "BACK [Esc]")) {
            assertTrue(row.applyAsDouble(button) <= 190 - 12, button);
        }
        assertTrue(regular(1.10f).applyAsDouble(PauseMenu.saveKeys(3)) <= 600 - 40, PauseMenu.saveKeys(3));
        assertTrue(regular(1.5f).applyAsDouble("AUTOSAVE") <= 360 - 24, "the title's button");
    }

    /**
     * The game's regular face. stb keeps a pointer into the buffer it was given, so the buffer is
     * held for as long as the class is loaded.
     */
    private static final ByteBuffer FONT = ResourceManager.readBuffer("fonts/SourceSans3-Regular.ttf");
    private static final STBTTFontinfo FACE = STBTTFontinfo.malloc();

    static {
        if (!stbtt_InitFont(FACE, FONT)) {
            throw new IllegalStateException("the bundled regular face does not load");
        }
    }

    private static ToDoubleFunction<String> regular(float scale) {
        float px = stbtt_ScaleForPixelHeight(FACE, 12 * scale);
        return text -> {
            int[] advance = new int[1], bearing = new int[1];
            double width = 0;
            int previous = -1;
            for (int cp : text.codePoints().toArray()) {
                stbtt_GetCodepointHMetrics(FACE, cp, advance, bearing);
                width += advance[0] * px;
                if (previous >= 0) width += stbtt_GetCodepointKernAdvance(FACE, previous, cp) * px;
                previous = cp;
            }
            return width;
        };
    }
}
