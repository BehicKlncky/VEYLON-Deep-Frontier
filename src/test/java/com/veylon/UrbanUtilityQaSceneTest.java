package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.item.ItemType;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.UrbanUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/** The {@code urban_utilities} tour, played headlessly in {@link Game#run}'s frame order. */
class UrbanUtilityQaSceneTest {
    private static final float DT = 1f / 60f;

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"SURVIVAL", "CREATIVE"})
    void theTourPowersLightsAndWatersTheRefugeRunsItDryAndRebuildsIt(GameMode mode) {
        Game game = new Game();
        game.newWorld(UtilityWorldArena.SEED, true, World.GEN_URBAN_UTILITIES, mode);
        game.appState = Game.AppState.PLAYING;
        UrbanQaScenes scenes = new UrbanQaScenes(game);
        HotkeyRouter router = new HotkeyRouter(game);
        scenes.stage("urban_utilities");
        UrbanUtilityQaScene tour = scenes.utilities();
        assertTrue(game.world.changedBlocks.isEmpty(), "staging changes no block");
        double time = 0;
        while (!tour.reported()) {
            if (time > UrbanUtilityQaScene.DEADLINE_SECONDS + 5) { fail("the tour never reported"); }
            scenes.update(time);
            if (game.appState != Game.AppState.DEATH && game.appState != Game.AppState.VICTORY) { router.update(); }
            game.playStep(DT, DT);
            game.world.ensureChunks((int) game.player.pos.x, (int) game.player.pos.z, game.renderer.renderRadius(), 2);
            game.input.endFrame();
            time += DT;
        }
        assertTrue(tour.passed());
        UrbanUtilityTourPlan plan = tour.plan();
        UrbanUtilities u = game.world.urban.utilities;
        assertEquals(4, u.deviceCount());
        var generator = u.device(plan.generator());
        assertEquals(0f, generator.fuel, "run dry");
        assertFalse(generator.on);
        assertTrue(generator.condition > 90f, "and rebuilt");
        assertTrue(u.device(plan.purifier()).condition > 0, "its filter still works");
        assertTrue(game.player.inventory.count(ItemType.WATERSKIN_CLEAN) >= 1, "clean water carried");
        assertTrue(u.drawn(plan.refuge().id()) >= UrbanUtilityQaScene.TAP_LITRES, "the pipes are lower for good");
        assertSame(BlockType.AIR, game.world.getBlock(plan.tank().x(), plan.tank().y(), plan.tank().z()), "the tank is gone");
    }

    @Test
    void theTourNeedsTheUtilityGenerator() {
        Game game = new Game();
        game.newWorld(7, true, World.GEN_URBAN_REFUGES, GameMode.SURVIVAL);
        assertThrows(IllegalStateException.class, () -> new UrbanQaScenes(game).stage("urban_utilities"));
    }
}
