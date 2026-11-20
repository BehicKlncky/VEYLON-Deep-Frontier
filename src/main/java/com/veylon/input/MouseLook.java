package com.veylon.input;

import com.veylon.engine.Camera;

/**
 * Turns the view by a frame's mouse movement under the player's {@link ControlSettings} (0.9.1
 * milestone 06). At the default sensitivity and without invert-Y it turns the camera exactly as the
 * fixed turn before 0.9.1 did: by {@link ControlSettings#BASE_DEGREES_PER_PIXEL} a pixel, the pitch
 * held short of straight up and down and the yaw kept within a turn either way.
 */
public final class MouseLook {

    /** The pitch, in degrees, the view stops at looking up or down. */
    public static final float PITCH_LIMIT = 89.5f;

    private MouseLook() {
    }

    /**
     * Turns {@code camera} by {@code dx} and {@code dy} pixels of mouse movement; with invert-Y,
     * moving the mouse forward looks down.
     */
    public static void turn(Camera camera, double dx, double dy, ControlSettings settings) {
        float degrees = settings.degreesPerPixel();
        camera.yaw += (float) (dx * degrees);
        camera.pitch += (float) ((settings.invertY ? -dy : dy) * degrees);
        if (camera.pitch > PITCH_LIMIT) camera.pitch = PITCH_LIMIT;
        if (camera.pitch < -PITCH_LIMIT) camera.pitch = -PITCH_LIMIT;
        if (camera.yaw > 360) camera.yaw -= 360;
        if (camera.yaw < -360) camera.yaw += 360;
    }
}
