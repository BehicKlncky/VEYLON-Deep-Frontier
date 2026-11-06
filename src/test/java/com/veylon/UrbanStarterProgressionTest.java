package com.veylon;

import com.veylon.entity.Affliction;
import com.veylon.entity.GameMode;
import com.veylon.entity.PlayerMovementSystem;
import com.veylon.item.*;
import com.veylon.save.SaveSystem;
import com.veylon.simulation.ShelterSystem;
import com.veylon.simulation.WeatherSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.*;
import com.veylon.world.urban.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Continuous production movement, searches, transfers and survival, with no resource injection. */
class UrbanStarterProgressionTest {
    static final long[] SEEDS = {20260731, -4242, 7, 0, 987654321, -9000000000000000000L};
    @TempDir Path directory;

    @Test
    void fixedSeedsCompleteTheScavengeReturnRouteWithoutTreesOrOre() {
        for (long seed : SEEDS) {
            Game g = session(seed, GameMode.SURVIVAL);
            UrbanStarterSite site = UrbanStarterSite.of(g.world.urban);
            Vec3i home = g.world.urban.refugeAnchor;
            assertTrue(UrbanBootstrap.safe(g.world, home));
            assertTrue(ShelterSystem.evaluate(g.world, g.player.pos.x, g.player.pos.y, g.player.pos.z).indoor());
            assertEquals(0, g.player.inventory.count(ItemType.LOG));
            assertNull(g.world.campPos);
            assertTrue(g.entities.npcs.isEmpty(), "provisional refuge is unattended until 16");

            // Spending the modest kit cannot consume the room reservations.
            g.player.hunger = 50;
            use(g, ItemType.DRIED_BERRY);
            g.player.thirst = 30;
            use(g, ItemType.WATERSKIN_CLEAN);
            assertTrue(g.player.thirst > 30);
            assertTrue(g.player.inventory.has(ItemType.WATERSKIN_EMPTY, 1));

            var buildings = g.world.urban.regionPlan(0, 0).parcels().stream()
                    .filter(UrbanStarterSite::isNeighbour)
                    .map(p -> g.world.urban.buildingAt(p.x0(), p.z0())).toList();
            assertTrue(buildings.stream().filter(b -> b.family() == BuildingPlan.Family.APARTMENT).count() >= 3);
            assertTrue(buildings.stream().anyMatch(b -> b.basement() && b.storeys() >= 3));
            BuildingPlan apartment = buildings.stream().filter(b -> b.id() != site.refuge().id()
                    && b.family() == BuildingPlan.Family.APARTMENT).findFirst().orElseThrow();
            BuildingPlan shop = buildings.stream().filter(b -> b.family() == BuildingPlan.Family.SHOP).findFirst().orElseThrow();
            int food = 0, foodContainers = 0, water = 0;
            for (BuildingPlan building : List.of(apartment, shop)) {
                for (var fixture : building.fixtures()) {
                    if (fixture.kind() != BuildingPlan.FixtureKind.CONTAINER || fixture.position().y() != 41) { continue; }
                    int[] door = g.world.urban.spawnColumn();
                    int distance = route(g.world, new Vec3i(door[0], 41, door[1]), fixture.target()).size() - 1;
                    assertTrue(distance <= 96, "starter walking distance=" + distance + ", seed=" + seed + " fixture=" + fixture);
                    walk(g, fixture.target());
                    assertTrue(g.interactWithBlockAt(fixture.position()));
                    UrbanLootGameplayTest.simulate(g, 4);
                    assertFalse(g.crateSearching());
                    if (g.openCrate != null) {
                        int servings = g.openCrate.count(ItemType.DRIED_BERRY) + g.openCrate.count(ItemType.DRIED_MEAT);
                        food += servings; if (servings > 0) { foodContainers++; }
                        water += g.openCrate.count(ItemType.WATERSKIN_CLEAN);
                        for (int slot = 0; slot < 12; slot++) { g.transferCrateItemToPlayer(slot); }
                    }
                    g.closeScreens();
                }
            }
            assertTrue(food >= 6 && foodContainers >= 3, "distributed meals");
            assertTrue(water >= 3, "distributed drinking water");
            assertTrue(g.player.inventory.has(ItemType.IRON_PICKAXE, 1), "unlocked pry-class tool");
            assertTrue(g.player.inventory.has(ItemType.BANDAGE, 2));
            assertTrue(g.player.inventory.has(ItemType.ANTISEPTIC, 1));

            walk(g, home);
            g.player.hurtPhysical(g, 4, true);
            g.player.addAffliction(Affliction.BLEEDING, 120);
            use(g, ItemType.BANDAGE);
            assertFalse(g.player.has(Affliction.BLEEDING));
            g.player.addAffliction(Affliction.INFECTION, 120);
            use(g, ItemType.ANTISEPTIC);
            assertFalse(g.player.has(Affliction.INFECTION));

            var bench = fixture(site.refuge(), BlockType.WORKBENCH);
            walk(g, bench.target());
            Recipe upgrade = UrbanCrafting.RECIPES.getFirst();
            assertEquals("Reinforced Spear", CraftingSystem.craft(g.player.inventory, upgrade,
                    g.nearbyStations(), g.player.blueprints));
            assertTrue(g.player.inventory.has(ItemType.IRON_SPEAR, 1));

            var bed = fixture(site.refuge(), BlockType.CAMP_BED);
            walk(g, bed.target()); g.player.fatigue = 60;
            assertTrue(g.interactWithBlockAt(bed.position())); assertTrue(g.sleeping);
            for (int tick = 0; tick < 100 && g.sleeping; tick++) { g.sleep.tickSleep(.1f); }
            assertFalse(g.sleeping); assertTrue(g.player.fatigue < 60, "rest reduces fatigue");
            for (byte block : g.world.changedBlocks.values()) {
                assertTrue(Set.of(BlockType.SAPLING, BlockType.TALL_GRASS, BlockType.HERB_PLANT,
                        BlockType.GRASS, BlockType.BERRY_BUSH).contains(BlockType.values()[Byte.toUnsignedInt(block)]),
                        "only live ecology changes blocks during this route");
            }
            assertEquals(0, g.player.inventory.count(ItemType.LOG));
            assertEquals(0, g.player.inventory.count(ItemType.IRON_ORE));
            Path save = directory.resolve("route-" + seed + ".sav");
            assertTrue(SaveSystem.save(g, save)); assertTrue(SaveSystem.load(g, save));
            assertEquals(home, g.world.urban.refugeAnchor);
            g.player.dead = true; g.respawn();
            assertFalse(g.player.dead); assertEquals(home.x() + .5f, g.player.pos.x);
            assertTrue(g.player.inventory.has(ItemType.IRON_SPEAR, 1), "legacy retained-inventory respawn");
        }
    }

    @Test
    void startingKitAndRefugeAreIdenticalAcrossSurvivalAndCreative() {
        Game survival = session(7, GameMode.SURVIVAL), creative = session(7, GameMode.CREATIVE);
        assertEquals(survival.spawnPos, creative.spawnPos);
        for (int i = 0; i < survival.player.inventory.size(); i++) {
            var a = survival.player.inventory.get(i); var b = creative.player.inventory.get(i);
            assertEquals(a == null ? null : a.type, b == null ? null : b.type);
            assertEquals(a == null ? 0 : a.count, b == null ? 0 : b.count);
        }
        creative.player.hurtPhysical(creative, 1000, true);
        assertFalse(creative.player.dead); assertTrue(creative.player.abilities.invulnerable());
        assertSame(CraftingSystem.RECIPES, CraftingSystem.recipesFor(new World(7, World.GEN_CAVE_IDENTITIES)));
        assertSame(CraftingSystem.RECIPES, CraftingSystem.recipesFor(new World(7, World.GEN_URBAN_SHAPED_STAIRS)));
        assertSame(UrbanCrafting.RECIPES, CraftingSystem.recipesFor(survival.world));
        for (Recipe recipe : UrbanCrafting.RECIPES) {
            for (ItemType ingredient : recipe.ingredients.keySet()) {
                assertFalse(Set.of(ItemType.LOG, ItemType.COAL, ItemType.COPPER_ORE, ItemType.IRON_ORE).contains(ingredient));
            }
        }
    }

    @Test
    void starterInteriorsKeepRainOutAndRoofedSmokeStillRespectsCreative() {
        for (GameMode mode : GameMode.values()) {
            Game g = session(20260731, mode);
            g.weather.current = g.weather.next = WeatherSystem.Weather.STORM; g.weather.blend = 1;
            new PlayerEnvironmentSystem(g).mediumTick(.5f);
            g.player.tickNeeds(g, 5);
            assertEquals(0f, g.player.wetness);
            assertTrue(g.temperature.envTempAt(g, g.player.pos.x, g.player.pos.y, g.player.pos.z) > 0,
                    "starter climate is survivable on the former cold seed");
            Vec3i home = g.world.urban.refugeAnchor;
            Vec3i source = home.offset(1, 0, 0);
            g.world.setBlock(source.x(), source.y(), source.z(), BlockType.CAMPFIRE, true);
            g.world.campfireFuel.put(source, 60f); g.temperature.mediumTick(g, .5f);
            new PlayerEnvironmentSystem(g).mediumTick(.5f);
            assertEquals(mode == GameMode.SURVIVAL, g.player.smokeExposure > 0);
            g.world.setBlock(source.x(), source.y(), source.z(), BlockType.AIR, true);
            g.temperature.mediumTick(g, .5f); g.player.smokeExposure = 0;
            new PlayerEnvironmentSystem(g).mediumTick(.5f); assertEquals(0f, g.player.smokeExposure);
        }
    }

    @Test
    void starterNativeSceneOnlyObservesNormalInputAndNeverStagesSupplies() {
        Game g = session(7, GameMode.SURVIVAL);
        var scene = new UrbanQaScenes(g); scene.stage("urban_starter_manual");
        g.player.pos.add(1, 0, 1); var pos = new org.joml.Vector3f(g.player.pos);
        g.camera.yaw = 123; g.camera.pitch = 17;
        int items = g.player.inventory.count(ItemType.DRIED_BERRY);
        scene.update(1); scene.update(20);
        assertEquals(pos, g.player.pos); assertEquals(123, g.camera.yaw); assertEquals(17, g.camera.pitch);
        assertEquals(items, g.player.inventory.count(ItemType.DRIED_BERRY));
        assertTrue(g.world.changedBlocks.isEmpty()); assertTrue(g.world.crateContents.isEmpty());
        assertTrue(UrbanApartmentQaScene.manualNavigation("urban_starter_manual"));
    }

    @Test
    void fullInventoriesPreserveRefillsCookingAndReusableDrinkingContainers() {
        Game g = session(7, GameMode.SURVIVAL);
        var site = UrbanStarterSite.of(g.world.urban);
        for (ItemType input : List.of(ItemType.WATERSKIN_EMPTY, ItemType.WATERSKIN_DIRTY,
                ItemType.WATERSKIN_CLEAN, ItemType.RAW_MEAT)) {
            for (int i = 0; i < g.player.inventory.size(); i++) {
                g.player.inventory.set(i, new ItemStack(ItemType.STONE, ItemType.STONE.maxStack));
            }
            g.player.inventory.set(0, new ItemStack(input, 2));
            float water = g.world.collectorWater.get(site.collector()), thirst = g.player.thirst;
            if (input == ItemType.WATERSKIN_EMPTY) { g.interactWithBlockAt(site.collector()); }
            else if (input == ItemType.WATERSKIN_CLEAN) { g.consumables.drink(g.player.inventory.get(0)); }
            else { g.interactWithBlockAt(site.fire()); }
            assertEquals(2, g.player.inventory.count(input));
            assertEquals(water, g.world.collectorWater.get(site.collector())); assertEquals(thirst, g.player.thirst);
        }
    }

    @Test
    void courtyardWaterRenewsAndRecoveredWoodCooksAndLightsWithoutCoal() {
        Game g = session(7, GameMode.SURVIVAL); var site = UrbanStarterSite.of(g.world.urban);
        g.player.inventory.clear(); g.player.inventory.add(ItemType.WATERSKIN_EMPTY, 1);
        g.interactWithBlockAt(site.collector()); assertTrue(g.player.inventory.has(ItemType.WATERSKIN_CLEAN, 1));
        assertEquals(2f, g.world.collectorWater.get(site.collector()));
        g.weather.current = g.weather.next = WeatherSystem.Weather.STORM; g.weather.blend = 1;
        g.itemConditions.slowTick(g, 10); assertTrue(g.world.collectorWater.get(site.collector()) > 2);
        g.player.inventory.clear(); g.player.inventory.add(ItemType.PLANK, 3);
        g.world.campfireFuel.put(site.fire(), 0f); g.interactWithBlockAt(site.fire());
        assertEquals(60f, g.world.campfireFuel.get(site.fire()));
        g.player.inventory.add(ItemType.WATERSKIN_DIRTY, 1); g.interactWithBlockAt(site.fire());
        assertTrue(g.player.inventory.has(ItemType.WATERSKIN_CLEAN, 1));
        g.player.inventory.add(ItemType.RAW_MEAT, 1); g.interactWithBlockAt(site.fire());
        assertTrue(g.player.inventory.has(ItemType.COOKED_MEAT, 1));
        Vec3i lantern = site.fire().offset(2, 0, 0);
        g.world.setBlock(lantern.x(), lantern.y(), lantern.z(), BlockType.LANTERN, true);
        g.player.hotbarSel = 0;
        assertEquals(ItemType.PLANK, g.player.selected().type);
        g.interactWithBlockAt(lantern);
        assertEquals(450f, g.world.lanternState(lantern).fuelSeconds());
        assertTrue(g.eventLog.all().getLast().contains("recovered wood"));
        g.player.hotbarSel = 1; g.interactWithBlockAt(lantern);
        assertTrue(g.world.lanternState(lantern).lit());
    }

    @Test
    void damagedRefugeRespawnUsesLoadedSupportedGroundInsteadOfRoofsOrBasements() {
        Game g = session(7, GameMode.SURVIVAL); Vec3i home = g.world.urban.refugeAnchor;
        g.world.setBlock(home.x(), home.y() - 1, home.z(), BlockType.AIR, true);
        g.player.dead = true; g.respawn();
        assertFalse(g.player.dead); assertEquals(41.05f, g.player.pos.y, .001f);
        Vec3i p = new Vec3i((int) Math.floor(g.player.pos.x), 41, (int) Math.floor(g.player.pos.z));
        assertNotEquals(home, p); assertTrue(UrbanBootstrap.safe(g.world, p));
        assertFalse(UrbanBootstrap.safe(g.world, new Vec3i(-9000, 41, -9000)));
        assertFalse(UrbanBootstrap.safe(g.world, home.offset(0, 4, 0)));
        assertFalse(UrbanBootstrap.safe(g.world, home.offset(0, -4, 0)));
    }

    static Game session(long seed, GameMode mode) {
        Game g = new Game(); g.newWorld(seed, true, World.GEN_URBAN_STARTER, mode);
        g.appState = Game.AppState.PLAYING; g.player.onGround = true;
        return g;
    }
    static BuildingPlan.Fixture fixture(BuildingPlan b, BlockType block) {
        return b.fixtures().stream().filter(f -> f.block() == block && f.position().y() == 41).findFirst().orElseThrow();
    }
    private static void use(Game g, ItemType type) {
        int slot = -1;
        for (int i = 0; i < g.player.inventory.size(); i++) {
            if (g.player.inventory.get(i) != null && g.player.inventory.get(i).type == type) { slot = i; break; }
        }
        assertTrue(slot >= 0, "missing " + type);
        ItemStack selected = g.player.inventory.get(slot); g.player.inventory.set(slot, g.player.inventory.get(0));
        g.player.inventory.set(0, selected); g.player.hotbarSel = 0; g.targetHit = null; g.interactions.rightClick();
    }
    static void walk(Game g, Vec3i target) {
        Vec3i start = new Vec3i((int) Math.floor(g.player.pos.x), 41, (int) Math.floor(g.player.pos.z));
        var command = new PlayerMovementSystem.Command();
        for (Vec3i next : route(g.world, start, target)) {
            if (Fixtures.isClosedDoor(g.world.getBlock(next.x(), 41, next.z()))) { assertTrue(g.interactWithBlockAt(next)); }
            boolean arrived = false;
            for (int frame = 0; frame < 240; frame++) {
                float dx = next.x() + .5f - g.player.pos.x, dz = next.z() + .5f - g.player.pos.z;
                boolean moving = dx * dx + dz * dz > .003f;
                command.set((float) Math.toDegrees(Math.atan2(dx, -dz)), moving, false, false, false, false, false, false, false);
                g.applyMovementCommand(command, 1f / 120f);
                if (!moving && Math.abs(g.player.pos.y - 41) < .06f) { arrived = true; break; }
            }
            assertTrue(arrived, "blocked route to " + target + " at " + next + ", actual=" + g.player.pos);
        }
    }
    static List<Vec3i> route(World world, Vec3i start, Vec3i goal) {
        VoxelAccess opened = new VoxelAccess() {
            public boolean isColumnLoaded(int x, int z) { return world.isColumnLoaded(x, z); }
            public BlockType getBlock(int x, int y, int z) {
                BlockType b = world.getBlock(x, y, z);
                return Fixtures.isClosedDoor(b) && world.fixtureLockAt(x, y, z) == FixtureStates.LOCK_NONE ? Fixtures.kindOf(b).open : b;
            }
        };
        Map<Vec3i, Vec3i> parents = new HashMap<>(); var queue = new ArrayDeque<Vec3i>();
        parents.put(start, null); queue.add(start);
        while (!queue.isEmpty() && !parents.containsKey(goal)) {
            Vec3i from = queue.remove();
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                Vec3i to = from.offset(d[0], 0, d[1]);
                if (Math.abs(to.x() - start.x()) <= 96 && Math.abs(to.z() - start.z()) <= 96
                        && !parents.containsKey(to) && AccessibilityValidator.canMove(opened, from, to)) {
                    parents.put(to, from); queue.add(to);
                }
            }
            assertTrue(parents.size() < 20000, "bounded starter traversal");
        }
        assertTrue(parents.containsKey(goal), "no route from " + start + " to " + goal);
        List<Vec3i> path = new ArrayList<>();
        for (Vec3i at = goal; at != null; at = parents.get(at)) { path.add(at); }
        Collections.reverse(path); return path;
    }
}
