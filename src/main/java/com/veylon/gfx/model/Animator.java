package com.veylon.gfx.model;

import com.veylon.entity.BodyFamily;
import com.veylon.entity.BodyFragment;
import com.veylon.entity.BodyPose;
import com.veylon.entity.BodySkeleton;
import com.veylon.entity.Creature;
import com.veylon.entity.FragmentPiece;
import com.veylon.entity.Npc;
import com.veylon.entity.NpcAppearance;

import java.util.List;

/**
 * Procedural state-driven poses: walk/run cycles, stalking crouches, attack
 * lunges, rest folds, wing flaps, hare hops. Poses blend from the current
 * movement speed so transitions read naturally without clip infrastructure.
 */
public final class Animator {

    private Animator() {
    }

    // ------------------------------------------------------------------
    // Creatures
    // ------------------------------------------------------------------

    public static void poseCreature(EntityModel m, Creature c, double time) {
        m.resetPose();
        float speed = (float) Math.sqrt(c.vel.x * c.vel.x + c.vel.z * c.vel.z);
        float run = Math.min(1f, speed / Math.max(0.5f, c.type.speed));
        float phase = c.bobPhase * 3.2f;
        float swing = (float) Math.sin(phase) * run;
        float t = (float) time;
        if (c.type == Creature.CreatureType.VEILMAW) {
            poseVeilmaw(m, c, t, run, phase);
            return;
        }

        ModelPart body = m.part("body");
        ModelPart head = m.part("head");
        ModelPart neck = m.part("neck");
        ModelPart tail = m.part("tail");

        // Base gait: diagonal leg pairs.
        float amp = 0.55f + 0.35f * run;
        m.part("leg_fl").rotX = swing * amp;
        m.part("leg_br").rotX = swing * amp;
        m.part("leg_fr").rotX = -swing * amp;
        m.part("leg_bl").rotX = -swing * amp;
        body.poseY = Math.abs((float) Math.sin(phase)) * 0.045f * run;
        body.rotZ = (float) Math.sin(phase * 0.5f) * 0.03f * run;

        // Idle life: breathing and ear/tail motion.
        body.scale = 1f + (float) Math.sin(t * 2.2 + c.bobPhase) * 0.012f;
        tail.rotY = (float) Math.sin(t * 1.7 + c.bobPhase * 2) * 0.25f;
        m.part("ear_l").rotZ = (float) Math.sin(t * 0.9) * 0.08f;
        m.part("ear_r").rotZ = -(float) Math.sin(t * 1.1) * 0.08f;

        switch (c.state) {
            case STALK, TRACK -> {
                body.poseY -= 0.16f;
                neck.poseY = -0.14f;
                head.rotX = 0.18f;
                tail.rotX = 0.35f;
                tail.rotY = 0;
            }
            case CHARGE, HUNT -> {
                body.rotX = -0.08f;
                neck.poseY = -0.06f;
                head.rotX = 0.10f;
            }
            case ATTACK -> {
                float lunge = (float) Math.abs(Math.sin(t * 10));
                body.rotX = -0.14f - lunge * 0.10f;
                head.rotX = -0.25f + lunge * 0.45f;
                neck.poseZ = -lunge * 0.09f;
            }
            case REST -> {
                body.poseY -= 0.30f;
                m.part("leg_fl").rotX = 1.35f;
                m.part("leg_fr").rotX = 1.35f;
                m.part("leg_bl").rotX = -1.35f;
                m.part("leg_br").rotX = -1.35f;
                head.rotX = 0.22f;
                neck.poseY = -0.10f;
            }
            case GRAZE -> {
                neck.poseY = -0.20f;
                head.rotX = 0.65f;
            }
            case FLEE -> {
                tail.rotX = -0.3f; // tucked
                body.rotX = -0.05f;
            }
            case FLEE_HURT -> {
                tail.rotX = -0.45f;
                body.rotZ = 0.20f;
                body.poseY -= 0.08f;
                head.rotX = 0.28f;
                m.part("leg_fr").rotX = 0.95f;
            }
            default -> {
            }
        }

        // Species specifics.
        if (c.type.flying) {
            float flap = (float) Math.sin(t * 16 + c.bobPhase);
            m.part("wing0_l").rotZ = 0.5f + flap * 0.7f;
            m.part("wing0_r").rotZ = -0.5f - flap * 0.7f;
            m.part("wing1_l").rotZ = 0.4f + (float) Math.sin(t * 16 + 1.1 + c.bobPhase) * 0.7f;
            m.part("wing1_r").rotZ = -0.4f - (float) Math.sin(t * 16 + 1.1 + c.bobPhase) * 0.7f;
        }
        if (c.type == Creature.CreatureType.HARE) {
            // Hop: whole body arcs instead of leg swings.
            float hop = Math.abs((float) Math.sin(phase * 0.8f)) * run;
            m.part("root").poseY = hop * 0.14f;
            body.rotX = -hop * 0.25f;
        }
        if (c.type == Creature.CreatureType.THORNHORN && c.state == Creature.CreatureState.ATTACK) {
            head.rotX = -0.5f + (float) Math.abs(Math.sin(t * 8)) * 0.7f; // horn toss
        }
    }

    private static final String[] VEILMAW_LEGS = {"leg_fl", "leg_fr", "leg_bl", "leg_br"};

    /**
     * The Veilmaw (urban milestone 15). Folded, it lies pressed to its membrane with legs splayed
     * flat, hood laid forward over the maw and veins barely lit, so at a glance it is part of the
     * growth. The telegraph raises the hood and brightens the veins over the windup, with a
     * tremor; the lunge pitches it forward with the hood flared back; recoiling and walking home
     * it stays half raised. The glow is set on every pose because the model is shared.
     */
    private static void poseVeilmaw(EntityModel m, Creature c, float t, float run, float phase) {
        float wake = switch (c.state) {
            case FOLDED -> 0f;
            case STIRRING -> c.windup <= 0f ? 1f
                    : 1f - Math.min(1f, c.windup / com.veylon.ai.VeilmawAI.WINDUP);
            case RECOILING, RETURNING, CREEPING -> 0.55f;
            default -> 1f;
        };
        float fold = 1f - wake;
        float swing = (float) Math.sin(phase) * run;
        float amp = 0.5f + 0.4f * run;
        for (int i = 0; i < VEILMAW_LEGS.length; i++) {
            ModelPart leg = m.part(VEILMAW_LEGS[i]);
            float side = i % 2 == 0 ? -1f : 1f;
            leg.rotZ = side * 1.25f * fold; // splayed outward, flat to the ground
            leg.rotX = (i == 0 || i == 3 ? swing : -swing) * amp * Math.max(wake, 0.3f);
        }
        ModelPart body = m.part("body");
        body.poseY = -0.22f * fold + (float) Math.sin(t * 1.6 + c.bobPhase) * 0.01f;
        body.scale = 1f + (float) Math.sin(t * 1.3 + c.bobPhase) * 0.015f; // the membrane breathes
        ModelPart neck = m.part("neck");
        neck.poseY = -0.22f * fold;
        neck.rotX = -1.40f * fold; // the hood laid flat forward
        float glow;
        switch (c.state) {
            case FOLDED -> glow = 0.10f + 0.04f * (float) Math.sin(t * 1.2 + c.bobPhase);
            case STIRRING -> {
                neck.rotZ = (float) Math.sin(t * 22) * 0.04f * wake;
                glow = 0.25f + 0.75f * wake * (0.75f + 0.25f * (float) Math.sin(t * 16));
            }
            case LUNGING -> {
                body.rotX = -0.16f;
                neck.rotX = 0.25f;
                glow = 1f;
            }
            case RECOILING -> glow = 0.45f;
            default -> glow = 0.3f;
        }
        m.part("vein_l").emissive = glow;
        m.part("vein_r").emissive = glow;
        m.part("vein_c").emissive = glow;
        m.part("tail").emissive = 0.15f + 0.45f * glow;
        m.part("tail_tip").emissive = 0.15f + 0.45f * glow;
    }

    /**
     * Carcass pose for a body nobody simulated: the fixed sprawl the game has
     * always drawn.
     *
     * <p>The keel itself is no longer here. A body's orientation now lives on
     * its {@link com.veylon.entity.BodyPose} and is applied to the draw
     * transform, so this writes limbs only and the same code path serves both a
     * solved pose and this one.
     */
    public static void poseCarcass(EntityModel m) {
        m.resetPose();
        m.part("leg_fl").rotX = 0.5f;
        m.part("leg_fr").rotX = -0.35f;
        m.part("leg_bl").rotX = 0.35f;
        m.part("leg_br").rotX = -0.5f;
        m.part("head").rotX = 0.3f;
    }

    /**
     * Writes a solved body's own bone angles into the shared species model.
     *
     * <p>This is the whole of the ragdoll/corpse render seam: the model stays a
     * cached singleton, and the per-body state lives on the {@link BodyPose}
     * that is stamped in here immediately before the draw. The pose's
     * yaw/pitch/roll are not written to the root — the caller puts them on the
     * draw transform, where a roll is a roll about the body's own axis rather
     * than about world Z.
     */
    public static void poseBody(EntityModel m, BodySkeleton skeleton, BodyPose pose) {
        m.resetPose();
        int bones = Math.min(pose.boneCount, skeleton.boneCount);
        for (int b = 0; b < bones; b++) {
            // An unmapped name resolves to a hidden dummy, so a species whose
            // bones were never listed degrades instead of throwing.
            ModelPart part = m.part(skeleton.part[b]);
            part.rotX = pose.boneRotX[b];
            part.rotY = pose.boneRotY[b];
            part.rotZ = pose.boneRotZ[b];
        }
    }

    // ------------------------------------------------------------------
    // Body fragments
    // ------------------------------------------------------------------

    /**
     * Poses the shared model of the piece's own body — the humanoid for a
     * person or the player, the species' model for an animal — as that one
     * piece; see {@link #poseFragment(EntityModel, BodyFragment)}.
     *
     * @return the part the piece must be drawn from
     */
    public static ModelPart poseFragment(BodyFragment f) {
        return poseFragment(AnatomyModels.modelOf(f.definition.family), f);
    }

    /**
     * Poses a shared model as one piece of a body blown apart: everything the
     * last body drawn from it left behind is reset, a person's piece puts on
     * the look it carries, the joints take the pose the body died in, and all
     * but the piece is hidden. Draw the returned part in
     * {@link FragmentModels#rootFrame}.
     *
     * @return the part the piece must be drawn from; see {@link #isolatePart}
     * @throws IllegalArgumentException when {@code m} is not the model of the
     *         piece's body family
     */
    public static ModelPart poseFragment(EntityModel m, BodyFragment f) {
        FragmentPiece piece = f.definition;
        if (m != AnatomyModels.modelOf(piece.family)) {
            throw new IllegalArgumentException("a " + piece + " piece is not drawn with this model");
        }
        m.resetPose();
        if (piece.family == BodyFamily.HUMANOID) {
            applyAppearance(m, f.appearance);
        }
        AnatomyModels.applyPose(m, f.pose);
        return isolatePart(m, piece.rootPart, piece.excludedParts);
    }

    /**
     * Hides every part outside {@code rootPart}'s subtree and every subtree
     * named in {@code excluded}, and makes a part whose split tip is excluded
     * draw its own half ({@link ModelPart#forceSplitDraw}) rather than the
     * merged whole box that would still include the missing tip.
     *
     * <p>Run it after {@code resetPose}, the look and the pose. It only ever
     * hides, so an accessory the person was not wearing stays hidden. Because
     * the ancestors are hidden too, and a hidden part draws nothing below it,
     * the piece is drawn from the returned part, never from the model root,
     * in the frame its ancestors had in the pose the body died in.
     *
     * @return the piece's root part
     */
    public static ModelPart isolatePart(EntityModel m, String rootPart, List<String> excluded) {
        ModelPart keep = m.part(rootPart);
        hideAllBut(m.root, keep);
        hideExcluded(keep, excluded);
        return keep;
    }

    private static void hideAllBut(ModelPart p, ModelPart keep) {
        if (p == keep) {
            return;
        }
        p.visible = false;
        for (int i = 0; i < p.children.size(); i++) {
            hideAllBut(p.children.get(i), keep);
        }
    }

    private static void hideExcluded(ModelPart p, List<String> excluded) {
        for (int i = 0; i < p.children.size(); i++) {
            ModelPart c = p.children.get(i);
            if (excluded.contains(c.name)) {
                hideAllBut(c, null); // no part to keep: the whole subtree
                if (p.isSplitTip(c)) {
                    p.forceSplitDraw = true;
                }
            } else {
                hideExcluded(c, excluded);
            }
        }
    }

    // ------------------------------------------------------------------
    // NPCs
    // ------------------------------------------------------------------

    public static void poseNpc(EntityModel m, Npc n, double time) {
        m.resetPose();
        float speed = (float) Math.sqrt(n.vel.x * n.vel.x + n.vel.z * n.vel.z);
        float run = Math.min(1f, speed / 3.5f);
        float phase = n.bobPhase * 3.2f;
        float swing = (float) Math.sin(phase) * run;
        float t = (float) time;

        applyAppearance(m, n.archetype, n.raider, n.isTrader, n.sick, n.campIndex);
        m.part("arm_l").rotX = swing * 0.7f;
        m.part("arm_r").rotX = -swing * 0.7f;
        m.part("leg_l").rotX = -swing * 0.8f;
        m.part("leg_r").rotX = swing * 0.8f;
        ModelPart torso = m.part("torso");
        torso.poseY = Math.abs((float) Math.sin(phase)) * 0.04f * run;
        torso.rotX = 0.05f * run;

        if (n.infected()) {
            boolean breacher = n.archetype == com.veylon.settlement.NpcArchetype.BREACHER;
            torso.rotX = n.painTimer > 0 ? -0.18f : breacher ? 0.34f : 0.22f;
            m.part("head").rotZ = breacher ? 0f : -0.13f;
            m.part("head").rotX = breacher ? 0.26f : 0f; // the sealed face hangs, listening
            m.part("arm_l").rotX = swing * 0.3f;
            m.part("arm_r").rotX = breacher ? -0.12f : -0.4f;
            m.part("arm_r").rotZ = breacher ? -0.05f : -0.16f;
            if (n.strikeWindup >= 0) {
                float windup = breacher ? com.veylon.ai.BreacherAI.WINDUP : com.veylon.ai.InfectedAI.WINDUP;
                float progress = 1 - n.strikeWindup / windup;
                m.part("arm_r").rotX = -0.4f - (breacher ? 2.3f : 1.8f) * progress;
                m.part("arm_l").rotX = -0.3f - 0.7f * progress;
                torso.rotX = 0.10f + 0.25f * progress;
            } else if (breacher && n.breachDoor != null) {
                // The ram climbs between blows and drops on the door with each one.
                float raise = 1f - Math.max(0f, n.breachTimer) / com.veylon.ai.BreacherAI.BLOW_SECONDS;
                m.part("arm_r").rotX = -0.3f - 2.2f * raise;
                torso.rotX = 0.18f + 0.2f * raise;
            }
            if (n.panic.active()) {
                m.part("arm_l").rotX = -1.4f;
                m.part("arm_r").rotX = -1.2f;
            }
            return;
        }

        // Idle sway + breathing.
        if (run < 0.1f) {
            torso.rotZ = (float) Math.sin(t * 1.3 + n.bobPhase) * 0.02f;
            m.part("arm_l").rotZ = 0.06f + (float) Math.sin(t * 2.1) * 0.02f;
            m.part("arm_r").rotZ = -0.06f - (float) Math.sin(t * 2.3) * 0.02f;
        }

        switch (n.state) {
            case ATTACK, RAID -> {
                float sw = (float) Math.abs(Math.sin(t * 9));
                m.part("arm_r").rotX = -1.6f + sw * 1.1f;
                torso.rotX = 0.12f;
            }
            case SLEEP -> {
                ModelPart root = m.part("root");
                root.rotX = 1.5f;
                root.poseY = -0.62f;
                m.part("arm_l").rotX = 0.25f;
                m.part("arm_r").rotX = 0.25f;
            }
            case WARM_BY_FIRE -> {
                m.part("arm_l").rotX = -0.9f;
                m.part("arm_r").rotX = -0.9f;
                m.part("head").rotX = 0.12f;
            }
            case GATHER_FOOD, GATHER_WOOD, BUILD -> {
                if (run < 0.1f) {
                    float work = (float) Math.sin(t * 5);
                    m.part("arm_r").rotX = -0.9f + work * 0.5f;
                    torso.rotX = 0.18f;
                }
            }
            case HEAL -> {
                torso.rotX = 0.3f;
                m.part("arm_l").rotX = -0.7f;
                m.part("arm_r").rotX = -0.7f;
            }
            default -> {
            }
        }
    }

    /** Puts on a look carried off a person, or the neutral look of the player's remains. */
    public static void applyAppearance(EntityModel m, NpcAppearance a) {
        applyAppearance(m, a.archetype, a.raider, a.trader, a.sick, a.campIndex);
    }

    /**
     * Vest colour and accessory visibility for the shared humanoid.
     *
     * <p>The humanoid is a superset model: every archetype's kit exists in the
     * tree and {@code resetPose} makes all of it visible again, so anything
     * that poses a person has to run this or draw a body wearing a trader's
     * pack, a raider's hood, a quiver, a powder keg and a leader's mantle at
     * once. Taking primitives rather than an {@code Npc} is what lets a corpse
     * keep the look of the person who died without holding on to the entity
     * the world has already removed.
     */
    public static void applyAppearance(EntityModel m, com.veylon.settlement.NpcArchetype a,
                                       boolean raider, boolean isTrader, boolean sick,
                                       int campIndex) {
        // Role/faction colors.
        int vest;
        if (a != null) {
            vest = switch (a) {
                case GUARD -> 0x5e4a30;
                case ARCHER -> 0x4a5a38;
                case MEDIC -> 0x707a80;
                case TRADER -> 0xa8842e;
                case FARMER -> 0x5c6a3a;
                case SMITH -> 0x54483c;
                case SCAVENGER -> 0x5a2a20;
                case TRACKER -> 0x4a382c;
                case SCOUT -> 0x3e4a42;
                case HUNTER -> 0x513f2c;
                case BRUTE -> 0x3a2f28;
                case POWDERMAN -> 0x40342a;
                case LEADER -> 0x5a2430;
                case CAPTIVE -> 0x565250;
                default -> 0x6a5a44;
            };
        } else if (raider) {
            vest = 0x5a2a20;
        } else if (isTrader) {
            vest = 0xa8842e;
        } else if (sick) {
            vest = 0x4a5e56;
        } else {
            vest = switch (Math.floorMod(campIndex, 4)) {
                case 0 -> 0x5e4a30;  // guard: leather
                case 1 -> 0x50603c;  // hunter: green
                case 2 -> 0x6a5a44;  // gatherer
                default -> 0x707a80; // medic: pale
            };
        }
        m.part("vest").color(vest);

        boolean scavengerLook = raider
                || a == com.veylon.settlement.NpcArchetype.SCAVENGER;
        boolean headhunter = a != null && a.hostileArchetype()
                && a != com.veylon.settlement.NpcArchetype.SCAVENGER;
        boolean trader = isTrader || a == com.veylon.settlement.NpcArchetype.TRADER;
        m.part("pack").visible = trader;
        m.part("traderRoll").visible = trader;
        m.part("traderAntenna").visible = trader;
        m.part("traderLamp").visible = trader;
        m.part("traderSatchelL").visible = trader;
        m.part("traderSatchelR").visible = trader;
        m.part("hood").visible = scavengerLook;
        m.part("visor").visible = scavengerLook;
        m.part("raiderPadL").visible = scavengerLook;
        m.part("raiderPadR").visible = scavengerLook;
        ModelPart raiderSpear = m.part("raiderSpear");
        raiderSpear.visible = scavengerLook;
        raiderSpear.rotZ = 0.62f; // slung diagonally across the back
        // The camp badge; a look with no camp (NpcAppearance.NEUTRAL_CAMP_INDEX,
        // the player's remains) has none to wear.
        m.part("friendlyBadge").visible = !scavengerLook && !headhunter && !trader
                && a != com.veylon.settlement.NpcArchetype.CAPTIVE && campIndex >= 0;

        // 0.3.0 archetype accessories.
        boolean archer = a == com.veylon.settlement.NpcArchetype.ARCHER
                || a == com.veylon.settlement.NpcArchetype.SCOUT;
        m.part("quiver").visible = archer;
        m.part("slungBow").visible = archer;
        m.part("kegPack").visible = a == com.veylon.settlement.NpcArchetype.POWDERMAN;
        m.part("slungGun").visible = a == com.veylon.settlement.NpcArchetype.POWDERMAN;
        boolean brute = a == com.veylon.settlement.NpcArchetype.BRUTE;
        m.part("brutePadL").visible = brute;
        m.part("brutePadR").visible = brute;
        m.part("bruteChest").visible = brute;
        boolean leader = a == com.veylon.settlement.NpcArchetype.LEADER;
        m.part("leaderCrest").visible = leader;
        m.part("leaderMantle").visible = leader;
        m.part("trophies").visible = headhunter;
        m.part("warPaint").visible = headhunter;
        m.part("medicSash").visible = a == com.veylon.settlement.NpcArchetype.MEDIC;
        m.part("guardPlate").visible = a == com.veylon.settlement.NpcArchetype.GUARD;
        boolean infected = a != null && a.infected();
        boolean breacher = a == com.veylon.settlement.NpcArchetype.BREACHER;
        m.part("veilShoulder").visible = infected && !breacher;
        m.part("veilRibs").visible = infected;
        m.part("breacherSeal").visible = breacher;
        m.part("breacherHump").visible = breacher;
        m.part("breacherRam").visible = breacher;
        m.part("friendlyBadge").visible &= !infected;
        m.part("head").color(breacher ? 0x8f957a : infected ? 0xa1aa8e : 0xd8ad8a);
        m.part("torso").color(breacher ? 0x55584a : infected ? 0x666954 : 0x33566e);
        m.part("vest").color(breacher ? 0x63664f : infected ? 0x777965 : vest);
        int arms = breacher ? 0x7f8668 : infected ? 0x929a7d : 0x3d5468;
        int legs = breacher ? 0x3f4339 : infected ? 0x494e46 : 0x2e3a44;
        m.part("arm_l").color(arms); m.part("arm_r").color(arms);
        m.part("forearm_l").color(arms); m.part("forearm_r").color(arms);
        m.part("leg_l").color(legs); m.part("leg_r").color(legs);
        m.part("shin_l").color(legs); m.part("shin_r").color(legs);
    }
}
