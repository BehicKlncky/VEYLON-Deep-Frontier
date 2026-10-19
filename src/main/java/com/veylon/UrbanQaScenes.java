package com.veylon;

import com.veylon.entity.PlayerMovementSystem;
import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.world.Chunk;
import com.veylon.world.World;
import com.veylon.world.urban.CityPlanDiagnostics;
import com.veylon.world.urban.DistrictPlan;
import com.veylon.world.urban.StreetNetwork;
import com.veylon.world.urban.UrbanWorldState;

import java.util.Locale;

/**
 * Urban QA scenes ({@code VEYLON_SCENE=urban_streets},
 * {@code urban_intersection}, {@code urban_seam_walk}, and the package-04
 * {@code urban_fixtures} and {@code urban_fixtures_night} of
 * {@link UrbanFixtureQaScene}), staged on a real
 * street-network world from {@code VEYLON_GENERATOR=urban-dev}. They read the
 * plan through its production queries, never the column top, and never touch
 * an outcome random stream. Each prints the plan diagnostics of the four
 * regions that meet at the origin; the walk also prints a pass or fail line.
 */
final class UrbanQaScenes {
    /** The walk starts after the first frames have streamed and ends before its report. */
    private static final double WALK_START_SECONDS = 2.0;
    private static final double WALK_REPORT_SECONDS = 24.0;
    /** The walk runs from this far west of the region seam at x 0 to as far east of it. */
    private static final int WALK_HALF_LENGTH = 40;
    private static final float EAST_YAW = 90f;
    private static final float NORTH_YAW = 0f;
    private static final float SOUTH_EAST_YAW = 135f;
    private static final float FEET_TOLERANCE = 0.05f;

    private final Game game;
    /** Package 04: doors, windows and stairs ({@code urban_fixtures}, {@code urban_fixtures_night}). */
    private final UrbanFixtureQaScene fixtures;
    private final UrbanApartmentQaScene apartments;
    private final UrbanLootQaScene loot;
    private final UrbanInfectedQaScene infected;
    private final UrbanNavigationQaScene navigation;
    /** Package 11: the playable slice route ({@code urban_slice}). */
    private final UrbanSliceQaScene slice;
    /** Package 12: one building of each commercial and civic family ({@code urban_catalogue}). */
    private final UrbanCatalogueQaScene catalogue;
    /** Package 13: a walked district transition and every district by day, night and fog ({@code urban_districts}). */
    private final UrbanDistrictQaScene districts;
    private final PlayerMovementSystem.Command walk = new PlayerMovementSystem.Command();
    private boolean holding;
    private float holdX;
    private float holdY;
    private float holdZ;
    private float holdYaw;
    private float holdPitch;

    // Seam walk observations.
    private boolean walking;
    private boolean reported;
    private double lastSample = -1;
    private int walkZ;
    private int frames;
    private int chunkCrossings;
    private int lastChunkX;
    private boolean regionCrossed;
    private boolean enteredUnloaded;
    private int blockedFrames;
    private float minFeet = Float.MAX_VALUE;
    private float maxFeet = -Float.MAX_VALUE;
    private boolean starterManual;
    private int lastStarterReport = -1;

    UrbanQaScenes(Game game) {
        this.game = game;
        this.fixtures = new UrbanFixtureQaScene(game);
        this.apartments = new UrbanApartmentQaScene(game);
        this.loot = new UrbanLootQaScene(game);
        this.infected = new UrbanInfectedQaScene(game);
        this.navigation = new UrbanNavigationQaScene(game);
        this.slice = new UrbanSliceQaScene(game);
        this.catalogue = new UrbanCatalogueQaScene(game);
        this.districts = new UrbanDistrictQaScene(game);
    }

    /** Stages one of the urban scenes; inert, with a notice, outside a street-network world. */
    void stage(String scene) {
        World world = game.world;
        if (scene.equals("urban_starter_manual")) {
            if (world.urban == null || !world.urban.hasStarter()) {
                throw new IllegalStateException("starter QA requires the urban starter generator");
            }
            starterManual = true;
            System.out.println("[urban-starter] evidence=normal-controls-only seed=" + world.seed
                    + " refuge=" + world.urban.refugeBuilding + " spawn=" + game.player.pos);
            return;
        }
        if (scene.equals("urban_slice")) {
            slice.stage(); // a normal fresh session: its own clock, weather and events
            return;
        }
        if (scene.equals("urban_catalogue")) {
            catalogue.stage(); // a normal session too: only poses between buildings and the kit are staged
            return;
        }
        if (scene.equals("urban_districts")) {
            districts.stage(); // a normal session: only poses between stops, the clock, weather and spear are staged
            return;
        }
        if (!world.isUrban() || !world.urban.hasCityPlan()) {
            System.out.println("[urban-qa] " + scene
                    + " needs VEYLON_GENERATOR=urban-dev (the street network); scene inactive");
            return;
        }
        game.time.totalMinutes = 12 * 60;
        game.weather.current = game.weather.next = Weather.CLEAR;
        game.weather.blend = 1;
        game.weather.changeTimer = 10_000;
        reportPlans(world);
        DistrictPlan origin = world.urban.regionPlan(0, 0);
        switch (scene) {
            case "urban_streets" -> {
                // On the corner sidewalk of two local streets, looking north up
                // one of them to the avenue on the region edge.
                int x = origin.northSouthLine(1) + 2;
                int z = origin.eastWestLine(1) + StreetNetwork.LOCAL_HALF_WIDTH + 1;
                hold(world, x + 0.5f, z + 0.5f, 0, NORTH_YAW, 6f);
            }
            case "urban_intersection" ->
                // Above the corner where regions (-1,-1), (0,-1), (-1,0) and (0,0) meet.
                hold(world, -21.5f, -21.5f, 20, SOUTH_EAST_YAW, 34f);
            case "urban_seam_walk" -> stageSeamWalk(world, origin);
            case "urban_fixtures", "urban_fixtures_night" -> fixtures.stage(scene);
            case "urban_apartments", "urban_apartments_night", "urban_apartments_manual",
                 "urban_apartments_manual_night", "urban_apartments_lit_night" -> apartments.stage(scene);
            case "urban_loot" -> loot.stage();
            case "urban_infected_street", "urban_infected_apartment" -> infected.stage(scene.endsWith("apartment"));
            case "urban_navigation" -> navigation.stage();
            default -> System.out.println("[urban-qa] unknown urban scene '" + scene + "'");
        }
    }

    private void reportPlans(World world) {
        for (int rz = -1; rz <= 0; rz++) {
            for (int rx = -1; rx <= 0; rx++) {
                System.out.println("[urban-qa] plan "
                        + CityPlanDiagnostics.inspect(world.seed,
                        world.urban.regionPlan(rx, rz).layoutRevision, rx, rz).summary());
            }
        }
        int[] spawn = world.urban.spawnColumn();
        System.out.println("[urban-qa] spawn=" + spawn[0] + "," + spawn[1]
                + " surface=" + world.urban.surfaceAt(spawn[0], spawn[1])
                + " groundY=" + world.urban.groundY(spawn[0], spawn[1]));
    }

    private void hold(World world, float x, float z, float aboveGround, float yaw, float pitch) {
        UrbanWorldState urban = world.urban;
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        world.ensureChunks(bx, bz, 6, 10_000);
        holdX = x;
        holdZ = z;
        holdY = urban.groundY(bx, bz) + 1 + aboveGround;
        holdYaw = yaw;
        holdPitch = pitch;
        holding = true;
        apply();
        System.out.println("[urban-qa] camera at " + x + "," + holdY + "," + z
                + " surface=" + urban.surfaceAt(bx, bz) + " district=" + urban.districtAt(bx, bz).id);
    }

    private void apply() {
        game.player.pos.set(holdX, holdY, holdZ);
        game.player.vel.zero();
        game.camera.yaw = holdYaw;
        game.camera.pitch = holdPitch;
    }

    private void stageSeamWalk(World world, DistrictPlan origin) {
        // An east-west local street keeps its z across every region column, so
        // its roadway centre runs straight through the avenue on the seam at x 0.
        walkZ = origin.eastWestLine(1);
        world.ensureChunks(0, walkZ, 6, 10_000);
        game.player.pos.set(-WALK_HALF_LENGTH + 0.5f,
                world.urban.groundY(-WALK_HALF_LENGTH, walkZ) + 1, walkZ + 0.5f);
        game.player.vel.zero();
        game.camera.yaw = EAST_YAW;
        game.camera.pitch = 4f;
        walking = true;
        lastChunkX = Math.floorDiv(-WALK_HALF_LENGTH, Chunk.SX);
        System.out.println("[urban-qa] seam walk along z=" + walkZ + " from x=" + -WALK_HALF_LENGTH
                + " to x=" + WALK_HALF_LENGTH + " across the region seam at x=0 (seam surfaces "
                + world.urban.surfaceAt(-1, walkZ) + "|" + world.urban.surfaceAt(0, walkZ) + ")");
    }

    UrbanSliceQaScene slice() { return slice; }
    UrbanCatalogueQaScene catalogue() { return catalogue; }
    UrbanDistrictQaScene districts() { return districts; }

    /** Per-frame hook, before streaming and rendering; inert unless a scene is staged. */
    void update(double elapsed) {
        if (starterManual && (int) (elapsed / 5) != lastStarterReport) {
            lastStarterReport = (int) (elapsed / 5);
            System.out.println("[urban-starter] t=" + (int) elapsed + " pos=" + game.player.pos
                    + " yaw=" + game.camera.yaw + " pitch=" + game.camera.pitch + " screen=" + game.uiMode
                    + " searched=" + game.world.urban.containers.size()
                    + " food=" + (game.player.inventory.count(com.veylon.item.ItemType.DRIED_BERRY)
                    + game.player.inventory.count(com.veylon.item.ItemType.DRIED_MEAT))
                    + " water=" + game.player.inventory.count(com.veylon.item.ItemType.WATERSKIN_CLEAN)
                    + " upgrade=" + game.player.inventory.count(com.veylon.item.ItemType.IRON_SPEAR));
        }
        fixtures.update(elapsed);
        apartments.update(elapsed);
        loot.update(elapsed);
        infected.update(elapsed);
        navigation.update(elapsed);
        slice.update(elapsed);
        catalogue.update(elapsed);
        districts.update(elapsed);
        if (holding) {
            apply();
        }
        if (!walking || reported) {
            return;
        }
        if (elapsed >= WALK_REPORT_SECONDS) {
            reported = true;
            report();
            return;
        }
        float dt = lastSample < 0 ? 0f : (float) Math.min(0.1, elapsed - lastSample);
        lastSample = elapsed;
        if (elapsed < WALK_START_SECONDS || game.player.pos.x >= WALK_HALF_LENGTH) {
            return;
        }
        game.camera.yaw = EAST_YAW;
        walk.set(EAST_YAW, true, false, false, false, false, false, false, false);
        game.applyMovementCommand(walk, dt);
        observe();
    }

    private void observe() {
        World world = game.world;
        frames++;
        int bx = (int) Math.floor(game.player.pos.x);
        int bz = (int) Math.floor(game.player.pos.z);
        float expectedFeet = world.urban.groundY(bx, bz) + 1;
        float feet = game.player.pos.y - expectedFeet;
        minFeet = Math.min(minFeet, feet);
        maxFeet = Math.max(maxFeet, feet);
        enteredUnloaded |= !world.isColumnLoaded(bx, bz);
        blockedFrames += game.player.horizontalCollision ? 1 : 0;
        int cx = Math.floorDiv(bx, Chunk.SX);
        if (cx != lastChunkX) {
            chunkCrossings++;
            lastChunkX = cx;
        }
        regionCrossed |= StreetNetwork.regionOf(bx) == 0 && bx >= 0;
    }

    private void report() {
        float endX = game.player.pos.x;
        boolean reachedEnd = endX >= WALK_HALF_LENGTH - 1;
        boolean level = frames > 0 && Math.abs(minFeet) < FEET_TOLERANCE && Math.abs(maxFeet) < FEET_TOLERANCE;
        boolean pass = reachedEnd && regionCrossed && chunkCrossings >= 4 && blockedFrames == 0
                && !enteredUnloaded && level;
        System.out.printf(Locale.ROOT, "[urban-qa] seam-walk frames=%d endX=%.2f z=%.2f chunkCrossings=%d"
                        + " regionCrossed=%s blockedFrames=%d enteredUnloaded=%s feetOffset=%.3f..%.3f"
                        + " loadedChunks=%d pass=%s%n",
                frames, endX, game.player.pos.z, chunkCrossings, regionCrossed, blockedFrames,
                enteredUnloaded, minFeet, maxFeet, game.world.loadedCount(), pass);
    }
}
