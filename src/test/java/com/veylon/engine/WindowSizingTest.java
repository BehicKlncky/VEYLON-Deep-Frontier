package com.veylon.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Exercises pre-context sizing APIs without creating GLFW or OpenGL state. */
class WindowSizingTest {

    @Test
    void initialWindowedSizeSeedsLogicalAndFramebufferDimensions() {
        Window window = new Window();

        window.setInitialWindowedSize(1920, 1080);

        assertEquals(1920, window.windowedWidth());
        assertEquals(1080, window.windowedHeight());
        assertEquals(1920, window.windowWidth());
        assertEquals(1080, window.windowHeight());
        assertEquals(1920, window.framebufferWidth());
        assertEquals(1080, window.framebufferHeight());
        assertEquals(1920, window.width(), "Legacy renderer getter remains framebuffer pixels");
        assertEquals(1080, window.height(), "Legacy renderer getter remains framebuffer pixels");
        assertEquals(1f, window.cursorToFramebufferScaleX(), 0.0001f);
        assertEquals(1f, window.cursorToFramebufferScaleY(), 0.0001f);
    }

    @Test
    void windowedResolutionCanBeReconfiguredBeforeContextCreation() {
        Window window = new Window();
        window.setInitialWindowedSize(1280, 720);

        window.setWindowedResolution(2560, 1440);

        assertEquals(2560, window.windowedWidth());
        assertEquals(1440, window.windowedHeight());
        assertEquals(2560, window.windowWidth());
        assertEquals(1440, window.windowHeight());
    }

    @Test
    void nonPositiveWindowSizesAreRejectedBeforeNativeCalls() {
        Window window = new Window();

        assertThrows(IllegalArgumentException.class, () -> window.setInitialWindowedSize(0, 720));
        assertThrows(IllegalArgumentException.class, () -> window.setWindowedResolution(1280, -1));
    }
}
