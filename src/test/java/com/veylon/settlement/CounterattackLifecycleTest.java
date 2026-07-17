package com.veylon.settlement;

import com.veylon.Game;
import com.veylon.ai.Quest;
import com.veylon.entity.Npc;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.save.SaveSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Production lifecycle coverage for active and dormant outpost counterattacks. */
class CounterattackLifecycleTest {

    @Test
    void activePartyLeavesARealHostileOriginAndContactLossDoesNotDeleteMission() {
        Game game = new Game();
        game.newWorld(20260716L, true);
        Pair pair = hostilePair(game);
        configureOutpost(game, pair.target, 3);
        game.world.layoutFor(pair.origin);

        CounterattackMission mission = game.settlementManager.dispatchCounterattack(
                game, pair.target, 4);
        assertNotNull(mission);
        assertEquals(pair.origin.id, mission.originSettlementId,
                "a matching live hostile settlement is the persisted origin");
        assertEquals(pair.target.id, mission.targetSettlementId);
        assertFalse(mission.origin.equals(mission.target));

        game.world.ensureChunks(mission.origin.x(), mission.origin.z(), 2, 10_000);
        game.player.pos.set(mission.origin.x() + 25f, mission.origin.y() + 1f,
                mission.origin.z());
        game.settlementManager.fastTick(game, 1f);
        game.settlementManager.fastTick(game, 0.1f);
        var members = game.settlementManager.counterattacks.members(game, mission.id);
        assertFalse(members.isEmpty(), "nearby outbound missions materialize as real NPCs");
        for (Npc member : members) {
            assertEquals(Npc.PartyKind.COUNTERATTACK, member.partyKind);
            assertEquals(mission.id, member.partyMissionId);
            assertEquals(pair.target.id, member.partyTargetSettlementId);
            assertTrue(member.distSqTo(game.player) >= 16 * 16,
                    "mission members never spawn on the player");
        }

        double before = distanceToTarget(mission);
        for (int i = 0; i < 120; i++) {
            game.player.pos.set(mission.x + 25f, mission.y + 1f, mission.z);
            game.world.ensureChunks((int) mission.x, (int) mission.z, 1, 10_000);
            game.entities.fastTick(game, 0.05f);
            game.settlementManager.fastTick(game, 0.05f);
        }
        assertTrue(distanceToTarget(mission) < before,
                "the active party advances toward the occupied outpost");
        assertEquals(CounterattackMission.Outcome.UNRESOLVED, mission.outcome);

        // Losing sight/range de-materializes only the entity view. The shared
        // mission, target and survivor count remain and coarse travel resumes.
        int survivors = mission.survivors;
        game.player.pos.set(50_000, 50, 50_000);
        game.settlementManager.fastTick(game, 1f);
        assertSame(mission, game.settlementManager.counterattacks.get(mission.id));
        assertEquals(survivors, mission.survivors);
        assertTrue(game.settlementManager.counterattacks.members(game, mission.id).isEmpty());
        assertTrue(mission.phase == CounterattackMission.Phase.OUTBOUND
                || mission.phase == CounterattackMission.Phase.APPROACH);
    }

    @Test
    void realCombatDeathsDefendTheTargetAndCleanupReturnsCapacity() {
        Game game = new Game();
        game.newWorld(42L, true);
        Pair pair = hostilePair(game);
        configureOutpost(game, pair.target, 4);
        Npc provider = outpostProvider(game, pair.target, 4);
        game.faction.makeQuestOfferAvailable();
        Quest defense = game.faction.offerSettlementQuestOfType(game, provider, pair.target,
                Quest.Type.DEFEND_OUTPOST);
        assertNotNull(defense, "the occupied outpost provider offers a targeted defense request");
        assertEquals(pair.target.id, defense.targetSettlementId);
        CounterattackMission mission = game.settlementManager.dispatchCounterattack(
                game, pair.target, 4);
        assertNotNull(mission);
        assertEquals(mission.id, defense.targetMissionId,
                "dispatch binds the provider's pending request to this exact mission");

        game.player.pos.set(50_000, 50, 50_000);
        advanceTo(game, mission, CounterattackMission.Phase.ASSAULT, 5_000);
        game.player.pos.set(pair.target.center.x() + 0.5f, pair.target.center.y() + 1f,
                pair.target.center.z() + 0.5f);
        game.world.ensureChunks(pair.target.center.x(), pair.target.center.z(), 3, 10_000);
        game.settlementManager.slowTick(game, 0.1f);
        game.settlementManager.fastTick(game, 0.1f);
        var attackers = game.settlementManager.counterattacks.members(game, mission.id);
        assertFalse(attackers.isEmpty());
        int occupiedResidents = pair.target.aliveResidents();
        game.player.inventory.set(0, new ItemStack(ItemType.IRON_SPEAR, 1));
        game.player.hotbarSel = 0;
        for (Npc attacker : attackers) {
            int swings = 0;
            while (!attacker.dead) {
                game.player.pos.set(attacker.pos.x - 1f, attacker.pos.y, attacker.pos.z);
                game.advancePlayerAttackCooldown(0.5f);
                assertTrue(game.performPlayerAttack(attacker),
                        "the same LMB melee command resolves each active attacker");
                assertTrue(++swings < 20, "counterattack combat remains bounded");
            }
        }
        game.entities.fastTick(game, 0.05f);
        game.settlementManager.fastTick(game, 0.05f);

        assertEquals(CounterattackMission.Outcome.DEFENDER_VICTORY, mission.outcome);
        assertTrue(pair.target.occupied, "defender victory preserves occupation");
        assertEquals(occupiedResidents, pair.target.aliveResidents());
        assertTrue(mission.outcomeApplied);
        assertEquals(Quest.Status.READY_TO_TURN_IN, defense.status,
                "the real combat outcome completes only its bound defend-outpost request");
        int ingots = game.player.inventory.count(com.veylon.item.ItemType.IRON_INGOT);
        assertTrue(game.faction.turnInSettlementQuest(game, pair.target)
                .contains("remember your help"));
        assertEquals(ingots + 2,
                game.player.inventory.count(com.veylon.item.ItemType.IRON_INGOT));

        advanceUntilRemoved(game, mission.id, 5_000);
        assertNull(game.settlementManager.counterattacks.get(mission.id));
        assertTrue(game.entities.npcs.stream().noneMatch(n -> mission.id.equals(n.partyMissionId)),
                "cleanup leaves no orphan mission members");
        assertTrue(game.settlementManager.availableNpcCapacity(game,
                SettlementManager.NpcCategory.COUNTERATTACK) > 0,
                "cleanup releases counterattack capacity");
    }

    @Test
    void dormantOutcomesAreDeterministicAndOwnershipChangesOnlyAfterResolvedVictory() {
        Result weakA = runDormant(999L, 1, 8);
        Result weakB = runDormant(999L, 1, 8);
        assertEquals(weakA.outcome, weakB.outcome);
        assertEquals(CounterattackMission.Outcome.ATTACKER_VICTORY, weakA.outcome);
        assertFalse(weakA.occupied);
        assertEquals(weakA.founderFaction, weakA.owner);
        assertEquals(weakA.residentCount, weakB.residentCount,
                "deterministic resolution seeds the same bounded replacement garrison");

        Result strongA = runDormant(-1234567L, 8, 1);
        Result strongB = runDormant(-1234567L, 8, 1);
        assertEquals(CounterattackMission.Outcome.DEFENDER_VICTORY, strongA.outcome);
        assertEquals(strongA.outcome, strongB.outcome);
        assertTrue(strongA.occupied);
        assertEquals(HumanFaction.FRONTIER, strongA.owner);
    }

    @Test
    void outboundAssaultAndRetreatRoundTripWithoutDuplication(@TempDir Path dir) {
        Game game = new Game();
        game.newWorld(987654321L, true);
        Pair pair = hostilePair(game);
        configureOutpost(game, pair.target, 8);
        game.player.pos.set(50_000, 50, 50_000);
        CounterattackMission mission = game.settlementManager.dispatchCounterattack(
                game, pair.target, 1);
        assertNotNull(mission);
        game.settlementManager.fastTick(game, 1f);
        assertEquals(CounterattackMission.Phase.OUTBOUND, mission.phase);

        game = roundTrip(game, dir.resolve("counter-outbound.sav"));
        mission = onlyMission(game);
        assertEquals(CounterattackMission.Phase.OUTBOUND, mission.phase);
        assertEquals(1, mission.survivors);

        advanceTo(game, mission, CounterattackMission.Phase.ASSAULT, 5_000);
        float assaultTimer = mission.phaseTimer;
        game = roundTrip(game, dir.resolve("counter-assault.sav"));
        mission = onlyMission(game);
        assertEquals(CounterattackMission.Phase.ASSAULT, mission.phase);
        assertEquals(assaultTimer, mission.phaseTimer, 0.001f);

        // Strong dormant garrison deterministically wins after the persisted timer.
        while (mission.phase == CounterattackMission.Phase.ASSAULT) {
            game.settlementManager.fastTick(game, 1f);
        }
        assertEquals(CounterattackMission.Phase.RETREAT, mission.phase);
        assertEquals(CounterattackMission.Outcome.DEFENDER_VICTORY, mission.outcome);
        String id = mission.id;
        game = roundTrip(game, dir.resolve("counter-retreat.sav"));
        mission = game.settlementManager.counterattacks.get(id);
        assertNotNull(mission);
        assertEquals(CounterattackMission.Phase.RETREAT, mission.phase);
        assertTrue(mission.outcomeApplied);
        assertEquals(1, game.settlementManager.counterattacks.missions.size());
        assertTrue(game.entities.npcs.stream().noneMatch(n -> id.equals(n.partyMissionId)),
                "dormant save/load does not duplicate active members");

        advanceUntilRemoved(game, id, 5_000);
        assertNull(game.settlementManager.counterattacks.get(id));
        assertTrue(game.entities.npcs.stream().noneMatch(n -> id.equals(n.partyMissionId)));
    }

    private static Result runDormant(long seed, int defenders, int attackers) {
        Game game = new Game();
        game.newWorld(seed, true);
        Pair pair = hostilePair(game);
        configureOutpost(game, pair.target, defenders);
        game.player.pos.set(50_000, 50, 50_000);
        CounterattackMission mission = game.settlementManager.dispatchCounterattack(
                game, pair.target, attackers);
        assertNotNull(mission);
        advanceTo(game, mission, CounterattackMission.Phase.ASSAULT, 5_000);
        while (mission.phase == CounterattackMission.Phase.ASSAULT) {
            game.settlementManager.fastTick(game, 1f);
        }
        CounterattackMission.Outcome outcome = mission.outcome;
        boolean occupied = pair.target.occupied;
        String owner = pair.target.factionId;
        int residents = pair.target.residents.size();
        // Repeated ticks cannot replay ownership or seed another population.
        for (int i = 0; i < 20; i++) {
            game.settlementManager.fastTick(game, 1f);
        }
        assertEquals(occupied, pair.target.occupied);
        assertEquals(owner, pair.target.factionId);
        assertEquals(residents, pair.target.residents.size());
        return new Result(outcome, occupied, owner, pair.target.founderFaction, residents);
    }

    private static Game roundTrip(Game game, Path path) {
        assertTrue(SaveSystem.save(game, path));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, path));
        return loaded;
    }

    private static CounterattackMission onlyMission(Game game) {
        assertEquals(1, game.settlementManager.counterattacks.missions.size());
        return game.settlementManager.counterattacks.missions.values().iterator().next();
    }

    private static void advanceTo(Game game, CounterattackMission mission,
                                  CounterattackMission.Phase wanted, int maxTicks) {
        for (int i = 0; i < maxTicks && mission.phase != wanted; i++) {
            game.settlementManager.fastTick(game, 1f);
        }
        assertEquals(wanted, mission.phase, "mission did not reach " + wanted);
    }

    private static void advanceUntilRemoved(Game game, String id, int maxTicks) {
        for (int i = 0; i < maxTicks
                && game.settlementManager.counterattacks.get(id) != null; i++) {
            game.settlementManager.fastTick(game, 1f);
        }
    }

    private static double distanceToTarget(CounterattackMission mission) {
        double dx = mission.target.x() + 0.5 - mission.x;
        double dz = mission.target.z() + 0.5 - mission.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void configureOutpost(Game game, Settlement target, int defenders) {
        game.entities.npcs.removeIf(n -> n.settled() && n.settlementId == target.id);
        target.residents.clear();
        for (int i = 0; i < defenders; i++) {
            target.residents.add(new Settlement.Resident("Frontier guard " + i,
                    NpcArchetype.GUARD));
        }
        target.residents.add(new Settlement.Resident("Outpost quartermaster",
                NpcArchetype.TRADER));
        target.cleared = true;
        target.occupied = true;
        target.alignment = Settlement.Alignment.FRIENDLY;
        target.factionId = HumanFaction.FRONTIER;
        target.commandNeutralized = true;
        target.alarmNeutralized = true;
        target.centralObjectiveControlled = true;
        target.counterattackTimer = 0;
    }

    private static Npc outpostProvider(Game game, Settlement target, int traderIndex) {
        Npc provider = game.entities.spawnNpc(game.world, "Outpost quartermaster",
                target.center.x() + 0.5f, target.center.y() + 0.4f,
                target.center.z() + 0.5f);
        provider.archetype = NpcArchetype.TRADER;
        provider.isTrader = true;
        provider.settlementId = target.id;
        provider.residentIndex = traderIndex;
        target.residents.get(traderIndex).live = provider;
        return provider;
    }

    /** Finds two generator-planned hostile settlements owned by the same force. */
    private static Pair hostilePair(Game game) {
        Map<String, Settlement> firstByFaction = new HashMap<>();
        for (int radius = 1; radius <= 22; radius++) {
            for (int rx = -radius; rx <= radius; rx++) {
                for (int rz = -radius; rz <= radius; rz++) {
                    if (Math.max(Math.abs(rx), Math.abs(rz)) != radius) {
                        continue;
                    }
                    Settlement settlement = game.world.settlementForRegion(rx, rz);
                    if (settlement == null || !settlement.hostile()) {
                        continue;
                    }
                    Settlement first = firstByFaction.putIfAbsent(
                            settlement.founderFaction, settlement);
                    if (first != null && first != settlement) {
                        return new Pair(first, settlement);
                    }
                }
            }
        }
        throw new AssertionError("seed did not plan a pair of same-faction hostile settlements");
    }

    private record Pair(Settlement target, Settlement origin) {
    }

    private record Result(CounterattackMission.Outcome outcome, boolean occupied, String owner,
                          String founderFaction, int residentCount) {
    }
}
