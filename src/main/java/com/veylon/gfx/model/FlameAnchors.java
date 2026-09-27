package com.veylon.gfx.model;

import com.veylon.entity.BodyFamily;
import com.veylon.entity.FragmentAnatomy;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where flames stand on a burning body: a fixed set of points on the real
 * boxes of one family's model, spread by surface area, and a way to find them
 * on the body as it is posed right now.
 *
 * <p>The boxes of the family's own anatomy (its {@code FragmentAnatomy} joint
 * table: torso, head, limbs, a tail, a bird's four wings) share between
 * {@link #MIN_ANCHORS} and {@link #MAX_CORE_ANCHORS} anchors by surface area;
 * what the body wears or grows beyond that (a vest, a pack, a ruff, plates,
 * horns) gets one or two each, at most {@link #MAX_EXTRA_ANCHORS}, so the
 * humanoid's superset kit, most of it hidden on any one person, never thins
 * the flames on the body. Tiny boxes and glowing ones get none. On a box the
 * anchors go more to large faces and faces that look up, fewer underneath; a
 * big box gets more, never a fire cube. Each anchor also carries the width of
 * the flame it holds, from the face it stands on, so a bird wears a few small
 * tongues and a thornhorn many broad ones.
 *
 * <p>{@link #sample} walks the posed part tree the way {@link ModelPart#render}
 * draws it — the same pivots, poses, rotation order and scale, the same
 * visibility — and writes the world position of every anchor on a visible
 * part. A part hidden by a person's look or by a piece's isolation has none,
 * so a piece of a body blown apart holds exactly its own anchors and the
 * pieces together hold the body's. Built once per family; sampling reuses
 * fixed arrays and allocates nothing. CPU only, main thread only.
 */
public final class FlameAnchors {

    /** Most anchors on a body's own anatomy, whatever its size. */
    public static final int MAX_CORE_ANCHORS = 32;
    /** Most anchors on what a body wears or grows beyond its anatomy (a vest, a pack, a ruff, plates). */
    public static final int MAX_EXTRA_ANCHORS = 24;
    /** Most anchors one body carries in all. */
    public static final int MAX_ANCHORS = MAX_CORE_ANCHORS + MAX_EXTRA_ANCHORS;
    /** Something worn with at least this much surface holds two anchors rather than one. */
    public static final float LARGE_EXTRA_AREA = 0.8f;
    /** Fewest anchors on a body's own anatomy. */
    public static final int MIN_ANCHORS = 4;
    /** Anchors per square metre of eligible box surface, before the bounds. */
    public static final float ANCHORS_PER_SQUARE_METRE = 10f;
    /** A box with less surface than this carries no flame of its own (eyes, beaks, tines). */
    public static final float MIN_PART_AREA = 0.012f;
    /** A box with at least this much surface is sure of one anchor before the rest are shared. */
    public static final float SIGNIFICANT_PART_AREA = 0.05f;
    /** Flame width per unit of the square root of the part's largest face. */
    public static final float WIDTH_PER_FACE = 0.75f;
    /** Narrowest and widest flame an anchor holds, metres. */
    public static final float MIN_WIDTH = 0.09f, MAX_WIDTH = 0.48f;
    /** Weight of a box's up-facing, side and down-facing faces when anchors are placed. */
    private static final float TOP_WEIGHT = 1.3f, SIDE_WEIGHT = 1f, BOTTOM_WEIGHT = 0.35f;
    /** Anchors keep off the very edges of a face. */
    private static final float FACE_MARGIN = 0.12f;
    private static final float GOLDEN = 0.618034f;

    private static final Map<BodyFamily, FlameAnchors> CACHE = new EnumMap<>(BodyFamily.class);

    public final BodyFamily family;
    /** Anchors on the whole body. */
    public final int count;
    /** Surface of the boxes that carry anchors, square metres. */
    public final float area;

    /** Every part of the model, depth first; anchors are grouped by part in this order. */
    private final ModelPart[] parts;
    private final int[] depth;
    /** Index after the last part of each part's subtree. */
    private final int[] end;
    private final int[] first;
    private final int[] held;
    private final float[] px, py, pz;
    private final float[] width, stretch, order, hash;
    private final boolean[] top;
    private final Matrix4f[] stack;
    private final Vector3f point = new Vector3f();

    /** The anchors of a family's shared model (the model the renderer draws it with). */
    public static FlameAnchors of(BodyFamily family) {
        FlameAnchors anchors = CACHE.get(family);
        if (anchors == null) {
            anchors = new FlameAnchors(family, AnatomyModels.modelOf(family));
            CACHE.put(family, anchors);
        }
        return anchors;
    }

    /** Anchors for {@code model}; they are only valid on that model instance's parts. */
    public FlameAnchors(BodyFamily family, EntityModel model) {
        this.family = family;
        List<ModelPart> list = new ArrayList<>();
        List<Integer> depths = new ArrayList<>();
        collect(model.root, 0, list, depths);
        int n = list.size();
        parts = list.toArray(new ModelPart[0]);
        depth = new int[n];
        end = new int[n];
        int maxDepth = 0;
        for (int i = 0; i < n; i++) {
            depth[i] = depths.get(i);
            maxDepth = Math.max(maxDepth, depth[i]);
        }
        for (int i = 0; i < n; i++) {
            int j = i + 1;
            while (j < n && depth[j] > depth[i]) {
                j++;
            }
            end[i] = j;
        }
        stack = new Matrix4f[maxDepth + 2];
        for (int i = 0; i < stack.length; i++) {
            stack[i] = new Matrix4f();
        }

        // The anatomy's own boxes (its joint table) share the core anchors by
        // area; anything else a body wears — a vest, a pack, a ruff, plates —
        // gets an allowance of its own, so the superset humanoid's kit, most of
        // it hidden on any one person, never thins the flames on the body.
        Set<String> anatomy = new HashSet<>();
        FragmentAnatomy table = family.anatomy();
        for (int j = 0; j < table.jointCount(); j++) {
            if (table.joint(j).boxed) {
                anatomy.add(table.joint(j).name);
            }
        }
        float[] coreArea = new float[n];
        float[] extraArea = new float[n];
        float core = 0f, total = 0f;
        int significant = 0;
        for (int i = 0; i < n; i++) {
            float a = eligibleArea(parts[i]);
            total += a;
            if (anatomy.contains(parts[i].name)) {
                coreArea[i] = a;
                core += a;
                if (a >= SIGNIFICANT_PART_AREA) {
                    significant++;
                }
            } else {
                extraArea[i] = a;
            }
        }
        area = total;
        int wanted = core <= 0f ? 0 : Math.max(Math.round(core * ANCHORS_PER_SQUARE_METRE), significant);
        int coreCount = core <= 0f ? 0 : Math.min(MAX_CORE_ANCHORS, Math.max(MIN_ANCHORS, wanted));
        held = share(coreArea, coreCount);
        int extras = wear(extraArea, held);
        count = coreCount + extras;

        first = new int[n];
        px = new float[count];
        py = new float[count];
        pz = new float[count];
        width = new float[count];
        stretch = new float[count];
        order = new float[count];
        hash = new float[count];
        top = new boolean[count];
        int a = 0;
        for (int i = 0; i < n; i++) {
            first[i] = a;
            for (int k = 0; k < held[i]; k++, a++) {
                place(parts[i], i, k, held[i], a);
            }
        }
    }

    private static void collect(ModelPart p, int d, List<ModelPart> out, List<Integer> depths) {
        out.add(p);
        depths.add(d);
        for (ModelPart c : p.children) {
            collect(c, d + 1, out, depths);
        }
    }

    /** Surface a part offers the flames: its whole box, unless it is tiny or it glows. */
    private static float eligibleArea(ModelPart p) {
        if (p.sizeX <= 0f || p.emissive > 0f) {
            return 0f;
        }
        float a = 2f * (p.sizeX * p.sizeY + p.sizeX * p.sizeZ + p.sizeY * p.sizeZ);
        return a >= MIN_PART_AREA ? a : 0f;
    }

    /**
     * How many anchors each part holds: one for every significant part first,
     * largest first, then the rest one at a time to whichever part has the
     * most area per anchor already held (ties to the earlier part).
     */
    private static int[] share(float[] partArea, int total) {
        int n = partArea.length;
        int[] held = new int[n];
        int given = 0;
        boolean[] seeded = new boolean[n];
        while (given < total) {
            int best = -1;
            for (int i = 0; i < n; i++) {
                if (!seeded[i] && partArea[i] >= SIGNIFICANT_PART_AREA
                        && (best < 0 || partArea[i] > partArea[best])) {
                    best = i;
                }
            }
            if (best < 0) {
                break;
            }
            seeded[best] = true;
            held[best] = 1;
            given++;
        }
        while (given < total) {
            int best = -1;
            float bestShare = 0f;
            for (int i = 0; i < n; i++) {
                float s = partArea[i] / (held[i] + 1);
                if (partArea[i] > 0f && s > bestShare) {
                    best = i;
                    bestShare = s;
                }
            }
            held[best]++;
            given++;
        }
        return held;
    }

    /**
     * Adds the anchors of what the body wears to {@code held}: one for each
     * worn box with at least {@link #SIGNIFICANT_PART_AREA} of surface, two
     * for one past {@link #LARGE_EXTRA_AREA}, largest first, at most
     * {@link #MAX_EXTRA_ANCHORS}.
     *
     * @return anchors added
     */
    private static int wear(float[] extraArea, int[] held) {
        int added = 0;
        boolean[] done = new boolean[extraArea.length];
        while (added < MAX_EXTRA_ANCHORS) {
            int best = -1;
            for (int i = 0; i < extraArea.length; i++) {
                if (!done[i] && extraArea[i] >= SIGNIFICANT_PART_AREA
                        && (best < 0 || extraArea[i] > extraArea[best])) {
                    best = i;
                }
            }
            if (best < 0) {
                break;
            }
            done[best] = true;
            int k = Math.min(extraArea[best] >= LARGE_EXTRA_AREA ? 2 : 1, MAX_EXTRA_ANCHORS - added);
            held[best] += k;
            added += k;
        }
        return added;
    }

    /** Puts the {@code k}-th of a part's {@code of} anchors on one of its faces. */
    private void place(ModelPart p, int partIndex, int k, int of, int a) {
        float sx = p.sizeX, sy = p.sizeY, sz = p.sizeZ;
        // Face weights: +Y, -Y, +X, -X, +Z, -Z, by area and by which way they look.
        float[] w = {
                sx * sz * TOP_WEIGHT, sx * sz * BOTTOM_WEIGHT,
                sy * sz * SIDE_WEIGHT, sy * sz * SIDE_WEIGHT,
                sx * sy * SIDE_WEIGHT, sx * sy * SIDE_WEIGHT};
        // The k-th anchor goes to the face whose weight per anchor already placed is largest.
        int[] placed = new int[6];
        int face = 0;
        for (int j = 0; j <= k; j++) {
            face = 0;
            float best = -1f;
            for (int f = 0; f < 6; f++) {
                float s = w[(f + partIndex) % 6] / (placed[(f + partIndex) % 6] + 1);
                if (s > best) {
                    best = s;
                    face = (f + partIndex) % 6;
                }
            }
            placed[face]++;
        }
        // A low-discrepancy point on the face, offset per part so twin limbs differ.
        float u = FACE_MARGIN + (1f - 2f * FACE_MARGIN) * fract(0.5f + (k + 1) * GOLDEN + partIndex * 0.37f);
        float v = FACE_MARGIN + (1f - 2f * FACE_MARGIN) * fract(0.5f + (k + 1) * 0.754878f + partIndex * 0.21f);
        float x = p.boxX, y = p.boxY, z = p.boxZ;
        switch (face) {
            case 0 -> { y += sy * 0.5f; x += (u - 0.5f) * sx; z += (v - 0.5f) * sz; }
            case 1 -> { y -= sy * 0.5f; x += (u - 0.5f) * sx; z += (v - 0.5f) * sz; }
            case 2 -> { x += sx * 0.5f; y += (u - 0.5f) * sy; z += (v - 0.5f) * sz; }
            case 3 -> { x -= sx * 0.5f; y += (u - 0.5f) * sy; z += (v - 0.5f) * sz; }
            case 4 -> { z += sz * 0.5f; x += (u - 0.5f) * sx; y += (v - 0.5f) * sy; }
            default -> { z -= sz * 0.5f; x += (u - 0.5f) * sx; y += (v - 0.5f) * sy; }
        }
        px[a] = x;
        py[a] = y;
        pz[a] = z;
        float largestFace = Math.max(sx * sy, Math.max(sx * sz, sy * sz));
        width[a] = clamp(WIDTH_PER_FACE * (float) Math.sqrt(largestFace), MIN_WIDTH, MAX_WIDTH);
        hash[a] = fract((a + 1) * 0.7548777f + family.ordinal() * 0.5698403f + partIndex * 0.1234567f);
        top[a] = face == 0;
        stretch[a] = 1.8f + 1.0f * hash[a] + (top[a] ? 0.5f : 0f);
        order[a] = fract(0.13f + a * GOLDEN + family.ordinal() * 0.31f);
    }

    /**
     * Writes the world position of every anchor on a visible part of the
     * posed subtree under {@code start}, drawn in {@code parentFrame} (the
     * frame {@link ModelPart#render} is handed for {@code start}): four floats
     * per anchor — its index as a float, then x, y, z. Parts outside the tree
     * of this family's model, or a {@code start} not in it, give nothing.
     *
     * @param out room for {@code 4 * count} floats
     * @return anchors written
     */
    public int sample(ModelPart start, Matrix4f parentFrame, float[] out) {
        int s = indexOf(start);
        if (s < 0) {
            return 0;
        }
        int base = depth[s];
        stack[0].set(parentFrame);
        int written = 0;
        int i = s;
        while (i < end[s]) {
            ModelPart p = parts[i];
            if (!p.visible) {
                i = end[i];
                continue;
            }
            int d = depth[i] - base + 1;
            Matrix4f m = stack[d].set(stack[d - 1])
                    .translate(p.pivotX + p.poseX, p.pivotY + p.poseY, p.pivotZ + p.poseZ)
                    .rotateZ(p.rotZ).rotateY(p.rotY).rotateX(p.rotX);
            if (p.scale != 1f) {
                m.scale(p.scale);
            }
            for (int a = first[i], last = first[i] + held[i]; a < last; a++) {
                m.transformPosition(px[a], py[a], pz[a], point);
                int o = written * 4;
                out[o] = a;
                out[o + 1] = point.x;
                out[o + 2] = point.y;
                out[o + 3] = point.z;
                written++;
            }
            i++;
        }
        return written;
    }

    private int indexOf(ModelPart start) {
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] == start) {
                return i;
            }
        }
        return -1;
    }

    /** The part anchor {@code a} stands on. */
    public ModelPart part(int a) {
        for (int i = 0; i < parts.length; i++) {
            if (a >= first[i] && a < first[i] + held[i]) {
                return parts[i];
            }
        }
        throw new IndexOutOfBoundsException(a);
    }

    /** How many anchors {@code part} holds (0 for a part of another model). */
    public int anchorsOn(ModelPart part) {
        int i = indexOf(part);
        return i < 0 ? 0 : held[i];
    }

    /** The anchor's point in its part's own frame (box coordinates, before the box is drawn). */
    public float localX(int a) {
        return px[a];
    }

    public float localY(int a) {
        return py[a];
    }

    public float localZ(int a) {
        return pz[a];
    }

    /** Width of the flame the anchor holds at full strength, metres. */
    public float width(int a) {
        return width[a];
    }

    /** Height over width of the anchor's flame; taller on up-facing faces. */
    public float stretch(int a) {
        return stretch[a];
    }

    /**
     * Where the anchor comes in the order the body's anchors catch, in
     * [0, 1): at coverage c every anchor below c burns. Spread by the golden
     * ratio, so any share of the anchors is spread over the whole body.
     */
    public float order(int a) {
        return order[a];
    }

    /** A number of the anchor's own in [0, 1), for flicker. */
    public float hash(int a) {
        return hash[a];
    }

    /** Whether the anchor stands on a face that looks up in the part's frame. */
    public boolean onTop(int a) {
        return top[a];
    }

    /** The largest flame width on the body; a measure of its size. */
    public float largestWidth() {
        float w = 0f;
        for (int a = 0; a < count; a++) {
            w = Math.max(w, width[a]);
        }
        return w;
    }

    private static float fract(float v) {
        return v - (float) Math.floor(v);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
