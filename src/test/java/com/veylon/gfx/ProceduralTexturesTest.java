package com.veylon.gfx;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProceduralTexturesTest {

    /** Snapshot of every tile ID consumed by the current material/prop pipeline. */
    private static final Set<String> KNOWN_TILE_IDS = Set.of(
            "grass_top", "grass_side", "dirt", "stone", "ruin_stone", "sand", "gravel", "clay",
            "snow", "ice", "log_side", "log_end", "plank", "wall", "leaves", "tall_grass", "bush",
            "berry_bush", "berry_bush_empty", "sapling", "herb", "coal_ore", "copper_ore", "iron_ore",
            "ash", "scrap", "pod_hull", "ruin_core", "bone", "furnace_side", "furnace_front",
            "anvil_metal", "torch_head", "campfire_wood", "crate_side", "crate_top", "workbench_top",
            "workbench_side", "beacon_side", "beacon_lit", "fabric", "fabric_red", "herb_station",
            "map_table", "water_still");

    @Test
    void everyKnownTileIdGeneratesACompleteDeterministicTile() {
        for (String id : KNOWN_TILE_IDS) {
            int[] first = ProceduralTextures.generate(id, 0, 64);
            int[] second = ProceduralTextures.generate(id, 0, 64);
            assertNotNull(first, "Missing procedural tile: " + id);
            assertEquals(64 * 64, first.length, "Wrong pixel count for " + id);
            assertArrayEquals(first, second, "Procedural output changed between identical calls for " + id);
        }
    }

    @Test
    void variantIsPartOfTheDeterministicSeed() {
        int[] base = ProceduralTextures.generate("grass_top", 0, 64);
        int[] variant = ProceduralTextures.generate("grass_top", 1, 64);
        assertNotNull(base);
        assertNotNull(variant);
        assertFalse(Arrays.equals(base, variant), "Different variants should not collapse to the same tile");
    }

    @Test
    void unknownTileIdUsesTheCallersMissingTexturePath() {
        assertNull(ProceduralTextures.generate("not_a_real_veylon_tile", 0, 64));
    }
}
