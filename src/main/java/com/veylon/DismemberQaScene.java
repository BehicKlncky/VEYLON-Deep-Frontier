package com.veylon;

import com.veylon.combat.ExplosionSystem;
import com.veylon.combat.WeaponDefinition;
import com.veylon.combat.WeaponRegistry;
import com.veylon.entity.Npc;
import com.veylon.entity.RagdollConstants;
import com.veylon.settlement.NpcArchetype;
import com.veylon.simulation.WeatherSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Opt-in capture scenes for dismemberment. Inert in an ordinary world.
 *
 * <p>{@code dismember_showcase} stands four people of different archetypes in
 * a row facing the camera and, one second in, blows each apart with a blast
 * 1.5 blocks in front of them, so the pieces fly away from the camera and the
 * fronts of the pieces, their colours and their kit are what it sees.
 * {@code dismember_wall} is the same with a stone wall three blocks behind the
 * row, for pieces striking a wall and coming to rest against it.
 *
 * <p>{@code dismember_bomb} is the whole chain in production code: at once the
 * player throws a real scrap bomb through {@code ProjectileSystem.fire} into a
 * gap in a ring of five people, with a sixth standing outside the lethal
 * radius. The fuse, the blast and the deaths run through the projectile,
 * explosion and entity systems, the entity tick at its real 20 Hz, so the
 * bomb goes off at 2.4 s and whoever it kills is blown apart by the death
 * pipeline itself. Everyone faces the camera and stands still, as if talking.
 *
 * <p>Like {@code RagdollQaScene} it keeps the simulation paused and advances
 * the pieces in fixed steps tied to elapsed wall-clock seconds, snapped to a
 * tenth of a second, so a capture requested at 1.2 s always shows exactly
 * 0.2 s of flight and two runs of the same seed match.
 */
final class DismemberQaScene {

    /** Wall-clock second the row is blown apart. */
    private static final double BLAST_AT = 1.0;
    /** Staged time advances in these steps, so a shot lands on a known moment. */
    private static final double STAGE_GRID = 0.1;
    /** A scrap bomb's power. */
    private static final float BLAST_STRENGTH = 2.6f;
    /** How far in front of each person their blast goes off. */
    private static final float BLAST_AHEAD = 1.5f;
    /** Blast height above the feet: a powder keg's centre, or a bomb on its first bounce. */
    private static final float BLAST_HEIGHT = 0.5f;
    private static final NpcArchetype[] ROW = {
            NpcArchetype.GUARD, NpcArchetype.TRADER, NpcArchetype.BRUTE, NpcArchetype.LEADER,
    };
    private static final float ROW_SPACING = 2.0f;
    /** Blocks from the staged site to the row. */
    private static final int ROW_DISTANCE = 5;
    /**
     * How far behind the site the camera stands and how high above the floor,
     * so all four bodies clear the HUD panels at 1280×720.
     */
    private static final float CAMERA_BACK = 0.8f, CAMERA_RISE = 0.8f;
    /** Blocks from the row to the wall behind it, in {@code dismember_wall}. */
    private static final int WALL_BEHIND = 3;
    private static final long SCENE_PARTICLE_SEED = 0x44495353454354L;

    /**
     * The bomb scene's people, around the point the bomb is thrown at: x and z
     * offsets and archetype. None stands between the camera and that point,
     * so the bomb reaches the ground there; the last stands outside the lethal
     * radius.
     */
    private static final float[][] GROUP = {
            {-2.0f, 0.3f}, {2.0f, 0.3f}, {-1.3f, -1.8f}, {1.4f, -1.7f}, {0.1f, -2.4f}, {3.6f, -4.0f},
    };
    private static final NpcArchetype[] GROUP_ROLES = {
            NpcArchetype.GUARD, NpcArchetype.TRADER, NpcArchetype.SCOUT, NpcArchetype.BRUTE,
            NpcArchetype.LEADER, NpcArchetype.FARMER,
    };
    /**
     * Blocks from the staged site to the point the bomb is thrown at: beyond
     * the scrap bomb's damage reach of the camera, so the blast neither hurts
     * nor pushes the player the capture looks through.
     */
    private static final int BOMB_DISTANCE = 7;
    /** Physics steps per entity tick: the scheduler's 20 Hz over the 60 Hz step. */
    private static final int STEPS_PER_ENTITY_TICK = 3;
    /** Keeps the bomb scene's people facing the camera and standing still. */
    private static final float HOLD_STILL = 60f;
    private static final long SCENE_THROW_SEED = 0x424f4d42L;

    private final Game game;
    private final List<Npc> row = new ArrayList<>();
    private boolean active;
    private boolean bomb;
    private boolean blown;
    private boolean reportedRest;
    private double simulated;
    private double blastAt;
    private int steps;

    DismemberQaScene(Game game) {
        this.game = game;
    }

    /**
     * The scene named {@code scene}, on the flat clearing the harness has
     * already built at {@code site}.
     */
    void stage(Vec3i site, String scene) {
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
        game.fragments.reset();
        game.particles.count = 0;
        game.particles.density = 1f;
        game.particles.setRandomSeed(SCENE_PARTICLE_SEED);
        game.player.inventory.set(0, null);
        game.player.hotbarSel = 0;
        game.simPaused = true;

        int floor = site.y();
        int rowZ = site.z() - ROW_DISTANCE;
        if (scene.equals("dismember_wall")) {
            int wallZ = rowZ - WALL_BEHIND;
            for (int x = site.x() - 9; x <= site.x() + 9; x++) {
                for (int y = floor; y < floor + 3; y++) {
                    game.world.setBlock(x, y, wallZ, BlockType.STONE, false);
                }
            }
        }
        // A raised, paused viewpoint: the simulation never runs, so it stays put.
        game.player.pos.set(site.x() + 0.5f, floor + CAMERA_RISE, site.z() + 0.5f + CAMERA_BACK);
        game.camera.yaw = 0f;
        game.camera.pitch = 20f;

        bomb = scene.equals("dismember_bomb");
        row.clear();
        if (bomb) {
            stageGroup(site.x() + 0.5f, floor, site.z() - BOMB_DISTANCE + 0.5f);
        } else {
            for (int i = 0; i < ROW.length; i++) {
                float x = site.x() + 0.5f + (i - (ROW.length - 1) * 0.5f) * ROW_SPACING;
                Npc n = game.entities.spawnNpc(game.world, ROW[i].displayName, x, floor, rowZ + 0.5f);
                n.archetype = ROW[i];
                n.yaw = 180f; // facing the camera
                n.vel.zero();
                row.add(n);
            }
        }
        simulated = 0;
        steps = 0;
        blown = false;
        reportedRest = false;
        active = true;
        update(0);
    }

    /**
     * Stands the group around {@code (tx, floor, tz)} and throws a scrap bomb
     * at that point from the player's eye, on the flatter of the two arcs that
     * reach it.
     */
    private void stageGroup(float tx, int floor, float tz) {
        for (int i = 0; i < GROUP.length; i++) {
            NpcArchetype role = GROUP_ROLES[i];
            Npc n = game.entities.spawnNpc(game.world, role.displayName,
                    tx + GROUP[i][0], floor, tz + GROUP[i][1]);
            n.archetype = role;
            n.maxHealth = role.maxHealth;
            n.health = role.maxHealth;
            n.yaw = 180f;
            n.interactFreeze = HOLD_STILL;
        }
        WeaponDefinition scrapBomb = WeaponRegistry.byId("scrap_bomb");
        float ox = game.player.pos.x;
        float oy = game.player.pos.y + game.player.eyeHeight();
        float oz = game.player.pos.z;
        float dx = tx - ox, dy = floor - oy, dz = tz - oz;
        float reach = (float) Math.sqrt(dx * dx + dz * dz);
        float speed = scrapBomb.projectileSpeed;
        float k = scrapBomb.projectileGravity * reach * reach / (2f * speed * speed);
        float slope = (reach - (float) Math.sqrt(reach * reach - 4f * k * (k + dy))) / (2f * k);
        float norm = (float) Math.sqrt(1f + slope * slope);
        game.projectiles.setRandomSeed(SCENE_THROW_SEED);
        game.projectiles.fire(game, game.player, true, ox, oy, oz,
                dx / reach / norm, slope / norm, dz / reach / norm, scrapBomb, null);
        System.out.printf(Locale.ROOT, "[scene] dismember_bomb: %d people, bomb thrown %.2f blocks,"
                        + " lethal radius %.2f%n", GROUP.length, reach,
                BLAST_STRENGTH * ExplosionSystem.LETHAL_RADIUS_FACTOR);
    }

    /** Advances the staged moment the elapsed second maps to. */
    void update(double elapsed) {
        if (!active) {
            return;
        }
        double staged = Math.floor(elapsed / STAGE_GRID + 1e-6) * STAGE_GRID;
        if (bomb) {
            while (simulated < staged - 1e-6) {
                stepBomb();
            }
            return;
        }
        if (!blown && staged >= BLAST_AT - 1e-6) {
            blowApart();
        }
        if (!blown) {
            return;
        }
        double flight = staged - BLAST_AT;
        while (simulated < flight - 1e-6) {
            game.fragments.update(game, RagdollConstants.FIXED_STEP);
            game.particles.update(RagdollConstants.FIXED_STEP, game.world);
            simulated += RagdollConstants.FIXED_STEP;
            reportRest(simulated);
        }
    }

    /** One fixed step of everything a thrown bomb touches, in the frame's order. */
    private void stepBomb() {
        game.projectiles.update(game, RagdollConstants.FIXED_STEP);
        if (++steps % STEPS_PER_ENTITY_TICK == 0) {
            game.entities.fastTick(game, RagdollConstants.FIXED_STEP * STEPS_PER_ENTITY_TICK);
        }
        game.fragments.update(game, RagdollConstants.FIXED_STEP);
        game.ragdolls.update(game, RagdollConstants.FIXED_STEP);
        game.particles.update(RagdollConstants.FIXED_STEP, game.world);
        simulated += RagdollConstants.FIXED_STEP;
        if (!blown && game.fragments.liveCount() > 0) {
            blown = true;
            blastAt = simulated;
            System.out.printf(Locale.ROOT, "[scene] dismember: %d bodies, %d pieces, %d standing,"
                            + " %d ragdolls at %.3f s%n", GROUP.length - game.entities.npcCount(),
                    game.fragments.liveCount(), game.entities.npcCount(), game.ragdolls.liveCount(),
                    simulated);
        }
        if (blown) {
            reportRest(simulated - blastAt);
        }
    }

    /** Logs, once, the exact step at which the last piece settles. */
    private void reportRest(double sinceBlast) {
        // Checked per step: a frame can run many steps, and the step is the fact.
        if (!reportedRest && game.fragments.liveCount() == 0) {
            reportedRest = true;
            System.out.printf(Locale.ROOT, "[scene] dismember: all %d pieces at rest %.3f s after the blast%n",
                    game.fragments.settledCount(), sinceBlast);
        }
    }

    private void blowApart() {
        blown = true;
        for (Npc n : row) {
            game.entities.npcs.remove(n);
            double yaw = Math.toRadians(n.yaw);
            float bx = n.pos.x + (float) Math.sin(yaw) * BLAST_AHEAD;
            float bz = n.pos.z - (float) Math.cos(yaw) * BLAST_AHEAD;
            game.fragments.spawnFromNpc(game, n, bx, n.pos.y + BLAST_HEIGHT, bz, BLAST_STRENGTH);
        }
        System.out.println("[scene] dismember: " + row.size() + " bodies, "
                + game.fragments.liveCount() + " pieces");
        row.clear();
    }
}
