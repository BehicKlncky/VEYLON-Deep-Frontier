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
import com.veylon.world.Fixtures;
import com.veylon.world.VoxelClearance;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan.Fixture;
import com.veylon.world.urban.EncounterAnchors;
import com.veylon.world.urban.UrbanContainers;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
final class UrbanSliceQaScene {
    /** Wall-clock seconds in a native session, simulated seconds in a headless route. */
    static final double DEADLINE_SECONDS = 900;
    private static final float MAX_STEP = 0.1f;
    private static final float THREAT_RADIUS = 7f, STRIKE_REACH = 2.6f;
    private static final float FIGHT_SECONDS = 45f, LOST_SECONDS = 4f, HUNT_WAIT_SECONDS = 60f;

    private final Game game;
    private final ArrayDeque<Task> tasks = new ArrayDeque<>();
    private Task current;
    private UrbanSlicePlan plan;
    private boolean staged, reported, passed;
    private double last = -1, elapsed;
    private float gameMinutesAtStart;

    // Input the runner holds through the production seam.
    private final BitSet heldKeys = new BitSet();
    private final List<Integer> releaseKeys = new ArrayList<>(4);
    private boolean leftHeld, rightRelease;

    // Threat handling and route observations.
    private Npc fighting;
    private float fightTime, lostTime;
    private final Set<Long> engaged = new LinkedHashSet<>(), defeated = new LinkedHashSet<>();
    private float walked, damage, lastX, lastZ, lastHealth = -1;
    private int searches, revisits, harm, lastHarm;
    private Player observed;
    private float minHealth = Float.MAX_VALUE, minBodyTemp = Float.MAX_VALUE;
    private final Set<String> eventsSeen = new java.util.TreeSet<>(), afflictionsSeen = new java.util.TreeSet<>();
    private final List<String> timeline = new ArrayList<>();

    // State saved by the quick-save, compared after the quick-load and on the revisit.
    private final Map<Vec3i, Touched> touched = new LinkedHashMap<>();
    private final Map<Vec3i, BlockType> doorBlocks = new LinkedHashMap<>();
    private final Map<Vec3i, Integer> doorLocks = new LinkedHashMap<>();
    private Vec3i forcedAt;
    private World savedWorld;
    private Player savedPlayer;
    private List<Npc> savedNpcs = List.of();
    private Vec3i savedAnchor;

    private record Touched(long id, UrbanContainers.State state, Map<ItemType, Integer> contents) { }

    UrbanSliceQaScene(Game game) {
        this.game = game;
    }

    boolean passed() { return passed; }
    boolean reported() { return reported; }
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
                new Force(plan.forcedDoor()),
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

    /** Per-frame hook, before the frame step reads input; inert until staged. */
    void update(double now) {
        if (!staged || reported) {
            return;
        }
        float dt = last < 0 ? 0f : (float) Math.min(MAX_STEP, Math.max(0, now - last));
        last = now;
        elapsed += dt;
        releaseQueued();
        if (elapsed > DEADLINE_SECONDS) {
            throw fail("deadline at " + (current == null ? "start" : current.name));
        }
        if (game.player.dead || game.appState == Game.AppState.DEATH) {
            throw fail("the player died at " + (current == null ? "start" : current.name));
        }
        observe();
        if (game.uiMode == Game.UiMode.NONE && (current == null || current.threatsInterrupt()) && fight(dt)) {
            return;
        }
        if (current == null) {
            current = tasks.poll();
            if (current == null) { return; }
            timeline.add(String.format(Locale.ROOT, "%.0f:%s", elapsed, current.name));
        }
        current.age += dt;
        if (current.step(dt)) {
            current = null;
        }
    }

    // ------------------------------------------------------------------
    // Input through the production seam
    // ------------------------------------------------------------------

    private void hold(int key, boolean down) {
        if (heldKeys.get(key) == down) { return; }
        heldKeys.set(key, down);
        game.input.onKey(key, down ? GLFW_PRESS : GLFW_RELEASE);
    }

    /** One press seen by this frame; released before the next. */
    private void press(int key) {
        game.input.onKey(key, GLFW_PRESS);
        releaseKeys.add(key);
    }

    private void leftMouse(boolean down) {
        if (leftHeld == down) { return; }
        leftHeld = down;
        game.input.onMouseButton(GLFW_MOUSE_BUTTON_LEFT, down ? GLFW_PRESS : GLFW_RELEASE);
    }

    private void rightClick() {
        game.input.onMouseButton(GLFW_MOUSE_BUTTON_RIGHT, GLFW_PRESS);
        rightRelease = true;
    }

    private void releaseQueued() {
        for (int key : releaseKeys) { game.input.onKey(key, GLFW_RELEASE); }
        releaseKeys.clear();
        if (rightRelease) {
            game.input.onMouseButton(GLFW_MOUSE_BUTTON_RIGHT, GLFW_RELEASE);
            rightRelease = false;
        }
    }

    private void releaseAll() {
        for (int key = heldKeys.nextSetBit(0); key >= 0; key = heldKeys.nextSetBit(key + 1)) {
            game.input.onKey(key, GLFW_RELEASE);
        }
        heldKeys.clear();
        leftMouse(false);
        releaseQueued();
    }

    /** Mouse look toward a point from the player's eye. */
    private void look(float x, float y, float z) {
        Player p = game.player;
        float dx = x - p.pos.x, dy = y - (p.pos.y + p.eyeHeight()), dz = z - p.pos.z;
        game.camera.yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
        game.camera.pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
    }

    private void lookAtBlock(Vec3i b, float height) {
        look(b.x() + .5f, b.y() + height, b.z() + .5f);
    }

    /** Digit key for the hotbar slot holding {@code type}, or false when it is not on the hotbar. */
    private boolean select(ItemType type) {
        Inventory inv = game.player.inventory;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack s = inv.get(slot);
            if (s != null && s.type == type) {
                if (game.player.hotbarSel != slot) { press(GLFW_KEY_1 + slot); }
                return true;
            }
        }
        return false;
    }

    private void selectWeapon() {
        if (select(ItemType.IRON_SPEAR) || select(ItemType.IRON_PICKAXE)) { return; }
        Inventory inv = game.player.inventory;
        for (int slot = 8; slot >= 0; slot--) {
            if (inv.get(slot) == null) {
                if (game.player.hotbarSel != slot) { press(GLFW_KEY_1 + slot); }
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // Threats: a pursuing infected interrupts walking until it is handled
    // ------------------------------------------------------------------

    private boolean fight(float dt) {
        if (fighting == null) {
            fighting = threat();
            if (fighting == null) { return false; }
            fightTime = lostTime = 0;
            engaged.add(fighting.encounterId);
            hold(GLFW_KEY_W, false);
        }
        Npc n = fighting;
        if (n.dead) {
            defeated.add(n.encounterId);
            leftMouse(false);
            fighting = null;
            approach = null;
            if (defeated.size() == 1) { capture("09-defeated"); }
            return true;
        }
        if (!game.entities.npcs.contains(n)) { // dormant or unloaded: no longer here
            leftMouse(false);
            fighting = null;
            approach = null;
            return false;
        }
        fightTime += dt;
        if (fightTime > FIGHT_SECONDS) { throw fail("fight did not resolve with " + n.encounterId); }
        selectWeapon();
        float dx = n.pos.x - game.player.pos.x, dz = n.pos.z - game.player.pos.z;
        float dist = (float) Math.hypot(dx, dz);
        lostTime = n.searchTimer <= 0 && dist > 6 ? lostTime + dt : 0;
        if (lostTime > LOST_SECONDS && !hunting) {
            leftMouse(false);
            fighting = null;
            approach = null;
            return false;
        }
        look(n.pos.x, n.pos.y + n.height * .5f, n.pos.z);
        boolean inReach = dist <= STRIKE_REACH && Math.abs(n.pos.y - game.player.pos.y) < 1.2f;
        leftMouse(inReach);
        if (inReach && !threatCaptured) { // the first infected faced within reach
            capture("08-threat");
            threatCaptured = true;
        }
        if (!inReach) {
            game.camera.pitch = 6;
            Vec3i target = cell(n.pos.x, n.pos.y, n.pos.z);
            approachAge += dt;
            // A fresh walker per target, re-planned once the target has moved on or the route ended.
            if (approach == null || approachFor != n || approachAge > .75f && !target.equals(approach.goal)) {
                approach = new Walk(target);
                approachFor = n;
                approachAge = 0;
            }
            if (approach.step(dt)) { approach = null; }
        } else {
            hold(GLFW_KEY_W, false);
        }
        return true;
    }

    private boolean hunting, threatCaptured;
    private Walk approach;
    private Npc approachFor;
    private float approachAge;

    private Npc threat() {
        if (!game.player.isPerceivableByAi()) { return null; }
        Npc best = null;
        float bestD = Float.MAX_VALUE;
        for (Npc n : game.entities.npcs) {
            if (!n.infected() || n.dead) { continue; }
            float dx = n.pos.x - game.player.pos.x, dz = n.pos.z - game.player.pos.z;
            float d = dx * dx + dz * dz;
            boolean pursuing = n.searchTimer > 0 && d < THREAT_RADIUS * THREAT_RADIUS;
            if (Math.abs(n.pos.y - game.player.pos.y) < 2.5f && (pursuing || d < 6.25f) && d < bestD) {
                bestD = d;
                best = n;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Tasks
    // ------------------------------------------------------------------

    private abstract static class Task {
        final String name;
        float age;
        Task(String name) { this.name = name; }
        /** @return true once complete */
        abstract boolean step(float dt);
        boolean threatsInterrupt() { return true; }
    }

    private void add(Task... list) {
        tasks.addAll(List.of(list));
    }

    private final class Wait extends Task {
        final float seconds;
        Wait(String name, float seconds) { super("wait " + name); this.seconds = seconds; }
        @Override boolean step(float dt) { return age >= seconds; }
    }

    private final class Capture extends Task {
        Capture(String label) { super("capture " + label); }
        @Override boolean step(float dt) {
            capture(name.substring("capture ".length()));
            return true;
        }
        @Override boolean threatsInterrupt() { return false; }
    }

    /** Walks a bounded route with W and mouse look, opening closed doors with F on the way. */
    private final class Walk extends Task {
        private final Vec3i goal;
        private List<Vec3i> path;
        private int index, replans, doorPresses;
        private float stuck, best = Float.MAX_VALUE, doorWait;

        Walk(Vec3i goal) { super("walk " + goal); this.goal = goal; }

        @Override boolean step(float dt) {
            if (path == null) {
                path = UrbanSlicePlan.route(game.world, feet(), goal);
                if (path == null) { throw fail("no walking route from " + feet() + " to " + goal); }
                index = 0; best = Float.MAX_VALUE; stuck = 0;
            }
            if (index >= path.size()) {
                hold(GLFW_KEY_W, false);
                return horizontalSpeed() < .5f;
            }
            Vec3i next = path.get(index);
            Vec3i door = closedDoor(next);
            if (door != null) {
                hold(GLFW_KEY_W, false);
                lookAtBlock(door, .9f);
                doorWait += dt;
                if (doorWait > .35f) {
                    if (++doorPresses > 12) { throw fail("door " + door + " never opened"); }
                    press(GLFW_KEY_F);
                    doorWait = 0;
                }
                return false;
            }
            doorWait = 0;
            float dx = next.x() + .5f - game.player.pos.x, dz = next.z() + .5f - game.player.pos.z;
            float d = (float) Math.hypot(dx, dz);
            boolean finalCell = index == path.size() - 1;
            if (d < (finalCell ? .3f : .45f) && Math.abs(game.player.pos.y - next.y()) < .75f) {
                index++;
                best = Float.MAX_VALUE;
                stuck = 0;
                return false;
            }
            game.camera.yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
            game.camera.pitch = 8;
            hold(GLFW_KEY_W, true);
            if (d < best - .05f) {
                best = d;
                stuck = 0;
            } else if ((stuck += dt) > 2.5f) {
                if (++replans > 8) { throw fail("stuck walking to " + goal + " at " + game.player.pos); }
                hold(GLFW_KEY_W, false);
                path = null;
            }
            return false;
        }
    }

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

    /** Holds the left mouse button on a closed interior door until it gives way. */
    private final class Force extends Task {
        private final Fixture door;
        private Walk walk;
        private float closeWait;

        Force(Fixture door) { super("force " + door.position()); this.door = door; }

        @Override boolean step(float dt) {
            Vec3i p = door.position();
            if (walk == null) {
                Vec3i a = door.target(), b = UrbanSlicePlan.inside(door);
                List<Vec3i> ra = UrbanSlicePlan.route(game.world, feet(), a), rb = UrbanSlicePlan.route(game.world, feet(), b);
                walk = new Walk(rb == null || ra != null && ra.size() <= rb.size() ? a : b);
            }
            if (!walk.step(dt)) { return false; }
            BlockType block = game.world.getBlock(p.x(), p.y(), p.z());
            if (age > 30) { throw fail("door " + p + " did not give way: " + block); }
            if (Fixtures.isClosedDoor(block)) {
                selectWeapon();
                lookAtBlock(p, .9f);
                leftMouse(true);
                return false;
            }
            if (Fixtures.isOpen(block)) { // an open leaf would only be dismantled: close it first
                leftMouse(false);
                lookAtBlock(p, .9f);
                if ((closeWait += dt) > .4f) {
                    press(GLFW_KEY_F);
                    closeWait = 0;
                }
                return false;
            }
            leftMouse(false);
            forcedAt = p;
            capture("04-forced-door");
            timeline.add(String.format(Locale.ROOT, "%.0f:forced=%s", elapsed, block));
            return true;
        }

        @Override boolean threatsInterrupt() { return !leftHeld; }
    }

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

    private void observe() {
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

    private void capture(String label) {
        game.automation.requestCapture(label);
        System.out.println("[urban-slice] capture=" + label + " t=" + String.format(Locale.ROOT, "%.1f", elapsed)
                + " pos=" + game.player.pos + " screen=" + game.uiMode);
    }

    private Vec3i feet() {
        Player p = game.player;
        int x = (int) Math.floor(p.pos.x), z = (int) Math.floor(p.pos.z), y = (int) Math.floor(p.pos.y + .01f);
        for (int dy : new int[]{0, 1, -1}) {
            if (VoxelClearance.standable(game.world, x, y + dy, z)) { return new Vec3i(x, y + dy, z); }
        }
        return new Vec3i(x, y, z);
    }

    private static Vec3i cell(float x, float y, float z) {
        return new Vec3i((int) Math.floor(x), (int) Math.floor(y + .01f), (int) Math.floor(z));
    }

    private float horizontalSpeed() {
        return (float) Math.hypot(game.player.vel.x, game.player.vel.z);
    }

    private Vec3i closedDoor(Vec3i cell) {
        for (int dy = 0; dy <= 1; dy++) {
            if (Fixtures.isClosedDoor(game.world.getBlock(cell.x(), cell.y() + dy, cell.z()))) {
                return cell.offset(0, dy, 0);
            }
        }
        return null;
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

    private IllegalStateException fail(String reason) {
        releaseAll();
        reported = true;
        System.out.println("[urban-slice] pass=false reason=" + reason + " mode="
                + game.gameMode().name().toLowerCase(Locale.ROOT) + " seed=" + game.world.seed
                + " pos=" + game.player.pos + " timeline " + String.join(" ", timeline));
        return new IllegalStateException("urban slice: " + reason);
    }
}
