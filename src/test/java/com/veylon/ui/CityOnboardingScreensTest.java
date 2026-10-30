package com.veylon.ui;

import com.veylon.entity.GameMode;
import com.veylon.gfx.ResourceManager;
import org.junit.jupiter.api.Test;
import org.lwjgl.stb.STBTTFontinfo;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.stb.STBTruetype.*;

/**
 * The screens a new city survivor reads first (urban milestone 21): the new-world choice describes
 * the abandoned city only when this session's new worlds are cities, keeps the truthful replace
 * notice and says the ordinary default is unchanged; the pause menu keeps the wilderness's reference
 * for its worlds and gives a city its own keys and goals. Every line fits where it is drawn.
 */
class CityOnboardingScreensTest {

    private static ToDoubleFunction<String> regular(float scale) {
        ByteBuffer data = ResourceManager.readBuffer("fonts/SourceSans3-Regular.ttf");
        STBTTFontinfo info = STBTTFontinfo.malloc();
        assertTrue(stbtt_InitFont(info, data));
        float px = stbtt_ScaleForPixelHeight(info, 12 * scale);
        return text -> {
            int[] advance = new int[1], bearing = new int[1];
            double width = 0;
            int previous = -1;
            for (int cp : text.codePoints().toArray()) {
                stbtt_GetCodepointHMetrics(info, cp, advance, bearing);
                width += advance[0] * px;
                if (previous >= 0) width += stbtt_GetCodepointKernAdvance(info, previous, cp) * px;
                previous = cp;
            }
            return width;
        };
    }

    @Test
    void theNewWorldChoiceDescribesTheCityOnlyForACityAndKeepsTheReplaceNotice() {
        NewFrontierScreen screen = new NewFrontierScreen();
        screen.open(true);
        assertFalse(screen.showsCity(), "an ordinary session creates the wilderness");
        assertEquals("Hunger, weather, wounds and wildlife.", screen.description(GameMode.SURVIVAL)[0]);
        screen.open(true, true);
        assertTrue(screen.showsCity());
        assertTrue(screen.showsReplaceNotice(), "the shared slot's notice stays");
        assertEquals("Saving this new frontier will replace your current save.", NewFrontierScreen.REPLACE_NOTICE);
        assertTrue(String.join(" ", screen.description(GameMode.SURVIVAL)).contains("Search buildings"));
        assertTrue(String.join(" ", screen.description(GameMode.CREATIVE)).contains("Fly"));
        assertEquals(GameMode.SURVIVAL, screen.selectedMode(), "Survival is still the default choice");

        ToDoubleFunction<String> card = regular(1.15f);
        for (GameMode mode : GameMode.values()) {
            for (String line : screen.description(mode)) {
                assertTrue(card.applyAsDouble(line) <= 300, "fits a 320-wide card: " + line);
            }
        }
        ToDoubleFunction<String> note = regular(1.2f);
        for (String line : new String[]{NewFrontierScreen.CITY_NOTICE, NewFrontierScreen.CITY_DEFAULT_NOTE,
                NewFrontierScreen.REPLACE_NOTICE}) {
            assertTrue(note.applyAsDouble(line) <= 1240, "fits a 1280-wide screen: " + line);
        }
    }

    @Test
    void thePauseMenuKeepsTheWildernessReferenceAndGivesACityItsOwn() {
        String[] wild = PauseMenu.controls(false, false);
        assertTrue(Arrays.stream(wild).anyMatch(l -> l.contains("Repair the distress beacon")), "historical worlds unchanged");
        assertTrue(Arrays.stream(wild).anyMatch(l -> l.contains("Map (POIs)")));
        String[] city = PauseMenu.controls(true, true);
        assertTrue(Arrays.stream(city).noneMatch(l -> l.contains("beacon") || l.contains("blueprint")), "no wilderness goals");
        assertTrue(Arrays.stream(city).anyMatch(l -> l.contains("Map, journal, floor sketch")));
        assertTrue(Arrays.stream(city).anyMatch(l -> l.contains("Crouch + LMB")), "salvage is taught");
        assertTrue(Arrays.stream(city).anyMatch(l -> l.contains("radio relay")));
        assertTrue(Arrays.stream(PauseMenu.controls(true, false)).noneMatch(l -> l.contains("radio relay")),
                "a city without a relay never promises one");
        ToDoubleFunction<String> line = regular(1.3f);
        for (String l : city) { assertTrue(line.applyAsDouble(l) <= 600 - 72, "fits the 600-wide panel: " + l); }
        assertTrue(regular(1.1f).applyAsDouble(PauseMenu.CITY_LABEL) <= 560);
    }
}
