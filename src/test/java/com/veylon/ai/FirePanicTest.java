package com.veylon.ai;

import com.veylon.Game;
import com.veylon.combat.WeaponRegistry;
import com.veylon.entity.CombustionSource;
import com.veylon.entity.Creature;
import com.veylon.entity.Creature.CreatureState;
import com.veylon.entity.Creature.CreatureType;
import com.veylon.entity.Entity;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.entity.Npc.NpcState;
import com.veylon.entity.PlayerMovementSystem;
import com.veylon.settlement.CounterattackMission;
import com.veylon.settlement.HumanFaction;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementType;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.veylon.ai.PanicConstants.CREATURE_SPEED_MUL;
import static com.veylon.ai.PanicConstants.LEGACY_PERSON_SPEED;
import static com.veylon.ai.PanicConstants.NPC_SPEED_MUL;
import static com.veylon.ai.PanicConstants.RECOVERY_SECONDS;
import static com.veylon.ai.PanicConstants.REPLAN_COOLDOWN;
import static com.veylon.ai.PanicConstants.TURN_RATE_DEGREES;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Burning people and animals flee the fire on their bodies, whatever they
 * were doing, and the player keeps every control (contract sections 12-13).
 *
 * <p>Isolated mechanics fixture: a flat stone arena (floor top at y = 39,
 * bodies stand at y = 40) over chunks 18-22, clear weather at noon, no camp
 * unless a case sets one, settlements staged in far regions so nothing
 * generated interferes. Bodies are made sturdy (1000 health) so the fire never
 * ends a run by killing them. Ignition goes through production paths: a fire
 * bomb thrown and broken on the body, a spill of burning liquid, or the
 * {@code CombustionSystem.ignite} command a direct hit uses.
 */
class FirePanicTest {

    private static final float DT = SimulationScheduler.FAST_DT;
    private static final float MEDIUM_DT = SimulationScheduler.MEDIUM_DT;
    private static final int FAST_PER_MEDIUM = Math.round(MEDIUM_DT / DT);
    private static final float FEET = 40f;
    private static final float STURDY = 1000f;
    private static final float HERE_X = 330.5f;
    private static final float HERE_Z = 330.5f;
    private static final int RECOVERY_TICKS = Math.round(RECOVERY_SECONDS / DT);

    /** Fast ticks run so far, so the medium tick falls on every tenth whatever the test does. */
    private int clock;

    // ------------------------------------------------------------------
    // Every dispatch family and every species
    // ------------------------------------------------------------------

    /** One family of body doing its ordinary thing, ready to be set alight. */
    record Case(String label, Function<Game, Entity> stage, boolean caged) {
        @Override
        public String toString() {
            return label;
        }
    }

    static Stream<Case> everyDispatchFamily() {
        Stream<Case> animals = Arrays.stream(CreatureType.values())
                .map(type -> new Case(type.name(), g -> animalAtWork(g, type), false));
        Stream<Case> people = Stream.of(
                new Case("camp member at work", g -> campMember(g, 0), false),
                new Case("camp member talking to the player", FirePanicTest::inConversation, false),
                new Case("wandering trader", FirePanicTest::wanderingTrader, false),
                new Case("raider", FirePanicTest::raider, false),
                new Case("resident at work", g -> resident(g, village(g), NpcArchetype.VILLAGER,
                        HERE_X, HERE_Z), false),
                new Case("resident asleep", FirePanicTest::sleeper, false),
                new Case("settlement trader", g -> resident(g, village(g), NpcArchetype.TRADER,
                        HERE_X, HERE_Z), false),
                new Case("archer shooting at the player", FirePanicTest::archerInCombat, false),
                new Case("guard striking the player", FirePanicTest::guardInMelee, false),
                new Case("war party on patrol", g -> warParty(g, Npc.PartyKind.PATROL, NpcArchetype.GUARD), false),
                new Case("bounty hunter", g -> warParty(g, Npc.PartyKind.BOUNTY_HUNTER, NpcArchetype.TRACKER),
                        false),
                new Case("counterattack", FirePanicTest::counterattacker, false),
                new Case("captive in a cage", FirePanicTest::captive, true));
        return Stream.concat(animals, people);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyDispatchFamily")
    void productionIgnitionTurnsEveryOrdinaryBehaviourIntoFlight(Case c) {
        Game g = arena();
        Entity actor = sturdy(c.stage().apply(g));
        PanicIntent panic = panicOf(actor);
        for (int i = 0; i < 20; i++) {
            gameTick(g);
        }
        assertFalse(panic.active(), "calm before the fire");
        assertFalse(actor.dead, "fixture: its ordinary life kept it in the world");

        if (c.caged()) {
            // A bottle breaks on the bars; its burning liquid runs into the cage.
            assertTrue(g.liquidFire.spill(g, actor.pos.x, FEET + 0.5f, actor.pos.z, 0, 0, true) > 0);
            gameTick(g);
        } else {
            throwAt(g, g.player, actor);
        }
        assertTrue(g.combustion.isBurning(actor), "alight through a real flame");

        float x0 = actor.pos.x;
        float z0 = actor.pos.z;
        float farthest = 0f;
        float path = 0f;
        float limit = panicSpeed(actor) * DT * 1.02f + 1e-3f;
        for (int t = 1; t <= 50; t++) {
            float px = actor.pos.x, pz = actor.pos.z;
            gameTick(g);
            assertTrue(panic.active(), "panicking on tick " + t);
            assertFalse(actor.collidesAt(actor.pos.x, actor.pos.y, actor.pos.z), "inside a block on tick " + t);
            float step = flat(actor.pos.x - px, actor.pos.z - pz);
            if (t > 1) {
                assertTrue(step <= limit, "one step of physics at the panic speed on tick " + t + ": " + step);
            }
            path += step;
            farthest = Math.max(farthest, flat(actor.pos.x - x0, actor.pos.z - z0));
        }
        assertTrue(panic.goals() >= 3, "irregular goals on a timer, not one: " + panic.goals());
        if (actor instanceof Npc n) {
            assertSame(NpcState.FLEE, n.state);
            assertEquals(0f, n.interactFreeze, 0f, "nothing holds a burning person still");
        } else {
            assertSame(CreatureState.FLEE, ((Creature) actor).state);
        }
        if (c.caged()) {
            assertTrue(path > 0.3f, "the captive struggles about its cage: " + path);
        } else {
            assertTrue(farthest >= 2.5f, "it ran from where it caught: " + farthest);
        }
    }

    // ------------------------------------------------------------------
    // Attacks, recovery and what resumes
    // ------------------------------------------------------------------

    /**
     * Every body that was about to strike or shoot the player stops while
     * alight and through its recovery, even when its flight carries it past
     * the player. A hungry wolf that is calm again bites a perceivable
     * Survival player afresh, and never a Creative one.
     */
    @Test
    void armedBodiesStrikeNobodyWhileAlightAndResumeOnlyWhereTheyStillMay() {
        for (GameMode mode : new GameMode[] {GameMode.SURVIVAL, GameMode.CREATIVE}) {
            Game g = arena(mode);
            g.time.totalMinutes = 0; // midnight: the stalker hunts in the dark
            g.player.pos.set(HERE_X, FEET, HERE_Z);
            Settlement fort = fort(g);
            List<Entity> armed = new ArrayList<>();
            Creature wolf = g.entities.spawnCreature(g.world, CreatureType.WOLF, HERE_X + 1.6f, FEET, HERE_Z);
            wolf.hunger = 80f;
            armed.add(wolf);
            Creature thornhorn = g.entities.spawnCreature(g.world, CreatureType.THORNHORN, HERE_X - 1.7f, FEET, HERE_Z);
            thornhorn.state = CreatureState.CHARGE;
            thornhorn.fear = 1f;
            armed.add(thornhorn);
            Creature stalker = g.entities.spawnCreature(g.world, CreatureType.STALKER, HERE_X, FEET, HERE_Z + 1.7f);
            armed.add(stalker);
            Npc guard = resident(g, fort, NpcArchetype.GUARD, HERE_X, HERE_Z - 1.6f);
            guard.lastKnown.set(g.player.pos);
            guard.lastKnownAge = 0f;
            armed.add(guard);
            Npc archer = resident(g, fort, NpcArchetype.ARCHER, HERE_X + 12.5f, HERE_Z);
            archer.yaw = 270f;
            archer.lastKnown.set(g.player.pos);
            archer.lastKnownAge = 0f;
            armed.add(archer);
            Npc powderman = resident(g, fort, NpcArchetype.POWDERMAN, HERE_X - 12.5f, HERE_Z);
            powderman.yaw = 90f;
            powderman.reloadTimer = 1f;
            powderman.loadedAmmo = 1;
            armed.add(powderman);
            for (Entity e : armed) {
                sturdy(e);
                // Touched on the far side from the player, so flight leads toward them.
                float dx = e.pos.x - HERE_X, dz = e.pos.z - HERE_Z;
                float len = Math.max(0.01f, flat(dx, dz));
                assertTrue(g.combustion.ignite(g, e, CombustionSource.DIRECT_HIT, 1f, false, 1,
                        e.pos.x + dx / len * 0.3f, e.pos.y + 0.5f, e.pos.z + dz / len * 0.3f));
            }
            float health = g.player.health;

            for (int t = 0; t < 30; t++) {
                gameTick(g);
            }
            for (Entity e : armed) {
                assertTrue(panicOf(e).active(), mode + ": " + e + " panics");
                g.combustion.extinguish(e);
            }
            for (int t = 0; t < RECOVERY_TICKS - 1; t++) {
                gameTick(g);
                assertTrue(wolf.panic.recovering(), "still recovering on tick " + t);
            }
            assertFalse(g.player.combustion.hasExposure(), "fixture: no flame reached the player");
            assertEquals(health, g.player.health, 0f, mode + ": nobody struck the player while panicking");
            assertEquals(0, g.projectiles.liveCount(), mode + ": nobody loosed an arrow or a ball");
            assertEquals(1f, powderman.reloadTimer, 0f, mode + ": no reload completed in flight");
            assertEquals(0, g.noise.countCategory("creature-attack"), mode + ": no animal attacked");

            // The player it was charging is out of reach by the time it is calm.
            g.player.pos.set(thornhorn.pos.x + 25f, FEET, thornhorn.pos.z);
            gameTick(g);
            for (Entity e : armed) {
                assertFalse(panicOf(e).active(), mode + ": the recovery ended for " + e);
            }
            assertNotEquals(CreatureState.CHARGE, thornhorn.state, mode + ": an old charge is not resumed");
            // Bring the player to the wolf, wherever it ran, and let it decide.
            g.player.pos.set(wolf.pos.x + 1.6f, FEET, wolf.pos.z);
            boolean bit = false;
            for (int t = 0; t < 40; t++) {
                gameTick(g);
                bit |= wolf.state == CreatureState.ATTACK && wolf.attackCooldown > 1f;
            }
            if (mode == GameMode.SURVIVAL) {
                assertTrue(bit && g.player.health < health, "the hungry wolf bites a perceivable player again");
            } else {
                assertFalse(bit, "and never an imperceptible Creative one");
                assertEquals(health, g.player.health, 0f);
            }
        }
    }

    /**
     * The game reaches the settlement brain only through {@code NpcAI.update},
     * but a direct caller (tests call it so) gets the same panic: a burning
     * archer that has just seen the player flees rather than shooting.
     */
    @Test
    void aDirectCallIntoTheSettlementBrainPanicsToo() {
        Game g = arena();
        Npc archer = sturdy(archerInCombat(g));
        archer.lastKnown.set(g.player.pos);
        archer.lastKnownAge = 0f;
        SettledNpcAI.update(g, archer, DT);
        assertEquals(NpcState.ATTACK, archer.state, "fixture: in combat with the player");
        g.projectiles.reset();
        archer.attackCooldown = 0f;
        g.combustion.ignite(g, archer, CombustionSource.DIRECT_HIT, 1f, false, 1, HERE_X + 0.3f, FEET + 1f, HERE_Z);
        for (int t = 0; t < 20; t++) {
            SettledNpcAI.update(g, archer, DT);
            archer.applyPhysics(DT, true);
        }
        assertSame(NpcState.FLEE, archer.state);
        assertTrue(archer.panic.goals() >= 1);
        assertEquals(0, g.projectiles.liveCount(), "no arrow loosed");
    }

    /**
     * The flames out, a body keeps fleeing for the recovery's length and then
     * decides afresh from the world as it is. Its burns alone never start it
     * again, however long it goes on scorched and hurt.
     */
    @Test
    void recoveryRunsItsCourseAndTheScorchedBodyStaysCalmAfter() {
        Game g = arena();
        Creature deer = sturdy(g.entities.spawnCreature(g.world, CreatureType.DEER, HERE_X, FEET, HERE_Z));
        Settlement village = village(g);
        Npc farmer = sturdy(resident(g, village, NpcArchetype.FARMER, HERE_X + 4f, HERE_Z));
        farmer.path = List.of(new Vec3i(360, 40, 360));
        farmer.combatTarget = deer;
        for (Entity e : List.of(deer, farmer)) {
            g.combustion.ignite(g, e, CombustionSource.DIRECT_HIT, 1f, false, 1, e.pos.x, e.pos.y + 0.5f, e.pos.z);
        }
        for (int t = 0; t < 20; t++) {
            gameTick(g);
        }
        g.combustion.extinguish(deer);
        g.combustion.extinguish(farmer);
        // The village is gone by the time the farmer is calm again.
        g.world.settlements.remove(village.id);

        float speedAtStart = 0f;
        for (int t = 1; t < RECOVERY_TICKS; t++) {
            gameTick(g);
            assertTrue(deer.panic.recovering() && farmer.panic.recovering(), "recovering on tick " + t);
            assertSame(CreatureState.FLEE, deer.state);
            float speed = flat(deer.vel.x, deer.vel.z);
            if (t == 1) {
                speedAtStart = speed;
            }
            assertTrue(speed > 0f, "still running on tick " + t);
        }
        assertTrue(flat(deer.vel.x, deer.vel.z) < speedAtStart, "slowing as it settles");
        gameTick(g);
        assertFalse(deer.panic.active(), "calm after exactly the recovery");
        assertFalse(farmer.panic.active());
        assertNotEquals(CreatureState.FLEE, deer.state, "its own AI chose again");
        assertNotEquals(NpcState.FLEE, farmer.state);
        assertNull(farmer.combatTarget, "no stale target");
        assertNull(farmer.path, "no stale path to a village that is gone");

        assertTrue(deer.combustion.scorch() > 0f && deer.health < deer.maxHealth, "fixture: burned");
        for (int t = 0; t < 200; t++) {
            gameTick(g);
            assertFalse(deer.panic.active() || farmer.panic.active(), "burns alone never panic, tick " + t);
        }
    }

    // ------------------------------------------------------------------
    // Seeded, individual, and blind to presentation and the player
    // ------------------------------------------------------------------

    @Test
    void oneSeedReplaysEveryPanicAndNoTwoBodiesRunInStep() {
        Trace first = crowdTrace(false);
        Trace replay = crowdTrace(false);
        Trace distracted = crowdTrace(true);
        assertArrayEquals(first.positions, replay.positions, "one seed and one input sequence: one panic");
        assertArrayEquals(first.positions, distracted.positions,
                "a spinning camera, a wandering player and particle effects change nothing");

        // Four villagers side by side, lit alike and facing alike, still run their own ways.
        for (int a = 0; a < 4; a++) {
            for (int b = a + 1; b < 4; b++) {
                assertNotEquals(first.goalTicks.get(a), first.goalTicks.get(b),
                        "villagers " + a + " and " + b + " choose goals at their own moments");
                assertTrue(first.divergence[a][b] > 0.5f,
                        "villagers " + a + " and " + b + " run different ways: " + first.divergence[a][b]);
            }
        }
    }

    record Trace(float[] positions, List<Set<Integer>> goalTicks, float[][] divergence) {
    }

    private Trace crowdTrace(boolean distracted) {
        Game g = arena();
        List<Entity> crowd = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            crowd.add(sturdy(g.entities.spawnNpc(g.world, "Villager " + i, 320.5f + i * 3, FEET, 320.5f)));
        }
        CreatureType[] types = CreatureType.values();
        for (int i = 0; i < types.length; i++) {
            float y = types[i].flying ? FEET + 4f : FEET;
            crowd.add(sturdy(g.entities.spawnCreature(g.world, types[i], 320.5f + i * 4, y, 328.5f)));
        }
        for (Entity e : crowd) {
            // Touched at the centre: nothing to run from but the way it faces.
            g.combustion.ignite(g, e, CombustionSource.DIRECT_HIT, 1f, false, 1, e.pos.x, e.pos.y + 0.5f, e.pos.z);
        }
        int ticks = 60;
        float[] positions = new float[ticks * crowd.size() * 3];
        List<Set<Integer>> goalTicks = new ArrayList<>();
        int[] lastGoals = new int[crowd.size()];
        for (int i = 0; i < crowd.size(); i++) {
            goalTicks.add(new HashSet<>());
        }
        float[][] divergence = new float[4][4];
        int k = 0;
        for (int t = 0; t < ticks; t++) {
            if (distracted) {
                g.camera.yaw = t * 37f;
                g.camera.pitch = (t % 11) * 7f - 35f;
                g.player.pos.set(300.5f + t * 0.4f, FEET, 305.5f);
                g.player.yaw = g.camera.yaw;
                g.particles.muzzleFlash(310f, 41f, 310f, 1f, 0f, 0f);
                g.particles.update(DT, g.world);
            }
            g.combustion.fastTick(g, DT);
            g.entities.fastTick(g, DT);
            for (int i = 0; i < crowd.size(); i++) {
                Entity e = crowd.get(i);
                assertTrue(panicOf(e).active());
                positions[k++] = e.pos.x;
                positions[k++] = e.pos.y;
                positions[k++] = e.pos.z;
                int goals = panicOf(e).goals();
                if (goals != lastGoals[i]) {
                    goalTicks.get(i).add(t);
                    lastGoals[i] = goals;
                }
            }
            for (int a = 0; a < 4; a++) {
                for (int b = a + 1; b < 4; b++) {
                    Entity ea = crowd.get(a), eb = crowd.get(b);
                    float dx = (ea.pos.x - (320.5f + a * 3)) - (eb.pos.x - (320.5f + b * 3));
                    float dz = ea.pos.z - eb.pos.z;
                    divergence[a][b] = Math.max(divergence[a][b], flat(dx, dz));
                }
            }
        }
        return new Trace(positions, goalTicks, divergence);
    }

    // ------------------------------------------------------------------
    // Obstacles, cages, cliffs and the edge of the loaded world
    // ------------------------------------------------------------------

    /**
     * A runner whose goal was clear when chosen finds walls thrown up round
     * it on three sides: it replans, never more than once a quarter second,
     * turns at a bounded rate rather than spinning, and leaves by the open side.
     */
    @Test
    void wallsThrownUpInTheWayMakeTheRunnerReplanWithinItsBoundAndFindTheWayOut() {
        Game g = arena();
        Npc runner = sturdy(g.entities.spawnNpc(g.world, "Runner", HERE_X, FEET, HERE_Z));
        runner.yaw = 90f;
        g.combustion.ignite(g, runner, CombustionSource.DIRECT_HIT, 1f, false, 1, HERE_X - 0.3f, FEET + 1f, HERE_Z);
        aiTick(g);
        assertTrue(runner.panic.hasGoal());
        assertTrue(runner.panic.headingX() > 0f, "fixture: it runs east, away from the touch");
        // Three walls, too tall to hop, open to the west.
        fill(g, 333, 333, 40, 42, 327, 334, BlockType.STONE);
        fill(g, 327, 333, 40, 42, 327, 327, BlockType.STONE);
        fill(g, 327, 333, 40, 42, 334, 334, BlockType.STONE);

        boolean out = false;
        float lastYaw = runner.yaw;
        float maxTurn = TURN_RATE_DEGREES * DT + 0.01f;
        int ticks = 0;
        while (ticks < 100 && !out) {
            aiTick(g);
            ticks++;
            assertFalse(runner.collidesAt(runner.pos.x, runner.pos.y, runner.pos.z), "through a wall at tick " + ticks);
            assertTrue(Float.isFinite(runner.yaw), "a finite heading");
            float turn = Math.abs(wrapDegrees(runner.yaw - lastYaw));
            assertTrue(turn <= maxTurn, "turned " + turn + " degrees in one tick at " + ticks);
            lastYaw = runner.yaw;
            out = runner.pos.x < 327f;
        }
        assertTrue(out, "it found the open side within five seconds");
        assertTrue(runner.panic.replans() >= 1, "the walls made it replan");
        assertTrue(runner.panic.goals() <= 1 + (ticks + 1) * DT / REPLAN_COOLDOWN + 1e-3f,
                runner.panic.goals() + " goals in " + ticks + " ticks is over the bound");
    }

    /**
     * Walled and roofed into one cell, a person and a hare struggle where they
     * stand: no slipping through the walls, no teleport, no second physics
     * step, no spin on a zero direction, and goals no faster than the bound.
     */
    @Test
    void aBodyWalledInStrugglesWhereItIsWithoutSpinningOrSlippingThrough() {
        Game g = arena();
        List<Entity> trapped = List.of(
                sturdy(g.entities.spawnNpc(g.world, "Walled in", 320.5f, FEET, 320.5f)),
                sturdy(g.entities.spawnCreature(g.world, CreatureType.HARE, 340.5f, FEET, 320.5f)));
        for (Entity e : trapped) {
            int x = (int) Math.floor(e.pos.x), z = (int) Math.floor(e.pos.z);
            fill(g, x - 1, x + 1, 40, 42, z - 1, z + 1, BlockType.STONE);
            fill(g, x, x, 40, 41, z, z, BlockType.AIR);
            g.combustion.ignite(g, e, CombustionSource.DIRECT_HIT, 1f, false, 1, e.pos.x, e.pos.y + 0.3f, e.pos.z);
        }
        float[] lastYaw = {trapped.get(0).yaw, trapped.get(1).yaw};
        for (int t = 1; t <= 100; t++) {
            float[] px = {trapped.get(0).pos.x, trapped.get(1).pos.x};
            float[] pz = {trapped.get(0).pos.z, trapped.get(1).pos.z};
            aiTick(g);
            for (int i = 0; i < trapped.size(); i++) {
                Entity e = trapped.get(i);
                int x = (int) Math.floor(i == 0 ? 320.5f : 340.5f);
                assertTrue(e.pos.x - e.width / 2 >= x - 1e-3f && e.pos.x + e.width / 2 <= x + 1 + 1e-3f
                        && e.pos.z - e.width / 2 >= 320 - 1e-3f && e.pos.z + e.width / 2 <= 321 + 1e-3f,
                        e + " stays in its cell at tick " + t);
                float step = flat(e.pos.x - px[i], e.pos.z - pz[i]);
                assertTrue(step <= panicSpeed(e) * DT * 1.02f + 1e-3f, "one physics step at tick " + t);
                assertTrue(Float.isFinite(e.yaw), "a finite heading");
                assertTrue(Math.abs(wrapDegrees(e.yaw - lastYaw[i])) <= TURN_RATE_DEGREES * DT + 0.01f,
                        "no spin at tick " + t);
                lastYaw[i] = e.yaw;
                PanicIntent p = panicOf(e);
                assertEquals(1f, flat(p.headingX(), p.headingZ()), 1e-3f, "never a zero direction");
            }
        }
        for (Entity e : trapped) {
            PanicIntent p = panicOf(e);
            assertTrue(p.trappedGoals() >= 1, e + " knew it had no way out");
            assertTrue(p.goals() <= 1 + 100 * DT / REPLAN_COOLDOWN + 1e-3f, "bounded: " + p.goals());
        }
    }

    /** A burning captive moves inside its cage, stays a captive and can still be rescued. */
    @Test
    void aBurningCaptiveStrugglesInsideItsCageAndIsNeverFreedByPanic() {
        Game g = arena();
        Npc captive = sturdy(captive(g));
        Settlement fort = g.world.settlements.get(captive.settlementId);
        assertTrue(g.liquidFire.spill(g, captive.pos.x, FEET + 0.5f, captive.pos.z, 0, 0, true) > 0);
        float path = 0f;
        for (int t = 0; t < 80; t++) {
            float px = captive.pos.x, pz = captive.pos.z;
            gameTick(g);
            assertTrue(captive.pos.x - captive.width / 2 > CAGE_X + 1 - 1e-3f
                    && captive.pos.x + captive.width / 2 < CAGE_X + 3 + 1e-3f
                    && captive.pos.z - captive.width / 2 > CAGE_Z + 1 - 1e-3f
                    && captive.pos.z + captive.width / 2 < CAGE_Z + 3 + 1e-3f, "inside the bars at tick " + t);
            path += flat(captive.pos.x - px, captive.pos.z - pz);
        }
        assertTrue(captive.panic.active());
        assertTrue(path > 0.3f, "it struggles about the cage: " + path);
        assertTrue(g.entities.npcs.contains(captive), "still in the world");
        assertFalse(fort.residents.get(captive.residentIndex).rescued, "panic freed nobody");
        assertTrue(g.settlementManager.canRescueCaptive(g, captive), "and the player still can");
    }

    /**
     * A burning bird escapes by flying: up and away, and round a tower in its
     * way. A flock under a wide roof flies level below it rather than up into
     * it until each is out from under it; no bird ever passes through a block.
     */
    @Test
    void aBurningBirdFliesItsEscapeUpwardRoundTowersAndLevelUnderARoof() {
        Game g = arena();
        fill(g, 333, 336, 40, 75, 327, 334, BlockType.STONE);
        Creature bird = sturdy(g.entities.spawnCreature(g.world, CreatureType.BIRD, HERE_X, FEET + 5f, HERE_Z));
        bird.yaw = 90f;
        g.combustion.ignite(g, bird, CombustionSource.DIRECT_HIT, 1f, false, 1, HERE_X - 0.2f, bird.pos.y + 0.17f, HERE_Z);

        fill(g, 295, 320, 44, 44, 295, 320, BlockType.STONE);
        List<Creature> flock = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Creature c = sturdy(g.entities.spawnCreature(g.world, CreatureType.BIRD, 298.5f + i * 3,
                    FEET + 1.5f + (i % 3) * 0.6f, 298.5f + (i * 5) % 20));
            c.yaw = i * 45f;
            g.combustion.ignite(g, c, CombustionSource.DIRECT_HIT, 1f, false, 1, c.pos.x - 0.2f, c.pos.y + 0.17f, c.pos.z);
            flock.add(c);
        }

        float startY = bird.pos.y;
        float highest = startY;
        float farthest = 0f;
        boolean[] leftRoof = new boolean[flock.size()];
        float[] sx = new float[flock.size()], sz = new float[flock.size()], flown = new float[flock.size()];
        for (int i = 0; i < flock.size(); i++) {
            sx[i] = flock.get(i).pos.x;
            sz[i] = flock.get(i).pos.z;
        }
        for (int t = 0; t < 60; t++) {
            aiTick(g);
            List<Creature> all = new ArrayList<>(flock);
            all.add(bird);
            for (Creature c : all) {
                assertFalse(c.collidesAt(c.pos.x, c.pos.y, c.pos.z), "inside a block at tick " + t);
                assertTrue(g.world.getChunk(Math.floorDiv((int) Math.floor(c.pos.x), Chunk.SX),
                        Math.floorDiv((int) Math.floor(c.pos.z), Chunk.SZ)) != null, "over loaded ground");
            }
            highest = Math.max(highest, bird.pos.y);
            farthest = Math.max(farthest, flat(bird.pos.x - HERE_X, bird.pos.z - HERE_Z));
            for (int i = 0; i < flock.size(); i++) {
                Creature c = flock.get(i);
                flown[i] = Math.max(flown[i], flat(c.pos.x - sx[i], c.pos.z - sz[i]));
                boolean underRoof = c.pos.x + c.width / 2 > 295f && c.pos.x - c.width / 2 < 321f
                        && c.pos.z + c.width / 2 > 295f && c.pos.z - c.width / 2 < 321f;
                leftRoof[i] |= !underRoof;
                if (!leftRoof[i]) {
                    assertTrue(c.pos.y + c.height < 44f - 0.1f && c.pos.y >= FEET - 1e-3f,
                            "bird " + i + " flies level below the roof, not up against it, at tick " + t);
                }
            }
        }
        assertTrue(highest >= startY + 2f, "it climbed: " + (highest - startY));
        assertTrue(farthest >= 4f, "and flew off: " + farthest);
        assertTrue(bird.panic.goals() >= 3);
        for (int i = 0; i < flock.size(); i++) {
            assertTrue(flown[i] >= 3f, "roofed bird " + i + " flew off: " + flown[i]);
            assertTrue(flock.get(i).panic.goals() >= 3);
        }
    }

    /**
     * Driven toward a seven-block pit and toward the edge of the loaded world,
     * panicking bodies stop short of both and turn away: nobody falls, no part
     * of a body crosses into an unloaded column, and nothing is loaded. They
     * start at the very brink, running at it, and the pit opens across a
     * runner's way only after it chose its goal, so no goal choice can keep
     * them off it: only the check of the ground ahead can.
     */
    @Test
    void panicStopsShortOfADeepDropAndOfTheEdgeOfTheLoadedWorld() {
        Game g = arena();
        assertNull(g.world.getChunk(17, 20), "fixture: west of the arena is not loaded");
        fill(g, 322, 330, 33, 39, 312, 328, BlockType.AIR);
        Creature hare = sturdy(g.entities.spawnCreature(g.world, CreatureType.HARE, 288.25f, FEET, 320.5f));
        hare.yaw = 270f;
        Creature deer = sturdy(g.entities.spawnCreature(g.world, CreatureType.DEER, 321.6f, FEET, 320.5f));
        deer.yaw = 90f;
        Npc person = sturdy(g.entities.spawnNpc(g.world, "Brink", 321.7f, FEET, 316.5f));
        person.yaw = 90f;
        Creature runner = sturdy(g.entities.spawnCreature(g.world, CreatureType.THORNHORN, 340.5f, FEET, 340.5f));
        runner.yaw = 90f;
        g.combustion.ignite(g, hare, CombustionSource.DIRECT_HIT, 1f, false, 1, 288.4f, FEET + 0.2f, 320.5f);
        g.combustion.ignite(g, deer, CombustionSource.DIRECT_HIT, 1f, false, 1, 321.3f, FEET + 0.5f, 320.5f);
        g.combustion.ignite(g, person, CombustionSource.DIRECT_HIT, 1f, false, 1, 321.4f, FEET + 1f, 316.5f);
        g.combustion.ignite(g, runner, CombustionSource.DIRECT_HIT, 1f, false, 1, 340.0f, FEET + 0.7f, 340.5f);
        aiTick(g);
        // A pit opens across the runner's way, whichever way east it chose.
        int x = (int) Math.floor(runner.pos.x + runner.width / 2) + 1;
        fill(g, x, x + 8, 33, 39, 330, 350, BlockType.AIR);
        for (int t = 0; t < 100; t++) {
            aiTick(g);
            assertTrue(hare.pos.x - hare.width / 2 >= 288f - 1e-3f, "the hare stays over loaded ground, tick " + t);
            for (Entity e : List.of(hare, deer, person, runner)) {
                assertTrue(e.pos.y > FEET - 0.1f, e + " never fell, tick " + t);
            }
        }
        for (Creature c : List.of(hare, deer, runner)) {
            assertTrue(c.panic.goals() >= 3, "it kept choosing where to run");
        }
        assertNull(g.world.getChunk(17, 20), "and fleeing loaded nothing");
    }

    // ------------------------------------------------------------------
    // Conversation
    // ------------------------------------------------------------------

    @Test
    void anOpenConversationEndsWhenTheSpeakerCatchesAndCannotStartUntilTheyAreCalm() {
        Game g = arena();
        Npc speaker = sturdy(inConversation(g));
        for (int t = 0; t < 10; t++) {
            gameTick(g);
        }
        assertEquals(Game.UiMode.NPC, g.uiMode, "fixture: talking");
        assertEquals(0f, flat(speaker.vel.x, speaker.vel.z), 0f, "fixture: held still by the talk");

        g.combustion.ignite(g, speaker, CombustionSource.DIRECT_HIT, 1f, true, 1, speaker.pos.x, FEET + 1f, speaker.pos.z);
        gameTick(g);
        assertEquals(Game.UiMode.NONE, g.uiMode, "the screen closed");
        assertNull(g.activeNpc, "and let the speaker go");
        assertEquals(0f, speaker.interactFreeze, 0f);
        assertTrue(g.eventLog.all().stream().anyMatch(line -> line.contains("breaks away")), "the log says why");
        for (int t = 0; t < 10; t++) {
            gameTick(g);
        }
        g.player.pos.set(speaker.pos.x + 1f, FEET, speaker.pos.z);
        assertSame(Game.NpcInteraction.NONE, g.npcInteraction(speaker), "nobody stops to talk while ablaze");
        assertFalse(g.interactWithNearbyNpc());

        g.combustion.extinguish(speaker);
        for (int t = 0; t < RECOVERY_TICKS; t++) {
            gameTick(g);
        }
        assertFalse(speaker.panic.active());
        g.player.pos.set(speaker.pos.x + 1f, FEET, speaker.pos.z);
        assertSame(Game.NpcInteraction.TALK, g.npcInteraction(speaker), "calm again, they can be spoken to");
    }

    // ------------------------------------------------------------------
    // The player keeps every control
    // ------------------------------------------------------------------

    /**
     * R4 among panicking bodies: two games driven by one command sequence
     * through the production movement system and the whole game tick, a
     * burning crowd round the player in both, the player alight in one. The
     * player moves, sprints, crouches and looks identically, and the crowd
     * runs identically too: the player's fire steers nobody.
     */
    @Test
    void thePlayerMovesAndLooksExactlyAsTheyWouldUnburnedAmongPanickingBodies() {
        Game burning = arena();
        Game calm = arena();
        List<List<Entity>> crowds = new ArrayList<>();
        for (Game g : new Game[] {burning, calm}) {
            g.player.pos.set(HERE_X, FEET, HERE_Z);
            List<Entity> crowd = new ArrayList<>();
            crowd.add(sturdy(g.entities.spawnNpc(g.world, "Near", HERE_X + 2f, FEET, HERE_Z)));
            crowd.add(sturdy(g.entities.spawnCreature(g.world, CreatureType.WOLF, HERE_X, FEET, HERE_Z + 2f)));
            crowd.add(sturdy(g.entities.spawnCreature(g.world, CreatureType.BIRD, HERE_X - 2f, FEET + 3f, HERE_Z)));
            for (Entity e : crowd) {
                g.combustion.ignite(g, e, CombustionSource.DIRECT_HIT, 1f, false, 1, g.player.pos.x, e.pos.y + 0.5f,
                        g.player.pos.z);
            }
            crowds.add(crowd);
        }
        burning.combustion.ignite(burning, burning.player, CombustionSource.DIRECT_HIT, 1f, false, 2,
                HERE_X, FEET + 1f, HERE_Z);

        PlayerMovementSystem movement = new PlayerMovementSystem();
        PlayerMovementSystem.Command command = new PlayerMovementSystem.Command();
        PlayerMovementSystem.FrameResult result = new PlayerMovementSystem.FrameResult();
        for (int t = 0; t < 60; t++) {
            command.set(t * 6f, t % 20 < 14, false, t % 9 == 0, false, t < 15, t % 25 == 24, t % 17 == 0, t % 17 == 0);
            for (Game g : new Game[] {burning, calm}) {
                g.camera.yaw = t * 6f;
                g.camera.pitch = (t % 13) * 3f - 20f;
                movement.update(g.player, g.world, command, DT, result);
                g.fastTick(DT);
            }
            assertTrue(burning.player.combustion.burning(), "alight throughout");
            assertEquals(calm.player.pos, burning.player.pos, "position, tick " + t);
            assertEquals(calm.player.vel, burning.player.vel, "velocity, tick " + t);
            assertEquals(calm.player.sprinting, burning.player.sprinting);
            assertEquals(calm.player.crouching, burning.player.crouching);
            assertEquals(calm.player.moveSpeedMul(), burning.player.moveSpeedMul(), 0f);
            assertEquals(calm.camera.yaw, burning.camera.yaw, 0f);
            assertEquals(calm.camera.pitch, burning.camera.pitch, 0f);
            for (int i = 0; i < crowds.get(0).size(); i++) {
                assertEquals(crowds.get(1).get(i).pos, crowds.get(0).get(i).pos, "the crowd, tick " + t);
            }
        }
        assertTrue(burning.player.health < calm.player.health, "the fire only hurts");
    }

    // ------------------------------------------------------------------
    // Staging
    // ------------------------------------------------------------------

    private static final int CAGE_X = 328;
    private static final int CAGE_Z = 328;

    private static Entity animalAtWork(Game g, CreatureType type) {
        float y = type.flying ? FEET + 5f : FEET;
        Creature c = g.entities.spawnCreature(g.world, type, HERE_X, y, HERE_Z);
        switch (type) {
            case DEER, HARE -> c.hunger = 80f; // grazing
            case WOLF -> {
                c.hunger = 80f; // biting the player beside it
                g.player.pos.set(HERE_X + 1.6f, FEET, HERE_Z);
            }
            case THORNHORN -> {
                c.health -= 1f; // charging the player who hurt it
                c.fear = 1f;
                g.player.pos.set(HERE_X + 8f, FEET, HERE_Z);
            }
            default -> {
            }
        }
        return c;
    }

    private static Npc campMember(Game g, int campIndex) {
        g.world.campPos = new Vec3i(322, 40, 322);
        Npc n = g.entities.spawnNpc(g.world, "Camp member", HERE_X, FEET, HERE_Z);
        n.campIndex = campIndex;
        return n;
    }

    private static Npc inConversation(Game g) {
        Npc n = campMember(g, 1);
        g.player.pos.set(HERE_X + 1.2f, FEET, HERE_Z);
        assertTrue(g.interactWithNearbyNpc(), "fixture: the conversation opened");
        assertSame(n, g.activeNpc);
        return n;
    }

    private static Npc wanderingTrader(Game g) {
        Npc n = g.entities.spawnNpc(g.world, "Trader", HERE_X, FEET, HERE_Z);
        n.isTrader = true;
        n.leaveTimer = 600f;
        return n;
    }

    private static Npc raider(Game g) {
        g.world.campPos = new Vec3i(322, 40, 322);
        Npc n = g.entities.spawnNpc(g.world, "Raider", HERE_X, FEET, HERE_Z);
        n.raider = true;
        n.leaveTimer = 600f;
        return n;
    }

    private static Npc sleeper(Game g) {
        g.time.totalMinutes = 23 * 60;
        Settlement village = village(g);
        Npc n = resident(g, village, NpcArchetype.VILLAGER, village.center.x() + 0.5f, village.center.z() + 0.5f);
        for (int t = 0; t < 5; t++) {
            NpcAI.update(g, n, DT);
        }
        assertSame(NpcState.SLEEP, n.state, "fixture: asleep");
        return n;
    }

    private static Npc archerInCombat(Game g) {
        Settlement fort = fort(g);
        Npc archer = resident(g, fort, NpcArchetype.ARCHER, HERE_X, HERE_Z);
        archer.yaw = 90f;
        g.player.pos.set(HERE_X + 14f, FEET, HERE_Z);
        return archer;
    }

    private static Npc guardInMelee(Game g) {
        Settlement fort = fort(g);
        Npc guard = resident(g, fort, NpcArchetype.GUARD, HERE_X, HERE_Z);
        guard.yaw = 90f;
        g.player.pos.set(HERE_X + 1.6f, FEET, HERE_Z);
        return guard;
    }

    private static Npc warParty(Game g, Npc.PartyKind kind, NpcArchetype archetype) {
        Settlement fort = fort(g);
        Npc n = g.entities.spawnNpc(g.world, kind.name(), HERE_X, FEET, HERE_Z);
        n.archetype = archetype;
        n.maxHealth = n.health = archetype.maxHealth;
        n.warParty = true;
        n.partyKind = kind;
        n.originSettlementId = fort.id;
        n.partyFactionId = fort.factionId;
        n.partyMission = Npc.PartyMission.OUTBOUND;
        n.partyDestination.set(360.5f, FEET, 360.5f);
        return n;
    }

    private static Npc counterattacker(Game g) {
        Settlement fort = fort(g);
        Settlement outpost = new Settlement(Settlement.packId(720, 720), 720, 720, SettlementType.VILLAGE,
                new Vec3i(362, 40, 362), HumanFaction.FRONTIER, Settlement.Alignment.FRIENDLY);
        g.world.settlements.put(outpost.id, outpost);
        CounterattackMission mission = new CounterattackMission("panic-test", fort.id, outpost.id,
                HumanFaction.HEADHUNTERS, new Vec3i(322, 40, 322), outpost.center, 1L, 1);
        mission.phase = CounterattackMission.Phase.OUTBOUND;
        g.settlementManager.counterattacks.missions.put(mission.id, mission);
        Npc n = warParty(g, Npc.PartyKind.COUNTERATTACK, NpcArchetype.BRUTE);
        n.partyMissionId = mission.id;
        n.partyTargetSettlementId = outpost.id;
        return n;
    }

    /** {@code SettlementBuilder.prisonCage}: a 4 x 4 stone floor, bars two high round it and a roof of bars. */
    private static Npc captive(Game g) {
        Settlement fort = fort(g);
        for (int x = CAGE_X; x < CAGE_X + 4; x++) {
            for (int z = CAGE_Z; z < CAGE_Z + 4; z++) {
                boolean edge = x == CAGE_X || x == CAGE_X + 3 || z == CAGE_Z || z == CAGE_Z + 3;
                if (edge) {
                    fill(g, x, x, 40, 41, z, z, BlockType.CAGE_BARS);
                }
                g.world.setBlock(x, 42, z, BlockType.CAGE_BARS, false);
            }
        }
        fort.prisonPos = new Vec3i(CAGE_X + 1, 40, CAGE_Z + 1);
        return resident(g, fort, NpcArchetype.CAPTIVE, CAGE_X + 1.5f, CAGE_Z + 1.5f);
    }

    private static Settlement village(Game g) {
        return settlement(g, 700, SettlementType.VILLAGE, new Vec3i(342, 40, 342), HumanFaction.FRONTIER,
                Settlement.Alignment.NEUTRAL);
    }

    private static Settlement fort(Game g) {
        return settlement(g, 710, SettlementType.FORT, new Vec3i(350, 40, 314), HumanFaction.HEADHUNTERS,
                Settlement.Alignment.HOSTILE);
    }

    private static Settlement settlement(Game g, int region, SettlementType type, Vec3i center, String faction,
                                         Settlement.Alignment alignment) {
        long id = Settlement.packId(region, region);
        Settlement s = g.world.settlements.get(id);
        if (s == null) {
            s = new Settlement(id, region, region, type, center, faction, alignment);
            g.world.settlements.put(id, s);
        }
        return s;
    }

    private static Npc resident(Game g, Settlement home, NpcArchetype archetype, float x, float z) {
        Settlement.Resident record = new Settlement.Resident(archetype.displayName, archetype);
        home.residents.add(record);
        Npc n = g.entities.spawnNpc(g.world, archetype.displayName, x, FEET, z);
        n.archetype = archetype;
        n.maxHealth = archetype.maxHealth;
        n.health = archetype.maxHealth;
        n.settlementId = home.id;
        n.residentIndex = home.residents.size() - 1;
        record.live = n;
        return n;
    }

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    /** One tick of the whole game, the medium tick after every tenth; an open NPC screen holds its speaker. */
    private void gameTick(Game g) {
        if (g.uiMode == Game.UiMode.NPC && g.activeNpc != null) {
            g.activeNpc.interactFreeze = 1f; // what NpcScreen.update does every frame
        }
        g.fastTick(DT);
        if (++clock % FAST_PER_MEDIUM == 0) {
            g.mediumTick(MEDIUM_DT);
        }
    }

    /** The fire and the entities alone: combustion, AI, physics, deaths. */
    private static void aiTick(Game g) {
        g.combustion.fastTick(g, DT);
        g.entities.fastTick(g, DT);
    }

    /** Throws a fire bomb flat from 1.5 blocks away at the body's middle and flies it until it breaks. */
    private static void throwAt(Game g, Entity thrower, Entity body) {
        float middle = body.pos.y + body.height * 0.5f + 0.05f;
        assertEquals(1, g.projectiles.fire(g, thrower, true, body.pos.x - 1.5f, middle, body.pos.z,
                1, 0, 0, WeaponRegistry.byId("fire_bomb"), null));
        for (int i = 0; i < 500 && g.projectiles.liveCount() > 0; i++) {
            g.projectiles.update(g, 0.01f);
        }
        assertEquals(0, g.projectiles.liveCount(), "the bottle broke");
    }

    private static PanicIntent panicOf(Entity e) {
        return e instanceof Npc n ? n.panic : ((Creature) e).panic;
    }

    /** The fastest a body runs in panic: its stride at most one. */
    private static float panicSpeed(Entity e) {
        if (e instanceof Npc n) {
            return (n.archetype != null ? n.archetype.speed : LEGACY_PERSON_SPEED) * NPC_SPEED_MUL;
        }
        return ((Creature) e).type.speed * CREATURE_SPEED_MUL;
    }

    /** Sturdy enough that the fire never ends a run, keeping any wound the staging gave it. */
    private static <T extends Entity> T sturdy(T e) {
        float wound = e.maxHealth - e.health;
        e.maxHealth = STURDY;
        e.health = STURDY - wound;
        return e;
    }

    private static float flat(float dx, float dz) {
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private static float wrapDegrees(float a) {
        a %= 360f;
        if (a > 180f) {
            a -= 360f;
        } else if (a < -180f) {
            a += 360f;
        }
        return a;
    }

    private static void fill(Game g, int x0, int x1, int y0, int y1, int z0, int z1, BlockType type) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    g.world.setBlock(x, y, z, type, false);
                }
            }
        }
    }

    static Game arena() {
        return arena(GameMode.SURVIVAL);
    }

    /** A flat stone arena over chunks 18-22, clear noon, nobody about, no camp. */
    static Game arena(GameMode mode) {
        Game g = new Game();
        g.newWorld(777L, true, mode);
        for (int cx = 18; cx <= 22; cx++) {
            for (int cz = 18; cz <= 22; cz++) {
                Chunk c = g.world.getOrCreateChunk(cx, cz);
                for (int lx = 0; lx < Chunk.SX; lx++) {
                    for (int lz = 0; lz < Chunk.SZ; lz++) {
                        for (int y = 0; y < Chunk.SY; y++) {
                            c.set(lx, y, lz, y <= 39 ? BlockType.STONE : BlockType.AIR);
                        }
                    }
                }
                c.recomputeAllHeights();
                c.rebuildLights();
            }
        }
        g.entities.creatures.clear();
        g.entities.npcs.clear();
        g.entities.carcasses.clear();
        g.world.campPos = null;
        g.fire.reset();
        g.noise.reset();
        g.player.pos.set(300.5f, FEET, 300.5f);
        g.camera.position.set(300.5f, FEET + 1.6f, 300.5f);
        g.time.totalMinutes = 12 * 60;
        g.weather.current = Weather.CLEAR;
        g.weather.next = Weather.CLEAR;
        g.weather.blend = 1f;
        g.projectiles.setRandomSeed(23L);
        g.liquidFire.setRandomSeed(29L);
        g.fire.setRandomSeed(31L);
        return g;
    }
}
