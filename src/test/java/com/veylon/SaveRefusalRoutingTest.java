package com.veylon;

import com.veylon.save.ForeignSaves;
import com.veylon.save.LoadResult;
import com.veylon.save.SaveSystem;
import com.veylon.ui.TitleScreen;
import com.veylon.world.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F9;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;

/**
 * 0.9.1 milestone 03: where a player meets a refused save. LOAD FRONTIER returns to the title with
 * the reason under the menu, and F9 keeps the live world and says why in a log line short enough
 * for the HUD; neither touches the file. The saves are VEYLON 0.9.0's header put on a save this
 * build wrote.
 */
class SaveRefusalRoutingTest {

    @TempDir
    Path directory;

    @AfterEach
    void emptyTheSlot() throws Exception {
        Files.deleteIfExists(SaveSystem.SAVE_PATH);
    }

    @Test
    void loadFrontierSaysAnOlderSaveCannotBeOpenedAndLeavesIt() throws Exception {
        byte[] older = ForeignSaves.asFormatThree(saveOf(20_261_119L));
        Game game = new Game();
        game.frontend.savePath = Files.write(directory.resolve("slot.sav"), older);

        game.frontend.applyTitle(TitleScreen.Action.LOAD_GAME);
        assertEquals(Game.AppState.LOADING, game.appState);
        assertFalse(game.frontend.performLoad(), "an older save does not load");
        assertEquals(Game.AppState.TITLE, game.appState, "the player is back at the title");
        assertNull(game.world, "and no world was built");
        assertEquals("This save was written by an older version of VEYLON. Start a new world.",
                game.titleScreen.notice());
        assertArrayEquals(older, Files.readAllBytes(game.frontend.savePath), "the file is untouched");

        game.frontend.applyTitle(TitleScreen.Action.NEW_GAME);
        assertTrue(game.newFrontierScreen.showsReplaceNotice(), "NEW FRONTIER still warns about the file");
    }

    @Test
    void f9SaysAnOlderSaveCannotBeOpenedAndKeepsTheLiveWorld() throws Exception {
        byte[] older = ForeignSaves.asFormatThree(saveOf(20_261_120L));
        Files.createDirectories(SaveSystem.SAVE_PATH.getParent());
        Files.write(SaveSystem.SAVE_PATH, older);
        Game game = new Game();
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
        assertArrayEquals(older, Files.readAllBytes(SaveSystem.SAVE_PATH), "the file is untouched");
    }

    /**
     * The HUD's log panel cuts a longer line short; the native capture of 0.9.1 milestone 03 showed
     * the title's sentence cut there, so F9 reports the short line.
     */
    @Test
    void everyLogLineFitsTheLogPanel() {
        for (LoadResult.Outcome outcome : LoadResult.Outcome.values()) {
            String line = new LoadResult(outcome, "").logLine();
            assertTrue(!line.isBlank() && line.length() <= LoadResult.LOG_LINE_LIMIT, outcome + ": " + line);
        }
    }

    @Test
    void f9WithNoSaveSaysSo() {
        Game game = new Game();
        game.newWorld(20_261_122L, true, World.GEN_CAVE_IDENTITIES);
        game.appState = Game.AppState.PLAYING;
        game.input.onKey(GLFW_KEY_F9, GLFW_PRESS);
        new HotkeyRouter(game).update();
        assertTrue(game.eventLog.recent(1).getFirst().endsWith("No save found."));
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
