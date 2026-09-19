package com.veylon.save;

import com.veylon.Game;
import com.veylon.entity.BodyFragment;
import com.veylon.entity.Npc;
import com.veylon.entity.RagdollConstants;
import com.veylon.settlement.NpcArchetype;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the pieces of a person blown apart survive a save: every settled piece
 * comes back where and how it lay, with the time it had left to rot and the
 * look of the person it came from; a piece still flying is saved where it
 * lands; a save written before the feature loads with no pieces; and a damaged
 * section fails the whole load instead of loading nonsense.
 */
class FragmentsSectionTest {

    private static final float GROUND = 40f;
    private static final float X = 310.5f;
    private static final float Z = 310.5f;
    /** A powder keg's power; the strongest blast the game makes. */
    private static final float KEG = 3.8f;

    @TempDir
    Path directory;

    @Test
    void settledFragmentsRoundTripExactly() throws IOException {
        Game original = arena(4242L);
        blowApart(original, X - 3f, 30f, NpcArchetype.POWDERMAN, true, false, true, 3);
        settle(original);
        original.fragments.slowTick(original, 90f);
        blowApart(original, X + 3f, 200f, NpcArchetype.CAPTIVE, false, true, false, 0);
        settle(original);
        original.fragments.slowTick(original, 30f);
        List<BodyFragment> before = List.copyOf(original.fragments.settled);
        assertEquals(20, before.size(), "precondition: two people lie in twenty pieces");
        assertEquals(RagdollConstants.CORPSE_DECAY - 120f, before.getFirst().decay, 1e-3f,
                "precondition: the first person has rotted longer than the second");
        assertEquals(RagdollConstants.CORPSE_DECAY - 30f, before.getLast().decay, 1e-3f);

        Path save = directory.resolve("fragments.sav");
        assertTrue(SaveSystem.save(original, save));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save));

        assertEquals(0, loaded.fragments.liveCount(), "nothing comes back flying");
        List<BodyFragment> after = loaded.fragments.settled;
        assertEquals(before.size(), after.size(), "every settled piece must come back");
        for (int i = 0; i < before.size(); i++) {
            assertSamePiece(before.get(i), after.get(i), "piece " + i);
        }
        assertSame(NpcArchetype.POWDERMAN, after.getFirst().appearance.archetype);
        assertSame(NpcArchetype.CAPTIVE, after.getLast().appearance.archetype);
        assertTrue(after.stream().noneMatch(f -> Math.abs(f.orientation.w) > 0.9999f),
                "precondition: no piece lies unturned, so the orientation is really compared");
    }

    @Test
    void aSaveTakenMidFlightStoresSettledPieces() throws IOException {
        Game game = arena(77L);
        blowApart(game, X, 0f, NpcArchetype.GUARD, false, false, false, 0);
        assertEquals(10, game.fragments.liveCount(), "precondition: every piece is in the air");

        Path save = directory.resolve("midflight.sav");
        assertTrue(SaveSystem.save(game, save));
        assertEquals(0, game.fragments.liveCount(), "saving lays flying pieces down");
        List<BodyFragment> laid = List.copyOf(game.fragments.settled);
        assertEquals(10, laid.size(), "rather than dropping them");

        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save));
        assertEquals(0, loaded.fragments.liveCount(), "pieces in flight are never restored");
        assertEquals(10, loaded.fragments.settledCount(),
                "a save taken mid-flight must come back as ten pieces, not as nothing");
        for (int i = 0; i < laid.size(); i++) {
            BodyFragment f = loaded.fragments.settled.get(i);
            assertSamePiece(laid.get(i), f, "piece " + i);
            assertEquals(GROUND, f.pos.y - f.halfHeight, 0.01f,
                    f.piece + " must come back on the ground it was laid on, not in the air");
            assertEquals(RagdollConstants.CORPSE_DECAY, f.decay, 0f,
                    "a piece laid down by the save starts the corpse clock");
        }
    }

    @Test
    void saveWithoutTheSectionLoadsWithNoFragments() throws IOException {
        Game original = arena(9001L);
        blowApart(original, X, 0f, NpcArchetype.SCAVENGER, true, false, false, 0);
        settle(original);
        Path save = directory.resolve("without-fragments.sav");
        assertTrue(SaveSystem.save(original, save));
        Files.write(save, CreativeSaveSections.replace(Files.readAllBytes(save),
                FragmentsSection.ID, null));

        // Loaded over a live world holding pieces of its own, at rest and in flight.
        Game live = arena(9002L);
        blowApart(live, X - 3f, 0f, NpcArchetype.GUARD, false, false, false, 0);
        settle(live);
        blowApart(live, X + 3f, 0f, NpcArchetype.GUARD, false, false, false, 0);
        assertEquals(10, live.fragments.settledCount());
        assertEquals(10, live.fragments.liveCount());

        assertTrue(SaveSystem.load(live, save), "a save written before this feature must still load");
        assertEquals(0, live.fragments.settledCount(),
                "an older save holds no pieces, and the outgoing world's are gone");
        assertEquals(0, live.fragments.liveCount());
    }

    @Test
    void corruptOrOversizedSectionIsRejected() throws IOException {
        byte[] good = payload(FragmentsSection.VERSION, 1, record().bytes());
        Game accepting = new Game();
        FragmentsSection.read(good, accepting);
        assertEquals(1, accepting.fragments.settledCount(),
                "a guard that rejected everything would pass every case below");

        Map<String, byte[]> corruptions = new LinkedHashMap<>();
        corruptions.put("one piece over the cap", payload(FragmentsSection.VERSION,
                FragmentsSection.MAX_FRAGMENTS + 1,
                repeat(record().bytes(), FragmentsSection.MAX_FRAGMENTS + 1)));
        corruptions.put("a negative count", payload(FragmentsSection.VERSION, -1, new byte[0]));
        corruptions.put("a NaN position", one(record().x(Float.NaN)));
        corruptions.put("a position below the world", one(record().y(-20_000f)));
        corruptions.put("a zero quaternion", one(record().orientation(0, 0, 0, 0)));
        corruptions.put("an infinite quaternion", one(record().orientation(0, 0, 0,
                Float.POSITIVE_INFINITY)));
        corruptions.put("a piece ordinal past the enum",
                one(record().piece(BodyFragment.Piece.values().length)));
        corruptions.put("a negative piece ordinal", one(record().piece(-1)));
        corruptions.put("an archetype ordinal past the enum",
                one(record().archetype(NpcArchetype.values().length + 1)));
        corruptions.put("decay longer than a corpse lasts",
                one(record().decay(RagdollConstants.CORPSE_DECAY + 1f)));
        corruptions.put("negative decay", one(record().decay(-1f)));
        corruptions.put("a future version", payload(FragmentsSection.VERSION + 1, 1,
                record().bytes()));
        corruptions.put("version zero", payload(0, 1, record().bytes()));
        corruptions.put("a truncated record", Arrays.copyOf(good, good.length - 1));
        corruptions.put("trailing bytes", Arrays.copyOf(good, good.length + 1));
        corruptions.put("an empty payload", new byte[0]);

        Game game = arena(5150L);
        blowApart(game, X, 0f, NpcArchetype.GUARD, false, false, false, 0);
        settle(game);
        Path save = directory.resolve("malformed-fragments.sav");
        assertTrue(SaveSystem.save(game, save));
        byte[] valid = Files.readAllBytes(save);
        var world = game.world;
        var player = game.player;
        player.health = 73f;
        List<BodyFragment> pieces = List.copyOf(game.fragments.settled);

        for (Map.Entry<String, byte[]> corruption : corruptions.entrySet()) {
            String what = corruption.getKey();
            assertThrows(IOException.class,
                    () -> FragmentsSection.read(corruption.getValue(), new Game()),
                    what + " must fail explicitly");
            Files.write(save, CreativeSaveSections.replace(valid, FragmentsSection.ID,
                    corruption.getValue()));
            boolean loaded = assertDoesNotThrow(() -> SaveSystem.load(game, save),
                    what + " must fail the load, not throw out of it");
            assertFalse(loaded, what + " must reject the entire load");
            assertSame(world, game.world, "failed verification cannot release the live world");
            assertSame(player, game.player);
            assertEquals(73f, game.player.health);
            assertEquals(pieces, game.fragments.settled,
                    "failed verification cannot touch the live world's pieces");
        }
    }

    @Test
    void aFullSectionFillsTheSettledCapAndNoFurther() throws IOException {
        byte[] full = payload(FragmentsSection.VERSION, FragmentsSection.MAX_FRAGMENTS,
                repeat(record().bytes(), FragmentsSection.MAX_FRAGMENTS));
        Game game = new Game();
        FragmentsSection.read(full, game);
        assertEquals(FragmentsSection.MAX_FRAGMENTS, game.fragments.settledCount(),
                "the largest valid section loads whole");
        BodyFragment oldest = game.fragments.settled.getFirst();

        FragmentsSection.read(payload(FragmentsSection.VERSION, 1, record().bytes()), game);
        assertEquals(FragmentsSection.MAX_FRAGMENTS, game.fragments.settledCount(),
                "a restored piece cannot push the settled list past its cap");
        assertFalse(game.fragments.settled.contains(oldest), "the oldest piece goes first");
    }

    @Test
    void aPieceTheReaderWouldRefuseIsLeftOutRatherThanCostingTheSave() throws IOException {
        Game original = arena(6060L);
        blowApart(original, X, 0f, NpcArchetype.MEDIC, false, false, true, 0);
        settle(original);
        BodyFragment broken = original.fragments.settled.get(3);
        broken.pos.y = Float.NaN;
        List<BodyFragment> sound = original.fragments.settled.stream()
                .filter(f -> f != broken).toList();

        Path save = directory.resolve("one-bad-piece.sav");
        assertTrue(SaveSystem.save(original, save), "one bad piece of debris cannot stop a save");
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save), "and the save it wrote must load");
        assertEquals(sound.size(), loaded.fragments.settledCount());
        for (int i = 0; i < sound.size(); i++) {
            assertSamePiece(sound.get(i), loaded.fragments.settled.get(i), "piece " + i);
        }
    }

    @Test
    void unknownSectionStillSkipped() throws IOException {
        Game original = arena(3131L);
        blowApart(original, X, 0f, NpcArchetype.TRACKER, true, false, false, 2);
        settle(original);
        List<BodyFragment> before = List.copyOf(original.fragments.settled);
        Path save = directory.resolve("newer-build.sav");
        assertTrue(SaveSystem.save(original, save));

        // Sections a newer build might add, one of them a near-namesake whose
        // payload this build's fragment reader would refuse outright.
        byte[] bytes = Files.readAllBytes(save);
        bytes = CreativeSaveSections.append(bytes, FragmentsSection.ID + ".v2",
                payload(99, Integer.MAX_VALUE, new byte[]{1, 2, 3}));
        bytes = CreativeSaveSections.append(bytes, "world.future-debris", new byte[]{-1, 0, 7});
        Files.write(save, bytes);

        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save), "unknown section ids are skipped by their length");
        assertEquals(before.size(), loaded.fragments.settledCount(),
                "and the known fragment section around them is still read");
        for (int i = 0; i < before.size(); i++) {
            assertSamePiece(before.get(i), loaded.fragments.settled.get(i), "piece " + i);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void assertSamePiece(BodyFragment expected, BodyFragment actual, String what) {
        assertSame(expected.piece, actual.piece, what + ": piece id");
        assertEquals(expected.pos.x, actual.pos.x, 0f, what + ": x");
        assertEquals(expected.pos.y, actual.pos.y, 0f, what + ": y");
        assertEquals(expected.pos.z, actual.pos.z, 0f, what + ": z");
        assertEquals(expected.orientation.x, actual.orientation.x, 1e-6f, what + ": orientation x");
        assertEquals(expected.orientation.y, actual.orientation.y, 1e-6f, what + ": orientation y");
        assertEquals(expected.orientation.z, actual.orientation.z, 1e-6f, what + ": orientation z");
        assertEquals(expected.orientation.w, actual.orientation.w, 1e-6f, what + ": orientation w");
        assertEquals(expected.decay, actual.decay, 0f, what + ": decay");
        assertSame(expected.appearance.archetype, actual.appearance.archetype, what + ": archetype");
        assertEquals(expected.appearance.raider, actual.appearance.raider, what + ": raider");
        assertEquals(expected.appearance.trader, actual.appearance.trader, what + ": trader");
        assertEquals(expected.appearance.sick, actual.appearance.sick, what + ": sick");
        assertEquals(expected.appearance.campIndex, actual.appearance.campIndex, what + ": camp");
        assertTrue(actual.settled, what + " must come back at rest, so it rots and is drawn rotting");
        assertEquals(expected.halfWidth, actual.halfWidth, 1e-5f,
                what + ": the sweep box is refit to the saved orientation");
        assertEquals(expected.halfHeight, actual.halfHeight, 1e-5f, what + ": sweep height");
    }

    /** A person with the given look standing at {@code x}, blown apart by a keg beside them. */
    private static void blowApart(Game g, float x, float yaw, NpcArchetype archetype,
                                  boolean raider, boolean trader, boolean sick, int campIndex) {
        Npc n = g.entities.spawnNpc(g.world, "Villager", x, GROUND, Z);
        n.yaw = yaw;
        n.vel.zero();
        n.archetype = archetype;
        n.raider = raider;
        n.isTrader = trader;
        n.sick = sick;
        n.campIndex = campIndex;
        g.entities.npcs.remove(n);
        g.fragments.spawnFromNpc(g, n, n.pos.x + 1f, n.pos.y + 1f, n.pos.z + 0.5f, KEG);
    }

    private static void settle(Game g) {
        for (int i = 0; i < 1200 && g.fragments.liveCount() > 0; i++) {
            g.fragments.update(g, 1f / 60f);
        }
        assertEquals(0, g.fragments.liveCount(), "precondition: every piece has settled");
    }

    /** One hand-built record; valid until a setter breaks it. */
    private static RecordBuilder record() {
        return new RecordBuilder();
    }

    private static final class RecordBuilder {
        int piece = BodyFragment.Piece.SHIN_L.ordinal();
        float x = X, y = GROUND + 0.09f, z = Z;
        float qx = 0.5f, qy = 0.5f, qz = -0.5f, qw = 0.5f;
        float decay = 200f;
        int archetype = NpcArchetype.GUARD.ordinal() + 1;

        RecordBuilder piece(int value) { piece = value; return this; }
        RecordBuilder x(float value) { x = value; return this; }
        RecordBuilder y(float value) { y = value; return this; }
        RecordBuilder decay(float value) { decay = value; return this; }
        RecordBuilder archetype(int value) { archetype = value; return this; }

        RecordBuilder orientation(float x, float y, float z, float w) {
            qx = x; qy = y; qz = z; qw = w;
            return this;
        }

        byte[] bytes() throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(piece);
                out.writeFloat(x); out.writeFloat(y); out.writeFloat(z);
                out.writeFloat(qx); out.writeFloat(qy); out.writeFloat(qz); out.writeFloat(qw);
                out.writeFloat(decay);
                out.writeInt(archetype);
                out.writeBoolean(true); out.writeBoolean(false); out.writeBoolean(false);
                out.writeInt(0);
            }
            assertEquals(47, bytes.size(), "one record is 47 bytes");
            return bytes.toByteArray();
        }
    }

    private static byte[] one(RecordBuilder record) throws IOException {
        return payload(FragmentsSection.VERSION, 1, record.bytes());
    }

    /** A hand-built section: version, count, then the given record bytes. */
    private static byte[] payload(int version, int count, byte[] records) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(version);
            out.writeInt(count);
            out.write(records);
        }
        return bytes.toByteArray();
    }

    private static byte[] repeat(byte[] record, int times) {
        byte[] all = new byte[record.length * times];
        for (int i = 0; i < times; i++) {
            System.arraycopy(record, 0, all, i * record.length, record.length);
        }
        return all;
    }

    private static Game arena(long seed) {
        Game game = new Game();
        game.newWorld(seed, true);
        for (int cx = 18; cx <= 21; cx++) {
            for (int cz = 18; cz <= 21; cz++) {
                Chunk c = game.world.getOrCreateChunk(cx, cz);
                for (int lx = 0; lx < Chunk.SX; lx++) {
                    for (int lz = 0; lz < Chunk.SZ; lz++) {
                        for (int y = 0; y < Chunk.SY; y++) {
                            c.set(lx, y, lz, y < GROUND ? BlockType.STONE : BlockType.AIR);
                        }
                    }
                }
                c.recomputeAllHeights();
                c.rebuildLights();
            }
        }
        game.player.pos.set(X, GROUND + 0.1f, Z);
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        game.entities.carcasses.clear();
        game.entities.corpses.clear();
        game.ragdolls.reset();
        game.fragments.reset();
        return game;
    }
}
