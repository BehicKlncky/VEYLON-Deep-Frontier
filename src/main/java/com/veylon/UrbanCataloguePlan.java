package com.veylon;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.BuildingPlan.Family;
import com.veylon.world.urban.BuildingPlan.Fixture;
import com.veylon.world.urban.BuildingPlan.FixtureKind;
import com.veylon.world.urban.BuildingPlan.Function;
import com.veylon.world.urban.BuildingPlan.Room;
import com.veylon.world.urban.BuildingPlan.Variant;
import com.veylon.world.urban.UrbanDatum;
import com.veylon.world.urban.UrbanIds;

import java.util.Comparator;
import java.util.function.Predicate;

/**
 * The milestone-12 catalogue walk's buildings: for each commercial and civic family, the one
 * nearest the spawn among the nine regions around the origin that shows what the walk must
 * sample. The grocer is a damaged one; the clinic and security post keep their locks; the
 * school and office block have upper floors. Read from the frozen plans only.
 */
record UrbanCataloguePlan(BuildingPlan grocery, BuildingPlan clinic, BuildingPlan school, BuildingPlan office,
                          BuildingPlan security, BuildingPlan warehouse) {

    static UrbanCataloguePlan of(World world) {
        if (world.urban == null || !world.urban.hasCivicCatalogue()) {
            throw new IllegalStateException("the catalogue walk needs the civic catalogue generator");
        }
        Predicate<BuildingPlan> unheld = b -> b.variant() != Variant.OCCUPIED;
        Predicate<BuildingPlan> locked = b -> b.variant() == Variant.INTACT || b.variant() == Variant.DAMAGED;
        return new UrbanCataloguePlan(
                nearest(world, b -> b.family() == Family.GROCERY && b.variant() == Variant.DAMAGED),
                nearest(world, b -> b.family() == Family.CLINIC && locked.test(b)),
                nearest(world, b -> b.family() == Family.SCHOOL && unheld.test(b)),
                nearest(world, b -> b.family() == Family.OFFICE && unheld.test(b)),
                nearest(world, b -> b.family() == Family.SECURITY && locked.test(b)),
                nearest(world, b -> b.family() == Family.WAREHOUSE && unheld.test(b)));
    }

    private static BuildingPlan nearest(World world, Predicate<BuildingPlan> wanted) {
        int[] spawn = world.urban.spawnColumn();
        BuildingPlan best = null;
        long bestD = Long.MAX_VALUE;
        for (int rz = -1; rz <= 1; rz++) {
            for (int rx = -1; rx <= 1; rx++) {
                for (var parcel : world.urban.regionPlan(rx, rz).parcels()) {
                    BuildingPlan b = world.urban.buildingAt(parcel.x0(), parcel.z0());
                    if (!wanted.test(b)) { continue; }
                    Vec3i a = b.approaches().getFirst();
                    long dx = a.x() - spawn[0], dz = a.z() - spawn[1], d = dx * dx + dz * dz;
                    if (d < bestD || d == bestD && b.id() < best.id()) { best = b; bestD = d; }
                }
            }
        }
        if (best == null) { throw new IllegalStateException("no suitable building for the catalogue walk"); }
        return best;
    }

    /** The middle frontage-sign board over the street door. */
    static Vec3i sign(BuildingPlan b) {
        Vec3i door = b.approaches().getFirst();
        var v = b.voxels();
        Vec3i best = null;
        int y = UrbanDatum.slabY(0) + 3;
        for (int z = v.z0; z < v.z0 + v.depth; z++) {
            for (int x = v.x0; x < v.x0 + v.width; x++) {
                if (v.getBlock(x, y, z) != BlockType.SIGN) { continue; }
                Vec3i c = new Vec3i(x, y, z);
                if (best == null || c.distSq(door.x(), y, door.z()) < best.distSq(door.x(), y, door.z())) { best = c; }
            }
        }
        if (best == null) { throw new IllegalStateException("no frontage sign on " + b.template()); }
        return best;
    }

    /** The first room on a floor whose functions include {@code function}. */
    static Room room(BuildingPlan b, int floor, Function function) {
        return b.floors().stream().filter(f -> f.index() == floor).flatMap(f -> f.rooms().stream())
                .filter(r -> r.functions().contains(function)).min(Comparator.comparingLong(Room::id))
                .orElseThrow(() -> new IllegalStateException("no " + function + " on floor " + floor + " of " + b.template()));
    }

    /** A fixture of a room: its first searchable container, else its first piece of furniture. */
    static Fixture furnishing(BuildingPlan b, Room room) {
        return b.fixtures().stream().filter(f -> UrbanIds.parent(f.id()) == room.id()
                        && (f.kind() == FixtureKind.CONTAINER || f.kind() == FixtureKind.FURNITURE))
                .min(Comparator.comparingInt((Fixture f) -> f.kind() == FixtureKind.CONTAINER ? 0 : 1)
                        .thenComparingLong(Fixture::id))
                .orElseThrow(() -> new IllegalStateException("room " + room.anchor() + " is unfurnished"));
    }

    /** A furniture fixture of a room made of {@code block}, or its first furnishing. */
    static Fixture furniture(BuildingPlan b, Room room, BlockType block) {
        return b.fixtures().stream().filter(f -> UrbanIds.parent(f.id()) == room.id() && f.block() == block)
                .min(Comparator.comparingLong(Fixture::id)).orElseGet(() -> furnishing(b, room));
    }

    /** A shelf bay set into a rack (a searchable shelf under a sheet-metal top). */
    static Fixture rackBay(BuildingPlan b, Room room) {
        return b.fixtures().stream().filter(f -> UrbanIds.parent(f.id()) == room.id() && f.kind() == FixtureKind.CONTAINER
                        && b.voxels().getBlock(f.position().x(), f.position().y() + 1, f.position().z()) == BlockType.SHEET_METAL)
                .min(Comparator.comparingLong(Fixture::id))
                .orElseThrow(() -> new IllegalStateException("no rack bay in " + b.template()));
    }

    /** The far end of the aisle through {@code cell}: aisles run away from the frontage. */
    static Vec3i aisleEnd(BuildingPlan b, Room room, Vec3i cell) {
        var r = room.interior();
        if (b.parcel().frontage().dx == 0) {
            int z = Math.abs(r.z0() - cell.z()) > Math.abs(r.z1() - cell.z()) ? r.z0() : r.z1();
            return new Vec3i(cell.x(), cell.y(), z);
        }
        int x = Math.abs(r.x0() - cell.x()) > Math.abs(r.x1() - cell.x()) ? r.x0() : r.x1();
        return new Vec3i(x, cell.y(), cell.z());
    }

    /** The secured room's locked door. */
    static Fixture securedDoor(BuildingPlan b) {
        return b.fixtures().stream().filter(f -> f.kind() == FixtureKind.DOOR && f.locked()
                && UrbanIds.slotIndex(f.id()) == 0).findFirst()
                .orElseThrow(() -> new IllegalStateException("no secured lock on " + b.template()));
    }

    /** The first leaf of the rear entrance (slot 9 of the entry hall). */
    static Fixture rearDoor(BuildingPlan b) {
        return b.fixtures().stream().filter(f -> f.kind() == FixtureKind.DOOR && UrbanIds.roomIndex(f.id()) == 0
                && UrbanIds.floorIndex(f.id()) == 0 && UrbanIds.slotIndex(f.id()) == 9).findFirst().orElseThrow();
    }

    /** The highest storey below the roof. */
    static int topFloor(BuildingPlan b) { return b.storeys() - 1; }
}
