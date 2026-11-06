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

    /**
     * Generator 14 (urban milestone 15): the fifteen above and a salve worked by hand from a
     * Veilmaw's membrane sac and fibre, the city's only source of the burn poultice. Earlier urban
     * worlds keep their fifteen.
     */
    public static final List<Recipe> ECOLOGY_RECIPES = java.util.stream.Stream.concat(RECIPES.stream(),
            java.util.stream.Stream.of(new Recipe("Membrane Salve x2", ItemType.HERBAL_POULTICE, 2, Station.HAND,
                    ItemType.MEMBRANE_SAC, 1, ItemType.FIBER, 1))).toList();

    /**
     * Generator 17 (urban milestone 18): the ecology recipes, less the planks broken out of a carried
     * table, plus the fortifications. Furniture now comes apart only where it stands, by the
     * dismantling of {@code SalvageTable}, which counts time, noise, tools and wear; a second,
     * instant path from the same table would pay out differently. Barricades and braces are made
     * from what salvage recovers: boards nailed with scrap by hand, sheet metal bolted at a
     * workbench. Earlier urban worlds keep their lists.
     */
    public static final List<Recipe> REFUGE_RECIPES = java.util.stream.Stream.concat(ECOLOGY_RECIPES.stream()
                    .filter(r -> !r.ingredients.containsKey(ItemType.TABLE)),
            java.util.stream.Stream.of(
                    new Recipe("Plank Barricade", ItemType.BARRICADE, 1, Station.HAND,
                            ItemType.PLANK, 3, ItemType.SCRAP, 1),
                    new Recipe("Sheet-Metal Barricade", ItemType.BARRICADE_METAL, 1, Station.WORKBENCH,
                            ItemType.SHEET_METAL, 2, ItemType.SCRAP, 2),
                    new Recipe("Door Brace", ItemType.DOOR_BRACE, 1, Station.HAND,
                            ItemType.PLANK, 2, ItemType.SCRAP, 2))).toList();

    /**
     * Generator 18 (urban milestone 19): the refuge recipes, plus the refuge utilities built at a
     * workbench from what salvage recovers. A generator is rebuilt from engine parts in a sheet-metal
     * frame, a work lamp is wired from a part and scrap, and a water purifier is a pump and filter in
     * a sheet-metal tank; its filter is packed with fibre where it stands. Fuel is never made: it comes out of the
     * tanks of the cars that did not burn. Earlier urban worlds keep their lists.
     */
    public static final List<Recipe> UTILITY_RECIPES = java.util.stream.Stream.concat(REFUGE_RECIPES.stream(),
            java.util.stream.Stream.of(
                    new Recipe("Rebuild Generator", ItemType.GENERATOR, 1, Station.WORKBENCH,
                            ItemType.SPARE_PARTS, 3, ItemType.SCRAP, 3, ItemType.SHEET_METAL, 1),
                    new Recipe("Wire Work Lamp", ItemType.WORK_LAMP, 1, Station.WORKBENCH,
                            ItemType.SPARE_PARTS, 1, ItemType.SCRAP, 1),
                    new Recipe("Build Water Purifier", ItemType.WATER_PURIFIER, 1, Station.WORKBENCH,
                            ItemType.SPARE_PARTS, 1, ItemType.SHEET_METAL, 1, ItemType.SCRAP, 2))).toList();

    /**
     * Generator 19 (urban milestone 20): the utility recipes, plus a radio workstation built at a
     * workbench round a transmitter module, the module's only use. The relay's own parts are found,
     * never made. Earlier urban worlds keep their lists.
     */
    public static final List<Recipe> MISSION_RECIPES = java.util.stream.Stream.concat(UTILITY_RECIPES.stream(),
            java.util.stream.Stream.of(
                    new Recipe("Build Radio Workstation", ItemType.RADIO, 1, Station.WORKBENCH,
                            ItemType.TRANSMITTER_MODULE, 1, ItemType.SPARE_PARTS, 1, ItemType.SCRAP, 2))).toList();
}
