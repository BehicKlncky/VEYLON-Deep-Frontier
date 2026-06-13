package com.veylon.engine;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Window {

    private long handle;
    private int width = 1280;
    private int height = 720;
    private boolean cursorCaptured;

    public void create(String title, Input input) {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

        handle = glfwCreateWindow(width, height, title, NULL, NULL);
        if (handle == NULL) {
            throw new RuntimeException("Failed to create GLFW window");
        }

        GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (vidMode != null) {
            glfwSetWindowPos(handle, (vidMode.width() - width) / 2, (vidMode.height() - height) / 2);
        }

        glfwSetFramebufferSizeCallback(handle, (win, w, h) -> {
            if (w > 0 && h > 0) {
                width = w;
                height = h;
            }
        });
        glfwSetKeyCallback(handle, (win, key, scancode, action, mods) -> input.onKey(key, action));
        glfwSetMouseButtonCallback(handle, (win, button, action, mods) -> input.onMouseButton(button, action));
        glfwSetCursorPosCallback(handle, (win, x, y) -> input.onCursorPos(x, y));
        glfwSetScrollCallback(handle, (win, dx, dy) -> input.onScroll(dy));

        glfwMakeContextCurrent(handle);
        GL.createCapabilities();
        glfwSwapInterval(1);
        glfwShowWindow(handle);
    }

    public void captureCursor(boolean capture, Input input) {
        if (cursorCaptured == capture) {
            return;
        }
        cursorCaptured = capture;
        glfwSetInputMode(handle, GLFW_CURSOR, capture ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
        input.resetMouseDelta();
        if (!capture) {
            glfwSetCursorPos(handle, width / 2.0, height / 2.0);
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
    }

    public void poll() {
        glfwPollEvents();
    }

    public void destroy() {
        glfwDestroyWindow(handle);
        glfwTerminate();
        GLFWErrorCallback cb = glfwSetErrorCallback(null);
        if (cb != null) {
            cb.free();
        }
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public long handle() {
        return handle;
    }
}
