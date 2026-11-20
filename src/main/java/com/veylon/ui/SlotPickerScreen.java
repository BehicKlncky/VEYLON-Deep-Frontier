package com.veylon.ui;

import com.veylon.Game;
import com.veylon.engine.Input;
import com.veylon.engine.UiRenderer;
import com.veylon.entity.GameMode;
import com.veylon.save.SaveHeader;
import com.veylon.save.SaveSlots;
import com.veylon.save.SaveStamp;
import com.veylon.save.SaveSummary;
import com.veylon.world.World;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The worldless slot picker (0.9.1 milestone 04): LOAD FRONTIER lists the three slots to load,
 * restore or delete, and NEW FRONTIER lists them to choose where a new world is saved. Each card
 * shows what the slot's files say about themselves ({@link SaveSlots#describeAll}); nothing is
 * loaded to show it. Replacing, deleting and restoring each ask first, in a confirmation drawn over
 * the list that owns every key while it is open. Like the other title screens it never acts itself:
 * it returns an action for the frontend controller, and keys are decided in {@link #handleKeys},
 * which never touches the renderer, so routing is testable without a GL context.
 */
public final class SlotPickerScreen {

    /** Why the picker is open. */
    public enum Purpose { LOAD, NEW }

    /** Result handed to the frontend controller; {@link #selected()} names the slot. */
    public enum Action { NONE, CHOOSE, RESTORE, DELETE, BACK }

    /** The confirmation open over the list, if any. */
    public enum Ask { NONE, REPLACE, DELETE, RESTORE }

    /** The line under the cards when VEYLON 0.9.0's single save is in the folder. */
    public static final String LEGACY_NOTE =
            "saves/veylon.sav is a save from VEYLON 0.9.0 or earlier: this version cannot open it and leaves it as it is.";
    public static final String LOAD_KEYS =
            "1-3 or arrows choose     Enter loads     R restores a backup     Del deletes     Esc returns";
    public static final String NEW_KEYS =
            "1-3 or arrows choose     Enter starts here     Del deletes     Esc returns";
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    static final float CARD_MAX_W = 820;
    static final float CARD_PAD = 16;
    static final float LINE_SCALE = 1.05f;
    private static final float CARD_H = 116;
    private static final float CARD_GAP = 10;
    private static final float LINE_STEP = 18;
    private static final float BUTTON_W = 180;
    private static final float BUTTON_H = 36;
    private static final float BUTTON_GAP = 12;
    static final float PANEL_MAX_W = 760;
    private static final float PANEL_H = 260;
    private static final float CONTENT_H = 650;

    private Purpose purpose = Purpose.LOAD;
    private List<SaveSlots.Slot> slots = List.of();
    private boolean legacySave;
    private int selected = 1;
    private Ask asking = Ask.NONE;
    private String notice = "";

    /**
     * Opens on {@code slots} for {@code purpose}, with {@code preferred} selected: a new world
     * starts on the first empty slot, a load on the slot saved last.
     */
    public void open(Purpose purpose, List<SaveSlots.Slot> slots, boolean legacySave, int preferred) {
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        asking = Ask.NONE;
        notice = "";
        refresh(slots, legacySave);
        select(preferred);
    }

    /** Shows the slots again as their files now are, keeping the selection and the purpose. */
    public void refresh(List<SaveSlots.Slot> slots, boolean legacySave) {
        if (slots.isEmpty()) {
            throw new IllegalArgumentException("no slots to show");
        }
        this.slots = List.copyOf(slots);
        this.legacySave = legacySave;
        selected = Math.max(1, Math.min(selected, this.slots.size()));
    }

    /** The slot a new world goes into by default: the first empty one, else slot 1. */
    public static int firstEmpty(List<SaveSlots.Slot> slots) {
        return slots.stream().filter(s -> s.state() == SaveSlots.State.EMPTY).mapToInt(SaveSlots.Slot::number)
                .findFirst().orElse(1);
    }

    /** The slot a load opens on by default: the ready slot saved last, else {@code fallback}. */
    public static int lastSaved(List<SaveSlots.Slot> slots, int fallback) {
        SaveSlots.Slot last = null;
        for (SaveSlots.Slot slot : slots) {
            if (slot.state() == SaveSlots.State.READY
                    && (last == null || slot.summary().savedAt() > last.summary().savedAt())) {
                last = slot;
            }
        }
        return last == null ? fallback : last.number();
    }

    public Purpose purpose() {
        return purpose;
    }

    /** The slots as the picker shows them. */
    public List<SaveSlots.Slot> slots() {
        return slots;
    }

    /** The selected slot's number. */
    public int selected() {
        return selected;
    }

    public SaveSlots.Slot selectedSlot() {
        return slots.get(selected - 1);
    }

    /** The confirmation open over the list. */
    public Ask asking() {
        return asking;
    }

    /** The line under the cards, such as why a slot did not open; empty for none. */
    public String notice() {
        return notice;
    }

    public void notice(String message) {
        notice = message == null ? "" : message;
    }

    /**
     * Keys: 1-3, the arrows or Tab choose a slot; Enter loads it or starts the new world in it;
     * R restores a damaged slot's backup; Delete or Backspace deletes a slot; Escape returns. While a
     * confirmation is open Escape or N cancels it first, so a mixed press never confirms, and Enter or
     * Y confirms.
     */
    public Action handleKeys(Input input) {
        if (asking != Ask.NONE) {
            if (input.wasKeyPressed(GLFW_KEY_ESCAPE) || input.wasKeyPressed(GLFW_KEY_N)) {
                asking = Ask.NONE;
                return Action.NONE;
            }
            if (input.wasKeyPressed(GLFW_KEY_ENTER) || input.wasKeyPressed(GLFW_KEY_Y)) {
                return confirm();
            }
            return Action.NONE;
        }
        for (int n = 1; n <= slots.size(); n++) {
            if (input.wasKeyPressed(GLFW_KEY_0 + n) || input.wasKeyPressed(GLFW_KEY_KP_0 + n)) {
                select(n);
            }
        }
        if (input.wasKeyPressed(GLFW_KEY_UP)) {
            select(selected == 1 ? slots.size() : selected - 1);
        }
        if (input.wasKeyPressed(GLFW_KEY_DOWN) || input.wasKeyPressed(GLFW_KEY_TAB)) {
            select(selected == slots.size() ? 1 : selected + 1);
        }
        if (input.wasKeyPressed(GLFW_KEY_ESCAPE)) {
            return Action.BACK;
        }
        if (input.wasKeyPressed(GLFW_KEY_ENTER)) {
            return choose();
        }
        if (purpose == Purpose.LOAD && input.wasKeyPressed(GLFW_KEY_R)) {
            askRestore();
        }
        if (input.wasKeyPressed(GLFW_KEY_DELETE) || input.wasKeyPressed(GLFW_KEY_BACKSPACE)) {
            askDelete();
        }
        return Action.NONE;
    }

    private void select(int slot) {
        int chosen = Math.max(1, Math.min(slot, slots.size()));
        if (chosen != selected) {
            notice = "";
        }
        selected = chosen;
    }

    /** Enter or a click on the selected card: what it means depends on the slot and the purpose. */
    private Action choose() {
        SaveSlots.Slot slot = selectedSlot();
        if (purpose == Purpose.NEW) {
            if (slot.state() == SaveSlots.State.EMPTY) {
                return Action.CHOOSE;
            }
            asking = Ask.REPLACE;
            return Action.NONE;
        }
        switch (slot.state()) {
            case READY -> {
                return Action.CHOOSE;
            }
            case EMPTY -> notice = "Slot " + slot.number() + " is empty.";
            case OTHER_VERSION -> notice = slot.current().problem().message();
            case DAMAGED -> notice = slot.backup() != null
                    ? "Slot " + slot.number() + " is damaged. Press R to restore its backup."
                    : "Slot " + slot.number() + " is damaged, and no backup of it can be restored.";
        }
        return Action.NONE;
    }

    private void askRestore() {
        SaveSlots.Slot slot = selectedSlot();
        if (slot.state() == SaveSlots.State.DAMAGED && slot.backup() != null) {
            asking = Ask.RESTORE;
        } else if (slot.state() == SaveSlots.State.DAMAGED) {
            notice = "No backup of slot " + slot.number() + " can be restored.";
        } else {
            notice = "Only a damaged slot is restored from its backup.";
        }
    }

    private void askDelete() {
        if (selectedSlot().state() == SaveSlots.State.EMPTY) {
            notice = "Slot " + selected + " is empty.";
        } else {
            asking = Ask.DELETE;
        }
    }

    private Action confirm() {
        Ask answered = asking;
        asking = Ask.NONE;
        return switch (answered) {
            case REPLACE -> Action.CHOOSE;
            case DELETE -> Action.DELETE;
            case RESTORE -> Action.RESTORE;
            case NONE -> Action.NONE;
        };
    }

    // ------------------------------------------------------------------
    // Words
    // ------------------------------------------------------------------

    /** The confirmation's heading, such as "REPLACE SLOT 2?"; null when none is open. */
    public String confirmationTitle() {
        return switch (asking) {
            case NONE -> null;
            case REPLACE -> "REPLACE SLOT " + selected + "?";
            case DELETE -> "DELETE SLOT " + selected + "?";
            case RESTORE -> "RESTORE SLOT " + selected + "?";
        };
    }

    /** The confirmation's lines, naming what the slot holds; null when none is open. */
    public String[] confirmation(ZoneId zone) {
        SaveSlots.Slot slot = selectedSlot();
        return switch (asking) {
            case NONE -> null;
            case REPLACE -> with(holds(slot, zone), "Starting the new world here replaces it.");
            case DELETE -> with(holds(slot, zone), "Its save and its backups are deleted. This cannot be undone.");
            case RESTORE -> restoring(slot, zone);
        };
    }

    private static String[] holds(SaveSlots.Slot slot, ZoneId zone) {
        return switch (slot.state()) {
            case READY -> new String[]{"It holds: " + worldLine(slot.current().generator(), slot.summary()),
                    timeLine(slot.summary()) + " · saved " + when(slot.summary().savedAt(), zone)};
            case OTHER_VERSION -> new String[]{"It holds a save from another version of VEYLON."};
            case DAMAGED -> new String[]{slot.currentExists() ? "It holds a damaged save."
                    : "Its save is missing; only its backups are left."};
            case EMPTY -> new String[0];
        };
    }

    private static String[] restoring(SaveSlots.Slot slot, ZoneId zone) {
        SaveSummary backup = slot.backup().summary();
        return new String[]{"From its backup of " + when(backup.savedAt(), zone) + ":",
                worldLine(slot.backup().inspection().generator(), backup) + " · " + timeLine(backup),
                slot.currentExists() ? "The damaged save is set aside in the saves folder, not deleted."
                        : "The backup becomes the slot's save again."};
    }

    /** The lines a slot's card shows under its name. */
    public String[] lines(SaveSlots.Slot slot, ZoneId zone) {
        return switch (slot.state()) {
            case READY -> new String[]{worldLine(slot.current().generator(), slot.summary()), timeLine(slot.summary()),
                    slot.summary().place(), savedLine(slot.summary(), zone)};
            case EMPTY -> new String[]{purpose == Purpose.NEW ? "A new world can start here." : "Nothing is saved here."};
            case OTHER_VERSION -> new String[]{slot.current().problem().message(), "The file is left as it is."};
            case DAMAGED -> damaged(slot, zone);
        };
    }

    private static String[] damaged(SaveSlots.Slot slot, ZoneId zone) {
        String what = slot.currentExists() ? "This save is damaged and cannot be opened." : "This slot's save is missing.";
        if (slot.backup() == null) {
            return new String[]{what, "No backup of it can be restored."};
        }
        SaveSummary backup = slot.backup().summary();
        return new String[]{what, "Its backup from " + when(backup.savedAt(), zone) + " can be restored with R:",
                worldLine(slot.backup().inspection().generator(), backup) + " · " + timeLine(backup)};
    }

    /** The tag at a card's right edge. */
    public static String tag(SaveSlots.Slot slot) {
        return switch (slot.state()) {
            case READY -> "READY";
            case EMPTY -> "EMPTY";
            case OTHER_VERSION -> "ANOTHER VERSION";
            case DAMAGED -> slot.backup() != null ? "DAMAGED  /  BACKUP AVAILABLE" : "DAMAGED";
        };
    }

    /** "The abandoned city · Survival": the world kind from the generator, the mode and the mark. */
    public static String worldLine(int generator, SaveSummary summary) {
        String world = World.isUrbanGenerator(generator) ? "The abandoned city" : "The wilderness";
        String mode = summary.mode() == GameMode.CREATIVE ? "Creative"
                : summary.creativeMarked() ? "Survival, a Creative world" : "Survival";
        return world + " · " + mode;
    }

    /** "Day 4, 13:20 · played 1 h 05 min". */
    public static String timeLine(SaveSummary summary) {
        return String.format(Locale.ROOT, "Day %d, %02d:%02d · played %s", summary.day(), summary.hour(),
                summary.minute(), duration(summary.playSeconds()));
    }

    /** "Saved 20 Nov 2026, 14:32 · VEYLON 0.9.1, save epoch 1". */
    public static String savedLine(SaveSummary summary, ZoneId zone) {
        String build = SaveStamp.DEVELOPMENT.equals(summary.version()) ? "a development build"
                : "VEYLON " + summary.version();
        return "Saved " + when(summary.savedAt(), zone) + " · " + build + ", save epoch " + SaveHeader.EPOCH;
    }

    /** Real play time in the player's words. */
    public static String duration(double seconds) {
        long minutes = (long) Math.floor(seconds / 60);
        if (minutes < 1) {
            return "under a minute";
        }
        if (minutes < 60) {
            return minutes + " min";
        }
        return String.format(Locale.ROOT, "%d h %02d min", minutes / 60, minutes % 60);
    }

    /** A wall-clock time in the player's time zone. */
    public static String when(long millis, ZoneId zone) {
        return WHEN.format(Instant.ofEpochMilli(millis).atZone(zone));
    }

    /** Every line the picker can draw for these slots: for the tests that check the lines fit. */
    List<String> everyLine(ZoneId zone) {
        List<String> all = new ArrayList<>();
        Ask was = asking;
        int at = selected;
        for (SaveSlots.Slot slot : slots) {
            all.addAll(List.of(lines(slot, zone)));
            selected = slot.number();
            for (Ask ask : Ask.values()) {
                boolean shown = ask == Ask.REPLACE || ask == Ask.DELETE && slot.state() != SaveSlots.State.EMPTY
                        || ask == Ask.RESTORE && slot.backup() != null;
                if (ask != Ask.NONE && shown) {
                    asking = ask;
                    all.addAll(List.of(confirmation(zone)));
                }
            }
        }
        asking = was;
        selected = at;
        return all;
    }

    private static String[] with(String[] lines, String last) {
        String[] out = java.util.Arrays.copyOf(lines, lines.length + 1);
        out[lines.length] = last;
        return out;
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    /** Draws the picker in the title's visual language and returns the mouse or key action. */
    public Action update(Game g) {
        UiRenderer ui = g.ui;
        int w = ui.screenW(), h = ui.screenH();
        ZoneId zone = ZoneId.systemDefault();
        ui.rect(0, 0, w, h, 0.015f, 0.022f, 0.035f, 1f);
        // The title, the cards and the buttons stand about 650 units tall: centred on a taller
        // canvas, near the top of the 720 rows UiRenderer always gives.
        float top = Math.max(30, (h - CONTENT_H) / 2f);
        ui.textCentered(w / 2f, top, 2.6f, purpose == Purpose.NEW ? "NEW FRONTIER" : "LOAD FRONTIER",
                0.80f, 0.94f, 0.95f, 1f);
        ui.textCentered(w / 2f, top + 40, 1.2f, purpose == Purpose.NEW
                        ? "Choose the slot this world is saved in." : "Choose a slot to load.",
                0.46f, 0.62f, 0.66f, 1f);

        double mx = g.input.cursorX(), my = g.input.cursorY();
        boolean click = g.input.wasMousePressed(GLFW_MOUSE_BUTTON_LEFT);
        boolean modal = asking != Ask.NONE;
        Action action = Action.NONE;
        float cardW = Math.min(CARD_MAX_W, w - 96), x = (w - cardW) / 2f, y = top + 76;
        for (SaveSlots.Slot slot : slots) {
            boolean hover = !modal && inside(mx, my, x, y, cardW, CARD_H);
            if (hover && click) {
                if (slot.number() == selected) {
                    action = choose();
                } else {
                    select(slot.number());
                }
            }
            drawCard(ui, x, y, cardW, slot, slot.number() == selected, hover, zone);
            y += CARD_H + CARD_GAP;
        }

        if (!notice.isBlank()) {
            ui.textCentered(w / 2f, y + 2, 1.2f, notice, 1f, 0.65f, 0.42f, 1f);
        }
        if (legacySave) {
            ui.textCentered(w / 2f, y + 26, 1.0f, LEGACY_NOTE, 0.55f, 0.66f, 0.70f, 1f);
        }
        float buttonY = y + 56;
        Action pressed = buttons(ui, w, buttonY, mx, my, click && !modal);
        if (action == Action.NONE) {
            action = pressed;
        }
        ui.textCentered(w / 2f, h - 34, 1.05f, purpose == Purpose.NEW ? NEW_KEYS : LOAD_KEYS,
                0.42f, 0.50f, 0.56f, 1f);

        if (modal) {
            Action answered = drawConfirmation(ui, w, h, mx, my, click, zone);
            if (answered != Action.NONE || asking == Ask.NONE) {
                return answered;
            }
        }
        return action != Action.NONE ? action : handleKeys(g.input);
    }

    private void drawCard(UiRenderer ui, float x, float y, float w, SaveSlots.Slot slot, boolean active,
                          boolean hover, ZoneId zone) {
        ui.rect(x, y, w, CARD_H, active ? 0.10f : 0.045f, active ? 0.28f : 0.07f, active ? 0.30f : 0.10f, 0.96f);
        ui.rectOutline(x, y, w, CARD_H, active ? 2 : 1, active ? 0.30f : 0.20f,
                active || hover ? 0.82f : 0.34f, active || hover ? 0.84f : 0.38f, 0.9f);
        ui.text(x + CARD_PAD, y + 9, 1.5f, "SLOT " + slot.number(), 0.80f, 0.94f, 0.95f, 1f);
        String tag = tag(slot);
        float[] tint = switch (slot.state()) {
            case READY -> new float[]{0.55f, 0.88f, 0.90f};
            case EMPTY -> new float[]{0.50f, 0.58f, 0.62f};
            case OTHER_VERSION, DAMAGED -> new float[]{1f, 0.65f, 0.42f};
        };
        ui.text(x + w - CARD_PAD - ui.textWidth(tag, 1.1f), y + 13, 1.1f, tag, tint[0], tint[1], tint[2], 1f);
        String[] lines = lines(slot, zone);
        float dim = active ? 1f : 0.78f;
        float room = w - 2 * CARD_PAD;
        for (int i = 0; i < lines.length; i++) {
            // A place name is the one line whose length the picker does not choose, so cut it to fit.
            ui.text(x + CARD_PAD, y + 38 + i * LINE_STEP, LINE_SCALE,
                    HudLayout.fit(lines[i], room, s -> ui.textWidth(s, LINE_SCALE)),
                    0.90f * dim, 0.96f * dim, 0.97f * dim, 1f);
        }
    }

    /** The row of buttons under the cards; a button that does nothing for the selected slot is dim. */
    private Action buttons(UiRenderer ui, int w, float y, double mx, double my, boolean click) {
        SaveSlots.Slot slot = selectedSlot();
        List<String> labels = new ArrayList<>();
        List<Boolean> enabled = new ArrayList<>();
        labels.add(purpose == Purpose.NEW ? "START HERE [Enter]" : "LOAD [Enter]");
        enabled.add(purpose == Purpose.NEW || slot.state() == SaveSlots.State.READY);
        if (purpose == Purpose.LOAD) {
            labels.add("RESTORE [R]");
            enabled.add(slot.state() == SaveSlots.State.DAMAGED && slot.backup() != null);
        }
        labels.add("DELETE [Del]");
        enabled.add(slot.state() != SaveSlots.State.EMPTY);
        labels.add("BACK [Esc]");
        enabled.add(true);

        float total = labels.size() * BUTTON_W + (labels.size() - 1) * BUTTON_GAP;
        float bx = (w - total) / 2f;
        Action action = Action.NONE;
        for (int i = 0; i < labels.size(); i++) {
            boolean hover = inside(mx, my, bx, y, BUTTON_W, BUTTON_H);
            button(ui, bx, y, BUTTON_W, labels.get(i), hover && enabled.get(i), i == 0, enabled.get(i));
            if (hover && click && enabled.get(i)) {
                String label = labels.get(i);
                if (label.startsWith("BACK")) {
                    action = Action.BACK;
                } else if (label.startsWith("DELETE")) {
                    askDelete();
                } else if (label.startsWith("RESTORE")) {
                    askRestore();
                } else {
                    action = choose();
                }
            }
            bx += BUTTON_W + BUTTON_GAP;
        }
        return action;
    }

    private Action drawConfirmation(UiRenderer ui, int w, int h, double mx, double my, boolean click, ZoneId zone) {
        ui.rect(0, 0, w, h, 0, 0, 0, 0.62f);
        float pw = Math.min(PANEL_MAX_W, w - 36);
        float x0 = (w - pw) / 2f, y0 = (h - PANEL_H) / 2f;
        ui.panel(x0, y0, pw, PANEL_H);
        ui.textCentered(w / 2f, y0 + 18, 2.0f, confirmationTitle(), 0.78f, 0.96f, 0.98f, 1f);
        float lineY = y0 + 66;
        for (String line : confirmation(zone)) {
            ui.textCentered(w / 2f, lineY, 1.2f, line, 0.90f, 0.96f, 0.97f, 1f);
            lineY += 28;
        }
        String yes = switch (asking) {
            case REPLACE -> "REPLACE [Y]";
            case DELETE -> "DELETE [Y]";
            default -> "RESTORE [Y]";
        };
        String no = asking == Ask.RESTORE ? "CANCEL [N]" : "KEEP [N]";
        float buttonY = y0 + PANEL_H - 80;
        float yesX = w / 2f - BUTTON_W - 8, noX = w / 2f + 8;
        boolean yesHover = inside(mx, my, yesX, buttonY, BUTTON_W, BUTTON_H);
        boolean noHover = inside(mx, my, noX, buttonY, BUTTON_W, BUTTON_H);
        button(ui, yesX, buttonY, BUTTON_W, yes, yesHover, true, true);
        button(ui, noX, buttonY, BUTTON_W, no, noHover, false, true);
        ui.textCentered(w / 2f, y0 + PANEL_H - 34, 1.05f, "Y or Enter confirms     N or Esc cancels",
                0.58f, 0.70f, 0.73f, 1f);
        if (click && noHover) {
            asking = Ask.NONE;
            return Action.NONE;
        }
        if (click && yesHover) {
            return confirm();
        }
        return Action.NONE;
    }

    private static void button(UiRenderer ui, float x, float y, float width, String text, boolean hover,
                               boolean primary, boolean enabled) {
        float fade = enabled ? 1f : 0.4f;
        ui.rect(x, y, width, BUTTON_H, 0.08f * fade, (primary ? (hover ? 0.40f : 0.28f) : 0.12f) * fade,
                (primary ? 0.30f : 0.15f) * fade, 0.98f);
        ui.rectOutline(x, y, width, BUTTON_H, 1, 0.28f * fade, (hover ? 0.92f : 0.56f) * fade,
                (hover ? 0.94f : 0.60f) * fade, 0.9f);
        ui.textCentered(x + width / 2, y + 10, 1.2f, text, 0.90f * fade, 0.96f * fade, 0.97f * fade, 1);
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
