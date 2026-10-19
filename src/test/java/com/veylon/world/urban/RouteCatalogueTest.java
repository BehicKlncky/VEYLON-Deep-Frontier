package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.urban.BuildingPlan.FixtureKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Milestone 14: building template seven and the region routes it carries, as pure plans.
 * Every parcel of a region of each district validates; the starter superblock is exactly its
 * template-six plan; apartments always have a basement; service doors, cellars, guarded roofs,
 * bulkheads, fire escapes and roof connectors are declared by every building they touch.
 */
class RouteCatalogueTest {
    private static final long[] SEEDS = {20260731L, -4242L, 7L};

    /** One region of every district near the spawn, nearest first. */
    static Map<UrbanDistrict, DistrictPlan> regions(long seed) {
        Map<UrbanDistrict, DistrictPlan> found = new EnumMap<>(UrbanDistrict.class);
        for (int r = 0; r <= DistrictMap.ALL_DISTRICTS_RING && found.size() < 6; r++) {
            for (int rx = -r; rx <= r; rx++) {
                for (int rz = -r; rz <= r; rz++) {
                    UrbanDistrict d = DistrictMap.districtOf(seed, rx, rz);
                    if (!found.containsKey(d)) { found.put(d, CityPlanner.planDistricts(seed, rx, rz)); }
                }
            }
        }
        assertEquals(6, found.size());
        return found;
    }

    record Region(DistrictPlan district, RouteCatalogue routes, Map<Integer, BuildingPlan> plans) { }

    static Region plan(long seed, DistrictPlan district) {
        RouteCatalogue routes = RouteCatalogue.of(seed, district);
        Map<Integer, BuildingPlan> plans = new HashMap<>();
        for (ParcelPlan parcel : district.parcels()) {
            BuildingPlanner.Result result = BuildingPlanner.planRoutes(seed, parcel, district, routes);
            assertTrue(result.validation().ok(), () -> BuildingPlanner.identity(result.plan()) + " "
                    + result.validation().summary());
            plans.put(parcel.index(), result.plan());
        }
        return new Region(district, routes, plans);
    }

    @Test
    void everyParcelOfEveryDistrictValidatesAndTheStarterSuperblockIsUnchanged() {
        int buildings = 0;
        for (long seed : SEEDS) {
            for (DistrictPlan district : regions(seed).values()) {
                Region region = plan(seed, district);
                for (ParcelPlan parcel : district.parcels()) {
                    BuildingPlan plan = region.plans().get(parcel.index());
                    buildings++;
                    if (UrbanStarterSite.isNeighbour(parcel)) {
                        BuildingPlan six = BuildingPlanner.plan(seed, UrbanRevision.BUILDING_TEMPLATE_DISTRICTS, parcel, district).plan();
                        assertEquals(BuildingPlanDiagnostics.fingerprint(six), BuildingPlanDiagnostics.fingerprint(plan),
                                "starter superblock keeps its template-six plan");
                        assertEquals(six.voxels(), plan.voxels());
                    } else {
                        assertEquals(RouteTemplates.REVISION, plan.templateRevision());
                    }
                }
            }
        }
        System.out.println("route-catalogue buildings=" + buildings);
    }

    @Test
    void theCatalogueIsPureAndEveryApartmentOutsideTheStarterHasABasement() {
        for (long seed : SEEDS) {
            for (DistrictPlan district : regions(seed).values()) {
                RouteCatalogue a = RouteCatalogue.of(seed, district), b = RouteCatalogue.of(seed, district);
                assertEquals(a.corridors(), b.corridors());
                assertEquals(a.fireEscapes(), b.fireEscapes());
                assertEquals(a.connectors(), b.connectors());
                assertEquals(a.hiddenCellar(), b.hiddenCellar());
                int residential = 0, basements = 0;
                for (ParcelPlan parcel : district.parcels()) {
                    if (UrbanStarterSite.isNeighbour(parcel) || parcel.use() != ParcelPlan.Use.RESIDENTIAL) { continue; }
                    RouteCatalogue.Expect e = a.expected(parcel.index());
                    residential++;
                    if (e.basement()) { basements++; }
                    if (e.family() == BuildingPlan.Family.APARTMENT) { assertTrue(e.basement(), "an apartment has a basement"); }
                }
                assertTrue(residential == 0 || basements * 2 >= residential,
                        district.district + ": basements under " + basements + " of " + residential + " residential lots");
            }
        }
    }

    @Test
    void serviceDoorsArePairedAcrossTheirPassageAndLockedOnlyInHeldBuildings() {
        int corridors = 0;
        for (long seed : SEEDS) {
            for (DistrictPlan district : regions(seed).values()) {
                Region region = plan(seed, district);
                for (RouteCatalogue.Corridor c : region.routes().corridors()) {
                    corridors++;
                    assertEquals(CityPlanner.PASSAGE_WIDTH, c.x1() - c.x0() + 1, "under a three-wide passage");
                    for (int x = c.x0(); x <= c.x1(); x++) {
                        for (int z = c.z0(); z <= c.z1(); z++) {
                            assertNull(district.parcelAt(x, z), "a service passage cuts no foundation");
                            assertSame(UrbanSurface.PASSAGE, district.surfaceAt(x, z));
                        }
                    }
                    for (RouteCatalogue.ServiceDoor door : List.of(c.west(), c.east())) {
                        BuildingPlan plan = region.plans().get(door.parcel());
                        List<BuildingPlan.Fixture> leaves = new ArrayList<>();
                        for (BuildingPlan.Fixture f : plan.fixtures()) {
                            if (f.kind() == FixtureKind.DOOR && (f.position().equals(door.first()) || f.position().equals(door.second()))) {
                                leaves.add(f);
                            }
                        }
                        assertEquals(2, leaves.size(), "both leaves are declared");
                        for (BuildingPlan.Fixture f : leaves) {
                            assertSame(BlockType.METAL_DOOR, f.block());
                            assertEquals(-1, UrbanIds.floorIndex(f.id()), "service doors are in the first basement");
                            assertEquals(plan.variant() == BuildingPlan.Variant.OCCUPIED, f.locked());
                            assertTrue(Math.abs(f.position().x() - c.x0()) == 1 || Math.abs(f.position().x() - c.x1()) == 1,
                                    "a leaf opens onto the corridor's end");
                        }
                    }
                }
            }
        }
        assertTrue(corridors > 10, "service passages exist: " + corridors);
    }

    @Test
    void everyResidentialRegionHidesOneCellarThatOnlyItsPanelOpens() {
        for (long seed : SEEDS) {
            DistrictPlan district = regions(seed).get(UrbanDistrict.RESIDENTIAL);
            Region region = plan(seed, district);
            int index = region.routes().hiddenCellar();
            assertTrue(index >= 0, "seed " + seed + " has a hidden cellar reservation");
            BuildingPlan plan = region.plans().get(index);
            BuildingPlan.Room cellar = null;
            for (BuildingPlan.Floor floor : plan.floors()) {
                for (BuildingPlan.Room room : floor.rooms()) {
                    if (room.functions().contains(BuildingPlan.Function.HIDDEN_REFUGE)) { cellar = room; assertEquals(-1, floor.index()); }
                }
            }
            assertNotNull(cellar, "the reservation is a sealed basement room");
            BuildingPlan.Fixture panel = null;
            for (BuildingPlan.Fixture f : plan.fixtures()) {
                if (UrbanIds.parent(f.id()) == cellar.id() && f.kind() == FixtureKind.DOOR) {
                    assertSame(BlockType.HIDDEN_PANEL, f.block(), "the only door is the loose panel");
                    panel = f;
                }
            }
            assertNotNull(panel);
            Vec3i p = panel.position(), t = panel.target();
            Vec3i outside = new Vec3i(2 * p.x() - t.x(), p.y(), 2 * p.z() - t.z());
            assertSame(BlockType.DEBRIS, plan.voxels().getBlock(outside.x(), outside.y(), outside.z()),
                    "drag-marked debris lies in front of the panel");
            // Walling the panel up leaves the room with no way in at all: the panel is the access.
            PlanVolume.Builder walled = plan.voxels().edit();
            walled.set(p.x(), p.y(), p.z(), BlockType.PLASTER).set(p.x(), p.y() + 1, p.z(), BlockType.PLASTER);
            List<BuildingPlan.Fixture> without = new ArrayList<>(plan.fixtures());
            without.remove(panel);
            AccessibilityValidator.Report sealed = AccessibilityValidator.inspect(plan.withVoxels(walled.build()).withFixtures(without));
            assertFalse(sealed.ok());
            assertTrue(sealed.messages().stream().anyMatch(m -> m.startsWith("ROOM") && m.contains("unreachable")), sealed.summary());
        }
    }

    @Test
    void roofsAreGuardedAndEveryStairwellHasABulkheadWithADoor() {
        DistrictPlan district = regions(SEEDS[0]).get(UrbanDistrict.COMMERCIAL);
        Region region = plan(SEEDS[0], district);
        for (BuildingPlan plan : region.plans().values()) {
            if (plan.templateRevision() != RouteTemplates.REVISION) { continue; }
            int roof = plan.roofY();
            ParcelPlan p = plan.parcel();
            java.util.Set<Vec3i> openings = new java.util.HashSet<>();
            for (BuildingPlan.Fixture f : plan.fixtures()) {
                if (f.kind() == FixtureKind.ROUTE) { openings.add(new Vec3i(f.position().x(), 0, f.position().z())); }
            }
            boolean door = false;
            for (BuildingPlan.Fixture f : plan.fixtures()) {
                if (f.kind() == FixtureKind.DOOR && UrbanIds.floorIndex(f.id()) == plan.storeys()) {
                    door = true;
                    assertSame(BlockType.METAL_DOOR, f.block());
                    assertEquals(roof + 1, f.position().y());
                }
            }
            assertTrue(door, "a roof door in the bulkhead");
            for (int x = p.x0(); x <= p.x1(); x++) {
                for (int z = p.z0(); z <= p.z1(); z++) {
                    if (!p.onOutline(x, z)) { continue; }
                    BlockType course = plan.voxels().getBlock(x, roof + 2, z);
                    boolean opened = !plan.voxels().getBlock(x, roof + 1, z).solid;
                    assertTrue(course == BlockType.RAILING || course == BlockType.CONCRETE || opened,
                            "two-course parapet at " + x + "," + z + ": " + course);
                    if (opened) {
                        assertTrue(openings.contains(new Vec3i(x, 0, z)) || openings.contains(new Vec3i(x, 0, z - 1))
                                || openings.contains(new Vec3i(x - 1, 0, z)), "every gap is a declared route " + x + "," + z);
                    }
                }
            }
            for (BuildingPlan.Stair stair : plan.stairs()) {
                if (stair.fromFloor() != plan.storeys() - 1) { continue; }
                Vec3i top = stair.route().getLast();
                assertTrue(plan.voxels().getBlock(top.x(), roof + 4, top.z()).solid, "the bulkhead roofs the stair head");
            }
        }
    }

    @Test
    void connectorsAndFireEscapesAreDeclaredByEveryBuildingTheyTouch() {
        int escapes = 0, connectors = 0, bridges = 0;
        for (long seed : SEEDS) {
            for (DistrictPlan district : regions(seed).values()) {
                Region region = plan(seed, district);
                for (RouteCatalogue.Connector c : region.routes().connectors()) {
                    connectors++;
                    if (c.bridge()) { bridges++; }
                    for (RouteCatalogue.Opening o : List.of(c.a(), c.b())) {
                        assertTrue(declares(region.plans().get(o.parcel()), o.first()), "opening declared " + o);
                        assertFalse(region.plans().get(o.parcel()).voxels().getBlock(o.second().x(), o.second().y(), o.second().z()).solid);
                    }
                    assertEquals(region.plans().get(c.a().parcel()).roofY(), region.plans().get(c.b().parcel()).roofY(),
                            "connected roofs are level");
                }
                for (RouteCatalogue.FireEscape f : region.routes().fireEscapes()) {
                    escapes++;
                    BuildingPlan plan = region.plans().get(f.parcel());
                    assertTrue(declares(plan, f.gap()), "fire-escape gap declared");
                    for (int y = UrbanDatum.STREET_Y + 1; y <= plan.roofY(); y++) {
                        assertTrue(plan.voxels().getBlock(f.gap().x(), y, f.gap().z()).solid, "the ladder climbs a solid wall");
                    }
                    Vec3i out = f.ladderTop();
                    assertNull(district.parcelAt(out.x(), out.z()), "the ladder stands outside every parcel");
                    assertTrue(district.surfaceAt(out.x(), out.z()).publicWalkable);
                }
            }
        }
        System.out.println("routes escapes=" + escapes + " connectors=" + connectors + " bridges=" + bridges);
        assertTrue(escapes > 0 && connectors > 0 && bridges > 0);
    }

    private static boolean declares(BuildingPlan plan, Vec3i cell) {
        for (BuildingPlan.Fixture f : plan.fixtures()) {
            if (f.kind() == FixtureKind.ROUTE && f.position().equals(cell)) { return true; }
        }
        return false;
    }
}
