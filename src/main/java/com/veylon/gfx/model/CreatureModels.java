package com.veylon.gfx.model;

import com.veylon.entity.Creature.CreatureType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Species model builders. Forward is -Z. One shared instance per species,
 * re-posed by the Animator before each draw. Silhouette first: ears, tails,
 * horns and stance carry recognition, not polycount.
 */
public final class CreatureModels {

    private static final Map<CreatureType, EntityModel> CACHE = new EnumMap<>(CreatureType.class);

    private CreatureModels() {
    }

    public static EntityModel of(CreatureType type) {
        return CACHE.computeIfAbsent(type, CreatureModels::build);
    }

    private static EntityModel build(CreatureType type) {
        return switch (type) {
            case DEER -> glowdeer();
            case WOLF -> ashwolf();
            case BIRD -> skitterwing();
            case HARE -> murkhare();
            case THORNHORN -> thornhorn();
            case STALKER -> gloomstalker();
        };
    }

    /** Shared quadruped chassis; species then decorate. */
    private static ModelPart quadruped(String tag, float bodyLen, float bodyW, float bodyH,
                                       float legH, float legW, int bodyCol, int legCol) {
        ModelPart root = new ModelPart("root");
        float shoulderY = legH + bodyH * 0.5f;

        ModelPart body = new ModelPart("body").pivot(0, shoulderY, 0)
                .box(0, 0, 0, bodyW, bodyH, bodyLen).color(bodyCol);
        root.child(body);

        float hx = bodyW * 0.32f;
        float fz = -bodyLen * 0.38f;
        float bz = bodyLen * 0.38f;
        String[] names = {"leg_fl", "leg_fr", "leg_bl", "leg_br"};
        float[][] at = {{-hx, fz}, {hx, fz}, {-hx, bz}, {hx, bz}};
        for (int i = 0; i < 4; i++) {
            ModelPart leg = new ModelPart(names[i]).pivot(at[i][0], legH, at[i][1])
                    .box(0, -legH * 0.5f, 0, legW, legH, legW).color(legCol);
            root.child(leg);
        }

        ModelPart neck = new ModelPart("neck").pivot(0, shoulderY + bodyH * 0.30f, -bodyLen * 0.48f);
        root.child(neck);
        return root;
    }

    private static EntityModel glowdeer() {
        ModelPart root = quadruped("deer", 0.85f, 0.42f, 0.42f, 0.58f, 0.11f, 0x8a6a48, 0x74573a);
        ModelPart neck = root.find("neck");
        ModelPart head = new ModelPart("head").pivot(0, 0.16f, -0.06f)
                .box(0, 0.06f, -0.10f, 0.22f, 0.24f, 0.34f).color(0x94765a);
        head.child(new ModelPart("snout").pivot(0, 0, -0.27f)
                .box(0, 0.02f, -0.04f, 0.13f, 0.13f, 0.14f).color(0x7c5f42));
        // Branching antlers.
        for (int s = -1; s <= 1; s += 2) {
            ModelPart antler = new ModelPart(s < 0 ? "antler_l" : "antler_r")
                    .pivot(s * 0.09f, 0.18f, 0.02f)
                    .box(0, 0.14f, 0, 0.035f, 0.30f, 0.035f).color(0xd8cbb2);
            antler.child(new ModelPart("tine" + s).pivot(s * 0.02f, 0.24f, 0)
                    .box(s * 0.07f, 0.02f, -0.02f, 0.16f, 0.035f, 0.035f).color(0xd8cbb2));
            head.child(antler);
        }
        neck.child(head);
        ModelPart body = root.find("body");
        // Bioluminescent flank spots: the "glow" in Glowdeer.
        for (int i = 0; i < 3; i++) {
            for (int s = -1; s <= 1; s += 2) {
                body.child(new ModelPart("glow" + i + "_" + s)
                        .pivot(s * 0.215f, 0.06f - i * 0.05f, -0.25f + i * 0.22f)
                        .box(0, 0, 0, 0.02f, 0.05f, 0.05f)
                        .color(0x59e0d8).emissive(0.85f));
            }
        }
        body.child(new ModelPart("tail").pivot(0, 0.12f, 0.44f)
                .box(0, 0.02f, 0.04f, 0.08f, 0.08f, 0.12f).color(0x74573a));
        return new EntityModel(root);
    }

    private static EntityModel ashwolf() {
        ModelPart root = quadruped("wolf", 0.80f, 0.34f, 0.34f, 0.42f, 0.10f, 0x565259, 0x46434a);
        ModelPart neck = root.find("neck");
        ModelPart head = new ModelPart("head").pivot(0, 0.06f, -0.05f)
                .box(0, 0.02f, -0.08f, 0.24f, 0.22f, 0.26f).color(0x605c64);
        head.child(new ModelPart("muzzle").pivot(0, -0.03f, -0.22f)
                .box(0, 0, -0.05f, 0.13f, 0.12f, 0.16f).color(0x4c4850));
        for (int s = -1; s <= 1; s += 2) {
            head.child(new ModelPart(s < 0 ? "ear_l" : "ear_r").pivot(s * 0.08f, 0.14f, 0.02f)
                    .box(0, 0.05f, 0, 0.06f, 0.11f, 0.04f).color(0x3c3941));
        }
        // Pale ash-gray eyes catch firelight.
        for (int s = -1; s <= 1; s += 2) {
            head.child(new ModelPart("eye" + s).pivot(s * 0.075f, 0.05f, -0.135f)
                    .box(0, 0, 0, 0.035f, 0.03f, 0.01f).color(0xd8c890).emissive(0.35f));
        }
        neck.child(head);
        ModelPart body = root.find("body");
        // Shoulder ruff makes the front read heavier.
        body.child(new ModelPart("ruff").pivot(0, 0.08f, -0.26f)
                .box(0, 0, 0, 0.40f, 0.30f, 0.22f).color(0x4c4850));
        ModelPart tail = new ModelPart("tail").pivot(0, 0.10f, 0.42f)
                .box(0, 0, 0.16f, 0.10f, 0.10f, 0.34f).color(0x46434a);
        body.child(tail);
        return new EntityModel(root);
    }

    private static EntityModel skitterwing() {
        ModelPart root = new ModelPart("root");
        ModelPart body = new ModelPart("body").pivot(0, 0.18f, 0)
                .box(0, 0, 0, 0.16f, 0.14f, 0.30f).color(0x9a8a4e);
        root.child(body);
        ModelPart head = new ModelPart("head").pivot(0, 0.05f, -0.17f)
                .box(0, 0.01f, -0.03f, 0.11f, 0.10f, 0.12f).color(0xa89858);
        head.child(new ModelPart("beak").pivot(0, -0.01f, -0.08f)
                .box(0, 0, -0.02f, 0.04f, 0.03f, 0.07f).color(0x3a3630));
        body.child(head);
        // Two wing pairs: unmistakably alien in flight.
        for (int pair = 0; pair < 2; pair++) {
            float z = -0.06f + pair * 0.14f;
            for (int s = -1; s <= 1; s += 2) {
                body.child(new ModelPart("wing" + pair + (s < 0 ? "_l" : "_r"))
                        .pivot(s * 0.08f, 0.05f, z)
                        .box(s * 0.16f, 0, 0, 0.30f, 0.02f, 0.10f)
                        .color(pair == 0 ? 0xb8a868 : 0x8a7a48));
            }
        }
        body.child(new ModelPart("tail").pivot(0, 0, 0.17f)
                .box(0, 0, 0.05f, 0.06f, 0.03f, 0.12f).color(0x8a7a48));
        return new EntityModel(root);
    }

    private static EntityModel murkhare() {
        ModelPart root = new ModelPart("root");
        ModelPart body = new ModelPart("body").pivot(0, 0.16f, 0)
                .box(0, 0.02f, 0.02f, 0.22f, 0.20f, 0.34f).color(0x8a7a62);
        root.child(body);
        // Heavy rear haunches for the hop silhouette.
        body.child(new ModelPart("haunch").pivot(0, 0.02f, 0.12f)
                .box(0, 0.02f, 0, 0.26f, 0.22f, 0.18f).color(0x7c6c54));
        ModelPart head = new ModelPart("head").pivot(0, 0.10f, -0.18f)
                .box(0, 0.02f, -0.04f, 0.15f, 0.15f, 0.16f).color(0x94846c);
        for (int s = -1; s <= 1; s += 2) {
            head.child(new ModelPart(s < 0 ? "ear_l" : "ear_r").pivot(s * 0.045f, 0.12f, 0.02f)
                    .box(0, 0.10f, 0, 0.045f, 0.22f, 0.03f).color(0x6c5c46));
        }
        body.child(head);
        String[] legs = {"leg_fl", "leg_fr", "leg_bl", "leg_br"};
        float[][] at = {{-0.07f, -0.10f}, {0.07f, -0.10f}, {-0.09f, 0.14f}, {0.09f, 0.14f}};
        for (int i = 0; i < 4; i++) {
            root.child(new ModelPart(legs[i]).pivot(at[i][0], 0.12f, at[i][1])
                    .box(0, -0.06f, 0, 0.05f, 0.12f, 0.05f).color(0x6c5c46));
        }
        body.child(new ModelPart("tail").pivot(0, 0.06f, 0.22f)
                .box(0, 0, 0.02f, 0.07f, 0.07f, 0.06f).color(0xb8ac96));
        return new EntityModel(root);
    }

    private static EntityModel thornhorn() {
        ModelPart root = quadruped("thorn", 1.25f, 0.72f, 0.72f, 0.55f, 0.20f, 0x6a5a42, 0x5a4c38);
        ModelPart neck = root.find("neck");
        ModelPart head = new ModelPart("head").pivot(0, -0.02f, -0.10f)
                .box(0, 0, -0.12f, 0.40f, 0.36f, 0.40f).color(0x76644a);
        // Two thick forward-swept horns.
        for (int s = -1; s <= 1; s += 2) {
            ModelPart horn = new ModelPart(s < 0 ? "horn_l" : "horn_r")
                    .pivot(s * 0.17f, 0.16f, -0.16f)
                    .box(0, 0.03f, -0.14f, 0.08f, 0.08f, 0.30f).color(0xcfc4a4);
            horn.child(new ModelPart("horn_tip" + s).pivot(0, 0, -0.28f)
                    .box(0, 0.02f, -0.05f, 0.05f, 0.05f, 0.14f).color(0xe0d6b8));
            head.child(horn);
        }
        neck.child(head);
        ModelPart body = root.find("body");
        // Dorsal plate ridge.
        for (int i = 0; i < 3; i++) {
            body.child(new ModelPart("plate" + i).pivot(0, 0.38f, -0.30f + i * 0.30f)
                    .box(0, 0.03f, 0, 0.16f, 0.14f, 0.20f).color(0x4c4034));
        }
        body.child(new ModelPart("tail").pivot(0, 0.10f, 0.65f)
                .box(0, 0, 0.10f, 0.14f, 0.14f, 0.26f).color(0x5a4c38));
        return new EntityModel(root);
    }

    private static EntityModel gloomstalker() {
        // Tall, wrong-jointed, too-thin: unsettling on the horizon.
        ModelPart root = quadruped("stalker", 0.70f, 0.26f, 0.26f, 0.72f, 0.06f, 0x2e3138, 0x272a30);
        ModelPart neck = root.find("neck");
        ModelPart head = new ModelPart("head").pivot(0, 0.10f, -0.06f)
                .box(0, 0, -0.10f, 0.18f, 0.16f, 0.30f).color(0x33363e);
        for (int s = -1; s <= 1; s += 2) {
            head.child(new ModelPart("eye" + s).pivot(s * 0.06f, 0.03f, -0.20f)
                    .box(0, 0, 0, 0.04f, 0.025f, 0.01f).color(0xbfe8ee).emissive(1.0f));
        }
        neck.child(head);
        ModelPart body = root.find("body");
        // Spine quills.
        for (int i = 0; i < 4; i++) {
            body.child(new ModelPart("quill" + i).pivot(0, 0.14f, -0.24f + i * 0.16f)
                    .box(0, 0.06f, 0, 0.03f, 0.16f, 0.03f).color(0x1e2126));
        }
        body.child(new ModelPart("tail").pivot(0, 0.02f, 0.36f)
                .box(0, 0, 0.18f, 0.05f, 0.05f, 0.40f).color(0x272a30));
        return new EntityModel(root);
    }
}
