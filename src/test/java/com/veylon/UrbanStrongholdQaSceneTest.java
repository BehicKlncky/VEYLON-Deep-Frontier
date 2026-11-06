package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.settlement.CompoundAccess;
import com.veylon.settlement.UrbanSite;
import com.veylon.settlement.UrbanSiteDirector;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/** The {@code urban_strongholds} tour, played headlessly in {@link Game#run}'s frame order. */
class UrbanStrongholdQaSceneTest {
    private static final float DT = 1f / 60f;

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"SURVIVAL", "CREATIVE"})
    void theTourEntersAStrongholdMeetsAThreatAndAvoidsTheRaiders(GameMode mode) {
        Game game = new Game();
        game.newWorld(20260731, true, World.GEN_URBAN_STRONGHOLDS, mode);
        game.appState = Game.AppState.PLAYING;
        UrbanQaScenes scenes = new UrbanQaScenes(game);
        HotkeyRouter router = new HotkeyRouter(game);
        scenes.stage("urban_strongholds");
        UrbanStrongholdQaScene tour = scenes.strongholds();
        assertTrue(game.world.changedBlocks.isEmpty(), "staging changes no block");
        double time = 0;
        while (!tour.reported()) {
            if (time > UrbanStrongholdQaScene.DEADLINE_SECONDS + 5) { fail("the tour never reported"); }
            scenes.update(time);
            if (game.appState != Game.AppState.DEATH && game.appState != Game.AppState.VICTORY) { router.update(); }
            game.playStep(DT, DT);
            game.world.ensureChunks((int) game.player.pos.x, (int) game.player.pos.z, game.renderer.renderRadius(), 2);
            game.input.endFrame();
            time += DT;
        }
        assertTrue(tour.passed());
        assertTrue(tour.residentFrames() > 1_000, "the compounds' people were watched: " + tour.residentFrames());
        UrbanSite stronghold = UrbanSiteDirector.registered(game.world, tour.plan().stronghold().compound().site().id());
        UrbanSite raiders = UrbanSiteDirector.registered(game.world, tour.plan().raiders().compound().site().id());
        assertFalse(stronghold.hostile(), "a visitor who kept the rules is still welcome");
        assertFalse(CompoundAccess.wanted(game, stronghold));
        assertFalse(raiders.cleared, "the raider yard was avoided, not taken");
        assertTrue(raiders.huntCooldownUntil < 0, "the raiders never came out after the player");
    }

    @Test
    void theTourNeedsTheStrongholdGenerator() {
        Game game = new Game();
        game.newWorld(7, true, World.GEN_URBAN_SURVIVORS, GameMode.SURVIVAL);
        assertThrows(IllegalStateException.class, () -> new UrbanQaScenes(game).stage("urban_strongholds"));
    }
}
