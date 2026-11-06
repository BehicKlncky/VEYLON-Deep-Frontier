package com.veylon;

import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.util.Vec3i;
import com.veylon.world.urban.UrbanDistrict;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The milestone-22 atmosphere tour ({@code VEYLON_SCENE=urban_atmosphere}) on a generator-19
 * world from {@code VEYLON_GENERATOR=urban-dev}, in either game mode: how the city looks and
 * what it sounds like at the places a survivor spends time, held still so captures taken before
 * and after a presentation change frame exactly the same view.
 *
 * <p>By day it stands in each of the six districts' streets, in a walk-up's entrance, kitchen,
 * upper floor, stairs and basement, in the hidden cellar of the refuge nearest the spawn with its
 * people, at the gate of the nearest stronghold and before the first alien growth; then the
 * street, the kitchen, the cellar, the gate, the growth and the flooded street at night, two
 * streets in fog, the upper floor and a street in rain, and by day again the chalk mark beside
 * that refuge's street door (milestone 22). Every pose, the clock and the weather are staged and
 * printed as staged; no block, item or person is. Each view prints what a listener there is
 * given: the acoustic zone, shelter, depth below the street, the light at the eye, the ambient
 * mix, the city's sounds so far, and the interaction line. Exactly {@value #CAPTURES} labelled
 * captures; the first 24 frame the same views at every revision. Failures throw.
 */
final class UrbanAtmosphereQaScene extends UrbanRouteScene {
    /** Wall-clock seconds in a native session, simulated seconds in a headless route. */
    static final double DEADLINE_SECONDS = 300;
    static final int CAPTURES = 25;
    /** Seconds a view is held before its capture, so meshes, shelter and the mix settle. */
    private static final float HOLD = 1.2f;

    private UrbanAtmosphereTourPlan plan;
    private boolean passed;
    private int poses, captures, number;
    private final List<String> views = new ArrayList<>();
    /** The interaction line each view was captured with, by its label. */
    private final java.util.Map<String, String> prompts = new java.util.LinkedHashMap<>();

    UrbanAtmosphereQaScene(Game game) {
        super(game);
    }

    @Override protected String tag() { return "urban-atmosphere"; }
    @Override protected double deadline() { return DEADLINE_SECONDS; }

    boolean passed() { return passed; }
    UrbanAtmosphereTourPlan plan() { return plan; }
    List<String> views() { return views; }
    java.util.Map<String, String> prompts() { return prompts; }

    @Override
    protected void capture(String label) {
        captures++;
        super.capture(label);
    }

    /** Plans the tour on the current generated world; no block is changed. */
    void stage() {
        if (game.world.urban == null || !game.world.urban.hasMissions()) {
            throw new IllegalStateException("urban_atmosphere needs a generator-19 world (VEYLON_GENERATOR=urban-dev)");
        }
        plan = UrbanAtmosphereTourPlan.of(game.world);
        System.out.println("[urban-atmosphere] stage seed=" + game.world.seed + " generator=" + game.world.generatorVersion
                + " mode=" + game.gameMode().name().toLowerCase(Locale.ROOT)
                + " evidence=staged-poses,staged-clock-and-weather; generated-geometry; normal-simulation"
                + " apartment=" + plan.building());
        for (var s : plan.streets().entrySet()) { stop("street-" + s.getKey().id, s.getValue()); }
        for (var s : plan.apartment().entrySet()) { stop("apartment-" + s.getKey(), s.getValue()); }
        stop("cellar", plan.cellar());
        stop("gate", plan.gate());
        stop("growth", plan.growth());

        add(new Wait("settle", 1f), new Clock("day", 12 * 60, Weather.CLEAR));
        for (UrbanDistrict d : UrbanDistrict.values()) { view("day-street-" + d.id, plan.streets().get(d)); }
        for (var s : plan.apartment().entrySet()) { view("day-apartment-" + s.getKey(), s.getValue()); }
        view("day-cellar-civilians", plan.cellar());
        view("day-stronghold-gate", plan.gate());
        view("day-quarantine-growth", plan.growth());
        add(new Clock("night", 23 * 60, Weather.CLEAR));
        view("night-street-residential", plan.streets().get(UrbanDistrict.RESIDENTIAL));
        view("night-apartment-kitchen", plan.apartment().get("kitchen"));
        view("night-cellar-civilians", plan.cellar());
        view("night-stronghold-gate", plan.gate());
        view("night-quarantine-growth", plan.growth());
        view("night-street-flooded", plan.streets().get(UrbanDistrict.FLOODED));
        add(new Clock("fog", 8 * 60, Weather.FOG));
        view("fog-street-quarantine", plan.streets().get(UrbanDistrict.QUARANTINE));
        view("fog-street-residential", plan.streets().get(UrbanDistrict.RESIDENTIAL));
        add(new Clock("rain", 14 * 60, Weather.RAIN));
        view("rain-apartment-upper-floor", plan.apartment().get("upper-floor"));
        view("rain-street-commercial", plan.streets().get(UrbanDistrict.COMMERCIAL));
        add(new Clock("mark", 15 * 60, Weather.CLEAR));
        view("day-refuge-door-mark", plan.mark());
        add(new Report());
        staged = true;
    }

    private void stop(String name, UrbanAtmosphereTourPlan.Stop s) {
        System.out.println("[urban-atmosphere] stop " + name + " stand=" + s.stand() + " subject=" + s.subject());
    }

    private void view(String name, UrbanAtmosphereTourPlan.Stop stop) {
        number++;
        add(new Pose(stop), new View(stop, String.format(Locale.ROOT, "%02d-%s", number, name)));
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
            System.out.println("[urban-atmosphere] staged-clock " + name.substring(6) + " minutes=" + minutes
                    + " weather=" + weather);
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** A staged pose: feet on a stop's standing cell, facing its subject; logged as staged. */
    private final class Pose extends Task {
        private final UrbanAtmosphereTourPlan.Stop stop;

        Pose(UrbanAtmosphereTourPlan.Stop stop) { super("pose " + stop.stand()); this.stop = stop; }

        @Override boolean step(float dt) {
            releaseAll();
            fighting = null;
            approach = null;
            Vec3i at = stop.stand();
            game.world.ensureChunks(at.x(), at.z(), 4, 10_000);
            game.player.pos.set(at.x() + .5f, at.y() + .001f, at.z() + .5f);
            game.player.vel.zero();
            game.player.onGround = true;
            lookAtBlock(stop.subject(), stop.height());
            poses++;
            System.out.println("[urban-atmosphere] staged-pose " + at);
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** Holds a stop's view until meshes, shelter and the mix settle, reports what is heard, captures. */
    private final class View extends Task {
        private final UrbanAtmosphereTourPlan.Stop stop;
        private final String label;

        View(UrbanAtmosphereTourPlan.Stop stop, String label) {
            super("view " + label);
            this.stop = stop;
            this.label = label;
        }

        @Override boolean step(float dt) {
            lookAtBlock(stop.subject(), stop.height());
            if (age < HOLD) { return false; }
            String heard = listener();
            views.add(label + "=" + game.player.locationLine());
            prompts.put(label, game.interactPrompt == null ? "" : game.interactPrompt);
            System.out.println("[urban-atmosphere] view " + label + " place=\"" + game.player.locationLine() + "\" " + heard
                    + " prompt=\"" + prompts.get(label) + "\"");
            capture(label);
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** What a listener at the camera is given: zone, shelter, depth, light at the eye and the ambient mix. */
    private String listener() {
        var eye = game.camera.position;
        int x = (int) Math.floor(eye.x), y = (int) Math.floor(eye.y), z = (int) Math.floor(eye.z);
        int fx = (int) Math.floor(game.player.pos.x), fz = (int) Math.floor(game.player.pos.z);
        float depth = game.world.environmentDepth(fx, game.player.pos.y, fz);
        float[] mix = game.ambience.lastMix;
        StringBuilder cues = new StringBuilder();
        int[] played = game.ambience.soundscape.played;
        for (var cue : com.veylon.engine.CitySounds.Cue.values()) {
            if (played[cue.ordinal()] > 0) {
                cues.append(cues.isEmpty() ? "" : ",").append(cue.name().toLowerCase(Locale.ROOT)).append(':')
                        .append(played[cue.ordinal()]);
            }
        }
        return String.format(Locale.ROOT, "zone=%s indoor=%b exposed=%b depth=%.1f sky=%.2f block=%.2f"
                        + " rain=%.2f wind=%.2f fire=%.2f cave=%.2f crickets=%.2f reach=%.2f cityCues=%s",
                AudioSceneState.environment(game, depth), game.player.shelter.indoor(), game.player.exposedToSky,
                depth, game.world.skyLight(x, y, z), game.world.blockLight(x, y, z),
                mix[0], mix[1], mix[2], mix[3], mix[4], game.ambience.lastReach, cues.isEmpty() ? "none" : cues);
    }

    private final class Report extends Task {
        Report() { super("report"); }

        @Override boolean step(float dt) {
            releaseAll();
            if (captures != CAPTURES) {
                throw fail("tour incomplete: captures=" + captures);
            }
            passed = true;
            reported = true;
            System.out.printf(Locale.ROOT, "[urban-atmosphere] pass=true mode=%s seed=%d poses=%d captures=%d"
                            + " engaged=%s defeated=%s health=%.1f routeSeconds=%.0f%n",
                    game.gameMode().name().toLowerCase(Locale.ROOT), game.world.seed, poses, captures,
                    engaged, defeated, game.player.health, elapsed);
            System.out.println("[urban-atmosphere] views " + String.join(" ", views));
            System.out.println("[urban-atmosphere] timeline " + String.join(" ", timeline));
            game.automation.finishScriptedRun();
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }
}
