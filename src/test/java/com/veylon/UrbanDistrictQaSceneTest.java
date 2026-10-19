package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/** The {@code urban_districts} tour, played headlessly in {@link Game#run}'s frame order. */
class UrbanDistrictQaSceneTest {
    private static final float DT = 1f / 60f;

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"SURVIVAL", "CREATIVE"})
    void theTourWalksATransitionAndSamplesEveryDistrictByDayNightAndFog(GameMode mode) {
        Game game = new Game();
        game.newWorld(20260731, true, World.GEN_URBAN_DISTRICTS, mode);
        game.appState = Game.AppState.PLAYING;
        UrbanQaScenes scenes = new UrbanQaScenes(game);
        HotkeyRouter router = new HotkeyRouter(game);
        scenes.stage("urban_districts");
        UrbanDistrictQaScene tour = scenes.districts();
        assertTrue(game.world.changedBlocks.isEmpty(), "staging changes no block");
        assertEquals(6, tour.plan().stops().size());
        assertNotEquals(tour.plan().from(), tour.plan().to(), "the walk crosses into another district");
        double time = 0;
        while (!tour.reported()) {
            if (time > UrbanDistrictQaScene.DEADLINE_SECONDS + 5) { fail("the tour never reported"); }
            scenes.update(time);
            if (game.appState != Game.AppState.DEATH && game.appState != Game.AppState.VICTORY) { router.update(); }
            game.playStep(DT, DT);
            game.world.ensureChunks((int) game.player.pos.x, (int) game.player.pos.z, game.renderer.renderRadius(), 2);
            game.input.endFrame();
            time += DT;
        }
        assertTrue(tour.passed());
        assertTrue(game.eventLog.all().stream().anyMatch(s -> s.contains("Entering ")), "the arrival line was shown");
    }

    @Test
    void theTourNeedsTheSixDistrictGenerator() {
        Game game = new Game();
        game.newWorld(7, true, World.GEN_URBAN_CIVIC, GameMode.SURVIVAL);
        assertThrows(IllegalStateException.class, () -> new UrbanQaScenes(game).stage("urban_districts"));
    }
}
