package com.veylon;

import com.veylon.ai.FactionSystem;
import com.veylon.combat.WeaponDefinition;
import com.veylon.engine.AudioManager;
import com.veylon.engine.Camera;
import com.veylon.engine.Input;
import com.veylon.engine.ParticleSystem;
import com.veylon.engine.Renderer;
import com.veylon.engine.UiRenderer;
import com.veylon.engine.Window;
import com.veylon.entity.Affliction;
import com.veylon.entity.Creature;
import com.veylon.entity.Entity;
import com.veylon.entity.EntityManager;
import com.veylon.entity.Npc;
import com.veylon.entity.Player;
import com.veylon.entity.PlayerMovementSystem;
import com.veylon.entity.PlayerTreatmentSystem;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.item.Station;
import com.veylon.input.PlayerInteractionSystem;
import com.veylon.gfx.FrameProfiler;
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
import com.veylon.world.Chunk;
import com.veylon.world.Poi;
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

    /** Player-facing NPC action selected by the same path used for prompts and F. */
    public enum NpcInteraction {
        NONE, TALK, RESCUE_BLOCKED, RESCUE_READY
    }

    /** Outcome from the real bow input command, exposed for gameplay integration tests. */
    public enum BowCommandResult {
        NONE, DRAWING, CANCELLED, FIRED, NO_AMMO, COOLDOWN, INVALID_WEAPON
    }

    /** Outcome from the production firearm trigger/reload command. */
    public enum FirearmCommandResult {
        NONE, FIRED, DRY_FIRE, RELOAD_STARTED, RELOADING, NO_AMMO, COOLDOWN,
        INVALID_WEAPON
    }

    enum AppState {
        TITLE, TITLE_OPTIONS, LOADING, PLAYING, DEATH, VICTORY;

        /** True while a live world is being drawn, so render stats are meaningful. */
        boolean rendersWorld() {
            return this != TITLE && this != TITLE_OPTIONS && this != LOADING;
        }
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
    private final PlayerMovementSystem playerMovement = new PlayerMovementSystem();
    private final PlayerMovementSystem.Command movementCommand = new PlayerMovementSystem.Command();
    private final PlayerMovementSystem.FrameResult movementResult =
            new PlayerMovementSystem.FrameResult();
    private final PlayerInteractionSystem playerInteractions = new PlayerInteractionSystem();
    private final PlayerInteractionSystem.FrameInput interactionInput =
            new PlayerInteractionSystem.FrameInput();
    final PlayerTreatmentSystem playerTreatments = new PlayerTreatmentSystem();

    // World & simulation.
    public World world;
    public Player player;
    public final EntityManager entities = new EntityManager();
    public final FactionSystem faction = new FactionSystem();
    // 0.3.0 world-expansion systems.
    public final com.veylon.combat.WorldNoise noise = new com.veylon.combat.WorldNoise();
    public final com.veylon.combat.ProjectileSystem projectiles = new com.veylon.combat.ProjectileSystem();
    public final com.veylon.combat.ExplosionSystem explosions = new com.veylon.combat.ExplosionSystem();
    public final com.veylon.settlement.SettlementManager settlementManager =
            new com.veylon.settlement.SettlementManager();
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

    // UI. Screens are drawn and driven from this class alone — nothing outside
    // com.veylon holds one — so they stay package-private. A screen that needs
    // to be reachable from elsewhere should expose a command on Game rather than
    // handing out its instance.
    final Hud hud = new Hud();
    final InventoryScreen inventoryScreen = new InventoryScreen();
    final CraftingScreen craftingScreen = new CraftingScreen();
    final CrateScreen crateScreen = new CrateScreen();
    /** @VisibleForTesting — public only for gameplay tests outside com.veylon. */
    public final NpcScreen npcScreen = new NpcScreen();
    final PauseMenu pauseMenu = new PauseMenu();
    final MapScreen mapScreen = new MapScreen();
    final DebugOverlay debugOverlay = new DebugOverlay();
    final SimulationPanel simulationPanel = new SimulationPanel();
    final TitleScreen titleScreen = new TitleScreen();
    final GraphicsOptionsScreen graphicsOptionsScreen = new GraphicsOptionsScreen();

    /** @VisibleForTesting — public only for gameplay tests outside com.veylon. */
    public UiMode uiMode = UiMode.NONE;
    boolean debugShown;
    boolean simPanelShown;
    public boolean simPaused;
    /** Set by F2; the run loop captures the back buffer after the frame renders. */
    boolean pendingScreenshot;

    // Per-frame state.
    public Raycaster.Result targetHit;
    final Raycaster.MutableHit targetHitBuffer = new Raycaster.MutableHit();
    final Raycaster.MutableHit fluidHitBuffer = new Raycaster.MutableHit();
    public float miningProgress;
    Vec3i miningTarget;
    public String interactPrompt;
    public double totalTime;
    public int fps;
    public double frameMs;
    public final FrameProfiler frameProfiler = new FrameProfiler();
    private int fpsCounter;
    private double fpsTimer;

    AppState appState = AppState.TITLE;
    private LoadRequest loadRequest;
    private boolean loadingPresented;
    private boolean automatedRun;
    long sessionSeed;
    private float deathTimer;
    /** Counted here because world teardown owns it; the QA report reads it. */
    long chunkMeshDeletes;

    /** Opt-in benchmark, capture and release-smoke scaffolding. Inert without VEYLON_* env. */
    private final QaHarness qa = new QaHarness(this);

    /** Melee, bow, firearm and thrown-weapon rules; see the delegates below. */
    final PlayerCombatSystem combat = new PlayerCombatSystem(this);

    /** Hold-to-mine, block breaking outcomes and placement. */
    private final PlayerBlockActions blockActions = new PlayerBlockActions(this);

    /** Read-only builder for the HUD's "[F] ..." interaction hint. */
    private final InteractPromptBuilder prompts = new InteractPromptBuilder(this);

    /** Crate open/transfer commands and their theft attribution. */
    private final CrateTransactionSystem crates = new CrateTransactionSystem(this);

    /** Eating, drinking, treating wounds and wearing gear. */
    final PlayerConsumables consumables = new PlayerConsumables(this);

    /** F-key and right-click handlers against world state, NPCs and stations. */
    private final WorldInteractions interactions = new WorldInteractions(this);

    /** Ambient particle emitters and the ambient audio mix. */
    private final AmbienceSystem ambience = new AmbienceSystem(this);

    /** Sleep eligibility, quality scoring and the night's effects. */
    private final SleepSystem sleep = new SleepSystem(this);

    // Ranged-weapon aim state the HUD draws. The rules live in PlayerCombatSystem;
    // these stay here because Hud reads them straight off the Game instance.
    /** 0..1 bow draw progress while holding LMB with a bow. */
    public float bowDraw;
    public boolean drawingBow;
    /** Seconds remaining of an active reload (0 = not reloading). */
    public float reloadTimer;
    /** Total duration of the active reload, for the HUD bar. */
    public float reloadTotal;

    // Animation / feedback timers.
    public float swingTimer;
    public float walkBob;
    private float footstepTimer;
    float hitSoundTimer;
    private float emitterTimer;
    private float breathTimer;
    private float coughTimer;

    // Sleep.
    public boolean sleeping;
    public float sleepFade;
    private float sleepQuality;
    private float sleptMinutes;

    public Inventory openCrate;
    /** @VisibleForTesting — public only for gameplay tests outside com.veylon. */
    public Vec3i openCratePos;
    public Npc activeNpc;

    private final PlayerInteractionSystem.Commands interactionCommands =
            new PlayerInteractionSystem.Commands() {
                @Override
                public void updateRanged(float dt, ItemStack held, WeaponDefinition weapon,
                                         Vector3f direction,
                                         PlayerInteractionSystem.FrameInput frameInput) {
                    combat.updateRangedWeapon(dt, held, weapon, direction, frameInput);
                }

                @Override
                public void advanceNonRangedCooldown(float dt) {
                    combat.advanceRangedCooldown(dt);
                }

                @Override
                public void cancelRangedState() {
                    combat.cancelRangedState();
                }

                @Override
                public void updatePrimary(float dt, Vector3f direction,
                                          boolean primaryPressed) {
                    combat.updatePrimaryAction(dt, direction, primaryPressed);
                }

                @Override
                public void resetPrimary() {
                    miningProgress = 0;
                    miningTarget = null;
                }

                @Override
                public void useSecondary() {
                    interactions.rightClick();
                }

                @Override
                public void interact() {
                    interactions.interact();
                }
            };

    final Vector3f spawnPos = new Vector3f();

    public void run() {
        qa.applyResolutionOverride();
        window.setInitialWindowedSize(renderer.settings.windowWidth, renderer.settings.windowHeight);
        window.create("VEYLON: Deep Frontier", input);
        renderer.init();
        window.setVsync(renderer.settings.vsync);
        if (renderer.settings.fullscreen) {
            window.setFullscreen(true);
        }
        ui.init();
        audio.init();
        sessionSeed = qa.configuredSeed();

        boolean smoke = System.getenv("VEYLON_SMOKE") != null;
        String scene = System.getenv("VEYLON_SCENE");
        String shotEnv = System.getenv("VEYLON_SHOT");
        String frontend = System.getenv("VEYLON_FRONTEND");
        boolean frontendQa = frontend != null && !frontend.isBlank();
        automatedRun = smoke || (scene != null && !scene.isBlank())
                || (shotEnv != null && !shotEnv.isBlank()) || frontendQa;
        if (automatedRun && !frontendQa) {
            newWorld(sessionSeed, true);
            qa.applyBenchmarkScene(scene);
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
                        qa.setStaticLoadingQa(true);
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
        qa.resetSmokeRun();
        while (!window.shouldClose()) {
            double now = glfwGetTime();
            double rawDt = now - last;
            double dt = Math.min(0.1, rawDt);
            last = now;
            totalTime = now;
            frameProfiler.record(rawDt);
            // Only ever active inside a smoke run; the harness owns the guard.
            qa.recordFortressApproach(rawDt, now);

            window.poll();
            qa.updateShowcases(now - start);
            frame(dt);
            qa.sampleRenderStats();
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
            if (appState == AppState.LOADING && loadingPresented && !qa.staticLoadingQa()) {
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
                    qa.equipFromInventoryFirst(ItemType.HIDE_COAT);
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
                if (smokePhase == 3 && now - start > 7.0) {
                    smokePhase = 4;
                    qa.beginSmokeFortressApproach(now);
                }
                if (now - start > smokeSeconds) {
                    window.pollGlErrors("smoke-gate");
                    smokeGateFailure = qa.emitSmokeReport(smokeSeconds, smokeSaveOk, smokeLoadOk);
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

    // ------------------------------------------------------------------
    // World setup
    // ------------------------------------------------------------------

    public void newWorld(long seed, boolean fresh) {
        newWorld(seed, fresh, World.CURRENT_GENERATOR);
    }

    public void newWorld(long seed, boolean fresh, int generatorVersion) {
        // Save-load and front-end transitions can replace a live world. Release
        // its bounded GPU meshes and reset cross-world simulation queues first.
        releaseWorldMeshes();
        scheduler.reset();
        time.reset();
        weather.reset();
        temperature.reset();
        fire.reset();
        water.reset();
        events.reset();
        plants.reset();
        itemConditions.reset();
        noise.reset();
        projectiles.reset();
        explosions.reset();
        settlementManager.reset();
        particles.count = 0;
        reseedSimulation(seed);
        world = new World(seed, generatorVersion);
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
        faction.resetQuestRuntime();
        faction.alliedGiftGiven = false;
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
        drawingBow = false;
        bowDraw = 0;
        combat.reset();
        crates.reset();

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
    /**
     * Reseeds every generator that affects simulation outcomes, so one world
     * seed replays identically instead of inheriting RNG state from whatever
     * world ran before it in the same process.
     *
     * <p>Each system gets a distinct salt so their streams stay independent —
     * seeding them all identically would correlate, say, weather rolls with
     * creature decisions. Presentation-only randomness (audio variation, NPC
     * screen flavour) is deliberately left unseeded; it cannot affect outcomes.
     *
     * <p>Tests that need an exact sequence still call {@code setRandomSeed}
     * directly after {@code newWorld}, and those explicit seeds win.
     */
    private void reseedSimulation(long seed) {
        particles.setRandomSeed(seed ^ 0x5645594c4f4eL);
        ambience.reseed(seed ^ 0x46584c4f4eL);
        entities.setRandomSeed(seed ^ 0x454e5449545933L);
        projectiles.setRandomSeed(seed ^ 0x50524f4a4543L);
        explosions.setRandomSeed(seed ^ 0x4558504c4f53L);
        settlementManager.setRandomSeed(seed ^ 0x534554544c4dL);
        faction.setRandomSeed(seed ^ 0x464143544e53L);
        weather.setRandomSeed(seed ^ 0x574541544852L);
        water.setRandomSeed(seed ^ 0x5741544552L);
        fire.setRandomSeed(seed ^ 0x4649524553L);
        plants.setRandomSeed(seed ^ 0x504c414e5453L);
        events.setRandomSeed(seed ^ 0x4556454e5453L);
        // Static AI decision jitter must also replay deterministically per seed;
        // otherwise QA runs and tests inherit RNG state from earlier worlds.
        com.veylon.ai.SettledNpcAI.reseed(seed ^ 0x5345544e5043L);
        com.veylon.ai.CreatureAI.reseed(seed ^ 0x435245415455L);
        com.veylon.ai.NpcAI.reseed(seed ^ 0x4c454741434eL);
    }

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
            projectiles.update(this, dt);
            explosions.tickFuses(this, dt);
            noise.update(dt);
            updateEmitters(dt);
        }
        audio.update(dt);

        // Camera follows the player eye.
        camera.position.set(player.pos.x, player.pos.y + player.eyeHeight(), player.pos.z);
        player.yaw = camera.yaw;
        qa.updateMiningShowcase(dt);
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
            qa.updateTitleOptionsQa(dt);
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

    private void handlePauseOptions(GraphicsOptionsScreen.Result result) {
        if (result.action() == GraphicsOptionsScreen.Action.NONE) {
            return;
        }
        if (result.action() == GraphicsOptionsScreen.Action.APPLY) {
            applyGraphicsOptions(result);
        }
        uiMode = UiMode.PAUSE;
    }

    void applyGraphicsOptions(GraphicsOptionsScreen.Result result) {
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

    /** Ambient particle and sound emitters driven by world state. */
    private void updateEmitters(float dt) {
        ambience.updateEmitters(dt);
    }

    // ------------------------------------------------------------------
    // Sleep
    // ------------------------------------------------------------------

    /** Attempts to begin sleeping; refusal is logged by the sleep system. */
    void startSleep(boolean campBed) {
        sleep.startSleep(campBed);
    }

    private void tickSleep(float dt) {
        sleep.tickSleep(dt);
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
        boolean fwd = input.isKeyDown(GLFW_KEY_W);
        boolean back = input.isKeyDown(GLFW_KEY_S);
        boolean left = input.isKeyDown(GLFW_KEY_A);
        boolean right = input.isKeyDown(GLFW_KEY_D);
        movementCommand.set(camera.yaw, fwd, back, left, right,
                input.isKeyDown(GLFW_KEY_LEFT_SHIFT),
                input.isKeyDown(GLFW_KEY_LEFT_CONTROL),
                input.isKeyDown(GLFW_KEY_SPACE), input.wasKeyPressed(GLFW_KEY_SPACE));
        playerMovement.update(p, world, movementCommand, dt, movementResult);
        if (movementResult.enteredWater) {
            particles.splash(p.pos.x, p.pos.y + 0.4f, p.pos.z);
            audio.playFootstep(BlockType.WATER, true);
        }

        // Footsteps with material sounds and view bob.
        boolean moving = movementResult.horizontalIntent && p.onGround;
        if (moving) {
            walkBob += dt * (p.sprinting ? 1.6f : 1f);
            footstepTimer -= dt;
            if (footstepTimer <= 0) {
                footstepTimer = p.crouching ? 0.62f : (p.sprinting ? 0.31f : 0.45f);
                BlockType under = world.getBlock((int) Math.floor(p.pos.x),
                        (int) Math.floor(p.pos.y - 0.1f), (int) Math.floor(p.pos.z));
                audio.playFootstep(under, p.inWater);
                emitPlayerFootstepNoise(p.sprinting, p.crouching);
            }
        }
    }

    /**
     * Applies the same rope-ladder intent used by the native movement path.
     * Exposed as a gameplay command so deterministic integration coverage does
     * not need to manufacture GLFW key state.
     */
    public boolean applyPlayerClimbCommand(boolean ascendHeld, boolean forwardHeld) {
        return playerMovement.applyClimbCommand(player, ascendHeld, forwardHeld);
    }

    /**
     * Emits the same positioned footstep perception event used by native
     * movement. Exposed as a small command seam so input-independent gameplay
     * tests can verify sprint/crouch audibility without a GLFW window.
     */
    public boolean emitPlayerFootstepNoise(boolean sprinting, boolean crouching) {
        if (player == null || crouching) {
            return false;
        }
        player.noise = Math.min(1f, player.noise + (sprinting ? 0.12f : 0.05f));
        noise.emit(this, player.pos.x, player.pos.y, player.pos.z,
                sprinting ? 22f : 10f, sprinting ? 0.38f : 0.15f,
                sprinting ? "sprint" : "footstep", true, player);
        return true;
    }

    // ------------------------------------------------------------------
    // Actions: mining, placing, attacking, interacting
    // ------------------------------------------------------------------

    private void updateActions(float dt) {
        advancePlayerAttackCooldown(dt);
        tickReload(dt);
        Vector3f origin = camera.position;
        Vector3f dir = camera.front();
        targetHit = Raycaster.castInto(world, origin, dir, 5.2, false, targetHitBuffer)
                ? targetHitBuffer : null;

        updatePrompt();
        ItemStack heldItem = player.selected();
        WeaponDefinition weapon =
                com.veylon.combat.WeaponRegistry.of(heldItem == null ? null : heldItem.type);
        interactionInput.set(dt,
                input.isMouseDown(GLFW_MOUSE_BUTTON_LEFT),
                input.wasMousePressed(GLFW_MOUSE_BUTTON_LEFT),
                input.wasMousePressed(GLFW_MOUSE_BUTTON_RIGHT),
                input.wasKeyPressed(GLFW_KEY_F), input.wasKeyPressed(GLFW_KEY_R));
        playerInteractions.update(interactionInput, heldItem, weapon, dir, interactionCommands);
    }

    // ------------------------------------------------------------------
    // Combat delegates
    //
    // The rules live in PlayerCombatSystem. These entry points stay on Game
    // because the native input path, the HUD, EntityManager and the gameplay
    // tests all drive combat through the Game instance.
    // ------------------------------------------------------------------

    /** Advances the melee cooldown shared by the native LMB path and tests. */
    public void advancePlayerAttackCooldown(float dt) {
        combat.advancePlayerAttackCooldown(dt);
    }

    /** Gameplay melee command: enforces reach, cooldown, damage and reputation. */
    public boolean performPlayerAttack(Entity victim) {
        return combat.performPlayerAttack(victim);
    }

    /** Production firearm trigger/reload command used by input and tests alike. */
    public FirearmCommandResult updateFirearmCommand(float dt, boolean triggerHeld,
                                                     boolean triggerPressed,
                                                     boolean reloadPressed, Vector3f dir) {
        return combat.updateFirearmCommand(dt, triggerHeld, triggerPressed, reloadPressed, dir);
    }

    /** Production bow draw/release command used by input and tests alike. */
    public BowCommandResult updateBowCommand(float dt, boolean triggerHeld,
                                             boolean triggerPressed, Vector3f dir) {
        return combat.updateBowCommand(dt, triggerHeld, triggerPressed, dir);
    }

    /** Production thrown-explosive command; true only when a bomb was thrown. */
    public boolean updateThrownWeaponCommand(float dt, boolean attackPressed, Vector3f dir) {
        return combat.updateThrownWeaponCommand(dt, attackPressed, dir);
    }

    /** Ammunition the HUD shows for the held bow. */
    public ItemType selectedBowAmmo() {
        return combat.selectedBowAmmo();
    }

    /** Native [R] bow command: switch between basic and iron arrows. */
    public ItemType cycleBowAmmo() {
        return combat.cycleBowAmmo();
    }

    /** Starts a reload using the currently selected firearm. */
    public boolean startReloadSelected() {
        return combat.startReloadSelected();
    }

    /** Advances an active reload, completing or cancelling it. */
    public void tickReload(float dt) {
        combat.tickReload(dt);
    }

    /** Drops an in-flight reload without consuming ammunition. */
    public void cancelReload() {
        combat.cancelReload();
    }

    /** True when the inventory holds any knife, which gates skinning yields. */
    public boolean playerHasKnife() {
        return combat.playerHasKnife();
    }

    /** Called by EntityManager when the player's hit killed a creature. */
    public void onCreatureKilled(Creature c) {
        combat.onCreatureKilled(c);
    }

    /** Called by EntityManager when the player's hit killed a raider. */
    public void onRaiderKilled() {
        combat.onRaiderKilled();
    }

    /** Recomputes the HUD interaction hint for whatever the player is facing. */
    public void updatePrompt() {
        prompts.update();
    }

    /** HUD text for the same lantern state consumed by the F-key command. */
    public String lanternPrompt(Vec3i pos) {
        return prompts.lanternPrompt(pos);
    }

    /** Advances the hold-to-mine timer against the currently targeted block. */
    void mine(float dt) {
        blockActions.mine(dt);
    }

    /** Completes a player mining action after the hold-to-mine timer succeeds. */
    public boolean completePlayerBlockBreak(Vec3i pos) {
        return blockActions.completePlayerBlockBreak(pos);
    }

    /** Gameplay placement command shared by RMB and integration tests. */
    public boolean placeSelectedBlockAt(int px, int py, int pz) {
        return blockActions.placeSelectedBlockAt(px, py, pz);
    }

    // ------------------------------------------------------------------
    // Interaction delegates
    //
    // Routing and the station handlers live in WorldInteractions; self-directed
    // item use lives in PlayerConsumables. These entry points stay on Game
    // because native input, the HUD prompt builder and the gameplay tests all
    // drive interaction through the Game instance.
    // ------------------------------------------------------------------

    /** Normal talk/trade gate. Captives use the rescue action and never this UI. */
    public boolean canOpenNpcInteraction(Npc npc) {
        return interactions.canOpenNpcInteraction(npc);
    }

    /** Shared decision used by the HUD prompt and the real interaction command. */
    public NpcInteraction npcInteraction(Npc npc) {
        return interactions.npcInteraction(npc);
    }

    /** Ignores closer hostile guards so a reachable prisoner remains selectable. */
    Npc nearestNpcForInteraction(float range) {
        return interactions.nearestNpcForInteraction(range);
    }

    /** Gameplay command used by the F-key path and integration tests. */
    public boolean interactWithNearbyNpc() {
        return interactions.interactWithNearbyNpc();
    }

    /** Gameplay block-interaction command shared by the F-key path and tests. */
    public boolean interactWithBlockAt(Vec3i pos) {
        return interactions.interactWithBlockAt(pos);
    }

    /** Gameplay command shared by the real F-key path and integration tests. */
    public boolean recoverNearbyArrow() {
        return interactions.recoverNearbyArrow();
    }

    /** Gameplay command shared by the real F-key path for harvesting carcasses. */
    public boolean interactWithNearbyCarcass() {
        return interactions.interactWithNearbyCarcass();
    }

    /** Gameplay command shared by the real F-key interaction and integration tests. */
    public boolean interactLantern(Vec3i pos) {
        return interactions.interactLantern(pos);
    }

    // ------------------------------------------------------------------
    // Crate delegates
    //
    // Transaction and theft-attribution rules live in CrateTransactionSystem.
    // These stay on Game because CrateScreen and the settlement tests drive
    // crate interaction through the Game instance.
    // ------------------------------------------------------------------

    /** Opens a real world crate; both RMB/F interaction paths use this command. */
    public boolean openCrateAt(Vec3i pos) {
        return crates.openCrateAt(pos);
    }

    /** Called by the crate UI; taking from camp crates is stealing unless trusted. */
    public void onCrateItemTaken(ItemType type, int count) {
        crates.onCrateItemTaken(type, count);
    }

    /** Attributes one logical crime event with at most one transfer per item type. */
    public void onCrateItemTaken(ItemType type, int count, long logicalEventId) {
        crates.onCrateItemTaken(type, count, logicalEventId);
    }

    /** Attributes a callback to one logical crime event and one physical transfer. */
    public void onCrateItemTaken(ItemType type, int count, long logicalEventId,
                                 long transferId) {
        crates.onCrateItemTaken(type, count, logicalEventId, transferId);
    }

    /** Real crate-UI transfer command used by mouse input and integration tests. */
    public int transferCrateItemToPlayer(int slot) {
        return crates.transferCrateItemToPlayer(slot);
    }

    /** Real crate-UI deposit command; returned supplies are attributed by crate position. */
    public int transferPlayerItemToCrate(int slot) {
        return crates.transferPlayerItemToCrate(slot);
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
        settlementManager.fastTick(this, dt);
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
        Vec3i fumarole = world.nearestBasaltFumarole(
                player.pos.x, player.pos.y + 0.5f, player.pos.z, 5);
        if (fumarole != null) {
            player.smokeExposure += 14f * dt;
            particles.smoke(fumarole.x() + 0.5f, fumarole.y() + 0.35f,
                    fumarole.z() + 0.5f, 0.75f);
        }
        if (events.toxicFog() && player.exposedToSky && !player.shelter.roofed()) {
            if (Math.random() < 0.04 && !player.has(Affliction.SICKNESS)) {
                player.addAffliction(Affliction.SICKNESS, 120);
                log("The toxic fog claws at your lungs... SICKNESS takes hold.");
            }
        }

        // Open flame cooks off adjacent powder kegs.
        for (Vec3i f : fire.burningCells()) {
            for (int[] off : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0},
                    {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                Vec3i k = new Vec3i(f.x() + off[0], f.y() + off[1], f.z() + off[2]);
                if (explosions.tryArmKeg(this, k, 1.5f, false)) {
                    audio.playFuse(k.x() + 0.5f, k.y() + 0.5f, k.z() + 0.5f);
                }
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
                faction.onPoiDiscovered(this, poi);
                if (poi.type == Poi.PoiType.ABANDONED_MINE
                        || poi.type == Poi.PoiType.SMUGGLER_CACHE
                        || poi.type == Poi.PoiType.HIDEOUT_CAVE
                        || poi.type == Poi.PoiType.RESONANT_SHRINE
                        || poi.type == Poi.PoiType.STALKER_NEST
                        || poi.type == Poi.PoiType.EXPEDITION_CAMP) {
                    faction.onCaveExplored(this, poi);
                }
            }
        }

        ambience.updateAmbienceMix();
    }

    @Override
    public void slowTick(float dt) {
        plants.slowTick(this, dt);
        events.slowTick(this, dt);
        faction.slowTick(this, dt);
        entities.slowTick(this);
        entities.tickWorldDetritus(this, dt);
        itemConditions.slowTick(this, dt);
        settlementManager.slowTick(this, dt);

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
