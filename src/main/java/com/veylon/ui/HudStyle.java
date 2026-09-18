package com.veylon.ui;

import com.veylon.engine.UiRenderer;
import com.veylon.gfx.FontRenderer;

/** HUD-only palette and type scale; other screens retain their existing presentation. */
final class HudStyle {
    static final int TEXT = 0xd8d4c8, MUTED = 0xa8b4bb, CYAN = 0x39c0c8, TEAL = 0x1e8f96;
    static final int AMBER = 0xd9ac66, RED = 0x8f3434, RED_TEXT = 0xe3aaaa;
    static final int NAVY = 0x0e1721, LINE = 0x536574, GREEN = 0x789776;
    static final float TITLE = 1.7f, BODY = 1.6f, SMALL = 1.4f, MICRO = 1.25f;
    // The cyan rivets occupy source pixels 3..5 and 18..20 of the 24px skin.
    // Keep them wholly inside the corners instead of stretching through a tall panel.
    static final float PANEL_CORNER = 7;

    private HudStyle() { }

    static float r(int c) { return ((c >> 16) & 255) / 255f; }
    static float g(int c) { return ((c >> 8) & 255) / 255f; }
    static float b(int c) { return (c & 255) / 255f; }

    static void rect(UiRenderer ui, float x, float y, float w, float h, int color, float alpha) {
        ui.rect(x, y, w, h, r(color), g(color), b(color), alpha);
    }

    static void outline(UiRenderer ui, HudLayout.Rect box, float thickness, int color) {
        ui.rectOutline(box.x(), box.y(), box.width(), box.height(), thickness,
                r(color), g(color), b(color), 1);
    }

    static float width(UiRenderer ui, String text, float scale) {
        return ui.fontRenderer().textWidth(text, scale, FontRenderer.Weight.SEMIBOLD);
    }

    static void text(UiRenderer ui, float x, float y, float scale, String text, int color) {
        ui.textSemibold(x, y, scale, text, r(color), g(color), b(color), 1);
    }

    static void fitted(UiRenderer ui, float x, float y, float maxWidth, float scale,
                       String label, int color) {
        text(ui, x, y, scale, HudLayout.fit(label, maxWidth, s -> width(ui, s, scale)), color);
    }

    static void centered(UiRenderer ui, HudLayout.Rect box, float y, float scale, String label, int color) {
        String fit = HudLayout.fit(label, box.width() - HudLayout.PAD * 2, s -> width(ui, s, scale));
        text(ui, box.centerX() - width(ui, fit, scale) / 2, y, scale, fit, color);
    }
}
