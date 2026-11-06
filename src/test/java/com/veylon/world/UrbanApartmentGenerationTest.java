package com.veylon.world;

import com.veylon.util.Vec3i;
import com.veylon.world.urban.AccessibilityValidator;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.BuildingPlanDiagnostics;
import com.veylon.world.urban.DistrictPlan;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.PlanVolume;
import com.veylon.world.urban.UrbanBuildingStamper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Actual generated blocks, including every room of every parcel in the tested regions. */
class UrbanApartmentGenerationTest {
    private static final long[] SEEDS = {20260731, -4242, 7, 0, 987654321, -9000000000000000000L};

    @Test
    void allGeneratedRoomsAndReservationsHaveProductionEntryAndExitProof() {
        for (long seed : SEEDS) {
            World world = new World(seed, World.GEN_URBAN_APARTMENTS);
            DistrictPlan district = world.urban.regionPlan(0, 0);
            // One complete 16x16 region deliberately exceeds the usual 15x15
            // matrix window, so no boundary parcel's rooms are omitted.
            for (int cz = 0; cz < 16; cz++) {
                for (int cx = 0; cx < 16; cx++) { world.getOrCreateChunk(cx, cz); }
            }
            for (ParcelPlan parcel : district.parcels()) {
                BuildingPlan plan = world.urban.buildingAt(parcel.x0(), parcel.z0());
                PlanVolume v = plan.voxels();
                for (int y = v.minY; y <= v.maxY; y++) {
                    for (int z = v.z0; z < v.z0 + v.depth; z++) {
                        for (int x = v.x0; x < v.x0 + v.width; x++) {
                            assertTrue(world.isColumnLoaded(x, z));
                            BlockType block = world.getBlock(x, y, z);
                            if (parcel.contains(x, z)) {
                                assertEquals(v.getBlock(x, y, z), block, "owned slice differs from proposal");
                            }
                        }
                    }
                }
                AccessibilityValidator.Report result = AccessibilityValidator.inspectGenerated(plan, district, world);
                assertTrue(result.ok(), () -> "seed=" + seed + " parcel=" + parcel.id() + " " + result.summary());
                assertTrue(result.cellsExamined() <= PlanVolume.MAX_CELLS);
                assertTrue(result.transitions() <= AccessibilityValidator.MAX_TRANSITIONS);
            }
            assertTrue(world.fixtures.isEmpty(), "generated defaults never consume touched-state capacity");
            assertTrue(world.crateContents.isEmpty(), "reservations do not roll starter loot");
            assertTrue(world.changedBlocks.isEmpty());
            assertEquals(0, world.pendingGenerationEditCount());
        }
    }

    @Test
    void chunkOrdersPreserveBlocksHeightsLightsAndStableReservations() {
        for (long seed : SEEDS) {
            World source = new World(seed, World.GEN_URBAN_APARTMENTS);
            ParcelPlan parcel = source.urban.regionPlan(0, 0).refuge();
            compareOrders(seed, Math.floorDiv(parcel.x0(), 16) - 1, Math.floorDiv(parcel.z0(), 16) - 1);
        }
        compareOrders(-4242, -3, -3);
        compareOrders(7, 65534, -65538);
        compareOrders(-9000000000000000000L, -65554, 65518);
    }

    private static void compareOrders(long seed, int x0, int z0) {
        List<int[]> chunks = new ArrayList<>();
        for (int z = z0; z < z0 + 6; z++) {
            for (int x = x0; x < x0 + 6; x++) { chunks.add(new int[]{x, z}); }
        }
        World reference = null;
        for (int order = 0; order < 7; order++) {
            List<int[]> ordered = new ArrayList<>(chunks);
            if (order == 1) { Collections.reverse(ordered); }
            if (order == 2) { ordered.sort(Comparator.<int[]>comparingInt(c -> c[0]).thenComparingInt(c -> c[1])); }
            if (order >= 3 && order <= 5) { Collections.shuffle(ordered, new Random(order * 11L)); }
            if (order == 6) {
                ordered.sort(Comparator.<int[]>comparingInt(c -> Math.max(Math.abs(c[0] - x0 - 2),
                        Math.abs(c[1] - z0 - 2))).thenComparingDouble(c -> Math.atan2(c[1] - z0 - 2, c[0] - x0 - 2)));
            }
            World world = new World(seed, World.GEN_URBAN_APARTMENTS);
            for (int[] c : ordered) { world.getOrCreateChunk(c[0], c[1]); }
            assertEquals(0, world.pendingGenerationEditCount());
            if (reference != null) {
                for (int[] c : chunks) {
                    Chunk a = reference.getChunk(c[0], c[1]), b = world.getChunk(c[0], c[1]);
                    assertArrayEquals(bytes(a), bytes(b));
                    assertArrayEquals(a.heightMap, b.heightMap);
                    assertEquals(a.moisture, b.moisture);
                    assertEquals(a.lights.size(), b.lights.size());
                    for (int i = 0; i < a.lights.size(); i++) { assertArrayEquals(a.lights.get(i), b.lights.get(i)); }
                    int x = c[0] * 16 + 8, z = c[1] * 16 + 8;
                    BuildingPlan pa = reference.urban.buildingAt(x, z), pb = world.urban.buildingAt(x, z);
                    assertEquals(pa == null ? 0 : BuildingPlanDiagnostics.fingerprint(pa),
                            pb == null ? 0 : BuildingPlanDiagnostics.fingerprint(pb));
                }
            } else { reference = world; }
        }
    }

    @Test
    void stampingIsIdempotentAndFinalisesLightsHeightsAndAdjacentMeshes() {
        World world = new World(20260731, World.GEN_URBAN_APARTMENTS);
        DistrictPlan district = world.urban.regionPlan(0, 0);
        ParcelPlan p = district.refuge();
        int cx = Math.floorDiv(p.x0(), 16), cz = Math.floorDiv(p.z0(), 16);
        Chunk adjacent = world.getOrCreateChunk(cx - 1, cz);
        adjacent.dirty = false;
        Chunk chunk = world.getOrCreateChunk(cx, cz);
        assertTrue(adjacent.dirty);
        byte[] before = bytes(chunk);
        UrbanBuildingStamper.stamp(chunk, district, world.urban);
        assertArrayEquals(before, bytes(chunk));
        assertEquals(1, chunk.fullLightRebuilds, "generation finalises once");
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int highest = 0;
                for (int y = 0; y < 96; y++) { if (chunk.get(x, y, z).opaque) { highest = y; } }
                assertEquals(highest, chunk.height(x, z));
            }
        }
    }

    @Test
    void destructionPrecedesFixtureStateOnRegeneration() {
        World first = new World(20260731, World.GEN_URBAN_APARTMENTS);
        ParcelPlan p = first.urban.regionPlan(0, 0).refuge();
        BuildingPlan plan = first.urban.buildingAt(p.x0(), p.z0());
        BuildingPlan.Fixture fixture = plan.fixtures().stream()
                .filter(f -> f.kind() == BuildingPlan.FixtureKind.DOOR).findFirst().orElseThrow();
        Vec3i at = fixture.position();
        first.getOrCreateChunk(Math.floorDiv(at.x(), 16), Math.floorDiv(at.z(), 16));
        first.setBlock(at.x(), at.y(), at.z(), BlockType.BROKEN_DOOR, true);
        World regenerated = new World(first.seed, first.generatorVersion);
        regenerated.changedBlocks.putAll(first.changedBlocks);
        regenerated.restoreFixtureState(at, FixtureStates.OPEN);
        regenerated.getOrCreateChunk(Math.floorDiv(at.x(), 16), Math.floorDiv(at.z(), 16));
        assertEquals(BlockType.BROKEN_DOOR, regenerated.getBlock(at.x(), at.y(), at.z()));
        assertEquals(-1, regenerated.fixtures.stateAt(at.x(), at.y(), at.z()));
        assertEquals(FixtureStates.LOCK_NONE, regenerated.fixtureLockAt(at.x(), at.y(), at.z()));
    }

    @Test
    void replacementDoorsAtOccupiedEntrancesDoNotInheritTheGeneratedLock() {
        World world = new World(20260731, World.GEN_URBAN_APARTMENTS);
        for (ParcelPlan p : world.urban.regionPlan(0, 0).parcels()) {
            BuildingPlan plan = world.urban.buildingAt(p.x0(), p.z0());
            var door = plan.fixtures().stream().filter(BuildingPlan.Fixture::locked).findFirst();
            if (door.isEmpty()) { continue; }
            Vec3i at = door.get().position();
            world.getOrCreateChunk(Math.floorDiv(at.x(), 16), Math.floorDiv(at.z(), 16));
            assertEquals(FixtureStates.LOCK_KEYED, world.fixtureLockAt(at.x(), at.y(), at.z()));
            world.setBlock(at.x(), at.y(), at.z(), BlockType.AIR, true);
            world.setBlock(at.x(), at.y(), at.z(), BlockType.WOOD_DOOR, true);
            assertEquals(FixtureStates.LOCK_NONE, world.fixtureLockAt(at.x(), at.y(), at.z()));
            return;
        }
        fail("fixed seed has no occupied entrance");
    }

    static byte[] bytes(Chunk c) {
        byte[] blocks = new byte[16 * 96 * 16];
        int i = 0;
        for (int y = 0; y < 96; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) { blocks[i++] = c.get(x, y, z).id(); }
            }
        }
        return blocks;
    }
}
