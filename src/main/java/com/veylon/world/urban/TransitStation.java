package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.StairGeometry;
import com.veylon.world.urban.BuildingPlan.Fixture;
import com.veylon.world.urban.BuildingPlan.FixtureKind;
import com.veylon.world.urban.BuildingPlan.Floor;
import com.veylon.world.urban.BuildingPlan.Function;
import com.veylon.world.urban.BuildingPlan.Room;
import com.veylon.world.urban.BuildingPlan.Variant;
import com.veylon.world.urban.TransitNetwork.Frame;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One abandoned transit station: a side platform beside its segment's track, a service room
 * at the platform's far end and a straight stair up to the owner region's avenue sidewalk,
 * all inside the avenue band on the owner's side (milestone 14).
 *
 * <p>Along the segment, from the low end ({@code R = a - start}):
 * <pre>
 *   R -1      end wall
 *   R 0..3    service room (two wide): lockers and a shelf along the outer side
 *   R 4       partition with the room's service door
 *   R 5..16   platform, one step above the track, under a vault clear to y 39
 *   R 17..23  stair, two wide, rising one block per cell to the street; a wall keeps it
 *             from the track
 *   R 24      the street cell at the top of the stair, the station's approach
 * </pre>
 * Across the band ({@code m}, 0 next to the avenue's centre line): track 0..2, platform,
 * room and stair 3..4, outer wall 5. At the street, guard rails two courses high close the
 * stair opening on three sides, so nobody walks into it. Every block comes from
 * {@link #blockAt}, which stamping, validation and plan queries share.
 *
 * <p>The station is also a {@link BuildingPlan} of family {@link BuildingPlan.Family#TRANSIT},
 * so containers, room locations, encounter anchors and the portal graph treat it like a
 * building. Its id borrows parcel slot 7 of the owner region's superblock beside it, a slot no
 * parcel uses (superblocks hold at most six parcels, slots 0 to 5).
 */
public final class TransitStation {
    /** Parcel slot station ids borrow; no planned parcel ever has it. */
    public static final int ID_SLOT = 7;
    public static final int SPAN = 27;
    static final int ROOM = 0, PARTITION = 4, PLATFORM_LOW = 5, PLATFORM_HIGH = 16, STAIR_LOW = 17,
            STAIR_HIGH = 23, TOP = 24, LOW = -1, HIGH = 25;
    static final int PLATFORM_Y = TransitNetwork.FEET_Y + 1;
    static final int VAULT_TOP = UrbanDatum.STREET_Y - 1;
    static final int ROOM_CEILING = 37;
    private static final int STREET = UrbanDatum.STREET_Y;
    /** First and last start offsets: stations keep clear of both junction boxes and plugs. */
    static final int FIRST_START = TransitNetwork.PLUG_LOW + 8;
    static final int LAST_START = TransitNetwork.PLUG_HIGH - 8 - HIGH;

    private final Frame frame;
    private final int start;
    private final boolean flooded;
    private final Variant variant;
    private final BlockType tread;
    private final BuildingPlan plan;

    private TransitStation(Frame frame, int start, boolean flooded, Variant variant, long districtId) {
        this.frame = frame; this.start = start; this.flooded = flooded; this.variant = variant;
        this.tread = StairGeometry.ascending(frame.alongDx(), frame.alongDz());
        this.plan = buildPlan(districtId);
    }

    public Frame frame() { return frame; }
    /** The along-offset of the service room's first cell. */
    public int start() { return start; }
    public int low() { return start + LOW; }
    public int high() { return start + HIGH; }
    public boolean covers(int a) { return a >= low() && a <= high(); }
    public boolean flooded() { return flooded; }
    public Variant variant() { return variant; }
    /** The station as a building plan: rooms, fixtures, approach and stair route. */
    public BuildingPlan plan() { return plan; }
    public long id() { return plan.id(); }

    /** True for a world column of the station's footprint, the stair top included. */
    public boolean containsColumn(int x, int z) {
        ParcelPlan p = plan.parcel();
        return x >= p.x0() && x <= p.x1() && z >= p.z0() && z <= p.z1();
    }

    /**
     * Sites a segment's station on its owner region's sidewalk, or returns null when no stretch
     * of the segment fits. Only the street stair has constraints: its rails need plain avenue
     * roadway and sidewalk (no crosswalk, tree, fence or checkpoint) and no building entrance
     * may open beside it. Candidates are tried from a seeded start, in order, so the answer is
     * the same for both bordering regions.
     */
    static TransitStation site(long seed, Frame frame, DistrictPlan owner, boolean flooded) {
        Set<Long> approaches = new HashSet<>();
        for (ParcelPlan parcel : owner.parcels()) {
            for (ParcelPlan.Entrance e : parcel.entrances()) {
                approaches.add(column(e.outsideX(), e.outsideZ()));
                approaches.add(column(e.x2() + e.facing().dx, e.z2() + e.facing().dz));
            }
        }
        int axis = frame.northSouth() ? 1 : 2;
        int count = LAST_START - FIRST_START + 1;
        int offset = UrbanHash.range(0, count - 1, seed, UrbanRevision.LAYOUT_UNDERGROUND,
                UrbanSalts.TRANSIT_STATION, axis, frame.line(), frame.index());
        for (int k = 0; k < count; k++) {
            int start = FIRST_START + (offset + k) % count;
            if (!fits(frame, start, owner, approaches)) { continue; }
            double roll = UrbanHash.unit(seed, UrbanRevision.LAYOUT_UNDERGROUND, UrbanSalts.TRANSIT_STATION,
                    axis, frame.line(), frame.index() * 2L + 1);
            Variant variant = roll < 0.5 ? Variant.INTACT : roll < 0.8 ? Variant.DAMAGED : Variant.PICKED_CLEAN;
            TransitStation station = new TransitStation(frame, start, flooded, variant, owner.districtId);
            if (TransitValidator.inspect(station).ok()) { return station; }
        }
        return null;
    }

    private static boolean fits(Frame frame, int start, DistrictPlan owner, Set<Long> approaches) {
        for (int r = STAIR_LOW + 1; r <= TOP + 1; r++) {
            for (int m = 1; m <= TransitNetwork.BAND; m++) {
                int x = frame.x(m, start + r), z = frame.z(m, start + r);
                if (approaches.contains(column(x, z))) { return false; }
                if (m < 2 || m > 5) { continue; }
                UrbanSurface surface = owner.surfaceAt(x, z);
                UrbanOverlay overlay = owner.overlayAt(x, z);
                boolean street = m == 2 ? surface == UrbanSurface.ROADWAY : surface == UrbanSurface.SIDEWALK;
                if (!street || overlay != UrbanOverlay.NONE && overlay != UrbanOverlay.OVERGROWN) { return false; }
            }
        }
        return true;
    }

    private static long column(int x, int z) { return ((long) x << 32) ^ (z & 0xFFFF_FFFFL); }

    /**
     * The station's block at mirrored offset {@code m} and along-offset {@code a} on its owner
     * side, or null to leave the street fill and surface. Only meaningful where
     * {@link #covers} is true.
     */
    public BlockType blockAt(int m, int a, int y) {
        int r = a - start;
        if (m >= TransitNetwork.BAND || !covers(a) || y < TransitNetwork.SLAB_Y) { return null; }
        boolean vault = r >= PLATFORM_LOW && r <= PLATFORM_HIGH;
        if (m <= 1 || m == 2 && vault) {
            return track(m, r, y, vault);
        }
        if (m == 2) {
            if (r >= STAIR_LOW + 2 && r <= STAIR_HIGH - 1 && (y == STREET + 1 || y == STREET + 2)) {
                return BlockType.RAILING;
            }
            if (r > STAIR_HIGH) { return track(m, r, y, false); }
            return y <= VAULT_TOP ? BlockType.CONCRETE : null;
        }
        if (m == 5) {
            if (r >= STAIR_LOW + 2 && r <= STAIR_HIGH - 1 && (y == STREET + 1 || y == STREET + 2)) {
                return BlockType.RAILING;
            }
            return r <= STAIR_HIGH && y <= VAULT_TOP ? BlockType.CONCRETE : null;
        }
        return inner(m, r, y);
    }

    /** Track cells: the plain tunnel, under the vault opened to y 39. */
    private BlockType track(int m, int r, int y, boolean vault) {
        if (y == TransitNetwork.SLAB_Y) { return m == 1 ? BlockType.SHEET_METAL : BlockType.GRAVEL; }
        if (y == TransitNetwork.FEET_Y) { return flooded ? BlockType.WATER : BlockType.AIR; }
        int top = vault ? VAULT_TOP : TransitNetwork.CEILING_Y - 1;
        if (y <= top) { return BlockType.AIR; }
        return !vault && y == TransitNetwork.CEILING_Y ? BlockType.CONCRETE : null;
    }

    /** Offsets 3 and 4: end wall, service room, partition, platform and stair. */
    private BlockType inner(int m, int r, int y) {
        if (r == LOW) { return y <= ROOM_CEILING ? BlockType.CONCRETE : null; }
        if (r < PARTITION) {
            if (y <= TransitNetwork.FEET_Y || y == ROOM_CEILING) { return y <= ROOM_CEILING ? BlockType.CONCRETE : null; }
            if (m == 4 && y == PLATFORM_Y && r <= 2) { return r == 1 ? BlockType.SHELF : BlockType.LOCKER; }
            return y < ROOM_CEILING ? BlockType.AIR : null;
        }
        if (r == PARTITION) {
            if (m == 4 && (y == PLATFORM_Y || y == PLATFORM_Y + 1)) { return BlockType.METAL_DOOR; }
            return y <= ROOM_CEILING ? BlockType.CONCRETE : null;
        }
        if (r <= PLATFORM_HIGH) {
            if (y <= TransitNetwork.FEET_Y) { return BlockType.CONCRETE; }
            if (y == PLATFORM_Y && variant == Variant.DAMAGED && (m == 3 && r == 8 || m == 4 && r == 13)) {
                return BlockType.DEBRIS;
            }
            return y <= VAULT_TOP ? BlockType.AIR : null;
        }
        if (r <= STAIR_HIGH) {
            int step = r - STAIR_LOW, treadY = PLATFORM_Y + step;
            if (y < treadY) { return BlockType.CONCRETE; }
            if (y == treadY) { return tread; }
            if (y <= VAULT_TOP) { return BlockType.AIR; }
            if (y == STREET) { return step >= 3 ? BlockType.AIR : null; }
            return r == STAIR_LOW + 2 && (y == STREET + 1 || y == STREET + 2) ? BlockType.RAILING : null;
        }
        // Past the stair top the plain tunnel wall resumes at offset 3.
        return m == TransitNetwork.WALL && y <= TransitNetwork.CEILING_Y ? BlockType.CONCRETE : null;
    }

    private Vec3i cell(int m, int r, int y) {
        return new Vec3i(frame.x(m, start + r), y, frame.z(m, start + r));
    }

    private BuildingPlan buildPlan(long districtId) {
        int mid = start + (PLATFORM_LOW + PLATFORM_HIGH) / 2;
        int across = frame.positive() ? 0 : 3, along = Math.max(0, Math.min(3, mid / StreetNetwork.SUPERBLOCK_PITCH));
        int superblock = frame.northSouth() ? along * 4 + across : across * 4 + along;
        int index = superblock * ParcelPlan.SLOTS_PER_SUPERBLOCK + ID_SLOT;
        long parcelId = UrbanIds.parcel(districtId, index);
        long id = UrbanIds.building(parcelId, 0);
        long floorId = UrbanIds.floor(id, -2);

        Vec3i a = cell(0, LOW, 0), b = cell(5, TOP, 0);
        ParcelPlan.Side facing = frame.northSouth() ? ParcelPlan.Side.SOUTH : ParcelPlan.Side.EAST;
        Vec3i entry = cell(frame.positive() ? 3 : 4, STAIR_HIGH, STREET + 1);
        Vec3i s0 = cell(3, STAIR_LOW, 0), s1 = cell(4, STAIR_HIGH, 0);
        ParcelPlan parcel = new ParcelPlan(index, parcelId, superblock, ParcelPlan.Use.SERVICE,
                Math.min(a.x(), b.x()), Math.min(a.z(), b.z()), Math.max(a.x(), b.x()), Math.max(a.z(), b.z()),
                facing, List.of(new ParcelPlan.Entrance(entry.x(), entry.z(), facing, true)),
                new ParcelPlan.Rect(Math.min(s0.x(), s1.x()), Math.min(s0.z(), s1.z()),
                        Math.max(s0.x(), s1.x()), Math.max(s0.z(), s1.z())), 0);

        Room platform = room(floorId, 0, Function.PLATFORM, 3, PLATFORM_LOW, 4, PLATFORM_HIGH,
                cell(3, (PLATFORM_LOW + PLATFORM_HIGH) / 2, PLATFORM_Y));
        Room track = room(floorId, 1, Function.TRACK, 0, PLATFORM_LOW, 2, PLATFORM_HIGH,
                cell(1, (PLATFORM_LOW + PLATFORM_HIGH) / 2, TransitNetwork.FEET_Y));
        Room service = room(floorId, 2, Function.SERVICE, 3, ROOM, 4, PARTITION - 1, cell(3, 1, PLATFORM_Y));
        Room stair = room(floorId, 3, Function.CORRIDOR_STAIRS, 3, STAIR_LOW, 4, STAIR_HIGH,
                cell(3, STAIR_LOW, PLATFORM_Y + 1));
        List<Fixture> fixtures = new ArrayList<>();
        fixtures.add(new Fixture(UrbanIds.fixture(service.id(), 0), FixtureKind.DOOR, cell(4, PARTITION, PLATFORM_Y),
                2, BlockType.METAL_DOOR, cell(4, PARTITION - 1, PLATFORM_Y), false));
        int[] slots = {2, 5, 6};
        for (int r = 0; r <= 2; r++) {
            BlockType block = r == 1 ? BlockType.SHELF : BlockType.LOCKER;
            fixtures.add(new Fixture(UrbanIds.fixture(service.id(), slots[r]), FixtureKind.CONTAINER,
                    cell(4, r, PLATFORM_Y), 1, block, cell(3, r, PLATFORM_Y), false));
        }
        fixtures.add(new Fixture(UrbanIds.fixture(service.id(), 4), FixtureKind.QUEST, service.anchor(), 1,
                BlockType.AIR, service.anchor(), false));
        fixtures.add(new Fixture(UrbanIds.fixture(platform.id(), 4), FixtureKind.QUEST, platform.anchor(), 1,
                BlockType.AIR, platform.anchor(), false));
        List<Vec3i> route = new ArrayList<>();
        route.add(cell(3, PLATFORM_HIGH, PLATFORM_Y));
        for (int r = STAIR_LOW; r <= STAIR_HIGH; r++) { route.add(cell(3, r, PLATFORM_Y + 1 + r - STAIR_LOW)); }
        route.add(cell(3, TOP, STREET + 1));
        Floor floor = new Floor(floorId, -2, TransitNetwork.SLAB_Y, List.of(platform, track, service, stair),
                platform.anchor());
        return new BuildingPlan(id, UrbanRevision.BUILDING_TEMPLATE_ROUTES, "transit-station",
                BuildingPlan.Family.TRANSIT, variant, parcel, 0, false, List.of(floor), fixtures,
                List.of(cell(3, TOP, STREET + 1)), List.of(new BuildingPlan.Stair(-2, route)), volume());
    }

    private Room room(long floorId, int index, Function function, int m0, int r0, int m1, int r1, Vec3i anchor) {
        Vec3i p = cell(m0, r0, 0), q = cell(m1, r1, 0);
        return new Room(UrbanIds.room(floorId, index), List.of(function), new ParcelPlan.Rect(Math.min(p.x(), q.x()),
                Math.min(p.z(), q.z()), Math.max(p.x(), q.x()), Math.max(p.z(), q.z())), anchor);
    }

    /**
     * The volume validation reads: the station, its track's far half, the street over it and one
     * column of open ground beyond the avenue, the worst case for whatever stands there.
     */
    PlanVolume volume() {
        Vec3i a = cell(-4, LOW - 1, 0), b = cell(TransitNetwork.BAND, HIGH + 2, 0);
        int x0 = Math.min(a.x(), b.x()), z0 = Math.min(a.z(), b.z());
        int w = Math.abs(a.x() - b.x()) + 1, d = Math.abs(a.z() - b.z()) + 1;
        PlanVolume.Builder out = new PlanVolume.Builder(x0, z0, w, d, TransitNetwork.SLAB_Y - 1, STREET + 3);
        for (int k = -4; k <= TransitNetwork.BAND; k++) {
            for (int r = LOW - 1; r <= HIGH + 2; r++) {
                Vec3i c = cell(k, r, 0);
                for (int y = TransitNetwork.SLAB_Y - 1; y <= STREET + 3; y++) {
                    BlockType base = y < STREET ? UrbanSurfaceMaterials.FILL : y == STREET ? BlockType.PAVING : BlockType.AIR;
                    BlockType block = k >= 0 && covers(start + r) ? blockAt(k, start + r, y)
                            : plainTunnel(k < 0 ? -1 - k : k, y);
                    out.set(c.x(), y, c.z(), block == null ? base : block);
                }
            }
        }
        return out.build();
    }

    /** The plain cross-section beside the station: no plug or mound comes this close. */
    private BlockType plainTunnel(int m, int y) {
        if (m > TransitNetwork.WALL || y < TransitNetwork.SLAB_Y || y > TransitNetwork.CEILING_Y) { return null; }
        if (m == TransitNetwork.WALL || y == TransitNetwork.CEILING_Y) { return BlockType.CONCRETE; }
        if (y == TransitNetwork.SLAB_Y) { return m == 1 ? BlockType.SHEET_METAL : BlockType.GRAVEL; }
        return y == TransitNetwork.FEET_Y && flooded ? BlockType.WATER : BlockType.AIR;
    }
}
