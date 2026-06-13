package com.veylon.item;

/** Crafting stations. A recipe is craftable while standing near its station block. */
public enum Station {
    HAND("Hands", null),
    WORKBENCH("Workbench", "WORKBENCH"),
    CAMPFIRE("Campfire", "CAMPFIRE"),
    FURNACE("Furnace", "FURNACE"),
    ANVIL("Anvil", "ANVIL"),
    TANNERY("Tannery", "TANNERY"),
    HERB_STATION("Herbalist Bench", "HERB_STATION"),
    MAP_TABLE("Map Table", "MAP_TABLE");

    public final String displayName;
    /** BlockType name that provides this station (resolved lazily; null = no block needed). */
    public final String blockName;

    Station(String displayName, String blockName) {
        this.displayName = displayName;
        this.blockName = blockName;
    }
}
