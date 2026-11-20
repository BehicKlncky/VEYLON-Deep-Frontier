package com.veylon.ui;

import com.veylon.Game;
import com.veylon.engine.Input;
import com.veylon.engine.UiRenderer;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The pause menu's way back to the title (0.9.1 milestone 06, decision D-04): save into the world's
 * slot and return, return without saving, or stay. It says how much play the slot's save is behind,
 * and Enter keeps the player's progress, since it is the choice that loses nothing. A save that
 * fails keeps the world and the prompt open, with the reason in a line under the choices. Keyboard
 * decisions live in {@link #handleKeys}, which never touches the renderer.
 */
public final class ReturnToTitlePrompt {

    /** The answer handed to the frontend controller. */
    public enum Action { NONE, SAVE_AND_RETURN, RETURN, CANCEL }

    static final String TITLE = "RETURN TO THE TITLE";
    static final String[] BUTTONS = {"SAVE AND RETURN [Enter]", "RETURN WITHOUT SAVING [N]", "CANCEL [Esc]"};
    static final Action[] ACTIONS = {Action.SAVE_AND_RETURN, Action.RETURN, Action.CANCEL};
    static final String KEYS = "Enter saves and returns     N returns without saving     Esc stays";
    static final float PANEL_W = 600, BUTTON_W = 340, BUTTON_H = 36;

    private int slot = 1;
    private boolean bound;
    private double unsavedSeconds;
    private String notice = "";

    /**
     * Opens for a world in {@code slot}.
     *
     * @param bound          whether the world belongs to the slot yet ({@code SaveSession.bound})
     * @param unsavedSeconds real seconds of play since the slot was last written
     */
    public void open(int slot, boolean bound, double unsavedSeconds) {
        this.slot = slot;
        this.bound = bound;
        this.unsavedSeconds = unsavedSeconds;
        notice = "";
    }

    /** The line under the choices, such as why the save failed; empty for none. */
    public void notice(String text) {
        notice = text == null ? "" : text;
    }

    public String notice() {
        return notice;
    }

    /** What the slot's save is behind the world, in the player's words. */
    public String unsavedLine() {
        if (!bound) {
            return "This world is in no slot yet: saving puts it in slot " + slot + ".";
        }
        return "Since slot " + slot + " was last saved: " + SlotPickerScreen.duration(unsavedSeconds) + " of play.";
    }

    /** What each choice does to the slot. */
    public String consequenceLine() {
        return "Returning without saving leaves slot " + slot + " as it is.";
    }

    /** Enter saves and returns, N returns without saving, Escape stays. */
    public Action handleKeys(Input input) {
        if (input.wasKeyPressed(GLFW_KEY_ESCAPE)) {
            return Action.CANCEL;
        }
        if (input.wasKeyPressed(GLFW_KEY_N)) {
            return Action.RETURN;
        }
        if (input.wasKeyPressed(GLFW_KEY_ENTER)) {
            return Action.SAVE_AND_RETURN;
        }
        return Action.NONE;
    }

    /** Draws the prompt over the paused world and returns the mouse or key answer. */
    public Action update(Game g) {
        UiRenderer ui = g.ui;
        int w = ui.screenW(), h = ui.screenH();
        float pw = Math.min(PANEL_W, w - 36), ph = 326;
        float x0 = (w - pw) / 2, y0 = (h - ph) / 2;
        ui.rect(0, 0, w, h, 0, 0, 0, 0.62f);
        ui.panel(x0, y0, pw, ph);
        ui.textCentered(w / 2f, y0 + 18, 2.15f, TITLE, 0.78f, 0.96f, 0.98f, 1);
        ui.textCentered(w / 2f, y0 + 54, 1.15f, unsavedLine(), 0.85f, 0.88f, 0.92f, 1);
        ui.textCentered(w / 2f, y0 + 78, 1.05f, consequenceLine(), 0.55f, 0.66f, 0.70f, 1);

        double mx = g.input.cursorX(), my = g.input.cursorY();
        boolean clicked = g.input.wasMousePressed(GLFW_MOUSE_BUTTON_LEFT);
        Action chosen = Action.NONE;
        float bx = w / 2f - BUTTON_W / 2f;
        for (int i = 0; i < BUTTONS.length; i++) {
            float by = y0 + 110 + i * (BUTTON_H + 10);
            boolean hover = mx >= bx && mx <= bx + BUTTON_W && my >= by && my <= by + BUTTON_H;
            boolean primary = i == 0;
            ui.rect(bx, by, BUTTON_W, BUTTON_H, 0.08f, primary ? (hover ? 0.40f : 0.28f) : (hover ? 0.22f : 0.12f),
                    primary ? (hover ? 0.42f : 0.30f) : (hover ? 0.24f : 0.15f), 0.98f);
            ui.rectOutline(bx, by, BUTTON_W, BUTTON_H, 1, hover ? 0.45f : 0.28f, hover ? 0.92f : 0.56f,
                    hover ? 0.94f : 0.60f, 0.9f);
            ui.textCentered(w / 2f, by + 10, 1.28f, BUTTONS[i], 0.90f, 0.96f, 0.97f, 1);
            if (hover && clicked) {
                chosen = ACTIONS[i];
            }
        }
        if (!notice.isBlank()) {
            ui.textCentered(w / 2f, y0 + ph - 50, 1.1f, notice, 1f, 0.65f, 0.42f, 1);
        }
        ui.textCentered(w / 2f, y0 + ph - 26, 1.05f, KEYS, 0.42f, 0.50f, 0.56f, 1);
        return chosen != Action.NONE ? chosen : handleKeys(g.input);
    }
}
