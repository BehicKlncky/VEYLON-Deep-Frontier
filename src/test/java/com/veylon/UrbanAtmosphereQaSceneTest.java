package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The {@code urban_atmosphere} tour, played headlessly in {@link Game#run}'s frame order: every
 * view is reached and reported on the design seed in both modes, staging changes no block, and
 * the places it names are the ones it means to show.
 */
class UrbanAtmosphereQaSceneTest {
    private static final float DT = 1f / 60f;

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"SURVIVAL", "CREATIVE"})
    void theTourHoldsEveryViewOnTheDesignSeed(GameMode mode) {
        Game game = new Game();
        game.newWorld(SurvivorWorldArena.SEED, true, World.GEN_URBAN_MISSIONS, mode);
        game.appState = Game.AppState.PLAYING;
        UrbanQaScenes scenes = new UrbanQaScenes(game);
        HotkeyRouter router = new HotkeyRouter(game);
        scenes.stage("urban_atmosphere");
        UrbanAtmosphereQaScene tour = scenes.atmosphere();
        assertTrue(game.world.changedBlocks.isEmpty(), "staging changes no block");
        double time = 0;
        while (!tour.reported()) {
            if (time > UrbanAtmosphereQaScene.DEADLINE_SECONDS + 5) { fail("the tour never reported"); }
            scenes.update(time);
            if (game.appState != Game.AppState.DEATH) { router.update(); }
            game.playStep(DT, DT);
            game.world.ensureChunks((int) game.player.pos.x, (int) game.player.pos.z, game.renderer.renderRadius(), 2);
            game.input.endFrame();
            time += DT;
        }
        assertTrue(tour.passed());
        assertEquals(UrbanAtmosphereQaScene.CAPTURES, tour.views().size());
        assertTrue(view(tour, "day-cellar-civilians").endsWith("/ Hidden cellar"), view(tour, "day-cellar-civilians"));
        assertTrue(view(tour, "day-apartment-basement").endsWith("/ Basement"), view(tour, "day-apartment-basement"));
        assertEquals("District / Quarantine Zone", view(tour, "day-street-quarantine"));
        assertEquals("District / Flooded Quarter", view(tour, "night-street-flooded"));
        assertEquals(com.veylon.world.urban.UrbanTraces.Kind.KNOCK_MARK.text(), tour.prompts().get("25-day-refuge-door-mark"),
                "the chalk mark by the refuge's door reads its words (milestone 22)");
    }

    /** The location line a view was captured with, by the view's name without its number. */
    private static String view(UrbanAtmosphereQaScene tour, String name) {
        return tour.views().stream().filter(v -> v.substring(3).startsWith(name + "=")).findFirst()
                .map(v -> v.substring(v.indexOf('=') + 1)).orElseThrow(() -> new AssertionError("no view " + name));
    }

    @Test
    void theTourNeedsTheMissionGenerator() {
        Game game = new Game();
        game.newWorld(7, true, World.GEN_URBAN_UTILITIES, GameMode.SURVIVAL);
        assertThrows(IllegalStateException.class, () -> new UrbanQaScenes(game).stage("urban_atmosphere"));
    }
}
