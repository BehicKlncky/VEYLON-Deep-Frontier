package com.veylon;

import com.veylon.engine.AudioEnvironment;
import com.veylon.engine.MusicMood;
import com.veylon.entity.GameMode;
import com.veylon.entity.Player;
import com.veylon.simulation.ShelterSystem;
import com.veylon.simulation.WeatherSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.UrbanRevision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Live production observations of real generated buildings, independent of scripted QA cameras. */
class UrbanIndoorEnvironmentTest {
    private static final long SEED = 20260731L;

    @Test
    void generatedRoomAndFloorLocationsDoNotConfuseRoofsWithStreetDepth() {
        Game game = scene();
        BuildingPlan building = apartment(game.world);
        loadBuilding(game.world, building);
        int checked = 0;
        for (BuildingPlan.Floor floor : building.floors()) {
            for (BuildingPlan.Room room : floor.rooms()) {
                Vec3i p = room.anchor();
                var location = game.world.urbanLocation(p.x(), p.y(), p.z());
                assertNotNull(location);
                assertEquals(building.id(), location.building().id());
                assertEquals(floor.id(), location.floor().id());
                assertNotNull(location.room());
                assertEquals(room.id(), location.room().id());
                assertEquals(40 - p.y(), game.world.environmentDepth(p.x(), p.y(), p.z()));
                checked++;
            }
        }
        assertTrue(checked > 12, "a real multi-floor apartment with distinct rooms was checked");
    }

    @Test
    void windowDaylightAndLiveRoofBreachesRemainDistinctFromRainExposure() {
        Game game = scene();
        BuildingPlan building = apartment(game.world);
        loadBuilding(game.world, building);
        var window = building.fixtures().stream().filter(f -> f.kind() == BuildingPlan.FixtureKind.WINDOW
                && f.position().y() == 42).findFirst().orElseThrow();
        Vec3i p = window.target(), pane = window.position();
        float daylight = game.world.skyLight(p.x(), p.y() + 1, p.z());
        assertTrue(daylight > 0.12f, "the real facade window admits daylight at its interior target");
        assertTrue(daylight < 0.9f, "indirect window light cannot drive rain exposure");
        assertFalse(game.world.isSkyExposed(p.x(), p.y() + 1, p.z()));
        rain(game);
        assertFalse(game.fire.isPrecipitationReaching(game, p.x(), p.y() + 1, p.z()));

        for (int y = pane.y(); y < pane.y() + window.height(); y++) {
            game.world.setBlock(pane.x(), y, pane.z(), BlockType.BRICK, false);
        }
        assertTrue(game.world.skyLight(p.x(), p.y() + 1, p.z()) < daylight,
                "sealing the opening changes daylight without a plan/cache invalidation");

        clearAbove(game.world, p);
        assertTrue(game.world.isSkyExposed(p.x(), p.y() + 1, p.z()));
        assertEquals(1f, game.world.skyLight(p.x(), p.y() + 1, p.z()));
        assertTrue(game.fire.isPrecipitationReaching(game, p.x(), p.y() + 1, p.z()));
        assertFalse(ShelterSystem.evaluate(game.world, p.x() + 0.5f, p.y(), p.z() + 0.5f).roofed());
    }

    @Test
    void closedGlassCoverTransmitsLightButOnlyAnOpenedOrBrokenPaneLetsRainIn() {
        Game game = scene();
        BuildingPlan building = apartment(game.world);
        loadBuilding(game.world, building);
        Vec3i p = domesticRoom(building).anchor();
        clearAbove(game.world, p);
        rain(game);
        int ceiling = p.y() + 3;
        game.world.setBlock(p.x(), p.y(), p.z(), BlockType.RAIN_COLLECTOR, false);
        game.world.collectorWater.put(p, 0f);
        game.world.setBlock(p.x(), ceiling, p.z(), BlockType.WINDOW, false);
        assertEquals(0.8f, game.world.skyLight(p.x(), p.y() + 1, p.z()));
        assertFalse(game.world.isSkyExposed(p.x(), p.y() + 1, p.z()));
        assertFalse(game.fire.isPrecipitationReaching(game, p.x(), p.y() + 1, p.z()));
        game.itemConditions.slowTick(game, 10f);
        assertEquals(0f, game.world.collectorWater.get(p), "transmitted daylight cannot fill a roofed collector");
        for (BlockType opening : new BlockType[]{BlockType.WINDOW_OPEN, BlockType.BROKEN_WINDOW}) {
            game.world.collectorWater.put(p, 0f);
            game.world.setBlock(p.x(), ceiling, p.z(), opening, true);
            assertTrue(game.world.isSkyExposed(p.x(), p.y() + 1, p.z()));
            assertTrue(game.fire.isPrecipitationReaching(game, p.x(), p.y() + 1, p.z()));
            game.itemConditions.slowTick(game, 10f);
            assertTrue(game.world.collectorWater.get(p) > 0, "the exposed collector receives rain");
        }
    }

    @Test
    void realApartmentShelterKeepsClimateAndUsesStructureAudioInsteadOfDeepCaves() {
        Game game = scene();
        BuildingPlan building = apartment(game.world);
        loadBuilding(game.world, building);
        Vec3i p = domesticRoom(building).anchor();
        stand(game, p);
        game.player.shelter = ShelterSystem.evaluate(game.world, game.player.pos.x,
                game.player.pos.y, game.player.pos.z);
        assertTrue(game.player.shelter.indoor());
        game.player.exposedToSky = false;
        assertEquals(AudioEnvironment.Zone.STONE_STRUCTURE, AudioSceneState.environment(game, 30));
        assertEquals(MusicMood.CALM, AudioSceneState.music(game, 30), "a tall roof cannot choose deep-cave music");

        game.time.totalMinutes = 12 * 60;
        game.weather.current = game.weather.next = WeatherSystem.Weather.CLEAR;
        float clearWeather = game.weather.tempOffset();
        float clear = game.temperature.envTempAt(game, p.x() + 0.5f, p.y(), p.z() + 0.5f);
        rain(game);
        float storm = game.temperature.envTempAt(game, p.x() + 0.5f, p.y(), p.z() + 0.5f);
        assertEquals((game.weather.tempOffset() - clearWeather) * 0.35f, storm - clear, 0.001f);
        assertNotEquals(9f, storm, 0.1f, "ground-floor climate is not deep-rock temperature");
    }

    @Test
    void playerWeatherExposureObservesLiveCoverInSurvivalAndCreative() {
        for (GameMode mode : GameMode.values()) {
            Game game = scene();
            BuildingPlan building = apartment(game.world);
            loadBuilding(game.world, building);
            Vec3i p = domesticRoom(building).anchor();
            game.player.abilities.apply(mode);
            stand(game, p);
            game.player.tickNeeds(game, 0.05f);
            assertFalse(game.player.exposedToSky, mode.toString());
            clearAbove(game.world, p);
            game.player.tickNeeds(game, 0.05f);
            assertTrue(game.player.exposedToSky, mode.toString());
        }
    }

    @Test
    void unloadedIndoorQueriesNeverGenerateOrTreatMissingColumnsAsExposedSky() {
        Game game = scene();
        int cached = game.world.urban.cachedBuildingPlans();
        assertNull(game.world.urbanLocation(-4000, 41, -4000));
        assertFalse(game.world.isSkyExposed(-4000, 42, -4000));
        assertEquals(0.04f, game.world.skyLight(-4000, 42, -4000));
        assertEquals(ShelterSystem.OPEN, ShelterSystem.evaluate(game.world, -4000, 41, -4000));
        game.player.pos.set(-4000, 41, -4000);
        game.ambience.updateAmbienceMix();
        assertEquals(0, game.world.loadedCount());
        assertEquals(cached, game.world.urban.cachedBuildingPlans());
    }

    @Test
    void anEditedSkyOccluderAcrossAChunkBoundaryDirtiesTheWindowLitNeighbor() {
        Game game = scene();
        Chunk interior = game.world.getOrCreateChunk(0, 0);
        game.world.getOrCreateChunk(1, 0);
        // Isolate one high window at the seam, above all generated buildings.
        game.world.beginBatch();
        for (int x = 11; x <= 16; x++) {
            for (int z = 7; z <= 9; z++) {
                for (int y = 84; y <= 88; y++) {
                    boolean shell = x <= 15 && (x == 11 || z != 8 || y == 84 || y == 88);
                    game.world.setBlock(x, y, z, shell ? BlockType.BRICK : BlockType.AIR, false);
                }
            }
        }
        for (int y = 85; y <= 87; y++) { game.world.setBlock(15, y, 8, BlockType.BRICK, false); }
        game.world.setBlock(15, 86, 8, BlockType.WINDOW, false);
        game.world.endBatch();
        float before = game.world.skyLight(13, 86, 8);
        assertTrue(before > 0.12f);
        interior.dirty = false;
        game.world.setBlock(16, 88, 8, BlockType.BRICK, true);
        assertTrue(interior.dirty, "the other chunk's window light must be remeshed");
        assertTrue(game.world.skyLight(13, 86, 8) < before);
    }

    @Test
    void historicalGeneratorsKeepTheirOriginalGlassAndRoofDepthQueries() {
        for (int generator = World.GEN_LEGACY; generator <= World.GEN_URBAN_PLANS; generator++) {
            World world = new World(SEED, generator);
            Chunk chunk = world.getOrCreateChunk(0, 0);
            for (int y = 0; y < Chunk.SY; y++) { chunk.set(8, y, 8, BlockType.AIR); }
            chunk.set(8, 80, 8, BlockType.WINDOW);
            chunk.recomputeHeight(8, 8);
            assertTrue(world.isSkyExposed(8, 60, 8), "legacy glass exposure, generator " + generator);
            assertEquals(1f, world.skyLight(8, 60, 8), "legacy glass lighting, generator " + generator);
            chunk.set(8, 80, 8, BlockType.CONCRETE);
            chunk.recomputeHeight(8, 8);
            assertEquals(20f, world.environmentDepth(8, 60, 8), "legacy roof depth, generator " + generator);
            assertEquals(0.04f, world.skyLight(8, 60, 8), "legacy depth attenuation, generator " + generator);
        }
    }

    private static Game scene() {
        Game game = new Game();
        game.world = new World(SEED, World.GEN_URBAN_APARTMENTS);
        game.world.urban.resolveContentRevision(UrbanRevision.CURRENT_CONTENT);
        game.player = new Player(game.world);
        return game;
    }

    private static BuildingPlan apartment(World world) {
        return world.urban.regionPlan(0, 0).parcels().stream()
                .map(p -> world.urban.buildingAt(p.x0(), p.z0()))
                .filter(p -> p.family() == BuildingPlan.Family.APARTMENT && p.storeys() > 1)
                .findFirst().orElseThrow();
    }

    private static BuildingPlan.Room domesticRoom(BuildingPlan building) {
        return building.floors().stream().filter(f -> f.index() == 0).flatMap(f -> f.rooms().stream())
                .filter(r -> r.functions().contains(BuildingPlan.Function.KITCHEN)
                        || r.functions().contains(BuildingPlan.Function.LIVING)).findFirst().orElseThrow();
    }

    private static void loadBuilding(World world, BuildingPlan building) {
        var p = building.parcel();
        for (int cx = Math.floorDiv(p.x0() - 1, Chunk.SX); cx <= Math.floorDiv(p.x1() + 1, Chunk.SX); cx++) {
            for (int cz = Math.floorDiv(p.z0() - 1, Chunk.SZ); cz <= Math.floorDiv(p.z1() + 1, Chunk.SZ); cz++) {
                world.getOrCreateChunk(cx, cz);
            }
        }
    }

    private static void clearAbove(World world, Vec3i p) {
        world.beginBatch();
        for (int y = p.y() + 2; y < Chunk.SY; y++) { world.setBlock(p.x(), y, p.z(), BlockType.AIR, true); }
        world.endBatch();
    }

    private static void stand(Game game, Vec3i p) {
        game.player.pos.set(p.x() + 0.5f, p.y(), p.z() + 0.5f);
    }

    private static void rain(Game game) {
        game.weather.current = game.weather.next = WeatherSystem.Weather.STORM;
        game.weather.blend = 1;
    }
}
