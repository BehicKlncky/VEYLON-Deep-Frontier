package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.item.BlockItemForms;
import com.veylon.item.CreativePalette;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.save.SaveSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Raycaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** R24: the palette in a real world - placement, pick block, save and Survival drops. */
class CreativePaletteWorldTest {
    private static final int BASE_Y = 41;
    private static final int ROW_Z = 306;
    private Game game;
    @TempDir Path directory;

    @BeforeEach
    void setUp() { game = CreativeTestArena.create(GameMode.CREATIVE); }

    @Test
    void everyPaletteBlockPlacesFromAFullStackWithoutSpendingIt() {
        int x = 304;
        for (ItemType item : CreativePalette.items()) {
            game.player.inventory.set(0, new ItemStack(item, item.maxStack));
            game.player.hotbarSel = 0;
            assertTrue(game.placeSelectedBlockAt(x, BASE_Y, ROW_Z),
                    "R24: " + item + " must place its block");
            assertEquals(item.places(), game.world.getBlock(x, BASE_Y, ROW_Z),
                    "R24: " + item + " places exactly the audited block");
            assertEquals(item.maxStack, game.player.selected().count,
                    "R20: Creative placement never spends the palette stack");
            x++;
        }
    }

    @Test
    void pickBlockReturnsThePaletteFormOfEveryNewlyBuildableBlock() {
        for (ItemType item : CreativePalette.items()) {
            BlockType block = item.places();
            game.player.inventory.clear();
            game.player.hotbarSel = 0;
            Vec3i pos = new Vec3i(306, BASE_Y, ROW_Z);
            game.world.setBlock(pos.x(), pos.y(), pos.z(), block, false);
            game.targetHit = new Raycaster.Hit(pos.x(), pos.y(), pos.z(), -1, 0, 0, 2, block);
            assertTrue(game.pickBlock(), "R21: " + block + " now has an item form to pick");
            assertEquals(item, game.player.selected().type, "R21: pick resolves " + block);
            assertEquals(item.maxStack, game.player.selected().count,
                    "R21: pick grants a full stack into the empty hotbar");
        }
    }

    @Test
    void placedPaletteBlocksAndCarriedPaletteStacksSurviveASaveAndLoad() {
        List<ItemType> items = new ArrayList<>(CreativePalette.items());
        int x = 304;
        for (ItemType item : items) {
            game.world.setBlock(x, BASE_Y, ROW_Z, item.places(), true);
            x++;
        }
        for (int i = 0; i < 9 && i < items.size(); i++) {
            game.player.inventory.set(i, new ItemStack(items.get(i), 7));
        }
        Path save = directory.resolve("palette.sav");
        assertTrue(SaveSystem.save(game, save), "R24: a world holding palette items must save");

        Game reloaded = new Game();
        reloaded.newWorld(4422, true);
        assertTrue(SaveSystem.load(reloaded, save), "R24: and must load again");
        assertEquals(GameMode.CREATIVE, reloaded.gameMode());
        x = 304;
        for (ItemType item : items) {
            assertEquals(item.places(), reloaded.world.getBlock(x, BASE_Y, ROW_Z),
                    "R24: " + item.places() + " survives the chunk round trip");
            x++;
        }
        for (int i = 0; i < 9 && i < items.size(); i++) {
            ItemStack stack = reloaded.player.inventory.get(i);
            assertNotNull(stack, "R24: carried palette stack " + i + " survives");
            assertEquals(items.get(i), stack.type, "R24: appended ordinals round trip by value");
            assertEquals(7, stack.count);
        }
    }

    @Test
    void survivalKeepsTheOldDropAndSpendsACarriedPaletteStackNormally() {
        Game survival = CreativeTestArena.create(GameMode.SURVIVAL);
        Vec3i pos = new Vec3i(312, 40, ROW_Z);
        survival.world.setBlock(pos.x(), pos.y(), pos.z(), BlockType.GRASS, false);
        survival.targetHit = new Raycaster.Hit(pos.x(), pos.y(), pos.z(), -1, 0, 0, 2, BlockType.GRASS);
        assertTrue(survival.completePlayerBlockBreak(pos));
        assertEquals(1, survival.player.inventory.count(ItemType.DIRT),
                "R24: breaking grass in Survival still yields dirt");
        assertEquals(0, survival.player.inventory.count(ItemType.GRASS_BLOCK),
                "R24: Survival never receives the Creative-only form");
        assertFalse(survival.pickBlock(), "R21: pick stays a Creative ability");

        // A world switched back to Survival keeps whatever the player carried,
        // and those stacks then behave like every other placeable item.
        survival.player.inventory.set(0, new ItemStack(ItemType.BASALT_BLOCK, 3));
        survival.player.hotbarSel = 0;
        assertTrue(survival.placeSelectedBlockAt(313, 41, ROW_Z));
        assertEquals(BlockType.BASALT, survival.world.getBlock(313, 41, ROW_Z));
        assertEquals(2, survival.player.selected().count,
                "R24: outside Creative a palette stack is spent like any block item");
        assertSame(ItemType.BASALT_BLOCK, BlockItemForms.of(BlockType.BASALT));
    }
}
