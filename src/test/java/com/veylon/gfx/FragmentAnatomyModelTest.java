package com.veylon.gfx;

import com.sun.management.ThreadMXBean;
import com.veylon.entity.BodyFamily;
import com.veylon.entity.BodyFragment;
import com.veylon.entity.BodyFragmentConstants;
import com.veylon.entity.BodySkeleton;
import com.veylon.entity.Creature;
import com.veylon.entity.Creature.CreatureState;
import com.veylon.entity.FragmentAnatomy;
import com.veylon.entity.FragmentPiece;
import com.veylon.entity.FragmentPose;
import com.veylon.entity.Npc;
import com.veylon.entity.NpcAppearance;
import com.veylon.gfx.model.AnatomyModels;
import com.veylon.gfx.model.Animator;
import com.veylon.gfx.model.EntityModel;
import com.veylon.gfx.model.ModelPart;
import com.veylon.settlement.NpcArchetype;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fragment tables held to the models they describe, and every body put
 * back together from its pieces.
 *
 * <p>The reassembly check poses a species' shared model with the living
 * animation, captures the pose the way a death does, cuts the body into its
 * pieces and draws each piece alone; every box must land where the whole
 * living model drew it, within {@link #TOLERANCE}. The living geometry is
 * rebuilt here from the real part tree the way {@code ModelPart.render} builds
 * it — translate by pivot and pose, then Rz·Ry·Rx, then scale, then the box —
 * independently of the tables and the pose code under test.
 */
class FragmentAnatomyModelTest {

    /**
     * Reassembly tolerance: float rounding through a chain of up to seven part
     * transforms at world coordinates in the tens. Everything else is exact.
     */
    static final float TOLERANCE = 1e-4f;
    private static final float[] HEADINGS = {0f, 117f, -63.5f, 200f};
    private static final float X = 12.3f, Y = 41.7f, Z = -5.2f;

    /** A living creature pose: state, speed as a share of the species' own, gait phase, clock. */
    private record CreatureScene(String label, CreatureState state, float run, float bobPhase, double time) {
    }

    private static final CreatureScene[] CREATURE_SCENES = {
            new CreatureScene("standing", CreatureState.WANDER, 0f, 0f, 0.0),
            new CreatureScene("mid-stride", CreatureState.FLEE, 1f, 0.49f, 1.3),
            new CreatureScene("attacking", CreatureState.ATTACK, 0.3f, 0.2f, 0.77),
            new CreatureScene("resting", CreatureState.REST, 0f, 0.1f, 2.4),
            new CreatureScene("grazing", CreatureState.GRAZE, 0.1f, 0.3f, 5.1),
            new CreatureScene("stalking", CreatureState.STALK, 0.5f, 0.8f, 3.3),
            new CreatureScene("wounded", CreatureState.FLEE_HURT, 1f, 1.7f, 0.41),
    };

    private record NpcScene(String label, Npc.NpcState state, float speed, float bobPhase, double time) {
    }

    private static final NpcScene[] NPC_SCENES = {
            new NpcScene("idle", Npc.NpcState.IDLE, 0f, 0f, 0.5),
            new NpcScene("running", Npc.NpcState.IDLE, 3.5f, 0.49f, 1.1),
            new NpcScene("attacking", Npc.NpcState.ATTACK, 1f, 0.3f, 0.3),
            new NpcScene("asleep", Npc.NpcState.SLEEP, 0f, 0f, 2.0),
            new NpcScene("healing", Npc.NpcState.HEAL, 0f, 0f, 4.2),
            new NpcScene("chopping", Npc.NpcState.GATHER_WOOD, 0f, 0f, 0.9),
    };

    private static final NpcAppearance[] LOOKS = {
            FragmentIsolationTest.look(NpcArchetype.GUARD, false, false),
            FragmentIsolationTest.look(NpcArchetype.SCAVENGER, true, false),
            FragmentIsolationTest.look(NpcArchetype.TRADER, false, false),
            FragmentIsolationTest.look(NpcArchetype.LEADER, false, false),
            FragmentIsolationTest.look(null, false, false),
    };

    // ------------------------------------------------------------------
    // Tables against models
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(BodyFamily.class)
    void everyTableMatchesItsModelPartForPart(BodyFamily family) {
        EntityModel model = AnatomyModels.modelOf(family);
        assertSame(family == BodyFamily.HUMANOID ? com.veylon.gfx.model.NpcModels.get()
                : com.veylon.gfx.model.CreatureModels.of(family.creature), model, "the shared model");
        assertDoesNotThrow(() -> AnatomyModels.validate(family.anatomy(), model));
    }

    @ParameterizedTest
    @EnumSource(BodyFamily.class)
    void isolatingEachPieceDrawsEveryBoxOfTheModelExactlyOnce(BodyFamily family) {
        EntityModel model = AnatomyModels.modelOf(family);
        FragmentAnatomy anatomy = family.anatomy();
        Map<String, Integer> drawnCount = new HashMap<>();
        for (FragmentPiece p : anatomy.pieces) {
            model.resetPose();
            ModelPart top = Animator.isolatePart(model, p.rootPart, p.excludedParts);
            assertSame(model.root.find(p.rootPart), top, p + " is drawn from its root part");
            Set<String> drawn = FragmentIsolationTest.drawn(top);
            for (String name : drawn) {
                ModelPart part = model.root.find(name);
                if (part.sizeX > 0) {
                    drawnCount.merge(name, 1, Integer::sum);
                }
                if (part.drawsWholeBox()) {
                    // Only a merged split draws its unsplit box, and then both halves are this piece's.
                    for (ModelPart child : part.children) {
                        if (part.isSplitTip(child)) {
                            assertTrue(drawn.contains(child.name), p + ": " + name
                                    + " draws a whole box reaching into another piece");
                        }
                    }
                }
            }
        }
        model.resetPose();
        List<ModelPart> all = new ArrayList<>();
        collect(model.root, all);
        for (ModelPart part : all) {
            if (part.sizeX > 0) {
                assertEquals(1, drawnCount.getOrDefault(part.name, 0),
                        family + ": box " + part.name + " must be drawn by exactly one piece");
            }
        }
    }

    @ParameterizedTest
    @EnumSource(BodyFamily.class)
    void everyRagdollBoneIsAJointInPlaceAndEveryCutIsABone(BodyFamily family) {
        FragmentAnatomy anatomy = family.anatomy();
        BodySkeleton s = family == BodyFamily.HUMANOID ? BodySkeleton.humanoid() : BodySkeleton.of(family.creature);
        FragmentPose rest = anatomy.restPose();
        float[][] at = new float[s.boneCount][];
        Set<String> bones = new HashSet<>();
        Vector3f origin = new Vector3f();
        for (int b = 0; b < s.boneCount; b++) {
            int j = anatomy.jointIndex(s.part[b]);
            assertTrue(j >= 0, family + ": ragdoll bone " + s.part[b] + " is a joint of the table");
            float[] base = s.parent[b] == BodySkeleton.TORSO ? new float[3] : at[s.parent[b]];
            at[b] = new float[] {base[0] + s.pivotX[b], base[1] + s.pivotY[b], base[2] + s.pivotZ[b]};
            rest.jointOrigin(j, origin);
            assertEquals(at[b][0], origin.x, 1e-5f, s.part[b] + " x");
            assertEquals(at[b][1], origin.y, 1e-5f, s.part[b] + " y");
            assertEquals(at[b][2], origin.z, 1e-5f, s.part[b] + " z");
            if (s.parent[b] != BodySkeleton.TORSO) {
                assertEquals(s.part[s.parent[b]], anatomy.joint(anatomy.joint(j).parent).name,
                        s.part[b] + " hangs from the same bone in both");
            }
            bones.add(s.part[b]);
        }
        for (FragmentPiece p : anatomy.pieces) {
            if (p.severed) {
                assertTrue(bones.contains(p.rootPart), p + " is cut at a real ragdoll joint, " + p.rootPart);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(BodyFamily.class)
    void theLivingAnimationMovesNothingButJoints(BodyFamily family) {
        EntityModel model = AnatomyModels.modelOf(family);
        FragmentAnatomy anatomy = family.anatomy();
        List<ModelPart> all = new ArrayList<>();
        collect(model.root, all);
        int checked = 0;
        for (Object[] living : livingBodies(family)) {
            model.resetPose();
            if (living[0] instanceof Npc n) {
                Animator.applyAppearance(model, n.archetype, n.raider, n.isTrader, n.sick, n.campIndex);
            }
            Map<String, float[]> baseline = new HashMap<>();
            for (ModelPart part : all) {
                baseline.put(part.name, transform(part));
            }
            pose(model, living);
            for (ModelPart part : all) {
                if (anatomy.jointIndex(part.name) < 0) {
                    assertArrayEquals(baseline.get(part.name), transform(part),
                            family + " " + living[2] + ": the animation moved " + part.name
                                    + ", which is not a joint, so a death pose would lose it");
                    checked++;
                }
            }
        }
        model.resetPose();
        assertTrue(checked > 0 || all.size() == anatomy.jointCount());
    }

    // ------------------------------------------------------------------
    // Reassembly
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(BodyFamily.class)
    void piecesReassembleIntoTheLivingBodyAtEveryHeadingAndPose(BodyFamily family) {
        EntityModel model = AnatomyModels.modelOf(family);
        FragmentAnatomy anatomy = family.anatomy();
        int boxes = 0;
        for (Object[] living : livingBodies(family)) {
            for (float heading : HEADINGS) {
                boxes += reassemble(family, model, anatomy, living, heading);
            }
        }
        assertTrue(boxes > 0);
        model.resetPose();
    }

    /** One body, one heading: returns how many boxes were compared. */
    private int reassemble(BodyFamily family, EntityModel model, FragmentAnatomy anatomy,
                           Object[] living, float heading) {
        com.veylon.entity.Entity body = (com.veylon.entity.Entity) living[0];
        double time = (double) living[1];
        String scene = family + " " + living[2] + " at " + heading + "°";
        body.pos.set(X, Y, Z);
        body.yaw = heading;

        // The living body, drawn as the renderer draws it.
        model.resetPose();
        pose(model, living);
        Matrix4f world = new Matrix4f().translate(X, Y, Z).rotateY((float) Math.toRadians(-heading));
        Map<String, Matrix4f> livingBoxes = new HashMap<>();
        Map<String, Matrix4f> livingFrames = new HashMap<>();
        walk(model.root, world, Set.of(), livingBoxes, livingFrames);
        Map<String, Matrix4f> wholeBoxes = new HashMap<>();
        for (FragmentPiece p : anatomy.pieces) {
            if (!p.mergedParts.isEmpty()) {
                wholeBoxes.put(p.name, wholeBox(model.root.find(p.boxPart), livingFrames.get(p.boxPart)));
            }
        }

        // The death.
        FragmentPose pose = body instanceof Npc n ? AnatomyModels.captureNpc(n, time)
                : AnatomyModels.captureCreature((Creature) body, time);
        for (int j = 0; j < anatomy.jointCount(); j++) {
            ModelPart part = model.root.find(anatomy.joint(j).name);
            assertArrayEquals(new float[] {0, 0, 0, 0, 0, 0, 1}, transform(part),
                    scene + ": capturing left " + part.name + " posed on the shared model");
        }
        float yaw = (float) Math.toRadians(-heading);
        List<BodyFragment> pieces = new ArrayList<>();
        for (FragmentPiece p : anatomy.pieces) {
            pieces.add(new BodyFragment(p, pose).placeAt(X, Y, Z, yaw));
        }

        // Each piece drawn alone, from the reset model carrying the snapshot.
        model.resetPose();
        if (body instanceof Npc n) {
            Animator.applyAppearance(model, n.archetype, n.raider, n.isTrader, n.sick, n.campIndex);
        }
        AnatomyModels.applyPose(model, pose);
        Map<String, String> drawnBy = new HashMap<>();
        Vector3f got = new Vector3f();
        Vector3f want = new Vector3f();
        int compared = 0;
        for (BodyFragment f : pieces) {
            FragmentPiece p = f.definition;
            String where = scene + ", " + p.name;
            Map<String, Matrix4f> own = new HashMap<>();
            walk(model.root.find(p.rootPart), f.rootTransform(new Matrix4f()),
                    new HashSet<>(p.excludedParts), own, new HashMap<>());
            for (Map.Entry<String, Matrix4f> box : own.entrySet()) {
                assertNull(drawnBy.put(box.getKey(), p.name), where + " also draws " + box.getKey());
                assertNotNull(livingBoxes.get(box.getKey()), where + " draws " + box.getKey() + ", which was hidden");
                assertBox(livingBoxes.get(box.getKey()), box.getValue(), where + " box " + box.getKey());
                compared++;
            }
            // The cut sits on the joint it was cut at.
            f.jointToWorld(p.rootJoint, got);
            livingFrames.get(p.rootPart).getTranslation(want);
            assertEquals(0f, want.distance(got), TOLERANCE, where + " cut joint");
            // The collision box is the box the living body drew there.
            Matrix4f expected = p.mergedParts.isEmpty() ? livingBoxes.get(p.boxPart) : wholeBoxes.get(p.name);
            assertCollisionBox(expected, f, where);
        }
        assertEquals(livingBoxes.keySet(), drawnBy.keySet(),
                scene + ": every box the living body drew belongs to exactly one piece");
        return compared;
    }

    // ------------------------------------------------------------------
    // Capture
    // ------------------------------------------------------------------

    @Test
    void aCaptureFreezesWhatTheAnimatorDrewAndLeavesTheSharedModelReset() {
        Creature bird = new Creature(null, Creature.CreatureType.BIRD);
        bird.vel.set(2f, 0f, 1f);
        bird.bobPhase = 0.3f;
        FragmentAnatomy anatomy = FragmentAnatomy.of(Creature.CreatureType.BIRD);
        int wing = anatomy.jointIndex("wing0_l");
        EntityModel model = AnatomyModels.modelOf(BodyFamily.BIRD);

        FragmentPose early = AnatomyModels.captureCreature(bird, 0.10);
        FragmentPose late = AnatomyModels.captureCreature(bird, 0.35);
        assertFalse(early.isRest(), "a flying bird is never captured standing still");
        Animator.poseCreature(model, bird, 0.10);
        float drawn = model.part("wing0_l").rotZ;
        assertEquals(drawn, early.rotZ(wing), 0f, "the wing is captured at the angle last drawn");
        assertNotEquals(early.rotZ(wing), late.rotZ(wing), 1e-3f, "mid-beat, not a fixed pose");

        Animator.poseCreature(model, bird, 0.9);
        assertEquals(drawn, early.rotZ(wing), 0f, "re-posing the shared model cannot reach a snapshot");
        FragmentPose again = AnatomyModels.captureCreature(bird, 0.10);
        for (int j = 0; j < anatomy.jointCount(); j++) {
            assertEquals(early.rotZ(j), again.rotZ(j), 0f, "same body, same clock, same pose");
            assertEquals(early.poseY(j), again.poseY(j), 0f);
            assertEquals(early.scale(j), again.scale(j), 0f);
        }

        Npc raider = new Npc(null, "Raider");
        raider.raider = true;
        raider.archetype = NpcArchetype.SCAVENGER;
        raider.state = Npc.NpcState.ATTACK;
        FragmentPose swing = AnatomyModels.captureNpc(raider, 0.3);
        int arm = FragmentAnatomy.humanoid().jointIndex("arm_r");
        EntityModel human = AnatomyModels.modelOf(BodyFamily.HUMANOID);
        Animator.poseNpc(human, raider, 0.3);
        assertEquals(human.part("arm_r").rotX, swing.rotX(arm), 0f, "the attacking arm as last drawn");
        assertTrue(swing.rotX(arm) < -0.4f, "raised, not hanging at rest");
        human.resetPose();
        AnatomyModels.captureNpc(raider, 0.3);
        List<ModelPart> all = new ArrayList<>();
        collect(human.root, all);
        for (ModelPart part : all) {
            assertArrayEquals(new float[] {0, 0, 0, 0, 0, 0, 1}, transform(part),
                    part.name + " is left in the reset pose for the next draw");
            assertTrue(part.visible, part.name + " is left visible for the next draw");
        }
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    @Test
    void validationNamesMissingPartsWrongGeometryAndUnownedBoxes() {
        FragmentAnatomy table = FragmentAnatomy.builder(BodyFamily.BIRD)
                .joint("root", null, 0, 0, 0)
                .part("body", "root", 0, 1, 0, 0, 0, 0, 0.4f, 0.4f, 0.8f)
                .part("arm", "body", 0.3f, 0, 0, 0, -0.2f, 0, 0.1f, 0.4f, 0.1f)
                .piece("torso", "body", "body").piece("arm", "arm", "arm")
                .build();
        Supplier<ModelPart> body = () -> new ModelPart("body").pivot(0, 1, 0).box(0, 0, 0, 0.4f, 0.4f, 0.8f);
        Supplier<ModelPart> arm = () -> new ModelPart("arm").pivot(0.3f, 0, 0).box(0, -0.2f, 0, 0.1f, 0.4f, 0.1f);

        assertDoesNotThrow(() -> AnatomyModels.validate(table,
                new EntityModel(new ModelPart("root").child(body.get().child(arm.get())))));
        invalid(table, new ModelPart("root").child(body.get()), "names part arm, which its model does not have");
        invalid(table, new ModelPart("root").child(body.get()).child(arm.get()), "arm hangs from root in the model, not body");
        invalid(table, new ModelPart("root").child(body.get().child(arm.get().pivot(0.31f, 0, 0))), "arm pivot");
        invalid(table, new ModelPart("root").child(body.get().child(arm.get().box(0, -0.2f, 0, 0.1f, 0.5f, 0.1f))),
                "arm box size");
        invalid(table, new ModelPart("root").child(new ModelPart("body").pivot(0, 1, 0).child(arm.get())),
                "body has no box in the model");
        invalid(table, new ModelPart("root").child(body.get().child(arm.get()))
                .child(new ModelPart("stray").box(0, 0, 0, 0.1f, 0.1f, 0.1f)), "box stray is drawn by no piece");

        FragmentAnatomy split = FragmentAnatomy.builder(BodyFamily.BIRD)
                .joint("root", null, 0, 0, 0)
                .part("body", "root", 0, 1, 0, 0, 0, 0, 0.4f, 0.4f, 0.8f)
                .part("arm", "body", 0.3f, 0, 0, 0, -0.2f, 0, 0.1f, 0.4f, 0.1f).split("arm", "hand", true)
                .piece("torso", "body", "body").piece("arm", "arm", "arm").piece("hand", "hand", "hand")
                .build();
        assertDoesNotThrow(() -> AnatomyModels.validate(split, new EntityModel(new ModelPart("root")
                .child(body.get().child(arm.get().split("hand", true))))));
        ModelPart glued = new ModelPart("arm").pivot(0.3f, 0, 0).box(0, -0.1f, 0, 0.1f, 0.2f, 0.1f)
                .child(new ModelPart("hand").pivot(0, -0.2f, 0).box(0, -0.1f, 0, 0.1f, 0.2f, 0.1f));
        invalid(split, new ModelPart("root").child(body.get().child(glued)), "hand is not a split half in the model");
    }

    private static void invalid(FragmentAnatomy table, ModelPart root, String message) {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> AnatomyModels.validate(table, new EntityModel(root)), message);
        assertTrue(e.getMessage().contains(message), "error names the problem: " + e.getMessage());
        assertTrue(e.getMessage().startsWith("BIRD anatomy"), "error names the body: " + e.getMessage());
    }

    // ------------------------------------------------------------------
    // Cost
    // ------------------------------------------------------------------

    @Test
    void readingPosesForAFullFieldOfPiecesAllocatesNothingPerFrame() {
        List<BodyFragment> field = new ArrayList<>();
        Map<BodyFamily, FragmentPose> poses = new HashMap<>();
        for (BodyFamily family : BodyFamily.values()) {
            Object[] living = livingBodies(family).get(1);
            poses.put(family, living[0] instanceof Npc n ? AnatomyModels.captureNpc(n, (double) living[1])
                    : AnatomyModels.captureCreature((Creature) living[0], (double) living[1]));
        }
        BodyFamily[] families = BodyFamily.values();
        for (int i = 0; i < BodyFragmentConstants.MAX_LIVE_FRAGMENTS; i++) {
            FragmentAnatomy anatomy = families[i % families.length].anatomy();
            FragmentPiece p = anatomy.piece(i % anatomy.pieces.size());
            field.add(new BodyFragment(p, poses.get(anatomy.family)).placeAt(i * 0.7f, 40f, -i * 0.3f, i * 0.4f));
        }
        Matrix4f root = new Matrix4f();
        Matrix4f piece = new Matrix4f();
        Vector3f cut = new Vector3f();
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        float sink = 0;
        for (int i = 0; i < 1_000; i++) {
            sink += frame(field, root, piece, cut);
        }
        long before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 2_000; i++) {
            sink += frame(field, root, piece, cut);
        }
        long bytes = (bean.getThreadAllocatedBytes(thread) - before) / 2_000;
        System.out.println("posed fragment read allocation: " + bytes + " bytes/frame (" + sink + ")");
        assertTrue(bytes < 4_096, "reading " + field.size() + " posed pieces allocated " + bytes + " bytes/frame");
    }

    /** What a renderer needs per posed piece per frame, minus the GL calls. */
    private static float frame(List<BodyFragment> field, Matrix4f root, Matrix4f piece, Vector3f cut) {
        float sum = 0;
        for (int i = 0; i < field.size(); i++) {
            BodyFragment f = field.get(i);
            EntityModel model = AnatomyModels.modelOf(f.definition.family);
            model.resetPose();
            AnatomyModels.applyPose(model, f.pose);
            f.rootTransform(root);
            f.modelTransform(piece);
            f.jointToWorld(f.definition.rootJoint, cut);
            sum += root.m31() + piece.m30() + cut.y;
        }
        return sum;
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    /** {entity, clock, label} for every living pose of a family. */
    private static List<Object[]> livingBodies(BodyFamily family) {
        List<Object[]> out = new ArrayList<>();
        if (family == BodyFamily.HUMANOID) {
            for (NpcScene scene : NPC_SCENES) {
                for (NpcAppearance look : LOOKS) {
                    Npc n = new Npc(null, "Test");
                    n.archetype = look.archetype;
                    n.raider = look.raider;
                    n.isTrader = look.trader;
                    n.state = scene.state;
                    n.vel.set(scene.speed, 0, scene.speed * 0.3f);
                    n.bobPhase = scene.bobPhase;
                    out.add(new Object[] {n, scene.time, scene.label + " " + look.archetype
                            + (look.raider ? " raider" : "")});
                }
            }
        } else {
            for (CreatureScene scene : CREATURE_SCENES) {
                Creature c = new Creature(null, family.creature);
                c.state = scene.state;
                c.vel.set(scene.run * family.creature.speed, 0, 0);
                c.bobPhase = scene.bobPhase;
                out.add(new Object[] {c, scene.time, scene.label});
            }
        }
        return out;
    }

    private static void pose(EntityModel model, Object[] living) {
        if (living[0] instanceof Npc n) {
            Animator.poseNpc(model, n, (double) living[1]);
        } else {
            Animator.poseCreature(model, (Creature) living[0], (double) living[1]);
        }
    }

    /** {@code ModelPart.render}'s traversal: every visible part's frame and, if it has one, its own box. */
    private static void walk(ModelPart part, Matrix4f parent, Set<String> skip,
                             Map<String, Matrix4f> boxes, Map<String, Matrix4f> frames) {
        if (!part.visible) {
            return;
        }
        Matrix4f local = new Matrix4f(parent)
                .translate(part.pivotX + part.poseX, part.pivotY + part.poseY, part.pivotZ + part.poseZ)
                .rotateZ(part.rotZ).rotateY(part.rotY).rotateX(part.rotX);
        if (part.scale != 1f) {
            local.scale(part.scale);
        }
        frames.put(part.name, local);
        if (part.sizeX > 0) {
            boxes.put(part.name, new Matrix4f(local).translate(part.boxX, part.boxY, part.boxZ)
                    .scale(part.sizeX, part.sizeY, part.sizeZ));
        }
        for (ModelPart child : part.children) {
            if (!skip.contains(child.name)) {
                walk(child, local, skip, boxes, frames);
            }
        }
    }

    /**
     * The unsplit box a straight split part draws, from the model's own
     * fields: its half and every straight tip below it, in its frame.
     */
    private static Matrix4f wholeBox(ModelPart part, Matrix4f frame) {
        float[] min = {part.boxX - part.sizeX / 2, part.boxY - part.sizeY / 2, part.boxZ - part.sizeZ / 2};
        float[] max = {part.boxX + part.sizeX / 2, part.boxY + part.sizeY / 2, part.boxZ + part.sizeZ / 2};
        float ox = 0, oy = 0, oz = 0;
        for (ModelPart at = part; ; ) {
            ModelPart tip = null;
            for (ModelPart child : at.children) {
                if (at.isSplitTip(child)) {
                    tip = child;
                }
            }
            if (tip == null) {
                break;
            }
            assertArrayEquals(new float[] {0, 0, 0, 0, 0, 0, 1}, transform(tip),
                    "the living animation never bends a merged split, so its whole box is exact");
            ox += tip.pivotX;
            oy += tip.pivotY;
            oz += tip.pivotZ;
            float[] c = {ox + tip.boxX, oy + tip.boxY, oz + tip.boxZ};
            float[] s = {tip.sizeX, tip.sizeY, tip.sizeZ};
            for (int a = 0; a < 3; a++) {
                min[a] = Math.min(min[a], c[a] - s[a] / 2);
                max[a] = Math.max(max[a], c[a] + s[a] / 2);
            }
            at = tip;
        }
        return new Matrix4f(frame).translate((min[0] + max[0]) / 2, (min[1] + max[1]) / 2, (min[2] + max[2]) / 2)
                .scale(max[0] - min[0], max[1] - min[1], max[2] - min[2]);
    }

    private static void assertBox(Matrix4f want, Matrix4f got, String where) {
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        for (int c = 0; c < 8; c++) {
            float sx = (c & 1) - 0.5f, sy = ((c >> 1) & 1) - 0.5f, sz = ((c >> 2) & 1) - 0.5f;
            want.transformPosition(sx, sy, sz, a);
            got.transformPosition(sx, sy, sz, b);
            assertEquals(0f, a.distance(b), TOLERANCE, where + " corner " + c + ": " + a + " vs " + b);
        }
    }

    /** The piece's rigid box, corner for corner along its own axes, against a drawn box. */
    private static void assertCollisionBox(Matrix4f drawn, BodyFragment f, String where) {
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        for (int c = 0; c < 8; c++) {
            float sx = (c & 1) == 0 ? -1 : 1, sy = ((c >> 1) & 1) == 0 ? -1 : 1, sz = ((c >> 2) & 1) == 0 ? -1 : 1;
            drawn.transformPosition(sx * 0.5f, sy * 0.5f, sz * 0.5f, a);
            f.orientation.transform(sx * f.halfX, sy * f.halfY, sz * f.halfZ, b).add(f.pos);
            assertEquals(0f, a.distance(b), TOLERANCE, where + " collision corner " + c + ": " + a + " vs " + b);
        }
    }

    private static float[] transform(ModelPart p) {
        return new float[] {p.rotX, p.rotY, p.rotZ, p.poseX, p.poseY, p.poseZ, p.scale};
    }

    private static void assertArrayEquals(float[] want, float[] got, String message) {
        org.junit.jupiter.api.Assertions.assertArrayEquals(want, got, 0f, message);
    }

    private static void collect(ModelPart part, List<ModelPart> out) {
        out.add(part);
        for (ModelPart child : part.children) {
            collect(child, out);
        }
    }
}
