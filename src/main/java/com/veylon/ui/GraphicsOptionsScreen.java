package com.veylon.ui;

import com.veylon.Game;
import com.veylon.engine.UiRenderer;
import com.veylon.gfx.GraphicsSettings;

import static org.lwjgl.glfw.GLFW.*;

/** Keyboard/mouse graphics options editor backed by the renderer's one settings object. */
public final class GraphicsOptionsScreen {

    public enum Action { NONE, APPLY, CANCEL }

    public record Result(Action action, int width, int height) {
        private static final Result NONE = new Result(Action.NONE, 0, 0);
    }

    private static final int[][] RESOLUTIONS = {
            {1280, 720}, {1600, 900}, {1920, 1080}, {2560, 1440}
    };
    private static final String[] LABELS = {
            "Resolution", "Fullscreen", "VSync", "Field of view", "UI scale",
            "Render distance", "Shadows", "Bloom", "Edge smoothing", "Particles",
            "Motion intensity"
    };

    private int selected;
    private int resolution;
    private boolean opened;
    private Snapshot original;

    public void open(GraphicsSettings s, int width, int height) {
        original = new Snapshot(s);
        resolution = nearestResolution(width, height);
        selected = 0;
        opened = true;
    }

    public Result update(Game g) {
        GraphicsSettings s = g.renderer.settings;
        if (!opened) open(s, g.window.width(), g.window.height());

        UiRenderer ui = g.ui;
        int w = ui.screenW(), h = ui.screenH();
        ui.rect(0, 0, w, h, 0, 0, 0, 0.62f);
        float pw = Math.min(680, w - 36), ph = Math.min(570, h - 30);
        float x0 = w / 2f - pw / 2f, y0 = h / 2f - ph / 2f;
        ui.panel(x0, y0, pw, ph);
        ui.textCentered(w / 2f, y0 + 18, 2.15f, "GRAPHICS",
                0.78f, 0.96f, 0.98f, 1f);
        ui.textCentered(w / 2f, y0 + 48, 1.08f,
                "Changes apply immediately and persist for the next launch",
                0.50f, 0.60f, 0.64f, 1f);

        if (g.input.wasKeyPressed(GLFW_KEY_UP) || g.input.wasKeyPressed(GLFW_KEY_W)) {
            selected = Math.floorMod(selected - 1, LABELS.length);
            g.audio.playClick();
        }
        if (g.input.wasKeyPressed(GLFW_KEY_DOWN) || g.input.wasKeyPressed(GLFW_KEY_S)) {
            selected = Math.floorMod(selected + 1, LABELS.length);
            g.audio.playClick();
        }

        double mx = g.input.cursorX(), my = g.input.cursorY();
        boolean clicked = g.input.wasMousePressed(GLFW_MOUSE_BUTTON_LEFT);
        float rowX = x0 + 54, rowW = pw - 108, rowY = y0 + 80, rowH = 31;
        for (int i = 0; i < LABELS.length; i++) {
            float y = rowY + i * rowH;
            boolean hover = mx >= rowX && mx <= rowX + rowW && my >= y && my <= y + rowH - 3;
            if (hover) selected = i;
            if (i == selected) {
                ui.rect(rowX, y, rowW, rowH - 3, 0.08f, 0.22f, 0.24f, 0.88f);
                ui.rect(rowX, y, 3, rowH - 3, 0.28f, 0.84f, 0.87f, 1f);
            }
            ui.textShadow(rowX + 14, y + 7, 1.28f, LABELS[i],
                    i == selected ? 0.90f : 0.66f, i == selected ? 0.97f : 0.72f,
                    i == selected ? 0.98f : 0.76f, 1f);
            String value = valueOf(i, s);
            ui.textShadow(rowX + rowW - ui.textWidth(value, 1.28f) - 14, y + 7, 1.28f,
                    value, 0.96f, 0.78f, 0.42f, 1f);
            if (hover && clicked) adjust(i, s, 1);
        }

        ui.textCentered(w / 2f, y0 + ph - 92, 1.05f,
                "Türkçe: çözünürlük, görüş, ışık, gölge - Çç Ğğ İı Öö Şş Üü",
                0.58f, 0.70f, 0.73f, 1f);

        int delta = 0;
        if (g.input.wasKeyPressed(GLFW_KEY_LEFT) || g.input.wasKeyPressed(GLFW_KEY_A)) delta = -1;
        if (g.input.wasKeyPressed(GLFW_KEY_RIGHT) || g.input.wasKeyPressed(GLFW_KEY_D)) delta = 1;
        if (g.input.wasKeyPressed(GLFW_KEY_ENTER) || g.input.wasKeyPressed(GLFW_KEY_SPACE)) delta = 1;
        if (delta != 0) {
            adjust(selected, s, delta);
            g.audio.playClick();
        }

        float buttonY = y0 + ph - 58, buttonW = 190, buttonH = 36;
        float applyX = w / 2f - buttonW - 8, cancelX = w / 2f + 8;
        boolean applyHover = inside(mx, my, applyX, buttonY, buttonW, buttonH);
        boolean cancelHover = inside(mx, my, cancelX, buttonY, buttonW, buttonH);
        button(ui, applyX, buttonY, buttonW, buttonH, "APPLY [F5]", applyHover, true);
        button(ui, cancelX, buttonY, buttonW, buttonH, "BACK [Esc]", cancelHover, false);

        if (g.input.wasKeyPressed(GLFW_KEY_F5) || (clicked && applyHover)) {
            opened = false;
            int[] r = RESOLUTIONS[resolution];
            return new Result(Action.APPLY, r[0], r[1]);
        }
        if (g.input.wasKeyPressed(GLFW_KEY_ESCAPE) || (clicked && cancelHover)) {
            original.restore(s);
            opened = false;
            int[] r = RESOLUTIONS[nearestResolution(g.window.width(), g.window.height())];
            return new Result(Action.CANCEL, r[0], r[1]);
        }
        return Result.NONE;
    }

    private void adjust(int row, GraphicsSettings s, int d) {
        switch (row) {
            case 0 -> resolution = Math.floorMod(resolution + d, RESOLUTIONS.length);
            case 1 -> s.fullscreen = !s.fullscreen;
            case 2 -> s.vsync = !s.vsync;
            case 3 -> s.fov = clamp(s.fov + d * 5, 60, 100);
            case 4 -> s.uiScale = clamp(s.uiScale + d * 0.25f, 0.75f, 1.5f);
            case 5 -> s.renderDistance = (int) clamp(s.renderDistance + d, 4, 10);
            case 6 -> s.shadowQuality = Math.floorMod(s.shadowQuality + d, 3);
            case 7 -> s.bloom = !s.bloom;
            case 8 -> s.fxaa = !s.fxaa;
            case 9 -> s.particleDensity = clamp(s.particleDensity + d * 0.25f, 0, 1);
            case 10 -> s.motion = clamp(s.motion + d * 0.25f, 0, 1);
            default -> { }
        }
    }

    private String valueOf(int row, GraphicsSettings s) {
        return switch (row) {
            case 0 -> RESOLUTIONS[resolution][0] + " x " + RESOLUTIONS[resolution][1];
            case 1 -> onOff(s.fullscreen);
            case 2 -> onOff(s.vsync);
            case 3 -> (int) s.fov + " deg";
            case 4 -> Math.round(s.uiScale * 100) + "%";
            case 5 -> s.renderDistance + " chunks";
            case 6 -> switch (s.shadowQuality) { case 0 -> "Off"; case 1 -> "Medium"; default -> "High"; };
            case 7 -> onOff(s.bloom);
            case 8 -> onOff(s.fxaa);
            case 9 -> s.particleDensity <= 0 ? "Off" : Math.round(s.particleDensity * 100) + "%";
            case 10 -> Math.round(s.motion * 100) + "%";
            default -> "";
        };
    }

    private static void button(UiRenderer ui, float x, float y, float w, float h,
                               String label, boolean hover, boolean primary) {
        ui.rect(x, y, w, h, primary ? 0.08f : 0.07f,
                primary ? (hover ? 0.40f : 0.28f) : (hover ? 0.22f : 0.12f),
                primary ? (hover ? 0.42f : 0.30f) : (hover ? 0.24f : 0.15f), 0.98f);
        ui.rectOutline(x, y, w, h, 1, hover ? 0.45f : 0.28f,
                hover ? 0.92f : 0.56f, hover ? 0.94f : 0.60f, 0.9f);
        ui.textCentered(x + w / 2f, y + 10, 1.28f, label, 0.90f, 0.96f, 0.97f, 1f);
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static String onOff(boolean v) { return v ? "On" : "Off"; }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int nearestResolution(int width, int height) {
        int best = 0;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < RESOLUTIONS.length; i++) {
            long dx = RESOLUTIONS[i][0] - width, dy = RESOLUTIONS[i][1] - height;
            long d = dx * dx + dy * dy;
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }

    private record Snapshot(int renderDistance, int shadowQuality, boolean bloom, boolean fxaa,
                            float particleDensity, float fov, float uiScale, boolean vsync,
                            boolean fullscreen, float motion, boolean crispTextures) {
        Snapshot(GraphicsSettings s) {
            this(s.renderDistance, s.shadowQuality, s.bloom, s.fxaa, s.particleDensity,
                    s.fov, s.uiScale, s.vsync, s.fullscreen, s.motion, s.crispTextures);
        }

        void restore(GraphicsSettings s) {
            s.renderDistance = renderDistance;
            s.shadowQuality = shadowQuality;
            s.bloom = bloom;
            s.fxaa = fxaa;
            s.particleDensity = particleDensity;
            s.fov = fov;
            s.uiScale = uiScale;
            s.vsync = vsync;
            s.fullscreen = fullscreen;
            s.motion = motion;
            s.crispTextures = crispTextures;
        }
    }
}
