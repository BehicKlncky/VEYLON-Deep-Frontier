package com.veylon.world.urban;

/**
 * The street lattice of layout revision 2, as pure functions of the seed and
 * world coordinates.
 *
 * <h2>Shape</h2>
 *
 * <p>Every planning region is 256 × 256 blocks. Avenues run along every region
 * edge, 12 wide: 3 sidewalk, 6 roadway, 3 sidewalk, centred on the edge, so
 * each region holds half of each of its four avenues. Inside a region three
 * north-south and three east-west local streets, 8 wide (2 + 4 + 2), divide it
 * into a 4 × 4 grid of superblocks (decision D-06).
 *
 * <h2>Why neighbours always agree</h2>
 *
 * <p>Nothing here reads a neighbouring region's plan, and nothing depends on
 * which chunk asks first:
 * <ul>
 *   <li>An avenue's cross-section is a function of the avenue line and the
 *       segment along it ({@link #avenueProfile}). The two regions that share
 *       it ask with the same arguments and stamp mirror halves.</li>
 *   <li>A north-south local street's position depends on the region column
 *       {@code rx} and its index only — not on {@code rz} — so it continues
 *       straight across every east-west avenue, and likewise for east-west
 *       streets. The lattice is therefore connected everywhere, and every
 *       crossing at a region edge is a full intersection on both sides.</li>
 *   <li>Each band is described by its mirrored offset {@code m} from its centre
 *       line, so the cell at {@code m} on one side of a seam always matches the
 *       cell at {@code m} on the other.</li>
 * </ul>
 *
 * <p>Every street surface sits at the street datum ({@link UrbanDatum#STREET_Y});
 * the city of this revision is flat, so elevations agree by construction. The
 * avenue profile is the single place a later revision adds a canal or a level
 * change, so both sides of a seam keep agreeing.
 */
public final class StreetNetwork {

    public static final int REGION_SIZE = 256;
    public static final int SUPERBLOCKS_PER_SIDE = 4;
    /** Nominal distance between street lines. */
    public static final int SUPERBLOCK_PITCH = REGION_SIZE / SUPERBLOCKS_PER_SIDE;
    /** Cells on each side of an avenue's centre line: 3 roadway, then 3 sidewalk. */
    public static final int AVENUE_HALF_WIDTH = 6;
    /** Roadway cells on each side of an avenue's centre line. */
    public static final int AVENUE_ROADWAY_HALF = 3;
    /** Cells on each side of a local street's centre line: 2 roadway, then 2 sidewalk. */
    public static final int LOCAL_HALF_WIDTH = 4;
    /** Roadway cells on each side of a local street's centre line. */
    public static final int LOCAL_ROADWAY_HALF = 2;
    /** Largest shift of a local street line from its nominal position. */
    public static final int LINE_JITTER = 5;
    /** Narrowest superblock: two local streets jittered towards each other. */
    public static final int MIN_SUPERBLOCK_SIDE = SUPERBLOCK_PITCH - 2 * LOCAL_HALF_WIDTH - 2 * LINE_JITTER;
    /** Widest superblock: two local streets jittered apart. Edge blocks fall in between. */
    public static final int MAX_SUPERBLOCK_SIDE = SUPERBLOCK_PITCH - 2 * LOCAL_HALF_WIDTH + 2 * LINE_JITTER;
    /** Dashed centre line: {@value #LANE_DASH_LENGTH} marked cells every {@value #LANE_DASH_PERIOD}. */
    public static final int LANE_DASH_PERIOD = 6;
    public static final int LANE_DASH_LENGTH = 3;

    /** Cross-section of one avenue segment between two region corners. */
    public enum AvenueProfile {
        /** Six roadway cells with a dashed centre line. */
        PLAIN,
        /** Two roadway cells each side of a two-cell planted median. */
        BOULEVARD
    }

    // Band parts, packed with the mirrored offset as (part << 8) | m.
    static final int NONE = 0;
    static final int CENTRE_LANE = 1;
    static final int CENTRE_MEDIAN = 2;
    static final int ROAD = 3;
    static final int WALK = 4;

    private StreetNetwork() {
    }

    /** The planning region a block coordinate belongs to; correct for negatives. */
    public static int regionOf(int block) {
        return Math.floorDiv(block, REGION_SIZE);
    }

    /** First block coordinate of a region. */
    public static int regionOrigin(int region) {
        return region * REGION_SIZE;
    }

    /**
     * The centre line of local street {@code k} (1..3) of a region along one
     * axis: the first cell of its east or south half.
     *
     * @param northSouth true for a north-south street (an x position in region
     *        column {@code region}); false for an east-west street (a z position
     *        in region row {@code region})
     * @throws IllegalArgumentException unless {@code k} is 1, 2 or 3
     */
    public static int localLine(long seed, int layoutRevision, boolean northSouth, int region, int k) {
        if (k < 1 || k >= SUPERBLOCKS_PER_SIDE) {
            throw new IllegalArgumentException("local street index " + k + " is outside 1..3");
        }
        long salt = northSouth ? UrbanSalts.LINE_JITTER_X : UrbanSalts.LINE_JITTER_Z;
        int jitter = UrbanHash.range(-LINE_JITTER, LINE_JITTER, seed, layoutRevision, salt,
                region, k, 0);
        return regionOrigin(region) + SUPERBLOCK_PITCH * k + jitter;
    }

    /**
     * The cross-section of one avenue segment.
     *
     * @param northSouth true for an avenue along a north-south region edge
     * @param line the edge: the region column whose west edge it is
     *        (north-south) or the region row whose north edge it is (east-west)
     * @param segment the region row (north-south) or column (east-west) the
     *        segment runs along
     */
    public static AvenueProfile avenueProfile(long seed, int layoutRevision, boolean northSouth,
                                              int line, int segment) {
        double roll = UrbanHash.unit(seed, layoutRevision, UrbanSalts.AVENUE_PROFILE,
                northSouth ? 1 : 2, line, segment);
        return roll < 0.3 ? AvenueProfile.BOULEVARD : AvenueProfile.PLAIN;
    }

    /**
     * True on a boundary avenue: within an avenue's half width of a region edge along either
     * axis. Every region boundary, and so every district boundary, runs down an avenue's
     * centre line.
     */
    public static boolean onBoundaryAvenue(int x, int z) {
        return nearRegionEdge(x) || nearRegionEdge(z);
    }

    private static boolean nearRegionEdge(int coord) {
        int local = coord - regionOrigin(regionOf(coord));
        return local < AVENUE_HALF_WIDTH || local >= REGION_SIZE - AVENUE_HALF_WIDTH;
    }

    /**
     * Which part of a street band a coordinate falls in, along one axis, packed
     * as {@code (part << 8) | m} with {@code m} the mirrored offset from the
     * band's centre line (0 next to it).
     *
     * @param coord an x coordinate for north-south bands, a z for east-west ones
     * @param crossRegion the region index along the other axis, which selects
     *        the avenue segment
     */
    static int bandPart(long seed, int layoutRevision, boolean northSouth, int coord, int crossRegion) {
        int region = regionOf(coord);
        int local = coord - regionOrigin(region);
        if (local < AVENUE_HALF_WIDTH) {
            return avenuePart(local, avenueProfile(seed, layoutRevision, northSouth, region, crossRegion));
        }
        if (local >= REGION_SIZE - AVENUE_HALF_WIDTH) {
            return avenuePart(REGION_SIZE - 1 - local,
                    avenueProfile(seed, layoutRevision, northSouth, region + 1, crossRegion));
        }
        for (int k = 1; k < SUPERBLOCKS_PER_SIDE; k++) {
            int line = localLine(seed, layoutRevision, northSouth, region, k);
            if (coord >= line - LOCAL_HALF_WIDTH && coord < line + LOCAL_HALF_WIDTH) {
                int m = coord >= line ? coord - line : line - 1 - coord;
                return ((m < LOCAL_ROADWAY_HALF ? ROAD : WALK) << 8) | m;
            }
        }
        return NONE;
    }

    private static int avenuePart(int m, AvenueProfile profile) {
        int part;
        if (m == 0) {
            part = profile == AvenueProfile.BOULEVARD ? CENTRE_MEDIAN : CENTRE_LANE;
        } else if (m < AVENUE_ROADWAY_HALF) {
            part = ROAD;
        } else {
            part = WALK;
        }
        return (part << 8) | m;
    }

    /**
     * The street surface where a north-south and an east-west band part meet,
     * or null when the cell is in neither band (inside a superblock).
     *
     * <p>Where two bands cross, roadway wins: roadway over roadway is the open
     * intersection box (no centre line, the median cut); a sidewalk band
     * crossing a roadway becomes a crosswalk striped by the roadway's mirrored
     * offset; sidewalk over sidewalk is the corner.
     */
    static UrbanSurface combine(int northSouth, int eastWest, int x, int z) {
        int nsPart = northSouth >> 8;
        int ewPart = eastWest >> 8;
        if (nsPart == NONE && ewPart == NONE) {
            return null;
        }
        if (ewPart == NONE) {
            return single(nsPart, z);
        }
        if (nsPart == NONE) {
            return single(ewPart, x);
        }
        boolean nsRoad = nsPart != WALK;
        boolean ewRoad = ewPart != WALK;
        if (nsRoad && ewRoad) {
            return UrbanSurface.ROADWAY;
        }
        if (nsRoad) {
            return (northSouth & 0xff) % 2 == 0 ? UrbanSurface.CROSSWALK : UrbanSurface.ROADWAY;
        }
        if (ewRoad) {
            return (eastWest & 0xff) % 2 == 0 ? UrbanSurface.CROSSWALK : UrbanSurface.ROADWAY;
        }
        return UrbanSurface.SIDEWALK;
    }

    private static UrbanSurface single(int part, int along) {
        return switch (part) {
            case CENTRE_LANE -> Math.floorMod(along, LANE_DASH_PERIOD) < LANE_DASH_LENGTH
                    ? UrbanSurface.LANE_MARK : UrbanSurface.ROADWAY;
            case CENTRE_MEDIAN -> UrbanSurface.MEDIAN;
            case ROAD -> UrbanSurface.ROADWAY;
            default -> UrbanSurface.SIDEWALK;
        };
    }

    /**
     * The street surface of one column, or null inside a superblock. Pure and
     * plan-free: the same answer every region plan rasterises, which is what
     * the seam diagnostics and tests compare against.
     */
    public static UrbanSurface streetSurfaceAt(long seed, int layoutRevision, int x, int z) {
        int northSouth = bandPart(seed, layoutRevision, true, x, regionOf(z));
        int eastWest = bandPart(seed, layoutRevision, false, z, regionOf(x));
        return combine(northSouth, eastWest, x, z);
    }
}
