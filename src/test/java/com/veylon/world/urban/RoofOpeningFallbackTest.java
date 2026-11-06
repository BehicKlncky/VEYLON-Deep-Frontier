package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan.FixtureKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Package 25 (D-248): under building template seven (generators 13 to 20) a parapet opening between
 * two abutting roofs could stand where the roof's stair house was right inside it, a notch nobody on
 * that roof can reach. Every attempt of its building failed on it, so planning the building threw and
 * generating its chunks crashed the game. A last pass without such openings plans it; the roof next
 * door keeps its own opening, which looks onto a whole parapet. Nothing that planned before reaches
 * that pass, and the repaired buildings are pinned here from the day they first planned.
 */
class RoofOpeningFallbackTest {
    /** A parcel that could not be planned, the roof it abuts and its repaired plan's fingerprint. */
    private record Case(long seed, int rx, int rz, int parcel, int neighbour, long fingerprint) { }

    private static final List<Case> CASES = List.of(
            new Case(987_654_321L, -2, -3, 44, 42, 7_865_289_980_778_632_171L),
            new Case(-9_000_000_000_000_000_000L, -3, 0, 116, 114, 6_754_843_376_405_436_927L));

    private static final int[] ROUTE_GENERATORS = {World.GEN_URBAN_UNDERGROUND, World.GEN_URBAN_ECOLOGY,
            World.GEN_URBAN_SURVIVORS, World.GEN_URBAN_STRONGHOLDS, World.GEN_URBAN_REFUGES,
            World.GEN_URBAN_UTILITIES, World.GEN_URBAN_MISSIONS, World.GEN_URBAN_ECONOMY};

    @Test
    void theTwoParcelsThatCouldNotBePlannedPlanOnEveryRouteGeneratorAsTheyWereMeantToBe() {
        for (int generator : ROUTE_GENERATORS) {
            for (Case c : CASES) {
                World w = new World(c.seed(), generator);
                w.urban.resolveContentRevision(UrbanRevision.contentFor(generator));
                DistrictPlan district = w.urban.regionPlan(c.rx(), c.rz());
                RouteCatalogue routes = w.urban.routes(c.rx(), c.rz());
                ParcelPlan parcel = parcel(district, c.parcel());
                RouteCatalogue.Expect expect = routes.expected(c.parcel());
                RouteCatalogue.Opening notch = routes.features(c.parcel()).openings().getFirst();
                assertTrue(expect.facesBulkhead(notch.first()) && expect.facesBulkhead(notch.second()),
                        "the recorded opening faces the stair house");

                BuildingPlanner.Result result = BuildingPlanner.planRoutes(c.seed(), parcel, district, routes);
                assertTrue(result.attempts() > BuildingPlanner.MAX_ATTEMPTS
                        && result.attempts() <= 2 * BuildingPlanner.MAX_ATTEMPTS, "planned in the last pass");
                assertEquals(result.attempts() - 1, result.repairs().size(), "every failed attempt is named");
                BuildingPlan plan = w.urban.buildingAt(parcel.x0(), parcel.z0());
                assertEquals(BuildingPlanDiagnostics.fingerprint(result.plan()), BuildingPlanDiagnostics.fingerprint(plan));
                assertTrue(AccessibilityValidator.inspect(plan).ok(), AccessibilityValidator.inspect(plan).summary());
                assertEquals(UrbanRevision.BUILDING_TEMPLATE_ROUTES, plan.templateRevision());
                assertEquals(expect.family(), plan.family());
                assertEquals(expect.variant(), plan.variant(), "the building keeps its own state of ruin");
                assertEquals(expect.storeys(), plan.storeys());
                assertEquals(expect.basement(), plan.basement());
                assertFalse(declares(plan, notch.first()), "no route is declared at the notch");
                assertEquals(c.fingerprint(), BuildingPlanDiagnostics.fingerprint(plan),
                        "generator " + generator + " parcel " + UrbanIds.format(parcel.id()) + " is pinned");
            }
        }
    }

    @Test
    void theRoofNextDoorKeepsItsOpeningAndLooksOntoAWholeParapet() {
        for (Case c : CASES) {
            World w = new World(c.seed(), World.GEN_URBAN_ECONOMY);
            w.urban.resolveContentRevision(UrbanRevision.contentFor(w.generatorVersion));
            RouteCatalogue routes = w.urban.routes(c.rx(), c.rz());
            RouteCatalogue.Connector joint = routes.connectors().stream()
                    .filter(k -> k.b().parcel() == c.parcel() || k.a().parcel() == c.parcel()).findFirst().orElseThrow();
            assertFalse(joint.bridge(), "only openings between abutting roofs face a stair house");
            RouteCatalogue.Opening own = joint.a().parcel() == c.parcel() ? joint.a() : joint.b();
            RouteCatalogue.Opening theirs = own == joint.a() ? joint.b() : joint.a();
            assertEquals(c.neighbour(), theirs.parcel());

            BuildingPlan neighbour = w.urban.buildingAt(theirs.first().x(), theirs.first().z());
            assertEquals(c.neighbour(), neighbour.parcel().index());
            assertTrue(neighbour.fixtures().size() > 0 && declares(neighbour, theirs.first()),
                    "the roof next door still opens its parapet");
            for (Vec3i cell : List.of(own.first(), own.second(), theirs.first(), theirs.second())) {
                w.getOrCreateChunk(Math.floorDiv(cell.x(), 16), Math.floorDiv(cell.z(), 16));
            }
            for (Vec3i cell : List.of(theirs.first(), theirs.second())) {
                assertEquals(BlockType.AIR, w.getBlock(cell.x(), cell.y(), cell.z()), "their opening at " + cell);
                assertEquals(BlockType.AIR, w.getBlock(cell.x(), cell.y() + 1, cell.z()));
            }
            for (Vec3i cell : List.of(own.first(), own.second())) {
                assertTrue(w.getBlock(cell.x(), cell.y(), cell.z()).solid, "a whole parapet at " + cell);
                assertEquals(BlockType.RAILING, w.getBlock(cell.x(), cell.y() + 1, cell.z()), "and its rail");
            }
        }
    }

    /**
     * Route catalogues are cheap to build, so many regions are scanned for any opening that faces a stair
     * house: only the two recorded ones exist across ten seeds and 49 regions each, every one is planned,
     * and no bridge's opening ever qualifies.
     */
    @Test
    void acrossManyRegionsOnlyTheRecordedOpeningsFaceAStairHouseAndEachOneIsPlanned() {
        long[] seeds = {20_260_731L, -4_242L, 7L, 0L, 987_654_321L, -9_000_000_000_000_000_000L, 1L, 42L, 999L,
                -1_234_567L};
        List<String> found = new ArrayList<>();
        int openings = 0;
        for (long seed : seeds) {
            World w = new World(seed, World.GEN_URBAN_ECONOMY);
            w.urban.resolveContentRevision(UrbanRevision.contentFor(w.generatorVersion));
            for (int rz = -3; rz <= 3; rz++) {
                for (int rx = -3; rx <= 3; rx++) {
                    RouteCatalogue routes = w.urban.routes(rx, rz);
                    for (RouteCatalogue.Connector k : routes.connectors()) {
                        for (RouteCatalogue.Opening o : List.of(k.a(), k.b())) {
                            openings++;
                            RouteCatalogue.Expect e = routes.expected(o.parcel());
                            if (!e.facesBulkhead(o.first()) || !e.facesBulkhead(o.second())) { continue; }
                            assertFalse(k.bridge(), "a bridge's opening stands past the stair house");
                            found.add(seed + ":" + rx + "," + rz + ":" + o.parcel());
                            BuildingPlan plan = w.urban.buildingAt(e.parcel().x0(), e.parcel().z0());
                            assertTrue(AccessibilityValidator.inspect(plan).ok());
                            assertFalse(declares(plan, o.first()));
                        }
                    }
                }
            }
        }
        assertEquals(List.of("987654321:-2,-3:44", "-9000000000000000000:-3,0:116"), found,
                "the openings facing a stair house among " + openings);
    }

    private static ParcelPlan parcel(DistrictPlan district, int index) {
        return district.parcels().stream().filter(p -> p.index() == index).findFirst().orElseThrow();
    }

    private static boolean declares(BuildingPlan plan, Vec3i cell) {
        return plan.fixtures().stream().anyMatch(f -> f.kind() == FixtureKind.ROUTE && f.position().equals(cell));
    }
}
