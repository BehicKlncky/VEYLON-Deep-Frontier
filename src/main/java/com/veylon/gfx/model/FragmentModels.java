package com.veylon.gfx.model;

import com.veylon.entity.BodyFragment;
import com.veylon.entity.BodyFragment.Piece;
import com.veylon.entity.RagdollConstants;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draw geometry for the pieces of a person blown apart: where each piece's
 * root part hangs, where its cut faces sit, how far it reaches for culling and
 * how far it has rotted.
 *
 * <p>A piece is drawn from its root part rather than the model root, because
 * {@link Animator#isolatePart} hides the ancestors and a hidden part draws
 * nothing below it. In the rest pose those ancestors carry no rotation, so all
 * they contribute is the sum of their pivots, the <em>anchor</em>, and the
 * piece draws exactly where the whole model would have drawn it.
 *
 * <p>Everything is computed once from {@link NpcModels}' pivots and boxes and
 * {@link Piece}'s table, both fixed at build time. It is plain CPU data, so the
 * renderer reads it without allocating and tests check it without GL.
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
    public static final float CUT_INSET = 0.8f;
    /**
     * Added to a piece's box half-diagonal for view culling, for the
     * accessories that reach past the box: a slung spear, a crest, a pack.
     */
    public static final float CULL_MARGIN = 0.3f;
    /**
     * Share of {@link RagdollConstants#CORPSE_DECAY} at the end of a settled
     * piece's life over which its tint darkens to the rotten carcass tint.
     */
    public static final float ROT_FRACTION = 0.25f;
    /** Red channel of a fully rotten carcass, the value the tint fades to. */
    public static final float ROTTEN_TINT = 0.6f;

    /**
     * Clothing that is always drawn and can stand proud of a piece's own box.
     * {@link Animator#applyAppearance} recolours the vest but never hides it,
     * and it is 2 cm wider than the torso, so a shoulder wound must clear it.
     */
    private static final String[] SHELLS = {"vest"};

    private static final int PIECES = Piece.values().length;
    private static final float[][] ANCHOR = new float[PIECES][];
    /** Per piece, per cut: centre x, y, z then size x, y, z, rest-pose model space. */
    private static final float[][][] CUTS = new float[PIECES][][];
    private static final float[] RADIUS = new float[PIECES];

    static {
        ModelPart root = NpcModels.get().root;
        List<List<float[]>> cuts = new ArrayList<>();
        for (int i = 0; i < PIECES; i++) {
            cuts.add(new ArrayList<>());
        }
        for (Piece p : Piece.values()) {
            ANCHOR[p.ordinal()] = new float[3];
            if (!anchor(root, p.rootPart, 0, 0, 0, ANCHOR[p.ordinal()])) {
                throw new IllegalStateException("no model part " + p.rootPart + " for " + p);
            }
            RADIUS[p.ordinal()] = (float) Math.sqrt(p.halfX * p.halfX + p.halfY * p.halfY
                    + p.halfZ * p.halfZ) + CULL_MARGIN;
        }
        // Every piece but the torso was cut from exactly one neighbour, at its
        // own root pivot. The neighbour is the piece that excludes it; the legs
        // hang off the model root, which is the hip at the torso's lower face.
        for (Piece child : Piece.values()) {
            if (!child.severed) {
                continue;
            }
            Piece parent = Piece.TORSO;
            for (Piece p : Piece.values()) {
                if (p.excludedParts.contains(child.rootPart)) {
                    parent = p;
                }
            }
            cuts.get(parent.ordinal()).add(cutFace(root, parent, child));
            cuts.get(child.ordinal()).add(cutFace(root, child, child));
        }
        for (int i = 0; i < PIECES; i++) {
            CUTS[i] = cuts.get(i).toArray(new float[0][]);
        }
    }

    private FragmentModels() {
    }

    // ------------------------------------------------------------------
    // Transforms
    // ------------------------------------------------------------------

    /**
     * The piece's model matrix:
     * {@code translate(pos) × rotate(orientation) × translate(−restCentre)},
     * which takes a rest-pose model point of the piece to where the simulation
     * has it now. Cut faces are placed in this frame.
     */
    public static Matrix4f pieceTransform(BodyFragment f, Matrix4f dest) {
        return dest.translation(f.pos).rotate(f.orientation)
                .translate(-f.restCentreX, -f.restCentreY, -f.restCentreZ);
    }

    /** The frame the piece's root part is drawn in: its anchor, in the piece's frame. */
    public static Matrix4f rootTransform(Piece p, Matrix4f pieceTransform, Matrix4f dest) {
        float[] a = ANCHOR[p.ordinal()];
        return dest.set(pieceTransform).translate(a[0], a[1], a[2]);
    }

    /** Sum of the pivots above the piece's root part, rest pose, model space. */
    public static Vector3f anchor(Piece p, Vector3f dest) {
        float[] a = ANCHOR[p.ordinal()];
        return dest.set(a[0], a[1], a[2]);
    }

    /** Culling radius about the piece's centre. */
    public static float radius(Piece p) {
        return RADIUS[p.ordinal()];
    }

    // ------------------------------------------------------------------
    // Cut faces
    // ------------------------------------------------------------------

    public static int cutCount(Piece p) {
        return CUTS[p.ordinal()].length;
    }

    /** Centre of cut {@code i}, rest-pose model space. */
    public static Vector3f cutCentre(Piece p, int i, Vector3f dest) {
        float[] c = CUTS[p.ordinal()][i];
        return dest.set(c[0], c[1], c[2]);
    }

    /** Size of cut {@code i} along the model axes; the smallest one is its thickness. */
    public static Vector3f cutSize(Piece p, int i, Vector3f dest) {
        float[] c = CUTS[p.ordinal()][i];
        return dest.set(c[3], c[4], c[5]);
    }

    /**
     * A wound on {@code piece} where {@code severed} was cut away at its root
     * pivot. It lies on the face of {@code piece}'s box that the joint is
     * farthest beyond, relative to the box's half size; it covers the severed
     * limb's cross-section, less {@link #CUT_INSET}, kept on that face; and it
     * runs from the face out to {@link #CUT_PROUD} beyond the outermost always
     * drawn surface over it.
     */
    private static float[] cutFace(ModelPart root, Piece piece, Piece severed) {
        float[] joint = {severed.pivotX, severed.pivotY, severed.pivotZ};
        float[] centre = {piece.centreX, piece.centreY, piece.centreZ};
        float[] half = {piece.halfX, piece.halfY, piece.halfZ};
        int axis = 0;
        float best = -1;
        for (int a = 0; a < 3; a++) {
            float beyond = Math.abs(joint[a] - centre[a]) / half[a];
            if (beyond > best) {
                best = beyond;
                axis = a;
            }
        }
        float side = joint[axis] >= centre[axis] ? 1f : -1f;
        float face = centre[axis] + side * half[axis];

        // The limb keeps its depth along Z; its width lies along the other face axis.
        float[] out = new float[6];
        for (int a = 0; a < 3; a++) {
            if (a == axis) {
                continue;
            }
            float limb = a == 2 ? severed.halfZ * 2f : severed.halfX * 2f;
            float size = Math.min(limb, half[a] * 2f) * CUT_INSET;
            float reach = half[a] - size * 0.5f;
            out[a] = Math.clamp(joint[a], centre[a] - reach, centre[a] + reach);
            out[3 + a] = size;
        }

        float outer = face;
        float[] min = new float[3];
        float[] max = new float[3];
        for (String shell : SHELLS) {
            if (!shellBounds(root, piece, shell, min, max)) {
                continue;
            }
            float edge = side > 0 ? max[axis] : min[axis];
            if (side * (edge - outer) <= 0 || !overlaps(out, axis, min, max)) {
                continue;
            }
            outer = edge;
        }
        float tip = outer + side * CUT_PROUD;
        out[axis] = (face + tip) * 0.5f;
        out[3 + axis] = Math.abs(tip - face);
        return out;
    }

    /** True when the cut's footprint across its face overlaps a box's. */
    private static boolean overlaps(float[] cut, int axis, float[] min, float[] max) {
        for (int a = 0; a < 3; a++) {
            if (a == axis) {
                continue;
            }
            float lo = cut[a] - cut[3 + a] * 0.5f;
            float hi = cut[a] + cut[3 + a] * 0.5f;
            if (hi <= min[a] || lo >= max[a]) {
                return false;
            }
        }
        return true;
    }

    /** Model-space bounds of a shell part that {@code piece} draws, if it draws it. */
    private static boolean shellBounds(ModelPart root, Piece piece, String shell,
                                       float[] min, float[] max) {
        ModelPart pieceRoot = root.find(piece.rootPart);
        if (pieceRoot == null || pieceRoot.find(shell) == null) {
            return false;
        }
        for (String excluded : piece.excludedParts) {
            ModelPart cut = pieceRoot.find(excluded);
            if (cut != null && cut.find(shell) != null) {
                return false;
            }
        }
        float[] at = new float[3];
        anchor(root, shell, 0, 0, 0, at);
        ModelPart part = root.find(shell);
        at[0] += part.pivotX + part.boxX;
        at[1] += part.pivotY + part.boxY;
        at[2] += part.pivotZ + part.boxZ;
        float[] size = {part.sizeX, part.sizeY, part.sizeZ};
        for (int a = 0; a < 3; a++) {
            min[a] = at[a] - size[a] * 0.5f;
            max[a] = at[a] + size[a] * 0.5f;
        }
        return true;
    }

    /** Sums the pivots above {@code target}; false when there is no such part. */
    private static boolean anchor(ModelPart p, String target, float x, float y, float z, float[] out) {
        if (p.name.equals(target)) {
            out[0] = x;
            out[1] = y;
            out[2] = z;
            return true;
        }
        float px = x + p.pivotX;
        float py = y + p.pivotY;
        float pz = z + p.pivotZ;
        for (int i = 0; i < p.children.size(); i++) {
            if (anchor(p.children.get(i), target, px, py, pz, out)) {
                return true;
            }
        }
        return false;
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
