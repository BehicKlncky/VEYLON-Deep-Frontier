package com.veylon.entity;

/**
 * Tuning values for the player's body and survival-needs simulation.
 *
 * <p>Rates suffixed {@code _PER_SECOND} are multiplied by the tick delta, so
 * they read directly as "units lost per second of play". Values in degrees are
 * Celsius; distances are in blocks; durations are in seconds.
 *
 * <p>These were extracted verbatim from {@link Player}; changing one is a
 * gameplay change, not a refactor.
 */
public final class PlayerConstants {

    private PlayerConstants() {
    }

    // ------------------------------------------------------------------
    // Body
    // ------------------------------------------------------------------

    public static final int INVENTORY_SLOTS = 36;
    public static final float BODY_WIDTH = 0.6f;
    public static final float BODY_HEIGHT = 1.8f;
    public static final float MAX_HEALTH = 100f;
    public static final float EYE_HEIGHT_STANDING = 1.62f;
    public static final float EYE_HEIGHT_CROUCHED = 1.32f;
    /** Ceiling shared by hunger, thirst, stamina and the nutrition meters. */
    public static final float MAX_NEED = 100f;
    /** Hunger, thirst and stamina all start full; nutrition starts here. */
    public static final float STARTING_NUTRITION = 70f;
    public static final float NORMAL_BODY_TEMP = 37f;
    public static final float STARTING_ENV_TEMP = 15f;

    // ------------------------------------------------------------------
    // Gear-derived stats
    // ------------------------------------------------------------------

    /** Worn gear can never block more than this fraction of rain. */
    public static final float MAX_WET_RESISTANCE = 0.9f;
    /** Carry capacity in weight units before any equipment bonus. */
    public static final float BASE_CARRY_CAPACITY = 28f;
    /** Worn gear counts half its weight against carry capacity. */
    public static final float WORN_GEAR_WEIGHT_FRACTION = 0.5f;

    // ------------------------------------------------------------------
    // Movement
    // ------------------------------------------------------------------

    /** Encumbrance above this (but at or below 1.0) is a mild slowdown. */
    public static final float HEAVY_LOAD_ENCUMBRANCE = 0.8f;
    public static final float HEAVY_LOAD_SPEED_MULT = 0.85f;
    /** Encumbrance above 1.0 means overloaded. */
    public static final float OVERLOADED_SPEED_MULT = 0.62f;
    public static final float SPRAIN_SPEED_MULT = 0.6f;
    public static final float EXHAUSTED_FATIGUE = 90f;
    public static final float EXHAUSTED_SPEED_MULT = 0.85f;
    public static final float SPRINT_MIN_STAMINA = 1f;
    public static final float SPRINT_MIN_HUNGER = 5f;
    /** Speed below which the player counts as standing still, for fatigue. */
    public static final float MOVEMENT_EPSILON = 0.1f;

    // ------------------------------------------------------------------
    // Incoming damage
    // ------------------------------------------------------------------

    /** Armor can never reduce a hit below this fraction of its raw damage. */
    public static final float MIN_DAMAGE_FRACTION = 0.25f;
    public static final float ARMOR_DURABILITY_PER_HIT = 1.5f;
    public static final double BLEED_CHANCE = 0.35;
    public static final float BLEEDING_DURATION_MIN = 45f;
    /** Added to the minimum, scaled by a random 0..1. */
    public static final float BLEEDING_DURATION_RANGE = 30f;

    /** Falls shorter than this do no damage. */
    public static final float FALL_SAFE_DISTANCE = 3.5f;
    public static final float FALL_DAMAGE_PER_BLOCK = 4.5f;
    /** Fall damage above this can also sprain a leg. */
    public static final float SPRAIN_DAMAGE_THRESHOLD = 9f;
    public static final double SPRAIN_CHANCE = 0.5;
    public static final float SPRAIN_DURATION_MIN = 150f;
    public static final float SPRAIN_DURATION_RANGE = 90f;

    public static final float DAMAGE_FLASH_DECAY_PER_SECOND = 1.6f;
    public static final float NOISE_DECAY_PER_SECOND = 0.5f;

    // ------------------------------------------------------------------
    // Hunger, thirst and nutrition
    // ------------------------------------------------------------------

    public static final float BASE_HUNGER_DRAIN_PER_SECOND = 0.045f;
    public static final float SPRINT_HUNGER_DRAIN_PER_SECOND = 0.05f;
    public static final float ENCUMBERED_HUNGER_DRAIN_PER_SECOND = 0.02f;

    public static final float BASE_THIRST_DRAIN_PER_SECOND = 0.075f;
    /** Environment temperature above which thirst accelerates. */
    public static final float HOT_WEATHER_TEMP = 30f;
    public static final float HOT_WEATHER_THIRST_MULT = 1.6f;
    /** Infection and food poisoning both dehydrate faster. */
    public static final float ILLNESS_THIRST_MULT = 1.6f;

    public static final float NUTRITION_DRAIN_PER_SECOND = 0.018f;
    /** Nutrition burns faster once hunger has bottomed out. */
    public static final float STARVING_NUTRITION_MULT = 2.5f;
    /** Vitamins drain slightly faster than protein. */
    public static final float VITAMIN_DRAIN_MULT = 1.1f;

    public static final float STARVATION_DAMAGE_PER_SECOND = 0.5f;
    public static final float DEHYDRATION_DAMAGE_PER_SECOND = 0.8f;

    // ------------------------------------------------------------------
    // Stamina
    // ------------------------------------------------------------------

    public static final float BASE_STAMINA_REGEN_PER_SECOND = 7f;
    public static final float LOW_HUNGER = 20f;
    public static final float LOW_HUNGER_REGEN_MULT = 0.35f;
    public static final float LOW_THIRST = 15f;
    public static final float LOW_THIRST_REGEN_MULT = 0.35f;
    public static final float COLD_REGEN_BODY_TEMP = 34.5f;
    public static final float COLD_REGEN_MULT = 0.5f;
    public static final float LOW_PROTEIN = 25f;
    public static final float LOW_PROTEIN_REGEN_MULT = 0.6f;
    public static final float ILLNESS_REGEN_MULT = 0.5f;
    public static final float BURN_REGEN_MULT = 0.65f;
    public static final float SMOKE_REGEN_MULT = 0.55f;
    /** Regen scales by {@code 1 - fatigue / this}. */
    public static final float FATIGUE_REGEN_DIVISOR = 250f;

    /** Fatigue above this starts eating into the stamina ceiling. */
    public static final float FATIGUE_STAMINA_THRESHOLD = 70f;
    public static final float FATIGUE_STAMINA_PENALTY = 1.2f;
    /** Vitamins below this cost a flat slice of the stamina ceiling. */
    public static final float LOW_VITAMINS = 25f;
    public static final float LOW_VITAMIN_STAMINA_PENALTY = 15f;
    /** The stamina ceiling never drops below this, however wrecked the player is. */
    public static final float MIN_MAX_STAMINA = 30f;

    // ------------------------------------------------------------------
    // Wetness
    // ------------------------------------------------------------------

    public static final float RAIN_WETNESS_PER_SECOND = 0.05f;
    /** Fraction of rain that full shelter coverage blocks. */
    public static final float SHELTER_RAIN_BLOCK = 0.85f;
    public static final float BASE_DRY_PER_SECOND = 0.025f;
    public static final float WARM_DRY_TEMP = 25f;
    public static final float WARM_DRY_BONUS_PER_SECOND = 0.03f;
    public static final float FIRE_DRY_BONUS_PER_SECOND = 0.10f;
    /** Fire heat in degrees above which drying and rest bonuses apply. */
    public static final float FIRE_HEAT_THRESHOLD = 5f;

    // ------------------------------------------------------------------
    // Body temperature
    // ------------------------------------------------------------------

    /** Environment temperature below which insulation starts to matter. */
    public static final float COLD_ENV_TEMP = 18f;
    /** Fraction of insulation that counts toward the effective temperature. */
    public static final float INSULATION_EFFECTIVENESS = 0.8f;
    /** Degrees lost to wind chill in an unsheltered storm. */
    public static final float STORM_WIND_CHILL = 4f;
    /** Environment temperature at which body temperature sits at normal. */
    public static final float TEMP_NEUTRAL_ENV = 16f;
    /** Degrees of body-temperature shift per degree away from neutral. */
    public static final float ENV_TEMP_INFLUENCE = 0.22f;
    /** Degrees of chill at full wetness in the cold. */
    public static final float WETNESS_CHILL = 6.5f;
    /** Wetness chills far less once the air is warm. */
    public static final float WETNESS_CHILL_WARM_MULT = 0.3f;
    /** Fever from an active infection, in degrees. */
    public static final float FEVER_TEMP_RISE = 2.2f;
    public static final float TEMP_APPROACH_RATE = 0.075f;
    /** Wet skin reaches the target temperature faster. */
    public static final float TEMP_APPROACH_WET_BONUS = 0.09f;
    public static final float MIN_BODY_TEMP = 25f;
    public static final float MAX_BODY_TEMP = 45f;

    public static final float FREEZING_THRESHOLD = 33f;
    public static final float FREEZING_DAMAGE_PER_DEGREE = 0.12f;
    public static final float OVERHEAT_THRESHOLD = 40.5f;
    public static final float OVERHEAT_DAMAGE_PER_DEGREE = 0.15f;
    public static final float OVERHEAT_THIRST_DRAIN_PER_SECOND = 0.1f;

    // ------------------------------------------------------------------
    // Health regeneration
    // ------------------------------------------------------------------

    public static final float REGEN_MIN_HUNGER = 70f;
    public static final float REGEN_MIN_THIRST = 60f;
    public static final float REGEN_MIN_BODY_TEMP = 35f;
    public static final float BASE_HEALTH_REGEN_PER_SECOND = 0.6f;
    /** Both nutrition meters above this heal faster. */
    public static final float BALANCED_DIET_THRESHOLD = 60f;
    public static final float BALANCED_DIET_REGEN_PER_SECOND = 1.0f;
    /** Either nutrition meter below this heals slower. */
    public static final float POOR_DIET_THRESHOLD = 25f;
    public static final float POOR_DIET_REGEN_PER_SECOND = 0.25f;

    // ------------------------------------------------------------------
    // Fatigue
    // ------------------------------------------------------------------

    public static final float MOVING_FATIGUE_PER_SECOND = 0.04f;
    public static final float IDLE_FATIGUE_PER_SECOND = 0.012f;
    public static final float OVERLOADED_FATIGUE_MULT = 1.6f;
    /** Resting still beside a fire recovers fatigue at this rate. */
    public static final float FIRE_REST_RECOVERY_PER_SECOND = 0.6f;
    public static final float MAX_FATIGUE = 100f;

    // ------------------------------------------------------------------
    // Afflictions
    // ------------------------------------------------------------------

    public static final float BLEEDING_DAMAGE_PER_SECOND = 0.5f;
    public static final float INFECTION_DAMAGE_PER_SECOND = 0.35f;
    public static final float BURN_DAMAGE_PER_SECOND = 0.18f;
    public static final float FOOD_POISONING_DAMAGE_PER_SECOND = 0.22f;
    public static final float FOOD_POISONING_HUNGER_DRAIN_PER_SECOND = 0.06f;
    public static final float SICKNESS_DAMAGE_PER_SECOND = 0.15f;
    public static final float SMOKE_DAMAGE_PER_SECOND = 0.10f;

    /** Chance a wound that clotted without a bandage turns septic. */
    public static final double INFECTION_FROM_UNCLEAN_WOUND_CHANCE = 0.55;
    public static final float INFECTION_DURATION_MIN = 240f;
    public static final float INFECTION_DURATION_RANGE = 120f;

    // ------------------------------------------------------------------
    // Smoke
    // ------------------------------------------------------------------

    public static final float MAX_SMOKE_EXPOSURE = 100f;
    /** Exposure at which the smoke affliction sets in. */
    public static final float SMOKE_AFFLICTION_THRESHOLD = 60f;
    public static final float SMOKE_AFFLICTION_SECONDS = 30f;
    /** Exposure below which an active smoke affliction clears. */
    public static final float SMOKE_CLEAR_THRESHOLD = 20f;
    public static final float SMOKE_DECAY_CLEAN_AIR_PER_SECOND = 4f;
    public static final float SMOKE_DECAY_AFFLICTED_PER_SECOND = 2f;

    // ------------------------------------------------------------------
    // Environment sampling
    // ------------------------------------------------------------------

    /** Height above the feet used to decide whether the sky is visible. */
    public static final float SKY_EXPOSURE_EYE_OFFSET = 1.6f;
    /** Height above the feet at which nearby fire heat is sampled. */
    public static final float FIRE_HEAT_SAMPLE_HEIGHT = 0.9f;
    /** Scent contributed by each raw or spoiled meat item carried. */
    public static final float SCENT_PER_MEAT = 0.12f;
    public static final float BLEEDING_SCENT = 0.5f;
}
