package com.veylon.world.urban;

import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.util.Vec3i;
import com.veylon.world.urban.BuildingFurnishing.ContainerUse;
import com.veylon.world.urban.BuildingPlan.Family;
import com.veylon.world.urban.BuildingPlan.Fixture;

/**
 * Where layout sixteen's city keeps the parts the old relay and a radio workstation need (urban
 * milestone 20). Each kind has two independent kinds of source, spread over the districts, so no
 * one building, district or person is ever the only way to one:
 *
 * <ul>
 *   <li><b>A transmitter module</b> in the first armoury locker of every security post (behind its
 *       metal lock), and one kept by every guarded stronghold in its stores, in the first stash
 *       container outside its clinic.</li>
 *   <li><b>Power cells</b> in the first tool chest of every warehouse (every region keeps one) and
 *       the first utility locker below the roof of every clinic.</li>
 *   <li><b>Antenna cable</b> in the first utility locker below the roof of every office block and the
 *       roof maintenance locker of any office, school or security post.</li>
 * </ul>
 *
 * <p>The part is added to that container's single first roll, so it is found by searching like
 * anything else, is somebody's property where the building is held, and is never put back: a
 * container that was picked clean before the city fell rolls nothing, part included. Pure in the
 * frozen plans; nothing here touches a chunk or a ledger.
 */
public final class CommsComponents {

    /** A kind of part; the bit is the missions ledger's. */
    public enum Kind {
        TRANSMITTER(1, ItemType.TRANSMITTER_MODULE, "transmitter module"),
        CELLS(2, ItemType.POWER_CELL, "power cells"),
        ANTENNA(4, ItemType.ANTENNA_CABLE, "antenna cable");

        public final int bit;
        public final ItemType item;
        public final String noun;

        Kind(int bit, ItemType item, String noun) {
            this.bit = bit;
            this.item = item;
            this.noun = noun;
        }

        /** The kind an item is, or null. */
        public static Kind of(ItemType item) {
            for (Kind k : values()) {
                if (k.item == item) { return k; }
            }
            return null;
        }
    }

    /** Every kind's bit together. */
    public static final int ALL = Kind.TRANSMITTER.bit | Kind.CELLS.bit | Kind.ANTENNA.bit;

    private CommsComponents() {
    }

    /**
     * The part a planned container's first roll holds, or null. Only layout sixteen keeps any.
     *
     * @param b a building plan, a transit station's included
     * @param f one of its container reservations
     */
    public static Kind sourceOf(UrbanWorldState state, BuildingPlan b, Fixture f) {
        if (state == null || !state.hasMissions() || f.kind() != BuildingPlan.FixtureKind.CONTAINER) { return null; }
        if (f.id() == strongholdModule(state, b.id())) { return Kind.TRANSMITTER; }
        if (b.family() == Family.TRANSIT) { return null; }
        ContainerUse use = BuildingFurnishing.containerUse(b, f);
        boolean roof = UrbanIds.floorIndex(f.id()) == b.storeys();
        return switch (b.family()) {
            case SECURITY -> use == ContainerUse.ARMORY_LOCKER && first(b, f, use, false) ? Kind.TRANSMITTER
                    : use == ContainerUse.UTILITY_LOCKER && roof ? Kind.ANTENNA : null;
            case WAREHOUSE -> use == ContainerUse.TOOL_CHEST && first(b, f, use, false) ? Kind.CELLS : null;
            case CLINIC -> use == ContainerUse.UTILITY_LOCKER && !roof && first(b, f, use, false) ? Kind.CELLS : null;
            case OFFICE -> use == ContainerUse.UTILITY_LOCKER && (roof || first(b, f, use, false)) ? Kind.ANTENNA : null;
            case SCHOOL -> use == ContainerUse.UTILITY_LOCKER && roof ? Kind.ANTENNA : null;
            default -> null;
        };
    }

    /** True when {@code f} is the lowest-id container of its use below (or, for {@code roof}, on) the roof. */
    private static boolean first(BuildingPlan b, Fixture f, ContainerUse use, boolean roof) {
        for (Fixture o : b.fixtures()) {
            if (o.kind() != BuildingPlan.FixtureKind.CONTAINER || o.id() >= f.id()) { continue; }
            if ((UrbanIds.floorIndex(o.id()) == b.storeys()) == roof && BuildingFurnishing.containerUse(b, o) == use) {
                return false;
            }
        }
        return true;
    }

    /**
     * The container a guarded stronghold keeps its transmitter module in, when the building is one
     * of a stronghold's: the first of its stash containers (in fixture-id order) outside its clinic,
     * so in a building only friends may walk into. {@link UrbanIds#NONE} for any other building.
     */
    public static long strongholdModule(UrbanWorldState state, long building) {
        if (state == null || !state.hasMissions()) { return UrbanIds.NONE; }
        CompoundSites.Compound c = SurvivorSites.compoundOf(state, building);
        if (c == null || !c.stronghold()) { return UrbanIds.NONE; }
        for (Vec3i p : c.site().stash()) {
            BuildingPlan holder = state.buildingAt(p.x(), p.z());
            if (holder == null || holder.id() == c.clinic()) { continue; }
            for (Fixture f : holder.fixtures()) {
                if (f.kind() == BuildingPlan.FixtureKind.CONTAINER && f.position().equals(p)) { return f.id(); }
            }
        }
        return UrbanIds.NONE;
    }

    /** The cell of the container a stronghold keeps its module in, or null. */
    public static Vec3i strongholdModuleCell(UrbanWorldState state, CompoundSites.Compound c) {
        if (c == null || !c.stronghold()) { return null; }
        long id = strongholdModule(state, c.site().id());
        if (id == UrbanIds.NONE) { return null; }
        for (Vec3i p : c.site().stash()) {
            BuildingPlan holder = state.buildingAt(p.x(), p.z());
            if (holder == null) { continue; }
            for (Fixture f : holder.fixtures()) {
                if (f.id() == id) { return f.position(); }
            }
        }
        return null;
    }

    /**
     * A container's first roll with its part added in the first free slot: a fresh inventory for an
     * empty roll, unchanged for a container that holds no part or was picked clean.
     */
    public static Inventory withPart(UrbanWorldState state, BuildingPlan b, Fixture f, Inventory rolled) {
        Kind kind = sourceOf(state, b, f);
        if (kind == null || b.variant() == BuildingPlan.Variant.PICKED_CLEAN) { return rolled; }
        Inventory out = rolled == null ? new Inventory(UrbanContainers.SLOTS) : rolled;
        out.addStack(new ItemStack(kind.item, 1));
        return out;
    }
}
