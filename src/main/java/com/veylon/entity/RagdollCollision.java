package com.veylon.entity;

import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.World;

/**
 * Allocation-free axis-resolved movement for a ragdoll point mass.
 *
 * <p>The shape is deliberately the same as {@code VoxelPhysics.moveAxis}: each
 * axis is walked in sub-steps no longer than
 * {@link RagdollConstants#MAX_AXIS_SUBSTEP} and backed off the moment the swept
 * box overlaps something, so a body carrying an explosion's worth of velocity
 * cannot step straight through a floor.
 *
 * <p>The one rule that is not {@code VoxelPhysics}'s is the important one:
 * {@code World.getBlock} answers {@code AIR} for a chunk column nobody has
 * generated, which means "not built yet", not "empty". This treats an unloaded
 * column as solid, so a body that dies at the streaming frontier settles on the
 * spot instead of falling forever into ungenerated space.
 *
 * <p>Column lookups go through the same direct-mapped cache
 * {@code RainCollision} uses, and for the same two reasons: {@code World}
 * retains voxel columns for its lifetime so a cached reference stays valid and
 * shows edits immediately, and going through {@code World.getChunk} for every
 * one of the thousands of voxel probes a field of bodies makes would box a key
 * on each miss. Chunk <em>references</em> are cached; voxel contents and absent
 * columns never are.
 */
final class RagdollCollision {

    private static final int COLUMNS = 32;

    /** Which axes the last {@link #move} was stopped on. */
    boolean hitX;
    boolean hitY;
    boolean hitZ;
    /** True when the last move ended resting on something below. */
    boolean grounded;

    /** Result position of the last {@link #move}. */
    float x;
    float y;
    float z;

    private World cachedWorld;
    private final Chunk[] columns = new Chunk[COLUMNS];

    void reset() {
        cachedWorld = null;
        java.util.Arrays.fill(columns, null);
    }

    void move(World world, float fromX, float fromY, float fromZ,
              float dx, float dy, float dz, float halfWidth, float halfHeight) {
        x = fromX;
        y = fromY;
        z = fromZ;
        hitX = hitY = hitZ = false;
        grounded = false;
        hitX = axis(world, 0, dx, halfWidth, halfHeight);
        hitZ = axis(world, 2, dz, halfWidth, halfHeight);
        hitY = axis(world, 1, dy, halfWidth, halfHeight);
        if (hitY && dy <= 0) {
            grounded = true;
        }
    }

    /** True when a body already overlaps geometry and has to be pushed free. */
    boolean blocked(World world, float px, float py, float pz,
                    float halfWidth, float halfHeight) {
        int x0 = (int) Math.floor(px - halfWidth);
        int x1 = (int) Math.floor(px + halfWidth);
        int y0 = (int) Math.floor(py - halfHeight);
        int y1 = (int) Math.floor(py + halfHeight);
        int z0 = (int) Math.floor(pz - halfWidth);
        int z1 = (int) Math.floor(pz + halfWidth);
        for (int bx = x0; bx <= x1; bx++) {
            for (int bz = z0; bz <= z1; bz++) {
                Chunk chunk = column(world, Math.floorDiv(bx, Chunk.SX),
                        Math.floorDiv(bz, Chunk.SZ));
                if (chunk == null) {
                    return true; // ungenerated space is not empty space
                }
                int lx = Math.floorMod(bx, Chunk.SX);
                int lz = Math.floorMod(bz, Chunk.SZ);
                for (int by = y0; by <= y1; by++) {
                    if (by < 0) {
                        return true;
                    }
                    if (by < Chunk.SY && chunk.get(lx, by, lz).solid) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Submerged bodies sink slowly; matches the entity water rules. */
    boolean inWater(World world, float px, float py, float pz) {
        int by = (int) Math.floor(py);
        if (by < 0 || by >= Chunk.SY) {
            return false;
        }
        int bx = (int) Math.floor(px);
        int bz = (int) Math.floor(pz);
        Chunk chunk = column(world, Math.floorDiv(bx, Chunk.SX), Math.floorDiv(bz, Chunk.SZ));
        return chunk != null && chunk.get(Math.floorMod(bx, Chunk.SX), by,
                Math.floorMod(bz, Chunk.SZ)) == BlockType.WATER;
    }

    /** Minimum face push-out for a sampled capsule point, including world edits. */
    void project(World world, float px, float py, float pz, float radius) {
        x = px; y = py; z = pz;
        grounded = false;
        for (int pass = 0; pass < 8; pass++) {
            float best = Float.POSITIVE_INFINITY, correction = 0;
            int axis = -1;
            for (int bx = (int) Math.floor(x - radius); bx <= (int) Math.floor(x + radius); bx++) {
                for (int bz = (int) Math.floor(z - radius); bz <= (int) Math.floor(z + radius); bz++) {
                    Chunk chunk = column(world, Math.floorDiv(bx, Chunk.SX), Math.floorDiv(bz, Chunk.SZ));
                    if (chunk == null) continue; // movement already treats this as a barrier
                    for (int by = (int) Math.floor(y - radius); by <= (int) Math.floor(y + radius); by++) {
                        if (by >= Chunk.SY || (by >= 0 && !chunk.get(Math.floorMod(bx, Chunk.SX), by,
                                Math.floorMod(bz, Chunk.SZ)).solid)) continue;
                        for (int a = 0; a < 3; a++) {
                            float p = a == 0 ? x : a == 1 ? y : z;
                            int block = a == 0 ? bx : a == 1 ? by : bz;
                            float lo = block - radius - p - RagdollConstants.CONTACT_SKIN;
                            float hi = block + 1 + radius - p + RagdollConstants.CONTACT_SKIN;
                            float c = -lo < hi ? lo : hi;
                            if (Math.abs(c) < best) { best = Math.abs(c); correction = c; axis = a; }
                        }
                    }
                }
            }
            if (axis < 0) return;
            offset(axis, correction);
            grounded |= axis == 1 && correction > 0;
        }
    }

    boolean loaded(World world, float px, float pz) {
        return column(world, Math.floorDiv((int) Math.floor(px), Chunk.SX),
                Math.floorDiv((int) Math.floor(pz), Chunk.SZ)) != null;
    }

    /**
     * World retains voxel columns for its lifetime (only GPU meshes unload), so
     * caching a reference keeps edits visible. Absent columns are never cached.
     */
    private Chunk column(World world, int cx, int cz) {
        if (world != cachedWorld) {
            java.util.Arrays.fill(columns, null);
            cachedWorld = world;
        }
        int slot = (cx * 31 + cz * 7) & (COLUMNS - 1);
        Chunk chunk = columns[slot];
        if (chunk == null || chunk.cx != cx || chunk.cz != cz) {
            chunk = world.getChunk(cx, cz);
            columns[slot] = chunk;
        }
        return chunk;
    }

    private boolean axis(World world, int which, float distance,
                         float halfWidth, float halfHeight) {
        if (distance == 0 || !Float.isFinite(distance)) {
            return false;
        }
        float remaining = distance;
        float sign = Math.signum(distance);
        while (Math.abs(remaining) > 1e-7f) {
            float step = sign * Math.min(RagdollConstants.MAX_AXIS_SUBSTEP, Math.abs(remaining));
            remaining -= step;
            offset(which, step);
            if (blocked(world, x, y, z, halfWidth, halfHeight)) {
                offset(which, -step);
                // Resolve the face to sub-millimetre precision instead of hovering
                // up to MAX_AXIS_SUBSTEP away from it.
                float free = 0, blocked = step;
                for (int i = 0; i < 9; i++) {
                    float middle = (free + blocked) * 0.5f;
                    offset(which, middle);
                    boolean hit = blocked(world, x, y, z, halfWidth, halfHeight);
                    offset(which, -middle);
                    if (hit) blocked = middle; else free = middle;
                }
                offset(which, free);
                return true;
            }
        }
        return false;
    }

    private void offset(int which, float step) {
        switch (which) {
            case 0 -> x += step;
            case 1 -> y += step;
            default -> z += step;
        }
    }
}
