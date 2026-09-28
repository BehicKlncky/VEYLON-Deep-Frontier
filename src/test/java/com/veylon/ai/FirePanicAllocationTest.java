package com.veylon.ai;

import com.sun.management.ThreadMXBean;
import com.veylon.Game;
import com.veylon.entity.CombustionSource;
import com.veylon.entity.Creature;
import com.veylon.entity.Creature.CreatureType;
import com.veylon.entity.Npc;
import com.veylon.settlement.SettlementManager;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.world.BlockType;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;

import static com.veylon.ai.PanicConstants.REPLAN_COOLDOWN;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The panic runs in every burning body's AI twenty times a second, so it is
 * held to the hot-path rule of the combustion tick: measured with the
 * thread's allocation counter, in the default suite.
 *
 * <p>A crowd at the NPC cap plus a herd with birds, all alight for the whole
 * run (the combustion tick is not run, so nobody burns out), inside a walled
 * yard strewn with pillars so goals are blocked, replanned and trapped all the
 * time. Only the AI and each body's one physics step run: the entity tick's
 * footprints allocate by design and are not the panic's. The yard keeps every
 * body over loaded ground, because looking up a column that is not loaded
 * boxes its key ({@code World.getChunk}), which the panic never does inside
 * the loaded world.
 */
class FirePanicAllocationTest {

    private static final float DT = SimulationScheduler.FAST_DT;
    private static final int WARMUP_TICKS = 1_000;
    private static final int MEASURED_TICKS = 4_000;
    private static final long BYTES_PER_TICK_ALLOWANCE = 64;
    private static final int CREATURES = 35;

    @Test
    void aPanickingCrowdDecidesWithoutAllocating() {
        Game g = FirePanicTest.arena();
        yard(g);
        for (int i = 0; i < SettlementManager.MAX_ACTIVE_NPCS; i++) {
            Npc n = g.entities.spawnNpc(g.world, "Villager " + i, 301.5f + (i % 8) * 7, 40f, 301.5f + (i / 8) * 7);
            n.yaw = i * 37f;
        }
        CreatureType[] types = CreatureType.values();
        for (int i = 0; i < CREATURES; i++) {
            CreatureType type = types[i % types.length];
            Creature c = g.entities.spawnCreature(g.world, type, 303.5f + (i % 7) * 8, type.flying ? 45f : 40f,
                    345.5f + (i / 7) * 3);
            c.yaw = i * 53f;
        }
        g.entities.npcs.forEach(n -> ignite(g, n));
        g.entities.creatures.forEach(c -> ignite(g, c));

        run(g, WARMUP_TICKS);
        ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        long before = threads.getThreadAllocatedBytes(thread);
        run(g, MEASURED_TICKS);
        long bytes = (threads.getThreadAllocatedBytes(thread) - before) / MEASURED_TICKS;
        System.out.println("fire panic allocation, a crowd of " + (g.entities.npcs.size() + CREATURES)
                + " panicking: " + bytes + " bytes/fast tick");

        int goals = 0;
        int replans = 0;
        float bound = 1 + (WARMUP_TICKS + MEASURED_TICKS) * DT / REPLAN_COOLDOWN + 1e-3f;
        for (Npc n : g.entities.npcs) {
            assertTrue(n.panic.active(), "everyone panicked throughout");
            assertTrue(n.panic.goals() <= bound, "goals within the replan bound");
            goals += n.panic.goals();
            replans += n.panic.replans();
        }
        for (Creature c : g.entities.creatures) {
            assertTrue(c.panic.active());
            assertTrue(c.panic.goals() <= bound);
            goals += c.panic.goals();
            replans += c.panic.replans();
        }
        assertTrue(replans > 100, "fixture: the yard blocked the crowd often (" + replans + " of " + goals + ")");
        assertTrue(bytes < BYTES_PER_TICK_ALLOWANCE, "the panic allocated " + bytes
                + " bytes/tick, over the " + BYTES_PER_TICK_ALLOWANCE + " byte allowance");
    }

    private static void ignite(Game g, com.veylon.entity.Entity e) {
        e.maxHealth = e.health = 1000f;
        g.combustion.ignite(g, e, CombustionSource.DIRECT_HIT, 1f, false, 1, e.pos.x, e.pos.y + 0.5f, e.pos.z);
    }

    /** The AI and one physics step of every body, as the entity tick runs them, without footprints. */
    private static void run(Game g, int ticks) {
        for (int t = 0; t < ticks; t++) {
            for (int i = 0; i < g.entities.npcs.size(); i++) {
                Npc n = g.entities.npcs.get(i);
                NpcAI.update(g, n, DT);
                n.applyPhysics(DT, true);
            }
            for (int i = 0; i < g.entities.creatures.size(); i++) {
                Creature c = g.entities.creatures.get(i);
                CreatureAI.update(g, c, DT);
                c.applyPhysics(DT, !c.type.flying);
            }
        }
    }

    /** Walls too tall to climb or fly over round the arena's loaded ground, and pillars in it. */
    private static void yard(Game g) {
        for (int y = 40; y <= 62; y++) {
            for (int i = 290; i <= 365; i++) {
                g.world.setBlock(i, y, 290, BlockType.STONE, false);
                g.world.setBlock(i, y, 365, BlockType.STONE, false);
                g.world.setBlock(290, y, i, BlockType.STONE, false);
                g.world.setBlock(365, y, i, BlockType.STONE, false);
            }
        }
        for (int x = 296; x < 362; x += 9) {
            for (int z = 296; z < 362; z += 9) {
                for (int y = 40; y <= 62; y++) {
                    g.world.setBlock(x, y, z, BlockType.STONE, false);
                    g.world.setBlock(x + 1, y, z, BlockType.STONE, false);
                }
            }
        }
    }
}
