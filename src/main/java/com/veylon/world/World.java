package com.veylon.world;

import com.veylon.item.Inventory;
import com.veylon.util.Vec3i;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class World {

    public interface BlockListener {
        void onBlockChanged(int x, int y, int z, BlockType oldType, BlockType newType);
    }

    public static final int SEA_LEVEL = 30;

    public final long seed;
    public final WorldGenerator generator;
    private final Map<Long, Chunk> chunks = new HashMap<>();
    /** Blocks set during gameplay, persisted in saves (value = BlockType ordinal). */
    public final Map<Vec3i, Byte> changedBlocks = new HashMap<>();
    /** Decoration blocks that target not-yet-generated chunks. */
    final Map<Long, List<int[]>> pendingGen = new HashMap<>();
    public final Map<Vec3i, Inventory> crateContents = new HashMap<>();
    /** Remaining campfire fuel in seconds, keyed by block position. */
    public final Map<Vec3i, Float> campfireFuel = new HashMap<>();
    /** Items currently drying, keyed by drying-rack block position. */
    public final Map<Vec3i, RackBatch> rackBatches = new HashMap<>();
    /** Collected rainwater in liters (0..3), keyed by rain-collector position. */
    public final Map<Vec3i, Float> collectorWater = new HashMap<>();
    /** All generated points of interest (filled as chunks generate). */
    public final List<Poi> pois = new ArrayList<>();
    /** Positions of POIs the player has discovered (persisted; survives regeneration). */
    public final java.util.Set<Vec3i> discoveredPois = new java.util.HashSet<>();

    /** Distress beacon endgame: placed position and repair stage 0..3 (-1 = not placed). */
    public Vec3i beaconPos;
    public int beaconStage = -1;

    public BlockListener listener;
    public Vec3i campPos;

    public World(long seed) {
        this.seed = seed;
        this.generator = new WorldGenerator(this, seed);
    }

    public static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    public Chunk getChunk(int cx, int cz) {
        return chunks.get(key(cx, cz));
    }

    public Chunk getOrCreateChunk(int cx, int cz) {
        long k = key(cx, cz);
        Chunk c = chunks.get(k);
        if (c == null) {
            c = new Chunk(cx, cz);
            chunks.put(k, c);
            generator.generate(c);
            List<int[]> pending = pendingGen.remove(k);
            if (pending != null) {
                for (int[] p : pending) {
                    BlockType t = BlockType.byId((byte) p[3]);
                    if (p[4] == 0 || !c.get(p[0], p[1], p[2]).opaque) {
                        c.set(p[0], p[1], p[2], t);
                    }
                }
            }
            c.recomputeAllHeights();
            c.rebuildLights();
            c.generated = true;
            c.dirty = true;
            markDirty(cx - 1, cz);
            markDirty(cx + 1, cz);
            markDirty(cx, cz - 1);
            markDirty(cx, cz + 1);
        }
        return c;
    }

    /** Used by the generator to place decoration blocks that may cross chunk borders. */
    void genSet(int x, int y, int z, BlockType t, boolean soft) {
        if (y < 0 || y >= Chunk.SY) {
            return;
        }
        int cx = Math.floorDiv(x, 16), cz = Math.floorDiv(z, 16);
        int lx = Math.floorMod(x, 16), lz = Math.floorMod(z, 16);
        Chunk c = getChunk(cx, cz);
        if (c != null) {
            if (soft && c.get(lx, y, lz).opaque) {
                return;
            }
            c.set(lx, y, lz, t);
            if (c.generated) {
                c.recomputeHeight(lx, lz);
                c.dirty = true;
            }
        } else {
            pendingGen.computeIfAbsent(key(cx, cz), k -> new ArrayList<>())
                    .add(new int[]{lx, y, lz, t.id(), soft ? 1 : 0});
        }
    }

    public BlockType getBlock(int x, int y, int z) {
        if (y < 0 || y >= Chunk.SY) {
            return BlockType.AIR;
        }
        Chunk c = getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        if (c == null) {
            return BlockType.AIR;
        }
        return c.get(Math.floorMod(x, 16), y, Math.floorMod(z, 16));
    }

    public boolean isSolid(int x, int y, int z) {
        return getBlock(x, y, z).solid;
    }

    public void setBlock(int x, int y, int z, BlockType t, boolean record) {
        if (y < 0 || y >= Chunk.SY) {
            return;
        }
        int cx = Math.floorDiv(x, 16), cz = Math.floorDiv(z, 16);
        Chunk c = getChunk(cx, cz);
        if (c == null) {
            return;
        }
        int lx = Math.floorMod(x, 16), lz = Math.floorMod(z, 16);
        BlockType old = c.get(lx, y, lz);
        if (old == t) {
            return;
        }
        c.set(lx, y, lz, t);
        c.recomputeHeight(lx, lz);
        c.dirty = true;
        boolean lightChanged = old.light > 0 || t.light > 0;
        if (lightChanged) {
            c.rebuildLights();
            // Light spills into neighbor meshes; rebuild them too.
            markDirty(cx - 1, cz);
            markDirty(cx + 1, cz);
            markDirty(cx, cz - 1);
            markDirty(cx, cz + 1);
        } else {
            if (lx == 0) markDirty(cx - 1, cz);
            if (lx == 15) markDirty(cx + 1, cz);
            if (lz == 0) markDirty(cx, cz - 1);
            if (lz == 15) markDirty(cx, cz + 1);
        }
        if (record) {
            changedBlocks.put(new Vec3i(x, y, z), t.id());
        }
        if (listener != null) {
            listener.onBlockChanged(x, y, z, old, t);
        }
    }

    private void markDirty(int cx, int cz) {
        Chunk c = getChunk(cx, cz);
        if (c != null) {
            c.dirty = true;
        }
    }

    /** Generates missing chunks around a center, budgeted per call. Returns chunks generated. */
    public int ensureChunks(int centerBX, int centerBZ, int radius, int budget) {
        int ccx = Math.floorDiv(centerBX, 16), ccz = Math.floorDiv(centerBZ, 16);
        int made = 0;
        for (int r = 0; r <= radius && made < budget; r++) {
            for (int dx = -r; dx <= r && made < budget; dx++) {
                for (int dz = -r; dz <= r && made < budget; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    if (getChunk(ccx + dx, ccz + dz) == null) {
                        getOrCreateChunk(ccx + dx, ccz + dz);
                        made++;
                    }
                }
            }
        }
        return made;
    }

    public Collection<Chunk> loadedChunks() {
        return chunks.values();
    }

    public int loadedCount() {
        return chunks.size();
    }

    /** World-surface height (top opaque block Y) at a column; generates the chunk if needed. */
    public int surfaceHeight(int x, int z) {
        Chunk c = getOrCreateChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        return c.height(Math.floorMod(x, 16), Math.floorMod(z, 16));
    }

    /** Sky light 0..1 at a cell using the column heightmap. */
    public float skyLight(int x, int y, int z) {
        Chunk c = getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        if (c == null) {
            return 1f;
        }
        int h = c.height(Math.floorMod(x, 16), Math.floorMod(z, 16));
        if (y >= h) {
            return 1f;
        }
        int depth = h - y;
        float l = (float) Math.pow(0.72, depth);
        return Math.max(0.04f, l);
    }

    /** Light contributed by torches/campfires near a cell, 0..1. */
    public float blockLight(int x, int y, int z) {
        int ccx = Math.floorDiv(x, 16), ccz = Math.floorDiv(z, 16);
        float best = 0f;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Chunk c = getChunk(ccx + dx, ccz + dz);
                if (c == null) {
                    continue;
                }
                for (int[] l : c.lights) {
                    float ddx = l[0] - x, ddy = l[1] - y, ddz = l[2] - z;
                    float dist = (float) Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
                    float range = l[3] * 0.75f;
                    if (dist < range) {
                        float v = (1f - dist / range) * (l[3] / 15f);
                        if (v > best) {
                            best = v;
                        }
                    }
                }
            }
        }
        return Math.min(1f, best);
    }

    public Biome biomeAt(int x, int z) {
        return generator.biomeAt(x, z);
    }

    /** Called by the generator when a POI structure is placed. */
    public void registerPoi(Poi poi) {
        for (Poi p : pois) {
            if (p.pos.equals(poi.pos)) {
                return;
            }
        }
        poi.discovered = discoveredPois.contains(poi.pos);
        pois.add(poi);
    }
}
