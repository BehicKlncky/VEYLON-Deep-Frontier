package com.veylon;

import com.veylon.entity.Creature;
import com.veylon.entity.Npc;
import com.veylon.entity.RagdollConstants;
import com.veylon.settlement.NpcArchetype;
import com.veylon.simulation.WeatherSystem;
import com.veylon.util.Vec3i;

/**
 * Opt-in capture scenes for death ragdolls and the blood burst. Inert in an
 * ordinary world.
 *
 * <p>{@code death_ragdoll_showcase} kills four animals and one person from
 * known directions on one frame and then advances the solver in fixed steps
 * tied to elapsed wall-clock seconds, so a capture at second N always shows the
 * same moment of the same fall. {@code death_ragdoll_sequence} does the same to
 * a single wolf, side-on and close, for frame-by-frame inspection of one body.
 *
 * <p>The frozen-step approach is the one {@code rain_impact} uses and for the
 * same reason: a two-second tumble is too short to inspect at live frame
 * cadence, and a screenshot scheduled by wall clock would otherwise land
 * somewhere different on every run.
 */
final class RagdollQaScene {

    /** Wall-clock seconds each staged shot corresponds to. */
    private static final double[] SHOT_TIMES = {0.0, 0.25, 0.8, 2.0, 5.0};
    private static final long SCENE_PARTICLE_SEED = 0x424c4f4f44L;
    /** Deterministic impulses, in blocks/second, one per staged victim. */
    private static final float[][] IMPULSES = {
            {6.5f, 3.2f, 0f},     // struck from the west, thrown east
            {-6.5f, 3.2f, 0f},    // struck from the east
            {0f, 5.5f, 6.0f},     // blown upward and back
            {4.2f, 1.2f, 4.2f},   // glancing diagonal
    };

    private final Game game;
    private boolean active;
    private boolean singleBody;
    private double simulated;

    RagdollQaScene(Game game) {
        this.game = game;
    }

    /** A cleared meadow with four animals and one settler killed at once. */
    void stage(Vec3i site) {
        prepare(site);
        Creature.CreatureType[] species = {
                Creature.CreatureType.DEER, Creature.CreatureType.WOLF,
                Creature.CreatureType.HARE, Creature.CreatureType.THORNHORN,
        };
        for (int i = 0; i < species.length; i++) {
            int x = site.x() - 6 + i * 4;
            int z = site.z() - 11;
            Creature c = game.entities.spawnCreature(game.world, species[i],
                    x + 0.5f, game.world.surfaceHeight(x, z) + 1.05f, z + 0.5f);
            c.yaw = 90f + i * 40f;
            kill(c, IMPULSES[i]);
        }
        int nx = site.x() + 8;
        int nz = site.z() - 11;
        Npc settler = game.entities.spawnNpc(game.world, "Ash Raider",
                nx + 0.5f, game.world.surfaceHeight(nx, nz) + 1.05f, nz + 0.5f);
        settler.raider = true;
        settler.archetype = NpcArchetype.SCAVENGER;
        settler.yaw = 200f;
        kill(settler, new float[]{-5.5f, 3.0f, 1.5f});

        game.entities.fastTick(game, 0f);
        active = true;
        game.camera.yaw = 0f;
        game.camera.pitch = 8f;
        update(0);
    }

    /** One wolf, side-on and close, so a single body can be read frame by frame. */
    void stageSequence(Vec3i site) {
        prepare(site);
        int x = site.x();
        int z = site.z() - 6;
        Creature wolf = game.entities.spawnCreature(game.world, Creature.CreatureType.WOLF,
                x + 0.5f, game.world.surfaceHeight(x, z) + 1.05f, z + 0.5f);
        wolf.yaw = 90f;
        kill(wolf, new float[]{7.5f, 3.4f, 0f});
        game.entities.fastTick(game, 0f);
        active = true;
        singleBody = true;
        game.camera.yaw = 0f;
        game.camera.pitch = 10f;
        update(0);
    }

    private void prepare(Vec3i site) {
        game.time.totalMinutes = (long) (15.8 * 60);
        game.weather.current = WeatherSystem.Weather.CLEAR;
        game.weather.next = WeatherSystem.Weather.CLEAR;
        game.weather.blend = 1f;
        game.weather.changeTimer = 4000f;
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        game.entities.carcasses.clear();
        game.entities.corpses.clear();
        game.entities.tracks.clear();
        game.ragdolls.reset();
        game.particles.count = 0;
        game.particles.density = 1f;
        game.particles.setRandomSeed(SCENE_PARTICLE_SEED);
        game.player.inventory.set(0, null);
        game.player.hotbarSel = 0;
        game.simPaused = true;
        // Kept so the scene reads as a staged clearing rather than a lucky spot.
        game.player.pos.set(site.x() + 0.5f, site.y(), site.z() + 0.5f);
        simulated = 0;
    }

    /** Kills outright and sets the launch velocity the solver reads. */
    private static void kill(com.veylon.entity.Entity e, float[] impulse) {
        e.hurt(e.health + 1000f, true);
        e.vel.set(impulse[0], impulse[1], impulse[2]);
    }

    /**
     * Advances the solver to the frozen moment the elapsed second maps to. The
     * scene keeps the simulation paused, so nothing else in the world moves
     * between shots and two runs of the same seed match.
     */
    void update(double elapsed) {
        if (!active) {
            return;
        }
        double target = SHOT_TIMES[Math.min(SHOT_TIMES.length - 1,
                Math.max(0, (int) Math.floor(elapsed)))];
        while (simulated < target - 1e-6) {
            game.ragdolls.update(game, RagdollConstants.FIXED_STEP);
            game.particles.update(RagdollConstants.FIXED_STEP, game.world);
            simulated += RagdollConstants.FIXED_STEP;
        }
        if (singleBody && game.ragdolls.liveCount() == 0 && simulated >= target) {
            // Nothing further to show once the one body has settled.
            active = target < SHOT_TIMES[SHOT_TIMES.length - 1];
        }
    }
}
