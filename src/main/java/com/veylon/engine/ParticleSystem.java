package com.veylon.engine;

import com.veylon.world.BlockType;

import java.util.Random;

/**
 * CPU particle simulation drawn by the instanced ParticleRenderer.
 * kind selects the procedural sprite + blend: 0 soft puff, 1 hard dot,
 * 2 vertical streak (rain), 3 additive spark (embers, beacon motes).
 */
public class ParticleSystem {

    public static final int MAX = 4000;
    public static final byte KIND_PUFF = 0;
    public static final byte KIND_DOT = 1;
    public static final byte KIND_STREAK = 2;
    public static final byte KIND_SPARK = 3;

    public final float[] px = new float[MAX];
    public final float[] py = new float[MAX];
    public final float[] pz = new float[MAX];
    private final float[] vx = new float[MAX];
    private final float[] vy = new float[MAX];
    private final float[] vz = new float[MAX];
    public final float[] cr = new float[MAX];
    public final float[] cg = new float[MAX];
    public final float[] cb = new float[MAX];
    public final float[] size = new float[MAX];
    public final byte[] kind = new byte[MAX];
    private final float[] life = new float[MAX];
    private final float[] maxLife = new float[MAX];
    private final float[] grav = new float[MAX];
    public int count;

    /** 0..1 multiplier applied to emission counts (graphics setting). */
    public float density = 1f;

    private final Random rng = new Random();

    /** Dev-scene hook; normal gameplay intentionally keeps organically varying particles. */
    public void setRandomSeed(long seed) {
        rng.setSeed(seed);
    }

    public void spawn(byte particleKind, float x, float y, float z,
                      float velX, float velY, float velZ,
                      float r, float g, float b, float sz, float lifetime, float gravity) {
        if (count >= MAX) {
            return;
        }
        int i = count++;
        px[i] = x;
        py[i] = y;
        pz[i] = z;
        vx[i] = velX;
        vy[i] = velY;
        vz[i] = velZ;
        cr[i] = r;
        cg[i] = g;
        cb[i] = b;
        size[i] = sz;
        kind[i] = particleKind;
        life[i] = lifetime;
        maxLife[i] = lifetime;
        grav[i] = gravity;
    }

    public void update(float dt) {
        for (int i = 0; i < count; ) {
            life[i] -= dt;
            if (life[i] <= 0) {
                int last = --count;
                px[i] = px[last];
                py[i] = py[last];
                pz[i] = pz[last];
                vx[i] = vx[last];
                vy[i] = vy[last];
                vz[i] = vz[last];
                cr[i] = cr[last];
                cg[i] = cg[last];
                cb[i] = cb[last];
                size[i] = size[last];
                kind[i] = kind[last];
                life[i] = life[last];
                maxLife[i] = maxLife[last];
                grav[i] = grav[last];
                continue;
            }
            vy[i] -= grav[i] * dt;
            px[i] += vx[i] * dt;
            py[i] += vy[i] * dt;
            pz[i] += vz[i] * dt;
            i++;
        }
    }

    /** 0..1 fade factor near end of life. */
    public float fade(int i) {
        return Math.min(1f, life[i] / Math.max(0.01f, maxLife[i] * 0.35f));
    }

    private float rnd(float spread) {
        return (rng.nextFloat() * 2f - 1f) * spread;
    }

    private int scaled(int n) {
        if (density >= 1f) {
            return n;
        }
        int k = (int) (n * density);
        if (k == 0 && rng.nextFloat() < n * density) {
            k = 1;
        }
        return k;
    }

    // ------------------------------------------------------------------
    // Emitters
    // ------------------------------------------------------------------

    public void blockDust(BlockType t, float x, float y, float z, int n) {
        var m = com.veylon.gfx.MaterialRegistry.of(t);
        int[] tile = com.veylon.gfx.MaterialRegistry.layerPixels(m.sideLayer[0]);
        for (int i = 0; i < scaled(n); i++) {
            // Sample a random texel so debris matches the block's actual texture.
            int p = tile[rng.nextInt(tile.length)];
            float shade = 0.75f + rng.nextFloat() * 0.4f;
            float r = ((p >> 16) & 0xFF) / 255f * shade;
            float g = ((p >> 8) & 0xFF) / 255f * shade;
            float b = (p & 0xFF) / 255f * shade;
            spawn(KIND_DOT, x + rnd(0.4f), y + rnd(0.4f), z + rnd(0.4f),
                    rnd(2.2f), 1.5f + rng.nextFloat() * 2f, rnd(2.2f),
                    r, g, b, 0.07f + rng.nextFloat() * 0.06f, 0.5f + rng.nextFloat() * 0.5f, 14f);
        }
    }

    public void smoke(float x, float y, float z, float strength) {
        if (scaled(1) < 1) {
            return;
        }
        float s = 0.30f + rng.nextFloat() * 0.25f;
        spawn(KIND_PUFF, x + rnd(0.25f), y, z + rnd(0.25f),
                rnd(0.25f), 0.7f + rng.nextFloat() * 0.6f * strength, rnd(0.25f),
                s, s, s + 0.02f,
                0.30f + rng.nextFloat() * 0.35f, 1.8f + rng.nextFloat() * 1.8f, -0.4f);
    }

    public void ember(float x, float y, float z) {
        if (scaled(1) < 1) {
            return;
        }
        spawn(KIND_SPARK, x + rnd(0.3f), y + 0.2f, z + rnd(0.3f),
                rnd(0.5f), 1.2f + rng.nextFloat() * 1.4f, rnd(0.5f),
                1f, 0.45f + rng.nextFloat() * 0.3f, 0.1f,
                0.09f, 0.7f + rng.nextFloat() * 0.6f, 1.5f);
    }

    /** Fire tongues rising from burning blocks (additive). */
    public void flame(float x, float y, float z) {
        if (scaled(1) < 1) {
            return;
        }
        spawn(KIND_SPARK, x + rnd(0.3f), y, z + rnd(0.3f),
                rnd(0.2f), 0.9f + rng.nextFloat() * 0.8f, rnd(0.2f),
                1f, 0.55f + rng.nextFloat() * 0.25f, 0.12f,
                0.22f + rng.nextFloat() * 0.15f, 0.35f + rng.nextFloat() * 0.3f, -1.2f);
    }

    public void rainSplash(float x, float y, float z) {
        for (int i = 0; i < scaled(2); i++) {
            spawn(KIND_DOT, x + rnd(0.2f), y + 0.05f, z + rnd(0.2f),
                    rnd(0.9f), 1.1f + rng.nextFloat(), rnd(0.9f),
                    0.6f, 0.7f, 0.9f, 0.04f, 0.25f, 16f);
        }
    }

    public void snowflake(float x, float y, float z) {
        if (scaled(1) < 1) {
            return;
        }
        spawn(KIND_DOT, x, y, z, rnd(0.4f), -0.9f - rng.nextFloat() * 0.5f, rnd(0.4f),
                0.95f, 0.96f, 1f, 0.06f + rng.nextFloat() * 0.04f,
                3.5f + rng.nextFloat() * 2f, 0f);
    }

    public void rainDrop(float x, float y, float z) {
        if (scaled(1) < 1) {
            return;
        }
        spawn(KIND_STREAK, x, y, z, 0, -11f, 0, 0.60f, 0.68f, 0.9f, 0.05f, 1.0f, 0f);
    }

    public void ashFlake(float x, float y, float z) {
        if (scaled(1) < 1) {
            return;
        }
        spawn(KIND_DOT, x, y, z, rnd(0.3f), -0.6f - rng.nextFloat() * 0.3f, rnd(0.3f),
                0.45f, 0.44f, 0.42f, 0.06f, 4f + rng.nextFloat() * 2f, 0f);
    }

    public void breath(float x, float y, float z, float dirX, float dirZ) {
        for (int i = 0; i < scaled(3); i++) {
            spawn(KIND_PUFF, x + dirX * 0.3f, y + rnd(0.05f), z + dirZ * 0.3f,
                    dirX * 0.5f + rnd(0.15f), 0.25f + rng.nextFloat() * 0.2f, dirZ * 0.5f + rnd(0.15f),
                    0.9f, 0.92f, 0.96f, 0.09f + rng.nextFloat() * 0.05f,
                    0.8f + rng.nextFloat() * 0.4f, -0.1f);
        }
    }

    public void blood(float x, float y, float z) {
        for (int i = 0; i < scaled(6); i++) {
            spawn(KIND_DOT, x + rnd(0.2f), y + rnd(0.2f), z + rnd(0.2f),
                    rnd(1.8f), 1f + rng.nextFloat() * 1.5f, rnd(1.8f),
                    0.48f + rng.nextFloat() * 0.14f, 0.06f, 0.06f,
                    0.05f + rng.nextFloat() * 0.04f, 0.5f, 13f);
        }
    }

    public void splash(float x, float y, float z) {
        for (int i = 0; i < scaled(8); i++) {
            spawn(KIND_DOT, x + rnd(0.3f), y, z + rnd(0.3f),
                    rnd(1.6f), 1.8f + rng.nextFloat() * 1.6f, rnd(1.6f),
                    0.45f, 0.6f, 0.85f, 0.06f, 0.5f, 12f);
        }
    }

    /** Toxic fog motes drifting at head height. */
    public void toxicMote(float x, float y, float z) {
        if (scaled(1) < 1) {
            return;
        }
        spawn(KIND_PUFF, x, y, z, rnd(0.25f), 0.05f + rnd(0.1f), rnd(0.25f),
                0.62f, 0.75f, 0.22f, 0.12f + rng.nextFloat() * 0.10f,
                2.5f + rng.nextFloat() * 2f, 0f);
    }

    /** Cyan energy motes rising around the active beacon. */
    public void beaconMote(float x, float y, float z) {
        if (scaled(1) < 1) {
            return;
        }
        spawn(KIND_SPARK, x + rnd(0.6f), y, z + rnd(0.6f),
                rnd(0.15f), 1.4f + rng.nextFloat() * 1.2f, rnd(0.15f),
                0.35f, 0.9f, 0.95f, 0.10f + rng.nextFloat() * 0.06f,
                1.2f + rng.nextFloat() * 0.8f, -0.2f);
    }

    /** Short, bounded impact burst for meteor events: hot fragments plus crater dust. */
    public void meteorImpact(float x, float y, float z) {
        int sparks = scaled(28);
        for (int i = 0; i < sparks; i++) {
            float angle = rng.nextFloat() * (float) (Math.PI * 2.0);
            float speed = 2.5f + rng.nextFloat() * 5.5f;
            spawn(KIND_SPARK, x + rnd(0.5f), y + 0.4f + rnd(0.25f), z + rnd(0.5f),
                    (float) Math.cos(angle) * speed, 3.5f + rng.nextFloat() * 5f,
                    (float) Math.sin(angle) * speed,
                    1f, 0.38f + rng.nextFloat() * 0.35f, 0.08f,
                    0.08f + rng.nextFloat() * 0.08f, 1.0f + rng.nextFloat() * 1.1f, 10f);
        }
        int dust = scaled(16);
        for (int i = 0; i < dust; i++) {
            float shade = 0.24f + rng.nextFloat() * 0.18f;
            spawn(KIND_PUFF, x + rnd(1.2f), y + 0.25f + rnd(0.2f), z + rnd(1.2f),
                    rnd(1.6f), 0.8f + rng.nextFloat() * 1.8f, rnd(1.6f),
                    shade, shade * 0.92f, shade * 0.82f,
                    0.35f + rng.nextFloat() * 0.45f, 1.8f + rng.nextFloat() * 1.7f, -0.25f);
        }
    }
}
