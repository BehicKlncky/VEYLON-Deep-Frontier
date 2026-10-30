package com.veylon.item;

/**
 * What the player is shown an item as (urban milestone 21). A city's prompts, recipes and stores
 * speak of a pry tool, and its pry tool is the iron pickaxe's item: in a city the screens call it
 * that, so the tool a locked door or the relay's cabinet asks for is the one the pack shows. Saves
 * keep the item's ordinal; the wilderness keeps every name.
 */
public final class ItemNames {
    /** The city's name for the iron pickaxe's item. */
    public static final String PRY_TOOL = "Pry Tool";

    private ItemNames() { }

    /** The name an item is shown by, in a city or elsewhere. */
    public static String of(ItemType type, boolean city) {
        return city && type == ItemType.IRON_PICKAXE ? PRY_TOOL : type.displayName;
    }

    /** What a prompt calls the tool a block needs: a pry tool in a city, else the tool's own word. */
    public static String toolNoun(ToolKind kind, boolean city) {
        return switch (kind) {
            case PICKAXE -> city ? "pry tool" : "pickaxe";
            case AXE -> "axe";
            case KNIFE -> "knife";
            case WEAPON -> "weapon";
            case BOW -> "bow";
            case FIREARM -> "firearm";
            case THROWN -> "throwing weapon";
            case NONE -> "tool";
        };
    }
}
