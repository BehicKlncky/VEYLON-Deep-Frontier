package com.veylon.engine;

import com.veylon.world.BlockType;

import java.util.Random;

/**
 * CPU particle simulation rendered as small cubes by the Renderer: block
 * debris, fire smoke and embers, rain splashes, 3D snow, breath puffs, blood
 * and water splashes.
 */
public class ParticleSystem {

    public static final int MAX = 4000;

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
    private final float[] life = new float[MAX];
    private final float[] maxLife = new float[MAX];
    private final float[] grav = new float[MAX];
    public int count;

    private final Random rng = new Random();

    public void spawn(float x, float y, float z, float velX, float velY, float velZ,
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

    /** 0..1 fade factor (shrinks particles near end of life). */
    public float fade(int i) {
        return Math.min(1f, life[i] / Math.max(0.01f, maxLife[i] * 0.35f));
    }

    private float rnd(float spread) {
        return (rng.nextFloat() * 2f - 1f) * spread;
    }

    // ------------------------------------------------------------------
    // Emitters
    // ------------------------------------------------------------------

    public void blockDust(BlockType t, float x, float y, float z, int n) {
        for (int i = 0; i < n; i++) {
            float shade = 0.75f + rng.nextFloat() * 0.4f;
            spawn(x + rnd(0.4f), y + rnd(0.4f), z + rnd(0.4f),
                    rnd(2.2f), 1.5f + rng.nextFloat() * 2f, rnd(2.2f),
                    t.r * shade, t.g * shade, t.b * shade,
                    0.07f + rng.nextFloat() * 0.06f, 0.5f + rng.nextFloat() * 0.5f, 14f);
        }
    }

    public void smoke(float x, float y, float z, float strength) {
        float s = 0.35f + rng.nextFloat() * 0.3f;
        spawn(x + rnd(0.25f), y, z + rnd(0.25f),
                rnd(0.25f), 0.7f + rng.nextFloat() * 0.6f * strength, rnd(0.25f),
                s, s, s + 0.02f,
                0.12f + rng.nextFloat() * 0.14f, 1.6f + rng.nextFloat() * 1.6f, -0.4f);
    }

    public void ember(float x, float y, float z) {
        spawn(x + rnd(0.3f), y + 0.2f, z + rnd(0.3f),
                rnd(0.5f), 1.2f + rng.nextFloat() * 1.4f, rnd(0.5f),
                1f, 0.45f + rng.nextFloat() * 0.3f, 0.1f,
                0.045f, 0.7f + rng.nextFloat() * 0.6f, 1.5f);
    }

    public void rainSplash(float x, float y, float z) {
        for (int i = 0; i < 2; i++) {
            spawn(x + rnd(0.2f), y + 0.05f, z + rnd(0.2f),
                    rnd(0.9f), 1.1f + rng.nextFloat(), rnd(0.9f),
                    0.6f, 0.7f, 0.9f, 0.04f, 0.25f, 16f);
        }
    }

    public void snowflake(float x, float y, float z) {
        spawn(x, y, z, rnd(0.4f), -0.9f - rng.nextFloat() * 0.5f, rnd(0.4f),
                0.95f, 0.96f, 1f, 0.05f + rng.nextFloat() * 0.04f,
                3.5f + rng.nextFloat() * 2f, 0f);
    }

    public void rainDrop(float x, float y, float z) {
        spawn(x, y, z, 0, -11f, 0, 0.55f, 0.65f, 0.9f, 0.035f, 1.0f, 0f);
    }

    public void ashFlake(float x, float y, float z) {
        spawn(x, y, z, rnd(0.3f), -0.6f - rng.nextFloat() * 0.3f, rnd(0.3f),
                0.45f, 0.44f, 0.42f, 0.05f, 4f + rng.nextFloat() * 2f, 0f);
    }

    public void breath(float x, float y, float z, float dirX, float dirZ) {
        for (int i = 0; i < 3; i++) {
            spawn(x + dirX * 0.3f, y + rnd(0.05f), z + dirZ * 0.3f,
                    dirX * 0.5f + rnd(0.15f), 0.25f + rng.nextFloat() * 0.2f, dirZ * 0.5f + rnd(0.15f),
                    0.9f, 0.92f, 0.96f, 0.05f + rng.nextFloat() * 0.03f,
                    0.8f + rng.nextFloat() * 0.4f, -0.1f);
        }
    }

    public void blood(float x, float y, float z) {
        for (int i = 0; i < 6; i++) {
            spawn(x + rnd(0.2f), y + rnd(0.2f), z + rnd(0.2f),
                    rnd(1.8f), 1f + rng.nextFloat() * 1.5f, rnd(1.8f),
                    0.6f + rng.nextFloat() * 0.2f, 0.08f, 0.08f,
                    0.05f + rng.nextFloat() * 0.04f, 0.5f, 13f);
        }
    }

    public void splash(float x, float y, float z) {
        for (int i = 0; i < 8; i++) {
            spawn(x + rnd(0.3f), y, z + rnd(0.3f),
                    rnd(1.6f), 1.8f + rng.nextFloat() * 1.6f, rnd(1.6f),
                    0.35f, 0.5f, 0.8f, 0.06f, 0.5f, 12f);
        }
    }
}
