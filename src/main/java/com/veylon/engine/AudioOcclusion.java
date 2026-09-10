package com.veylon.engine;

/** Bounded voxel sampling for presentation only; never participates in combat line of sight. */
final class AudioOcclusion {
    /** Maximum voxel probes per source-to-listener segment. */
    static final int MAX_STEPS = 64;
    /** New and existing sources share this ray allowance per frame. */
    static final int RAYS_PER_FRAME = 4;
    /** Preferred spacing in blocks; long rays use coarser uniform samples to retain the cap. */
    static final float STEP_BLOCKS = 0.5f;
    /** Solid samples needed for full muffling. */
    static final int SOLID_SATURATION = 3;

    @FunctionalInterface interface Voxels { boolean solid(int x, int y, int z); }

    /** Shared accounting includes initial sound submissions and later reevaluations. */
    static final class Budget {
        int rays, queries, peakRays, peakQueries;
        boolean available() { return rays < RAYS_PER_FRAME; }
        void nextFrame() {
            peakRays = Math.max(peakRays, rays);
            peakQueries = Math.max(peakQueries, queries);
            rays = queries = 0;
        }
    }

    private AudioOcclusion() { }

    static float trace(Voxels voxels, float ax, float ay, float az,
                       float bx, float by, float bz, Budget budget) {
        if (!budget.available()) return 0;
        budget.rays++;
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.min(MAX_STEPS, Math.max(1, (int) Math.ceil(distance / STEP_BLOCKS)));
        int solid = 0;
        for (int i = 1; i <= steps; i++) {
            float fraction = i / (float) (steps + 1);
            budget.queries++;
            if (voxels.solid((int) Math.floor(ax + dx * fraction),
                    (int) Math.floor(ay + dy * fraction), (int) Math.floor(az + dz * fraction))) {
                if (++solid == SOLID_SATURATION) return 1;
            }
        }
        return solid / (float) SOLID_SATURATION;
    }
}
