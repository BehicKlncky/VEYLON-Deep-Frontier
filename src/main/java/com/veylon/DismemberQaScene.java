package com.veylon;

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

    private final Game game;
    private final List<Npc> row = new ArrayList<>();
    private boolean active;
    private boolean blown;
    private boolean reportedRest;
    private double simulated;

    DismemberQaScene(Game game) {
        this.game = game;
    }

    /** The row, on the flat clearing the harness has already built at {@code site}. */
    void stage(Vec3i site, boolean wall) {
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
        if (wall) {
            int wallZ = rowZ - WALL_BEHIND;
            for (int x = site.x() - 9; x <= site.x() + 9; x++) {
                for (int y = floor; y < floor + 3; y++) {
                    game.world.setBlock(x, y, wallZ, BlockType.STONE, false);
                }
            }
        }
        row.clear();
        for (int i = 0; i < ROW.length; i++) {
            float x = site.x() + 0.5f + (i - (ROW.length - 1) * 0.5f) * ROW_SPACING;
            Npc n = game.entities.spawnNpc(game.world, ROW[i].displayName, x, floor, rowZ + 0.5f);
            n.archetype = ROW[i];
            n.yaw = 180f; // facing the camera
            n.vel.zero();
            row.add(n);
        }

        // A raised, paused viewpoint: the simulation never runs, so it stays put.
        game.player.pos.set(site.x() + 0.5f, floor + CAMERA_RISE, site.z() + 0.5f + CAMERA_BACK);
        game.camera.yaw = 0f;
        game.camera.pitch = 20f;
        simulated = 0;
        blown = false;
        reportedRest = false;
        active = true;
        update(0);
    }

    /** Advances the staged moment the elapsed second maps to. */
    void update(double elapsed) {
        if (!active) {
            return;
        }
        double staged = Math.floor(elapsed / STAGE_GRID + 1e-6) * STAGE_GRID;
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
            // Checked per step: a frame can run many steps, and the step is the fact.
            if (!reportedRest && game.fragments.liveCount() == 0) {
                reportedRest = true;
                System.out.printf(Locale.ROOT, "[scene] dismember: all %d pieces at rest %.3f s after the blast%n",
                        game.fragments.settledCount(), simulated);
            }
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
