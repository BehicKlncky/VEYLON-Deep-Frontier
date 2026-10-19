package com.veylon.world.urban;

import com.veylon.world.urban.BuildingPlan.Family;
import com.veylon.world.urban.BuildingPlan.Variant;
import com.veylon.world.urban.LootTables.Table;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Building template revision 6 (milestone 13): the district catalogue chooses proven
 * template-four and template-five plans per district, and every district keeps its basics.
 */
class DistrictCatalogueTest {
    private static final int DISTRICTS = UrbanRevision.BUILDING_TEMPLATE_DISTRICTS;
    private static final long[] SEEDS = {20_260_731L, -4_242L, 7L};

    /** Necessities and the unsecured tables that supply them (DESIGN §8, S3). */
    private static final Map<String, Set<Table>> NECESSITIES = Map.of(
            "food", EnumSet.of(Table.KITCHEN, Table.FRIDGE, Table.PANTRY, Table.GROCER),
            "water", EnumSet.of(Table.KITCHEN, Table.FRIDGE, Table.PANTRY, Table.GROCER, Table.SHOP),
            "treatment", EnumSet.of(Table.BATHROOM, Table.CLINIC, Table.DESK, Table.GROCER, Table.SHOP),
            "light", EnumSet.of(Table.PANTRY, Table.DESK, Table.UTILITY, Table.WAREHOUSE, Table.SHOP),
            "pry tool", EnumSet.of(Table.UTILITY, Table.WAREHOUSE));

    @Test
    void everyDistrictPlansValidBuildingsOfItsOwnMixAndKeepsItsBasics() {
        for (long seed : SEEDS) {
            for (UrbanDistrict district : UrbanDistrict.values()) {
                DistrictPlan plan = region(seed, district);
                Map<Family, Integer> families = new EnumMap<>(Family.class);
                Map<Variant, Integer> variants = new EnumMap<>(Variant.class);
                Map<Table, Integer> open = new EnumMap<>(Table.class);
                for (ParcelPlan parcel : plan.parcels()) {
                    BuildingPlanner.Result result = BuildingPlanner.plan(seed, DISTRICTS, parcel, plan);
                    BuildingPlan building = result.plan();
                    AccessibilityValidator.Report report = AccessibilityValidator.inspect(building, plan);
                    assertTrue(report.ok(), () -> BuildingPlanner.identity(building) + " " + report.summary());
                    assertEquals(DistrictCatalogue.family(seed, plan, parcel), building.family());
                    families.merge(building.family(), 1, Integer::sum);
                    variants.merge(building.variant(), 1, Integer::sum);
                    if (building.family().walkUp() && DistrictCatalogue.walkUpVariant(seed, plan, parcel) == null) {
                        assertEquals(BuildingPlanDiagnostics.fingerprint(
                                        BuildingPlanner.plan(seed, UrbanRevision.BUILDING_TEMPLATE_STARTER, parcel).plan()),
                                BuildingPlanDiagnostics.fingerprint(building), "a familiar walk-up keeps template four");
                    }
                    if (building.variant() == Variant.PICKED_CLEAN) {
                        continue;
                    }
                    for (BuildingPlan.Fixture f : building.fixtures()) {
                        if (f.kind() == BuildingPlan.FixtureKind.CONTAINER && !building.survivorOwned(f)
                                && !securedRoom(building, f)) {
                            open.merge(LootTables.table(building, f, plan.district), 1, Integer::sum);
                        }
                    }
                }
                String where = seed + " " + district + " " + families + " " + variants;
                switch (district) {
                    case RESIDENTIAL -> assertEquals(1, families.getOrDefault(Family.SCHOOL, 0), where);
                    case COMMERCIAL -> {
                        assertEquals(1, families.getOrDefault(Family.SECURITY, 0), "the downtown post " + where);
                        assertTrue(families.getOrDefault(Family.OFFICE, 0) >= 10, where);
                    }
                    case INDUSTRIAL -> assertTrue(families.getOrDefault(Family.WAREHOUSE, 0) * 2
                            > plan.parcels().size(), "depots dominate " + where);
                    case FLOODED -> assertTrue(variants.getOrDefault(Variant.PICKED_CLEAN, 0) * 6
                            < plan.parcels().size(), "the water kept looters out " + where);
                    case CIVIC -> {
                        for (Family f : EnumSet.of(Family.SCHOOL, Family.SECURITY, Family.CLINIC, Family.OFFICE)) {
                            assertTrue(families.getOrDefault(f, 0) >= 2, f + " " + where);
                        }
                    }
                    case QUARANTINE -> {
                        assertEquals(0, variants.getOrDefault(Variant.OCCUPIED, 0), "nobody holds a cordon " + where);
                        assertTrue(families.getOrDefault(Family.SECURITY, 0) >= 2
                                && families.getOrDefault(Family.CLINIC, 0) >= 1, where);
                    }
                }
                for (var need : NECESSITIES.entrySet()) {
                    long sources = need.getValue().stream().filter(t -> open.getOrDefault(t, 0) > 0).count();
                    assertTrue(sources >= 2, need.getKey() + " has " + sources + " unsecured source types in "
                            + where + " " + open);
                }
            }
        }
    }

    @Test
    void theRegionsAroundTheSpawnHoldEveryFamilyTheCatalogueWalkVisits() {
        for (long seed : CityPlannerTest.SEEDS) {
            Set<Family> nearby = EnumSet.noneOf(Family.class);
            for (int rz = -1; rz <= 1; rz++) {
                for (int rx = -1; rx <= 1; rx++) {
                    DistrictPlan plan = CityPlanner.planDistricts(seed, rx, rz);
                    for (ParcelPlan parcel : plan.parcels()) {
                        nearby.add(DistrictCatalogue.family(seed, plan, parcel));
                    }
                }
            }
            assertTrue(nearby.containsAll(CivicCatalogue.FAMILIES), seed + " " + nearby);
        }
    }

    @Test
    void theStarterSuperblockAndRefugeKeepTheirTemplateFourPlans() {
        for (long seed : CityPlannerTest.SEEDS) {
            DistrictPlan origin = CityPlanner.planDistricts(seed, 0, 0);
            for (ParcelPlan parcel : origin.parcels()) {
                if (!UrbanStarterSite.isNeighbour(parcel)) {
                    continue;
                }
                assertEquals(BuildingPlanDiagnostics.fingerprint(
                                BuildingPlanner.plan(seed, UrbanRevision.BUILDING_TEMPLATE_STARTER, parcel).plan()),
                        BuildingPlanDiagnostics.fingerprint(BuildingPlanner.plan(seed, DISTRICTS, parcel, origin).plan()));
            }
        }
    }

    @Test
    void theCatalogueIsPureAndNeedsItsDistrict() {
        DistrictPlan plan = region(SEEDS[0], UrbanDistrict.CIVIC);
        for (ParcelPlan parcel : plan.parcels()) {
            assertEquals(DistrictCatalogue.family(SEEDS[0], plan, parcel), DistrictCatalogue.family(SEEDS[0],
                    CityPlanner.planDistricts(SEEDS[0], plan.rx, plan.rz), parcel));
            assertEquals(DistrictCatalogue.variant(SEEDS[0], plan, parcel), DistrictCatalogue.variant(SEEDS[0], plan, parcel));
        }
        assertThrows(IllegalArgumentException.class,
                () -> BuildingPlanner.plan(SEEDS[0], DISTRICTS, plan.parcels().getFirst()));
        for (UrbanDistrict district : UrbanDistrict.values()) {
            int[] weights = DistrictCatalogue.weights(district);
            if (weights != null) {
                assertEquals(100, java.util.Arrays.stream(weights).sum(), district.id);
            }
        }
        String report = BuildingPlanDiagnostics.districts(SEEDS[0], plan.rx, plan.rz);
        assertTrue(report.contains("district=CIVIC") && report.contains("failed=0"), report);
    }

    /** The first non-origin region of a district within three of the spawn. */
    private static DistrictPlan region(long seed, UrbanDistrict district) {
        for (int rz = -3; rz <= 3; rz++) {
            for (int rx = -3; rx <= 3; rx++) {
                if ((rx != 0 || rz != 0) && DistrictMap.districtOf(seed, rx, rz) == district) {
                    return CityPlanner.planDistricts(seed, rx, rz);
                }
            }
        }
        throw new AssertionError("no " + district + " near the spawn of " + seed);
    }

    private static boolean securedRoom(BuildingPlan building, BuildingPlan.Fixture fixture) {
        long room = UrbanIds.parent(fixture.id());
        return building.floors().stream().flatMap(f -> f.rooms().stream())
                .anyMatch(r -> r.id() == room && r.functions().stream().anyMatch(BuildingPlan.Function::secured));
    }
}
