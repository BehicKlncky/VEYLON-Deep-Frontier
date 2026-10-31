package com.veylon.entity;

import com.veylon.ai.FactionSystem;
import com.veylon.ai.PanicIntent;
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
    /** Final hierarchical destination; never serialized. */
    public Vec3i navigationGoal;
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
    /** Non-persisted deterministic diagnostic: completed settled-NPC perception queries. */
    public long perceptionChecks;
    public float workTimer;
    public float attackCooldown;
    /** While > 0 the NPC stands still (talking/trading with the player). */
    public float interactFreeze;
    /** Seconds before a trader or raider leaves. */
    public float leaveTimer;
    public Entity combatTarget;
    /** Animation phase for walking bob. */
    public float bobPhase;
    /**
     * Torso wound units from bullets and arrows; the NPC dies when they reach
     * {@code ProjectileLethality.LETHAL_TORSO_WOUNDS}. Healing does not clear
     * them. Transient: not saved, reset only by a new {@code Npc} instance.
     */
    public int torsoWounds;
    /**
     * Shot id of the last trigger pull or bow release that wounded this torso,
     * so the other pellets of that shot do not count again; -1 before any.
     * Transient: not saved, reset only by a new {@code Npc} instance.
     */
    public int lastTorsoShotId = -1;
    /** Flight from the fire on this body; written only by the AI. Transient. */
    public final PanicIntent panic = new PanicIntent();

    /** Stable generated encounter slot; zero for an unmanaged fixture body. */
    public long encounterId;
    /** Infected combat intent is transient; health and torso wounds are durable in the encounter ledger. */
    public float strikeWindup = -1f, painTimer;
    public boolean painSound;
    public com.veylon.combat.WorldNoise.NoiseEvent lastHeard;
    /**
     * A Breacher's door (urban milestone 15): the bottom cell of the closed wooden door on its
     * route that it is hammering down, the seconds to its next blow and the blows landed on the
     * door itself. From urban milestone 18 the target may also be a plank barricade cell, and a
     * door's brace takes blows before the door does; {@link #breachStruck} counts every blow on the
     * target. Transient intent: a load, a departure or a lost scent forgets it, and the door keeps
     * only what the blows did, which is nothing until the last one forces it (a barricade's and a
     * brace's lost strength is kept by the world).
     */
    public Vec3i breachDoor;
    public float breachTimer;
    public int breachBlows;
    public int breachStruck;

    public Npc(World world, String name) {
        super(world);
        this.name = name;
        width = 0.55f;
        height = 1.75f;
        maxHealth = 35;
        health = 35;
    }

    /** People walk round torches and campfires rather than through them. */
    @Override
    protected boolean keepsOutOfFlames() {
        return true;
    }

    /**
     * In a city people climb stairs of full-block steps by walking, as the
     * player does (D-31): the jump that gets them up a ledge clears a step by
     * only about 0.05 of a block. The path finder already demands the headroom
     * a step-up needs. Legacy worlds keep the jump, unchanged.
     */
    @Override
    protected float stepHeight() {
        return world.isUrban() ? URBAN_STEP_HEIGHT : 0f;
    }

    /** Step-up of a person in an urban world: one full stair step. */
    public static final float URBAN_STEP_HEIGHT = 1f;

    public boolean hostileToPlayer() {
        if (infected()) { return true; }
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
        if (infected()) { return "urban.infected"; }
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
        return !infected() && archetype != null && residentIndex >= 0;
    }

    /** Species profile uses the shared humanoid appearance codec, not settlement ownership. */
    public boolean infected() { return archetype != null && archetype.infected(); }

    /**
     * Layout seventeen (urban milestone 23): the seconds after a flinch in which an infected body shrugs
     * off further pain, so a strike wound up under a rain of blows still lands (decision D-97's finding).
     */
    public static final float PAIN_GUARD_SECONDS = 1.2f;
    /** Seconds left in which a fresh blow does not flinch this infected body again; transient. */
    public float painGuard;

    @Override
    public void hurt(float damage, boolean byPlayer) {
        boolean living = !dead;
        super.hurt(damage, byPlayer);
        if (living && !dead && damage > 0 && infected()) {
            painSound = true;
            if (painGuard <= 0f) {
                painTimer = Math.max(painTimer, 0.35f);
                strikeWindup = -1f;
                if (world != null && world.urban != null && world.urban.hasEconomy()) { painGuard = PAIN_GUARD_SECONDS; }
            }
        }
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
