package com.veylon;

import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Fixtures;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.BuildingPlan.Fixture;
import com.veylon.world.urban.BuildingPlan.Function;
import com.veylon.world.urban.BuildingPlan.Room;
import com.veylon.world.urban.BuildingPlanner;
import com.veylon.world.urban.CivicCatalogue;
import com.veylon.world.urban.UrbanContainers;
import com.veylon.world.urban.UrbanDatum;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The milestone-12 catalogue walk ({@code VEYLON_SCENE=urban_catalogue}) on a generator-11
 * world from {@code VEYLON_GENERATOR=urban-dev}, in either game mode.
 *
 * <p>It visits one building of each commercial and civic family near the spawn: a damaged
 * grocer, a clinic, a school, an office block, a security post and a warehouse. Moving between
 * buildings is a staged pose at each street door, logged as such, and the run starts with a
 * staged spear and pry tool for self-defence and the armoury lock. Inside, it plays with
 * ordinary controls through the production input seam: it walks with W and mouse look, opens
 * doors with F, tries the clinic's locked staff door, forces the dispensary by holding the
 * left button, searches its stock with F, climbs to upper floors by the stairs, and captures
 * each room's identity. No block, body, clock or weather is staged. Failures throw.
 */
final class UrbanCatalogueQaScene extends UrbanRouteScene {
    /** Wall-clock seconds in a native session, simulated seconds in a headless route. */
    static final double DEADLINE_SECONDS = 600;

    private UrbanCataloguePlan plan;
    private boolean passed;
    private int poses, upperFloor = Integer.MIN_VALUE;
    private final List<String> lockedEntries = new ArrayList<>();
    private final Map<ItemType, Integer> searched = new TreeMap<>();

    UrbanCatalogueQaScene(Game game) {
        super(game);
    }

    @Override protected String tag() { return "urban-catalogue"; }
    @Override protected double deadline() { return DEADLINE_SECONDS; }
    @Override protected void observe() {
        int feet = (int) Math.floor(game.player.pos.y + .01f);
        if (feet > UrbanDatum.STREET_Y && feet < UrbanDatum.slabY(12)) {
            upperFloor = Math.max(upperFloor, UrbanDatum.floorContaining(feet));
        }
    }

    boolean passed() { return passed; }
    UrbanCataloguePlan plan() { return plan; }

    /** Plans the walk on the current generated world and stages the kit; no block is changed. */
    void stage() {
        plan = UrbanCataloguePlan.of(game.world);
        // Staged kit: a spear for any infected met on the way and a pry tool for the armoury.
        Inventory inv = game.player.inventory;
        inv.set(7, new ItemStack(ItemType.IRON_SPEAR, 1));
        inv.set(8, new ItemStack(ItemType.IRON_PICKAXE, 1));
        System.out.println("[urban-catalogue] stage seed=" + game.world.seed + " generator=" + game.world.generatorVersion
                + " mode=" + game.gameMode().name().toLowerCase(Locale.ROOT)
                + " evidence=staged-poses-between-buildings,staged-kit(spear,pry-tool);"
                + " input-seam(W,F,1-9,LMB) inside buildings; generated-geometry; normal-simulation");
        for (BuildingPlan b : List.of(plan.grocery(), plan.clinic(), plan.school(), plan.office(), plan.security(),
                plan.warehouse())) {
            System.out.println("[urban-catalogue] building " + BuildingPlanner.identity(b)
                    + " sign=\"" + CivicCatalogue.signText(game.world.seed, b) + "\"");
        }
        BuildingPlan grocer = plan.grocery(), clinic = plan.clinic(), school = plan.school();
        BuildingPlan office = plan.office(), post = plan.security(), depot = plan.warehouse();
        Fixture dispensary = UrbanCataloguePlan.securedDoor(clinic), armoury = UrbanCataloguePlan.securedDoor(post);
        Room shop = UrbanCataloguePlan.room(grocer, 0, Function.SHOP_FLOOR);
        Room treatment = UrbanCataloguePlan.room(clinic, 0, Function.CLINIC);
        Room classroom = UrbanCataloguePlan.room(school, 0, Function.CLASSROOM);
        Room upperClass = UrbanCataloguePlan.room(school, UrbanCataloguePlan.topFloor(school), Function.CLASSROOM);
        Room desks = UrbanCataloguePlan.room(office, 0, Function.OFFICE);
        Room upperDesks = UrbanCataloguePlan.room(office, UrbanCataloguePlan.topFloor(office), Function.OFFICE);
        Room squad = UrbanCataloguePlan.room(post, 0, Function.OFFICE);
        Room racks = UrbanCataloguePlan.room(depot, 0, Function.WAREHOUSE);
        Room workshop = UrbanCataloguePlan.room(depot, 0, Function.WORKSHOP);
        Fixture stock = UrbanCataloguePlan.furnishing(clinic, UrbanCataloguePlan.room(clinic, 0, Function.PHARMACY));
        Fixture bench = UrbanCataloguePlan.furniture(depot, workshop, BlockType.WORKBENCH);
        add(new Wait("settle", 1f),
                new Pose(grocer.approaches().getFirst(), grocer), new Wait("stream", .8f),
                new View(UrbanCataloguePlan.sign(grocer), .5f, "01-grocery-sign"),
                new Walk(shop.anchor()), new View(centre(shop), .3f, "02-grocery-shelves"),
                new Pose(clinic.approaches().getLast(), clinic), new Wait("stream", .8f),
                new Try(UrbanCataloguePlan.rearDoor(clinic), "03-clinic-staff-door-locked"),
                new Pose(clinic.approaches().getFirst(), clinic),
                new Walk(UrbanSlicePlan.inside(dispensary)), new View(dispensary.position(), .9f, "04-clinic-lobby"),
                new Force(dispensary, "05-clinic-dispensary-forced"),
                new Search(stock, "06-clinic-dispensary-stock"),
                new Walk(treatment.anchor()),
                new View(UrbanCataloguePlan.furnishing(clinic, treatment).position(), .5f, "07-clinic-treatment"),
                new Pose(school.approaches().getFirst(), school), new Wait("stream", .8f),
                new Walk(classroom.anchor()), new View(centre(classroom), .5f, "08-school-classroom"),
                new Walk(upperClass.anchor()), new View(centre(upperClass), .5f, "09-school-upper-floor"),
                new Pose(office.approaches().getFirst(), office), new Wait("stream", .8f),
                new Walk(desks.anchor()), new View(centre(desks), .5f, "10-office-desks"),
                new Walk(upperDesks.anchor()), new View(centre(upperDesks), .5f, "11-office-upper-floor"),
                new Pose(post.approaches().getFirst(), post), new Wait("stream", .8f),
                new Walk(UrbanSlicePlan.inside(armoury)), new LockedView(armoury, "12-security-armoury-lock"),
                new Walk(squad.anchor()), new View(centre(squad), .5f, "13-security-squad-room"),
                new Pose(depot.approaches().getFirst(), depot), new Wait("stream", .8f),
                new Walk(UrbanCataloguePlan.rackBay(depot, racks).target()),
                new View(UrbanCataloguePlan.aisleEnd(depot, racks, UrbanCataloguePlan.rackBay(depot, racks).target()), .9f,
                        "14-warehouse-racks"),
                new Walk(bench.target()), new View(bench.position(), .5f, "15-warehouse-workshop"),
                new Report());
        staged = true;
    }

    /** A room's middle cell at desk height, to frame the whole room. */
    private static Vec3i centre(Room room) {
        var r = room.interior();
        return new Vec3i((r.x0() + r.x1()) / 2, room.anchor().y(), (r.z0() + r.z1()) / 2);
    }

    /** A staged pose: feet on a street-door approach, facing the building; logged as staged. */
    private final class Pose extends Task {
        private final Vec3i at;
        private final BuildingPlan building;

        Pose(Vec3i at, BuildingPlan building) { super("pose " + at); this.at = at; this.building = building; }

        @Override boolean step(float dt) {
            releaseAll();
            fighting = null;
            approach = null;
            game.world.ensureChunks(at.x(), at.z(), 4, 10_000);
            game.player.pos.set(at.x() + .5f, at.y() + .001f, at.z() + .5f);
            game.player.vel.zero();
            game.player.onGround = true;
            var p = building.parcel();
            look((p.x0() + p.x1()) * .5f + .5f, at.y() + 1.6f, (p.z0() + p.z1()) * .5f + .5f);
            poses++;
            System.out.println("[urban-catalogue] staged-pose " + at + " for " + building.template());
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** Looks at a cell for a moment, then captures. */
    private final class View extends Task {
        private final Vec3i target;
        private final float height;
        private final String label;

        View(Vec3i target, float height, String label) {
            super("view " + label);
            this.target = target;
            this.height = height;
            this.label = label;
        }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            lookAtBlock(target, height);
            if (age < .4f) { return false; }
            capture(label);
            return true;
        }
    }

    /** F on a locked entrance: the attempt is refused and heard, and the prompt names the cost. */
    private final class Try extends Task {
        private final Fixture door;
        private final String label;
        private boolean pressed;

        Try(Fixture door, String label) { super("try " + door.position()); this.door = door; this.label = label; }

        @Override boolean step(float dt) {
            Vec3i p = door.position();
            lookAtBlock(p, .9f);
            if (age < .3f) { return false; }
            if (!pressed) {
                press(GLFW_KEY_F);
                pressed = true;
                return false;
            }
            if (age < .8f) { return false; }
            String prompt = game.interactPrompt;
            if (!Fixtures.isClosedDoor(game.world.getBlock(p.x(), p.y(), p.z())) || prompt == null
                    || !prompt.startsWith("Locked.")) {
                throw fail("the locked entrance " + p + " did not refuse entry: " + prompt);
            }
            lockedEntries.add(p + " " + prompt);
            capture(label);
            return true;
        }
    }

    /** Faces a secured lock from the corridor: its prompt says what forcing it needs. */
    private final class LockedView extends Task {
        private final Fixture door;
        private final String label;

        LockedView(Fixture door, String label) { super("locked " + door.position()); this.door = door; this.label = label; }

        @Override boolean step(float dt) {
            lookAtBlock(door.position(), .9f);
            if (age < .5f) { return false; }
            String prompt = game.interactPrompt;
            if (prompt == null || !prompt.startsWith("Locked.")) {
                throw fail("the secured lock " + door.position() + " shows no lock prompt: " + prompt);
            }
            lockedEntries.add(door.position() + " " + prompt);
            capture(label);
            return true;
        }
    }

    /** F to search, wait for the stock to show, capture it, then F to close; nothing is taken. */
    private final class Search extends Task {
        private final Fixture fixture;
        private final String label;
        private Walk walk;
        private int phase, presses;
        private float wait;

        Search(Fixture fixture, String label) { super("search " + fixture.position()); this.fixture = fixture; this.label = label; }

        @Override boolean threatsInterrupt() { return phase < 2 || game.uiMode == Game.UiMode.NONE; }

        @Override boolean step(float dt) {
            Vec3i p = fixture.position();
            switch (phase) {
                case 0 -> {
                    if (walk == null) { walk = new Walk(fixture.target()); }
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
                    for (int i = 0; crate != null && i < crate.size(); i++) {
                        ItemStack s = crate.get(i);
                        if (s != null) { searched.merge(s.type, s.count, Integer::sum); }
                    }
                    var entry = game.world.urban.containers.at(p);
                    timeline.add(String.format(Locale.ROOT, "%.0f:searched=%s table=%s", elapsed, searched,
                            UrbanContainers.planned(game.world, p).table()));
                    capture(label);
                    if (entry == null || !entry.state.revealed()) { throw fail("the search at " + p + " did not finish"); }
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
    }

    private final class Report extends Task {
        Report() { super("report"); }
        @Override boolean step(float dt) {
            releaseAll();
            capture("16-end");
            passed = true;
            reported = true;
            System.out.printf(Locale.ROOT, "[urban-catalogue] pass=true mode=%s seed=%d families=6 poses=%d"
                            + " forced=%s searched=%s upperFloorReached=%d lockedEntries=%s engaged=%s defeated=%s"
                            + " health=%.1f routeSeconds=%.0f%n",
                    game.gameMode().name().toLowerCase(Locale.ROOT), game.world.seed, poses, forcedAt, searched,
                    upperFloor, lockedEntries, engaged, defeated, game.player.health, elapsed);
            System.out.println("[urban-catalogue] timeline " + String.join(" ", timeline));
            game.automation.finishScriptedRun();
            return true;
        }
        @Override boolean threatsInterrupt() { return false; }
    }
}
