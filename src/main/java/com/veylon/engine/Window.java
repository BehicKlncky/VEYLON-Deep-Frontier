package com.veylon.engine;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GLDebugMessageCallback;
import org.lwjgl.opengl.KHRDebug;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Window {

    private long handle;
    /** Logical GLFW client-area size; cursor coordinates use this space. */
    private int windowWidth = 1280;
    private int windowHeight = 720;
    /** Physical pixel size consumed by OpenGL viewports and render targets. */
    private int framebufferWidth = 1280;
    private int framebufferHeight = 720;
    /** Geometry restored after leaving fullscreen. */
    private int windowedWidth = 1280;
    private int windowedHeight = 720;
    private int windowedX = Integer.MIN_VALUE;
    private int windowedY = Integer.MIN_VALUE;
    private boolean fullscreen;
    private boolean cursorCaptured;
    private final boolean glDiagnosticsEnabled = diagnosticsEnabled();
    private final AtomicLong glErrorCount = new AtomicLong();
    private final AtomicLong glDebugMessageCount = new AtomicLong();
    private final AtomicLong glDebugErrorCount = new AtomicLong();
    private GLDebugMessageCallback glDebugCallback;
    private boolean glReady;
    private boolean khrDebugActive;
    private volatile String glContextSummary = "OpenGL context not created";
    private volatile String lastGlDiagnostic = "none";

    private static final int MAX_IMMEDIATE_DIAGNOSTICS = 32;

    public void create(String title, Input input) {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }
        boolean debugContextRequested = glDiagnosticsEnabled;
        applyWindowHints(debugContextRequested);

        handle = glfwCreateWindow(windowedWidth, windowedHeight, title, NULL, NULL);
        if (handle == NULL && debugContextRequested) {
            // A debug context is useful but must never raise the minimum GPU/driver
            // requirement. Retry the same 3.3 Core request without the debug hint.
            System.err.println("[gl] debug-context request failed; retrying OpenGL 3.3 Core");
            applyWindowHints(false);
            handle = glfwCreateWindow(windowedWidth, windowedHeight, title, NULL, NULL);
        }
        if (handle == NULL) {
            throw new RuntimeException("Failed to create GLFW window");
        }

        GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (vidMode != null) {
            windowedX = (vidMode.width() - windowedWidth) / 2;
            windowedY = (vidMode.height() - windowedHeight) / 2;
            glfwSetWindowPos(handle, windowedX, windowedY);
        }

        glfwSetWindowPosCallback(handle, (win, x, y) -> {
            if (!fullscreen) {
                windowedX = x;
                windowedY = y;
            }
        });
        glfwSetWindowSizeCallback(handle, (win, w, h) -> {
            if (w > 0 && h > 0) {
                windowWidth = w;
                windowHeight = h;
                if (!fullscreen) {
                    windowedWidth = w;
                    windowedHeight = h;
                }
            }
        });
        glfwSetFramebufferSizeCallback(handle, (win, w, h) -> {
            if (w > 0 && h > 0) {
                framebufferWidth = w;
                framebufferHeight = h;
            }
        });
        glfwSetKeyCallback(handle, (win, key, scancode, action, mods) -> input.onKey(key, action));
        glfwSetMouseButtonCallback(handle, (win, button, action, mods) -> input.onMouseButton(button, action));
        glfwSetCursorPosCallback(handle, (win, x, y) -> input.onCursorPos(x, y));
        glfwSetScrollCallback(handle, (win, dx, dy) -> input.onScroll(dy));

        refreshSizes();

        glfwMakeContextCurrent(handle);
        GLCapabilities capabilities = GL.createCapabilities();
        glReady = true;
        installGlDiagnostics(capabilities, debugContextRequested);
        glfwSwapInterval(1);
        glfwShowWindow(handle);
    }

    /** Sets the logical windowed client size before {@link #create(String, Input)}. */
    public void setInitialWindowedSize(int width, int height) {
        if (handle != NULL) {
            throw new IllegalStateException("Initial window size must be set before create()");
        }
        setWindowedResolution(width, height);
    }

    /**
     * Changes the configured logical windowed resolution. While fullscreen this
     * is retained and applied when returning to windowed mode.
     */
    public void setWindowedResolution(int width, int height) {
        requirePositiveSize(width, height);
        windowedWidth = width;
        windowedHeight = height;
        if (handle == NULL) {
            windowWidth = width;
            windowHeight = height;
            // Until GLFW creates the surface, assume a 1:1 framebuffer scale.
            framebufferWidth = width;
            framebufferHeight = height;
        } else if (!fullscreen) {
            windowWidth = width;
            windowHeight = height;
            glfwSetWindowSize(handle, width, height);
            refreshSizes();
        }
    }

    private static void requirePositiveSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Window size must be positive: " + width + "x" + height);
        }
    }

    private void refreshSizes() {
        if (handle == NULL) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            glfwGetWindowSize(handle, w, h);
            if (w.get(0) > 0 && h.get(0) > 0) {
                windowWidth = w.get(0);
                windowHeight = h.get(0);
                if (!fullscreen) {
                    windowedWidth = windowWidth;
                    windowedHeight = windowHeight;
                }
            }
            w.clear();
            h.clear();
            glfwGetFramebufferSize(handle, w, h);
            if (w.get(0) > 0 && h.get(0) > 0) {
                framebufferWidth = w.get(0);
                framebufferHeight = h.get(0);
            }
        }
    }

    private void applyWindowHints(boolean debugContext) {
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, debugContext ? GLFW_TRUE : GLFW_FALSE);
    }

    private void installGlDiagnostics(GLCapabilities capabilities, boolean debugContextRequested) {
        String vendor = safeGlString(GL_VENDOR);
        String renderer = safeGlString(GL_RENDERER);
        String version = safeGlString(GL_VERSION);
        String glsl = safeGlString(GL_SHADING_LANGUAGE_VERSION);
        boolean debugContext = (glGetInteger(GL_CONTEXT_FLAGS) & KHRDebug.GL_CONTEXT_FLAG_DEBUG_BIT) != 0;
        glContextSummary = String.format(Locale.ROOT,
                "%s | %s | OpenGL %s | GLSL %s | debug requested=%s granted=%s | KHR_debug=%s",
                vendor, renderer, version, glsl, debugContextRequested, debugContext,
                capabilities.GL_KHR_debug);
        System.out.println("[gl] " + glContextSummary);

        if (!glDiagnosticsEnabled || !capabilities.GL_KHR_debug) {
            if (glDiagnosticsEnabled) {
                System.out.println("[gl] KHR_debug unavailable; using per-frame glGetError polling");
            }
            pollGlErrors("startup");
            return;
        }

        try {
            glDebugCallback = GLDebugMessageCallback.create(
                    (source, type, id, severity, length, message, userParam) -> {
                        if (severity == KHRDebug.GL_DEBUG_SEVERITY_NOTIFICATION) {
                            return;
                        }
                        long count = glDebugMessageCount.incrementAndGet();
                        if (type == KHRDebug.GL_DEBUG_TYPE_ERROR) {
                            glDebugErrorCount.incrementAndGet();
                        }
                        String text = GLDebugMessageCallback.getMessage(length, message);
                        lastGlDiagnostic = "KHR_debug " + debugSeverityName(severity)
                                + " type=0x" + Integer.toHexString(type) + " id=" + id + ": " + text;
                        if (count <= MAX_IMMEDIATE_DIAGNOSTICS || count % 100 == 0) {
                            System.err.println("[gl-debug] " + lastGlDiagnostic);
                        }
                    });
            glEnable(KHRDebug.GL_DEBUG_OUTPUT);
            glEnable(KHRDebug.GL_DEBUG_OUTPUT_SYNCHRONOUS);
            KHRDebug.glDebugMessageCallback(glDebugCallback, 0L);
            khrDebugActive = true;
            System.out.println("[gl] KHR_debug callback active");
        } catch (Throwable t) {
            if (glDebugCallback != null) {
                glDebugCallback.free();
                glDebugCallback = null;
            }
            khrDebugActive = false;
            System.err.println("[gl] KHR_debug setup failed; using glGetError polling: " + t);
        }
        pollGlErrors("startup");
    }

    private static String safeGlString(int name) {
        String value = glGetString(name);
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static String debugSeverityName(int severity) {
        return switch (severity) {
            case KHRDebug.GL_DEBUG_SEVERITY_HIGH -> "HIGH";
            case KHRDebug.GL_DEBUG_SEVERITY_MEDIUM -> "MEDIUM";
            case KHRDebug.GL_DEBUG_SEVERITY_LOW -> "LOW";
            default -> "severity=0x" + Integer.toHexString(severity);
        };
    }

    private static boolean diagnosticsEnabled() {
        String configured = System.getProperty("veylon.glDiagnostics");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("VEYLON_GL_DIAGNOSTICS");
        }
        if (configured == null || configured.isBlank()) {
            return true;
        }
        return switch (configured.trim().toLowerCase(Locale.ROOT)) {
            case "0", "false", "off", "no" -> false;
            default -> true;
        };
    }

    public void setVsync(boolean on) {
        glfwSwapInterval(on ? 1 : 0);
    }

    /** Toggles borderless fullscreen while preserving the last windowed geometry. */
    public void setFullscreen(boolean on) {
        if (handle == NULL) {
            throw new IllegalStateException("Fullscreen can only change after create()");
        }
        if (fullscreen == on) {
            return;
        }
        GLFWVidMode mode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (mode == null) {
            return;
        }
        if (on) {
            captureWindowedGeometry();
            // Set this before glfwSetWindowMonitor: GLFW may invoke size/position
            // callbacks synchronously with the monitor switch.
            fullscreen = true;
            glfwSetWindowMonitor(handle, glfwGetPrimaryMonitor(), 0, 0,
                    mode.width(), mode.height(), mode.refreshRate());
        } else {
            fullscreen = false;
            int x = windowedX == Integer.MIN_VALUE ? (mode.width() - windowedWidth) / 2 : windowedX;
            int y = windowedY == Integer.MIN_VALUE ? (mode.height() - windowedHeight) / 2 : windowedY;
            glfwSetWindowMonitor(handle, NULL, x, y, windowedWidth, windowedHeight, 0);
        }
        refreshSizes();
    }

    private void captureWindowedGeometry() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer x = stack.mallocInt(1);
            IntBuffer y = stack.mallocInt(1);
            glfwGetWindowPos(handle, x, y);
            windowedX = x.get(0);
            windowedY = y.get(0);

            x.clear();
            y.clear();
            glfwGetWindowSize(handle, x, y);
            if (x.get(0) > 0 && y.get(0) > 0) {
                windowedWidth = x.get(0);
                windowedHeight = y.get(0);
            }
        }
    }

    public void captureCursor(boolean capture, Input input) {
        if (cursorCaptured == capture) {
            return;
        }
        cursorCaptured = capture;
        glfwSetInputMode(handle, GLFW_CURSOR, capture ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
        input.resetMouseDelta();
        if (!capture) {
            glfwSetCursorPos(handle, windowWidth / 2.0, windowHeight / 2.0);
        }
    }

    public boolean isCursorCaptured() {
        return cursorCaptured;
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(handle);
    }

    public void requestClose() {
        glfwSetWindowShouldClose(handle, true);
    }

    public void swap() {
        glfwSwapBuffers(handle);
        pollGlErrors("frame");
    }

    public void poll() {
        glfwPollEvents();
    }

    public void destroy() {
        if (glReady) {
            pollGlErrors("shutdown");
            if (glDiagnosticsEnabled) {
                System.out.println("[gl] diagnostics summary: glGetError=" + glErrorCount()
                        + " KHR messages=" + glDebugMessageCount()
                        + " KHR errors=" + glDebugErrorCount()
                        + " last=" + lastGlDiagnostic());
            }
            if (khrDebugActive) {
                KHRDebug.glDebugMessageCallback(null, 0L);
                khrDebugActive = false;
            }
            if (glDebugCallback != null) {
                glDebugCallback.free();
                glDebugCallback = null;
            }
        }
        glfwDestroyWindow(handle);
        handle = NULL;
        glReady = false;
        fullscreen = false;
        glfwTerminate();
        GLFWErrorCallback cb = glfwSetErrorCallback(null);
        if (cb != null) {
            cb.free();
        }
    }

    /** Physical framebuffer width in pixels (legacy renderer-facing name). */
    public int width() {
        return framebufferWidth;
    }

    /** Physical framebuffer height in pixels (legacy renderer-facing name). */
    public int height() {
        return framebufferHeight;
    }

    public int framebufferWidth() {
        return framebufferWidth;
    }

    public int framebufferHeight() {
        return framebufferHeight;
    }

    /** Logical GLFW client width used by cursor positions and window sizing. */
    public int windowWidth() {
        return windowWidth;
    }

    /** Logical GLFW client height used by cursor positions and window sizing. */
    public int windowHeight() {
        return windowHeight;
    }

    public int windowedWidth() {
        return windowedWidth;
    }

    public int windowedHeight() {
        return windowedHeight;
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    /** Multiplier from GLFW logical cursor X to framebuffer pixel X. */
    public float cursorToFramebufferScaleX() {
        return framebufferWidth / (float) Math.max(1, windowWidth);
    }

    /** Multiplier from GLFW logical cursor Y to framebuffer pixel Y. */
    public float cursorToFramebufferScaleY() {
        return framebufferHeight / (float) Math.max(1, windowHeight);
    }

    public long handle() {
        return handle;
    }

    /** Drains and retains OpenGL errors. Called once per presented frame. */
    public void pollGlErrors(String phase) {
        if (!glReady || !glDiagnosticsEnabled) {
            return;
        }
        int drained = 0;
        int error;
        while ((error = glGetError()) != GL_NO_ERROR) {
            long count = glErrorCount.incrementAndGet();
            lastGlDiagnostic = "glGetError " + glErrorName(error) + " during " + phase;
            if (count <= MAX_IMMEDIATE_DIAGNOSTICS || count % 100 == 0) {
                System.err.println("[gl-error] #" + count + " " + lastGlDiagnostic);
            }
            if (++drained == 64) {
                System.err.println("[gl-error] stopped draining after 64 errors; remaining errors defer to next frame");
                break;
            }
        }
    }

    private static String glErrorName(int error) {
        return switch (error) {
            case GL_INVALID_ENUM -> "GL_INVALID_ENUM";
            case GL_INVALID_VALUE -> "GL_INVALID_VALUE";
            case GL_INVALID_OPERATION -> "GL_INVALID_OPERATION";
            case GL_INVALID_FRAMEBUFFER_OPERATION -> "GL_INVALID_FRAMEBUFFER_OPERATION";
            case GL_OUT_OF_MEMORY -> "GL_OUT_OF_MEMORY";
            default -> "0x" + Integer.toHexString(error);
        };
    }

    public long glErrorCount() {
        return glErrorCount.get();
    }

    public long glDebugMessageCount() {
        return glDebugMessageCount.get();
    }

    public long glDebugErrorCount() {
        return glDebugErrorCount.get();
    }

    public boolean khrDebugActive() {
        return khrDebugActive;
    }

    public String glContextSummary() {
        return glContextSummary;
    }

    public String lastGlDiagnostic() {
        return lastGlDiagnostic;
    }
}
