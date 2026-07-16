package com.veylon.gfx;

import com.veylon.util.AppPaths;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImageWrite;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.lwjgl.opengl.GL33C.*;

/** Saves the current back buffer as a PNG under the writable app-data directory. */
public final class ScreenshotUtil {

    private ScreenshotUtil() {
    }

    /** Captures the back buffer. Pass name=null for a timestamped filename. Returns the file path or null. */
    public static String capture(int width, int height, String name) {
        if (width <= 0 || height <= 0) {
            return null;
        }
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 3);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadBuffer(GL_BACK);
        glReadPixels(0, 0, width, height, GL_RGB, GL_UNSIGNED_BYTE, pixels);

        // Flip vertically (GL reads bottom-up).
        int stride = width * 3;
        byte[] row = new byte[stride];
        byte[] tmp = new byte[stride];
        for (int y = 0; y < height / 2; y++) {
            int top = y * stride, bot = (height - 1 - y) * stride;
            pixels.position(top);
            pixels.get(row, 0, stride);
            pixels.position(bot);
            pixels.get(tmp, 0, stride);
            pixels.position(top);
            pixels.put(tmp, 0, stride);
            pixels.position(bot);
            pixels.put(row, 0, stride);
        }
        pixels.position(0);

        Path dir = AppPaths.dataDirectory().resolve("screenshots");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            System.err.println("[screenshot] cannot create " + dir.toAbsolutePath() + ": " + e.getMessage());
            return null;
        }
        if (name == null || name.isEmpty()) {
            name = "shot_" + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
        }
        Path out = dir.resolve(name + ".png");
        boolean ok = STBImageWrite.stbi_write_png(
                out.toAbsolutePath().toString(), width, height, 3, pixels, stride);
        if (ok) {
            System.out.println("[screenshot] saved " + out);
            return out.toString();
        }
        System.err.println("[screenshot] write failed: " + out);
        return null;
    }
}
