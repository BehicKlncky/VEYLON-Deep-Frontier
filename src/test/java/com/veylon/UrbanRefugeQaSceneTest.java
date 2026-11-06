package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.RefugeExposure;
import com.veylon.world.urban.UrbanRefuges;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/** The {@code urban_refuge} tour, played headlessly in {@link Game#run}'s frame order. */
class UrbanRefugeQaSceneTest {
    private static final float DT = 1f / 60f;

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"SURVIVAL", "CREATIVE"})
    void theTourSetsUpAHomeMeetsRealThreatsSleepsSafeAndIsRefusedTheCampsBed(GameMode mode) {
        Game game = new Game();
        game.newWorld(RefugeWorldArena.SEED, true, World.GEN_URBAN_REFUGES, mode);
        game.appState = Game.AppState.PLAYING;
        UrbanQaScenes scenes = new UrbanQaScenes(game);
        HotkeyRouter router = new HotkeyRouter(game);
        scenes.stage("urban_refuge");
        UrbanRefugeQaScene tour = scenes.refuge();
        assertTrue(game.world.changedBlocks.isEmpty(), "staging changes no block");
        double time = 0;
        while (!tour.reported()) {
            if (time > UrbanRefugeQaScene.DEADLINE_SECONDS + 5) { fail("the tour never reported"); }
            scenes.update(time);
            if (game.appState != Game.AppState.DEATH && game.appState != Game.AppState.VICTORY) { router.update(); }
            game.playStep(DT, DT);
            game.world.ensureChunks((int) game.player.pos.x, (int) game.player.pos.z, game.renderer.renderRadius(), 2);
            game.input.endFrame();
            time += DT;
        }
        assertTrue(tour.passed());
        UrbanRefuges refuges = game.world.urban.refuges;
        assertEquals(2, refuges.size(), "the starter refuge and the flat");
        assertEquals(tour.floor(), refuges.homeFloor(), "the flat is home");
        UrbanRefugeTourPlan plan = tour.plan();
        for (BuildingPlan.Fixture piece : plan.pieces()) {
            var p = piece.position();
            assertSame(BlockType.AIR, game.world.getBlock(p.x(), p.y(), p.z()), "salvaged for good: " + p);
        }
        RefugeExposure.Assessment a = RefugeExposure.assess(game.world, refuges.claim(tour.floor()));
        assertTrue(a.shutTight(), "left shut tight: " + a.ways());
        var leaf = plan.braced().position();
        BlockType expected = mode == GameMode.SURVIVAL ? BlockType.BARRICADE : BlockType.WOOD_DOOR;
        assertSame(expected, game.world.getBlock(leaf.x(), leaf.y(), leaf.z()),
                mode == GameMode.SURVIVAL ? "the breach is boarded up" : "nothing broke in on a Creative builder");
    }

    @Test
    void theTourNeedsTheRefugeGenerator() {
        Game game = new Game();
        game.newWorld(7, true, World.GEN_URBAN_STRONGHOLDS, GameMode.SURVIVAL);
        assertThrows(IllegalStateException.class, () -> new UrbanQaScenes(game).stage("urban_refuge"));
    }
}
