package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.urban.BuildingPlan.Family;
import com.veylon.world.urban.BuildingPlan.Fixture;
import com.veylon.world.urban.BuildingPlan.FixtureKind;
import com.veylon.world.urban.BuildingPlan.Floor;
import com.veylon.world.urban.BuildingPlan.Function;
import com.veylon.world.urban.BuildingPlan.Room;
import com.veylon.world.urban.ParcelPlan.Rect;
import com.veylon.world.urban.SurvivorSites.Resident;
import com.veylon.world.urban.SurvivorSites.Role;
import com.veylon.world.urban.SurvivorSites.Site;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The guarded compounds of layout thirteen (urban milestone 17), planned from a region's
 * {@link CompoundClaims} and the frozen plans of the buildings they hold. Pure, like every site
 * plan: chunk order, cache eviction and the order sites are met never change it.
 *
 * <ul>
 *   <li><b>A stronghold</b> holds a civic courtyard block. Its six buildings, all held, make the
 *       wall: their street doors are locked and their rear doors open onto the courtyard. One
 *       passage carries the gate, a pair of metal leaves {@value #PORCH} cells in from the street
 *       beside a solid post with the sign; the other passage is barricaded at the street. Inside:
 *       the gate's two posts, a post at the barricade, a patrol round the courtyard walkway, the
 *       trader's pitch by the gate, the medic in the clinic, barracks in the apartments.</li>
 *   <li><b>A raider yard</b> holds a depot row. The service alley between its two rows is the
 *       yard, gated at one end and barricaded at the other; the raiders hold every lot but the
 *       back way, an unheld building whose street door is open and whose rear door opens into the
 *       yard. A sentry stands on the street outside the gate, another inside, one walks the alley.</li>
 * </ul>
 *
 * <p>Every cell a person uses is a planned standing cell: inside a building, a cell of its frozen
 * voxels with a floor and headroom and no fixture, door lane or water; outside, a courtyard,
 * passage or alley cell with nothing planned on it, never a tree bed. A compound whose buildings
 * did not all come out held (the finite repair can plan an intact building instead) is not a
 * compound: no barricade is stamped and nobody lives there.
 */
public final class CompoundSites {
    public static final int MIN_STRONGHOLD = 8, MAX_STRONGHOLD = 16, MIN_RAIDERS = 4, MAX_RAIDERS = 8;
    /** Passage or alley cells between the street and a gate: the porch a gate guard keeps. */
    public static final int PORCH = 2;
    /** Barricades stand three courses high: the feet cell and the two above it. */
    public static final int HEIGHT = 3;
    /** Stash containers a compound's people eat from, at most. */
    public static final int MAX_STASH = 12;
    /** Feet height on the street datum. */
    static final int FEET = UrbanDatum.STREET_Y + 1;
    private static final int LAYOUT = UrbanRevision.LAYOUT_STRONGHOLDS;

    /** Where a cell lies with respect to a compound: what a stronghold's guards allow depends on it. */
    public enum Zone {
        /** Not the compound's: the street, a neighbour, or a raider yard's back way. */
        OUTSIDE,
        /** The passage or alley cells between the street and the gate. */
        PORCH,
        /** The courtyard or alley inside the gate, with the inner ends of the passages. */
        YARD,
        /** A stronghold clinic's ground floor, where visitors may see the medic. */
        GUEST,
        /** Every other storey of every held building. */
        RESTRICTED
    }

    /**
     * A gate: two metal leaves and a solid post in one barricade line, at feet height. The guard
     * of the gate stands {@code outside} or {@code inside}, beside the post; the lanes in front of
     * the leaves are kept clear.
     */
    public record Gate(Vec3i first, Vec3i second, Vec3i post, Vec3i outside, Vec3i inside,
                       Vec3i laneOutside, Vec3i laneInside, ParcelPlan.Side outward) {
        /** True on either leaf's column. */
        public boolean column(int x, int z) {
            return x == first.x() && z == first.z() || x == second.x() && z == second.z();
        }

        /** True for a leaf cell: the leaves are two cells high. */
        public boolean leaf(int x, int y, int z) {
            return column(x, z) && y >= FEET && y < FEET + 2;
        }

        public List<Vec3i> leaves() {
            return List.of(first, second);
        }
    }

    /** One barricade line, three cells across a passage or alley mouth at feet height. */
    public record Barricade(List<Vec3i> cells, boolean gated) {
        public Barricade {
            cells = List.copyOf(cells);
        }
    }

    /**
     * One planned compound.
     *
     * @param site      the site record: its id is the first member's building id
     * @param members   the buildings it holds, in parcel order
     * @param lots      their parcels, in the same order
     * @param backWay   a raider yard's unheld back way, {@link UrbanIds#NONE} for a stronghold
     * @param backLot   the back way's parcel, or null
     * @param backDoor  the yard cell outside the back way's rear door, or null
     * @param yard      the open ground inside the gate
     * @param porch     the passage or alley cells between the street and the gate
     * @param barricades the gate line first, then the closed line
     * @param gate      the gate
     * @param sign      the sign cell over the gate post, one course below its top
     * @param signText  the sign's words
     * @param patrol    the patrol's waypoints, walked in order and round again
     * @param clinic    a stronghold's clinic building, or {@link UrbanIds#NONE}
     * @param market    a stronghold trader's pitch in the courtyard, or null
     * @param posts     every fixed post, outside the gate first
     */
    public record Compound(Site site, List<Long> members, List<Rect> lots, long backWay, Rect backLot,
                           Vec3i backDoor, List<Rect> yard, Rect porch, List<Barricade> barricades, Gate gate,
                           Vec3i sign, String signText, List<Vec3i> patrol, long clinic, Vec3i market,
                           List<Vec3i> posts) {
        public Compound {
            members = List.copyOf(members); lots = List.copyOf(lots); yard = List.copyOf(yard);
            barricades = List.copyOf(barricades); patrol = List.copyOf(patrol); posts = List.copyOf(posts);
        }

        public SurvivorSites.Kind kind() {
            return site.kind();
        }

        public boolean stronghold() {
            return site.kind() == SurvivorSites.Kind.STRONGHOLD;
        }

        /** The held building whose lot holds a column, or {@link UrbanIds#NONE}. */
        public long memberAt(int x, int z) {
            for (int i = 0; i < lots.size(); i++) {
                if (lots.get(i).contains(x, z)) { return members.get(i); }
            }
            return UrbanIds.NONE;
        }

        public boolean inYard(int x, int z) {
            for (Rect r : yard) {
                if (r.contains(x, z)) { return true; }
            }
            return false;
        }

        /** Where a feet cell lies with respect to this compound. */
        public Zone zoneAt(int x, int y, int z) {
            boolean ground = y >= FEET - 1 && y <= FEET + HEIGHT;
            if (ground && porch.contains(x, z)) { return Zone.PORCH; }
            if (ground && inYard(x, z)) { return Zone.YARD; }
            long member = memberAt(x, z);
            if (member == UrbanIds.NONE || !site.holds(x, y, z)) { return Zone.OUTSIDE; }
            return stronghold() && member == clinic && y >= FEET && y < FEET + UrbanDatum.STOREY_PITCH
                    ? Zone.GUEST : Zone.RESTRICTED;
        }

        /** Inside the gate: the yard or one of the held buildings. */
        public boolean inside(int x, int y, int z) {
            Zone zone = zoneAt(x, y, z);
            return zone == Zone.YARD || zone == Zone.GUEST || zone == Zone.RESTRICTED;
        }
    }

    private CompoundSites() {
    }

    /** Plans the compound a claim names, or null for no claim or a claim whose buildings are not all held. */
    public static Compound plan(long seed, DistrictPlan district, UrbanWorldState state, CompoundClaims.Claim claim) {
        if (claim == null) { return null; }
        return claim.kind() == SurvivorSites.Kind.STRONGHOLD ? stronghold(seed, district, state, claim)
                : raiders(seed, district, state, claim);
    }

    // ---- the stronghold ---------------------------------------------------------------------

    private static Compound stronghold(long seed, DistrictPlan district, UrbanWorldState state, CompoundClaims.Claim claim) {
        SuperblockPlan block = district.superblock(claim.superblock());
        Rect court = block.open();
        List<ParcelPlan> ring = CompoundClaims.parcelsOf(district, block.index());
        List<BuildingPlan> held = heldBuildings(state, ring);
        if (held == null || court == null) { return null; }
        ParcelPlan nw = CompoundClaims.slot(ring, CityPlanner.SLOT_NORTH_WEST), ne = CompoundClaims.slot(ring, CityPlanner.SLOT_NORTH_EAST);
        ParcelPlan sw = CompoundClaims.slot(ring, CityPlanner.SLOT_SOUTH_WEST), se = CompoundClaims.slot(ring, CityPlanner.SLOT_SOUTH_EAST);
        Rect north = new Rect(nw.x1() + 1, nw.z0(), ne.x0() - 1, nw.z1());
        Rect south = new Rect(sw.x1() + 1, sw.z0(), se.x0() - 1, sw.z1());
        boolean gateNorth = claim.gateMouth() == 0;
        Rect gated = gateNorth ? north : south, closed = gateNorth ? south : north;
        int out = gateNorth ? -1 : 1;
        int gz = gateNorth ? gated.z0() + PORCH : gated.z1() - PORCH;
        int px = gated.x0();
        Gate gate = new Gate(v(px, gz), v(px + 1, gz), v(px + 2, gz), v(px + 2, gz + out), v(px + 2, gz - out),
                v(px, gz + out), v(px, gz - out), gateNorth ? ParcelPlan.Side.NORTH : ParcelPlan.Side.SOUTH);
        int cz = gateNorth ? closed.z1() : closed.z0();
        List<Barricade> barricades = List.of(line(px, gz, true, true),
                line(closed.x0(), cz, true, false));
        Rect porch = gateNorth ? new Rect(px, gated.z0(), px + 2, gz - 1) : new Rect(px, gz + 1, px + 2, gated.z1());
        Rect gatedInner = gateNorth ? new Rect(px, gz + 1, px + 2, gated.z1()) : new Rect(px, gated.z0(), px + 2, gz - 1);
        Rect closedInner = gateNorth ? new Rect(closed.x0(), closed.z0(), closed.x1(), cz - 1)
                : new Rect(closed.x0(), cz + 1, closed.x1(), closed.z1());
        List<Rect> yard = List.of(court, gatedInner, closedInner);
        Set<Vec3i> doors = doorways(ring);

        Vec3i barricadePost = gateNorth ? v(closed.x0() + 1, closed.z0()) : v(closed.x0() + 1, closed.z1());
        List<Vec3i> patrol = new ArrayList<>();
        for (Vec3i c : List.of(v(court.x0(), court.z0()), v(court.x1(), court.z0()),
                v(court.x1(), court.z1()), v(court.x0(), court.z1()))) {
            if (yardCell(district, c) && !doors.contains(c)) { patrol.add(c); }
        }
        int edge = gateNorth ? court.z0() : court.z1();
        int step = gateNorth ? 1 : -1;
        Vec3i market = null;
        for (Vec3i c : List.of(v(px + 5, edge), v(px - 3, edge), v(px + 5, edge + step), v(px - 3, edge + step),
                v(px + 6, edge + step), v(px - 4, edge + step))) {
            if (yardCell(district, c) && !doors.contains(c)) { market = c; break; }
        }
        for (Vec3i c : List.of(gate.outside(), gate.inside(), gate.laneOutside(), gate.laneInside(), barricadePost)) {
            if (!yardCell(district, c)) { return null; }
        }
        if (patrol.size() < 2 || market == null) { return null; }

        long id = held.getFirst().id();
        BuildingPlan clinic = null;
        for (BuildingPlan b : held) {
            if (b.family() == Family.CLINIC && clinic == null) { clinic = b; }
        }
        if (clinic == null) { return null; }
        // Barracks: the apartments' bedrooms and living rooms first, then their other rooms, then
        // the civic buildings' street floors; farthest from the gate first in each.
        Vec3i gateCell = gate.laneInside();
        List<RoomCells> rooms = new ArrayList<>();
        for (BuildingPlan b : held) {
            boolean walkUp = b.family().walkUp();
            Set<Vec3i> reserved = SurvivorSites.reserved(b);
            for (Floor f : b.floors()) {
                if (f.index() < 0 || f.index() >= b.storeys() || !walkUp && f.index() > 0) { continue; }
                for (Room room : f.rooms()) {
                    if (!living(room)) { continue; }
                    boolean bedroom = room.functions().contains(Function.BEDROOM) || room.functions().contains(Function.LIVING);
                    rooms.add(new RoomCells(walkUp ? bedroom ? 0 : 1 : 2, room.anchor(),
                            SurvivorSites.cells(b, room, f.slabY() + 1, reserved, true)));
                }
            }
        }
        Vec3i safe = safeCell(rooms, gateCell);
        if (safe == null) { return null; }
        List<Vec3i> beds = beds(rooms, gateCell, safe, MAX_STRONGHOLD);
        int people = Math.min(UrbanHash.range(MIN_STRONGHOLD, MAX_STRONGHOLD, seed, LAYOUT, UrbanSalts.COMPOUND_PEOPLE, id, 0, 0),
                beds.size());
        if (people < MIN_STRONGHOLD) { return null; }
        beds = new ArrayList<>(beds.subList(0, people));
        List<Vec3i> workCells = groundCells(held, beds);
        Vec3i medicCell = clinicCell(clinic, beds);
        if (medicCell == null) { return null; }

        List<Fixture> stash = stash(held, true);
        if (stash.isEmpty()) { return null; }
        List<Resident> residents = new ArrayList<>();
        Set<String> names = new HashSet<>();
        List<Vec3i> kept = new ArrayList<>(List.of(gate.outside(), gate.inside(), barricadePost, market, medicCell, safe));
        int guards = 4 + (people >= 11 ? 1 : 0);
        for (int slot = 0; slot < people; slot++) {
            Role role;
            Vec3i day;
            switch (slot) {
                case 0 -> { role = Role.GATEKEEPER; day = gate.outside(); }
                case 1 -> { role = Role.SENTRY; day = gate.inside(); }
                case 2 -> { role = Role.PATROL; day = patrol.getFirst(); }
                case 3 -> { role = Role.SENTRY; day = barricadePost; }
                case 4 -> { role = Role.TRADER; day = market; }
                case 5 -> { role = Role.MEDIC; day = medicCell; }
                default -> {
                    if (slot == 6 && guards > 4) {
                        role = Role.PATROL;
                        day = patrol.get(patrol.size() / 2);
                    } else {
                        role = Role.FORAGER;
                        day = spread(workCells, kept);
                    }
                }
            }
            if (day == null) { day = beds.get(slot); }
            kept.add(day);
            long resident = UrbanIds.resident(id, slot, 0);
            residents.add(new Resident(resident, slot, role, SurvivorSites.name(seed, resident, names), beds.get(slot), day));
        }
        Vec3i anchor = v((court.x0() + court.x1()) / 2, (court.z0() + court.z1()) / 2);
        Site site = new Site(id, SurvivorSites.Kind.STRONGHOLD, UrbanIds.NONE, anchor, gate.laneInside(), safe,
                gate.outside(), SurvivorSites.positions(stash), SurvivorSites.targets(stash), residents, List.of(),
                block.bounds(), 0);
        String sign = CivicCatalogue.colonyName(seed, id) + " Ward. Visitors by day: ask at the gate.";
        return new Compound(site, ids(held), lots(ring), UrbanIds.NONE, null, null, yard, porch, barricades, gate,
                gate.post().offset(0, HEIGHT - 1, 0), sign, patrol, clinic.id(), market,
                List.of(gate.outside(), gate.inside(), barricadePost));
    }

    // ---- the raider yard --------------------------------------------------------------------

    private static Compound raiders(long seed, DistrictPlan district, UrbanWorldState state, CompoundClaims.Claim claim) {
        SuperblockPlan block = district.superblock(claim.superblock());
        Rect alley = block.open();
        List<ParcelPlan> row = CompoundClaims.parcelsOf(district, block.index());
        List<ParcelPlan> memberLots = new ArrayList<>();
        ParcelPlan backLot = null;
        for (ParcelPlan p : row) {
            if (p.index() == claim.backWay()) { backLot = p; } else { memberLots.add(p); }
        }
        List<BuildingPlan> held = heldBuildings(state, memberLots);
        BuildingPlan back = backLot == null ? null : state.buildingAt(backLot.x0(), backLot.z0());
        if (held == null || back == null || back.variant() == BuildingPlan.Variant.OCCUPIED || !openBothWays(back)) {
            return null;
        }
        boolean gateWest = claim.gateMouth() == 0;
        int out = gateWest ? -1 : 1;
        int gx = gateWest ? alley.x0() + PORCH : alley.x1() - PORCH;
        int fx = gateWest ? alley.x1() : alley.x0();
        int z0 = alley.z0();
        Gate gate = new Gate(v(gx, z0), v(gx, z0 + 1), v(gx, z0 + 2), v(gx + out, z0 + 2), v(gx - out, z0 + 2),
                v(gx + out, z0), v(gx - out, z0), gateWest ? ParcelPlan.Side.WEST : ParcelPlan.Side.EAST);
        List<Barricade> barricades = List.of(line(gx, z0, false, true), line(fx, z0, false, false));
        Rect porch = gateWest ? new Rect(alley.x0(), z0, gx - 1, alley.z1()) : new Rect(gx + 1, z0, alley.x1(), alley.z1());
        Rect inner = gateWest ? new Rect(gx + 1, z0, fx - 1, alley.z1()) : new Rect(fx + 1, z0, gx - 1, alley.z1());
        Set<Vec3i> doors = doorways(row);
        List<Vec3i> patrol = new ArrayList<>();
        // Inside the gate, the middle of the alley, and inside the far barricade (the yard lies
        // away from the gate's outward side, so the far end's inside is a step outward of it).
        for (Vec3i c : List.of(v(gx - 2 * out, z0 + 1), v((gx + fx) / 2, z0 + 1), v(fx + 2 * out, z0 + 1))) {
            if (yardCell(district, c) && !doors.contains(c)) { patrol.add(c); }
        }
        for (Vec3i c : List.of(gate.outside(), gate.inside(), gate.laneOutside(), gate.laneInside())) {
            if (!yardCell(district, c)) { return null; }
        }
        ParcelPlan.Entrance rear = CompoundClaims.rear(backLot);
        if (patrol.size() < 3 || rear == null) { return null; }
        Vec3i backDoor = v(rear.outsideX(), rear.outsideZ());

        long id = held.getFirst().id();
        Vec3i gateCell = gate.laneInside();
        List<RoomCells> rooms = new ArrayList<>();
        for (BuildingPlan b : held) {
            Set<Vec3i> reserved = SurvivorSites.reserved(b);
            for (Floor f : b.floors()) {
                if (f.index() < 0 || f.index() > 1 || f.index() >= b.storeys()) { continue; }
                for (Room room : f.rooms()) {
                    if (living(room)) {
                        rooms.add(new RoomCells(f.index(), room.anchor(), SurvivorSites.cells(b, room, f.slabY() + 1, reserved, true)));
                    }
                }
            }
        }
        Vec3i safe = safeCell(rooms, gateCell);
        if (safe == null) { return null; }
        List<Vec3i> beds = beds(rooms, gateCell, safe, MAX_RAIDERS);
        int people = Math.min(UrbanHash.range(MIN_RAIDERS, MAX_RAIDERS, seed, LAYOUT, UrbanSalts.COMPOUND_PEOPLE, id, 1, 0),
                beds.size());
        if (people < MIN_RAIDERS) { return null; }
        beds = new ArrayList<>(beds.subList(0, people));
        List<Vec3i> workCells = groundCells(held, beds);
        List<Fixture> stash = stash(held, false);
        if (stash.isEmpty() || workCells.isEmpty()) { return null; }
        List<Resident> residents = new ArrayList<>();
        Set<String> names = new HashSet<>();
        List<Vec3i> kept = new ArrayList<>(List.of(gate.outside(), gate.inside(), safe));
        Vec3i boss = nearest(workCells, stash.getFirst().target());
        for (int slot = 0; slot < people; slot++) {
            Role role;
            Vec3i day;
            switch (slot) {
                case 0 -> { role = Role.BOSS; day = boss; workCells.remove(boss); }
                case 1 -> { role = Role.SENTRY; day = gate.outside(); }
                case 2 -> { role = Role.PATROL; day = patrol.getFirst(); }
                case 3 -> { role = Role.SENTRY; day = gate.inside(); }
                default -> { role = Role.RAIDER; day = spread(workCells, kept); }
            }
            if (day == null) { day = beds.get(slot); }
            kept.add(day);
            long resident = UrbanIds.resident(id, slot, 0);
            residents.add(new Resident(resident, slot, role, SurvivorSites.name(seed, resident, names), beds.get(slot), day));
        }
        Vec3i anchor = v((inner.x0() + inner.x1()) / 2, z0 + 1);
        Site site = new Site(id, SurvivorSites.Kind.RAIDERS, UrbanIds.NONE, anchor, gate.laneInside(), safe,
                gate.outside(), SurvivorSites.positions(stash), SurvivorSites.targets(stash), residents, List.of(),
                block.bounds(), 0);
        return new Compound(site, ids(held), lots(memberLots), back.id(), SurvivorSites.rect(backLot), backDoor,
                List.of(inner), porch, barricades, gate, gate.post().offset(0, HEIGHT - 1, 0),
                "KEEP OUT. Wreckers hold this yard.", patrol, UrbanIds.NONE, null,
                residents.size() > 3 ? List.of(gate.outside(), gate.inside()) : List.of(gate.outside()));
    }

    // ---- planned cells ----------------------------------------------------------------------

    /** Beds a room holds at most, so a garrison sleeps spread over its rooms. */
    static final int BEDS_PER_ROOM = 4;

    /** The standing cells of one room, with how much its kind of room is preferred for sleeping (0 first). */
    private record RoomCells(int preference, Vec3i anchor, List<Vec3i> cells) { }

    /** The cell the frightened retreat to: the living-room cell farthest from the gate. */
    private static Vec3i safeCell(List<RoomCells> rooms, Vec3i gate) {
        Vec3i best = null;
        Comparator<Vec3i> farthest = farthestFrom(gate);
        for (RoomCells r : rooms) {
            for (Vec3i c : r.cells()) {
                if (best == null || farthest.compare(c, best) < 0) { best = c; }
            }
        }
        return best;
    }

    /**
     * Beds spread over rooms: preferred rooms first and, within a preference, the rooms farthest
     * from the gate; at most {@value #BEDS_PER_ROOM} a room and never two side by side, unless the
     * rooms run out, when a second pass fills them closer. At most {@code wanted}.
     */
    private static List<Vec3i> beds(List<RoomCells> rooms, Vec3i gate, Vec3i safe, int wanted) {
        List<RoomCells> order = new ArrayList<>(rooms);
        Comparator<Vec3i> farthest = farthestFrom(gate);
        order.sort(Comparator.comparingInt(RoomCells::preference)
                .thenComparing(RoomCells::anchor, farthest));
        List<Vec3i> out = new ArrayList<>();
        for (int pass = 0; pass < 2 && out.size() < wanted; pass++) {
            for (RoomCells room : order) {
                int inRoom = 0;
                for (Vec3i c : out) { if (room.cells().contains(c)) { inRoom++; } }
                List<Vec3i> cells = new ArrayList<>(room.cells());
                cells.sort(farthest);
                for (Vec3i c : cells) {
                    if (out.size() >= wanted || pass == 0 && inRoom >= BEDS_PER_ROOM) { break; }
                    if (c.equals(safe) || out.contains(c) || pass == 0 && besideAny(out, c)) { continue; }
                    out.add(c);
                    inRoom++;
                }
            }
        }
        return out;
    }

    private static boolean besideAny(List<Vec3i> cells, Vec3i c) {
        for (Vec3i o : cells) {
            if (o.y() == c.y() && Math.abs(o.x() - c.x()) <= 1 && Math.abs(o.z() - c.z()) <= 1) { return true; }
        }
        return false;
    }

    /** The planned buildings of some lots, or null unless every one of them came out held. */
    private static List<BuildingPlan> heldBuildings(UrbanWorldState state, List<ParcelPlan> lots) {
        List<BuildingPlan> out = new ArrayList<>();
        for (ParcelPlan p : lots) {
            BuildingPlan b = state.buildingAt(p.x0(), p.z0());
            // The finite repair can plan an intact building in a held one's place: then it is no wall.
            if (b == null || b.variant() != BuildingPlan.Variant.OCCUPIED) { return null; }
            out.add(b);
        }
        return out.isEmpty() ? null : out;
    }

    /**
     * A back way: both leaves of its street entrance (the ground hall's door slots 8 and 10) and
     * of its rear entrance (slots 9 and 11) stand unlocked in the plan.
     */
    private static boolean openBothWays(BuildingPlan b) {
        Floor ground = SurvivorSites.floor(b, 0);
        Room hall = ground == null ? null : SurvivorSites.room(ground, 0);
        if (hall == null) { return false; }
        int leaves = 0;
        for (Fixture f : b.fixtures()) {
            if (f.kind() != FixtureKind.DOOR || UrbanIds.parent(f.id()) != hall.id()) { continue; }
            int slot = UrbanIds.slotIndex(f.id());
            if (slot < 8 || slot > 11) { continue; }
            if (f.locked()) { return false; }
            leaves++;
        }
        return leaves == 4;
    }

    /** Rooms people may live in: not the entry hall, the stair core or a locked specialist room. */
    private static boolean living(Room room) {
        for (Function f : room.functions()) {
            if (f == Function.ENTRY || f == Function.CORRIDOR_STAIRS || f.secured()) { return false; }
        }
        return true;
    }

    /** Street-floor cells of the held buildings left over after the beds: where people work by day. */
    private static List<Vec3i> groundCells(List<BuildingPlan> held, List<Vec3i> beds) {
        List<Vec3i> out = new ArrayList<>();
        for (BuildingPlan b : held) {
            Set<Vec3i> reserved = SurvivorSites.reserved(b);
            Floor ground = SurvivorSites.floor(b, 0);
            if (ground == null) { continue; }
            for (Room room : ground.rooms()) {
                if (!living(room)) { continue; }
                for (Vec3i c : SurvivorSites.cells(b, room, ground.slabY() + 1, reserved, false)) {
                    if (!beds.contains(c)) { out.add(c); }
                }
            }
        }
        out.sort(Comparator.comparingInt(Vec3i::x).thenComparingInt(Vec3i::z));
        return out;
    }

    /** The medic's cell: the clinic's ground-floor treatment room, else any of its street-floor rooms. */
    private static Vec3i clinicCell(BuildingPlan clinic, List<Vec3i> beds) {
        Floor ground = SurvivorSites.floor(clinic, 0);
        if (ground == null) { return null; }
        Set<Vec3i> reserved = SurvivorSites.reserved(clinic);
        for (boolean treatment : new boolean[]{true, false}) {
            for (Room room : ground.rooms()) {
                if (!living(room) || treatment && !room.functions().contains(Function.CLINIC)) { continue; }
                for (Vec3i c : SurvivorSites.cells(clinic, room, ground.slabY() + 1, reserved, false)) {
                    if (!beds.contains(c)) { return c; }
                }
            }
        }
        return null;
    }

    /**
     * The stash: the held buildings' street-floor kitchen cupboards, fridges and pantries (a
     * stronghold), or any of their street-floor containers outside a locked room, in fixture-id
     * order, at most {@value #MAX_STASH}.
     */
    private static List<Fixture> stash(List<BuildingPlan> held, boolean kitchens) {
        List<Fixture> out = new ArrayList<>();
        for (BuildingPlan b : held) {
            Floor ground = SurvivorSites.floor(b, 0);
            if (ground == null) { continue; }
            for (Fixture f : b.fixtures()) {
                if (f.kind() != FixtureKind.CONTAINER || UrbanIds.floorIndex(f.id()) != 0) { continue; }
                Room room = roomOf(ground, f);
                if (room == null || !living(room)) { continue; }
                BuildingFurnishing.ContainerUse use = BuildingFurnishing.containerUse(b, f);
                boolean kitchen = use == BuildingFurnishing.ContainerUse.KITCHEN_CABINET
                        || use == BuildingFurnishing.ContainerUse.FRIDGE || use == BuildingFurnishing.ContainerUse.PANTRY_SHELF;
                if (!kitchens || kitchen) { out.add(f); }
            }
        }
        out.sort(Comparator.comparingLong(Fixture::id));
        return out.size() > MAX_STASH ? new ArrayList<>(out.subList(0, MAX_STASH)) : out;
    }

    private static Room roomOf(Floor floor, Fixture f) {
        for (Room r : floor.rooms()) {
            if (r.id() == UrbanIds.parent(f.id())) { return r; }
        }
        return null;
    }

    /** The cells outside every rear door of a block, with the cells beside them: lanes kept clear. */
    private static Set<Vec3i> doorways(List<ParcelPlan> lots) {
        Set<Vec3i> out = new HashSet<>();
        for (ParcelPlan p : lots) {
            for (ParcelPlan.Entrance e : p.entrances()) {
                int ox = e.facing().dx, oz = e.facing().dz;
                for (int[] cell : new int[][]{{e.x(), e.z()}, {e.x2(), e.z2()}}) {
                    for (int a = -1; a <= 1; a++) {
                        for (int b = 0; b <= 1; b++) {
                            int x = cell[0] + ox * (1 + b) + (ox == 0 ? a : 0);
                            int z = cell[1] + oz * (1 + b) + (oz == 0 ? a : 0);
                            out.add(v(x, z));
                        }
                    }
                }
            }
        }
        return out;
    }

    /**
     * Open ground a body can stand on at the street datum: a courtyard, passage or alley cell with
     * no parcel, tree or overlay on it.
     */
    static boolean yardCell(DistrictPlan district, Vec3i c) {
        int x = c.x(), z = c.z();
        if (!district.contains(x, z) || district.parcelAt(x, z) != null || district.overlayAt(x, z) != UrbanOverlay.NONE) {
            return false;
        }
        UrbanSurface s = district.surfaceAt(x, z);
        return s == UrbanSurface.COURTYARD_PATH || s == UrbanSurface.COURTYARD || s == UrbanSurface.PASSAGE
                || s == UrbanSurface.ALLEY;
    }

    /** A barricade line of three cells, across x (a passage) or along z (an alley end). */
    private static Barricade line(int x, int z, boolean acrossX, boolean gated) {
        List<Vec3i> cells = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) { cells.add(acrossX ? v(x + i, z) : v(x, z + i)); }
        return new Barricade(cells, gated);
    }

    /** The free cell farthest from those already kept; removes and returns it, or null. */
    private static Vec3i spread(List<Vec3i> free, List<Vec3i> kept) {
        Vec3i best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Vec3i c : free) {
            int score = Integer.MAX_VALUE;
            for (Vec3i k : kept) { score = Math.min(score, SurvivorSites.manhattan(c, k) + 8 * Math.abs(c.y() - k.y())); }
            if (score > bestScore) {
                best = c;
                bestScore = score;
            }
        }
        if (best != null) { free.remove(best); }
        return best;
    }

    private static Vec3i nearest(List<Vec3i> cells, Vec3i to) {
        Vec3i best = null;
        for (Vec3i c : cells) {
            if (best == null || SurvivorSites.manhattan(c, to) < SurvivorSites.manhattan(best, to)) { best = c; }
        }
        return best;
    }

    private static Comparator<Vec3i> farthestFrom(Vec3i from) {
        return Comparator.comparingInt((Vec3i c) -> -(SurvivorSites.manhattan(c, from) + 2 * Math.abs(c.y() - from.y())))
                .thenComparingInt(Vec3i::y).thenComparingInt(Vec3i::x).thenComparingInt(Vec3i::z);
    }

    private static List<Long> ids(List<BuildingPlan> buildings) {
        List<Long> out = new ArrayList<>();
        for (BuildingPlan b : buildings) { out.add(b.id()); }
        return out;
    }

    private static List<Rect> lots(List<ParcelPlan> parcels) {
        List<Rect> out = new ArrayList<>();
        for (ParcelPlan p : parcels) { out.add(SurvivorSites.rect(p)); }
        return out;
    }

    private static Vec3i v(int x, int z) {
        return new Vec3i(x, FEET, z);
    }
}
