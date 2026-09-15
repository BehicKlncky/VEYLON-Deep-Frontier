package com.veylon.ui;

import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;

/**
 * The one-line item summary shared by the inventory screen and the Creative
 * catalog tooltip, so both show weight, nutrition, freshness, durability and
 * gear stats identically.
 */
final class ItemDetails {

    private ItemDetails() {
    }

    static String line(ItemStack s) {
        ItemType t = s.type;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%.1f kg", t.weight * s.count));
        if (t.food > 0) {
            sb.append("  food +").append(t.food);
        }
        if (t.hydration > 0) {
            sb.append("  water +").append(t.hydration);
        }
        if (t.spoils()) {
            sb.append("  fresh ").append((int) (s.freshnessFrac() * 100)).append("%");
        }
        if (t.hasDurability()) {
            sb.append("  dur ").append((int) s.durability).append("/").append((int) t.maxDurability);
        }
        if (t.damage > 1.5f) {
            sb.append("  dmg ").append((int) t.damage);
        }
        if (t.insulation > 0) {
            sb.append("  warmth +").append((int) t.insulation);
        }
        if (t.wetResist > 0) {
            sb.append("  rainproof ").append((int) (t.wetResist * 100)).append("%");
        }
        if (t.armor > 0) {
            sb.append("  armor ").append((int) t.armor);
        }
        if (t.carryBonus > 0) {
            sb.append("  +").append((int) t.carryBonus).append("kg capacity");
        }
        return sb.toString();
    }
}
