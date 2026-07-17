package com.veylon;

import com.veylon.combat.ProjectileSystem;
import com.veylon.combat.WorldNoise;
import com.veylon.entity.Carcass;
import com.veylon.entity.Creature;
import com.veylon.item.CraftingSystem;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.item.Recipe;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end production progression from bow crafting through a recovered hunt. */
class BowHuntProgressionIntegrationTest {

    @Test
    void craftedBowHuntsRealCreatureRecoversLodgedArrowAndIsQuieterThanFirearm() {
        Game game = new Game();
        game.newWorld(987654321L, true);
        flattenArena(game);
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        game.entities.carcasses.clear();
        game.player.inventory.clear();
        game.player.hotbarSel = 0;
        game.player.pos.set(310.5f, 40.1f, 310.5f);
        game.camera.position.set(game.player.pos.x,
                game.player.pos.y + game.player.eyeHeight(), game.player.pos.z);
        game.world.setBlock(309, 40, 310, BlockType.WORKBENCH, false);

        // These are gathered recipe ingredients, not a prebuilt weapon or ammo.
        game.player.inventory.add(ItemType.STICK, 6);
        game.player.inventory.add(ItemType.FIBER, 7);
        game.player.inventory.add(ItemType.STONE, 1);
        game.player.inventory.add(ItemType.BONE, 1);
        assertEquals("Primitive Bow", craft(game, ItemType.PRIMITIVE_BOW));
        assertEquals("Arrow x4", craft(game, ItemType.ARROW));
        assertEquals("Bone Knife", craft(game, ItemType.BONE_KNIFE));
        assertEquals(1, game.player.inventory.count(ItemType.PRIMITIVE_BOW));
        assertEquals(4, game.player.inventory.count(ItemType.ARROW));

        int bowSlot = findSlot(game, ItemType.PRIMITIVE_BOW);
        game.player.hotbarSel = bowSlot;
        ItemStack craftedBow = game.player.selected();
        float durabilityBefore = craftedBow.durability;

        Creature hare = new Creature(game.world, Creature.CreatureType.HARE);
        hare.pos.set(315.5f, 40.1f, 310.5f);
        hare.onGround = true;
        game.entities.creatures.add(hare);
        game.projectiles.setRandomSeed(1L);
        // Aim high in the small animal's body to account for real arrow drop.
        Vector3f aim = new Vector3f(hare.pos.x,
                hare.pos.y + hare.height * 0.9f, hare.pos.z)
                .sub(game.camera.position).normalize();

        assertEquals(Game.BowCommandResult.DRAWING,
                game.updateBowCommand(1.2f, true, true, aim));
        assertEquals(Game.BowCommandResult.FIRED,
                game.updateBowCommand(0f, false, false, aim));
        assertEquals(3, game.player.inventory.count(ItemType.ARROW));
        assertEquals(durabilityBefore - 1f, craftedBow.durability, 0.0001f);
        assertEquals(1, game.noise.countCategory("bow"));
        WorldNoise.NoiseEvent bowNoise = game.noise.loudestAudible(
                game.camera.position.x, game.camera.position.y, game.camera.position.z, 0);
        assertNotNull(bowNoise);
        assertEquals("bow", bowNoise.category);

        for (int i = 0; i < 100 && !hare.dead; i++) {
            game.projectiles.update(game, 0.02f);
        }
        assertTrue(hare.dead, "the held-draw/release projectile kills the live hare");
        assertEquals(0, game.projectiles.liveCount());
        assertTrue(hare.lastHitByPlayer);
        assertEquals(1, hare.stuckArrows,
                "the deterministic arrow impact lodges in the hunted animal");

        game.entities.fastTick(game, 0f);
        assertFalse(game.entities.creatures.contains(hare));
        assertEquals(1, game.entities.carcasses.size());
        Carcass carcass = game.entities.carcasses.getFirst();
        assertEquals(1, carcass.stuckArrows,
                "normal death processing transfers lodged arrows to the carcass");

        // The separately crafted knife permits full hide salvage; the arrow
        // itself comes back only through the same nearby-carcass command used by F.
        game.player.pos.set(carcass.pos.x, carcass.pos.y, carcass.pos.z);
        assertTrue(game.interactWithNearbyCarcass());
        assertEquals(4, game.player.inventory.count(ItemType.ARROW));
        assertEquals(0, carcass.stuckArrows);

        // Compare sound produced by actual commands. The firearm is a measurement
        // fixture; no firearm acquisition claim is made by this bow progression.
        game.noise.reset();
        game.player.hotbarSel = bowSlot;
        game.updateBowCommand(1f, false, false, aim);
        ItemStack musket = new ItemStack(ItemType.MUSKET, 1);
        musket.charge = 1;
        int firearmSlot = firstEmptySlot(game);
        game.player.inventory.set(firearmSlot, musket);
        game.player.hotbarSel = firearmSlot;
        assertEquals(Game.FirearmCommandResult.FIRED,
                game.updateFirearmCommand(0f, true, true, false,
                        new Vector3f(1, 0, 0)));
        WorldNoise.NoiseEvent gunNoise = game.noise.loudestAudible(
                game.camera.position.x, game.camera.position.y, game.camera.position.z, 0);
        assertNotNull(gunNoise);
        assertEquals("gunshot", gunNoise.category);
        assertTrue(gunNoise.radius > bowNoise.radius * 4f,
                "the gameplay firearm report carries much farther than the bow release");

        game.projectiles.update(game, 10f);
        assertEquals(0, game.projectiles.liveCount());
        assertTrue(game.projectiles.stuck.size() <= ProjectileSystem.MAX_STUCK);
    }

    private static String craft(Game game, ItemType result) {
        Recipe recipe = CraftingSystem.RECIPES.stream()
                .filter(candidate -> candidate.result == result)
                .findFirst().orElseThrow();
        return CraftingSystem.craft(game.player.inventory, recipe,
                game.nearbyStations(), game.player.blueprints);
    }

    private static int findSlot(Game game, ItemType type) {
        for (int i = 0; i < game.player.inventory.size(); i++) {
            if (game.player.inventory.get(i) != null
                    && game.player.inventory.get(i).type == type) {
                return i;
            }
        }
        throw new AssertionError("Missing item: " + type);
    }

    private static int firstEmptySlot(Game game) {
        for (int i = 0; i < game.player.inventory.size(); i++) {
            if (game.player.inventory.get(i) == null) {
                return i;
            }
        }
        throw new AssertionError("No empty inventory slot");
    }

    private static void flattenArena(Game game) {
        Chunk chunk = game.world.getOrCreateChunk(19, 19);
        for (int lx = 0; lx < Chunk.SX; lx++) {
            for (int lz = 0; lz < Chunk.SZ; lz++) {
                for (int y = 0; y < Chunk.SY; y++) {
                    chunk.set(lx, y, lz, y <= 39 ? BlockType.STONE : BlockType.AIR);
                }
            }
        }
        chunk.recomputeAllHeights();
        chunk.rebuildLights(game.world);
    }
}
