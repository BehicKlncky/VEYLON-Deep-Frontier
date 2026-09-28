package com.veylon.gfx;

import com.sun.management.ThreadMXBean;
import com.veylon.BodyFireArena;
import com.veylon.Game;
import com.veylon.entity.BodyFamily;
import com.veylon.entity.BodyFragment;
import com.veylon.entity.Creature;
import com.veylon.entity.Creature.CreatureType;
import com.veylon.entity.FragmentAnatomy;
import com.veylon.entity.FragmentPiece;
import com.veylon.entity.Npc;
import com.veylon.gfx.model.AnatomyModels;
import com.veylon.gfx.model.BodyPosing;
import com.veylon.gfx.model.EntityModel;
import com.veylon.gfx.model.FlameAnchors;
import com.veylon.gfx.model.ModelPart;
import com.veylon.settlement.NpcArchetype;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flames stand on the body as it is drawn: every anchor lies on a face of a
 * box the renderer draws for that body in that pose, moves with the body and
 * with the limb it is on, is gone with a part the body's look hides, and a
 * body in pieces holds each of its anchors on exactly one piece. How many
 * there are and how wide their flames follow the body's real surface, so a
 * bird and a thornhorn never wear the same fire.
 */
class FlameAnchorsTest {

    private static final float ON_FACE = 1e-3f;

    @ParameterizedTest
    @EnumSource(BodyFamily.class)
    void everyBodyCarriesBoundedAnchorsOnItsOwnBoxesAndNoneOnWhatGlows(BodyFamily family) {
        FlameAnchors anchors = FlameAnchors.of(family);
        assertTrue(anchors.count >= FlameAnchors.MIN_ANCHORS && anchors.count <= FlameAnchors.MAX_ANCHORS,
                family + " carries " + anchors.count);
        for (int a = 0; a < anchors.count; a++) {
            ModelPart part = anchors.part(a);
            assertTrue(part.sizeX > 0f, family + " anchor " + a + " stands on a box");
            assertEquals(0f, part.emissive, 0f, family + " anchor " + a + " is not on an eye or a glow spot");
            float dx = Math.abs(anchors.localX(a) - part.boxX) / (part.sizeX * 0.5f);
            float dy = Math.abs(anchors.localY(a) - part.boxY) / (part.sizeY * 0.5f);
            float dz = Math.abs(anchors.localZ(a) - part.boxZ) / (part.sizeZ * 0.5f);
            assertTrue(Math.max(dx, Math.max(dy, dz)) <= 1f + ON_FACE, family + " anchor " + a + " is on its box");
            assertTrue(Math.abs(Math.max(dx, Math.max(dy, dz)) - 1f) <= ON_FACE,
                    family + " anchor " + a + " is on a face of its box, not inside it");
            assertTrue(anchors.width(a) >= FlameAnchors.MIN_WIDTH && anchors.width(a) <= FlameAnchors.MAX_WIDTH);
            assertTrue(anchors.order(a) >= 0f && anchors.order(a) < 1f);
        }
    }

    @Test
    void eachKindOfBodyBurnsOnItsOwnAnatomy() {
        requireAnchorsOn(BodyFamily.HUMANOID, "torso", "head", "arm_l", "arm_r", "leg_l", "leg_r", "shin_l", "shin_r");
        requireAnchorsOn(BodyFamily.BIRD, "body", "head", "wing0_l", "wing0_r", "wing1_l", "wing1_r");
        for (BodyFamily quadruped : new BodyFamily[] {BodyFamily.DEER, BodyFamily.WOLF, BodyFamily.THORNHORN,
                BodyFamily.STALKER}) {
            requireAnchorsOn(quadruped, "body", "head", "leg_fl", "leg_br");
        }
        requireAnchorsOn(BodyFamily.WOLF, "tail", "ruff");
        requireAnchorsOn(BodyFamily.HARE, "body", "haunch", "head");
    }

    @Test
    void aBirdWearsAFewSmallFlamesAndAThornhornManyBroadOnes() {
        FlameAnchors bird = FlameAnchors.of(BodyFamily.BIRD);
        FlameAnchors hare = FlameAnchors.of(BodyFamily.HARE);
        FlameAnchors person = FlameAnchors.of(BodyFamily.HUMANOID);
        FlameAnchors thornhorn = FlameAnchors.of(BodyFamily.THORNHORN);
        assertTrue(bird.count < hare.count && hare.count < thornhorn.count,
                "anchors follow the surface: bird " + bird.count + ", hare " + hare.count
                        + ", thornhorn " + thornhorn.count);
        assertTrue(bird.largestWidth() < person.largestWidth() && person.largestWidth() <= thornhorn.largestWidth(),
                "flame width follows the faces: bird " + bird.largestWidth() + ", person "
                        + person.largestWidth() + ", thornhorn " + thornhorn.largestWidth());
        assertTrue(totalWidth(thornhorn) > 5f * totalWidth(bird), "a thornhorn's fire is many times a bird's");
    }

    @ParameterizedTest
    @EnumSource(CreatureType.class)
    void sampledFlamesSitOnTheBoxesDrawnForTheLivingBody(CreatureType type) {
        Game game = BodyFireArena.arena();
        Creature c = BodyFireArena.creature(game, type, 310.5f, 305.5f);
        c.yaw = 117f;
        c.vel.set(1.2f, 0f, -0.8f);
        c.bobPhase = 0.49f;
        assertOnDrawnBoxes(BodyFamily.of(type), c, null);
    }

    @Test
    void sampledFlamesSitOnTheBoxesDrawnForAPersonInTheirLook() {
        Game game = BodyFireArena.arena();
        Npc guard = BodyFireArena.person(game, NpcArchetype.GUARD, 310.5f, 305.5f);
        guard.yaw = -63.5f;
        guard.vel.set(0f, 0f, -3f);
        guard.bobPhase = 0.49f;
        assertOnDrawnBoxes(BodyFamily.HUMANOID, null, guard);
    }

    /**
     * A falling body bends every joint about all three axes at once, which a
     * living gait never does: the flames must still sit on the boxes drawn.
     */
    @Test
    void sampledFlamesSitOnTheBoxesDrawnForAFallingBody() {
        Game game = BodyFireArena.arena();
        Npc guard = BodyFireArena.person(game, NpcArchetype.GUARD, 305.5f, 305.5f);
        Creature wolf = BodyFireArena.creature(game, CreatureType.WOLF, 312.5f, 305.5f);
        guard.killBy(false);
        wolf.killBy(false);
        guard.vel.set(3f, 2f, -1f);
        wolf.vel.set(-2f, 3f, 2f);
        game.entities.fastTick(game, 0.05f);
        assertEquals(2, game.ragdolls.live.size());
        for (int f = 0; f < 20; f++) {
            game.ragdolls.update(game, 1f / 60f);
        }
        Matrix4f frame = new Matrix4f();
        float[] out = new float[FlameAnchors.MAX_ANCHORS * 4];
        int bent = 0;
        for (var r : game.ragdolls.live) {
            BodyFamily family = BodyPosing.family(r);
            FlameAnchors anchors = FlameAnchors.of(family);
            ModelPart root = BodyPosing.ragdoll(r, frame);
            for (ModelPart part : allParts(root)) {
                bent += (part.rotX != 0f ? 1 : 0) + (part.rotY != 0f ? 1 : 0) + (part.rotZ != 0f ? 1 : 0) >= 2 ? 1 : 0;
            }
            int n = anchors.sample(root, frame, out);
            assertOnBoxes(anchors, root, frame, out, n, family + " falling");
        }
        assertTrue(bent > 0, "precondition: some joint is turned about two axes at once");
    }

    @Test
    void aGlowingBoxNeverHoldsAFlameHoweverLarge() {
        ModelPart root = new ModelPart("root");
        ModelPart plain = new ModelPart("plainSlab").box(0f, 0.3f, 0f, 0.5f, 0.5f, 0.1f).color(0.5f, 0.4f, 0.3f);
        ModelPart glowing = new ModelPart("glowSlab").box(0.6f, 0.3f, 0f, 0.5f, 0.5f, 0.1f)
                .color(0.3f, 0.9f, 0.9f).emissive(1f);
        root.child(plain).child(glowing);
        FlameAnchors anchors = new FlameAnchors(BodyFamily.HUMANOID, new EntityModel(root));
        assertTrue(anchors.anchorsOn(plain) > 0, "a plain box of that size holds a flame");
        assertEquals(0, anchors.anchorsOn(glowing), "a lamp, an eye or a glow spot never burns");
    }

    @Test
    void theFlamesMoveWithTheBodyAndWithTheLimbTheyStandOn() {
        Game game = BodyFireArena.arena();
        Creature deer = BodyFireArena.creature(game, CreatureType.DEER, 310.5f, 305.5f);
        FlameAnchors anchors = FlameAnchors.of(BodyFamily.DEER);
        Matrix4f frame = new Matrix4f();
        float[] before = new float[FlameAnchors.MAX_ANCHORS * 4];
        float[] after = new float[FlameAnchors.MAX_ANCHORS * 4];

        int n = anchors.sample(BodyPosing.creature(deer, 40.0, frame), frame, before);
        deer.pos.add(2.5f, 1f, -1.5f);
        int m = anchors.sample(BodyPosing.creature(deer, 40.0, frame), frame, after);
        assertEquals(n, m);
        for (int i = 0; i < n; i++) {
            assertEquals(before[i * 4], after[i * 4], 0f, "the same anchors, in the same order");
            assertEquals(before[i * 4 + 1] + 2.5f, after[i * 4 + 1], 1e-4f, "moved with the body");
            assertEquals(before[i * 4 + 2] + 1f, after[i * 4 + 2], 1e-4f);
            assertEquals(before[i * 4 + 3] - 1.5f, after[i * 4 + 3], 1e-4f);
        }

        // Turn the body: every anchor turns about its feet.
        deer.yaw += 90f;
        anchors.sample(BodyPosing.creature(deer, 40.0, frame), frame, before);
        for (int i = 0; i < n; i++) {
            float ax = after[i * 4 + 1] - deer.pos.x, az = after[i * 4 + 3] - deer.pos.z;
            float bx = before[i * 4 + 1] - deer.pos.x, bz = before[i * 4 + 3] - deer.pos.z;
            assertEquals(ax * ax + az * az, bx * bx + bz * bz, 1e-3f, "turned, not moved, about the feet");
            assertEquals(after[i * 4 + 2], before[i * 4 + 2], 1e-4f, "at the same height");
        }

        // Swing one leg: only the anchors on that leg (and its lower half) move.
        EntityModel model = AnatomyModels.modelOf(BodyFamily.DEER);
        ModelPart root = BodyPosing.creature(deer, 40.0, frame);
        anchors.sample(root, frame, before);
        model.part("leg_fl").rotX = 0.8f;
        anchors.sample(root, frame, after);
        int onLeg = 0;
        for (int i = 0; i < n; i++) {
            ModelPart part = anchors.part((int) before[i * 4]);
            boolean leg = part.name.startsWith("leg_fl");
            float moved = new Vector3f(before[i * 4 + 1], before[i * 4 + 2], before[i * 4 + 3])
                    .distance(after[i * 4 + 1], after[i * 4 + 2], after[i * 4 + 3]);
            if (leg) {
                onLeg++;
                assertTrue(moved > 0.02f, part.name + " anchor swings with the leg: " + moved);
            } else {
                assertEquals(0f, moved, 1e-5f, part.name + " anchor stays where it was");
            }
        }
        assertTrue(onLeg > 0, "the front leg holds a flame");
        model.resetPose();
    }

    @Test
    void aPartTheLookHidesHoldsNoFlameAndAnotherLookShowsIt() {
        Game game = BodyFireArena.arena();
        Npc guard = BodyFireArena.person(game, NpcArchetype.GUARD, 310.5f, 305.5f);
        Npc trader = BodyFireArena.person(game, NpcArchetype.TRADER, 312.5f, 305.5f);
        trader.isTrader = true;
        FlameAnchors anchors = FlameAnchors.of(BodyFamily.HUMANOID);
        ModelPart pack = AnatomyModels.modelOf(BodyFamily.HUMANOID).part("pack");
        assertTrue(anchors.anchorsOn(pack) > 0, "a trader's pack can burn");
        assertFalse(sampledParts(guard).contains("pack"), "a guard carries no pack to burn");
        assertTrue(sampledParts(trader).contains("pack"), "a trader's pack burns with them");
        assertFalse(sampledParts(guard).contains("hood"), "nor a raider's hood");
    }

    @Test
    void aFallingOrFallenPersonBurnsInTheirOwnLook() {
        Game game = BodyFireArena.arena();
        Npc guard = BodyFireArena.person(game, NpcArchetype.GUARD, 310.5f, 305.5f);
        guard.killBy(false);
        game.entities.fastTick(game, 0.05f);
        assertEquals(1, game.ragdolls.live.size());
        FlameAnchors anchors = FlameAnchors.of(BodyFamily.HUMANOID);
        Matrix4f frame = new Matrix4f();
        float[] out = new float[FlameAnchors.MAX_ANCHORS * 4];
        int n = anchors.sample(BodyPosing.ragdoll(game.ragdolls.live.getFirst(), frame), frame, out);
        Set<String> parts = new HashSet<>();
        for (int i = 0; i < n; i++) {
            parts.add(anchors.part((int) out[i * 4]).name);
        }
        assertTrue(parts.contains("torso") && parts.contains("guardPlate"), "their body and their kit burn: " + parts);
        assertFalse(parts.contains("pack") || parts.contains("hood") || parts.contains("kegPack"),
                "nothing another look wears: " + parts);
    }

    @ParameterizedTest
    @EnumSource(BodyFamily.class)
    void aBodyInPiecesHoldsEachOfItsFlamesOnExactlyOnePiece(BodyFamily family) {
        FragmentAnatomy anatomy = FragmentAnatomy.of(family);
        FlameAnchors anchors = FlameAnchors.of(family);
        Matrix4f frame = new Matrix4f();
        float[] out = new float[FlameAnchors.MAX_ANCHORS * 4];
        Map<Integer, String> holder = new HashMap<>();
        for (FragmentPiece piece : anatomy.pieces) {
            BodyFragment f = new BodyFragment(piece, anatomy.restPose()).placeAt(300f, 41f, 300f, 0f);
            if (family == BodyFamily.HUMANOID) {
                f.appearance.archetype = NpcArchetype.TRADER;
                f.appearance.trader = true;
            }
            int n = anchors.sample(BodyPosing.fragment(f, frame), frame, out);
            for (int i = 0; i < n; i++) {
                String was = holder.put((int) out[i * 4], piece.name);
                assertEquals(null, was, family + " anchor " + (int) out[i * 4] + " is on " + was + " and " + piece.name);
            }
        }
        Set<Integer> expected = new HashSet<>();
        Game game = BodyFireArena.arena();
        Object living = family == BodyFamily.HUMANOID ? trader(game) : BodyFireArena.creature(game, family.creature, 300f, 300f);
        for (int a : sampledAnchors(family, living)) {
            expected.add(a);
        }
        assertEquals(expected, holder.keySet(), family + ": the pieces together hold the living body's flames, once");
        AnatomyModels.modelOf(family).resetPose();
    }

    @Test
    void aLivingBodyDrawnAfterAPieceBurnsWhereItWouldAlone() {
        Game game = BodyFireArena.arena();
        Npc person = BodyFireArena.person(game, NpcArchetype.SCAVENGER, 310.5f, 305.5f);
        person.raider = true;
        int[] alone = sampledAnchors(BodyFamily.HUMANOID, person);
        FragmentAnatomy anatomy = FragmentAnatomy.humanoid();
        BodyFragment leg = new BodyFragment(anatomy.piece("shin_l"), anatomy.restPose()).placeAt(300f, 41f, 300f, 0f);
        BodyPosing.fragment(leg, new Matrix4f());
        int[] after = sampledAnchors(BodyFamily.HUMANOID, person);
        assertEquals(java.util.Arrays.toString(alone), java.util.Arrays.toString(after),
                "the shared model is reset: nothing of the piece's isolation is left on the next body");
    }

    @Test
    void samplingABodyAllocatesNothing() {
        Game game = BodyFireArena.arena();
        Npc person = BodyFireArena.person(game, NpcArchetype.GUARD, 310.5f, 305.5f);
        Creature thornhorn = BodyFireArena.creature(game, CreatureType.THORNHORN, 314.5f, 305.5f);
        FlameAnchors human = FlameAnchors.of(BodyFamily.HUMANOID);
        FlameAnchors beast = FlameAnchors.of(BodyFamily.THORNHORN);
        Matrix4f frame = new Matrix4f();
        float[] out = new float[FlameAnchors.MAX_ANCHORS * 4];
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        long before = 0;
        int total = 0;
        for (int i = 0; i < 40_000; i++) {
            if (i == 10_000) {
                before = bean.getThreadAllocatedBytes(thread);
            }
            total += human.sample(BodyPosing.npc(person, 40.0 + i * 0.01, frame), frame, out);
            total += beast.sample(BodyPosing.creature(thornhorn, 40.0 + i * 0.01, frame), frame, out);
        }
        long bytes = bean.getThreadAllocatedBytes(thread) - before;
        assertTrue(total > 0);
        assertTrue(bytes < 4_096, "posing and sampling 60,000 bodies allocated " + bytes + " bytes");
    }

    // ------------------------------------------------------------------

    private static Npc trader(Game game) {
        Npc n = BodyFireArena.person(game, NpcArchetype.TRADER, 300f, 300f);
        n.isTrader = true;
        n.vel.zero();
        return n;
    }

    private static void requireAnchorsOn(BodyFamily family, String... parts) {
        FlameAnchors anchors = FlameAnchors.of(family);
        EntityModel model = AnatomyModels.modelOf(family);
        for (String name : parts) {
            assertTrue(anchors.anchorsOn(model.part(name)) > 0, family + " " + name + " holds a flame");
        }
    }

    private static float totalWidth(FlameAnchors anchors) {
        float w = 0f;
        for (int a = 0; a < anchors.count; a++) {
            w += anchors.width(a);
        }
        return w;
    }

    private static Set<String> sampledParts(Npc n) {
        FlameAnchors anchors = FlameAnchors.of(BodyFamily.HUMANOID);
        Set<String> parts = new HashSet<>();
        for (int a : sampledAnchors(BodyFamily.HUMANOID, n)) {
            parts.add(anchors.part(a).name);
        }
        return parts;
    }

    private static int[] sampledAnchors(BodyFamily family, Object body) {
        FlameAnchors anchors = FlameAnchors.of(family);
        Matrix4f frame = new Matrix4f();
        float[] out = new float[FlameAnchors.MAX_ANCHORS * 4];
        ModelPart root = body instanceof Npc n ? BodyPosing.npc(n, 40.0, frame)
                : BodyPosing.creature((Creature) body, 40.0, frame);
        int n = anchors.sample(root, frame, out);
        int[] ids = new int[n];
        for (int i = 0; i < n; i++) {
            ids[i] = (int) out[i * 4];
        }
        return ids;
    }

    /**
     * Poses the body the way the renderer does, finds the matrix of every
     * visible box as {@code ModelPart.render} submits it (each part its own
     * half), and checks each sampled anchor lies on a face of its part's box.
     */
    private static void assertOnDrawnBoxes(BodyFamily family, Creature c, Npc n) {
        FlameAnchors anchors = FlameAnchors.of(family);
        Matrix4f frame = new Matrix4f();
        ModelPart root = c != null ? BodyPosing.creature(c, 40.0, frame) : BodyPosing.npc(n, 40.0, frame);
        float[] out = new float[FlameAnchors.MAX_ANCHORS * 4];
        int count = anchors.sample(root, frame, out);
        assertTrue(count >= FlameAnchors.MIN_ANCHORS, family + " shows flames");
        assertOnBoxes(anchors, root, frame, out, count, family.toString());
        AnatomyModels.modelOf(family).resetPose();
    }

    private static void assertOnBoxes(FlameAnchors anchors, ModelPart root, Matrix4f frame, float[] out, int count,
                                      String who) {
        Map<ModelPart, Matrix4f> boxes = new HashMap<>();
        boxes(root, frame, boxes);
        Vector3f local = new Vector3f();
        for (int i = 0; i < count; i++) {
            ModelPart part = anchors.part((int) out[i * 4]);
            Matrix4f box = boxes.get(part);
            assertTrue(box != null, who + " " + part.name + " is drawn");
            new Matrix4f(box).invert().transformPosition(out[i * 4 + 1], out[i * 4 + 2], out[i * 4 + 3], local);
            float extent = Math.max(Math.abs(local.x), Math.max(Math.abs(local.y), Math.abs(local.z)));
            assertEquals(0.5f, extent, 2e-3f, who + " anchor on " + part.name + " lies on a face of the box drawn: "
                    + local);
        }
    }

    private static java.util.List<ModelPart> allParts(ModelPart root) {
        java.util.List<ModelPart> out = new java.util.ArrayList<>();
        out.add(root);
        for (ModelPart c : root.children) {
            out.addAll(allParts(c));
        }
        return out;
    }

    /** {@code ModelPart.render}'s traversal, each part drawing its own box. */
    private static void boxes(ModelPart part, Matrix4f parent, Map<ModelPart, Matrix4f> out) {
        if (!part.visible) {
            return;
        }
        Matrix4f local = new Matrix4f(parent)
                .translate(part.pivotX + part.poseX, part.pivotY + part.poseY, part.pivotZ + part.poseZ)
                .rotateZ(part.rotZ).rotateY(part.rotY).rotateX(part.rotX);
        if (part.scale != 1f) {
            local.scale(part.scale);
        }
        if (part.sizeX > 0) {
            out.put(part, new Matrix4f(local).translate(part.boxX, part.boxY, part.boxZ)
                    .scale(part.sizeX, part.sizeY, part.sizeZ));
        }
        for (ModelPart child : part.children) {
            boxes(child, local, out);
        }
    }
}
