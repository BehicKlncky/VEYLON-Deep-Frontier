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
    BEACON_LIT("Distress Beacon (active)", Shape.CUBE, true, true, false, -1f, ToolKind.NONE, false, null, 0, 15, 0f, 0.55f, 0.90f, 1.0f),

    // ---- Deep Frontier world expansion (append-only; ordinals are chunk/save IDs) ----
    BASALT("Basalt", Shape.CUBE, true, true, false, 6.5f, ToolKind.PICKAXE, false, ItemType.STONE, 1, 0, 0f, 0.24f, 0.24f, 0.28f),
    SULFUR_ORE("Sulfur Vein", Shape.CUBE, true, true, false, 6.0f, ToolKind.PICKAXE, true, ItemType.SULFUR, 2, 0, 0f, 0.72f, 0.68f, 0.22f),
    SALTPETER_ORE("Saltpeter Crust", Shape.CUBE, true, true, false, 5.5f, ToolKind.PICKAXE, true, ItemType.SALTPETER, 2, 0, 0f, 0.78f, 0.76f, 0.66f),
    GLOW_FUNGUS("Glow Fungus", Shape.CROSS, false, false, false, 0.2f, ToolKind.NONE, false, ItemType.HERB, 1, 7, 0f, 0.42f, 0.78f, 0.62f),
    LADDER("Rope Ladder", Shape.CROSS, false, false, true, 0.5f, ToolKind.NONE, false, ItemType.ROPE_LADDER, 1, 0, 0f, 0.60f, 0.48f, 0.28f),
    LANTERN("Lantern", Shape.TORCH, false, false, false, 0.6f, ToolKind.NONE, false, ItemType.LANTERN, 1, 15, 2f, 0.88f, 0.74f, 0.38f),
    TRAIL_MARKER("Trail Marker", Shape.TORCH, false, false, false, 0.1f, ToolKind.NONE, false, ItemType.TRAIL_MARKER, 1, 3, 0f, 0.90f, 0.55f, 0.25f),
    POWDER_KEG("Powder Keg", Shape.CUBE, true, true, true, 1.2f, ToolKind.AXE, false, ItemType.POWDER_KEG, 1, 0, 0f, 0.45f, 0.33f, 0.20f),
    GATE("Gate", Shape.CUBE, true, true, true, 2.6f, ToolKind.AXE, false, null, 0, 0, 0f, 0.44f, 0.34f, 0.22f),
    GATE_OPEN("Gate (open)", Shape.RACK, false, false, true, 2.6f, ToolKind.AXE, false, null, 0, 0, 0f, 0.44f, 0.34f, 0.22f),
    STONE_BRICK("Stone Brick", Shape.CUBE, true, true, false, 7.5f, ToolKind.PICKAXE, false, ItemType.STONE, 1, 0, 0f, 0.44f, 0.44f, 0.48f),
    ALARM_BELL("Alarm Bell", Shape.TORCH, false, false, false, 1.4f, ToolKind.NONE, false, ItemType.SCRAP, 1, 0, 0f, 0.75f, 0.60f, 0.25f),
    CAGE_BARS("Cage Bars", Shape.CUBE, true, true, false, 5.0f, ToolKind.PICKAXE, false, ItemType.SCRAP, 1, 0, 0f, 0.36f, 0.38f, 0.42f),

    // ---- Urban materials and fixtures (urban package 04; append-only, budget <= 24) ----
    // Doors and windows are closed/open/broken id pairs (Fixtures); lock and bar
    // state lives in World.fixtures. A broken door or window, the open halves and
    // the fixture items are handled by Fixtures and FixtureActions, so their
    // BlockType drop is null.
    ASPHALT("Asphalt", Shape.CUBE, true, true, false, 4.5f, ToolKind.PICKAXE, false, ItemType.ASPHALT, 1, 0, 0f, 0.20f, 0.20f, 0.22f),
    PAVING("Paving Slabs", Shape.CUBE, true, true, false, 4.0f, ToolKind.PICKAXE, false, ItemType.PAVING, 1, 0, 0f, 0.58f, 0.57f, 0.55f),
    CONCRETE("Concrete", Shape.CUBE, true, true, false, 7.0f, ToolKind.PICKAXE, false, ItemType.CONCRETE, 1, 0, 0f, 0.62f, 0.62f, 0.60f),
    BRICK("Brick", Shape.CUBE, true, true, false, 6.0f, ToolKind.PICKAXE, false, ItemType.BRICK, 1, 0, 0f, 0.55f, 0.30f, 0.24f),
    PLASTER("Plaster Wall", Shape.CUBE, true, true, false, 1.5f, ToolKind.PICKAXE, false, ItemType.PLASTER, 1, 0, 0f, 0.80f, 0.77f, 0.70f),
    SHEET_METAL("Sheet Metal", Shape.CUBE, true, true, false, 8.0f, ToolKind.PICKAXE, true, ItemType.SHEET_METAL, 1, 0, 0f, 0.45f, 0.48f, 0.50f),
    RUBBLE("Rubble", Shape.CUBE, true, true, false, 1.2f, ToolKind.PICKAXE, false, ItemType.GRAVEL, 1, 0, 0f, 0.50f, 0.48f, 0.45f),
    WOOD_DOOR("Wooden Door", Shape.PANE, true, true, true, 3.0f, ToolKind.AXE, false, null, 0, 0, 0f, 0.52f, 0.38f, 0.24f),
    WOOD_DOOR_OPEN("Wooden Door (open)", Shape.LEAF, false, false, true, 3.0f, ToolKind.AXE, false, null, 0, 0, 0f, 0.52f, 0.38f, 0.24f),
    METAL_DOOR("Metal Door", Shape.PANE, true, true, false, 9.0f, ToolKind.PICKAXE, true, null, 0, 0, 0f, 0.38f, 0.42f, 0.44f),
    METAL_DOOR_OPEN("Metal Door (open)", Shape.LEAF, false, false, false, 9.0f, ToolKind.PICKAXE, true, null, 0, 0, 0f, 0.38f, 0.42f, 0.44f),
    BROKEN_DOOR("Broken Doorway", Shape.PANE, false, false, true, 0.6f, ToolKind.AXE, false, null, 0, 0, 0f, 0.36f, 0.28f, 0.20f),
    WINDOW("Window", Shape.CUBE, true, false, false, 0.4f, ToolKind.NONE, false, null, 0, 0, 0f, 0.62f, 0.72f, 0.78f),
    WINDOW_OPEN("Window (open)", Shape.LEAF, false, false, false, 0.4f, ToolKind.NONE, false, null, 0, 0, 0f, 0.62f, 0.72f, 0.78f),
    BROKEN_WINDOW("Shattered Window", Shape.PANE, false, false, false, 0.3f, ToolKind.NONE, false, null, 0, 0, 0f, 0.45f, 0.48f, 0.50f),
    STAIRS("Concrete Stairs", Shape.CUBE, true, true, false, 6.0f, ToolKind.PICKAXE, false, ItemType.STAIRS, 1, 0, 0f, 0.60f, 0.60f, 0.58f),
    SHELF("Shelving Unit", Shape.CUBE, true, true, true, 2.0f, ToolKind.AXE, false, ItemType.SHELF, 1, 0, 0f, 0.55f, 0.44f, 0.30f),
    CABINET("Cabinet", Shape.CUBE, true, true, true, 2.0f, ToolKind.AXE, false, ItemType.CABINET, 1, 0, 0f, 0.62f, 0.52f, 0.38f),
    LOCKER("Metal Locker", Shape.CUBE, true, true, false, 4.0f, ToolKind.PICKAXE, false, ItemType.LOCKER, 1, 0, 0f, 0.40f, 0.48f, 0.46f),
    TABLE("Table", Shape.TABLE, true, false, true, 1.5f, ToolKind.AXE, false, ItemType.TABLE, 1, 0, 0f, 0.58f, 0.44f, 0.28f),
    DEBRIS("Debris", Shape.PANEL, false, false, false, 0.15f, ToolKind.NONE, false, null, 0, 0, 0f, 0.48f, 0.46f, 0.42f),

    // Append-only byte ids: the direction names the ascending (high) end.
    STAIRS_NORTH("Concrete Stairs", Shape.STAIRS, true, false, false, 6.0f, ToolKind.PICKAXE, false, ItemType.STAIRS, 1, 0, 0f, 0.60f, 0.60f, 0.58f),
    STAIRS_SOUTH("Concrete Stairs", Shape.STAIRS, true, false, false, 6.0f, ToolKind.PICKAXE, false, ItemType.STAIRS, 1, 0, 0f, 0.60f, 0.60f, 0.58f),
    STAIRS_WEST("Concrete Stairs", Shape.STAIRS, true, false, false, 6.0f, ToolKind.PICKAXE, false, ItemType.STAIRS, 1, 0, 0f, 0.60f, 0.60f, 0.58f),
    STAIRS_EAST("Concrete Stairs", Shape.STAIRS, true, false, false, 6.0f, ToolKind.PICKAXE, false, ItemType.STAIRS, 1, 0, 0f, 0.60f, 0.60f, 0.58f),

    // Milestone 12: a painted frontage board set into the facade above an entrance. Its words
    // come from the building plan, so breaking it salvages nothing and no item places one.
    SIGN("Painted Sign", Shape.CUBE, true, true, true, 1.5f, ToolKind.AXE, false, null, 0, 0, 0f, 0.66f, 0.58f, 0.40f),

    // Milestone 13: spilled chemicals and quarantine runoff crusted over the ground. Standing on
    // it risks sickness in Survival; scraping it away by hand leaves a shallow pit and nothing else.
    CONTAMINATED_GROUND("Contaminated Ground", Shape.CUBE, true, true, false, 1.2f, ToolKind.NONE, false, null, 0, 0, 0f, 0.36f, 0.42f, 0.20f),

    // Milestone 14: a see-through steel guard rail. On a parapet, a bridge or around a stair
    // opening it makes a second course that nobody steps over, without hiding the view.
    RAILING("Guard Rail", Shape.CUBE, true, false, false, 4.0f, ToolKind.PICKAXE, false, null, 0, 0, 0f, 0.36f, 0.38f, 0.40f),
    // A loose plaster panel over a cellar doorway. Closed it is named and built like the wall
    // around it; working it with F slides it aside like a door (Fixtures.Kind.HIDDEN_PANEL).
    HIDDEN_PANEL("Plaster Wall", Shape.CUBE, true, true, false, 1.5f, ToolKind.PICKAXE, false, null, 0, 0, 0f, 0.78f, 0.75f, 0.68f),
    HIDDEN_PANEL_OPEN("Loose Panel (open)", Shape.LEAF, false, false, false, 1.5f, ToolKind.PICKAXE, false, null, 0, 0, 0f, 0.78f, 0.75f, 0.68f),

    // Milestone 15: the quarantine's alien growth. A node anchors one Veilmaw's territory; its
    // glow is in the texture, not the light simulation, so it never reads as a lamp. A flame
    // beside it sets it alight, breaking it gives one membrane sac, and either way its organism
    // withers. The membrane carpet round it is the territory floor: it burns, and scraping it
    // away by hand leaves nothing.
    GROWTH_NODE("Growth Node", Shape.CUBE, true, true, true, 2.5f, ToolKind.AXE, false, ItemType.MEMBRANE_SAC, 1, 0, 0f, 0.46f, 0.38f, 0.54f),
    VEIL_MEMBRANE("Veil Membrane", Shape.CUBE, true, true, true, 0.9f, ToolKind.NONE, false, null, 0, 0, 0f, 0.29f, 0.26f, 0.33f);

    /**
     * How a block is meshed. Not persisted: only {@link BlockType} ordinals are
     * saved, so shapes may be appended or reordered freely.
     */
    public enum Shape {
        NONE, CUBE, CROSS, LIQUID, TORCH, CAMPFIRE, PANEL, RACK, BASIN,
        /** An open door or casement: a thin leaf along one jamb ({@code FixtureGeometry}). */
        LEAF,
        /** A thin panel in the wall plane: closed/broken doors and shattered windows. */
        PANE,
        /** A table top on four legs over a full-cell collider. */
        TABLE,
        /** Two half-height treads, with matching collision and targeting. */
        STAIRS
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
        return this == AIR || this == WATER || this == TALL_GRASS || this == DEBRIS;
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

    /** Blocks entities can climb while inside them (vertical shafts). */
    public boolean isClimbable() {
        return this == LADDER;
    }
}
