package com.veylon;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.CityPlanner;
import com.veylon.world.urban.DistrictMap;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.RouteCatalogue;
import com.veylon.world.urban.TransitNetwork;
import com.veylon.world.urban.TransitStation;
import com.veylon.world.urban.UrbanIds;

/**
 * The generated routes the milestone-14 tour walks, chosen from pure plans of a generator-13
 * world (no block is changed): a service passage between two unheld buildings of the spawn
 * region, its hidden cellar, a roof connector and a fire escape there, the spawn station, and a
 * flooded tunnel in the nearest flooded district. Each leg starts and ends on a cell a body can
 * stand on, so the tour's walks need no staging inside a route.
 */
record UrbanUndergroundTourPlan(Passage passage, Cellar cellar, Roof roof, Escape escape, Station station,
                                Vec3i flood) {

    /** Street, the west basement's service door, the corridor and the east building's street. */
    record Passage(Vec3i westStreet, Vec3i westDoor, Vec3i serviceDoor, Vec3i corridor, Vec3i eastStreet) { }
    /** A cellar building's entrance, the hall in front of its loose panel, the panel and the room. */
    record Cellar(Vec3i street, Vec3i hall, Vec3i panel, Vec3i inside, long room) { }
    /** One building's entrance, its roof, and the connected roof beyond the opening or bridge. */
    record Roof(Vec3i street, Vec3i roof, Vec3i opening, Vec3i otherRoof, boolean bridge) { }
    /**
     * A fire escape: the ladder's foot, the parapet gap at its head, a roof cell inward of it,
     * and the street the building's stairs lead back down to (a ladder is only climbed up).
     */
    record Escape(Vec3i foot, Vec3i gap, Vec3i roof, Vec3i street) { }
    /**
     * The spawn station: its street approach, platform, service door and room, the tunnel beyond, a
     * track cell beside it to light, and a point further down the tunnel to look along.
     */
    record Station(Vec3i street, Vec3i platform, Vec3i door, Vec3i room, Vec3i tunnel, Vec3i trackFloor,
                   Vec3i along, long id) { }

    static UrbanUndergroundTourPlan of(World world) {
        if (world.urban == null || !world.urban.hasUnderground()) {
            throw new IllegalStateException("urban_underground needs generator 13 (VEYLON_GENERATOR=urban-dev)");
        }
        RouteCatalogue routes = world.urban.routes(0, 0);
        return new UrbanUndergroundTourPlan(passage(world, routes), cellar(world, routes), roof(world, routes),
                escape(world, routes), station(world), flood(world));
    }

    private static BuildingPlan at(World world, Vec3i cell) { return world.urban.buildingAt(cell.x(), cell.z()); }

    private static boolean unheld(BuildingPlan plan) { return plan.variant() != BuildingPlan.Variant.OCCUPIED; }

    private static Passage passage(World world, RouteCatalogue routes) {
        for (RouteCatalogue.Corridor c : routes.corridors()) {
            BuildingPlan west = at(world, c.west().first()), east = at(world, c.east().first());
            if (unheld(west) && unheld(east)) {
                return new Passage(west.approaches().getFirst(), c.west().firstInside(), c.west().first(),
                        new Vec3i(c.x0() + 1, RouteCatalogue.SERVICE_Y, c.z0()), east.approaches().getFirst());
            }
        }
        throw new IllegalStateException("no unheld service passage in the spawn region");
    }

    private static Cellar cellar(World world, RouteCatalogue routes) {
        ParcelPlan parcel = world.urban.regionPlan(0, 0).parcel(routes.hiddenCellar());
        BuildingPlan plan = world.urban.buildingAt(parcel.x0(), parcel.z0());
        for (BuildingPlan.Fixture f : plan.fixtures()) {
            if (f.block() != BlockType.HIDDEN_PANEL) { continue; }
            Vec3i p = f.position(), t = f.target();
            return new Cellar(plan.approaches().getFirst(), new Vec3i(2 * p.x() - t.x(), p.y(), 2 * p.z() - t.z()),
                    p, t, UrbanIds.parent(f.id()));
        }
        throw new IllegalStateException("the spawn region's cellar has no panel");
    }

    private static Roof roof(World world, RouteCatalogue routes) {
        Roof found = null;
        for (RouteCatalogue.Connector c : routes.connectors()) {
            BuildingPlan a = at(world, c.a().first()), b = at(world, c.b().first());
            if (!unheld(a) || !unheld(b)) { continue; }
            Roof roof = new Roof(a.approaches().getFirst(), roofCell(a), c.a().first(), roofCell(b), c.bridge());
            if (found == null || c.bridge() && !found.bridge()) { found = roof; }
        }
        if (found == null) { throw new IllegalStateException("no unheld roof connector in the spawn region"); }
        return found;
    }

    /** The roof's own reservation cell, out in the open beyond the stair bulkhead. */
    static Vec3i roofCell(BuildingPlan plan) {
        for (BuildingPlan.Fixture f : plan.fixtures()) {
            if (f.kind() == BuildingPlan.FixtureKind.QUEST && UrbanIds.floorIndex(f.id()) == plan.storeys()) {
                return f.position();
            }
        }
        throw new IllegalStateException("roof without a reservation");
    }

    private static Escape escape(World world, RouteCatalogue routes) {
        for (RouteCatalogue.FireEscape f : routes.fireEscapes()) {
            BuildingPlan building = at(world, f.gap());
            if (!unheld(building)) { continue; }
            Vec3i top = f.ladderTop(), gap = f.gap();
            int dx = gap.x() - top.x(), dz = gap.z() - top.z();
            return new Escape(new Vec3i(top.x(), 41, top.z()), gap, new Vec3i(gap.x() + dx, gap.y(), gap.z() + dz),
                    building.approaches().getFirst());
        }
        throw new IllegalStateException("no fire escape on an unheld building in the spawn region");
    }

    private static Station station(World world) {
        TransitStation st = world.urban.segment(true, 0, 0).station();
        BuildingPlan plan = st.plan();
        BuildingPlan.Fixture door = plan.fixtures().stream()
                .filter(f -> f.kind() == BuildingPlan.FixtureKind.DOOR).findFirst().orElseThrow();
        TransitNetwork.Frame f = st.frame();
        Vec3i tunnel = new Vec3i(f.x(1, st.low() - 4), TransitNetwork.FEET_Y, f.z(1, st.low() - 4));
        // Mounds keep four cells clear of a station, so these track cells are bare.
        Vec3i trackFloor = new Vec3i(f.x(0, st.low() - 4), TransitNetwork.SLAB_Y, f.z(0, st.low() - 4));
        Vec3i along = new Vec3i(f.x(1, st.low() - 14), TransitNetwork.FEET_Y, f.z(1, st.low() - 14));
        return new Station(plan.approaches().getFirst(), plan.floors().getFirst().landing(), door.position(),
                door.target(), tunnel, trackFloor, along, st.id());
    }

    /** A track cell of the nearest flooded segment, standing water underfoot. */
    private static Vec3i flood(World world) {
        for (int r = 1; r <= DistrictMap.ALL_DISTRICTS_RING + 1; r++) {
            for (int line = -2 * r; line <= 2 * r; line += 2) {
                for (int index = -r; index <= r; index++) {
                    for (boolean ns : new boolean[]{true, false}) {
                        int[] p = TransitNetwork.region(ns, line, index, true);
                        if (!CityPlanner.isPlannable(p[0], p[1])) { continue; }
                        TransitNetwork.Segment s = world.urban.segment(ns, line, index);
                        if (!s.present() || !s.flooded()) { continue; }
                        TransitStation st = s.station();
                        TransitNetwork.Frame f = new TransitNetwork.Frame(ns, line, index, !st.frame().positive());
                        int a = st.low() - 3;
                        return new Vec3i(f.x(1, a), TransitNetwork.FEET_Y, f.z(1, a));
                    }
                }
            }
        }
        throw new IllegalStateException("no flooded tunnel within " + (DistrictMap.ALL_DISTRICTS_RING + 1) + " regions");
    }
}
