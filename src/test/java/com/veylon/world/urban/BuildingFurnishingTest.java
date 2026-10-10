package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.VoxelAccess;
import com.veylon.world.urban.BuildingPlan.Family;
import com.veylon.world.urban.BuildingPlan.Fixture;
import com.veylon.world.urban.BuildingPlan.FixtureKind;
import com.veylon.world.urban.BuildingPlan.Function;
import com.veylon.world.urban.BuildingPlan.Variant;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuildingFurnishingTest {
    @Test
    void frozenRevisionTwoPinsGeometryFunctionsFurnitureReservationsAndRoutes() {
        long[] pins = {0x6a4838a4148b857eL, 0x68a7e776ba630523L,
                0xf9d269ca745af303L, 0x146e8c354d0b1101L,
                0x2dde08c882a0afe3L, 0x9ef553817a50e2a7L};
        int pin = 0;
        for (long seed : new long[]{20260731, -4242, 7}) {
            DistrictPlan district = CityPlanner.plan(seed, 2, 0, 0);
            for (ParcelPlan.Use use : new ParcelPlan.Use[]{ParcelPlan.Use.REFUGE, ParcelPlan.Use.COMMERCIAL}) {
                ParcelPlan parcel = district.parcels().stream().filter(p -> p.use() == use).findFirst().orElseThrow();
                BuildingPlan plan = BuildingPlanner.plan(seed, 2, parcel).plan();
                assertEquals(pins[pin++], BuildingPlanDiagnostics.fingerprint(plan),
                        "saved furnished revision two requires a new revision for geometry or reservation changes");
            }
        }
    }

    @Test
    void generatedProofRefusesUnloadedColumnsBeforeReadingTheirAir() {
        DistrictPlan district = CityPlanner.plan(7, 2, 0, 0);
        BuildingPlan plan = BuildingPlanner.plan(7, 2, district.refuge()).plan();
        VoxelAccess unloaded = new VoxelAccess() {
            @Override public boolean isColumnLoaded(int x, int z) { return false; }
            @Override public BlockType getBlock(int x, int y, int z) {
                fail("unavailable columns must never be treated as generated AIR");
                return BlockType.AIR;
            }
        };
        AccessibilityValidator.Report report = AccessibilityValidator.inspectGenerated(plan, district, unloaded);
        assertFalse(report.ok());
        assertEquals(0, report.cellsExamined());
        assertTrue(report.messages().size() <= AccessibilityValidator.MAX_MESSAGES);
        assertTrue(report.messages().stream().allMatch(m -> m.startsWith("LOADED:")));
    }

    @Test
    void generatedProofAllowsNeighbourCellsInTheHaloWhileProposalOwnershipStaysStrict() {
        DistrictPlan district = CityPlanner.plan(7, 2, 0, 0);
        BuildingPlan plan = BuildingPlanner.plan(7, 2, district.refuge()).plan();
        PlanVolume volume = plan.voxels();
        PlanVolume.Builder neighbour = volume.edit();
        for (int y = 41; y <= volume.maxY; y++) {
            neighbour.set(volume.x0, y, volume.z0, BlockType.BRICK);
        }
        PlanVolume actual = neighbour.build();
        assertFalse(AccessibilityValidator.inspect(plan.withVoxels(actual), district).ok(),
                "a proposal may never claim its approach halo");
        AccessibilityValidator.Report report = AccessibilityValidator.inspectGenerated(plan, district, actual);
        assertTrue(report.ok(), report.summary());
        assertEquals(volume.cellCount(), report.cellsExamined());
    }

    @Test
    void furnishedFamilyOrientationSizeAndAbandonmentMatrixRetainsPhysicalEntryAndReturnRoutes() {
        EnumSet<BlockType> furniture = EnumSet.noneOf(BlockType.class);
        for (int[] size : new int[][]{{12, 12}, {12, 16}, {16, 20}, {20, 24}, {32, 32}}) {
            for (ParcelPlan.Side side : ParcelPlan.Side.values()) {
                for (Variant variant : Variant.values()) {
                    for (Family family : Family.values()) {
                        for (boolean minimal : new boolean[]{false, true}) {
                            ParcelPlan parcel = BuildingPlannerTest.parcel(size[0], size[1], side, 14, 14);
                            BuildingPlan plan = BuildingTemplates.propose(parcel, 2, family, variant,
                                    family == Family.SHOP ? 2 : 5, family == Family.APARTMENT, minimal);
                            AccessibilityValidator.Report report = AccessibilityValidator.inspect(plan);
                            assertTrue(report.ok(), family + " " + side + " " + variant + " " + size[0] + "x"
                                    + size[1] + " minimal=" + minimal + " " + report.summary());
                            assertEquals(plan, BuildingTemplates.propose(parcel, 2, family, variant,
                                    family == Family.SHOP ? 2 : 5, family == Family.APARTMENT, minimal));
                            assertTrue(report.cellsExamined() <= PlanVolume.MAX_CELLS);
                            assertTrue(report.transitions() <= AccessibilityValidator.MAX_TRANSITIONS);
                            for (Fixture fixture : plan.fixtures()) {
                                if (fixture.kind() == FixtureKind.FURNITURE) { furniture.add(fixture.block()); }
                                if (fixture.kind() == FixtureKind.CONTAINER) {
                                    assertNotNull(BuildingFurnishing.containerUse(plan, fixture));
                                }
                            }
                        }
                    }
                }
            }
        }
        assertTrue(furniture.containsAll(EnumSet.of(BlockType.CAMP_BED, BlockType.RAIN_COLLECTOR,
                BlockType.TABLE, BlockType.SHEET_METAL, BlockType.DEBRIS, BlockType.BUSH)));
    }

    @Test
    void everyParcelInTheFrozenSixSeedEightRegionMatrixHasAccessibleFurnishedInteriors() {
        int checked = 0, repaired = 0;
        EnumSet<ParcelPlan.Use> uses = EnumSet.noneOf(ParcelPlan.Use.class);
        EnumSet<Function> functions = EnumSet.noneOf(Function.class);
        for (long seed : BuildingPlannerTest.SEEDS) {
            for (int[] coordinate : new int[][]{{0, 0}, {-1, -1}, {1, -1}, {-1, 1}, {2, 3}, {-3, -2},
                    {4096, -4096}, {-4097, 4095}}) {
                DistrictPlan district = CityPlanner.plan(seed, 2, coordinate[0], coordinate[1]);
                for (ParcelPlan parcel : district.parcels()) {
                    BuildingPlanner.Result result = BuildingPlanner.plan(seed, 2, parcel);
                    assertTrue(result.validation().ok());
                    assertTrue(AccessibilityValidator.inspect(result.plan(), district).ok(),
                            "seed=" + seed + " parcel=" + UrbanIds.format(parcel.id()));
                    assertEquals(2, result.plan().templateRevision());
                    for (BuildingPlan.Floor floor : result.plan().floors()) {
                        for (BuildingPlan.Room room : floor.rooms()) { functions.addAll(room.functions()); }
                    }
                    uses.add(parcel.use());
                    repaired += result.attempts() > 1 ? 1 : 0;
                    checked++;
                }
            }
        }
        assertEquals(EnumSet.allOf(ParcelPlan.Use.class), uses, "service parcels also have real interiors");
        assertEquals(EnumSet.allOf(Function.class), functions);
        System.out.println("furnished-building-city-matrix plans=" + checked + " repaired=" + repaired);
    }

    @Test
    void roomFunctionsHaveDistinctVisibleFurnitureAndContextualSearchReservations() {
        BuildingPlan plan = BuildingTemplates.propose(BuildingPlannerTest.example().parcel(), 2,
                Family.APARTMENT, Variant.INTACT, 3, true, false);
        for (BuildingPlan.Floor floor : plan.floors()) {
            if (floor.index() < 0 || floor.index() == plan.storeys()) { continue; }
            for (BuildingPlan.Room room : floor.rooms()) {
                List<Fixture> furnishings = plan.fixtures().stream()
                        .filter(f -> UrbanIds.parent(f.id()) == room.id()).toList();
                assertTrue(furnishings.size() <= 6);
                if (room.functions().contains(Function.BEDROOM)) {
                    assertTrue(furnishings.stream().anyMatch(f -> f.block() == BlockType.CAMP_BED));
                    Fixture wardrobe = furnishings.stream().filter(f -> f.kind() == FixtureKind.CONTAINER)
                            .findFirst().orElseThrow();
                    assertEquals(BuildingFurnishing.ContainerUse.WARDROBE, BuildingFurnishing.containerUse(plan, wardrobe));
                }
                if (room.functions().contains(Function.KITCHEN)) {
                    assertTrue(furnishings.stream().anyMatch(f -> f.block() == BlockType.CABINET));
                    assertTrue(furnishings.stream().anyMatch(f -> f.block() == BlockType.TABLE));
                    assertTrue(furnishings.stream().anyMatch(f -> f.block() == BlockType.RAIN_COLLECTOR));
                }
                if (room.functions().contains(Function.BATHROOM)) {
                    assertTrue(furnishings.stream().anyMatch(f -> f.block() == BlockType.RAIN_COLLECTOR));
                }
            }
        }
    }

    @Test
    void damagedPartitionAndSparseGrowthNeverMoveStructuralReservationsOrSearchableIdentities() {
        ParcelPlan parcel = BuildingPlannerTest.example().parcel();
        BuildingPlan intact = BuildingTemplates.propose(parcel, 2, Family.APARTMENT, Variant.INTACT, 3, true, false);
        BuildingPlan damaged = BuildingTemplates.propose(parcel, 2, Family.APARTMENT, Variant.DAMAGED, 3, true, false);
        assertEquals(intact.floors(), damaged.floors());
        assertEquals(intact.stairs(), damaged.stairs());
        for (Fixture fixture : intact.fixtures()) {
            Fixture other = damaged.fixtures().stream().filter(f -> f.id() == fixture.id()).findFirst().orElseThrow();
            assertEquals(fixture.position(), other.position());
            assertEquals(fixture.target(), other.target());
            if (fixture.kind() == FixtureKind.CONTAINER) {
                assertEquals(fixture.kind(), other.kind());
                assertEquals(fixture.block(), other.block());
            }
        }
        int partitionHoles = 0, plants = 0;
        PlanVolume a = intact.voxels(), b = damaged.voxels();
        for (int x = parcel.x0(); x <= parcel.x1(); x++) {
            for (int z = parcel.z0(); z <= parcel.z1(); z++) {
                for (int y = a.minY; y <= a.maxY; y++) {
                    if (a.getBlock(x, y, z) == BlockType.PLASTER && b.getBlock(x, y, z) == BlockType.AIR) {
                        partitionHoles++;
                    }
                    if (b.getBlock(x, y, z) == BlockType.BUSH) { plants++; }
                }
            }
        }
        assertEquals(6, partitionHoles, "two upper partition cells removed from each of three domestic floors");
        assertEquals(1, plants, "one growth at the damaged ground facade, no indoor thicket");
        assertTrue(AccessibilityValidator.inspect(damaged).ok());
    }

    @Test
    void productionValidatorRejectsExcessiveRoomAndApartmentReservations() {
        BuildingPlan plan = BuildingTemplates.propose(BuildingPlannerTest.example().parcel(), 2,
                Family.APARTMENT, Variant.INTACT, 3, true, false);
        BuildingPlan.Room room = plan.floors().stream().filter(f -> f.index() == 0).findFirst().orElseThrow()
                .rooms().stream().filter(r -> r.functions().contains(Function.KITCHEN)).findFirst().orElseThrow();
        List<Fixture> fixtures = new ArrayList<>(plan.fixtures());
        fixtures.add(new Fixture(UrbanIds.fixture(room.id(), 6), FixtureKind.QUEST, room.anchor(), 1,
                BlockType.AIR, room.anchor(), false));
        AccessibilityValidator.Report excessive = AccessibilityValidator.inspect(plan.withFixtures(fixtures));
        assertFalse(excessive.ok());
        assertTrue(excessive.messages().stream().anyMatch(m -> m.startsWith("FURNISHING:")));

        fixtures = new ArrayList<>(plan.fixtures());
        PlanVolume.Builder emptied = plan.voxels().edit();
        fixtures.removeIf(fixture -> {
            if (fixture.kind() != FixtureKind.CONTAINER || UrbanIds.floorIndex(fixture.id()) != 0) { return false; }
            Vec3i position = fixture.position();
            emptied.set(position.x(), position.y(), position.z(), BlockType.AIR);
            return true;
        });
        BuildingPlan noSearch = plan.withVoxels(emptied.build()).withFixtures(fixtures);
        AccessibilityValidator.Report missing = AccessibilityValidator.inspect(noSearch);
        assertFalse(missing.ok());
        assertTrue(missing.messages().stream().anyMatch(m -> m.contains("apartment searchable reservation budget")));
    }
}
