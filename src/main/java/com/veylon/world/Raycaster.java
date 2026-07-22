package com.veylon.world;

import org.joml.Vector3f;

/** Voxel DDA raycast (Amanatides & Woo). */
public final class Raycaster {

    /** Read-only view shared by immutable and caller-owned raycast results. */
    public interface Result {
        int x();

        int y();

        int z();

        int nx();

        int ny();

        int nz();

        double dist();

        BlockType type();
    }

    /** Convenient immutable result for occasional/non-hot callers. */
    public record Hit(int x, int y, int z, int nx, int ny, int nz,
                      double dist, BlockType type) implements Result {
    }

    /**
     * Caller-owned hot-path result. A miss clears the result, and separate
     * instances never overwrite one another.
     */
    public static final class MutableHit implements Result {
        private int x;
        private int y;
        private int z;
        private int nx;
        private int ny;
        private int nz;
        private double dist;
        private BlockType type;

        @Override
        public int x() {
            return x;
        }

        @Override
        public int y() {
            return y;
        }

        @Override
        public int z() {
            return z;
        }

        @Override
        public int nx() {
            return nx;
        }

        @Override
        public int ny() {
            return ny;
        }

        @Override
        public int nz() {
            return nz;
        }

        @Override
        public double dist() {
            return dist;
        }

        @Override
        public BlockType type() {
            return type;
        }

        public boolean hit() {
            return type != null;
        }

        /** Copies this mutable value for storage beyond the next cast. */
        public Hit immutableCopy() {
            return hit() ? new Hit(x, y, z, nx, ny, nz, dist, type) : null;
        }

        private void set(int x, int y, int z, int nx, int ny, int nz,
                         double dist, BlockType type) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
            this.dist = dist;
            this.type = type;
        }

        private void clear() {
            x = y = z = nx = ny = nz = 0;
            dist = 0;
            type = null;
        }
    }

    private Raycaster() {
    }

    /**
     * Casts a ray and returns the first targetable block, or null.
     *
     * @param includeFluids if true, water counts as a hit (used for drinking).
     */
    public static Hit cast(World world, Vector3f origin, Vector3f dir, double maxDist, boolean includeFluids) {
        MutableHit result = new MutableHit();
        return castInto(world, origin, dir, maxDist, includeFluids, result)
                ? result.immutableCopy() : null;
    }

    /**
     * Allocation-free cast into a caller-owned result buffer.
     *
     * <p>The DDA visits one voxel at a time by advancing the nearest axis
     * boundary. The normal is the opposite of that boundary step, and the
     * distance is measured along the normalized input direction. The origin
     * cell is intentionally not returned, preserving the historical targeting
     * behavior for a camera starting inside a block.</p>
     *
     * @return true when {@code result} contains a hit; false after clearing it
     */
    public static boolean castInto(World world, Vector3f origin, Vector3f dir,
                                   double maxDist, boolean includeFluids,
                                   MutableHit result) {
        if (world == null || origin == null || dir == null || result == null) {
            throw new IllegalArgumentException("raycast arguments must be non-null");
        }
        result.clear();
        double ox = origin.x, oy = origin.y, oz = origin.z;
        double dx = dir.x, dy = dir.y, dz = dir.z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-9) {
            return false;
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
        boolean advanced = false;

        for (int i = 0; i < 512; i++) {
            BlockType type = world.getBlock(x, y, z);
            boolean hit = includeFluids ? type != BlockType.AIR : type.isTargetable();
            if (hit && advanced) {
                result.set(x, y, z, nx, ny, nz, t, type);
                return true;
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
            advanced = true;
            if (t > maxDist) {
                return false;
            }
        }
        return false;
    }
}
