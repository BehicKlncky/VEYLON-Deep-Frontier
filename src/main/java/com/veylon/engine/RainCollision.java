package com.veylon.engine;

import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.World;

/** Reusable, allocation-free segment traversal. Never generates chunks or edits voxels. */
public final class RainCollision {
    public static final int CLEAR = 0, HIT = 1, UNAVAILABLE = 2;
    private static final int MAX_STEPS = 512;
    /** Matches the still-water mesh top in ChunkMesher. */
    private static final float WATER_TOP = 0.88f;
    public float x, y, z;
    public int nx, ny, nz;
    public BlockType material;
    public int probes;
    private World cachedWorld;
    private final Chunk[] columns = new Chunk[32];

    void reset() {
        cachedWorld = null;
        java.util.Arrays.fill(columns, null);
        material = null;
    }

    private Chunk loadedColumn(World world, int cx, int cz) {
        if (world != cachedWorld) {
            java.util.Arrays.fill(columns, null);
            cachedWorld = world;
        }
        int slot = (cx * 31 + cz * 7) & (columns.length - 1);
        Chunk chunk = columns[slot];
        if (chunk == null || chunk.cx != cx || chunk.cz != cz) {
            chunk = world.getChunk(cx, cz);
            columns[slot] = chunk;
        }
        // World retains voxel columns for its lifetime (only GPU meshes unload).
        // Cache references, never voxel contents or absent columns, so edits stay visible.
        return chunk;
    }

    public int trace(World world, float ax, float ay, float az, float bx, float by, float bz) {
        probes = 0;
        material = null;
        nx = ny = nz = 0;
        if (!valid(ax, ay, az) || !valid(bx, by, bz)) return UNAVAILABLE;
        int ix = (int) Math.floor(ax), iy = (int) Math.floor(ay), iz = (int) Math.floor(az);
        double dx = (double) bx - ax, dy = (double) by - ay, dz = (double) bz - az;
        int sx = Double.compare(dx, 0), sy = Double.compare(dy, 0), sz = Double.compare(dz, 0);
        double tx = boundary(ax, ix, dx), ty = boundary(ay, iy, dy), tz = boundary(az, iz, dz);
        double stepx = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dx);
        double stepy = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dy);
        double stepz = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dz);
        double t = 0;
        Chunk chunk = null;
        for (int step = 0; step < MAX_STEPS; step++) {
            int cx = Math.floorDiv(ix, 16), cz = Math.floorDiv(iz, 16);
            if (chunk == null || chunk.cx != cx || chunk.cz != cz) chunk = loadedColumn(world, cx, cz);
            if (chunk == null || iy < 0) return UNAVAILABLE;
            probes++;
            BlockType block = iy >= Chunk.SY ? BlockType.AIR
                    : chunk.get(Math.floorMod(ix, 16), iy, Math.floorMod(iz, 16));
            double contact = t;
            boolean hit = block.solid;
            if (block == BlockType.WATER) {
                // Water occupies a shorter box than a solid voxel; traverse the air gap above it.
                if (ay + dy * t <= iy + WATER_TOP) hit = true;
                else if (dy < 0) {
                    contact = (iy + WATER_TOP - ay) / dy;
                    hit = contact <= Math.min(1, Math.min(tx, Math.min(ty, tz)));
                    if (hit) { nx = nz = 0; ny = 1; }
                }
            }
            if (hit) {
                // A newly placed block may envelop a drop. Retire it without fabricating a surface hit.
                if (contact == 0 && nx == 0 && ny == 0 && nz == 0) return UNAVAILABLE;
                x = (float) (ax + dx * contact);
                y = (float) (ay + dy * contact);
                z = (float) (az + dz * contact);
                material = block;
                return HIT;
            }
            double next = Math.min(tx, Math.min(ty, tz));
            if (next > 1) return CLEAR;
            t = next;
            nx = ny = nz = 0;
            // Cross all tied planes: touching only a voxel edge is not entering its interior.
            if (tx == next) { ix += sx; tx += stepx; nx = -sx; }
            if (ty == next) { iy += sy; ty += stepy; ny = -sy; }
            if (tz == next) { iz += sz; tz += stepz; nz = -sz; }
        }
        // Bound pathological segments without allowing an unchecked continuation through a roof.
        return UNAVAILABLE;
    }

    private static double boundary(float a, int cell, double delta) {
        return delta == 0 ? Double.POSITIVE_INFINITY : ((delta > 0 ? cell + 1.0 : cell) - a) / delta;
    }

    private static boolean valid(float x, float y, float z) {
        return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z)
                && Math.abs(x) < 1_000_000_000 && Math.abs(z) < 1_000_000_000 && Math.abs(y) < 10000;
    }
}
