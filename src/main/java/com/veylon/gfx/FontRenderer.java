package com.veylon.gfx;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTFontinfo;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.stb.STBTruetype.*;

/**
 * Source Sans 3 glyph atlas and metrics used by {@code UiRenderer}.
 *
 * <p>The public UI API historically expressed text size as a multiplier over
 * the prototype's small bitmap face. Keeping a 12 logical-pixel base height
 * preserves existing screen layouts while the atlas is rasterized at 48 px and
 * filtered down for clean 720p-1440p text. Metrics and pair kerning come from
 * stb_truetype; no Java2D or platform-installed font is involved.</p>
 */
public final class FontRenderer {

    public enum Weight {
        REGULAR,
        SEMIBOLD
    }

    public static final float LOGICAL_BASE_HEIGHT = 12f;
    private static final float RASTER_HEIGHT = 48f;
    private static final int ATLAS_SIZE = 1024;
    private static final int PADDING = 2;

    /** A rasterized glyph. Metrics are in atlas/raster pixels. */
    public static final class Glyph {
        public final int codePoint;
        public final float advance;
        public final float xOffset;
        public final float yOffset;
        public final float width;
        public final float height;
        public final float u0;
        public final float v0;
        public final float u1;
        public final float v1;

        Glyph(int codePoint, float advance, float xOffset, float yOffset,
              float width, float height, float u0, float v0, float u1, float v1) {
            this.codePoint = codePoint;
            this.advance = advance;
            this.xOffset = xOffset;
            this.yOffset = yOffset;
            this.width = width;
            this.height = height;
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
        }

        public boolean visible() {
            return width > 0 && height > 0;
        }
    }

    private static final class Face implements AutoCloseable {
        final ByteBuffer data;
        final STBTTFontinfo info;
        final Map<Integer, Glyph> glyphs = new java.util.HashMap<>();
        final float rasterScale;
        final float ascent;
        final float descent;
        final float lineHeight;

        Face(String assetPath) {
            data = ResourceManager.readBuffer(assetPath);
            info = STBTTFontinfo.malloc();
            if (!stbtt_InitFont(info, data)) {
                info.free();
                throw new IllegalStateException("Invalid TrueType font: assets/" + assetPath);
            }
            rasterScale = stbtt_ScaleForPixelHeight(info, RASTER_HEIGHT);
            int[] a = new int[1], d = new int[1], gap = new int[1];
            stbtt_GetFontVMetrics(info, a, d, gap);
            ascent = a[0] * rasterScale;
            descent = d[0] * rasterScale;
            lineHeight = (a[0] - d[0] + gap[0]) * rasterScale;
        }

        @Override
        public void close() {
            info.free();
        }
    }

    private static final class Cursor {
        int x = PADDING;
        int y = PADDING;
        int rowHeight;
    }

    private final EnumMap<Weight, Face> faces = new EnumMap<>(Weight.class);
    private int textureId;
    private int bakedGlyphs;

    public void init() {
        if (textureId != 0) {
            return;
        }

        ByteBuffer alpha = BufferUtils.createByteBuffer(ATLAS_SIZE * ATLAS_SIZE);
        for (int i = 0; i < alpha.capacity(); i++) {
            alpha.put(i, (byte) 0);
        }

        Face regular = new Face("fonts/SourceSans3-Regular.ttf");
        Face semibold = null;
        try {
            semibold = new Face("fonts/SourceSans3-Semibold.ttf");
            faces.put(Weight.REGULAR, regular);
            faces.put(Weight.SEMIBOLD, semibold);

            int[] codePoints = supportedCodePoints();
            Cursor cursor = new Cursor();
            packFace(regular, codePoints, alpha, cursor);
            packFace(semibold, codePoints, alpha, cursor);
            validateRequiredGlyphs();
            upload(alpha);
        } catch (RuntimeException e) {
            regular.close();
            if (semibold != null) {
                semibold.close();
            }
            faces.clear();
            throw e;
        }

        System.out.println("[ui] Source Sans 3 atlas: " + bakedGlyphs
                + " glyphs, Latin + Turkish coverage OK");
    }

    private void packFace(Face face, int[] codePoints, ByteBuffer atlas, Cursor cursor) {
        int[] advance = new int[1], bearing = new int[1];
        int[] x0 = new int[1], y0 = new int[1], x1 = new int[1], y1 = new int[1];

        for (int codePoint : codePoints) {
            int glyphIndex = stbtt_FindGlyphIndex(face.info, codePoint);
            if (glyphIndex == 0 && codePoint != 0) {
                continue;
            }

            stbtt_GetCodepointHMetrics(face.info, codePoint, advance, bearing);
            stbtt_GetCodepointBitmapBox(face.info, codePoint, face.rasterScale, face.rasterScale,
                    x0, y0, x1, y1);
            int width = Math.max(0, x1[0] - x0[0]);
            int height = Math.max(0, y1[0] - y0[0]);

            int px = 0, py = 0;
            if (width > 0 && height > 0) {
                if (cursor.x + width + PADDING > ATLAS_SIZE) {
                    cursor.x = PADDING;
                    cursor.y += cursor.rowHeight + PADDING;
                    cursor.rowHeight = 0;
                }
                if (cursor.y + height + PADDING > ATLAS_SIZE) {
                    throw new IllegalStateException("Source Sans 3 glyph atlas overflow ("
                            + ATLAS_SIZE + "x" + ATLAS_SIZE + ")");
                }
                px = cursor.x;
                py = cursor.y;

                ByteBuffer target = atlas.duplicate();
                target.position(py * ATLAS_SIZE + px);
                target = target.slice();
                stbtt_MakeCodepointBitmap(face.info, target, width, height, ATLAS_SIZE,
                        face.rasterScale, face.rasterScale, codePoint);

                cursor.x += width + PADDING;
                cursor.rowHeight = Math.max(cursor.rowHeight, height);
            }

            face.glyphs.put(codePoint, new Glyph(codePoint,
                    advance[0] * face.rasterScale, x0[0], y0[0], width, height,
                    px / (float) ATLAS_SIZE, py / (float) ATLAS_SIZE,
                    (px + width) / (float) ATLAS_SIZE,
                    (py + height) / (float) ATLAS_SIZE));
            bakedGlyphs++;
        }
    }

    private void upload(ByteBuffer alpha) {
        textureId = glGenTextures();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        alpha.position(0);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, ATLAS_SIZE, ATLAS_SIZE,
                0, GL_RED, GL_UNSIGNED_BYTE, alpha);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    /** Converts the legacy text size multiplier into an atlas-pixel scale. */
    public float drawScale(float uiTextScale) {
        return LOGICAL_BASE_HEIGHT * Math.max(0f, uiTextScale) / RASTER_HEIGHT;
    }

    public float ascent(Weight weight) {
        return face(weight).ascent;
    }

    public float lineHeight(float uiTextScale, Weight weight) {
        return face(weight).lineHeight * drawScale(uiTextScale);
    }

    public Glyph glyph(int codePoint, Weight weight) {
        Face face = face(weight);
        Glyph glyph = face.glyphs.get(codePoint);
        if (glyph == null) {
            glyph = face.glyphs.get((int) '?');
        }
        return glyph;
    }

    /** Pair kerning in raster pixels. */
    public float kerning(int leftCodePoint, int rightCodePoint, Weight weight) {
        if (leftCodePoint < 0 || rightCodePoint < 0) {
            return 0f;
        }
        Face face = face(weight);
        return stbtt_GetCodepointKernAdvance(face.info, leftCodePoint, rightCodePoint)
                * face.rasterScale;
    }

    public float textWidth(String text, float uiTextScale) {
        return textWidth(text, uiTextScale, Weight.REGULAR);
    }

    public float textWidth(String text, float uiTextScale, Weight weight) {
        if (text == null || text.isEmpty()) {
            return 0f;
        }
        float scale = drawScale(uiTextScale);
        float line = 0f, widest = 0f;
        int previous = -1;
        for (int offset = 0; offset < text.length();) {
            int cp = text.codePointAt(offset);
            offset += Character.charCount(cp);
            if (cp == '\r') {
                continue;
            }
            if (cp == '\n') {
                widest = Math.max(widest, line);
                line = 0f;
                previous = -1;
                continue;
            }
            if (cp == '\t') {
                Glyph space = glyph(' ', weight);
                line += (space == null ? RASTER_HEIGHT * 0.25f : space.advance) * scale * 4f;
                previous = -1;
                continue;
            }
            Glyph glyph = glyph(cp, weight);
            if (glyph != null) {
                line += kerning(previous, cp, weight) * scale;
                line += glyph.advance * scale;
            }
            previous = cp;
        }
        return Math.max(widest, line);
    }

    public boolean hasGlyph(int codePoint, Weight weight) {
        return face(weight).glyphs.containsKey(codePoint)
                && stbtt_FindGlyphIndex(face(weight).info, codePoint) != 0;
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    public int glyphCount() {
        return bakedGlyphs;
    }

    public void delete() {
        if (textureId != 0) {
            glDeleteTextures(textureId);
            textureId = 0;
        }
        for (Face face : faces.values()) {
            face.close();
        }
        faces.clear();
        bakedGlyphs = 0;
    }

    private Face face(Weight weight) {
        Face face = faces.get(weight);
        if (face == null) {
            throw new IllegalStateException("FontRenderer has not been initialized");
        }
        return face;
    }

    private void validateRequiredGlyphs() {
        String required = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
                + " .,:;!?'-+/()[]%#"
                + "ÇçĞğİıÖöŞşÜüÂâÎîÛû€°–—‘’“”•…";
        List<String> missing = new ArrayList<>();
        for (Weight weight : Weight.values()) {
            required.codePoints().distinct().forEach(cp -> {
                if (!hasGlyph(cp, weight)) {
                    missing.add(weight.name().toLowerCase() + " U+" + String.format("%04X", cp));
                }
            });
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Source Sans 3 missing required UI glyphs: " + missing);
        }
    }

    private static int[] supportedCodePoints() {
        Set<Integer> cps = new LinkedHashSet<>();
        addRange(cps, 0x20, 0x7E);       // Basic Latin
        addRange(cps, 0xA0, 0xFF);       // Latin-1 Supplement
        addRange(cps, 0x100, 0x17F);     // Latin Extended-A (Turkish included)
        int[] punctuation = {
                0x2013, 0x2014, 0x2018, 0x2019, 0x201C, 0x201D,
                0x2022, 0x2026, 0x20AC
        };
        for (int cp : punctuation) {
            cps.add(cp);
        }
        return cps.stream().mapToInt(Integer::intValue).toArray();
    }

    private static void addRange(Set<Integer> out, int first, int last) {
        for (int cp = first; cp <= last; cp++) {
            out.add(cp);
        }
    }
}
