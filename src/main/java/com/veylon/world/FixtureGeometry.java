package com.veylon.world;

/**
 * The one definition of where a thin fixture sits inside its cell, shared by
 * {@link ChunkMesher} and {@link Raycaster} so what the player sees is what the
 * crosshair hits. An open door or casement ({@link BlockType.Shape#LEAF}) is a
 * leaf along the jamb on the low side of the opening; a broken door or a
 * shattered window ({@link BlockType.Shape#PANE}) is a frame in the wall plane.
 * Both are non-solid, so the rest of the cell is open passage.
 *
 * <p>The wall an opening sits in is read from its neighbours: the axis with
 * more solid or fixture cells beside the opening is the wall's direction, ties
 * go to X. A free-standing fixture therefore faces along Z.
 */
public final class FixtureGeometry {

    /** Thickness of an open door or casement leaf. */
    public static final float LEAF_THICKNESS = 0.125f;
    /** Thickness of a broken door or window frame. */
    public static final float PANE_THICKNESS = 0.10f;

    private FixtureGeometry() {
    }

    /** True when the wall around the opening runs along X, so the opening faces along Z. */
    public static boolean wallAlongX(World w, int x, int y, int z) {
        int alongX = walled(w, x - 1, y, z) + walled(w, x + 1, y, z);
        int alongZ = walled(w, x, y, z - 1) + walled(w, x, y, z + 1);
        if (alongX == alongZ) {
            // Look at the rest of a stacked opening before settling a tie.
            BlockType self = w.getBlock(x, y, z);
            for (int dy = -1; dy <= 1; dy += 2) {
                if (Fixtures.isFixture(w.getBlock(x, y + dy, z)) && Fixtures.isFixture(self)) {
                    alongX += walled(w, x - 1, y + dy, z) + walled(w, x + 1, y + dy, z);
                    alongZ += walled(w, x, y + dy, z - 1) + walled(w, x, y + dy, z + 1);
                }
            }
        }
        return alongX >= alongZ;
    }

    private static int walled(World w, int x, int y, int z) {
        BlockType t = w.getBlock(x, y, z);
        return t.solid || Fixtures.isFixture(t) ? 1 : 0;
    }

    /** True for the shapes whose visible and targetable volume is a thin box. */
    public static boolean isThin(BlockType t) {
        return t.shape == BlockType.Shape.LEAF || t.shape == BlockType.Shape.PANE;
    }

    /**
     * Writes the thin box of a {@code LEAF} or {@code PANE} block at (x, y, z) as
     * {@code x0, y0, z0, x1, y1, z1} into {@code out}.
     *
     * @return false, leaving {@code out} untouched, for any other shape
     */
    public static boolean thinBox(World w, int x, int y, int z, BlockType t, float[] out) {
        if (!isThin(t)) {
            return false;
        }
        boolean alongX = wallAlongX(w, x, y, z);
        if (t.shape == BlockType.Shape.LEAF) {
            // Swung open against the low jamb: perpendicular to the wall.
            if (alongX) {
                set(out, x, y, z, x + LEAF_THICKNESS, y + 1, z + 1);
            } else {
                set(out, x, y, z, x + 1, y + 1, z + LEAF_THICKNESS);
            }
        } else {
            float h = PANE_THICKNESS / 2f;
            if (alongX) {
                set(out, x, y, z + 0.5f - h, x + 1, y + 1, z + 0.5f + h);
            } else {
                set(out, x + 0.5f - h, y, z, x + 0.5f + h, y + 1, z + 1);
            }
        }
        return true;
    }

    private static void set(float[] out, float x0, float y0, float z0, float x1, float y1, float z1) {
        out[0] = x0;
        out[1] = y0;
        out[2] = z0;
        out[3] = x1;
        out[4] = y1;
        out[5] = z1;
    }

    /**
     * True when a ray from (ox, oy, oz) along the unit direction (dx, dy, dz)
     * crosses the thin box of the block at (x, y, z) within {@code maxT}.
     * Allocation-free: it runs inside the per-frame targeting raycast.
     */
    static boolean rayHitsThin(World w, int x, int y, int z, BlockType t,
                               double ox, double oy, double oz,
                               double dx, double dy, double dz, double maxT) {
        double x0, y0 = y, z0, x1, y1 = y + 1.0, z1;
        boolean alongX = wallAlongX(w, x, y, z);
        if (t.shape == BlockType.Shape.LEAF) {
            if (alongX) {
                x0 = x;
                x1 = x + LEAF_THICKNESS;
                z0 = z;
                z1 = z + 1.0;
            } else {
                x0 = x;
                x1 = x + 1.0;
                z0 = z;
                z1 = z + LEAF_THICKNESS;
            }
        } else {
            double h = PANE_THICKNESS / 2.0;
            if (alongX) {
                x0 = x;
                x1 = x + 1.0;
                z0 = z + 0.5 - h;
                z1 = z + 0.5 + h;
            } else {
                x0 = x + 0.5 - h;
                x1 = x + 0.5 + h;
                z0 = z;
                z1 = z + 1.0;
            }
        }
        return slabHit(ox, oy, oz, dx, dy, dz, x0, y0, z0, x1, y1, z1, maxT);
    }

    /** Standard slab test: the ray enters the box at a distance in [0, maxT]. */
    private static boolean slabHit(double ox, double oy, double oz, double dx, double dy, double dz,
                                   double x0, double y0, double z0, double x1, double y1, double z1,
                                   double maxT) {
        double tMin = 0, tMax = maxT;
        if (Math.abs(dx) < 1e-12) {
            if (ox < x0 || ox > x1) {
                return false;
            }
        } else {
            double a = (x0 - ox) / dx, b = (x1 - ox) / dx;
            tMin = Math.max(tMin, Math.min(a, b));
            tMax = Math.min(tMax, Math.max(a, b));
        }
        if (Math.abs(dy) < 1e-12) {
            if (oy < y0 || oy > y1) {
                return false;
            }
        } else {
            double a = (y0 - oy) / dy, b = (y1 - oy) / dy;
            tMin = Math.max(tMin, Math.min(a, b));
            tMax = Math.min(tMax, Math.max(a, b));
        }
        if (Math.abs(dz) < 1e-12) {
            if (oz < z0 || oz > z1) {
                return false;
            }
        } else {
            double a = (z0 - oz) / dz, b = (z1 - oz) / dz;
            tMin = Math.max(tMin, Math.min(a, b));
            tMax = Math.min(tMax, Math.max(a, b));
        }
        return tMin <= tMax;
    }
}
