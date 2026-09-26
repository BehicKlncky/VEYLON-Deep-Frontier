package com.veylon.entity;

import com.sun.management.ThreadMXBean;
import com.veylon.Game;
import com.veylon.settlement.SettlementManager;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.world.BlockType;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The combustion tick runs twenty times a second over every living body, so
 * it is held to the hot-path rule the ragdoll and fragment steps follow:
 * measured with the thread's allocation counter, in the default suite.
 *
 * <p>A crowd at the NPC cap plus a herd. Nobody burning is the usual case and
 * must cost nothing at all. Everybody burning is the worst case: half the crowd
 * in contact every tick (the candidate, refresh and contact-damage path), half
 * in afterburn (fuel, fade and drain), some of them in shallow water under a
 * roof, and a herd in open rain that is put out and caught again. Its only
 * allocation is {@code World.getChunk} boxing its map key when consecutive
 * bodies stand in different chunks (about 80 bytes each), which every
 * entity's physics step already pays; the allowance is the ragdoll and
 * fragment steps' 4 KB.
 */
class CombustionAllocationTest {

    private static final float DT = SimulationScheduler.FAST_DT;
    private static final int WARMUP_TICKS = 2_000;
    private static final int MEASURED_TICKS = 6_000;
    private static final long IDLE_BYTES_PER_TICK_ALLOWANCE = 64;
    private static final long BYTES_PER_TICK_ALLOWANCE = 4_096;
    private static final int CREATURES = 35;

    @Test
    void aCrowdThatIsNotBurningCostsNothing() {
        Game g = crowd();
        long bytes = measure(g, false);
        System.out.println("combustion allocation, nobody burning: " + bytes + " bytes/fast tick");
        assertEquals(0, g.combustion.burningBodies(g));
        assertTrue(bytes < IDLE_BYTES_PER_TICK_ALLOWANCE, "an idle combustion tick allocated " + bytes
                + " bytes/tick, over the " + IDLE_BYTES_PER_TICK_ALLOWANCE + " byte allowance");
    }

    @Test
    void aCrowdOnFireBurnsWithinTheHotPathAllowance() {
        Game g = crowd();
        for (Entity e : g.entities.npcs) {
            g.combustion.ignite(g, e, CombustionSource.DIRECT_HIT, 1f, false, 1, e.pos.x, e.pos.y, e.pos.z);
        }
        for (Entity e : g.entities.creatures) {
            g.combustion.ignite(g, e, CombustionSource.DIRECT_HIT, 1f, true, 2, e.pos.x, e.pos.y, e.pos.z);
        }
        long bytes = measure(g, true);
        System.out.println("combustion allocation, everybody burning: " + bytes + " bytes/fast tick");

        assertTrue(g.entities.npcs.stream().allMatch(g.combustion::isBurning),
                "the crowd under the roof burned through every measured tick");
        assertTrue(g.combustion.totalRainedOut >= CREATURES, "the herd was put out by the rain");
        assertTrue(g.combustion.totalIgnitions > SettlementManager.MAX_ACTIVE_NPCS + CREATURES,
                "and caught again");
        assertTrue(bytes < BYTES_PER_TICK_ALLOWANCE, "the combustion tick allocated " + bytes
                + " bytes/tick, over the " + BYTES_PER_TICK_ALLOWANCE + " byte allowance");
    }

    /** Forty people under a roof, some in shallow water, and a herd of every species out in the rain. */
    private static Game crowd() {
        Game g = BodyCombustionTest.arena();
        g.weather.current = Weather.RAIN;
        g.weather.next = Weather.RAIN;
        g.weather.blend = 1f;
        for (int x = 288; x <= 351; x++) {
            for (int z = 288; z <= 319; z++) {
                g.world.setBlock(x, 43, z, BlockType.STONE, false);
            }
        }
        for (int x = 288; x <= 351; x += 3) {
            g.world.setBlock(x, 40, 300, BlockType.WATER, false);
        }
        for (int i = 0; i < SettlementManager.MAX_ACTIVE_NPCS; i++) {
            g.entities.spawnNpc(g.world, "Villager", 288.5f + (i % 22) * 3f, 40f, 300.5f + (i / 22) * 6f);
        }
        Creature.CreatureType[] species = Creature.CreatureType.values();
        for (int i = 0; i < CREATURES; i++) {
            g.entities.spawnCreature(g.world, species[i % species.length],
                    290.5f + (i % 18) * 3f, 40f, 330.5f + (i / 18) * 6f);
        }
        return g;
    }

    private static long measure(Game g, boolean feed) {
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        for (int t = 0; t < WARMUP_TICKS; t++) {
            tick(g, t, feed);
        }
        long before = bean.getThreadAllocatedBytes(thread);
        for (int t = 0; t < MEASURED_TICKS; t++) {
            tick(g, t, feed);
        }
        return (bean.getThreadAllocatedBytes(thread) - before) / MEASURED_TICKS;
    }

    /** Every animal and every other person touch a flame each tick; the other people are refed every second. */
    private static void tick(Game g, int t, boolean feed) {
        for (int i = 0; i < g.entities.npcs.size(); i++) {
            Npc n = g.entities.npcs.get(i);
            n.health = n.maxHealth;
            if (feed && (i % 2 == 0 || t % 20 == 0)) {
                g.combustion.expose(n, CombustionSource.LIQUID, 0.5f + (i % 5) * 0.1f, i % 3 == 0, i,
                        n.pos.x, n.pos.y, n.pos.z);
            }
        }
        for (int i = 0; i < g.entities.creatures.size(); i++) {
            Creature c = g.entities.creatures.get(i);
            c.health = c.maxHealth;
            if (feed) {
                g.combustion.expose(c, CombustionSource.LIQUID, 1f, true, i, c.pos.x, c.pos.y, c.pos.z);
            }
        }
        g.combustion.fastTick(g, DT);
    }
}
