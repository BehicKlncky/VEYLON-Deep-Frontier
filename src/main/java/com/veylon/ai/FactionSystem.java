package com.veylon.ai;

import com.veylon.Game;
import com.veylon.entity.Npc;
import com.veylon.item.Inventory;
import com.veylon.item.ItemType;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementBuilder;
import com.veylon.settlement.SettlementPlanner;
import com.veylon.settlement.SettlementManager;
import com.veylon.settlement.SettlementType;
import com.veylon.util.MathUtil;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Poi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

/**
 * State of the single NPC camp: stocks, trust toward the player, alert level,
 * camp quests and staged settlement upgrades (palisade, comforts, med tent).
 */
public class FactionSystem {

    public Vec3i campPos;
    /** 0..100; below 15 the camp turns hostile. */
    public float trust = 35;
    public float alert = 0;
    public int foodStock = 10;
    public int woodStock = 8;
    public boolean hostile = false;
    /** 0 = bare camp .. 3 = fully built settlement. */
    public int upgradeStage = 0;
    /** While > 0 an NPC works the construction site (cosmetic of a real build). */
    public float buildTimer = 0;
    /** Reward flags so tier perks are granted once. */
    public boolean alliedGiftGiven = false;

    public Quest quest;

    private float foodTimer = 0;
    private float fireTimer = 0;
    private float questCooldown = 60;
    /** Monotonic ingredient of newly-created quest instance ids. */
    private long questSequence;
    private final Random rng = new Random();

    /** Seeded per world so a given world seed replays identically. */
    public void setRandomSeed(long seed) {
        rng.setSeed(seed);
    }

    public void addTrust(Game g, float delta, String reason) {
        trust = MathUtil.clamp(trust + delta, 0, 100);
        if (reason != null && Math.abs(delta) >= 1) {
            g.log(reason + " (trust " + (delta > 0 ? "+" : "") + (int) delta + ")");
        }
        if (trust < 15 && !hostile) {
            hostile = true;
            g.log("The camp has turned HOSTILE toward you!");
        } else if (trust >= 25 && hostile) {
            hostile = false;
            g.log("The camp is no longer hostile.");
        }
        if (delta < 0) {
            alert = Math.min(100, alert + Math.abs(delta) * 2);
        }
        // Allied tier perk: a one-time gift of advanced supplies.
        if (trust >= 75 && !alliedGiftGiven) {
            alliedGiftGiven = true;
            g.player.inventory.add(ItemType.IRON_INGOT, 2);
            g.player.inventory.add(ItemType.MEDICINE, 1);
            g.log("ALLIED with the camp! They share supplies: 2 iron ingots, 1 medicine.");
            g.audio.playQuest();
        }
    }

    public String standing() {
        if (hostile) {
            return "Hostile";
        }
        if (trust >= 75) {
            return "Allied";
        }
        if (trust >= 50) {
            return "Friendly";
        }
        if (trust >= 25) {
            return "Neutral";
        }
        return "Wary";
    }

    /** Camp beds and medic help are open to Friendly+ players. */
    public boolean campPrivileges() {
        return !hostile && trust >= 50;
    }

    // ------------------------------------------------------------------
    // Quests
    // ------------------------------------------------------------------

    /** Offers a safely bound request based on what the starter camp needs. */
    public Quest offerQuest(Game g, String giverName) {
        if (quest != null) {
            return quest;
        }
        if (questCooldown > 0) {
            return null;
        }
        String giverId = "camp:" + slug(giverName);
        Quest offered;
        boolean npcSick = g.entities.npcs.stream()
                .anyMatch(n -> n.sick && !n.isTrader && !n.raider && !n.settled());
        if (npcSick || rng.nextFloat() < 0.15f) {
            offered = newQuest(g, Quest.Type.FETCH, ItemType.MEDICINE, 1, 900,
                    14, ItemType.IRON_INGOT, 1, giverName, giverId,
                    Quest.NO_SETTLEMENT, "camp");
        } else if (foodStock < 8) {
            offered = newQuest(g, Quest.Type.FETCH, ItemType.COOKED_MEAT, 3, 700,
                    10, ItemType.HIDE, 2, giverName, giverId,
                    Quest.NO_SETTLEMENT, "camp");
        } else if (woodStock < 14) {
            offered = newQuest(g, Quest.Type.FETCH, ItemType.LOG, 6, 700,
                    8, ItemType.BANDAGE, 2, giverName, giverId,
                    Quest.NO_SETTLEMENT, "camp");
        } else {
            List<Quest.Type> available = new ArrayList<>(List.of(
                    Quest.Type.HUNT_PREDATOR, Quest.Type.INVESTIGATE,
                    Quest.Type.DELIVER_SUPPLIES));
            if (nearestToCamp(g, s -> !s.discovered) != null) {
                available.add(Quest.Type.SCOUT_SETTLEMENT);
            }
            if (nearestToCamp(g, s -> s.hostile() && firstCaptiveIndex(s) >= 0) != null) {
                available.add(Quest.Type.RESCUE_CAPTIVE);
            }
            if (nearestToCamp(g, s -> s.hostile() && isFort(s)) != null) {
                available.add(Quest.Type.CLEAR_HOSTILE);
            }
            if (nearestToCamp(g, Settlement::hostile) != null) {
                available.add(Quest.Type.DRIVE_OFF);
            }
            Quest.Type type = available.get(rng.nextInt(available.size()));
            offered = campObjective(g, giverName, giverId, type);
        }
        markQuestTargetRumored(g, offered);
        quest = offered;
        g.audio.playQuest();
        return quest;
    }

    /** Regional traders/leaders provide targetable expansion quests through dialogue. */
    public Quest offerSettlementQuest(Game g, Npc giver, Settlement settlement) {
        if (quest != null) {
            return quest;
        }
        if (questCooldown > 0 || !validProvider(giver, settlement)) {
            return null;
        }
        if (settlement.medStock < 2) {
            return offerSettlementQuestOfType(g, giver, settlement, Quest.Type.DELIVER_SUPPLIES);
        }
        if (settlement.foodStock < 8) {
            return offerSettlementQuestOfType(g, giver, settlement, Quest.Type.DELIVER_SUPPLIES);
        }
        List<Quest.Type> available = new ArrayList<>();
        for (Quest.Type type : List.of(Quest.Type.DEFEND_VILLAGE, Quest.Type.ESCORT_TRADER,
                Quest.Type.SCOUT_HOSTILE_FORT, Quest.Type.RESCUE_CAPTIVE,
                Quest.Type.CLEAR_PATROL, Quest.Type.SABOTAGE_ALARM,
                Quest.Type.RECOVER_STOLEN_SUPPLIES, Quest.Type.CAPTURE_FORT,
                Quest.Type.DEFEND_OUTPOST, Quest.Type.EXPLORE_SETTLEMENT_CAVE)) {
            if (hasTargetFor(g, giver, settlement, type)) {
                available.add(type);
            }
        }
        if (available.isEmpty()) {
            return null;
        }
        return offerSettlementQuestOfType(g, giver, settlement,
                available.get(rng.nextInt(available.size())));
    }

    /**
     * Production quest-provider seam used by dialogue and deterministic QA.
     * It still performs all provider/target validation and cannot create an
     * impossible or unbound request.
     */
    public Quest offerSettlementQuestOfType(Game g, Npc giver, Settlement home,
                                            Quest.Type type) {
        if (quest != null) {
            return quest;
        }
        if (questCooldown > 0 || !validProvider(giver, home)
                || !hasTargetFor(g, giver, home, type)) {
            return null;
        }
        String provider = settlementProviderId(home.id);
        String giverId = stableNpcId(giver, home.id);
        String name = giver.name + " of " + home.type.displayName;
        Quest q;
        Settlement target;
        switch (type) {
            case DELIVER_SUPPLIES, FETCH -> {
                ItemType need = home.medStock < 2 ? ItemType.MEDICINE
                        : home.foodStock < 8 ? ItemType.COOKED_MEAT : ItemType.IRON_ORE;
                int count = need == ItemType.MEDICINE ? 2 : need == ItemType.COOKED_MEAT ? 4 : 3;
                q = newQuest(g, type, need, count, 1200, need == ItemType.MEDICINE ? 12 : 10,
                        need == ItemType.MEDICINE ? ItemType.BANDAGE : ItemType.HIDE,
                        need == ItemType.MEDICINE ? 3 : 2, name, giverId, home.id, provider);
                targetSettlement(q, home);
            }
            case DEFEND_VILLAGE -> {
                q = newQuest(g, type, null, 3, 1800, 16, ItemType.IRON_ARROW, 6,
                        name, giverId, home.id, provider);
                targetSettlement(q, home);
                q.targetFactionId = ""; // filled when the actual attacking mission dispatches
                q.targetMissionId = pendingMission("defense", home.id);
            }
            case ESCORT_TRADER -> {
                q = newQuest(g, type, null, 1, 1800, 15,
                        ItemType.BLUEPRINT_FRAGMENT, 1, name, giverId, home.id, provider);
                Settlement destination = closestSettlement(g, home,
                        s -> s.id != home.id && s.friendly());
                if (destination == null) {
                    return null;
                }
                q.destinationId = settlementProviderId(destination.id);
                q.targetSettlementId = destination.id;
                q.targetFactionId = destination.factionId;
            }
            case SCOUT_HOSTILE_FORT -> {
                target = hostileFortTarget(g, home, false, true);
                q = newQuest(g, type, null, 1, 1800, 15, ItemType.BANDAGE, 3,
                        name, giverId, home.id, provider);
                targetSettlement(q, target);
            }
            case RESCUE_CAPTIVE -> {
                target = captiveTarget(g, home);
                q = newQuest(g, type, null, 1, 2400, 20, ItemType.MEDICINE, 2,
                        name, giverId, home.id, provider);
                targetSettlement(q, target);
                q.targetCaptiveId = firstCaptiveId(target);
            }
            case CLEAR_PATROL -> {
                target = closestSettlement(g, home, Settlement::hostile);
                q = newQuest(g, type, null, 3, 1800, 16, ItemType.MUSKET_BALL, 5,
                        name, giverId, home.id, provider);
                targetSettlement(q, target);
                q.targetMissionId = pendingMission("patrol", target.id);
            }
            case SABOTAGE_ALARM -> {
                target = hostileFortTarget(g, home, true, false);
                q = newQuest(g, type, null, 1, 2200, 18, ItemType.BLACK_POWDER, 2,
                        name, giverId, home.id, provider);
                targetSettlement(q, target);
            }
            case RECOVER_STOLEN_SUPPLIES -> {
                target = closestSettlement(g, home, Settlement::hostile);
                q = newQuest(g, type, ItemType.MEDICINE, 1, 1800, 14, ItemType.MEDICINE, 1,
                        name, giverId, home.id, provider);
                targetSettlement(q, target);
                // Replaced with an exact generated-crate identity when the
                // objective is materialized below.
                q.targetPoiId = settlementStorageId(target.id);
            }
            case CAPTURE_FORT -> {
                target = hostileFortTarget(g, home, false, false);
                q = newQuest(g, type, null, 1, 3600, 28, ItemType.RIFLE_CARTRIDGE, 4,
                        name, giverId, home.id, provider);
                targetSettlement(q, target);
            }
            case DEFEND_OUTPOST -> {
                target = closestSettlement(g, home, s -> s.occupied);
                q = newQuest(g, type, null, 1, 3000, 22, ItemType.IRON_INGOT, 2,
                        name, giverId, home.id, provider);
                targetSettlement(q, target);
                q.targetFactionId = ""; // filled by the counterattack mission
                q.targetMissionId = pendingMission("counterattack", target.id);
            }
            case EXPLORE_SETTLEMENT_CAVE -> {
                q = newQuest(g, type, null, 1, 2400, 18,
                        ItemType.BLUEPRINT_FRAGMENT, 1, name, giverId, home.id, provider);
                targetSettlement(q, home);
                q.targetPoiId = caveRegionId(home.regionX, home.regionZ);
            }
            default -> {
                return null;
            }
        }
        if (!materializeQuestObjective(g, q, home)) {
            return null;
        }
        markQuestTargetRumored(g, q);
        quest = q;
        g.audio.playQuest();
        return quest;
    }

    /** Attempts to turn in a starter-camp quest at the camp provider. */
    public String turnInQuest(Game g) {
        if (quest == null) {
            return null;
        }
        String state = nonRewardingTerminalState();
        if (state != null) {
            return state;
        }
        if (!quest.providerMatches("camp")) {
            return "This request belongs to another settlement.";
        }
        if (!prepareDelivery(g, null)) {
            return deliveryMissingText(g);
        }
        if (!quest.readyToTurnIn()) {
            return "Not done yet: " + quest.describe();
        }
        rewardCampQuest(g);
        return "You have our thanks. The camp won't forget this.";
    }

    /** Turns a request in only to the settlement that authorized its reward. */
    public String turnInSettlementQuest(Game g, Settlement settlement) {
        if (quest == null || settlement == null) {
            return null;
        }
        String state = nonRewardingTerminalState();
        if (state != null) {
            return state;
        }
        if (!quest.providerMatches(settlementProviderId(settlement.id))) {
            return "This request must be returned to " + quest.giverName + ".";
        }
        if (!prepareDelivery(g, settlement)) {
            return deliveryMissingText(g);
        }
        if (!quest.readyToTurnIn()) {
            return "Not done yet: " + quest.describe();
        }
        if (quest.rewardClaimed) {
            return "That reward was already claimed.";
        }
        Quest completed = quest;
        completed.rewardClaimed = true;
        completed.status = Quest.Status.COMPLETED;
        if (completed.rewardItem != null && completed.rewardCount > 0) {
            g.player.inventory.add(completed.rewardItem, completed.rewardCount);
            g.log("Reward: " + completed.rewardCount + "x " + completed.rewardItem.displayName);
        }
        g.settlementManager.addLocalReputation(g, settlement, completed.trustReward,
                "Local request fulfilled");
        g.settlementManager.addReputation(g, settlement.factionId,
                Math.max(1, completed.trustReward / 2f), null);
        clearCompletedQuest();
        g.audio.playQuest();
        return "You have our thanks. This settlement will remember your help.";
    }

    public void setQuestRandomSeed(long seed) {
        rng.setSeed(seed);
    }

    public void makeQuestOfferAvailable() {
        questCooldown = 0;
    }

    /** Clears cross-world request identity/cooldown state for a newly created world. */
    public void resetQuestRuntime() {
        quest = null;
        questCooldown = 60;
        questSequence = 0;
    }

    public long questSequence() {
        return questSequence;
    }

    public void restoreQuestSequence(long value) {
        questSequence = Math.max(questSequence, value);
    }

    // ---- Target-aware gameplay event hooks ----

    public void onPredatorKilledNearCamp(Game g) {
        progress(g, Quest.Type.HUNT_PREDATOR, campRegionId(g), 1);
    }

    public void onPoiDiscovered(Game g) {
        // POI context is mandatory; use onPoiDiscovered(Game, Poi).
    }

    public void onPoiDiscovered(Game g, Poi poi) {
        if (quest == null || quest.type != Quest.Type.INVESTIGATE || poi == null) {
            return;
        }
        String region = "camp-region:" + SettlementPlanner.regionOfBlock(poi.pos.x()) + ":"
                + SettlementPlanner.regionOfBlock(poi.pos.z());
        if (quest.targetPoiId.equals(region)) {
            progress(g, Quest.Type.INVESTIGATE, "investigate:" + poiId(poi), 1);
        }
    }

    public void onSettlementDiscovered(Game g, Settlement settlement) {
        if (settlement == null || quest == null || quest.targetSettlementId != settlement.id) {
            return;
        }
        if (quest.type == Quest.Type.SCOUT_SETTLEMENT
                || quest.type == Quest.Type.SCOUT_HOSTILE_FORT && isFort(settlement)
                && settlement.hostile()) {
            progress(g, quest.type, "settlement-discovered:" + stableSettlementId(settlement.id), 1);
        }
    }

    public void onCaptiveRescued(Game g, Settlement settlement, int residentIndex) {
        if (quest == null || quest.type != Quest.Type.RESCUE_CAPTIVE || settlement == null
                || quest.targetSettlementId != settlement.id
                || !quest.targetCaptiveId.equals(captiveId(settlement, residentIndex))) {
            return;
        }
        progress(g, Quest.Type.RESCUE_CAPTIVE,
                "captive-rescued:" + quest.targetCaptiveId, 1);
    }

    /** Compatibility overload intentionally cannot guess which historical captive was targeted. */
    public void onCaptiveRescued(Game g) {
        // Context is mandatory; old unbound quests never attach to an unrelated rescue.
    }

    public void onSettlementCleared(Game g, Settlement settlement) {
        if (matchesSettlement(Quest.Type.CLEAR_HOSTILE, settlement)) {
            progress(g, Quest.Type.CLEAR_HOSTILE,
                    "settlement-cleared:" + stableSettlementId(settlement.id), 1);
        }
    }

    public void onSettlementCleared(Game g) {
        // Context is mandatory.
    }

    /** Binds a pending defense/patrol objective when its concrete mission dispatches. */
    public boolean bindMission(Quest.Type type, String missionId, long targetSettlementId,
                               String targetFactionId) {
        if (quest == null || quest.type != type || quest.status != Quest.Status.ACTIVE
                || quest.targetSettlementId != targetSettlementId
                || missionId == null || missionId.isBlank()
                || !quest.targetMissionId.startsWith("pending:")) {
            return false;
        }
        quest.targetMissionId = missionId;
        if (targetFactionId != null && !targetFactionId.isBlank()) {
            quest.targetFactionId = targetFactionId;
        }
        return true;
    }

    public void onWarPartyKill(Game g, String missionId, long originSettlementId,
                               long targetSettlementId, String factionId, String memberId) {
        if (quest == null || !(quest.type == Quest.Type.DRIVE_OFF
                || quest.type == Quest.Type.CLEAR_PATROL
                || quest.type == Quest.Type.DEFEND_VILLAGE)) {
            return;
        }
        long expectedSettlement = quest.type == Quest.Type.CLEAR_PATROL
                || quest.type == Quest.Type.DRIVE_OFF
                ? originSettlementId : targetSettlementId;
        if (quest.targetSettlementId != expectedSettlement
                || !missionMatchesOrBinds(quest, missionId)
                || !factionMatches(quest, factionId)) {
            return;
        }
        progress(g, quest.type, "party-kill:" + missionId + ":" + memberId, 1);
    }

    public void onWarPartyKill(Game g) {
        // Mission and target context are mandatory.
    }

    public void onAlarmSabotaged(Game g, Settlement settlement) {
        if (matchesSettlement(Quest.Type.SABOTAGE_ALARM, settlement)) {
            progress(g, Quest.Type.SABOTAGE_ALARM,
                    "alarm:" + stableSettlementId(settlement.id), 1);
        }
    }

    public void onAlarmSabotaged(Game g) {
        // Context is mandatory.
    }

    public void onStolenSuppliesRecovered(Game g, Settlement settlement, String storageId) {
        onStolenSuppliesRecovered(g, settlement, storageId,
                quest != null ? quest.item : null, 1);
    }

    public void onStolenSuppliesRecovered(Game g, Settlement settlement, String storageId,
                                          ItemType recoveredItem, int count) {
        if (matchesSettlement(Quest.Type.RECOVER_STOLEN_SUPPLIES, settlement)
                && storageTargetMatches(quest, settlement, storageId)
                && quest.item == recoveredItem) {
            progress(g, Quest.Type.RECOVER_STOLEN_SUPPLIES,
                    "supplies:" + storageId + ":" + recoveredItem.name(), count);
        }
    }

    public void onStolenSuppliesRecovered(Game g) {
        // Context is mandatory.
    }

    public void onFortCaptured(Game g, Settlement settlement) {
        if (matchesSettlement(Quest.Type.CAPTURE_FORT, settlement) && settlement.occupied) {
            progress(g, Quest.Type.CAPTURE_FORT,
                    "captured:" + stableSettlementId(settlement.id), 1);
        }
    }

    public void onFortCaptured(Game g) {
        // Context is mandatory.
    }

    public void onOutpostDefended(Game g, Settlement settlement, String missionId) {
        if (matchesSettlement(Quest.Type.DEFEND_OUTPOST, settlement)
                && missionMatchesOrBinds(quest, missionId)) {
            progress(g, Quest.Type.DEFEND_OUTPOST, "outpost-defense:" + missionId, 1);
        }
    }

    public void onOutpostDefended(Game g) {
        // Context is mandatory.
    }

    public void onCaveExplored(Game g, Poi poi) {
        if (poi == null || !isUndergroundPoi(poi.type)) {
            return;
        }
        int rx = SettlementPlanner.regionOfBlock(poi.pos.x());
        int rz = SettlementPlanner.regionOfBlock(poi.pos.z());
        onCaveRegionExplored(g, rx, rz, poiId(poi));
    }

    public void onCaveRegionExplored(Game g, int regionX, int regionZ, String poiIdentity) {
        if (quest != null && quest.type == Quest.Type.EXPLORE_SETTLEMENT_CAVE
                && quest.targetPoiId.equals(caveRegionId(regionX, regionZ))) {
            progress(g, Quest.Type.EXPLORE_SETTLEMENT_CAVE,
                    "cave:" + poiIdentity, 1);
        }
    }

    public void onCaveExplored(Game g) {
        // POI/region context is mandatory.
    }

    public void onTraderEscorted(Game g, String traderId, long destinationSettlementId) {
        if (quest != null && quest.type == Quest.Type.ESCORT_TRADER
                && quest.targetMissionId.equals(traderId)
                && quest.destinationId.equals(settlementProviderId(destinationSettlementId))) {
            progress(g, Quest.Type.ESCORT_TRADER,
                    "escort:" + traderId + ":" + stableSettlementId(destinationSettlementId), 1);
        }
    }

    public void onTraderEscorted(Game g) {
        // Trader and destination context are mandatory.
    }

    public void failMission(String missionId, String reason) {
        if (quest != null && quest.targetMissionId.equals(missionId)) {
            quest.fail(reason);
        }
    }

    private void progress(Game g, Quest.Type type, String eventId, int amount) {
        if (quest != null && quest.type == type && quest.advance(eventId, amount)) {
            g.log("Request updated: " + quest.describe());
            if (quest.readyToTurnIn()) {
                g.audio.playQuest();
            }
        }
    }

    private boolean validProvider(Npc giver, Settlement home) {
        return giver != null && home != null && !home.hostile()
                && (giver.isTrader || giver.archetype != null && giver.archetype.leader);
    }

    private boolean hasTargetFor(Game g, Npc giver, Settlement home, Quest.Type type) {
        return switch (type) {
            case FETCH, DELIVER_SUPPLIES, EXPLORE_SETTLEMENT_CAVE -> true;
            case DEFEND_VILLAGE -> closestSettlement(g, home, Settlement::hostile) != null
                    && g.settlementManager.availableNpcCapacity(g,
                    SettlementManager.NpcCategory.PATROL) > 0;
            case ESCORT_TRADER -> closestSettlement(g, home,
                    s -> s.id != home.id && s.friendly()) != null
                    && g.settlementManager.canSpawnNpc(g,
                    SettlementManager.NpcCategory.LEGACY);
            case SCOUT_HOSTILE_FORT, CAPTURE_FORT ->
                    hostileFortTarget(g, home, false,
                            type == Quest.Type.SCOUT_HOSTILE_FORT) != null;
            case RESCUE_CAPTIVE -> captiveTarget(g, home) != null;
            case CLEAR_PATROL -> closestSettlement(g, home, Settlement::hostile) != null
                    && g.settlementManager.availableNpcCapacity(g,
                    SettlementManager.NpcCategory.PATROL) > 0;
            case RECOVER_STOLEN_SUPPLIES ->
                    closestSettlement(g, home, Settlement::hostile) != null;
            case SABOTAGE_ALARM -> hostileFortTarget(g, home, true, false) != null;
            case DEFEND_OUTPOST -> closestSettlement(g, home, s -> s.occupied) != null;
            default -> false;
        };
    }

    /** Creates the actual party/trader/counterattack referenced by mission quests. */
    private boolean materializeQuestObjective(Game g, Quest q, Settlement home) {
        switch (q.type) {
            case DEFEND_VILLAGE -> {
                Settlement origin = closestSettlement(g, home, Settlement::hostile);
                if (origin == null) {
                    return false;
                }
                List<Npc> party = g.settlementManager.spawnParty(g, origin, q.required,
                        origin.factionId, home.center, Npc.PartyKind.PATROL, "", home.id);
                if (party.isEmpty()) {
                    return false;
                }
                q.required = party.size();
                q.targetMissionId = party.getFirst().partyMissionId;
                q.targetFactionId = origin.factionId;
            }
            case CLEAR_PATROL -> {
                Settlement origin = g.world.settlements.get(q.targetSettlementId);
                if (origin == null || !origin.hostile()) {
                    return false;
                }
                List<Npc> party = g.settlementManager.spawnParty(g, origin, q.required,
                        origin.factionId, home.center, Npc.PartyKind.PATROL, "", home.id);
                if (party.isEmpty()) {
                    return false;
                }
                q.required = party.size();
                q.targetMissionId = party.getFirst().partyMissionId;
                q.targetFactionId = origin.factionId;
            }
            case ESCORT_TRADER -> {
                if (!g.settlementManager.canSpawnNpc(g, SettlementManager.NpcCategory.LEGACY)) {
                    return false;
                }
                String escortId = "escort:" + q.instanceId;
                Npc escort = g.entities.spawnNpc(g.world, "Caravan trader",
                        home.center.x() + 0.5f, home.center.y() + 0.4f,
                        home.center.z() + 0.5f);
                escort.isTrader = true;
                escort.leaveTimer = q.timeLeft;
                escort.partyMissionId = escortId;
                escort.partyMemberId = escortId;
                escort.partyTargetSettlementId = q.targetSettlementId;
                q.targetMissionId = escortId;
            }
            case DEFEND_OUTPOST -> {
                Settlement outpost = g.world.settlements.get(q.targetSettlementId);
                if (outpost == null || !outpost.occupied) {
                    return false;
                }
                var mission = g.settlementManager.counterattacks.forTarget(outpost.id);
                if (mission == null) {
                    mission = g.settlementManager.dispatchCounterattack(g, outpost, 4);
                }
                if (mission == null) {
                    return false;
                }
                q.targetMissionId = mission.id;
                q.targetFactionId = mission.attackerFactionId;
            }
            case RECOVER_STOLEN_SUPPLIES -> {
                Settlement target = g.world.settlements.get(q.targetSettlementId);
                if (target == null || !target.hostile() || q.item == null) {
                    return false;
                }
                var layout = g.world.layoutFor(target);
                Map.Entry<Vec3i, Integer> targetCrate = layout.crates.entrySet().stream()
                        .min(Comparator
                                .comparingInt((Map.Entry<Vec3i, Integer> entry) ->
                                        entry.getKey().x())
                                .thenComparingInt(entry -> entry.getKey().y())
                                .thenComparingInt(entry -> entry.getKey().z()))
                        .orElse(null);
                if (targetCrate == null) {
                    return false;
                }
                Vec3i storage = targetCrate.getKey();
                Inventory contents = g.world.crateContents.computeIfAbsent(storage,
                        ignored -> SettlementBuilder.rollCrate(g.world.seed, storage,
                                targetCrate.getValue()));
                int missing = Math.max(0, q.required - contents.count(q.item));
                if (missing > 0 && contents.add(q.item, missing) != 0) {
                    return false;
                }
                q.targetPoiId = settlementStorageId(target.id, storage);
            }
            default -> {
                // Non-mission objectives already point at concrete world state.
            }
        }
        return true;
    }

    private Quest newQuest(Game g, Quest.Type type, ItemType item, int required,
                           float timeLeft, int trustReward, ItemType rewardItem,
                           int rewardCount, String giverName, String giverId,
                           long giverSettlementId, String providerId) {
        Quest created = new Quest(type, item, required, timeLeft, trustReward,
                rewardItem, rewardCount, giverName);
        long sequence = ++questSequence;
        long mixed = com.veylon.util.Noise.mix(g.world.seed
                ^ sequence * 0x9E3779B97F4A7C15L
                ^ ((long) giverId.hashCode() << 32)
                ^ type.name().hashCode());
        return created.bind("quest:" + Long.toUnsignedString(mixed, 16) + ":"
                        + Long.toUnsignedString(sequence, 16), giverId,
                giverSettlementId, providerId);
    }

    private Quest campObjective(Game g, String giverName, String giverId, Quest.Type type) {
        Quest q;
        Settlement target;
        switch (type) {
            case HUNT_PREDATOR -> q = newQuest(g, type, null, 2, 900, 12,
                    ItemType.SPEAR, 1, giverName, giverId, Quest.NO_SETTLEMENT, "camp");
            case INVESTIGATE -> q = newQuest(g, type, null, 1, 1200, 12,
                    ItemType.BLUEPRINT_FRAGMENT, 1, giverName, giverId,
                    Quest.NO_SETTLEMENT, "camp");
            case DELIVER_SUPPLIES -> q = newQuest(g, type, ItemType.IRON_ORE, 3,
                    900, 10, ItemType.COOKED_MEAT, 3, giverName, giverId,
                    Quest.NO_SETTLEMENT, "camp");
            case SCOUT_SETTLEMENT -> {
                target = nearestToCamp(g, s -> !s.discovered);
                q = newQuest(g, type, null, 1, 1500, 12,
                        ItemType.BLUEPRINT_FRAGMENT, 1, giverName, giverId,
                        Quest.NO_SETTLEMENT, "camp");
                targetSettlement(q, target);
            }
            case RESCUE_CAPTIVE -> {
                target = nearestToCamp(g, s -> s.hostile() && firstCaptiveIndex(s) >= 0);
                q = newQuest(g, type, null, 1, 2400, 20, ItemType.MEDICINE, 2,
                        giverName, giverId, Quest.NO_SETTLEMENT, "camp");
                targetSettlement(q, target);
                q.targetCaptiveId = firstCaptiveId(target);
            }
            case CLEAR_HOSTILE -> {
                target = nearestToCamp(g, s -> s.hostile() && isFort(s));
                q = newQuest(g, type, null, 1, 3000, 25, ItemType.MUSKET_BALL, 8,
                        giverName, giverId, Quest.NO_SETTLEMENT, "camp");
                targetSettlement(q, target);
            }
            case DRIVE_OFF -> {
                target = nearestToCamp(g, Settlement::hostile);
                q = newQuest(g, type, null, 3, 1800, 16, ItemType.IRON_ARROW, 6,
                        giverName, giverId, Quest.NO_SETTLEMENT, "camp");
                targetSettlement(q, target);
                q.targetMissionId = pendingMission("raid", target.id);
            }
            default -> throw new IllegalArgumentException("Unsupported camp quest " + type);
        }
        if (type == Quest.Type.HUNT_PREDATOR || type == Quest.Type.INVESTIGATE
                || type == Quest.Type.DELIVER_SUPPLIES) {
            q.targetPoiId = campRegionId(g);
        }
        return q;
    }

    private Settlement nearestToCamp(Game g, Predicate<Settlement> predicate) {
        Vec3i pos = campPos != null ? campPos : new Vec3i((int) g.player.pos.x, 0,
                (int) g.player.pos.z);
        return g.world.settlements.values().stream()
                .filter(s -> s != null && predicate.test(s))
                .min(Comparator.comparingDouble((Settlement s) -> s.distSqTo(pos.x(), pos.z()))
                        .thenComparingLong(s -> s.id))
                .orElse(null);
    }

    private Settlement closestSettlement(Game g, Settlement from,
                                         Predicate<Settlement> predicate) {
        return g.world.settlements.values().stream()
                .filter(s -> s != null && predicate.test(s))
                .min(Comparator.comparingDouble((Settlement s) ->
                                s.distSqTo(from.center.x(), from.center.z()))
                        .thenComparingLong(s -> s.id))
                .orElse(null);
    }

    private Settlement hostileFortTarget(Game g, Settlement from,
                                         boolean requireAlarm, boolean undiscoveredOnly) {
        return closestSettlement(g, from, s -> {
            if (!s.hostile() || !isFort(s) || undiscoveredOnly && s.discovered) {
                return false;
            }
            // Layout metadata is deterministic and does not load a chunk; it
            // supplies the actual bell needed for a sabotage target.
            g.world.layoutFor(s);
            return !requireAlarm || s.alarmBell != null && !s.alarmNeutralized;
        });
    }

    private Settlement captiveTarget(Game g, Settlement from) {
        return closestSettlement(g, from, s -> s.hostile() && firstCaptiveIndex(s) >= 0);
    }

    private static boolean isFort(Settlement settlement) {
        return settlement.type == SettlementType.FORT
                || settlement.type == SettlementType.CASTLE
                || settlement.type == SettlementType.FORTRESS;
    }

    private static int firstCaptiveIndex(Settlement settlement) {
        if (settlement == null) {
            return -1;
        }
        for (int i = 0; i < settlement.residents.size(); i++) {
            Settlement.Resident resident = settlement.residents.get(i);
            if (resident.archetype == NpcArchetype.CAPTIVE && resident.alive
                    && !resident.rescued) {
                return i;
            }
        }
        return -1;
    }

    private static String firstCaptiveId(Settlement settlement) {
        return captiveId(settlement, firstCaptiveIndex(settlement));
    }

    public static String captiveId(Settlement settlement, int residentIndex) {
        if (settlement == null || residentIndex < 0
                || residentIndex >= settlement.residents.size()) {
            return "";
        }
        Settlement.Resident resident = settlement.residents.get(residentIndex);
        return "captive:" + stableSettlementId(settlement.id) + ":" + residentIndex
                + ":" + slug(resident.name);
    }

    public static String stableNpcId(Npc npc, long homeSettlementId) {
        if (npc == null) {
            return "";
        }
        if (npc.residentIndex >= 0) {
            return "npc:" + stableSettlementId(homeSettlementId) + ":resident:"
                    + npc.residentIndex;
        }
        return "npc:" + stableSettlementId(homeSettlementId) + ":" + slug(npc.name);
    }

    public static String settlementProviderId(long settlementId) {
        return "settlement:" + stableSettlementId(settlementId);
    }

    public static String settlementStorageId(long settlementId) {
        return "storage:" + stableSettlementId(settlementId);
    }

    /** Stable identity for one generated settlement crate. */
    public static String settlementStorageId(long settlementId, Vec3i position) {
        if (position == null) {
            return settlementStorageId(settlementId);
        }
        return settlementStorageId(settlementId) + ":" + position.x() + ":"
                + position.y() + ":" + position.z();
    }

    private static boolean storageTargetMatches(Quest q, Settlement settlement,
                                                String eventStorageId) {
        if (q == null || settlement == null || eventStorageId == null) {
            return false;
        }
        if (q.targetPoiId.equals(eventStorageId)) {
            return true;
        }
        // Pre-extension v3 quests used only the settlement-wide storage ID.
        // Keep those saves completable inside their bound settlement without
        // weakening exact matching for newly issued coordinate-bound quests.
        String legacy = settlementStorageId(settlement.id);
        return q.targetPoiId.equals(legacy)
                && eventStorageId.startsWith(legacy + ":");
    }

    private static String stableSettlementId(long settlementId) {
        return Long.toUnsignedString(settlementId, 16);
    }

    private static String pendingMission(String kind, long settlementId) {
        return "pending:" + kind + ":" + stableSettlementId(settlementId);
    }

    private static void targetSettlement(Quest q, Settlement target) {
        q.targetSettlementId = target.id;
        q.targetFactionId = target.factionId == null ? "" : target.factionId;
    }

    /** Accepted target coordinates become a generic rumor without revealing hidden metadata. */
    private static void markQuestTargetRumored(Game g, Quest q) {
        if (q == null || q.targetSettlementId == Quest.NO_SETTLEMENT) {
            return;
        }
        Settlement target = g.world.settlements.get(q.targetSettlementId);
        if (target != null && !target.discovered) {
            target.rumored = true;
        }
    }

    private static String caveRegionId(int regionX, int regionZ) {
        return "cave-region:" + regionX + ":" + regionZ;
    }

    private static String campRegionId(Game g) {
        Vec3i pos = g.faction.campPos != null ? g.faction.campPos
                : new Vec3i((int) Math.floor(g.player.pos.x), 0,
                (int) Math.floor(g.player.pos.z));
        return "camp-region:" + SettlementPlanner.regionOfBlock(pos.x()) + ":"
                + SettlementPlanner.regionOfBlock(pos.z());
    }

    public static String poiId(Poi poi) {
        return "poi:" + poi.type.name().toLowerCase(Locale.ROOT) + ":"
                + poi.pos.x() + ":" + poi.pos.y() + ":" + poi.pos.z();
    }

    private static boolean isUndergroundPoi(Poi.PoiType type) {
        return type == Poi.PoiType.ABANDONED_MINE
                || type == Poi.PoiType.SMUGGLER_CACHE
                || type == Poi.PoiType.HIDEOUT_CAVE
                || type == Poi.PoiType.RESONANT_SHRINE
                || type == Poi.PoiType.STALKER_NEST
                || type == Poi.PoiType.EXPEDITION_CAMP;
    }

    private boolean matchesSettlement(Quest.Type type, Settlement settlement) {
        return quest != null && quest.type == type && settlement != null
                && quest.targetSettlementId == settlement.id;
    }

    private static boolean factionMatches(Quest q, String factionId) {
        if (q.targetFactionId.isBlank()) {
            if (factionId != null && !factionId.isBlank()) {
                q.targetFactionId = factionId;
            }
            return true;
        }
        return q.targetFactionId.equals(factionId);
    }

    private static boolean missionMatchesOrBinds(Quest q, String missionId) {
        if (q == null || missionId == null || missionId.isBlank()) {
            return false;
        }
        if (q.targetMissionId.startsWith("pending:")) {
            q.targetMissionId = missionId;
            return true;
        }
        return q.targetMissionId.equals(missionId);
    }

    private boolean prepareDelivery(Game g, Settlement settlement) {
        boolean recovered = quest.type == Quest.Type.RECOVER_STOLEN_SUPPLIES;
        if (quest.type != Quest.Type.FETCH && quest.type != Quest.Type.DELIVER_SUPPLIES
                && !recovered) {
            return true;
        }
        if (!recovered && quest.readyToTurnIn()) {
            return true;
        }
        if (quest.item == null || !g.player.inventory.has(quest.item, quest.required)) {
            return false;
        }
        g.player.inventory.remove(quest.item, quest.required);
        if (settlement != null) {
            if (quest.item == ItemType.MEDICINE) {
                settlement.medStock += quest.required;
            } else if (quest.item.isEdible()) {
                settlement.foodStock += quest.required;
            } else if (quest.item == ItemType.LOG) {
                settlement.woodStock += quest.required;
            } else {
                settlement.metalStock += quest.required;
            }
        } else if (quest.item == ItemType.MEDICINE) {
            for (Npc n : g.entities.npcs) {
                if (n.sick && !n.settled() && !n.raider) {
                    n.sick = false;
                    n.mood = Math.min(100, n.mood + 20);
                    g.log(n.name + " takes the medicine and starts to recover.");
                    break;
                }
            }
        } else if (quest.item.isEdible()) {
            foodStock += quest.required;
        } else if (quest.item == ItemType.LOG) {
            woodStock += quest.required;
        }
        if (!recovered) {
            quest.advance("delivery:" + quest.instanceId, quest.required);
        }
        return true;
    }

    private String deliveryMissingText(Game g) {
        if (quest.item == null) {
            return "Not done yet: " + quest.describe();
        }
        return "We still need " + Math.max(0,
                quest.required - g.player.inventory.count(quest.item))
                + " more " + quest.item.displayName + ".";
    }

    private String nonRewardingTerminalState() {
        if (quest.status == Quest.Status.LEGACY_UNBOUND) {
            quest = null;
            questCooldown = 0;
            return "The old request has no verifiable target, so it was withdrawn without reward. Ask again for a marked request.";
        }
        if (quest.status == Quest.Status.EXPIRED || quest.status == Quest.Status.FAILED) {
            String reason = quest.failureReason.isBlank() ? quest.status.name().toLowerCase(Locale.ROOT)
                    : quest.failureReason;
            quest = null;
            questCooldown = 30;
            return "That request is closed (" + reason + ").";
        }
        return null;
    }

    private void rewardCampQuest(Game g) {
        if (quest.rewardClaimed) {
            return;
        }
        quest.rewardClaimed = true;
        quest.status = Quest.Status.COMPLETED;
        addTrust(g, quest.trustReward, "Camp request fulfilled");
        if (quest.rewardItem != null && quest.rewardCount > 0) {
            g.player.inventory.add(quest.rewardItem, quest.rewardCount);
            g.log("Reward: " + quest.rewardCount + "x " + quest.rewardItem.displayName);
        }
        g.audio.playQuest();
        clearCompletedQuest();
    }

    private void clearCompletedQuest() {
        quest = null;
        questCooldown = 90 + rng.nextInt(90);
    }

    private static String slug(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        return normalized.replaceAll("(^-+|-+$)", "");
    }

    // ------------------------------------------------------------------
    // Slow tick: stocks, fire upkeep, sickness, quests, upgrades
    // ------------------------------------------------------------------

    public void slowTick(Game g, float dt) {
        alert = Math.max(0, alert - 1.5f * dt);
        questCooldown = Math.max(0, questCooldown - dt);
        buildTimer = Math.max(0, buildTimer - dt);
        if (quest != null && (quest.status == Quest.Status.ACTIVE
                || quest.status == Quest.Status.READY_TO_TURN_IN)) {
            quest.timeLeft -= dt;
            if (quest.timeLeft <= 0) {
                g.log("The camp's request expired (" + quest.describe() + ").");
                quest.expire();
                cleanupExpiredObjective(g, quest);
            }
        }
        if (campPos == null) {
            return;
        }

        // NPCs eat from the food stock about once per game hour each.
        foodTimer += dt;
        int campNpcs = 0;
        for (Npc n : g.entities.npcs) {
            if (!n.isTrader && !n.raider && !n.dead) {
                campNpcs++;
            }
        }
        if (foodTimer > 75 && campNpcs > 0) {
            foodTimer = 0;
            if (foodStock > 0) {
                foodStock--;
                for (Npc n : g.entities.npcs) {
                    if (!n.isTrader && !n.raider) {
                        n.hunger = Math.max(0, n.hunger - 25f / campNpcs);
                        n.mood = Math.min(100, n.mood + 2);
                    }
                }
            } else {
                for (Npc n : g.entities.npcs) {
                    if (!n.isTrader && !n.raider) {
                        n.mood = Math.max(0, n.mood - 5);
                    }
                }
            }
        }

        // Sick NPCs slowly worsen; they can die untreated.
        for (Npc n : g.entities.npcs) {
            if (n.sick && !n.dead) {
                n.sickTimer += dt;
                n.mood = Math.max(0, n.mood - 0.5f * dt * 0.1f);
                if (n.sickTimer > 600) {
                    n.health -= 0.4f * dt;
                    if (n.health <= 0) {
                        n.dead = true;
                        n.lastHitByPlayer = false;
                        g.log(n.name + " succumbed to the illness. The camp mourns.");
                        addTrust(g, -5, null);
                    }
                }
            }
        }

        // Keep the camp fire fueled from the wood stock.
        fireTimer += dt;
        if (fireTimer > 60) {
            fireTimer = 0;
            Vec3i firePos = campPos;
            if (g.world.getBlock(firePos.x(), firePos.y(), firePos.z()) == BlockType.CAMPFIRE) {
                float fuel = g.world.campfireFuel.getOrDefault(firePos, 0f);
                if (fuel < 120 && woodStock > 0) {
                    woodStock--;
                    g.world.campfireFuel.put(firePos, fuel + 180);
                }
            } else if (woodStock >= 3) {
                // Rebuild a burned-out camp fire.
                woodStock -= 3;
                g.world.setBlock(firePos.x(), firePos.y(), firePos.z(), BlockType.CAMPFIRE, true);
                g.world.campfireFuel.put(firePos, 300f);
            }
        }

        checkUpgrade(g);
    }

    /**
     * Removes entities created solely for an expired request. Persistent world
     * events such as an outpost counterattack continue independently, while
     * temporary escort/raid actors immediately release their bounded NPC slots.
     */
    private void cleanupExpiredObjective(Game g, Quest expired) {
        if (expired == null) {
            return;
        }
        if (expired.type == Quest.Type.RECOVER_STOLEN_SUPPLIES
                && expired.item != null && expired.targetSettlementId != Quest.NO_SETTLEMENT) {
            for (Map.Entry<Vec3i, Inventory> entry : g.world.crateContents.entrySet()) {
                if (settlementStorageId(expired.targetSettlementId, entry.getKey())
                        .equals(expired.targetPoiId)) {
                    entry.getValue().remove(expired.item, expired.required);
                    break;
                }
            }
            return;
        }
        if (expired.targetMissionId == null || expired.targetMissionId.isBlank()) {
            return;
        }
        switch (expired.type) {
            case DEFEND_VILLAGE, CLEAR_PATROL, DRIVE_OFF, ESCORT_TRADER ->
                    g.entities.npcs.removeIf(n -> expired.targetMissionId.equals(
                            n.partyMissionId));
            default -> {
                // Captures, rescues, POIs, and counterattacks are persistent
                // world state rather than quest-owned temporary actors.
            }
        }
    }

    /** Builds the next settlement stage when trust and wood allow it. */
    private void checkUpgrade(Game g) {
        int cx = campPos.x(), cy = campPos.y(), cz = campPos.z();
        if (upgradeStage == 0 && trust >= 45 && woodStock >= 18) {
            woodStock -= 14;
            buildPalisade(g, cx, cy, cz);
            upgradeStage = 1;
            buildTimer = 60;
            g.log("CAMP UPGRADE: The camp raises a palisade and a watch post!");
            g.audio.playQuest();
        } else if (upgradeStage == 1 && trust >= 60 && woodStock >= 28) {
            woodStock -= 20;
            buildComforts(g, cx, cy, cz);
            upgradeStage = 2;
            buildTimer = 60;
            g.log("CAMP UPGRADE: Beds, storage and a drying rack - the camp grows!");
            g.audio.playQuest();
        } else if (upgradeStage == 2 && trust >= 75 && woodStock >= 34) {
            woodStock -= 24;
            buildMedTent(g, cx, cy, cz);
            upgradeStage = 3;
            buildTimer = 60;
            g.log("CAMP UPGRADE: A medical tent with an herbalist bench is built!");
            g.audio.playQuest();
        }
    }

    private void buildPalisade(Game g, int cx, int cy, int cz) {
        for (int d = -6; d <= 6; d++) {
            // Ring with a gate gap on the south side.
            if (Math.abs(d) <= 1) {
                placeWall(g, cx + d, cz - 6);
            } else {
                placeWall(g, cx + d, cz - 6);
                placeWall(g, cx + d, cz + 6);
            }
            if (Math.abs(d) < 6) {
                placeWall(g, cx - 6, cz + d);
                placeWall(g, cx + 6, cz + d);
            }
        }
        // Watch post: a torch tower at the corner.
        int h = surfaceAt(g, cx + 5, cz + 5);
        for (int i = 1; i <= 3; i++) {
            g.world.setBlock(cx + 5, h + i, cz + 5, BlockType.WALL, true);
        }
        g.world.setBlock(cx + 5, h + 4, cz + 5, BlockType.TORCH, true);
    }

    private void buildComforts(Game g, int cx, int cy, int cz) {
        g.world.setBlock(cx - 3, cy, cz + 1, BlockType.CAMP_BED, true);
        g.world.setBlock(cx - 3, cy, cz - 1, BlockType.CAMP_BED, true);
        g.world.setBlock(cx + 3, cy, cz + 1, BlockType.CRATE, true);
        g.world.crateContents.putIfAbsent(new Vec3i(cx + 3, cy, cz + 1),
                new com.veylon.item.Inventory(12));
        g.world.setBlock(cx + 4, cy, cz - 1, BlockType.DRYING_RACK, true);
        g.world.setBlock(cx, cy, cz + 4, BlockType.CAMPFIRE, true);
        g.world.campfireFuel.putIfAbsent(new Vec3i(cx, cy, cz + 4), 300f);
    }

    private void buildMedTent(Game g, int cx, int cy, int cz) {
        // Small walled tent with herbalist bench and bed.
        for (int dx = -5; dx <= -3; dx++) {
            g.world.setBlock(dx + cx, cy, cz - 5, BlockType.WALL, true);
            g.world.setBlock(dx + cx, cy + 1, cz - 5, BlockType.WALL, true);
            g.world.setBlock(dx + cx, cy + 2, cz - 4, BlockType.PLANK, true);
            g.world.setBlock(dx + cx, cy + 2, cz - 3, BlockType.PLANK, true);
        }
        g.world.setBlock(cx - 5, cy, cz - 4, BlockType.WALL, true);
        g.world.setBlock(cx - 5, cy + 1, cz - 4, BlockType.WALL, true);
        g.world.setBlock(cx - 4, cy, cz - 4, BlockType.HERB_STATION, true);
        g.world.setBlock(cx - 3, cy, cz - 3, BlockType.CAMP_BED, true);
        g.world.setBlock(cx - 5, cy + 1, cz - 3, BlockType.TORCH, true);
    }

    private void placeWall(Game g, int x, int z) {
        int h = surfaceAt(g, x, z);
        g.world.setBlock(x, h + 1, z, BlockType.WALL, true);
        g.world.setBlock(x, h + 2, z, BlockType.WALL, true);
    }

    private int surfaceAt(Game g, int x, int z) {
        return g.world.surfaceHeight(x, z);
    }
}
