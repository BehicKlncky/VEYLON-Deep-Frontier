package com.veylon.entity;

import com.sun.management.ThreadMXBean;
import com.veylon.Game;
import com.veylon.settlement.SettlementManager;
import com.veylon.simulation.FireSystem;
import com.veylon.simulation.LiquidFireConstants;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.util.Vec3i;
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
 * must cost nothing at all, contact sampling included. Everybody burning is the
 * worst case of the fire itself: half the crowd in contact every tick (the
 * candidate, refresh and contact-damage path), half in afterburn (fuel, fade
 * and drain), some of them in shallow water under a roof, and a herd in open
 * rain that is put out and caught again. Every flame at its cap is the worst
 * case of contact sampling. Since {@code World.getChunk} stopped boxing its
 * key for recently used chunks, only a body standing in a campfire allocates
 * (the fuel map's key); the allowance is the ragdoll and fragment steps' 4 KB.
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

    /**
     * Every kind of flame at its cap around a crowd that keeps moving: the
     * liquid's 160 patches under one row of people, 220 burning bushes under
     * the herd, torches and fueled campfires in the other row's cells. Every
     * body sweeps a new box each tick and touches something, so this is the
     * contact sampling's worst case, held to the same allowance.
     */
    @Test
    void touchingEveryKindOfFlameAtItsCapStaysWithinTheHotPathAllowance() {
        Game g = BodyCombustionTest.arena();
        // Eight bottles in a row: the ninth, 176 patches, runs past the cap and
        // the first pool loses its middle, so nobody stands in that one.
        for (int bottle = 0; bottle < 8; bottle++) {
            g.liquidFire.spill(g, 292.5f + bottle * 8f, 40.5f, 300.5f, 0, 0, bottle % 2 == 0);
        }
        for (int i = 0; i < SettlementManager.MAX_ACTIVE_NPCS; i++) {
            if (i < 22) {
                g.entities.spawnNpc(g.world, "Bather", 300.5f + (i % 7) * 8f, 40f, 299.5f + i / 7);
                continue;
            }
            Vec3i cell = new Vec3i(288 + (i - 22) * 3, 40, 306);
            g.world.setBlock(cell.x(), cell.y(), cell.z(), i % 4 == 0 ? BlockType.CAMPFIRE : BlockType.TORCH, false);
            g.world.campfireFuel.put(cell, 1_000f);
            g.entities.spawnNpc(g.world, "Villager", cell.x() + 0.5f, 40f, cell.z() + 0.5f);
        }
        Creature.CreatureType[] species = Creature.CreatureType.values();
        for (int i = 0; i < CREATURES; i++) {
            g.entities.spawnCreature(g.world, species[i % species.length],
                    290.5f + (i % 18) * 3f, 40f, 330.5f + (i / 18) * 6f);
        }
        int[] rows = {330, 336, 333, 331};
        for (int z : rows) {
            for (int x = 288; x < 352 && g.fire.count() < FireSystem.MAX_ACTIVE_FIRES; x++) {
                g.world.setBlock(x, 40, z, BlockType.BUSH, false);
                g.fire.ignite(g, x, 40, z, z == 330, 3);
            }
        }
        assertEquals(LiquidFireConstants.MAX_PATCHES, g.liquidFire.count(), "precondition: every patch in use");
        assertEquals(FireSystem.MAX_ACTIVE_FIRES, g.fire.count(), "precondition: every fire slot in use");

        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        long before = 0;
        for (int t = 0; t < WARMUP_TICKS + MEASURED_TICKS; t++) {
            if (t == WARMUP_TICKS) {
                before = bean.getThreadAllocatedBytes(thread);
            }
            float step = t % 2 == 0 ? 0.4f : -0.4f;
            for (int i = 0; i < g.entities.npcs.size(); i++) {
                Npc n = g.entities.npcs.get(i);
                n.health = n.maxHealth;
                n.pos.x += step;
            }
            for (int i = 0; i < g.entities.creatures.size(); i++) {
                Creature c = g.entities.creatures.get(i);
                c.health = c.maxHealth;
                c.pos.x += step;
            }
            g.combustion.fastTick(g, DT);
        }
        long bytes = (bean.getThreadAllocatedBytes(thread) - before) / MEASURED_TICKS;
        System.out.println("combustion allocation, every flame at its cap: " + bytes + " bytes/fast tick");

        assertEquals(SettlementManager.MAX_ACTIVE_NPCS + CREATURES, g.combustion.burningBodies(g),
                "every body touched a flame and burns");
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
