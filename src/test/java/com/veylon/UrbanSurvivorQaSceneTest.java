package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.UrbanSite;
import com.veylon.settlement.UrbanSiteDirector;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/** The {@code urban_survivors} tour, played headlessly in {@link Game#run}'s frame order. */
class UrbanSurvivorQaSceneTest {
    private static final float DT = 1f / 60f;

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"SURVIVAL", "CREATIVE"})
    void theTourFindsHelpsMovesAndRevisitsTheSurvivors(GameMode mode) {
        Game game = new Game();
        game.newWorld(20260731, true, World.GEN_URBAN_SURVIVORS, mode);
        game.appState = Game.AppState.PLAYING;
        UrbanQaScenes scenes = new UrbanQaScenes(game);
        HotkeyRouter router = new HotkeyRouter(game);
        scenes.stage("urban_survivors");
        UrbanSurvivorQaScene tour = scenes.survivors();
        assertTrue(game.world.changedBlocks.isEmpty(), "staging changes no block");
        double time = 0;
        while (!tour.reported()) {
            if (time > UrbanSurvivorQaScene.DEADLINE_SECONDS + 5) { fail("the tour never reported"); }
            scenes.update(time);
            if (game.appState != Game.AppState.DEATH && game.appState != Game.AppState.VICTORY) { router.update(); }
            game.playStep(DT, DT);
            game.world.ensureChunks((int) game.player.pos.x, (int) game.player.pos.z, game.renderer.renderRadius(), 2);
            game.input.endFrame();
            time += DT;
        }
        assertTrue(tour.passed());
        assertTrue(tour.residentFrames() > 1_000, "the survivors were watched walking: " + tour.residentFrames());
        UrbanSite camp = UrbanSiteDirector.registered(game.world, tour.plan().camp().site().id());
        UrbanSite refuge = UrbanSiteDirector.registered(game.world, tour.plan().refuge().site().id());
        assertTrue(camp.grantsAccess(), "the camp stays a friend");
        assertEquals(UrbanSite.Relocation.ARRIVED, refuge.relocation, "the civilians live at the camp now");
        for (Settlement.Resident r : refuge.residents) {
            assertTrue(r.alive, r.name + " made it");
        }
        assertTrue(game.eventLog.all().stream().noneMatch(l -> l.contains("has died of")), "nobody starved on the tour");
    }

    @Test
    void theTourNeedsTheSurvivorGenerator() {
        Game game = new Game();
        game.newWorld(7, true, World.GEN_URBAN_ECOLOGY, GameMode.SURVIVAL);
        assertThrows(IllegalStateException.class, () -> new UrbanQaScenes(game).stage("urban_survivors"));
    }
}
