package com.veylon.entity;

import com.veylon.Game;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;

/**
 * A private flat arena far from the generated camp, so a body falls onto known
 * ground rather than whatever the seed happened to put underfoot.
 *
 * <p>Same shape as {@code CombatSystemsTest.setUp}: four chunks filled to stone
 * at y = 40, the player standing on top, nothing else alive.
 */
final class RagdollTestArena {

    /** Ground surface of the arena; a body standing on it has its feet here. */
    static final float GROUND = 40f;
    static final float CENTER_X = 310.5f;
    static final float CENTER_Z = 310.5f;

    private RagdollTestArena() {
    }

    static Game create(long seed) {
        Game game = new Game();
        game.newWorld(seed, true);
        flatten(game, 18, 21, 18, 21);
        game.player.pos.set(CENTER_X, GROUND + 0.1f, CENTER_Z);
        game.player.vel.zero();
        game.player.onGround = true;
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        game.entities.carcasses.clear();
        game.entities.corpses.clear();
        game.entities.tracks.clear();
        game.ragdolls.reset();
        game.particles.count = 0;
        return game;
    }

    static void flatten(Game game, int cx0, int cx1, int cz0, int cz1) {
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
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
    }

    /** Runs the real per-frame solver until every body has settled. */
    static int settleAll(Game game) {
        int frames = 0;
        while (game.ragdolls.liveCount() > 0 && frames < 1200) {
            game.ragdolls.update(game, 1f / 60f);
            frames++;
        }
        return frames;
    }

    /** Kills an entity outright with a known impulse, the way a real blow does. */
    static void kill(Entity e, float impulseX, float impulseY, float impulseZ) {
        e.hurt(e.health + 1000f, true);
        e.vel.set(impulseX, impulseY, impulseZ);
    }
}
