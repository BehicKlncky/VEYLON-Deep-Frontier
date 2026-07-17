package com.veylon.combat;

import com.veylon.item.ItemType;

/**
 * Data-driven ranged/thrown weapon stats, keyed by stable string id in
 * {@link WeaponRegistry}. Shared by the player and NPC AI so both obey the
 * same damage, spread, reload and noise rules.
 */
public final class WeaponDefinition {

    public enum Category {
        BOW, FIREARM, THROWN
    }

    public final String id;
    public final ItemType item;
    public final Category category;
    /** Damage per projectile/pellet at full power. */
    public final float damage;
    /** Effective range in blocks (projectiles despawn beyond ~1.5x). */
    public final float range;
    /** Seconds between shots once loaded/drawn. */
    public final float attackInterval;
    /** Bow: seconds of hold for a full-power shot. */
    public final float drawTime;
    /** Firearm: seconds to reload one round. */
    public final float reloadTime;
    /** Base angular spread in degrees (cone half-angle). */
    public final float spread;
    public final float projectileSpeed;
    /** Blocks/s^2 of projectile drop. */
    public final float projectileGravity;
    /** Ammo item consumed per shot; null = needs no ammo. */
    public final ItemType ammo;
    public final int ammoPerShot;
    /** Rounds held when fully loaded (1 = single-shot muzzleloader). */
    public final int magazine;
    /** World-noise radius in blocks emitted per shot. */
    public final float noiseRadius;
    /** Durability cost per shot. */
    public final float durabilityCost;
    /** View-kick intensity 0..1. */
    public final float recoil;
    /** Pellets per shot (blunderbuss). */
    public final int pellets;
    /** Full-auto fire while the trigger is held. */
    public final boolean automatic;
    /** Craftable weapons follow survival progression; relics are loot-only. */
    public final boolean relic;

    WeaponDefinition(String id, ItemType item, Category category, float damage, float range,
                     float attackInterval, float drawTime, float reloadTime, float spread,
                     float projectileSpeed, float projectileGravity, ItemType ammo,
                     int ammoPerShot, int magazine, float noiseRadius, float durabilityCost,
                     float recoil, int pellets, boolean automatic, boolean relic) {
        this.id = id;
        this.item = item;
        this.category = category;
        this.damage = damage;
        this.range = range;
        this.attackInterval = attackInterval;
        this.drawTime = drawTime;
        this.reloadTime = reloadTime;
        this.spread = spread;
        this.projectileSpeed = projectileSpeed;
        this.projectileGravity = projectileGravity;
        this.ammo = ammo;
        this.ammoPerShot = ammoPerShot;
        this.magazine = magazine;
        this.noiseRadius = noiseRadius;
        this.durabilityCost = durabilityCost;
        this.recoil = recoil;
        this.pellets = pellets;
        this.automatic = automatic;
        this.relic = relic;
    }
}
