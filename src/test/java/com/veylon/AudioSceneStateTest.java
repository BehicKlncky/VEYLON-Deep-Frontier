package com.veylon;

import com.veylon.engine.AudioEnvironment;
import com.veylon.engine.MusicMood;
import com.veylon.entity.Creature;
import com.veylon.entity.Npc;
import com.veylon.util.Vec3i;
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

    @Test void musicReadsDayDepthBeaconAndThreatWithoutGeneratingWorldState() {
        var game = new Game(); game.world = new World(42); game.player = new Player(game.world);
        game.player.pos.set(10, 80, 10);
        assertEquals(MusicMood.CALM, AudioSceneState.music(game, 0));
        game.time.totalMinutes = 23 * 60;
        assertEquals(MusicMood.FIRST_NIGHT, AudioSceneState.music(game, 0));
        game.time.totalMinutes += 24 * 60;
        assertEquals(MusicMood.NIGHT, AudioSceneState.music(game, 0));
        assertEquals(MusicMood.DEEP_CAVE, AudioSceneState.music(game, 30));
        game.world.beaconStage = 3; game.world.beaconPos = new Vec3i(12, 80, 10);
        assertEquals(MusicMood.BEACON, AudioSceneState.music(game, 30));
        var wolf = new Creature(game.world, Creature.CreatureType.WOLF); wolf.pos.set(20, 80, 10);
        game.entities.creatures.add(wolf);
        assertEquals(MusicMood.THREAT, AudioSceneState.music(game, 30));
        wolf.dead = true; assertEquals(MusicMood.BEACON, AudioSceneState.music(game, 30));
        assertEquals(0, game.world.loadedCount()); assertTrue(game.world.settlements.isEmpty());
    }

    @Test void combatTargetsAreThreatsButDeadAndAbstractNpcsAreIgnored() {
        var game = new Game(); game.world = new World(42); game.player = new Player(game.world);
        var npc = new Npc(game.world, "audio observer"); npc.pos.set(game.player.pos).add(30, 0, 0); npc.combatTarget = game.player;
        game.entities.npcs.add(npc);
        assertEquals(MusicMood.THREAT, AudioSceneState.music(game, 0));
        npc.abstractTravel = true; assertEquals(MusicMood.CALM, AudioSceneState.music(game, 0));
        npc.abstractTravel = false; npc.dead = true; assertEquals(MusicMood.CALM, AudioSceneState.music(game, 0));
        assertEquals(0, game.world.loadedCount());
    }
}
