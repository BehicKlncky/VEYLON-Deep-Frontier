package com.veylon.combat;

import com.veylon.Game;
import com.veylon.entity.BodyFragment;
import com.veylon.entity.Creature;
import com.veylon.entity.Npc;
import com.veylon.item.ItemType;
import com.veylon.settlement.HumanFaction;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementType;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A scrap bomb or a powder keg kills every person whose body centre is inside
 * {@code power × LETHAL_RADIUS_FACTOR}, whatever their health and whatever
 * stands between, and blows the body apart instead of letting it fall. People
 * further out, creatures and the player keep the ordinary falloff damage, and
 * every consequence of a death still fires once, on the tick of the death.
 *
 * <p>Blasts go off at the height of a standing person's body centre, so the
 * distances below are the horizontal distances the rule measures.
 */
class BlastLethalityTest {

    /** A scrap bomb's blast profile, as {@code ProjectileSystem} detonates it. */
    private static final float SCRAP_POWER = 2.6f, SCRAP_DAMAGE = 14f;
    /** Every powder keg's power. */
    private static final float KEG_POWER = 3.8f;
    /** Pieces a humanoid is split into. */
    private static final int PIECES = 10;
    /** Where people stand: spawned a hair above the arena floor at y = 40. */
    private static final float FEET = 40.1f;
    /** Half the height of a standing {@link Npc}. */
    private static final float CENTRE = 0.875f;
    private static final float BX = 320.5f, BZ = 320.5f, BY = FEET + CENTRE;

    private Game g;

    @BeforeEach
    void setUp() {
        g = arena();
    }

    @Test
    void theLethalRadiusIsOneAndAHalfTimesTheBlastPower() {
        assertEquals(1.5f, ExplosionSystem.LETHAL_RADIUS_FACTOR);
        assertEquals(3.9f, SCRAP_POWER * ExplosionSystem.LETHAL_RADIUS_FACTOR, 1e-5f);
        assertEquals(5.7f, KEG_POWER * ExplosionSystem.LETHAL_RADIUS_FACTOR, 1e-5f);
    }

    @Test
    void everyNpcInsideTheLethalRadiusDiesRegardlessOfHealth() {
        Npc captive = person(g, NpcArchetype.CAPTIVE, BX + 1f, BZ);
        Npc guard = person(g, NpcArchetype.GUARD, BX, BZ - 2.5f);
        Npc leader = person(g, NpcArchetype.LEADER, BX - 3.8f, BZ);
        assertEquals(24f, captive.maxHealth);
        assertEquals(45f, guard.maxHealth);
        assertEquals(90f, leader.maxHealth);

        scrapBlast(g, true);
        for (Npc n : new Npc[] {captive, guard, leader}) {
            String who = n.archetype.id;
            assertTrue(n.dead && n.health <= 0, who + " dies outright, a real death not a despawn");
            assertTrue(n.lastHitByPlayer, who + ": the kill is credited to whoever threw the bomb");
            assertTrue(n.dismemberOnDeath, who + " is marked to be blown apart");
            assertEquals(BX, n.blastX, 0f, who + ": the record holds the blast that killed");
            assertEquals(BY, n.blastY, 0f, who);
            assertEquals(BZ, n.blastZ, 0f, who);
            assertEquals(SCRAP_POWER, n.blastStrength, 0f, who + ": the blast's power is the strength");
            assertEquals(BX, n.lastKnown.x, 0f, who + ": the blast is still what the person last noticed");
            assertEquals(BZ, n.lastKnown.z, 0f, who);
        }

        g.entities.fastTick(g, 0.05f);

        assertEquals(0, g.entities.npcCount(), "every one of them leaves the living on that tick");
    }

    @Test
    void coverDoesNotSaveAnNpcInsideTheLethalRadius() {
        Game twin = arena();
        Npc behindWall = person(g, NpcArchetype.LEADER, BX + 3f, BZ);
        Npc twinBehindWall = person(twin, NpcArchetype.LEADER, BX + 3f, BZ);
        wall(g);
        wall(twin);

        scrapBlast(twin, false);
        assertFalse(twinBehindWall.dead,
                "precondition: under the ordinary model this wall keeps the leader alive");
        assertTrue(twinBehindWall.health > NpcArchetype.LEADER.maxHealth - SCRAP_DAMAGE * 0.2f,
                "precondition: the wall stops almost all of the blast");

        scrapBlast(g, true);
        assertEquals(BlockType.STONE_BRICK, g.world.getBlock(321, 41, 320),
                "precondition: the cover is still standing after the blast");
        assertTrue(behindWall.dead && behindWall.dismemberOnDeath,
                "cover does not save a person inside the lethal radius");
    }

    @Test
    void npcsBeyondTheLethalRadiusTakeTheExistingFalloffDamage() {
        Game twin = arena();
        float[] distances = {4.0f, 5.0f};
        Npc[] lethal = new Npc[distances.length];
        Npc[] ordinary = new Npc[distances.length];
        for (int i = 0; i < distances.length; i++) {
            lethal[i] = person(g, NpcArchetype.CAPTIVE, BX + distances[i], BZ + i * 0.25f);
            ordinary[i] = person(twin, NpcArchetype.CAPTIVE, BX + distances[i], BZ + i * 0.25f);
        }

        scrapBlast(g, true);
        twin.explosions.explode(twin, BX, BY, BZ, SCRAP_POWER, SCRAP_DAMAGE, 0f, true);

        for (int i = 0; i < distances.length; i++) {
            Npc n = lethal[i];
            String at = distances[i] + " blocks";
            assertFalse(n.dead, "a person " + at + " out survives a scrap bomb");
            assertFalse(n.dismemberOnDeath, at + " is outside the lethal radius");
            assertEquals(ordinary[i].health, n.health, 0f,
                    at + ": exactly the damage the blast dealt before the lethal radius existed");
            assertEquals(ordinary[i].vel.x, n.vel.x, 0f, at + ": and the same knockback");
            // The pre-change formula, from the feet, in the open: exposure 1.
            assertEquals(falloffDamage(Math.sqrt(n.distSqTo(BX, BY, BZ))),
                    NpcArchetype.CAPTIVE.maxHealth - n.health, 1e-4f,
                    at + ": falloff damage by the formula");
        }
        g.entities.fastTick(g, 0.05f);
        assertEquals(distances.length, g.entities.npcCount(), "survivors stay in the world");
        assertEquals(0, g.fragments.liveCount());
    }

    @Test
    void blastKillsBecomeTenFragmentsNotARagdoll() {
        Npc[] victims = {
                person(g, NpcArchetype.GUARD, BX + 1.5f, BZ),
                person(g, NpcArchetype.BRUTE, BX - 2f, BZ + 1f),
                person(g, NpcArchetype.TRADER, BX, BZ + 3.2f),
        };
        int ragdolls = g.ragdolls.liveCount();

        scrapBlast(g, true);
        g.entities.fastTick(g, 0.05f);

        assertEquals(victims.length * PIECES, g.fragments.liveCount(),
                "each person caught in the blast becomes ten pieces");
        assertEquals(ragdolls, g.ragdolls.liveCount(), "and none of them falls as a ragdoll");
        assertTrue(g.entities.corpses.isEmpty(), "nor leaves a whole corpse");
        for (BodyFragment f : g.fragments.live) {
            float away = (f.pos.x - BX) * f.vel.x + (f.pos.y - BY) * f.vel.y + (f.pos.z - BZ) * f.vel.z;
            assertTrue(away > 0, f.piece + " flies away from the blast that killed it");
        }

        for (int frame = 0; frame < 1200 && g.fragments.liveCount() > 0; frame++) {
            g.fragments.update(g, 1f / 60f);
            g.ragdolls.update(g, 1f / 60f);
        }
        assertEquals(0, g.fragments.liveCount(), "the pieces come to rest");
        assertEquals(victims.length * PIECES, g.fragments.settledCount());
        assertTrue(g.entities.corpses.isEmpty(), "settled pieces never become a corpse");
        assertEquals(ragdolls, g.ragdolls.liveCount());
    }

    @Test
    void projectileAndMeleeKillsStillRagdoll() {
        g.projectiles.setRandomSeed(20_260_919L);

        Npc bulletVictim = person(g, NpcArchetype.LEADER, 318.5f, 330.5f);
        assertEquals(HitZone.HEAD, headShot(bulletVictim, "musket", null));
        Npc arrowVictim = person(g, NpcArchetype.LEADER, 318.5f, 334.5f);
        assertEquals(HitZone.HEAD, headShot(arrowVictim, "primitive_bow", ItemType.ARROW));

        g.player.inventory.clear();
        Npc meleeVictim = person(g, NpcArchetype.VILLAGER, g.player.pos.x + 1.2f, g.player.pos.z);
        meleeVictim.health = 1f;
        assertTrue(g.performPlayerAttack(meleeVictim), "precondition: the swing lands");

        for (Npc n : new Npc[] {bulletVictim, arrowVictim, meleeVictim}) {
            assertTrue(n.dead && n.health <= 0, n.name + " is killed");
            assertFalse(n.dismemberOnDeath, n.name + ": only a blast blows a body apart");
        }
        g.entities.fastTick(g, 0.05f);

        assertEquals(3, g.ragdolls.liveCount(), "a bullet, an arrow and a blade each leave a ragdoll");
        assertEquals(0, g.fragments.liveCount(), "and no pieces");
    }

    @Test
    void aThrownScrapBombKillsThroughTheProductionPath() {
        g.projectiles.setRandomSeed(11L);
        Npc target = person(g, NpcArchetype.LEADER, 313.5f, 310.5f);
        g.projectiles.fire(g, g.player, true, 310.5f, 41.1f, 310.5f,
                1, 0, 0, WeaponRegistry.byId("scrap_bomb"), null);
        for (int i = 0; i < 200 && g.projectiles.liveCount() > 0; i++) {
            g.projectiles.update(g, 0.02f);
        }
        assertEquals(1, g.noise.countCategory("explosion"), "precondition: the bomb went off");

        assertTrue(target.dead && target.dismemberOnDeath,
                "a thrown scrap bomb at a leader's feet kills and blows apart a 90-health leader");
        assertEquals(SCRAP_POWER, target.blastStrength, 0f);
        g.entities.fastTick(g, 0.05f);
        assertEquals(PIECES, g.fragments.liveCount());
        assertEquals(0, g.ragdolls.liveCount());
    }

    @Test
    void kegDetonationsAndChainsAreLethal() {
        Vec3i kegA = new Vec3i(320, 40, 320);
        Vec3i kegB = new Vec3i(323, 40, 320);
        g.world.setBlock(kegA.x(), kegA.y(), kegA.z(), BlockType.POWDER_KEG, false);
        g.world.setBlock(kegB.x(), kegB.y(), kegB.z(), BlockType.POWDER_KEG, false);
        // Two from the lit keg; seven from it and four from the keg it sets off.
        Npc nearA = person(g, NpcArchetype.LEADER, 318.5f, 320.5f);
        Npc onlyNearB = person(g, NpcArchetype.LEADER, 327.5f, 320.5f);

        assertTrue(g.explosions.tryArmKeg(g, kegA, 0.1f, true));
        g.explosions.tickFuses(g, 0.2f);

        assertEquals(BlockType.AIR, g.world.getBlock(kegB.x(), kegB.y(), kegB.z()),
                "precondition: the first keg set off the second");
        assertTrue(nearA.dead && nearA.dismemberOnDeath, "a lit keg is lethal");
        assertEquals(kegA.x() + 0.5f, nearA.blastX, 0f,
                "the first fatal blast is the one kept, though the second also reached");
        assertEquals(KEG_POWER, nearA.blastStrength, 0f);
        assertTrue(onlyNearB.dead && onlyNearB.dismemberOnDeath,
                "a keg set off by another keg is lethal too");
        assertEquals(kegB.x() + 0.5f, onlyNearB.blastX, 0f,
                "the person out of the first keg's lethal radius was killed by the second");

        // A keg set off by a blast that is not itself lethal still is.
        Vec3i kegC = new Vec3i(300, 40, 300);
        g.world.setBlock(kegC.x(), kegC.y(), kegC.z(), BlockType.POWDER_KEG, false);
        Npc nearC = person(g, NpcArchetype.LEADER, 304.5f, 300.5f);
        g.explosions.explode(g, 299.5f, 40.5f, 300.5f, 1.6f, 4f, 0f, true);
        assertEquals(BlockType.AIR, g.world.getBlock(kegC.x(), kegC.y(), kegC.z()),
                "precondition: the small blast set the keg off");
        assertTrue(nearC.dead && nearC.dismemberOnDeath,
                "a person out of the small blast's reach dies to the keg it set off");

        g.entities.fastTick(g, 0.05f);
        assertEquals(3 * PIECES, g.fragments.liveCount());
        assertEquals(0, g.ragdolls.liveCount());
    }

    @Test
    void fireBombIsNotLethal() {
        Npc tough = person(g, NpcArchetype.LEADER, 332.5f, 330.5f);
        Npc fragile = person(g, NpcArchetype.CAPTIVE, 330.5f, 330.5f);
        fragile.health = 1f;
        ProjectileSystem.Projectile fireBomb = new ProjectileSystem.Projectile();
        fireBomb.kind = ProjectileSystem.Kind.FIRE_BOMB;
        fireBomb.x = 331.5f;
        fireBomb.y = 41f;
        fireBomb.z = 330.5f;
        fireBomb.life = 1f;
        fireBomb.fuse = 0.01f;
        fireBomb.fromPlayer = true;
        g.projectiles.live.add(fireBomb);
        g.projectiles.update(g, 0.02f);
        assertEquals(1, g.noise.countCategory("molotov"), "precondition: the fire bomb broke");
        assertEquals(0, g.noise.countCategory("explosion"), "a molotov has no blast");
        assertEquals(tough.maxHealth, tough.health, "so nobody is hurt the moment it breaks");
        assertFalse(fragile.dead);

        // Standing in the burning liquid can still finish someone.
        g.liquidFire.mediumTick(g, 0.5f);
        assertTrue(tough.health < tough.maxHealth, "the liquid burns");
        assertFalse(tough.dead, "but does not kill a person one block away");
        assertFalse(tough.dismemberOnDeath);
        assertTrue(fragile.dead, "precondition: its burn can still finish someone");
        assertFalse(fragile.dismemberOnDeath, "and that death is an ordinary one");

        g.entities.fastTick(g, 0.05f);
        assertEquals(1, g.ragdolls.liveCount());
        assertEquals(0, g.fragments.liveCount());
    }

    @Test
    void deathConsequencesFireExactlyOnce() {
        Settlement fort = settlement(g, -40, HumanFaction.HEADHUNTERS, Settlement.Alignment.HOSTILE);
        Settlement village = settlement(g, -41, HumanFaction.FREE_SETTLERS, Settlement.Alignment.NEUTRAL);
        Npc leader = resident(fort, NpcArchetype.LEADER, BX + 1.5f, BZ);
        // A defender elsewhere, so the leader's death does not clear the fort
        // and leave nobody hostile to loot.
        fort.residents.add(new Settlement.Resident("Headhunter", NpcArchetype.HUNTER));
        Npc villager = resident(village, NpcArchetype.VILLAGER, BX, BZ + 2f);
        Npc raider = person(g, NpcArchetype.SCAVENGER, BX - 2f, BZ);
        raider.raider = true;
        float trustBefore = g.faction.trust;
        int scrapBefore = g.player.inventory.count(ItemType.SCRAP);
        int ingotsBefore = g.player.inventory.count(ItemType.IRON_INGOT);
        float grudgeBefore = g.world.factionReputation.getOrDefault(HumanFaction.HEADHUNTERS, 0f);
        float moraleBefore = fort.morale;

        scrapBlast(g, true);
        assertEquals(-18f, village.localReputation, 0.001f,
                "the blast is one attack on the villager's home");
        g.entities.fastTick(g, 0.05f);

        assertEquals(3 * PIECES, g.fragments.liveCount(), "precondition: all three were blown apart");
        assertTrue(g.player.inventory.count(ItemType.SCRAP) > scrapBefore,
                "raider loot lands on the tick of the kill");
        assertTrue(g.faction.trust > trustBefore, "so does the camp's credit for the raider");
        assertTrue(g.player.inventory.count(ItemType.IRON_INGOT) > ingotsBefore,
                "the hostile leader is searched on the tick of the kill");
        assertFalse(fort.residents.getFirst().alive, "the leader's resident record is closed");
        assertNull(fort.residents.getFirst().live);
        assertEquals(Math.max(-100f, grudgeBefore - 4f),
                g.world.factionReputation.get(HumanFaction.HEADHUNTERS), 0.001f,
                "killing a headhunter costs their grudge once");
        assertEquals(moraleBefore - 45f, fort.morale, 0.001f, "the garrison wavers once");
        assertEquals(-53f, village.localReputation, 0.001f,
                "the villager's death adds one kill consequence, exactly as for a blade");
        assertEquals(1, logLines("You looted the fallen scavenger."));
        assertEquals(1, logLines("You search the fallen headhunter leader."));
        // The kill turned the village hostile, so its villager is searched too, as after a blade.
        assertEquals(1, logLines("You search the fallen villager."));
        assertEquals(1, logLines("leader has fallen!"));

        int scrap = g.player.inventory.count(ItemType.SCRAP);
        int ingots = g.player.inventory.count(ItemType.IRON_INGOT);
        float trust = g.faction.trust;
        List<String> log = g.eventLog.all();
        for (int tick = 0; tick < 1200 && (tick < 40 || g.fragments.liveCount() > 0); tick++) {
            g.entities.fastTick(g, 0.05f);
            g.fragments.update(g, 1f / 60f);
        }
        g.entities.tickWorldDetritus(g, 10f);
        assertEquals(0, g.fragments.liveCount(), "precondition: the pieces have settled");
        assertEquals(scrap, g.player.inventory.count(ItemType.SCRAP), "no second helping of loot");
        assertEquals(ingots, g.player.inventory.count(ItemType.IRON_INGOT));
        assertEquals(trust, g.faction.trust, 0f);
        assertEquals(-53f, village.localReputation, 0.001f);
        assertEquals(moraleBefore - 45f, fort.morale, 0.001f);
        assertEquals(log, g.eventLog.all(), "nothing more happens as the pieces land");
    }

    @Test
    void creaturesAndPlayerKeepTheExistingExplosionModel() {
        Game twin = arena();
        Creature[] beasts = beasts(g);
        Creature[] twinBeasts = beasts(twin);

        scrapBlast(g, true);
        scrapBlast(twin, false);

        assertEquals(twinBeasts[0].health, beasts[0].health, 0f,
                "a creature inside the lethal radius takes the ordinary damage");
        assertFalse(beasts[0].dead);
        assertEquals(twinBeasts[0].bleedTimer, beasts[0].bleedTimer, 0f);
        assertEquals(twinBeasts[0].vel.x, beasts[0].vel.x, 0f);
        assertTrue(beasts[1].dead, "precondition: that damage can still kill a creature");
        assertEquals(twin.player.health, g.player.health, 0f,
                "the player inside the lethal radius takes the ordinary damage");
        assertTrue(g.player.health < g.player.maxHealth && !g.player.dead);

        g.entities.fastTick(g, 0.05f);
        assertEquals(1, g.ragdolls.liveCount(), "a creature killed by a blast falls whole");
        assertEquals(0, g.fragments.liveCount());
    }

    // ------------------------------------------------------------------

    /** A scrap bomb's blast at {@code (BX, BY, BZ)}, thrown by the player. */
    private static void scrapBlast(Game game, boolean lethalToHumans) {
        game.explosions.explode(game, BX, BY, BZ, SCRAP_POWER, SCRAP_DAMAGE, 0f, true,
                lethalToHumans);
    }

    /** The damage the blast dealt before the lethal radius, at feet distance {@code d}, in the open. */
    private static float falloffDamage(double d) {
        float entityRange = SCRAP_POWER * 2.4f;
        float falloff = (float) (1.0 - d / entityRange);
        return SCRAP_DAMAGE * falloff * (0.15f + 0.85f * 1f);
    }

    /**
     * A tough thornhorn and a one-health hare inside the lethal radius, and
     * the player two blocks from the blast.
     */
    private static Creature[] beasts(Game game) {
        Creature tough = game.entities.spawnCreature(game.world, Creature.CreatureType.THORNHORN,
                BX + 1.5f, FEET, BZ);
        tough.maxHealth = 200f;
        tough.health = 200f;
        Creature fragile = game.entities.spawnCreature(game.world, Creature.CreatureType.HARE,
                BX - 1f, FEET, BZ);
        fragile.health = 1f;
        game.player.pos.set(BX, FEET, BZ + 2f);
        return new Creature[] {tough, fragile};
    }

    /** A two-thick stone brick wall between the blast and {@code BX + 3}. */
    private static void wall(Game game) {
        for (int x = 321; x <= 322; x++) {
            for (int y = 40; y <= 44; y++) {
                for (int z = 315; z <= 326; z++) {
                    game.world.setBlock(x, y, z, BlockType.STONE_BRICK, false);
                }
            }
        }
    }

    /** Fires one shot along +x at the head of {@code target} from three blocks; flies it out. */
    private HitZone headShot(Npc target, String weapon, ItemType ammo) {
        g.projectiles.lastNpcHitZone = null;
        g.projectiles.fire(g, g.player, true, target.pos.x - 3f, target.pos.y + 1.66f,
                target.pos.z, 1, 0, 0, WeaponRegistry.byId(weapon), ammo);
        for (int i = 0; i < 400 && g.projectiles.liveCount() > 0; i++) {
            g.projectiles.update(g, 0.01f);
        }
        return g.projectiles.lastNpcHitZone;
    }

    private int logLines(String fragment) {
        int count = 0;
        for (String line : g.eventLog.all()) {
            if (line.contains(fragment)) {
                count++;
            }
        }
        return count;
    }

    private static Npc person(Game game, NpcArchetype archetype, float x, float z) {
        Npc n = game.entities.spawnNpc(game.world, archetype.displayName, x, FEET, z);
        n.archetype = archetype;
        n.maxHealth = archetype.maxHealth;
        n.health = archetype.maxHealth;
        return n;
    }

    /** A settlement far from every generated one, keyed by region only; it owns no blocks here. */
    private static Settlement settlement(Game game, int region, String faction,
                                         Settlement.Alignment alignment) {
        Settlement s = new Settlement(Settlement.packId(region, region), region, region,
                SettlementType.VILLAGE, new Vec3i((int) BX, (int) FEET, (int) BZ), faction, alignment);
        s.factionId = faction;
        game.world.settlements.put(s.id, s);
        return s;
    }

    private Npc resident(Settlement home, NpcArchetype archetype, float x, float z) {
        Settlement.Resident record = new Settlement.Resident(archetype.displayName, archetype);
        home.residents.add(record);
        Npc n = person(g, archetype, x, z);
        n.settlementId = home.id;
        n.residentIndex = home.residents.size() - 1;
        record.live = n;
        return n;
    }

    /** {@code CombatSystemsTest}'s isolated arena: four chunks of stone up to y = 39. */
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
        return game;
    }
}
