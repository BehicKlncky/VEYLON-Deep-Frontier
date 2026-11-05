package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.entity.PlayerMovementSystem;
import com.veylon.gfx.MaterialRegistry;
import com.veylon.qa.RuntimeBudgetSnapshot;
import com.veylon.save.SaveSystem;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.ChunkMesher;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.DistrictMap;
import com.veylon.world.urban.DistrictPlan;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.StreetNetwork;
import com.veylon.world.urban.UrbanDistrict;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the dense city costs, part by part, measured through production entry points on the
 * economy city (generator 20, seed 20260731): world startup; planning, chunk generation, light
 * lists and CPU meshing per chunk in each district; the streaming half of a frame at walking and
 * flight pace; the simulation half in an apartment, a basement, a transit platform, a stronghold
 * yard, a quarantine growth site and a storm with a building fire; and a long journey's retained
 * chunks, heap and save growth (urban milestone 24).
 *
 * <p>Rendering needs a GL context the test JVM does not have, so GPU work, uploads, draw calls and
 * frame pacing come from the native {@code urban_performance} scene instead. Meshing here is the
 * CPU half of a rebuild ({@link ChunkMesher#tessellate}), with the real material catalog.
 *
 * <p>Wall-clock figures depend on the host, so this runs only in its own opt-in worker
 * ({@code cityPerformanceTest}), apart from the calibrated {@code performanceTest} figures it would
 * otherwise disturb, and under the launcher's 2 GiB heap. Its assertions are coarse targets taken
 * from the 60 fps frame (DESIGN §17 of the urban records), not calibrated budgets.
 */
@Tag("performance")
@Tag("city-performance")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UrbanCityPerformanceTest {
    static final long SEED = 20260731L;
    static final int GENERATOR = World.GEN_URBAN_ECONOMY;
    static final float DT = 1f / 60f;
    /** Above every roof, mast and treetop of the city: flight never collides with a building. */
    static final float CRUISE_Y = 90f;
    /** One frame at 60 fps, in milliseconds. */
    static final double FRAME_MS = 1000.0 / 60.0;

    // ------------------------------------------------------------------
    // 1. Startup
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    void worldStartup() {
        long t = System.nanoTime();
        Game cold = city(GameMode.SURVIVAL);
        double coldMs = ms(t);
        report("startup", "cold newWorld=%.1fms chunks=%d regionPlans=%d buildingPlans=%d heapMiB=%.1f",
                coldMs, cold.world.loadedCount(), cold.world.urban.cachedRegionPlans(),
                cold.world.urban.cachedBuildingPlans(), mib(heapAfterGc()));
        Samples warm = new Samples();
        for (int i = 0; i < 3; i++) {
            t = System.nanoTime();
            city(GameMode.SURVIVAL);
            warm.add(ms(t));
        }
        report("startup", "warm newWorld %s", warm);
    }

    // ------------------------------------------------------------------
    // 2. Planning, generation, light lists and meshing per chunk
    // ------------------------------------------------------------------

    @Test
    @Order(2)
    void planningGenerationLightsAndMeshingPerChunk() {
        MaterialRegistry.initCatalog();
        ChunkMesher mesher = new ChunkMesher();
        Map<UrbanDistrict, int[]> regions = new EnumMap<>(UrbanDistrict.class);
        regions.put(UrbanDistrict.RESIDENTIAL, new int[]{0, 0}); // the spawn region, starter block included
        for (int r = 1; r < 12 && regions.size() < UrbanDistrict.values().length; r++) {
            for (int rx = -r; rx <= r; rx++) {
                for (int rz = -r; rz <= r; rz++) {
                    if (Math.max(Math.abs(rx), Math.abs(rz)) == r) {
                        regions.putIfAbsent(DistrictMap.districtOf(SEED, rx, rz), new int[]{rx, rz});
                    }
                }
            }
        }
        Samples allGen = new Samples(), allMesh = new Samples();
        for (var entry : regions.entrySet()) {
            int rx = entry.getValue()[0], rz = entry.getValue()[1];
            World w = new World(SEED, GENERATOR);
            long t = System.nanoTime();
            DistrictPlan plan = w.urban.regionPlan(rx, rz);
            double regionMs = ms(t);
            t = System.nanoTime();
            int buildings = 0;
            for (ParcelPlan parcel : plan.parcels()) {
                if (w.urban.buildingAt(parcel.x0(), parcel.z0()) != null) { buildings++; }
            }
            double buildingMs = ms(t);
            t = System.nanoTime();
            w.urban.routes(rx, rz);
            w.urban.sites(rx, rz);
            w.urban.vehicles(rx, rz);
            w.urban.ecology(rx, rz);
            double sitesMs = ms(t);
            Samples gen = new Samples(), lights = new Samples(), mesh = new Samples();
            int cx0 = rx * StreetNetwork.REGION_SIZE / Chunk.SX, cz0 = rz * StreetNetwork.REGION_SIZE / Chunk.SZ;
            int span = StreetNetwork.REGION_SIZE / Chunk.SX;
            long emitters = 0, props = 0, vertices = 0;
            for (int cz = cz0; cz < cz0 + span; cz++) {
                for (int cx = cx0; cx < cx0 + span; cx++) {
                    t = System.nanoTime();
                    Chunk c = w.getOrCreateChunk(cx, cz);
                    gen.add(ms(t));
                    t = System.nanoTime();
                    c.rebuildLights(w);
                    lights.add(ms(t));
                    emitters += c.lights.size();
                    props += props(c);
                }
            }
            for (int cz = cz0 + 1; cz < cz0 + span - 1; cz++) {
                for (int cx = cx0 + 1; cx < cx0 + span - 1; cx++) {
                    Chunk c = w.getChunk(cx, cz);
                    t = System.nanoTime();
                    mesher.tessellate(w, c);
                    mesh.add(ms(t));
                    vertices += mesher.opaqueFloats() / 14 + mesher.waterFloats() / 6;
                }
            }
            allGen.addAll(gen);
            allMesh.addAll(mesh);
            report("chunk", "%s region=%d,%d plan=%.2fms buildings=%d in %.2fms sites+routes=%.2fms",
                    entry.getKey().id, rx, rz, regionMs, buildings, buildingMs, sitesMs);
            report("chunk", "%s generate %s", entry.getKey().id, gen);
            report("chunk", "%s lights %s emitters/chunk=%.1f props/chunk=%.1f", entry.getKey().id, lights,
                    emitters / (double) gen.count(), props / (double) gen.count());
            report("chunk", "%s tessellate %s vertices/chunk=%.0f", entry.getKey().id, mesh,
                    vertices / (double) mesh.count());
        }
        report("chunk", "all-districts generate %s", allGen);
        report("chunk", "all-districts tessellate %s", allMesh);
        // Coarse guards, as TickProfileTest keeps: a city chunk generates, and its mesh is built, within
        // one 60 fps frame at the 95th percentile. They catch a cost grown by an order of magnitude; the
        // frame-level streaming target is the streaming test's.
        assertTrue(allGen.pct(0.95) < FRAME_MS, "city chunk generation p95 above a whole frame: " + allGen);
        assertTrue(allMesh.pct(0.95) < FRAME_MS, "city chunk tessellation p95 above a whole frame: " + allMesh);

        // What one retained city chunk costs the heap, arrays, light lists and object overhead together.
        World w = new World(SEED, GENERATOR);
        w.ensureChunks(8, 8, 2, 10_000); // plans first, so only chunks are counted below
        long before = heapAfterGc();
        int added = 0;
        for (int cx = 40; cx < 72; cx++) {
            for (int cz = -16; cz < 16; cz++) {
                w.getOrCreateChunk(cx, cz);
                added++;
            }
        }
        long after = heapAfterGc();
        report("memory", "retained city chunk=%.0f bytes (%d chunks, fixed arrays %d bytes each)",
                (after - before) / (double) added, added, Chunk.SX * Chunk.SY * Chunk.SZ + 2 * 256 * 4);
    }

    // ------------------------------------------------------------------
    // 3. The streaming half of a frame
    // ------------------------------------------------------------------

    @Test
    @Order(3)
    void streamingAtWalkingAndFlightPace() {
        stream("walk", PlayerMovementSystem.WALK_SPEED, 60);
        stream("sprint", PlayerMovementSystem.SPRINT_SPEED, 60);
        stream("flight", PlayerMovementSystem.FLY_FAST_SPEED, 60);
    }

    private void stream(String label, float speed, int seconds) {
        MaterialRegistry.initCatalog();
        ChunkMesher mesher = new ChunkMesher();
        Game g = city(GameMode.CREATIVE);
        g.player.abilities.setFlying(true);
        int radius = g.renderer.renderRadius();
        float x = g.player.pos.x, z = g.player.pos.z;
        Samples sim = new Samples(), gen = new Samples(), select = new Samples(), mesh = new Samples(),
                visible = new Samples(), frame = new Samples();
        int generated = 0, meshed = 0, holes = 0;
        for (int f = 0; f < seconds * 60; f++) {
            x += speed * DT;
            g.player.pos.set(x, CRUISE_Y, z);
            g.player.vel.zero();
            long t0 = System.nanoTime();
            g.playStep(DT, DT);
            long t1 = System.nanoTime();
            generated += g.world.ensureChunks((int) g.player.pos.x, (int) g.player.pos.z,
                    ChunkStreaming.generationRadius(radius), ChunkStreaming.GENERATE_PER_FRAME);
            long t2 = System.nanoTime();
            int pcx = Math.floorDiv((int) g.player.pos.x, 16), pcz = Math.floorDiv((int) g.player.pos.z, 16);
            List<Chunk> dirty = g.renderer.selectDirty(g.world, pcx, pcz, ChunkStreaming.MESH_PER_FRAME);
            long t3 = System.nanoTime();
            for (int i = 0; i < dirty.size(); i++) {
                mesher.tessellate(g.world, dirty.get(i));
                dirty.get(i).dirty = false;
                meshed++;
            }
            long t4 = System.nanoTime();
            g.renderer.selectVisible(g.world, pcx, pcz);
            long t5 = System.nanoTime();
            sim.add((t1 - t0) / 1e6);
            gen.add((t2 - t1) / 1e6);
            select.add((t3 - t2) / 1e6);
            mesh.add((t4 - t3) / 1e6);
            visible.add((t5 - t4) / 1e6);
            frame.add((t5 - t0) / 1e6);
            holes += hole(g.world, pcx, pcz) ? 1 : 0;
        }
        report("stream", "%s speed=%.1f b/s seconds=%d generated=%d meshed=%d holeFrames=%d retained=%d",
                label, speed, seconds, generated, meshed, holes, g.world.loadedCount());
        report("stream", "%s simulation %s", label, sim);
        report("stream", "%s generation %s", label, gen);
        report("stream", "%s dirty-select %s", label, select);
        report("stream", "%s tessellate %s", label, mesh);
        report("stream", "%s visible-select %s", label, visible);
        report("stream", "%s cpu-frame %s", label, frame);
        // Target (milestone 24): streaming and the simulation together keep under half a 60 fps frame at
        // the 99th percentile, leaving the other half to rendering, the interface and the swap.
        assertTrue(frame.pct(0.99) < FRAME_MS / 2, label + " streaming CPU frame p99 above half a frame: " + frame);
        assertTrue(RuntimeBudgetSnapshot.capture(g).withinHardLimits(), RuntimeBudgetSnapshot.capture(g).occupancySummary());
    }

    /** A chunk within two of the eye is missing or unmeshed: a visible hole in a native frame. */
    private static boolean hole(World w, int pcx, int pcz) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Chunk c = w.getChunk(pcx + dx, pcz + dz);
                if (c == null || c.dirty) { return true; }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 4. The simulation half in the dense scenes
    // ------------------------------------------------------------------

    @Test
    @Order(4)
    void simulationInTheDenseScenes() {
        Game g = city(GameMode.SURVIVAL);
        UrbanAtmosphereTourPlan atmosphere = UrbanAtmosphereTourPlan.of(g.world);
        UrbanUndergroundTourPlan underground = UrbanUndergroundTourPlan.of(g.world);
        UrbanStrongholdTourPlan strongholds = UrbanStrongholdTourPlan.of(g.world);
        Map<String, Vec3i> scenes = new LinkedHashMap<>();
        scenes.put("street", atmosphere.streets().get(UrbanDistrict.RESIDENTIAL).stand());
        scenes.put("apartment-kitchen", atmosphere.apartment().get("kitchen").stand());
        scenes.put("apartment-basement", atmosphere.apartment().get("basement").stand());
        scenes.put("transit-platform", underground.station().platform());
        scenes.put("stronghold-yard", strongholds.stronghold().yard());
        scenes.put("quarantine-growth", atmosphere.growth().stand());
        scenes.put("storm-fire", atmosphere.apartment().get("kitchen").stand());
        for (var scene : scenes.entrySet()) {
            Vec3i at = scene.getValue();
            g.world.ensureChunks(at.x(), at.z(), g.renderer.renderRadius(), 10_000);
            pose(g, at);
            boolean storm = scene.getKey().equals("storm-fire");
            g.weather.current = g.weather.next = storm ? Weather.STORM : Weather.CLEAR;
            g.weather.blend = 1f;
            g.weather.changeTimer = 4000f;
            int lit = storm ? igniteAround(g, at, 40) : 0;
            Samples step = new Samples();
            for (int f = 0; f < 13 * 60; f++) {
                keepAlive(g, at);
                long t = System.nanoTime();
                g.playStep(DT, DT);
                if (f >= 3 * 60) { step.add(ms(t)); }
                g.world.ensureChunks((int) g.player.pos.x, (int) g.player.pos.z, g.renderer.renderRadius(), 2);
            }
            Samples fast = new Samples(), medium = new Samples(), slow = new Samples();
            int queries = 0, expansions = 0, deferred = 0;
            for (int i = 0; i < 60; i++) {
                keepAlive(g, at);
                long t = System.nanoTime();
                g.fastTick(SimulationScheduler.FAST_DT);
                fast.add(ms(t));
                queries += g.world.navigation.budget.queries();
                expansions += g.world.navigation.budget.expansions();
                deferred += g.world.navigation.budget.deferred();
                if (i % 10 == 0) {
                    t = System.nanoTime();
                    g.mediumTick(SimulationScheduler.MEDIUM_DT);
                    medium.add(ms(t));
                }
                if (i % 30 == 0) {
                    t = System.nanoTime();
                    g.slowTick(SimulationScheduler.SLOW_DT);
                    slow.add(ms(t));
                }
            }
            RuntimeBudgetSnapshot snap = RuntimeBudgetSnapshot.capture(g);
            report("scene", "%s at=%s npcs=%d infected=%d creatures=%d aliens=%d fires=%d lit=%d particles=%d"
                            + " noise=%d sounds=%d emitters3x3=%d",
                    scene.getKey(), at, g.entities.npcCount(), snap.npcs().infected(), g.entities.creatureCount(),
                    snap.activeAliens(), g.fire.count(), lit, g.particles.count, snap.noiseEvents(),
                    soundsPlayed(g), emitters(g.world, at));
            report("scene", "%s frame-step %s", scene.getKey(), step);
            report("scene", "%s fast-tick %s paths={queries=%d,expanded=%d,deferred=%d} over 60 ticks",
                    scene.getKey(), fast, queries, expansions, deferred);
            report("scene", "%s medium-tick %s slow-tick %s", scene.getKey(), medium, slow);
            assertTrue(snap.withinHardLimits(), snap.occupancySummary());
        }
    }

    private static void pose(Game g, Vec3i at) {
        g.player.pos.set(at.x() + .5f, at.y() + .001f, at.z() + .5f);
        g.player.vel.zero();
        g.player.onGround = true;
    }

    /** Staged: the measurement wants the pressure of a pursuit, not its outcome. */
    private static void keepAlive(Game g, Vec3i at) {
        g.player.health = g.player.maxHealth;
        g.appState = Game.AppState.PLAYING;
        if (g.player.pos.distanceSquared(at.x() + .5f, at.y(), at.z() + .5f) > 64) { pose(g, at); }
    }

    private static int igniteAround(Game g, Vec3i at, int wanted) {
        int lit = 0;
        for (int dy = -1; dy <= 3 && lit < wanted; dy++) {
            for (int dx = -8; dx <= 8 && lit < wanted; dx++) {
                for (int dz = -8; dz <= 8 && lit < wanted; dz++) {
                    int x = at.x() + dx, y = at.y() + dy, z = at.z() + dz;
                    if (g.world.getBlock(x, y, z).flammable && g.fire.ignite(g, x, y, z)) { lit++; }
                }
            }
        }
        return lit;
    }

    private static int soundsPlayed(Game g) {
        int n = 0;
        for (int p : g.ambience.soundscape.played) { n += p; }
        return n;
    }

    private static int emitters(World w, Vec3i at) {
        int n = 0, cx = Math.floorDiv(at.x(), 16), cz = Math.floorDiv(at.z(), 16);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Chunk c = w.getChunk(cx + dx, cz + dz);
                n += c == null ? 0 : c.lights.size();
            }
        }
        return n;
    }

    // ------------------------------------------------------------------
    // 5. A long journey: retained chunks, heap, ledgers and the save
    // ------------------------------------------------------------------

    @Test
    @Order(5)
    void longTravelRetentionHeapAndSaveGrowth(@TempDir Path dir) throws Exception {
        Game g = city(GameMode.CREATIVE);
        g.player.abilities.setFlying(true);
        int radius = g.renderer.renderRadius();
        float x = g.player.pos.x, z = g.player.pos.z;
        float speed = PlayerMovementSystem.FLY_FAST_SPEED;
        Path save = dir.resolve("travel.sav");
        report("travel", "start retained=%d heapMiB=%.1f speed=%.1f b/s radius=%d", g.world.loadedCount(),
                mib(heapAfterGc()), speed, radius);
        int searched = 0;
        for (int km = 1; km <= 8; km++) {
            Samples frame = new Samples(), scans = new Samples();
            int frames = Math.round(1000f / speed / DT);
            for (int f = 0; f < frames; f++) {
                x += speed * DT;
                g.player.pos.set(x, CRUISE_Y, z);
                g.player.vel.zero();
                long t0 = System.nanoTime();
                g.playStep(DT, DT);
                g.world.ensureChunks((int) g.player.pos.x, (int) g.player.pos.z,
                        ChunkStreaming.generationRadius(radius), ChunkStreaming.GENERATE_PER_FRAME);
                long t1 = System.nanoTime();
                int pcx = Math.floorDiv((int) g.player.pos.x, 16), pcz = Math.floorDiv((int) g.player.pos.z, 16);
                g.renderer.selectDirty(g.world, pcx, pcz, ChunkStreaming.MESH_PER_FRAME);
                g.renderer.selectVisible(g.world, pcx, pcz);
                long t2 = System.nanoTime();
                frame.add((t1 - t0) / 1e6);
                scans.add((t2 - t1) / 1e6);
            }
            searched += searchNearestBuilding(g, (int) x, (int) z);
            long t = System.nanoTime();
            assertTrue(SaveSystem.save(g, save), "the travelled world saves");
            double saveMs = ms(t);
            long bytes = Files.size(save);
            Game loaded = new Game();
            t = System.nanoTime();
            assertTrue(SaveSystem.load(loaded, save), "the travelled world loads");
            double loadMs = ms(t);
            int loadedChunks = loaded.world.loadedCount();
            loaded = null;
            RuntimeBudgetSnapshot snap = RuntimeBudgetSnapshot.capture(g);
            report("travel", "km=%d retained=%d heapMiB=%.1f regionPlans=%d buildingPlans=%d encounterRecords=%d"
                            + " sites=%d visits=%d containers=%d edits=%d fixtures=%d searched=%d",
                    km, g.world.loadedCount(), mib(heapAfterGc()), snap.urbanRegionPlans(), snap.urbanBuildingPlans(),
                    snap.urbanEncounterRecords(), snap.urbanSiteRecords(), snap.visitedPlaces(),
                    g.world.urban.containers.size(), g.world.changedBlocks.size(), g.world.fixtures.size(), searched);
            report("travel", "km=%d save=%d bytes in %.1fms load=%.1fms (%d chunks regenerated) step+stream %s"
                    + " chunk-scans %s", km, bytes, saveMs, loadMs, loadedChunks, frame, scans);
            assertTrue(snap.withinHardLimits(), snap.occupancySummary());
        }
    }

    /**
     * Touches every container of the building nearest the eye through the production first search
     * ({@code UrbanContainers.touch}), as a survivor emptying one building a kilometre would.
     */
    private static int searchNearestBuilding(Game g, int x, int z) {
        for (int r = 0; r <= 96; r += 4) {
            for (int[] d : new int[][]{{r, 0}, {-r, 0}, {0, r}, {0, -r}, {r, r}, {-r, -r}, {r, -r}, {-r, r}}) {
                BuildingPlan b = g.world.urban.buildingAt(x + d[0], z + d[1]);
                if (b == null) { continue; }
                g.world.ensureChunks(x + d[0], z + d[1], 2, 10_000);
                int n = 0;
                for (BuildingPlan.Fixture f : b.fixtures()) {
                    if (f.kind() == BuildingPlan.FixtureKind.CONTAINER && g.world.isColumnLoaded(f.position().x(), f.position().z())
                            && g.world.urban.containers.touch(g.world, f.position()) != null) { n++; }
                }
                if (n > 0) { return n; }
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    static Game city(GameMode mode) {
        Game g = new Game();
        g.newWorld(SEED, true, GENERATOR, mode);
        g.appState = Game.AppState.PLAYING;
        return g;
    }

    private static int props(Chunk c) {
        int n = 0;
        for (int y = 0; y < Chunk.SY; y++) {
            for (int lz = 0; lz < Chunk.SZ; lz++) {
                for (int lx = 0; lx < Chunk.SX; lx++) {
                    BlockType t = c.get(lx, y, lz);
                    if (t != BlockType.AIR && t.shape != BlockType.Shape.CUBE && t.shape != BlockType.Shape.LIQUID) { n++; }
                }
            }
        }
        return n;
    }

    static double ms(long start) { return (System.nanoTime() - start) / 1e6; }

    static double mib(long bytes) { return bytes / (1024.0 * 1024.0); }

    static long heapAfterGc() {
        for (int i = 0; i < 3; i++) { System.gc(); }
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    static void report(String area, String format, Object... args) {
        System.out.println("[city-perf] " + area + " " + String.format(Locale.ROOT, format, args));
    }

    /** Millisecond samples, summarised as the native reports are: mean, p50, p95, p99 and max. */
    static final class Samples {
        private double[] v = new double[256];
        private int n;

        void add(double ms) {
            if (n == v.length) { v = Arrays.copyOf(v, n * 2); }
            v[n++] = ms;
        }

        void addAll(Samples other) {
            for (int i = 0; i < other.n; i++) { add(other.v[i]); }
        }

        int count() { return n; }

        double pct(double fraction) {
            if (n == 0) { return 0; }
            double[] sorted = Arrays.copyOf(v, n);
            Arrays.sort(sorted);
            return sorted[Math.max(0, Math.min(n - 1, (int) Math.ceil(fraction * n) - 1))];
        }

        @Override
        public String toString() {
            double sum = 0, max = 0;
            for (int i = 0; i < n; i++) { sum += v[i]; max = Math.max(max, v[i]); }
            return String.format(Locale.ROOT, "{n=%d mean=%.3f p50=%.3f p95=%.3f p99=%.3f max=%.3f}",
                    n, n == 0 ? 0 : sum / n, pct(.5), pct(.95), pct(.99), max);
        }
    }
}
