package com.veylon.save;

import com.veylon.Game;
import com.veylon.combat.ProjectileSystem;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveExplosivePersistenceTest {

    private static final Vector3f EAST = new Vector3f(1, 0, 0);
    private static final Vector3f SOUTH = new Vector3f(0, 0, 1);

    @Test
    void gameplayThrownBombsAndPlacedKegKeepIndependentFusesAcrossSaveLoad(
            @TempDir Path directory) {
        Game game = new Game();
        game.newWorld(20260716L, true);
        flattenArena(game);
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        game.player.inventory.clear();
        game.player.hotbarSel = 0;
        game.player.pos.set(310.5f, 40.1f, 310.5f);
        game.camera.position.set(game.player.pos.x,
                game.player.pos.y + game.player.eyeHeight(), game.player.pos.z);
        game.projectiles.setRandomSeed(9917L);

        Vec3i keg = new Vec3i(342, 40, 342);
        select(game, ItemType.POWDER_KEG);
        assertTrue(game.placeSelectedBlockAt(keg.x(), keg.y(), keg.z()),
                "RMB gameplay placement consumes and places the selected keg");
        assertTrue(game.interactWithBlockAt(keg), "F interaction lights the placed keg");
        assertEquals(5f, game.world.kegFuses.get(keg), 0.0001f);
        assertEquals(Boolean.TRUE, game.world.kegFusePlayerAttribution.get(keg));

        select(game, ItemType.SCRAP_BOMB);
        assertTrue(game.updateThrownWeaponCommand(0f, true, EAST),
                "the native-input command consumes and throws the scrap bomb");
        assertEquals(0, game.player.inventory.count(ItemType.SCRAP_BOMB));

        // Advance the real fuse/flight and attack cooldown together. The first
        // bomb now has less time remaining than the bomb thrown below.
        for (int i = 0; i < 3; i++) {
            game.updateThrownWeaponCommand(0.41f, false, EAST);
            game.projectiles.update(game, 0.41f);
            game.explosions.tickFuses(game, 0.41f);
        }

        select(game, ItemType.FIRE_BOMB);
        assertTrue(game.updateThrownWeaponCommand(0f, true, SOUTH),
                "the same gameplay command throws the independently fused fire bomb");
        assertEquals(0, game.player.inventory.count(ItemType.FIRE_BOMB));
        assertEquals(2, game.projectiles.liveCount());

        ProjectileSystem.Projectile scrapBefore = explosive(game, ProjectileSystem.Kind.BOMB);
        ProjectileSystem.Projectile fireBefore = explosive(game, ProjectileSystem.Kind.FIRE_BOMB);
        float scrapFuse = scrapBefore.fuse;
        float fireFuse = fireBefore.fuse;
        float kegFuse = game.world.kegFuses.get(keg);
        assertTrue(scrapFuse > 0 && scrapFuse < fireFuse,
                "each thrown explosive owns its own remaining fuse");
        assertTrue(kegFuse > fireFuse, "the separately placed keg keeps its five-second timer");

        Path save = directory.resolve("active-explosives.sav");
        assertTrue(SaveSystem.save(game, save));

        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save));
        assertEquals(2, loaded.projectiles.liveCount(),
                "only the two active fused projectiles are restored");
        ProjectileSystem.Projectile restoredScrap =
                explosive(loaded, ProjectileSystem.Kind.BOMB);
        ProjectileSystem.Projectile restoredFire =
                explosive(loaded, ProjectileSystem.Kind.FIRE_BOMB);
        assertEquals(scrapFuse, restoredScrap.fuse, 0.0001f);
        assertEquals(fireFuse, restoredFire.fuse, 0.0001f);
        assertEquals(scrapBefore.x, restoredScrap.x, 0.0001f);
        assertEquals(scrapBefore.y, restoredScrap.y, 0.0001f);
        assertEquals(scrapBefore.z, restoredScrap.z, 0.0001f);
        assertEquals(scrapBefore.vx, restoredScrap.vx, 0.0001f);
        assertEquals(scrapBefore.vy, restoredScrap.vy, 0.0001f);
        assertEquals(scrapBefore.vz, restoredScrap.vz, 0.0001f);
        assertSame(loaded.player, restoredScrap.owner,
                "player attribution and owner collision filtering survive load");
        assertSame(loaded.player, restoredFire.owner);
        assertEquals(kegFuse, loaded.world.kegFuses.get(keg), 0.0001f);
        assertEquals(Boolean.TRUE, loaded.world.kegFusePlayerAttribution.get(keg),
                "the player source of the mid-fuse placed keg survives the optional v3 tail");

        for (int i = 0; i < 140
                && (loaded.projectiles.liveCount() > 0 || !loaded.world.kegFuses.isEmpty()); i++) {
            loaded.projectiles.update(loaded, 0.05f);
            loaded.explosions.tickFuses(loaded, 0.05f);
        }
        assertEquals(0, loaded.projectiles.liveCount(),
                "both restored projectiles detonate and leave the bounded live pool");
        assertTrue(loaded.world.kegFuses.isEmpty(), "the restored keg fuse is cleaned up");
        assertTrue(loaded.world.kegFusePlayerAttribution.isEmpty());
        assertEquals(BlockType.AIR, loaded.world.getBlock(keg.x(), keg.y(), keg.z()));
        assertTrue(loaded.world.changedBlocks.containsKey(keg),
                "the resolved placed keg remains a persisted world delta");
        assertEquals(3, loaded.noise.countCategory("explosion"),
                "two thrown bombs and one keg each resolve exactly once");

        Path resolved = directory.resolve("resolved-explosives.sav");
        assertTrue(SaveSystem.save(loaded, resolved));
        Game reloaded = new Game();
        assertTrue(SaveSystem.load(reloaded, resolved));
        assertEquals(0, reloaded.projectiles.liveCount(),
                "cleanup state cannot duplicate resolved explosives on another load");
        assertTrue(reloaded.world.kegFuses.isEmpty());
        assertTrue(reloaded.world.kegFusePlayerAttribution.isEmpty());
    }

    private static void select(Game game, ItemType item) {
        game.player.inventory.set(0, new ItemStack(item, 1));
        game.player.hotbarSel = 0;
    }

    private static ProjectileSystem.Projectile explosive(Game game,
                                                          ProjectileSystem.Kind kind) {
        return game.projectiles.live.stream()
                .filter(projectile -> projectile.kind == kind)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing active " + kind));
    }

    private static void flattenArena(Game game) {
        for (int cx = 19; cx <= 21; cx++) {
            for (int cz = 19; cz <= 21; cz++) {
                Chunk chunk = game.world.getOrCreateChunk(cx, cz);
                for (int lx = 0; lx < Chunk.SX; lx++) {
                    for (int lz = 0; lz < Chunk.SZ; lz++) {
                        for (int y = 0; y < Chunk.SY; y++) {
                            chunk.set(lx, y, lz, y <= 39 ? BlockType.STONE : BlockType.AIR);
                        }
                    }
                }
                chunk.recomputeAllHeights();
                chunk.rebuildLights();
            }
        }
    }
}
