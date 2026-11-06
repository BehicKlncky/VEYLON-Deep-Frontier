package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The {@code urban_onboarding} tour, played headlessly in {@link Game#run}'s frame order: the map
 * opens and closes on M without being drawn, the cupboard is searched through the storage screen,
 * the camp's lookout answers, and the journal and visits survive F5 and F9.
 */
class UrbanOnboardingQaSceneTest {
    private static final float DT = 1f / 60f;

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"SURVIVAL", "CREATIVE"})
    void theTourReadsTheHudMapJournalAndSketchAndKeepsThemAcrossALoad(GameMode mode) {
        Game game = new Game();
        game.newWorld(SurvivorWorldArena.SEED, true, World.GEN_URBAN_MISSIONS, mode);
        game.appState = Game.AppState.PLAYING;
        UrbanQaScenes scenes = new UrbanQaScenes(game);
        HotkeyRouter router = new HotkeyRouter(game);
        scenes.stage("urban_onboarding");
        UrbanOnboardingQaScene tour = scenes.onboarding();
        assertTrue(game.world.changedBlocks.isEmpty(), "staging changes no block");
        double time = 0;
        while (!tour.reported()) {
            if (time > UrbanOnboardingQaScene.DEADLINE_SECONDS + 5) { fail("the tour never reported"); }
            scenes.update(time);
            if (game.appState != Game.AppState.DEATH) { router.update(); }
            game.playStep(DT, DT);
            game.world.ensureChunks((int) game.player.pos.x, (int) game.player.pos.z, game.renderer.renderRadius(), 2);
            game.input.endFrame();
            time += DT;
        }
        assertTrue(tour.passed());
        assertEquals(2, game.world.urban.missions.activeCount());
        assertTrue(game.world.urban.visits.buildingCount() >= 2, "the refuge and the flats beside it were entered");
    }

    @Test
    void theTourNeedsTheMissionGenerator() {
        Game game = new Game();
        game.newWorld(7, true, World.GEN_URBAN_UTILITIES, GameMode.SURVIVAL);
        assertThrows(IllegalStateException.class, () -> new UrbanQaScenes(game).stage("urban_onboarding"));
    }
}
