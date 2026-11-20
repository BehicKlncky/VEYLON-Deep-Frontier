package com.veylon.ui;

import com.veylon.Game;
import com.veylon.engine.UiRenderer;
import com.veylon.save.AutosaveSettings;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Whether the game saves by itself and how often (0.9.1 milestone 05), from the title and the
 * pause menu. It edits the live settings, which the autosave reads every frame; the caller writes
 * them to disk on APPLY, and BACK restores what they were when the screen opened.
 */
public final class AutosaveOptionsScreen {

    /** Result returned to the title or pause controller. */
    public enum Action { NONE, APPLY, CANCEL }

    static final String[] LABELS = {"Autosave", "Every"};
    static final String SUBTITLE = "Saves into the world's own slot when it is safe";
    static final String WAITS = "It waits while you sleep, fall, burn or are hurt or hunted";
    static final String KEYS = "Arrow keys / click change     Back restores the previous choice";

    private int selected;
    private AutosaveSettings original;

    /** Begins a transaction against the live settings. */
    public void open(AutosaveSettings settings) {
        original = new AutosaveSettings(settings);
        selected = 0;
    }

    /** Draws and edits the settings; the caller persists only an APPLY result. */
    public Action update(Game game) {
        AutosaveSettings settings = game.saves.autosave().settings();
        if (original == null) open(settings);
        UiRenderer ui = game.ui;
        int width = ui.screenW(), height = ui.screenH();
        float pw = Math.min(680, width - 36), ph = Math.min(330, height - 30);
        float x0 = (width - pw) / 2, y0 = (height - ph) / 2;
        ui.rect(0, 0, width, height, 0, 0, 0, 0.62f);
        ui.panel(x0, y0, pw, ph);
        ui.textCentered(width / 2f, y0 + 18, 2.15f, "AUTOSAVE", 0.78f, 0.96f, 0.98f, 1);
        ui.textCentered(width / 2f, y0 + 48, 1.08f, SUBTITLE, 0.50f, 0.60f, 0.64f, 1);

        if (game.input.wasKeyPressed(GLFW_KEY_UP) || game.input.wasKeyPressed(GLFW_KEY_W)) selected = Math.floorMod(selected - 1, LABELS.length);
        if (game.input.wasKeyPressed(GLFW_KEY_DOWN) || game.input.wasKeyPressed(GLFW_KEY_S)) selected = (selected + 1) % LABELS.length;
        int delta = 0;
        if (game.input.wasKeyPressed(GLFW_KEY_LEFT) || game.input.wasKeyPressed(GLFW_KEY_A)) delta = -1;
        if (game.input.wasKeyPressed(GLFW_KEY_RIGHT) || game.input.wasKeyPressed(GLFW_KEY_D)
                || game.input.wasKeyPressed(GLFW_KEY_ENTER) || game.input.wasKeyPressed(GLFW_KEY_SPACE)) delta = 1;
        if (delta != 0) { adjust(settings, selected, delta); game.audio.playClick(); }

        double mx = game.input.cursorX(), my = game.input.cursorY();
        boolean clicked = game.input.wasMousePressed(GLFW_MOUSE_BUTTON_LEFT);
        float rowX = x0 + 42, rowW = pw - 84;
        for (int i = 0; i < LABELS.length; i++) {
            float y = y0 + 84 + i * 44;
            boolean hover = inside(mx, my, rowX, y, rowW, 36);
            if (hover) selected = i;
            if (hover && clicked) { adjust(settings, i, 1); game.audio.playClick(); }
            boolean live = i == 0 || settings.enabled;
            if (i == selected) {
                ui.rect(rowX, y, rowW, 36, 0.08f, 0.22f, 0.24f, 0.88f);
                ui.rect(rowX, y, 3, 36, 0.28f, 0.84f, 0.87f, 1);
            }
            float shade = live ? 1f : 0.55f;
            ui.textShadow(rowX + 14, y + 11, 1.28f, LABELS[i], 0.90f * shade, 0.97f * shade, 0.98f * shade, 1);
            String value = valueOf(settings, i);
            ui.textShadow(rowX + rowW - ui.textWidth(value, 1.28f) - 12, y + 11, 1.28f, value,
                    0.96f * shade, 0.78f * shade, 0.42f * shade, 1);
        }

        ui.textCentered(width / 2f, y0 + ph - 118, 1.05f, WAITS, 0.58f, 0.70f, 0.73f, 1);
        ui.textCentered(width / 2f, y0 + ph - 94, 1.05f, KEYS, 0.58f, 0.70f, 0.73f, 1);
        float buttonY = y0 + ph - 58, buttonW = 190, applyX = width / 2f - buttonW - 8, cancelX = width / 2f + 8;
        boolean apply = inside(mx, my, applyX, buttonY, buttonW, 36), cancel = inside(mx, my, cancelX, buttonY, buttonW, 36);
        button(ui, applyX, buttonY, buttonW, "APPLY [F5]", apply, true);
        button(ui, cancelX, buttonY, buttonW, "BACK [Esc]", cancel, false);
        if (game.input.wasKeyPressed(GLFW_KEY_F5) || clicked && apply) { original = null; return Action.APPLY; }
        if (game.input.wasKeyPressed(GLFW_KEY_ESCAPE) || clicked && cancel) { cancel(settings); return Action.CANCEL; }
        return Action.NONE;
    }

    /** One step on a row: the switch flips, the interval moves to the next one offered. */
    static void adjust(AutosaveSettings settings, int row, int direction) {
        if (row == 0) settings.enabled = !settings.enabled;
        else settings.stepInterval(direction);
    }

    /** What a row shows. */
    static String valueOf(AutosaveSettings settings, int row) {
        return row == 0 ? (settings.enabled ? "On" : "Off") : settings.intervalMinutes() + " minutes";
    }

    /** BACK: the settings are what they were when the screen opened. */
    void cancel(AutosaveSettings settings) {
        settings.copyFrom(original);
        original = null;
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static void button(UiRenderer ui, float x, float y, float w, String text, boolean hover, boolean primary) {
        ui.rect(x, y, w, 36, 0.08f, primary ? (hover ? 0.40f : 0.28f) : 0.12f, primary ? 0.30f : 0.15f, 0.98f);
        ui.rectOutline(x, y, w, 36, 1, 0.28f, hover ? 0.92f : 0.56f, hover ? 0.94f : 0.60f, 0.9f);
        ui.textCentered(x + w / 2, y + 10, 1.28f, text, 0.90f, 0.96f, 0.97f, 1);
    }
}
