package com.veylon;

import com.veylon.engine.AudioManager;
import com.veylon.entity.CombustionSource;
import com.veylon.entity.Creature;
import com.veylon.entity.Entity;
import com.veylon.entity.Npc;
import com.veylon.settlement.NpcArchetype;
import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;

/**
 * A flat stone arena for body-fire presentation tests: x and z 288..351, the
 * floor's top at y = 40, clear weather, no wildlife or people, the player at
 * (310, 40, 310). Public so the gfx and engine tests can stage the same place.
 */
public final class BodyFireArena {

    /** Where bodies stand. */
    public static final float FLOOR = 40f;

    private BodyFireArena() {
    }

    /** The arena, with sounds going nowhere. */
    public static Game arena() {
        return arena(new Game());
    }

    /** The arena, every sound request going to {@code audio}. */
    public static Game arena(AudioManager audio) {
        return arena(new Game(audio));
    }

    private static Game arena(Game game) {
        game.newWorld(777L, true);
        for (int cx = 18; cx <= 21; cx++) {
            for (int cz = 18; cz <= 21; cz++) {
                Chunk c = game.world.getOrCreateChunk(cx, cz);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int y = 0; y < Chunk.SY; y++) {
                            c.set(lx, y, lz, y <= 39 ? BlockType.STONE : BlockType.AIR);
                        }
                    }
                }
                c.recomputeAllHeights();
                c.rebuildLights();
            }
        }
        game.player.pos.set(310, FLOOR, 310);
        game.player.envTemp = 20f;
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        game.world.campfireFuel.clear();
        game.world.campPos = null;
        game.fire.reset();
        weather(game, Weather.CLEAR);
        game.particles.setRandomSeed(47L);
        game.particles.density = 1f;
        game.particles.count = 0;
        game.ambience.reseed(53L);
        return game;
    }

    /** Pins the weather. */
    public static void weather(Game game, Weather weather) {
        game.weather.current = weather;
        game.weather.next = weather;
        game.weather.blend = 1f;
        game.weather.changeTimer = 4000f;
    }

    public static Creature creature(Game game, Creature.CreatureType type, float x, float z) {
        Creature c = game.entities.spawnCreature(game.world, type, x, FLOOR, z);
        c.vel.zero();
        return c;
    }

    public static Npc person(Game game, NpcArchetype archetype, float x, float z) {
        Npc n = game.entities.spawnNpc(game.world, "Villager", x, FLOOR, z);
        n.archetype = archetype;
        n.vel.zero();
        return n;
    }

    /** A bottle breaking low on the body's side, the way the QA scenes set bodies alight. */
    public static void ignite(Game game, Entity e, int bottle) {
        game.combustion.ignite(game, e, CombustionSource.DIRECT_HIT, 1f, false, bottle,
                e.pos.x, e.pos.y + 0.2f, e.pos.z + e.width * 0.5f);
    }

    /** Fast ticks of the fire alone, {@code seconds} of them. */
    public static void burn(Game game, float seconds) {
        int ticks = Math.round(seconds / com.veylon.simulation.SimulationScheduler.FAST_DT);
        for (int i = 0; i < ticks; i++) {
            game.combustion.fastTick(game, com.veylon.simulation.SimulationScheduler.FAST_DT);
        }
    }
}
