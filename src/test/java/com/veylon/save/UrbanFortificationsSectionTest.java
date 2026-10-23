package com.veylon.save;

import com.veylon.Game;
import com.veylon.RefugeWorldArena;
import com.veylon.entity.GameMode;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.FixtureStates;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.UrbanFortifications;
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
 * {@code urban.fortifications} v1 (urban milestone 18): a damaged barricade's and a brace's strength
 * come back from a load exactly; a generator-17 save without the section, the section in any other
 * world and every malformed row fail the whole load and leave the live world alone.
 */
class UrbanFortificationsSectionTest {
    @TempDir Path directory;

    private record Fixture(Game game, Vec3i barricade, Vec3i door) { }

    /** A barricade 25 strength down in the neighbour's street doorway and a brace 20 down behind a room door. */
    private static Fixture history() {
        Game g = RefugeWorldArena.session(GameMode.SURVIVAL);
        BuildingPlan b = RefugeWorldArena.neighbour(g);
        BuildingPlan.Fixture street = RefugeWorldArena.fixture(b, 0, f -> f.kind() == BuildingPlan.FixtureKind.DOOR
                && f.position().z() == b.parcel().z0());
        Vec3i cell = street.position();
        g.world.setBlock(cell.x(), cell.y(), cell.z(), BlockType.BARRICADE, true);
        g.world.urban.fortifications.damage(BlockType.BARRICADE, cell, 25f);
        BuildingPlan.Fixture room = RefugeWorldArena.fixture(b, 0, f -> f.kind() == BuildingPlan.FixtureKind.DOOR
                && f.position().x() != f.target().x() && f.position().z() > b.parcel().z0());
        Vec3i door = room.position();
        for (int y = 0; y < 2; y++) {
            assertTrue(g.world.fixtures.set(door.x(), door.y() + y, door.z(), FixtureStates.LOCK_BARRED));
        }
        assertTrue(g.world.urban.fortifications.setBrace(g.world, door, UrbanFortifications.BRACE_STRENGTH - 20f));
        return new Fixture(g, cell, door);
    }

    @Test
    void aDamagedBarricadeAndABraceComeBackExactly() {
        Fixture fx = history();
        Path file = directory.resolve("fortified.sav");
        assertTrue(SaveSystem.save(fx.game(), file));
        Game g = fx.game();
        for (int cycle = 0; cycle < 2; cycle++) {
            assertTrue(SaveSystem.load(g, file));
            assertEquals(35f, g.world.urban.fortifications.health(BlockType.BARRICADE, fx.barricade()), 1e-4f);
            assertEquals(70f, g.world.urban.fortifications.brace(g.world, fx.door()), 1e-4f);
            assertEquals(1, g.world.urban.fortifications.damagedCount());
            assertEquals(1, g.world.urban.fortifications.braceCount());
        }
    }

    @Test
    void aGeneratorSeventeenSaveWithoutItsFortificationsFailsAndLeavesTheLiveWorldAlone() throws IOException {
        Fixture fx = history();
        Path file = directory.resolve("missing.sav");
        assertTrue(SaveSystem.save(fx.game(), file));
        byte[] bytes = Files.readAllBytes(file);
        // Renaming the section makes it an unknown id, which the reader skips: the same as an absent one.
        byte[] id = UrbanFortificationsSection.ID.getBytes(StandardCharsets.UTF_8);
        int at = indexOf(bytes, id);
        assertTrue(at > 0);
        bytes[at + id.length - 1] = 'z';
        Path broken = directory.resolve("missing-broken.sav");
        Files.write(broken, bytes);
        World before = fx.game().world;
        assertFalse(SaveSystem.load(fx.game(), broken), "a required section is missing");
        assertSame(before, fx.game().world, "the live world is untouched");
    }

    @Test
    void theSectionInAnEarlierWorldIsRejected() throws IOException {
        Game earlier = com.veylon.SurvivorWorldArena.session(RefugeWorldArena.SEED, GameMode.SURVIVAL,
                World.GEN_URBAN_STRONGHOLDS, new com.veylon.engine.AudioManager());
        assertFalse(UrbanFortificationsSection.manages(earlier), "no fortification rules before layout fourteen");
        Path file = directory.resolve("earlier.sav");
        assertTrue(SaveSystem.save(earlier, file));
        byte[] save = Files.readAllBytes(file);
        byte[] empty = ByteBuffer.allocate(12).putInt(1).putInt(0).putInt(0).array();
        Path appended = directory.resolve("earlier-appended.sav");
        Files.write(appended, CreativeSaveSections.append(save, UrbanFortificationsSection.ID, empty));
        assertFalse(SaveSystem.load(earlier, appended), "generator 16 has no fortifications to restore");
    }

    @Test
    void everyMalformedRowFailsTheWholeLoad() throws IOException {
        Fixture fx = history();
        Path file = directory.resolve("good.sav");
        assertTrue(SaveSystem.save(fx.game(), file));
        byte[] good = UrbanFortificationsSection.write(fx.game());
        assertEquals(44, good.length, "a version, a damaged cell and a brace");
        int full = UrbanFortifications.MAX_DAMAGED;
        List<byte[]> bad = List.of(
                withInt(good, 0, 2),                                     // another version
                withInt(good, 4, 2),                                     // a count the bytes do not hold
                withInt(good, 4, full + 1),                              // past the cap
                withInt(good, 8, fx.barricade().x() + 1),                // a cell with no barricade edit
                withInt(good, 12, -1),                                   // below the chunk
                withFloat(good, 20, UrbanFortifications.PLANK_STRENGTH), // a sound barricade is never written
                withFloat(good, 20, 0f),                                 // no strength left is no barricade
                withFloat(good, 20, Float.NaN),
                withInt(good, 24, 2),                                    // a brace the bytes do not hold
                withInt(good, 28, fx.door().x() + 50),                   // a brace behind no barred door
                withInt(good, 32, 96),                                   // above the chunk
                withFloat(good, 40, UrbanFortifications.BRACE_STRENGTH + 1),
                withFloat(good, 40, -5f),
                Arrays.copyOf(good, good.length + 1));                   // trailing byte
        for (int i = 0; i < bad.size(); i++) {
            byte[] payload = bad.get(i);
            int index = i;
            assertThrows(IOException.class, () -> UrbanFortificationsSection.read(payload, reloaded(file)),
                    "malformed case " + index + " must fail");
        }
        byte[] duplicate = ByteBuffer.allocate(good.length + 16).put(good, 0, 24)
                .put(good, 8, 16).put(good, 24, 20).array();
        ByteBuffer.wrap(duplicate).putInt(4, 2);
        assertThrows(IOException.class, () -> UrbanFortificationsSection.read(duplicate, reloaded(file)), "a duplicate cell");
        assertDoesNotThrow(() -> UrbanFortificationsSection.read(good, reloaded(file)), "the payload as written reads");
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

    private static byte[] withFloat(byte[] b, int at, float value) {
        byte[] out = b.clone();
        ByteBuffer.wrap(out).putFloat(at, value);
        return out;
    }
}
