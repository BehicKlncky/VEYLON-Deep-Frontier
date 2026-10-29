package com.veylon.save;

import com.veylon.CompoundWorldArena;
import com.veylon.Game;
import com.veylon.SurvivorWorldArena;
import com.veylon.engine.AudioManager;
import com.veylon.entity.GameMode;
import com.veylon.settlement.UrbanComms;
import com.veylon.settlement.UrbanSite;
import com.veylon.world.World;
import com.veylon.world.urban.CommsComponents;
import com.veylon.world.urban.CompoundSites;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanMissions;
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
 * {@code urban.missions} v1 (urban milestone 20): what a layout-sixteen survivor has learned toward
 * the city's communications comes back from every load exactly, and is never learned twice; a
 * generator-19 save without the section, the section in an earlier world and every malformed field
 * fail the whole load and leave the live world alone.
 */
class UrbanMissionsSectionTest {
    @TempDir Path directory;

    private record History(Game game, long camp, long stronghold, long raiders, long office) { }

    /**
     * A camp's lookout spoke of the relay, the stronghold (a friend) gave its codes, two kinds of
     * part are recovered and the relay itself was found.
     */
    private static History history() {
        Game g = SurvivorWorldArena.session(SurvivorWorldArena.SEED, GameMode.SURVIVAL, World.GEN_URBAN_MISSIONS,
                new AudioManager());
        SurvivorSites.Site campPlan = SurvivorWorldArena.nearest(g, SurvivorSites.Kind.CAMP);
        SurvivorWorldArena.standAt(g, campPlan.entry());
        SurvivorWorldArena.slowTick(g);
        UrbanSite camp = SurvivorWorldArena.site(g, campPlan);
        UrbanComms.askAboutRadio(g, camp.resident(0).live);
        CompoundSites.Compound c = CompoundWorldArena.nearest(g, SurvivorSites.Kind.STRONGHOLD);
        CompoundWorldArena.load(g, c);
        CompoundWorldArena.standFacing(g, c, CompoundWorldArena.street(c, 2), c.gate().outside());
        SurvivorWorldArena.slowTick(g);
        UrbanSite ward = CompoundWorldArena.site(g, c);
        ward.localReputation = UrbanSite.FRIEND_TRUST + 5;
        ward.settleAlignment();
        UrbanComms.askAboutRadio(g, CompoundWorldArena.live(ward).getFirst());
        UrbanComms.onTaken(g, CommsComponents.Kind.CELLS.item, 1);
        UrbanComms.onTaken(g, CommsComponents.Kind.ANTENNA.item, 1);
        SurvivorWorldArena.standAt(g, g.world.urban.relay().stand());
        UrbanComms.mediumTick(g, 0.25f);
        UrbanMissions.Comms comms = g.world.urban.missions.comms;
        assertTrue(comms.rumoured && comms.relayFound);
        assertEquals(UrbanMissions.CODES_STRONGHOLD, comms.codes);
        CompoundSites.Compound yard = CompoundWorldArena.nearest(g, SurvivorSites.Kind.RAIDERS);
        return new History(g, camp.id, ward.id, yard.site().id(), g.world.urban.relay().building());
    }

    @Test
    void whatTheSurvivorLearnedComesBackExactlyAndIsNeverLearnedTwice() {
        History h = history();
        Game g = h.game();
        Path file = directory.resolve("missions.sav");
        assertTrue(SaveSystem.save(g, file));
        for (int cycle = 0; cycle < 2; cycle++) {
            assertTrue(SaveSystem.load(g, file));
            UrbanMissions.Comms c = g.world.urban.missions.comms;
            assertTrue(c.rumoured && c.relayFound);
            assertEquals(h.camp(), c.rumourFrom);
            assertEquals(CommsComponents.Kind.CELLS.bit | CommsComponents.Kind.ANTENNA.bit, c.recovered);
            assertEquals(UrbanMissions.CODES_STRONGHOLD, c.codes);
            assertEquals(h.stronghold(), c.codesFrom);
            assertEquals("Comms / recover the relay's parts: 2 of 3", UrbanComms.hudLine(g));
        }
        Game fresh = new Game();
        assertTrue(SaveSystem.load(fresh, file));
        assertEquals(h.stronghold(), fresh.world.urban.missions.comms.codesFrom, "a fresh game reads the same");
    }

    @Test
    void aGeneratorNineteenSaveWithoutItsMissionsFailsAndLeavesTheLiveWorldAlone() throws IOException {
        Game g = history().game();
        Path file = directory.resolve("missing.sav");
        assertTrue(SaveSystem.save(g, file));
        byte[] bytes = Files.readAllBytes(file);
        // Renaming the section makes it an unknown id, which the reader skips: the same as an absent one.
        byte[] id = UrbanMissionsSection.ID.getBytes(StandardCharsets.UTF_8);
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
        Game earlier = SurvivorWorldArena.session(SurvivorWorldArena.SEED, GameMode.SURVIVAL, World.GEN_URBAN_UTILITIES,
                new AudioManager());
        assertFalse(UrbanMissionsSection.manages(earlier), "no missions before layout sixteen");
        Path file = directory.resolve("earlier.sav");
        assertTrue(SaveSystem.save(earlier, file));
        byte[] save = Files.readAllBytes(file);
        Path appended = directory.resolve("earlier-appended.sav");
        Files.write(appended, CreativeSaveSections.append(save, UrbanMissionsSection.ID, emptyPayload()));
        assertFalse(SaveSystem.load(earlier, appended), "generator 18 has no objective to restore");
    }

    /** Field offsets of the payload as written. */
    private static final int FLAGS = 4, RUMOUR = 5, RECOVERED = 13, INSTALLED = 17, CODES = 21, CODES_FROM = 25,
            BROADCAST = 33, COMPLETED = 37, MISSIONS = 45;

    @Test
    void everyMalformedFieldFailsTheWholeLoad() throws IOException {
        History h = history();
        Game g = h.game();
        Path file = directory.resolve("good.sav");
        assertTrue(SaveSystem.save(g, file));
        byte[] good = UrbanMissionsSection.write(g);
        assertEquals(49, good.length, "a version, the objective and no missions");
        List<byte[]> bad = List.of(
                withInt(good, 0, 2),                                          // another version
                withByte(good, FLAGS, 3 | 16),                                // a flag nothing knows
                withByte(good, FLAGS, 3 | 8),                                 // complete with nothing repaired or sent
                withByte(good, FLAGS, 0),                                     // a rumour's source without a rumour
                withLong(good, RUMOUR, h.office()),                           // a building no site holds
                withLong(good, RUMOUR, UrbanIds.floor(h.camp(), 0)),          // a storey is no site
                withLong(good, RUMOUR, h.raiders()),                          // raiders tell nobody anything
                withInt(good, RECOVERED, 8),                                  // a part nothing knows
                withInt(good, RECOVERED, -1),
                withInt(good, INSTALLED, 4),                                  // a relay part nothing knows
                withInt(good, INSTALLED, -1),
                withInt(withByte(good, FLAGS, 1), INSTALLED, 1),              // fitted to a relay never found
                withInt(good, CODES, 3),                                      // a source nothing knows
                withInt(good, CODES, UrbanMissions.CODES_REFUGE),             // a stronghold is no refuge
                withLong(good, CODES_FROM, h.camp()),                         // a camp keeps no codes
                withLong(good, CODES_FROM, UrbanIds.NONE),                    // codes from nobody
                withFloat(good, BROADCAST, 1f),                               // sent through a dead relay
                withFloat(repaired(good), BROADCAST, Float.NaN),
                withFloat(repaired(good), BROADCAST, -1f),
                withFloat(repaired(good), BROADCAST, UrbanMissions.SCAN_SECONDS + 1),
                withFloat(withByte(repaired(good), FLAGS, 3), BROADCAST, 5f), // sent with no radio built
                withDouble(good, COMPLETED, 12.0),                            // a completion minute unfinished
                withDouble(completed(good), COMPLETED, -1),                   // complete with no minute
                withFloat(completed(good), BROADCAST, UrbanMissions.BROADCAST_SECONDS - 1), // complete too soon
                withInt(good, MISSIONS, 1),                                   // a mission before missions exist
                Arrays.copyOf(good, good.length + 1));                        // trailing byte
        for (int i = 0; i < bad.size(); i++) {
            byte[] payload = bad.get(i);
            int index = i;
            assertThrows(IOException.class, () -> UrbanMissionsSection.read(payload, reloaded(file)),
                    "malformed case " + index + " must fail");
        }
        byte[] none = withLong(withInt(good, CODES, UrbanMissions.CODES_NONE), CODES_FROM, h.stronghold());
        assertThrows(IOException.class, () -> UrbanMissionsSection.read(none, reloaded(file)), "a source for no codes");
        assertDoesNotThrow(() -> UrbanMissionsSection.read(good, reloaded(file)), "the payload as written reads");
        assertDoesNotThrow(() -> UrbanMissionsSection.read(emptyPayload(), reloaded(file)), "nothing learned reads");
        assertDoesNotThrow(() -> UrbanMissionsSection.read(withFloat(repaired(good), BROADCAST, 20f), reloaded(file)),
                "a broadcast under way reads");
        assertDoesNotThrow(() -> UrbanMissionsSection.read(completed(good), reloaded(file)), "a finished objective reads");
    }

    /** The payload with the relay repaired and a radio built. */
    private static byte[] repaired(byte[] good) {
        return withInt(withByte(good, FLAGS, 1 | 2 | 4), INSTALLED, UrbanMissions.RELAY_REPAIRED);
    }

    /** The payload with the call gone out and answered at minute 900. */
    private static byte[] completed(byte[] good) {
        return withDouble(withFloat(withByte(repaired(good), FLAGS, 1 | 2 | 4 | 8), BROADCAST,
                UrbanMissions.BROADCAST_SECONDS), COMPLETED, 900.0);
    }

    /** A payload of a survivor who has learned nothing. */
    private static byte[] emptyPayload() {
        return ByteBuffer.allocate(49).putInt(1).put((byte) 0).putLong(UrbanIds.NONE).putInt(0).putInt(0)
                .putInt(UrbanMissions.CODES_NONE).putLong(UrbanIds.NONE).putFloat(0f).putDouble(-1).putInt(0).array();
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

    private static byte[] withDouble(byte[] b, int at, double value) {
        byte[] out = b.clone();
        ByteBuffer.wrap(out).putDouble(at, value);
        return out;
    }
}
