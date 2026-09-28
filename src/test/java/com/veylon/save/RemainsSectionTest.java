package com.veylon.save;

import com.veylon.Game;
import com.veylon.entity.BodyFamily;
import com.veylon.entity.BodyFragment;
import com.veylon.entity.BodyFragmentConstants;
import com.veylon.entity.BodyFragmentSystem;
import com.veylon.entity.Carcass;
import com.veylon.entity.Creature;
import com.veylon.entity.Creature.CreatureType;
import com.veylon.entity.FragmentAnatomy;
import com.veylon.entity.FragmentPose;
import com.veylon.entity.Npc;
import com.veylon.entity.NpcAppearance;
import com.veylon.entity.RagdollConstants;
import com.veylon.gfx.model.FragmentModels;
import com.veylon.item.ItemType;
import com.veylon.settlement.NpcArchetype;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the remains of every body blown apart survive a save in
 * {@code world.remains}: each piece comes back as its own species' piece, in
 * the pose its body died in, where and how it lay, with its look and its rot
 * clock; an animal's one harvest record comes back on its torso, never twice
 * and never as a second whole body; a save without the section loads as
 * v0.8.0 would, and a literal v0.8.0 section still loads; a body this build
 * does not know is skipped, never read as a person; and anything malformed
 * fails the whole load and leaves the live world alone.
 */
class RemainsSectionTest {

    private static final float GROUND = 40f;
    private static final float X = 310.5f;
    private static final float Z = 310.5f;
    /** A powder keg's power; the strongest blast the game makes. */
    private static final float KEG = 3.8f;

    /**
     * A {@code world.fragments} version 1 payload exactly as v0.8.0 wrote it,
     * composed by hand from the format, not by any writer: a guard's head and
     * the player's right shin.
     */
    private static final String V080_FRAGMENTS = String.join("",
            "00000001", "00000002",
            // HEAD (1) at (310.5, 40.25, 312), turned (0.5, 0.5, -0.5, 0.5), 200 s left,
            // GUARD (ordinal 1, stored + 1), not raider/trader/sick, camp 1.
            "00000001", "439B4000", "42210000", "439C0000",
            "3F000000", "3F000000", "BF000000", "3F000000", "43480000",
            "00000002", "00", "00", "00", "00000001",
            // SHIN_R (9) at (308.75, 40.0625, 309.5), turned (-0.5, 0.5, 0.5, 0.5),
            // a full 420 s, no archetype, the neutral camp -2.
            "00000009", "439A6000", "42204000", "439AC000",
            "BF000000", "3F000000", "3F000000", "3F000000", "43D20000",
            "00000000", "00", "00", "00", "FFFFFFFE");

    @TempDir
    Path directory;

    // ------------------------------------------------------------------
    // Round trips
    // ------------------------------------------------------------------

    @Test
    void everyKindOfBodyComesBackAsItsOwnPiecesInThePoseItDiedIn() throws IOException {
        Game original = mixedWorld(4711L);
        List<BodyFragment> before = List.copyOf(original.fragments.settled);
        List<Carcass> recordsBefore = List.copyOf(original.entities.carcasses);

        Path save = directory.resolve("mixed.sav");
        assertTrue(SaveSystem.save(original, save));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save));

        assertEquals(0, loaded.fragments.liveCount(), "nothing comes back flying");
        List<BodyFragment> after = loaded.fragments.settled;
        assertEquals(before.size(), after.size(), "every settled piece of every body comes back");
        for (int i = 0; i < before.size(); i++) {
            assertSamePiece(before.get(i), after.get(i), "piece " + i);
        }
        for (BodyFamily family : BodyFamily.values()) {
            int bodies = family == BodyFamily.HUMANOID ? 2 : 1; // the person and the player's remains
            assertEquals(bodies * family.anatomy().pieces.size(),
                    after.stream().filter(f -> f.definition.family == family).count(),
                    family + " comes back as its own pieces, once");
        }
        assertOneShared(before, after);

        assertEquals(recordsBefore.size(), loaded.entities.carcasses.size(),
                "a load adds no carcass and loses none");
        for (int i = 0; i < recordsBefore.size(); i++) {
            Carcass was = recordsBefore.get(i);
            Carcass is = loaded.entities.carcasses.get(i);
            String what = was.type + "'s record";
            assertSame(was.type, is.type, what);
            assertEquals(was.meatLeft, is.meatLeft, what + ": meat left");
            assertEquals(was.hideLeft, is.hideLeft, what + ": hide left");
            assertEquals(was.decay, is.decay, 0f, what + ": rot");
            assertEquals(was.stuckArrows, is.stuckArrows, what + ": lodged arrows");
            assertSame(was.stuckArrowType, is.stuckArrowType, what + ": arrow kind");
            assertTrue(is.fragmented(), what + " is tied to its torso again, never drawn whole");
            assertTrue(is.atRest(), what + " can be harvested at once");
            BodyFragment torso = after.get(before.indexOf(was.remains));
            assertSame(torso, is.remains, what + " rides on the same torso piece");
            assertSame(is, torso.harvest, what + ": and the torso carries it back");
            assertEquals(was.pos.x, is.pos.x, 0f, what + " lies under its torso");
            assertEquals(was.pos.y, is.pos.y, 0f);
            assertEquals(was.pos.z, is.pos.z, 0f);
        }
        assertEquals(5, recordsBefore.size(), "precondition: one record per species that leaves a carcass");
        Carcass deer = loaded.entities.carcasses.get(0);
        assertEquals(CreatureType.DEER.meatYield - 1, deer.meatLeft, "precondition: the deer was partly taken");
        assertEquals(0, deer.hideLeft);
        Carcass wolf = loaded.entities.carcasses.get(1);
        assertEquals(3, wolf.stuckArrows, "the wolf's arrows are still in it");
        assertSame(ItemType.IRON_ARROW, wolf.stuckArrowType);

        assertArrayEquals(RemainsSection.write(original), RemainsSection.write(loaded),
                "saving the loaded world again writes the same remains, so repeated loads cannot drift");
    }

    @Test
    void aSaveWithoutTheRemainsSectionLoadsAsV080Would() throws IOException {
        Game original = mixedWorld(8080L);
        List<BodyFragment> people = original.fragments.settled.stream()
                .filter(f -> f.piece != null).toList();
        assertEquals(20, people.size(), "precondition: a person and the player's remains");
        List<Carcass> recordsBefore = List.copyOf(original.entities.carcasses);

        Path save = directory.resolve("v080.sav");
        assertTrue(SaveSystem.save(original, save));
        Files.write(save, CreativeSaveSections.replace(Files.readAllBytes(save), RemainsSection.ID, null));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save), "a save without world.remains still loads");

        List<BodyFragment> after = loaded.fragments.settled;
        assertEquals(people.size(), after.size(), "the people come back from version 1; no animal piece does");
        FragmentPose rest = FragmentAnatomy.humanoid().restPose();
        for (int i = 0; i < people.size(); i++) {
            BodyFragment was = people.get(i);
            BodyFragment is = after.get(i);
            assertSame(was.piece, is.piece, "piece " + i);
            assertEquals(was.pos.x, is.pos.x, 0f);
            assertEquals(was.pos.y, is.pos.y, 0f);
            assertEquals(was.pos.z, is.pos.z, 0f);
            assertEquals(was.orientation.w, is.orientation.w, 1e-6f);
            assertEquals(was.decay, is.decay, 0f);
            assertEquals(was.appearance.campIndex, is.appearance.campIndex);
            assertSame(rest, is.pose, "version 1 never carried a pose");
        }
        assertEquals(recordsBefore.size(), loaded.entities.carcasses.size(),
                "each animal still leaves exactly one record");
        for (int i = 0; i < recordsBefore.size(); i++) {
            Carcass is = loaded.entities.carcasses.get(i);
            assertFalse(is.fragmented(), is.type + " falls back to one whole carcass, not none and not two");
            assertEquals(recordsBefore.get(i).meatLeft, is.meatLeft, is.type + ": same meat");
            assertEquals(recordsBefore.get(i).hideLeft, is.hideLeft, is.type + ": same hide");
        }
    }

    @Test
    void aLiteralVersion1SectionStillLoadsAndIsStillWrittenByteForByte() throws IOException {
        byte[] literal = HexFormat.of().parseHex(V080_FRAGMENTS);
        assertEquals(102, literal.length, "precondition: two 47-byte records after version and count");

        Game direct = new Game();
        FragmentsSection.read(literal, direct);
        assertV080Pieces(direct.fragments.settled);
        assertArrayEquals(literal, FragmentsSection.write(direct),
                "people are still written to version 1 exactly as v0.8.0 wrote them");

        Game empty = arena(1201L);
        Path save = directory.resolve("literal-v1.sav");
        assertTrue(SaveSystem.save(empty, save));
        byte[] bytes = CreativeSaveSections.replace(Files.readAllBytes(save), FragmentsSection.ID, literal);
        Files.write(save, CreativeSaveSections.replace(bytes, RemainsSection.ID, null));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save), "a v0.8.0 fragment section loads through the whole reader");
        assertV080Pieces(loaded.fragments.settled);
    }

    @Test
    void aSaveTakenMidFlightKeepsEveryBodyAndItsRecordOnce() throws IOException {
        Game game = arena(77L);
        game.totalTime = 3.3;
        blowApart(game, CreatureType.DEER, X - 3f, 0f);
        blowApart(game, CreatureType.WOLF, X + 3f, 90f);
        person(game, X, 45f, NpcArchetype.SCOUT);
        for (int i = 0; i < 3; i++) {
            game.fragments.update(game, 1f / 60f);
        }
        int pieces = FragmentAnatomy.of(CreatureType.DEER).pieces.size()
                + FragmentAnatomy.of(CreatureType.WOLF).pieces.size() + BodyFragment.Piece.values().length;
        assertEquals(pieces, game.fragments.liveCount(), "precondition: every piece is in the air");
        assertEquals(2, game.entities.carcasses.size());
        assertFalse(game.entities.carcasses.getFirst().atRest(), "precondition: the torsos are still flying");

        Path save = directory.resolve("midflight.sav");
        assertTrue(SaveSystem.save(game, save));
        assertEquals(0, game.fragments.liveCount(), "saving lays flying pieces down");
        assertEquals(pieces, game.fragments.settledCount(), "rather than dropping or doubling them");
        List<BodyFragment> laid = List.copyOf(game.fragments.settled);

        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save));
        assertEquals(0, loaded.fragments.liveCount());
        assertEquals(pieces, loaded.fragments.settledCount(), "a mid-flight save comes back whole, in pieces");
        for (int i = 0; i < laid.size(); i++) {
            assertSamePiece(laid.get(i), loaded.fragments.settled.get(i), "piece " + i);
        }
        assertEquals(2, loaded.entities.carcasses.size(), "one record per animal, not one per save");
        for (Carcass c : loaded.entities.carcasses) {
            assertTrue(c.fragmented() && c.atRest(), c.type + " is tied to its landed torso");
            assertEquals(c.remains.pos.y - c.remains.halfHeight, c.pos.y, 1e-4f,
                    c.type + "'s record lies under its torso");
        }
    }

    @Test
    void theFullestWorldTheCapsAdmitRoundTrips() throws IOException {
        Game original = arena(6060L);
        original.player.pos.set(X, GROUND + 0.1f, Z);
        for (int i = 0; i < BodyFragmentConstants.MAX_ANCHORED_REMAINS; i++) {
            float x = X - 27f + (i % 10) * 6f;
            float z = Z - 15f + (i / 10) * 6f;
            blowApart(original, CreatureType.DEER, x, z, i * 37f);
            original.fragments.settleAll(original);
        }
        assertEquals(RemainsSection.MAX_PIECES, original.fragments.settledCount(),
                "precondition: sixty deer fill the settled cap");
        assertEquals(RemainsSection.MAX_LINKS, BodyFragmentSystem.anchoredRemains(original.entities.carcasses),
                "precondition: and the anchored cap");
        List<BodyFragment> before = List.copyOf(original.fragments.settled);

        Path save = directory.resolve("full.sav");
        assertTrue(SaveSystem.save(original, save));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save), "the largest world the caps admit must load");
        assertEquals(before.size(), loaded.fragments.settledCount());
        for (int i = 0; i < before.size(); i++) {
            assertSamePiece(before.get(i), loaded.fragments.settled.get(i), "piece " + i);
        }
        assertEquals(RemainsSection.MAX_LINKS, BodyFragmentSystem.anchoredRemains(loaded.entities.carcasses),
                "every record is tied to its torso again");
        for (Carcass c : loaded.entities.carcasses) {
            assertSame(c, c.remains.harvest);
            assertTrue(loaded.fragments.settled.contains(c.remains));
        }
    }

    @Test
    void loadingAnotherWorldDropsEveryPieceAndRecordOfTheLastOne() throws IOException {
        Path withRemains = directory.resolve("remains.sav");
        assertTrue(SaveSystem.save(mixedWorld(515L), withRemains));
        Path without = directory.resolve("empty.sav");
        assertTrue(SaveSystem.save(arena(516L), without));

        Game live = new Game();
        assertTrue(SaveSystem.load(live, withRemains));
        int pieces = live.fragments.settledCount();
        List<Carcass> first = List.copyOf(live.entities.carcasses);
        assertEquals(5, first.size());

        assertTrue(SaveSystem.load(live, without));
        assertEquals(0, live.fragments.settledCount(), "the other world's pieces are gone");
        assertEquals(0, live.fragments.liveCount());
        assertTrue(live.entities.carcasses.isEmpty(), "and so are its records");

        assertTrue(SaveSystem.load(live, withRemains), "loading the first world again");
        assertEquals(pieces, live.fragments.settledCount(), "brings back its pieces once");
        assertEquals(first.size(), live.entities.carcasses.size(), "and its records once");
        for (int i = 0; i < first.size(); i++) {
            Carcass c = live.entities.carcasses.get(i);
            assertNotSame(first.get(i), c, "a record of an earlier load is never reused");
            assertSame(c, c.remains.harvest, c.type + ": tied to a piece of this load");
            assertTrue(live.fragments.settled.contains(c.remains));
            assertEquals(first.get(i).meatLeft, c.meatLeft);
        }
    }

    @Test
    void aPoseTheReaderWouldRefuseIsWrittenAsTheRestPose() throws IOException {
        FragmentAnatomy deer = FragmentAnatomy.of(CreatureType.DEER);
        FragmentPose oversized = new FragmentPose.Recorder(deer).set(0, 0, 0, 0, 0, 0, 0, 3f).snapshot();
        FragmentPose wound = new FragmentPose.Recorder(deer).set(1, 50f, 0, 0, 0, 0, 0, 1f).snapshot();
        Game original = new Game();
        for (FragmentPose pose : List.of(oversized, wound)) {
            BodyFragment f = new BodyFragment(deer.piece(1), pose);
            f.pos.set(X, GROUND + 0.3f, Z);
            f.orientation.rotationY(0.4f);
            original.fragments.restoreSettled(f);
        }

        Game loaded = new Game();
        RemainsSection.read(RemainsSection.write(original), loaded);
        assertEquals(2, loaded.fragments.settledCount(), "the pieces themselves are kept");
        for (int i = 0; i < 2; i++) {
            BodyFragment f = loaded.fragments.settled.get(i);
            assertSame(deer.restPose(), f.pose, "a pose the reader would refuse travels as the rest pose");
            assertEquals(X, f.pos.x, 0f, "and the piece keeps its place");
            assertEquals(original.fragments.settled.get(i).orientation.y, f.orientation.y, 1e-6f);
        }
    }

    // ------------------------------------------------------------------
    // What this build cannot read
    // ------------------------------------------------------------------

    @Test
    void aBodyThisBuildDoesNotKnowIsSkippedAndNeverReadAsAPerson() throws IOException {
        Spec spec = new Spec();
        spec.pose(BodyFamily.values().length, 0);                       // 0: a family from a newer build
        spec.pose(BodyFamily.DEER.ordinal(), 3);                         // 1: a deer table of another size
        spec.pose(BodyFamily.DEER.ordinal(), 0);                         // 2: the deer at rest
        spec.pose(BodyFamily.HUMANOID.ordinal(), 0);                     // 3: a person at rest
        spec.pose(BodyFamily.WOLF.ordinal(), FragmentAnatomy.of(CreatureType.WOLF).jointCount()); // 4
        spec.poses.get(4).values[0] = 0.4f;                              //    a wolf that died turning
        spec.piece(0, 0);                                                // 0 skipped: unknown family
        spec.piece(1, 0);                                                // 1 a deer torso, pose unknown
        spec.piece(2, FragmentAnatomy.of(CreatureType.DEER).pieces.size()); // 2 skipped: unknown piece
        spec.piece(2, 1);                                                // 3 a deer's head
        spec.piece(3, 1);                                                // 4 a person's head
        spec.piece(4, FragmentAnatomy.of(CreatureType.WOLF).pieces.size() + 3); // 5 skipped: unknown piece
        spec.link(0, 0, 2, ItemType.ARROW.ordinal() + 1);                // on the skipped torso
        spec.link(1, 3, 1, ItemType.IRON_ARROW.ordinal() + 1);           // on the other table's torso
        spec.link(5, 1, 0, 0);                                           // on the skipped wolf piece

        Game game = withCarcasses(new Game());
        RemainsSection.read(spec.bytes(), game);
        List<BodyFragment> read = game.fragments.settled;
        assertEquals(3, read.size(), "only the pieces this build knows are laid down");
        FragmentAnatomy deerTable = FragmentAnatomy.of(CreatureType.DEER);
        assertSame(deerTable.piece(0), read.get(0).definition, "a deer torso posed by another table");
        assertSame(deerTable.restPose(), read.get(0).pose, "lies where it lay, in the deer's rest pose");
        assertSame(deerTable.piece(1), read.get(1).definition, "a deer's piece 1 is the deer's head");
        assertNull(read.get(1).piece, "and never the person's piece with the same id");
        assertSame(BodyFragment.Piece.HEAD, read.get(2).piece);

        List<Carcass> records = game.entities.carcasses;
        assertFalse(records.get(0).fragmented(), "a record whose torso was skipped stays one whole carcass");
        assertEquals(2, records.get(0).stuckArrows, "and keeps its arrows");
        assertSame(ItemType.ARROW, records.get(0).stuckArrowType);
        assertSame(read.get(0), records.get(3).remains, "a known torso still carries its record");
        assertEquals(1, records.get(3).stuckArrows);
        assertFalse(records.get(1).fragmented(), "the wolf's record stays whole too");
    }

    @Test
    void malformedRemainsFailTheWholeLoadAndLeaveTheLiveWorldAlone() throws IOException {
        Game accepting = withCarcasses(new Game());
        RemainsSection.read(valid().bytes(), accepting);
        assertEquals(5, accepting.fragments.settledCount(),
                "a guard that rejected everything would pass every case below");
        assertTrue(accepting.entities.carcasses.getFirst().fragmented());

        Map<String, Consumer<Spec>> corruptions = new LinkedHashMap<>();
        corruptions.put("a future version", s -> s.version = RemainsSection.VERSION + 1);
        corruptions.put("version zero", s -> s.version = 0);
        corruptions.put("a negative pose count", s -> s.poseCount = -1);
        corruptions.put("one pose over the cap", s -> s.poseCount = RemainsSection.MAX_POSES + 1);
        corruptions.put("a negative family ordinal", s -> s.poses.get(0).family = -1);
        corruptions.put("a negative joint count", s -> s.poses.get(1).resize(-1));
        corruptions.put("more joints than any pose may list",
                s -> s.poses.get(1).resize(RemainsSection.MAX_POSE_JOINTS + 1));
        corruptions.put("a NaN pose angle", s -> s.poses.get(1).values[0] = Float.NaN);
        corruptions.put("an angle past the bound", s -> s.poses.get(1).values[1] = 41f);
        corruptions.put("an offset past the bound", s -> s.poses.get(1).values[4] = -4.5f);
        corruptions.put("a zero joint scale", s -> s.poses.get(1).values[6] = 0f);
        corruptions.put("a joint scale past the bound", s -> s.poses.get(1).values[6] = 2.5f);
        corruptions.put("a pose that draws a piece out of scale", s -> {
            float[] values = s.poses.get(1).values;
            for (int j = 6; j < values.length; j += 7) values[j] = 1.5f;
        });
        corruptions.put("a negative piece count", s -> s.pieceCount = -1);
        corruptions.put("one piece over the cap", s -> s.pieceCount = RemainsSection.MAX_PIECES + 1);
        corruptions.put("a piece of a missing pose", s -> s.pieces.get(0).pose = s.poses.size());
        corruptions.put("a negative pose index", s -> s.pieces.get(0).pose = -1);
        corruptions.put("a negative piece id", s -> s.pieces.get(0).id = -1);
        corruptions.put("a NaN position", s -> s.pieces.get(1).x = Float.NaN);
        corruptions.put("a position below the world", s -> s.pieces.get(1).y = -20_000f);
        corruptions.put("a zero quaternion", s -> s.pieces.get(1).orientation(0, 0, 0, 0));
        corruptions.put("a quaternion of half length", s -> s.pieces.get(1).orientation(0.5f, 0, 0, 0));
        corruptions.put("an infinite quaternion", s -> s.pieces.get(1).qw = Float.POSITIVE_INFINITY);
        corruptions.put("decay longer than a corpse lasts",
                s -> s.pieces.get(2).decay = RagdollConstants.CORPSE_DECAY + 1f);
        corruptions.put("negative decay", s -> s.pieces.get(2).decay = -1f);
        corruptions.put("an archetype past the enum",
                s -> s.pieces.get(2).archetype = NpcArchetype.values().length + 1);
        corruptions.put("a negative link count", s -> s.linkCount = -1);
        corruptions.put("one link over the cap", s -> s.linkCount = RemainsSection.MAX_LINKS + 1);
        corruptions.put("a link to a missing piece", s -> s.links.get(0).piece = s.pieces.size());
        corruptions.put("a link to a missing carcass", s -> s.links.get(0).carcass = 99);
        corruptions.put("a negative carcass index", s -> s.links.get(0).carcass = -1);
        // Each of these breaks exactly one rule; everything else about the link is sound.
        corruptions.put("two records on one torso", s -> s.link(0, 3, 0, 0));
        corruptions.put("one record on two torsos", s -> s.link(4, 0, 0, 0));
        corruptions.put("a record on a limb", s -> s.links.get(0).piece = 1);
        corruptions.put("a record on a person", s -> {
            s.piece(1, BodyFragment.Piece.TORSO.ordinal());
            s.links.get(0).piece = s.pieces.size() - 1;
        });
        corruptions.put("a record on another species", s -> s.links.get(0).piece = 3);
        corruptions.put("a record of a body that leaves none", s -> {
            s.pose(BodyFamily.BIRD.ordinal(), 0);
            s.piece(s.poses.size() - 1, 0);
            s.links.get(0).piece = s.pieces.size() - 1;
            s.links.get(0).carcass = 2;
        });
        corruptions.put("negative arrows", s -> s.links.get(0).arrows = -1);
        corruptions.put("more arrows than any animal holds",
                s -> s.links.get(0).arrows = RemainsSection.MAX_STUCK_ARROWS + 1);
        corruptions.put("an arrow item past the enum", s -> s.links.get(0).item = ItemType.values().length + 1);
        corruptions.put("a negative arrow item", s -> s.links.get(0).item = -1);

        Map<String, byte[]> payloads = new LinkedHashMap<>();
        for (Map.Entry<String, Consumer<Spec>> corruption : corruptions.entrySet()) {
            Spec spec = valid();
            corruption.getValue().accept(spec);
            payloads.put(corruption.getKey(), spec.bytes());
        }
        byte[] good = valid().bytes();
        payloads.put("a truncated record", Arrays.copyOf(good, good.length - 1));
        payloads.put("trailing bytes", Arrays.copyOf(good, good.length + 1));
        payloads.put("an empty payload", new byte[0]);

        Game game = withCarcasses(arena(5150L));
        blowApart(game, CreatureType.HARE, X, Z, 0f);
        settle(game);
        Path save = directory.resolve("malformed-remains.sav");
        assertTrue(SaveSystem.save(game, save));
        byte[] saved = Files.readAllBytes(save);
        var world = game.world;
        var player = game.player;
        player.health = 73f;
        List<BodyFragment> pieces = List.copyOf(game.fragments.settled);
        List<Carcass> records = List.copyOf(game.entities.carcasses);
        Carcass hare = records.getLast();
        BodyFragment hareTorso = hare.remains;
        assertTrue(pieces.contains(hareTorso), "precondition: the hare's record rides on its settled torso");

        for (Map.Entry<String, byte[]> payload : payloads.entrySet()) {
            String what = payload.getKey();
            assertThrows(IOException.class,
                    () -> RemainsSection.read(payload.getValue(), withCarcasses(new Game())),
                    what + " must fail explicitly");
            Files.write(save, CreativeSaveSections.replace(saved, RemainsSection.ID, payload.getValue()));
            boolean loaded = assertDoesNotThrow(() -> SaveSystem.load(game, save),
                    what + " must fail the load, not throw out of it");
            assertFalse(loaded, what + " must reject the entire load");
            assertSame(world, game.world, "failed verification cannot release the live world");
            assertSame(player, game.player);
            assertEquals(73f, game.player.health);
            assertEquals(pieces, game.fragments.settled, "failed verification cannot touch the live pieces");
            assertEquals(records, game.entities.carcasses, "or the live records");
            assertSame(hareTorso, hare.remains, "or the links between them");
            assertSame(hare, hareTorso.harvest);
        }
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    /**
     * One body of every kind at rest: the six species, a person and the
     * player's remains, each in the pose it died in. The deer has been
     * partly taken and the wolf carries three iron arrows; the first bodies
     * have rotted a little longer than the last.
     */
    private static Game mixedWorld(long seed) {
        Game g = arena(seed);
        g.totalTime = 17.25;
        CreatureType[] species = CreatureType.values();
        for (int i = 0; i < species.length; i++) {
            Creature c = creature(g, species[i], X - 9f + 3f * i, Z - 2f, i * 50f);
            if (species[i] == CreatureType.WOLF) {
                c.stuckArrows = 3;
                c.stuckArrowType = ItemType.IRON_ARROW;
            }
            FragmentPose pose = g.fragments.deathPose(c);
            assertFalse(pose.isRest(), "precondition: the " + c.type + " dies in a living pose");
            g.fragments.spawnFromCreature(g, c, pose, c.pos.x + 1f, GROUND + 1f, c.pos.z + 0.5f, KEG);
        }
        settle(g);
        g.fragments.slowTick(g, 40f);
        person(g, X, 120f, NpcArchetype.BRUTE);
        g.player.pos.set(X - 4f, GROUND, Z + 3f);
        g.player.killBy(false);
        assertTrue(g.player.recordBlastDeath(X - 3f, GROUND + 0.5f, Z + 3f, KEG));
        assertEquals(10, g.fragments.spawnPlayerRemains(g, g.player).size());
        g.player.dead = false;
        g.player.health = 55f;
        settle(g);

        Carcass deer = g.entities.carcasses.getFirst();
        assertSame(CreatureType.DEER, deer.type);
        deer.meatLeft -= 1;
        deer.hideLeft = 0;
        return g;
    }

    private static Creature creature(Game g, CreatureType type, float x, float z, float yaw) {
        Creature c = g.entities.spawnCreature(g.world, type, x, GROUND, z);
        c.yaw = yaw;
        c.vel.zero();
        g.entities.creatures.remove(c);
        return c;
    }

    private static void blowApart(Game g, CreatureType type, float x, float z, float yaw) {
        Creature c = creature(g, type, x, z, yaw);
        g.fragments.spawnFromCreature(g, c, g.fragments.deathPose(c), x + 1f, GROUND + 1f, z + 0.5f, KEG);
    }

    private static void blowApart(Game g, CreatureType type, float x, float yaw) {
        blowApart(g, type, x, Z, yaw);
    }

    private static void person(Game g, float x, float yaw, NpcArchetype archetype) {
        Npc n = g.entities.spawnNpc(g.world, "Villager", x, GROUND, Z + 4f);
        n.yaw = yaw;
        n.vel.zero();
        n.archetype = archetype;
        n.raider = true;
        n.campIndex = 3;
        g.entities.npcs.remove(n);
        FragmentPose pose = g.fragments.deathPose(n);
        assertFalse(pose.isRest(), "precondition: the person dies in a living pose");
        g.fragments.spawnFromNpc(g, n, pose, n.pos.x + 1f, n.pos.y + 1f, n.pos.z + 0.5f, KEG);
    }

    private static void settle(Game g) {
        for (int i = 0; i < 1200 && g.fragments.liveCount() > 0; i++) {
            g.fragments.update(g, 1f / 60f);
        }
        assertEquals(0, g.fragments.liveCount(), "precondition: every piece has settled");
    }

    /**
     * Carcass 0 a deer, 1 a wolf, 2 a bird (which no real death leaves), 3 a
     * second deer, appended to the game's own.
     */
    private static Game withCarcasses(Game g) {
        g.entities.carcasses.add(new Carcass(CreatureType.DEER, X, GROUND, Z));
        g.entities.carcasses.add(new Carcass(CreatureType.WOLF, X + 2f, GROUND, Z));
        g.entities.carcasses.add(new Carcass(CreatureType.BIRD, X + 4f, GROUND, Z));
        g.entities.carcasses.add(new Carcass(CreatureType.DEER, X + 6f, GROUND, Z));
        return g;
    }

    private static Game arena(long seed) {
        Game game = new Game();
        game.newWorld(seed, true);
        for (int cx = 17; cx <= 21; cx++) {
            for (int cz = 17; cz <= 21; cz++) {
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
        game.world.campPos = null;
        game.ragdolls.reset();
        game.fragments.reset();
        return game;
    }

    // ------------------------------------------------------------------
    // Assertions
    // ------------------------------------------------------------------

    private static void assertSamePiece(BodyFragment expected, BodyFragment actual, String what) {
        assertSame(expected.definition, actual.definition, what + ": family and piece id");
        assertSame(expected.piece, actual.piece, what + ": a person's version 1 id, or none");
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
        assertTrue(actual.settled, what + " comes back at rest");
        assertEquals(expected.pose.isRest(), actual.pose.isRest(), what + ": rest or captured");
        for (int j = 0; j < expected.pose.anatomy.jointCount(); j++) {
            String joint = what + ": joint " + expected.pose.anatomy.joint(j).name;
            assertEquals(expected.pose.rotX(j), actual.pose.rotX(j), 0f, joint);
            assertEquals(expected.pose.rotY(j), actual.pose.rotY(j), 0f, joint);
            assertEquals(expected.pose.rotZ(j), actual.pose.rotZ(j), 0f, joint);
            assertEquals(expected.pose.poseX(j), actual.pose.poseX(j), 0f, joint);
            assertEquals(expected.pose.poseY(j), actual.pose.poseY(j), 0f, joint);
            assertEquals(expected.pose.poseZ(j), actual.pose.poseZ(j), 0f, joint);
            assertEquals(expected.pose.scale(j), actual.pose.scale(j), 0f, joint);
        }
        assertEquals(expected.halfX, actual.halfX, 0f, what + ": box drawn at the scale it died at");
        assertEquals(expected.halfY, actual.halfY, 0f);
        assertEquals(expected.halfZ, actual.halfZ, 0f);
        assertEquals(expected.halfWidth, actual.halfWidth, 1e-5f, what + ": sweep refit to the orientation");
        assertEquals(expected.halfHeight, actual.halfHeight, 1e-5f, what + ": sweep height");
        Matrix4f was = FragmentModels.rootFrame(expected, new Matrix4f());
        Matrix4f is = FragmentModels.rootFrame(actual, new Matrix4f());
        assertTrue(was.equals(is, 1e-5f), what + " is drawn exactly where and as it lay: " + was + " vs " + is);
        assertEquals(FragmentModels.rot(expected), FragmentModels.rot(actual), 0f, what + ": drawn as rotten");
    }

    /** Pieces that shared one pose before the save share one after it; different bodies do not. */
    private static void assertOneShared(List<BodyFragment> before, List<BodyFragment> after) {
        Map<FragmentPose, FragmentPose> loadedOf = new IdentityHashMap<>();
        for (int i = 0; i < before.size(); i++) {
            FragmentPose was = before.get(i).pose;
            FragmentPose is = after.get(i).pose;
            FragmentPose seen = loadedOf.putIfAbsent(was, is);
            assertSame(seen == null ? is : seen, is, "piece " + i + " shares its body's one pose");
        }
        assertEquals(loadedOf.size(), new IdentityHashMap<>(invert(loadedOf)).size(),
                "and no two bodies are merged into one pose");
        assertEquals(8, loadedOf.size(),
                "precondition: six animals and a person died in their own poses, the player's remains lie at rest");
    }

    private static Map<FragmentPose, FragmentPose> invert(Map<FragmentPose, FragmentPose> map) {
        Map<FragmentPose, FragmentPose> inverse = new IdentityHashMap<>();
        map.forEach((k, v) -> inverse.put(v, k));
        return inverse;
    }

    private static void assertV080Pieces(List<BodyFragment> pieces) {
        assertEquals(2, pieces.size());
        BodyFragment head = pieces.get(0);
        assertSame(BodyFragment.Piece.HEAD, head.piece, "version 1 id 1 is the person's head");
        assertSame(BodyFamily.HUMANOID, head.definition.family);
        assertSame(FragmentAnatomy.humanoid().restPose(), head.pose);
        assertEquals(310.5f, head.pos.x, 0f);
        assertEquals(40.25f, head.pos.y, 0f);
        assertEquals(312f, head.pos.z, 0f);
        assertEquals(-0.5f, head.orientation.z, 0f);
        assertEquals(200f, head.decay, 0f);
        assertSame(NpcArchetype.GUARD, head.appearance.archetype);
        assertEquals(1, head.appearance.campIndex);
        BodyFragment shin = pieces.get(1);
        assertSame(BodyFragment.Piece.SHIN_R, shin.piece, "version 1 id 9 is the right shin");
        assertEquals(308.75f, shin.pos.x, 0f);
        assertEquals(40.0625f, shin.pos.y, 0f);
        assertEquals(-0.5f, shin.orientation.x, 0f);
        assertEquals(RagdollConstants.CORPSE_DECAY, shin.decay, 0f);
        assertNull(shin.appearance.archetype);
        assertEquals(NpcAppearance.NEUTRAL_CAMP_INDEX, shin.appearance.campIndex, "the player's campless look");
    }

    // ------------------------------------------------------------------
    // Hand-built sections
    // ------------------------------------------------------------------

    /**
     * A valid section against {@link #withCarcasses}: a deer at rest (torso, a
     * leg), a person in a captured pose (shin), a wolf at rest (torso), a
     * second deer's torso, and the first deer's record, with two arrows, on
     * the first deer's torso.
     */
    private static Spec valid() {
        Spec spec = new Spec();
        spec.pose(BodyFamily.DEER.ordinal(), 0);
        PoseSpec person = spec.pose(BodyFamily.HUMANOID.ordinal(), FragmentAnatomy.humanoid().jointCount());
        person.values[0] = 0.3f;              // the root leans
        person.values[4] = 0.02f;             // and bobs
        person.values[6] = 1.01f;             // and breathes
        spec.pose(BodyFamily.WOLF.ordinal(), 0);
        spec.piece(0, 0);
        spec.piece(0, 5);
        spec.piece(1, BodyFragment.Piece.SHIN_L.ordinal());
        spec.piece(2, 0);
        spec.piece(0, 0);
        spec.link(0, 0, 2, ItemType.ARROW.ordinal() + 1);
        return spec;
    }

    private static final class PoseSpec {
        int family;
        int joints;
        float[] values;

        PoseSpec(int family, int joints) {
            this.family = family;
            resize(joints);
        }

        /** Sets the joint count; the values follow it (none for a negative count), each joint at rest. */
        void resize(int count) {
            joints = count;
            values = new float[Math.max(0, count) * 7];
            for (int j = 6; j < values.length; j += 7) {
                values[j] = 1f;
            }
        }
    }

    private static final class PieceSpec {
        int pose;
        int id;
        float x = X, y = GROUND + 0.2f, z = Z;
        float qx = 0.5f, qy = 0.5f, qz = -0.5f, qw = 0.5f;
        float decay = 300f;
        int archetype;
        int campIndex;

        void orientation(float x, float y, float z, float w) {
            qx = x; qy = y; qz = z; qw = w;
        }
    }

    private static final class LinkSpec {
        int piece;
        int carcass;
        int arrows;
        int item;
    }

    private static final class Spec {
        int version = RemainsSection.VERSION;
        Integer poseCount, pieceCount, linkCount;
        final List<PoseSpec> poses = new ArrayList<>();
        final List<PieceSpec> pieces = new ArrayList<>();
        final List<LinkSpec> links = new ArrayList<>();

        PoseSpec pose(int family, int joints) {
            PoseSpec pose = new PoseSpec(family, joints);
            poses.add(pose);
            return pose;
        }

        void piece(int pose, int id) {
            PieceSpec piece = new PieceSpec();
            piece.pose = pose;
            piece.id = id;
            piece.x = X + pieces.size();
            pieces.add(piece);
        }

        void link(int piece, int carcass, int arrows, int item) {
            LinkSpec link = new LinkSpec();
            link.piece = piece;
            link.carcass = carcass;
            link.arrows = arrows;
            link.item = item;
            links.add(link);
        }

        byte[] bytes() throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(version);
                out.writeInt(poseCount != null ? poseCount : poses.size());
                for (PoseSpec pose : poses) {
                    out.writeInt(pose.family);
                    out.writeInt(pose.joints);
                    for (float v : pose.values) out.writeFloat(v);
                }
                out.writeInt(pieceCount != null ? pieceCount : pieces.size());
                for (PieceSpec p : pieces) {
                    out.writeInt(p.pose);
                    out.writeInt(p.id);
                    out.writeFloat(p.x); out.writeFloat(p.y); out.writeFloat(p.z);
                    out.writeFloat(p.qx); out.writeFloat(p.qy); out.writeFloat(p.qz); out.writeFloat(p.qw);
                    out.writeFloat(p.decay);
                    out.writeInt(p.archetype);
                    out.writeBoolean(false); out.writeBoolean(false); out.writeBoolean(false);
                    out.writeInt(p.campIndex);
                }
                out.writeInt(linkCount != null ? linkCount : links.size());
                for (LinkSpec l : links) {
                    out.writeInt(l.piece);
                    out.writeInt(l.carcass);
                    out.writeInt(l.arrows);
                    out.writeInt(l.item);
                }
            }
            return bytes.toByteArray();
        }
    }
}
