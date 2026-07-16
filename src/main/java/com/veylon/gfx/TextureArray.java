package com.veylon.gfx;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

import java.nio.ByteBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.opengl.EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT;
import static org.lwjgl.opengl.EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT;

/**
 * GL_TEXTURE_2D_ARRAY of same-size RGBA tiles with mipmaps — the backbone of
 * voxel materials (no atlas bleeding, repeat wrapping per layer).
 */
public class TextureArray {

    public final int id;
    public final int size;
    public final int layers;

    /**
     * Uploads tiles (each an int[size*size] of 0xAARRGGBB, top-left first).
     *
     * @param nearest true = crisp voxel look (nearest mag), false = bilinear
     */
    public TextureArray(List<int[]> tiles, int size, boolean srgb, boolean nearest) {
        this.size = size;
        this.layers = tiles.size();
        id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D_ARRAY, id);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, srgb ? GL_SRGB8_ALPHA8 : GL_RGBA8,
                size, size, layers, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);

        ByteBuffer buf = BufferUtils.createByteBuffer(size * size * 4);
        for (int layer = 0; layer < layers; layer++) {
            int[] argb = tiles.get(layer);
            if (argb.length != size * size) {
                throw new IllegalArgumentException("Tile layer " + layer + " has "
                        + argb.length + " pixels, expected " + (size * size));
            }
            buf.clear();
            for (int p : argb) {
                buf.put((byte) ((p >> 16) & 0xFF));
                buf.put((byte) ((p >> 8) & 0xFF));
                buf.put((byte) (p & 0xFF));
                buf.put((byte) ((p >> 24) & 0xFF));
            }
            buf.flip();
            glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, layer,
                    size, size, 1, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        }
        glGenerateMipmap(GL_TEXTURE_2D_ARRAY);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, nearest ? GL_NEAREST : GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_REPEAT);
        // Cap mip level: below 4x4 the tiles just turn to mud.
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAX_LEVEL,
                Math.max(0, Integer.numberOfTrailingZeros(size) - 2));
        if (GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
            float max = glGetFloat(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
            glTexParameterf(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAX_ANISOTROPY_EXT, Math.min(8f, max));
        }
        glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D_ARRAY, id);
    }

    public void delete() {
        glDeleteTextures(id);
    }
}
