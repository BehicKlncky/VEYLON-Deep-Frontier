package com.veylon.world.urban;

import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;

/** Frozen content-one tables. Pure rolls never consume a simulation or presentation RNG. */
public final class LootTables {
    /** Stable names, not ordinal save ids. Clinic/warehouse/security roles also serve later catalogues. */
    public enum Table { KITCHEN, FRIDGE, PANTRY, BATHROOM, CLINIC, WARDROBE, DESK,
        UTILITY, WAREHOUSE, GROCER, SHOP, SECURED }

    private record Supply(ItemType type, int min, int max, int chance) { }
    private static Supply s(ItemType type, int min, int max, int chance) {
        return new Supply(type, min, max, chance);
    }
    private static final Supply[][] TABLES = {
        {s(ItemType.DRIED_BERRY,1,3,100), s(ItemType.WATERSKIN_CLEAN,1,1,100), s(ItemType.BANDAGE,1,2,30)},
        {s(ItemType.DRIED_MEAT,1,2,75), s(ItemType.WATERSKIN_CLEAN,1,1,100)},
        {s(ItemType.DRIED_BERRY,1,3,100), s(ItemType.WATERSKIN_CLEAN,1,2,100), s(ItemType.TORCH,1,2,45)},
        {s(ItemType.BANDAGE,1,3,100), s(ItemType.ANTISEPTIC,1,1,50), s(ItemType.MEDICINE,1,1,15)},
        {s(ItemType.BANDAGE,2,4,100), s(ItemType.ANTISEPTIC,1,2,90), s(ItemType.MEDICINE,1,2,70), s(ItemType.SPLINT,1,1,50)},
        {s(ItemType.RAIN_CLOAK,1,1,65), s(ItemType.HIDE_BOOTS,1,1,45), s(ItemType.FIBER,2,5,100)},
        {s(ItemType.BANDAGE,1,2,50), s(ItemType.TORCH,1,1,65), s(ItemType.SCRAP,1,2,70)},
        {s(ItemType.SCRAP,2,5,100), s(ItemType.IRON_PICKAXE,1,1,65), s(ItemType.TORCH,1,3,100)},
        {s(ItemType.SCRAP,3,7,100), s(ItemType.IRON_PICKAXE,1,1,80), s(ItemType.IRON_INGOT,1,3,55), s(ItemType.TORCH,1,3,85)},
        {s(ItemType.DRIED_BERRY,2,4,100), s(ItemType.WATERSKIN_CLEAN,1,2,100), s(ItemType.BANDAGE,1,2,50)},
        {s(ItemType.WATERSKIN_CLEAN,1,1,100), s(ItemType.BANDAGE,1,2,65), s(ItemType.TORCH,1,2,75)},
        {s(ItemType.SCRAP,2,4,100), s(ItemType.MUSKET_BALL,1,3,65), s(ItemType.BLACK_POWDER,1,2,55), s(ItemType.FLINTLOCK_PISTOL,1,1,12)}
    };

    private LootTables() { }

    /** Function first, then specialist building/district context; common necessities have several sources. */
    public static Table table(BuildingPlan building, BuildingPlan.Fixture fixture, UrbanDistrict district) {
        var use = BuildingFurnishing.containerUse(building, fixture);
        if (building.variant() == BuildingPlan.Variant.OCCUPIED
                && (use == BuildingFurnishing.ContainerUse.UTILITY_LOCKER
                || use == BuildingFurnishing.ContainerUse.STOCKROOM_CRATE)) { return Table.SECURED; }
        return switch (use) {
            case KITCHEN_CABINET -> Table.KITCHEN;
            case FRIDGE -> Table.FRIDGE;
            case PANTRY_SHELF -> Table.PANTRY;
            case BATHROOM_CABINET -> district == UrbanDistrict.CIVIC ? Table.CLINIC : Table.BATHROOM;
            case WARDROBE -> Table.WARDROBE;
            case DRESSER_DESK -> Table.DESK;
            case UTILITY_LOCKER -> Table.UTILITY;
            case STOCKROOM_CRATE -> Table.WAREHOUSE;
            case SHOP_SHELF -> district == UrbanDistrict.CIVIC ? Table.CLINIC
                    : building.family() == BuildingPlan.Family.SHOP ? Table.GROCER : Table.SHOP;
        };
    }

    /** Null is a rolled empty result, never permission to roll again. At most four occupied slots. */
    public static Inventory roll(long seed, int content, long id, Table table,
                                 UrbanDistrict district, BuildingPlan.Variant variant) {
        UrbanRevision.requireSupportedContent(content);
        if (variant == BuildingPlan.Variant.PICKED_CLEAN) { return null; }
        Inventory result = null;
        Supply[] supplies = TABLES[table.ordinal()];
        for (int i = 0; i < supplies.length; i++) {
            Supply supply = supplies[i];
            int bias = switch (district) {
                case RESIDENTIAL -> table == Table.WARDROBE ? 10 : 0;
                case COMMERCIAL -> table == Table.GROCER || table == Table.SHOP ? 10 : 0;
                case INDUSTRIAL -> table == Table.UTILITY || table == Table.WAREHOUSE ? 15 : 0;
                case FLOODED -> -15;
                case CIVIC -> table == Table.CLINIC || table == Table.BATHROOM ? 15 : 0;
                case QUARANTINE -> table == Table.SECURED ? 10 : -10;
            };
            if (UrbanHash.range(0,99,seed,content,UrbanSalts.LOOT_PRESENCE,id,table.ordinal(),i)
                    >= Math.min(100, supply.chance + bias)) { continue; }
            int count = UrbanHash.range(supply.min,supply.max,seed,content,UrbanSalts.LOOT_COUNT,id,table.ordinal(),i);
            ItemStack stack = new ItemStack(supply.type,count);
            double condition = UrbanHash.unit(seed,content,UrbanSalts.LOOT_CONDITION,id,table.ordinal(),i);
            if (stack.type.hasDurability()) { stack.durability *= (float) (.35 + .5 * condition); }
            if (stack.type.spoils()) { stack.freshness *= (float) (.4 + .45 * condition); }
            if (result == null) { result = new Inventory(UrbanContainers.SLOTS); }
            result.set(i,stack);
        }
        return result;
    }

    public static float searchSeconds(Table table) {
        return switch (table) { case WARDROBE, WAREHOUSE, SECURED -> 3.5f; case DESK, BATHROOM -> 1.5f; default -> 2.5f; };
    }
}
