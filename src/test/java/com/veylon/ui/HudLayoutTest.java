package com.veylon.ui;

import com.veylon.engine.UiRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static com.veylon.ui.HudLayout.*;
import static org.junit.jupiter.api.Assertions.*;

class HudLayoutTest {
    record View(int w, int h, float scale, boolean creative, int afflictions) { }

    static Stream<View> views() {
        return Stream.of(new int[]{1280, 720}, new int[]{1920, 1080})
                .flatMap(size -> Stream.of(0.75f, 1f, 1.5f)
                        .flatMap(scale -> Stream.of(false, true)
                                .flatMap(creative -> Stream.of(0, 1, 7, 8)
                                        .map(count -> new View(size[0], size[1], scale, creative, count)))));
    }

    @ParameterizedTest
    @MethodSource("views")
    void cardsAndAllNineSlotsStayInsideMarginsAndNeverOverlap(View view) {
        UiRenderer ui = new UiRenderer();
        // begin is headless: exercise the real safe scale policy instead of duplicating it.
        ui.begin(view.w, view.h, view.scale);
        HudLayout layout = new HudLayout(ui.screenW(), ui.screenH(), view.creative, view.afflictions);
        List<Rect> boxes = List.of(layout.status, layout.context, layout.mission, layout.eventLog,
                layout.hotbar, layout.heldItem, layout.weapon, layout.target, layout.prompt);
        for (Rect box : boxes) {
            assertTrue(box.x() >= MARGIN && box.y() >= MARGIN, "top/left margin: " + box);
            assertTrue(box.right() <= ui.screenW() - MARGIN, "right edge: " + box);
            assertTrue(box.bottom() <= ui.screenH() - MARGIN, "bottom edge: " + box);
            for (Rect other : boxes) if (box != other) {
                assertFalse(box.overlaps(other), box + " collides with " + other);
            }
        }
        assertTrue(layout.logLines >= 2, "even all conditions leave room for recent events");
        for (int selected = 0; selected < SLOT_COUNT; selected++) {
            for (int i = 0; i < SLOT_COUNT; i++) {
                Rect slot = layout.slot(i, i == selected);
                assertTrue(slot.x() >= layout.hotbar.x() && slot.right() <= layout.hotbar.right());
                assertTrue(slot.y() >= layout.hotbar.y() && slot.bottom() <= layout.hotbar.bottom());
                assertFalse(slot.overlaps(layout.status));
                if (i > 0) assertFalse(slot.overlaps(layout.slot(i - 1, i - 1 == selected)));
            }
        }
        assertTrue(layout.target.y() > ui.screenH() / 2f + 16, "focus background clears aim point");
    }

    @Test
    void defaultComponentsAreLargerWithoutChangingGlobalScale() {
        UiRenderer ui = new UiRenderer();
        ui.begin(1280, 720, 1);
        assertEquals(1, ui.uiScale());
        assertTrue(BAR_WIDTH >= 250 && BAR_WIDTH <= 290);
        assertTrue(BAR_HEIGHT >= 16 && BAR_HEIGHT <= 20);
        assertTrue(SLOT_SIZE >= 54 && SLOT_SIZE <= 58);
        ui.begin(1280, 720, 1.5f);
        assertEquals(1, ui.uiScale(), "existing 720 logical-pixel safety cap is unchanged");
        ui.begin(1920, 1080, 1.5f);
        assertEquals(1.5f, ui.uiScale());
    }

    @Test
    void creativeHasEquivalentFootprintAndIgnoresMedicalChipCount() {
        HudLayout survival = new HudLayout(1280, 720, false, 1);
        HudLayout creative = new HudLayout(1280, 720, true, 0);
        assertEquals(survival.status, creative.status);
        assertEquals(creative.status, new HudLayout(1280, 720, true, 8).status);
        assertEquals(survival.hotbar, creative.hotbar);
    }

    @Test
    void lowAndCriticalStatesHaveExplicitWordsAtTheirBoundaries() {
        assertEquals("CRITICAL", vitalState(0));
        assertEquals("CRITICAL", vitalState(0.1f));
        assertEquals("LOW", vitalState(0.1001f));
        assertEquals("LOW", vitalState(0.25f));
        assertEquals("", vitalState(0.2501f));
    }
}
