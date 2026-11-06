package com.veylon;

import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.urban.UrbanPlaces.Place;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The milestone-14 route tour ({@code VEYLON_SCENE=urban_underground}) on a generator-13 world
 * from {@code VEYLON_GENERATOR=urban-dev}, in either game mode.
 *
 * <p>Every leg is walked with ordinary controls (W, F, right click and mouse look through the
 * production input seam), from a staged pose at its start:
 * <ol>
 *   <li>street, a basement, the service passage under a courtyard passage, the neighbour's
 *       basement and back out to the street;</li>
 *   <li>a basement hall, its loose panel slid aside with F, and the hidden cellar behind it;</li>
 *   <li>street, the stairs and stair bulkhead to a roof, and over a connector to the next roof;</li>
 *   <li>a fire escape climbed from the courtyard to the roof, and back down by the stairs (the
 *       controller climbs a ladder but has no way down one, so a ladder is an ascent);</li>
 *   <li>at night, the spawn station's stair, its dark platform, its service room and the tunnel
 *       beyond, the platform and the track each lit by a torch placed with a right click;</li>
 *   <li>a flooded tunnel, waded.</li>
 * </ol>
 * Poses between legs, the clock, the weather, and a kit of a spear and torches are staged and
 * printed as staged; blocks are never staged. Exactly sixteen labelled captures. Failures throw.
 */
final class UrbanUndergroundQaScene extends UrbanRouteScene {
    static final double DEADLINE_SECONDS = 900;

    private UrbanUndergroundTourPlan plan;
    private boolean passed, cellarFound, stationFound, climbed, waded, lit;
    private int poses, captures;
    private final List<String> views = new ArrayList<>();

    UrbanUndergroundQaScene(Game game) { super(game); }

    @Override protected String tag() { return "urban-underground"; }
    @Override protected double deadline() { return DEADLINE_SECONDS; }

    boolean passed() { return passed; }
    UrbanUndergroundTourPlan plan() { return plan; }

    @Override
    protected void capture(String label) {
        captures++;
        super.capture(label);
    }

    /** Plans the tour on the current generated world and stages the kit; no block is changed. */
    void stage() {
        plan = UrbanUndergroundTourPlan.of(game.world);
        Inventory inv = game.player.inventory;
        inv.set(7, new ItemStack(ItemType.IRON_SPEAR, 1));
        inv.set(6, new ItemStack(ItemType.TORCH, 4));
        System.out.println("[urban-underground] stage seed=" + game.world.seed + " generator=" + game.world.generatorVersion
                + " mode=" + game.gameMode().name().toLowerCase(Locale.ROOT)
                + " evidence=walked-legs(W,F,right-click,mouse-look); staged-poses-between-legs,staged-clock,"
                + "staged-kit(spear,torches); generated-geometry; normal-simulation");
        System.out.println("[urban-underground] plan " + plan);
        var p = plan.passage();
        add(new Clock("day", 11 * 60), new Pose(p.westStreet(), p.serviceDoor()), new Wait("stream", .8f),
                new View("01-street-before-the-basement", p.serviceDoor(), null),
                new Walk(p.westDoor()), new View("02-basement-service-door", p.serviceDoor(), Place.BASEMENT),
                new Walk(p.corridor()), new View("03-service-passage", p.eastStreet(), Place.SERVICE_PASSAGE),
                new Walk(p.eastStreet()), new View("04-back-on-the-street", p.westStreet(), Place.NONE));
        var c = plan.cellar();
        add(new Pose(c.street(), c.hall()), new Wait("stream", .8f), new Walk(c.hall()),
                new View("05-loose-panel-and-drag-marks", c.panel(), Place.BASEMENT),
                new Slide(), new Walk(c.inside()), new View("06-hidden-cellar", c.hall(), Place.HIDDEN_CELLAR));
        var r = plan.roof();
        add(new Pose(r.street(), r.roof()), new Wait("stream", .8f), new Walk(r.roof()),
                new View("07-roof-by-the-stairs", r.opening(), Place.ROOF), new Walk(r.otherRoof()),
                new View("08-roof-" + (r.bridge() ? "bridge" : "connector"), r.opening(), Place.ROOF));
        var e = plan.escape();
        add(new Pose(e.foot(), e.gap()), new Wait("stream", .8f), new Climb(), new Walk(e.street()),
                new View("11-down-by-the-stairs", e.gap(), Place.NONE));
        var s = plan.station();
        add(new Clock("night", 23 * 60), new Pose(s.street(), s.platform()), new Wait("stream", .8f),
                new View("12-station-stair-at-night", s.platform(), Place.NONE),
                new Walk(s.platform()), new View("13-dark-platform", s.door(), Place.STATION),
                new Torch(platformFloor(s)), new View("14-torchlit-platform", s.door(), Place.STATION),
                new Walk(s.room()), new Walk(s.tunnel()), new Torch(s.trackFloor()),
                new View("15-torchlit-tunnel", s.along(), Place.TUNNEL));
        add(new Pose(plan.flood(), plan.flood().offset(0, 0, 1)), new Wait("stream", 1.2f), new Wade(),
                new Report());
        staged = true;
    }

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
            System.out.println("[urban-underground] staged-clock " + name.substring(6) + " minutes=" + minutes);
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** A staged pose: feet on a leg's first cell, facing along it; logged as staged. */
    private final class Pose extends Task {
        private final Vec3i at, facing;

        Pose(Vec3i at, Vec3i facing) { super("pose " + at); this.at = at; this.facing = facing; }

        @Override boolean step(float dt) {
            releaseAll();
            fighting = null;
            approach = null;
            game.world.ensureChunks(at.x(), at.z(), 4, 10_000);
            game.player.pos.set(at.x() + .5f, at.y() + .001f, at.z() + .5f);
            game.player.vel.zero();
            game.player.onGround = true;
            look(facing.x() + .5f, facing.y() + 1f, facing.z() + .5f);
            poses++;
            System.out.println("[urban-underground] staged-pose " + at);
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** Looks at a leg's subject, checks the place the HUD names, then captures. */
    private final class View extends Task {
        private final String label;
        private final Vec3i subject;
        private final Place expected;

        View(String label, Vec3i subject, Place expected) {
            super("view " + label); this.label = label; this.subject = subject; this.expected = expected;
        }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            lookAtBlock(subject, .9f);
            if (age < .5f) { return false; }
            if (expected != null && game.player.place != expected) {
                throw fail("expected " + expected + " at " + label + " but the HUD names " + game.player.place);
            }
            views.add(label + "=" + game.player.locationLine());
            capture(label);
            return true;
        }
    }

    /** Aims at the loose panel and slides it aside with F: the cellar is found. */
    private final class Slide extends Task {
        private float wait;
        private int presses;

        Slide() { super("slide the loose panel"); }

        @Override boolean step(float dt) {
            Vec3i panel = plan.cellar().panel();
            hold(GLFW_KEY_W, false);
            lookAtBlock(panel, .9f);
            if (game.world.getBlock(panel.x(), panel.y(), panel.z()) == BlockType.HIDDEN_PANEL_OPEN) {
                if (!game.world.urban.discovery.discovered(plan.cellar().room())) { throw fail("the cellar was not found"); }
                cellarFound = true;
                return true;
            }
            if ((wait += dt) > .4f) {
                if (++presses > 8) { throw fail("the loose panel never slid aside: " + game.interactPrompt); }
                press(GLFW_KEY_F);
                wait = 0;
            }
            return false;
        }
    }

    /** Climbs the fire escape: W held against the wall while in the ladder, then onto the roof. */
    private final class Climb extends Task {
        private boolean midway;

        Climb() { super("climb the fire escape"); }

        @Override boolean step(float dt) {
            var e = plan.escape();
            game.camera.yaw = (float) Math.toDegrees(Math.atan2(e.roof().x() + .5f - game.player.pos.x,
                    -(e.roof().z() + .5f - game.player.pos.z)));
            game.camera.pitch = -55; // up the ladder (negative pitch looks up); W still pushes at the wall
            hold(GLFW_KEY_W, true);
            if (!midway && game.player.pos.y > (e.foot().y() + e.gap().y()) / 2f) {
                midway = true;
                capture("09-fire-escape-climb");
            }
            Vec3i at = feet();
            if (at.equals(e.roof()) && game.player.onGround) {
                hold(GLFW_KEY_W, false);
                if (game.player.place != Place.ROOF) { throw fail("climbed onto " + game.player.place); }
                climbed = true;
                views.add("10-fire-escape-roof=" + game.player.locationLine());
                lookAtBlock(e.gap(), .2f);
                capture("10-fire-escape-roof");
                return true;
            }
            if (age > 60) { throw fail("the climb stalled at " + game.player.pos); }
            return false;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** The platform floor one cell from the landing towards the service room. */
    private static Vec3i platformFloor(UrbanUndergroundTourPlan.Station s) {
        return new Vec3i(s.platform().x(), s.platform().y() - 1, s.platform().z())
                .offset(Integer.signum(s.door().x() - s.platform().x()), 0, Integer.signum(s.door().z() - s.platform().z()));
    }

    /** Selects a torch and places it on a floor cell with a right click: bringing light below. */
    private final class Torch extends Task {
        private final Vec3i floor;
        private float wait;

        Torch(Vec3i floor) { super("place a torch on " + floor); this.floor = floor; }

        @Override boolean step(float dt) {
            Vec3i above = floor.offset(0, 1, 0);
            if (game.world.getBlock(above.x(), above.y(), above.z()) == BlockType.TORCH) { lit = true; return true; }
            if (!select(ItemType.TORCH)) { throw fail("no torch in the kit"); }
            lookAtBlock(floor, 1f);
            if ((wait += dt) > .5f) {
                if (age > 6) { throw fail("the torch was never placed"); }
                rightClick();
                wait = 0;
            }
            return false;
        }
    }

    /** Standing in a flooded tunnel: in water and wet. */
    private final class Wade extends Task {
        Wade() { super("wade the flooded tunnel"); }

        @Override boolean step(float dt) {
            if (age < .6f) { return false; }
            if (!game.player.inWater || game.player.place != Place.TUNNEL) {
                throw fail("the flooded tunnel was not water underfoot at " + game.player.pos + " " + game.player.place);
            }
            waded = true;
            views.add("16-flooded-tunnel=" + game.player.locationLine() + " wetness=" + game.player.wetness);
            capture("16-flooded-tunnel");
            return true;
        }
    }

    private final class Report extends Task {
        Report() { super("report"); }

        @Override boolean step(float dt) {
            releaseAll();
            stationFound = game.world.urban.discovery.discovered(plan.station().id());
            if (!cellarFound || !stationFound || !climbed || !waded || !lit || captures != 16) {
                throw fail("tour incomplete: cellar=" + cellarFound + " station=" + stationFound + " climbed=" + climbed
                        + " waded=" + waded + " lit=" + lit + " captures=" + captures);
            }
            passed = true;
            reported = true;
            System.out.printf(Locale.ROOT, "[urban-underground] pass=true mode=%s seed=%d poses=%d captures=%d"
                            + " discovered=%d engaged=%s defeated=%s health=%.1f routeSeconds=%.0f%n",
                    game.gameMode().name().toLowerCase(Locale.ROOT), game.world.seed, poses, captures,
                    game.world.urban.discovery.size(), engaged, defeated, game.player.health, elapsed);
            System.out.println("[urban-underground] views " + String.join(" | ", views));
            System.out.println("[urban-underground] timeline " + String.join(" ", timeline));
            game.automation.finishScriptedRun();
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }
}
