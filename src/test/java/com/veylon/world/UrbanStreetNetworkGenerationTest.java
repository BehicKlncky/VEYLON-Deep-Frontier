package com.veylon.world;

import com.veylon.Game;
import com.veylon.entity.GameMode;
import com.veylon.world.urban.CityPlanner;
import com.veylon.world.urban.DistrictPlan;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.UrbanDatum;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanRevision;
import com.veylon.world.urban.UrbanSurface;
import com.veylon.world.urban.UrbanSurfaceMaterials;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generator 5 as chunks: the street network stamped through
 * {@code World.getOrCreateChunk}, checked on the generated blocks rather than
 * on the plan alone (DESIGN §11 determinism and seam criteria).
 */
class UrbanStreetNetworkGenerationTest {

    private static final long[] SEEDS = {20_260_731L, -4_242L, 7L, 0L, 987_654_321L,
            -9_000_000_000_000_000_000L};
    private static final int STREET = UrbanDatum.STREET_Y;

    // ---- chunk order -----------------------------------------------------

    @Test
    void chunkCreationOrderNeverChangesBlocksHeightsOrIdentities() {
        for (long seed : SEEDS) {
            // Six by six chunks around the corner where four regions meet,
            // spiralling out from the spawn chunk.
            int[] spawn = CityPlanner.plan(seed, UrbanRevision.LAYOUT_STREET_NETWORK, 0, 0).spawnAnchor();
            assertOrderIndependent(seed, window(-3, -3, 6),
                    new int[]{Math.floorDiv(spawn[0], Chunk.SX), Math.floorDiv(spawn[1], Chunk.SZ)});
        }
        // The far pair at about ±4096 regions: overflow and negative rounding.
        for (long seed : new long[]{SEEDS[1], SEEDS[5]}) {
            assertOrderIndependent(seed, window(65_533, -65_539, 6), new int[]{65_536, -65_536});
            assertOrderIndependent(seed, window(-65_555, 65_517, 6), new int[]{-65_552, 65_520});
        }
    }

    /** @param spiralCentre the chunk the outward spiral starts from */
    private static void assertOrderIndependent(long seed, List<int[]> chunks, int[] spiralCentre) {
        List<List<int[]>> orders = orders(chunks, spiralCentre);
        World reference = null;
        for (List<int[]> order : orders) {
            World world = new World(seed, World.GEN_URBAN_STREETS);
            for (int[] at : order) {
                world.getOrCreateChunk(at[0], at[1]);
            }
            assertTrue(world.pendingGen.isEmpty(), "a slice wrote outside its chunk");
            if (reference == null) {
                reference = world;
                continue;
            }
            for (int[] at : chunks) {
                Chunk a = reference.getChunk(at[0], at[1]);
                Chunk b = world.getChunk(at[0], at[1]);
                assertArrayEquals(blocks(a), blocks(b), "seed " + seed + " chunk " + at[0] + "," + at[1]);
                assertArrayEquals(a.heightMap, b.heightMap);
                assertEquals(a.moisture, b.moisture);
                for (int lx = 0; lx < Chunk.SX; lx += 5) {
                    for (int lz = 0; lz < Chunk.SZ; lz += 5) {
                        int x = at[0] * Chunk.SX + lx;
                        int z = at[1] * Chunk.SZ + lz;
                        ParcelPlan pa = reference.urban.parcelAt(x, z);
                        ParcelPlan pb = world.urban.parcelAt(x, z);
                        assertEquals(pa == null ? UrbanIds.NONE : pa.id(), pb == null ? UrbanIds.NONE : pb.id());
                        assertEquals(reference.urban.surfaceAt(x, z), world.urban.surfaceAt(x, z));
                    }
                }
            }
        }
    }

    /** Row-major, reverse, column-major, three fixed shuffles and an outward spiral (DESIGN §11). */
    private static List<List<int[]>> orders(List<int[]> chunks, int[] centre) {
        List<List<int[]>> orders = new ArrayList<>();
        orders.add(chunks);
        List<int[]> reverse = new ArrayList<>(chunks);
        Collections.reverse(reverse);
        orders.add(reverse);
        List<int[]> columns = new ArrayList<>(chunks);
        columns.sort(Comparator.<int[]>comparingInt(c -> c[0]).thenComparingInt(c -> c[1]));
        orders.add(columns);
        for (long shuffle : new long[]{11L, 22L, 33L}) {
            List<int[]> shuffled = new ArrayList<>(chunks);
            Collections.shuffle(shuffled, new Random(shuffle));
            orders.add(shuffled);
        }
        // Outward from the spawn chunk, ring by ring, as streaming would.
        List<int[]> spiral = new ArrayList<>(chunks);
        spiral.sort(Comparator.<int[]>comparingInt(c -> Math.max(Math.abs(c[0] - centre[0]),
                        Math.abs(c[1] - centre[1])))
                .thenComparingDouble(c -> Math.atan2(c[1] - centre[1], c[0] - centre[0])));
        orders.add(spiral);
        return orders;
    }

    private static List<int[]> window(int cx0, int cz0, int size) {
        List<int[]> chunks = new ArrayList<>();
        for (int cz = cz0; cz < cz0 + size; cz++) {
            for (int cx = cx0; cx < cx0 + size; cx++) {
                chunks.add(new int[]{cx, cz});
            }
        }
        return chunks;
    }

    // ---- seams -----------------------------------------------------------

    @Test
    void generatedAvenuesMirrorAcrossRegionSeams() {
        for (long seed : SEEDS) {
            World world = new World(seed, World.GEN_URBAN_STREETS);
            for (int[] at : window(-3, -3, 6)) {
                world.getOrCreateChunk(at[0], at[1]);
            }
            for (int along = -48; along < 48; along++) {
                for (int d = 0; d < 6; d++) {
                    // The north-south avenue on x = 0 and the east-west one on z = 0.
                    assertMirrored(world, -1 - d, along, d, along);
                    assertMirrored(world, along, -1 - d, along, d);
                }
            }
        }
    }

    private static void assertMirrored(World world, int ax, int az, int bx, int bz) {
        BlockType a = world.getBlock(ax, STREET, az);
        assertEquals(a, world.getBlock(bx, STREET, bz), "seam " + ax + "," + az + " | " + bx + "," + bz);
        assertTrue(a == BlockType.BASALT || a == BlockType.SAND || a == BlockType.CLAY
                || a == BlockType.STONE_BRICK || a == BlockType.GRASS, "avenue surface " + a);
        for (int y = STREET + 1; y <= STREET + 3; y++) {
            assertEquals(BlockType.AIR, world.getBlock(ax, y, az), "clear above the seam");
            assertEquals(BlockType.AIR, world.getBlock(bx, y, bz), "clear above the seam");
        }
    }

    // ---- the starter network ----------------------------------------------

    @Test
    void theStarterAreaHasAConnectedWalkableStreetNetwork() {
        for (long seed : SEEDS) {
            Game game = new Game();
            game.newWorld(seed, true, World.GEN_URBAN_STREETS, GameMode.SURVIVAL);
            World world = game.world;
            int sx = (int) Math.floor(game.player.pos.x);
            int sz = (int) Math.floor(game.player.pos.z);
            assertEquals(UrbanSurface.COURTYARD_PATH, world.urban.surfaceAt(sx, sz), "seed " + seed);
            assertEquals(STREET + 1.2f, game.player.pos.y, 1e-4f);
            int radius = 7;
            int ccx = Math.floorDiv(sx, Chunk.SX);
            int ccz = Math.floorDiv(sz, Chunk.SZ);
            for (int cx = ccx - radius; cx <= ccx + radius; cx++) {
                for (int cz = ccz - radius; cz <= ccz + radius; cz++) {
                    world.getOrCreateChunk(cx, cz);
                }
            }
            int x0 = (ccx - radius) * Chunk.SX;
            int z0 = (ccz - radius) * Chunk.SZ;
            int size = (2 * radius + 1) * Chunk.SX;
            boolean[] reached = walk(world, sx, sz, x0, z0, size);
            int streets = 0;
            int parcels = 0;
            boolean east = false;
            boolean west = false;
            boolean north = false;
            boolean south = false;
            for (int lz = 0; lz < size; lz++) {
                for (int lx = 0; lx < size; lx++) {
                    int x = x0 + lx;
                    int z = z0 + lz;
                    UrbanSurface surface = world.urban.surfaceAt(x, z);
                    if (surface.publicWalkable) {
                        assertTrue(walkable(world, x, z), "public ground is walkable at " + x + "," + z);
                    }
                    if (surface.parcel) {
                        parcels++;
                    }
                    if (!surface.street) {
                        continue;
                    }
                    streets++;
                    assertTrue(reached[lz * size + lx], "street cell " + x + "," + z
                            + " is cut off from the spawn (seed " + seed + ")");
                    east |= x - sx >= 96;
                    west |= sx - x >= 96;
                    south |= z - sz >= 96;
                    north |= sz - z >= 96;
                }
            }
            assertTrue(streets > 10_000 && parcels > 10_000, "a city, not a placeholder");
            assertTrue(east && west && north && south, "the network reaches 96 blocks every way");
        }
    }

    /** Breadth-first over generated, walkable, public ground; parcels are never crossed. */
    private static boolean[] walk(World world, int sx, int sz, int x0, int z0, int size) {
        boolean[] seen = new boolean[size * size];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        seen[(sz - z0) * size + (sx - x0)] = true;
        queue.add(new int[]{sx, sz});
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            for (int[] step : steps) {
                int x = at[0] + step[0];
                int z = at[1] + step[1];
                int lx = x - x0;
                int lz = z - z0;
                if (lx < 0 || lz < 0 || lx >= size || lz >= size || seen[lz * size + lx]) {
                    continue;
                }
                if (world.urban.surfaceAt(x, z).publicWalkable && walkable(world, x, z)) {
                    seen[lz * size + lx] = true;
                    queue.add(new int[]{x, z});
                }
            }
        }
        return seen;
    }

    /** Solid ground at the datum and two clear cells for the 1.8-block player above it. */
    private static boolean walkable(World world, int x, int z) {
        return world.isColumnLoaded(x, z) && world.getBlock(x, STREET, z).solid
                && !world.getBlock(x, STREET + 1, z).solid && !world.getBlock(x, STREET + 2, z).solid;
    }

    // ---- no wilderness, and the queries -----------------------------------

    @Test
    void urbanGenerationAddsNoWildernessFeatures() {
        Set<BlockType> allowed = EnumSet.of(BlockType.AIR, UrbanSurfaceMaterials.FILL,
                UrbanSurfaceMaterials.TRUNK, UrbanSurfaceMaterials.CANOPY);
        for (UrbanSurface surface : UrbanSurface.values()) {
            allowed.add(UrbanSurfaceMaterials.blockFor(surface));
        }
        for (long seed : new long[]{SEEDS[0], SEEDS[3]}) {
            World world = new World(seed, World.GEN_URBAN_STREETS);
            for (int[] at : window(-6, -6, 13)) {
                world.getOrCreateChunk(at[0], at[1]);
            }
            assertTrue(world.pois.isEmpty(), "no surface or underground POI");
            assertTrue(world.pendingGen.isEmpty());
            assertTrue(world.crateContents.isEmpty(), "no generated loot crate");
            for (int rx = -2; rx <= 2; rx++) {
                for (int rz = -2; rz <= 2; rz++) {
                    assertNull(world.settlementForRegion(rx, rz), "no legacy settlement");
                }
            }
            Set<Long> trunks = new HashSet<>();
            for (int rx = -1; rx <= 0; rx++) {
                for (int rz = -1; rz <= 0; rz++) {
                    for (DistrictPlan.Tree tree : world.urban.regionPlan(rx, rz).trees()) {
                        trunks.add(World.key(tree.x(), tree.z()));
                    }
                }
            }
            for (Chunk chunk : world.loadedChunks()) {
                for (int lx = 0; lx < Chunk.SX; lx++) {
                    for (int lz = 0; lz < Chunk.SZ; lz++) {
                        int x = chunk.cx * Chunk.SX + lx;
                        int z = chunk.cz * Chunk.SZ + lz;
                        for (int y = 0; y < Chunk.SY; y++) {
                            BlockType block = chunk.get(lx, y, lz);
                            assertTrue(allowed.contains(block), block + " at " + x + "," + y + "," + z);
                            if (y < STREET) {
                                assertEquals(UrbanSurfaceMaterials.FILL, block, "no cave or ore in the fill");
                            }
                            if (block == UrbanSurfaceMaterials.TRUNK) {
                                assertTrue(trunks.contains(World.key(x, z)), "unplanned trunk at " + x + "," + z);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void groundAndRoofQueriesComeFromThePlanNotTheColumnTop() {
        World world = new World(SEEDS[0], World.GEN_URBAN_STREETS);
        DistrictPlan origin = world.urban.regionPlan(0, 0);
        DistrictPlan.Tree tree = origin.trees().getFirst();
        int loaded = world.loadedCount();
        assertFalse(world.isColumnLoaded(tree.x(), tree.z()));
        // Every plan query, including far regions it must plan first, leaves the world alone.
        assertEquals(STREET, world.urban.groundY(tree.x(), tree.z()));
        assertEquals(UrbanSurface.TREE_BED, world.urban.surfaceAt(tree.x(), tree.z()));
        assertNull(world.urban.parcelAt(tree.x(), tree.z()));
        assertEquals(STREET, world.urban.plannedRoofY(tree.x(), tree.z()));
        assertNotNull(world.urban.districtAt(9_000, -9_000));
        assertNotNull(world.urban.surfaceAt(-70_000, 33_000));
        world.urban.spawnColumn();
        assertFalse(world.isColumnLoaded(tree.x(), tree.z()));
        assertFalse(world.isColumnLoaded(9_000, -9_000));
        assertEquals(loaded, world.loadedCount(), "plan queries never generate a chunk");
        assertTrue(world.surfaceHeight(tree.x(), tree.z()) > STREET + 3,
                "the column top is the crown of a tree");
        assertTrue(world.isColumnLoaded(tree.x(), tree.z()));
        assertEquals(STREET, world.urban.groundY(tree.x(), tree.z()), "the ground is still the street");

        ParcelPlan parcel = origin.parcels().getFirst();
        int px = parcel.x0() + 2;
        int pz = parcel.z0() + 2;
        assertEquals(UrbanDatum.slabY(parcel.maxStoreys()), world.urban.plannedRoofY(px, pz));
        assertTrue(world.urban.plannedRoofY(px, pz) > STREET);
        assertEquals(STREET, world.surfaceHeight(px, pz), "no building stands on a reserved parcel yet");
        int[] spawn = world.urban.spawnColumn();
        assertEquals(STREET, world.urban.plannedRoofY(spawn[0], spawn[1]), "open ground has no envelope");
        assertNull(world.urban.parcelAt(spawn[0], spawn[1]));
        assertEquals(0, UrbanDatum.floorContaining(STREET + 1));
        assertEquals(-1, UrbanDatum.floorContaining(UrbanDatum.slabY(-1)));
        assertEquals(parcel.maxStoreys(), UrbanDatum.floorContaining(parcel.plannedRoofY()));
        assertTrue(world.urban.cachedRegionPlans() >= 1);
    }

    @Test
    void beyondTheRangeIdsCanNameTheCityIsTheFlatDatum() {
        World world = new World(SEEDS[0], World.GEN_URBAN_STREETS);
        int rx = UrbanIds.MAX_REGION + 1;
        assertFalse(CityPlanner.isPlannable(rx, 0));
        Chunk chunk = world.getOrCreateChunk(rx * 16, 3);
        for (int lx = 0; lx < Chunk.SX; lx++) {
            for (int lz = 0; lz < Chunk.SZ; lz++) {
                assertEquals(BlockType.STONE_BRICK, chunk.get(lx, STREET, lz));
                assertEquals(BlockType.AIR, chunk.get(lx, STREET + 1, lz));
            }
        }
        assertEquals(UrbanSurface.DATUM, world.urban.surfaceAt(rx * 256, 50));
        assertNull(world.urban.districtAt(rx * 256, 50));
    }

    @Test
    void theDatumGeneratorKeepsItsLayoutAndHasNoPlan() {
        World datum = new World(SEEDS[0], World.GEN_URBAN);
        assertEquals(UrbanRevision.LAYOUT_DATUM_PLACEHOLDER, datum.urban.layoutRevision());
        assertFalse(datum.urban.hasCityPlan());
        assertEquals(UrbanSurface.DATUM, datum.urban.surfaceAt(100, 100));
        assertNull(datum.urban.parcelAt(100, 100));
        assertArrayEquals(new int[]{8, 8}, datum.urban.spawnColumn());
        assertEquals(0, datum.urban.cachedRegionPlans());
        World streets = new World(SEEDS[0], World.GEN_URBAN_STREETS);
        assertEquals(UrbanRevision.LAYOUT_STREET_NETWORK, streets.urban.layoutRevision());
        assertNotNull(streets.urban.regionPlan(0, 0).refuge());
    }

    private static byte[] blocks(Chunk chunk) {
        byte[] out = new byte[Chunk.SX * Chunk.SY * Chunk.SZ];
        int i = 0;
        for (int y = 0; y < Chunk.SY; y++) {
            for (int z = 0; z < Chunk.SZ; z++) {
                for (int x = 0; x < Chunk.SX; x++) {
                    out[i++] = chunk.get(x, y, z).id();
                }
            }
        }
        return out;
    }
}
