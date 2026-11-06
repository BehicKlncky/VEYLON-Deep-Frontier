package com.veylon.world.urban;

import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The production city planner across the frozen urban design seeds and the
 * region matrix of DESIGN §11: plans obey their rules, neighbours agree on
 * every shared edge without seeing each other, and no identity depends on the
 * order plans are built or evicted in.
 */
class CityPlannerTest {

    /** The urban design seed set, frozen by package 03 (VALIDATION §6). */
    static final long[] SEEDS = {20_260_731L, -4_242L, 7L, 0L, 987_654_321L,
            -9_000_000_000_000_000_000L};

    /** DESIGN §11 region axis, including the far pair at about ±4096 regions. */
    static final int[][] REGIONS = {{0, 0}, {-1, -1}, {1, -1}, {-1, 1}, {2, 3}, {-3, -2},
            {4_096, -4_096}, {-4_097, 4_095}};

    private static final int LAYOUT = UrbanRevision.LAYOUT_STREET_NETWORK;

    @Test
    void everyMatrixRegionPassesThePlanRulesAgainstItsFourNeighbours() {
        for (long seed : SEEDS) {
            for (int[] region : REGIONS) {
                CityPlanDiagnostics.Report report = CityPlanDiagnostics.inspect(seed, LAYOUT,
                        region[0], region[1]);
                assertTrue(report.ok(), () -> report.summary() + "\n  " + String.join("\n  ",
                        report.messages()));
                assertEquals(4, report.seamsChecked(), report.summary());
                assertTrue(report.messages().isEmpty(), report.summary());
                assertTrue(report.parcels() > 0 && report.streetCells() > 0, report.summary());
            }
        }
    }

    @Test
    void roadsJoinAcrossEveryEdgeOfIndependentlyBuiltNeighbours() {
        for (long seed : SEEDS) {
            for (int[] region : REGIONS) {
                DistrictPlan here = CityPlanner.plan(seed, LAYOUT, region[0], region[1]);
                DistrictPlan east = CityPlanner.plan(seed, LAYOUT, region[0] + 1, region[1]);
                DistrictPlan south = CityPlanner.plan(seed, LAYOUT, region[0], region[1] + 1);
                assertEquals(0, CityPlanDiagnostics.seamMismatches(here, east), "east seam " + seed);
                assertEquals(0, CityPlanDiagnostics.seamMismatches(here, south), "south seam " + seed);
                // A roadway on the border always has roadway, crosswalk or median facing it.
                int border = east.originX();
                for (int z = here.originZ(); z < here.originZ() + StreetNetwork.REGION_SIZE; z++) {
                    UrbanSurface west = here.surfaceAt(border - 1, z);
                    UrbanSurface across = east.surfaceAt(border, z);
                    assertTrue(west.street && across.street, "the avenue spans the seam at z " + z);
                    assertEquals(west, across, "mirrored centre cells at z " + z);
                }
            }
        }
    }

    @Test
    void thePlanRasterAgreesWithThePlanFreeStreetFunction() {
        // Chunks read the raster; seams were reasoned about with the pure
        // function. They must be the same thing.
        for (long seed : new long[]{SEEDS[0], SEEDS[5]}) {
            DistrictPlan plan = CityPlanner.plan(seed, LAYOUT, -1, 1);
            for (int z = plan.originZ(); z < plan.originZ() + StreetNetwork.REGION_SIZE; z++) {
                for (int x = plan.originX(); x < plan.originX() + StreetNetwork.REGION_SIZE; x++) {
                    UrbanSurface street = StreetNetwork.streetSurfaceAt(seed, LAYOUT, x, z);
                    UrbanSurface planned = plan.surfaceAt(x, z);
                    if (street != null) {
                        assertEquals(street, planned, "street cell " + x + "," + z);
                    } else {
                        assertFalse(planned.street, "a street surface off the lattice at " + x + "," + z);
                    }
                }
            }
        }
    }

    @Test
    void localStreetsRunStraightAcrossRegionEdges() {
        for (long seed : SEEDS) {
            for (int[] region : REGIONS) {
                int rx = region[0];
                int rz = region[1];
                DistrictPlan here = CityPlanner.plan(seed, LAYOUT, rx, rz);
                DistrictPlan south = CityPlanner.plan(seed, LAYOUT, rx, rz + 1);
                DistrictPlan east = CityPlanner.plan(seed, LAYOUT, rx + 1, rz);
                for (int k = 1; k <= 3; k++) {
                    assertEquals(here.northSouthLine(k), south.northSouthLine(k),
                            "a north-south street keeps its x across the avenue");
                    assertEquals(here.eastWestLine(k), east.eastWestLine(k),
                            "an east-west street keeps its z across the avenue");
                    int nominal = StreetNetwork.regionOrigin(rx) + StreetNetwork.SUPERBLOCK_PITCH * k;
                    assertTrue(Math.abs(here.northSouthLine(k) - nominal) <= StreetNetwork.LINE_JITTER);
                }
            }
        }
    }

    @Test
    void theStarterRegionIsResidentialWithARefugeAndACornerShop() {
        for (long seed : SEEDS) {
            DistrictPlan origin = CityPlanner.plan(seed, LAYOUT, 0, 0);
            assertEquals(UrbanDistrict.RESIDENTIAL, origin.district);
            ParcelPlan refuge = origin.refuge();
            assertNotNull(refuge, "seed " + seed);
            assertEquals(ParcelPlan.Use.REFUGE, refuge.use());
            assertEquals(CityPlanner.STARTER_BLOCK, refuge.superblock());
            assertEquals(SuperblockPlan.Kind.COURTYARD_HOUSING,
                    origin.superblock(CityPlanner.STARTER_BLOCK).kind());
            assertTrue(origin.parcels().stream().anyMatch(p -> p.superblock() == CityPlanner.STARTER_BLOCK
                    && p.use() == ParcelPlan.Use.COMMERCIAL), "a corner shop on the refuge's block");
            int[] anchor = origin.spawnAnchor();
            assertEquals(UrbanSurface.COURTYARD_PATH, origin.surfaceAt(anchor[0], anchor[1]));
            ParcelPlan.Entrance door = refuge.entrances().get(1);
            assertEquals(anchor[0], door.outsideX(), "the anchor is the step outside the refuge door");
            assertEquals(anchor[1], door.outsideZ());
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0) {
                        UrbanDistrict neighbour = CityPlanner.districtOf(seed, LAYOUT, dx, dz);
                        assertTrue(neighbour == UrbanDistrict.RESIDENTIAL
                                || neighbour == UrbanDistrict.COMMERCIAL, "starter neighbour " + neighbour);
                    }
                }
            }
            for (int[] region : REGIONS) {
                if (region[0] != 0 || region[1] != 0) {
                    assertNull(CityPlanner.plan(seed, LAYOUT, region[0], region[1]).refuge());
                }
            }
        }
    }

    @Test
    void districtsHaveStableCodesAndOnlyResidentialAndCommercialArePlannedYet() {
        Set<Integer> codes = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (UrbanDistrict district : UrbanDistrict.values()) {
            assertTrue(codes.add(district.code));
            assertTrue(ids.add(district.id));
            assertEquals(district, UrbanDistrict.fromCode(district.code));
            assertEquals(district, UrbanDistrict.fromId(" " + district.id.toUpperCase(java.util.Locale.ROOT)));
        }
        assertEquals(Set.of(1, 2, 3, 4, 5, 6), codes, "six identities with codes 1..6 (D-07)");
        assertThrows(IllegalArgumentException.class, () -> UrbanDistrict.fromCode(7));
        assertThrows(IllegalArgumentException.class, () -> UrbanDistrict.fromId("downtown"));

        Set<UrbanDistrict> planned = EnumSet.noneOf(UrbanDistrict.class);
        for (int rx = -10; rx <= 10; rx++) {
            for (int rz = -10; rz <= 10; rz++) {
                planned.add(CityPlanner.districtOf(SEEDS[0], LAYOUT, rx, rz));
            }
        }
        assertEquals(EnumSet.of(UrbanDistrict.RESIDENTIAL, UrbanDistrict.COMMERCIAL), planned);
        for (UrbanDistrict district : planned) {
            assertTrue(district.plannedInStreetLayout);
        }
    }

    @Test
    void everySuperblockSizeTheJitterAllowsHasAValidSubdivision() {
        // A fallback would leave a silent open lot, so no size may need one.
        assertEquals(46, StreetNetwork.MIN_SUPERBLOCK_SIDE);
        assertEquals(66, StreetNetwork.MAX_SUPERBLOCK_SIDE);
        for (int w = StreetNetwork.MIN_SUPERBLOCK_SIDE; w <= StreetNetwork.MAX_SUPERBLOCK_SIDE; w++) {
            for (int d = StreetNetwork.MIN_SUPERBLOCK_SIDE; d <= StreetNetwork.MAX_SUPERBLOCK_SIDE; d++) {
                assertTrue(CityPlanner.courtyardFits(w, d), "courtyard housing " + w + "x" + d);
                assertTrue(CityPlanner.shopRowFits(w, d), "shop row " + w + "x" + d);
            }
            for (int[] sides : CityPlanner.ringSides(w)) {
                int lo = CityPlanner.splitLow(w, sides[0]);
                int hi = CityPlanner.splitHigh(w, sides[1]);
                assertTrue(lo >= ParcelPlan.MIN_SIDE && hi <= ParcelPlan.MAX_SIDE && lo <= hi);
                assertTrue(w - sides[0] - sides[1] >= CityPlanner.MIN_COURTYARD);
            }
        }
    }

    @Test
    void manyRegionsSubdivideWithoutFallbackOrOverlap() {
        Set<SuperblockPlan.Kind> kinds = EnumSet.noneOf(SuperblockPlan.Kind.class);
        for (int rx = -12; rx < 12; rx++) {
            for (int rz = -6; rz < 6; rz++) {
                DistrictPlan plan = CityPlanner.plan(SEEDS[rx & 3], LAYOUT, rx, rz);
                assertEquals(0, plan.fallbackSuperblocks(), "region " + rx + "," + rz);
                assertEquals(0, plan.overlapConflicts(), "region " + rx + "," + rz);
                for (SuperblockPlan block : plan.superblocks()) {
                    kinds.add(block.kind());
                    assertTrue(block.width() >= StreetNetwork.MIN_SUPERBLOCK_SIDE
                            && block.width() <= StreetNetwork.MAX_SUPERBLOCK_SIDE, "width " + block.width());
                    assertTrue(block.depth() >= StreetNetwork.MIN_SUPERBLOCK_SIDE
                            && block.depth() <= StreetNetwork.MAX_SUPERBLOCK_SIDE, "depth " + block.depth());
                }
                for (ParcelPlan parcel : plan.parcels()) {
                    for (int z = parcel.z0(); z <= parcel.z1(); z++) {
                        for (int x = parcel.x0(); x <= parcel.x1(); x++) {
                            assertFalse(plan.surfaceAt(x, z).route, "a parcel covers a route");
                        }
                    }
                }
            }
        }
        // Canals and yards belong to layout nine's districts (DistrictPlannerTest).
        assertEquals(EnumSet.of(SuperblockPlan.Kind.COURTYARD_HOUSING, SuperblockPlan.Kind.SHOP_ROW,
                SuperblockPlan.Kind.PARK, SuperblockPlan.Kind.PLAZA), kinds, "every layout-two kind appears");
    }

    @Test
    void identitiesDoNotDependOnPlanningOrderOrCacheEviction() {
        long seed = SEEDS[1];
        UrbanWorldState forward = new UrbanWorldState(seed, World.GEN_URBAN_STREETS);
        UrbanWorldState churned = new UrbanWorldState(seed, World.GEN_URBAN_STREETS);
        DistrictPlan first = forward.regionPlan(-1, 1);
        // Plan the region, then forty others, evicting everything more than
        // once, then ask again: the second answer is a rebuild, not the cached one.
        DistrictPlan evicted = churned.regionPlan(-1, 1);
        for (int i = 0; i < 40; i++) {
            churned.regionPlan(i - 20, -i - 2);
            assertTrue(churned.cachedRegionPlans() <= UrbanWorldState.MAX_REGION_PLANS);
        }
        DistrictPlan later = churned.regionPlan(-1, 1);
        assertTrue(later != evicted, "the plan was evicted and rebuilt");
        DistrictPlan fresh = CityPlanner.plan(seed, LAYOUT, -1, 1);
        for (DistrictPlan other : List.of(later, fresh)) {
            assertEquals(first.parcels(), other.parcels(), "parcel ids, bounds and reservations");
            assertEquals(first.trees(), other.trees());
            assertEquals(first.superblocks(), other.superblocks());
            assertEquals(first.districtId, other.districtId);
            for (int z = first.originZ(); z < first.originZ() + StreetNetwork.REGION_SIZE; z += 3) {
                for (int x = first.originX(); x < first.originX() + StreetNetwork.REGION_SIZE; x += 3) {
                    assertEquals(first.surfaceAt(x, z), other.surfaceAt(x, z));
                }
            }
        }
        Set<Long> parcelIds = new HashSet<>();
        for (ParcelPlan parcel : first.parcels()) {
            assertEquals(UrbanIds.Kind.PARCEL, UrbanIds.kind(parcel.id()));
            assertEquals(first.districtId, UrbanIds.parent(parcel.id()));
            assertTrue(parcelIds.add(parcel.id()), "unique parcel id");
            assertEquals(parcel.id(), UrbanIds.parse(UrbanIds.format(parcel.id())));
        }
    }

    @Test
    void diagnosticsCatchAMismatchedSeamAndRefuseAWrongNeighbour() {
        DistrictPlan here = CityPlanner.plan(SEEDS[0], LAYOUT, 0, 0);
        // The east neighbour of another seed has its own avenue profile and street lines.
        DistrictPlan alien = CityPlanner.plan(SEEDS[2], LAYOUT, 1, 0);
        CityPlanDiagnostics.Report report = CityPlanDiagnostics.inspect(here, null, alien, null, null);
        assertFalse(report.ok());
        assertTrue(report.seamViolations() > 0);
        assertEquals(1, report.seamsChecked());
        assertTrue(report.messages().size() <= CityPlanDiagnostics.MAX_MESSAGES);
        assertTrue(report.summary().contains("ok=false"));
        DistrictPlan notAdjacent = CityPlanner.plan(SEEDS[0], LAYOUT, 2, 0);
        assertThrows(IllegalArgumentException.class,
                () -> CityPlanDiagnostics.inspect(here, null, notAdjacent, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> CityPlanDiagnostics.seamMismatches(here, notAdjacent));
    }

    @Test
    void diagnosticsCatchBrokenReservationsRoutesAndGapsAndCapTheirMessages() {
        DistrictPlan plan = CityPlanner.plan(SEEDS[0], LAYOUT, 0, 0);
        assertTrue(CityPlanDiagnostics.inspect(plan, null, null, null, null).ok());

        // An entrance painted over: the parcel loses its reserved door.
        ParcelPlan.Entrance door = plan.parcels().getFirst().primaryEntrance();
        CityPlanDiagnostics.Report noDoor = CityPlanDiagnostics.inspect(
                edited(plan, new int[][]{{door.x(), door.z()}}, UrbanSurface.LOT_EDGE, true),
                null, null, null, null);
        assertTrue(noDoor.parcelViolations() > 0 && !noDoor.ok(), noDoor.summary());

        // Both passages of a courtyard filled with lot: the courtyard is cut off.
        SuperblockPlan block = plan.superblock(CityPlanner.STARTER_BLOCK);
        List<int[]> passages = new java.util.ArrayList<>();
        for (int z = block.bounds().z0(); z <= block.bounds().z1(); z++) {
            for (int x = block.bounds().x0(); x <= block.bounds().x1(); x++) {
                if (plan.surfaceAt(x, z) == UrbanSurface.PASSAGE) {
                    passages.add(new int[]{x, z});
                }
            }
        }
        assertFalse(passages.isEmpty());
        CityPlanDiagnostics.Report cutOff = CityPlanDiagnostics.inspect(
                edited(plan, passages.toArray(new int[0][]), UrbanSurface.LOT, false),
                null, null, null, null);
        assertTrue(cutOff.routeViolations() > 0 && !cutOff.ok(), cutOff.summary());

        // An unassigned cell is an overlap-or-gap violation.
        int[] sidewalk = {plan.originX() + 5, plan.originZ() + 100};
        CityPlanDiagnostics.Report gap = CityPlanDiagnostics.inspect(
                edited(plan, new int[][]{sidewalk}, UrbanSurface.VOID, false), null, null, null, null);
        assertTrue(gap.overlapViolations() > 0 && !gap.ok(), gap.summary());

        // Every parcel's street door painted over: far more problems than messages.
        int[][] doors = plan.parcels().stream()
                .map(p -> new int[]{p.primaryEntrance().x(), p.primaryEntrance().z()})
                .toArray(int[][]::new);
        CityPlanDiagnostics.Report many = CityPlanDiagnostics.inspect(
                edited(plan, doors, UrbanSurface.LOT_EDGE, true), null, null, null, null);
        assertEquals(CityPlanDiagnostics.MAX_MESSAGES, many.messages().size());
        assertTrue(many.droppedMessages() > 0, "the message list is bounded");
        assertEquals(doors.length, many.parcelViolations(), "but the counter keeps the true total");
    }

    /**
     * A copy of a production plan with some cells repainted, built through the
     * same constructor the planner uses; {@code keepParcel} keeps each cell's
     * parcel index, otherwise it is cleared.
     */
    private static DistrictPlan edited(DistrictPlan plan, int[][] cells, UrbanSurface surface,
                                       boolean keepParcel) {
        int size = StreetNetwork.REGION_SIZE;
        byte[] surfaces = new byte[size * size];
        byte[] parcels = new byte[size * size];
        for (int lz = 0; lz < size; lz++) {
            for (int lx = 0; lx < size; lx++) {
                surfaces[lz * size + lx] = plan.surfaceAt(plan.originX() + lx, plan.originZ() + lz).code;
                parcels[lz * size + lx] = (byte) plan.parcelIndexAt(plan.originX() + lx, plan.originZ() + lz);
            }
        }
        for (int[] cell : cells) {
            int index = (cell[1] - plan.originZ()) * size + (cell[0] - plan.originX());
            surfaces[index] = surface.code;
            if (!keepParcel) {
                parcels[index] = (byte) DistrictPlan.NO_PARCEL;
            }
        }
        int[] northSouth = {plan.northSouthLine(1), plan.northSouthLine(2), plan.northSouthLine(3)};
        int[] eastWest = {plan.eastWestLine(1), plan.eastWestLine(2), plan.eastWestLine(3)};
        return new DistrictPlan(plan.seed, plan.layoutRevision, plan.rx, plan.rz, plan.district,
                northSouth, eastWest, plan.superblocks(), plan.parcels(), plan.trees(), surfaces, parcels,
                plan.refuge(), plan.spawnAnchor(), plan.overlapConflicts(), plan.fallbackSuperblocks());
    }

    @Test
    void avenueSegmentsMixPlainAndBoulevardProfiles() {
        Set<StreetNetwork.AvenueProfile> seen = EnumSet.noneOf(StreetNetwork.AvenueProfile.class);
        for (int line = -8; line < 8; line++) {
            for (int segment = -8; segment < 8; segment++) {
                seen.add(StreetNetwork.avenueProfile(SEEDS[0], LAYOUT, line % 2 == 0, line, segment));
            }
        }
        assertEquals(EnumSet.allOf(StreetNetwork.AvenueProfile.class), seen);
    }

    @Test
    void plansRefuseLayoutsAndRegionsTheyCannotPlan() {
        assertThrows(IllegalArgumentException.class,
                () -> CityPlanner.plan(1L, UrbanRevision.LAYOUT_DATUM_PLACEHOLDER, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> CityPlanner.plan(1L, LAYOUT, UrbanIds.MAX_REGION + 1, 0));
        assertFalse(CityPlanner.isPlannable(UrbanIds.MIN_REGION - 1, 0));
        assertTrue(CityPlanner.isPlannable(UrbanIds.MIN_REGION, UrbanIds.MAX_REGION));
    }
}
