package com.veylon.ui;

import com.veylon.gfx.ResourceManager;
import org.junit.jupiter.api.Test;
import org.lwjgl.stb.STBTTFontinfo;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.stb.STBTruetype.*;

/**
 * The city map's geometry at every supported logical screen (urban milestone 21): 1280×720 (the
 * smallest, including 1.5 scale clamped at 720p), 1920×1080 and its 1.5 scale, 0.75 scale at 720p
 * and 1080p, and 2560×1440. Every panel stays on screen and apart, the map stays large enough to
 * read, and journal text wraps within its panel in the bundled semibold face.
 */
class CityMapLayoutTest {
    private static final int[][] SCREENS = {{1280, 720}, {1707, 960}, {1920, 1080}, {2560, 1440}, {1280, 1024}};

    private static boolean inside(HudLayout.Rect outer, HudLayout.Rect inner) {
        return inner.x() >= outer.x() - .01f && inner.y() >= outer.y() - .01f
                && inner.right() <= outer.right() + .01f && inner.bottom() <= outer.bottom() + .01f;
    }

    @Test
    void everyPanelStaysOnScreenApartAndReadable() {
        for (int[] s : SCREENS) {
            CityMapView.Layout l = CityMapView.Layout.of(s[0], s[1]);
            HudLayout.Rect screen = new HudLayout.Rect(0, 0, s[0], s[1]);
            String at = s[0] + "x" + s[1];
            assertTrue(inside(screen, l.frame()), "frame on screen at " + at);
            for (HudLayout.Rect r : List.of(l.map(), l.side(), l.legend())) { assertTrue(inside(l.frame(), r), at + " " + r); }
            assertTrue(inside(l.side(), l.journal()) && inside(l.side(), l.here()), at);
            assertFalse(l.map().overlaps(l.side()), "map and side panel apart at " + at);
            assertFalse(l.journal().overlaps(l.here()), "journal and here apart at " + at);
            assertFalse(l.legend().overlaps(l.map()) || l.legend().overlaps(l.side()), "legend below at " + at);
            assertEquals(l.map().width(), l.map().height(), .01f, "the map is square");
            assertTrue(l.map().width() >= 560, "the map is large enough to read at " + at + ": " + l.map().width());
            assertTrue(l.here().height() >= 220, "room for a floor sketch at " + at);
        }
    }

    @Test
    void journalTextWrapsWithinItsPanelInTheBundledFace() {
        ByteBuffer data = ResourceManager.readBuffer("fonts/SourceSans3-Semibold.ttf");
        try (STBTTFontinfo info = STBTTFontinfo.malloc()) {
            assertTrue(stbtt_InitFont(info, data));
            float scale = stbtt_ScaleForPixelHeight(info, 12 * HudStyle.MICRO);
            ToDoubleFunction<String> measure = text -> {
                int[] advance = new int[1], bearing = new int[1];
                double width = 0;
                int previous = -1;
                for (int cp : text.codePoints().toArray()) {
                    stbtt_GetCodepointHMetrics(info, cp, advance, bearing);
                    width += advance[0] * scale;
                    if (previous >= 0) width += stbtt_GetCodepointKernAdvance(info, previous, cp) * scale;
                    previous = cp;
                }
                return width;
            };
            float width = CityMapView.Layout.of(1280, 720).side().width() - HudLayout.PAD * 2;
            String[] details = {
                    "Find the people hiding below the street; leave them 3 supplies or guide them to safety",
                    "Take medicine from the Halvard Street Community Clinic and bring 2 back",
                    "Survivors at camps and strongholds may need help: ask them [0] when you talk.",
                    "Pay the Wardens 6 supplies, or take the module as their friend"};
            for (String detail : details) {
                List<String> lines = HudLayout.wrap(detail, width, 2, measure);
                assertFalse(lines.isEmpty());
                assertTrue(lines.size() <= 2);
                for (String line : lines) assertTrue(measure.applyAsDouble(line) <= width, "overflow: " + line);
                String joined = String.join(" ", lines);
                if (!joined.equals(detail)) assertTrue(joined.endsWith("…"), "truncation is visible: " + joined);
            }
            assertEquals(List.of("A B"), HudLayout.wrap("A  B", 100, 2, t -> t.length() * 8.0));
            assertEquals(List.of("AAAA", "BB…"), HudLayout.wrap("AAAA BB CC", 32, 2, t -> t.length() * 8.0));
            assertEquals(List.of(), HudLayout.wrap(null, 100, 2, t -> 1.0));
        }
    }
}
