package com.veylon;

import com.veylon.entity.Npc;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.UrbanSite;
import com.veylon.settlement.UrbanSiteDirector;
import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.ui.CityMap;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.DistrictPlan;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanStarterSite;

import java.util.Locale;
import java.util.function.Supplier;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The milestone-21 onboarding tour ({@code VEYLON_SCENE=urban_onboarding}) on a generator-19 world
 * from {@code VEYLON_GENERATOR=urban-dev}: what a new survivor reads, with ordinary controls, from
 * the refuge they wake in to a mission's next step. It captures the HUD as the session begins; the
 * city map with home and the journal's first steps; a cupboard's search line, its search in progress
 * and its contents; the building and floor on the HUD and the floor sketch upstairs; a locked door's
 * line; a camp lookout's answer wrapped in the conversation panel; the map with the camp, the
 * missions and their rumours; the HUD and the map at night; the HUD in fog; and the map after F5 and
 * F9, the journal and the entered buildings unchanged.
 *
 * <p>Staged and printed as staged: a spear for self-defence in Survival, a pose before the locked
 * door and one at the camp between legs, a forward-only clock and the weather. Everything else is
 * the fresh session: its kit, its log and its generated city.
 */
final class UrbanOnboardingQaScene extends UrbanPeopleScene {
    static final double DEADLINE_SECONDS = 600;
    static final int CAPTURES = 14;

    private BuildingPlan neighbour;
    private BuildingPlan.Fixture cupboard, upstairs, locked;
    private SurvivorSites.Site camp;
    private boolean passed, reloaded;
    private String before = "";

    UrbanOnboardingQaScene(Game game) { super(game); }

    @Override protected String tag() { return "urban-onboarding"; }
    @Override protected double deadline() { return DEADLINE_SECONDS; }

    boolean passed() { return passed; }

    /** Plans the tour on the fresh generated world and stages the spear; no block is changed. */
    void stage() {
        if (!CityMap.applies(game.world)) {
            throw new IllegalStateException("urban_onboarding needs a generator-19 world (VEYLON_GENERATOR=urban-dev)");
        }
        neighbour = neighbour();
        cupboard = container(neighbour, 0);
        upstairs = container(neighbour, 1);
        locked = lockedDoor();
        camp = UrbanMissionTourPlan.of(game.world).camp();
        if (!game.player.abilities.invulnerable()) {
            game.player.inventory.set(5, new ItemStack(ItemType.IRON_SPEAR, 1));
        }
        System.out.println("[urban-onboarding] stage seed=" + game.world.seed + " generator=" + game.world.generatorVersion
                + " mode=" + game.gameMode().name().toLowerCase(Locale.ROOT)
                + " evidence=walked-legs(W,F,M,Esc,0-9,F5,F9,mouse-look); screen-commands(conversation);"
                + " staged-poses(locked-door,camp),staged-clock(forward),staged-weather(fog),staged-kit(spear-in-survival);"
                + " fresh-session-kit-and-log; generated-geometry; normal-simulation");
        System.out.println("[urban-onboarding] plan neighbour=" + UrbanIds.format(neighbour.id()) + " cupboard="
                + cupboard.position() + " upstairs=" + upstairs.position() + " locked=" + locked.position()
                + " " + locked.block() + " camp=" + UrbanIds.format(camp.id()) + " refuge="
                + UrbanIds.format(game.world.urban.refugeBuilding));
        Supplier<Vec3i> campEntry = camp::entry;
        add(new Wait("the first frames", 1.5f),
                new Capture("01-wake-in-the-refuge"),
                new MapView("02-city-map-home"),
                new Clock("day", 10 * 60),
                new Walk(cupboard.target()),
                new Facing("03-search-prompt", () -> cupboard.position(), "[F] Search the "),
                new Search(),
                new Leave(),
                new Walk(upstairs.target()),
                new Here("06-floor-hud", "· floor 1"),
                new MapView("07-floor-sketch"),
                new Pose("before a locked door", () -> locked.target(), () -> locked.position()),
                new Facing("08-locked-door-prompt", () -> locked.position(), "Locked."),
                new Pose("the camp", campEntry, camp::safe),
                new Talk("the camp's lookout", this::campPerson),
                new Say(9, "09-a-wrapped-answer", s -> s.contains("relay mast")),
                new Say(10, null, s -> s.contains("dispensary")),
                new Say(10, null, s -> s.contains("hiding")),
                new Leave(),
                new MapView("10-map-journal-rumours"),
                new Clock("night", 23 * 60),
                new Wait("night falls", 1f),
                new Capture("11-night-hud"),
                new MapView("12-night-map"),
                new Fog(),
                new Wait("fog closes in", 1.5f),
                new Capture("13-fog-hud"),
                new Record(), new Save(), new Load(), new Reloaded(),
                new MapView("14-after-load-map"),
                new Report());
        staged = true;
    }

    // ------------------------------------------------------------------
    // Reading the plan
    // ------------------------------------------------------------------

    /** The starter block's unheld walk-up flats beside the refuge, as the refuge tour reads them. */
    private BuildingPlan neighbour() {
        DistrictPlan district = game.world.urban.regionPlan(0, 0);
        for (ParcelPlan parcel : district.parcels()) {
            if (!UrbanStarterSite.isNeighbour(parcel) || parcel.use() != ParcelPlan.Use.RESIDENTIAL) { continue; }
            BuildingPlan b = game.world.urban.buildingAt(parcel.x0(), parcel.z0());
            if (b != null && b.family() == BuildingPlan.Family.APARTMENT && b.variant() != BuildingPlan.Variant.OCCUPIED) {
                return b;
            }
        }
        throw new IllegalStateException("no unheld flats beside the refuge");
    }

    /** A cupboard of a floor of a building: its first cabinet, else its first container. */
    private static BuildingPlan.Fixture container(BuildingPlan b, int floor) {
        BuildingPlan.Fixture any = null;
        for (BuildingPlan.Fixture f : b.fixtures()) {
            if (f.kind() != BuildingPlan.FixtureKind.CONTAINER || UrbanIds.floorIndex(f.id()) != floor) { continue; }
            if (f.block() == BlockType.CABINET) { return f; }
            if (any == null) { any = f; }
        }
        if (any == null) { throw new IllegalStateException("no container on floor " + floor + " of " + UrbanIds.format(b.id())); }
        return any;
    }

    /** The starter block's nearest locked wooden door to the flats, a lock a fresh survivor meets. */
    private BuildingPlan.Fixture lockedDoor() {
        BuildingPlan.Fixture best = null;
        Vec3i from = cupboard.position();
        for (ParcelPlan parcel : game.world.urban.regionPlan(0, 0).parcels()) {
            BuildingPlan b = game.world.urban.buildingAt(parcel.x0(), parcel.z0());
            if (b == null) { continue; }
            for (BuildingPlan.Fixture f : b.fixtures()) {
                if (f.kind() == BuildingPlan.FixtureKind.DOOR && f.locked() && !f.block().requiresTool
                        && (best == null || f.position().distSq(from.x(), from.y(), from.z())
                        < best.position().distSq(from.x(), from.y(), from.z()))) {
                    best = f;
                }
            }
        }
        if (best == null) { throw new IllegalStateException("no locked door in the starter district"); }
        return best;
    }

    private Npc campPerson() {
        UrbanSite s = UrbanSiteDirector.registered(game.world, camp.id());
        if (s == null) { return null; }
        for (Settlement.Resident r : s.residents) {
            if (r.live != null && !r.live.dead) { return r.live; }
        }
        return null;
    }

    private String prompt() {
        return game.interactPrompt == null ? "" : game.interactPrompt;
    }

    /** The journal, the entered buildings and the walked districts, as one comparable line. */
    private String state() {
        StringBuilder out = new StringBuilder("visits=").append(game.world.urban.visits.districts())
                .append('/').append(game.world.urban.visits.buildings()).append(" journal=");
        for (CityMap.Entry e : CityMap.journal(game)) { out.append('[').append(e.state()).append(':').append(e.heading()).append(']'); }
        return out.toString();
    }

    // ------------------------------------------------------------------
    // Legs
    // ------------------------------------------------------------------

    /** M opens the city map; a capture once it has drawn; M closes it again. */
    private final class MapView extends Task {
        private final String label;
        private boolean opened, captured;

        MapView(String label) { super("map " + label); this.label = label; }

        @Override boolean step(float dt) {
            if (!opened) {
                if (game.uiMode != Game.UiMode.NONE) { press(GLFW_KEY_ESCAPE); return false; }
                press(GLFW_KEY_M);
                opened = true;
                return false;
            }
            if (!captured) {
                if (game.uiMode != Game.UiMode.MAP) {
                    if (age > 3) { throw fail("M did not open the map: " + game.uiMode); }
                    return false;
                }
                if (age < .8f) { return false; }
                views.add(label + "=" + String.join(" | ", CityMap.journal(game).stream().map(CityMap.Entry::heading).toList()));
                capture(label);
                captured = true;
                return false;
            }
            if (age < 1.3f) { return false; }
            if (game.uiMode == Game.UiMode.MAP) { press(GLFW_KEY_M); return false; }
            return game.uiMode == Game.UiMode.NONE;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** Looks at a block until the HUD's line starts as expected, then captures it. */
    private final class Facing extends Task {
        private final String label, expected;
        private final Supplier<Vec3i> block;

        Facing(String label, Supplier<Vec3i> block, String expected) {
            super("face " + label); this.label = label; this.block = block; this.expected = expected;
        }

        @Override boolean step(float dt) {
            lookAtBlock(block.get(), .5f);
            if (age < .5f) { return false; }
            if (!prompt().startsWith(expected)) {
                if (age > 5) { throw fail(block.get() + " reads '" + prompt() + "', not '" + expected + "...'"); }
                return false;
            }
            views.add(label + "=" + prompt());
            capture(label);
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** F on the cupboard: its search in progress, then its contents once searched. */
    private final class Search extends Task {
        private boolean pressed, progress;

        Search() { super("search the cupboard"); }

        @Override boolean step(float dt) {
            lookAtBlock(cupboard.position(), .5f);
            if (!pressed) {
                press(GLFW_KEY_F);
                pressed = true;
                return false;
            }
            if (game.uiMode != Game.UiMode.CRATE) {
                if (age > 3) { throw fail("F did not open the cupboard: " + prompt()); }
                return false;
            }
            if (!progress) {
                if (!game.crateSearching()) { throw fail("the cupboard was searched before: " + game.crateStatus()); }
                if (game.crateSearchProgress() < .25f) { return false; }
                views.add("04-search-progress=" + game.crateStatus());
                capture("04-search-progress");
                progress = true;
                return false;
            }
            if (game.crateSearching()) {
                if (age > 12) { throw fail("the search never finished"); }
                return false;
            }
            views.add("05-searched-contents=" + game.crateStatus());
            capture("05-searched-contents");
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** The HUD's building and floor line, read before its capture. */
    private final class Here extends Task {
        private final String label, expected;

        Here(String label, String expected) { super("here " + label); this.label = label; this.expected = expected; }

        @Override boolean step(float dt) {
            if (age < .6f) { return false; }
            String here = CityMap.hereLine(game);
            if (!here.contains(expected)) { throw fail("the HUD reads '" + here + "', not '" + expected + "'"); }
            views.add(label + "=" + here);
            capture(label);
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** Fog rolls in and stays (staged weather, printed). */
    private final class Fog extends Task {
        Fog() { super("fog"); }

        @Override boolean step(float dt) {
            game.weather.current = game.weather.next = Weather.FOG;
            game.weather.blend = 1f;
            game.weather.changeTimer = 4000f;
            System.out.println("[urban-onboarding] staged-weather fog");
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    private final class Record extends Task {
        Record() { super("record the journal and the visits"); }

        @Override boolean step(float dt) {
            before = state();
            System.out.println("[urban-onboarding] before-save " + before);
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    private final class Reloaded extends Task {
        Reloaded() { super("the same journal and visits after the load"); }

        @Override boolean step(float dt) {
            if (age < .8f) { return false; }
            String after = state();
            if (!after.equals(before)) { throw fail("the load changed what the map knows:\n  " + before + "\n  " + after); }
            reloaded = true;
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    private final class Report extends Task {
        Report() { super("report"); }

        @Override boolean step(float dt) {
            var visits = game.world.urban.visits;
            boolean ok = reloaded && captures == CAPTURES && visits.entered(neighbour.id())
                    && visits.entered(game.world.urban.refugeBuilding)
                    && game.world.urban.missions.activeCount() == 2 && game.world.urban.missions.comms.rumoured;
            if (!ok) { throw fail("incomplete: captures=" + captures + " " + state()); }
            passed = true;
            reported = true;
            System.out.println("[urban-onboarding] pass=true mode=" + game.gameMode().name().toLowerCase(Locale.ROOT)
                    + " seed=" + game.world.seed + " poses=" + poses + " captures=" + captures + " health="
                    + game.player.health + " routeSeconds=" + Math.round(elapsed));
            System.out.println("[urban-onboarding] state " + state());
            System.out.println("[urban-onboarding] deeds " + String.join(" | ", deeds));
            System.out.println("[urban-onboarding] views " + String.join(" | ", views));
            System.out.println("[urban-onboarding] timeline " + String.join(" ", timeline));
            game.automation.finishScriptedRun();
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }
}
