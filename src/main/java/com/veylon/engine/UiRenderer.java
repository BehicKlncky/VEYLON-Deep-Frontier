package com.veylon.engine;

import com.veylon.gfx.FontRenderer;
import com.veylon.gfx.IconAtlas;
import com.veylon.item.ItemType;
import com.veylon.util.FloatList;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Ordered batched 2D renderer for HUD and menus. Solid geometry, Source Sans 3
 * glyphs and icon-atlas sprites share one vertex stream, so overlays retain the
 * exact call order expected by the existing screens while still drawing in one
 * UI call.
 */
public class UiRenderer {

    private static final float MODE_SOLID = 0f;
    private static final float MODE_FONT = 1f;
    private static final float MODE_ICON = 2f;

    private static final String VS = """
            #version 330 core
            layout(location=0) in vec2 aPos;
            layout(location=1) in vec2 aUV;
            layout(location=2) in vec4 aColor;
            layout(location=3) in float aMode;
            uniform vec2 uScreen;
            out vec2 vUV;
            out vec4 vColor;
            out float vMode;
            void main() {
                gl_Position = vec4(aPos.x / uScreen.x * 2.0 - 1.0, 1.0 - aPos.y / uScreen.y * 2.0, 0.0, 1.0);
                vUV = aUV;
                vColor = aColor;
                vMode = aMode;
            }
            """;

    private static final String FS = """
            #version 330 core
            in vec2 vUV;
            in vec4 vColor;
            in float vMode;
            uniform sampler2D uFontAtlas;
            uniform sampler2D uIconAtlas;
            out vec4 FragColor;
            void main() {
                vec4 sampleColor = vec4(1.0);
                if (vMode > 1.5) {
                    sampleColor = texture(uIconAtlas, vUV);
                } else if (vMode > 0.5) {
                    float coverage = texture(uFontAtlas, vUV).r;
                    sampleColor = vec4(1.0, 1.0, 1.0, coverage);
                }
                FragColor = vec4(sampleColor.rgb * vColor.rgb, sampleColor.a * vColor.a);
            }
            """;

    private ShaderProgram shader;
    private Mesh mesh;
    private final FloatList buf = new FloatList(1 << 14);
    private final FontRenderer font = new FontRenderer();
    private final IconAtlas icons = new IconAtlas();
    private int screenW, screenH;
    private int framebufferW, framebufferH;
    private float logicalW, logicalH;
    private float uiScale = 1f;
    private int drawCallsLastFrame;

    public void init() {
        shader = new ShaderProgram(VS, FS);
        mesh = new Mesh(new int[]{2, 2, 4, 1});
        font.init();
        icons.init();
    }

    /** Begins an unscaled UI frame; retained for existing callers. */
    public void begin(int w, int h) {
        begin(w, h, 1f);
    }

    /**
     * Begins a frame in logical pixels. A scale of 1.5 makes a 1280x720
     * framebuffer a roughly 853x480 logical canvas while preserving crisp
     * framebuffer output.
     */
    public void begin(int w, int h, float scale) {
        framebufferW = Math.max(1, w);
        framebufferH = Math.max(1, h);
        float requested = Math.max(0.5f, Math.min(2f, scale));
        // Existing screens are authored for a 720 logical-pixel safe height.
        // Clamp enlargement at low resolutions so panels and controls never
        // disappear offscreen; higher resolutions still honor up to 150%.
        float safeMaximum = Math.max(0.5f, framebufferH / 720f);
        uiScale = Math.min(requested, safeMaximum);
        logicalW = framebufferW / uiScale;
        logicalH = framebufferH / uiScale;
        screenW = Math.max(1, Math.round(logicalW));
        screenH = Math.max(1, Math.round(logicalH));
        buf.clear();
    }

    public int screenW() {
        return screenW;
    }

    public int screenH() {
        return screenH;
    }

    public int framebufferW() {
        return framebufferW;
    }

    public int framebufferH() {
        return framebufferH;
    }

    public float uiScale() {
        return uiScale;
    }

    public int drawCallsLastFrame() {
        return drawCallsLastFrame;
    }

    /** Converts a framebuffer-space pointer coordinate to the logical canvas. */
    public double toLogicalX(double framebufferX) {
        return framebufferX / uiScale;
    }

    /** Converts a framebuffer-space pointer coordinate to the logical canvas. */
    public double toLogicalY(double framebufferY) {
        return framebufferY / uiScale;
    }

    public FontRenderer fontRenderer() {
        return font;
    }

    public IconAtlas iconAtlas() {
        return icons;
    }

    public void rect(float x, float y, float w, float h, float r, float g, float b, float a) {
        quad(x, y, w, h, 0, 0, 0, 0, r, g, b, a, MODE_SOLID);
    }

    public void rectOutline(float x, float y, float w, float h, float t, float r, float g, float b, float a) {
        rect(x, y, w, t, r, g, b, a);
        rect(x, y + h - t, w, t, r, g, b, a);
        rect(x, y, t, h, r, g, b, a);
        rect(x + w - t, y, t, h, r, g, b, a);
    }

    private void quad(float x, float y, float w, float h,
                      float u0, float v0, float u1, float v1,
                      float r, float g, float b, float a, float mode) {
        if (w <= 0 || h <= 0 || a <= 0) {
            return;
        }
        vertex(x, y, u0, v0, r, g, b, a, mode);
        vertex(x + w, y, u1, v0, r, g, b, a, mode);
        vertex(x + w, y + h, u1, v1, r, g, b, a, mode);
        vertex(x, y, u0, v0, r, g, b, a, mode);
        vertex(x + w, y + h, u1, v1, r, g, b, a, mode);
        vertex(x, y + h, u0, v1, r, g, b, a, mode);
    }

    private void vertex(float x, float y, float u, float v,
                        float r, float g, float b, float a, float mode) {
        buf.add(x, y);
        buf.add(u, v);
        buf.add(r, g, b, a);
        buf.add(mode);
    }

    public float textWidth(String s, float scale) {
        return font.textWidth(s, scale);
    }

    public void text(float x, float y, float scale, String s, float r, float g, float b, float a) {
        textInternal(x, y, scale, s, r, g, b, a, FontRenderer.Weight.REGULAR);
    }

    public void textSemibold(float x, float y, float scale, String s,
                             float r, float g, float b, float a) {
        textInternal(x, y, scale, s, r, g, b, a, FontRenderer.Weight.SEMIBOLD);
    }

    private void textInternal(float x, float y, float scale, String s,
                              float r, float g, float b, float a, FontRenderer.Weight weight) {
        if (s == null || s.isEmpty()) {
            return;
        }
        if (s.length() > 4096) {
            s = s.substring(0, 4096);
        }

        float drawScale = font.drawScale(scale);
        float cursorX = x;
        float baseline = y + font.ascent(weight) * drawScale;
        float lineHeight = font.lineHeight(scale, weight);
        int previous = -1;

        for (int offset = 0; offset < s.length();) {
            int codePoint = s.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\r') {
                continue;
            }
            if (codePoint == '\n') {
                cursorX = x;
                baseline += lineHeight;
                previous = -1;
                continue;
            }
            if (codePoint == '\t') {
                FontRenderer.Glyph space = font.glyph(' ', weight);
                cursorX += (space == null ? FontRenderer.LOGICAL_BASE_HEIGHT
                        : space.advance * drawScale) * 4f;
                previous = -1;
                continue;
            }

            cursorX += font.kerning(previous, codePoint, weight) * drawScale;
            FontRenderer.Glyph glyph = font.glyph(codePoint, weight);
            if (glyph != null) {
                if (glyph.visible()) {
                    quad(cursorX + glyph.xOffset * drawScale,
                            baseline + glyph.yOffset * drawScale,
                            glyph.width * drawScale, glyph.height * drawScale,
                            glyph.u0, glyph.v0, glyph.u1, glyph.v1,
                            r, g, b, a, MODE_FONT);
                }
                cursorX += glyph.advance * drawScale;
            }
            previous = codePoint;
        }
    }

    public void textShadow(float x, float y, float scale, String s, float r, float g, float b, float a) {
        text(x + 1, y + 1, scale, s, 0f, 0f, 0f, a * 0.8f);
        text(x, y, scale, s, r, g, b, a);
    }

    public void textCentered(float cx, float y, float scale, String s, float r, float g, float b, float a) {
        textShadow(cx - textWidth(s, scale) / 2f, y, scale, s, r, g, b, a);
    }

    public void textCenteredSemibold(float cx, float y, float scale, String s,
                                     float r, float g, float b, float a) {
        float width = font.textWidth(s, scale, FontRenderer.Weight.SEMIBOLD);
        textSemibold(cx - width / 2f + 1, y + 1, scale, s, 0, 0, 0, a * 0.8f);
        textSemibold(cx - width / 2f, y, scale, s, r, g, b, a);
    }

    public void sprite(String id, float x, float y, float w, float h) {
        sprite(id, x, y, w, h, 1, 1, 1, 1);
    }

    public void sprite(String id, float x, float y, float w, float h,
                       float r, float g, float b, float a) {
        IconAtlas.Region region = icons.region(id);
        spriteRegion(region, x, y, w, h, region.u0, region.v0, region.u1, region.v1,
                r, g, b, a);
    }

    public void itemIcon(ItemType item, float x, float y, float size) {
        itemIcon(item, x, y, size, 1f);
    }

    public void itemIcon(ItemType item, float x, float y, float size, float alpha) {
        if (item == null) {
            return;
        }
        IconAtlas.Region region = icons.item(item);
        spriteRegion(region, x, y, size, size, region.u0, region.v0, region.u1, region.v1,
                1, 1, 1, alpha);
    }

    public void nineSlice(String id, float x, float y, float w, float h, float border) {
        nineSlice(id, x, y, w, h, border, 1, 1, 1, 1);
    }

    public void nineSlice(String id, float x, float y, float w, float h, float border,
                          float r, float g, float b, float a) {
        IconAtlas.Region region = icons.region(id);
        float bx = Math.min(Math.max(0, border), w * 0.5f);
        float by = Math.min(Math.max(0, border), h * 0.5f);
        float sourceX = Math.min(border, region.width * 0.5f);
        float sourceY = Math.min(border, region.height * 0.5f);
        float um = sourceX / region.width;
        float vm = sourceY / region.height;
        float[] dx = {x, x + bx, x + w - bx, x + w};
        float[] dy = {y, y + by, y + h - by, y + h};
        float[] us = {region.u0,
                region.u0 + (region.u1 - region.u0) * um,
                region.u1 - (region.u1 - region.u0) * um,
                region.u1};
        float[] vs = {region.v0,
                region.v0 + (region.v1 - region.v0) * vm,
                region.v1 - (region.v1 - region.v0) * vm,
                region.v1};
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                spriteRegion(region, dx[col], dy[row], dx[col + 1] - dx[col],
                        dy[row + 1] - dy[row], us[col], vs[row], us[col + 1], vs[row + 1],
                        r, g, b, a);
            }
        }
    }

    /** Standard restrained Veylon panel skin. */
    public void panel(float x, float y, float w, float h) {
        nineSlice(IconAtlas.PANEL, x, y, w, h, 5);
    }

    /** Draws a reusable interaction-state button background. */
    public void button(float x, float y, float w, float h,
                       boolean hovered, boolean pressed, boolean enabled) {
        float brightness = !enabled ? 0.48f : (pressed ? 0.72f : (hovered ? 1.18f : 1f));
        float alpha = enabled ? 1f : 0.75f;
        nineSlice(IconAtlas.BUTTON, x, y, w, h, 5,
                brightness, brightness, brightness, alpha);
        if (hovered && enabled) {
            rectOutline(x, y, w, h, 1, 0.35f, 0.88f, 0.92f, 0.9f);
        }
    }

    /** Draws a reusable inventory/equipment slot background. */
    public void slot(float x, float y, float size, boolean hovered, boolean selected) {
        float tint = hovered ? 1.18f : 1f;
        nineSlice(IconAtlas.SLOT, x, y, size, size, 4, tint, tint, tint, 1f);
        if (selected) {
            rectOutline(x, y, size, size, 2, 0.35f, 0.9f, 0.95f, 1f);
        } else if (hovered) {
            rectOutline(x, y, size, size, 1, 0.75f, 0.9f, 0.95f, 0.9f);
        }
    }

    private void spriteRegion(IconAtlas.Region region, float x, float y, float w, float h,
                              float u0, float v0, float u1, float v1,
                              float r, float g, float b, float a) {
        if (region == null) {
            return;
        }
        quad(x, y, w, h, u0, v0, u1, v1, r, g, b, a, MODE_ICON);
    }

    public void end() {
        drawCallsLastFrame = buf.size() == 0 ? 0 : 1;
        if (drawCallsLastFrame == 0) {
            return;
        }
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE); // screen-space quads are wound CW after the Y flip
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        shader.bind();
        shader.set("uScreen", logicalW, logicalH);
        shader.set("uFontAtlas", 0);
        shader.set("uIconAtlas", 1);
        font.bind(0);
        icons.bind(1);
        mesh.upload(buf.array(), buf.size());
        mesh.draw();
        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
    }

    public void delete() {
        font.delete();
        icons.delete();
        if (shader != null) {
            shader.delete();
        }
        if (mesh != null) {
            mesh.delete();
        }
    }
}
