package com.veylon.save;

import com.veylon.Game;
import com.veylon.entity.Player;
import com.veylon.item.ItemType;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.9.1 D-01: a build opens only saves of its own format and epoch. Every other one, VEYLON 0.9.0's
 * and every earlier release's included, is refused from its header before the live world is
 * touched, with a reason the player understands, and the file is left exactly as it was. The
 * headers are built in code from a save this build wrote ({@link ForeignSaves}).
 */
class SaveEpochRefusalTest {

    private static final long LIVE_SEED = 90_100L;
    private static final long FOREIGN_SEED = 90_200L;

    @TempDir
    Path directory;

    /** A save of another world, written by this build: the body every foreign header is put on. */
    private byte[] written;

    @BeforeEach
    void writeASaveOfAnotherWorld() throws IOException {
        Game other = new Game();
        other.newWorld(FOREIGN_SEED, true, World.GEN_CAVE_IDENTITIES);
        Path file = directory.resolve("written.sav");
        assertTrue(SaveSystem.save(other, file), "precondition: this build saves");
        written = Files.readAllBytes(file);
        Files.delete(file);
    }

    @Test
    void thisBuildsSaveCarriesItsFormatAndEpochAndOpens() throws IOException {
        try (DataInputStream header = new DataInputStream(new ByteArrayInputStream(written))) {
            assertEquals(SaveHeader.MAGIC, header.readInt(), "VEYL");
            assertEquals(4, header.readInt(), "format 4 is the first with an epoch");
            assertEquals(SaveHeader.EPOCH, header.readInt(), "the build's epoch");
            assertEquals(FOREIGN_SEED, header.readLong());
            assertEquals(World.GEN_CAVE_IDENTITIES, header.readInt());
        }
        assertEquals(1, SaveHeader.EPOCH, "0.9.1 starts at epoch 1");
        Live live = new Live();
        LoadResult result = SaveSystem.open(live.game, file("own.sav", written));
        assertEquals(LoadResult.Outcome.LOADED, result.outcome());
        assertEquals(FOREIGN_SEED, live.game.world.seed, "a save of this epoch replaces the live world");
    }

    @Test
    void aSaveInVeylonZeroNineZerosFormatIsRefusedAsOlderAndLeftAlone() throws IOException {
        byte[] older = ForeignSaves.asFormatThree(written);
        assertRefused(older, LoadResult.Outcome.OLDER_VERSION);
        assertEquals("This save was written by an older version of VEYLON. Start a new world.",
                SaveSystem.open(new Game(), file("message.sav", older)).message());
        // Magic and format alone decide it: even a file cut right after them is older, not damaged.
        assertRefused(ForeignSaves.cut(older, 8), LoadResult.Outcome.OLDER_VERSION);
    }

    @Test
    void theFirstReleasesFormatTwoIsRefusedAsOlder() throws IOException {
        assertRefused(ForeignSaves.asFormatTwo(written), LoadResult.Outcome.OLDER_VERSION);
        assertRefused(ForeignSaves.withFormat(written, 1), LoadResult.Outcome.OLDER_VERSION);
    }

    @Test
    void aLaterFormatIsRefusedAsNewer() throws IOException {
        assertRefused(ForeignSaves.withFormat(written, SaveHeader.FORMAT + 1), LoadResult.Outcome.NEWER_VERSION);
        assertRefused(ForeignSaves.withFormat(written, Integer.MAX_VALUE), LoadResult.Outcome.NEWER_VERSION);
        assertEquals("This save was written by a newer version of VEYLON. Update the game.",
                SaveSystem.open(new Game(), file("newer.sav",
                        ForeignSaves.withFormat(written, SaveHeader.FORMAT + 1))).message());
    }

    @Test
    void anotherEpochIsRefused() throws IOException {
        assertRefused(ForeignSaves.withEpoch(written, SaveHeader.EPOCH + 1), LoadResult.Outcome.NEWER_VERSION);
        for (int older = 1; older < SaveHeader.EPOCH; older++) {
            assertRefused(ForeignSaves.withEpoch(written, older), LoadResult.Outcome.OLDER_VERSION);
        }
        // Epochs start at 1: zero or less is no epoch any build wrote.
        assertRefused(ForeignSaves.withEpoch(written, 0), LoadResult.Outcome.DAMAGED);
        assertRefused(ForeignSaves.withEpoch(written, -1), LoadResult.Outcome.DAMAGED);
        assertRefused(ForeignSaves.withFormat(written, 0), LoadResult.Outcome.DAMAGED);
    }

    @Test
    void aHeaderCutShortIsDamagedRatherThanAnotherVersion() throws IOException {
        for (int length : new int[]{0, 2, 4, 6, 8, 10, 12, 16, SaveHeader.LENGTH - 1}) {
            assertRefused(ForeignSaves.cut(written, length), LoadResult.Outcome.DAMAGED);
        }
    }

    @Test
    void aFileThatIsNoSaveIsSaidToBeNone() throws IOException {
        assertRefused("these are not the bytes of a save".getBytes(StandardCharsets.US_ASCII),
                LoadResult.Outcome.NOT_A_SAVE);
        assertEquals("This file is not a VEYLON save.", SaveSystem.open(new Game(),
                file("text.sav", "plain text".getBytes(StandardCharsets.US_ASCII))).message());
    }

    @Test
    void aMissingOrUnreadableSaveIsSaidToBeSo() throws IOException {
        Live live = new Live();
        assertEquals(LoadResult.Outcome.NO_SAVE, SaveSystem.open(live.game, directory.resolve("absent.sav")).outcome());
        Path folder = Files.createDirectory(directory.resolve("folder.sav"));
        assertEquals(LoadResult.Outcome.UNREADABLE, SaveSystem.open(live.game, folder).outcome());
        live.assertIntact();
    }

    /**
     * Refused from the title, where nothing is built, and over a populated live world, which the
     * refusal leaves exactly as it was; either way the file is not touched.
     */
    private void assertRefused(byte[] bytes, LoadResult.Outcome expected) throws IOException {
        Path save = file("refused.sav", bytes);
        FileTime stamp = Files.getLastModifiedTime(save);

        Game title = new Game();
        LoadResult fromTitle = SaveSystem.open(title, save);
        assertEquals(expected, fromTitle.outcome(), fromTitle.reason());
        assertNull(title.world, "a refused load builds no world");
        assertNull(title.player);

        Live live = new Live();
        LoadResult overLive = SaveSystem.open(live.game, save);
        assertEquals(expected, overLive.outcome(), overLive.reason());
        assertTrue(!overLive.loaded() && !overLive.message().isBlank() && !overLive.reason().isBlank());
        live.assertIntact();

        assertArrayEquals(bytes, Files.readAllBytes(save), "a refused save is never modified");
        assertEquals(stamp, Files.getLastModifiedTime(save));
        Files.delete(save);
    }

    private Path file(String name, byte[] bytes) throws IOException {
        return Files.write(directory.resolve(name), bytes);
    }

    /** A live session with a world, edits and a carried kit, and what it must still hold. */
    private static final class Live {
        final Game game = new Game();
        final World world;
        final Player player;
        final Vec3i edited;
        final Map<Vec3i, Byte> edits;

        Live() {
            game.newWorld(LIVE_SEED, true, World.GEN_CAVE_IDENTITIES);
            world = game.world;
            player = game.player;
            edited = new Vec3i((int) Math.floor(player.pos.x) + 2, (int) Math.floor(player.pos.y) + 3,
                    (int) Math.floor(player.pos.z));
            world.setBlock(edited.x(), edited.y(), edited.z(), BlockType.PLANK, true);
            player.inventory.add(ItemType.IRON_INGOT, 5);
            player.health = 61f;
            edits = new LinkedHashMap<>(world.changedBlocks);
        }

        void assertIntact() {
            assertSame(world, game.world, "the live world is never released");
            assertSame(player, game.player, "nor its player replaced");
            assertEquals(LIVE_SEED, game.world.seed);
            assertEquals(BlockType.PLANK, world.getBlock(edited.x(), edited.y(), edited.z()));
            assertEquals(edits, world.changedBlocks, "every edit is kept");
            assertTrue(player.inventory.count(ItemType.IRON_INGOT) >= 5);
            assertEquals(61f, player.health);
        }
    }
}
