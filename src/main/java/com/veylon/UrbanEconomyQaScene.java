package com.veylon;

import com.veylon.item.CraftingSystem;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.item.Recipe;
import com.veylon.ui.CraftingScreen;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingFurnishing;
import com.veylon.world.urban.BuildingFurnishing.ContainerUse;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.BuildingPlan.Fixture;
import com.veylon.world.urban.EncounterAnchors;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanStarterSite;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The milestone-23 extended-play tour ({@code VEYLON_SCENE=urban_economy}) on a generator-20 world:
 * two and a half days of survival in the economy city, played in real time with ordinary controls.
 *
 * <ul>
 *   <li>Day one: the refuge's own stores, then the first trip next door (a pry tool, scrap, offcuts,
 *       rags and food), the reinforced spear at the refuge's bench, a meal and a drink, the corner
 *       shop, water from the rain collector and from the refuge's taps boiled at the courtyard fire,
 *       the rest of the day indoors and the night slept in the refuge.</li>
 *   <li>Day two: the morning meal, a flat in the next block searched floor by floor, home again, water,
 *       the day out indoors and a second night.</li>
 *   <li>Day three: the morning ledger, F5 and F9, and the report.</li>
 * </ul>
 * The clock is never staged: days pass in real time and nights by sleeping, so hunger, thirst,
 * spoilage, weather and threats run as they do for a player. Only one pose is staged, the walk to the
 * next block's street door, which no bounded walking route reaches from the refuge; it is printed as
 * staged. Whatever infected come near are fought with the held weapon by the route's own fight handler.
 * Each day prints a ledger: day, time, health, hunger, thirst, food and water in hand, containers
 * searched, meals, drinks, threats met and defeated. Scripted native evidence, never a human playtest.
 */
final class UrbanEconomyQaScene extends UrbanPeopleScene {
    /** Wall-clock seconds the whole tour may take natively (about eighteen minutes of real days). */
    static final double DEADLINE_SECONDS = 2_400;

    private record Plan(BuildingPlan refuge, Fixture bench, Fixture bed, Fixture basin, Vec3i collector, Vec3i fire,
                        BuildingPlan flat, List<Fixture> flatFood) { }

    private Plan plan;
    private boolean passed;
    private int searches, meals, drinks, nights;

    UrbanEconomyQaScene(Game game) { super(game); }

    @Override protected String tag() { return "urban-economy"; }

    @Override protected double deadline() { return DEADLINE_SECONDS; }

    boolean passed() { return passed; }

    /** Plans the tour on the fresh generated world; no block is changed and nothing is staged but one pose. */
    void stage() {
        if (game.world.urban == null || !game.world.urban.hasEconomy()) {
            throw new IllegalStateException("urban_economy needs a generator-20 world (VEYLON_GENERATOR=urban-dev)");
        }
        plan = plan(game.world);
        System.out.println("[urban-economy] stage seed=" + game.world.seed + " generator=" + game.world.generatorVersion
                + " content=" + game.world.urban.contentRevision() + " mode=" + game.gameMode().name().toLowerCase(Locale.ROOT)
                + " refuge=" + UrbanIds.format(plan.refuge().id()) + " flat=" + UrbanIds.format(plan.flat().id())
                + " evidence=input-seam(W,F,C,1-9,RMB,F5,F9); screen-commands(crate-transfers,craft); real-time-days;"
                + " sleep-nights; staged=one-pose");
        Vec3i home = plan.bed().target();
        UrbanSlicePlan slice = UrbanSlicePlan.of(game.world);
        Vec3i door = slice.apartment().approaches().getLast();
        add(new Wait("settle", 1.5f), new Capture("01-day1-refuge-morning"), new Ledger("day 1 morning"));
        for (Fixture f : refugeStores(plan.refuge())) { add(new Search(f)); }
        // The first trip: the quiet apartment next door, its locked inner door forced as the slice forces it.
        add(new Walk(plan.refuge().approaches().getLast()), new Walk(door), new Force(slice.forcedDoor(), "02-forced-door"),
                new Search(slice.locker()), new Search(slice.kitchen()), new Search(slice.bathroom()),
                new Capture("03-first-trip"), new Walk(door), new Walk(plan.refuge().approaches().getLast()),
                new Craft("Reinforced Spear"), new Capture("04-first-upgrade"), new Meal(),
                new Walk(slice.shop().approaches().getLast()), new Search(slice.shelf()), new Capture("05-corner-shop"),
                new Walk(slice.shop().approaches().getLast()), new Walk(plan.refuge().approaches().getLast()));
        addWater();
        add(new Capture("06-water"), new Rest(14 * 60, home), new Capture("07-afternoon"), new Rest(20 * 60 + 30, home),
                new Ledger("day 1 evening"), new Sleep(), new Capture("08-day2-morning"), new Ledger("day 2 morning"),
                new Meal(), new Pose("pose the next block's street door", () -> plan.flat().approaches().getFirst(),
                        () -> plan.flat().approaches().getLast()));
        for (Fixture f : plan.flatFood()) { add(new Search(f)); }
        add(new Capture("09-next-block"), new Walk(plan.flat().approaches().getFirst()),
                new Pose("pose home", () -> plan.refuge().approaches().getLast(), () -> home), new Walk(home), new Meal());
        addWater();
        add(new Rest(20 * 60 + 30, home), new Ledger("day 2 evening"), new Sleep(), new Capture("10-day3-morning"),
                new Ledger("day 3 morning"), new Meal(), new Record(), new Save(), new Load(), new Reloaded(), new Report());
        staged = true;
    }

    private void addWater() {
        add(new WaterRun(), new Meal());
    }

    // ------------------------------------------------------------------
    // Planning
    // ------------------------------------------------------------------

    private static Plan plan(World world) {
        UrbanStarterSite site = UrbanStarterSite.of(world.urban);
        BuildingPlan refuge = site.refuge();
        Fixture bench = first(refuge, 0, f -> f.block() == BlockType.WORKBENCH);
        Fixture bed = first(refuge, 0, f -> f.block() == BlockType.CAMP_BED);
        Fixture basin = first(refuge, 0, f -> f.block() == BlockType.RAIN_COLLECTOR && f.target() != null);
        Vec3i court = refuge.approaches().getLast();
        BuildingPlan flat = null;
        for (int rz = -1; rz <= 1; rz++) {
            for (int rx = -1; rx <= 1; rx++) {
                for (ParcelPlan parcel : world.urban.regionPlan(rx, rz).parcels()) {
                    BuildingPlan b = world.urban.buildingAt(parcel.x0(), parcel.z0());
                    if (b == null || UrbanStarterSite.isNeighbour(parcel) || b.family() != BuildingPlan.Family.APARTMENT
                            || b.variant() == BuildingPlan.Variant.OCCUPIED || b.variant() == BuildingPlan.Variant.PICKED_CLEAN
                            || SurvivorSites.owner(world.urban, b.id()) != null
                            || !EncounterAnchors.forParcel(world, parcel).isEmpty() || foodOf(b).size() < 2) {
                        continue;
                    }
                    if (flat == null || distance(b.approaches().getFirst(), court) < distance(flat.approaches().getFirst(), court)) {
                        flat = b;
                    }
                }
            }
        }
        if (flat == null) { throw new IllegalStateException("no quiet flat near the starter block"); }
        return new Plan(refuge, bench, bed, basin, site.collector(), site.fire(), flat, foodOf(flat));
    }

    /** A flat's kitchen, pantry and fridge on its two lowest storeys, at most four. */
    private static List<Fixture> foodOf(BuildingPlan b) {
        List<Fixture> out = new ArrayList<>();
        for (Fixture f : b.fixtures()) {
            if (f.kind() != BuildingPlan.FixtureKind.CONTAINER || f.target() == null || UrbanIds.floorIndex(f.id()) > 1) { continue; }
            ContainerUse use = BuildingFurnishing.containerUse(b, f);
            if (use == ContainerUse.KITCHEN_CABINET || use == ContainerUse.PANTRY_SHELF || use == ContainerUse.FRIDGE) { out.add(f); }
        }
        out.sort(Comparator.comparingInt((Fixture f) -> UrbanIds.floorIndex(f.id())).thenComparingLong(Fixture::id));
        return out.subList(0, Math.min(4, out.size()));
    }

    /** The refuge's ground-floor stores, by id. */
    private static List<Fixture> refugeStores(BuildingPlan refuge) {
        List<Fixture> out = new ArrayList<>();
        for (Fixture f : refuge.fixtures()) {
            if (f.kind() == BuildingPlan.FixtureKind.CONTAINER && f.target() != null && UrbanIds.floorIndex(f.id()) == 0) { out.add(f); }
        }
        return out;
    }

    private static Fixture first(BuildingPlan b, int floor, java.util.function.Predicate<Fixture> test) {
        return b.fixtures().stream().filter(f -> UrbanIds.floorIndex(f.id()) == floor && test.test(f))
                .min(Comparator.comparingLong(Fixture::id))
                .orElseThrow(() -> new IllegalStateException("no such fixture in " + UrbanIds.format(b.id())));
    }

    private static double distance(Vec3i a, Vec3i b) {
        return Math.hypot(a.x() - b.x(), a.z() - b.z());
    }

    // ------------------------------------------------------------------
    // Tasks
    // ------------------------------------------------------------------

    /** Walks to a container, opens it with F, waits out the search and takes everything it holds. */
    private final class Search extends Task {
        private final Fixture fixture;
        private Walk walk;
        private int phase, presses;
        private float wait;

        Search(Fixture fixture) { super("search " + fixture.position()); this.fixture = fixture; }

        @Override boolean threatsInterrupt() { return phase < 3 || game.uiMode == Game.UiMode.NONE; }

        @Override boolean step(float dt) {
            Vec3i p = fixture.position();
            switch (phase) {
                case 0 -> {
                    if (walk == null) {
                        // A flat behind a locked door is passed by rather than forced: the day goes on.
                        if (UrbanSlicePlan.route(game.world, feet(), fixture.target()) == null) {
                            deeds.add("passed by " + UrbanIds.format(fixture.id()) + ": no open way in");
                            return true;
                        }
                        walk = new Walk(fixture.target());
                    }
                    if (walk.step(dt)) { phase = 1; }
                }
                case 1 -> {
                    lookAtBlock(p, .5f);
                    if (++presses > 8) { throw fail("could not open " + p); }
                    press(GLFW_KEY_F);
                    phase = 2;
                    wait = 0;
                }
                case 2 -> {
                    wait += dt;
                    if (game.uiMode == Game.UiMode.CRATE && p.equals(game.openCratePos)) {
                        phase = 3;
                    } else if (wait > .5f) {
                        phase = 1;
                    }
                }
                case 3 -> {
                    if (game.uiMode != Game.UiMode.CRATE) { phase = 1; return false; }
                    if (game.crateSearching()) { return false; }
                    Inventory crate = game.openCrate;
                    int taken = 0;
                    for (int slot = 0; crate != null && slot < crate.size(); slot++) {
                        if (crate.get(slot) != null) { taken += game.transferCrateItemToPlayer(slot); }
                    }
                    searches++;
                    deeds.add("searched " + UrbanIds.format(fixture.id()) + " took " + taken);
                    press(GLFW_KEY_F);
                    phase = 4;
                    wait = 0;
                }
                default -> {
                    wait += dt;
                    if (game.uiMode == Game.UiMode.NONE) { return true; }
                    if (wait > .5f) { press(GLFW_KEY_F); wait = 0; }
                }
            }
            return false;
        }
    }

    /** F on the workbench, the crafting screen's own command, then C to close. */
    private final class Craft extends Task {
        private final String recipe;
        private Walk walk;
        private int phase;

        Craft(String recipe) { super("craft " + recipe); this.recipe = recipe; }

        @Override boolean step(float dt) {
            switch (phase) {
                case 0 -> {
                    if (walk == null) { walk = new Walk(plan.bench().target()); }
                    if (walk.step(dt)) { phase = 1; }
                }
                case 1 -> {
                    lookAtBlock(plan.bench().position(), .5f);
                    press(GLFW_KEY_F);
                    phase = 2;
                }
                case 2 -> {
                    if (game.uiMode != Game.UiMode.CRAFTING) {
                        if (age > 8) { throw fail("the workbench did not open"); }
                        phase = 1;
                        return false;
                    }
                    Recipe r = CraftingSystem.recipesFor(game.world).stream().filter(x -> x.name.equals(recipe))
                            .findFirst().orElseThrow();
                    String made = CraftingScreen.craft(game, r, game.nearbyStations());
                    if (made == null) { throw fail("could not craft " + recipe + ": " + inventory()); }
                    deeds.add("crafted " + made);
                    phase = 3;
                }
                case 3 -> {
                    press(GLFW_KEY_C);
                    phase = 4;
                }
                default -> {
                    return game.uiMode == Game.UiMode.NONE;
                }
            }
            return false;
        }
    }

    /** Eats while hungry and drinks while thirsty, one use at a time with the use button. */
    private final class Meal extends Task {
        private ItemType using;
        private int before;
        private float wait;

        Meal() { super("eat and drink"); }

        @Override boolean step(float dt) {
            if (using != null) {
                wait += dt;
                if (wait < .3f) { return false; }
                if (game.player.inventory.count(using) < before) {
                    if (using.isEdible()) { meals++; } else { drinks++; }
                }
                using = null;
            }
            boolean free = game.player.abilities.unlimitedItems();
            ItemType next = !free && game.player.hunger < 75 && food() > 0
                    ? (has(ItemType.DRIED_MEAT) ? ItemType.DRIED_MEAT : ItemType.DRIED_BERRY)
                    : !free && game.player.thirst < 70 && has(ItemType.WATERSKIN_CLEAN) ? ItemType.WATERSKIN_CLEAN : null;
            if (next == null || age > 20) { return true; }
            onHotbar(next);
            if (!select(next)) { throw fail("could not hold " + next); }
            game.camera.pitch = 75;
            before = game.player.inventory.count(next);
            rightClick();
            using = next;
            wait = 0;
            return false;
        }
    }

    /** Walks to a cell, faces a block and presses F once: filling a skin, fuelling or boiling at the fire. */
    private final class PressF extends Task {
        private final Vec3i block, stand;
        private Walk walk;
        private Object mark;
        private String aimed;

        PressF(String name, Vec3i block, Vec3i stand) { super(name); this.block = block; this.stand = stand; }

        @Override boolean step(float dt) {
            if (walk == null) { walk = new Walk(stand); }
            if (mark == null) {
                if (!walk.step(dt)) { return false; }
                lookAtBlock(block, .4f);
                mark = logMark();
                age = 0;
                return false;
            }
            lookAtBlock(block, .4f);
            if (age < .4f) { return false; }
            if (aimed == null) {
                var hit = game.targetHit;
                aimed = (hit == null ? "nothing" : hit.type() + " at " + hit.x() + "," + hit.y() + "," + hit.z())
                        + ", prompt '" + game.interactPrompt + "'";
                press(GLFW_KEY_F);
                return false;
            }
            if (age < 1f) { return false; }
            List<String> said = loggedAfter(mark);
            deeds.add(name + ": " + (said.isEmpty() ? "nothing said (aimed at " + aimed + ")" : said.getLast()));
            return true;
        }
    }

    /**
     * Water for the day, one F at a time and only where it makes sense: an empty skin filled at the rain
     * collector while it holds a litre, then at the refuge's tap (stale, never drunk as it is), the
     * courtyard fire fuelled with a plank while it is out and every dirty skin boiled clean.
     */
    private final class WaterRun extends Task {
        private Task current;
        private int taps, fires;

        WaterRun() { super("water for the day"); }

        @Override boolean step(float dt) {
            if (current != null) {
                current.age += dt;
                if (!current.step(dt)) { return false; }
                current = null;
            }
            current = next();
            if (current == null) {
                deeds.add(String.format(Locale.ROOT, "water: %d clean, %d dirty, %d empty skins",
                        count(ItemType.WATERSKIN_CLEAN), count(ItemType.WATERSKIN_DIRTY), count(ItemType.WATERSKIN_EMPTY)));
                return true;
            }
            return false;
        }

        private Task next() {
            int empty = count(ItemType.WATERSKIN_EMPTY), dirty = count(ItemType.WATERSKIN_DIRTY);
            if (empty > 0 && game.world.collectorWater.getOrDefault(plan.collector(), 0f) >= 1f) {
                return new PressF("fill a skin at the rain collector", plan.collector(), plan.collector().offset(1, 0, 0));
            }
            if (empty > 0 && taps < 4 && game.world.urban.utilities.left(plan.refuge()) > 0) {
                taps++;
                return new PressF("fill a skin at the refuge's tap", plan.basin().position(), plan.basin().target());
            }
            if (dirty == 0 || fires >= 8) { return null; }
            fires++;
            boolean lit = game.world.campfireFuel.getOrDefault(plan.fire(), 0f) > 0;
            if (!lit && count(ItemType.PLANK) == 0) { return null; }
            return new PressF(lit ? "boil a skin at the courtyard fire" : "fuel the courtyard fire", plan.fire(),
                    plan.fire().offset(1, 0, 0));
        }
    }

    /** Rests indoors at the refuge in real time until a time of day, eating and drinking when needed. */
    private final class Rest extends Task {
        private final int minute;
        private final Vec3i at;
        private Walk walk;
        private Meal meal;

        Rest(int minute, Vec3i at) {
            super(String.format(Locale.ROOT, "rest until %02d:%02d", minute / 60, minute % 60));
            this.minute = minute;
            this.at = at;
        }

        @Override boolean step(float dt) {
            if (walk == null) { walk = new Walk(at); }
            if (!walk.step(dt)) { return false; }
            if (meal != null) {
                if (meal.step(dt)) { meal = null; }
                return false;
            }
            if (!game.player.abilities.unlimitedItems() && (game.player.hunger < 60 || game.player.thirst < 55)) {
                meal = new Meal();
                return false;
            }
            return game.time.totalMinutes % (24 * 60) >= minute;
        }
    }

    /** F on the refuge's bed at night, slept until a morning; woken rested in the dark, back to sleep. */
    private final class Sleep extends Task {
        private Walk walk;
        private int presses;
        private float wait;
        private double started = -1;

        Sleep() { super("sleep in the refuge"); }

        @Override boolean step(float dt) {
            if (walk == null) { walk = new Walk(plan.bed().target()); }
            if (!walk.step(dt)) { return false; }
            if (started < 0) { started = game.time.totalMinutes; }
            if (game.sleeping) { wait = 0; return false; }
            double hour = game.time.totalMinutes % (24 * 60) / 60.0;
            boolean morning = game.time.totalMinutes - started > 6 * 60 && hour >= 5 && hour < 12;
            if (morning && game.sleepFade < .05f) {
                nights++;
                deeds.add("woke at " + clock() + ": " + lastLine());
                return true;
            }
            wait += dt;
            if (wait < 1f) { return false; }
            if (++presses > 12) { throw fail("could not sleep: " + lastLine()); }
            lookAtBlock(plan.bed().position(), .4f);
            press(GLFW_KEY_F);
            wait = 0;
            return false;
        }

        @Override boolean threatsInterrupt() { return !game.sleeping; }
    }

    /** One line of the day's account, the extended play's record. */
    private final class Ledger extends Task {
        Ledger(String label) { super("ledger " + label); }

        @Override boolean step(float dt) {
            System.out.printf(Locale.ROOT, "[urban-economy] ledger %s day=%d time=%s health=%.0f hunger=%.0f thirst=%.0f"
                            + " food=%d clean-water=%d searched=%d meals=%d drinks=%d nights=%d engaged=%d defeated=%d%n",
                    name.substring(7), day(), clock(), game.player.health, game.player.hunger, game.player.thirst, food(),
                    game.player.inventory.count(ItemType.WATERSKIN_CLEAN), searches, meals, drinks, nights, engaged.size(),
                    defeated.size());
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** What the save must bring back: the container ledger and the food and water in hand. */
    private String before;
    private boolean reloaded;

    private String state() {
        return "containers=" + game.world.urban.containers.size() + " food=" + food() + " clean-water="
                + count(ItemType.WATERSKIN_CLEAN) + " day=" + day();
    }

    private final class Record extends Task {
        Record() { super("record before the save"); }

        @Override boolean step(float dt) {
            before = state();
            System.out.println("[urban-economy] before-save " + before);
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** The reloaded world, held a moment so its chunks draw, compared with what was saved. */
    private final class Reloaded extends Task {
        Reloaded() { super("the same supplies after the load"); }

        @Override boolean step(float dt) {
            if (age < 1.5f) { return false; }
            String after = state();
            if (!after.equals(before)) { throw fail("the load changed what was saved:\n  " + before + "\n  " + after); }
            reloaded = true;
            System.out.println("[urban-economy] after-load " + after);
            capture("11-after-load");
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    private final class Report extends Task {
        Report() { super("report"); }

        @Override boolean step(float dt) {
            boolean survival = !game.player.abilities.unlimitedItems();
            passed = nights == 2 && day() >= 3 && searches >= 10 && !game.player.dead && reloaded && captures == 11
                    && game.player.inventory.count(ItemType.IRON_SPEAR) >= 1 && (!survival || meals > 0 && drinks > 0);
            reported = true;
            System.out.println("[urban-economy] pass=" + passed + " mode=" + game.gameMode().name().toLowerCase(Locale.ROOT)
                    + " seed=" + game.world.seed + " day=" + day() + " time=" + clock() + " searches=" + searches
                    + " meals=" + meals + " drinks=" + drinks + " nights=" + nights + " engaged=" + engaged
                    + " defeated=" + defeated + " poses=" + poses + " captures=" + captures + " deeds=" + String.join(" | ", deeds));
            System.out.println("[urban-economy] timeline " + String.join(" ", timeline));
            game.automation.finishScriptedRun();
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Brings a stack from the pack onto the hotbar's last slot, as a player arranging the pack does. */
    private void onHotbar(ItemType type) {
        if (select(type)) { return; }
        Inventory inv = game.player.inventory;
        for (int i = 9; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s != null && s.type == type) {
                ItemStack held = inv.get(8);
                inv.set(8, s);
                inv.set(i, held);
                return;
            }
        }
    }

    private boolean has(ItemType type) {
        return game.player.inventory.count(type) > 0;
    }

    private int count(ItemType type) {
        return game.player.inventory.count(type);
    }

    private int food() {
        return game.player.inventory.count(ItemType.DRIED_BERRY) + game.player.inventory.count(ItemType.DRIED_MEAT);
    }

    private int day() {
        return (int) Math.floor(game.time.totalMinutes / (24 * 60)) + 1;
    }

    private String clock() {
        int m = (int) Math.floor(game.time.totalMinutes % (24 * 60));
        return String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60);
    }

    private String lastLine() {
        var all = game.eventLog.all();
        return all.isEmpty() ? "" : all.getLast();
    }

    private String inventory() {
        StringBuilder sb = new StringBuilder();
        Inventory inv = game.player.inventory;
        for (int i = 0; i < inv.size(); i++) {
            if (inv.get(i) != null) { sb.append(inv.get(i).type).append('x').append(inv.get(i).count).append(' '); }
        }
        return sb.toString();
    }
}
