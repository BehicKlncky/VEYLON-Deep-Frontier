package com.veylon;

import com.veylon.entity.Npc;
import com.veylon.entity.Player;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Fixtures;
import com.veylon.world.VoxelClearance;
import com.veylon.world.urban.BuildingPlan.Fixture;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

import static org.lwjgl.glfw.GLFW.*;

/**
 * A scripted urban QA route played with ordinary controls (milestones 11 and 12). Held keys
 * and mouse buttons go through the production {@link com.veylon.engine.Input} seam, the camera
 * turns as mouse look would, and the normal frame step owns every outcome. A route is a queue
 * of tasks; a pursuing infected interrupts walking until it is handled. Failures throw.
 *
 * <p>Subclasses supply the tasks, their log tag and deadline, and may observe each frame.
 */
abstract class UrbanRouteScene {
    private static final float MAX_STEP = 0.1f;
    private static final float THREAT_RADIUS = 7f, STRIKE_REACH = 2.6f;
    private static final float FIGHT_SECONDS = 45f, LOST_SECONDS = 4f;

    protected final Game game;
    private final ArrayDeque<Task> tasks = new ArrayDeque<>();
    private Task current;
    protected boolean staged, reported;
    protected double elapsed;
    private double last = -1;
    protected final List<String> timeline = new ArrayList<>();

    // Input the runner holds through the production seam.
    private final BitSet heldKeys = new BitSet();
    private final List<Integer> releaseKeys = new ArrayList<>(4);
    protected boolean leftHeld;
    private boolean rightRelease;

    // Threat handling.
    protected Npc fighting;
    protected float fightTime, lostTime;
    protected boolean hunting;
    protected final Set<Long> engaged = new LinkedHashSet<>(), defeated = new LinkedHashSet<>();
    protected Walk approach;
    private Npc approachFor;
    private float approachAge;
    private boolean threatCaptured;
    /** The last door a {@link Force} task broke open. */
    protected Vec3i forcedAt;

    UrbanRouteScene(Game game) {
        this.game = game;
    }

    /** The log tag, for example {@code urban-slice}. */
    protected abstract String tag();

    /** Wall-clock seconds in a native session, simulated seconds in a headless route. */
    protected abstract double deadline();

    /** Called once per frame before threats and tasks run. */
    protected void observe() { }

    /** The first infected a fight brings within reach. */
    protected void threatInReach() { }

    /** An infected this route was fighting has died. */
    protected void defeatedThreat() { }

    boolean reported() { return reported; }

    /** Per-frame hook, before the frame step reads input; inert until staged. */
    void update(double now) {
        if (!staged || reported) {
            return;
        }
        float dt = last < 0 ? 0f : (float) Math.min(MAX_STEP, Math.max(0, now - last));
        last = now;
        elapsed += dt;
        releaseQueued();
        if (elapsed > deadline()) {
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

    protected void hold(int key, boolean down) {
        if (heldKeys.get(key) == down) { return; }
        heldKeys.set(key, down);
        game.input.onKey(key, down ? GLFW_PRESS : GLFW_RELEASE);
    }

    /** One press seen by this frame; released before the next. */
    protected void press(int key) {
        game.input.onKey(key, GLFW_PRESS);
        releaseKeys.add(key);
    }

    protected void leftMouse(boolean down) {
        if (leftHeld == down) { return; }
        leftHeld = down;
        game.input.onMouseButton(GLFW_MOUSE_BUTTON_LEFT, down ? GLFW_PRESS : GLFW_RELEASE);
    }

    protected void rightClick() {
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

    protected void releaseAll() {
        for (int key = heldKeys.nextSetBit(0); key >= 0; key = heldKeys.nextSetBit(key + 1)) {
            game.input.onKey(key, GLFW_RELEASE);
        }
        heldKeys.clear();
        leftMouse(false);
        releaseQueued();
    }

    /** Mouse look toward a point from the player's eye. */
    protected void look(float x, float y, float z) {
        Player p = game.player;
        float dx = x - p.pos.x, dy = y - (p.pos.y + p.eyeHeight()), dz = z - p.pos.z;
        game.camera.yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
        game.camera.pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
    }

    protected void lookAtBlock(Vec3i b, float height) {
        look(b.x() + .5f, b.y() + height, b.z() + .5f);
    }

    /** Digit key for the hotbar slot holding {@code type}, or false when it is not on the hotbar. */
    protected boolean select(ItemType type) {
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

    protected void selectWeapon() {
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
            defeatedThreat();
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
            threatInReach();
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

    protected abstract static class Task {
        final String name;
        float age;
        Task(String name) { this.name = name; }
        /** @return true once complete */
        abstract boolean step(float dt);
        boolean threatsInterrupt() { return true; }
    }

    protected void add(Task... list) {
        tasks.addAll(List.of(list));
    }

    protected final class Wait extends Task {
        final float seconds;
        Wait(String name, float seconds) { super("wait " + name); this.seconds = seconds; }
        @Override boolean step(float dt) { return age >= seconds; }
    }

    protected final class Capture extends Task {
        Capture(String label) { super("capture " + label); }
        @Override boolean step(float dt) {
            capture(name.substring("capture ".length()));
            return true;
        }
        @Override boolean threatsInterrupt() { return false; }
    }

    /**
     * Walks a bounded route with W and mouse look, opening closed doors with F on the way; a
     * route may be kept to the cells a predicate allows.
     */
    protected final class Walk extends Task {
        final Vec3i goal;
        private final Predicate<Vec3i> allowed;
        private List<Vec3i> path;
        private int index, replans, doorPresses;
        private float stuck, best = Float.MAX_VALUE, doorWait;

        Walk(Vec3i goal) { this(goal, cell -> true); }

        Walk(Vec3i goal, Predicate<Vec3i> allowed) { super("walk " + goal); this.goal = goal; this.allowed = allowed; }

        @Override boolean step(float dt) {
            if (path == null) {
                path = UrbanSlicePlan.route(game.world, feet(), goal, allowed);
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

    /** Holds the left mouse button on a closed door until it gives way, then captures it. */
    protected final class Force extends Task {
        private final Fixture door;
        private final String label;
        private Walk walk;
        private float closeWait;

        Force(Fixture door, String label) { super("force " + door.position()); this.door = door; this.label = label; }

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
            capture(label);
            timeline.add(String.format(Locale.ROOT, "%.0f:forced=%s", elapsed, block));
            return true;
        }

        @Override boolean threatsInterrupt() { return !leftHeld; }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    protected void capture(String label) {
        game.automation.requestCapture(label);
        System.out.println("[" + tag() + "] capture=" + label + " t=" + String.format(Locale.ROOT, "%.1f", elapsed)
                + " pos=" + game.player.pos + " screen=" + game.uiMode);
    }

    protected Vec3i feet() {
        Player p = game.player;
        int x = (int) Math.floor(p.pos.x), z = (int) Math.floor(p.pos.z), y = (int) Math.floor(p.pos.y + .01f);
        for (int dy : new int[]{0, 1, -1}) {
            if (VoxelClearance.standable(game.world, x, y + dy, z)) { return new Vec3i(x, y + dy, z); }
        }
        return new Vec3i(x, y, z);
    }

    protected static Vec3i cell(float x, float y, float z) {
        return new Vec3i((int) Math.floor(x), (int) Math.floor(y + .01f), (int) Math.floor(z));
    }

    protected float horizontalSpeed() {
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

    protected IllegalStateException fail(String reason) {
        releaseAll();
        reported = true;
        System.out.println("[" + tag() + "] pass=false reason=" + reason + " mode="
                + game.gameMode().name().toLowerCase(Locale.ROOT) + " seed=" + game.world.seed
                + " pos=" + game.player.pos + " timeline " + String.join(" ", timeline));
        return new IllegalStateException(tag().replace('-', ' ') + ": " + reason);
    }
}
