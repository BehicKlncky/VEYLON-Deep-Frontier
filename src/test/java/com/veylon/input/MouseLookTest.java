package com.veylon.input;

import com.veylon.engine.Camera;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The view's turn under the mouse settings (0.9.1 milestone 06): the default reproduces the fixed
 * turn of before bit for bit, the sensitivity scales both axes, and invert-Y flips the pitch alone.
 */
class MouseLookTest {

    /** The turn {@code Game.updateMouseLook} made before 0.9.1, word for word. */
    private static void fixedTurn(Camera camera, double dx, double dy) {
        float sens = 0.115f;
        camera.yaw += (float) (dx * sens);
        camera.pitch += (float) (dy * sens);
        if (camera.pitch > 89.5f) camera.pitch = 89.5f;
        if (camera.pitch < -89.5f) camera.pitch = -89.5f;
        if (camera.yaw > 360) camera.yaw -= 360;
        if (camera.yaw < -360) camera.yaw += 360;
    }

    private static final double[][] MOVES = {{0, 0}, {3, -2}, {-7.5, 11.25}, {120, 40}, {-900, 900},
            {0.001, -0.003}, {4_000, -4_000}, {-3_131.7, 2_000.2}};

    @Test
    void theDefaultTurnsTheViewExactlyAsTheFixedTurnDid() {
        Camera before = new Camera(), now = new Camera();
        ControlSettings settings = new ControlSettings();
        for (int round = 0; round < 3; round++) {
            for (double[] move : MOVES) {
                fixedTurn(before, move[0], move[1]);
                MouseLook.turn(now, move[0], move[1], settings);
                assertEquals(Float.floatToIntBits(before.yaw), Float.floatToIntBits(now.yaw), "yaw after " + move[0]);
                assertEquals(Float.floatToIntBits(before.pitch), Float.floatToIntBits(now.pitch), "pitch after " + move[1]);
            }
        }
    }

    @Test
    void theSensitivityScalesBothAxesAndInvertYFlipsThePitchAlone() {
        ControlSettings twice = new ControlSettings();
        twice.setSensitivity(200);
        Camera camera = new Camera();
        MouseLook.turn(camera, 10, 20, twice);
        assertEquals(2.3f, camera.yaw, 1e-5f, "twice the default turn");
        assertEquals(4.6f, camera.pitch, 1e-5f);

        ControlSettings inverted = new ControlSettings();
        inverted.invertY = true;
        Camera plain = new Camera(), flipped = new Camera();
        MouseLook.turn(plain, 15, -30, new ControlSettings());
        MouseLook.turn(flipped, 15, -30, inverted);
        assertEquals(plain.yaw, flipped.yaw, "the yaw is never inverted");
        assertEquals(-plain.pitch, flipped.pitch, "the pitch is");
        assertTrue(plain.pitch < 0 && flipped.pitch > 0,
                "moving the mouse forward looks up, or down with invert on (positive pitch looks down)");
    }

    @Test
    void thePitchStopsShortOfStraightUpAndDownAtAnySensitivity() {
        ControlSettings fastest = new ControlSettings();
        fastest.setSensitivity(ControlSettings.MAX_SENSITIVITY);
        Camera camera = new Camera();
        MouseLook.turn(camera, 0, 10_000, fastest);
        assertEquals(MouseLook.PITCH_LIMIT, camera.pitch);
        MouseLook.turn(camera, 0, -10_000, fastest);
        assertEquals(-MouseLook.PITCH_LIMIT, camera.pitch);
    }
}
