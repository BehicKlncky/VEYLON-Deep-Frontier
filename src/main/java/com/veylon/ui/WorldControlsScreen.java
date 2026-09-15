package com.veylon.ui;

import com.veylon.Game;
import com.veylon.engine.Input;
import com.veylon.simulation.TimePreset;
import com.veylon.simulation.WeatherSystem;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Paused Creative world controls (R25). Like the game-mode confirmation, the
 * screen changes nothing itself: it reports one command per frame and the
 * controls collaborator applies it, so drawing and policy stay apart.
 *
 * <p>The screen owns every key while it is open, which is why the hotkey
 * router returns early for {@code WORLD_CONTROLS}. Escape and T close it; no
 * key here reaches quick save, quick load, quit or the hotbar.
 */
public final class WorldControlsScreen {

    /** One frame's command. {@code time} and {@code weather} are set only for their kind. */
    public record Action(Kind kind, TimePreset time, WeatherSystem.Weather weather) {

        /** What the player asked for. */
        public enum Kind {
            NONE, CLOSE, SET_TIME, SET_WEATHER, TOGGLE_FREEZE, TOGGLE_LOCK, TOGGLE_SPAWNING
        }

        public static final Action NONE = new Action(Kind.NONE, null, null);

        public static Action of(Kind kind) {
            return new Action(kind, null, null);
        }

        public static Action time(TimePreset preset) {
            return new Action(Kind.SET_TIME, preset, null);
        }

        public static Action weather(WeatherSystem.Weather weather) {
            return new Action(Kind.SET_WEATHER, null, weather);
        }
    }

    private static final int[] TIME_KEYS = {GLFW_KEY_1, GLFW_KEY_2, GLFW_KEY_3, GLFW_KEY_4};
    private static final int[] WEATHER_KEYS = {
            GLFW_KEY_5, GLFW_KEY_6, GLFW_KEY_7, GLFW_KEY_8, GLFW_KEY_9, GLFW_KEY_0};
    private static final String[] WEATHER_KEY_LABELS = {"5", "6", "7", "8", "9", "0"};
    private static final float PANEL_MAX_W = 780;
    private static final float PANEL_H = 440;
    private static final float ROW_MAX_W = 700;
    private static final float BUTTON_H = 32;
    private static final float BUTTON_GAP = 8;
    private static final float TOGGLE_W = 460;
    private static final float SECTION_GAP = 42;
    private static final float HEADING_GAP = 24;

    private float panelX;
    private float panelY;
    private float panelW;

    /** The world itself holds every control, so opening has nothing to remember. */
    public void open() {
    }

    public Action update(Game g) {
        ButtonRow row = new ButtonRow(g);
        int w = g.ui.screenW(), h = g.ui.screenH();
        g.ui.rect(0, 0, w, h, 0, 0, 0, 0.62f);
        panelW = Math.min(PANEL_MAX_W, w - 36);
        panelX = (w - panelW) / 2;
        panelY = (h - PANEL_H) / 2;
        g.ui.panel(panelX, panelY, panelW, PANEL_H);

        g.ui.textCentered(w / 2f, panelY + 16, 2.15f, "WORLD CONTROLS", 0.78f, 0.96f, 0.98f, 1);
        g.ui.textCentered(w / 2f, panelY + 50, 1.05f,
                "Creative only. Returning to Survival releases every control.",
                0.55f, 0.66f, 0.70f, 1);

        float y = panelY + 84;
        heading(g, y, "TIME OF DAY  (forward only)");
        y += HEADING_GAP;
        TimePreset[] presets = TimePreset.values();
        for (int i = 0; i < presets.length; i++) {
            if (row.button(i, presets.length, y, (i + 1) + "  " + presets[i].label, false)) {
                row.take(Action.time(presets[i]));
            }
        }
        y += SECTION_GAP;
        if (toggle(g, y, "[F] Freeze daylight cycle", g.daylightFrozen())) {
            row.take(Action.of(Action.Kind.TOGGLE_FREEZE));
        }

        y += SECTION_GAP;
        heading(g, y, "WEATHER");
        y += HEADING_GAP;
        WeatherSystem.Weather[] weathers = WeatherSystem.Weather.values();
        for (int i = 0; i < weathers.length; i++) {
            boolean active = g.weather.effective() == weathers[i];
            if (row.button(i, weathers.length, y,
                    WEATHER_KEY_LABELS[i] + "  " + weathers[i].displayName, active)) {
                row.take(Action.weather(weathers[i]));
            }
        }
        y += SECTION_GAP;
        if (toggle(g, y, "[L] Lock weather", g.weatherLocked())) {
            row.take(Action.of(Action.Kind.TOGGLE_LOCK));
        }

        y += SECTION_GAP;
        heading(g, y, "WILDLIFE");
        y += HEADING_GAP;
        if (toggle(g, y, "[K] Pause wildlife spawning", g.spawningPaused())) {
            row.take(Action.of(Action.Kind.TOGGLE_SPAWNING));
        }

        g.ui.textCentered(w / 2f, panelY + PANEL_H - 32, 1.1f,
                "Esc or T returns to the pause menu", 0.58f, 0.70f, 0.73f, 1);

        return row.clicked != null ? row.clicked : handleKeys(g.input);
    }

    /** R25 key ownership: every key the screen answers, and nothing else. */
    public Action handleKeys(Input input) {
        if (input.wasKeyPressed(GLFW_KEY_ESCAPE) || input.wasKeyPressed(GLFW_KEY_T)) {
            return Action.of(Action.Kind.CLOSE);
        }
        TimePreset[] presets = TimePreset.values();
        for (int i = 0; i < presets.length && i < TIME_KEYS.length; i++) {
            if (input.wasKeyPressed(TIME_KEYS[i])) {
                return Action.time(presets[i]);
            }
        }
        WeatherSystem.Weather[] weathers = WeatherSystem.Weather.values();
        for (int i = 0; i < weathers.length && i < WEATHER_KEYS.length; i++) {
            if (input.wasKeyPressed(WEATHER_KEYS[i])) {
                return Action.weather(weathers[i]);
            }
        }
        if (input.wasKeyPressed(GLFW_KEY_F)) {
            return Action.of(Action.Kind.TOGGLE_FREEZE);
        }
        if (input.wasKeyPressed(GLFW_KEY_L)) {
            return Action.of(Action.Kind.TOGGLE_LOCK);
        }
        if (input.wasKeyPressed(GLFW_KEY_K)) {
            return Action.of(Action.Kind.TOGGLE_SPAWNING);
        }
        return Action.NONE;
    }

    private void heading(Game g, float y, String text) {
        g.ui.textShadow(panelX + 34, y, 1.2f, text, 0.80f, 0.86f, 0.90f, 1);
    }

    private boolean toggle(Game g, float y, String text, boolean on) {
        float tw = Math.min(TOGGLE_W, panelW - 68);
        float x = panelX + (panelW - tw) / 2;
        boolean hover = inside(g, x, y, tw, BUTTON_H);
        g.ui.rect(x, y, tw, BUTTON_H, 0.08f, hover ? 0.32f : 0.14f, on ? 0.26f : 0.16f, 0.98f);
        g.ui.rectOutline(x, y, tw, BUTTON_H, 1, on ? 0.95f : 0.28f,
                hover || on ? 0.92f : 0.56f, 0.60f, 0.9f);
        g.ui.textShadow(x + 14, y + 9, 1.18f, text, 0.90f, 0.96f, 0.97f, 1);
        g.ui.textShadow(x + tw - 52, y + 9, 1.18f, on ? "ON" : "OFF",
                on ? 1f : 0.55f, on ? 0.82f : 0.66f, on ? 0.40f : 0.70f, 1);
        return hover && g.input.wasMousePressed(GLFW_MOUSE_BUTTON_LEFT);
    }

    private boolean inside(Game g, float x, float y, float w, float h) {
        double mx = g.input.cursorX(), my = g.input.cursorY();
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    /** Lays out one evenly divided button row and remembers the click it received. */
    private final class ButtonRow {
        private final Game game;
        private Action clicked;

        ButtonRow(Game game) {
            this.game = game;
        }

        void take(Action action) {
            clicked = action;
        }

        boolean button(int index, int count, float y, String text, boolean active) {
            float rowW = Math.min(ROW_MAX_W, panelW - 68);
            float bw = (rowW - (count - 1) * BUTTON_GAP) / count;
            float x = panelX + (panelW - rowW) / 2 + index * (bw + BUTTON_GAP);
            boolean hover = inside(game, x, y, bw, BUTTON_H);
            game.ui.rect(x, y, bw, BUTTON_H, 0.08f, hover ? 0.34f : (active ? 0.26f : 0.14f),
                    active ? 0.24f : 0.16f, 0.98f);
            game.ui.rectOutline(x, y, bw, BUTTON_H, 1, active ? 0.95f : 0.28f,
                    hover || active ? 0.92f : 0.56f, 0.60f, 0.9f);
            game.ui.textCentered(x + bw / 2, y + 9, 1.15f, text, 0.90f, 0.96f, 0.97f, 1);
            return hover && game.input.wasMousePressed(GLFW_MOUSE_BUTTON_LEFT);
        }
    }
}
