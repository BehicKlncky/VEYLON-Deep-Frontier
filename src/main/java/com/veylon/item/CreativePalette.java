package com.veylon.item;

import com.veylon.world.BlockType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * R24: the Creative building palette, and the record of what it leaves out.
 *
 * <p>Twenty-six blocks already had an item form because Survival produces them.
 * The rest were unbuildable: the terrain and the settlement builder could place
 * grass, leaves, ore, basalt, stone brick or wreckage, and the player could only
 * break them. This adds an appended, Creative-only item form for the inert ones.
 *
 * <p>"Inert" is the whole criterion. A block qualifies when placing it only
 * changes what the world is made of. Blocks that carry gameplay state are
 * excluded, because {@code placeSelectedBlockAt} writes the block and then runs
 * a small per-block initialization; a block whose meaning lives in a side table
 * ({@code World.gateTimers}), in a settlement field ({@code Settlement.alarmBell}),
 * in progression ({@code World.beaconStage}) or in a harvest and regrowth cycle
 * would be placed without it and would read as a broken copy of the real thing.
 * {@link #EXCLUDED} records the reason for each one, and
 * {@code CreativePaletteTest} fails if a block is neither included nor excluded,
 * so a new block cannot quietly fall through the audit.
 *
 * <p>Palette items are Creative-only by construction rather than by a gate: the
 * Creative catalog and pick block are the only things that create them, and both
 * already require Creative abilities. No recipe, drop table, loot table or trader
 * references one, so Survival cannot obtain them and Survival drop tables are
 * unchanged - breaking grass still yields dirt.
 *
 * @see BlockItemForms
 */
public final class CreativePalette {

    /** The appended item forms, in {@link BlockType} declaration order. */
    private static final Set<ItemType> ITEMS = Collections.unmodifiableSet(EnumSet.of(
            ItemType.GRASS_BLOCK,
            ItemType.ICE_BLOCK,
            ItemType.LEAVES_BLOCK,
            ItemType.BUSH_BLOCK,
            ItemType.TALL_GRASS_BLOCK,
            ItemType.COAL_ORE_BLOCK,
            ItemType.COPPER_ORE_BLOCK,
            ItemType.IRON_ORE_BLOCK,
            ItemType.ASH_BLOCK,
            ItemType.WRECKAGE_BLOCK,
            ItemType.POD_HULL_BLOCK,
            ItemType.RUIN_STONE_BLOCK,
            ItemType.BONE_PILE_BLOCK,
            ItemType.BASALT_BLOCK,
            ItemType.SULFUR_ORE_BLOCK,
            ItemType.SALTPETER_ORE_BLOCK,
            ItemType.GLOW_FUNGUS_BLOCK,
            ItemType.STONE_BRICK_BLOCK,
            ItemType.CAGE_BARS_BLOCK));

    /** Every block deliberately left without an item form, with its reason. */
    private static final Map<BlockType, String> EXCLUDED;

    static {
        EnumMap<BlockType, String> excluded = new EnumMap<>(BlockType.class);
        excluded.put(BlockType.AIR,
                "absence of a block; removing one is what breaking already does");
        excluded.put(BlockType.WATER,
                "liquid: immersion, swimming and the shoreline mesh assume generated water,"
                        + " and a placed source has no flow simulation to spread or drain it");
        excluded.put(BlockType.BERRY_BUSH,
                "harvest state: harvesting swaps it for BERRY_BUSH_EMPTY and PlantSystem"
                        + " ripens it again, so a placed one is an unlimited berry source");
        excluded.put(BlockType.BERRY_BUSH_EMPTY,
                "the spent half of that pair; it only exists as the result of a harvest");
        excluded.put(BlockType.HERB_PLANT,
                "harvestable resource grown by PlantSystem in damp biomes;"
                        + " placing it would be a herb generator");
        excluded.put(BlockType.SAPLING,
                "PlantSystem grows it into a whole runtime tree, so it is a world edit"
                        + " with a delay rather than a building block");
        excluded.put(BlockType.CAMP_BED,
                "sleep anchor: WorldInteractions.sleepInBed and the camp radius treat it"
                        + " as the bed of a settlement or the player camp");
        excluded.put(BlockType.GATE,
                "settlement device opened by SettlementManager, which writes GATE_OPEN"
                        + " and a World.gateTimers entry a placed gate would not have");
        excluded.put(BlockType.GATE_OPEN,
                "the transient half of that pair; it closes itself from a gate timer");
        excluded.put(BlockType.ALARM_BELL,
                "settlement alarm addressed by Settlement.alarmBell; a placed bell"
                        + " belongs to no settlement and rings for nobody");
        excluded.put(BlockType.RUIN_CORE,
                "relic progression: it is the tool-gated source of signal crystals,"
                        + " so an unlimited supply would survive a return to Survival");
        excluded.put(BlockType.BEACON_LIT,
                "endgame state written by the beacon installation; it is unbreakable"
                        + " and placing one would fake a transmitting beacon");
        EXCLUDED = Collections.unmodifiableMap(excluded);
    }

    private CreativePalette() {
    }

    /** True for the appended Creative-only item forms. */
    public static boolean isPaletteItem(ItemType item) {
        return ITEMS.contains(item);
    }

    /** The appended item forms, in declaration order. */
    public static Set<ItemType> items() {
        return ITEMS;
    }

    /** Why a block has no item form, or null when it has one. */
    public static String exclusionReason(BlockType block) {
        return EXCLUDED.get(block);
    }

    /** Every block the audit deliberately left unbuildable. */
    public static Set<BlockType> excluded() {
        return EXCLUDED.keySet();
    }
}
