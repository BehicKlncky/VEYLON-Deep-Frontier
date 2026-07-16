package com.veylon.ui;

import com.veylon.Game;
import com.veylon.engine.UiRenderer;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

/** Storage crate UI: click an item to transfer it between crate and inventory. */
public class CrateScreen {

    private static final float SLOT = 50;

    public void update(Game g) {
        Inventory crate = g.openCrate;
        if (crate == null) {
            return;
        }
        UiRenderer ui = g.ui;
        int w = ui.screenW(), h = ui.screenH();

        float pw = 620, ph = 470;
        float x0 = w / 2f - pw / 2f, y0 = h / 2f - ph / 2f;
        ui.panel(x0, y0, pw, ph);
        ui.textCentered(w / 2f, y0 + 10, 2f, "STORAGE CRATE", 1f, 1f, 1f, 1f);
        ui.textCentered(w / 2f, y0 + ph - 22, 1.2f, "Click an item to transfer it. [F or Esc] close",
                0.7f, 0.7f, 0.7f, 1f);

        double mx = g.input.cursorX(), my = g.input.cursorY();
        boolean click = g.input.wasMousePressed(GLFW_MOUSE_BUTTON_LEFT);

        // Crate grid: 4x3.
        ui.textShadow(x0 + 20, y0 + 42, 1.4f, "Crate", 0.9f, 0.85f, 0.6f, 1f);
        int moveFromCrate = drawGrid(g, ui, crate, x0 + 20, y0 + 62, 4, mx, my);
        // Player grid: 9x4 (smaller slots).
        ui.textShadow(x0 + 20, y0 + 232, 1.4f, "Your inventory", 0.7f, 0.85f, 0.95f, 1f);
        int moveFromPlayer = drawGridSmall(g, ui, g.player.inventory, x0 + 20, y0 + 252, 9, mx, my);

        if (click) {
            if (moveFromCrate >= 0) {
                ItemStack s = crate.get(moveFromCrate);
                if (s != null) {
                    int before = s.count;
                    int leftover = g.player.inventory.addStack(s);
                    int taken = before - leftover;
                    crate.set(moveFromCrate, leftover > 0 ? s : null);
                    if (taken > 0) {
                        g.audio.playClick();
                        g.onCrateItemTaken(s.type, taken);
                    }
                }
            } else if (moveFromPlayer >= 0) {
                ItemStack s = g.player.inventory.get(moveFromPlayer);
                if (s != null) {
                    int leftover = crate.addStack(s);
                    g.player.inventory.set(moveFromPlayer, leftover > 0 ? s : null);
                    g.audio.playClick();
                }
            }
        }
    }

    private int drawGrid(Game g, UiRenderer ui, Inventory inv, float x0, float y0, int cols,
                         double mx, double my) {
        int hover = -1;
        for (int i = 0; i < inv.size(); i++) {
            float x = x0 + (i % cols) * SLOT;
            float y = y0 + (i / cols) * SLOT;
            boolean inside = mx >= x && mx < x + SLOT && my >= y && my < y + SLOT;
            if (inside) {
                hover = i;
            }
            drawSlot(ui, inv.get(i), x, y, SLOT, inside);
        }
        return hover;
    }

    private int drawGridSmall(Game g, UiRenderer ui, Inventory inv, float x0, float y0, int cols,
                              double mx, double my) {
        float s = 44;
        int hover = -1;
        for (int i = 0; i < inv.size(); i++) {
            float x = x0 + (i % cols) * s;
            float y = y0 + (i / cols) * s;
            boolean inside = mx >= x && mx < x + s && my >= y && my < y + s;
            if (inside) {
                hover = i;
            }
            drawSlot(ui, inv.get(i), x, y, s, inside);
        }
        return hover;
    }

    private void drawSlot(UiRenderer ui, ItemStack s, float x, float y, float size, boolean hover) {
        ui.slot(x + 2, y + 2, size - 4, hover, false);
        if (s != null) {
            ui.itemIcon(s.type, x + 7, y + 5, size - 14);
            ui.textShadow(x + 6, y + size - 15, 1.1f, String.valueOf(s.count), 1f, 1f, 1f, 1f);
        }
    }
}
