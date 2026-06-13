package com.veylon.world;

import org.joml.Vector3f;

/** Voxel DDA raycast (Amanatides & Woo). */
public final class Raycaster {

    public record Hit(int x, int y, int z, int nx, int ny, int nz, double dist, BlockType type) {
    }

    private Raycaster() {
    }

    /**
     * Casts a ray and returns the first targetable block, or null.
     *
     * @param includeFluids if true, water counts as a hit (used for drinking).
     */
    public static Hit cast(World world, Vector3f origin, Vector3f dir, double maxDist, boolean includeFluids) {
        double ox = origin.x, oy = origin.y, oz = origin.z;
        double dx = dir.x, dy = dir.y, dz = dir.z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-9) {
            return null;
        }
        dx /= len;
        dy /= len;
        dz /= len;

        int x = (int) Math.floor(ox);
        int y = (int) Math.floor(oy);
        int z = (int) Math.floor(oz);

        int stepX = dx > 0 ? 1 : -1;
        int stepY = dy > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;

        double tDeltaX = dx != 0 ? Math.abs(1.0 / dx) : Double.POSITIVE_INFINITY;
        double tDeltaY = dy != 0 ? Math.abs(1.0 / dy) : Double.POSITIVE_INFINITY;
        double tDeltaZ = dz != 0 ? Math.abs(1.0 / dz) : Double.POSITIVE_INFINITY;

        double tMaxX = dx != 0 ? ((dx > 0 ? (x + 1 - ox) : (ox - x)) * tDeltaX) : Double.POSITIVE_INFINITY;
        double tMaxY = dy != 0 ? ((dy > 0 ? (y + 1 - oy) : (oy - y)) * tDeltaY) : Double.POSITIVE_INFINITY;
        double tMaxZ = dz != 0 ? ((dz > 0 ? (z + 1 - oz) : (oz - z)) * tDeltaZ) : Double.POSITIVE_INFINITY;

        int nx = 0, ny = 0, nz = 0;
        double t = 0;

        for (int i = 0; i < 512; i++) {
            BlockType type = world.getBlock(x, y, z);
            boolean hit = includeFluids ? type != BlockType.AIR : type.isTargetable();
            if (hit && t > 0) {
                return new Hit(x, y, z, nx, ny, nz, t, type);
            }
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                t = tMaxX;
                tMaxX += tDeltaX;
                x += stepX;
                nx = -stepX;
                ny = 0;
                nz = 0;
            } else if (tMaxY < tMaxZ) {
                t = tMaxY;
                tMaxY += tDeltaY;
                y += stepY;
                nx = 0;
                ny = -stepY;
                nz = 0;
            } else {
                t = tMaxZ;
                tMaxZ += tDeltaZ;
                z += stepZ;
                nx = 0;
                ny = 0;
                nz = -stepZ;
            }
            if (t > maxDist) {
                return null;
            }
        }
        return null;
    }
}
