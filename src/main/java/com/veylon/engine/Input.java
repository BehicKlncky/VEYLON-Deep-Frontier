package com.veylon.engine;

import static org.lwjgl.glfw.GLFW.*;

public class Input {

    private final boolean[] keyDown = new boolean[GLFW_KEY_LAST + 1];
    private final boolean[] keyPressed = new boolean[GLFW_KEY_LAST + 1];
    private final boolean[] mouseDown = new boolean[8];
    private final boolean[] mousePressed = new boolean[8];

    private double cursorX, cursorY;
    private double cursorScaleX = 1.0, cursorScaleY = 1.0;
    private double lastX, lastY;
    private double deltaX, deltaY;
    private double scrollY;
    private boolean firstMouse = true;

    void onKey(int key, int action) {
        if (key < 0 || key > GLFW_KEY_LAST) {
            return;
        }
        if (action == GLFW_PRESS) {
            keyDown[key] = true;
            keyPressed[key] = true;
        } else if (action == GLFW_RELEASE) {
            keyDown[key] = false;
        }
    }

    void onMouseButton(int button, int action) {
        if (button < 0 || button >= 8) {
            return;
        }
        if (action == GLFW_PRESS) {
            mouseDown[button] = true;
            mousePressed[button] = true;
        } else if (action == GLFW_RELEASE) {
            mouseDown[button] = false;
        }
    }

    void onCursorPos(double x, double y) {
        cursorX = x;
        cursorY = y;
        if (firstMouse) {
            lastX = x;
            lastY = y;
            firstMouse = false;
        }
        deltaX += x - lastX;
        deltaY += y - lastY;
        lastX = x;
        lastY = y;
    }

    void onScroll(double dy) {
        scrollY += dy;
    }

    public boolean isKeyDown(int key) {
        return key >= 0 && key <= GLFW_KEY_LAST && keyDown[key];
    }

    public boolean wasKeyPressed(int key) {
        return key >= 0 && key <= GLFW_KEY_LAST && keyPressed[key];
    }

    public boolean isMouseDown(int button) {
        return button >= 0 && button < 8 && mouseDown[button];
    }

    public boolean wasMousePressed(int button) {
        return button >= 0 && button < 8 && mousePressed[button];
    }

    public double mouseDX() {
        return deltaX;
    }

    public double mouseDY() {
        return deltaY;
    }

    public double cursorX() {
        return cursorX * cursorScaleX;
    }

    public double cursorY() {
        return cursorY * cursorScaleY;
    }

    /**
     * Scales GLFW logical-window pointer coordinates into the active UI canvas.
     * Mouse-look deltas intentionally remain in raw logical pixels.
     */
    public void setCursorScale(double x, double y) {
        cursorScaleX = Double.isFinite(x) && x > 0 ? x : 1.0;
        cursorScaleY = Double.isFinite(y) && y > 0 ? y : 1.0;
    }

    public double scrollDelta() {
        return scrollY;
    }

    public void resetMouseDelta() {
        deltaX = 0;
        deltaY = 0;
        firstMouse = true;
    }

    /** Call once per frame after all input has been consumed. */
    public void endFrame() {
        java.util.Arrays.fill(keyPressed, false);
        java.util.Arrays.fill(mousePressed, false);
        deltaX = 0;
        deltaY = 0;
        scrollY = 0;
    }
}
