package com.veylon.save;

import com.veylon.Game;
import com.veylon.RefugeWorldArena;
import com.veylon.SurvivorWorldArena;
import com.veylon.UtilityWorldArena;
import com.veylon.entity.GameMode;
import com.veylon.item.ItemType;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanUtilities;
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
 * {@code urban.utilities} v1 (urban milestone 19): the water drawn from a building's taps comes
 * back from every load exactly, so emptied pipes stay empty; a generator-18 save without the
 * section, the section in any other world and every malformed row fail the whole load and leave
 * the live world alone.
 */
class UrbanUtilitiesSectionTest {
    @TempDir Path directory;

    /** Two litres drawn from the neighbour's taps. */
    private static Game history() {
        Game g = UtilityWorldArena.session(GameMode.SURVIVAL);
        BuildingPlan b = RefugeWorldArena.neighbour(g);
        BuildingPlan.Fixture tap = UtilityWorldArena.basins(b).getFirst();
        g.player.inventory.clear();
        UtilityWorldArena.giveEmptySkins(g, 2);
        SurvivorWorldArena.standAt(g, tap.target());
        SurvivorWorldArena.pressF(g, tap.position());
        SurvivorWorldArena.pressF(g, tap.position());
        assertEquals(2, g.world.urban.utilities.drawn(b.id()));
        return g;
    }

    @Test
    void whatWasDrawnComesBackExactlyAndThePipesNeverRefill() {
        Game g = history();
        long building = RefugeWorldArena.neighbour(g).id();
        Path file = directory.resolve("tapped.sav");
        assertTrue(SaveSystem.save(g, file));
        for (int cycle = 0; cycle < 2; cycle++) {
            assertTrue(SaveSystem.load(g, file));
            assertEquals(2, g.world.urban.utilities.drawn(building), "loading puts no water back");
            assertEquals(1, g.world.urban.utilities.tappedCount());
            assertEquals(2, g.player.inventory.count(ItemType.WATERSKIN_DIRTY));
        }
        // A fresh game reading the save over nothing regenerates the city and still finds them drawn.
        Game fresh = new Game();
        assertTrue(SaveSystem.load(fresh, file));
        BuildingPlan b = fresh.world.urban.buildingById(building);
        assertEquals(UrbanUtilities.capacity(b) - 2, fresh.world.urban.utilities.left(b));
    }

    @Test
    void aGeneratorEighteenSaveWithoutItsUtilitiesFailsAndLeavesTheLiveWorldAlone() throws IOException {
        Game g = history();
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
        assertFalse(SaveSystem.load(earlier, appended), "generator 17 has no taps to restore");
    }

    @Test
    void everyMalformedRowFailsTheWholeLoad() throws IOException {
        Game g = history();
        BuildingPlan b = RefugeWorldArena.neighbour(g);
        Path file = directory.resolve("good.sav");
        assertTrue(SaveSystem.save(g, file));
        byte[] good = UrbanUtilitiesSection.write(g);
        assertEquals(24, good.length, "a version, one tapped building and no devices");
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
                withInt(good, 20, 1),                                             // version 1 keeps no devices
                Arrays.copyOf(good, good.length + 1));                            // trailing byte
        for (int i = 0; i < bad.size(); i++) {
            byte[] payload = bad.get(i);
            int index = i;
            assertThrows(IOException.class, () -> UrbanUtilitiesSection.read(payload, reloaded(file)),
                    "malformed case " + index + " must fail");
        }
        byte[] duplicate = ByteBuffer.allocate(good.length + 12).put(good, 0, 20)
                .put(good, 8, 12).put(good, 20, 4).array();
        ByteBuffer.wrap(duplicate).putInt(4, 2);
        assertThrows(IOException.class, () -> UrbanUtilitiesSection.read(duplicate, reloaded(file)), "a duplicate building");
        assertDoesNotThrow(() -> UrbanUtilitiesSection.read(good, reloaded(file)), "the payload as written reads");
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
}
