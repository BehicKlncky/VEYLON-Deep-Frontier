package com.veylon.ai;

import com.veylon.item.ItemType;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One concrete request accepted from a quest provider.
 *
 * <p>{@link Type} remains ordinal-persisted for compatibility, so its members
 * are append-only. All extensible identity/state added in 0.3.0 is represented
 * by stable strings or explicit longs and is written by the optional save
 * extension.</p>
 */
public class Quest {

    public enum Type {
        FETCH("Delivery"),
        HUNT_PREDATOR("Hunt"),
        INVESTIGATE("Scout"),
        // ---- 0.3.0 settlement quests (append-only; ordinal is persisted) ----
        SCOUT_SETTLEMENT("Survey"),
        RESCUE_CAPTIVE("Rescue"),
        CLEAR_HOSTILE("Assault"),
        DRIVE_OFF("Defense"),
        // Additional 0.3.0 categories — append only; ordinals are serialized.
        DELIVER_SUPPLIES("Relief delivery"),
        DEFEND_VILLAGE("Village defense"),
        ESCORT_TRADER("Escort"),
        SCOUT_HOSTILE_FORT("Fort reconnaissance"),
        CLEAR_PATROL("Patrol clearance"),
        SABOTAGE_ALARM("Sabotage"),
        RECOVER_STOLEN_SUPPLIES("Recovery"),
        CAPTURE_FORT("Capture"),
        DEFEND_OUTPOST("Outpost defense"),
        EXPLORE_SETTLEMENT_CAVE("Cave expedition");

        public final String displayName;

        Type(String displayName) {
            this.displayName = displayName;
        }
    }

    /** Stored by name in the optional save extension, never by ordinal. */
    public enum Status {
        ACTIVE,
        READY_TO_TURN_IN,
        COMPLETED,
        FAILED,
        EXPIRED,
        /** A v2/pre-target-v3 quest that cannot safely be attached to a new target. */
        LEGACY_UNBOUND;

        public static Status byName(String name) {
            for (Status status : values()) {
                if (status.name().equals(name)) {
                    return status;
                }
            }
            return LEGACY_UNBOUND;
        }
    }

    public static final long NO_SETTLEMENT = Long.MIN_VALUE;
    private static final int MAX_CREDITED_EVENTS = 64;

    public Type type;
    /** Item to deliver for FETCH/delivery quests. */
    public ItemType item;
    public int required;
    public int progress;
    /** Real seconds before the request expires. */
    public float timeLeft;
    public int trustReward;
    public ItemType rewardItem;
    public int rewardCount;
    public String giverName;

    // ---- Targeted 0.3.0 identity/state (optional save extension) ----
    public String instanceId = "";
    public Status status = Status.LEGACY_UNBOUND;
    public String giverId = "";
    public long giverSettlementId = NO_SETTLEMENT;
    /** Provider id authorized to turn this request in (camp or one settlement). */
    public String rewardProviderId = "";
    public long targetSettlementId = NO_SETTLEMENT;
    public String targetFactionId = "";
    public String targetMissionId = "";
    public String targetCaptiveId = "";
    public String targetPoiId = "";
    public String destinationId = "";
    public String failureReason = "";
    public boolean rewardClaimed;
    /** Bounded event identities prevent duplicate progress/reward after retries. */
    public final Set<String> creditedEvents = new LinkedHashSet<>();

    /**
     * Compatibility constructor. Callers loading a core-only historical quest
     * intentionally get {@link Status#LEGACY_UNBOUND}; a target extension or a
     * newly-created offer must explicitly bind it before gameplay can progress.
     */
    public Quest(Type type, ItemType item, int required, float timeLeft,
                 int trustReward, ItemType rewardItem, int rewardCount, String giverName) {
        this.type = type;
        this.item = item;
        this.required = Math.max(1, required);
        this.timeLeft = timeLeft;
        this.trustReward = trustReward;
        this.rewardItem = rewardItem;
        this.rewardCount = rewardCount;
        this.giverName = giverName == null ? "" : giverName;
    }

    /** Marks a newly offered quest as safely target-bound and active. */
    public Quest bind(String instanceId, String giverId, long giverSettlementId,
                      String rewardProviderId) {
        this.instanceId = nonNull(instanceId);
        this.giverId = nonNull(giverId);
        this.giverSettlementId = giverSettlementId;
        this.rewardProviderId = nonNull(rewardProviderId);
        this.status = Status.ACTIVE;
        return this;
    }

    public boolean active() {
        return status == Status.ACTIVE || status == Status.READY_TO_TURN_IN;
    }

    public boolean complete() {
        return progress >= required;
    }

    public boolean readyToTurnIn() {
        return status == Status.READY_TO_TURN_IN || active() && complete();
    }

    /** Applies a uniquely identified matching gameplay event at most once. */
    public boolean advance(String eventId, int amount) {
        if (status != Status.ACTIVE || amount <= 0 || eventId == null || eventId.isBlank()
                || creditedEvents.contains(eventId)) {
            return false;
        }
        if (creditedEvents.size() >= MAX_CREDITED_EVENTS) {
            // Every current quest requires at most a handful of events. Refuse
            // unbounded input rather than letting malformed missions grow saves.
            return false;
        }
        creditedEvents.add(eventId);
        progress = Math.min(required, progress + amount);
        if (complete()) {
            status = Status.READY_TO_TURN_IN;
        }
        return true;
    }

    public void fail(String reason) {
        if (status == Status.ACTIVE || status == Status.READY_TO_TURN_IN) {
            status = Status.FAILED;
            failureReason = nonNull(reason);
        }
    }

    public void expire() {
        if (status == Status.ACTIVE || status == Status.READY_TO_TURN_IN) {
            status = Status.EXPIRED;
            failureReason = "expired";
        }
    }

    public boolean providerMatches(String providerId) {
        return !rewardProviderId.isBlank() && rewardProviderId.equals(providerId);
    }

    public String describe() {
        String task = switch (type) {
            case FETCH -> "Bring " + required + "x " + item.displayName;
            case HUNT_PREDATOR -> "Kill " + required + " predator(s) near the camp";
            case INVESTIGATE -> "Discover the marked point of interest";
            case SCOUT_SETTLEMENT -> "Discover the marked frontier settlement";
            case RESCUE_CAPTIVE -> "Rescue the marked captive from hostile custody";
            case CLEAR_HOSTILE -> "Clear the marked hostile fort, castle or fortress";
            case DRIVE_OFF -> "Cut down " + required + " member(s) of the marked raiding party";
            case DELIVER_SUPPLIES -> "Deliver " + required + "x " + item.displayName;
            case DEFEND_VILLAGE -> "Defeat " + required + " attacker(s) at the marked village";
            case ESCORT_TRADER -> "Escort the named trader to the marked destination";
            case SCOUT_HOSTILE_FORT -> "Discover and scout the marked hostile fort";
            case CLEAR_PATROL -> "Clear " + required + " member(s) of the marked hostile patrol";
            case SABOTAGE_ALARM -> "Destroy the marked hostile settlement's alarm bell";
            case RECOVER_STOLEN_SUPPLIES -> "Recover supplies from the marked hostile stores";
            case CAPTURE_FORT -> "Supply and occupy the marked cleared hostile fort";
            case DEFEND_OUTPOST -> "Repel the marked attack on the occupied outpost";
            case EXPLORE_SETTLEMENT_CAVE -> "Explore the marked cave region beneath the settlement";
        };
        String suffix = switch (status) {
            case LEGACY_UNBOUND -> "  [old request: return to its provider to reissue]";
            case FAILED -> "  [failed]";
            case EXPIRED -> "  [expired]";
            default -> "  (" + progress + "/" + required + ")";
        };
        return task + suffix;
    }

    public String rewardText() {
        return "+" + trustReward + " reputation"
                + (rewardItem != null ? ", " + rewardCount + "x " + rewardItem.displayName : "");
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }
}
