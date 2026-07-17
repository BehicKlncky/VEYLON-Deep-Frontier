package com.veylon.settlement;

import com.veylon.util.Vec3i;
import com.veylon.world.World;
import com.veylon.world.WorldGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deterministic settlement planning: same seed, any order, stable rarity rules. */
class SettlementPlannerTest {

    private static final long SEED = 20260716L;

    private WorldGenerator gen(long seed) {
        return new World(seed, World.GEN_DEEP).generator;
    }

    @Test
    void sameSeedProducesIdenticalPlans() {
        WorldGenerator a = gen(SEED);
        WorldGenerator b = gen(SEED);
        for (int rx = -6; rx <= 6; rx++) {
            for (int rz = -6; rz <= 6; rz++) {
                Settlement sa = SettlementPlanner.plan(SEED, a, rx, rz);
                Settlement sb = SettlementPlanner.plan(SEED, b, rx, rz);
                if (sa == null) {
                    assertNull(sb, "plan mismatch at " + rx + "," + rz);
                } else {
                    assertNotNull(sb);
                    assertEquals(sa.type, sb.type);
                    assertEquals(sa.center, sb.center);
                    assertEquals(sa.factionId, sb.factionId);
                    assertEquals(sa.alignment, sb.alignment);
                    assertEquals(sa.residents.size(), sb.residents.size());
                }
            }
        }
    }

    @Test
    void chunkGenerationOrderDoesNotChangeSettlements() {
        // Generate the same chunks in reversed order on two worlds and compare
        // every block: layouts must be identical.
        World w1 = new World(SEED, World.GEN_DEEP);
        World w2 = new World(SEED, World.GEN_DEEP);
        Settlement plan = null;
        int rx = 0, rz = 0;
        outer:
        for (rx = -3; rx <= 3; rx++) {
            for (rz = -3; rz <= 3; rz++) {
                plan = SettlementPlanner.plan(SEED, w1.generator, rx, rz);
                if (plan != null) {
                    break outer;
                }
            }
        }
        assertNotNull(plan, "expected at least one settlement in 49 regions");
        int ccx = Math.floorDiv(plan.center.x(), 16);
        int ccz = Math.floorDiv(plan.center.z(), 16);
        int r = 3;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                w1.getOrCreateChunk(ccx + dx, ccz + dz);
                w2.getOrCreateChunk(ccx - dx, ccz - dz); // reversed order
            }
        }
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                var c1 = w1.getChunk(ccx + dx, ccz + dz);
                var c2 = w2.getOrCreateChunk(ccx + dx, ccz + dz);
                for (int y = 0; y < com.veylon.world.Chunk.SY; y++) {
                    for (int lx = 0; lx < 16; lx++) {
                        for (int lz = 0; lz < 16; lz++) {
                            assertEquals(c1.get(lx, y, lz), c2.get(lx, y, lz),
                                    "block mismatch at chunk " + (ccx + dx) + "," + (ccz + dz)
                                            + " cell " + lx + "," + y + "," + lz);
                        }
                    }
                }
            }
        }
        // No duplicate registration despite repeated planning calls.
        assertEquals(w1.settlements.size(), w2.settlements.size());
    }

    @Test
    void starterRegionIsFriendlyAndBalanceControlled() {
        for (long seed : new long[]{1, 42, 999, -1234567, SEED, 987654321}) {
            WorldGenerator g = gen(seed);
            Settlement s = SettlementPlanner.plan(seed, g, 0, 0);
            assertNotNull(s, "starter settlement is guaranteed (seed " + seed + ")");
            assertEquals(Settlement.Alignment.FRIENDLY, s.alignment,
                    "starter settlement must be friendly (seed " + seed + ")");
            assertTrue(s.type == SettlementType.CAMP || s.type == SettlementType.VILLAGE,
                    "starter settlement capped at village (seed " + seed + ")");
            assertTrue(s.center.x() >= 0 && s.center.z() >= 0
                            && s.center.x() < SettlementPlanner.REGION_BLOCKS
                            && s.center.z() < SettlementPlanner.REGION_BLOCKS,
                    "starter must be reachable inside the spawn region");
            assertTrue(SettlementPlanner.surfaceReachableFromSpawn(g,
                            s.center.x(), s.center.z()),
                    "starter needs a normal walk/swim route (seed " + seed + ")");
            // Neighbors of spawn never host hostiles or fortresses.
            for (int rx = -1; rx <= 1; rx++) {
                for (int rz = -1; rz <= 1; rz++) {
                    Settlement n = SettlementPlanner.plan(seed, g, rx, rz);
                    if (n != null) {
                        assertTrue(n.alignment != Settlement.Alignment.HOSTILE,
                                "no hostile settlement beside spawn (seed " + seed + ")");
                        assertTrue(n.type != SettlementType.FORTRESS,
                                "no fortress beside spawn (seed " + seed + ")");
                    }
                }
            }
        }
    }

    @Test
    void rarityHierarchyAndFortressSeparationHold() {
        WorldGenerator g = gen(SEED);
        Map<SettlementType, Integer> counts = new HashMap<>();
        Map<Long, Settlement> fortresses = new HashMap<>();
        int range = 14; // 29x29 regions = 841 rolls
        for (int rx = -range; rx <= range; rx++) {
            for (int rz = -range; rz <= range; rz++) {
                Settlement s = SettlementPlanner.plan(SEED, g, rx, rz);
                if (s == null) {
                    continue;
                }
                counts.merge(s.type, 1, Integer::sum);
                if (s.type == SettlementType.FORTRESS) {
                    fortresses.put(Settlement.packId(rx, rz), s);
                }
            }
        }
        int villages = counts.getOrDefault(SettlementType.VILLAGE, 0);
        int forts = counts.getOrDefault(SettlementType.FORT, 0);
        int castles = counts.getOrDefault(SettlementType.CASTLE, 0);
        int fortressCount = counts.getOrDefault(SettlementType.FORTRESS, 0);
        assertTrue(villages > 0, "villages must exist");
        assertTrue(villages > forts, "villages more common than forts");
        assertTrue(forts > castles, "forts more common than castles");
        assertTrue(castles >= fortressCount, "castles at least as common as fortresses");

        // Fortress pairs respect the minimum separation.
        for (Settlement a : fortresses.values()) {
            for (Settlement b : fortresses.values()) {
                if (a == b) {
                    continue;
                }
                int dist = Math.max(Math.abs(a.regionX - b.regionX),
                        Math.abs(a.regionZ - b.regionZ));
                assertTrue(dist >= SettlementPlanner.FORTRESS_SEPARATION,
                        "fortresses too close: " + a.regionX + "," + a.regionZ
                                + " vs " + b.regionX + "," + b.regionZ);
            }
        }
        // Fortresses stay away from the starting area.
        for (Settlement f : fortresses.values()) {
            assertTrue(Math.max(Math.abs(f.regionX), Math.abs(f.regionZ))
                            >= SettlementPlanner.FORTRESS_MIN_SPAWN_DIST,
                    "fortress too close to spawn");
        }
    }

    @Test
    void settlementsStayInsideTheirRegionAndNeverOverlap() {
        WorldGenerator g = gen(SEED);
        for (int rx = -8; rx <= 8; rx++) {
            for (int rz = -8; rz <= 8; rz++) {
                Settlement s = SettlementPlanner.plan(SEED, g, rx, rz);
                if (s == null) {
                    continue;
                }
                int minX = rx * SettlementPlanner.REGION_BLOCKS;
                int minZ = rz * SettlementPlanner.REGION_BLOCKS;
                assertTrue(s.center.x() - s.radius >= minX
                                && s.center.x() + s.radius < minX + SettlementPlanner.REGION_BLOCKS
                                && s.center.z() - s.radius >= minZ
                                && s.center.z() + s.radius < minZ + SettlementPlanner.REGION_BLOCKS,
                        "settlement bounds leak out of region " + rx + "," + rz);
            }
        }
    }

    @Test
    void everyRegionOffersACaveEntrance() {
        WorldGenerator g = gen(SEED);
        for (long seed : new long[]{1, 42, 999, -1234567, SEED, 987654321}) {
            g = gen(seed);
            for (int rx = -4; rx <= 4; rx++) {
                for (int rz = -4; rz <= 4; rz++) {
                    Vec3i mouth = SettlementPlanner.caveEntrance(seed, g, rx, rz);
                    assertNotNull(mouth, "every eligible region gets an entrance");
                    assertEquals(mouth, SettlementPlanner.caveEntrance(seed, g, rx, rz),
                                "cave entrance must be deterministic");
                    assertTrue(mouth.y() > World.SEA_LEVEL + 2,
                            "entrance must start on dry terrain");
                    Settlement settlement = SettlementPlanner.plan(seed, g, rx, rz);
                    if (settlement != null) {
                        assertTrue(Math.abs(mouth.x() - settlement.center.x())
                                        > settlement.radius + SettlementPlanner.CAVE_RESERVATION_RADIUS
                                        || Math.abs(mouth.z() - settlement.center.z())
                                        > settlement.radius + SettlementPlanner.CAVE_RESERVATION_RADIUS,
                                "entrance reservation cannot overlap a settlement");
                    }
                }
            }
        }
    }
}
