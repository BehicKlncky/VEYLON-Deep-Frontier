package com.veylon.settlement;

import com.veylon.Game;
import com.veylon.ai.SettledNpcAI;
import com.veylon.ai.Quest;
import com.veylon.entity.Npc;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.save.SaveSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptiveRescueGameplayTest {

    private static final long SEED = 424242L;

    @Test
    void generatedCageBlocksNormalRescueUntilPlayerBreaksBarsAndTheResultPersists(
            @TempDir Path dir) {
        Game initial = new Game();
        initial.newWorld(SEED, true);
        Settlement offerHome = firstFriendlySettlement(initial);
        assertNotNull(offerHome, "expected a generated settlement quest provider");
        Npc provider = new Npc(initial.world, "Rescue coordinator");
        provider.archetype = NpcArchetype.TRADER;
        provider.isTrader = true;
        provider.settlementId = offerHome.id;
        provider.residentIndex = 0;
        initial.faction.makeQuestOfferAvailable();
        Quest rescueQuest = initial.faction.offerSettlementQuestOfType(initial, provider,
                offerHome, Quest.Type.RESCUE_CAPTIVE);
        assertNotNull(rescueQuest, "normal provider dialogue acquires a concrete rescue target");
        Settlement source = initial.world.settlements.get(rescueQuest.targetSettlementId);
        assertNotNull(source, "expected a generated hostile fort with a captive");
        initial.world.layoutFor(source);
        assertNotNull(source.prisonPos, "the captive settlement needs a generated prison");
        initial.world.ensureChunks(source.center.x(), source.center.z(), 5, 10_000);

        Vec3i upperWestBar = upperWestBar(source);
        initial.player.pos.set(upperWestBar.x() - 0.6f, source.prisonPos.y(),
                source.prisonPos.z() + 0.5f);
        initial.settlementManager.slowTick(initial, 1f);

        int captiveIndex = captiveIndex(source);
        Settlement.Resident captiveRecord = source.residents.get(captiveIndex);
        Npc captive = captiveRecord.live;
        assertNotNull(captive, "approaching the prison materializes its captive");
        assertEquals(BlockType.CAGE_BARS, initial.world.getBlock(
                upperWestBar.x(), upperWestBar.y(), upperWestBar.z()));

        assertFalse(captive.hostileToPlayer(), "captivity does not inherit captor hostility");
        assertFalse(captive.combatant(), "a captive is a protected non-combatant");
        assertFalse(captive.countsAsSettlementDefender());
        assertFalse(source.countsAsDefender(captiveRecord));
        assertFalse(initial.canOpenNpcInteraction(captive),
                "captives never enter the normal talk/trade screen");

        Npc guard = source.residents.stream().map(r -> r.live)
                .filter(n -> n != null && n != captive && n.combatant())
                .findFirst().orElseThrow();
        assertFalse(captive.alliedWith(guard), "captors do not own the captive's combat side");
        assertFalse(guard.alliedWith(captive));

        float playerHealth = initial.player.health;
        captive.lastKnown.set(initial.player.pos);
        captive.lastKnownAge = 0;
        for (int i = 0; i < 20; i++) {
            SettledNpcAI.update(initial, captive, 0.05f);
        }
        assertEquals(playerHealth, initial.player.health, 0.001f,
                "the captive AI never attacks the player");

        float frontierBefore = reputation(initial, HumanFaction.FRONTIER);
        float settlersBefore = reputation(initial, HumanFaction.FREE_SETTLERS);
        assertEquals(Game.NpcInteraction.RESCUE_BLOCKED, initial.npcInteraction(captive));
        initial.updatePrompt();
        assertTrue(initial.interactPrompt.contains("Break the cage bars"));
        assertTrue(initial.interactWithNearbyNpc(), "F consumes the blocked rescue interaction");
        assertFalse(captiveRecord.rescued);
        assertEquals(frontierBefore, reputation(initial, HumanFaction.FRONTIER), 0.001f);
        assertEquals(settlersBefore, reputation(initial, HumanFaction.FREE_SETTLERS), 0.001f);

        Path beforeSave = dir.resolve("captive-before-rescue.sav");
        assertTrue(SaveSystem.save(initial, beforeSave));
        Game game = new Game();
        assertTrue(SaveSystem.load(game, beforeSave));
        assertNotNull(game.faction.quest);
        assertEquals(rescueQuest.instanceId, game.faction.quest.instanceId);
        assertEquals(source.id, game.faction.quest.targetSettlementId,
                "the captive target survives save/load before rescue");

        Settlement restoredSource = game.world.settlements.get(source.id);
        assertNotNull(restoredSource);
        game.world.layoutFor(restoredSource);
        Settlement.Resident restoredRecord = restoredSource.residents.get(captiveIndex);
        Npc restoredCaptive = restoredRecord.live;
        assertNotNull(restoredCaptive, "an unrescued active captive rebinds after load");
        Vec3i restoredBar = upperWestBar(restoredSource);
        game.world.getOrCreateChunk(Math.floorDiv(restoredBar.x(), 16),
                Math.floorDiv(restoredBar.z(), 16));
        assertEquals(BlockType.CAGE_BARS, game.world.getBlock(
                restoredBar.x(), restoredBar.y(), restoredBar.z()));
        assertEquals(Game.NpcInteraction.RESCUE_BLOCKED, game.npcInteraction(restoredCaptive));

        Settlement destination = nearestFriendlyDestination(game, restoredSource);
        assertNotNull(destination, "rescued settlers need a friendly destination");
        assertTrue(destination.aliveResidents() < destination.type.maxPopulation,
                "fixture destination must have capacity for the population assertion");
        int destinationResidentsBefore = destination.residents.size();
        int sourcePopulationBefore = restoredSource.aliveResidents();

        game.player.inventory.set(0, new ItemStack(ItemType.IRON_PICKAXE, 1));
        game.player.hotbarSel = 0;
        assertTrue(game.completePlayerBlockBreak(restoredBar),
                "the normal mining outcome breaks the generated cage bar");
        assertEquals(BlockType.AIR, game.world.getBlock(
                restoredBar.x(), restoredBar.y(), restoredBar.z()));
        assertTrue(game.world.changedBlocks.containsKey(restoredBar));
        assertEquals(Game.NpcInteraction.RESCUE_READY, game.npcInteraction(restoredCaptive));
        game.updatePrompt();
        assertEquals("[F] Rescue " + restoredCaptive.name, game.interactPrompt);

        assertTrue(game.interactWithNearbyNpc(), "the real F-command frees the reachable captive");
        assertTrue(restoredRecord.rescued);
        assertFalse(game.entities.npcs.contains(restoredCaptive));
        assertEquals(sourcePopulationBefore - 1, restoredSource.aliveResidents());
        assertEquals(destinationResidentsBefore + 1, destination.residents.size(),
                "the rescued settler joins one friendly population");
        assertEquals(Game.UiMode.NONE, game.uiMode, "rescue does not open normal NPC UI");
        assertNull(game.activeNpc);
        assertEquals(Quest.Status.READY_TO_TURN_IN, game.faction.quest.status,
                "the real F-key rescue completes its exact targeted quest");

        float frontierAfter = reputation(game, HumanFaction.FRONTIER);
        float settlersAfter = reputation(game, HumanFaction.FREE_SETTLERS);
        int destinationResidentsAfter = destination.residents.size();
        assertTrue(frontierAfter > frontierBefore);
        assertTrue(settlersAfter > settlersBefore);

        assertFalse(game.settlementManager.rescueCaptive(game, restoredCaptive),
                "a stale repeated command cannot replay the transaction");
        assertFalse(game.interactWithNearbyNpc(),
                "repeating F after the captive leaves finds no rescue action");
        assertEquals(frontierAfter, reputation(game, HumanFaction.FRONTIER), 0.001f);
        assertEquals(settlersAfter, reputation(game, HumanFaction.FREE_SETTLERS), 0.001f);
        assertEquals(destinationResidentsAfter, destination.residents.size());

        Settlement loadedProvider = game.world.settlements.get(rescueQuest.giverSettlementId);
        int medicineRewardBefore = game.player.inventory.count(ItemType.MEDICINE);
        assertTrue(game.faction.turnInSettlementQuest(game, loadedProvider).contains("thanks"));
        assertNull(game.faction.quest);
        assertEquals(medicineRewardBefore + rescueQuest.rewardCount,
                game.player.inventory.count(ItemType.MEDICINE));
        float frontierAfterTurnIn = reputation(game, HumanFaction.FRONTIER);
        float settlersAfterTurnIn = reputation(game, HumanFaction.FREE_SETTLERS);
        assertTrue(frontierAfterTurnIn >= frontierAfter,
                "quest turn-in may add its separate provider/faction reward");
        assertTrue(settlersAfterTurnIn >= settlersAfter);

        Path afterSave = dir.resolve("captive-after-rescue.sav");
        assertTrue(SaveSystem.save(game, afterSave));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, afterSave));
        Settlement loadedSource = loaded.world.settlements.get(restoredSource.id);
        Settlement loadedDestination = loaded.world.settlements.get(destination.id);
        assertTrue(loadedSource.residents.get(captiveIndex).rescued);
        assertNull(loadedSource.residents.get(captiveIndex).live);
        assertTrue(loaded.entities.npcs.stream().noneMatch(n -> n.settlementId == loadedSource.id
                && n.residentIndex == captiveIndex));
        assertEquals(destinationResidentsAfter, loadedDestination.residents.size());
        assertEquals(frontierAfterTurnIn, reputation(loaded, HumanFaction.FRONTIER), 0.001f);
        assertEquals(settlersAfterTurnIn, reputation(loaded, HumanFaction.FREE_SETTLERS), 0.001f);
        assertEquals(BlockType.AIR, loaded.world.getBlock(
                restoredBar.x(), restoredBar.y(), restoredBar.z()),
                "the broken cage bar remains a changed block after load");
    }

    private static Settlement findHostileCaptiveSettlement(Game game) {
        game.world.settlementForRegion(0, 0); // guarantee a planned friendly destination
        for (int rx = -10; rx <= 10; rx++) {
            for (int rz = -10; rz <= 10; rz++) {
                Settlement settlement = game.world.settlementForRegion(rx, rz);
                if (settlement != null && settlement.hostile()
                        && settlement.residents.stream()
                        .anyMatch(r -> r.archetype == NpcArchetype.CAPTIVE)) {
                    return settlement;
                }
            }
        }
        return null;
    }

    private static Settlement firstFriendlySettlement(Game game) {
        // Plan enough deterministic regions to expose both a provider and its
        // nearest captive target without generating their chunks.
        findHostileCaptiveSettlement(game);
        for (Settlement settlement : game.world.settlements.values()) {
            if (settlement.friendly() && !settlement.cleared) {
                return settlement;
            }
        }
        return null;
    }

    private static int captiveIndex(Settlement settlement) {
        for (int i = 0; i < settlement.residents.size(); i++) {
            if (settlement.residents.get(i).archetype == NpcArchetype.CAPTIVE) {
                return i;
            }
        }
        return -1;
    }

    private static Vec3i upperWestBar(Settlement settlement) {
        return new Vec3i(settlement.prisonPos.x() - 1, settlement.prisonPos.y() + 1,
                settlement.prisonPos.z());
    }

    private static Settlement nearestFriendlyDestination(Game game, Settlement source) {
        Settlement best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Settlement candidate : game.world.settlements.values()) {
            if (!candidate.friendly() || candidate.cleared) {
                continue;
            }
            double distance = candidate.distSqTo(source.center.x(), source.center.z());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private static float reputation(Game game, String factionId) {
        return game.world.factionReputation.getOrDefault(factionId, 0f);
    }
}
