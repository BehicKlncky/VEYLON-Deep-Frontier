package com.veylon.item;

/**
 * Fluent property bag used by the {@link ItemType} enum constructor so item
 * definitions stay readable despite the large number of survival stats.
 */
public final class ItemProps {

    int maxStack = 64;
    String placesName;
    ToolKind tool = ToolKind.NONE;
    float toolPower = 1f;
    float damage = 1f;
    int food;
    int hydration;
    FoodGroup group = FoodGroup.NONE;
    float weight = 0.5f;
    /** Real seconds until the item spoils; -1 = never. */
    float spoilSeconds = -1;
    /** Uses before the item breaks; -1 = indestructible. */
    float maxDurability = -1;
    EquipSlot equipSlot;
    /** Degrees C of cold protection while worn. */
    float insulation;
    /** 0..1 fraction of rain/wetness blocked while worn. */
    float wetResist;
    /** Flat-ish damage reduction points while worn. */
    float armor;
    /** Extra carry capacity in kg while worn. */
    float carryBonus;
    float r = 0.7f, g = 0.7f, b = 0.7f;

    private ItemProps() {
    }

    public static ItemProps p() {
        return new ItemProps();
    }

    public ItemProps stack(int n) {
        maxStack = n;
        return this;
    }

    public ItemProps places(String blockName) {
        placesName = blockName;
        return this;
    }

    public ItemProps tool(ToolKind kind, float power) {
        tool = kind;
        toolPower = power;
        maxStack = 1;
        return this;
    }

    public ItemProps damage(float d) {
        damage = d;
        return this;
    }

    public ItemProps food(int satiety, FoodGroup g) {
        food = satiety;
        group = g;
        return this;
    }

    public ItemProps hydration(int h) {
        hydration = h;
        return this;
    }

    public ItemProps weight(float kg) {
        weight = kg;
        return this;
    }

    public ItemProps spoils(float seconds) {
        spoilSeconds = seconds;
        return this;
    }

    public ItemProps durability(float uses) {
        maxDurability = uses;
        return this;
    }

    public ItemProps gear(EquipSlot slot, float insulation, float wetResist, float armor) {
        equipSlot = slot;
        this.insulation = insulation;
        this.wetResist = wetResist;
        this.armor = armor;
        maxStack = 1;
        return this;
    }

    public ItemProps carry(float kg) {
        carryBonus = kg;
        return this;
    }

    public ItemProps color(float r, float g, float b) {
        this.r = r;
        this.g = g;
        this.b = b;
        return this;
    }
}
