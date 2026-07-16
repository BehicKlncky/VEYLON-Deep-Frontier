package com.veylon.ui;

import com.veylon.Game;
import com.veylon.engine.UiRenderer;
import com.veylon.item.CraftingSystem;
import com.veylon.item.Recipe;
import com.veylon.item.Station;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Recipe browser with station tabs ([Q]/[R] to cycle), blueprint locks and a
 * scrolling list. Recipes gate on nearby stations; green tags mean in range.
 */
public class CraftingScreen {

    private static final int VISIBLE_ROWS = 13;

    private int selected = 0;
    private int tab = 0; // 0 = ALL, then Station ordinal + 1
    private int scroll = 0;

    public void update(Game g) {
        UiRenderer ui = g.ui;
        int w = ui.screenW(), h = ui.screenH();
        Set<Station> nearby = g.nearbyStations();

        // Tab cycling.
        Station[] stations = Station.values();
        if (g.input.wasKeyPressed(GLFW_KEY_Q)) {
            tab = (tab + stations.length) % (stations.length + 1);
            selected = 0;
        }
        if (g.input.wasKeyPressed(GLFW_KEY_R)) {
            tab = (tab + 1) % (stations.length + 1);
            selected = 0;
        }

        List<Recipe> recipes = new ArrayList<>();
        for (Recipe r : CraftingSystem.RECIPES) {
            if (tab == 0 || r.station == stations[tab - 1]) {
                recipes.add(r);
            }
        }
        if (recipes.isEmpty()) {
            tab = 0;
            recipes.addAll(CraftingSystem.RECIPES);
        }

        float pw = 700, ph = 500;
        float x0 = w / 2f - pw / 2f, y0 = h / 2f - ph / 2f;
        ui.panel(x0, y0, pw, ph);
        ui.textCentered(w / 2f, y0 + 12, 2f, "CRAFTING", 1f, 1f, 1f, 1f);

        // Tab row.
        float tx = x0 + 14;
        String allLabel = "[ALL]";
        drawTab(ui, tx, y0 + 36, allLabel, tab == 0, true);
        tx += ui.textWidth(allLabel, 1.25f) + 14;
        for (int i = 0; i < stations.length; i++) {
            Station st = stations[i];
            String label = st.displayName;
            drawTab(ui, tx, y0 + 36, label, tab == i + 1, nearby.contains(st));
            tx += ui.textWidth(label, 1.25f) + 14;
        }
        ui.textShadow(x0 + 14, y0 + 54, 1.15f, "[Q]/[R] cycle station tabs - green = in range",
                0.6f, 0.6f, 0.65f, 1f);

        // Keyboard navigation.
        if (g.input.wasKeyPressed(GLFW_KEY_W) || g.input.wasKeyPressed(GLFW_KEY_UP)) {
            selected = (selected + recipes.size() - 1) % recipes.size();
        }
        if (g.input.wasKeyPressed(GLFW_KEY_S) || g.input.wasKeyPressed(GLFW_KEY_DOWN)) {
            selected = (selected + 1) % recipes.size();
        }
        selected = Math.min(selected, recipes.size() - 1);
        int scrollInput = (int) g.input.scrollDelta();
        if (scrollInput != 0) {
            scroll -= scrollInput;
        }
        // Keep the selection visible.
        if (selected < scroll) {
            scroll = selected;
        }
        if (selected >= scroll + VISIBLE_ROWS) {
            scroll = selected - VISIBLE_ROWS + 1;
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, recipes.size() - VISIBLE_ROWS)));

        double mx = g.input.cursorX(), my = g.input.cursorY();
        float listX = x0 + 14, listY = y0 + 78;
        float rowH = 27;

        for (int row = 0; row < Math.min(VISIBLE_ROWS, recipes.size()); row++) {
            int i = scroll + row;
            if (i >= recipes.size()) {
                break;
            }
            Recipe r = recipes.get(i);
            float y = listY + row * rowH;
            boolean inside = mx >= listX && mx < listX + 330 && my >= y && my < y + rowH;
            if (inside) {
                selected = i;
            }
            boolean known = CraftingSystem.knowsBlueprint(r, g.player.blueprints);
            boolean craftable = CraftingSystem.canCraft(g.player.inventory, r, nearby,
                    g.player.blueprints);
            ui.button(listX - 4, y - 2, 338, rowH - 2, inside, i == selected, known);
            String name = known ? r.name : "??? (blueprint needed)";
            float cr = !known ? 0.55f : (craftable ? 0.55f : 0.55f);
            float cg = !known ? 0.5f : (craftable ? 1f : 0.45f);
            float cb = !known ? 0.65f : (craftable ? 0.55f : 0.45f);
            float nameX = listX;
            if (r.result != null) {
                ui.itemIcon(r.result, listX, y + 1, 20, known ? 1f : 0.35f);
                nameX += 24;
            }
            ui.textShadow(nameX, y + 4, 1.5f, name, cr, cg, cb, 1f);
            if (r.station != Station.HAND) {
                boolean inRange = nearby.contains(r.station);
                ui.text(listX + 240, y + 6, 1.1f, "[" + r.station.displayName + "]",
                        inRange ? 0.45f : 0.8f, inRange ? 0.85f : 0.6f, inRange ? 0.4f : 0.3f, 1f);
            }
        }
        if (recipes.size() > VISIBLE_ROWS) {
            ui.textShadow(listX, listY + VISIBLE_ROWS * rowH + 2, 1.1f,
                    "scroll: " + (scroll + 1) + "-" + Math.min(recipes.size(), scroll + VISIBLE_ROWS)
                            + " / " + recipes.size(), 0.55f, 0.55f, 0.6f, 1f);
        }

        // Details panel for selected recipe.
        Recipe r = recipes.get(selected);
        boolean known = CraftingSystem.knowsBlueprint(r, g.player.blueprints);
        float dx = x0 + 380, dy = y0 + 82;
        ui.textShadow(dx, dy, 1.7f, known ? r.name : "Locked Blueprint", 1f, 1f, 0.9f, 1f);
        dy += 26;
        if (r.result != null) {
            ui.itemIcon(r.result, dx, dy - 3, 24, known ? 1f : 0.4f);
            ui.textShadow(dx + 30, dy, 1.3f, "Makes: " + r.resultCount + " x " + r.result.displayName,
                    0.85f, 0.85f, 0.85f, 1f);
        } else {
            String next = CraftingSystem.nextBlueprint(g.player.blueprints);
            ui.textShadow(dx, dy, 1.3f, next != null
                            ? "Decodes: " + CraftingSystem.blueprintTitle(next) + " blueprint"
                            : "All blueprints decoded!",
                    0.6f, 0.8f, 1f, 1f);
        }
        dy += 22;
        ui.textShadow(dx, dy, 1.3f, "Station: " + r.station.displayName
                        + (r.station != Station.HAND
                        ? (nearby.contains(r.station) ? "  (in range)" : "  (NOT NEARBY)") : ""),
                0.85f, 0.85f, 0.85f, 1f);
        dy += 22;
        if (r.blueprint != null) {
            ui.textShadow(dx, dy, 1.3f, known ? "Blueprint: decoded"
                            : "Blueprint: decode fragments at a Map Table",
                    known ? 0.5f : 0.85f, known ? 0.9f : 0.7f, 0.5f, 1f);
            dy += 22;
        }
        dy += 4;
        ui.textShadow(dx, dy, 1.4f, "Ingredients:", 0.95f, 0.95f, 0.95f, 1f);
        dy += 20;
        for (var e : r.ingredients.entrySet()) {
            int have = g.player.inventory.count(e.getKey());
            boolean ok = have >= e.getValue();
            ui.itemIcon(e.getKey(), dx, dy - 1, 16);
            ui.textShadow(dx + 21, dy, 1.3f,
                    e.getKey().displayName + "  " + have + "/" + e.getValue() + (ok ? "" : "  MISSING"),
                    ok ? 0.6f : 1f, ok ? 1f : 0.4f, ok ? 0.6f : 0.4f, 1f);
            dy += 19;
        }

        // Craft button.
        boolean canCraft = CraftingSystem.canCraft(g.player.inventory, r, nearby, g.player.blueprints);
        float btnX = dx, btnY = y0 + ph - 70, btnW = 220, btnH = 36;
        boolean btnHover = mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + btnH;
        ui.button(btnX, btnY, btnW, btnH, btnHover, false, canCraft);
        String btnLabel = canCraft ? "CRAFT [Enter]"
                : (!known ? "BLUEPRINT LOCKED"
                : (r.station != Station.HAND && !nearby.contains(r.station)
                ? "NEEDS " + r.station.displayName.toUpperCase() : "MISSING PARTS"));
        ui.textCentered(btnX + btnW / 2, btnY + 10, 1.5f, btnLabel,
                canCraft ? 1f : 0.6f, canCraft ? 1f : 0.5f, canCraft ? 1f : 0.5f, 1f);

        boolean doCraft = (g.input.wasMousePressed(GLFW_MOUSE_BUTTON_LEFT) && btnHover)
                || g.input.wasKeyPressed(GLFW_KEY_ENTER);
        if (doCraft && canCraft) {
            String result = CraftingSystem.craft(g.player.inventory, r, nearby, g.player.blueprints);
            if (result != null) {
                g.audio.playCraft();
                if (r.result == null) {
                    g.log("Blueprint decoded: " + CraftingSystem.blueprintTitle(result)
                            + "! New recipe available.");
                } else {
                    g.log("Crafted " + r.resultCount + " x " + r.result.displayName);
                }
            }
        }

        ui.textCentered(w / 2f, y0 + ph - 24, 1.2f,
                "[C] close   [W/S] select   [Q/R] station tab   [Enter] craft",
                0.7f, 0.7f, 0.7f, 1f);
    }

    private void drawTab(UiRenderer ui, float x, float y, String label, boolean active, boolean inRange) {
        float r = active ? 1f : (inRange ? 0.5f : 0.55f);
        float g = active ? 0.95f : (inRange ? 0.85f : 0.55f);
        float b = active ? 0.6f : (inRange ? 0.45f : 0.6f);
        if (active) {
            ui.rect(x - 4, y - 2, ui.textWidth(label, 1.25f) + 8, 16, 0.22f, 0.22f, 0.3f, 0.9f);
        }
        ui.textShadow(x, y, 1.25f, label, r, g, b, 1f);
    }
}
