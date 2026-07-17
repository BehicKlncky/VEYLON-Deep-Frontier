package com.veylon.ai;

import com.veylon.Game;
import com.veylon.entity.Npc;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.save.SaveSystem;
import com.veylon.settlement.HumanFaction;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementBuilder;
import com.veylon.settlement.SettlementType;
import com.veylon.ui.NpcScreen;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Poi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Integration events intentionally pass through production discovery and AI paths. */
class QuestGameplayEventIntegrationTest {

    @Test
    void providerScoutRequestCompletesOnlyWhenTheMarkedFortIsDiscoveredInWorldTick() {
        Context context = generatedContext(20260716L);
        context.game.faction.makeQuestOfferAvailable();
        Quest quest = context.game.faction.offerSettlementQuestOfType(context.game,
                context.provider, context.home, Quest.Type.SCOUT_HOSTILE_FORT);
        assertNotNull(quest);
        Settlement target = context.game.world.settlements.get(quest.targetSettlementId);
        assertNotNull(target);
        assertFalse(target.discovered);

        // Visiting a different hostile fort cannot progress this target.
        Settlement other = context.game.world.settlements.values().stream()
                .filter(s -> s.id != target.id && s.hostile()
                        && (s.type == SettlementType.FORT || s.type == SettlementType.CASTLE
                        || s.type == SettlementType.FORTRESS))
                .findFirst().orElse(null);
        if (other != null) {
            context.game.player.pos.set(other.center.x(), other.center.y() + 1,
                    other.center.z());
            context.game.settlementManager.slowTick(context.game, 0.1f);
            assertEquals(0, quest.progress);
        }

        context.game.player.pos.set(target.center.x(), target.center.y() + 1, target.center.z());
        context.game.settlementManager.slowTick(context.game, 0.1f);
        assertTrue(target.discovered);
        assertEquals(Quest.Status.READY_TO_TURN_IN, quest.status);
        NpcScreen dialogue = new NpcScreen();
        dialogue.performSettlementQuestAction(context.game, context.provider, context.home);
        assertNull(context.game.faction.quest);
    }

    @Test
    void escortTraderActuallyTravelsBehindPlayerToItsBoundDestination() {
        Context context = generatedContext(42L);
        // A nearby authorized destination keeps this integration fast while
        // retaining normal trader-follow AI and target identity.
        Settlement destination = new Settlement(0x5eed5eedL, 70, 70,
                SettlementType.VILLAGE,
                new Vec3i(context.home.center.x() + 42, context.home.center.y(),
                        context.home.center.z()),
                HumanFaction.FREE_SETTLERS, Settlement.Alignment.FRIENDLY);
        context.game.world.settlements.put(destination.id, destination);
        context.game.faction.makeQuestOfferAvailable();
        Quest quest = context.game.faction.offerSettlementQuestOfType(context.game,
                context.provider, context.home, Quest.Type.ESCORT_TRADER);
        assertNotNull(quest);
        assertEquals(destination.id, quest.targetSettlementId);
        Npc escort = context.game.entities.npcs.stream()
                .filter(n -> quest.targetMissionId.equals(n.partyMissionId))
                .findFirst().orElseThrow();
        float startX = escort.pos.x;
        float startZ = escort.pos.z;

        for (int i = 0; i < 1_200 && !quest.readyToTurnIn(); i++) {
            float dx = destination.center.x() + 0.5f - escort.pos.x;
            float dz = destination.center.z() + 0.5f - escort.pos.z;
            float len = Math.max(0.001f, (float) Math.sqrt(dx * dx + dz * dz));
            context.game.player.pos.set(escort.pos.x + dx / len * 11f,
                    escort.pos.y, escort.pos.z + dz / len * 11f);
            NpcAI.update(context.game, escort, 0.05f);
            escort.applyPhysics(0.05f, true);
        }
        double travelled = Math.hypot(escort.pos.x - startX, escort.pos.z - startZ);
        assertTrue(travelled > 5, "the trader traverses the route instead of teleporting");
        assertEquals(Quest.Status.READY_TO_TURN_IN, quest.status);
        assertEquals(destination.id, escort.partyTargetSettlementId);
    }

    @Test
    void caveRequestCompletesThroughNormalPoiDiscoveryForTheProviderRegionOnly() {
        Context context = generatedContext(987654321L);
        context.game.faction.makeQuestOfferAvailable();
        Quest quest = context.game.faction.offerSettlementQuestOfType(context.game,
                context.provider, context.home, Quest.Type.EXPLORE_SETTLEMENT_CAVE);
        assertNotNull(quest);
        Poi wrong = new Poi(Poi.PoiType.ABANDONED_MINE,
                new Vec3i(context.home.center.x() + 500, 8, context.home.center.z() + 500));
        Poi matching = new Poi(Poi.PoiType.ABANDONED_MINE,
                new Vec3i(context.home.center.x(), 8, context.home.center.z()));
        context.game.world.pois.add(wrong);
        context.game.world.pois.add(matching);

        context.game.player.pos.set(wrong.pos.x(), wrong.pos.y(), wrong.pos.z());
        context.game.mediumTick(0.1f);
        assertEquals(0, quest.progress);
        context.game.player.pos.set(matching.pos.x(), matching.pos.y(), matching.pos.z());
        context.game.mediumTick(0.1f);
        assertTrue(matching.discovered);
        assertEquals(Quest.Status.READY_TO_TURN_IN, quest.status);
    }

    @Test
    void villageDefenseAndPatrolClearUsePartiesThatActuallyTravelThenDieToPlayerMelee() {
        for (Quest.Type type : new Quest.Type[]{
                Quest.Type.DEFEND_VILLAGE, Quest.Type.CLEAR_PATROL}) {
            Context context = generatedContext(73_000L + type.ordinal());
            context.game.player.health = 10_000f;
            context.game.faction.makeQuestOfferAvailable();
            Quest quest = context.game.faction.offerSettlementQuestOfType(context.game,
                    context.provider, context.home, type);
            assertNotNull(quest);
            assertFalse(quest.targetMissionId.startsWith("pending:"));
            var party = context.game.entities.npcs.stream()
                    .filter(n -> quest.targetMissionId.equals(n.partyMissionId))
                    .toList();
            assertEquals(quest.required, party.size());
            double initialDistance = party.getFirst().distSqTo(
                    context.home.center.x(), context.home.center.y(), context.home.center.z());

            // Keep the party abstract and let production party AI traverse the full route.
            // Mixed archetypes have different movement speeds, so intercept each
            // named member as it reaches the destination instead of fabricating a
            // synchronized group phase or teleporting stragglers.
            context.game.player.pos.set(50_000, 50, 50_000);
            context.game.player.inventory.set(0, new ItemStack(ItemType.IRON_SPEAR, 1));
            context.game.player.hotbarSel = 0;
            Set<Npc> reached = Collections.newSetFromMap(new IdentityHashMap<>());
            for (int tick = 0; tick < 5_000 && reached.size() < party.size(); tick++) {
                context.game.entities.fastTick(context.game, 1f);
                for (Npc attacker : party) {
                    if (!attacker.dead && !reached.contains(attacker)
                            && attacker.partyMission == Npc.PartyMission.SEARCHING) {
                        double dx = attacker.pos.x - context.home.center.x() - 0.5;
                        double dz = attacker.pos.z - context.home.center.z() - 0.5;
                        assertTrue(dx * dx + dz * dz <= 9 * 9,
                                "the mission member reached the village under travel AI; member="
                                        + attacker.partyMemberId + ", pos=" + attacker.pos
                                        + ", target=" + context.home.center
                                        + ", distance=" + Math.sqrt(dx * dx + dz * dz)
                                        + ", contact=" + attacker.partyContact);
                        reached.add(attacker);
                        defeatWithMeleeCommand(context.game, attacker);
                        context.game.player.pos.set(50_000, 50, 50_000);
                    }
                }
            }
            assertEquals(party.size(), reached.size(),
                    type + " brings every mission member to the destination without teleportation; "
                            + party.stream().map(attacker -> attacker.partyMemberId
                                    + "{dead=" + attacker.dead
                                    + ",phase=" + attacker.partyMission
                                    + ",pos=" + attacker.pos + "}")
                            .toList());
            assertTrue(party.getFirst().distSqTo(context.home.center.x(), context.home.center.y(),
                    context.home.center.z()) < initialDistance);
            assertEquals(Quest.Status.READY_TO_TURN_IN, quest.status,
                    type + " advances from the deaths of its actual mission members");
            String result = new NpcScreen().performSettlementQuestAction(
                    context.game, context.provider, context.home);
            assertTrue(result.contains("thanks"));
            assertNull(context.game.faction.quest);
        }
    }

    @Test
    void sabotageRequestCompletesOnlyWhenThePlayerMinesItsGeneratedAlarm() {
        Context context = generatedContext(-1_234_567L);
        context.game.faction.makeQuestOfferAvailable();
        Quest quest = context.game.faction.offerSettlementQuestOfType(context.game,
                context.provider, context.home, Quest.Type.SABOTAGE_ALARM);
        assertNotNull(quest);
        Settlement target = context.game.world.settlements.get(quest.targetSettlementId);
        assertNotNull(target);
        context.game.world.layoutFor(target);
        assertNotNull(target.alarmBell);
        context.game.world.ensureChunks(target.center.x(), target.center.z(), 5, 10_000);
        assertEquals(BlockType.ALARM_BELL, context.game.world.getBlock(
                target.alarmBell.x(), target.alarmBell.y(), target.alarmBell.z()));

        assertTrue(context.game.completePlayerBlockBreak(target.alarmBell));
        assertTrue(target.alarmNeutralized);
        assertEquals(Quest.Status.READY_TO_TURN_IN, quest.status);
        assertTrue(new NpcScreen().performSettlementQuestAction(
                context.game, context.provider, context.home).contains("thanks"));
    }

    @Test
    void recoveryRequestSeedsOneExactHostileCrateAndUsesTheRealTransferAfterSaveLoad(
            @TempDir Path dir) {
        Context context = generatedContext(999L);
        context.game.faction.makeQuestOfferAvailable();
        Quest quest = context.game.faction.offerSettlementQuestOfType(context.game,
                context.provider, context.home, Quest.Type.RECOVER_STOLEN_SUPPLIES);
        assertNotNull(quest);
        Settlement target = context.game.world.settlements.get(quest.targetSettlementId);
        assertNotNull(target);
        SettlementBuilder.Layout layout = context.game.world.layoutFor(target);
        Vec3i storage = layout.crates.keySet().stream()
                .filter(pos -> quest.targetPoiId.equals(
                        FactionSystem.settlementStorageId(target.id, pos)))
                .findFirst().orElseThrow();
        var staged = context.game.world.crateContents.get(storage);
        assertNotNull(staged, "normal acquisition creates the persistent target inventory");
        assertTrue(staged.count(quest.item) >= quest.required,
                "normal acquisition stages the marked supplies without test injection");

        Vec3i unrelated = layout.crates.keySet().stream()
                .filter(pos -> !pos.equals(storage))
                .findFirst().orElse(null);
        if (unrelated != null) {
            context.game.faction.onStolenSuppliesRecovered(context.game, target,
                    FactionSystem.settlementStorageId(target.id, unrelated),
                    quest.item, quest.required);
            assertEquals(0, quest.progress,
                    "a different crate in the same settlement cannot satisfy the exact target");
        }

        Path save = dir.resolve("staged-recovery.sav");
        assertTrue(SaveSystem.save(context.game, save));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save));
        Quest restoredQuest = loaded.faction.quest;
        assertNotNull(restoredQuest);
        assertEquals(quest.targetPoiId, restoredQuest.targetPoiId);
        Settlement restoredTarget = loaded.world.settlements.get(target.id);
        assertNotNull(restoredTarget);
        loaded.world.layoutFor(restoredTarget);
        loaded.world.ensureChunks(restoredTarget.center.x(), restoredTarget.center.z(),
                5, 10_000);
        assertEquals(BlockType.CRATE,
                loaded.world.getBlock(storage.x(), storage.y(), storage.z()));
        var restoredInventory = loaded.world.crateContents.get(storage);
        assertNotNull(restoredInventory);
        assertTrue(restoredInventory.count(restoredQuest.item) >= restoredQuest.required,
                "the staged objective and exact crate survive save/load");
        int medicineSlot = -1;
        for (int i = 0; i < restoredInventory.size(); i++) {
            if (restoredInventory.get(i) != null
                    && restoredInventory.get(i).type == restoredQuest.item) {
                medicineSlot = i;
                break;
            }
        }
        assertTrue(medicineSlot >= 0);

        assertTrue(loaded.openCrateAt(storage));
        assertEquals(restoredQuest.required,
                loaded.transferCrateItemToPlayer(medicineSlot));
        assertEquals(Quest.Status.READY_TO_TURN_IN, restoredQuest.status,
                "the crate UI transfer carries storage, settlement, item, and count identity");
        Settlement restoredHome = loaded.world.settlements.get(restoredQuest.giverSettlementId);
        Npc restoredProvider = provider(loaded, restoredHome, "Regional quartermaster");
        assertTrue(new NpcScreen().performSettlementQuestAction(
                loaded, restoredProvider, restoredHome).contains("thanks"));
        assertNull(loaded.faction.quest);
    }

    @Test
    void expiringRecoveryRemovesOnlyItsStillStagedSupplies() {
        Context context = generatedContext(999L);
        context.game.faction.makeQuestOfferAvailable();
        Quest quest = context.game.faction.offerSettlementQuestOfType(context.game,
                context.provider, context.home, Quest.Type.RECOVER_STOLEN_SUPPLIES);
        assertNotNull(quest);
        Settlement target = context.game.world.settlements.get(quest.targetSettlementId);
        SettlementBuilder.Layout layout = context.game.world.layoutFor(target);
        Vec3i storage = layout.crates.keySet().stream()
                .filter(pos -> quest.targetPoiId.equals(
                        FactionSystem.settlementStorageId(target.id, pos)))
                .findFirst().orElseThrow();
        var targetInventory = context.game.world.crateContents.get(storage);
        assertNotNull(targetInventory);
        int stagedBefore = targetInventory.count(quest.item);
        assertTrue(stagedBefore >= quest.required);

        Vec3i unrelated = context.game.world.crateContents.keySet().stream()
                .filter(pos -> !pos.equals(storage)).findFirst().orElseThrow();
        var unrelatedInventory = context.game.world.crateContents.get(unrelated);
        int unrelatedMedicine = unrelatedInventory.count(quest.item);

        context.game.faction.slowTick(context.game, quest.timeLeft + 1f);

        assertEquals(Quest.Status.EXPIRED, quest.status);
        assertEquals(stagedBefore - quest.required, targetInventory.count(quest.item),
                "expiration removes only the still-staged objective quantity");
        assertEquals(unrelatedMedicine, unrelatedInventory.count(quest.item),
                "cleanup never edits another crate");
    }

    @Test
    void escortedTraderDeathFailsTheExactMissionAndFailurePersistsWithoutReward(
            @TempDir Path dir) {
        Context context = generatedContext(1L);
        context.game.faction.makeQuestOfferAvailable();
        Quest quest = context.game.faction.offerSettlementQuestOfType(context.game,
                context.provider, context.home, Quest.Type.ESCORT_TRADER);
        assertNotNull(quest);
        Npc escort = context.game.entities.npcs.stream()
                .filter(n -> quest.targetMissionId.equals(n.partyMissionId))
                .findFirst().orElseThrow();
        context.game.player.inventory.set(0, new ItemStack(ItemType.IRON_SPEAR, 1));
        context.game.player.hotbarSel = 0;
        defeatWithMeleeCommand(context.game, escort);
        assertEquals(Quest.Status.FAILED, quest.status);
        assertTrue(quest.failureReason.contains("lost"));

        Path save = dir.resolve("failed-escort.sav");
        assertTrue(SaveSystem.save(context.game, save));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save));
        Quest restored = loaded.faction.quest;
        assertNotNull(restored);
        assertEquals(quest.instanceId, restored.instanceId);
        assertEquals(quest.targetMissionId, restored.targetMissionId);
        assertEquals(Quest.Status.FAILED, restored.status);
        int rewardBefore = loaded.player.inventory.count(restored.rewardItem);
        Settlement providerHome = loaded.world.settlements.get(restored.giverSettlementId);
        Npc provider = provider(loaded, providerHome, "Regional quartermaster");
        assertTrue(new NpcScreen().performSettlementQuestAction(
                loaded, provider, providerHome).contains("closed"));
        assertNull(loaded.faction.quest);
        assertEquals(rewardBefore, loaded.player.inventory.count(restored.rewardItem));
    }

    @Test
    void expiringVillageDefenseRemovesQuestOwnedAttackersAndPersistsClosedState(
            @TempDir Path dir) {
        Context context = generatedContext(4242L);
        context.game.faction.makeQuestOfferAvailable();
        Quest quest = context.game.faction.offerSettlementQuestOfType(context.game,
                context.provider, context.home, Quest.Type.DEFEND_VILLAGE);
        assertNotNull(quest);
        String mission = quest.targetMissionId;
        assertTrue(context.game.entities.npcs.stream()
                .anyMatch(n -> mission.equals(n.partyMissionId)));

        context.game.faction.slowTick(context.game, quest.timeLeft + 1f);
        assertEquals(Quest.Status.EXPIRED, quest.status);
        assertTrue(context.game.entities.npcs.stream()
                .noneMatch(n -> mission.equals(n.partyMissionId)),
                "temporary mission actors release their NPC budget on expiration");

        Path save = dir.resolve("expired-defense.sav");
        assertTrue(SaveSystem.save(context.game, save));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save));
        assertEquals(Quest.Status.EXPIRED, loaded.faction.quest.status);
        assertEquals(mission, loaded.faction.quest.targetMissionId);
        assertTrue(loaded.entities.npcs.stream()
                .noneMatch(n -> mission.equals(n.partyMissionId)));
    }

    private static void defeatWithMeleeCommand(Game game, Npc npc) {
        int swings = 0;
        while (!npc.dead) {
            game.player.pos.set(npc.pos.x - 1f, npc.pos.y, npc.pos.z);
            game.advancePlayerAttackCooldown(0.5f);
            assertTrue(game.performPlayerAttack(npc));
            assertTrue(++swings < 20, "melee mission combat stays bounded");
        }
        game.entities.fastTick(game, 0f);
        assertFalse(game.entities.npcs.contains(npc));
    }

    private static Npc provider(Game game, Settlement home, String name) {
        Npc provider = new Npc(game.world, name);
        provider.archetype = NpcArchetype.TRADER;
        provider.isTrader = true;
        provider.settlementId = home.id;
        provider.residentIndex = 0;
        return provider;
    }

    private static Context generatedContext(long seed) {
        Game game = new Game();
        game.newWorld(seed, true);
        Settlement home = null;
        for (int rx = -12; rx <= 12; rx++) {
            for (int rz = -12; rz <= 12; rz++) {
                Settlement settlement = game.world.settlementForRegion(rx, rz);
                if (settlement != null && !settlement.hostile() && home == null) {
                    home = settlement;
                }
            }
        }
        assertNotNull(home);
        Npc provider = new Npc(game.world, "Regional quartermaster");
        provider.archetype = NpcArchetype.TRADER;
        provider.isTrader = true;
        provider.settlementId = home.id;
        provider.residentIndex = 0;
        return new Context(game, home, provider);
    }

    private record Context(Game game, Settlement home, Npc provider) {
    }
}
