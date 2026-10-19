package com.veylon.world.urban;

import com.veylon.world.BlockType;

/**
 * The abandoned transit layer of layout ten (milestone 14): tunnels under the boundary
 * avenues, as pure functions of the seed and the avenue they run under.
 *
 * <h2>Where tunnels run</h2>
 *
 * <p>Every planning-region edge carries a twelve-wide avenue centred on it, and nothing but
 * street fill lies under an avenue: no parcel, foundation, basement or overlay reaches into
 * its band. Tunnels therefore run only there, so they never cut a building. A
 * <em>candidate line</em> is a region edge with an even index: north-south candidates are the
 * west edges of even region columns, east-west candidates the north edges of even rows. A
 * region always borders exactly one candidate of each axis, so it holds at most two tunnel
 * <em>segments</em>, the stretches of candidate line along its two edges, each shared with
 * the neighbour across it.
 *
 * <h2>Why neighbours agree</h2>
 *
 * <p>A segment is named by its axis, its line and its index along the line (the region row of
 * a north-south segment, the region column of an east-west one). Whether it exists, whether
 * it is flooded, whether its ends have fallen in, where its rubble lies and where its station
 * stands are functions of that name, the seed and the two bordering regions' plans only, so
 * the two regions stamp mirror halves of one tunnel whichever chunk generates first. The
 * cross-section is described by the mirrored offset {@code m} from the line, as the avenue's
 * own profile is ({@link StreetNetwork}). Where two candidate lines cross, each quadrant of
 * the junction is the union of the two segments it belongs to: open space wins over wall, so
 * crossing tunnels join without either needing the other's plan.
 *
 * <h2>Stations and access</h2>
 *
 * <p>Each segment has one owner region: the east region of a north-south segment on an even
 * row and the west one on an odd row; the south region of an east-west segment on an odd
 * column and the north one on an even column. That rule gives every region exactly one owned
 * segment, so no region holds more than one station. A segment exists only if its station can
 * be sited on its owner's sidewalk ({@link TransitStation#site}); a tunnel section therefore
 * always has a way in. Rubble mounds inside a segment slow a walk but never close it; only a
 * segment's ends can be blocked, by a full plug just outside the junction.
 *
 * <p>Vertical datum: the track bed is the B2 slab (y {@value #SLAB_Y}), feet stand at
 * y {@value #FEET_Y} with three clear cells, and the ceiling is the B1 slab
 * (y {@value #CEILING_Y}); a flooded segment holds one block of standing water on its track,
 * above the band where water spreads, on a solid bed, so it never moves.
 */
public final class TransitNetwork {
    /** Cross-section: track cells at mirrored offsets 0..2 each side, a wall at 3. */
    public static final int TRACK = 3;
    public static final int WALL = 3;
    /** The avenue's half width: every transit cell lies within it. */
    public static final int BAND = StreetNetwork.AVENUE_HALF_WIDTH;
    public static final int SLAB_Y = 32;
    public static final int FEET_Y = SLAB_Y + 1;
    public static final int CEILING_Y = 36;
    /** Along-offsets of the end plugs: just outside the junction box at either end. */
    public static final int PLUG_LOW = BAND + 1;
    public static final int PLUG_HIGH = StreetNetwork.REGION_SIZE - BAND - 2;
    /** Initial shares (DESIGN §10 package 14): about half the candidates carry a tunnel. */
    static final double PRESENT = 0.55;
    static final double PLUGGED = 0.3;
    static final int MAX_MOUNDS = 2;

    /** Region plans by coordinates; the world's bounded cache or a test's direct planner. */
    @FunctionalInterface
    public interface RegionPlans {
        DistrictPlan plan(int rx, int rz);
    }

    /**
     * One candidate stretch of tunnel and everything about it a chunk needs.
     *
     * @param northSouth axis of the avenue
     * @param line the region edge: the column whose west edge it is (north-south) or the row
     *        whose north edge it is (east-west)
     * @param index the region row (north-south) or column (east-west) it runs along
     * @param present whether a tunnel runs here at all
     * @param flooded one block of standing water on the track
     * @param plugLow the end at along-offset {@value #PLUG_LOW} has fallen in
     * @param plugHigh the end at along-offset {@value #PLUG_HIGH} has fallen in
     * @param mounds along-offsets of low rubble mounds across the track
     * @param station the owner region's station, null when the segment is absent
     */
    public record Segment(boolean northSouth, int line, int index, boolean present, boolean flooded,
                          boolean plugLow, boolean plugHigh, int[] mounds, TransitStation station) {
        public Segment { mounds = mounds.clone(); }

        @Override public int[] mounds() { return mounds.clone(); }

        /** The positive (east or south) side owns the station on this segment. */
        public boolean ownerPositive() { return TransitNetwork.ownerPositive(northSouth, index); }

        boolean mound(int a) {
            for (int m : mounds) { if (m == a) { return true; } }
            return false;
        }
    }

    private TransitNetwork() { }

    public static boolean candidate(int line) {
        return Math.floorMod(line, 2) == 0;
    }

    /** Which side owns a segment's station: see the class description. */
    public static boolean ownerPositive(boolean northSouth, int index) {
        boolean even = Math.floorMod(index, 2) == 0;
        return northSouth == even;
    }

    /** Packed cache key of a segment. */
    public static long key(boolean northSouth, int line, int index) {
        return (northSouth ? 1L : 2L) << 60 | ((long) line & 0x3FF_FFFFL) << 30 | ((long) index & 0x3FF_FFFFL);
    }

    /** The region on one side of a segment: {rx, rz}. */
    public static int[] region(boolean northSouth, int line, int index, boolean positive) {
        int across = positive ? line : line - 1;
        return northSouth ? new int[]{across, index} : new int[]{index, across};
    }

    /**
     * Plans one segment. Pure: the same name always gives the same segment, whichever region
     * asks. Absent unless the line is a candidate, both bordering regions are planned, the
     * presence roll passes (the spawn region's own north-south segment always does) and the
     * owner's station can be sited.
     */
    public static Segment plan(long seed, boolean northSouth, int line, int index, RegionPlans plans) {
        int[] pos = region(northSouth, line, index, true), neg = region(northSouth, line, index, false);
        if (!candidate(line) || !CityPlanner.isPlannable(pos[0], pos[1]) || !CityPlanner.isPlannable(neg[0], neg[1])) {
            return absent(northSouth, line, index);
        }
        int axis = northSouth ? 1 : 2;
        boolean forced = northSouth && line == 0 && index == 0;
        if (!forced && UrbanHash.unit(seed, UrbanRevision.LAYOUT_UNDERGROUND, UrbanSalts.TRANSIT_SEGMENT,
                axis, line, index) >= PRESENT) {
            return absent(northSouth, line, index);
        }
        boolean flooded = DistrictMap.districtOf(seed, pos[0], pos[1]) == UrbanDistrict.FLOODED
                || DistrictMap.districtOf(seed, neg[0], neg[1]) == UrbanDistrict.FLOODED;
        boolean owner = ownerPositive(northSouth, index);
        int[] o = owner ? pos : neg;
        Frame frame = new Frame(northSouth, line, index, owner);
        TransitStation station = TransitStation.site(seed, frame, plans.plan(o[0], o[1]), flooded);
        if (station == null) {
            return absent(northSouth, line, index);
        }
        boolean plugLow = UrbanHash.unit(seed, UrbanRevision.LAYOUT_UNDERGROUND, UrbanSalts.TRANSIT_PLUG,
                axis, line, index * 2L) < PLUGGED;
        boolean plugHigh = UrbanHash.unit(seed, UrbanRevision.LAYOUT_UNDERGROUND, UrbanSalts.TRANSIT_PLUG,
                axis, line, index * 2L + 1) < PLUGGED;
        int count = UrbanHash.range(0, MAX_MOUNDS, seed, UrbanRevision.LAYOUT_UNDERGROUND,
                UrbanSalts.TRANSIT_MOUND, axis, line, index * 8L);
        int[] mounds = new int[count];
        int placed = 0;
        for (int k = 0; placed < count && k < 8; k++) {
            int a = UrbanHash.range(PLUG_LOW + 20, PLUG_HIGH - 20, seed, UrbanRevision.LAYOUT_UNDERGROUND,
                    UrbanSalts.TRANSIT_MOUND, axis, line, index * 8L + 1 + k);
            if (a >= station.low() - 4 && a <= station.high() + 4) { continue; }
            mounds[placed++] = a;
        }
        return new Segment(northSouth, line, index, true, flooded, plugLow, plugHigh,
                java.util.Arrays.copyOf(mounds, placed), station);
    }

    private static Segment absent(boolean northSouth, int line, int index) {
        return new Segment(northSouth, line, index, false, false, false, false, new int[0], null);
    }

    /**
     * Converts a segment side's mirrored offset {@code m} (0 next to the line) and along-offset
     * {@code a} (0 at the segment's west or north end) to world columns and back.
     */
    public record Frame(boolean northSouth, int line, int index, boolean positive) {
        public int edge() { return StreetNetwork.regionOrigin(line); }
        public int along0() { return StreetNetwork.regionOrigin(index); }
        public int cross(int m) { return positive ? edge() + m : edge() - 1 - m; }
        public int x(int m, int a) { return northSouth ? cross(m) : along0() + a; }
        public int z(int m, int a) { return northSouth ? along0() + a : cross(m); }
        /** The world column one step towards higher {@code a}. */
        public int alongDx() { return northSouth ? 0 : 1; }
        public int alongDz() { return northSouth ? 1 : 0; }
        /** The world step towards higher {@code m}. */
        public int acrossDx() { return northSouth ? (positive ? 1 : -1) : 0; }
        public int acrossDz() { return northSouth ? 0 : (positive ? 1 : -1); }
    }

    /**
     * The tunnel block a segment puts at mirrored offset {@code m}, along-offset {@code a} and
     * height {@code y} on one of its sides, or null to leave the street fill. Station cells on
     * the owner side come first.
     */
    public static BlockType blockAt(Segment s, boolean positive, int m, int a, int y) {
        if (!s.present() || m >= BAND) { return null; }
        if (positive == s.ownerPositive() && s.station().covers(a)) {
            return s.station().blockAt(m, a, y);
        }
        return tunnelBlock(s, m, a, y);
    }

    /** The plain cross-section: track, wall, plugs at closed ends and low mounds. */
    static BlockType tunnelBlock(Segment s, int m, int a, int y) {
        int low = s.plugLow() ? PLUG_LOW : 0, high = s.plugHigh() ? PLUG_HIGH : StreetNetwork.REGION_SIZE - 1;
        if (m > WALL || a < low || a > high || y < SLAB_Y || y > CEILING_Y) { return null; }
        if (m == WALL) { return BlockType.CONCRETE; }
        if (y == SLAB_Y) { return m == 1 ? BlockType.SHEET_METAL : BlockType.GRAVEL; }
        if (y == CEILING_Y) { return BlockType.CONCRETE; }
        if (a == low && s.plugLow() || a == high && s.plugHigh()) { return BlockType.RUBBLE; }
        if (y == FEET_Y && s.mound(a)) { return BlockType.RUBBLE; }
        return y == FEET_Y && s.flooded() ? BlockType.WATER : BlockType.AIR;
    }

    /**
     * Two segments' blocks for one junction cell: open space wins over wall, so crossing
     * tunnels join; otherwise the first solid answer, or null when neither claims the cell.
     */
    static BlockType union(BlockType a, BlockType b) {
        if (a == null) { return b; }
        if (b == null) { return a; }
        boolean openA = !a.solid, openB = !b.solid;
        if (openA || openB) {
            return a == BlockType.WATER || b == BlockType.WATER ? BlockType.WATER : BlockType.AIR;
        }
        return a;
    }

    /**
     * Which segment and side a column belongs to along one axis, with its mirrored offset
     * {@code m} from the line and its along-offset {@code a} in the segment.
     */
    public record Band(boolean northSouth, int line, int index, boolean positive, int m, int a) { }

    /**
     * The candidate band a column lies in along one axis, or null: within {@link #BAND} of a
     * candidate edge, x edges for north-south lines and z edges for east-west ones.
     */
    public static Band band(boolean northSouth, int x, int z) {
        int across = northSouth ? x : z, along = northSouth ? z : x;
        int region = StreetNetwork.regionOf(across);
        int local = across - StreetNetwork.regionOrigin(region);
        int line, m;
        boolean positive;
        if (local < BAND) { line = region; m = local; positive = true; }
        else if (local >= StreetNetwork.REGION_SIZE - BAND) {
            line = region + 1; m = StreetNetwork.REGION_SIZE - 1 - local; positive = false;
        } else { return null; }
        if (!candidate(line)) { return null; }
        int index = StreetNetwork.regionOf(along);
        return new Band(northSouth, line, index, positive, m, along - StreetNetwork.regionOrigin(index));
    }

    /**
     * The transit block of a world cell, or null to leave whatever the street put there. Reads
     * at most two segments, each from {@code segments}, so a caller can cache them.
     */
    public static BlockType blockAt(java.util.function.Function<Band, Segment> segments, int x, int y, int z) {
        Band ns = band(true, x, z), ew = band(false, x, z);
        BlockType a = ns == null ? null : blockAt(segments.apply(ns), ns.positive(), ns.m(), ns.a(), y);
        BlockType b = ew == null ? null : blockAt(segments.apply(ew), ew.positive(), ew.m(), ew.a(), y);
        return union(a, b);
    }

    /** True when any column of the chunk lies in a candidate band, so most chunks skip at once. */
    public static boolean touches(int chunkX, int chunkZ) {
        int x0 = chunkX * 16, z0 = chunkZ * 16;
        return bandHit(x0) || bandHit(z0);
    }

    private static boolean bandHit(int origin) {
        for (int c = origin; c < origin + 16; c += 15) {
            int region = StreetNetwork.regionOf(c), local = c - StreetNetwork.regionOrigin(region);
            if (local < BAND && candidate(region)
                    || local >= StreetNetwork.REGION_SIZE - BAND && candidate(region + 1)) { return true; }
        }
        return false;
    }
}
