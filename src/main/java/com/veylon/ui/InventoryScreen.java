package com.veylon.ui;

import com.veylon.Game;
import com.veylon.engine.UiRenderer;
import com.veylon.item.EquipSlot;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

/**
 * 9x4 inventory grid plus equipment slots. Click a slot to pick it up, click
 * another to swap/merge; click a gear slot to equip/unequip. Shows item
 * details: weight, nutrition, durability, freshness and gear stats.
 */
public class InventoryScreen {

    private static final int COLS = 9;
    private static final int ROWS = 4;
    private static final float SLOT = 52;

    private int selectedSlot = -1;

    public void update(Game g) {
        UiRenderer ui = g.ui;
        int w = ui.screenW(), h = ui.screenH();
        float gw = COLS * SLOT, gh = ROWS * SLOT;
        float eqW = SLOT + 24;
        float x0 = w / 2f - (gw - eqW) / 2f, y0 = h / 2f - gh / 2f - 10;
        float ex = x0 - eqW - 22;

        ui.rect(ex - 16, y0 - 52, gw + eqW + 56, gh + 130, 0.07f, 0.07f, 0.1f, 0.93f);
        ui.rectOutline(ex - 16, y0 - 52, gw + eqW + 56, gh + 130, 2, 0.6f, 0.6f, 0.7f, 0.9f);
        ui.textCentered(w / 2f, y0 - 40, 2f, "INVENTORY", 1f, 1f, 1f, 1f);
        ui.textCentered(w / 2f, y0 + gh + 12, 1.25f,
                "Click to select, click again to move/swap. Click a gear slot to equip. Row 1 = hotbar. [E] close",
                0.75f, 0.75f, 0.75f, 1f);
        float weight = g.player.carriedWeight();
        float cap = g.player.carryCapacity();
        boolean over = weight > cap;
        ui.textCentered(w / 2f, y0 + gh + 30, 1.4f,
                String.format("Carry weight: %.1f / %.0f kg%s", weight, cap,
                        over ? "  (OVERLOADED: slow, no sprint)" : ""),
                over ? 1f : 0.85f, over ? 0.45f : 0.9f, over ? 0.3f : 0.75f, 1f);

        double mx = g.input.cursorX(), my = g.input.cursorY();
        int hover = -1;
        int hoverEquip = -1;

        // Equipment column.
        ui.textShadow(ex, y0 - 18, 1.3f, "GEAR", 0.85f, 0.85f, 0.6f, 1f);
        EquipSlot[] eqSlots = EquipSlot.values();
        for (int i = 0; i < eqSlots.length; i++) {
            float y = y0 + i * (SLOT - 6);
            boolean inside = mx >= ex && mx < ex + SLOT && my >= y && my < y + SLOT - 8;
            if (inside) {
                hoverEquip = i;
            }
            ui.rect(ex + 2, y + 2, SLOT - 4, SLOT - 12, 0.12f, 0.14f, 0.10f, 0.95f);
            ui.rectOutline(ex + 2, y + 2, SLOT - 4, SLOT - 12, 1,
                    inside ? 0.95f : 0.55f, inside ? 0.95f : 0.55f, inside ? 0.95f : 0.45f, 0.85f);
            ItemStack s = g.player.equipment[i];
            if (s != null) {
                ui.rect(ex + 11, y + 8, SLOT - 22, SLOT - 28, s.type.r, s.type.g, s.type.b, 1f);
                if (s.type.hasDurability()) {
                    float frac = s.durabilityFrac();
                    ui.rect(ex + 6, y + SLOT - 16, SLOT - 12, 3, 0.1f, 0.1f, 0.1f, 0.9f);
                    ui.rect(ex + 6, y + SLOT - 16, (SLOT - 12) * frac, 3, 1f - frac, frac, 0.15f, 1f);
                }
            } else {
                ui.text(ex + 8, y + SLOT / 2 - 8, 1f, eqSlots[i].displayName,
                        0.5f, 0.5f, 0.5f, 0.8f);
            }
        }

        // Main grid.
        for (int i = 0; i < COLS * ROWS; i++) {
            int col = i % COLS, row = i / COLS;
            float x = x0 + col * SLOT, y = y0 + row * SLOT;
            boolean isHotbar = row == 0;
            boolean inside = mx >= x && mx < x + SLOT && my >= y && my < y + SLOT;
            if (inside) {
                hover = i;
            }
            ui.rect(x + 2, y + 2, SLOT - 4, SLOT - 4,
                    isHotbar ? 0.13f : 0.10f, isHotbar ? 0.13f : 0.10f, isHotbar ? 0.18f : 0.13f, 0.95f);
            if (i == selectedSlot) {
                ui.rectOutline(x + 1, y + 1, SLOT - 2, SLOT - 2, 2, 1f, 0.9f, 0.3f, 1f);
            } else if (inside) {
                ui.rectOutline(x + 2, y + 2, SLOT - 4, SLOT - 4, 1, 0.9f, 0.9f, 0.9f, 0.9f);
            } else {
                ui.rectOutline(x + 2, y + 2, SLOT - 4, SLOT - 4, 1, 0.45f, 0.45f, 0.5f, 0.7f);
            }
            ItemStack s = g.player.inventory.get(i);
            if (s != null) {
                ui.rect(x + 11, y + 9, SLOT - 22, SLOT - 26, s.type.r, s.type.g, s.type.b, 1f);
                if (s.count > 1) {
                    ui.textShadow(x + 7, y + SLOT - 16, 1.2f, String.valueOf(s.count), 1f, 1f, 1f, 1f);
                }
                if (s.type.hasDurability()) {
                    float frac = s.durabilityFrac();
                    ui.rect(x + 6, y + SLOT - 8, SLOT - 12, 3, 0.1f, 0.1f, 0.1f, 0.9f);
                    ui.rect(x + 6, y + SLOT - 8, (SLOT - 12) * frac, 3, 1f - frac, frac, 0.15f, 1f);
                } else if (s.type.spoils()) {
                    float frac = s.freshnessFrac();
                    ui.rect(x + 6, y + SLOT - 8, SLOT - 12, 3, 0.1f, 0.1f, 0.1f, 0.9f);
                    ui.rect(x + 6, y + SLOT - 8, (SLOT - 12) * frac, 3,
                            0.5f - frac * 0.2f, 0.32f + frac * 0.45f, 0.2f, 1f);
                }
            }
        }

        // Hovered item details.
        ItemStack detail = null;
        if (hover >= 0) {
            detail = g.player.inventory.get(hover);
        } else if (hoverEquip >= 0) {
            detail = g.player.equipment[hoverEquip];
        }
        if (detail != null) {
            ui.textCentered(w / 2f, y0 + gh + 48, 1.5f, detail.type.displayName, 1f, 1f, 0.85f, 1f);
            ui.textCentered(w / 2f, y0 + gh + 66, 1.2f, detailLine(detail), 0.75f, 0.8f, 0.85f, 1f);
        }

        if (g.input.wasMousePressed(GLFW_MOUSE_BUTTON_LEFT)) {
            if (hoverEquip >= 0) {
                handleEquipClick(g, hoverEquip);
            } else if (hover >= 0) {
                g.audio.playClick();
                if (selectedSlot < 0) {
                    if (g.player.inventory.get(hover) != null) {
                        selectedSlot = hover;
                    }
                } else if (hover == selectedSlot) {
                    selectedSlot = -1;
                } else {
                    moveOrSwap(g.player.inventory, selectedSlot, hover);
                    selectedSlot = -1;
                }
            }
        }
    }

    private void handleEquipClick(Game g, int slotIdx) {
        EquipSlot slot = EquipSlot.values()[slotIdx];
        ItemStack worn = g.player.equipment[slotIdx];
        if (selectedSlot >= 0) {
            ItemStack sel = g.player.inventory.get(selectedSlot);
            if (sel != null && sel.type.equipSlot == slot) {
                // Equip the selected item, return what was worn.
                ItemStack toWear = sel.copy();
                toWear.count = 1;
                g.player.inventory.shrink(selectedSlot, 1);
                g.player.equipment[slotIdx] = toWear;
                if (worn != null) {
                    g.player.inventory.addStack(worn);
                }
                g.audio.playEquip();
                g.log("Equipped " + toWear.type.displayName + ".");
            } else if (sel != null) {
                g.log(sel.type.displayName + " can't be worn on: " + slot.displayName);
            }
            selectedSlot = -1;
            return;
        }
        if (worn != null) {
            int leftover = g.player.inventory.addStack(worn);
            if (leftover == 0) {
                g.player.equipment[slotIdx] = null;
                g.audio.playEquip();
                g.log("Unequipped " + worn.type.displayName + ".");
            } else {
                g.log("No room in your inventory.");
            }
        }
    }

    private String detailLine(ItemStack s) {
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

    static void moveOrSwap(Inventory inv, int from, int to) {
        ItemStack a = inv.get(from);
        ItemStack b = inv.get(to);
        if (a == null) {
            return;
        }
        if (b != null && b.type == a.type && b.count < b.type.maxStack) {
            int move = Math.min(a.count, b.type.maxStack - b.count);
            if (a.type.spoils()) {
                b.freshness = (b.freshness * b.count + a.freshness * move) / (b.count + move);
            }
            b.count += move;
            a.count -= move;
            inv.set(from, a.count > 0 ? a : null);
        } else {
            inv.set(to, a);
            inv.set(from, b);
        }
    }

    public void reset() {
        selectedSlot = -1;
    }
}
