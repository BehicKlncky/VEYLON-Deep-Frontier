package com.veylon.item;

import com.veylon.world.BlockType;

import static com.veylon.item.ItemProps.p;

/**
 * Every item in the game. Block-placing items reference the BlockType they place
 * by name (resolved lazily to avoid enum initialization cycles with BlockType drops).
 */
public enum ItemType {
    // ---- Raw blocks ----
    DIRT("Dirt", p().places("DIRT").weight(1.2f).color(0.45f, 0.33f, 0.22f)),
    STONE("Stone", p().places("STONE").weight(1.6f).damage(2).color(0.52f, 0.52f, 0.55f)),
    SAND("Sand", p().places("SAND").weight(1.4f).color(0.80f, 0.75f, 0.55f)),
    GRAVEL("Gravel", p().places("GRAVEL").weight(1.5f).color(0.48f, 0.45f, 0.42f)),
    CLAY("Clay", p().places("CLAY").weight(1.3f).color(0.58f, 0.55f, 0.50f)),
    SNOW("Snow", p().places("SNOW").weight(0.8f).color(0.92f, 0.93f, 0.95f)),
    LOG("Log", p().places("LOG").weight(2.2f).damage(2.5f).color(0.42f, 0.30f, 0.18f)),
    PLANK("Plank", p().places("PLANK").weight(0.9f).damage(1.5f).color(0.62f, 0.46f, 0.28f)),
    WALL("Wall Block", p().places("WALL").weight(1.6f).damage(1.5f).color(0.50f, 0.38f, 0.26f)),

    // ---- Gathered materials ----
    STICK("Stick", p().weight(0.2f).color(0.55f, 0.42f, 0.25f)),
    FIBER("Fiber", p().weight(0.05f).color(0.55f, 0.62f, 0.30f)),
    HIDE("Animal Hide", p().weight(1.2f).color(0.55f, 0.40f, 0.26f)),
    LEATHER("Leather", p().weight(0.8f).color(0.48f, 0.32f, 0.18f)),
    BONE("Bone", p().weight(0.5f).color(0.88f, 0.85f, 0.76f)),
    SCRAP("Scrap Metal", p().weight(1.0f).color(0.45f, 0.48f, 0.52f)),
    COAL("Coal", p().weight(1.0f).color(0.18f, 0.18f, 0.20f)),
    CHARCOAL("Charcoal", p().weight(0.8f).color(0.25f, 0.22f, 0.20f)),
    COPPER_ORE("Copper Ore", p().weight(2.2f).color(0.72f, 0.45f, 0.28f)),
    IRON_ORE("Iron Ore", p().weight(2.4f).color(0.72f, 0.62f, 0.55f)),
    COPPER_INGOT("Copper Ingot", p().weight(1.6f).color(0.85f, 0.52f, 0.30f)),
    IRON_INGOT("Iron Ingot", p().weight(1.8f).color(0.80f, 0.78f, 0.80f)),
    SIGNAL_CRYSTAL("Signal Crystal", p().stack(4).weight(0.8f).color(0.45f, 0.85f, 0.95f)),
    BLUEPRINT_FRAGMENT("Blueprint Fragment", p().stack(16).weight(0.1f).color(0.30f, 0.55f, 0.85f)),

    // ---- Food ----
    BERRY("Berry", p().food(10, FoodGroup.PLANT).hydration(4).weight(0.05f).spoils(1300)
            .color(0.70f, 0.15f, 0.30f)),
    DRIED_BERRY("Dried Berries", p().food(14, FoodGroup.PLANT).weight(0.04f).spoils(6500)
            .color(0.50f, 0.18f, 0.25f)),
    HERB("Medicinal Herb", p().food(3, FoodGroup.PLANT).weight(0.05f).spoils(2700)
            .color(0.35f, 0.70f, 0.40f)),
    RAW_MEAT("Raw Meat", p().food(12, FoodGroup.MEAT).weight(0.5f).spoils(650)
            .color(0.75f, 0.30f, 0.30f)),
    COOKED_MEAT("Cooked Meat", p().food(30, FoodGroup.MEAT).weight(0.4f).spoils(1900)
            .color(0.60f, 0.35f, 0.18f)),
    DRIED_MEAT("Dried Meat", p().food(22, FoodGroup.MEAT).weight(0.25f).spoils(7000)
            .color(0.48f, 0.26f, 0.16f)),
    SPOILED_MEAT("Spoiled Meat", p().food(8, FoodGroup.MEAT).weight(0.4f)
            .color(0.45f, 0.45f, 0.25f)),

    // ---- Water containers ----
    WATERSKIN_EMPTY("Waterskin (empty)", p().stack(4).weight(0.3f).color(0.50f, 0.36f, 0.24f)),
    WATERSKIN_DIRTY("Waterskin (dirty water)", p().stack(4).weight(1.3f).color(0.42f, 0.40f, 0.28f)),
    WATERSKIN_CLEAN("Waterskin (clean water)", p().stack(4).weight(1.3f).color(0.35f, 0.52f, 0.70f)),

    // ---- Medical ----
    BANDAGE("Bandage", p().stack(16).weight(0.1f).color(0.90f, 0.88f, 0.82f)),
    SPLINT("Splint", p().stack(8).weight(0.4f).color(0.68f, 0.55f, 0.35f)),
    ANTISEPTIC("Antiseptic", p().stack(8).weight(0.2f).color(0.55f, 0.80f, 0.60f)),
    HERBAL_POULTICE("Herbal Poultice", p().stack(8).weight(0.2f).color(0.40f, 0.62f, 0.35f)),
    MEDICINE("Medicine", p().stack(8).weight(0.2f).color(0.80f, 0.45f, 0.60f)),

    // ---- Placeable stations & utility ----
    TORCH("Torch", p().places("TORCH").weight(0.4f).color(0.95f, 0.75f, 0.30f)),
    CAMPFIRE("Campfire", p().stack(8).places("CAMPFIRE").weight(3f).color(0.90f, 0.50f, 0.20f)),
    WORKBENCH("Workbench", p().stack(8).places("WORKBENCH").weight(4f).color(0.70f, 0.52f, 0.25f)),
    CRATE("Storage Crate", p().stack(8).places("CRATE").weight(4f).color(0.55f, 0.42f, 0.22f)),
    DRYING_RACK("Drying Rack", p().stack(8).places("DRYING_RACK").weight(3f).color(0.62f, 0.48f, 0.30f)),
    FURNACE("Stone Furnace", p().stack(8).places("FURNACE").weight(8f).color(0.42f, 0.40f, 0.42f)),
    ANVIL("Anvil", p().stack(4).places("ANVIL").weight(12f).color(0.30f, 0.31f, 0.35f)),
    TANNERY("Tannery", p().stack(8).places("TANNERY").weight(4f).color(0.58f, 0.42f, 0.26f)),
    HERB_STATION("Herbalist Bench", p().stack(8).places("HERB_STATION").weight(3f).color(0.38f, 0.58f, 0.34f)),
    MAP_TABLE("Map Table", p().stack(8).places("MAP_TABLE").weight(4f).color(0.34f, 0.45f, 0.62f)),
    RAIN_COLLECTOR("Rain Collector", p().stack(8).places("RAIN_COLLECTOR").weight(4f).color(0.50f, 0.55f, 0.60f)),
    BEDROLL("Bedroll", p().stack(4).places("BEDROLL").weight(2f).color(0.60f, 0.50f, 0.34f)),
    BEACON_FRAME("Beacon Frame", p().stack(1).places("BEACON").weight(10f).color(0.55f, 0.70f, 0.80f)),

    // ---- Tools & weapons ----
    WOOD_PICKAXE("Wooden Pickaxe", p().tool(ToolKind.PICKAXE, 2.5f).damage(3).durability(60)
            .weight(1.5f).color(0.65f, 0.50f, 0.30f)),
    STONE_PICKAXE("Stone Pickaxe", p().tool(ToolKind.PICKAXE, 4f).damage(4).durability(130)
            .weight(2.2f).color(0.58f, 0.58f, 0.62f)),
    IRON_PICKAXE("Iron Pickaxe", p().tool(ToolKind.PICKAXE, 6f).damage(5).durability(300)
            .weight(2.8f).color(0.78f, 0.80f, 0.85f)),
    WOOD_AXE("Wooden Axe", p().tool(ToolKind.AXE, 2.5f).damage(4).durability(60)
            .weight(1.4f).color(0.60f, 0.45f, 0.25f)),
    STONE_AXE("Stone Axe", p().tool(ToolKind.AXE, 4f).damage(5).durability(130)
            .weight(2f).color(0.52f, 0.55f, 0.55f)),
    IRON_AXE("Iron Axe", p().tool(ToolKind.AXE, 6f).damage(6).durability(300)
            .weight(2.6f).color(0.75f, 0.78f, 0.82f)),
    SPEAR("Spear", p().tool(ToolKind.WEAPON, 1f).damage(8).durability(90)
            .weight(1.2f).color(0.75f, 0.65f, 0.45f)),
    IRON_SPEAR("Iron Spear", p().tool(ToolKind.WEAPON, 1f).damage(13).durability(240)
            .weight(2f).color(0.70f, 0.74f, 0.80f)),
    BONE_KNIFE("Bone Knife", p().tool(ToolKind.KNIFE, 1f).damage(4).durability(80)
            .weight(0.4f).color(0.85f, 0.82f, 0.72f)),
    IRON_KNIFE("Iron Knife", p().tool(ToolKind.KNIFE, 1f).damage(6).durability(220)
            .weight(0.8f).color(0.72f, 0.75f, 0.80f)),

    // ---- Clothing & gear ----
    FIBER_WRAP("Fiber Wrap", p().gear(EquipSlot.TORSO, 2, 0.10f, 1).durability(80)
            .weight(0.8f).color(0.58f, 0.60f, 0.34f)),
    HIDE_COAT("Hide Coat", p().gear(EquipSlot.TORSO, 6, 0.35f, 2).durability(160)
            .weight(2.5f).color(0.52f, 0.38f, 0.24f)),
    RAIN_CLOAK("Rain Cloak", p().gear(EquipSlot.TORSO, 3, 0.85f, 1).durability(120)
            .weight(1.2f).color(0.35f, 0.45f, 0.40f)),
    LEATHER_ARMOR("Leather Armor", p().gear(EquipSlot.TORSO, 3, 0.30f, 5).durability(220)
            .weight(3.5f).color(0.42f, 0.28f, 0.16f)),
    IRON_ARMOR("Iron Armor", p().gear(EquipSlot.TORSO, 1, 0.20f, 8).durability(350)
            .weight(8f).color(0.68f, 0.72f, 0.78f)),
    HIDE_PANTS("Hide Pants", p().gear(EquipSlot.LEGS, 3, 0.25f, 1.5f).durability(140)
            .weight(1.8f).color(0.50f, 0.36f, 0.22f)),
    HIDE_BOOTS("Hide Boots", p().gear(EquipSlot.FEET, 2, 0.50f, 1).durability(140)
            .weight(1.2f).color(0.44f, 0.32f, 0.20f)),
    FUR_HOOD("Fur Hood", p().gear(EquipSlot.HEAD, 3, 0.25f, 1).durability(120)
            .weight(0.8f).color(0.56f, 0.44f, 0.30f)),
    BACKPACK("Backpack", p().gear(EquipSlot.BACK, 0, 0, 0).carry(14)
            .weight(1.2f).color(0.46f, 0.38f, 0.26f));

    public final String displayName;
    public final int maxStack;
    private final String placesName;
    public final ToolKind tool;
    /** Mining speed multiplier when the tool matches the block's preferred tool. */
    public final float toolPower;
    public final float damage;
    /** Hunger restored when eaten; 0 = not edible. */
    public final int food;
    /** Thirst restored when eaten/drunk. */
    public final int hydration;
    public final FoodGroup group;
    /** Weight in kg per single item. */
    public final float weight;
    /** Real seconds of freshness; -1 = never spoils. */
    public final float spoilSeconds;
    /** Uses before breaking; -1 = indestructible. */
    public final float maxDurability;
    public final EquipSlot equipSlot;
    public final float insulation;
    public final float wetResist;
    public final float armor;
    public final float carryBonus;
    public final float r, g, b;

    ItemType(String displayName, ItemProps p) {
        this.displayName = displayName;
        this.maxStack = p.maxStack;
        this.placesName = p.placesName;
        this.tool = p.tool;
        this.toolPower = p.toolPower;
        this.damage = p.damage;
        this.food = p.food;
        this.hydration = p.hydration;
        this.group = p.group;
        this.weight = p.weight;
        this.spoilSeconds = p.spoilSeconds;
        this.maxDurability = p.maxDurability;
        this.equipSlot = p.equipSlot;
        this.insulation = p.insulation;
        this.wetResist = p.wetResist;
        this.armor = p.armor;
        this.carryBonus = p.carryBonus;
        this.r = p.r;
        this.g = p.g;
        this.b = p.b;
    }

    public BlockType places() {
        return placesName == null ? null : BlockType.valueOf(placesName);
    }

    public boolean isEdible() {
        return food > 0;
    }

    public boolean spoils() {
        return spoilSeconds > 0;
    }

    public boolean hasDurability() {
        return maxDurability > 0;
    }

    public boolean isEquippable() {
        return equipSlot != null;
    }

    /** Treatment items applied with right-click. */
    public boolean isMedical() {
        return this == BANDAGE || this == SPLINT || this == ANTISEPTIC
                || this == HERBAL_POULTICE || this == MEDICINE;
    }
}
