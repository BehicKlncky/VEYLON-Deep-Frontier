package com.veylon.qa;

import com.veylon.item.ItemType;
import com.veylon.world.BlockType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Guards enum ordinals that are persisted directly in save/chunk data. */
class SerializedEnumOrderTest {

    private static final List<String> BLOCK_TYPE_ORDER = List.of(
            "AIR", "GRASS", "DIRT", "STONE", "SAND", "GRAVEL", "CLAY", "SNOW", "ICE", "WATER",
            "LOG", "LEAVES", "BUSH", "BERRY_BUSH", "BERRY_BUSH_EMPTY", "COAL_ORE", "COPPER_ORE",
            "IRON_ORE", "PLANK", "WALL", "CAMPFIRE", "TORCH", "WORKBENCH", "CRATE", "ASH",
            "SAPLING", "TALL_GRASS", "HERB_PLANT", "SCRAP_BLOCK", "POD_HULL", "RUIN_STONE",
            "RUIN_CORE", "BONE_PILE", "DRYING_RACK", "FURNACE", "ANVIL", "TANNERY",
            "HERB_STATION", "MAP_TABLE", "RAIN_COLLECTOR", "BEDROLL", "CAMP_BED", "BEACON",
            "BEACON_LIT");

    private static final List<String> ITEM_TYPE_ORDER = List.of(
            "DIRT", "STONE", "SAND", "GRAVEL", "CLAY", "SNOW", "LOG", "PLANK", "WALL", "STICK",
            "FIBER", "HIDE", "LEATHER", "BONE", "SCRAP", "COAL", "CHARCOAL", "COPPER_ORE",
            "IRON_ORE", "COPPER_INGOT", "IRON_INGOT", "SIGNAL_CRYSTAL", "BLUEPRINT_FRAGMENT", "BERRY",
            "DRIED_BERRY", "HERB", "RAW_MEAT", "COOKED_MEAT", "DRIED_MEAT", "SPOILED_MEAT",
            "WATERSKIN_EMPTY", "WATERSKIN_DIRTY", "WATERSKIN_CLEAN", "BANDAGE", "SPLINT",
            "ANTISEPTIC", "HERBAL_POULTICE", "MEDICINE", "TORCH", "CAMPFIRE", "WORKBENCH", "CRATE",
            "DRYING_RACK", "FURNACE", "ANVIL", "TANNERY", "HERB_STATION", "MAP_TABLE",
            "RAIN_COLLECTOR", "BEDROLL", "BEACON_FRAME", "WOOD_PICKAXE", "STONE_PICKAXE",
            "IRON_PICKAXE", "WOOD_AXE", "STONE_AXE", "IRON_AXE", "SPEAR", "IRON_SPEAR", "BONE_KNIFE",
            "IRON_KNIFE", "FIBER_WRAP", "HIDE_COAT", "RAIN_CLOAK", "LEATHER_ARMOR", "IRON_ARMOR",
            "HIDE_PANTS", "HIDE_BOOTS", "FUR_HOOD", "BACKPACK");

    @Test
    void blockTypeNamesAndOrderMatchTheChunkFormatSnapshot() {
        List<String> actual = Arrays.stream(BlockType.values()).map(BlockType::name).toList();
        assertEquals(BLOCK_TYPE_ORDER, actual,
                "BlockType ordinals are chunk/save IDs; append new entries and never reorder existing ones");

        BlockType[] values = BlockType.values();
        for (int i = 0; i < values.length; i++) {
            assertEquals(i, Byte.toUnsignedInt(values[i].id()), "Unexpected byte ID for " + values[i]);
            assertSame(values[i], BlockType.byId((byte) i), "BlockType.byId mismatch at " + i);
        }
    }

    @Test
    void itemTypeNamesAndOrderMatchTheSaveFormatSnapshot() {
        List<String> actual = Arrays.stream(ItemType.values()).map(ItemType::name).toList();
        assertEquals(ITEM_TYPE_ORDER, actual,
                "SaveSystem persists ItemType.ordinal(); append new entries and never reorder existing ones");
    }
}
