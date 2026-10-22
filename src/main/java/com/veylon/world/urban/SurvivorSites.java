package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Fixtures;
import com.veylon.world.VoxelClearance;
import com.veylon.world.urban.BuildingPlan.Fixture;
import com.veylon.world.urban.BuildingPlan.FixtureKind;
import com.veylon.world.urban.BuildingPlan.Floor;
import com.veylon.world.urban.BuildingPlan.Function;
import com.veylon.world.urban.BuildingPlan.Room;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Layout twelve's survivor sites in one planning region (urban milestone 16): who lives where,
 * planned before anyone is met. A pure function of (seed, district plan) and the frozen building
 * plans it reads, so chunk order, cache eviction and the order sites are visited never change it.
 *
 * <ul>
 *   <li><b>A survivor camp</b>, in every region but the quarantine that has one: the reclaimed
 *       ground floor of a held walk-up apartment (its street door already locked metal, its
 *       storage already theirs), chosen by lowest salted hash among the region's held apartments
 *       outside the starter superblock. Three to six people: a lookout by the rear door, a
 *       trader, a medic when there are four or more, and foragers. Guests who move in later
 *       sleep upstairs.</li>
 *   <li><b>A hidden refuge</b>, in every residential region whose route catalogue seals a cellar
 *       (milestone 14): two to five civilians in the room behind the loose panel. They belong to
 *       no faction and are nobody's captives.</li>
 * </ul>
 *
 * <p>Every place a person stands is a planned cell of the building's own frozen voxels: a floor
 * under the feet, headroom, no door, furniture, interaction lane or water. Beds, the cells where
 * people work by day, the stash they eat from, the cell they retreat to and a camp's lookout post
 * are all inside the site, so nothing ever sends a resident to a street or roof height. Resident
 * ids hang off the building with a stable slot ({@link UrbanIds#resident}); they never come from
 * a list order. Sites stamp no block: the buildings they occupy already exist.
 */
public final class SurvivorSites {
    public static final int MIN_CAMP = 3, MAX_CAMP = 6, MIN_REFUGE = 2, MAX_REFUGE = 5;
    /** Upstairs cells a camp keeps for guests: room for a whole refuge. */
    public static final int GUEST_BEDS = MAX_REFUGE;
    /** Held apartments a region tries, in hash order, before it keeps no camp. */
    static final int CAMP_ATTEMPTS = 6;
    private static final int LAYOUT = UrbanRevision.LAYOUT_SURVIVORS;

    /** What a site is; the code is what saves store. */
    public enum Kind {
        CAMP(1, "Survivor camp"), REFUGE(2, "Hidden refuge");

        public final int code;
        public final String label;

        Kind(int code, String label) {
            this.code = code;
            this.label = label;
        }

        public static Kind fromCode(int code) {
            for (Kind k : values()) {
                if (k.code == code) { return k; }
            }
            return null;
        }
    }

    /** A resident's part in the site; the stable archetype id the settlement layer gives the body. */
    public enum Role {
        LOOKOUT("guard"), TRADER("trader"), MEDIC("medic"), FORAGER("villager"), CIVILIAN("villager");

        public final String archetypeId;

        Role(String archetypeId) {
            this.archetypeId = archetypeId;
        }
    }

    /** One planned person: stable id and slot, role, name, the cell they sleep on and the cell they keep by day. */
    public record Resident(long id, int slot, Role role, String name, Vec3i bed, Vec3i day) { }

    /**
     * One planned site.
     *
     * @param id      the building the site occupies; also its settlement id
     * @param room    the hidden refuge's sealed room, {@link UrbanIds#NONE} for a camp
     * @param anchor  the planned cell activation and the map measure from
     * @param entry   where a stranger comes in: inside a camp's rear door, inside a refuge's panel
     * @param safe    the cell the frightened retreat to, farthest from the entry
     * @param post    a camp lookout's watch cell inside the rear door; null for a refuge
     * @param stash   the containers the site eats from, in fixture-id order
     * @param meals   where each stash container is used from, in the same order
     * @param guestBeds a camp's spare cells for people who move in; empty for a refuge
     * @param floor   the floor index the site lives on (0 for a camp, -1 for a refuge)
     */
    public record Site(long id, Kind kind, long room, Vec3i anchor, Vec3i entry, Vec3i safe, Vec3i post,
                       List<Vec3i> stash, List<Vec3i> meals, List<Resident> residents, List<Vec3i> guestBeds,
                       ParcelPlan.Rect bounds, int floor) {
        public Site {
            stash = List.copyOf(stash); meals = List.copyOf(meals); residents = List.copyOf(residents);
            guestBeds = List.copyOf(guestBeds);
        }

        /** The feet height people stand at. */
        public int feetY() {
            return UrbanDatum.slabY(floor) + 1;
        }

        /**
         * Whether a feet cell lies inside the site: a camp's held storeys (its ground floor and
         * the guest storey above), or a refuge's sealed room.
         */
        public boolean holds(int x, int y, int z) {
            if (!bounds.contains(x, z)) { return false; }
            int feet = feetY();
            return kind == Kind.CAMP ? y >= feet && y < feet + 2 * UrbanDatum.STOREY_PITCH : y == feet;
        }

        /** The resident in a slot, or null. */
        public Resident resident(int slot) {
            return slot >= 0 && slot < residents.size() ? residents.get(slot) : null;
        }
    }

    private static final String[] NAMES = {
        "Ada", "Bram", "Cleo", "Dov", "Esme", "Faris", "Greta", "Hollis", "Ines", "Jory", "Kasia", "Lorne",
        "Mira", "Nils", "Oona", "Pavel", "Quill", "Rosa", "Silas", "Tamsin", "Ugo", "Vera", "Wim", "Xenia",
        "Yusra", "Zev", "Anouk", "Bastien", "Calla", "Desmond", "Elif", "Fenna", "Gideon", "Hedda", "Ilse",
        "Jonah", "Kaito", "Linnea", "Matteo", "Noor", "Orla", "Priya", "Ronan", "Saskia", "Teodor", "Una",
        "Viggo", "Wren"
    };

    private final Site camp;
    private final Site refuge;

    private SurvivorSites(Site camp, Site refuge) {
        this.camp = camp;
        this.refuge = refuge;
    }

    /** The region's camp, or null. */
    public Site camp() { return camp; }

    /** The region's hidden refuge, or null. */
    public Site refuge() { return refuge; }

    /** Every site of the region: the camp first, then the refuge. */
    public List<Site> sites() {
        List<Site> out = new ArrayList<>(2);
        if (camp != null) { out.add(camp); }
        if (refuge != null) { out.add(refuge); }
        return out;
    }

    /** The site a building id names in a layout-twelve world, or null when it names none. */
    public static Site resolve(UrbanWorldState state, long id) {
        if (state == null || !state.hasSurvivors() || !UrbanIds.isValid(id)
                || UrbanIds.kind(id) != UrbanIds.Kind.BUILDING) {
            return null;
        }
        int rx = UrbanIds.regionX(id), rz = UrbanIds.regionZ(id);
        if (!CityPlanner.isPlannable(rx, rz)) { return null; }
        for (Site s : state.sites(rx, rz).sites()) {
            if (s.id() == id) { return s; }
        }
        return null;
    }

    /** Plans one region. Any district plan outside layout twelve's six districts has no sites. */
    public static SurvivorSites of(long seed, DistrictPlan plan, UrbanWorldState state) {
        if (!state.hasSurvivors() || plan.district == null) {
            return new SurvivorSites(null, null);
        }
        RouteCatalogue routes = state.routes(plan.rx, plan.rz);
        return new SurvivorSites(camp(seed, plan, state, routes), refuge(seed, plan, state, routes));
    }

    // ---- the camp -------------------------------------------------------------------------

    private static Site camp(long seed, DistrictPlan plan, UrbanWorldState state, RouteCatalogue routes) {
        if (plan.district == UrbanDistrict.QUARANTINE) { return null; }
        List<ParcelPlan> candidates = new ArrayList<>();
        for (ParcelPlan p : plan.parcels()) {
            RouteCatalogue.Expect e = routes.expected(p.index());
            if (e == null || p.index() == routes.hiddenCellar() || e.family() != BuildingPlan.Family.APARTMENT
                    || e.variant() != BuildingPlan.Variant.OCCUPIED) {
                continue;
            }
            candidates.add(p);
        }
        candidates.sort(Comparator.comparing((ParcelPlan p) -> UrbanHash.mix(seed, LAYOUT, UrbanSalts.SITE_CAMP,
                p.id(), 0, 0), Long::compareUnsigned));
        for (int i = 0; i < candidates.size() && i < CAMP_ATTEMPTS; i++) {
            ParcelPlan p = candidates.get(i);
            BuildingPlan building = state.buildingAt(p.x0(), p.z0());
            // The finite repair can replace a held candidate with an intact one: only a held plan is a camp.
            if (building == null || building.variant() != BuildingPlan.Variant.OCCUPIED
                    || building.family() != BuildingPlan.Family.APARTMENT) {
                continue;
            }
            Site site = campLayout(seed, building);
            if (site != null) { return site; }
        }
        return null;
    }

    private static Site campLayout(long seed, BuildingPlan b) {
        Floor ground = floor(b, 0);
        if (ground == null) { return null; }
        int y = ground.slabY() + 1;
        Set<Vec3i> reserved = reserved(b);
        Set<Vec3i> furniture = fixtureCells(b);
        // The rear entrance opens wherever the parcel put it, often into a back room's corner, and
        // a shelf may stand behind one of its two leaves: the way in is the leaf with a clear cell.
        Vec3i inside = null, post = null;
        for (Fixture leaf : rearLeaves(b)) {
            Vec3i p = leaf.position(), t = leaf.target();
            Vec3i in = new Vec3i(2 * p.x() - t.x(), y, 2 * p.z() - t.z());
            if (!open(b.voxels(), in) || furniture.contains(in)) { continue; }
            Vec3i deeper = new Vec3i(3 * p.x() - 2 * t.x(), y, 3 * p.z() - 2 * t.z());
            inside = in;
            post = open(b.voxels(), deeper) && !furniture.contains(deeper) ? deeper : in;
            break;
        }
        if (inside == null) { return null; }
        Vec3i door = inside;
        reserved.add(post);
        reserved.add(inside);

        List<Vec3i> sleeping = new ArrayList<>();
        List<Vec3i> spare = new ArrayList<>();
        for (Room room : ground.rooms()) {
            if (room.functions().contains(Function.ENTRY) || room.functions().contains(Function.CORRIDOR_STAIRS)) {
                continue;
            }
            boolean bedroom = room.functions().contains(Function.BEDROOM) || room.functions().contains(Function.LIVING);
            (bedroom ? sleeping : spare).addAll(cells(b, room, y, reserved, bedroom));
        }
        Comparator<Vec3i> fromDoor = Comparator.comparingInt((Vec3i c) -> -manhattan(c, door))
                .thenComparingInt(Vec3i::x).thenComparingInt(Vec3i::z);
        List<Vec3i> all = new ArrayList<>(sleeping);
        all.addAll(spare);
        if (all.size() < MIN_CAMP + 1) { return null; }
        all.sort(fromDoor);
        // The farthest cell from the rear door is where the frightened gather; beds come from the
        // bedrooms and living rooms first, then the other rooms, farthest first.
        Vec3i safe = all.getFirst();
        sleeping.remove(safe);
        spare.remove(safe);
        sleeping.sort(fromDoor);
        spare.sort(fromDoor);
        List<Vec3i> ordered = new ArrayList<>(sleeping);
        ordered.addAll(spare);
        int people = Math.min(UrbanHash.range(MIN_CAMP, MAX_CAMP, seed, LAYOUT, UrbanSalts.SITE_PEOPLE, b.id(), 0, 0),
                ordered.size());
        List<Vec3i> bedCells = ordered.subList(0, people);
        List<Vec3i> dayCells = new ArrayList<>(ordered.subList(people, ordered.size()));

        List<Fixture> stash = stash(b, 0);
        if (stash.isEmpty()) { return null; }
        List<Resident> residents = new ArrayList<>();
        Set<String> names = new HashSet<>();
        List<Vec3i> kept = new ArrayList<>(List.of(post, inside, safe));
        for (int slot = 0; slot < people; slot++) {
            Role role = slot == 0 ? Role.LOOKOUT : slot == 1 ? Role.TRADER
                    : slot == 2 && people >= 4 ? Role.MEDIC : Role.FORAGER;
            Vec3i bed = bedCells.get(slot);
            Vec3i day = role == Role.LOOKOUT ? post : dayCell(dayCells, kept, door, role == Role.TRADER);
            if (day == null) { day = bed; }
            kept.add(day);
            long id = UrbanIds.resident(b.id(), slot, 0);
            residents.add(new Resident(id, slot, role, name(seed, id, names), bed, day));
        }
        Floor upstairs = floor(b, 1);
        List<Vec3i> guests = new ArrayList<>();
        if (upstairs != null && b.storeys() > 1) {
            int uy = upstairs.slabY() + 1;
            for (Room room : upstairs.rooms()) {
                if (room.functions().contains(Function.ENTRY) || room.functions().contains(Function.CORRIDOR_STAIRS)) {
                    continue;
                }
                guests.addAll(cells(b, room, uy, reserved, false));
            }
            guests.sort(Comparator.comparingInt(Vec3i::x).thenComparingInt(Vec3i::z));
        }
        if (guests.size() > GUEST_BEDS) { guests = new ArrayList<>(guests.subList(0, GUEST_BEDS)); }
        Room hall = room(ground, 0);
        Vec3i anchor = hall != null ? hall.anchor() : safe;
        return new Site(b.id(), Kind.CAMP, UrbanIds.NONE, anchor, inside, safe, post, positions(stash), targets(stash),
                residents, guests, rect(b.parcel()), 0);
    }

    /**
     * Where a camp member works by day, kept apart from the others: the trader nearest the way in
     * but out of its lane, everyone else on the free cell farthest from those already placed.
     * Removes the cell it returns; null when none is left.
     */
    private static Vec3i dayCell(List<Vec3i> free, List<Vec3i> kept, Vec3i door, boolean trader) {
        Vec3i best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Vec3i c : free) {
            int score;
            if (trader) {
                int d = manhattan(c, door);
                score = d >= 3 ? -d : -1000 - d;
            } else {
                score = Integer.MAX_VALUE;
                for (Vec3i k : kept) { score = Math.min(score, manhattan(c, k)); }
            }
            if (best == null || score > bestScore
                    || score == bestScore && (c.x() < best.x() || c.x() == best.x() && c.z() < best.z())) {
                best = c;
                bestScore = score;
            }
        }
        if (best != null) { free.remove(best); }
        return best;
    }

    /** Both leaves of the rear entrance, the ground hall's fixture slots nine and eleven, in that order. */
    private static List<Fixture> rearLeaves(BuildingPlan b) {
        Floor ground = floor(b, 0);
        Room hall = ground == null ? null : room(ground, 0);
        List<Fixture> out = new ArrayList<>(2);
        if (hall == null) { return out; }
        for (int slot : new int[]{9, 11}) {
            for (Fixture f : b.fixtures()) {
                if (f.kind() == FixtureKind.DOOR && UrbanIds.parent(f.id()) == hall.id()
                        && UrbanIds.slotIndex(f.id()) == slot) {
                    out.add(f);
                }
            }
        }
        return out;
    }

    /** Every cell a fixture fills, whatever its kind. */
    private static Set<Vec3i> fixtureCells(BuildingPlan b) {
        Set<Vec3i> out = new HashSet<>();
        for (Fixture f : b.fixtures()) {
            for (int h = 0; h < Math.max(1, f.height()); h++) { out.add(f.position().offset(0, h, 0)); }
        }
        return out;
    }

    // ---- the hidden refuge -----------------------------------------------------------------

    private static Site refuge(long seed, DistrictPlan plan, UrbanWorldState state, RouteCatalogue routes) {
        int index = routes.hiddenCellar();
        if (index < 0) { return null; }
        ParcelPlan parcel = plan.parcel(index);
        BuildingPlan b = state.buildingAt(parcel.x0(), parcel.z0());
        Floor cellarFloor = b == null ? null : floor(b, -1);
        if (cellarFloor == null) { return null; }
        Room cellar = null;
        for (Room r : cellarFloor.rooms()) {
            if (r.functions().contains(Function.HIDDEN_REFUGE)) { cellar = r; }
        }
        Fixture panel = null;
        for (Fixture f : b.fixtures()) {
            if (f.block() == BlockType.HIDDEN_PANEL && cellar != null && UrbanIds.parent(f.id()) == cellar.id()) { panel = f; }
        }
        if (cellar == null || panel == null) { return null; }
        int y = cellarFloor.slabY() + 1;
        Vec3i entry = panel.target();
        Set<Vec3i> reserved = reserved(b);
        // People rest away from the partitions: a body one wall from the basement hall could be
        // reached across it, and the panel's own lane stays clear for whoever slides it aside.
        List<Vec3i> rest = new ArrayList<>();
        for (Vec3i c : cells(b, cellar, y, reserved, true)) {
            if (!acrossOneWall(b.voxels(), cellar.interior(), c)) { rest.add(c); }
        }
        rest.sort(Comparator.comparingInt((Vec3i c) -> -manhattan(c, entry)).thenComparingInt(Vec3i::x)
                .thenComparingInt(Vec3i::z));
        if (rest.size() < MIN_REFUGE + 1) { return null; }
        int people = Math.min(UrbanHash.range(MIN_REFUGE, MAX_REFUGE, seed, LAYOUT, UrbanSalts.SITE_PEOPLE, b.id(), 1, 0),
                rest.size() - 1);
        Vec3i safe = rest.getFirst();
        List<Resident> residents = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int slot = 0; slot < people; slot++) {
            Vec3i bed = rest.get(1 + slot);
            long id = UrbanIds.resident(b.id(), slot, 0);
            residents.add(new Resident(id, slot, Role.CIVILIAN, name(seed, id, names), bed, bed));
        }
        List<Fixture> stash = new ArrayList<>();
        for (Fixture f : b.fixtures()) {
            if (f.kind() == FixtureKind.CONTAINER && UrbanIds.parent(f.id()) == cellar.id()) { stash.add(f); }
        }
        stash.sort(Comparator.comparingLong(Fixture::id));
        if (stash.isEmpty()) { return null; }
        return new Site(b.id(), Kind.REFUGE, cellar.id(), cellar.anchor(), entry, safe, null, positions(stash),
                targets(stash), residents, List.of(), cellar.interior(), -1);
    }

    // ---- planned cells ---------------------------------------------------------------------

    /**
     * The cells of a room a body can stand on in the frozen plan: a floor under the feet, headroom,
     * no fixture in either cell, no water, and nothing reserved. Camp beds and bedrolls (open
     * furniture) count when {@code beds} allows them.
     */
    private static List<Vec3i> cells(BuildingPlan b, Room room, int y, Set<Vec3i> reserved, boolean beds) {
        List<Vec3i> out = new ArrayList<>();
        ParcelPlan.Rect r = room.interior();
        for (int z = r.z0(); z <= r.z1(); z++) {
            for (int x = r.x0(); x <= r.x1(); x++) {
                Vec3i c = new Vec3i(x, y, z);
                BlockType feet = b.voxels().getBlock(x, y, z);
                boolean cot = beds && (feet == BlockType.CAMP_BED || feet == BlockType.BEDROLL);
                if ((cot || !reserved.contains(c)) && open(b.voxels(), c)) { out.add(c); }
            }
        }
        return out;
    }

    /** Standable in the plan with no door, window or panel in the feet or head cell, and dry. */
    private static boolean open(PlanVolume v, Vec3i c) {
        BlockType feet = v.getBlock(c.x(), c.y(), c.z()), head = v.getBlock(c.x(), c.y() + 1, c.z());
        return VoxelClearance.standable(v, c.x(), c.y(), c.z()) && Fixtures.kindOf(feet) == null
                && Fixtures.kindOf(head) == null && feet != BlockType.WATER && feet != BlockType.STAIRS;
    }

    /** Fixture cells, the cells they are used from, and the lanes beside every door and panel. */
    private static Set<Vec3i> reserved(BuildingPlan b) {
        Set<Vec3i> out = new HashSet<>();
        for (Fixture f : b.fixtures()) {
            for (int h = 0; h < Math.max(1, f.height()); h++) { out.add(f.position().offset(0, h, 0)); }
            out.add(f.target());
            if (f.kind() == FixtureKind.DOOR) {
                Vec3i p = f.position();
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) { out.add(p.offset(d[0], 0, d[1])); }
            }
        }
        return out;
    }

    private static List<Fixture> stash(BuildingPlan b, int floor) {
        List<Fixture> kitchen = new ArrayList<>(), any = new ArrayList<>();
        for (Fixture f : b.fixtures()) {
            if (f.kind() != FixtureKind.CONTAINER || UrbanIds.floorIndex(f.id()) != floor) { continue; }
            any.add(f);
            BuildingFurnishing.ContainerUse use = BuildingFurnishing.containerUse(b, f);
            if (use == BuildingFurnishing.ContainerUse.KITCHEN_CABINET || use == BuildingFurnishing.ContainerUse.FRIDGE
                    || use == BuildingFurnishing.ContainerUse.PANTRY_SHELF) {
                kitchen.add(f);
            }
        }
        List<Fixture> out = kitchen.isEmpty() ? any : kitchen;
        out.sort(Comparator.comparingLong(Fixture::id));
        return out;
    }

    private static List<Vec3i> positions(List<Fixture> fixtures) {
        return fixtures.stream().map(Fixture::position).toList();
    }

    private static List<Vec3i> targets(List<Fixture> fixtures) {
        return fixtures.stream().map(Fixture::target).toList();
    }

    private static Floor floor(BuildingPlan b, int index) {
        for (Floor f : b.floors()) {
            if (f.index() == index) { return f; }
        }
        return null;
    }

    private static Room room(Floor floor, int index) {
        for (Room r : floor.rooms()) {
            if (UrbanIds.roomIndex(r.id()) == index) { return r; }
        }
        return null;
    }

    private static ParcelPlan.Rect rect(ParcelPlan p) {
        return new ParcelPlan.Rect(p.x0(), p.z0(), p.x1(), p.z1());
    }

    private static int manhattan(Vec3i a, Vec3i b) {
        return Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z());
    }

    /**
     * Whether a cell of a sealed room lies just one wall from a cell outside it that a body can
     * stand on: within two blocks horizontally, where a hand or a voice reaches across.
     */
    private static boolean acrossOneWall(PlanVolume v, ParcelPlan.Rect room, Vec3i c) {
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                int x = c.x() + dx, z = c.z() + dz;
                if (room.contains(x, z) || dx * dx + dz * dz > 4) { continue; }
                if (VoxelClearance.standable(v, x, c.y(), z)) { return true; }
            }
        }
        return false;
    }

    /** A resident's name: a pure pick from an original list, kept unique inside the site. */
    private static String name(long seed, long id, Set<String> taken) {
        int start = UrbanHash.range(0, NAMES.length - 1, seed, LAYOUT, UrbanSalts.SITE_NAME, id, 0, 0);
        for (int i = 0; i < NAMES.length; i++) {
            String n = NAMES[(start + i) % NAMES.length];
            if (taken.add(n)) { return n; }
        }
        throw new IllegalStateException("more residents than names");
    }
}
