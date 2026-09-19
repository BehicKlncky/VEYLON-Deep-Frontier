package com.veylon;

import com.veylon.combat.HitZone;
import com.veylon.combat.ProjectileLethality;
import com.veylon.combat.ProjectileSystem;
import com.veylon.combat.WeaponRegistry;
import com.veylon.entity.Affliction;
import com.veylon.entity.BodyFragment;
import com.veylon.entity.Entity;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.settlement.NpcArchetype;
import com.veylon.simulation.FireConstants;
import com.veylon.simulation.FireSystem;
import com.veylon.simulation.LiquidFireConstants;
import com.veylon.simulation.LiquidFireSystem.Patch;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules 0.8.0 adds, meeting each other: bullets and arrows that kill people
 * by where they go in, blasts that kill everyone close and blow them apart, and
 * fire bombs that spill burning liquid which rain puts out. Each rule has its
 * own suite ({@code ProjectileHitZoneTest}, {@code BlastLethalityTest},
 * {@code MolotovTest}, {@code FireWeatherTest}); what is pinned here is where
 * they overlap. Which death decides how a body falls, what a wounded body does
 * in a blast or a fire, how a bottle sets off a keg and whose kill that is,
 * how the two kinds of fire share one cap, which fires the rain reaches, and
 * that Creative changes none of it except that its player cannot be hurt.
 *
 * <p>Everything runs through production entry points in
 * {@code CombatSystemsTest}'s flat stone arena, floor top at y = 40, so people
 * stand at y = 40.1 and liquid on the floor lies in the cells at y = 40. The
 * weather is set directly and only the systems a scene needs are stepped, at
 * the frame and medium-tick rates {@code Game} drives them at, so no weather
 * roll or AI decision changes a scene mid-test.
 */
class CombatFireIntegrationTest {

    private static final float TICK = SimulationScheduler.MEDIUM_DT;
    /** Frame step for fuses; ten of them make one medium tick. */
    private static final float FRAME = 0.05f;
    private static final int FRAMES_PER_TICK = 10;
    /** Step for projectiles in flight: fine enough that nothing tunnels. */
    private static final float FLIGHT_STEP = 0.01f;
    /** Where people stand: spawned a hair above the arena floor. */
    private static final float FEET = 40.1f;
    /** Half the height of a standing {@link Npc}: where a blast is measured to. */
    private static final float CENTRE = 0.875f;
    /** Eye height above the feet the player's weapons fire from. */
    private static final float EYE = 1.6f;
    /** Aim heights above the feet, well inside each zone of the humanoid. */
    private static final float HEAD = 1.66f;
    private static final float TORSO = 1.165f;
    /** A scrap bomb's blast, as {@code ProjectileSystem} detonates it. */
    private static final float SCRAP_POWER = 2.6f, SCRAP_DAMAGE = 14f;
    /** Every powder keg's power. */
    private static final float KEG_POWER = 3.8f;
    private static final int PIECES = BodyFragment.Piece.values().length;
    /**
     * Launch height of a flat throw at a keg on the floor 3.5 blocks away: it
     * drops about 0.6 on the way, so it meets the keg's side well above the
     * floor and below the keg's top, whatever the throw spread does.
     */
    private static final float KEG_THROW_HEIGHT = 41.2f;

    private Game g;

    @BeforeEach
    void setUp() {
        g = arena(GameMode.SURVIVAL);
    }

    // ------------------------------------------------------------------
    // Projectiles and blasts
    // ------------------------------------------------------------------

    @Test
    void headShotKillsFallWholeEvenWhenABlastCatchesTheBodyBeforeTheEntityTick() {
        Npc shot = person(NpcArchetype.LEADER, 318.5f, 320.5f);
        Npc arrowed = person(NpcArchetype.LEADER, 318.5f, 323.5f);

        assertEquals(HitZone.HEAD, shoot(g.player, shot, "musket", null, HEAD));
        assertEquals(HitZone.HEAD, shoot(g.player, arrowed, "primitive_bow", ItemType.ARROW, HEAD));
        assertTrue(shot.dead && arrowed.dead, "one bullet or one arrow in the head kills a leader");

        // A scrap bomb goes off between the two before the entity tick has
        // taken the bodies. They were dead before it, so it passes them by.
        g.explosions.explode(g, 318.5f, FEET + CENTRE, 322f, SCRAP_POWER, SCRAP_DAMAGE, 0f,
                true, true);
        assertEquals(1, g.noise.countCategory("explosion"), "precondition: the blast went off");
        for (Npc n : new Npc[] {shot, arrowed}) {
            assertFalse(n.dismemberOnDeath, n.name + ": the head shot decided how this body falls");
        }

        g.entities.fastTick(g, SimulationScheduler.FAST_DT);

        assertEquals(2, g.ragdolls.liveCount(), "a bullet and an arrow in the head each leave a ragdoll");
        assertEquals(0L, g.fragments.totalSpawned, "and nothing is blown apart");
    }

    @Test
    void aBlastKillsATorsoWoundedNpcAndBlowsItApartExactlyOnce() {
        Npc wounded = person(NpcArchetype.LEADER, 313.5f, 310.5f);
        assertEquals(HitZone.TORSO, shoot(g.player, wounded, "musket", null, TORSO));
        assertEquals(HitZone.TORSO, shoot(g.player, wounded, "primitive_bow", ItemType.ARROW, TORSO));
        assertEquals(ProjectileLethality.BULLET_TORSO_WOUNDS + ProjectileLethality.ARROW_TORSO_WOUNDS,
                wounded.torsoWounds);
        assertFalse(wounded.dead, "precondition: one unit short of the lethal count");

        // A scrap bomb thrown for real: it thuds off the body and goes off at its feet.
        g.projectiles.fire(g, g.player, true, 310.5f, 41.1f, 310.5f, 1, 0, 0,
                WeaponRegistry.byId("scrap_bomb"), null);
        fly();
        assertEquals(1, g.noise.countCategory("explosion"), "precondition: the bomb went off");
        assertTrue(wounded.dead && wounded.health <= 0, "the blast kills the wounded leader");
        assertTrue(wounded.dismemberOnDeath, "a blast death blows the body apart, wounds or not");
        assertEquals(SCRAP_POWER, wounded.blastStrength, 0f);
        float firstBlastX = wounded.blastX;

        // A second blast before the entity tick finds a body that is already dead.
        g.explosions.explode(g, wounded.pos.x + 1f, FEET + CENTRE, wounded.pos.z,
                SCRAP_POWER, SCRAP_DAMAGE, 0f, true, true);
        assertEquals(firstBlastX, wounded.blastX, 0f, "the first fatal blast's record is kept");

        for (int i = 0; i < 5; i++) {
            g.entities.fastTick(g, SimulationScheduler.FAST_DT);
        }

        assertFalse(g.entities.npcs.contains(wounded));
        assertEquals((long) PIECES, g.fragments.totalSpawned, "blown apart exactly once, into ten pieces");
        assertEquals(PIECES, g.fragments.liveCount());
        assertEquals(0, g.ragdolls.liveCount(), "the torso wounds leave no ragdoll beside the pieces");
        assertTrue(g.entities.corpses.isEmpty());
    }

    @Test
    void torsoHitsFromThePlayerAndFromNpcsShareOneWoundCount() {
        Npc raider = new Npc(g.world, "Raider");
        raider.raider = true;
        float arrow = WeaponRegistry.byId("primitive_bow").damage
                + WeaponRegistry.ammoDamageBonus(ItemType.ARROW);
        float bullet = WeaponRegistry.byId("musket").damage + WeaponRegistry.ammoDamageBonus(null);

        // A raider's arrow, the player's bullet, then another raider arrow.
        Npc first = sturdySettler(318.5f, 320.5f);
        assertFalse(first.alliedWith(raider), "precondition: the raider shoots at a different side");
        float health = first.maxHealth;
        assertEquals(HitZone.TORSO, shoot(raider, first, "primitive_bow", ItemType.ARROW, TORSO));
        health -= ProjectileLethality.torsoHitDamage(ProjectileSystem.Kind.ARROW, arrow, first.maxHealth);
        assertEquals(ProjectileLethality.ARROW_TORSO_WOUNDS, first.torsoWounds);
        assertEquals(health, first.health, 1e-3f, "an NPC's arrow in the chest costs the arrow row");
        assertFalse(first.lastHitByPlayer);

        assertEquals(HitZone.TORSO, shoot(g.player, first, "musket", null, TORSO));
        health -= ProjectileLethality.torsoHitDamage(ProjectileSystem.Kind.BULLET, bullet, first.maxHealth);
        assertEquals(ProjectileLethality.ARROW_TORSO_WOUNDS + ProjectileLethality.BULLET_TORSO_WOUNDS,
                first.torsoWounds, "the player's bullet adds to the raider's arrow");
        assertEquals(health, first.health, 1e-3f, "and costs the bullet row");
        assertFalse(first.dead, "five units: alive, whoever fired them");

        assertEquals(HitZone.TORSO, shoot(raider, first, "primitive_bow", ItemType.ARROW, TORSO));
        assertTrue(first.dead && first.health <= 0,
                "the third torso hit kills a 90-health settler, though two shooters shared them");
        assertFalse(first.lastHitByPlayer, "the raider's arrow killed, so the player gets no credit");

        // A raider's bullet, then two of the player's arrows.
        Npc second = sturdySettler(318.5f, 323.5f);
        assertEquals(HitZone.TORSO, shoot(raider, second, "musket", null, TORSO));
        assertEquals(HitZone.TORSO, shoot(g.player, second, "primitive_bow", ItemType.ARROW, TORSO));
        assertFalse(second.dead, "a bullet and an arrow are one unit short");
        assertEquals(HitZone.TORSO, shoot(g.player, second, "primitive_bow", ItemType.ARROW, TORSO));
        assertTrue(second.dead && second.health <= 0, "the second arrow kills");
        assertTrue(second.lastHitByPlayer, "and the kill is the player's");

        g.entities.fastTick(g, SimulationScheduler.FAST_DT);
        assertEquals(2, g.ragdolls.liveCount(), "a death by torso wounds leaves a ragdoll");
        assertEquals(0L, g.fragments.totalSpawned);
    }

    // ------------------------------------------------------------------
    // Fire bombs
    // ------------------------------------------------------------------

    @Test
    void aBurnDeathInLiquidFireFallsWholeEvenWithTorsoWounds() {
        Npc villager = person(NpcArchetype.VILLAGER, 333.5f, 330.5f);
        assertEquals(HitZone.TORSO, shoot(g.player, villager, "primitive_bow", ItemType.ARROW, TORSO));
        assertEquals(ProjectileLethality.ARROW_TORSO_WOUNDS, villager.torsoWounds);
        assertFalse(villager.dead, "precondition: one arrow in the chest does not kill");

        // The bottle breaks on the wounded villager and the liquid runs to their feet.
        throwBottle(330.5f, 41.5f, 330.5f, 1, 0, 0);
        assertTrue(g.liquidFire.count() > 0, "precondition: the bottle broke into burning liquid");
        assertEquals(0, g.noise.countCategory("explosion"), "a molotov has no blast");
        for (int i = 0; i < 20 && !villager.dead; i++) {
            tickFires();
            assertFalse(villager.dismemberOnDeath, "burning never marks a body to be blown apart");
        }

        assertTrue(villager.dead && villager.health <= 0, "the fire finished the villager");
        assertFalse(villager.dismemberOnDeath);
        assertEquals(ProjectileLethality.ARROW_TORSO_WOUNDS, villager.torsoWounds,
                "fire adds no wound units");
        assertTrue(villager.lastHitByPlayer, "the thrower's fire killed");
        g.entities.fastTick(g, SimulationScheduler.FAST_DT);
        assertEquals(1, g.ragdolls.liveCount(), "a burned body falls as a ragdoll");
        assertEquals(0L, g.fragments.totalSpawned);
    }

    @Test
    void aMolotovBesideAPowderKegSetsOffABlastThatBlowsPeopleApart() {
        Vec3i keg = new Vec3i(334, 40, 330);
        place(keg, BlockType.POWDER_KEG);
        Npc inThePool = person(NpcArchetype.GUARD, 332.5f, 331.5f);
        // Body centres 5.0 and 8.1 blocks from the keg's: inside its 5.7-block
        // lethal radius, and past it but inside its 9.1-block damage range.
        Npc nearby = person(NpcArchetype.GUARD, 338.5f, 333.5f);
        Npc beyond = person(NpcArchetype.GUARD, 341.5f, 326.5f);

        // The bottle breaks against the keg itself.
        throwBottle(330.5f, KEG_THROW_HEIGHT, 330.5f, 1, 0, 0);
        assertTrue(standsInFire(inThePool), "precondition: the liquid runs under the first guard");
        assertFalse(standsInFire(nearby) || standsInFire(beyond), "precondition: and no further");

        boolean armed = false;
        for (int frame = 1; frame <= 100 && block(keg) == BlockType.POWDER_KEG; frame++) {
            g.explosions.tickFuses(g, FRAME);
            if (frame % FRAMES_PER_TICK == 0) {
                tickFires();
            }
            if (!armed && g.world.kegFuses.containsKey(keg)) {
                armed = true;
                assertEquals(Boolean.TRUE, g.world.kegFusePlayerAttribution.get(keg),
                        "the fuse the thrower's liquid lit is the thrower's");
            }
            if (block(keg) == BlockType.POWDER_KEG) {
                assertFalse(inThePool.dead, "the liquid alone has not killed a guard before the blast");
            }
        }

        assertTrue(armed, "the burning liquid lit the keg's fuse");
        assertEquals(BlockType.AIR, block(keg), "and the keg went off");
        for (Npc n : new Npc[] {inThePool, nearby}) {
            assertTrue(n.dead && n.health <= 0, n.name + " inside the keg's lethal radius dies");
            assertTrue(n.dismemberOnDeath, n.name + " is blown apart, not burned");
            assertEquals(KEG_POWER, n.blastStrength, 0f, "by the keg's blast, not the bottle");
            assertTrue(n.lastHitByPlayer, "the kill is the thrower's");
        }
        assertFalse(beyond.dead, "past the lethal radius a keg is survivable");
        assertFalse(beyond.dismemberOnDeath);
        assertTrue(beyond.health < beyond.maxHealth, "with the ordinary falloff damage");

        g.entities.fastTick(g, SimulationScheduler.FAST_DT);
        assertEquals(2 * PIECES, g.fragments.liveCount(), "both are blown into ten pieces");
        assertEquals(0, g.ragdolls.liveCount(), "and neither falls as a ragdoll");
    }

    @Test
    void blockFiresFromAKegBlastAndBurningLiquidShareOneFireCap() {
        // Every fire the cap allows, burning on a plank floor well away.
        int admitted = 0;
        for (int x = 290; x < 312; x++) {
            for (int z = 290; z < 300; z++) {
                g.world.setBlock(x, 40, z, BlockType.PLANK, false);
                admitted += g.fire.ignite(g, x, 40, z) ? 1 : 0;
            }
        }
        assertEquals(FireSystem.MAX_ACTIVE_FIRES, admitted, "precondition: every fire slot is taken");
        // A bottle dropped between two plank walls, beside a keg with more
        // planks behind it: fuel for the blast to break and set alight, and
        // walls for the liquid to touch, far enough from the keg to survive it.
        Vec3i keg = new Vec3i(336, 40, 330);
        place(keg, BlockType.POWDER_KEG);
        fill(337, 338, 40, 40, 329, 331, BlockType.PLANK);
        fill(329, 333, 40, 41, 327, 327, BlockType.PLANK);
        fill(329, 333, 40, 41, 333, 333, BlockType.PLANK);
        int planksBefore = count(329, 338, 40, 41, 327, 333, BlockType.PLANK);

        throwBottle(333.5f, 42f, 330.5f, 0, -1, 0);
        assertTrue(g.liquidFire.count() > 0, "precondition: the bottle broke on the floor");

        int atBlast = -1;
        boolean penCaught = false;
        for (int frame = 1; frame <= 30 * FRAMES_PER_TICK; frame++) {
            boolean kegWasThere = block(keg) == BlockType.POWDER_KEG;
            g.explosions.tickFuses(g, FRAME);
            if (kegWasThere && block(keg) != BlockType.POWDER_KEG) {
                atBlast = g.fire.count();
            }
            if (frame % FRAMES_PER_TICK == 0) {
                tickFires();
            }
            assertTrue(g.fire.count() <= FireSystem.MAX_ACTIVE_FIRES,
                    "block fires over the cap at frame " + frame + ": " + g.fire.count());
            assertTrue(g.liquidFire.count() <= LiquidFireConstants.MAX_PATCHES);
            for (Vec3i c : g.fire.burningCells()) {
                penCaught |= c.x() >= 329 && c.x() <= 333 && (c.z() == 327 || c.z() == 333);
            }
        }

        assertEquals(FireSystem.MAX_ACTIVE_FIRES, atBlast,
                "the keg went off while every slot was taken, and its fires took none extra");
        assertTrue(count(329, 338, 40, 41, 327, 333, BlockType.PLANK) < planksBefore,
                "precondition: the blast broke planks it could have set alight");
        assertTrue(penCaught, "once the far floor burned out, the liquid lit the walls in its place");
    }

    @Test
    void rainPutsOutExposedFiresAndPoolsButATrunkUnderItsCrownBurnsOn() {
        // A tree in a grass meadow: five logs under a 5x5 crown two deep. The
        // ground under the crown is bare, so the only fuel that meets both
        // shelter and open sky is the grass at the crown's edge, beside the
        // pool; a sheltered block fire spreading into the open would only be
        // the brief flare FireWeatherTest allows.
        fill(324, 336, 40, 40, 324, 336, BlockType.TALL_GRASS);
        fill(328, 332, 40, 40, 328, 332, BlockType.AIR);
        fill(330, 330, 40, 44, 330, 330, BlockType.LOG);
        fill(328, 332, 45, 46, 328, 332, BlockType.LEAVES);
        Vec3i trunk = new Vec3i(330, 40, 330);
        Vec3i openLog = new Vec3i(318, 40, 318);
        place(openLog, BlockType.LOG);

        // Dry weather: a bottle broken against the trunk sets it alight.
        throwBottle(325.5f, 42f, 330.5f, 1, 0, 0);
        assertTrue(g.liquidFire.count() > 0, "precondition: the bottle broke against the trunk");
        for (float t = 0; !burning(trunk) && t < 5f; t += TICK) {
            tickFires();
        }
        assertTrue(burning(trunk), "precondition: the burning liquid lit the trunk");
        assertTrue(g.fire.ignite(g, openLog.x(), openLog.y(), openLog.z()),
                "precondition: a log out in the open burns too");

        setWeather(g, Weather.RAIN);
        assertFalse(rainedOn(trunk), "the crown keeps the rain off the trunk");
        assertTrue(rainedOn(openLog));
        Map<Vec3i, BlockType> exposedFires = new HashMap<>();
        List<Vec3i> shelteredFires = new ArrayList<>();
        for (Vec3i c : g.fire.burningCells()) {
            if (rainedOn(c)) {
                exposedFires.put(c, block(c));
            } else {
                shelteredFires.add(c);
            }
        }
        List<Patch> exposedPools = new ArrayList<>();
        Map<Patch, Float> shelteredPools = new HashMap<>();
        for (Patch p : g.liquidFire.patches()) {
            if (g.fire.isRainedOn(g, p.x, p.y, p.z)) {
                exposedPools.add(p);
            } else {
                shelteredPools.put(p, p.burnLeft());
            }
        }
        assertFalse(exposedPools.isEmpty() || shelteredPools.isEmpty(),
                "precondition: the pool runs out from under the crown into the open");

        // Read at each tick boundary, which is what a frame can show: a fire
        // put out and lit again inside one medium tick never goes out at all.
        float window = FireConstants.RAIN_EXTINGUISH_SECONDS + TICK;
        Set<Vec3i> putOut = new HashSet<>();
        for (float t = 0; t < window; t += TICK) {
            tickFires();
            for (Vec3i c : exposedFires.keySet()) {
                if (!burning(c)) {
                    putOut.add(c);
                }
            }
        }

        exposedFires.forEach((c, type) -> {
            assertTrue(putOut.contains(c), "rain puts out the fire at " + c + " within " + window + " s");
            assertEquals(type, block(c), "and leaves its block unburnt: " + c);
        });
        assertTrue(g.fire.totalExtinguished >= exposedFires.size());
        for (Patch p : exposedPools) {
            assertFalse(g.liquidFire.patches().contains(p),
                    "rain puts out the liquid in the open at " + p.x + "," + p.z);
        }
        shelteredPools.forEach((p, burnLeft) -> {
            if (burnLeft > window) {
                assertTrue(g.liquidFire.patches().contains(p),
                        "the liquid under the crown burns on at " + p.x + "," + p.z);
            }
        });
        for (Vec3i c : shelteredFires) {
            assertTrue(burning(c) || block(c) == BlockType.ASH || block(c) == BlockType.AIR,
                    "a sheltered fire only ever burns down, never goes out: " + c);
        }
        assertTrue(burning(trunk), "the trunk under its crown burns on in the rain");

        for (float t = 0; burning(trunk)
                && t < FireConstants.BURN_SECONDS_MIN + FireConstants.BURN_SECONDS_RANGE; t += TICK) {
            tickFires();
        }
        assertEquals(BlockType.ASH, block(trunk), "until it has burned to ash");
    }

    // ------------------------------------------------------------------
    // Creative parity
    // ------------------------------------------------------------------

    @Test
    void creativeKillsBreakBodiesAsSurvivalKillsDoAndItsPlayerNeverBurns() {
        Map<GameMode, List<Object>> outcomes = new EnumMap<>(GameMode.class);
        for (GameMode mode : GameMode.values()) {
            g = arena(mode);
            assertEquals(mode == GameMode.CREATIVE, g.player.abilities.invulnerable(),
                    mode + ": precondition");

            // A musket ball through a leader's head, off the player's own trigger.
            Npc shot = person(NpcArchetype.LEADER, 316.5f, 320.5f);
            stand(313.5f, 320.5f);
            hold(0, ItemType.MUSKET).charge = 1;
            assertEquals(Game.FirearmCommandResult.FIRED,
                    g.updateFirearmCommand(FRAME, true, true, false, aimAt(shot, HEAD)));
            fly();
            assertEquals(HitZone.HEAD, g.projectiles.lastNpcHitZone, mode + ": precondition: a head shot");

            // A scrap bomb from the player's own arm, at a guard seven blocks out.
            Npc bombed = person(NpcArchetype.GUARD, 320.5f, 340.5f);
            stand(313.5f, 340.5f);
            hold(1, ItemType.SCRAP_BOMB);
            assertTrue(g.updateThrownWeaponCommand(2f, true, new Vector3f(1, 0, 0)));
            fly();
            // Not read off the explosion's noise: nobody hears a Creative
            // player's noise, so that record differs between the modes.
            assertTrue(bombed.dead, mode + ": precondition: the bomb went off beside the guard");

            // A fire bomb broken at the player's own feet, beside a hurt villager.
            stand(310.5f, 330.5f);
            Npc burned = person(NpcArchetype.VILLAGER, 311.5f, 330.5f);
            burned.health = 12f;
            hold(2, ItemType.FIRE_BOMB);
            float before = g.player.health;
            assertTrue(g.updateThrownWeaponCommand(2f, true, new Vector3f(0, -1, 0)));
            fly();
            assertTrue(standsInFire(g.player), mode + ": precondition: the player stands in the pool");
            int ticks = 6;
            for (int i = 0; i < ticks; i++) {
                g.liquidFire.mediumTick(g, TICK);
            }
            if (mode == GameMode.SURVIVAL) {
                assertEquals(before - ticks * LiquidFireConstants.ENTITY_DPS_PLAYER * TICK,
                        g.player.health, 1e-3f, "Survival: the player's own pool burns them");
            } else {
                assertEquals(before, g.player.health, "Creative: the liquid cannot hurt the player");
                assertEquals(0f, g.player.damageFlash);
                assertFalse(g.player.has(Affliction.BURN), "nor burn them");
            }

            g.entities.fastTick(g, SimulationScheduler.FAST_DT);
            List<Object> outcome = List.of(
                    shot.dead, shot.dismemberOnDeath, shot.lastHitByPlayer,
                    bombed.dead, bombed.dismemberOnDeath, bombed.lastHitByPlayer,
                    burned.dead, burned.dismemberOnDeath, burned.lastHitByPlayer,
                    g.ragdolls.liveCount(), g.fragments.liveCount());
            assertEquals(List.of(true, false, true, true, true, true, true, false, true, 2, PIECES),
                    outcome, mode + ": a head shot and a burn leave ragdolls, the bomb ten pieces,"
                            + " and every kill is the player's");
            outcomes.put(mode, outcome);
        }
        assertEquals(outcomes.get(GameMode.SURVIVAL), outcomes.get(GameMode.CREATIVE),
                "Creative kills leave exactly what Survival kills leave");
    }

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    /**
     * Fires one shot horizontally along +x at {@code height} above the
     * target's feet from three blocks away and flies it out. No entity tick
     * runs, so the target stands where it was for the next shot.
     *
     * @return the zone the hit was classified as, or null if no NPC was hit
     */
    private HitZone shoot(Entity owner, Npc target, String weapon, ItemType ammo, float height) {
        g.projectiles.lastNpcHitZone = null;
        float ox = target.pos.x - 3f;
        if (owner != g.player) {
            owner.pos.set(ox, target.pos.y, target.pos.z);
        }
        g.projectiles.fire(g, owner, owner == g.player, ox, target.pos.y + height, target.pos.z,
                1, 0, 0, WeaponRegistry.byId(weapon), ammo);
        fly();
        return g.projectiles.lastNpcHitZone;
    }

    /** Throws a fire bomb as the player from {@code (ox, oy, oz)} and flies it until it breaks. */
    private void throwBottle(float ox, float oy, float oz, float dx, float dy, float dz) {
        assertEquals(1, g.projectiles.fire(g, g.player, true, ox, oy, oz, dx, dy, dz,
                WeaponRegistry.byId("fire_bomb"), null));
        fly();
    }

    /** Advances projectiles until nothing is in flight; a bomb's 2.4 s fuse fits well inside. */
    private void fly() {
        for (int i = 0; i < 500 && g.projectiles.liveCount() > 0; i++) {
            g.projectiles.update(g, FLIGHT_STEP);
        }
        assertEquals(0, g.projectiles.liveCount(), "everything thrown or fired has landed");
    }

    /** Block fire first, then the liquid, as {@code Game.mediumTick} orders them. */
    private void tickFires() {
        g.fire.mediumTick(g, TICK);
        g.liquidFire.mediumTick(g, TICK);
    }

    /** Whether a patch of burning liquid lies under {@code e}'s footprint. */
    private boolean standsInFire(Entity e) {
        float hw = e.width * 0.5f;
        for (Patch p : g.liquidFire.patches()) {
            if (e.pos.y >= p.y - LiquidFireConstants.CONTACT_BELOW
                    && e.pos.y <= p.y + LiquidFireConstants.CONTACT_HALF_HEIGHT
                    && e.pos.x + hw > p.x && e.pos.x - hw < p.x + 1
                    && e.pos.z + hw > p.z && e.pos.z - hw < p.z + 1) {
                return true;
            }
        }
        return false;
    }

    /** Puts the player at {@code (x, z)} with the weapon eye where the camera is. */
    private void stand(float x, float z) {
        g.player.pos.set(x, FEET, z);
        g.player.vel.zero();
        g.camera.position.set(x, FEET + EYE, z);
    }

    /** Selects hotbar slot {@code slot} holding one {@code type}; returns the stack. */
    private ItemStack hold(int slot, ItemType type) {
        ItemStack stack = new ItemStack(type, 1);
        g.player.inventory.set(slot, stack);
        g.player.hotbarSel = slot;
        return stack;
    }

    private Vector3f aimAt(Npc target, float height) {
        Vector3f eye = g.camera.position;
        return new Vector3f(target.pos.x - eye.x, target.pos.y + height - eye.y,
                target.pos.z - eye.z).normalize();
    }

    /**
     * An independent settler with a leader's 90 health, so that neither a
     * musket ball's own 34 damage nor any single torso hit kills by damage
     * alone. Not a leader: a leader is a hostile archetype and on a raider's
     * side, and a raider's shots pass through their own.
     */
    private Npc sturdySettler(float x, float z) {
        Npc n = g.entities.spawnNpc(g.world, "Settler", x, FEET, z);
        n.maxHealth = NpcArchetype.LEADER.maxHealth;
        n.health = n.maxHealth;
        return n;
    }

    private Npc person(NpcArchetype archetype, float x, float z) {
        Npc n = g.entities.spawnNpc(g.world, archetype.displayName, x, FEET, z);
        n.archetype = archetype;
        n.maxHealth = archetype.maxHealth;
        n.health = archetype.maxHealth;
        return n;
    }

    private boolean burning(Vec3i c) {
        return g.fire.burningCells().contains(c);
    }

    private boolean rainedOn(Vec3i c) {
        return g.fire.isRainedOn(g, c.x(), c.y(), c.z());
    }

    private BlockType block(Vec3i c) {
        return g.world.getBlock(c.x(), c.y(), c.z());
    }

    private void place(Vec3i c, BlockType type) {
        g.world.setBlock(c.x(), c.y(), c.z(), type, false);
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

    private int count(int x0, int x1, int y0, int y1, int z0, int z1, BlockType type) {
        int n = 0;
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    n += g.world.getBlock(x, y, z) == type ? 1 : 0;
                }
            }
        }
        return n;
    }

    private static void setWeather(Game game, Weather weather) {
        game.weather.current = weather;
        game.weather.next = weather;
        game.weather.blend = 1f;
    }

    /**
     * {@code CombatSystemsTest}'s isolated arena in the given mode: four chunks
     * of stone up to y = 39, dry, nobody about, and fixed seeds.
     */
    private static Game arena(GameMode mode) {
        Game game = new Game();
        game.newWorld(777L, true, mode);
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
        game.player.pos.set(310, FEET, 310);
        game.player.inventory.clear();
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        game.noise.reset();
        game.projectiles.setRandomSeed(20_260_919L);
        game.fire.setRandomSeed(31L);
        game.liquidFire.setRandomSeed(29L);
        setWeather(game, Weather.CLEAR);
        return game;
    }
}
