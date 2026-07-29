package com.veylon.settlement;

import com.veylon.Game;
import com.veylon.util.Vec3i;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The off-screen settlement simulation, exercised directly rather than through
 * {@link SettlementManager}'s activation sweep.
 *
 * <p>What matters here is the fixed-step contract: a settlement's dormant
 * outcome must depend only on the world seed, the settlement and how much time
 * passed — never on how many other settlements happened to be simulated first,
 * and never on how the elapsed time was chopped into ticks.
 */
class DormantSettlementSimulationTest {

    private static final long SEED = 4_242_424L;

    // ------------------------------------------------------------------
    // Fixed-step contract
    // ------------------------------------------------------------------

    @Test
    void elapsedTimeIsDrainedInWholeMinutesRegardlessOfTickSize() {
        Game g = world();
        DormantSettlementSimulation coarse = seeded();
        DormantSettlementSimulation fine = seeded();

        Settlement inOneTick = village(g, 1);
        Settlement inManyTicks = village(g, 1);

        coarse.simulate(inOneTick, g, 300f);
        for (int i = 0; i < 300; i++) {
            fine.simulate(inManyTicks, g, 1f);
        }

        assertEquals(5, inOneTick.dormantStep, "300 s is five simulated minutes");
        assertEquals(inOneTick.dormantStep, inManyTicks.dormantStep,
                "the accumulator makes tick size irrelevant");
        assertEquals(inOneTick.foodStock, inManyTicks.foodStock);
        assertEquals(inOneTick.woodStock, inManyTicks.woodStock);
        assertEquals(inOneTick.morale, inManyTicks.morale, 1e-4f);
    }

    @Test
    void aSingleHugeTickCannotReplayAnUnboundedNumberOfMinutes() {
        Game g = world();
        Settlement s = village(g, 1);

        // A day of banked time in one call: the step clamp keeps the work bounded
        // rather than letting a stall spiral into 1440 simulated minutes.
        seeded().simulate(s, g, 86_400f);

        assertEquals(20, s.dormantStep, "one tick drains at most 20 fixed steps");
        assertTrue(s.dormantAccumulator > 0,
                "the undrained remainder stays banked instead of being discarded");
    }

    @Test
    void simulationOrderDoesNotChangeASettlementsOutcome() {
        Game g = world();

        // Same settlement, same elapsed time — but in the second run three other
        // settlements are simulated first. Illness rolls are derived from the
        // settlement's own identity and step, so the outcome must not move.
        Settlement alone = village(g, 3);
        seeded().simulate(alone, g, 1_200f);

        DormantSettlementSimulation shared = seeded();
        for (int i = 0; i < 3; i++) {
            shared.simulate(village(g, 3), g, 1_200f);
        }
        Settlement afterOthers = village(g, 3);
        shared.simulate(afterOthers, g, 1_200f);

        assertEquals(fingerprint(alone), fingerprint(afterOthers),
                "another settlement's simulation must not perturb this one");
    }

    // ------------------------------------------------------------------
    // Skipping, stocks and starvation
    // ------------------------------------------------------------------

    @Test
    void aDepopulatedUnoccupiedSettlementIsSkippedEntirely() {
        Game g = world();
        Settlement ruin = village(g, 2);
        for (Settlement.Resident r : ruin.residents) {
            r.alive = false;
        }
        float replenishBefore = ruin.replenishTimer;

        seeded().simulate(ruin, g, 6_000f);

        assertEquals(0, ruin.dormantStep, "a ruin runs no simulated minutes");
        assertEquals(0f, ruin.dormantAccumulator, 1e-4f, "and banks no time");
        assertEquals(replenishBefore, ruin.replenishTimer, 1e-4f,
                "its replenish timer does not drift while it is empty");
    }

    @Test
    void farmersRestockFoodAndEmptyStoresStarveTheResidents() {
        Game g = world();
        Settlement fed = village(g, 2);
        fed.foodStock = 500;
        float moraleBefore = fed.morale;
        seeded().simulate(fed, g, 600f);
        assertTrue(fed.morale > moraleBefore, "a well-fed settlement gains morale");
        for (Settlement.Resident r : fed.residents) {
            assertEquals(0f, r.hunger, 1e-4f, "shared meals clear resident hunger");
        }

        Settlement starving = new Settlement(Settlement.packId(9, 9), 9, 9,
                SettlementType.CAMP, new Vec3i(900, 40, 900), HumanFaction.FRONTIER,
                Settlement.Alignment.FRIENDLY);
        starving.residents.add(new Settlement.Resident("Hungry", NpcArchetype.VILLAGER));
        starving.foodStock = 0;
        starving.morale = 70;
        seeded().simulate(starving, g, 600f);

        assertEquals(0, starving.foodStock, "empty stores stay empty without a farmer");
        assertTrue(starving.morale < 70, "starvation costs morale");
        assertTrue(starving.residents.getFirst().hunger > 0, "and residents go hungry");
    }

    @Test
    void stockpilesAreCappedSoAnUnattendedSettlementCannotOverflow() {
        Game g = world();
        Settlement s = village(g, 8);
        s.foodStock = 998;

        seeded().simulate(s, g, 20 * 60f);

        assertTrue(s.foodStock <= 999, "food is capped, was " + s.foodStock);
        assertTrue(s.woodStock <= 999, "wood is capped, was " + s.woodStock);
        assertTrue(s.metalStock <= 999, "metal is capped, was " + s.metalStock);
    }

    // ------------------------------------------------------------------
    // Replenishment
    // ------------------------------------------------------------------

    @Test
    void replenishmentNeedsSuppliesMoraleAndSecurityAndStopsAtCapacity() {
        Game g = world();

        // A settlement that meets none of the conditions gains nobody, however
        // often its timer fires.
        Settlement destitute = village(g, 2);
        destitute.foodStock = 0;
        destitute.medStock = 0;
        destitute.morale = 10;
        int before = destitute.residents.size();
        DormantSettlementSimulation sim = seeded();
        for (int i = 0; i < 20; i++) {
            destitute.replenishTimer = 0;
            sim.simulate(destitute, g, 0f);
        }
        assertEquals(before, destitute.residents.size(),
                "a starving, demoralized settlement takes nobody in");

        // A thriving one grows, but never past its type's capacity.
        Settlement thriving = village(g, 2);
        thriving.foodStock = 999;
        thriving.medStock = 999;
        thriving.morale = 90;
        thriving.alertLevel = 0;
        for (int i = 0; i < 60; i++) {
            thriving.replenishTimer = 0;
            sim.simulate(thriving, g, 0f);
        }
        assertTrue(thriving.residents.size() > before, "a thriving settlement grows");
        assertTrue(thriving.aliveResidents() <= thriving.type.maxPopulation,
                "growth respects the population cap, was " + thriving.aliveResidents()
                        + " of " + thriving.type.maxPopulation);
    }

    @Test
    void aClearedSettlementNeverRepopulates() {
        Game g = world();
        Settlement razed = village(g, 2);
        razed.cleared = true;
        razed.foodStock = 999;
        razed.medStock = 999;
        razed.morale = 90;
        int before = razed.residents.size();

        DormantSettlementSimulation sim = seeded();
        for (int i = 0; i < 30; i++) {
            razed.replenishTimer = 0;
            sim.simulate(razed, g, 0f);
        }

        assertEquals(before, razed.residents.size(), "cleared sites stay cleared");
    }

    // ------------------------------------------------------------------
    // Illness
    // ------------------------------------------------------------------

    @Test
    void untreatedIllnessKillsAndMedicineWithAMedicCures() {
        Game g = world();

        Settlement doomed = village(g, 1);
        doomed.foodStock = 0;
        doomed.medStock = 0;
        Settlement.Resident dying = doomed.residents.getFirst();
        dying.sick = true;
        dying.sicknessTimer = 601;
        dying.health = 2;
        seeded().simulate(doomed, g, 600f);
        assertFalse(dying.alive, "untreated off-screen illness has a real mortality outcome");

        // Deliberately no farmer: with food stuck at zero the medic cannot also
        // brew medicine, so the stock can only move downwards when a cure lands.
        Settlement infirmary = new Settlement(Settlement.packId(11, 11), 11, 11,
                SettlementType.VILLAGE, new Vec3i(1200, 40, 1200), HumanFaction.FRONTIER,
                Settlement.Alignment.FRIENDLY);
        infirmary.residents.add(new Settlement.Resident("Patient", NpcArchetype.VILLAGER));
        infirmary.residents.add(new Settlement.Resident("Medic", NpcArchetype.MEDIC));
        g.world.settlements.put(infirmary.id, infirmary);
        infirmary.foodStock = 0;
        infirmary.medStock = 50;
        Settlement.Resident patient = infirmary.residents.getFirst();
        patient.sick = true;
        patient.health = 20;
        seeded().simulate(infirmary, g, 3_600f);
        assertFalse(patient.sick, "a stocked medic eventually cures the patient");
        assertTrue(patient.alive);
        assertTrue(patient.health > 20, "recovery restores health, was " + patient.health);
        assertTrue(infirmary.medStock < 50, "the cure consumes real medicine");
    }

    // ------------------------------------------------------------------

    private static Game world() {
        Game g = new Game();
        g.newWorld(SEED, true);
        return g;
    }

    private static DormantSettlementSimulation seeded() {
        DormantSettlementSimulation sim = new DormantSettlementSimulation();
        sim.setRandomSeed(SEED);
        return sim;
    }

    /** A fresh village with one farmer, one guard and {@code extras} villagers. */
    private static Settlement village(Game g, int extras) {
        Settlement s = new Settlement(Settlement.packId(30, 30), 30, 30,
                SettlementType.VILLAGE, new Vec3i(900, 40, 900), HumanFaction.FRONTIER,
                Settlement.Alignment.FRIENDLY);
        s.residents.add(new Settlement.Resident("Farmer", NpcArchetype.FARMER));
        s.residents.add(new Settlement.Resident("Guard", NpcArchetype.GUARD));
        for (int i = 0; i < extras; i++) {
            s.residents.add(new Settlement.Resident("Villager " + i, NpcArchetype.VILLAGER));
        }
        g.world.settlements.put(s.id, s);
        return s;
    }

    /** Everything the dormant simulation is allowed to move. */
    private static String fingerprint(Settlement s) {
        StringBuilder sb = new StringBuilder()
                .append("step=").append(s.dormantStep)
                .append(" food=").append(s.foodStock)
                .append(" wood=").append(s.woodStock)
                .append(" med=").append(s.medStock)
                .append(" metal=").append(s.metalStock)
                .append(" morale=").append(Math.round(s.morale * 100));
        for (Settlement.Resident r : s.residents) {
            sb.append(" |").append(r.alive ? '+' : '-').append(r.sick ? 'S' : '.')
                    .append(Math.round(r.health * 100)).append('/')
                    .append(Math.round(r.hunger * 100));
        }
        return sb.toString();
    }
}
