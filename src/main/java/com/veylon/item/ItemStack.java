package com.veylon.item;

public final class ItemStack {

    public ItemType type;
    public int count;
    /** Remaining uses for tools/gear; -1 when the type has no durability. */
    public float durability;
    /** Remaining real seconds of freshness; -1 when the type never spoils. */
    public float freshness;

    public ItemStack(ItemType type, int count) {
        this.type = type;
        this.count = count;
        this.durability = type.maxDurability;
        this.freshness = type.spoilSeconds;
    }

    public ItemStack copy() {
        ItemStack s = new ItemStack(type, count);
        s.durability = durability;
        s.freshness = freshness;
        return s;
    }

    /** 0..1 fraction of durability remaining (1 if the item has none). */
    public float durabilityFrac() {
        return type.hasDurability() ? Math.max(0, durability / type.maxDurability) : 1f;
    }

    /** 0..1 fraction of freshness remaining (1 if the item never spoils). */
    public float freshnessFrac() {
        return type.spoils() ? Math.max(0, freshness / type.spoilSeconds) : 1f;
    }

    @Override
    public String toString() {
        return type.displayName + " x" + count;
    }
}
