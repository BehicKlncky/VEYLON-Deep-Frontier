package com.veylon.entity;

import com.veylon.entity.Creature.CreatureType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where one kind of body comes apart: its model's joints and the pieces a
 * lethal blast cuts it into.
 *
 * <p>A body is described by its <em>joint table</em>. Every model part a piece
 * is cut at, hangs from or flies as is a joint, and so is every part the living
 * animation moves, so that a {@link FragmentPose} recorded at death holds the
 * whole pose. A joint names its model part, its parent joint, its pivot in the
 * parent's frame and, when it has one, its box — the model builders' own
 * numbers ({@code NpcModels}, {@code CreatureModels}), written out here because
 * the simulation does not read the renderer's models. {@link Builder#split}
 * halves a box exactly as {@code ModelPart.split} does. The graphics side holds
 * every table to its model ({@code gfx.model.AnatomyModels.validate}), so a
 * model edit that is not repeated here fails loudly instead of drawing pieces
 * in the wrong place.
 *
 * <p>Pieces are declared by the joint they are cut at, core first. A piece
 * owns that joint and everything below it down to the next piece's joint;
 * accessories without a joint of their own — ears, antlers, horns, a beak, a
 * vest — ride along with their parent part. The box-less model root belongs to
 * the core, and a box-less connector ({@code neck}, the hare's
 * {@code head_joint}) to the piece below it, so no invisible, massless piece
 * ever exists. A split half shorter than
 * {@link BodyFragmentConstants#MIN_SEPARATE_PIECE} stays merged with the part
 * it was split from; a longer one is its own piece.
 *
 * <p>Tables are built once, when this class loads, and never change. Nothing
 * here holds a model or a live entity.
 */
public final class FragmentAnatomy {

    /** Most joints a table may list; a pose snapshot stores one transform per joint. */
    public static final int MAX_JOINTS = 24;
    /** Most pieces one body comes apart into: the skeleton's bone limit. */
    public static final int MAX_PIECES = BodySkeleton.MAX_BONES;
    /**
     * A cut joint this close to a face of its parent's box, relative to the
     * box's half size, counts as lying on that face.
     */
    private static final float ON_FACE = 1e-3f;
    /** Leg part names shared by the quadrupeds and the hare; declared before the tables are built. */
    private static final String[] LEGS = {"leg_fl", "leg_fr", "leg_bl", "leg_br"};

    private static final Map<BodyFamily, FragmentAnatomy> TABLES = new EnumMap<>(BodyFamily.class);

    static {
        for (BodyFamily family : BodyFamily.values()) {
            TABLES.put(family, table(family));
        }
    }

    /** One model part of the table, in the model's own terms. */
    public static final class Joint {
        public final int index;
        /** The model part's name. */
        public final String name;
        /** Index of the parent joint, or -1 for the model root. */
        public final int parent;
        /** Pivot in the parent's frame; the model root's is in model space. */
        public final float pivotX, pivotY, pivotZ;
        /** False for a box-less connector: the model root, a neck pivot. */
        public final boolean boxed;
        /** Box centre relative to the pivot, and box size; zero when not {@link #boxed}. */
        public final float boxX, boxY, boxZ, sizeX, sizeY, sizeZ;
        /** True when this is the tip {@code ModelPart.split} cut from its parent's box. */
        public final boolean splitTip;
        /** Id of the piece that owns it. */
        public final int piece;

        private Joint(int index, Spec s, int parent, int piece) {
            this.index = index;
            this.name = s.name;
            this.parent = parent;
            this.pivotX = s.pivotX;
            this.pivotY = s.pivotY;
            this.pivotZ = s.pivotZ;
            this.boxed = s.boxed;
            this.boxX = s.boxX;
            this.boxY = s.boxY;
            this.boxZ = s.boxZ;
            this.sizeX = s.sizeX;
            this.sizeY = s.sizeY;
            this.sizeZ = s.sizeZ;
            this.splitTip = s.splitTip;
            this.piece = piece;
        }
    }

    public final BodyFamily family;
    /** Every piece, core first; a piece's id is its index here. */
    public final List<FragmentPiece> pieces;
    private final Joint[] joints;
    private final Map<String, Integer> jointIndex;
    private final FragmentPose restPose;

    // ------------------------------------------------------------------
    // Lookup
    // ------------------------------------------------------------------

    public static FragmentAnatomy of(BodyFamily family) {
        return TABLES.get(family);
    }

    public static FragmentAnatomy of(CreatureType type) {
        return TABLES.get(BodyFamily.of(type));
    }

    public static FragmentAnatomy humanoid() {
        return TABLES.get(BodyFamily.HUMANOID);
    }

    public int jointCount() {
        return joints.length;
    }

    public Joint joint(int index) {
        return joints[index];
    }

    /** Index of the joint at the named model part, or -1 when the table has none. */
    public int jointIndex(String part) {
        Integer i = jointIndex.get(part);
        return i == null ? -1 : i;
    }

    public FragmentPiece piece(int id) {
        return pieces.get(id);
    }

    /** The piece with this name, or null. */
    public FragmentPiece piece(String name) {
        for (FragmentPiece p : pieces) {
            if (p.name.equals(name)) {
                return p;
            }
        }
        return null;
    }

    /** The standing rest pose: every joint unrotated, as a piece with no captured pose is placed. */
    public FragmentPose restPose() {
        return restPose;
    }

    public static Builder builder(BodyFamily family) {
        return new Builder(family);
    }

    // ------------------------------------------------------------------
    // Authoring
    // ------------------------------------------------------------------

    /**
     * Collects a joint table and a piece list; {@link #build} checks and derives
     * everything else. Joints are listed parent first, starting with the
     * box-less model root.
     */
    public static final class Builder {
        private final BodyFamily family;
        private final List<Spec> joints = new ArrayList<>();
        private final List<String[]> pieces = new ArrayList<>();

        private Builder(BodyFamily family) {
            this.family = family;
        }

        /** A box-less joint: the model root (parent null) or a connector such as a neck. */
        public Builder joint(String name, String parent, float x, float y, float z) {
            joints.add(new Spec(name, parent).pivot(x, y, z));
            return this;
        }

        /** A joint with a box, exactly as {@code new ModelPart(name).pivot(..).box(..)} declares it. */
        public Builder part(String name, String parent, float x, float y, float z,
                            float boxX, float boxY, float boxZ, float sizeX, float sizeY, float sizeZ) {
            joints.add(new Spec(name, parent).pivot(x, y, z).box(boxX, boxY, boxZ, sizeX, sizeY, sizeZ));
            return this;
        }

        /**
         * Halves a declared joint's box at its midpoint and adds the far half as
         * the joint {@code tip}, with {@code ModelPart.split}'s arithmetic.
         */
        public Builder split(String name, String tip, boolean alongY) {
            Spec s = null;
            for (Spec j : joints) {
                if (j.name != null && j.name.equals(name)) {
                    s = j;
                }
            }
            if (s == null || !s.boxed) {
                throw new IllegalArgumentException(family + ": cannot split " + name + ", which is not a boxed joint");
            }
            if (s.split) {
                throw new IllegalArgumentException(family + ": " + name + " is already split");
            }
            s.split = true;
            Spec t = new Spec(tip, name);
            t.splitTip = true;
            if (alongY) {
                t.pivot(0, s.boxY, 0).box(s.boxX, -s.sizeY * 0.25f, s.boxZ, s.sizeX, s.sizeY * 0.5f, s.sizeZ);
                s.boxY += s.sizeY * 0.25f;
                s.sizeY *= 0.5f;
            } else {
                t.pivot(0, 0, s.boxZ).box(s.boxX, s.boxY, s.sizeZ * 0.25f, s.sizeX, s.sizeY, s.sizeZ * 0.5f);
                s.boxZ -= s.sizeZ * 0.25f;
                s.sizeZ *= 0.5f;
            }
            joints.add(t);
            return this;
        }

        /**
         * A piece cut at joint {@code root} whose collision box is {@code box}'s:
         * the root itself, or the first box below a box-less root.
         */
        public Builder piece(String name, String root, String box) {
            pieces.add(new String[] {name, root, box});
            return this;
        }

        /** @throws IllegalArgumentException naming the first thing wrong with the table */
        public FragmentAnatomy build() {
            return new FragmentAnatomy(family, joints, pieces);
        }
    }

    private static final class Spec {
        final String name;
        final String parent;
        float pivotX, pivotY, pivotZ;
        boolean boxed;
        float boxX, boxY, boxZ, sizeX, sizeY, sizeZ;
        boolean split;
        boolean splitTip;

        Spec(String name, String parent) {
            this.name = name;
            this.parent = parent;
        }

        Spec pivot(float x, float y, float z) {
            pivotX = x;
            pivotY = y;
            pivotZ = z;
            return this;
        }

        Spec box(float x, float y, float z, float sx, float sy, float sz) {
            boxed = true;
            boxX = x;
            boxY = y;
            boxZ = z;
            sizeX = sx;
            sizeY = sy;
            sizeZ = sz;
            return this;
        }

        float longest() {
            return Math.max(sizeX, Math.max(sizeY, sizeZ));
        }
    }

    // ------------------------------------------------------------------
    // Validation and derivation
    // ------------------------------------------------------------------

    private FragmentAnatomy(BodyFamily family, List<Spec> specs, List<String[]> pieceSpecs) {
        if (family == null) {
            throw new IllegalArgumentException("an anatomy needs a body family");
        }
        this.family = family;
        int n = specs.size();
        if (n == 0) {
            throw fail(family, "has no joints");
        }
        if (n > MAX_JOINTS) {
            throw fail(family, "lists " + n + " joints; a pose holds at most " + MAX_JOINTS);
        }

        // Joints: parent first, finite, boxes with a real size.
        Map<String, Integer> index = new HashMap<>();
        int[] parent = new int[n];
        for (int j = 0; j < n; j++) {
            Spec s = specs.get(j);
            if (s.name == null || s.name.isEmpty()) {
                throw fail(family, "joint " + j + " has no name");
            }
            if (index.containsKey(s.name)) {
                throw fail(family, "joint " + s.name + " is declared twice");
            }
            if (j == 0) {
                if (s.parent != null) {
                    throw fail(family, "the first joint must be the model root, not " + s.name
                            + " under " + s.parent);
                }
                if (s.boxed) {
                    throw fail(family, "the model root " + s.name + " must be a box-less connector");
                }
                parent[j] = -1;
            } else {
                if (s.parent == null) {
                    throw fail(family, "joint " + s.name + " has no parent; only the first joint is the model root");
                }
                Integer p = index.get(s.parent);
                if (p == null) {
                    throw fail(family, "joint " + s.name + " hangs from " + s.parent
                            + ", which is not a joint declared before it");
                }
                parent[j] = p;
            }
            if (!finite(s.pivotX, s.pivotY, s.pivotZ)) {
                throw fail(family, "joint " + s.name + " has a non-finite pivot");
            }
            if (s.boxed) {
                if (!finite(s.boxX, s.boxY, s.boxZ) || !finite(s.sizeX, s.sizeY, s.sizeZ)) {
                    throw fail(family, "joint " + s.name + " has a non-finite box");
                }
                if (!(s.sizeX > 0) || !(s.sizeY > 0) || !(s.sizeZ > 0)) {
                    throw fail(family, "joint " + s.name + " has a box size that is not positive: "
                            + s.sizeX + " x " + s.sizeY + " x " + s.sizeZ);
                }
            }
            index.put(s.name, j);
        }

        // Pieces: named once, each cut at its own joint, never at the model root.
        int count = pieceSpecs.size();
        if (count == 0) {
            throw fail(family, "has no pieces");
        }
        if (count > MAX_PIECES) {
            throw fail(family, "comes apart into " + count + " pieces; at most " + MAX_PIECES + " are allowed");
        }
        String[] names = new String[count];
        int[] rootOf = new int[count];
        int[] boxOf = new int[count];
        int[] pieceAt = new int[n];
        Arrays.fill(pieceAt, -1);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < count; i++) {
            String[] p = pieceSpecs.get(i);
            names[i] = p[0];
            if (p[0] == null || p[0].isEmpty()) {
                throw fail(family, "piece " + i + " has no name");
            }
            if (!seen.add(p[0])) {
                throw fail(family, "piece " + p[0] + " is declared twice");
            }
            Integer root = p[1] == null ? null : index.get(p[1]);
            if (root == null) {
                throw fail(family, "piece " + p[0] + " is cut at " + p[1] + ", which is not a joint");
            }
            if (root == 0) {
                throw fail(family, "piece " + p[0] + " cannot be cut at the model root, which is a connector");
            }
            if (pieceAt[root] >= 0) {
                throw fail(family, "joint " + p[1] + " is the root of two pieces: "
                        + names[pieceAt[root]] + " and " + p[0]);
            }
            pieceAt[root] = i;
            rootOf[i] = root;
            Integer box = p[2] == null ? null : index.get(p[2]);
            if (box == null) {
                throw fail(family, "piece " + p[0] + " takes its box from " + p[2] + ", which is not a joint");
            }
            boxOf[i] = box;
        }
        if (parent[rootOf[0]] != 0) {
            throw fail(family, "the first piece, " + names[0]
                    + ", is the core the others are cut from and must hang from the model root");
        }

        // Ownership: a joint belongs to the nearest piece root above it, the
        // model root to the core; every other joint must lie under its owner.
        int[] owner = new int[n];
        for (int j = 1; j < n; j++) {
            owner[j] = pieceAt[j] >= 0 ? pieceAt[j] : owner[parent[j]];
            if (!below(parent, rootOf[owner[j]], j)) {
                throw fail(family, "joint " + specs.get(j).name
                        + " is under no piece: cut a piece at it or at a joint above it");
            }
        }

        for (int i = 0; i < count; i++) {
            int box = boxOf[i];
            Spec b = specs.get(box);
            if (!b.boxed) {
                throw fail(family, "piece " + names[i] + " takes its box from " + b.name + ", which has none");
            }
            if (owner[box] != i) {
                throw fail(family, "piece " + names[i] + "'s box part " + b.name
                        + " belongs to piece " + names[owner[box]]);
            }
            for (int k = parent[box]; box != rootOf[i] && k != parent[rootOf[i]]; k = parent[k]) {
                if (specs.get(k).boxed) {
                    throw fail(family, "piece " + names[i] + "'s box part " + b.name
                            + " is not the first box below its cut: " + specs.get(k).name + " has one");
                }
            }
        }
        for (int j = 1; j < n; j++) {
            Spec s = specs.get(j);
            if (!s.splitTip) {
                continue;
            }
            boolean separate = pieceAt[j] >= 0;
            if (s.longest() >= BodyFragmentConstants.MIN_SEPARATE_PIECE && !separate) {
                throw fail(family, "split half " + s.name + " is " + s.longest()
                        + " m long and must be a piece of its own (MIN_SEPARATE_PIECE)");
            }
            if (s.longest() < BodyFragmentConstants.MIN_SEPARATE_PIECE && separate) {
                throw fail(family, "split half " + s.name + " is only " + s.longest()
                        + " m long and must stay with " + s.parent + " (MIN_SEPARATE_PIECE)");
            }
        }

        this.joints = new Joint[n];
        for (int j = 0; j < n; j++) {
            joints[j] = new Joint(j, specs.get(j), parent[j], owner[j]);
        }
        this.jointIndex = Map.copyOf(index);

        // Rest-pose origin of every joint: its pivots summed, parent first.
        float[][] origin = new float[n][3];
        for (int j = 0; j < n; j++) {
            Joint k = joints[j];
            float[] base = k.parent < 0 ? new float[3] : origin[k.parent];
            origin[j][0] = base[0] + k.pivotX;
            origin[j][1] = base[1] + k.pivotY;
            origin[j][2] = base[2] + k.pivotZ;
        }

        // Collision boxes, in the box joint's frame and in model space.
        float[][] offset = new float[count][];
        float[][] centre = new float[count][];
        float[][] half = new float[count][];
        float[] volume = new float[count];
        List<List<String>> merged = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Joint b = joints[boxOf[i]];
            float[] min = {b.boxX - b.sizeX * 0.5f, b.boxY - b.sizeY * 0.5f, b.boxZ - b.sizeZ * 0.5f};
            float[] max = {b.boxX + b.sizeX * 0.5f, b.boxY + b.sizeY * 0.5f, b.boxZ + b.sizeZ * 0.5f};
            float vol = b.sizeX * b.sizeY * b.sizeZ;
            List<String> tips = new ArrayList<>();
            float ax = 0, ay = 0, az = 0;
            for (Joint t = tipOf(b); t != null && t.piece == i; t = tipOf(t)) {
                ax += t.pivotX;
                ay += t.pivotY;
                az += t.pivotZ;
                grow(min, max, ax + t.boxX, ay + t.boxY, az + t.boxZ, t.sizeX, t.sizeY, t.sizeZ);
                vol += t.sizeX * t.sizeY * t.sizeZ;
                tips.add(t.name);
            }
            if (tips.isEmpty()) {
                // A single box keeps its own numbers exactly.
                offset[i] = new float[] {b.boxX, b.boxY, b.boxZ};
                half[i] = new float[] {b.sizeX * 0.5f, b.sizeY * 0.5f, b.sizeZ * 0.5f};
            } else {
                offset[i] = new float[3];
                half[i] = new float[3];
                for (int a = 0; a < 3; a++) {
                    offset[i][a] = (min[a] + max[a]) * 0.5f;
                    half[i][a] = (max[a] - min[a]) * 0.5f;
                }
            }
            float[] o = origin[boxOf[i]];
            centre[i] = new float[] {o[0] + offset[i][0], o[1] + offset[i][1], o[2] + offset[i][2]};
            volume[i] = vol;
            merged.add(tips);
        }

        // Wounds: every severed piece is cut once, leaving a face on each side.
        int[] parentPiece = new int[count];
        List<List<FragmentCut>> cuts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            parentPiece[i] = i == 0 ? -1 : owner[parent[rootOf[i]]];
            cuts.add(new ArrayList<>());
        }
        for (int c = 1; c < count; c++) {
            float[] joint = origin[rootOf[c]];
            int p = parentPiece[c];
            cuts.get(p).add(cut(p, c, rootOf[c], joint, centre[p], half[p], centre[c], half[c], false));
            cuts.get(c).add(cut(c, c, rootOf[c], joint, centre[c], half[c], centre[c], half[c], true));
        }

        List<FragmentPiece> built = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int root = rootOf[i];
            List<String> owned = new ArrayList<>();
            List<String> excluded = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                if (owner[j] == i) {
                    owned.add(joints[j].name);
                }
                // A child of this piece's subtree that another piece is cut at.
                if (j != root && pieceAt[j] >= 0 && pieceAt[j] != i && joints[j].parent >= 0
                        && owner[joints[j].parent] == i && below(parent, root, joints[j].parent)) {
                    excluded.add(joints[j].name);
                }
            }
            int up = joints[root].parent;
            built.add(new FragmentPiece(this, i, names[i], root, boxOf[i], parentPiece[i],
                    owned, excluded, merged.get(i), origin[up], origin[root], centre[i], offset[i],
                    half[i], volume[i], cuts.get(i)));
        }
        this.pieces = List.copyOf(built);
        this.restPose = new FragmentPose(this, null);
    }

    /** The joint split from {@code j}'s box, or null. */
    private Joint tipOf(Joint j) {
        for (Joint k : joints) {
            if (k.splitTip && k.parent == j.index) {
                return k;
            }
        }
        return null;
    }

    /** True when {@code joint} is {@code ancestor} or lies below it. */
    private static boolean below(int[] parent, int ancestor, int joint) {
        for (int k = joint; k >= 0; k = parent[k]) {
            if (k == ancestor) {
                return true;
            }
        }
        return false;
    }

    private static void grow(float[] min, float[] max, float x, float y, float z, float sx, float sy, float sz) {
        float[] c = {x, y, z};
        float[] s = {sx, sy, sz};
        for (int a = 0; a < 3; a++) {
            min[a] = Math.min(min[a], c[a] - s[a] * 0.5f);
            max[a] = Math.max(max[a], c[a] + s[a] * 0.5f);
        }
    }

    /**
     * A wound on piece {@code on} where piece {@code severed} was cut at
     * {@code joint}.
     *
     * <p>The face: for the severed piece's own end, and wherever the joint
     * lies on or beyond the parent's box, the face the joint is farthest
     * beyond relative to the box's half size — the shoulder on the torso's
     * flank, the hip on its underside. A joint buried inside the parent's box,
     * like a hare's hip, is cut on the face the severed piece leaves through.
     *
     * <p>The size: the severed piece's cross-section, the two sizes across the
     * way it points from its joint, laid on the face so that a shared axis
     * keeps its size — an arm's depth stays depth on the torso's flank and its
     * width runs up the flank — less {@link BodyFragmentConstants#CUT_INSET},
     * never wider than the face and kept on it.
     */
    private static FragmentCut cut(int on, int severed, int jointIndex, float[] joint,
                                   float[] c, float[] h, float[] severedCentre, float[] severedHalf,
                                   boolean ownEnd) {
        int axis = 0;
        float best = -1;
        for (int a = 0; a < 3; a++) {
            float beyond = Math.abs(joint[a] - c[a]) / h[a];
            if (beyond > best) {
                best = beyond;
                axis = a;
            }
        }
        float side = joint[axis] >= c[axis] ? 1f : -1f;
        if (!ownEnd && best < 1f - ON_FACE) {
            int leave = dominant(severedCentre[0] - joint[0], severedCentre[1] - joint[1],
                    severedCentre[2] - joint[2]);
            if (leave >= 0) {
                axis = leave;
                side = severedCentre[axis] - joint[axis] >= 0 ? 1f : -1f;
            }
        }
        int along = dominant(severedCentre[0] - joint[0], severedCentre[1] - joint[1],
                severedCentre[2] - joint[2]);
        if (along < 0) {
            along = dominant(severedHalf[0], severedHalf[1], severedHalf[2]);
        }

        float[] centre = new float[3];
        float[] size = new float[3];
        centre[axis] = c[axis] + side * h[axis];
        for (int a = 0; a < 3; a++) {
            if (a == axis) {
                continue;
            }
            int source = a != along ? a : axis;
            float limb = severedHalf[source] * 2f;
            float extent = Math.min(limb, h[a] * 2f) * BodyFragmentConstants.CUT_INSET;
            float reach = h[a] - extent * 0.5f;
            centre[a] = Math.clamp(joint[a], c[a] - reach, c[a] + reach);
            size[a] = extent;
        }
        return new FragmentCut(on, severed, jointIndex, axis, side, centre, size);
    }

    /** Axis of the largest absolute component, or -1 for a zero vector. */
    private static int dominant(float x, float y, float z) {
        float ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        if (ax == 0 && ay == 0 && az == 0) {
            return -1;
        }
        return ax >= ay && ax >= az ? 0 : ay >= az ? 1 : 2;
    }

    private static boolean finite(float x, float y, float z) {
        return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z);
    }

    private static IllegalArgumentException fail(BodyFamily family, String what) {
        return new IllegalArgumentException(family + " anatomy: " + what);
    }

    // ------------------------------------------------------------------
    // The tables
    // ------------------------------------------------------------------

    private static FragmentAnatomy table(BodyFamily family) {
        return switch (family) {
            case HUMANOID -> humanoidTable();
            case DEER -> deer();
            case WOLF -> wolf();
            case BIRD -> bird();
            case HARE -> hare();
            case THORNHORN -> thornhorn();
            case STALKER -> stalker();
        };
    }

    /**
     * {@code NpcModels.build()}. Piece order is {@link BodyFragment.Piece}'s,
     * the ids {@code world.fragments} version 1 stores.
     */
    private static FragmentAnatomy humanoidTable() {
        float hipY = 0.86f;
        Builder b = builder(BodyFamily.HUMANOID)
                .joint("root", null, 0, 0, 0)
                .part("torso", "root", 0, hipY, 0, 0, 0.31f, 0, 0.46f, 0.62f, 0.26f)
                .joint("neck", "torso", 0, 0.58f, 0)
                // Built at 0.64 above the torso, then hung under the neck.
                .part("head", "neck", 0, 0.64f - 0.58f, 0, 0, 0.14f, 0, 0.26f, 0.26f, 0.26f);
        for (int s = -1; s <= 1; s += 2) {
            String side = s < 0 ? "_l" : "_r";
            b.part("arm" + side, "torso", s * 0.30f, 0.55f, 0, 0, -0.26f, 0, 0.13f, 0.55f, 0.15f)
                    .split("arm" + side, "forearm" + side, true);
        }
        for (int s = -1; s <= 1; s += 2) {
            String side = s < 0 ? "_l" : "_r";
            b.part("leg" + side, "root", s * 0.115f, hipY, 0, 0, -0.43f, 0, 0.16f, 0.86f, 0.18f)
                    .split("leg" + side, "shin" + side, true);
        }
        return b.piece("torso", "torso", "torso")
                .piece("head", "neck", "head")
                .piece("upper_arm_l", "arm_l", "arm_l")
                .piece("upper_arm_r", "arm_r", "arm_r")
                .piece("forearm_l", "forearm_l", "forearm_l")
                .piece("forearm_r", "forearm_r", "forearm_r")
                .piece("thigh_l", "leg_l", "leg_l")
                .piece("thigh_r", "leg_r", "leg_r")
                .piece("shin_l", "shin_l", "shin_l")
                .piece("shin_r", "shin_r", "shin_r")
                .build();
    }

    /**
     * {@code CreatureModels.quadruped}: body along Z, four legs and the neck
     * pivot hung from the model root, each leg split at the knee or hock.
     */
    private static Builder quadruped(BodyFamily family, float bodyLen, float bodyW, float bodyH,
                                     float legH, float legW) {
        float shoulderY = legH + bodyH * 0.5f;
        Builder b = builder(family)
                .joint("root", null, 0, 0, 0)
                .part("body", "root", 0, shoulderY, 0, 0, 0, 0, bodyW, bodyH, bodyLen);
        float hx = bodyW * 0.32f;
        float fz = -bodyLen * 0.38f;
        float bz = bodyLen * 0.38f;
        float[][] at = {{-hx, fz}, {hx, fz}, {-hx, bz}, {hx, bz}};
        for (int i = 0; i < 4; i++) {
            b.part(LEGS[i], "root", at[i][0], legH, at[i][1], 0, -legH * 0.5f, 0, legW, legH, legW)
                    .split(LEGS[i], LEGS[i] + "_lower", true);
        }
        return b.joint("neck", "root", 0, shoulderY + bodyH * 0.30f, -bodyLen * 0.48f);
    }

    /** Torso, head, four upper legs, four lower legs: the pieces every quadruped shares. */
    private static Builder quadrupedPieces(Builder b) {
        b.piece("torso", "body", "body").piece("head", "neck", "head");
        for (String leg : LEGS) {
            b.piece("upper_" + leg, leg, leg);
        }
        for (String leg : LEGS) {
            b.piece("lower_" + leg, leg + "_lower", leg + "_lower");
        }
        return b;
    }

    /** {@code CreatureModels.glowdeer}. The tail halves are too small to fly apart. */
    private static FragmentAnatomy deer() {
        Builder b = quadruped(BodyFamily.DEER, 0.85f, 0.42f, 0.42f, 0.58f, 0.11f)
                .part("head", "neck", 0, 0.16f, -0.06f, 0, 0.06f, -0.10f, 0.22f, 0.24f, 0.34f)
                .part("tail", "body", 0, 0.12f, 0.44f, 0, 0.02f, 0.04f, 0.08f, 0.08f, 0.12f)
                .split("tail", "tail_tip", false);
        return quadrupedPieces(b).piece("tail", "tail", "tail").build();
    }

    /** {@code CreatureModels.ashwolf}. The ears twitch, so they are joints of the head. */
    private static FragmentAnatomy wolf() {
        Builder b = quadruped(BodyFamily.WOLF, 0.80f, 0.34f, 0.34f, 0.42f, 0.10f)
                .part("head", "neck", 0, 0.06f, -0.05f, 0, 0.02f, -0.08f, 0.24f, 0.22f, 0.26f);
        for (int s = -1; s <= 1; s += 2) {
            b.part(s < 0 ? "ear_l" : "ear_r", "head", s * 0.08f, 0.14f, 0.02f, 0, 0.05f, 0, 0.06f, 0.11f, 0.04f);
        }
        b.part("tail", "body", 0, 0.10f, 0.42f, 0, 0, 0.16f, 0.10f, 0.10f, 0.34f)
                .split("tail", "tail_tip", false);
        return quadrupedPieces(b).piece("tail", "tail", "tail").piece("tail_tip", "tail_tip", "tail_tip").build();
    }

    /** {@code CreatureModels.thornhorn}. */
    private static FragmentAnatomy thornhorn() {
        Builder b = quadruped(BodyFamily.THORNHORN, 1.25f, 0.72f, 0.72f, 0.55f, 0.20f)
                .part("head", "neck", 0, -0.02f, -0.10f, 0, 0, -0.12f, 0.40f, 0.36f, 0.40f)
                .part("tail", "body", 0, 0.10f, 0.65f, 0, 0, 0.10f, 0.14f, 0.14f, 0.26f)
                .split("tail", "tail_tip", false);
        return quadrupedPieces(b).piece("tail", "tail", "tail").piece("tail_tip", "tail_tip", "tail_tip").build();
    }

    /** {@code CreatureModels.gloomstalker}. */
    private static FragmentAnatomy stalker() {
        Builder b = quadruped(BodyFamily.STALKER, 0.70f, 0.26f, 0.26f, 0.72f, 0.06f)
                .part("head", "neck", 0, 0.10f, -0.06f, 0, 0, -0.10f, 0.18f, 0.16f, 0.30f)
                .part("tail", "body", 0, 0.02f, 0.36f, 0, 0, 0.18f, 0.05f, 0.05f, 0.40f)
                .split("tail", "tail_tip", false);
        return quadrupedPieces(b).piece("tail", "tail", "tail").piece("tail_tip", "tail_tip", "tail_tip").build();
    }

    /**
     * {@code CreatureModels.murkhare}: the head hangs from the box-less
     * {@code head_joint}, and every split half is too small to fly alone, so
     * each leg and the tail come off whole.
     */
    private static FragmentAnatomy hare() {
        Builder b = builder(BodyFamily.HARE)
                .joint("root", null, 0, 0, 0)
                .part("body", "root", 0, 0.16f, 0, 0, 0.02f, 0.02f, 0.22f, 0.20f, 0.34f)
                .joint("head_joint", "body", 0, 0.07f, -0.18f)
                .part("head", "head_joint", 0, 0.03f, 0, 0, 0.02f, -0.04f, 0.15f, 0.15f, 0.16f);
        for (int s = -1; s <= 1; s += 2) {
            b.part(s < 0 ? "ear_l" : "ear_r", "head", s * 0.045f, 0.12f, 0.02f, 0, 0.10f, 0, 0.045f, 0.22f, 0.03f);
        }
        float[][] at = {{-0.07f, -0.10f}, {0.07f, -0.10f}, {-0.09f, 0.14f}, {0.09f, 0.14f}};
        for (int i = 0; i < 4; i++) {
            b.part(LEGS[i], "root", at[i][0], 0.12f, at[i][1], 0, -0.06f, 0, 0.05f, 0.12f, 0.05f)
                    .split(LEGS[i], LEGS[i] + "_lower", true);
        }
        b.part("tail", "body", 0, 0.06f, 0.22f, 0, 0, 0.02f, 0.07f, 0.07f, 0.06f)
                .split("tail", "tail_tip", false);
        b.piece("torso", "body", "body").piece("head", "head_joint", "head");
        for (String leg : LEGS) {
            b.piece(leg, leg, leg);
        }
        return b.piece("tail", "tail", "tail").build();
    }

    /**
     * {@code CreatureModels.skitterwing}: no neck and no legs. Each wing is a
     * thin board offset sideways from its root pivot and is its own piece.
     */
    private static FragmentAnatomy bird() {
        Builder b = builder(BodyFamily.BIRD)
                .joint("root", null, 0, 0, 0)
                .part("body", "root", 0, 0.18f, 0, 0, 0, 0, 0.16f, 0.14f, 0.30f)
                .part("head", "body", 0, 0.05f, -0.17f, 0, 0.01f, -0.03f, 0.11f, 0.10f, 0.12f);
        for (int pair = 0; pair < 2; pair++) {
            float z = -0.06f + pair * 0.14f;
            for (int s = -1; s <= 1; s += 2) {
                b.part("wing" + pair + (s < 0 ? "_l" : "_r"), "body", s * 0.08f, 0.05f, z,
                        s * 0.16f, 0, 0, 0.30f, 0.02f, 0.10f);
            }
        }
        b.part("tail", "body", 0, 0, 0.17f, 0, 0, 0.05f, 0.06f, 0.03f, 0.12f);
        return b.piece("torso", "body", "body")
                .piece("head", "head", "head")
                .piece("tail", "tail", "tail")
                .piece("wing0_l", "wing0_l", "wing0_l")
                .piece("wing0_r", "wing0_r", "wing0_r")
                .piece("wing1_l", "wing1_l", "wing1_l")
                .piece("wing1_r", "wing1_r", "wing1_r")
                .build();
    }
}
