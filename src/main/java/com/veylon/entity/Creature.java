package com.veylon.entity;

import com.veylon.ai.PanicIntent;
import com.veylon.item.ItemType;
import com.veylon.world.World;
import org.joml.Vector3f;

public class Creature extends Entity {

    public enum CreatureType {
        //   display       w      h      hp    speed  pred   fly    meat hide  r      g      b
        DEER("Glowdeer", 0.7f, 1.05f, 14f, 2.7f, false, false, 2, 1, 0.62f, 0.46f, 0.30f),
        WOLF("Ashwolf", 0.65f, 0.95f, 22f, 3.7f, true, false, 2, 1, 0.34f, 0.33f, 0.38f),
        BIRD("Skitterwing", 0.34f, 0.34f, 4f, 4.6f, false, true, 1, 0, 0.62f, 0.55f, 0.28f),
        HARE("Murkhare", 0.4f, 0.4f, 6f, 4.4f, false, false, 1, 0, 0.58f, 0.52f, 0.42f),
        THORNHORN("Thornhorn", 1.1f, 1.5f, 42f, 3.0f, false, false, 4, 2, 0.45f, 0.38f, 0.28f),
        STALKER("Gloomstalker", 0.7f, 1.0f, 30f, 4.0f, true, false, 2, 1, 0.22f, 0.26f, 0.32f),
        /**
         * The quarantine's alien organism (urban milestone 15): a low membrane body that lies
         * folded beside its growth node and lunges at light, noise and touch inside its territory.
         * It is neither prey nor a roaming predator; its remains give membrane sacs, never meat.
         */
        VEILMAW("Veilmaw", 0.9f, 0.55f, 34f, 1.6f, false, false, 0, 2, 0.24f, 0.20f, 0.28f);

        public final String displayName;
        public final float width, height, maxHealth, speed;
        public final boolean predator;
        public final boolean flying;
        /** Meat / hide yielded by a fully skinned carcass. */
        public final int meatYield;
        public final int hideYield;
        public final float r, g, b;

        CreatureType(String displayName, float width, float height, float maxHealth, float speed,
                     boolean predator, boolean flying, int meatYield, int hideYield,
                     float r, float g, float b) {
            this.displayName = displayName;
            this.width = width;
            this.height = height;
            this.maxHealth = maxHealth;
            this.speed = speed;
            this.predator = predator;
            this.flying = flying;
            this.meatYield = meatYield;
            this.hideYield = hideYield;
            this.r = r;
            this.g = g;
            this.b = b;
        }

        /** Whether a dead body of this kind leaves a carcass to harvest; a bird is too small. */
        public boolean leavesCarcass() {
            return this != BIRD;
        }

        /**
         * Hostile alien life bound to one place (milestone 15): it never roams after prey or a
         * distant noise, wildlife does not treat it as a hunter, and it is never food.
         */
        public boolean territorial() {
            return this == VEILMAW;
        }

        /**
         * What the second harvest yield of a carcass is: an animal's hide, or the membrane sacs
         * of an alien organism. Its meat yield is the edible part, and an alien has none.
         */
        public ItemType hideProduct() {
            return this == VEILMAW ? ItemType.MEMBRANE_SAC : ItemType.HIDE;
        }
    }

    public enum CreatureState {
        WANDER, GRAZE, SEEK_WATER, FLEE, REST, TRACK, HUNT, ATTACK, FLEE_HURT, STALK, CHARGE,
        // Milestone 15, the Veilmaw. States are never saved, so they append freely.
        /** Lying flat beside its node, hood down: waiting. */
        FOLDED,
        /** The telegraph: hood rising and veins brightening before it commits. */
        STIRRING,
        /** The dash at a target point. */
        LUNGING,
        /** Exposed and still after a lunge. */
        RECOILING,
        /** Walking back to its node. */
        RETURNING,
        /** Creeping toward a light to smother it. */
        CREEPING
    }

    public final CreatureType type;
    public CreatureState state = CreatureState.WANDER;
    /** 0 = full, 100 = starving. */
    public float hunger = 30;
    public float fear = 0;
    public final Vector3f target = new Vector3f();
    public boolean hasTarget;
    public Entity targetEntity;
    public float decideTimer = 0;
    public float attackCooldown = 0;
    public float eatTimer = 0;
    /** Seconds until the next footprint is dropped while moving. */
    public float trackTimer = 1.5f;
    /** While > 0 the creature drips blood marks (recently wounded). */
    public float bleedTimer = 0;
    /** Animation phase advanced by movement speed (render-side bob). */
    public float bobPhase;
    /** Arrows lodged in the animal; recovered when the carcass is harvested. */
    public int stuckArrows;
    public com.veylon.item.ItemType stuckArrowType;
    /** Flight from the fire on this body; written only by the AI. Transient. */
    public final PanicIntent panic = new PanicIntent();

    /**
     * Stable generated encounter slot of a city creature (milestone 15); zero for wildlife of the
     * legacy spawner and staged bodies. Its durable record lives in the encounter ledger.
     */
    public long encounterId;
    /** Where a city creature lives; null until its AI or the encounter director assigns one. Transient. */
    public com.veylon.world.urban.Habitat home;
    /**
     * Seconds left of the Veilmaw's telegraph, or of its current lunge, recoil or creep; its own
     * timers. Transient, like every other intent: a load or reactivation starts it folded.
     */
    public float windup = -1f, actionTimer, lureCooldown;
    /** Seconds until the Veilmaw may log its waking warning again. */
    public float warnCooldown;
    /** Health seen on the previous AI tick, to notice a blow from any source. */
    public float lastHealth = -1f;
    /** The target of a telegraph or lunge, and whether it is the player. */
    public boolean targetIsPlayer;
    /** The last noise the Veilmaw reacted to, so one event provokes it once. */
    public com.veylon.combat.WorldNoise.NoiseEvent lastHeard;

    public Creature(World world, CreatureType type) {
        super(world);
        this.type = type;
        this.width = type.width;
        this.height = type.height;
        this.maxHealth = type.maxHealth;
        this.health = type.maxHealth;
        if (type == CreatureType.VEILMAW) {
            state = CreatureState.FOLDED;
        }
    }

    /** Animals keep out of torches and campfires, flying or not. */
    @Override
    protected boolean keepsOutOfFlames() {
        return true;
    }
}
