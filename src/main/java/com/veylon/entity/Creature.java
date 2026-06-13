package com.veylon.entity;

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
        STALKER("Gloomstalker", 0.7f, 1.0f, 30f, 4.0f, true, false, 2, 1, 0.22f, 0.26f, 0.32f);

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
    }

    public enum CreatureState {
        WANDER, GRAZE, SEEK_WATER, FLEE, REST, TRACK, HUNT, ATTACK, FLEE_HURT, STALK, CHARGE
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

    public Creature(World world, CreatureType type) {
        super(world);
        this.type = type;
        this.width = type.width;
        this.height = type.height;
        this.maxHealth = type.maxHealth;
        this.health = type.maxHealth;
    }
}
