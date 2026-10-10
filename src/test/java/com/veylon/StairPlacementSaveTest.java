package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.item.BlockItemForms;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.save.SaveSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.UrbanRevision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StairPlacementSaveTest {
    @TempDir Path directory;

    @Test
    void placementBreakReplacementAndProductionLoadRetainDirectionAndOneItemForm() {
        Game game = new Game();
        game.newWorld(20260731, true, World.GEN_URBAN_APARTMENTS, GameMode.SURVIVAL);
        game.world.getOrCreateChunk(0, 0);
        game.player.pos.set(2.5f, 70, 2.5f);
        game.player.inventory.set(game.player.hotbarSel, new ItemStack(ItemType.STAIRS, 8));
        float[] yaws = {0, 180, -90, 90};
        BlockType[] types = {BlockType.STAIRS_NORTH, BlockType.STAIRS_SOUTH,
                BlockType.STAIRS_WEST, BlockType.STAIRS_EAST};
        for (int i = 0; i < types.length; i++) {
            game.camera.yaw = yaws[i];
            assertTrue(game.placeSelectedBlockAt(8 + i, 70, 8));
            assertSame(types[i], game.world.getBlock(8 + i, 70, 8));
            assertSame(ItemType.STAIRS, BlockItemForms.of(types[i]));
        }
        assertEquals(4, game.player.inventory.count(ItemType.STAIRS));
        assertTrue(game.completePlayerBlockBreak(new Vec3i(8, 70, 8)));
        assertEquals(5, game.player.inventory.count(ItemType.STAIRS), "one stair drops one shared item");
        game.camera.yaw = 90;
        assertTrue(game.placeSelectedBlockAt(8, 70, 8));
        Path save = directory.resolve("stairs.sav");
        assertTrue(SaveSystem.save(game, save));
        Game loaded = new Game();
        loaded.newWorld(1, true, World.GEN_URBAN, GameMode.CREATIVE);
        assertTrue(SaveSystem.load(loaded, save));
        assertEquals(World.GEN_URBAN_APARTMENTS, loaded.world.generatorVersion);
        assertEquals(UrbanRevision.LAYOUT_GENERATED_APARTMENTS, loaded.world.urban.layoutRevision());
        loaded.world.getOrCreateChunk(0, 0);
        assertSame(BlockType.STAIRS_EAST, loaded.world.getBlock(8, 70, 8));
        for (int i = 1; i < types.length; i++) { assertSame(types[i], loaded.world.getBlock(8 + i, 70, 8)); }
        assertEquals(4, loaded.player.inventory.count(ItemType.STAIRS));
    }

}
