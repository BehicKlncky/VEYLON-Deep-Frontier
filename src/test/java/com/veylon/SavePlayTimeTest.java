package com.veylon;

import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 0.9.1 milestone 04: a save's summary names how long its world has been played. Only frames that
 * advance the world count, so a paused game or an open pause menu adds nothing.
 */
class SavePlayTimeTest {

    @Test
    void onlyFramesThatAdvanceTheWorldCountAsPlay() {
        Game game = new Game();
        game.newWorld(20_261_130L, true, World.GEN_CAVE_IDENTITIES);
        game.appState = Game.AppState.PLAYING;
        assertEquals(0, game.saves.playSeconds(), "a new world is unplayed");

        game.playStep(0.25f, 0.25);
        game.playStep(0.25f, 0.25);
        assertEquals(0.5, game.saves.playSeconds(), 1e-9, "two played frames");

        game.uiMode = Game.UiMode.PAUSE;
        game.playStep(0.25f, 0.25);
        game.uiMode = Game.UiMode.NONE;
        game.simPaused = true;
        game.playStep(0.25f, 0.25);
        game.simPaused = false;
        assertEquals(0.5, game.saves.playSeconds(), 1e-9, "the pause menu and P add nothing");

        game.playStep(0.25f, 0.25);
        assertEquals(0.75, game.saves.playSeconds(), 1e-9);
    }
}
