package com.veylon.item;

import java.util.List;

/** Fifteen repair/reuse recipes; original legacy recipes and requirements remain frozen. */
public final class UrbanCrafting {
    private UrbanCrafting() { }
    public static final List<Recipe> RECIPES = List.of(
            new Recipe("Reinforced Spear", ItemType.IRON_SPEAR, 1, Station.WORKBENCH,
                    ItemType.SCRAP, 3, ItemType.PLANK, 2, ItemType.FIBER, 2),
            new Recipe("Recover Table Wood x4", ItemType.PLANK, 4, Station.HAND, ItemType.TABLE, 1),
            new Recipe("Cut Sheet Metal x3", ItemType.SCRAP, 3, Station.HAND, ItemType.SHEET_METAL, 1),
            new Recipe("Sticks x4", ItemType.STICK, 4, Station.HAND, ItemType.PLANK, 1),
            new Recipe("Textile Bandages x2", ItemType.BANDAGE, 2, Station.HAND, ItemType.FIBER, 4),
            new Recipe("Splint", ItemType.SPLINT, 1, Station.HAND, ItemType.STICK, 2, ItemType.FIBER, 2),
            new Recipe("Salvage Fire Pit", ItemType.CAMPFIRE, 1, Station.HAND, ItemType.SCRAP, 2, ItemType.PLANK, 3),
            new Recipe("Repair Workbench", ItemType.WORKBENCH, 1, Station.HAND, ItemType.PLANK, 4, ItemType.SCRAP, 2),
            new Recipe("Reused Water Container", ItemType.WATERSKIN_EMPTY, 1, Station.HAND, ItemType.SCRAP, 2, ItemType.FIBER, 2),
            new Recipe("Textile Bedroll", ItemType.BEDROLL, 1, Station.HAND, ItemType.FIBER, 8, ItemType.PLANK, 2),
            new Recipe("Rain Collector", ItemType.RAIN_COLLECTOR, 1, Station.WORKBENCH, ItemType.PLANK, 4, ItemType.SCRAP, 2),
            new Recipe("Textile Torch x2", ItemType.TORCH, 2, Station.HAND, ItemType.STICK, 1, ItemType.FIBER, 2),
            new Recipe("Salvage Lantern", ItemType.LANTERN, 1, Station.WORKBENCH, ItemType.SCRAP, 3, ItemType.TORCH, 1),
            new Recipe("Repair Pry Tool", ItemType.IRON_PICKAXE, 1, Station.WORKBENCH, ItemType.IRON_PICKAXE, 1, ItemType.SCRAP, 2),
            new Recipe("Repair Spear", ItemType.IRON_SPEAR, 1, Station.WORKBENCH, ItemType.IRON_SPEAR, 1, ItemType.SCRAP, 2)
    );
}
