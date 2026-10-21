package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.urban.ParcelPlan.Rect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Layout eleven's living sites in one planning region (urban milestone 15). A pure function of
 * (seed, district plan), like every plan, so neighbours, chunk order and cache eviction never
 * change it.
 *
 * <ul>
 *   <li><b>Growth sites</b>, in the quarantine only: up to {@value #GROWTH_SITES_PER_REGION} of a
 *       region's courtyards and its fenced square, those with room, by lowest salted hash. A site
 *       is a node on the open cell nearest the middle of the space, a membrane carpet over the
 *       open ground within {@value #MEMBRANE_RADIUS} cells of it, and the membrane cell beside
 *       the node where its Veilmaw folds. Its territory reaches {@value #TERRITORY} blocks from
 *       the node, over the courtyard walkway and the rear doors that open onto it.</li>
 *   <li><b>Wildlife habitats</b>, outside the quarantine: a park keeps
 *       {@value #PARK_HARES} Murkhares on its lawn, a Glowdeer when it is large, and a flock of
 *       Skitterwings over its trees; one in three residential courtyards keeps a Murkhare; a
 *       canal basin keeps a flock over the water. Roof flocks need building heights, so
 *       {@link EncounterAnchors} adds those.</li>
 * </ul>
 *
 * <p>Nothing here touches a walkway, path, passage, tree bed, fence or the two cells around a
 * parcel (rule A9), and nothing lies in the starter superblock. Wildlife stamps no block: a
 * habitat is only where its animals may go. A growth site stamps its node and carpet
 * ({@link UrbanEcologyStamper}). Every slot has a stable encounter id that borrows parcel slot
 * {@value #ID_SLOT} of its superblock, a slot no parcel or station uses, with the room naming the
 * habitat and the slot the individual.
 */
public final class EcologySites {
    /** Parcel slot the ecology ids borrow; superblocks hold at most six parcels, stations use 7. */
    public static final int ID_SLOT = 6;
    public static final int GROWTH_SITES_PER_REGION = 2;
    public static final double MEMBRANE_RADIUS = 3.5;
    /** A growth territory's radius around its node, in blocks. */
    public static final float TERRITORY = 9f;
    /** Smallest open interior, in cells a side, that may hold a growth site. */
    static final int MIN_INTERIOR = 5;
    public static final int PARK_HARES = 3;
    /** A park at least this many cells in area keeps a Glowdeer. */
    public static final int LARGE_PARK_AREA = 900;
    public static final int FLOCK = 2;
    /** Habitat rooms of an ecology id. */
    public static final int ROOM_GROWTH = 0, ROOM_HARE = 1, ROOM_DEER = 2, ROOM_ROOF = 3, ROOM_FLOCK = 4;
    private static final int GROUND = UrbanDatum.STREET_Y;
    private static final int LAYOUT = UrbanRevision.LAYOUT_ECOLOGY;

    /** A growth node, its carpet and the cell its Veilmaw folds in. */
    public record GrowthSite(int superblock, long encounterId, Vec3i node, Vec3i rest, List<Vec3i> membrane) {
        public GrowthSite {
            membrane = List.copyOf(membrane);
        }

        /** The Veilmaw's territory around this node. */
        public Habitat territory() {
            return Habitat.territory(node, rest, TERRITORY);
        }
    }

    /** One planned body: its stable id, species, the cell it appears at and where it may go. */
    public record Slot(long encounterId, UrbanSpecies species, Vec3i cell, Habitat habitat) { }

    private final List<GrowthSite> growth;
    private final List<Slot> slots;

    private EcologySites(List<GrowthSite> growth, List<Slot> slots) {
        this.growth = List.copyOf(growth);
        this.slots = List.copyOf(slots);
    }

    public List<GrowthSite> growthSites() {
        return growth;
    }

    /** Every slot of the region but roof flocks: growth organisms first, then wildlife. */
    public List<Slot> slots() {
        return slots;
    }

    /** The slots of one superblock, in plan order. */
    public List<Slot> slots(int superblock) {
        List<Slot> out = new ArrayList<>();
        for (Slot s : slots) {
            if (UrbanIds.parcelIndex(s.encounterId()) / ParcelPlan.SLOTS_PER_SUPERBLOCK == superblock) {
                out.add(s);
            }
        }
        return out;
    }

    /** The encounter id of slot {@code slot} in habitat room {@code room} of a superblock. */
    public static long encounterId(DistrictPlan plan, int superblock, int room, int slot) {
        long parcel = UrbanIds.parcel(plan.districtId, superblock * ParcelPlan.SLOTS_PER_SUPERBLOCK + ID_SLOT);
        long floor = UrbanIds.floor(UrbanIds.building(parcel, 0), 0);
        return UrbanIds.encounter(UrbanIds.room(floor, room), slot);
    }

    /** True for an encounter id in the ecology slot of its superblock. */
    public static boolean isEcologyId(long id) {
        return UrbanIds.kind(id) == UrbanIds.Kind.ENCOUNTER
                && UrbanIds.parcelIndex(id) % ParcelPlan.SLOTS_PER_SUPERBLOCK == ID_SLOT;
    }

    /** Plans a layout-eleven region; any other layout has no living sites. */
    public static EcologySites of(long seed, DistrictPlan plan) {
        if (plan.layoutRevision != UrbanRevision.LAYOUT_DISTRICTS || plan.district == null) {
            return new EcologySites(List.of(), List.of());
        }
        List<GrowthSite> growth = plan.district == UrbanDistrict.QUARANTINE ? growth(seed, plan) : List.of();
        List<Slot> slots = new ArrayList<>();
        for (GrowthSite site : growth) {
            slots.add(new Slot(site.encounterId(), UrbanSpecies.VEILMAW, site.rest(), site.territory()));
        }
        if (plan.district != UrbanDistrict.QUARANTINE) {
            for (SuperblockPlan sb : plan.superblocks()) {
                if (starter(plan, sb)) {
                    continue;
                }
                switch (sb.kind()) {
                    case PARK -> park(seed, plan, sb, slots);
                    case CANAL -> flock(plan, sb, sb.bounds(), GROUND + 5, GROUND + 3, GROUND + 12, slots);
                    case COURTYARD_HOUSING -> {
                        if (plan.district == UrbanDistrict.RESIDENTIAL && sb.open() != null
                                && UrbanHash.unit(seed, LAYOUT, UrbanSalts.WILDLIFE_COURTYARD, plan.rx, plan.rz, sb.index()) < 1.0 / 3) {
                            courtyardHare(plan, sb, slots);
                        }
                    }
                    default -> { }
                }
            }
        }
        return new EcologySites(growth, slots);
    }

    // ------------------------------------------------------------------
    // Growth sites
    // ------------------------------------------------------------------

    private static List<GrowthSite> growth(long seed, DistrictPlan plan) {
        List<SuperblockPlan> candidates = new ArrayList<>();
        for (SuperblockPlan sb : plan.superblocks()) {
            Rect interior = growthInterior(sb);
            if (!starter(plan, sb) && interior != null && width(interior) >= MIN_INTERIOR && depth(interior) >= MIN_INTERIOR) {
                candidates.add(sb);
            }
        }
        candidates.sort(Comparator.comparing((SuperblockPlan sb) ->
                UrbanHash.mix(seed, LAYOUT, UrbanSalts.GROWTH_SITE, plan.rx, plan.rz, sb.index()), Long::compareUnsigned));
        List<GrowthSite> sites = new ArrayList<>();
        for (SuperblockPlan sb : candidates) {
            if (sites.size() >= GROWTH_SITES_PER_REGION) {
                break;
            }
            GrowthSite site = growthSite(plan, sb, growthInterior(sb));
            if (site != null) {
                sites.add(site);
            }
        }
        return sites;
    }

    /** The open ground a growth site may use: a courtyard inside its walkway ring, a square inside its fence. */
    static Rect growthInterior(SuperblockPlan sb) {
        return switch (sb.kind()) {
            case COURTYARD_HOUSING -> sb.open() == null ? null : inset(sb.open(), CityPlanner.COURTYARD_PATH_WIDTH);
            case PLAZA -> inset(sb.bounds(), 3);
            default -> null;
        };
    }

    private static GrowthSite growthSite(DistrictPlan plan, SuperblockPlan sb, Rect in) {
        Set<Long> trunks = trunks(plan);
        int cx = (in.x0() + in.x1()) / 2, cz = (in.z0() + in.z1()) / 2;
        for (int r = 0; r <= 3; r++) {
            for (int z = cz - r; z <= cz + r; z++) {
                for (int x = cx - r; x <= cx + r; x++) {
                    if (Math.max(Math.abs(x - cx), Math.abs(z - cz)) != r || !openGround(plan, in, trunks, x, z)) {
                        continue;
                    }
                    List<Vec3i> membrane = new ArrayList<>();
                    int reach = (int) Math.ceil(MEMBRANE_RADIUS);
                    for (int dz = -reach; dz <= reach; dz++) {
                        for (int dx = -reach; dx <= reach; dx++) {
                            if (dx * dx + dz * dz <= MEMBRANE_RADIUS * MEMBRANE_RADIUS
                                    && openGround(plan, in, trunks, x + dx, z + dz)) {
                                membrane.add(new Vec3i(x + dx, GROUND, z + dz));
                            }
                        }
                    }
                    Vec3i rest = null;
                    int[][] around = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
                    for (int[] d : around) {
                        if (membrane.contains(new Vec3i(x + d[0], GROUND, z + d[1]))) {
                            rest = new Vec3i(x + d[0], GROUND + 1, z + d[1]);
                            break;
                        }
                    }
                    if (rest == null) {
                        continue;
                    }
                    return new GrowthSite(sb.index(), encounterId(plan, sb.index(), ROOM_GROWTH, 0),
                            new Vec3i(x, GROUND + 1, z), rest, membrane);
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Wildlife
    // ------------------------------------------------------------------

    private static void park(long seed, DistrictPlan plan, SuperblockPlan sb, List<Slot> slots) {
        Rect b = sb.bounds();
        Rect in = inset(b, 2);
        Set<Long> trunks = trunks(plan);
        List<Vec3i> cells = new ArrayList<>();
        for (int z = in.z0() + 1; z <= in.z1() - 1; z += 4) {
            for (int x = in.x0() + 1; x <= in.x1() - 1; x += 4) {
                if (plan.surfaceAt(x, z) == UrbanSurface.PARK && lawn(plan, trunks, x, z)) {
                    cells.add(new Vec3i(x, GROUND + 1, z));
                }
            }
        }
        cells.sort(Comparator.comparing((Vec3i c) ->
                UrbanHash.mix(plan.seed, LAYOUT, UrbanSalts.WILDLIFE_PARK, c.x(), c.z(), 0), Long::compareUnsigned));
        Habitat ground = Habitat.area(in.x0(), in.z0(), in.x1(), in.z1(), GROUND + 1, GROUND + 4);
        List<Vec3i> taken = new ArrayList<>();
        int deer = width(b) * depth(b) >= LARGE_PARK_AREA ? 1 : 0;
        for (Vec3i c : cells) {
            if (taken.size() >= PARK_HARES + deer) {
                break;
            }
            if (taken.stream().anyMatch(t -> Math.max(Math.abs(t.x() - c.x()), Math.abs(t.z() - c.z())) < 4)) {
                continue;
            }
            boolean isDeer = taken.size() == PARK_HARES;
            slots.add(new Slot(encounterId(plan, sb.index(), isDeer ? ROOM_DEER : ROOM_HARE, isDeer ? 0 : taken.size()),
                    isDeer ? UrbanSpecies.GLOWDEER : UrbanSpecies.MURKHARE, c, ground));
            taken.add(c);
        }
        flock(plan, sb, b, GROUND + 9, GROUND + 5, GROUND + 18, slots);
    }

    /** A flock over open space: {@value #FLOCK} Skitterwings at the middle, kept to the space and a height band. */
    private static void flock(DistrictPlan plan, SuperblockPlan sb, Rect area, int y, int floorY, int ceilingY,
                              List<Slot> slots) {
        int cx = (area.x0() + area.x1()) / 2, cz = (area.z0() + area.z1()) / 2;
        Habitat air = Habitat.area(area.x0(), area.z0(), area.x1(), area.z1(), floorY, ceilingY);
        for (int i = 0; i < FLOCK; i++) {
            slots.add(new Slot(encounterId(plan, sb.index(), ROOM_FLOCK, i), UrbanSpecies.SKITTERWING,
                    new Vec3i(cx - 1 + 2 * i, y, cz), air));
        }
    }

    private static void courtyardHare(DistrictPlan plan, SuperblockPlan sb, List<Slot> slots) {
        Rect in = inset(sb.open(), CityPlanner.COURTYARD_PATH_WIDTH);
        if (width(in) < 4 || depth(in) < 4) {
            return;
        }
        Set<Long> trunks = trunks(plan);
        int cx = (in.x0() + in.x1()) / 2, cz = (in.z0() + in.z1()) / 2;
        for (int r = 0; r <= 3; r++) {
            for (int z = cz - r; z <= cz + r; z++) {
                for (int x = cx - r; x <= cx + r; x++) {
                    if (Math.max(Math.abs(x - cx), Math.abs(z - cz)) == r && in(in, x, z)
                            && plan.surfaceAt(x, z) == UrbanSurface.COURTYARD && lawn(plan, trunks, x, z)) {
                        slots.add(new Slot(encounterId(plan, sb.index(), ROOM_HARE, 0), UrbanSpecies.MURKHARE,
                                new Vec3i(x, GROUND + 1, z),
                                Habitat.area(in.x0(), in.z0(), in.x1(), in.z1(), GROUND + 1, GROUND + 4)));
                        return;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Cells
    // ------------------------------------------------------------------

    /** A growth cell: inside the interior, open lawn or square, clear of tree trunks and fences. */
    private static boolean openGround(DistrictPlan plan, Rect in, Set<Long> trunks, int x, int z) {
        if (!in(in, x, z) || !plan.contains(x, z)) {
            return false;
        }
        UrbanSurface s = plan.surfaceAt(x, z);
        return (s == UrbanSurface.COURTYARD || s == UrbanSurface.PLAZA) && settles(plan.overlayAt(x, z))
                && clearOfTrunks(trunks, x, z);
    }

    /** Walkable lawn for an animal: no tree bed, solid overlay or trunk beside it. */
    private static boolean lawn(DistrictPlan plan, Set<Long> trunks, int x, int z) {
        return plan.contains(x, z) && settles(plan.overlayAt(x, z)) && clearOfTrunks(trunks, x, z);
    }

    private static boolean settles(UrbanOverlay o) {
        return o == UrbanOverlay.NONE || o == UrbanOverlay.CONTAMINATED || o == UrbanOverlay.PLANTS;
    }

    private static boolean clearOfTrunks(Set<Long> trunks, int x, int z) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (trunks.contains(key(x + dx, z + dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Set<Long> trunks(DistrictPlan plan) {
        Set<Long> out = new HashSet<>();
        for (DistrictPlan.Tree t : plan.trees()) {
            out.add(key(t.x(), t.z()));
        }
        return Collections.unmodifiableSet(out);
    }

    private static boolean starter(DistrictPlan plan, SuperblockPlan sb) {
        return plan.rx == 0 && plan.rz == 0 && sb.index() == CityPlanner.STARTER_BLOCK;
    }

    private static Rect inset(Rect r, int by) {
        return new Rect(r.x0() + by, r.z0() + by, r.x1() - by, r.z1() - by);
    }

    private static boolean in(Rect r, int x, int z) {
        return x >= r.x0() && x <= r.x1() && z >= r.z0() && z <= r.z1();
    }

    private static int width(Rect r) {
        return r.x1() - r.x0() + 1;
    }

    private static int depth(Rect r) {
        return r.z1() - r.z0() + 1;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFF_FFFFL);
    }
}
