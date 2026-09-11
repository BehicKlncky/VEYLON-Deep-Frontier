package com.veylon.ui;

import com.veylon.Game;
import com.veylon.engine.UiRenderer;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_L;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_N;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_O;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_Q;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_V;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

/** Lightweight front end; world creation/loading remains owned by {@link Game}. */
public final class TitleScreen {

    public enum Action {
        NONE, NEW_GAME, LOAD_GAME, OPTIONS, QUIT, AUDIO
    }

    private int selection;
    private String notice = "";

    public Action update(Game g) {
        UiRenderer ui = g.ui;
        int w = ui.screenW(), h = ui.screenH();

        // Restrained alien-science backdrop assembled from UI primitives.
        ui.rect(0, 0, w, h, 0.015f, 0.022f, 0.035f, 1f);
        for (int i = 0; i < 9; i++) {
            float x = w * (0.08f + i * 0.105f);
            float height = h * (0.10f + (i % 3) * 0.045f);
            ui.rect(x, h * 0.18f, 2, height, 0.10f, 0.52f, 0.56f, 0.28f);
        }
        ui.rect(w * 0.18f, h * 0.34f, w * 0.64f, 2, 0.18f, 0.72f, 0.76f, 0.34f);

        ui.textCentered(w / 2f, h * 0.20f, 3.2f, "VEYLON", 0.80f, 0.94f, 0.95f, 1f);
        ui.textCentered(w / 2f, h * 0.20f + 42, 1.55f, "DEEP FRONTIER",
                0.90f, 0.72f, 0.42f, 1f);
        ui.textCentered(w / 2f, h * 0.20f + 70, 1.15f,
                "SURVIVE  /  UNDERSTAND  /  SIGNAL", 0.46f, 0.62f, 0.66f, 1f);

        String[] labels = {"NEW FRONTIER", "LOAD FRONTIER", "GRAPHICS", "AUDIO", "QUIT"};
        Action[] actions = {Action.NEW_GAME, Action.LOAD_GAME, Action.OPTIONS, Action.AUDIO, Action.QUIT};
        float bw = Math.min(360, w * 0.52f), bh = 36;
        float x = w / 2f - bw / 2f, y0 = h * 0.45f;
        double mx = g.input.cursorX(), my = g.input.cursorY();
        boolean click = g.input.wasMousePressed(GLFW_MOUSE_BUTTON_LEFT);
        for (int i = 0; i < labels.length; i++) {
            float y = y0 + i * 44;
            boolean hover = mx >= x && mx <= x + bw && my >= y && my <= y + bh;
            if (hover) {
                selection = i;
            }
            boolean active = i == selection;
            ui.rect(x, y, bw, bh, active ? 0.10f : 0.045f,
                    active ? 0.28f : 0.07f, active ? 0.30f : 0.10f, 0.96f);
            ui.rectOutline(x, y, bw, bh, active ? 2 : 1,
                    active ? 0.30f : 0.20f, active ? 0.82f : 0.34f,
                    active ? 0.84f : 0.38f, 0.9f);
            ui.textCentered(w / 2f, y + 9, 1.5f, labels[i],
                    active ? 0.92f : 0.64f, active ? 1f : 0.72f,
                    active ? 1f : 0.76f, 1f);
            if (hover && click) {
                return actions[i];
            }
        }

        if (!notice.isBlank()) {
            ui.textCentered(w / 2f, y0 + 228, 1.2f, notice, 1f, 0.65f, 0.42f, 1f);
        }
        ui.textCentered(w / 2f, h - 34, 1.05f,
                "N / L / O / V / Q     Enter selects     Esc quits",
                0.42f, 0.50f, 0.56f, 1f);

        if (g.input.wasKeyPressed(GLFW_KEY_N)) return Action.NEW_GAME;
        if (g.input.wasKeyPressed(GLFW_KEY_L)) return Action.LOAD_GAME;
        if (g.input.wasKeyPressed(GLFW_KEY_V)) return Action.AUDIO;
        if (g.input.wasKeyPressed(GLFW_KEY_O)) return Action.OPTIONS;
        if (g.input.wasKeyPressed(GLFW_KEY_Q) || g.input.wasKeyPressed(GLFW_KEY_ESCAPE)) {
            return Action.QUIT;
        }
        if (g.input.wasKeyPressed(GLFW_KEY_ENTER)) return actions[selection];
        return Action.NONE;
    }

    public void notice(String message) {
        notice = message == null ? "" : message;
    }
}
