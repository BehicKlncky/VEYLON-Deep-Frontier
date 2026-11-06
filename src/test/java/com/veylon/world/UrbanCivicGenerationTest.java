package com.veylon.world;

import com.veylon.world.urban.AccessibilityValidator;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.BuildingPlan.Family;
import com.veylon.world.urban.BuildingPlanDiagnostics;
import com.veylon.world.urban.BuildingPlanner;
import com.veylon.world.urban.CivicCatalogue;
import com.veylon.world.urban.DistrictPlan;
import com.veylon.world.urban.EncounterAnchors;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.PlanVolume;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanStarterSite;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Generator 11: the commercial and civic catalogue as actually generated, in any chunk order. */
class UrbanCivicGenerationTest {
    private static final long[] SEEDS = {20260731, -4242, 7, 0, 987654321, -9000000000000000000L};

    @Test
    void everyGeneratedRoomAndReservationOfTheCatalogueHasProductionEntryAndExitProof() {
        Map<Family, Integer> families = new EnumMap<>(Family.class);
        List<long[]> regions = new ArrayList<>();
        for (long seed : SEEDS) { regions.add(new long[]{seed, 0, 0}); }
        // One downtown region, where office blocks are common, besides every seed's origin.
        regions.add(new long[]{20260731, 1, -1});
        for (long[] r : regions) {
            long seed = r[0];
            int rx = (int) r[1], rz = (int) r[2];
            World world = new World(seed, World.GEN_URBAN_CIVIC);
            DistrictPlan district = world.urban.regionPlan(rx, rz);
            // A complete 16 x 16-chunk region, so no boundary parcel's rooms are omitted.
            for (int cz = rz * 16; cz < rz * 16 + 16; cz++) {
                for (int cx = rx * 16; cx < rx * 16 + 16; cx++) { world.getOrCreateChunk(cx, cz); }
            }
            for (ParcelPlan parcel : district.parcels()) {
                BuildingPlan plan = world.urban.buildingAt(parcel.x0(), parcel.z0());
                PlanVolume v = plan.voxels();
                for (int y = v.minY; y <= v.maxY; y++) {
                    for (int z = parcel.z0(); z <= parcel.z1(); z++) {
                        for (int x = parcel.x0(); x <= parcel.x1(); x++) {
                            assertEquals(v.getBlock(x, y, z), world.getBlock(x, y, z), "owned slice differs from proposal");
                        }
                    }
                }
                AccessibilityValidator.Report report = AccessibilityValidator.inspectGenerated(plan, district, world);
                assertTrue(report.ok(), () -> "seed=" + seed + " " + BuildingPlanner.identity(plan) + " " + report.summary());
                families.merge(plan.family(), 1, Integer::sum);
            }
            assertTrue(world.fixtures.isEmpty(), "generated locks consume no touched-state capacity");
            assertTrue(world.crateContents.isEmpty(), "reservations roll nothing until searched");
            assertTrue(world.changedBlocks.isEmpty());
            assertEquals(0, world.pendingGenerationEditCount());
        }
        for (Family family : CivicCatalogue.FAMILIES) {
            assertTrue(families.getOrDefault(family, 0) > 0, family + " was generated: " + families);
        }
        System.out.println("civic-generated-rooms families=" + families);
    }

    @Test
    void everyFamilyGeneratesIdenticallyInEveryChunkOrder() {
        for (long seed : new long[]{20260731, -4242}) {
            for (Family family : CivicCatalogue.FAMILIES) {
                ParcelPlan parcel = BuildingPlanDiagnostics.example(seed, family).parcel();
                compareOrders(seed, Math.floorDiv(parcel.x0(), 16) - 1, Math.floorDiv(parcel.z0(), 16) - 1);
            }
        }
    }

    @Test
    void theStarterSliceAndEveryWalkUpKeepGeneratorTenBlocksPlansAndEncounters() {
        for (long seed : SEEDS) {
            World ten = new World(seed, World.GEN_URBAN_ENCOUNTERS), eleven = new World(seed, World.GEN_URBAN_CIVIC);
            int civic = 0;
            for (ParcelPlan parcel : ten.urban.regionPlan(0, 0).parcels()) {
                BuildingPlan a = ten.urban.buildingAt(parcel.x0(), parcel.z0());
                BuildingPlan b = eleven.urban.buildingAt(parcel.x0(), parcel.z0());
                if (b.family().walkUp()) {
                    assertEquals(BuildingPlanDiagnostics.fingerprint(a), BuildingPlanDiagnostics.fingerprint(b),
                            "walk-ups are generator 10's buildings");
                    assertEquals(EncounterAnchors.forParcel(ten, parcel), EncounterAnchors.forParcel(eleven, parcel),
                            "catalogue two keeps catalogue one's residential reservations");
                } else {
                    assertFalse(UrbanStarterSite.isNeighbour(parcel));
                    civic++;
                }
            }
            assertTrue(civic > 0);
            ParcelPlan refuge = ten.urban.regionPlan(0, 0).refuge();
            int cx0 = Math.floorDiv(refuge.x0(), 16) - 2, cz0 = Math.floorDiv(refuge.z0(), 16) - 2;
            for (int cz = cz0; cz < cz0 + 6; cz++) {
                for (int cx = cx0; cx < cx0 + 6; cx++) {
                    Chunk a = ten.getOrCreateChunk(cx, cz), b = eleven.getOrCreateChunk(cx, cz);
                    if (!civicIn(eleven, cx, cz)) {
                        assertArrayEquals(UrbanApartmentGenerationTest.bytes(a), UrbanApartmentGenerationTest.bytes(b),
                                "the slice neighbourhood is unchanged at chunk " + cx + "," + cz);
                    }
                }
            }
        }
    }

    @Test
    void catalogueTwoGuardsEveryUnheldClinicAndSecurityPostAndResolvesItsIds() {
        int guarded = 0;
        for (long seed : new long[]{20260731, -4242, 7}) {
            World world = new World(seed, World.GEN_URBAN_CIVIC);
            for (int[] r : new int[][]{{0, 0}, {1, -1}, {-1, -1}}) {
                for (ParcelPlan parcel : world.urban.regionPlan(r[0], r[1]).parcels()) {
                    BuildingPlan plan = world.urban.buildingAt(parcel.x0(), parcel.z0());
                    var anchors = EncounterAnchors.forParcel(world, parcel);
                    boolean secured = plan.family() == Family.CLINIC || plan.family() == Family.SECURITY;
                    if (plan.variant() == BuildingPlan.Variant.OCCUPIED) {
                        assertTrue(anchors.isEmpty(), "held buildings have no infected");
                    } else if (secured) {
                        assertEquals(2, anchors.size());
                        assertEquals(0, UrbanIds.roomIndex(anchors.get(1).id()), "the lobby guard");
                        guarded++;
                    }
                    for (var anchor : anchors) {
                        assertEquals(anchor, EncounterAnchors.resolve(world, anchor.id()));
                        assertEquals(plan.id(), anchor.building());
                    }
                }
            }
        }
        assertTrue(guarded > 0);
    }

    private static boolean civicIn(World world, int cx, int cz) {
        for (int x = cx * 16; x < cx * 16 + 16; x++) {
            for (int z = cz * 16; z < cz * 16 + 16; z++) {
                BuildingPlan b = world.urban.buildingAt(x, z);
                if (b != null && !b.family().walkUp()) { return true; }
            }
        }
        return false;
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
            World world = new World(seed, World.GEN_URBAN_CIVIC);
            for (int[] c : ordered) { world.getOrCreateChunk(c[0], c[1]); }
            assertEquals(0, world.pendingGenerationEditCount());
            if (reference != null) {
                for (int[] c : chunks) {
                    Chunk a = reference.getChunk(c[0], c[1]), b = world.getChunk(c[0], c[1]);
                    assertArrayEquals(UrbanApartmentGenerationTest.bytes(a), UrbanApartmentGenerationTest.bytes(b));
                    assertArrayEquals(a.heightMap, b.heightMap);
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
}
