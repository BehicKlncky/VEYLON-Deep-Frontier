package com.veylon;

import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.util.Vec3i;
import com.veylon.world.urban.DistrictRules;
import com.veylon.world.urban.UrbanDistrict;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The milestone-13 district tour ({@code VEYLON_SCENE=urban_districts}) on a generator-12 world
 * from {@code VEYLON_GENERATOR=urban-dev}, in either game mode.
 *
 * <p>It first walks one district transition with ordinary controls (W and mouse look through
 * the production input seam): from a staged start on a local street's sidewalk forty blocks
 * inside one district, across the avenue on the boundary, to forty blocks inside a different
 * one, and checks that the HUD's district and the arrival line changed. It then visits a stop
 * in each of the six districts by day, and the four whose hazards change most after dark and
 * in fog again at night and in fog, wading into the flooded street on the way. Poses between
 * stops, the clock, the weather and a self-defence spear are staged and printed as staged;
 * blocks are never staged. Exactly sixteen labelled captures. Failures throw.
 */
final class UrbanDistrictQaScene extends UrbanRouteScene {
    /** Wall-clock seconds in a native session, simulated seconds in a headless route. */
    static final double DEADLINE_SECONDS = 600;

    private static final UrbanDistrict[] NIGHT = {UrbanDistrict.FLOODED, UrbanDistrict.QUARANTINE,
        UrbanDistrict.INDUSTRIAL, UrbanDistrict.CIVIC};
    private static final UrbanDistrict[] FOG = {UrbanDistrict.FLOODED, UrbanDistrict.QUARANTINE,
        UrbanDistrict.COMMERCIAL, UrbanDistrict.RESIDENTIAL};

    private UrbanDistrictTourPlan plan;
    private boolean passed, transitioned, waded;
    private int poses, captures;
    private String arrival = "";
    private final List<String> visited = new ArrayList<>();

    UrbanDistrictQaScene(Game game) {
        super(game);
    }

    @Override protected String tag() { return "urban-districts"; }
    @Override protected double deadline() { return DEADLINE_SECONDS; }

    boolean passed() { return passed; }
    UrbanDistrictTourPlan plan() { return plan; }

    @Override
    protected void capture(String label) {
        captures++;
        super.capture(label);
    }

    /** Plans the tour on the current generated world and stages the spear; no block is changed. */
    void stage() {
        plan = UrbanDistrictTourPlan.of(game.world);
        Inventory inv = game.player.inventory;
        inv.set(7, new ItemStack(ItemType.IRON_SPEAR, 1));
        System.out.println("[urban-districts] stage seed=" + game.world.seed + " generator=" + game.world.generatorVersion
                + " mode=" + game.gameMode().name().toLowerCase(Locale.ROOT)
                + " evidence=walked-transition(W,mouse-look); staged-poses-between-stops,staged-clock-and-weather,"
                + "staged-kit(spear); generated-geometry; normal-simulation");
        System.out.println("[urban-districts] transition " + plan.from().id + "->" + plan.to().id + " from "
                + plan.transitionStart() + " to " + plan.transitionEnd());
        for (var stop : plan.stops().entrySet()) {
            System.out.println("[urban-districts] stop " + stop.getKey().id + " stand=" + stop.getValue().stand()
                    + " subject=" + stop.getValue().subject());
        }
        add(new Wait("settle", 1f), new Clock("day", 12 * 60, Weather.CLEAR),
                new Pose(plan.transitionStart(), plan.transitionEnd()), new Wait("stream", .8f),
                new Capture("01-transition-" + plan.from().id),
                new Walk(plan.transitionEnd()), new Arrived(),
                new Capture("02-transition-" + plan.to().id));
        int n = 3;
        for (UrbanDistrict district : UrbanDistrict.values()) {
            n = visit(district, "day", n);
            if (district == UrbanDistrict.FLOODED) {
                add(new Walk(plan.wade()), new Wade());
            }
        }
        add(new Clock("night", 23 * 60, Weather.CLEAR));
        for (UrbanDistrict district : NIGHT) { n = visit(district, "night", n); }
        add(new Clock("fog", 8 * 60, Weather.FOG));
        for (UrbanDistrict district : FOG) { n = visit(district, "fog", n); }
        add(new Report());
        staged = true;
    }

    private int visit(UrbanDistrict district, String light, int n) {
        UrbanDistrictTourPlan.Stop stop = plan.stops().get(district);
        add(new Pose(stop.stand(), stop.subject()), new Wait("stream", .8f),
                new View(stop, district, String.format(Locale.ROOT, "%02d-%s-%s", n, light, district.id)));
        return n + 1;
    }

    /** Staged time and weather, pinned so a capture is not taken mid-transition. */
    private final class Clock extends Task {
        private final int minutes;
        private final Weather weather;

        Clock(String name, int minutes, Weather weather) {
            super("clock " + name);
            this.minutes = minutes;
            this.weather = weather;
        }

        @Override boolean step(float dt) {
            long day = (long) (game.time.totalMinutes / (24 * 60));
            game.time.totalMinutes = day * 24 * 60 + minutes;
            game.weather.current = weather;
            game.weather.next = weather;
            game.weather.blend = 1f;
            game.weather.changeTimer = 4000f;
            System.out.println("[urban-districts] staged-clock " + name.substring(6) + " minutes=" + minutes
                    + " weather=" + weather);
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** A staged pose: feet on a stop's standing cell, facing its subject; logged as staged. */
    private final class Pose extends Task {
        private final Vec3i at;
        private final Vec3i facing;

        Pose(Vec3i at, Vec3i facing) { super("pose " + at); this.at = at; this.facing = facing; }

        @Override boolean step(float dt) {
            releaseAll();
            fighting = null;
            approach = null;
            game.world.ensureChunks(at.x(), at.z(), 4, 10_000);
            game.player.pos.set(at.x() + .5f, at.y() + .001f, at.z() + .5f);
            game.player.vel.zero();
            game.player.onGround = true;
            look(facing.x() + .5f, at.y() + 1.6f, facing.z() + .5f);
            poses++;
            System.out.println("[urban-districts] staged-pose " + at);
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** Looks at a stop's subject for a moment, checks the district the HUD names, then captures. */
    private final class View extends Task {
        private final UrbanDistrictTourPlan.Stop stop;
        private final UrbanDistrict district;
        private final String label;

        View(UrbanDistrictTourPlan.Stop stop, UrbanDistrict district, String label) {
            super("view " + label);
            this.stop = stop;
            this.district = district;
            this.label = label;
        }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            lookAtBlock(stop.subject(), stop.height());
            if (age < .5f) { return false; }
            if (game.player.district != district) {
                throw fail("the HUD names " + game.player.district + " at the " + district + " stop");
            }
            visited.add(label + "=" + game.player.locationLine() + "/" + game.player.biome.displayName);
            capture(label);
            return true;
        }
    }

    /** The walked transition reached the other district: the HUD and the arrival line say so. */
    private final class Arrived extends Task {
        Arrived() { super("arrived"); }

        @Override boolean step(float dt) {
            if (age < .3f) { return false; }
            String expected = "Entering " + DistrictRules.of(plan.to()).displayName() + ".";
            arrival = game.eventLog.recent(8).stream().filter(s -> s.contains(expected)).findFirst().orElse("");
            if (game.player.district != plan.to() || arrival.isEmpty()) {
                throw fail("walked into " + game.player.district + " without the arrival line " + expected);
            }
            look(plan.transitionEnd().x() + (plan.transitionEnd().x() - plan.transitionStart().x()) * 0.5f, 42.6f,
                    plan.transitionEnd().z() + (plan.transitionEnd().z() - plan.transitionStart().z()) * 0.5f);
            transitioned = true;
            return true;
        }
    }

    /** Standing in the flooded street: in water, soaked and slowed. */
    private final class Wade extends Task {
        Wade() { super("wade"); }

        @Override boolean step(float dt) {
            if (age < .6f) { return false; }
            if (!game.player.inWater || game.player.district != UrbanDistrict.FLOODED) {
                throw fail("the flooded street was not water underfoot at " + game.player.pos);
            }
            waded = true;
            timeline.add(String.format(Locale.ROOT, "%.0f:wading inWater=%b wetness=%.2f", elapsed,
                    game.player.inWater, game.player.wetness));
            return true;
        }
    }

    private final class Report extends Task {
        Report() { super("report"); }

        @Override boolean step(float dt) {
            releaseAll();
            if (!transitioned || !waded || captures != 16) {
                throw fail("tour incomplete: transitioned=" + transitioned + " waded=" + waded + " captures=" + captures);
            }
            passed = true;
            reported = true;
            System.out.printf(Locale.ROOT, "[urban-districts] pass=true mode=%s seed=%d transition=%s->%s arrival=\"%s\""
                            + " poses=%d captures=%d engaged=%s defeated=%s health=%.1f routeSeconds=%.0f%n",
                    game.gameMode().name().toLowerCase(Locale.ROOT), game.world.seed, plan.from().id, plan.to().id,
                    arrival, poses, captures, engaged, defeated, game.player.health, elapsed);
            System.out.println("[urban-districts] views " + String.join(" ", visited));
            System.out.println("[urban-districts] timeline " + String.join(" ", timeline));
            game.automation.finishScriptedRun();
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }
}
