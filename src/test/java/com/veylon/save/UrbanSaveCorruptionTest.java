package com.veylon.save;

import com.veylon.Game;
import com.veylon.IntegratedCityFixture;
import com.veylon.SaveEnvelope;
import com.veylon.entity.Player;
import com.veylon.world.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Package 25: the explicit corruption policy over every city section of the integrated urban save
 * ({@link IntegratedCityFixture}). A section that is missing, repeated, of a version this world does
 * not read, cut short, followed by stray bytes or counting more than it holds fails the whole load,
 * except {@code urban.visits}, whose absence means nothing walked or entered. A failed load changes
 * nothing in the live world and never touches the last good save; a flipped byte never escapes the
 * load as an exception, and whatever a load accepts can be saved again. A section this build does
 * not know is skipped and dropped by the next save. A snapshot that cannot be written whole never
 * replaces the last save and leaves no temporary file behind.
 */
class UrbanSaveCorruptionTest {
    @TempDir Path directory;

    /** The city sections of the save and where each one's first count sits; -1 for none. */
    private static final Map<String, Integer> COUNT_AT = Map.ofEntries(
            Map.entry("world.fixtures", 4), Map.entry("urban.population", 8), Map.entry("urban.meta", -1),
            Map.entry("urban.refuges", 25), Map.entry("urban.containers", 16), Map.entry("urban.sites", 4),
            Map.entry("urban.compounds", 4), Map.entry("urban.fortifications", 4), Map.entry("urban.utilities", 4),
            Map.entry("urban.missions", 45), Map.entry("urban.discovery", 4), Map.entry("urban.visits", 4));

    private static byte[] saved;
    private static IntegratedCityFixture.Facts facts;
    private static List<String> described;

    @BeforeAll
    static void buildTheFixtureOnce(@TempDir Path dir) throws Exception {
        IntegratedCityFixture.Built built = IntegratedCityFixture.build();
        Path file = dir.resolve("integrated.sav");
        assertTrue(SaveSystem.save(built.game(), file));
        saved = Files.readAllBytes(file);
        facts = built.facts();
        described = IntegratedCityFixture.describe(built.game(), facts);
        assertEquals(new TreeSet<>(COUNT_AT.keySet()), new TreeSet<>(SaveEnvelope.parse(saved).ids().stream()
                .filter(COUNT_AT::containsKey).toList()), "the fixture carries every city section");
    }

    @Test
    void everyCitySectionMissingFailsTheWholeLoadExceptTheVisitsWhoseAbsenceMeansNothingVisited() throws Exception {
        Live live = live();
        for (String id : COUNT_AT.keySet()) {
            byte[] without = SaveEnvelope.replace(saved, id, null);
            if (id.equals("urban.visits")) {
                Game g = new Game();
                assertTrue(SaveSystem.load(g, write(without)), "a save without its visits still loads");
                assertTrue(g.world.urban.visits.districts().isEmpty() && g.world.urban.visits.buildings().isEmpty());
                assertEquals(described, IntegratedCityFixture.describe(g, facts), "and everything else is there");
                continue;
            }
            live.assertRefuses(without, "without " + id);
        }
    }

    @Test
    void aRepeatedSectionAnotherVersionACutOrAStrayByteFailsTheWholeLoad() throws Exception {
        Live live = live();
        SaveEnvelope.Parsed parsed = SaveEnvelope.parse(saved);
        for (String id : COUNT_AT.keySet()) {
            byte[] payload = parsed.section(id).payload();
            List<SaveEnvelope.Section> twice = new ArrayList<>(parsed.sections());
            twice.add(new SaveEnvelope.Section(id, payload));
            live.assertRefuses(SaveEnvelope.rebuild(saved, parsed, twice), id + " twice");
            int version = ByteBuffer.wrap(payload).getInt();
            for (int other : new int[]{version + 1, version - 1, Integer.MIN_VALUE}) {
                live.assertRefuses(SaveEnvelope.replace(saved, id, withInt(payload, 0, other)), id + " version " + other);
            }
            live.assertRefuses(SaveEnvelope.replace(saved, id, Arrays.copyOf(payload, payload.length - 1)), id + " cut short");
            live.assertRefuses(SaveEnvelope.replace(saved, id, Arrays.copyOf(payload, 3)), id + " cut to three bytes");
            live.assertRefuses(SaveEnvelope.replace(saved, id, new byte[0]), id + " empty");
            live.assertRefuses(SaveEnvelope.replace(saved, id, Arrays.copyOf(payload, payload.length + 1)), id + " and a stray byte");
        }
    }

    @Test
    void aCountBeyondWhatASectionHoldsOrMayHoldFailsTheWholeLoad() throws Exception {
        Live live = live();
        SaveEnvelope.Parsed parsed = SaveEnvelope.parse(saved);
        for (Map.Entry<String, Integer> e : COUNT_AT.entrySet()) {
            if (e.getValue() < 0) { continue; }
            byte[] payload = parsed.section(e.getKey()).payload();
            int count = ByteBuffer.wrap(payload).getInt(e.getValue());
            for (int bad : new int[]{-1, count + 1, 1 << 20, Integer.MAX_VALUE}) {
                live.assertRefuses(SaveEnvelope.replace(saved, e.getKey(), withInt(payload, e.getValue(), bad)),
                        e.getKey() + " count " + bad + " (holds " + count + ")");
            }
        }
    }

    /**
     * Flipped bytes across every city section, at fixed offsets: the load never throws, a refused load
     * leaves the live world alone, and anything a load accepts is a state the game can save and load again.
     */
    @Test
    void aFlippedByteAnywhereInACitySectionNeverEscapesTheLoadAndNeverStrandsTheSurvivor() throws Exception {
        Live live = live();
        SaveEnvelope.Parsed parsed = SaveEnvelope.parse(saved);
        int refused = 0, accepted = 0;
        for (String id : COUNT_AT.keySet()) {
            byte[] payload = parsed.section(id).payload();
            for (int offset : offsets(payload.length, COUNT_AT.get(id))) {
                byte[] flipped = payload.clone();
                flipped[offset] ^= (byte) (offset % 2 == 0 ? 0xFF : 0x01);
                byte[] bytes = SaveEnvelope.replace(saved, id, flipped);
                Path file = write(bytes);
                Game target = live.game;
                boolean loaded = assertDoesNotThrow(() -> SaveSystem.load(target, file), id + " byte " + offset);
                if (!loaded) {
                    live.assertUntouched(id + " byte " + offset);
                    refused++;
                    continue;
                }
                accepted++;
                Path again = directory.resolve("again.sav");
                assertTrue(SaveSystem.save(target, again), "what " + id + " byte " + offset + " loaded can be saved");
                assertTrue(SaveSystem.load(new Game(), again), "and loaded again");
                live = live();
            }
        }
        System.out.println("[corruption] flipped bytes refused " + refused + ", accepted as other valid state " + accepted);
        assertTrue(refused > accepted, "most damage is caught: " + refused + " refused, " + accepted + " accepted");
    }

    @Test
    void aSectionThisBuildDoesNotKnowIsSkippedAndDroppedByTheNextSave() throws Exception {
        SaveEnvelope.Parsed parsed = SaveEnvelope.parse(saved);
        List<SaveEnvelope.Section> newer = new ArrayList<>(parsed.sections());
        newer.add(2, new SaveEnvelope.Section("urban.future-ledger", new byte[]{0, 0, 0, 9, 1, 2, 3}));
        Game g = new Game();
        assertTrue(SaveSystem.load(g, write(SaveEnvelope.rebuild(saved, parsed, newer))), "an unknown id is skipped");
        assertEquals(described, IntegratedCityFixture.describe(g, facts));
        Path again = directory.resolve("again.sav");
        assertTrue(SaveSystem.save(g, again));
        SaveEnvelope.Parsed resaved = SaveEnvelope.parse(Files.readAllBytes(again));
        assertEquals(parsed.ids(), resaved.ids(), "its state is lost on the next save, as documented");
        for (String id : parsed.ids()) {
            assertArrayEquals(parsed.section(id).payload(), resaved.section(id).payload(), id);
        }
    }

    /**
     * A snapshot the writer refuses halfway: the clock set back before the refuges were claimed and the
     * survivors met, which every one of those sections' own checks rejects. The save fails, the last
     * complete save is byte for byte what it was and no temporary file is left beside it.
     */
    @Test
    void aSnapshotRefusedHalfwayNeverReplacesTheLastSaveOrLeavesATemporaryFile() throws Exception {
        Path folder = Files.createDirectory(directory.resolve("slot"));
        Path slot = folder.resolve("veylon.sav");
        Files.write(slot, saved);
        Game g = new Game();
        assertTrue(SaveSystem.load(g, slot));
        g.time.totalMinutes = 1;
        assertFalse(SaveSystem.save(g, slot), "a claim cannot have been made in the future");
        assertArrayEquals(saved, Files.readAllBytes(slot), "the last complete save is untouched");
        try (Stream<Path> files = Files.list(folder)) {
            assertEquals(List.of(slot), files.toList(), "no temporary file is left beside it");
        }
    }

    // ------------------------------------------------------------------

    /** A live world loaded from the good save, the good save on disk, and the checks a refusal must pass. */
    private final class Live {
        Game game;
        World world;
        Player player;
        Path good;

        void assertRefuses(byte[] bytes, String what) throws Exception {
            Path file = write(bytes);
            boolean loaded = assertDoesNotThrow(() -> SaveSystem.load(game, file), what + " must not throw");
            assertFalse(loaded, what + " must fail the whole load");
            assertUntouched(what);
        }

        void assertUntouched(String what) throws Exception {
            assertSame(world, game.world, what + ": the live world is not replaced");
            assertSame(player, game.player, what + ": nor its player");
            assertArrayEquals(saved, Files.readAllBytes(good), what + ": the last good save is untouched");
        }
    }

    private Live live() throws Exception {
        Live l = new Live();
        l.good = write(saved);
        l.game = new Game();
        assertTrue(SaveSystem.load(l.game, l.good));
        l.world = l.game.world;
        l.player = l.game.player;
        return l;
    }

    private Path write(byte[] bytes) throws IOException {
        Path file = Files.createTempFile(directory, "corrupt-", ".sav");
        Files.write(file, bytes);
        return file;
    }

    private static byte[] withInt(byte[] payload, int offset, int value) throws IOException {
        byte[] out = payload.clone();
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        new DataOutputStream(b).writeInt(value);
        System.arraycopy(b.toByteArray(), 0, out, offset, 4);
        return out;
    }

    /**
     * Fixed points of a section: both ends of its version, both ends of its first count, four spread
     * through its rows and its last byte. Every load regenerates the city's spawn, so a byte-by-byte
     * sweep would cost minutes; the structured cases above carry the specific fields.
     */
    private static List<Integer> offsets(int length, int count) {
        TreeSet<Integer> out = new TreeSet<>(List.of(0, 3, length - 1));
        if (count >= 0) {
            out.add(count);
            out.add(count + 3);
        }
        for (int i = 1; i <= 4; i++) { out.add((int) ((long) (length - 1) * i / 5)); }
        out.removeIf(i -> i < 0 || i >= length);
        return new ArrayList<>(out);
    }
}
