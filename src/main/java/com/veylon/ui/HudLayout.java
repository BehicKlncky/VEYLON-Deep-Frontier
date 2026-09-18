package com.veylon.ui;

import java.util.function.ToDoubleFunction;

/** Logical-pixel geometry and bounded text for the gameplay HUD, independent of GL. */
public final class HudLayout {
    public static final float MARGIN = 20, PAD = 16, GAP = 10;
    public static final float STATUS_WIDTH = 312, BAR_WIDTH = STATUS_WIDTH - PAD * 2;
    public static final float BAR_HEIGHT = 18, VITAL_TOP = 36, VITAL_PITCH = 40;
    public static final float VITAL_ICON = 18, VITAL_LABEL_X = 24, VITAL_METER_Y = 19;
    public static final float NUTRITION_TOP = 204, TELEMETRY_TOP = 238, TELEMETRY_PITCH = 22;
    public static final float CHIP_TOP = 312, CHIP_HEIGHT = 34, CHIP_PITCH = 38;
    public static final int SLOT_COUNT = 9, MAX_LOG_LINES = 6;
    public static final float SLOT_SIZE = 56, SLOT_GAP = 6, SELECTED_LIFT = 6;
    public static final float HOTBAR_WIDTH = SLOT_COUNT * SLOT_SIZE + (SLOT_COUNT - 1) * SLOT_GAP;
    public static final float ITEM_ICON = 42, CONDITION_HEIGHT = 5, LOG_LINE = 20;
    public static final float HELD_HEIGHT = 28, WEAPON_HEIGHT = 64, WEAPON_METER_X = 172;
    public static final float CONTEXT_HEIGHT = 120, MISSION_WIDTH = 420, MISSION_HEIGHT = 152;
    public static final float TARGET_WIDTH = 300, TARGET_HEIGHT = 40, TARGET_OFFSET = 26;
    public static final float PROMPT_WIDTH = 440, PROMPT_HEIGHT = 36;

    public record Rect(float x, float y, float width, float height) {
        public float right() { return x + width; }
        public float bottom() { return y + height; }
        public float centerX() { return x + width / 2; }
        public boolean overlaps(Rect other) {
            return x < other.right() && right() > other.x
                    && y < other.bottom() && bottom() > other.y;
        }
    }

    public final Rect status, hotbar, heldItem, weapon, context, mission, eventLog, target, prompt;
    public final boolean creative;
    public final int logLines;

    public HudLayout(int width, int height, boolean creative, int afflictionCount) {
        this.creative = creative;
        int chipRows = creative ? 1 : Math.max(1, (afflictionCount + 1) / 2);
        float statusHeight = CHIP_TOP + chipRows * CHIP_PITCH + PAD;
        status = new Rect(MARGIN, height - MARGIN - statusHeight, STATUS_WIDTH, statusHeight);
        float hotbarX = Math.max((width - HOTBAR_WIDTH) / 2, status.right() + GAP * 2);
        hotbar = new Rect(hotbarX, height - MARGIN - SLOT_SIZE - SELECTED_LIFT,
                HOTBAR_WIDTH, SLOT_SIZE + SELECTED_LIFT);
        heldItem = new Rect(hotbarX, hotbar.y - GAP - HELD_HEIGHT, HOTBAR_WIDTH, HELD_HEIGHT);
        weapon = new Rect(hotbarX, heldItem.y - GAP - WEAPON_HEIGHT, HOTBAR_WIDTH, WEAPON_HEIGHT);
        context = new Rect(MARGIN, MARGIN, STATUS_WIDTH, CONTEXT_HEIGHT);
        float missionWidth = Math.min(MISSION_WIDTH, width - context.right() - MARGIN - GAP * 2);
        mission = new Rect(width - MARGIN - missionWidth, MARGIN, missionWidth, MISSION_HEIGHT);
        logLines = Math.max(0, Math.min(MAX_LOG_LINES,
                (int) ((status.y - GAP - context.bottom() - GAP - PAD) / LOG_LINE)));
        float logHeight = logLines * LOG_LINE + PAD;
        eventLog = new Rect(MARGIN, status.y - GAP - logHeight, STATUS_WIDTH, logHeight);
        target = new Rect((width - TARGET_WIDTH) / 2, height / 2f + TARGET_OFFSET, TARGET_WIDTH, TARGET_HEIGHT);
        prompt = new Rect((width - PROMPT_WIDTH) / 2, target.bottom() + GAP, PROMPT_WIDTH, PROMPT_HEIGHT);
    }

    public Rect slot(int index, boolean selected) {
        return new Rect(hotbar.x + index * (SLOT_SIZE + SLOT_GAP),
                hotbar.y + (selected ? 0 : SELECTED_LIFT), SLOT_SIZE, SLOT_SIZE);
    }

    /** Single-line ellipsis measured using the same font weight/size as the eventual draw. */
    public static String fit(String text, float width, ToDoubleFunction<String> measure) {
        if (text == null || width <= 0) return "";
        String line = text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        if (measure.applyAsDouble(line) <= width) return line;
        String ellipsis = "…";
        if (measure.applyAsDouble(ellipsis) > width) return "";
        int[] points = line.codePoints().toArray();
        int lo = 0, hi = points.length;
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            String candidate = new String(points, 0, mid).stripTrailing() + ellipsis;
            if (measure.applyAsDouble(candidate) <= width) lo = mid;
            else hi = mid - 1;
        }
        return new String(points, 0, lo).stripTrailing() + ellipsis;
    }

    /** State words are also rendered, so a warning never depends on hue alone. */
    public static String vitalState(float fraction) {
        return fraction <= 0.1f ? "CRITICAL" : fraction <= 0.25f ? "LOW" : "";
    }
}
