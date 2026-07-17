package com.veylon.ui;

import com.veylon.Game;
import com.veylon.ai.FactionSystem;
import com.veylon.ai.Quest;
import com.veylon.entity.Npc;
import com.veylon.save.SaveSystem;
import com.veylon.settlement.HumanFaction;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementType;
import com.veylon.util.Vec3i;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapScreenTest {

    @Test
    void rumorDoesNotLeakTierAlignmentOrServicesBeforeDiscovery() {
        Settlement s = new Settlement(1, 1, 1, SettlementType.FORTRESS,
                new Vec3i(100, 40, 100), HumanFaction.HEADHUNTERS,
                Settlement.Alignment.HOSTILE);
        s.rumored = true;
        s.residents.add(new Settlement.Resident("Quartermaster", NpcArchetype.TRADER));

        assertEquals("?", MapScreen.markerTag(s));
        assertEquals("", MapScreen.serviceTag(s));

        s.discovered = true;
        s.alignment = Settlement.Alignment.FRIENDLY;
        s.beds.add(s.center);
        assertTrue(MapScreen.markerTag(s).startsWith("X "));
        assertEquals("$Z", MapScreen.serviceTag(s));
    }

    @Test
    void rescueObjectiveUsesExactStableTargetThenSwitchesToItsProviderAndClears(
            @TempDir Path dir) {
        Game game = targetedRescueGame();
        Quest quest = game.faction.quest;
        Settlement target = game.world.settlements.get(quest.targetSettlementId);
        Settlement provider = game.world.settlements.get(quest.giverSettlementId);

        QuestObjectiveView.Objective active = QuestObjectiveView.resolve(game);
        assertNotNull(active);
        assertEquals(QuestObjectiveView.Phase.TARGET, active.phase());
        assertEquals(FactionSystem.settlementProviderId(target.id), active.targetId());
        assertEquals(target.center, active.position());
        assertTrue(target.rumored, "acceptance records only a generic target rumor");
        assertFalse(target.discovered);
        assertEquals("?", MapScreen.markerTag(target));
        assertEquals("", MapScreen.serviceTag(target));
        assertTrue(QuestObjectiveView.navigationLabel(game).startsWith("Marked objective - "));

        Path save = dir.resolve("quest-map-target.sav");
        assertTrue(SaveSystem.save(game, save));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save));
        QuestObjectiveView.Objective restored = QuestObjectiveView.resolve(loaded);
        assertNotNull(restored);
        assertEquals(active.questId(), restored.questId());
        assertEquals(active.targetId(), restored.targetId());
        assertEquals(active.position(), restored.position());
        assertTrue(loaded.world.settlements.get(target.id).rumored);

        Quest loadedQuest = loaded.faction.quest;
        Settlement loadedTarget = loaded.world.settlements.get(target.id);
        int captiveIndex = captiveIndex(loadedTarget, loadedQuest.targetCaptiveId);
        loaded.faction.onCaptiveRescued(loaded, loadedTarget, captiveIndex);
        assertEquals(Quest.Status.READY_TO_TURN_IN, loadedQuest.status);

        QuestObjectiveView.Objective turnIn = QuestObjectiveView.resolve(loaded);
        assertNotNull(turnIn);
        assertEquals(QuestObjectiveView.Phase.RETURN_TO_PROVIDER, turnIn.phase());
        assertEquals(FactionSystem.settlementProviderId(provider.id), turnIn.targetId());
        assertEquals(provider.center, turnIn.position());
        assertTrue(QuestObjectiveView.navigationLabel(loaded)
                .startsWith("Return to request provider - "));

        Settlement loadedProvider = loaded.world.settlements.get(provider.id);
        assertTrue(loaded.faction.turnInSettlementQuest(loaded, loadedProvider).contains("thanks"));
        assertNull(loaded.faction.quest);
        assertNull(QuestObjectiveView.resolve(loaded));
        assertEquals("", QuestObjectiveView.navigationLabel(loaded));
    }

    @Test
    void failedOrExpiredQuestRemovesNavigationInsteadOfGuessingAnotherTarget() {
        Game failed = targetedRescueGame();
        assertNotNull(QuestObjectiveView.resolve(failed));

        failed.faction.quest.fail("target lost");
        assertNull(QuestObjectiveView.resolve(failed));
        assertEquals("", QuestObjectiveView.navigationLabel(failed));

        Game expired = targetedRescueGame();
        expired.faction.quest.expire();
        assertNull(QuestObjectiveView.resolve(expired));
        assertEquals("", QuestObjectiveView.navigationLabel(expired));
    }

    @Test
    void escortNavigationUsesThePersistedDestinationIdentity() {
        Game game = targetedSettlementQuestGame(Quest.Type.ESCORT_TRADER);
        Quest quest = game.faction.quest;
        QuestObjectiveView.Objective objective = QuestObjectiveView.resolve(game);

        assertNotNull(objective);
        assertFalse(quest.destinationId.isBlank());
        assertEquals(quest.destinationId, objective.targetId());
        assertEquals(game.world.settlements.get(quest.targetSettlementId).center,
                objective.position());

        quest.destinationId = FactionSystem.settlementProviderId(quest.targetSettlementId + 1);
        assertNull(QuestObjectiveView.resolve(game),
                "a mismatched destination id must not fall back to the settlement field");
    }

    private static Game targetedRescueGame() {
        return targetedSettlementQuestGame(Quest.Type.RESCUE_CAPTIVE);
    }

    private static Game targetedSettlementQuestGame(Quest.Type type) {
        Game game = new Game();
        game.newWorld(20260716L, true);
        Settlement provider = null;
        boolean hasCaptive = false;
        for (int rx = -10; rx <= 10; rx++) {
            for (int rz = -10; rz <= 10; rz++) {
                Settlement settlement = game.world.settlementForRegion(rx, rz);
                if (settlement == null) {
                    continue;
                }
                if (provider == null && settlement.friendly()) {
                    provider = settlement;
                    provider.discovered = true;
                }
                hasCaptive |= settlement.hostile() && settlement.residents.stream()
                        .anyMatch(r -> r.archetype == NpcArchetype.CAPTIVE
                                && r.alive && !r.rescued);
            }
        }
        assertNotNull(provider);
        assertTrue(hasCaptive);
        Npc giver = new Npc(game.world, "Rescue coordinator");
        giver.archetype = NpcArchetype.TRADER;
        giver.isTrader = true;
        giver.settlementId = provider.id;
        giver.residentIndex = 0;
        game.faction.makeQuestOfferAvailable();
        Quest quest = game.faction.offerSettlementQuestOfType(
                game, giver, provider, type);
        assertNotNull(quest);
        return game;
    }

    private static int captiveIndex(Settlement settlement, String captiveId) {
        for (int i = 0; i < settlement.residents.size(); i++) {
            if (captiveId.equals(FactionSystem.captiveId(settlement, i))) {
                return i;
            }
        }
        throw new AssertionError("quest captive not found in its bound settlement");
    }
}
