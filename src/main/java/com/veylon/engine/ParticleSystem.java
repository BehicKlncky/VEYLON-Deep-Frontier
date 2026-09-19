package com.veylon.engine;

import com.veylon.util.MathUtil;
import com.veylon.world.BlockType;
import com.veylon.world.World;

import java.util.Random;

/**
 * CPU particle simulation drawn by the instanced ParticleRenderer.
 * kind selects the procedural sprite + blend: 0 soft puff, 1 hard dot,
 * 2 velocity-aligned streak (rain), 3 additive spark (embers, beacon motes).
 */
public class ParticleSystem {

    public static final int MAX = 4000;
    public static final byte KIND_PUFF = 0;
    public static final byte KIND_DOT = 1;
    public static final byte KIND_STREAK = 2;
    public static final byte KIND_SPARK = 3;
    private static final float RAIN_WIND_RESPONSE = 1.7f;
    private static final float RAIN_FALL_RESPONSE = 2.5f;
    private static final float RAIN_TERMINAL_BASE = 15f;
    private static final float RAIN_TERMINAL_SIZE_SCALE = 100f;
    private static final float RAIN_FADE_IN = 0.12f;
    private static final float RAIN_VERTICAL_RANGE = 42f;
    private static final float IMPACT_OFFSET = 0.006f;
    private static final float SPLASH_GRAVITY = 12f;

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
    private final boolean[] splash = new boolean[MAX];
    private final RainCollision collision = new RainCollision();
    /** Weather leaves at least 1,200 slots for fire, combat and other effects. */
    public static final int RAIN_LIMIT = 2400, SPLASH_LIMIT = 2800;
    /**
     * Blood stops here, under the splash ceiling, so a fight during a storm
     * still leaves room for flame, smoke and explosion debris.
     */
    public static final int BLOOD_LIMIT = 3600;
    // Burning liquid from a fire bomb (the bottle's burst, pool flames, steam)
    // stops at SPLASH_LIMIT as well. Like rain's splashes it can never take the
    // 1,200 slots above that ceiling that blood and blast debris are promised,
    // and because rain stops at RAIN_LIMIT, the heaviest storm still leaves a
    // pool the 400 slots between the two. The rain a big pool could crowd out
    // is the rain that puts every pool open to the sky out within a second.
    /** The most glass shards, burning droplets and flash sparks one shattering bottle emits. */
    public static final int SHATTER_GLASS = 10, SHATTER_DROPS = 22, SHATTER_FLASH = 3;
    /** Puffs in one breath of steam from a doused pool. */
    public static final int STEAM_PUFFS = 3;
    private static final float GLASS_GRAVITY = 18f;
    private static final float DROPLET_GRAVITY = 14f;
    /** Burning liquid at its hottest (fresh, centre of a spill) and at its coolest. */
    private static final float LIQUID_HOT_G = 0.85f, LIQUID_HOT_B = 0.30f;
    private static final float LIQUID_COOL_G = 0.45f, LIQUID_COOL_B = 0.06f;
    /**
     * The most droplets and mist puffs one death burst can emit, before density
     * scaling. Body size only ever scales this down, so the pair is a real
     * ceiling rather than a typical case.
     */
    public static final int BURST_DROPS = 22, BURST_MIST = 5;
    /** Body size at which a burst reaches full strength. */
    private static final float BURST_FULL_POWER = 1.2f;
    private static final float BLOOD_GRAVITY = 13f;
    private float windX, windZ;
    public int collisionProbesLastUpdate;
    public int impactsLastUpdate;
    public int count;

    /** 0..1 multiplier applied to emission counts (graphics setting). */
    public float density = 1f;

    private final Random rng = new Random();

    /** WorldBootstrap and QA seed cosmetic randomness without consuming simulation RNG. */
    public void setRandomSeed(long seed) {
        rng.setSeed(seed);
        // A new dry world must not retain the previous world's voxel data until its first rain.
        collision.reset();
        windX = windZ = 0;
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
        splash[i] = false;
    }

    /** Compatibility for isolated non-weather effects; rain needs a loaded world to survive. */
    public void update(float dt) {
        update(dt, null);
    }

    /** World-aware physics; new impact droplets begin integrating on the next frame. */
    public void update(float dt, World world) {
        collisionProbesLastUpdate = impactsLastUpdate = 0;
        if (!(dt > 0) || !Float.isFinite(dt)) return;
        // Descending traversal handles swap removal and appending secondary droplets together:
        // the swapped tail has already been processed, or was just born this frame.
        for (int i = count - 1; i >= 0; i--) {
            boolean rain = kind[i] == KIND_STREAK;
            float step = Math.min(dt, life[i]);
            if (rain && world == null) { remove(i); continue; }
            if (rain) {
                float drag = 1f - (float) Math.exp(-RAIN_WIND_RESPONSE * step);
                vx[i] += (windX - vx[i]) * drag;
                vz[i] += (windZ - vz[i]) * drag;
                float terminal = RAIN_TERMINAL_BASE + size[i] * RAIN_TERMINAL_SIZE_SCALE;
                vy[i] += (-terminal - vy[i]) * (1f - (float) Math.exp(-RAIN_FALL_RESPONSE * step));
            } else vy[i] -= grav[i] * step;
            float x = px[i] + vx[i] * step, y = py[i] + vy[i] * step, z = pz[i] + vz[i] * step;
            if ((rain || splash[i]) && world != null) {
                int result = collision.trace(world, px[i], py[i], pz[i], x, y, z);
                collisionProbesLastUpdate += collision.probes;
                if (result != RainCollision.CLEAR) {
                    remove(i);
                    if (rain && result == RainCollision.HIT) {
                        impactsLastUpdate++;
                        impactSplash();
                    }
                    continue;
                }
            }
            life[i] -= dt;
            if (life[i] <= 0) { remove(i); continue; }
            px[i] = x; py[i] = y; pz[i] = z;
        }
    }

    private void remove(int i) {
        int last = --count;
        px[i] = px[last]; py[i] = py[last]; pz[i] = pz[last];
        vx[i] = vx[last]; vy[i] = vy[last]; vz[i] = vz[last];
        cr[i] = cr[last]; cg[i] = cg[last]; cb[i] = cb[last];
        size[i] = size[last]; kind[i] = kind[last]; splash[i] = splash[last];
        life[i] = life[last]; maxLife[i] = maxLife[last]; grav[i] = grav[last];
    }

    public float velocityX(int i) { return vx[i]; }
    public float velocityY(int i) { return vy[i]; }
    public float velocityZ(int i) { return vz[i]; }
    public boolean isRainSplash(int i) { return splash[i]; }

    /** Smooth cosmetic wind supplied by the rain field, independent of gameplay weather RNG. */
    public void setRainWind(float x, float z) { windX = x; windZ = z; }

    /** Existing particles stay in world space; only obsolete precipitation is recycled. */
    public void cullRain(float x, float y, float z, float radius) {
        for (int i = count - 1; i >= 0; i--) {
            if (kind[i] != KIND_STREAK && !splash[i]) continue;
            float dx = px[i] - x, dz = pz[i] - z;
            if (dx * dx + dz * dz > radius * radius || Math.abs(py[i] - y) > RAIN_VERTICAL_RANGE) remove(i);
        }
    }

    private void impactSplash() {
        float response = switch (collision.material) {
            case WATER -> 0.35f;
            case LEAVES, GRASS, DIRT, SAND, CLAY, SNOW, ASH -> 0.55f;
            default -> 1f;
        };
        int n = scaled(2 + rng.nextInt(3));
        for (int j = 0; j < n && count < SPLASH_LIMIT; j++) {
            float sx = rnd(0.85f) * response, sz = rnd(0.85f) * response;
            float sy = (0.7f + rng.nextFloat() * 1.0f) * response;
            // On side faces scatter back into air, never through the struck block.
            if (collision.nx != 0) sx = collision.nx * (0.3f + Math.abs(sx));
            if (collision.nz != 0) sz = collision.nz * (0.3f + Math.abs(sz));
            if (collision.ny < 0) sy = -sy;
            int i = count;
            spawn(KIND_DOT, collision.x + collision.nx * IMPACT_OFFSET,
                    collision.y + collision.ny * IMPACT_OFFSET, collision.z + collision.nz * IMPACT_OFFSET,
                    sx, sy, sz, 0.52f, 0.62f, 0.73f,
                    0.009f + rng.nextFloat() * 0.009f, 0.10f + rng.nextFloat() * 0.12f, SPLASH_GRAVITY);
            splash[i] = true;
        }
    }

    /** 0..1 fade factor near end of life. */
    public float fade(int i) {
        float end = Math.min(1f, life[i] / Math.max(0.01f, maxLife[i] * 0.35f));
        return kind[i] == KIND_STREAK ? end * Math.min(1f, (maxLife[i] - life[i]) / RAIN_FADE_IN) : end;
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
        // The material registry only exists with a live GL context; fall back
        // to the block's flat color in headless runs (unit tests, tools).
        var m = com.veylon.gfx.MaterialRegistry.of(t);
        int[] tile = m != null
                ? com.veylon.gfx.MaterialRegistry.layerPixels(m.sideLayer[0]) : null;
        for (int i = 0; i < scaled(n); i++) {
            float shade = 0.75f + rng.nextFloat() * 0.4f;
            float r, g, b;
            if (tile != null) {
                // Sample a random texel so debris matches the block's actual texture.
                int p = tile[rng.nextInt(tile.length)];
                r = ((p >> 16) & 0xFF) / 255f * shade;
                g = ((p >> 8) & 0xFF) / 255f * shade;
                b = (p & 0xFF) / 255f * shade;
            } else {
                r = t.r * shade;
                g = t.g * shade;
                b = t.b * shade;
            }
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

    public void snowflake(float x, float y, float z) {
        if (scaled(1) < 1) {
            return;
        }
        spawn(KIND_DOT, x, y, z, rnd(0.4f), -0.9f - rng.nextFloat() * 0.5f, rnd(0.4f),
                0.95f, 0.96f, 1f, 0.06f + rng.nextFloat() * 0.04f,
                3.5f + rng.nextFloat() * 2f, 0f);
    }

    public void rainDrop(float x, float y, float z) {
        if (count >= RAIN_LIMIT || scaled(1) < 1) return;
        spawn(KIND_STREAK, x, y, z, windX + rnd(0.65f), -12f - rng.nextFloat() * 5f,
                windZ + rnd(0.65f), 0.52f, 0.61f, 0.72f,
                0.035f + rng.nextFloat() * 0.025f, 3.5f, 0f);
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

    /**
     * The moment of death: a radial spray thrown genuinely upward, biased along
     * the killing blow, plus a little mist that fades before the droplets land.
     *
     * <p>Bounded twice over. Counts are small and fixed rather than scaled with
     * damage, every one of them goes through {@link #scaled} so a zero density
     * setting emits nothing at all, and the whole burst stops at
     * {@link #BLOOD_LIMIT} — under the rain and splash ceilings — so even a
     * massacre during a storm cannot crowd out fire and combat effects.
     *
     * @param power 0..~1.5 body-size scale; a hare sprays less than a thornhorn
     */
    public void bloodBurst(float x, float y, float z,
                           float dirX, float dirY, float dirZ, float power) {
        float scale = Math.min(1f, Math.max(0.35f, power / BURST_FULL_POWER));
        int drops = scaled(Math.round(BURST_DROPS * scale));
        for (int i = 0; i < drops && count < BLOOD_LIMIT; i++) {
            float angle = rng.nextFloat() * (float) (Math.PI * 2.0);
            float spread = 1.4f + rng.nextFloat() * 2.6f;
            float lift = 2.2f + rng.nextFloat() * 3.4f;
            spawn(KIND_DOT, x + rnd(0.16f), y + rnd(0.16f), z + rnd(0.16f),
                    (float) Math.cos(angle) * spread + dirX * 3.2f,
                    lift + dirY * 1.6f,
                    (float) Math.sin(angle) * spread + dirZ * 3.2f,
                    0.48f + rng.nextFloat() * 0.14f, 0.06f, 0.06f,
                    0.045f + rng.nextFloat() * 0.05f,
                    0.45f + rng.nextFloat() * 0.35f, BLOOD_GRAVITY);
        }
        int mist = scaled(BURST_MIST);
        for (int i = 0; i < mist && count < BLOOD_LIMIT; i++) {
            spawn(KIND_PUFF, x + rnd(0.2f), y + rnd(0.15f), z + rnd(0.2f),
                    dirX * 1.1f + rnd(0.5f), 0.5f + rng.nextFloat() * 0.7f,
                    dirZ * 1.1f + rnd(0.5f),
                    0.52f + rng.nextFloat() * 0.10f, 0.06f, 0.06f,
                    0.13f + rng.nextFloat() * 0.10f,
                    0.22f + rng.nextFloat() * 0.16f, 2.5f);
        }
    }

    /** One droplet shed by a body that is still tumbling. */
    public void bloodDrip(float x, float y, float z, float velX, float velY, float velZ) {
        if (scaled(1) < 1 || count >= BLOOD_LIMIT) {
            return;
        }
        spawn(KIND_DOT, x + rnd(0.12f), y + rnd(0.12f), z + rnd(0.12f),
                velX * 0.25f + rnd(0.5f), velY * 0.15f + rnd(0.4f), velZ * 0.25f + rnd(0.5f),
                0.48f + rng.nextFloat() * 0.14f, 0.06f, 0.06f,
                0.04f + rng.nextFloat() * 0.03f, 0.45f, BLOOD_GRAVITY);
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

    /**
     * Black-powder explosion: a very short emissive flash, radial sparks and
     * hot fragments, texture-neutral debris, expanding dust/smoke and embers.
     * Counts scale with blast power but respect the density setting and cap.
     */
    public void explosion(float x, float y, float z, float power) {
        float mul = Math.min(1.6f, 0.6f + power * 0.25f);
        // Core flash: a handful of large, very short-lived additive sparks.
        for (int i = 0; i < scaled(6); i++) {
            spawn(KIND_SPARK, x + rnd(0.3f), y + rnd(0.3f), z + rnd(0.3f),
                    rnd(0.6f), rnd(0.6f), rnd(0.6f),
                    1f, 0.85f, 0.55f, 0.9f + rng.nextFloat() * 0.6f, 0.12f, 0f);
        }
        // Radial sparks / hot fragments.
        int sparks = scaled((int) (26 * mul));
        for (int i = 0; i < sparks; i++) {
            float ang = rng.nextFloat() * (float) (Math.PI * 2.0);
            float el = (rng.nextFloat() - 0.35f) * 1.6f;
            float speed = 4f + rng.nextFloat() * 8f * mul;
            spawn(KIND_SPARK, x, y + 0.2f, z,
                    (float) (Math.cos(ang) * Math.cos(el)) * speed,
                    (float) Math.sin(el) * speed + 2.5f,
                    (float) (Math.sin(ang) * Math.cos(el)) * speed,
                    1f, 0.42f + rng.nextFloat() * 0.35f, 0.10f,
                    0.07f + rng.nextFloat() * 0.08f, 0.5f + rng.nextFloat() * 0.9f, 11f);
        }
        // Dark debris chunks.
        for (int i = 0; i < scaled((int) (10 * mul)); i++) {
            float shade = 0.16f + rng.nextFloat() * 0.14f;
            spawn(KIND_DOT, x + rnd(0.5f), y + 0.3f, z + rnd(0.5f),
                    rnd(5f), 2.5f + rng.nextFloat() * 5f, rnd(5f),
                    shade, shade * 0.9f, shade * 0.8f,
                    0.08f + rng.nextFloat() * 0.09f, 0.8f + rng.nextFloat() * 0.8f, 15f);
        }
        // Expanding dust and smoke column.
        for (int i = 0; i < scaled((int) (14 * mul)); i++) {
            float shade = 0.26f + rng.nextFloat() * 0.16f;
            spawn(KIND_PUFF, x + rnd(0.9f), y + 0.2f + rnd(0.3f), z + rnd(0.9f),
                    rnd(2.2f), 0.9f + rng.nextFloat() * 2.4f, rnd(2.2f),
                    shade, shade * 0.94f, shade * 0.86f,
                    0.4f + rng.nextFloat() * 0.55f, 1.6f + rng.nextFloat() * 1.6f, -0.3f);
        }
        // Lingering embers.
        for (int i = 0; i < scaled(5); i++) {
            ember(x + rnd(1.2f), y + 0.4f, z + rnd(1.2f));
        }
    }

    /**
     * A fire bomb breaking: a few pale shards of glass and a splash of burning
     * droplets that arc out the way it was thrown and fall, over one brief
     * flash far smaller than a blast's. No debris, dust or smoke column,
     * because nothing blew up. {@code (dirX, dirZ)} need not be normalised;
     * zero scatters the splash evenly.
     */
    public void molotovShatter(float x, float y, float z, float dirX, float dirZ) {
        float len = (float) Math.sqrt(dirX * dirX + dirZ * dirZ);
        float bx = len > 1e-4f ? dirX / len : 0f;
        float bz = len > 1e-4f ? dirZ / len : 0f;
        int flash = scaled(SHATTER_FLASH - 1 + rng.nextInt(2));
        for (int i = 0; i < flash && count < SPLASH_LIMIT; i++) {
            spawn(KIND_SPARK, x + rnd(0.15f), y + 0.1f + rnd(0.1f), z + rnd(0.15f),
                    rnd(0.4f), 0.3f + rnd(0.4f), rnd(0.4f),
                    1f, 0.9f, 0.6f, 0.45f + rng.nextFloat() * 0.2f, 0.1f, 0f);
        }
        int glass = scaled(SHATTER_GLASS - 4 + rng.nextInt(5));
        for (int i = 0; i < glass && count < SPLASH_LIMIT; i++) {
            float angle = rng.nextFloat() * (float) (Math.PI * 2.0);
            float speed = 1f + rng.nextFloat() * 1.8f;
            float shade = rnd(0.05f);
            spawn(KIND_DOT, x + rnd(0.1f), y + rnd(0.1f), z + rnd(0.1f),
                    (float) Math.cos(angle) * speed + bx * 1.2f, 1.2f + rng.nextFloat() * 1.8f,
                    (float) Math.sin(angle) * speed + bz * 1.2f,
                    0.75f + shade, 0.85f + shade, 0.80f + shade,
                    0.04f + rng.nextFloat() * 0.03f, 0.35f + rng.nextFloat() * 0.25f, GLASS_GRAVITY);
        }
        int drops = scaled(SHATTER_DROPS - 8 + rng.nextInt(9));
        for (int i = 0; i < drops && count < SPLASH_LIMIT; i++) {
            float angle = rng.nextFloat() * (float) (Math.PI * 2.0);
            float spread = 0.6f + rng.nextFloat() * 1.6f;
            float ahead = 1.5f + rng.nextFloat() * 2.5f;
            float t = rng.nextFloat();
            spawn(KIND_SPARK, x + rnd(0.12f), y + 0.05f + rnd(0.08f), z + rnd(0.12f),
                    bx * ahead + (float) Math.cos(angle) * spread, 2.5f + rng.nextFloat() * 2.5f,
                    bz * ahead + (float) Math.sin(angle) * spread,
                    1f, 0.55f + 0.37f * t, 0.08f + 0.37f * t,
                    0.10f + rng.nextFloat() * 0.08f, 0.5f + rng.nextFloat() * 0.4f, DROPLET_GRAVITY);
        }
    }

    /**
     * One low, wide tongue of flame off burning liquid, anywhere over the
     * cell whose floor centre is {@code (x, y, z)}. It burns yellow over
     * fresh liquid at the centre of a spill and deep orange at the rim and
     * as the pool burns down; see {@link #liquidHeat}.
     *
     * @param intensity the patch's intensity, 1 at the centre of a spill
     * @param lifeLeft  share of the patch's burn still ahead of it, 1 when fresh
     */
    public void liquidFlame(float x, float y, float z, float intensity, float lifeLeft) {
        float strength = unit(intensity);
        if (strength <= 0f || count >= SPLASH_LIMIT || scaled(1) < 1) {
            return;
        }
        float heat = unit(liquidHeat(intensity, lifeLeft) + rnd(0.08f));
        spawn(KIND_SPARK, x + rnd(0.45f), y + 0.08f, z + rnd(0.45f),
                rnd(0.15f), 0.6f + rng.nextFloat() * 0.7f, rnd(0.15f),
                1f, MathUtil.lerp(LIQUID_COOL_G, LIQUID_HOT_G, heat),
                MathUtil.lerp(LIQUID_COOL_B, LIQUID_HOT_B, heat),
                (0.25f + rng.nextFloat() * 0.20f) * strength, 0.25f + rng.nextFloat() * 0.25f, -0.8f);
    }

    /** A breath of steam off burning liquid the rain is putting out: light grey-white, rising. */
    public void steamPuff(float x, float y, float z) {
        int n = scaled(STEAM_PUFFS);
        for (int i = 0; i < n && count < SPLASH_LIMIT; i++) {
            float s = 0.82f + rng.nextFloat() * 0.12f;
            spawn(KIND_PUFF, x + rnd(0.35f), y + rng.nextFloat() * 0.15f, z + rnd(0.35f),
                    rnd(0.2f), 0.8f + rng.nextFloat() * 0.7f, rnd(0.2f),
                    s, s, s + 0.03f,
                    0.28f + rng.nextFloat() * 0.22f, 1.0f + rng.nextFloat() * 0.8f, -0.3f);
        }
    }

    /**
     * How hot burning liquid looks, 0..1: 1 for fresh liquid at the centre
     * of a spill, half that at its rim or once it has nearly burned down.
     * The pool's sheet and its flames share it, so they cool together.
     * Anything that is not a positive number counts as cold.
     */
    public static float liquidHeat(float intensity, float lifeLeft) {
        return unit(intensity) * (0.5f + 0.5f * unit(lifeLeft));
    }

    /** Clamps to 0..1, with NaN as 0. */
    private static float unit(float v) {
        return v > 0f ? Math.min(1f, v) : 0f;
    }

    /** Brief muzzle flash + powder smoke at a firing position. */
    public void muzzleFlash(float x, float y, float z, float dirX, float dirY, float dirZ) {
        for (int i = 0; i < scaled(3); i++) {
            spawn(KIND_SPARK, x + dirX * 0.5f, y + dirY * 0.5f, z + dirZ * 0.5f,
                    dirX * 3f + rnd(0.8f), dirY * 3f + rnd(0.8f), dirZ * 3f + rnd(0.8f),
                    1f, 0.8f, 0.45f, 0.22f, 0.10f, 0f);
        }
        for (int i = 0; i < scaled(4); i++) {
            float s = 0.55f + rng.nextFloat() * 0.2f;
            spawn(KIND_PUFF, x + dirX * 0.6f, y + dirY * 0.6f, z + dirZ * 0.6f,
                    dirX * 1.2f + rnd(0.4f), 0.5f + rnd(0.3f), dirZ * 1.2f + rnd(0.4f),
                    s, s, s, 0.16f + rng.nextFloat() * 0.12f, 0.7f + rng.nextFloat() * 0.5f, -0.3f);
        }
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
