package com.veylon.gfx;

import com.veylon.gfx.model.FlameAnchors;
import com.veylon.gfx.model.ModelPart;
import org.joml.Matrix4f;

/**
 * The flames standing on burning bodies this frame: one flame tongue, and
 * close up a hotter core inside it, on each alight anchor of each burning body
 * drawn, written as instances for {@link ParticleRenderer}'s additive pass.
 *
 * <p>Tongues are rebuilt every frame from the pose the body is drawn in, so
 * they move with the limbs they stand on; each keeps its own flicker number
 * (the anchor's, mixed with the body's fire), so it flickers smoothly rather
 * than jumping from frame to frame, and no two tongues flicker in step.
 *
 * <p>Presentation only and bounded: at most {@link #MAX_INSTANCES} tongues a
 * frame. {@link #begin} shares them out evenly over the burning bodies in range
 * rather than letting the first ones drawn take them all, detail falls with
 * distance ({@link #FULL_DETAIL}, {@link #HALF_DETAIL}, {@link #MAX_DISTANCE}),
 * and nothing here reads or writes the simulation. No allocation per frame.
 */
public final class BodyFlames {

    /** Most tongues drawn in one frame, over every body. */
    public static final int MAX_INSTANCES = 1024;
    /** Most tongues one body carries: a core and an outer tongue per anchor. */
    public static final int MAX_PER_BODY = 2 * FlameAnchors.MAX_ANCHORS + 1;
    /** Least tongues a body keeps however crowded the frame is, so no burning body looks unburned. */
    public static final int MIN_PER_BODY = 3;
    /** Within this distance a body shows every anchor with its core; beyond, outer tongues only. */
    public static final float FULL_DETAIL = 32f;
    /** Beyond this, a quarter of the anchors rather than half. */
    public static final float HALF_DETAIL = 64f;
    /** No attached flames past this distance; the body burns on regardless. */
    public static final float MAX_DISTANCE = 96f;
    /** Particle density at which tongues are thinned, never below half: they are how a body shows it burns. */
    public static final float MIN_DENSITY_SHARE = 0.5f;
    /** A piece of a body with at least this share of it glows like a whole body. */
    public static final float GLOW_SHARE = 0.3f;
    /** A tongue leans this much per unit height per m/s of wind across the body, at most {@link #MAX_LEAN}. */
    public static final float LEAN_PER_SPEED = 0.11f, MAX_LEAN = 0.7f;

    private static final int F = ParticleRenderer.INSTANCE_FLOATS;
    /** Particle sprite id of a flame tongue (see particle.vert). */
    public static final float SPRITE_FLAME = 4f;
    /** Particle sprite id of the soft glow round a burning body (the additive spark). */
    public static final float SPRITE_GLOW = 3f;
    /** HDR brightness of the outer tongues and the cores: flames are light, not paint. */
    private static final float OUTER_GAIN = 1.2f, CORE_GAIN = 1.5f;
    /** The glow is at most this wide, metres, and this strong at full heat. */
    private static final float GLOW_MAX_SIZE = 3f, GLOW_ALPHA = 0.09f;

    public final float[] data = new float[MAX_INSTANCES * F];
    /** Tongues written this frame. */
    public int count;
    /** Bodies (or pieces) that drew at least one tongue this frame. */
    public int bodies;
    private int budgetPerBody = MAX_PER_BODY;
    private float density = 1f;
    private float glow = 1f;
    private final float[] sampled = new float[FlameAnchors.MAX_ANCHORS * 4];

    /**
     * Starts a frame. {@code bodiesInRange} counts the burning bodies within
     * {@link #MAX_DISTANCE} (a piece of a body counts as its share); each
     * gets an even share of {@link #MAX_INSTANCES}, never more than
     * {@link #MAX_PER_BODY} nor fewer than {@link #MIN_PER_BODY}. {@code glowStrength}
     * 0..1 scales the soft glow round each fire: full in the dark, faint by day.
     */
    public void begin(float bodiesInRange, float particleDensity, float glowStrength) {
        count = 0;
        bodies = 0;
        glow = Math.max(0f, Math.min(1f, glowStrength));
        density = Math.max(MIN_DENSITY_SHARE, Math.min(1f, particleDensity));
        budgetPerBody = bodiesInRange <= 1f ? MAX_PER_BODY
                : Math.max(MIN_PER_BODY, Math.min(MAX_PER_BODY, (int) (MAX_INSTANCES / bodiesInRange)));
    }

    /** The most tongues one body may add this frame. */
    public int budgetPerBody() {
        return budgetPerBody;
    }

    /**
     * Adds the flames of one burning body, posed from {@code start} in
     * {@code frame} (see {@link FlameAnchors#sample}), as they look now.
     *
     * @param share the body's share of its fire: 1 for a whole body, a piece's
     *              {@code BodyFragment.burnShare} for a piece of one, so the
     *              pieces of a body together have one body's flames
     * @param distance metres from the camera
     * @param airX the air's velocity past the body, m/s — the wind less the body's own
     *             velocity — which the flames lean with, so a running body trails them
     * @return tongues added
     */
    public int add(FlameAnchors anchors, ModelPart start, Matrix4f frame, BodyFireLook look,
                   int pieceSeed, float share, float distance, float airX, float airZ) {
        if (look.flame <= 0f || look.coverage <= 0f || distance > MAX_DISTANCE || count >= MAX_INSTANCES) {
            return 0;
        }
        int n = anchors.sample(start, frame, sampled);
        if (n == 0) {
            return 0;
        }
        // A piece of a body has its share of the body's flames: a small share in a
        // crowded frame may be too little for a core, or for any, and then the
        // body's bigger pieces carry its fire. Only a big share has a glow.
        int budget = Math.min(MAX_INSTANCES - count,
                share >= 1f ? budgetPerBody : (int) (budgetPerBody * share));
        if (budget <= 0) {
            return 0;
        }
        boolean cores = distance <= FULL_DETAIL && budget >= 2;
        float detail = distance <= FULL_DETAIL ? 1f : distance <= HALF_DETAIL ? 0.5f : 0.25f;
        int layers = cores ? 2 : 1;
        boolean glows = share >= GLOW_SHARE && budget > layers;
        // Lowest anchors in the body's order burn first; keep only as many as the budget holds,
        // so a crowded frame thins every body evenly over its whole surface.
        float byBudget = (float) budget / (layers * n);
        float threshold = Math.min(look.coverage * detail * density, byBudget);
        float bodyShift = shift(look, pieceSeed);
        float leanX = clamp(airX * LEAN_PER_SPEED, -MAX_LEAN, MAX_LEAN);
        float leanZ = clamp(airZ * LEAN_PER_SPEED, -MAX_LEAN, MAX_LEAN);
        float heat = look.flame;
        float grow = (0.45f + 0.55f * heat) * (1f + 0.45f * look.flare);
        int added = 0;
        int first = count;
        float cx = 0f, cy = 0f, cz = 0f;
        // One glow is kept back for the body's own heat haze.
        int tongueBudget = budget - (glows ? 1 : 0);
        for (int i = 0; i < n && added + layers <= tongueBudget; i++) {
            int a = (int) sampled[i * 4];
            float x = sampled[i * 4 + 1], y = sampled[i * 4 + 2], z = sampled[i * 4 + 3];
            if (!alight(anchors, a, x, y, z, look, bodyShift, threshold)) {
                continue;
            }
            float w = anchors.width(a) * grow;
            float seed = fract(anchors.hash(a) + bodyShift * 0.37f) * 64f;
            // The outer tongue: orange cooling to red at its edges, bright enough to read in daylight.
            put(x, y, z, w, OUTER_GAIN, OUTER_GAIN * (0.36f + 0.16f * heat), OUTER_GAIN * (0.06f + 0.04f * heat),
                    0.8f + 0.2f * heat, anchors.stretch(a), leanX, seed, leanZ);
            added++;
            if (cores) {
                // The hot core, lower and narrower inside it.
                put(x, y, z, w * 0.52f, CORE_GAIN, CORE_GAIN * (0.72f + 0.12f * heat),
                        CORE_GAIN * (0.30f + 0.12f * heat), 0.9f + 0.1f * heat,
                        anchors.stretch(a) * 0.75f, leanX, seed + 17.3f, leanZ);
                added++;
            }
            cx += x;
            cy += y;
            cz += z;
        }
        int tongues = cores ? added / 2 : added;
        if (glows && tongues > 0 && count < MAX_INSTANCES) {
            // A soft glow round the flames, as wide as they spread over the body: the heat and
            // light of the whole fire, which blooms at night.
            cx /= tongues;
            cy /= tongues;
            cz /= tongues;
            float reach = 0f;
            for (int i = first; i < count; i++) {
                float dx = data[i * F] - cx, dy = data[i * F + 1] - cy, dz = data[i * F + 2] - cz;
                reach = Math.max(reach, dx * dx + dy * dy + dz * dz);
            }
            float size = Math.min(GLOW_MAX_SIZE, (2f * (float) Math.sqrt(reach) + anchors.largestWidth())
                    * (0.7f + 0.3f * heat));
            put(cx, cy + size * 0.15f, cz, size, 1f, 0.42f, 0.1f,
                    GLOW_ALPHA * heat * glow, 1f, 0f, 0f, 0f);
            data[(count - 1) * F + 8] = SPRITE_GLOW;
            added++;
        }
        if (added > 0) {
            bodies++;
        }
        return added;
    }

    /**
     * A few small flames around the grip of the item the burning player holds,
     * in camera space (the frame the held item is drawn in). Returns how many
     * were written into {@code out}, at most {@code out.length / INSTANCE_FLOATS}.
     */
    public static int grip(Matrix4f heldFrame, BodyFireLook look, float[] out) {
        int max = out.length / F;
        if (look.flame <= 0f || max == 0) {
            return 0;
        }
        int tongues = Math.min(max, 2 + Math.round(3f * look.flame));
        org.joml.Vector3f p = GRIP_POINT;
        int n = 0;
        for (int i = 0; i < tongues; i++) {
            float t = (i + 0.5f) / tongues;
            // Along the first 0.2 m of the item from the grip, a little either side of it.
            heldFrame.transformPosition((i % 2 == 0 ? -0.035f : 0.035f), -0.02f, -t * 0.2f, p);
            float w = (0.035f + 0.03f * look.flame) * (1f + 0.3f * look.flare);
            int o = n * F;
            out[o] = p.x;
            out[o + 1] = p.y;
            out[o + 2] = p.z;
            out[o + 3] = w;
            out[o + 4] = 1f;
            out[o + 5] = 0.45f + 0.25f * look.flame;
            out[o + 6] = 0.10f + 0.10f * look.flame;
            out[o + 7] = 0.7f;
            out[o + 8] = SPRITE_FLAME;
            out[o + 9] = 2.1f;
            out[o + 10] = 0f;
            out[o + 11] = 5.7f + i * 9.1f;
            out[o + 12] = 0f;
            n++;
        }
        return n;
    }

    private static final org.joml.Vector3f GRIP_POINT = new org.joml.Vector3f();

    /**
     * Where in a body's anchor order its fire starts: fixed by the fire's
     * number and the piece, so different bodies — and a body's pieces — catch
     * on different anchors, and the same fire always on the same ones.
     */
    public static float shift(BodyFireLook look, int pieceSeed) {
        return fract((look.seed ^ pieceSeed) * 2.3283064e-10f + 0.5f);
    }

    /**
     * Whether anchor {@code a}, standing at {@code (x, y, z)} now, is alight:
     * it comes before {@code threshold} (at most the look's coverage) in the
     * body's order, and the flames have climbed that far from where they
     * touched the body. Flames are drawn and given off only where this holds.
     */
    public static boolean alight(FlameAnchors anchors, int a, float x, float y, float z,
                                 BodyFireLook look, float shift, float threshold) {
        if (fract(anchors.order(a) + shift) >= Math.min(threshold, look.coverage)) {
            return false;
        }
        if (look.spreading) {
            float dx = x - look.touchX, dy = y - look.touchY, dz = z - look.touchZ;
            return dx * dx + dy * dy + dz * dz <= look.spread * look.spread;
        }
        return true;
    }

    private void put(float x, float y, float z, float size, float r, float g, float b, float alpha,
                     float stretch, float leanX, float seed, float leanZ) {
        int o = count * F;
        data[o] = x;
        data[o + 1] = y;
        data[o + 2] = z;
        data[o + 3] = size;
        data[o + 4] = r;
        data[o + 5] = g;
        data[o + 6] = b;
        data[o + 7] = Math.min(1f, alpha);
        data[o + 8] = SPRITE_FLAME;
        data[o + 9] = stretch;
        data[o + 10] = leanX;
        data[o + 11] = seed;
        data[o + 12] = leanZ;
        count++;
    }

    private static float fract(float v) {
        return v - (float) Math.floor(v);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
