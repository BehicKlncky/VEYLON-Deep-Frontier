package com.veylon.world;

import com.veylon.Game;
import com.veylon.item.CraftingSystem;
import com.veylon.item.ItemType;
import com.veylon.item.Recipe;
import com.veylon.save.SaveSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Production crafting, placement, traversal, readability, and persistence for cave tools. */
class CaveToolsGameplayIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void craftedLadderClimbsWithoutBlockingAndCraftedMarkerRemainsReadableAtNightAfterLoad() {
        Game game = new Game();
        game.newWorld(-1234567L, true);
        flatten(game, -20, -20);
        game.player.inventory.clear();
        game.player.pos.set(-310.5f, 40.1f, -310.5f);
        game.camera.position.set(game.player.pos.x,
                game.player.pos.y + game.player.eyeHeight(), game.player.pos.z);

        game.world.setBlock(-309, 40, -310, BlockType.WORKBENCH, false);
        game.player.inventory.add(ItemType.STICK, 5);
        game.player.inventory.add(ItemType.FIBER, 7);

        Recipe ladderRecipe = recipe(ItemType.ROPE_LADDER);
        Recipe markerRecipe = recipe(ItemType.TRAIL_MARKER);
        assertEquals("Rope Ladder x3", CraftingSystem.craft(game.player.inventory,
                ladderRecipe, game.nearbyStations(), game.player.blueprints));
        assertEquals("Trail Marker x4", CraftingSystem.craft(game.player.inventory,
                markerRecipe, game.nearbyStations(), game.player.blueprints));
        assertEquals(3, game.player.inventory.count(ItemType.ROPE_LADDER));
        assertEquals(4, game.player.inventory.count(ItemType.TRAIL_MARKER));

        select(game, ItemType.ROPE_LADDER);
        for (int y = 40; y <= 42; y++) {
            assertTrue(game.placeSelectedBlockAt(-308, y, -310),
                    "RMB gameplay placement builds the complete climb");
        }
        select(game, ItemType.TRAIL_MARKER);
        assertTrue(game.placeSelectedBlockAt(-312, 40, -310));

        game.time.totalMinutes = 22 * 60;
        assertTrue(game.time.isNight());
        assertFalse(BlockType.LADDER.solid);
        assertFalse(BlockType.TRAIL_MARKER.opaque);
        assertTrue(game.world.blockLight(-312, 40, -310) > 0.15f,
                "the placed marker emits its own low night-readable light");
        assertTrue(game.world.blockLight(-311, 40, -310) > 0f,
                "marker readability extends beyond its own voxel");

        // Enter the actual non-solid ladder volume. A solid mounting wall still
        // collides, while the placed climbable itself does not.
        game.player.pos.set(-307.5f, 40.1f, -309.5f);
        game.player.vel.zero();
        game.player.applyPhysics(0.01f, true);
        assertTrue(game.player.onLadder);
        assertFalse(game.player.collidesAt(-307.5f, 40.1f, -309.5f));
        game.world.setBlock(-308, 40, -311, BlockType.STONE, false);
        game.world.setBlock(-308, 41, -311, BlockType.STONE, false);
        assertTrue(game.player.collidesAt(-307.5f, 40.1f, -310.5f),
                "ordinary solid cave walls retain collision beside a ladder");

        float beforeClimb = game.player.pos.y;
        assertTrue(game.applyPlayerClimbCommand(true, false),
                "the native Space climb command accepts the placed ladder");
        game.player.applyPhysics(0.25f, true);
        assertTrue(game.player.pos.y > beforeClimb + 0.5f,
                "ladder intent produces real collision-aware upward traversal");
        assertTrue(game.player.onLadder);

        Path save = tempDir.resolve("placed-cave-tools.sav");
        assertTrue(SaveSystem.save(game, save));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save));
        for (int y = 40; y <= 42; y++) {
            assertEquals(BlockType.LADDER, loaded.world.getBlock(-308, y, -310));
        }
        assertEquals(BlockType.TRAIL_MARKER, loaded.world.getBlock(-312, 40, -310));
        assertTrue(loaded.world.blockLight(-312, 40, -310) > 0.15f);
        assertTrue(loaded.time.isNight());
    }

    private static Recipe recipe(ItemType result) {
        Recipe recipe = CraftingSystem.RECIPES.stream()
                .filter(candidate -> candidate.result == result)
                .findFirst().orElse(null);
        assertNotNull(recipe);
        return recipe;
    }

    private static void select(Game game, ItemType type) {
        for (int i = 0; i < game.player.inventory.size(); i++) {
            if (game.player.inventory.get(i) != null
                    && game.player.inventory.get(i).type == type) {
                game.player.hotbarSel = i;
                return;
            }
        }
        throw new AssertionError("Missing crafted item: " + type);
    }

    private static void flatten(Game game, int cx, int cz) {
        Chunk chunk = game.world.getOrCreateChunk(cx, cz);
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
