package com.veylon.save;

import com.veylon.Game;
import com.veylon.entity.Carcass;
import com.veylon.entity.Creature;
import com.veylon.item.EquipSlot;
import com.veylon.item.ItemType;
import com.veylon.simulation.EventSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A save this build cannot read must fail, not crash.
 *
 * <p>{@link SaveSystem} states that contract in its class documentation, and
 * the v3 extension sections have always honoured it. The core body did not: a
 * damaged file reached {@code values()[ordinal]} and {@code new ArrayList<>(n)}
 * with numbers the file chose, throwing {@link ArrayIndexOutOfBoundsException},
 * {@link IllegalArgumentException} or {@link OutOfMemoryError}. None of those
 * is an {@link IOException}, so none was caught, and all of them escaped
 * {@code Game.run} and killed the process.
 *
 * <p>That is worse than it sounds. {@code load} calls {@code newWorld} before
 * it reads any of this, so the crash lands after the player's live world has
 * already been released — a half-written save from a power loss during F5 took
 * the running game down with it.
 *
 * <p>Each test corrupts one field of a genuine save and asserts the same two
 * things: the call returns {@code false}, and nothing is thrown. The fields are
 * located by {@link Layout}, which replays the writer's section order rather
 * than hard-coding byte offsets that would silently drift onto a different
 * field the first time a section is added above them.
 */
class CorruptSaveResilienceTest {

    /**
     * Points sampled by the two sweeps at the end. Every load regenerates a
     * world from its seed, so an exhaustive byte-by-byte sweep would cost
     * minutes in the portable suite. The targeted tests above them carry the
     * specific fields that used to throw; the sweeps are breadth over the
     * readers between those fields.
     */
    private static final int SWEEP_POINTS = 24;

    @Test
    void anOutOfRangeWeatherOrdinalFailsInsteadOfThrowing(@TempDir Path dir) throws IOException {
        Path save = fixture(dir, 40_101L);

        assertLoadFailsCleanly(corrupt(dir, save, Layout.of(save).weatherOrdinal, 9999),
                "an out-of-range weather ordinal");
    }

    @Test
    void anOutOfRangeCreatureOrdinalFailsInsteadOfThrowing(@TempDir Path dir) throws IOException {
        Path save = fixture(dir, 40_102L);

        assertLoadFailsCleanly(corrupt(dir, save, Layout.of(save).creatureOrdinal, 512),
                "an out-of-range creature ordinal");
    }

    @Test
    void anOutOfRangeCarcassOrdinalFailsInsteadOfThrowing(@TempDir Path dir) throws IOException {
        Path save = fixture(dir, 40_103L);

        assertLoadFailsCleanly(corrupt(dir, save, Layout.of(save).carcassOrdinal, -3),
                "an out-of-range carcass ordinal");
    }

    @Test
    void anOutOfRangeEventOrdinalFailsInsteadOfThrowing(@TempDir Path dir) throws IOException {
        Path save = fixture(dir, 40_104L);

        assertLoadFailsCleanly(corrupt(dir, save, Layout.of(save).eventOrdinal, 77),
                "an out-of-range event ordinal");
    }

    @Test
    void anOutOfRangeHotbarSlotFailsInsteadOfCrashingTheFirstFrame(@TempDir Path dir)
            throws IOException {
        // This one never threw inside load; it threw on the first frame after
        // it, when the HUD read the selected slot. Rejecting it at the boundary
        // keeps the failure attributable to the file that caused it.
        Path save = fixture(dir, 40_105L);

        assertLoadFailsCleanly(corrupt(dir, save, Layout.of(save).hotbarSel, 4242),
                "an out-of-range hotbar slot");
    }

    @Test
    void aNonFinitePlayerScalarFailsInsteadOfLoadingAnUnplayableGame(@TempDir Path dir)
            throws IOException {
        // NaN health never threw either. It loaded, and then no comparison
        // against it was ever true again: the player could not die, heal or eat.
        Path save = fixture(dir, 40_106L);

        assertLoadFailsCleanly(
                corrupt(dir, save, Layout.of(save).playerHealth, Float.floatToIntBits(Float.NaN)),
                "NaN player health");
    }

    @Test
    void anAbsurdEntryCountFailsWithoutAllocatingForIt(@TempDir Path dir) throws IOException {
        Path save = fixture(dir, 40_107L);
        int offset = Layout.of(save).changedBlockCount;

        // Integer.MAX_VALUE entries was a two-billion-element array reservation
        // before the first entry byte was read.
        assertLoadFailsCleanly(corrupt(dir, save, offset, Integer.MAX_VALUE),
                "an implausible entry count");
        assertLoadFailsCleanly(corrupt(dir, save, offset, -5),
                "a negative entry count");
    }

    @Test
    void aTruncatedSaveFailsInsteadOfThrowing(@TempDir Path dir) throws IOException {
        Path save = fixture(dir, 40_108L);
        byte[] whole = Files.readAllBytes(save);

        // A power loss can cut the write anywhere, and each prefix stops a
        // different reader mid-value.
        for (int i = 1; i <= SWEEP_POINTS; i++) {
            int length = Math.max(1, whole.length * i / (SWEEP_POINTS + 1));
            Path truncated = dir.resolve("truncated-" + length + ".dat");
            Files.write(truncated, Arrays.copyOf(whole, length));

            Game target = new Game();
            boolean loaded = assertDoesNotThrow(() -> SaveSystem.load(target, truncated),
                    "a save truncated to " + length + " bytes must fail, not throw");
            assertFalse(loaded, "a truncated save must not report success at " + length);
        }
    }

    @Test
    void garbageAcrossTheWholeFileFailsInsteadOfThrowing(@TempDir Path dir) throws IOException {
        Path save = fixture(dir, 40_109L);
        byte[] whole = Files.readAllBytes(save);

        // Breadth over the readers between the named fields, which is where the
        // next unguarded read would appear.
        for (int i = 1; i <= SWEEP_POINTS; i++) {
            int offset = 8 + (whole.length - 12) * i / (SWEEP_POINTS + 1);
            Path mangled = corrupt(dir, save, offset, 0x7F00_00FF);
            Game target = new Game();
            assertDoesNotThrow(() -> SaveSystem.load(target, mangled),
                    "corrupting the int at offset " + offset + " must not throw");
        }
    }

    @Test
    void anIntactSaveStillLoads(@TempDir Path dir) throws IOException {
        // A guard that rejected everything would pass every test above.
        Path save = fixture(dir, 40_110L);

        Game target = new Game();
        assertTrue(SaveSystem.load(target, save), "the unmodified fixture must still load");
        assertTrue(target.player.inventory.count(ItemType.IRON_INGOT) >= 3,
                "loading must restore real state, not just return true");
        assertFalse(target.entities.creatures.isEmpty(), "the creature must come back");
        assertFalse(target.entities.carcasses.isEmpty(), "the carcass must come back");
        assertFalse(target.events.active.isEmpty(), "the active event must come back");
    }

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    /**
     * A save written by the real writer holding one of everything the ordinal
     * tests need. The variable-length sections that precede them are emptied so
     * {@link Layout} stays a short, checkable walk instead of a second
     * implementation of the reader.
     */
    private static Path fixture(Path dir, long seed) {
        Game game = new Game();
        game.newWorld(seed, true);
        game.player.inventory.add(ItemType.IRON_INGOT, 3);

        game.world.changedBlocks.clear();
        game.world.campfireFuel.clear();
        game.world.crateContents.clear();
        game.world.rackBatches.clear();
        game.world.collectorWater.clear();
        game.world.discoveredPois.clear();
        game.entities.npcs.clear();
        game.entities.creatures.clear();
        game.entities.carcasses.clear();
        game.events.active.clear();
        game.faction.quest = null;
        game.world.beaconPos = null;

        game.entities.spawnCreature(game.world, Creature.CreatureType.DEER,
                game.player.pos.x + 4, game.player.pos.y, game.player.pos.z + 4);
        game.entities.carcasses.add(new Carcass(Creature.CreatureType.HARE,
                game.player.pos.x + 2, game.player.pos.y, game.player.pos.z + 2));
        game.events.active.add(new EventSystem.ActiveEvent(
                EventSystem.EventType.values()[0], 120f, 0.5f));

        Path save = dir.resolve("veylon-" + seed + ".dat");
        assertTrue(SaveSystem.save(game, save), "precondition: the fixture saves");
        return save;
    }

    /** Copies {@code source} with the big-endian int at {@code offset} replaced. */
    private static Path corrupt(Path dir, Path source, int offset, int value) throws IOException {
        byte[] bytes = Files.readAllBytes(source);
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
        Path corrupted = Files.createTempFile(dir, "corrupt-", ".dat");
        Files.write(corrupted, bytes);
        return corrupted;
    }

    private static void assertLoadFailsCleanly(Path save, String what) {
        Game target = new Game();
        boolean loaded = assertDoesNotThrow(() -> SaveSystem.load(target, save),
                what + " must fail the load, not throw out of it");
        assertFalse(loaded, what + " must not report a successful load");
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    /**
     * Byte offsets of the fields these tests corrupt, found by replaying the
     * writer's section order over a real file.
     *
     * <p>The walk asserts the counts it reads are the fixture's, so if the
     * format gains a section the walk will land on nonsense and say so, rather
     * than quietly corrupting an unrelated field and still passing.
     */
    private record Layout(int weatherOrdinal, int playerHealth, int hotbarSel,
                          int changedBlockCount, int creatureOrdinal,
                          int carcassOrdinal, int eventOrdinal) {

        static Layout of(Path save) throws IOException {
            try (DataInputStream in = new DataInputStream(
                    new BufferedInputStream(Files.newInputStream(save)))) {
                Cursor c = new Cursor(in);
                c.skip(4 + 4 + 8 + 4 + 8);              // magic, version, seed, generator, clock
                int weatherOrdinal = c.position();
                c.skip(4 + 4 + 4 + 4);                  // weather current, next, blend, timer
                c.skip(5 * 4);                          // player position, camera yaw/pitch
                int playerHealth = c.position();
                c.skip(10 * 4 + 1);                     // needs, then woundClean
                int hotbarSel = c.position();
                c.skip(4);
                c.expectCount(0, "afflictions");
                c.expectCount(0, "blueprints");
                c.skip(c.expectPositiveCount("inventory slots") * ItemStackBytes.V3);
                c.skip(EquipSlot.values().length * ItemStackBytes.V3);

                int changedBlockCount = c.position();
                c.expectCount(0, "changed blocks");
                c.expectCount(0, "campfire fuel");
                c.expectCount(0, "crates");
                c.expectCount(0, "drying racks");
                c.expectCount(0, "rain collectors");
                c.expectCount(0, "discovered POIs");
                c.skip(1 + 4);                          // beacon present flag, stage
                c.skip(4 + 4 + 4 + 4 + 1 + 4 + 1);      // faction core
                c.skip(1);                              // no active quest
                c.expectCount(0, "NPCs");

                c.expectCount(1, "creatures");
                int creatureOrdinal = c.position();
                c.skip(4 + 6 * 4);                      // type, position, health, hunger, bleed
                c.expectCount(1, "carcasses");
                int carcassOrdinal = c.position();
                c.skip(4 + 3 * 4 + 4 + 4 + 4);          // type, position, meat, hide, decay
                c.expectCount(1, "active events");
                int eventOrdinal = c.position();

                return new Layout(weatherOrdinal, playerHealth, hotbarSel, changedBlockCount,
                        creatureOrdinal, carcassOrdinal, eventOrdinal);
            }
        }
    }

    /** Serialized width of one {@code ItemStack}, per {@code SaveSystem.writeStack}. */
    private static final class ItemStackBytes {
        static final int V3 = 4 + 4 + 4 + 4 + 4;

        private ItemStackBytes() {
        }
    }

    /** Position-tracking reader; only what the layout walk needs. */
    private static final class Cursor {
        private final DataInputStream in;
        private int position;

        Cursor(DataInputStream in) {
            this.in = in;
        }

        int position() {
            return position;
        }

        void skip(int bytes) throws IOException {
            if (in.readNBytes(bytes).length != bytes) {
                throw new IOException("save ended while skipping " + bytes + " bytes");
            }
            position += bytes;
        }

        private int readInt() throws IOException {
            position += 4;
            return in.readInt();
        }

        void expectCount(int expected, String label) throws IOException {
            int actual = readInt();
            if (actual != expected) {
                throw new IOException("layout walk desynchronized: expected " + expected
                        + " " + label + " but read " + actual
                        + " — the save format changed and this walk must be updated");
            }
        }

        int expectPositiveCount(String label) throws IOException {
            int actual = readInt();
            if (actual <= 0 || actual > 1024) {
                throw new IOException("layout walk desynchronized reading " + label
                        + ": " + actual);
            }
            return actual;
        }
    }
}
