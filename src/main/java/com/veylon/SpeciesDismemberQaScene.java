package com.veylon;

import com.veylon.entity.BodyFamily;
import com.veylon.entity.BodyFragment;
import com.veylon.entity.Carcass;
import com.veylon.entity.Creature;
import com.veylon.entity.Creature.CreatureState;
import com.veylon.entity.Creature.CreatureType;
import com.veylon.entity.Npc;
import com.veylon.entity.Player;
import com.veylon.entity.RagdollConstants;
import com.veylon.settlement.NpcArchetype;
import com.veylon.simulation.WeatherSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Opt-in capture scenes for every kind of body blown apart. Inert in an
 * ordinary world.
 *
 * <p>{@code dismember_species} stands a row of eight bodies on open meadow:
 * a hare mid-hop, a bird in flight mid-beat, a stalking gloomstalker, the
 * player's remains (the player has no body of their own, so that spot is empty
 * until the blast), a guard mid-stride facing the camera as the human
 * reference, a lunging wolf, a grazing deer and a thornhorn tossing its horns,
 * each turned its own way. One second in, a scrap bomb's blast goes off on
 * the camera's side of each, so the pieces fly away from the camera.
 * {@code dismember_species_wall} puts a one-block stone step behind the row
 * and a wall behind the step, so pieces strike the step's edge and the wall and
 * come to rest on two levels, some of them across the edge; the step rises
 * away from the camera, so nothing lands out of its sight.
 * {@code dismember_species_close} is the step and wall seen from close by the
 * small end of the row: hare, bird, gloomstalker, the player's remains and the
 * guard.
 *
 * <p>Each body dies through the entry points a lethal blast ends in: the
 * ordinary kill, {@code Entity.recordBlastDeath}, then the death pose and the
 * spawn the death routing calls ({@code BodyFragmentSystem.spawnFromCreature},
 * {@code spawnFromNpc}, {@code spawnPlayerRemains}), so an animal's harvest
 * record is made exactly as in play. The blast itself, its radius and its
 * victims are {@code ExplosionSystem}'s and covered by headless tests; staging
 * one explosion per body would kill the neighbours first and carve craters
 * the pieces fall into.
 *
 * <p>Like {@code DismemberQaScene} it keeps the simulation paused and advances
 * the pieces in fixed steps tied to elapsed wall-clock seconds, snapped to a
 * tenth of a second, and it pins the clock the living are animated by, so the
 * frame before the blast and the pose the bodies die in are the same and two
 * runs of the same seed match.
 */
final class SpeciesDismemberQaScene {

    /** Wall-clock second the row is blown apart. */
    private static final double BLAST_AT = 1.0;
    /** Staged time advances in these steps, so a shot lands on a known moment. */
    private static final double STAGE_GRID = 0.1;
    /** The animation clock, held still: the living keep one pose and die in it. */
    private static final double CLOCK = 40.0;
    /** A scrap bomb's power. */
    private static final float BLAST_STRENGTH = 2.6f;
    /** How far on the camera's side of each body its blast goes off, and how high above the floor. */
    private static final float BLAST_AHEAD = 1.5f, BLAST_HEIGHT = 0.5f;
    /** Blocks from the staged site to the row. */
    private static final int ROW_DISTANCE = 6;
    /**
     * Blocks from the row to the front of the step behind it, the step's
     * depth, and the wall's distance from the row and height.
     */
    private static final int STEP_BEHIND = 2, STEP_DEPTH = 2, WALL_BEHIND = 4, WALL_HEIGHT = 5;
    /** The bird dies in flight, this high above the floor. */
    private static final float BIRD_ALTITUDE = 1.4f;
    private static final long SCENE_PARTICLE_SEED = 0x5350454349455321L;

    private enum Kind { CREATURE, PERSON, PLAYER }

    /**
     * One body of the row: x offset from the site's centre, heading, and for
     * the living the state, share of full speed and gait phase it is drawn in.
     */
    private record Body(Kind kind, CreatureType type, float x, float yaw, CreatureState state,
                        float run, float bobPhase) {
    }

    private static final Body[] ROW = {
            new Body(Kind.CREATURE, CreatureType.HARE, -4.0f, 250f, CreatureState.WANDER, 1f, 0.9f),
            new Body(Kind.CREATURE, CreatureType.BIRD, -2.6f, 120f, CreatureState.WANDER, 0.6f, 0.3f),
            new Body(Kind.CREATURE, CreatureType.STALKER, -1.2f, 300f, CreatureState.STALK, 0.5f, 0.8f),
            new Body(Kind.PLAYER, null, 0.4f, 160f, null, 0f, 0f),
            new Body(Kind.PERSON, null, 1.8f, 180f, null, 1f, 0.49f),
            new Body(Kind.CREATURE, CreatureType.WOLF, 3.5f, 75f, CreatureState.ATTACK, 0.3f, 0.2f),
            new Body(Kind.CREATURE, CreatureType.DEER, 5.4f, 105f, CreatureState.GRAZE, 0f, 0.3f),
            new Body(Kind.CREATURE, CreatureType.THORNHORN, 7.8f, 215f, CreatureState.ATTACK, 0.2f, 0.6f),
    };
    /** A person's running speed in {@code Animator.poseNpc}. */
    private static final float PERSON_RUN = 3.5f;

    private final Game game;
    private final List<Creature> creatures = new ArrayList<>();
    private final List<Npc> people = new ArrayList<>();
    private final List<float[]> playerSpots = new ArrayList<>();
    private String name;
    private boolean active;
    private boolean blown;
    private boolean reportedRest;
    private double simulated;
    private float blastY;

    SpeciesDismemberQaScene(Game game) {
        this.game = game;
    }

    /**
     * The scene named {@code scene}, on the flat clearing the harness has
     * already built at {@code site}.
     */
    void stage(Vec3i site, String scene) {
        name = scene;
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
        game.totalTime = CLOCK;

        int floor = site.y();
        int rowZ = site.z() - ROW_DISTANCE;
        blastY = floor + BLAST_HEIGHT;
        boolean walled = !scene.equals("dismember_species");
        if (walled) {
            for (int x = site.x() - 10; x <= site.x() + 10; x++) {
                for (int d = 0; d < STEP_DEPTH; d++) {
                    game.world.setBlock(x, floor, rowZ - STEP_BEHIND - d, BlockType.STONE, false);
                }
                for (int y = floor; y < floor + WALL_HEIGHT; y++) {
                    game.world.setBlock(x, y, rowZ - WALL_BEHIND, BlockType.STONE, false);
                }
            }
        }

        // A raised, paused viewpoint: the simulation never runs, so it stays put.
        game.camera.yaw = 0f;
        if (scene.equals("dismember_species_close")) {
            game.player.pos.set(site.x() + 0.5f - 2.4f, floor + 1.6f, rowZ + 0.5f + 3.6f);
            game.camera.pitch = 26f;
        } else {
            game.player.pos.set(site.x() + 0.5f, floor + (walled ? 1.4f : 1.0f), site.z() + 0.5f + 0.8f);
            game.camera.pitch = walled ? 20f : 18f;
        }

        creatures.clear();
        people.clear();
        playerSpots.clear();
        for (Body b : ROW) {
            float x = site.x() + 0.5f + b.x;
            float z = rowZ + 0.5f;
            double heading = Math.toRadians(b.yaw);
            float fx = (float) Math.sin(heading), fz = (float) -Math.cos(heading);
            switch (b.kind) {
                case CREATURE -> {
                    float y = floor + (b.type == CreatureType.BIRD ? BIRD_ALTITUDE : 0f);
                    Creature c = game.entities.spawnCreature(game.world, b.type, x, y, z);
                    c.yaw = b.yaw;
                    c.state = b.state;
                    c.vel.set(fx * b.run * b.type.speed, 0f, fz * b.run * b.type.speed);
                    c.bobPhase = b.bobPhase;
                    creatures.add(c);
                }
                case PERSON -> {
                    Npc n = game.entities.spawnNpc(game.world, NpcArchetype.GUARD.displayName, x, floor, z);
                    n.archetype = NpcArchetype.GUARD;
                    n.state = Npc.NpcState.IDLE;
                    n.yaw = b.yaw;
                    n.vel.set(fx * b.run * PERSON_RUN, 0f, fz * b.run * PERSON_RUN);
                    n.bobPhase = b.bobPhase;
                    people.add(n);
                }
                case PLAYER -> playerSpots.add(new float[] {x, floor, z, b.yaw});
            }
        }
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
        game.totalTime = CLOCK;
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
            if (!reportedRest && game.fragments.liveCount() == 0) {
                // Checked per step: a frame can run many steps, and the step is the fact.
                reportedRest = true;
                System.out.printf(Locale.ROOT, "[scene] %s: all %d pieces at rest %.3f s"
                        + " after the blast%n", name, game.fragments.settledCount(), simulated);
            }
        }
    }

    /** Each body dies of its blast and comes apart in the pose it was last drawn in. */
    private void blowApart() {
        blown = true;
        for (Creature c : creatures) {
            game.entities.creatures.remove(c);
            c.killBy(false);
            c.recordBlastDeath(c.pos.x, blastY, c.pos.z + BLAST_AHEAD, BLAST_STRENGTH);
            game.fragments.spawnFromCreature(game, c, game.fragments.deathPose(c),
                    c.blastX, c.blastY, c.blastZ, c.blastStrength);
        }
        for (Npc n : people) {
            game.entities.npcs.remove(n);
            n.killBy(false);
            n.recordBlastDeath(n.pos.x, blastY, n.pos.z + BLAST_AHEAD, BLAST_STRENGTH);
            game.fragments.spawnFromNpc(game, n, game.fragments.deathPose(n),
                    n.blastX, n.blastY, n.blastZ, n.blastStrength);
        }
        for (float[] spot : playerSpots) {
            // The player's remains, from a stand-in: killing the real player
            // would put the death screen over the capture.
            Player remains = new Player(game.world);
            remains.pos.set(spot[0], spot[1], spot[2]);
            remains.yaw = spot[3];
            remains.killBy(false);
            remains.recordBlastDeath(spot[0], blastY, spot[2] + BLAST_AHEAD, BLAST_STRENGTH);
            game.fragments.spawnPlayerRemains(game, remains);
        }
        report();
        creatures.clear();
        people.clear();
        playerSpots.clear();
    }

    private void report() {
        Map<BodyFamily, Integer> pieces = new EnumMap<>(BodyFamily.class);
        for (BodyFragment f : game.fragments.live) {
            pieces.merge(f.definition.family, 1, Integer::sum);
        }
        int records = 0;
        int drawnWhole = 0;
        for (Carcass c : game.entities.carcasses) {
            if (c.fragmented()) {
                records++;
            } else {
                drawnWhole++;
            }
        }
        System.out.printf(Locale.ROOT, "[scene] %s: %d bodies, %d pieces %s,"
                        + " %d harvest records on torsos, %d carcasses drawn whole%n",
                name, ROW.length, game.fragments.liveCount(), pieces, records, drawnWhole);
    }
}
