package com.veylon.engine;

import com.veylon.util.FloatList;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBEasyFont;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Batched 2D renderer for HUD and menus: colored rectangles plus text rendered
 * with stb_easy_font (no font assets required).
 */
public class UiRenderer {

    private static final String VS = """
            #version 330 core
            layout(location=0) in vec2 aPos;
            layout(location=1) in vec4 aColor;
            uniform vec2 uScreen;
            out vec4 vColor;
            void main() {
                gl_Position = vec4(aPos.x / uScreen.x * 2.0 - 1.0, 1.0 - aPos.y / uScreen.y * 2.0, 0.0, 1.0);
                vColor = aColor;
            }
            """;

    private static final String FS = """
            #version 330 core
            in vec4 vColor;
            out vec4 FragColor;
            void main() {
                FragColor = vColor;
            }
            """;

    private ShaderProgram shader;
    private Mesh mesh;
    private final FloatList buf = new FloatList(1 << 14);
    private final ByteBuffer fontBuf = BufferUtils.createByteBuffer(280 * 1024);
    private int screenW, screenH;

    public void init() {
        shader = new ShaderProgram(VS, FS);
        mesh = new Mesh(new int[]{2, 4});
    }

    public void begin(int w, int h) {
        screenW = w;
        screenH = h;
        buf.clear();
    }

    public int screenW() {
        return screenW;
    }

    public int screenH() {
        return screenH;
    }

    public void rect(float x, float y, float w, float h, float r, float g, float b, float a) {
        tri(x, y, x + w, y, x + w, y + h, r, g, b, a);
        tri(x, y, x + w, y + h, x, y + h, r, g, b, a);
    }

    public void rectOutline(float x, float y, float w, float h, float t, float r, float g, float b, float a) {
        rect(x, y, w, t, r, g, b, a);
        rect(x, y + h - t, w, t, r, g, b, a);
        rect(x, y, t, h, r, g, b, a);
        rect(x + w - t, y, t, h, r, g, b, a);
    }

    private void tri(float x1, float y1, float x2, float y2, float x3, float y3,
                     float r, float g, float b, float a) {
        buf.add(x1, y1);
        buf.add(r, g, b, a);
        buf.add(x2, y2);
        buf.add(r, g, b, a);
        buf.add(x3, y3);
        buf.add(r, g, b, a);
    }

    public float textWidth(String s, float scale) {
        return STBEasyFont.stb_easy_font_width(s) * scale;
    }

    public void text(float x, float y, float scale, String s, float r, float g, float b, float a) {
        if (s == null || s.isEmpty()) {
            return;
        }
        if (s.length() > 950) {
            s = s.substring(0, 950);
        }
        fontBuf.clear();
        int quads = STBEasyFont.stb_easy_font_print(0, 0, s, null, fontBuf);
        for (int q = 0; q < quads; q++) {
            int base = q * 4 * 16;
            float[] xs = new float[4];
            float[] ys = new float[4];
            for (int v = 0; v < 4; v++) {
                xs[v] = x + fontBuf.getFloat(base + v * 16) * scale;
                ys[v] = y + fontBuf.getFloat(base + v * 16 + 4) * scale;
            }
            tri(xs[0], ys[0], xs[1], ys[1], xs[2], ys[2], r, g, b, a);
            tri(xs[0], ys[0], xs[2], ys[2], xs[3], ys[3], r, g, b, a);
        }
    }

    public void textShadow(float x, float y, float scale, String s, float r, float g, float b, float a) {
        text(x + 1, y + 1, scale, s, 0f, 0f, 0f, a * 0.8f);
        text(x, y, scale, s, r, g, b, a);
    }

    public void textCentered(float cx, float y, float scale, String s, float r, float g, float b, float a) {
        textShadow(cx - textWidth(s, scale) / 2f, y, scale, s, r, g, b, a);
    }

    public void end() {
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        shader.bind();
        shader.set("uScreen", (float) screenW, (float) screenH);
        mesh.upload(buf.array(), buf.size());
        mesh.draw();
        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
    }

    public void delete() {
        if (shader != null) {
            shader.delete();
        }
        if (mesh != null) {
            mesh.delete();
        }
    }
}
