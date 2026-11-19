package com.veylon.save;

import com.veylon.Game;
import com.veylon.entity.BodyFragment;
import com.veylon.entity.Npc;
import com.veylon.entity.NpcAppearance;
import com.veylon.entity.RagdollConstants;
import com.veylon.gfx.model.Animator;
import com.veylon.gfx.model.ModelPart;
import com.veylon.gfx.model.NpcModels;
import com.veylon.settlement.NpcArchetype;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the pieces of a person blown apart survive a save in {@code world.remains}: every settled
 * piece comes back where and how it lay, with the time it had left to rot and the look of the
 * person it came from, the player's own remains keep their campless look, a piece still flying is
 * saved where it lands, and one piece the reader would refuse costs that piece, not the save.
 * Until 0.9.1 these pieces were also written to {@code world.fragments}, which this class tested;
 * {@link RemainsSectionTest} covers the section itself.
 */
class PersonPiecesSaveTest {

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
    void thePlayersRemainsKeepTheirPlainCamplessLook() throws IOException {
        Game original = arena(1337L);
        original.player.pos.set(X, GROUND, Z);
        original.player.killBy(false);
        assertTrue(original.player.recordBlastDeath(X + 1f, GROUND + 0.5f, Z, KEG));
        assertEquals(10, original.fragments.spawnPlayerRemains(original, original.player).size());
        settle(original);

        Path save = directory.resolve("remains.sav");
        assertTrue(SaveSystem.save(original, save));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save));
        assertEquals(10, loaded.fragments.settledCount());
        for (BodyFragment f : loaded.fragments.settled) {
            assertEquals(NpcAppearance.NEUTRAL_CAMP_INDEX, f.appearance.campIndex,
                    "the camp index is kept as it is, so the remains stay campless");
            assertNull(f.appearance.archetype);
        }
        BodyFragment torso = loaded.fragments.settled.stream()
                .filter(f -> f.piece == BodyFragment.Piece.TORSO).findFirst().orElseThrow();
        ModelPart top = Animator.poseFragment(torso);
        assertFalse(top.find("friendlyBadge").visible, "a loaded player's torso wears no camp badge either");
        assertTrue(top.find("vest").visible);
        NpcModels.get().resetPose();
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

    private static Game arena(long seed) {
        Game game = new Game();
        game.newWorld(seed, true, World.GEN_CAVE_IDENTITIES);
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
