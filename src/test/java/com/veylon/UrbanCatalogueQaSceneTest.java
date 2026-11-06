package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.item.ItemType;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/** The {@code urban_catalogue} walk, played headlessly in {@link Game#run}'s frame order. */
class UrbanCatalogueQaSceneTest {
    private static final float DT = 1f / 60f;

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"SURVIVAL", "CREATIVE"})
    void theWalkSamplesEveryFamilyUpperFloorsLockedEntriesAndTheForcedDispensary(GameMode mode) {
        Game game = new Game();
        game.newWorld(20260731, true, World.GEN_URBAN_CIVIC, mode);
        game.appState = Game.AppState.PLAYING;
        UrbanQaScenes scenes = new UrbanQaScenes(game);
        HotkeyRouter router = new HotkeyRouter(game);
        scenes.stage("urban_catalogue");
        UrbanCatalogueQaScene walk = scenes.catalogue();
        assertTrue(game.world.changedBlocks.isEmpty(), "staging changes no block");
        double time = 0;
        while (!walk.reported()) {
            if (time > UrbanCatalogueQaScene.DEADLINE_SECONDS + 5) { fail("the walk never reported"); }
            scenes.update(time);
            if (game.appState != Game.AppState.DEATH && game.appState != Game.AppState.VICTORY) { router.update(); }
            game.playStep(DT, DT);
            game.world.ensureChunks((int) game.player.pos.x, (int) game.player.pos.z, game.renderer.renderRadius(), 2);
            game.input.endFrame();
            time += DT;
        }
        assertTrue(walk.passed());
        var dispensary = UrbanCataloguePlan.securedDoor(walk.plan().clinic()).position();
        assertSame(mode == GameMode.CREATIVE ? BlockType.AIR : BlockType.BROKEN_DOOR,
                game.world.getBlock(dispensary.x(), dispensary.y(), dispensary.z()), "the dispensary was forced");
        var stock = UrbanCataloguePlan.furnishing(walk.plan().clinic(),
                UrbanCataloguePlan.room(walk.plan().clinic(), 0, com.veylon.world.urban.BuildingPlan.Function.PHARMACY));
        assertTrue(game.world.urban.containers.at(stock.position()).state.revealed(), "the dispensary stock was searched");
        assertTrue(game.player.inventory.count(ItemType.IRON_PICKAXE) == 1, "nothing was taken or lost");
    }

    /**
     * The development selector now creates generator 12, whose district catalogue keeps every
     * family within one region of the spawn (a school in residential regions, a police post
     * downtown), so the native catalogue walk still finds a building of each.
     */
    @Test
    void theWalkStillVisitsEveryFamilyOnTheSixDistrictGenerator() {
        Game game = new Game();
        game.newWorld(20260731, true, World.GEN_URBAN_DISTRICTS, GameMode.SURVIVAL);
        game.appState = Game.AppState.PLAYING;
        UrbanQaScenes scenes = new UrbanQaScenes(game);
        HotkeyRouter router = new HotkeyRouter(game);
        scenes.stage("urban_catalogue");
        UrbanCatalogueQaScene walk = scenes.catalogue();
        double time = 0;
        while (!walk.reported()) {
            if (time > UrbanCatalogueQaScene.DEADLINE_SECONDS + 5) { fail("the walk never reported"); }
            scenes.update(time);
            if (game.appState != Game.AppState.DEATH && game.appState != Game.AppState.VICTORY) { router.update(); }
            game.playStep(DT, DT);
            game.world.ensureChunks((int) game.player.pos.x, (int) game.player.pos.z, game.renderer.renderRadius(), 2);
            game.input.endFrame();
            time += DT;
        }
        assertTrue(walk.passed());
    }

    @Test
    void theWalkNeedsTheCivicCatalogueGenerator() {
        Game game = new Game();
        game.newWorld(7, true, World.GEN_URBAN_ENCOUNTERS, GameMode.SURVIVAL);
        assertThrows(IllegalStateException.class, () -> new UrbanQaScenes(game).stage("urban_catalogue"));
    }
}
