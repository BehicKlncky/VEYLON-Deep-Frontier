package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.urban.BuildingPlan.Fixture;
import com.veylon.world.urban.BuildingPlan.FixtureKind;
import com.veylon.world.urban.BuildingPlan.Floor;
import com.veylon.world.urban.BuildingPlan.Function;
import com.veylon.world.urban.BuildingPlan.Room;
import com.veylon.world.urban.BuildingPlan.Variant;

import java.util.ArrayList;
import java.util.List;

/**
 * Frozen revision-two furnishings. The entire building is furnished before a chunk slice is
 * stamped; no placement consults neighbours, generated chunks or mutable interaction state.
 * Furniture hugs the facade, leaving the interior interaction lane, anchors and core clear.
 * Containers reserve identities and functions only: package 07 owns their contents.
 */
public final class BuildingFurnishing {
    public static final int MAX_ROOM_FIXTURES = 6;
    public static final int MAX_ROOM_CONTAINERS = 3;
    public static final int MIN_FLAT_CONTAINERS = 3;
    public static final int MAX_FLAT_CONTAINERS = 6;

    /** Derived plan metadata, never persisted as an enum ordinal. */
    public enum ContainerUse {
        KITCHEN_CABINET, FRIDGE, PANTRY_SHELF, BATHROOM_CABINET, WARDROBE,
        DRESSER_DESK, UTILITY_LOCKER, SHOP_SHELF, STOCKROOM_CRATE,
        // Template revision five: secured dispensary and armoury stock, treatment rooms, racks and tools.
        MEDICINE_CABINET, ARMORY_LOCKER, TREATMENT_CABINET, WAREHOUSE_RACK, TOOL_CHEST
    }

    private BuildingFurnishing() { }

    /** Map a reserved container to its contextual role without allocating loot or an inventory. */
    public static ContainerUse containerUse(BuildingPlan plan, Fixture fixture) {
        if (fixture.kind() != FixtureKind.CONTAINER) {
            throw new IllegalArgumentException("fixture is not a searchable reservation");
        }
        long roomId = UrbanIds.parent(fixture.id());
        for (Floor floor : plan.floors()) {
            for (Room room : floor.rooms()) {
                if (room.id() != roomId) { continue; }
                List<Function> functions = room.functions();
                // Revision-five functions never occur in earlier rooms, so checking them first
                // leaves every frozen walk-up role unchanged.
                if (functions.contains(Function.PHARMACY)) { return ContainerUse.MEDICINE_CABINET; }
                if (functions.contains(Function.SECURITY)) { return ContainerUse.ARMORY_LOCKER; }
                if (functions.contains(Function.CLINIC)) { return ContainerUse.TREATMENT_CABINET; }
                if (functions.contains(Function.CLASSROOM) || functions.contains(Function.OFFICE)) {
                    return ContainerUse.DRESSER_DESK;
                }
                if (functions.contains(Function.WAREHOUSE)) { return ContainerUse.WAREHOUSE_RACK; }
                if (functions.contains(Function.WORKSHOP)) { return ContainerUse.TOOL_CHEST; }
                // Milestone 14: a station's maintenance lockers, and a hidden cellar's stash of food.
                if (functions.contains(Function.SERVICE)) { return ContainerUse.UTILITY_LOCKER; }
                if (functions.contains(Function.HIDDEN_REFUGE)) { return ContainerUse.PANTRY_SHELF; }
                if (functions.contains(Function.KITCHEN)) {
                    return UrbanIds.slotIndex(fixture.id()) == 5 ? ContainerUse.FRIDGE : ContainerUse.KITCHEN_CABINET;
                }
                if (functions.contains(Function.BATHROOM)) { return ContainerUse.BATHROOM_CABINET; }
                if (functions.contains(Function.BEDROOM)) { return ContainerUse.WARDROBE; }
                if (functions.contains(Function.SHOP_FLOOR)) { return ContainerUse.SHOP_SHELF; }
                if (functions.contains(Function.STOCKROOM) || functions.contains(Function.BASEMENT_STORAGE)) {
                    return ContainerUse.STOCKROOM_CRATE;
                }
                if (functions.contains(Function.UTILITY) || functions.contains(Function.BASEMENT_UTILITY)) {
                    return ContainerUse.UTILITY_LOCKER;
                }
                if (functions.contains(Function.STORAGE)) { return ContainerUse.PANTRY_SHELF; }
                return ContainerUse.DRESSER_DESK;
            }
        }
        throw new IllegalArgumentException("container has no room in building");
    }

    static BuildingPlan apply(BuildingPlan original) {
        if (original.templateRevision() != BuildingTemplates.REVISION) {
            throw new IllegalArgumentException("furnishing requires the frozen revision-one structure");
        }
        PlanVolume.Builder voxels = original.voxels().edit();
        List<Fixture> fixtures = new ArrayList<>();
        // The original catalogue's sparse placeholders are replaced, never layered twice.
        for (Fixture fixture : original.fixtures()) {
            if (fixture.kind() == FixtureKind.CONTAINER || fixture.kind() == FixtureKind.FURNITURE) {
                Vec3i p = fixture.position();
                for (int h = 0; h < fixture.height(); h++) {
                    voxels.set(p.x(), p.y() + h, p.z(), BlockType.AIR);
                }
            } else { fixtures.add(fixture); }
        }
        Layout layout = new Layout(original.parcel());
        for (Floor floor : original.floors()) {
            if (floor.index() == original.storeys()) { continue; }
            for (Room room : floor.rooms()) {
                if (room.functions().contains(Function.ENTRY) || room.functions().contains(Function.CORRIDOR_STAIRS)) {
                    continue;
                }
                furnish(original, floor, room, layout, voxels, fixtures);
            }
            if (original.variant() == Variant.DAMAGED) { brokenPartition(original, floor, layout, voxels); }
        }
        return new BuildingPlan(original.id(), UrbanRevision.BUILDING_TEMPLATE_FURNISHED,
                original.template() + "-furnished", original.family(), original.variant(), original.parcel(),
                original.storeys(), original.basement(), original.floors(), fixtures, original.approaches(),
                original.stairs(), voxels.build());
    }

    private static void furnish(BuildingPlan plan, Floor floor, Room room, Layout layout,
                                PlanVolume.Builder voxels, List<Fixture> fixtures) {
        Bounds bounds = layout.bounds(room.interior());
        int facade = bounds.u0 == 1 ? bounds.u0 : bounds.u1;
        int lane = facade == bounds.u0 ? facade + 1 : facade - 1;
        int y = floor.slabY() + 1;
        List<Function> functions = room.functions();
        BlockType container = functions.contains(Function.KITCHEN) || functions.contains(Function.BATHROOM)
                || functions.contains(Function.BEDROOM) ? BlockType.CABINET
                : functions.contains(Function.UTILITY) || functions.contains(Function.BASEMENT_UTILITY)
                ? BlockType.LOCKER : BlockType.SHELF;
        add(room, 2, FixtureKind.CONTAINER, container, facade, bounds.v1, lane, y, layout, voxels, fixtures);

        BlockType flooring = functions.contains(Function.BEDROOM) || functions.contains(Function.LIVING)
                ? BlockType.PLANK : functions.contains(Function.KITCHEN) || functions.contains(Function.BATHROOM)
                ? BlockType.PAVING : BlockType.CONCRETE;
        for (int u = bounds.u0; u <= bounds.u1; u++) {
            for (int v = bounds.v0; v <= bounds.v1; v++) {
                Vec3i p = layout.pos(u, floor.slabY(), v);
                voxels.set(p.x(), p.y(), p.z(), flooring);
            }
        }
        // There are always at least two cells along the facade. The first prop never shares
        // the container cell, while its target stays on the reserved one-cell interaction lane.
        BlockType prop = functions.contains(Function.BEDROOM) ? BlockType.CAMP_BED
                : functions.contains(Function.BATHROOM) ? BlockType.RAIN_COLLECTOR
                : functions.contains(Function.KITCHEN) || functions.contains(Function.LIVING)
                || functions.contains(Function.SHOP_FLOOR) ? BlockType.TABLE
                : functions.contains(Function.UTILITY) || functions.contains(Function.BASEMENT_UTILITY)
                ? BlockType.SHEET_METAL : BlockType.DEBRIS;
        if (plan.variant() == Variant.PICKED_CLEAN && prop == BlockType.TABLE) { prop = BlockType.DEBRIS; }
        add(room, 3, FixtureKind.FURNITURE, prop, facade, bounds.v0, lane, y, layout, voxels, fixtures);

        if (bounds.v1 - bounds.v0 >= 2) {
            BlockType extra = functions.contains(Function.KITCHEN)
                    || functions.contains(Function.BEDROOM) && functions.contains(Function.BATHROOM)
                    ? BlockType.RAIN_COLLECTOR
                    : functions.contains(Function.BEDROOM) || functions.contains(Function.LIVING) ? BlockType.TABLE
                    : functions.contains(Function.SHOP_FLOOR) ? BlockType.SHELF : BlockType.DEBRIS;
            // Picked-clean rooms retain their searchable identities but lose spare furnishings.
            if (plan.variant() == Variant.PICKED_CLEAN && extra != BlockType.SHELF) { extra = BlockType.DEBRIS; }
            // A single sparse growth in a damaged ground-floor room, never in the stair/core lane.
            if (plan.variant() == Variant.DAMAGED && floor.index() == 0
                    && UrbanIds.roomIndex(room.id()) == 8) { extra = BlockType.BUSH; }
            add(room, 5, extra == BlockType.SHELF ? FixtureKind.CONTAINER : FixtureKind.FURNITURE,
                    extra, facade, bounds.v0 + 1, lane, y, layout, voxels, fixtures);
        }
    }

    private static void add(Room room, int slot, FixtureKind kind, BlockType block, int u, int v,
                            int targetU, int y, Layout layout, PlanVolume.Builder voxels, List<Fixture> fixtures) {
        Vec3i position = layout.pos(u, y, v), target = layout.pos(targetU, y, v);
        voxels.set(position.x(), position.y(), position.z(), block);
        fixtures.add(new Fixture(UrbanIds.fixture(room.id(), slot), kind, position, 1, block, target, false));
    }

    /** A removed upper partition panel reads as damage without removing support or narrowing routes. */
    private static void brokenPartition(BuildingPlan plan, Floor floor, Layout layout, PlanVolume.Builder voxels) {
        int u = 1, v = 3;
        Vec3i panel = layout.pos(u, floor.slabY() + 1, v);
        // Basement/shop/minimal variants have no cross partition here: leave their facade intact.
        if (plan.voxels().getBlock(panel.x(), panel.y(), panel.z()) != BlockType.PLASTER) { return; }
        for (int h = 0; h < 3; h++) {
            voxels.set(panel.x(), panel.y() + h, panel.z(), h == 0 ? BlockType.DEBRIS : BlockType.AIR);
        }
    }

    /** Inclusive skeleton-local bounds: u along the frontage, v away from it. */
    record Bounds(int u0, int v0, int u1, int v1) { }

    /** Converts between world cells and the skeleton's frontage-local (u, v) frame. */
    record Layout(ParcelPlan parcel) {
        Bounds bounds(ParcelPlan.Rect rect) {
            int a = u(rect.x0(), rect.z0()), b = u(rect.x1(), rect.z1());
            int c = v(rect.x0(), rect.z0()), d = v(rect.x1(), rect.z1());
            return new Bounds(Math.min(a, b), Math.min(c, d), Math.max(a, b), Math.max(c, d));
        }

        int u(int x, int z) {
            return parcel.frontage().dx == 0 ? x - parcel.x0() : z - parcel.z0();
        }

        int v(int x, int z) {
            return switch (parcel.frontage()) {
                case NORTH -> z - parcel.z0();
                case SOUTH -> parcel.z1() - z;
                case WEST -> x - parcel.x0();
                case EAST -> parcel.x1() - x;
            };
        }

        Vec3i pos(int u, int y, int v) {
            return switch (parcel.frontage()) {
                case NORTH -> new Vec3i(parcel.x0() + u, y, parcel.z0() + v);
                case SOUTH -> new Vec3i(parcel.x0() + u, y, parcel.z1() - v);
                case WEST -> new Vec3i(parcel.x0() + v, y, parcel.z0() + u);
                case EAST -> new Vec3i(parcel.x1() - v, y, parcel.z0() + u);
            };
        }
    }
}
