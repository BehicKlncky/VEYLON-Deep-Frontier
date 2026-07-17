package com.veylon.world;

import com.veylon.Game;
import com.veylon.item.CraftingSystem;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.item.Recipe;
import com.veylon.item.Station;
import com.veylon.save.SaveSystem;
import com.veylon.util.Vec3i;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanternGameplayTest {

    @TempDir
    Path tempDir;

    @Test
    void craftPlaceRefuelToggleBurnOutAndRemoveUseGameplayCommands() {
        Game g = newGame(20260716L);
        g.player.inventory.clear();
        g.player.inventory.add(ItemType.IRON_INGOT, 2);
        g.player.inventory.add(ItemType.TORCH, 1);
        g.player.inventory.add(ItemType.SCRAP, 1);

        Recipe recipe = CraftingSystem.RECIPES.stream()
                .filter(candidate -> candidate.result == ItemType.LANTERN)
                .findFirst().orElseThrow();
        assertEquals("Lantern", CraftingSystem.craft(g.player.inventory, recipe,
                EnumSet.of(Station.ANVIL), new HashSet<>()));
        g.player.hotbarSel = slotOf(g, ItemType.LANTERN);

        Vec3i pos = emptyHighCell(g, 5, 0);
        assertTrue(g.placeSelectedBlockAt(pos.x(), pos.y(), pos.z()));
        assertEquals(BlockType.LANTERN, g.world.getBlock(pos.x(), pos.y(), pos.z()));
        World.LanternState placed = g.world.lanternState(pos);
        assertNotNull(placed);
        assertEquals(0, placed.fuelSeconds(), 0.001f);
        assertFalse(placed.lit());
        assertFalse(hasExactLight(g.world, pos));
        assertTrue(g.lanternPrompt(pos).contains("UNLIT"));
        assertTrue(g.lanternPrompt(pos).contains("charcoal"));

        g.player.inventory.add(ItemType.CHARCOAL, 1);
        g.player.hotbarSel = slotOf(g, ItemType.CHARCOAL);
        assertTrue(g.interactLantern(pos));
        assertEquals(0, g.player.inventory.count(ItemType.CHARCOAL));
        assertEquals(World.LANTERN_FUEL_PER_CHARCOAL,
                g.world.lanternState(pos).fuelSeconds(), 0.001f);
        assertFalse(g.world.lanternState(pos).lit(), "refueling is distinct from ignition");

        assertTrue(g.interactLantern(pos));
        assertTrue(g.world.lanternState(pos).lit());
        assertTrue(hasExactLight(g.world, pos));
        assertTrue(g.lanternPrompt(pos).contains("LIT"));

        float beforeBurn = g.world.lanternState(pos).fuelSeconds();
        g.fire.mediumTick(g, 12.5f);
        assertEquals(beforeBurn - 12.5f, g.world.lanternState(pos).fuelSeconds(), 0.001f);

        assertTrue(g.interactLantern(pos));
        assertFalse(g.world.lanternState(pos).lit());
        assertFalse(hasExactLight(g.world, pos));
        float pausedFuel = g.world.lanternState(pos).fuelSeconds();
        g.fire.mediumTick(g, 20f);
        assertEquals(pausedFuel, g.world.lanternState(pos).fuelSeconds(), 0.001f,
                "an extinguished lantern preserves its fuel");

        assertTrue(g.interactLantern(pos));
        assertTrue(g.world.restoreLanternState(pos, 0.25f, true));
        g.fire.mediumTick(g, 0.5f);
        assertEquals(0, g.world.lanternState(pos).fuelSeconds(), 0.001f);
        assertFalse(g.world.lanternState(pos).lit());
        assertFalse(hasExactLight(g.world, pos));

        g.world.setBlock(pos.x(), pos.y(), pos.z(), BlockType.AIR, true);
        assertFalse(g.world.lanterns.containsKey(pos));
        assertNull(g.world.lanternState(pos));
        assertFalse(hasExactLight(g.world, pos));
    }

    @Test
    void independentLanternsRoundTripFuelLightAndReplacementCleanup() {
        Game g = newGame(-1234567L);
        Vec3i first = emptyHighCell(g, 4, 0);
        Vec3i second = emptyHighCell(g, 7, 0);
        g.world.setBlock(first.x(), first.y(), first.z(), BlockType.LANTERN, true);
        g.world.setBlock(second.x(), second.y(), second.z(), BlockType.LANTERN, true);

        refuelThroughInteraction(g, first);
        assertTrue(g.interactLantern(first));
        refuelThroughInteraction(g, second);
        float firstFuel = g.world.lanternState(first).fuelSeconds();
        float secondFuel = g.world.lanternState(second).fuelSeconds();
        g.fire.mediumTick(g, 17.25f);
        firstFuel -= 17.25f;
        assertEquals(firstFuel, g.world.lanternState(first).fuelSeconds(), 0.001f);
        assertEquals(secondFuel, g.world.lanternState(second).fuelSeconds(), 0.001f);
        assertTrue(g.world.lanternState(first).lit());
        assertFalse(g.world.lanternState(second).lit());

        Path save = tempDir.resolve("lantern-round-trip.sav");
        assertTrue(SaveSystem.save(g, save));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save));

        assertEquals(firstFuel, loaded.world.lanternState(first).fuelSeconds(), 0.001f);
        assertEquals(secondFuel, loaded.world.lanternState(second).fuelSeconds(), 0.001f);
        assertTrue(loaded.world.lanternState(first).lit());
        assertFalse(loaded.world.lanternState(second).lit());
        assertTrue(hasExactLight(loaded.world, first));
        assertFalse(hasExactLight(loaded.world, second));
        assertEquals(2, loaded.world.lanterns.size(), "load does not duplicate position state");

        loaded.world.setBlock(first.x(), first.y(), first.z(), BlockType.STONE, true);
        assertFalse(loaded.world.lanterns.containsKey(first));
        assertFalse(hasExactLight(loaded.world, first));
        loaded.world.setBlock(first.x(), first.y(), first.z(), BlockType.LANTERN, true);
        World.LanternState replacement = loaded.world.lanternState(first);
        assertNotNull(replacement);
        assertEquals(0, replacement.fuelSeconds(), 0.001f);
        assertFalse(replacement.lit(), "a replacement cannot inherit stale fuel or light");
        assertEquals(2, loaded.world.lanterns.size());
    }

    @Test
    void extensionVersionOneLoadsPlacedLanternWithSafeUnlitDefault() throws Exception {
        Game original = newGame(424242L);
        Vec3i pos = emptyHighCell(original, 6, 0);
        original.world.setBlock(pos.x(), pos.y(), pos.z(), BlockType.LANTERN, true);
        assertTrue(original.world.restoreLanternState(pos, 611.5f, true));
        original.world.refreshLoadedLights();

        Path save = tempDir.resolve("extension-v1.sav");
        assertTrue(SaveSystem.save(original, save));
        byte[] bytes = Files.readAllBytes(save);
        int sectionEnvelopeOffset = lastIndexOf(bytes,
                new byte[]{0x53, 0x33, 0x45, 0x43});
        assertTrue(sectionEnvelopeOffset > 0, "current fixture contains the v2 section envelope");
        int extensionOffset = lastIndexOf(bytes, new byte[]{0x57, 0x33, 0x45, 0x58});
        assertTrue(extensionOffset > 0);
        writeInt(bytes, extensionOffset + Integer.BYTES, 1);
        Files.write(save, Arrays.copyOf(bytes, sectionEnvelopeOffset));

        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save));
        assertEquals(BlockType.LANTERN, loaded.world.getBlock(pos.x(), pos.y(), pos.z()));
        World.LanternState safeDefault = loaded.world.lanternState(pos);
        assertNotNull(safeDefault);
        assertEquals(0, safeDefault.fuelSeconds(), 0.001f);
        assertFalse(safeDefault.lit());
        assertFalse(hasExactLight(loaded.world, pos));
    }

    private static Game newGame(long seed) {
        Game game = new Game();
        game.newWorld(seed, true);
        return game;
    }

    private static Vec3i emptyHighCell(Game g, int dx, int dz) {
        int x = (int) Math.floor(g.player.pos.x) + dx;
        int z = (int) Math.floor(g.player.pos.z) + dz;
        int y = Chunk.SY - 2;
        g.world.getOrCreateChunk(Math.floorDiv(x, Chunk.SX), Math.floorDiv(z, Chunk.SZ));
        g.world.setBlock(x, y, z, BlockType.AIR, false);
        return new Vec3i(x, y, z);
    }

    private static void refuelThroughInteraction(Game g, Vec3i pos) {
        g.player.inventory.add(ItemType.CHARCOAL, 1);
        g.player.hotbarSel = slotOf(g, ItemType.CHARCOAL);
        assertTrue(g.interactLantern(pos));
    }

    private static int slotOf(Game g, ItemType type) {
        for (int i = 0; i < g.player.inventory.size(); i++) {
            ItemStack stack = g.player.inventory.get(i);
            if (stack != null && stack.type == type) {
                return i;
            }
        }
        throw new AssertionError("missing " + type);
    }

    private static boolean hasExactLight(World world, Vec3i pos) {
        Chunk chunk = world.getChunk(Math.floorDiv(pos.x(), Chunk.SX),
                Math.floorDiv(pos.z(), Chunk.SZ));
        return chunk != null && chunk.lights.stream().anyMatch(light ->
                light[0] == pos.x() && light[1] == pos.y() && light[2] == pos.z());
    }

    private static int lastIndexOf(byte[] bytes, byte[] needle) {
        outer:
        for (int i = bytes.length - needle.length; i >= 0; i--) {
            for (int j = 0; j < needle.length; j++) {
                if (bytes[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }
}
