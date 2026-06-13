package com.veylon.util;

/** Deterministic seeded value noise with fBm, used by all world generation. */
public final class Noise {

    private final long seed;

    public Noise(long seed) {
        this.seed = seed;
    }

    public static long mix(long h) {
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }

    private double lattice2(int x, int y) {
        long h = mix(seed ^ (x * 0x9E3779B97F4A7C15L) ^ ((long) y * 0xC2B2AE3D27D4EB4FL));
        return (h >>> 11) * (1.0 / (1L << 53)) * 2.0 - 1.0;
    }

    private double lattice3(int x, int y, int z) {
        long h = mix(seed ^ (x * 0x9E3779B97F4A7C15L) ^ ((long) y * 0xC2B2AE3D27D4EB4FL)
                ^ ((long) z * 0x165667B19E3779F9L));
        return (h >>> 11) * (1.0 / (1L << 53)) * 2.0 - 1.0;
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Value noise in [-1, 1]. */
    public double value2(double x, double y) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        double tx = smooth(x - x0);
        double ty = smooth(y - y0);
        double a = lattice2(x0, y0);
        double b = lattice2(x0 + 1, y0);
        double c = lattice2(x0, y0 + 1);
        double d = lattice2(x0 + 1, y0 + 1);
        return lerp(lerp(a, b, tx), lerp(c, d, tx), ty);
    }

    /** 3D value noise in [-1, 1]. */
    public double value3(double x, double y, double z) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int z0 = (int) Math.floor(z);
        double tx = smooth(x - x0);
        double ty = smooth(y - y0);
        double tz = smooth(z - z0);
        double c000 = lattice3(x0, y0, z0);
        double c100 = lattice3(x0 + 1, y0, z0);
        double c010 = lattice3(x0, y0 + 1, z0);
        double c110 = lattice3(x0 + 1, y0 + 1, z0);
        double c001 = lattice3(x0, y0, z0 + 1);
        double c101 = lattice3(x0 + 1, y0, z0 + 1);
        double c011 = lattice3(x0, y0 + 1, z0 + 1);
        double c111 = lattice3(x0 + 1, y0 + 1, z0 + 1);
        double x00 = lerp(c000, c100, tx);
        double x10 = lerp(c010, c110, tx);
        double x01 = lerp(c001, c101, tx);
        double x11 = lerp(c011, c111, tx);
        return lerp(lerp(x00, x10, ty), lerp(x01, x11, ty), tz);
    }

    /** Fractal brownian motion of value2, normalized to [-1, 1]. */
    public double fbm2(double x, double y, int octaves, double lacunarity, double gain) {
        double sum = 0, amp = 1, freq = 1, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += value2(x * freq, y * freq) * amp;
            norm += amp;
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / norm;
    }

    public double fbm3(double x, double y, double z, int octaves, double lacunarity, double gain) {
        double sum = 0, amp = 1, freq = 1, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += value3(x * freq, y * freq, z * freq) * amp;
            norm += amp;
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / norm;
    }

    /** Deterministic per-cell random in [0, 1). */
    public double rand2(int x, int y) {
        long h = mix(seed ^ (x * 668265263L) ^ ((long) y * 2246822519L));
        return (h >>> 11) * (1.0 / (1L << 53));
    }
}
