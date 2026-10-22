package com.veylon.settlement;

import com.veylon.util.Vec3i;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanWorldState;

/**
 * A survivor camp or hidden refuge of layout twelve (urban milestone 16), registered beside the
 * legacy settlements so residents are ordinary settled people: they share the resident budget,
 * the attack and death bookkeeping and the conversation screen.
 *
 * <p>What differs from a wilderness settlement is fixed here. The plan comes from
 * {@link SurvivorSites}, never from the legacy planner or builder, so nothing reads persisted
 * state to lay the site out. A resident's index in {@link #residents} is its planned slot: the
 * list is built once, in slot order, and never appended to or reordered, so a resident's identity
 * ({@link Resident#id}) never depends on a list position anyone could change. The legacy codec
 * never writes a site; {@code urban.sites} does.
 *
 * <p>{@link #localReputation} is the site's trust in the player (-100 to 100). A camp is friendly
 * at {@value #FRIEND_TRUST} and turns hostile at {@value #HOSTILE_TRUST}; hidden civilians never
 * fight, they hide. A betrayal (harm or theft after they trusted the player) caps trust at
 * {@value #BETRAYED_CAP} for good. Fear (0 to 100) makes the frightened retreat and refuse to deal.
 */
public final class UrbanSite extends Settlement {
    /** Runtime side of hidden civilians: no faction, so no faction reputation ever moves for them. */
    public static final String CIVILIANS = "civilians";
    public static final float FRIEND_TRUST = 40f, HOSTILE_TRUST = -40f, BETRAYED_CAP = -20f;
    /** Fear at which non-combatants retreat and refuse to trade. */
    public static final float FRIGHTENED = 60f;

    /** Where a refuge's people are; a camp is always home. Codes are what saves store. */
    public enum Relocation {
        HOME(0), DEPARTING(1), TRAVELLING(2), ARRIVED(3);

        public final int code;

        Relocation(int code) {
            this.code = code;
        }

        public static Relocation fromCode(int code) {
            for (Relocation r : values()) {
                if (r.code == code) { return r; }
            }
            return null;
        }
    }

    /** A planned person: the stable id and slot of the plan, and thirst beside the legacy needs. */
    public static final class Resident extends Settlement.Resident {
        public final long id;
        public final int slot;
        public final SurvivorSites.Role role;
        /** 0 to 100, like hunger. */
        public float thirst;

        Resident(SurvivorSites.Kind kind, SurvivorSites.Resident plan) {
            super(plan.name(), archetypeOf(kind, plan.role()));
            this.id = plan.id();
            this.slot = plan.slot();
            this.role = plan.role();
            this.bedIndex = plan.slot();
            this.dutyIndex = plan.slot();
            this.hunger = 0;
        }
    }

    public final SurvivorSites.Site plan;
    /** 0 to 100; decays by the hour. */
    public float fear;
    /** Harm or theft while they trusted the player: trust never rises above {@link #BETRAYED_CAP} again. */
    public boolean betrayed;
    /** A refuge's people left their stash to the player when they moved; taking from it is no theft. */
    public boolean stashReleased;
    public Relocation relocation = Relocation.HOME;
    /** The camp a refuge's people move to, or {@link UrbanIds#NONE}. */
    public long relocatedTo;
    /** The game minute travelling people reach their camp. */
    public double arriveMinute;
    /** The game minute this site's needs are simulated up to; negative until its people are met. */
    public double simMinute = -1;
    /** The last game day a camp medic treated the player; -1 for never. */
    public int lastTreatmentDay = -1;
    /**
     * Items the player took from their storage and has not put back. Putting supplies back pays
     * this first and earns nothing, so taking and returning can never farm trust.
     */
    public int owed;
    /** Presentation only, never saved: the log has told the player they heard these people. */
    public boolean heard;
    /** Transient (milestone 17): seconds a compound's guard still holds its gate open. */
    public float gateTimer;

    public UrbanSite(SurvivorSites.Site plan) {
        super(plan.id(), UrbanIds.regionX(plan.id()), UrbanIds.regionZ(plan.id()), SettlementType.CAMP, plan.anchor(),
                sideOf(plan.kind()), plan.kind() == SurvivorSites.Kind.RAIDERS ? Alignment.HOSTILE : Alignment.NEUTRAL);
        this.plan = plan;
        for (SurvivorSites.Resident r : plan.residents()) {
            residents.add(new Resident(plan.kind(), r));
        }
        // Hidden people are wary of anyone who finds them; a camp and a stronghold start open-minded.
        localReputation = camp() || stronghold() ? 0f : raiders() ? RAIDER_TRUST : -10f;
        morale = 70f;
    }

    /** Raiders' view of any stranger: never a friend, whatever is done. */
    public static final float RAIDER_TRUST = -60f;

    /** The side a site's people are on: legacy Free Settlers for a camp, an urban side otherwise. */
    static String sideOf(SurvivorSites.Kind kind) {
        return switch (kind) {
            case CAMP -> HumanFaction.FREE_SETTLERS;
            case REFUGE -> CIVILIANS;
            case STRONGHOLD -> UrbanFactions.WARDENS;
            case RAIDERS -> UrbanFactions.WRECKERS;
        };
    }

    /** The settlement archetype a planned role plays. */
    public static NpcArchetype archetypeOf(SurvivorSites.Role role) {
        return NpcArchetype.byId(role.archetypeId);
    }

    /** As {@link #archetypeOf(SurvivorSites.Role)}; every raider, whatever the role, is the scavenger profile. */
    public static NpcArchetype archetypeOf(SurvivorSites.Kind kind, SurvivorSites.Role role) {
        return kind == SurvivorSites.Kind.RAIDERS ? NpcArchetype.SCAVENGER : archetypeOf(role);
    }

    public boolean camp() { return plan.kind() == SurvivorSites.Kind.CAMP; }

    public boolean refuge() { return plan.kind() == SurvivorSites.Kind.REFUGE; }

    /** Layout thirteen: a guarded stronghold. */
    public boolean stronghold() { return plan.kind() == SurvivorSites.Kind.STRONGHOLD; }

    /** Layout thirteen: a raider yard. */
    public boolean raiders() { return plan.kind() == SurvivorSites.Kind.RAIDERS; }

    /** Either compound kind. */
    public boolean compound() { return plan.kind().compound(); }

    /** A compound's plan; null for a camp or refuge. */
    public com.veylon.world.urban.CompoundSites.Compound compoundPlan(UrbanWorldState state) {
        return compound() ? SurvivorSites.compoundOf(state, plan.id()) : null;
    }

    /** The resident of a planned slot. */
    public Resident resident(int slot) {
        return (Resident) residents.get(slot);
    }

    /** The site's trust in the player. */
    public float trust() { return localReputation; }

    /** Whether the people are where they live (home or arrived), not on the road. */
    public boolean atHome() {
        return relocation != Relocation.TRAVELLING;
    }

    /** Living people who are present at their home now. */
    public int present() {
        if (!atHome()) { return 0; }
        int n = 0;
        for (Settlement.Resident r : residents) {
            if (r.alive) { n++; }
        }
        return n;
    }

    /** A camp or a stronghold lets a trusted friend use its storage, as a friendly settlement always has. */
    public boolean grantsAccess() {
        return (camp() || stronghold()) && !betrayed && localReputation >= FRIEND_TRUST;
    }

    /** Whether the frightened among them hide and refuse to deal. */
    public boolean frightened() {
        return fear >= FRIGHTENED;
    }

    /**
     * Where the people live now: their own site, or for a refuge that moved, the camp that took
     * them in. Never a street or a roof.
     */
    public SurvivorSites.Site home(UrbanWorldState state) {
        if (relocation == Relocation.ARRIVED) {
            SurvivorSites.Site camp = SurvivorSites.resolve(state, relocatedTo);
            if (camp != null) { return camp; }
        }
        return plan;
    }

    /** The anchor activation measures from: the home site's anchor. */
    public Vec3i homeAnchor(UrbanWorldState state) {
        return home(state).anchor();
    }

    /**
     * Re-derives the legacy alignment from trust: a camp or a stronghold may turn hostile, hidden
     * civilians never do, and raiders always are.
     */
    public void settleAlignment() {
        if (raiders()) {
            localReputation = Math.min(localReputation, RAIDER_TRUST);
            alignment = Alignment.HOSTILE;
            return;
        }
        if (betrayed) { localReputation = Math.min(localReputation, BETRAYED_CAP); }
        if ((camp() || stronghold()) && localReputation <= HOSTILE_TRUST) {
            alignment = Alignment.HOSTILE;
        } else if (!betrayed && localReputation >= FRIEND_TRUST) {
            alignment = Alignment.FRIENDLY;
        } else {
            alignment = Alignment.NEUTRAL;
        }
    }

    @Override
    public String label() {
        if (raiders()) { return plan.kind().label + (cleared ? " (taken)" : ""); }
        return plan.kind().label + ((camp() || stronghold()) && alignment == Alignment.HOSTILE ? " (hostile)"
                : alignment == Alignment.FRIENDLY ? " (friends)" : "");
    }
}
