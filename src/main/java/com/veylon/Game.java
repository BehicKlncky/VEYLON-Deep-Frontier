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
import com.veylon.ui.Hud;
import com.veylon.ui.InventoryScreen;
import com.veylon.ui.MapScreen;
import com.veylon.ui.NpcScreen;
import com.veylon.ui.PauseMenu;
import com.veylon.ui.SimulationPanel;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.Poi;
import com.veylon.world.RackBatch;
import com.veylon.world.Raycaster;
import com.veylon.world.World;
import org.joml.Vector3f;

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;

/** Central orchestrator: game loop, input handling, player actions, tick wiring. */
public class Game implements SimulationScheduler.Ticks, World.BlockListener {

    public enum UiMode {
        NONE, INVENTORY, CRAFTING, PAUSE, MAP, CRATE, NPC
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

    public UiMode uiMode = UiMode.NONE;
    public boolean debugShown;
    public boolean simPanelShown;
    public boolean simPaused;

    // Per-frame state.
    public Raycaster.Hit targetHit;
    public float miningProgress;
    private Vec3i miningTarget;
    public String interactPrompt;
    public double totalTime;
    public int fps;
    public double frameMs;
    private int fpsCounter;
    private double fpsTimer;
    private float attackCooldown;

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
        window.create("VEYLON: Deep Frontier", input);
        renderer.init();
        ui.init();
        audio.init();
        newWorld(new Random().nextLong(), true);
        window.captureCursor(true, input);

        boolean smoke = System.getenv("VEYLON_SMOKE") != null;
        double smokeSeconds = 6;
        if (smoke) {
            try {
                smokeSeconds = Math.max(6, Double.parseDouble(System.getenv("VEYLON_SMOKE")));
            } catch (NumberFormatException ignored) {
            }
        }
        int smokePhase = 0;
        double start = glfwGetTime();
        double last = start;
        while (!window.shouldClose()) {
            double now = glfwGetTime();
            double dt = Math.min(0.1, now - last);
            last = now;
            totalTime = now;

            window.poll();
            frame(dt);
            window.swap();
            input.endFrame();

            fpsCounter++;
            fpsTimer += dt;
            if (fpsTimer >= 1.0) {
                fps = fpsCounter;
                frameMs = 1000.0 / Math.max(1, fpsCounter);
                fpsCounter = 0;
                fpsTimer = 0;
            }
            if (smoke) {
                if (smokePhase == 0 && now - start > 2.5) {
                    smokePhase = 1;
                    world.setBlock((int) player.pos.x + 2, (int) player.pos.y + 1,
                            (int) player.pos.z + 2, BlockType.TORCH, true);
                    player.inventory.add(ItemType.HIDE_COAT, 1);
                    equipFromInventoryFirst(ItemType.HIDE_COAT);
                    System.out.println("[smoke] save=" + SaveSystem.save(this));
                }
                if (smokePhase == 1 && now - start > 4.0) {
                    smokePhase = 2;
                    System.out.println("[smoke] load=" + SaveSystem.load(this));
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
                    System.out.println("[smoke] Veylon ran " + (int) smokeSeconds + "s; chunks="
                            + world.loadedCount() + " creatures=" + entities.creatureCount()
                            + " npcs=" + entities.npcCount()
                            + " changedBlocks=" + world.changedBlocks.size()
                            + " fires=" + fire.count() + " events=" + events.summary()
                            + " growth=" + plants.growthEvents
                            + " pois=" + world.pois.size()
                            + " tracks=" + entities.tracks.size()
                            + " particles=" + particles.count
                            + " equipped=" + (player.equipped(EquipSlot.TORSO) != null)
                            + " fps=" + fps);
                    window.requestClose();
                }
            }
        }
        renderer.delete();
        ui.delete();
        audio.shutdown();
        window.destroy();
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
        world = new World(seed);
        world.listener = this;
        player = new Player(world);
        entities.creatures.clear();
        entities.npcs.clear();
        entities.carcasses.clear();
        entities.tracks.clear();
        events.active.clear();
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
        float dt = (float) dtD;
        handleGlobalKeys();
        window.captureCursor(uiMode == UiMode.NONE, input);

        boolean simulate = uiMode != UiMode.PAUSE && !simPaused;

        swingTimer = Math.max(0, swingTimer - dt);
        hitSoundTimer = Math.max(0, hitSoundTimer - dt);

        if (sleeping && simulate) {
            tickSleep(dt);
        } else if (!sleeping) {
            sleepFade = Math.max(0, sleepFade - dt * 1.2f);
        }

        if (uiMode == UiMode.NONE && simulate && !sleeping) {
            updateMouseLook();
            updateMovement(dt);
            updateActions(dt);
        } else {
            miningProgress = 0;
            interactPrompt = null;
            if (uiMode == UiMode.NONE && !sleeping) {
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
        audio.setListener(camera.position.x, camera.position.y, camera.position.z, camera.yaw);

        if (player.dead) {
            respawn();
        }

        // Stream chunks and rebuild meshes near the player.
        world.ensureChunks((int) player.pos.x, (int) player.pos.z, Renderer.RENDER_RADIUS, 2);
        int pcx = Math.floorDiv((int) player.pos.x, 16);
        int pcz = Math.floorDiv((int) player.pos.z, 16);
        renderer.buildDirtyMeshes(world, pcx, pcz, 3);

        // Render world + UI.
        renderer.render(this);
        ui.begin(window.width(), window.height());
        hud.render(this);
        switch (uiMode) {
            case INVENTORY -> inventoryScreen.update(this);
            case CRAFTING -> craftingScreen.update(this);
            case CRATE -> crateScreen.update(this);
            case NPC -> npcScreen.update(this);
            case MAP -> mapScreen.update(this);
            case PAUSE -> pauseMenu.update(this);
            case NONE -> {
            }
        }
        if (debugShown) {
            debugOverlay.render(this);
        }
        if (simPanelShown) {
            simulationPanel.render(this);
        }
        ui.end();
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
        // Fire smoke and embers (burning blocks and fueled campfires).
        for (Vec3i p : fire.burningCells()) {
            if (p.distSq(px, py, pz) < 40 * 40) {
                particles.smoke(p.x() + 0.5f, p.y() + 1f, p.z() + 0.5f, 1f);
                if (rng.nextFloat() < 0.5f) {
                    particles.ember(p.x() + 0.5f, p.y() + 0.6f, p.z() + 0.5f);
                }
            }
        }
        for (Vec3i p : world.campfireFuel.keySet()) {
            if (p.distSq(px, py, pz) < 35 * 35
                    && world.getBlock(p.x(), p.y(), p.z()) == BlockType.CAMPFIRE) {
                particles.smoke(p.x() + 0.5f, p.y() + 0.7f, p.z() + 0.5f, 0.6f);
                if (rng.nextFloat() < 0.35f) {
                    particles.ember(p.x() + 0.5f, p.y() + 0.4f, p.z() + 0.5f);
                }
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
                    && Math.max(Math.abs(c.cx - pcx), Math.abs(c.cz - pcz)) > Renderer.RENDER_RADIUS + 3) {
                c.deleteMeshes();
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
