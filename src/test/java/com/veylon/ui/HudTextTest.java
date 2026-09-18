package com.veylon.ui;

import com.veylon.gfx.ResourceManager;
import org.junit.jupiter.api.Test;
import org.lwjgl.stb.STBTTFontinfo;

import java.nio.ByteBuffer;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.stb.STBTruetype.*;

class HudTextTest {
    @Test
    void longQuestNavigationEventsAndNamesFitTheActualBundledSemiboldFont() {
        ByteBuffer data = ResourceManager.readBuffer("fonts/SourceSans3-Semibold.ttf");
        try (STBTTFontinfo info = STBTTFontinfo.malloc()) {
            assertTrue(stbtt_InitFont(info, data));
            float scale = stbtt_ScaleForPixelHeight(info, 12 * HudStyle.BODY);
            ToDoubleFunction<String> measure = text -> {
                int[] advance = new int[1], bearing = new int[1];
                int previous = -1;
                double width = 0;
                for (int cp : text.codePoints().toArray()) {
                    stbtt_GetCodepointHMetrics(info, cp, advance, bearing);
                    width += advance[0] * scale;
                    if (previous >= 0) width += stbtt_GetCodepointKernAdvance(info, previous, cp) * scale;
                    previous = cp;
                }
                return width;
            };
            String[] labels = {
                    "Explore the marked cave region beneath the settlement  [old request: return to its provider to reissue]",
                    "Events / Toxic fog, drought, raiders and a particularly long frontier status update",
                    "Return to request provider - 123456m NW  [M]",
                    "Smoke Inhalation 124s", "Çığlık — A distant settlement with a long name"
            };
            for (float width : new float[]{103, 135, 280, 388}) for (String label : labels) {
                String fitted = HudLayout.fit(label, width, measure);
                assertTrue(measure.applyAsDouble(fitted) <= width, "overflow: " + fitted);
                if (measure.applyAsDouble(label) > width) {
                    assertTrue(fitted.endsWith("…"), "truncation is visible, not silent clipping");
                } else assertEquals(label, fitted);
            }
        }
    }

    @Test
    void fittingHandlesEmptyNarrowMultilineAndSupplementaryText() {
        ToDoubleFunction<String> cells = s -> s.codePointCount(0, s.length()) * 8;
        assertEquals("", HudLayout.fit(null, 100, cells));
        assertEquals("", HudLayout.fit("Long", 7, cells));
        assertEquals("…", HudLayout.fit("Long", 8, cells));
        assertEquals("A B C D", HudLayout.fit("A\nB\rC\tD", 100, cells));
        assertEquals("A😀…", HudLayout.fit("A😀BCD", 24, cells));
        assertEquals("", HudLayout.fit("Long", 0, cells));
    }
}
