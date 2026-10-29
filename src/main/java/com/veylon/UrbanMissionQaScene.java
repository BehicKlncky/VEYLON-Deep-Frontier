package com.veylon;

import com.veylon.entity.Npc;
import com.veylon.item.CraftingSystem;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.item.Recipe;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.UrbanComms;
import com.veylon.settlement.UrbanMissionBoard;
import com.veylon.settlement.UrbanSite;
import com.veylon.settlement.UrbanSiteDirector;
import com.veylon.ui.CraftingScreen;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanMissions;
import com.veylon.world.urban.UrbanMissions.Kind;
import com.veylon.world.urban.UrbanMissions.Mission;
import com.veylon.world.urban.UrbanMissions.Status;
import com.veylon.world.urban.UrbanUtilities.Device;

import java.util.Locale;
import java.util.function.Supplier;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The milestone-20 mission tour ({@code VEYLON_SCENE=urban_missions}) on a generator-19 world from
 * {@code VEYLON_GENERATOR=urban-dev}, in either game mode, with ordinary controls (W, F, C, the number
 * keys, both mouse buttons, Escape, F5, F9 and mouse look through the production input seam):
 * <ol>
 *   <li>by day, the camp nearest the spawn: asked about the radio, its lookout speaks of the old relay
 *       mast (the rumour) and gives three missions in turn: medicine from a named clinic, the people
 *       hiding below the street, and a look at the mast;</li>
 *   <li>the hidden refuge that mission names: its loose panel slid aside with F, its people found and
 *       given food, which readies the mission;</li>
 *   <li>the stronghold nearest the spawn: its gatekeeper given food until the ward counts the survivor
 *       a friend, then the relay's codes and the Wardens' transmitter module asked for; inside their
 *       restricted stores, where only a friend may stand, the module taken through the storage screen,
 *       and the mission reported at the gate;</li>
 *   <li>the relay's other parts out of the nearest warehouse tool chest and office utility locker;</li>
 *   <li>the relay's roof: the dead cabinet, the antenna cable strung and the power cells seated with
 *       the use button, which repairs it and answers the mast's mission;</li>
 *   <li>the starter refuge: a generator and a radio workstation built at its bench through the crafting
 *       screen's own command and put down on its wiring, the generator fuelled and started, the radio
 *       switched on, and the call carried on the Wardens' codes until the city answers: the ending
 *       card, and Escape carrying on;</li>
 *   <li>the camp again: the hidden people's and the mast's missions reported, each rewarded once;</li>
 *   <li>F5 and F9: the objective and every mission the same after the load.</li>
 * </ol>
 * The poses between legs (into the Wardens' stores too, where a friend may walk), the clock (only ever
 * forward), the weather and a kit of food to share, a pry tool, salvage for the bench and a fuel can
 * are staged and printed as staged; the integration route ({@code UrbanMissionRouteTest}) gathers
 * all of it in the city instead. No block is staged. Exactly {@value #CAPTURES} labelled captures,
 * after which the run closes itself. Failures throw.
 */
final class UrbanMissionQaScene extends UrbanPeopleScene {
    static final double DEADLINE_SECONDS = 900;
    static final int CAPTURES = 15;
    /** Dried berries brought to share: one handover to the hidden people, up to four to the ward. */
    static final int FOOD = 30;

    private UrbanMissionTourPlan plan;
    private boolean passed, answered, carriedOn, reloaded;
    private String before = "";

    UrbanMissionQaScene(Game game) { super(game); }

    @Override protected String tag() { return "urban-missions"; }
    @Override protected double deadline() { return DEADLINE_SECONDS; }

    boolean passed() { return passed; }
    UrbanMissionTourPlan plan() { return plan; }

    /** Plans the tour on the current generated world and stages the kit; no block is changed. */
    void stage() {
        plan = UrbanMissionTourPlan.of(game.world);
        Inventory inv = game.player.inventory;
        inv.clear();
        inv.set(7, new ItemStack(ItemType.IRON_PICKAXE, 1));
        inv.add(ItemType.DRIED_BERRY, FOOD);
        inv.add(ItemType.SPARE_PARTS, 4);
        inv.add(ItemType.SCRAP, 5);
        inv.add(ItemType.SHEET_METAL, 1);
        inv.add(ItemType.FUEL_CAN, 1);
        System.out.println("[urban-missions] stage seed=" + game.world.seed + " generator=" + game.world.generatorVersion
                + " mode=" + game.gameMode().name().toLowerCase(Locale.ROOT)
                + " evidence=walked-legs(W,F,C,0-9,LMB,RMB,Esc,F5,F9,mouse-look); screen-commands(conversation,storage,craft);"
                + " staged-poses-between-legs(including-the-wardens-stores),staged-clock(forward),"
                + "staged-kit(food-to-share,pry-tool,salvage-for-the-bench,fuel-can); generated-geometry; normal-simulation");
        System.out.println("[urban-missions] plan " + plan);
        Supplier<Vec3i> campEntry = () -> plan.camp().entry();
        add(new Clock("day", 10 * 60),
                new Pose("the camp", campEntry, () -> plan.camp().safe()),
                new Talk("the camp's lookout", this::campPerson),
                new Say(9, "01-the-rumour", s -> s.contains("relay mast")),
                new Say(10, null, s -> s.contains("dispensary")),
                new Say(10, null, s -> s.contains("hiding")),
                new Say(10, "02-three-missions", s -> s.contains("relay mast")),
                new Leave(), new Accepted(),
                new Pose("the hidden refuge's hall", this::hall, this::panel),
                new Slide(),
                new WalkTo("into the cellar", this::cellar),
                new Talk("the hidden civilians", this::civilian),
                new Say(2, "04-the-people-below", s -> s.contains("Thank you")),
                new Leave(), new Ready(Kind.HIDDEN_AID),
                new Pose("the stronghold's gate", () -> plan.gateStreet(), () -> plan.stronghold().gate().first()),
                new Talk("the gatekeeper", this::gatekeeper),
                new Befriend(),
                new Say(9, "05-the-wardens-codes", s -> s.contains("codes")),
                new Say(10, null, s -> s.contains("transmitter module")),
                new Leave(),
                new Pose("the Wardens' stores, where a friend may walk", () -> plan.module().target(), () -> plan.module().position()),
                new Take("06-the-wardens-module", () -> plan.module(), ItemType.TRANSMITTER_MODULE, "shared with you as a friend"),
                new Ready(Kind.STRONGHOLD_COMPONENT),
                new Pose("the stronghold's gate again", () -> plan.gateStreet(), () -> plan.stronghold().gate().first()),
                new Talk("the gatekeeper", this::gatekeeper),
                new Say(10, null, s -> s.contains("doesn't give twice")),
                new Leave(),
                new Pose("the warehouse's tool chest", () -> plan.cells().target(), () -> plan.cells().position()),
                new Take("07-power-cells", () -> plan.cells(), ItemType.POWER_CELL, null),
                new Pose("the office's utility locker", () -> plan.cable().target(), () -> plan.cable().position()),
                new Take("08-antenna-cable", () -> plan.cable(), ItemType.ANTENNA_CABLE, null),
                new Pose("the relay's roof", () -> plan.relay().stand(), () -> plan.relay().relay()),
                new DeadRelay(), new Fit(ItemType.ANTENNA_CABLE, null), new Fit(ItemType.POWER_CELL, "10-relay-repaired"),
                new Ready(Kind.LANDMARK),
                new Pose("the refuge's bench", () -> plan.bench().target(), () -> plan.bench().position()),
                new Craft("Rebuild Generator", "Build Radio Workstation"),
                new Put(ItemType.GENERATOR, plan.generator(), plan.generatorFrom()),
                new Fuel(), new Switch(plan.generator(), "[F] Start the generator"),
                new Put(ItemType.RADIO, plan.radio(), plan.radioFrom()),
                new Switch(plan.radio(), "[F] Switch the radio on"),
                new OnAir(), new Answered(),
                new Pose("the camp again", campEntry, () -> plan.camp().safe()),
                new Talk("the camp's lookout", this::campPerson),
                new Say(10, null, s -> s.contains("You found them")),
                new Say(10, "14-missions-reported", s -> s.contains("still stands")),
                new Leave(),
                new Record(), new Save(), new Load(), new Reloaded(), new Report());
        staged = true;
    }

    // ------------------------------------------------------------------
    // Reading the world
    // ------------------------------------------------------------------

    private UrbanMissions ledger() {
        return game.world.urban.missions;
    }

    private Mission mission(Kind kind) {
        for (Mission m : ledger().missions()) {
            if (m.kind == kind) { return m; }
        }
        return null;
    }

    private String prompt() {
        return game.interactPrompt == null ? "" : game.interactPrompt;
    }

    private static Npc firstLive(UrbanSite s) {
        if (s == null) { return null; }
        for (Settlement.Resident r : s.residents) {
            if (r.live != null && !r.live.dead) { return r.live; }
        }
        return null;
    }

    private Npc campPerson() {
        return firstLive(UrbanSiteDirector.registered(game.world, plan.camp().id()));
    }

    private Npc gatekeeper() {
        UrbanSite s = UrbanSiteDirector.registered(game.world, plan.stronghold().site().id());
        if (s == null) { return null; }
        for (int i = 0; i < s.residents.size(); i++) {
            UrbanSite.Resident r = s.resident(i);
            if (r.live != null && !r.live.dead && r.role == SurvivorSites.Role.GATEKEEPER) { return r.live; }
        }
        return firstLive(s);
    }

    /** The hidden refuge the camp's mission names. */
    private SurvivorSites.Site refuge() {
        Mission m = mission(Kind.HIDDEN_AID);
        if (m == null) { throw fail("the camp gave no mission for the hidden people"); }
        return SurvivorSites.resolve(game.world.urban, m.target);
    }

    private Npc civilian() {
        return firstLive(UrbanSiteDirector.registered(game.world, refuge().id()));
    }

    private Vec3i panel() {
        for (BuildingPlan.Fixture f : game.world.urban.buildingAt(refuge().anchor().x(), refuge().anchor().z()).fixtures()) {
            if (f.block() == BlockType.HIDDEN_PANEL) { return f.position(); }
        }
        throw fail("the refuge has no loose panel");
    }

    /** The basement hall cell in front of the panel. */
    private Vec3i hall() {
        Vec3i p = panel(), t = refuge().entry();
        return new Vec3i(2 * p.x() - t.x(), p.y(), 2 * p.z() - t.z());
    }

    private Vec3i cellar() {
        return refuge().entry();
    }

    /** The first stack of a type on the hotbar, swapped there from the pack when needed, selected. */
    private boolean hand(ItemType type) {
        if (select(type)) { return true; }
        Inventory inv = game.player.inventory;
        for (int i = 9; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s != null && s.type == type) {
                ItemStack moved = inv.get(8);
                inv.set(8, s);
                inv.set(i, moved);
                return select(type);
            }
        }
        return false;
    }

    /** Aims at the face of the relay's cabinet toward the stand cell, under the mast that rises from its top. */
    private void aimRelay() {
        Vec3i r = plan.relay().relay(), s = plan.relay().stand();
        look(r.x() + .5f + (s.x() - r.x()) * .48f, r.y() + .4f, r.z() + .5f + (s.z() - r.z()) * .48f);
    }

    /** The objective and every mission record, for comparing across the save. */
    private String state() {
        UrbanMissions.Comms c = ledger().comms;
        StringBuilder b = new StringBuilder(String.format(Locale.ROOT,
                "comms rumoured=%b found=%b recovered=%d installed=%d codes=%d radio=%b broadcast=%.2f complete=%b at=%.2f;",
                c.rumoured, c.relayFound, c.recovered, c.installed, c.codes, c.radioBuilt, c.broadcast, c.complete,
                c.completeMinute));
        for (Mission m : ledger().missions()) {
            b.append(' ').append(m.kind).append('=').append(m.status).append(String.format(Locale.ROOT, "(%.1f%s)",
                    m.progress, m.rewarded ? ",rewarded" : ""));
        }
        return b.toString();
    }

    @Override
    protected IllegalStateException fail(String reason) {
        System.out.println("[urban-missions] deeds " + String.join(" | ", deeds));
        return super.fail(reason);
    }

    // ------------------------------------------------------------------
    // Missions and people
    // ------------------------------------------------------------------

    /** The camp's three missions are in hand, each naming its real object. */
    private final class Accepted extends Task {
        Accepted() { super("three missions in hand"); }

        @Override boolean step(float dt) {
            for (Kind k : new Kind[]{Kind.CLINIC_SUPPLIES, Kind.HIDDEN_AID, Kind.LANDMARK}) {
                Mission m = mission(k);
                if (m == null || m.status != Status.ACTIVE) { throw fail("no " + k + " in hand: " + state()); }
            }
            if (!ledger().comms.rumoured) { throw fail("the rumour was not heard"); }
            deeds.add("missions " + state());
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** A mission's evidence is in: it is ready to report. */
    private final class Ready extends Task {
        private final Kind kind;

        Ready(Kind kind) { super(kind + " ready"); this.kind = kind; }

        @Override boolean step(float dt) {
            Mission m = mission(kind);
            if (m == null || m.status != Status.READY) {
                if (age > 3) { throw fail(kind + " is not ready: " + state()); }
                return false;
            }
            deeds.add(kind + " ready");
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** Aims at the loose panel and slides it aside with F: the refuge is found. */
    private final class Slide extends Task {
        private float wait;
        private int presses;

        Slide() { super("slide the loose panel"); }

        @Override boolean step(float dt) {
            Vec3i p = panel();
            hold(GLFW_KEY_W, false);
            lookAtBlock(p, .9f);
            if (game.world.getBlock(p.x(), p.y(), p.z()) == BlockType.HIDDEN_PANEL_OPEN) {
                if (!game.world.urban.discovery.discovered(refuge().room())) { throw fail("the panel moved but nothing was found"); }
                if (age < .8f) { return false; }
                capture("03-the-loose-panel");
                return true;
            }
            if ((wait += dt) > .4f) {
                if (++presses > 8) { throw fail("the loose panel never slid aside: " + prompt()); }
                press(GLFW_KEY_F);
                wait = 0;
            }
            return false;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** A walk to a cell known only once the route gets there. */
    private final class WalkTo extends Task {
        private final Supplier<Vec3i> goal;
        private Walk walk;

        WalkTo(String name, Supplier<Vec3i> goal) { super("walk " + name); this.goal = goal; }

        @Override boolean step(float dt) {
            if (walk == null) { walk = new Walk(goal.get()); }
            return walk.step(dt);
        }
    }

    /** Food handed to the gatekeeper through the conversation's own command until the ward counts the survivor a friend. */
    private final class Befriend extends Task {
        private float wait;
        private int gifts;

        Befriend() { super("give the ward food until it counts the survivor a friend"); }

        @Override boolean step(float dt) {
            if (game.uiMode != Game.UiMode.NPC || game.activeNpc == null) { throw fail("the gatekeeper's conversation closed"); }
            UrbanSite ward = UrbanSiteDirector.registered(game.world, plan.stronghold().site().id());
            if (ward.grantsAccess()) {
                deeds.add("the ward's friend after " + gifts + " handovers (trust " + Math.round(ward.localReputation) + ")");
                return true;
            }
            if ((wait += dt) < .4f) { return false; }
            wait = 0;
            if (++gifts > 5) { throw fail("the ward never counted the survivor a friend: " + ward.localReputation); }
            game.npcScreen.performSiteChoice(game, game.activeNpc, 2);
            deeds.add("2:" + game.npcScreen.lastResponse());
            return false;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /**
     * F on a container from where the route stands, its search waited out, the storage screen's
     * status checked and captured, and the stack of one item taken through the screen's own command.
     */
    private final class Take extends Task {
        private final String label, owner;
        private final Supplier<BuildingPlan.Fixture> container;
        private final ItemType item;
        private float wait;
        private int presses;
        private boolean taken;

        Take(String label, Supplier<BuildingPlan.Fixture> container, ItemType item, String owner) {
            super("take the " + item); this.label = label; this.container = container; this.item = item; this.owner = owner;
        }

        @Override boolean step(float dt) {
            Vec3i box = container.get().position();
            if (game.uiMode == Game.UiMode.CRATE) {
                if (game.crateSearching()) { return false; }
                if (!taken) {
                    String status = game.crateStatus();
                    if (owner != null && !status.contains(owner)) { throw fail("the storage says '" + status + "'"); }
                    Inventory open = game.openCrate;
                    for (int i = 0; open != null && i < open.size(); i++) {
                        ItemStack s = open.get(i);
                        if (s != null && s.type == item) {
                            if (game.transferCrateItemToPlayer(i) <= 0) { throw fail("could not take the " + item); }
                            taken = true;
                            break;
                        }
                    }
                    if (!taken) { throw fail("no " + item + " at " + box + ": " + status); }
                    views.add(label + "=" + status);
                    capture(label);
                    deeds.add("took the " + item + " from " + box);
                    return false;
                }
                press(GLFW_KEY_ESCAPE);
                return false;
            }
            if (taken) { return true; }
            if (game.uiMode == Game.UiMode.NPC) {
                press(GLFW_KEY_ESCAPE);
                return false;
            }
            hand(ItemType.IRON_PICKAXE);
            lookAtBlock(box, .5f);
            if ((wait += dt) > .4f) {
                if (++presses > 10) { throw fail("the storage at " + box + " never opened: " + prompt()); }
                press(GLFW_KEY_F);
                wait = 0;
            }
            return false;
        }

        @Override boolean threatsInterrupt() { return game.uiMode == Game.UiMode.NONE && !taken; }
    }

    // ------------------------------------------------------------------
    // The relay
    // ------------------------------------------------------------------

    /** The relay's cabinet, dead: its line says what it lacks. */
    private final class DeadRelay extends Task {
        DeadRelay() { super("look at the dead relay"); }

        @Override boolean step(float dt) {
            hand(ItemType.IRON_PICKAXE);
            aimRelay();
            if (age < .5f) { return false; }
            if (!prompt().equals("Relay mast: needs antenna cable and power cells")) {
                if (age > 6) { throw fail("the relay reads '" + prompt() + "'"); }
                return false;
            }
            views.add("09-the-dead-relay=" + prompt());
            capture("09-the-dead-relay");
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** A part in hand and the use button at the cabinet: the part fitted. */
    private final class Fit extends Task {
        private final ItemType part;
        private final String label;
        private boolean pressed;
        private float wait;

        Fit(ItemType part, String label) { super("fit the " + part); this.part = part; this.label = label; }

        @Override boolean step(float dt) {
            int bit = part == ItemType.ANTENNA_CABLE ? UrbanMissions.RELAY_ANTENNA : UrbanMissions.RELAY_CELLS;
            if ((ledger().comms.installed & bit) != 0) {
                if (label == null) { return true; }
                hand(ItemType.IRON_PICKAXE);
                aimRelay();
                if ((wait += dt) < .6f) { return false; }
                if (!prompt().equals("Relay mast: repaired, listening for a radio")) {
                    if (wait > 6) { throw fail("the repaired relay reads '" + prompt() + "'"); }
                    return false;
                }
                views.add(label + "=" + prompt());
                capture(label);
                return true;
            }
            if (!hand(part)) { throw fail("no " + part + " in hand"); }
            aimRelay();
            if ((wait += dt) < .4f) { return false; }
            wait = 0;
            if (!prompt().startsWith("[RMB]")) {
                if (age > 6) { throw fail("the relay offers '" + prompt() + "' with the " + part); }
                return false;
            }
            if (pressed && age > 6) { throw fail("the " + part + " was never fitted"); }
            rightClick();
            pressed = true;
            return false;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    // ------------------------------------------------------------------
    // The refuge: building, wiring and the call
    // ------------------------------------------------------------------

    /** The crafting screen opened with C at the bench, and things built through its own command. */
    private final class Craft extends Task {
        private final String[] recipes;
        private int phase;
        private float wait;

        Craft(String... recipes) { super("build " + String.join(", ", recipes)); this.recipes = recipes; }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            switch (phase) {
                case 0 -> {
                    lookAtBlock(plan.bench().position(), .6f);
                    press(GLFW_KEY_C);
                    phase = 1;
                    wait = 0;
                }
                case 1 -> {
                    if (game.uiMode != Game.UiMode.CRAFTING) {
                        if ((wait += dt) > .5f) {
                            if (age > 5) { throw fail("the crafting screen never opened: " + game.uiMode); }
                            phase = 0;
                        }
                        return false;
                    }
                    for (String name : recipes) {
                        Recipe r = CraftingSystem.recipesFor(game.world).stream().filter(x -> x.name.equals(name)).findFirst()
                                .orElseThrow(() -> fail("no recipe " + name));
                        if (CraftingScreen.craft(game, r, game.nearbyStations()) == null) { throw fail("could not build " + name); }
                        deeds.add("built " + name);
                    }
                    phase = 2;
                }
                case 2 -> {
                    press(GLFW_KEY_C);
                    phase = 3;
                    wait = 0;
                }
                default -> {
                    if (game.uiMode == Game.UiMode.NONE) { return true; }
                    if ((wait += dt) > .5f) { phase = 2; }
                }
            }
            return false;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** Walks to a cell, aims at the top of the floor under the device's cell and presses the use button. */
    private final class Put extends Task {
        private final ItemType item;
        private final Vec3i at, from;
        private Walk walk;
        private float wait;
        private int presses;

        Put(ItemType item, Vec3i at, Vec3i from) { super("put the " + item + " down at " + at); this.item = item; this.at = at; this.from = from; }

        @Override boolean step(float dt) {
            if (walk == null) { walk = new Walk(from); }
            if (!walk.step(dt)) { return false; }
            if (game.world.urban.utilities.device(at) != null) {
                deeds.add("put " + item + "@" + at);
                return true;
            }
            if (!hand(item)) { throw fail("no " + item + " to put down"); }
            look(at.x() + .5f, at.y() + .02f, at.z() + .5f);
            var t = game.targetHit;
            boolean floor = t != null && t.x() == at.x() && t.y() == at.y() - 1 && t.z() == at.z() && t.ny() == 1;
            if (!floor) {
                if ((wait += dt) > 4) { throw fail("the floor under " + at + " is not in reach: aimed " + t); }
                return false;
            }
            if ((wait += dt) > .4f) {
                if (++presses > 6) { throw fail("the " + item + " was never put down at " + at); }
                rightClick();
                wait = 0;
            }
            return false;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** The fuel can poured into the generator with the use button. */
    private final class Fuel extends Task {
        private float wait;

        Fuel() { super("fuel the generator"); }

        @Override boolean step(float dt) {
            Device g = game.world.urban.utilities.device(plan.generator());
            if (g != null && g.fuel > 0) { return true; }
            if (!hand(ItemType.FUEL_CAN)) { throw fail("no fuel can"); }
            lookAtBlock(plan.generator(), .5f);
            if ((wait += dt) > .4f) {
                if (age > 6) { throw fail("the generator never took the fuel: " + prompt()); }
                rightClick();
                wait = 0;
            }
            return false;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** F on a device whose line starts as expected: switched on. */
    private final class Switch extends Task {
        private final Vec3i device;
        private final String expected;
        private boolean pressed;
        private float wait;

        Switch(Vec3i device, String expected) { super("switch on " + device); this.device = device; this.expected = expected; }

        @Override boolean step(float dt) {
            Device d = game.world.urban.utilities.device(device);
            if (d != null && d.on) { return true; }
            hand(ItemType.IRON_PICKAXE);
            lookAtBlock(device, .5f);
            if ((wait += dt) < .4f) { return false; }
            wait = 0;
            if (!prompt().startsWith(expected)) {
                if (age > 6) { throw fail(device + " reads '" + prompt() + "'"); }
                return false;
            }
            if (pressed && age > 6) { throw fail("F never switched " + device + " on"); }
            press(GLFW_KEY_F);
            pressed = true;
            return false;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** The call going out on the radio: its line counts the seconds. */
    private final class OnAir extends Task {
        OnAir() { super("on the air"); }

        @Override boolean step(float dt) {
            hand(ItemType.IRON_PICKAXE);
            lookAtBlock(plan.radio(), .5f);
            if (ledger().comms.broadcast < 5f || !prompt().contains("broadcasting")) {
                if (age > 30) { throw fail("nothing went out: " + prompt() + " " + state()); }
                return false;
            }
            views.add("11-on-the-air=" + prompt());
            capture("11-on-the-air");
            return true;
        }
    }

    /** The city answers: the ending card, then Escape carries on surviving. */
    private final class Answered extends Task {
        private int phase;
        private float wait;

        Answered() { super("the city answers"); }

        @Override boolean step(float dt) {
            switch (phase) {
                case 0 -> {
                    if (!ledger().comms.complete) {
                        if (age > 180) { throw fail("the call never finished: " + state()); }
                        return false;
                    }
                    if (game.appState != Game.AppState.VICTORY) { throw fail("no ending card: " + game.appState); }
                    if ((wait += dt) < .6f) { return false; }
                    answered = true;
                    capture("12-the-city-answers");
                    phase = 1;
                    wait = 0;
                }
                case 1 -> {
                    press(GLFW_KEY_ESCAPE);
                    phase = 2;
                }
                case 2 -> {
                    if (game.appState != Game.AppState.PLAYING) {
                        if ((wait += dt) > 3) { throw fail("Escape did not carry on: " + game.appState); }
                        return false;
                    }
                    if (game.world == null) { throw fail("the card ended the game"); }
                    if ((wait += dt) < 1.2f) { return false; }
                    carriedOn = true;
                    views.add("13-carrying-on=" + UrbanComms.hudLine(game));
                    capture("13-carrying-on");
                    return true;
                }
                default -> { }
            }
            return false;
        }

        @Override boolean threatsInterrupt() { return phase == 0; }
    }

    // ------------------------------------------------------------------
    // Saving and the report
    // ------------------------------------------------------------------

    private final class Record extends Task {
        Record() { super("record the objective and the missions"); }

        @Override boolean step(float dt) {
            before = state();
            System.out.println("[urban-missions] before-save " + before);
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    private final class Reloaded extends Task {
        Reloaded() { super("the same objective and missions after the load"); }

        @Override boolean step(float dt) {
            if (age < .8f) { return false; }
            String after = state();
            if (!after.equals(before)) { throw fail("the load changed the missions:\n  " + before + "\n  " + after); }
            if (game.appState != Game.AppState.PLAYING) { throw fail("a load showed the ending again: " + game.appState); }
            reloaded = true;
            views.add("15-after-reload=" + UrbanMissionBoard.journalLine(game));
            capture("15-after-reload");
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    private final class Report extends Task {
        Report() { super("report"); }

        @Override boolean step(float dt) {
            Mission aid = mission(Kind.HIDDEN_AID), mast = mission(Kind.LANDMARK), module = mission(Kind.STRONGHOLD_COMPONENT);
            boolean ok = answered && carriedOn && reloaded && captures == CAPTURES && ledger().comms.complete
                    && aid.status == Status.COMPLETED && aid.rewarded && mast.status == Status.COMPLETED && mast.rewarded
                    && module.status == Status.COMPLETED && mission(Kind.CLINIC_SUPPLIES).status == Status.ACTIVE;
            if (!ok) { throw fail("incomplete: captures=" + captures + " " + state()); }
            passed = true;
            reported = true;
            System.out.println("[urban-missions] pass=true mode=" + game.gameMode().name().toLowerCase(Locale.ROOT) + " seed="
                    + game.world.seed + " poses=" + poses + " captures=" + captures + " health=" + game.player.health
                    + " routeSeconds=" + Math.round(elapsed));
            System.out.println("[urban-missions] state " + state());
            System.out.println("[urban-missions] deeds " + String.join(" | ", deeds));
            System.out.println("[urban-missions] views " + String.join(" | ", views));
            System.out.println("[urban-missions] timeline " + String.join(" ", timeline));
            game.automation.finishScriptedRun();
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }
}
