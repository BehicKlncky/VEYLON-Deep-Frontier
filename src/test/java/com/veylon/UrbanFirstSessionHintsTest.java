package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.gfx.ResourceManager;
import com.veylon.ui.HudLayout;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.stb.STBTTFontinfo;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.stb.STBTruetype.*;

/**
 * A new layout-sixteen city's first lines (urban milestone 21): five short lines in Survival and
 * Creative's own, each whole on the HUD's log beside its timestamp; earlier cities keep theirs; and
 * NEW FRONTIER describes the city when the development selector makes this session's worlds cities.
 */
class UrbanFirstSessionHintsTest {
    @TempDir Path temporary;

    private static Game fresh(int generator, GameMode mode) {
        Game g = new Game();
        g.newWorld(SurvivorWorldArena.SEED, true, generator, mode);
        return g;
    }

    @Test
    void aNewCityGreetsEachModeWithItsOwnShortLines() {
        List<String> survival = fresh(World.GEN_URBAN_MISSIONS, GameMode.SURVIVAL).eventLog.all();
        for (String hint : WorldBootstrap.CITY_HINTS) {
            assertTrue(survival.stream().anyMatch(l -> l.endsWith(hint)), "missing: " + hint + " in " + survival);
        }
        List<String> creative = fresh(World.GEN_URBAN_MISSIONS, GameMode.CREATIVE).eventLog.all();
        for (String hint : WorldBootstrap.CITY_CREATIVE_HINTS) {
            assertTrue(creative.stream().anyMatch(l -> l.endsWith(hint)), "missing: " + hint);
        }
        assertTrue(creative.stream().noneMatch(l -> l.endsWith(WorldBootstrap.CITY_HINTS[2])),
                "Creative is not told that searching is a risk");
        List<String> older = fresh(World.GEN_URBAN_UTILITIES, GameMode.SURVIVAL).eventLog.all();
        assertTrue(older.stream().anyMatch(l -> l.contains("City refuge. Close the doors")), "a generator-18 city keeps its lines");
    }

    @Test
    void everyLineStaysWholeOnTheHudLogBesideItsTimestamp() {
        ByteBuffer data = ResourceManager.readBuffer("fonts/SourceSans3-Semibold.ttf");
        try (STBTTFontinfo info = STBTTFontinfo.malloc()) {
            assertTrue(stbtt_InitFont(info, data));
            float scale = stbtt_ScaleForPixelHeight(info, 12 * 1.4f);
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
            float width = HudLayout.STATUS_WIDTH - HudLayout.PAD * 2;
            for (String[] set : new String[][]{WorldBootstrap.CITY_HINTS, WorldBootstrap.CITY_CREATIVE_HINTS}) {
                for (String hint : set) {
                    String line = "[D1 08:00] " + hint;
                    assertTrue(measure.applyAsDouble(line) <= width, "cut short on the log: " + line);
                }
            }
        }
    }

    @Test
    void newFrontierDescribesTheCityWhenTheSessionMakesCities() {
        Game game = new Game();
        game.frontend.savePath = temporary.resolve("absent.sav");
        game.frontend.openNewFrontier(false);
        assertFalse(game.newFrontierScreen.showsCity(), "an ordinary session's new world is the wilderness");
        game.frontend.newWorldGenerator = World.GEN_URBAN_MISSIONS;
        game.frontend.openNewFrontier(true);
        assertTrue(game.newFrontierScreen.showsCity());
        assertTrue(game.newFrontierScreen.showsReplaceNotice());
    }
}
