package com.veylon.entity;

import com.sun.management.ThreadMXBean;
import com.veylon.Game;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Severed pieces are stepped per frame beside the ragdolls, so they are held
 * to the same rule and measured the same way as {@link RagdollAllocationTest}:
 * a full field at the live cap, a 4 KB/frame allowance, the default suite.
 *
 * <p>Two paths are measured: pieces tumbling through open air over loaded
 * terrain, and pieces lying on the ground, where the contact, topple and
 * refit-on-turn code runs every step. Only {@code spawnFromNpc} and settling
 * allocate, and neither happens inside the measurement.
 */
class BodyFragmentAllocationTest {

    private static final int WARMUP_FRAMES = 3_000;
    private static final int MEASURED_FRAMES = 6_000;
    private static final long BYTES_PER_FRAME_ALLOWANCE = 4_096;

    private static final float DROP_HEIGHT = RagdollTestArena.GROUND + 12f;

    @Test
    void aFullFieldOfFlyingPiecesUpdatesWithoutAllocatingPerFrame() {
        Game game = filledArena();
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        for (int i = 0; i < WARMUP_FRAMES; i++) {
            reseat(game);
            game.fragments.update(game, 1f / 60f);
        }
        long before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < MEASURED_FRAMES; i++) {
            reseat(game);
            game.fragments.update(game, 1f / 60f);
        }
        long bytes = (bean.getThreadAllocatedBytes(thread) - before) / MEASURED_FRAMES;
        System.out.println("fragment airborne allocation: " + bytes + " bytes/fixed tick");

        assertEquals(BodyFragmentConstants.MAX_LIVE_FRAGMENTS, game.fragments.liveCount(),
                "every measured frame must have simulated a full field of pieces");
        assertTrue(bytes < BYTES_PER_FRAME_ALLOWANCE, "the per-frame fragment step allocated "
                + bytes + " bytes/frame, over the " + BYTES_PER_FRAME_ALLOWANCE + " byte allowance");
    }

    @Test
    void aFullFieldOfPiecesOnTheGroundDoesNotAllocatePerStep() {
        Game game = filledArena();
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        for (int i = 0; i < WARMUP_FRAMES; i++) {
            keepAwake(game);
            game.fragments.update(game, RagdollConstants.FIXED_STEP);
        }
        int grounded = 0;
        for (BodyFragment f : game.fragments.live) {
            grounded += f.grounded ? 1 : 0;
        }
        assertEquals(BodyFragmentConstants.MAX_LIVE_FRAGMENTS, grounded,
                "precondition: every piece is lying on the ground");
        long before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < MEASURED_FRAMES; i++) {
            keepAwake(game);
            game.fragments.update(game, RagdollConstants.FIXED_STEP);
        }
        long bytes = (bean.getThreadAllocatedBytes(thread) - before) / MEASURED_FRAMES;
        System.out.println("fragment contact allocation: " + bytes + " bytes/fixed tick");
        assertEquals(BodyFragmentConstants.MAX_LIVE_FRAGMENTS, game.fragments.liveCount());
        assertTrue(bytes < BYTES_PER_FRAME_ALLOWANCE, "ground contact allocated " + bytes + " bytes/tick");
    }

    /** Twelve people blown apart in mid-air: exactly the live cap. */
    private static Game filledArena() {
        Game game = RagdollTestArena.create(606L);
        // Pieces drip while they move; emission must be inside the budget too.
        game.particles.density = 1f;
        for (int body = 0; body < BodyFragmentConstants.MAX_LIVE_FRAGMENTS / 10; body++) {
            Npc n = game.entities.spawnNpc(game.world, "Villager",
                    RagdollTestArena.CENTER_X - 11f + body * 2f, DROP_HEIGHT, RagdollTestArena.CENTER_Z);
            n.yaw = body * 29f;
            n.vel.zero();
            game.entities.npcs.remove(n);
            game.fragments.spawnFromNpc(game, n, n.pos.x, n.pos.y - 1f, n.pos.z, 1.5f);
        }
        assertEquals(BodyFragmentConstants.MAX_LIVE_FRAGMENTS, game.fragments.liveCount(),
                "precondition: the cap is full before measuring");
        return game;
    }

    /**
     * Re-drops every piece that has fallen three metres, so the measurement
     * keeps running the whole airborne job over loaded ground instead of
     * letting pieces land, settle or drift out of the arena.
     */
    private static void reseat(Game game) {
        for (int i = 0; i < game.fragments.live.size(); i++) {
            BodyFragment f = game.fragments.live.get(i);
            f.age = 0f;
            f.quietSteps = 0;
            if (f.pos.y > DROP_HEIGHT - 3f) {
                continue;
            }
            f.pos.set(RagdollTestArena.CENTER_X - 11f + (i / 10) * 2f, DROP_HEIGHT,
                    RagdollTestArena.CENTER_Z - 2.5f + (i % 10) * 0.5f);
            f.vel.set(2f, 1f, 1f);
            f.angularVelocity.set(3f, 2f, 1f);
        }
    }

    private static void keepAwake(Game game) {
        for (int i = 0; i < game.fragments.live.size(); i++) {
            BodyFragment f = game.fragments.live.get(i);
            f.age = 0f;
            f.quietSteps = 0;
        }
    }
}
