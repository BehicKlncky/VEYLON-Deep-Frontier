package com.veylon.ui;

import com.veylon.Game;
import com.veylon.engine.UiRenderer;
import com.veylon.item.Inventory;
import com.veylon.item.ItemNames;
import com.veylon.item.ItemStack;
import com.veylon.world.BlockType;
import com.veylon.world.Fixtures;

import java.util.Locale;

import static com.veylon.ui.HudStyle.*;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;

/**
 * Storage UI: click an item to transfer it between the container and the pack. The world keeps
 * running while it is open. The header names the container and whose it is, warning where taking
 * is theft; a search shows its progress and its cost; slots show condition and freshness like the
 * hotbar, and the item under the cursor is described (urban milestone 21).
 */
public class CrateScreen {

    private static final float SLOT = 50;
    private static final float PANEL_W = 620, PANEL_H = 470;

    public void update(Game g) {
        Inventory crate = g.openCrate;
        if (g.openCratePos == null) {
            return;
        }
        UiRenderer ui = g.ui;
        int w = ui.screenW(), h = ui.screenH();

        float x0 = w / 2f - PANEL_W / 2f, y0 = h / 2f - PANEL_H / 2f;
        ui.panel(x0, y0, PANEL_W, PANEL_H, 8);
        ui.textCentered(w / 2f, y0 + 10, 2f, title(g), 1f, 1f, 1f, 1f);

        double mx = g.input.cursorX(), my = g.input.cursorY();
        boolean click = g.input.wasMousePressed(GLFW_MOUSE_BUTTON_LEFT);
        boolean single = g.input.wasMousePressed(GLFW_MOUSE_BUTTON_RIGHT);

        // Whose it is, then how the search stands: "Abandoned property | Searching 40%".
        String status = g.crateStatus();
        int bar = status.indexOf(" | ");
        String owner = bar < 0 ? status : status.substring(0, bar);
        String state = bar < 0 ? "" : status.substring(bar + 3);
        fitted(ui, x0 + 20, y0 + 40, PANEL_W - 40, SMALL, owner, ownerTone(owner));

        // Crate grid: 4x3.
        int moveFromCrate = drawGrid(g, ui, crate, x0 + 20, y0 + 66, 4, mx, my);
        float infoX = x0 + 250, infoW = PANEL_W - 270;
        if (g.crateSearching()) {
            float progress = Math.max(0, Math.min(1, g.crateSearchProgress()));
            text(ui, infoX, y0 + 66, SMALL, "Searching " + Math.round(progress * 100) + "%", AMBER);
            rect(ui, infoX, y0 + 90, infoW, 14, NAVY, 1);
            rect(ui, infoX + 2, y0 + 92, (infoW - 4) * progress, 10, AMBER, .95f);
            ui.rectOutline(infoX, y0 + 90, infoW, 14, 1, r(LINE), HudStyle.g(LINE), b(LINE), 1);
            text(ui, infoX, y0 + 114, MICRO, "Rummaging makes noise that carries.", AMBER);
            text(ui, infoX, y0 + 132, MICRO, "Stay within reach: the world keeps moving.", MUTED);
            text(ui, infoX, y0 + 150, MICRO, "Closing keeps your progress.", MUTED);
        } else if (!state.isEmpty()) {
            text(ui, infoX, y0 + 66, SMALL, state, MUTED);
        }
        // Player grid: 9x4 (smaller slots).
        text(ui, x0 + 20, y0 + 230, SMALL, "Your pack", CYAN);
        int moveFromPlayer = drawGridSmall(g, ui, g.player.inventory, x0 + 20, y0 + 252, 9, mx, my);

        ItemStack hovered = moveFromCrate >= 0 && crate != null ? crate.get(moveFromCrate)
                : moveFromPlayer >= 0 ? g.player.inventory.get(moveFromPlayer) : null;
        if (hovered != null) {
            float dy = g.crateSearching() ? y0 + 176 : y0 + 96;
            fitted(ui, infoX, dy, infoW, SMALL, ItemNames.of(hovered.type, g.world.isUrban())
                    + (hovered.count > 1 ? " x" + hovered.count : ""), TEXT);
            fitted(ui, infoX, dy + 22, infoW, MICRO, ItemDetails.line(hovered), MUTED);
            fitted(ui, infoX, dy + 40, infoW, MICRO, moveFromCrate >= 0 ? "Click: take it" : "Click: store it", CYAN);
        }
        String footer = "Left click: whole stack   Right click: one   [F or Esc] close";
        ui.textCentered(w / 2f, y0 + PANEL_H - 22, 1.3f, footer, 0.7f, 0.7f, 0.7f, 1f);

        if ((click || single) && !g.crateSearching()) {
            if (moveFromCrate >= 0) {
                g.transferCrateItemToPlayer(moveFromCrate, single ? 1 : Integer.MAX_VALUE);
            } else if (moveFromPlayer >= 0) {
                g.transferPlayerItemToCrate(moveFromPlayer, single ? 1 : Integer.MAX_VALUE);
            }
        }
    }

    /** Taking from someone else's stores is theft or worse: such a header is a warning. */
    static int ownerTone(String owner) {
        String o = owner.toLowerCase(Locale.ROOT);
        if (o.contains("theft") || o.contains("want them back")) { return AMBER; }
        if (o.startsWith("your") || o.contains("shared with you") || o.contains("left to you")) { return CYAN; }
        return MUTED;
    }

    private int drawGrid(Game g, UiRenderer ui, Inventory inv, float x0, float y0, int cols,
                         double mx, double my) {
        int hover = -1;
        for (int i = 0; i < (inv == null ? 12 : inv.size()); i++) {
            float x = x0 + (i % cols) * SLOT;
            float y = y0 + (i / cols) * SLOT;
            boolean inside = mx >= x && mx < x + SLOT && my >= y && my < y + SLOT;
            if (inside) {
                hover = i;
            }
            drawSlot(ui, inv == null ? null : inv.get(i), x, y, SLOT, inside);
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
            ui.textShadow(x + 6, y + size - 17, 1.1f, String.valueOf(s.count), 1f, 1f, 1f, 1f);
            // Condition as the hotbar shows it: a teal rail for wear, amber segments for freshness.
            float fraction = s.type.hasDurability() ? s.durabilityFrac() : s.type.spoils() ? s.freshnessFrac() : -1;
            if (fraction >= 0) {
                float rx = x + 6, ry = y + size - 7, rw = size - 12;
                rect(ui, rx, ry, rw, 3, NAVY, 1);
                rect(ui, rx, ry, rw * Math.max(0, Math.min(1, fraction)), 3, s.type.hasDurability() ? CYAN : AMBER, 1);
            }
        }
    }

    /** The open container's name: "STORAGE CRATE", or the cabinet, shelving or locker being searched. */
    private static String title(Game g) {
        if (g.crateTitle() != null) { return g.crateTitle(); }
        BlockType t = g.openCratePos == null ? BlockType.CRATE
                : g.world.getBlock(g.openCratePos.x(), g.openCratePos.y(), g.openCratePos.z());
        return t == BlockType.CRATE || !Fixtures.isUrbanContainer(t)
                ? "STORAGE CRATE" : t.displayName.toUpperCase(Locale.ROOT);
    }
}
