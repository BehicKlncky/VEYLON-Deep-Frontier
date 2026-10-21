package com.veylon;

import com.veylon.entity.Carcass;
import com.veylon.entity.Creature;
import com.veylon.entity.Creature.CreatureState;
import com.veylon.entity.Entity;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.EncounterAnchors;
import com.veylon.world.urban.Habitat;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanPlaces.Place;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The milestone-15 ecology tour ({@code VEYLON_SCENE=urban_ecology}) on a generator-14 world
 * from {@code VEYLON_GENERATOR=urban-dev}, in either game mode, played with ordinary controls (W,
 * S, crouch, F, F5, F9, right click and mouse look through the production input seam):
 * <ol>
 *   <li>by day, the nearest growth site: its folded Veilmaw, a crouched approach onto the
 *       membrane that does not wake it, then (Survival) one uncrouched step back that does: the
 *       telegraph, and the lunge or let-go that a player backing away escapes; in Creative the
 *       same body ignores the unseen player beside it;</li>
 *   <li>a torch set beside the node: the node burns, the Veilmaw withers, its membrane sacs are
 *       cut from the remains with F, and after a quick-save and quick-load it stays dead and the
 *       node stays gone;</li>
 *   <li>at night, the second site's glow, and a torch set inside its ground from beyond it: the
 *       Veilmaw creeps to the light and smothers it;</li>
 *   <li>by day, a park's animals: a hare crept up on crouched bolts when the player stands
 *       (Survival), and every animal stays on the park's ground; and a roof flock over a
 *       walk-up;</li>
 *   <li>a Breacher at its street door, blind to a crouched observer.</li>
 * </ol>
 * Bodies are admitted through the production gate from a staged observer pose; that admission,
 * the poses between legs, the clock, the weather, the fire stream's seed and a kit of a spear and
 * torches are staged and printed as staged. No block is staged. Exactly sixteen labelled
 * captures. Failures throw.
 */
final class UrbanEcologyQaScene extends UrbanRouteScene {
    static final double DEADLINE_SECONDS = 900;
    static final int CAPTURES = 16;

    private UrbanEcologyTourPlan plan;
    private boolean passed, telegraphed, escaped, unseen, burned, withered, harvested, reloaded, lured, smothered,
            parkKept, flockKept, breacherBlind;
    private boolean lunged;
    private int poses, captures, admitted;
    private float struck;
    private final List<String> views = new ArrayList<>();

    UrbanEcologyQaScene(Game game) { super(game); }

    @Override protected String tag() { return "urban-ecology"; }
    @Override protected double deadline() { return DEADLINE_SECONDS; }

    boolean passed() { return passed; }
    UrbanEcologyTourPlan plan() { return plan; }

    @Override
    protected void capture(String label) {
        captures++;
        super.capture(label);
    }

    private boolean survival() { return game.gameMode() == GameMode.SURVIVAL; }

    /** Plans the tour on the current generated world and stages the kit; no block is changed. */
    void stage() {
        plan = UrbanEcologyTourPlan.of(game.world);
        Inventory inv = game.player.inventory;
        inv.set(7, new ItemStack(ItemType.IRON_SPEAR, 1));
        inv.set(6, new ItemStack(ItemType.TORCH, 4));
        game.fire.setRandomSeed(game.world.seed);
        System.out.println("[urban-ecology] stage seed=" + game.world.seed + " generator=" + game.world.generatorVersion
                + " mode=" + game.gameMode().name().toLowerCase(Locale.ROOT)
                + " evidence=walked-legs(W,S,crouch,F,F5,F9,right-click,mouse-look); staged-admission(production-gate),"
                + "staged-poses-between-legs,staged-clock,staged-fire-seed,staged-kit(spear,torches); generated-geometry;"
                + " normal-simulation");
        System.out.println("[urban-ecology] plan " + plan);
        var a = plan.first();
        Vec3i node = a.site().node();
        add(new Clock("day", 11 * 60), new Arrive(List.of(a.anchor()), a.stand(), node), new Wait("stream", 1f),
                new Look("01-growth-site-by-day", a, null, CreatureState.FOLDED),
                new Crouch(true), new Walk(a.quiet(), a.clear()),
                new Look("02-crouched-on-the-membrane", a, Place.GROWTH, CreatureState.FOLDED));
        if (survival()) {
            add(new Wake(a), new Walk(a.stand()), new Crouch(true), new Walk(a.quiet(), a.clear()));
        } else {
            add(new Unseen(a), new Walk(a.quiet()));
        }
        add(new Torch(a.torchFloor()), new Alight(a), new Walk(a.stand(), a.clear()), new Crouch(false), new Burn(a),
                new Wither(a), new Harvest(a), new Save(), new Load(), new Revisit(a));
        var b = plan.second();
        add(new Clock("night", 23 * 60), new Arrive(List.of(b.anchor()), b.lureStand(), b.site().node()),
                new Wait("stream", 1.2f), new Look("10-growth-at-night", b, null, CreatureState.FOLDED),
                new Torch(b.lureFloor()), new Lure(b));
        var park = plan.park();
        add(new Clock("day", 10 * 60), new Arrive(park.animals(), park.stand(), park.toward()), new Wait("stream", 1f),
                new Animals("13-park-wildlife"), new Startle());
        add(new Arrive(plan.flock(), plan.flockStand(), plan.flock().getFirst().cell()), new Wait("stream", 1f),
                new Flock());
        add(new Arrive(List.of(plan.breacher()), plan.breacherStand(), plan.breacher().cell()), new Crouch(true),
                new Blind(), new Report());
        staged = true;
    }

    // ------------------------------------------------------------------
    // Bodies
    // ------------------------------------------------------------------

    /** The live body of an encounter, or null while it is dormant, dead or never admitted. */
    private Entity live(long id) {
        for (Creature c : game.entities.creatures) {
            if (c.encounterId == id && !c.dead) { return c; }
        }
        for (Npc n : game.entities.npcs) {
            if (n.encounterId == id && !n.dead) { return n; }
        }
        return null;
    }

    private Creature maw(UrbanEcologyTourPlan.Site site) {
        return live(site.anchor().id()) instanceof Creature c ? c : null;
    }

    private Creature mawOrFail(UrbanEcologyTourPlan.Site site) {
        Creature c = maw(site);
        if (c == null) { throw fail("the Veilmaw " + UrbanIds.format(site.anchor().id()) + " is not here"); }
        return c;
    }

    private List<Creature> animals(List<EncounterAnchors.Anchor> anchors) {
        List<Creature> out = new ArrayList<>();
        for (var a : anchors) {
            if (live(a.id()) instanceof Creature c) { out.add(c); }
        }
        return out;
    }

    private void lookAt(Entity e, float height) {
        lookAt(e.pos, height);
    }

    private void lookAt(Vector3f p, float height) {
        look(p.x, p.y + height, p.z);
    }

    /** Horizontal distance from the player to a body. */
    private float distanceTo(Entity e) {
        return (float) Math.hypot(e.pos.x - game.player.pos.x, e.pos.z - game.player.pos.z);
    }

    // ------------------------------------------------------------------
    // Staging
    // ------------------------------------------------------------------

    /** Staged time of day, clear weather pinned, so a capture is not taken mid-transition. */
    private final class Clock extends Task {
        private final int minutes;

        Clock(String name, int minutes) { super("clock " + name); this.minutes = minutes; }

        @Override boolean step(float dt) {
            long day = (long) (game.time.totalMinutes / (24 * 60));
            game.time.totalMinutes = day * 24 * 60 + minutes;
            game.weather.current = game.weather.next = Weather.CLEAR;
            game.weather.blend = 1f;
            game.weather.changeTimer = 4000f;
            System.out.println("[urban-ecology] staged-clock " + name.substring(6) + " minutes=" + minutes);
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /**
     * Admits planned bodies through the production gate, from a staged observer 24 blocks east
     * of each facing east (the gate's breathing-room and view rules met by the pose alone), then
     * stages the leg's pose: feet on {@code at}, facing {@code facing}. A body the director's
     * own scan already admitted counts as it is.
     */
    private final class Arrive extends Task {
        private final List<EncounterAnchors.Anchor> anchors;
        private final Vec3i at, facing;

        Arrive(List<EncounterAnchors.Anchor> anchors, Vec3i at, Vec3i facing) {
            super("arrive " + at); this.anchors = anchors; this.at = at; this.facing = facing;
        }

        @Override boolean step(float dt) {
            releaseAll();
            fighting = null;
            approach = null;
            for (var a : anchors) {
                Entity body = live(a.id());
                String how = "director";
                if (body == null) {
                    Vec3i c = a.cell();
                    game.world.ensureChunks(c.x(), c.z(), 3, 10_000);
                    game.player.pos.set(c.x() + 24.5f, c.y(), c.z() + .5f);
                    game.player.vel.zero();
                    game.camera.yaw = 90;
                    game.camera.pitch = 0;
                    body = game.entities.encounters.activate(game, a);
                    how = "production-gate";
                }
                if (body == null) {
                    throw fail("the production gate refused " + a.species() + " " + UrbanIds.format(a.id()) + " at " + a.cell());
                }
                admitted++;
                System.out.println("[urban-ecology] staged-admission species=" + a.species().id + " id="
                        + UrbanIds.format(a.id()) + " cell=" + a.cell() + " by=" + how);
            }
            game.world.ensureChunks(at.x(), at.z(), 4, 10_000);
            game.player.pos.set(at.x() + .5f, at.y() + .001f, at.z() + .5f);
            game.player.vel.zero();
            game.player.onGround = true;
            look(facing.x() + .5f, facing.y() + 1f, facing.z() + .5f);
            poses++;
            System.out.println("[urban-ecology] staged-pose " + at);
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** Holds or releases crouch (left control). */
    private final class Crouch extends Task {
        private final boolean down;

        Crouch(boolean down) { super(down ? "crouch" : "stand up"); this.down = down; }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_LEFT_CONTROL, down);
            return age > .15f;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    // ------------------------------------------------------------------
    // The Veilmaw
    // ------------------------------------------------------------------

    /** Looks at a site's Veilmaw, checks its state and the place the HUD names, then captures. */
    private final class Look extends Task {
        private final String label;
        private final UrbanEcologyTourPlan.Site site;
        private final Place expected;
        private final CreatureState state;

        Look(String label, UrbanEcologyTourPlan.Site site, Place expected, CreatureState state) {
            super("look " + label); this.label = label; this.site = site; this.expected = expected; this.state = state;
        }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            Creature maw = mawOrFail(site);
            lookAt(maw, .5f);
            if (age < .6f) { return false; }
            if (expected != null && game.player.place != expected) {
                throw fail("expected " + expected + " at " + label + " but the HUD names " + game.player.place);
            }
            if (state != null && maw.state != state) {
                throw fail("expected the Veilmaw " + state + " at " + label + " but it is " + maw.state);
            }
            views.add(label + "=" + game.player.locationLine() + " maw=" + maw.state + " d=" + fmt(distanceTo(maw)));
            capture(label);
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /**
     * Survival: stands up and backs away with S while facing the Veilmaw. The first uncrouched
     * step wakes it; its telegraph is captured, and then whatever ends it: a lunge at where the
     * step was heard, or a let-go. Backing away must escape the blow.
     */
    private final class Wake extends Task {
        private final UrbanEcologyTourPlan.Site site;
        private float health = -1, settle;
        private boolean stirred, after;

        Wake(UrbanEcologyTourPlan.Site site) { super("wake the Veilmaw and back away"); this.site = site; }

        @Override boolean step(float dt) {
            Creature maw = mawOrFail(site);
            if (health < 0) { health = game.player.health; }
            hold(GLFW_KEY_LEFT_CONTROL, false);
            lookAt(maw, .4f);
            boolean far = distanceTo(maw) >= 7.5f;
            hold(GLFW_KEY_S, !far);
            lunged |= maw.state == CreatureState.LUNGING;
            if (!telegraphed) {
                // Captured two thirds of the way through the windup, hood raised and veins lit.
                if (maw.state == CreatureState.STIRRING) {
                    stirred = true;
                    if (maw.windup <= com.veylon.ai.VeilmawAI.WINDUP * .35f) {
                        telegraphed = true;
                        views.add("03-telegraph=" + game.player.locationLine() + " d=" + fmt(distanceTo(maw)));
                        capture("03-telegraph");
                    }
                } else if (stirred) {
                    throw fail("the telegraph ended before it could be captured: " + maw.state);
                } else if (age > 6) {
                    throw fail("the Veilmaw never stirred: " + maw.state);
                }
                return false;
            }
            if (!after) {
                if (maw.state == CreatureState.RECOILING || maw.state == CreatureState.RETURNING
                        || maw.state == CreatureState.FOLDED) {
                    after = true;
                    views.add("04-after-the-telegraph=" + maw.state + " lunged=" + lunged + " d=" + fmt(distanceTo(maw)));
                    capture("04-after-the-telegraph");
                }
                return false;
            }
            if (!far || maw.state != CreatureState.FOLDED) {
                if (age > 25) { throw fail("the Veilmaw never settled: " + maw.state); }
                return false;
            }
            if ((settle += dt) < .5f) { return false; }
            hold(GLFW_KEY_S, false);
            struck = health - game.player.health;
            if (struck > .01f) { throw fail("the lunge struck a player who backed away (-" + fmt(struck) + " HP)"); }
            escaped = true;
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** Creative: stands up and walks beside the folded Veilmaw, which neither hears nor feels the player. */
    private final class Unseen extends Task {
        private final UrbanEcologyTourPlan.Site site;
        private Walk walk;
        private boolean first;

        Unseen(UrbanEcologyTourPlan.Site site) { super("walk unseen beside the Veilmaw"); this.site = site; }

        @Override boolean step(float dt) {
            Creature maw = mawOrFail(site);
            hold(GLFW_KEY_LEFT_CONTROL, false);
            if (walk == null) {
                Vec3i rest = site.site().rest();
                walk = new Walk(beside(rest, site.site().node()));
            }
            if (!walk.step(dt)) { return false; }
            lookAt(maw, .4f);
            if (maw.state != CreatureState.FOLDED) { throw fail("the Veilmaw sensed an unseen player: " + maw.state); }
            if (!first && age > 1) {
                first = true;
                views.add("03-unseen-beside-it=" + game.player.locationLine() + " d=" + fmt(distanceTo(maw)));
                capture("03-unseen-beside-it");
                return false;
            }
            if (!first || age < 4) { return false; }
            unseen = true;
            views.add("04-still-folded=" + maw.state + " d=" + fmt(distanceTo(maw)));
            capture("04-still-folded");
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** A standing cell diagonal to the fold cell, away from the node: within touch of the body. */
    private Vec3i beside(Vec3i rest, Vec3i node) {
        int ax = Integer.signum(rest.x() - node.x()), az = Integer.signum(rest.z() - node.z());
        int[][] options = ax != 0 ? new int[][]{{ax, 1}, {ax, -1}} : new int[][]{{1, az}, {-1, az}};
        for (int[] o : options) {
            Vec3i c = rest.offset(o[0], 0, o[1]);
            if (com.veylon.world.VoxelClearance.standable(game.world, c.x(), c.y(), c.z())) { return c; }
        }
        throw fail("no standing cell beside the fold cell " + rest);
    }

    /** Selects a torch and places it on a floor with a right click. */
    private final class Torch extends Task {
        private final Vec3i floor;
        private float wait;

        Torch(Vec3i floor) { super("place a torch on " + floor); this.floor = floor; }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            Vec3i above = floor.offset(0, 1, 0);
            if (game.world.getBlock(above.x(), above.y(), above.z()) == BlockType.TORCH) { return true; }
            if (!select(ItemType.TORCH)) { throw fail("no torch in the kit"); }
            lookAtBlock(floor, 1f);
            if ((wait += dt) > .5f) {
                if (age > 6) { throw fail("the torch was never placed on " + floor + ": " + game.targetHit); }
                rightClick();
                wait = 0;
            }
            return false;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** Per frame: whether the first site's node has been seen alight. */
    @Override
    protected void observe() {
        if (plan != null && !burned && game.fire.burningCells().contains(plan.first().site().node())) { burned = true; }
    }

    /** Beside the node: the torch just placed sets it alight. */
    private final class Alight extends Task {
        private final UrbanEcologyTourPlan.Site site;

        Alight(UrbanEcologyTourPlan.Site site) { super("see the node catch"); this.site = site; }

        @Override boolean step(float dt) {
            lookAtBlock(site.site().node(), .6f);
            if (!burned) {
                if (age > 3) { throw fail("the node never caught from the torch beside it"); }
                return false;
            }
            if (age < .4f) { return false; }
            views.add("05-node-alight=" + game.player.locationLine() + " fires=" + game.fire.count());
            capture("05-node-alight");
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** From beyond the territory, the growth burning. */
    private final class Burn extends Task {
        private final UrbanEcologyTourPlan.Site site;

        Burn(UrbanEcologyTourPlan.Site site) { super("watch the growth burn"); this.site = site; }

        @Override boolean step(float dt) {
            Vec3i node = site.site().node();
            lookAtBlock(node, .5f);
            if (age < .6f) { return false; }
            BlockType block = game.world.getBlock(node.x(), node.y(), node.z());
            views.add("06-growth-burning=" + block + " fires=" + game.fire.count());
            capture("06-growth-burning");
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** Waits for the node to burn out and the Veilmaw to wither with it, leaving remains. */
    private final class Wither extends Task {
        private final UrbanEcologyTourPlan.Site site;
        private float since = -1;

        Wither(UrbanEcologyTourPlan.Site site) { super("watch the Veilmaw wither"); this.site = site; }

        @Override boolean step(float dt) {
            var record = game.entities.encounters.resident(site.anchor().id());
            Carcass remains = remains(site);
            Creature body = maw(site);
            if (body != null) { lookAt(body, .3f); }
            if (record == null || !record.dead() || remains == null) {
                if (body == null && (record == null || !record.dead())) {
                    throw fail("the Veilmaw vanished without dying: " + (record == null ? "no record" : record.health));
                }
                if (age > 60) { throw fail("the Veilmaw outlived its node: " + (body == null ? "?" : body.state)); }
                return false;
            }
            Vec3i node = site.site().node();
            if (game.world.getBlock(node.x(), node.y(), node.z()) == BlockType.GROWTH_NODE) {
                throw fail("the Veilmaw died but its node stands");
            }
            lookAt(remains.pos, .2f);
            if (since < 0) { since = age; }
            if (age - since < 1.5f) { return false; }
            withered = true;
            views.add("07-withered=" + game.player.locationLine() + " fires=" + game.fire.count());
            capture("07-withered");
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** Once the fire near them is out, walks to the remains and cuts the membrane sacs with F. */
    private final class Harvest extends Task {
        private final UrbanEcologyTourPlan.Site site;
        private Walk walk;
        private int before = -1, presses;
        private float wait;

        Harvest(UrbanEcologyTourPlan.Site site) { super("cut the membrane sacs"); this.site = site; }

        @Override boolean step(float dt) {
            Carcass remains = remains(site);
            if (remains == null) {
                if (age > 30) { throw fail("the Veilmaw left no remains"); }
                return false;
            }
            if (walk == null) {
                lookAt(remains.pos, .2f);
                if (!remains.atRest() || burningNear(remains.pos, 4)) {
                    if (age > 150) { throw fail("the fire around the remains never went out: " + game.fire.count()); }
                    return false;
                }
                before = game.player.inventory.count(ItemType.MEMBRANE_SAC);
                walk = new Walk(standBeside(remains.pos));
            }
            if (!walk.step(dt)) { return false; }
            lookAt(remains.pos, .1f);
            int now = game.player.inventory.count(ItemType.MEMBRANE_SAC);
            if (now >= before + 2) {
                if (remains.hideLeft != 0) { throw fail("sacs left on the remains: " + remains.hideLeft); }
                if (game.player.inventory.count(ItemType.RAW_MEAT) > 0) { throw fail("the alien remains gave meat"); }
                harvested = true;
                views.add("08-membrane-sacs=" + now + " " + game.player.locationLine());
                capture("08-membrane-sacs");
                return true;
            }
            if ((wait += dt) > .4f) {
                if (++presses > 10) { throw fail("F never cut the sacs: " + game.interactPrompt); }
                press(GLFW_KEY_F);
                wait = 0;
            }
            return false;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    private Carcass remains(UrbanEcologyTourPlan.Site site) {
        Vec3i node = site.site().node();
        for (Carcass c : game.entities.carcasses) {
            if (c.type == Creature.CreatureType.VEILMAW
                    && Math.hypot(c.pos.x - node.x() - .5f, c.pos.z - node.z() - .5f) < 14) { return c; }
        }
        return null;
    }

    private boolean burningNear(Vector3f p, int radius) {
        for (Vec3i b : game.fire.burningCells()) {
            if (Math.abs(b.x() + .5f - p.x) <= radius && Math.abs(b.z() + .5f - p.z) <= radius) { return true; }
        }
        return false;
    }

    /** The nearest standing cell within reach of the remains. */
    private Vec3i standBeside(Vector3f p) {
        int ex = (int) Math.floor(p.x), ez = (int) Math.floor(p.z);
        Vec3i best = null;
        double bestD = Double.MAX_VALUE;
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int y = 40; y <= 43; y++) {
                    Vec3i c = new Vec3i(ex + dx, y, ez + dz);
                    double d = Math.hypot(c.x() + .5f - p.x, c.z() + .5f - p.z);
                    if (d >= .7 && d <= 2.0 && d < bestD
                            && com.veylon.world.VoxelClearance.standable(game.world, c.x(), c.y(), c.z())
                            && UrbanSlicePlan.route(game.world, feet(), c) != null) {
                        bestD = d;
                        best = c;
                    }
                }
            }
        }
        if (best == null) { throw fail("no standing cell beside " + p); }
        return best;
    }

    /** F5 through the hotkeys. */
    private final class Save extends Task {
        private boolean pressed;
        private Object mark;

        Save() { super("quick-save"); }

        @Override boolean step(float dt) {
            if (!pressed) {
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
        private World before;

        Load() { super("quick-load over the live world"); }

        @Override boolean step(float dt) {
            if (!pressed) {
                before = game.world;
                mark = logMark();
                press(GLFW_KEY_F9);
                pressed = true;
                return false;
            }
            if (!loggedSince(mark, "Game loaded.") || game.world == before) {
                throw fail("quick-load did not replace the live world: " + lastLog());
            }
            before = null;
            fighting = null;
            approach = null;
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** After the reload: the node is still gone and the Veilmaw still a tombstone the gate refuses. */
    private final class Revisit extends Task {
        private final UrbanEcologyTourPlan.Site site;

        Revisit(UrbanEcologyTourPlan.Site site) { super("revisit the burnt site"); this.site = site; }

        @Override boolean step(float dt) {
            Vec3i node = site.site().node();
            lookAtBlock(node, .4f);
            if (age < 1.2f) { return false; }
            BlockType block = game.world.getBlock(node.x(), node.y(), node.z());
            var record = game.entities.encounters.resident(site.anchor().id());
            if (block == BlockType.GROWTH_NODE || record == null || !record.dead() || live(site.anchor().id()) != null
                    || game.entities.encounters.activate(game, site.anchor()) != null) {
                throw fail("the burnt site came back after the reload: node=" + block + " record="
                        + (record == null ? "none" : record.health));
            }
            if (game.player.inventory.count(ItemType.MEMBRANE_SAC) < 2) { throw fail("the sacs were lost in the reload"); }
            reloaded = true;
            views.add("09-after-reload=" + block + " " + game.player.locationLine());
            capture("09-after-reload");
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** The torch set inside its ground draws the Veilmaw, which creeps to it and smothers it. */
    private final class Lure extends Task {
        private final UrbanEcologyTourPlan.Site site;
        private Object mark;

        Lure(UrbanEcologyTourPlan.Site site) { super("lure the Veilmaw to a torch"); this.site = site; }

        @Override boolean step(float dt) {
            if (mark == null) { mark = logMark(); }
            Creature maw = mawOrFail(site);
            Vec3i torch = site.lureFloor().offset(0, 1, 0);
            boolean lit = game.world.getBlock(torch.x(), torch.y(), torch.z()) == BlockType.TORCH;
            if (!lured) {
                lookAt(maw, .4f);
                if (maw.state == CreatureState.CREEPING) {
                    if (age < 1.2f) { return false; }
                    lured = true;
                    views.add("11-lured-to-the-torch=" + game.player.locationLine() + " d=" + fmt(distanceTo(maw)));
                    capture("11-lured-to-the-torch");
                } else if (age > 12 || !lit) {
                    throw fail("the torch never drew the Veilmaw: " + maw.state + " lit=" + lit);
                }
                return false;
            }
            lookAtBlock(torch, .2f);
            if (lit) {
                if (age > 30) { throw fail("the Veilmaw never reached the torch: " + maw.state); }
                return false;
            }
            if (!loggedSince(mark, "smothers the torch")) { throw fail("the torch went out unsmothered: " + lastLog()); }
            if (age < 2f + 1.2f) { return false; }
            smothered = true;
            views.add("12-torch-smothered=" + maw.state + " d=" + fmt(distanceTo(maw)));
            capture("12-torch-smothered");
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    // ------------------------------------------------------------------
    // Wildlife and the Breacher
    // ------------------------------------------------------------------

    /** Checks every animal of a habitat stays inside it, then captures the park. */
    private final class Animals extends Task {
        private final String label;

        Animals(String label) { super("watch " + label); this.label = label; }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            List<Creature> herd = animals(plan.park().animals());
            if (herd.size() < 2) { throw fail("the park keeps " + herd.size() + " animals"); }
            lookAt(herd.getFirst(), .3f);
            keptIn(herd);
            if (age < .8f) { return false; }
            views.add(label + "=" + herd.size() + " " + game.player.locationLine());
            capture(label);
            return true;
        }
    }

    /** Fails when an animal has left its habitat, beyond the leash's own margin. */
    private void keptIn(List<Creature> herd) {
        for (Creature c : herd) {
            Habitat h = c.home;
            if (h == null || h.outside(c.pos.x, c.pos.z) > .75f) {
                throw fail(c.type + " " + UrbanIds.format(c.encounterId) + " left its habitat at " + c.pos);
            }
        }
    }

    /**
     * Approaches the nearest hare. In Survival the player creeps up crouched until just outside
     * the distance a crouched player spooks it, then stands: it bolts, and the park's animals stay
     * on the park's ground. In Creative they ignore the unseen walker beside them.
     */
    private final class Startle extends Task {
        private Walk walk;
        private float since = -1, boltAt = -1;
        private boolean captured;
        private Creature bolting;

        Startle() { super("approach the hares"); }

        @Override boolean step(float dt) {
            List<Creature> herd = animals(plan.park().animals());
            keptIn(herd);
            Creature nearest = null;
            for (Creature c : herd) {
                if (c.type == Creature.CreatureType.HARE && (nearest == null || distanceTo(c) < distanceTo(nearest))) {
                    nearest = c;
                }
                if (c.state == CreatureState.FLEE && boltAt < 0) {
                    boltAt = age;
                    bolting = c;
                }
            }
            if (nearest == null) { throw fail("no hare in the park"); }
            if (since < 0) {
                if (boltAt >= 0 || distanceTo(nearest) < (survival() ? 4.7f : 3f)) {
                    since = age;
                    hold(GLFW_KEY_W, false);
                    hold(GLFW_KEY_LEFT_CONTROL, false); // standing up: a hare's spook radius nearly doubles
                } else {
                    hold(GLFW_KEY_LEFT_CONTROL, survival());
                    if (walk == null || walk.step(dt)) {
                        walk = new Walk(standBeside(nearest.pos));
                    }
                    if (age > 40) { throw fail("never came near a hare"); }
                    return false;
                }
            }
            Creature watched = bolting != null && !bolting.dead ? bolting : nearest;
            lookAt(watched, .2f);
            if (!captured) {
                if (survival() && boltAt < 0 && age - since > 3f) { throw fail("the hares never bolted from the player"); }
                if (survival() ? boltAt < 0 || age - boltAt < .12f : age - since < 1f) { return false; }
                captured = true;
                String label = survival() ? "14-hares-bolt-inside-the-park" : "14-unseen-among-the-hares";
                views.add(label + "=" + herd.size() + " bolted=" + (boltAt >= 0) + " d=" + fmt(distanceTo(watched)));
                capture(label);
            }
            if (age - since < 3.5f) { return false; }
            if (!survival() && boltAt >= 0) { throw fail("the hares bolted from an unseen player"); }
            parkKept = true;
            return true;
        }
    }

    /** The roof flock: both birds in the air over the walk-up, kept to their band. */
    private final class Flock extends Task {
        Flock() { super("watch the roof flock"); }

        @Override boolean step(float dt) {
            List<Creature> birds = animals(plan.flock());
            if (birds.isEmpty()) { throw fail("the roof flock is gone"); }
            lookAt(birds.getFirst(), .2f);
            for (Creature c : birds) {
                Habitat h = c.home;
                if (h == null || h.outside(c.pos.x, c.pos.z) > .75f || c.pos.y < h.floorY() - 1 || c.pos.y > h.ceilingY() + 1) {
                    throw fail("a roof bird left its band at " + c.pos);
                }
            }
            if (age < 2f) { return false; }
            flockKept = true;
            views.add("15-roof-flock=" + birds.size() + " y=" + fmt(birds.getFirst().pos.y));
            capture("15-roof-flock");
            return true;
        }
    }

    /** A crouched, still observer near a Breacher: it does not hear or see the player. */
    private final class Blind extends Task {
        Blind() { super("watch the Breacher crouched"); }

        @Override boolean step(float dt) {
            if (!(live(plan.breacher().id()) instanceof Npc n)) { throw fail("the Breacher is gone"); }
            lookAt(n, 1.4f);
            if (n.searchTimer > 0 || n.strikeWindup > 0) {
                throw fail("the Breacher noticed a crouched, still player: search=" + n.searchTimer);
            }
            if (age < 3f) { return false; }
            breacherBlind = true;
            views.add("16-breacher-blind-to-a-crouch=" + game.player.locationLine() + " d=" + fmt(distanceTo(n)));
            capture("16-breacher-blind-to-a-crouch");
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    private final class Report extends Task {
        Report() { super("report"); }

        @Override boolean step(float dt) {
            releaseAll();
            boolean veilmaw = survival() ? telegraphed && escaped : unseen;
            if (!veilmaw || !burned || !withered || !harvested || !reloaded || !lured || !smothered || !parkKept
                    || !flockKept || !breacherBlind || captures != CAPTURES) {
                throw fail("tour incomplete: veilmaw=" + veilmaw + " burned=" + burned + " withered=" + withered
                        + " harvested=" + harvested + " reloaded=" + reloaded + " lured=" + lured + " smothered=" + smothered
                        + " park=" + parkKept + " flock=" + flockKept + " breacher=" + breacherBlind + " captures=" + captures);
            }
            passed = true;
            reported = true;
            System.out.printf(Locale.ROOT, "[urban-ecology] pass=true mode=%s seed=%d poses=%d admitted=%d captures=%d"
                            + " lunged=%s struck=%.1f engaged=%s defeated=%s health=%.1f sacs=%d routeSeconds=%.0f%n",
                    game.gameMode().name().toLowerCase(Locale.ROOT), game.world.seed, poses, admitted, captures, lunged,
                    struck, engaged, defeated, game.player.health, game.player.inventory.count(ItemType.MEMBRANE_SAC), elapsed);
            System.out.println("[urban-ecology] views " + String.join(" | ", views));
            System.out.println("[urban-ecology] timeline " + String.join(" ", timeline));
            game.automation.finishScriptedRun();
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

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

    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
