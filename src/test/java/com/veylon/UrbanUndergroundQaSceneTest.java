package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/** The {@code urban_underground} tour, played headlessly in {@link Game#run}'s frame order. */
class UrbanUndergroundQaSceneTest {
    private static final float DT = 1f / 60f;

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"SURVIVAL", "CREATIVE"})
    void theTourWalksEveryUndergroundAndRooftopRouteWithOrdinaryControls(GameMode mode) {
        Game game = new Game();
        game.newWorld(20260731, true, World.GEN_URBAN_UNDERGROUND, mode);
        game.appState = Game.AppState.PLAYING;
        UrbanQaScenes scenes = new UrbanQaScenes(game);
        HotkeyRouter router = new HotkeyRouter(game);
        scenes.stage("urban_underground");
        UrbanUndergroundQaScene tour = scenes.underground();
        assertTrue(game.world.changedBlocks.isEmpty(), "staging changes no block");
        double time = 0;
        while (!tour.reported()) {
            if (time > UrbanUndergroundQaScene.DEADLINE_SECONDS + 5) { fail("the tour never reported"); }
            scenes.update(time);
            if (game.appState != Game.AppState.DEATH && game.appState != Game.AppState.VICTORY) { router.update(); }
            game.playStep(DT, DT);
            game.world.ensureChunks((int) game.player.pos.x, (int) game.player.pos.z, game.renderer.renderRadius(), 2);
            game.input.endFrame();
            time += DT;
        }
        assertTrue(tour.passed());
        var cellar = tour.plan().cellar();
        assertSame(BlockType.HIDDEN_PANEL_OPEN, game.world.getBlock(cellar.panel().x(), cellar.panel().y(), cellar.panel().z()));
        assertTrue(game.world.urban.discovery.discovered(cellar.room()));
        assertTrue(game.world.urban.discovery.discovered(tour.plan().station().id()));
        assertTrue(game.eventLog.all().stream().anyMatch(s -> s.contains("A hidden cellar.")));
    }

    @Test
    void theTourNeedsTheUndergroundGenerator() {
        Game game = new Game();
        game.newWorld(7, true, World.GEN_URBAN_DISTRICTS, GameMode.SURVIVAL);
        assertThrows(IllegalStateException.class, () -> new UrbanQaScenes(game).stage("urban_underground"));
    }
}
