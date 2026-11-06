package com.veylon.settlement;

import com.veylon.item.ItemType;

/**
 * Humanoid body/combat profiles, primarily settlement roles; VEILBOUND has a separate brain.
 * Live NPCs persist by stable string {@link #id}; remains also pin the append-only ordinal.
 */
public enum NpcArchetype {
    //        id            display        hp   speed melee view  hear  weapon                 leader
    VILLAGER("villager", "Villager", 30, 3.2f, 4, 14, 12, null, false),
    GUARD("guard", "Guard", 45, 3.6f, 8, 20, 16, ItemType.IRON_SPEAR, false),
    MEDIC("medic", "Medic", 32, 3.2f, 4, 14, 12, null, false),
    TRADER("trader", "Trader", 34, 3.2f, 4, 14, 12, null, false),
    FARMER("farmer", "Farmer", 30, 3.2f, 4, 14, 12, null, false),
    SMITH("smith", "Blacksmith", 38, 3.2f, 6, 14, 12, null, false),
    ARCHER("archer", "Archer", 36, 3.5f, 4, 24, 16, ItemType.PRIMITIVE_BOW, false),

    SCAVENGER("scavenger", "Scavenger", 32, 3.8f, 6, 18, 14, null, false),

    TRACKER("tracker", "Headhunter Tracker", 34, 4.0f, 6, 22, 22, ItemType.BONE_KNIFE, false),
    SCOUT("scout", "Headhunter Scout", 30, 4.1f, 4, 26, 18, ItemType.PRIMITIVE_BOW, false),
    HUNTER("hunter", "Headhunter", 42, 3.7f, 9, 20, 16, ItemType.SPEAR, false),
    BRUTE("brute", "Headhunter Brute", 70, 2.9f, 13, 16, 12, ItemType.IRON_SPEAR, false),
    POWDERMAN("powderman", "Headhunter Powderman", 40, 3.3f, 5, 22, 16, ItemType.MUSKET, false),
    LEADER("leader", "Headhunter Leader", 90, 3.6f, 12, 24, 18, ItemType.IRON_SPEAR, true),

    CAPTIVE("captive", "Captive", 24, 3.2f, 2, 10, 10, null, false),

    /** Biological humanoid profile, never a settlement role or human faction member. */
    VEILBOUND("veilbound", "Veilbound", 28, 1.65f, 6, 18, 0, null, false),
    /**
     * The infected variant of urban milestone 15: membrane sealed over the face and a forearm fused
     * into a ram. Blind (no sight range), it hears farther than a Veilbound and hammers through
     * wooden doors. A biological profile like the Veilbound, never a settlement role.
     */
    BREACHER("breacher", "Breacher", 44, 1.25f, 9, 0, 22, null, false);

    public final String id;
    public final String displayName;
    public final float maxHealth;
    public final float speed;
    public final float meleeDamage;
    /** Sight range in blocks (halved for crouched targets, scaled by light). */
    public final float viewRange;
    public final float hearRange;
    /** Weapon carried; null = unarmed/fists. Ranged weapons use WeaponRegistry stats. */
    public final ItemType weapon;
    public final boolean leader;

    NpcArchetype(String id, String displayName, float maxHealth, float speed, float meleeDamage,
                 float viewRange, float hearRange, ItemType weapon, boolean leader) {
        this.id = id;
        this.displayName = displayName;
        this.maxHealth = maxHealth;
        this.speed = speed;
        this.meleeDamage = meleeDamage;
        this.viewRange = viewRange;
        this.hearRange = hearRange;
        this.weapon = weapon;
        this.leader = leader;
    }

    public static NpcArchetype byId(String id) {
        for (NpcArchetype a : values()) {
            if (a.id.equals(id)) {
                return a;
            }
        }
        return VILLAGER;
    }

    /** The infected profiles: a separate brain, no settlement allegiance, the encounter ledger. */
    public boolean infected() {
        return this == VEILBOUND || this == BREACHER;
    }

    /** True for archetypes belonging to hostile war parties. */
    public boolean hostileArchetype() {
        return this == SCAVENGER || this == TRACKER || this == SCOUT || this == HUNTER
                || this == BRUTE || this == POWDERMAN || this == LEADER;
    }

    /** Uses the shared projectile system (bow or firearm). */
    public boolean ranged() {
        return weapon == ItemType.PRIMITIVE_BOW || weapon == ItemType.MUSKET;
    }
}
