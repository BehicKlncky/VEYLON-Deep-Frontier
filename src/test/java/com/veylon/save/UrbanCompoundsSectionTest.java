package com.veylon.save;

import com.veylon.Game;
import com.veylon.entity.GameMode;
import com.veylon.settlement.UrbanSite;
import com.veylon.world.World;
import com.veylon.world.urban.CompoundSites;
import com.veylon.world.urban.SurvivorSites;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static com.veylon.CompoundWorldArena.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code urban.compounds} v1 (urban milestone 17): a stronghold's pass, wanted status, last sighting
 * and missing stores and a raider yard's hunt cooldown come back from a load exactly; a generator-16
 * save without the section, and a row that contradicts its compound, fail the whole load and leave
 * the live world alone.
 */
class UrbanCompoundsSectionTest {
    @TempDir Path directory;

    /** A stronghold and the raider yard both met, with memories set as play would leave them. */
    private static Game history() {
        Game g = session(SEED, GameMode.SURVIVAL);
        CompoundSites.Compound yard = nearest(g, SurvivorSites.Kind.RAIDERS);
        setClock(g, 12 * 60);
        standFacing(g, yard, street(yard, 8), yard.gate().outside());
        com.veylon.SurvivorWorldArena.slowTick(g);
        site(g, yard).huntCooldownUntil = g.time.totalMinutes + 600;
        CompoundSites.Compound c = nearest(g, SurvivorSites.Kind.STRONGHOLD);
        standFacing(g, c, street(c, 3), c.gate().outside());
        com.veylon.SurvivorWorldArena.slowTick(g);
        com.veylon.SurvivorWorldArena.run(g, 10f);
        UrbanSite s = site(g, c);
        s.passUntil = g.time.totalMinutes + 300;
        s.wantedUntil = g.time.totalMinutes + 120;
        s.lastSeenInside = g.time.totalMinutes - 15;
        s.missing = 4;
        return g;
    }

    @Test
    void whatACompoundRemembersComesBackExactly() {
        Game g = history();
        CompoundSites.Compound c = nearest(g, SurvivorSites.Kind.STRONGHOLD), yard = nearest(g, SurvivorSites.Kind.RAIDERS);
        UrbanSite s = site(g, c), r = site(g, yard);
        double pass = s.passUntil, wanted = s.wantedUntil, seen = s.lastSeenInside, cooldown = r.huntCooldownUntil;
        Path file = directory.resolve("compounds.sav");
        assertTrue(SaveSystem.save(g, file));
        for (int cycle = 0; cycle < 2; cycle++) {
            assertTrue(SaveSystem.load(g, file));
            UrbanSite a = site(g, c), b = site(g, yard);
            assertEquals(pass, a.passUntil, 1e-9);
            assertEquals(wanted, a.wantedUntil, 1e-9);
            assertEquals(seen, a.lastSeenInside, 1e-9);
            assertEquals(4, a.missing);
            assertEquals(cooldown, b.huntCooldownUntil, 1e-9);
            assertFalse(b.cleared, "a yard with living raiders is not taken");
            assertFalse(b.rewarded);
        }
    }

    @Test
    void aGeneratorSixteenSaveWithoutItsCompoundsFailsAndLeavesTheLiveWorldAlone() throws IOException {
        Game g = history();
        Path file = directory.resolve("missing.sav");
        assertTrue(SaveSystem.save(g, file));
        byte[] bytes = Files.readAllBytes(file);
        // Renaming the section makes it an unknown id, which the reader skips: the same as an absent one.
        byte[] id = UrbanCompoundsSection.ID.getBytes(StandardCharsets.UTF_8);
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
    void aCompoundRowThatContradictsItsCompoundFailsTheWholeLoad() throws IOException {
        Game g = history();
        Path file = directory.resolve("good.sav");
        assertTrue(SaveSystem.save(g, file));
        byte[] good = UrbanCompoundsSection.write(g);
        CompoundSites.Compound c = nearest(g, SurvivorSites.Kind.STRONGHOLD), yard = nearest(g, SurvivorSites.Kind.RAIDERS);
        int stronghold = rowOf(good, c.site().id()), raiders = rowOf(good, yard.site().id());
        double now = g.time.totalMinutes;
        List<byte[]> bad = List.of(
                withInt(good, 0, 2),                                    // another version
                withInt(good, 4, ByteBuffer.wrap(good).getInt(4) - 1),  // a compound missing
                withLong(good, stronghold, 1L),                         // another compound
                withByte(good, stronghold + 8, 9),                      // unknown kind
                withDouble(good, stronghold + 9, now + 3 * 24 * 60),    // a pass beyond any dusk
                withDouble(good, stronghold + 17, Double.NaN),          // wanted until never
                withDouble(good, stronghold + 25, now + 60),            // seen inside in the future
                withInt(good, stronghold + 33, -1),                     // negative missing stores
                withDouble(good, stronghold + 37, now),                 // a stronghold with a hunt
                withDouble(good, raiders + 9, now + 10),                // a raider yard handing out passes
                withInt(good, raiders + 33, 2),                         // missing raider stores
                withByte(good, raiders + 45, 1),                        // rewarded while raiders live
                withByte(good, raiders + 45, 4),                        // an unknown flag
                Arrays.copyOf(good, good.length + 1));                  // trailing byte
        for (int i = 0; i < bad.size(); i++) {
            byte[] payload = bad.get(i);
            int index = i;
            assertThrows(IOException.class, () -> UrbanCompoundsSection.read(payload, reloaded(file)),
                    "malformed case " + index + " must fail");
        }
        assertDoesNotThrow(() -> UrbanCompoundsSection.read(good, reloaded(file)), "the payload as written reads");
    }

    private static Game reloaded(Path file) throws IOException {
        Game copy = new Game();
        if (!SaveSystem.load(copy, file)) { throw new IOException("load failed"); }
        return copy;
    }

    private static int rowOf(byte[] payload, long id) {
        ByteBuffer b = ByteBuffer.wrap(payload);
        int rows = b.getInt(4);
        for (int i = 0; i < rows; i++) {
            int at = 8 + i * UrbanCompoundsSection.ROW;
            if (b.getLong(at) == id) { return at; }
        }
        throw new AssertionError("no row for " + id);
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

    private static byte[] withDouble(byte[] b, int at, double value) {
        byte[] out = b.clone();
        ByteBuffer.wrap(out).putDouble(at, value);
        return out;
    }

    private static byte[] withByte(byte[] b, int at, int value) {
        byte[] out = b.clone();
        out[at] = (byte) value;
        return out;
    }
}
