package com.veylon.entity;

import com.veylon.Game;
import com.veylon.item.EquipSlot;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.simulation.ShelterSystem;
import com.veylon.util.MathUtil;
import com.veylon.world.Biome;
import com.veylon.world.World;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.veylon.entity.PlayerConstants.*;

/** The player: physics body plus the full survival-needs simulation. */
public class Player extends Entity {

    public final Inventory inventory = new Inventory(INVENTORY_SLOTS);
    /** Worn gear, indexed by {@link EquipSlot#ordinal()}. */
    public final ItemStack[] equipment = new ItemStack[EquipSlot.values().length];
    public int hotbarSel;

    public float hunger = MAX_NEED;
    public float thirst = MAX_NEED;
    public float stamina = MAX_NEED;
    public float bodyTemp = NORMAL_BODY_TEMP;
    public float fatigue = 0;
    public float wetness = 0;
    /** Nutrition balance 0..100: animal protein and plant vitamins. */
    public float protein = STARTING_NUTRITION;
    public float vitamins = STARTING_NUTRITION;

    /** Active medical conditions -> remaining seconds. */
    public final Map<Affliction, Float> afflictions = new EnumMap<>(Affliction.class);
    /** Blueprints decoded at a map table. */
    public final Set<String> blueprints = new HashSet<>();

    public boolean sprinting;
    public boolean crouching;
    /** Cached environment readings, updated each fast tick. */
    public float envTemp = STARTING_ENV_TEMP;
    public Biome biome = Biome.MEADOW;
    public boolean exposedToSky = true;
    /** Cached shelter evaluation (refreshed on the medium tick). */
    public ShelterSystem.Shelter shelter = ShelterSystem.OPEN;

    /** How loud the player has recently been, 0..1 (sprinting, mining). */
    public float noise;
    /** How strongly the player smells of blood/meat, 0..1. */
    public float scent;
    /** Red-flash feedback for the HUD; decays each frame. */
    public float damageFlash;
    /** Smoke buildup 0..100 from enclosed fires and Basalt-depth fumaroles. */
    public float smokeExposure;
    /** Set by antiseptic: the current wound won't get infected when it closes. */
    public boolean woundClean;

    private float pendingFallDamage;

    public Player(World world) {
        super(world);
        width = BODY_WIDTH;
        height = BODY_HEIGHT;
        maxHealth = MAX_HEALTH;
        health = MAX_HEALTH;
    }

    public float eyeHeight() {
        return crouching ? EYE_HEIGHT_CROUCHED : EYE_HEIGHT_STANDING;
    }

    public ItemStack selected() {
        return inventory.get(hotbarSel);
    }

    public ItemStack equipped(EquipSlot slot) {
        return equipment[slot.ordinal()];
    }

    // ------------------------------------------------------------------
    // Derived gear stats
    // ------------------------------------------------------------------

    public float insulation() {
        float sum = 0;
        for (ItemStack s : equipment) {
            if (s != null) {
                sum += s.type.insulation;
            }
        }
        return sum;
    }

    public float wetResistance() {
        float sum = 0;
        for (ItemStack s : equipment) {
            if (s != null) {
                sum += s.type.wetResist;
            }
        }
        return Math.min(MAX_WET_RESISTANCE, sum);
    }

    public float armor() {
        float sum = 0;
        for (ItemStack s : equipment) {
            if (s != null) {
                sum += s.type.armor * s.durabilityFrac();
            }
        }
        return sum;
    }

    public float carryCapacity() {
        float cap = BASE_CARRY_CAPACITY;
        for (ItemStack s : equipment) {
            if (s != null) {
                cap += s.type.carryBonus;
            }
        }
        return cap;
    }

    public float carriedWeight() {
        float w = inventory.totalWeight();
        for (ItemStack s : equipment) {
            if (s != null) {
                w += s.type.weight * WORN_GEAR_WEIGHT_FRACTION;
            }
        }
        return w;
    }

    /** 0..1+ load factor; above 1 the player is overloaded. */
    public float encumbrance() {
        return carriedWeight() / carryCapacity();
    }

    /** Movement speed multiplier from load and injuries. */
    public float moveSpeedMul() {
        float mul = 1f;
        float enc = encumbrance();
        if (enc > 1f) {
            mul *= OVERLOADED_SPEED_MULT;
        } else if (enc > HEAVY_LOAD_ENCUMBRANCE) {
            mul *= HEAVY_LOAD_SPEED_MULT;
        }
        if (has(Affliction.SPRAIN)) {
            mul *= SPRAIN_SPEED_MULT;
        }
        if (fatigue > EXHAUSTED_FATIGUE) {
            mul *= EXHAUSTED_SPEED_MULT;
        }
        return mul;
    }

    public boolean canSprint() {
        return stamina > SPRINT_MIN_STAMINA && hunger > SPRINT_MIN_HUNGER
                && !has(Affliction.SPRAIN) && encumbrance() <= 1f;
    }

    // ------------------------------------------------------------------
    // Afflictions
    // ------------------------------------------------------------------

    public boolean has(Affliction a) {
        return afflictions.containsKey(a);
    }

    public void addAffliction(Affliction a, float seconds) {
        afflictions.merge(a, seconds, Math::max);
    }

    public void cure(Affliction a) {
        afflictions.remove(a);
    }

    /** Damage with armor applied; wears down worn gear. */
    public void hurtPhysical(Game g, float dmg, boolean canBleed) {
        float reduced = Math.max(dmg * MIN_DAMAGE_FRACTION, dmg - armor());
        hurt(reduced, false);
        damageFlash = 1f;
        for (ItemStack s : equipment) {
            if (s != null && s.type.armor > 0 && s.type.hasDurability()) {
                s.durability -= ARMOR_DURABILITY_PER_HIT;
                if (s.durability <= 0) {
                    g.log("Your " + s.type.displayName + " is destroyed!");
                    equipment[s.type.equipSlot.ordinal()] = null;
                }
            }
        }
        if (canBleed && Math.random() < BLEED_CHANCE) {
            if (!has(Affliction.BLEEDING)) {
                g.log("You are BLEEDING! Bandage the wound before it festers.");
            }
            addAffliction(Affliction.BLEEDING,
                    BLEEDING_DURATION_MIN + (float) Math.random() * BLEEDING_DURATION_RANGE);
            woundClean = false;
        }
    }

    @Override
    protected void onLanded(float fall) {
        pendingFallDamage = (fall - FALL_SAFE_DISTANCE) * FALL_DAMAGE_PER_BLOCK;
    }

    // ------------------------------------------------------------------
    // Needs tick (20 Hz)
    // ------------------------------------------------------------------

    /** Survival needs update; called on the fast tick (20 Hz). */
    public void tickNeeds(Game g, float dt) {
        int bx = (int) Math.floor(pos.x), bz = (int) Math.floor(pos.z);
        biome = world.biomeAt(bx, bz);
        envTemp = g.temperature.envTempAt(g, pos.x, pos.y, pos.z);
        int surface = 0;
        var chunk = world.getChunk(Math.floorDiv(bx, 16), Math.floorDiv(bz, 16));
        if (chunk != null) {
            surface = chunk.height(Math.floorMod(bx, 16), Math.floorMod(bz, 16));
        }
        exposedToSky = pos.y + SKY_EXPOSURE_EYE_OFFSET >= surface;

        damageFlash = Math.max(0, damageFlash - DAMAGE_FLASH_DECAY_PER_SECOND * dt);
        noise = Math.max(0, noise - NOISE_DECAY_PER_SECOND * dt);

        applyPendingFallDamage(g);
        tickHungerAndThirst(g, dt);
        tickStamina(dt);
        tickWetness(g, dt);
        tickBodyTemperature(g, dt);

        if (hunger <= 0) health -= STARVATION_DAMAGE_PER_SECOND * dt;
        if (thirst <= 0) health -= DEHYDRATION_DAMAGE_PER_SECOND * dt;

        tickAfflictions(g, dt);
        updateScent();
        tickHealthRegen(dt);
        tickFatigue(g, dt);

        if (health <= 0 && !dead) {
            dead = true;
        }
        health = MathUtil.clamp(health, 0, maxHealth);
    }

    private void applyPendingFallDamage(Game g) {
        if (pendingFallDamage <= 0) {
            return;
        }
        hurtPhysical(g, pendingFallDamage, false);
        g.log("You hit the ground hard (-" + (int) pendingFallDamage + " HP)");
        g.audio.playHurt();
        if (pendingFallDamage > SPRAIN_DAMAGE_THRESHOLD && Math.random() < SPRAIN_CHANCE
                && !has(Affliction.SPRAIN)) {
            addAffliction(Affliction.SPRAIN,
                    SPRAIN_DURATION_MIN + (float) Math.random() * SPRAIN_DURATION_RANGE);
            g.log("You SPRAINED your leg in the fall. Splint it or hobble.");
        }
        pendingFallDamage = 0;
    }

    private void tickHungerAndThirst(Game g, float dt) {
        float hungerRate = BASE_HUNGER_DRAIN_PER_SECOND
                + (sprinting ? SPRINT_HUNGER_DRAIN_PER_SECOND : 0f)
                + (encumbrance() > HEAVY_LOAD_ENCUMBRANCE
                        ? ENCUMBERED_HUNGER_DRAIN_PER_SECOND : 0f);
        hunger = Math.max(0, hunger - hungerRate * dt);

        float thirstRate = BASE_THIRST_DRAIN_PER_SECOND
                * (envTemp > HOT_WEATHER_TEMP ? HOT_WEATHER_THIRST_MULT : 1f)
                * g.events.thirstMul();
        if (has(Affliction.INFECTION) || has(Affliction.FOOD_POISONING)) {
            thirstRate *= ILLNESS_THIRST_MULT;
        }
        thirst = Math.max(0, thirst - thirstRate * dt);

        // Nutrition balance drains slowly; starving drains it faster.
        float nutriDrain = NUTRITION_DRAIN_PER_SECOND * dt
                * (hunger <= 0 ? STARVING_NUTRITION_MULT : 1f);
        protein = Math.max(0, protein - nutriDrain);
        vitamins = Math.max(0, vitamins - nutriDrain * VITAMIN_DRAIN_MULT);
    }

    /** Stamina regeneration; the drain happens in the movement code. */
    private void tickStamina(float dt) {
        if (sprinting) {
            return;
        }
        float regen = BASE_STAMINA_REGEN_PER_SECOND;
        if (hunger < LOW_HUNGER) regen *= LOW_HUNGER_REGEN_MULT;
        if (thirst < LOW_THIRST) regen *= LOW_THIRST_REGEN_MULT;
        if (bodyTemp < COLD_REGEN_BODY_TEMP) regen *= COLD_REGEN_MULT;
        if (protein < LOW_PROTEIN) regen *= LOW_PROTEIN_REGEN_MULT;
        if (has(Affliction.FOOD_POISONING) || has(Affliction.SICKNESS)) {
            regen *= ILLNESS_REGEN_MULT;
        }
        if (has(Affliction.BURN)) regen *= BURN_REGEN_MULT;
        if (has(Affliction.SMOKE)) regen *= SMOKE_REGEN_MULT;
        regen *= 1f - fatigue / FATIGUE_REGEN_DIVISOR;
        stamina = Math.min(maxStamina(), stamina + regen * dt);
    }

    /** Wetness: rain blocked partially by gear and shelter. */
    private void tickWetness(Game g, float dt) {
        if (inWater) {
            wetness = 1f;
        } else if (g.weather.isPrecip() && exposedToSky) {
            float gain = RAIN_WETNESS_PER_SECOND * g.weather.intensity()
                    * (1f - wetResistance())
                    * (1f - shelter.coverage() * SHELTER_RAIN_BLOCK);
            wetness = Math.min(1f, wetness + gain * dt);
        } else {
            float dry = BASE_DRY_PER_SECOND
                    + (envTemp > WARM_DRY_TEMP ? WARM_DRY_BONUS_PER_SECOND : 0f)
                    + (nearFireHeat(g) > FIRE_HEAT_THRESHOLD ? FIRE_DRY_BONUS_PER_SECOND : 0f);
            wetness = Math.max(0, wetness - dry * dt);
        }
    }

    /** Body temperature drifts toward an environment-driven target. */
    private void tickBodyTemperature(Game g, float dt) {
        float insul = insulation();
        float effectiveEnv = envTemp;
        if (envTemp < COLD_ENV_TEMP) {
            effectiveEnv += Math.min(insul * INSULATION_EFFECTIVENESS, COLD_ENV_TEMP - envTemp);
            // Wind chill in storms when not behind walls.
            if (g.weather.isStormy() && exposedToSky) {
                effectiveEnv -= STORM_WIND_CHILL * (1f - shelter.enclosure());
            }
        }
        float target = NORMAL_BODY_TEMP + (effectiveEnv - TEMP_NEUTRAL_ENV) * ENV_TEMP_INFLUENCE
                - wetness * WETNESS_CHILL
                * (effectiveEnv < COLD_ENV_TEMP ? 1f : WETNESS_CHILL_WARM_MULT);
        if (has(Affliction.INFECTION)) {
            target += FEVER_TEMP_RISE; // fever
        }
        bodyTemp = MathUtil.approach(bodyTemp, target,
                (TEMP_APPROACH_RATE + wetness * TEMP_APPROACH_WET_BONUS) * dt);
        bodyTemp = MathUtil.clamp(bodyTemp, MIN_BODY_TEMP, MAX_BODY_TEMP);

        if (bodyTemp < FREEZING_THRESHOLD) {
            health -= (FREEZING_THRESHOLD - bodyTemp) * FREEZING_DAMAGE_PER_DEGREE * dt;
        } else if (bodyTemp > OVERHEAT_THRESHOLD) {
            health -= (bodyTemp - OVERHEAT_THRESHOLD) * OVERHEAT_DAMAGE_PER_DEGREE * dt;
            thirst = Math.max(0, thirst - OVERHEAT_THIRST_DRAIN_PER_SECOND * dt);
        }
    }

    /** Raw/spoiled meat carried and open wounds attract predators. */
    private void updateScent() {
        int meatCarried = inventory.count(ItemType.RAW_MEAT)
                + inventory.count(ItemType.SPOILED_MEAT);
        scent = Math.min(1f, meatCarried * SCENT_PER_MEAT
                + (has(Affliction.BLEEDING) ? BLEEDING_SCENT : 0f));
    }

    /** Slow regeneration when well fed, hydrated and uninjured. */
    private void tickHealthRegen(float dt) {
        boolean healthyEnough = !has(Affliction.BLEEDING) && !has(Affliction.INFECTION)
                && !has(Affliction.FOOD_POISONING);
        if (hunger > REGEN_MIN_HUNGER && thirst > REGEN_MIN_THIRST
                && bodyTemp > REGEN_MIN_BODY_TEMP && health < maxHealth && healthyEnough) {
            float regen = BASE_HEALTH_REGEN_PER_SECOND;
            if (protein > BALANCED_DIET_THRESHOLD && vitamins > BALANCED_DIET_THRESHOLD) {
                regen = BALANCED_DIET_REGEN_PER_SECOND; // balanced diet heals faster
            } else if (protein < POOR_DIET_THRESHOLD || vitamins < POOR_DIET_THRESHOLD) {
                regen = POOR_DIET_REGEN_PER_SECOND;
            }
            health = Math.min(maxHealth, health + regen * dt);
        }
    }

    private void tickFatigue(Game g, float dt) {
        boolean moving = Math.abs(vel.x) > MOVEMENT_EPSILON || Math.abs(vel.z) > MOVEMENT_EPSILON;
        fatigue += (moving ? MOVING_FATIGUE_PER_SECOND : IDLE_FATIGUE_PER_SECOND) * dt
                * (encumbrance() > 1f ? OVERLOADED_FATIGUE_MULT : 1f);
        if (nearFireHeat(g) > FIRE_HEAT_THRESHOLD && !moving) {
            fatigue -= FIRE_REST_RECOVERY_PER_SECOND * dt;
        }
        fatigue = MathUtil.clamp(fatigue, 0, MAX_FATIGUE);
    }

    private void tickAfflictions(Game g, float dt) {
        var it = afflictions.entrySet().iterator();
        boolean bleedEnded = false;
        while (it.hasNext()) {
            var e = it.next();
            Affliction a = e.getKey();
            float left = e.getValue() - dt;
            switch (a) {
                case BLEEDING -> health -= BLEEDING_DAMAGE_PER_SECOND * dt;
                case INFECTION -> health -= INFECTION_DAMAGE_PER_SECOND * dt;
                case BURN -> health -= BURN_DAMAGE_PER_SECOND * dt;
                case FOOD_POISONING -> {
                    health -= FOOD_POISONING_DAMAGE_PER_SECOND * dt;
                    hunger = Math.max(0, hunger - FOOD_POISONING_HUNGER_DRAIN_PER_SECOND * dt);
                }
                case SICKNESS -> health -= SICKNESS_DAMAGE_PER_SECOND * dt;
                case SMOKE -> health -= SMOKE_DAMAGE_PER_SECOND * dt;
                case SPRAIN -> {
                }
            }
            if (left <= 0) {
                it.remove();
                if (a == Affliction.BLEEDING) {
                    bleedEnded = true;
                } else {
                    g.log(a.displayName + " has passed.");
                }
            } else {
                e.setValue(left);
            }
        }
        // A wound that clotted on its own (never bandaged) risks infection.
        if (bleedEnded) {
            if (!woundClean && Math.random() < INFECTION_FROM_UNCLEAN_WOUND_CHANCE) {
                addAffliction(Affliction.INFECTION,
                        INFECTION_DURATION_MIN + (float) Math.random() * INFECTION_DURATION_RANGE);
                g.log("The untreated wound has become INFECTED. Fever sets in...");
            } else {
                g.log("The bleeding stopped on its own.");
            }
        }

        tickSmokeExposure(g, dt);
    }

    /** Smoke exposure converts to the smoke affliction. */
    private void tickSmokeExposure(Game g, float dt) {
        smokeExposure = MathUtil.clamp(smokeExposure, 0, MAX_SMOKE_EXPOSURE);
        if (smokeExposure >= SMOKE_AFFLICTION_THRESHOLD && !has(Affliction.SMOKE)) {
            addAffliction(Affliction.SMOKE, SMOKE_AFFLICTION_SECONDS);
            g.log("Smoke fills your lungs - get to fresh air or ventilate the shelter!");
        }
        if (!has(Affliction.SMOKE)) {
            smokeExposure = Math.max(0, smokeExposure - SMOKE_DECAY_CLEAN_AIR_PER_SECOND * dt);
        } else if (smokeExposure < SMOKE_CLEAR_THRESHOLD) {
            cure(Affliction.SMOKE);
        } else {
            smokeExposure = Math.max(0, smokeExposure - SMOKE_DECAY_AFFLICTED_PER_SECOND * dt);
        }
    }

    public float maxStamina() {
        float max = MAX_NEED;
        if (fatigue > FATIGUE_STAMINA_THRESHOLD) {
            max -= (fatigue - FATIGUE_STAMINA_THRESHOLD) * FATIGUE_STAMINA_PENALTY;
        }
        if (vitamins < LOW_VITAMINS) {
            max -= LOW_VITAMIN_STAMINA_PENALTY;
        }
        return Math.max(MIN_MAX_STAMINA, max);
    }

    /** Heat bonus in degrees from nearby campfires/torches (via temperature system cache). */
    public float nearFireHeat(Game g) {
        return g.temperature.fireHeatAt(pos.x, pos.y + FIRE_HEAT_SAMPLE_HEIGHT, pos.z);
    }
}
