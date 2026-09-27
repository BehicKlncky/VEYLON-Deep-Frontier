package com.veylon;

import com.veylon.entity.CombustionSource;
import com.veylon.entity.Creature;
import com.veylon.entity.Creature.CreatureState;
import com.veylon.entity.Creature.CreatureType;
import com.veylon.entity.Entity;
import com.veylon.entity.Npc;
import com.veylon.entity.RagdollConstants;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.settlement.NpcArchetype;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.simulation.WeatherSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Opt-in capture scenes for burning bodies. Inert in an ordinary world.
 *
 * <p>Every body is set alight through {@code CombustionSystem.ignite} — the
 * entry point a fire bomb breaking on a body uses — at a point low on its side
 * facing the camera, so the flames can be seen climbing from there. Two kinds
 * of scene:
 *
 * <ul>
 *   <li><b>Held</b> ({@code body_fire_row}, {@code body_fire_row_night},
 *       {@code body_fire_out}, {@code body_fire_blast}): the bodies stand where
 *       they were put. Only the fire itself, the remains, the particles and the
 *       ambience's emitters run, in fixed steps, so nobody moves and the fire
 *       goes through its whole life on one spot; the bodies' health is raised so
 *       none dies of it inside the capture (a QA hold, not a gameplay rule).</li>
 *   <li><b>Live</b> ({@code body_fire_panic}, {@code body_fire_bird},
 *       {@code body_fire_rain}, {@code body_fire_ragdoll},
 *       {@code body_fire_player}): the whole world step
 *       ({@code Game.advanceWorld}) runs in fixed steps, so bodies panic, run,
 *       fly, die, fall and settle exactly as in play.</li>
 * </ul>
 *
 * <p>The row: people of five families in front (a guard, a scavenger, a
 * trader, a captive, a brute) and every species behind them, a bird hovering
 * over them; alight at 1 s, burning out at about 7 s. {@code body_fire_out}:
 * four bodies in the open, three under a stone roof and a person in shallow
 * water; rain from 2.5 s puts out the open ones, the roof keeps the others
 * burning to the end, and the water at the feet makes that one burn out
 * sooner. {@code body_fire_blast} sets a row alight and blows it apart at
 * 2 s through the entry points a lethal blast ends in, so the pieces carry the
 * flames. {@code body_fire_panic} is a walled yard of people and animals alight
 * at 1 s; {@code body_fire_rain} the same crowd with a roof over half the yard
 * and rain from 1.5 s; {@code body_fire_bird} three birds alight in flight;
 * {@code body_fire_ragdoll} the yard's crowd too weak to outlive its fire, so they
 * fall burning and burn on as bodies. {@code body_fire_close} is a guard, a wolf, a deer
 * and a bird close to the camera.
 * {@code body_fire_player} sets the Survival player alight, holding an axe, with
 * a burning guard ahead, and brings rain at 5 s.
 *
 * <p>Like the other combat scenes it keeps the simulation paused and steps
 * what it is about in fixed 1/60 s steps tied to elapsed wall-clock seconds,
 * snapped to a tenth, and it drives the animation clock from the same steps,
 * so a capture at a given second shows the same moment on every run.
 */
final class BodyFireQaScene {

    /** Every scene this class stages. */
    static final String[] SCENES = {
            "body_fire_row", "body_fire_row_night", "body_fire_close", "body_fire_out", "body_fire_blast",
            "body_fire_panic", "body_fire_bird", "body_fire_rain", "body_fire_ragdoll", "body_fire_player"};

    private static final double STAGE_GRID = 0.1;
    private static final float STEP = RagdollConstants.FIXED_STEP;
    private static final int STEPS_PER_FAST_TICK = Math.round(SimulationScheduler.FAST_DT / STEP);
    /** The animation clock at the start of a scene; it then runs with the staged steps. */
    private static final double CLOCK = 40.0;
    /** Health a held body is given so the capture's fire cannot kill it. */
    private static final float HELD_HEALTH = 400f;
    private static final float BLAST_STRENGTH = 2.6f;
    private static final long SCENE_PARTICLE_SEED = 0x424f445946495245L;

    private final Game game;
    private final List<Entity> alight = new ArrayList<>();
    private final List<Creature> creatures = new ArrayList<>();
    private final List<Npc> people = new ArrayList<>();
    private String name;
    private boolean active;
    private boolean live;
    private double igniteAt, rainAt, blastAt;
    private boolean ignited, raining, blown;
    private double simulated;
    private int steps;
    private int floor;
    private int nextReport;
    private int lastBurning = -1;
    /** Frames and milliseconds presented at the last once-a-second report, for that second's frame time. */
    private long reportedFrames;
    private double reportedMs;

    BodyFireQaScene(Game game) {
        this.game = game;
    }

    /** The scene named {@code scene}, on the flat clearing the harness has already built at {@code site}. */
    void stage(Vec3i site, String scene) {
        name = scene;
        live = switch (scene) {
            case "body_fire_row", "body_fire_row_night", "body_fire_close", "body_fire_out", "body_fire_blast" -> false;
            default -> true;
        };
        double hour = switch (scene) {
            case "body_fire_row_night" -> 22.5;
            case "body_fire_panic", "body_fire_ragdoll", "body_fire_blast" -> 18.9;
            case "body_fire_rain" -> 18.4;
            default -> 14.5;
        };
        game.time.totalMinutes = (long) (hour * 60);
        setWeather(WeatherSystem.Weather.CLEAR);
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        game.entities.carcasses.clear();
        game.entities.corpses.clear();
        game.entities.tracks.clear();
        game.ragdolls.reset();
        game.fragments.reset();
        game.burnResidues.reset();
        game.projectiles.reset();
        game.fire.reset();
        game.liquidFire.reset();
        game.combustion.reset();
        game.particles.count = 0;
        game.particles.density = 1f;
        game.particles.setRandomSeed(SCENE_PARTICLE_SEED);
        game.ambience.reseed(SCENE_PARTICLE_SEED);
        game.player.inventory.set(0, null);
        game.player.hotbarSel = 0;
        game.player.vel.zero();
        game.simPaused = true;
        game.totalTime = CLOCK;

        floor = site.y();
        alight.clear();
        creatures.clear();
        people.clear();
        igniteAt = 1.0;
        rainAt = -1;
        blastAt = -1;
        int x = site.x(), z = site.z();
        switch (scene) {
            case "body_fire_row", "body_fire_row_night" -> stageRow(x, z);
            case "body_fire_close" -> stageClose(x, z);
            case "body_fire_out" -> stageOut(x, z);
            case "body_fire_blast" -> stageBlast(x, z);
            case "body_fire_panic" -> stageYard(x, z, false);
            case "body_fire_rain" -> stageYard(x, z, true);
            case "body_fire_bird" -> stageBirds(x, z);
            case "body_fire_ragdoll" -> {
                stageYard(x, z, false);
                weaken();
            }
            default -> stagePlayer(x, z);
        }
        if (!live) {
            for (Entity e : alight) {
                e.maxHealth = e.health = HELD_HEALTH;
            }
        }
        simulated = 0;
        steps = 0;
        nextReport = 0;
        lastBurning = -1;
        reportedFrames = 0;
        reportedMs = 0;
        ignited = raining = blown = false;
        active = true;
        System.out.printf(Locale.ROOT, "[scene] %s: %d bodies staged (%s)%n", name, alight.size(),
                live ? "live world step" : "held in place");
        update(0);
    }

    // ------------------------------------------------------------------
    // Staging
    // ------------------------------------------------------------------

    /** People of five families in front, every species behind, a bird hovering over them. */
    private void stageRow(int x, int z) {
        camera(x + 0.5f, floor + 1.7f, z + 0.5f, 0f, 12f);
        int front = z - 6, back = z - 9;
        person(NpcArchetype.GUARD, x - 2.5f, front, 200f);
        raider(x - 0.6f, front, 150f);
        trader(x + 1.3f, front, 170f);
        person(NpcArchetype.CAPTIVE, x + 3.2f, front, 220f);
        person(NpcArchetype.BRUTE, x + 5.2f, front, 185f);
        creature(CreatureType.HARE, x - 3.2f, back, 0f, 250f);
        creature(CreatureType.STALKER, x - 1.3f, back, 0f, 300f);
        creature(CreatureType.WOLF, x + 1.0f, back, 0f, 75f);
        creature(CreatureType.DEER, x + 3.4f, back, 0f, 110f);
        creature(CreatureType.THORNHORN, x + 6.6f, back, 0f, 215f);
        creature(CreatureType.BIRD, x + 1.8f, z - 7.5f, 2.3f, 120f);
    }

    /** Close by: a guard mid-stride, a wolf, a deer and a bird hovering at eye height. */
    private void stageClose(int x, int z) {
        camera(x + 0.5f, floor, z + 0.5f, 0f, 6f);
        Npc guard = person(NpcArchetype.GUARD, x + 1.2f, z - 3.2f, 150f);
        guard.vel.set(0f, 0f, 0f);
        creature(CreatureType.WOLF, x + 2.8f, z - 4.2f, 0f, 250f);
        creature(CreatureType.DEER, x - 0.4f, z - 5.2f, 0f, 100f);
        creature(CreatureType.BIRD, x + 0.9f, z - 2.2f, 1.3f, 60f);
    }

    /** Four in the open, three under a roof, a person with their feet in water; rain at 2.5 s. */
    private void stageOut(int x, int z) {
        camera(x + 0.5f, floor + 1.8f, z + 0.5f, 0f, 14f);
        rainAt = 2.5;
        int row = z - 7;
        // A stone roof three blocks up over the right of the row, on four posts.
        for (int bx = x + 2; bx <= x + 8; bx++) {
            for (int bz = row - 2; bz <= row + 1; bz++) {
                game.world.setBlock(bx, floor + 3, bz, BlockType.STONE, false);
            }
        }
        for (int[] post : new int[][] {{x + 2, row - 2}, {x + 8, row - 2}, {x + 2, row + 1}, {x + 8, row + 1}}) {
            for (int y = floor; y < floor + 3; y++) {
                game.world.setBlock(post[0], y, post[1], BlockType.STONE, false);
            }
        }
        // A one-block pool on the left, deep enough to wet the feet and no more.
        int poolX = x - 3;
        game.world.setBlock(poolX, floor - 1, row, BlockType.WATER, false);
        person(NpcArchetype.GUARD, poolX + 0.5f, row, 200f).pos.y = floor - 1;
        person(null, x - 1.5f, row, 170f);
        creature(CreatureType.DEER, x - 0.2f, row - 1, 0f, 100f);
        creature(CreatureType.WOLF, x + 1.1f, row, 0f, 250f);
        trader(x + 3.5f, row, 190f);
        creature(CreatureType.THORNHORN, x + 5.3f, row - 1, 0f, 80f);
        person(NpcArchetype.CAPTIVE, x + 7.2f, row, 160f);
    }

    /** A row set alight at 0.5 s and blown apart at 2 s. */
    private void stageBlast(int x, int z) {
        camera(x + 0.5f, floor + 2.0f, z + 0.5f, 0f, 18f);
        igniteAt = 0.5;
        blastAt = 2.0;
        int row = z - 7;
        creature(CreatureType.HARE, x - 2.8f, row, 0f, 250f);
        creature(CreatureType.STALKER, x - 1.2f, row, 0f, 300f);
        person(NpcArchetype.GUARD, x + 0.6f, row, 180f);
        creature(CreatureType.WOLF, x + 2.4f, row, 0f, 75f);
        creature(CreatureType.DEER, x + 4.4f, row, 0f, 105f);
        raider(x + 6.2f, row, 160f);
        creature(CreatureType.THORNHORN, x + 8.4f, row, 0f, 215f);
    }

    /** A walled yard below a raised viewpoint; {@code roofed} covers its right half and brings rain. */
    private void stageYard(int x, int z, boolean roofed) {
        int x0 = x - 4, x1 = x + 11, z0 = z - 13, z1 = z - 4;
        for (int bx = x0; bx <= x1; bx++) {
            for (int y = floor; y < floor + 2; y++) {
                game.world.setBlock(bx, y, z0, BlockType.STONE, false);
                game.world.setBlock(bx, y, z1, BlockType.STONE, false);
            }
        }
        for (int bz = z0; bz <= z1; bz++) {
            for (int y = floor; y < floor + 2; y++) {
                game.world.setBlock(x0, y, bz, BlockType.STONE, false);
                game.world.setBlock(x1, y, bz, BlockType.STONE, false);
            }
        }
        if (roofed) {
            rainAt = 1.5;
            for (int bx = x + 4; bx < x1; bx++) {
                for (int bz = z0 + 1; bz < z1; bz++) {
                    game.world.setBlock(bx, floor + 4, bz, BlockType.STONE, false);
                }
            }
        }
        // The viewpoint stands on a stone pillar just behind the near wall, looking down into the yard.
        for (int y = floor; y < floor + 4; y++) {
            game.world.setBlock(x + 3, y, z - 2, BlockType.STONE, false);
        }
        camera(x + 3.5f, floor + 4f, z - 1.5f, 0f, 40f);
        int row = z - 8;
        person(NpcArchetype.GUARD, x - 1.5f, row, 180f);
        trader(x + 0.5f, row + 2, 90f);
        person(null, x + 2.5f, row - 2, 270f);
        person(NpcArchetype.CAPTIVE, x + 7.5f, row + 1, 200f);
        creature(CreatureType.DEER, x + 5f, row, 0f, 130f);
        creature(CreatureType.WOLF, x + 8.5f, row - 2, 0f, 250f);
        creature(CreatureType.THORNHORN, x + 3.5f, row + 2.5f, 0f, 60f);
        creature(CreatureType.HARE, x - 2f, row - 3, 0f, 30f);
    }

    /** Three birds in flight ahead of the camera, alight at 0.5 s. */
    private void stageBirds(int x, int z) {
        camera(x + 0.5f, floor + 1.6f, z + 0.5f, 0f, -10f);
        igniteAt = 0.5;
        creature(CreatureType.BIRD, x + 0.5f, z - 7, 3.2f, 90f);
        creature(CreatureType.BIRD, x + 3.0f, z - 9, 4.6f, 250f);
        creature(CreatureType.BIRD, x + 5.5f, z - 6, 2.4f, 140f);
    }

    /** The yard's crowd too weak to outlive its fire: each falls burning and burns on as a body. */
    private void weaken() {
        float[] health = {3f, 4f, 2.5f, 3.5f, 5f, 2f, 4.5f, 1.5f};
        for (int i = 0; i < alight.size(); i++) {
            alight.get(i).health = health[i % health.length];
        }
    }

    /** The Survival player alight with an axe in hand and a burning guard ahead; rain at 5 s. */
    private void stagePlayer(int x, int z) {
        camera(x + 0.5f, floor, z + 0.5f, 0f, 4f);
        game.player.inventory.set(0, new ItemStack(ItemType.IRON_AXE, 1));
        game.player.hotbarSel = 0;
        rainAt = 5.0;
        alight.add(game.player);
        person(NpcArchetype.GUARD, x + 2.0f, z - 4, 200f).maxHealth = HELD_HEALTH;
        people.get(0).health = HELD_HEALTH;
    }

    private void camera(float x, float y, float z, float yaw, float pitch) {
        game.player.pos.set(x, y, z);
        game.camera.yaw = yaw;
        game.camera.pitch = pitch;
    }

    private Npc person(NpcArchetype archetype, float x, float z, float yaw) {
        Npc n = game.entities.spawnNpc(game.world, archetype == null ? "Settler" : archetype.displayName,
                x, floor, z);
        n.archetype = archetype;
        n.campIndex = 1;
        n.state = Npc.NpcState.IDLE;
        n.yaw = yaw;
        n.vel.zero();
        people.add(n);
        alight.add(n);
        return n;
    }

    private void raider(float x, float z, float yaw) {
        Npc n = person(NpcArchetype.SCAVENGER, x, z, yaw);
        n.raider = true;
    }

    /** A trader's look; only a held one is a trader, since a live one would pack up and leave. */
    private void trader(float x, float z, float yaw) {
        Npc n = person(NpcArchetype.TRADER, x, z, yaw);
        n.isTrader = !live;
    }

    private Creature creature(CreatureType type, float x, float z, float lift, float yaw) {
        Creature c = game.entities.spawnCreature(game.world, type, x, floor + lift, z);
        c.yaw = yaw;
        c.state = CreatureState.WANDER;
        c.vel.zero();
        creatures.add(c);
        alight.add(c);
        return c;
    }

    // ------------------------------------------------------------------
    // Running
    // ------------------------------------------------------------------

    /** Advances the staged moment the elapsed second maps to. */
    void update(double elapsed) {
        if (!active) {
            return;
        }
        double staged = Math.floor(elapsed / STAGE_GRID + 1e-6) * STAGE_GRID;
        while (simulated < staged - 1e-6) {
            step();
        }
        game.totalTime = CLOCK + simulated;
    }

    private void step() {
        game.totalTime = CLOCK + simulated;
        if (!ignited && simulated >= igniteAt - 1e-6) {
            ignite();
        }
        if (rainAt >= 0 && !raining && simulated >= rainAt - 1e-6) {
            raining = true;
            setWeather(WeatherSystem.Weather.RAIN);
            System.out.printf(Locale.ROOT, "[scene] %s: rain from %.2f s%n", name, simulated);
        }
        if (blastAt >= 0 && !blown && simulated >= blastAt - 1e-6) {
            blowApart();
        }
        if (live) {
            game.advanceWorld(STEP);
        } else {
            if (++steps % STEPS_PER_FAST_TICK == 0) {
                game.combustion.fastTick(game, SimulationScheduler.FAST_DT);
            }
            game.fragments.update(game, STEP);
            game.burnResidues.update(game, STEP);
            game.particles.update(STEP, game.world);
            game.ambience.updateEmitters(STEP);
        }
        simulated += STEP;
        report();
    }

    /** Every staged body catches where a bottle breaking low on its near side would touch it. */
    private void ignite() {
        ignited = true;
        int id = 1;
        for (Entity e : alight) {
            game.combustion.ignite(game, e, CombustionSource.DIRECT_HIT, 1f, false, id++,
                    e.pos.x, e.pos.y + Math.min(0.25f, e.height * 0.3f), e.pos.z + e.width * 0.5f);
        }
        System.out.printf(Locale.ROOT, "[scene] %s: %d bodies set alight at %.2f s%n", name, alight.size(), simulated);
    }

    /** Each body dies of its blast and comes apart in the pose it was last drawn in, still burning. */
    private void blowApart() {
        blown = true;
        float blastY = floor + 0.5f;
        for (Creature c : creatures) {
            game.entities.creatures.remove(c);
            c.killBy(false);
            c.recordBlastDeath(c.pos.x, blastY, c.pos.z + 1.5f, BLAST_STRENGTH);
            game.fragments.spawnFromCreature(game, c, game.fragments.deathPose(c),
                    c.blastX, c.blastY, c.blastZ, c.blastStrength);
        }
        for (Npc n : people) {
            game.entities.npcs.remove(n);
            n.killBy(false);
            n.recordBlastDeath(n.pos.x, blastY, n.pos.z + 1.5f, BLAST_STRENGTH);
            game.fragments.spawnFromNpc(game, n, game.fragments.deathPose(n),
                    n.blastX, n.blastY, n.blastZ, n.blastStrength);
        }
        System.out.printf(Locale.ROOT, "[scene] %s: blown apart at %.2f s: %d pieces, %d residues%n",
                name, simulated, game.fragments.liveCount(), game.burnResidues.trackedCount());
    }

    /** Once a second, and whenever the count of bodies alight changes: what is burning and drawn. */
    private void report() {
        int burning = game.combustion.burningBodies(game);
        boolean second = simulated >= nextReport - 1e-6;
        if (!second && burning == lastBurning) {
            return;
        }
        double frameMs = 0;
        if (second) {
            nextReport++;
            // The frames presented since the last report: their mean wall-clock interval.
            var timing = game.frameProfiler.snapshot();
            double totalMs = timing.averageMs() * timing.frames();
            long frames = timing.frames() - reportedFrames;
            frameMs = frames > 0 ? (totalMs - reportedMs) / frames : 0;
            reportedFrames = timing.frames();
            reportedMs = totalMs;
        }
        lastBurning = burning;
        System.out.printf(Locale.ROOT, "[scene] %s t=%.2f: alight=%d residues=%d ragdolls=%d pieces=%d"
                        + " particles=%d bodyFireParticles=%d heard=%d flamesDrawn=%d rainedOut=%d burnedOut=%d"
                        + " frameMs=%.2f%n",
                name, simulated, burning, game.burnResidues.trackedCount(), game.ragdolls.live.size(),
                game.fragments.liveCount() + game.fragments.settledCount(), game.particles.count,
                game.ambience.bodyFire.particlesLastPass, game.ambience.bodyFire.heardCount(),
                game.renderer.bodyFlamesDrawn, game.combustion.totalRainedOut, game.combustion.totalBurnouts,
                frameMs);
    }

    /** Pins weather so a capture is not blended mid-transition. */
    private void setWeather(WeatherSystem.Weather w) {
        game.weather.current = w;
        game.weather.next = w;
        game.weather.blend = 1f;
        game.weather.changeTimer = 4000f;
    }
}
