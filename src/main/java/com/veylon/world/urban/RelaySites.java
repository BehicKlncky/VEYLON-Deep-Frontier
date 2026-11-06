package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.urban.BuildingPlan.Family;
import com.veylon.world.urban.BuildingPlan.Fixture;

import java.util.ArrayList;
import java.util.List;

/**
 * Layout sixteen's relay (urban milestone 20): the old radio relay mast that the communications
 * objective repairs. There is one in a world, planned before anything is met from the seed and the
 * frozen plans alone, so chunk order, cache eviction and the order the city is explored never move
 * it:
 *
 * <ul>
 *   <li><b>Where.</b> The civic region nearest the spawn (by ring, then distance, then x, then z;
 *       rings two and three always hold one, D-108), on the roof of its tallest unheld office block
 *       outside every compound; failing that a school, a security post or a clinic, in that order,
 *       ties broken by a salted hash. If that region offers none the next civic region is tried,
 *       out to the third ring.</li>
 *   <li><b>The cell.</b> An open roof cell whose eight neighbours are open roof too, at least two
 *       cells inside the parapet and clear of every roof fixture and route head, nearest the cell
 *       outside the stair bulkhead's door, so the mast stands in plain sight of anyone coming up and
 *       takes no step of any walking route away.</li>
 *   <li><b>What stands there.</b> A {@link BlockType#RELAY} cabinet on the roof with a mast of
 *       {@value #MAST} guard-rail cells above it, stamped after everything else by
 *       {@link #stamp}, which writes only inside the chunk being generated.</li>
 * </ul>
 *
 * <p>The cabinet cannot be broken or blasted; what has been repaired in it is the missions
 * ledger's to keep ({@code UrbanMissions}), never a block state.
 */
public final class RelaySites {
    /** Guard-rail cells of the mast over the cabinet. */
    public static final int MAST = 3;
    /** Families that may carry the relay, most wanted first. */
    static final List<Family> FAMILIES = List.of(Family.OFFICE, Family.SCHOOL, Family.SECURITY, Family.CLINIC);
    private static final int LAYOUT = UrbanRevision.LAYOUT_MISSIONS;

    /**
     * The relay.
     *
     * @param building the building whose roof carries it
     * @param rx       its region
     * @param rz       its region
     * @param relay    the cabinet cell, on the roof's feet level
     * @param stand    the open roof cell beside it a worker stands on, the nearest to the stair door
     * @param mast     the guard-rail cells above the cabinet, bottom first
     */
    public record Site(long building, int rx, int rz, Vec3i relay, Vec3i stand, List<Vec3i> mast) {
        public Site {
            mast = List.copyOf(mast);
        }

        /** Whether a cell is the cabinet or its mast. */
        public boolean holds(int x, int y, int z) {
            if (x != relay.x() || z != relay.z()) { return false; }
            return y >= relay.y() && y <= relay.y() + MAST;
        }
    }

    private RelaySites() {
    }

    /**
     * The civic regions a relay may stand in, nearest the spawn first: rings one to three, by ring,
     * then squared distance, then x, then z.
     */
    static List<int[]> civicRegions(long seed) {
        List<int[]> out = new ArrayList<>();
        int ring = DistrictMap.ALL_DISTRICTS_RING;
        for (int rz = -ring; rz <= ring; rz++) {
            for (int rx = -ring; rx <= ring; rx++) {
                if ((rx != 0 || rz != 0) && DistrictMap.districtOf(seed, rx, rz) == UrbanDistrict.CIVIC) {
                    out.add(new int[]{rx, rz});
                }
            }
        }
        out.sort((a, b) -> {
            int c = Integer.compare(DistrictMap.ring(a[0], a[1]), DistrictMap.ring(b[0], b[1]));
            if (c != 0) { return c; }
            c = Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]);
            if (c != 0) { return c; }
            c = Integer.compare(a[0], b[0]);
            return c != 0 ? c : Integer.compare(a[1], b[1]);
        });
        return out;
    }

    /**
     * Plans the world's relay, or null when no civic region out to the third ring offers a roof
     * for it (no tested seed lacks one).
     */
    public static Site plan(long seed, UrbanWorldState state) {
        if (!state.hasMissions()) { return null; }
        for (int[] region : civicRegions(seed)) {
            Site site = inRegion(seed, state, region[0], region[1]);
            if (site != null) { return site; }
        }
        return null;
    }

    private static Site inRegion(long seed, UrbanWorldState state, int rx, int rz) {
        if (!CityPlanner.isPlannable(rx, rz)) { return null; }
        DistrictPlan district = state.regionPlan(rx, rz);
        CompoundClaims claims = state.routes(rx, rz).claims();
        record Candidate(BuildingPlan plan, int rank, long key) { }
        List<Candidate> candidates = new ArrayList<>();
        for (ParcelPlan parcel : district.parcels()) {
            if (claims.claims(parcel.index())) { continue; }
            Family wanted = DistrictCatalogue.family(seed, district, parcel);
            if (!FAMILIES.contains(wanted)) { continue; }
            BuildingPlan b = state.buildingAt(parcel.x0(), parcel.z0());
            if (b == null || !FAMILIES.contains(b.family()) || b.variant() == BuildingPlan.Variant.OCCUPIED
                    || b.storeys() < 1 || roofDoor(b) == null) {
                continue;
            }
            // Most wanted family first, then the tallest, then the lowest salted hash.
            candidates.add(new Candidate(b, FAMILIES.indexOf(b.family()) * 100 - b.storeys(),
                    UrbanHash.mix(seed, LAYOUT, UrbanSalts.RELAY_SITE, b.id(), 0, 0)));
        }
        candidates.sort((a, b) -> a.rank() != b.rank() ? Integer.compare(a.rank(), b.rank())
                : Long.compareUnsigned(a.key(), b.key()));
        // The best building whose roof leaves room for a mast.
        for (Candidate c : candidates) {
            Site site = siteOn(c.plan(), rx, rz);
            if (site != null) { return site; }
        }
        return null;
    }

    /** The stair bulkhead's roof door of a template-seven building, or null. */
    static Fixture roofDoor(BuildingPlan b) {
        long roofRoom = UrbanIds.room(UrbanIds.floor(b.id(), b.storeys()), 0);
        for (Fixture f : b.fixtures()) {
            if (f.kind() == BuildingPlan.FixtureKind.DOOR && UrbanIds.parent(f.id()) == roofRoom) { return f; }
        }
        return null;
    }

    /** The relay on one building's roof, or null when no roof cell leaves room for it. */
    static Site siteOn(BuildingPlan b, int rx, int rz) {
        Fixture door = roofDoor(b);
        PlanVolume v = b.voxels();
        int roof = b.roofY(), feet = roof + 1;
        // The roof cell outside the door: the opposite side from the cell the door's target stands on.
        Vec3i d = door.position(), t = door.target();
        Vec3i outside = new Vec3i(2 * d.x() - t.x(), feet, 2 * d.z() - t.z());
        ParcelPlan p = b.parcel();
        Vec3i bestCell = null;
        long bestD = Long.MAX_VALUE;
        for (int z = p.z0() + 2; z <= p.z1() - 2; z++) {
            for (int x = p.x0() + 2; x <= p.x1() - 2; x++) {
                if (!open(v, x, roof, z, MAST + 1)) { continue; }
                boolean clear = true;
                for (int dz = -1; dz <= 1 && clear; dz++) {
                    for (int dx = -1; dx <= 1 && clear; dx++) {
                        clear = open(v, x + dx, roof, z + dz, 2);
                    }
                }
                if (!clear || nearFixture(b, x, feet, z) || Math.abs(x - outside.x()) + Math.abs(z - outside.z()) < 2) {
                    continue;
                }
                long dist = (long) (x - outside.x()) * (x - outside.x()) + (long) (z - outside.z()) * (z - outside.z());
                if (dist < bestD) {
                    bestD = dist;
                    bestCell = new Vec3i(x, feet, z);
                }
            }
        }
        if (bestCell == null) { return null; }
        Vec3i stand = null;
        long standD = Long.MAX_VALUE;
        for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            Vec3i c = bestCell.offset(o[0], 0, o[1]);
            long dist = (long) (c.x() - outside.x()) * (c.x() - outside.x()) + (long) (c.z() - outside.z()) * (c.z() - outside.z());
            if (dist < standD) {
                standD = dist;
                stand = c;
            }
        }
        List<Vec3i> mast = new ArrayList<>(MAST);
        for (int h = 1; h <= MAST; h++) { mast.add(bestCell.offset(0, h, 0)); }
        return new Site(b.id(), rx, rz, bestCell, stand, mast);
    }

    /** A roof cell with the slab under it and {@code clear} open cells above. */
    private static boolean open(PlanVolume v, int x, int roof, int z, int clear) {
        if (!v.isColumnLoaded(x, z) || v.getBlock(x, roof, z) != BlockType.CONCRETE) { return false; }
        for (int h = 1; h <= clear; h++) {
            if (v.getBlock(x, roof + h, z) != BlockType.AIR || roof + h > v.maxY) { return false; }
        }
        return true;
    }

    /** True within two cells of any fixture of the roof storey (a door, locker or route head) or its target. */
    private static boolean nearFixture(BuildingPlan b, int x, int feet, int z) {
        for (Fixture f : b.fixtures()) {
            for (Vec3i c : new Vec3i[]{f.position(), f.target()}) {
                if (c != null && Math.abs(c.y() - feet) <= 2 && Math.abs(c.x() - x) <= 2 && Math.abs(c.z() - z) <= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Stamps the relay's cells that lie in the chunk being generated, after every other layer. */
    public static void stamp(Chunk chunk, Site site) {
        if (site == null) { return; }
        put(chunk, site.relay(), BlockType.RELAY);
        for (Vec3i m : site.mast()) { put(chunk, m, BlockType.RAILING); }
    }

    private static void put(Chunk chunk, Vec3i cell, BlockType block) {
        int lx = cell.x() - chunk.cx * Chunk.SX, lz = cell.z() - chunk.cz * Chunk.SZ;
        if (lx >= 0 && lx < Chunk.SX && lz >= 0 && lz < Chunk.SZ && cell.y() >= 0 && cell.y() < Chunk.SY) {
            chunk.set(lx, cell.y(), lz, block);
        }
    }
}
