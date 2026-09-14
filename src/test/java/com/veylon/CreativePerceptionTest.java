package com.veylon;

import com.veylon.ai.CreatureAI;
import com.veylon.ai.SettledNpcAI;
import com.veylon.combat.WeaponDefinition;
import com.veylon.combat.WeaponRegistry;
import com.veylon.engine.AudioManager;
import com.veylon.entity.Creature;
import com.veylon.entity.Creature.CreatureState;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.settlement.HumanFaction;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementType;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** R11-R12: wildlife and settlers ignore a Creative player; Survival twins and D3 consequences stay. */
class CreativePerceptionTest {
    private static final Vector3f EAST = new Vector3f(1, 0, 0);
    private static final Set<CreatureState> PURSUIT = EnumSet.of(CreatureState.STALK,
            CreatureState.ATTACK, CreatureState.CHARGE, CreatureState.HUNT, CreatureState.FLEE_HURT);

    @ParameterizedTest
    @EnumSource(value = Creature.CreatureType.class, names = {"WOLF", "THORNHORN", "STALKER"})
    void predatorsNeverDetectChargeAmbushOrAttackACreativePlayer(Creature.CreatureType type) {
        for (GameMode mode : GameMode.values()) {
            Game game = CreativeTestArena.create(mode);
            game.time.totalMinutes = 0;
            Creature creature = game.entities.spawnCreature(game.world, type, 311.5f, 40.001f, 310.5f);
            creature.hunger = 80;
            if (type == Creature.CreatureType.THORNHORN) {
                assertTrue(game.performPlayerAttack(creature), "Fixture: the player wounds the thornhorn");
            }
            boolean pursued = false;
            for (int i = 0; i < 40; i++) {
                CreatureAI.update(game, creature, 0.05f);
                pursued |= PURSUIT.contains(creature.state);
            }
            if (mode == GameMode.CREATIVE) {
                assertFalse(pursued, "R11: " + type + " never enters chase, charge, ambush or attack");
                assertEquals(0, game.noise.count(), "R11: no attack or player event reaches perception");
                assertEquals(game.player.maxHealth, game.player.health);
            } else {
                assertTrue(pursued, "Survival " + type + " still pursues the player");
                assertTrue(game.player.health < game.player.maxHealth, "Survival " + type + " still attacks");
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = Creature.CreatureType.class, names = {"DEER", "HARE", "BIRD"})
    void preyAndBirdsDoNotFleeFromACreativePlayerBesideThem(Creature.CreatureType type) {
        for (GameMode mode : GameMode.values()) {
            WildlifeAudio audio = new WildlifeAudio();
            Game game = CreativeTestArena.create(mode, audio);
            Creature creature = game.entities.spawnCreature(game.world, type, 312.5f, 40.001f, 310.5f);
            boolean fled = false;
            for (int i = 0; i < 10; i++) {
                CreatureAI.update(game, creature, 0.05f);
                fled |= creature.state == CreatureState.FLEE;
            }
            boolean reacted = fled || audio.flaps > 0;
            if (mode == GameMode.CREATIVE) {
                assertFalse(reacted, "R11: " + type + " does not flee from an unperceived player");
            } else {
                assertTrue(reacted, "Survival " + type + " still flees from a close player");
            }
        }
    }

    @Test
    void aHostileSettlerWithLineOfSightRaisesNoAlarmAndKeepsAStaleMemory() {
        for (GameMode mode : GameMode.values()) {
            Game game = CreativeTestArena.create(mode);
            Settlement fort = settlement(game, Settlement.Alignment.HOSTILE, HumanFaction.HEADHUNTERS);
            Npc guard = resident(game, fort, "Watcher", NpcArchetype.GUARD, 312f, 40.001f, 310.5f);
            guard.yaw = 270f;
            guard.lastKnown.set(1, 2, 3);
            for (int i = 0; i < 10; i++) {
                guard.decideTimer = 0f;
                SettledNpcAI.update(game, guard, 0.31f);
            }
            if (mode == GameMode.CREATIVE) {
                assertEquals(0f, fort.alertLevel, "R11: seeing a Creative player raises no alert");
                assertEquals(0, game.noise.countCategory("alarm-shout"), "R11: no alarm shout");
                assertTrue(guard.lastKnownAge > 900f, "R11: the settler never records a sighting");
                assertEquals(new Vector3f(1, 2, 3), guard.lastKnown, "R11: the stale memory stays stale");
                assertNotEquals(Npc.NpcState.ATTACK, guard.state, "R11: no combat intent forms");
            } else {
                assertTrue(fort.alertLevel >= 45f, "Survival sight still raises the alarm");
            }
        }
    }

    @Test
    void creativeGunshotAndMiningBesideASettlementRaiseNoAlertOrSearch() {
        for (GameMode mode : GameMode.values()) {
            Game game = CreativeTestArena.create(mode);
            Settlement fort = settlement(game, Settlement.Alignment.HOSTILE, HumanFaction.HEADHUNTERS);
            Npc listener = resident(game, fort, "Listener", NpcArchetype.GUARD, 326.5f, 40.001f, 310.5f);
            listener.yaw = 90f; // faces away: only hearing could reveal the player
            Vec3i plank = new Vec3i(312, 40, 312);
            game.world.setBlock(plank.x(), plank.y(), plank.z(), BlockType.PLANK, false);
            assertTrue(game.completePlayerBlockBreak(plank), "Fixture: the structure block breaks");
            game.player.inventory.set(0, new ItemStack(ItemType.MUSKET, 1));
            game.player.hotbarSel = 0;
            game.player.inventory.add(ItemType.MUSKET_BALL, 1);
            WeaponDefinition musket = WeaponRegistry.of(ItemType.MUSKET);
            assertEquals(Game.FirearmCommandResult.RELOAD_STARTED,
                    game.updateFirearmCommand(0f, false, false, true, EAST));
            game.tickReload(musket.reloadTime + 0.01f);
            assertEquals(Game.FirearmCommandResult.FIRED,
                    game.updateFirearmCommand(0f, true, true, false, EAST));
            SettledNpcAI.update(game, listener, 0.31f);
            if (mode == GameMode.CREATIVE) {
                assertEquals(0, game.noise.count(), "R11: gunshots and mining produce no perception events");
                assertTrue(listener.lastKnownAge > 900f, "R11: nothing reveals the Creative player's position");
                assertEquals(0f, listener.searchTimer, "R11: no investigation starts");
                assertEquals(0f, fort.alertLevel, "R11: no settlement alert");
            } else {
                assertEquals(1, game.noise.countCategory("gunshot"), "Survival gunshots remain audible");
                assertTrue(listener.searchTimer > 0f, "Survival hostile AI still investigates");
            }
        }
    }

    @Test
    void switchingToCreativeMidCombatStopsPursuitAndClearsMemoryOnTheNextTick() {
        Game game = CreativeTestArena.create(GameMode.SURVIVAL);
        game.time.totalMinutes = 0;
        Creature wolf = game.entities.spawnCreature(game.world, Creature.CreatureType.WOLF,
                311.5f, 40.001f, 310.5f);
        wolf.hunger = 80;
        CreatureAI.update(game, wolf, 0.05f);
        assertTrue(PURSUIT.contains(wolf.state), "precondition: the wolf is attacking");
        Settlement fort = settlement(game, Settlement.Alignment.HOSTILE, HumanFaction.HEADHUNTERS);
        Npc guard = resident(game, fort, "Guard", NpcArchetype.GUARD, 313f, 40.001f, 310.5f);
        guard.yaw = 270f;
        SettledNpcAI.update(game, guard, 0.31f);
        assertEquals(0f, guard.lastKnownAge, "precondition: the guard sees the player");
        Npc hunter = game.entities.spawnNpc(game.world, "Hunter", 330.5f, 40.001f, 330.5f);
        hunter.archetype = NpcArchetype.TRACKER;
        hunter.warParty = true;
        hunter.partyKind = Npc.PartyKind.BOUNTY_HUNTER;
        hunter.partyMission = Npc.PartyMission.OUTBOUND;
        assertTrue(game.emitPlayerFootstepNoise(true, false), "precondition: a player event is queued");
        game.player.scent = 1f;

        assertTrue(game.switchGameMode(GameMode.CREATIVE));
        assertEquals(0, game.noise.countCategory("sprint"), "R4: queued player events are forgotten");
        assertEquals(0f, game.player.scent, "R4: carried scent does not survive the switch");
        assertEquals(0f, guard.searchTimer, "R4: the settler's search is retired");
        assertEquals(Npc.PartyMission.RETURNING, hunter.partyMission, "R4: pursuing parties turn home");

        CreatureAI.update(game, wolf, 0.05f);
        guard.decideTimer = 0f;
        SettledNpcAI.update(game, guard, 0.31f);
        assertFalse(PURSUIT.contains(wolf.state), "R4: the wolf's pursuit ends on the next tick");
        assertTrue(guard.lastKnownAge > 900f, "R4: the settler forgot the last sighting");
        assertNotEquals(Npc.NpcState.ATTACK, guard.state, "R4: no attack continues");
    }

    @Test
    void switchingBackToSurvivalResumesPerception() {
        Game game = CreativeTestArena.create(GameMode.CREATIVE);
        game.time.totalMinutes = 0;
        Creature wolf = game.entities.spawnCreature(game.world, Creature.CreatureType.WOLF,
                311.5f, 40.001f, 310.5f);
        wolf.hunger = 80;
        for (int i = 0; i < 20; i++) {
            CreatureAI.update(game, wolf, 0.05f);
            assertFalse(PURSUIT.contains(wolf.state), "precondition: Creative is not hunted");
        }
        assertTrue(game.switchGameMode(GameMode.SURVIVAL));
        for (int i = 0; i < 20 && game.player.health == game.player.maxHealth; i++) {
            CreatureAI.update(game, wolf, 0.05f);
        }
        assertTrue(game.player.health < game.player.maxHealth, "R5: perception and attacks resume");
        assertTrue(game.emitPlayerFootstepNoise(true, false), "R5: player events are audible again");
    }

    @Test
    void attackingASettlerCostsTheSameReputationWithoutAlarmOrWitnessMemory() {
        float survivalReputation = 0;
        float creativeReputation = 0;
        for (GameMode mode : GameMode.values()) {
            Game game = CreativeTestArena.create(mode);
            Settlement village = settlement(game, Settlement.Alignment.NEUTRAL, HumanFaction.FREE_SETTLERS);
            Npc farmer = resident(game, village, "Farmer", NpcArchetype.FARMER, 311.5f, 40.001f, 310.5f);
            assertTrue(game.performPlayerAttack(farmer), "Fixture: the swing lands");
            if (mode == GameMode.CREATIVE) {
                creativeReputation = village.localReputation;
                assertEquals(0f, village.alertLevel, "R11: the attack alarms nobody");
                assertTrue(farmer.lastKnownAge > 900f, "R11: the victim does not perceive the attacker");
                assertEquals(0f, farmer.searchTimer, "R11: no search for the attacker");
            } else {
                survivalReputation = village.localReputation;
                assertEquals(40f, village.alertLevel, 0.001f, "Survival attacks still alarm the settlement");
                assertEquals(0f, farmer.lastKnownAge, "Survival victims remember the attacker");
            }
        }
        assertTrue(survivalReputation < 0f, "Fixture: the attack costs reputation");
        assertEquals(survivalReputation, creativeReputation, 0.0001f,
                "D3/R12: reputation loss is identical in both modes");
    }

    @Test
    void friendlyTalkTradeAndGiftsStillWorkForACreativePlayer() {
        float survivalReputation = 0;
        float creativeReputation = 0;
        for (GameMode mode : GameMode.values()) {
            Game game = CreativeTestArena.create(mode);
            Settlement village = settlement(game, Settlement.Alignment.NEUTRAL, HumanFaction.FREE_SETTLERS);
            Npc trader = resident(game, village, "Mira", NpcArchetype.TRADER, 311.5f, 40.001f, 310.5f);
            trader.isTrader = true;
            assertEquals(Game.NpcInteraction.TALK, game.npcInteraction(trader), "R12: talking stays available");
            game.player.inventory.add(ItemType.BERRY, 2);
            assertTrue(game.npcScreen.performTrade(game, trader, ItemType.BERRY, 2, ItemType.PLANK, 1),
                    "R12: trading still works");
            game.player.hotbarSel = 0;
            game.player.inventory.set(0, new ItemStack(ItemType.COOKED_MEAT, 1));
            assertTrue(game.npcScreen.performGift(game, trader), "R12: gifts still work");
            if (mode == GameMode.CREATIVE) {
                creativeReputation = village.localReputation;
            } else {
                survivalReputation = village.localReputation;
            }
        }
        assertTrue(survivalReputation > 0f, "Fixture: friendly actions earn reputation");
        assertEquals(survivalReputation, creativeReputation, 0.0001f,
                "R12: friendly interactions earn identical reputation");
    }

    @Test
    void bountyHuntersCannotPickUpTheTrailOfACreativePlayer() {
        for (GameMode mode : GameMode.values()) {
            Game game = CreativeTestArena.create(mode);
            long id = Settlement.packId(710, 700);
            Settlement lair = new Settlement(id, 710, 700, SettlementType.FORT, new Vec3i(510, 40, 310),
                    HumanFaction.HEADHUNTERS, Settlement.Alignment.HOSTILE);
            game.world.settlements.put(id, lair);
            game.world.factionBounty.put(HumanFaction.HEADHUNTERS, 130f);
            game.settlementManager.slowTick(game, 130f);
            long hunters = game.entities.npcs.stream()
                    .filter(n -> n.partyKind == Npc.PartyKind.BOUNTY_HUNTER).count();
            if (mode == GameMode.CREATIVE) {
                assertEquals(0, hunters, "R11: no hunting party is sent after a Creative player");
                assertEquals(130f, game.world.factionBounty.get(HumanFaction.HEADHUNTERS),
                        "D3: the bounty itself is kept");
            } else {
                assertTrue(hunters > 0, "Survival bounty still dispatches hunters");
            }
        }
    }

    @Test
    void playerEventsAndTracesAreNotProducedWhileUnrelatedEventsStayAudible() {
        for (GameMode mode : GameMode.values()) {
            Game game = CreativeTestArena.create(mode);
            boolean creative = mode == GameMode.CREATIVE;
            Creature deer = game.entities.spawnCreature(game.world, Creature.CreatureType.DEER,
                    311.5f, 40.001f, 310.5f);
            assertEquals(!creative, game.emitPlayerFootstepNoise(true, false),
                    "R11: the footstep seam reports whether an event was queued");
            assertTrue(game.performPlayerAttack(deer), "Fixture: the swing lands");
            game.player.inventory.add(ItemType.RAW_MEAT, 5);
            game.player.tickNeeds(game, 0.05f);
            if (creative) {
                assertEquals(0, game.noise.count(), "R11: no player-attributed event is queued");
                assertEquals(0f, game.player.noise, "R11: the player's own noise stays silent");
                assertEquals(0f, game.player.scent, "R11: carried meat leaves no scent");
            } else {
                assertEquals(2, game.noise.count(), "Survival queues the sprint and melee events");
                assertTrue(game.player.noise > 0f && game.player.scent > 0f, "Survival traces remain");
            }
            assertTrue(game.noise.emit(game, 320f, 40f, 310f, 30f, 0.7f, "alarm-shout", false, null),
                    "R12: events without a player source are unchanged");
        }
    }

    @Test
    void proximitySimulationStillDiscoversSettlementsAroundACreativePlayer() {
        Game game = CreativeTestArena.create(GameMode.CREATIVE);
        Settlement ruin = settlement(game, Settlement.Alignment.NEUTRAL, HumanFaction.FREE_SETTLERS);
        ruin.cleared = true; // keeps resident activation out of this fixture
        game.settlementManager.slowTick(game, 10f);
        assertTrue(ruin.discovered, "R12: discovery is proximity simulation and stays active");
    }

    private static Settlement settlement(Game game, Settlement.Alignment alignment, String faction) {
        long id = Settlement.packId(700, 700);
        Settlement settlement = new Settlement(id, 700, 700,
                alignment == Settlement.Alignment.HOSTILE ? SettlementType.FORT : SettlementType.VILLAGE,
                new Vec3i(320, 40, 310), faction, alignment);
        game.world.settlements.put(id, settlement);
        return settlement;
    }

    private static Npc resident(Game game, Settlement settlement, String name, NpcArchetype archetype,
                                float x, float y, float z) {
        Settlement.Resident record = new Settlement.Resident(name, archetype);
        settlement.residents.add(record);
        Npc npc = game.entities.spawnNpc(game.world, name, x, y, z);
        npc.archetype = archetype;
        npc.settlementId = settlement.id;
        npc.residentIndex = settlement.residents.size() - 1;
        npc.maxHealth = archetype.maxHealth;
        npc.health = archetype.maxHealth;
        record.live = npc;
        return npc;
    }

    static final class WildlifeAudio extends AudioManager {
        int flaps;

        @Override
        public void playBirdFlap(float x, float y, float z) {
            flaps++;
        }
    }
}
