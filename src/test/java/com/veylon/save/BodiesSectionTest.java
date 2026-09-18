package com.veylon.save;

import com.veylon.Game;
import com.veylon.entity.Carcass;
import com.veylon.entity.Creature;
import com.veylon.entity.HumanCorpse;
import com.veylon.entity.Npc;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How bodies survive a save: the corpse and the pose it settled in persist, the
 * falling body does not, and a save written before the feature existed still
 * loads with every carcass in the pose the game has always drawn.
 */
class BodiesSectionTest {

    private static final float GROUND = 40f;
    private static final float X = 310.5f;
    private static final float Z = 310.5f;

    @TempDir
    Path directory;

    @Test
    void versionOneFlatBonesMigrateByNameWithoutBendingTheNewChildLinks() throws IOException {
        Game game = arena(4242L);
        game.entities.carcasses.add(new Carcass(Creature.CreatureType.WOLF, X, GROUND, Z));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(1);
            out.writeInt(1);
            writeLegacyPose(out, 6);
            out.writeInt(1);
            out.writeFloat(X); out.writeFloat(GROUND); out.writeFloat(Z);
            out.writeFloat(200);
            out.writeInt(NpcArchetype.GUARD.ordinal() + 1);
            out.writeBoolean(false); out.writeBoolean(false); out.writeBoolean(false); out.writeInt(0);
            writeLegacyPose(out, 5);
        }
        BodiesSection.read(bytes.toByteArray(), game);
        var wolf = game.entities.carcasses.getFirst().pose;
        assertTrue(wolf.solved);
        assertEquals(12, wolf.boneCount);
        assertEquals(0.1f, wolf.boneRotX[0]);
        assertEquals(0.2f, wolf.boneRotX[2]);
        assertEquals(0.5f, wolf.boneRotX[8]); // old neck
        assertEquals(0.6f, wolf.boneRotX[10]); // old tail
        assertEquals(0, wolf.boneRotX[1]); // new knee
        assertEquals(0, wolf.boneRotX[11]); // new tail tip
        var human = game.entities.corpses.getFirst().pose;
        assertEquals(10, human.boneCount);
        assertEquals(0.1f, human.boneRotX[1]); // old head, new head
        assertEquals(0.2f, human.boneRotX[2]); // left arm
        assertEquals(0.3f, human.boneRotX[6]); // right arm
        assertEquals(0, human.boneRotX[3]); // forearm
        for (float y : human.boneRotY) assertEquals(0, y);
    }

    private static void writeLegacyPose(DataOutputStream out, int count) throws IOException {
        out.writeFloat(0.5f); out.writeFloat(0.2f); out.writeFloat(1.1f);
        out.writeFloat(0); out.writeFloat(0.8f);
        out.writeBoolean(true); out.writeInt(count);
        for (int b = 0; b < count; b++) {
            out.writeFloat((b + 1) / 10f);
            out.writeFloat(-(b + 1) / 10f);
        }
    }

    @Test
    void aSettledPoseAndItsCorpseRoundTripThroughARealSave() throws IOException {
        Game original = arena(4242L);
        killAndSettle(original);
        Carcass before = original.entities.carcasses.getFirst();
        HumanCorpse corpseBefore = original.entities.corpses.getFirst();

        Path save = directory.resolve("bodies.sav");
        assertTrue(SaveSystem.save(original, save));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save));

        Carcass after = loaded.entities.carcasses.getFirst();
        assertTrue(after.pose.solved, "a settled carcass must come back settled");
        assertEquals(before.pose.yaw, after.pose.yaw, 1e-5f);
        assertEquals(before.pose.pitch, after.pose.pitch, 1e-5f);
        assertEquals(before.pose.roll, after.pose.roll, 1e-5f);
        assertEquals(before.pose.pivotY, after.pose.pivotY, 1e-5f);
        assertEquals(before.pose.boneCount, after.pose.boneCount);
        for (int b = 0; b < before.pose.boneCount; b++) {
            assertEquals(before.pose.boneRotX[b], after.pose.boneRotX[b], 1e-5f,
                    "bone " + b + " rotX must survive a save");
            assertEquals(before.pose.boneRotY[b], after.pose.boneRotY[b], 0f,
                    "third-axis rotations must survive a save exactly");
            assertEquals(before.pose.boneRotZ[b], after.pose.boneRotZ[b], 1e-5f);
        }

        assertEquals(1, loaded.entities.corpses.size(), "the human corpse must come back");
        HumanCorpse corpseAfter = loaded.entities.corpses.getFirst();
        assertEquals(corpseBefore.pos.x, corpseAfter.pos.x, 1e-4f);
        assertEquals(corpseBefore.pos.y, corpseAfter.pos.y, 1e-4f);
        assertEquals(corpseBefore.pos.z, corpseAfter.pos.z, 1e-4f);
        assertEquals(corpseBefore.decay, corpseAfter.decay, 1e-3f);
        assertSame(NpcArchetype.SCAVENGER, corpseAfter.appearance.archetype);
        assertTrue(corpseAfter.appearance.raider);
        assertTrue(corpseAfter.pose.solved);
    }

    @Test
    void aSaveTakenMidFallProducesASettledCorpseRatherThanALostBody() throws IOException {
        Game game = arena(77L);
        Npc victim = game.entities.spawnNpc(game.world, "Villager", X + 2f, GROUND + 8f, Z);
        victim.hurt(victim.health + 100f, true);
        victim.vel.set(3f, 2f, 0f);
        game.entities.fastTick(game, 0.05f);
        assertEquals(1, game.ragdolls.liveCount(), "precondition: the body is still falling");

        Path save = directory.resolve("midfall.sav");
        assertTrue(SaveSystem.save(game, save));

        assertEquals(0, game.ragdolls.liveCount(),
                "saving freezes falling bodies rather than dropping them");
        assertEquals(1, game.entities.corpses.size());

        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save));
        assertEquals(1, loaded.entities.corpses.size(),
                "a save taken mid-fall must come back as a corpse, not as nothing");
        assertEquals(0, loaded.ragdolls.liveCount(),
                "falling bodies are transient and never restored");
    }

    @Test
    void anAbsentSectionLeavesCarcassesInTheFixedPoseAndNoCorpses() throws IOException {
        Game original = arena(9001L);
        killAndSettle(original);
        Path save = directory.resolve("without-bodies.sav");
        assertTrue(SaveSystem.save(original, save));
        Files.write(save, CreativeSaveSections.replace(Files.readAllBytes(save),
                BodiesSection.ID, null));

        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save),
                "a save written before this feature must still load");
        assertEquals(1, loaded.entities.carcasses.size());
        Carcass carcass = loaded.entities.carcasses.getFirst();
        assertFalse(carcass.pose.solved,
                "with no section, a carcass keeps the pose it has always been drawn in");
        assertEquals(Carcass.defaultYaw(carcass.pos.x, carcass.pos.z), carcass.pose.yaw, 1e-5f);
        assertTrue(loaded.entities.corpses.isEmpty(),
                "an older save holds no human corpses");
    }

    @Test
    void aMalformedSectionFailsTheWholeLoadAndLeavesTheLiveWorldAlone() throws IOException {
        Game game = arena(5150L);
        killAndSettle(game);
        Path save = directory.resolve("malformed-bodies.sav");
        assertTrue(SaveSystem.save(game, save));
        byte[] valid = Files.readAllBytes(save);
        byte[] section = payload(1, 1, 0);

        List<byte[]> corruptions = List.of(
                payload(3, 1, 0),                                   // unsupported version
                payload(1, 0, 0),                                   // carcass count mismatch
                Arrays.copyOf(section, section.length - 1),         // truncated
                Arrays.copyOf(section, section.length + 1),         // trailing bytes
                new byte[0]);

        var world = game.world;
        var player = game.player;
        player.health = 73f;
        for (byte[] corruption : corruptions) {
            assertThrows(IOException.class,
                    () -> BodiesSection.read(corruption, settledArena()),
                    "malformed body state must fail explicitly");
            Files.write(save, CreativeSaveSections.replace(valid, BodiesSection.ID, corruption));
            assertFalse(SaveSystem.load(game, save),
                    "a corrupt body section must reject the entire load");
            assertSame(world, game.world,
                    "failed verification cannot release the live world");
            assertSame(player, game.player);
            assertEquals(73f, game.player.health);
        }
    }

    @Test
    void aNonFinitePoseAngleIsRejectedRatherThanLoaded() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(1);
            out.writeInt(1);
            out.writeFloat(Float.NaN); // yaw
        }
        Game target = arena(5150L);
        killAndSettle(target);
        assertThrows(IOException.class, () -> BodiesSection.read(bytes.toByteArray(), target),
                "a NaN angle would load and then never compare true against anything");
    }

    /** A world that already holds one settled carcass and one settled corpse. */
    private static Game settledArena() {
        Game game = arena(5150L);
        killAndSettle(game);
        return game;
    }

    /** Kills one animal and one person and lets both bodies come to rest. */
    private static void killAndSettle(Game game) {
        Creature deer = game.entities.spawnCreature(game.world, Creature.CreatureType.DEER,
                X + 2f, GROUND + 0.1f, Z);
        deer.hurt(deer.health + 100f, true);
        deer.vel.set(2f, 1f, 0f);
        Npc raider = game.entities.spawnNpc(game.world, "Ash Raider", X - 2f, GROUND + 0.1f, Z);
        raider.raider = true;
        raider.archetype = NpcArchetype.SCAVENGER;
        raider.hurt(raider.health + 100f, true);
        raider.vel.set(-2f, 1f, 0f);
        game.entities.fastTick(game, 0.05f);
        for (int i = 0; i < 1200 && game.ragdolls.liveCount() > 0; i++) {
            game.ragdolls.update(game, 1f / 60f);
        }
    }

    /** A hand-built section payload with a chosen version and carcass count. */
    private static byte[] payload(int version, int carcasses, int corpses) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(version);
            out.writeInt(carcasses);
            for (int i = 0; i < carcasses; i++) {
                writePose(out);
            }
            out.writeInt(corpses);
        }
        return bytes.toByteArray();
    }

    private static void writePose(DataOutputStream out) throws IOException {
        out.writeFloat(0.5f);   // yaw
        out.writeFloat(0f);     // pitch
        out.writeFloat(1.45f);  // roll
        out.writeFloat(0f);     // lift
        out.writeFloat(0.79f);  // pivotY
        out.writeBoolean(true); // solved
        out.writeInt(0);        // bones
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
        return game;
    }
}
