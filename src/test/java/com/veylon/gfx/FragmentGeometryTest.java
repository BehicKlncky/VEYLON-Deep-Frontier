package com.veylon.gfx;

import com.sun.management.ThreadMXBean;
import com.veylon.entity.BodyFragment;
import com.veylon.entity.BodyFragment.Piece;
import com.veylon.entity.BodyFragmentConstants;
import com.veylon.entity.NpcAppearance;
import com.veylon.entity.RagdollConstants;
import com.veylon.gfx.model.Animator;
import com.veylon.gfx.model.EntityModel;
import com.veylon.gfx.model.FragmentModels;
import com.veylon.gfx.model.ModelPart;
import com.veylon.gfx.model.NpcModels;
import com.veylon.settlement.NpcArchetype;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a piece of a person blown apart is drawn: on the spot the simulation
 * has it, turned the way the simulation turned it, with its wounds on its
 * severed ends and its tint rotting like a carcass.
 *
 * <p>Headless. The draw matrices are rebuilt here the way
 * {@code ModelPart.render} builds them — translate by pivot and pose, then
 * Rz·Ry·Rx, then the box — from the frame {@code FragmentModels} hands the
 * renderer.
 */
class FragmentGeometryTest {

    private static final int WARMUP_FRAMES = 1_000;
    private static final int MEASURED_FRAMES = 2_000;
    private static final long BYTES_PER_FRAME_ALLOWANCE = 4_096;

    private final EntityModel model = NpcModels.get();

    @Test
    void everyPieceCentreIsItsPivotChainPlusItsBoxCentre() {
        for (Piece p : Piece.values()) {
            BodyFragment f = new BodyFragment(p);
            ModelPart box = model.root.find(p.boxPart);
            Vector3f boxPivot = pivotSum(p.boxPart, true);
            assertEquals(boxPivot.x + box.boxX, f.restCentreX, 1e-5f, p + " centre x");
            assertEquals(boxPivot.y + box.boxY, f.restCentreY, 1e-5f, p + " centre y");
            assertEquals(boxPivot.z + box.boxZ, f.restCentreZ, 1e-5f, p + " centre z");

            Vector3f above = pivotSum(p.rootPart, false);
            Vector3f anchor = FragmentModels.anchor(p, new Vector3f());
            assertEquals(above.x, anchor.x, 1e-5f, p + " anchor x");
            assertEquals(above.y, anchor.y, 1e-5f, p + " anchor y");
            assertEquals(above.z, anchor.z, 1e-5f, p + " anchor z");
            ModelPart top = model.root.find(p.rootPart);
            assertEquals(f.restPivotX, anchor.x + top.pivotX, 1e-5f, p + " joint x");
            assertEquals(f.restPivotY, anchor.y + top.pivotY, 1e-5f, p + " joint y");
            assertEquals(f.restPivotZ, anchor.z + top.pivotZ, 1e-5f, p + " joint z");
        }
    }

    @Test
    void theRestPoseTurnsNoPartAboveOrAlongAPieceSoTheAnchorIsAPureTranslation() {
        for (NpcAppearance look : FragmentIsolationTest.everyLook()) {
            for (Piece p : Piece.values()) {
                Animator.poseFragment(model, fragment(p, look));
                for (ModelPart part : chainTo(p.boxPart)) {
                    String where = part.name + " above " + p.boxPart + " for " + look.archetype;
                    assertEquals(0f, part.rotX, where);
                    assertEquals(0f, part.rotY, where);
                    assertEquals(0f, part.rotZ, where);
                    assertEquals(0f, part.poseX, where);
                    assertEquals(0f, part.poseY, where);
                    assertEquals(0f, part.poseZ, where);
                    assertEquals(1f, part.scale, where);
                }
            }
        }
    }

    @Test
    void everyPieceIsDrawnWhereTheSimulationHasItTurnedTheWayItTurned() {
        Quaternionf[] turns = {
                new Quaternionf(),
                new Quaternionf().rotationY((float) Math.toRadians(-117)),
                new Quaternionf().rotationXYZ(0.3f, 1.1f, -0.7f),
                new Quaternionf().rotationAxis(2.0f, new Vector3f(1, 1, 0).normalize()),
        };
        NpcAppearance guard = FragmentIsolationTest.look(NpcArchetype.GUARD, false, false);
        Vector3f drawn = new Vector3f();
        Vector3f want = new Vector3f();
        for (Piece p : Piece.values()) {
            for (Quaternionf turn : turns) {
                BodyFragment f = fragment(p, guard);
                f.pos.set(12.3f, 41.7f, -5.2f);
                f.orientation.set(turn);
                Map<String, Matrix4f> boxes = drawnBoxes(f);
                Matrix4f box = boxes.get(p.boxPart);
                assertNotNull(box, p + " draws its own box");
                for (int corner = 0; corner < 8; corner++) {
                    float sx = (corner & 1) == 0 ? -0.5f : 0.5f;
                    float sy = (corner & 2) == 0 ? -0.5f : 0.5f;
                    float sz = (corner & 4) == 0 ? -0.5f : 0.5f;
                    box.transformPosition(sx, sy, sz, drawn);
                    f.modelToWorld(f.restCentreX + sx * 2f * f.halfX, f.restCentreY + sy * 2f * f.halfY,
                            f.restCentreZ + sz * 2f * f.halfZ, want);
                    assertEquals(want.x, drawn.x, 1e-4f, p + " corner " + corner + " x");
                    assertEquals(want.y, drawn.y, 1e-4f, p + " corner " + corner + " y");
                    assertEquals(want.z, drawn.z, 1e-4f, p + " corner " + corner + " z");
                }
                box.transformPosition(0, 0, 0, drawn);
                assertEquals(0f, drawn.distance(f.pos), 1e-4f, p + " is drawn centred on its simulated position");
            }
        }
    }

    @Test
    void everySeveredEndCarriesOneThinCutFaceOnItsOwnSurface() {
        Map<Piece, Integer> counts = Map.of(Piece.TORSO, 5, Piece.HEAD, 1,
                Piece.UPPER_ARM_L, 2, Piece.UPPER_ARM_R, 2, Piece.FOREARM_L, 1, Piece.FOREARM_R, 1,
                Piece.THIGH_L, 2, Piece.THIGH_R, 2, Piece.SHIN_L, 1, Piece.SHIN_R, 1);
        int total = 0;
        Vector3f at = new Vector3f();
        Vector3f size = new Vector3f();
        for (Piece p : Piece.values()) {
            assertEquals(counts.get(p), FragmentModels.cutCount(p), p + " cut faces");
            total += FragmentModels.cutCount(p);
            float[] centre = {p.centreX, p.centreY, p.centreZ};
            float[] half = {p.halfX, p.halfY, p.halfZ};
            for (int i = 0; i < FragmentModels.cutCount(p); i++) {
                FragmentModels.cutCentre(p, i, at);
                FragmentModels.cutSize(p, i, size);
                float[] c = {at.x, at.y, at.z};
                float[] s = {size.x, size.y, size.z};
                int thin = s[0] <= s[1] && s[0] <= s[2] ? 0 : s[1] <= s[2] ? 1 : 2;
                String where = p + " cut " + i;
                assertTrue(s[thin] > 0 && s[thin] <= 0.03f + 1e-6f, where + " is at most 0.03 thick: " + s[thin]);
                // Its inner side lies on a face of the piece's box; the rest stands outside it.
                float lo = c[thin] - s[thin] * 0.5f;
                float hi = c[thin] + s[thin] * 0.5f;
                boolean onTop = Math.abs(lo - (centre[thin] + half[thin])) < 1e-5f;
                boolean onBottom = Math.abs(hi - (centre[thin] - half[thin])) < 1e-5f;
                assertTrue(onTop || onBottom, where + " sits on a face of the box");
                for (int a = 0; a < 3; a++) {
                    if (a == thin) {
                        continue;
                    }
                    assertTrue(s[a] >= 0.1f, where + " covers a limb's cross-section, not a sliver");
                    assertTrue(c[a] - s[a] * 0.5f >= centre[a] - half[a] - 1e-5f
                            && c[a] + s[a] * 0.5f <= centre[a] + half[a] + 1e-5f, where + " stays on its face");
                }
            }
        }
        assertEquals(18, total, "nine cut joints, one face on each side of each");

        // The torso's five: neck on top, shoulders on the sides, hips below.
        assertCut(Piece.TORSO, 0, 1, 0f, 1.485f, 0f);
        assertCut(Piece.TORSO, 1, 0, -0.245f, 1.41f, 0f);
        assertCut(Piece.TORSO, 2, 0, 0.245f, 1.41f, 0f);
        assertCut(Piece.TORSO, 3, 1, -0.115f, 0.855f, 0f);
        assertCut(Piece.TORSO, 4, 1, 0.115f, 0.855f, 0f);
        // Each limb at its own joint ends.
        assertCut(Piece.HEAD, 0, 1, 0f, 1.505f, 0f);
        assertCut(Piece.UPPER_ARM_L, 0, 1, -0.30f, 1.43f, 0f);
        assertCut(Piece.UPPER_ARM_L, 1, 1, -0.30f, 1.145f, 0f);
        assertCut(Piece.FOREARM_R, 0, 1, 0.30f, 1.155f, 0f);
        assertCut(Piece.THIGH_R, 0, 1, 0.115f, 0.865f, 0f);
        assertCut(Piece.THIGH_R, 1, 1, 0.115f, 0.425f, 0f);
        assertCut(Piece.SHIN_L, 0, 1, -0.115f, 0.435f, 0f);
    }

    @Test
    void aShoulderWoundClearsTheVestItSitsBeside() {
        Vector3f vestAt = pivotSum("vest", true);
        ModelPart vest = model.root.find("vest");
        float vestSide = vestAt.x + vest.boxX - vest.sizeX * 0.5f;
        Vector3f at = FragmentModels.cutCentre(Piece.TORSO, 1, new Vector3f());
        Vector3f size = FragmentModels.cutSize(Piece.TORSO, 1, new Vector3f());
        assertEquals(vestSide - FragmentModels.CUT_PROUD, at.x - size.x * 0.5f, 1e-5f,
                "the left shoulder wound must stand just proud of the vest, not inside it");
    }

    @Test
    void theCullingRadiusHoldsEverythingAPieceDrawsInEveryLook() {
        Vector3f corner = new Vector3f();
        Vector3f at = new Vector3f();
        Vector3f size = new Vector3f();
        for (NpcAppearance look : FragmentIsolationTest.everyLook()) {
            for (Piece p : Piece.values()) {
                BodyFragment f = fragment(p, look);
                float radius = FragmentModels.radius(p);
                for (Map.Entry<String, Matrix4f> box : drawnBoxes(f).entrySet()) {
                    for (int c = 0; c < 8; c++) {
                        box.getValue().transformPosition((c & 1) - 0.5f, ((c >> 1) & 1) - 0.5f,
                                ((c >> 2) & 1) - 0.5f, corner);
                        assertTrue(corner.length() <= radius, p + " of " + look.archetype + ": "
                                + box.getKey() + " reaches " + corner.length() + " past radius " + radius);
                    }
                }
                for (int i = 0; i < FragmentModels.cutCount(p); i++) {
                    FragmentModels.cutCentre(p, i, at).sub(f.restCentreX, f.restCentreY, f.restCentreZ);
                    FragmentModels.cutSize(p, i, size);
                    assertTrue(at.length() + size.length() * 0.5f <= radius, p + " cut " + i + " inside radius");
                }
            }
        }
    }

    @Test
    void settledPiecesFadeToTheRottenCarcassTintOverTheLastQuarterOfTheirDecay() {
        BodyFragment f = new BodyFragment(Piece.TORSO);
        Vector3f tint = new Vector3f();
        f.decay = 0f;
        assertTint(1f, 1f, 1f, FragmentModels.tint(f, tint), "a piece in flight is fresh");

        f.settled = true;
        f.decay = RagdollConstants.CORPSE_DECAY;
        assertTint(1f, 1f, 1f, FragmentModels.tint(f, tint), "a newly settled piece is fresh");
        f.decay = FragmentModels.ROT_FRACTION * RagdollConstants.CORPSE_DECAY;
        assertTint(1f, 1f, 1f, FragmentModels.tint(f, tint), "fresh until the last quarter");
        f.decay = 0.5f * FragmentModels.ROT_FRACTION * RagdollConstants.CORPSE_DECAY;
        assertEquals(0.5f, FragmentModels.rot(f), 1e-5f, "half way through the last quarter");
        f.decay = 0f;
        // renderCarcasses: rot, rot × 0.9, rot × 0.85 with rot = 0.6.
        assertTint(0.6f, 0.6f * 0.9f, 0.6f * 0.85f, FragmentModels.tint(f, tint),
                "a piece about to vanish wears the rotten carcass tint");

        float lastRot = -1f;
        float lastRed = 2f;
        for (float decay = RagdollConstants.CORPSE_DECAY; decay >= 0f; decay -= 1f) {
            f.decay = decay;
            float rot = FragmentModels.rot(f);
            FragmentModels.tint(f, tint);
            assertTrue(rot >= lastRot && tint.x <= lastRed, "rotting never reverses, at decay " + decay);
            lastRot = rot;
            lastRed = tint.x;
        }
    }

    @Test
    void posingAndPlacingAFullFieldOfPiecesAllocatesNothingPerFrame() {
        List<BodyFragment> field = new ArrayList<>();
        List<NpcAppearance> looks = FragmentIsolationTest.everyLook();
        for (int i = 0; i < BodyFragmentConstants.MAX_LIVE_FRAGMENTS; i++) {
            BodyFragment f = fragment(Piece.values()[i % Piece.values().length], looks.get(i % looks.size()));
            f.pos.set(i * 0.7f, 40.5f, -i * 0.3f);
            f.orientation.rotationXYZ(i * 0.1f, i * 0.2f, i * 0.3f);
            f.settled = i % 2 == 0;
            f.decay = i;
            field.add(f);
        }
        Matrix4f piece = new Matrix4f();
        Matrix4f base = new Matrix4f();
        Matrix4f cut = new Matrix4f();
        Vector3f at = new Vector3f();
        Vector3f size = new Vector3f();
        Vector3f tint = new Vector3f();
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();

        float sink = 0;
        for (int i = 0; i < WARMUP_FRAMES; i++) {
            sink += frame(field, piece, base, cut, at, size, tint);
        }
        long before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < MEASURED_FRAMES; i++) {
            sink += frame(field, piece, base, cut, at, size, tint);
        }
        long bytes = (bean.getThreadAllocatedBytes(thread) - before) / MEASURED_FRAMES;
        System.out.println("fragment draw preparation allocation: " + bytes + " bytes/frame (" + sink + ")");
        assertTrue(bytes < BYTES_PER_FRAME_ALLOWANCE, "posing and placing " + field.size()
                + " pieces allocated " + bytes + " bytes/frame, over the "
                + BYTES_PER_FRAME_ALLOWANCE + " byte allowance");
    }

    /** Everything {@code Renderer.drawFragment} does for a field of pieces, minus the GL calls. */
    private float frame(List<BodyFragment> field, Matrix4f piece, Matrix4f base, Matrix4f cut,
                        Vector3f at, Vector3f size, Vector3f tint) {
        float sum = 0;
        for (int i = 0; i < field.size(); i++) {
            BodyFragment f = field.get(i);
            sum += FragmentModels.radius(f.piece);
            FragmentModels.tint(f, tint);
            ModelPart root = Animator.poseFragment(model, f);
            FragmentModels.pieceTransform(f, piece);
            FragmentModels.rootTransform(f.piece, piece, base);
            sum += root.visible ? base.m30() : 0;
            for (int c = 0; c < FragmentModels.cutCount(f.piece); c++) {
                FragmentModels.cutCentre(f.piece, c, at);
                FragmentModels.cutSize(f.piece, c, size);
                cut.set(piece).translate(at.x, at.y - size.y * 0.5f, at.z).scale(size);
                sum += cut.m31() + tint.y;
            }
        }
        return sum;
    }

    // ------------------------------------------------------------------

    private static BodyFragment fragment(Piece p, NpcAppearance look) {
        BodyFragment f = new BodyFragment(p);
        f.appearance.copyFrom(look);
        return f;
    }

    /**
     * Poses the shared model for {@code f} as the renderer does and returns
     * the matrix {@code ModelPart.render} would submit for every box it draws.
     */
    private Map<String, Matrix4f> drawnBoxes(BodyFragment f) {
        ModelPart top = Animator.poseFragment(model, f);
        Matrix4f piece = FragmentModels.pieceTransform(f, new Matrix4f());
        Matrix4f base = FragmentModels.rootTransform(f.piece, piece, new Matrix4f());
        Map<String, Matrix4f> out = new LinkedHashMap<>();
        collectBoxes(top, base, out);
        return out;
    }

    private static void collectBoxes(ModelPart part, Matrix4f parent, Map<String, Matrix4f> out) {
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
            // A merged whole box would reach into the next piece; isolation never leaves one.
            assertFalse(part.drawsWholeBox(), part.name + " would draw its unsplit box");
            out.put(part.name, new Matrix4f(local).translate(part.boxX, part.boxY, part.boxZ)
                    .scale(part.sizeX, part.sizeY, part.sizeZ));
        }
        for (ModelPart child : part.children) {
            collectBoxes(child, local, out);
        }
    }

    /** The parts from the model root down to {@code name}, found level by level with {@code find}. */
    private List<ModelPart> chainTo(String name) {
        List<ModelPart> chain = new ArrayList<>();
        ModelPart at = model.root;
        chain.add(at);
        while (!at.name.equals(name)) {
            ModelPart next = null;
            for (ModelPart child : at.children) {
                if (child.find(name) != null) {
                    next = child;
                    break;
                }
            }
            assertNotNull(next, name + " is a part of the humanoid");
            at = next;
            chain.add(at);
        }
        return chain;
    }

    /** Pivots summed down to {@code name}: including its own, or only those above it. */
    private Vector3f pivotSum(String name, boolean includingOwn) {
        List<ModelPart> chain = chainTo(name);
        Vector3f sum = new Vector3f();
        for (int i = 0; i < chain.size() - (includingOwn ? 0 : 1); i++) {
            ModelPart part = chain.get(i);
            sum.add(part.pivotX, part.pivotY, part.pivotZ);
        }
        return sum;
    }

    private static void assertCut(Piece p, int i, int thinAxis, float x, float y, float z) {
        Vector3f at = FragmentModels.cutCentre(p, i, new Vector3f());
        Vector3f size = FragmentModels.cutSize(p, i, new Vector3f());
        String where = p + " cut " + i;
        assertEquals(x, at.x, 1e-5f, where + " x");
        assertEquals(y, at.y, 1e-5f, where + " y");
        assertEquals(z, at.z, 1e-5f, where + " z");
        assertEquals(size.get(thinAxis), Math.min(size.x, Math.min(size.y, size.z)), 1e-7f,
                where + " is thin across axis " + thinAxis);
    }

    private static void assertTint(float r, float g, float b, Vector3f tint, String why) {
        assertEquals(r, tint.x, 1e-5f, why);
        assertEquals(g, tint.y, 1e-5f, why);
        assertEquals(b, tint.z, 1e-5f, why);
    }
}
