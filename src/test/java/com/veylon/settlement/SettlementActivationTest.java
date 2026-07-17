package com.veylon.settlement;

import com.veylon.Game;
import com.veylon.ai.Pathfinder;
import com.veylon.ai.SettledNpcAI;
import com.veylon.entity.Npc;
import com.veylon.util.Vec3i;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end settlement runtime: residents materialize when the player is
 * near, run their daily-life AI for many ticks without leaking or crashing,
 * and write back to dormant records when the player leaves.
 */
class SettlementActivationTest {

    @Test
    void residentsActivateRunAiAndDeactivateCleanly() {
        Game g = new Game();
        g.newWorld(20260716L, true);

        // Find any settlement, generate its chunks, and stand the player there.
        Settlement s = null;
        outer:
        for (int rx = -4; rx <= 4; rx++) {
            for (int rz = -4; rz <= 4; rz++) {
                s = g.world.settlementForRegion(rx, rz);
                if (s != null) {
                    break outer;
                }
            }
        }
        assertNotNull(s, "expected a settlement within 9x9 regions");
        g.world.ensureChunks(s.center.x(), s.center.z(), 4, 10_000);
        g.player.pos.set(s.center.x() + 0.5f, s.center.y() + 1f, s.center.z() + 0.5f);

        int npcsBefore = g.entities.npcs.size();
        g.settlementManager.slowTick(g, 10f);
        int live = 0;
        for (Settlement.Resident r : s.residents) {
            if (r.live != null) {
                live++;
            }
        }
        assertTrue(live > 0, "residents must materialize near the player");
        assertTrue(g.entities.npcs.size() > npcsBefore);
        assertTrue(s.discovered, "standing in a settlement discovers it");

        // Run the full NPC AI + physics for ~15 simulated seconds.
        for (int tick = 0; tick < 300; tick++) {
            g.entities.fastTick(g, 0.05f);
            g.settlementManager.fastTick(g, 0.05f);
            g.projectiles.update(g, 0.05f);
            g.noise.update(0.05f);
        }
        // No NPC leaked, none fell through the world.
        for (Npc n : g.entities.npcs) {
            if (n.settled()) {
                assertTrue(n.pos.y > 0, n.name + " fell out of the world");
                assertTrue(n.path == null || n.path.size() <= Pathfinder.DEFAULT_BUDGET,
                        n.name + " retained an unbounded cached path");
            }
        }

        // Leave: residents deactivate and keep their identity/health.
        g.player.pos.set(s.center.x() + 500f, 45f, s.center.z() + 500f);
        g.settlementManager.slowTick(g, 10f);
        for (Settlement.Resident r : s.residents) {
            assertTrue(r.live == null, "live entities must be written back on deactivation");
        }
        for (Npc n : g.entities.npcs) {
            assertTrue(!n.settled() || n.settlementId != s.id,
                    "settlement NPCs must despawn when the player leaves");
        }
    }

    @Test
    void activeResidentsEatWorkTradeAndUseFiniteMedicineStocks() {
        Game g = new Game();
        g.newWorld(31337L, true);
        int x = 240, z = 240;
        g.world.ensureChunks(x, z, 2, 10_000);
        int y = g.world.surfaceHeight(x, z) + 1;
        Settlement s = new Settlement(Settlement.packId(20, 20), 20, 20,
                SettlementType.VILLAGE, new Vec3i(x, y, z), HumanFaction.FRONTIER,
                Settlement.Alignment.FRIENDLY);
        s.foodStock = 20;
        s.medStock = 3;
        s.dutyPoints.add(s.center);
        s.beds.add(s.center);
        s.residents.add(new Settlement.Resident("Farmer", NpcArchetype.FARMER));
        s.residents.add(new Settlement.Resident("Medic", NpcArchetype.MEDIC));
        s.residents.add(new Settlement.Resident("Patient", NpcArchetype.VILLAGER));
        s.residents.add(new Settlement.Resident("Trader", NpcArchetype.TRADER));
        for (int i = 0; i < s.residents.size(); i++) {
            s.residents.get(i).bedIndex = 0;
            s.residents.get(i).dutyIndex = 0;
        }
        g.world.settlements.put(s.id, s);
        g.player.pos.set(x + 0.5f, y, z + 0.5f);
        g.time.totalMinutes = 12 * 60;
        g.settlementManager.slowTick(g, 1f);

        Npc farmer = s.residents.get(0).live;
        Npc medic = s.residents.get(1).live;
        Npc patient = s.residents.get(2).live;
        Npc trader = s.residents.get(3).live;
        assertNotNull(farmer);
        for (Npc npc : new Npc[]{farmer, medic, patient, trader}) {
            npc.pos.set(x + 0.5f, y, z + 0.5f);
            npc.onGround = true;
        }

        int foodBeforeWork = s.foodStock;
        farmer.workTimer = 29.5f;
        SettledNpcAI.update(g, farmer, 1f);
        assertEquals(foodBeforeWork + 1, s.foodStock,
                "a farmer physically at duty produces bounded food");

        patient.health = patient.maxHealth - 12;
        int medicineBefore = s.medStock;
        medic.workTimer = 2.4f;
        SettledNpcAI.update(g, medic, 0.2f);
        assertTrue(patient.health > patient.maxHealth - 12);
        assertEquals(medicineBefore - 1, s.medStock,
                "treatment consumes real settlement medicine");

        patient.health = patient.maxHealth;
        patient.hunger = 100;
        int foodBeforeMeal = s.foodStock;
        patient.workTimer = 1.9f;
        SettledNpcAI.update(g, patient, 0.2f);
        assertEquals(foodBeforeMeal - 1, s.foodStock,
                "resident meals consume real settlement food");
        assertTrue(patient.hunger < 100);

        SettledNpcAI.update(g, trader, 0.1f);
        assertEquals(Npc.NpcState.TRADE, trader.state);

        long dormantStep = s.dormantStep;
        s.dormantAccumulator = 59f;
        g.settlementManager.slowTick(g, 2f);
        assertEquals(dormantStep, s.dormantStep,
                "active residents are never simulated a second time as dormant records");
    }

    @Test
    void dormantSicknessCanKillAndPopulationNeverExceedsCapacity() {
        Game g = new Game();
        g.newWorld(616161L, true);
        Settlement s = new Settlement(Settlement.packId(30, 30), 30, 30,
                SettlementType.CAMP, new Vec3i(900, 40, 900), HumanFaction.FRONTIER,
                Settlement.Alignment.FRIENDLY);
        Settlement.Resident sick = new Settlement.Resident("Sick", NpcArchetype.VILLAGER);
        sick.sick = true;
        sick.sicknessTimer = 601;
        sick.health = 2;
        Settlement.Resident guard = new Settlement.Resident("Guard", NpcArchetype.GUARD);
        s.residents.add(sick);
        s.residents.add(guard);
        s.foodStock = 0;
        s.medStock = 0;
        s.morale = 70;
        g.world.settlements.put(s.id, s);
        g.player.pos.set(0, 45, 0);

        g.settlementManager.slowTick(g, 60f);
        assertTrue(!sick.alive, "untreated off-screen illness has a real mortality outcome");

        s.foodStock = 999;
        s.medStock = 999;
        s.morale = 80;
        s.alertLevel = 0;
        for (int i = 0; i < 30; i++) {
            s.replenishTimer = 0;
            g.settlementManager.slowTick(g, 0f);
        }
        assertTrue(s.aliveResidents() <= s.type.maxPopulation,
                "dormant replenishment respects the settlement population cap");
    }

    @Test
    void hostileGarrisonEngagesTheNearbyPlayerWithoutCrashing() {
        Game g = new Game();
        g.newWorld(918273L, true);
        Settlement hostile = null;
        outer:
        for (int rx = -8; rx <= 8; rx++) {
            for (int rz = -8; rz <= 8; rz++) {
                Settlement s = g.world.settlementForRegion(rx, rz);
                if (s != null && s.hostile()) {
                    hostile = s;
                    break outer;
                }
            }
        }
        assertNotNull(hostile, "expected a hostile settlement");
        g.world.ensureChunks(hostile.center.x(), hostile.center.z(), 4, 10_000);
        g.player.pos.set(hostile.center.x() + 6.5f, hostile.center.y() + 1f,
                hostile.center.z() + 6.5f);
        g.settlementManager.slowTick(g, 10f);

        // Stand the player right beside a live melee defender so the test
        // exercises perception -> combat without depending on long pathing.
        Npc defender = null;
        for (Npc n : g.entities.npcs) {
            if (n.settled() && n.settlementId == hostile.id && !n.dead
                    && n.archetype.hostileArchetype() && !n.archetype.ranged()) {
                defender = n;
                break;
            }
        }
        assertNotNull(defender, "hostile settlement must field defenders");
        g.player.pos.set(defender.pos.x + 1.2f, defender.pos.y, defender.pos.z);

        // Fire a gunshot noise event in the middle of the garrison.
        g.noise.emit(g, g.player.pos.x, g.player.pos.y, g.player.pos.z, 90f, 1f,
                "gunshot", true, g.player);
        float hpBefore = g.player.health;
        for (int tick = 0; tick < 600 && !g.player.dead; tick++) {
            g.entities.fastTick(g, 0.05f);
            g.projectiles.update(g, 0.05f);
            g.noise.update(0.05f);
        }
        // The garrison should have hurt the player (melee or ranged), and the
        // settlement should be on alert.
        assertTrue(g.player.dead || g.player.health < hpBefore,
                "hostile garrison must respond to the intruder");
        assertTrue(hostile.alertLevel > 0, "the settlement goes on alert");
    }

    @Test
    void actualEntityDeathsAndSiegeObjectivesClearAFortInStages(@TempDir Path dir) {
        Game g = new Game();
        g.newWorld(515151L, true);
        Settlement fort = null;
        outer:
        for (int rx = -10; rx <= 10; rx++) {
            for (int rz = -10; rz <= 10; rz++) {
                Settlement candidate = g.world.settlementForRegion(rx, rz);
                if (candidate != null && candidate.hostile()
                        && (candidate.type == SettlementType.FORT
                        || candidate.type == SettlementType.CASTLE
                        || candidate.type == SettlementType.FORTRESS)) {
                    fort = candidate;
                    break outer;
                }
            }
        }
        assertNotNull(fort, "expected a staged hostile stronghold");
        g.world.ensureChunks(fort.center.x(), fort.center.z(), 5, 10_000);
        g.player.pos.set(fort.center.x() + 0.5f, fort.center.y() + 1f,
                fort.center.z() + 0.5f);
        g.settlementManager.slowTick(g, 1f);

        int defenders = 0;
        for (Settlement.Resident resident : fort.residents) {
            if (resident.live != null && fort.countsAsDefender(resident)) {
                defenders++;
                resident.live.hurt(resident.live.health + 10f, true);
            }
        }
        assertTrue(defenders > 0);
        g.entities.fastTick(g, 0.05f);
        assertTrue(fort.commandNeutralized);
        assertTrue(!fort.cleared,
                "dead defenders alone cannot bypass the alarm and central objectives");

        g.settlementManager.onAlarmSabotaged(g, fort);
        assertTrue(!fort.cleared, "alarm suppression alone still needs central control");
        assertTrue(g.settlementManager.controlCentralObjective(g, fort));
        assertTrue(fort.cleared, "all staged objectives permanently clear the stronghold");

        Path save = dir.resolve("cleared-fort.sav");
        assertTrue(com.veylon.save.SaveSystem.save(g, save));
        Game loaded = new Game();
        assertTrue(com.veylon.save.SaveSystem.load(loaded, save));
        Settlement restored = loaded.world.settlements.get(fort.id);
        assertNotNull(restored);
        assertTrue(restored.cleared && restored.commandNeutralized
                && restored.alarmNeutralized && restored.centralObjectiveControlled,
                "the actual staged clear remains permanent after reload");
        assertTrue(restored.aliveResidents() == 0,
                "defenders cannot repopulate a cleared stronghold on load");
    }
}
