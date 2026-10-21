package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.item.ItemType;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/** The {@code urban_ecology} tour, played headlessly in {@link Game#run}'s frame order. */
class UrbanEcologyQaSceneTest {
    private static final float DT = 1f / 60f;

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"SURVIVAL", "CREATIVE"})
    void theTourShowsTheVeilmawItsCountersAndTheCityWildlife(GameMode mode) {
        Game game = new Game();
        game.newWorld(20260731, true, World.GEN_URBAN_ECOLOGY, mode);
        game.appState = Game.AppState.PLAYING;
        UrbanQaScenes scenes = new UrbanQaScenes(game);
        HotkeyRouter router = new HotkeyRouter(game);
        scenes.stage("urban_ecology");
        UrbanEcologyQaScene tour = scenes.ecology();
        assertTrue(game.world.changedBlocks.isEmpty(), "staging changes no block");
        double time = 0;
        while (!tour.reported()) {
            if (time > UrbanEcologyQaScene.DEADLINE_SECONDS + 5) { fail("the tour never reported"); }
            scenes.update(time);
            if (game.appState != Game.AppState.DEATH && game.appState != Game.AppState.VICTORY) { router.update(); }
            game.playStep(DT, DT);
            game.world.ensureChunks((int) game.player.pos.x, (int) game.player.pos.z, game.renderer.renderRadius(), 2);
            game.input.endFrame();
            time += DT;
        }
        assertTrue(tour.passed());
        var first = tour.plan().first().site();
        assertNotSame(BlockType.GROWTH_NODE, game.world.getBlock(first.node().x(), first.node().y(), first.node().z()),
                "the burnt node stays gone");
        assertTrue(game.entities.encounters.resident(first.encounterId()).dead(), "the withered Veilmaw stays a tombstone");
        assertTrue(game.player.inventory.count(ItemType.MEMBRANE_SAC) >= 2);
        assertEquals(0, game.player.inventory.count(ItemType.RAW_MEAT), "alien remains are not food");
        var lure = tour.plan().second().lureFloor();
        assertNotSame(BlockType.TORCH, game.world.getBlock(lure.x(), lure.y() + 1, lure.z()), "the lure torch was smothered");
        assertTrue(game.eventLog.all().stream().anyMatch(s -> s.contains("The Veilmaw smothers the torch under its hood.")));
    }

    @Test
    void theTourNeedsTheEcologyGenerator() {
        Game game = new Game();
        game.newWorld(7, true, World.GEN_URBAN_UNDERGROUND, GameMode.SURVIVAL);
        assertThrows(IllegalStateException.class, () -> new UrbanQaScenes(game).stage("urban_ecology"));
    }
}
