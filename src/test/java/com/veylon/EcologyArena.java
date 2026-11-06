package com.veylon;

import com.veylon.ai.VeilmawAI;
import com.veylon.entity.Creature;
import com.veylon.world.World;
import com.veylon.world.urban.Habitat;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;

/**
 * A flat stone arena far from the legacy camp with one growth node on it, its membrane carpet
 * around it and a Veilmaw folded beside it, for the alien's mechanics tests (urban milestone 15).
 * Only the geometry and the body are staged; every tick runs the production systems in the
 * game's own order. Clear weather at noon, so rain never puts out a test fire.
 */
final class EcologyArena {
    /** Fast ticks run per game, so a medium tick follows every tenth one however the calls are split. */
    private static final java.util.Map<Game, int[]> TICKS = new java.util.WeakHashMap<>();
    /** The floor's top is y 39; feet stand at y 40. */
    static final int FEET = 40;
    static final Vec3i NODE = new Vec3i(310, FEET, 310);
    static final Vec3i REST = new Vec3i(311, FEET, 310);

    private EcologyArena() {
    }

    static Game create(long seed) {
        Game g = new Game();
        g.newWorld(seed, true, World.GEN_CAVE_IDENTITIES);
        for (int cx = 17; cx <= 21; cx++) {
            for (int cz = 17; cz <= 21; cz++) {
                Chunk c = g.world.getOrCreateChunk(cx, cz);
                for (int lx = 0; lx < Chunk.SX; lx++) {
                    for (int lz = 0; lz < Chunk.SZ; lz++) {
                        for (int y = 0; y < Chunk.SY; y++) {
                            c.set(lx, y, lz, y < FEET ? BlockType.STONE : BlockType.AIR);
                        }
                    }
                }
                c.recomputeAllHeights();
                c.rebuildLights();
            }
        }
        g.entities.creatures.clear();
        g.entities.npcs.clear();
        g.entities.carcasses.clear();
        g.entities.corpses.clear();
        g.time.totalMinutes = 12 * 60;
        g.weather.current = g.weather.next = Weather.CLEAR;
        g.weather.blend = 1f;
        g.weather.changeTimer = 100_000f;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz <= 12) {
                    g.world.setBlock(NODE.x() + dx, FEET - 1, NODE.z() + dz, BlockType.VEIL_MEMBRANE, false);
                }
            }
        }
        g.world.setBlock(NODE.x(), NODE.y(), NODE.z(), BlockType.GROWTH_NODE, false);
        g.player.pos.set(330.5f, FEET, 330.5f);
        g.player.vel.zero();
        g.player.onGround = true;
        return g;
    }

    /** A Veilmaw folded at the rest cell, guarding the arena's node. */
    static Creature veilmaw(Game g) {
        Creature c = g.entities.spawnCreature(g.world, Creature.CreatureType.VEILMAW,
                REST.x() + 0.5f, FEET, REST.z() + 0.5f);
        c.home = Habitat.territory(NODE, REST, VeilmawAI.TERRITORY);
        c.onGround = true;
        return c;
    }

    /** Puts the player at a cell centre, standing, still. */
    static void stand(Game g, float x, float z) {
        g.player.pos.set(x, FEET, z);
        g.player.vel.zero();
        g.player.onGround = true;
    }

    /** Runs whole fast ticks, with a medium tick every half second, in the game's order. */
    static void run(Game g, float seconds) {
        int ticks = Math.round(seconds / SimulationScheduler.FAST_DT);
        for (int i = 0; i < ticks; i++) {
            tick(g);
        }
    }

    /**
     * One fast tick and, every tenth, a medium tick, then the per-frame body systems of
     * {@code Game.advanceWorld} (ragdolls, fragments, burn residue, noise ageing). The slow tick
     * is left out on purpose: in this legacy arena it would spawn wildlife into the test.
     */
    private static void tick(Game g) {
        float dt = SimulationScheduler.FAST_DT;
        g.fastTick(dt);
        if (++TICKS.computeIfAbsent(g, k -> new int[1])[0] % 10 == 0) {
            g.mediumTick(SimulationScheduler.MEDIUM_DT);
        }
        g.ragdolls.update(g, dt);
        g.fragments.update(g, dt);
        g.burnResidues.update(g, dt);
        g.noise.update(dt);
    }

    /** Like {@link #run}, but keeps the player where they stand (no gravity drift or knockback). */
    static void hold(Game g, float x, float z, float seconds) {
        int ticks = Math.round(seconds / SimulationScheduler.FAST_DT);
        for (int i = 0; i < ticks; i++) {
            stand(g, x, z);
            tick(g);
        }
    }
}
