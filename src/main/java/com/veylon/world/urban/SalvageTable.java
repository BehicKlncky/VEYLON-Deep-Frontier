package com.veylon.world.urban;

import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.item.ToolKind;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Fixtures;
import com.veylon.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What taking something apart gives back in a layout-fourteen city (urban milestone 18): one entry
 * for every urban furniture kind, the doors, the guard rails and signs, the workshop's bench and
 * the parts of a burnt-out car.
 *
 * <p>An entry names the seconds of work it takes by hand, the tool that halves them, whether that
 * tool is needed at all (metal needs a pry tool), whether the work rings like metal, and the
 * recovered wood, metal, textiles and parts a sound piece gives. Wear takes some of that back:
 * <ul>
 *   <li><b>Sound</b>: the whole yield. Anything the player built or carried in, and every piece
 *       of a held building, where survivors kept it up.</li>
 *   <li><b>Worn</b>: one less of each, keeping at least one of the first. One piece in four of an
 *       intact building, every piece of a picked-clean one, a half of a damaged one's, and every
 *       wreck that did not burn.</li>
 *   <li><b>Wrecked</b>: half of each, rounded down, keeping at least one of the first. The other
 *       half of a damaged building's, and a burnt-out car, whose cabin keeps no seat fabric.</li>
 * </ul>
 * Wear is a pure function of the plan and the cell (seed, layout, position), so it is the same in
 * every session and needs no saving; what has already been taken apart is gone as a recorded edit.
 *
 * <p>Pure: it reads the block, the plans and whether a cell carries a recorded edit, and never
 * creates a chunk or an inventory.
 */
public final class SalvageTable {
    /** How worn a piece is. */
    public enum Condition {
        SOUND("sound"), WORN("worn"), WRECKED("wrecked");

        public final String label;

        Condition(String label) { this.label = label; }
    }

    /** One kind of salvage: the work it takes and what a sound piece gives. */
    public record Entry(String noun, float seconds, ToolKind tool, boolean needsTool, boolean metal,
                        List<ItemStack> yields) {
        public Entry {
            yields = List.copyOf(yields);
        }
    }

    private static final int LAYOUT = UrbanRevision.LAYOUT_REFUGES;

    private SalvageTable() { }

    /** True in a world whose rules include dismantling: layout fourteen. */
    public static boolean applies(World w) {
        return w != null && w.urban != null && w.urban.hasRefuges();
    }

    /**
     * The salvage of the block at a cell, or null for one that is not taken apart: materials,
     * stairs, glass, debris and everything legacy keep their own break rules. A door is taken down
     * whole, open; a closed door still has its entry, which {@link #refusal} explains.
     */
    public static Entry entry(World w, Vec3i p, BlockType t) {
        if (!applies(w)) { return null; }
        return switch (t) {
            case TABLE -> wood("table", 3f, ItemType.PLANK, 3);
            case SHELF -> wood("shelving unit", 4f, ItemType.PLANK, 2, ItemType.SCRAP, 1);
            case CABINET -> wood("cabinet", 4f, ItemType.PLANK, 3, ItemType.SCRAP, 1);
            case LOCKER -> metal("metal locker", 6f, ItemType.SCRAP, 3, ItemType.SHEET_METAL, 1);
            case SIGN -> wood("painted sign", 2f, ItemType.PLANK, 2);
            case RAILING -> metal("guard rail", 4f, ItemType.SCRAP, 2);
            case WOOD_DOOR, WOOD_DOOR_OPEN -> wood("wooden door", 5f, ItemType.PLANK, 3, ItemType.SCRAP, 1);
            case METAL_DOOR, METAL_DOOR_OPEN -> metal("metal door", 7f, ItemType.SHEET_METAL, 1, ItemType.SCRAP, 2);
            case CAMP_BED -> new Entry("bed", 3f, ToolKind.KNIFE, false, false,
                    stacks(ItemType.FIBER, 4, ItemType.PLANK, 1, ItemType.SCRAP, 1));
            case BEDROLL -> new Entry("bedroll", 2f, ToolKind.KNIFE, false, false, stacks(ItemType.FIBER, 4));
            case WORKBENCH -> wood("workbench", 5f, ItemType.PLANK, 3, ItemType.SCRAP, 2, ItemType.SPARE_PARTS, 1);
            case RAIN_COLLECTOR -> new Entry("basin", 3f, ToolKind.PICKAXE, false, true, stacks(ItemType.SCRAP, 2));
            case CAR_WRECK -> wreck(w, p);
            default -> null;
        };
    }

    private static Entry wreck(World w, Vec3i p) {
        VehicleSites.Wreck wreck = w.urban.wreckAt(p.x(), p.y(), p.z());
        if (wreck == null) {
            // A wreck cell set down where no car was planned (Creative staging): plain body metal.
            return metal("wrecked car body", 5f, ItemType.SCRAP, 3);
        }
        return switch (wreck.partAt(p.x(), p.y(), p.z())) {
            case ENGINE -> metal("wrecked car's engine", 7f, ItemType.SPARE_PARTS, 2, ItemType.SCRAP, 1);
            case CABIN -> wreck.burnt() ? metal("burnt-out car cabin", 4f, ItemType.SCRAP, 2)
                    : metal("wrecked car cabin", 4f, ItemType.FIBER, 3, ItemType.SCRAP, 1);
            case BODY -> metal("wrecked car body", 5f, ItemType.SCRAP, 3);
        };
    }

    private static Entry wood(String noun, float seconds, Object... pairs) {
        return new Entry(noun, seconds, ToolKind.AXE, false, false, stacks(pairs));
    }

    private static Entry metal(String noun, float seconds, Object... pairs) {
        return new Entry(noun, seconds, ToolKind.PICKAXE, true, true, stacks(pairs));
    }

    private static List<ItemStack> stacks(Object... pairs) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) { out.add(new ItemStack((ItemType) pairs[i], (Integer) pairs[i + 1])); }
        return out;
    }

    /**
     * Why the piece at a cell cannot be taken apart as it stands, or null when it can: a door is
     * taken down open and whole, never forced; metal needs a pry tool.
     *
     * @param tool the kind of the held tool, {@link ToolKind#NONE} for bare hands or anything else
     */
    public static String refusal(World w, Vec3i p, BlockType t, ToolKind tool) {
        Entry e = entry(w, p, t);
        if (e == null) { return null; }
        Fixtures.Kind kind = Fixtures.kindOf(t);
        if (kind != null && kind.door) {
            if (!Fixtures.isOpen(t)) {
                return "Open the " + e.noun() + " to take it off its hinges";
            }
            int bottom = Fixtures.stackBottom(w, p.x(), p.y(), p.z(), kind);
            if (Fixtures.stackHeight(w, p.x(), bottom, p.z(), kind) < 2) {
                return "Half a door: break it out instead";
            }
        }
        if (e.needsTool() && tool != e.tool()) {
            return "Needs a pry tool to strip the " + e.noun();
        }
        return null;
    }

    /** How worn the piece at a cell is. */
    public static Condition condition(World w, Vec3i p, BlockType t) {
        if (t == BlockType.CAR_WRECK) {
            VehicleSites.Wreck wreck = w.urban.wreckAt(p.x(), p.y(), p.z());
            return wreck != null && wreck.burnt() ? Condition.WRECKED : Condition.WORN;
        }
        if (w.changedBlocks.containsKey(p)) {
            return Condition.SOUND; // built, carried in or re-hung by the player
        }
        BuildingPlan building = w.urban.buildingAt(p.x(), p.z());
        if (building == null) { return Condition.WORN; }
        int roll = UrbanHash.range(0, 3, w.seed, LAYOUT, UrbanSalts.SALVAGE_CONDITION, p.x(), p.y(), p.z());
        return switch (building.variant()) {
            case OCCUPIED -> Condition.SOUND;
            case INTACT -> roll == 0 ? Condition.WORN : Condition.SOUND;
            case PICKED_CLEAN -> Condition.WORN;
            case DAMAGED -> roll < 2 ? Condition.WORN : Condition.WRECKED;
        };
    }

    /** What taking apart the piece at a cell gives, after its wear; empty for anything else. */
    public static List<ItemStack> yields(World w, Vec3i p, BlockType t) {
        Entry e = entry(w, p, t);
        if (e == null) { return List.of(); }
        Condition c = condition(w, p, t);
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < e.yields().size(); i++) {
            ItemStack base = e.yields().get(i);
            int count = switch (c) {
                case SOUND -> base.count;
                case WORN -> base.count - 1;
                case WRECKED -> base.count / 2;
            };
            if (i == 0) { count = Math.max(1, count); }
            if (count > 0) { out.add(new ItemStack(base.type, count)); }
        }
        return out;
    }

    /** A short list of what a yield holds, such as "3 planks, 1 scrap metal". */
    public static String describe(List<ItemStack> yields) {
        StringBuilder sb = new StringBuilder();
        for (ItemStack s : yields) {
            if (!sb.isEmpty()) { sb.append(", "); }
            String name = s.type.displayName.toLowerCase(Locale.ROOT);
            // Metal and fibre are counted as they come; only planks and the like take a plural.
            boolean mass = name.endsWith("s") || name.endsWith("metal") || name.equals("fiber");
            sb.append(s.count).append(' ').append(s.count == 1 || mass ? name : name + "s");
        }
        return sb.toString();
    }

    /**
     * The held building whose people own the piece at a cell, or {@link UrbanIds#NONE}: the same
     * rule as their storage (a held walk-up throughout, a held commercial or civic building at
     * street level and below, a compound's every storey, a hidden refuge's own room), applied to the
     * planned fixture there. Streets, wrecks and anything the player placed belong to nobody.
     */
    public static long survivorOwner(World w, Vec3i p) {
        if (!applies(w) || w.changedBlocks.containsKey(p)) { return UrbanIds.NONE; }
        BuildingPlan building = w.urban.buildingAt(p.x(), p.z());
        if (building == null) { return UrbanIds.NONE; }
        for (BuildingPlan.Fixture f : building.fixtures()) {
            Vec3i q = f.position();
            if (q.x() == p.x() && q.z() == p.z() && p.y() >= q.y() && p.y() < q.y() + Math.max(1, f.height())) {
                return UrbanContainers.survivorOwned(w, new UrbanContainers.Planned(building, f, null))
                        ? building.id() : UrbanIds.NONE;
            }
        }
        return UrbanIds.NONE;
    }
}
