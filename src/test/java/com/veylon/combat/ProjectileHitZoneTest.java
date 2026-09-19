package com.veylon.combat;

import com.veylon.Game;
import com.veylon.entity.Creature;
import com.veylon.entity.Entity;
import com.veylon.entity.Npc;
import com.veylon.item.ItemType;
import com.veylon.settlement.NpcArchetype;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bullets and arrows kill people by where they go in: a head hit kills
 * outright, torso hits add wound units until two bullets or three arrows have
 * killed, and legs take the plain projectile damage. The player, creatures and
 * bombs keep the old model.
 *
 * <p>Every shot here is fired horizontally from a few blocks away, close
 * enough that spread and drop cannot carry it out of the zone it was aimed at.
 * The zone each hit was classified as is read back through
 * {@link ProjectileSystem#lastNpcHitZone} rather than assumed.
 */
class ProjectileHitZoneTest {

    /** Aim heights above the feet, well inside each zone of the humanoid. */
    private static final float HEAD = 1.66f;
    private static final float TORSO = 1.165f;
    private static final float LEGS = 0.45f;
    /** Horizontal distance from muzzle to target centre for ordinary shots. */
    private static final float RANGE = 3f;
    /** The blunderbuss spreads nine degrees; this keeps every pellet in the chest. */
    private static final float BLUNDERBUSS_RANGE = 1.5f;
    /** Row of the arena the targets stand in, well clear of the player at z=310. */
    private static final float LANE_Z = 320.5f;

    private Game g;

    @BeforeEach
    void setUp() {
        g = arena();
    }

    @Test
    void bulletHeadShotKillsAFullHealthLeaderInOneHit() {
        Npc leader = person(g, "Leader", NpcArchetype.LEADER.maxHealth);

        assertEquals(HitZone.HEAD, shoot(g, leader, "musket", null, HEAD));

        assertTrue(leader.dead, "one bullet in the head kills a 90-health leader");
        assertTrue(leader.health <= 0, "the kill is a real death, not a despawn");
        assertTrue(leader.lastHitByPlayer, "the kill is credited to the player");
    }

    @Test
    void arrowHeadShotKillsAFullHealthLeaderInOneHit() {
        Npc leader = person(g, "Leader", NpcArchetype.LEADER.maxHealth);

        assertEquals(HitZone.HEAD, shoot(g, leader, "primitive_bow", ItemType.ARROW, HEAD));

        assertTrue(leader.dead, "one plain arrow in the head kills a 90-health leader");
        assertTrue(leader.health <= 0);
        assertTrue(leader.lastHitByPlayer);
    }

    @Test
    void twoBulletTorsoHitsKillRegardlessOfMaxHealth() {
        // The carbine, because a musket ball's own 34 damage would already
        // kill a 24-health captive outright and prove nothing about wounds.
        float carbine = WeaponRegistry.byId("relic_carbine").damage;
        for (NpcArchetype archetype : NpcArchetype.values()) {
            Npc npc = person(g, archetype.displayName, archetype.maxHealth);
            npc.archetype = archetype;

            assertEquals(HitZone.TORSO, shoot(g, npc, "relic_carbine", null, TORSO), archetype.id);
            assertFalse(npc.dead, archetype.id + " survives one bullet in the chest");
            assertEquals(ProjectileLethality.BULLET_TORSO_WOUNDS, npc.torsoWounds, archetype.id);
            assertEquals(archetype.maxHealth - ProjectileLethality.torsoHitDamage(
                            ProjectileSystem.Kind.BULLET, carbine, archetype.maxHealth),
                    npc.health, 1e-3f, archetype.id + " loses half its health, at least");

            assertEquals(HitZone.TORSO, shoot(g, npc, "relic_carbine", null, TORSO), archetype.id);
            assertTrue(npc.dead && npc.health <= 0,
                    archetype.id + " dies to the second bullet in the chest");
        }
    }

    @Test
    void threeArrowTorsoHitsKillRegardlessOfMaxHealth() {
        for (NpcArchetype archetype : NpcArchetype.values()) {
            Npc npc = person(g, archetype.displayName, archetype.maxHealth);
            npc.archetype = archetype;

            for (int arrow = 1; arrow <= 2; arrow++) {
                assertEquals(HitZone.TORSO,
                        shoot(g, npc, "primitive_bow", ItemType.ARROW, TORSO), archetype.id);
                assertFalse(npc.dead, archetype.id + " survives arrow " + arrow + " in the chest");
            }
            assertEquals(2 * ProjectileLethality.ARROW_TORSO_WOUNDS, npc.torsoWounds, archetype.id);

            assertEquals(HitZone.TORSO,
                    shoot(g, npc, "primitive_bow", ItemType.ARROW, TORSO), archetype.id);
            assertTrue(npc.dead && npc.health <= 0,
                    archetype.id + " dies to the third arrow in the chest");
        }
    }

    @Test
    void aTorsoArrowHurtsLessThanATorsoBullet() {
        Game twin = arena();
        Npc shotByArrow = person(g, "Leader", NpcArchetype.LEADER.maxHealth);
        Npc shotByBullet = person(twin, "Leader", NpcArchetype.LEADER.maxHealth);

        assertEquals(HitZone.TORSO,
                shoot(g, shotByArrow, "primitive_bow", ItemType.ARROW, TORSO));
        assertEquals(HitZone.TORSO, shoot(twin, shotByBullet, "musket", null, TORSO));

        float arrowLoss = NpcArchetype.LEADER.maxHealth - shotByArrow.health;
        float bulletLoss = NpcArchetype.LEADER.maxHealth - shotByBullet.health;
        assertTrue(arrowLoss > 0 && arrowLoss < bulletLoss,
                "an arrow in the chest costs less than a bullet: " + arrowLoss
                        + " vs " + bulletLoss);
        assertTrue(shotByArrow.torsoWounds < shotByBullet.torsoWounds,
                "and brings death less close");
    }

    @Test
    void aPlainArrowInTheChestCostsLessThanAnyBulletForEveryArchetype() {
        WeaponDefinition bow = WeaponRegistry.byId("primitive_bow");
        float arrow = bow.damage + WeaponRegistry.ammoDamageBonus(ItemType.ARROW);
        for (NpcArchetype archetype : NpcArchetype.values()) {
            float arrowHit = ProjectileLethality.torsoHitDamage(
                    ProjectileSystem.Kind.ARROW, arrow, archetype.maxHealth);
            for (WeaponDefinition gun : WeaponRegistry.all()) {
                if (gun.category != WeaponDefinition.Category.FIREARM) {
                    continue;
                }
                float bulletHit = ProjectileLethality.torsoHitDamage(
                        ProjectileSystem.Kind.BULLET, gun.damage, archetype.maxHealth);
                assertTrue(arrowHit < bulletHit, archetype.id + ": arrow " + arrowHit
                        + " must cost less than a " + gun.id + " bullet " + bulletHit);
            }
        }
    }

    @Test
    void theArrowRowIsStrictlyLighterThanTheBulletRow() {
        assertTrue(ProjectileLethality.ARROW_TORSO_WOUNDS < ProjectileLethality.BULLET_TORSO_WOUNDS);
        assertTrue(ProjectileLethality.torsoWounds(ProjectileSystem.Kind.ARROW)
                < ProjectileLethality.torsoWounds(ProjectileSystem.Kind.BULLET));
        assertTrue(ProjectileLethality.torsoHealthFraction(ProjectileSystem.Kind.ARROW)
                < ProjectileLethality.torsoHealthFraction(ProjectileSystem.Kind.BULLET));

        int lethal = ProjectileLethality.LETHAL_TORSO_WOUNDS;
        int bullet = ProjectileLethality.BULLET_TORSO_WOUNDS;
        int arrow = ProjectileLethality.ARROW_TORSO_WOUNDS;
        assertTrue(bullet < lethal && 2 * bullet >= lethal, "exactly two bullets kill");
        assertTrue(2 * arrow < lethal && 3 * arrow >= lethal, "exactly three arrows kill");
    }

    @Test
    void mixedTorsoHitsAddUp() {
        Npc npc = person(g, "Leader", NpcArchetype.LEADER.maxHealth);

        assertEquals(HitZone.TORSO, shoot(g, npc, "musket", null, TORSO));
        assertEquals(HitZone.TORSO, shoot(g, npc, "primitive_bow", ItemType.ARROW, TORSO));
        assertFalse(npc.dead, "a bullet and an arrow in the chest are not yet lethal");
        assertEquals(ProjectileLethality.BULLET_TORSO_WOUNDS
                + ProjectileLethality.ARROW_TORSO_WOUNDS, npc.torsoWounds);

        assertEquals(HitZone.TORSO, shoot(g, npc, "primitive_bow", ItemType.ARROW, TORSO));
        assertTrue(npc.dead && npc.health <= 0, "a bullet and two arrows in the chest kill");
    }

    @Test
    void healingBetweenTorsoHitsDoesNotResetTheWounds() {
        Npc npc = person(g, "Leader", NpcArchetype.LEADER.maxHealth);

        assertEquals(HitZone.TORSO, shoot(g, npc, "musket", null, TORSO));
        assertFalse(npc.dead);
        npc.health = npc.maxHealth;

        assertEquals(HitZone.TORSO, shoot(g, npc, "musket", null, TORSO));
        assertTrue(npc.dead && npc.health <= 0,
                "full health does not undo a bullet already in the chest");
    }

    @Test
    void legHitsDealOnlyProjectileDamage() {
        Npc npc = person(g, "Leader", NpcArchetype.LEADER.maxHealth);

        assertLegHitCosts(npc, "musket", null, 34f);
        assertLegHitCosts(npc, "primitive_bow", ItemType.ARROW, 11f);
        assertLegHitCosts(npc, "primitive_bow", ItemType.IRON_ARROW, 16f);

        assertEquals(0, npc.torsoWounds, "a leg hit is not a torso wound");
        assertFalse(npc.dead);
    }

    @Test
    void blunderbussPelletsFromOneTriggerPullCountAsOneTorsoHit() {
        Npc npc = person(g, "Leader", NpcArchetype.LEADER.maxHealth);
        WeaponDefinition blunderbuss = WeaponRegistry.byId("blunderbuss");

        g.projectiles.fire(g, g.player, true, npc.pos.x - BLUNDERBUSS_RANGE,
                npc.pos.y + TORSO, npc.pos.z, 1, 0, 0, blunderbuss, null);
        assertEquals(blunderbuss.pellets, g.projectiles.liveCount(), "precondition: a full load");
        int shotId = g.projectiles.live.getFirst().shotId;
        for (ProjectileSystem.Projectile pellet : g.projectiles.live) {
            assertEquals(shotId, pellet.shotId, "every pellet of one trigger pull shares a shot");
        }
        fly(g);
        g.entities.fastTick(g, 0.05f);

        assertEquals(HitZone.TORSO, g.projectiles.lastNpcHitZone);
        assertFalse(npc.dead);
        assertEquals(ProjectileLethality.BULLET_TORSO_WOUNDS, npc.torsoWounds,
                "the whole load is one bullet's worth of wounds");
        assertEquals(shotId, npc.lastTorsoShotId);
        float firstPellet = ProjectileLethality.torsoHitDamage(ProjectileSystem.Kind.BULLET,
                blunderbuss.damage, npc.maxHealth);
        float extraPellets = (npc.maxHealth - npc.health - firstPellet) / blunderbuss.damage;
        assertTrue(extraPellets >= 1f,
                "precondition: more than one pellet reached the body, got " + extraPellets);
        assertEquals(Math.round(extraPellets), extraPellets, 1e-3f,
                "every pellet after the first deals exactly its own pellet damage");

        assertEquals(HitZone.TORSO,
                shoot(g, npc, "blunderbuss", null, TORSO, BLUNDERBUSS_RANGE));
        assertTrue(npc.dead, "the second trigger pull is a second bullet in the chest");
    }

    @Test
    void classificationUsesTheEntryPointNotTheSubstepSample() {
        Npc npc = person(g, "Target", NpcArchetype.LEADER.maxHealth);
        float feet = npc.pos.y;
        float face = npc.pos.x - (npc.width / 2f + 0.1f);

        // Diving through the side of the head: the path enters at 1.525
        // above the feet, but the sample that lands inside is at 1.30.
        float entry = ProjectileSystem.entryY(npc,
                face - 0.075f, feet + 1.60f, npc.pos.z, face + 0.225f, feet + 1.30f, npc.pos.z);
        assertEquals(feet + 1.525f, entry, 1e-4f, "the side face is crossed a quarter in");
        assertEquals(HitZone.HEAD, HitZone.classify(npc, entry));
        assertEquals(HitZone.TORSO, HitZone.classify(npc, feet + 1.30f),
                "precondition: the sample alone would have said chest");

        // Dropping in through the top of the hit box.
        assertEquals(feet + npc.height + 0.1f, ProjectileSystem.entryY(npc,
                npc.pos.x, feet + 2.0f, npc.pos.z, npc.pos.x, feet + 1.40f, npc.pos.z), 1e-4f);

        // A path that starts inside the box went in where it started.
        assertEquals(feet + 1.2f, ProjectileSystem.entryY(npc,
                npc.pos.x, feet + 1.2f, npc.pos.z, npc.pos.x + 0.1f, feet + 1.0f, npc.pos.z), 1e-6f);
    }

    @Test
    void aShotStraightDownIsJudgedWhereItEnteredTheHead() {
        Npc npc = person(g, "Target", NpcArchetype.LEADER.maxHealth);
        WeaponDefinition musket = WeaponRegistry.byId("musket");
        float dt = 0.01f;
        float travel = musket.projectileSpeed * dt;
        assertEquals(2, (int) Math.ceil(travel / 0.45f), "precondition: two sub-steps per update");
        float subStep = travel / 2f;
        float top = npc.height + 0.1f;
        // Start so the first sample stops just above the hit box and the second
        // lands a sub-step lower, below the head line.
        float start = top + 0.02f + subStep;
        assertEquals(HitZone.TORSO, HitZone.classify(npc, npc.pos.y + start - 2 * subStep),
                "precondition: the first sample inside the box is in the chest");

        g.projectiles.fire(g, g.player, true, npc.pos.x, npc.pos.y + start, npc.pos.z,
                0, -1, 0, musket, null);
        g.projectiles.update(g, dt);

        assertEquals(HitZone.HEAD, g.projectiles.lastNpcHitZone,
                "the ball went in through the top of the head");
        assertTrue(npc.dead);
    }

    @Test
    void sleepingNpcHitsCountAsTorso() {
        Npc npc = person(g, "Sleeper", NpcArchetype.LEADER.maxHealth);

        npc.state = Npc.NpcState.SLEEP;
        assertEquals(HitZone.TORSO, shoot(g, npc, "musket", null, HEAD),
                "a sleeper lying down is hit in the body, whatever the height");
        assertFalse(npc.dead, "so a shot at head height is not an instant kill");
        assertEquals(ProjectileLethality.BULLET_TORSO_WOUNDS, npc.torsoWounds);

        npc.state = Npc.NpcState.SLEEP;
        assertEquals(HitZone.TORSO, shoot(g, npc, "musket", null, LEGS));
        assertTrue(npc.dead, "two bullets in a sleeper kill as two in the chest would");
    }

    @Test
    void npcFiredArrowsAndBulletsFollowTheSameRules() {
        Npc shooter = new Npc(g.world, "Raider");
        shooter.raider = true;
        Npc leader = person(g, "Leader", NpcArchetype.LEADER.maxHealth);
        assertFalse(leader.alliedWith(shooter), "precondition: the two are on different sides");

        assertEquals(HitZone.HEAD,
                shootAs(g, shooter, leader, "primitive_bow", ItemType.ARROW, HEAD, RANGE));
        assertTrue(leader.dead && leader.health <= 0, "an NPC's arrow in the head kills");
        assertFalse(leader.lastHitByPlayer, "and the player gets no credit for it");

        Npc guard = person(g, "Guard", NpcArchetype.LEADER.maxHealth);
        assertEquals(HitZone.TORSO, shootAs(g, shooter, guard, "musket", null, TORSO, RANGE));
        assertFalse(guard.dead);
        assertEquals(shooter.pos.x, guard.lastKnown.x, 1e-4f,
                "being shot still reveals roughly where the shooter stood");
        assertEquals(HitZone.TORSO, shootAs(g, shooter, guard, "musket", null, TORSO, RANGE));
        assertTrue(guard.dead && !guard.lastHitByPlayer,
                "two NPC-fired bullets in the chest kill");
    }

    @Test
    void playerAndCreatureVictimsAreUnaffected() {
        // The player: an NPC's musket ball at head height is ordinary physical damage.
        Npc shooter = new Npc(g.world, "Raider");
        shooter.raider = true;
        WeaponDefinition musket = WeaponRegistry.byId("musket");
        assertEquals(0f, g.player.armor(), "precondition: an unarmoured player");
        float playerBefore = g.player.health;
        shooter.pos.set(g.player.pos.x - RANGE, g.player.pos.y, g.player.pos.z);
        g.projectiles.fire(g, shooter, false, shooter.pos.x, g.player.pos.y + HEAD,
                g.player.pos.z, 1, 0, 0, musket, null);
        fly(g);
        assertEquals(playerBefore - musket.damage, g.player.health, 1e-3f,
                "a head-height hit on the player is the plain hurtPhysical damage");
        assertFalse(g.player.dead);
        assertNull(g.projectiles.lastNpcHitZone, "the zone rule never looked at the player");

        // A creature: plain arrow damage, and arrows still lodge in the hide.
        g.projectiles.setRandomSeed(4L);
        Creature thornhorn = g.entities.spawnCreature(g.world, Creature.CreatureType.THORNHORN,
                318.5f, 40.1f, 330.5f);
        float arrow = WeaponRegistry.byId("primitive_bow").damage;
        for (int shot = 0; shot < 3; shot++) {
            float before = thornhorn.health;
            g.projectiles.fire(g, g.player, true, thornhorn.pos.x - 2f,
                    thornhorn.pos.y + 1.5f, thornhorn.pos.z, 1, 0, 0,
                    WeaponRegistry.byId("primitive_bow"), ItemType.ARROW);
            fly(g);
            assertEquals(before - arrow, thornhorn.health, 1e-3f,
                    "arrow " + shot + " deals its plain damage to a creature");
            g.entities.fastTick(g, 0.05f);
        }
        assertFalse(thornhorn.dead, "three arrows high in a creature do not kill it outright");
        assertTrue(thornhorn.stuckArrows > 0, "arrows still lodge in a creature they hit");
        assertEquals(ItemType.ARROW, thornhorn.stuckArrowType);
        assertTrue(g.projectiles.stuck.isEmpty(), "an arrow that hit a body is not left in the ground");
        assertNull(g.projectiles.lastNpcHitZone, "the zone rule never looked at the creature");
    }

    @Test
    void unknownProjectileKindsMustChooseATableRow() {
        for (ProjectileSystem.Kind kind : new ProjectileSystem.Kind[] {
                ProjectileSystem.Kind.BOMB, ProjectileSystem.Kind.FIRE_BOMB}) {
            assertThrows(IllegalArgumentException.class,
                    () -> ProjectileLethality.torsoWounds(kind), kind.name());
            assertThrows(IllegalArgumentException.class,
                    () -> ProjectileLethality.torsoHealthFraction(kind), kind.name());
        }
    }

    @Test
    void aZoneKillProducesTheNormalDeathPipeline() {
        int bodies = g.ragdolls.liveCount();

        Npc headShot = person(g, "Head shot", NpcArchetype.LEADER.maxHealth);
        g.projectiles.fire(g, g.player, true, headShot.pos.x - RANGE, headShot.pos.y + HEAD,
                headShot.pos.z, 1, 0, 0, WeaponRegistry.byId("musket"), null);
        fly(g);
        assertEquals(HitZone.HEAD, g.projectiles.lastNpcHitZone);
        assertTrue(g.entities.npcs.contains(headShot),
                "precondition: the body is still in the world until the entity tick");
        g.entities.fastTick(g, 0.05f);
        assertFalse(g.entities.npcs.contains(headShot), "the entity tick removes the dead");
        assertEquals(bodies + 1, g.ragdolls.liveCount(), "and hands the body to the ragdolls");

        Npc chestShot = person(g, "Chest shot", NpcArchetype.LEADER.maxHealth);
        shoot(g, chestShot, "musket", null, TORSO);
        shoot(g, chestShot, "musket", null, TORSO);
        assertTrue(chestShot.dead);
        assertFalse(g.entities.npcs.contains(chestShot));
        assertEquals(bodies + 2, g.ragdolls.liveCount(),
                "a death by torso wounds leaves a body the same way");
    }

    // ------------------------------------------------------------------

    private void assertLegHitCosts(Npc npc, String weapon, ItemType ammo, float expected) {
        float before = npc.health;
        assertEquals(HitZone.LEGS, shoot(g, npc, weapon, ammo, LEGS), weapon + "/" + ammo);
        assertEquals(expected, before - npc.health, 1e-3f,
                weapon + "/" + ammo + " in the leg deals its plain damage");
    }

    /** The player shoots {@code target} at {@code height} above its feet from {@link #RANGE}. */
    private static HitZone shoot(Game game, Npc target, String weapon, ItemType ammo,
                                 float height) {
        return shoot(game, target, weapon, ammo, height, RANGE);
    }

    private static HitZone shoot(Game game, Npc target, String weapon, ItemType ammo,
                                 float height, float range) {
        return shootAs(game, game.player, target, weapon, ammo, height, range);
    }

    /**
     * Fires one shot horizontally along +x at {@code height} above the target's
     * feet, flies it to the end, runs one entity tick and returns the zone it
     * was classified as, or null when it hit no NPC.
     */
    private static HitZone shootAs(Game game, Entity owner, Npc target, String weapon,
                                   ItemType ammo, float height, float range) {
        game.projectiles.lastNpcHitZone = null;
        float ox = target.pos.x - range;
        if (owner != game.player) {
            owner.pos.set(ox, target.pos.y, target.pos.z);
        }
        game.projectiles.fire(game, owner, owner == game.player, ox, target.pos.y + height,
                target.pos.z, 1, 0, 0, WeaponRegistry.byId(weapon), ammo);
        fly(game);
        HitZone zone = game.projectiles.lastNpcHitZone;
        game.entities.fastTick(game, 0.05f);
        return zone;
    }

    /** Advances until nothing is in flight; a miss outlives the arena, not its 3.5 s life. */
    private static void fly(Game game) {
        for (int i = 0; i < 400 && game.projectiles.liveCount() > 0; i++) {
            game.projectiles.update(game, 0.01f);
        }
    }

    private static Npc person(Game game, String name, float maxHealth) {
        Npc npc = game.entities.spawnNpc(game.world, name, 318.5f, 40.1f, LANE_Z);
        npc.maxHealth = maxHealth;
        npc.health = maxHealth;
        return npc;
    }

    /** {@code CombatSystemsTest}'s isolated arena: four chunks of stone up to y=39. */
    private static Game arena() {
        Game game = new Game();
        game.newWorld(777L, true);
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
        game.player.pos.set(310, 40.1f, 310);
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        game.projectiles.setRandomSeed(20_260_919L);
        return game;
    }
}
