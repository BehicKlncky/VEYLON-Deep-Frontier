package com.veylon.world;

import com.veylon.engine.Mesh;

import java.util.ArrayList;
import java.util.List;

public class Chunk {

    public static final int SX = 16;
    public static final int SY = 96;
    public static final int SZ = 16;

    public final int cx, cz;
    private final byte[] blocks = new byte[SX * SY * SZ];
    /** Highest opaque block per column, for cheap sky light. */
    public final int[] heightMap = new int[SX * SZ];
    /** Light-emitting blocks in this chunk: {worldX, worldY, worldZ, level}. */
    public final List<int[]> lights = new ArrayList<>();

    public boolean dirty = true;
    public boolean generated = false;
    /** Soil moisture 0..1 used by the plant simulation. */
    public float moisture = 0.5f;

    public Mesh meshOpaque;
    public Mesh meshWater;

    public Chunk(int cx, int cz) {
        this.cx = cx;
        this.cz = cz;
    }

    private static int idx(int x, int y, int z) {
        return (y * SZ + z) * SX + x;
    }

    public BlockType get(int x, int y, int z) {
        if (y < 0 || y >= SY) {
            return BlockType.AIR;
        }
        return BlockType.byId(blocks[idx(x, y, z)]);
    }

    public void set(int x, int y, int z, BlockType t) {
        if (y < 0 || y >= SY) {
            return;
        }
        blocks[idx(x, y, z)] = t.id();
    }

    public void recomputeHeight(int lx, int lz) {
        for (int y = SY - 1; y >= 0; y--) {
            if (BlockType.byId(blocks[idx(lx, y, lz)]).opaque) {
                heightMap[lz * SX + lx] = y;
                return;
            }
        }
        heightMap[lz * SX + lx] = 0;
    }

    public void recomputeAllHeights() {
        for (int lx = 0; lx < SX; lx++) {
            for (int lz = 0; lz < SZ; lz++) {
                recomputeHeight(lx, lz);
            }
        }
    }

    public int height(int lx, int lz) {
        return heightMap[lz * SX + lx];
    }

    /** Rescans the chunk for light-emitting blocks. */
    public void rebuildLights() {
        lights.clear();
        for (int y = 0; y < SY; y++) {
            for (int z = 0; z < SZ; z++) {
                for (int x = 0; x < SX; x++) {
                    BlockType t = BlockType.byId(blocks[idx(x, y, z)]);
                    if (t.light > 0) {
                        lights.add(new int[]{cx * SX + x, y, cz * SZ + z, t.light});
                    }
                }
            }
        }
    }

    public void deleteMeshes() {
        if (meshOpaque != null) {
            meshOpaque.delete();
            meshOpaque = null;
        }
        if (meshWater != null) {
            meshWater.delete();
            meshWater = null;
        }
        dirty = true;
    }
}
