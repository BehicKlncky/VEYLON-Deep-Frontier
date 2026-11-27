package com.veylon.ui;

import com.veylon.engine.Input;
import com.veylon.entity.GameMode;
import com.veylon.gfx.ResourceManager;
import com.veylon.save.LoadResult;
import com.veylon.save.SaveSlots;
import com.veylon.save.SaveSummary;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;
import org.lwjgl.stb.STBTTFontinfo;

import java.nio.ByteBuffer;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.stb.STBTruetype.*;

/**
 * 0.9.1 milestone 04: the slot picker's keys, words and room. Enter means what the slot holds and
 * why the picker is open; replacing, deleting and restoring each ask first and name what the slot
 * holds; Escape or N cancels a confirmation before anything else; every line fits where it is drawn
 * at the smallest logical canvas UiRenderer draws (1280 x 720, or 960 x 720 on a 4:3 screen).
 */
class SlotPickerScreenTest {

    /** 20 Nov 2026, 14:32 UTC. */
    private static final long SAVED_AT = 1_795_185_120_000L;
    private static final ZoneOffset UTC = ZoneOffset.UTC;

    @Test
    void enterLoadsAReadySlotAndSaysWhyAnyOtherDoesNotOpen() {
        SlotPickerScreen picker = new SlotPickerScreen();
        picker.open(SlotPickerScreen.Purpose.LOAD, List.of(ready(1), older(2), damaged(3, true)), false, 1);
        Input input = new Input();
        assertEquals(SlotPickerScreen.Action.CHOOSE, press(picker, input, GLFW_KEY_ENTER), "a ready slot loads");

        assertEquals(SlotPickerScreen.Action.NONE, press(picker, input, GLFW_KEY_2));
        assertEquals(2, picker.selected(), "a digit chooses the slot");
        assertEquals(SlotPickerScreen.Action.NONE, press(picker, input, GLFW_KEY_ENTER), "another version does not load");
        assertEquals(new LoadResult(LoadResult.Outcome.OLDER_VERSION, "").message(), picker.notice());

        press(picker, input, GLFW_KEY_DOWN);
        assertEquals(3, picker.selected());
        assertEquals("", picker.notice(), "a new slot clears the last notice");
        assertEquals(SlotPickerScreen.Action.NONE, press(picker, input, GLFW_KEY_ENTER), "a damaged slot does not load");
        assertTrue(picker.notice().contains("R"), "it says how to restore it: " + picker.notice());

        press(picker, input, GLFW_KEY_DOWN);
        assertEquals(1, picker.selected(), "the arrows wrap");
        press(picker, input, GLFW_KEY_UP);
        assertEquals(3, picker.selected());
        assertEquals(SlotPickerScreen.Action.BACK, press(picker, input, GLFW_KEY_ESCAPE));
    }

    @Test
    void aNewWorldStartsInAnEmptySlotAndAsksBeforeReplacingOneNamingWhatItHolds() {
        SlotPickerScreen picker = new SlotPickerScreen();
        List<SaveSlots.Slot> slots = List.of(ready(1), SlotPickerScreenTest.empty(2), older(3));
        picker.open(SlotPickerScreen.Purpose.NEW, slots, false, SlotPickerScreen.firstEmpty(slots));
        Input input = new Input();
        assertEquals(2, picker.selected(), "the first empty slot is offered");
        assertEquals(SlotPickerScreen.Action.CHOOSE, press(picker, input, GLFW_KEY_ENTER), "an empty slot asks nothing");

        press(picker, input, GLFW_KEY_1);
        assertEquals(SlotPickerScreen.Action.NONE, press(picker, input, GLFW_KEY_ENTER));
        assertEquals(SlotPickerScreen.Ask.REPLACE, picker.asking(), "an occupied slot asks first");
        assertEquals("REPLACE SLOT 1?", picker.confirmationTitle());
        String[] lines = picker.confirmation(UTC);
        assertEquals("It holds: The abandoned city · Survival", lines[0], "naming what it holds");
        assertEquals("Day 4, 13:20 · played 1 h 05 min · saved 20 Nov 2026, 14:32", lines[1]);
        assertEquals("Starting the new world here replaces it.", lines[2]);

        assertEquals(SlotPickerScreen.Action.NONE, press(picker, input, GLFW_KEY_ESCAPE), "Esc cancels, not leaves");
        assertEquals(SlotPickerScreen.Ask.NONE, picker.asking());
        press(picker, input, GLFW_KEY_ENTER);
        assertEquals(SlotPickerScreen.Action.NONE, press(picker, input, GLFW_KEY_Y, GLFW_KEY_N), "a mixed press cancels");
        assertEquals(SlotPickerScreen.Ask.NONE, picker.asking());
        press(picker, input, GLFW_KEY_ENTER);
        assertEquals(SlotPickerScreen.Action.CHOOSE, press(picker, input, GLFW_KEY_Y), "Y replaces");

        press(picker, input, GLFW_KEY_3);
        press(picker, input, GLFW_KEY_ENTER);
        assertEquals("It holds a save from another version of VEYLON.", picker.confirmation(UTC)[0]);
        assertEquals(SlotPickerScreen.Action.CHOOSE, press(picker, input, GLFW_KEY_ENTER), "Enter confirms too");
    }

    @Test
    void deletingAsksFirstAndAnEmptySlotHasNothingToDelete() {
        SlotPickerScreen picker = new SlotPickerScreen();
        picker.open(SlotPickerScreen.Purpose.LOAD, List.of(ready(1), SlotPickerScreenTest.empty(2), damaged(3, false)), false, 2);
        Input input = new Input();
        assertEquals(SlotPickerScreen.Action.NONE, press(picker, input, GLFW_KEY_DELETE));
        assertEquals(SlotPickerScreen.Ask.NONE, picker.asking(), "an empty slot asks nothing");
        assertEquals("Slot 2 is empty.", picker.notice());

        press(picker, input, GLFW_KEY_1);
        press(picker, input, GLFW_KEY_DELETE);
        assertEquals(SlotPickerScreen.Ask.DELETE, picker.asking());
        assertEquals("DELETE SLOT 1?", picker.confirmationTitle());
        String[] lines = picker.confirmation(UTC);
        assertTrue(lines[0].startsWith("It holds: The abandoned city"), lines[0]);
        assertEquals("Its save and its backups are deleted. This cannot be undone.", lines[lines.length - 1]);
        assertEquals(SlotPickerScreen.Action.NONE, press(picker, input, GLFW_KEY_N), "N keeps it");
        press(picker, input, GLFW_KEY_BACKSPACE);
        assertEquals(SlotPickerScreen.Ask.DELETE, picker.asking(), "Backspace deletes too, for keyboards without Delete");
        assertEquals(SlotPickerScreen.Action.DELETE, press(picker, input, GLFW_KEY_Y));
        assertEquals(1, picker.selected(), "the action names the slot");
    }

    @Test
    void onlyADamagedSlotWithAnIntactBackupOffersARestore() {
        SlotPickerScreen picker = new SlotPickerScreen();
        picker.open(SlotPickerScreen.Purpose.LOAD, List.of(ready(1), damaged(2, false), damaged(3, true)), false, 1);
        Input input = new Input();
        press(picker, input, GLFW_KEY_R);
        assertEquals(SlotPickerScreen.Ask.NONE, picker.asking());
        assertEquals("Only a damaged slot is restored from its backup.", picker.notice());
        press(picker, input, GLFW_KEY_2);
        press(picker, input, GLFW_KEY_R);
        assertEquals("No backup of slot 2 can be restored.", picker.notice());

        press(picker, input, GLFW_KEY_3);
        press(picker, input, GLFW_KEY_R);
        assertEquals(SlotPickerScreen.Ask.RESTORE, picker.asking());
        assertEquals("RESTORE SLOT 3?", picker.confirmationTitle());
        String[] lines = picker.confirmation(UTC);
        assertEquals("From its backup of 20 Nov 2026, 14:32:", lines[0]);
        assertEquals("The wilderness · Creative · Day 2, 06:00 · played 12 min", lines[1]);
        assertEquals("The damaged save is set aside in the saves folder, not deleted.", lines[2]);
        assertEquals(SlotPickerScreen.Action.RESTORE, press(picker, input, GLFW_KEY_Y));

        picker.open(SlotPickerScreen.Purpose.NEW, List.of(ready(1), damaged(2, false), damaged(3, true)), false, 3);
        press(picker, input, GLFW_KEY_R);
        assertEquals(SlotPickerScreen.Ask.NONE, picker.asking(), "a new world restores nothing");
    }

    @Test
    void aNewWorldGoesToTheFirstEmptySlotAndALoadOpensOnTheSlotSavedLast() {
        SaveSlots.Slot earlier = readyAt(1, SAVED_AT - 60_000), later = readyAt(3, SAVED_AT);
        assertEquals(2, SlotPickerScreen.firstEmpty(List.of(earlier, SlotPickerScreenTest.empty(2), later)));
        assertEquals(1, SlotPickerScreen.firstEmpty(List.of(earlier, older(2), later)), "slot 1 when none is empty");
        assertEquals(3, SlotPickerScreen.lastSaved(List.of(earlier, SlotPickerScreenTest.empty(2), later), 2));
        assertEquals(2, SlotPickerScreen.lastSaved(List.of(SlotPickerScreenTest.empty(1), older(2), damaged(3, true)), 2),
                "the active slot when none is ready");
    }

    @Test
    void theWordsNameTheWorldTheModeTheTimeAndTheBuild() {
        SaveSummary marked = new SaveSummary(SAVED_AT, "0.9.1", 3_900, GameMode.SURVIVAL, true, 4_800, "Ashgrove");
        assertEquals("The abandoned city · Survival, a Creative world", SlotPickerScreen.worldLine(World.DEFAULT_NEW_WORLD_GENERATOR, marked));
        assertEquals("The wilderness · Survival, a Creative world", SlotPickerScreen.worldLine(World.WILDERNESS_NEW_WORLD_GENERATOR, marked));
        assertEquals("Day 4, 08:00 · played 1 h 05 min", SlotPickerScreen.timeLine(marked));
        assertEquals("Saved 20 Nov 2026, 14:32 · VEYLON 0.9.1, save epoch 2", SlotPickerScreen.savedLine(marked, UTC));
        SaveSummary development = new SaveSummary(SAVED_AT, "development", 0, GameMode.CREATIVE, true, 480, "");
        assertEquals("Saved 20 Nov 2026, 14:32 · a development build, save epoch 2", SlotPickerScreen.savedLine(development, UTC));
        assertEquals("under a minute", SlotPickerScreen.duration(59));
        assertEquals("1 min", SlotPickerScreen.duration(60));
        assertEquals("59 min", SlotPickerScreen.duration(3_599));
        assertEquals("1 h 00 min", SlotPickerScreen.duration(3_600));
        assertEquals("100 h 01 min", SlotPickerScreen.duration(360_060));
        assertEquals("DAMAGED  /  BACKUP AVAILABLE", SlotPickerScreen.tag(damaged(1, true)));
        assertEquals("ANOTHER VERSION", SlotPickerScreen.tag(older(1)));
    }

    @Test
    void everyLineFitsWhereItIsDrawn() {
        ToDoubleFunction<String> card = regular(SlotPickerScreen.LINE_SCALE), tag = regular(1.1f), heading = regular(1.5f);
        ToDoubleFunction<String> confirm = regular(1.2f), title = regular(2.0f), notice = regular(1.2f);
        ToDoubleFunction<String> keys = regular(1.05f), note = regular(1.0f), button = regular(1.2f);
        float cardRoom = SlotPickerScreen.CARD_MAX_W - 2 * SlotPickerScreen.CARD_PAD;
        float panelRoom = SlotPickerScreen.PANEL_MAX_W - 40;

        List<SaveSlots.Slot> worst = List.of(longest(1), damaged(2, true), older(3));
        for (SlotPickerScreen.Purpose purpose : SlotPickerScreen.Purpose.values()) {
            SlotPickerScreen picker = new SlotPickerScreen();
            picker.open(purpose, worst, true, 1);
            for (SaveSlots.Slot slot : worst) {
                for (String line : picker.lines(slot, UTC)) {
                    assertTrue(card.applyAsDouble(line) <= cardRoom, "fits a card: " + line);
                }
                assertTrue(heading.applyAsDouble("SLOT 3") + tag.applyAsDouble(SlotPickerScreen.tag(slot)) + 24 <= cardRoom);
            }
            for (String line : picker.everyLine(UTC)) {
                assertTrue(confirm.applyAsDouble(line) <= panelRoom || card.applyAsDouble(line) <= cardRoom,
                        "fits where it is drawn: " + line);
            }
        }
        SlotPickerScreen picker = new SlotPickerScreen();
        picker.open(SlotPickerScreen.Purpose.LOAD, worst, true, 1);
        Input input = new Input();
        List<String> confirmations = new ArrayList<>();
        for (int key : new int[]{GLFW_KEY_DELETE, GLFW_KEY_R}) {
            press(picker, input, key == GLFW_KEY_R ? GLFW_KEY_2 : GLFW_KEY_1);
            press(picker, input, key);
            confirmations.add(picker.confirmationTitle());
            confirmations.addAll(List.of(picker.confirmation(UTC)));
            press(picker, input, GLFW_KEY_N);
        }
        for (String line : confirmations) {
            assertTrue(confirm.applyAsDouble(line) <= panelRoom, "fits the confirmation: " + line);
        }
        assertTrue(title.applyAsDouble("RESTORE SLOT 3?") <= panelRoom);

        List<String> notices = new ArrayList<>(List.of("Slot 3 is empty.", "Slot 3 deleted.", "Slot 3 could not be deleted.",
                "Slot 3 is damaged. Press R to restore its backup.",
                "Slot 3 is damaged, and no backup of it can be restored.",
                "No backup of slot 3 can be restored.", "Only a damaged slot is restored from its backup."));
        for (LoadResult.Outcome outcome : LoadResult.Outcome.values()) {
            notices.add(new LoadResult(outcome, "").message());
        }
        for (String line : notices) {
            assertTrue(notice.applyAsDouble(line) <= 820, "fits under the cards: " + line);
        }
        assertTrue(keys.applyAsDouble(SlotPickerScreen.LOAD_KEYS) <= 820, SlotPickerScreen.LOAD_KEYS);
        assertTrue(keys.applyAsDouble(SlotPickerScreen.NEW_KEYS) <= 820, SlotPickerScreen.NEW_KEYS);
        assertTrue(note.applyAsDouble(SlotPickerScreen.LEGACY_NOTE) <= 820, SlotPickerScreen.LEGACY_NOTE);
        for (String label : List.of("START HERE [Enter]", "LOAD [Enter]", "RESTORE [R]", "DELETE [Del]", "BACK [Esc]",
                "REPLACE [Y]", "DELETE [Y]", "RESTORE [Y]", "KEEP [N]", "CANCEL [N]")) {
            assertTrue(button.applyAsDouble(label) <= 180 - 12, "fits its button: " + label);
        }
        // The pause menu's save keys and NEW FRONTIER's button name the slot or lead to it.
        assertTrue(regular(1.10f).applyAsDouble(PauseMenu.saveKeys(SaveSlots.COUNT)) <= 600 - 40,
                PauseMenu.saveKeys(SaveSlots.COUNT));
        assertTrue(regular(1.28f).applyAsDouble("CHOOSE SLOT [Enter]") <= 190 - 12);
        assertTrue(keys.applyAsDouble(NewFrontierScreen.KEYS) <= 820, NewFrontierScreen.KEYS);
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static SaveSlots.Slot ready(int n) {
        return readyAt(n, SAVED_AT);
    }

    private static SaveSlots.Slot readyAt(int n, long savedAt) {
        SaveSummary summary = new SaveSummary(savedAt, "0.9.1", 3_900, GameMode.SURVIVAL, false,
                3 * 24 * 60 + 13 * 60 + 20, "Ashgrove · Apartment · floor 2 · your refuge");
        return new SaveSlots.Slot(n, SaveSlots.State.READY,
                new SaveSummary.Inspection(null, World.DEFAULT_NEW_WORLD_GENERATOR, summary), true, null);
    }

    /** The longest card a ready slot draws: a marked world, many hours and a long place. */
    private static SaveSlots.Slot longest(int n) {
        SaveSummary summary = new SaveSummary(SAVED_AT, "0.10.0-development", 4_000_000, GameMode.SURVIVAL, true,
                9_999 * 24 * 60, "Reclaimed residential · Apartment block · floor 11 of 12 · survivor camp");
        return new SaveSlots.Slot(n, SaveSlots.State.READY,
                new SaveSummary.Inspection(null, World.DEFAULT_NEW_WORLD_GENERATOR, summary), true, null);
    }

    private static SaveSlots.Slot empty(int n) {
        return new SaveSlots.Slot(n, SaveSlots.State.EMPTY,
                new SaveSummary.Inspection(new LoadResult(LoadResult.Outcome.NO_SAVE, ""), 0, null), false, null);
    }

    private static SaveSlots.Slot older(int n) {
        return new SaveSlots.Slot(n, SaveSlots.State.OTHER_VERSION,
                new SaveSummary.Inspection(new LoadResult(LoadResult.Outcome.OLDER_VERSION, ""), 0, null), true, null);
    }

    private static SaveSlots.Slot damaged(int n, boolean withBackup) {
        SaveSummary backup = new SaveSummary(SAVED_AT, "0.9.1", 720, GameMode.CREATIVE, true, 24 * 60 + 6 * 60, "Pine Forest");
        return new SaveSlots.Slot(n, SaveSlots.State.DAMAGED,
                new SaveSummary.Inspection(new LoadResult(LoadResult.Outcome.DAMAGED, ""), 0, null), true,
                withBackup ? new SaveSlots.Backup(1,
                        new SaveSummary.Inspection(null, World.WILDERNESS_NEW_WORLD_GENERATOR, backup)) : null);
    }

    private static SlotPickerScreen.Action press(SlotPickerScreen picker, Input input, int... keys) {
        for (int key : keys) {
            input.onKey(key, GLFW_PRESS);
        }
        SlotPickerScreen.Action action = picker.handleKeys(input);
        input.endFrame();
        for (int key : keys) {
            input.onKey(key, GLFW_RELEASE);
        }
        return action;
    }

    /**
     * The game's regular face. stb keeps a pointer into the buffer it was given, so the buffer is
     * held for as long as the class is loaded: a measurer that outlived a collected buffer would
     * read freed memory.
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
