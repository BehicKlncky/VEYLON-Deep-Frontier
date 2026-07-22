package com.veylon.world;

import com.veylon.item.Inventory;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementBuilder;
import com.veylon.settlement.SettlementPlanner;
import com.veylon.util.Vec3i;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class World {

    public interface BlockListener {
        void onBlockChanged(int x, int y, int z, BlockType oldType, BlockType newType);
    }

    public static final int SEA_LEVEL = 30;
    private static final int[][] CARDINAL_DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    /** Terrain algorithm used when this world's chunks generate. */
    public static final int GEN_LEGACY = 1;
    /** Pre-identity 0.3.0 development generator, retained for existing v3 saves. */
    public static final int GEN_DEEP = 2;
    /** Release 0.3.0 generator with distinct Root/Basalt cave identities. */
    public static final int GEN_CAVE_IDENTITIES = 3;
    public static final int CURRENT_GENERATOR = GEN_CAVE_IDENTITIES;

    public final long seed;
    /** Persisted in save v3; legacy (v2) saves always load as {@link #GEN_LEGACY}. */
    public final int generatorVersion;
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

    // ---- 0.3.0 world expansion state ----
    /** Registered settlements keyed by packed region id (deep-frontier worlds only). */
    public final Map<Long, Settlement> settlements = new LinkedHashMap<>();
    /** Cached deterministic settlement layouts (rebuilt on demand, never saved). */
    private final Map<Long, SettlementBuilder.Layout> settlementLayouts = new HashMap<>();
    /** Regions whose settlement plan has been resolved (null plans included). */
    private final Set<Long> plannedRegions = new HashSet<>();
    /** Player reputation per human faction id, -100..100 (persisted in v3). */
    public final Map<String, Float> factionReputation = new HashMap<>();
    /** Escalating bounty per hostile faction id, 0..100 (persisted in v3). */
    public final Map<String, Float> factionBounty = new HashMap<>();
    /** Lit powder-keg fuses: block position -> seconds remaining (persisted in v3). */
    public final Map<Vec3i, Float> kegFuses = new HashMap<>();
    /**
     * Source attribution for every armed keg. The historical v3 core stores
     * only {@link #kegFuses}; the optional extension persists this parallel,
     * bounded state so older v3 saves default safely to environmental rather
     * than charging an unknown blast to the player.
     */
    public final Map<Vec3i, Boolean> kegFusePlayerAttribution = new HashMap<>();
    /** Gates opened by NPCs/players that auto-close: position -> seconds left. */
    public final Map<Vec3i, Float> gateTimers = new HashMap<>();
    /** One charcoal charge keeps a lantern lit for fifteen real-time minutes. */
    public static final float LANTERN_FUEL_PER_CHARCOAL = 15 * 60f;
    /** Lantern reservoirs hold at most four charcoal charges. */
    public static final float LANTERN_MAX_FUEL = 4 * LANTERN_FUEL_PER_CHARCOAL;
    /** Finite lantern state keyed by the placed block position. */
    public final Map<Vec3i, LanternState> lanterns = new HashMap<>();

    public BlockListener listener;
    public Vec3i campPos;

    public World(long seed) {
        this(seed, CURRENT_GENERATOR);
    }

    public World(long seed, int generatorVersion) {
        this.seed = seed;
        this.generatorVersion = generatorVersion;
        this.generator = new WorldGenerator(this, seed);
    }

    /**
     * Resolves (lazily, deterministically) the settlement of a region.
     * Safe to call from any chunk in any order; registration is idempotent.
     */
    public Settlement settlementForRegion(int rx, int rz) {
        if (generatorVersion < GEN_DEEP) {
            return null;
        }
        long key = Settlement.packId(rx, rz);
        if (plannedRegions.contains(key)) {
            return settlements.get(key);
        }
        plannedRegions.add(key);
        Settlement s = SettlementPlanner.plan(seed, generator, rx, rz);
        if (s != null) {
            settlements.put(key, s);
        }
        return s;
    }

    /** The settlement whose reserved bounds contain a block position, or null. */
    public Settlement settlementAt(int x, int z) {
        Settlement s = settlementForRegion(SettlementPlanner.regionOfBlock(x),
                SettlementPlanner.regionOfBlock(z));
        return s != null && s.containsBlock(x, z) ? s : null;
    }

    /** Cached layout for a settlement (deterministic; built on first request). */
    public SettlementBuilder.Layout layoutFor(Settlement s) {
        return settlementLayouts.computeIfAbsent(s.id,
                k -> SettlementBuilder.layout(seed, s, generator));
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
            c.rebuildLights(this);
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

    // ---- Batched world edits (explosions): one heightmap/light/mesh pass ----
    private boolean batching;
    private final Set<Long> batchChunks = new HashSet<>();

    /** Starts coalescing block edits; call {@link #endBatch()} when done. */
    public void beginBatch() {
        batching = true;
    }

    /** Recomputes heights/lights once per touched chunk and marks meshes dirty. */
    public void endBatch() {
        batching = false;
        for (long key : batchChunks) {
            Chunk c = chunks.get(key);
            if (c == null) {
                continue;
            }
            c.recomputeAllHeights();
            c.rebuildLights(this);
            c.dirty = true;
            markDirty(c.cx - 1, c.cz);
            markDirty(c.cx + 1, c.cz);
            markDirty(c.cx, c.cz - 1);
            markDirty(c.cx, c.cz + 1);
        }
        batchChunks.clear();
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
            if (t == BlockType.LANTERN) {
                lanterns.putIfAbsent(new Vec3i(x, y, z), new LanternState(0, false));
            }
            return;
        }
        c.set(lx, y, lz, t);
        Vec3i pos = new Vec3i(x, y, z);
        if (old == BlockType.POWDER_KEG) {
            kegFuses.remove(pos);
            kegFusePlayerAttribution.remove(pos);
        }
        if (old == BlockType.LANTERN) {
            lanterns.remove(pos);
        }
        if (t == BlockType.LANTERN) {
            lanterns.put(pos, new LanternState(0, false));
        }
        if (batching) {
            batchChunks.add(key(cx, cz));
            if (record) {
                changedBlocks.put(pos, t.id());
            }
            if (listener != null) {
                listener.onBlockChanged(x, y, z, old, t);
            }
            return;
        }
        c.recomputeHeight(lx, lz);
        c.dirty = true;
        boolean lightChanged = old.light > 0 || t.light > 0;
        if (lightChanged) {
            c.updateLightAt(this, x, y, z);
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
            changedBlocks.put(pos, t.id());
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

    /** Mutable value object persisted in the versioned v3 extension. */
    public static final class LanternState {
        private float fuelSeconds;
        private boolean lit;

        public LanternState(float fuelSeconds, boolean lit) {
            this.fuelSeconds = clampLanternFuel(fuelSeconds);
            this.lit = lit && this.fuelSeconds > 0;
        }

        public float fuelSeconds() {
            return fuelSeconds;
        }

        public boolean lit() {
            return lit;
        }
    }

    /** Returns state for a real lantern, creating the safe empty default if needed. */
    public LanternState lanternState(Vec3i pos) {
        if (pos == null || getBlock(pos.x(), pos.y(), pos.z()) != BlockType.LANTERN) {
            if (pos != null) {
                lanterns.remove(pos);
            }
            return null;
        }
        return lanterns.computeIfAbsent(pos, ignored -> new LanternState(0, false));
    }

    public boolean isLanternLit(int x, int y, int z) {
        LanternState state = lanterns.get(new Vec3i(x, y, z));
        return state != null && state.lit && state.fuelSeconds > 0;
    }

    /** Adds fuel without implicitly igniting the lantern. Returns its new fuel level. */
    public float addLanternFuel(Vec3i pos, float seconds) {
        LanternState state = lanternState(pos);
        if (state == null || !Float.isFinite(seconds) || seconds <= 0) {
            return state == null ? 0 : state.fuelSeconds;
        }
        state.fuelSeconds = (float) Math.min(LANTERN_MAX_FUEL,
                (double) state.fuelSeconds + seconds);
        return state.fuelSeconds;
    }

    /** Changes the lit state and immediately refreshes lighting and the visible model. */
    public boolean setLanternLit(Vec3i pos, boolean lit) {
        LanternState state = lanternState(pos);
        if (state == null || (lit && state.fuelSeconds <= 0)) {
            return false;
        }
        boolean next = lit && state.fuelSeconds > 0;
        if (state.lit != next) {
            state.lit = next;
            refreshLightsAt(pos);
        }
        return state.lit == lit;
    }

    /** Save loader hook: validates the block and defers the one-time light rebuild. */
    public boolean restoreLanternState(Vec3i pos, float fuelSeconds, boolean lit) {
        if (pos == null || getBlock(pos.x(), pos.y(), pos.z()) != BlockType.LANTERN
                || !Float.isFinite(fuelSeconds) || fuelSeconds < 0
                || fuelSeconds > LANTERN_MAX_FUEL) {
            return false;
        }
        lanterns.put(pos, new LanternState(fuelSeconds, lit));
        return true;
    }

    /**
     * Burns all lit lanterns independently and removes stale position state.
     * The returned positions are lanterns that exhausted their fuel this tick.
     */
    public List<Vec3i> tickLanterns(float dt) {
        if (!Float.isFinite(dt) || dt <= 0 || lanterns.isEmpty()) {
            return List.of();
        }
        List<Vec3i> expired = new ArrayList<>();
        var it = lanterns.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Vec3i, LanternState> entry = it.next();
            Vec3i pos = entry.getKey();
            if (getBlock(pos.x(), pos.y(), pos.z()) != BlockType.LANTERN) {
                it.remove();
                continue;
            }
            LanternState state = entry.getValue();
            if (!state.lit) {
                continue;
            }
            state.fuelSeconds = Math.max(0, state.fuelSeconds - dt);
            if (state.fuelSeconds <= 0) {
                state.lit = false;
                expired.add(pos);
            }
        }
        for (Vec3i pos : expired) {
            refreshLightsAt(pos);
        }
        return expired;
    }

    /** Rebuilds the affected light list and every mesh that samples its spill. */
    public void refreshLightsAt(Vec3i pos) {
        int cx = Math.floorDiv(pos.x(), Chunk.SX);
        int cz = Math.floorDiv(pos.z(), Chunk.SZ);
        Chunk chunk = getChunk(cx, cz);
        if (chunk == null) {
            return;
        }
        chunk.updateLightAt(this, pos.x(), pos.y(), pos.z());
        chunk.dirty = true;
        markDirty(cx - 1, cz);
        markDirty(cx + 1, cz);
        markDirty(cx, cz - 1);
        markDirty(cx, cz + 1);
    }

    /** One bounded pass after loading all position state. */
    public void refreshLoadedLights() {
        for (Chunk chunk : chunks.values()) {
            chunk.rebuildLights(this);
            chunk.dirty = true;
        }
    }

    private static float clampLanternFuel(float fuelSeconds) {
        if (!Float.isFinite(fuelSeconds)) {
            return 0;
        }
        return Math.max(0, Math.min(LANTERN_MAX_FUEL, fuelSeconds));
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

    /** Number of unloaded chunks currently holding deterministic cross-border edits. */
    public int pendingGenerationChunkCount() {
        return pendingGen.size();
    }

    /** Total queued cross-border edits; exposed for release QA and diagnostics. */
    public int pendingGenerationEditCount() {
        int count = 0;
        for (List<int[]> edits : pendingGen.values()) {
            count += edits.size();
        }
        return count;
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

    /**
     * A generated Basalt-depth fumarole is represented without a new serialized
     * block id: exposed sulfur surrounded by the ash rim placed by the cave
     * identity pass. Ordinary sulfur veins therefore remain safe to mine.
     */
    public boolean isBasaltFumarole(int x, int y, int z) {
        if (generatorVersion < GEN_CAVE_IDENTITIES
                || getBlock(x, y, z) != BlockType.AIR
                || getBlock(x, y + 1, z) != BlockType.AIR
                || getBlock(x, y - 1, z) != BlockType.SULFUR_ORE
                || generator.caveZoneAt(x, y, z) != WorldGenerator.CaveZone.BASALT) {
            return false;
        }
        int ashRim = 0;
        for (int[] dir : CARDINAL_DIRECTIONS) {
            if (getBlock(x + dir[0], y - 1, z + dir[1]) == BlockType.ASH) {
                ashRim++;
            }
        }
        return ashRim >= 2;
    }

    /**
     * Finds the nearest already-loaded fumarole in a small bounded volume.
     * This never generates distant chunks and is suitable for medium-tick
     * environmental exposure checks.
     */
    public Vec3i nearestBasaltFumarole(float fx, float fy, float fz, int radius) {
        int cx = (int) Math.floor(fx);
        int cy = (int) Math.floor(fy);
        int cz = (int) Math.floor(fz);
        int r = Math.max(1, Math.min(8, radius));
        double bestDistance = (double) r * r;
        Vec3i best = null;
        for (int y = Math.max(3, cy - r);
             y <= Math.min(Chunk.SY - 3, cy + r); y++) {
            for (int x = cx - r; x <= cx + r; x++) {
                for (int z = cz - r; z <= cz + r; z++) {
                    double dx = x + 0.5 - fx;
                    double dy = y + 0.5 - fy;
                    double dz = z + 0.5 - fz;
                    double distance = dx * dx + dy * dy + dz * dz;
                    if (distance <= bestDistance && isBasaltFumarole(x, y, z)) {
                        bestDistance = distance;
                        best = new Vec3i(x, y, z);
                    }
                }
            }
        }
        return best;
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
