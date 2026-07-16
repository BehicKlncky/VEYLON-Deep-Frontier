package com.veylon.ui;

import com.veylon.engine.UiRenderer;

/** Full-screen product-state cards shared by loading, death, and victory flow. */
public final class PresentationOverlay {

    private PresentationOverlay() {
    }

    public static void loading(UiRenderer ui, String detail, double time) {
        int w = ui.screenW(), h = ui.screenH();
        ui.rect(0, 0, w, h, 0.012f, 0.018f, 0.028f, 1f);
        ui.textCentered(w / 2f, h / 2f - 46, 2.2f, "PREPARING THE FRONTIER",
                0.76f, 0.92f, 0.94f, 1f);
        float trackW = Math.min(420, w * 0.58f);
        float x = w / 2f - trackW / 2f;
        ui.rect(x, h / 2f, trackW, 5, 0.04f, 0.12f, 0.14f, 1f);
        float pulse = 0.18f + 0.12f * (float) Math.sin(time * 3.0);
        float px = (float) ((time * 92) % (trackW + 70)) - 70;
        ui.rect(x + px, h / 2f, 70, 5, 0.20f, 0.72f, 0.76f, 0.72f + pulse);
        ui.textCentered(w / 2f, h / 2f + 24, 1.15f,
                detail == null ? "Mapping atmosphere and terrain..." : detail,
                0.55f, 0.64f, 0.68f, 1f);
    }

    public static void death(UiRenderer ui, float secondsRemaining) {
        int w = ui.screenW(), h = ui.screenH();
        ui.rect(0, 0, w, h, 0.08f, 0.01f, 0.015f, 0.72f);
        ui.textCentered(w / 2f, h / 2f - 54, 2.8f, "THE FRONTIER CLAIMS YOU",
                0.88f, 0.52f, 0.48f, 1f);
        ui.textCentered(w / 2f, h / 2f, 1.35f,
                "Your emergency transponder recalls you to the crash site.",
                0.86f, 0.82f, 0.78f, 1f);
        ui.textCentered(w / 2f, h / 2f + 28, 1.1f,
                "Respawn in " + Math.max(0, (int) Math.ceil(secondsRemaining)) + "  /  Enter to continue",
                0.62f, 0.64f, 0.68f, 1f);
    }

    public static void victory(UiRenderer ui, double time) {
        int w = ui.screenW(), h = ui.screenH();
        ui.rect(0, 0, w, h, 0.005f, 0.035f, 0.045f, 0.88f);
        float beam = 0.72f + 0.18f * (float) Math.sin(time * 2.4);
        ui.rect(w / 2f - 2, h * 0.10f, 4, h * 0.38f, 0.22f, 0.85f, 0.90f, beam);
        ui.textCentered(w / 2f, h / 2f - 34, 2.7f, "SIGNAL ACQUIRED",
                0.54f, 0.96f, 0.98f, 1f);
        ui.textCentered(w / 2f, h / 2f + 8, 1.45f,
                "The beacon cuts through Veylon's sky. Rescue is inbound.",
                0.82f, 0.90f, 0.90f, 1f);
        ui.textCentered(w / 2f, h / 2f + 42, 1.12f,
                "Enter: continue surviving     Esc: return to title",
                0.52f, 0.64f, 0.68f, 1f);
    }
}
