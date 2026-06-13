package com.veylon.world;

import com.veylon.item.ItemType;
import com.veylon.item.ToolKind;

/**
 * Every block in the world. The byte stored in chunks is the enum ordinal.
 */
public enum BlockType {
    //          name              shape         solid  opaque flam  hard  tool             reqTool drop          n  light heat  r      g      b
    AIR("Air", Shape.NONE, false, false, false, 0f, ToolKind.NONE, false, null, 0, 0, 0f, 0f, 0f, 0f),
    GRASS("Grass", Shape.CUBE, true, true, false, 0.7f, ToolKind.NONE, false, ItemType.DIRT, 1, 0, 0f, 0.32f, 0.55f, 0.24f),
    DIRT("Dirt", Shape.CUBE, true, true, false, 0.6f, ToolKind.NONE, false, ItemType.DIRT, 1, 0, 0f, 0.45f, 0.33f, 0.22f),
    STONE("Stone", Shape.CUBE, true, true, false, 5.0f, ToolKind.PICKAXE, false, ItemType.STONE, 1, 0, 0f, 0.52f, 0.52f, 0.55f),
    SAND("Sand", Shape.CUBE, true, true, false, 0.5f, ToolKind.NONE, false, ItemType.SAND, 1, 0, 0f, 0.80f, 0.75f, 0.55f),
    GRAVEL("Gravel", Shape.CUBE, true, true, false, 0.7f, ToolKind.NONE, false, ItemType.GRAVEL, 1, 0, 0f, 0.48f, 0.45f, 0.42f),
    CLAY("Clay", Shape.CUBE, true, true, false, 0.7f, ToolKind.NONE, false, ItemType.CLAY, 1, 0, 0f, 0.58f, 0.55f, 0.50f),
    SNOW("Snow", Shape.CUBE, true, true, false, 0.4f, ToolKind.NONE, false, ItemType.SNOW, 1, 0, 0f, 0.90f, 0.91f, 0.94f),
    ICE("Ice", Shape.CUBE, true, true, false, 0.9f, ToolKind.PICKAXE, false, null, 0, 0, 0f, 0.62f, 0.76f, 0.90f),
    WATER("Water", Shape.LIQUID, false, false, false, -1f, ToolKind.NONE, false, null, 0, 0, 0f, 0.18f, 0.34f, 0.62f),
    LOG("Wood Log", Shape.CUBE, true, true, true, 2.8f, ToolKind.AXE, false, ItemType.LOG, 1, 0, 0f, 0.42f, 0.30f, 0.18f),
    LEAVES("Leaves", Shape.CUBE, true, true, true, 0.25f, ToolKind.NONE, false, ItemType.STICK, 1, 0, 0f, 0.20f, 0.42f, 0.16f),
    BUSH("Bush", Shape.CROSS, false, false, true, 0.3f, ToolKind.NONE, false, ItemType.FIBER, 2, 0, 0f, 0.18f, 0.40f, 0.16f),
    BERRY_BUSH("Berry Bush", Shape.CROSS, false, false, true, 0.4f, ToolKind.NONE, false, ItemType.BERRY, 2, 0, 0f, 0.55f, 0.20f, 0.30f),
    BERRY_BUSH_EMPTY("Berry Bush (bare)", Shape.CROSS, false, false, true, 0.4f, ToolKind.NONE, false, ItemType.FIBER, 1, 0, 0f, 0.22f, 0.38f, 0.20f),
    COAL_ORE("Coal Ore", Shape.CUBE, true, true, false, 6.0f, ToolKind.PICKAXE, true, ItemType.COAL, 1, 0, 0f, 0.32f, 0.32f, 0.34f),
    COPPER_ORE("Copper Ore", Shape.CUBE, true, true, false, 6.5f, ToolKind.PICKAXE, true, ItemType.COPPER_ORE, 1, 0, 0f, 0.55f, 0.36f, 0.25f),
    IRON_ORE("Iron Ore", Shape.CUBE, true, true, false, 7.0f, ToolKind.PICKAXE, true, ItemType.IRON_ORE, 1, 0, 0f, 0.60f, 0.52f, 0.46f),
    PLANK("Plank Block", Shape.CUBE, true, true, true, 1.8f, ToolKind.AXE, false, ItemType.PLANK, 1, 0, 0f, 0.62f, 0.46f, 0.28f),
    WALL("Wall Block", Shape.CUBE, true, true, true, 3.0f, ToolKind.AXE, false, ItemType.WALL, 1, 0, 0f, 0.50f, 0.38f, 0.26f),
    CAMPFIRE("Campfire", Shape.CAMPFIRE, false, false, false, 0.8f, ToolKind.NONE, false, ItemType.CAMPFIRE, 1, 14, 40, 0.55f, 0.30f, 0.15f),
    TORCH("Torch", Shape.TORCH, false, false, false, 0.1f, ToolKind.NONE, false, ItemType.TORCH, 1, 12, 4, 0.85f, 0.65f, 0.30f),
    WORKBENCH("Workbench", Shape.CUBE, true, true, true, 1.8f, ToolKind.AXE, false, ItemType.WORKBENCH, 1, 0, 0f, 0.70f, 0.52f, 0.25f),
    CRATE("Storage Crate", Shape.CUBE, true, true, true, 1.6f, ToolKind.AXE, false, ItemType.CRATE, 1, 0, 0f, 0.55f, 0.42f, 0.22f),
    ASH("Charred Block", Shape.CUBE, true, true, false, 0.3f, ToolKind.NONE, false, null, 0, 0, 0f, 0.22f, 0.21f, 0.20f),
    SAPLING("Sapling", Shape.CROSS, false, false, true, 0.1f, ToolKind.NONE, false, ItemType.STICK, 1, 0, 0f, 0.25f, 0.50f, 0.22f),
    TALL_GRASS("Tall Grass", Shape.CROSS, false, false, true, 0.1f, ToolKind.NONE, false, ItemType.FIBER, 1, 0, 0f, 0.34f, 0.52f, 0.24f),

    // ---- Plants & POI blocks ----
    HERB_PLANT("Wild Herbs", Shape.CROSS, false, false, true, 0.15f, ToolKind.NONE, false, ItemType.HERB, 2, 0, 0f, 0.35f, 0.70f, 0.40f),
    SCRAP_BLOCK("Wreckage", Shape.CUBE, true, true, false, 1.4f, ToolKind.NONE, false, ItemType.SCRAP, 2, 0, 0f, 0.38f, 0.40f, 0.44f),
    POD_HULL("Pod Hull", Shape.CUBE, true, true, false, 3.0f, ToolKind.PICKAXE, false, ItemType.SCRAP, 1, 0, 0f, 0.78f, 0.80f, 0.84f),
    RUIN_STONE("Ruin Stone", Shape.CUBE, true, true, false, 8.0f, ToolKind.PICKAXE, false, ItemType.STONE, 1, 0, 0f, 0.30f, 0.32f, 0.40f),
    RUIN_CORE("Resonant Core", Shape.CUBE, true, true, false, 9.0f, ToolKind.PICKAXE, true, ItemType.SIGNAL_CRYSTAL, 1, 10, 0f, 0.35f, 0.75f, 0.85f),
    BONE_PILE("Bone Pile", Shape.PANEL, false, false, false, 0.5f, ToolKind.NONE, false, ItemType.BONE, 2, 0, 0f, 0.82f, 0.79f, 0.70f),

    // ---- Stations & utility ----
    DRYING_RACK("Drying Rack", Shape.RACK, false, false, true, 1.2f, ToolKind.AXE, false, ItemType.DRYING_RACK, 1, 0, 0f, 0.62f, 0.48f, 0.30f),
    FURNACE("Stone Furnace", Shape.CUBE, true, true, false, 3.5f, ToolKind.PICKAXE, false, ItemType.FURNACE, 1, 0, 0f, 0.42f, 0.40f, 0.42f),
    ANVIL("Anvil", Shape.PANEL, false, false, false, 4.0f, ToolKind.PICKAXE, false, ItemType.ANVIL, 1, 0, 0f, 0.30f, 0.31f, 0.35f),
    TANNERY("Tannery", Shape.RACK, false, false, true, 1.4f, ToolKind.AXE, false, ItemType.TANNERY, 1, 0, 0f, 0.58f, 0.42f, 0.26f),
    HERB_STATION("Herbalist Bench", Shape.CUBE, true, true, true, 1.6f, ToolKind.AXE, false, ItemType.HERB_STATION, 1, 0, 0f, 0.38f, 0.58f, 0.34f),
    MAP_TABLE("Map Table", Shape.CUBE, true, true, true, 1.6f, ToolKind.AXE, false, ItemType.MAP_TABLE, 1, 0, 0f, 0.34f, 0.45f, 0.62f),
    RAIN_COLLECTOR("Rain Collector", Shape.BASIN, false, false, false, 1.2f, ToolKind.NONE, false, ItemType.RAIN_COLLECTOR, 1, 0, 0f, 0.50f, 0.55f, 0.60f),
    BEDROLL("Bedroll", Shape.PANEL, false, false, true, 0.4f, ToolKind.NONE, false, ItemType.BEDROLL, 1, 0, 0f, 0.60f, 0.50f, 0.34f),
    CAMP_BED("Camp Bed", Shape.PANEL, false, false, true, 0.8f, ToolKind.AXE, false, ItemType.BEDROLL, 1, 0, 0f, 0.55f, 0.42f, 0.40f),
    BEACON("Distress Beacon", Shape.CUBE, true, true, false, 6.0f, ToolKind.PICKAXE, false, ItemType.BEACON_FRAME, 1, 4, 0f, 0.55f, 0.70f, 0.80f),
    BEACON_LIT("Distress Beacon (active)", Shape.CUBE, true, true, false, -1f, ToolKind.NONE, false, null, 0, 15, 0f, 0.55f, 0.90f, 1.0f);

    public enum Shape {
        NONE, CUBE, CROSS, LIQUID, TORCH, CAMPFIRE, PANEL, RACK, BASIN
    }

    public final String displayName;
    public final Shape shape;
    public final boolean solid;
    public final boolean opaque;
    public final boolean flammable;
    /** Seconds to break by hand; negative = unbreakable. */
    public final float hardness;
    /** Tool that speeds up mining this block. */
    public final ToolKind preferredTool;
    /** If true the block drops nothing without the preferred tool. */
    public final boolean requiresTool;
    public final ItemType drop;
    public final int dropCount;
    /** Light emission 0-15. */
    public final int light;
    /** Heat contribution in degrees C at zero distance. */
    public final float heat;
    public final float r, g, b;

    private static final BlockType[] BY_ID = values();

    BlockType(String displayName, Shape shape, boolean solid, boolean opaque, boolean flammable,
              float hardness, ToolKind preferredTool, boolean requiresTool, ItemType drop, int dropCount,
              int light, float heat, float r, float g, float b) {
        this.displayName = displayName;
        this.shape = shape;
        this.solid = solid;
        this.opaque = opaque;
        this.flammable = flammable;
        this.hardness = hardness;
        this.preferredTool = preferredTool;
        this.requiresTool = requiresTool;
        this.drop = drop;
        this.dropCount = dropCount;
        this.light = light;
        this.heat = heat;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public static BlockType byId(byte id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : AIR;
    }

    public byte id() {
        return (byte) ordinal();
    }

    public boolean isAir() {
        return this == AIR;
    }

    /** Blocks the player can place another block into. */
    public boolean isReplaceable() {
        return this == AIR || this == WATER || this == TALL_GRASS;
    }

    /** Blocks that can be targeted by the mining raycast. */
    public boolean isTargetable() {
        return this != AIR && this != WATER;
    }

    /** True for blocks that act as a crafting station. */
    public boolean isStation() {
        return this == WORKBENCH || this == CAMPFIRE || this == FURNACE || this == ANVIL
                || this == TANNERY || this == HERB_STATION || this == MAP_TABLE;
    }
}
