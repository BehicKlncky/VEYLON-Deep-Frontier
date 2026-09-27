package com.veylon.entity;

import com.veylon.Game;
import com.veylon.ai.Pathfinder;
import com.veylon.ai.Steering;
import com.veylon.combat.WeaponRegistry;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.settlement.HumanFaction;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementType;
import com.veylon.simulation.LiquidFireSystem.Patch;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static com.veylon.entity.CombustionConstants.AFTERBURN_DPS_CREATURE;
import static com.veylon.entity.CombustionConstants.AFTERBURN_DPS_NPC;
import static com.veylon.entity.CombustionConstants.AFTERBURN_DPS_PLAYER;
import static com.veylon.entity.CombustionConstants.CONTACT_DPS_CREATURE;
import static com.veylon.entity.CombustionConstants.CONTACT_DPS_NPC;
import static com.veylon.entity.CombustionConstants.CONTACT_DPS_PLAYER;
import static com.veylon.entity.CombustionConstants.SHALLOW_WATER_DRAIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The flames the game already has set living bodies alight through one path:
 * a fire bomb breaking on a body, burning liquid, burning blocks, fueled
 * campfires and placed torches, each touching the body's flame box as it moves
 * ({@link BodySweep}), and nothing else — not warmth, light, a lantern, a torch
 * in the hand, a flame behind a wall or under a floor, or one the rain has
 * reached.
 *
 * <p>{@code BodyCombustionTest}'s arena: stone up to y = 39, so bodies stand at
 * y = 40, clear weather, no camp to walk to. Fires are driven as {@code Game}
 * orders them: the body-fire fast tick, where bodies touch flames, then the
 * block and liquid fires' medium tick after every tenth fast tick. Only the
 * tests that say so run AI and physics too ({@code Game.fastTick}) or move the
 * player through the production movement system; otherwise bodies stay where
 * a test puts them, and a test moves one by setting its position between
 * ticks, which is all the fire sees of any movement.
 */
class FlameSourceIgnitionTest {

    private static final float DT = SimulationScheduler.FAST_DT;
    private static final float MEDIUM = SimulationScheduler.MEDIUM_DT;
    private static final int FAST_PER_MEDIUM = Math.round(MEDIUM / DT);
    private static final float FEET = 40f;
    private static final float STURDY = 1000f;
    /** Float resolution at {@link #STURDY} health, summed over a few dozen ticks. */
    private static final float HEALTH_EPS = 5e-3f;
    private static final float FLIGHT_STEP = 0.01f;

    /** Fast ticks run so far, so the medium tick falls on every tenth whatever the test does. */
    private int clock;

    // ------------------------------------------------------------------
    // A fire bomb breaking on a body
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.veylon.entity.BodyCombustionTest#everyLivingBody")
    void aBottleBrokenOnAnyBodySetsItAlightAndItBurnsOnAwayFromThePool(BodyCombustionTest.Target target) {
        Game g = arena();
        Entity body = sturdy(target.spawn(g, 320.5f, FEET, 320.5f));
        boolean playerHit = body instanceof Player;
        // People throw bottles at the player; the player throws them at everyone else.
        Entity thrower = playerHit ? g.entities.spawnNpc(g.world, "Thrower", 316.5f, FEET, 316.5f) : g.player;

        throwAt(g, thrower, !playerHit, body);

        assertEquals(1, g.liquidFire.totalSpills, "the bottle broke");
        assertTrue(g.liquidFire.count() > 0, "and its liquid ran to the feet");
        assertEquals(0, g.noise.countCategory("explosion"), "a molotov, not a bomb");
        assertTrue(g.combustion.isBurning(body), "the body it broke on is alight at once");
        assertSame(CombustionSource.DIRECT_HIT, body.combustion.owner());
        assertEquals(!playerHit, body.combustion.ownerByPlayer(), "whoever threw it owns the fire");

        float before = body.health;
        fires(g, FAST_PER_MEDIUM);
        assertTrue(body.combustion.inContact(), "standing in its pool it keeps touching the flames");
        assertSame(CombustionSource.LIQUID, body.combustion.owner(), "the pool has taken the fire over");
        assertEquals(contactDps(body) * FAST_PER_MEDIUM * DT, before - body.health, HEALTH_EPS,
                "half a second in the flames at full strength, once per tick");

        body.pos.set(345.5f, FEET, 345.5f);
        float away = body.health;
        for (int i = 0; i < 40; i++) {
            fires(g, 1);
            assertTrue(g.combustion.isBurning(body), "still alight " + i + " ticks after leaving");
            assertFalse(body.combustion.inContact(), "with no flame anywhere near it");
        }
        assertEquals(afterburnDps(body) * 40 * DT, away - body.health, HEALTH_EPS,
                "two seconds of afterburn, with fuel to spare");
    }

    @Test
    void aCreativePlayerHitByABottleNeverBurnsWhileThoseBesideThemDo() {
        Game g = arena();
        assertTrue(g.switchGameMode(GameMode.CREATIVE));
        g.player.pos.set(320.5f, FEET, 320.5f);
        Npc neighbour = sturdy(g.entities.spawnNpc(g.world, "Neighbour", 321.5f, FEET, 320.5f));
        Creature deer = sturdy(g.entities.spawnCreature(g.world, Creature.CreatureType.DEER, 320.5f, FEET, 321.5f));
        Npc thrower = g.entities.spawnNpc(g.world, "Thrower", 316.5f, FEET, 316.5f);

        throwAt(g, thrower, false, g.player);
        assertTrue(g.liquidFire.count() > 0, "precondition: the bottle broke at the player's feet");
        fires(g, 3 * FAST_PER_MEDIUM);

        assertFalse(g.player.combustion.burning(), "Creative: neither the hit nor the pool lights the player");
        assertEquals(g.player.maxHealth, g.player.health);
        assertEquals(0f, g.player.damageFlash);
        assertFalse(g.player.has(Affliction.BURN));
        assertTrue(g.combustion.isBurning(neighbour), "a person in the same pool burns");
        assertTrue(g.combustion.isBurning(deer), "and so does an animal");
    }

    /**
     * A bird hit six blocks up: the liquid finds no ground within reach, but
     * the bird is alight anyway, flies on under its own AI in the real game
     * tick, burns to death in the air and is the thrower's kill.
     */
    @Test
    void aBottleThatBreaksOnABirdHighInTheAirSetsItAlightWithoutAnyPool() {
        Game g = arena();
        Creature bird = g.entities.spawnCreature(g.world, Creature.CreatureType.BIRD, 320.5f, 46.5f, 320.5f);
        int meat = g.player.inventory.count(ItemType.RAW_MEAT);

        throwAt(g, g.player, true, bird);

        assertEquals(1, g.liquidFire.totalSpills, "the bottle broke on the bird");
        assertEquals(0, g.liquidFire.count(), "and its liquid found no ground in reach");
        assertTrue(g.combustion.isBurning(bird), "yet the bird is alight");
        assertSame(CombustionSource.DIRECT_HIT, bird.combustion.owner());
        assertTrue(bird.combustion.ownerByPlayer());

        int ticks = 0;
        while (g.entities.creatures.contains(bird) && ticks < 200) {
            gameTick(g);
            ticks++;
        }
        assertTrue(bird.dead, "it burned to death");
        assertTrue(ticks > 10, "long after the bottle broke: " + ticks + " ticks");
        assertTrue(bird.lastHitByPlayer, "the thrower's kill");
        assertEquals(meat + 1, g.player.inventory.count(ItemType.RAW_MEAT), "and the thrower's bird");
        assertEquals(0, g.noise.countCategory("explosion"));
    }

    /**
     * The agreed suppression policy: a bottle breaking on a body whose torso
     * is under water ignites nothing, and water to the hips is not the torso.
     */
    @Test
    void aBottleOnABodyUnderWaterIgnitesNothingButWaterToTheHipsDoesNotSaveIt() {
        Game g = arena();
        fill(g, 318, 322, 40, 40, 318, 322, BlockType.WATER);
        fill(g, 328, 332, 39, 40, 328, 332, BlockType.WATER);
        Creature deer = sturdy(g.entities.spawnCreature(g.world, Creature.CreatureType.DEER, 320.5f, FEET, 320.5f));
        Npc wader = sturdy(g.entities.spawnNpc(g.world, "Wader", 321.5f, FEET, 318.5f));
        Npc swimmer = sturdy(g.entities.spawnNpc(g.world, "Swimmer", 330.5f, 39f, 330.5f));

        throwAt(g, g.player, true, deer);
        throwAt(g, g.player, true, wader);
        throwAt(g, g.player, true, swimmer);

        assertEquals(3, g.liquidFire.totalSpills, "three bottles broke");
        assertEquals(0, g.liquidFire.count(), "all in water, so no pool anywhere");
        assertFalse(deer.combustion.burning(), "one cell of water covers an animal's body");
        assertFalse(swimmer.combustion.burning(), "nor does a person under water catch");
        assertTrue(g.combustion.isBurning(wader), "water to the hips is not the torso: the wader burns");
        float fuel = wader.combustion.fuel();
        fires(g, 2);
        assertEquals(fuel - SHALLOW_WATER_DRAIN * DT, wader.combustion.fuel(), 1e-4f,
                "though the fire burns down faster (the hit's own contact tick first)");
    }

    // ------------------------------------------------------------------
    // Brief contact: the sweep between fast ticks
    // ------------------------------------------------------------------

    /**
     * One patch of liquid on top of a three-block pillar, crossed at 12
     * blocks a second, the whole crossing between two medium ticks: a check
     * on the medium tick alone found the body standing in it never.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("com.veylon.entity.BodyCombustionTest#everyLivingBody")
    void aCrossingThatFallsBetweenTwoMediumTicksStillCatches(BodyCombustionTest.Target target) {
        Game g = arena();
        Patch patch = onePatchOnAPillar(g, 330, 330);
        Entity body = sturdy(target.spawn(g, 326.5f, patch.y, 330.5f));
        fires(g, FAST_PER_MEDIUM);
        assertFalse(standsIn(body, patch), "precondition: out of the flames at one medium tick");
        assertFalse(body.combustion.burning());

        for (int i = 0; i < FAST_PER_MEDIUM; i++) {
            body.pos.x += 12f * DT;
            fires(g, 1);
        }

        assertFalse(standsIn(body, patch), "precondition: and past them at the next");
        assertTrue(g.combustion.isBurning(body), "the crossing in between caught all the same");
        assertSame(CombustionSource.LIQUID, body.combustion.owner());
        assertFalse(body.combustion.inContact(), "and it burns on away from the patch");
    }

    /**
     * Faster still: the body is short of the patch at one fast tick and past
     * it at the next, never over it at any sample. Only the sweep sees it.
     * The same dash a hair to the side touches nothing.
     */
    @Test
    void aDashAcrossAFlameBetweenTwoFastTicksCatchesAndOneBesideItDoesNot() {
        for (Creature.CreatureType type : List.of(Creature.CreatureType.HARE, Creature.CreatureType.BIRD,
                Creature.CreatureType.DEER)) {
            dashAcross(type.name(), g -> g.entities.spawnCreature(g.world, type, 0, 0, 0));
        }
        dashAcross("person", g -> g.entities.spawnNpc(g.world, "Runner", 0, 0, 0));
    }

    private void dashAcross(String label, java.util.function.Function<Game, Entity> spawn) {
        for (boolean beside : new boolean[] {false, true}) {
            Game g = arena();
            Patch patch = onePatchOnAPillar(g, 330, 330);
            Entity body = sturdy(spawn.apply(g));
            float hw = BodySweep.halfWidth(body);
            float z = beside ? patch.z + 1 + hw + 0.01f : patch.z + 0.5f;
            body.pos.set(patch.x - hw - 0.01f, patch.y, z);
            fires(g, 1);
            body.pos.x = patch.x + 1 + hw + 0.01f;
            assertTrue(body.pos.x - (patch.x - hw - 0.01f) <= BodySweep.MAX_SWEEP,
                    label + ": precondition: one tick's dash, not a jump");
            fires(g, 1);
            if (beside) {
                assertNeverTouched(body, label + ": a dash a hair beside the flame misses it");
            } else {
                assertTrue(g.combustion.isBurning(body), label + ": the sweep caught the dash");
            }
        }
    }

    @Test
    void aJumpIsNotASweepThroughWhateverLayBetween() {
        Game g = arena();
        Patch patch = onePatchOnAPillar(g, 330, 330);
        Npc runner = sturdy(g.entities.spawnNpc(g.world, "Runner", patch.x - 1.5f, patch.y, patch.z + 0.5f));
        fires(g, 1);
        runner.pos.x = patch.x + 2.6f;
        fires(g, 1);
        assertNeverTouched(runner, "a move longer than a sweep samples only where it ends");
    }

    @Test
    void liquidStaysNearItsSurfaceAndABirdsWingsReachFurtherThanItsBody() {
        Game g = arena();
        Patch patch = onePatchOnAPillar(g, 330, 330);
        Creature over = sturdy(g.entities.spawnCreature(g.world, Creature.CreatureType.BIRD,
                patch.x + 0.5f, patch.y + 1f, patch.z + 0.5f));
        Creature skimming = sturdy(g.entities.spawnCreature(g.world, Creature.CreatureType.BIRD,
                patch.x + 0.5f, patch.y + 0.7f, patch.z + 0.5f));
        fires(g, 2);
        assertNeverTouched(over, "a bird flying a block over the pool is out of its reach");
        assertTrue(g.combustion.isBurning(skimming), "one skimming it dips its wings in");

        // A torch flame at wing's reach from a bird's body, and at the same
        // distance from a hare's: only the wings touch it.
        Game t = arena();
        t.world.setBlock(320, 40, 320, BlockType.TORCH, false);
        float edge = 320.62f;
        Creature bird = sturdy(t.entities.spawnCreature(t.world, Creature.CreatureType.BIRD,
                edge + 0.3f, 40.7f, 320.5f));
        Creature hare = sturdy(t.entities.spawnCreature(t.world, Creature.CreatureType.HARE,
                edge + 0.3f, 40.7f, 320.5f));
        assertTrue(bird.width / 2f < 0.3f, "precondition: the bird's body box stops short of the flame");
        fires(t, 1);
        assertTrue(bird.combustion.heat() > 0f, "a wing in a torch flame heats the bird");
        assertNeverTouched(hare, "the hare beside it touches nothing");
    }

    // ------------------------------------------------------------------
    // Each flame the world has, walked through by the player
    // ------------------------------------------------------------------

    @Test
    void runningThroughABurningPoolLightsThePlayerAndTheFireOutlastsIt() {
        Game g = arena();
        g.liquidFire.spill(g, 320.5f, 40.5f, 300.5f, 0, 0, false);
        g.player.pos.set(312.5f, FEET, 300.5f);
        walk(g, 90f, true, 60, 328.5f);
        assertTrue(g.player.pos.x >= 328.5f, "precondition: the player ran right through it");
        assertTrue(g.combustion.isBurning(g.player), "and caught");
        assertSame(CombustionSource.LIQUID, g.player.combustion.owner());

        walk(g, 90f, false, 20, Float.MAX_VALUE);
        assertTrue(g.combustion.isBurning(g.player), "still alight a second after leaving the pool");
        assertFalse(g.player.combustion.inContact());
    }

    @Test
    void walkingThroughABurningBushLightsTheWalkerAndWalkingPastItDoesNot() {
        for (boolean through : new boolean[] {false, true}) {
            Game g = arena();
            fill(g, 320, 321, 40, 40, 290, 290, BlockType.BUSH);
            assertTrue(g.fire.ignite(g, 320, 40, 290) && g.fire.ignite(g, 321, 40, 290));
            g.player.pos.set(316.5f, FEET, through ? 290.5f : 291.85f);
            walk(g, 90f, false, 60, 325.5f);
            assertTrue(g.player.pos.x >= 325.5f, "precondition: walked the length of the bushes");
            if (!through) {
                assertNeverTouched(g.player,
                        "walking past them a hand's width away, the player never felt the flame");
                continue;
            }
            assertTrue(g.combustion.isBurning(g.player), "walking through them catches");
            assertSame(CombustionSource.BLOCK_FIRE, g.player.combustion.owner());
            assertFalse(g.player.combustion.ownerByPlayer(), "a fire nobody lit is nobody's");
            walk(g, 90f, false, 20, Float.MAX_VALUE);
            assertTrue(g.combustion.isBurning(g.player), "and the player burns on after leaving them");
        }
    }

    @Test
    void aCampfireCatchesWhoeverStandsInItButNotWhoeverBrushesThrough() {
        Game g = arena();
        campfire(g, 320, 40, 330);
        g.player.pos.set(317.5f, FEET, 330.5f);
        walk(g, 90f, false, 40, 323.5f);
        assertTrue(g.player.pos.x >= 323.5f, "precondition: walked straight through it");
        assertFalse(g.player.combustion.burning(), "brushing through a campfire does not catch");

        g.player.pos.set(320.5f, FEET, 330.5f);
        walk(g, 0f, false, 30, Float.MAX_VALUE);
        assertTrue(g.combustion.isBurning(g.player), "standing in it for a second and a half does");
        assertSame(CombustionSource.CAMPFIRE, g.player.combustion.owner());
        walk(g, 90f, false, 20, Float.MAX_VALUE);
        assertTrue(g.player.pos.x > 322f, "precondition: out of it");
        assertTrue(g.combustion.isBurning(g.player), "and the player burns on after stepping out");
    }

    @Test
    void aTorchFlameCatchesOnlyWhoLingersInItAndOnlyAtItsHeight() {
        Game g = arena();
        g.world.setBlock(320, 40, 340, BlockType.TORCH, false);
        g.player.pos.set(317.5f, FEET, 340.5f);
        walk(g, 90f, false, 40, 323.5f);
        assertTrue(g.player.pos.x >= 323.5f, "precondition: walked through the torch");
        assertFalse(g.player.combustion.burning(), "walking past a torch is safe");

        g.player.pos.set(320.5f, FEET, 340.5f);
        walk(g, 0f, false, 40, Float.MAX_VALUE);
        assertTrue(g.combustion.isBurning(g.player), "holding still in its flame for two seconds is not");
        assertSame(CombustionSource.TORCH, g.player.combustion.owner());
        walk(g, 90f, false, 10, Float.MAX_VALUE);
        assertTrue(g.combustion.isBurning(g.player), "the fire outlasts stepping away from it");

        // A hare's back is below a standing torch's flame; a person's legs are not.
        Game h = arena();
        h.world.setBlock(320, 40, 340, BlockType.TORCH, false);
        Creature hare = sturdy(h.entities.spawnCreature(h.world, Creature.CreatureType.HARE, 320.5f, FEET, 340.5f));
        Npc person = sturdy(h.entities.spawnNpc(h.world, "Person", 320.5f, FEET, 340.5f));
        fires(h, 60);
        assertNeverTouched(hare, "the flame burns above a hare sitting under it");
        assertTrue(h.combustion.isBurning(person), "and catches the person standing in it");
    }

    // ------------------------------------------------------------------
    // What is not a flame, or cannot reach
    // ------------------------------------------------------------------

    @Test
    void warmthLightHeldTorchesAndEnclosedFlamesAreNotContact() {
        Game g = arena();
        campfire(g, 320, 40, 320);
        g.player.pos.set(321.9f, FEET, 320.5f);
        g.player.inventory.set(0, new ItemStack(ItemType.TORCH, 1));
        g.player.hotbarSel = 0;
        g.mediumTick(MEDIUM);
        assertTrue(g.player.nearFireHeat(g) > 0f, "precondition: the campfire warms the player beside it");
        Npc[] beside = {
                standingIn(g, BlockType.LANTERN, 326), standingIn(g, BlockType.TRAIL_MARKER, 328),
                standingIn(g, BlockType.GLOW_FUNGUS, 330), standingIn(g, BlockType.ALARM_BELL, 332)};
        Npc byFurnace = sturdy(g.entities.spawnNpc(g.world, "Smith", 335.3f, FEET, 320.5f));
        g.world.setBlock(334, 40, 320, BlockType.FURNACE, false);
        Npc unfueled = standingIn(g, BlockType.CAMPFIRE, 338);

        fires(g, 200);

        assertNeverTouched(g.player, "ten seconds of warmth, a torch in hand: no flame");
        for (Npc n : beside) {
            assertNeverTouched(n, n.name + ": light and enclosed flames are not contact");
        }
        assertNeverTouched(byFurnace, "nor is a furnace beside you");
        assertNeverTouched(unfueled, "a campfire with no fuel is out");
    }

    /**
     * Heat never crosses a floor or comes up through the ground: the old
     * contact check burned a person standing on stone over a buried burning
     * log, or on a slab over one. Standing on a burning log itself, or with a
     * burning crown at head height beside you, does touch the flames.
     */
    @Test
    void flamesDoNotReachThroughAFloorButDoRiseAndLickSideways() {
        Game g = arena();
        g.world.setBlock(320, 38, 320, BlockType.LOG, false);
        g.world.setBlock(325, 40, 320, BlockType.LOG, false);
        g.world.setBlock(325, 41, 320, BlockType.STONE, false);
        g.world.setBlock(330, 40, 320, BlockType.LOG, false);
        g.world.setBlock(340, 41, 320, BlockType.LEAVES, false);
        for (Vec3i fire : List.of(new Vec3i(320, 38, 320), new Vec3i(325, 40, 320), new Vec3i(330, 40, 320),
                new Vec3i(340, 41, 320))) {
            assertTrue(g.fire.ignite(g, fire.x(), fire.y(), fire.z()));
        }
        Npc overBuried = sturdy(g.entities.spawnNpc(g.world, "Over the buried log", 320.5f, FEET, 320.5f));
        Npc onSlab = sturdy(g.entities.spawnNpc(g.world, "On the slab", 325.5f, 42f, 320.5f));
        Npc onLog = sturdy(g.entities.spawnNpc(g.world, "On the log", 330.5f, 41f, 320.5f));
        Npc underCrown = sturdy(g.entities.spawnNpc(g.world, "Beside the crown", 341.3f, FEET, 320.5f));

        fires(g, 20);

        assertNeverTouched(overBuried, "no heat up through the ground");
        assertNeverTouched(onSlab, "nor through a slab laid over a fire");
        assertTrue(g.combustion.isBurning(onLog), "standing on a burning log is standing in its flames");
        assertTrue(g.combustion.isBurning(underCrown), "flames lick sideways out of a burning crown at head height");
        assertEquals(STURDY, overBuried.health, 0f);
        assertEquals(STURDY, onSlab.health, 0f);
    }

    /**
     * A flame that is out is out, whatever list or picture still shows it
     * until the next medium tick: a bush pulled up, a pool flooded, a pool or
     * a burning block the rain is reaching. Under a roof they burn on.
     */
    @Test
    void flamesThatWentOutOrThatRainReachesSetNobodyAlight() {
        Game g = arena();
        g.world.setBlock(320, 40, 320, BlockType.BUSH, false);
        assertTrue(g.fire.ignite(g, 320, 40, 320));
        g.world.setBlock(320, 40, 320, BlockType.AIR, false);
        Npc wherePulled = sturdy(g.entities.spawnNpc(g.world, "Where the bush was", 320.5f, FEET, 320.5f));
        assertTrue(g.liquidFire.spill(g, 330.5f, 40.5f, 330.5f, 0, 0, true) > 0);
        g.world.setBlock(330, 40, 330, BlockType.WATER, false);
        Npc inFlood = sturdy(g.entities.spawnNpc(g.world, "In the flood", 330.5f, FEET, 330.5f));
        fires(g, 3);
        assertNeverTouched(wherePulled, "a bush that is gone is not burning");
        assertNeverTouched(inFlood, "nor is liquid that water has covered");

        Game wet = arena();
        setWeather(wet, Weather.RAIN);
        fill(wet, 296, 304, 43, 43, 296, 304, BlockType.STONE);
        for (int x : new int[] {300, 320}) {
            wet.world.setBlock(x, 40, 300, BlockType.BUSH, false);
            assertTrue(wet.fire.ignite(wet, x, 40, 300));
        }
        wet.liquidFire.spill(wet, 320.5f, 40.5f, 320.5f, 0, 0, true);
        Npc inRainedBush = sturdy(wet.entities.spawnNpc(wet.world, "Rained bush", 320.5f, FEET, 300.5f));
        Npc inRainedPool = sturdy(wet.entities.spawnNpc(wet.world, "Rained pool", 320.5f, FEET, 320.5f));
        Npc inRoofedBush = sturdy(wet.entities.spawnNpc(wet.world, "Roofed bush", 300.5f, FEET, 300.5f));
        fires(wet, 8);
        assertNeverTouched(inRainedBush, "a burning bush out in the rain touches nobody");
        assertNeverTouched(inRainedPool, "a pool out in the rain lights nobody, even before it soaks");
        assertTrue(wet.combustion.isBurning(inRoofedBush), "under the roof the bush catches whoever steps in");
    }

    /**
     * A campfire has no weather rule: rain drains its fuel but it stays lit,
     * so it catches whoever stands in it in the rain, and the rain on them
     * puts them out again. Contact never outlasts the rain on the head.
     */
    @Test
    void aCampfireInTheRainStillCatchesAndTheRainPutsItsVictimOut() {
        Game g = arena();
        setWeather(g, Weather.RAIN);
        campfire(g, 320, 40, 320);
        Npc n = sturdy(g.entities.spawnNpc(g.world, "In the campfire", 320.5f, FEET, 320.5f));
        fires(g, 120);
        assertTrue(g.combustion.totalIgnitions >= 2, "caught, was put out, and caught again");
        assertTrue(g.combustion.totalRainedOut >= 1, "by the rain on the head");
    }

    // ------------------------------------------------------------------
    // Several flames at once, and whose fire it is
    // ------------------------------------------------------------------

    /**
     * A person straddling a grass cell that holds the player's burning
     * liquid and burns as a wild fire of its own, next to a fueled campfire:
     * one contact, the pool's, whatever order the flames were made in.
     */
    @Test
    void overlappingFlamesBurnAsOneAndTheOrderTheyAppearedInDecidesNothing() {
        float[][] outcomes = new float[2][];
        for (int order = 0; order < 2; order++) {
            Game g = arena();
            g.world.setBlock(320, 40, 320, BlockType.TALL_GRASS, false);
            g.world.setBlock(340, 40, 340, BlockType.TALL_GRASS, false);
            if (order == 0) {
                assertTrue(g.fire.ignite(g, 340, 40, 340));
                assertTrue(g.fire.ignite(g, 320, 40, 320));
                g.liquidFire.spill(g, 320.5f, 40.5f, 320.5f, 0, 0, true);
                campfire(g, 321, 40, 320);
            } else {
                campfire(g, 321, 40, 320);
                g.liquidFire.spill(g, 320.5f, 40.5f, 320.5f, 0, 0, true);
                assertTrue(g.fire.ignite(g, 320, 40, 320));
                assertTrue(g.fire.ignite(g, 340, 40, 340));
            }
            Npc n = sturdy(g.entities.spawnNpc(g.world, "Straddler", 321.0f, FEET, 320.5f));
            fires(g, 1);
            BodyCombustion fire = n.combustion;
            assertSame(CombustionSource.LIQUID, fire.owner(), "the liquid outranks the other flames");
            assertTrue(fire.ownerByPlayer());
            assertEquals(CONTACT_DPS_NPC * DT, STURDY - n.health, 2e-4f, "one flame's damage, not three");
            outcomes[order] = new float[] {n.health, fire.ownerSourceId(), fire.exposureX(), fire.exposureY(),
                    fire.exposureZ(), g.combustion.totalIgnitions};
        }
        assertEquals(java.util.Arrays.toString(outcomes[0]), java.util.Arrays.toString(outcomes[1]),
                "the same fire whichever flame came first");
    }

    /**
     * The player's bottle lights the grass around its pool, the fire spreads
     * past the liquid, and a resident who walks into it there burns for the
     * player's account: the player's attack (once, for that bottle, however
     * the fire reached them) and the player's kill. A wild fire's burn is
     * nobody's.
     */
    @Test
    void aFireTheBottleStartedCarriesTheThrowersAttackAndKillPastItsPool() {
        Game g = arena();
        fill(g, 318, 342, 40, 40, 318, 342, BlockType.TALL_GRASS);
        Settlement village = settlement(g, -40);
        int bottle = g.liquidFire.nextSpillId();
        g.liquidFire.spill(g, 330.5f, 40.5f, 330.5f, 0, 0, true);
        Npc bather = resident(g, village, 330.5f, 330.5f);
        fires(g, 1);
        assertTrue(g.combustion.isBurning(bather));
        assertEquals(-18f, village.localReputation, 1e-4f, "the bottle is one attack on the bather");

        Vec3i spread = null;
        for (int i = 0; i < 600 && spread == null; i++) {
            fires(g, 1);
            spread = burningCellClearOfLiquid(g);
        }
        assertNotNull(spread, "precondition: the grass fire spread clear of the liquid");
        bather.pos.set(spread.x() + 0.5f, FEET, spread.z() + 0.5f);
        Npc walker = resident(g, village, spread.x() + 0.5f, spread.z() + 0.5f);
        walker.health = 2f;
        for (int i = 0; i < 20 && !walker.dead; i++) {
            fires(g, 1);
        }
        assertSame(CombustionSource.BLOCK_FIRE, walker.combustion.owner(), "the grass fire lit the walker");
        assertTrue(walker.combustion.ownerByPlayer(), "and it is the thrower's fire");
        assertEquals(bottle, walker.combustion.ownerSourceId(), "from that very bottle");
        assertTrue(walker.dead && walker.lastHitByPlayer, "so the burn death is the thrower's kill");
        assertEquals(-36f, village.localReputation, 1e-4f,
                "one attack on the walker; the bather, back in that bottle's fire, counts no second time");

        // A fire lightning started: nobody's attack, nobody's kill.
        Settlement other = settlement(g, -41);
        g.world.setBlock(300, 40, 300, BlockType.BUSH, false);
        assertTrue(g.fire.ignite(g, 300, 40, 300));
        Npc wild = resident(g, other, 300.5f, 300.5f);
        wild.health = 2f;
        for (int i = 0; i < 40 && !wild.dead; i++) {
            fires(g, 1);
        }
        assertTrue(wild.dead, "precondition: the wild fire killed");
        assertFalse(wild.lastHitByPlayer, "nobody's kill");
        assertEquals(0f, other.localReputation, 0f, "and nobody's attack");
    }

    /**
     * Four bottles broken on one resident, who burns throughout: four
     * attacks, one per bottle, however long they then stand burning in the
     * pools. A Creative thrower still pays for each, unseen.
     */
    @Test
    void everyBottleIsOneAttackOnAPersonHoweverLongTheFireLasts() {
        for (GameMode mode : GameMode.values()) {
            Game g = arena();
            if (mode == GameMode.CREATIVE) {
                assertTrue(g.switchGameMode(GameMode.CREATIVE));
            }
            Settlement village = settlement(g, -40);
            Npc leader = resident(g, village, 320.5f, 320.5f);
            float age = leader.lastKnownAge;
            for (int i = 1; i <= 4; i++) {
                throwAt(g, g.player, true, leader);
                fires(g, 4);
                assertEquals(-18f * i, village.localReputation, 1e-4f, mode + ": bottle " + i);
            }
            fires(g, 60);
            assertTrue(g.combustion.isBurning(leader) && leader.combustion.inContact(),
                    "precondition: still standing in the flames");
            assertEquals(-72f, village.localReputation, 1e-4f, mode + ": three more seconds, no more attacks");
            if (mode == GameMode.SURVIVAL) {
                assertEquals(0f, leader.lastKnownAge, "the perceivable thrower is noticed");
                assertTrue(village.alertLevel > 0);
            } else {
                assertEquals(age, leader.lastKnownAge, "Creative: nobody perceives the thrower");
                assertEquals(0f, village.alertLevel, 1e-6f);
            }
        }
    }

    // ------------------------------------------------------------------
    // People and animals keep out of the fires they live beside
    // ------------------------------------------------------------------

    /**
     * Torches and campfires now catch, so the people and animals who live
     * among them must not wander in: steered straight at one, a person or an
     * animal walks round it through the production steering and physics and
     * never feels its heat; the pathfinder routes round it; one set down in a
     * campfire walks out before it catches. The player walks where they
     * choose (the walking tests above).
     */
    @Test
    void peopleAndAnimalsWalkRoundTorchesAndCampfiresAndOutOfOneTheyAreIn() {
        Game g = arena();
        campfire(g, 320, 40, 320);
        g.world.setBlock(320, 40, 330, BlockType.TORCH, false);
        List<Entity> walkers = List.of(
                g.entities.spawnNpc(g.world, "Walker", 316.5f, FEET, 320.5f),
                g.entities.spawnCreature(g.world, Creature.CreatureType.DEER, 316.5f, FEET, 320.3f),
                g.entities.spawnNpc(g.world, "Torch walker", 316.5f, FEET, 330.5f),
                g.entities.spawnCreature(g.world, Creature.CreatureType.THORNHORN, 316.5f, FEET, 330.7f));
        for (Entity w : walkers) {
            sturdy(w);
            float z = w.pos.z;
            for (int i = 0; i < 120 && w.pos.x < 323.5f; i++) {
                fires(g, 1);
                Steering.moveToward(w, 324.5f, z, 3f);
                w.applyPhysics(DT, true);
                assertNeverTouched(w, w + " stepped into the flame at tick " + i);
                assertTrue(w.pos.y < FEET + 0.1f, w + " walks round the fire rather than hopping over it");
            }
            assertTrue(w.pos.x >= 323.5f, w + " got past the fire: " + w.pos);
        }

        List<Vec3i> path = Pathfinder.find(g.world, 316, 40, 320, 324, 40, 320,
                Pathfinder.DEFAULT_BUDGET);
        assertNotNull(path, "a way round the campfire exists");
        assertFalse(path.contains(new Vec3i(320, 40, 320)), "and the route keeps out of it");

        Npc shoved = sturdy(g.entities.spawnNpc(g.world, "Shoved", 318.5f, FEET, 320.5f));
        for (int i = 0; i < 20; i++) {
            shoved.vel.set(6f, 0f, 0f);
            fires(g, 1);
            shoved.applyPhysics(DT, true);
            assertFalse(shoved.inFlameCell(), "shoved at the campfire, a person stops at its edge: " + shoved.pos);
        }
        assertNeverTouched(shoved);

        Npc setDown = sturdy(g.entities.spawnNpc(g.world, "Set down in the fire", 320.5f, FEET, 320.5f));
        for (int i = 0; i < 40; i++) {
            fires(g, 1);
            Steering.stop(setDown);
            setDown.applyPhysics(DT, true);
        }
        assertFalse(setDown.combustion.burning(), "one set down in a campfire walks out before it catches");
        assertFalse(setDown.inFlameCell(), "and stands clear of it: " + setDown.pos);
    }

    // ------------------------------------------------------------------
    // Placing a flame, and the world it looks at
    // ------------------------------------------------------------------

    @Test
    void aFlameCannotBePlacedIntoAnyoneWhoCanBurn() {
        for (GameMode mode : GameMode.values()) {
            Game g = arena();
            if (mode == GameMode.CREATIVE) {
                assertTrue(g.switchGameMode(GameMode.CREATIVE));
            }
            g.player.pos.set(320.5f, FEET, 320.5f);
            g.entities.spawnNpc(g.world, "Bystander", 325.5f, FEET, 320.5f);
            g.player.inventory.set(0, new ItemStack(ItemType.TORCH, 8));
            g.player.inventory.set(1, new ItemStack(ItemType.CAMPFIRE, 8));

            g.player.hotbarSel = 0;
            assertEquals(mode == GameMode.CREATIVE, g.placeSelectedBlockAt(320, 40, 320),
                    mode + ": a torch at one's own feet only where one cannot burn");
            assertTrue(g.placeSelectedBlockAt(322, 40, 320), mode + ": a step away is fine");
            g.player.hotbarSel = 1;
            assertFalse(g.placeSelectedBlockAt(325, 40, 320), mode + ": never a campfire under a person");
            assertTrue(g.placeSelectedBlockAt(327, 40, 320), mode + ": and beside them is fine");
        }
    }

    @Test
    void lookingForFlamesNeverLoadsAChunk() {
        Game g = arena();
        int loaded = g.world.loadedChunks().size();
        int pending = g.world.pendingGenerationChunkCount();
        g.world.setBlock(351, 40, 330, BlockType.TORCH, false);
        g.world.setBlock(351, 40, 331, BlockType.BUSH, false);
        assertTrue(g.fire.ignite(g, 351, 40, 331));
        g.liquidFire.spill(g, 350.5f, 40.5f, 320.5f, 1, 0, true);
        Npc edge = sturdy(g.entities.spawnNpc(g.world, "At the edge", 351.8f, FEET, 330.5f));
        Creature bird = sturdy(g.entities.spawnCreature(g.world, Creature.CreatureType.BIRD, 351.9f, 46f, 331.5f));
        for (int i = 0; i < 100; i++) {
            edge.pos.x = 351.8f + (i % 2) * 1.5f;
            bird.pos.x = 351.9f + (i % 2) * 1.5f;
            fires(g, 1);
        }
        assertEquals(loaded, g.world.loadedChunks().size(), "contact sampling created no chunk");
        assertEquals(pending, g.world.pendingGenerationChunkCount());
    }

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    /** Fast ticks of body fire, with the block and liquid fires' medium tick after every tenth. */
    private void fires(Game g, int fastTicks) {
        for (int i = 0; i < fastTicks; i++) {
            g.combustion.fastTick(g, DT);
            if (++clock % FAST_PER_MEDIUM == 0) {
                g.fire.mediumTick(g, MEDIUM);
                g.liquidFire.mediumTick(g, MEDIUM);
            }
        }
    }

    /** One tick of the whole game: needs, fire, AI, physics, deaths; the medium tick after every tenth. */
    private void gameTick(Game g) {
        g.fastTick(DT);
        if (++clock % FAST_PER_MEDIUM == 0) {
            g.mediumTick(MEDIUM);
        }
    }

    /**
     * Moves the player through the production movement system, facing
     * {@code yaw} degrees (90 is +x), walking or sprinting, one fast tick of
     * movement at a time followed by that tick of the whole game, for at most
     * {@code ticks} ticks or until the player is past {@code untilX}. A yaw of
     * 0 with no key held stands still.
     */
    private void walk(Game g, float yaw, boolean sprint, int ticks, float untilX) {
        PlayerMovementSystem movement = new PlayerMovementSystem();
        PlayerMovementSystem.Command command = new PlayerMovementSystem.Command();
        PlayerMovementSystem.FrameResult result = new PlayerMovementSystem.FrameResult();
        boolean moving = yaw != 0f;
        for (int i = 0; i < ticks && g.player.pos.x < untilX; i++) {
            command.set(yaw, moving, false, false, false, sprint, false, false, false);
            movement.update(g.player, g.world, command, DT, result);
            gameTick(g);
        }
    }

    /** Throws a fire bomb flat from 1.5 blocks away at the body's middle and flies it until it breaks. */
    private static void throwAt(Game g, Entity thrower, boolean fromPlayer, Entity body) {
        float middle = body.pos.y + body.height * 0.5f + 0.05f;
        assertEquals(1, g.projectiles.fire(g, thrower, fromPlayer, body.pos.x - 1.5f, middle, body.pos.z,
                1, 0, 0, WeaponRegistry.byId("fire_bomb"), null));
        for (int i = 0; i < 500 && g.projectiles.liveCount() > 0; i++) {
            g.projectiles.update(g, FLIGHT_STEP);
        }
        assertEquals(0, g.projectiles.liveCount(), "the bottle broke");
    }

    /** One patch of the player's burning liquid on a lone pillar three blocks high: too far to run down. */
    private static Patch onePatchOnAPillar(Game g, int x, int z) {
        fill(g, x, x, 40, 42, z, z, BlockType.STONE);
        assertEquals(1, g.liquidFire.spill(g, x + 0.5f, 43.5f, z + 0.5f, 0, 0, true));
        return g.liquidFire.patches().getFirst();
    }

    /** The old medium-tick contact rule: feet in the patch's band, footprint over its cell. */
    private static boolean standsIn(Entity e, Patch p) {
        float hw = BodySweep.halfWidth(e);
        return e.pos.y >= p.y - 0.1f && e.pos.y <= p.y + 0.6f
                && e.pos.x + hw > p.x && e.pos.x - hw < p.x + 1
                && e.pos.z + hw > p.z && e.pos.z - hw < p.z + 1;
    }

    /** A burning cell at floor level with no liquid within one cell of it, or null. */
    private static Vec3i burningCellClearOfLiquid(Game g) {
        for (Vec3i c : g.fire.burningCells()) {
            boolean clear = c.y() == 40;
            for (Patch p : g.liquidFire.patches()) {
                clear &= Math.abs(p.x - c.x()) > 1 || Math.abs(p.z - c.z()) > 1;
            }
            if (clear) {
                return c;
            }
        }
        return null;
    }

    private static Npc standingIn(Game g, BlockType type, int x) {
        g.world.setBlock(x, 40, 320, type, false);
        return sturdy(g.entities.spawnNpc(g.world, type.displayName, x + 0.5f, FEET, 320.5f));
    }

    private static void campfire(Game g, int x, int y, int z) {
        g.world.setBlock(x, y, z, BlockType.CAMPFIRE, false);
        g.world.campfireFuel.put(new Vec3i(x, y, z), 300f);
    }

    /**
     * No flame has touched the body at all: none was ever applied to it (so
     * no heat, which a catch would also reset to zero) and it is not alight.
     */
    private static void assertNeverTouched(Entity e, String message) {
        assertFalse(e.combustion.hasExposure(), message + ": a flame touched it");
        assertFalse(e.combustion.burning(), message + ": it is alight");
        assertEquals(0f, e.combustion.heat(), 0f, message);
    }

    private static void assertNeverTouched(Entity e) {
        assertNeverTouched(e, "untouched");
    }

    private static float contactDps(Entity e) {
        return e instanceof Player ? CONTACT_DPS_PLAYER : e instanceof Npc ? CONTACT_DPS_NPC : CONTACT_DPS_CREATURE;
    }

    private static float afterburnDps(Entity e) {
        return e instanceof Player ? AFTERBURN_DPS_PLAYER : e instanceof Npc ? AFTERBURN_DPS_NPC : AFTERBURN_DPS_CREATURE;
    }

    private static <T extends Entity> T sturdy(T e) {
        e.maxHealth = STURDY;
        e.health = STURDY;
        return e;
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

    private static void setWeather(Game g, Weather weather) {
        g.weather.current = weather;
        g.weather.next = weather;
        g.weather.blend = 1f;
    }

    /** {@code MolotovTest}'s neutral village, far from every generated one. */
    private static Settlement settlement(Game g, int region) {
        Settlement s = new Settlement(Settlement.packId(region, region), region, region,
                SettlementType.VILLAGE, new Vec3i(330, 40, 330), HumanFaction.FRONTIER,
                Settlement.Alignment.NEUTRAL);
        g.world.settlements.put(s.id, s);
        return s;
    }

    /** A leader of that village: sturdy enough for several bottles. */
    private static Npc resident(Game g, Settlement home, float x, float z) {
        NpcArchetype archetype = NpcArchetype.LEADER;
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

    /** {@code BodyCombustionTest}'s arena with fixed seeds and no camp for anyone to walk to. */
    private static Game arena() {
        Game g = BodyCombustionTest.arena();
        g.world.campPos = null;
        g.projectiles.setRandomSeed(23L);
        g.liquidFire.setRandomSeed(29L);
        g.fire.setRandomSeed(31L);
        return g;
    }
}
