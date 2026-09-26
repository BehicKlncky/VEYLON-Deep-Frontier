package com.veylon.gfx.model;

import com.veylon.entity.BodyFamily;
import com.veylon.entity.BodyFragment;
import com.veylon.entity.BodyFragmentConstants;
import com.veylon.entity.FragmentAnatomy;
import com.veylon.entity.FragmentCut;
import com.veylon.entity.FragmentPiece;
import com.veylon.entity.RagdollConstants;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draw geometry for the pieces of any body blown apart — a person, the
 * player's remains or any animal: the frames a piece and its wounds are drawn
 * in, how far each wound stands out, how far a piece reaches for culling,
 * where a thin piece meets the ground and how far it has rotted.
 *
 * <p>A piece is its own body's shared model posed by
 * {@link Animator#poseFragment} and drawn from its root part in
 * {@link #rootFrame}. Its wounds are its {@link FragmentAnatomy} table's: flat
 * rectangles on the faces of its collision box, given depth here and drawn in
 * {@link #pieceFrame}, the collision box's own frame, so each tumbles with its
 * piece and stays on the joint it was cut at.
 *
 * <p>Wound depths and culling radii are computed once per family, when the
 * class loads, from the tables and the models' fixed geometry — pivots, boxes
 * and splits, never a model's current pose, look or visibility.
 * {@link AnatomyModels#modelOf} checks every table against its model first.
 * It is plain CPU data, so the renderer reads it without allocating and tests
 * check it without GL.
 */
public final class FragmentModels {

    /** Cut face colour: dark red, drawn without emission. */
    public static final float CUT_R = 0.42f, CUT_G = 0.05f, CUT_B = 0.05f;
    /**
     * How far a cut face stands proud of the outermost surface it sits on, so
     * it never fights the face below it for depth.
     */
    public static final float CUT_PROUD = 0.01f;
    /**
     * A cut face covers this fraction of the severed limb's cross-section, so
     * a rim of the piece's own colour is left round the wound.
     */
    public static final float CUT_INSET = BodyFragmentConstants.CUT_INSET;
    /**
     * A shell may stand at most this far beyond the face a wound lies on: a
     * person's vest (2 cm), a hare's haunch (2 cm). The wound stands proud of
     * the shell rather than disappearing inside it.
     */
    public static final float SHELL_REACH = 0.03f;
    /**
     * Below this speed, in m/s, a thin piece is drawn all the way down on the
     * floor of its sweep box; see {@link #contactDrop}. Every piece leaves a
     * body faster than {@link BodyFragmentConstants#UPWARD_BIAS}, so this
     * never moves a piece at the moment it separates.
     */
    public static final float CONTACT_SPEED = 1f;
    /**
     * Share of {@link RagdollConstants#CORPSE_DECAY} at the end of a settled
     * piece's life over which its tint darkens to the rotten carcass tint.
     */
    public static final float ROT_FRACTION = 0.25f;
    /** Red channel of a fully rotten carcass, the value the tint fades to. */
    public static final float ROTTEN_TINT = 0.6f;

    /**
     * A person's clothing that is always drawn and can stand proud of a
     * piece's own box. {@link Animator#applyAppearance} recolours the vest but
     * never hides it, and it is 2 cm wider than the torso, so a shoulder wound
     * must clear it. Every other kit part comes and goes with the look, so it
     * cannot decide a wound's depth. An animal's parts are always all drawn,
     * so any of them may be a shell.
     */
    private static final List<String> HUMAN_SHELLS = List.of("vest");

    private static final int FAMILIES = BodyFamily.values().length;
    /** Per family, per piece: culling radius about the collision box centre, at rest scale. */
    private static final float[][] RADIUS = new float[FAMILIES][];
    /** Per family, per piece, per cut: centre x, y, z then size x, y, z, rest-pose model space. */
    private static final float[][][][] CUTS = new float[FAMILIES][][][];

    static {
        for (BodyFamily family : BodyFamily.values()) {
            ModelPart root = AnatomyModels.modelOf(family).root;
            List<FragmentPiece> pieces = family.anatomy().pieces;
            RADIUS[family.ordinal()] = new float[pieces.size()];
            CUTS[family.ordinal()] = new float[pieces.size()][][];
            for (FragmentPiece p : pieces) {
                List<float[]> shells = shells(root, p);
                float[][] cuts = new float[p.cuts.size()][];
                for (int i = 0; i < cuts.length; i++) {
                    cuts[i] = standProud(p.cuts.get(i), shells);
                }
                CUTS[family.ordinal()][p.id] = cuts;
                RADIUS[family.ordinal()][p.id] = reach(root, p, cuts);
            }
        }
    }

    private FragmentModels() {
    }

    // ------------------------------------------------------------------
    // Transforms
    // ------------------------------------------------------------------

    /**
     * The frame the piece's root part is drawn in, once {@link Animator#poseFragment}
     * has posed its model: {@link BodyFragment#rootTransform}, lowered by
     * {@link #contactDrop}. Every box the living model drew for this piece is
     * drawn where the simulation has the piece, turned the way it turned.
     */
    public static Matrix4f rootFrame(BodyFragment f, Matrix4f dest) {
        return f.rootTransform(dest).translateLocal(0f, -contactDrop(f), 0f);
    }

    /**
     * The collision box's own frame, set up to take a point of the piece's
     * box in rest-pose model space — a wound from its table — to where the
     * piece has it now:
     * {@code translate(pos − drop) × rotate(orientation) × scale(pieceScale) × translate(−restCentre)}.
     * The box part's frame in the pose the body died in is this turn and
     * scale, so a wound lies on the face of the box as drawn however the piece
     * tumbles. For a piece in the rest pose it is the whole piece's frame.
     */
    public static Matrix4f pieceFrame(BodyFragment f, Matrix4f dest) {
        float scale = f.pose.pieceScale(f.definition.id);
        return dest.translation(f.pos.x, f.pos.y - contactDrop(f), f.pos.z).rotate(f.orientation)
                .scale(scale).translate(-f.restCentreX, -f.restCentreY, -f.restCentreZ);
    }

    /**
     * How far below its simulated centre a piece is drawn, so a thin piece
     * lies on the ground that holds it up. The fragment sweep never shrinks
     * below {@link BodyFragmentConstants#MIN_HALF_EXTENT}, so a bird's wing
     * lying flat rests with its sweep box on the ground and its 2 cm board
     * 4 cm above it. The piece is drawn at the bottom of its sweep box
     * instead: lowered by the sweep's height over the turned box's own, eased
     * in as it slows below {@link #CONTACT_SPEED}. A piece at rest touches the
     * ground; a piece leaving a body is drawn exactly where the body drew it.
     *
     * <p>The sweep's height is the one {@code BodyFragmentSystem} fits to the
     * orientation, worked out here from the orientation itself, so a drawing
     * never depends on when the sweep box was last refit.
     */
    public static float contactDrop(BodyFragment f) {
        Quaternionf q = f.orientation;
        float x = q.x, y = q.y, z = q.z, w = q.w;
        // The world Y of the box's three axes: the rotation's second row, as
        // BodyFragmentSystem reads it when it fits the sweep box.
        float xUp = 2f * (x * y + z * w);
        float yUp = y * y - z * z + w * w - x * x;
        float zUp = 2f * (y * z - x * w);
        float height = Math.abs(xUp) * f.halfX + Math.abs(yUp) * f.halfY + Math.abs(zUp) * f.halfZ;
        float inflation = BodyFragmentConstants.MIN_HALF_EXTENT - height;
        float ease = 1f - f.vel.length() / CONTACT_SPEED;
        if (inflation <= 0f || ease <= 0f) {
            return 0f;
        }
        return inflation * Math.min(1f, ease);
    }

    // ------------------------------------------------------------------
    // Culling
    // ------------------------------------------------------------------

    /**
     * Culling radius about the piece's drawn centre, {@link #contactDrop}
     * below {@code pos}: everything it can draw, at the scale it was drawn with
     * at death.
     */
    public static float radius(BodyFragment f) {
        return radius(f.definition) * f.pose.pieceScale(f.definition.id);
    }

    /**
     * Culling radius about the collision box centre at rest scale: the whole
     * box, every wound and every part the piece owns — antlers, horns, a
     * slung spear — however any joint inside the piece is turned.
     */
    public static float radius(FragmentPiece p) {
        return RADIUS[p.family.ordinal()][p.id];
    }

    // ------------------------------------------------------------------
    // Cut faces
    // ------------------------------------------------------------------

    public static int cutCount(FragmentPiece p) {
        return cuts(p).length;
    }

    /** Centre of cut {@code i}, rest-pose model space. */
    public static Vector3f cutCentre(FragmentPiece p, int i, Vector3f dest) {
        float[] c = cuts(p)[i];
        return dest.set(c[0], c[1], c[2]);
    }

    /** Size of cut {@code i} along the model axes; the smallest one is its thickness. */
    public static Vector3f cutSize(FragmentPiece p, int i, Vector3f dest) {
        float[] c = cuts(p)[i];
        return dest.set(c[3], c[4], c[5]);
    }

    /**
     * The model matrix for drawing cut {@code i} with the renderer's unit cube,
     * which stands on its base, given the piece's {@link #pieceFrame}.
     */
    public static Matrix4f cutFrame(FragmentPiece p, int i, Matrix4f pieceFrame, Matrix4f dest) {
        float[] c = cuts(p)[i];
        return dest.set(pieceFrame).translate(c[0], c[1] - c[4] * 0.5f, c[2]).scale(c[3], c[4], c[5]);
    }

    private static float[][] cuts(FragmentPiece p) {
        return CUTS[p.family.ordinal()][p.id];
    }

    /**
     * A wound from the table, which lies flat on a face of the piece's
     * collision box, given depth: from the face out to {@link #CUT_PROUD}
     * beyond the outermost shell over it, where a shell is a box of the piece
     * that stands no more than {@link #SHELL_REACH} beyond that face and
     * covers part of the wound. A box reaching further out, a shoulder pad
     * over the stump of an arm, lies over the joint rather than on the face,
     * and the wound stays on the body.
     */
    private static float[] standProud(FragmentCut cut, List<float[]> shells) {
        int axis = cut.axis;
        float side = cut.side;
        float[] out = {cut.centreX, cut.centreY, cut.centreZ, cut.sizeX, cut.sizeY, cut.sizeZ};
        float face = out[axis];
        float outer = face;
        for (float[] box : shells) {
            float edge = side > 0 ? box[3 + axis] : box[axis];
            float beyond = side * (edge - face);
            if (beyond <= 0f || beyond > SHELL_REACH + 1e-6f || !overlaps(out, axis, box)) {
                continue;
            }
            if (side * (edge - outer) > 0f) {
                outer = edge;
            }
        }
        float tip = outer + side * CUT_PROUD;
        out[axis] = (face + tip) * 0.5f;
        out[3 + axis] = Math.abs(tip - face);
        return out;
    }

    /** True when the cut's footprint across its face overlaps a box's. */
    private static boolean overlaps(float[] cut, int axis, float[] box) {
        for (int a = 0; a < 3; a++) {
            if (a == axis) {
                continue;
            }
            float lo = cut[a] - cut[3 + a] * 0.5f;
            float hi = cut[a] + cut[3 + a] * 0.5f;
            if (hi <= box[a] || lo >= box[3 + a]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Rest-pose model-space bounds — min x, y, z then max x, y, z — of every
     * box the piece draws that may be a shell over its wounds.
     */
    private static List<float[]> shells(ModelPart root, FragmentPiece piece) {
        List<float[]> out = new ArrayList<>();
        collectShells(root.find(piece.rootPart), piece, piece.anchorX, piece.anchorY, piece.anchorZ, out);
        return out;
    }

    private static void collectShells(ModelPart part, FragmentPiece piece, float x, float y, float z,
                                      List<float[]> out) {
        float px = x + part.pivotX;
        float py = y + part.pivotY;
        float pz = z + part.pivotZ;
        boolean shell = piece.family != BodyFamily.HUMANOID || HUMAN_SHELLS.contains(part.name);
        if (part.sizeX > 0 && shell) {
            float cx = px + part.boxX, cy = py + part.boxY, cz = pz + part.boxZ;
            out.add(new float[] {
                    cx - part.sizeX * 0.5f, cy - part.sizeY * 0.5f, cz - part.sizeZ * 0.5f,
                    cx + part.sizeX * 0.5f, cy + part.sizeY * 0.5f, cz + part.sizeZ * 0.5f,
            });
        }
        for (ModelPart child : part.children) {
            if (!piece.excludedParts.contains(child.name)) {
                collectShells(child, piece, px, py, pz, out);
            }
        }
    }

    // ------------------------------------------------------------------
    // Reach
    // ------------------------------------------------------------------

    /**
     * How far from its collision box centre anything the piece draws can
     * reach: its box, each wound, and every part hanging below its box part,
     * bounded link by link — the distance to a part's pivot plus the length
     * of each pivot below it plus the far corner of its box — so the bound
     * holds however the animation or the look turns any of those joints.
     *
     * @throws IllegalStateException when a box the piece draws does not hang
     *         below its box part, where this bound does not reach
     */
    private static float reach(ModelPart root, FragmentPiece piece, float[][] cuts) {
        ModelPart box = root.find(piece.boxPart);
        requireAllBelow(root.find(piece.rootPart), box, piece);
        float reach = length(piece.halfX, piece.halfY, piece.halfZ);
        for (ModelPart child : box.children) {
            if (!piece.excludedParts.contains(child.name)) {
                float start = length(child.pivotX - piece.boxOffsetX, child.pivotY - piece.boxOffsetY,
                        child.pivotZ - piece.boxOffsetZ);
                reach = Math.max(reach, chain(child, start, piece));
            }
        }
        for (float[] cut : cuts) {
            reach = Math.max(reach, length(cut[0] - piece.centreX, cut[1] - piece.centreY,
                    cut[2] - piece.centreZ) + 0.5f * length(cut[3], cut[4], cut[5]));
        }
        return reach;
    }

    /** The reach of {@code part} and everything below it, whose pivot lies at most {@code start} away. */
    private static float chain(ModelPart part, float start, FragmentPiece piece) {
        float reach = start;
        if (part.sizeX > 0) {
            reach = start + length(part.boxX, part.boxY, part.boxZ)
                    + 0.5f * length(part.sizeX, part.sizeY, part.sizeZ);
        }
        for (ModelPart child : part.children) {
            if (!piece.excludedParts.contains(child.name)) {
                reach = Math.max(reach, chain(child, start + length(child.pivotX, child.pivotY, child.pivotZ),
                        piece));
            }
        }
        return reach;
    }

    /** Every box drawn from {@code part} lies at or below {@code box}; the parts above it are connectors. */
    private static void requireAllBelow(ModelPart part, ModelPart box, FragmentPiece piece) {
        if (part == box) {
            return;
        }
        if (part.sizeX > 0) {
            throw new IllegalStateException(piece + " draws " + part.name + ", which is not below its box part "
                    + box.name);
        }
        for (ModelPart child : part.children) {
            if (!piece.excludedParts.contains(child.name)) {
                requireAllBelow(child, box, piece);
            }
        }
    }

    private static float length(float x, float y, float z) {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    // ------------------------------------------------------------------
    // Rot
    // ------------------------------------------------------------------

    /**
     * How rotten a piece looks: 0 while it is in flight or has more than
     * {@link #ROT_FRACTION} of its decay left, rising to 1 as the decay runs
     * out. The renderer fades its tint towards the rotten carcass tint by it.
     */
    public static float rot(BodyFragment f) {
        if (!f.settled) {
            return 0f;
        }
        float window = ROT_FRACTION * RagdollConstants.CORPSE_DECAY;
        return Math.clamp(1f - f.decay / window, 0f, 1f);
    }

    /**
     * The piece's tint multiplier: white while fresh, fading with {@link #rot}
     * to exactly the rotten carcass tint {@code renderCarcasses} uses,
     * {@code (0.6, 0.6 × 0.9, 0.6 × 0.85)}.
     */
    public static Vector3f tint(BodyFragment f, Vector3f dest) {
        float rot = rot(f);
        float shade = 1f - (1f - ROTTEN_TINT) * rot;
        return dest.set(shade, shade * (1f - 0.1f * rot), shade * (1f - 0.15f * rot));
    }
}
