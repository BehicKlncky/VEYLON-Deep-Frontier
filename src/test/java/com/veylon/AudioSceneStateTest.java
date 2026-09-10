package com.veylon;

import com.veylon.engine.AudioEnvironment;
import com.veylon.entity.Player;
import com.veylon.world.Biome;
import com.veylon.world.World;
import com.veylon.world.BlockType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AudioSceneStateTest {
    @Test void acousticQueriesDoNotGenerateChunksOrPlanSettlements() {
        Game game = new Game();
        game.world = new World(42);
        game.player = new Player(game.world);
        game.player.pos.set(-4000, 80, -4000);
        game.player.biome = Biome.PINE_FOREST;
        game.player.exposedToSky = true;
        int loaded = game.world.loadedCount(), settlements = game.world.settlements.size();
        assertEquals(AudioEnvironment.Zone.FOREST, AudioSceneState.environment(game, 0));
        assertEquals(loaded, game.world.loadedCount());
        assertEquals(settlements, game.world.settlements.size());
    }

    @Test void ruinedStoneFloorAndUndergroundDepthSelectDifferentSpaces() {
        Game game = new Game();
        game.world = new World(42);
        game.player = new Player(game.world);
        game.player.pos.set(1, 90, 1);
        game.world.getOrCreateChunk(0, 0);
        game.world.setBlock(1, 89, 1, BlockType.RUIN_STONE, false);
        assertEquals(AudioEnvironment.Zone.STONE_STRUCTURE, AudioSceneState.environment(game, 0));
        assertEquals(AudioEnvironment.Zone.DEEP_CAVE, AudioSceneState.environment(game, 30));
    }
}

