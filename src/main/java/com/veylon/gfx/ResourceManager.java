package com.veylon.gfx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.lwjgl.BufferUtils;

/**
 * Classpath asset loading for everything under src/main/resources/assets/.
 * All errors name the asset path so a missing/broken file is easy to trace.
 */
public final class ResourceManager {

    private ResourceManager() {
    }

    /** True if the classpath resource exists. Path is relative to assets/, e.g. "shaders/chunk.vert". */
    public static boolean exists(String path) {
        try (InputStream in = stream(path)) {
            return in != null;
        } catch (IOException e) {
            return false;
        }
    }

    private static InputStream stream(String path) {
        return ResourceManager.class.getClassLoader().getResourceAsStream("assets/" + path);
    }

    /** Reads a text asset (UTF-8). Throws with the asset path on failure. */
    public static String readText(String path) {
        byte[] bytes = readBytes(path);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** Reads a binary asset fully. Throws with the asset path on failure. */
    public static byte[] readBytes(String path) {
        try (InputStream in = stream(path)) {
            if (in == null) {
                throw new RuntimeException("Missing asset: assets/" + path
                        + " (expected under src/main/resources/assets/)");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(1024, in.available()));
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed reading asset: assets/" + path, e);
        }
    }

    /** Reads a binary asset into an off-heap buffer (for STB APIs). */
    public static ByteBuffer readBuffer(String path) {
        byte[] bytes = readBytes(path);
        ByteBuffer buf = BufferUtils.createByteBuffer(bytes.length);
        buf.put(bytes).flip();
        return buf;
    }
}
