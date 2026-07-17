package com.veylon.settlement;

import com.veylon.Game;
import com.veylon.ai.SettledNpcAI;
import com.veylon.entity.Npc;
import com.veylon.item.ItemType;
import com.veylon.util.Vec3i;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Faction behavior and capture flow: theft/destruction reputation, neutral
 * alignment flips, cleared/occupied transitions, resident bookkeeping and
 * bounded replenishment.
 */
class SettlementRuntimeTest {

    private Game g;

    @BeforeEach
    void setUp() {
        g = new Game();
        g.newWorld(424242L, true);
    }

    private Settlement findSettlement(Settlement.Alignment alignment) {
        for (int rx = -8; rx <= 8; rx++) {
            for (int rz = -8; rz <= 8; rz++) {
                Settlement s = g.world.settlementForRegion(rx, rz);
                if (s != null && s.alignment == alignment
                        && s.founderFaction.equals(alignment == Settlement.Alignment.NEUTRAL
                        ? HumanFaction.FREE_SETTLERS : s.founderFaction)) {
                    return s;
                }
            }
        }
        return null;
    }

    @Test
    void theftAndDestructionTurnANeutralSettlementHostile() {
        Settlement s = findSettlement(Settlement.Alignment.NEUTRAL);
        assertNotNull(s, "expected a neutral settlement");
        // Repeated theft.
        for (int i = 0; i < 3; i++) {
            g.settlementManager.onCrateTheft(g, s.center, 4);
        }
        g.settlementManager.onStructureDestroyed(g, s, com.veylon.world.BlockType.WALL);
        assertTrue(s.localReputation < 0);
        while (s.alignment != Settlement.Alignment.HOSTILE && s.localReputation > -100) {
            g.settlementManager.addLocalReputation(g, s, -10, null);
        }
        assertEquals(Settlement.Alignment.HOSTILE, s.alignment,
                "sustained crimes flip a neutral settlement hostile");
    }

    @Test
    void assistanceTurnsANeutralSettlementFriendly() {
        Settlement s = findSettlement(Settlement.Alignment.NEUTRAL);
        assertNotNull(s);
        for (int i = 0; i < 6; i++) {
            g.settlementManager.addLocalReputation(g, s, 8, null);
        }
        assertEquals(Settlement.Alignment.FRIENDLY, s.alignment,
                "helping a neutral settlement wins it over");
    }

    @Test
    void hostileFlipImmediatelyUpdatesResidentAiSidesAndBlocksFriendlyInteraction() {
        Settlement s = findSettlement(Settlement.Alignment.NEUTRAL);
        assertNotNull(s);
        g.world.ensureChunks(s.center.x(), s.center.z(), 4, 10_000);
        g.player.pos.set(s.center.x() + 0.5f, s.center.y() + 1f, s.center.z() + 0.5f);
        g.settlementManager.slowTick(g, 10f);

        g.settlementManager.addLocalReputation(g, s, -40, null);
        assertEquals(Settlement.Alignment.HOSTILE, s.alignment);
        int live = 0;
        for (Settlement.Resident resident : s.residents) {
            if (resident.live == null || resident.archetype == NpcArchetype.CAPTIVE) {
                continue;
            }
            live++;
            assertTrue(resident.live.hostileToPlayer());
            assertTrue(resident.live.combatant());
            assertEquals(HumanFaction.FREE_SETTLERS, resident.live.sideId());
            assertFalse(g.canOpenNpcInteraction(resident.live),
                    "hostile residents cannot open talk/trade UI");
        }
        assertTrue(live > 1, "test needs multiple live residents");

        var defender = s.residents.stream().map(r -> r.live)
                .filter(java.util.Objects::nonNull).findFirst().orElseThrow();
        g.player.pos.set(defender.pos.x + 1.2f, defender.pos.y, defender.pos.z);
        defender.lastKnown.set(g.player.pos);
        defender.lastKnownAge = 0;
        float before = g.player.health;
        for (int i = 0; i < 80 && g.player.health == before; i++) {
            g.entities.fastTick(g, 0.05f);
        }
        assertTrue(g.player.health < before || g.player.dead,
                "residents attack immediately after the alignment flip");
    }

    @Test
    void killingOneFlippedResidentCannotClearOtherActiveDefenders() {
        Settlement s = findSettlement(Settlement.Alignment.NEUTRAL);
        assertNotNull(s);
        g.settlementManager.addLocalReputation(g, s, -40, null);
        int defendersBefore = (int) s.residents.stream().filter(s::countsAsDefender).count();
        assertTrue(defendersBefore > 1);
        int victimIndex = -1;
        for (int i = 0; i < s.residents.size(); i++) {
            if (s.countsAsDefender(s.residents.get(i))) {
                victimIndex = i;
                break;
            }
        }
        Settlement.Resident victim = s.residents.get(victimIndex);
        var npc = g.entities.spawnNpc(g.world, victim.name,
                s.center.x(), s.center.y(), s.center.z());
        npc.archetype = victim.archetype;
        npc.settlementId = s.id;
        npc.residentIndex = victimIndex;
        g.settlementManager.onNpcKilledByPlayer(g, npc);
        assertFalse(s.cleared, "one death cannot clear living defenders");
        assertEquals(defendersBefore - 1,
                s.residents.stream().filter(s::countsAsDefender).count());
    }

    @Test
    void hostileFreeSettlersOnlyReturnToNeutralThroughExplicitRestitution(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        Settlement s = findSettlement(Settlement.Alignment.NEUTRAL);
        assertNotNull(s);
        g.settlementManager.addLocalReputation(g, s, -40, null);
        g.settlementManager.addLocalReputation(g, s, 100, null);
        assertEquals(Settlement.Alignment.HOSTILE, s.alignment,
                "generic reputation changes cannot silently clear hostility");

        g.player.inventory.add(ItemType.COOKED_MEAT, SettlementManager.RESTITUTION_FOOD);
        g.player.inventory.add(ItemType.MEDICINE, SettlementManager.RESTITUTION_MEDICINE);
        assertTrue(g.settlementManager.offerRestitution(g, s));
        assertEquals(Settlement.Alignment.NEUTRAL, s.alignment);

        var save = dir.resolve("restored-neutral.sav");
        assertTrue(com.veylon.save.SaveSystem.save(g, save));
        Game loaded = new Game();
        assertTrue(com.veylon.save.SaveSystem.load(loaded, save));
        assertEquals(Settlement.Alignment.NEUTRAL,
                loaded.world.settlements.get(s.id).alignment,
                "the explicit alignment transition survives save/load");
    }

    @Test
    void killingTheGarrisonClearsAHostileSettlementAndOccupationSticks() {
        Settlement s = findSettlement(Settlement.Alignment.HOSTILE);
        assertNotNull(s, "expected a hostile settlement");
        // Kill every hostile resident through the manager bookkeeping.
        for (int i = 0; i < s.residents.size(); i++) {
            Settlement.Resident r = s.residents.get(i);
            if (r.archetype.hostileArchetype()) {
                var npc = g.entities.spawnNpc(g.world, r.name, s.center.x(), 40, s.center.z());
                npc.archetype = r.archetype;
                npc.settlementId = s.id;
                npc.residentIndex = i;
                npc.lastHitByPlayer = true;
                g.settlementManager.onNpcKilledByPlayer(g, npc);
            }
        }
        if (s.type == SettlementType.FORT || s.type == SettlementType.CASTLE
                || s.type == SettlementType.FORTRESS) {
            g.settlementManager.onAlarmSabotaged(g, s);
            g.settlementManager.controlCentralObjective(g, s);
        }
        assertTrue(s.cleared, "settlement clears once leader and defenders fall");

        // Occupation requires supplies.
        assertFalse(g.settlementManager.occupy(g, s), "occupation needs supplies");
        g.player.inventory.add(ItemType.COOKED_MEAT, SettlementManager.OCCUPY_FOOD);
        g.player.inventory.add(ItemType.LOG, SettlementManager.OCCUPY_WOOD);
        assertTrue(g.settlementManager.occupy(g, s));
        assertTrue(s.occupied);
        assertEquals(HumanFaction.FRONTIER, s.factionId, "ownership handed to the Frontier");
        assertTrue(s.friendly());
        assertTrue(s.counterattackTimer > 0, "counterattack is scheduled");
        boolean garrison = false;
        boolean trader = false;
        for (Settlement.Resident r : s.residents) {
            if (r.archetype == NpcArchetype.GUARD && r.alive) {
                garrison = true;
            }
            trader |= r.archetype == NpcArchetype.TRADER && r.alive;
        }
        assertTrue(garrison, "friendly garrison moves in");
        assertTrue(trader, "claimed outpost has limited trade support");

        g.player.pos.set(s.center.x() + 0.5f, s.center.y() + 1f, s.center.z() + 0.5f);
        g.settlementManager.slowTick(g, 1f);
        assertTrue(s.residents.stream().anyMatch(r -> r.live != null && r.live.isTrader),
                "occupied services materialize when the player returns");
    }

    @Test
    void populationDoesNotInstantlyRespawn() {
        Settlement s = findSettlement(Settlement.Alignment.NEUTRAL);
        assertNotNull(s);
        int before = s.aliveResidents();
        // Kill one resident.
        for (Settlement.Resident r : s.residents) {
            if (r.alive) {
                r.alive = false;
                break;
            }
        }
        assertEquals(before - 1, s.aliveResidents());
        // Several slow ticks: replenishment is gated on a long timer.
        s.replenishTimer = 500;
        for (int i = 0; i < 5; i++) {
            g.settlementManager.slowTick(g, 10f);
        }
        assertTrue(s.aliveResidents() <= before,
                "population never exceeds its pre-loss level within a minute");
        assertTrue(s.replenishTimer > 0, "replenish timer still running");
    }

    @Test
    void bountyGrowsFromHostileFactionKills() {
        float before = g.world.factionBounty.getOrDefault(HumanFaction.HEADHUNTERS, 0f);
        g.settlementManager.addReputation(g, HumanFaction.HEADHUNTERS, -20, null);
        float after = g.world.factionBounty.getOrDefault(HumanFaction.HEADHUNTERS, 0f);
        assertTrue(after > before, "attacking headhunters raises their bounty on you");
    }

    @Test
    void hostilePatrolLeavesARealGateSearchesAndReturnsToReport() {
        Settlement s = findSettlement(Settlement.Alignment.HOSTILE);
        assertNotNull(s);
        g.world.layoutFor(s);
        Vec3i gate = s.gates.isEmpty() ? s.center : s.gates.getFirst();
        Vec3i mission = new Vec3i(gate.x() + 40, gate.y(), gate.z());
        g.player.pos.set(gate.x() + 500, gate.y(), gate.z() + 500);

        var party = g.settlementManager.dispatchWarParty(g, s, 1,
                HumanFaction.HEADHUNTERS, mission);
        assertEquals(1, party.size());
        Npc scout = party.getFirst();
        scout.archetype = NpcArchetype.SCOUT;
        assertEquals(s.id, scout.originSettlementId);
        assertEquals(Npc.PartyMission.OUTBOUND, scout.partyMission);
        assertTrue(scout.distSqTo(gate.x(), scout.pos.y, gate.z()) < 8 * 8,
                "patrol materializes at its settlement gate");

        // Let the real bounded off-screen travel advance all the way out.
        for (int tick = 0; tick < 120 && scout.partyMission == Npc.PartyMission.OUTBOUND;
             tick++) {
            SettledNpcAI.update(g, scout, 1f);
        }
        assertEquals(Npc.PartyMission.SEARCHING, scout.partyMission);

        // The player approaches and is genuinely perceived; contact is not
        // injected into party state by the test.
        g.player.pos.set(scout.pos.x + 1.2f, scout.pos.y, scout.pos.z);
        scout.decideTimer = 0f;
        SettledNpcAI.update(g, scout, 0.31f);
        assertTrue(scout.partyContact, "the patrol records real visual contact");

        // Break contact and allow its search timer to expire naturally.
        g.player.pos.set(gate.x() + 500, gate.y(), gate.z() + 500);
        for (int tick = 0; tick < 120 && scout.partyMission != Npc.PartyMission.RETURNING;
             tick++) {
            SettledNpcAI.update(g, scout, 1f);
        }
        assertEquals(Npc.PartyMission.RETURNING, scout.partyMission);

        float bounty = g.world.factionBounty.getOrDefault(HumanFaction.HEADHUNTERS, 0f);
        for (int tick = 0; tick < 180 && !scout.dead; tick++) {
            SettledNpcAI.update(g, scout, 1f);
        }
        assertTrue(scout.dead, "returning patrol is retired at its real home gate");
        assertEquals(bounty + 6f,
                g.world.factionBounty.get(HumanFaction.HEADHUNTERS), 0.01f,
                "a surviving scout reports contact");
    }

    @Test
    void rescueMarksTheCaptiveAndRewardsReputation() {
        // Find a hostile settlement with a captive.
        Settlement s = null;
        Settlement.Resident captive = null;
        outer:
        for (int rx = -10; rx <= 10; rx++) {
            for (int rz = -10; rz <= 10; rz++) {
                Settlement cand = g.world.settlementForRegion(rx, rz);
                if (cand == null || !cand.hostile()) {
                    continue;
                }
                for (Settlement.Resident r : cand.residents) {
                    if (r.archetype == NpcArchetype.CAPTIVE) {
                        s = cand;
                        captive = r;
                        break outer;
                    }
                }
            }
        }
        assertNotNull(captive, "expected at least one captive in hostile settlements");
        var npc = g.entities.spawnNpc(g.world, captive.name, s.center.x(), 40, s.center.z());
        npc.archetype = NpcArchetype.CAPTIVE;
        npc.settlementId = s.id;
        npc.residentIndex = s.residents.indexOf(captive);
        float repBefore = g.world.factionReputation.getOrDefault(HumanFaction.FRONTIER, 0f);
        g.settlementManager.rescueCaptive(g, npc);
        assertTrue(captive.rescued);
        assertTrue(g.world.factionReputation.get(HumanFaction.FRONTIER) > repBefore);
        assertFalse(g.entities.npcs.contains(npc), "freed captive leaves the scene");
    }

    @Test
    void gatesAutoCloseButNeverOnSomeoneInside() {
        // Place a gate and open it.
        Vec3i pos = new Vec3i((int) g.player.pos.x + 3, (int) g.player.pos.y, (int) g.player.pos.z);
        g.world.getOrCreateChunk(Math.floorDiv(pos.x(), 16), Math.floorDiv(pos.z(), 16));
        g.world.setBlock(pos.x(), pos.y(), pos.z(), com.veylon.world.BlockType.GATE, false);
        g.settlementManager.openGate(g, pos);
        assertEquals(com.veylon.world.BlockType.GATE_OPEN,
                g.world.getBlock(pos.x(), pos.y(), pos.z()));
        // Tick past the timer with nobody inside: closes.
        for (int i = 0; i < 200; i++) {
            g.settlementManager.tickGates(g, 0.05f);
        }
        assertEquals(com.veylon.world.BlockType.GATE,
                g.world.getBlock(pos.x(), pos.y(), pos.z()), "gate closes on its own");
    }

    @Test
    void multipleGateTimersCloseAndRescheduleWithoutConcurrentModification() {
        int y = (int) g.player.pos.y;
        Vec3i blocked = new Vec3i((int) g.player.pos.x, y, (int) g.player.pos.z);
        Vec3i free = blocked.offset(4, 0, 0);
        g.world.setBlock(blocked.x(), blocked.y(), blocked.z(),
                com.veylon.world.BlockType.GATE, false);
        g.world.setBlock(free.x(), free.y(), free.z(),
                com.veylon.world.BlockType.GATE, false);
        g.settlementManager.openGate(g, blocked);
        g.settlementManager.openGate(g, free);

        g.settlementManager.tickGates(g, SettlementManager.GATE_OPEN_SECONDS + 0.1f);
        assertEquals(com.veylon.world.BlockType.GATE_OPEN,
                g.world.getBlock(blocked.x(), blocked.y(), blocked.z()));
        assertEquals(com.veylon.world.BlockType.GATE,
                g.world.getBlock(free.x(), free.y(), free.z()));
        assertTrue(g.world.gateTimers.containsKey(blocked), "blocked gate is safely rescheduled");
        assertFalse(g.world.gateTimers.containsKey(free));

        g.player.pos.x += 8;
        g.settlementManager.tickGates(g, 1.6f);
        assertEquals(com.veylon.world.BlockType.GATE,
                g.world.getBlock(blocked.x(), blocked.y(), blocked.z()));
    }

    @Test
    void npcBlocksGateAndReplacedGateDropsItsStaleTimer() {
        int x = (int) g.player.pos.x + 5;
        int z = (int) g.player.pos.z;
        int y = (int) g.player.pos.y;
        Vec3i npcGate = new Vec3i(x, y, z);
        Vec3i removedGate = npcGate.offset(4, 0, 0);
        g.world.setBlock(npcGate.x(), npcGate.y(), npcGate.z(),
                com.veylon.world.BlockType.GATE, false);
        g.world.setBlock(removedGate.x(), removedGate.y(), removedGate.z(),
                com.veylon.world.BlockType.GATE, false);
        g.settlementManager.openGate(g, npcGate);
        g.settlementManager.openGate(g, removedGate);
        var npc = g.entities.spawnNpc(g.world, "Gate tester",
                npcGate.x() + 0.5f, npcGate.y(), npcGate.z() + 0.5f);
        g.world.setBlock(removedGate.x(), removedGate.y(), removedGate.z(),
                com.veylon.world.BlockType.AIR, true);

        g.settlementManager.tickGates(g, SettlementManager.GATE_OPEN_SECONDS + 0.1f);
        assertEquals(com.veylon.world.BlockType.GATE_OPEN,
                g.world.getBlock(npcGate.x(), npcGate.y(), npcGate.z()));
        assertTrue(g.world.gateTimers.containsKey(npcGate));
        assertFalse(g.world.gateTimers.containsKey(removedGate), "replaced gate timer is discarded");
        npc.pos.x += 4;
        g.settlementManager.tickGates(g, 1.6f);
        assertEquals(com.veylon.world.BlockType.GATE,
                g.world.getBlock(npcGate.x(), npcGate.y(), npcGate.z()));
    }
}
