package com.veylon;

import com.veylon.entity.Npc;
import com.veylon.entity.Player;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.item.UrbanCrafting;
import com.veylon.qa.RuntimeBudgetSnapshot;
import com.veylon.save.SaveSystem;
import com.veylon.ui.CraftingScreen;
import com.veylon.util.AppPaths;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.FixtureStates;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan.Fixture;
import com.veylon.world.urban.EncounterAnchors;
import com.veylon.world.urban.UrbanContainers;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The milestone-11 playable-slice route ({@code VEYLON_SCENE=urban_slice}) on the generated
 * starter neighbourhood of {@code VEYLON_GENERATOR=urban-dev}, in either game mode.
 *
 * <p>It plays with ordinary controls: held keys and mouse buttons go through the production
 * {@link com.veylon.engine.Input} seam (W, Ctrl, F, C, 1-9, F5, F9, left and right mouse), the
 * camera turns as mouse look would, and container and crafting clicks use the same commands
 * those screens call. The normal frame step owns every outcome: no item, pose, block or body is
 * staged. The route leaves the refuge, forces a door and searches a multi-floor apartment that
 * hosts an encounter, handles an infected, searches the shop, bars the refuge, eats, crafts the
 * first upgrade, quick-saves, quick-loads over the live world, then revisits in reverse order and
 * checks for refills, resurrection and stale state. Failures throw, like the other urban scenes.
 */
final class UrbanSliceQaScene extends UrbanRouteScene {
    /** Wall-clock seconds in a native session, simulated seconds in a headless route. */
    static final double DEADLINE_SECONDS = 900;
    private static final float HUNT_WAIT_SECONDS = 60f;

    private UrbanSlicePlan plan;
    private boolean passed;
    private float gameMinutesAtStart;

    // Route observations.
    private float walked, damage, lastX, lastZ, lastHealth = -1;
    private int searches, revisits, harm, lastHarm;
    private Player observed;
    private float minHealth = Float.MAX_VALUE, minBodyTemp = Float.MAX_VALUE;
    private final Set<String> eventsSeen = new java.util.TreeSet<>(), afflictionsSeen = new java.util.TreeSet<>();

    // State saved by the quick-save, compared after the quick-load and on the revisit.
    private final Map<Vec3i, Touched> touched = new LinkedHashMap<>();
    private final Map<Vec3i, BlockType> doorBlocks = new LinkedHashMap<>();
    private final Map<Vec3i, Integer> doorLocks = new LinkedHashMap<>();
    private World savedWorld;
    private Player savedPlayer;
    private List<Npc> savedNpcs = List.of();
    private Vec3i savedAnchor;

    private record Touched(long id, UrbanContainers.State state, Map<ItemType, Integer> contents) { }

    UrbanSliceQaScene(Game game) {
        super(game);
    }

    @Override protected String tag() { return "urban-slice"; }
    @Override protected double deadline() { return DEADLINE_SECONDS; }
    @Override protected void threatInReach() { capture("08-threat"); }
    @Override protected void defeatedThreat() {
        if (defeated.size() == 1) { capture("09-defeated"); }
    }

    boolean passed() { return passed; }
    Set<Long> defeated() { return defeated; }
    Vec3i forcedAt() { return forcedAt; }
    UrbanSlicePlan plan() { return plan; }

    /** Stages the route on the current generated world; nothing in the world is changed. */
    void stage() {
        if (!AppPaths.isolatedFromPlayerData()) { // the route quick-saves over the default slot
            throw new IllegalStateException("urban_slice needs an isolated data directory (veylon.dataDir)");
        }
        plan = UrbanSlicePlan.of(game.world);
        gameMinutesAtStart = (float) game.time.totalMinutes;
        System.out.println("[urban-slice] stage seed=" + game.world.seed + " generator=" + game.world.generatorVersion
                + " mode=" + game.gameMode().name().toLowerCase(Locale.ROOT)
                + " refuge=" + plan.refuge().id() + " apartment=" + plan.apartment().id() + " shop=" + plan.shop().id()
                + " home=" + plan.home() + " evidence=input-seam(W,Ctrl,F,C,1-9,F5,F9,LMB,RMB);"
                + " screen-commands(crate-transfers,craft); generated-geometry; normal-simulation");
        Vec3i court = plan.refuge().approaches().getLast();
        Vec3i apartmentDoor = plan.apartment().approaches().getLast();
        Vec3i shopDoor = plan.shop().approaches().getLast();
        add(new Wait("settle", 1.5f), new Capture("01-refuge-start"),
                new Walk(court), new Capture("02-courtyard"),
                new Walk(apartmentDoor), new Capture("03-apartment-entry"),
                new Force(plan.forcedDoor(), "04-forced-door"),
                new Search(plan.locker(), Policy.TAKE_EXCEPT_LIGHT), new Search(plan.kitchen(), Policy.TAKE_ALL),
                new Search(plan.bathroom(), Policy.TAKE_ALL), new Search(plan.upstairs(), Policy.TAKE_ALL),
                new Capture("07-upper-floor"), new Hunt(), new Walk(apartmentDoor),
                new Walk(shopDoor), new Search(plan.shelf(), Policy.TAKE_FOOD_AND_WATER, "10-shop"),
                new Walk(court), new Walk(plan.home()), new Bar(), new Search(plan.store(), Policy.DEPOSIT_PLANKS),
                new Use(ItemType.DRIED_BERRY), new Use(ItemType.WATERSKIN_CLEAN), new Treat(), new Craft(),
                new Memory("route"), new Save(), new Load(), new Capture("13-after-load"), new Verify(),
                new Memory("loaded"), new Walk(court), new Walk(shopDoor), new Search(plan.shelf(), Policy.VERIFY, "14-revisit-shop"),
                new Walk(apartmentDoor), new Search(plan.locker(), Policy.VERIFY, "15-revisit-locker"),
                new Search(plan.kitchen(), Policy.VERIFY), new Encounters(),
                new Walk(court), new Walk(plan.home()), new Memory("revisit"), new Report());
        staged = true;
    }

    // ------------------------------------------------------------------
    // Tasks
    // ------------------------------------------------------------------

    private enum Policy { TAKE_ALL, TAKE_EXCEPT_LIGHT, TAKE_FOOD_AND_WATER, DEPOSIT_PLANKS, VERIFY }

    /** F to search, the crate screen's transfer commands, then F to close. */
    private final class Search extends Task {
        private final Fixture fixture;
        private final Policy policy;
        private Walk walk;
        private int phase, presses;
        private float wait;
        private boolean revealedAtOpen, counted, first;

        private final String label;

        Search(Fixture fixture, Policy policy) {
            this(fixture, policy, null);
        }

        /** {@code label} captures the open screen once its policy has been applied. */
        Search(Fixture fixture, Policy policy, String label) {
            super("search " + policy + " " + fixture.position());
            this.fixture = fixture;
            this.policy = policy;
            this.label = label;
        }

        @Override boolean threatsInterrupt() { return phase < 3 || phase == 3 && game.uiMode == Game.UiMode.NONE; }

        @Override boolean step(float dt) {
            Vec3i p = fixture.position();
            switch (phase) {
                case 0 -> {
                    if (walk == null) { walk = new Walk(fixture.target()); }
                    if (walk.step(dt)) { phase = 1; }
                }
                case 1 -> {
                    if (feet().distSq(fixture.target().x() + .5, fixture.target().y(), fixture.target().z() + .5) > 2.5) {
                        walk = null; phase = 0; return false;
                    }
                    lookAtBlock(p, .5f);
                    if (++presses > 8) { throw fail("could not open " + p); }
                    press(GLFW_KEY_F);
                    phase = 2;
                    wait = 0;
                }
                case 2 -> {
                    wait += dt;
                    if (game.uiMode == Game.UiMode.CRATE && p.equals(game.openCratePos)) {
                        revealedAtOpen = !game.crateSearching();
                        if (!revealedAtOpen && !counted) { // a resumed, interrupted search is still one search
                            counted = true;
                            first = ++searches == 1;
                        }
                        phase = 3;
                    } else if (wait > .5f) {
                        phase = 1;
                    }
                }
                case 3 -> {
                    if (game.uiMode != Game.UiMode.CRATE) { // interrupted: progress is kept on the entry
                        phase = 1;
                        return false;
                    }
                    if (game.crateSearching()) {
                        if (first && !hiddenCaptured) { capture("05-search-hidden"); hiddenCaptured = true; }
                        return false;
                    }
                    apply();
                    phase = 4;
                }
                case 4 -> {
                    press(GLFW_KEY_F);
                    phase = 5;
                    wait = 0;
                }
                default -> {
                    wait += dt;
                    if (game.uiMode == Game.UiMode.NONE) { return true; }
                    if (wait > .5f) { phase = 4; }
                }
            }
            return false;
        }

        private void apply() {
            Vec3i p = fixture.position();
            Inventory crate = game.openCrate;
            switch (policy) {
                case VERIFY -> {
                    Touched saved = touched.get(p);
                    var entry = game.world.urban.containers.at(p);
                    if (!revealedAtOpen || saved == null || entry == null || entry.id != saved.id()
                            || entry.state != saved.state() || !counts(crate).equals(saved.contents())) {
                        throw fail("refill or stale container at " + p + ": saved=" + saved + " now="
                                + (entry == null ? null : entry.state) + " " + counts(crate)
                                + " searchedAgain=" + !revealedAtOpen);
                    }
                    revisits++;
                    if (label != null) { capture(label); }
                    return;
                }
                case DEPOSIT_PLANKS -> {
                    Inventory inv = game.player.inventory;
                    for (int slot = 0; slot < inv.size(); slot++) {
                        ItemStack s = inv.get(slot);
                        if (s != null && s.type == ItemType.PLANK) { game.transferPlayerItemToCrate(slot, 2); break; }
                    }
                }
                default -> {
                    for (int slot = 0; crate != null && slot < crate.size(); slot++) {
                        ItemStack s = crate.get(slot);
                        if (s != null && takes(s.type)) { game.transferCrateItemToPlayer(slot); }
                    }
                }
            }
            if (first && !revealedCaptured) { capture("06-search-revealed"); revealedCaptured = true; }
            if (label != null) { capture(label); }
            var entry = game.world.urban.containers.at(p);
            touched.put(p, new Touched(entry.id, entry.state, counts(game.world.crateContents.get(p))));
        }

        private boolean takes(ItemType type) {
            return switch (policy) {
                case TAKE_EXCEPT_LIGHT -> type != ItemType.TORCH;
                case TAKE_FOOD_AND_WATER -> type.isEdible() || type == ItemType.WATERSKIN_CLEAN;
                default -> true;
            };
        }
    }

    private boolean hiddenCaptured, revealedCaptured;

    /** Ensures at least one infected is handled, seeking one when none came to the player. */
    private final class Hunt extends Task {
        Hunt() { super("handle an infected"); }

        @Override boolean step(float dt) {
            if (!defeated.isEmpty()) {
                hunting = false;
                return true;
            }
            if (age > HUNT_WAIT_SECONDS) { throw fail("no infected became available to handle"); }
            Npc target = null;
            double best = Double.MAX_VALUE;
            for (Npc n : game.entities.npcs) {
                double d = n.infected() && !n.dead ? n.distSqTo(game.player) : Double.MAX_VALUE;
                if (d > 64 * 64) { continue; }
                // The apartment's own resident first, then the nearest one.
                if (EncounterAnchors.resolve(game.world, n.encounterId).building() == plan.apartment().id()) { d -= 1e7; }
                if (d < best) { best = d; target = n; }
            }
            if (target != null && fighting == null) {
                fighting = target;
                fightTime = lostTime = 0;
                hunting = true;
                engaged.add(target.encounterId);
            }
            return false;
        }
    }

    /** Crouch and F on each leaf of the refuge's street door. */
    private final class Bar extends Task {
        private int leaf, phase;
        private Walk walk;

        Bar() { super("bar the street door"); }

        @Override boolean step(float dt) {
            if (leaf >= plan.streetDoors().size()) {
                hold(GLFW_KEY_LEFT_CONTROL, false);
                capture("11-barred-door");
                return true;
            }
            Fixture door = plan.streetDoors().get(leaf);
            switch (phase) {
                case 0 -> {
                    if (walk == null) { walk = new Walk(UrbanSlicePlan.inside(door)); }
                    if (walk.step(dt)) { phase = 1; }
                }
                case 1 -> {
                    hold(GLFW_KEY_LEFT_CONTROL, true);
                    phase = 2;
                }
                case 2 -> {
                    lookAtBlock(door.position(), .9f);
                    press(GLFW_KEY_F);
                    phase = 3;
                }
                default -> {
                    hold(GLFW_KEY_LEFT_CONTROL, false);
                    Vec3i p = door.position();
                    int expected = game.player.abilities.instantBuild() ? FixtureStates.LOCK_KEYED : FixtureStates.LOCK_BARRED;
                    if (game.world.fixtureLockAt(p.x(), p.y(), p.z()) != expected) {
                        throw fail("street door leaf " + p + " was not secured");
                    }
                    leaf++;
                    phase = 0;
                    walk = null;
                }
            }
            return false;
        }
    }

    /** Selects an item on the hotbar and uses it with the right mouse button, facing the floor. */
    private class Use extends Task {
        private final ItemType type;
        private int before = -1;
        private Object mark;
        private boolean clicked;

        Use(ItemType type) { super("use " + type); this.type = type; }

        @Override boolean step(float dt) {
            if (before < 0) {
                if (!select(type)) {
                    timeline.add(String.format(Locale.ROOT, "%.0f:skip-%s-not-on-hotbar", elapsed, type));
                    return true;
                }
                before = game.player.inventory.count(type);
                mark = logMark();
                game.camera.pitch = 75;
                return false;
            }
            if (age < .2f) { return false; }
            if (!clicked) {
                clicked = true;
                rightClick();
                return false;
            }
            int after = game.player.inventory.count(type);
            String feedback = type.isEdible() ? "Ate " : type == ItemType.WATERSKIN_CLEAN ? "You drink clean water" : null;
            boolean used = feedback == null || loggedSince(mark, feedback);
            boolean free = game.player.abilities.unlimitedItems();
            // Survival must consume; the Creative body is kept full and may simply decline, consuming nothing.
            if (!free && (!used || after >= before) || free && after != before) {
                throw fail("using " + type + " did not behave: used=" + used + " before=" + before + " after=" + after);
            }
            timeline.add(String.format(Locale.ROOT, "%.0f:%s-%s", elapsed, type, used ? "used" : "declined-full"));
            return true;
        }
    }

    /** Bandages a bleeding wound; nothing to do when uninjured. */
    private final class Treat extends Use {
        Treat() { super(ItemType.BANDAGE); }
        @Override boolean step(float dt) {
            return !game.player.has(com.veylon.entity.Affliction.BLEEDING) || super.step(dt);
        }
    }

    /** F on the workbench, the screen's craft command, then C to close. */
    private final class Craft extends Task {
        private Walk walk;
        private int phase;

        Craft() { super("craft the first upgrade"); }

        @Override boolean step(float dt) {
            switch (phase) {
                case 0 -> {
                    if (walk == null) { walk = new Walk(plan.workbench().target()); }
                    if (walk.step(dt)) { phase = 1; }
                }
                case 1 -> {
                    lookAtBlock(plan.workbench().position(), .5f);
                    press(GLFW_KEY_F);
                    phase = 2;
                }
                case 2 -> {
                    if (game.uiMode != Game.UiMode.CRAFTING) {
                        if (age > 8) { throw fail("the workbench did not open"); }
                        phase = 1;
                        return false;
                    }
                    String made = CraftingScreen.craft(game, UrbanCrafting.RECIPES.getFirst(), game.nearbyStations());
                    if (made == null || game.player.inventory.count(ItemType.IRON_SPEAR) < 1) {
                        throw fail("the reinforced spear could not be crafted: " + inventory());
                    }
                    capture("12-craft");
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

    /** F5 through the hotkeys, after recording what the save must preserve. */
    private final class Save extends Task {
        private boolean pressed;
        private Object mark;
        Save() { super("quick-save"); }
        @Override boolean step(float dt) {
            if (!pressed) {
                for (Fixture door : plan.streetDoors()) {
                    Vec3i p = door.position();
                    doorLocks.put(p, game.world.fixtureLockAt(p.x(), p.y(), p.z()));
                }
                for (Fixture door : plan.apartment().fixtures()) {
                    Vec3i p = door.position();
                    if (door.kind() == com.veylon.world.urban.BuildingPlan.FixtureKind.DOOR && p.y() == plan.locker().position().y()) {
                        doorBlocks.put(p, game.world.getBlock(p.x(), p.y(), p.z()));
                    }
                }
                for (Vec3i p : touched.keySet()) {
                    var entry = game.world.urban.containers.at(p);
                    touched.put(p, new Touched(entry.id, entry.state, counts(game.world.crateContents.get(p))));
                }
                savedWorld = game.world;
                savedPlayer = game.player;
                savedNpcs = List.copyOf(game.entities.npcs);
                savedAnchor = game.world.urban.refugeAnchor;
                mark = logMark();
                press(GLFW_KEY_F5);
                pressed = true;
                return false;
            }
            if (!loggedSince(mark, "Game saved.")) { throw fail("quick-save failed: " + lastLog()); }
            return true;
        }
        @Override boolean threatsInterrupt() { return false; }
    }

    /** F9 over the live world. */
    private final class Load extends Task {
        private boolean pressed;
        private Object mark;
        Load() { super("quick-load over the live world"); }
        @Override boolean step(float dt) {
            if (!pressed) {
                mark = logMark();
                press(GLFW_KEY_F9);
                pressed = true;
                return false;
            }
            if (!loggedSince(mark, "Game loaded.") || game.world == savedWorld || game.player == savedPlayer) {
                throw fail("quick-load did not replace the live world: " + lastLog());
            }
            plan = UrbanSlicePlan.of(game.world);
            fighting = null;
            approach = null;
            return true;
        }
        @Override boolean threatsInterrupt() { return false; }
    }

    /** No stale screen, search, body or reference; the save's facts reloaded exactly. */
    private final class Verify extends Task {
        Verify() { super("verify the reloaded world"); }
        @Override boolean step(float dt) {
            List<String> stale = new ArrayList<>();
            if (game.uiMode != Game.UiMode.NONE || game.openCrate != null || game.openCratePos != null
                    || game.crateSearching()) { stale.add("screen-or-search"); }
            for (Npc n : game.entities.npcs) {
                if (savedNpcs.contains(n)) { stale.add("old-body"); }
            }
            if (!game.entities.encounters.restored) { stale.add("encounters-not-restored"); }
            for (long id : defeated) {
                var r = game.entities.encounters.resident(id);
                if (r == null || !r.dead()) { stale.add("resurrected " + id); }
            }
            for (var e : touched.entrySet()) {
                var entry = game.world.urban.containers.at(e.getKey());
                if (entry == null || entry.id != e.getValue().id() || entry.state != e.getValue().state()
                        || !counts(game.world.crateContents.get(e.getKey())).equals(e.getValue().contents())) {
                    stale.add("container " + e.getKey());
                }
            }
            for (var e : doorLocks.entrySet()) {
                Vec3i p = e.getKey();
                if (game.world.fixtureLockAt(p.x(), p.y(), p.z()) != e.getValue()) { stale.add("lock " + p); }
            }
            for (var e : doorBlocks.entrySet()) {
                Vec3i p = e.getKey();
                game.world.ensureChunks(p.x(), p.z(), 1, 9);
                if (game.world.getBlock(p.x(), p.y(), p.z()) != e.getValue()) { stale.add("door " + p); }
            }
            if (!savedAnchor.equals(game.world.urban.refugeAnchor)) { stale.add("refuge-anchor"); }
            if (game.player.inventory.count(ItemType.IRON_SPEAR) < 1) { stale.add("upgrade-lost"); }
            if (!RuntimeBudgetSnapshot.capture(game).withinHardLimits()) { stale.add("runtime-bounds"); }
            if (!stale.isEmpty()) { throw fail("reloaded world differs: " + stale); }
            savedWorld = null; // let the replaced world go before the memory checkpoints
            savedPlayer = null;
            savedNpcs = List.of();
            return true;
        }
        @Override boolean threatsInterrupt() { return false; }
    }

    /** Defeated encounters stay tombstones after the revisit's slow ticks. */
    private final class Encounters extends Task {
        Encounters() { super("check defeated encounters"); }
        @Override boolean step(float dt) {
            for (long id : defeated) {
                var r = game.entities.encounters.resident(id);
                boolean live = game.entities.npcs.stream().anyMatch(n -> n.encounterId == id && !n.dead);
                if (r == null || !r.dead() || live
                        || game.entities.encounters.tryActivate(game, EncounterAnchors.resolve(game.world, id)) != null) {
                    throw fail("encounter " + id + " came back");
                }
            }
            return true;
        }
        @Override boolean threatsInterrupt() { return false; }
    }

    /** Retained-chunk and heap accounting at a route checkpoint (a host measurement, not a target). */
    private final class Memory extends Task {
        Memory(String phase) { super(phase); }
        @Override boolean step(float dt) {
            System.gc();
            MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            World w = game.world;
            int meshed = 0;
            for (Chunk c : w.loadedChunks()) {
                if (c.meshOpaque != null || c.meshWater != null) { meshed++; }
            }
            var runtime = RuntimeBudgetSnapshot.capture(game);
            System.out.printf(Locale.ROOT, "[urban-slice] memory phase=%s loadedChunks=%d meshedChunks=%d"
                            + " chunkArrayBytes=%d containers=%d crates=%d edits=%d fixtures=%d routes=%d"
                            + " encounters=%d regionPlans=%d buildingPlans=%d heapUsedMiB=%.1f"
                            + " heapCommittedMiB=%.1f heapMaxMiB=%.1f heapSample=post-gc-best-effort"
                            + " withinHardLimits=%s runtime=%s%n",
                    name, w.loadedCount(), meshed, (long) w.loadedCount() * (Chunk.SX * Chunk.SY * Chunk.SZ + 2 * 256 * 4),
                    w.urban.containers.size(), w.crateContents.size(), w.changedBlocks.size(), w.fixtures.size(),
                    w.navigation.size(), game.entities.encounters.size(), w.urban.cachedRegionPlans(),
                    w.urban.cachedBuildingPlans(), heap.getUsed() / 1048576.0, heap.getCommitted() / 1048576.0,
                    heap.getMax() / 1048576.0, runtime.withinHardLimits(), runtime.occupancySummary());
            return true;
        }
        @Override boolean threatsInterrupt() { return false; }
    }

    private final class Report extends Task {
        Report() { super("report"); }
        @Override boolean step(float dt) {
            releaseAll();
            passed = true;
            reported = true;
            System.out.printf(Locale.ROOT, "[urban-slice] pass=true mode=%s seed=%d apartment=%d searches=%d"
                            + " revisits=%d engaged=%s defeated=%s forced=%s upgrade=%d refill=false resurrection=false"
                            + " staleState=false damageTaken=%.1f harmEvents=%d walkedBlocks=%.0f gameMinutes=%.0f"
                            + " routeSeconds=%.0f minHealth=%.1f minBodyTemp=%.1f events=%s afflictions=%s inventory=%s%n",
                    game.gameMode().name().toLowerCase(Locale.ROOT), game.world.seed, plan.apartment().id(),
                    searches, revisits, engaged, defeated, forcedAt, game.player.inventory.count(ItemType.IRON_SPEAR),
                    damage, harm, walked, game.time.totalMinutes - gameMinutesAtStart, elapsed,
                    minHealth, minBodyTemp, eventsSeen, afflictionsSeen,
                    inventory());
            System.out.println("[urban-slice] timeline " + String.join(" ", timeline));
            capture("16-end");
            game.automation.finishScriptedRun();
            return true;
        }
        @Override boolean threatsInterrupt() { return false; }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    @Override protected void observe() {
        Player p = game.player;
        if (p != observed) { // a quick-load replaces the player: carry the totals over
            observed = p;
            lastHarm = p.harmEvents;
        }
        harm += p.harmEvents - lastHarm;
        lastHarm = p.harmEvents;
        if (lastHealth >= 0 && p.health < lastHealth) { damage += lastHealth - p.health; }
        lastHealth = p.health;
        minHealth = Math.min(minHealth, p.health);
        minBodyTemp = Math.min(minBodyTemp, p.bodyTemp);
        for (var e : game.events.active) { eventsSeen.add(e.type.name()); }
        for (var a : p.afflictions.keySet()) { afflictionsSeen.add(a.name()); }
        float dx = p.pos.x - lastX, dz = p.pos.z - lastZ;
        float step = (float) Math.hypot(dx, dz);
        if (step < 2) { walked += step; } // a reload restores a saved position, not a walk
        lastX = p.pos.x;
        lastZ = p.pos.z;
    }

    private static Map<ItemType, Integer> counts(Inventory inv) {
        Map<ItemType, Integer> m = new EnumMap<>(ItemType.class);
        for (int i = 0; inv != null && i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s != null) { m.merge(s.type, s.count, Integer::sum); }
        }
        return m;
    }

    private String inventory() {
        return counts(game.player.inventory).toString();
    }

    /** The newest log line by identity: a later line is a new object even when its text repeats. */
    private Object logMark() {
        var all = game.eventLog.all();
        return all.isEmpty() ? null : all.getLast();
    }

    private boolean loggedSince(Object mark, String text) {
        var all = game.eventLog.all();
        for (int i = all.size() - 1; i >= 0 && all.get(i) != mark; i--) {
            if (all.get(i).contains(text)) { return true; }
        }
        return false;
    }

    private String lastLog() {
        var all = game.eventLog.all();
        return all.isEmpty() ? "" : all.getLast();
    }
}
