package com.veylon.util;

/** Immutable integer block coordinate, usable as a map key. */
public record Vec3i(int x, int y, int z) {

    public Vec3i offset(int dx, int dy, int dz) {
        return new Vec3i(x + dx, y + dy, z + dz);
    }

    public double distSq(double px, double py, double pz) {
        double dx = x + 0.5 - px, dy = y + 0.5 - py, dz = z + 0.5 - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public String toString() {
        return x + ", " + y + ", " + z;
    }
}
