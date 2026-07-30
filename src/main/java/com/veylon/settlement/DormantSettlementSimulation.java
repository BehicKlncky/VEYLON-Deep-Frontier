package com.veylon.settlement;

import com.veylon.Game;
import com.veylon.util.MathUtil;
import com.veylon.util.Noise;

import java.util.Random;

/**
 * Abstract simulation for settlements without live resident entities.
 * Handles food production, consumption, illness, morale and population
 * replenishment at an accelerated timestep (one simulated minute per fixed
 * step, drained from an accumulator).
 *
 * <p>Extracted from {@link SettlementManager} in v0.4.1 to improve
 * maintainability and testability of the dormant simulation logic. The rules
 * are unchanged; only their owner moved.
 *
 * <h2>Determinism</h2>
 *
 * <p>Two independent sources of randomness feed this simulation, and the split
 * is deliberate:
 *
 * <ul>
 *   <li>Per-resident illness and recovery rolls are derived from
 *       {@link Noise#mix} over the world seed, the settlement id, the
 *       settlement's own {@code dormantStep} and the resident index. They carry
 *       no stream state at all, so a settlement's dormant outcome does not
 *       depend on how many other settlements were simulated first — the
 *       order-independence the settlement layer relies on.</li>
 *   <li>Replenishment scheduling uses this class's own {@link Random}, seeded
 *       from {@link SettlementManager#setRandomSeed(long)} with a distinct
 *       salt. Sharing the manager's stream would couple the timing of new
 *       arrivals to every other draw the manager makes.</li>
 * </ul>
 */
public final class DormantSettlementSimulation {

    /** Simulated seconds drained from the accumulator per fixed step. */
    private static final float STEP_SECONDS = 60f;
    /** Fixed steps one slow tick may drain, so a long stall cannot spiral. */
    private static final int MAX_STEPS_PER_TICK = 20;

    /** Ceiling on every stockpile, so an unattended settlement cannot overflow. */
    private static final int STOCK_CAP = 999;
    /** Seconds between replenishment attempts: base plus a random spread. */
    private static final int REPLENISH_SECONDS_MIN = 420;
    private static final int REPLENISH_SECONDS_RANGE = 300;

    // Conditions a settlement must meet before it takes in a newcomer.
    private static final int REPLENISH_MIN_FOOD = 6;
    private static final int REPLENISH_MIN_MEDICINE = 1;
    private static final float REPLENISH_MIN_MORALE = 35;
    private static final float REPLENISH_MAX_ALERT = 60;
    /** Food a newcomer costs to take in. */
    private static final int REPLENISH_FOOD_COST = 3;

    private final Random rng = new Random();

    /** Seeded from the settlement manager so a world seed replays identically. */
    public void setRandomSeed(long seed) {
        rng.setSeed(seed ^ 0x444f524d53494dL); // "DORMSIM" salt
    }

    /**
     * Clears per-world state. All mutable simulation state lives on the
     * {@link Settlement} records themselves — {@code dormantStep},
     * {@code dormantAccumulator} and {@code replenishTimer} — and those are
     * discarded with the world, so there is nothing to clear here. The hook
     * exists so a future per-world cache has an obvious home and callers do not
     * have to change.
     */
    public void reset() {
        // Intentionally empty; see the JavaDoc.
    }

    /**
     * Advances one dormant settlement by {@code dt} seconds of real time.
     *
     * <p>Does nothing for a depopulated, unoccupied settlement: a ruin has no
     * stocks to move and no residents to feed, and its replenish timer must not
     * drift while it is empty.
     */
    public void simulate(Settlement s, Game g, float dt) {
        int pop = s.aliveResidents();
        if (pop <= 0 && !s.occupied) {
            return;
        }
        s.dormantAccumulator += dt;
        int steps = 0;
        while (s.dormantAccumulator >= STEP_SECONDS && steps++ < MAX_STEPS_PER_TICK) {
            s.dormantAccumulator -= STEP_SECONDS;
            simulateDormantMinute(s, g);
        }
        replenish(s, pop, dt);
    }

    /**
     * Population replenishment: a settlement that is still standing, fed,
     * stocked with medicine, in decent spirits, calm and defended will
     * occasionally take in kin of an existing resident.
     */
    private void replenish(Settlement s, int pop, float dt) {
        s.replenishTimer -= dt;
        if (s.replenishTimer > 0) {
            return;
        }
        s.replenishTimer = REPLENISH_SECONDS_MIN + rng.nextInt(REPLENISH_SECONDS_RANGE);
        int security = 0;
        for (Settlement.Resident r : s.residents) {
            if (r.alive && !r.rescued && !r.routed && !r.surrendered
                    && (r.archetype == NpcArchetype.GUARD
                    || r.archetype == NpcArchetype.ARCHER
                    || r.archetype.hostileArchetype())) {
                security++;
            }
        }
        if (s.cleared || pop <= 0 || pop >= s.type.maxPopulation
                || s.foodStock < REPLENISH_MIN_FOOD || s.medStock < REPLENISH_MIN_MEDICINE
                || s.morale <= REPLENISH_MIN_MORALE || s.alertLevel >= REPLENISH_MAX_ALERT
                || security <= 0) {
            return;
        }
        Settlement.Resident template = null;
        for (Settlement.Resident r : s.residents) {
            if (r.alive && !r.rescued && !r.archetype.leader) {
                template = r;
                break;
            }
        }
        if (template != null) {
            Settlement.Resident newcomer = new Settlement.Resident(
                    template.name + " kin", template.archetype);
            newcomer.bedIndex = s.residents.size();
            newcomer.dutyIndex = s.residents.size();
            s.residents.add(newcomer);
            s.foodStock -= REPLENISH_FOOD_COST;
        }
    }

    /**
     * One simulated minute of settlement life: production, then a meal every
     * other step, then each resident's illness roll, then alert-driven morale
     * decay.
     */
    private void simulateDormantMinute(Settlement s, Game g) {
        s.dormantStep++;
        int pop = s.aliveResidents();
        int farmers = 0, workers = 0, medics = 0, smiths = 0;
        for (Settlement.Resident resident : s.residents) {
            if (!resident.alive || resident.rescued || resident.routed || resident.surrendered) {
                continue;
            }
            switch (resident.archetype) {
                case FARMER -> farmers++;
                case MEDIC -> medics++;
                case SMITH -> smiths++;
                case VILLAGER, GUARD, ARCHER, TRADER -> workers++;
                default -> {
                }
            }
        }
        // Role-appropriate fixed-step outputs, all capped.
        s.foodStock = Math.min(STOCK_CAP, s.foodStock + Math.max(0, farmers));
        s.woodStock = Math.min(STOCK_CAP, s.woodStock + workers / 3);
        if (s.woodStock > 0 && smiths > 0 && s.dormantStep % 3 == 0) {
            s.woodStock--;
            s.metalStock = Math.min(STOCK_CAP, s.metalStock + smiths);
        }
        if (medics > 0 && s.foodStock > 0 && s.dormantStep % 4 == 0) {
            s.foodStock--;
            s.medStock = Math.min(STOCK_CAP, s.medStock + 1);
        }

        if (s.dormantStep % 2 == 0) {
            eat(s, pop);
        }

        for (int i = 0; i < s.residents.size(); i++) {
            Settlement.Resident resident = s.residents.get(i);
            if (!resident.alive || resident.rescued || resident.routed || resident.surrendered) {
                continue;
            }
            // Position-derived, not stream-derived: the roll depends only on the
            // world, the settlement, its step and the resident's index, so
            // simulation order cannot change the outcome.
            long rollBits = Noise.mix(g.world.seed ^ s.id
                    ^ (s.dormantStep * 0x9E3779B97F4A7C15L) ^ i);
            float roll = (rollBits >>> 40) / (float) (1 << 24);
            tickHealth(s, resident, roll, medics);
        }
        if (s.alertLevel > 70) {
            s.morale = Math.max(0, s.morale - 1);
        }
    }

    /** A shared meal if the stores can cover it, hunger and morale loss if not. */
    private static void eat(Settlement s, int pop) {
        int meals = Math.max(1, (pop + 5) / 6);
        if (s.foodStock >= meals) {
            s.foodStock -= meals;
            for (Settlement.Resident resident : s.residents) {
                if (resident.alive) {
                    resident.hunger = Math.max(0, resident.hunger - 35);
                }
            }
            s.morale = MathUtil.clamp(s.morale + 0.5f, 0,
                    s.leaderResident() != null ? 95 : 80);
        } else {
            s.foodStock = 0;
            s.morale = Math.max(0, s.morale - 3f);
            for (Settlement.Resident resident : s.residents) {
                if (resident.alive) {
                    resident.hunger = Math.min(100, resident.hunger + 20);
                }
            }
        }
    }

    /**
     * Illness onset, treatment and mortality for one resident. Empty stores
     * raise both the chance of falling ill and the rate an untreated illness
     * kills, so a starving settlement dies from the inside.
     */
    private static void tickHealth(Settlement s, Settlement.Resident resident, float roll,
                                   int medics) {
        if (!resident.sick) {
            float risk = (s.foodStock == 0 ? 0.025f : 0.002f)
                    + (s.medStock == 0 ? 0.006f : 0f)
                    + (s.alertLevel > 70 ? 0.004f : 0f);
            if (roll < risk) {
                resident.sick = true;
                resident.sicknessTimer = 0;
            }
            return;
        }
        resident.sicknessTimer += 60;
        if (s.medStock > 0 && medics > 0 && roll < 0.45f) {
            s.medStock--;
            resident.sick = false;
            resident.sicknessTimer = 0;
            resident.health = Math.min(resident.archetype.maxHealth, resident.health + 8);
        } else if (resident.sicknessTimer > 600) {
            resident.health -= s.foodStock == 0 ? 3f : 1.2f;
            if (resident.health <= 0) {
                resident.health = 0;
                resident.alive = false;
                s.morale = Math.max(0, s.morale - 8);
            }
        }
    }
}
