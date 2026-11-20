package com.veylon.ui;

import com.veylon.Game;
import com.veylon.engine.Input;
import com.veylon.engine.UiRenderer;
import com.veylon.entity.GameMode;
import com.veylon.gfx.FontRenderer;
import com.veylon.world.WorldSeed;

import java.util.function.IntPredicate;
import java.util.function.ToDoubleFunction;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Worldless choice of the world, its mode and its seed between the title and the slot it is saved
 * in (R2; 0.9.1 milestones 04 and 06). The abandoned city is the new world (urban milestone 26); the
 * wilderness stays on offer beside it, labelled as the legacy world. Every opening starts from the
 * session's world, from Survival and from the seed the frontend offers, so an abandoned choice never
 * carries into a later frontier.
 *
 * <p>The seed (D-33) is the one offered unless the player types another: E or a click on the field
 * starts typing, and while it is typed every key goes to the field, so S, C, W and R type letters
 * rather than choosing; Enter or Escape finishes it. A number is the seed itself and any other text
 * is hashed as {@link WorldSeed} says. R asks the frontend for a new seed. Keyboard decisions live
 * in {@link #handleKeys}, which never touches the renderer, so routing stays testable without a GL
 * context.
 */
public final class NewFrontierScreen {

    /** Result handed to the frontend controller: START goes on to the slot picker, REROLL asks for a seed. */
    public enum Action { NONE, START, BACK, REROLL }

    /** The line under the notes: where Enter goes next. */
    public static final String SLOT_NOTE = "Next, choose the slot this world is saved in.";
    /** The two worlds, the city first. */
    public static final String CITY_TITLE = "THE ABANDONED CITY";
    public static final String WILDERNESS_TITLE = "THE WILDERNESS  (legacy)";
    /** One line under the cards saying what the chosen world is. */
    public static final String CITY_NOTE =
            "Scavenge its buildings, hold a refuge and bring the old radio relay back.";
    public static final String WILDERNESS_NOTE =
            "The world of releases up to 0.8.1: forests, caves, settlements and the beacon.";
    /** The key line at the foot of the screen. */
    public static final String KEYS = "S / C or arrows choose the mode     W the world     E types a seed"
            + "     R rerolls it     Enter chooses the slot     Esc returns";
    /** The key line while the seed is typed. */
    public static final String SEED_KEYS =
            "Type a number or any words     Backspace deletes     Enter or Esc finishes the seed";
    /** The line under an offered seed. */
    public static final String OFFERED_NOTE = "A new seed. The same seed always makes the same world.";
    /** The line under a typed number. */
    public static final String NUMBER_NOTE = "Your seed. The same seed always makes the same world.";
    /** The field's text while the player has typed nothing yet. */
    public static final String PLACEHOLDER = "a number or any words";
    public static final String SEED_LABEL = "SEED";
    public static final String REROLL_LABEL = "REROLL [R]";
    /** Characters a typed seed holds at most; a long's longest number is 20. */
    public static final int MAX_SEED_CODE_POINTS = 32;
    /** Scale of the seed's text and its label. */
    public static final float SEED_SCALE = 1.25f;
    /** Width of the seed field, and the room its text has inside it. */
    public static final float FIELD_W = 340;
    public static final float FIELD_TEXT_SPACE = FIELD_W - 24;
    public static final float REROLL_W = 150;

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
    private static final float FIELD_H = 32;
    private static final float BUTTON_W = 190;
    private static final float BUTTON_H = 36;

    private int selected;
    private boolean city = true;
    /** The seed offered by the frontend, which stands unless the player types one. */
    private long offered;
    private String offeredText = "0";
    /** What the player typed; empty for none. */
    private String typed = "";
    private boolean typing;
    /** The field's text as drawn, cut to its tail when it is wider than the field. */
    private String fittedFor;
    private String fitted = "";
    private IntPredicate drawable;

    /** Opens on the city and Survival, offering the seed offered last. */
    public void open() {
        open(true);
    }

    /** As {@link #open(boolean, long)}, offering the seed offered last. */
    public void open(boolean city) {
        open(city, offered);
    }

    /**
     * Resets the choice to Survival and offers {@code seed}, with nothing typed; {@code city}
     * decides which world the screen opens on: the session's own, the city unless a QA session
     * chose the wilderness.
     */
    public void open(boolean city, long seed) {
        selected = 0;
        this.city = city;
        typing = false;
        offer(seed);
    }

    /** R: offers {@code seed} instead, and forgets what was typed. */
    public void offer(long seed) {
        offered = seed;
        offeredText = Long.toString(seed);
        typed = "";
    }

    public GameMode selectedMode() {
        return MODES[selected];
    }

    /** Whether the chosen world is the abandoned city rather than the wilderness. */
    public boolean showsCity() {
        return city;
    }

    /** The seed the new world is built from: the typed one, or the one offered. */
    public long seed() {
        return typed.isBlank() ? offered : WorldSeed.of(typed);
    }

    /** What the player typed; empty when the offered seed stands. */
    public String typedSeed() {
        return typed;
    }

    /** Whether keys are going to the seed field. */
    public boolean typingSeed() {
        return typing;
    }

    /** The field's text: the typed seed while there is one or while typing, else the offered number. */
    public String fieldText() {
        return typing || !typed.isBlank() ? typed : offeredText;
    }

    /** The line under the seed: where it comes from, and for typed words the number they make. */
    public String seedNote() {
        if (typed.isBlank()) {
            return OFFERED_NOTE;
        }
        return WorldSeed.isNumber(typed) ? NUMBER_NOTE : "These words make seed " + seed() + ".";
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

    /** As {@link #handleKeys(Input, IntPredicate)}, taking every printable character. */
    public Action handleKeys(Input input) {
        return handleKeys(input, codePoint -> true);
    }

    /**
     * R2 keys: S/C, arrows or Tab choose the mode; W switches between the city and the wilderness;
     * E starts typing the seed and R asks for a new one (0.9.1 milestone 06); Enter goes on to the
     * slot picker; Escape returns to the title. While the seed is typed, typed characters edit it,
     * Backspace deletes one, and Enter or Escape finishes it; nothing else acts.
     *
     * @param drawable whether the font can draw a typed code point
     */
    public Action handleKeys(Input input, IntPredicate drawable) {
        if (typing) {
            if (input.wasKeyPressed(GLFW_KEY_ENTER) || input.wasKeyPressed(GLFW_KEY_ESCAPE)) {
                typing = false;
                return Action.NONE;
            }
            if (input.wasKeyPressed(GLFW_KEY_BACKSPACE) && !typed.isEmpty()) {
                typed = typed.substring(0, typed.offsetByCodePoints(typed.length(), -1));
            }
            for (int i = 0; i < input.typedCount(); i++) {
                int codePoint = input.typedCodePoint(i);
                if (codePoint >= ' ' && codePoint != 0x7F && drawable.test(codePoint)
                        && typed.codePointCount(0, typed.length()) < MAX_SEED_CODE_POINTS) {
                    typed = typed + Character.toString(codePoint);
                }
            }
            return Action.NONE;
        }
        if (input.wasKeyPressed(GLFW_KEY_E)) {
            typing = true; // The E typed with this press is never read: the field reads from the next frame.
            return Action.NONE;
        }
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
        if (input.wasKeyPressed(GLFW_KEY_R)) {
            return Action.REROLL;
        }
        if (input.wasKeyPressed(GLFW_KEY_ENTER)) {
            return Action.START;
        }
        return Action.NONE;
    }

    /** Draws the choice in the title's visual language and returns the mouse or key action. */
    public Action update(Game g) {
        if (drawable == null) {
            FontRenderer font = g.ui.fontRenderer();
            drawable = codePoint -> font.hasGlyph(codePoint, FontRenderer.Weight.REGULAR);
        }
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
        // Where the title's rule stood, so the tabs keep their place among the title and the
        // cards at every interface scale, but never on the line above them.
        float tabY = Math.max(h * 0.12f + 62, h * 0.24f - TAB_H / 2f);
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

        float seedY = noteY + 32;
        boolean reroll = drawSeed(g, ui, w, seedY, mx, my, click);
        ui.textCentered(w / 2f, seedY + FIELD_H + 8, 1.05f, seedNote(), 0.55f, 0.66f, 0.70f, 1f);
        float slotY = seedY + FIELD_H + 34;
        ui.textCentered(w / 2f, slotY, 1.2f, SLOT_NOTE, 0.80f, 0.94f, 0.95f, 1f);

        float buttonY = slotY + 36;
        float startX = w / 2f - BUTTON_W - 8, backX = w / 2f + 8;
        boolean startHover = inside(mx, my, startX, buttonY, BUTTON_W, BUTTON_H);
        boolean backHover = inside(mx, my, backX, buttonY, BUTTON_W, BUTTON_H);
        button(ui, startX, buttonY, BUTTON_W, "CHOOSE SLOT [Enter]", startHover, true);
        button(ui, backX, buttonY, BUTTON_W, "BACK [Esc]", backHover, false);
        ui.textCentered(w / 2f, h - 34, 1.05f, typing ? SEED_KEYS : KEYS, 0.42f, 0.50f, 0.56f, 1f);

        if (reroll) {
            return Action.REROLL;
        }
        if (click && startHover) {
            typing = false;
            return Action.START;
        }
        if (click && backHover) {
            typing = false;
            return Action.BACK;
        }
        return handleKeys(g.input, drawable);
    }

    /**
     * The seed's row: its label, the field and REROLL. A click on the field starts typing and a
     * click elsewhere finishes it. Returns whether REROLL was clicked.
     */
    private boolean drawSeed(Game g, UiRenderer ui, int w, float y, double mx, double my, boolean click) {
        float labelW = ui.textWidth(SEED_LABEL, SEED_SCALE);
        float rowW = labelW + 14 + FIELD_W + 14 + REROLL_W;
        float labelX = w / 2f - rowW / 2f, fieldX = labelX + labelW + 14, rerollX = fieldX + FIELD_W + 14;
        boolean fieldHover = inside(mx, my, fieldX, y, FIELD_W, FIELD_H);
        boolean rerollHover = inside(mx, my, rerollX, y - 2, REROLL_W, FIELD_H + 4);
        if (click) {
            typing = fieldHover;
        }
        ui.textShadow(labelX, y + 8, SEED_SCALE, SEED_LABEL, 0.90f, 0.72f, 0.42f, 1f);
        ui.rect(fieldX, y, FIELD_W, FIELD_H, 0.03f, 0.06f, 0.08f, 0.95f);
        ui.rectOutline(fieldX, y, FIELD_W, FIELD_H, typing ? 2 : 1,
                0.28f, typing || fieldHover ? 0.9f : 0.5f, typing || fieldHover ? 0.95f : 0.55f, 0.9f);
        String text = fieldText();
        if (text.isEmpty()) {
            ui.text(fieldX + 12, y + 8, SEED_SCALE, PLACEHOLDER, 0.40f, 0.48f, 0.52f, 1f);
        } else {
            if (!text.equals(fittedFor)) {
                fittedFor = text;
                fitted = fittedTail(text, s -> ui.textWidth(s, SEED_SCALE));
            }
            ui.text(fieldX + 12, y + 8, SEED_SCALE, fitted, 0.92f, 0.97f, 0.98f, 1f);
        }
        if (typing && ((int) (g.totalTime * 2) & 1) == 0) {
            float caretX = fieldX + 13 + (text.isEmpty() ? 0 : ui.textWidth(fitted, SEED_SCALE));
            ui.rect(caretX, y + 7, 2, FIELD_H - 14, 0.9f, 0.95f, 1f, 1f);
        }
        button(ui, rerollX, y - 2, REROLL_W, REROLL_LABEL, rerollHover, false);
        return click && rerollHover;
    }

    /** The end of {@code text} that fits the field, cut only between code points. */
    static String fittedTail(String text, ToDoubleFunction<String> width) {
        String tail = text;
        int offset = 0;
        while (offset < text.length() && width.applyAsDouble(tail) > FIELD_TEXT_SPACE) {
            offset = text.offsetByCodePoints(offset, 1);
            tail = text.substring(offset);
        }
        return tail;
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

    private static void button(UiRenderer ui, float x, float y, float width, String text,
                               boolean hover, boolean primary) {
        ui.rect(x, y, width, BUTTON_H, 0.08f, primary ? (hover ? 0.40f : 0.28f) : 0.12f,
                primary ? 0.30f : 0.15f, 0.98f);
        ui.rectOutline(x, y, width, BUTTON_H, 1, 0.28f, hover ? 0.92f : 0.56f,
                hover ? 0.94f : 0.60f, 0.9f);
        ui.textCentered(x + width / 2, y + 10, 1.28f, text, 0.90f, 0.96f, 0.97f, 1);
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
