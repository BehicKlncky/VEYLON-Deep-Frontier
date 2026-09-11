package com.veylon;

import com.veylon.engine.AudioEnvironment;
import com.veylon.engine.MusicMood;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementPlanner;
import com.veylon.settlement.SettlementType;
import com.veylon.world.Biome;
import com.veylon.world.BlockType;

/** Read-only acoustic observations, using existing loaded state without planning or simulation RNG. */
final class AudioSceneState {
    /** Maximum entries examined per medium-tick threat query, regardless of list size. */
    private static final int THREAT_SCAN_LIMIT = 64;
    /** Blocks around the listener that can drive music; combat targets carry slightly farther. */
    private static final float THREAT_RADIUS = 24, COMBAT_RADIUS = 48, BEACON_RADIUS = 36;
    /** Blocks below the local surface selecting deep-cave music. */
    private static final float MUSIC_CAVE_DEPTH = 18;
    private AudioSceneState() { }

    static MusicMood music(Game game, float depth) {
        var player = game.player;
        for (int i = 0, count = Math.min(THREAT_SCAN_LIMIT, game.entities.creatures.size()); i < count; i++) {
            var creature = game.entities.creatures.get(i);
            float radius = creature.targetEntity == player ? COMBAT_RADIUS : THREAT_RADIUS;
            if (!creature.dead && (creature.type.predator || creature.targetEntity == player)
                    && creature.distSqTo(player) < radius * radius) return MusicMood.THREAT;
        }
        for (int i = 0, count = Math.min(THREAT_SCAN_LIMIT, game.entities.npcs.size()); i < count; i++) {
            var npc = game.entities.npcs.get(i);
            float radius = npc.combatTarget == player ? COMBAT_RADIUS : THREAT_RADIUS;
            if (!npc.dead && !npc.abstractTravel && (npc.hostileToPlayer() || npc.combatTarget == player)
                    && npc.distSqTo(player) < radius * radius) return MusicMood.THREAT;
        }
        if (game.world.beaconStage >= 3 && game.world.beaconPos != null
                && game.world.beaconPos.distSq(player.pos.x, player.pos.y, player.pos.z) < BEACON_RADIUS * BEACON_RADIUS)
            return MusicMood.BEACON;
        if (depth >= MUSIC_CAVE_DEPTH) return MusicMood.DEEP_CAVE;
        if (game.time.isNight()) return game.time.day() == 1 ? MusicMood.FIRST_NIGHT : MusicMood.NIGHT;
        return MusicMood.CALM;
    }

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
