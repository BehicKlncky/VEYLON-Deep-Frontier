package com.veylon.gfx;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL33C.*;

/** A 2D RGBA texture, loadable from PNG bytes or raw pixels. */
public class Texture2D {

    public final int id;
    public final int width;
    public final int height;

    private Texture2D(int id, int width, int height) {
        this.id = id;
        this.width = width;
        this.height = height;
    }

    /** Decodes a PNG/JPG asset. Returns the missing-texture checker on failure (never null). */
    public static Texture2D load(String assetPath, boolean srgb, boolean nearest) {
        try {
            ByteBuffer file = ResourceManager.readBuffer(assetPath);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), n = stack.mallocInt(1);
                ByteBuffer pixels = STBImage.stbi_load_from_memory(file, w, h, n, 4);
                if (pixels == null) {
                    throw new RuntimeException("STB decode failed for assets/" + assetPath
                            + ": " + STBImage.stbi_failure_reason());
                }
                Texture2D t = fromPixels(pixels, w.get(0), h.get(0), srgb, nearest);
                STBImage.stbi_image_free(pixels);
                return t;
            }
        } catch (RuntimeException e) {
            System.err.println("[texture] " + e.getMessage() + " -> using missing-texture fallback");
            return missing();
        }
    }

    /** Creates a texture from RGBA8 pixel ints (0xAARRGGBB, row-major, top-left first). */
    public static Texture2D fromArgb(int[] argb, int width, int height, boolean srgb, boolean nearest) {
        ByteBuffer buf = BufferUtils.createByteBuffer(width * height * 4);
        for (int p : argb) {
            buf.put((byte) ((p >> 16) & 0xFF));
            buf.put((byte) ((p >> 8) & 0xFF));
            buf.put((byte) (p & 0xFF));
            buf.put((byte) ((p >> 24) & 0xFF));
        }
        buf.flip();
        return fromPixels(buf, width, height, srgb, nearest);
    }

    private static Texture2D fromPixels(ByteBuffer rgba, int width, int height, boolean srgb, boolean nearest) {
        int id = glGenTextures();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, id);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexImage2D(GL_TEXTURE_2D, 0, srgb ? GL_SRGB8_ALPHA8 : GL_RGBA8,
                width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
        glGenerateMipmap(GL_TEXTURE_2D);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER,
                nearest ? GL_NEAREST_MIPMAP_LINEAR : GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, nearest ? GL_NEAREST : GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);
        return new Texture2D(id, width, height);
    }

    /** The classic magenta/black checker so missing art is unmissable in-game. */
    public static Texture2D missing() {
        int s = 16;
        int[] px = new int[s * s];
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                boolean m = ((x / 4) + (y / 4)) % 2 == 0;
                px[y * s + x] = m ? 0xFFFF00FF : 0xFF000000;
            }
        }
        return fromArgb(px, s, s, false, true);
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, id);
    }

    public void delete() {
        glDeleteTextures(id);
    }
}
