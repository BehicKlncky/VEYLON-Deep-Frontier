package com.veylon.combat;

import com.veylon.Game;
import com.veylon.item.ItemType;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Saturation behaviour of the fixed-size projectile pool.
 *
 * <p>{@code CombatSystemsTest} covers what a projectile does when it flies,
 * hits and sticks. This suite covers what happens when more are fired than the
 * pool can hold — an automatic firearm emptying a magazine into a wall, or a
 * long fight leaving arrows in every surface — because those caps are what
 * keeps frame time and memory bounded during a release smoke run.
 */
class ProjectilePoolBoundsTest {

    private Game g;
    private WeaponDefinition bow;

    @BeforeEach
    void setUp() {
        g = new Game();
        g.newWorld(4242L, true);
        // Flat stone arena at y=40 so shots have a predictable backstop.
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
        bow = WeaponRegistry.byId("primitive_bow");
    }

    @Test
    void firingPastCapacityDropsShotsInsteadOfGrowingTheLiveList() {
        // Fire well past MAX_LIVE without ticking, so nothing can retire.
        for (int i = 0; i < ProjectileSystem.MAX_LIVE + 10; i++) {
            g.projectiles.fire(g, g.player, true, 310.5f, 41.5f, 310.5f, 0, 1, 0,
                    bow, ItemType.ARROW);
        }
        assertEquals(ProjectileSystem.MAX_LIVE, g.projectiles.liveCount(),
                "the live list saturates at MAX_LIVE rather than growing");
    }

    @Test
    void aSaturatedPoolStillFlushesAndAcceptsNewShots() {
        for (int i = 0; i < ProjectileSystem.MAX_LIVE + 10; i++) {
            g.projectiles.fire(g, g.player, true, 310.5f, 41.5f, 310.5f, 0, 1, 0,
                    bow, ItemType.ARROW);
        }
        assertEquals(ProjectileSystem.MAX_LIVE, g.projectiles.liveCount());

        // Straight up: every arrow falls back and retires without sticking in a
        // wall, so the live list must drain completely.
        for (int i = 0; i < 400 && g.projectiles.liveCount() > 0; i++) {
            g.projectiles.update(g, 0.05f);
        }
        assertEquals(0, g.projectiles.liveCount(), "all shots eventually retire");

        // A recycled pool must still fire; a leak here would silently disarm
        // the player after one heavy fight.
        g.projectiles.fire(g, g.player, true, 310.5f, 41.5f, 310.5f, 0, 1, 0,
                bow, ItemType.ARROW);
        assertTrue(g.projectiles.liveCount() > 0,
                "the pool is reusable after saturating and draining");
    }

    @Test
    void stuckArrowsStayWithinTheirOwnCap() {
        // A wall to the east, then far more arrows into it than MAX_STUCK.
        for (int y = 38; y <= 46; y++) {
            for (int z = 300; z <= 320; z++) {
                g.world.setBlock(314, y, z, BlockType.STONE, false);
            }
        }
        for (int shot = 0; shot < ProjectileSystem.MAX_STUCK + 30; shot++) {
            g.projectiles.fire(g, g.player, true, 310.5f, 41.5f, 310.5f, 1, 0, 0,
                    bow, ItemType.ARROW);
            for (int i = 0; i < 40 && g.projectiles.liveCount() > 0; i++) {
                g.projectiles.update(g, 0.05f);
            }
        }
        assertTrue(g.projectiles.stuck.size() <= ProjectileSystem.MAX_STUCK,
                "stuck arrows are capped at MAX_STUCK, was " + g.projectiles.stuck.size());
        assertTrue(g.projectiles.stuck.size() > 0, "some arrows did stick");
    }

    @Test
    void resetReturnsEveryProjectileSoWorldChangesStartClean() {
        for (int i = 0; i < 20; i++) {
            g.projectiles.fire(g, g.player, true, 310.5f, 41.5f, 310.5f, 0, 1, 0,
                    bow, ItemType.ARROW);
        }
        assertTrue(g.projectiles.liveCount() > 0);

        g.projectiles.reset();
        assertEquals(0, g.projectiles.liveCount(), "reset clears live projectiles");
        assertEquals(0, g.projectiles.stuck.size(), "reset clears stuck projectiles");

        // Firing MAX_LIVE again proves reset recycled rather than discarded.
        for (int i = 0; i < ProjectileSystem.MAX_LIVE; i++) {
            g.projectiles.fire(g, g.player, true, 310.5f, 41.5f, 310.5f, 0, 1, 0,
                    bow, ItemType.ARROW);
        }
        assertEquals(ProjectileSystem.MAX_LIVE, g.projectiles.liveCount(),
                "a reset pool can be filled to capacity again");
    }
}
