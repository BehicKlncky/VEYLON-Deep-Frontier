package com.veylon.world.urban;

import com.veylon.world.urban.ParcelPlan.Entrance;
import com.veylon.world.urban.ParcelPlan.Rect;
import com.veylon.world.urban.ParcelPlan.Side;
import com.veylon.world.urban.ParcelPlan.Use;
import com.veylon.world.urban.SuperblockPlan.Kind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Plans one planning region of layout revision 2: the street-network city.
 *
 * <p>{@link #plan} is a pure function of (seed, layout revision, rx, rz). It
 * reads no world state and no other region's plan, so a region is the same
 * whichever chunk asks first and however often its plan is evicted and
 * rebuilt. Cost is fixed per region: one 256 × 256 raster and at most 96
 * parcels and a few dozen trees.
 *
 * <h2>What a region holds</h2>
 *
 * <ul>
 *   <li>A district identity: region (0, 0) is residential, every other region
 *       residential or commercial (the other four identities arrive in package
 *       13, under their own generator).</li>
 *   <li>The street lattice of {@link StreetNetwork}.</li>
 *   <li>Sixteen superblocks. Residential regions are courtyard housing, with at
 *       most one park and possibly one shop row; commercial regions are shop
 *       rows along service alleys, four courtyard blocks and at most one
 *       plaza.</li>
 *   <li>Parcels with a primary street entrance, a rear entrance onto the
 *       courtyard or alley, a stair-core footprint and a storey envelope. One
 *       parcel per region is a service reservation.</li>
 *   <li>In region (0, 0): the starter refuge site and the spawn anchor in
 *       front of its courtyard door, plus a corner shop on the same block.</li>
 * </ul>
 *
 * <h2>Overlap priority</h2>
 *
 * <p>Cells are claimed in a fixed order — streets, then alleys and courtyard
 * passages, then parcels, then courtyards, parks and plazas, then tree beds —
 * and a cell is never taken from an earlier claimant. The subdivision is built
 * so no claim collides; a collision is counted in
 * {@link DistrictPlan#overlapConflicts()} for the diagnostics instead of being
 * resolved silently.
 */
public final class CityPlanner {

    /** Share of non-starter regions planned as commercial downtown. */
    static final double COMMERCIAL_SHARE = 0.35;
    static final double RESIDENTIAL_PARK_CHANCE = 0.6;
    static final double RESIDENTIAL_SHOP_ROW_CHANCE = 0.5;
    static final double COMMERCIAL_PLAZA_CHANCE = 0.6;
    static final int COMMERCIAL_HOUSING_BLOCKS = 4;
    static final double RESIDENTIAL_CORNER_SHOP_SHARE = 0.2;
    static final double COMMERCIAL_CORNER_SHOP_SHARE = 0.4;

    /** Strip depth range of a courtyard ring. */
    static final int RING_MIN_DEPTH = 12;
    static final int RING_MAX_DEPTH = 18;
    /** Smallest courtyard side (DESIGN §4.2). */
    static final int MIN_COURTYARD = 8;
    /** Width of the courtyard walkway ring that rear entrances open onto. */
    static final int COURTYARD_PATH_WIDTH = 2;
    /** Passage and alley width. */
    static final int PASSAGE_WIDTH = 3;
    /** Rear-entrance clearance: a corner parcel overlaps its courtyard by at least this. */
    static final int REAR_OVERLAP = 3;

    /** Superblock that holds the starter refuge in region (0, 0). */
    static final int STARTER_BLOCK = 5;
    /** Courtyard slots: four strip corners, then the west and east side parcels. */
    static final int SLOT_NORTH_WEST = 0;
    static final int SLOT_NORTH_EAST = 1;
    static final int SLOT_SOUTH_WEST = 2;
    static final int SLOT_SOUTH_EAST = 3;
    static final int SLOT_WEST = 4;
    static final int SLOT_EAST = 5;
    /** First south-row slot of a shop row. */
    static final int SHOP_SOUTH_FIRST_SLOT = 3;

    private CityPlanner() {
    }

    /**
     * True when urban ids can name every parcel of the region. Beyond about
     * 33.5 million blocks from the origin the city is the flat street datum,
     * far past where float positions stay usable.
     */
    public static boolean isPlannable(int rx, int rz) {
        return rx >= UrbanIds.MIN_REGION && rx <= UrbanIds.MAX_REGION
                && rz >= UrbanIds.MIN_REGION && rz <= UrbanIds.MAX_REGION;
    }

    /** The district identity of a region. */
    public static UrbanDistrict districtOf(long seed, int layoutRevision, int rx, int rz) {
        if (rx == 0 && rz == 0) {
            return UrbanDistrict.RESIDENTIAL;
        }
        return UrbanHash.unit(seed, layoutRevision, UrbanSalts.DISTRICT, rx, rz, 0) < COMMERCIAL_SHARE
                ? UrbanDistrict.COMMERCIAL : UrbanDistrict.RESIDENTIAL;
    }

    /**
     * Plans a region.
     *
     * @throws IllegalArgumentException for a layout revision without a street
     *         network, or a region urban ids cannot name
     */
    public static DistrictPlan plan(long seed, int layoutRevision, int rx, int rz) {
        if (layoutRevision != UrbanRevision.LAYOUT_STREET_NETWORK) {
            throw new IllegalArgumentException("layout revision " + layoutRevision
                    + " has no city plan");
        }
        if (!isPlannable(rx, rz)) {
            throw new IllegalArgumentException("region " + rx + "," + rz
                    + " is outside the planned city");
        }
        return new Builder(seed, layoutRevision, rx, rz).build();
    }

    // ---- subdivision rules, shared by the planner and its tests -----------------

    /**
     * Every (north, south) strip depth pair that leaves the side parcels
     * between {@link ParcelPlan#MIN_SIDE} and {@link ParcelPlan#MAX_SIDE} long.
     */
    static List<int[]> ringDepths(int depth) {
        List<int[]> pairs = new ArrayList<>();
        for (int north = RING_MIN_DEPTH; north <= RING_MAX_DEPTH; north++) {
            for (int south = RING_MIN_DEPTH; south <= RING_MAX_DEPTH; south++) {
                int side = depth - north - south;
                if (side >= ParcelPlan.MIN_SIDE && side <= ParcelPlan.MAX_SIDE) {
                    pairs.add(new int[]{north, south});
                }
            }
        }
        return pairs;
    }

    /**
     * Every (west, east) side parcel width pair that leaves a courtyard of at
     * least {@link #MIN_COURTYARD} and room to split each strip into two
     * parcels around its passage.
     */
    static List<int[]> ringSides(int width) {
        List<int[]> pairs = new ArrayList<>();
        for (int west = RING_MIN_DEPTH; west <= RING_MAX_DEPTH; west++) {
            for (int east = RING_MIN_DEPTH; east <= RING_MAX_DEPTH; east++) {
                if (width - west - east >= MIN_COURTYARD && splitLow(width, west) <= splitHigh(width, east)) {
                    pairs.add(new int[]{west, east});
                }
            }
        }
        return pairs;
    }

    /**
     * Smallest width of a strip's west parcel: its rear door needs
     * {@link #REAR_OVERLAP} cells of courtyard beside the west side parcel, and
     * the east parcel may not pass {@link ParcelPlan#MAX_SIDE}.
     */
    static int splitLow(int width, int west) {
        return Math.max(ParcelPlan.MIN_SIDE,
                Math.max(west + REAR_OVERLAP, width - PASSAGE_WIDTH - ParcelPlan.MAX_SIDE));
    }

    /** Largest width of a strip's west parcel, by the same rules for the east parcel. */
    static int splitHigh(int width, int east) {
        return Math.min(ParcelPlan.MAX_SIDE, Math.min(width - PASSAGE_WIDTH - ParcelPlan.MIN_SIDE,
                width - PASSAGE_WIDTH - REAR_OVERLAP - east));
    }

    static boolean courtyardFits(int width, int depth) {
        return !ringDepths(depth).isEmpty() && !ringSides(width).isEmpty();
    }

    static int shopsPerRow(int width) {
        return (width + ParcelPlan.MAX_SIDE - 1) / ParcelPlan.MAX_SIDE;
    }

    static boolean shopRowFits(int width, int depth) {
        int lo = Math.max(ParcelPlan.MIN_SIDE, depth - PASSAGE_WIDTH - ParcelPlan.MAX_SIDE);
        int hi = Math.min(ParcelPlan.MAX_SIDE, depth - PASSAGE_WIDTH - ParcelPlan.MIN_SIDE);
        int perRow = shopsPerRow(width);
        return lo <= hi && perRow * ParcelPlan.MIN_SIDE <= width && perRow <= SHOP_SOUTH_FIRST_SLOT;
    }

    /** A parcel before its use, storeys and entrances are final. */
    private static final class Draft {
        final int superblock;
        final int slot;
        final Rect bounds;
        final Side frontage;
        final Side rear;
        /** Inclusive range along the rear edge where the rear entrance may go. */
        final int rearFrom;
        final int rearTo;
        Use use = Use.RESIDENTIAL;

        Draft(int superblock, int slot, Rect bounds, Side frontage, Side rear, int rearFrom, int rearTo) {
            this.superblock = superblock;
            this.slot = slot;
            this.bounds = bounds;
            this.frontage = frontage;
            this.rear = rear;
            this.rearFrom = rearFrom;
            this.rearTo = rearTo;
        }

        int index() {
            return superblock * ParcelPlan.SLOTS_PER_SUPERBLOCK + slot;
        }
    }

    private record Area(Rect rect, UrbanSurface surface) {
    }

    private static final class Builder {
        final long seed;
        final int rev;
        final int rx;
        final int rz;
        final int ox;
        final int oz;
        final UrbanDistrict district;
        final boolean starter;
        final int[] northSouth = new int[3];
        final int[] eastWest = new int[3];
        final byte[] surface = new byte[StreetNetwork.REGION_SIZE * StreetNetwork.REGION_SIZE];
        final byte[] parcelIndex = new byte[surface.length];
        final List<SuperblockPlan> superblocks = new ArrayList<>();
        final List<Draft> drafts = new ArrayList<>();
        final List<Area> routes = new ArrayList<>();
        final List<Area> openSpaces = new ArrayList<>();
        final List<DistrictPlan.Tree> candidateTrees = new ArrayList<>();
        int conflicts;
        int fallbacks;

        Builder(long seed, int rev, int rx, int rz) {
            this.seed = seed;
            this.rev = rev;
            this.rx = rx;
            this.rz = rz;
            this.ox = StreetNetwork.regionOrigin(rx);
            this.oz = StreetNetwork.regionOrigin(rz);
            this.district = districtOf(seed, rev, rx, rz);
            this.starter = rx == 0 && rz == 0;
            Arrays.fill(parcelIndex, (byte) DistrictPlan.NO_PARCEL);
        }

        DistrictPlan build() {
            for (int k = 1; k <= 3; k++) {
                northSouth[k - 1] = StreetNetwork.localLine(seed, rev, true, rx, k);
                eastWest[k - 1] = StreetNetwork.localLine(seed, rev, false, rz, k);
            }
            stampStreets();
            Kind[] kinds = chooseKinds();
            for (int j = 0; j < StreetNetwork.SUPERBLOCKS_PER_SIDE; j++) {
                for (int i = 0; i < StreetNetwork.SUPERBLOCKS_PER_SIDE; i++) {
                    layoutSuperblock(j * StreetNetwork.SUPERBLOCKS_PER_SIDE + i, i, j, kinds);
                }
            }
            assignUses();
            List<ParcelPlan> parcels = new ArrayList<>();
            ParcelPlan refuge = null;
            for (Draft draft : drafts) {
                ParcelPlan parcel = finish(draft);
                parcels.add(parcel);
                if (parcel.use() == Use.REFUGE) {
                    refuge = parcel;
                }
            }
            // Claim order is the overlap priority: routes, parcels, open space, trees.
            for (Area route : routes) {
                fill(route.rect(), route.surface());
            }
            for (ParcelPlan parcel : parcels) {
                stampParcel(parcel);
            }
            for (Area open : openSpaces) {
                stampOpenSpace(open);
            }
            List<DistrictPlan.Tree> trees = new ArrayList<>();
            for (DistrictPlan.Tree tree : candidateTrees) {
                if (plantBed(tree.x(), tree.z())) {
                    trees.add(tree);
                }
            }
            int[] anchor = null;
            if (refuge != null) {
                Entrance door = refuge.entrances().get(1);
                anchor = new int[]{door.outsideX(), door.outsideZ()};
            }
            return new DistrictPlan(seed, rev, rx, rz, district, northSouth, eastWest, superblocks,
                    parcels, trees, surface, parcelIndex, refuge, anchor, conflicts, fallbacks);
        }

        // ---- streets ------------------------------------------------------

        void stampStreets() {
            int size = StreetNetwork.REGION_SIZE;
            int[] northSouthParts = new int[size];
            int[] eastWestParts = new int[size];
            for (int l = 0; l < size; l++) {
                northSouthParts[l] = StreetNetwork.bandPart(seed, rev, true, ox + l, rz);
                eastWestParts[l] = StreetNetwork.bandPart(seed, rev, false, oz + l, rx);
            }
            for (int lz = 0; lz < size; lz++) {
                for (int lx = 0; lx < size; lx++) {
                    UrbanSurface street = StreetNetwork.combine(northSouthParts[lx], eastWestParts[lz],
                            ox + lx, oz + lz);
                    if (street != null) {
                        surface[lz * size + lx] = street.code;
                    }
                }
            }
        }

        // ---- superblock kinds ----------------------------------------------

        Kind[] chooseKinds() {
            int count = StreetNetwork.SUPERBLOCKS_PER_SIDE * StreetNetwork.SUPERBLOCKS_PER_SIDE;
            Kind[] kinds = new Kind[count];
            List<Integer> free = new ArrayList<>();
            for (int s = 0; s < count; s++) {
                if (!(starter && s == STARTER_BLOCK)) {
                    free.add(s);
                }
            }
            if (district == UrbanDistrict.COMMERCIAL) {
                Arrays.fill(kinds, Kind.SHOP_ROW);
                for (int n = 0; n < COMMERCIAL_HOUSING_BLOCKS; n++) {
                    kinds[take(free, UrbanSalts.MIXED_HOUSING, n)] = Kind.COURTYARD_HOUSING;
                }
                if (roll(UrbanSalts.OPEN_SPACE_CHANCE, 0) < COMMERCIAL_PLAZA_CHANCE) {
                    kinds[take(free, UrbanSalts.OPEN_SPACE, 0)] = Kind.PLAZA;
                }
            } else {
                Arrays.fill(kinds, Kind.COURTYARD_HOUSING);
                if (roll(UrbanSalts.OPEN_SPACE_CHANCE, 0) < RESIDENTIAL_PARK_CHANCE) {
                    kinds[take(free, UrbanSalts.OPEN_SPACE, 0)] = Kind.PARK;
                }
                if (roll(UrbanSalts.SHOP_ROW, 1) < RESIDENTIAL_SHOP_ROW_CHANCE) {
                    kinds[take(free, UrbanSalts.SHOP_ROW, 0)] = Kind.SHOP_ROW;
                }
            }
            if (starter) {
                kinds[STARTER_BLOCK] = Kind.COURTYARD_HOUSING;
            }
            return kinds;
        }

        /** Removes and returns one deterministic pick from the remaining superblocks. */
        int take(List<Integer> free, long salt, int attempt) {
            int pick = UrbanHash.range(0, free.size() - 1, seed, rev, salt, rx, rz, attempt);
            return free.remove(pick);
        }

        double roll(long salt, long key) {
            return UrbanHash.unit(seed, rev, salt, rx, rz, key);
        }

        int pick(int lo, int hi, long salt, long key) {
            return UrbanHash.range(lo, hi, seed, rev, salt, rx, rz, key);
        }

        // ---- superblock layouts --------------------------------------------

        void layoutSuperblock(int index, int i, int j, Kind[] kinds) {
            int x0 = i == 0 ? ox + StreetNetwork.AVENUE_HALF_WIDTH
                    : northSouth[i - 1] + StreetNetwork.LOCAL_HALF_WIDTH;
            int x1 = i == 3 ? ox + StreetNetwork.REGION_SIZE - StreetNetwork.AVENUE_HALF_WIDTH - 1
                    : northSouth[i] - StreetNetwork.LOCAL_HALF_WIDTH - 1;
            int z0 = j == 0 ? oz + StreetNetwork.AVENUE_HALF_WIDTH
                    : eastWest[j - 1] + StreetNetwork.LOCAL_HALF_WIDTH;
            int z1 = j == 3 ? oz + StreetNetwork.REGION_SIZE - StreetNetwork.AVENUE_HALF_WIDTH - 1
                    : eastWest[j] - StreetNetwork.LOCAL_HALF_WIDTH - 1;
            Rect bounds = new Rect(x0, z0, x1, z1);
            Kind kind = kinds[index];
            Rect open = switch (kind) {
                case COURTYARD_HOUSING -> layoutCourtyard(index, bounds);
                case SHOP_ROW -> layoutShopRow(index, bounds);
                case PARK, PLAZA -> null;
            };
            if (open == null && (kind == Kind.COURTYARD_HOUSING || kind == Kind.SHOP_ROW)) {
                // Finite fallback (rule A10): no subdivision fitted, so the block stays open.
                fallbacks++;
                kind = Kind.PARK;
            }
            if (kind == Kind.PARK) {
                open = bounds;
                layoutPark(bounds);
            } else if (kind == Kind.PLAZA) {
                open = bounds;
                layoutPlaza(bounds);
            }
            superblocks.add(new SuperblockPlan(index, i, j, kind, bounds, open));
        }

        /**
         * A ring of six parcels around a courtyard: two north strip parcels and
         * two south strip parcels split by a passage each, and one parcel on
         * each side. Returns the courtyard, or null when nothing fits.
         */
        Rect layoutCourtyard(int index, Rect b) {
            int w = b.x1() - b.x0() + 1;
            int d = b.z1() - b.z0() + 1;
            List<int[]> depths = ringDepths(d);
            List<int[]> sides = ringSides(w);
            if (depths.isEmpty() || sides.isEmpty()) {
                return null;
            }
            int[] depth = depths.get(pick(0, depths.size() - 1, UrbanSalts.RING_DEPTHS, index * 2L));
            int[] side = sides.get(pick(0, sides.size() - 1, UrbanSalts.RING_DEPTHS, index * 2L + 1));
            int dn = depth[0];
            int ds = depth[1];
            int dw = side[0];
            int de = side[1];
            int lo = splitLow(w, dw);
            int hi = splitHigh(w, de);
            int northSplit = pick(lo, hi, UrbanSalts.STRIP_SPLIT, index * 2L);
            int southSplit = pick(lo, hi, UrbanSalts.STRIP_SPLIT, index * 2L + 1);

            Rect court = new Rect(b.x0() + dw, b.z0() + dn, b.x1() - de, b.z1() - ds);
            int northZ1 = b.z0() + dn - 1;
            int southZ0 = b.z1() - ds + 1;
            // North strip: parcel, passage, parcel; rear entrances open onto the courtyard.
            drafts.add(new Draft(index, SLOT_NORTH_WEST,
                    new Rect(b.x0(), b.z0(), b.x0() + northSplit - 1, northZ1), Side.NORTH, Side.SOUTH,
                    court.x0(), b.x0() + northSplit - 2));
            routes.add(new Area(new Rect(b.x0() + northSplit, b.z0(),
                    b.x0() + northSplit + PASSAGE_WIDTH - 1, northZ1), UrbanSurface.PASSAGE));
            drafts.add(new Draft(index, SLOT_NORTH_EAST,
                    new Rect(b.x0() + northSplit + PASSAGE_WIDTH, b.z0(), b.x1(), northZ1),
                    Side.NORTH, Side.SOUTH, b.x0() + northSplit + PASSAGE_WIDTH + 1, court.x1()));
            // South strip, mirrored.
            drafts.add(new Draft(index, SLOT_SOUTH_WEST,
                    new Rect(b.x0(), southZ0, b.x0() + southSplit - 1, b.z1()), Side.SOUTH, Side.NORTH,
                    court.x0(), b.x0() + southSplit - 2));
            routes.add(new Area(new Rect(b.x0() + southSplit, southZ0,
                    b.x0() + southSplit + PASSAGE_WIDTH - 1, b.z1()), UrbanSurface.PASSAGE));
            drafts.add(new Draft(index, SLOT_SOUTH_EAST,
                    new Rect(b.x0() + southSplit + PASSAGE_WIDTH, southZ0, b.x1(), b.z1()),
                    Side.SOUTH, Side.NORTH, b.x0() + southSplit + PASSAGE_WIDTH + 1, court.x1()));
            // Side parcels front the west and east streets and back onto the courtyard.
            drafts.add(new Draft(index, SLOT_WEST,
                    new Rect(b.x0(), court.z0(), court.x0() - 1, court.z1()), Side.WEST, Side.EAST,
                    court.z0() + 1, court.z1() - 1));
            drafts.add(new Draft(index, SLOT_EAST,
                    new Rect(court.x1() + 1, court.z0(), b.x1(), court.z1()), Side.EAST, Side.WEST,
                    court.z0() + 1, court.z1() - 1));
            openSpaces.add(new Area(court, UrbanSurface.COURTYARD));
            courtyardTrees(court);
            return court;
        }

        /** Two rows of shop parcels back to back along a through alley. Returns the alley. */
        Rect layoutShopRow(int index, Rect b) {
            int w = b.x1() - b.x0() + 1;
            int d = b.z1() - b.z0() + 1;
            if (!shopRowFits(w, d)) {
                return null;
            }
            int lo = Math.max(ParcelPlan.MIN_SIDE, d - PASSAGE_WIDTH - ParcelPlan.MAX_SIDE);
            int hi = Math.min(ParcelPlan.MAX_SIDE, d - PASSAGE_WIDTH - ParcelPlan.MIN_SIDE);
            int perRow = shopsPerRow(w);
            int centre = (d - PASSAGE_WIDTH) / 2 + pick(-2, 2, UrbanSalts.ALLEY_OFFSET, index);
            int north = Math.max(lo, Math.min(hi, centre));
            Rect alley = new Rect(b.x0(), b.z0() + north, b.x1(), b.z0() + north + PASSAGE_WIDTH - 1);
            routes.add(new Area(alley, UrbanSurface.ALLEY));
            shopRowParcels(index, b, w, perRow, b.z0(), alley.z0() - 1, Side.NORTH, 0);
            shopRowParcels(index, b, w, perRow, alley.z1() + 1, b.z1(), Side.SOUTH,
                    SHOP_SOUTH_FIRST_SLOT);
            return alley;
        }

        void shopRowParcels(int index, Rect b, int w, int count, int z0, int z1, Side frontage,
                            int firstSlot) {
            int start = b.x0();
            for (int k = 0; k < count; k++) {
                int end;
                if (k == count - 1) {
                    end = b.x1();
                } else {
                    int remaining = count - k - 1;
                    int nominal = b.x0() + Math.round((k + 1) * (float) w / count)
                            + pick(-3, 3, UrbanSalts.PARCEL_WIDTHS, index * 8L + firstSlot + k);
                    int low = Math.max(start + ParcelPlan.MIN_SIDE,
                            b.x1() + 1 - ParcelPlan.MAX_SIDE * remaining);
                    int high = Math.min(start + ParcelPlan.MAX_SIDE,
                            b.x1() + 1 - ParcelPlan.MIN_SIDE * remaining);
                    end = Math.max(low, Math.min(high, nominal)) - 1;
                }
                Side rear = frontage.opposite();
                drafts.add(new Draft(index, firstSlot + k, new Rect(start, z0, end, z1), frontage, rear,
                        start + 1, end - 1));
                start = end + 1;
            }
        }

        /** Lawn with a cross of paths joining all four streets and a small central square. */
        void layoutPark(Rect b) {
            openSpaces.add(new Area(b, UrbanSurface.PARK));
            int cx = (b.x0() + b.x1() + 1) / 2;
            int cz = (b.z0() + b.z1() + 1) / 2;
            for (int x = b.x0() + 4; x <= b.x1() - 4; x += 6) {
                for (int z = b.z0() + 4; z <= b.z1() - 4; z += 6) {
                    boolean clearOfPaths = (x <= cx - 4 || x >= cx + 3) && (z <= cz - 4 || z >= cz + 3);
                    if (clearOfPaths && treeRoll(x, z) < 0.55) {
                        candidateTrees.add(tree(x, z));
                    }
                }
            }
        }

        void layoutPlaza(Rect b) {
            openSpaces.add(new Area(b, UrbanSurface.PLAZA));
            for (int x = b.x0() + 5; x <= b.x1() - 5; x += 7) {
                for (int z = b.z0() + 5; z <= b.z1() - 5; z += 7) {
                    if (treeRoll(x, z) < 0.6) {
                        candidateTrees.add(tree(x, z));
                    }
                }
            }
        }

        void courtyardTrees(Rect court) {
            int ix0 = court.x0() + COURTYARD_PATH_WIDTH;
            int iz0 = court.z0() + COURTYARD_PATH_WIDTH;
            int ix1 = court.x1() - COURTYARD_PATH_WIDTH;
            int iz1 = court.z1() - COURTYARD_PATH_WIDTH;
            int planted = 0;
            for (int x = ix0 + 1; x <= ix1 - 1 && planted < 4; x += 5) {
                for (int z = iz0 + 1; z <= iz1 - 1 && planted < 4; z += 5) {
                    if (treeRoll(x, z) < 0.5) {
                        candidateTrees.add(tree(x, z));
                        planted++;
                    }
                }
            }
        }

        double treeRoll(int x, int z) {
            return UrbanHash.unit(seed, rev, UrbanSalts.TREE, x, z, 0);
        }

        DistrictPlan.Tree tree(int x, int z) {
            return new DistrictPlan.Tree(x, z,
                    UrbanHash.range(5, 6, seed, rev, UrbanSalts.TREE_HEIGHT, x, z, 0));
        }

        // ---- uses, storeys and entrances -------------------------------------

        void assignUses() {
            double cornerShare = district == UrbanDistrict.COMMERCIAL
                    ? COMMERCIAL_CORNER_SHOP_SHARE : RESIDENTIAL_CORNER_SHOP_SHARE;
            List<Draft> serviceCandidates = new ArrayList<>();
            for (Draft draft : drafts) {
                Kind kind = superblockKind(draft.superblock);
                if (kind == Kind.SHOP_ROW) {
                    draft.use = Use.COMMERCIAL;
                } else if (draft.slot <= SLOT_SOUTH_EAST
                        && roll(UrbanSalts.CORNER_SHOP, draft.index()) < cornerShare) {
                    draft.use = Use.COMMERCIAL;
                }
                if (starter && draft.superblock == STARTER_BLOCK) {
                    // The refuge opens onto a courtyard with a corner shop on the same block.
                    if (draft.slot == SLOT_WEST) {
                        draft.use = Use.REFUGE;
                    } else if (draft.slot == SLOT_NORTH_EAST) {
                        draft.use = Use.COMMERCIAL;
                    }
                    continue;
                }
                serviceCandidates.add(draft);
            }
            if (!serviceCandidates.isEmpty()) {
                serviceCandidates.get(pick(0, serviceCandidates.size() - 1,
                        UrbanSalts.SERVICE_PARCEL, 0)).use = Use.SERVICE;
            }
        }

        Kind superblockKind(int index) {
            for (SuperblockPlan block : superblocks) {
                if (block.index() == index) {
                    return block.kind();
                }
            }
            throw new IllegalStateException("superblock " + index + " not planned");
        }

        ParcelPlan finish(Draft draft) {
            Rect b = draft.bounds;
            boolean alongX = draft.frontage == Side.NORTH || draft.frontage == Side.SOUTH;
            int frontLength = alongX ? b.x1() - b.x0() + 1 : b.z1() - b.z0() + 1;
            int u = (frontLength - ParcelPlan.ENTRANCE_WIDTH) / 2;
            int[] door = local(b, draft.frontage, u, 0);
            Entrance primary = new Entrance(door[0], door[1], draft.frontage, true);
            int[] coreA = local(b, draft.frontage, u + ParcelPlan.ENTRANCE_WIDTH, 1);
            int[] coreB = local(b, draft.frontage,
                    u + ParcelPlan.ENTRANCE_WIDTH + ParcelPlan.CORE_WIDTH - 1, ParcelPlan.CORE_DEPTH);
            Rect core = new Rect(Math.min(coreA[0], coreB[0]), Math.min(coreA[1], coreB[1]),
                    Math.max(coreA[0], coreB[0]), Math.max(coreA[1], coreB[1]));
            int along = draft.rearFrom
                    + (draft.rearTo - draft.rearFrom + 1 - ParcelPlan.ENTRANCE_WIDTH) / 2;
            Entrance rear = switch (draft.rear) {
                case NORTH -> new Entrance(along, b.z0(), Side.NORTH, false);
                case SOUTH -> new Entrance(along, b.z1(), Side.SOUTH, false);
                case WEST -> new Entrance(b.x0(), along, Side.WEST, false);
                case EAST -> new Entrance(b.x1(), along, Side.EAST, false);
            };
            long id = UrbanIds.parcel(UrbanIds.district(rx, rz), draft.index());
            return new ParcelPlan(draft.index(), id, draft.superblock, draft.use,
                    b.x0(), b.z0(), b.x1(), b.z1(), draft.frontage, List.of(primary, rear), core,
                    storeys(draft));
        }

        /** World cell at {@code u} along the frontage and {@code v} deep into the parcel. */
        static int[] local(Rect b, Side frontage, int u, int v) {
            return switch (frontage) {
                case NORTH -> new int[]{b.x0() + u, b.z0() + v};
                case SOUTH -> new int[]{b.x0() + u, b.z1() - v};
                case WEST -> new int[]{b.x0() + v, b.z0() + u};
                case EAST -> new int[]{b.x1() - v, b.z0() + u};
            };
        }

        int storeys(Draft draft) {
            long key = draft.index();
            boolean downtown = district == UrbanDistrict.COMMERCIAL;
            return switch (draft.use) {
                case RESIDENTIAL -> pick(downtown ? 3 : 2, 5, UrbanSalts.STOREYS, key);
                case COMMERCIAL -> downtown ? pick(3, 8, UrbanSalts.STOREYS, key)
                        : pick(1, 3, UrbanSalts.STOREYS, key);
                case SERVICE -> pick(1, 2, UrbanSalts.STOREYS, key);
                case REFUGE -> pick(2, 3, UrbanSalts.STOREYS, key);
            };
        }

        // ---- rasterisation ---------------------------------------------------

        int cell(int x, int z) {
            return (z - oz) * StreetNetwork.REGION_SIZE + (x - ox);
        }

        void claim(int x, int z, UrbanSurface kind, int parcel) {
            int c = cell(x, z);
            if (surface[c] != UrbanSurface.VOID.code) {
                conflicts++;
                return;
            }
            surface[c] = kind.code;
            parcelIndex[c] = (byte) parcel;
        }

        void fill(Rect r, UrbanSurface kind) {
            for (int z = r.z0(); z <= r.z1(); z++) {
                for (int x = r.x0(); x <= r.x1(); x++) {
                    claim(x, z, kind, DistrictPlan.NO_PARCEL);
                }
            }
        }

        void stampParcel(ParcelPlan parcel) {
            for (int z = parcel.z0(); z <= parcel.z1(); z++) {
                for (int x = parcel.x0(); x <= parcel.x1(); x++) {
                    claim(x, z, parcel.onOutline(x, z) ? UrbanSurface.LOT_EDGE : UrbanSurface.LOT,
                            parcel.index());
                }
            }
            for (Entrance door : parcel.entrances()) {
                refine(door.x(), door.z(), UrbanSurface.ENTRANCE, parcel.index());
                refine(door.x2(), door.z2(), UrbanSurface.ENTRANCE, parcel.index());
            }
            Rect core = parcel.core();
            for (int z = core.z0(); z <= core.z1(); z++) {
                for (int x = core.x0(); x <= core.x1(); x++) {
                    refine(x, z, UrbanSurface.STAIR_CORE, parcel.index());
                }
            }
        }

        /** Marks a reservation inside a parcel's own lot; anything else is a conflict. */
        void refine(int x, int z, UrbanSurface kind, int parcel) {
            int c = cell(x, z);
            byte current = surface[c];
            boolean own = parcelIndex[c] == (byte) parcel;
            boolean lot = current == UrbanSurface.LOT.code || current == UrbanSurface.LOT_EDGE.code;
            if (!own || !lot) {
                conflicts++;
                return;
            }
            surface[c] = kind.code;
        }

        void stampOpenSpace(Area open) {
            Rect r = open.rect();
            if (open.surface() == UrbanSurface.COURTYARD) {
                for (int z = r.z0(); z <= r.z1(); z++) {
                    for (int x = r.x0(); x <= r.x1(); x++) {
                        boolean ring = x - r.x0() < COURTYARD_PATH_WIDTH || r.x1() - x < COURTYARD_PATH_WIDTH
                                || z - r.z0() < COURTYARD_PATH_WIDTH || r.z1() - z < COURTYARD_PATH_WIDTH;
                        claim(x, z, ring ? UrbanSurface.COURTYARD_PATH : UrbanSurface.COURTYARD,
                                DistrictPlan.NO_PARCEL);
                    }
                }
            } else if (open.surface() == UrbanSurface.PARK) {
                int cx = (r.x0() + r.x1() + 1) / 2;
                int cz = (r.z0() + r.z1() + 1) / 2;
                for (int z = r.z0(); z <= r.z1(); z++) {
                    for (int x = r.x0(); x <= r.x1(); x++) {
                        boolean path = x == cx - 1 || x == cx || z == cz - 1 || z == cz
                                || (Math.abs(x - cx + 0.5) < 3 && Math.abs(z - cz + 0.5) < 3);
                        claim(x, z, path ? UrbanSurface.PARK_PATH : UrbanSurface.PARK,
                                DistrictPlan.NO_PARCEL);
                    }
                }
            } else {
                fill(r, open.surface());
            }
        }

        boolean plantBed(int x, int z) {
            int c = cell(x, z);
            byte current = surface[c];
            if (current != UrbanSurface.COURTYARD.code && current != UrbanSurface.PARK.code
                    && current != UrbanSurface.PLAZA.code) {
                conflicts++;
                return false;
            }
            surface[c] = UrbanSurface.TREE_BED.code;
            return true;
        }
    }
}
