package com.veylon.item;

/**
 * R17 grant rules for the Creative catalog. Every grant builds a new stack
 * through the ordinary {@link ItemStack} constructor, so it starts with full
 * durability and freshness and an empty magazine, exactly like crafted items.
 */
public final class CreativeGrants {

    /** Outcome the screen reports to the player. */
    public enum Result { GRANTED, INVENTORY_FULL }

    private CreativeGrants() {
    }

    /** A fresh stack of {@code type}: its full stack size, or a single item. */
    public static ItemStack fresh(ItemType type, boolean fullStack) {
        return new ItemStack(type, fullStack ? type.maxStack : 1);
    }

    /**
     * Left click grants a full stack and right click one item. The empty
     * selected hotbar slot is preferred, then the first empty slot; a full
     * inventory changes nothing.
     */
    public static Result grant(Inventory inventory, int selectedSlot, ItemType type, boolean fullStack) {
        int target = inventory.get(selectedSlot) == null && selectedSlot >= 0
                && selectedSlot < inventory.size() ? selectedSlot : firstEmpty(inventory);
        if (target < 0) {
            return Result.INVENTORY_FULL;
        }
        inventory.set(target, fresh(type, fullStack));
        return Result.GRANTED;
    }

    /** Hovering an entry and pressing 1-9 replaces that hotbar slot with a full stack. */
    public static void putInHotbar(Inventory inventory, int hotbarSlot, ItemType type) {
        inventory.set(hotbarSlot, fresh(type, true));
    }

    static int firstEmpty(Inventory inventory) {
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.get(i) == null) {
                return i;
            }
        }
        return -1;
    }
}
