package com.veylon.simulation;

import com.veylon.Game;
import com.veylon.combat.ProjectileSystem;
import com.veylon.combat.WeaponRegistry;
import com.veylon.entity.Affliction;
import com.veylon.entity.Creature;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.save.SaveSystem;
import com.veylon.settlement.HumanFaction;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementType;
import com.veylon.simulation.LiquidFireSystem.Patch;
import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static com.veylon.simulation.LiquidFireConstants.ENTITY_DPS_CREATURE;
import static com.veylon.simulation.LiquidFireConstants.ENTITY_DPS_NPC;
import static com.veylon.simulation.LiquidFireConstants.ENTITY_DPS_PLAYER;
import static com.veylon.simulation.LiquidFireConstants.MAX_PATCHES;
import static com.veylon.simulation.LiquidFireConstants.MAX_PATCHES_PER_SPILL;
import static com.veylon.simulation.LiquidFireConstants.MAX_TRACKED_NPC_SPILLS;
import static com.veylon.simulation.LiquidFireConstants.RAIN_EXTINGUISH_SECONDS;
import static com.veylon.simulation.LiquidFireConstants.SPILL_RADIUS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A fire bomb is a molotov: it breaks on the first thing it hits, makes no
 * blast, and spills burning liquid over the ground that burns whoever stands
 * in it, lights what it touches and goes out on its own or in the rain.
 *
 * <p>Everything happens in {@code CombatSystemsTest}'s flat stone arena, floor
 * top at y = 39, so liquid on the floor lies in the cells at y = 40. Weather is
 * set directly and only the fire systems' medium ticks are driven, in the
 * scheduler's half-second steps, so no weather roll changes the sky mid-test.
 */
class MolotovTest {

    private static final float TICK = SimulationScheduler.MEDIUM_DT;
    private static final float FRAME = 0.02f;
    /** Where people stand: spawned a hair above the arena floor. */
    private static final float FEET = 40.1f;
    /** The fire bomb's fuse, which a bottle that hits something never reaches. */
    private static final float FUSE = 2.4f;

    private Game g;

    @BeforeEach
    void setUp() {
        g = arena();
    }

    @Test
    void aMolotovShattersOnItsFirstBlockImpact() {
        Creature bystander = g.entities.spawnCreature(g.world, Creature.CreatureType.THORNHORN,
                340.5f, FEET, 330.5f);
        float bystanderHealth = bystander.health;
        int editsBefore = g.world.changedBlocks.size();

        float flight = throwBottle(330.5f, 42.5f, 330.5f, 1, -0.6f, 0);

        assertEquals(0, g.projectiles.liveCount(), "the bottle is gone");
        assertTrue(flight < 1f, "it broke on the floor, long before its fuse: " + flight + " s");
        assertEquals(1, g.liquidFire.totalSpills);
        assertEquals(MAX_PATCHES_PER_SPILL, g.liquidFire.count(), "a full bottle on open floor");
        for (Patch p : g.liquidFire.patches()) {
            assertEquals(40, p.y, "the liquid lies on the floor");
        }
        assertEquals(0, g.noise.countCategory("explosion"), "no fireball");
        assertEquals(1, g.noise.countCategory("molotov"), "one breaking bottle");
        assertEquals(editsBefore, g.world.changedBlocks.size(), "no block was destroyed");
        assertEquals(bystanderHealth, bystander.health, "no blast damage");
        assertEquals(0, g.fire.count(), "nothing burns before the liquid's first tick");
    }

    @Test
    void aMolotovShattersOnAnEntityAndSpillsAtItsFeet() {
        Npc target = g.entities.spawnNpc(g.world, "Target", 333.5f, FEET, 330.5f);
        float before = target.health;

        // Thrown flat from 1.5 m, the bottle would reach the floor near
        // x = 336; the body in the way at x = 333.5 stops it first.
        float flight = throwBottle(330.5f, 41.5f, 330.5f, 1, 0, 0);

        assertEquals(0, g.projectiles.liveCount());
        assertTrue(flight < 0.4f, "it broke on the body: " + flight + " s");
        Patch centre = g.liquidFire.patches().getFirst();
        assertEquals(new Vec3i(333, 40, 330), new Vec3i(centre.x, centre.y, centre.z),
                "the pool is centred at the feet of the one it hit");
        assertEquals(1f, centre.intensity(), 0f);
        assertEquals(before, target.health, "the bottle itself does no impact damage");

        g.liquidFire.mediumTick(g, TICK);
        assertEquals(before - ENTITY_DPS_NPC * TICK, target.health, 1e-4f,
                "the liquid at its feet burns it");
        assertTrue(target.lastHitByPlayer, "and the burn is the thrower's");
    }

    @Test
    void theSpillStaysOnTheSurfaceFlowsDownhillAndNeverClimbs() {
        // A platform two blocks high, x 330..332, with a wall one block
        // higher along its west side and open floor to the east.
        fill(330, 332, 40, 41, 327, 333, BlockType.STONE);
        fill(329, 329, 40, 42, 327, 333, BlockType.STONE);
        int covered = g.liquidFire.spill(g, 331.5f, 42.5f, 330.5f, 1, 0, true);

        assertEquals(covered, g.liquidFire.count());
        boolean ranDown = false;
        for (Patch p : g.liquidFire.patches()) {
            String at = "(" + p.x + ", " + p.y + ", " + p.z + ")";
            assertFalse(g.world.isSolid(p.x, p.y, p.z), "never inside a block " + at);
            assertTrue(g.world.isSolid(p.x, p.y - 1, p.z), "always on solid ground " + at);
            assertTrue(p.y <= 42, "never climbs onto the wall " + at);
            assertTrue(Math.hypot(p.x - 331, p.z - 330) <= SPILL_RADIUS, "within the radius " + at);
            ranDown |= p.y == 40 && p.x >= 333;
        }
        assertTrue(ranDown, "it ran two blocks down off the platform's edge");

        // A three-block drop is too far to follow: a 3x3 pillar keeps it all.
        fill(343, 345, 40, 42, 343, 345, BlockType.STONE);
        g.liquidFire.reset();
        assertEquals(9, g.liquidFire.spill(g, 344.5f, 43.5f, 344.5f, 1, 0, true),
                "only the pillar's top");
        for (Patch p : g.liquidFire.patches()) {
            assertEquals(43, p.y, "nothing ran over the pillar's edge");
        }

        // A bottle that breaks against a wall runs down it, three blocks at most.
        g.liquidFire.reset();
        assertTrue(g.liquidFire.spill(g, 338.5f, 43.5f, 336.5f, 0, 0, true) > 0,
                "three blocks above the floor still reaches it");
        assertEquals(40, g.liquidFire.patches().getFirst().y);
        assertEquals(0, g.liquidFire.spill(g, 338.5f, 44.5f, 336.5f, 0, 0, true),
                "four blocks above the floor does not");
    }

    @Test
    void theSpillIsBoundedPerBottleAndGlobally() {
        // One bottle on open floor covers its cap, and runs further the way it flew.
        assertEquals(MAX_PATCHES_PER_SPILL, g.liquidFire.spill(g, 300.5f, 40.5f, 330.5f, 1, 0, true));
        int ahead = 0;
        int behind = 0;
        for (Patch p : g.liquidFire.patches()) {
            ahead = Math.max(ahead, p.x - 300);
            behind = Math.max(behind, 300 - p.x);
        }
        assertEquals(3, ahead, "the pool reaches the full radius ahead");
        assertTrue(behind <= 1, "but hardly any way back: " + behind);

        // Ten bottles far apart: the pool count stops at the cap, and room is
        // made from the oldest spill, oldest patches first.
        g.liquidFire.reset();
        int bottles = MAX_PATCHES / MAX_PATCHES_PER_SPILL + 3;
        for (int i = 0; i < bottles; i++) {
            g.liquidFire.spill(g, 295.5f + (i % 5) * 8, 40.5f, 330.5f + (i / 5) * 10, 0, 0, true);
            assertTrue(g.liquidFire.count() <= MAX_PATCHES, "over the cap after bottle " + i);
        }
        assertEquals(MAX_PATCHES, g.liquidFire.count(), "the cap is reachable");
        int evicted = bottles * MAX_PATCHES_PER_SPILL - MAX_PATCHES;
        for (int spill = 0; spill < bottles; spill++) {
            int left = MAX_PATCHES_PER_SPILL * (spill + 1) - evicted;
            assertEquals(Math.clamp(left, 0, MAX_PATCHES_PER_SPILL), patchesOf(spill),
                    "patches left of spill " + spill);
        }
        Patch newest = g.liquidFire.patches().getLast();
        assertEquals(bottles - 1, newest.spillId());
        assertNotNull(patchAt(295 + 4 * 8, 40, 340), "the newest bottle keeps its centre");

        // So many people in one pool that the memory of who was burned by
        // which bottle fills up: everyone still burns, the memory stays capped.
        g.liquidFire.reset();
        g.liquidFire.spill(g, 320.5f, 40.5f, 345.5f, 0, 0, false);
        Npc[] crowd = new Npc[MAX_TRACKED_NPC_SPILLS + 6];
        for (int i = 0; i < crowd.length; i++) {
            crowd[i] = g.entities.spawnNpc(g.world, "Crowd " + i, 320.5f, FEET, 345.5f);
        }
        g.liquidFire.mediumTick(g, TICK);
        assertEquals(MAX_TRACKED_NPC_SPILLS, g.liquidFire.trackedNpcSpills());
        for (Npc n : crowd) {
            assertTrue(n.health < n.maxHealth, n.name + " burns though the memory is full");
        }
        tick(LiquidFireConstants.BURN_SECONDS_MIN + LiquidFireConstants.BURN_SECONDS_RANGE
                + LiquidFireConstants.BURN_SECONDS_JITTER + TICK);
        assertEquals(0, g.liquidFire.count(), "the pool burns out on its own");
        assertEquals(0, g.liquidFire.trackedNpcSpills(), "and is forgotten with it");
    }

    @Test
    void waterQuenchesTheSpill() {
        fill(328, 334, 40, 40, 327, 333, BlockType.WATER);

        float flight = throwBottle(331.5f, 44f, 330.5f, 0, -1, 0);
        assertTrue(flight < FUSE, "the bottle broke in the pond");
        assertEquals(0, g.projectiles.liveCount());
        assertEquals(1, g.liquidFire.totalSpills);
        assertEquals(0, g.liquidFire.count(), "water quenches it");
        assertEquals(1, g.noise.countCategory("molotov"), "the bottle still breaks audibly");

        // Spilled on the bank and thrown towards the pond, it stops at the water.
        assertTrue(g.liquidFire.spill(g, 336.5f, 40.5f, 330.5f, -1, 0, true) > 0);
        for (Patch p : g.liquidFire.patches()) {
            assertTrue(p.x >= 335, "no liquid in the pond at x " + p.x);
        }

        // Water flowing over a burning patch puts it out.
        assertNotNull(patchAt(335, 40, 330));
        g.world.setBlock(335, 40, 330, BlockType.WATER, false);
        g.liquidFire.mediumTick(g, TICK);
        assertNull(patchAt(335, 40, 330), "flooded patches go out");
    }

    @Test
    void patchesBurnEntitiesOncePerTickAndRespectCreativeInvulnerability() {
        for (GameMode mode : GameMode.values()) {
            g = arena();
            if (mode == GameMode.CREATIVE) {
                assertTrue(g.switchGameMode(GameMode.CREATIVE));
            }
            // Everyone straddles four burning cells at once.
            g.player.pos.set(330f, FEET, 330f);
            Creature thornhorn = g.entities.spawnCreature(g.world, Creature.CreatureType.THORNHORN,
                    331f, FEET, 329f);
            Npc settler = g.entities.spawnNpc(g.world, "Settler", 329f, FEET, 331f);
            g.liquidFire.spill(g, 330.5f, 40.5f, 330.5f, 0, 0, false);
            float player = g.player.health;
            float creature = thornhorn.health;
            float npc = settler.health;

            g.liquidFire.mediumTick(g, TICK);

            assertEquals(creature - ENTITY_DPS_CREATURE * TICK, thornhorn.health, 1e-4f,
                    mode + ": a creature on four patches burns once");
            assertEquals(npc - ENTITY_DPS_NPC * TICK, settler.health, 1e-4f,
                    mode + ": an NPC on four patches burns once");
            assertFalse(thornhorn.lastHitByPlayer, "nobody threw this one");
            if (mode == GameMode.SURVIVAL) {
                assertEquals(player - ENTITY_DPS_PLAYER * TICK, g.player.health, 1e-4f,
                        "the player on four patches burns once");
                assertEquals(1f, g.player.damageFlash);
                for (int i = 0; i < 12 && !g.player.has(Affliction.BURN); i++) {
                    g.liquidFire.mediumTick(g, TICK);
                }
                assertTrue(g.player.has(Affliction.BURN), "standing in it inflicts burns");
            } else {
                for (int i = 0; i < 12; i++) {
                    g.liquidFire.mediumTick(g, TICK);
                }
                assertEquals(player, g.player.health, "Creative: the liquid cannot hurt the player");
                assertEquals(0f, g.player.damageFlash);
                assertFalse(g.player.has(Affliction.BURN), "nor burn them");
            }
        }
    }

    @Test
    void aSpillNextToATreeStartsABlockFireThatClimbs() {
        // A generated-style tree: a five-log trunk under a 5x5, two-deep crown.
        fill(330, 330, 40, 44, 330, 330, BlockType.LOG);
        fill(328, 332, 45, 46, 328, 332, BlockType.LEAVES);
        g.liquidFire.spill(g, 328.5f, 40.5f, 330.5f, 1, 0, true);
        assertNotNull(patchAt(329, 40, 330), "precondition: liquid against the trunk");

        float lit = -1;
        float crown = -1;
        for (float t = TICK; t <= 30f && crown < 0; t += TICK) {
            g.fire.mediumTick(g, TICK);
            g.liquidFire.mediumTick(g, TICK);
            if (lit < 0 && g.fire.count() > 0) {
                lit = t;
            }
            for (Vec3i p : g.fire.burningCells()) {
                if (g.world.getBlock(p.x(), p.y(), p.z()) == BlockType.LEAVES) {
                    crown = t;
                }
            }
        }
        assertTrue(lit > 0 && lit <= 3f, "the trunk caught within 3 s: " + lit);
        assertTrue(g.liquidFire.totalPatchIgnitions > 0, "the liquid lit it");
        assertTrue(crown > 0, "the fire climbed into the leaves within 30 s");
    }

    @Test
    void tallGrassUnderTheSpillIgnites() {
        fill(326, 334, 40, 40, 326, 334, BlockType.TALL_GRASS);
        assertEquals(MAX_PATCHES_PER_SPILL, g.liquidFire.spill(g, 330.5f, 40.5f, 330.5f, 0, 0, true),
                "grass does not stop the liquid");
        for (Patch p : g.liquidFire.patches()) {
            assertEquals(BlockType.TALL_GRASS, g.world.getBlock(p.x, p.y, p.z), "it soaks the grass");
        }

        boolean grassBurning = false;
        for (float t = 0; t < 2f && !grassBurning; t += TICK) {
            g.liquidFire.mediumTick(g, TICK);
            for (Vec3i c : g.fire.burningCells()) {
                grassBurning |= g.world.getBlock(c.x(), c.y(), c.z()) == BlockType.TALL_GRASS
                        && patchAt(c.x(), c.y(), c.z()) != null;
            }
        }
        assertTrue(grassBurning, "soaked grass catches within 2 s");
    }

    @Test
    void rainExtinguishesPatchesAndPreventsIgnition() {
        setWeather(Weather.RAIN);
        // Open to the sky, on grass, with someone standing in it.
        fill(326, 334, 40, 40, 326, 334, BlockType.TALL_GRASS);
        Npc wet = g.entities.spawnNpc(g.world, "Wet", 330.5f, FEET, 330.5f);
        float health = wet.health;
        int exposed = g.liquidFire.spill(g, 330.5f, 40.5f, 330.5f, 0, 0, true);
        // Under a stone roof with a block of headroom, on bare floor.
        fill(296, 304, 42, 42, 296, 304, BlockType.STONE);
        int sheltered = g.liquidFire.spill(g, 300.5f, 40.5f, 300.5f, 0, 0, true);
        assertEquals(MAX_PATCHES_PER_SPILL, exposed);
        assertEquals(MAX_PATCHES_PER_SPILL, sheltered);
        assertFalse(g.fire.isRainedOn(g, 300, 40, 300), "precondition: the roof shelters");

        tick(RAIN_EXTINGUISH_SECONDS + TICK);

        assertEquals(sheltered, g.liquidFire.count(), "only the sheltered pool is still burning");
        for (Patch p : g.liquidFire.patches()) {
            assertTrue(p.x <= 304 && p.z <= 304, "the one under the roof");
        }
        assertEquals(0, g.fire.count(), "the rained-on pool lit nothing");
        assertEquals(0, g.liquidFire.totalPatchIgnitions);
        assertEquals(BlockType.TALL_GRASS, g.world.getBlock(330, 40, 330), "the grass is untouched");
        assertEquals(health, wet.health, "a soaking pool burns nobody while it goes out");
    }

    @Test
    void aPoolUnderCoverDoesNotLightWetGrassBesideIt() {
        setWeather(Weather.RAIN);
        // A stone roof with a block of headroom over x, z 296..304, grass out
        // in the rain along its east side, and a plank under it.
        fill(296, 304, 42, 42, 296, 304, BlockType.STONE);
        fill(305, 308, 40, 40, 296, 304, BlockType.TALL_GRASS);
        Vec3i plank = new Vec3i(300, 40, 300);
        g.world.setBlock(plank.x(), plank.y(), plank.z(), BlockType.PLANK, false);
        // The pool runs from under the roof out onto the wet grass.
        g.liquidFire.spill(g, 303.5f, 40.5f, 300.5f, 0, 0, true);
        assertNotNull(patchAt(304, 40, 300), "precondition: burning liquid under the roof's edge");
        assertFalse(g.fire.isRainedOn(g, 304, 40, 300), "precondition: and sheltered there");
        assertTrue(g.fire.isRainedOn(g, 305, 40, 300), "precondition: the grass beside it is not");

        boolean plankCaught = false;
        for (int tick = 0; tick < 16; tick++) {
            tick(TICK);
            for (Vec3i c : g.fire.burningCells()) {
                assertFalse(g.fire.isRainedOn(g, c.x(), c.y(), c.z()),
                        "the pool lit " + g.world.getBlock(c.x(), c.y(), c.z()) + " at " + c
                                + " in the rain, tick " + tick);
                plankCaught |= c.equals(plank);
            }
        }
        assertTrue(plankCaught, "control: the pool still lights fuel under the roof");
        for (int x = 305; x <= 308; x++) {
            for (int z = 296; z <= 304; z++) {
                assertEquals(BlockType.TALL_GRASS, g.world.getBlock(x, 40, z), "the wet grass is untouched");
            }
        }
    }

    @Test
    void aPatchLightsANeighbouringKegsFuseForWhoeverThrewIt() {
        Vec3i keg = new Vec3i(333, 40, 330);
        g.world.setBlock(keg.x(), keg.y(), keg.z(), BlockType.POWDER_KEG, false);
        g.liquidFire.spill(g, 331.5f, 40.5f, 330.5f, 1, 0, true);
        assertNotNull(patchAt(332, 40, 330), "precondition: liquid against the keg");

        g.liquidFire.mediumTick(g, TICK);

        assertTrue(g.world.kegFuses.containsKey(keg), "the flame reaches the fuse on the first tick");
        assertEquals(Boolean.TRUE, g.world.kegFusePlayerAttribution.get(keg),
                "and the blast it leads to is the thrower's");
    }

    @Test
    void playerMolotovOnSettlementNpcsCountsAsOneAttackPerNpc() {
        Settlement village = settlement(-40);
        Npc a = resident(village, 332.5f, 330.5f);
        Npc b = resident(village, 332.5f, 331.5f);
        Npc drifter = g.entities.spawnNpc(g.world, "Drifter", 331.5f, FEET, 329.5f);

        throwBottle(326.5f, 41.5f, 330.5f, 1, 0, 0);
        assertTrue(g.liquidFire.count() > 0, "precondition: the bottle broke among them");
        tick(4 * TICK);
        for (Npc n : new Npc[] {a, b, drifter}) {
            assertTrue(n.health <= n.maxHealth - 4 * ENTITY_DPS_NPC * TICK + 1e-3f,
                    n.name + " stood in the fire for all four ticks");
            assertTrue(n.lastHitByPlayer);
        }
        assertEquals(-36f, village.localReputation, 1e-4f,
                "two residents burned for four ticks are two attacks, not eight");
        assertEquals(0f, a.lastKnownAge, "the perceivable thrower is noticed");

        // A second bottle on the same people is a second attack on each.
        g.liquidFire.spill(g, 332.5f, 40.5f, 330.5f, 0, 0, true);
        tick(4 * TICK);
        assertEquals(-72f, village.localReputation, 1e-4f, "one attack per NPC per bottle");

        // Nobody pays for a fire the player did not start.
        Settlement other = settlement(-41);
        Npc c = resident(other, 300.5f, 345.5f);
        g.liquidFire.spill(g, 300.5f, 40.5f, 345.5f, 0, 0, false);
        tick(4 * TICK);
        assertTrue(c.health < c.maxHealth, "precondition: it burned");
        assertFalse(c.lastHitByPlayer);
        assertEquals(0f, other.localReputation, "no reputation for an unowned fire");
    }

    @Test
    void aCreativeThrowerIsNotNoticedButStillPaysForTheAttack() {
        for (GameMode mode : GameMode.values()) {
            g = arena();
            if (mode == GameMode.CREATIVE) {
                assertTrue(g.switchGameMode(GameMode.CREATIVE));
            }
            Settlement village = settlement(-40);
            Npc resident = resident(village, 330.5f, 330.5f);
            float ageBefore = resident.lastKnownAge;
            g.liquidFire.spill(g, 330.5f, 40.5f, 330.5f, 0, 0, true);

            g.liquidFire.mediumTick(g, TICK);

            assertTrue(resident.health < resident.maxHealth, mode + ": precondition: it burned");
            assertEquals(-18f, village.localReputation, 1e-4f, mode + ": the attack costs reputation");
            if (mode == GameMode.SURVIVAL) {
                assertEquals(0f, resident.lastKnownAge, "the burning resident looks up");
                assertTrue(village.alertLevel > 0, "and the settlement is alerted");
            } else {
                assertEquals(ageBefore, resident.lastKnownAge, "Creative: nobody perceives the thrower");
                assertEquals(0f, village.alertLevel, 1e-6f, "Creative: no alert");
            }
        }
    }

    @Test
    void inFlightMolotovStillSurvivesSaveLoad(@TempDir Path dir) {
        assertEquals(1, g.projectiles.fire(g, g.player, true, 310.5f, 42f, 310.5f, 1, 0.8f, 0,
                WeaponRegistry.byId("fire_bomb"), null));
        g.projectiles.update(g, 0.1f);
        assertEquals(1, g.projectiles.liveCount(), "precondition: still in the air");
        Path save = dir.resolve("molotov.sav");
        assertTrue(SaveSystem.save(g, save));

        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save));
        // The arena was carved straight into chunk data, which a save does not
        // record; carve it again under the restored bottle.
        flatten(loaded);
        setWeather(loaded, Weather.CLEAR);
        assertEquals(1, loaded.projectiles.liveCount());
        ProjectileSystem.Projectile bottle = loaded.projectiles.live.getFirst();
        assertEquals(ProjectileSystem.Kind.FIRE_BOMB, bottle.kind);
        assertSame(loaded.player, bottle.owner);
        float fuse = bottle.fuse;

        float t = 0;
        while (loaded.projectiles.liveCount() > 0 && t < FUSE) {
            loaded.projectiles.update(loaded, FRAME);
            t += FRAME;
        }
        assertEquals(0, loaded.projectiles.liveCount());
        assertTrue(t < fuse - FRAME, "the restored bottle broke on impact, not on its fuse");
        assertTrue(loaded.liquidFire.count() > 0, "and spilled burning liquid");
        assertTrue(loaded.liquidFire.patches().getFirst().byPlayer(), "still the player's");
        assertEquals(0, loaded.noise.countCategory("explosion"));
    }

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    /** {@code CombatSystemsTest}'s isolated arena, dry, with fixed seeds. */
    private static Game arena() {
        Game game = new Game();
        game.newWorld(777L, true);
        flatten(game);
        game.player.pos.set(310, FEET, 310);
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        game.projectiles.setRandomSeed(23L);
        game.liquidFire.setRandomSeed(29L);
        game.fire.setRandomSeed(31L);
        setWeather(game, Weather.CLEAR);
        return game;
    }

    /** Four chunks of stone up to y = 39 and air above. */
    private static void flatten(Game game) {
        for (int cx = 18; cx <= 21; cx++) {
            for (int cz = 18; cz <= 21; cz++) {
                Chunk c = game.world.getOrCreateChunk(cx, cz);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int y = 0; y < Chunk.SY; y++) {
                            c.set(lx, y, lz, y <= 39 ? BlockType.STONE : BlockType.AIR);
                        }
                    }
                }
                c.recomputeAllHeights();
                c.rebuildLights();
            }
        }
    }

    /** Throws a fire bomb as the player and flies it until it is gone; returns the seconds it took. */
    private float throwBottle(float ox, float oy, float oz, float dx, float dy, float dz) {
        assertEquals(1, g.projectiles.fire(g, g.player, true, ox, oy, oz, dx, dy, dz,
                WeaponRegistry.byId("fire_bomb"), null));
        float t = 0;
        while (g.projectiles.liveCount() > 0 && t < FUSE + 1f) {
            g.projectiles.update(g, FRAME);
            t += FRAME;
        }
        return t;
    }

    /** Block fire first, then the liquid, as {@code Game.mediumTick} orders them. */
    private void tick(float seconds) {
        for (float t = 0; t < seconds; t += TICK) {
            g.fire.mediumTick(g, TICK);
            g.liquidFire.mediumTick(g, TICK);
        }
    }

    private Patch patchAt(int x, int y, int z) {
        for (Patch p : g.liquidFire.patches()) {
            if (p.x == x && p.y == y && p.z == z) {
                return p;
            }
        }
        return null;
    }

    private int patchesOf(int spillId) {
        int n = 0;
        for (Patch p : g.liquidFire.patches()) {
            if (p.spillId() == spillId) {
                n++;
            }
        }
        return n;
    }

    private void fill(int x0, int x1, int y0, int y1, int z0, int z1, BlockType type) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    g.world.setBlock(x, y, z, type, false);
                }
            }
        }
    }

    private void setWeather(Weather weather) {
        setWeather(g, weather);
    }

    private static void setWeather(Game game, Weather weather) {
        game.weather.current = weather;
        game.weather.next = weather;
        game.weather.blend = 1f;
    }

    /** A neutral village far from every generated one, keyed by region only. */
    private Settlement settlement(int region) {
        Settlement s = new Settlement(Settlement.packId(region, region), region, region,
                SettlementType.VILLAGE, new Vec3i(330, 40, 330), HumanFaction.FRONTIER,
                Settlement.Alignment.NEUTRAL);
        g.world.settlements.put(s.id, s);
        return s;
    }

    /** A leader, so four ticks in the fire twice over do not kill them. */
    private Npc resident(Settlement home, float x, float z) {
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
}
