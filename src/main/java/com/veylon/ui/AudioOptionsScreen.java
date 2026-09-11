package com.veylon.ui;

import com.veylon.Game;
import com.veylon.engine.AudioSettings;
import com.veylon.engine.UiRenderer;
import static org.lwjgl.glfw.GLFW.*;

/** Live keyboard and draggable-slider audio editor with apply/cancel semantics. */
public final class AudioOptionsScreen {
    /** Result returned to the title or pause controller. */
    public enum Action { NONE, APPLY, CANCEL }
    private static final String[] LABELS = {"Master", "Sound effects", "Ambience", "Music", "Mute all"};
    private static final AudioSettings.Bus[] BUSES = AudioSettings.Bus.values();
    /** Linear gain increment per keyboard press. */
    private static final float KEY_STEP = 0.05f;
    private int selected, dragging = -1;
    private AudioSettings original;

    /** Begins a transaction against the existing shared mix. */
    public void open(AudioSettings settings) {
        original = new AudioSettings(settings); selected = 0; dragging = -1;
    }

    /** Draws and edits the current mix; the caller persists only an APPLY result. */
    public Action update(Game game) {
        AudioSettings settings = game.audio.settings;
        if (original == null) open(settings);
        UiRenderer ui = game.ui;
        int width = ui.screenW(), height = ui.screenH();
        float pw = Math.min(680, width - 36), ph = Math.min(420, height - 30);
        float x0 = (width - pw) / 2, y0 = (height - ph) / 2;
        ui.rect(0, 0, width, height, 0, 0, 0, 0.62f);
        ui.panel(x0, y0, pw, ph);
        ui.textCentered(width / 2f, y0 + 18, 2.15f, "AUDIO", 0.78f, 0.96f, 0.98f, 1);
        ui.textCentered(width / 2f, y0 + 48, 1.08f, "Changes apply live. Set music to zero to disable it.", 0.50f, 0.60f, 0.64f, 1);

        if (game.input.wasKeyPressed(GLFW_KEY_UP) || game.input.wasKeyPressed(GLFW_KEY_W)) selected = Math.floorMod(selected - 1, LABELS.length);
        if (game.input.wasKeyPressed(GLFW_KEY_DOWN) || game.input.wasKeyPressed(GLFW_KEY_S)) selected = (selected + 1) % LABELS.length;
        int delta = 0;
        if (game.input.wasKeyPressed(GLFW_KEY_LEFT) || game.input.wasKeyPressed(GLFW_KEY_A)) delta = -1;
        if (game.input.wasKeyPressed(GLFW_KEY_RIGHT) || game.input.wasKeyPressed(GLFW_KEY_D)
                || game.input.wasKeyPressed(GLFW_KEY_ENTER) || game.input.wasKeyPressed(GLFW_KEY_SPACE)) delta = 1;
        if (delta != 0) { adjust(settings, selected, delta); game.audio.playClick(); }

        double mx = game.input.cursorX(), my = game.input.cursorY();
        boolean clicked = game.input.wasMousePressed(GLFW_MOUSE_BUTTON_LEFT);
        float rowX = x0 + 42, rowW = pw - 84, sliderX = rowX + 190, sliderW = rowW - 258;
        for (int i = 0; i < LABELS.length; i++) {
            float y = y0 + 84 + i * 44;
            boolean hover = inside(mx, my, rowX, y, rowW, 36);
            if (hover && clicked) {
                selected = i;
                if (i == 4) settings.mute = !settings.mute;
                else if (mx >= sliderX - 10) dragging = i;
            }
            if (i == selected) {
                ui.rect(rowX, y, rowW, 36, 0.08f, 0.22f, 0.24f, 0.88f);
                ui.rect(rowX, y, 3, 36, 0.28f, 0.84f, 0.87f, 1);
            }
            ui.textShadow(rowX + 14, y + 11, 1.28f, LABELS[i], 0.90f, 0.97f, 0.98f, 1);
            if (i < BUSES.length) {
                float value = settings.level(BUSES[i]);
                ui.rect(sliderX, y + 17, sliderW, 3, 0.20f, 0.30f, 0.33f, 1);
                ui.rect(sliderX, y + 17, sliderW * value, 3, 0.28f, 0.84f, 0.87f, 1);
                ui.rect(sliderX + sliderW * value - 3, y + 10, 6, 17, 0.96f, 0.78f, 0.42f, 1);
            }
            String value = i == 4 ? (settings.mute ? "On" : "Off") : Math.round(settings.level(BUSES[i]) * 100) + "%";
            ui.textShadow(rowX + rowW - ui.textWidth(value, 1.28f) - 12, y + 11, 1.28f, value, 0.96f, 0.78f, 0.42f, 1);
        }
        if (!game.input.isMouseDown(GLFW_MOUSE_BUTTON_LEFT)) dragging = -1;
        if (dragging >= 0) drag(settings, dragging, mx, sliderX, sliderW);

        ui.textCentered(width / 2f, y0 + ph - 91, 1.05f, "Drag sliders / Arrow keys     Back restores previous levels", 0.58f, 0.70f, 0.73f, 1);
        float buttonY = y0 + ph - 58, buttonW = 190, applyX = width / 2f - buttonW - 8, cancelX = width / 2f + 8;
        boolean apply = inside(mx, my, applyX, buttonY, buttonW, 36), cancel = inside(mx, my, cancelX, buttonY, buttonW, 36);
        button(ui, applyX, buttonY, buttonW, "APPLY [F5]", apply, true);
        button(ui, cancelX, buttonY, buttonW, "BACK [Esc]", cancel, false);
        if (game.input.wasKeyPressed(GLFW_KEY_F5) || clicked && apply) { original = null; return Action.APPLY; }
        if (game.input.wasKeyPressed(GLFW_KEY_ESCAPE) || clicked && cancel) { cancel(settings); return Action.CANCEL; }
        return Action.NONE;
    }

    static void adjust(AudioSettings settings, int row, int direction) {
        if (row == 4) settings.mute = !settings.mute;
        else settings.set(BUSES[row], settings.level(BUSES[row]) + KEY_STEP * direction);
    }

    static void drag(AudioSettings settings, int row, double mouse, float start, float width) {
        settings.set(BUSES[row], (float) ((mouse - start) / width));
    }

    void cancel(AudioSettings settings) { settings.copyFrom(original); original = null; dragging = -1; }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static void button(UiRenderer ui, float x, float y, float w, String text, boolean hover, boolean primary) {
        ui.rect(x, y, w, 36, 0.08f, primary ? (hover ? 0.40f : 0.28f) : 0.12f, primary ? 0.30f : 0.15f, 0.98f);
        ui.rectOutline(x, y, w, 36, 1, 0.28f, hover ? 0.92f : 0.56f, hover ? 0.94f : 0.60f, 0.9f);
        ui.textCentered(x + w / 2, y + 10, 1.28f, text, 0.90f, 0.96f, 0.97f, 1);
    }
}
