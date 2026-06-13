package com.veylon.item;

/** Slot-based inventory. For the player, slots 0-8 are the hotbar. */
public class Inventory {

    private final ItemStack[] slots;

    public Inventory(int size) {
        slots = new ItemStack[size];
    }

    public int size() {
        return slots.length;
    }

    public ItemStack get(int i) {
        return i >= 0 && i < slots.length ? slots[i] : null;
    }

    public void set(int i, ItemStack stack) {
        if (i >= 0 && i < slots.length) {
            slots[i] = (stack != null && stack.count <= 0) ? null : stack;
        }
    }

    /** Adds items, stacking first. Returns the count that did NOT fit. */
    public int add(ItemType type, int count) {
        if (type == null || count <= 0) {
            return 0;
        }
        for (int i = 0; i < slots.length && count > 0; i++) {
            ItemStack s = slots[i];
            if (s != null && s.type == type && s.count < type.maxStack) {
                int move = Math.min(count, type.maxStack - s.count);
                s.count += move;
                count -= move;
                // New items are fresh; blending raises the merged freshness slightly.
                if (type.spoils()) {
                    s.freshness = Math.min(type.spoilSeconds,
                            (s.freshness * (s.count - move) + type.spoilSeconds * move) / s.count);
                }
            }
        }
        for (int i = 0; i < slots.length && count > 0; i++) {
            if (slots[i] == null) {
                int move = Math.min(count, type.maxStack);
                slots[i] = new ItemStack(type, move);
                count -= move;
            }
        }
        return count;
    }

    /** Adds a specific stack (preserving durability/freshness). Returns leftover count. */
    public int addStack(ItemStack stack) {
        if (stack == null || stack.count <= 0) {
            return 0;
        }
        ItemType type = stack.type;
        if (type.maxStack > 1) {
            for (int i = 0; i < slots.length && stack.count > 0; i++) {
                ItemStack s = slots[i];
                if (s != null && s.type == type && s.count < type.maxStack) {
                    int move = Math.min(stack.count, type.maxStack - s.count);
                    // Mixed freshness merges to the count-weighted average.
                    if (type.spoils()) {
                        s.freshness = (s.freshness * s.count + stack.freshness * move) / (s.count + move);
                    }
                    s.count += move;
                    stack.count -= move;
                }
            }
        }
        for (int i = 0; i < slots.length && stack.count > 0; i++) {
            if (slots[i] == null) {
                int move = Math.min(stack.count, type.maxStack);
                ItemStack ns = stack.copy();
                ns.count = move;
                slots[i] = ns;
                stack.count -= move;
            }
        }
        return stack.count;
    }

    public int count(ItemType type) {
        int total = 0;
        for (ItemStack s : slots) {
            if (s != null && s.type == type) {
                total += s.count;
            }
        }
        return total;
    }

    public boolean has(ItemType type, int count) {
        return count(type) >= count;
    }

    /** Removes up to count items of the type. Returns how many were removed. */
    public int remove(ItemType type, int count) {
        int removed = 0;
        for (int i = 0; i < slots.length && removed < count; i++) {
            ItemStack s = slots[i];
            if (s != null && s.type == type) {
                int take = Math.min(count - removed, s.count);
                s.count -= take;
                removed += take;
                if (s.count <= 0) {
                    slots[i] = null;
                }
            }
        }
        return removed;
    }

    /** Removes count items from a specific slot. */
    public void shrink(int slot, int count) {
        ItemStack s = get(slot);
        if (s != null) {
            s.count -= count;
            if (s.count <= 0) {
                set(slot, null);
            }
        }
    }

    /** Total weight in kg of everything stored. */
    public float totalWeight() {
        float w = 0;
        for (ItemStack s : slots) {
            if (s != null) {
                w += s.type.weight * s.count;
            }
        }
        return w;
    }

    public boolean isEmpty() {
        for (ItemStack s : slots) {
            if (s != null) {
                return false;
            }
        }
        return true;
    }

    public void clear() {
        for (int i = 0; i < slots.length; i++) {
            slots[i] = null;
        }
    }
}
