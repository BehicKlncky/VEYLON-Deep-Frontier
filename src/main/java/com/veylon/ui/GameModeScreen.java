package com.veylon.ui;

import com.veylon.Game;
import com.veylon.engine.Input;
import com.veylon.engine.UiRenderer;
import com.veylon.entity.GameMode;

import java.util.Objects;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Paused confirmation for switching an existing world's mode (R3). The screen
 * never switches anything itself: it returns CONFIRM or CANCEL and the owning
 * controller applies the answer, so a cancelled screen has no world effect.
 */
public final class GameModeScreen {

    /** Result handed to the game-mode controller. */
    public enum Action { NONE, CONFIRM, CANCEL }

    private static final String[] FIRST_CREATIVE = {
            "Creative mode permanently marks this frontier as a Creative world.",
            "You can return to Survival at any time, but the mark stays with this world."
    };
    private static final String[] MARKED_CREATIVE = {
            "This frontier is already a Creative world.",
            "Switch back to Creative mode?"
    };
    private static final String[] TO_SURVIVAL = {
            "Hunger, injuries and hostile creatures return.",
            "Flight ends, and falls hurt again."
    };
    private static final float PANEL_MAX_W = 760;
    private static final float PANEL_H = 320;
    private static final float BUTTON_W = 200;
    private static final float BUTTON_H = 36;

    private GameMode current = GameMode.SURVIVAL;
    private boolean marked;
    private String currentLine = "";

    public void open(GameMode current, boolean marked) {
        this.current = Objects.requireNonNull(current, "current");
        this.marked = marked;
        currentLine = "Current mode: " + PauseMenu.modeLabel(current, marked);
    }

    /** The mode a confirmation switches to: always the other one. */
    public static GameMode target(GameMode current) {
        return current == GameMode.SURVIVAL ? GameMode.CREATIVE : GameMode.SURVIVAL;
    }

    /**
     * Confirmation lines for leaving {@code current}. Only a switch to Creative
     * on a world that does not yet carry the mark shows the permanent-mark warning.
     */
    public static String[] confirmation(GameMode current, boolean marked) {
        if (current == GameMode.CREATIVE) {
            return TO_SURVIVAL.clone();
        }
        return (marked ? MARKED_CREATIVE : FIRST_CREATIVE).clone();
    }

    /** R3 keys: Escape or N cancels first, so a mixed press can never switch; Enter or Y confirms. */
    public Action handleKeys(Input input) {
        if (input.wasKeyPressed(GLFW_KEY_ESCAPE) || input.wasKeyPressed(GLFW_KEY_N)) {
            return Action.CANCEL;
        }
        if (input.wasKeyPressed(GLFW_KEY_ENTER) || input.wasKeyPressed(GLFW_KEY_Y)) {
            return Action.CONFIRM;
        }
        return Action.NONE;
    }

    public Action update(Game g) {
        UiRenderer ui = g.ui;
        int w = ui.screenW(), h = ui.screenH();
        ui.rect(0, 0, w, h, 0, 0, 0, 0.62f);
        float pw = Math.min(PANEL_MAX_W, w - 36);
        float x0 = (w - pw) / 2, y0 = (h - PANEL_H) / 2;
        ui.panel(x0, y0, pw, PANEL_H);

        boolean toCreative = target(current) == GameMode.CREATIVE;
        ui.textCentered(w / 2f, y0 + 18, 2.15f, toCreative ? "SWITCH TO CREATIVE" : "SWITCH TO SURVIVAL",
                0.78f, 0.96f, 0.98f, 1);
        ui.textCentered(w / 2f, y0 + 56, 1.1f, currentLine, 0.55f, 0.66f, 0.70f, 1);

        boolean warning = toCreative && !marked;
        String[] lines = current == GameMode.CREATIVE ? TO_SURVIVAL
                : marked ? MARKED_CREATIVE : FIRST_CREATIVE;
        float lineY = y0 + 104;
        for (String line : lines) {
            ui.textCentered(w / 2f, lineY, 1.25f, line, warning ? 1f : 0.90f,
                    warning ? 0.78f : 0.96f, warning ? 0.42f : 0.97f, 1f);
            lineY += 30;
        }

        double mx = g.input.cursorX(), my = g.input.cursorY();
        boolean click = g.input.wasMousePressed(GLFW_MOUSE_BUTTON_LEFT);
        float buttonY = y0 + PANEL_H - 84;
        float confirmX = w / 2f - BUTTON_W - 8, cancelX = w / 2f + 8;
        boolean confirmHover = inside(mx, my, confirmX, buttonY, BUTTON_W, BUTTON_H);
        boolean cancelHover = inside(mx, my, cancelX, buttonY, BUTTON_W, BUTTON_H);
        button(ui, confirmX, buttonY, "CONFIRM [Enter]", confirmHover, true);
        button(ui, cancelX, buttonY, "CANCEL [Esc]", cancelHover, false);
        ui.textCentered(w / 2f, y0 + PANEL_H - 36, 1.05f, "Y confirms     N cancels",
                0.58f, 0.70f, 0.73f, 1);

        if (click && cancelHover) {
            return Action.CANCEL;
        }
        if (click && confirmHover) {
            return Action.CONFIRM;
        }
        return handleKeys(g.input);
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
