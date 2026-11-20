package com.veylon.ui;

import com.veylon.gfx.ResourceManager;
import com.veylon.input.ControlSettings;
import org.junit.jupiter.api.Test;
import org.lwjgl.stb.STBTTFontinfo;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.stb.STBTruetype.*;

/**
 * The CONTROLS screen (0.9.1 milestone 06): what a step on each row does, what BACK restores, and
 * that every line fits its panel, the title's button and the pause menu.
 */
class ControlsOptionsScreenTest {

    @Test
    void aStepMovesTheSensitivityOrFlipsInvertYAndBackRestoresBoth() {
        ControlSettings live = new ControlSettings();
        ControlsOptionsScreen screen = new ControlsOptionsScreen();
        screen.open(live);
        ControlsOptionsScreen.adjust(live, 0, 1);
        ControlsOptionsScreen.adjust(live, 0, 1);
        ControlsOptionsScreen.adjust(live, 1, 1);
        assertEquals(120, live.sensitivity(), "the live settings change at once");
        assertTrue(live.invertY);
        assertEquals("120%", ControlsOptionsScreen.valueOf(live, 0));
        assertEquals("On", ControlsOptionsScreen.valueOf(live, 1));
        ControlsOptionsScreen.adjust(live, 0, -1);
        ControlsOptionsScreen.adjust(live, 1, -1);
        assertEquals(110, live.sensitivity());
        assertFalse(live.invertY, "either direction flips the switch");
        screen.cancel(live);
        assertEquals(100, live.sensitivity(), "BACK restores what the screen opened on");
        assertFalse(live.invertY);
    }

    /** The panel is 680 wide at most; its rows are 84 narrower, the buttons 190. */
    @Test
    void everyLineFitsItsPanelTheTitleAndThePauseMenu() {
        ToDoubleFunction<String> hint = regular(1.05f), subtitle = regular(1.08f), row = regular(1.28f);
        assertTrue(subtitle.applyAsDouble(ControlsOptionsScreen.SUBTITLE) <= 680 - 40, ControlsOptionsScreen.SUBTITLE);
        assertTrue(hint.applyAsDouble(ControlsOptionsScreen.INVERT_NOTE) <= 680 - 40, ControlsOptionsScreen.INVERT_NOTE);
        assertTrue(hint.applyAsDouble(ControlsOptionsScreen.KEYS) <= 680 - 40, ControlsOptionsScreen.KEYS);
        ControlSettings widest = new ControlSettings();
        widest.setSensitivity(ControlSettings.MAX_SENSITIVITY);
        for (int i = 0; i < ControlsOptionsScreen.LABELS.length; i++) {
            double value = Math.max(row.applyAsDouble(ControlsOptionsScreen.valueOf(widest, 0)),
                    row.applyAsDouble("Off"));
            assertTrue(row.applyAsDouble(ControlsOptionsScreen.LABELS[i]) + value + 40 <= 680 - 84,
                    "a row holds its label and value: " + ControlsOptionsScreen.LABELS[i]);
        }
        for (String button : List.of("APPLY [F5]", "BACK [Esc]")) {
            assertTrue(row.applyAsDouble(button) <= 190 - 12, button);
        }
        assertTrue(regular(1.5f).applyAsDouble("CONTROLS") <= 360 - 24, "the title's button");
        for (boolean creative : new boolean[]{true, false}) {
            String settings = PauseMenu.keyLines(creative, 3)[1];
            assertTrue(settings.contains("[C] Controls"), "the pause menu names the screen: " + settings);
            assertTrue(regular(1.10f).applyAsDouble(settings) <= 600 - 40, settings);
        }
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
