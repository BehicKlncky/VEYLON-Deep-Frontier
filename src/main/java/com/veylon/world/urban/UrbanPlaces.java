package com.veylon.world.urban;

import com.veylon.world.BlockType;
import com.veylon.world.World;

/**
 * Which of a layout-ten city's routes a body stands in (milestone 14): a basement, a rooftop, a
 * hidden cellar, a service passage, a transit station or a tunnel. It reads loaded columns and
 * pure plans only, never creating a chunk, and answers {@link Place#NONE} in every earlier
 * world, so nothing changes there.
 *
 * <p>The HUD's location line names the place, and an observation of a station or a hidden cellar
 * is what records its discovery ({@link UrbanDiscovery}).
 */
public final class UrbanPlaces {
    /** A route place; plan metadata, never persisted. */
    public enum Place {
        NONE(""), BASEMENT("Basement"), ROOF("Rooftop"), HIDDEN_CELLAR("Hidden cellar"),
        SERVICE_PASSAGE("Service passage"), STATION("Transit station"), TUNNEL("Transit tunnel");

        public final String label;

        Place(String label) { this.label = label; }
    }

    /** A place and, for a station or hidden cellar, the site id its discovery records; else 0. */
    public record Observation(Place place, long site) {
        static final Observation NONE = new Observation(Place.NONE, UrbanIds.NONE);
    }

    private UrbanPlaces() { }

    /** The place holding a feet cell. */
    public static Observation at(World world, int x, int y, int z) {
        if (world.urban == null || !world.urban.hasUnderground() || !world.isColumnLoaded(x, z)) {
            return Observation.NONE;
        }
        UrbanIndoorQueries.Location location = UrbanIndoorQueries.location(world, x, y, z);
        if (location != null) {
            BuildingPlan building = location.building();
            if (building.family() == BuildingPlan.Family.TRANSIT) {
                return new Observation(Place.STATION, building.id());
            }
            if (location.room() != null && location.room().functions().contains(BuildingPlan.Function.HIDDEN_REFUGE)) {
                return new Observation(Place.HIDDEN_CELLAR, location.room().id());
            }
            if (location.floor().index() < 0) { return new Observation(Place.BASEMENT, UrbanIds.NONE); }
            if (location.floor().index() == building.storeys()) { return new Observation(Place.ROOF, UrbanIds.NONE); }
            return Observation.NONE;
        }
        int rx = StreetNetwork.regionOf(x), rz = StreetNetwork.regionOf(z);
        if (!CityPlanner.isPlannable(rx, rz)) { return Observation.NONE; }
        if (y >= TransitNetwork.FEET_Y && y < TransitNetwork.CEILING_Y) {
            for (boolean northSouth : new boolean[]{true, false}) {
                TransitNetwork.Band band = TransitNetwork.band(northSouth, x, z);
                if (band == null || band.m() >= TransitNetwork.TRACK) { continue; }
                TransitNetwork.Segment s = world.urban.segment(band);
                BlockType b = s.present() ? TransitNetwork.tunnelBlock(s, band.m(), band.a(), y) : null;
                if (b != null && !b.solid) { return new Observation(Place.TUNNEL, UrbanIds.NONE); }
            }
        }
        if (y >= RouteCatalogue.SERVICE_Y && y < UrbanDatum.STREET_Y) {
            for (RouteCatalogue.Corridor c : world.urban.routes(rx, rz).corridors()) {
                if (x >= c.x0() && x <= c.x1() && z >= c.z0() && z <= c.z1()) {
                    return new Observation(Place.SERVICE_PASSAGE, UrbanIds.NONE);
                }
            }
        }
        return Observation.NONE;
    }

    /**
     * The hidden cellar a loose panel opens onto, or {@link UrbanIds#NONE}: the panel must be the
     * planned door of a building's {@link BuildingPlan.Function#HIDDEN_REFUGE} room.
     */
    public static long cellarBehind(World world, int x, int y, int z) {
        if (world.urban == null || !world.urban.hasUnderground()) { return UrbanIds.NONE; }
        BuildingPlan building = world.urban.buildingAt(x, z);
        if (building == null) { return UrbanIds.NONE; }
        for (BuildingPlan.Fixture f : building.fixtures()) {
            if (f.block() != BlockType.HIDDEN_PANEL || f.position().x() != x || f.position().z() != z
                    || y < f.position().y() || y >= f.position().y() + f.height()) { continue; }
            long room = UrbanIds.parent(f.id());
            for (BuildingPlan.Floor floor : building.floors()) {
                for (BuildingPlan.Room r : floor.rooms()) {
                    if (r.id() == room && r.functions().contains(BuildingPlan.Function.HIDDEN_REFUGE)) { return room; }
                }
            }
        }
        return UrbanIds.NONE;
    }

    /** The event-log line for a newly discovered site. */
    public static String discoveryLine(Place place) {
        return switch (place) {
            case STATION -> "An abandoned transit station. Its tunnels run on into the dark.";
            case HIDDEN_CELLAR -> "A hidden cellar. Someone has been sheltering here.";
            default -> throw new IllegalArgumentException(place + " is not a discoverable site");
        };
    }
}
