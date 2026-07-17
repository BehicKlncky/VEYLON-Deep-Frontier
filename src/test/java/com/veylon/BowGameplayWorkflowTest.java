package com.veylon;

import com.veylon.combat.ProjectileSystem;
import com.veylon.combat.WeaponRegistry;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.ui.Hud;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BowGameplayWorkflowTest {

    private static final Vector3f EAST = new Vector3f(1, 0, 0);

    private Game g;
    private ItemStack bow;

    @BeforeEach
    void setUp() {
        g = new Game();
        g.newWorld(20260716L, true);
        g.player.inventory.clear();
        g.player.hotbarSel = 0;
        bow = new ItemStack(ItemType.PRIMITIVE_BOW, 1);
        g.player.inventory.set(0, bow);
        flattenArena();
        g.player.pos.set(310.5f, 40.1f, 310.5f);
        g.camera.position.set(g.player.pos.x, g.player.pos.y + g.player.eyeHeight(), g.player.pos.z);
        g.entities.creatures.clear();
        g.entities.npcs.clear();
        g.projectiles.setRandomSeed(1L);
    }

    @Test
    void bowInputCyclesToIronAndReleaseConsumesOnlySelectedAmmoAndDurability() {
        g.player.inventory.add(ItemType.ARROW, 4);
        g.player.inventory.add(ItemType.IRON_ARROW, 3);

        assertEquals(ItemType.ARROW, g.selectedBowAmmo());
        assertEquals(ItemType.IRON_ARROW, g.cycleBowAmmo(), "R selects iron while both exist");
        String hud = Hud.bowAmmoLabel(g);
        assertTrue(hud.contains("Selected Iron Arrow [R]"));
        assertTrue(hud.contains("Basic 4"));
        assertTrue(hud.contains("Iron 3"));

        float durabilityBefore = bow.durability;
        assertEquals(Game.BowCommandResult.DRAWING,
                g.updateBowCommand(0.9f, true, true, EAST));
        assertEquals(1f, g.bowDraw, 0.0001f, "a full hold reaches maximum draw");
        assertEquals(Game.BowCommandResult.FIRED,
                g.updateBowCommand(0f, false, false, EAST));

        assertEquals(4, g.player.inventory.count(ItemType.ARROW),
                "basic arrows are not forced while iron is selected");
        assertEquals(2, g.player.inventory.count(ItemType.IRON_ARROW));
        assertEquals(durabilityBefore - 1f, bow.durability, 0.0001f);
        assertEquals(1, g.projectiles.liveCount());
        ProjectileSystem.Projectile shot = g.projectiles.live.getFirst();
        assertEquals(ItemType.IRON_ARROW, shot.ammoItem);
        assertTrue(shot.damage > WeaponRegistry.byId("primitive_bow").damage,
                "iron arrow damage bonus follows the selected ammo");
        assertEquals(1, g.noise.countCategory("bow"));
    }

    @Test
    void shortDrawCancelsWithoutCostAndShotCooldownBlocksImmediateRedraw() {
        g.player.inventory.add(ItemType.ARROW, 4);
        int ammoBefore = g.player.inventory.count(ItemType.ARROW);
        float durabilityBefore = bow.durability;

        assertEquals(Game.BowCommandResult.DRAWING,
                g.updateBowCommand(0.1f, true, true, EAST));
        assertTrue(g.bowDraw < 0.3f);
        assertEquals(Game.BowCommandResult.CANCELLED,
                g.updateBowCommand(0f, false, false, EAST));
        assertFalse(g.drawingBow);
        assertEquals(0f, g.bowDraw, 0.0001f);
        assertEquals(ammoBefore, g.player.inventory.count(ItemType.ARROW));
        assertEquals(durabilityBefore, bow.durability, 0.0001f);
        assertEquals(0, g.projectiles.liveCount());
        assertEquals(0, g.noise.countCategory("bow"));

        fireFullDraw();
        assertEquals(Game.BowCommandResult.COOLDOWN,
                g.updateBowCommand(0.01f, true, true, EAST),
                "the bow cannot start another draw during its attack interval");
        assertFalse(g.drawingBow);
        assertEquals(Game.BowCommandResult.NONE,
                g.updateBowCommand(0.5f, false, false, EAST));
        assertEquals(Game.BowCommandResult.DRAWING,
                g.updateBowCommand(0.1f, true, true, EAST),
                "drawing becomes available after the cooldown elapses");
    }

    @Test
    void firedArrowsUseTheFInteractionRecoveryPathAndBothPoolsCleanUp() {
        g.player.inventory.add(ItemType.ARROW, 4);
        buildEastWall();
        int initialAmmo = g.player.inventory.count(ItemType.ARROW);

        fireFullDraw();
        flyUntilImpact();
        assertEquals(0, g.projectiles.liveCount(), "the impacted projectile leaves the live pool");
        assertEquals(1, g.projectiles.stuck.size(), "the seeded arrow sticks in the wall");

        g.player.pos.x = 312f;
        assertTrue(g.recoverNearbyArrow(), "the same command used by F recovers a nearby arrow");
        assertEquals(initialAmmo, g.player.inventory.count(ItemType.ARROW));
        assertEquals(0, g.projectiles.stuck.size());

        // A second gameplay-fired arrow proves unrecovered arrows also leave the
        // bounded stuck pool when their lifetime ends.
        g.updateBowCommand(0.5f, false, false, EAST);
        g.camera.position.set(310.5f, 40.1f + g.player.eyeHeight(), 310.5f);
        g.player.pos.x = 310.5f;
        g.projectiles.setRandomSeed(1L);
        fireFullDraw();
        flyUntilImpact();
        assertEquals(1, g.projectiles.stuck.size());
        g.projectiles.update(g, ProjectileSystem.STUCK_LIFE + 0.1f);
        assertEquals(0, g.projectiles.liveCount());
        assertEquals(0, g.projectiles.stuck.size(), "stale recoverable arrows cannot leak");
    }

    private void fireFullDraw() {
        assertEquals(Game.BowCommandResult.DRAWING,
                g.updateBowCommand(0.9f, true, true, EAST));
        assertEquals(Game.BowCommandResult.FIRED,
                g.updateBowCommand(0f, false, false, EAST));
    }

    private void flyUntilImpact() {
        for (int i = 0; i < 80 && g.projectiles.liveCount() > 0; i++) {
            g.projectiles.update(g, 0.02f);
        }
    }

    private void buildEastWall() {
        for (int y = 39; y <= 45; y++) {
            for (int z = 305; z <= 315; z++) {
                g.world.setBlock(314, y, z, BlockType.STONE, false);
            }
        }
    }

    private void flattenArena() {
        Chunk chunk = g.world.getOrCreateChunk(19, 19);
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
