package com.veylon.simulation;

import com.veylon.Game;
import com.veylon.entity.Npc;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization coverage for {@link EventSystem}'s event selection.
 *
 * <p>Event selection is one roll walked against a ladder of cumulative bounds,
 * where a rejected event hands its roll to the next one. That makes it very easy
 * to change the odds by accident — reordering two entries, or hoisting a
 * precondition into the range test, silently re-weights everything below it.
 *
 * <p>{@link #theRollLadderProducesItsRecordedEventSequence()} therefore pins the
 * distribution of events that fixed seeds produce in four contrasting world
 * states. The expected strings were captured from the v0.4.0 chained-else-if
 * implementation and must survive any restructuring of the ladder; if a change
 * to the selection code moves them, the odds moved with it. The remaining tests
 * state the individual rules that fingerprint is protecting, so a failure is
 * diagnosable rather than just a wall of changed counts.
 *
 * <p>Worlds are expensive to build, so every case here runs against one shared
 * world and resets {@link EventSystem} between rolls. Nothing in event selection
 * reads terrain, only the plant, faction and camp state each case sets.
 */
class EventRollLadderTest {

    /** Independent selection rolls sampled per world state. */
    private static final int SAMPLES = 400;

    /**
     * Captured from the v0.4.0 implementation. One entry per world state in
     * {@link #scenarios(Game)}, in order.
     */
    private static final List<String> RECORDED = List.of(
            // Baseline: everything eligible.
            "hospitable: Ashfall=12, Berry Bloom=30, Camp Illness=18, Cold Snap=20, "
                    + "Heat Wave=18, Meteor Shard=8, Meteor Shower=11, Predator Migration=17, "
                    + "Predator Raid=8, Toxic Fog=12, Trader Visit=18, none=228",
            // Dry soil opens the drought rung and closes the berry bloom, whose
            // rolls slide down into the predator migration below it.
            "parched: Ashfall=12, Camp Illness=18, Cold Snap=20, Drought=18, Heat Wave=18, "
                    + "Meteor Shard=8, Meteor Shower=11, Predator Migration=29, "
                    + "Predator Raid=8, Toxic Fog=12, Trader Visit=18, none=228",
            // A hostile, distrusted camp turns away the trader; those 18 rolls
            // fall through to the toxic fog, taking it from 12 to 30.
            "distrusted: Ashfall=12, Berry Bloom=30, Camp Illness=18, Cold Snap=20, "
                    + "Heat Wave=18, Meteor Shard=8, Meteor Shower=11, Predator Migration=17, "
                    + "Predator Raid=8, Toxic Fog=30, none=228",
            // An upgraded camp unlocks the scavenger raid, which takes half of
            // what previously fell through to camp illness.
            "fortified: Ashfall=12, Berry Bloom=30, Camp Illness=9, Cold Snap=20, "
                    + "Heat Wave=18, Meteor Shard=8, Meteor Shower=11, Predator Migration=17, "
                    + "Predator Raid=8, Scavenger Raid=9, Toxic Fog=12, Trader Visit=18, "
                    + "none=228");

    @Test
    void theRollLadderProducesItsRecordedEventSequence() {
        assertEquals(RECORDED, scenarios(world()),
                "event selection changed: a bound, an ordering or a precondition moved");
    }

    // ------------------------------------------------------------------
    // The rules the fingerprint above is protecting
    // ------------------------------------------------------------------

    @Test
    void aRejectedEventHandsItsRollToTheNextOneInsteadOfCancellingIt() {
        // Cold snap owns the bottom of the ladder. With one already running, a
        // roll that would have selected it must slide up the ladder rather than
        // producing nothing at all.
        Game g = world();
        neutralState(g);
        long seed = seedFor(g, EventSystem.EventType.COLD_SNAP);

        prepare(g);
        g.events.setRandomSeed(seed);
        g.events.active.add(new EventSystem.ActiveEvent(
                EventSystem.EventType.COLD_SNAP, 9_999f, 1f));
        forceRoll(g);

        // Heat wave, the next rung up, is mutually exclusive with the cold snap
        // too, so the roll slides past it as well and lands on the drought — the
        // first rung whose precondition the world actually satisfies.
        assertTrue(g.events.isActive(EventSystem.EventType.DROUGHT),
                "the rejected roll fell through to a later rung, was " + g.events.summary());
    }

    @Test
    void anEmptyCampConsumesTheIllnessRollInsteadOfFallingThroughToAMeteor() {
        // Camp illness is the one rung whose real precondition — a healthy camp
        // NPC — lives inside the effect. With nobody to fall ill, the roll must
        // be consumed silently; if it fell through, the lone meteor below it
        // would carve a crater and announce itself.
        Game g = world();
        neutralState(g);
        long seed = seedFor(g, EventSystem.EventType.NPC_ILLNESS);

        prepare(g);
        g.entities.npcs.clear();
        g.events.setRandomSeed(seed);
        forceRoll(g);

        assertTrue(g.events.active.isEmpty(),
                "an empty camp starts no event at all, was " + g.events.summary());
        assertEquals(0, g.events.totalEventsTriggered,
                "and in particular does not fall through to the lone meteor");
    }

    @Test
    void anIllnessRollWithAHealthyNpcPresentDoesStartTheEvent() {
        // The mirror of the test above: the roll itself is sound, so the silence
        // there really is the empty-camp path and not a dud seed.
        Game g = world();
        neutralState(g);
        long seed = seedFor(g, EventSystem.EventType.NPC_ILLNESS);

        prepare(g);
        Npc patient = onlyNpc(g);
        g.events.setRandomSeed(seed);
        forceRoll(g);

        assertTrue(g.events.isActive(EventSystem.EventType.NPC_ILLNESS),
                "a camp with a healthy resident does catch the illness");
        assertTrue(patient.sick, "and that resident is the one who falls ill");
    }

    @Test
    void seasonBiasWidensItsOwnEventAtTheExpenseOfTheNextOne() {
        // A cold season adds 0.08 to the cold snap's 0.06 bound, so rolls in
        // [0.06, 0.14) that would otherwise have been a heat wave or a drought
        // become a cold snap instead.
        Game g = world();
        neutralState(g);
        List<String> temperate = new ArrayList<>();
        List<String> cold = new ArrayList<>();
        for (int index = 0; index < 120; index++) {
            temperate.add(firstEventInSeason(g, spread(index), SeasonSystem.Season.TEMPERATE));
            cold.add(firstEventInSeason(g, spread(index), SeasonSystem.Season.COLD));
        }
        assertNotEquals(temperate, cold, "the cold season must change what fires");

        long coldSnaps = cold.stream().filter("Cold Snap"::equals).count();
        long temperateColdSnaps = temperate.stream().filter("Cold Snap"::equals).count();
        assertTrue(coldSnaps > temperateColdSnaps,
                "a cold season produces more cold snaps: " + coldSnaps
                        + " vs " + temperateColdSnaps);
    }

    @Test
    void anEventNeverStartsWhileTheCooldownIsStillRunning() {
        Game g = world();
        neutralState(g);
        prepare(g);
        g.events.setRandomSeed(1L);
        // INITIAL_COOLDOWN is 60 s; five 10 s slow ticks cannot exhaust it.
        for (int i = 0; i < 5; i++) {
            g.events.slowTick(g, SimulationScheduler.SLOW_DT);
        }
        assertEquals(0, g.events.totalEventsTriggered,
                "the opening cooldown holds the first event back");
        assertTrue(g.events.active.isEmpty());
    }

    @Test
    void expiredEventsAreRetiredAndReportedOnce() {
        Game g = world();
        prepare(g);
        g.events.active.add(new EventSystem.ActiveEvent(
                EventSystem.EventType.ASHFALL, 5f, 1f));
        assertTrue(g.events.ashfall(), "precondition: ashfall is running");

        g.events.slowTick(g, SimulationScheduler.SLOW_DT);
        assertFalse(g.events.ashfall(), "an elapsed event stops modifying the world");
        assertFalse(g.events.isActive(EventSystem.EventType.ASHFALL));
    }

    // ------------------------------------------------------------------

    /** One histogram line per world state, in a fixed order. */
    private static List<String> scenarios(Game g) {
        List<String> out = new ArrayList<>();
        for (String state : List.of("hospitable", "parched", "distrusted", "fortified")) {
            out.add(state + ": " + histogram(g, state));
        }
        return out;
    }

    /**
     * Counts what {@link #SAMPLES} independent selection rolls produce in the
     * named world state, as a sorted "Event=count" list.
     *
     * <p>A histogram rather than a sequence, because it is the shape that
     * matters: every rung's share of the probability space shows up here, so
     * moving a bound, reordering two rungs or changing a precondition all
     * register, while an unrelated change to one event's duration does not.
     */
    private static String histogram(Game g, String worldState) {
        Map<String, Integer> counts = new TreeMap<>();
        for (int index = 0; index < SAMPLES; index++) {
            prepare(g);
            applyState(g, worldState);
            g.events.setRandomSeed(spread(index));
            int before = g.events.totalEventsTriggered;
            forceRoll(g);
            counts.merge(describe(g, before), 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
    }

    /**
     * What the roll that just ran produced. A lone meteor announces itself but
     * leaves nothing running, and a meteor aimed at an unloaded chunk fizzles
     * without counting at all — so the trigger counter, not the active list, is
     * what says whether anything happened.
     */
    private static String describe(Game g, int triggeredBefore) {
        if (g.events.totalEventsTriggered == triggeredBefore) {
            return "none";
        }
        return g.events.active.isEmpty()
                ? "Meteor Shard" : g.events.active.getLast().type.displayName;
    }

    private static void applyState(Game g, String worldState) {
        switch (worldState) {
            case "hospitable" -> factionState(g, 0.6f, 60, false, 0);
            case "parched" -> factionState(g, 0.2f, 60, false, 0);
            case "distrusted" -> factionState(g, 0.6f, 5, true, 0);
            case "fortified" -> factionState(g, 0.6f, 60, false, 2);
            default -> throw new IllegalArgumentException(worldState);
        }
    }

    private static void factionState(Game g, float moisture, float trust, boolean hostile,
                                     int upgradeStage) {
        g.plants.lastAvgMoisture = moisture;
        g.faction.trust = trust;
        g.faction.hostile = hostile;
        g.faction.upgradeStage = upgradeStage;
    }

    /** Middle-of-the-road world state: no precondition is trivially blocked. */
    private static void neutralState(Game g) {
        factionState(g, 0.5f, 60, false, 2);
    }

    /** The display name of the first event a seed produces in a given season. */
    private static String firstEventInSeason(Game g, long seed, SeasonSystem.Season season) {
        prepare(g);
        g.time.totalMinutes = seasonStartMinutes(season);
        g.events.setRandomSeed(seed);
        int before = g.events.totalEventsTriggered;
        forceRoll(g);
        return describe(g, before);
    }

    /** Game minutes at the start of the first day of the given season. */
    private static double seasonStartMinutes(SeasonSystem.Season season) {
        int day = season.ordinal() * SeasonSystem.SEASON_DAYS + 1;
        return (double) (day - 1) * TimeConstants.MINUTES_PER_DAY;
    }

    /** Clears event state and guarantees exactly one healthy camp NPC. */
    private static void prepare(Game g) {
        g.events.reset();
        g.entities.npcs.clear();
        g.entities.creatures.clear();
        g.time.reset();
        g.entities.spawnNpc(g.world, "Camp Resident",
                g.player.pos.x + 2, g.player.pos.y, g.player.pos.z);
    }

    private static Npc onlyNpc(Game g) {
        assertEquals(1, g.entities.npcs.size(), "the fixture keeps exactly one camp NPC");
        return g.entities.npcs.getFirst();
    }

    /** Drains the cooldown so the next slow tick performs a selection roll. */
    private static void forceRoll(Game g) {
        g.events.slowTick(g, 5_000f);
    }

    /**
     * The lowest seed whose first roll lands on {@code wanted}, found by search
     * so the tests do not encode magic numbers whose meaning is invisible.
     */
    private static long seedFor(Game g, EventSystem.EventType wanted) {
        for (int index = 0; index < 20_000; index++) {
            long seed = spread(index);
            prepare(g);
            g.events.setRandomSeed(seed);
            forceRoll(g);
            if (g.events.isActive(wanted)) {
                return seed;
            }
        }
        throw new IllegalStateException("no seed reaches " + wanted);
    }

    /**
     * Spreads a small index across the seed space. {@code java.util.Random}'s
     * LCG barely moves its first output for consecutive small seeds — every seed
     * below a few hundred opens on roughly 0.73 — so iterating 0, 1, 2... would
     * test one rung over and over. Multiplying by the 64-bit golden ratio pushes
     * the index into the high bits, which the first draw actually depends on.
     */
    private static long spread(int index) {
        return index * 0x9E3779B97F4A7C15L;
    }

    private static Game world() {
        Game g = new Game();
        g.newWorld(4242L, true);
        return g;
    }
}
