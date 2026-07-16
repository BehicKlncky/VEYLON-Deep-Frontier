package com.veylon.gfx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphicsSettingsTest {

    @TempDir
    Path tempDir;

    @Test
    void missingFileReturnsDocumentedDefaults() {
        GraphicsSettings settings = GraphicsSettings.load(tempDir.resolve("missing.properties"));

        assertEquals(1280, settings.windowWidth);
        assertEquals(720, settings.windowHeight);
        assertEquals(6, settings.renderDistance);
        assertEquals(1, settings.shadowQuality);
        assertTrue(settings.bloom);
        assertTrue(settings.fxaa);
        assertEquals(1f, settings.particleDensity, 0.0001f);
        assertEquals(75f, settings.fov, 0.0001f);
        assertEquals(1f, settings.uiScale, 0.0001f);
        assertTrue(settings.vsync);
        assertFalse(settings.fullscreen);
        assertEquals(1f, settings.motion, 0.0001f);
        assertTrue(settings.crispTextures);
    }

    @Test
    void loadClampsRangesAndFallsBackForMalformedNumbers() throws IOException {
        Path file = tempDir.resolve("clamped.properties");
        Files.writeString(file, """
                windowWidth=320
                windowHeight=99999
                renderDistance=-50
                shadowQuality=99
                bloom=false
                fxaa=false
                particleDensity=8.5
                fov=not-a-number
                uiScale=0.1
                vsync=false
                fullscreen=true
                motion=-2
                crispTextures=false
                """);

        GraphicsSettings settings = GraphicsSettings.load(file);

        assertEquals(GraphicsSettings.MIN_WINDOW_WIDTH, settings.windowWidth);
        assertEquals(GraphicsSettings.MAX_WINDOW_HEIGHT, settings.windowHeight);
        assertEquals(4, settings.renderDistance);
        assertEquals(2, settings.shadowQuality);
        assertFalse(settings.bloom);
        assertFalse(settings.fxaa);
        assertEquals(1f, settings.particleDensity, 0.0001f);
        assertEquals(75f, settings.fov, 0.0001f, "Malformed FOV should retain its default");
        assertEquals(0.75f, settings.uiScale, 0.0001f);
        assertFalse(settings.vsync);
        assertTrue(settings.fullscreen);
        assertEquals(0f, settings.motion, 0.0001f);
        assertFalse(settings.crispTextures);
    }

    @Test
    void saveAndLoadRoundTripWithoutTouchingTheWorkspaceSettingsFile() {
        Path file = tempDir.resolve("round-trip.properties");
        GraphicsSettings expected = new GraphicsSettings();
        expected.windowWidth = 2560;
        expected.windowHeight = 1440;
        expected.renderDistance = 9;
        expected.shadowQuality = 2;
        expected.bloom = false;
        expected.fxaa = false;
        expected.particleDensity = 0.42f;
        expected.fov = 91f;
        expected.uiScale = 1.25f;
        expected.vsync = false;
        expected.fullscreen = true;
        expected.motion = 0.35f;
        expected.crispTextures = false;

        expected.save(file);
        assertTrue(Files.isRegularFile(file));
        GraphicsSettings actual = GraphicsSettings.load(file);

        assertEquals(expected.windowWidth, actual.windowWidth);
        assertEquals(expected.windowHeight, actual.windowHeight);
        assertEquals(expected.renderDistance, actual.renderDistance);
        assertEquals(expected.shadowQuality, actual.shadowQuality);
        assertEquals(expected.bloom, actual.bloom);
        assertEquals(expected.fxaa, actual.fxaa);
        assertEquals(expected.particleDensity, actual.particleDensity, 0.0001f);
        assertEquals(expected.fov, actual.fov, 0.0001f);
        assertEquals(expected.uiScale, actual.uiScale, 0.0001f);
        assertEquals(expected.vsync, actual.vsync);
        assertEquals(expected.fullscreen, actual.fullscreen);
        assertEquals(expected.motion, actual.motion, 0.0001f);
        assertEquals(expected.crispTextures, actual.crispTextures);
    }
}
