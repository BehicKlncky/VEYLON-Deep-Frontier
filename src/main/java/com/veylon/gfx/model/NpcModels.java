package com.veylon.gfx.model;

/**
 * Modular humanoid: shared skeleton, role-driven colors and accessory parts
 * (trader pack, raider hood) toggled at pose time. Forward is -Z.
 */
public final class NpcModels {

    private static EntityModel humanoid;

    private NpcModels() {
    }

    public static EntityModel get() {
        if (humanoid == null) {
            humanoid = build();
        }
        return humanoid;
    }

    private static EntityModel build() {
        ModelPart root = new ModelPart("root");
        float hipY = 0.86f;

        ModelPart torso = new ModelPart("torso").pivot(0, hipY, 0)
                .box(0, 0.31f, 0, 0.46f, 0.62f, 0.26f).color(0x33566e);
        root.child(torso);

        // Chest plate/vest reads the faction/role color.
        torso.child(new ModelPart("vest").pivot(0, 0.34f, 0)
                .box(0, 0, 0, 0.50f, 0.44f, 0.30f).color(0x415e50));

        ModelPart head = new ModelPart("head").pivot(0, 0.64f, 0)
                .box(0, 0.14f, 0, 0.26f, 0.26f, 0.26f).color(0xd8ad8a);
        head.child(new ModelPart("hood").pivot(0, 0.02f, 0)
                .box(0, 0.14f, 0.015f, 0.30f, 0.28f, 0.30f).color(0x2c2620));
        head.child(new ModelPart("visor").pivot(0, 0.14f, -0.135f)
                .box(0, 0, 0, 0.22f, 0.05f, 0.02f).color(0x8a2018).emissive(0.5f));
        head.child(new ModelPart("traderAntenna").pivot(0.10f, 0.26f, 0)
                .box(0, 0.09f, 0, 0.025f, 0.20f, 0.025f).color(0x8c7745));
        head.child(new ModelPart("traderLamp").pivot(0.10f, 0.38f, 0)
                .box(0, 0, 0, 0.055f, 0.055f, 0.055f).color(0xe0b84b).emissive(0.4f));
        torso.child(head);

        for (int s = -1; s <= 1; s += 2) {
            torso.child(new ModelPart(s < 0 ? "arm_l" : "arm_r").pivot(s * 0.30f, 0.55f, 0)
                    .box(0, -0.26f, 0, 0.13f, 0.55f, 0.15f).color(0x3d5468));
            root.child(new ModelPart(s < 0 ? "leg_l" : "leg_r").pivot(s * 0.115f, hipY, 0)
                    .box(0, -0.43f, 0, 0.16f, 0.86f, 0.18f).color(0x2e3a44));
        }

        torso.child(new ModelPart("pack").pivot(0, 0.30f, 0.20f)
                .box(0, 0, 0.06f, 0.38f, 0.44f, 0.20f).color(0x7a5a34));
        torso.child(new ModelPart("traderRoll").pivot(0, 0.08f, 0.29f)
                .box(0, 0, 0, 0.46f, 0.11f, 0.11f).color(0x8e6d3b));
        // Hip satchels widen the trader's lower profile at range; with the pack
        // hump they give a loaded-mule outline no other role has.
        torso.child(new ModelPart("traderSatchelL").pivot(-0.32f, 0.06f, 0.04f)
                .box(0, 0, 0, 0.14f, 0.24f, 0.28f).color(0x6b4f2e));
        torso.child(new ModelPart("traderSatchelR").pivot(0.32f, 0.06f, 0.04f)
                .box(0, 0, 0, 0.14f, 0.24f, 0.28f).color(0x6b4f2e));
        torso.child(new ModelPart("raiderPadL").pivot(-0.30f, 0.57f, 0)
                .box(0, 0, 0, 0.19f, 0.15f, 0.34f).color(0x342c27));
        torso.child(new ModelPart("raiderPadR").pivot(0.30f, 0.57f, 0)
                .box(0, 0, 0, 0.19f, 0.15f, 0.34f).color(0x342c27));
        // Back-slung spear: a hard diagonal across the back that separates the
        // raider silhouette from guards long before mask or color resolve.
        ModelPart raiderSpear = new ModelPart("raiderSpear").pivot(0, 0.28f, 0.245f)
                .box(0, 0, 0, 0.045f, 1.15f, 0.045f).color(0x54402a);
        raiderSpear.child(new ModelPart("raiderSpearTip").pivot(0, 0.60f, 0)
                .box(0, 0, 0, 0.075f, 0.14f, 0.04f).color(0x9aa0a6));
        torso.child(raiderSpear);
        torso.child(new ModelPart("friendlyBadge").pivot(-0.15f, 0.40f, -0.158f)
                .box(0, 0, 0, 0.08f, 0.12f, 0.018f).color(0x3c9aa2).emissive(0.18f));

        // ---- 0.3.0 archetype accessories (toggled per pose) ----
        // Archer/scout quiver: angled tube with pale fletching tips.
        ModelPart quiver = new ModelPart("quiver").pivot(0.16f, 0.26f, 0.19f)
                .box(0, 0, 0, 0.12f, 0.42f, 0.12f).color(0x5c4426);
        quiver.rotZ = -0.35f;
        quiver.child(new ModelPart("quiverFletch").pivot(0, 0.24f, 0)
                .box(0, 0, 0, 0.14f, 0.08f, 0.14f).color(0xd9d2bd));
        torso.child(quiver);
        // Slung bow arc across the back.
        ModelPart slungBow = new ModelPart("slungBow").pivot(-0.06f, 0.28f, 0.235f)
                .box(0, 0, 0, 0.045f, 1.0f, 0.05f).color(0x6a4c2c);
        slungBow.rotZ = -0.55f;
        torso.child(slungBow);
        // Powderman: powder keg back-pack and slung long gun.
        torso.child(new ModelPart("kegPack").pivot(0, 0.26f, 0.24f)
                .box(0, 0, 0.02f, 0.30f, 0.34f, 0.26f).color(0x6b4a28));
        ModelPart slungGun = new ModelPart("slungGun").pivot(0.05f, 0.28f, 0.25f)
                .box(0, 0, 0, 0.05f, 1.1f, 0.05f).color(0x565c64);
        slungGun.rotZ = 0.5f;
        torso.child(slungGun);
        // Brute bulk: heavy pauldrons and a chest slab.
        torso.child(new ModelPart("brutePadL").pivot(-0.33f, 0.55f, 0)
                .box(0, 0, 0, 0.24f, 0.20f, 0.38f).color(0x2e2724));
        torso.child(new ModelPart("brutePadR").pivot(0.33f, 0.55f, 0)
                .box(0, 0, 0, 0.24f, 0.20f, 0.38f).color(0x2e2724));
        torso.child(new ModelPart("bruteChest").pivot(0, 0.30f, -0.16f)
                .box(0, 0, 0, 0.46f, 0.34f, 0.06f).color(0x3a3430));
        // Leader crest: tall silhouette-breaking headdress + shoulder cape hint.
        head.child(new ModelPart("leaderCrest").pivot(0, 0.30f, 0.02f)
                .box(0, 0, 0, 0.06f, 0.26f, 0.24f).color(0x7a1f2b));
        torso.child(new ModelPart("leaderMantle").pivot(0, 0.52f, 0.10f)
                .box(0, 0, 0, 0.56f, 0.14f, 0.20f).color(0x4a1a22));
        // Headhunter identity: pale bone trophies strung across the chest.
        torso.child(new ModelPart("trophies").pivot(0, 0.42f, -0.16f)
                .box(0, 0, 0, 0.40f, 0.06f, 0.03f).color(0xd9d2bd));
        // Head wrap for all headhunters (distinct from the scavenger hood).
        head.child(new ModelPart("warPaint").pivot(0, 0.20f, -0.135f)
                .box(0, 0, 0, 0.27f, 0.035f, 0.02f).color(0xe8e2cc));
        // Medic sash.
        torso.child(new ModelPart("medicSash").pivot(0, 0.32f, -0.155f)
                .box(0, 0, 0, 0.10f, 0.46f, 0.02f).color(0xc8d6ce));
        // Guard shoulder plate.
        torso.child(new ModelPart("guardPlate").pivot(-0.30f, 0.56f, 0)
                .box(0, 0, 0, 0.17f, 0.10f, 0.30f).color(0x6e747c));
        return new EntityModel(root);
    }
}
