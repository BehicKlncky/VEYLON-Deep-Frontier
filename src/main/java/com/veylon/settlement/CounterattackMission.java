package com.veylon.settlement;

import com.veylon.util.Vec3i;

/**
 * Persistent state for one hostile attempt to retake an occupied outpost.
 *
 * <p>The mission, rather than any one live NPC, owns travel and resolution.
 * This lets distant parties remain abstract without loading chunks and lets
 * active members be materialized/de-materialized without losing the target,
 * outcome, survivor count, or quest identity.</p>
 */
public final class CounterattackMission {

    /** Stable phase names are persisted in the optional v3 save tail. */
    public enum Phase {
        DISPATCH,
        OUTBOUND,
        APPROACH,
        ASSAULT,
        RETREAT,
        CLEANUP
    }

    /** An outcome is applied exactly once before cleanup. */
    public enum Outcome {
        UNRESOLVED,
        DEFENDER_VICTORY,
        ATTACKER_VICTORY
    }

    /** No registered settlement; the persisted regional-force position is the origin. */
    public static final long REGIONAL_FORCE_ORIGIN = Long.MIN_VALUE;

    public final String id;
    public final long originSettlementId;
    public final long targetSettlementId;
    public final String attackerFactionId;
    public final Vec3i origin;
    public final Vec3i target;
    public final long resolutionSeed;
    public final int initialAttackers;

    public Phase phase = Phase.DISPATCH;
    public Outcome outcome = Outcome.UNRESOLVED;
    public float phaseTimer = 1f;
    public int survivors;
    /** Coarse party position used while no live members exist. */
    public float x;
    public float y;
    public float z;
    /** Guards ownership/reward changes against duplicate ticks or reloads. */
    public boolean outcomeApplied;

    public CounterattackMission(String id, long originSettlementId, long targetSettlementId,
                                String attackerFactionId, Vec3i origin, Vec3i target,
                                long resolutionSeed, int initialAttackers) {
        this.id = id;
        this.originSettlementId = originSettlementId;
        this.targetSettlementId = targetSettlementId;
        this.attackerFactionId = attackerFactionId;
        this.origin = origin;
        this.target = target;
        this.resolutionSeed = resolutionSeed;
        this.initialAttackers = initialAttackers;
        this.survivors = initialAttackers;
        this.x = origin.x() + 0.5f;
        this.y = origin.y() + 0.4f;
        this.z = origin.z() + 0.5f;
    }

    public boolean resolved() {
        return outcome != Outcome.UNRESOLVED;
    }
}
