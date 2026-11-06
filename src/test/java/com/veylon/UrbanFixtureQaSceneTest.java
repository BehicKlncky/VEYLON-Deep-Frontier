package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code urban_fixtures} QA timeline run headless with synthetic seconds:
 * its production interactions happen and its walk in through the front door
 * and up the stairs passes, so a native capture shows real behaviour.
 */
class UrbanFixtureQaSceneTest {

    @Test
    void theDayTimelineOpensShattersClosesAndWalksToTheRoof() {
        Game game = new Game();
        game.newWorld(UrbanFixtureArena.SEED, true, World.GEN_URBAN_STREETS, GameMode.SURVIVAL);
        UrbanFixtureQaScene scene = new UrbanFixtureQaScene(game);
        scene.stage("urban_fixtures");
        for (double t = 0; t < 34 && !scene.reported(); t += 1 / 60.0) {
            scene.update(t);
        }
        assertTrue(scene.reported());
        assertTrue(scene.passed(), "in through the front door and up the stairs");
        int[] spawn = game.world.urban.spawnColumn();
        int x0 = spawn[0] + 6, z0 = spawn[1] + 10;
        assertSame(BlockType.METAL_DOOR_OPEN, game.world.getBlock(x0 + 4, 41, z0), "reopened for the walk");
        assertSame(BlockType.WINDOW_OPEN, game.world.getBlock(x0 + 9, 42, z0));
        assertSame(BlockType.BROKEN_WINDOW, game.world.getBlock(x0 + 10, 42, z0));
        assertSame(BlockType.BROKEN_WINDOW, game.world.getBlock(x0 + 10, 43, z0));
        assertEquals(2, game.world.changedBlocks.size(),
                "staging writes no saved edits; only the two production pane breaks are recorded");
        assertEquals(BlockType.BROKEN_WINDOW.id(), game.world.changedBlocks.get(
                new com.veylon.util.Vec3i(x0 + 10, 42, z0)));
    }

    @Test
    void theNightVariantPinsTheHourAndLightsTheRooms() {
        Game game = new Game();
        game.newWorld(UrbanFixtureArena.SEED, true, World.GEN_URBAN_STREETS, GameMode.SURVIVAL);
        UrbanFixtureQaScene scene = new UrbanFixtureQaScene(game);
        scene.stage("urban_fixtures_night");
        for (double t = 0; t < 14; t += 1 / 60.0) {
            scene.update(t);
        }
        assertEquals(23 * 60.0, game.time.totalMinutes, 1e-6);
        int[] spawn = game.world.urban.spawnColumn();
        int x0 = spawn[0] + 6, z0 = spawn[1] + 10;
        assertTrue(game.world.isLanternLit(x0 + 10, 42, z0 + 3));
        assertTrue(game.world.isLanternLit(x0 + 1, 41, z0 + 1));
    }
}
