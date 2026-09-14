package com.veylon;

import com.veylon.engine.AudioManager;
import com.veylon.entity.GameMode;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;

/** Shared deterministic arena, separated from generated camp and settlement mechanics. */
final class CreativeTestArena {
    private CreativeTestArena() { }

    static Game create(GameMode mode) { return create(mode, new AudioManager()); }

    static Game create(GameMode mode, AudioManager audio) {
        Game game = new Game(audio);
        game.newWorld(20260910L, true, mode);
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        game.entities.carcasses.clear();
        game.player.inventory.clear();
        game.fire.reset();
        game.noise.reset();
        for (int cx = 19; cx <= 20; cx++) {
            for (int cz = 19; cz <= 20; cz++) {
                Chunk chunk = game.world.getOrCreateChunk(cx, cz);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y < Chunk.SY; y++) {
                            chunk.set(x, y, z, y < 40 ? BlockType.STONE : BlockType.AIR);
                        }
                    }
                }
                chunk.recomputeAllHeights();
                chunk.rebuildLights();
            }
        }
        game.player.pos.set(310.5f, 40.001f, 310.5f);
        game.player.vel.zero();
        game.player.onGround = true;
        game.camera.position.set(310.5f, 41.6f, 310.5f);
        game.camera.yaw = 0;
        game.camera.pitch = 0;
        return game;
    }
}
