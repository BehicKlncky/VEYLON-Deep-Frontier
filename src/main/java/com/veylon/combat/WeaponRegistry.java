package com.veylon.combat;

import com.veylon.item.ItemType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * All ranged/thrown weapon definitions, registered by stable string id.
 * Balance intent: the bow is the quiet hunting tool; black-powder guns hit
 * hard but are slow, loud and risky; relic rifles are loot-only late-game
 * tools starved by scarce cartridges.
 */
public final class WeaponRegistry {

    private static final Map<String, WeaponDefinition> BY_ID = new LinkedHashMap<>();
    private static final Map<ItemType, WeaponDefinition> BY_ITEM = new LinkedHashMap<>();

    static {
        //  id                 item                     cat      dmg  range intvl draw  reload sprd  speed grav  ammo                     n  mag noise  dura  kick pellets auto  relic
        def("primitive_bow", ItemType.PRIMITIVE_BOW, WeaponDefinition.Category.BOW,
                11f, 34f, 0.4f, 0.9f, 0f, 1.6f, 26f, 14f, ItemType.ARROW, 1, 1, 9f, 1f, 0.12f, 1, false, false);
        def("musket", ItemType.MUSKET, WeaponDefinition.Category.FIREARM,
                34f, 52f, 0.5f, 0f, 3.6f, 2.2f, 85f, 3.5f, ItemType.MUSKET_BALL, 1, 1, 90f, 1.5f, 0.65f, 1, false, false);
        def("flintlock", ItemType.FLINTLOCK_PISTOL, WeaponDefinition.Category.FIREARM,
                18f, 26f, 0.4f, 0f, 2.4f, 3.5f, 70f, 4.5f, ItemType.MUSKET_BALL, 1, 1, 70f, 1.2f, 0.4f, 1, false, false);
        def("blunderbuss", ItemType.BLUNDERBUSS, WeaponDefinition.Category.FIREARM,
                7f, 13f, 0.6f, 0f, 3.0f, 9.0f, 55f, 6f, ItemType.SCRAP_SHOT, 2, 1, 85f, 1.5f, 0.75f, 6, false, false);
        def("scrap_bomb", ItemType.SCRAP_BOMB, WeaponDefinition.Category.THROWN,
                0f, 18f, 1.2f, 0f, 0f, 3f, 13f, 16f, null, 0, 1, 60f, 0f, 0.2f, 1, false, false);
        def("fire_bomb", ItemType.FIRE_BOMB, WeaponDefinition.Category.THROWN,
                0f, 18f, 1.2f, 0f, 0f, 3f, 13f, 16f, null, 0, 1, 40f, 0f, 0.2f, 1, false, false);
        // Relic tier: distinct roles — the carbine is the accurate marksman
        // tool, the auto-rifle the loud close-range room clearer.
        def("relic_carbine", ItemType.RELIC_CARBINE, WeaponDefinition.Category.FIREARM,
                16f, 60f, 0.35f, 0f, 2.0f, 0.8f, 110f, 1.5f, ItemType.RIFLE_CARTRIDGE, 1, 8, 80f, 0.8f, 0.22f, 1, false, true);
        def("relic_rifle", ItemType.RELIC_RIFLE, WeaponDefinition.Category.FIREARM,
                11f, 40f, 0.11f, 0f, 3.0f, 3.8f, 100f, 2f, ItemType.RIFLE_CARTRIDGE, 1, 20, 95f, 1.2f, 0.5f, 1, true, true);
    }

    private static void def(String id, ItemType item, WeaponDefinition.Category cat,
                            float dmg, float range, float intvl, float draw, float reload,
                            float spread, float speed, float grav, ItemType ammo, int perShot,
                            int mag, float noise, float dura, float recoil, int pellets,
                            boolean auto, boolean relic) {
        WeaponDefinition d = new WeaponDefinition(id, item, cat, dmg, range, intvl, draw, reload,
                spread, speed, grav, ammo, perShot, mag, noise, dura, recoil, pellets, auto, relic);
        BY_ID.put(id, d);
        BY_ITEM.put(item, d);
    }

    private WeaponRegistry() {
    }

    public static WeaponDefinition byId(String id) {
        return BY_ID.get(id);
    }

    public static WeaponDefinition of(ItemType item) {
        return item == null ? null : BY_ITEM.get(item);
    }

    public static Iterable<WeaponDefinition> all() {
        return BY_ID.values();
    }

    /** Better arrows upgrade bow damage; used when firing a specific ammo type. */
    public static float ammoDamageBonus(ItemType ammo) {
        return ammo == ItemType.IRON_ARROW ? 5f : 0f;
    }
}
