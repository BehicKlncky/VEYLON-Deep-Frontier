package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.urban.BuildingPlan.Family;
import com.veylon.world.urban.BuildingPlan.Variant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The routes between one region's buildings under building template seven (milestone 14),
 * as a pure function of the seed and the region's district plan.
 *
 * <p>A route that joins two buildings has to be agreed by both before either is planned, so
 * this catalogue works from what every plan of a parcel shares whatever its variant or repair:
 * the family, storeys and basement the district catalogue gives it, and the skeleton every
 * family is built on ({@link BuildingTemplates.Builder}). In that skeleton the cross hall
 * (rooms 1 and 2, skeleton rows 7 and 8) reaches both side walls on every floor and is never
 * furnished; the rear wall is solid brick on every storey but for the rear entrance; the
 * stair bulkhead on the roof covers skeleton columns {@code core - 1 .. core + 4}, rows 0 to 7.
 * Buildings then declare what they actually opened ({@link BuildingPlan.FixtureKind#ROUTE}
 * and service doors), and the route stamper builds only the outside parts both ends declare.
 *
 * <ul>
 *   <li><b>Service passages:</b> a corridor two cells wide at the first basement level under
 *       the courtyard passage between a strip's two buildings, from one cross hall's service
 *       door to the other's, when both have basements.</li>
 *   <li><b>Hidden cellar:</b> one apartment basement in every residential region seals its
 *       back room behind a loose panel (the reservation package 16 will fill).</li>
 *   <li><b>Fire escapes:</b> a ladder up a rear wall from the courtyard path or alley to a gap
 *       in the parapet, where the cell behind the wall is open public ground that touches no
 *       other parcel, entrance or tree.</li>
 *   <li><b>Connectors:</b> an opening through the parapets of two abutting roofs of the same
 *       height, and a plank bridge over a courtyard passage between two such roofs.</li>
 * </ul>
 * Nothing here touches the starter superblock, which keeps generator 10's buildings.
 */
public final class RouteCatalogue {
    static final double CORRIDOR = 0.6, FIRE_ESCAPE = 0.3, CONNECTOR = 0.45, BRIDGE = 0.5;
    /** Skeleton rows of the cross hall and the first row past the roof bulkhead. */
    static final int HALL_ROW = 7, PAST_BULKHEAD = 9;
    /** Feet height of the first basement (its slab is y 36). */
    public static final int SERVICE_Y = 37;

    /** What every plan of a parcel shares, known before any is planned. */
    public record Expect(ParcelPlan parcel, Family family, int storeys, boolean basement, Variant variant,
                         int width, int depth) {
        public int roofY() { return UrbanDatum.slabY(storeys); }
        public int entry() { return (width - 2) / 2; }
        public int core() { return entry() + 2; }
        BuildingFurnishing.Layout layout() { return new BuildingFurnishing.Layout(parcel); }
        int u(int x, int z) { return layout().u(x, z); }
        int v(int x, int z) { return layout().v(x, z); }
        Vec3i pos(int u, int y, int v) { return layout().pos(u, y, v); }
        /** Skeleton columns and rows the roof bulkhead covers. */
        boolean bulkhead(int x, int z) {
            int u = u(x, z), v = v(x, z);
            return u >= core() - 1 && u <= core() + 4 && v <= 7;
        }
    }

    /** Two leaves of a service door in a basement side wall, lower cells at {@link #SERVICE_Y}. */
    public record ServiceDoor(int parcel, int room, Vec3i first, Vec3i second, Vec3i firstInside, Vec3i secondInside) { }
    /** A first-basement corridor: open cells y {@value #SERVICE_Y}..39 over the inclusive rectangle. */
    public record Corridor(ServiceDoor west, ServiceDoor east, int x0, int z0, int x1, int z1) { }
    /** Two neighbouring parapet cells, one above the roof slab, opened for a connector or a bridge. */
    public record Opening(int parcel, Vec3i first, Vec3i second) { }
    /** A gap in a rear parapet with the ladder column outside it, from the street datum to the roof. */
    public record FireEscape(int parcel, Vec3i gap, Vec3i ladderTop) { }
    /** Roofs joined over a shared wall line, or by a bridge whose deck spans the cells between. */
    public record Connector(Opening a, Opening b, boolean bridge, int x0, int z0, int x1, int z1, int deckY) { }

    /** Everything one building opens, for the template-seven overlay. */
    public record Features(List<ServiceDoor> serviceDoors, boolean hiddenCellar, List<FireEscape> fireEscapes,
                           List<Opening> openings) {
        public Features {
            serviceDoors = List.copyOf(serviceDoors); fireEscapes = List.copyOf(fireEscapes);
            openings = List.copyOf(openings);
        }
        public boolean roofRoute() { return !fireEscapes.isEmpty() || !openings.isEmpty(); }
        public boolean none() { return serviceDoors.isEmpty() && !hiddenCellar && !roofRoute(); }
    }

    private static final Features NONE = new Features(List.of(), false, List.of(), List.of());

    private final DistrictPlan district;
    private final Map<Integer, Expect> expect = new HashMap<>();
    private final List<Corridor> corridors = new ArrayList<>();
    private final List<FireEscape> fireEscapes = new ArrayList<>();
    private final List<Connector> connectors = new ArrayList<>();
    private final int hiddenCellar;

    private RouteCatalogue(long seed, DistrictPlan district) {
        this.district = district;
        for (ParcelPlan parcel : district.parcels()) {
            if (!UrbanStarterSite.isNeighbour(parcel) && parcel.use() != ParcelPlan.Use.REFUGE) {
                expect.put(parcel.index(), expect(seed, district, parcel));
            }
        }
        for (SuperblockPlan block : district.superblocks()) {
            if (block.kind() == SuperblockPlan.Kind.COURTYARD_HOUSING) {
                pair(seed, block.index(), CityPlanner.SLOT_NORTH_WEST, CityPlanner.SLOT_NORTH_EAST, true);
                pair(seed, block.index(), CityPlanner.SLOT_SOUTH_WEST, CityPlanner.SLOT_SOUTH_EAST, false);
            }
        }
        abutting(seed);
        for (Expect e : expect.values()) { fireEscape(seed, e); }
        fireEscapes.sort(java.util.Comparator.comparingInt(FireEscape::parcel));
        hiddenCellar = chooseCellar(seed);
    }

    /** The catalogue of a six-district region. Pure; callers cache it with the region plan. */
    public static RouteCatalogue of(long seed, DistrictPlan district) {
        return new RouteCatalogue(seed, district);
    }

    /** The template-seven facts of a non-starter parcel: family, storeys, basement and variant. */
    public static Expect expect(long seed, DistrictPlan district, ParcelPlan parcel) {
        Family family = DistrictCatalogue.family(seed, district, parcel);
        int storeys = family.walkUp() ? Math.min(family == Family.SHOP ? 2 : BuildingTemplates.MAX_STOREYS,
                parcel.maxStoreys()) : CivicTemplates.envelope(family).storeys(parcel);
        boolean basement = family == Family.APARTMENT || !family.walkUp() && CivicCatalogue.basement(seed, family, parcel);
        boolean alongX = parcel.frontage().dx == 0;
        return new Expect(parcel, family, storeys, basement, DistrictCatalogue.expectedVariant(seed, district, parcel),
                alongX ? parcel.width() : parcel.depth(), alongX ? parcel.depth() : parcel.width());
    }

    public List<Corridor> corridors() { return List.copyOf(corridors); }
    public List<FireEscape> fireEscapes() { return List.copyOf(fireEscapes); }
    public List<Connector> connectors() { return List.copyOf(connectors); }
    /** The parcel index whose basement hides a cellar, or -1. */
    public int hiddenCellar() { return hiddenCellar; }
    public Expect expected(int parcel) { return expect.get(parcel); }

    /** What one parcel's building opens; nothing for the starter superblock. */
    public Features features(int parcel) {
        if (!expect.containsKey(parcel)) { return NONE; }
        List<ServiceDoor> doors = new ArrayList<>();
        for (Corridor c : corridors) {
            if (c.west().parcel() == parcel) { doors.add(c.west()); }
            if (c.east().parcel() == parcel) { doors.add(c.east()); }
        }
        List<FireEscape> escapes = new ArrayList<>();
        for (FireEscape f : fireEscapes) { if (f.parcel() == parcel) { escapes.add(f); } }
        List<Opening> openings = new ArrayList<>();
        for (Connector c : connectors) {
            if (c.a().parcel() == parcel) { openings.add(c.a()); }
            if (c.b().parcel() == parcel) { openings.add(c.b()); }
        }
        return new Features(doors, parcel == hiddenCellar, escapes, openings);
    }

    // ---- service passages and bridges over a strip's courtyard passage ----------------------

    private void pair(long seed, int superblock, int westSlot, int eastSlot, boolean north) {
        Expect a = expect.get(superblock * ParcelPlan.SLOTS_PER_SUPERBLOCK + westSlot);
        Expect b = expect.get(superblock * ParcelPlan.SLOTS_PER_SUPERBLOCK + eastSlot);
        if (a == null || b == null) { return; }
        ParcelPlan pa = a.parcel(), pb = b.parcel();
        int x0 = pa.x1() + 1, x1 = pb.x0() - 1;
        if (x1 - x0 + 1 != CityPlanner.PASSAGE_WIDTH || pa.z0() != pb.z0() || pa.z1() != pb.z1()) { return; }
        if (a.basement() && b.basement() && roll(seed, UrbanSalts.ROUTE_PASSAGE, pa.id(), pb.id()) < CORRIDOR) {
            int z = north ? pa.z0() + HALL_ROW : pa.z1() - HALL_ROW - 1;
            corridors.add(new Corridor(door(a, pa.x1(), -1, z, 2), door(b, pb.x0(), 1, z, 1), x0, z, x1, z + 1));
        }
        int depth = a.depth();
        if (a.roofY() == b.roofY() && depth >= PAST_BULKHEAD + 4 && b.depth() == depth
                && roll(seed, UrbanSalts.ROUTE_CONNECT, pa.id(), pb.id()) < BRIDGE) {
            int row = (PAST_BULKHEAD + depth - 3) / 2;
            int z = north ? pa.z0() + row : pa.z1() - row - 1;
            int y = a.roofY() + 1;
            connectors.add(new Connector(new Opening(pa.index(), new Vec3i(pa.x1(), y, z), new Vec3i(pa.x1(), y, z + 1)),
                    new Opening(pb.index(), new Vec3i(pb.x0(), y, z), new Vec3i(pb.x0(), y, z + 1)),
                    true, x0, z, x1, z + 1, a.roofY()));
        }
    }

    private static ServiceDoor door(Expect e, int wallX, int inward, int z, int room) {
        return new ServiceDoor(e.parcel().index(), room, new Vec3i(wallX, SERVICE_Y, z), new Vec3i(wallX, SERVICE_Y, z + 1),
                new Vec3i(wallX + inward, SERVICE_Y, z), new Vec3i(wallX + inward, SERVICE_Y, z + 1));
    }

    // ---- parapet openings between abutting roofs --------------------------------------------

    private void abutting(long seed) {
        List<Expect> all = new ArrayList<>(expect.values());
        all.sort(java.util.Comparator.comparingInt(e -> e.parcel().index()));
        Set<Integer> joined = new HashSet<>();
        for (Expect a : all) {
            for (Expect b : all) {
                ParcelPlan pa = a.parcel(), pb = b.parcel();
                if (pa.index() >= pb.index() || pa.superblock() != pb.superblock() || a.roofY() != b.roofY()) { continue; }
                boolean eastWest = pa.x1() + 1 == pb.x0() || pb.x1() + 1 == pa.x0();
                boolean northSouth = pa.z1() + 1 == pb.z0() || pb.z1() + 1 == pa.z0();
                if (eastWest == northSouth) { continue; }
                int lo = eastWest ? Math.max(pa.z0(), pb.z0()) + 2 : Math.max(pa.x0(), pb.x0()) + 2;
                int hi = eastWest ? Math.min(pa.z1(), pb.z1()) - 3 : Math.min(pa.x1(), pb.x1()) - 3;
                if (hi < lo || roll(seed, UrbanSalts.ROUTE_CONNECT, pa.id(), pb.id() ^ 1) >= CONNECTOR) { continue; }
                // Two cells as near the middle of the shared wall as the bulkheads allow.
                int best = Integer.MIN_VALUE, mid = (lo + hi) / 2;
                for (int s = lo; s <= hi; s++) {
                    if (clear(a, b, eastWest, s) && clear(a, b, eastWest, s + 1)
                            && (best == Integer.MIN_VALUE || Math.abs(s - mid) < Math.abs(best - mid))) { best = s; }
                }
                if (best == Integer.MIN_VALUE || !joined.add(pa.index() * 128 + pb.index())) { continue; }
                int y = a.roofY() + 1;
                Opening oa = opening(pa, eastWest, pa.x1() + 1 == pb.x0() || pa.z1() + 1 == pb.z0(), best, y);
                Opening ob = opening(pb, eastWest, !(pa.x1() + 1 == pb.x0() || pa.z1() + 1 == pb.z0()), best, y);
                connectors.add(new Connector(oa, ob, false, 0, 0, -1, -1, a.roofY()));
            }
        }
    }

    private static boolean clear(Expect a, Expect b, boolean eastWest, int s) {
        int ax = eastWest ? edgeX(a, b) : s, az = eastWest ? s : edgeZ(a, b);
        int bx = eastWest ? edgeX(b, a) : s, bz = eastWest ? s : edgeZ(b, a);
        return !a.bulkhead(ax, az) && !b.bulkhead(bx, bz);
    }

    private static int edgeX(Expect self, Expect other) {
        return self.parcel().x1() + 1 == other.parcel().x0() ? self.parcel().x1() : self.parcel().x0();
    }

    private static int edgeZ(Expect self, Expect other) {
        return self.parcel().z1() + 1 == other.parcel().z0() ? self.parcel().z1() : self.parcel().z0();
    }

    /** The two wall-line cells of a parcel at a shared edge; {@code high} for its east or south edge. */
    private static Opening opening(ParcelPlan p, boolean eastWest, boolean high, int s, int y) {
        if (eastWest) {
            int x = high ? p.x1() : p.x0();
            return new Opening(p.index(), new Vec3i(x, y, s), new Vec3i(x, y, s + 1));
        }
        int z = high ? p.z1() : p.z0();
        return new Opening(p.index(), new Vec3i(s, y, z), new Vec3i(s + 1, y, z));
    }

    // ---- fire escapes -------------------------------------------------------------------------

    private void fireEscape(long seed, Expect e) {
        ParcelPlan p = e.parcel();
        if (e.storeys() < 2 || roll(seed, UrbanSalts.ROUTE_ESCAPE, p.id(), 0) >= FIRE_ESCAPE) { return; }
        int rear = e.depth() - 1;
        List<Integer> valid = new ArrayList<>();
        for (int u = 2; u <= e.width() - 3; u++) {
            if (u == e.entry() || u == e.entry() + 1 || nearRearEntrance(e, u)) { continue; }
            Vec3i outside = e.pos(u, UrbanDatum.STREET_Y + 1, rear + 1);
            if (openGround(e, outside)) { valid.add(u); }
        }
        if (valid.isEmpty()) { return; }
        int u = valid.get(valid.size() / 2);
        Vec3i gap = e.pos(u, e.roofY() + 1, rear), top = e.pos(u, e.roofY() + 1, rear + 1);
        fireEscapes.add(new FireEscape(p.index(), gap, top));
    }

    private static boolean nearRearEntrance(Expect e, int u) {
        for (ParcelPlan.Entrance entrance : e.parcel().entrances()) {
            if (entrance.primary()) { continue; }
            int a = e.u(entrance.x(), entrance.z()), b = e.u(entrance.x2(), entrance.z2());
            if (e.v(entrance.x(), entrance.z()) == e.depth() - 1 && u >= Math.min(a, b) - 2 && u <= Math.max(a, b) + 2) {
                return true;
            }
        }
        return false;
    }

    /**
     * Open public ground outside a rear wall: walkable, unplanted, unflooded, in this region,
     * touching no other parcel and no building's entrance approach, clear of tree canopies.
     */
    private boolean openGround(Expect e, Vec3i c) {
        int x = c.x(), z = c.z();
        if (!district.contains(x, z) || district.parcelAt(x, z) != null) { return false; }
        UrbanSurface surface = district.surfaceAt(x, z);
        UrbanOverlay overlay = district.overlayAt(x, z);
        if (!surface.publicWalkable || surface == UrbanSurface.TREE_BED || surface == UrbanSurface.CANAL
                || overlay != UrbanOverlay.NONE && overlay != UrbanOverlay.OVERGROWN) { return false; }
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : around) {
            int nx = x + d[0], nz = z + d[1];
            if (!district.contains(nx, nz)) { return false; }
            ParcelPlan other = district.parcelAt(nx, nz);
            if (other != null && other.index() != e.parcel().index()) { return false; }
        }
        for (ParcelPlan parcel : district.parcels()) {
            for (ParcelPlan.Entrance entrance : parcel.entrances()) {
                if (Math.abs(entrance.outsideX() - x) <= 1 && Math.abs(entrance.outsideZ() - z) <= 1) { return false; }
            }
        }
        for (DistrictPlan.Tree tree : district.trees()) {
            if (Math.abs(tree.x() - x) <= DistrictPlan.Tree.CANOPY_RADIUS + 1
                    && Math.abs(tree.z() - z) <= DistrictPlan.Tree.CANOPY_RADIUS + 1) { return false; }
        }
        return true;
    }

    // ---- the hidden cellar --------------------------------------------------------------------

    /** One apartment of a residential region, unheld and deep enough for a sealed back room. */
    private int chooseCellar(long seed) {
        if (district.district != UrbanDistrict.RESIDENTIAL) { return -1; }
        int chosen = -1;
        long best = 0;
        for (Expect e : expect.values()) {
            if (e.family() != Family.APARTMENT || e.variant() == Variant.OCCUPIED || e.depth() < RouteTemplates.CELLAR_MIN_DEPTH) {
                continue;
            }
            long h = UrbanHash.mix(seed, UrbanRevision.LAYOUT_UNDERGROUND, UrbanSalts.ROUTE_REFUGE, e.parcel().id(), 0, 0);
            if (chosen < 0 || Long.compareUnsigned(h, best) < 0) { chosen = e.parcel().index(); best = h; }
        }
        return chosen;
    }

    private static double roll(long seed, long salt, long a, long b) {
        return UrbanHash.unit(seed, UrbanRevision.LAYOUT_UNDERGROUND, salt, a, b, 0);
    }
}
