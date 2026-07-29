package com.veylon.simulation;

/**
 * World event scheduling, durations and the modifiers active events apply.
 *
 * <p>The {@code *_ROLL} values are cumulative upper bounds on a single random
 * 0..1, tested in ascending order — a season bias shifts the roll, not the
 * bounds. The gap between one bound and the previous is that event's share of
 * the probability space, so they must stay in ascending order and only the
 * gaps are meaningful. Anything above {@link #LONE_METEOR_ROLL} means no event
 * fires this tick.
 *
 * <p>Extracted verbatim from {@link EventSystem}; changing one is a gameplay
 * change, not a refactor.
 */
public final class EventConstants {

    private EventConstants() {
    }

    // ------------------------------------------------------------------
    // Scheduling
    // ------------------------------------------------------------------

    /** Seconds before the first event roll of a world. */
    public static final float INITIAL_COOLDOWN = 60;
    /** Minimum seconds between events once one has fired. */
    public static final int COOLDOWN_MIN = 70;
    public static final int COOLDOWN_RANGE = 60;
    /** A lone meteor imposes a longer cooldown than a normal event. */
    public static final int METEOR_COOLDOWN_MIN = 100;
    public static final int METEOR_COOLDOWN_RANGE = 80;

    /** Seasons shift the roll, making their characteristic events likelier. */
    public static final float COLD_SEASON_BIAS = 0.08f;
    public static final float DRY_SEASON_BIAS = 0.08f;
    public static final float WET_SEASON_BIAS = 0.06f;

    // ------------------------------------------------------------------
    // Roll bounds, ascending
    // ------------------------------------------------------------------

    public static final float COLD_SNAP_ROLL = 0.06f;
    public static final float HEAT_WAVE_ROLL = 0.10f;
    public static final float DROUGHT_ROLL = 0.14f;
    public static final float BERRY_BLOOM_ROLL = 0.19f;
    public static final float PREDATOR_MIGRATION_ROLL = 0.23f;
    public static final float TRADER_VISIT_ROLL = 0.27f;
    public static final float TOXIC_FOG_ROLL = 0.30f;
    public static final float ASHFALL_ROLL = 0.325f;
    public static final float METEOR_SHOWER_ROLL = 0.345f;
    public static final float PREDATOR_RAID_ROLL = 0.365f;
    public static final float SCAVENGER_RAID_ROLL = 0.385f;
    public static final float NPC_ILLNESS_ROLL = 0.405f;
    public static final float LONE_METEOR_ROLL = 0.43f;

    // ------------------------------------------------------------------
    // Preconditions
    // ------------------------------------------------------------------

    /** Average soil moisture below which a drought can start. */
    public static final float DROUGHT_MAX_MOISTURE = 0.55f;
    /** Average soil moisture above which a berry bloom can start. */
    public static final float BERRY_BLOOM_MIN_MOISTURE = 0.45f;
    /** Camp trust above which a trader will visit. */
    public static final int TRADER_MIN_TRUST = 20;

    // ------------------------------------------------------------------
    // Durations, in seconds
    // ------------------------------------------------------------------

    public static final int COLD_SNAP_SECONDS_MIN = 120;
    public static final int COLD_SNAP_SECONDS_RANGE = 120;
    public static final int HEAT_WAVE_SECONDS_MIN = 120;
    public static final int HEAT_WAVE_SECONDS_RANGE = 120;
    public static final int DROUGHT_SECONDS_MIN = 180;
    public static final int DROUGHT_SECONDS_RANGE = 120;
    public static final int BERRY_BLOOM_SECONDS_MIN = 150;
    public static final int BERRY_BLOOM_SECONDS_RANGE = 120;
    public static final int PREDATOR_MIGRATION_SECONDS = 180;
    public static final int TRADER_VISIT_SECONDS = 240;
    public static final int TOXIC_FOG_SECONDS_MIN = 90;
    public static final int TOXIC_FOG_SECONDS_RANGE = 80;
    public static final int ASHFALL_SECONDS_MIN = 120;
    public static final int ASHFALL_SECONDS_RANGE = 100;
    public static final int METEOR_SHOWER_SECONDS_MIN = 70;
    public static final int METEOR_SHOWER_SECONDS_RANGE = 40;
    public static final int PREDATOR_RAID_SECONDS = 90;
    public static final int SCAVENGER_RAID_SECONDS = 110;
    public static final int NPC_ILLNESS_SECONDS = 600;
    public static final int STORM_FRONT_SECONDS = 90;
    public static final int FOREST_FIRE_SECONDS = 60;
    public static final int METEOR_SHARD_SECONDS = 30;

    /** Alert the camp gains when a storm front arrives. */
    public static final int STORM_ALERT_INCREASE = 15;

    // ------------------------------------------------------------------
    // Meteors
    // ------------------------------------------------------------------

    /** Seconds until the first shard once a shower starts. */
    public static final float METEOR_SHOWER_FIRST_SHARD = 5;
    /** Minimum seconds between shards during a shower. */
    public static final int METEOR_INTERVAL_MIN = 15;
    public static final int METEOR_INTERVAL_RANGE = 15;
    /** Blocks from the player a shard can land. */
    public static final double METEOR_DIST_MIN = 25;
    public static final double METEOR_DIST_RANGE = 35;
    /** Radius of the carved crater, and the squared cutoff shaping it. */
    public static final int CRATER_RADIUS = 2;
    public static final int CRATER_DEPTH = 1;
    public static final int CRATER_HEIGHT = 2;
    public static final int CRATER_SHAPE_CUTOFF = 5;

    // ------------------------------------------------------------------
    // Spawns
    // ------------------------------------------------------------------

    public static final int MIGRATION_WOLVES = 2;
    /** Blocks from the player that migrating wolves appear. */
    public static final float MIGRATION_SPAWN_DIST = 55;
    /** Hunger a migrating wolf spawns with; higher means bolder. */
    public static final float MIGRATION_WOLF_HUNGER = 70;

    public static final int CAMP_RAID_WOLVES = 3;
    /** Blocks from the camp centre that raiding wolves appear. */
    public static final int CAMP_WOLF_SPAWN_DIST = 18;
    public static final float CAMP_WOLF_HUNGER = 90;

    public static final int RAIDERS_MIN = 2;
    public static final int RAIDERS_RANGE = 2;
    /** Blocks from the camp centre that scavengers appear. */
    public static final int RAIDER_SPAWN_DIST = 24;
    /** Fallback offset when the preferred spawn ring is unloaded. */
    public static final int RAIDER_FALLBACK_OFFSET = 20;
    public static final float RAIDER_LEAVE_SECONDS = 90;
    public static final float RAIDER_HEALTH = 28;

    /** Blocks from the player that a visiting trader appears. */
    public static final int TRADER_SPAWN_DIST = 35;
    /** Fallback offset when the preferred spawn ring is unloaded. */
    public static final int TRADER_FALLBACK_OFFSET = 6;
    public static final float TRADER_LEAVE_SECONDS = 240;

    // ------------------------------------------------------------------
    // Modifiers applied while active
    // ------------------------------------------------------------------

    /** Degrees C a cold snap subtracts and a heat wave adds. */
    public static final float TEMP_SWING = 12;
    public static final float HEAT_WAVE_THIRST_MULT = 1.6f;
    public static final float DROUGHT_GROWTH_MULT = 0.3f;
    public static final float ASHFALL_GROWTH_MULT = 0.5f;
    public static final float BERRY_BLOOM_MULT = 4f;
    public static final float DROUGHT_FIRE_SPREAD_MULT = 1.9f;
    public static final float ASHFALL_SKYLIGHT_MULT = 0.7f;
    /** Extra simultaneous wolves permitted during each predator event. */
    public static final int MIGRATION_WOLF_CAP_BONUS = 4;
    public static final int RAID_WOLF_CAP_BONUS = 3;
}
