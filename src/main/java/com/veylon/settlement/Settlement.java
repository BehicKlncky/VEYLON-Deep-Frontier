package com.veylon.settlement;

import com.veylon.entity.Npc;
import com.veylon.util.Vec3i;

import java.util.ArrayList;
import java.util.List;

/**
 * One planned settlement: deterministic static layout metadata (re-derived
 * from the world seed) plus dynamic state (alignment, stocks, residents,
 * capture) that save v3 persists.
 *
 * <p>Identity is the packed region coordinate, stable across sessions.</p>
 */
public class Settlement {

    public enum Alignment {
        FRIENDLY, NEUTRAL, HOSTILE;

        public static Alignment byName(String n) {
            for (Alignment a : values()) {
                if (a.name().equals(n)) {
                    return a;
                }
            }
            return NEUTRAL;
        }
    }

    /**
     * A settlement inhabitant. Residents are dormant records by default;
     * {@link SettlementManager} materializes live {@link Npc} entities while
     * the player is nearby and writes state back when they deactivate.
     */
    public static class Resident {
        public String name;
        public NpcArchetype archetype;
        public float health;
        public boolean alive = true;
        /** Index into the layout's bed list (-1 = sleeps at the campfire). */
        public int bedIndex = -1;
        /** Index into the layout's duty-point list (-1 = wanders the center). */
        public int dutyIndex = -1;
        /** Rescued captives stop belonging to the hostile settlement. */
        public boolean rescued;
        /** Routed/surrendered residents remain alive but no longer block clearing. */
        public boolean routed;
        public boolean surrendered;
        /** Dormant health simulation. */
        public boolean sick;
        public float sicknessTimer;
        public float hunger;
        /** Live entity while the settlement is active; null when dormant. */
        public transient Npc live;

        public Resident(String name, NpcArchetype archetype) {
            this.name = name;
            this.archetype = archetype;
            this.health = archetype.maxHealth;
        }
    }

    // ---- Identity & static plan (never saved; re-derived from seed) ----
    public final long id;
    public final int regionX;
    public final int regionZ;
    public final SettlementType type;
    public final Vec3i center;
    public final int radius;
    /** Faction that founded the settlement (stable string id). */
    public final String founderFaction;

    // ---- Layout metadata (filled by SettlementBuilder, deterministic) ----
    public final List<Vec3i> beds = new ArrayList<>();
    public final List<Vec3i> dutyPoints = new ArrayList<>();
    public final List<Vec3i> patrolPoints = new ArrayList<>();
    public final List<Vec3i> gates = new ArrayList<>();
    public Vec3i alarmBell;
    public Vec3i leaderPost;
    public Vec3i prisonPos;
    public Vec3i magazinePos;
    public boolean layoutBuilt;

    // ---- Dynamic state (persisted in save v3) ----
    public Alignment alignment;
    /** Current owner faction id; changes when the player hands a fort over. */
    public String factionId;
    public int foodStock;
    public int woodStock;
    public int medStock;
    public int metalStock;
    /** 0..100; high alert spawns defenders at posts and locks gates. */
    public float alertLevel;
    /** 0..100; broken morale routs defenders. Leaders prop it up. */
    public float morale = 70;
    /** Local reputation with the player, -100..100 (distinct from faction rep). */
    public float localReputation;
    public boolean discovered;
    /** All defenders dead/routed and leader eliminated. */
    public boolean cleared;
    /** Player supplied and occupied a cleared settlement as an allied outpost. */
    public boolean occupied;
    /** Siege objective state; forts+ require all three before they clear. */
    public boolean commandNeutralized;
    public boolean alarmNeutralized;
    public boolean centralObjectiveControlled;
    /** Known only as a vague fortress rumor until normal discovery. */
    public boolean rumored;
    /** Seconds until a hostile counterattack tests an occupied outpost ({@code <= 0}: none). */
    public float counterattackTimer;
    /** Seconds until population replenishment is evaluated. */
    public float replenishTimer = 600;
    /**
     * Crime-action throttles. They are persisted so saving inside a storehouse
     * cannot be used to repeat or evade the restricted-area consequences.
     */
    public float trespassCooldown;
    public float restrictedStorageCooldown;
    /** Fixed-step dormant simulation index for deterministic events. */
    public long dormantStep;
    public float dormantAccumulator;

    public final List<Resident> residents = new ArrayList<>();

    public Settlement(long id, int regionX, int regionZ, SettlementType type,
                      Vec3i center, String founderFaction, Alignment alignment) {
        this.id = id;
        this.regionX = regionX;
        this.regionZ = regionZ;
        this.type = type;
        this.center = center;
        this.radius = type.radius;
        this.founderFaction = founderFaction;
        this.factionId = founderFaction;
        this.alignment = alignment;
    }

    public static long packId(int regionX, int regionZ) {
        return ((long) regionX << 32) ^ (regionZ & 0xffffffffL);
    }

    public boolean hostile() {
        return alignment == Alignment.HOSTILE && !cleared;
    }

    public boolean friendly() {
        return alignment == Alignment.FRIENDLY || occupied;
    }

    /** Axis-aligned reserved bounds check (blocks). */
    public boolean containsBlock(int x, int z) {
        return Math.abs(x - center.x()) <= radius && Math.abs(z - center.z()) <= radius;
    }

    public double distSqTo(double x, double z) {
        double dx = center.x() - x, dz = center.z() - z;
        return dx * dx + dz * dz;
    }

    public int aliveResidents() {
        int n = 0;
        for (Resident r : residents) {
            if (r.alive && !r.rescued && !r.routed && !r.surrendered) {
                n++;
            }
        }
        return n;
    }

    /** Every active non-captive resident of a hostile settlement is on its side. */
    public boolean countsAsDefender(Resident r) {
        return hostile() && r.alive && !r.rescued && !r.routed && !r.surrendered
                && r.archetype != NpcArchetype.CAPTIVE;
    }

    public Resident leaderResident() {
        for (Resident r : residents) {
            if (r.archetype.leader) {
                return r;
            }
        }
        return null;
    }

    /** Display label for map/prompts. */
    public String label() {
        String owner = occupied ? "Outpost (yours)"
                : cleared ? "Cleared"
                : HumanFaction.displayName(factionId);
        return type.displayName + " — " + owner;
    }
}
