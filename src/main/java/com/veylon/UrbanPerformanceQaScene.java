package com.veylon;

import com.veylon.entity.PlayerMovementSystem;
import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.util.Vec3i;
import com.veylon.world.Chunk;
import com.veylon.world.urban.DistrictPlan;
import com.veylon.world.urban.UrbanDistrict;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_F5;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F9;

/**
 * The dense-city performance tour ({@code VEYLON_SCENE=urban_performance}, urban milestone 24) on a
 * generator-19 or -20 world from {@code VEYLON_GENERATOR=urban-dev}, in either game mode: measured
 * legs through the places a survivor spends time, each reporting its frame times and what a frame
 * spent them on.
 *
 * <p>A street walked at walking pace across a region seam; the kitchen, upper floor and basement of a
 * generated walk-up and a transit platform and tunnel, looked around; a stronghold yard and a
 * quarantine growth site with their people and threats about; a storm over a kitchen with a
 * building fire set; five minutes of fast travel above the roofs, reported minute by minute; then
 * F5 and F9. Movement along a leg and every pose are staged (the player is carried at the leg's pace,
 * and kept from harm so a pursuit's pressure lasts the leg); the clock runs, and the simulation,
 * streaming, meshing and rendering are the game's own. Each leg prints frames, frame-time
 * percentiles, the CPU milliseconds of the control and simulation step, chunk generation, mesh
 * rebuilds and world rendering, chunks generated and meshed, draw calls, triangles, retained
 * chunks, resident chunk meshes, heap and populations. Failures throw.
 */
final class UrbanPerformanceQaScene extends UrbanRouteScene {
    static final double DEADLINE_SECONDS = 900;
    /** Seconds of fast travel, and of each of its reports. */
    static final float TRAVEL_SECONDS = 300, TRAVEL_REPORT = 60;
    /** Above every roof, mast and treetop: travel never meets a building. */
    private static final float CRUISE_Y = 90;
    private static final float LOOK_DEGREES_PER_SECOND = 36;
    /** Seconds after a leg's pose before it measures. */
    private static final float WARMUP = 2;

    private final List<String> legs = new ArrayList<>();
    /** Seconds of fast travel; a headless test plays a shorter journey. */
    private float travelSeconds = TRAVEL_SECONDS;
    private boolean passed;
    private long lastUpdate;
    private Leg measuring;

    UrbanPerformanceQaScene(Game game) {
        super(game);
    }

    @Override protected String tag() { return "urban-performance"; }
    @Override protected double deadline() { return DEADLINE_SECONDS; }

    boolean passed() { return passed; }
    List<String> legs() { return legs; }
    void travelSeconds(float seconds) { travelSeconds = seconds; }

    /** Plans the tour on the current generated world; no block is changed until the fire leg. */
    void stage() {
        if (game.world.urban == null || !game.world.urban.hasMissions()) {
            throw new IllegalStateException("urban_performance needs a generator-19 or -20 world (VEYLON_GENERATOR=urban-dev)");
        }
        UrbanAtmosphereTourPlan atmosphere = UrbanAtmosphereTourPlan.of(game.world);
        UrbanUndergroundTourPlan underground = UrbanUndergroundTourPlan.of(game.world);
        UrbanStrongholdTourPlan strongholds = UrbanStrongholdTourPlan.of(game.world);
        DistrictPlan origin = game.world.urban.regionPlan(0, 0);
        int streetZ = origin.eastWestLine(1);
        Vec3i streetStart = new Vec3i(-60, game.world.urban.groundY(-60, streetZ) + 1, streetZ);
        var s = game.renderer.settings;
        System.out.println("[urban-performance] stage seed=" + game.world.seed + " generator=" + game.world.generatorVersion
                + " mode=" + game.gameMode().name().toLowerCase(Locale.ROOT)
                + " evidence=staged-poses,staged-pace,staged-safety; generated-geometry; normal-simulation"
                + String.format(Locale.ROOT, " uptimeMs=%d heapMaxMiB=%.0f", ManagementFactory.getRuntimeMXBean().getUptime(),
                Runtime.getRuntime().maxMemory() / 1048576.0)
                + " resolution=" + game.window.framebufferWidth() + "x" + game.window.framebufferHeight()
                + " renderDistance=" + s.renderDistance + " shadows=" + s.shadowQuality + " vsync=" + s.vsync
                + " bloom=" + s.bloom + " fxaa=" + s.fxaa);
        System.out.println("[urban-performance] context=" + game.window.glContextSummary());
        add(new Wait("settle", 3f));
        add(new Leg("street-walk", streetStart, 90, PlayerMovementSystem.WALK_SPEED, 28, false));
        add(new Leg("apartment-kitchen", atmosphere.apartment().get("kitchen").stand(), 0, 0, 10, false));
        add(new Leg("apartment-upper-floor", atmosphere.apartment().get("upper-floor").stand(), 0, 0, 10, false));
        add(new Leg("apartment-basement", atmosphere.apartment().get("basement").stand(), 0, 0, 10, false));
        add(new Leg("transit-platform", underground.station().platform(), 0, 0, 10, false));
        add(new Leg("transit-tunnel", underground.station().tunnel(), 0, 0, 10, false));
        add(new Leg("stronghold-yard", strongholds.stronghold().yard(), 0, 0, 15, false));
        add(new Leg("quarantine-growth", atmosphere.growth().stand(), 0, 0, 15, false));
        add(new Leg("storm-fire", atmosphere.apartment().get("kitchen").stand(), 0, 0, 15, true));
        Vec3i spawn = new Vec3i((int) Math.floor(game.player.pos.x), (int) CRUISE_Y, (int) Math.floor(game.player.pos.z));
        add(new Leg("long-travel", spawn, 90, PlayerMovementSystem.FLY_FAST_SPEED, travelSeconds, false));
        add(new SaveAndLoad(streetStart), new Report());
        for (UrbanDistrict d : UrbanDistrict.values()) {
            var street = atmosphere.streets().get(d);
            System.out.println("[urban-performance] district " + d.id + " street=" + street.stand());
        }
        staged = true;
    }

    @Override
    protected void observe() {
        long now = System.nanoTime();
        if (measuring != null && lastUpdate != 0) { measuring.sample(now, lastUpdate); }
        lastUpdate = now;
    }

    /** Milliseconds of one quantity over a leg's frames: mean and the high percentiles. */
    private static final class Track {
        private float[] v = new float[1024];
        private int n;
        private double sum;

        void add(double ms) {
            if (n == v.length) { v = Arrays.copyOf(v, n * 2); }
            v[n++] = (float) ms;
            sum += ms;
        }

        double pct(double f) {
            if (n == 0) { return 0; }
            float[] sorted = Arrays.copyOf(v, n);
            Arrays.sort(sorted);
            return sorted[Math.max(0, Math.min(n - 1, (int) Math.ceil(f * n) - 1))];
        }

        String line() {
            return String.format(Locale.ROOT, "{mean=%.2f,p50=%.2f,p95=%.2f,p99=%.2f,max=%.2f}",
                    n == 0 ? 0 : sum / n, pct(.5), pct(.95), pct(.99), pct(1));
        }
    }

    /**
     * One measured leg: a staged pose, then either a look around at the pose or a glide at a pace
     * along a heading; the fire leg first sets a storm and a building alight.
     */
    private final class Leg extends Task {
        private final String label;
        private final Vec3i start;
        private final float heading, pace, seconds;
        private final boolean fire;
        private Track frame, step, gen, mesh, render;
        private long generated, meshed, draws, triangles, chunksDrawn, peakDraws, peakTriangles;
        private int frames, part;
        private float partStart;
        private boolean begun;

        Leg(String label, Vec3i start, float heading, float pace, float seconds, boolean fire) {
            super("leg " + label);
            this.label = label;
            this.start = start;
            this.heading = heading;
            this.pace = pace;
            this.seconds = seconds;
            this.fire = fire;
        }

        @Override boolean step(float dt) {
            if (!begun) { begin(); }
            keepSafe();
            float x = start.x() + .5f, z = start.z() + .5f;
            if (pace > 0) {
                double r = Math.toRadians(heading);
                x += (float) (Math.sin(r) * pace * age);
                z -= (float) (Math.cos(r) * pace * age);
                game.camera.yaw = heading;
                game.camera.pitch = pace > 10 ? 18 : 6;
            } else {
                game.camera.yaw = (heading + LOOK_DEGREES_PER_SECOND * age) % 360f;
                game.camera.pitch = 4;
            }
            game.player.pos.set(x, start.y() + .001f, z);
            game.player.vel.zero();
            if (measuring != this && age >= WARMUP) {
                // A pose's own streaming burst settles first; the leg measures what follows.
                reset();
                measuring = this;
                partStart = age;
            }
            if (label.equals("long-travel") && measuring == this && age - partStart >= TRAVEL_REPORT
                    && age < WARMUP + seconds) {
                report(label + "-" + ++part);
                reset();
                partStart = age;
            }
            if (age < WARMUP + seconds) { return false; }
            report(part == 0 ? label : label + "-" + ++part);
            measuring = null;
            if (label.equals("long-travel")) {
                // What the journey left in the heap, collected: no later leg measures this frame.
                System.gc();
                Runtime rt = Runtime.getRuntime();
                String line = String.format(Locale.ROOT, "leg=long-travel-end retained=%d heapAfterGcMiB=%.1f",
                        game.world.loadedCount(), (rt.totalMemory() - rt.freeMemory()) / 1048576.0);
                legs.add(line);
                System.out.println("[urban-performance] " + line);
            }
            return true;
        }

        private void begin() {
            begun = true;
            releaseAll();
            game.world.ensureChunks(start.x(), start.z(), 4, 10_000);
            game.player.pos.set(start.x() + .5f, start.y() + .001f, start.z() + .5f);
            game.player.vel.zero();
            if (fire) {
                game.weather.current = game.weather.next = Weather.STORM;
                game.weather.blend = 1f;
                game.weather.changeTimer = 4000f;
                System.out.println("[urban-performance] staged-storm lit=" + ignite(40));
            }
            System.out.println("[urban-performance] staged-pose " + start + " leg=" + label);
        }

        private void reset() {
            frame = new Track(); step = new Track(); gen = new Track(); mesh = new Track(); render = new Track();
            generated = meshed = draws = triangles = chunksDrawn = peakDraws = peakTriangles = 0;
            frames = 0;
        }

        /** The previous frame, read at the start of this one: its interval and what each part cost. */
        void sample(long now, long previousUpdate) {
            frames++;
            frame.add((now - previousUpdate) / 1e6);
            long streamedAt = game.streaming.startedAt;
            if (streamedAt > previousUpdate && streamedAt < now) { step.add((streamedAt - previousUpdate) / 1e6); }
            gen.add(game.streaming.generationNanos / 1e6);
            mesh.add(game.streaming.meshNanos / 1e6);
            render.add(game.renderer.renderNanos / 1e6);
            generated += game.streaming.generatedLastFrame;
            meshed += game.renderer.chunkMeshRebuildsLastFrame;
            draws += game.renderer.drawCalls;
            triangles += game.renderer.trianglesRendered;
            chunksDrawn += game.renderer.chunksRendered;
            peakDraws = Math.max(peakDraws, game.renderer.drawCalls);
            peakTriangles = Math.max(peakTriangles, game.renderer.trianglesRendered);
        }

        private void report(String name) {
            int resident = 0;
            for (Chunk c : game.world.loadedChunks()) { if (c.meshOpaque != null) { resident++; } }
            Runtime rt = Runtime.getRuntime();
            double seconds = frame.sum / 1000.0;
            String line = String.format(Locale.ROOT, "leg=%s frames=%d avgFps=%.1f frameMs=%s stepMs=%s genMs=%s meshMs=%s"
                            + " renderMs=%s generated=%d meshed=%d drawCalls={avg=%.0f,peak=%d}"
                            + " triangles={avg=%.0f,peak=%d} chunksDrawn=%.1f retained=%d residentMeshes=%d heapMiB=%.1f"
                            + " npcs=%d creatures=%d fires=%d particles=%d",
                    name, frames, seconds > 0 ? frames / seconds : 0, frame.line(), step.line(), gen.line(), mesh.line(),
                    render.line(), generated, meshed, frames == 0 ? 0 : draws / (double) frames, peakDraws,
                    frames == 0 ? 0 : triangles / (double) frames, peakTriangles,
                    frames == 0 ? 0 : chunksDrawn / (double) frames, game.world.loadedCount(), resident,
                    (rt.totalMemory() - rt.freeMemory()) / 1048576.0, game.entities.npcCount(),
                    game.entities.creatureCount(), game.fire.count(), game.particles.count);
            legs.add(line);
            System.out.println("[urban-performance] " + line);
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /**
     * Staged: a leg measures a pursuit's pressure, not its outcome, and a carried player never falls,
     * so no pose or glide lands as a fall from where the last one left off.
     */
    private void keepSafe() {
        game.player.health = game.player.maxHealth;
        game.player.resetFallState();
    }

    private int ignite(int wanted) {
        int lit = 0;
        int px = (int) Math.floor(game.player.pos.x), py = (int) Math.floor(game.player.pos.y);
        int pz = (int) Math.floor(game.player.pos.z);
        for (int dy = -1; dy <= 3 && lit < wanted; dy++) {
            for (int dx = -8; dx <= 8 && lit < wanted; dx++) {
                for (int dz = -8; dz <= 8 && lit < wanted; dz++) {
                    int x = px + dx, y = py + dy, z = pz + dz;
                    if (game.world.getBlock(x, y, z).flammable && game.fire.ignite(game, x, y, z)) { lit++; }
                }
            }
        }
        return lit;
    }

    /** F5 on a street, then F9 over the live world: each is one frame, so its time is that frame's. */
    private final class SaveAndLoad extends Task {
        private final Vec3i street;
        private Track frames;
        private boolean saved, loaded;
        private long last;
        private double saveFrame, loadFrame;

        SaveAndLoad(Vec3i street) { super("save-and-load"); this.street = street; }

        @Override boolean step(float dt) {
            keepSafe();
            long now = System.nanoTime();
            if (frames == null) {
                frames = new Track();
                game.world.ensureChunks(street.x(), street.z(), 4, 10_000);
                game.player.pos.set(street.x() + .5f, street.y() + .001f, street.z() + .5f);
                game.player.vel.zero();
                System.out.println("[urban-performance] staged-pose " + street + " leg=save-and-load");
            } else {
                double ms = (now - last) / 1e6;
                frames.add(ms);
                if (saved && saveFrame == 0) { saveFrame = ms; }
                if (loaded && loadFrame == 0) { loadFrame = ms; }
            }
            last = now;
            if (!saved && age > 2) { press(GLFW_KEY_F5); saved = true; return false; }
            if (saved && !loaded && age > 4) { press(GLFW_KEY_F9); loaded = true; return false; }
            if (age < 7) { return false; }
            String line = String.format(Locale.ROOT, "leg=save-and-load saveFrameMs=%.1f loadFrameMs=%.1f frameMs=%s retained=%d",
                    saveFrame, loadFrame, frames.line(), game.world.loadedCount());
            legs.add(line);
            System.out.println("[urban-performance] " + line);
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    private final class Report extends Task {
        Report() { super("report"); }

        @Override boolean step(float dt) {
            releaseAll();
            passed = true;
            reported = true;
            System.gc();
            Runtime rt = Runtime.getRuntime();
            System.out.printf(Locale.ROOT, "[urban-performance] pass=true mode=%s seed=%d legs=%d heapAfterGcMiB=%.1f"
                            + " retained=%d routeSeconds=%.0f%n", game.gameMode().name().toLowerCase(Locale.ROOT),
                    game.world.seed, legs.size(), (rt.totalMemory() - rt.freeMemory()) / 1048576.0,
                    game.world.loadedCount(), elapsed);
            System.out.println("[urban-performance] timeline " + String.join(" ", timeline));
            game.automation.finishScriptedRun();
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }
}
