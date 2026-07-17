package com.veylon.entity;

import com.veylon.Game;
import com.veylon.item.EquipSlot;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.simulation.ShelterSystem;
import com.veylon.util.MathUtil;
import com.veylon.world.Biome;
import com.veylon.world.World;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** The player: physics body plus the full survival-needs simulation. */
public class Player extends Entity {

    public final Inventory inventory = new Inventory(36);
    /** Worn gear, indexed by {@link EquipSlot#ordinal()}. */
    public final ItemStack[] equipment = new ItemStack[EquipSlot.values().length];
    public int hotbarSel;

    public float hunger = 100;
    public float thirst = 100;
    public float stamina = 100;
    public float bodyTemp = 37f;
    public float fatigue = 0;
    public float wetness = 0;
    /** Nutrition balance 0..100: animal protein and plant vitamins. */
    public float protein = 70;
    public float vitamins = 70;

    /** Active medical conditions -> remaining seconds. */
    public final Map<Affliction, Float> afflictions = new EnumMap<>(Affliction.class);
    /** Blueprints decoded at a map table. */
    public final Set<String> blueprints = new HashSet<>();

    public boolean sprinting;
    public boolean crouching;
    /** Cached environment readings, updated each fast tick. */
    public float envTemp = 15f;
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
        width = 0.6f;
        height = 1.8f;
        maxHealth = 100;
        health = 100;
    }

    public float eyeHeight() {
        return crouching ? 1.32f : 1.62f;
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
        return Math.min(0.9f, sum);
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
        float cap = 28f;
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
                w += s.type.weight * 0.5f; // worn gear counts half
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
            mul *= 0.62f;
        } else if (enc > 0.8f) {
            mul *= 0.85f;
        }
        if (has(Affliction.SPRAIN)) {
            mul *= 0.6f;
        }
        if (fatigue > 90) {
            mul *= 0.85f;
        }
        return mul;
    }

    public boolean canSprint() {
        return stamina > 1 && hunger > 5 && !has(Affliction.SPRAIN) && encumbrance() <= 1f;
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
        float reduced = Math.max(dmg * 0.25f, dmg - armor());
        hurt(reduced, false);
        damageFlash = 1f;
        for (ItemStack s : equipment) {
            if (s != null && s.type.armor > 0 && s.type.hasDurability()) {
                s.durability -= 1.5f;
                if (s.durability <= 0) {
                    g.log("Your " + s.type.displayName + " is destroyed!");
                    equipment[s.type.equipSlot.ordinal()] = null;
                }
            }
        }
        if (canBleed && Math.random() < 0.35) {
            if (!has(Affliction.BLEEDING)) {
                g.log("You are BLEEDING! Bandage the wound before it festers.");
            }
            addAffliction(Affliction.BLEEDING, 45 + (float) Math.random() * 30);
            woundClean = false;
        }
    }

    @Override
    protected void onLanded(float fall) {
        pendingFallDamage = (fall - 3.5f) * 4.5f;
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
        exposedToSky = pos.y + 1.6f >= surface;

        damageFlash = Math.max(0, damageFlash - 1.6f * dt);
        noise = Math.max(0, noise - 0.5f * dt);

        if (pendingFallDamage > 0) {
            hurtPhysical(g, pendingFallDamage, false);
            g.log("You hit the ground hard (-" + (int) pendingFallDamage + " HP)");
            g.audio.playHurt();
            if (pendingFallDamage > 9 && Math.random() < 0.5 && !has(Affliction.SPRAIN)) {
                addAffliction(Affliction.SPRAIN, 150 + (float) Math.random() * 90);
                g.log("You SPRAINED your leg in the fall. Splint it or hobble.");
            }
            pendingFallDamage = 0;
        }

        // Hunger / thirst drains.
        float hungerRate = 0.045f + (sprinting ? 0.05f : 0f) + (encumbrance() > 0.8f ? 0.02f : 0f);
        hunger = Math.max(0, hunger - hungerRate * dt);
        float thirstRate = 0.075f * (envTemp > 30 ? 1.6f : 1f) * g.events.thirstMul();
        if (has(Affliction.INFECTION) || has(Affliction.FOOD_POISONING)) {
            thirstRate *= 1.6f;
        }
        thirst = Math.max(0, thirst - thirstRate * dt);

        // Nutrition balance drains slowly; starving drains it faster.
        float nutriDrain = 0.018f * dt * (hunger <= 0 ? 2.5f : 1f);
        protein = Math.max(0, protein - nutriDrain);
        vitamins = Math.max(0, vitamins - nutriDrain * 1.1f);

        // Stamina regeneration (drain happens in movement code).
        if (!sprinting) {
            float regen = 7f;
            if (hunger < 20) regen *= 0.35f;
            if (thirst < 15) regen *= 0.35f;
            if (bodyTemp < 34.5f) regen *= 0.5f;
            if (protein < 25) regen *= 0.6f;
            if (has(Affliction.FOOD_POISONING) || has(Affliction.SICKNESS)) regen *= 0.5f;
            if (has(Affliction.BURN)) regen *= 0.65f;
            if (has(Affliction.SMOKE)) regen *= 0.55f;
            regen *= 1f - fatigue / 250f;
            float maxStam = maxStamina();
            stamina = Math.min(maxStam, stamina + regen * dt);
        }

        // Wetness: rain blocked partially by gear and shelter.
        if (inWater) {
            wetness = 1f;
        } else if (g.weather.isPrecip() && exposedToSky) {
            float gain = 0.05f * g.weather.intensity() * (1f - wetResistance())
                    * (1f - shelter.coverage() * 0.85f);
            wetness = Math.min(1f, wetness + gain * dt);
        } else {
            float dry = 0.025f + (envTemp > 25 ? 0.03f : 0f) + (nearFireHeat(g) > 5 ? 0.10f : 0f);
            wetness = Math.max(0, wetness - dry * dt);
        }

        // Body temperature drifts toward an environment-driven target.
        float insul = insulation();
        float effectiveEnv = envTemp;
        if (envTemp < 18) {
            effectiveEnv += Math.min(insul * 0.8f, 18 - envTemp);
            // Wind chill in storms when not behind walls.
            if (g.weather.isStormy() && exposedToSky) {
                effectiveEnv -= 4f * (1f - shelter.enclosure());
            }
        }
        float target = 37f + (effectiveEnv - 16f) * 0.22f
                - wetness * 6.5f * (effectiveEnv < 18 ? 1f : 0.3f);
        if (has(Affliction.INFECTION)) {
            target += 2.2f; // fever
        }
        bodyTemp = MathUtil.approach(bodyTemp, target, (0.075f + wetness * 0.09f) * dt);
        bodyTemp = MathUtil.clamp(bodyTemp, 25f, 45f);

        if (bodyTemp < 33f) {
            health -= (33f - bodyTemp) * 0.12f * dt;
        } else if (bodyTemp > 40.5f) {
            health -= (bodyTemp - 40.5f) * 0.15f * dt;
            thirst = Math.max(0, thirst - 0.1f * dt);
        }

        if (hunger <= 0) health -= 0.5f * dt;
        if (thirst <= 0) health -= 0.8f * dt;

        tickAfflictions(g, dt);

        // Scent: raw/spoiled meat carried and open wounds attract predators.
        int meatCarried = inventory.count(com.veylon.item.ItemType.RAW_MEAT)
                + inventory.count(com.veylon.item.ItemType.SPOILED_MEAT);
        scent = Math.min(1f, meatCarried * 0.12f + (has(Affliction.BLEEDING) ? 0.5f : 0f));

        // Slow regeneration when well fed, hydrated and uninjured.
        boolean healthyEnough = !has(Affliction.BLEEDING) && !has(Affliction.INFECTION)
                && !has(Affliction.FOOD_POISONING);
        if (hunger > 70 && thirst > 60 && bodyTemp > 35 && health < maxHealth && healthyEnough) {
            float regen = 0.6f;
            if (protein > 60 && vitamins > 60) {
                regen = 1.0f; // balanced diet heals faster
            } else if (protein < 25 || vitamins < 25) {
                regen = 0.25f;
            }
            health = Math.min(maxHealth, health + regen * dt);
        }

        // Fatigue.
        boolean moving = Math.abs(vel.x) > 0.1f || Math.abs(vel.z) > 0.1f;
        fatigue += (moving ? 0.04f : 0.012f) * dt * (encumbrance() > 1f ? 1.6f : 1f);
        if (nearFireHeat(g) > 5 && !moving) {
            fatigue -= 0.6f * dt;
        }
        fatigue = MathUtil.clamp(fatigue, 0, 100);

        if (health <= 0 && !dead) {
            dead = true;
        }
        health = MathUtil.clamp(health, 0, maxHealth);
    }

    private void tickAfflictions(Game g, float dt) {
        var it = afflictions.entrySet().iterator();
        boolean bleedEnded = false;
        while (it.hasNext()) {
            var e = it.next();
            Affliction a = e.getKey();
            float left = e.getValue() - dt;
            switch (a) {
                case BLEEDING -> health -= 0.5f * dt;
                case INFECTION -> health -= 0.35f * dt;
                case BURN -> health -= 0.18f * dt;
                case FOOD_POISONING -> {
                    health -= 0.22f * dt;
                    hunger = Math.max(0, hunger - 0.06f * dt);
                }
                case SICKNESS -> health -= 0.15f * dt;
                case SMOKE -> health -= 0.10f * dt;
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
            if (!woundClean && Math.random() < 0.55) {
                addAffliction(Affliction.INFECTION, 240 + (float) Math.random() * 120);
                g.log("The untreated wound has become INFECTED. Fever sets in...");
            } else {
                g.log("The bleeding stopped on its own.");
            }
        }

        // Smoke exposure converts to the smoke affliction.
        smokeExposure = MathUtil.clamp(smokeExposure, 0, 100);
        if (smokeExposure >= 60 && !has(Affliction.SMOKE)) {
            addAffliction(Affliction.SMOKE, 30);
            g.log("Smoke fills your lungs - get to fresh air or ventilate the shelter!");
        }
        if (!has(Affliction.SMOKE)) {
            smokeExposure = Math.max(0, smokeExposure - 4f * dt);
        } else if (smokeExposure < 20) {
            cure(Affliction.SMOKE);
        } else {
            smokeExposure = Math.max(0, smokeExposure - 2f * dt);
        }
    }

    public float maxStamina() {
        float max = 100;
        if (fatigue > 70) {
            max -= (fatigue - 70) * 1.2f;
        }
        if (vitamins < 25) {
            max -= 15;
        }
        return Math.max(30, max);
    }

    /** Heat bonus in degrees from nearby campfires/torches (via temperature system cache). */
    public float nearFireHeat(Game g) {
        return g.temperature.fireHeatAt(pos.x, pos.y + 0.9f, pos.z);
    }
}
