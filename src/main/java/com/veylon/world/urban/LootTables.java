package com.veylon.world.urban;

import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;

/**
 * Frozen loot tables: the tables of content revisions one to three, and content revision four's for
 * the economy city (milestone 23). Pure rolls never consume a simulation or presentation RNG.
 */
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

    /**
     * Content revision four (milestone 23): the same twelve tables in the same order, tuned against a
     * survivor's daily needs. A flat now holds a serving or two of food and about half a litre of water
     * instead of three servings and nearly two litres; treatment is in about half of bathrooms; pry tools
     * and torches are things to look for rather than things to trip over. Grocers stay the richest food
     * and clinics the richest medicine, so a district still decides where to go. Iron ingots and black
     * powder, which no city recipe uses, give way to spare parts in warehouses and first aid in armouries.
     */
    private static final Supply[][] ECONOMY_TABLES = {
        {s(ItemType.DRIED_BERRY,1,2,60), s(ItemType.WATERSKIN_CLEAN,1,1,40), s(ItemType.BANDAGE,1,1,15)},
        {s(ItemType.DRIED_MEAT,1,1,35), s(ItemType.WATERSKIN_CLEAN,1,1,40)},
        {s(ItemType.DRIED_BERRY,1,3,70), s(ItemType.WATERSKIN_CLEAN,1,1,40), s(ItemType.TORCH,1,1,25)},
        {s(ItemType.BANDAGE,1,2,40), s(ItemType.ANTISEPTIC,1,1,20), s(ItemType.MEDICINE,1,1,5)},
        {s(ItemType.BANDAGE,1,3,90), s(ItemType.ANTISEPTIC,1,2,70), s(ItemType.MEDICINE,1,2,55), s(ItemType.SPLINT,1,1,40)},
        {s(ItemType.RAIN_CLOAK,1,1,30), s(ItemType.HIDE_BOOTS,1,1,20), s(ItemType.FIBER,1,4,85)},
        {s(ItemType.BANDAGE,1,1,20), s(ItemType.TORCH,1,1,30), s(ItemType.SCRAP,1,2,45)},
        {s(ItemType.SCRAP,1,4,85), s(ItemType.IRON_PICKAXE,1,1,12), s(ItemType.TORCH,1,1,40)},
        {s(ItemType.SCRAP,2,5,90), s(ItemType.IRON_PICKAXE,1,1,35), s(ItemType.SPARE_PARTS,1,1,15), s(ItemType.TORCH,1,2,45)},
        {s(ItemType.DRIED_BERRY,1,3,70), s(ItemType.WATERSKIN_CLEAN,1,1,55), s(ItemType.DRIED_MEAT,1,1,25), s(ItemType.BANDAGE,1,1,25)},
        {s(ItemType.WATERSKIN_CLEAN,1,1,50), s(ItemType.BANDAGE,1,1,40), s(ItemType.TORCH,1,1,40), s(ItemType.DRIED_BERRY,1,1,25)},
        {s(ItemType.SCRAP,1,3,80), s(ItemType.MUSKET_BALL,1,3,50), s(ItemType.BANDAGE,1,2,40), s(ItemType.FLINTLOCK_PISTOL,1,1,8)}
    };

    private LootTables() { }

    /**
     * Function first, then specialist building/district context; common necessities have several
     * sources. Specialist medicine and ammunition come from secured dispensaries and armouries
     * (behind a forced lock) or survivor-held storage (theft), never from an ordinary shelf.
     */
    public static Table table(BuildingPlan building, BuildingPlan.Fixture fixture, UrbanDistrict district) {
        var use = BuildingFurnishing.containerUse(building, fixture);
        if (building.survivorOwned(fixture)
                && (use == BuildingFurnishing.ContainerUse.UTILITY_LOCKER
                || use == BuildingFurnishing.ContainerUse.STOCKROOM_CRATE)) { return Table.SECURED; }
        boolean grocery = building.family() == BuildingPlan.Family.SHOP
                || building.family() == BuildingPlan.Family.GROCERY;
        return switch (use) {
            case KITCHEN_CABINET -> Table.KITCHEN;
            case FRIDGE -> Table.FRIDGE;
            case PANTRY_SHELF -> Table.PANTRY;
            case BATHROOM_CABINET -> district == UrbanDistrict.CIVIC ? Table.CLINIC : Table.BATHROOM;
            case WARDROBE -> Table.WARDROBE;
            case DRESSER_DESK -> Table.DESK;
            case UTILITY_LOCKER -> Table.UTILITY;
            // A grocer's back room holds packaged food, not hardware.
            case STOCKROOM_CRATE -> building.family() == BuildingPlan.Family.GROCERY ? Table.PANTRY : Table.WAREHOUSE;
            case SHOP_SHELF -> district == UrbanDistrict.CIVIC ? Table.CLINIC : grocery ? Table.GROCER : Table.SHOP;
            case MEDICINE_CABINET -> Table.CLINIC;
            case ARMORY_LOCKER -> Table.SECURED;
            case TREATMENT_CABINET -> Table.BATHROOM;
            case WAREHOUSE_RACK -> Table.WAREHOUSE;
            case TOOL_CHEST -> Table.UTILITY;
        };
    }

    /**
     * The table of a container under a content revision. Content four keeps every mapping above but one:
     * a walk-up's basement storage is household storage, rolled from the utility table, so a warehouse's
     * spare parts stay in warehouses, workshops and shop stockrooms (milestone 23).
     */
    public static Table table(BuildingPlan building, BuildingPlan.Fixture fixture, UrbanDistrict district, int content) {
        Table table = table(building, fixture, district);
        return content == UrbanRevision.ECONOMY_CONTENT && table == Table.WAREHOUSE
                && building.family() == BuildingPlan.Family.APARTMENT ? Table.UTILITY : table;
    }

    /**
     * Null is a rolled empty result, never permission to roll again. At most four occupied slots.
     *
     * <p>A district's bias moves each supply's chance in percentage points under content revisions one
     * to three. Content four moves it by that percentage of the chance instead, so a district makes a
     * rare find rarer or commoner without ever erasing it: a flooded flat's pry tool is waterlogged ten
     * times in a hundred, not gone (milestone 23).
     */
    public static Inventory roll(long seed, int content, long id, Table table,
                                 UrbanDistrict district, BuildingPlan.Variant variant) {
        UrbanRevision.requireSupportedContent(content);
        if (variant == BuildingPlan.Variant.PICKED_CLEAN) { return null; }
        Inventory result = null;
        boolean economy = content == UrbanRevision.ECONOMY_CONTENT;
        Supply[] supplies = (economy ? ECONOMY_TABLES : TABLES)[table.ordinal()];
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
            int chance = economy ? Math.round(supply.chance * (100 + bias) / 100f) : supply.chance + bias;
            if (UrbanHash.range(0,99,seed,content,UrbanSalts.LOOT_PRESENCE,id,table.ordinal(),i)
                    >= Math.min(100, chance)) { continue; }
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
