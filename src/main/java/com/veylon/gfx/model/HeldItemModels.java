package com.veylon.gfx.model;

import com.veylon.item.ItemType;
import com.veylon.item.ToolKind;

import java.util.EnumMap;
import java.util.Map;

/**
 * First-person held item shapes. Categories share silhouettes (all pickaxes
 * look like pickaxes); material tier comes from the item color on the business
 * end. Models are built lying along -Z with the grip at the origin.
 */
public final class HeldItemModels {

    private enum Shape {
        PICKAXE, AXE, SPEAR, KNIFE, TORCH, BERRY, MEAT, FOOD, DRINK, MEDICAL,
        BANDAGE, SPLINT, BOTTLE, POULTICE, WORKBENCH, BLOCK, MATERIAL, GEAR, GENERIC,
        BOW, LONG_GUN, PISTOL, BOMB
    }

    private static final Map<Shape, EntityModel> CACHE = new EnumMap<>(Shape.class);

    private HeldItemModels() {
    }

    public static EntityModel of(ItemType type) {
        Shape s = shapeOf(type);
        EntityModel m = CACHE.computeIfAbsent(s, HeldItemModels::build);
        tint(m, s, type);
        return m;
    }

    private static Shape shapeOf(ItemType t) {
        if (t.tool == ToolKind.BOW) {
            return Shape.BOW;
        }
        if (t.tool == ToolKind.FIREARM) {
            return t == ItemType.FLINTLOCK_PISTOL ? Shape.PISTOL : Shape.LONG_GUN;
        }
        if (t.tool == ToolKind.THROWN) {
            return Shape.BOMB;
        }
        if (t.tool == ToolKind.PICKAXE) {
            return Shape.PICKAXE;
        }
        if (t.tool == ToolKind.AXE) {
            return Shape.AXE;
        }
        if (t.tool == ToolKind.WEAPON) {
            return Shape.SPEAR;
        }
        if (t.tool == ToolKind.KNIFE) {
            return Shape.KNIFE;
        }
        if (t == ItemType.TORCH) {
            return Shape.TORCH;
        }
        if (t == ItemType.WATERSKIN_EMPTY || t == ItemType.WATERSKIN_DIRTY
                || t == ItemType.WATERSKIN_CLEAN) {
            return Shape.DRINK;
        }
        if (t.isMedical()) {
            return switch (t) {
                case BANDAGE -> Shape.BANDAGE;
                case SPLINT -> Shape.SPLINT;
                case ANTISEPTIC -> Shape.BOTTLE;
                case HERBAL_POULTICE -> Shape.POULTICE;
                default -> Shape.MEDICAL;
            };
        }
        if (t == ItemType.BERRY || t == ItemType.DRIED_BERRY) {
            return Shape.BERRY;
        }
        if (t == ItemType.RAW_MEAT || t == ItemType.COOKED_MEAT
                || t == ItemType.DRIED_MEAT || t == ItemType.SPOILED_MEAT) {
            return Shape.MEAT;
        }
        if (t.isEdible()) {
            return Shape.FOOD;
        }
        if (t.isEquippable()) {
            return Shape.GEAR;
        }
        if (t == ItemType.WORKBENCH) {
            return Shape.WORKBENCH;
        }
        if (t.places() != null) {
            return Shape.BLOCK;
        }
        return Shape.MATERIAL;
    }

    /** Applies the item color to the parts tagged as tintable. */
    private static void tint(EntityModel m, Shape s, ItemType t) {
        // Food colors are deliberately darker and less saturated than their UI
        // swatches. Under HDR daylight the old direct tint made berries read as
        // a neon-pink construction instead of fruit.
        if (s == Shape.BERRY) {
            float r = t == ItemType.DRIED_BERRY ? 0.24f : 0.43f;
            float g = t == ItemType.DRIED_BERRY ? 0.055f : 0.045f;
            float b = t == ItemType.DRIED_BERRY ? 0.075f : 0.11f;
            m.part("tint").color(r, g, b);
            m.part("tint2").color(r * 0.78f, g * 0.85f, b * 0.82f);
            m.part("tint3").color(r * 0.90f, g * 0.72f, b * 0.78f);
            return;
        }
        if (s == Shape.MEAT) {
            m.part("tint").color(t.r * 0.68f, t.g * 0.62f, t.b * 0.56f);
            m.part("tint2").color(t.r * 0.50f, t.g * 0.48f, t.b * 0.44f);
            return;
        }
        for (int i = 1; i <= 4; i++) {
            ModelPart p = m.part(i == 1 ? "tint" : "tint" + i);
            if (p.sizeX > 0) {
                float shade = 1f - (i - 1) * 0.08f;
                p.color(t.r * shade, t.g * shade, t.b * shade);
            }
        }
    }

    private static EntityModel build(Shape s) {
        ModelPart root = new ModelPart("root");
        switch (s) {
            case PICKAXE -> {
                root.child(new ModelPart("handle").pivot(0, 0, 0)
                        .box(0, 0.17f, 0, 0.045f, 0.44f, 0.045f).color(0x6a4e30));
                ModelPart head = new ModelPart("tint").pivot(0, 0.40f, 0)
                        .box(0, 0, 0, 0.30f, 0.055f, 0.05f).color(0x888888);
                head.rotZ = 0;
                // Two drooping tips make the classic pick silhouette.
                head.child(new ModelPart("tipL").pivot(-0.16f, -0.04f, 0)
                        .box(0, 0, 0, 0.06f, 0.10f, 0.045f).color(0x777777));
                head.child(new ModelPart("tipR").pivot(0.16f, -0.04f, 0)
                        .box(0, 0, 0, 0.06f, 0.10f, 0.045f).color(0x777777));
                root.child(head);
            }
            case AXE -> {
                root.child(new ModelPart("handle").pivot(0, 0, 0)
                        .box(0, 0.17f, 0, 0.045f, 0.44f, 0.045f).color(0x6a4e30));
                ModelPart blade = new ModelPart("tint").pivot(0.075f, 0.36f, 0)
                        .box(0, 0, 0, 0.12f, 0.14f, 0.045f).color(0x888888);
                blade.child(new ModelPart("edge").pivot(0.075f, 0, 0)
                        .box(0, 0, 0, 0.035f, 0.19f, 0.04f).color(0xb8b8b8));
                root.child(blade);
            }
            case SPEAR -> {
                root.child(new ModelPart("shaft").pivot(0, 0.10f, 0)
                        .box(0, 0.32f, 0, 0.04f, 0.95f, 0.04f).color(0x6a4e30));
                ModelPart tip = new ModelPart("tint").pivot(0, 0.82f, 0)
                        .box(0, 0.05f, 0, 0.06f, 0.16f, 0.045f).color(0x999999);
                tip.child(new ModelPart("point").pivot(0, 0.14f, 0)
                        .box(0, 0.02f, 0, 0.03f, 0.08f, 0.03f).color(0xb8b8b8));
                root.child(tip);
                root.child(new ModelPart("lash").pivot(0, 0.72f, 0)
                        .box(0, 0, 0, 0.055f, 0.05f, 0.055f).color(0x4a3a24));
            }
            case KNIFE -> {
                root.child(new ModelPart("grip").pivot(0, 0, 0)
                        .box(0, 0.06f, 0, 0.05f, 0.14f, 0.05f).color(0x4a3a24));
                root.child(new ModelPart("tint").pivot(0, 0.14f, 0)
                        .box(0, 0.10f, 0, 0.035f, 0.22f, 0.06f).color(0xcccccc));
            }
            case TORCH -> {
                root.child(new ModelPart("stick").pivot(0, 0, 0)
                        .box(0, 0.15f, 0, 0.05f, 0.38f, 0.05f).color(0x5a4430));
                root.child(new ModelPart("head").pivot(0, 0.36f, 0)
                        .box(0, 0.04f, 0, 0.09f, 0.11f, 0.09f)
                        .color(0xffb24a).emissive(1.1f));
            }
            case BERRY -> {
                // Five offset lobes and a broad calyx survive the oblique
                // first-person angle and read as a berry cluster, not a cube.
                root.child(new ModelPart("tint").pivot(-0.045f, 0.075f, -0.018f)
                        .box(0, 0, 0, 0.065f, 0.075f, 0.065f).color(0x6e0b1c));
                root.child(new ModelPart("tint2").pivot(0.035f, 0.072f, 0.022f)
                        .box(0, 0, 0, 0.066f, 0.073f, 0.066f).color(0x570916));
                root.child(new ModelPart("tint3").pivot(0.005f, 0.125f, -0.025f)
                        .box(0, 0, 0, 0.062f, 0.070f, 0.062f).color(0x650a19));
                root.child(new ModelPart("lobe4").pivot(0.055f, 0.125f, 0.025f)
                        .box(0, 0, 0, 0.055f, 0.064f, 0.055f).color(0x500914));
                root.child(new ModelPart("lobe5").pivot(-0.040f, 0.135f, 0.025f)
                        .box(0, 0, 0, 0.055f, 0.062f, 0.055f).color(0x780d22));
                root.child(new ModelPart("stem").pivot(0, 0.19f, 0)
                        .box(0, 0, 0, 0.014f, 0.085f, 0.014f).color(0x25472a));
                root.child(new ModelPart("leafL").pivot(-0.035f, 0.18f, 0)
                        .box(0, 0, 0, 0.075f, 0.016f, 0.050f).color(0x315f35));
                root.child(new ModelPart("leafR").pivot(0.040f, 0.185f, 0.005f)
                        .box(0, 0, 0, 0.070f, 0.016f, 0.045f).color(0x3c7040));
            }
            case MEAT -> {
                root.child(new ModelPart("tint").pivot(0, 0.075f, 0)
                        .box(0, 0, 0, 0.17f, 0.095f, 0.12f).color(0x984b31));
                root.child(new ModelPart("tint2").pivot(0.055f, 0.125f, 0.01f)
                        .box(0, 0, 0, 0.075f, 0.065f, 0.08f).color(0x7f3d29));
                root.child(new ModelPart("bone").pivot(-0.105f, 0.075f, 0)
                        .box(0, 0, 0, 0.075f, 0.027f, 0.027f).color(0xe0d6ba));
            }
            case FOOD -> {
                root.child(new ModelPart("stalk").pivot(0, 0.08f, 0)
                        .box(0, 0, 0, 0.022f, 0.18f, 0.022f).color(0x3f6834));
                root.child(new ModelPart("tint").pivot(-0.045f, 0.10f, 0)
                        .box(0, 0, 0, 0.095f, 0.035f, 0.065f).color(0x57904c));
                root.child(new ModelPart("tint2").pivot(0.045f, 0.145f, 0)
                        .box(0, 0, 0, 0.095f, 0.035f, 0.065f).color(0x4a7d41));
            }
            case DRINK -> {
                root.child(new ModelPart("tint").pivot(0, 0.05f, 0)
                        .box(0, 0.04f, 0, 0.14f, 0.20f, 0.09f).color(0x6a4e30));
                root.child(new ModelPart("neck").pivot(0, 0.20f, 0)
                        .box(0, 0.02f, 0, 0.05f, 0.06f, 0.05f).color(0x4a3a24));
            }
            case MEDICAL -> {
                root.child(new ModelPart("box").pivot(0, 0.05f, 0)
                        .box(0, 0.02f, 0, 0.15f, 0.11f, 0.11f).color(0xd8d4c8));
                // Mark both broad faces: the viewmodel can roll past either side
                // during bob/use animation, and a one-sided cross reads as a cube.
                root.child(new ModelPart("crossVFront").pivot(0, 0.08f, -0.057f)
                        .box(0, 0, 0, 0.03f, 0.08f, 0.005f).color(0xb03028));
                root.child(new ModelPart("crossHFront").pivot(0, 0.08f, -0.057f)
                        .box(0, 0, 0, 0.08f, 0.03f, 0.005f).color(0xb03028));
                root.child(new ModelPart("crossVBack").pivot(0, 0.08f, 0.057f)
                        .box(0, 0, 0, 0.03f, 0.08f, 0.005f).color(0xb03028));
                root.child(new ModelPart("crossHBack").pivot(0, 0.08f, 0.057f)
                        .box(0, 0, 0, 0.08f, 0.03f, 0.005f).color(0xb03028));
                root.child(new ModelPart("latch").pivot(0, 0.137f, 0)
                        .box(0, 0, 0, 0.045f, 0.018f, 0.12f).color(0x8f918d));
            }
            case BANDAGE -> {
                root.child(new ModelPart("roll").pivot(0, 0.085f, 0)
                        .box(0, 0, 0, 0.16f, 0.12f, 0.12f).color(0xd8d2c4));
                root.child(new ModelPart("wrap").pivot(0, 0.085f, -0.063f)
                        .box(0, 0, 0, 0.09f, 0.125f, 0.008f).color(0xeee9dd));
                root.child(new ModelPart("core").pivot(0.082f, 0.085f, 0)
                        .box(0, 0, 0, 0.012f, 0.05f, 0.05f).color(0x8b765f));
            }
            case SPLINT -> {
                for (int x : new int[]{-1, 1}) {
                    root.child(new ModelPart(x < 0 ? "slatL" : "slatR").pivot(x * 0.052f, 0.13f, 0)
                            .box(0, 0, 0, 0.035f, 0.28f, 0.045f).color(0x8a6236));
                }
                root.child(new ModelPart("tieTop").pivot(0, 0.20f, 0)
                        .box(0, 0, 0, 0.14f, 0.025f, 0.055f).color(0xd2c5a8));
                root.child(new ModelPart("tieBottom").pivot(0, 0.07f, 0)
                        .box(0, 0, 0, 0.14f, 0.025f, 0.055f).color(0xd2c5a8));
            }
            case BOTTLE -> {
                root.child(new ModelPart("bottle").pivot(0, 0.09f, 0)
                        .box(0, 0, 0, 0.12f, 0.17f, 0.09f).color(0x5f9273));
                root.child(new ModelPart("neck").pivot(0, 0.20f, 0)
                        .box(0, 0, 0, 0.052f, 0.07f, 0.052f).color(0x84aa8e));
                root.child(new ModelPart("stopper").pivot(0, 0.245f, 0)
                        .box(0, 0, 0, 0.065f, 0.028f, 0.065f).color(0x5b432c));
                root.child(new ModelPart("label").pivot(0, 0.105f, -0.047f)
                        .box(0, 0, 0, 0.078f, 0.07f, 0.006f).color(0xd8d4c8));
            }
            case POULTICE -> {
                root.child(new ModelPart("cloth").pivot(0, 0.075f, 0)
                        .box(0, 0, 0, 0.17f, 0.10f, 0.13f).color(0xb9ad91));
                root.child(new ModelPart("herbs").pivot(0, 0.135f, -0.01f)
                        .box(0, 0, 0, 0.11f, 0.045f, 0.09f).color(0x45693a));
                root.child(new ModelPart("tie").pivot(0, 0.145f, 0)
                        .box(0, 0, 0, 0.025f, 0.11f, 0.025f).color(0x765b37));
            }
            case WORKBENCH -> {
                // A miniature table silhouette is much more legible in hand than
                // the generic placeable cube used by terrain blocks.
                root.child(new ModelPart("tint").pivot(0, 0.15f, 0)
                        .box(0, 0, 0, 0.23f, 0.055f, 0.17f).color(0x9a7138));
                root.child(new ModelPart("apronFront").pivot(0, 0.11f, -0.065f)
                        .box(0, 0, 0, 0.20f, 0.055f, 0.035f).color(0x654825));
                root.child(new ModelPart("apronSide").pivot(0.082f, 0.11f, 0)
                        .box(0, 0, 0, 0.035f, 0.055f, 0.14f).color(0x654825));
                root.child(new ModelPart("tint2").pivot(-0.08f, 0.045f, -0.055f)
                        .box(0, 0, 0, 0.04f, 0.16f, 0.04f).color(0x755329));
                root.child(new ModelPart("legFR").pivot(0.08f, 0.045f, -0.055f)
                        .box(0, 0, 0, 0.04f, 0.16f, 0.04f).color(0x755329));
                root.child(new ModelPart("legBL").pivot(-0.08f, 0.045f, 0.055f)
                        .box(0, 0, 0, 0.04f, 0.16f, 0.04f).color(0x755329));
                root.child(new ModelPart("legBR").pivot(0.08f, 0.045f, 0.055f)
                        .box(0, 0, 0, 0.04f, 0.16f, 0.04f).color(0x755329));
            }
            case BLOCK -> root.child(new ModelPart("tint").pivot(0, 0.08f, 0)
                    .box(0, 0, 0, 0.17f, 0.17f, 0.17f).color(0x888888));
            case MATERIAL -> {
                root.child(new ModelPart("tint").pivot(0, 0.05f, 0)
                        .box(0, 0.01f, 0, 0.14f, 0.09f, 0.11f).color(0x888888));
                root.child(new ModelPart("tint2").pivot(-0.03f, 0.11f, 0.01f)
                        .box(0, 0, 0, 0.08f, 0.05f, 0.07f).color(0x777777));
            }
            case GEAR -> {
                root.child(new ModelPart("tint").pivot(0, 0.06f, 0)
                        .box(0, 0.03f, 0, 0.18f, 0.16f, 0.06f).color(0x7a5a34));
                root.child(new ModelPart("strap").pivot(0, 0.16f, 0)
                        .box(0, 0, 0, 0.20f, 0.03f, 0.05f).color(0x4a3a24));
            }
            case BOW -> {
                // Vertical stave with angled tips; the string closes the arc.
                root.child(new ModelPart("tint").pivot(0, 0.28f, 0)
                        .box(0, -0.24f, 0, 0.045f, 0.48f, 0.06f).color(0x7a5a34));
                ModelPart tipTop = new ModelPart("tipTop").pivot(0, 0.52f, 0);
                tipTop.rotX = 0.5f;
                tipTop.child(new ModelPart("tipTopArm").pivot(0, 0.07f, 0.02f)
                        .box(0, 0, 0, 0.04f, 0.16f, 0.05f).color(0x6a4c2c));
                root.child(tipTop);
                ModelPart tipBot = new ModelPart("tipBot").pivot(0, 0.04f, 0);
                tipBot.rotX = -0.5f;
                tipBot.child(new ModelPart("tipBotArm").pivot(0, -0.07f, 0.02f)
                        .box(0, 0, 0, 0.04f, 0.16f, 0.05f).color(0x6a4c2c));
                root.child(tipBot);
                root.child(new ModelPart("string").pivot(0, 0.28f, 0.085f)
                        .box(0, -0.31f, 0, 0.012f, 0.62f, 0.012f).color(0xd8d2c0));
                root.child(new ModelPart("gripWrap").pivot(0, 0.28f, 0)
                        .box(0, -0.05f, 0, 0.06f, 0.10f, 0.075f).color(0x4a3a24));
            }
            case LONG_GUN -> {
                // Full-length musket lying along -Z: stock, barrel, lock, ramrod.
                root.child(new ModelPart("tint").pivot(0, 0.06f, 0.22f)
                        .box(0, 0, 0.02f, 0.06f, 0.11f, 0.30f).color(0x5a4028));
                root.child(new ModelPart("tint2").pivot(0, 0.115f, -0.02f)
                        .box(0, 0, -0.24f, 0.055f, 0.055f, 0.62f).color(0x6a4e30));
                root.child(new ModelPart("barrel").pivot(0, 0.15f, -0.24f)
                        .box(0, 0, -0.34f, 0.042f, 0.042f, 0.92f).color(0x565c64));
                root.child(new ModelPart("muzzle").pivot(0, 0.15f, -0.60f)
                        .box(0, 0, -0.02f, 0.055f, 0.055f, 0.05f).color(0x3c4046));
                root.child(new ModelPart("lock").pivot(0.038f, 0.11f, 0.06f)
                        .box(0, 0, 0, 0.03f, 0.06f, 0.09f).color(0x3c4046));
                root.child(new ModelPart("hammer").pivot(0.045f, 0.16f, 0.075f)
                        .box(0, 0, 0, 0.02f, 0.05f, 0.03f).color(0x2c3036));
                root.child(new ModelPart("ramrod").pivot(0, 0.085f, -0.30f)
                        .box(0, 0, -0.20f, 0.016f, 0.016f, 0.55f).color(0x8a8e94));
                root.child(new ModelPart("trigger").pivot(0, 0.055f, 0.08f)
                        .box(0, -0.03f, 0, 0.016f, 0.045f, 0.03f).color(0x2c3036));
            }
            case PISTOL -> {
                root.child(new ModelPart("tint").pivot(0, 0.03f, 0.10f)
                        .box(0, -0.05f, 0.01f, 0.05f, 0.13f, 0.09f).color(0x5a4028));
                root.child(new ModelPart("tint2").pivot(0, 0.10f, 0)
                        .box(0, 0, -0.26f, 0.05f, 0.05f, 0.36f).color(0x6a4e30));
                root.child(new ModelPart("barrel").pivot(0, 0.125f, -0.10f)
                        .box(0, 0, -0.22f, 0.038f, 0.038f, 0.34f).color(0x565c64));
                root.child(new ModelPart("hammer").pivot(0.03f, 0.14f, 0.06f)
                        .box(0, 0, 0, 0.02f, 0.05f, 0.028f).color(0x2c3036));
                root.child(new ModelPart("guard").pivot(0, 0.035f, 0.02f)
                        .box(0, -0.02f, 0, 0.014f, 0.03f, 0.06f).color(0x8a8e94));
            }
            case BOMB -> {
                root.child(new ModelPart("tint").pivot(0, 0.10f, 0)
                        .box(0, 0, 0, 0.15f, 0.15f, 0.15f).color(0x3a3d42));
                root.child(new ModelPart("band").pivot(0, 0.10f, 0)
                        .box(0, 0.055f, 0, 0.16f, 0.03f, 0.16f).color(0x63676d));
                root.child(new ModelPart("fusePost").pivot(0, 0.185f, 0)
                        .box(0, 0, 0, 0.035f, 0.045f, 0.035f).color(0x2c2c30));
                ModelPart fuse = new ModelPart("fuse").pivot(0.01f, 0.22f, 0);
                fuse.rotZ = 0.5f;
                fuse.child(new ModelPart("fuseCord").pivot(0, 0.03f, 0)
                        .box(0, 0, 0, 0.016f, 0.08f, 0.016f).color(0x9a8258));
                root.child(fuse);
                root.child(new ModelPart("spark").pivot(0.045f, 0.29f, 0)
                        .box(0, 0, 0, 0.03f, 0.03f, 0.03f)
                        .color(0xffb24a).emissive(0.9f));
            }
            default -> root.child(new ModelPart("tint").pivot(0, 0.07f, 0)
                    .box(0, 0, 0, 0.12f, 0.12f, 0.12f).color(0x888888));
        }
        return new EntityModel(root);
    }
}
