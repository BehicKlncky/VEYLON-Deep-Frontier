package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.item.ItemType;
import com.veylon.qa.RuntimeBudgetSnapshot;
import com.veylon.save.SaveSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.FixtureStates;
import com.veylon.world.World;
import com.veylon.world.urban.AccessibilityValidator;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.EncounterAnchors;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.UrbanStarterSite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The milestone-11 slice gate on generated starter neighbourhoods: the {@code urban_slice}
 * route plays the scavenging, risk and return loop through the production input seam and the
 * frame step, quick-saves and quick-loads over the live world and revisits. These assertions are
 * independent of the route's own checks.
 */
class UrbanSliceRouteTest {
    /** The frozen slice regression seeds: the urban design set (VALIDATION §6). */
    static final long[] SLICE_SEEDS = UrbanStarterProgressionTest.SEEDS;
    @TempDir Path directory;

    /**
     * The development selector now creates generator 11, whose starter superblock is generator
     * 10's but whose specialist buildings add guards nearby (D-102). The loop must still hold.
     */
    @Test
    void theSliceLoopStillCompletesOnTheCivicCatalogueGenerator() {
        for (long seed : SLICE_SEEDS) {
            UrbanSliceRun run = new UrbanSliceRun(seed, GameMode.SURVIVAL, World.GEN_URBAN_CIVIC).play();
            assertTrue(run.slice().passed(), "seed=" + seed);
            assertEquals(World.GEN_URBAN_CIVIC, run.game.world.generatorVersion);
            assertTrue(run.game.player.inventory.count(ItemType.IRON_SPEAR) >= 1, "the first upgrade seed=" + seed);
        }
    }

    /**
     * Generator 12 (milestone 13) keeps that starter superblock under six districts: district
     * overlays stay out of it and its neighbours are residential or commercial by construction.
     * The loop must still hold, and the residential starter region now reports its own climate.
     */
    @Test
    void theSliceLoopStillCompletesOnTheSixDistrictGenerator() {
        for (long seed : SLICE_SEEDS) {
            UrbanSliceRun run = new UrbanSliceRun(seed, GameMode.SURVIVAL, World.GEN_URBAN_DISTRICTS).play();
            assertTrue(run.slice().passed(), "seed=" + seed);
            assertEquals(World.GEN_URBAN_DISTRICTS, run.game.world.generatorVersion);
            assertTrue(run.game.player.inventory.count(ItemType.IRON_SPEAR) >= 1, "the first upgrade seed=" + seed);
            assertSame(com.veylon.world.Biome.MEADOW, run.game.player.biome, "reclaimed residential climate");
        }
    }

    /**
     * Generator 13 (milestone 14) keeps that city and starter superblock exactly and adds
     * transit under its avenues, a station on the spawn region's west edge, and routes between
     * the other buildings. The development selector now creates it; the loop must still hold.
     */
    @Test
    void theSliceLoopStillCompletesOnTheUndergroundGenerator() {
        for (long seed : SLICE_SEEDS) {
            UrbanSliceRun run = new UrbanSliceRun(seed, GameMode.SURVIVAL, World.GEN_URBAN_UNDERGROUND).play();
            assertTrue(run.slice().passed(), "seed=" + seed);
            assertEquals(World.GEN_URBAN_UNDERGROUND, run.game.world.generatorVersion);
            assertTrue(run.game.player.inventory.count(ItemType.IRON_SPEAR) >= 1, "the first upgrade seed=" + seed);
        }
    }

    /**
     * Generator 14 (milestone 15) keeps generator 13's city and adds growth in the quarantine,
     * wildlife outside it and Breachers at some street doors; the starter superblock holds none of
     * them. The development selector now creates it; the loop must still hold.
     */
    @Test
    void theSliceLoopStillCompletesOnTheEcologyGenerator() {
        for (long seed : SLICE_SEEDS) {
            UrbanSliceRun run = new UrbanSliceRun(seed, GameMode.SURVIVAL, World.GEN_URBAN_ECOLOGY).play();
            assertTrue(run.slice().passed(), "seed=" + seed);
            assertEquals(World.GEN_URBAN_ECOLOGY, run.game.world.generatorVersion);
            assertTrue(run.game.player.inventory.count(ItemType.IRON_SPEAR) >= 1, "the first upgrade seed=" + seed);
        }
    }

    /**
     * Generator 15 (milestone 16) keeps generator 14's city and brings survivor camps and hidden
     * civilians to life outside the starter superblock; a camp may stand in a neighbouring block,
     * so its people are on screen during the loop. The loop must still hold.
     */
    @Test
    void theSliceLoopStillCompletesOnTheSurvivorGenerator() {
        for (long seed : SLICE_SEEDS) {
            UrbanSliceRun run = new UrbanSliceRun(seed, GameMode.SURVIVAL, World.GEN_URBAN_SURVIVORS).play();
            assertTrue(run.slice().passed(), "seed=" + seed);
            assertEquals(World.GEN_URBAN_SURVIVORS, run.game.world.generatorVersion);
            assertTrue(run.game.player.inventory.count(ItemType.IRON_SPEAR) >= 1, "the first upgrade seed=" + seed);
        }
    }

    /**
     * Generator 16 (milestone 17) keeps generator 15's city and holds guarded compounds in the
     * civic and industrial regions, never within a region of the spawn. The development selector
     * now creates it; the loop must still hold.
     */
    @Test
    void theSliceLoopStillCompletesOnTheStrongholdGenerator() {
        for (long seed : SLICE_SEEDS) {
            UrbanSliceRun run = new UrbanSliceRun(seed, GameMode.SURVIVAL, World.GEN_URBAN_STRONGHOLDS).play();
            assertTrue(run.slice().passed(), "seed=" + seed);
            assertEquals(World.GEN_URBAN_STRONGHOLDS, run.game.world.generatorVersion);
            assertTrue(run.game.player.inventory.count(ItemType.IRON_SPEAR) >= 1, "the first upgrade seed=" + seed);
        }
    }

    /**
     * Generator 17 (milestone 18) keeps generator 16's city and leaves burnt-out cars in one lane
     * of the local streets, including those round the starter superblock. The development selector
     * now creates it; the loop must still hold.
     */
    @Test
    void theSliceLoopStillCompletesOnTheRefugeGenerator() {
        for (long seed : SLICE_SEEDS) {
            UrbanSliceRun run = new UrbanSliceRun(seed, GameMode.SURVIVAL, World.GEN_URBAN_REFUGES).play();
            assertTrue(run.slice().passed(), "seed=" + seed);
            assertEquals(World.GEN_URBAN_REFUGES, run.game.world.generatorVersion);
            assertTrue(run.game.player.inventory.count(ItemType.IRON_SPEAR) >= 1, "the first upgrade seed=" + seed);
        }
    }

    @Test
    void frozenSeedsPlayTheScavengeRiskReturnLoopInSurvivalAndCreative() {
        for (GameMode mode : GameMode.values()) {
            for (long seed : SLICE_SEEDS) {
                UrbanSliceRun run = new UrbanSliceRun(seed, mode).play();
                Game g = run.game;
                String context = "seed=" + seed + " mode=" + mode;
                assertTrue(run.slice().passed(), context);
                assertFalse(run.slice().defeated().isEmpty(), "an infected was handled " + context);
                for (long id : run.slice().defeated()) {
                    assertTrue(g.entities.encounters.resident(id).dead(), context);
                    assertTrue(g.entities.npcs.stream().noneMatch(n -> n.encounterId == id && !n.dead), context);
                    assertNull(g.entities.encounters.tryActivate(g, EncounterAnchors.resolve(g.world, id)), context);
                }
                assertTrue(g.player.inventory.count(ItemType.IRON_SPEAR) >= 1, "the first upgrade " + context);
                for (ItemType wild : List.of(ItemType.LOG, ItemType.COAL, ItemType.COPPER_ORE, ItemType.IRON_ORE)) {
                    assertEquals(0, g.player.inventory.count(wild), "no tree or ore " + context);
                }
                Vec3i forced = run.slice().forcedAt();
                assertEquals(mode == GameMode.CREATIVE ? BlockType.AIR : BlockType.BROKEN_DOOR,
                        g.world.getBlock(forced.x(), forced.y(), forced.z()), "the forced door stays changed " + context);
                for (var door : run.slice().plan().streetDoors()) {
                    Vec3i p = door.position();
                    assertEquals(mode == GameMode.CREATIVE ? FixtureStates.LOCK_KEYED : FixtureStates.LOCK_BARRED,
                            g.world.fixtureLockAt(p.x(), p.y(), p.z()), context);
                }
                if (mode == GameMode.CREATIVE) {
                    assertEquals(g.player.maxHealth, g.player.health, context);
                    assertTrue(g.player.afflictions.isEmpty(), context);
                }
                // The plan validator demands as-built fixtures; after forced and opened doors, walk instead.
                BuildingPlan apartment = run.slice().plan().apartment();
                for (BuildingPlan.Floor floor : apartment.floors()) {
                    for (BuildingPlan.Room room : floor.rooms()) {
                        assertNotNull(UrbanSlicePlan.route(g.world, apartment.approaches().getLast(), room.anchor()),
                                "room " + room.id() + " stays reachable after the route " + context);
                    }
                }
                assertTrue(RuntimeBudgetSnapshot.capture(g).withinHardLimits(),
                        RuntimeBudgetSnapshot.capture(g).occupancySummary());
            }
        }
    }

    @Test
    void theSliceNeighbourhoodIsGeneratedConnectedAndReachableOnEveryFrozenSeed() {
        for (long seed : SLICE_SEEDS) {
            Game g = new Game();
            g.newWorld(seed, true, World.GEN_URBAN_ENCOUNTERS, GameMode.SURVIVAL);
            UrbanSlicePlan plan = UrbanSlicePlan.of(g.world);
            var site = UrbanStarterSite.of(g.world.urban);
            String context = "seed=" + seed;
            assertEquals(BuildingPlan.Family.APARTMENT, plan.apartment().family());
            assertTrue(plan.apartment().storeys() >= 3 && plan.apartment().basement(), context);
            assertEquals(BuildingPlan.Family.SHOP, plan.shop().family());
            assertFalse(EncounterAnchors.forParcel(g.world, plan.apartment().parcel()).isEmpty(), context);
            assertEquals(BlockType.CAMPFIRE, g.world.getBlock(site.fire().x(), site.fire().y(), site.fire().z()));
            assertEquals(BlockType.RAIN_COLLECTOR,
                    g.world.getBlock(site.collector().x(), site.collector().y(), site.collector().z()));
            assertTrue(plan.refuge().fixtures().stream().anyMatch(f -> f.block() == BlockType.CAMP_BED));
            assertEquals(BlockType.WORKBENCH, plan.workbench().block());

            // Two separate ways between home and the apartment: across the courtyard, and around
            // the block on streets and sidewalks without entering the superblock at all.
            var superblock = g.world.urban.regionPlan(0, 0).superblock(plan.refuge().parcel().superblock());
            ParcelPlan.Rect block = superblock.bounds(), yard = superblock.open();
            Vec3i refugeFront = plan.refuge().approaches().getFirst(), apartmentFront = plan.apartment().approaches().getFirst();
            assertFalse(block.contains(refugeFront.x(), refugeFront.z()) || block.contains(apartmentFront.x(), apartmentFront.z()));
            List<Vec3i> court = UrbanSlicePlan.route(g.world, plan.refuge().approaches().getLast(),
                    plan.apartment().approaches().getLast());
            List<Vec3i> street = UrbanSlicePlan.route(g.world, refugeFront, apartmentFront,
                    c -> !block.contains(c.x(), c.z()));
            assertNotNull(court, context);
            assertTrue(court.stream().anyMatch(c -> yard.contains(c.x(), c.z())), "courtyard route " + context);
            assertNotNull(street, "a street route that never enters the block " + context);
            assertNotNull(UrbanSlicePlan.route(g.world, plan.refuge().approaches().getLast(),
                    plan.shop().approaches().getLast()), context);

            Vec3i door = plan.refuge().approaches().getLast();
            for (BuildingPlan.Fixture f : List.of(plan.locker(), plan.kitchen(), plan.bathroom(), plan.upstairs(),
                    plan.shelf(), plan.workbench(), plan.store())) {
                List<Vec3i> walk = UrbanSlicePlan.route(g.world, door, f.target());
                assertNotNull(walk, context + " " + f);
                assertTrue(walk.size() <= 96, "starter walking distance " + walk.size() + " " + context);
            }
            for (BuildingPlan b : List.of(plan.refuge(), plan.apartment(), plan.shop())) {
                assertTrue(AccessibilityValidator.inspectGenerated(b, g.world.urban.regionPlan(0, 0), g.world).ok(),
                        "every generated room is reachable " + context + " building=" + b.id());
            }
        }
    }

    @Test
    void theSavedSliceRegeneratesIdenticallyInEveryChunkOrder() {
        for (long seed : new long[]{20260731, 7}) {
            UrbanSliceRun run = new UrbanSliceRun(seed, GameMode.SURVIVAL).play();
            Path save = directory.resolve("slice-" + seed + ".sav");
            assertTrue(SaveSystem.save(run.game, save));
            Game loaded = new Game();
            assertTrue(SaveSystem.load(loaded, save));
            UrbanSlicePlan plan = UrbanSlicePlan.of(loaded.world);
            ParcelPlan.Rect block = loaded.world.urban.regionPlan(0, 0).superblock(plan.refuge().parcel().superblock()).bounds();
            List<int[]> chunks = new ArrayList<>();
            for (int cx = Math.floorDiv(block.x0() - 1, Chunk.SX); cx <= Math.floorDiv(block.x1() + 1, Chunk.SX); cx++) {
                for (int cz = Math.floorDiv(block.z0() - 1, Chunk.SZ); cz <= Math.floorDiv(block.z1() + 1, Chunk.SZ); cz++) {
                    chunks.add(new int[]{cx, cz});
                    loaded.world.getOrCreateChunk(cx, cz);
                }
            }
            for (List<int[]> order : orders(chunks, seed)) {
                World regenerated = new World(seed, loaded.world.generatorVersion);
                regenerated.changedBlocks.putAll(loaded.world.changedBlocks);
                loaded.world.fixtures.forEach(regenerated::restoreFixtureState);
                for (int[] c : order) { regenerated.getOrCreateChunk(c[0], c[1]); }
                for (int[] c : chunks) {
                    Chunk a = loaded.world.getChunk(c[0], c[1]), b = regenerated.getChunk(c[0], c[1]);
                    for (int y = 0; y < Chunk.SY; y++) {
                        for (int z = 0; z < Chunk.SZ; z++) {
                            for (int x = 0; x < Chunk.SX; x++) {
                                assertSame(a.get(x, y, z), b.get(x, y, z), "seed=" + seed + " chunk=" + c[0] + "," + c[1]);
                            }
                        }
                    }
                    assertArrayEquals(a.heightMap, b.heightMap);
                }
                Vec3i forced = run.slice().forcedAt();
                assertEquals(BlockType.BROKEN_DOOR, regenerated.getBlock(forced.x(), forced.y(), forced.z()));
                for (var door : plan.streetDoors()) {
                    Vec3i p = door.position();
                    assertEquals(FixtureStates.LOCK_BARRED, regenerated.fixtureLockAt(p.x(), p.y(), p.z()));
                }
                assertTrue(regenerated.crateContents.isEmpty(), "regeneration rolls and refills nothing");
            }
        }
    }

    /** Reverse row-major, column-major, a fixed shuffle and a spiral from the far corner. */
    private static List<List<int[]>> orders(List<int[]> rowMajor, long seed) {
        List<int[]> reverse = new ArrayList<>(rowMajor);
        Collections.reverse(reverse);
        List<int[]> columns = new ArrayList<>(rowMajor);
        columns.sort(Comparator.<int[]>comparingInt(c -> c[1]).thenComparingInt(c -> c[0]));
        List<int[]> shuffled = new ArrayList<>(rowMajor);
        Collections.shuffle(shuffled, new Random(seed));
        int[] far = rowMajor.getLast();
        List<int[]> spiral = new ArrayList<>(rowMajor);
        spiral.sort(Comparator.comparingInt(c -> Math.max(Math.abs(c[0] - far[0]), Math.abs(c[1] - far[1]))));
        return List.of(rowMajor, reverse, columns, shuffled, spiral);
    }
}
