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
 * The QA selector of a session's new world: unset is the ordinary default, the abandoned city;
 * {@code wilderness} the legacy world; {@code urban-dev} the newest urban layout only with an
 * isolated data directory. Each reaches the same frontend and bootstrap path a player's new world
 * takes, and a scripted city scene never runs where it could save over a real save.
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
    void wildernessSelectsTheLegacyWorldAnywhere() {
        assertEquals(World.WILDERNESS_NEW_WORLD_GENERATOR,
                AutomatedRunDriver.configuredGenerator("wilderness", false),
                "the wilderness is an ordinary world a player can choose, so it needs no isolation");
        assertEquals(World.GEN_CAVE_IDENTITIES, AutomatedRunDriver.configuredGenerator(" Wilderness ", true));
    }

    @Test
    void scenesPlayTheWorldTheyWereMadeForAndCityScenesOnlyFromAnIsolatedDirectory() {
        int city = World.DEFAULT_NEW_WORLD_GENERATOR;
        assertEquals(city, AutomatedRunDriver.sceneGenerator("urban_slice", null, city, true));
        assertEquals(city, AutomatedRunDriver.sceneGenerator("Save_Audit", null, city, true));
        for (String scene : new String[]{"urban_missions", "save_audit", "urban_apartments_manual"}) {
            IllegalStateException unsafe = assertThrows(IllegalStateException.class,
                    () -> AutomatedRunDriver.sceneGenerator(scene, null, city, false), scene);
            assertTrue(unsafe.getMessage().contains("veylon.dataDir"), unsafe.getMessage());
        }
        assertEquals(World.WILDERNESS_NEW_WORLD_GENERATOR,
                AutomatedRunDriver.sceneGenerator("urban_slice", "wilderness",
                        World.WILDERNESS_NEW_WORLD_GENERATOR, false),
                "a city scene on the wilderness saves nothing of a city; it reports itself inactive");
        assertEquals(World.WILDERNESS_NEW_WORLD_GENERATOR,
                AutomatedRunDriver.sceneGenerator("day", null, city, false),
                "a wilderness scene keeps its world when no selector names one");
        assertEquals(World.WILDERNESS_NEW_WORLD_GENERATOR,
                AutomatedRunDriver.sceneGenerator("hud_showcase", "", city, false));
        assertEquals(city, AutomatedRunDriver.sceneGenerator("day", "urban-dev", city, true),
                "a selector that names a world wins");
        assertEquals(city, AutomatedRunDriver.sceneGenerator(null, null, city, false),
                "smoke and timed captures play the session's world");
    }

    @Test
    void urbanDevSelectsTheUrbanGeneratorOnlyWithAnIsolatedDataDirectory() {
        assertEquals(World.GEN_URBAN_ECONOMY, AutomatedRunDriver.configuredGenerator("urban-dev", true),
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

        assertEquals(World.GEN_URBAN_ECONOMY, game.world.generatorVersion);
        assertEquals(GameMode.SURVIVAL, game.gameMode());
        assertEquals(UrbanRevision.CURRENT_CONTENT, game.world.urban.contentRevision());
        assertEquals(UrbanDatum.STREET_Y + 1.05f, game.player.pos.y, 1e-4f,
                "the spawn stands on the planned ground, not a roof, a crown or the fill");
        int x = (int) Math.floor(game.player.pos.x);
        int z = (int) Math.floor(game.player.pos.z);
        assertEquals(x, game.world.urban.refugeAnchor.x());
        assertEquals(z, game.world.urban.refugeAnchor.z());
        assertEquals(com.veylon.world.urban.ParcelPlan.Use.REFUGE, game.world.urban.parcelAt(x, z).use());
        assertTrue(game.world.getBlock(x, UrbanDatum.STREET_Y, z).solid);
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
                .anyMatch(line -> line.contains("City refuge")),
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
    void anOrdinaryNewFrontierIsTheCityAndTheWildernessIsOneKeyAway() {
        Game city = new Game();
        city.sessionSeed = SEED;
        city.frontend.savePath = temporary.resolve("absent.sav");
        startNewFrontier(city);
        assertEquals(World.GEN_URBAN_ECONOMY, city.world.generatorVersion, "the default is the economy city");
        assertEquals(UrbanRevision.CURRENT_CONTENT, city.world.urban.contentRevision());
        assertEquals(com.veylon.world.urban.ParcelPlan.Use.REFUGE, city.world.urban.parcelAt((int) Math.floor(city.player.pos.x),
                (int) Math.floor(city.player.pos.z)).use(), "the survivor wakes in the starter refuge");

        Game wild = new Game();
        wild.sessionSeed = SEED;
        wild.frontend.savePath = temporary.resolve("absent.sav");
        wild.frontend.applyTitle(TitleScreen.Action.NEW_GAME);
        assertEquals("Mapping the abandoned city...", wild.frontend.loadingDetail(), "the city is on offer first");
        wild.newFrontierScreen.open(false, false); // what W does on the screen (ModeSelectionRoutingTest)
        wild.frontend.applyNewFrontier(NewFrontierScreen.Action.START);
        assertEquals("Mapping atmosphere and terrain...", wild.frontend.loadingDetail());
        assertTrue(wild.frontend.performLoad());
        assertEquals(World.WILDERNESS_NEW_WORLD_GENERATOR, wild.world.generatorVersion);
        assertNull(wild.world.urban);
        assertNotNull(wild.world.campPos, "the legacy crash camp is still raised");
        assertFalse(wild.entities.npcs.isEmpty());

        wild.frontend.returnToTitle();
        startNewFrontier(wild);
        assertEquals(World.DEFAULT_NEW_WORLD_GENERATOR, wild.world.generatorVersion,
                "the wilderness choice does not carry into the next new frontier");
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
        assertTrue(ordinary.world.isUrban(), "an ordinary staged capture shows the city");
        assertEquals(GameMode.CREATIVE, ordinary.gameMode());

        Game wild = new Game();
        wild.sessionSeed = SEED;
        wild.frontend.newWorldGenerator = AutomatedRunDriver.configuredGenerator("wilderness", false);
        wild.frontend.openForQa("pause");
        assertEquals(World.WILDERNESS_NEW_WORLD_GENERATOR, wild.world.generatorVersion);
    }

    @Test
    void anUnsupportedGeneratorIsRefusedBeforeTheLiveWorldIsTouched() {
        Game game = new Game();
        game.newWorld(SEED, true, World.GEN_CAVE_IDENTITIES);
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
