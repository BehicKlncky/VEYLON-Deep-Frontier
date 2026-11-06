package com.veylon.ui;

import com.veylon.Game;
import com.veylon.engine.Input;
import com.veylon.engine.UiRenderer;
import com.veylon.entity.GameMode;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Worldless choice of the world and its mode between the title and loading (R2). The abandoned
 * city is the new world (urban milestone 26); the wilderness stays on offer beside it, labelled as
 * the legacy world. Every opening starts from the session's world and from Survival, so an abandoned
 * choice never carries into a later frontier. Keyboard decisions live in {@link #handleKeys}, which
 * never touches the renderer, so routing stays testable without a GL context.
 */
public final class NewFrontierScreen {

    /** Result handed to the frontend controller. */
    public enum Action { NONE, START, BACK }

    /** D7: every world and both modes share the single save slot. */
    public static final String REPLACE_NOTICE =
            "Saving this new frontier will replace your current save.";
    /** The same notice for a new city, naming the kind of world that would take the slot (P-05). */
    public static final String CITY_REPLACE_NOTICE =
            "Saving this new city will replace your current save.";
    /** The two worlds, the city first. */
    public static final String CITY_TITLE = "THE ABANDONED CITY";
    public static final String WILDERNESS_TITLE = "THE WILDERNESS  (legacy)";
    /** One line under the cards saying what the chosen world is. */
    public static final String CITY_NOTE =
            "Scavenge its buildings, hold a refuge and bring the old radio relay back.";
    public static final String WILDERNESS_NOTE =
            "The world of releases up to 0.8.1: forests, caves, settlements and the beacon.";
    /** The key line at the foot of the screen. */
    public static final String KEYS =
            "S / C or arrows choose the mode     W the world     Enter starts     Esc returns";

    private static final GameMode[] MODES = {GameMode.SURVIVAL, GameMode.CREATIVE};
    private static final String[] TITLES = {"SURVIVAL", "CREATIVE"};
    private static final String[][] DESCRIPTIONS = {
            {"Hunger, weather, wounds and wildlife.", "The frontier fights back."},
            {"Fly, with no damage or needs.", "Wildlife and settlers ignore you."}
    };
    /** Urban milestone 21: what each mode means in the abandoned city. */
    private static final String[][] CITY_DESCRIPTIONS = {
            {"Search buildings for food, water and medicine.", "Hold a refuge. The infected hunt by sound."},
            {"Fly, with no damage or needs.", "The infected, raiders and survivors ignore you."}
    };
    private static final String MARK_NOTE =
            "A Creative world stays marked as Creative, even after switching to Survival.";
    private static final float CARD_MAX_W = 320;
    private static final float CARD_H = 132;
    private static final float CARD_GAP = 24;
    private static final float TAB_H = 30;
    private static final float BUTTON_W = 190;
    private static final float BUTTON_H = 36;

    private int selected;
    private boolean saveExists;
    private boolean city = true;

    /** Opens on the city and Survival; {@code saveExists} decides the replace notice. */
    public void open(boolean saveExists) {
        open(saveExists, true);
    }

    /**
     * Resets the choice to Survival; {@code saveExists} decides the replace notice and {@code city}
     * which world the screen opens on: the session's own, the city unless a QA session chose the
     * wilderness.
     */
    public void open(boolean saveExists, boolean city) {
        selected = 0;
        this.saveExists = saveExists;
        this.city = city;
    }

    public GameMode selectedMode() {
        return MODES[selected];
    }

    public boolean showsReplaceNotice() {
        return saveExists;
    }

    /** Whether the chosen world is the abandoned city rather than the wilderness. */
    public boolean showsCity() {
        return city;
    }

    /** The shared slot's notice, naming the kind of world chosen. */
    public String replaceNotice() {
        return city ? CITY_REPLACE_NOTICE : REPLACE_NOTICE;
    }

    /** What the chosen world is, in one line. */
    public String worldNote() {
        return city ? CITY_NOTE : WILDERNESS_NOTE;
    }

    /** The two lines a mode's card shows, for the world this screen creates. */
    public String[] description(GameMode mode) {
        int index = mode == GameMode.CREATIVE ? 1 : 0;
        return (city ? CITY_DESCRIPTIONS : DESCRIPTIONS)[index].clone();
    }

    /**
     * R2 keys: S/C, arrows or Tab choose the mode; W switches between the city and the wilderness;
     * Enter starts; Escape returns to the title.
     */
    public Action handleKeys(Input input) {
        if (input.wasKeyPressed(GLFW_KEY_S) || input.wasKeyPressed(GLFW_KEY_LEFT)) {
            selected = 0;
        }
        if (input.wasKeyPressed(GLFW_KEY_C) || input.wasKeyPressed(GLFW_KEY_RIGHT)) {
            selected = 1;
        }
        if (input.wasKeyPressed(GLFW_KEY_TAB) || input.wasKeyPressed(GLFW_KEY_UP)
                || input.wasKeyPressed(GLFW_KEY_DOWN)) {
            selected = 1 - selected;
        }
        if (input.wasKeyPressed(GLFW_KEY_W)) {
            city = !city;
        }
        if (input.wasKeyPressed(GLFW_KEY_ESCAPE)) {
            return Action.BACK;
        }
        if (input.wasKeyPressed(GLFW_KEY_ENTER)) {
            return Action.START;
        }
        return Action.NONE;
    }

    /** Draws the choice in the title's visual language and returns the mouse or key action. */
    public Action update(Game g) {
        UiRenderer ui = g.ui;
        int w = ui.screenW(), h = ui.screenH();
        ui.rect(0, 0, w, h, 0.015f, 0.022f, 0.035f, 1f);
        ui.textCentered(w / 2f, h * 0.12f, 2.6f, "NEW FRONTIER", 0.80f, 0.94f, 0.95f, 1f);
        ui.textCentered(w / 2f, h * 0.12f + 40, 1.2f, "Choose where and how this world plays.",
                0.46f, 0.62f, 0.66f, 1f);

        double mx = g.input.cursorX(), my = g.input.cursorY();
        boolean click = g.input.wasMousePressed(GLFW_MOUSE_BUTTON_LEFT);
        float cardW = Math.min(CARD_MAX_W, (w - 96 - CARD_GAP) / 2f);
        float left = w / 2f - cardW - CARD_GAP / 2f;
        float tabY = h * 0.12f + 64;
        for (int i = 0; i < 2; i++) {
            boolean cityTab = i == 0;
            float x = left + i * (cardW + CARD_GAP);
            boolean hover = inside(mx, my, x, tabY, cardW, TAB_H);
            if (hover && click) {
                city = cityTab;
            }
            drawTab(ui, x, tabY, cardW, cityTab, city == cityTab, hover);
        }

        float cardY = Math.max(h * 0.32f, tabY + TAB_H + 10);
        // The title's rule now parts the world from its mode.
        ui.rect(w * 0.18f, (tabY + TAB_H + cardY) / 2f - 1, w * 0.64f, 2, 0.18f, 0.72f, 0.76f, 0.34f);
        for (int i = 0; i < MODES.length; i++) {
            float x = left + i * (cardW + CARD_GAP);
            boolean hover = inside(mx, my, x, cardY, cardW, CARD_H);
            if (hover && click) {
                selected = i;
            }
            drawCard(ui, x, cardY, cardW, i, description(MODES[i]), i == selected, hover);
        }

        float noteY = cardY + CARD_H + 22;
        ui.textCentered(w / 2f, noteY, 1.1f, MARK_NOTE, 0.55f, 0.66f, 0.70f, 1f);
        noteY += 26;
        ui.textCentered(w / 2f, noteY, 1.1f, worldNote(), 0.55f, 0.66f, 0.70f, 1f);
        if (saveExists) {
            ui.textCentered(w / 2f, noteY + 26, 1.2f, replaceNotice(), 1f, 0.65f, 0.42f, 1f);
        }

        float buttonY = noteY + 66;
        float startX = w / 2f - BUTTON_W - 8, backX = w / 2f + 8;
        boolean startHover = inside(mx, my, startX, buttonY, BUTTON_W, BUTTON_H);
        boolean backHover = inside(mx, my, backX, buttonY, BUTTON_W, BUTTON_H);
        button(ui, startX, buttonY, "START [Enter]", startHover, true);
        button(ui, backX, buttonY, "BACK [Esc]", backHover, false);
        ui.textCentered(w / 2f, h - 34, 1.05f, KEYS, 0.42f, 0.50f, 0.56f, 1f);

        if (click && startHover) {
            return Action.START;
        }
        if (click && backHover) {
            return Action.BACK;
        }
        return handleKeys(g.input);
    }

    /** A world's tab: the city in the amber of its signs, the wilderness in the title's teal. */
    private static void drawTab(UiRenderer ui, float x, float y, float w, boolean cityTab,
                                boolean active, boolean hover) {
        ui.rect(x, y, w, TAB_H, active ? 0.10f : 0.035f, active ? 0.16f : 0.05f,
                active ? 0.20f : 0.07f, 0.96f);
        ui.rectOutline(x, y, w, TAB_H, active ? 2 : 1,
                active ? 0.30f : 0.20f, active || hover ? 0.82f : 0.34f,
                active || hover ? 0.84f : 0.38f, 0.9f);
        float dim = active ? 1f : 0.62f;
        ui.textCentered(x + w / 2f, y + 7, 1.15f, cityTab ? CITY_TITLE : WILDERNESS_TITLE,
                (cityTab ? 0.90f : 0.80f) * dim, (cityTab ? 0.72f : 0.94f) * dim,
                (cityTab ? 0.42f : 0.95f) * dim, 1f);
    }

    private static void drawCard(UiRenderer ui, float x, float y, float w, int index, String[] lines,
                                 boolean active, boolean hover) {
        ui.rect(x, y, w, CARD_H, active ? 0.10f : 0.045f,
                active ? 0.28f : 0.07f, active ? 0.30f : 0.10f, 0.96f);
        ui.rectOutline(x, y, w, CARD_H, active ? 2 : 1,
                active ? 0.30f : 0.20f, active || hover ? 0.82f : 0.34f,
                active || hover ? 0.84f : 0.38f, 0.9f);
        boolean creative = MODES[index] == GameMode.CREATIVE;
        ui.textCentered(x + w / 2f, y + 18, 1.8f, TITLES[index],
                creative ? 1f : 0.80f, creative ? 0.8f : 0.94f, creative ? 0.4f : 0.95f, 1f);
        for (int line = 0; line < lines.length; line++) {
            ui.textCentered(x + w / 2f, y + 62 + line * 24, 1.15f, lines[line],
                    active ? 0.90f : 0.62f, active ? 0.96f : 0.70f, active ? 0.97f : 0.74f, 1f);
        }
    }

    private static void button(UiRenderer ui, float x, float y, String text,
                               boolean hover, boolean primary) {
        ui.rect(x, y, BUTTON_W, BUTTON_H, 0.08f, primary ? (hover ? 0.40f : 0.28f) : 0.12f,
                primary ? 0.30f : 0.15f, 0.98f);
        ui.rectOutline(x, y, BUTTON_W, BUTTON_H, 1, 0.28f, hover ? 0.92f : 0.56f,
                hover ? 0.94f : 0.60f, 0.9f);
        ui.textCentered(x + BUTTON_W / 2, y + 10, 1.28f, text, 0.90f, 0.96f, 0.97f, 1);
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
