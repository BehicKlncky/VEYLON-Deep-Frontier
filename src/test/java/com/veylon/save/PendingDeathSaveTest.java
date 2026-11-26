package com.veylon.save;

import com.veylon.Game;
import com.veylon.entity.Creature;
import com.veylon.entity.Creature.CreatureType;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 0.9.1 B17: a save written between a killing blow and the next fast tick, the tick that commits
 * a death, used to write the fallen with health below zero and no death. A projectile's blow lands
 * after the frame's ticks, and F5 at the start of the next frame or an autosave at the end of the
 * same one can come first. The person or animal came back from the save alive at negative health,
 * walking about, with nothing credited and no body; only the infected, managed creatures and urban
 * survivors were committed first. Every pending death is now committed before the save, as the
 * fast tick would commit it.
 */
class PendingDeathSaveTest {

    private static final long SEED = 424242L;

    @TempDir
    Path dir;

    @Test
    void aResidentKilledJustBeforeASaveStaysDeadWithEverythingTheirDeathBrings() throws Exception {
        Game g = new Game();
        g.newWorld(SEED, true, World.GEN_CAVE_IDENTITIES, GameMode.SURVIVAL);
        Settlement village = peacefulSettlement(g);
        assertNotNull(village);
        g.world.layoutFor(village);
        g.world.ensureChunks(village.center.x(), village.center.z(), 4, 10_000);
        g.player.pos.set(village.center.x() + 0.5f, village.center.y() + 1f, village.center.z() + 0.5f);
        g.settlementManager.slowTick(g, SimulationScheduler.SLOW_DT);
        Settlement.Resident record = village.residents.stream()
                .filter(r -> r.live != null && r.archetype != NpcArchetype.CAPTIVE && !r.live.isTrader)
                .findFirst().orElseThrow();
        int index = village.residents.indexOf(record);
        Npc victim = record.live;
        float reputation = village.localReputation;

        victim.killBy(true); // the blow, after the frame's ticks
        Path file = dir.resolve("pending-resident.sav");
        assertTrue(SaveSystem.save(g, file), "F5 before the next fast tick");
        assertFalse(g.entities.npcs.contains(victim), "the death was committed before the save was written");
        assertEquals(reputation - 35f, village.localReputation, 1e-3f, "and held against the player");
        assertEquals(1, g.ragdolls.liveCount() + g.entities.corpses.size(), "the body fell and the save laid it down");

        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, file));
        Settlement back = loaded.world.settlements.get(village.id);
        Settlement.Resident backRecord = back.residents.get(index);
        assertFalse(backRecord.alive, "the record says they died");
        assertNull(backRecord.live, "nobody stands in for them");
        assertTrue(loaded.entities.npcs.stream().noneMatch(n -> n.health <= 0f), "no one walks about with no health");
        assertEquals(reputation - 35f, back.localReputation, 1e-3f);
        assertEquals(1, loaded.entities.corpses.size(), "their body lies where it fell");
    }

    @Test
    void anAnimalKilledJustBeforeASaveComesBackAsItsCarcassNotAsAnAnimalWithNoHealth() throws Exception {
        Game g = new Game();
        g.newWorld(SEED, true, World.GEN_CAVE_IDENTITIES, GameMode.SURVIVAL);
        g.entities.creatures.clear();
        g.entities.carcasses.clear();
        Creature deer = g.entities.spawnCreature(g.world, CreatureType.DEER,
                g.player.pos.x + 3f, g.player.pos.y + 0.5f, g.player.pos.z);
        deer.killBy(true);
        Path file = dir.resolve("pending-deer.sav");
        assertTrue(SaveSystem.save(g, file));
        assertFalse(g.entities.creatures.contains(deer));

        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, file));
        assertTrue(loaded.entities.creatures.stream().noneMatch(c -> c.type == CreatureType.DEER),
                "the deer does not get up again");
        assertEquals(1, loaded.entities.carcasses.stream().filter(c -> c.type == CreatureType.DEER).count(),
                "its carcass is there to skin");
        for (int tick = 0; tick < 20; tick++) {
            loaded.fastTick(SimulationScheduler.FAST_DT);
        }
        assertEquals(1, loaded.entities.carcasses.stream().filter(c -> c.type == CreatureType.DEER).count(),
                "and nothing later makes a second body of it");
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
