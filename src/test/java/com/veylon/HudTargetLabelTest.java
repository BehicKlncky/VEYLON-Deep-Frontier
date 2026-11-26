package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.ui.Hud;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * 0.9.1 B06: the HUD names the block aimed at only while the world is in view. The death and
 * victory cards and the pause menu draw translucent backdrops over the HUD, and the label showed
 * through them, naming whatever was aimed at when they opened, since none of them updates the aim.
 */
class HudTargetLabelTest {

    private static final long SEED = 20_261_126L;
    private static final float DT = 1f / 20f;

    private static void press(Game g, int key) {
        g.input.onKey(key, GLFW_PRESS);
        new HotkeyRouter(g).update();
    }

    private static void release(Game g, int key) {
        g.input.onKey(key, GLFW_RELEASE);
        g.input.endFrame();
    }

    private static void play(Game g, float seconds) {
        for (float t = 0; t < seconds; t += DT) {
            g.playStep(DT, DT);
        }
    }

    /** A survivor on their feet in the wilderness, aiming level at a stone two blocks north. */
    private static Game aimingAtStone(GameMode mode) {
        Game g = new Game();
        g.newWorld(SEED, true, World.WILDERNESS_NEW_WORLD_GENERATOR, mode);
        g.appState = Game.AppState.PLAYING;
        play(g, 1f);
        assertTrue(g.player.onGround, "the survivor stands");
        g.player.pos.x = (float) Math.floor(g.player.pos.x) + 0.5f;
        g.player.pos.z = (float) Math.floor(g.player.pos.z) + 0.5f;
        int x = (int) Math.floor(g.player.pos.x);
        int eye = (int) Math.floor(g.player.pos.y + g.player.eyeHeight());
        int z = (int) Math.floor(g.player.pos.z);
        g.world.setBlock(x, eye, z - 1, BlockType.AIR, false);
        g.world.setBlock(x, eye, z - 2, BlockType.STONE, false);
        g.camera.yaw = 0f;
        g.camera.pitch = 0f;
        play(g, 0.2f);
        assertNotNull(g.targetHit, "the aim finds the stone");
        assertEquals(BlockType.STONE, g.targetHit.type());
        assertTrue(Hud.showsTargetLabel(g), "the label names it in play");
        return g;
    }

    @Test
    void theDeathCardHidesTheLabelOfTheBlockAimedAtWhenTheSurvivorDied() {
        Game g = aimingAtStone(GameMode.SURVIVAL);
        // The native route's kill, through the player's damage gate.
        g.player.hurt(1000f, false);
        g.playStep(DT, DT);
        assertTrue(g.showsEndCard(), "the death card is up");
        assertNotNull(g.targetHit, "the aim of the moment of death is still recorded");
        assertFalse(Hud.showsTargetLabel(g), "but its label is not drawn under the card");
        play(g, 1f);
        assertFalse(Hud.showsTargetLabel(g), "for as long as the card shows");
    }

    @Test
    void theVictoryCardHidesItAndPlayingOnShowsItAgain() {
        Game g = aimingAtStone(GameMode.SURVIVAL);
        g.appState = Game.AppState.VICTORY; // as the beacon and the city's answer set it
        g.playStep(DT, DT);
        assertTrue(g.showsEndCard());
        assertFalse(Hud.showsTargetLabel(g));
        g.appState = Game.AppState.PLAYING; // Enter on the card plays on
        play(g, 0.2f);
        assertTrue(Hud.showsTargetLabel(g), "in play the label is back");
    }

    @Test
    void thePauseMenuAndItsScreensHideItInBothModes() {
        for (GameMode mode : GameMode.values()) {
            Game g = aimingAtStone(mode);
            press(g, GLFW_KEY_ESCAPE);
            release(g, GLFW_KEY_ESCAPE);
            assertEquals(Game.UiMode.PAUSE, g.uiMode, mode + ": Escape opens the pause menu");
            play(g, 0.5f);
            assertNotNull(g.targetHit);
            assertFalse(Hud.showsTargetLabel(g), mode + ": not under the pause menu");
            press(g, GLFW_KEY_O);
            release(g, GLFW_KEY_O);
            assertEquals(Game.UiMode.OPTIONS, g.uiMode, mode + ": O opens the graphics options");
            assertFalse(Hud.showsTargetLabel(g), mode + ": nor under a screen opened from it");
            g.closeScreens();
            play(g, 0.2f);
            assertTrue(Hud.showsTargetLabel(g), mode + ": back in play it shows again");
        }
    }
}
