package com.veylon;

import com.veylon.entity.Carcass;
import com.veylon.entity.Creature;
import com.veylon.entity.Npc;
import com.veylon.entity.Track;
import com.veylon.gfx.FrameProfiler;
import com.veylon.gfx.GraphicsSettings;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.qa.RuntimeBudgetSnapshot;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementPlanner;
import com.veylon.settlement.SettlementType;
import com.veylon.simulation.EventSystem;
import com.veylon.simulation.WeatherSystem;
import com.veylon.ui.GraphicsOptionsScreen;
import com.veylon.util.Vec3i;
import com.veylon.world.Biome;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.Raycaster;
import com.veylon.world.World;
import org.joml.Vector3f;

import java.util.Locale;
import java.util.Random;

/**
 * Opt-in QA, benchmark and capture scaffolding for {@link Game}.
 *
 * <p>Every entry point here is inert unless a {@code VEYLON_*} environment
 * variable selects it, so a normal player session never touches this code.
 * It is kept out of {@code Game} because it is roughly a quarter of the
 * orchestrator's volume while carrying none of its gameplay responsibility.
 *
 * <p>The harness deliberately reaches into {@code Game} package-private state:
 * it emulates player input and world edits that the production code paths
 * reach through UI and simulation, and it must observe the same fields the
 * renderer and profiler write.
 */
final class QaHarness {

    /** Deterministic particle seeds keep repeat captures byte-comparable. */
    private static final long BEACON_PARTICLE_SEED = 0x424541434f4eL;
    private static final long SHOWCASE_PARTICLE_SEED = 0x5645594c4f4eL;
    /** Freezes AI re-decision so a staged pose survives the capture window. */
    private static final float POSE_FREEZE_SECONDS = 9999f;
    /** sin(0.49 * 3.2) ~= 1: freezes moving gaits at maximum leg swing. */
    private static final float POSE_MAX_SWING_PHASE = 0.49f;
    private static final int FORTRESS_SEARCH_RADIUS_REGIONS = 24;
    private static final double FORTRESS_APPROACH_SECONDS = 12.0;

    private final Game game;

    // Render statistics sampled across an automated run.
    private long profiledRenderFrames;
    private long drawCallSum;
    private double triangleSum;
    private int peakDrawCalls;
    private long peakTriangles;
    private int peakParticles;
    private int peakChunks;
    private long particleDrawCallSum;
    private int peakParticleDrawCalls;
    private int peakResidentChunkMeshes;

    /** Dedicated release-smoke sample while streaming toward a generated fortress. */
    private final FrameProfiler fortressApproachProfiler = new FrameProfiler();
    private final Vector3f fortressApproachStart = new Vector3f();
    private final Vector3f fortressApproachEnd = new Vector3f();
    private Settlement smokeFortress;
    private double fortressApproachStarted;
    private boolean fortressApproaching;
    private boolean fortressApproachComplete;
    private long fortressApproachMeshStart;
    private int fortressApproachChunkStart;

    // Showcase toggles.
    private boolean heldCycleShowcase;
    private boolean uiCycleShowcase;
    private boolean staticLoadingQa;
    private boolean benchmarkMovement;
    private boolean miningShowcase;
    private float miningShowcaseElapsed;
    private float miningShowcaseDustTimer;
    private boolean ashwolfSeqShowcase;
    private Creature ashwolfSeqWolf;
    private Carcass ashwolfSeqCarcass;
    private final Vector3f benchmarkMovementStart = new Vector3f();

    private String qaOptionsSet = System.getenv("VEYLON_QA_SET_OPTIONS");
    private float qaOptionsTimer;

    QaHarness(Game game) {
        this.game = game;
    }

    // ------------------------------------------------------------------
    // Session configuration
    // ------------------------------------------------------------------

    /** Resolves the world seed, honouring {@code VEYLON_SEED} for reproducible runs. */
    long configuredSeed() {
        long seed = new Random().nextLong();
        String configured = System.getenv("VEYLON_SEED");
        if (configured == null || configured.isBlank()) {
            return seed;
        }
        try {
            seed = Long.parseLong(configured.trim());
        } catch (NumberFormatException e) {
            seed = configured.trim().hashCode();
        }
        System.out.println("[world] deterministic seed " + seed);
        return seed;
    }

    /** Optional QA-only framebuffer/window override, e.g. VEYLON_RESOLUTION=1920x1080. */
    void applyResolutionOverride() {
        GraphicsSettings settings = game.renderer.settings;
        String configured = System.getenv("VEYLON_RESOLUTION");
        if (configured != null && !configured.isBlank()) {
            String[] parts = configured.trim().toLowerCase(Locale.ROOT).split("x");
            try {
                if (parts.length != 2) throw new NumberFormatException("expected WIDTHxHEIGHT");
                int width = Integer.parseInt(parts[0].trim());
                int height = Integer.parseInt(parts[1].trim());
                if (width < 960 || height < 540 || width > 7680 || height > 4320) {
                    throw new NumberFormatException("outside supported range");
                }
                settings.windowWidth = width;
                settings.windowHeight = height;
                settings.fullscreen = false;
                System.out.println("[settings] QA resolution override " + width + "x" + height);
            } catch (NumberFormatException e) {
                System.err.println("[settings] ignoring invalid VEYLON_RESOLUTION='" + configured + "'");
            }
        }
        String uiScale = System.getenv("VEYLON_UI_SCALE");
        if (uiScale != null && !uiScale.isBlank()) {
            try {
                settings.uiScale = Math.max(0.75f,
                        Math.min(1.5f, Float.parseFloat(uiScale.trim())));
                System.out.println("[settings] QA UI scale override " + settings.uiScale);
            } catch (NumberFormatException e) {
                System.err.println("[settings] ignoring invalid VEYLON_UI_SCALE='" + uiScale + "'");
            }
        }
        String vsync = System.getenv("VEYLON_VSYNC");
        if (vsync != null && !vsync.isBlank()) {
            settings.vsync = !(vsync.equals("0") || vsync.equalsIgnoreCase("false")
                    || vsync.equalsIgnoreCase("off"));
            System.out.println("[settings] QA VSync override " + settings.vsync);
        }
        String fullscreen = System.getenv("VEYLON_FULLSCREEN");
        if (fullscreen != null && !fullscreen.isBlank()) {
            settings.fullscreen = !(fullscreen.equals("0")
                    || fullscreen.equalsIgnoreCase("false")
                    || fullscreen.equalsIgnoreCase("off"));
            System.out.println("[settings] QA fullscreen override " + settings.fullscreen);
        }
        String shadows = System.getenv("VEYLON_SHADOWS");
        if (shadows != null && !shadows.isBlank()) {
            try {
                settings.shadowQuality = Math.max(0,
                        Math.min(2, Integer.parseInt(shadows.trim())));
                System.out.println("[settings] QA shadow override " + settings.shadowQuality);
            } catch (NumberFormatException e) {
                System.err.println("[settings] ignoring invalid VEYLON_SHADOWS='" + shadows + "'");
            }
        }
    }

    /**
     * QA relaunch-persistence hook (VEYLON_QA_SET_OPTIONS="fov=85,bloom=false"):
     * mutates live settings on the open options screen, then routes through the
     * exact same applyGraphicsOptions(...) -> settings.save() path the APPLY
     * button uses, so a following unmodified launch proves persistence.
     */
    private void applyQaOptions(String script) {
        GraphicsSettings s = game.renderer.settings;
        for (String pair : script.split(",")) {
            String[] kv = pair.trim().split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = kv[0].trim();
            String value = kv[1].trim();
            try {
                switch (key) {
                    case "fov" -> s.fov = Float.parseFloat(value);
                    case "uiScale" -> s.uiScale = Float.parseFloat(value);
                    case "particleDensity" -> s.particleDensity = Float.parseFloat(value);
                    case "motion" -> s.motion = Float.parseFloat(value);
                    case "renderDistance" -> s.renderDistance = Integer.parseInt(value);
                    case "shadowQuality" -> s.shadowQuality = Integer.parseInt(value);
                    case "bloom" -> s.bloom = Boolean.parseBoolean(value);
                    case "fxaa" -> s.fxaa = Boolean.parseBoolean(value);
                    case "vsync" -> s.vsync = Boolean.parseBoolean(value);
                    case "crispTextures" -> s.crispTextures = Boolean.parseBoolean(value);
                    default -> System.err.println("[qa] unknown option key '" + key + "'");
                }
            } catch (NumberFormatException e) {
                System.err.println("[qa] bad option value '" + pair + "'");
            }
        }
        game.frontend.applyGraphicsOptions(new GraphicsOptionsScreen.Result(
                GraphicsOptionsScreen.Action.APPLY, s.windowWidth, s.windowHeight));
        System.out.println("[qa] options applied+saved via APPLY path: " + script);
    }

    /** Drives the deferred options-screen mutation while the title options screen is up. */
    void updateTitleOptionsQa(float dt) {
        if (qaOptionsSet == null || qaOptionsSet.isBlank()) {
            return;
        }
        qaOptionsTimer += dt;
        if (qaOptionsTimer > 0.8f) {
            applyQaOptions(qaOptionsSet);
            qaOptionsSet = null;
        }
    }

    /** True when VEYLON_FRONTEND=loading pins the loading screen for a static capture. */
    boolean staticLoadingQa() {
        return staticLoadingQa;
    }

    void setStaticLoadingQa(boolean value) {
        staticLoadingQa = value;
    }

    // ------------------------------------------------------------------
    // Per-frame hooks
    // ------------------------------------------------------------------

    /** Advances whichever timed showcase the active scene selected. */
    void updateShowcases(double elapsed) {
        if (game.player == null) {
            return;
        }
        if (heldCycleShowcase) {
            updateHeldCycle(elapsed);
        }
        if (ashwolfSeqShowcase) {
            updateAshwolfSeq(elapsed);
        }
        if (benchmarkMovement) {
            updateBenchmarkMovement(elapsed);
        }
        if (uiCycleShowcase) {
            updateUiCycle(elapsed);
        }
    }

    /**
     * Progressive mining pantomime: crack decal grows over ~5 s while the
     * pickaxe swings and dust bursts off the struck face, so timed shots
     * prove dust + impact feedback + crack progression, not a static prop.
     */
    void updateMiningShowcase(float dt) {
        if (!miningShowcase) {
            return;
        }
        miningShowcaseElapsed += dt;
        miningShowcaseDustTimer -= dt;
        game.targetHit = Raycaster.castInto(game.world, game.camera.position, game.camera.front(),
                7.0, false, game.targetHitBuffer) ? game.targetHitBuffer : null;
        game.miningProgress = game.targetHit == null ? 0f
                : Math.min(0.95f, miningShowcaseElapsed * 0.19f);
        if (game.targetHit != null && miningShowcaseDustTimer <= 0f) {
            miningShowcaseDustTimer = 0.55f;
            game.swingTimer = Math.max(game.swingTimer, 0.35f);
            game.particles.blockDust(game.world.getBlock(
                            game.targetHit.x(), game.targetHit.y(), game.targetHit.z()),
                    game.targetHit.x() + 0.5f + game.targetHit.nx() * 0.55f,
                    game.targetHit.y() + 0.5f + game.targetHit.ny() * 0.55f,
                    game.targetHit.z() + 0.5f + game.targetHit.nz() * 0.55f, 6);
        }
    }

    void sampleRenderStats() {
        if (game.world == null || !game.appState.rendersWorld()) {
            return;
        }
        profiledRenderFrames++;
        int uiDraws = game.ui.drawCallsLastFrame();
        drawCallSum += game.renderer.drawCalls + uiDraws;
        triangleSum += game.renderer.trianglesRendered;
        peakDrawCalls = Math.max(peakDrawCalls, game.renderer.drawCalls + uiDraws);
        peakTriangles = Math.max(peakTriangles, game.renderer.trianglesRendered);
        peakParticles = Math.max(peakParticles, game.renderer.particlesDrawn);
        peakChunks = Math.max(peakChunks, game.renderer.chunksRendered);
        particleDrawCallSum += game.renderer.particleDrawCalls;
        peakParticleDrawCalls = Math.max(peakParticleDrawCalls, game.renderer.particleDrawCalls);
        int resident = 0;
        for (Chunk c : game.world.loadedChunks()) {
            if (c.meshOpaque != null || c.meshWater != null) resident++;
        }
        peakResidentChunkMeshes = Math.max(peakResidentChunkMeshes, resident);
    }

    // ------------------------------------------------------------------
    // Release smoke gate
    // ------------------------------------------------------------------

    /** Clears smoke-run accumulators before the loop starts sampling. */
    void resetSmokeRun() {
        fortressApproachProfiler.reset();
        smokeFortress = null;
        fortressApproaching = false;
        fortressApproachComplete = false;
    }

    /** Samples the dedicated fortress-approach profiler while the route is active. */
    void recordFortressApproach(double rawDt, double now) {
        if (!fortressApproaching) {
            return;
        }
        fortressApproachProfiler.record(rawDt);
        updateSmokeFortressApproach(now);
    }

    /**
     * Emits the benchmark/smoke report and returns a failure description, or
     * {@code null} when every release gate passed.
     */
    String emitSmokeReport(double duration, boolean saveOk, boolean loadOk) {
        FrameProfiler.Snapshot timing = game.frameProfiler.snapshot();
        FrameProfiler.Snapshot fortressTiming = fortressApproachProfiler.snapshot();
        double avgDraws = profiledRenderFrames == 0 ? 0 : drawCallSum / (double) profiledRenderFrames;
        double avgTriangles = profiledRenderFrames == 0 ? 0 : triangleSum / profiledRenderFrames;
        double avgParticleDraws = profiledRenderFrames == 0 ? 0
                : particleDrawCallSum / (double) profiledRenderFrames;
        boolean iconsOk = game.ui.iconAtlas().itemIconCount() == ItemType.values().length
                && game.ui.iconAtlas().validationErrors().isEmpty();
        boolean glOk = game.window.glErrorCount() == 0 && game.window.glDebugErrorCount() == 0;
        boolean target60 = timing.averageFps() >= 60.0;
        double minimumFps = Math.max(60.0, configuredMinimumSmokeFps());
        boolean performanceOk = timing.averageFps() >= minimumFps;
        boolean materialsOk = com.veylon.gfx.MaterialRegistry.errors().isEmpty();
        RuntimeBudgetSnapshot runtime = RuntimeBudgetSnapshot.capture(game);
        boolean runtimeBoundsOk = runtime.withinHardLimits();
        boolean fortressApproachOk = game.world.generatorVersion < World.GEN_DEEP
                || fortressApproachComplete && smokeFortress != null
                && fortressTiming.frames() > 0 && fortressTiming.averageFps() >= 60.0;

        System.out.printf(Locale.ROOT,
                "[benchmark] resolution=%dx%d duration=%.1fs seed=%d avgFps=%.1f avgMs=%.2f "
                        + "p95Ms=%.2f p99Ms=%.2f maxMs=%.2f target60=%s minFps=%.1f performanceGate=%s%n",
                game.window.framebufferWidth(), game.window.framebufferHeight(), duration,
                game.sessionSeed, timing.averageFps(), timing.averageMs(), timing.p95Ms(),
                timing.p99Ms(), timing.maxMs(), target60, minimumFps, performanceOk);
        GraphicsSettings settings = game.renderer.settings;
        System.out.printf(Locale.ROOT,
                "[benchmark] settings={renderDistance=%d shadows=%d bloom=%s fxaa=%s particles=%.2f "
                        + "fov=%.0f uiScale=%.2f vsync=%s fullscreen=%s motion=%.2f}%n",
                settings.renderDistance, settings.shadowQuality, settings.bloom, settings.fxaa,
                settings.particleDensity, settings.fov, settings.uiScale, settings.vsync,
                settings.fullscreen, settings.motion);
        System.out.printf(Locale.ROOT,
                "[benchmark] avgDrawCalls=%.1f peakDrawCalls=%d avgTriangles=%.0f peakTriangles=%d "
                        + "peakChunks=%d peakParticles=%d particleSubmissionsAvg=%.2f "
                        + "particleSubmissionsPeak=%d loadedChunks=%d liveParticles=%d%n",
                avgDraws, peakDrawCalls, avgTriangles, peakTriangles, peakChunks, peakParticles,
                avgParticleDraws, peakParticleDrawCalls, game.world.loadedCount(),
                game.particles.count);
        System.out.printf(Locale.ROOT,
                "[benchmark] chunkMeshes={rebuilt=%d deleted=%d peakResident=%d loadedBlockChunks=%d}%n",
                game.renderer.chunkMeshRebuildsTotal, game.chunkMeshDeletes,
                peakResidentChunkMeshes, game.world.loadedCount());
        System.out.println("[benchmark] context=" + game.window.glContextSummary());
        System.out.println("[smoke] save=" + saveOk + " load=" + loadOk
                + " assets={materials=" + com.veylon.gfx.MaterialRegistry.materialCount()
                + " fontGlyphs=" + game.ui.fontRenderer().glyphCount()
                + " itemIcons=" + game.ui.iconAtlas().itemIconCount() + "/" + ItemType.values().length + "}"
                + " glErrors=" + game.window.glErrorCount()
                + " khrErrors=" + game.window.glDebugErrorCount()
                + " creatures=" + game.entities.creatureCount() + " npcs=" + game.entities.npcCount()
                + " tracks=" + game.entities.tracks.size() + " fires=" + game.fire.count());
        System.out.println("[smoke] runtime=" + runtime.occupancySummary()
                + " withinHardLimits=" + runtimeBoundsOk);
        System.out.println("[smoke] hardLimits={" + RuntimeBudgetSnapshot.hardLimitSummary() + "}");
        System.out.println("[smoke] fortressPlan=" + deterministicFortressSnapshot());
        System.out.printf(Locale.ROOT,
                "[smoke] fortressApproach={complete=%s,id=%s,frames=%d,avgFps=%.1f,"
                        + "p95Ms=%.2f,p99Ms=%.2f,maxMs=%.2f,meshRebuilds=%d,"
                        + "loadedChunkDelta=%d} target60=%s%n",
                fortressApproachComplete,
                smokeFortress == null ? "none" : Long.toUnsignedString(smokeFortress.id),
                fortressTiming.frames(), fortressTiming.averageFps(), fortressTiming.p95Ms(),
                fortressTiming.p99Ms(), fortressTiming.maxMs(),
                Math.max(0, game.renderer.chunkMeshRebuildsTotal - fortressApproachMeshStart),
                game.world.loadedCount() - fortressApproachChunkStart, fortressApproachOk);

        StringBuilder failure = new StringBuilder();
        if (!saveOk) failure.append("isolated save failed; ");
        if (!loadOk) failure.append("isolated load failed; ");
        if (!materialsOk) failure.append("material validation failed; ");
        if (!iconsOk) failure.append("item icon validation failed; ");
        if (!performanceOk) failure.append(String.format(Locale.ROOT,
                "average FPS %.1f below required %.1f; ", timing.averageFps(), minimumFps));
        if (!glOk) failure.append("OpenGL errors observed; ");
        if (!runtimeBoundsOk) failure.append("runtime collection limit exceeded; ");
        if (!fortressApproachOk) {
            failure.append("generated-fortress approach did not sustain 60 FPS; ");
        }
        return failure.isEmpty() ? null : failure.toString();
    }

    /**
     * Starts the deterministic smoke route outside the nearest planned fortress.
     * The route remains outside the wall so it measures normal chunk streaming,
     * settlement activation, structure meshing and rendering without fabricating
     * a cleared/disabled combat state.
     */
    void beginSmokeFortressApproach(double now) {
        Settlement planned = nearestPlannedFortress();
        if (planned == null) {
            System.out.println("[smoke] no fortress found for approach inside "
                    + FORTRESS_SEARCH_RADIUS_REGIONS + " regions");
            return;
        }
        smokeFortress = game.world.settlementForRegion(planned.regionX, planned.regionZ);
        if (smokeFortress == null || smokeFortress.type != SettlementType.FORTRESS) {
            smokeFortress = null;
            System.out.println("[smoke] planned fortress failed deterministic registration");
            return;
        }
        game.world.layoutFor(smokeFortress);

        float dx = game.spawnPos.x - (smokeFortress.center.x() + 0.5f);
        float dz = game.spawnPos.z - (smokeFortress.center.z() + 0.5f);
        float length = (float) Math.sqrt(dx * dx + dz * dz);
        if (length < 0.001f) {
            dx = 0;
            dz = 1;
            length = 1;
        }
        dx /= length;
        dz /= length;
        float centerX = smokeFortress.center.x() + 0.5f;
        float centerZ = smokeFortress.center.z() + 0.5f;
        fortressApproachStart.set(centerX + dx * 180f, 0, centerZ + dz * 180f);
        fortressApproachEnd.set(centerX + dx * 65f, 0, centerZ + dz * 65f);
        fortressApproachStarted = now;
        fortressApproachMeshStart = game.renderer.chunkMeshRebuildsTotal;
        fortressApproachChunkStart = game.world.loadedCount();
        fortressApproachProfiler.reset();
        fortressApproaching = true;
        placeSmokePlayerAt(fortressApproachStart.x, fortressApproachStart.z);
        System.out.println("[smoke] fortress approach started id="
                + Long.toUnsignedString(smokeFortress.id) + " center="
                + smokeFortress.center.x() + "," + smokeFortress.center.z());
    }

    private void updateSmokeFortressApproach(double now) {
        float progress = (float) Math.min(1.0,
                Math.max(0.0, (now - fortressApproachStarted) / FORTRESS_APPROACH_SECONDS));
        float x = fortressApproachStart.x
                + (fortressApproachEnd.x - fortressApproachStart.x) * progress;
        float z = fortressApproachStart.z
                + (fortressApproachEnd.z - fortressApproachStart.z) * progress;
        placeSmokePlayerAt(x, z);
        if (progress >= 1f) {
            fortressApproaching = false;
            fortressApproachComplete = true;
            System.out.println("[smoke] fortress approach completed at distance="
                    + Math.round(Math.sqrt(
                            smokeFortress.distSqTo(game.player.pos.x, game.player.pos.z))));
        }
    }

    private void placeSmokePlayerAt(float x, float z) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        float y = game.world.generator.heightAt(blockX, blockZ) + 1.4f;
        game.player.pos.set(x, y, z);
        game.player.vel.zero();
    }

    /**
     * Stable planner-level fortress marker for comparing smoke runs without
     * mutating the loaded-chunk frontier merely to collect the metric.
     */
    private String deterministicFortressSnapshot() {
        Settlement settlement = nearestPlannedFortress();
        if (game.world.generatorVersion < World.GEN_DEEP) {
            return "{available=false,generator=legacy}";
        }
        if (settlement == null) {
            return "{available=false,searchRadiusRegions=" + FORTRESS_SEARCH_RADIUS_REGIONS + "}";
        }
        double distance = Math.sqrt(settlement.distSqTo(game.spawnPos.x, game.spawnPos.z));
        boolean registered = game.world.settlements.containsKey(settlement.id);
        return String.format(Locale.ROOT,
                "{available=true,id=%d,region=%d,%d,center=%d,%d,distance=%.0f,registered=%s}",
                settlement.id, settlement.regionX, settlement.regionZ,
                settlement.center.x(), settlement.center.z(), distance, registered);
    }

    private Settlement nearestPlannedFortress() {
        if (game.world == null || game.world.generatorVersion < World.GEN_DEEP) {
            return null;
        }
        int originRx = SettlementPlanner.regionOfBlock((int) Math.floor(game.spawnPos.x));
        int originRz = SettlementPlanner.regionOfBlock((int) Math.floor(game.spawnPos.z));
        for (int radius = 0; radius <= FORTRESS_SEARCH_RADIUS_REGIONS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    Settlement settlement = SettlementPlanner.plan(
                            game.world.seed, game.world.generator, originRx + dx, originRz + dz);
                    if (settlement == null || settlement.type != SettlementType.FORTRESS) {
                        continue;
                    }
                    return settlement;
                }
            }
        }
        return null;
    }

    /** Optional stricter release gate layered over the mandatory 60 FPS target. */
    private double configuredMinimumSmokeFps() {
        String configured = System.getenv("VEYLON_MIN_FPS");
        if (configured == null || configured.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Double.parseDouble(configured.trim()));
        } catch (NumberFormatException e) {
            System.err.println("[smoke] ignoring invalid VEYLON_MIN_FPS='" + configured + "'");
            return 0;
        }
    }

    // ------------------------------------------------------------------
    // Benchmark scenes
    // ------------------------------------------------------------------

    /**
     * Dev/QA benchmark scenes (VEYLON_SCENE env): reproducible lighting/weather
     * setups for visual regression screenshots. No effect when unset.
     */
    void applyBenchmarkScene(String scene) {
        if (scene == null || scene.isBlank()) {
            return;
        }
        String normalized = scene.trim().toLowerCase(Locale.ROOT);
        Vec3i site;
        switch (normalized) {
            case "day", "meadow" -> {
                site = moveBenchmarkToBiome(Biome.MEADOW);
                clearBenchmarkStage(site, 9, 18);
                game.time.totalMinutes = 12 * 60;
                setWeatherNow(WeatherSystem.Weather.CLEAR);
                stageMeadowWater(site);
                stageMeadowLife(site);
                clearHeldForScenicCapture();
            }
            case "dawnfog", "pinefog" -> {
                site = moveBenchmarkToBiome(Biome.PINE_FOREST);
                clearBenchmarkStage(site, 9, 20);
                game.time.totalMinutes = (long) (6.15 * 60);
                setWeatherNow(WeatherSystem.Weather.FOG);
                stagePineFog(site);
                clearHeldForScenicCapture();
            }
            case "nightfire", "campfire_rain" -> {
                site = moveBenchmarkToBiome(Biome.MEADOW);
                clearBenchmarkStage(site, 8, 16);
                game.time.totalMinutes = 23 * 60;
                setWeatherNow(WeatherSystem.Weather.RAIN);
                Vec3i fp = stageBlock(site, 0, -7, BlockType.CAMPFIRE);
                game.world.campfireFuel.put(fp, 4000f);
                stageBlock(site, -4, -8, BlockType.WORKBENCH);
                stageBlock(site, 4, -8, BlockType.CRATE);
                stageBlock(site, -3, -12, BlockType.FURNACE);
                stageBlock(site, 3, -12, BlockType.TORCH);
                for (int i = 0; i < 12; i++) {
                    game.particles.smoke(fp.x() + 0.5f, fp.y() + 0.7f, fp.z() + 0.5f, 0.7f);
                    game.particles.flame(fp.x() + 0.5f, fp.y() + 0.15f, fp.z() + 0.5f);
                }
                clearHeldForScenicCapture();
            }
            case "ruin", "cave_ruin" -> {
                site = moveBenchmarkToBiome(Biome.ROCKY_HIGHLANDS);
                clearBenchmarkStage(site, 9, 18);
                game.time.totalMinutes = 22 * 60;
                setWeatherNow(WeatherSystem.Weather.CLEAR);
                stageRuin(site);
                clearHeldForScenicCapture();
            }
            case "toxic", "ashfall" -> {
                site = moveBenchmarkToBiome(Biome.SCRUBLAND);
                clearBenchmarkStage(site, 9, 20);
                game.time.totalMinutes = 14 * 60;
                setWeatherNow(WeatherSystem.Weather.CLEAR);
                game.events.active.add(new EventSystem.ActiveEvent(
                        EventSystem.EventType.TOXIC_FOG, 3000f, 1f));
                for (int i = 0; i < 24; i++) {
                    game.particles.toxicMote(site.x() - 5 + (i % 8) * 1.4f,
                            site.y() + 0.2f + (i % 3) * 0.5f,
                            site.z() - 5 - (i / 8) * 3f);
                }
                stageToxicWastes(site);
                clearHeldForScenicCapture();
            }
            case "ao_shadow", "lighting" -> {
                site = moveBenchmarkToBiome(Biome.MEADOW);
                // Cleared through z-26 so the south-facing comparison camera at
                // z-24 stands on level ground.
                clearBenchmarkStage(site, 10, 26);
                // Low-ish western sun: pillar shadows stretch ~1x height onto
                // open grass, so the on/off comparison is unmistakable.
                game.time.totalMinutes = (long) (17.7 * 60);
                setWeatherNow(WeatherSystem.Weather.CLEAR);
                stageAoShadow(site);
                clearHeldForScenicCapture();
                game.simPaused = true;
            }
            case "phase4", "verticalslice" -> {
                site = moveBenchmarkToBiome(Biome.MEADOW);
                clearBenchmarkStage(site, 11, 22);
                setupPhase4Showcase(site.x(), site.z());
            }
            case "ashwolf", "ashwolf_poses" -> {
                site = moveBenchmarkToBiome(Biome.MEADOW);
                clearBenchmarkStage(site, 9, 16);
                setupAshwolfPoseShowcase(site);
            }
            case "ashwolf_seq", "ashwolf_states" -> {
                site = moveBenchmarkToBiome(Biome.MEADOW);
                clearBenchmarkStage(site, 8, 14);
                setupAshwolfSeqShowcase(site);
            }
            case "silhouette30", "silhouette_30m" -> {
                site = moveBenchmarkToBiome(Biome.MEADOW);
                clearBenchmarkStage(site, 13, 38);
                setupSilhouette30mShowcase(site);
            }
            case "held_pickaxe", "held_axe", "held_spear", "held_knife", "held_torch",
                    "held_food", "held_berry", "held_medicine", "held_building" -> {
                site = moveBenchmarkToBiome(Biome.MEADOW);
                clearBenchmarkStage(site, 11, 22);
                setupPhase4Showcase(site.x(), site.z());
                selectShowcaseItem(normalized);
            }
            case "held_cycle" -> {
                site = moveBenchmarkToBiome(Biome.MEADOW);
                clearBenchmarkStage(site, 11, 22);
                setupPhase4Showcase(site.x(), site.z());
                heldCycleShowcase = true;
                updateHeldCycle(0);
            }
            case "vfx_blood", "blood_tracks" -> {
                site = moveBenchmarkToBiome(Biome.MEADOW);
                clearBenchmarkStage(site, 10, 18);
                setupBloodVfxShowcase(site);
            }
            case "vfx_mining", "mining_cracks" -> {
                site = moveBenchmarkToBiome(Biome.ROCKY_HIGHLANDS);
                clearBenchmarkStage(site, 10, 18);
                setupMiningVfxShowcase(site);
            }
            case "vfx_beacon", "beacon_vfx" -> {
                site = moveBenchmarkToBiome(Biome.MEADOW);
                clearBenchmarkStage(site, 10, 20);
                setupBeaconVfxShowcase(site);
            }
            case "movement", "chunk_churn" -> {
                site = moveBenchmarkToBiome(Biome.MEADOW);
                clearBenchmarkStage(site, 8, 16);
                game.time.totalMinutes = 11 * 60;
                setWeatherNow(WeatherSystem.Weather.CLEAR);
                benchmarkMovementStart.set(game.player.pos);
                benchmarkMovement = true;
            }
            case "inventory", "icons" -> {
                site = moveBenchmarkToBiome(Biome.MEADOW);
                clearBenchmarkStage(site, 8, 14);
                setupInventoryShowcase();
            }
            case "ui_cycle", "screens" -> {
                site = moveBenchmarkToBiome(Biome.MEADOW);
                clearBenchmarkStage(site, 11, 22);
                setupPhase4Showcase(site.x(), site.z());
                setupInventoryShowcase();
                uiCycleShowcase = true;
                updateUiCycle(0);
            }
            default -> System.out.println("[scene] unknown VEYLON_SCENE '" + scene + "'");
        }
        System.out.println("[scene] applied benchmark scene '" + scene + "'");
    }

    /** Finds a nearby deterministic dry site in the requested biome and moves the QA camera there. */
    private Vec3i moveBenchmarkToBiome(Biome desired) {
        World world = game.world;
        int ox = (int) Math.floor(game.spawnPos.x);
        int oz = (int) Math.floor(game.spawnPos.z);
        int bx = ox, bz = oz;
        boolean found = false;
        outer:
        for (int radius = 0; radius <= 1024; radius += 8) {
            for (int dx = -radius; dx <= radius; dx += 8) {
                int[] zs = radius == 0 ? new int[]{0} : new int[]{-radius, radius};
                for (int dz : zs) {
                    if (world.biomeAt(ox + dx, oz + dz) == desired) {
                        bx = ox + dx;
                        bz = oz + dz;
                        found = true;
                        break outer;
                    }
                }
            }
            for (int dz = -radius + 8; dz <= radius - 8; dz += 8) {
                for (int dx : new int[]{-radius, radius}) {
                    if (world.biomeAt(ox + dx, oz + dz) == desired) {
                        bx = ox + dx;
                        bz = oz + dz;
                        found = true;
                        break outer;
                    }
                }
            }
        }
        if (!found) {
            System.err.println("[scene] no nearby " + desired.displayName + "; using spawn biome");
        }
        world.ensureChunks(bx, bz, 4, 10_000);

        int sx = bx, sz = bz;
        search:
        for (int radius = 0; radius <= 48; radius += 4) {
            for (int dx = -radius; dx <= radius; dx += 4) {
                for (int dz = -radius; dz <= radius; dz += 4) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = bx + dx, z = bz + dz;
                    int surface = world.surfaceHeight(x, z);
                    if (world.biomeAt(x, z) == desired && surface > World.SEA_LEVEL + 1
                            && world.getBlock(x, surface + 1, z) != BlockType.WATER) {
                        sx = x;
                        sz = z;
                        break search;
                    }
                }
            }
        }
        int naturalGround = world.surfaceHeight(sx, sz);
        int ground = Math.max(naturalGround, World.SEA_LEVEL + 3);
        Biome siteBiome = world.biomeAt(sx, sz);
        for (int y = naturalGround + 1; y < ground; y++) {
            world.setBlock(sx, y, sz, siteBiome.subsurface, false);
        }
        world.setBlock(sx, ground, sz, siteBiome.surface, false);
        for (int y = ground + 1; y <= Math.min(Chunk.SY - 1, ground + 5); y++) {
            world.setBlock(sx, y, sz, BlockType.AIR, false);
        }
        game.player.pos.set(sx + 0.5f, ground + 1.2f, sz + 0.5f);
        game.player.vel.zero();
        game.player.biome = world.biomeAt(sx, sz);
        game.camera.yaw = 0f;
        game.camera.pitch = 4f;
        System.out.println("[scene] camera site=" + sx + "," + (ground + 1) + "," + sz
                + " biome=" + game.player.biome.displayName
                + " sea=" + World.SEA_LEVEL
                + " feet=" + world.getBlock(sx, ground + 1, sz)
                + " eye=" + world.getBlock(sx, ground + 2, sz));
        return new Vec3i(sx, ground + 1, sz);
    }

    /**
     * Builds a dry, solid QA deck and clears its composition volume. The previous
     * version only replaced the top voxel in front of the camera; hidden water
     * columns and caves could therefore flood or drop a timed showcase.
     */
    private void clearBenchmarkStage(Vec3i site, int halfWidth, int depth) {
        World world = game.world;
        int targetGround = site.y() - 1;
        Biome biome = world.biomeAt(site.x(), site.z());
        for (int dx = -halfWidth; dx <= halfWidth; dx++) {
            for (int dz = 4; dz >= -depth; dz--) {
                int x = site.x() + dx, z = site.z() + dz;
                for (int y = Math.max(1, targetGround - 5); y < targetGround; y++) {
                    world.setBlock(x, y, z, biome.subsurface, false);
                }
                world.setBlock(x, targetGround, z, biome.surface, false);
                for (int y = targetGround + 1; y <= Math.min(Chunk.SY - 1, targetGround + 8); y++) {
                    BlockType existing = world.getBlock(x, y, z);
                    if (existing != BlockType.AIR) {
                        world.setBlock(x, y, z, BlockType.AIR, false);
                    }
                }
            }
        }
        game.player.pos.set(site.x() + 0.5f, targetGround + 1.2f, site.z() + 0.5f);
        game.player.vel.zero();
        game.player.inWater = false;
        game.camera.position.set(game.player.pos.x,
                game.player.pos.y + game.player.eyeHeight(), game.player.pos.z);
    }

    private Vec3i stageBlock(Vec3i site, int dx, int dz, BlockType type) {
        int x = site.x() + dx, z = site.z() + dz;
        int y = game.world.surfaceHeight(x, z) + 1;
        game.world.setBlock(x, y, z, type, false);
        return new Vec3i(x, y, z);
    }

    private void stageMeadowWater(Vec3i site) {
        for (int dx = 4; dx <= 8; dx++) {
            for (int dz = -12; dz <= -8; dz++) {
                int x = site.x() + dx, z = site.z() + dz;
                int ground = game.world.surfaceHeight(x, z);
                game.world.setBlock(x, ground, z, BlockType.WATER, false);
            }
        }
        stageBlock(site, -5, -10, BlockType.WORKBENCH);
        stageBlock(site, -2, -12, BlockType.CRATE);
    }

    private void clearHeldForScenicCapture() {
        game.player.inventory.set(0, null);
        game.player.hotbarSel = 0;
    }

    private void stageMeadowLife(Vec3i site) {
        stageTree(site, -7, -14, 3, false);
        stageTree(site, 7, -17, 4, false);
        stageTree(site, -9, -20, 4, false);
        int[][] plants = {
                {-7, -6}, {-5, -9}, {-3, -15}, {0, -8}, {2, -14}, {5, -6},
                {7, -11}, {-8, -18}, {4, -18}, {8, -20}
        };
        for (int i = 0; i < plants.length; i++) {
            stageBlock(site, plants[i][0], plants[i][1], switch (i % 4) {
                case 0 -> BlockType.TALL_GRASS;
                case 1 -> BlockType.HERB_PLANT;
                case 2 -> BlockType.BERRY_BUSH;
                default -> BlockType.BUSH;
            });
        }
    }

    private void stagePineFog(Vec3i site) {
        int[][] pines = {
                {-7, -8, 4}, {6, -9, 5}, {-4, -14, 6}, {4, -16, 5},
                {-8, -20, 6}, {8, -22, 7}, {0, -25, 6}
        };
        for (int[] p : pines) {
            stageTree(site, p[0], p[1], p[2], true);
        }
        for (int i = -7; i <= 7; i += 2) {
            stageBlock(site, i, -6 - Math.floorMod(i * 3, 9), BlockType.TALL_GRASS);
        }
    }

    private void stageTree(Vec3i site, int dx, int dz, int trunkHeight, boolean pine) {
        World world = game.world;
        int x = site.x() + dx, z = site.z() + dz;
        int y = world.surfaceHeight(x, z) + 1;
        for (int i = 0; i < trunkHeight; i++) {
            world.setBlock(x, y + i, z, BlockType.LOG, false);
        }
        if (pine) {
            for (int layer = 0; layer < 3; layer++) {
                int ly = y + trunkHeight - 1 + layer;
                int radius = layer == 0 ? 2 : 1;
                for (int lx = -radius; lx <= radius; lx++) {
                    for (int lz = -radius; lz <= radius; lz++) {
                        if (Math.abs(lx) + Math.abs(lz) <= radius + 1) {
                            world.setBlock(x + lx, ly, z + lz, BlockType.LEAVES, false);
                        }
                    }
                }
            }
            world.setBlock(x, y + trunkHeight + 2, z, BlockType.LEAVES, false);
        } else {
            for (int lx = -2; lx <= 2; lx++) {
                for (int lz = -2; lz <= 2; lz++) {
                    for (int ly = 0; ly <= 2; ly++) {
                        if (Math.abs(lx) + Math.abs(lz) + ly < 5) {
                            world.setBlock(x + lx, y + trunkHeight - 1 + ly, z + lz,
                                    BlockType.LEAVES, false);
                        }
                    }
                }
            }
        }
    }

    private void stageToxicWastes(Vec3i site) {
        for (int x = -8; x <= 8; x += 4) {
            int z = -8 - Math.floorMod(x * 5, 11);
            stageBlock(site, x, z, x % 8 == 0 ? BlockType.ASH : BlockType.SCRAP_BLOCK);
        }
        for (int i = 0; i < 5; i++) {
            int x = site.x() - 6 + i * 3;
            int z = site.z() - 13 - (i % 2) * 3;
            int y = game.world.surfaceHeight(x, z) + 1;
            game.world.setBlock(x, y, z, BlockType.LOG, false);
            game.world.setBlock(x, y + 1, z, BlockType.ASH, false);
            game.particles.smoke(x + 0.5f, y + 1.2f, z + 0.5f, 0.45f);
        }
    }

    /** Repeated recesses expose vertex AO; staggered pillars cast long sun shadows. */
    private void stageAoShadow(Vec3i site) {
        World world = game.world;
        int ground = site.y() - 1;
        // Spacing 3 leaves open grass east of every pillar, so cast shadows land
        // on the ground instead of on the neighboring pillar.
        for (int x = -7; x <= 7; x += 3) {
            int z = site.z() - 8 - Math.floorMod(x + 7, 6);
            int h = 2 + Math.floorMod(x + 7, 3);
            for (int y = 1; y <= h; y++) {
                world.setBlock(site.x() + x, ground + y, z, BlockType.STONE, false);
            }
            // Side blocks create stable concave corners for AO comparison.
            world.setBlock(site.x() + x + 1, ground + 1, z, BlockType.STONE, false);
        }
        // Staircase off to the left so it exposes stepped AO corners without
        // walling the pillar row off from the south-facing comparison camera.
        for (int step = 0; step < 5; step++) {
            for (int x = -9; x <= -5; x++) {
                world.setBlock(site.x() + x, ground + 1 + step,
                        site.z() - 15 - step, BlockType.RUIN_STONE, false);
            }
        }
        // View from the north looking back south: the engine sun always sits in
        // the southern sky, so from the default north-facing camera every cast
        // shadow hides directly behind its own pillar. From here the shadows
        // stretch toward the camera across open grass.
        game.player.pos.set(site.x() + 0.5f, site.y() + 0.2f, site.z() - 24 + 0.5f);
        game.player.vel.zero();
        game.camera.yaw = 180f;
        game.camera.pitch = 8f;
    }

    private void stageRuin(Vec3i site) {
        World world = game.world;
        int z = site.z() - 11;
        int baseY = Math.max(world.surfaceHeight(site.x(), z), site.y() - 1) + 1;
        for (int xOff : new int[]{-3, 3}) {
            for (int y = 0; y < 5; y++) {
                world.setBlock(site.x() + xOff, baseY + y, z, BlockType.RUIN_STONE, false);
            }
        }
        for (int x = -3; x <= 3; x++) {
            world.setBlock(site.x() + x, baseY + 4, z, BlockType.RUIN_STONE, false);
        }
        for (int x = -6; x <= 6; x += 3) {
            world.setBlock(site.x() + x, baseY, z - 3, BlockType.RUIN_STONE, false);
        }
        world.setBlock(site.x(), baseY + 1, z - 1, BlockType.RUIN_CORE, false);
        stageBlock(site, -5, -8, BlockType.TORCH);
        stageBlock(site, 5, -8, BlockType.TORCH);
    }

    private void selectShowcaseItem(String scene) {
        ItemType item = switch (scene) {
            case "held_axe" -> ItemType.IRON_AXE;
            case "held_spear" -> ItemType.IRON_SPEAR;
            case "held_knife" -> ItemType.IRON_KNIFE;
            case "held_torch" -> ItemType.TORCH;
            case "held_food" -> ItemType.COOKED_MEAT;
            case "held_berry" -> ItemType.BERRY;
            case "held_medicine" -> ItemType.MEDICINE;
            case "held_building" -> ItemType.WORKBENCH;
            default -> ItemType.IRON_PICKAXE;
        };
        game.player.inventory.set(0, new ItemStack(item, 1));
        game.player.hotbarSel = 0;
    }

    private void updateHeldCycle(double elapsed) {
        ItemType[] items = {
                ItemType.IRON_PICKAXE, ItemType.IRON_AXE, ItemType.IRON_SPEAR,
                ItemType.IRON_KNIFE, ItemType.TORCH, ItemType.COOKED_MEAT,
                ItemType.MEDICINE, ItemType.WORKBENCH
        };
        int index = Math.min(items.length - 1, Math.max(0, (int) Math.floor(elapsed)));
        ItemStack selected = game.player.inventory.get(0);
        if (selected == null || selected.type != items[index]) {
            game.player.inventory.set(0, new ItemStack(items[index], 1));
            game.player.hotbarSel = 0;
        }
    }

    /**
     * Opt-in visual regression path for every gameplay overlay. It uses the same
     * screen instances and update methods as player input, but supplies stable
     * crate/NPC data so a single unattended run can catch layout regressions.
     */
    private void updateUiCycle(double elapsed) {
        int screen = Math.min(6, Math.max(0, (int) Math.floor(elapsed)));
        game.simPaused = true;
        game.simPanelShown = false;
        switch (screen) {
            case 0 -> game.uiMode = Game.UiMode.INVENTORY;
            case 1 -> game.uiMode = Game.UiMode.CRAFTING;
            case 2 -> game.uiMode = Game.UiMode.MAP;
            case 3 -> game.uiMode = Game.UiMode.PAUSE;
            case 4 -> {
                game.uiMode = Game.UiMode.NONE;
                game.simPanelShown = true;
            }
            case 5 -> {
                if (game.openCrate == null) {
                    game.openCrate = new Inventory(12);
                    ItemType[] items = ItemType.values();
                    for (int i = 0; i < game.openCrate.size(); i++) {
                        game.openCrate.set(i,
                                new ItemStack(items[(i * 5 + 3) % items.length], i % 4 + 1));
                    }
                    game.openCratePos = new Vec3i((int) game.player.pos.x, (int) game.player.pos.y,
                            (int) game.player.pos.z - 5);
                }
                game.uiMode = Game.UiMode.CRATE;
            }
            default -> {
                if (game.activeNpc == null || game.activeNpc.dead) {
                    game.activeNpc = game.entities.npcs.stream().filter(n -> n.isTrader).findFirst()
                            .orElseGet(() -> game.entities.npcs.isEmpty()
                                    ? null : game.entities.npcs.getFirst());
                    game.npcScreen.open();
                }
                game.uiMode = game.activeNpc != null ? Game.UiMode.NPC : Game.UiMode.NONE;
            }
        }
    }

    private void setupInventoryShowcase() {
        ItemType[] items = ItemType.values();
        for (int i = 0; i < game.player.inventory.size(); i++) {
            game.player.inventory.set(i, new ItemStack(items[i % items.length], i % 4 + 1));
        }
        game.inventoryScreen.reset();
        game.uiMode = Game.UiMode.INVENTORY;
        game.simPaused = true;
        game.time.totalMinutes = 12 * 60;
        setWeatherNow(WeatherSystem.Weather.CLEAR);
    }

    /**
     * One-frame Ashwolf core-set lineup. All live wolves share the cached model,
     * so eight visibly different poses plus a carcass also exercise resetPose()
     * between consecutive submissions and make pose leakage apparent.
     */
    private void setupAshwolfPoseShowcase(Vec3i site) {
        // Mid-afternoon: high warm sun, no dusk haze, so silhouettes stay crisp.
        game.time.totalMinutes = (long) (15.5 * 60);
        setWeatherNow(WeatherSystem.Weather.CLEAR);
        clearStagedEntities();

        Creature.CreatureState[] states = {
                Creature.CreatureState.WANDER, Creature.CreatureState.WANDER,
                Creature.CreatureState.HUNT, Creature.CreatureState.STALK,
                Creature.CreatureState.CHARGE, Creature.CreatureState.ATTACK,
                Creature.CreatureState.FLEE_HURT, Creature.CreatureState.REST
        };
        for (int i = 0; i < states.length; i++) {
            int x = site.x() - 7 + i * 2;
            int z = site.z() - 9 - (i % 2);
            int y = game.world.surfaceHeight(x, z) + 1;
            Creature wolf = game.entities.spawnCreature(game.world, Creature.CreatureType.WOLF,
                    x + 0.5f, y + 0.08f, z + 0.5f);
            // Side-on to the camera: skulk height, tail and leg splay differences
            // are silhouette features, and they vanish in a head-on view.
            wolf.yaw = 90f;
            wolf.state = states[i];
            wolf.decideTimer = POSE_FREEZE_SECONDS;
            wolf.bobPhase = POSE_MAX_SWING_PHASE;
            wolf.onGround = true;
            wolf.vel.x = switch (i) {
                case 1, 3 -> 1.15f; // walk / stalk
                case 2, 4, 6 -> 3.4f; // run / charge / hit recoil
                default -> 0f;
            };
        }

        int deadX = site.x() + 9, deadZ = site.z() - 8;
        game.entities.carcasses.add(new Carcass(Creature.CreatureType.WOLF,
                deadX + 0.5f, game.world.surfaceHeight(deadX, deadZ) + 1.08f, deadZ + 0.5f));

        for (int i = 0; i < 3; i++) {
            int x = site.x() - 6 + i * 6;
            int z = site.z() - 14;
            int y = game.world.surfaceHeight(x, z) + 1;
            Npc npc = game.entities.spawnNpc(game.world,
                    i == 0 ? "Maro - Guard" : (i == 1 ? "Ressk - Trader" : "Vex - Raider"),
                    x + 0.5f, y + 0.08f, z + 0.5f);
            npc.yaw = 180f;
            npc.campIndex = i;
            npc.faction = i == 0 ? game.faction : null;
            npc.isTrader = i == 1;
            npc.raider = i == 2;
            npc.state = i == 2 ? Npc.NpcState.RAID : Npc.NpcState.IDLE;
            npc.interactFreeze = POSE_FREEZE_SECONDS;
        }
        Vec3i firePos = stageBlock(site, 8, -6, BlockType.CAMPFIRE);
        game.world.campfireFuel.put(firePos, 4000f);
        for (int i = 0; i < 8; i++) {
            game.particles.flame(firePos.x() + 0.5f, firePos.y() + 0.15f, firePos.z() + 0.5f);
            game.particles.smoke(firePos.x() + 0.5f, firePos.y() + 0.7f, firePos.z() + 0.5f, 0.6f);
        }
        // No held item: the earlier capture had the pickaxe viewmodel occluding
        // the center of the pose lineup.
        clearHeldForScenicCapture();
        game.simPaused = true;
        game.camera.yaw = 0f;
        game.camera.pitch = 6f;
    }

    /**
     * Deterministic single-wolf state sequence: one second per core state, side-on
     * and close to the camera, so timed captures prove each pose separately.
     * Schedule (seconds -> state): 0 idle, 1 walk, 2 run, 3 stalk, 4 charge,
     * 5 attack, 6 hit-recoil, 7 rest, 8 death (carcass, same cached model),
     * 9 idle again — identical to second 0 only if no pose leaked from the
     * carcass render between them.
     */
    private void setupAshwolfSeqShowcase(Vec3i site) {
        game.time.totalMinutes = (long) (15.5 * 60);
        setWeatherNow(WeatherSystem.Weather.CLEAR);
        clearStagedEntities();

        int x = site.x(), z = site.z() - 6;
        int y = game.world.surfaceHeight(x, z) + 1;
        ashwolfSeqWolf = game.entities.spawnCreature(game.world, Creature.CreatureType.WOLF,
                x + 0.5f, y + 0.08f, z + 0.5f);
        ashwolfSeqWolf.yaw = 90f;
        ashwolfSeqWolf.decideTimer = POSE_FREEZE_SECONDS;
        ashwolfSeqWolf.bobPhase = POSE_MAX_SWING_PHASE;
        ashwolfSeqWolf.onGround = true;
        ashwolfSeqCarcass = new Carcass(Creature.CreatureType.WOLF,
                x + 0.5f, y + 0.08f, z + 0.5f);
        ashwolfSeqShowcase = true;
        clearHeldForScenicCapture();
        game.simPaused = true;
        game.camera.yaw = 0f;
        game.camera.pitch = 10f;
        updateAshwolfSeq(0);
    }

    private void updateAshwolfSeq(double elapsed) {
        if (ashwolfSeqWolf == null) {
            return;
        }
        int step = Math.min(9, Math.max(0, (int) Math.floor(elapsed)));
        boolean carcassStep = step == 8;
        if (carcassStep) {
            if (!game.entities.carcasses.contains(ashwolfSeqCarcass)) {
                game.entities.carcasses.add(ashwolfSeqCarcass);
            }
            game.entities.creatures.remove(ashwolfSeqWolf);
        } else {
            game.entities.carcasses.remove(ashwolfSeqCarcass);
            if (!game.entities.creatures.contains(ashwolfSeqWolf)) {
                game.entities.creatures.add(ashwolfSeqWolf);
            }
            ashwolfSeqWolf.state = switch (step) {
                case 1, 2 -> Creature.CreatureState.WANDER;
                case 3 -> Creature.CreatureState.STALK;
                case 4 -> Creature.CreatureState.CHARGE;
                case 5 -> Creature.CreatureState.ATTACK;
                case 6 -> Creature.CreatureState.FLEE_HURT;
                case 7 -> Creature.CreatureState.REST;
                default -> Creature.CreatureState.WANDER; // 0 and 9: idle
            };
            ashwolfSeqWolf.vel.set(switch (step) {
                case 1, 3 -> 1.15f;
                case 2, 4, 6 -> 3.4f;
                default -> 0f;
            }, 0f, 0f);
        }
    }

    private void setupSilhouette30mShowcase(Vec3i site) {
        // Afternoon sun from the west side-lights the lineup instead of dusk haze.
        game.time.totalMinutes = (long) (15.5 * 60);
        setWeatherNow(WeatherSystem.Weather.CLEAR);
        clearStagedEntities();

        // Cluster all four subjects near the crosshair so a reviewer can actually
        // compare silhouettes; wolf and trader stand side-on (skulk line and pack
        // hump are profile features), guard and raider face the camera (badge,
        // visor and shoulder pads are frontal features).
        int z = site.z() - 30;
        int wolfX = site.x() - 5;
        Creature wolf = game.entities.spawnCreature(game.world, Creature.CreatureType.WOLF,
                wolfX + 0.5f, game.world.surfaceHeight(wolfX, z) + 1.08f, z + 0.5f);
        wolf.yaw = 90f;
        wolf.state = Creature.CreatureState.STALK;
        wolf.vel.x = 1.1f;
        wolf.bobPhase = POSE_MAX_SWING_PHASE;
        wolf.decideTimer = POSE_FREEZE_SECONDS;

        for (int i = 0; i < 3; i++) {
            int x = site.x() - 1 + i * 3;
            Npc npc = game.entities.spawnNpc(game.world,
                    i == 0 ? "Frontier Guard" : (i == 1 ? "Wandering Trader" : "Ash Raider"),
                    x + 0.5f, game.world.surfaceHeight(x, z) + 1.08f, z + 0.5f);
            npc.yaw = i == 1 ? 90f : 180f;
            npc.campIndex = i;
            npc.faction = i == 0 ? game.faction : null;
            npc.isTrader = i == 1;
            npc.raider = i == 2;
            npc.state = i == 2 ? Npc.NpcState.RAID : Npc.NpcState.IDLE;
            npc.interactFreeze = POSE_FREEZE_SECONDS;
        }
        clearHeldForScenicCapture();
        game.simPaused = true;
        game.camera.yaw = 0f;
        game.camera.pitch = 2f;
    }

    private void setupBloodVfxShowcase(Vec3i site) {
        game.time.totalMinutes = (long) (16.8 * 60);
        setWeatherNow(WeatherSystem.Weather.CLEAR);
        clearStagedEntities();
        int z = site.z() - 9;
        game.entities.carcasses.add(new Carcass(Creature.CreatureType.THORNHORN,
                site.x() + 0.5f, game.world.surfaceHeight(site.x(), z) + 1.08f, z + 0.5f));
        for (int i = 0; i < 9; i++) {
            float x = site.x() - 2.3f + i * 0.55f;
            float zz = site.z() - 5.5f - i * 0.52f;
            float y = game.world.surfaceHeight((int) x, (int) zz) + 1.02f;
            game.entities.tracks.add(new Track(x, y, zz, 15f, null, true));
            game.particles.blood(x, y + 0.22f, zz);
        }
        clearHeldForScenicCapture();
        game.particles.update(0.08f);
        game.simPaused = true;
        game.camera.pitch = 8f;
    }

    private void setupMiningVfxShowcase(Vec3i site) {
        game.time.totalMinutes = (long) (15.8 * 60);
        setWeatherNow(WeatherSystem.Weather.CLEAR);
        int ground = site.y() - 1;
        int z = site.z() - 4;
        for (int y = 1; y <= 3; y++) {
            game.world.setBlock(site.x(), ground + y, z,
                    y == 2 ? BlockType.IRON_ORE : BlockType.STONE, false);
        }
        for (int i = 0; i < 36; i++) {
            game.particles.blockDust(i % 3 == 0 ? BlockType.IRON_ORE : BlockType.STONE,
                    site.x() + 0.5f, ground + 2.5f, z + 0.9f, 1);
        }
        game.player.inventory.set(0, new ItemStack(ItemType.IRON_PICKAXE, 1));
        game.player.hotbarSel = 0;
        miningShowcase = true;
        game.simPaused = true;
        game.camera.yaw = 0f;
        game.camera.pitch = 7f;
    }

    private void setupBeaconVfxShowcase(Vec3i site) {
        game.time.totalMinutes = 22 * 60;
        setWeatherNow(WeatherSystem.Weather.CLEAR);
        Vec3i bp = stageBlock(site, 0, -11, BlockType.BEACON_LIT);
        game.world.beaconPos = bp;
        game.world.beaconStage = 3;
        for (int x = -4; x <= 4; x += 2) {
            stageBlock(site, x, -14, BlockType.RUIN_STONE);
        }
        game.particles.setRandomSeed(BEACON_PARTICLE_SEED);
        for (int i = 0; i < 40; i++) {
            game.particles.beaconMote(bp.x() + 0.5f, bp.y() + 0.25f + (i % 8) * 0.35f,
                    bp.z() + 0.5f);
        }
        game.particles.update(0.10f);
        clearHeldForScenicCapture();
        game.simPaused = true;
        game.camera.pitch = 4f;
    }

    private void updateBenchmarkMovement(double elapsed) {
        float distance = (float) Math.min(320.0, elapsed * 7.0);
        float x = benchmarkMovementStart.x + distance;
        float z = benchmarkMovementStart.z + (float) Math.sin(distance * 0.035f) * 18f;
        int ground = game.world.surfaceHeight((int) Math.floor(x), (int) Math.floor(z));
        game.player.pos.set(x, ground + 1.2f, z);
        game.player.vel.set(7f, 0f, (float) Math.cos(distance * 0.035f) * 4.4f);
        game.camera.yaw = 90f;
        game.camera.pitch = 3f;
    }

    /** Deterministic, opt-in model/VFX lineup for silhouette and held-item capture. */
    private void setupPhase4Showcase(int px, int pz) {
        World world = game.world;
        // Mid-afternoon: crisp warm sun instead of the washed-out dusk band.
        game.time.totalMinutes = (long) (15.5 * 60);
        setWeatherNow(WeatherSystem.Weather.CLEAR);
        clearStagedEntities();

        Creature.CreatureType[] types = Creature.CreatureType.values();
        Creature.CreatureState[] poses = {
                Creature.CreatureState.WANDER, Creature.CreatureState.STALK,
                Creature.CreatureState.WANDER, Creature.CreatureState.GRAZE,
                Creature.CreatureState.CHARGE, Creature.CreatureState.HUNT
        };
        for (int i = 0; i < types.length; i++) {
            int x = px - 7 + i * 3;
            int z = pz - 10;
            int y = world.surfaceHeight(x, z) + 1;
            for (int clearY = y; clearY < Math.min(Chunk.SY, y + 4); clearY++) {
                world.setBlock(x, clearY, z, BlockType.AIR, false);
            }
            Creature c = game.entities.spawnCreature(world, types[i], x + 0.5f,
                    y + (types[i].flying ? 2.5f : 0.08f), z + 0.5f);
            // Alternate 3/4 angles: pure head-on views hid the body silhouettes.
            c.yaw = i % 2 == 0 ? 150f : 210f;
            c.state = poses[i];
            c.hunger = 0;
            c.decideTimer = POSE_FREEZE_SECONDS;
            c.bobPhase = POSE_MAX_SWING_PHASE;
            c.onGround = !types[i].flying;
        }

        for (int i = 0; i < 3; i++) {
            int x = px - 4 + i * 4;
            int z = pz - 6;
            int y = world.surfaceHeight(x, z) + 1;
            for (int clearY = y; clearY < Math.min(Chunk.SY, y + 3); clearY++) {
                world.setBlock(x, clearY, z, BlockType.AIR, false);
            }
            Npc n = game.entities.spawnNpc(world,
                    i == 0 ? "Frontier" : (i == 1 ? "Trader" : "Raider"),
                    x + 0.5f, y + 0.08f, z + 0.5f);
            n.yaw = i == 1 ? 135f : 180f; // trader angled so the pack profile reads
            n.campIndex = i;
            n.faction = i == 0 ? game.faction : null;
            n.isTrader = i == 1;
            n.raider = i == 2;
            n.state = i == 1 ? Npc.NpcState.TRADE : (i == 2 ? Npc.NpcState.RAID : Npc.NpcState.IDLE);
            n.interactFreeze = POSE_FREEZE_SECONDS;
        }

        int fireX = px + 8, fireZ = pz - 12;
        int fireY = world.surfaceHeight(fireX, fireZ) + 1;
        Vec3i firePos = new Vec3i(fireX, fireY, fireZ);
        world.setBlock(fireX, fireY, fireZ, BlockType.CAMPFIRE, false);
        world.campfireFuel.put(firePos, 4000f);

        // Beacon well off to the side: its bloom beam previously backed the
        // glowdeer and washed out that silhouette.
        int beaconX = px - 14, beaconZ = pz - 20;
        int beaconY = world.surfaceHeight(beaconX, beaconZ) + 1;
        world.beaconPos = new Vec3i(beaconX, beaconY, beaconZ);
        world.beaconStage = 3;
        world.setBlock(beaconX, beaconY, beaconZ, BlockType.BEACON_LIT, false);

        game.particles.setRandomSeed(SHOWCASE_PARTICLE_SEED);
        for (int i = 0; i < 7; i++) {
            game.particles.smoke(fireX + 0.5f, fireY + 0.7f, fireZ + 0.5f, 0.8f);
            game.particles.flame(fireX + 0.5f, fireY + 0.15f, fireZ + 0.5f);
            game.particles.beaconMote(beaconX + 0.5f, beaconY + 0.4f, beaconZ + 0.5f);
        }
        game.particles.meteorImpact(px, world.surfaceHeight(px, pz - 20) + 1f, pz - 20);
        for (int i = 0; i < 12; i++) {
            // Toxic motes drift at the far left edge instead of hazing the lineup.
            game.particles.toxicMote(px - 12 + (i % 4) * 0.8f,
                    game.player.pos.y + 0.6f + (i % 3) * 0.35f, pz - 16 - i / 4f);
        }
        game.particles.update(0.22f);

        game.player.inventory.set(0, new ItemStack(ItemType.IRON_PICKAXE, 1));
        game.player.hotbarSel = 0;
        game.camera.yaw = 0f;
        game.camera.pitch = 7f;
        game.simPaused = true;
    }

    /** Empties every live entity collection so a lineup starts from a known state. */
    private void clearStagedEntities() {
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        game.entities.carcasses.clear();
        game.entities.tracks.clear();
    }

    /** Pins weather so a capture is not blended mid-transition. */
    private void setWeatherNow(WeatherSystem.Weather w) {
        game.weather.current = w;
        game.weather.next = w;
        game.weather.blend = 1f;
        game.weather.changeTimer = 4000f;
    }

    /** Moves the first matching equippable stack from the inventory into its slot. */
    void equipFromInventoryFirst(ItemType type) {
        for (int i = 0; i < game.player.inventory.size(); i++) {
            ItemStack s = game.player.inventory.get(i);
            if (s != null && s.type == type && s.type.isEquippable()) {
                game.player.inventory.set(i, null);
                game.player.equipment[s.type.equipSlot.ordinal()] = s;
                return;
            }
        }
    }
}
