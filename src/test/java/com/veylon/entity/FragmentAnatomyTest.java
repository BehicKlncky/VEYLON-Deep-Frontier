package com.veylon.entity;

import com.veylon.entity.BodyFragment.Piece;
import com.veylon.entity.Creature.CreatureType;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where every kind of body comes apart, checked against the anatomy each
 * species actually has, without reading the renderer's models: those checks
 * are {@code gfx.FragmentAnatomyModelTest}'s.
 *
 * <p>The expected tables below are written out by hand from the model
 * builders, one row per piece: name, the part it is cut at, the part whose box
 * it flies as, the piece it was cut from, the joints it owns and the child
 * parts it leaves to other pieces. Positions in the geometry tests are
 * likewise hand-summed from the builders' pivots and boxes, not computed by
 * the formula under test.
 */
class FragmentAnatomyTest {

    private static final float EPS = 1e-5f;

    private static final Map<BodyFamily, String[][]> EXPECTED = new EnumMap<>(BodyFamily.class);

    static {
        EXPECTED.put(BodyFamily.HUMANOID, new String[][] {
                {"torso", "torso", "torso", "-", "root,torso", "neck,arm_l,arm_r"},
                {"head", "neck", "head", "torso", "neck,head", ""},
                {"upper_arm_l", "arm_l", "arm_l", "torso", "arm_l", "forearm_l"},
                {"upper_arm_r", "arm_r", "arm_r", "torso", "arm_r", "forearm_r"},
                {"forearm_l", "forearm_l", "forearm_l", "upper_arm_l", "forearm_l", ""},
                {"forearm_r", "forearm_r", "forearm_r", "upper_arm_r", "forearm_r", ""},
                {"thigh_l", "leg_l", "leg_l", "torso", "leg_l", "shin_l"},
                {"thigh_r", "leg_r", "leg_r", "torso", "leg_r", "shin_r"},
                {"shin_l", "shin_l", "shin_l", "thigh_l", "shin_l", ""},
                {"shin_r", "shin_r", "shin_r", "thigh_r", "shin_r", ""},
        });
        EXPECTED.put(BodyFamily.DEER, quadruped("neck,head", false));
        EXPECTED.put(BodyFamily.WOLF, quadruped("neck,head,ear_l,ear_r", true));
        EXPECTED.put(BodyFamily.THORNHORN, quadruped("neck,head", true));
        EXPECTED.put(BodyFamily.STALKER, quadruped("neck,head", true));
        EXPECTED.put(BodyFamily.HARE, new String[][] {
                {"torso", "body", "body", "-", "root,body", "head_joint,tail"},
                {"head", "head_joint", "head", "torso", "head_joint,head,ear_l,ear_r", ""},
                {"leg_fl", "leg_fl", "leg_fl", "torso", "leg_fl,leg_fl_lower", ""},
                {"leg_fr", "leg_fr", "leg_fr", "torso", "leg_fr,leg_fr_lower", ""},
                {"leg_bl", "leg_bl", "leg_bl", "torso", "leg_bl,leg_bl_lower", ""},
                {"leg_br", "leg_br", "leg_br", "torso", "leg_br,leg_br_lower", ""},
                {"tail", "tail", "tail", "torso", "tail,tail_tip", ""},
        });
        EXPECTED.put(BodyFamily.BIRD, new String[][] {
                {"torso", "body", "body", "-", "root,body", "head,wing0_l,wing0_r,wing1_l,wing1_r,tail"},
                {"head", "head", "head", "torso", "head", ""},
                {"tail", "tail", "tail", "torso", "tail", ""},
                {"wing0_l", "wing0_l", "wing0_l", "torso", "wing0_l", ""},
                {"wing0_r", "wing0_r", "wing0_r", "torso", "wing0_r", ""},
                {"wing1_l", "wing1_l", "wing1_l", "torso", "wing1_l", ""},
                {"wing1_r", "wing1_r", "wing1_r", "torso", "wing1_r", ""},
        });
    }

    /** The shared quadruped rows; the deer's tail tip is too small to fly alone. */
    private static String[][] quadruped(String head, boolean tailTip) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"torso", "body", "body", "-", "root,body", "tail"});
        rows.add(new String[] {"head", "neck", "head", "torso", head, ""});
        for (String leg : List.of("fl", "fr", "bl", "br")) {
            rows.add(new String[] {"upper_leg_" + leg, "leg_" + leg, "leg_" + leg, "torso", "leg_" + leg,
                    "leg_" + leg + "_lower"});
        }
        for (String leg : List.of("fl", "fr", "bl", "br")) {
            String lower = "leg_" + leg + "_lower";
            rows.add(new String[] {"lower_leg_" + leg, lower, lower, "upper_leg_" + leg, lower, ""});
        }
        if (tailTip) {
            rows.add(new String[] {"tail", "tail", "tail", "torso", "tail", "tail_tip"});
            rows.add(new String[] {"tail_tip", "tail_tip", "tail_tip", "tail", "tail_tip", ""});
        } else {
            rows.add(new String[] {"tail", "tail", "tail", "torso", "tail,tail_tip", ""});
        }
        return rows.toArray(new String[0][]);
    }

    // ------------------------------------------------------------------
    // Coverage and identity
    // ------------------------------------------------------------------

    @Test
    void everyCreatureTypeAndThePersonHaveTheirOwnTableBuiltOnce() {
        Set<BodyFamily> seen = EnumSet.noneOf(BodyFamily.class);
        for (CreatureType type : CreatureType.values()) {
            BodyFamily family = BodyFamily.of(type);
            assertSame(type, family.creature, type + " maps to its own family");
            assertTrue(seen.add(family), type + " must not share a family");
            assertSame(FragmentAnatomy.of(type), family.anatomy());
            assertSame(family.anatomy(), FragmentAnatomy.of(family), "tables are built once, not per call");
        }
        assertNull(BodyFamily.HUMANOID.creature);
        assertSame(FragmentAnatomy.humanoid(), BodyFamily.HUMANOID.anatomy());
        assertEquals(CreatureType.values().length + 1, BodyFamily.values().length,
                "one family per creature type plus the humanoid");
        assertEquals(EnumSet.allOf(BodyFamily.class), EXPECTED.keySet(), "this test covers every family");
    }

    @ParameterizedTest
    @EnumSource(BodyFamily.class)
    void eachBodyComesApartAtItsOwnJointsIntoItsOwnPieces(BodyFamily family) {
        FragmentAnatomy a = family.anatomy();
        String[][] rows = EXPECTED.get(family);
        assertEquals(rows.length, a.pieces.size(), family + " piece count");
        assertTrue(a.pieces.size() <= FragmentAnatomy.MAX_PIECES);
        for (int i = 0; i < rows.length; i++) {
            FragmentPiece p = a.piece(i);
            String where = family + " piece " + i;
            assertEquals(i, p.id, where + " id is its index");
            assertSame(family, p.family);
            assertSame(a, p.anatomy);
            assertEquals(rows[i][0], p.name, where + " name");
            assertSame(p, a.piece(p.name));
            assertEquals(rows[i][1], p.rootPart, where + " is cut at");
            assertEquals(rows[i][2], p.boxPart, where + " flies as the box of");
            assertEquals(rows[i][3], p.parent < 0 ? "-" : a.piece(p.parent).name, where + " was cut from");
            assertEquals(p.parent >= 0, p.severed);
            assertEquals(rows[i][4], String.join(",", p.ownedParts), where + " owns");
            assertEquals(rows[i][5], String.join(",", p.excludedParts), where + " leaves to other pieces");
            assertEquals(p.rootPart, a.joint(p.rootJoint).name);
            assertEquals(p.boxPart, a.joint(p.boxJoint).name);
        }
        assertFalse(a.piece(0).severed, "the core is what the others are cut from");
    }

    @ParameterizedTest
    @EnumSource(BodyFamily.class)
    void everyJointBelongsToExactlyOnePieceAndNoPieceIsABoxlessSliver(BodyFamily family) {
        FragmentAnatomy a = family.anatomy();
        assertTrue(a.jointCount() <= FragmentAnatomy.MAX_JOINTS);
        Set<String> owned = new HashSet<>();
        for (FragmentPiece p : a.pieces) {
            for (String part : p.ownedParts) {
                assertTrue(owned.add(part), family + ": " + part + " is owned twice");
                assertEquals(p.id, a.joint(a.jointIndex(part)).piece, part + " knows its owner");
            }
            assertTrue(a.joint(p.boxJoint).boxed, p + " flies as a real box");
        }
        assertEquals(a.jointCount(), owned.size(), family + ": every joint has an owner");
        FragmentAnatomy.Joint root = a.joint(0);
        assertEquals(-1, root.parent);
        assertFalse(root.boxed, "the model root is a box-less connector");
        assertEquals(0, root.piece, "and belongs to the core");
        for (int j = 1; j < a.jointCount(); j++) {
            FragmentAnatomy.Joint joint = a.joint(j);
            assertTrue(joint.parent >= 0 && joint.parent < j, joint.name + " is listed after its parent");
            if (!joint.boxed) {
                for (FragmentPiece p : a.pieces) {
                    assertFalse(p.boxJoint == j, "a box-less " + joint.name + " cannot be a piece's box");
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Collision boxes and mass
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(BodyFamily.class)
    void collisionBoxesAreTheModelBoxesWithRealExtentsAndMass(BodyFamily family) {
        FragmentAnatomy a = family.anatomy();
        for (FragmentPiece p : a.pieces) {
            FragmentAnatomy.Joint box = a.joint(p.boxJoint);
            assertTrue(p.halfX > 0 && p.halfY > 0 && p.halfZ > 0, p + " has positive extents");
            float volume = box.sizeX * box.sizeY * box.sizeZ;
            if (p.mergedParts.isEmpty()) {
                assertEquals(box.sizeX * 0.5f, p.halfX, 0f, p + " is its box, not a bone radius");
                assertEquals(box.sizeY * 0.5f, p.halfY, 0f);
                assertEquals(box.sizeZ * 0.5f, p.halfZ, 0f);
                assertEquals(box.boxX, p.boxOffsetX, 0f);
                assertEquals(box.boxY, p.boxOffsetY, 0f);
                assertEquals(box.boxZ, p.boxOffsetZ, 0f);
            } else {
                for (String merged : p.mergedParts) {
                    FragmentAnatomy.Joint tip = a.joint(a.jointIndex(merged));
                    assertTrue(tip.splitTip, merged + " is a split half");
                    volume += tip.sizeX * tip.sizeY * tip.sizeZ;
                }
            }
            assertEquals(volume, p.volume, 1e-9f, p + " volume");
            assertEquals(volume * BodyFragmentConstants.DENSITY, p.mass, 1e-6f, p + " mass follows volume");
            assertEquals(1f / p.mass, p.inverseMass, 1e-3f);
            assertEquals(Math.max(BodyFragmentConstants.MIN_HALF_EXTENT, Math.max(p.halfX, p.halfZ)),
                    p.halfWidth, 0f, p + " sweep width");
            assertEquals(Math.max(BodyFragmentConstants.MIN_HALF_EXTENT, p.halfY), p.halfHeight, 0f,
                    p + " sweep height");
        }
    }

    @ParameterizedTest
    @EnumSource(BodyFamily.class)
    void onlySplitHalvesLongEnoughToReadAsLimbsFlyAlone(BodyFamily family) {
        FragmentAnatomy a = family.anatomy();
        for (int j = 0; j < a.jointCount(); j++) {
            FragmentAnatomy.Joint tip = a.joint(j);
            if (!tip.splitTip) {
                continue;
            }
            float longest = Math.max(tip.sizeX, Math.max(tip.sizeY, tip.sizeZ));
            boolean alone = a.piece(tip.piece).rootJoint == j;
            assertEquals(longest >= BodyFragmentConstants.MIN_SEPARATE_PIECE, alone,
                    tip.name + " (" + longest + " m) separates only above MIN_SEPARATE_PIECE");
            if (!alone) {
                assertTrue(a.piece(tip.piece).mergedParts.contains(tip.name),
                        tip.name + " is merged into the collision box of the piece it stays with");
            }
        }
    }

    @Test
    void quadrupedLegsAndTailsLieAlongZAndBirdWingsReachSideways() {
        // Hand-summed from CreatureModels: pivots down the tree plus the box centre.
        FragmentAnatomy deer = FragmentAnatomy.of(CreatureType.DEER);
        assertPiece(deer, "torso", 0, 0.79f, 0, 0.21f, 0.21f, 0.425f);
        assertPiece(deer, "upper_leg_fl", -0.1344f, 0.435f, -0.323f, 0.055f, 0.145f, 0.055f);
        assertPiece(deer, "upper_leg_br", 0.1344f, 0.435f, 0.323f, 0.055f, 0.145f, 0.055f);
        assertPiece(deer, "lower_leg_fl", -0.1344f, 0.145f, -0.323f, 0.055f, 0.145f, 0.055f);
        assertPivot(deer, "lower_leg_fl", -0.1344f, 0.29f, -0.323f);
        assertPiece(deer, "head", 0, 1.136f, -0.568f, 0.11f, 0.12f, 0.17f);
        assertPivot(deer, "head", 0, 0.916f, -0.408f);
        assertPiece(deer, "tail", 0, 0.93f, 0.48f, 0.04f, 0.04f, 0.06f);

        FragmentAnatomy wolf = FragmentAnatomy.of(CreatureType.WOLF);
        assertPiece(wolf, "tail", 0, 0.69f, 0.495f, 0.05f, 0.05f, 0.085f);
        assertPiece(wolf, "tail_tip", 0, 0.69f, 0.665f, 0.05f, 0.05f, 0.085f);
        assertPivot(wolf, "tail_tip", 0, 0.69f, 0.58f);
        assertPivot(wolf, "head", 0, 0.692f, -0.384f);

        FragmentAnatomy thornhorn = FragmentAnatomy.of(CreatureType.THORNHORN);
        assertPiece(thornhorn, "lower_leg_br", 0.2304f, 0.1375f, 0.475f, 0.1f, 0.1375f, 0.1f);
        assertPiece(thornhorn, "head", 0, 1.106f, -0.82f, 0.2f, 0.18f, 0.2f);

        FragmentAnatomy stalker = FragmentAnatomy.of(CreatureType.STALKER);
        assertPiece(stalker, "head", 0, 1.028f, -0.496f, 0.09f, 0.08f, 0.15f);
        assertPiece(stalker, "tail_tip", 0, 0.87f, 0.64f, 0.025f, 0.025f, 0.1f);

        FragmentAnatomy hare = FragmentAnatomy.of(CreatureType.HARE);
        assertPiece(hare, "leg_fl", -0.07f, 0.06f, -0.10f, 0.025f, 0.06f, 0.025f);
        assertPiece(hare, "head", 0, 0.28f, -0.22f, 0.075f, 0.075f, 0.08f);
        assertPivot(hare, "head", 0, 0.23f, -0.18f);
        assertPiece(hare, "tail", 0, 0.22f, 0.24f, 0.035f, 0.035f, 0.03f);

        FragmentAnatomy bird = FragmentAnatomy.of(CreatureType.BIRD);
        assertPiece(bird, "wing0_l", -0.24f, 0.23f, -0.06f, 0.15f, 0.01f, 0.05f);
        assertPivot(bird, "wing0_l", -0.08f, 0.23f, -0.06f);
        assertPiece(bird, "wing1_r", 0.24f, 0.23f, 0.08f, 0.15f, 0.01f, 0.05f);
        assertPiece(bird, "head", 0, 0.24f, -0.20f, 0.055f, 0.05f, 0.06f);
        assertPiece(bird, "tail", 0, 0.18f, 0.22f, 0.03f, 0.015f, 0.06f);
        FragmentPiece wing = bird.piece("wing0_l");
        assertEquals(0.15f, wing.halfWidth, EPS, "a wing sweeps its full span");
        assertEquals(BodyFragmentConstants.MIN_HALF_EXTENT, wing.halfHeight, 0f,
                "its 2 cm thickness is only inflated for the sweep");
        assertTrue(BodySkeleton.of(CreatureType.BIRD).radius[2] > wing.halfY,
                "the true extent is the board, not the ragdoll's bone radius");
    }

    // ------------------------------------------------------------------
    // Wounds
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(BodyFamily.class)
    void everyCutLeavesOneWoundOnEachSideLyingOnABoxFace(BodyFamily family) {
        FragmentAnatomy a = family.anatomy();
        int total = 0;
        for (FragmentPiece p : a.pieces) {
            total += p.cuts.size();
            if (p.severed) {
                FragmentCut own = p.cuts.getFirst();
                assertTrue(own.ownEnd, p + " lists its own end first");
                assertEquals(p.rootJoint, own.joint, p + " was cut at its root");
                FragmentPiece parent = a.piece(p.parent);
                long onParent = parent.cuts.stream()
                        .filter(c -> !c.ownEnd && c.severed == p.id && c.joint == p.rootJoint).count();
                assertEquals(1, onParent, parent + " carries exactly one wound where " + p + " came off");
            }
            float[] centre = {p.centreX, p.centreY, p.centreZ};
            float[] half = {p.halfX, p.halfY, p.halfZ};
            for (FragmentCut cut : p.cuts) {
                String where = p + " wound at " + a.joint(cut.joint).name;
                assertEquals(p.id, cut.piece);
                assertEquals(1f, Math.abs(cut.side), 0f);
                float[] at = {cut.centreX, cut.centreY, cut.centreZ};
                float[] size = {cut.sizeX, cut.sizeY, cut.sizeZ};
                assertEquals(centre[cut.axis] + cut.side * half[cut.axis], at[cut.axis], EPS, where + " on a face");
                assertEquals(0f, size[cut.axis], 0f, where + " is flat");
                for (int ax = 0; ax < 3; ax++) {
                    if (ax == cut.axis) {
                        continue;
                    }
                    assertTrue(size[ax] > 0, where + " has an area");
                    assertTrue(at[ax] - size[ax] * 0.5f >= centre[ax] - half[ax] - EPS
                            && at[ax] + size[ax] * 0.5f <= centre[ax] + half[ax] + EPS, where + " stays on its face");
                }
            }
        }
        assertEquals(2 * (a.pieces.size() - 1), total, family + ": two wounds per cut joint");
    }

    @Test
    void jointsBuriedInsideTheParentAreCutWhereTheLimbLeaves() {
        // A hare's hip pivot sits 4 cm inside the body box, nearer its front
        // face than its underside; the wound belongs on the underside.
        FragmentAnatomy hare = FragmentAnatomy.of(CreatureType.HARE);
        FragmentCut hip = woundFor(hare, "torso", "leg_fl");
        assertEquals(1, hip.axis, "under the body");
        assertEquals(-1f, hip.side, 0f);
        // A shoulder outside the torso keeps the human rule: on the flank.
        FragmentCut shoulder = woundFor(FragmentAnatomy.humanoid(), "torso", "upper_arm_l");
        assertEquals(0, shoulder.axis, "on the flank");
        assertEquals(-1f, shoulder.side, 0f);
        assertEquals(0.104f, shoulder.sizeY, EPS, "the arm's width runs up the flank");
        assertEquals(0.12f, shoulder.sizeZ, EPS, "and its depth stays depth");
        // A wing's board is only 2 cm thick; so is its wound.
        FragmentCut wing = woundFor(FragmentAnatomy.of(CreatureType.BIRD), "torso", "wing0_l");
        assertEquals(0, wing.axis);
        assertEquals(0.016f, wing.sizeY, EPS);
        assertEquals(0.08f, wing.sizeZ, EPS);
    }

    private static FragmentCut woundFor(FragmentAnatomy a, String on, String severed) {
        int id = a.piece(severed).id;
        for (FragmentCut c : a.piece(on).cuts) {
            if (c.severed == id) {
                return c;
            }
        }
        throw new AssertionError(on + " has no wound for " + severed);
    }

    // ------------------------------------------------------------------
    // Legacy human identity
    // ------------------------------------------------------------------

    @Test
    void theTenLegacyHumanPiecesKeepTheirIdsAndReadTheHumanoidTable() {
        FragmentAnatomy human = FragmentAnatomy.humanoid();
        assertEquals(Piece.values().length, human.pieces.size());
        for (Piece p : Piece.values()) {
            FragmentPiece d = human.piece(p.ordinal());
            assertSame(d, p.definition, p + " is humanoid piece " + p.ordinal());
            assertEquals(p.name().toLowerCase(Locale.ROOT), d.name);
            assertEquals(d.rootPart, p.rootPart);
            assertEquals(d.boxPart, p.boxPart);
            assertEquals(d.excludedParts, p.excludedParts);
            assertEquals(d.centreY, p.centreY, 0f);
            assertEquals(d.pivotX, p.pivotX, 0f);
            assertEquals(d.mass, p.mass, 0f);
            assertEquals(d.severed, p.severed);

            BodyFragment legacy = new BodyFragment(p);
            assertSame(p, legacy.piece);
            assertSame(d, legacy.definition);
            assertSame(human.restPose(), legacy.pose, "a legacy piece lies in the rest pose");
            assertSame(p, new BodyFragment(d, human.restPose()).piece, "a humanoid piece has its legacy id");
        }
        FragmentAnatomy deer = FragmentAnatomy.of(CreatureType.DEER);
        assertNull(new BodyFragment(deer.piece(0), deer.restPose()).piece,
                "no animal piece can be mistaken for a world.fragments v1 id");
    }

    // ------------------------------------------------------------------
    // Poses
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(BodyFamily.class)
    void theRestPosePlacesEveryPieceExactlyAtItsTableNumbers(BodyFamily family) {
        FragmentAnatomy a = family.anatomy();
        FragmentPose rest = a.restPose();
        assertSame(rest, FragmentPose.rest(a), "one rest pose per family, built with the table");
        assertTrue(rest.isRest());
        Vector3f v = new Vector3f();
        Quaternionf q = new Quaternionf();
        for (FragmentPiece p : a.pieces) {
            rest.pieceCentre(p.id, v);
            assertEquals(p.centreX, v.x, 0f, p + " centre");
            assertEquals(p.centreY, v.y, 0f, p + " centre");
            assertEquals(p.centreZ, v.z, 0f, p + " centre");
            rest.pieceRotation(p.id, q);
            assertEquals(0f, q.x, 0f, p + " is unturned at rest");
            assertEquals(0f, q.y, 0f, p + " is unturned at rest");
            assertEquals(0f, q.z, 0f, p + " is unturned at rest");
            assertEquals(1f, q.w, 0f, p + " is unturned at rest");
            assertEquals(1f, rest.pieceScale(p.id), 0f, p + " is its table size at rest");
            rest.jointOrigin(p.rootJoint, v);
            assertEquals(p.pivotY, v.y, 0f, p + " cut joint");
            rest.jointOrigin(a.joint(p.rootJoint).parent, v);
            assertEquals(p.anchorX, v.x, 0f, p + " anchor");
            assertEquals(p.anchorY, v.y, 0f, p + " anchor");
            assertEquals(p.anchorZ, v.z, 0f, p + " anchor");
        }
    }

    @Test
    void aPosedLegSwingsAboutItsHipAndCarriesItsLowerLegAlong() {
        FragmentAnatomy deer = FragmentAnatomy.of(CreatureType.DEER);
        float swing = 0.6f;
        FragmentPose pose = new FragmentPose.Recorder(deer)
                .set(deer.jointIndex("leg_fl"), swing, 0, 0, 0, 0, 0, 1).snapshot();
        assertFalse(pose.isRest());
        float c = (float) Math.cos(swing), s = (float) Math.sin(swing);
        // Rx(θ)·(0, y, 0) = (0, y cos θ, y sin θ), hung from the hip.
        assertCentre(pose, deer.piece("upper_leg_fl"), -0.1344f, 0.58f - 0.145f * c, -0.323f - 0.145f * s);
        assertCentre(pose, deer.piece("lower_leg_fl"), -0.1344f, 0.58f - 0.435f * c, -0.323f - 0.435f * s);
        Vector3f knee = pose.jointOrigin(deer.jointIndex("leg_fl_lower"), new Vector3f());
        assertEquals(0.58f - 0.29f * c, knee.y, EPS, "the knee moves with the thigh");
        assertEquals(-0.323f - 0.29f * s, knee.z, EPS);
        assertTurn(pose, deer.piece("lower_leg_fl"), new Quaternionf().rotationX(swing));
        assertCentre(pose, deer.piece("upper_leg_fr"), 0.1344f, 0.435f, -0.323f);
    }

    @Test
    void aWingBeatsOnTopOfTheBodysOwnRollAndBob() {
        FragmentAnatomy bird = FragmentAnatomy.of(CreatureType.BIRD);
        float roll = 0.2f, bob = 0.03f, beat = 0.9f;
        FragmentPose pose = new FragmentPose.Recorder(bird)
                .set(bird.jointIndex("body"), 0, 0, roll, 0, bob, 0, 1)
                .set(bird.jointIndex("wing0_l"), 0, 0, beat, 0, 0, 0, 1)
                .snapshot();
        // Body frame T(0, 0.18 + bob, 0)·Rz(roll); the wing hangs at its pivot
        // turned by roll, then beats about Z: every turn is about Z, so the
        // board's offset (−0.16, 0, 0) is turned by roll + beat.
        float px = -0.08f, py = 0.05f;
        float cr = (float) Math.cos(roll), sr = (float) Math.sin(roll);
        float ct = (float) Math.cos(roll + beat), st = (float) Math.sin(roll + beat);
        float x = px * cr - py * sr - 0.16f * ct;
        float y = 0.18f + bob + px * sr + py * cr - 0.16f * st;
        assertCentre(pose, bird.piece("wing0_l"), x, y, -0.06f);
        assertTurn(pose, bird.piece("wing0_l"), new Quaternionf().rotationZ(roll + beat));
        assertCentre(pose, bird.piece("torso"), 0, 0.18f + bob, 0);
        assertTurn(pose, bird.piece("torso"), new Quaternionf().rotationZ(roll));
    }

    @Test
    void aPieceStartsWhereThePosedModelHadItAtTheBodysHeading() {
        FragmentAnatomy wolf = FragmentAnatomy.of(CreatureType.WOLF);
        FragmentPose pose = new FragmentPose.Recorder(wolf)
                .set(wolf.jointIndex("body"), -0.08f, 0, 0.03f, 0, 0.02f, 0, 1.01f)
                .set(wolf.jointIndex("tail"), 0.35f, 0.2f, 0, 0, 0, 0, 1)
                .set(wolf.jointIndex("neck"), 0, 0, 0, 0, -0.14f, 0, 1)
                .set(wolf.jointIndex("head"), 0.18f, 0, 0, 0, 0, 0, 1)
                .snapshot();
        // The breathing scale must not leak into the rigid turn: parts compose Rz·Ry·Rx.
        assertTurn(pose, wolf.piece("torso"), new Quaternionf().rotationZ(0.03f).rotateX(-0.08f));
        assertEquals(1f, pose.pieceRotation(0, new Quaternionf()).lengthSquared(), 1e-6f,
                "a scaled body still turns by a unit quaternion");
        // It does reach the box: the torso and the tail that hangs from it are drawn 1 % larger.
        assertEquals(1.01f, pose.pieceScale(wolf.piece("tail").id), 1e-6f);
        assertEquals(1f, pose.pieceScale(wolf.piece("head").id), 0f, "the neck hangs from the model root");
        BodyFragment torso = new BodyFragment(wolf.piece("torso"), pose);
        assertEquals(wolf.piece("torso").halfZ * 1.01f, torso.halfZ, 1e-6f, "the box as drawn at death");
        float yaw = (float) Math.toRadians(-117);
        Quaternionf heading = new Quaternionf().rotationY(yaw);
        Vector3f want = new Vector3f();
        Vector3f got = new Vector3f();
        for (FragmentPiece p : wolf.pieces) {
            BodyFragment f = new BodyFragment(p, pose).placeAt(12.3f, 41.7f, -5.2f, yaw);
            heading.transform(pose.pieceCentre(p.id, want)).add(12.3f, 41.7f, -5.2f);
            assertEquals(0f, want.distance(f.pos), EPS, p + " starts on the posed body");
            assertTrue(Math.abs(new Quaternionf(heading).mul(pose.pieceRotation(p.id, new Quaternionf()))
                    .dot(f.orientation)) > 1 - 1e-6f, p + " is turned like the posed body");
            // The cut joint travels with the piece.
            heading.transform(pose.jointOrigin(p.rootJoint, want)).add(12.3f, 41.7f, -5.2f);
            f.jointToWorld(p.rootJoint, got);
            assertEquals(0f, want.distance(got), EPS, p + " joint");
        }
    }

    @Test
    void aSnapshotIsImmutableAndRejectsImpossibleTransforms() {
        FragmentAnatomy hare = FragmentAnatomy.of(CreatureType.HARE);
        int ear = hare.jointIndex("ear_l");
        FragmentPose.Recorder recorder = new FragmentPose.Recorder(hare).set(ear, 0, 0, 0.08f, 0, 0, 0, 1);
        FragmentPose first = recorder.snapshot();
        recorder.set(ear, 0, 0, -0.5f, 0, 0, 0, 1);
        assertEquals(0.08f, first.rotZ(ear), 0f, "a snapshot never sees later recording");

        assertThrows(IllegalArgumentException.class, () -> new FragmentPose.Recorder(hare)
                .set(ear, Float.NaN, 0, 0, 0, 0, 0, 1).snapshot(), "NaN rotation");
        assertThrows(IllegalArgumentException.class, () -> new FragmentPose.Recorder(hare)
                .set(ear, 0, 0, 0, Float.POSITIVE_INFINITY, 0, 0, 1).snapshot(), "infinite offset");
        assertThrows(IllegalArgumentException.class, () -> new FragmentPose.Recorder(hare)
                .set(ear, 0, 0, 0, 0, 0, 0, 0).snapshot(), "zero scale");
        assertThrows(IllegalArgumentException.class, () -> new FragmentPose.Recorder(hare)
                .set(ear, 0, 0, 0, 0, 0, 0, -1).snapshot(), "negative scale");
        assertThrows(IndexOutOfBoundsException.class, () -> new FragmentPose.Recorder(hare)
                .set(hare.jointCount(), 0, 0, 0, 0, 0, 0, 1));
        FragmentAnatomy deer = FragmentAnatomy.of(CreatureType.DEER);
        assertThrows(IllegalArgumentException.class, () -> new BodyFragment(deer.piece(0), first),
                "a hare's pose cannot place a deer's piece");
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    @Test
    void aTableWithAMissingOrMisorderedParentFailsClearly() {
        fails("hangs from nope", b -> b.joint("root", null, 0, 0, 0)
                .part("body", "nope", 0, 1, 0, 0, 0, 0, 1, 1, 1).piece("torso", "body", "body"));
        fails("hangs from arm", b -> b.joint("root", null, 0, 0, 0)
                .part("hand", "arm", 0, 1, 0, 0, 0, 0, 1, 1, 1)
                .part("arm", "root", 0, 1, 0, 0, 0, 0, 1, 1, 1).piece("torso", "arm", "arm"));
        fails("first joint must be the model root", b -> b.part("body", "root", 0, 1, 0, 0, 0, 0, 1, 1, 1)
                .piece("torso", "body", "body"));
        fails("only the first joint", b -> b.joint("root", null, 0, 0, 0).joint("loose", null, 0, 0, 0)
                .piece("torso", "loose", "loose"));
        fails("declared twice", b -> body(b).part("body", "root", 0, 1, 0, 0, 0, 0, 1, 1, 1));
    }

    @Test
    void aTableWithoutRealGeometryFailsClearly() {
        fails("non-finite pivot", b -> b.joint("root", null, 0, 0, 0)
                .part("body", "root", Float.NaN, 1, 0, 0, 0, 0, 1, 1, 1).piece("torso", "body", "body"));
        fails("non-finite box", b -> b.joint("root", null, 0, 0, 0)
                .part("body", "root", 0, 1, 0, 0, Float.POSITIVE_INFINITY, 0, 1, 1, 1).piece("torso", "body", "body"));
        fails("not positive", b -> b.joint("root", null, 0, 0, 0)
                .part("body", "root", 0, 1, 0, 0, 0, 0, 1, 0, 1).piece("torso", "body", "body"));
        fails("not positive", b -> b.joint("root", null, 0, 0, 0)
                .part("body", "root", 0, 1, 0, 0, 0, 0, 1, 1, -0.2f).piece("torso", "body", "body"));
        fails("box-less connector", b -> b.part("root", null, 0, 0, 0, 0, 0, 0, 1, 1, 1)
                .part("body", "root", 0, 1, 0, 0, 0, 0, 1, 1, 1).piece("torso", "body", "body"));
    }

    @Test
    void overlappingOrMissingOwnershipFailsClearly() {
        fails("root of two pieces", b -> body(b).piece("again", "body", "body"));
        fails("piece torso is declared twice", b -> body(b)
                .part("arm", "body", 0, 0, 0, 0, 0, 0, 0.2f, 0.5f, 0.2f).piece("torso", "arm", "arm"));
        fails("belongs to piece", b -> body(b)
                .joint("neck", "body", 0, 0.5f, 0)
                .part("head", "neck", 0, 0.1f, 0, 0, 0, 0, 0.2f, 0.2f, 0.2f)
                .piece("head", "head", "head").piece("neck", "neck", "head"));
        fails("which has none", b -> body(b).joint("neck", "body", 0, 0.5f, 0)
                .part("head", "neck", 0, 0.1f, 0, 0, 0, 0, 0.2f, 0.2f, 0.2f).piece("head", "neck", "neck"));
        fails("not the first box below its cut", b -> body(b)
                .part("arm", "body", 0.3f, 0, 0, 0, -0.2f, 0, 0.2f, 0.4f, 0.2f)
                .part("hand", "arm", 0, -0.4f, 0, 0, -0.1f, 0, 0.2f, 0.2f, 0.2f)
                .piece("arm", "arm", "hand"));
        fails("is under no piece", b -> body(b).part("leg", "root", 0.1f, 0.5f, 0, 0, -0.25f, 0, 0.2f, 0.5f, 0.2f));
        fails("is under no piece", b -> body(b).joint("neck", "root", 0, 1.5f, 0)
                .part("head", "neck", 0, 0.1f, 0, 0, 0, 0, 0.2f, 0.2f, 0.2f).piece("head", "head", "head"));
        fails("cannot be cut at the model root", b -> body(b).piece("everything", "root", "body"));
        fails("must hang from the model root", b -> b.joint("root", null, 0, 0, 0)
                .part("body", "root", 0, 1, 0, 0, 0, 0, 0.4f, 0.4f, 0.8f)
                .part("arm", "body", 0.3f, 0, 0, 0, -0.2f, 0, 0.1f, 0.4f, 0.1f)
                .piece("arm", "arm", "arm").piece("torso", "body", "body"));
        fails("not a joint", b -> body(b).piece("tail", "tail", "tail"));
        fails("has no pieces", b -> b.joint("root", null, 0, 0, 0));
        fails("has no joints", b -> b);
    }

    @Test
    void theSplitRuleIsEnforcedBothWays() {
        fails("must be a piece of its own", b -> body(b)
                .part("arm", "body", 0.3f, 0, 0, 0, -0.3f, 0, 0.1f, 0.6f, 0.1f).split("arm", "forearm", true)
                .piece("arm", "arm", "arm"));
        fails("must stay with arm", b -> body(b)
                .part("arm", "body", 0.3f, 0, 0, 0, -0.05f, 0, 0.04f, 0.1f, 0.04f).split("arm", "forearm", true)
                .piece("arm", "arm", "arm").piece("forearm", "forearm", "forearm"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> FragmentAnatomy.builder(BodyFamily.BIRD).joint("root", null, 0, 0, 0)
                        .split("root", "tip", true));
        assertTrue(e.getMessage().contains("not a boxed joint"), e.getMessage());
    }

    @Test
    void tooManyJointsOrPiecesFailClearly() {
        fails("a pose holds at most", b -> {
            body(b);
            for (int i = 0; i < FragmentAnatomy.MAX_JOINTS; i++) {
                b.part("spine" + i, "body", 0, i * 0.1f, 0, 0, 0, 0, 0.1f, 0.1f, 0.1f);
            }
            return b;
        });
        fails("at most " + FragmentAnatomy.MAX_PIECES, b -> {
            body(b);
            for (int i = 0; i < FragmentAnatomy.MAX_PIECES; i++) {
                b.part("quill" + i, "body", 0, i * 0.1f, 0, 0, 0, 0, 0.1f, 0.1f, 0.1f)
                        .piece("quill" + i, "quill" + i, "quill" + i);
            }
            return b;
        });
    }

    /** A valid minimal body: the model root and one boxed core. */
    private static FragmentAnatomy.Builder body(FragmentAnatomy.Builder b) {
        return b.joint("root", null, 0, 0, 0).part("body", "root", 0, 1, 0, 0, 0, 0, 0.4f, 0.4f, 0.8f)
                .piece("torso", "body", "body");
    }

    private static void fails(String message, java.util.function.UnaryOperator<FragmentAnatomy.Builder> table) {
        FragmentAnatomy.Builder b = table.apply(FragmentAnatomy.builder(BodyFamily.BIRD));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, b::build,
                "expected a table error containing: " + message);
        assertTrue(e.getMessage().contains(message), "error names the problem: " + e.getMessage());
        assertTrue(e.getMessage().startsWith("BIRD anatomy"), "error names the body: " + e.getMessage());
    }

    // ------------------------------------------------------------------

    private static void assertPiece(FragmentAnatomy a, String name, float x, float y, float z,
                                    float hx, float hy, float hz) {
        FragmentPiece p = a.piece(name);
        assertNotNull(p, a.family + " has a " + name);
        assertEquals(x, p.centreX, EPS, p + " centre x");
        assertEquals(y, p.centreY, EPS, p + " centre y");
        assertEquals(z, p.centreZ, EPS, p + " centre z");
        assertEquals(hx, p.halfX, EPS, p + " half x");
        assertEquals(hy, p.halfY, EPS, p + " half y");
        assertEquals(hz, p.halfZ, EPS, p + " half z");
    }

    private static void assertPivot(FragmentAnatomy a, String name, float x, float y, float z) {
        FragmentPiece p = a.piece(name);
        assertEquals(x, p.pivotX, EPS, p + " cut x");
        assertEquals(y, p.pivotY, EPS, p + " cut y");
        assertEquals(z, p.pivotZ, EPS, p + " cut z");
    }

    private static void assertCentre(FragmentPose pose, FragmentPiece p, float x, float y, float z) {
        Vector3f c = pose.pieceCentre(p.id, new Vector3f());
        assertEquals(x, c.x, EPS, p + " posed centre x");
        assertEquals(y, c.y, EPS, p + " posed centre y");
        assertEquals(z, c.z, EPS, p + " posed centre z");
    }

    private static void assertTurn(FragmentPose pose, FragmentPiece p, Quaternionf want) {
        Quaternionf got = pose.pieceRotation(p.id, new Quaternionf());
        assertTrue(Math.abs(got.dot(want)) > 1 - 1e-6f, p + " turned " + got + ", expected " + want);
    }
}
