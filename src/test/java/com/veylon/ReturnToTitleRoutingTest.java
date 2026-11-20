package com.veylon;

import com.veylon.combat.ExplosionSystem;
import com.veylon.save.SaveSlots;
import com.veylon.ui.ReturnToTitlePrompt;
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
 * The pause menu's way back to the title (0.9.1 milestone 06, D-04) through the hotkey router and
 * the frontend controller, with real files in a folder of slots of the test's own: R asks over the
 * paused world; Save and return writes the world's slot and only then returns; Return without saving
 * leaves the slot byte for byte; Cancel goes back to the menu; a save that fails keeps the world.
 */
class ReturnToTitleRoutingTest {

    private static final long SEED = 20_261_120L;

    @TempDir
    Path directory;

    private SaveSlots slots;

    @BeforeEach
    void aFolderOfSlots() {
        slots = new SaveSlots(directory.resolve("slots"));
    }

    @Test
    void rInThePauseMenuAsksOverThePausedWorldAndEscapeGoesBackToTheMenu() throws IOException {
        Game g = playing();
        byte[] saved = read(slots.current(1));
        g.saves.countPlay(125);
        g.saves.autosave().stageElapsed(125);
        HotkeyRouter router = new HotkeyRouter(g);
        route(g, router, GLFW_KEY_R);
        assertEquals(Game.UiMode.NONE, g.uiMode, "during play R is the reload key, not the way out");
        route(g, router, GLFW_KEY_ESCAPE);
        assertEquals(Game.UiMode.PAUSE, g.uiMode);
        route(g, router, GLFW_KEY_R);
        assertEquals(Game.UiMode.RETURN_TO_TITLE, g.uiMode, "R in the pause menu asks");
        assertFalse(g.simulates(), "the world stays paused under the question");
        assertEquals("Since slot 1 was last saved: 2 min of play.", g.frontend.returnPrompt.unsavedLine());

        route(g, router, GLFW_KEY_F5);
        assertArrayEquals(saved, read(slots.current(1)), "F5 is not a quick save while it asks");
        assertEquals(ReturnToTitlePrompt.Action.CANCEL, answer(g, GLFW_KEY_ESCAPE));
        assertEquals(Game.UiMode.PAUSE, g.uiMode, "Escape goes back to the pause menu");
        assertEquals(Game.AppState.PLAYING, g.appState);
        assertNotNull(g.world);
        route(g, router, GLFW_KEY_ESCAPE);
        assertEquals(Game.UiMode.NONE, g.uiMode, "and the next Escape resumes");
    }

    @Test
    void saveAndReturnWritesTheSlotThenReturnsAndTheTitleSaysSo() throws IOException {
        Game g = playing();
        byte[] first = read(slots.current(1));
        edit(g);
        List<String> edited = SaveAuditQaScene.durable(g);
        ask(g);
        assertEquals(ReturnToTitlePrompt.Action.SAVE_AND_RETURN, answer(g, GLFW_KEY_ENTER), "Enter keeps the progress");
        assertEquals(Game.AppState.TITLE, g.appState);
        assertNull(g.world, "the world is let go once it is saved");
        assertEquals("Saved to slot 1.", g.titleScreen.notice());
        assertArrayEquals(first, read(slots.backup(1, 1)), "the save it replaced is the newest backup, as with F5");

        g.frontend.applyTitle(TitleScreen.Action.LOAD_GAME);
        g.frontend.applySlots(pick(g, GLFW_KEY_ENTER));
        assertTrue(g.frontend.performLoad());
        assertEquals(edited, SaveAuditQaScene.durable(g), "the slot holds the world as it was left");
    }

    @Test
    void returnWithoutSavingLeavesTheSlotAsItWas() throws IOException {
        Game g = playing();
        assertTrue(g.saves.saveActive(), "a second save, so the slot has a backup to keep too");
        byte[] current = read(slots.current(1)), backup = read(slots.backup(1, 1));
        List<String> saved = SaveAuditQaScene.durable(g);
        edit(g);
        ask(g);
        assertEquals(ReturnToTitlePrompt.Action.RETURN, answer(g, GLFW_KEY_N));
        assertEquals(Game.AppState.TITLE, g.appState);
        assertNull(g.world);
        assertEquals("Slot 1 keeps its last save.", g.titleScreen.notice());
        assertArrayEquals(current, read(slots.current(1)), "the slot is byte for byte what it was");
        assertArrayEquals(backup, read(slots.backup(1, 1)), "and so is its backup");
        assertFalse(Files.exists(slots.backup(1, 2)));

        g.frontend.applyTitle(TitleScreen.Action.LOAD_GAME);
        g.frontend.applySlots(pick(g, GLFW_KEY_ENTER));
        assertTrue(g.frontend.performLoad());
        assertEquals(saved, SaveAuditQaScene.durable(g), "the edit after the last save is gone");
    }

    @Test
    void aSaveThatFailsKeepsTheWorldAndThePromptOpen() throws IOException {
        Game g = playing();
        byte[] saved = read(slots.current(1));
        World live = g.world;
        for (int i = 0; i <= ExplosionSystem.MAX_ACTIVE_FUSES; i++) {
            g.world.kegFuses.put(new Vec3i(i, 1, 0), 1f); // more lit kegs than a save may hold
        }
        ask(g);
        answer(g, GLFW_KEY_ENTER);
        assertEquals(Game.AppState.PLAYING, g.appState, "nothing is let go");
        assertSame(live, g.world);
        assertEquals(Game.UiMode.RETURN_TO_TITLE, g.uiMode, "the question stays");
        assertEquals("Could not save to slot 1. The world is still open.", g.frontend.returnPrompt.notice());
        assertTrue(g.eventLog.recent(1).getFirst().endsWith("Could not save to slot 1."));
        assertArrayEquals(saved, read(slots.current(1)), "and the slot keeps its save");

        g.world.kegFuses.clear();
        answer(g, GLFW_KEY_N);
        assertEquals(Game.AppState.TITLE, g.appState, "Return without saving still leaves");
        assertArrayEquals(saved, read(slots.current(1)));
    }

    @Test
    void aWorldInNoSlotSaysWhereSavingPutsIt() {
        Game g = new Game();
        g.saves.useSlots(slots);
        g.newWorld(SEED, true, World.WILDERNESS_NEW_WORLD_GENERATOR); // as a QA scene builds one
        g.appState = Game.AppState.PLAYING;
        ask(g);
        assertEquals("This world is in no slot yet: saving puts it in slot 1.", g.frontend.returnPrompt.unsavedLine());
        answer(g, GLFW_KEY_N);
        assertEquals("", g.titleScreen.notice(), "nothing was in a slot to keep");
        assertEquals(SaveSlots.State.EMPTY, slots.describe(1).state(), "and nothing was written");
    }

    // ------------------------------------------------------------------
    // The player's paths
    // ------------------------------------------------------------------

    /** A new city from NEW FRONTIER in slot 1, saved there at once, and playing. */
    private Game playing() {
        Game g = new Game();
        g.saves.useSlots(slots);
        g.frontend.seeds = NewWorldSeeds.fixed(SEED);
        g.frontend.applyTitle(TitleScreen.Action.NEW_GAME);
        g.frontend.applyNewFrontier(com.veylon.ui.NewFrontierScreen.Action.START);
        g.frontend.applySlots(pick(g, GLFW_KEY_ENTER));
        assertTrue(g.frontend.performLoad());
        assertEquals(1, g.saves.activeSlot());
        assertEquals(SaveSlots.State.READY, slots.describe(1).state());
        g.appState = Game.AppState.PLAYING;
        return g;
    }

    /** Escape, then R: the question, over the paused world. */
    private static void ask(Game g) {
        HotkeyRouter router = new HotkeyRouter(g);
        g.uiMode = Game.UiMode.NONE;
        route(g, router, GLFW_KEY_ESCAPE);
        route(g, router, GLFW_KEY_R);
        assertEquals(Game.UiMode.RETURN_TO_TITLE, g.uiMode);
    }

    /** {@code key} on the open question, applied as the frame applies it. */
    private static ReturnToTitlePrompt.Action answer(Game g, int key) {
        g.input.onKey(key, GLFW_PRESS);
        ReturnToTitlePrompt.Action action = g.frontend.returnPrompt.handleKeys(g.input);
        g.frontend.applyReturnPrompt(action);
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

    private static SlotPickerScreen.Action pick(Game g, int key) {
        g.input.onKey(key, GLFW_PRESS);
        SlotPickerScreen.Action action = g.frontend.slotPicker.handleKeys(g.input);
        g.input.endFrame();
        g.input.onKey(key, GLFW_RELEASE);
        return action;
    }

    /** A plank beside the survivor, so the world differs from its save. */
    private static void edit(Game g) {
        g.world.setBlock((int) g.player.pos.x + 2, (int) g.player.pos.y + 1, (int) g.player.pos.z + 2, BlockType.PLANK, true);
    }

    private static byte[] read(Path file) throws IOException {
        return Files.readAllBytes(file);
    }
}
