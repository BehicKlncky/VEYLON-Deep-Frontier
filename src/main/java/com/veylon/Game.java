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
import com.veylon.ui.AudioOptionsScreen;
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
import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;

/** Central orchestrator: game loop, input handling, player actions, tick wiring. */
public class Game implements SimulationScheduler.Ticks, World.BlockListener {

    public enum UiMode {
        NONE, INVENTORY, CRAFTING, PAUSE, OPTIONS, MAP, CRATE, NPC, AUDIO_OPTIONS
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
        TITLE, TITLE_OPTIONS, LOADING, PLAYING, DEATH, VICTORY, TITLE_AUDIO_OPTIONS;

        /** True while a live world is being drawn, so render stats are meaningful. */
        boolean rendersWorld() {
            return this != TITLE && this != TITLE_OPTIONS && this != TITLE_AUDIO_OPTIONS && this != LOADING;
        }
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
    final SimulationScheduler scheduler = new SimulationScheduler();

    // UI. Screens are drawn and driven from this class alone — nothing outside
    // com.veylon holds one — so they stay package-private. A screen that needs
    // to be reachable from elsewhere should expose a command on Game rather than
    // handing out its instance.
    final Hud hud = new Hud();
    final InventoryScreen inventoryScreen = new InventoryScreen();
    final CraftingScreen craftingScreen = new CraftingScreen();
    final CrateScreen crateScreen = new CrateScreen();
    /** Visible for testing; public only for gameplay tests outside {@code com.veylon}. */
    public final NpcScreen npcScreen = new NpcScreen();
    final PauseMenu pauseMenu = new PauseMenu();
    final MapScreen mapScreen = new MapScreen();
    final DebugOverlay debugOverlay = new DebugOverlay();
    final SimulationPanel simulationPanel = new SimulationPanel();
    final TitleScreen titleScreen = new TitleScreen();
    final AudioOptionsScreen audioOptionsScreen = new AudioOptionsScreen();
    final GraphicsOptionsScreen graphicsOptionsScreen = new GraphicsOptionsScreen();

    /** Visible for testing; public only for gameplay tests outside {@code com.veylon}. */
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
    private boolean automatedRun;
    long sessionSeed;
    float deathTimer;
    /** Counted here because world teardown owns it; the QA report reads it. */
    long chunkMeshDeletes;

    /** Opt-in benchmark, capture and release-smoke scaffolding. Inert without VEYLON_* env. */
    final QaHarness qa = new QaHarness(this);

    /** Reset, reseed, construct and camp placement for every new or loaded world. */
    private final WorldBootstrap bootstrap = new WorldBootstrap(this);

    /** Title, graphics options and loading -- the states with no world in them. */
    final FrontendController frontend = new FrontendController(this);

    /** VEYLON_* benchmark scenes, timed captures and the release smoke gate. */
    private final AutomatedRunDriver automation = new AutomatedRunDriver(this);

    /** Global keys: screens, debug overlays, quick save/load, hotbar. */
    private final HotkeyRouter hotkeys = new HotkeyRouter(this);

    /** Shelter, smoke, vents, discovery and the stations within reach. */
    private final PlayerEnvironmentSystem environment = new PlayerEnvironmentSystem(this);

    /** Melee, bow, firearm and thrown-weapon rules; see the delegates below. */
    final PlayerCombatSystem combat = new PlayerCombatSystem(this);

    /** Hold-to-mine, block breaking outcomes and placement. */
    final PlayerBlockActions blockActions = new PlayerBlockActions(this);

    /** Read-only builder for the HUD's "[F] ..." interaction hint. */
    private final InteractPromptBuilder prompts = new InteractPromptBuilder(this);

    /** Crate open/transfer commands and their theft attribution. */
    final CrateTransactionSystem crates = new CrateTransactionSystem(this);

    /** Eating, drinking, treating wounds and wearing gear. */
    final PlayerConsumables consumables = new PlayerConsumables(this);

    /** F-key and right-click handlers against world state, NPCs and stations. */
    final WorldInteractions interactions = new WorldInteractions(this);

    /** Ambient particle emitters and the ambient audio mix. */
    final AmbienceSystem ambience = new AmbienceSystem(this);

    /** Sleep eligibility, quality scoring and the night's effects. */
    final SleepSystem sleep = new SleepSystem(this);

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

    // Sleep.
    public boolean sleeping;
    public float sleepFade;

    public Inventory openCrate;
    /** Visible for testing; public only for gameplay tests outside {@code com.veylon}. */
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
        automatedRun = automation.configure();

        double last = glfwGetTime();
        frameProfiler.reset();
        automation.beginSession(last);
        while (!window.shouldClose()) {
            double now = glfwGetTime();
            double rawDt = now - last;
            // The frame delta is clamped so a stall advances the world a little
            // rather than replaying hundreds of ticks; see SimulationScheduler.
            double dt = Math.min(0.1, rawDt);
            last = now;
            totalTime = now;
            frameProfiler.record(rawDt);
            // Only ever active inside a smoke run; the harness owns the guard.
            qa.recordFortressApproach(rawDt, now);

            window.poll();
            qa.updateShowcases(automation.elapsed(now));
            frame(dt);
            qa.sampleRenderStats();
            automation.captureDueScreenshots(now);
            if (pendingScreenshot) {
                pendingScreenshot = false;
                com.veylon.gfx.ScreenshotUtil.capture(
                        window.framebufferWidth(), window.framebufferHeight(), null);
            }
            window.swap();
            input.endFrame();
            if (frontend.loadingFramePresented() && !qa.staticLoadingQa()) {
                frontend.completeLoading();
            }
            countFrame(rawDt);
            automation.advanceSmokeRun(now);
        }
        renderer.delete();
        ui.delete();
        audio.shutdown();
        window.pollGlErrors("resource-delete");
        automation.assertSmokeGatePassed();
        window.destroy();
    }

    /** Rolling one-second FPS and average frame time for the HUD and debug overlay. */
    private void countFrame(double rawDt) {
        fpsCounter++;
        fpsTimer += rawDt;
        if (fpsTimer >= 1.0) {
            fps = (int) Math.round(fpsCounter / fpsTimer);
            frameMs = fpsTimer * 1000.0 / Math.max(1, fpsCounter);
            fpsCounter = 0;
            fpsTimer = 0;
        }
    }

    // ------------------------------------------------------------------
    // World setup
    // ------------------------------------------------------------------

    public void newWorld(long seed, boolean fresh) {
        newWorld(seed, fresh, World.CURRENT_GENERATOR);
    }

    /**
     * The single path that produces a playable world, used by New Game, by save
     * loading and by the QA harness. See {@link WorldBootstrap} for the reset
     * ordering it guarantees and the tests that pin it.
     */
    public void newWorld(long seed, boolean fresh, int generatorVersion) {
        bootstrap.newWorld(seed, fresh, generatorVersion);
    }

    void releaseWorldMeshes() {
        if (world == null) {
            return;
        }
        for (Chunk c : world.loadedChunks()) {
            c.deleteMeshes();
        }
    }

    // ------------------------------------------------------------------
    // Frame
    // ------------------------------------------------------------------

    private void frame(double dtD) {
        if (frontend.owns(appState)) {
            frontend.frame((float) dtD);
            return;
        }

        float dt = (float) dtD;
        if (appState == AppState.DEATH) {
            deathTimer -= dt;
            if (input.wasKeyPressed(GLFW_KEY_ESCAPE)) {
                frontend.returnToTitle();
                frontend.frame(dt);
                return;
            }
            if (deathTimer <= 0 || input.wasKeyPressed(GLFW_KEY_ENTER)) {
                respawn();
                appState = AppState.PLAYING;
            }
        } else if (appState == AppState.VICTORY) {
            if (input.wasKeyPressed(GLFW_KEY_ESCAPE)) {
                frontend.returnToTitle();
                frontend.frame(dt);
                return;
            }
            if (input.wasKeyPressed(GLFW_KEY_ENTER)) {
                appState = AppState.PLAYING;
            }
        } else {
            hotkeys.update();
        }
        window.captureCursor(appState == AppState.PLAYING && uiMode == UiMode.NONE, input);

        boolean simulate = appState == AppState.PLAYING
                && uiMode != UiMode.PAUSE && uiMode != UiMode.OPTIONS && uiMode != UiMode.AUDIO_OPTIONS && !simPaused;

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

        // Camera follows the player eye.
        camera.position.set(player.pos.x, player.pos.y + player.eyeHeight(), player.pos.z);
        player.yaw = camera.yaw;
        qa.updateMiningShowcase(dt);
        audio.setListener(camera.position.x, camera.position.y, camera.position.z, camera.yaw);
        audio.update(dt);

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
            case OPTIONS -> frontend.handlePauseOptions(graphicsOptionsScreen.update(this));
            case AUDIO_OPTIONS -> frontend.handlePauseAudio(audioOptionsScreen.update(this));
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

    void beginUiFrame() {
        ui.begin(window.framebufferWidth(), window.framebufferHeight(), renderer.settings.uiScale);
        input.setCursorScale(window.cursorToFramebufferScaleX() / ui.uiScale(),
                window.cursorToFramebufferScaleY() / ui.uiScale());
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
        environment.mediumTick(dt);
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
        environment.slowTick();
    }

    @Override
    public void onBlockChanged(int x, int y, int z, BlockType oldType, BlockType newType) {
        water.notifyBlockChanged(world, x, y, z);
    }

    /** Crafting stations within working distance of the player. */
    public Set<Station> nearbyStations() {
        return environment.nearbyStations();
    }

    public void log(String msg) {
        eventLog.add(String.format("[D%d %02d:%02d] %s", time.day(), time.hour(), time.minute(), msg));
    }
}
