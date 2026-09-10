package com.veylon;

import com.veylon.engine.AudioEnvironment;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementPlanner;
import com.veylon.settlement.SettlementType;
import com.veylon.world.Biome;
import com.veylon.world.BlockType;

/** Read-only acoustic observations, using existing loaded state without planning or simulation RNG. */
final class AudioSceneState {
    private AudioSceneState() { }

    static AudioEnvironment.Zone environment(Game game, float depth) {
        int x = (int) Math.floor(game.player.pos.x), z = (int) Math.floor(game.player.pos.z);
        Settlement settlement = game.world.settlements.get(Settlement.packId(
                SettlementPlanner.regionOfBlock(x), SettlementPlanner.regionOfBlock(z)));
        boolean stone = settlement != null && settlement.containsBlock(x, z)
                && (settlement.type == SettlementType.CASTLE || settlement.type == SettlementType.FORTRESS);
        BlockType floor = game.world.getBlock(x, (int) Math.floor(game.player.pos.y) - 1, z);
        stone |= floor == BlockType.RUIN_STONE || floor == BlockType.STONE_BRICK;
        return AudioEnvironment.classify(depth, game.player.exposedToSky,
                game.player.biome == Biome.PINE_FOREST, stone);
    }
}
