package com.veylon.ui;

import com.veylon.Game;
import com.veylon.engine.UiRenderer;
import com.veylon.entity.GameMode;

/** Escape menu with the world's game mode and a controls reference. */
public class PauseMenu {

    private static final String SURVIVAL_LABEL = "SURVIVAL";
    private static final String SURVIVAL_MARKED_LABEL = "SURVIVAL  /  Creative world";
    private static final String CREATIVE_LABEL = "CREATIVE  /  Creative world";

    /** R6: the mode plus the permanent mark. A Creative world is always marked. */
    public static String modeLabel(GameMode mode, boolean marked) {
        if (mode == GameMode.CREATIVE) {
            return CREATIVE_LABEL;
        }
        return marked ? SURVIVAL_MARKED_LABEL : SURVIVAL_LABEL;
    }

    public void update(Game g) {
        UiRenderer ui = g.ui;
        int w = ui.screenW(), h = ui.screenH();
        ui.rect(0, 0, w, h, 0, 0, 0, 0.55f);

        float pw = 600, ph = 520;
        float x0 = w / 2f - pw / 2f, y0 = h / 2f - ph / 2f;
        ui.panel(x0, y0, pw, ph);

        ui.textCentered(w / 2f, y0 + 16, 2.6f, "VEYLON: DEEP FRONTIER", 1f, 0.95f, 0.8f, 1f);
        ui.textCentered(w / 2f, y0 + 48, 1.4f, "- PAUSED -", 0.8f, 0.8f, 0.8f, 1f);
        boolean creative = g.gameMode() == GameMode.CREATIVE;
        ui.textCentered(w / 2f, y0 + 72, 1.2f, modeLabel(g.gameMode(), g.creativeMarked()),
                creative ? 1f : 0.55f, creative ? 0.8f : 0.88f, creative ? 0.4f : 0.9f, 1f);

        float oy = y0 + 102;
        ui.textCentered(w / 2f, oy, 1.10f,
                "[Esc] Resume   [G] Game mode   [O] Graphics   [V] Audio",
                1f, 1f, 1f, 1f);
        oy += 22;
        ui.textCentered(w / 2f, oy, 1.10f, "[F5] Save   [F9] Load   [Q] Quit", 1f, 1f, 1f, 1f);
        oy += 34;

        String[] controls = {
                "WASD          Move              Space  Jump / swim up",
                "Left Shift    Sprint            Left Ctrl  Crouch (stealth, read tracks)",
                "Space twice   Creative: start or stop flying",
                "(flying)      Space rise   Left Ctrl descend   Left Shift faster",
                "LMB           Mine / attack     RMB  Place / eat / drink / treat / equip",
                "F             Interact: talk, harvest, carcass, campfire, rack,",
                "              collector, bedroll (sleep), beacon, fill waterskin",
                "E             Inventory + gear  C   Crafting (Q/R station tabs)",
                "Creative: E catalog, instant LMB, free RMB, middle mouse picks",
                "1-9 / scroll  Hotbar            Tab  Simulation panel",
                "M             Map (POIs)        F3   Debug overlay",
                "P             Pause simulation  F5/F9  Save / Load",
                "",
                "Survival: cook meat & boil water at campfires, dry food on racks,",
                "bandage wounds, splint sprains, sleep warm & dry, watch your weight.",
                "Explore for blueprints. Befriend the camp. Repair the distress beacon.",
        };
        for (String line : controls) {
            ui.textShadow(x0 + 36, oy, 1.3f, line, 0.85f, 0.88f, 0.92f, 1f);
            oy += 22;
        }
    }
}
