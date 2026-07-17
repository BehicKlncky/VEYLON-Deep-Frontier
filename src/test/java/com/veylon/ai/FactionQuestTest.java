package com.veylon.ai;

import com.veylon.Game;
import com.veylon.entity.Npc;
import com.veylon.item.ItemType;
import com.veylon.save.SaveSystem;
import com.veylon.settlement.HumanFaction;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementType;
import com.veylon.ui.NpcScreen;
import com.veylon.util.Vec3i;
import com.veylon.world.Poi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionQuestTest {

    @Test
    void npcDialogueDeliversMedicineOnlyToTheProviderAndRewardsExactlyOnce() {
        Scenario s = scenario(9091L);
        s.home.medStock = 0;
        s.g.faction.makeQuestOfferAvailable();
        NpcScreen dialogue = new NpcScreen();

        String offer = dialogue.performSettlementQuestAction(s.g, s.provider, s.home);
        Quest q = s.g.faction.quest;
        assertNotNull(q);
        assertEquals(Quest.Type.DELIVER_SUPPLIES, q.type);
        assertEquals(ItemType.MEDICINE, q.item);
        assertEquals(s.home.id, q.targetSettlementId);
        assertEquals(FactionSystem.settlementProviderId(s.home.id), q.rewardProviderId);
        assertEquals(Quest.Status.ACTIVE, q.status);
        assertTrue(offer.contains("help"));

        s.g.player.inventory.add(ItemType.MEDICINE, q.required);
        int medicineBefore = s.g.player.inventory.count(ItemType.MEDICINE);
        String wrongProvider = dialogue.performSettlementQuestAction(
                s.g, s.otherProvider, s.otherFriendly);
        assertTrue(wrongProvider.contains("returned to"));
        assertEquals(medicineBefore, s.g.player.inventory.count(ItemType.MEDICINE));
        assertEquals(10, s.otherFriendly.medStock);

        float localBefore = s.home.localReputation;
        float factionBefore = s.g.world.factionReputation
                .getOrDefault(s.home.factionId, 0f);
        String completed = dialogue.performSettlementQuestAction(s.g, s.provider, s.home);
        assertTrue(completed.contains("thanks"));
        assertNull(s.g.faction.quest);
        assertEquals(q.required, s.home.medStock);
        assertEquals(localBefore + q.trustReward, s.home.localReputation, 0.001f);
        assertTrue(s.g.world.factionReputation.get(s.home.factionId) > factionBefore);
        int rewardAfter = s.g.player.inventory.count(q.rewardItem);

        dialogue.performSettlementQuestAction(s.g, s.provider, s.home);
        assertEquals(rewardAfter, s.g.player.inventory.count(q.rewardItem),
                "repeated dialogue cannot duplicate the completed reward");
        assertEquals(localBefore + q.trustReward, s.home.localReputation, 0.001f);
    }

    @Test
    void npcDialogueUsesTheRequestedSettlementsFoodStock() {
        Scenario s = scenario(9092L);
        s.home.medStock = 10;
        s.home.foodStock = 0;
        s.g.faction.makeQuestOfferAvailable();
        NpcScreen dialogue = new NpcScreen();

        dialogue.performSettlementQuestAction(s.g, s.provider, s.home);
        Quest q = s.g.faction.quest;
        assertNotNull(q);
        assertEquals(ItemType.COOKED_MEAT, q.item);
        s.g.player.inventory.add(q.item, q.required);
        dialogue.performSettlementQuestAction(s.g, s.provider, s.home);

        assertEquals(q.required, s.home.foodStock);
        assertEquals(50, s.otherFriendly.foodStock,
                "another provider's stores must remain untouched");
    }

    @Test
    void everyRegionalCategoryGetsAConcreteTargetAndRejectsUnrelatedEvents() {
        for (Quest.Type type : EnumSet.of(
                Quest.Type.DEFEND_VILLAGE, Quest.Type.ESCORT_TRADER,
                Quest.Type.SCOUT_HOSTILE_FORT, Quest.Type.RESCUE_CAPTIVE,
                Quest.Type.CLEAR_PATROL, Quest.Type.SABOTAGE_ALARM,
                Quest.Type.RECOVER_STOLEN_SUPPLIES, Quest.Type.CAPTURE_FORT,
                Quest.Type.DEFEND_OUTPOST, Quest.Type.EXPLORE_SETTLEMENT_CAVE)) {
            Scenario s = scenario(10_000L + type.ordinal());
            s.g.faction.makeQuestOfferAvailable();
            Quest q = s.g.faction.offerSettlementQuestOfType(s.g, s.provider, s.home, type);
            assertNotNull(q, type + " has a normally validated provider offer");
            assertFalse(q.instanceId.isBlank());
            assertFalse(q.giverId.isBlank());
            assertEquals(s.home.id, q.giverSettlementId);
            assertEquals(FactionSystem.settlementProviderId(s.home.id), q.rewardProviderId);
            assertEquals(Quest.Status.ACTIVE, q.status);

            applyUnrelatedThenMatchingEvent(s, q);
            assertEquals(Quest.Status.READY_TO_TURN_IN, q.status,
                    type + " only completes from its matching event context");
            if (type == Quest.Type.RECOVER_STOLEN_SUPPLIES) {
                s.g.player.inventory.add(q.item, q.required);
            }

            NpcScreen dialogue = new NpcScreen();
            dialogue.performSettlementQuestAction(s.g, s.otherProvider, s.otherFriendly);
            assertNotNull(s.g.faction.quest, type + " cannot be turned in elsewhere");
            dialogue.performSettlementQuestAction(s.g, s.provider, s.home);
            assertNull(s.g.faction.quest, type + " turns in at its provider");
        }
    }

    @Test
    void normalRegionalOfferPoolContainsEveryCategoryWhenTargetsExist() {
        Scenario s = scenario(9090L);
        s.g.faction.setQuestRandomSeed(12345L);
        EnumSet<Quest.Type> offered = EnumSet.noneOf(Quest.Type.class);
        for (int i = 0; i < 400; i++) {
            s.g.faction.quest = null; // isolate independent provider conversations
            s.g.faction.makeQuestOfferAvailable();
            Quest offer = s.g.faction.offerSettlementQuest(s.g, s.provider, s.home);
            assertNotNull(offer);
            offered.add(offer.type);
        }
        EnumSet<Quest.Type> expected = EnumSet.of(
                Quest.Type.DEFEND_VILLAGE, Quest.Type.ESCORT_TRADER,
                Quest.Type.SCOUT_HOSTILE_FORT, Quest.Type.RESCUE_CAPTIVE,
                Quest.Type.CLEAR_PATROL, Quest.Type.SABOTAGE_ALARM,
                Quest.Type.RECOVER_STOLEN_SUPPLIES, Quest.Type.CAPTURE_FORT,
                Quest.Type.DEFEND_OUTPOST, Quest.Type.EXPLORE_SETTLEMENT_CAVE);
        assertTrue(offered.containsAll(expected), "eligible dialogue pool covers every category");
    }

    @Test
    void preTargetQuestIsWithdrawnWithoutBindingOrRewardingAnUnrelatedProvider() {
        Scenario s = scenario(9093L);
        s.g.faction.quest = new Quest(Quest.Type.RESCUE_CAPTIVE, null, 1, 500,
                20, ItemType.MEDICINE, 2, "Historical giver");
        float rep = s.home.localReputation;
        int medicine = s.g.player.inventory.count(ItemType.MEDICINE);

        String result = s.g.faction.turnInSettlementQuest(s.g, s.home);

        assertTrue(result.contains("no verifiable target"));
        assertNull(s.g.faction.quest);
        assertEquals(rep, s.home.localReputation);
        assertEquals(medicine, s.g.player.inventory.count(ItemType.MEDICINE));
    }

    @Test
    void coreQuestFieldsRemainSaveCompatible(@TempDir Path dir) {
        for (Quest.Type type : Quest.Type.values()) {
            Game g = new Game();
            g.newWorld(7070L, true);
            Quest q = new Quest(type, ItemType.MEDICINE, 3, 432.5f,
                    17, ItemType.BANDAGE, 2, "Persistent giver");
            q.progress = 1;
            g.faction.quest = q;
            Path save = dir.resolve(type.name() + ".sav");
            assertTrue(SaveSystem.save(g, save));

            Game loaded = new Game();
            assertTrue(SaveSystem.load(loaded, save));
            assertNotNull(loaded.faction.quest);
            assertEquals(type, loaded.faction.quest.type);
            assertEquals(1, loaded.faction.quest.progress);
            assertEquals("Persistent giver", loaded.faction.quest.giverName);
            assertEquals(Quest.Status.LEGACY_UNBOUND, loaded.faction.quest.status,
                    "core-only data is never guessed onto a new target");
        }
    }

    private static void applyUnrelatedThenMatchingEvent(Scenario s, Quest q) {
        switch (q.type) {
            case DEFEND_VILLAGE -> {
                String mission = q.targetMissionId;
                assertFalse(mission.startsWith("pending:"));
                s.g.faction.onWarPartyKill(s.g, "defense-wrong", s.hostile.id,
                        s.home.id, HumanFaction.HEADHUNTERS, "wrong-member");
                assertEquals(0, q.progress);
                for (int i = 0; i < q.required; i++) {
                    s.g.faction.onWarPartyKill(s.g, mission, s.hostile.id,
                            s.home.id, HumanFaction.HEADHUNTERS, "member-" + i);
                }
                s.g.faction.onWarPartyKill(s.g, mission, s.hostile.id,
                        s.home.id, HumanFaction.HEADHUNTERS, "member-0");
                assertEquals(q.required, q.progress, "duplicate member event is ignored");
            }
            case ESCORT_TRADER -> {
                s.g.faction.onTraderEscorted(s.g, q.targetMissionId, s.hostile.id);
                assertEquals(0, q.progress);
                s.g.faction.onTraderEscorted(s.g, q.targetMissionId, q.targetSettlementId);
            }
            case SCOUT_HOSTILE_FORT -> {
                s.g.faction.onSettlementDiscovered(s.g, s.otherHostile);
                assertEquals(0, q.progress);
                s.g.faction.onSettlementDiscovered(s.g, s.hostile);
            }
            case RESCUE_CAPTIVE -> {
                s.g.faction.onCaptiveRescued(s.g, s.otherHostile, 0);
                assertEquals(0, q.progress);
                s.g.faction.onCaptiveRescued(s.g, s.hostile, 0);
            }
            case CLEAR_PATROL -> {
                String mission = q.targetMissionId;
                assertFalse(mission.startsWith("pending:"));
                s.g.faction.onWarPartyKill(s.g, mission, s.otherHostile.id,
                        s.home.id, HumanFaction.HEADHUNTERS, "wrong-member");
                assertEquals(0, q.progress);
                for (int i = 0; i < q.required; i++) {
                    s.g.faction.onWarPartyKill(s.g, mission, s.hostile.id,
                            s.home.id, HumanFaction.HEADHUNTERS, "member-" + i);
                }
            }
            case SABOTAGE_ALARM -> {
                s.g.faction.onAlarmSabotaged(s.g, s.otherHostile);
                assertEquals(0, q.progress);
                s.g.faction.onAlarmSabotaged(s.g, s.hostile);
            }
            case RECOVER_STOLEN_SUPPLIES -> {
                s.g.faction.onStolenSuppliesRecovered(s.g, s.hostile, "wrong-storage");
                assertEquals(0, q.progress);
                s.g.faction.onStolenSuppliesRecovered(s.g, s.hostile, q.targetPoiId);
            }
            case CAPTURE_FORT -> {
                s.otherHostile.occupied = true;
                s.g.faction.onFortCaptured(s.g, s.otherHostile);
                assertEquals(0, q.progress);
                s.hostile.occupied = true;
                s.g.faction.onFortCaptured(s.g, s.hostile);
            }
            case DEFEND_OUTPOST -> {
                String mission = q.targetMissionId;
                assertFalse(mission.startsWith("pending:"));
                s.g.faction.onOutpostDefended(s.g, s.outpost, "counterattack-wrong");
                assertEquals(0, q.progress);
                s.g.faction.onOutpostDefended(s.g, s.outpost, mission);
            }
            case EXPLORE_SETTLEMENT_CAVE -> {
                Poi wrong = new Poi(Poi.PoiType.ABANDONED_MINE,
                        new Vec3i(800, 8, 800));
                s.g.faction.onCaveExplored(s.g, wrong);
                assertEquals(0, q.progress);
                Poi matching = new Poi(Poi.PoiType.ABANDONED_MINE,
                        new Vec3i(s.home.center.x(), 8, s.home.center.z()));
                s.g.faction.onCaveExplored(s.g, matching);
            }
            default -> throw new AssertionError("Unexpected type " + q.type);
        }
    }

    private static Scenario scenario(long seed) {
        Game g = new Game();
        g.newWorld(seed, true);
        g.world.settlements.clear();

        Settlement home = settlement(100, 0, 0, SettlementType.VILLAGE,
                HumanFaction.FRONTIER, Settlement.Alignment.FRIENDLY);
        home.foodStock = 50;
        home.medStock = 10;
        Settlement otherFriendly = settlement(101, 0, 0, SettlementType.VILLAGE,
                HumanFaction.FREE_SETTLERS, Settlement.Alignment.FRIENDLY);
        otherFriendly.foodStock = 50;
        otherFriendly.medStock = 10;
        Settlement hostile = settlement(200, 0, 0, SettlementType.FORTRESS,
                HumanFaction.HEADHUNTERS, Settlement.Alignment.HOSTILE);
        hostile.alarmBell = new Vec3i(105, 40, 100);
        hostile.residents.add(new Settlement.Resident("Marked Prisoner", NpcArchetype.CAPTIVE));
        Settlement otherHostile = settlement(201, 1, 1, SettlementType.FORT,
                HumanFaction.HEADHUNTERS, Settlement.Alignment.HOSTILE);
        otherHostile.alarmBell = new Vec3i(805, 40, 800);
        otherHostile.residents.add(new Settlement.Resident("Other Prisoner", NpcArchetype.CAPTIVE));
        Settlement outpost = settlement(300, -1, 0, SettlementType.FORT,
                HumanFaction.FRONTIER, Settlement.Alignment.FRIENDLY);
        outpost.cleared = true;
        outpost.occupied = true;

        g.world.settlements.put(home.id, home);
        g.world.settlements.put(otherFriendly.id, otherFriendly);
        g.world.settlements.put(hostile.id, hostile);
        g.world.settlements.put(otherHostile.id, otherHostile);
        g.world.settlements.put(outpost.id, outpost);

        Npc provider = provider(g, home, "Home Quartermaster");
        Npc otherProvider = provider(g, otherFriendly, "Other Quartermaster");
        return new Scenario(g, home, otherFriendly, hostile, otherHostile, outpost,
                provider, otherProvider);
    }

    private static Settlement settlement(long id, int regionX, int regionZ,
                                         SettlementType type, String faction,
                                         Settlement.Alignment alignment) {
        int x = id == 100 ? 0 : id == 101 ? 25 : id == 200 ? 100
                : id == 201 ? 800 : -100;
        return new Settlement(id, regionX, regionZ, type, new Vec3i(x, 40, x),
                faction, alignment);
    }

    private static Npc provider(Game g, Settlement home, String name) {
        Npc npc = new Npc(g.world, name);
        npc.archetype = NpcArchetype.TRADER;
        npc.isTrader = true;
        npc.settlementId = home.id;
        npc.residentIndex = 0;
        return npc;
    }

    private record Scenario(Game g, Settlement home, Settlement otherFriendly,
                            Settlement hostile, Settlement otherHostile,
                            Settlement outpost, Npc provider, Npc otherProvider) {
    }
}
