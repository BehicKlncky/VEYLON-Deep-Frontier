package com.veylon.util;

public final class MathUtil {

    private MathUtil() {
    }

    public static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    public static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static int floorDiv16(int v) {
        return Math.floorDiv(v, 16);
    }

    public static int floorMod16(int v) {
        return Math.floorMod(v, 16);
    }

    /** Moves current toward target by at most maxDelta. */
    public static float approach(float current, float target, float maxDelta) {
        if (current < target) {
            return Math.min(current + maxDelta, target);
        }
        return Math.max(current - maxDelta, target);
    }
}
