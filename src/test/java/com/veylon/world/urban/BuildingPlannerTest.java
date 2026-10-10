package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan.Family;
import com.veylon.world.urban.BuildingPlan.Variant;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BuildingPlannerTest {
    static final long[] SEEDS = {20260731L, -4242L, 7L, 0L, 987654321L, -9000000000000000000L};

    @Test
    void revisionOneGeometryAndAllReservationMetadataMatchTheirRecordedFingerprints() {
        long[] pins = {0xb0f9b6f1052ccff7L, 0x3c648c643014bf19L,
                0xddb4eb4afe12a997L, 0xc9cadc31e53080b3L,
                0x1e8cd4d6638962beL, 0x5051b04a42623ef1L};
        int pin = 0;
        for (long seed : new long[]{20260731, -4242, 7}) {
            DistrictPlan district = CityPlanner.plan(seed, 2, 0, 0);
            for (ParcelPlan.Use use : new ParcelPlan.Use[]{ParcelPlan.Use.REFUGE, ParcelPlan.Use.COMMERCIAL}) {
                ParcelPlan p = district.parcels().stream().filter(it -> it.use() == use).findFirst().orElseThrow();
                assertEquals(pins[pin++], BuildingPlanDiagnostics.fingerprint(BuildingPlanner.plan(seed, 1, p).plan()),
                        "saved template revision one must retain geometry, functions, routes and reservation ids");
            }
        }
    }

    @Test
    void apartmentAndShopMatrixHasRealClearanceAndBidirectionalRoomRoutes() {
        EnumSet<BuildingPlan.Function> functions = EnumSet.noneOf(BuildingPlan.Function.class);
        for (int[] size : new int[][]{{12, 16}, {16, 20}, {20, 24}, {12, 12}}) {
            for (ParcelPlan.Side side : ParcelPlan.Side.values()) {
                for (Variant variant : Variant.values()) {
                    for (Family family : Family.values()) {
                        for (long seed : SEEDS) {
                            ParcelPlan parcel = parcel(size[0], size[1], side, 14, 14);
                            int floors = family == Family.SHOP ? 2 : 2 + (int) Math.floorMod(seed, 4);
                            BuildingPlan p = BuildingTemplates.propose(parcel, 1, family, variant, floors,
                                    family == Family.APARTMENT && (seed & 1) == 1, false);
                            AccessibilityValidator.Report report = AccessibilityValidator.inspect(p);
                            assertTrue(report.ok(), family + " " + size[0] + "x" + size[1] + " " + side
                                    + " " + variant + " seed=" + seed + " " + report.summary());
                            assertTrue(report.cellsExamined() <= PlanVolume.MAX_CELLS);
                            assertTrue(report.transitions() <= AccessibilityValidator.MAX_TRANSITIONS);
                            assertTrue(report.forwardVisits() <= report.cellsExamined());
                            assertTrue(report.reverseVisits() <= report.cellsExamined());
                            assertEquals(p, BuildingTemplates.propose(parcel, 1, family, variant, floors,
                                    family == Family.APARTMENT && (seed & 1) == 1, false));
                            for (BuildingPlan.Floor f : p.floors()) {
                                for (BuildingPlan.Room r : f.rooms()) { functions.addAll(r.functions()); }
                            }
                        }
                    }
                }
            }
        }
        assertEquals(EnumSet.allOf(BuildingPlan.Function.class), functions, "all twelve initial room functions exist");
    }

    @Test
    void everyParcelInTheFrozenCitySeedRegionMatrixFitsItsNeighboursAndApproachRoutes() {
        int checked = 0, repaired = 0;
        for (long seed : SEEDS) {
            for (int[] coordinate : new int[][]{{0, 0}, {-1, -1}, {1, -1}, {-1, 1}, {2, 3}, {-3, -2},
                    {4096, -4096}, {-4097, 4095}}) {
                DistrictPlan district = CityPlanner.plan(seed, 2, coordinate[0], coordinate[1]);
                for (ParcelPlan parcel : district.parcels()) {
                    BuildingPlanner.Result result = BuildingPlanner.plan(seed, 1, parcel);
                    assertTrue(result.attempts() <= BuildingPlanner.MAX_ATTEMPTS);
                    AccessibilityValidator.Report report = AccessibilityValidator.inspect(result.plan(), district);
                    assertTrue(report.ok(), "seed=" + seed + " region=" + district.rx + "," + district.rz
                            + " parcel=" + parcel.index() + " " + report.summary());
                    repaired += result.attempts() > 1 ? 1 : 0;
                    checked++;
                }
            }
        }
        System.out.println("building-city-matrix plans=" + checked + " repaired=" + repaired);
    }

    @Test
    void productionPlanningAndChunkQueriesArePureUnderShuffledRegionAccess() {
        UrbanWorldState a = new UrbanWorldState(SEEDS[0], World.GEN_URBAN_PLANS);
        UrbanWorldState b = new UrbanWorldState(SEEDS[0], World.GEN_URBAN_PLANS);
        List<ParcelPlan> parcels = new ArrayList<>();
        for (int[] region : new int[][]{{0, 0}, {-1, -1}, {2, 3}}) {
            parcels.addAll(a.regionPlan(region[0], region[1]).parcels().subList(0, 6));
        }
        Collections.shuffle(parcels, new Random(19));
        for (ParcelPlan p : parcels) {
            BuildingPlan planned = a.buildingAt(p.x0(), p.z0());
            assertEquals(planned, b.buildingAt(p.x1(), p.z1()));
            DistrictPlan district = a.regionPlan(UrbanIds.regionX(p.id()), UrbanIds.regionZ(p.id()));
            assertTrue(AccessibilityValidator.inspect(planned, district).ok());
        }
        World world = new World(SEEDS[0], World.GEN_URBAN_PLANS);
        ParcelPlan p = world.urban.regionPlan(0, 0).parcels().getFirst();
        BuildingPlan before = world.urban.buildingAt(p.x0(), p.z0());
        assertEquals(0, world.loadedCount());
        assertTrue(world.changedBlocks.isEmpty());
        assertTrue(world.crateContents.isEmpty());
        assertEquals(0, world.fixtures.size());
        world.getOrCreateChunk(Math.floorDiv(p.x0(), 16), Math.floorDiv(p.z0(), 16));
        assertEquals(before, world.urban.buildingAt(p.x1(), p.z1()));
        assertEquals(BlockType.AIR, world.getBlock(p.x0(), 41, p.z0()), "05 does not stamp buildings");
        assertNull(new World(7, World.GEN_URBAN_STREETS).urban.buildingAt(20, 20));
    }

    @Test
    void proposedBlocksAndReservationsMatchAfterReversedAndShuffledChunkAndRegionAccess() {
        for (long seed : SEEDS) {
            World forward = new World(seed, World.GEN_URBAN_PLANS);
            World shuffled = new World(seed, World.GEN_URBAN_PLANS);
            List<ParcelPlan> parcels = new ArrayList<>();
            LinkedHashSet<Vec3i> chunks = new LinkedHashSet<>();
            for (int[] r : new int[][]{{0, 0}, {-1, -1}, {2, 3}, {4096, -4096}}) {
                DistrictPlan district = forward.urban.regionPlan(r[0], r[1]);
                parcels.addAll(district.parcels().subList(0, 2));
            }
            List<BuildingPlan> expected = new ArrayList<>();
            for (ParcelPlan parcel : parcels) {
                BuildingPlan p = forward.urban.buildingAt(parcel.x0(), parcel.z0());
                expected.add(p);
                for (int cx = Math.floorDiv(parcel.x0(), 16); cx <= Math.floorDiv(parcel.x1(), 16); cx++) {
                    for (int cz = Math.floorDiv(parcel.z0(), 16); cz <= Math.floorDiv(parcel.z1(), 16); cz++) {
                        chunks.add(new Vec3i(cx, 0, cz));
                    }
                }
            }
            assertTrue(chunks.size() <= 225);
            for (Vec3i chunk : chunks) { forward.getOrCreateChunk(chunk.x(), chunk.z()); }
            List<Vec3i> order = new ArrayList<>(chunks);
            Collections.shuffle(order, new Random(9182));
            for (Vec3i chunk : order) { shuffled.getOrCreateChunk(chunk.x(), chunk.z()); }
            for (int i = parcels.size() - 1; i >= 0; i--) {
                ParcelPlan parcel = parcels.get(i);
                assertEquals(expected.get(i), shuffled.urban.buildingAt(parcel.x1(), parcel.z1()),
                        "every proposed voxel, room, stair, fixture and target keeps its identity");
            }
            assertEquals(forward.loadedCount(), shuffled.loadedCount());
            assertTrue(forward.changedBlocks.isEmpty());
            assertTrue(shuffled.changedBlocks.isEmpty());
        }
    }

    @Test
    void abandonmentKeepsCommonRoomAndFixtureReservationIdsAndPositions() {
        BuildingPlan intact = example();
        for (Variant variant : Variant.values()) {
            BuildingPlan p = BuildingTemplates.propose(intact.parcel(), 1, Family.APARTMENT, variant, 3, true, false);
            assertEquals(intact.floors(), p.floors());
            for (BuildingPlan.Fixture f : intact.fixtures()) {
                BuildingPlan.Fixture other = p.fixtures().stream().filter(it -> it.id() == f.id()).findFirst().orElseThrow();
                assertEquals(f.position(), other.position());
                assertEquals(f.target(), other.target());
            }
        }
    }

    @Test
    void invalidProposalRepairsToAValidatedIntactAlternateInFiniteWork() {
        BuildingPlan p = example();
        PlanVolume.Builder sealed = p.voxels().edit();
        for (BuildingPlan.Fixture fixture : p.fixtures()) {
            if (fixture.kind() == BuildingPlan.FixtureKind.DOOR) {
                for (int h = 0; h < 2; h++) {
                    Vec3i pos = fixture.position();
                    sealed.set(pos.x(), pos.y() + h, pos.z(), BlockType.BRICK);
                }
            }
        }
        BuildingPlanner.Result fixed = BuildingPlanner.repair(p.withVoxels(sealed.build()));
        assertEquals(2, fixed.attempts());
        assertTrue(fixed.validation().ok());
        assertEquals(1, fixed.repairs().size());
        assertEquals(p.id(), fixed.plan().id());
        assertEquals(p.storeys(), fixed.plan().storeys());
    }

    static BuildingPlan example() {
        return BuildingTemplates.propose(parcel(16, 20, ParcelPlan.Side.NORTH, 14, 14),
                1, Family.APARTMENT, Variant.INTACT, 3, true, false);
    }

    static ParcelPlan parcel(int width, int depth, ParcelPlan.Side side, int x0, int z0) {
        boolean alongX = side.dx == 0;
        int wx = alongX ? width : depth, wz = alongX ? depth : width;
        int x1 = x0 + wx - 1, z1 = z0 + wz - 1;
        int e = (width - 2) / 2;
        ParcelPlan.Entrance front = switch (side) {
            case NORTH -> new ParcelPlan.Entrance(x0 + e, z0, side, true);
            case SOUTH -> new ParcelPlan.Entrance(x0 + e, z1, side, true);
            case WEST -> new ParcelPlan.Entrance(x0, z0 + e, side, true);
            case EAST -> new ParcelPlan.Entrance(x1, z0 + e, side, true);
        };
        ParcelPlan.Entrance rear = switch (side) {
            case NORTH -> new ParcelPlan.Entrance(x0 + e, z1, side.opposite(), false);
            case SOUTH -> new ParcelPlan.Entrance(x0 + e, z0, side.opposite(), false);
            case WEST -> new ParcelPlan.Entrance(x1, z0 + e, side.opposite(), false);
            case EAST -> new ParcelPlan.Entrance(x0, z0 + e, side.opposite(), false);
        };
        ParcelPlan.Rect core = switch (side) {
            case NORTH -> new ParcelPlan.Rect(x0 + e + 2, z0 + 1, x0 + e + 5, z0 + 6);
            case SOUTH -> new ParcelPlan.Rect(x0 + e + 2, z1 - 6, x0 + e + 5, z1 - 1);
            case WEST -> new ParcelPlan.Rect(x0 + 1, z0 + e + 2, x0 + 6, z0 + e + 5);
            case EAST -> new ParcelPlan.Rect(x1 - 6, z0 + e + 2, x1 - 1, z0 + e + 5);
        };
        long id = UrbanIds.parcel(UrbanIds.district(Math.floorDiv(x0, 256), Math.floorDiv(z0, 256)), 0);
        return new ParcelPlan(0, id, 0, ParcelPlan.Use.RESIDENTIAL, x0, z0, x1, z1, side,
                List.of(front, rear), core, 5);
    }
}
