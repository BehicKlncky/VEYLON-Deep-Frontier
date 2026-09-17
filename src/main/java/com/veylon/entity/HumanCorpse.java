package com.veylon.entity;

import org.joml.Vector3f;

/**
 * A person's body lying where it fell.
 *
 * <p>The animal equivalent is {@link Carcass}, which is a resource; this is
 * deliberately not. It cannot be harvested, cannot be perceived, is not an
 * {@link Npc} and never enters {@code EntityManager.npcs}, so it costs nothing
 * against the active-NPC budget and cannot become a target or break resident
 * bookkeeping. It exists so that killing someone leaves evidence instead of
 * the body vanishing mid-stride, and it rots away on the slow tick alongside
 * carcasses.
 */
public class HumanCorpse {

    public final Vector3f pos = new Vector3f();
    public final NpcAppearance appearance = new NpcAppearance();
    /** Orientation and bone angles the ragdoll came to rest in. */
    public final BodyPose pose = new BodyPose();
    /** Real seconds until the body is gone; matches {@link Carcass}. */
    public float decay = RagdollConstants.CORPSE_DECAY;

    public HumanCorpse(float x, float y, float z) {
        pos.set(x, y, z);
    }
}
