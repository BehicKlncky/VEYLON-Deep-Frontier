package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.item.ItemType;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The {@code urban_economy} tour, played headlessly in {@link Game#run}'s frame order: two and a half
 * real-time days of survival in the economy city, nights slept in the refuge, a load at the end. The
 * native run is the extended-play evidence; this keeps its route honest between native runs.
 */
class UrbanEconomyQaSceneTest {
    private static final float DT = 1f / 30f;

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"SURVIVAL", "CREATIVE"})
    void theTourLivesTwoAndAHalfDaysOnWhatTheCityGives(GameMode mode) {
        Game game = new Game();
        game.newWorld(SurvivorWorldArena.SEED, true, World.GEN_URBAN_ECONOMY, mode);
        game.appState = Game.AppState.PLAYING;
        UrbanQaScenes scenes = new UrbanQaScenes(game);
        HotkeyRouter router = new HotkeyRouter(game);
        scenes.stage("urban_economy");
        UrbanEconomyQaScene tour = scenes.economy();
        assertTrue(game.world.changedBlocks.isEmpty(), "staging changes no block");
        double time = 0;
        while (!tour.reported()) {
            if (time > UrbanEconomyQaScene.DEADLINE_SECONDS + 5) { fail("the tour never reported"); }
            scenes.update(time);
            if (game.appState != Game.AppState.DEATH && game.appState != Game.AppState.VICTORY) { router.update(); }
            game.playStep(DT, DT);
            game.world.ensureChunks((int) game.player.pos.x, (int) game.player.pos.z, game.renderer.renderRadius(), 2);
            game.input.endFrame();
            time += DT;
        }
        assertTrue(tour.passed(), "the tour passed in " + mode);
        assertFalse(game.player.dead);
        assertTrue(game.time.totalMinutes >= 2 * 24 * 60 + 5 * 60, "into the third day: " + game.time.totalMinutes);
        assertEquals(1, Math.min(1, game.player.inventory.count(ItemType.IRON_SPEAR)), "the first upgrade");
        for (ItemType wild : new ItemType[]{ItemType.LOG, ItemType.IRON_ORE, ItemType.COAL}) {
            assertEquals(0, game.player.inventory.count(wild), "no " + wild);
        }
    }

    @Test
    void theTourNeedsTheEconomyGenerator() {
        Game game = new Game();
        game.newWorld(7, true, World.GEN_URBAN_MISSIONS, GameMode.SURVIVAL);
        assertThrows(IllegalStateException.class, () -> new UrbanQaScenes(game).stage("urban_economy"));
    }
}
