package com.veylon;

import com.veylon.save.SaveSlots;
import com.veylon.ui.TitleScreen;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Where the autosave options open (0.9.1 milestone 05): AUTOSAVE on the title, and [A] in the pause
 * menu over the paused world, whose screen then owns Escape and F5.
 */
class AutosaveOptionsRoutingTest {

    @TempDir
    Path dir;

    private static void press(Game g, int key) {
        g.input.onKey(key, GLFW_PRESS);
        new HotkeyRouter(g).update();
    }

    private static void release(Game g, int key) {
        g.input.onKey(key, GLFW_RELEASE);
        g.input.endFrame();
    }

    @Test
    void theTitlesAutosaveEntryOpensTheScreenWithNoWorld() {
        Game g = new Game();
        g.frontend.applyTitle(TitleScreen.Action.AUTOSAVE);
        assertEquals(Game.AppState.TITLE_AUTOSAVE_OPTIONS, g.appState);
        assertTrue(g.frontend.owns(g.appState), "a worldless state the frontend draws");
        assertFalse(g.appState.rendersWorld());
    }

    @Test
    void aInThePauseMenuOpensThemOverThePausedWorldAndChangesNothingOnTheWay() {
        Game g = new Game();
        g.saves.useSlots(new SaveSlots(dir.resolve("saves")));
        g.newWorld(20_261_120L, true, World.WILDERNESS_NEW_WORLD_GENERATOR);
        g.appState = Game.AppState.PLAYING;
        g.uiMode = Game.UiMode.PAUSE;
        press(g, GLFW_KEY_A);
        assertEquals(Game.UiMode.AUTOSAVE_OPTIONS, g.uiMode);
        assertFalse(g.input.wasKeyPressed(GLFW_KEY_A), "the press that opened it is not read again as \"less\"");
        assertTrue(g.saves.autosave().settings().enabled);
        assertFalse(g.simulates(), "the world stays paused under it");
        release(g, GLFW_KEY_A);

        press(g, GLFW_KEY_F5);
        release(g, GLFW_KEY_F5);
        assertFalse(Files.exists(dir.resolve("saves")), "F5 is the screen's APPLY, not a quick save");
        press(g, GLFW_KEY_ESCAPE);
        assertEquals(Game.UiMode.AUTOSAVE_OPTIONS, g.uiMode, "Escape is the screen's own BACK");
    }
}
