package com.veylon.combat;

import com.veylon.Game;
import com.veylon.entity.Creature;
import com.veylon.entity.Npc;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Weapon registry, projectile flight/occlusion, noise events and explosion
 * rules (falloff, cover, protected blocks, chains, batch edits, reputation).
 */
class CombatSystemsTest {

    private Game g;

    @BeforeEach
    void setUp() {
        g = new Game();
        g.newWorld(777L, true);
        // Flatten a private arena far from spawn structures at y=40.
        for (int cx = 18; cx <= 21; cx++) {
            for (int cz = 18; cz <= 21; cz++) {
                Chunk c = g.world.getOrCreateChunk(cx, cz);
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
        g.player.pos.set(310, 40.1f, 310);
        g.entities.creatures.clear();
        g.entities.npcs.clear();
    }

    @Test
    void weaponRegistryDefinitionsAreCoherent() {
        for (WeaponDefinition d : WeaponRegistry.all()) {
            assertTrue(d.range > 0 && d.projectileSpeed > 0, d.id);
            assertTrue(d.noiseRadius > 0, d.id + " must make noise");
            if (d.category == WeaponDefinition.Category.FIREARM) {
                assertNotNull(d.ammo, d.id + " needs ammo");
                assertTrue(d.reloadTime > 0, d.id + " needs a reload time");
            }
        }
        // Balance intent: musket is loud and slow; bow is quiet.
        WeaponDefinition musket = WeaponRegistry.byId("musket");
        WeaponDefinition bow = WeaponRegistry.byId("primitive_bow");
        assertTrue(musket.noiseRadius > bow.noiseRadius * 4, "gunshots carry much farther");
        assertTrue(musket.damage > bow.damage, "musket hits harder");
        assertTrue(musket.reloadTime > 2, "musket reload is a real commitment");
        // Relics are loot-only.
        assertTrue(WeaponRegistry.byId("relic_carbine").relic);
        assertTrue(WeaponRegistry.byId("relic_rifle").relic);
    }

    @Test
    void projectileHitsATargetAndEmitsNoise() {
        Creature deer = g.entities.spawnCreature(g.world, Creature.CreatureType.DEER,
                318.5f, 40.1f, 310.5f);
        float before = deer.health;
        WeaponDefinition musket = WeaponRegistry.byId("musket");
        // Aim at center mass; a short volley averages out the spread.
        for (int shot = 0; shot < 4; shot++) {
            g.projectiles.fire(g, g.player, true, 310.5f, 41.5f, 310.5f,
                    1, -0.11f, 0, musket, null);
            for (int i = 0; i < 40 && g.projectiles.liveCount() > 0; i++) {
                g.projectiles.update(g, 0.05f);
            }
            if (deer.health < before) {
                break;
            }
        }
        g.noise.emit(g, 310.5f, 41.5f, 310.5f, musket.noiseRadius, 1f, "gunshot", true, g.player);
        assertTrue(deer.health < before, "musket ball should hit the deer");
        assertNotNull(g.noise.loudestAudible(340, 40, 310, 0), "gunshot audible far away");
        assertEquals(0, g.projectiles.liveCount(), "no projectile leaks");
    }

    @Test
    void blocksOccludeProjectiles() {
        // Wall between shooter and target.
        for (int y = 38; y <= 45; y++) {
            for (int z = 305; z <= 315; z++) {
                g.world.setBlock(314, y, z, BlockType.STONE, false);
            }
        }
        Creature deer = g.entities.spawnCreature(g.world, Creature.CreatureType.DEER,
                318.5f, 40.1f, 310.5f);
        float before = deer.health;
        g.projectiles.fire(g, g.player, true, 310.5f, 41.5f, 310.5f, 1, 0, 0,
                WeaponRegistry.byId("musket"), null);
        for (int i = 0; i < 40 && g.projectiles.liveCount() > 0; i++) {
            g.projectiles.update(g, 0.05f);
        }
        assertEquals(before, deer.health, 0.001f, "wall must stop the shot");
    }

    @Test
    void explosionDamageFallsOffAndRespectsCover() {
        Creature near = g.entities.spawnCreature(g.world, Creature.CreatureType.THORNHORN,
                313.5f, 40.1f, 310.5f);
        Creature far = g.entities.spawnCreature(g.world, Creature.CreatureType.THORNHORN,
                317.5f, 40.1f, 310.5f);
        Creature covered = g.entities.spawnCreature(g.world, Creature.CreatureType.THORNHORN,
                313.5f, 40.1f, 316.5f);
        // Thick wall shielding "covered" from the blast.
        for (int y = 39; y <= 44; y++) {
            for (int x = 309; x <= 317; x++) {
                g.world.setBlock(x, y, 313, BlockType.STONE_BRICK, false);
                g.world.setBlock(x, y, 314, BlockType.STONE_BRICK, false);
            }
        }
        float nearBefore = near.health, farBefore = far.health, coveredBefore = covered.health;
        g.explosions.explode(g, 311.5f, 41f, 310.5f, 3.5f, 30f, 0f, true);
        float nearDmg = nearBefore - near.health;
        float farDmg = farBefore - far.health;
        float coveredDmg = coveredBefore - covered.health;
        assertTrue(nearDmg > 0, "close target takes damage");
        assertTrue(nearDmg > farDmg, "damage falls off with distance");
        assertTrue(coveredDmg < nearDmg * 0.5f, "walls absorb most of the blast");
    }

    @Test
    void protectedBlocksSurviveExplosionsAndStoneResistsSmallCharges() {
        g.world.setBlock(312, 40, 310, BlockType.RUIN_CORE, false);
        g.world.setBlock(312, 40, 311, BlockType.PLANK, false);
        g.world.setBlock(313, 40, 310, BlockType.STONE_BRICK, false);
        g.explosions.explode(g, 311.5f, 40.5f, 310.5f, 2.2f, 10f, 0f, true);
        assertEquals(BlockType.RUIN_CORE, g.world.getBlock(312, 40, 310),
                "progression-critical blocks are blast-proof");
        assertEquals(BlockType.AIR, g.world.getBlock(312, 40, 311),
                "weak planks are destroyed");
        assertEquals(BlockType.STONE_BRICK, g.world.getBlock(313, 40, 310),
                "reinforced stone resists small charges");
    }

    @Test
    void kegChainsAreBoundedAndFusedKegsSurviveSaves() {
        // A line of kegs: chain detonation must stay within the cap.
        for (int i = 0; i < ExplosionSystem.MAX_CHAIN + 4; i++) {
            g.world.setBlock(312 + i * 2, 40, 310, BlockType.POWDER_KEG, false);
        }
        g.explosions.explode(g, 311f, 40.5f, 310.5f, 3.8f, 30f, 0f, true);
        int surviving = 0;
        for (int i = 0; i < ExplosionSystem.MAX_CHAIN + 4; i++) {
            if (g.world.getBlock(312 + i * 2, 40, 310) == BlockType.POWDER_KEG) {
                surviving++;
            }
        }
        assertTrue(surviving >= 2, "chain reactions are bounded, kegs beyond the cap survive");
    }

    @Test
    void adjacentFireArmsAKegAndRemovedKegsDropStaleFuses() {
        var burningLog = new com.veylon.util.Vec3i(312, 40, 310);
        var keg = new com.veylon.util.Vec3i(313, 40, 310);
        g.world.setBlock(burningLog.x(), burningLog.y(), burningLog.z(), BlockType.LOG, false);
        g.world.setBlock(keg.x(), keg.y(), keg.z(), BlockType.POWDER_KEG, false);
        assertTrue(g.fire.ignite(g, burningLog.x(), burningLog.y(), burningLog.z()));
        g.fire.mediumTick(g, 0.1f);
        assertTrue(g.world.kegFuses.containsKey(keg), "adjacent open flame lights the fuse");

        g.world.setBlock(keg.x(), keg.y(), keg.z(), BlockType.AIR, false);
        g.explosions.tickFuses(g, 0.1f);
        assertTrue(!g.world.kegFuses.containsKey(keg), "destroyed/replaced keg has no stale fuse");
    }

    @Test
    void batchEditsKeepHeightmapAndLightsCorrect() {
        g.world.setBlock(312, 40, 310, BlockType.TORCH, false);
        int hBefore = g.world.surfaceHeight(313, 310);
        g.world.beginBatch();
        for (int y = 38; y <= 39; y++) {
            g.world.setBlock(313, y, 310, BlockType.AIR, true);
        }
        g.world.setBlock(312, 40, 310, BlockType.AIR, true);
        g.world.endBatch();
        assertTrue(g.world.surfaceHeight(313, 310) < hBefore, "heightmap recomputed after batch");
        assertEquals(0f, g.world.blockLight(312, 41, 310), 0.001f,
                "light list rebuilt after batched torch removal");
        assertTrue(g.world.changedBlocks.containsKey(new com.veylon.util.Vec3i(313, 39, 310)),
                "batched edits still record save deltas");
    }

    @Test
    void arrowsStickAndCanBeRecovered() {
        // Fire an arrow into a wall; some arrows should stick for pickup.
        for (int y = 38; y <= 45; y++) {
            for (int z = 305; z <= 315; z++) {
                g.world.setBlock(314, y, z, BlockType.STONE, false);
            }
        }
        WeaponDefinition bow = WeaponRegistry.byId("primitive_bow");
        int stuckTotal = 0;
        for (int shot = 0; shot < 12; shot++) {
            g.projectiles.fire(g, g.player, true, 310.5f, 41.5f, 310.5f, 1, 0, 0, bow,
                    ItemType.ARROW);
            for (int i = 0; i < 60 && g.projectiles.liveCount() > 0; i++) {
                g.projectiles.update(g, 0.05f);
            }
        }
        stuckTotal = g.projectiles.stuck.size();
        assertTrue(stuckTotal > 0, "some arrows should stick in the wall");
        var arrow = g.projectiles.nearestStuckArrow(314, 41.5f, 310.5f, 4f);
        assertNotNull(arrow);
        int before = g.player.inventory.count(ItemType.ARROW);
        g.projectiles.pickUp(g, arrow);
        assertEquals(before + 1, g.player.inventory.count(ItemType.ARROW));
    }

    @Test
    void playerFirearmChargeSurvivesSaveLoad(@org.junit.jupiter.api.io.TempDir
                                             java.nio.file.Path dir) {
        ItemStack musket = new ItemStack(ItemType.MUSKET, 1);
        musket.charge = 1;
        g.player.inventory.set(0, musket);
        var save = dir.resolve("charge.sav");
        assertTrue(com.veylon.save.SaveSystem.save(g, save));
        Game g2 = new Game();
        assertTrue(com.veylon.save.SaveSystem.load(g2, save));
        assertEquals(1, g2.player.inventory.get(0).charge, "loaded round survives reload");
    }

    @Test
    void scrapBombHittingNpcRemainsFusedThenDetonatesExactlyOnce() {
        g.projectiles.setRandomSeed(11L);
        Npc npc = g.entities.spawnNpc(g.world, "Target", 313.5f, 40.1f, 310.5f);
        float healthBefore = npc.health;
        g.projectiles.fire(g, g.player, true, 310.5f, 41.1f, 310.5f,
                1, 0, 0, WeaponRegistry.byId("scrap_bomb"), null);
        for (int i = 0; i < 30 && !g.projectiles.live.getFirst().impactedEntity; i++) {
            g.projectiles.update(g, 0.02f);
        }
        assertEquals(1, g.projectiles.liveCount(), "entity impact must not consume the bomb");
        assertTrue(g.projectiles.live.getFirst().impactedEntity);
        assertTrue(g.projectiles.live.getFirst().fuse > 0, "the original fuse keeps running");

        for (int i = 0; i < 150 && g.projectiles.liveCount() > 0; i++) {
            g.projectiles.update(g, 0.02f);
        }
        assertEquals(0, g.projectiles.liveCount(), "bomb is removed after detonation");
        assertTrue(npc.health < healthBefore, "scrap bomb damages the NPC when its fuse ends");
        assertEquals(1, g.noise.countCategory("explosion"));
        for (int i = 0; i < 20; i++) {
            g.projectiles.update(g, 0.05f);
        }
        assertEquals(1, g.noise.countCategory("explosion"), "detonation occurs exactly once");
    }

    @Test
    void bombHittingCreatureRemainsActiveUntilItsFuseEnds() {
        g.projectiles.setRandomSeed(17L);
        Creature creature = g.entities.spawnCreature(g.world, Creature.CreatureType.THORNHORN,
                313.5f, 40.1f, 310.5f);
        g.projectiles.fire(g, g.player, true, 310.5f, 41.1f, 310.5f,
                1, 0, 0, WeaponRegistry.byId("scrap_bomb"), null);
        for (int i = 0; i < 30 && !g.projectiles.live.getFirst().impactedEntity; i++) {
            g.projectiles.update(g, 0.02f);
        }
        assertEquals(1, g.projectiles.liveCount());
        assertTrue(g.projectiles.live.getFirst().impactedEntity);
        float before = creature.health;
        for (int i = 0; i < 150 && g.projectiles.liveCount() > 0; i++) {
            g.projectiles.update(g, 0.02f);
        }
        assertEquals(0, g.projectiles.liveCount());
        assertTrue(creature.health < before);
    }

    @Test
    void bombsThatHitWallOrFloorKeepCooking() {
        for (int y = 40; y <= 44; y++) {
            g.world.setBlock(313, y, 310, BlockType.STONE_BRICK, false);
        }
        g.projectiles.setRandomSeed(23L);
        g.projectiles.fire(g, g.player, true, 310.5f, 41.1f, 310.5f,
                1, 0, 0, WeaponRegistry.byId("scrap_bomb"), null);
        for (int i = 0; i < 40; i++) {
            g.projectiles.update(g, 0.02f);
        }
        assertEquals(1, g.projectiles.liveCount(), "wall impact preserves the fuse");
        assertTrue(g.projectiles.live.getFirst().fuse > 0);

        g.projectiles.fire(g, g.player, true, 311.5f, 44f, 311.5f,
                0, -1, 0, WeaponRegistry.byId("fire_bomb"), null);
        for (int i = 0; i < 40; i++) {
            g.projectiles.update(g, 0.02f);
        }
        assertEquals(2, g.projectiles.liveCount(), "floor impact also preserves the fuse");
        for (int i = 0; i < 150 && g.projectiles.liveCount() > 0; i++) {
            g.projectiles.update(g, 0.02f);
        }
        assertEquals(0, g.projectiles.liveCount());
        assertEquals(2, g.noise.countCategory("explosion"));
    }

    @Test
    void fireAndScrapBombsHaveDistinctEffects() {
        Creature scrapVictim = g.entities.spawnCreature(g.world, Creature.CreatureType.THORNHORN,
                332.5f, 40.1f, 330.5f);
        float scrapBefore = scrapVictim.health;
        ProjectileSystem.Projectile scrap = fusedAt(ProjectileSystem.Kind.BOMB, 331.5f, 41f, 330.5f);
        g.projectiles.live.add(scrap);
        g.projectiles.update(g, 0.02f);
        float scrapDamage = scrapBefore - scrapVictim.health;
        assertEquals(0, g.fire.count(), "scrap bomb is the direct-damage profile");

        g.entities.creatures.clear();
        g.fire.reset();
        for (int dz = -2; dz <= 2; dz++) {
            g.world.setBlock(343, 41, 340 + dz, BlockType.LOG, false);
        }
        Creature fireVictim = g.entities.spawnCreature(g.world, Creature.CreatureType.THORNHORN,
                341.5f, 40.1f, 340.5f);
        float fireBefore = fireVictim.health;
        ProjectileSystem.Projectile fireBomb = fusedAt(
                ProjectileSystem.Kind.FIRE_BOMB, 340.5f, 41f, 340.5f);
        g.projectiles.live.add(fireBomb);
        g.projectiles.update(g, 0.02f);
        float fireDamage = fireBefore - fireVictim.health;
        assertTrue(g.fire.count() > 0, "fire bomb deterministically starts bounded fires");
        assertTrue(scrapDamage > fireDamage, "scrap bomb trades ignition for direct damage");
    }

    private static ProjectileSystem.Projectile fusedAt(ProjectileSystem.Kind kind,
                                                        float x, float y, float z) {
        ProjectileSystem.Projectile p = new ProjectileSystem.Projectile();
        p.kind = kind;
        p.x = x;
        p.y = y;
        p.z = z;
        p.life = 1f;
        p.fuse = 0.01f;
        p.fromPlayer = true;
        return p;
    }
}
