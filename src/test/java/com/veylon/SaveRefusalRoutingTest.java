package com.veylon;

import com.veylon.engine.Input;
import com.veylon.save.ForeignSaves;
import com.veylon.save.LoadResult;
import com.veylon.save.SaveSlots;
import com.veylon.save.SaveSystem;
import com.veylon.ui.SlotPickerScreen;
import com.veylon.ui.TitleScreen;
import com.veylon.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_2;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F9;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;

/**
 * 0.9.1 milestones 03 and 04: where a player meets a refused save. LOAD FRONTIER's slot picker shows
 * a slot of another version as such and says why under the cards when it is chosen, and F9 keeps
 * the live world and says why in a log line short enough for the HUD; neither touches the file.
 * The saves are VEYLON 0.9.0's header put on a save this build wrote.
 */
class SaveRefusalRoutingTest {

    @TempDir
    Path directory;

    private SaveSlots slots;

    @BeforeEach
    void aFolderOfSlots() throws Exception {
        slots = new SaveSlots(directory.resolve("slots"));
        Files.createDirectories(slots.directory());
    }

    @Test
    void loadFrontierSaysAnOlderSaveCannotBeOpenedAndLeavesIt() throws Exception {
        byte[] older = ForeignSaves.asFormatThree(saveOf(20_261_119L));
        Files.write(slots.current(2), older);
        Game game = new Game();
        game.saves.useSlots(slots);

        game.frontend.applyTitle(TitleScreen.Action.LOAD_GAME);
        assertEquals(Game.AppState.TITLE_SLOTS, game.appState, "LOAD FRONTIER opens the slot picker");
        assertEquals(SaveSlots.State.OTHER_VERSION, game.frontend.slotPicker.slots().get(1).state(),
                "the slot shows as another version before anything is loaded");
        assertEquals(SlotPickerScreen.Action.NONE, press(game, GLFW_KEY_2, GLFW_KEY_ENTER), "nothing is loaded");
        assertEquals(Game.AppState.TITLE_SLOTS, game.appState);
        assertNull(game.world, "and no world was built");
        assertEquals("This save was written by an older version of VEYLON. Start a new world.",
                game.frontend.slotPicker.notice());
        assertArrayEquals(older, Files.readAllBytes(slots.current(2)), "the file is untouched");
    }

    @Test
    void f9SaysAnOlderSaveCannotBeOpenedAndKeepsTheLiveWorld() throws Exception {
        byte[] older = ForeignSaves.asFormatThree(saveOf(20_261_120L));
        Files.write(slots.current(1), older);
        Game game = new Game();
        game.saves.useSlots(slots);
        game.newWorld(20_261_121L, true, World.GEN_CAVE_IDENTITIES);
        game.appState = Game.AppState.PLAYING;
        World live = game.world;
        var player = game.player;

        game.input.onKey(GLFW_KEY_F9, GLFW_PRESS);
        new HotkeyRouter(game).update();

        assertSame(live, game.world, "the live world is kept");
        assertSame(player, game.player);
        List<String> log = game.eventLog.recent(1);
        assertTrue(log.getFirst().endsWith("Save is from an older version."), log.toString());
        assertArrayEquals(older, Files.readAllBytes(slots.current(1)), "the file is untouched");
    }

    /**
     * The HUD's log panel cuts a longer line short; the native capture of 0.9.1 milestone 03 showed
     * the title's sentence cut there, so F5 and F9 report short lines that name the slot.
     */
    @Test
    void everyLogLineFitsTheLogPanel() {
        for (LoadResult.Outcome outcome : LoadResult.Outcome.values()) {
            String line = new LoadResult(outcome, "").logLine();
            assertTrue(!line.isBlank() && line.length() <= LoadResult.LOG_LINE_LIMIT, outcome + ": " + line);
            String f9 = SaveSession.notLoadedLine(SaveSlots.COUNT, new LoadResult(outcome, ""));
            assertTrue(f9.length() <= LoadResult.LOG_LINE_LIMIT, outcome + ": " + f9);
        }
        for (String line : List.of(SaveSession.savedLine(SaveSlots.COUNT), SaveSession.saveFailedLine(SaveSlots.COUNT),
                SaveSession.loadedLine(SaveSlots.COUNT))) {
            assertTrue(line.length() <= LoadResult.LOG_LINE_LIMIT, line);
        }
    }

    @Test
    void f9OnAnEmptySlotSaysSo() {
        Game game = new Game();
        game.saves.useSlots(slots);
        game.newWorld(20_261_122L, true, World.GEN_CAVE_IDENTITIES);
        game.appState = Game.AppState.PLAYING;
        game.input.onKey(GLFW_KEY_F9, GLFW_PRESS);
        new HotkeyRouter(game).update();
        assertTrue(game.eventLog.recent(1).getFirst().endsWith("Slot 1 is empty."));
    }

    private static SlotPickerScreen.Action press(Game game, int... keys) throws ReflectiveOperationException {
        SlotPickerScreen.Action action = SlotPickerScreen.Action.NONE;
        for (int key : keys) {
            var field = Input.class.getDeclaredField("keyPressed");
            field.setAccessible(true);
            ((boolean[]) field.get(game.input))[key] = true;
            action = game.frontend.slotPicker.handleKeys(game.input);
            game.input.endFrame();
        }
        return action;
    }

    private byte[] saveOf(long seed) throws Exception {
        Game game = new Game();
        game.newWorld(seed, true, World.GEN_CAVE_IDENTITIES);
        Path file = directory.resolve("written-" + seed + ".sav");
        assertTrue(SaveSystem.save(game, file));
        byte[] bytes = Files.readAllBytes(file);
        Files.delete(file);
        return bytes;
    }
}
