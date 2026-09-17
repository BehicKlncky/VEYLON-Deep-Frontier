package com.veylon.entity;

import com.veylon.Game;
import com.veylon.settlement.HumanFaction;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementPlanner;
import com.veylon.settlement.SettlementType;
import com.veylon.util.Vec3i;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Entity.dead} means two different things and only one of them is death.
 *
 * <p>Half the flags in the game are despawns: a trader whose visit ended, a
 * raider fading at the edge of the camp, a war party whose settlement was
 * cleared, survivors who routed. Every one of those keeps its health, and every
 * one of them would drop dead on the road in full view of the player if the
 * ragdoll were gated on the flag instead of on {@code health <= 0}.
 *
 * <p>These drive the real AI paths that set the flag rather than setting it by
 * hand, so the test still means something if one of those paths moves.
 */
class OverloadedDeadFlagTest {

    private Game game;

    @BeforeEach
    void setUp() {
        game = RagdollTestArena.create(31337L);
    }

    @Test
    void aTraderWhoseVisitEndsLeavesQuietlyWithNoBodyAtAll() {
        Npc trader = game.entities.spawnNpc(game.world, "Wandering Trader",
                RagdollTestArena.CENTER_X + 2f, RagdollTestArena.GROUND + 0.1f,
                RagdollTestArena.CENTER_Z);
        trader.isTrader = true;
        trader.leaveTimer = 0.01f;

        // The real NpcAI trader path expires the visit and sets the flag.
        for (int i = 0; i < 10 && game.entities.npcCount() > 0; i++) {
            game.entities.fastTick(game, 0.05f);
        }

        assertEquals(0, game.entities.npcCount(), "precondition: the trader left");
        assertTrue(trader.health > 0,
                "precondition: the trader left alive, which is the whole hazard");
        assertNoBody("a trader whose visit ended");
    }

    @Test
    void aRaiderFadingAtTheCampEdgeLeavesNoBody() {
        Npc raider = game.entities.spawnNpc(game.world, "Ash Raider",
                RagdollTestArena.CENTER_X + 2f, RagdollTestArena.GROUND + 0.1f,
                RagdollTestArena.CENTER_Z);
        raider.raider = true;
        // Past the fade threshold the raid path uses.
        raider.leaveTimer = -9f;

        for (int i = 0; i < 10 && game.entities.npcCount() > 0; i++) {
            game.entities.fastTick(game, 0.05f);
        }

        assertEquals(0, game.entities.npcCount(), "precondition: the raider faded out");
        assertTrue(raider.health > 0, "precondition: it faded alive");
        assertNoBody("a raider fading at the camp edge");
    }

    @Test
    void aWarPartyMemberWhoseOriginIsGoneLeavesNoBody() {
        Npc member = game.entities.spawnNpc(game.world, "Raid Scout",
                RagdollTestArena.CENTER_X + 2f, RagdollTestArena.GROUND + 0.1f,
                RagdollTestArena.CENTER_Z);
        member.warParty = true;
        member.archetype = NpcArchetype.SCOUT;
        member.partyFactionId = HumanFaction.SCAVENGERS;
        member.partyMissionId = "mission:gone";
        // An origin id no settlement in this world has: the mission is over.
        member.originSettlementId = 0xDEADBEEFL;
        // Out of perception range, or the party brain fights instead of standing
        // its mission down, and the despawn path is never reached.
        game.player.pos.set(RagdollTestArena.CENTER_X + 900f, RagdollTestArena.GROUND + 0.1f,
                RagdollTestArena.CENTER_Z + 900f);

        for (int i = 0; i < 20 && game.entities.npcCount() > 0; i++) {
            game.entities.fastTick(game, 0.05f);
        }

        assertEquals(0, game.entities.npcCount(),
                "precondition: the disbanded party member despawned");
        assertTrue(member.health > 0, "precondition: it despawned alive");
        assertNoBody("a war-party member whose settlement was cleared");
    }

    @Test
    void routedSurvivorsWithdrawWithoutLeavingCorpses() {
        Settlement fort = hostileSettlement();
        Settlement.Resident leaderRecord = new Settlement.Resident("Warlord", NpcArchetype.LEADER);
        leaderRecord.alive = false;
        fort.residents.add(leaderRecord);
        Npc survivor = resident(fort, "Survivor", NpcArchetype.BRUTE);
        fort.morale = 1f;

        // The production rout path, not a hand-set flag.
        game.settlementManager.checkCleared(game, fort);
        assertTrue(survivor.dead, "precondition: the survivor is flagged for removal");
        assertTrue(survivor.health > 0, "precondition: routed survivors are alive");

        game.entities.fastTick(game, 0.05f);

        assertEquals(0, game.entities.npcCount());
        assertNoBody("a routed survivor");
    }

    @Test
    void deathByIllnessIsARealDeathAndDoesLeaveABody() {
        // The counterexample that proves the gate is not simply "never spawn".
        // Illness kills through health, so it must behave like any other death.
        Npc sick = game.entities.spawnNpc(game.world, "Fevered Settler",
                RagdollTestArena.CENTER_X + 2f, RagdollTestArena.GROUND + 0.1f,
                RagdollTestArena.CENTER_Z);
        sick.health = 0f;
        sick.dead = true;
        sick.lastHitByPlayer = false;

        game.entities.fastTick(game, 0.05f);

        assertEquals(1, game.ragdolls.liveCount(),
                "a death with no player involved is still a death");
        RagdollTestArena.settleAll(game);
        assertEquals(1, game.entities.corpses.size());
    }

    private void assertNoBody(String what) {
        assertEquals(0, game.ragdolls.liveCount(),
                what + " must not spawn a falling body");
        assertEquals(0, game.ragdolls.totalSpawned,
                what + " must never have spawned a body at any point");
        assertTrue(game.entities.corpses.isEmpty(),
                what + " must not leave a corpse behind");
        assertTrue(game.entities.carcasses.isEmpty(),
                what + " must not leave a carcass behind");
    }

    private Settlement hostileSettlement() {
        int x = 12 * SettlementPlanner.REGION_BLOCKS + 120;
        int z = 12 * SettlementPlanner.REGION_BLOCKS + 120;
        int y = game.world.surfaceHeight(x, z) + 1;
        Settlement s = new Settlement(Settlement.packId(12, 12), 12, 12,
                SettlementType.VILLAGE, new Vec3i(x, y, z),
                HumanFaction.SCAVENGERS, Settlement.Alignment.HOSTILE);
        s.factionId = HumanFaction.SCAVENGERS;
        game.world.settlements.put(s.id, s);
        return s;
    }

    private Npc resident(Settlement home, String name, NpcArchetype archetype) {
        Settlement.Resident record = new Settlement.Resident(name, archetype);
        home.residents.add(record);
        Npc npc = game.entities.spawnNpc(game.world, name,
                home.center.x() + 0.5f, home.center.y(), home.center.z() + 0.5f);
        npc.archetype = archetype;
        npc.settlementId = home.id;
        npc.residentIndex = home.residents.size() - 1;
        record.live = npc;
        return npc;
    }
}
