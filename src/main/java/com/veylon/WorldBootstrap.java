package com.veylon;

import com.veylon.entity.Npc;
import com.veylon.entity.Player;
import com.veylon.item.Inventory;
import com.veylon.item.ItemType;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.World;

import java.util.Random;

/**
 * Builds a playable world: reset, reseed, construct, place the player, raise
 * the starter camp.
 *
 * <p>This is the single path that produces a world, used by New Game, by save
 * loading and by the QA harness. Its ordering is a contract rather than an
 * implementation detail, so it is worth stating plainly:
 *
 * <ol>
 *   <li>release the outgoing world's GPU meshes;</li>
 *   <li>reset every system that outlives a world;</li>
 *   <li>reseed every generator that affects outcomes;</li>
 *   <li>construct {@link World} and {@link Player};</li>
 *   <li>clear entities, log, faction standing and player action state;</li>
 *   <li>generate around spawn, find dry land, raise the camp.</li>
 * </ol>
 *
 * <p>Steps 2 through 5 are pinned by
 * {@code GameLoopIntegrationTest.newWorldClearsSimulationQueuesAndPlayerActionState}
 * and {@code SimulationSystemContractTest}. Adding a system with cross-world
 * state means adding its {@code reset()} here <em>and</em> an assertion there.
 */
final class WorldBootstrap {

    /** Blocks from spawn searched outward for dry land, in steps of four. */
    private static final int DRY_LAND_SEARCH_RADIUS = 48;
    private static final int DRY_LAND_SEARCH_STEP = 4;
    /** Attempts to find camp ground that is above water and below the ceiling. */
    private static final int CAMP_PLACEMENT_ATTEMPTS = 10;
    /** Seconds of fuel the starter campfire begins with. */
    private static final float CAMP_STARTING_FUEL = 600f;
    /** Slow ticks run at world creation so wildlife exists before the first frame. */
    private static final int INITIAL_WILDLIFE_TICKS = 8;

    private final Game game;

    WorldBootstrap(Game game) {
        this.game = game;
    }

    void newWorld(long seed, boolean fresh, int generatorVersion) {
        resetForNewWorld(seed);
        game.world = new World(seed, generatorVersion);
        game.world.listener = game;
        game.player = new Player(game.world);
        clearPerWorldState();

        placePlayerOnDryLand();
        setupCamp(fresh);
        if (fresh) {
            grantStartingKit();
        }
    }

    /**
     * Releases the outgoing world and resets everything that outlives one.
     *
     * <p>Save-load and front-end transitions can replace a live world, so this
     * runs before the new one is constructed rather than after.
     */
    private void resetForNewWorld(long seed) {
        game.releaseWorldMeshes();
        game.scheduler.reset();
        game.audio.resetWorld();
        game.time.reset();
        game.weather.reset();
        game.temperature.reset();
        game.fire.reset();
        game.water.reset();
        game.events.reset();
        game.plants.reset();
        game.itemConditions.reset();
        game.noise.reset();
        game.projectiles.reset();
        game.explosions.reset();
        game.settlementManager.reset();
        game.particles.count = 0;
        reseedSimulation(seed);
    }

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
     * <p>This covers the player-outcome rolls too. Bleeding, sprains,
     * infection, food and water poisoning, sleep sickness, toxic fog and break
     * drops all used {@code Math.random()} through 0.4.1, which made the
     * survival layer's entire failure model the one part of the game a seed did
     * not reproduce. The {@link Player} seeds itself from the world it is
     * constructed for, just after this runs.
     *
     * <p>{@code WorldSeedDeterminismTest} enforces this reflectively: any
     * {@link Random} field on a system reachable from {@code Game} must agree
     * across two worlds built from one seed. That is why these are fields
     * rather than calls into a shared static.
     *
     * <p>Tests that need an exact sequence still call {@code setRandomSeed}
     * directly after {@code newWorld}, and those explicit seeds win.
     *
     * @see com.veylon.entity.PlayerConstants#AFFLICTION_RNG_SALT
     */
    private void reseedSimulation(long seed) {
        game.particles.setRandomSeed(seed ^ 0x5645594c4f4eL);
        game.ambience.reseed(seed ^ 0x46584c4f4eL);
        game.entities.setRandomSeed(seed ^ 0x454e5449545933L);
        game.entities.setAiRandomSeed(seed);
        game.projectiles.setRandomSeed(seed ^ 0x50524f4a4543L);
        game.explosions.setRandomSeed(seed ^ 0x4558504c4f53L);
        game.settlementManager.setRandomSeed(seed ^ 0x534554544c4dL);
        game.faction.setRandomSeed(seed ^ 0x464143544e53L);
        game.weather.setRandomSeed(seed ^ 0x574541544852L);
        game.water.setRandomSeed(seed ^ 0x5741544552L);
        game.fire.setRandomSeed(seed ^ 0x4649524553L);
        game.plants.setRandomSeed(seed ^ 0x504c414e5453L);
        game.events.setRandomSeed(seed ^ 0x4556454e5453L);
        // Player-outcome rolls. These collaborators outlive a world, so they
        // need the same explicit reseed the simulation systems get.
        game.consumables.setRandomSeed(seed ^ 0x434f4e53554dL);
        game.interactions.setRandomSeed(seed ^ 0x494e54455241L);
        game.blockActions.setRandomSeed(seed ^ 0x424c4f434b41L);
        game.sleep.setRandomSeed(seed ^ 0x534c454550L);
    }

    /** Entities, log, faction standing and every in-progress player action. */
    private void clearPerWorldState() {
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        game.entities.carcasses.clear();
        game.entities.tracks.clear();
        game.eventLog.clear();
        game.faction.trust = 35;
        game.faction.alert = 0;
        game.faction.foodStock = 10;
        game.faction.woodStock = 8;
        game.faction.hostile = false;
        game.faction.upgradeStage = 0;
        game.faction.resetQuestRuntime();
        game.faction.alliedGiftGiven = false;
        game.sleeping = false;
        game.sleepFade = 0;
        game.simPaused = false;
        game.simPanelShown = false;
        game.uiMode = Game.UiMode.NONE;
        game.targetHit = null;
        game.miningTarget = null;
        game.miningProgress = 0;
        game.swingTimer = 0;
        game.walkBob = 0;
        game.drawingBow = false;
        game.bowDraw = 0;
        game.combat.reset();
        game.crates.reset();
    }

    /** Generates around spawn and walks outward until the crash site is not a lake. */
    private void placePlayerOnDryLand() {
        World world = game.world;
        world.ensureChunks(8, 8, 5, 10_000);
        int sx = 8;
        int sz = 8;
        outer:
        for (int r = 0; r <= DRY_LAND_SEARCH_RADIUS; r += DRY_LAND_SEARCH_STEP) {
            for (int dx = -r; dx <= r; dx += DRY_LAND_SEARCH_STEP) {
                for (int dz = -r; dz <= r; dz += DRY_LAND_SEARCH_STEP) {
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
        game.spawnPos.set(sx + 0.5f, sy + 0.2f, sz + 0.5f);
        game.player.pos.set(game.spawnPos);
        game.camera.yaw = 35;
        game.camera.pitch = 8;
    }

    /**
     * Deterministically builds the NPC camp near spawn. Runs for both new games
     * and loads; a load then overwrites crate contents and fuel from the save,
     * which is why the containers here are filled with {@code putIfAbsent}.
     */
    private void setupCamp(boolean fresh) {
        World world = game.world;
        Random rng = new Random(world.seed * 31 + 7);
        int baseX = (int) game.spawnPos.x;
        int baseZ = (int) game.spawnPos.z;
        int cx = baseX;
        int cz = baseZ;
        int h = 0;
        for (int attempt = 0; attempt < CAMP_PLACEMENT_ATTEMPTS; attempt++) {
            cx = baseX + 28 + rng.nextInt(14) + attempt * 8;
            cz = baseZ + 22 + rng.nextInt(14);
            world.ensureChunks(cx, cz, 3, 10_000);
            h = world.surfaceHeight(cx, cz);
            if (h > World.SEA_LEVEL + 1 && h < Chunk.SY - 14) {
                break;
            }
        }
        flattenCampPad(world, cx, cz, h);

        Vec3i campPos = new Vec3i(cx, h + 1, cz);
        world.campPos = campPos;
        game.faction.campPos = campPos;
        raiseCampStructures(world, cx, cz, h, campPos);
        fillCampCrates(world, cx, cz, h);

        if (fresh) {
            String[] names = {"Maro", "Senna", "Korrin", "Della"};
            for (int i = 0; i < names.length; i++) {
                Npc n = game.entities.spawnNpc(world, names[i],
                        cx + 1.5f + (i % 2) * 2 - 2, h + 1.2f, cz + 1.5f + (i / 2) * 2 - 2);
                n.faction = game.faction;
                n.campIndex = i;
            }
        }
    }

    /** Clears headroom and fills holes so the camp sits on a level 9x9 pad. */
    private static void flattenCampPad(World world, int cx, int cz, int h) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int x = cx + dx;
                int z = cz + dz;
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
    }

    private static void raiseCampStructures(World world, int cx, int cz, int h, Vec3i campPos) {
        world.setBlock(cx, h + 1, cz, BlockType.CAMPFIRE, false);
        world.campfireFuel.putIfAbsent(campPos, CAMP_STARTING_FUEL);
        world.setBlock(cx - 2, h + 1, cz - 2, BlockType.CRATE, false);
        world.setBlock(cx + 2, h + 1, cz + 2, BlockType.CRATE, false);
        world.setBlock(cx + 2, h + 1, cz - 2, BlockType.WORKBENCH, false);
        world.setBlock(cx - 3, h + 1, cz + 3, BlockType.TORCH, false);
        world.setBlock(cx + 3, h + 1, cz - 3, BlockType.TORCH, false);
        for (int dx = -3; dx <= -1; dx++) {
            world.setBlock(cx + dx, h + 1, cz - 4, BlockType.WALL, false);
        }
    }

    private static void fillCampCrates(World world, int cx, int cz, int h) {
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
    }

    private void grantStartingKit() {
        game.player.inventory.add(ItemType.BERRY, 4);
        game.player.inventory.add(ItemType.LOG, 2);
        game.player.inventory.add(ItemType.STICK, 2);
        game.player.inventory.add(ItemType.FIBER, 4);
        game.player.inventory.add(ItemType.TORCH, 2);
        game.player.inventory.add(ItemType.BANDAGE, 1);
        game.player.inventory.add(ItemType.WATERSKIN_EMPTY, 1);
        game.log("You crash-landed on Veylon. Survive.");
        game.log("Gather wood and berries; craft tools with [C]. Watch your wounds.");
        game.log("An NPC camp lies somewhere nearby - and stranger things besides...");
        // Populate the world with wildlife before the player sees the first frame.
        for (int i = 0; i < INITIAL_WILDLIFE_TICKS; i++) {
            game.entities.slowTick(game);
        }
    }
}
