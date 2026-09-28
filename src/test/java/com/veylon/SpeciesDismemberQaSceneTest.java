package com.veylon;

import com.veylon.entity.BodyFamily;
import com.veylon.entity.BodyFragment;
import com.veylon.entity.Carcass;
import com.veylon.entity.Creature.CreatureType;
import com.veylon.entity.FragmentAnatomy;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The all-species dismemberment capture scenes stage what their captures are
 * read as showing: every body comes apart once, into its own family's pieces,
 * an animal's harvest record rides on its torso and is never a second whole
 * body, the camera's player is untouched, and two runs match.
 */
class SpeciesDismemberQaSceneTest {

    private static final int FLOOR = 40;
    private static final Vec3i SITE = new Vec3i(310, FLOOR, 310);

    @ParameterizedTest
    @ValueSource(strings = {"dismember_species", "dismember_species_wall", "dismember_species_close"})
    void everyBodyComesApartOnceIntoItsOwnPieces(String name) {
        Game game = arena();
        SpeciesDismemberQaScene scene = new SpeciesDismemberQaScene(game);
        scene.stage(SITE, name);
        assertEquals(CreatureType.values().length, game.entities.creatures.size(), "one of every species");
        assertEquals(1, game.entities.npcs.size(), "and a person for reference");

        scene.update(0.95);
        assertEquals(0, game.fragments.liveCount(), "nothing comes apart before the blast");
        scene.update(1.0);
        Map<BodyFamily, Integer> pieces = new EnumMap<>(BodyFamily.class);
        for (BodyFragment f : game.fragments.live) {
            pieces.merge(f.definition.family, 1, Integer::sum);
        }
        for (BodyFamily family : BodyFamily.values()) {
            int bodies = family == BodyFamily.HUMANOID ? 2 : 1; // the guard and the player's remains
            assertEquals(bodies * FragmentAnatomy.of(family).pieces.size(), pieces.getOrDefault(family, 0),
                    family + " comes apart into its own pieces, once");
        }
        assertTrue(game.entities.creatures.isEmpty() && game.entities.npcs.isEmpty(), "nobody is left standing");
        assertEquals(5, game.entities.carcasses.size(), "one harvest record per animal that leaves a carcass");
        for (Carcass c : game.entities.carcasses) {
            assertTrue(c.fragmented(), c.type + "'s record is tied to its torso and never drawn as a whole body");
        }
        assertFalse(game.player.dead, "the camera's player is not the one blown apart");
        assertEquals(Game.AppState.PLAYING, game.appState);

        scene.update(9.0);
        assertEquals(0, game.fragments.liveCount(), "every piece has come to rest");
        if (!name.equals("dismember_species")) {
            int wallZ = SITE.z() - 6 - 4;
            for (BodyFragment f : game.fragments.settled) {
                assertTrue(f.pos.z > wallZ + 1, f.definition + " is stopped by the wall, not thrown over it");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"dismember_species", "dismember_species_wall"})
    void twoRunsOfTheSceneMatch(String name) {
        List<float[]> first = run(name);
        List<float[]> second = run(name);
        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            for (int k = 0; k < 7; k++) {
                assertEquals(first.get(i)[k], second.get(i)[k], 0f, "piece " + i + " value " + k);
            }
        }
    }

    private static List<float[]> run(String name) {
        Game game = arena();
        SpeciesDismemberQaScene scene = new SpeciesDismemberQaScene(game);
        scene.stage(SITE, name);
        scene.update(2.4);
        List<float[]> out = new ArrayList<>();
        for (List<BodyFragment> list : List.of(game.fragments.live, game.fragments.settled)) {
            for (BodyFragment f : list) {
                out.add(new float[] {f.pos.x, f.pos.y, f.pos.z,
                        f.orientation.x, f.orientation.y, f.orientation.z, f.orientation.w});
            }
        }
        return out;
    }

    /** A flat stone clearing: the ground the harness levels before it stages a scene. */
    private static Game arena() {
        Game game = new Game();
        game.newWorld(20260926L, true);
        for (int cx = 18; cx <= 21; cx++) {
            for (int cz = 18; cz <= 21; cz++) {
                Chunk c = game.world.getOrCreateChunk(cx, cz);
                for (int lx = 0; lx < Chunk.SX; lx++) {
                    for (int lz = 0; lz < Chunk.SZ; lz++) {
                        for (int y = 0; y < Chunk.SY; y++) {
                            c.set(lx, y, lz, y < FLOOR ? BlockType.STONE : BlockType.AIR);
                        }
                    }
                }
                c.recomputeAllHeights();
                c.rebuildLights();
            }
        }
        game.appState = Game.AppState.PLAYING;
        return game;
    }
}
