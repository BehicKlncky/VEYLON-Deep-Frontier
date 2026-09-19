package com.veylon.gfx;

import com.veylon.entity.BodyFragment;
import com.veylon.entity.BodyFragment.Piece;
import com.veylon.entity.NpcAppearance;
import com.veylon.gfx.model.Animator;
import com.veylon.gfx.model.EntityModel;
import com.veylon.gfx.model.ModelPart;
import com.veylon.gfx.model.NpcModels;
import com.veylon.settlement.NpcArchetype;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A piece of a person blown apart draws the shared humanoid cut down to that
 * piece: its own parts, in the look of the person it came from, and nothing
 * the neighbouring pieces draw.
 *
 * <p>Headless: the model is plain CPU data, and "drawn" here is
 * {@code ModelPart.render}'s own rule — start at a part, stop at any hidden
 * one — without the GL calls.
 */
class FragmentIsolationTest {

    private final EntityModel model = NpcModels.get();

    @Test
    void anIsolatedPieceDrawsExactlyItsOwnPartsInEveryLook() {
        for (NpcAppearance look : everyLook()) {
            for (Piece p : Piece.values()) {
                model.resetPose();
                wear(look);
                Set<String> worn = drawn(model.root);
                Map<String, Boolean> flags = visibility(model.root);

                ModelPart top = Animator.isolatePart(model, p.rootPart, p.excludedParts);

                Set<String> own = subtree(model.root.find(p.rootPart));
                for (String excluded : p.excludedParts) {
                    own.removeAll(subtree(model.root.find(excluded)));
                }
                Set<String> expected = new HashSet<>(own);
                expected.retainAll(worn);
                String where = p + " of " + describe(look);
                assertSame(model.root.find(p.rootPart), top, where + ": drawn from its root part");
                assertEquals(expected, drawn(top), where + ": its own parts, as worn, and nothing else");
                for (Map.Entry<String, Boolean> part : visibility(model.root).entrySet()) {
                    boolean shown = part.getValue();
                    if (own.contains(part.getKey())) {
                        assertEquals(flags.get(part.getKey()), shown,
                                where + ": isolation must not touch " + part.getKey());
                    } else {
                        assertFalse(shown, where + ": " + part.getKey() + " belongs to another piece");
                    }
                }
                for (String name : drawn(top)) {
                    assertFalse(model.part(name).drawsWholeBox(),
                            where + ": " + name + " must not draw a box that reaches into another piece");
                }
            }
        }
    }

    @Test
    void accessoriesThePersonWasNotWearingStayHidden() {
        int hiddenInPieces = 0;
        for (NpcAppearance look : everyLook()) {
            for (Piece p : Piece.values()) {
                model.resetPose();
                wear(look);
                List<String> notWorn = new ArrayList<>();
                visibility(model.root).forEach((name, shown) -> {
                    if (!shown) {
                        notWorn.add(name);
                    }
                });
                Animator.isolatePart(model, p.rootPart, p.excludedParts);
                ModelPart top = model.root.find(p.rootPart);
                for (String name : notWorn) {
                    assertFalse(model.part(name).visible,
                            p + " of " + describe(look) + " put on " + name);
                    if (top.find(name) != null) {
                        hiddenInPieces++;
                    }
                }
            }
        }
        assertTrue(hiddenInPieces > 1_000, "the check must cover accessories inside the pieces too");
    }

    @Test
    void eachPieceOfAKnownPersonIsWhatTheirModelShowsThere() {
        assertEquals(Set.of("torso", "vest", "friendlyBadge", "guardPlate"),
                piece(look(NpcArchetype.GUARD, false, false), Piece.TORSO));
        int vest = 0x5e4a30;
        ModelPart guardVest = model.part("vest");
        assertEquals(((vest >> 16) & 0xFF) / 255f, guardVest.r, 1e-6f, "a guard's vest colour");
        assertEquals(((vest >> 8) & 0xFF) / 255f, guardVest.g, 1e-6f, "a guard's vest colour");
        assertEquals((vest & 0xFF) / 255f, guardVest.b, 1e-6f, "a guard's vest colour");

        assertEquals(Set.of("torso", "vest", "pack", "traderRoll", "traderSatchelL", "traderSatchelR"),
                piece(look(NpcArchetype.TRADER, false, false), Piece.TORSO));
        assertEquals(Set.of("neck", "head", "traderAntenna", "traderLamp"),
                piece(look(NpcArchetype.TRADER, false, false), Piece.HEAD));
        assertEquals(Set.of("torso", "vest", "brutePadL", "brutePadR", "bruteChest", "trophies"),
                piece(look(NpcArchetype.BRUTE, false, false), Piece.TORSO));
        assertEquals(Set.of("torso", "vest", "leaderMantle", "trophies"),
                piece(look(NpcArchetype.LEADER, false, false), Piece.TORSO));
        assertEquals(Set.of("neck", "head", "leaderCrest", "warPaint"),
                piece(look(NpcArchetype.LEADER, false, false), Piece.HEAD));
        assertEquals(Set.of("neck", "head", "hood", "visor"),
                piece(look(NpcArchetype.SCAVENGER, true, false), Piece.HEAD));
        assertEquals(Set.of("torso", "vest", "raiderPadL", "raiderPadR", "raiderSpear", "raiderSpearTip"),
                piece(look(NpcArchetype.SCAVENGER, true, false), Piece.TORSO));

        NpcAppearance guard = look(NpcArchetype.GUARD, false, false);
        assertEquals(Set.of("arm_l"), piece(guard, Piece.UPPER_ARM_L));
        assertEquals(Set.of("forearm_r"), piece(guard, Piece.FOREARM_R));
        assertEquals(Set.of("leg_r"), piece(guard, Piece.THIGH_R));
        assertEquals(Set.of("shin_l"), piece(guard, Piece.SHIN_L));
    }

    @Test
    void onlyALimbCutBelowItsSplitDrawsItsOwnHalfAndResetPoseUndoesIt() {
        Map<Piece, Set<String>> halves = Map.of(
                Piece.UPPER_ARM_L, Set.of("arm_l"), Piece.UPPER_ARM_R, Set.of("arm_r"),
                Piece.THIGH_L, Set.of("leg_l"), Piece.THIGH_R, Set.of("leg_r"));
        NpcAppearance guard = look(NpcArchetype.GUARD, false, false);
        for (Piece p : Piece.values()) {
            model.resetPose();
            wear(guard);
            Animator.isolatePart(model, p.rootPart, p.excludedParts);
            Set<String> forced = forcedSplits(model.root);
            assertEquals(halves.getOrDefault(p, Set.of()), forced,
                    p + ": forced split drawing belongs exactly on a split part whose tip was cut off");
            for (String name : forced) {
                assertFalse(model.part(name).drawsWholeBox(), name + " must draw only its own half");
            }

            // The model is shared: the next whole person must get whole limbs back.
            model.resetPose();
            wear(guard);
            assertEquals(Set.of(), forcedSplits(model.root), "resetPose clears forced split drawing");
            for (String limb : List.of("arm_l", "arm_r", "leg_l", "leg_r")) {
                assertTrue(model.part(limb).drawsWholeBox(),
                        limb + " of a whole straight person draws its original single box after " + p);
            }
        }
    }

    @Test
    void aForcedSplitDrawsTheHalfBoxEvenWhileTheChainIsStraight() {
        ModelPart upper = new ModelPart("upper").box(0, -0.5f, 0, 0.2f, 1f, 0.2f).split("lower", true);
        ModelPart lower = upper.find("lower");
        assertTrue(upper.isSplitTip(lower));
        assertFalse(upper.isSplitTip(new ModelPart("lower")), "only the tip split made");
        assertFalse(upper.isSplitTip(null));
        assertFalse(lower.isSplitTip(upper));

        assertTrue(upper.drawsWholeBox(), "a straight split draws the original single box");
        assertEquals(0.5f, upper.sizeY, 1e-6f, "the part's own box is its half");
        assertEquals(-0.25f, upper.boxY, 1e-6f, "the part's own box is its half");

        upper.forceSplitDraw = true;
        assertFalse(upper.drawsWholeBox(), "forced, a straight split draws its own half only");
        upper.resetPose();
        assertFalse(upper.forceSplitDraw, "resetPose clears the flag");
        assertTrue(upper.drawsWholeBox());

        lower.rotX = 0.4f;
        assertFalse(upper.drawsWholeBox(), "a bent chain draws two halves, as it always has");
        assertFalse(new ModelPart("plain").box(0, 0, 0, 1, 1, 1).drawsWholeBox(),
                "a part never split has no whole box to merge into");
    }

    // ------------------------------------------------------------------

    /** Every archetype and none, with each combination of the three look flags. */
    static List<NpcAppearance> everyLook() {
        List<NpcArchetype> archetypes = new ArrayList<>();
        archetypes.add(null);
        archetypes.addAll(List.of(NpcArchetype.values()));
        List<NpcAppearance> looks = new ArrayList<>();
        for (NpcArchetype a : archetypes) {
            for (int flags = 0; flags < 8; flags++) {
                NpcAppearance look = look(a, (flags & 1) != 0, (flags & 2) != 0);
                look.sick = (flags & 4) != 0;
                look.campIndex = flags;
                looks.add(look);
            }
        }
        return looks;
    }

    static NpcAppearance look(NpcArchetype archetype, boolean raider, boolean trader) {
        NpcAppearance look = new NpcAppearance();
        look.archetype = archetype;
        look.raider = raider;
        look.trader = trader;
        return look;
    }

    /** The pose the renderer runs, for one piece of a person with this look. */
    private Set<String> piece(NpcAppearance look, Piece p) {
        BodyFragment f = new BodyFragment(p);
        f.appearance.copyFrom(look);
        return drawn(Animator.poseFragment(model, f));
    }

    private void wear(NpcAppearance a) {
        Animator.applyAppearance(model, a.archetype, a.raider, a.trader, a.sick, a.campIndex);
    }

    private static String describe(NpcAppearance a) {
        return a.archetype + (a.raider ? " raider" : "") + (a.trader ? " trader" : "")
                + (a.sick ? " sick" : "");
    }

    /** Names {@code ModelPart.render} would reach from {@code part}: it stops at a hidden part. */
    static Set<String> drawn(ModelPart part) {
        Set<String> out = new HashSet<>();
        if (part.visible) {
            out.add(part.name);
            for (ModelPart child : part.children) {
                out.addAll(drawn(child));
            }
        }
        return out;
    }

    private static Set<String> subtree(ModelPart part) {
        Set<String> out = new HashSet<>();
        out.add(part.name);
        for (ModelPart child : part.children) {
            out.addAll(subtree(child));
        }
        return out;
    }

    private static Map<String, Boolean> visibility(ModelPart part) {
        Map<String, Boolean> out = new java.util.HashMap<>();
        out.put(part.name, part.visible);
        for (ModelPart child : part.children) {
            out.putAll(visibility(child));
        }
        return out;
    }

    private static Set<String> forcedSplits(ModelPart part) {
        Set<String> out = new HashSet<>();
        if (part.forceSplitDraw) {
            out.add(part.name);
        }
        for (ModelPart child : part.children) {
            out.addAll(forcedSplits(child));
        }
        return out;
    }
}
