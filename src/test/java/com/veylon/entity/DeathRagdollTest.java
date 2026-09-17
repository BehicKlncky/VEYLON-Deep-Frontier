package com.veylon.entity;

import com.veylon.Game;
import com.veylon.item.ItemType;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementPlanner;
import com.veylon.settlement.SettlementType;
import com.veylon.util.Vec3i;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a death leaves behind, and when.
 *
 * <p>The ordering is the point of the feature: the body falls first and only
 * becomes a carcass or a corpse where it comes to rest, while every reputation,
 * loot and bookkeeping consequence still fires on the tick of death.
 */
class DeathRagdollTest {

    private Game game;

    @BeforeEach
    void setUp() {
        game = RagdollTestArena.create(20260918L);
    }

    @Test
    void aKilledCreatureBecomesOneFallingBodyAndLeavesNoCarcassUntilItSettles() {
        Creature deer = spawnDeer();
        RagdollTestArena.kill(deer, 2.5f, 1.2f, 0f);

        game.entities.fastTick(game, 0.05f);

        assertEquals(1, game.ragdolls.liveCount(),
                "a death must produce exactly one falling body");
        assertFalse(game.entities.creatures.contains(deer),
                "the dead animal leaves the live list on the tick it dies");
        assertTrue(game.entities.carcasses.isEmpty(),
                "no carcass may exist while the body is still in the air");
    }

    @Test
    void theSettledBodyBecomesExactlyOneCarcassWithItsYieldAndLodgedArrows() {
        Creature deer = spawnDeer();
        deer.stuckArrows = 3;
        deer.stuckArrowType = ItemType.IRON_ARROW;
        RagdollTestArena.kill(deer, 3f, 1f, 1f);
        game.entities.fastTick(game, 0.05f);

        RagdollTestArena.settleAll(game);

        assertEquals(0, game.ragdolls.liveCount(), "the body must not stay unsettled");
        assertEquals(1, game.entities.carcasses.size(),
                "a settled animal leaves exactly one carcass");
        Carcass carcass = game.entities.carcasses.getFirst();
        assertSame(Creature.CreatureType.DEER, carcass.type);
        assertEquals(Creature.CreatureType.DEER.meatYield, carcass.meatLeft);
        assertEquals(Creature.CreatureType.DEER.hideYield, carcass.hideLeft);
        assertEquals(3, carcass.stuckArrows,
                "lodged arrows must survive the fall or they become unrecoverable");
        assertSame(ItemType.IRON_ARROW, carcass.stuckArrowType);
        assertTrue(carcass.pose.solved,
                "a carcass from a ragdoll is drawn in the pose it settled in");
    }

    @Test
    void theCarcassRestsWhereTheBodyStoppedNotWhereTheAnimalDied() {
        Creature deer = spawnDeer();
        float deathX = deer.pos.x;
        RagdollTestArena.kill(deer, 8f, 3f, 0f);
        game.entities.fastTick(game, 0.05f);
        RagdollTestArena.settleAll(game);

        Carcass carcass = game.entities.carcasses.getFirst();
        assertNotEquals(deathX, carcass.pos.x, 0.25f,
                "a body thrown by the killing blow must not land on the spot it died");
    }

    @Test
    void aKilledPersonLeavesACorpseInsteadOfVanishing() {
        Npc raider = game.entities.spawnNpc(game.world, "Ash Raider",
                RagdollTestArena.CENTER_X + 2f, RagdollTestArena.GROUND + 0.1f,
                RagdollTestArena.CENTER_Z);
        raider.raider = true;
        raider.archetype = NpcArchetype.SCAVENGER;
        RagdollTestArena.kill(raider, -2f, 1.5f, 0f);

        game.entities.fastTick(game, 0.05f);
        assertEquals(0, game.entities.npcCount(),
                "the dead person leaves the live list on the tick they die");
        assertEquals(1, game.ragdolls.liveCount());

        RagdollTestArena.settleAll(game);
        assertEquals(1, game.entities.corpses.size(),
                "killing someone must leave a body, not nothing at all");
        HumanCorpse corpse = game.entities.corpses.getFirst();
        assertTrue(corpse.appearance.raider,
                "the corpse keeps the look of the person who died");
        assertSame(NpcArchetype.SCAVENGER, corpse.appearance.archetype);
        assertTrue(corpse.pose.solved);
    }

    @Test
    void raiderLootAndReputationStillFireOnTheTickOfDeathNotOnSettle() {
        Npc raider = game.entities.spawnNpc(game.world, "Ash Raider",
                RagdollTestArena.CENTER_X + 2f, RagdollTestArena.GROUND + 0.1f,
                RagdollTestArena.CENTER_Z);
        raider.raider = true;
        float trustBefore = game.faction.trust;
        int scrapBefore = game.player.inventory.count(ItemType.SCRAP);
        RagdollTestArena.kill(raider, -2f, 1.5f, 0f);

        game.entities.fastTick(game, 0.05f);

        assertTrue(game.player.inventory.count(ItemType.SCRAP) > scrapBefore,
                "raider loot must not wait for the body to stop moving");
        assertTrue(game.faction.trust > trustBefore,
                "reputation credit for a kill must land on the tick of the kill");
        assertEquals(1, game.ragdolls.liveCount(),
                "precondition: the body is still falling while those already happened");
    }

    @Test
    void settlementBookkeepingStillFiresOnTheTickOfDeath() {
        Settlement home = settlement(12, 12);
        Npc guard = resident(home, "Warden", NpcArchetype.GUARD);
        RagdollTestArena.kill(guard, 1f, 1f, 0f);

        game.entities.fastTick(game, 0.05f);

        Settlement.Resident record = home.residents.getFirst();
        assertFalse(record.alive,
                "the resident record must be closed on the tick the resident dies");
        assertEquals(1, game.ragdolls.liveCount(),
                "precondition: the bookkeeping happened while the body was still falling");
    }

    @Test
    void aHumanCorpseIsInertAndCostsNothingAgainstTheLivingPopulation() {
        Npc victim = game.entities.spawnNpc(game.world, "Villager",
                RagdollTestArena.CENTER_X + 2f, RagdollTestArena.GROUND + 0.1f,
                RagdollTestArena.CENTER_Z);
        RagdollTestArena.kill(victim, 1f, 1f, 0f);
        game.entities.fastTick(game, 0.05f);
        RagdollTestArena.settleAll(game);

        assertEquals(1, game.entities.corpses.size());
        assertEquals(0, game.entities.npcCount(),
                "a corpse must not count against the active NPC budget");
        HumanCorpse corpse = game.entities.corpses.getFirst();
        assertEquals(null, game.entities.nearestNpc(corpse.pos.x, corpse.pos.y, corpse.pos.z, 40),
                "a corpse must never be found as a perception or combat target");
    }

    @Test
    void aBirdTumblesAndThenLeavesNothingBehind() {
        Creature bird = game.entities.spawnCreature(game.world, Creature.CreatureType.BIRD,
                RagdollTestArena.CENTER_X + 2f, RagdollTestArena.GROUND + 6f,
                RagdollTestArena.CENTER_Z);
        RagdollTestArena.kill(bird, 1f, 0f, 0f);

        game.entities.fastTick(game, 0.05f);
        assertEquals(1, game.ragdolls.liveCount(),
                "a bird still falls; it is only what it leaves that differs");

        RagdollTestArena.settleAll(game);
        assertTrue(game.entities.carcasses.isEmpty(),
                "a bird is too small to leave a carcass");
        assertTrue(game.entities.corpses.isEmpty());
    }

    @Test
    void aSettledKillLeavesALastingBloodStain() {
        int tracksBefore = game.entities.tracks.size();
        Creature deer = spawnDeer();
        RagdollTestArena.kill(deer, 2f, 1f, 0f);
        game.entities.fastTick(game, 0.05f);
        RagdollTestArena.settleAll(game);

        assertEquals(tracksBefore + 1, game.entities.tracks.size(),
                "a kill must leave a mark where the body came to rest");
        Track stain = game.entities.tracks.getLast();
        assertTrue(stain.blood, "the mark a body leaves is blood, not a footprint");
        assertTrue(stain.describe().startsWith("Blood trail"),
                "a blood mark must describe itself without dereferencing a species");
    }

    @Test
    void aHumanBloodStainDescribesItselfWithoutASpeciesName() {
        Npc victim = game.entities.spawnNpc(game.world, "Villager",
                RagdollTestArena.CENTER_X + 2f, RagdollTestArena.GROUND + 0.1f,
                RagdollTestArena.CENTER_Z);
        RagdollTestArena.kill(victim, 1f, 1f, 0f);
        game.entities.fastTick(game, 0.05f);
        RagdollTestArena.settleAll(game);

        Track stain = game.entities.tracks.getLast();
        assertEquals(null, stain.type, "a person is not a creature type");
        assertTrue(stain.describe().startsWith("Blood trail"));
    }

    @Test
    void liveBodiesAreCappedAndTheOldestSettlesImmediately() {
        for (int i = 0; i < RagdollConstants.MAX_LIVE + 4; i++) {
            Creature hare = game.entities.spawnCreature(game.world, Creature.CreatureType.HARE,
                    RagdollTestArena.CENTER_X + i * 1.5f, RagdollTestArena.GROUND + 0.1f,
                    RagdollTestArena.CENTER_Z);
            RagdollTestArena.kill(hare, 1f, 1f, 0f);
            game.entities.fastTick(game, 0.05f);
        }

        assertEquals(RagdollConstants.MAX_LIVE, game.ragdolls.liveCount(),
                "live bodies must be capped so a massacre cannot grow without bound");
        assertEquals(4, game.entities.carcasses.size(),
                "bodies pushed over the cap settle immediately rather than disappearing");
    }

    @Test
    void anAnimalHarvestedAfterSettlingYieldsThroughTheRealInteractionCommand() {
        Creature deer = spawnDeer();
        RagdollTestArena.kill(deer, 1f, 0.5f, 0f);
        game.entities.fastTick(game, 0.05f);

        game.player.pos.set(deer.pos.x, RagdollTestArena.GROUND + 0.1f, deer.pos.z);
        assertFalse(game.interactWithNearbyCarcass(),
                "there is nothing to harvest while the body is still falling");

        RagdollTestArena.settleAll(game);
        Carcass carcass = game.entities.carcasses.getFirst();
        game.player.pos.set(carcass.pos.x, carcass.pos.y, carcass.pos.z);
        assertTrue(game.interactWithNearbyCarcass(),
                "a settled body is harvestable through the same command F uses");
        assertTrue(game.player.inventory.count(ItemType.RAW_MEAT) > 0);
    }

    @Test
    void corpsesAndCarcassesRotAwayOnTheSlowTick() {
        Npc victim = game.entities.spawnNpc(game.world, "Villager",
                RagdollTestArena.CENTER_X + 2f, RagdollTestArena.GROUND + 0.1f,
                RagdollTestArena.CENTER_Z);
        RagdollTestArena.kill(victim, 1f, 1f, 0f);
        game.entities.fastTick(game, 0.05f);
        RagdollTestArena.settleAll(game);
        assertEquals(1, game.entities.corpses.size());

        game.entities.tickWorldDetritus(game, RagdollConstants.CORPSE_DECAY + 1f);

        assertTrue(game.entities.corpses.isEmpty(),
                "a corpse must rot away rather than lie in the world forever");
    }

    @Test
    void corpsesFarFromThePlayerAreDespawnedLikeWildlife() {
        Npc victim = game.entities.spawnNpc(game.world, "Villager",
                RagdollTestArena.CENTER_X + 2f, RagdollTestArena.GROUND + 0.1f,
                RagdollTestArena.CENTER_Z);
        RagdollTestArena.kill(victim, 1f, 1f, 0f);
        game.entities.fastTick(game, 0.05f);
        RagdollTestArena.settleAll(game);
        assertEquals(1, game.entities.corpses.size());

        game.player.pos.set(RagdollTestArena.CENTER_X + RagdollConstants.DESPAWN_DISTANCE + 50f,
                RagdollTestArena.GROUND + 0.1f, RagdollTestArena.CENTER_Z);
        game.entities.slowTick(game);

        assertTrue(game.entities.corpses.isEmpty(),
                "bodies must not accumulate behind a player walking away");
    }

    @Test
    void aNewWorldLeavesNoBodiesFromThePreviousOne() {
        Creature deer = spawnDeer();
        RagdollTestArena.kill(deer, 2f, 2f, 0f);
        game.entities.fastTick(game, 0.05f);
        assertEquals(1, game.ragdolls.liveCount(), "precondition: a body is mid-fall");

        game.newWorld(4242L, true);

        assertEquals(0, game.ragdolls.liveCount(),
                "a falling body must not survive into the next world");
        assertTrue(game.entities.corpses.isEmpty(),
                "corpses must not survive into the next world");
    }

    private Creature spawnDeer() {
        return game.entities.spawnCreature(game.world, Creature.CreatureType.DEER,
                RagdollTestArena.CENTER_X + 2f, RagdollTestArena.GROUND + 0.1f,
                RagdollTestArena.CENTER_Z);
    }

    private Settlement settlement(int rx, int rz) {
        int x = rx * SettlementPlanner.REGION_BLOCKS + 120;
        int z = rz * SettlementPlanner.REGION_BLOCKS + 120;
        int y = game.world.surfaceHeight(x, z) + 1;
        Settlement s = new Settlement(Settlement.packId(rx, rz), rx, rz,
                SettlementType.VILLAGE, new Vec3i(x, y, z),
                com.veylon.settlement.HumanFaction.FREE_SETTLERS,
                Settlement.Alignment.NEUTRAL);
        s.factionId = com.veylon.settlement.HumanFaction.FREE_SETTLERS;
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
