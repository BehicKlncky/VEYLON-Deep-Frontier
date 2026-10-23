package com.veylon.world.urban;

import com.veylon.util.Vec3i;

import java.util.ArrayList;
import java.util.List;

/**
 * The burnt-out cars of one layout-fourteen region (urban milestone 18): static salvage props left
 * in a lane of its local streets, nothing that moves or drives.
 *
 * <p>Each local street line of the region (three north-south, three east-west) runs through four
 * segments between the crossings and avenues that cut it, so a region offers 24 segments. A
 * segment is offered a wreck with odds of one in {@value #SEGMENT_ODDS}, at most
 * {@value #MAX_PER_REGION} a region, each
 * placed from (seed, layout, region, line, segment) alone: a hashed side of the centre line, a
 * hashed offset along the segment and a hashed front. A wreck is {@value #WIDTH} cells across (one
 * side's whole roadway lane) and {@value #LENGTH} long: a lower course of eight cells at feet height
 * and a cabin of four over its middle. The front pair of the lower course is its engine.
 *
 * <p>A candidate is kept only where all of it stands on planned roadway with no district overlay
 * (so never in a flooded street's water, on a contaminated patch or a duckboard), no parcel, at
 * least {@value #CROSSING_CLEARANCE} cells from either end of its segment and
 * {@value #COMPOUND_CLEARANCE} cells from any compound's gate, barricades and posts. The other lane
 * and both sidewalks stay open, so every street, entrance and encounter approach keeps its way
 * past.
 *
 * <p>Pure and stateless: any chunk asks the same plan, so the wrecks are identical in every chunk
 * order. What salvage has taken from one is ordinary recorded block edits.
 */
public final class VehicleSites {
    /** Most wrecks in one region. */
    public static final int MAX_PER_REGION = 6;
    /** Cells across the street: one side's two roadway cells. */
    public static final int WIDTH = 2;
    /** Cells along the street. */
    public static final int LENGTH = 4;
    /** Clear roadway kept between a wreck and either end of its segment. */
    public static final int CROSSING_CLEARANCE = 6;
    /** Clear cells kept between a wreck and a compound's gate, barricades and posts. */
    public static final int COMPOUND_CLEARANCE = 6;
    /** Feet height of the lower course: the street datum's walking cell. */
    public static final int FEET = UrbanDatum.STREET_Y + 1;
    /** One wreck in three has had its cabin burnt out: no seat fabric is left in it. */
    static final int BURNT_ONE_IN = 3;
    /** One street segment in this many is offered a wreck. */
    static final int SEGMENT_ODDS = 5;

    private static final int LAYOUT = UrbanRevision.LAYOUT_REFUGES;
    private static final int SEGMENTS = StreetNetwork.SUPERBLOCKS_PER_SIDE;

    /** What part of a wreck a cell is; it decides what dismantling it yields. */
    public enum Part {
        /** A lower body cell: panels and frame. */
        BODY,
        /** The front pair of the lower course: the engine bay. */
        ENGINE,
        /** The cabin over the middle: roof, doors and seats. */
        CABIN
    }

    /**
     * One wreck.
     *
     * @param index    its place in the region's list, in plan order
     * @param x0       west edge of its footprint
     * @param z0       north edge of its footprint
     * @param alongZ   true when it lies along a north-south street
     * @param frontLow true when its engine is at the low end of its length
     * @param burnt    true when its cabin burnt out
     */
    public record Wreck(int index, int x0, int z0, boolean alongZ, boolean frontLow, boolean burnt) {
        public int x1() { return x0 + (alongZ ? WIDTH : LENGTH) - 1; }
        public int z1() { return z0 + (alongZ ? LENGTH : WIDTH) - 1; }

        /** True on a footprint column. */
        public boolean covers(int x, int z) {
            return x >= x0 && x <= x1() && z >= z0 && z <= z1();
        }

        /** Distance of a footprint column from the wreck's low end, 0 to {@code LENGTH - 1}. */
        private int along(int x, int z) {
            return alongZ ? z - z0 : x - x0;
        }

        /** The part a cell of the wreck is, or null for a cell that is not part of it. */
        public Part partAt(int x, int y, int z) {
            if (!covers(x, z)) { return null; }
            int a = along(x, z);
            if (y == FEET) {
                return a == (frontLow ? 0 : LENGTH - 1) ? Part.ENGINE : Part.BODY;
            }
            return y == FEET + 1 && a >= 1 && a <= LENGTH - 2 ? Part.CABIN : null;
        }

        /** Every cell of the wreck: the lower course, then the cabin. */
        public List<Vec3i> cells() {
            List<Vec3i> out = new ArrayList<>(WIDTH * LENGTH + WIDTH * (LENGTH - 2));
            for (int y = FEET; y <= FEET + 1; y++) {
                for (int x = x0; x <= x1(); x++) {
                    for (int z = z0; z <= z1(); z++) {
                        if (partAt(x, y, z) != null) { out.add(new Vec3i(x, y, z)); }
                    }
                }
            }
            return out;
        }
    }

    /** No wrecks: every region of every layout before fourteen. */
    public static final VehicleSites NONE = new VehicleSites(List.of());

    private final List<Wreck> wrecks;

    private VehicleSites(List<Wreck> wrecks) {
        this.wrecks = List.copyOf(wrecks);
    }

    public List<Wreck> wrecks() {
        return wrecks;
    }

    /** The wreck holding a cell, or null. */
    public Wreck wreckAt(int x, int y, int z) {
        for (Wreck w : wrecks) {
            if (w.partAt(x, y, z) != null) { return w; }
        }
        return null;
    }

    /**
     * Plans a region's wrecks.
     *
     * @param sites the region's survivor sites and compounds, whose gates keep their streets clear
     */
    public static VehicleSites of(long seed, DistrictPlan plan, SurvivorSites sites) {
        int rx = StreetNetwork.regionOf(plan.originX()), rz = StreetNetwork.regionOf(plan.originZ());
        List<Wreck> out = new ArrayList<>();
        List<Vec3i> guarded = guardedCells(sites);
        // Lines in turn, each north-south street before the east-west one of the same index, so the
        // cap does not fall on one direction's streets.
        for (int k = 1; k < StreetNetwork.SUPERBLOCKS_PER_SIDE && out.size() < MAX_PER_REGION; k++) {
            for (int axis = 0; axis < 2 && out.size() < MAX_PER_REGION; axis++) {
                boolean northSouth = axis == 0;
                int line = northSouth ? plan.northSouthLine(k) : plan.eastWestLine(k);
                for (int s = 0; s < SEGMENTS && out.size() < MAX_PER_REGION; s++) {
                    long key = (long) axis << 8 | (long) k << 4 | s;
                    if (UrbanHash.range(0, SEGMENT_ODDS - 1, seed, LAYOUT, UrbanSalts.VEHICLE_SEGMENT, rx, rz, key) != 0) {
                        continue;
                    }
                    int[] span = segment(plan, northSouth, s);
                    int start = span[0] + CROSSING_CLEARANCE, end = span[1] - CROSSING_CLEARANCE - LENGTH + 1;
                    if (end < start) { continue; }
                    int offset = UrbanHash.range(start, end, seed, LAYOUT, UrbanSalts.VEHICLE_OFFSET, rx, rz, key);
                    boolean high = UrbanHash.range(0, 1, seed, LAYOUT, UrbanSalts.VEHICLE_SIDE, rx, rz, key) == 1;
                    // The roadway's two cells beside the centre line on the chosen side (StreetNetwork.bandPart).
                    int across = high ? line : line - WIDTH;
                    boolean frontLow = UrbanHash.range(0, 1, seed, LAYOUT, UrbanSalts.VEHICLE_FRONT, rx, rz, key) == 0;
                    boolean burnt = UrbanHash.range(0, BURNT_ONE_IN - 1, seed, LAYOUT, UrbanSalts.VEHICLE_BURNT, rx, rz, key) == 0;
                    Wreck w = northSouth
                            ? new Wreck(out.size(), across, offset, true, frontLow, burnt)
                            : new Wreck(out.size(), offset, across, false, frontLow, burnt);
                    if (fits(plan, w, guarded)) { out.add(w); }
                }
            }
        }
        return out.isEmpty() ? NONE : new VehicleSites(out);
    }

    /**
     * The open stretch of segment {@code s} of a local street, between the avenue or crossing
     * street bands at its ends, as inclusive coordinates along it: z for a north-south street, x
     * for an east-west one. The crossing lines are the plan's own, so the stretch is exactly what
     * the plan rasterises as this street.
     */
    private static int[] segment(DistrictPlan plan, boolean northSouth, int s) {
        int origin = northSouth ? plan.originZ() : plan.originX();
        int lo = s == 0 ? origin + StreetNetwork.AVENUE_HALF_WIDTH
                : crossing(plan, northSouth, s) + StreetNetwork.LOCAL_HALF_WIDTH;
        int hi = s == SEGMENTS - 1 ? origin + StreetNetwork.REGION_SIZE - StreetNetwork.AVENUE_HALF_WIDTH - 1
                : crossing(plan, northSouth, s + 1) - StreetNetwork.LOCAL_HALF_WIDTH - 1;
        return new int[]{lo, hi};
    }

    /** Centre line {@code k} of the local streets crossing a street of the given direction. */
    private static int crossing(DistrictPlan plan, boolean northSouth, int k) {
        return northSouth ? plan.eastWestLine(k) : plan.northSouthLine(k);
    }

    /** Every footprint column on planned roadway, overlay-free, unparcelled and clear of compounds. */
    private static boolean fits(DistrictPlan plan, Wreck w, List<Vec3i> guarded) {
        for (int x = w.x0(); x <= w.x1(); x++) {
            for (int z = w.z0(); z <= w.z1(); z++) {
                if (!plan.contains(x, z) || plan.surfaceAt(x, z) != UrbanSurface.ROADWAY
                        || plan.overlayAt(x, z) != UrbanOverlay.NONE || plan.parcelAt(x, z) != null) {
                    return false;
                }
                for (Vec3i g : guarded) {
                    if (Math.max(Math.abs(g.x() - x), Math.abs(g.z() - z)) <= COMPOUND_CLEARANCE) { return false; }
                }
            }
        }
        return true;
    }

    private static List<Vec3i> guardedCells(SurvivorSites sites) {
        List<Vec3i> out = new ArrayList<>();
        for (CompoundSites.Compound c : sites.compounds()) {
            for (CompoundSites.Barricade b : c.barricades()) { out.addAll(b.cells()); }
            out.addAll(c.posts());
            out.add(c.gate().laneOutside());
            out.add(c.gate().outside());
        }
        return out;
    }

    @Override
    public String toString() {
        return "VehicleSites" + wrecks;
    }
}
