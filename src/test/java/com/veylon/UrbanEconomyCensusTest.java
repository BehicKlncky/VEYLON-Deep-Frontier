package com.veylon;

import com.veylon.entity.PlayerConstants;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.item.Recipe;
import com.veylon.item.UrbanCrafting;
import com.veylon.simulation.TimeConstants;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingFurnishing;
import com.veylon.world.urban.BuildingFurnishing.ContainerUse;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.DistrictMap;
import com.veylon.world.urban.LootTables;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.UrbanContainers;
import com.veylon.world.urban.UrbanDatum;
import com.veylon.world.urban.UrbanDistrict;
import com.veylon.world.urban.UrbanRevision;
import com.veylon.world.urban.UrbanStarterSite;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The survival economy's supply bands (urban milestone 23, DESIGN §16) on the six design seeds of the
 * economy city: generator 20 with content revision 4, read through the production first-touch roll
 * ({@link UrbanContainers#firstRoll}) without touching a ledger or generating a chunk.
 *
 * <p>Bands, not snapshots. A tuning change inside a band keeps these green; a change that breaks the
 * starter guarantees (DESIGN §4.4), lets the refuge alone fund the first upgrade again, moves a flat's
 * food and water off S5, takes away a district's second kind of source for a necessity (S3) or lets a
 * specialist good leak into ordinary flats fails them. Each seed prints the census line DESIGN §16 quotes.
 */
class UrbanEconomyCensusTest {
    /** Hunger a survivor who never sleeps spends in one game day: the unit "waking days" are counted in. */
    private static final float HUNGER_PER_WAKING_DAY = PlayerConstants.BASE_HUNGER_DRAIN_PER_SECOND
            * (float) (TimeConstants.MINUTES_PER_DAY / TimeConstants.MINUTES_PER_REAL_SECOND);

    /** What a set of containers holds, by item, with the containers and buildings each item lies in. */
    private static final class Tally {
        final Map<ItemType, Integer> items = new EnumMap<>(ItemType.class);
        final Map<ItemType, Set<Long>> containers = new EnumMap<>(ItemType.class);
        final Map<ItemType, Set<Long>> buildings = new EnumMap<>(ItemType.class);
        final Map<ItemType, Set<ContainerUse>> uses = new EnumMap<>(ItemType.class);
        int flats, bathrooms, treatedBathrooms, searched;

        void add(BuildingPlan b, BuildingPlan.Fixture f, Inventory inv) {
            searched++;
            if (inv == null) { return; }
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.get(i);
                if (s == null) { continue; }
                items.merge(s.type, s.count, Integer::sum);
                containers.computeIfAbsent(s.type, k -> new HashSet<>()).add(f.id());
                buildings.computeIfAbsent(s.type, k -> new HashSet<>()).add(b.id());
                uses.computeIfAbsent(s.type, k -> EnumSet.noneOf(ContainerUse.class))
                        .add(BuildingFurnishing.containerUse(b, f));
            }
        }

        int n(ItemType... types) {
            int total = 0;
            for (ItemType t : types) { total += items.getOrDefault(t, 0); }
            return total;
        }

        int places(Map<ItemType, Set<Long>> where, ItemType... types) {
            Set<Long> all = new HashSet<>();
            for (ItemType t : types) { all.addAll(where.getOrDefault(t, Set.of())); }
            return all.size();
        }

        Set<ContainerUse> usesOf(ItemType... types) {
            Set<ContainerUse> all = EnumSet.noneOf(ContainerUse.class);
            for (ItemType t : types) { all.addAll(uses.getOrDefault(t, Set.of())); }
            return all;
        }

        /** Nominal hunger: each item's food value times its count, before freshness. */
        float hunger() {
            float h = 0;
            for (var e : items.entrySet()) { h += e.getKey().food * e.getValue(); }
            return h;
        }

        boolean covers(Recipe recipe) {
            for (var e : recipe.ingredients.entrySet()) {
                if (items.getOrDefault(e.getKey(), 0) < e.getValue()) { return false; }
            }
            return true;
        }
    }

    private static World economyCity(long seed) {
        World w = new World(seed, World.GEN_URBAN_ECONOMY);
        w.urban.resolveContentRevision(UrbanRevision.contentFor(w.generatorVersion));
        return w;
    }

    private static World missionCity(long seed) {
        World w = new World(seed, World.GEN_URBAN_MISSIONS);
        w.urban.resolveContentRevision(UrbanRevision.contentFor(w.generatorVersion));
        return w;
    }

    /** The planned buildings of a region, one per building parcel. */
    private static List<BuildingPlan> buildings(World w, int rx, int rz) {
        List<BuildingPlan> out = new ArrayList<>();
        for (ParcelPlan p : w.urban.regionPlan(rx, rz).parcels()) {
            BuildingPlan b = w.urban.buildingAt(p.x0(), p.z0());
            if (b != null) { out.add(b); }
        }
        return out;
    }

    /** Adds every container of a building that a filter accepts to a tally, through the production roll. */
    private static void census(World w, BuildingPlan b, Tally t, java.util.function.Predicate<BuildingPlan.Fixture> which) {
        for (BuildingPlan.Fixture f : b.fixtures()) {
            if (f.kind() != BuildingPlan.FixtureKind.CONTAINER || !which.test(f)) { continue; }
            UrbanContainers.Planned plan = UrbanContainers.planned(w, f.position());
            assertNotNull(plan, "a planned container at " + f.position());
            Inventory inv = UrbanContainers.firstRoll(w, plan);
            t.add(b, f, inv);
            if (BuildingFurnishing.containerUse(b, f) == ContainerUse.BATHROOM_CABINET) {
                t.bathrooms++;
                if (inv != null && (inv.has(ItemType.BANDAGE, 1) || inv.has(ItemType.ANTISEPTIC, 1)
                        || inv.has(ItemType.MEDICINE, 1))) {
                    t.treatedBathrooms++;
                }
            }
        }
        if (b.family() == BuildingPlan.Family.APARTMENT) { t.flats += b.storeys(); }
    }

    private static List<BuildingPlan> starterBlock(World w) {
        List<BuildingPlan> out = new ArrayList<>();
        for (BuildingPlan b : buildings(w, 0, 0)) {
            if (UrbanStarterSite.isNeighbour(b.parcel())) { out.add(b); }
        }
        return out;
    }

    private static boolean ground(BuildingPlan.Fixture f) {
        return f.position().y() == UrbanDatum.STREET_Y + 1;
    }

    @Test
    void theStarterBlockKeepsItsGuaranteesWhileItsRefugeAloneNeverFundsTheFirstUpgrade() {
        Recipe spear = UrbanCrafting.RECIPES.getFirst();
        assertEquals(ItemType.IRON_SPEAR, spear.result, "the first upgrade (DESIGN §4.4)");
        for (long seed : UrbanStarterProgressionTest.SEEDS) {
            World w = economyCity(seed);
            List<BuildingPlan> block = starterBlock(w);
            assertEquals(6, block.size(), "seed " + seed + ": the refuge, four apartments and the shop");
            Tally groundFloors = new Tally(), refuge = new Tally(), all = new Tally();
            boolean firstTripFundsTheSpear = false;
            for (BuildingPlan b : block) {
                boolean home = b.parcel().use() == ParcelPlan.Use.REFUGE;
                census(w, b, groundFloors, UrbanEconomyCensusTest::ground);
                census(w, b, all, f -> true);
                if (home) {
                    census(w, b, refuge, f -> true);
                } else {
                    Tally trip = new Tally();
                    census(w, b, trip, UrbanEconomyCensusTest::ground);
                    firstTripFundsTheSpear |= trip.covers(spear);
                }
            }
            String context = "seed " + seed;
            // DESIGN §4.4, from the ground floors alone, which are reservations rather than rolls.
            assertTrue(groundFloors.n(ItemType.DRIED_BERRY, ItemType.DRIED_MEAT) >= 6, context + " food");
            assertTrue(groundFloors.places(groundFloors.containers, ItemType.DRIED_BERRY, ItemType.DRIED_MEAT) >= 3,
                    context + " food in three containers");
            assertTrue(groundFloors.places(groundFloors.buildings, ItemType.DRIED_BERRY, ItemType.DRIED_MEAT) >= 2,
                    context + " food in two buildings");
            assertTrue(groundFloors.n(ItemType.WATERSKIN_CLEAN) >= 3, context + " drinkable water");
            assertTrue(groundFloors.n(ItemType.BANDAGE) >= 2 && groundFloors.n(ItemType.ANTISEPTIC) >= 1, context + " treatment");
            assertTrue(groundFloors.places(groundFloors.buildings, ItemType.IRON_PICKAXE)
                    > (refuge.n(ItemType.IRON_PICKAXE) > 0 ? 1 : 0), context + " a pry tool outside the refuge");
            assertTrue(firstTripFundsTheSpear, context + ": one neighbour's ground floor funds the reinforced spear");
            // The refuge's own stores feed and treat the survivor but never fund the first upgrade (D-97).
            assertFalse(refuge.covers(spear), context + ": the refuge alone funds the spear " + refuge.items);
            assertEquals(0, refuge.n(ItemType.SCRAP), context + ": no scrap on any storey of the refuge");
            assertTrue(refuge.n(ItemType.DRIED_BERRY) >= 2 && refuge.n(ItemType.WATERSKIN_CLEAN) >= 1
                    && refuge.n(ItemType.BANDAGE) >= 1, context + ": the refuge keeps a day's food, water and first aid");
            // The block as a whole feeds a survivor for days, not weeks, and far less than content three's did.
            Tally before = new Tally();
            World old = missionCity(seed);
            for (BuildingPlan b : starterBlock(old)) { census(old, b, before, f -> true); }
            float days = all.hunger() / HUNGER_PER_WAKING_DAY, oldDays = before.hunger() / HUNGER_PER_WAKING_DAY;
            System.out.printf("[economy-census] seed=%d starter block: food %.1f waking days (content 3: %.1f), water %d"
                            + " skins (%d), bandages %d (%d), pry tools %d (%d), scrap %d (%d); refuge %s%n",
                    seed, days, oldDays, all.n(ItemType.WATERSKIN_CLEAN), before.n(ItemType.WATERSKIN_CLEAN),
                    all.n(ItemType.BANDAGE), before.n(ItemType.BANDAGE), all.n(ItemType.IRON_PICKAXE),
                    before.n(ItemType.IRON_PICKAXE), all.n(ItemType.SCRAP), before.n(ItemType.SCRAP), refuge.items);
            assertTrue(days >= 3 && days <= 12, context + ": the starter block holds " + days + " waking days of food");
            assertTrue(days <= 0.6f * oldDays, context + ": " + days + " against content three's " + oldDays);
            assertTrue(all.n(ItemType.IRON_PICKAXE) <= before.n(ItemType.IRON_PICKAXE) / 2,
                    context + ": pry tools are found, not tripped over");
        }
    }

    @Test
    void aStockedFlatHoldsAServingOrTwoOfFoodAndAboutHalfALitreOfWater() {
        for (long seed : UrbanStarterProgressionTest.SEEDS) {
            World w = economyCity(seed);
            Tally flats = new Tally();
            int buildings = 0, pickedClean = 0;
            for (int rz = -1; rz <= 1; rz++) {
                for (int rx = -1; rx <= 1; rx++) {
                    for (BuildingPlan b : buildings(w, rx, rz)) {
                        buildings++;
                        if (b.variant() == BuildingPlan.Variant.PICKED_CLEAN) { pickedClean++; }
                        if (b.family() != BuildingPlan.Family.APARTMENT || UrbanStarterSite.isNeighbour(b.parcel())
                                || b.variant() == BuildingPlan.Variant.PICKED_CLEAN
                                || b.variant() == BuildingPlan.Variant.OCCUPIED) {
                            continue;
                        }
                        census(w, b, flats, f -> true);
                    }
                }
            }
            float food = flats.n(ItemType.DRIED_BERRY, ItemType.DRIED_MEAT) / (float) flats.flats;
            float water = flats.n(ItemType.WATERSKIN_CLEAN) / (float) flats.flats;
            float treated = flats.treatedBathrooms / (float) flats.bathrooms;
            float stripped = pickedClean / (float) buildings;
            System.out.printf("[economy-census] seed=%d %d stocked flats in nine regions: %.2f servings, %.2f litres a"
                    + " flat; treatment in %.0f%% of bathrooms; %.0f%% of %d buildings picked clean%n",
                    seed, flats.flats, food, water, 100 * treated, 100 * stripped, buildings);
            String context = "seed " + seed;
            assertTrue(flats.flats > 300, context + ": a census of hundreds of flats, not a handful");
            assertTrue(food >= 1f && food <= 2f, context + ": S5 wants a serving or two a flat, not " + food);
            assertTrue(water >= .5f && water <= 1f, context + ": S5 wants half a litre to a litre a flat, not " + water);
            assertTrue(treated >= .35f && treated <= .65f, context + ": treatment in about half of bathrooms, not " + treated);
            assertTrue(stripped >= .15f && stripped <= .40f, context + ": about a third of buildings picked clean, not " + stripped);
            assertEquals(0, flats.n(ItemType.SPARE_PARTS), context + ": a flat's storage holds no specialist spare parts");
        }
    }

    @Test
    void everyDistrictHasTwoKindsOfSourceForEachNecessityAndSpecialistGoodsKeepToTheirPlaces() {
        ItemType[][] necessities = {
                {ItemType.DRIED_BERRY, ItemType.DRIED_MEAT}, {ItemType.WATERSKIN_CLEAN},
                {ItemType.BANDAGE, ItemType.ANTISEPTIC, ItemType.MEDICINE}, {ItemType.TORCH}, {ItemType.IRON_PICKAXE}};
        for (long seed : UrbanStarterProgressionTest.SEEDS) {
            World w = economyCity(seed);
            Map<UrbanDistrict, int[]> nearest = new EnumMap<>(UrbanDistrict.class);
            for (int rz = -3; rz <= 3; rz++) {
                for (int rx = -3; rx <= 3; rx++) {
                    UrbanDistrict d = DistrictMap.districtOf(seed, rx, rz);
                    int[] best = nearest.get(d);
                    if (best == null || rx * rx + rz * rz < best[0] * best[0] + best[1] * best[1]) {
                        nearest.put(d, new int[]{rx, rz});
                    }
                }
            }
            assertEquals(UrbanDistrict.values().length, nearest.size(), "seed " + seed + ": all six districts within three regions");
            for (var e : nearest.entrySet()) {
                Tally t = new Tally();
                Tally clinic = new Tally(), bathroom = new Tally();
                for (BuildingPlan b : buildings(w, e.getValue()[0], e.getValue()[1])) {
                    census(w, b, t, f -> true);
                    census(w, b, clinic, f -> UrbanContainers.planned(w, f.position()).table() == LootTables.Table.CLINIC);
                    census(w, b, bathroom, f -> UrbanContainers.planned(w, f.position()).table() == LootTables.Table.BATHROOM);
                }
                String context = "seed " + seed + " " + e.getKey();
                for (ItemType[] need : necessities) {
                    assertTrue(t.usesOf(need).size() >= 2, context + ": " + need[0] + " from " + t.usesOf(need));
                }
                // Specialist goods keep to specialist places (DESIGN §8, S3).
                assertTrue(t.usesOf(ItemType.FLINTLOCK_PISTOL).stream().allMatch(u -> u == ContainerUse.ARMORY_LOCKER
                        || u == ContainerUse.UTILITY_LOCKER || u == ContainerUse.STOCKROOM_CRATE), context + " pistols");
                assertEquals(0, t.n(ItemType.IRON_INGOT, ItemType.BLACK_POWDER),
                        context + ": nothing no city recipe uses");
                if (clinic.searched > 0 && bathroom.searched > 0) {
                    float clinicMedicine = clinic.n(ItemType.MEDICINE) / (float) clinic.searched;
                    float bathroomMedicine = bathroom.n(ItemType.MEDICINE) / (float) bathroom.searched;
                    assertTrue(clinicMedicine > 4 * bathroomMedicine, context + ": medicine is richest in clinics, "
                            + clinicMedicine + " a cabinet against a bathroom's " + bathroomMedicine);
                }
            }
        }
    }
}
