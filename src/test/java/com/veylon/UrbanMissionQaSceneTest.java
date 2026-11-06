package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.world.World;
import com.veylon.world.urban.UrbanMissions;
import com.veylon.world.urban.UrbanMissions.Kind;
import com.veylon.world.urban.UrbanMissions.Mission;
import com.veylon.world.urban.UrbanMissions.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

/**
 * The {@code urban_missions} tour, played headlessly in {@link Game#run}'s frame order. The ending
 * card's keys belong to the frame step this loop does not run, so the loop answers Escape on a
 * city's card the way that step does: back to the city, never to the title.
 */
class UrbanMissionQaSceneTest {
    private static final float DT = 1f / 60f;

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"SURVIVAL", "CREATIVE"})
    void theTourTakesThreeMissionsRepairsTheRelayAndTheCityAnswers(GameMode mode) {
        Game game = new Game();
        game.newWorld(UrbanCommsTest.SEED, true, World.GEN_URBAN_MISSIONS, mode);
        game.appState = Game.AppState.PLAYING;
        UrbanQaScenes scenes = new UrbanQaScenes(game);
        HotkeyRouter router = new HotkeyRouter(game);
        scenes.stage("urban_missions");
        UrbanMissionQaScene tour = scenes.missions();
        assertTrue(game.world.changedBlocks.isEmpty(), "staging changes no block");
        double time = 0;
        while (!tour.reported()) {
            if (time > UrbanMissionQaScene.DEADLINE_SECONDS + 5) { fail("the tour never reported"); }
            scenes.update(time);
            if (game.appState == Game.AppState.VICTORY && game.input.wasKeyPressed(GLFW_KEY_ESCAPE) && game.world.isUrban()) {
                game.appState = Game.AppState.PLAYING;
            }
            if (game.appState != Game.AppState.DEATH && game.appState != Game.AppState.VICTORY) { router.update(); }
            game.playStep(DT, DT);
            game.world.ensureChunks((int) game.player.pos.x, (int) game.player.pos.z, game.renderer.renderRadius(), 2);
            game.input.endFrame();
            time += DT;
        }
        assertTrue(tour.passed());
        assertSame(Game.AppState.PLAYING, game.appState, "the city carries on after its card");
        UrbanMissions ledger = game.world.urban.missions;
        assertTrue(ledger.comms.complete);
        assertEquals(UrbanMissions.RELAY_REPAIRED, ledger.comms.installed);
        assertEquals(UrbanMissions.CODES_STRONGHOLD, ledger.comms.codes, "the call carried the Wardens' codes");
        int rewarded = 0;
        for (Mission m : ledger.missions()) {
            if (m.rewarded) { rewarded++; }
        }
        assertEquals(3, rewarded, "the hidden people's, the mast's and the module's missions, each paid once: " + ledger.missions());
        assertSame(Status.ACTIVE, ledger.find(Kind.CLINIC_SUPPLIES, mission(ledger, Kind.CLINIC_SUPPLIES).target).status,
                "the clinic was never visited, so its mission stays open");
    }

    @Test
    void theTourNeedsTheMissionGenerator() {
        Game game = new Game();
        game.newWorld(7, true, World.GEN_URBAN_UTILITIES, GameMode.SURVIVAL);
        assertThrows(IllegalStateException.class, () -> new UrbanQaScenes(game).stage("urban_missions"));
    }

    private static Mission mission(UrbanMissions ledger, Kind kind) {
        return ledger.missions().stream().filter(m -> m.kind == kind).findFirst().orElseThrow();
    }
}
