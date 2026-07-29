package com.veylon;

import com.veylon.entity.Affliction;
import com.veylon.simulation.WeatherSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.Biome;
import com.veylon.world.BlockType;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Atmosphere around the player: ambient particle emitters and the ambient
 * audio mix.
 *
 * <p>Both are derived entirely from existing world state — weather, events,
 * burning cells, depth, biome and time of day — and neither feeds anything
 * back. Nothing here changes simulation outcomes, so it is safe to throttle:
 * emitters run on a fixed {@value #EMITTER_INTERVAL}s cadence rather than every
 * frame, and every spawner is distance-bounded so a large fire or a long
 * campfire list cannot scale particle cost with world size.
 */
final class AmbienceSystem {

    /** Seconds between emitter passes. */
    private static final float EMITTER_INTERVAL = 0.12f;

    // Precipitation.
    private static final float RAIN_RADIUS = 11f;
    private static final float RAIN_SPAWN_HEIGHT = 6f;
    private static final float RAIN_SPAWN_HEIGHT_RANGE = 5f;
    /** Drops spawned per pass at full storm intensity. */
    private static final int RAIN_PARTICLES_AT_FULL_INTENSITY = 5;
    private static final float RAIN_SPLASH_CHANCE = 0.5f;

    // Weather events.
    private static final int ASHFALL_PARTICLES = 3;
    private static final float ASHFALL_RADIUS = 10f;
    private static final float ASHFALL_HEIGHT = 5f;
    private static final int TOXIC_PARTICLES = 2;
    private static final float TOXIC_RADIUS = 9f;

    // Fire and beacon, all distance-bounded.
    private static final float FIRE_PARTICLE_RANGE = 40f;
    private static final float FIRE_EMBER_CHANCE = 0.5f;
    private static final float CAMPFIRE_PARTICLE_RANGE = 35f;
    private static final float CAMPFIRE_EMBER_CHANCE = 0.35f;
    private static final float BEACON_PARTICLE_RANGE = 55f;
    private static final float BEACON_SECOND_MOTE_CHANCE = 0.45f;

    // Player-driven effects.
    private static final float BREATH_INTERVAL = 2.6f;
    /** Environment temperature below which breath is visible. */
    private static final float BREATH_TEMP = 2f;
    private static final float COUGH_INTERVAL = 3.5f;

    // Ambient audio mix.
    /** Rain is muffled to this fraction when the player is under cover. */
    private static final float RAIN_INDOOR_GAIN = 0.45f;
    private static final float STORM_WIND_GAIN = 0.9f;
    private static final float EXPOSED_WIND_GAIN = 0.45f;
    private static final float CLOUDY_WIND_GAIN = 0.2f;
    /** Height above which wind is audible regardless of weather. */
    private static final float WINDY_ALTITUDE = 52f;
    private static final float FIRE_MIN_AUDIBLE_HEAT = 1f;
    /** Fire heat that saturates the fire ambience channel. */
    private static final float FIRE_GAIN_FULL_HEAT = 14f;
    /** Blocks below the surface at which cave ambience takes over. */
    private static final float CAVE_DEPTH = 5f;
    private static final float CAVE_GAIN = 0.9f;
    private static final float CRICKET_GAIN = 0.8f;
    /** Blocks within which the lit beacon is audible. */
    private static final double BEACON_AUDIO_RANGE = 22.0;

    private final Game game;
    private final Random emitterRng = new Random();
    private float emitterTimer;
    private float breathTimer;
    private float coughTimer;

    AmbienceSystem(Game game) {
        this.game = game;
    }

    /** Keeps ambient scatter replayable per world seed. */
    void reseed(long seed) {
        emitterRng.setSeed(seed);
    }

    /** Ambient particle and sound emitters driven by world state. */
    void updateEmitters(float dt) {
        emitterTimer -= dt;
        if (emitterTimer > 0) {
            return;
        }
        emitterTimer = EMITTER_INTERVAL;
        float px = game.player.pos.x, py = game.player.pos.y, pz = game.player.pos.z;

        emitPrecipitation(px, py, pz);
        emitWeatherEvents(px, py, pz);
        emitFires(px, py, pz);
        emitBeacon(px, py, pz);
        emitPlayerEffects();
    }

    private void emitPrecipitation(float px, float py, float pz) {
        if (!game.weather.isPrecip() || !game.player.exposedToSky) {
            return;
        }
        Random rng = emitterRng;
        boolean snow = game.weather.effective() == WeatherSystem.Weather.SNOW;
        int n = (int) (RAIN_PARTICLES_AT_FULL_INTENSITY * game.weather.intensity());
        for (int i = 0; i < n; i++) {
            float x = px + (rng.nextFloat() * 2 - 1) * RAIN_RADIUS;
            float z = pz + (rng.nextFloat() * 2 - 1) * RAIN_RADIUS;
            float y = py + RAIN_SPAWN_HEIGHT + rng.nextFloat() * RAIN_SPAWN_HEIGHT_RANGE;
            if (snow) {
                game.particles.snowflake(x, y, z);
                continue;
            }
            game.particles.rainDrop(x, y, z);
            if (rng.nextFloat() < RAIN_SPLASH_CHANCE) {
                int gx = (int) x, gz = (int) z;
                // Only splash on ground that is actually loaded.
                if (game.world.getChunk(Math.floorDiv(gx, 16), Math.floorDiv(gz, 16)) != null) {
                    game.particles.rainSplash(x, game.world.surfaceHeight(gx, gz) + 1.05f, z);
                }
            }
        }
    }

    private void emitWeatherEvents(float px, float py, float pz) {
        Random rng = emitterRng;
        // Ashfall drifts grey flakes everywhere.
        if (game.events.ashfall()) {
            for (int i = 0; i < ASHFALL_PARTICLES; i++) {
                game.particles.ashFlake(px + (rng.nextFloat() * 2 - 1) * ASHFALL_RADIUS,
                        py + ASHFALL_HEIGHT + rng.nextFloat() * ASHFALL_HEIGHT,
                        pz + (rng.nextFloat() * 2 - 1) * ASHFALL_RADIUS);
            }
        }
        // Toxic fog carries slow, sickly motes at eye and ground level.
        if (game.events.toxicFog()) {
            for (int i = 0; i < TOXIC_PARTICLES; i++) {
                game.particles.toxicMote(px + (rng.nextFloat() * 2 - 1) * TOXIC_RADIUS,
                        py + 0.3f + rng.nextFloat() * 2.2f,
                        pz + (rng.nextFloat() * 2 - 1) * TOXIC_RADIUS);
            }
        }
    }

    /** Smoke and embers from burning blocks and fueled campfires. */
    private void emitFires(float px, float py, float pz) {
        Random rng = emitterRng;
        for (Vec3i p : game.fire.burningCells()) {
            if (p.distSq(px, py, pz) < FIRE_PARTICLE_RANGE * FIRE_PARTICLE_RANGE) {
                game.particles.smoke(p.x() + 0.5f, p.y() + 1f, p.z() + 0.5f, 1f);
                game.particles.flame(p.x() + 0.5f, p.y() + 0.2f, p.z() + 0.5f);
                if (rng.nextFloat() < FIRE_EMBER_CHANCE) {
                    game.particles.ember(p.x() + 0.5f, p.y() + 0.6f, p.z() + 0.5f);
                }
            }
        }
        for (Vec3i p : game.world.campfireFuel.keySet()) {
            if (p.distSq(px, py, pz) < CAMPFIRE_PARTICLE_RANGE * CAMPFIRE_PARTICLE_RANGE
                    && game.world.getBlock(p.x(), p.y(), p.z()) == BlockType.CAMPFIRE) {
                game.particles.smoke(p.x() + 0.5f, p.y() + 0.7f, p.z() + 0.5f, 0.6f);
                game.particles.flame(p.x() + 0.5f, p.y() + 0.15f, p.z() + 0.5f);
                if (rng.nextFloat() < CAMPFIRE_EMBER_CHANCE) {
                    game.particles.ember(p.x() + 0.5f, p.y() + 0.4f, p.z() + 0.5f);
                }
            }
        }
    }

    /** The active distress beacon sheds a bounded stream of cyan energy motes. */
    private void emitBeacon(float px, float py, float pz) {
        if (game.world.beaconStage < 3 || game.world.beaconPos == null
                || game.world.beaconPos.distSq(px, py, pz)
                        >= BEACON_PARTICLE_RANGE * BEACON_PARTICLE_RANGE) {
            return;
        }
        Vec3i bp = game.world.beaconPos;
        game.particles.beaconMote(bp.x() + 0.5f, bp.y() + 0.3f, bp.z() + 0.5f);
        if (emitterRng.nextFloat() < BEACON_SECOND_MOTE_CHANCE) {
            game.particles.beaconMote(bp.x() + 0.5f, bp.y() + 1.2f, bp.z() + 0.5f);
        }
    }

    /** Cold breath and smoke-poisoned coughing. */
    private void emitPlayerEffects() {
        breathTimer -= EMITTER_INTERVAL;
        if (breathTimer <= 0 && game.player.envTemp < BREATH_TEMP && !game.player.inWater) {
            breathTimer = BREATH_INTERVAL;
            Vector3f f = game.camera.front();
            game.particles.breath(game.camera.position.x, game.camera.position.y - 0.15f,
                    game.camera.position.z, f.x, f.z);
        }
        coughTimer -= EMITTER_INTERVAL;
        if (coughTimer <= 0 && game.player.has(Affliction.SMOKE)) {
            coughTimer = COUGH_INTERVAL;
            game.audio.playCough();
        }
    }

    /** Recomputes the six ambient audio channel gains from world state. */
    void updateAmbienceMix() {
        boolean snow = game.weather.effective() == WeatherSystem.Weather.SNOW;
        float rainGain = game.weather.isPrecip() && !snow
                ? game.weather.intensity()
                        * (game.player.exposedToSky ? 1f : RAIN_INDOOR_GAIN)
                : 0f;

        float windGain = 0f;
        if (game.weather.isStormy()) {
            windGain = STORM_WIND_GAIN;
        } else if (snow || game.player.pos.y > WINDY_ALTITUDE) {
            windGain = EXPOSED_WIND_GAIN;
        } else if (game.weather.effective() == WeatherSystem.Weather.CLOUDY) {
            windGain = CLOUDY_WIND_GAIN;
        }

        float fireHeat = game.player.nearFireHeat(game);
        float fireGain = fireHeat > FIRE_MIN_AUDIBLE_HEAT
                ? Math.min(1f, fireHeat / FIRE_GAIN_FULL_HEAT) : 0f;

        int surface = game.world.surfaceHeight((int) game.player.pos.x, (int) game.player.pos.z);
        float caveGain = game.player.pos.y < surface - CAVE_DEPTH ? CAVE_GAIN : 0f;

        boolean cricketBiome = game.player.biome == Biome.MEADOW
                || game.player.biome == Biome.PINE_FOREST
                || game.player.biome == Biome.MARSH;
        float cricketGain = game.time.isNight() && !game.weather.isPrecip()
                && caveGain == 0 && cricketBiome ? CRICKET_GAIN : 0f;

        float beaconGain = 0f;
        if (game.world.beaconStage >= 3 && game.world.beaconPos != null) {
            double d = game.world.beaconPos.distSq(
                    game.player.pos.x, game.player.pos.y, game.player.pos.z);
            if (d < BEACON_AUDIO_RANGE * BEACON_AUDIO_RANGE) {
                beaconGain = (float) (1.0 - Math.sqrt(d) / BEACON_AUDIO_RANGE);
            }
        }
        game.audio.setAmbience(rainGain, windGain, fireGain, caveGain, cricketGain, beaconGain);
    }
}
