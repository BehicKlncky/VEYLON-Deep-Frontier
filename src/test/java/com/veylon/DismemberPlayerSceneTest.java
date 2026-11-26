package com.veylon;

import com.veylon.entity.BodyFamily;
import com.veylon.entity.BodyFragment;
import com.veylon.entity.GameMode;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code VEYLON_SCENE=dismember_player} (0.9.1 milestone 07, B13) headlessly: the Survival player's
 * own scrap bomb goes off at their feet at 1 s, and the game's own death transition shows the card
 * over their remains, which come apart in the player's neutral look; a Creative player, who cannot
 * die, is refused.
 */
class DismemberPlayerSceneTest {

    private static final int FLOOR = 40;
    private static final Vec3i SITE = new Vec3i(310, FLOOR, 310);
    private static final float DT = 1f / 60f;

    @Test
    void theBombAtThePlayersFeetEndsInTheirDeathCardOverTheirRemains() {
        Game game = arena(GameMode.SURVIVAL);
        DismemberQaScene scene = new DismemberQaScene(game);
        scene.stage(SITE, "dismember_player");
        assertFalse(game.simPaused, "the world runs as in play");
        for (double t = 0; t < 0.95; t += DT) {
            scene.update(t);
            game.playStep(DT, DT);
        }
        assertEquals(Game.AppState.PLAYING, game.appState, "alive until the bomb");
        assertEquals(0, game.fragments.liveCount());
        for (double t = 0.95; t < 1.2; t += DT) {
            scene.update(t);
            game.playStep(DT, DT);
        }
        assertEquals(Game.AppState.DEATH, game.appState, "the ordinary death card");
        assertTrue(game.player.dead);
        assertEquals(10, game.fragments.liveCount() + game.fragments.settledCount(), "the player's ten pieces");
        for (BodyFragment f : game.fragments.live) {
            assertSame(BodyFamily.HUMANOID, f.definition.family);
            assertNull(f.appearance.archetype, "in the player's neutral look");
        }
    }

    @Test
    void aCreativePlayerWhoCannotDieIsRefused() {
        Game game = arena(GameMode.CREATIVE);
        DismemberQaScene scene = new DismemberQaScene(game);
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> scene.stage(SITE, "dismember_player"));
        assertTrue(refusal.getMessage().contains("Survival"), refusal.getMessage());
    }

    private static Game arena(GameMode mode) {
        Game game = new Game();
        game.newWorld(20_261_126L, true, World.GEN_CAVE_IDENTITIES, mode);
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
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        game.world.campPos = null;
        game.appState = Game.AppState.PLAYING;
        return game;
    }
}
