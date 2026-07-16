package com.veylon.gfx.model;

import com.veylon.entity.Creature;
import com.veylon.entity.Npc;

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

    /** Carcass pose: same model, keeled over on its side. */
    public static void poseCarcass(EntityModel m) {
        m.resetPose();
        ModelPart root = m.part("root");
        root.rotZ = 1.45f;
        root.poseY = 0.16f;
        m.part("leg_fl").rotX = 0.5f;
        m.part("leg_fr").rotX = -0.35f;
        m.part("leg_bl").rotX = 0.35f;
        m.part("leg_br").rotX = -0.5f;
        m.part("head").rotX = 0.3f;
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

        // Role/faction colors.
        int vest;
        if (n.raider) {
            vest = 0x5a2a20;
        } else if (n.isTrader) {
            vest = 0xa8842e;
        } else if (n.sick) {
            vest = 0x4a5e56;
        } else {
            vest = switch (n.campIndex % 4) {
                case 0 -> 0x5e4a30;  // guard: leather
                case 1 -> 0x50603c;  // hunter: green
                case 2 -> 0x6a5a44;  // gatherer
                default -> 0x707a80; // medic: pale
            };
        }
        m.part("vest").color(vest);
        m.part("pack").visible = n.isTrader;
        m.part("traderRoll").visible = n.isTrader;
        m.part("traderAntenna").visible = n.isTrader;
        m.part("traderLamp").visible = n.isTrader;
        m.part("traderSatchelL").visible = n.isTrader;
        m.part("traderSatchelR").visible = n.isTrader;
        m.part("hood").visible = n.raider;
        m.part("visor").visible = n.raider;
        m.part("raiderPadL").visible = n.raider;
        m.part("raiderPadR").visible = n.raider;
        ModelPart raiderSpear = m.part("raiderSpear");
        raiderSpear.visible = n.raider;
        raiderSpear.rotZ = 0.62f; // slung diagonally across the back
        m.part("friendlyBadge").visible = !n.raider && !n.isTrader;

        m.part("arm_l").rotX = swing * 0.7f;
        m.part("arm_r").rotX = -swing * 0.7f;
        m.part("leg_l").rotX = -swing * 0.8f;
        m.part("leg_r").rotX = swing * 0.8f;
        ModelPart torso = m.part("torso");
        torso.poseY = Math.abs((float) Math.sin(phase)) * 0.04f * run;
        torso.rotX = 0.05f * run;

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
}
