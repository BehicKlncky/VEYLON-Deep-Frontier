package com.veylon.world.urban;

import com.veylon.combat.ExplosionSystem;
import com.veylon.item.CreativePalette;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layout sixteen's relay mast (urban milestone 20): one per world, on the roof of an unheld building
 * of the civic precinct nearest the spawn, stamped in any chunk order without taking a step of any
 * walking route away, and impossible to break, blast or conjure.
 */
class RelaySitesTest {
    /** The urban design seeds (DESIGN §11). */
    private static final long[] SEEDS = {20_260_731L, -4_242L, 7L, 0L, 987_654_321L, -9_000_000_000_000_000_000L};

    @Test
    void everyDesignSeedHasOneRelayOnAnUnheldPrecinctRoof() {
        for (long seed : SEEDS) {
            World world = new World(seed, World.GEN_URBAN_MISSIONS);
            RelaySites.Site r = world.urban.relay();
            String context = "seed " + seed + ": " + r;
            assertNotNull(r, "seed " + seed + " has a relay");
            assertEquals(UrbanDistrict.CIVIC, DistrictMap.districtOf(seed, r.rx(), r.rz()), context);
            assertTrue(DistrictMap.ring(r.rx(), r.rz()) <= DistrictMap.ALL_DISTRICTS_RING, context);
            assertEquals(nearestCivic(seed), List.of(r.rx(), r.rz()), "the nearest civic region " + context);
            BuildingPlan b = world.urban.buildingById(r.building());
            assertNotNull(b, context);
            assertTrue(RelaySites.FAMILIES.contains(b.family()), context);
            assertNotEquals(BuildingPlan.Variant.OCCUPIED, b.variant(), "nobody holds the building " + context);
            assertNull(SurvivorSites.owner(world.urban, b.id()), "no site or compound holds it " + context);
            assertEquals(UrbanIds.regionX(b.id()), r.rx(), context);
            assertEquals(b.roofY() + 1, r.relay().y(), "the cabinet stands on the roof " + context);
            assertTrue(b.parcel().contains(r.relay().x(), r.relay().z()), context);
            assertFalse(b.parcel().onOutline(r.relay().x(), r.relay().z()), context);
            // Open roof round it in the plan: the cabinet takes no step away from any walk.
            PlanVolume v = b.voxels();
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int x = r.relay().x() + dx, z = r.relay().z() + dz;
                    assertEquals(BlockType.CONCRETE, v.getBlock(x, b.roofY(), z), "roof slab " + context);
                    assertEquals(BlockType.AIR, v.getBlock(x, b.roofY() + 1, z), "open roof " + context);
                    assertEquals(BlockType.AIR, v.getBlock(x, b.roofY() + 2, z), "headroom " + context);
                }
            }
            assertEquals(1, Math.abs(r.stand().x() - r.relay().x()) + Math.abs(r.stand().z() - r.relay().z()),
                    "the worker stands beside it " + context);
            assertEquals(RelaySites.MAST, r.mast().size());
            for (int h = 0; h < RelaySites.MAST; h++) { assertEquals(r.relay().offset(0, h + 1, 0), r.mast().get(h)); }
            // Pure: a fresh world, after plan caches churned elsewhere, plans the same relay.
            World again = new World(seed, World.GEN_URBAN_MISSIONS);
            for (int rz = -4; rz <= 4; rz++) {
                for (int rx = -4; rx <= 4; rx++) { again.urban.regionPlan(rx, rz); }
            }
            assertEquals(r, again.urban.relay(), "the relay is a pure function of the seed " + context);
            System.out.println("[relay] seed=" + seed + " region=" + r.rx() + "," + r.rz() + " building="
                    + UrbanIds.format(b.id()) + " " + b.family() + " " + b.variant() + " storeys=" + b.storeys()
                    + " cabinet=" + r.relay() + " stand=" + r.stand());
        }
    }

    @Test
    void theMastIsStampedInAnyChunkOrderAndItsRoofStaysWalkable() {
        for (long seed : new long[]{SEEDS[0], SEEDS[1], SEEDS[2]}) {
            World forward = new World(seed, World.GEN_URBAN_MISSIONS);
            World backward = new World(seed, World.GEN_URBAN_MISSIONS);
            RelaySites.Site r = forward.urban.relay();
            BuildingPlan b = forward.urban.buildingById(r.building());
            List<int[]> chunks = chunksUnder(b);
            for (int[] c : chunks) { forward.getOrCreateChunk(c[0], c[1]); }
            for (int i = chunks.size() - 1; i >= 0; i--) { backward.getOrCreateChunk(chunks.get(i)[0], chunks.get(i)[1]); }
            String context = "seed " + seed + ": " + r;
            assertEquals(BlockType.RELAY, forward.getBlock(r.relay().x(), r.relay().y(), r.relay().z()), context);
            for (Vec3i m : r.mast()) { assertEquals(BlockType.RAILING, forward.getBlock(m.x(), m.y(), m.z()), context); }
            assertEquals(BlockType.AIR, forward.getBlock(r.stand().x(), r.stand().y(), r.stand().z()), context);
            PlanVolume v = b.voxels();
            for (int y = v.minY; y <= v.maxY; y++) {
                for (int z = v.z0; z < v.z0 + v.depth; z++) {
                    for (int x = v.x0; x < v.x0 + v.width; x++) {
                        assertEquals(forward.getBlock(x, y, z), backward.getBlock(x, y, z), "chunk order " + context);
                        if (!r.holds(x, y, z) && b.parcel().contains(x, z)) {
                            assertEquals(v.getBlock(x, y, z), forward.getBlock(x, y, z), "only the mast differs " + context);
                        }
                    }
                }
            }
            var report = AccessibilityValidator.inspectGenerated(b, forward.urban.regionPlan(r.rx(), r.rz()), forward);
            assertTrue(report.ok(), () -> context + " " + report.summary());
            assertTrue(forward.changedBlocks.isEmpty(), "the mast is generated, never an edit");
        }
    }

    @Test
    void earlierGeneratorsHaveNoRelay() {
        World mission = new World(SEEDS[0], World.GEN_URBAN_MISSIONS);
        RelaySites.Site r = mission.urban.relay();
        World utility = new World(SEEDS[0], World.GEN_URBAN_UTILITIES);
        assertNull(utility.urban.relay());
        assertFalse(utility.urban.relayAt(r.relay().x(), r.relay().y(), r.relay().z()));
        utility.getOrCreateChunk(Math.floorDiv(r.relay().x(), Chunk.SX), Math.floorDiv(r.relay().z(), Chunk.SZ));
        assertEquals(BlockType.AIR, utility.getBlock(r.relay().x(), r.relay().y(), r.relay().z()),
                "generator 18's roof is open where generator 19 raises the mast");
        assertEquals(utility.urban.buildingById(r.building()), mission.urban.buildingById(r.building()),
                "the same building under it");
    }

    @Test
    void theCabinetCannotBeBrokenBlastedBurntOrTakenFromThePalette() {
        assertTrue(BlockType.RELAY.hardness < 0, "unbreakable by hand or tool, in every mode");
        assertTrue(ExplosionSystem.blastResistance(BlockType.RELAY) >= 1000f);
        assertFalse(BlockType.RELAY.flammable);
        assertNull(BlockType.RELAY.drop);
        assertNotNull(CreativePalette.exclusionReason(BlockType.RELAY));
    }

    private static List<Integer> nearestCivic(long seed) {
        int[] first = RelaySites.civicRegions(seed).getFirst();
        return List.of(first[0], first[1]);
    }

    private static List<int[]> chunksUnder(BuildingPlan b) {
        List<int[]> out = new ArrayList<>();
        PlanVolume v = b.voxels();
        for (int cz = Math.floorDiv(v.z0, Chunk.SZ); cz <= Math.floorDiv(v.z0 + v.depth - 1, Chunk.SZ); cz++) {
            for (int cx = Math.floorDiv(v.x0, Chunk.SX); cx <= Math.floorDiv(v.x0 + v.width - 1, Chunk.SX); cx++) {
                out.add(new int[]{cx, cz});
            }
        }
        return out;
    }
}
