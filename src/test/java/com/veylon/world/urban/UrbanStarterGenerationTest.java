package com.veylon.world.urban;

import com.veylon.world.Chunk;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class UrbanStarterGenerationTest {
    @Test
    void courtyardUtilityAndFurnishedStarterSlicesAreIndependentOfChunkOrder() {
        for (long seed : new long[]{20260731, -4242, 7, 0, 987654321, -9000000000000000000L}) {
            World a = new World(seed, World.GEN_URBAN_STARTER), b = new World(seed, World.GEN_URBAN_STARTER);
            World encounters = new World(seed, World.GEN_URBAN_ENCOUNTERS);
            var site = UrbanStarterSite.of(a.urban); var court = a.urban.regionPlan(0, 0).superblock(5).bounds();
            var order = new ArrayList<int[]>();
            for (int cx = Math.floorDiv(court.x0() - 1, 16); cx <= Math.floorDiv(court.x1() + 1, 16); cx++) {
                for (int cz = Math.floorDiv(court.z0() - 1, 16); cz <= Math.floorDiv(court.z1() + 1, 16); cz++) {
                    order.add(new int[]{cx, cz}); a.getOrCreateChunk(cx, cz);
                }
            }
            Collections.reverse(order);
            for (int[] c : order) { b.getOrCreateChunk(c[0], c[1]); encounters.getOrCreateChunk(c[0], c[1]); }
            for (int[] c : order) {
                Chunk x = a.getChunk(c[0], c[1]), y = b.getChunk(c[0], c[1]);
                for (int ly = 0; ly < 96; ly++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int lx = 0; lx < 16; lx++) {
                            assertSame(x.get(lx, ly, lz), y.get(lx, ly, lz));
                            assertSame(x.get(lx, ly, lz), encounters.getChunk(c[0], c[1]).get(lx, ly, lz),
                                    "adding encounters preserves generator-9 geometry");
                        }
                    }
                }
                assertArrayEquals(x.heightMap, y.heightMap);
            }
            assertEquals(com.veylon.world.BlockType.CAMPFIRE, b.getBlock(site.fire().x(), 41, site.fire().z()));
            assertEquals(com.veylon.world.BlockType.RAIN_COLLECTOR, b.getBlock(site.collector().x(), 41, site.collector().z()));
            assertTrue(a.crateContents.isEmpty()); assertTrue(b.crateContents.isEmpty());
            for (var parcel : a.urban.regionPlan(0, 0).parcels()) {
                if (!UrbanStarterSite.isNeighbour(parcel)) { continue; }
                BuildingPlan plan = a.urban.buildingAt(parcel.x0(), parcel.z0());
                assertTrue(AccessibilityValidator.inspectGenerated(plan, a.urban.regionPlan(0, 0), a).ok(),
                        "actual starter geometry seed=" + seed);
            }
        }
    }

    @Test
    void starterRulesLeaveOtherRegionsAndHistoricalCityPlansUnchanged() {
        for (long seed : new long[]{20260731, -4242, 7}) {
            var old = CityPlanner.plan(seed, UrbanRevision.LAYOUT_STREET_NETWORK, 1, -1);
            var fresh = CityPlanner.planStarter(seed, 1, -1);
            assertEquals(old.parcels(), fresh.parcels());
            for (int z = 0; z < 256; z++) {
                for (int x = 0; x < 256; x++) {
                    assertEquals(old.surfaceAt(x + 256, z - 256), fresh.surfaceAt(x + 256, z - 256));
                }
            }
        }
    }
}
