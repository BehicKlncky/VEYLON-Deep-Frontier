package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.urban.BuildingPlan.Fixture;
import com.veylon.world.urban.BuildingPlan.FixtureKind;
import com.veylon.world.urban.BuildingPlan.Floor;
import com.veylon.world.urban.BuildingPlan.Function;
import com.veylon.world.urban.BuildingPlan.Room;
import com.veylon.world.urban.BuildingPlan.Variant;
import com.veylon.world.urban.RouteCatalogue.Expect;
import com.veylon.world.urban.RouteCatalogue.Features;

import java.util.ArrayList;
import java.util.List;

/**
 * Building template revision seven (milestone 14): the overlay that turns a district-catalogue
 * plan into a building of the city's underground and rooftop routes. It never moves a wall,
 * stair, entrance or room of the ground and upper storeys, so every proven route of templates
 * four and five survives; it adds:
 *
 * <ul>
 *   <li>a rail column in the stair core's open well, so no landing or hall cell stands beside a
 *       drop deeper than the safe limit;</li>
 *   <li>on every roof, a guard rail on the parapet, so the two-course edge is never stepped
 *       over, and a concrete bulkhead around the stairwell with a metal roof door, which keeps
 *       the rain out of the stairs (the volume grows by one cell for its roof slab);</li>
 *   <li>the service doors, hidden cellar, fire-escape gap and roof openings its
 *       {@link RouteCatalogue} features name, each only where the plan's own cells show it
 *       can stand, and declared as fixtures so the route stamper builds the outside parts;</li>
 *   <li>a maintenance locker on a roof that carries a route;</li>
 *   <li>in a flooded district, one block of standing water over the basement floor.</li>
 * </ul>
 *
 * <p>The hidden cellar seals the right back room of the basement (room 8): a plaster partition
 * on its first row, its door walled up, and the way in moved one cell along the hall wall
 * behind a {@link BlockType#HIDDEN_PANEL}. The clues are real cells: drag-marked debris on the
 * hall floor in front of the panel, the panel's own seam and chalk, and a basement whose right
 * side has a door into its cross hall but none into its back room. A cot inside says someone
 * sheltered there.
 */
public final class RouteTemplates {
    public static final int REVISION = UrbanRevision.BUILDING_TEMPLATE_ROUTES;
    /** Parcel depth a sealed back room needs: a partition row and three rows of room. */
    public static final int CELLAR_MIN_DEPTH = 14;
    /** Roof-room fixture slots: the bulkhead door, the locker, openings and the fire escape. */
    static final int ROOF_DOOR = 0, ROOF_LOCKER = 2, FIRST_OPENING = 6, MAX_OPENINGS = 4, FIRE_ESCAPE = 10;
    /** Cross-hall fixture slots of the two service-door leaves. */
    static final int SERVICE_FIRST = 8, SERVICE_SECOND = 9;
    static final int CELLAR_ROOM = 8;

    private RouteTemplates() { }

    /**
     * Applies template seven to a validated-shape plan of templates four or five.
     *
     * @param flooded the parcel lies in a flooded district
     */
    public static BuildingPlan apply(BuildingPlan plan, Expect expect, Features features, boolean flooded) {
        Overlay o = new Overlay(plan, expect);
        o.guardStairwell();
        o.roof(features);
        o.serviceDoors(features);
        if (features.hiddenCellar()) { o.cellar(); }
        if (flooded) { o.flood(); }
        return o.build();
    }

    private static final class Overlay {
        final BuildingPlan plan;
        final Expect expect;
        final int width, depth, entry, core, roof;
        final PlanVolume.Builder voxels;
        final List<Fixture> fixtures;
        final List<Floor> floors;

        Overlay(BuildingPlan plan, Expect expect) {
            this.plan = plan; this.expect = expect;
            width = expect.width(); depth = expect.depth(); entry = expect.entry(); core = expect.core();
            roof = plan.roofY();
            PlanVolume v = plan.voxels();
            voxels = new PlanVolume.Builder(v.x0, v.z0, v.width, v.depth, v.minY, v.maxY + 1);
            for (int y = v.minY; y <= v.maxY; y++) {
                for (int z = v.z0; z < v.z0 + v.depth; z++) {
                    for (int x = v.x0; x < v.x0 + v.width; x++) { voxels.set(x, y, z, v.getBlock(x, y, z)); }
                }
            }
            fixtures = new ArrayList<>(plan.fixtures());
            floors = new ArrayList<>(plan.floors());
        }

        BlockType at(int u, int y, int v) { Vec3i p = expect.pos(u, y, v); return get(p.x(), y, p.z()); }
        BlockType get(int x, int y, int z) { return voxels.get(x, y, z); }
        void put(int u, int y, int v, BlockType block) { Vec3i p = expect.pos(u, y, v); voxels.set(p.x(), y, p.z(), block); }


        long roofRoom() { return UrbanIds.room(UrbanIds.floor(plan.id(), plan.storeys()), 0); }

        /**
         * The switchback leaves skeleton row 6 of its core open from the lowest floor to the roof:
         * beside every intermediate landing, and beside the hall over a basement stair, a misstep
         * would fall four to six blocks. A rail column fills that row; no flight or landing uses it.
         */
        void guardStairwell() {
            int bottom = UrbanDatum.slabY(plan.basement() ? -1 : 0) + 1;
            for (int u = core; u <= core + 3; u++) {
                for (int y = bottom; y <= roof + 3; y++) {
                    if (at(u, y, 6) == BlockType.AIR) { put(u, y, 6, BlockType.RAILING); }
                }
            }
        }

        // ---- the roof ----------------------------------------------------------------------

        void roof(Features features) {
            for (int u = 0; u < width; u++) {
                for (int v = 0; v < depth; v++) {
                    boolean edge = u == 0 || u == width - 1 || v == 0 || v == depth - 1;
                    if (edge && at(u, roof + 1, v).solid && !at(u, roof + 2, v).solid) {
                        put(u, roof + 2, v, BlockType.RAILING);
                    }
                }
            }
            bulkhead();
            boolean route = false;
            int slot = FIRST_OPENING;
            for (RouteCatalogue.Opening opening : features.openings()) {
                if (slot >= FIRST_OPENING + MAX_OPENINGS) { break; }
                if (open(opening.first()) && open(opening.second())) {
                    fixtures.add(new Fixture(UrbanIds.fixture(roofRoom(), slot++), FixtureKind.ROUTE, opening.first(), 1,
                            BlockType.AIR, opening.first(), false));
                    route = true;
                }
            }
            for (RouteCatalogue.FireEscape escape : features.fireEscapes()) {
                if (wallColumnSolid(escape.gap()) && open(escape.gap())) {
                    fixtures.add(new Fixture(UrbanIds.fixture(roofRoom(), FIRE_ESCAPE), FixtureKind.ROUTE, escape.gap(), 1,
                            BlockType.AIR, escape.gap(), false));
                    route = true;
                    break;
                }
            }
            if (route) { locker(); }
        }

        /** A concrete stair house over the stairwell, entered from the roof by a metal door. */
        void bulkhead() {
            for (int u = core - 1; u <= core + 4; u++) {
                for (int v = 0; v <= 7; v++) {
                    boolean wall = u == core - 1 || u == core + 4 || v == 0 || v == 7;
                    if (wall) {
                        for (int h = 1; h <= 3; h++) { put(u, roof + h, v, BlockType.CONCRETE); }
                    }
                    put(u, roof + 4, v, BlockType.CONCRETE);
                }
            }
            put(core - 1, roof + 1, 1, BlockType.METAL_DOOR);
            put(core - 1, roof + 2, 1, BlockType.METAL_DOOR);
            fixtures.add(new Fixture(UrbanIds.fixture(roofRoom(), ROOF_DOOR), FixtureKind.DOOR,
                    expect.pos(core - 1, roof + 1, 1), 2, BlockType.METAL_DOOR, expect.pos(core, roof + 1, 1), false));
        }

        /** Opens one parapet cell (and its rail) when it is a parapet beside walkable roof. */
        boolean open(Vec3i cell) {
            int x = cell.x(), z = cell.z();
            if (!plan.parcel().onOutline(x, z) || expect.bulkhead(x, z) || !get(x, roof + 1, z).solid
                    || get(x, roof, z) != BlockType.CONCRETE) { return false; }
            voxels.set(x, roof + 1, z, BlockType.AIR);
            voxels.set(x, roof + 2, z, BlockType.AIR);
            return true;
        }

        /** The rear wall below a fire-escape gap is solid on every storey, so the ladder has a wall. */
        boolean wallColumnSolid(Vec3i gap) {
            for (int y = UrbanDatum.STREET_Y + 1; y <= roof; y++) {
                if (!get(gap.x(), y, gap.z()).solid) { return false; }
            }
            return true;
        }

        void locker() {
            Vec3i at = expect.pos(core + 1, roof + 1, 9), target = expect.pos(core + 1, roof + 1, 8);
            if (get(at.x(), at.y(), at.z()) != BlockType.AIR || get(target.x(), target.y(), target.z()) != BlockType.AIR) {
                return;
            }
            voxels.set(at.x(), at.y(), at.z(), BlockType.LOCKER);
            fixtures.add(new Fixture(UrbanIds.fixture(roofRoom(), ROOF_LOCKER), FixtureKind.CONTAINER, at, 1,
                    BlockType.LOCKER, target, false));
        }

        // ---- the basement ------------------------------------------------------------------

        void serviceDoors(Features features) {
            if (!plan.basement()) { return; }
            boolean locked = plan.variant() == Variant.OCCUPIED;
            long floor = UrbanIds.floor(plan.id(), -1);
            for (RouteCatalogue.ServiceDoor door : features.serviceDoors()) {
                if (!leaf(door.first(), door.firstInside()) || !leaf(door.second(), door.secondInside())) { continue; }
                long room = UrbanIds.room(floor, door.room());
                for (Vec3i p : List.of(door.first(), door.second())) {
                    voxels.set(p.x(), p.y(), p.z(), BlockType.METAL_DOOR);
                    voxels.set(p.x(), p.y() + 1, p.z(), BlockType.METAL_DOOR);
                }
                fixtures.add(new Fixture(UrbanIds.fixture(room, SERVICE_FIRST), FixtureKind.DOOR, door.first(), 2,
                        BlockType.METAL_DOOR, door.firstInside(), locked));
                fixtures.add(new Fixture(UrbanIds.fixture(room, SERVICE_SECOND), FixtureKind.DOOR, door.second(), 2,
                        BlockType.METAL_DOOR, door.secondInside(), locked));
            }
        }

        /** A side-wall cell with clear hall behind it, both two cells tall. */
        boolean leaf(Vec3i wall, Vec3i inside) {
            return plan.parcel().onOutline(wall.x(), wall.z())
                    && get(wall.x(), wall.y(), wall.z()).solid && get(wall.x(), wall.y() + 1, wall.z()).solid
                    && get(inside.x(), inside.y(), inside.z()) == BlockType.AIR
                    && get(inside.x(), inside.y() + 1, inside.z()) == BlockType.AIR;
        }

        void cellar() {
            if (plan.family() != BuildingPlan.Family.APARTMENT || !plan.basement() || depth < CELLAR_MIN_DEPTH) { return; }
            int floorIndex = indexOf(-1);
            Floor floor = floors.get(floorIndex);
            Room room = null;
            for (Room r : floor.rooms()) { if (UrbanIds.roomIndex(r.id()) == CELLAR_ROOM) { room = r; } }
            if (room == null) { return; }
            int y = floor.slabY() + 1, a = entry + 3, b = width - 2, d = depth - 2;
            // Seal the first row and the old doorway; the panel goes one cell further along the hall wall.
            for (int u = a; u <= b; u++) {
                for (int h = 0; h < 3; h++) { put(u, y + h, 9, BlockType.PLASTER); }
            }
            put(entry + 2, y, 9, BlockType.PLASTER);
            put(entry + 2, y + 1, 9, BlockType.PLASTER);
            put(entry + 2, y, 10, BlockType.HIDDEN_PANEL);
            put(entry + 2, y + 1, 10, BlockType.HIDDEN_PANEL);
            put(entry + 1, y, 10, BlockType.DEBRIS);
            long roomId = room.id();
            fixtures.removeIf(f -> UrbanIds.parent(f.id()) == roomId
                    && (UrbanIds.slotIndex(f.id()) == 0 || UrbanIds.slotIndex(f.id()) == 3 || UrbanIds.slotIndex(f.id()) == 4
                    || expect.v(f.position().x(), f.position().z()) == 9));
            Vec3i anchor = expect.pos((a + b) / 2, y, 10);
            fixtures.add(new Fixture(UrbanIds.fixture(roomId, 0), FixtureKind.DOOR, expect.pos(entry + 2, y, 10), 2,
                    BlockType.HIDDEN_PANEL, expect.pos(a, y, 10), false));
            if (at(a, y, d) == BlockType.AIR && at(a + 1, y, d) == BlockType.AIR) {
                put(a, y, d, BlockType.CAMP_BED);
                fixtures.add(new Fixture(UrbanIds.fixture(roomId, 3), FixtureKind.FURNITURE, expect.pos(a, y, d), 1,
                        BlockType.CAMP_BED, expect.pos(a + 1, y, d), false));
            }
            fixtures.add(new Fixture(UrbanIds.fixture(roomId, 4), FixtureKind.QUEST, anchor, 1, BlockType.AIR, anchor, false));
            Vec3i p = expect.pos(a, y, 10), q = expect.pos(b, y, d);
            Room sealed = new Room(roomId, List.of(Function.HIDDEN_REFUGE), new ParcelPlan.Rect(Math.min(p.x(), q.x()),
                    Math.min(p.z(), q.z()), Math.max(p.x(), q.x()), Math.max(p.z(), q.z())), anchor);
            List<Room> rooms = new ArrayList<>();
            for (Room r : floor.rooms()) { rooms.add(r.id() == roomId ? sealed : r); }
            floors.set(floorIndex, new Floor(floor.id(), floor.index(), floor.slabY(), rooms, floor.landing()));
        }

        /** One block of standing water over every open basement floor cell. */
        void flood() {
            if (!plan.basement()) { return; }
            int y = UrbanDatum.slabY(-1) + 1;
            ParcelPlan p = plan.parcel();
            for (int x = p.x0(); x <= p.x1(); x++) {
                for (int z = p.z0(); z <= p.z1(); z++) {
                    if (get(x, y, z) == BlockType.AIR && get(x, y - 1, z).solid) { voxels.set(x, y, z, BlockType.WATER); }
                }
            }
        }

        int indexOf(int floorIndex) {
            for (int i = 0; i < floors.size(); i++) { if (floors.get(i).index() == floorIndex) { return i; } }
            throw new IllegalStateException("no floor " + floorIndex);
        }

        BuildingPlan build() {
            return new BuildingPlan(plan.id(), REVISION, plan.template() + "-routes", plan.family(), plan.variant(),
                    plan.parcel(), plan.storeys(), plan.basement(), floors, fixtures, plan.approaches(), plan.stairs(),
                    voxels.build());
        }
    }
}
