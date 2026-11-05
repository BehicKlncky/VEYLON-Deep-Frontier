package com.veylon;

import com.veylon.combat.ProjectileSystem;
import com.veylon.entity.BodyFragment;
import com.veylon.entity.Carcass;
import com.veylon.entity.Creature;
import com.veylon.entity.HumanCorpse;
import com.veylon.entity.Npc;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.settlement.UrbanSiteDirector;
import com.veylon.simulation.LiquidFireSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.Chunk;
import com.veylon.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bounded CPU chunk retention for generated cities (urban milestone 24, decision D-241).
 *
 * <p>Streaming generates block data and, until now, kept all of it: GPU meshes were released beyond
 * the render radius, but a long journey held every chunk it passed, about 27 KB each, 810 more for
 * every kilometre walked at the default render distance. A generated city's chunk is pure in the
 * seed and its plans, and the world gives it back every recorded edit and saved door or window
 * state whenever it generates ({@link World#evictsChunks}), so once more than {@link #RETAIN_LIMIT}
 * chunks are held, the farthest are dropped ({@link World#unloadChunk}) and come back identical when
 * the player returns. Nothing is capped: exploration goes on, and only memory stops growing.
 *
 * <p>A chunk is never dropped while it lies inside the {@link #keepRadius} window around the player
 * (render radius, the generation ring and the mesh margin, and never less than the 170-block reach
 * beyond which bodies despawn and the 130-block dormancy of encounters and sites), or while it or a
 * chunk beside it holds anything that only runs, or is only read back, while its column is loaded:
 * a lit fire, a burning campfire, a lantern, a drying rack, a rain collector, a lit fuse, a gate or a
 * resident's door still swinging, water still flowing, a utility device, a container whose food is
 * still spoiling (its temperature depends on the shelter around it), a survivor site's stash and a
 * compound's running gate, and any body, piece of a body or projectile. Those are what a save keeps
 * in the world's maps or what a load regenerates the chunk for, so a dropped chunk behaves exactly as
 * one a load has not brought back yet. Legacy and unbuilt urban worlds keep every chunk.
 *
 * <p>The pass runs on the main thread after streaming, only once the count passes the limit, and
 * drops down to {@link #RETAIN_LIMIT} less {@link #EVICT_BATCH}, so it runs once in that many new
 * chunks.
 */
final class ChunkRetention {
    /** Chunks a generated city holds at most after a pass, beside the kept window and pinned chunks. */
    static final int RETAIN_LIMIT = 2_048;
    /** A pass drops this far below the limit, so passes stay rare while travelling. */
    static final int EVICT_BATCH = 256;
    /**
     * The smallest kept window, in chunks (Chebyshev): bodies despawn beyond 170 blocks and
     * encounters, sites and utilities sleep beyond 130, all well inside it.
     */
    static final int MIN_KEEP_RADIUS = 13;
    /** Chunks beyond the render radius kept besides: the generation ring and the mesh margin of 3. */
    static final int KEEP_BEYOND_RENDER = 4;

    private final Game game;
    /** Chunks dropped this session, passes run and chunks pinned by the last pass (QA reports read them). */
    long evicted;
    int passes;
    int pinnedLastPass;

    ChunkRetention(Game game) {
        this.game = game;
    }

    /** The kept window's radius for a render radius, in chunks. */
    static int keepRadius(int renderRadius) {
        return Math.max(renderRadius + KEEP_BEYOND_RENDER, MIN_KEEP_RADIUS);
    }

    /** After a frame's streaming: drops far chunks of a generated city once it holds too many. */
    void afterStreaming() {
        World w = game.world;
        if (w == null || !w.evictsChunks() || w.loadedCount() <= RETAIN_LIMIT) {
            return;
        }
        pass();
    }

    /** One eviction pass: farthest unpinned chunks outside the kept window first, ties by position. */
    void pass() {
        World w = game.world;
        int pcx = Math.floorDiv((int) Math.floor(game.player.pos.x), Chunk.SX);
        int pcz = Math.floorDiv((int) Math.floor(game.player.pos.z), Chunk.SZ);
        int keep = keepRadius(game.renderer.renderRadius());
        Set<Long> pinned = pinnedChunks();
        List<Chunk> candidates = new ArrayList<>();
        for (Chunk c : w.loadedChunks()) {
            if (Math.max(Math.abs(c.cx - pcx), Math.abs(c.cz - pcz)) > keep && !pinned.contains(World.key(c.cx, c.cz))) {
                candidates.add(c);
            }
        }
        candidates.sort(Comparator.comparingInt((Chunk c) -> -Math.max(Math.abs(c.cx - pcx), Math.abs(c.cz - pcz)))
                .thenComparingInt(c -> c.cx).thenComparingInt(c -> c.cz));
        int excess = w.loadedCount() - (RETAIN_LIMIT - EVICT_BATCH);
        for (int i = 0; i < Math.min(excess, candidates.size()); i++) {
            Chunk c = candidates.get(i);
            if (w.unloadChunk(c.cx, c.cz)) { evicted++; }
        }
        passes++;
        pinnedLastPass = pinned.size();
    }

    /** Every chunk that holds, or stands beside a chunk that holds, state only a loaded column runs. */
    Set<Long> pinnedChunks() {
        World w = game.world;
        Set<Long> pins = new HashSet<>();
        pinKeys(pins, w.campfireFuel);
        pinKeys(pins, w.rackBatches);
        pinKeys(pins, w.collectorWater);
        pinKeys(pins, w.lanterns);
        pinKeys(pins, w.kegFuses);
        pinKeys(pins, w.kegFusePlayerAttribution);
        pinKeys(pins, w.gateTimers);
        pinKeys(pins, w.fixtures.transitTimers());
        for (Map.Entry<Vec3i, Inventory> e : w.crateContents.entrySet()) {
            if (spoils(e.getValue())) { pin(pins, e.getKey().x(), e.getKey().z()); }
        }
        if (w.urban != null) {
            for (var device : w.urban.utilities.devices()) { pin(pins, device.pos.x(), device.pos.z()); }
        }
        for (Vec3i p : game.fire.burningCells()) { pin(pins, p.x(), p.z()); }
        for (LiquidFireSystem.Patch p : game.liquidFire.patches()) { pin(pins, p.x, p.z); }
        for (Vec3i p : game.water.pendingCells()) { pin(pins, p.x(), p.z()); }
        UrbanSiteDirector.heldCells(game, p -> pin(pins, p.x(), p.z()));
        for (Creature c : game.entities.creatures) { pin(pins, c.pos.x, c.pos.z); }
        for (Npc n : game.entities.npcs) { pin(pins, n.pos.x, n.pos.z); }
        for (Carcass c : game.entities.carcasses) { pin(pins, c.pos.x, c.pos.z); }
        for (HumanCorpse c : game.entities.corpses) { pin(pins, c.pos.x, c.pos.z); }
        for (BodyFragment f : game.fragments.live) { pin(pins, f.pos.x, f.pos.z); }
        for (BodyFragment f : game.fragments.settled) { pin(pins, f.pos.x, f.pos.z); }
        for (ProjectileSystem.Projectile p : game.projectiles.live) { pin(pins, p.x, p.z); }
        for (ProjectileSystem.Projectile p : game.projectiles.stuck) { pin(pins, p.x, p.z); }
        return pins;
    }

    private static boolean spoils(Inventory inventory) {
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack s = inventory.get(i);
            if (s != null && s.type.spoils()) { return true; }
        }
        return false;
    }

    private static void pinKeys(Set<Long> pins, Map<Vec3i, ?> state) {
        for (Vec3i p : state.keySet()) { pin(pins, p.x(), p.z()); }
    }

    private static void pin(Set<Long> pins, float x, float z) {
        pin(pins, (int) Math.floor(x), (int) Math.floor(z));
    }

    /** The cell's chunk and its eight neighbours: fire, water and blasts reach across a border. */
    private static void pin(Set<Long> pins, int x, int z) {
        int cx = Math.floorDiv(x, Chunk.SX), cz = Math.floorDiv(z, Chunk.SZ);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) { pins.add(World.key(cx + dx, cz + dz)); }
        }
    }
}
