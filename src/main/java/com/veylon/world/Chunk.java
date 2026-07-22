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
    /** Deterministic diagnostics for full versus point light-list maintenance. */
    public long fullLightRebuilds;
    public long incrementalLightUpdates;

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
        rebuildLights(null);
    }

    /** Rescans lights, consulting runtime state for controllable emitters. */
    public void rebuildLights(World world) {
        fullLightRebuilds++;
        lights.clear();
        for (int y = 0; y < SY; y++) {
            for (int z = 0; z < SZ; z++) {
                for (int x = 0; x < SX; x++) {
                    BlockType t = BlockType.byId(blocks[idx(x, y, z)]);
                    boolean enabled = t != BlockType.LANTERN || world != null
                            && world.isLanternLit(cx * SX + x, y, cz * SZ + z);
                    if (t.light > 0 && enabled) {
                        lights.add(new int[]{cx * SX + x, y, cz * SZ + z, t.light});
                    }
                }
            }
        }
    }

    /**
     * Updates the one emitter at a world position without rescanning all 24,576
     * cells. Bulk generation/load still uses {@link #rebuildLights(World)}.
     */
    public void updateLightAt(World world, int worldX, int y, int worldZ) {
        incrementalLightUpdates++;
        int found = -1;
        for (int i = 0; i < lights.size(); i++) {
            int[] light = lights.get(i);
            if (light[0] == worldX && light[1] == y && light[2] == worldZ) {
                found = i;
                break;
            }
        }
        BlockType type = world.getBlock(worldX, y, worldZ);
        boolean enabled = type.light > 0 && (type != BlockType.LANTERN
                || world.isLanternLit(worldX, y, worldZ));
        if (!enabled) {
            if (found >= 0) {
                lights.remove(found);
            }
            return;
        }
        if (found >= 0) {
            lights.get(found)[3] = type.light;
        } else {
            lights.add(new int[]{worldX, y, worldZ, type.light});
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
