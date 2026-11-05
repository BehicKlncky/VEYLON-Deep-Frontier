package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.entity.PlayerMovementSystem;
import com.veylon.save.SaveSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.Fixtures;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A generated city's far chunks are dropped and come back identical (urban milestone 24, D-241):
 * with every recorded edit, opened door and searched container, with no stale reference left to
 * them, and with nothing lost or changed in what a save holds, however the journey ran; legacy
 * worlds keep every chunk.
 */
class ChunkRetentionTest {
    private static final long SEED = 20260731L;
    private static final float DT = 1f / 60f;
    private static final float CRUISE_Y = 90f;

    @Test
    void aDroppedCityChunkComesBackIdenticalWithItsEditsDoorsAndContainers() {
        Game g = city(GameMode.SURVIVAL);
        BuildingPlan flats = UrbanApartmentQaScene.selectBuilding(g.world);
        Vec3i at = flats.approaches().getFirst();
        g.world.ensureChunks(at.x(), at.z(), 3, 10_000);
        // Changes a survivor leaves: a forced door, an opened one, a broken wall and searched cupboards.
        int doors = 0, searched = 0;
        for (BuildingPlan.Fixture f : flats.fixtures()) {
            Vec3i p = f.position();
            if (f.kind() == BuildingPlan.FixtureKind.DOOR && Fixtures.isClosedDoor(g.world.getBlock(p.x(), p.y(), p.z()))) {
                if (doors == 0) {
                    g.world.setBlock(p.x(), p.y(), p.z(), BlockType.DEBRIS, true);
                } else if (doors == 1) {
                    g.player.pos.set(p.x() + .5f, p.y(), p.z() + .5f);
                    FixtureActions.interact(g, p.x(), p.y(), p.z());
                }
                doors++;
            }
            if (f.kind() == BuildingPlan.FixtureKind.CONTAINER && searched < 3
                    && g.world.urban.containers.touch(g.world, p) != null) { searched++; }
        }
        g.world.setBlock(at.x(), at.y() + 1, at.z() + 1, BlockType.AIR, true);
        assertTrue(doors >= 2 && searched >= 2, "the flats have doors and cupboards to change");
        assertFalse(g.world.fixtures.isEmpty(), "a door was opened");

        Map<Long, Snapshot> before = snapshots(g.world, at, 2);
        int edits = g.world.changedBlocks.size(), fixtures = g.world.fixtures.size(), crates = g.world.crateContents.size();
        for (long key : before.keySet()) {
            Snapshot s = before.get(key);
            assertTrue(g.world.unloadChunk(s.cx, s.cz));
            assertNull(g.world.getChunk(s.cx, s.cz), "no lookup cache still answers with the dropped chunk");
            assertFalse(g.world.isColumnLoaded(s.cx * 16, s.cz * 16));
        }
        assertEquals(BlockType.AIR, g.world.getBlock(at.x(), at.y(), at.z()), "an unloaded column reads as air");
        assertEquals(edits, g.world.changedBlocks.size(), "dropping a chunk keeps every recorded edit");
        assertEquals(fixtures, g.world.fixtures.size(), "and every saved door state");
        assertEquals(crates, g.world.crateContents.size(), "and every container's contents");

        g.world.ensureChunks(at.x(), at.z(), 3, 10_000);
        Map<Long, Snapshot> after = snapshots(g.world, at, 2);
        for (long key : before.keySet()) {
            assertEquals(before.get(key), after.get(key), "chunk " + before.get(key).cx + "," + before.get(key).cz);
        }
        assertEquals(0, g.world.pendingGenerationChunkCount(), "a city chunk queues no edit into a neighbour");
    }

    @Test
    void aLongJourneyStaysBoundedAndChangesNothingASaveHolds(@TempDir Path dir) throws Exception {
        Game kept = city(GameMode.CREATIVE), dropped = city(GameMode.CREATIVE);
        Vec3i fire = prepare(kept);
        assertEquals(fire, prepare(dropped));
        long fireChunk = World.key(Math.floorDiv(fire.x(), 16), Math.floorDiv(fire.z(), 16));
        int peak = 0;
        boolean firePinned = true;
        java.util.Set<Long> home = snapshots(kept.world, fire, 4).keySet(), droppedHome = new java.util.HashSet<>();
        float startX = kept.player.pos.x;
        int frames = Math.round(3_000f / PlayerMovementSystem.FLY_FAST_SPEED / DT);
        for (int f = 0; f < 2 * frames; f++) {
            float x = startX + PlayerMovementSystem.FLY_FAST_SPEED * DT * (f < frames ? f : 2 * frames - f);
            for (Game g : List.of(kept, dropped)) {
                g.player.pos.set(x, CRUISE_Y, g.player.pos.z);
                g.player.vel.zero();
                g.playStep(DT, DT);
                int made = g.world.ensureChunks((int) g.player.pos.x, (int) g.player.pos.z,
                        ChunkStreaming.generationRadius(g.renderer.renderRadius()), ChunkStreaming.GENERATE_PER_FRAME);
                if (g == dropped && made > 0) { g.streaming.retention.afterStreaming(); }
            }
            peak = Math.max(peak, dropped.world.loadedCount());
            firePinned &= dropped.world.getChunk(Math.floorDiv(fire.x(), 16), Math.floorDiv(fire.z(), 16)) != null;
            if (dropped.world.chunkUnloads() > 0) {
                for (long key : home) {
                    if (dropped.world.getChunk((int) (key >> 32), (int) key) == null) { droppedHome.add(key); }
                }
            }
        }
        assertTrue(kept.world.loadedCount() > ChunkRetention.RETAIN_LIMIT + 1_000,
                "the journey is long enough to matter: " + kept.world.loadedCount());
        assertTrue(dropped.streaming.retention.evicted > 1_000, "the far chunks were dropped: " + dropped.streaming.retention.evicted);
        assertTrue(peak <= ChunkRetention.RETAIN_LIMIT + ChunkStreaming.GENERATE_PER_FRAME,
                "retained chunks stay bounded on the way: " + peak);
        assertTrue(firePinned, "the burning campfire's chunk was never dropped: " + fireChunk);
        assertTrue(dropped.world.campfireFuel.get(fire) > 0, "and it still burns");
        assertEquals(kept.world.campfireFuel, dropped.world.campfireFuel, "burning on the same clock");

        // Home again, regenerated where it was dropped: the same blocks, lights and heights as the twin's.
        for (Game g : List.of(kept, dropped)) { g.world.ensureChunks(fire.x(), fire.z(), 5, 10_000); }
        Map<Long, Snapshot> twin = snapshots(kept.world, fire, 4), returned = snapshots(dropped.world, fire, 4);
        assertEquals(twin.keySet(), returned.keySet());
        for (long key : twin.keySet()) {
            assertEquals(twin.get(key), returned.get(key), "chunk " + twin.get(key).cx + "," + twin.get(key).cz);
        }
        assertTrue(droppedHome.size() > 9, "most of home was dropped on the way and regenerated: " + droppedHome.size());
        // Nothing a save holds differs from the twin that kept every chunk, byte for byte.
        Path a = dir.resolve("kept.sav"), b = dir.resolve("dropped.sav");
        assertTrue(SaveSystem.save(kept, a));
        assertTrue(SaveSystem.save(dropped, b));
        assertEquals(-1L, Files.mismatch(a, b), "the two saves are identical");
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, b), "the save of a world that dropped chunks loads");
        assertEquals(kept.world.changedBlocks, loaded.world.changedBlocks);
    }

    @Test
    void legacyWorldsKeepEveryChunkTheyGenerate() {
        Game g = new Game();
        g.newWorld(SEED, true, World.GEN_CAVE_IDENTITIES, GameMode.CREATIVE);
        assertFalse(g.world.evictsChunks());
        assertThrows(IllegalStateException.class, () -> g.world.unloadChunk(0, 0));
        for (int i = 0; i < 40; i++) { g.world.ensureChunks(i * 256, 0, 6, 10_000); }
        int held = g.world.loadedCount();
        assertTrue(held > ChunkRetention.RETAIN_LIMIT, "beyond the city's limit: " + held);
        g.streaming.retention.afterStreaming();
        assertEquals(held, g.world.loadedCount(), "a legacy world drops nothing");
        assertEquals(0, g.world.chunkUnloads());
    }

    // ------------------------------------------------------------------

    private static Game city(GameMode mode) {
        Game g = new Game();
        g.newWorld(SEED, true, World.GEN_URBAN_ECONOMY, mode);
        g.appState = Game.AppState.PLAYING;
        return g;
    }

    /** The same changes in either twin: cupboards searched and a campfire lit, both near the spawn. */
    private static Vec3i prepare(Game g) {
        BuildingPlan flats = UrbanApartmentQaScene.selectBuilding(g.world);
        g.world.ensureChunks(flats.approaches().getFirst().x(), flats.approaches().getFirst().z(), 3, 10_000);
        for (BuildingPlan.Fixture f : flats.fixtures()) {
            if (f.kind() == BuildingPlan.FixtureKind.CONTAINER) { g.world.urban.containers.touch(g.world, f.position()); }
        }
        Vec3i street = flats.approaches().getFirst();
        Vec3i fire = street.offset(0, 0, 0);
        for (int dx = 0; dx < 8 && g.world.getBlock(fire.x(), fire.y(), fire.z()) != BlockType.AIR; dx++) {
            fire = street.offset(dx, 0, 0);
        }
        g.world.setBlock(fire.x(), fire.y(), fire.z(), BlockType.CAMPFIRE, true);
        g.world.campfireFuel.put(fire, 900f);
        g.world.ensureChunks(fire.x(), fire.z(), 5, 10_000);
        return fire;
    }

    /** One chunk's blocks, heights, rain bound and lights. */
    private record Snapshot(int cx, int cz, byte[] blocks, int[] heights, int[] rain, List<String> lights) {
        @Override public boolean equals(Object o) {
            return o instanceof Snapshot s && s.cx == cx && s.cz == cz && Arrays.equals(s.blocks, blocks)
                    && Arrays.equals(s.heights, heights) && Arrays.equals(s.rain, rain) && s.lights.equals(lights);
        }
        @Override public int hashCode() { return Arrays.hashCode(blocks); }
        @Override public String toString() { return "Snapshot[" + cx + "," + cz + "]"; }
    }

    private static Map<Long, Snapshot> snapshots(World w, Vec3i at, int radius) {
        Map<Long, Snapshot> out = new LinkedHashMap<>();
        int cx0 = Math.floorDiv(at.x(), 16), cz0 = Math.floorDiv(at.z(), 16);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Chunk c = w.getChunk(cx0 + dx, cz0 + dz);
                assertNotNull(c, "chunk " + (cx0 + dx) + "," + (cz0 + dz) + " is loaded");
                byte[] blocks = new byte[Chunk.SX * Chunk.SY * Chunk.SZ];
                int[] heights = new int[Chunk.SX * Chunk.SZ], rain = new int[Chunk.SX * Chunk.SZ];
                int i = 0;
                for (int y = 0; y < Chunk.SY; y++) {
                    for (int z = 0; z < Chunk.SZ; z++) {
                        for (int x = 0; x < Chunk.SX; x++) { blocks[i++] = c.get(x, y, z).id(); }
                    }
                }
                for (int x = 0; x < Chunk.SX; x++) {
                    for (int z = 0; z < Chunk.SZ; z++) {
                        heights[z * Chunk.SX + x] = c.height(x, z);
                        rain[z * Chunk.SX + x] = c.precipitationHeight(x, z);
                    }
                }
                List<String> lights = new ArrayList<>();
                for (int[] l : c.lights) { lights.add(Arrays.toString(l)); }
                lights.sort(null);
                out.put(World.key(c.cx, c.cz), new Snapshot(c.cx, c.cz, blocks, heights, rain, lights));
            }
        }
        return out;
    }
}
