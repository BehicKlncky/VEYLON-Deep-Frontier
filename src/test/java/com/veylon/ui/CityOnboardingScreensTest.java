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
 * The screens a new city survivor reads first (urban milestones 21 and 26): the new-world choice
 * opens on the abandoned city, offers the wilderness as the legacy world, describes each and keeps
 * a truthful replace notice naming the world chosen; the pause menu keeps the wilderness's reference
 * for its worlds and gives a city its own keys and goals. Every line fits where it is drawn.
 */
class CityOnboardingScreensTest {

    /**
     * The game's regular face. stb keeps a pointer into the buffer it was given, so the buffer is
     * held for as long as the class is loaded: a measurer that outlived a collected buffer would
     * read freed memory and take the test JVM down.
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

    @Test
    void theNewWorldChoiceOpensOnTheCityOffersTheWildernessAndLeadsToTheSlot() {
        NewFrontierScreen screen = new NewFrontierScreen();
        screen.open(false);
        assertFalse(screen.showsCity(), "a wilderness choice describes the wilderness");
        assertEquals("Hunger, weather, wounds and wildlife.", screen.description(GameMode.SURVIVAL)[0]);
        screen.open();
        assertTrue(screen.showsCity(), "every opening starts on the city");
        assertTrue(NewFrontierScreen.SLOT_NOTE.contains("slot"), "Enter goes on to the slot (0.9.1 milestone 04)");
        assertTrue(String.join(" ", screen.description(GameMode.SURVIVAL)).contains("Search buildings"));
        assertTrue(String.join(" ", screen.description(GameMode.CREATIVE)).contains("Fly"));
        assertEquals(GameMode.SURVIVAL, screen.selectedMode(), "Survival is still the default choice");
        assertTrue(NewFrontierScreen.WILDERNESS_TITLE.contains("legacy"), "the wilderness is labelled as legacy");

        ToDoubleFunction<String> card = regular(1.15f);
        for (boolean city : new boolean[]{true, false}) {
            screen.open(city);
            for (GameMode mode : GameMode.values()) {
                for (String line : screen.description(mode)) {
                    assertTrue(card.applyAsDouble(line) <= 300, "fits a 320-wide card: " + line);
                }
            }
        }
        for (String tab : new String[]{NewFrontierScreen.CITY_TITLE, NewFrontierScreen.WILDERNESS_TITLE}) {
            assertTrue(card.applyAsDouble(tab) <= 300, "fits a 320-wide tab: " + tab);
        }
        // Kept within 820 units: UiRenderer never draws fewer than 720 logical rows, so a 16:9
        // screen is at least 1280 units wide, and a 4:3 one 960.
        ToDoubleFunction<String> note = regular(1.1f), notice = regular(1.2f), keys = regular(1.05f);
        for (String line : new String[]{NewFrontierScreen.CITY_NOTE, NewFrontierScreen.WILDERNESS_NOTE}) {
            assertTrue(note.applyAsDouble(line) <= 820, "fits within 820 units: " + line);
        }
        for (String line : new String[]{NewFrontierScreen.SLOT_NOTE}) {
            assertTrue(notice.applyAsDouble(line) <= 820, "fits within 820 units: " + line);
        }
        assertTrue(keys.applyAsDouble(NewFrontierScreen.KEYS) <= 820, "fits within 820 units: " + NewFrontierScreen.KEYS);
    }

    /**
     * 0.9.1 milestone 06: the seed's row fits the narrowest screen, every number a seed can be fits
     * its field uncut, longer typed words show their end, and the lines around it fit too.
     */
    @Test
    void theSeedRowAndItsLinesFitAndLongWordsShowTheirEnd() {
        ToDoubleFunction<String> seed = regular(NewFrontierScreen.SEED_SCALE), button = regular(1.28f),
                hint = regular(1.05f);
        double row = seed.applyAsDouble(NewFrontierScreen.SEED_LABEL) + 14 + NewFrontierScreen.FIELD_W + 14
                + NewFrontierScreen.REROLL_W;
        assertTrue(row <= 960 - 48, "the row fits a 4:3 screen of 720 rows: " + row);
        assertTrue(button.applyAsDouble(NewFrontierScreen.REROLL_LABEL) <= NewFrontierScreen.REROLL_W - 12);
        for (long number : new long[]{Long.MIN_VALUE, Long.MAX_VALUE, -8_888_888_888_888_888_888L}) {
            String text = Long.toString(number);
            assertTrue(seed.applyAsDouble(text) <= NewFrontierScreen.FIELD_TEXT_SPACE, "a seed's number fits: " + text);
            assertEquals(text, NewFrontierScreen.fittedTail(text, seed), "and is never cut");
        }
        assertTrue(seed.applyAsDouble(NewFrontierScreen.PLACEHOLDER) <= NewFrontierScreen.FIELD_TEXT_SPACE);
        String wide = "W".repeat(NewFrontierScreen.MAX_SEED_CODE_POINTS);
        assertEquals(wide, NewFrontierScreen.fittedTail(wide, seed), "as many of the widest letters as fit are whole");
        // A face whose glyphs were wider would show the end of the words, never half a character.
        ToDoubleFunction<String> wider = text -> text.codePointCount(0, text.length()) * 20.0;
        String faces = "😀".repeat(NewFrontierScreen.MAX_SEED_CODE_POINTS);
        String tail = NewFrontierScreen.fittedTail(faces, wider);
        assertTrue(faces.endsWith(tail) && wider.applyAsDouble(tail) <= NewFrontierScreen.FIELD_TEXT_SPACE);
        assertEquals((int) (NewFrontierScreen.FIELD_TEXT_SPACE / 20), tail.codePointCount(0, tail.length()));
        assertFalse(Character.isLowSurrogate(tail.charAt(0)), "cut between characters");

        NewFrontierScreen screen = new NewFrontierScreen();
        screen.open(true, Long.MIN_VALUE);
        String widestNote = "These words make seed " + Long.MIN_VALUE + ".";
        for (String line : new String[]{NewFrontierScreen.KEYS, NewFrontierScreen.SEED_KEYS,
                NewFrontierScreen.OFFERED_NOTE, NewFrontierScreen.NUMBER_NOTE, widestNote, screen.seedNote()}) {
            assertTrue(hint.applyAsDouble(line) <= 820, "fits within 820 units: " + line);
        }
    }

    @Test
    void aCityDeathCardSaysWhereTheSurvivorWakes() {
        assertEquals("THE CITY CLAIMS YOU", PresentationOverlay.deathText(true)[0]);
        assertTrue(PresentationOverlay.deathText(true)[1].contains("refuge"), "a city respawns at its refuge (D-178)");
        assertFalse(PresentationOverlay.deathText(true)[1].contains("crash site"));
        assertTrue(PresentationOverlay.deathText(false)[1].contains("crash site"), "the wilderness card is unchanged");
        ToDoubleFunction<String> line = regular(1.35f);
        for (boolean city : new boolean[]{true, false}) {
            assertTrue(line.applyAsDouble(PresentationOverlay.deathText(city)[1]) <= 820,
                    "fits within 820 units: " + PresentationOverlay.deathText(city)[1]);
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
