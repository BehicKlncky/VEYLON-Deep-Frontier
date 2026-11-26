package com.veylon.settlement;

import com.veylon.Game;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 0.9.1 B12: a death commits its bookkeeping before a settlement going dormant can write the
 * resident back. A projectile lands after the frame's ticks, so a person it kills waits for the
 * next fast tick; the scheduler's buckets keep separate time, so a slow tick can run in a frame
 * without a fast tick, and a settlement deactivating there wrote the fallen resident back as dead
 * and took them away with no death at all: no reputation, no log line, no body.
 */
class DeathBeforeDormancyTest {

    private static final long SEED = 424242L;

    @Test
    void aResidentKilledJustBeforeTheirSettlementGoesDormantDiesOnceWithEverythingADeathBrings() {
        Game g = new Game();
        g.newWorld(SEED, true, World.GEN_CAVE_IDENTITIES, GameMode.SURVIVAL);
        Settlement village = peacefulSettlement(g);
        assertNotNull(village, "a generated settlement that does not fight the player");
        g.world.layoutFor(village);
        g.world.ensureChunks(village.center.x(), village.center.z(), 4, 10_000);
        g.player.pos.set(village.center.x() + 0.5f, village.center.y() + 1f, village.center.z() + 0.5f);
        g.settlementManager.slowTick(g, SimulationScheduler.SLOW_DT);
        Settlement.Resident record = null;
        for (Settlement.Resident r : village.residents) {
            if (r.live != null && r.archetype != NpcArchetype.CAPTIVE && !r.live.isTrader) {
                record = r;
                break;
            }
        }
        assertNotNull(record, "the village's people are in the world");
        Npc victim = record.live;
        float reputation = village.localReputation;
        int ragdolls = g.ragdolls.liveCount();
        int logged = g.eventLog.all().size();

        // The blow: the player's arrow kills them through the damage path, after the frame's ticks.
        victim.killBy(true);
        // The same frame's slow tick, with no fast tick in it: the player has gone, the village sleeps.
        g.player.pos.set(village.center.x() + SettlementManager.DEACTIVATE_RADIUS + 40f,
                village.center.y() + 1f, village.center.z() + 0.5f);
        g.settlementManager.slowTick(g, SimulationScheduler.SLOW_DT);

        assertFalse(g.entities.npcs.contains(victim), "the village went dormant without them");
        assertNull(record.live);
        assertFalse(record.alive, "the record says they died");
        assertEquals(reputation - 35f, village.localReputation, 1e-3f, "the killing is held against the player");
        assertTrue(g.eventLog.all().stream().skip(logged).anyMatch(line -> line.contains(victim.name + " has died.")),
                "and reported: " + g.eventLog.all().stream().skip(logged).toList());
        assertEquals(ragdolls + 1, g.ragdolls.liveCount(), "they fall as a body");

        for (int tick = 0; tick < 3; tick++) {
            g.fastTick(SimulationScheduler.FAST_DT);
        }
        assertEquals(reputation - 35f, village.localReputation, 1e-3f, "the next fast tick does not book it again");
        assertEquals(ragdolls + 1, g.ragdolls.liveCount(), "one body, never two");
    }

    private static Settlement peacefulSettlement(Game g) {
        for (int rx = -6; rx <= 6; rx++) {
            for (int rz = -6; rz <= 6; rz++) {
                Settlement s = g.world.settlementForRegion(rx, rz);
                if (s != null && !s.hostile() && s.residents.size() >= 3) {
                    return s;
                }
            }
        }
        return null;
    }
}
