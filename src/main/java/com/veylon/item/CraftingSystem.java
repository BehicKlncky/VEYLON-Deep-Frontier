package com.veylon.item;

import java.util.List;
import java.util.Set;

public final class CraftingSystem {

    /** Blueprint ids in the fixed order they decode at the map table. */
    public static final List<String> BLUEPRINT_ORDER = List.of("rain_cloak", "iron_armor", "beacon");

    public static String blueprintTitle(String id) {
        return switch (id) {
            case "rain_cloak" -> "Rain Cloak";
            case "iron_armor" -> "Iron Armor";
            case "beacon" -> "Beacon Frame";
            default -> id;
        };
    }

    public static final List<Recipe> RECIPES = List.of(
            // ---- Hand recipes ----
            new Recipe("Planks x4", ItemType.PLANK, 4, Station.HAND, ItemType.LOG, 1),
            new Recipe("Sticks x4", ItemType.STICK, 4, Station.HAND, ItemType.PLANK, 1),
            new Recipe("Spear", ItemType.SPEAR, 1, Station.HAND, ItemType.STICK, 2, ItemType.FIBER, 2),
            new Recipe("Torch x2", ItemType.TORCH, 2, Station.HAND, ItemType.STICK, 1, ItemType.COAL, 1),
            new Recipe("Torch x2 (charcoal)", ItemType.TORCH, 2, Station.HAND,
                    ItemType.STICK, 1, ItemType.CHARCOAL, 1),
            new Recipe("Campfire", ItemType.CAMPFIRE, 1, Station.HAND,
                    ItemType.LOG, 3, ItemType.STONE, 2, ItemType.FIBER, 1),
            new Recipe("Workbench", ItemType.WORKBENCH, 1, Station.HAND, ItemType.PLANK, 4, ItemType.STICK, 2),
            new Recipe("Fiber Wrap", ItemType.FIBER_WRAP, 1, Station.HAND, ItemType.FIBER, 8),
            new Recipe("Bandage x2", ItemType.BANDAGE, 2, Station.HAND, ItemType.FIBER, 4),
            new Recipe("Splint", ItemType.SPLINT, 1, Station.HAND, ItemType.STICK, 2, ItemType.FIBER, 2),

            // ---- Workbench ----
            new Recipe("Wooden Pickaxe", ItemType.WOOD_PICKAXE, 1, Station.WORKBENCH,
                    ItemType.PLANK, 3, ItemType.STICK, 2),
            new Recipe("Wooden Axe", ItemType.WOOD_AXE, 1, Station.WORKBENCH,
                    ItemType.PLANK, 3, ItemType.STICK, 2),
            new Recipe("Stone Pickaxe", ItemType.STONE_PICKAXE, 1, Station.WORKBENCH,
                    ItemType.STONE, 3, ItemType.STICK, 2),
            new Recipe("Stone Axe", ItemType.STONE_AXE, 1, Station.WORKBENCH,
                    ItemType.STONE, 3, ItemType.STICK, 2),
            new Recipe("Bone Knife", ItemType.BONE_KNIFE, 1, Station.WORKBENCH,
                    ItemType.BONE, 1, ItemType.STICK, 1, ItemType.FIBER, 2),
            new Recipe("Storage Crate", ItemType.CRATE, 1, Station.WORKBENCH,
                    ItemType.PLANK, 6, ItemType.STONE, 1),
            new Recipe("Wall Block x2", ItemType.WALL, 2, Station.WORKBENCH,
                    ItemType.LOG, 1, ItemType.PLANK, 2),
            new Recipe("Drying Rack", ItemType.DRYING_RACK, 1, Station.WORKBENCH,
                    ItemType.PLANK, 2, ItemType.STICK, 4, ItemType.FIBER, 2),
            new Recipe("Stone Furnace", ItemType.FURNACE, 1, Station.WORKBENCH,
                    ItemType.STONE, 12, ItemType.CLAY, 4),
            new Recipe("Tannery", ItemType.TANNERY, 1, Station.WORKBENCH,
                    ItemType.LOG, 2, ItemType.STICK, 4, ItemType.STONE, 2),
            new Recipe("Herbalist Bench", ItemType.HERB_STATION, 1, Station.WORKBENCH,
                    ItemType.PLANK, 4, ItemType.HERB, 4, ItemType.STICK, 2),
            new Recipe("Map Table", ItemType.MAP_TABLE, 1, Station.WORKBENCH,
                    ItemType.PLANK, 6, ItemType.SCRAP, 1),
            new Recipe("Rain Collector", ItemType.RAIN_COLLECTOR, 1, Station.WORKBENCH,
                    ItemType.PLANK, 4, ItemType.SCRAP, 2),
            new Recipe("Waterskin", ItemType.WATERSKIN_EMPTY, 1, Station.WORKBENCH,
                    ItemType.HIDE, 1, ItemType.FIBER, 2),
            new Recipe("Bedroll", ItemType.BEDROLL, 1, Station.WORKBENCH,
                    ItemType.HIDE, 2, ItemType.FIBER, 6),
            new Recipe("Backpack", ItemType.BACKPACK, 1, Station.WORKBENCH,
                    ItemType.LEATHER, 3, ItemType.FIBER, 4),

            // ---- Campfire ----
            new Recipe("Charcoal x2", ItemType.CHARCOAL, 2, Station.CAMPFIRE, ItemType.LOG, 2),

            // ---- Furnace ----
            new Recipe("Copper Ingot", ItemType.COPPER_INGOT, 1, Station.FURNACE,
                    ItemType.COPPER_ORE, 1, ItemType.COAL, 1),
            new Recipe("Iron Ingot", ItemType.IRON_INGOT, 1, Station.FURNACE,
                    ItemType.IRON_ORE, 1, ItemType.COAL, 1),

            // ---- Anvil ----
            new Recipe("Iron Pickaxe", ItemType.IRON_PICKAXE, 1, Station.ANVIL,
                    ItemType.IRON_INGOT, 3, ItemType.STICK, 2),
            new Recipe("Iron Axe", ItemType.IRON_AXE, 1, Station.ANVIL,
                    ItemType.IRON_INGOT, 3, ItemType.STICK, 2),
            new Recipe("Iron Spear", ItemType.IRON_SPEAR, 1, Station.ANVIL,
                    ItemType.IRON_INGOT, 2, ItemType.STICK, 2, ItemType.FIBER, 1),
            new Recipe("Iron Knife", ItemType.IRON_KNIFE, 1, Station.ANVIL,
                    ItemType.IRON_INGOT, 1, ItemType.STICK, 1),
            new Recipe("Iron Armor", ItemType.IRON_ARMOR, 1, Station.ANVIL, "iron_armor",
                    ItemType.IRON_INGOT, 8, ItemType.LEATHER, 2),
            new Recipe("Beacon Frame", ItemType.BEACON_FRAME, 1, Station.ANVIL, "beacon",
                    ItemType.IRON_INGOT, 6, ItemType.SCRAP, 4, ItemType.COPPER_INGOT, 2),

            // ---- Tannery ----
            new Recipe("Leather", ItemType.LEATHER, 1, Station.TANNERY,
                    ItemType.HIDE, 1, ItemType.CHARCOAL, 1),
            new Recipe("Hide Coat", ItemType.HIDE_COAT, 1, Station.TANNERY,
                    ItemType.HIDE, 4, ItemType.FIBER, 4),
            new Recipe("Hide Pants", ItemType.HIDE_PANTS, 1, Station.TANNERY,
                    ItemType.HIDE, 3, ItemType.FIBER, 3),
            new Recipe("Hide Boots", ItemType.HIDE_BOOTS, 1, Station.TANNERY,
                    ItemType.HIDE, 2, ItemType.FIBER, 2),
            new Recipe("Fur Hood", ItemType.FUR_HOOD, 1, Station.TANNERY,
                    ItemType.HIDE, 2, ItemType.FIBER, 2),
            new Recipe("Leather Armor", ItemType.LEATHER_ARMOR, 1, Station.TANNERY,
                    ItemType.LEATHER, 5, ItemType.FIBER, 4),
            new Recipe("Rain Cloak", ItemType.RAIN_CLOAK, 1, Station.TANNERY, "rain_cloak",
                    ItemType.LEATHER, 2, ItemType.FIBER, 4),

            // ---- Herbalist bench ----
            new Recipe("Antiseptic x2", ItemType.ANTISEPTIC, 2, Station.HERB_STATION,
                    ItemType.HERB, 2, ItemType.CHARCOAL, 1),
            new Recipe("Herbal Poultice x2", ItemType.HERBAL_POULTICE, 2, Station.HERB_STATION,
                    ItemType.HERB, 3, ItemType.FIBER, 2),
            new Recipe("Medicine", ItemType.MEDICINE, 1, Station.HERB_STATION,
                    ItemType.HERB, 4, ItemType.CHARCOAL, 1, ItemType.BERRY, 2),
            new Recipe("Purify Water", ItemType.WATERSKIN_CLEAN, 1, Station.HERB_STATION,
                    ItemType.WATERSKIN_DIRTY, 1, ItemType.CHARCOAL, 1),

            // ---- Ranged weapons & cave gear (0.3.0) ----
            new Recipe("Arrow x4", ItemType.ARROW, 4, Station.HAND,
                    ItemType.STICK, 2, ItemType.STONE, 1, ItemType.FIBER, 1),
            new Recipe("Trail Marker x4", ItemType.TRAIL_MARKER, 4, Station.HAND,
                    ItemType.STICK, 1, ItemType.FIBER, 1),
            new Recipe("Primitive Bow", ItemType.PRIMITIVE_BOW, 1, Station.WORKBENCH,
                    ItemType.STICK, 3, ItemType.FIBER, 4),
            new Recipe("Rope Ladder x3", ItemType.ROPE_LADDER, 3, Station.WORKBENCH,
                    ItemType.STICK, 4, ItemType.FIBER, 6),
            new Recipe("Iron Arrow x4", ItemType.IRON_ARROW, 4, Station.WORKBENCH,
                    ItemType.STICK, 2, ItemType.IRON_INGOT, 1, ItemType.FIBER, 1),
            new Recipe("Black Powder x3", ItemType.BLACK_POWDER, 3, Station.WORKBENCH,
                    ItemType.SULFUR, 1, ItemType.SALTPETER, 2, ItemType.CHARCOAL, 1),
            new Recipe("Powder Keg", ItemType.POWDER_KEG, 1, Station.WORKBENCH,
                    ItemType.PLANK, 6, ItemType.BLACK_POWDER, 4, ItemType.SCRAP, 1),
            new Recipe("Scrap Bomb x2", ItemType.SCRAP_BOMB, 2, Station.WORKBENCH,
                    ItemType.BLACK_POWDER, 2, ItemType.SCRAP, 2, ItemType.FIBER, 1),
            new Recipe("Fire Bomb x2", ItemType.FIRE_BOMB, 2, Station.WORKBENCH,
                    ItemType.BLACK_POWDER, 1, ItemType.CHARCOAL, 1, ItemType.FIBER, 2),
            new Recipe("Lantern", ItemType.LANTERN, 1, Station.ANVIL,
                    ItemType.IRON_INGOT, 2, ItemType.TORCH, 1, ItemType.SCRAP, 1),
            new Recipe("Veylan Musket", ItemType.MUSKET, 1, Station.ANVIL,
                    ItemType.IRON_INGOT, 4, ItemType.PLANK, 2, ItemType.SCRAP, 2),
            new Recipe("Scrap Flintlock", ItemType.FLINTLOCK_PISTOL, 1, Station.ANVIL,
                    ItemType.IRON_INGOT, 2, ItemType.SCRAP, 2, ItemType.PLANK, 1),
            new Recipe("Scrap Blunderbuss", ItemType.BLUNDERBUSS, 1, Station.ANVIL,
                    ItemType.SCRAP, 4, ItemType.IRON_INGOT, 1, ItemType.PLANK, 2),
            new Recipe("Iron Ball x6", ItemType.MUSKET_BALL, 6, Station.ANVIL,
                    ItemType.IRON_INGOT, 1),
            new Recipe("Scrap Shot x6", ItemType.SCRAP_SHOT, 6, Station.ANVIL,
                    ItemType.SCRAP, 1),
            // Relic weapons cannot be crafted, only restored with rare parts.
            new Recipe("Restore Frontier Carbine", ItemType.RELIC_CARBINE, 1, Station.ANVIL,
                    ItemType.RELIC_CARBINE, 1, ItemType.RELIC_PARTS, 1),
            new Recipe("Restore Auto-Rifle", ItemType.RELIC_RIFLE, 1, Station.ANVIL,
                    ItemType.RELIC_RIFLE, 1, ItemType.RELIC_PARTS, 1),

            // ---- Map table ----
            new Recipe("Decode Blueprint", null, 0, Station.MAP_TABLE,
                    ItemType.BLUEPRINT_FRAGMENT, 2)
    );

    private CraftingSystem() {
    }

    public static boolean hasIngredients(Inventory inv, Recipe recipe) {
        for (var e : recipe.ingredients.entrySet()) {
            if (!inv.has(e.getKey(), e.getValue())) {
                return false;
            }
        }
        return true;
    }

    public static boolean knowsBlueprint(Recipe recipe, Set<String> blueprints) {
        return recipe.blueprint == null || blueprints.contains(recipe.blueprint);
    }

    public static boolean canCraft(Inventory inv, Recipe recipe, Set<Station> nearby,
                                   Set<String> blueprints) {
        if (!knowsBlueprint(recipe, blueprints)) {
            return false;
        }
        if (recipe.station != Station.HAND && !nearby.contains(recipe.station)) {
            return false;
        }
        if (recipe.result == null && nextBlueprint(blueprints) == null) {
            return false; // everything already decoded
        }
        return hasIngredients(inv, recipe);
    }

    /** The next undecoded blueprint id, or null when all are known. */
    public static String nextBlueprint(Set<String> blueprints) {
        for (String id : BLUEPRINT_ORDER) {
            if (!blueprints.contains(id)) {
                return id;
            }
        }
        return null;
    }

    /**
     * Attempts the craft; returns the decoded blueprint id for decode recipes,
     * the recipe name for item recipes, or null on failure.
     */
    public static String craft(Inventory inv, Recipe recipe, Set<Station> nearby,
                               Set<String> blueprints) {
        if (!canCraft(inv, recipe, nearby, blueprints)) {
            return null;
        }
        if (recipe.result == null) {
            String id = nextBlueprint(blueprints);
            if (id == null) {
                return null;
            }
            for (var e : recipe.ingredients.entrySet()) {
                inv.remove(e.getKey(), e.getValue());
            }
            blueprints.add(id);
            return id;
        }
        for (var e : recipe.ingredients.entrySet()) {
            inv.remove(e.getKey(), e.getValue());
        }
        int leftover = inv.add(recipe.result, recipe.resultCount);
        if (leftover > 0) {
            // Refund what didn't fit so items are never silently destroyed.
            for (var e : recipe.ingredients.entrySet()) {
                inv.add(e.getKey(), e.getValue());
            }
            inv.remove(recipe.result, recipe.resultCount - leftover);
            return null;
        }
        return recipe.name;
    }
}
