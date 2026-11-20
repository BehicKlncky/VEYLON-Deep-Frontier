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

        // The controls reference is one line taller since [T] joined it, so the
        // panel grew with it rather than letting the last line touch the border.
        // A city named its world under the mode, one line more (urban milestone 21); since 0.9.1
        // milestone 06 every world does, with its seed.
        boolean city = g.world.isUrban();
        float extra = 20;
        float pw = 600, ph = 548 + extra + 22; // and a third line of keys since milestone 06
        float x0 = w / 2f - pw / 2f, y0 = h / 2f - ph / 2f;
        ui.panel(x0, y0, pw, ph);

        ui.textCentered(w / 2f, y0 + 16, 2.6f, "VEYLON: DEEP FRONTIER", 1f, 0.95f, 0.8f, 1f);
        ui.textCentered(w / 2f, y0 + 48, 1.4f, "- PAUSED -", 0.8f, 0.8f, 0.8f, 1f);
        boolean creative = g.gameMode() == GameMode.CREATIVE;
        ui.textCentered(w / 2f, y0 + 72, 1.2f, modeLabel(g.gameMode(), g.creativeMarked()),
                creative ? 1f : 0.55f, creative ? 0.8f : 0.88f, creative ? 0.4f : 0.9f, 1f);
        ui.textCentered(w / 2f, y0 + 94, 1.1f, worldLine(city, g.world.seed),
                city ? 0.90f : 0.80f, city ? 0.72f : 0.94f, city ? 0.42f : 0.95f, 1f);

        float oy = y0 + 102 + extra;
        for (String keys : keyLines(creative, g.saves.activeSlot())) {
            ui.textCentered(w / 2f, oy, 1.10f, keys, 1f, 1f, 1f, 1f);
            oy += 22;
        }
        oy += 12;

        for (String line : controls(city, g.world.urban != null && g.world.urban.hasMissions())) {
            ui.textShadow(x0 + 36, oy, 1.3f, line, 0.85f, 0.88f, 0.92f, 1f);
            oy += 22;
        }
    }

    /**
     * The pause menu's own keys, a line each: playing on and the mode, the settings, then saving and
     * leaving (0.9.1 milestone 06).
     */
    public static String[] keyLines(boolean creative, int slot) {
        return new String[]{creative ? "[Esc] Resume   [G] Game mode   [T] World controls" : "[Esc] Resume   [G] Game mode",
                "[O] Graphics   [V] Audio   [C] Controls   [A] Autosave", saveKeys(slot)};
    }

    /**
     * The save keys, naming the slot the live world belongs to, and the ways out (0.9.1 milestones 04
     * and 06): R returns to the title with a save choice, Q quits the game.
     */
    public static String saveKeys(int slot) {
        return "[F5] Save to slot " + slot + "   [F9] Load slot " + slot + "   [R] Return to title   [Q] Quit";
    }

    /**
     * Under the mode: which world this is and its seed, so a player can share it (0.9.1 milestone 06,
     * B01). The names are NEW FRONTIER's working names until the canon's replace them (D-26).
     */
    public static String worldLine(boolean city, long seed) {
        return (city ? "The abandoned city" : "The wilderness") + "  /  seed " + seed;
    }

    private static final String[] WILDERNESS = {
            "WASD          Move              Space  Jump / swim up",
            "Left Shift    Sprint            Left Ctrl  Crouch (stealth, read tracks)",
            "Space twice   Creative: start or stop flying",
            "(flying)      Space rise   Left Ctrl descend   Left Shift faster",
            "LMB           Mine / attack     RMB  Place / eat / drink / treat / equip",
            "F             Interact: talk, harvest, carcass, campfire, rack,",
            "              collector, bedroll (sleep), beacon, fill waterskin",
            "E             Inventory + gear  C   Crafting (Q/R station tabs)",
            "Creative: E catalog, instant LMB, free RMB, middle mouse picks",
            "Creative: T sets the time, weather and natural spawning",
            "1-9 / scroll  Hotbar            Tab  Simulation panel",
            "M             Map (POIs)        F3   Debug overlay",
            "P             Pause simulation  F5/F9  Save / Load",
            "",
            "Survival: cook meat & boil water at campfires, dry food on racks,",
            "bandage wounds, splint sprains, sleep warm & dry, watch your weight.",
            "Explore for blueprints. Befriend the camp. Repair the distress beacon.",
    };

    /**
     * The controls reference and the goal lines: the wilderness's own for its worlds, unchanged, and
     * in a city what its keys do there and what its survival is about (urban milestone 21). The
     * relay is named only where the city has one.
     */
    static String[] controls(boolean city, boolean relay) {
        if (!city) {
            return WILDERNESS.clone();
        }
        return new String[]{
                "WASD          Move              Space  Jump / swim up",
                "Left Shift    Sprint            Left Ctrl  Crouch (quieter, bars doors)",
                "Space twice   Creative: start or stop flying",
                "(flying)      Space rise   Left Ctrl descend   Left Shift faster",
                "LMB           Attack / break / force a lock (loud)",
                "Crouch + LMB  Take furniture apart for salvage (loud)",
                "F             Open doors, search, talk, rest, switch devices",
                "RMB           Eat / drink / treat / place / use on a device",
                "E             Inventory + gear  C   Crafting (workbench nearby)",
                "Creative: E catalog, instant LMB, free RMB, middle mouse picks",
                "Creative: T sets the time, weather and natural spawning",
                "1-9 / scroll  Hotbar            M   Map, journal, floor sketch",
                "P             Pause simulation  F5/F9  Save / Load",
                "",
                "City: search buildings and carry supplies home; noise draws the",
                "infected. Crouch and F on a bed claims a refuge: bar its doors.",
                relay ? "Help survivors for work, and restore the city's old radio relay."
                        : "Help the survivors you find; they remember what you do.",
        };
    }
}
