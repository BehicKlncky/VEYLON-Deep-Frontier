package com.veylon;

import com.veylon.combat.ExplosionSystem;
import com.veylon.entity.GameMode;
import com.veylon.save.ForeignSaves;
import com.veylon.save.SaveSlots;
import com.veylon.ui.NewFrontierScreen;
import com.veylon.ui.PauseMenu;
import com.veylon.ui.SlotPickerScreen;
import com.veylon.ui.TitleScreen;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * 0.9.1 milestone 04's acceptance through the frontend controller's seams, with real files in a
 * folder of slots of the test's own: each slot round-trips its world in either mode without touching
 * the others; replacing and deleting ask first; a damaged slot is recovered from its backup at the
 * player's choice; F5 and F9 use the active slot, which the pause menu names; and the picker shows
 * every state without building a world.
 */
class SlotPickerRoutingTest {

    @TempDir
    Path directory;

    private SaveSlots slots;

    @BeforeEach
    void aFolderOfSlots() {
        slots = new SaveSlots(directory.resolve("slots"));
    }

    @Test
    void eachSlotRoundTripsItsWorldInEitherModeWithoutTouchingTheOthers() throws IOException {
        Game city = start(IntegratedCityFixture.SEED, 1);
        IntegratedCityFixture.Built built = IntegratedCityFixture.build(city);
        List<String> cityFacts = IntegratedCityFixture.describe(city, built.facts());
        assertTrue(city.saves.saveActive(), "F5 writes the populated city into slot 1");
        List<String> cityDurable = SaveAuditQaScene.durable(city);

        Game wild = start(20_261_150L, 2, GLFW_KEY_W, GLFW_KEY_C);
        assertEquals(GameMode.CREATIVE, wild.gameMode());
        assertFalse(wild.world.isUrban());
        edit(wild);
        assertTrue(wild.saves.saveActive());
        List<String> wildDurable = SaveAuditQaScene.durable(wild);

        Game creativeCity = start(20_261_151L, 3, GLFW_KEY_C);
        assertTrue(creativeCity.world.isUrban() && creativeCity.gameMode() == GameMode.CREATIVE);
        edit(creativeCity);
        assertTrue(creativeCity.saves.saveActive());
        List<String> creativeDurable = SaveAuditQaScene.durable(creativeCity);
        byte[][] files = {read(slots.current(1)), read(slots.current(2)), read(slots.current(3))};

        Game one = load(1);
        assertEquals(cityFacts, IntegratedCityFixture.describe(one, built.facts()), "every fact of the city");
        assertEquals(cityDurable, SaveAuditQaScene.durable(one));
        assertEquals(wildDurable, SaveAuditQaScene.durable(load(2)), "the Creative wilderness");
        assertEquals(creativeDurable, SaveAuditQaScene.durable(load(3)), "the Creative city");
        for (int slot = 1; slot <= SaveSlots.COUNT; slot++) {
            assertArrayEquals(files[slot - 1], read(slots.current(slot)), "loading never writes slot " + slot);
        }

        // A Survival wilderness replaces slot 2 once the picker has asked; slots 1 and 3 stay.
        Game replaced = start(20_261_152L, 2, GLFW_KEY_W);
        assertEquals(GameMode.SURVIVAL, replaced.gameMode());
        assertFalse(replaced.world.isUrban());
        assertArrayEquals(files[1], read(slots.backup(2, 1)), "the save it replaced is slot 2's newest backup");
        assertArrayEquals(files[0], read(slots.current(1)));
        assertArrayEquals(files[2], read(slots.current(3)));
        assertEquals(SaveAuditQaScene.durable(replaced), SaveAuditQaScene.durable(load(2)), "the Survival wilderness");
    }

    @Test
    void replacingAndDeletingAskFirstAndDeletingRemovesOnlyThatSlotsFiles() throws IOException {
        start(20_261_153L, 1);
        Game second = start(20_261_154L, 2, GLFW_KEY_W);
        assertTrue(second.saves.saveActive());
        Files.write(slots.legacySave(), new byte[]{'V', 'E', 'Y', 'L', 0, 0, 0, 3});
        byte[] one = read(slots.current(1)), two = read(slots.current(2));

        Game g = title();
        g.frontend.applyTitle(TitleScreen.Action.NEW_GAME);
        g.frontend.applyNewFrontier(NewFrontierScreen.Action.START);
        assertEquals(3, g.frontend.slotPicker.selected(), "the first empty slot");
        pick(g, GLFW_KEY_1);
        assertEquals(SlotPickerScreen.Action.NONE, pick(g, GLFW_KEY_ENTER));
        assertEquals(SlotPickerScreen.Ask.REPLACE, g.frontend.slotPicker.asking(), "an occupied slot asks first");
        g.frontend.applySlots(pick(g, GLFW_KEY_N));
        assertEquals(Game.AppState.TITLE_SLOTS, g.appState, "N keeps it, and nothing is built");
        assertNull(g.world);
        assertArrayEquals(one, read(slots.current(1)));

        g.frontend.applySlots(pick(g, GLFW_KEY_DELETE));
        assertEquals(SlotPickerScreen.Ask.DELETE, g.frontend.slotPicker.asking(), "a delete asks first");
        g.frontend.applySlots(pick(g, GLFW_KEY_N));
        assertArrayEquals(one, read(slots.current(1)), "N keeps it");
        g.frontend.applySlots(pick(g, GLFW_KEY_DELETE));
        g.frontend.applySlots(pick(g, GLFW_KEY_Y));
        assertEquals("Slot 1 deleted.", g.frontend.slotPicker.notice());
        assertEquals(SaveSlots.State.EMPTY, g.frontend.slotPicker.slots().getFirst().state(), "the picker shows it");
        for (Path file : slots.files(1)) {
            assertFalse(Files.exists(file), file + " is gone");
        }
        assertArrayEquals(two, read(slots.current(2)), "slot 2 is untouched");
        assertArrayEquals(new byte[]{'V', 'E', 'Y', 'L', 0, 0, 0, 3}, read(slots.legacySave()), "and so is 0.9.0's file");
    }

    @Test
    void aDamagedSlotIsRecoveredFromItsBackupOnlyAtThePlayersChoice() throws IOException {
        Game g = start(20_261_155L, 1);
        double firstClock = g.time.totalMinutes;
        g.time.totalMinutes += 600;
        assertTrue(g.saves.saveActive(), "a second save makes the first one the newest backup");
        byte[] backup = read(slots.backup(1, 1));
        byte[] damaged = read(slots.current(1));
        damaged[damaged.length / 2] ^= 0x5A;
        Files.write(slots.current(1), damaged);

        Game title = title();
        title.frontend.applyTitle(TitleScreen.Action.LOAD_GAME);
        SaveSlots.Slot shown = title.frontend.slotPicker.slots().getFirst();
        assertEquals(SaveSlots.State.DAMAGED, shown.state());
        assertEquals(1, shown.backup().generation(), "with its newest backup on offer");
        pick(title, GLFW_KEY_1);
        title.frontend.applySlots(pick(title, GLFW_KEY_ENTER));
        assertEquals(Game.AppState.TITLE_SLOTS, title.appState, "Enter does not load a damaged save");
        title.frontend.applySlots(pick(title, GLFW_KEY_R));
        assertEquals(SlotPickerScreen.Ask.RESTORE, title.frontend.slotPicker.asking());
        title.frontend.applySlots(pick(title, GLFW_KEY_N));
        assertArrayEquals(damaged, read(slots.current(1)), "a cancelled restore changes nothing");
        assertFalse(Files.exists(slots.setAside(1, 1)));

        title.frontend.applySlots(pick(title, GLFW_KEY_R));
        title.frontend.applySlots(pick(title, GLFW_KEY_Y));
        assertEquals("Restoring the backup...", title.frontend.loadingDetail());
        assertTrue(title.frontend.performLoad(), "the backup loads");
        assertEquals(Game.AppState.PLAYING, title.appState);
        assertEquals(1, title.saves.activeSlot());
        assertEquals(firstClock, title.time.totalMinutes, "the world of the first save");
        assertArrayEquals(backup, read(slots.current(1)), "the backup is the slot's save again");
        assertArrayEquals(damaged, read(slots.setAside(1, 1)), "the damaged save is set aside, not deleted");
        assertArrayEquals(backup, read(slots.backup(1, 1)), "and the backup stays");
    }

    @Test
    void f5AndF9UseTheActiveSlotWhichThePauseMenuNames() throws IOException {
        start(20_261_156L, 1);
        start(20_261_157L, 2);
        byte[] one = read(slots.current(1));
        Game g = load(2);
        assertTrue(PauseMenu.saveKeys(g.saves.activeSlot()).contains("Save to slot 2"));
        HotkeyRouter router = new HotkeyRouter(g);
        g.world.setBlock((int) g.player.pos.x + 2, (int) g.player.pos.y + 1, (int) g.player.pos.z + 2, BlockType.PLANK, true);
        byte[] before = read(slots.current(2));
        route(g, router, GLFW_KEY_F5);
        assertTrue(g.eventLog.recent(1).getFirst().endsWith("Saved to slot 2."), g.eventLog.recent(1).toString());
        assertFalse(java.util.Arrays.equals(before, read(slots.current(2))), "F5 wrote slot 2");
        assertArrayEquals(before, read(slots.backup(2, 1)), "and kept what it replaced");
        assertArrayEquals(one, read(slots.current(1)), "slot 1 is untouched");

        byte[] saved = read(slots.current(2));
        for (int i = 0; i <= ExplosionSystem.MAX_ACTIVE_FUSES; i++) {
            g.world.kegFuses.put(new Vec3i(i, 1, 0), 1f);
        }
        route(g, router, GLFW_KEY_F5);
        assertTrue(g.eventLog.recent(1).getFirst().endsWith("Could not save to slot 2."), "a failed save says so");
        assertArrayEquals(saved, read(slots.current(2)), "and leaves the slot as it was");
        g.world.kegFuses.clear();

        World live = g.world;
        route(g, router, GLFW_KEY_F9);
        assertTrue(g.eventLog.recent(1).getFirst().endsWith("Loaded slot 2."));
        assertNotSame(live, g.world, "F9 loaded the slot over the live world");
    }

    @Test
    void thePickerShowsEveryStateWithoutBuildingAWorld() throws IOException {
        Game first = start(20_261_158L, 1);
        assertTrue(first.saves.saveActive());
        Files.write(slots.current(2), ForeignSaves.asFormatThree(read(slots.current(1))));
        Files.copy(slots.current(1), slots.current(3));
        Files.copy(slots.backup(1, 1), slots.backup(3, 1));
        byte[] damaged = read(slots.current(3));
        damaged[damaged.length / 2] ^= 0x5A;
        Files.write(slots.current(3), damaged);
        Files.write(slots.legacySave(), new byte[]{1});

        for (TitleScreen.Action action : new TitleScreen.Action[]{TitleScreen.Action.LOAD_GAME, TitleScreen.Action.NEW_GAME}) {
            Game g = title();
            g.frontend.applyTitle(action);
            if (action == TitleScreen.Action.NEW_GAME) {
                g.frontend.applyNewFrontier(NewFrontierScreen.Action.START);
            }
            assertEquals(Game.AppState.TITLE_SLOTS, g.appState);
            assertEquals(List.of(SaveSlots.State.READY, SaveSlots.State.OTHER_VERSION, SaveSlots.State.DAMAGED),
                    g.frontend.slotPicker.slots().stream().map(SaveSlots.Slot::state).toList());
            assertNotNull(g.frontend.slotPicker.slots().get(2).backup(), "the damaged slot offers its backup");
            assertNull(g.world, "no world was built to show them");
            assertNull(g.player);
        }
        Files.delete(slots.current(1));
        Files.delete(slots.backup(1, 1));
        Game g = title();
        g.frontend.applyTitle(TitleScreen.Action.LOAD_GAME);
        assertEquals(SaveSlots.State.EMPTY, g.frontend.slotPicker.slots().getFirst().state());
    }

    // ------------------------------------------------------------------
    // The player's paths
    // ------------------------------------------------------------------

    private Game title() {
        Game g = new Game();
        g.saves.useSlots(slots);
        return g;
    }

    /**
     * NEW FRONTIER with these world and mode keys, then {@code slot} on the slot picker, confirming a
     * replacement when it asks: the path a player takes into a slot.
     */
    private Game start(long seed, int slot, int... keys) {
        Game g = title();
        g.sessionSeed = seed;
        g.frontend.applyTitle(TitleScreen.Action.NEW_GAME);
        for (int key : keys) {
            g.input.onKey(key, GLFW_PRESS);
            g.newFrontierScreen.handleKeys(g.input);
            g.input.endFrame();
            g.input.onKey(key, GLFW_RELEASE);
        }
        g.frontend.applyNewFrontier(NewFrontierScreen.Action.START);
        pick(g, GLFW_KEY_0 + slot);
        SlotPickerScreen.Action action = pick(g, GLFW_KEY_ENTER);
        if (g.frontend.slotPicker.asking() == SlotPickerScreen.Ask.REPLACE) {
            action = pick(g, GLFW_KEY_Y);
        }
        g.frontend.applySlots(action);
        assertTrue(g.frontend.performLoad(), "the new world starts");
        assertEquals(slot, g.saves.activeSlot(), "in the slot chosen");
        assertEquals(SaveSlots.State.READY, slots.describe(slot).state(), "which holds it at once");
        g.appState = Game.AppState.PLAYING;
        return g;
    }

    /** LOAD FRONTIER, {@code slot} and Enter. */
    private Game load(int slot) {
        Game g = title();
        g.frontend.applyTitle(TitleScreen.Action.LOAD_GAME);
        pick(g, GLFW_KEY_0 + slot);
        g.frontend.applySlots(pick(g, GLFW_KEY_ENTER));
        assertEquals(Game.AppState.LOADING, g.appState);
        assertTrue(g.frontend.performLoad(), "slot " + slot + " loads");
        assertEquals(slot, g.saves.activeSlot());
        return g;
    }

    private static SlotPickerScreen.Action pick(Game g, int key) {
        g.input.onKey(key, GLFW_PRESS);
        SlotPickerScreen.Action action = g.frontend.slotPicker.handleKeys(g.input);
        g.input.endFrame();
        g.input.onKey(key, GLFW_RELEASE);
        return action;
    }

    private static void route(Game g, HotkeyRouter router, int key) {
        g.input.onKey(key, GLFW_PRESS);
        router.update();
        g.input.endFrame();
        g.input.onKey(key, GLFW_RELEASE);
    }

    /** A block placed beside the survivor, so a save of the world differs from a new one. */
    private static void edit(Game g) {
        g.world.setBlock((int) g.player.pos.x + 2, (int) g.player.pos.y + 1, (int) g.player.pos.z + 2, BlockType.PLANK, true);
    }

    private static byte[] read(Path file) throws IOException {
        return Files.readAllBytes(file);
    }
}
