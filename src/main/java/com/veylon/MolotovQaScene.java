package com.veylon;

import com.veylon.combat.WeaponDefinition;
import com.veylon.combat.WeaponRegistry;
import com.veylon.entity.RagdollConstants;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.simulation.WeatherSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;

import java.util.Locale;

/**
 * Opt-in capture scenes for the fire bomb. Inert in an ordinary world.
 *
 * <p>Each throws one real fire bomb through {@code ProjectileSystem.fire} at
 * 0.5 s onto the dry grass clearing the harness has built, at dusk, so the
 * burning liquid stands out against the ground. It is thrown from a standing
 * player's eye height, 6.5 blocks east of where it lands and across the
 * camera's view, so the pool's run along the throw is seen from the side
 * rather than end-on. {@code molotov_ground} is just that. {@code molotov_tree}
 * stands a round tree the way the generator grows one, a four-log trunk under
 * a leaf canopy, two blocks along the throw from where the bottle lands, so
 * the pool runs around its trunk, and steps back to keep the canopy in frame.
 * {@code molotov_rain} is the ground scene with the weather turned to rain at
 * 3 s.
 *
 * <p>Like {@code DismemberQaScene}'s bomb scene it keeps the simulation
 * paused and runs what the bottle touches itself, in fixed 1/60 s steps
 * tied to elapsed wall-clock seconds snapped to a tenth: the projectile and
 * the particles every step, block fire and then the liquid on the scheduler's
 * half-second medium tick, and the ambient emitters that draw the flames,
 * smoke and steam. A capture at a given second shows the same moment every run
 * of one seed; only the sheet's shimmer follows the wall clock.
 */
final class MolotovQaScene {

    /** Wall-clock second the bottle is thrown. */
    private static final double THROW_AT = 0.5;
    /** Wall-clock second the rain starts in {@code molotov_rain}. */
    private static final double RAIN_AT = 3.0;
    /** Staged time advances in these steps, so a shot lands on a known moment. */
    private static final double STAGE_GRID = 0.1;
    private static final float STEP = RagdollConstants.FIXED_STEP;
    private static final int STEPS_PER_MEDIUM_TICK = Math.round(SimulationScheduler.MEDIUM_DT / STEP);
    /** Low, warm sun with enough light left to read the ground. */
    private static final double DUSK_HOUR = 19.5;
    /** Blocks north of the camera and east of it to the cell the bottle is thrown at. */
    private static final int TARGET_AHEAD = 7, TARGET_RIGHT = 1;
    /**
     * The bottle flies west, from the right of the frame: on the left the
     * HUD's log and vitals panels would hide it.
     */
    private static final int THROW_X = -1;
    /** How far from that cell, back along the throw, the thrower stands. */
    private static final float THROW_REACH = 6.5f;
    /** Camera feet above the floor, and how far it looks down. */
    private static final float CAMERA_RISE = 1.6f, CAMERA_PITCH = 20f;
    /** The tree scene's camera stands further back and looks up more, for the canopy. */
    private static final float TREE_CAMERA_BACK = 2f, TREE_CAMERA_PITCH = 8f;
    /** Trunk offset from the target cell: two along the throw, one to the north. */
    private static final int TREE_ALONG = 2, TREE_ASIDE = -1;
    private static final int TREE_TRUNK = 4;
    private static final long SCENE_PARTICLE_SEED = 0x4d4f4c4f544f56L;
    private static final long SCENE_THROW_SEED = 0x424f54544c45L;
    private static final long SCENE_FIRE_SEED = 0x464c414d45L;

    private final Game game;
    private boolean active;
    private boolean rain;
    private boolean thrown;
    private boolean raining;
    private boolean reportedBreak;
    private boolean reportedTrunk;
    private boolean reportedCanopy;
    private boolean reportedOut;
    private double simulated;
    private int steps;
    private int floor;
    private int targetX, targetZ;

    MolotovQaScene(Game game) {
        this.game = game;
    }

    /**
     * The scene named {@code scene}, on the flat clearing the harness has
     * already built at {@code site}.
     */
    void stage(Vec3i site, String scene) {
        game.time.totalMinutes = (long) (DUSK_HOUR * 60);
        setWeather(WeatherSystem.Weather.CLEAR);
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        game.entities.carcasses.clear();
        game.entities.corpses.clear();
        game.entities.tracks.clear();
        game.projectiles.reset();
        game.fire.reset();
        game.liquidFire.reset();
        game.fire.setRandomSeed(SCENE_FIRE_SEED);
        game.liquidFire.setRandomSeed(SCENE_FIRE_SEED);
        game.projectiles.setRandomSeed(SCENE_THROW_SEED);
        game.particles.count = 0;
        game.particles.setRandomSeed(SCENE_PARTICLE_SEED);
        game.player.inventory.set(0, null);
        game.player.hotbarSel = 0;
        game.simPaused = true;

        boolean tree = scene.equals("molotov_tree");
        rain = scene.equals("molotov_rain");
        floor = site.y();
        targetX = site.x() + TARGET_RIGHT;
        targetZ = site.z() - TARGET_AHEAD;
        if (tree) {
            stageTree(targetX + TREE_ALONG * THROW_X, floor, targetZ + TREE_ASIDE);
        }
        // A raised, paused viewpoint: the simulation never moves the player.
        game.player.pos.set(site.x() + 0.5f, floor + CAMERA_RISE,
                site.z() + 0.5f + (tree ? TREE_CAMERA_BACK : 0f));
        game.player.vel.zero();
        game.camera.yaw = 0f;
        game.camera.pitch = tree ? TREE_CAMERA_PITCH : CAMERA_PITCH;

        simulated = 0;
        steps = 0;
        thrown = raining = false;
        reportedBreak = reportedTrunk = reportedCanopy = reportedOut = false;
        active = true;
        update(0);
    }

    /** A round tree as {@code WorldGenerator.placeTree} grows one outside pine country. */
    private void stageTree(int x, int y, int z) {
        for (int i = 0; i < TREE_TRUNK; i++) {
            game.world.setBlock(x, y + i, z, BlockType.LOG, false);
        }
        int cy = y + TREE_TRUNK;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int man = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (man <= 3 && !(dx == 0 && dz == 0 && dy <= 0)) {
                        game.world.setBlock(x + dx, cy + dy, z + dz, BlockType.LEAVES, false);
                    }
                }
            }
        }
        game.world.setBlock(x, cy + 1, z, BlockType.LEAVES, false);
    }

    /** Advances the staged moment the elapsed second maps to. */
    void update(double elapsed) {
        if (!active) {
            return;
        }
        double staged = Math.floor(elapsed / STAGE_GRID + 1e-6) * STAGE_GRID;
        while (simulated < staged - 1e-6) {
            step();
        }
    }

    /** One fixed step of everything the bottle and its fire touch, in the frame's order. */
    private void step() {
        if (!thrown && simulated >= THROW_AT - 1e-6) {
            throwBottle();
        }
        if (rain && !raining && simulated >= RAIN_AT - 1e-6) {
            raining = true;
            setWeather(WeatherSystem.Weather.RAIN);
            System.out.printf(Locale.ROOT, "[scene] molotov: rain from %.2f s, %d cells alight%n",
                    simulated, game.liquidFire.count());
        }
        game.projectiles.update(game, STEP);
        if (++steps % STEPS_PER_MEDIUM_TICK == 0) {
            game.fire.mediumTick(game, SimulationScheduler.MEDIUM_DT);
            game.liquidFire.mediumTick(game, SimulationScheduler.MEDIUM_DT);
        }
        game.particles.update(STEP, game.world);
        game.ambience.updateEmitters(STEP);
        simulated += STEP;
        report();
    }

    /**
     * Throws a fire bomb as the player, from a standing eye height east of
     * the target cell, on the flatter of the two arcs that reach its floor.
     */
    private void throwBottle() {
        thrown = true;
        WeaponDefinition fireBomb = WeaponRegistry.byId("fire_bomb");
        float tx = targetX + 0.5f, tz = targetZ + 0.5f;
        float ox = tx - THROW_X * THROW_REACH;
        float oy = floor + game.player.eyeHeight();
        float dy = floor - oy;
        float speed = fireBomb.projectileSpeed;
        float k = fireBomb.projectileGravity * THROW_REACH * THROW_REACH / (2f * speed * speed);
        float slope = (THROW_REACH - (float) Math.sqrt(THROW_REACH * THROW_REACH - 4f * k * (k + dy)))
                / (2f * k);
        float norm = (float) Math.sqrt(1f + slope * slope);
        game.projectiles.fire(game, game.player, true, ox, oy, tz,
                THROW_X / norm, slope / norm, 0f, fireBomb, null);
        System.out.printf(Locale.ROOT, "[scene] molotov: bottle thrown %.1f blocks at %.2f s%n",
                THROW_REACH, simulated);
    }

    /** Logs, once each, the moments a capture is timed against. */
    private void report() {
        if (!reportedBreak && thrown && game.liquidFire.totalSpills > 0) {
            reportedBreak = true;
            System.out.printf(Locale.ROOT, "[scene] molotov: bottle broke at %.2f s, %d cells alight%n",
                    simulated, game.liquidFire.count());
        }
        if (!reportedTrunk && game.fire.count() > 0) {
            reportedTrunk = true;
            System.out.printf(Locale.ROOT, "[scene] molotov: first block alight at %.2f s%n", simulated);
        }
        if (!reportedCanopy && reportedTrunk && leavesBurning()) {
            reportedCanopy = true;
            System.out.printf(Locale.ROOT, "[scene] molotov: canopy alight at %.2f s%n", simulated);
        }
        if (!reportedOut && reportedBreak && game.liquidFire.count() == 0) {
            reportedOut = true;
            System.out.printf(Locale.ROOT, "[scene] molotov: pool out at %.2f s%n", simulated);
        }
    }

    private boolean leavesBurning() {
        for (Vec3i p : game.fire.burningCells()) {
            if (game.world.getBlock(p.x(), p.y(), p.z()) == BlockType.LEAVES) {
                return true;
            }
        }
        return false;
    }

    /** Pins weather so a capture is not blended mid-transition. */
    private void setWeather(WeatherSystem.Weather w) {
        game.weather.current = w;
        game.weather.next = w;
        game.weather.blend = 1f;
        game.weather.changeTimer = 4000f;
    }
}
