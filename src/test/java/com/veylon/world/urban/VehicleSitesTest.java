package com.veylon.world.urban;

import com.veylon.ai.Pathfinder;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.VoxelClearance;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Layout fourteen's burnt-out cars (urban milestone 18) on the design seeds: a few wrecks in one
 * lane of the local streets of every region, never at a crossing, in water, on a hazard patch, on
 * a parcel or at a compound's gate; identical in every planning and chunk order; walked round on
 * the open lane and sidewalks; and nowhere in generator 16, which is otherwise the same city.
 */
class VehicleSitesTest {
    private static final long[] DESIGN_SEEDS = SurvivorSitesTest.DESIGN_SEEDS;
    private static final int RANGE = DistrictMap.ALL_DISTRICTS_RING;

    @Test
    void wrecksStandInOneLaneOfALocalStreetClearOfCrossingsWaterHazardsLotsAndGates() {
        for (long seed : DESIGN_SEEDS) {
            World w = new World(seed, World.GEN_URBAN_REFUGES);
            int nearSpawn = 0;
            for (int rz = -RANGE; rz <= RANGE; rz++) {
                for (int rx = -RANGE; rx <= RANGE; rx++) {
                    DistrictPlan plan = w.urban.regionPlan(rx, rz);
                    List<VehicleSites.Wreck> wrecks = w.urban.vehicles(rx, rz).wrecks();
                    assertTrue(wrecks.size() <= VehicleSites.MAX_PER_REGION, "a region holds at most six wrecks");
                    if (Math.abs(rx) <= 1 && Math.abs(rz) <= 1) { nearSpawn += wrecks.size(); }
                    for (int i = 0; i < wrecks.size(); i++) {
                        VehicleSites.Wreck wreck = wrecks.get(i);
                        String where = seed + " " + rx + "," + rz + " " + wreck;
                        assertEquals(i, wreck.index());
                        checkLane(plan, wreck, where);
                        for (CompoundSites.Compound c : w.urban.sites(rx, rz).compounds()) {
                            for (Vec3i leaf : c.gate().leaves()) {
                                assertTrue(Math.max(Math.abs(leaf.x() - wreck.x0()), Math.abs(leaf.z() - wreck.z0()))
                                        > VehicleSites.COMPOUND_CLEARANCE, "no wreck at a compound's gate: " + where);
                            }
                        }
                    }
                }
            }
            assertTrue(nearSpawn >= 3, "salvage within a region of the spawn on seed " + seed + ": " + nearSpawn);
        }
    }

    /** All of a wreck on unparcelled, overlay-free roadway in one lane, with clear roadway past both ends. */
    private static void checkLane(DistrictPlan plan, VehicleSites.Wreck wreck, String where) {
        boolean onLine = false;
        for (int k = 1; k < StreetNetwork.SUPERBLOCKS_PER_SIDE; k++) {
            int l = wreck.alongZ() ? plan.northSouthLine(k) : plan.eastWestLine(k);
            int across = wreck.alongZ() ? wreck.x0() : wreck.z0();
            onLine |= across == l || across == l - VehicleSites.WIDTH;
        }
        assertTrue(onLine, "a wreck lies against a local street's centre line: " + where);
        for (int x = wreck.x0(); x <= wreck.x1(); x++) {
            for (int z = wreck.z0(); z <= wreck.z1(); z++) {
                assertEquals(UrbanSurface.ROADWAY, plan.surfaceAt(x, z), where);
                assertEquals(UrbanOverlay.NONE, plan.overlayAt(x, z), "never in water or on a hazard: " + where);
                assertNull(plan.parcelAt(x, z), where);
            }
        }
        // Six cells beyond each end the street is still plain roadway: no crossing, crosswalk or avenue.
        for (int d = 1; d <= VehicleSites.CROSSING_CLEARANCE; d++) {
            for (int across = 0; across < VehicleSites.WIDTH; across++) {
                int lowX = wreck.alongZ() ? wreck.x0() + across : wreck.x0() - d;
                int lowZ = wreck.alongZ() ? wreck.z0() - d : wreck.z0() + across;
                int highX = wreck.alongZ() ? wreck.x0() + across : wreck.x1() + d;
                int highZ = wreck.alongZ() ? wreck.z1() + d : wreck.z0() + across;
                assertEquals(UrbanSurface.ROADWAY, plan.surfaceAt(lowX, lowZ), "clear before a wreck: " + where);
                assertEquals(UrbanSurface.ROADWAY, plan.surfaceAt(highX, highZ), "clear after a wreck: " + where);
            }
        }
    }

    @Test
    void wrecksAreTheSameWhateverOrderTheCityIsPlannedIn() {
        for (long seed : new long[]{DESIGN_SEEDS[0], DESIGN_SEEDS[2]}) {
            World a = new World(seed, World.GEN_URBAN_REFUGES), b = new World(seed, World.GEN_URBAN_REFUGES);
            List<String> forward = new ArrayList<>(), backward = new ArrayList<>();
            for (int rz = -RANGE; rz <= RANGE; rz++) {
                for (int rx = -RANGE; rx <= RANGE; rx++) { forward.add(String.valueOf(a.urban.vehicles(rx, rz))); }
            }
            for (int rz = RANGE; rz >= -RANGE; rz--) {
                for (int rx = RANGE; rx >= -RANGE; rx--) { backward.add(0, String.valueOf(b.urban.vehicles(rx, rz))); }
            }
            assertEquals(forward, backward, "plans never depend on visit order or cache eviction");
        }
    }

    @Test
    void aWreckHasAnEngineABodyAndACabinOverItsMiddle() {
        World w = new World(DESIGN_SEEDS[0], World.GEN_URBAN_REFUGES);
        VehicleSites.Wreck wreck = nearest(w);
        List<Vec3i> cells = wreck.cells();
        assertEquals(12, cells.size(), "eight cells in the lower course and four in the cabin");
        int engine = 0, body = 0, cabin = 0;
        for (Vec3i c : cells) {
            switch (wreck.partAt(c.x(), c.y(), c.z())) {
                case ENGINE -> { engine++; assertEquals(VehicleSites.FEET, c.y()); }
                case BODY -> { body++; assertEquals(VehicleSites.FEET, c.y()); }
                case CABIN -> { cabin++; assertEquals(VehicleSites.FEET + 1, c.y()); }
            }
            assertSame(wreck, w.urban.wreckAt(c.x(), c.y(), c.z()), "the plan names the wreck at each of its cells");
        }
        assertEquals(2, engine);
        assertEquals(6, body);
        assertEquals(4, cabin);
        assertNull(w.urban.wreckAt(wreck.x0(), VehicleSites.FEET + 2, wreck.z0()), "nothing above the cabin");
    }

    @Test
    void aGeneratedWreckIsWholeInEveryChunkOrderAndTheStreetPastItStaysOpen() {
        World forward = new World(DESIGN_SEEDS[1], World.GEN_URBAN_REFUGES);
        World backward = new World(DESIGN_SEEDS[1], World.GEN_URBAN_REFUGES);
        VehicleSites.Wreck wreck = nearest(forward);
        List<long[]> chunks = chunksAround(wreck);
        for (long[] c : chunks) { forward.getOrCreateChunk((int) c[0], (int) c[1]); }
        for (int i = chunks.size() - 1; i >= 0; i--) { backward.getOrCreateChunk((int) chunks.get(i)[0], (int) chunks.get(i)[1]); }
        for (long[] c : chunks) {
            Chunk a = forward.getChunk((int) c[0], (int) c[1]), b = backward.getChunk((int) c[0], (int) c[1]);
            for (int x = 0; x < Chunk.SX; x++) {
                for (int y = 0; y < Chunk.SY; y++) {
                    for (int z = 0; z < Chunk.SZ; z++) {
                        assertSame(a.get(x, y, z), b.get(x, y, z), "identical in every chunk order");
                    }
                }
            }
        }
        for (Vec3i cell : wreck.cells()) {
            assertSame(BlockType.CAR_WRECK, forward.getBlock(cell.x(), cell.y(), cell.z()), "stamped whole at " + cell);
        }
        assertSame(BlockType.AIR, forward.getBlock(wreck.x0(), VehicleSites.FEET + 2, wreck.z0()));
        // The other lane beside it is open street: a body passes, and a route leads from one end to the other.
        Vec3i before = wreck.alongZ() ? new Vec3i(wreck.x0(), VehicleSites.FEET, wreck.z0() - 2)
                : new Vec3i(wreck.x0() - 2, VehicleSites.FEET, wreck.z0());
        Vec3i after = wreck.alongZ() ? new Vec3i(wreck.x0(), VehicleSites.FEET, wreck.z1() + 2)
                : new Vec3i(wreck.x1() + 2, VehicleSites.FEET, wreck.z0());
        for (int a = -1; a <= VehicleSites.LENGTH; a++) {
            int x = wreck.alongZ() ? otherLane(forward, wreck) : wreck.x0() + a;
            int z = wreck.alongZ() ? wreck.z0() + a : otherLane(forward, wreck);
            assertTrue(VoxelClearance.standable(forward, x, VehicleSites.FEET, z), "the other lane is open at " + x + "," + z);
        }
        List<Vec3i> route = Pathfinder.find(forward, before.x(), before.y(), before.z(), after.x(), after.y(), after.z(),
                Pathfinder.DEFAULT_BUDGET);
        assertNotNull(route, "a body walks round a wreck in its lane");
        for (Vec3i p : route) { assertNull(forward.urban.wreckAt(p.x(), p.y(), p.z()), "never through the wreck"); }
    }

    /** A coordinate across the street in the lane on the other side of the centre line. */
    private static int otherLane(World w, VehicleSites.Wreck wreck) {
        DistrictPlan plan = w.urban.regionPlan(StreetNetwork.regionOf(wreck.x0()), StreetNetwork.regionOf(wreck.z0()));
        for (int k = 1; k < StreetNetwork.SUPERBLOCKS_PER_SIDE; k++) {
            int line = wreck.alongZ() ? plan.northSouthLine(k) : plan.eastWestLine(k);
            int across = wreck.alongZ() ? wreck.x0() : wreck.z0();
            if (across == line) { return line - 1; }
            if (across == line - VehicleSites.WIDTH) { return line; }
        }
        throw new AssertionError("wreck off its street");
    }

    @Test
    void generatorSixteenIsTheSameCityWithoutTheWrecks() {
        World strongholds = new World(DESIGN_SEEDS[2], World.GEN_URBAN_STRONGHOLDS);
        World refuges = new World(DESIGN_SEEDS[2], World.GEN_URBAN_REFUGES);
        VehicleSites.Wreck wreck = nearest(refuges);
        for (int rz = -RANGE; rz <= RANGE; rz++) {
            for (int rx = -RANGE; rx <= RANGE; rx++) {
                assertSame(VehicleSites.NONE, strongholds.urban.vehicles(rx, rz), "no wreck before layout fourteen");
                assertEquals(String.valueOf(strongholds.urban.sites(rx, rz).compounds()),
                        String.valueOf(refuges.urban.sites(rx, rz).compounds()), "the compounds are layout thirteen's");
                assertEquals(String.valueOf(strongholds.urban.sites(rx, rz).sites()),
                        String.valueOf(refuges.urban.sites(rx, rz).sites()), "the survivor sites are layout thirteen's");
            }
        }
        for (long[] c : chunksAround(wreck)) {
            Chunk a = strongholds.getOrCreateChunk((int) c[0], (int) c[1]), b = refuges.getOrCreateChunk((int) c[0], (int) c[1]);
            for (int x = 0; x < Chunk.SX; x++) {
                for (int y = 0; y < Chunk.SY; y++) {
                    for (int z = 0; z < Chunk.SZ; z++) {
                        int wx = (int) c[0] * Chunk.SX + x, wz = (int) c[1] * Chunk.SZ + z;
                        if (refuges.urban.wreckAt(wx, y, wz) != null) {
                            assertSame(BlockType.AIR, a.get(x, y, z), "the lane is empty in generator 16");
                        } else {
                            assertSame(a.get(x, y, z), b.get(x, y, z), "the rest of the chunk is unchanged");
                        }
                    }
                }
            }
        }
    }

    private static VehicleSites.Wreck nearest(World w) {
        VehicleSites.Wreck best = null;
        int[] spawn = w.urban.spawnColumn();
        for (int rz = -1; rz <= 1; rz++) {
            for (int rx = -1; rx <= 1; rx++) {
                for (VehicleSites.Wreck wreck : w.urban.vehicles(rx, rz).wrecks()) {
                    if (best == null || dist(wreck, spawn) < dist(best, spawn)) { best = wreck; }
                }
            }
        }
        assertNotNull(best, "a wreck within a region of the spawn");
        return best;
    }

    private static long dist(VehicleSites.Wreck w, int[] spawn) {
        long dx = w.x0() - spawn[0], dz = w.z0() - spawn[1];
        return dx * dx + dz * dz;
    }

    /** The chunks under a wreck and its neighbours, in row order. */
    private static List<long[]> chunksAround(VehicleSites.Wreck wreck) {
        LinkedHashSet<Long> keys = new LinkedHashSet<>();
        for (int x = wreck.x0() - 8; x <= wreck.x1() + 8; x += 4) {
            for (int z = wreck.z0() - 8; z <= wreck.z1() + 8; z += 4) {
                keys.add(((long) Math.floorDiv(x, Chunk.SX) << 32) | (Math.floorDiv(z, Chunk.SZ) & 0xffffffffL));
            }
        }
        List<long[]> out = new ArrayList<>();
        for (long k : keys) { out.add(new long[]{k >> 32, (int) k}); }
        return out;
    }
}
