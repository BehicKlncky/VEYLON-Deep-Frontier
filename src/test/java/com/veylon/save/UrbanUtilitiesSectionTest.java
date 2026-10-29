package com.veylon.save;

import com.veylon.Game;
import com.veylon.RefugeWorldArena;
import com.veylon.SurvivorWorldArena;
import com.veylon.UtilityWorldArena;
import com.veylon.entity.GameMode;
import com.veylon.item.ItemType;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanUtilities;
import com.veylon.world.urban.UrbanUtilities.Device;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code urban.utilities} v1 (urban milestone 19): the water drawn from a building's taps and every
 * device's switch, fuel and wear come back from every load exactly, so emptied pipes stay empty and
 * nothing is refilled or caught up; a generator-18 save without the section, the section in any
 * other world and every malformed row fail the whole load and leave the live world alone.
 */
class UrbanUtilitiesSectionTest {
    @TempDir Path directory;

    private record History(Game game, BuildingPlan building, Vec3i generator, Vec3i lamp, Vec3i spare, Vec3i street) { }

    /**
     * Two litres drawn from the neighbour's taps; a running generator with one and a half cans at
     * condition 70 and a lamp it lights on the first floor; a second generator's block standing in
     * the building and a lamp's out in the street, neither with a record of its own.
     */
    private static History history() {
        Game g = UtilityWorldArena.session(GameMode.SURVIVAL);
        BuildingPlan b = RefugeWorldArena.neighbour(g);
        BuildingPlan.Fixture tap = UtilityWorldArena.basins(b).getFirst();
        g.player.inventory.clear();
        UtilityWorldArena.giveEmptySkins(g, 2);
        SurvivorWorldArena.standAt(g, tap.target());
        SurvivorWorldArena.pressF(g, tap.position());
        SurvivorWorldArena.pressF(g, tap.position());
        assertEquals(2, g.world.urban.utilities.drawn(b.id()));

        Vec3i generator = RefugeWorldArena.piece(b, 1, BlockType.CAMP_BED).target();
        Vec3i lamp = RefugeWorldArena.piece(b, 1, BlockType.CABINET).target();
        Vec3i spare = RefugeWorldArena.piece(b, 2, BlockType.CAMP_BED).target();
        Vec3i street = new Vec3i(b.parcel().x0() + 3, 41, b.parcel().z0() - 3);
        SurvivorWorldArena.standAt(g, RefugeWorldArena.piece(b, 1, BlockType.LOCKER).target());
        UrbanUtilities u = g.world.urban.utilities;
        g.world.setBlock(generator.x(), generator.y(), generator.z(), BlockType.GENERATOR, true);
        g.world.setBlock(lamp.x(), lamp.y(), lamp.z(), BlockType.ELECTRIC_LAMP, true);
        g.world.setBlock(spare.x(), spare.y(), spare.z(), BlockType.GENERATOR, true);
        assertSame(BlockType.AIR, g.world.getBlock(street.x(), street.y(), street.z()), "a street cell");
        g.world.setBlock(street.x(), street.y(), street.z(), BlockType.ELECTRIC_LAMP, true);
        Device gen = u.add(g.world, generator, BlockType.GENERATOR);
        Device light = u.add(g.world, lamp, BlockType.ELECTRIC_LAMP);
        gen.fuel = 1.5f * UrbanUtilities.CAN_SECONDS;
        gen.condition = 70f;
        gen.on = true;
        light.on = true;
        u.settle(g.world);
        assertTrue(light.lit());
        return new History(g, b, generator, lamp, spare, street);
    }

    @Test
    void whatWasDrawnAndEveryDeviceComeBackExactlyWithNothingRefilled() {
        History h = history();
        Game g = h.game();
        Path file = directory.resolve("utilities.sav");
        assertTrue(SaveSystem.save(g, file));
        for (int cycle = 0; cycle < 2; cycle++) {
            assertTrue(SaveSystem.load(g, file));
            UrbanUtilities u = g.world.urban.utilities;
            assertEquals(2, u.drawn(h.building().id()), "loading puts no water back");
            assertEquals(1, u.tappedCount());
            assertEquals(2, g.player.inventory.count(ItemType.WATERSKIN_DIRTY));
            assertEquals(2, u.deviceCount(), "the unrecorded block is no device until it is used");
            Device gen = u.device(h.generator());
            assertEquals(1.5f * UrbanUtilities.CAN_SECONDS, gen.fuel, 1e-3f, "no fuel burnt or added by a load");
            assertEquals(70f, gen.condition, 1e-4f);
            assertTrue(gen.on && gen.running());
            Device lamp = u.device(h.lamp());
            assertTrue(lamp.on && lamp.lit(), "the lamp is lit again by the generator that was running");
            g.world.ensureChunks(h.lamp().x(), h.lamp().z(), 1, 100);
            Chunk c = g.world.getChunk(Math.floorDiv(h.lamp().x(), Chunk.SX), Math.floorDiv(h.lamp().z(), Chunk.SZ));
            assertTrue(c.lights.stream().anyMatch(l -> l[0] == h.lamp().x() && l[1] == h.lamp().y() && l[2] == h.lamp().z()),
                    "its light is registered again");
        }
        // A fresh game reading the save over nothing regenerates the city and finds the same.
        Game fresh = new Game();
        assertTrue(SaveSystem.load(fresh, file));
        BuildingPlan b = fresh.world.urban.buildingById(h.building().id());
        assertEquals(UrbanUtilities.capacity(b) - 2, fresh.world.urban.utilities.left(b));
        assertEquals(1.5f * UrbanUtilities.CAN_SECONDS, fresh.world.urban.utilities.device(h.generator()).fuel, 1e-3f);
    }

    @Test
    void aGeneratorEighteenSaveWithoutItsUtilitiesFailsAndLeavesTheLiveWorldAlone() throws IOException {
        Game g = history().game();
        Path file = directory.resolve("missing.sav");
        assertTrue(SaveSystem.save(g, file));
        byte[] bytes = Files.readAllBytes(file);
        // Renaming the section makes it an unknown id, which the reader skips: the same as an absent one.
        byte[] id = UrbanUtilitiesSection.ID.getBytes(StandardCharsets.UTF_8);
        int at = indexOf(bytes, id);
        assertTrue(at > 0);
        bytes[at + id.length - 1] = 'z';
        Path broken = directory.resolve("missing-broken.sav");
        Files.write(broken, bytes);
        World before = g.world;
        assertFalse(SaveSystem.load(g, broken), "a required section is missing");
        assertSame(before, g.world, "the live world is untouched");
    }

    @Test
    void theSectionInAnEarlierWorldIsRejected() throws IOException {
        Game earlier = RefugeWorldArena.session(GameMode.SURVIVAL);
        assertFalse(UrbanUtilitiesSection.manages(earlier), "no utility rules before layout fifteen");
        Path file = directory.resolve("earlier.sav");
        assertTrue(SaveSystem.save(earlier, file));
        byte[] save = Files.readAllBytes(file);
        byte[] empty = ByteBuffer.allocate(12).putInt(1).putInt(0).putInt(0).array();
        Path appended = directory.resolve("earlier-appended.sav");
        Files.write(appended, CreativeSaveSections.append(save, UrbanUtilitiesSection.ID, empty));
        assertFalse(SaveSystem.load(earlier, appended), "generator 17 has no utilities to restore");
    }

    /** Offsets of the payload as written: a tap row, then the generator's row and the lamp's. */
    private static final int GEN = 24, LAMP = 50, KIND = 0, X = 1, Y = 5, Z = 9, ON = 13, CONDITION = 14, FUEL = 18, SPARE = 22;

    @Test
    void everyMalformedRowFailsTheWholeLoad() throws IOException {
        History h = history();
        Game g = h.game();
        BuildingPlan b = h.building();
        Path file = directory.resolve("good.sav");
        assertTrue(SaveSystem.save(g, file));
        byte[] good = UrbanUtilitiesSection.write(g);
        assertEquals(76, good.length, "a version, one tapped building and two devices");
        List<byte[]> bad = List.of(
                withInt(good, 0, 2),                                              // another version
                withInt(good, 4, 2),                                              // a count the bytes do not hold
                withInt(good, 4, UrbanUtilities.MAX_TAPPED + 1),                  // past the cap
                withLong(good, 8, UrbanIds.floor(b.id(), 0)),                     // a storey is no building
                withLong(good, 8, UrbanIds.building(b.parcel().id(), 1)),         // a building nothing planned
                withLong(good, 8, 0L),                                            // no id at all
                withInt(good, 16, 0),                                             // nothing drawn is never written
                withInt(good, 16, UrbanUtilities.capacity(b) + 1),                // more than the pipes held
                withInt(good, 16, -3),
                withInt(good, 20, 3),                                             // a device the bytes do not hold
                withInt(good, 20, UrbanUtilities.MAX_DEVICES + 1),                // past the device cap
                withByte(good, GEN + KIND, 9),                                    // no kind of device
                withByte(good, GEN + KIND, 2),                                    // a lamp where a generator stands
                withInt(good, GEN + X, h.generator().x() + 40),                   // a cell with no edit
                withInt(good, GEN + Y, -1),                                       // below the chunk
                withByte(good, GEN + ON, 2),
                withFloat(good, GEN + CONDITION, Float.NaN),
                withFloat(good, GEN + CONDITION, UrbanUtilities.SOUND + 1),
                withFloat(good, GEN + CONDITION, -1f),
                withFloat(good, GEN + FUEL, UrbanUtilities.TANK_SECONDS + 1),     // more than a tank holds
                withFloat(good, GEN + FUEL, -2f),
                withFloat(good, GEN + FUEL, 0f),                                  // running on a dry tank
                withFloat(good, GEN + CONDITION, 0f),                             // running seized
                withFloat(good, GEN + SPARE, 1f),
                withFloat(good, LAMP + CONDITION, 50f),                           // a lamp never wears
                withFloat(good, LAMP + FUEL, 1f),                                 // nor holds fuel
                moved(good, LAMP, h.spare(), 1),                                  // a second generator in the building
                moved(good, LAMP, h.street(), 2),                                     // a lamp out in the street
                Arrays.copyOf(good, good.length + 1));                            // trailing byte
        for (int i = 0; i < bad.size(); i++) {
            byte[] payload = bad.get(i);
            int index = i;
            assertThrows(IOException.class, () -> UrbanUtilitiesSection.read(payload, reloaded(file)),
                    "malformed case " + index + " must fail");
        }
        byte[] duplicateTap = ByteBuffer.allocate(good.length + 12).put(good, 0, 20)
                .put(good, 8, 12).put(good, 20, good.length - 20).array();
        ByteBuffer.wrap(duplicateTap).putInt(4, 2);
        assertThrows(IOException.class, () -> UrbanUtilitiesSection.read(duplicateTap, reloaded(file)), "a duplicate building");
        byte[] duplicateDevice = good.clone();
        System.arraycopy(good, LAMP, duplicateDevice, GEN, 26);
        assertThrows(IOException.class, () -> UrbanUtilitiesSection.read(duplicateDevice, reloaded(file)), "a duplicate cell");
        assertDoesNotThrow(() -> UrbanUtilitiesSection.read(good, reloaded(file)), "the payload as written reads");
    }

    /** The row at an offset moved to another cell, as another kind. */
    private static byte[] moved(byte[] b, int row, Vec3i cell, int kind) {
        byte[] out = b.clone();
        ByteBuffer.wrap(out).put(row + KIND, (byte) kind).putInt(row + X, cell.x()).putInt(row + Y, cell.y()).putInt(row + Z, cell.z())
                .putFloat(row + CONDITION, UrbanUtilities.SOUND).putFloat(row + FUEL, 0f).put(row + ON, (byte) 0);
        return out;
    }

    private static Game reloaded(Path file) throws IOException {
        Game copy = new Game();
        if (!SaveSystem.load(copy, file)) { throw new IOException("load failed"); }
        return copy;
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) { continue outer; }
            }
            return i;
        }
        return -1;
    }

    private static byte[] withByte(byte[] b, int at, int value) {
        byte[] out = b.clone();
        out[at] = (byte) value;
        return out;
    }

    private static byte[] withInt(byte[] b, int at, int value) {
        byte[] out = b.clone();
        ByteBuffer.wrap(out).putInt(at, value);
        return out;
    }

    private static byte[] withLong(byte[] b, int at, long value) {
        byte[] out = b.clone();
        ByteBuffer.wrap(out).putLong(at, value);
        return out;
    }

    private static byte[] withFloat(byte[] b, int at, float value) {
        byte[] out = b.clone();
        ByteBuffer.wrap(out).putFloat(at, value);
        return out;
    }
}
