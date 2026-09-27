package com.veylon.gfx;

import com.veylon.entity.BodyCombustion;
import com.veylon.entity.BurnResidue;
import com.veylon.entity.CombustionConstants;
import com.veylon.entity.Entity;

/**
 * What a body's fire looks like at one moment, read from the fire the
 * simulation keeps on a living body ({@link BodyCombustion}) or on remains
 * ({@link BurnResidue}). Every presentation of body fire — the flames drawn on
 * the body, the smoke, embers and steam it gives off, its scorch, the player's
 * own view and the sound — starts here, so they all tell the same story:
 *
 * <ul>
 *   <li>catching: the flames swell briefly and climb from where the flame
 *       touched the body to the rest of it;</li>
 *   <li>burning: full flames over most of the body while the fire is strong;
 *       as its fuel runs down they shrink to fewer, smaller tongues and the
 *       smoke thickens;</li>
 *   <li>rain on an exposed burning body subdues the flames and steams, as the
 *       simulation's soak builds towards putting them out;</li>
 *   <li>out: the flames die down over a moment — faster when water or rain put
 *       them out, and then with a breath of steam; a fire that burned out
 *       leaves a wisp of smoke instead;</li>
 *   <li>remains: the flames the body died with burn down and smoke, and steam
 *       if water or rain doused them.</li>
 * </ul>
 *
 * <p>It only reads. A mutable value reused per body, so drawing and emitting
 * allocate nothing; {@link #none()} clears it for an unburned body, so
 * nothing of one body's fire carries over to the next one drawn.
 */
public final class BodyFireLook {

    /** Seconds the flames swell for after a body catches. */
    public static final float FLARE_SECONDS = 0.35f;
    /** How far from the touch point the flames stand at once, and how fast they climb, m and m/s. */
    public static final float SPREAD_START = 0.25f, SPREAD_SPEED = 3f;
    /** Beyond this the flames have reached every part of any body. */
    public static final float SPREAD_DONE = 3f;
    /** Share of a body's flame anchors alight at the weakest and at full intensity. */
    public static final float MIN_COVERAGE = 0.35f;
    /** Seconds flames take to die down after burning out, and after water or rain. */
    public static final float TAPER_SECONDS = 0.6f, DOUSED_TAPER_SECONDS = 0.25f;
    /** Seconds of steam after water or rain puts flames out. */
    public static final float STEAM_SECONDS = 1.2f;
    /** Seconds of smoke wisp after a fire burns out. */
    public static final float WISP_SECONDS = 1.5f;
    /** How much of the flame rain can hold down before it puts the fire out. */
    public static final float RAIN_SUBDUE = 0.45f;
    /** Smoke at full strength and at the weakest flames, as a rate 0..1. */
    public static final float SMOKE_STRONG = 0.25f, SMOKE_WEAK = 1f;

    /** Strength of the flames drawn, 0..1: their size, heat and light. */
    public float flame;
    /** Share of the body's flame anchors alight, 0..1. */
    public float coverage;
    /** 0..1 swell just after the body caught. */
    public float flare;
    /** Whether the flames are still climbing from where they touched the body. */
    public boolean spreading;
    /** Metres from the touch point the flames have reached while {@link #spreading}. */
    public float spread;
    /** Where the flame touched the body, while {@link #spreading}. */
    public float touchX, touchY, touchZ;
    /** Rate of smoke given off, 0..1. */
    public float smoke;
    /** Rate of steam given off, 0..1. */
    public float steam;
    /** Rate of embers thrown, 0..1. */
    public float embers;
    /** How charred the body looks, 0..1; never fades. */
    public float scorch;
    /** Embers glowing in the char while flames burn, 0..1. */
    public float glow;
    /** How much the body's own flames light it, 0..1. */
    public float light;
    /** The body's flicker number, fixed per fire. */
    public int seed;

    /** An unburned body: nothing at all. */
    public BodyFireLook none() {
        flame = coverage = flare = spread = smoke = steam = embers = scorch = glow = light = 0f;
        spreading = false;
        touchX = touchY = touchZ = 0f;
        seed = 0;
        return this;
    }

    /** Whether there is anything to draw or emit beyond the scorch. */
    public boolean active() {
        return flame > 0f || smoke > 0f || steam > 0f;
    }

    /**
     * A living body's fire, as its combustion state stands now; the flames
     * climb from where they touched it, carried with the body as it moves.
     */
    public BodyFireLook living(Entity e) {
        none();
        if (e == null) {
            return this;
        }
        BodyCombustion b = e.combustion;
        scorch = b.scorch();
        seed = b.flameSeed();
        if (b.burning()) {
            float intensity = b.intensity();
            float rain = unit(b.soak() / CombustionConstants.RAIN_EXTINGUISH_SECONDS);
            flame = intensity * (1f - RAIN_SUBDUE * rain);
            coverage = coverage(intensity) * (1f - 0.3f * rain);
            flare = Math.max(0f, 1f - b.burnSeconds() / FLARE_SECONDS);
            float reach = SPREAD_START + b.burnSeconds() * SPREAD_SPEED;
            if (b.hasExposure() && reach < SPREAD_DONE) {
                spreading = true;
                spread = reach;
                touchX = e.pos.x + b.touchX();
                touchY = e.pos.y + b.touchY();
                touchZ = e.pos.z + b.touchZ();
            }
            smoke = smokeFor(intensity);
            steam = rain;
            embers = flame;
            glow = intensity;
            light = flame * (0.8f + 0.2f * flare);
            return this;
        }
        float out = b.outSeconds();
        if (out >= CombustionConstants.OUT_MEMORY_SECONDS || b.outIntensity() <= 0f) {
            return this;
        }
        float strength = b.outIntensity();
        float taper = b.outDoused() ? DOUSED_TAPER_SECONDS : TAPER_SECONDS;
        float left = Math.max(0f, 1f - out / taper);
        flame = strength * left;
        coverage = coverage(strength) * left;
        glow = flame;
        light = flame;
        if (b.outDoused()) {
            steam = Math.max(0f, 1f - out / STEAM_SECONDS);
            smoke = 0.4f * Math.max(0f, 1f - out / WISP_SECONDS);
        } else {
            smoke = Math.max(0f, 1f - out / WISP_SECONDS) * smokeFor(strength);
        }
        return this;
    }

    /**
     * The fire on the remains of a body that died: the flames it died with
     * burning down, their smoke, steam once water or rain doused them, and
     * the scorch it had. Null remains are unburned.
     */
    public BodyFireLook remains(BurnResidue r) {
        none();
        if (r == null) {
            return this;
        }
        scorch = r.scorch();
        seed = r.seed();
        flame = r.flame();
        coverage = flame > 0f ? coverage(flame) * Math.min(1f, flame / Math.max(r.flameAtDeath(), 1e-3f) * 1.5f) : 0f;
        smoke = r.smoke();
        embers = flame;
        glow = flame;
        light = flame;
        if (r.doused() && r.active()) {
            float after = r.age() - r.flameSeconds();
            steam = Math.max(0f, 1f - after / STEAM_SECONDS);
        }
        return this;
    }

    /**
     * The player's red damage flash as the screen shows it: while they are
     * alight, only what rises above the least flash the afterburn keeps
     * ({@link CombustionConstants#AFTERBURN_FLASH} of the intensity), so the
     * flames at the edges of the view speak for the burn and red still means
     * a fresh hurt, such as standing in the flames. The flash itself, and
     * everything that reads it, is unchanged.
     */
    public static float shownDamageFlash(com.veylon.entity.Player p) {
        float floor = !p.dead && p.combustion.burning()
                ? CombustionConstants.AFTERBURN_FLASH * p.combustion.intensity() : 0f;
        return Math.max(0f, p.damageFlash - floor);
    }

    /** Share of the anchors alight at an intensity: {@link #MIN_COVERAGE} at the weakest, all at 1. */
    public static float coverage(float intensity) {
        float min = CombustionConstants.MIN_INTENSITY;
        float t = unit((intensity - min) / (1f - min));
        return intensity <= 0f ? 0f : MIN_COVERAGE + (1f - MIN_COVERAGE) * t;
    }

    /** Smoke rises as the flames weaken: {@link #SMOKE_STRONG} at full strength, {@link #SMOKE_WEAK} at the weakest. */
    public static float smokeFor(float intensity) {
        float min = CombustionConstants.MIN_INTENSITY;
        float t = unit((intensity - min) / (1f - min));
        return SMOKE_WEAK + (SMOKE_STRONG - SMOKE_WEAK) * t;
    }

    private static float unit(float v) {
        return v > 0f ? Math.min(1f, v) : 0f;
    }
}
