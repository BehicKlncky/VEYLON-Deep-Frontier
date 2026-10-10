package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.FixtureStates;
import com.veylon.world.urban.BuildingPlan.Fixture;
import com.veylon.world.urban.BuildingPlan.FixtureKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AccessibilityValidatorTest {
    @Test
    void visuallyNamedDoorsCannotTurnSolidWallsIntoGraphEdges() {
        BuildingPlan p = BuildingPlannerTest.example();
        PlanVolume.Builder volume = p.voxels().edit();
        List<Fixture> fixtures = new ArrayList<>();
        for (Fixture f : p.fixtures()) {
            if (f.kind() == FixtureKind.DOOR) {
                for (int h = 0; h < 2; h++) { set(volume, f.position(), h, BlockType.BRICK); }
                fixtures.add(new Fixture(f.id(), f.kind(), f.position(), 2, BlockType.BRICK, f.target(), false));
            } else { fixtures.add(f); }
        }
        rejected(p.withVoxels(volume.build()).withFixtures(fixtures), "physical action");
    }

    @Test
    void allEntrancesLockedFailsEvenIfAQuestReservationIsLabelledAsAKey() {
        BuildingPlan p = occupied();
        List<Fixture> fixtures = p.fixtures().stream().map(f -> {
            boolean exterior = f.kind() == FixtureKind.DOOR && f.position().y() == 41
                    && p.parcel().onOutline(f.position().x(), f.position().z());
            return exterior ? new Fixture(f.id(), f.kind(), f.position(), f.height(), f.block(), f.target(), true) : f;
        }).toList();
        rejected(p.withFixtures(fixtures), "unreachable");
        assertTrue(AccessibilityValidator.inspect(p).ok(), "the original has an attainable unlatched rear entry");
    }

    @Test
    void lowDoorAndWindowThroughSlabAreRefused() {
        BuildingPlan p = BuildingPlannerTest.example();
        Fixture door = first(p, FixtureKind.DOOR);
        rejected(replace(p, door, new Fixture(door.id(), door.kind(), door.position(), 1,
                door.block(), door.target(), false)), "floor bands");
        Fixture window = first(p, FixtureKind.WINDOW);
        Vec3i pos = window.position();
        rejected(replace(p, window, new Fixture(window.id(), window.kind(),
                new Vec3i(pos.x(), pos.y() + 1, pos.z()), 2, window.block(), window.target(), false)), "floor bands");
    }

    @Test
    void lowStairCeilingMissingTreadAndNarrowFlightFailProductionChecks() {
        BuildingPlan p = BuildingPlannerTest.example();
        Vec3i departure = p.stairs().getFirst().route().getFirst();
        rejected(p.withVoxels(p.voxels().edit()
                .set(departure.x(), departure.y() + 2, departure.z(), BlockType.CONCRETE).build()), "STAIR");
        Vec3i step = p.stairs().getFirst().route().get(1);
        rejected(p.withVoxels(p.voxels().edit()
                .set(step.x(), step.y() - 1, step.z(), BlockType.AIR).build()), "STAIR");
        // Remove the other lane of the first step, keeping the declared route intact.
        rejected(p.withVoxels(p.voxels().edit()
                .set(step.x() + 1, step.y() - 1, step.z(), BlockType.AIR).build()), "narrower");
    }

    @Test
    void solidFurnitureAndQuestTargetsNeedActualInteractionClearance() {
        BuildingPlan p = BuildingPlannerTest.example();
        Fixture cabinet = first(p, FixtureKind.CONTAINER);
        Vec3i target = cabinet.target();
        rejected(p.withVoxels(p.voxels().edit().set(target.x(), target.y(), target.z(), BlockType.TABLE).build()),
                "CONTAINER: unreachable");
        Fixture quest = first(p, FixtureKind.QUEST);
        rejected(replace(p, quest, new Fixture(quest.id(), quest.kind(), quest.position(), 1,
                BlockType.AIR, cabinet.position(), false)), "QUEST: unreachable");
    }

    @Test
    void reachableThreeBlockDropWithoutReturnIsNotAnExitRoute() {
        BuildingPlan p = BuildingPlannerTest.example();
        Fixture quest = p.fixtures().stream().filter(f -> f.kind() == FixtureKind.QUEST
                && f.position().y() == 41).findFirst().orElseThrow();
        Vec3i at = quest.position(), bottom = new Vec3i(at.x(), 38, at.z());
        PlanVolume.Builder pit = p.voxels().edit();
        for (int y = 38; y <= 40; y++) { pit.set(at.x(), y, at.z(), BlockType.AIR); }
        pit.set(at.x(), 37, at.z(), BlockType.CONCRETE);
        // The existing basement is open; wall off the pit below the ground-floor slab.
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int y = 37; y <= 40; y++) { pit.set(at.x() + d[0], y, at.z() + d[1], BlockType.CONCRETE); }
        }
        BuildingPlan changed = replace(p.withVoxels(pit.build()), quest,
                new Fixture(quest.id(), quest.kind(), bottom, 1, BlockType.AIR, bottom, false));
        rejected(changed, "no exit route");
    }

    @Test
    void missingRoomFloorCannotBeIgnoredAsANonStandingCell() {
        BuildingPlan p = BuildingPlannerTest.example();
        int x = p.parcel().x0() + 2, z = p.parcel().z0() + 2;
        assertEquals(BlockType.AIR, p.voxels().getBlock(x, 41, z));
        rejected(p.withVoxels(p.voxels().edit().set(x, 40, z, BlockType.AIR).build()), "ROOM_CLEARANCE");
        rejected(p.withVoxels(p.voxels().edit().set(x, 42, z, BlockType.CONCRETE).build()), "ROOM_CLEARANCE");
    }

    @Test
    void missingFloorOverflowOverlappingFootprintAndExcessiveMetadataAreRejected() {
        BuildingPlan p = BuildingPlannerTest.example();
        BuildingPlan floorMissing = new BuildingPlan(p.id(), 1, p.template(), p.family(), p.variant(), p.parcel(),
                3, true, p.floors().subList(1, p.floors().size()), p.fixtures(), p.approaches(), p.stairs(), p.voxels());
        rejected(floorMissing, "FLOOR");
        BuildingPlan overflow = new BuildingPlan(p.id(), 1, p.template(), p.family(), p.variant(), p.parcel(),
                12, true, p.floors(), p.fixtures(), p.approaches(), p.stairs(), p.voxels());
        rejected(overflow, "ENVELOPE");
        assertTrue(AccessibilityValidator.overlaps(p.parcel(), p.parcel()));
        assertFalse(AccessibilityValidator.overlaps(p.parcel(),
                BuildingPlannerTest.parcel(16, 20, ParcelPlan.Side.NORTH, 100, 100)));
        List<Fixture> excessive = new ArrayList<>();
        for (int i = 0; i <= AccessibilityValidator.MAX_FIXTURES; i++) { excessive.add(p.fixtures().getFirst()); }
        AccessibilityValidator.Report report = AccessibilityValidator.inspect(p.withFixtures(excessive));
        assertFalse(report.ok());
        assertEquals(0, report.cellsExamined(), "bounds reject before voxel work");
        assertThrows(IllegalArgumentException.class, () -> new PlanVolume.Builder(0, 0, 100, 100, 0, 95));
    }

    @Test
    void planLocksHaveSavedOverridesWithoutMutatingTheProposal() {
        BuildingPlan p = occupied();
        Fixture door = p.fixtures().stream().filter(Fixture::locked).findFirst().orElseThrow();
        FixtureStates saved = new FixtureStates();
        Vec3i at = door.position();
        assertEquals(FixtureStates.LOCK_KEYED, p.fixtureStateAt(saved, at.x(), at.y(), at.z()));
        assertTrue(saved.set(at.x(), at.y(), at.z(), FixtureStates.OPEN));
        assertEquals(FixtureStates.OPEN, p.fixtureStateAt(saved, at.x(), at.y(), at.z()));
        assertEquals(FixtureStates.LOCK_KEYED, p.fixtureStateAt(saved, at.x(), at.y() + 1, at.z()));
        assertTrue(saved.set(at.x(), at.y(), at.z(), 0));
        assertEquals(0, p.fixtureStateAt(saved, at.x(), at.y(), at.z()), "explicit saved unlock overrides default");
        assertEquals(BlockType.METAL_DOOR, p.voxels().getBlock(at.x(), at.y(), at.z()));
        assertTrue(AccessibilityValidator.inspect(p).ok());
    }

    @Test
    void impossibleParcelContractExhaustsThreeAttemptsAndNeverReturnsASealedShell() {
        ParcelPlan good = BuildingPlannerTest.example().parcel();
        ParcelPlan impossible = new ParcelPlan(good.index(), good.id(), good.superblock(), good.use(),
                good.x0(), good.z0(), good.x1(), good.z1(), good.frontage(), good.entrances(),
                new ParcelPlan.Rect(good.x0(), good.z0(), good.x0(), good.z0()), good.maxStoreys());
        BuildingPlan proposal = BuildingTemplates.propose(impossible, 1, BuildingPlan.Family.APARTMENT,
                BuildingPlan.Variant.INTACT, 2, false, false);
        IllegalStateException failed = assertThrows(IllegalStateException.class, () -> BuildingPlanner.repair(proposal));
        assertTrue(failed.getMessage().contains("attempt=3"));
        assertTrue(failed.getMessage().contains("reserved core"));
        assertThrows(IllegalArgumentException.class, () -> BuildingTemplates.propose(good, 2,
                BuildingPlan.Family.APARTMENT, BuildingPlan.Variant.INTACT, 2, false, false));
    }

    @Test
    void minimalFallbackStillHasAllRoomsAndRoofAccess() {
        for (FamilyAndBasement choice : List.of(new FamilyAndBasement(BuildingPlan.Family.APARTMENT, true),
                new FamilyAndBasement(BuildingPlan.Family.SHOP, false))) {
            BuildingPlan p = BuildingTemplates.propose(BuildingPlannerTest.parcel(12, 12, ParcelPlan.Side.EAST, -20, -20),
                    1, choice.family(), BuildingPlan.Variant.INTACT, 2, choice.basement(), true);
            assertTrue(AccessibilityValidator.inspect(p).ok());
            assertTrue(p.fixtures().stream().anyMatch(f -> f.kind() == FixtureKind.QUEST));
        }
    }

    private record FamilyAndBasement(BuildingPlan.Family family, boolean basement) { }
    private static BuildingPlan occupied() {
        return BuildingTemplates.propose(BuildingPlannerTest.example().parcel(), 1,
                BuildingPlan.Family.APARTMENT, BuildingPlan.Variant.OCCUPIED, 3, true, false);
    }
    private static Fixture first(BuildingPlan p, FixtureKind kind) {
        return p.fixtures().stream().filter(f -> f.kind() == kind).findFirst().orElseThrow();
    }
    private static BuildingPlan replace(BuildingPlan p, Fixture old, Fixture replacement) {
        return p.withFixtures(p.fixtures().stream().map(f -> f.id() == old.id() ? replacement : f).toList());
    }
    private static void set(PlanVolume.Builder b, Vec3i p, int h, BlockType t) {
        b.set(p.x(), p.y() + h, p.z(), t);
    }
    private static void rejected(BuildingPlan p, String diagnostic) {
        AccessibilityValidator.Report report = AccessibilityValidator.inspect(p);
        assertFalse(report.ok(), "invalid fixture accepted");
        assertTrue(report.messages().stream().anyMatch(m -> m.contains(diagnostic)), report.summary());
        assertTrue(report.messages().size() <= AccessibilityValidator.MAX_MESSAGES);
    }
}
