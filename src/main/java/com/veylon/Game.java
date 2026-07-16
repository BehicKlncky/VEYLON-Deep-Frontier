package com.veylon;

import com.veylon.ai.FactionSystem;
import com.veylon.engine.AudioManager;
import com.veylon.engine.Camera;
import com.veylon.engine.Input;
import com.veylon.engine.ParticleSystem;
import com.veylon.engine.Renderer;
import com.veylon.engine.UiRenderer;
import com.veylon.engine.Window;
import com.veylon.entity.Affliction;
import com.veylon.entity.Carcass;
import com.veylon.entity.Creature;
import com.veylon.entity.Entity;
import com.veylon.entity.EntityManager;
import com.veylon.entity.Npc;
import com.veylon.entity.Player;
import com.veylon.entity.Track;
import com.veylon.item.EquipSlot;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.item.Station;
import com.veylon.item.ToolKind;
import com.veylon.gfx.FrameProfiler;
import com.veylon.gfx.GraphicsSettings;
import com.veylon.save.SaveSystem;
import com.veylon.simulation.EventSystem;
import com.veylon.simulation.FireSystem;
import com.veylon.simulation.ItemConditionSystem;
import com.veylon.simulation.PlantSystem;
import com.veylon.simulation.SeasonSystem;
import com.veylon.simulation.ShelterSystem;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.simulation.TemperatureSystem;
import com.veylon.simulation.TimeSystem;
import com.veylon.simulation.WaterSystem;
import com.veylon.simulation.WeatherSystem;
import com.veylon.ui.CraftingScreen;
import com.veylon.ui.CrateScreen;
import com.veylon.ui.DebugOverlay;
import com.veylon.ui.EventLog;
import com.veylon.ui.GraphicsOptionsScreen;
import com.veylon.ui.Hud;
import com.veylon.ui.InventoryScreen;
import com.veylon.ui.MapScreen;
import com.veylon.ui.NpcScreen;
import com.veylon.ui.PauseMenu;
import com.veylon.ui.PresentationOverlay;
import com.veylon.ui.SimulationPanel;
import com.veylon.ui.TitleScreen;
import com.veylon.util.AppPaths;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Biome;
import com.veylon.world.Chunk;
import com.veylon.world.Poi;
import com.veylon.world.RackBatch;
import com.veylon.world.Raycaster;
import com.veylon.world.World;
import org.joml.Vector3f;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;

/** Central orchestrator: game loop, input handling, player actions, tick wiring. */
public class Game implements SimulationScheduler.Ticks, World.BlockListener {

    public enum UiMode {
        NONE, INVENTORY, CRAFTING, PAUSE, OPTIONS, MAP, CRATE, NPC
    }

    private enum AppState {
        TITLE, TITLE_OPTIONS, LOADING, PLAYING, DEATH, VICTORY
    }

    private enum LoadRequest {
        NEW_GAME, LOAD_GAME
    }

    // Engine.
    public final Window window = new Window();
    public final Input input = new Input();
    public final Camera camera = new Camera();
    public final Renderer renderer = new Renderer();
    public final UiRenderer ui = new UiRenderer();
    public final AudioManager audio = new AudioManager();
    public final ParticleSystem particles = new ParticleSystem();

    // World & simulation.
    public World world;
    public Player player;
    public final EntityManager entities = new EntityManager();
    public final FactionSystem faction = new FactionSystem();
    public final TimeSystem time = new TimeSystem();
    public final WeatherSystem weather = new WeatherSystem();
    public final TemperatureSystem temperature = new TemperatureSystem();
    public final WaterSystem water = new WaterSystem();
    public final FireSystem fire = new FireSystem();
    public final PlantSystem plants = new PlantSystem();
    public final EventSystem events = new EventSystem();
    public final SeasonSystem seasons = new SeasonSystem();
    public final ItemConditionSystem itemConditions = new ItemConditionSystem();
    public final EventLog eventLog = new EventLog();
    private final SimulationScheduler scheduler = new SimulationScheduler();

    // UI.
    public final Hud hud = new Hud();
    public final InventoryScreen inventoryScreen = new InventoryScreen();
    public final CraftingScreen craftingScreen = new CraftingScreen();
    public final CrateScreen crateScreen = new CrateScreen();
    public final NpcScreen npcScreen = new NpcScreen();
    public final PauseMenu pauseMenu = new PauseMenu();
    public final MapScreen mapScreen = new MapScreen();
    public final DebugOverlay debugOverlay = new DebugOverlay();
    public final SimulationPanel simulationPanel = new SimulationPanel();
    public final TitleScreen titleScreen = new TitleScreen();
    public final GraphicsOptionsScreen graphicsOptionsScreen = new GraphicsOptionsScreen();

    public UiMode uiMode = UiMode.NONE;
    public boolean debugShown;
    public boolean simPanelShown;
    public boolean simPaused;
    /** Set by F2; the run loop captures the back buffer after the frame renders. */
    public boolean pendingScreenshot;

    // Per-frame state.
    public Raycaster.Hit targetHit;
    public float miningProgress;
    private Vec3i miningTarget;
    public String interactPrompt;
    public double totalTime;
    public int fps;
    public double frameMs;
    public final FrameProfiler frameProfiler = new FrameProfiler();
    private int fpsCounter;
    private double fpsTimer;
    private float attackCooldown;

    private AppState appState = AppState.TITLE;
    private LoadRequest loadRequest;
    private boolean loadingPresented;
    private boolean automatedRun;
    private long sessionSeed;
    private float deathTimer;
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
    private long chunkMeshDeletes;
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
    private String qaOptionsSet = System.getenv("VEYLON_QA_SET_OPTIONS");
    private float qaOptionsTimer;
    private final Vector3f benchmarkMovementStart = new Vector3f();

    // Animation / feedback timers.
    public float swingTimer;
    public float walkBob;
    private float footstepTimer;
    private float hitSoundTimer;
    private float emitterTimer;
    private float breathTimer;
    private float coughTimer;

    // Sleep.
    public boolean sleeping;
    public float sleepFade;
    private float sleepQuality;
    private float sleptMinutes;

    public Inventory openCrate;
    public Vec3i openCratePos;
    public Npc activeNpc;

    private final Vector3f spawnPos = new Vector3f();

    public void run() {
        applyResolutionOverride();
        window.setInitialWindowedSize(renderer.settings.windowWidth, renderer.settings.windowHeight);
        window.create("VEYLON: Deep Frontier", input);
        renderer.init();
        window.setVsync(renderer.settings.vsync);
        if (renderer.settings.fullscreen) {
            window.setFullscreen(true);
        }
        ui.init();
        audio.init();
        sessionSeed = configuredSeed();

        boolean smoke = System.getenv("VEYLON_SMOKE") != null;
        String scene = System.getenv("VEYLON_SCENE");
        String shotEnv = System.getenv("VEYLON_SHOT");
        String frontend = System.getenv("VEYLON_FRONTEND");
        boolean frontendQa = frontend != null && !frontend.isBlank();
        automatedRun = smoke || (scene != null && !scene.isBlank())
                || (shotEnv != null && !shotEnv.isBlank()) || frontendQa;
        if (automatedRun && !frontendQa) {
            newWorld(sessionSeed, true);
            applyBenchmarkScene(scene);
            appState = AppState.PLAYING;
            window.captureCursor(true, input);
        } else {
            appState = AppState.TITLE;
            window.captureCursor(false, input);
            if (frontendQa) {
                switch (frontend.trim().toLowerCase(Locale.ROOT)) {
                    case "options" -> {
                        graphicsOptionsScreen.open(renderer.settings,
                                window.windowedWidth(), window.windowedHeight());
                        appState = AppState.TITLE_OPTIONS;
                    }
                    case "loading" -> {
                        appState = AppState.LOADING;
                        staticLoadingQa = true;
                    }
                    case "death", "victory" -> {
                        newWorld(sessionSeed, true);
                        appState = frontend.trim().equalsIgnoreCase("death")
                                ? AppState.DEATH : AppState.VICTORY;
                        deathTimer = 30f;
                    }
                    case "glyphs" -> titleScreen.notice(
                            "Türkçe glif doğrulama: Çığ, İĞÜÖŞ, çğıöşü");
                    default -> {
                    }
                }
            }
        }

        double smokeSeconds = 6;
        if (smoke) {
            try {
                smokeSeconds = Math.max(6, Double.parseDouble(System.getenv("VEYLON_SMOKE")));
            } catch (NumberFormatException ignored) {
            }
        }
        // VEYLON_SHOT="5,10" saves screenshots under the writable app-data directory.
        double[] shotMarks = new double[0];
        if (shotEnv != null && !shotEnv.isBlank()) {
            String[] parts = shotEnv.split(",");
            shotMarks = new double[parts.length];
            for (int i = 0; i < parts.length; i++) {
                try {
                    shotMarks[i] = Double.parseDouble(parts[i].trim());
                } catch (NumberFormatException e) {
                    shotMarks[i] = -1;
                }
            }
        }
        int shotIndex = 0;
        String scenePrefix = scene != null && !scene.isBlank() ? scene
                : (frontendQa ? "frontend_" + frontend.trim().toLowerCase(Locale.ROOT) : "shot");
        String captureTag = System.getenv("VEYLON_CAPTURE_TAG");
        if (captureTag != null && !captureTag.isBlank()) {
            scenePrefix = captureTag.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        }
        int smokePhase = 0;
        boolean smokeSaveOk = false;
        boolean smokeLoadOk = false;
        String smokeGateFailure = null;
        Path smokeSave = AppPaths.dataDirectory().resolve("build/qa/smoke-save.dat");
        double start = glfwGetTime();
        double last = start;
        frameProfiler.reset();
        while (!window.shouldClose()) {
            double now = glfwGetTime();
            double rawDt = now - last;
            double dt = Math.min(0.1, rawDt);
            last = now;
            totalTime = now;
            frameProfiler.record(rawDt);

            window.poll();
            if (heldCycleShowcase && player != null) {
                updateHeldCycle(now - start);
            }
            if (ashwolfSeqShowcase && player != null) {
                updateAshwolfSeq(now - start);
            }
            if (benchmarkMovement && player != null) {
                updateBenchmarkMovement(now - start);
            }
            if (uiCycleShowcase && player != null) {
                updateUiCycle(now - start);
            }
            frame(dt);
            sampleRenderStats();
            if (shotIndex < shotMarks.length && now - start >= shotMarks[shotIndex]) {
                com.veylon.gfx.ScreenshotUtil.capture(window.framebufferWidth(), window.framebufferHeight(),
                        scenePrefix + "_" + (int) shotMarks[shotIndex] + "s");
                System.out.println("[capture] tag=" + scenePrefix
                        + " scene=" + (scene == null ? "frontend" : scene)
                        + " framebuffer=" + window.framebufferWidth() + "x" + window.framebufferHeight()
                        + " particles=" + renderer.particlesDrawn
                        + " particleSubmissions=" + renderer.particleDrawCalls
                        + " drawCalls=" + (renderer.drawCalls + ui.drawCallsLastFrame())
                        + " triangles=" + renderer.trianglesRendered
                        + " glErrors=" + window.glErrorCount()
                        + " khrErrors=" + window.glDebugErrorCount());
                shotIndex++;
                if (shotIndex >= shotMarks.length && !smoke) {
                    window.requestClose();
                }
            }
            if (pendingScreenshot) {
                pendingScreenshot = false;
                com.veylon.gfx.ScreenshotUtil.capture(window.framebufferWidth(), window.framebufferHeight(), null);
            }
            window.swap();
            input.endFrame();
            if (appState == AppState.LOADING && loadingPresented && !staticLoadingQa) {
                completeLoading();
            }

            fpsCounter++;
            fpsTimer += rawDt;
            if (fpsTimer >= 1.0) {
                fps = (int) Math.round(fpsCounter / fpsTimer);
                frameMs = fpsTimer * 1000.0 / Math.max(1, fpsCounter);
                fpsCounter = 0;
                fpsTimer = 0;
            }
            if (smoke && world != null && appState == AppState.PLAYING) {
                if (smokePhase == 0 && now - start > 2.5) {
                    smokePhase = 1;
                    world.setBlock((int) player.pos.x + 2, (int) player.pos.y + 1,
                            (int) player.pos.z + 2, BlockType.TORCH, true);
                    player.inventory.add(ItemType.HIDE_COAT, 1);
                    equipFromInventoryFirst(ItemType.HIDE_COAT);
                    smokeSaveOk = SaveSystem.save(this, smokeSave);
                    System.out.println("[smoke] isolated save=" + smokeSaveOk + " path=" + smokeSave);
                }
                if (smokePhase == 1 && now - start > 4.0) {
                    smokePhase = 2;
                    smokeLoadOk = SaveSystem.load(this, smokeSave);
                    System.out.println("[smoke] isolated load=" + smokeLoadOk + " path=" + smokeSave);
                }
                if (smokePhase == 2 && now - start > 5.0) {
                    smokePhase = 3;
                    // Stress fire + storm systems: build a small wooden stack and torch it.
                    int fx = (int) player.pos.x + 4, fz = (int) player.pos.z + 4;
                    int fy = world.surfaceHeight(fx, fz) + 1;
                    world.setBlock(fx, fy, fz, BlockType.LOG, true);
                    world.setBlock(fx, fy + 1, fz, BlockType.LOG, true);
                    world.setBlock(fx + 1, fy, fz, BlockType.PLANK, true);
                    fire.ignite(this, fx, fy, fz);
                    System.out.println("[smoke] ignited log stack at " + fx + "," + fy + "," + fz);
                    weather.next = WeatherSystem.Weather.STORM;
                    weather.blend = 0.6f;
                }
                if (now - start > smokeSeconds) {
                    window.pollGlErrors("smoke-gate");
                    smokeGateFailure = emitSmokeReport(smokeSeconds, smokeSaveOk, smokeLoadOk);
                    window.requestClose();
                }
            }
        }
        renderer.delete();
        ui.delete();
        audio.shutdown();
        window.pollGlErrors("resource-delete");
        if (smoke && (window.glErrorCount() != 0 || window.glDebugErrorCount() != 0)) {
            smokeGateFailure = smokeGateFailure == null
                    ? "OpenGL errors observed during resource cleanup"
                    : smokeGateFailure + "OpenGL errors observed during resource cleanup; ";
        }
        window.destroy();
        if (smokeGateFailure != null) {
            throw new IllegalStateException("Graphics smoke gate failed: " + smokeGateFailure);
        }
    }

    private long configuredSeed() {
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
    private void applyResolutionOverride() {
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
                renderer.settings.windowWidth = width;
                renderer.settings.windowHeight = height;
                renderer.settings.fullscreen = false;
                System.out.println("[settings] QA resolution override " + width + "x" + height);
            } catch (NumberFormatException e) {
                System.err.println("[settings] ignoring invalid VEYLON_RESOLUTION='" + configured + "'");
            }
        }
        String uiScale = System.getenv("VEYLON_UI_SCALE");
        if (uiScale != null && !uiScale.isBlank()) {
            try {
                renderer.settings.uiScale = Math.max(0.75f,
                        Math.min(1.5f, Float.parseFloat(uiScale.trim())));
                System.out.println("[settings] QA UI scale override " + renderer.settings.uiScale);
            } catch (NumberFormatException e) {
                System.err.println("[settings] ignoring invalid VEYLON_UI_SCALE='" + uiScale + "'");
            }
        }
        String vsync = System.getenv("VEYLON_VSYNC");
        if (vsync != null && !vsync.isBlank()) {
            renderer.settings.vsync = !(vsync.equals("0") || vsync.equalsIgnoreCase("false")
                    || vsync.equalsIgnoreCase("off"));
            System.out.println("[settings] QA VSync override " + renderer.settings.vsync);
        }
        String fullscreen = System.getenv("VEYLON_FULLSCREEN");
        if (fullscreen != null && !fullscreen.isBlank()) {
            renderer.settings.fullscreen = !(fullscreen.equals("0")
                    || fullscreen.equalsIgnoreCase("false")
                    || fullscreen.equalsIgnoreCase("off"));
            System.out.println("[settings] QA fullscreen override " + renderer.settings.fullscreen);
        }
        String shadows = System.getenv("VEYLON_SHADOWS");
        if (shadows != null && !shadows.isBlank()) {
            try {
                renderer.settings.shadowQuality = Math.max(0,
                        Math.min(2, Integer.parseInt(shadows.trim())));
                System.out.println("[settings] QA shadow override " + renderer.settings.shadowQuality);
            } catch (NumberFormatException e) {
                System.err.println("[settings] ignoring invalid VEYLON_SHADOWS='" + shadows + "'");
            }
        }
    }

    private void sampleRenderStats() {
        if (world == null || appState == AppState.TITLE || appState == AppState.TITLE_OPTIONS
                || appState == AppState.LOADING) {
            return;
        }
        profiledRenderFrames++;
        drawCallSum += renderer.drawCalls + ui.drawCallsLastFrame();
        triangleSum += renderer.trianglesRendered;
        peakDrawCalls = Math.max(peakDrawCalls, renderer.drawCalls + ui.drawCallsLastFrame());
        peakTriangles = Math.max(peakTriangles, renderer.trianglesRendered);
        peakParticles = Math.max(peakParticles, renderer.particlesDrawn);
        peakChunks = Math.max(peakChunks, renderer.chunksRendered);
        particleDrawCallSum += renderer.particleDrawCalls;
        peakParticleDrawCalls = Math.max(peakParticleDrawCalls, renderer.particleDrawCalls);
        int resident = 0;
        for (Chunk c : world.loadedChunks()) {
            if (c.meshOpaque != null || c.meshWater != null) resident++;
        }
        peakResidentChunkMeshes = Math.max(peakResidentChunkMeshes, resident);
    }

    private String emitSmokeReport(double duration, boolean saveOk, boolean loadOk) {
        FrameProfiler.Snapshot timing = frameProfiler.snapshot();
        double avgDraws = profiledRenderFrames == 0 ? 0 : drawCallSum / (double) profiledRenderFrames;
        double avgTriangles = profiledRenderFrames == 0 ? 0 : triangleSum / profiledRenderFrames;
        double avgParticleDraws = profiledRenderFrames == 0 ? 0
                : particleDrawCallSum / (double) profiledRenderFrames;
        boolean iconsOk = ui.iconAtlas().itemIconCount() == ItemType.values().length
                && ui.iconAtlas().validationErrors().isEmpty();
        boolean glOk = window.glErrorCount() == 0 && window.glDebugErrorCount() == 0;
        boolean target60 = timing.averageFps() >= 60.0;
        double minimumFps = configuredMinimumSmokeFps();
        boolean performanceOk = minimumFps <= 0 || timing.averageFps() >= minimumFps;
        boolean materialsOk = com.veylon.gfx.MaterialRegistry.errors().isEmpty();

        System.out.printf(Locale.ROOT,
                "[benchmark] resolution=%dx%d duration=%.1fs seed=%d avgFps=%.1f avgMs=%.2f "
                        + "p95Ms=%.2f p99Ms=%.2f maxMs=%.2f target60=%s minFps=%.1f performanceGate=%s%n",
                window.framebufferWidth(), window.framebufferHeight(), duration, sessionSeed,
                timing.averageFps(), timing.averageMs(), timing.p95Ms(), timing.p99Ms(),
                timing.maxMs(), target60, minimumFps, performanceOk);
        System.out.printf(Locale.ROOT,
                "[benchmark] settings={renderDistance=%d shadows=%d bloom=%s fxaa=%s particles=%.2f "
                        + "fov=%.0f uiScale=%.2f vsync=%s fullscreen=%s motion=%.2f}%n",
                renderer.settings.renderDistance, renderer.settings.shadowQuality,
                renderer.settings.bloom, renderer.settings.fxaa, renderer.settings.particleDensity,
                renderer.settings.fov, renderer.settings.uiScale, renderer.settings.vsync,
                renderer.settings.fullscreen, renderer.settings.motion);
        System.out.printf(Locale.ROOT,
                "[benchmark] avgDrawCalls=%.1f peakDrawCalls=%d avgTriangles=%.0f peakTriangles=%d "
                        + "peakChunks=%d peakParticles=%d particleSubmissionsAvg=%.2f "
                        + "particleSubmissionsPeak=%d loadedChunks=%d liveParticles=%d%n",
                avgDraws, peakDrawCalls, avgTriangles, peakTriangles, peakChunks, peakParticles,
                avgParticleDraws, peakParticleDrawCalls, world.loadedCount(), particles.count);
        System.out.printf(Locale.ROOT,
                "[benchmark] chunkMeshes={rebuilt=%d deleted=%d peakResident=%d loadedBlockChunks=%d}%n",
                renderer.chunkMeshRebuildsTotal, chunkMeshDeletes, peakResidentChunkMeshes,
                world.loadedCount());
        System.out.println("[benchmark] context=" + window.glContextSummary());
        System.out.println("[smoke] save=" + saveOk + " load=" + loadOk
                + " assets={materials=" + com.veylon.gfx.MaterialRegistry.materialCount()
                + " fontGlyphs=" + ui.fontRenderer().glyphCount()
                + " itemIcons=" + ui.iconAtlas().itemIconCount() + "/" + ItemType.values().length + "}"
                + " glErrors=" + window.glErrorCount() + " khrErrors=" + window.glDebugErrorCount()
                + " creatures=" + entities.creatureCount() + " npcs=" + entities.npcCount()
                + " tracks=" + entities.tracks.size() + " fires=" + fire.count());

        StringBuilder failure = new StringBuilder();
        if (!saveOk) failure.append("isolated save failed; ");
        if (!loadOk) failure.append("isolated load failed; ");
        if (!materialsOk) failure.append("material validation failed; ");
        if (!iconsOk) failure.append("item icon validation failed; ");
        if (!performanceOk) failure.append(String.format(Locale.ROOT,
                "average FPS %.1f below required %.1f; ", timing.averageFps(), minimumFps));
        if (!glOk) failure.append("OpenGL errors observed; ");
        return failure.isEmpty() ? null : failure.toString();
    }

    /** Optional release gate; unset/zero records timing without enforcing a hardware target. */
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

    /**
     * Dev/QA benchmark scenes (VEYLON_SCENE env): reproducible lighting/weather
     * setups for visual regression screenshots. No effect when unset.
     */
    private void applyBenchmarkScene(String scene) {
        if (scene == null || scene.isBlank()) {
            return;
        }
        String normalized = scene.trim().toLowerCase(Locale.ROOT);
        Vec3i site;
        switch (normalized) {
            case "day", "meadow" -> {
                site = moveBenchmarkToBiome(Biome.MEADOW);
                clearBenchmarkStage(site, 9, 18);
                time.totalMinutes = 12 * 60;
                setWeatherNow(WeatherSystem.Weather.CLEAR);
                stageMeadowWater(site);
                stageMeadowLife(site);
                clearHeldForScenicCapture();
            }
            case "dawnfog", "pinefog" -> {
                site = moveBenchmarkToBiome(Biome.PINE_FOREST);
                clearBenchmarkStage(site, 9, 20);
                time.totalMinutes = (long) (6.15 * 60);
                setWeatherNow(WeatherSystem.Weather.FOG);
                stagePineFog(site);
                clearHeldForScenicCapture();
            }
            case "nightfire", "campfire_rain" -> {
                site = moveBenchmarkToBiome(Biome.MEADOW);
                clearBenchmarkStage(site, 8, 16);
                time.totalMinutes = 23 * 60;
                setWeatherNow(WeatherSystem.Weather.RAIN);
                Vec3i fp = stageBlock(site, 0, -7, BlockType.CAMPFIRE);
                world.campfireFuel.put(fp, 4000f);
                stageBlock(site, -4, -8, BlockType.WORKBENCH);
                stageBlock(site, 4, -8, BlockType.CRATE);
                stageBlock(site, -3, -12, BlockType.FURNACE);
                stageBlock(site, 3, -12, BlockType.TORCH);
                for (int i = 0; i < 12; i++) {
                    particles.smoke(fp.x() + 0.5f, fp.y() + 0.7f, fp.z() + 0.5f, 0.7f);
                    particles.flame(fp.x() + 0.5f, fp.y() + 0.15f, fp.z() + 0.5f);
                }
                clearHeldForScenicCapture();
            }
            case "ruin", "cave_ruin" -> {
                site = moveBenchmarkToBiome(Biome.ROCKY_HIGHLANDS);
                clearBenchmarkStage(site, 9, 18);
                time.totalMinutes = 22 * 60;
                setWeatherNow(WeatherSystem.Weather.CLEAR);
                stageRuin(site);
                clearHeldForScenicCapture();
            }
            case "toxic", "ashfall" -> {
                site = moveBenchmarkToBiome(Biome.SCRUBLAND);
                clearBenchmarkStage(site, 9, 20);
                time.totalMinutes = 14 * 60;
                setWeatherNow(WeatherSystem.Weather.CLEAR);
                events.active.add(new EventSystem.ActiveEvent(
                        EventSystem.EventType.TOXIC_FOG, 3000f, 1f));
                for (int i = 0; i < 24; i++) {
                    particles.toxicMote(site.x() - 5 + (i % 8) * 1.4f,
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
                time.totalMinutes = (long) (17.7 * 60);
                setWeatherNow(WeatherSystem.Weather.CLEAR);
                stageAoShadow(site);
                clearHeldForScenicCapture();
                simPaused = true;
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
                time.totalMinutes = 11 * 60;
                setWeatherNow(WeatherSystem.Weather.CLEAR);
                benchmarkMovementStart.set(player.pos);
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
        int ox = (int) Math.floor(spawnPos.x);
        int oz = (int) Math.floor(spawnPos.z);
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
        player.pos.set(sx + 0.5f, ground + 1.2f, sz + 0.5f);
        player.vel.zero();
        player.biome = world.biomeAt(sx, sz);
        camera.yaw = 0f;
        camera.pitch = 4f;
        System.out.println("[scene] camera site=" + sx + "," + (ground + 1) + "," + sz
                + " biome=" + player.biome.displayName
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
        player.pos.set(site.x() + 0.5f, targetGround + 1.2f, site.z() + 0.5f);
        player.vel.zero();
        player.inWater = false;
        camera.position.set(player.pos.x, player.pos.y + player.eyeHeight(), player.pos.z);
    }

    private Vec3i stageBlock(Vec3i site, int dx, int dz, BlockType type) {
        int x = site.x() + dx, z = site.z() + dz;
        int y = world.surfaceHeight(x, z) + 1;
        world.setBlock(x, y, z, type, false);
        return new Vec3i(x, y, z);
    }

    private void stageMeadowWater(Vec3i site) {
        for (int dx = 4; dx <= 8; dx++) {
            for (int dz = -12; dz <= -8; dz++) {
                int x = site.x() + dx, z = site.z() + dz;
                int ground = world.surfaceHeight(x, z);
                world.setBlock(x, ground, z, BlockType.WATER, false);
            }
        }
        stageBlock(site, -5, -10, BlockType.WORKBENCH);
        stageBlock(site, -2, -12, BlockType.CRATE);
    }

    private void clearHeldForScenicCapture() {
        player.inventory.set(0, null);
        player.hotbarSel = 0;
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
            int y = world.surfaceHeight(x, z) + 1;
            world.setBlock(x, y, z, BlockType.LOG, false);
            world.setBlock(x, y + 1, z, BlockType.ASH, false);
            particles.smoke(x + 0.5f, y + 1.2f, z + 0.5f, 0.45f);
        }
    }

    /** Repeated recesses expose vertex AO; staggered pillars cast long sun shadows. */
    private void stageAoShadow(Vec3i site) {
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
        player.pos.set(site.x() + 0.5f, site.y() + 0.2f, site.z() - 24 + 0.5f);
        player.vel.zero();
        camera.yaw = 180f;
        camera.pitch = 8f;
    }

    private void stageRuin(Vec3i site) {
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
        player.inventory.set(0, new ItemStack(item, 1));
        player.hotbarSel = 0;
    }

    private void updateHeldCycle(double elapsed) {
        ItemType[] items = {
                ItemType.IRON_PICKAXE, ItemType.IRON_AXE, ItemType.IRON_SPEAR,
                ItemType.IRON_KNIFE, ItemType.TORCH, ItemType.COOKED_MEAT,
                ItemType.MEDICINE, ItemType.WORKBENCH
        };
        int index = Math.min(items.length - 1, Math.max(0, (int) Math.floor(elapsed)));
        ItemStack selected = player.inventory.get(0);
        if (selected == null || selected.type != items[index]) {
            player.inventory.set(0, new ItemStack(items[index], 1));
            player.hotbarSel = 0;
        }
    }

    /**
     * Opt-in visual regression path for every gameplay overlay. It uses the same
     * screen instances and update methods as player input, but supplies stable
     * crate/NPC data so a single unattended run can catch layout regressions.
     */
    private void updateUiCycle(double elapsed) {
        int screen = Math.min(6, Math.max(0, (int) Math.floor(elapsed)));
        simPaused = true;
        simPanelShown = false;
        switch (screen) {
            case 0 -> uiMode = UiMode.INVENTORY;
            case 1 -> uiMode = UiMode.CRAFTING;
            case 2 -> uiMode = UiMode.MAP;
            case 3 -> uiMode = UiMode.PAUSE;
            case 4 -> {
                uiMode = UiMode.NONE;
                simPanelShown = true;
            }
            case 5 -> {
                if (openCrate == null) {
                    openCrate = new Inventory(12);
                    ItemType[] items = ItemType.values();
                    for (int i = 0; i < openCrate.size(); i++) {
                        openCrate.set(i, new ItemStack(items[(i * 5 + 3) % items.length], i % 4 + 1));
                    }
                    openCratePos = new Vec3i((int) player.pos.x, (int) player.pos.y,
                            (int) player.pos.z - 5);
                }
                uiMode = UiMode.CRATE;
            }
            default -> {
                if (activeNpc == null || activeNpc.dead) {
                    activeNpc = entities.npcs.stream().filter(n -> n.isTrader).findFirst()
                            .orElseGet(() -> entities.npcs.isEmpty() ? null : entities.npcs.getFirst());
                    npcScreen.open();
                }
                uiMode = activeNpc != null ? UiMode.NPC : UiMode.NONE;
            }
        }
    }

    private void setupInventoryShowcase() {
        ItemType[] items = ItemType.values();
        for (int i = 0; i < player.inventory.size(); i++) {
            player.inventory.set(i, new ItemStack(items[i % items.length], i % 4 + 1));
        }
        inventoryScreen.reset();
        uiMode = UiMode.INVENTORY;
        simPaused = true;
        time.totalMinutes = 12 * 60;
        setWeatherNow(WeatherSystem.Weather.CLEAR);
    }

    /**
     * One-frame Ashwolf core-set lineup. All live wolves share the cached model,
     * so eight visibly different poses plus a carcass also exercise resetPose()
     * between consecutive submissions and make pose leakage apparent.
     */
    private void setupAshwolfPoseShowcase(Vec3i site) {
        // Mid-afternoon: high warm sun, no dusk haze, so silhouettes stay crisp.
        time.totalMinutes = (long) (15.5 * 60);
        setWeatherNow(WeatherSystem.Weather.CLEAR);
        entities.creatures.clear();
        entities.npcs.clear();
        entities.carcasses.clear();
        entities.tracks.clear();

        Creature.CreatureState[] states = {
                Creature.CreatureState.WANDER, Creature.CreatureState.WANDER,
                Creature.CreatureState.HUNT, Creature.CreatureState.STALK,
                Creature.CreatureState.CHARGE, Creature.CreatureState.ATTACK,
                Creature.CreatureState.FLEE_HURT, Creature.CreatureState.REST
        };
        for (int i = 0; i < states.length; i++) {
            int x = site.x() - 7 + i * 2;
            int z = site.z() - 9 - (i % 2);
            int y = world.surfaceHeight(x, z) + 1;
            Creature wolf = entities.spawnCreature(world, Creature.CreatureType.WOLF,
                    x + 0.5f, y + 0.08f, z + 0.5f);
            // Side-on to the camera: skulk height, tail and leg splay differences
            // are silhouette features, and they vanish in a head-on view.
            wolf.yaw = 90f;
            wolf.state = states[i];
            wolf.decideTimer = 9999f;
            // sin(0.49 * 3.2) ~= 1: freeze moving gaits at their maximum leg swing.
            wolf.bobPhase = 0.49f;
            wolf.onGround = true;
            wolf.vel.x = switch (i) {
                case 1, 3 -> 1.15f; // walk / stalk
                case 2, 4, 6 -> 3.4f; // run / charge / hit recoil
                default -> 0f;
            };
        }

        int deadX = site.x() + 9, deadZ = site.z() - 8;
        entities.carcasses.add(new Carcass(Creature.CreatureType.WOLF,
                deadX + 0.5f, world.surfaceHeight(deadX, deadZ) + 1.08f, deadZ + 0.5f));

        for (int i = 0; i < 3; i++) {
            int x = site.x() - 6 + i * 6;
            int z = site.z() - 14;
            int y = world.surfaceHeight(x, z) + 1;
            Npc npc = entities.spawnNpc(world,
                    i == 0 ? "Maro - Guard" : (i == 1 ? "Ressk - Trader" : "Vex - Raider"),
                    x + 0.5f, y + 0.08f, z + 0.5f);
            npc.yaw = 180f;
            npc.campIndex = i;
            npc.faction = i == 0 ? faction : null;
            npc.isTrader = i == 1;
            npc.raider = i == 2;
            npc.state = i == 2 ? Npc.NpcState.RAID : Npc.NpcState.IDLE;
            npc.interactFreeze = 9999f;
        }
        Vec3i firePos = stageBlock(site, 8, -6, BlockType.CAMPFIRE);
        world.campfireFuel.put(firePos, 4000f);
        for (int i = 0; i < 8; i++) {
            particles.flame(firePos.x() + 0.5f, firePos.y() + 0.15f, firePos.z() + 0.5f);
            particles.smoke(firePos.x() + 0.5f, firePos.y() + 0.7f, firePos.z() + 0.5f, 0.6f);
        }
        // No held item: the earlier capture had the pickaxe viewmodel occluding
        // the center of the pose lineup.
        clearHeldForScenicCapture();
        simPaused = true;
        camera.yaw = 0f;
        camera.pitch = 6f;
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
        time.totalMinutes = (long) (15.5 * 60);
        setWeatherNow(WeatherSystem.Weather.CLEAR);
        entities.creatures.clear();
        entities.npcs.clear();
        entities.carcasses.clear();
        entities.tracks.clear();

        int x = site.x(), z = site.z() - 6;
        int y = world.surfaceHeight(x, z) + 1;
        ashwolfSeqWolf = entities.spawnCreature(world, Creature.CreatureType.WOLF,
                x + 0.5f, y + 0.08f, z + 0.5f);
        ashwolfSeqWolf.yaw = 90f;
        ashwolfSeqWolf.decideTimer = 9999f;
        ashwolfSeqWolf.bobPhase = 0.49f;
        ashwolfSeqWolf.onGround = true;
        ashwolfSeqCarcass = new Carcass(Creature.CreatureType.WOLF,
                x + 0.5f, y + 0.08f, z + 0.5f);
        ashwolfSeqShowcase = true;
        clearHeldForScenicCapture();
        simPaused = true;
        camera.yaw = 0f;
        camera.pitch = 10f;
        updateAshwolfSeq(0);
    }

    private void updateAshwolfSeq(double elapsed) {
        if (ashwolfSeqWolf == null) {
            return;
        }
        int step = Math.min(9, Math.max(0, (int) Math.floor(elapsed)));
        boolean carcassStep = step == 8;
        if (carcassStep) {
            if (!entities.carcasses.contains(ashwolfSeqCarcass)) {
                entities.carcasses.add(ashwolfSeqCarcass);
            }
            entities.creatures.remove(ashwolfSeqWolf);
        } else {
            entities.carcasses.remove(ashwolfSeqCarcass);
            if (!entities.creatures.contains(ashwolfSeqWolf)) {
                entities.creatures.add(ashwolfSeqWolf);
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
        time.totalMinutes = (long) (15.5 * 60);
        setWeatherNow(WeatherSystem.Weather.CLEAR);
        entities.creatures.clear();
        entities.npcs.clear();
        entities.carcasses.clear();
        entities.tracks.clear();

        // Cluster all four subjects near the crosshair so a reviewer can actually
        // compare silhouettes; wolf and trader stand side-on (skulk line and pack
        // hump are profile features), guard and raider face the camera (badge,
        // visor and shoulder pads are frontal features).
        int z = site.z() - 30;
        int wolfX = site.x() - 5;
        Creature wolf = entities.spawnCreature(world, Creature.CreatureType.WOLF,
                wolfX + 0.5f, world.surfaceHeight(wolfX, z) + 1.08f, z + 0.5f);
        wolf.yaw = 90f;
        wolf.state = Creature.CreatureState.STALK;
        wolf.vel.x = 1.1f;
        wolf.bobPhase = 0.49f;
        wolf.decideTimer = 9999f;

        for (int i = 0; i < 3; i++) {
            int x = site.x() - 1 + i * 3;
            Npc npc = entities.spawnNpc(world,
                    i == 0 ? "Frontier Guard" : (i == 1 ? "Wandering Trader" : "Ash Raider"),
                    x + 0.5f, world.surfaceHeight(x, z) + 1.08f, z + 0.5f);
            npc.yaw = i == 1 ? 90f : 180f;
            npc.campIndex = i;
            npc.faction = i == 0 ? faction : null;
            npc.isTrader = i == 1;
            npc.raider = i == 2;
            npc.state = i == 2 ? Npc.NpcState.RAID : Npc.NpcState.IDLE;
            npc.interactFreeze = 9999f;
        }
        clearHeldForScenicCapture();
        simPaused = true;
        camera.yaw = 0f;
        camera.pitch = 2f;
    }

    private void setupBloodVfxShowcase(Vec3i site) {
        time.totalMinutes = (long) (16.8 * 60);
        setWeatherNow(WeatherSystem.Weather.CLEAR);
        entities.creatures.clear();
        entities.npcs.clear();
        entities.carcasses.clear();
        entities.tracks.clear();
        int z = site.z() - 9;
        entities.carcasses.add(new Carcass(Creature.CreatureType.THORNHORN,
                site.x() + 0.5f, world.surfaceHeight(site.x(), z) + 1.08f, z + 0.5f));
        for (int i = 0; i < 9; i++) {
            float x = site.x() - 2.3f + i * 0.55f;
            float zz = site.z() - 5.5f - i * 0.52f;
            float y = world.surfaceHeight((int) x, (int) zz) + 1.02f;
            entities.tracks.add(new Track(x, y, zz, 15f, null, true));
            particles.blood(x, y + 0.22f, zz);
        }
        clearHeldForScenicCapture();
        particles.update(0.08f);
        simPaused = true;
        camera.pitch = 8f;
    }

    private void setupMiningVfxShowcase(Vec3i site) {
        time.totalMinutes = (long) (15.8 * 60);
        setWeatherNow(WeatherSystem.Weather.CLEAR);
        int ground = site.y() - 1;
        int z = site.z() - 4;
        for (int y = 1; y <= 3; y++) {
            world.setBlock(site.x(), ground + y, z, y == 2 ? BlockType.IRON_ORE : BlockType.STONE, false);
        }
        for (int i = 0; i < 36; i++) {
            particles.blockDust(i % 3 == 0 ? BlockType.IRON_ORE : BlockType.STONE,
                    site.x() + 0.5f, ground + 2.5f, z + 0.9f, 1);
        }
        player.inventory.set(0, new ItemStack(ItemType.IRON_PICKAXE, 1));
        player.hotbarSel = 0;
        miningShowcase = true;
        simPaused = true;
        camera.yaw = 0f;
        camera.pitch = 7f;
    }

    private void setupBeaconVfxShowcase(Vec3i site) {
        time.totalMinutes = 22 * 60;
        setWeatherNow(WeatherSystem.Weather.CLEAR);
        Vec3i bp = stageBlock(site, 0, -11, BlockType.BEACON_LIT);
        world.beaconPos = bp;
        world.beaconStage = 3;
        for (int x = -4; x <= 4; x += 2) {
            stageBlock(site, x, -14, BlockType.RUIN_STONE);
        }
        particles.setRandomSeed(0x424541434f4eL);
        for (int i = 0; i < 40; i++) {
            particles.beaconMote(bp.x() + 0.5f, bp.y() + 0.25f + (i % 8) * 0.35f,
                    bp.z() + 0.5f);
        }
        particles.update(0.10f);
        clearHeldForScenicCapture();
        simPaused = true;
        camera.pitch = 4f;
    }

    private void updateBenchmarkMovement(double elapsed) {
        float distance = (float) Math.min(320.0, elapsed * 7.0);
        float x = benchmarkMovementStart.x + distance;
        float z = benchmarkMovementStart.z + (float) Math.sin(distance * 0.035f) * 18f;
        int ground = world.surfaceHeight((int) Math.floor(x), (int) Math.floor(z));
        player.pos.set(x, ground + 1.2f, z);
        player.vel.set(7f, 0f, (float) Math.cos(distance * 0.035f) * 4.4f);
        camera.yaw = 90f;
        camera.pitch = 3f;
    }

    /** Deterministic, opt-in model/VFX lineup for silhouette and held-item capture. */
    private void setupPhase4Showcase(int px, int pz) {
        // Mid-afternoon: crisp warm sun instead of the washed-out dusk band.
        time.totalMinutes = (long) (15.5 * 60);
        setWeatherNow(WeatherSystem.Weather.CLEAR);
        entities.creatures.clear();
        entities.npcs.clear();
        entities.carcasses.clear();
        entities.tracks.clear();

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
            Creature c = entities.spawnCreature(world, types[i], x + 0.5f,
                    y + (types[i].flying ? 2.5f : 0.08f), z + 0.5f);
            // Alternate 3/4 angles: pure head-on views hid the body silhouettes.
            c.yaw = i % 2 == 0 ? 150f : 210f;
            c.state = poses[i];
            c.hunger = 0;
            c.decideTimer = 9999f;
            c.bobPhase = 0.49f;
            c.onGround = !types[i].flying;
        }

        for (int i = 0; i < 3; i++) {
            int x = px - 4 + i * 4;
            int z = pz - 6;
            int y = world.surfaceHeight(x, z) + 1;
            for (int clearY = y; clearY < Math.min(Chunk.SY, y + 3); clearY++) {
                world.setBlock(x, clearY, z, BlockType.AIR, false);
            }
            Npc n = entities.spawnNpc(world, i == 0 ? "Frontier" : (i == 1 ? "Trader" : "Raider"),
                    x + 0.5f, y + 0.08f, z + 0.5f);
            n.yaw = i == 1 ? 135f : 180f; // trader angled so the pack profile reads
            n.campIndex = i;
            n.faction = i == 0 ? faction : null;
            n.isTrader = i == 1;
            n.raider = i == 2;
            n.state = i == 1 ? Npc.NpcState.TRADE : (i == 2 ? Npc.NpcState.RAID : Npc.NpcState.IDLE);
            n.interactFreeze = 9999f;
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

        particles.setRandomSeed(0x5645594c4f4eL);
        for (int i = 0; i < 7; i++) {
            particles.smoke(fireX + 0.5f, fireY + 0.7f, fireZ + 0.5f, 0.8f);
            particles.flame(fireX + 0.5f, fireY + 0.15f, fireZ + 0.5f);
            particles.beaconMote(beaconX + 0.5f, beaconY + 0.4f, beaconZ + 0.5f);
        }
        particles.meteorImpact(px, world.surfaceHeight(px, pz - 20) + 1f, pz - 20);
        for (int i = 0; i < 12; i++) {
            // Toxic motes drift at the far left edge instead of hazing the lineup.
            particles.toxicMote(px - 12 + (i % 4) * 0.8f,
                    player.pos.y + 0.6f + (i % 3) * 0.35f, pz - 16 - i / 4f);
        }
        particles.update(0.22f);

        player.inventory.set(0, new ItemStack(ItemType.IRON_PICKAXE, 1));
        player.hotbarSel = 0;
        camera.yaw = 0f;
        camera.pitch = 7f;
        simPaused = true;
    }

    private void setWeatherNow(WeatherSystem.Weather w) {
        weather.current = w;
        weather.next = w;
        weather.blend = 1f;
        weather.changeTimer = 4000f;
    }

    private void equipFromInventoryFirst(ItemType type) {
        for (int i = 0; i < player.inventory.size(); i++) {
            ItemStack s = player.inventory.get(i);
            if (s != null && s.type == type && s.type.isEquippable()) {
                player.inventory.set(i, null);
                player.equipment[s.type.equipSlot.ordinal()] = s;
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // World setup
    // ------------------------------------------------------------------

    public void newWorld(long seed, boolean fresh) {
        // Save-load and front-end transitions can replace a live world. Release
        // its bounded GPU meshes and reset cross-world simulation queues first.
        releaseWorldMeshes();
        scheduler.reset();
        fire.reset();
        water.reset();
        events.reset();
        plants.reset();
        itemConditions.reset();
        particles.count = 0;
        particles.setRandomSeed(seed ^ 0x5645594c4f4eL);
        emitterRng.setSeed(seed ^ 0x46584c4f4eL);
        world = new World(seed);
        world.listener = this;
        player = new Player(world);
        entities.creatures.clear();
        entities.npcs.clear();
        entities.carcasses.clear();
        entities.tracks.clear();
        eventLog.clear();
        faction.trust = 35;
        faction.alert = 0;
        faction.foodStock = 10;
        faction.woodStock = 8;
        faction.hostile = false;
        faction.upgradeStage = 0;
        faction.quest = null;
        faction.alliedGiftGiven = false;
        time.totalMinutes = 8 * 60;
        weather.current = WeatherSystem.Weather.CLEAR;
        weather.next = WeatherSystem.Weather.CLEAR;
        weather.blend = 1f;
        weather.changeTimer = 100;
        sleeping = false;
        sleepFade = 0;
        simPaused = false;
        simPanelShown = false;
        uiMode = UiMode.NONE;
        targetHit = null;
        miningTarget = null;
        miningProgress = 0;
        swingTimer = 0;
        walkBob = 0;

        // Synchronous initial generation around spawn.
        world.ensureChunks(8, 8, 5, 10_000);
        // Find dry land for the crash site (don't spawn in a lake).
        int sx = 8, sz = 8;
        outer:
        for (int r = 0; r <= 48; r += 4) {
            for (int dx = -r; dx <= r; dx += 4) {
                for (int dz = -r; dz <= r; dz += 4) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    world.ensureChunks(8 + dx, 8 + dz, 1, 10_000);
                    if (world.surfaceHeight(8 + dx, 8 + dz) > World.SEA_LEVEL + 1) {
                        sx = 8 + dx;
                        sz = 8 + dz;
                        break outer;
                    }
                }
            }
        }
        world.ensureChunks(sx, sz, 5, 10_000);
        int sy = world.surfaceHeight(sx, sz) + 1;
        spawnPos.set(sx + 0.5f, sy + 0.2f, sz + 0.5f);
        player.pos.set(spawnPos);
        camera.yaw = 35;
        camera.pitch = 8;

        setupCamp(fresh);

        if (fresh) {
            player.inventory.add(ItemType.BERRY, 4);
            player.inventory.add(ItemType.LOG, 2);
            player.inventory.add(ItemType.STICK, 2);
            player.inventory.add(ItemType.FIBER, 4);
            player.inventory.add(ItemType.TORCH, 2);
            player.inventory.add(ItemType.BANDAGE, 1);
            player.inventory.add(ItemType.WATERSKIN_EMPTY, 1);
            log("You crash-landed on Veylon. Survive.");
            log("Gather wood and berries; craft tools with [C]. Watch your wounds.");
            log("An NPC camp lies somewhere nearby - and stranger things besides...");
            for (int i = 0; i < 8; i++) {
                entities.slowTick(this);
            }
        }
    }

    /**
     * Deterministically builds the NPC camp near spawn. Runs for both new games
     * and loads (loads then overwrite crate contents/fuel from the save).
     */
    private void setupCamp(boolean fresh) {
        Random rng = new Random(world.seed * 31 + 7);
        int baseX = (int) spawnPos.x, baseZ = (int) spawnPos.z;
        int cx = baseX, cz = baseZ, h = 0;
        for (int attempt = 0; attempt < 10; attempt++) {
            cx = baseX + 28 + rng.nextInt(14) + attempt * 8;
            cz = baseZ + 22 + rng.nextInt(14);
            world.ensureChunks(cx, cz, 3, 10_000);
            h = world.surfaceHeight(cx, cz);
            if (h > World.SEA_LEVEL + 1 && h < Chunk.SY - 14) {
                break;
            }
        }
        // Flatten a 9x9 pad.
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int x = cx + dx, z = cz + dz;
                for (int y = h + 1; y <= h + 7; y++) {
                    if (world.getBlock(x, y, z) != BlockType.AIR) {
                        world.setBlock(x, y, z, BlockType.AIR, false);
                    }
                }
                for (int y = Math.max(2, h - 3); y < h; y++) {
                    if (!world.getBlock(x, y, z).solid) {
                        world.setBlock(x, y, z, BlockType.DIRT, false);
                    }
                }
                world.setBlock(x, h, z, BlockType.GRASS, false);
            }
        }
        Vec3i campPos = new Vec3i(cx, h + 1, cz);
        world.campPos = campPos;
        faction.campPos = campPos;

        world.setBlock(cx, h + 1, cz, BlockType.CAMPFIRE, false);
        world.campfireFuel.putIfAbsent(campPos, 600f);
        world.setBlock(cx - 2, h + 1, cz - 2, BlockType.CRATE, false);
        world.setBlock(cx + 2, h + 1, cz + 2, BlockType.CRATE, false);
        world.setBlock(cx + 2, h + 1, cz - 2, BlockType.WORKBENCH, false);
        world.setBlock(cx - 3, h + 1, cz + 3, BlockType.TORCH, false);
        world.setBlock(cx + 3, h + 1, cz - 3, BlockType.TORCH, false);
        for (int dx = -3; dx <= -1; dx++) {
            world.setBlock(cx + dx, h + 1, cz - 4, BlockType.WALL, false);
        }

        Inventory crate1 = new Inventory(12);
        crate1.add(ItemType.BERRY, 6);
        crate1.add(ItemType.PLANK, 4);
        crate1.add(ItemType.STICK, 4);
        crate1.add(ItemType.COAL, 2);
        world.crateContents.putIfAbsent(new Vec3i(cx - 2, h + 1, cz - 2), crate1);
        Inventory crate2 = new Inventory(12);
        crate2.add(ItemType.LOG, 6);
        crate2.add(ItemType.FIBER, 4);
        crate2.add(ItemType.STONE, 3);
        world.crateContents.putIfAbsent(new Vec3i(cx + 2, h + 1, cz + 2), crate2);

        if (fresh) {
            String[] names = {"Maro", "Senna", "Korrin", "Della"};
            for (int i = 0; i < names.length; i++) {
                Npc n = entities.spawnNpc(world, names[i],
                        cx + 1.5f + (i % 2) * 2 - 2, h + 1.2f, cz + 1.5f + (i / 2) * 2 - 2);
                n.faction = faction;
                n.campIndex = i;
            }
        }
    }

    // ------------------------------------------------------------------
    // Frame
    // ------------------------------------------------------------------

    private void frame(double dtD) {
        if (appState == AppState.TITLE || appState == AppState.TITLE_OPTIONS
                || appState == AppState.LOADING) {
            frameFrontend((float) dtD);
            return;
        }

        float dt = (float) dtD;
        if (appState == AppState.DEATH) {
            deathTimer -= dt;
            if (input.wasKeyPressed(GLFW_KEY_ESCAPE)) {
                returnToTitle();
                frameFrontend(dt);
                return;
            }
            if (deathTimer <= 0 || input.wasKeyPressed(GLFW_KEY_ENTER)) {
                respawn();
                appState = AppState.PLAYING;
            }
        } else if (appState == AppState.VICTORY) {
            if (input.wasKeyPressed(GLFW_KEY_ESCAPE)) {
                returnToTitle();
                frameFrontend(dt);
                return;
            }
            if (input.wasKeyPressed(GLFW_KEY_ENTER)) {
                appState = AppState.PLAYING;
            }
        } else {
            handleGlobalKeys();
        }
        window.captureCursor(appState == AppState.PLAYING && uiMode == UiMode.NONE, input);

        boolean simulate = appState == AppState.PLAYING
                && uiMode != UiMode.PAUSE && uiMode != UiMode.OPTIONS && !simPaused;

        swingTimer = Math.max(0, swingTimer - dt);
        hitSoundTimer = Math.max(0, hitSoundTimer - dt);

        if (sleeping && simulate) {
            tickSleep(dt);
        } else if (!sleeping) {
            sleepFade = Math.max(0, sleepFade - dt * 1.2f);
        }

        if (uiMode == UiMode.NONE && simulate && !sleeping) {
            if (!automatedRun) {
                updateMouseLook();
            }
            updateMovement(dt);
            updateActions(dt);
        } else {
            miningProgress = 0;
            interactPrompt = null;
            if (uiMode == UiMode.NONE && !sleeping && !automatedRun) {
                updateMouseLook();
            }
        }

        if (simulate) {
            time.advance(dtD);
            scheduler.update(dtD, this);
            particles.update(dt);
            updateEmitters(dt);
        }
        audio.update(dt);

        // Camera follows the player eye.
        camera.position.set(player.pos.x, player.pos.y + player.eyeHeight(), player.pos.z);
        player.yaw = camera.yaw;
        if (miningShowcase) {
            // Progressive mining pantomime: crack decal grows over ~5 s while the
            // pickaxe swings and dust bursts off the struck face, so timed shots
            // prove dust + impact feedback + crack progression, not a static prop.
            miningShowcaseElapsed += dt;
            miningShowcaseDustTimer -= dt;
            targetHit = Raycaster.cast(world, camera.position, camera.front(), 7.0, false);
            miningProgress = targetHit == null ? 0f
                    : Math.min(0.95f, miningShowcaseElapsed * 0.19f);
            if (targetHit != null && miningShowcaseDustTimer <= 0f) {
                miningShowcaseDustTimer = 0.55f;
                swingTimer = Math.max(swingTimer, 0.35f);
                particles.blockDust(world.getBlock(targetHit.x(), targetHit.y(), targetHit.z()),
                        targetHit.x() + 0.5f + targetHit.nx() * 0.55f,
                        targetHit.y() + 0.5f + targetHit.ny() * 0.55f,
                        targetHit.z() + 0.5f + targetHit.nz() * 0.55f, 6);
            }
        }
        audio.setListener(camera.position.x, camera.position.y, camera.position.z, camera.yaw);

        if (player.dead && appState == AppState.PLAYING) {
            appState = AppState.DEATH;
            deathTimer = 3f;
            closeScreens();
        }

        // Stream chunks and rebuild meshes near the player.
        world.ensureChunks((int) player.pos.x, (int) player.pos.z, renderer.renderRadius(), 2);
        int pcx = Math.floorDiv((int) player.pos.x, 16);
        int pcz = Math.floorDiv((int) player.pos.z, 16);
        renderer.buildDirtyMeshes(world, pcx, pcz, 3);

        // Render world + UI.
        renderer.render(this, dt);
        beginUiFrame();
        hud.render(this);
        switch (uiMode) {
            case INVENTORY -> inventoryScreen.update(this);
            case CRAFTING -> craftingScreen.update(this);
            case CRATE -> crateScreen.update(this);
            case NPC -> npcScreen.update(this);
            case MAP -> mapScreen.update(this);
            case PAUSE -> pauseMenu.update(this);
            case OPTIONS -> handlePauseOptions(graphicsOptionsScreen.update(this));
            case NONE -> {
            }
        }
        if (debugShown) {
            debugOverlay.render(this);
        }
        if (simPanelShown) {
            simulationPanel.render(this);
        }
        if (appState == AppState.DEATH) {
            PresentationOverlay.death(ui, deathTimer);
        } else if (appState == AppState.VICTORY) {
            PresentationOverlay.victory(ui, totalTime);
        }
        ui.end();
    }

    private void frameFrontend(float dt) {
        window.captureCursor(false, input);
        audio.update(dt);
        beginUiFrame();
        if (appState == AppState.TITLE) {
            TitleScreen.Action action = titleScreen.update(this);
            switch (action) {
                case NEW_GAME -> beginLoading(LoadRequest.NEW_GAME);
                case LOAD_GAME -> beginLoading(LoadRequest.LOAD_GAME);
                case OPTIONS -> {
                    graphicsOptionsScreen.open(renderer.settings,
                            window.windowedWidth(), window.windowedHeight());
                    appState = AppState.TITLE_OPTIONS;
                }
                case QUIT -> window.requestClose();
                case NONE -> {
                }
            }
        } else if (appState == AppState.TITLE_OPTIONS) {
            if (qaOptionsSet != null && !qaOptionsSet.isBlank()) {
                qaOptionsTimer += dt;
                if (qaOptionsTimer > 0.8f) {
                    applyQaOptions(qaOptionsSet);
                    qaOptionsSet = null;
                }
            }
            GraphicsOptionsScreen.Result result = graphicsOptionsScreen.update(this);
            if (result.action() == GraphicsOptionsScreen.Action.APPLY) {
                applyGraphicsOptions(result);
                titleScreen.notice("Graphics settings applied.");
                appState = AppState.TITLE;
            } else if (result.action() == GraphicsOptionsScreen.Action.CANCEL) {
                appState = AppState.TITLE;
            }
        } else {
            String detail = loadRequest == LoadRequest.LOAD_GAME
                    ? "Restoring your frontier..." : "Mapping atmosphere and terrain...";
            PresentationOverlay.loading(ui, detail, totalTime);
            loadingPresented = true;
        }
        ui.end();
    }

    private void beginUiFrame() {
        ui.begin(window.framebufferWidth(), window.framebufferHeight(), renderer.settings.uiScale);
        input.setCursorScale(window.cursorToFramebufferScaleX() / ui.uiScale(),
                window.cursorToFramebufferScaleY() / ui.uiScale());
    }

    private void beginLoading(LoadRequest request) {
        loadRequest = request;
        loadingPresented = false;
        appState = AppState.LOADING;
    }

    /** Runs after a loading frame has been swapped, keeping the synchronous work honest. */
    private void completeLoading() {
        loadingPresented = false;
        boolean loaded;
        if (loadRequest == LoadRequest.LOAD_GAME) {
            loaded = SaveSystem.load(this);
        } else {
            newWorld(sessionSeed, true);
            loaded = true;
        }
        loadRequest = null;
        if (loaded) {
            appState = AppState.PLAYING;
            closeScreens();
            window.captureCursor(true, input);
        } else {
            releaseWorldMeshes();
            world = null;
            player = null;
            titleScreen.notice("No compatible save was found.");
            appState = AppState.TITLE;
        }
    }

    /**
     * QA relaunch-persistence hook (VEYLON_QA_SET_OPTIONS="fov=85,bloom=false"):
     * mutates live settings on the open options screen, then routes through the
     * exact same applyGraphicsOptions(...) -> settings.save() path the APPLY
     * button uses, so a following unmodified launch proves persistence.
     */
    private void applyQaOptions(String script) {
        GraphicsSettings s = renderer.settings;
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
        applyGraphicsOptions(new GraphicsOptionsScreen.Result(
                GraphicsOptionsScreen.Action.APPLY, s.windowWidth, s.windowHeight));
        System.out.println("[qa] options applied+saved via APPLY path: " + script);
    }

    private void handlePauseOptions(GraphicsOptionsScreen.Result result) {
        if (result.action() == GraphicsOptionsScreen.Action.NONE) {
            return;
        }
        if (result.action() == GraphicsOptionsScreen.Action.APPLY) {
            applyGraphicsOptions(result);
        }
        uiMode = UiMode.PAUSE;
    }

    private void applyGraphicsOptions(GraphicsOptionsScreen.Result result) {
        renderer.settings.windowWidth = result.width();
        renderer.settings.windowHeight = result.height();
        window.setWindowedResolution(result.width(), result.height());
        window.setFullscreen(renderer.settings.fullscreen);
        window.setVsync(renderer.settings.vsync);
        renderer.settings.save();
    }

    private void returnToTitle() {
        releaseWorldMeshes();
        world = null;
        player = null;
        particles.count = 0;
        entities.creatures.clear();
        entities.npcs.clear();
        entities.carcasses.clear();
        entities.tracks.clear();
        closeScreens();
        simPaused = false;
        appState = AppState.TITLE;
        titleScreen.notice("");
    }

    private void releaseWorldMeshes() {
        if (world == null) {
            return;
        }
        for (Chunk c : world.loadedChunks()) {
            c.deleteMeshes();
        }
    }

    private final Random emitterRng = new Random();

    /** Ambient particle and sound emitters driven by world state. */
    private void updateEmitters(float dt) {
        emitterTimer -= dt;
        if (emitterTimer > 0) {
            return;
        }
        emitterTimer = 0.12f;
        Random rng = emitterRng;
        float px = player.pos.x, py = player.pos.y, pz = player.pos.z;

        // Precipitation around the player.
        if (weather.isPrecip() && player.exposedToSky) {
            boolean snow = weather.effective() == WeatherSystem.Weather.SNOW;
            int n = (int) (5 * weather.intensity());
            for (int i = 0; i < n; i++) {
                float x = px + (rng.nextFloat() * 2 - 1) * 11;
                float z = pz + (rng.nextFloat() * 2 - 1) * 11;
                if (snow) {
                    particles.snowflake(x, py + 6 + rng.nextFloat() * 5, z);
                } else {
                    particles.rainDrop(x, py + 6 + rng.nextFloat() * 5, z);
                    if (rng.nextFloat() < 0.5f) {
                        int gx = (int) x, gz = (int) z;
                        if (world.getChunk(Math.floorDiv(gx, 16), Math.floorDiv(gz, 16)) != null) {
                            particles.rainSplash(x, world.surfaceHeight(gx, gz) + 1.05f, z);
                        }
                    }
                }
            }
        }
        // Ashfall drifts grey flakes everywhere.
        if (events.ashfall()) {
            for (int i = 0; i < 3; i++) {
                particles.ashFlake(px + (rng.nextFloat() * 2 - 1) * 10,
                        py + 5 + rng.nextFloat() * 5, pz + (rng.nextFloat() * 2 - 1) * 10);
            }
        }
        // Toxic fog carries slow, sickly motes at eye and ground level.
        if (events.toxicFog()) {
            for (int i = 0; i < 2; i++) {
                particles.toxicMote(px + (rng.nextFloat() * 2 - 1) * 9,
                        py + 0.3f + rng.nextFloat() * 2.2f,
                        pz + (rng.nextFloat() * 2 - 1) * 9);
            }
        }
        // Fire smoke and embers (burning blocks and fueled campfires).
        for (Vec3i p : fire.burningCells()) {
            if (p.distSq(px, py, pz) < 40 * 40) {
                particles.smoke(p.x() + 0.5f, p.y() + 1f, p.z() + 0.5f, 1f);
                particles.flame(p.x() + 0.5f, p.y() + 0.2f, p.z() + 0.5f);
                if (rng.nextFloat() < 0.5f) {
                    particles.ember(p.x() + 0.5f, p.y() + 0.6f, p.z() + 0.5f);
                }
            }
        }
        for (Vec3i p : world.campfireFuel.keySet()) {
            if (p.distSq(px, py, pz) < 35 * 35
                    && world.getBlock(p.x(), p.y(), p.z()) == BlockType.CAMPFIRE) {
                particles.smoke(p.x() + 0.5f, p.y() + 0.7f, p.z() + 0.5f, 0.6f);
                particles.flame(p.x() + 0.5f, p.y() + 0.15f, p.z() + 0.5f);
                if (rng.nextFloat() < 0.35f) {
                    particles.ember(p.x() + 0.5f, p.y() + 0.4f, p.z() + 0.5f);
                }
            }
        }
        // The active distress beacon sheds a bounded stream of cyan energy motes.
        if (world.beaconStage >= 3 && world.beaconPos != null
                && world.beaconPos.distSq(px, py, pz) < 55 * 55) {
            Vec3i bp = world.beaconPos;
            particles.beaconMote(bp.x() + 0.5f, bp.y() + 0.3f, bp.z() + 0.5f);
            if (rng.nextFloat() < 0.45f) {
                particles.beaconMote(bp.x() + 0.5f, bp.y() + 1.2f, bp.z() + 0.5f);
            }
        }
        // Cold breath.
        breathTimer -= 0.12f;
        if (breathTimer <= 0 && player.envTemp < 2 && !player.inWater) {
            breathTimer = 2.6f;
            Vector3f f = camera.front();
            particles.breath(camera.position.x, camera.position.y - 0.15f, camera.position.z, f.x, f.z);
        }
        // Coughing while smoke-poisoned.
        coughTimer -= 0.12f;
        if (coughTimer <= 0 && player.has(Affliction.SMOKE)) {
            coughTimer = 3.5f;
            audio.playCough();
        }
    }

    // ------------------------------------------------------------------
    // Sleep
    // ------------------------------------------------------------------

    private void startSleep(boolean campBed) {
        Creature threat = entities.nearestCreature(player.pos.x, player.pos.y, player.pos.z, 12,
                c -> c.type.predator);
        if (threat != null) {
            log("Too dangerous to sleep - a predator prowls nearby!");
            return;
        }
        boolean night = time.hourF() >= 19 || time.hourF() < 5;
        if (!night && player.fatigue < 55) {
            log("You aren't tired enough to sleep (wait for night or fatigue 55+).");
            return;
        }
        ShelterSystem.Shelter sh = ShelterSystem.evaluate(world, player.pos.x, player.pos.y, player.pos.z);
        float quality = 0.35f;
        quality += sh.coverage() * 0.3f;
        if (campBed) {
            quality += 0.2f;
        }
        if (player.nearFireHeat(this) > 3) {
            quality += 0.15f;
        }
        if (player.wetness > 0.5f) {
            quality -= 0.25f;
        }
        if (player.envTemp < 0) {
            quality -= 0.2f;
        }
        sleepQuality = Math.max(0.1f, Math.min(1f, quality));
        sleeping = true;
        sleptMinutes = 0;
        audio.playSleep();
        log("You settle down to sleep" + (sleepQuality > 0.7f ? " comfortably."
                : (sleepQuality < 0.4f ? " - cold, wet and uneasy." : ".")));
    }

    private void tickSleep(float dt) {
        sleepFade = Math.min(1f, sleepFade + dt * 1.5f);
        float minutes = dt * 170f;
        time.totalMinutes += minutes;
        sleptMinutes += minutes;

        // Reduced needs while asleep, faster fatigue recovery with quality.
        player.fatigue = Math.max(0, player.fatigue - dt * 9f * sleepQuality);
        player.hunger = Math.max(0, player.hunger - dt * 0.10f);
        player.thirst = Math.max(0, player.thirst - dt * 0.14f);
        if (sleepQuality < 0.4f) {
            player.bodyTemp -= dt * 0.25f;
        }

        boolean morning = time.hourF() >= 5.5f && time.hourF() < 9 && sleptMinutes > 90;
        boolean rested = player.fatigue <= 1 && sleptMinutes > 120;
        boolean attacked = player.damageFlash > 0.5f;
        if (morning || rested || attacked) {
            sleeping = false;
            if (attacked) {
                log("You are attacked in your sleep!");
            } else {
                log("You wake after " + (int) (sleptMinutes / 60f * 10) / 10f + " hours. Fatigue "
                        + (int) player.fatigue + ".");
                if (sleepQuality < 0.4f && Math.random() < 0.45) {
                    player.addAffliction(Affliction.SICKNESS, 150);
                    log("That miserable night left you SICK. Sleep warm, dry and sheltered.");
                }
            }
        }
    }

    private void handleGlobalKeys() {
        if (uiMode == UiMode.OPTIONS) {
            return; // GraphicsOptionsScreen owns Escape/F5/navigation while open.
        }
        if (uiMode == UiMode.PAUSE && input.wasKeyPressed(GLFW_KEY_O)) {
            graphicsOptionsScreen.open(renderer.settings,
                    window.windowedWidth(), window.windowedHeight());
            uiMode = UiMode.OPTIONS;
            return;
        }
        if (input.wasKeyPressed(GLFW_KEY_ESCAPE)) {
            if (sleeping) {
                sleeping = false;
                log("You wake up early.");
            } else if (uiMode == UiMode.NONE) {
                uiMode = UiMode.PAUSE;
            } else {
                closeScreens();
            }
        }
        if (sleeping) {
            return;
        }
        if (input.wasKeyPressed(GLFW_KEY_E)) {
            toggle(UiMode.INVENTORY);
            inventoryScreen.reset();
        }
        if (input.wasKeyPressed(GLFW_KEY_C)) {
            toggle(UiMode.CRAFTING);
        }
        if (input.wasKeyPressed(GLFW_KEY_M)) {
            toggle(UiMode.MAP);
        }
        if (input.wasKeyPressed(GLFW_KEY_TAB)) {
            simPanelShown = !simPanelShown;
        }
        if (input.wasKeyPressed(GLFW_KEY_F3)) {
            debugShown = !debugShown;
        }
        if (input.wasKeyPressed(GLFW_KEY_F2)) {
            pendingScreenshot = true;
        }
        if (input.wasKeyPressed(GLFW_KEY_P)) {
            simPaused = !simPaused;
        }
        if (input.wasKeyPressed(GLFW_KEY_F5)) {
            if (SaveSystem.save(this)) {
                log("Game saved.");
            } else {
                log("Save FAILED (see console).");
            }
        }
        if (input.wasKeyPressed(GLFW_KEY_F9)) {
            if (SaveSystem.load(this)) {
                closeScreens();
                log("Game loaded.");
            } else {
                log("No save found (or load failed).");
            }
        }
        if (uiMode == UiMode.PAUSE && input.wasKeyPressed(GLFW_KEY_Q)) {
            window.requestClose();
        }
        if ((uiMode == UiMode.CRATE || uiMode == UiMode.NPC) && input.wasKeyPressed(GLFW_KEY_F)) {
            closeScreens();
        }
        if (uiMode == UiMode.NONE) {
            for (int i = 0; i < 9; i++) {
                if (input.wasKeyPressed(GLFW_KEY_1 + i)) {
                    player.hotbarSel = i;
                }
            }
            int scroll = (int) input.scrollDelta();
            if (scroll != 0) {
                player.hotbarSel = Math.floorMod(player.hotbarSel - scroll, 9);
            }
        }
    }

    private void toggle(UiMode mode) {
        uiMode = uiMode == mode ? UiMode.NONE : mode;
        if (uiMode == UiMode.NONE) {
            closeScreens();
        }
    }

    public void closeScreens() {
        uiMode = UiMode.NONE;
        openCrate = null;
        openCratePos = null;
        activeNpc = null;
        inventoryScreen.reset();
    }

    // ------------------------------------------------------------------
    // Player control
    // ------------------------------------------------------------------

    private void updateMouseLook() {
        float sens = 0.115f;
        camera.yaw += (float) (input.mouseDX() * sens);
        camera.pitch += (float) (input.mouseDY() * sens);
        if (camera.pitch > 89.5f) camera.pitch = 89.5f;
        if (camera.pitch < -89.5f) camera.pitch = -89.5f;
        if (camera.yaw > 360) camera.yaw -= 360;
        if (camera.yaw < -360) camera.yaw += 360;
    }

    private void updateMovement(float dt) {
        Player p = player;
        p.crouching = input.isKeyDown(GLFW_KEY_LEFT_CONTROL);
        boolean fwd = input.isKeyDown(GLFW_KEY_W);
        boolean back = input.isKeyDown(GLFW_KEY_S);
        boolean left = input.isKeyDown(GLFW_KEY_A);
        boolean right = input.isKeyDown(GLFW_KEY_D);
        boolean wantSprint = input.isKeyDown(GLFW_KEY_LEFT_SHIFT) && fwd && !p.crouching;
        p.sprinting = wantSprint && p.canSprint();

        float speed = (p.crouching ? 2.1f : (p.sprinting ? 6.7f : 4.3f)) * p.moveSpeedMul();
        float yawRad = (float) Math.toRadians(camera.yaw);
        float fx = (float) Math.sin(yawRad), fz = -(float) Math.cos(yawRad);
        float rx = (float) Math.cos(yawRad), rz = (float) Math.sin(yawRad);

        float mx = 0, mz = 0;
        if (fwd) {
            mx += fx;
            mz += fz;
        }
        if (back) {
            mx -= fx;
            mz -= fz;
        }
        if (right) {
            mx += rx;
            mz += rz;
        }
        if (left) {
            mx -= rx;
            mz -= rz;
        }
        float len = (float) Math.sqrt(mx * mx + mz * mz);
        if (len > 0.01f) {
            p.vel.x = mx / len * speed;
            p.vel.z = mz / len * speed;
        } else {
            p.vel.x = 0;
            p.vel.z = 0;
        }

        if (p.sprinting) {
            p.stamina = Math.max(0, p.stamina - 9f * dt);
            p.noise = Math.min(1f, p.noise + 0.8f * dt);
        }

        if (input.isKeyDown(GLFW_KEY_SPACE)) {
            if (p.inWater) {
                p.vel.y = Math.max(p.vel.y, 3.0f);
            } else if (p.onGround && p.stamina >= 3 && input.wasKeyPressed(GLFW_KEY_SPACE)) {
                p.vel.y = p.has(Affliction.SPRAIN) ? 6.2f : 8.2f;
                p.stamina -= 3;
                p.onGround = false;
            }
        }

        boolean wasInWater = p.inWater;
        p.applyPhysics(dt, true);
        if (!wasInWater && p.inWater) {
            particles.splash(p.pos.x, p.pos.y + 0.4f, p.pos.z);
            audio.playFootstep(BlockType.WATER, true);
        }

        // Footsteps with material sounds and view bob.
        boolean moving = len > 0.01f && p.onGround;
        if (moving) {
            walkBob += dt * (p.sprinting ? 1.6f : 1f);
            footstepTimer -= dt;
            if (footstepTimer <= 0) {
                footstepTimer = p.crouching ? 0.62f : (p.sprinting ? 0.31f : 0.45f);
                BlockType under = world.getBlock((int) Math.floor(p.pos.x),
                        (int) Math.floor(p.pos.y - 0.1f), (int) Math.floor(p.pos.z));
                audio.playFootstep(under, p.inWater);
                if (!p.crouching) {
                    p.noise = Math.min(1f, p.noise + (p.sprinting ? 0.12f : 0.05f));
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Actions: mining, placing, attacking, interacting
    // ------------------------------------------------------------------

    private void updateActions(float dt) {
        attackCooldown -= dt;
        Vector3f origin = camera.position;
        Vector3f dir = camera.front();
        targetHit = Raycaster.cast(world, origin, dir, 5.2, false);

        updatePrompt();

        // Left mouse: attack entity in reach, otherwise mine.
        if (input.isMouseDown(GLFW_MOUSE_BUTTON_LEFT)) {
            Entity victim = findAttackTarget(dir);
            if (victim != null) {
                miningProgress = 0;
                miningTarget = null;
                if (attackCooldown <= 0) {
                    attackCooldown = 0.45f;
                    swingTimer = 0.35f;
                    attack(victim);
                }
            } else if (targetHit != null) {
                mine(dt);
            } else {
                if (input.wasMousePressed(GLFW_MOUSE_BUTTON_LEFT)) {
                    swingTimer = 0.35f;
                    audio.playSwing();
                }
                miningProgress = 0;
                miningTarget = null;
            }
        } else {
            miningProgress = 0;
            miningTarget = null;
        }

        // Right mouse: use block / eat / place / equip / treat.
        if (input.wasMousePressed(GLFW_MOUSE_BUTTON_RIGHT)) {
            rightClick();
        }

        // F: interact.
        if (input.wasKeyPressed(GLFW_KEY_F)) {
            interact();
        }
    }

    private void attack(Entity victim) {
        ItemStack held = player.selected();
        float dmg = held != null ? held.type.damage : 1.5f;
        if (player.crouching) {
            dmg *= 1.4f; // ambush bonus
        }
        victim.hurt(dmg, true);
        victim.knockback(player.pos.x, player.pos.z, 3.2f);
        audio.playHit();
        particles.blood(victim.pos.x, victim.pos.y + victim.height * 0.6f, victim.pos.z);
        consumeDurability(held, 1f);
        player.noise = Math.min(1f, player.noise + 0.25f);
        if (victim instanceof Creature c) {
            c.fear = 1f;
            c.bleedTimer = Math.max(c.bleedTimer, 18f);
        }
        if (victim instanceof Npc n && !n.isTrader && !n.raider) {
            faction.addTrust(this, -30, "You attacked " + n.name + "!");
        }
    }

    /** Wears the held item; breaks it when durability runs out. */
    private void consumeDurability(ItemStack held, float amount) {
        if (held == null || !held.type.hasDurability()) {
            return;
        }
        held.durability -= amount;
        if (held.durability <= 0) {
            log("Your " + held.type.displayName + " broke!");
            audio.playToolBreak();
            player.inventory.set(player.hotbarSel, null);
        }
    }

    public boolean playerHasKnife() {
        return player.inventory.count(ItemType.BONE_KNIFE) > 0
                || player.inventory.count(ItemType.IRON_KNIFE) > 0;
    }

    /** Wears down the first knife in the inventory; used when skinning. */
    private void useKnife() {
        for (int i = 0; i < player.inventory.size(); i++) {
            ItemStack s = player.inventory.get(i);
            if (s != null && (s.type == ItemType.BONE_KNIFE || s.type == ItemType.IRON_KNIFE)) {
                s.durability -= 1.5f;
                if (s.durability <= 0) {
                    log("Your " + s.type.displayName + " broke!");
                    audio.playToolBreak();
                    player.inventory.set(i, null);
                }
                return;
            }
        }
    }

    /** Called by EntityManager when the player's hit killed a creature. */
    public void onCreatureKilled(Creature c) {
        if (c.type.predator && world.campPos != null
                && c.distSqTo(world.campPos.x(), world.campPos.y(), world.campPos.z()) < 22 * 22) {
            faction.addTrust(this, 10, "The camp saw you slay a predator");
            faction.onPredatorKilledNearCamp(this);
        }
    }

    public void onRaiderKilled() {
        faction.addTrust(this, 8, "You drove off a scavenger");
    }

    private void updatePrompt() {
        interactPrompt = null;
        Npc npc = entities.nearestNpc(player.pos.x, player.pos.y, player.pos.z, 3.2f);
        if (npc != null && !npc.raider) {
            interactPrompt = "[F] Talk to " + npc.name;
            return;
        }
        Carcass carcass = entities.nearestCarcass(player.pos.x, player.pos.y, player.pos.z, 2.6f);
        if (carcass != null) {
            interactPrompt = "[F] Harvest " + carcass.type.displayName + " carcass"
                    + (carcass.rotten() ? " (rotting!)" : "")
                    + (playerHasKnife() ? "" : " (no knife: scraps only)");
            return;
        }
        if (targetHit != null) {
            Vec3i pos = new Vec3i(targetHit.x(), targetHit.y(), targetHit.z());
            switch (targetHit.type()) {
                case BERRY_BUSH -> interactPrompt = "[F] Harvest berries";
                case HERB_PLANT -> interactPrompt = "[F] Gather herbs";
                case CAMPFIRE -> interactPrompt = "[F] Cook meat / boil water / add fuel ("
                        + (int) (float) world.campfireFuel.getOrDefault(pos, 0f) + "s fuel)";
                case CRATE -> interactPrompt = "[F] Open crate";
                case WORKBENCH -> interactPrompt = "[F] Use workbench";
                case FURNACE -> interactPrompt = "[F] Use furnace (crafting)";
                case ANVIL -> interactPrompt = "[F] Use anvil (crafting)";
                case TANNERY -> interactPrompt = "[F] Use tannery (crafting)";
                case HERB_STATION -> interactPrompt = "[F] Use herbalist bench (crafting)";
                case MAP_TABLE -> interactPrompt = "[F] Use map table (decode blueprints)";
                case DRYING_RACK -> interactPrompt = rackPrompt(pos);
                case RAIN_COLLECTOR -> {
                    float liters = world.collectorWater.getOrDefault(pos, 0f);
                    interactPrompt = "[F] Rain collector: " + String.format("%.1f", liters)
                            + "/3.0 L" + (liters >= 1f ? " (fill waterskin)" : "");
                }
                case BEDROLL -> interactPrompt = "[F] Sleep (bedroll)";
                case CAMP_BED -> interactPrompt = "[F] Sleep (camp bed"
                        + (faction.campPrivileges() ? ")" : " - needs Friendly trust)");
                case BEACON -> interactPrompt = beaconPrompt();
                case BEACON_LIT -> interactPrompt = "[F] Beacon transmitting... rescue inbound";
                default -> {
                }
            }
            if (interactPrompt != null) {
                return;
            }
            if (targetHit.type().requiresTool && !holdingTool(targetHit.type().preferredTool)) {
                interactPrompt = "Requires " + targetHit.type().preferredTool.name().toLowerCase();
                return;
            }
        }
        // Crouching close to the ground reveals animal tracks.
        if (player.crouching) {
            Track track = entities.nearestTrack(player.pos.x, player.pos.y, player.pos.z, 3f);
            if (track != null) {
                interactPrompt = track.describe();
                return;
            }
        }
        Raycaster.Hit fluid = Raycaster.cast(world, camera.position, camera.front(), 4.0, true);
        if (fluid != null && fluid.type() == BlockType.WATER) {
            interactPrompt = player.inventory.count(ItemType.WATERSKIN_EMPTY) > 0
                    ? "[F] Fill waterskin (untreated water)"
                    : "[F] Drink (untreated water - risky)";
        }
    }

    private String rackPrompt(Vec3i pos) {
        RackBatch batch = world.rackBatches.get(pos);
        if (batch == null) {
            return "[F] Load drying rack (raw meat or berries)";
        }
        if (batch.done()) {
            return "[F] Collect " + batch.count + "x " + batch.output().displayName;
        }
        int pct = (int) (batch.progress / batch.required() * 100);
        return "Drying " + batch.count + "x " + batch.input.displayName + " (" + pct + "%)";
    }

    private String beaconPrompt() {
        return switch (world.beaconStage) {
            case 0 -> "[F] Install Signal Crystal (need 1, from ancient ruins)";
            case 1 -> "[F] Wire the array (need 3 copper ingots)";
            case 2 -> faction.trust >= 75
                    ? "[F] Calibrate with the camp's codes (Allied)"
                    : "Calibration needs the camp's codes - become Allied (trust 75+)";
            default -> "[F] Distress beacon";
        };
    }

    private boolean holdingTool(ToolKind kind) {
        ItemStack held = player.selected();
        return held != null && held.type.tool == kind;
    }

    private void mine(float dt) {
        BlockType t = targetHit.type();
        if (t.hardness < 0) {
            return;
        }
        Vec3i pos = new Vec3i(targetHit.x(), targetHit.y(), targetHit.z());
        if (!pos.equals(miningTarget)) {
            miningTarget = pos;
            miningProgress = 0;
        }
        ItemStack held = player.selected();
        float mult = 1f;
        if (held != null && held.type.tool != ToolKind.NONE && held.type.tool == t.preferredTool) {
            mult = held.type.toolPower;
        }
        if (t.requiresTool && (held == null || held.type.tool != t.preferredTool)) {
            miningProgress = 0;
            return;
        }
        swingTimer = Math.max(swingTimer, 0.18f);
        player.noise = Math.min(1f, player.noise + 0.35f * dt);
        if (hitSoundTimer <= 0) {
            hitSoundTimer = 0.32f;
            audio.playBlockHit(t, pos.x() + 0.5f, pos.y() + 0.5f, pos.z() + 0.5f);
            particles.blockDust(t, pos.x() + 0.5f, pos.y() + 0.5f, pos.z() + 0.5f, 3);
        }
        miningProgress += dt * mult / Math.max(0.05f, t.hardness);
        if (miningProgress >= 1f) {
            breakBlock(pos, t, held);
            miningProgress = 0;
            miningTarget = null;
        }
    }

    private void breakBlock(Vec3i pos, BlockType t, ItemStack held) {
        // Crates dump their contents to the player.
        if (t == BlockType.CRATE) {
            Inventory crate = world.crateContents.remove(pos);
            if (crate != null) {
                for (int i = 0; i < crate.size(); i++) {
                    ItemStack s = crate.get(i);
                    if (s != null) {
                        player.inventory.addStack(s);
                    }
                }
            }
        }
        if (t == BlockType.CAMPFIRE) {
            world.campfireFuel.remove(pos);
        }
        if (t == BlockType.DRYING_RACK) {
            RackBatch batch = world.rackBatches.remove(pos);
            if (batch != null) {
                player.inventory.add(batch.done() ? batch.output() : batch.input, batch.count);
            }
        }
        if (t == BlockType.RAIN_COLLECTOR) {
            world.collectorWater.remove(pos);
        }
        if (t == BlockType.BEACON && pos.equals(world.beaconPos)) {
            world.beaconPos = null;
            world.beaconStage = -1;
            log("You dismantled the distress beacon.");
        }

        // Drops.
        if (t.drop != null) {
            boolean dropOk = !t.requiresTool || (held != null && held.type.tool == t.preferredTool);
            if (t == BlockType.LEAVES) {
                dropOk = Math.random() < 0.35;
            }
            if (dropOk) {
                player.inventory.add(t.drop, t.dropCount);
            }
        }
        if (t == BlockType.BERRY_BUSH) {
            player.inventory.add(ItemType.FIBER, 1);
        }

        world.setBlock(pos.x(), pos.y(), pos.z(), BlockType.AIR, true);
        audio.playBlockBreak(pos.x() + 0.5f, pos.y() + 0.5f, pos.z() + 0.5f);
        particles.blockDust(t, pos.x() + 0.5f, pos.y() + 0.5f, pos.z() + 0.5f, 12);
        consumeDurability(held, 1f);
        player.noise = Math.min(1f, player.noise + 0.3f);

        // Damaging camp structures angers the camp.
        if (world.campPos != null && !faction.hostile
                && (t == BlockType.CRATE || t == BlockType.CAMPFIRE || t == BlockType.WALL
                || t == BlockType.WORKBENCH || t == BlockType.TORCH || t == BlockType.CAMP_BED
                || t == BlockType.HERB_STATION || t == BlockType.DRYING_RACK)
                && pos.distSq(world.campPos.x(), world.campPos.y(), world.campPos.z()) < 14 * 14) {
            faction.addTrust(this, -15, "The camp saw you wreck their property!");
        }
    }

    private void rightClick() {
        // Interactable blocks first.
        if (targetHit != null) {
            switch (targetHit.type()) {
                case WORKBENCH, FURNACE, ANVIL, TANNERY, HERB_STATION, MAP_TABLE -> {
                    uiMode = UiMode.CRAFTING;
                    return;
                }
                case CRATE -> {
                    openCrateAt(new Vec3i(targetHit.x(), targetHit.y(), targetHit.z()));
                    return;
                }
                case CAMPFIRE -> {
                    campfireInteract(new Vec3i(targetHit.x(), targetHit.y(), targetHit.z()));
                    return;
                }
                default -> {
                }
            }
        }

        ItemStack held = player.selected();
        if (held == null) {
            return;
        }
        // A short restrained dip gives drinking, medicine, food and placement tactile feedback.
        swingTimer = Math.max(swingTimer, 0.35f);
        // Drink from waterskins.
        if (held.type == ItemType.WATERSKIN_CLEAN || held.type == ItemType.WATERSKIN_DIRTY) {
            drink(held);
            return;
        }
        // Treat wounds.
        if (held.type.isMedical()) {
            applyMedical(held);
            return;
        }
        // Wear gear.
        if (held.type.isEquippable()) {
            equipHeld(held);
            return;
        }
        // Eat.
        if (held.type.isEdible()) {
            eat(held);
            return;
        }
        // Place.
        BlockType place = held.type.places();
        if (place != null && targetHit != null) {
            int px = targetHit.x() + targetHit.nx();
            int py = targetHit.y() + targetHit.ny();
            int pz = targetHit.z() + targetHit.nz();
            if (!world.getBlock(px, py, pz).isReplaceable()) {
                return;
            }
            // Don't place inside the player or an entity.
            if (place.solid && wouldCollide(px, py, pz)) {
                return;
            }
            world.setBlock(px, py, pz, place, true);
            Vec3i pos = new Vec3i(px, py, pz);
            switch (place) {
                case CAMPFIRE -> world.campfireFuel.put(pos, 300f);
                case CRATE -> world.crateContents.put(pos, new Inventory(12));
                case RAIN_COLLECTOR -> world.collectorWater.put(pos, 0f);
                case BEACON -> {
                    world.beaconPos = pos;
                    world.beaconStage = 0;
                    log("Beacon frame placed. It needs a signal crystal, copper wiring and calibration.");
                }
                default -> {
                }
            }
            player.inventory.shrink(player.hotbarSel, 1);
            audio.playBlockPlace(px + 0.5f, py + 0.5f, pz + 0.5f);
            particles.blockDust(place, px + 0.5f, py + 0.8f, pz + 0.5f, 5);
        }
    }

    private boolean wouldCollide(int bx, int by, int bz) {
        if (aabbIntersectsBlock(player, bx, by, bz)) {
            return true;
        }
        for (Creature c : entities.creatures) {
            if (aabbIntersectsBlock(c, bx, by, bz)) {
                return true;
            }
        }
        for (Npc n : entities.npcs) {
            if (aabbIntersectsBlock(n, bx, by, bz)) {
                return true;
            }
        }
        return false;
    }

    private boolean aabbIntersectsBlock(Entity e, int bx, int by, int bz) {
        float hw = e.width / 2f;
        return e.pos.x + hw > bx && e.pos.x - hw < bx + 1
                && e.pos.y + e.height > by && e.pos.y < by + 1
                && e.pos.z + hw > bz && e.pos.z - hw < bz + 1;
    }

    // ------------------------------------------------------------------
    // Consuming, treating, equipping
    // ------------------------------------------------------------------

    private void eat(ItemStack held) {
        Player p = player;
        if (p.hunger > 98) {
            return;
        }
        ItemType t = held.type;
        float freshness = held.freshnessFrac();
        p.hunger = Math.min(100, p.hunger + t.food * (0.5f + 0.5f * freshness));
        p.thirst = Math.min(100, p.thirst + t.hydration);
        switch (t.group) {
            case MEAT -> p.protein = Math.min(100, p.protein + t.food * 0.9f);
            case PLANT -> p.vitamins = Math.min(100, p.vitamins + t.food * 1.1f);
            case NONE -> {
            }
        }

        float poisonChance = 0f;
        if (t == ItemType.SPOILED_MEAT) {
            poisonChance = 0.75f;
        } else if (t == ItemType.RAW_MEAT) {
            poisonChance = 0.25f;
        } else if (freshness < 0.3f) {
            poisonChance = 0.35f;
        }
        if (poisonChance > 0 && Math.random() < poisonChance) {
            p.addAffliction(Affliction.FOOD_POISONING, 90 + (float) Math.random() * 60);
            log("That food didn't sit well... FOOD POISONING sets in.");
        }
        p.inventory.shrink(p.hotbarSel, 1);
        audio.playEat();
        log("Ate " + t.displayName + " (+" + t.food + " food"
                + (freshness < 0.5f && t.spoils() ? ", going off" : "") + ")");
    }

    private void drink(ItemStack held) {
        Player p = player;
        boolean clean = held.type == ItemType.WATERSKIN_CLEAN;
        p.thirst = Math.min(100, p.thirst + (clean ? 60 : 35));
        p.inventory.shrink(p.hotbarSel, 1);
        p.inventory.add(ItemType.WATERSKIN_EMPTY, 1);
        audio.playDrink();
        if (clean) {
            log("You drink clean water (+60 thirst).");
        } else if (Math.random() < 0.30) {
            p.addAffliction(Affliction.FOOD_POISONING, 90 + (float) Math.random() * 60);
            log("The dirty water churns in your gut... FOOD POISONING.");
        } else {
            log("You drink dirty water (+35 thirst). You got lucky this time.");
        }
    }

    private void applyMedical(ItemStack held) {
        Player p = player;
        boolean used = false;
        switch (held.type) {
            case BANDAGE -> {
                if (p.has(Affliction.BLEEDING)) {
                    p.cure(Affliction.BLEEDING);
                    p.woundClean = true;
                    p.health = Math.min(p.maxHealth, p.health + 3);
                    log("You bandage the wound. Bleeding stopped.");
                    used = true;
                } else {
                    log("No bleeding to bandage.");
                }
            }
            case SPLINT -> {
                if (p.has(Affliction.SPRAIN)) {
                    p.cure(Affliction.SPRAIN);
                    log("You splint your leg. You can move normally again.");
                    used = true;
                } else {
                    log("Nothing needs splinting.");
                }
            }
            case ANTISEPTIC -> {
                if (p.has(Affliction.INFECTION)) {
                    p.cure(Affliction.INFECTION);
                    log("The antiseptic burns away the infection.");
                    used = true;
                } else if (p.has(Affliction.BLEEDING)) {
                    p.woundClean = true;
                    log("You disinfect the wound - it won't fester now. Still needs a bandage.");
                    used = true;
                } else {
                    log("No wound to disinfect.");
                }
            }
            case HERBAL_POULTICE -> {
                if (p.has(Affliction.BURN)) {
                    p.cure(Affliction.BURN);
                    log("The poultice soothes your burns.");
                    used = true;
                } else if (p.has(Affliction.INFECTION)) {
                    p.afflictions.computeIfPresent(Affliction.INFECTION, (a, v) -> v * 0.4f);
                    log("The poultice draws out some of the infection.");
                    used = true;
                } else {
                    log("No burns or infection to treat.");
                }
            }
            case MEDICINE -> {
                if (p.has(Affliction.FOOD_POISONING) || p.has(Affliction.SICKNESS)
                        || p.has(Affliction.INFECTION)) {
                    p.cure(Affliction.FOOD_POISONING);
                    p.cure(Affliction.SICKNESS);
                    p.cure(Affliction.INFECTION);
                    log("The medicine works fast. You feel much better.");
                    used = true;
                } else {
                    log("You aren't sick enough to need medicine.");
                }
            }
            default -> {
            }
        }
        if (used) {
            p.inventory.shrink(p.hotbarSel, 1);
            audio.playEquip();
        }
    }

    private void equipHeld(ItemStack held) {
        EquipSlot slot = held.type.equipSlot;
        ItemStack prev = player.equipment[slot.ordinal()];
        player.equipment[slot.ordinal()] = held.copy();
        player.equipment[slot.ordinal()].count = 1;
        player.inventory.shrink(player.hotbarSel, 1);
        if (prev != null) {
            player.inventory.addStack(prev);
        }
        audio.playEquip();
        log("Equipped " + held.type.displayName + " (" + slot.displayName + ").");
    }

    // ------------------------------------------------------------------
    // Interactions (F)
    // ------------------------------------------------------------------

    private void interact() {
        // NPCs first.
        Npc npc = entities.nearestNpc(player.pos.x, player.pos.y, player.pos.z, 3.2f);
        if (npc != null && !npc.raider) {
            activeNpc = npc;
            npcScreen.open();
            uiMode = UiMode.NPC;
            return;
        }
        // Carcasses.
        Carcass carcass = entities.nearestCarcass(player.pos.x, player.pos.y, player.pos.z, 2.6f);
        if (carcass != null) {
            harvestCarcass(carcass);
            return;
        }
        if (targetHit != null) {
            Vec3i pos = new Vec3i(targetHit.x(), targetHit.y(), targetHit.z());
            switch (targetHit.type()) {
                case BERRY_BUSH -> {
                    world.setBlock(pos.x(), pos.y(), pos.z(), BlockType.BERRY_BUSH_EMPTY, true);
                    player.inventory.add(ItemType.BERRY, 2);
                    audio.playEat();
                    log("Harvested 2 berries (the bush will regrow).");
                    return;
                }
                case HERB_PLANT -> {
                    world.setBlock(pos.x(), pos.y(), pos.z(), BlockType.AIR, true);
                    player.inventory.add(ItemType.HERB, 2);
                    audio.playEat();
                    log("Gathered 2 medicinal herbs.");
                    return;
                }
                case CAMPFIRE -> {
                    campfireInteract(pos);
                    return;
                }
                case CRATE -> {
                    openCrateAt(pos);
                    return;
                }
                case WORKBENCH, FURNACE, ANVIL, TANNERY, HERB_STATION, MAP_TABLE -> {
                    uiMode = UiMode.CRAFTING;
                    return;
                }
                case DRYING_RACK -> {
                    rackInteract(pos);
                    return;
                }
                case RAIN_COLLECTOR -> {
                    collectorInteract(pos);
                    return;
                }
                case BEDROLL -> {
                    startSleep(false);
                    return;
                }
                case CAMP_BED -> {
                    boolean atCamp = world.campPos != null
                            && pos.distSq(world.campPos.x(), world.campPos.y(), world.campPos.z()) < 14 * 14;
                    if (atCamp && !faction.campPrivileges()) {
                        log("The camp won't let you use their beds yet (needs Friendly trust).");
                        return;
                    }
                    startSleep(true);
                    return;
                }
                case BEACON -> {
                    beaconInteract();
                    return;
                }
                case BEACON_LIT -> {
                    log("The beacon thrums steadily, its signal cutting through the sky.");
                    return;
                }
                default -> {
                }
            }
        }
        // Drink from / fill at water.
        Raycaster.Hit fluid = Raycaster.cast(world, camera.position, camera.front(), 4.0, true);
        if (fluid != null && fluid.type() == BlockType.WATER) {
            if (player.inventory.count(ItemType.WATERSKIN_EMPTY) > 0) {
                player.inventory.remove(ItemType.WATERSKIN_EMPTY, 1);
                player.inventory.add(ItemType.WATERSKIN_DIRTY, 1);
                audio.playDrink();
                log("Filled a waterskin with untreated water. Boil it at a campfire.");
                return;
            }
            player.thirst = Math.min(100, player.thirst + 35);
            audio.playDrink();
            if (Math.random() < 0.25) {
                player.addAffliction(Affliction.FOOD_POISONING, 90 + (float) Math.random() * 60);
                log("You drank dirty water and feel ill...");
            } else {
                log("You drink from the water (+35 thirst).");
            }
        }
    }

    private void harvestCarcass(Carcass carcass) {
        boolean knife = playerHasKnife();
        ItemType meatType = carcass.rotten() ? ItemType.SPOILED_MEAT : ItemType.RAW_MEAT;
        if (knife) {
            int meat = carcass.meatLeft;
            int hide = carcass.hideLeft;
            if (meat > 0) {
                player.inventory.add(meatType, meat);
            }
            if (hide > 0) {
                player.inventory.add(ItemType.HIDE, hide);
            }
            if (Math.random() < 0.6) {
                player.inventory.add(ItemType.BONE, 1);
            }
            carcass.meatLeft = 0;
            carcass.hideLeft = 0;
            useKnife();
            audio.playEat();
            particles.blood(carcass.pos.x, carcass.pos.y + 0.3f, carcass.pos.z);
            log("Skinned the " + carcass.type.displayName + ": " + meat + " meat, " + hide
                    + " hide" + (carcass.rotten() ? " (the meat is spoiled)" : "") + ".");
        } else if (carcass.meatLeft > 0) {
            carcass.meatLeft--;
            player.inventory.add(meatType, 1);
            audio.playEat();
            particles.blood(carcass.pos.x, carcass.pos.y + 0.3f, carcass.pos.z);
            log("You tear off some meat with your hands. A knife would salvage the hide.");
        } else {
            log("Nothing left worth taking.");
        }
        player.scent = Math.min(1f, player.scent + 0.3f);
    }

    private void campfireInteract(Vec3i pos) {
        boolean lit = world.campfireFuel.getOrDefault(pos, 0f) > 0;
        if (lit && player.inventory.has(ItemType.RAW_MEAT, 1)) {
            player.inventory.remove(ItemType.RAW_MEAT, 1);
            player.inventory.add(ItemType.COOKED_MEAT, 1);
            audio.playClick();
            particles.smoke(pos.x() + 0.5f, pos.y() + 0.8f, pos.z() + 0.5f, 1f);
            log("Cooked meat over the campfire.");
        } else if (lit && player.inventory.has(ItemType.WATERSKIN_DIRTY, 1)) {
            player.inventory.remove(ItemType.WATERSKIN_DIRTY, 1);
            player.inventory.add(ItemType.WATERSKIN_CLEAN, 1);
            audio.playBoil();
            log("Boiled the waterskin - the water is safe to drink now.");
        } else if (player.inventory.has(ItemType.LOG, 1)) {
            player.inventory.remove(ItemType.LOG, 1);
            world.campfireFuel.merge(pos, 120f, Float::sum);
            audio.playClick();
            log("Added a log to the fire (+120s fuel).");
        } else {
            log(lit ? "Bring raw meat to cook, dirty water to boil, or a log for fuel."
                    : "The fire is out. Add a log to relight it.");
        }
    }

    private void rackInteract(Vec3i pos) {
        RackBatch batch = world.rackBatches.get(pos);
        if (batch != null && batch.done()) {
            player.inventory.add(batch.output(), batch.count);
            world.rackBatches.remove(pos);
            audio.playClick();
            log("Collected " + batch.count + "x " + batch.output().displayName + " from the rack.");
            return;
        }
        if (batch != null) {
            int pct = (int) (batch.progress / batch.required() * 100);
            log("Still drying: " + batch.count + "x " + batch.input.displayName + " (" + pct + "%).");
            return;
        }
        ItemType input = null;
        if (player.inventory.count(ItemType.RAW_MEAT) > 0) {
            input = ItemType.RAW_MEAT;
        } else if (player.inventory.count(ItemType.BERRY) > 0) {
            input = ItemType.BERRY;
        }
        if (input == null) {
            log("You need raw meat or berries to dry on the rack.");
            return;
        }
        int count = Math.min(4, player.inventory.count(input));
        player.inventory.remove(input, count);
        world.rackBatches.put(pos, new RackBatch(input, count));
        audio.playClick();
        log("Hung " + count + "x " + input.displayName + " to dry. Keep it out of the rain.");
    }

    private void collectorInteract(Vec3i pos) {
        float liters = world.collectorWater.getOrDefault(pos, 0f);
        if (liters >= 1f && player.inventory.count(ItemType.WATERSKIN_EMPTY) > 0) {
            player.inventory.remove(ItemType.WATERSKIN_EMPTY, 1);
            player.inventory.add(ItemType.WATERSKIN_CLEAN, 1);
            world.collectorWater.put(pos, liters - 1f);
            audio.playDrink();
            log("Filled a waterskin with clean rainwater.");
        } else if (liters >= 1f) {
            player.thirst = Math.min(100, player.thirst + 40);
            world.collectorWater.put(pos, liters - 1f);
            audio.playDrink();
            log("You drink fresh rainwater (+40 thirst).");
        } else {
            log("The collector holds " + String.format("%.1f", liters)
                    + " L. It fills while it rains.");
        }
    }

    private void beaconInteract() {
        switch (world.beaconStage) {
            case 0 -> {
                if (player.inventory.has(ItemType.SIGNAL_CRYSTAL, 1)) {
                    player.inventory.remove(ItemType.SIGNAL_CRYSTAL, 1);
                    world.beaconStage = 1;
                    audio.playCraft();
                    log("Signal crystal installed. Next: wire the array with 3 copper ingots.");
                } else {
                    log("The beacon needs a SIGNAL CRYSTAL. Ancient ruins hold resonant cores...");
                }
            }
            case 1 -> {
                if (player.inventory.has(ItemType.COPPER_INGOT, 3)) {
                    player.inventory.remove(ItemType.COPPER_INGOT, 3);
                    world.beaconStage = 2;
                    audio.playCraft();
                    log("Wiring complete. The beacon needs calibration codes from the camp (Allied).");
                } else {
                    log("Wiring requires 3 COPPER INGOTS (smelt copper ore at a furnace).");
                }
            }
            case 2 -> {
                if (faction.trust >= 75) {
                    world.beaconStage = 3;
                    Vec3i bp = world.beaconPos;
                    world.setBlock(bp.x(), bp.y(), bp.z(), BlockType.BEACON_LIT, true);
                    audio.playDiscover();
                    log("=== THE DISTRESS BEACON IS ALIVE! Its signal pierces the sky. ===");
                    log("You did it. Rescue will come. Survive until then - Veylon isn't done with you.");
                    closeScreens();
                    appState = AppState.VICTORY;
                } else {
                    log("The camp must trust you as an ALLY (trust 75+) to share calibration codes.");
                }
            }
            default -> {
            }
        }
    }

    private void openCrateAt(Vec3i pos) {
        openCrate = world.crateContents.computeIfAbsent(pos, k -> new Inventory(12));
        openCratePos = pos;
        uiMode = UiMode.CRATE;
    }

    /** Called by the crate UI; taking from camp crates is stealing unless trusted. */
    public void onCrateItemTaken(ItemType type, int count) {
        if (openCratePos != null && world.campPos != null && faction.trust < 75
                && openCratePos.distSq(world.campPos.x(), world.campPos.y(), world.campPos.z()) < 9 * 9) {
            faction.addTrust(this, -Math.min(12, 3 + count), "The camp caught you stealing!");
        }
    }

    private Entity findAttackTarget(Vector3f dir) {
        Entity best = null;
        double bestD = 3.4 * 3.4;
        for (Creature c : entities.creatures) {
            double d = candidateDist(c, dir);
            if (d >= 0 && d < bestD) {
                bestD = d;
                best = c;
            }
        }
        for (Npc n : entities.npcs) {
            double d = candidateDist(n, dir);
            if (d >= 0 && d < bestD) {
                bestD = d;
                best = n;
            }
        }
        return best;
    }

    private double candidateDist(Entity e, Vector3f dir) {
        float ex = e.pos.x - camera.position.x;
        float ey = (e.pos.y + e.height * 0.5f) - camera.position.y;
        float ez = e.pos.z - camera.position.z;
        double dist2 = ex * ex + ey * ey + ez * ez;
        if (dist2 > 3.4 * 3.4 || dist2 < 1e-4) {
            return -1;
        }
        double len = Math.sqrt(dist2);
        double dot = (ex * dir.x + ey * dir.y + ez * dir.z) / len;
        return dot > 0.80 ? dist2 : -1;
    }

    private void respawn() {
        log("You died. The frontier reclaims you... (respawned at the crash site)");
        player.dead = false;
        player.health = 55;
        player.hunger = Math.max(player.hunger, 50);
        player.thirst = Math.max(player.thirst, 50);
        player.bodyTemp = 37;
        player.afflictions.clear();
        player.smokeExposure = 0;
        player.protein = Math.max(player.protein, 40);
        player.vitamins = Math.max(player.vitamins, 40);
        player.vel.set(0, 0, 0);
        player.pos.set(spawnPos);
        sleeping = false;
    }

    // ------------------------------------------------------------------
    // Simulation ticks
    // ------------------------------------------------------------------

    @Override
    public void fastTick(float dt) {
        player.tickNeeds(this, dt);
        entities.fastTick(this, dt);
    }

    @Override
    public void mediumTick(float dt) {
        weather.mediumTick(this, dt);
        temperature.mediumTick(this, dt);
        water.mediumTick(this, dt);
        fire.mediumTick(this, dt);

        // Shelter, smoke buildup, toxic fog exposure.
        player.shelter = ShelterSystem.evaluate(world, player.pos.x, player.pos.y, player.pos.z);
        if (player.shelter.indoor() && player.nearFireHeat(this) > 3) {
            player.smokeExposure += 9f * dt;
            if (player.smokeExposure > 30) {
                particles.smoke(player.pos.x, player.pos.y + 1.9f, player.pos.z, 0.4f);
            }
        }
        if (events.toxicFog() && player.exposedToSky && !player.shelter.roofed()) {
            if (Math.random() < 0.04 && !player.has(Affliction.SICKNESS)) {
                player.addAffliction(Affliction.SICKNESS, 120);
                log("The toxic fog claws at your lungs... SICKNESS takes hold.");
            }
        }

        // POI discovery.
        for (Poi poi : world.pois) {
            if (!poi.discovered
                    && poi.pos.distSq(player.pos.x, player.pos.y, player.pos.z) < 15 * 15) {
                poi.discovered = true;
                world.discoveredPois.add(poi.pos);
                audio.playDiscover();
                log("DISCOVERED: " + poi.type.displayName + " (marked on your map)");
                if (poi.type == Poi.PoiType.PREDATOR_DEN) {
                    log("Bones and claw marks everywhere... wolves den here.");
                }
                faction.onPoiDiscovered(this);
            }
        }

        updateAmbienceMix();
    }

    private void updateAmbienceMix() {
        boolean snow = weather.effective() == WeatherSystem.Weather.SNOW;
        float rainGain = weather.isPrecip() && !snow
                ? weather.intensity() * (player.exposedToSky ? 1f : 0.45f) : 0f;
        float windGain = 0f;
        if (weather.isStormy()) {
            windGain = 0.9f;
        } else if (snow || player.pos.y > 52) {
            windGain = 0.45f;
        } else if (weather.effective() == WeatherSystem.Weather.CLOUDY) {
            windGain = 0.2f;
        }
        float fireHeat = player.nearFireHeat(this);
        float fireGain = fireHeat > 1 ? Math.min(1f, fireHeat / 14f) : 0f;
        int surface = world.surfaceHeight((int) player.pos.x, (int) player.pos.z);
        float caveGain = player.pos.y < surface - 5 ? 0.9f : 0f;
        boolean cricketBiome = player.biome == com.veylon.world.Biome.MEADOW
                || player.biome == com.veylon.world.Biome.PINE_FOREST
                || player.biome == com.veylon.world.Biome.MARSH;
        float cricketGain = time.isNight() && !weather.isPrecip() && caveGain == 0 && cricketBiome
                ? 0.8f : 0f;
        float beaconGain = 0f;
        if (world.beaconStage >= 3 && world.beaconPos != null) {
            double d = world.beaconPos.distSq(player.pos.x, player.pos.y, player.pos.z);
            if (d < 22 * 22) {
                beaconGain = (float) (1.0 - Math.sqrt(d) / 22.0);
            }
        }
        audio.setAmbience(rainGain, windGain, fireGain, caveGain, cricketGain, beaconGain);
    }

    @Override
    public void slowTick(float dt) {
        plants.slowTick(this, dt);
        events.slowTick(this, dt);
        faction.slowTick(this, dt);
        entities.slowTick(this);
        entities.tickWorldDetritus(this, dt);
        itemConditions.slowTick(this, dt);

        // Clear the camp illness event once everyone has recovered.
        boolean anySick = false;
        for (Npc n : entities.npcs) {
            if (n.sick && !n.dead) {
                anySick = true;
                break;
            }
        }
        if (!anySick) {
            events.onCampCured();
        }

        // Free GPU meshes for far-away chunks (block data stays loaded).
        int pcx = Math.floorDiv((int) player.pos.x, 16);
        int pcz = Math.floorDiv((int) player.pos.z, 16);
        for (Chunk c : world.loadedChunks()) {
            if (c.meshOpaque != null
                    && Math.max(Math.abs(c.cx - pcx), Math.abs(c.cz - pcz)) > renderer.renderRadius() + 3) {
                c.deleteMeshes();
                chunkMeshDeletes++;
            }
        }
    }

    @Override
    public void onBlockChanged(int x, int y, int z, BlockType oldType, BlockType newType) {
        water.notifyBlockChanged(world, x, y, z);
    }

    /** Crafting stations within working distance of the player. */
    public Set<Station> nearbyStations() {
        EnumSet<Station> found = EnumSet.of(Station.HAND);
        int px = (int) Math.floor(player.pos.x);
        int py = (int) Math.floor(player.pos.y);
        int pz = (int) Math.floor(player.pos.z);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockType t = world.getBlock(px + dx, py + dy, pz + dz);
                    switch (t) {
                        case WORKBENCH -> found.add(Station.WORKBENCH);
                        case CAMPFIRE -> {
                            if (world.campfireFuel.getOrDefault(
                                    new Vec3i(px + dx, py + dy, pz + dz), 0f) > 0) {
                                found.add(Station.CAMPFIRE);
                            }
                        }
                        case FURNACE -> found.add(Station.FURNACE);
                        case ANVIL -> found.add(Station.ANVIL);
                        case TANNERY -> found.add(Station.TANNERY);
                        case HERB_STATION -> found.add(Station.HERB_STATION);
                        case MAP_TABLE -> found.add(Station.MAP_TABLE);
                        default -> {
                        }
                    }
                }
            }
        }
        return found;
    }

    public void log(String msg) {
        eventLog.add(String.format("[D%d %02d:%02d] %s", time.day(), time.hour(), time.minute(), msg));
    }
}
