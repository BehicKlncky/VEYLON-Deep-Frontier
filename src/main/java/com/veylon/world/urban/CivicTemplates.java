package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.urban.BuildingFurnishing.Bounds;
import com.veylon.world.urban.BuildingFurnishing.Layout;
import com.veylon.world.urban.BuildingPlan.Family;
import com.veylon.world.urban.BuildingPlan.Fixture;
import com.veylon.world.urban.BuildingPlan.FixtureKind;
import com.veylon.world.urban.BuildingPlan.Floor;
import com.veylon.world.urban.BuildingPlan.Function;
import com.veylon.world.urban.BuildingPlan.Room;
import com.veylon.world.urban.BuildingPlan.Variant;
import com.veylon.world.urban.BuildingTemplates.FloorProgram;

import java.util.ArrayList;
import java.util.List;

/**
 * Frozen template revision five: grocery and convenience stores, clinics and pharmacies,
 * schools, office blocks, security posts and warehouses (milestone 12, D-98 to D-101).
 *
 * <p>Every family runs the proven walk-up skeleton ({@link BuildingTemplates.Builder}) under
 * its own floor program, so walls, halls, the stair core, entrances, windows and reservation
 * ids keep the skeleton's validated geometry. An overlay then gives the rooms their purpose:
 * stock and fittings along the facade, desks, counters or racks as islands between one-cell
 * lanes, a secured room behind a forcible lock, a frontage sign and the family's facade.
 * Damage, picked-clean and partial occupation are deterministic parts of the same proposal.
 *
 * <p>Lanes are the reason every placement here is safe: the facade column holds furniture,
 * the next column is always clear (window, container and prop targets stand there), islands
 * occupy every other column after that and never the first or last row of a room, and the
 * column against the hall partition stays clear for the room's door.
 */
public final class CivicTemplates {
    public static final int REVISION = UrbanRevision.BUILDING_TEMPLATE_CIVIC;
    /** Searchable reservations per room and per storey; every rack bay counts. */
    public static final int MAX_ROOM_CONTAINERS = 4;
    public static final int MAX_FLOOR_CONTAINERS = 12;
    /** Container reservations take these slots in order, so a kitchen's second one is its fridge. */
    private static final int[] CONTAINER_SLOTS = {2, 5, 6, 7};
    private static final int[] PROP_SLOTS = {3, 12, 13, 14, 15};
    /** A shop floor's frontage display window; slot 1 keeps the skeleton's side window. */
    private static final int FRONT_WINDOW_SLOT = 8;

    /**
     * What a family needs from a parcel: its storey range, the smallest frontage width and
     * depth its program fits, and whether it may have a first basement.
     */
    public record Envelope(int minStoreys, int maxStoreys, int minWidth, int minDepth, boolean basements) {
        public boolean fits(ParcelPlan parcel) {
            boolean alongX = parcel.frontage().dx == 0;
            int width = alongX ? parcel.width() : parcel.depth();
            int depth = alongX ? parcel.depth() : parcel.width();
            return width >= minWidth && depth >= minDepth && parcel.maxStoreys() >= minStoreys;
        }

        /** The storeys a fitting parcel's envelope allows, never above the family's limit. */
        public int storeys(ParcelPlan parcel) {
            return Math.min(maxStoreys, parcel.maxStoreys());
        }
    }

    private CivicTemplates() { }

    public static Envelope envelope(Family family) {
        return switch (family) {
            case GROCERY -> new Envelope(1, 2, 12, 12, false);
            case CLINIC -> new Envelope(1, 3, 12, 12, false);
            case SCHOOL -> new Envelope(2, 3, 20, 12, true);
            case OFFICE -> new Envelope(3, 5, 16, 12, true);
            case SECURITY -> new Envelope(1, 2, 16, 12, false);
            case WAREHOUSE -> new Envelope(1, 2, 16, 12, false);
            case APARTMENT, SHOP, TRANSIT -> throw new IllegalArgumentException(family + " uses the walk-up catalogue");
        };
    }

    /** Facade material; a family reads from the street before its sign does. */
    static BlockType facade(Family family) {
        return switch (family) {
            case CLINIC -> BlockType.PLASTER;
            case OFFICE, SECURITY -> BlockType.CONCRETE;
            case WAREHOUSE -> BlockType.SHEET_METAL;
            default -> BlockType.BRICK;
        };
    }

    /** The facade block of any plan: brick for the walk-ups. */
    static BlockType facadeOf(BuildingPlan plan) {
        return plan.family().walkUp() ? BlockType.BRICK : facade(plan.family());
    }

    /** Low-level proposal; callers validate it or use {@link BuildingPlanner#plan}. */
    static BuildingPlan propose(ParcelPlan parcel, Family family, Variant variant, int storeys,
                                boolean basement, boolean minimal) {
        Envelope envelope = envelope(family);
        if (parcel.width() > ParcelPlan.MAX_SIDE || parcel.depth() > ParcelPlan.MAX_SIDE
                || !envelope.fits(parcel) || storeys < envelope.minStoreys() || storeys > envelope.storeys(parcel)
                || basement && !envelope.basements()) {
            throw new IllegalArgumentException(family + " does not fit parcel/storey envelope");
        }
        int depth = parcel.frontage().dx == 0 ? parcel.depth() : parcel.width();
        BuildingPlan skeleton = new BuildingTemplates.Builder(parcel, family, variant, storeys, basement, minimal,
                f -> program(family, f, minimal, depth)).build();
        BuildingPlan furnished = new Overlay(skeleton, minimal).apply();
        BuildingPlan shaped = BuildingStairShapes.apply(furnished);
        return new BuildingPlan(shaped.id(), REVISION, furnished.template(), family, variant, parcel, storeys,
                basement, shaped.floors(), shaped.fixtures(), shaped.approaches(), shaped.stairs(), shaped.voxels());
    }

    /**
     * Room functions by family and storey. The front block of the ground floor is the secured
     * room where a family keeps one; the finite minimal fallback drops it.
     */
    static FloorProgram program(Family family, int f, boolean minimal, int depth) {
        if (f < 0) {
            return new FloorProgram(List.of(Function.BASEMENT_UTILITY), false,
                    List.of(Function.BASEMENT_STORAGE, Function.BASEMENT_UTILITY),
                    List.of(Function.BASEMENT_STORAGE, Function.BASEMENT_UTILITY), false);
        }
        boolean split = !minimal && depth >= 16, ground = f == 0;
        return switch (family) {
            case GROCERY -> ground
                    ? fp(Function.SHOP_FLOOR, Function.SHOP_FLOOR, Function.SHOP_FLOOR,
                    Function.STOCKROOM, Function.STORAGE, split)
                    : fp(Function.OFFICE, Function.STORAGE, Function.KITCHEN, Function.UTILITY, Function.BATHROOM, split);
            case CLINIC -> ground
                    ? fp(minimal ? Function.CLINIC : Function.PHARMACY, Function.CLINIC, Function.CLINIC,
                    Function.BATHROOM, Function.UTILITY, split)
                    : fp(Function.OFFICE, Function.CLINIC, Function.CLINIC, Function.UTILITY, Function.BATHROOM, split);
            case SCHOOL -> ground
                    ? fp(Function.OFFICE, Function.CLASSROOM, Function.CLASSROOM, Function.KITCHEN,
                    Function.BATHROOM, split)
                    : fp(Function.CLASSROOM, Function.CLASSROOM, Function.CLASSROOM, Function.STORAGE,
                    Function.BATHROOM, split);
            case OFFICE -> ground
                    ? fp(Function.OFFICE, Function.OFFICE, Function.OFFICE, Function.UTILITY, Function.BATHROOM, split)
                    : fp(Function.OFFICE, Function.OFFICE, Function.OFFICE, Function.KITCHEN, Function.UTILITY, split);
            case SECURITY -> ground
                    ? fp(minimal ? Function.OFFICE : Function.SECURITY, Function.OFFICE, Function.OFFICE,
                    Function.UTILITY, Function.STORAGE, split)
                    : fp(Function.OFFICE, Function.KITCHEN, Function.BEDROOM, Function.BATHROOM, Function.STORAGE, split);
            // One long rack hall per side, so aisles run the whole depth.
            case WAREHOUSE -> ground
                    ? fp(Function.WORKSHOP, Function.WAREHOUSE, Function.WAREHOUSE,
                    Function.WAREHOUSE, Function.WAREHOUSE, false)
                    : fp(Function.OFFICE, Function.STORAGE, Function.STORAGE, Function.UTILITY, Function.BATHROOM, split);
            case APARTMENT, SHOP, TRANSIT -> throw new IllegalArgumentException(family + " uses the walk-up program");
        };
    }

    private static FloorProgram fp(Function front, Function left0, Function left1, Function right0,
                                   Function right1, boolean split) {
        return new FloorProgram(List.of(front), false, List.of(left0, left1), List.of(right0, right1), split);
    }

    /** The function that decides a room's fittings, in {@link BuildingFurnishing#containerUse} order. */
    static Function primary(List<Function> functions) {
        for (Function f : new Function[]{Function.PHARMACY, Function.SECURITY, Function.CLINIC, Function.CLASSROOM,
                Function.OFFICE, Function.WAREHOUSE, Function.WORKSHOP, Function.KITCHEN, Function.BATHROOM,
                Function.BEDROOM, Function.SHOP_FLOOR, Function.STOCKROOM, Function.BASEMENT_STORAGE,
                Function.UTILITY, Function.BASEMENT_UTILITY, Function.STORAGE}) {
            if (functions.contains(f)) { return f; }
        }
        return functions.getFirst();
    }

    /** Name of the frozen template a family's proposal records. */
    static String templateName(Family family, ParcelPlan parcel, int storeys) {
        return switch (family) {
            case GROCERY -> parcel.width() * parcel.depth() <= 320 ? "convenience-store" : "grocery";
            case CLINIC -> storeys == 1 ? "pharmacy" : "clinic";
            case SCHOOL -> "school";
            case OFFICE -> "office-block";
            case SECURITY -> "security-post";
            case WAREHOUSE -> "warehouse";
            case APARTMENT, SHOP, TRANSIT -> throw new IllegalArgumentException(family + " is a walk-up");
        };
    }

    /**
     * Fittings of a room by its primary function: the facade container block and count, the
     * facade props, the island block between lanes (SHEET_METAL meaning racks) and flooring.
     */
    private record Spec(BlockType container, int containers, List<BlockType> props, BlockType island,
                        BlockType floor) { }

    private static Spec spec(Function function) {
        return switch (function) {
            case SHOP_FLOOR -> new Spec(BlockType.SHELF, 2, List.of(BlockType.TABLE), BlockType.TABLE, BlockType.PAVING);
            case STOCKROOM -> new Spec(BlockType.SHELF, 2, List.of(BlockType.DEBRIS), null, BlockType.CONCRETE);
            case CLINIC -> new Spec(BlockType.CABINET, 1, List.of(BlockType.CAMP_BED, BlockType.RAIN_COLLECTOR),
                    null, BlockType.PAVING);
            case PHARMACY -> new Spec(BlockType.CABINET, 3, List.of(), BlockType.TABLE, BlockType.PAVING);
            case SECURITY -> new Spec(BlockType.LOCKER, 3, List.of(), BlockType.TABLE, BlockType.CONCRETE);
            case CLASSROOM -> new Spec(BlockType.SHELF, 1, List.of(BlockType.TABLE), BlockType.TABLE, BlockType.PLANK);
            case OFFICE -> new Spec(BlockType.CABINET, 2, List.of(), BlockType.TABLE, BlockType.PLANK);
            case WAREHOUSE -> new Spec(BlockType.SHELF, 1, List.of(), BlockType.SHEET_METAL, BlockType.CONCRETE);
            case WORKSHOP -> new Spec(BlockType.LOCKER, 2, List.of(BlockType.WORKBENCH), BlockType.TABLE,
                    BlockType.CONCRETE);
            case KITCHEN -> new Spec(BlockType.CABINET, 2, List.of(), BlockType.TABLE, BlockType.PAVING);
            case BATHROOM -> new Spec(BlockType.CABINET, 1, List.of(BlockType.RAIN_COLLECTOR), null, BlockType.PAVING);
            case UTILITY, BASEMENT_UTILITY -> new Spec(BlockType.LOCKER, 1, List.of(BlockType.SHEET_METAL), null,
                    BlockType.CONCRETE);
            case BEDROOM -> new Spec(BlockType.CABINET, 1, List.of(BlockType.CAMP_BED, BlockType.CAMP_BED), null,
                    BlockType.PLANK);
            default -> new Spec(BlockType.SHELF, 2, List.of(), null, BlockType.CONCRETE);
        };
    }

    private static final class Overlay {
        final BuildingPlan plan;
        final Family family;
        final Variant variant;
        final boolean minimal;
        final Layout layout;
        final int width, entry;
        final PlanVolume.Builder voxels;
        final List<Fixture> fixtures = new ArrayList<>();

        Overlay(BuildingPlan skeleton, boolean minimal) {
            plan = skeleton; family = skeleton.family(); variant = skeleton.variant(); this.minimal = minimal;
            layout = new Layout(skeleton.parcel());
            width = skeleton.parcel().frontage().dx == 0 ? skeleton.parcel().width() : skeleton.parcel().depth();
            entry = (width - 2) / 2;
            voxels = skeleton.voxels().edit();
        }

        BuildingPlan apply() {
            // The skeleton's placeholder furniture is replaced, never layered twice.
            for (Fixture fixture : plan.fixtures()) {
                if (fixture.kind() == FixtureKind.CONTAINER || fixture.kind() == FixtureKind.FURNITURE) {
                    Vec3i p = fixture.position();
                    for (int h = 0; h < fixture.height(); h++) { voxels.set(p.x(), p.y() + h, p.z(), BlockType.AIR); }
                } else {
                    fixtures.add(fixture);
                }
            }
            BlockType facade = facade(family);
            if (facade != BlockType.BRICK) {
                PlanVolume v = plan.voxels();
                for (int x = v.x0; x < v.x0 + v.width; x++) {
                    for (int z = v.z0; z < v.z0 + v.depth; z++) {
                        for (int y = v.minY; y <= v.maxY; y++) {
                            if (v.getBlock(x, y, z) == BlockType.BRICK) { voxels.set(x, y, z, facade); }
                        }
                    }
                }
            }
            for (Floor floor : plan.floors()) {
                if (floor.index() == plan.storeys()) { continue; }
                for (Room room : floor.rooms()) {
                    if (room.functions().contains(Function.ENTRY)
                            || room.functions().contains(Function.CORRIDOR_STAIRS)) {
                        continue;
                    }
                    boolean secured = floor.index() == 0 && primary(room.functions()).secured();
                    if (secured) { secure(floor, room); }
                    furnish(floor, room, secured);
                }
            }
            entrances();
            sign();
            return new BuildingPlan(plan.id(), REVISION,
                    templateName(family, plan.parcel(), plan.storeys()) + (minimal ? "-minimal" : ""),
                    family, variant, plan.parcel(), plan.storeys(), plan.basement(), plan.floors(), fixtures,
                    plan.approaches(), plan.stairs(), voxels.build());
        }

        /**
         * A concrete shell around the front block, iron bars in place of its window and its
         * door locked: forced by hand (wood, loud) or with a pry tool (metal, loud). Looters
         * already broke a picked-clean building's door.
         */
        void secure(Floor floor, Room room) {
            Bounds b = layout.bounds(room.interior());
            int slab = floor.slabY();
            for (int u = b.u0(); u <= b.u1(); u++) { wall(u, b.v1(), slab, BlockType.CONCRETE); }
            for (int v = 0; v <= b.v1(); v++) {
                wall(0, v, slab, BlockType.CONCRETE);
                wall(b.u1() + 1, v, slab, BlockType.CONCRETE);
            }
            for (int u = 0; u <= b.u1() + 1; u++) { wall(u, 0, slab, BlockType.CONCRETE); }
            for (int i = 0; i < fixtures.size(); i++) {
                Fixture f = fixtures.get(i);
                if (UrbanIds.parent(f.id()) != room.id()) { continue; }
                if (f.kind() == FixtureKind.WINDOW) {
                    set(f.position(), f.height(), BlockType.CAGE_BARS);
                    fixtures.remove(i--);
                } else if (f.kind() == FixtureKind.DOOR) {
                    if (variant == Variant.PICKED_CLEAN) {
                        set(f.position(), f.height(), BlockType.BROKEN_DOOR);
                        fixtures.remove(i--);
                    } else {
                        BlockType door = family == Family.SECURITY ? BlockType.METAL_DOOR : BlockType.WOOD_DOOR;
                        set(f.position(), f.height(), door);
                        fixtures.set(i, new Fixture(f.id(), f.kind(), f.position(), f.height(), door, f.target(), true));
                    }
                }
            }
        }

        void furnish(Floor floor, Room room, boolean secured) {
            Bounds b = layout.bounds(room.interior());
            int v0 = b.v0(), v1 = secured ? b.v1() - 1 : b.v1();
            boolean left = b.u0() == 1;
            int fu = left ? b.u0() : b.u1(), dir = left ? 1 : -1, edge = left ? b.u1() : b.u0();
            int y = floor.slabY() + 1;
            Spec spec = spec(primary(room.functions()));
            for (int u = b.u0(); u <= b.u1(); u++) {
                for (int v = b.v0(); v <= b.v1(); v++) { put(u, floor.slabY(), v, spec.floor()); }
            }
            int containers = 0, props = 0;
            // Stock fills the facade column from the far row; props start at the near row.
            List<Integer> stock = new ArrayList<>();
            for (int v = v1; v >= v0 && stock.size() < Math.min(spec.containers(), MAX_ROOM_CONTAINERS); v -= 2) {
                stock.add(v);
            }
            for (int v : stock) {
                add(room, CONTAINER_SLOTS[containers++], FixtureKind.CONTAINER, spec.container(), fu, y, v, fu + dir);
            }
            List<BlockType> fittings = new ArrayList<>(spec.props());
            // Partial occupation: survivors sleep on the floor they hold.
            if (variant == Variant.OCCUPIED && floor.index() == 0 && UrbanIds.roomIndex(room.id()) >= 6) {
                fittings.add(BlockType.BEDROLL);
            }
            int limit = stock.isEmpty() ? v1 + 1 : stock.getLast();
            for (int v = v0; v < limit && props < fittings.size(); v += 2) {
                BlockType prop = fittings.get(props);
                if (variant == Variant.PICKED_CLEAN && prop == BlockType.TABLE) { prop = BlockType.DEBRIS; }
                add(room, PROP_SLOTS[props++], FixtureKind.FURNITURE, prop, fu, y, v, fu + dir);
            }
            if (spec.island() != null && v1 - v0 >= 2) {
                for (int u = fu + 2 * dir; left ? u <= edge - 1 : u >= edge + 1; u += 2 * dir) {
                    for (int v = v0 + 1; v <= v1 - 1; v++) {
                        if (spec.island() != BlockType.SHEET_METAL) {
                            put(u, y, v, spec.island());
                        } else if (containers < MAX_ROOM_CONTAINERS && (v - v0) % 3 == 2) {
                            // A shelf bay in the rack, searched from the aisle on its facade side.
                            add(room, CONTAINER_SLOTS[containers++], FixtureKind.CONTAINER, BlockType.SHELF,
                                    u, y, v, u - dir);
                            put(u, y + 1, v, BlockType.SHEET_METAL);
                        } else {
                            put(u, y, v, BlockType.SHEET_METAL);
                            put(u, y + 1, v, BlockType.SHEET_METAL);
                        }
                    }
                }
            }
            // A shop floor's display window looks onto the street from its frontage wall.
            if (floor.index() == 0 && primary(room.functions()) == Function.SHOP_FLOOR && b.v0() == 1 && left) {
                BlockType glass = variant == Variant.DAMAGED ? BlockType.BROKEN_WINDOW : BlockType.WINDOW;
                put(fu + dir, floor.slabY() + 2, 0, glass);
                put(fu + dir, floor.slabY() + 3, 0, glass);
                fixtures.add(new Fixture(UrbanIds.fixture(room.id(), FRONT_WINDOW_SLOT), FixtureKind.WINDOW,
                        layout.pos(fu + dir, floor.slabY() + 2, 0), 2, glass,
                        layout.pos(fu + dir, floor.slabY() + 1, 1), false));
            }
            // Litter lies in the always-clear lane; it is walkable and blocks no target.
            if (variant == Variant.DAMAGED) { put(fu + dir, y, v0, BlockType.DEBRIS); }
        }

        /**
         * Family entrances: a security post's doors are metal and its rear sally port is locked;
         * a clinic locks its rear staff door; a warehouse loads through metal rear doors. The
         * street door is never locked together with the rear one.
         */
        void entrances() {
            boolean frontLocked = fixtures.stream().anyMatch(f -> f.locked() && isEntrance(f, true));
            for (int i = 0; i < fixtures.size(); i++) {
                Fixture f = fixtures.get(i);
                if (family == Family.SECURITY && isEntrance(f, true) && f.block() != BlockType.METAL_DOOR) {
                    set(f.position(), f.height(), BlockType.METAL_DOOR);
                    fixtures.set(i, new Fixture(f.id(), f.kind(), f.position(), f.height(), BlockType.METAL_DOOR,
                            f.target(), f.locked()));
                }
                if (!isEntrance(f, false)) { continue; }
                BlockType door = f.block();
                boolean locked = false;
                switch (family) {
                    case SECURITY -> { door = BlockType.METAL_DOOR; locked = !frontLocked && !minimal; }
                    case CLINIC -> locked = !frontLocked && !minimal;
                    case WAREHOUSE -> door = BlockType.METAL_DOOR;
                    default -> { }
                }
                set(f.position(), f.height(), door);
                fixtures.set(i, new Fixture(f.id(), f.kind(), f.position(), f.height(), door, f.target(), locked));
            }
        }

        /** Entrance leaves are room-0 doors in slots 8 and 10 (primary) or 9 and 11 (rear). */
        boolean isEntrance(Fixture f, boolean primary) {
            int slot = UrbanIds.slotIndex(f.id());
            return f.kind() == FixtureKind.DOOR && UrbanIds.roomIndex(f.id()) == 0 && UrbanIds.floorIndex(f.id()) == 0
                    && slot >= 8 && slot <= 11 && (slot % 2 == 0) == primary;
        }

        /** A board over the primary entrance; a damaged building has lost part of it. */
        void sign() {
            int y = UrbanDatum.slabY(0) + 3;
            for (int u = entry - 1; u <= entry + 2; u++) {
                put(u, y, 0, variant == Variant.DAMAGED && u == entry + 2 ? facade(family) : BlockType.SIGN);
            }
        }

        void add(Room room, int slot, FixtureKind kind, BlockType block, int u, int y, int v, int targetU) {
            Vec3i at = layout.pos(u, y, v);
            voxels.set(at.x(), at.y(), at.z(), block);
            fixtures.add(new Fixture(UrbanIds.fixture(room.id(), slot), kind, at, 1, block,
                    layout.pos(targetU, y, v), false));
        }

        void wall(int u, int v, int slab, BlockType block) {
            for (int h = 1; h <= 3; h++) { put(u, slab + h, v, block); }
        }

        void set(Vec3i p, int height, BlockType block) {
            for (int h = 0; h < height; h++) { voxels.set(p.x(), p.y() + h, p.z(), block); }
        }

        void put(int u, int y, int v, BlockType block) {
            Vec3i p = layout.pos(u, y, v);
            voxels.set(p.x(), p.y(), p.z(), block);
        }
    }
}
