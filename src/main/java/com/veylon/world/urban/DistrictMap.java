package com.veylon.world.urban;

/**
 * The district identity of every planning region under layout revision 9 (milestone 13).
 *
 * <p>Pure: a function of the seed and the region coordinates, so neighbouring regions agree
 * about each other (a cordon or a canal never depends on which region was planned first) and
 * an evicted plan is rebuilt with the same identity. Guarantees are constructive rather than
 * hoped for, so they hold on every seed, not only on the design seeds:
 * <ul>
 *   <li>The spawn region (0, 0) is reclaimed residential (D-09).</li>
 *   <li>Its eight neighbours are residential or commercial, never flooded or quarantine, and at
 *       least one of them is commercial.</li>
 *   <li>Within three regions (Chebyshev) of the spawn there is at least one industrial, one
 *       flooded, one civic and one quarantine region. The quarantine one is exactly three
 *       regions out, and no quarantine region lies closer.</li>
 * </ul>
 * Every other region draws its identity from fixed weights. No district is ever required for
 * the first meal, water or treatment: those come from the residential and commercial ring.
 */
public final class DistrictMap {

    /** Chebyshev distance from the spawn region within which every district appears. */
    public static final int ALL_DISTRICTS_RING = 3;
    /** Share of the first ring that is commercial beyond the one guaranteed region. */
    static final double RING_ONE_COMMERCIAL = 0.35;
    /** Open-draw weights, in percent, in {@link UrbanDistrict} order. */
    static final int[] WEIGHTS = {36, 22, 14, 10, 10, 8};
    private static final int LAYOUT = UrbanRevision.LAYOUT_DISTRICTS;
    private static final UrbanDistrict[] RESERVED = {
        UrbanDistrict.INDUSTRIAL, UrbanDistrict.FLOODED, UrbanDistrict.CIVIC};

    private DistrictMap() {
    }

    /** Chebyshev distance of a region from the spawn region. */
    public static int ring(int rx, int rz) {
        return Math.max(Math.abs(rx), Math.abs(rz));
    }

    /** The district identity of a region. */
    public static UrbanDistrict districtOf(long seed, int rx, int rz) {
        int ring = ring(rx, rz);
        if (ring == 0) {
            return UrbanDistrict.RESIDENTIAL;
        }
        if (ring == 1) {
            boolean anchor = ringIndex(rx, rz) == UrbanHash.range(0, 7, seed, LAYOUT, UrbanSalts.DISTRICT_ANCHORS,
                    1, 0, 0);
            return anchor || UrbanHash.unit(seed, LAYOUT, UrbanSalts.DISTRICT_MAP, rx, rz, 1) < RING_ONE_COMMERCIAL
                    ? UrbanDistrict.COMMERCIAL : UrbanDistrict.RESIDENTIAL;
        }
        if (ring <= ALL_DISTRICTS_RING) {
            UrbanDistrict reserved = reserved(seed, rx, rz);
            if (reserved != null) {
                return reserved;
            }
        }
        UrbanDistrict open = open(seed, rx, rz);
        // The guaranteed quarantine region is the nearest one: none inside the third ring.
        return open == UrbanDistrict.QUARANTINE && ring < ALL_DISTRICTS_RING ? UrbanDistrict.RESIDENTIAL : open;
    }

    /** The weighted draw of a region without a guarantee. */
    static UrbanDistrict open(long seed, int rx, int rz) {
        int roll = UrbanHash.range(0, 99, seed, LAYOUT, UrbanSalts.DISTRICT_MAP, rx, rz, 0);
        UrbanDistrict[] districts = UrbanDistrict.values();
        for (int i = 0; i < WEIGHTS.length; i++) {
            if (roll < WEIGHTS[i]) {
                return districts[i];
            }
            roll -= WEIGHTS[i];
        }
        throw new IllegalStateException("district weights do not sum to 100");
    }

    /**
     * The guaranteed specialist on a region of rings two and three, or null. Quarantine takes
     * the third-ring region with the lowest key; industrial, flooded and civic then each take
     * the lowest key among the remaining regions of rings two and three.
     */
    static UrbanDistrict reserved(long seed, int rx, int rz) {
        long quarantine = lowest(seed, UrbanDistrict.QUARANTINE, ALL_DISTRICTS_RING, ALL_DISTRICTS_RING, 0L, 0);
        if (quarantine == pack(rx, rz)) {
            return UrbanDistrict.QUARANTINE;
        }
        long[] taken = {quarantine, 0, 0, 0};
        for (int i = 0; i < RESERVED.length; i++) {
            long pick = lowest(seed, RESERVED[i], 2, ALL_DISTRICTS_RING, taken, i + 1);
            if (pick == pack(rx, rz)) {
                return RESERVED[i];
            }
            taken[i + 1] = pick;
        }
        return null;
    }

    private static long lowest(long seed, UrbanDistrict district, int fromRing, int toRing, long taken, int count) {
        return lowest(seed, district, fromRing, toRing, new long[]{taken}, count);
    }

    /** The packed region of rings {@code fromRing..toRing} with the lowest key, skipping {@code taken}. */
    private static long lowest(long seed, UrbanDistrict district, int fromRing, int toRing, long[] taken, int count) {
        long best = 0;
        long bestKey = 0;
        boolean found = false;
        for (int rz = -toRing; rz <= toRing; rz++) {
            for (int rx = -toRing; rx <= toRing; rx++) {
                int ring = ring(rx, rz);
                long packed = pack(rx, rz);
                if (ring < fromRing || isTaken(packed, taken, count)) {
                    continue;
                }
                long key = UrbanHash.mix(seed, LAYOUT, UrbanSalts.DISTRICT_ANCHORS, rx, rz, district.code);
                if (!found || Long.compareUnsigned(key, bestKey) < 0) {
                    best = packed;
                    bestKey = key;
                    found = true;
                }
            }
        }
        return best;
    }

    private static boolean isTaken(long packed, long[] taken, int count) {
        for (int i = 0; i < count; i++) {
            if (taken[i] == packed) {
                return true;
            }
        }
        return false;
    }

    private static long pack(int rx, int rz) {
        return ((long) rx << 32) | (rz & 0xffffffffL);
    }

    /** Index 0..7 of a first-ring region, clockwise from the north-west. */
    static int ringIndex(int rx, int rz) {
        return switch (rz) {
            case -1 -> rx + 1;
            case 0 -> rx == 1 ? 3 : 7;
            default -> 5 - rx;
        };
    }
}
