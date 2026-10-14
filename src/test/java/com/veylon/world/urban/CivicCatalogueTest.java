package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.urban.BuildingFurnishing.ContainerUse;
import com.veylon.world.urban.BuildingPlan.Family;
import com.veylon.world.urban.BuildingPlan.Fixture;
import com.veylon.world.urban.BuildingPlan.FixtureKind;
import com.veylon.world.urban.BuildingPlan.Function;
import com.veylon.world.urban.BuildingPlan.Room;
import com.veylon.world.urban.BuildingPlan.Variant;
import com.veylon.world.urban.LootTables.Table;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Milestone 12: the commercial and civic catalogue of template revision five, as pure plans. */
class CivicCatalogueTest {
    private static final int CIVIC = UrbanRevision.BUILDING_TEMPLATE_CIVIC;
    private static final int[][] SIZES = {{12, 12}, {16, 12}, {16, 16}, {20, 12}, {20, 16}, {24, 20}, {28, 14}, {32, 32}};

    @Test
    void everyFamilyRotationVariantStoreyAndBasementHasConnectedRoomsReachableStockAndExits() {
        assertEquals(List.of(), BuildingPlanDiagnostics.familyMatrix(SIZES, new long[]{20260731L, -4242L}),
                "the diagnostic matrix names any failing template");
        Map<Family, EnumSet<Function>> functions = new EnumMap<>(Family.class);
        int checked = 0;
        for (Family family : CivicCatalogue.FAMILIES) {
            CivicTemplates.Envelope envelope = CivicTemplates.envelope(family);
            for (int[] size : SIZES) {
                for (ParcelPlan.Side side : ParcelPlan.Side.values()) {
                    ParcelPlan parcel = BuildingPlanDiagnostics.testParcel(size[0], size[1], side, 7, 8);
                    if (!envelope.fits(parcel)) { continue; }
                    for (Variant variant : Variant.values()) {
                        for (boolean minimal : new boolean[]{false, true}) {
                            int storeys = envelope.storeys(parcel);
                            BuildingPlan plan = BuildingTemplates.propose(parcel, CIVIC, family, variant, storeys,
                                    envelope.basements(), minimal);
                            AccessibilityValidator.Report report = AccessibilityValidator.inspect(plan);
                            assertTrue(report.ok(), BuildingPlanner.identity(plan) + " " + report.summary());
                            assertTrue(report.cellsExamined() <= PlanVolume.MAX_CELLS);
                            assertTrue(report.transitions() <= AccessibilityValidator.MAX_TRANSITIONS);
                            assertEquals(plan, BuildingTemplates.propose(parcel, CIVIC, family, variant, storeys,
                                    envelope.basements(), minimal), "proposals are pure");
                            assertEquals(CIVIC, plan.templateRevision());
                            assertEquals(storeys + (envelope.basements() ? 1 : 0), plan.stairs().size(),
                                    "every storey and the roof are joined by the shared switchback");
                            if (!minimal) {
                                for (var floor : plan.floors()) {
                                    for (Room room : floor.rooms()) {
                                        functions.computeIfAbsent(family, f -> EnumSet.noneOf(Function.class))
                                                .addAll(room.functions());
                                    }
                                }
                            }
                            assertTrue(plan.fixtures().stream().anyMatch(f -> f.kind() == FixtureKind.CONTAINER),
                                    "even the minimal fallback has searchable contents");
                            checked++;
                        }
                    }
                }
            }
        }
        assertTrue(functions.get(Family.GROCERY).containsAll(EnumSet.of(Function.SHOP_FLOOR, Function.STOCKROOM)));
        assertTrue(functions.get(Family.CLINIC).containsAll(EnumSet.of(Function.CLINIC, Function.PHARMACY,
                Function.BATHROOM)), "a clinic has treatment rooms and dispensary storage");
        assertTrue(functions.get(Family.SCHOOL).containsAll(EnumSet.of(Function.CLASSROOM, Function.KITCHEN)));
        assertTrue(functions.get(Family.OFFICE).containsAll(EnumSet.of(Function.OFFICE, Function.UTILITY)),
                "offices have work and utility rooms");
        assertTrue(functions.get(Family.SECURITY).containsAll(EnumSet.of(Function.SECURITY, Function.OFFICE)));
        assertTrue(functions.get(Family.WAREHOUSE).containsAll(EnumSet.of(Function.WAREHOUSE, Function.WORKSHOP)),
                "a warehouse has rack halls and a repair workshop");
        System.out.println("civic-family-matrix plans=" + checked);
    }

    @Test
    void securedStockIsReachedOnlyByForcingItsOwnLock() {
        ParcelPlan parcel = BuildingPlanDiagnostics.testParcel(20, 16, ParcelPlan.Side.WEST, -4242, 8);
        for (Family family : List.of(Family.CLINIC, Family.SECURITY)) {
            BuildingPlan plan = BuildingTemplates.propose(parcel, CIVIC, family, Variant.INTACT, 2, false, false);
            Room secured = securedRoom(plan);
            Fixture door = plan.fixtures().stream().filter(f -> f.id() == UrbanIds.fixture(secured.id(), 0))
                    .findFirst().orElseThrow();
            assertTrue(door.locked());
            assertEquals(family == Family.SECURITY ? BlockType.METAL_DOOR : BlockType.WOOD_DOOR, door.block(),
                    "an armoury needs a pry tool; a dispensary door can be forced by hand");
            assertTrue(plan.fixtures().stream().noneMatch(f -> UrbanIds.parent(f.id()) == secured.id()
                    && f.kind() == FixtureKind.WINDOW), "the secured room's window is barred");
            assertTrue(plan.fixtures().stream().filter(f -> UrbanIds.parent(f.id()) == secured.id()
                    && f.kind() == FixtureKind.CONTAINER).count() >= 2);
            assertTrue(AccessibilityValidator.inspect(plan).ok());

            // Remove one bar: the stock is reachable without the lock, which defeats its cost.
            Vec3i bar = cell(plan, BlockType.CAGE_BARS);
            BuildingPlan bypass = plan.withVoxels(plan.voxels().edit().set(bar.x(), bar.y(), bar.z(), BlockType.AIR)
                    .set(bar.x(), bar.y() + 1, bar.z(), BlockType.AIR)
                    .set(bar.x(), bar.y() - 1, bar.z(), BlockType.AIR).build());
            assertTrue(AccessibilityValidator.inspect(bypass).messages().stream()
                    .anyMatch(m -> m.startsWith("SECURED: room is reachable without forcing")), family + " bypass");

            // Block the corridor side of the lock: forcing it is no longer an ordinary choice.
            Vec3i p = door.position(), t = door.target();
            Vec3i outside = new Vec3i(2 * p.x() - t.x(), t.y(), 2 * p.z() - t.z());
            BuildingPlan walled = plan.withVoxels(plan.voxels().edit().set(outside.x(), outside.y(), outside.z(),
                    BlockType.CONCRETE).set(outside.x(), outside.y() + 1, outside.z(), BlockType.CONCRETE).build());
            assertTrue(AccessibilityValidator.inspect(walled).messages().stream()
                    .anyMatch(m -> m.startsWith("SECURED: forcible door")), family + " forced entry");

            // The same lock around an ordinary storeroom is a sealed room, not an access cost.
            List<BuildingPlan.Floor> relabelled = new ArrayList<>();
            for (BuildingPlan.Floor floor : plan.floors()) {
                List<Room> rooms = new ArrayList<>();
                for (Room room : floor.rooms()) {
                    rooms.add(room.id() == secured.id()
                            ? new Room(room.id(), List.of(Function.STORAGE), room.interior(), room.anchor()) : room);
                }
                relabelled.add(new BuildingPlan.Floor(floor.id(), floor.index(), floor.slabY(), rooms, floor.landing()));
            }
            BuildingPlan storeroom = new BuildingPlan(plan.id(), plan.templateRevision(), plan.template(),
                    plan.family(), plan.variant(), plan.parcel(), plan.storeys(), plan.basement(), relabelled,
                    plan.fixtures(), plan.approaches(), plan.stairs(), plan.voxels());
            assertTrue(AccessibilityValidator.inspect(storeroom).messages().stream()
                    .anyMatch(m -> m.startsWith("ROOM: unreachable")), "only secured stock may rely on forcing");

            BuildingPlan looted = BuildingTemplates.propose(parcel, CIVIC, family, Variant.PICKED_CLEAN, 2, false, false);
            Room open = securedRoom(looted);
            assertTrue(looted.fixtures().stream().noneMatch(f -> UrbanIds.parent(f.id()) == open.id()
                    && f.kind() == FixtureKind.DOOR), "looters already forced the picked-clean door");
            assertEquals(BlockType.BROKEN_DOOR, looted.voxels().getBlock(p.x(), p.y(), p.z()));
            assertTrue(AccessibilityValidator.inspect(looted).ok());
        }
    }

    @Test
    void everyParcelOfTheFrozenSeedRegionMatrixGetsAValidatedFamilyAndWalkUpsStayTemplateFour() {
        Map<Family, Integer> counts = new EnumMap<>(Family.class);
        int checked = 0, repaired = 0;
        for (long seed : BuildingPlannerTest.SEEDS) {
            for (int[] r : new int[][]{{0, 0}, {-1, -1}, {1, -1}, {-1, 1}, {2, 3}, {-3, -2}, {4096, -4096}, {-4097, 4095}}) {
                DistrictPlan district = CityPlanner.planStarter(seed, r[0], r[1]);
                Map<Family, Integer> region = new EnumMap<>(Family.class);
                for (ParcelPlan parcel : district.parcels()) {
                    BuildingPlanner.Result result = BuildingPlanner.plan(seed, CIVIC, parcel, district);
                    BuildingPlan plan = result.plan();
                    assertTrue(result.attempts() <= BuildingPlanner.MAX_ATTEMPTS);
                    AccessibilityValidator.Report report = AccessibilityValidator.inspect(plan, district);
                    assertTrue(report.ok(), () -> BuildingPlanner.identity(plan) + " " + report.summary());
                    if (plan.family().walkUp()) {
                        assertEquals(BuildingPlanDiagnostics.fingerprint(BuildingPlanner.plan(seed,
                                        UrbanRevision.BUILDING_TEMPLATE_STARTER, parcel).plan()),
                                BuildingPlanDiagnostics.fingerprint(plan), "walk-ups keep template four");
                    } else {
                        assertFalse(UrbanStarterSite.isNeighbour(parcel), "the slice superblock is unchanged");
                        assertNotNull(CivicCatalogue.signText(seed, plan));
                    }
                    region.merge(plan.family(), 1, Integer::sum);
                    counts.merge(plan.family(), 1, Integer::sum);
                    repaired += result.attempts() > 1 ? 1 : 0;
                    checked++;
                }
                assertEquals(1, region.getOrDefault(Family.SCHOOL, 0), "one school per region");
                assertEquals(1, region.getOrDefault(Family.SECURITY, 0), "one security post per region");
                assertEquals(1, region.getOrDefault(Family.WAREHOUSE, 0), "the service parcel is a warehouse");
            }
        }
        for (Family family : CivicCatalogue.FAMILIES) {
            assertTrue(counts.getOrDefault(family, 0) > 0, family + " appears in the frozen matrix");
        }
        System.out.println("civic-city-matrix plans=" + checked + " repaired=" + repaired + " families=" + counts);
    }

    @Test
    void roomPurposeDecidesStockAndSpecialistSuppliesCostAccessOrTheft() {
        Map<Table, Set<Family>> openSources = new EnumMap<>(Table.class);
        for (long seed : new long[]{20260731L, -4242L, 7L}) {
            for (Family family : CivicCatalogue.FAMILIES) {
                for (Variant variant : Variant.values()) {
                    BuildingPlan example = BuildingPlanDiagnostics.example(seed, family);
                    BuildingPlan plan = BuildingTemplates.propose(example.parcel(), CIVIC, family, variant,
                            example.storeys(), example.basement(), false);
                    for (Fixture f : plan.fixtures()) {
                        if (f.kind() != FixtureKind.CONTAINER) { continue; }
                        ContainerUse use = BuildingFurnishing.containerUse(plan, f);
                        Table table = LootTables.table(plan, f, UrbanDistrict.RESIDENTIAL);
                        Room room = roomOf(plan, f);
                        boolean secured = CivicTemplates.primary(room.functions()).secured();
                        if (table == Table.SECURED) {
                            assertTrue(secured || plan.survivorOwned(f), "ammunition needs a forced lock or theft "
                                    + BuildingPlanner.identity(plan));
                        }
                        if (table == Table.CLINIC) {
                            assertEquals(ContainerUse.MEDICINE_CABINET, use, "dispensary medicine is behind its lock");
                        }
                        if (!secured && !plan.survivorOwned(f)) {
                            openSources.computeIfAbsent(table, t -> EnumSet.noneOf(Family.class)).add(family);
                        }
                        switch (use) {
                            case MEDICINE_CABINET -> assertTrue(room.functions().contains(Function.PHARMACY));
                            case ARMORY_LOCKER -> assertEquals(Table.SECURED, table);
                            case WAREHOUSE_RACK -> assertEquals(Table.WAREHOUSE, table);
                            case TOOL_CHEST -> assertEquals(Table.UTILITY, table, "workshops stock repair supplies");
                            case SHOP_SHELF -> assertEquals(Table.GROCER, table);
                            default -> { }
                        }
                        if (family == Family.GROCERY && use == ContainerUse.STOCKROOM_CRATE && !plan.survivorOwned(f)) {
                            assertEquals(Table.PANTRY, table, "a grocer's stockroom holds food");
                        }
                    }
                }
            }
        }
        // Necessities keep alternatives across specialist destinations; specialist stock does not.
        assertTrue(union(openSources, Table.KITCHEN, Table.FRIDGE, Table.PANTRY, Table.GROCER).size() >= 3, "food");
        assertTrue(union(openSources, Table.BATHROOM).size() >= 3, "treatment");
        assertTrue(union(openSources, Table.UTILITY, Table.WAREHOUSE).size() >= 3, "pry tools and light");
        assertFalse(openSources.containsKey(Table.SECURED));
        assertFalse(openSources.containsKey(Table.CLINIC));
        assertNull(LootTables.roll(7, 3, UrbanIds.fixture(UrbanIds.room(UrbanIds.floor(
                        UrbanIds.building(UrbanIds.parcel(UrbanIds.district(0, 0), 3), 0), 0), 4), 2),
                Table.CLINIC, UrbanDistrict.RESIDENTIAL, Variant.PICKED_CLEAN), "picked-clean stock stays empty");
    }

    @Test
    void partialOccupationHoldsTheStreetFloorAndLeavesUpperStoreysAbandoned() {
        ParcelPlan parcel = BuildingPlanDiagnostics.testParcel(24, 20, ParcelPlan.Side.SOUTH, 987654321L, 8);
        BuildingPlan office = BuildingTemplates.propose(parcel, CIVIC, Family.OFFICE, Variant.OCCUPIED, 4, true, false);
        assertTrue(AccessibilityValidator.inspect(office).ok());
        boolean ground = false, upper = false;
        for (Fixture f : office.fixtures()) {
            if (f.kind() != FixtureKind.CONTAINER) { continue; }
            int floor = UrbanIds.floorIndex(f.id());
            assertEquals(floor <= 0, office.survivorOwned(f), "survivors hold the street floor and basement");
            ground |= floor == 0;
            upper |= floor > 0;
            if (floor > 0 && BuildingFurnishing.containerUse(office, f) == ContainerUse.UTILITY_LOCKER) {
                assertEquals(Table.UTILITY, LootTables.table(office, f, UrbanDistrict.COMMERCIAL));
            }
        }
        assertTrue(ground && upper);
        List<Fixture> entrances = office.fixtures().stream().filter(f -> f.kind() == FixtureKind.DOOR
                && UrbanIds.roomIndex(f.id()) == 0 && UrbanIds.slotIndex(f.id()) >= 8).toList();
        assertTrue(entrances.stream().filter(f -> UrbanIds.slotIndex(f.id()) % 2 == 0).allMatch(Fixture::locked),
                "the holders lock the street door");
        assertTrue(entrances.stream().filter(f -> UrbanIds.slotIndex(f.id()) % 2 == 1).noneMatch(Fixture::locked),
                "and keep the rear route open");
        assertTrue(office.fixtures().stream().anyMatch(f -> f.block() == BlockType.BEDROLL
                && UrbanIds.floorIndex(f.id()) == 0), "bedrolls show who lives there");
        BuildingPlan walkUp = BuildingTemplates.propose(BuildingPlannerTest.parcel(16, 20, ParcelPlan.Side.NORTH, 14, 14),
                1, Family.APARTMENT, Variant.OCCUPIED, 3, true, false);
        assertTrue(walkUp.fixtures().stream().filter(f -> f.kind() == FixtureKind.CONTAINER)
                .allMatch(walkUp::survivorOwned), "occupied walk-ups stay owned throughout");
    }

    @Test
    void revisionsKeepTheirOwnFamiliesAndFailuresNameTheirTemplate() {
        ParcelPlan parcel = BuildingPlanDiagnostics.testParcel(20, 16, ParcelPlan.Side.EAST, 0, 8);
        for (int revision = 1; revision <= 4; revision++) {
            int r = revision;
            assertThrows(IllegalArgumentException.class,
                    () -> BuildingTemplates.propose(parcel, r, Family.CLINIC, Variant.INTACT, 1, false, false));
        }
        assertThrows(IllegalArgumentException.class,
                () -> BuildingTemplates.propose(parcel, CIVIC, Family.APARTMENT, Variant.INTACT, 2, false, false));
        assertThrows(IllegalArgumentException.class, () -> BuildingPlanner.plan(0, CIVIC, parcel));
        assertThrows(IllegalArgumentException.class, () -> BuildingTemplates.propose(parcel, CIVIC, Family.OFFICE,
                Variant.INTACT, 2, false, false), "offices start at three storeys");

        BuildingPlan clinic = BuildingTemplates.propose(parcel, CIVIC, Family.CLINIC, Variant.DAMAGED, 2, false, false);
        PlanVolume.Builder sealed = clinic.voxels().edit();
        for (Fixture f : clinic.fixtures()) {
            if (f.kind() == FixtureKind.DOOR && UrbanIds.slotIndex(f.id()) >= 8) {
                for (int h = 0; h < f.height(); h++) {
                    sealed.set(f.position().x(), f.position().y() + h, f.position().z(), BlockType.BRICK);
                }
            }
        }
        BuildingPlanner.Result fixed = BuildingPlanner.repair(clinic.withVoxels(sealed.build()));
        assertEquals(2, fixed.attempts());
        assertEquals(Variant.INTACT, fixed.plan().variant());
        String failure = fixed.repairs().getFirst();
        assertTrue(failure.contains("template=clinic") && failure.contains("family=CLINIC")
                && failure.contains("variant=DAMAGED") && failure.contains("parcel=" + UrbanIds.format(parcel.id()))
                && failure.contains("facing=EAST"), failure);
        String report = BuildingPlanDiagnostics.catalogue(20260731L, 0, 0);
        assertTrue(report.contains("failed=0") && report.contains("SCHOOL/school=1"), report);
    }

    /** First building of each family near the origin, recorded 2026-10-14 when revision five was introduced. */
    @Test
    void templateFiveExamplesMatchTheirRecordedFingerprints() {
        long[][] pins = {
                {0x64c28f93045ef3e3L, 0x70586f74b59259a8L, 0x2ebcaeb36c72614fL,
                        0xcd1135d5b8d3ad9eL, 0x0eff6acfda0bc3d7L, 0xc1d22d2d605d6b68L},
                {0xdf5b0c50c902c0edL, 0x98d3d4af8f0b9696L, 0x23a3b316eb41f678L,
                        0x36a3a447c59b1635L, 0xfaec2016c31fd368L, 0x4338743cea542ad5L},
                {0xe50e93687b0e4496L, 0x4bde92a85b327dc1L, 0x40b7fb03c28999b5L,
                        0xcbfe0ba9a6149202L, 0xcccaa3216e794e0fL, 0x19f3885c859ee1c0L}};
        long[] seeds = {20260731L, -4242L, 7L};
        List<String> moved = new ArrayList<>();
        for (int s = 0; s < seeds.length; s++) {
            for (int f = 0; f < CivicCatalogue.FAMILIES.size(); f++) {
                long actual = BuildingPlanDiagnostics.fingerprint(BuildingPlanDiagnostics.example(seeds[s],
                        CivicCatalogue.FAMILIES.get(f)));
                if (actual != pins[s][f]) {
                    moved.add(String.format("seed %d %s: 0x%016xL", seeds[s], CivicCatalogue.FAMILIES.get(f), actual));
                }
            }
        }
        assertTrue(moved.isEmpty(), () -> "template five is a saved-world contract; add a revision instead:\n  "
                + String.join("\n  ", moved));
    }

    private static Room securedRoom(BuildingPlan plan) {
        return plan.floors().stream().filter(f -> f.index() == 0).flatMap(f -> f.rooms().stream())
                .filter(r -> CivicTemplates.primary(r.functions()).secured()).findFirst().orElseThrow();
    }

    private static Room roomOf(BuildingPlan plan, Fixture fixture) {
        long id = UrbanIds.parent(fixture.id());
        return plan.floors().stream().flatMap(f -> f.rooms().stream()).filter(r -> r.id() == id).findFirst().orElseThrow();
    }

    private static Vec3i cell(BuildingPlan plan, BlockType block) {
        PlanVolume v = plan.voxels();
        for (int y = v.minY; y <= v.maxY; y++) {
            for (int z = v.z0; z < v.z0 + v.depth; z++) {
                for (int x = v.x0; x < v.x0 + v.width; x++) {
                    if (v.getBlock(x, y, z) == block) { return new Vec3i(x, y, z); }
                }
            }
        }
        throw new AssertionError("no " + block);
    }

    private static Set<Family> union(Map<Table, Set<Family>> sources, Table... tables) {
        Set<Family> all = EnumSet.noneOf(Family.class);
        for (Table t : tables) { all.addAll(sources.getOrDefault(t, Set.of())); }
        return all;
    }
}
