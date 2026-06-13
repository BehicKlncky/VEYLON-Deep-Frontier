package com.veylon.ui;

import com.veylon.Game;
import com.veylon.engine.UiRenderer;

/** Escape menu with controls reference. */
public class PauseMenu {

    public void update(Game g) {
        UiRenderer ui = g.ui;
        int w = ui.screenW(), h = ui.screenH();
        ui.rect(0, 0, w, h, 0, 0, 0, 0.55f);

        float pw = 600, ph = 520;
        float x0 = w / 2f - pw / 2f, y0 = h / 2f - ph / 2f;
        ui.rect(x0, y0, pw, ph, 0.08f, 0.08f, 0.12f, 0.95f);
        ui.rectOutline(x0, y0, pw, ph, 2, 0.6f, 0.6f, 0.7f, 0.9f);

        ui.textCentered(w / 2f, y0 + 16, 2.6f, "VEYLON: DEEP FRONTIER", 1f, 0.95f, 0.8f, 1f);
        ui.textCentered(w / 2f, y0 + 48, 1.4f, "- PAUSED -", 0.8f, 0.8f, 0.8f, 1f);

        float oy = y0 + 78;
        ui.textCentered(w / 2f, oy, 1.6f, "[Esc] Resume      [F5] Save      [F9] Load      [Q] Quit",
                1f, 1f, 1f, 1f);
        oy += 36;

        String[] controls = {
                "WASD          Move              Space  Jump / swim up",
                "Left Shift    Sprint            Left Ctrl  Crouch (stealth, read tracks)",
                "LMB           Mine / attack     RMB  Place / eat / drink / treat / equip",
                "F             Interact: talk, harvest, carcass, campfire, rack,",
                "              collector, bedroll (sleep), beacon, fill waterskin",
                "E             Inventory + gear  C   Crafting (Q/R station tabs)",
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
