package com.veylon;

import com.veylon.engine.AudioManager;
import com.veylon.entity.GameMode;
import com.veylon.item.CraftingSystem;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.item.Recipe;
import com.veylon.item.Station;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.CommsComponents;
import com.veylon.world.urban.EncounterAnchors;
import com.veylon.world.urban.SalvageTable;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanContainers;
import com.veylon.world.urban.UrbanRevision;
import com.veylon.world.urban.UrbanSpecies;
import com.veylon.world.urban.VehicleSites;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The survival economy's inputs audited (urban milestone 23, work item 1): every recipe the city offers
 * and every part the long-term objective needs comes from urban supply paths (searched containers,
 * salvaged furniture, doors and cars, survivors' stores, the starter kit and the city's creatures),
 * never from a felled tree, a mined vein or a smelter; each specialist input of the objective has two
 * sources outside every compound on all six design seeds; and optional nature never outweighs the city.
 */
class UrbanEconomyAuditTest {
    /** What only a tree, an ore vein, a smelter or wilderness hunting and foraging gives. */
    private static final Set<ItemType> WILD = EnumSet.of(ItemType.LOG, ItemType.COAL, ItemType.CHARCOAL,
            ItemType.COPPER_ORE, ItemType.IRON_ORE, ItemType.COPPER_INGOT, ItemType.IRON_INGOT, ItemType.SULFUR,
            ItemType.SALTPETER, ItemType.BLACK_POWDER, ItemType.HIDE, ItemType.LEATHER, ItemType.BONE, ItemType.BERRY,
            ItemType.HERB);

    private static World economyCity(long seed) {
        World w = new World(seed, World.GEN_URBAN_ECONOMY);
        w.urban.resolveContentRevision(UrbanRevision.contentFor(w.generatorVersion));
        return w;
    }

    /**
     * The planned buildings of a region. Every parcel plans: the one parcel this audit used to skip
     * (seed 987654321, region (-2, -3), parcel 44) plans since package 25 (D-248).
     */
    private static List<BuildingPlan> buildings(World w, int rx, int rz) {
        List<BuildingPlan> out = new java.util.ArrayList<>();
        for (var p : w.urban.regionPlan(rx, rz).parcels()) {
            BuildingPlan b = w.urban.buildingAt(p.x0(), p.z0());
            if (b != null) { out.add(b); }
        }
        return out;
    }

    @Test
    void theCityRecipeListAsksForNoTreeOreOrSmelter() {
        World w = economyCity(20_260_731L);
        List<Recipe> recipes = CraftingSystem.recipesFor(w);
        assertFalse(recipes.isEmpty());
        for (Recipe r : recipes) {
            assertTrue(r.station == Station.HAND || r.station == Station.WORKBENCH,
                    r.name + " needs no furnace, anvil or tannery: " + r.station);
            for (ItemType input : r.ingredients.keySet()) {
                assertFalse(WILD.contains(input), r.name + " asks for " + input);
            }
        }
    }

    @Test
    void everyCityRecipeIsReachableFromUrbanSourcesAlone() {
        long seed = 20_260_731L;
        Game g = SurvivorWorldArena.session(seed, GameMode.SURVIVAL, World.GEN_URBAN_ECONOMY, new AudioManager());
        World w = g.world;
        Map<ItemType, String> sources = new EnumMap<>(ItemType.class);
        Inventory kit = g.player.inventory;
        for (int i = 0; i < kit.size(); i++) {
            if (kit.get(i) != null) { sources.putIfAbsent(kit.get(i).type, "the starting kit"); }
        }
        for (int rz = -2; rz <= 2; rz++) {
            for (int rx = -2; rx <= 2; rx++) {
                for (BuildingPlan b : buildings(w, rx, rz)) {
                    for (BuildingPlan.Fixture f : b.fixtures()) {
                        if (f.kind() == BuildingPlan.FixtureKind.CONTAINER) {
                            Inventory inv = UrbanContainers.firstRoll(w, UrbanContainers.planned(w, f.position()));
                            for (int i = 0; inv != null && i < inv.size(); i++) {
                                if (inv.get(i) != null) { sources.putIfAbsent(inv.get(i).type, "a " + b.family() + " container"); }
                            }
                        }
                        if (f.kind() != BuildingPlan.FixtureKind.QUEST && f.kind() != BuildingPlan.FixtureKind.ROUTE) {
                            for (ItemStack s : SalvageTable.yields(w, f.position(), f.block())) {
                                sources.putIfAbsent(s.type, "a salvaged " + f.block());
                            }
                        }
                    }
                }
                for (VehicleSites.Wreck wreck : w.urban.vehicles(rx, rz).wrecks()) {
                    for (Vec3i cell : wreck.cells()) {
                        for (ItemStack s : SalvageTable.yields(w, cell, BlockType.CAR_WRECK)) {
                            sources.putIfAbsent(s.type, "a car wreck");
                        }
                    }
                }
            }
        }
        sources.putIfAbsent(ItemType.MEMBRANE_SAC, "a Veilmaw's remains (quarantine)");
        for (ItemType wild : WILD) {
            assertFalse(sources.containsKey(wild) && !sources.get(wild).contains("container"),
                    wild + " only comes from " + sources.get(wild));
        }
        // The closure: whatever a recipe makes from what is already in reach is in reach.
        Set<ItemType> reach = EnumSet.copyOf(sources.keySet());
        List<Recipe> recipes = CraftingSystem.recipesFor(w);
        for (boolean grew = true; grew; ) {
            grew = false;
            for (Recipe r : recipes) {
                if (r.result != null && !reach.contains(r.result) && reach.containsAll(r.ingredients.keySet())) {
                    reach.add(r.result);
                    grew = true;
                }
            }
        }
        for (Recipe r : recipes) {
            Set<ItemType> missing = EnumSet.noneOf(ItemType.class);
            for (ItemType input : r.ingredients.keySet()) {
                if (!reach.contains(input)) { missing.add(input); }
            }
            assertTrue(missing.isEmpty(), r.name + " needs " + missing + " that no urban source gives");
        }
        for (ItemType objective : List.of(ItemType.TRANSMITTER_MODULE, ItemType.POWER_CELL, ItemType.ANTENNA_CABLE,
                ItemType.FUEL_CAN, ItemType.SPARE_PARTS, ItemType.SHEET_METAL, ItemType.IRON_PICKAXE)) {
            assertTrue(sources.containsKey(objective), objective + " lies somewhere within two regions");
        }
        System.out.println("[economy-audit] urban sources within two regions of seed " + seed + ": " + sources);
    }

    @Test
    void eachSpecialistInputOfTheObjectiveHasTwoSourcesOutsideAnyCompoundOnEveryDesignSeed() {
        for (long seed : UrbanStarterProgressionTest.SEEDS) {
            World w = economyCity(seed);
            Map<CommsComponents.Kind, Set<Long>> parts = new EnumMap<>(CommsComponents.Kind.class);
            for (int rz = -3; rz <= 3; rz++) {
                for (int rx = -3; rx <= 3; rx++) {
                    for (BuildingPlan b : buildings(w, rx, rz)) {
                        if (b.variant() == BuildingPlan.Variant.OCCUPIED || b.variant() == BuildingPlan.Variant.PICKED_CLEAN
                                || SurvivorSites.owner(w.urban, b.id()) != null) {
                            continue;
                        }
                        for (BuildingPlan.Fixture f : b.fixtures()) {
                            CommsComponents.Kind kind = f.kind() == BuildingPlan.FixtureKind.CONTAINER
                                    ? CommsComponents.sourceOf(w.urban, b, f) : null;
                            if (kind != null) { parts.computeIfAbsent(kind, k -> new HashSet<>()).add(b.id()); }
                        }
                    }
                }
            }
            int sound = 0;
            for (int rz = -1; rz <= 1; rz++) {
                for (int rx = -1; rx <= 1; rx++) {
                    for (VehicleSites.Wreck wreck : w.urban.vehicles(rx, rz).wrecks()) {
                        if (!wreck.burnt()) { sound++; }
                    }
                }
            }
            System.out.printf("[economy-audit] seed=%d unheld part sources within three regions: %s buildings;"
                    + " cars that did not burn within one region ring: %d%n", seed,
                    parts.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue().size()).toList(), sound);
            for (CommsComponents.Kind kind : CommsComponents.Kind.values()) {
                assertTrue(parts.getOrDefault(kind, Set.of()).size() >= 2,
                        "seed " + seed + ": " + kind + " lies in two unheld buildings within three regions, needing no survivor's leave");
            }
            // A generator (three parts), a radio (one) and a purifier (one) from engines, and fuel for the band scan.
            assertTrue(sound >= 6, "seed " + seed + ": " + sound + " cars that did not burn near the spawn");
        }
    }

    @Test
    void optionalNatureNeverOutweighsTheCity() {
        for (long seed : UrbanStarterProgressionTest.SEEDS) {
            World w = economyCity(seed);
            float cityFood = 0, cityFuel = 0;
            for (BuildingPlan b : buildings(w, 0, 0)) {
                for (BuildingPlan.Fixture f : b.fixtures()) {
                    if (f.kind() == BuildingPlan.FixtureKind.CONTAINER) {
                        Inventory inv = UrbanContainers.firstRoll(w, UrbanContainers.planned(w, f.position()));
                        for (int i = 0; inv != null && i < inv.size(); i++) {
                            if (inv.get(i) != null) { cityFood += inv.get(i).type.food * inv.get(i).count; }
                        }
                    }
                    for (ItemStack s : SalvageTable.yields(w, f.position(), f.block())) {
                        if (s.type == ItemType.PLANK) { cityFuel += 60f * s.count; }
                    }
                }
            }
            float wildFood = 0;
            for (int superblock = 0; superblock < 16; superblock++) {
                for (EncounterAnchors.Anchor a : EncounterAnchors.forEcology(w, 0, 0, superblock)) {
                    int meat = a.species() == UrbanSpecies.GLOWDEER ? 2 : a.species() == UrbanSpecies.MURKHARE ? 1 : 0;
                    wildFood += meat * ItemType.COOKED_MEAT.food;
                }
            }
            float treeFuel = 0;
            for (var tree : w.urban.regionPlan(0, 0).trees()) { treeFuel += tree.trunkHeight() * 120f; } // a log is 120 s of fire
            System.out.printf("[economy-audit] seed=%d spawn region: city food %.0f against hunting %.0f; city fuel %.0f s"
                    + " against park trees' %.0f s%n", seed, cityFood, wildFood, cityFuel, treeFuel);
            assertTrue(cityFood >= 10 * wildFood, "seed " + seed + ": hunting stays a side dish");
            assertTrue(cityFuel >= 5 * treeFuel, "seed " + seed + ": felling every park tree gives less than a fifth of what the furniture gives");
        }
    }
}
