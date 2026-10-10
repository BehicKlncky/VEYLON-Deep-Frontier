package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.ui.NewFrontierScreen;
import com.veylon.ui.TitleScreen;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.UrbanDatum;
import com.veylon.world.urban.UrbanRevision;
import com.veylon.world.urban.UrbanSurface;
import com.veylon.world.urban.UrbanSurfaceMaterials;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The development selector reaches the urban generator only on request, only
 * with an isolated data directory, and through the same frontend and bootstrap
 * path a player's new world takes; without it nothing changes.
 */
class UrbanDevelopmentSelectorTest {

    private static final long SEED = 20_261_007L;

    @TempDir
    Path temporary;

    @Test
    void unsetSelectsTheOrdinaryDefault() {
        assertEquals(World.DEFAULT_NEW_WORLD_GENERATOR,
                AutomatedRunDriver.configuredGenerator(null, false));
        assertEquals(World.DEFAULT_NEW_WORLD_GENERATOR,
                AutomatedRunDriver.configuredGenerator("  ", false));
    }

    @Test
    void urbanDevSelectsTheUrbanGeneratorOnlyWithAnIsolatedDataDirectory() {
        assertEquals(World.GEN_URBAN_APARTMENTS, AutomatedRunDriver.configuredGenerator("urban-dev", true),
                "the selector builds the newest urban layout");
        assertEquals(UrbanRevision.DEVELOPMENT_GENERATOR,
                AutomatedRunDriver.configuredGenerator(" Urban-Dev ", true));

        IllegalStateException unsafe = assertThrows(IllegalStateException.class,
                () -> AutomatedRunDriver.configuredGenerator("urban-dev", false));
        assertTrue(unsafe.getMessage().contains("veylon.dataDir"), unsafe.getMessage());
        for (String invalid : new String[]{"urban", "4", "legacy", "urban-dev2"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> AutomatedRunDriver.configuredGenerator(invalid, true), invalid);
        }
    }

    @Test
    void newFrontierBuildsAnUrbanDevelopmentWorldThroughTheProductionPath() {
        Game game = new Game();
        game.sessionSeed = SEED;
        game.frontend.savePath = temporary.resolve("absent.sav");
        game.frontend.newWorldGenerator = AutomatedRunDriver.configuredGenerator("urban-dev", true);

        startNewFrontier(game);

        assertEquals(World.GEN_URBAN_APARTMENTS, game.world.generatorVersion);
        assertEquals(GameMode.SURVIVAL, game.gameMode());
        assertEquals(UrbanRevision.CURRENT_CONTENT, game.world.urban.contentRevision());
        assertEquals(UrbanDatum.STREET_Y + 1.2f, game.player.pos.y, 1e-4f,
                "the spawn stands on the planned ground, not a roof, a crown or the fill");
        int x = (int) Math.floor(game.player.pos.x);
        int z = (int) Math.floor(game.player.pos.z);
        assertEquals(x, game.world.urban.spawnColumn()[0]);
        assertEquals(z, game.world.urban.spawnColumn()[1]);
        assertEquals(UrbanSurface.COURTYARD_PATH, game.world.urban.surfaceAt(x, z),
                "in front of the reserved refuge door, never on a parcel");
        assertEquals(UrbanSurfaceMaterials.blockFor(UrbanSurface.COURTYARD_PATH),
                game.world.getBlock(x, UrbanDatum.STREET_Y, z));
        assertEquals(BlockType.AIR, game.world.getBlock(x, UrbanDatum.STREET_Y + 1, z));
        assertEquals(BlockType.AIR, game.world.getBlock(x, UrbanDatum.STREET_Y + 2, z));
        assertTrue(game.entities.creatures.isEmpty(),
                "no surface wildlife lands on city roofs (decision D-28)");
        assertNull(game.world.campPos, "no crash camp in a city");
        assertNull(game.faction.campPos);
        assertTrue(game.entities.npcs.isEmpty(), "no camp residents");
        assertTrue(game.world.crateContents.isEmpty(), "no camp crates");
        assertTrue(game.player.inventory.count(com.veylon.item.ItemType.BANDAGE) > 0,
                "the fresh-world kit is still granted");
        assertTrue(game.eventLog.all().stream()
                .anyMatch(line -> line.endsWith(UrbanBootstrap.APARTMENTS_NOTICE)),
                "the log says what this world is instead of promising a camp");
    }

    @Test
    void anUrbanDevelopmentWorldHonoursTheChosenMode() {
        Game creative = new Game();
        creative.newWorld(SEED, true, UrbanRevision.DEVELOPMENT_GENERATOR, GameMode.CREATIVE);
        assertEquals(GameMode.CREATIVE, creative.gameMode());
        assertTrue(creative.player.abilities.invulnerable());
        assertTrue(creative.world.isUrban());
        assertTrue(creative.entities.npcs.isEmpty());

        Game survival = new Game();
        survival.newWorld(SEED, true, UrbanRevision.DEVELOPMENT_GENERATOR, GameMode.SURVIVAL);
        assertFalse(survival.player.abilities.invulnerable());
        assertEquals(survival.spawnPos, creative.spawnPos, "mode does not move the spawn");
    }

    @Test
    void anOrdinaryNewFrontierIsUnchanged() {
        Game game = new Game();
        game.sessionSeed = SEED;
        game.frontend.savePath = temporary.resolve("absent.sav");

        startNewFrontier(game);

        assertEquals(World.DEFAULT_NEW_WORLD_GENERATOR, game.world.generatorVersion);
        assertNull(game.world.urban);
        assertNotNull(game.world.campPos, "the legacy crash camp is still raised");
        assertFalse(game.entities.npcs.isEmpty());
    }

    @Test
    void stagedFrontendCapturesUseTheSessionGenerator() {
        Game urban = new Game();
        urban.sessionSeed = SEED;
        urban.frontend.newWorldGenerator = UrbanRevision.DEVELOPMENT_GENERATOR;
        urban.frontend.openForQa("pause");
        assertEquals(UrbanRevision.DEVELOPMENT_GENERATOR, urban.world.generatorVersion,
                "VEYLON_FRONTEND staging must not silently fall back to the default");
        assertEquals(Game.UiMode.PAUSE, urban.uiMode);

        Game ordinary = new Game();
        ordinary.sessionSeed = SEED;
        ordinary.frontend.openForQa("pause-creative");
        assertEquals(World.DEFAULT_NEW_WORLD_GENERATOR, ordinary.world.generatorVersion);
        assertEquals(GameMode.CREATIVE, ordinary.gameMode());
    }

    @Test
    void anUnsupportedGeneratorIsRefusedBeforeTheLiveWorldIsTouched() {
        Game game = new Game();
        game.newWorld(SEED, true);
        World live = game.world;
        assertThrows(IllegalArgumentException.class,
                () -> game.newWorld(SEED, true, World.MAX_SUPPORTED_GENERATOR + 1, GameMode.SURVIVAL));
        assertEquals(live, game.world);
    }

    private static void startNewFrontier(Game game) {
        game.frontend.applyTitle(TitleScreen.Action.NEW_GAME);
        game.frontend.applyNewFrontier(NewFrontierScreen.Action.START);
        assertEquals(Game.AppState.LOADING, game.appState);
        assertTrue(game.frontend.performLoad());
        assertEquals(Game.AppState.PLAYING, game.appState);
    }
}
