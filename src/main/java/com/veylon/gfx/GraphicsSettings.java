package com.veylon.gfx;

import com.veylon.util.AppPaths;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** User-facing graphics options, persisted to veylon_graphics.properties. */
public class GraphicsSettings {

    public static final int MIN_WINDOW_WIDTH = 960;
    public static final int MIN_WINDOW_HEIGHT = 540;
    public static final int MAX_WINDOW_WIDTH = 7680;
    public static final int MAX_WINDOW_HEIGHT = 4320;

    /** Logical client-area size used when the game is windowed. */
    public int windowWidth = 1280;
    public int windowHeight = 720;
    public int renderDistance = 6;      // chunks, 4..10
    public int shadowQuality = 1;       // 0 off, 1 = 2048 map, 2 = 4096 map
    public boolean bloom = true;
    public boolean fxaa = true;
    public float particleDensity = 1f;  // 0..1 (zero disables emission)
    public float fov = 75f;             // 60..100
    public float uiScale = 1f;          // 0.75..1.5
    public boolean vsync = true;
    public boolean fullscreen = false;
    /** Camera bob/shake intensity 0..1. */
    public float motion = 1f;
    /** true = crisp nearest-neighbor texels, false = bilinear. */
    public boolean crispTextures = true;

    private static final Path FILE = AppPaths.dataDirectory().resolve("veylon_graphics.properties");

    public static GraphicsSettings loadOrDefaults() {
        return load(FILE);
    }

    /** Path-injectable loader used by non-GL validation tests. */
    static GraphicsSettings load(Path path) {
        GraphicsSettings s = new GraphicsSettings();
        File f = path.toFile();
        if (!f.isFile()) {
            return s;
        }
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(f)) {
            p.load(in);
            s.windowWidth = clampI(intOf(p, "windowWidth", s.windowWidth),
                    MIN_WINDOW_WIDTH, MAX_WINDOW_WIDTH);
            s.windowHeight = clampI(intOf(p, "windowHeight", s.windowHeight),
                    MIN_WINDOW_HEIGHT, MAX_WINDOW_HEIGHT);
            s.renderDistance = clampI(intOf(p, "renderDistance", s.renderDistance), 4, 10);
            s.shadowQuality = clampI(intOf(p, "shadowQuality", s.shadowQuality), 0, 2);
            s.bloom = boolOf(p, "bloom", s.bloom);
            s.fxaa = boolOf(p, "fxaa", s.fxaa);
            s.particleDensity = clampF(floatOf(p, "particleDensity", s.particleDensity), 0f, 1f);
            s.fov = clampF(floatOf(p, "fov", s.fov), 60f, 100f);
            s.uiScale = clampF(floatOf(p, "uiScale", s.uiScale), 0.75f, 1.5f);
            s.vsync = boolOf(p, "vsync", s.vsync);
            s.fullscreen = boolOf(p, "fullscreen", s.fullscreen);
            s.motion = clampF(floatOf(p, "motion", s.motion), 0f, 1f);
            s.crispTextures = boolOf(p, "crispTextures", s.crispTextures);
        } catch (IOException e) {
            System.err.println("[settings] failed to read " + path + ": " + e.getMessage());
        }
        return s;
    }

    public void save() {
        save(FILE);
    }

    /** Path-injectable writer used by non-GL validation tests. */
    void save(Path path) {
        Properties p = new Properties();
        p.setProperty("windowWidth", String.valueOf(windowWidth));
        p.setProperty("windowHeight", String.valueOf(windowHeight));
        p.setProperty("renderDistance", String.valueOf(renderDistance));
        p.setProperty("shadowQuality", String.valueOf(shadowQuality));
        p.setProperty("bloom", String.valueOf(bloom));
        p.setProperty("fxaa", String.valueOf(fxaa));
        p.setProperty("particleDensity", String.valueOf(particleDensity));
        p.setProperty("fov", String.valueOf(fov));
        p.setProperty("uiScale", String.valueOf(uiScale));
        p.setProperty("vsync", String.valueOf(vsync));
        p.setProperty("fullscreen", String.valueOf(fullscreen));
        p.setProperty("motion", String.valueOf(motion));
        p.setProperty("crispTextures", String.valueOf(crispTextures));
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            System.err.println("[settings] failed to create " + path + ": " + e.getMessage());
            return;
        }
        try (FileOutputStream out = new FileOutputStream(path.toFile())) {
            p.store(out, "VEYLON graphics settings");
        } catch (IOException e) {
            System.err.println("[settings] failed to write " + path + ": " + e.getMessage());
        }
    }

    public int shadowMapSize() {
        return shadowQuality == 2 ? 4096 : 2048;
    }

    private static int intOf(Properties p, String k, int def) {
        try {
            return Integer.parseInt(p.getProperty(k, String.valueOf(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static float floatOf(Properties p, String k, float def) {
        try {
            return Float.parseFloat(p.getProperty(k, String.valueOf(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean boolOf(Properties p, String k, boolean def) {
        return Boolean.parseBoolean(p.getProperty(k, String.valueOf(def)).trim());
    }

    private static int clampI(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float clampF(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
