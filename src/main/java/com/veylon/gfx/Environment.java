package com.veylon.gfx;

import com.veylon.Game;
import com.veylon.entity.Affliction;
import com.veylon.simulation.SeasonSystem;
import com.veylon.simulation.WeatherSystem;
import com.veylon.world.Biome;
import com.veylon.world.BlockType;
import org.joml.Vector3f;

/**
 * Per-frame atmosphere state: sun/moon, light and ambient colors, fog profile,
 * sky palette, foliage tint, wetness/frost, and the post-processing grade.
 * One place where time, weather, season, events and biome combine.
 */
public class Environment {

    // Light rig.
    public final Vector3f lightDir = new Vector3f(0, 1, 0);
    public final Vector3f sunDirVisual = new Vector3f(0, 1, 0);
    public final Vector3f lightColor = new Vector3f();
    public final Vector3f ambientSky = new Vector3f();
    public final Vector3f ambientGround = new Vector3f();
    public final Vector3f blockLightColor = new Vector3f(1.0f, 0.62f, 0.28f);

    // Sky.
    public final Vector3f zenith = new Vector3f();
    public final Vector3f horizon = new Vector3f();
    public final Vector3f sunDiskColor = new Vector3f();
    public float night;
    public float cloudCover;
    public float cloudDark;
    public float flash;

    // Fog.
    public final Vector3f fogColor = new Vector3f();
    public float fogStart = 60, fogEnd = 110;

    // Surface state.
    public final Vector3f foliageTint = new Vector3f(1, 1, 1);
    public float wetness;
    public float frost;

    // Water palette.
    public final Vector3f waterDeep = new Vector3f(0.05f, 0.16f, 0.28f);
    public final Vector3f waterShallow = new Vector3f(0.16f, 0.42f, 0.45f);

    // Post grade.
    public float exposure = 1f;
    public float saturation = 1.04f;
    public float contrast = 1.02f;
    public final Vector3f gradeTint = new Vector3f(1, 1, 1);
    public float underwater;
    public float vigDamage, vigCold, vigPoison, vigSmokeHeat, heatMix;

    private final Vector3f tmp = new Vector3f();

    public void update(Game g, float dt) {
        double h = g.time.hourF();
        float dayLight = (float) g.time.dayLight();
        float weatherLight = g.weather.lightMul() * g.events.skyLightMul();
        flash = g.weather.flashLight();

        // ---- Sun / moon path ----
        // Day arc 5h..21h; night arc for the moon.
        float dayT = (float) ((h - 5.0) / 16.0);
        boolean isDay = dayT >= 0 && dayT <= 1;
        float sunEl = (float) Math.sin(Math.PI * clamp(dayT, 0f, 1f));
        float az = (float) (Math.PI * (0.15 + 0.7 * clamp(dayT, 0f, 1f)));
        sunDirVisual.set((float) Math.cos(az) * (1.1f - sunEl * 0.6f), Math.max(sunEl, -0.25f),
                (float) Math.sin(az) * 0.55f).normalize();

        float nightT = (float) (h >= 21 ? (h - 21.0) / 8.0 : (h + 3.0) / 8.0);
        float moonEl = (float) Math.sin(Math.PI * clamp(nightT, 0f, 1f));

        night = 1f - smooth(clamp((dayLight - 0.10f) / 0.5f, 0f, 1f));

        if (isDay && sunEl > 0.02f) {
            lightDir.set(sunDirVisual);
            // Sun color: warm noon -> deep orange at the horizon.
            float lowSun = 1f - clamp(sunEl * 1.6f, 0f, 1f);
            lightColor.set(
                    lerp(1.0f, 1.0f, lowSun),
                    lerp(0.97f, 0.55f, lowSun),
                    lerp(0.90f, 0.28f, lowSun))
                    .mul(1.15f * sunEl * weatherLight + 0.05f);
        } else {
            lightDir.set((float) Math.cos(Math.PI * 0.4), Math.max(moonEl, 0.25f),
                    (float) Math.sin(Math.PI * 0.4)).normalize();
            lightColor.set(0.30f, 0.36f, 0.52f).mul(0.16f * weatherLight);
        }

        // ---- Ambient hemisphere ----
        float amb = Math.max(dayLight * weatherLight, 0.05f);
        ambientSky.set(lerp(0.055f, 0.48f, amb), lerp(0.07f, 0.58f, amb), lerp(0.13f, 0.78f, amb));
        ambientGround.set(lerp(0.03f, 0.34f, amb), lerp(0.032f, 0.30f, amb), lerp(0.05f, 0.26f, amb));

        // ---- Sky palette ----
        float dl = Math.max(dayLight * weatherLight, 0.05f);
        zenith.set(lerp(0.012f, 0.24f, dl), lerp(0.02f, 0.45f, dl), lerp(0.06f, 0.80f, dl));
        horizon.set(lerp(0.03f, 0.66f, dl), lerp(0.04f, 0.76f, dl), lerp(0.08f, 0.88f, dl));
        // Dawn/dusk warm band.
        float lowSunBand = isDay ? (1f - clamp(sunEl * 2.2f, 0f, 1f)) : 0f;
        horizon.add(0.35f * lowSunBand * dl, 0.10f * lowSunBand * dl, -0.06f * lowSunBand * dl);
        sunDiskColor.set(1.0f, lerp(0.92f, 0.5f, lowSunBand), lerp(0.8f, 0.25f, lowSunBand));

        float gray = g.weather.grayness();
        desaturate(zenith, gray);
        desaturate(horizon, gray);

        // ---- Clouds ----
        WeatherSystem.Weather w = g.weather.effective();
        cloudCover = switch (w) {
            case CLEAR -> 0.15f;
            case CLOUDY -> 0.7f;
            case FOG -> 0.8f;
            case RAIN -> 0.9f;
            case SNOW -> 0.85f;
            case STORM -> 1.0f;
        };
        cloudDark = switch (w) {
            case STORM -> 0.85f;
            case RAIN -> 0.55f;
            case SNOW -> 0.3f;
            default -> 0.08f;
        };

        // ---- Biome profile ----
        Biome biome = g.player.biome;
        Vector3f biomeFog = switch (biome) {
            case MARSH -> tmp.set(0.85f, 1.0f, 0.9f);
            case SCRUBLAND -> tmp.set(1.1f, 1.02f, 0.85f);
            case COLD_RIDGE -> tmp.set(0.95f, 1.0f, 1.1f);
            case PINE_FOREST -> tmp.set(0.88f, 0.98f, 0.95f);
            case ROCKY_HIGHLANDS -> tmp.set(1.0f, 1.0f, 1.05f);
            default -> tmp.set(1f, 1f, 1f);
        };

        // ---- Fog ----
        fogStart = g.weather.fogStart();
        fogEnd = g.weather.fogEnd();
        fogColor.set(horizon).mul(0.9f).add(zenith.x * 0.1f, zenith.y * 0.1f, zenith.z * 0.1f);
        fogColor.mul(biomeFog.x, biomeFog.y, biomeFog.z);

        // ---- Events ----
        boolean toxic = g.events.toxicFog();
        boolean ash = g.events.ashfall();
        gradeTint.set(1, 1, 1);
        saturation = 1.04f;
        contrast = 1.02f;
        if (toxic) {
            fogStart = Math.min(fogStart, 12f);
            fogEnd = Math.min(fogEnd, 40f);
            fogColor.set(fogColor.x * 0.55f, Math.min(1f, fogColor.y * 1.05f + 0.05f), fogColor.z * 0.4f);
            gradeTint.set(0.88f, 1.0f, 0.62f);
            saturation = 0.9f;
            desaturate(zenith, 0.3f);
            zenith.mul(0.7f, 0.9f, 0.55f);
            horizon.mul(0.75f, 0.95f, 0.5f);
        } else if (ash) {
            gradeTint.set(0.92f, 0.9f, 0.87f);
            saturation = 0.55f;
            desaturate(zenith, 0.55f);
            desaturate(horizon, 0.55f);
            zenith.mul(0.75f);
            horizon.mul(0.75f);
            fogColor.mul(0.7f);
            cloudCover = Math.max(cloudCover, 0.85f);
            cloudDark = Math.max(cloudDark, 0.6f);
        }

        // ---- Foliage tint: season x biome ----
        SeasonSystem.Season season = g.seasons.current(g.time);
        switch (season) {
            case WET -> foliageTint.set(0.9f, 1.06f, 0.88f);
            case COLD -> foliageTint.set(0.86f, 0.82f, 0.80f);
            case DRY -> foliageTint.set(1.08f, 0.98f, 0.62f);
            default -> foliageTint.set(1f, 1f, 1f);
        }
        if (biome == Biome.SCRUBLAND) {
            foliageTint.mul(1.05f, 0.95f, 0.8f);
        } else if (biome == Biome.MARSH) {
            foliageTint.mul(0.9f, 1.0f, 0.85f);
        }

        // ---- Wetness / frost (smoothed so transitions read naturally) ----
        boolean rainWet = g.weather.isPrecip() && g.weather.effective() != WeatherSystem.Weather.SNOW;
        float wetTarget = rainWet ? g.weather.intensity() : 0f;
        float rate = wetTarget > wetness ? 0.25f : 0.04f;
        wetness += (wetTarget - wetness) * Math.min(1f, dt * rate * 10f);

        float frostTarget = 0f;
        if (biome == Biome.COLD_RIDGE || season == SeasonSystem.Season.COLD) {
            frostTarget = g.player.envTemp < 0 ? 0.6f : 0.3f;
        }
        frost += (frostTarget - frost) * Math.min(1f, dt * 0.5f);

        // ---- Exposure & underwater ----
        exposure = lerp(1.45f, 1.0f, clamp(dl * 1.3f, 0f, 1f));
        BlockType camBlock = g.world.getBlock(
                (int) Math.floor(g.camera.position.x),
                (int) Math.floor(g.camera.position.y),
                (int) Math.floor(g.camera.position.z));
        float uwTarget = camBlock == BlockType.WATER ? 1f : 0f;
        underwater += (uwTarget - underwater) * Math.min(1f, dt * 10f);
        if (underwater > 0.3f) {
            fogStart = Math.min(fogStart, 2f);
            fogEnd = Math.min(fogEnd, 18f);
            fogColor.set(0.05f, 0.2f, 0.3f);
        }

        // ---- State vignettes ----
        var p = g.player;
        vigDamage = clamp(p.damageFlash * 0.8f, 0f, 1f);
        if (p.health < 25) {
            vigDamage = Math.max(vigDamage,
                    (0.5f + 0.5f * (float) Math.sin(g.totalTime * 4)) * (1f - p.health / 25f) * 0.5f);
        }
        vigCold = p.bodyTemp < 34.5f ? clamp((34.5f - p.bodyTemp) / 5f, 0f, 0.8f) : 0f;
        vigPoison = (p.has(Affliction.FOOD_POISONING) || p.has(Affliction.SICKNESS)) ? 0.35f : 0f;
        if (toxic && p.exposedToSky) {
            vigPoison = Math.max(vigPoison, 0.3f);
        }
        boolean hot = p.bodyTemp > 40f;
        heatMix = hot ? 1f : 0f;
        vigSmokeHeat = p.has(Affliction.SMOKE) ? 0.45f
                : (hot ? clamp((p.bodyTemp - 40f) / 2f, 0f, 0.6f) : 0f);
    }

    private static void desaturate(Vector3f c, float amount) {
        float l = (c.x + c.y + c.z) / 3f;
        c.set(lerp(c.x, l, amount), lerp(c.y, l, amount), lerp(c.z, l, amount));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }
}
