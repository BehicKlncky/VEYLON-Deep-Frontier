package com.veylon.entity;

import com.veylon.ai.FactionSystem;
import com.veylon.settlement.HumanFaction;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.util.Vec3i;
import com.veylon.world.World;
import org.joml.Vector3f;

public class Npc extends Entity {

    public enum NpcState {
        IDLE, GATHER_FOOD, GATHER_WOOD, HUNT, HEAL, BUILD,
        WARM_BY_FIRE, SLEEP, GUARD, FLEE, ATTACK, TRADE, RAID
    }

    /** Explicit lifecycle for settlement patrols and bounty parties. */
    public enum PartyMission {
        OUTBOUND, SEARCHING, RETURNING
    }

    /** Stable category used for runtime budgets and persisted mission identity. */
    public enum PartyKind {
        PATROL, BOUNTY_HUNTER, COUNTERATTACK
    }

    public final String name;
    public NpcState state = NpcState.IDLE;
    /** 0..100. */
    public float mood = 65;
    public float hunger = 20;
    public boolean isTrader;
    /** Hostile scavenger attacking the camp during raids. */
    public boolean raider;
    /** Sick NPCs work slowly and need medicine (NPC illness event). */
    public boolean sick;
    public float sickTimer;
    /** Null for wandering traders and raiders. */
    public FactionSystem faction;
    /** Index within the camp; used to assign jobs (0 guard, 1 hunter, 2 gatherer, 3 medic). */
    public int campIndex;

    // ---- 0.3.0 settlement residency (persisted in save v3) ----
    /** Archetype for settlement NPCs; null for legacy camp/trader/raider NPCs. */
    public com.veylon.settlement.NpcArchetype archetype;
    /** Home settlement id (0 = none). */
    public long settlementId;
    /** Index into the home settlement's resident list (-1 = none). */
    public int residentIndex = -1;
    /** Loaded rounds in a ranged weapon (NPC-side). */
    public int loadedAmmo;
    /** Remaining seconds of the current reload/draw. */
    public float reloadTimer;
    /** Perception: last known player position while alerted. */
    public final Vector3f lastKnown = new Vector3f();
    public float lastKnownAge = 999f;
    /** Seconds left of active searching before returning to duty. */
    public float searchTimer;
    /** Current path (world cell centers) and progress index. */
    public java.util.List<Vec3i> path;
    public int pathIndex;
    public float repathCooldown;
    /** Patrol progress for guards/patrollers. */
    public int patrolIndex;
    /** Set while this NPC belongs to a traveling war/hunter party. */
    public boolean warParty;
    /** Party budget/category. Existing saves default safely to a regular patrol. */
    public PartyKind partyKind = PartyKind.PATROL;
    /** Stable shared mission id; blank for legacy patrols. */
    public String partyMissionId = "";
    /** Stable identity within a party, used for exactly-once quest credit. */
    public String partyMemberId = "";
    /** Settlement objective for target-bound missions, or 0 when not applicable. */
    public long partyTargetSettlementId;
    /** Actual faction side for a traveling party (stable id, not an archetype guess). */
    public String partyFactionId;
    /** Origin retained even while a party is away from its settlement. */
    public long originSettlementId;
    /** Patrol objective and phase, persisted by stable enum name. */
    public PartyMission partyMission = PartyMission.OUTBOUND;
    public final Vector3f partyDestination = new Vector3f();
    public float partyMissionTimer;
    public boolean partyContact;
    /** Coarse travel avoids pathfinding or physics in unloaded, distant chunks. */
    public boolean abstractTravel;
    public float abstractTravelTick;

    public final Vector3f target = new Vector3f();
    public boolean hasTarget;
    public Vec3i targetBlock;
    public float decideTimer;
    public float workTimer;
    public float attackCooldown;
    /** While > 0 the NPC stands still (talking/trading with the player). */
    public float interactFreeze;
    /** Seconds before a trader or raider leaves. */
    public float leaveTimer;
    public Entity combatTarget;
    /** Animation phase for walking bob. */
    public float bobPhase;

    public Npc(World world, String name) {
        super(world);
        this.name = name;
        width = 0.55f;
        height = 1.75f;
        maxHealth = 35;
        health = 35;
    }

    public boolean hostileToPlayer() {
        // Captives are protected prisoners, not members of the faction that
        // happens to hold them.  Keep this before settlement ownership so a
        // captive inside a hostile fort remains interactable and never becomes
        // a combat target merely because its resident record lives there.
        if (archetype == NpcArchetype.CAPTIVE) {
            return false;
        }
        if (settled()) {
            Settlement s = world.settlements.get(settlementId);
            if (s != null) {
                return s.hostile();
            }
        }
        if (warParty) {
            return true;
        }
        return raider || (faction != null && faction.hostile);
    }

    /** Runtime side used by targeting and friendly-fire checks. */
    public String sideId() {
        if (archetype == NpcArchetype.CAPTIVE) {
            return "protected-captive";
        }
        if (settled()) {
            Settlement s = world.settlements.get(settlementId);
            if (s != null) {
                return s.factionId;
            }
        }
        if (partyFactionId != null && !partyFactionId.isBlank()) {
            return partyFactionId;
        }
        if (raider || (archetype != null && archetype.hostileArchetype())) {
            return HumanFaction.SCAVENGERS;
        }
        if (faction != null) {
            return HumanFaction.FRONTIER;
        }
        return "independent";
    }

    public boolean alliedWith(Npc other) {
        return other != null && sideId().equals(other.sideId());
    }

    /** Coherent combatant answer after runtime ownership/alignment changes. */
    public boolean combatant() {
        if (archetype == NpcArchetype.CAPTIVE) {
            return false;
        }
        if (hostileToPlayer()) {
            return true;
        }
        return archetype == NpcArchetype.GUARD || archetype == NpcArchetype.ARCHER
                || archetype == NpcArchetype.SMITH;
    }

    /** Whether this live resident currently counts toward its settlement objective. */
    public boolean countsAsSettlementDefender() {
        return settled() && combatant() && hostileToPlayer() && !dead;
    }

    /** True for settlement residents (as opposed to legacy camp/trader/raider NPCs). */
    public boolean settled() {
        // Region (0,0) legitimately packs to id 0, so id 0 cannot be a sentinel.
        // Resident index distinguishes real residents from traveling parties.
        return archetype != null && residentIndex >= 0;
    }

    public String jobName() {
        if (archetype != null) {
            return archetype.displayName;
        }
        if (isTrader) {
            return "Trader";
        }
        if (raider) {
            return "Scavenger";
        }
        return switch (campIndex % 4) {
            case 0 -> "Guard";
            case 1 -> "Hunter";
            case 2 -> "Gatherer";
            default -> "Medic";
        };
    }
}
