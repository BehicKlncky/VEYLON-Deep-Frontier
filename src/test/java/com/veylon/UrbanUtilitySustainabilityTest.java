package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.entity.PlayerConstants;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.simulation.TimeConstants;
import com.veylon.util.Vec3i;
import com.veylon.world.urban.UrbanStarterSite;
import com.veylon.world.urban.UrbanUtilities;
import com.veylon.world.urban.VehicleSites;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scarcity in a layout-fifteen city must not make sustained survival impossible (urban milestone
 * 19). Water is the need that comes back every day, so the arithmetic is made on the production
 * systems: the city's own weather, rolled from each design seed, filling the starter courtyard's
 * rain collector through the ordinary collector rule, against what a waking day of thirst costs;
 * and the finite routes beside it, fuel from the cars that did not burn and the purifier that
 * spends it, never being needed to drink.
 */
class UrbanUtilitySustainabilityTest {
    /** Game days the city's weather is run for on each seed. */
    private static final int DAYS = 20;
    private static final float REAL_SECONDS_PER_DAY = (float) (TimeConstants.MINUTES_PER_DAY / TimeConstants.MINUTES_PER_REAL_SECOND);
    /** Drinkable litres the starter area guarantees (DESIGN section 4.4). */
    private static final float STARTER_LITRES = 3f;

    /** Litres of clean water a waking day of thirst costs, at the ordinary drain and a clean litre's relief. */
    private static float litresADay() {
        return PlayerConstants.BASE_THIRST_DRAIN_PER_SECOND * REAL_SECONDS_PER_DAY / PlayerConsumables.CLEAN_WATER_THIRST;
    }

    @Test
    void oneRainCollectorEmptiedOnceADayKeepsASurvivorInWaterOnEveryDesignSeed() {
        float need = litresADay();
        assertTrue(need > 0.5f && need < 2f, "a little over a litre a day: " + need);
        for (long seed : UrbanStarterProgressionTest.SEEDS) {
            Game g = UtilityWorldArena.session(seed, GameMode.SURVIVAL);
            Vec3i collector = UrbanStarterSite.of(g.world.urban).collector();
            SurvivorWorldArena.standAt(g, collector.offset(1, 0, 0));
            g.world.collectorWater.put(collector, 0f);
            // The starter area's drinkable water (DESIGN section 4.4) is what the survivor starts with in hand.
            float harvested = 0, banked = STARTER_LITRES, worst = banked;
            for (int day = 0; day < DAYS; day++) {
                float today = 0;
                int ticks = Math.round(REAL_SECONDS_PER_DAY / SimulationScheduler.MEDIUM_DT);
                for (int t = 0; t < ticks; t++) {
                    g.time.totalMinutes += SimulationScheduler.MEDIUM_DT * TimeConstants.MINUTES_PER_REAL_SECOND;
                    g.weather.mediumTick(g, SimulationScheduler.MEDIUM_DT);
                    g.temperature.mediumTick(g, SimulationScheduler.MEDIUM_DT);
                    if (t % 20 == 19) { g.itemConditions.slowTick(g, SimulationScheduler.SLOW_DT); }
                }
                // Once a day the survivor fills skins from it, a litre at a time as the F rule does: its
                // three-litre basin is the most one day can give, rain or no rain.
                float in = g.world.collectorWater.getOrDefault(collector, 0f);
                int litres = (int) Math.floor(in);
                today += litres;
                g.world.collectorWater.put(collector, in - litres);
                harvested += today;
                // What was not drunk is carried over in skins.
                banked += today - need;
                worst = Math.min(worst, banked);
            }
            float perDay = harvested / DAYS;
            System.out.printf("[sustainability] seed=%d rain litres/day from one collector emptied daily=%.2f need=%.2f"
                    + " lowest in hand=%.2f after %d days=%.1f%n", seed, perDay, need, worst, DAYS, banked);
            assertTrue(perDay >= need, "seed " + seed + ": one collector gathers " + perDay + " L a day for " + need);
            assertTrue(worst >= 0, "seed " + seed + ": the survivor never runs dry, lowest " + worst + " L in hand");
        }
    }

    @Test
    void treatmentAndFuelAreNeverWhatDrinkingWaitsOn() {
        // A fuel can runs a purifier long enough to treat days of water.
        float burn = 1f + UrbanUtilities.LOAD_BURN;
        float litresPerCan = UrbanUtilities.CAN_SECONDS / burn / UrbanUtilities.TREAT_SECONDS;
        assertTrue(litresPerCan >= 5 * litresADay(), "a can treats " + litresPerCan + " L");
        // A fresh filter treats more than a week's water before it needs more fibre.
        assertTrue(UrbanUtilities.SOUND / UrbanUtilities.FILTER_WEAR >= 7 * litresADay());
        // And fuel is out there: every design seed has cars that did not burn within a region of the spawn.
        for (long seed : UrbanStarterProgressionTest.SEEDS) {
            Game g = UtilityWorldArena.session(seed, GameMode.SURVIVAL);
            int cans = 0;
            for (int rz = -1; rz <= 1; rz++) {
                for (int rx = -1; rx <= 1; rx++) {
                    for (VehicleSites.Wreck w : g.world.urban.vehicles(rx, rz).wrecks()) {
                        if (!w.burnt()) { cans++; }
                    }
                }
            }
            System.out.printf("[sustainability] seed=%d fuel cans within a region of the spawn=%d%n", seed, cans);
            assertTrue(cans >= 1, "seed " + seed + " has fuel to find");
        }
    }
}
