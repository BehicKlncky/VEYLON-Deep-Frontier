package com.veylon.combat;

import com.veylon.Game;
import com.veylon.entity.GameMode;
import com.veylon.item.ItemType;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShapedStairProjectileTest {
    @Test
    void anArrowPassesTheMissingUpperQuarterButHitsTheLowTread() {
        Game game = new Game();
        game.newWorld(7, true, World.GEN_URBAN, GameMode.SURVIVAL);
        game.world.getOrCreateChunk(0, 0);
        game.world.setBlock(8, 50, 8, BlockType.STAIRS_EAST, true);
        for (float y : new float[]{50.75f, 50.25f}) {
            game.projectiles.reset();
            game.projectiles.fire(game, game.player, true, 8.2f, y, 7,
                    0, 0, 1, WeaponRegistry.byId("primitive_bow"), ItemType.ARROW);
            // Isolate the stair intersection from random spread and ballistic drop.
            var arrow = game.projectiles.live.getFirst();
            arrow.vx = 0; arrow.vy = 0; arrow.vz = 10; arrow.gravity = 0;
            for (int i = 0; i < 20; i++) { game.projectiles.update(game, .01f); }
            if (y > 50.5f) {
                assertEquals(1, game.projectiles.liveCount());
                assertTrue(arrow.z > 8.9f);
                assertTrue(game.projectiles.stuck.isEmpty());
            } else {
                assertEquals(0, game.projectiles.liveCount());
                // Recovery is probabilistic; impact always consumes the flying arrow.
            }
        }
    }
}
