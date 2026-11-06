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
     * A camp's lookout spoke of the relay and gave three missions, the stronghold (a friend) gave its
     * codes, two kinds of part are recovered and the relay itself was found, which readied the mast's
     * mission.
     */
    private static History history() {
        Game g = SurvivorWorldArena.session(SurvivorWorldArena.SEED, GameMode.SURVIVAL, World.GEN_URBAN_MISSIONS,
                new AudioManager());
        SurvivorSites.Site campPlan = SurvivorWorldArena.nearest(g, SurvivorSites.Kind.CAMP);
        SurvivorWorldArena.standAt(g, campPlan.entry());
        SurvivorWorldArena.slowTick(g);
        UrbanSite camp = SurvivorWorldArena.site(g, campPlan);
        UrbanComms.askAboutRadio(g, camp.resident(0).live);
        // Three of the camp's missions: the clinic's medicine, the hidden people and the mast.
        for (int i = 0; i < 3; i++) { com.veylon.settlement.UrbanMissionBoard.askForWork(g, camp.resident(0).live); }
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
        com.veylon.settlement.UrbanMissionBoard.mediumTick(g, 0.25f);
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
            var missions = g.world.urban.missions.missions();
            assertEquals(3, missions.size(), "every mission record");
            assertEquals(UrbanMissions.Kind.CLINIC_SUPPLIES, missions.get(0).kind);
            assertEquals(UrbanMissions.Status.ACTIVE, missions.get(0).status);
            assertEquals(UrbanMissions.Kind.HIDDEN_AID, missions.get(1).kind);
            assertEquals(UrbanMissions.Kind.LANDMARK, missions.get(2).kind);
            assertEquals(UrbanMissions.Status.READY, missions.get(2).status, "the mast's mission is ready to report");
            assertEquals(1f, missions.get(2).progress);
            for (UrbanMissions.Mission m : missions) { assertEquals(h.camp(), m.giver); }
            assertEquals(3, g.world.urban.missions.activeCount());
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
    /** Mission rows, 32 bytes each from offset 49, and the offsets of their fields. */
    private static final int ROW0 = 49, ROW1 = 81, ROW2 = 113, KIND = 0, TARGET = 1, GIVER = 9, STATUS = 17, PROGRESS = 18,
            FAILURE = 22, ACCEPTED = 23, REWARDED = 31;

    @Test
    void everyMalformedFieldFailsTheWholeLoad() throws IOException {
        History h = history();
        Game g = h.game();
        Path file = directory.resolve("good.sav");
        assertTrue(SaveSystem.save(g, file));
        byte[] good = UrbanMissionsSection.write(g);
        assertEquals(49 + 3 * 32, good.length, "a version, the objective and three missions");
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
                withInt(good, MISSIONS, 1),                                   // fewer missions than the bytes hold
                withInt(good, MISSIONS, 4),                                   // more missions than the bytes hold
                withInt(good, MISSIONS, UrbanMissions.MAX_RECORDS + 1),       // past the cap
                withByte(good, ROW0 + KIND, 9),                               // a kind nothing knows
                withByte(good, ROW0 + KIND, 5),                               // a camp offering the Wardens' module
                withLong(good, ROW0 + TARGET, h.office()),                    // an office is no clinic
                withLong(good, ROW1 + TARGET, h.camp()),                      // a camp is no hidden refuge
                withLong(good, ROW2 + TARGET, h.camp()),                      // the mast is the relay's building
                withLong(good, ROW0 + GIVER, h.stronghold()),                 // a stronghold asks no clinic's medicine
                withLong(good, ROW0 + GIVER, h.raiders()),                    // raiders give no missions
                withByte(good, ROW0 + STATUS, 9),                             // a status nothing knows
                withByte(good, ROW0 + STATUS, 2),                             // ready with no evidence
                withByte(good, ROW2 + STATUS, 1),                             // in hand with all its evidence
                withByte(good, ROW2 + STATUS, 3),                             // complete with no reward given
                withFloat(good, ROW0 + PROGRESS, Float.NaN),
                withFloat(good, ROW1 + PROGRESS, 4f),                         // more evidence than it asks
                withFloat(good, ROW1 + PROGRESS, -1f),
                withByte(good, ROW0 + FAILURE, 6),                            // a failure while in hand
                withByte(good, ROW0 + FAILURE, 9),                            // a failure nothing knows
                withByte(good, ROW2 + REWARDED, 1),                           // rewarded before it was reported
                withByte(good, ROW2 + REWARDED, 2),
                withDouble(good, ROW0 + ACCEPTED, -1),
                withDouble(good, ROW0 + ACCEPTED, Double.NaN),
                duplicate(good, ROW0, ROW1),                                  // the clinic asked for twice
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
        byte[] reported = withByte(withByte(good, ROW2 + STATUS, 3), ROW2 + REWARDED, 1);
        assertDoesNotThrow(() -> UrbanMissionsSection.read(reported, reloaded(file)), "a reported mission reads");
        byte[] failed = withByte(withByte(good, ROW0 + STATUS, 4), ROW0 + FAILURE, 3);
        assertDoesNotThrow(() -> UrbanMissionsSection.read(failed, reloaded(file)), "a failed mission reads");
    }

    /** One 32-byte mission row copied over another. */
    private static byte[] duplicate(byte[] good, int from, int to) {
        byte[] out = good.clone();
        System.arraycopy(good, from, out, to, 32);
        return out;
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
