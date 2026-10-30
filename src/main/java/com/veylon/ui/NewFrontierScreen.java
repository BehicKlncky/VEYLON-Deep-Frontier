package com.veylon.ui;

import com.veylon.Game;
import com.veylon.engine.Input;
import com.veylon.engine.UiRenderer;
import com.veylon.entity.GameMode;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Worldless mode choice between the title and loading (R2). Survival is the
 * default on every opening, so an abandoned Creative choice never carries into
 * a later frontier. Keyboard decisions live in {@link #handleKeys}, which never
 * touches the renderer, so routing stays testable without a GL context.
 */
public final class NewFrontierScreen {

    /** Result handed to the frontend controller. */
    public enum Action { NONE, START, BACK }

    /** D7: both modes share the single save slot. */
    public static final String REPLACE_NOTICE =
            "Saving this new frontier will replace your current save.";

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
    /**
     * Shown while this session's new worlds are the abandoned city (the development selector): what
     * the world is, and that the ordinary default is unchanged.
     */
    public static final String CITY_NOTICE = "THE ABANDONED CITY  /  development build";
    public static final String CITY_DEFAULT_NOTE = "Ordinary new frontiers stay in the wilderness until the city is finished.";
    private static final String MARK_NOTE =
            "A Creative world stays marked as Creative, even after switching to Survival.";
    private static final float CARD_MAX_W = 320;
    private static final float CARD_H = 132;
    private static final float CARD_GAP = 24;
    private static final float BUTTON_W = 190;
    private static final float BUTTON_H = 36;

    private int selected;
    private boolean saveExists;
    private boolean city;

    /** Resets the choice to Survival; {@code saveExists} decides the replace notice. */
    public void open(boolean saveExists) {
        open(saveExists, false);
    }

    /**
     * Resets the choice to Survival; {@code saveExists} decides the replace notice and {@code city}
     * whether this session's new worlds are the abandoned city.
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

    /** Whether the screen describes the abandoned city rather than the wilderness. */
    public boolean showsCity() {
        return city;
    }

    /** The two lines a mode's card shows, for the world this screen creates. */
    public String[] description(GameMode mode) {
        int index = mode == GameMode.CREATIVE ? 1 : 0;
        return (city ? CITY_DESCRIPTIONS : DESCRIPTIONS)[index].clone();
    }

    /** R2 keys: S/C, arrows or Tab choose; Enter starts; Escape returns to the title. */
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
        ui.rect(w * 0.18f, h * 0.24f, w * 0.64f, 2, 0.18f, 0.72f, 0.76f, 0.34f);
        ui.textCentered(w / 2f, h * 0.12f, 2.6f, "NEW FRONTIER", 0.80f, 0.94f, 0.95f, 1f);
        ui.textCentered(w / 2f, h * 0.12f + 40, 1.2f, "Choose how this world plays.",
                0.46f, 0.62f, 0.66f, 1f);
        if (city) {
            ui.textCentered(w / 2f, h * 0.12f + 64, 1.2f, CITY_NOTICE, 0.90f, 0.72f, 0.42f, 1f);
        }

        double mx = g.input.cursorX(), my = g.input.cursorY();
        boolean click = g.input.wasMousePressed(GLFW_MOUSE_BUTTON_LEFT);
        float cardW = Math.min(CARD_MAX_W, (w - 96 - CARD_GAP) / 2f);
        float left = w / 2f - cardW - CARD_GAP / 2f;
        float cardY = h * 0.32f;
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
        if (city) {
            noteY += 26;
            ui.textCentered(w / 2f, noteY, 1.1f, CITY_DEFAULT_NOTE, 0.55f, 0.66f, 0.70f, 1f);
        }
        if (saveExists) {
            ui.textCentered(w / 2f, noteY + 26, 1.2f, REPLACE_NOTICE, 1f, 0.65f, 0.42f, 1f);
        }

        float buttonY = noteY + 66;
        float startX = w / 2f - BUTTON_W - 8, backX = w / 2f + 8;
        boolean startHover = inside(mx, my, startX, buttonY, BUTTON_W, BUTTON_H);
        boolean backHover = inside(mx, my, backX, buttonY, BUTTON_W, BUTTON_H);
        button(ui, startX, buttonY, "START [Enter]", startHover, true);
        button(ui, backX, buttonY, "BACK [Esc]", backHover, false);
        ui.textCentered(w / 2f, h - 34, 1.05f,
                "S / C or arrows choose     Enter starts     Esc returns",
                0.42f, 0.50f, 0.56f, 1f);

        if (click && startHover) {
            return Action.START;
        }
        if (click && backHover) {
            return Action.BACK;
        }
        return handleKeys(g.input);
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
