package com.veylon.entity;

import org.joml.Vector3f;

/**
 * A dead animal lying in the world. Harvest with F: a knife yields full meat,
 * hide and bone; bare hands tear off a single piece of meat. Carcasses rot
 * over time and their scent draws predators.
 *
 * <p>A carcass produced by a ragdoll is positioned by its <em>torso</em> and
 * carries the {@link BodyPose} the body actually came to rest in. One placed
 * directly — restored from a save written before 0.7.2, or staged by a QA
 * scene — is positioned by its feet and keeps the fixed keeled-over pose the
 * game has always drawn, with an orientation derived from where it lies so two
 * carcasses in the same clearing do not face identically.
 *
 * <p>An animal a blast blew apart leaves no whole body, but it still leaves
 * exactly one carcass: the harvest record, created on the tick it died and
 * tied to the torso piece of its remains ({@link #remains}). It is never
 * drawn whole, it lies where that torso lies, and it can be harvested once
 * the torso has come to rest. The limbs carry nothing.
 */
public class Carcass {

    public final Creature.CreatureType type;
    public final Vector3f pos = new Vector3f();
    public int meatLeft;
    public int hideLeft;
    /** Real seconds until the carcass rots away entirely. */
    public float decay;
    /** Arrows recoverable when skinning (transferred from the live animal). */
    public int stuckArrows;
    public com.veylon.item.ItemType stuckArrowType;
    /** How the body lies. Unsolved until a ragdoll settles into it. */
    public final BodyPose pose = new BodyPose();
    /**
     * The torso piece of the remains this record belongs to, when a blast
     * blew the animal apart; null for a whole carcass. Set once by
     * {@code BodyFragmentSystem}; the piece links back through
     * {@link BodyFragment#harvest} for as long as the record is in the world.
     * A save stores the link, and the lodged arrows with it, in
     * {@code world.remains}; a build or save without that section loads the
     * record as a whole carcass.
     */
    public BodyFragment remains;
    /**
     * The fire a whole animal died with, handed on from its ragdoll; null when
     * it died neither burning nor scorched, and always for the record of
     * remains, whose torso piece carries it instead. Never saved.
     */
    public BurnResidue burn;

    public Carcass(Creature.CreatureType type, float x, float y, float z) {
        this.type = type;
        pos.set(x, y, z);
        meatLeft = type.meatYield;
        hideLeft = type.hideYield;
        decay = 420f;
        pose.yaw = defaultYaw(x, z);
        pose.roll = RagdollConstants.FALLBACK_CREATURE_ROLL;
        pose.lift = 0.21f;
    }

    /**
     * Orientation for a carcass nobody simulated. This is the long-standing
     * position hash, kept here rather than in the renderer so every carcass has
     * a real stored yaw and the draw path no longer has to invent one.
     */
    public static float defaultYaw(float x, float z) {
        return (x * 0.37f + z * 0.73f) % ((float) Math.PI * 2f);
    }

    public boolean empty() {
        return meatLeft <= 0 && hideLeft <= 0;
    }

    /** True for the harvest record of an animal blown apart, which is never drawn whole. */
    public boolean fragmented() {
        return remains != null;
    }

    /**
     * Whether the body has come to rest, so it can be harvested or eaten:
     * always for a whole carcass, which only exists once its ragdoll settled;
     * once the torso lands for remains.
     */
    public boolean atRest() {
        return remains == null || remains.settled;
    }

    /** Rotten carcasses only give spoiled meat. */
    public boolean rotten() {
        return decay < 120f;
    }
}
