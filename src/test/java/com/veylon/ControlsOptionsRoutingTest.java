package com.veylon;

import com.veylon.input.ControlSettings;
import com.veylon.save.SaveSlots;
import com.veylon.ui.ControlsOptionsScreen;
import com.veylon.ui.TitleScreen;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Where the mouse settings open and how they last (0.9.1 milestone 06): CONTROLS on the title, and
 * [C] in the pause menu over the paused world, whose screen then owns Escape and F5; APPLY writes
 * them for the next launch and BACK keeps the file as it was.
 */
class ControlsOptionsRoutingTest {

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
    void theTitlesControlsEntryOpensTheScreenWithNoWorld() {
        Game g = new Game();
        g.frontend.applyTitle(TitleScreen.Action.CONTROLS);
        assertEquals(Game.AppState.TITLE_CONTROLS_OPTIONS, g.appState);
        assertTrue(g.frontend.owns(g.appState), "a worldless state the frontend draws");
        assertFalse(g.appState.rendersWorld());
        assertNull(g.world);
    }

    @Test
    void cInThePauseMenuOpensThemOverThePausedWorldAndChangesNothingOnTheWay() {
        Game g = new Game();
        g.saves.useSlots(new SaveSlots(dir.resolve("saves")));
        g.newWorld(20_261_120L, true, World.WILDERNESS_NEW_WORLD_GENERATOR);
        g.appState = Game.AppState.PLAYING;
        g.uiMode = Game.UiMode.PAUSE;
        press(g, GLFW_KEY_C);
        assertEquals(Game.UiMode.CONTROLS_OPTIONS, g.uiMode, "C opens the controls, not the crafting screen");
        assertFalse(g.simulates(), "the world stays paused under it");
        release(g, GLFW_KEY_C);

        press(g, GLFW_KEY_F5);
        release(g, GLFW_KEY_F5);
        assertFalse(Files.exists(dir.resolve("saves")), "F5 is the screen's APPLY, not a quick save");
        press(g, GLFW_KEY_ESCAPE);
        assertEquals(Game.UiMode.CONTROLS_OPTIONS, g.uiMode, "Escape is the screen's own BACK");
        g.frontend.applyPauseControls(ControlsOptionsScreen.Action.CANCEL);
        assertEquals(Game.UiMode.PAUSE, g.uiMode, "and BACK returns to the pause menu");
    }

    @Test
    void applyWritesTheSettingsForTheNextLaunchAndBackLeavesTheFileAlone() throws IOException {
        Path file = ControlSettings.file();
        Files.deleteIfExists(file);
        try {
            Game g = new Game();
            assertEquals(ControlSettings.DEFAULT_SENSITIVITY, g.controls.sensitivity(), "a game that never ran reads no file");
            g.frontend.applyTitle(TitleScreen.Action.CONTROLS);
            for (int i = 0; i < 7; i++) {
                g.controls.stepSensitivity(1); // what Right does on the screen's first row
            }
            g.controls.invertY = true; // and Enter on its second
            g.frontend.applyTitleControls(ControlsOptionsScreen.Action.APPLY);
            assertEquals(Game.AppState.TITLE, g.appState);
            assertEquals("Control settings applied.", g.titleScreen.notice());

            ControlSettings relaunched = ControlSettings.loadOrDefaults(); // what Game.run reads at launch
            assertEquals(170, relaunched.sensitivity(), "the sensitivity survives a relaunch");
            assertTrue(relaunched.invertY, "and so does invert-Y");

            byte[] written = Files.readAllBytes(file);
            g.frontend.applyTitle(TitleScreen.Action.CONTROLS);
            g.controls.invertY = false;
            g.frontend.applyTitleControls(ControlsOptionsScreen.Action.CANCEL);
            assertArrayEquals(written, Files.readAllBytes(file), "BACK writes nothing");
            assertEquals(Game.AppState.TITLE, g.appState);
        } finally {
            Files.deleteIfExists(file); // later tests in this worker start from the defaults
        }
    }
}
