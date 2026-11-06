package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The {@code urban_performance} tour, played headlessly in {@link Game#run}'s frame order with a
 * short journey: every leg is reached and reported in order on the design seed, the journey is
 * measured minute by minute, and F5 and F9 run. Wall-clock figures are the native run's to judge;
 * here only the tour's coverage is checked.
 */
class UrbanPerformanceQaSceneTest {
    private static final float DT = 1f / 60f;

    @Test
    void theTourReportsEveryLegOnTheDesignSeed() {
        Game game = new Game();
        game.newWorld(SurvivorWorldArena.SEED, true, World.GEN_URBAN_ECONOMY, GameMode.SURVIVAL);
        game.appState = Game.AppState.PLAYING;
        UrbanQaScenes scenes = new UrbanQaScenes(game);
        HotkeyRouter router = new HotkeyRouter(game);
        UrbanPerformanceQaScene tour = scenes.performance();
        tour.travelSeconds(2 * UrbanPerformanceQaScene.TRAVEL_REPORT + 5);
        scenes.stage("urban_performance");
        assertTrue(game.world.changedBlocks.isEmpty(), "staging changes no block");
        double time = 0;
        while (!tour.reported()) {
            if (time > UrbanPerformanceQaScene.DEADLINE_SECONDS) { fail("the tour never reported"); }
            scenes.update(time);
            if (game.appState != Game.AppState.DEATH) { router.update(); }
            game.playStep(DT, DT);
            game.world.ensureChunks((int) game.player.pos.x, (int) game.player.pos.z, game.renderer.renderRadius(), 2);
            game.input.endFrame();
            time += DT;
        }
        assertTrue(tour.passed());
        List<String> names = tour.legs().stream().map(l -> l.substring(4, l.indexOf(' '))).toList();
        assertEquals(List.of("street-walk", "apartment-kitchen", "apartment-upper-floor", "apartment-basement",
                "transit-platform", "transit-tunnel", "stronghold-yard", "quarantine-growth", "storm-fire",
                "long-travel-1", "long-travel-2", "long-travel-3", "long-travel-end", "save-and-load"), names);
        for (String leg : tour.legs()) {
            if (!leg.startsWith("leg=save-and-load")) {
                assertFalse(leg.contains(" frames=0 "), "every leg measured frames: " + leg);
            }
        }
        assertTrue(tour.legs().getLast().contains("loadFrameMs="), "F9 ran: " + tour.legs().getLast());
    }

    @Test
    void theTourNeedsTheMissionGenerator() {
        Game game = new Game();
        game.newWorld(7, true, World.GEN_URBAN_UTILITIES, GameMode.SURVIVAL);
        assertThrows(IllegalStateException.class, () -> new UrbanQaScenes(game).stage("urban_performance"));
    }
}
