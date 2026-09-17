package com.veylon.entity;

import com.veylon.Game;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-frame body solver runs beside rain and projectiles inside the
 * simulate gate, so it has to be allocation-free in the same way they are:
 * fixed arrays on the ragdoll, reused JOML scratch on the system, indexed loops
 * instead of iterators, and chunk references cached rather than looked up per
 * voxel probe.
 *
 * <p>Modelled on {@code RainPerformanceTest.stormUpdateStaysBoundedAndAllocationLight},
 * with the same 4 KB/frame allowance, but kept in the default suite because
 * what it pins is a property of the code rather than of one machine's clock.
 *
 * <p>What it measures is the hot path: a full field of bodies falling through
 * open air over loaded terrain. A body over an <em>unloaded</em> column is a
 * different matter — like {@code RainCollision}, the column cache deliberately
 * never caches an absent chunk, so those probes go through
 * {@code World.getChunk} and box a key. That case is rare, settles within a
 * fraction of a second because unloaded space reads as solid, and caching it
 * would make a body blind to terrain that has since streamed in.
 */
class RagdollAllocationTest {

    private static final int WARMUP_FRAMES = 3_000;
    private static final int MEASURED_FRAMES = 6_000;
    private static final long BYTES_PER_FRAME_ALLOWANCE = 4_096;

    /** Where the bodies are re-seated each frame, well inside the arena. */
    private static final float DROP_HEIGHT = RagdollTestArena.GROUND + 12f;

    @Test
    void theFullBodyBudgetUpdatesWithoutAllocatingPerFrame() {
        Game game = RagdollTestArena.create(606L);
        // Bodies drip blood while they move; emission must be inside the budget
        // too, so leave the density where gameplay leaves it.
        game.particles.density = 1f;
        fillWithFallingBodies(game);
        assertEquals(RagdollConstants.MAX_LIVE, game.ragdolls.liveCount(),
                "precondition: the cap is full before measuring");

        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();

        for (int i = 0; i < WARMUP_FRAMES; i++) {
            reseat(game);
            game.ragdolls.update(game, 1f / 60f);
        }
        long before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < MEASURED_FRAMES; i++) {
            reseat(game);
            game.ragdolls.update(game, 1f / 60f);
        }
        long bytes = (bean.getThreadAllocatedBytes(thread) - before) / MEASURED_FRAMES;

        assertEquals(RagdollConstants.MAX_LIVE, game.ragdolls.liveCount(),
                "every measured frame must have simulated a full field of bodies");
        assertTrue(bytes < BYTES_PER_FRAME_ALLOWANCE,
                "the per-frame body solver allocated " + bytes
                        + " bytes/frame, over the " + BYTES_PER_FRAME_ALLOWANCE
                        + " byte allowance");
    }

    private static void fillWithFallingBodies(Game game) {
        for (int i = 0; i < RagdollConstants.MAX_LIVE; i++) {
            Creature wolf = game.entities.spawnCreature(game.world, Creature.CreatureType.WOLF,
                    RagdollTestArena.CENTER_X + i * 2f, DROP_HEIGHT,
                    RagdollTestArena.CENTER_Z);
            RagdollTestArena.kill(wolf, 2f, 1f, 1f);
            game.entities.fastTick(game, 0.05f);
        }
    }

    /**
     * Re-drops every body from its launch state so the measurement keeps
     * running the full job — integrate, collide, relax, re-pose — over loaded
     * ground instead of drifting a body out of the arena or letting one settle.
     */
    private static void reseat(Game game) {
        for (int i = 0; i < game.ragdolls.live.size(); i++) {
            Ragdoll r = game.ragdolls.live.get(i);
            r.age = 0f;
            r.quietSteps = 0;
            if (r.py[Ragdoll.TORSO] > DROP_HEIGHT - 3f) {
                continue;
            }
            float x = RagdollTestArena.CENTER_X + i * 2f;
            for (int p = 0; p < r.pointCount; p++) {
                r.px[p] = x;
                r.py[p] = DROP_HEIGHT;
                r.pz[p] = RagdollTestArena.CENTER_Z;
                r.vx[p] = 2f;
                r.vy[p] = 1f;
                r.vz[p] = 1f;
            }
        }
    }
}
