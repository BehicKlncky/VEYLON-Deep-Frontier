package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Fixtures;
import com.veylon.world.VoxelAccess;
import com.veylon.world.VoxelClearance;
import com.veylon.world.urban.BuildingPlan.FixtureKind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Exhaustive bounded directed voxel reachability, including exit routes and swept body clearance.
 * Unlocked doors are evaluated after their ordinary open action; locks remain solid. No key or
 * force-tool is assumed. The validator therefore proves a usable alternative for every lock.
 *
 * <p>The one deliberate exception is a secured room (pharmacy or armoury stock, D-99): its lock
 * is the access cost. Such a room must be unreachable by ordinary routes, its door must be
 * reachable from them, and with that door forced open every reservation inside must have an
 * entry and an exit route. Ordinary rooms are never accepted on the strength of a forced door.
 */
public final class AccessibilityValidator {
    public static final int MAX_MESSAGES = 32;
    public static final int MAX_FIXTURES = 512;
    public static final int MAX_FLOORS = 7;
    public static final int MAX_TRANSITIONS = 4 * PlanVolume.MAX_CELLS;
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private AccessibilityValidator() { }

    public record Report(int violations, List<String> messages, int cellsExamined, int transitions,
                         int forwardVisits, int reverseVisits) {
        public Report { messages = List.copyOf(messages); }
        public boolean ok() { return violations == 0; }
        public String summary() {
            return "ok=" + ok() + " violations=" + violations + " cells=" + cellsExamined
                    + " transitions=" + transitions + " reachable=" + forwardVisits
                    + " escapable=" + reverseVisits + " diagnostics=" + messages;
        }
    }

    public static Report inspect(BuildingPlan plan) {
        return new Check(plan).run();
    }

    /** Checks city approach reservations and adjacent footprints as well as the building itself. */
    public static Report inspect(BuildingPlan plan, DistrictPlan district) {
        return inspectDistrict(plan, district, false);
    }

    /**
     * Validate the actual generated cells through the same physical entry/return proof. Every
     * column of the bounded footprint and approach halo must already be loaded. Neighbouring
     * structures may occupy that halo; the proposal ownership restriction still applies to
     * {@link #inspect(BuildingPlan)} and is checked before stamping, rather than to their cells.
     * This read never requests a chunk and does not edit blocks or fixture state.
     */
    public static Report inspectGenerated(BuildingPlan plan, DistrictPlan district, VoxelAccess generated) {
        Check bounds = new Check(plan);
        if (!bounds.boundedMetadata()) {
            bounds.fail("BOUNDS: plan metadata exceeds validation limits");
            return bounds.report(0);
        }
        PlanVolume volume = plan.voxels();
        for (int x = volume.x0; x < volume.x0 + volume.width; x++) {
            for (int z = volume.z0; z < volume.z0 + volume.depth; z++) {
                if (!generated.isColumnLoaded(x, z)) {
                    bounds.fail("LOADED: generated building/approach column is unavailable " + x + "," + z);
                }
            }
        }
        if (bounds.violations != 0) { return bounds.report(0); }
        PlanVolume.Builder actual = volume.edit();
        for (int x = volume.x0; x < volume.x0 + volume.width; x++) {
            for (int z = volume.z0; z < volume.z0 + volume.depth; z++) {
                for (int y = volume.minY; y <= volume.maxY; y++) {
                    actual.set(x, y, z, generated.getBlock(x, y, z));
                }
            }
        }
        return inspectDistrict(plan.withVoxels(actual.build()), district, true);
    }

    private static Report inspectDistrict(BuildingPlan plan, DistrictPlan district, boolean worldContext) {
        Check c = new Check(plan, worldContext);
        if (!c.boundedMetadata() || district.parcels().size() > UrbanIds.MAX_PARCEL + 1) {
            c.fail("BOUNDS: district/plan metadata exceeds validation limits");
            return c.report(0);
        }
        if (!plan.parcel().equals(district.parcel(plan.parcel().index()))) {
            c.fail("PARCEL: plan does not belong to district");
        }
        for (Vec3i approach : plan.approaches()) {
            if (!district.contains(approach.x(), approach.z())
                    || !district.surfaceAt(approach.x(), approach.z()).route) {
                c.fail("APPROACH: entrance is not on a reserved exterior route");
            }
        }
        for (ParcelPlan other : district.parcels()) {
            if (other.id() != plan.parcel().id() && overlaps(plan.parcel(), other)) {
                c.fail("OVERLAP: neighbouring parcel " + other.index());
            }
        }
        return c.run();
    }

    public static boolean overlaps(ParcelPlan a, ParcelPlan b) {
        return a.x0() <= b.x1() && a.x1() >= b.x0() && a.z0() <= b.z1() && a.z1() >= b.z0();
    }

    /**
     * A single cardinal move using Pathfinder's step/drop policy and the body's real AABB.
     * The lift and horizontal sweep are sampled at 0.2 blocks, matching VoxelPhysics's axis
     * substep. Descents also clear the source-height sweep before falling (no wall tunnelling).
     */
    public static boolean canMove(VoxelAccess access, Vec3i from, Vec3i to) {
        if (Math.abs(to.x() - from.x()) + Math.abs(to.z() - from.z()) != 1
                || to.y() - from.y() > 1 || from.y() - to.y() > VoxelClearance.MAX_DROP
                || !VoxelClearance.standable(access, from.x(), from.y(), from.z())
                || !VoxelClearance.standable(access, to.x(), to.y(), to.z())) {
            return false;
        }
        if (to.y() > from.y() && !VoxelClearance.passable(access, from.x(), from.y() + 2, from.z())) {
            return false;
        }
        float high = Math.max(from.y(), to.y());
        if (VoxelClearance.collides(access, from.x() + .5f, high, from.z() + .5f,
                VoxelClearance.BODY_WIDTH, VoxelClearance.BODY_HEIGHT, true)) {
            return false;
        }
        for (int i = 1; i <= 5; i++) {
            float t = i / 5f;
            if (VoxelClearance.collides(access, from.x() + .5f + (to.x() - from.x()) * t,
                    high, from.z() + .5f + (to.z() - from.z()) * t,
                    VoxelClearance.BODY_WIDTH, VoxelClearance.BODY_HEIGHT, true)) {
                return false;
            }
        }
        for (int y = to.y(); y < high; y++) {
            if (VoxelClearance.collides(access, to.x() + .5f, y, to.z() + .5f,
                    VoxelClearance.BODY_WIDTH, VoxelClearance.BODY_HEIGHT, true)) {
                return false;
            }
        }
        return true;
    }

    private static final class Check {
        final BuildingPlan plan;
        final PlanVolume volume;
        final boolean worldContext;
        final List<String> messages = new ArrayList<>();
        int violations, transitions, forwardVisits, reverseVisits;
        PlanVolume opened;
        boolean[] walk, reached, escaped, forcedReached, forcedEscaped;
        int[] edges, reverseHead, reverseNext, reverseFrom;
        /** Rooms behind a forcible lock, and those locked doors. */
        final Set<Long> securedRooms = new HashSet<>();
        /** Hidden cellars (template seven), behind a loose panel. */
        final Set<Long> concealedRooms = new HashSet<>();
        final List<BuildingPlan.Fixture> forcible = new ArrayList<>();

        Check(BuildingPlan p) { this(p, false); }
        Check(BuildingPlan p, boolean worldContext) {
            plan = p; volume = p.voxels(); this.worldContext = worldContext;
        }

        Report run() {
            if (!boundedMetadata()) {
                fail("BOUNDS: plan metadata exceeds validation limits");
                return report(0);
            }
            metadata();
            if (violations > 0) { return report(0); }
            openUnlockedDoors();
            buildGraph();
            reached = flood(false);
            escaped = flood(true);
            stairs();
            if (!forcible.isEmpty()) { forcedPhase(); }
            targets();
            return report(volume.cellCount());
        }

        /**
         * Secured rooms: the lock must be the only way in, its door must stand on an ordinary
         * route, and forcing it must open entry and exit routes to everything inside.
         */
        void forcedPhase() {
            for (BuildingPlan.Fixture door : forcible) {
                Vec3i p = door.position(), t = door.target();
                Vec3i outside = new Vec3i(2 * p.x() - t.x(), t.y(), 2 * p.z() - t.z());
                int i = worldIndex(outside);
                if (i < 0 || !reached[i] || !escaped[i]) {
                    fail("SECURED: forcible door is not on an ordinary route " + p);
                }
            }
            for (BuildingPlan.Floor floor : plan.floors()) {
                for (BuildingPlan.Room room : floor.rooms()) {
                    int i = worldIndex(room.anchor());
                    boolean behind = securedRooms.contains(room.id()) || concealedRooms.contains(room.id());
                    if (behind && i >= 0 && reached[i]) {
                        fail("SECURED: room is reachable without forcing its lock " + room.anchor());
                    }
                }
            }
            PlanVolume.Builder b = opened.edit();
            for (BuildingPlan.Fixture door : forcible) {
                Vec3i p = door.position();
                for (int h = 0; h < door.height(); h++) { b.set(p.x(), p.y() + h, p.z(), BlockType.BROKEN_DOOR); }
            }
            opened = b.build();
            transitions = 0;
            buildGraph();
            forcedReached = flood(false);
            forcedEscaped = flood(true);
        }

        /** Cells above the roof slab the volume holds: three, or four under a template-seven bulkhead. */
        int top() { return plan.templateRevision() == UrbanRevision.BUILDING_TEMPLATE_ROUTES ? 4 : 3; }

        boolean boundedMetadata() {
            return plan.floors().size() <= MAX_FLOORS && plan.fixtures().size() <= MAX_FIXTURES
                    && plan.approaches().size() <= 4 && plan.stairs().size() <= 6
                    && plan.floors().stream().allMatch(f -> f.rooms().size() <= 32)
                    && plan.stairs().stream().allMatch(s -> s.route().size() <= 32);
        }

        void metadata() {
            int revision = plan.templateRevision();
            boolean routes = revision == UrbanRevision.BUILDING_TEMPLATE_ROUTES;
            // Template seven overlays both walk-ups and the commercial and civic families.
            boolean familyFits = plan.family() != BuildingPlan.Family.TRANSIT
                    && (routes || plan.family().walkUp() != (revision == UrbanRevision.BUILDING_TEMPLATE_CIVIC));
            if (!UrbanIds.isValid(plan.id()) || plan.id() != UrbanIds.building(plan.parcel().id(), 0)
                    || !BuildingTemplates.supportsRevision(revision) || !familyFits) {
                fail("IDENTITY: unsupported building/template identity");
            }
            if (plan.storeys() < 1
                    || plan.storeys() > Math.min(BuildingTemplates.maxStoreys(plan.family()), plan.parcel().maxStoreys())
                    || plan.roofY() + top() >= 94 || volume.minY < 35 || volume.maxY != plan.roofY() + top()
                    || volume.x0 != plan.parcel().x0() - 1 || volume.z0 != plan.parcel().z0() - 1
                    || volume.width != plan.parcel().width() + 2 || volume.depth != plan.parcel().depth() + 2) {
                fail("ENVELOPE: footprint or vertical overflow");
            }
            Set<Long> ids = new HashSet<>();
            Set<Long> roomIds = new HashSet<>();
            int expectedFloor = plan.basement() ? -1 : 0;
            for (BuildingPlan.Floor floor : plan.floors()) {
                if (!UrbanDatum.isFloor(floor.index())) { fail("FLOOR: outside vertical datum"); continue; }
                if (floor.index() != expectedFloor++ || floor.slabY() != UrbanDatum.slabY(floor.index())
                        || floor.id() != UrbanIds.floor(plan.id(), floor.index()) || !ids.add(floor.id())) {
                    fail("FLOOR: missing, duplicate or inconsistent floor");
                }
                for (BuildingPlan.Room room : floor.rooms()) {
                    ParcelPlan.Rect r = room.interior();
                    if (!UrbanIds.isValid(room.id()) || UrbanIds.parent(room.id()) != floor.id()
                            || !ids.add(room.id()) || room.functions().isEmpty()
                            || !r.contains(room.anchor().x(), room.anchor().z())
                            || room.anchor().y() != floor.slabY() + 1 || r.x1() < r.x0() || r.z1() < r.z0()
                            || !plan.parcel().contains(r.x0(), r.z0()) || !plan.parcel().contains(r.x1(), r.z1())) {
                        fail("ROOM: invalid bounds, function, anchor or identity");
                    }
                    roomIds.add(room.id());
                    if (room.functions().stream().anyMatch(BuildingPlan.Function::secured)) {
                        securedRooms.add(room.id());
                    }
                    if (room.functions().contains(BuildingPlan.Function.HIDDEN_REFUGE)) {
                        concealedRooms.add(room.id());
                    }
                    for (BuildingPlan.Room other : floor.rooms()) {
                        if (room.id() < other.id() && intersects(r, other.interior())) {
                            fail("ROOM: overlapping playable interiors");
                        }
                    }
                }
            }
            if (expectedFloor != plan.storeys() + 1 || plan.approaches().isEmpty()
                    || plan.stairs().size() != plan.storeys() + (plan.basement() ? 1 : 0)) {
                fail("FLOOR: missing floor, exterior approach or stair flight");
            }
            Set<Vec3i> occupied = new HashSet<>();
            for (BuildingPlan.Fixture fixture : plan.fixtures()) {
                if (!UrbanIds.isValid(fixture.id()) || !roomIds.contains(UrbanIds.parent(fixture.id()))
                        || !ids.add(fixture.id()) || fixture.height() < 1 || fixture.height() > 3) {
                    fail("FIXTURE: invalid reservation identity/height");
                    continue;
                }
                Vec3i p = fixture.position();
                if (!plan.parcel().contains(p.x(), p.z()) || p.y() < volume.minY
                        || p.y() + fixture.height() - 1 > volume.maxY) {
                    fail("FIXTURE: outside footprint/vertical limits");
                    continue;
                }
                int slab = UrbanDatum.slabY(UrbanIds.floorIndex(fixture.id()));
                Fixtures.Kind kind = Fixtures.kindOf(fixture.block());
                if (fixture.kind() == FixtureKind.DOOR && (kind == null || !kind.door)
                        || fixture.kind() == FixtureKind.WINDOW && kind != Fixtures.Kind.WINDOW
                        && fixture.block() != BlockType.BROKEN_WINDOW
                        || fixture.locked() && !Fixtures.isClosedDoor(fixture.block())) {
                    fail("FIXTURE: declared opening has no matching physical action");
                }
                if (fixture.kind() == FixtureKind.WINDOW && (p.y() != slab + 2 || fixture.height() != 2)
                        || fixture.kind() == FixtureKind.DOOR && (p.y() != slab + 1 || fixture.height() != 2)
                        || fixture.locked() && fixture.kind() != FixtureKind.DOOR) {
                    fail("FIXTURE: opening conflicts with floor bands/lock policy");
                }
                // A secured room's own locked door is the forced entry; every other lock stays solid.
                if (fixture.locked() && securedRooms.contains(UrbanIds.parent(fixture.id()))
                        && UrbanIds.slotIndex(fixture.id()) == 0) {
                    forcible.add(fixture);
                }
                // A hidden cellar's loose panel is its only way in, proven like a secured room's lock.
                if (fixture.block() == BlockType.HIDDEN_PANEL && concealedRooms.contains(UrbanIds.parent(fixture.id()))) {
                    forcible.add(fixture);
                }
                if (fixture.kind() == FixtureKind.WINDOW && (!plan.parcel().onOutline(p.x(), p.z())
                        || Math.abs(p.x() - fixture.target().x()) + Math.abs(p.z() - fixture.target().z()) > 2)) {
                    fail("WINDOW: facade/interaction reach mismatch");
                }
                if (fixture.kind() != FixtureKind.QUEST && fixture.kind() != FixtureKind.ROUTE) {
                    for (int h = 0; h < fixture.height(); h++) {
                        Vec3i cell = new Vec3i(p.x(), p.y() + h, p.z());
                        if (!occupied.add(cell) || volume.getBlock(cell.x(), cell.y(), cell.z()) != fixture.block()) {
                            fail("FIXTURE: overlapping or missing physical fixture");
                        }
                    }
                }
            }
            if (violations == 0 && (plan.templateRevision() == UrbanRevision.BUILDING_TEMPLATE_FURNISHED
                    || plan.templateRevision() == UrbanRevision.BUILDING_TEMPLATE_SHAPED_STAIRS)) {
                furnishingBounds();
            }
            if (violations == 0 && !plan.family().walkUp() && (plan.templateRevision() == UrbanRevision.BUILDING_TEMPLATE_CIVIC
                    || plan.templateRevision() == UrbanRevision.BUILDING_TEMPLATE_ROUTES)) {
                civicBounds();
            }
            // No structure is allowed to spill into the approach halo, even if its route still works.
            if (!worldContext) {
                for (int x = volume.x0; x < volume.x0 + volume.width; x++) {
                    for (int z = volume.z0; z < volume.z0 + volume.depth; z++) {
                        if (!plan.parcel().contains(x, z)) {
                            for (int y = 41; y <= volume.maxY; y++) {
                                if (volume.getBlock(x, y, z) != BlockType.AIR) { fail("OVERLAP: geometry outside parcel"); }
                            }
                        }
                    }
                }
            }
        }

        void furnishingBounds() {
            for (BuildingPlan.Floor floor : plan.floors()) {
                int flatContainers = 0;
                for (BuildingPlan.Room room : floor.rooms()) {
                    int fixtures = 0, containers = 0;
                    for (BuildingPlan.Fixture fixture : plan.fixtures()) {
                        if (UrbanIds.parent(fixture.id()) == room.id()) {
                            fixtures++;
                            if (fixture.kind() == FixtureKind.CONTAINER) { containers++; }
                        }
                    }
                    if (fixtures > BuildingFurnishing.MAX_ROOM_FIXTURES
                            || containers > BuildingFurnishing.MAX_ROOM_CONTAINERS) {
                        fail("FURNISHING: room fixture/container budget exceeded");
                    }
                    flatContainers += containers;
                }
                boolean domestic = floor.index() >= 0 && floor.index() < plan.storeys()
                        && (plan.family() == BuildingPlan.Family.APARTMENT || floor.index() > 0);
                if (domestic && (flatContainers < BuildingFurnishing.MIN_FLAT_CONTAINERS
                        || flatContainers > BuildingFurnishing.MAX_FLAT_CONTAINERS)) {
                    fail("FURNISHING: apartment searchable reservation budget exceeded");
                }
            }
        }

        /** Revision five: bounded stock per room and storey; secured rooms only on the ground floor. */
        void civicBounds() {
            for (BuildingPlan.Floor floor : plan.floors()) {
                int floorContainers = 0;
                for (BuildingPlan.Room room : floor.rooms()) {
                    int containers = 0;
                    for (BuildingPlan.Fixture fixture : plan.fixtures()) {
                        if (UrbanIds.parent(fixture.id()) == room.id() && fixture.kind() == FixtureKind.CONTAINER) {
                            containers++;
                        }
                    }
                    if (containers > CivicTemplates.MAX_ROOM_CONTAINERS) {
                        fail("FURNISHING: civic room container budget exceeded " + room.anchor());
                    }
                    if (securedRooms.contains(room.id()) && floor.index() != 0) {
                        fail("SECURED: secured stock above or below street level " + room.anchor());
                    }
                    floorContainers += containers;
                }
                if (floorContainers > CivicTemplates.MAX_FLOOR_CONTAINERS) {
                    fail("FURNISHING: civic storey container budget exceeded on floor " + floor.index());
                }
            }
        }

        static boolean intersects(ParcelPlan.Rect a, ParcelPlan.Rect b) {
            return a.x0() <= b.x1() && a.x1() >= b.x0() && a.z0() <= b.z1() && a.z1() >= b.z0();
        }

        void openUnlockedDoors() {
            PlanVolume.Builder b = volume.edit();
            for (BuildingPlan.Fixture fixture : plan.fixtures()) {
                // A closed loose panel stays shut: it is a wall to every ordinary route.
                if (fixture.kind() == FixtureKind.DOOR && !fixture.locked() && fixture.block() != BlockType.HIDDEN_PANEL) {
                    Vec3i p = fixture.position();
                    for (int h = 0; h < fixture.height(); h++) {
                        b.set(p.x(), p.y() + h, p.z(), BlockType.WOOD_DOOR_OPEN);
                    }
                }
            }
            opened = b.build();
        }

        // Local coordinates keep collider arithmetic precise even for far-region plan coordinates.
        VoxelAccess localAccess() {
            return new VoxelAccess() {
                @Override public BlockType getBlock(int x, int y, int z) {
                    return opened.getBlock(x + volume.x0, y, z + volume.z0);
                }
                @Override public boolean isColumnLoaded(int x, int z) {
                    return opened.isColumnLoaded(x + volume.x0, z + volume.z0);
                }
            };
        }

        void buildGraph() {
            int n = volume.cellCount();
            walk = new boolean[n]; edges = new int[n * 4]; reverseHead = new int[n];
            reverseNext = new int[n * 4]; reverseFrom = new int[n * 4];
            Arrays.fill(edges, -1); Arrays.fill(reverseHead, -1);
            VoxelAccess local = localAccess();
            for (int i = 0; i < n; i++) {
                Vec3i p = cell(i);
                walk[i] = VoxelClearance.standable(local, p.x(), p.y(), p.z())
                        && !VoxelClearance.collides(local, p.x() + .5f, p.y(), p.z() + .5f,
                        VoxelClearance.BODY_WIDTH, VoxelClearance.BODY_HEIGHT, true);
            }
            for (int i = 0; i < n; i++) {
                if (!walk[i]) { continue; }
                Vec3i from = cell(i);
                for (int d = 0; d < 4; d++) {
                    int x = from.x() + DIRS[d][0], z = from.z() + DIRS[d][1], y = from.y();
                    if (!at(x, y, z)) {
                        if (at(x, y + 1, z) && VoxelClearance.passable(local, from.x(), y + 2, from.z())) {
                            y++;
                        } else {
                            boolean found = false;
                            for (int drop = 1; drop <= VoxelClearance.MAX_DROP; drop++) {
                                if (!VoxelClearance.passable(local, x, y - drop + 1, z)) { break; }
                                if (at(x, y - drop, z)) { y -= drop; found = true; break; }
                            }
                            if (!found) { continue; }
                        }
                    }
                    Vec3i to = new Vec3i(x, y, z);
                    if (!canMove(local, from, to)) { continue; }
                    int dest = index(x, y, z);
                    edges[i * 4 + d] = dest;
                    reverseFrom[transitions] = i;
                    reverseNext[transitions] = reverseHead[dest];
                    reverseHead[dest] = transitions++;
                }
            }
        }

        boolean[] flood(boolean reverse) {
            boolean[] visited = new boolean[walk.length];
            int[] queue = new int[walk.length];
            int tail = 0;
            for (Vec3i approach : plan.approaches()) {
                int i = worldIndex(approach);
                if (i < 0 || !walk[i] || approach.y() != 41 || plan.parcel().contains(approach.x(), approach.z())) {
                    fail("APPROACH: no exterior standing clearance");
                } else if (!visited[i]) { visited[i] = true; queue[tail++] = i; }
            }
            for (int head = 0; head < tail; head++) {
                int i = queue[head];
                if (reverse) {
                    for (int edge = reverseHead[i]; edge >= 0; edge = reverseNext[edge]) {
                        int dest = reverseFrom[edge];
                        if (!visited[dest]) { visited[dest] = true; queue[tail++] = dest; }
                    }
                } else {
                    for (int d = 0; d < 4; d++) {
                        int dest = edges[i * 4 + d];
                        if (dest >= 0 && !visited[dest]) { visited[dest] = true; queue[tail++] = dest; }
                    }
                }
            }
            if (reverse) { reverseVisits = tail; } else { forwardVisits = tail; }
            return visited;
        }

        void targets() {
            Set<Long> forced = new HashSet<>();
            for (BuildingPlan.Fixture door : forcible) { forced.add(UrbanIds.parent(door.id())); }
            for (BuildingPlan.Floor floor : plan.floors()) {
                target(floor.landing(), "LANDING");
                for (BuildingPlan.Room room : floor.rooms()) {
                    boolean inside = forced.contains(room.id());
                    target(room.anchor(), "ROOM", inside);
                    for (int x = room.interior().x0(); x <= room.interior().x1(); x++) {
                        for (int z = room.interior().z0(); z <= room.interior().z1(); z++) {
                            Vec3i at = new Vec3i(x, floor.slabY() + 1, z);
                            int i = worldIndex(at);
                            if (i >= 0 && walk[i]) { target(at, "ROOM_CELL", inside); }
                            else if (!plan.parcel().core().contains(x, z)
                                    && !opened.getBlock(x, at.y(), z).solid) {
                                fail("ROOM_CLEARANCE: missing floor or standing headroom " + at);
                            }
                        }
                    }
                }
            }
            for (BuildingPlan.Fixture fixture : plan.fixtures()) {
                boolean inside = forced.contains(UrbanIds.parent(fixture.id()));
                target(fixture.target(), fixture.kind().name(), inside);
                if (fixture.kind() == FixtureKind.DOOR && (!fixture.locked() || forcible.contains(fixture))) {
                    target(fixture.position(), "DOOR", inside);
                }
                if (fixture.kind() == FixtureKind.CONTAINER || fixture.kind() == FixtureKind.FURNITURE) {
                    Vec3i a = fixture.position(), b = fixture.target();
                    if (a.y() != b.y() || Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z()) != 1
                            || !Fixtures.isUrbanContainer(fixture.block()) && fixture.kind() == FixtureKind.CONTAINER) {
                        fail("FURNITURE: no adjacent interaction clearance");
                    }
                }
            }
        }

        void stairs() {
            VoxelAccess local = localAccess();
            int expected = plan.basement() ? -1 : 0;
            for (BuildingPlan.Stair stair : plan.stairs()) {
                if (stair.fromFloor() != expected++ || stair.route().isEmpty()) {
                    fail("STAIR: disconnected or missing flight"); continue;
                }
                List<Vec3i> route = stair.route();
                if (route.getFirst().y() != UrbanDatum.slabY(stair.fromFloor()) + 1
                        || route.getLast().y() != UrbanDatum.slabY(stair.fromFloor() + 1) + 1) {
                    fail("STAIR: flight does not join adjacent floors");
                }
                for (int i = 0; i < route.size(); i++) {
                    if (!plan.parcel().core().contains(route.get(i).x(), route.get(i).z())) {
                        fail("STAIR: flight leaves reserved core");
                    }
                    target(route.get(i), "STAIR");
                    if (i == 0) { continue; }
                    Vec3i a = local(route.get(i - 1)), b = local(route.get(i));
                    if (!canMove(local, a, b) || !canMove(local, b, a)) {
                        fail("STAIR: invalid swept clearance or step");
                    }
                    if (b.y() > a.y()) {
                        int dx = b.z() - a.z(), dz = b.x() - a.x();
                        boolean twoWide = false;
                        for (int side : new int[]{-1, 1}) {
                            if (canMove(local, new Vec3i(a.x() + dx * side, a.y(), a.z() + dz * side),
                                    new Vec3i(b.x() + dx * side, b.y(), b.z() + dz * side))) { twoWide = true; }
                        }
                        if (!twoWide) { fail("STAIR: flight narrower than two cells"); }
                    }
                }
            }
        }

        Vec3i local(Vec3i p) { return new Vec3i(p.x() - volume.x0, p.y(), p.z() - volume.z0); }
        int worldIndex(Vec3i p) { return index(p.x() - volume.x0, p.y(), p.z() - volume.z0); }
        int index(int x, int y, int z) {
            return x < 0 || x >= volume.width || z < 0 || z >= volume.depth || y < volume.minY || y > volume.maxY
                    ? -1 : ((y - volume.minY) * volume.depth + z) * volume.width + x;
        }
        Vec3i cell(int i) {
            return new Vec3i(i % volume.width, i / (volume.width * volume.depth) + volume.minY,
                    i / volume.width % volume.depth);
        }
        boolean at(int x, int y, int z) { int i = index(x, y, z); return i >= 0 && walk[i]; }
        void target(Vec3i p, String kind) { target(p, kind, false); }
        /** Secured-room targets count only once their lock is forced; everything else ordinarily. */
        void target(Vec3i p, String kind, boolean forced) {
            int i = worldIndex(p);
            boolean[] in = forced ? forcedReached : reached, out = forced ? forcedEscaped : escaped;
            if (i < 0 || !in[i]) { fail(kind + ": unreachable " + p); }
            else if (!out[i]) { fail(kind + ": no exit route " + p); }
        }
        void fail(String message) { violations++; if (messages.size() < MAX_MESSAGES) { messages.add(message); } }
        Report report(int cells) { return new Report(violations, messages, cells, transitions, forwardVisits, reverseVisits); }
    }
}
