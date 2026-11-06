package com.veylon.world.urban;

import com.veylon.world.BlockType;

import java.util.List;

/**
 * Marks the city's people leave for each other beside a street door (urban milestone 22): a
 * chalked ringed dot by the door of a building whose cellar hides people, a painted arrow sending
 * callers round to a camp's rear door, a red warning daubed by a raider yard's. Each is a decal
 * on the wall beside the door at eye height, and the interaction line reads its words.
 *
 * <p>Pure: derived from the site plans of the region, never saved and never a block, so a trace
 * cannot change saved geometry or narrow a way. A trace only hints: it finds nothing, marks no map
 * and starts no mission; hidden people are still found by their sounds and the loose panel.
 * Strongholds keep their own signs. Layouts before the survivor sites have none.
 */
public final class UrbanTraces {
    /** What a trace says and the decal that draws it; the words are original and fit the prompt line. */
    public enum Kind {
        KNOCK_MARK("decal_chalk", "Chalk by the door: a ringed dot. \"Knock twice.\""),
        CAMP_ARROW("decal_arrow", "Painted arrow: \"Camp round the back. Hands empty.\""),
        RAIDER_WARNING("decal_warning", "Daubed in red: \"TURN BACK. WE SEE YOU.\"");

        private final String tile, text;

        Kind(String tile, String text) {
            this.tile = tile;
            this.text = text;
        }

        public String tile() {
            return tile;
        }

        public String text() {
            return text;
        }
    }

    /** A trace on the side face {@code face} (the mesher's 2..5) of the wall cell {@code (x, y, z)}. */
    public record Trace(int x, int y, int z, int face, Kind kind) {
        public boolean on(int cx, int cy, int cz, int cface) {
            return x == cx && y == cy && z == cz && face == cface;
        }
    }

    /** Traces sit at eye height on the street storey, beside the door. */
    public static final int EYE_Y = UrbanDatum.STREET_Y + 2;

    private UrbanTraces() { }

    /**
     * Adds to {@code out} the traces of a region's sites that lie in the block rectangle
     * {@code [x0, x1] x [z0, z1]}; nothing outside a city with survivor sites. Bounded by the sites
     * the region plans.
     */
    public static void collect(UrbanWorldState urban, int rx, int rz, int x0, int z0, int x1, int z1, List<Trace> out) {
        if (urban == null || !urban.hasSurvivors() || !CityPlanner.isPlannable(rx, rz)) { return; }
        for (SurvivorSites.Site site : urban.sites(rx, rz).sites()) {
            Trace t = of(urban, site);
            if (t != null && t.x() >= x0 && t.x() <= x1 && t.z() >= z0 && t.z() <= z1) { out.add(t); }
        }
    }

    /** The trace on one face of a cell, or null; reads the cell's region plans and allocates nothing else. */
    public static Trace at(UrbanWorldState urban, int x, int y, int z, int face) {
        if (y != EYE_Y || face < 2 || urban == null || !urban.hasSurvivors()) { return null; }
        int rx = StreetNetwork.regionOf(x), rz = StreetNetwork.regionOf(z);
        if (!CityPlanner.isPlannable(rx, rz)) { return null; }
        for (SurvivorSites.Site site : urban.sites(rx, rz).sites()) {
            Trace t = of(urban, site);
            if (t != null && t.on(x, y, z, face)) { return t; }
        }
        return null;
    }

    /** A site's trace: on the first wall cell beside its building's street door, facing the street. */
    static Trace of(UrbanWorldState urban, SurvivorSites.Site site) {
        Kind kind = switch (site.kind()) {
            case REFUGE -> Kind.KNOCK_MARK;
            case CAMP -> Kind.CAMP_ARROW;
            case RAIDERS -> Kind.RAIDER_WARNING;
            case STRONGHOLD -> null;
        };
        if (kind == null) { return null; }
        BuildingPlan building = urban.buildingAt(site.anchor().x(), site.anchor().z());
        if (building == null) { return null; }
        ParcelPlan.Entrance door = building.parcel().primaryEntrance();
        ParcelPlan.Side out = door.facing();
        int ax = out.dz != 0 ? 1 : 0, az = out.dx != 0 ? 1 : 0; // along the facade
        int face = switch (out) {
            case NORTH -> 2;
            case SOUTH -> 3;
            case WEST -> 4;
            case EAST -> 5;
        };
        int[][] beside = {{door.x() - ax, door.z() - az}, {door.x2() + ax, door.z2() + az}};
        for (int[] cell : beside) {
            BlockType block = building.voxels().getBlock(cell[0], EYE_Y, cell[1]);
            if (block == BlockType.BRICK || block == BlockType.PLASTER || block == BlockType.CONCRETE) {
                return new Trace(cell[0], EYE_Y, cell[1], face, kind);
            }
        }
        return null;
    }
}
