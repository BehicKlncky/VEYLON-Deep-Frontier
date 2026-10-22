package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.UrbanSite;
import com.veylon.settlement.UrbanSiteDirector;
import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.VoxelClearance;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanPlaces.Place;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The milestone-16 survivor tour ({@code VEYLON_SCENE=urban_survivors}) on a generator-15 world
 * from {@code VEYLON_GENERATOR=urban-dev}, in either game mode, walked with ordinary controls (W,
 * F, Escape, F5, F9, right click and mouse look through the production input seam):
 * <ol>
 *   <li>by day, the camp nearest the spawn, entered by its unlatched rear door; a trade with its
 *       trader from the camp's own stores; food and water given until the camp counts the player
 *       a friend; and its storage, now shared;</li>
 *   <li>the hidden refuge nearest the spawn: a walk from its street door to the basement hall,
 *       the people heard behind the wall before anything is found, the loose panel slid aside
 *       with F, the civilians inside, supplies given until they trust the player, and word of the
 *       camp, which they agree to move to;</li>
 *   <li>F5 and F9: the same people, standing and plans;</li>
 *   <li>a street far from both until both go dormant and the civilians set out;</li>
 *   <li>at night, the camp: its people at their beds and the lookout at the rear door, one of
 *       them seen by a torch the player sets down; the next morning, the civilians in the spare
 *       beds upstairs;</li>
 *   <li>the empty refuge, whose shelf they left to the player.</li>
 * </ol>
 * Every frame, every survivor on screen must stand clear of solid blocks and inside its home's
 * held cells (a camp lookout within its leash of the post). Conversation choices go through the
 * conversation screen's own command, and items leave a container through the storage screen's
 * transfer command, the seams their keys and clicks call. The poses between legs, the clock
 * (only ever forward), the weather and a kit of a spear, trade goods, food, water and torches are
 * staged and printed as staged. No block is staged. Exactly sixteen labelled captures. Failures
 * throw.
 */
final class UrbanSurvivorQaScene extends UrbanRouteScene {
    static final double DEADLINE_SECONDS = 900;
    static final int CAPTURES = 16;
    static final String HEARD = "Somewhere behind the wall, someone is trying very hard to be quiet.";
    private static final float REACH = 2.2f;

    private UrbanSurvivorTourPlan plan;
    private boolean passed, campFound, traded, befriended, shared, heard, refugeFound, guided, reloaded, dormant,
            bedtime, guestsSeen, emptied, leftToYou;
    private int poses, captures, residentFrames, wallFrames, strayFrames;
    private String wallNote = "", strayNote = "", before = "";
    private final List<String> views = new ArrayList<>(), deeds = new ArrayList<>();

    UrbanSurvivorQaScene(Game game) { super(game); }

    @Override protected String tag() { return "urban-survivors"; }
    @Override protected double deadline() { return DEADLINE_SECONDS; }

    boolean passed() { return passed; }
    UrbanSurvivorTourPlan plan() { return plan; }
    int residentFrames() { return residentFrames; }

    @Override
    protected void capture(String label) {
        captures++;
        super.capture(label);
    }

    /** Plans the tour on the current generated world and stages the kit; no block is changed. */
    void stage() {
        plan = UrbanSurvivorTourPlan.of(game.world);
        Inventory inv = game.player.inventory;
        inv.clear();
        inv.set(7, new ItemStack(ItemType.IRON_SPEAR, 1));
        // Food and water for six gifts, one of every trader's wanted goods, and torches: light enough to carry.
        inv.add(ItemType.DRIED_BERRY, 40);
        inv.add(ItemType.WATERSKIN_CLEAN, 4);
        inv.add(ItemType.SCRAP, 4);
        inv.add(ItemType.PLANK, 2);
        inv.add(ItemType.FIBER, 4);
        inv.add(ItemType.TORCH, 4);
        inv.add(ItemType.MEDICINE, 1);
        System.out.println("[urban-survivors] stage seed=" + game.world.seed + " generator=" + game.world.generatorVersion
                + " mode=" + game.gameMode().name().toLowerCase(Locale.ROOT)
                + " evidence=walked-legs(W,F,Esc,F5,F9,right-click,mouse-look); conversation-choices(NpcScreen.performSiteChoice),"
                + "storage-transfer(Game.transferCrateItemToPlayer); staged-poses-between-legs,staged-clock(forward),"
                + "staged-kit(spear,food,water,trade-goods,torches); generated-geometry; normal-simulation");
        System.out.println("[urban-survivors] plan " + plan);
        long camp = plan.camp().site().id(), refuge = plan.refuge().site().id();
        add(new Clock("day", 10 * 60), new PoseYard(), new Wait("stream", 1f),
                new View("01-camp-rear-door", () -> plan.camp().door(), .9f, null),
                new Walk(plan.camp().site().entry()), new Arrived(),
                new Talk("the trader", () -> liveAt(camp, 1)), new Trade(),
                new Befriend(), new Leave(),
                new OpenStore("06-shared-camp-storage", () -> plan.camp().site().stash().getFirst(),
                        () -> plan.camp().site().meals().getFirst(), "shared with you as a friend", false));
        var r = plan.refuge();
        add(new Pose(r.street(), r.hall()), new Wait("stream", 1f), new Walk(r.hall()), new Listen(), new Slide(),
                new Walk(r.inside()), new View("09-hidden-civilians", () -> nearestLive(refuge), 1.4f, Place.HIDDEN_CELLAR),
                new Talk("a civilian", () -> nearestLiveNpc(refuge)), new Say(1, "10-a-civilian-speaks", s -> !s.isEmpty()),
                new Guide(), new Leave(), new Record(), new Save(), new Load(), new Reloaded());
        add(new PoseAway(), new Dormant(), new Clock("night", 23 * 60), new Wait("night", 1f),
                new PoseYard(), new Wait("stream", 1f), new Walk(plan.camp().site().entry()), new Bedtime(),
                new Clock("morning", 9 * 60), new Wait("morning", 1f), new Guests());
        add(new Pose(r.street(), r.hall()), new Wait("stream", 1f), new Walk(r.hall()),
                new Walk(r.inside()), new Emptied(),
                new OpenStore("16-left-to-you", () -> r.site().stash().getFirst(), () -> r.site().meals().getFirst(),
                        "Left to you by the people who hid here", true),
                new Report());
        staged = true;
    }

    // ------------------------------------------------------------------
    // The people
    // ------------------------------------------------------------------

    private UrbanSite site(long id) {
        return UrbanSiteDirector.registered(game.world, id);
    }

    private UrbanSite camp() { return site(plan.camp().site().id()); }

    private UrbanSite refuge() { return site(plan.refuge().site().id()); }

    private List<Npc> live(long id) {
        List<Npc> out = new ArrayList<>();
        UrbanSite s = site(id);
        if (s == null) { return out; }
        for (Settlement.Resident r : s.residents) {
            if (r.live != null && !r.live.dead && game.entities.npcs.contains(r.live)) { out.add(r.live); }
        }
        return out;
    }

    private Npc liveAt(long id, int slot) {
        UrbanSite s = site(id);
        UrbanSite.Resident r = s == null ? null : s.resident(slot);
        return r != null && r.live != null && !r.live.dead ? r.live : null;
    }

    private Npc nearestLiveNpc(long id) {
        Npc best = null;
        for (Npc n : live(id)) {
            if (best == null || n.distSqTo(game.player) < best.distSqTo(game.player)) { best = n; }
        }
        return best;
    }

    private Vec3i nearestLive(long id) {
        Npc n = nearestLiveNpc(id);
        return n == null ? null : cell(n.pos.x, n.pos.y, n.pos.z);
    }

    private float distanceTo(Npc n) {
        return (float) Math.hypot(n.pos.x - game.player.pos.x, n.pos.z - game.player.pos.z);
    }

    /** Who lives where, how they are and what they hold: compared across the save. */
    private String identities() {
        StringBuilder b = new StringBuilder();
        for (UrbanSite s : new UrbanSite[]{camp(), refuge()}) {
            if (s == null) { b.append("unregistered;"); continue; }
            b.append(UrbanIds.format(s.id)).append(" trust=").append(Math.round(s.localReputation))
                    .append(" fear=").append(Math.round(s.fear)).append(' ').append(s.relocation)
                    .append(" owed=").append(s.owed).append(" stash=").append(stash(s)).append(" [");
            for (Settlement.Resident base : s.residents) {
                UrbanSite.Resident r = (UrbanSite.Resident) base;
                b.append(UrbanIds.format(r.id)).append(':').append(r.name).append(r.alive ? "" : "+")
                        .append(String.format(Locale.ROOT, " %.1f/%.1f/%.1f, ", r.health, r.hunger, r.thirst));
            }
            b.append("];");
        }
        return b.toString();
    }

    /** Items in a site's own stash containers, as their inventories hold them. */
    private int stash(UrbanSite s) {
        int n = 0;
        for (Vec3i p : s.plan.stash()) {
            Inventory inv = game.world.crateContents.get(p);
            if (inv == null) { continue; }
            for (int i = 0; i < inv.size(); i++) {
                if (inv.get(i) != null) { n += inv.get(i).count; }
            }
        }
        return n;
    }

    // ------------------------------------------------------------------
    // Every frame: nobody in a wall, nobody out of their home
    // ------------------------------------------------------------------

    @Override
    protected void observe() {
        if (plan == null) { return; }
        for (Settlement settlement : game.world.settlements.values()) {
            if (!(settlement instanceof UrbanSite s)) { continue; }
            SurvivorSites.Site home = s.home(game.world.urban);
            for (Settlement.Resident base : s.residents) {
                UrbanSite.Resident r = (UrbanSite.Resident) base;
                Npc n = r.live;
                if (n == null || n.dead || !game.entities.npcs.contains(n)) { continue; }
                residentFrames++;
                Vec3i f = cell(n.pos.x, n.pos.y, n.pos.z);
                if (VoxelClearance.collides(game.world, n.pos.x, n.pos.y + .05f, n.pos.z, .5f, 1.6f, false)) {
                    if (wallFrames++ == 0) { wallNote = r.name + "@" + f; }
                }
                boolean held = home.holds(f.x(), f.y(), f.z()) || home.holds(f.x(), f.y() - 1, f.z());
                boolean leashed = s.camp() && r.role == SurvivorSites.Role.LOOKOUT && home.post() != null
                        && Math.hypot(f.x() - home.post().x(), f.z() - home.post().z()) <= com.veylon.ai.UrbanResidentAI.LEASH
                        && Math.abs(f.y() - home.post().y()) <= 1;
                if (!held && !leashed && strayFrames++ == 0) { strayNote = r.name + "@" + f + " home=" + UrbanIds.format(home.id()); }
            }
        }
    }

    // ------------------------------------------------------------------
    // Staging
    // ------------------------------------------------------------------

    /** Staged time of day, only ever forward, with clear weather pinned. */
    private final class Clock extends Task {
        private final int minutes;

        Clock(String name, int minutes) { super("clock " + name); this.minutes = minutes; }

        @Override boolean step(float dt) {
            double now = game.time.totalMinutes;
            double target = Math.floor(now / (24 * 60)) * 24 * 60 + minutes;
            if (target <= now) { target += 24 * 60; }
            game.time.totalMinutes = target;
            game.weather.current = game.weather.next = Weather.CLEAR;
            game.weather.blend = 1f;
            game.weather.changeTimer = 4000f;
            System.out.println("[urban-survivors] staged-clock " + name.substring(6) + " minutes=" + minutes
                    + " advancedBy=" + Math.round(target - now));
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    private void stagePose(Vec3i at, Vec3i facing, float height) {
        releaseAll();
        fighting = null;
        approach = null;
        game.closeScreens();
        game.world.ensureChunks(at.x(), at.z(), 4, 10_000);
        game.player.pos.set(at.x() + .5f, at.y() + .001f, at.z() + .5f);
        game.player.vel.zero();
        game.player.onGround = true;
        look(facing.x() + .5f, facing.y() + height, facing.z() + .5f);
        poses++;
        System.out.println("[urban-survivors] staged-pose " + at);
    }

    /** A staged pose: feet on a leg's first cell, facing along it. */
    private final class Pose extends Task {
        private final Vec3i at, facing;

        Pose(Vec3i at, Vec3i facing) { super("pose " + at); this.at = at; this.facing = facing; }

        @Override boolean step(float dt) {
            stagePose(at, facing, 1f);
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** In the camp's yard, a few steps out from its rear door, facing it. */
    private final class PoseYard extends Task {
        PoseYard() { super("pose in the camp's yard"); }

        @Override boolean step(float dt) {
            Vec3i door = plan.camp().door(), yard = plan.camp().yard();
            game.world.ensureChunks(yard.x(), yard.z(), 4, 10_000);
            int dx = yard.x() - door.x(), dz = yard.z() - door.z();
            for (int k = 3; k >= 0; k--) {
                for (int dy : new int[]{0, -1, 1}) {
                    Vec3i c = new Vec3i(yard.x() + dx * k, yard.y() + dy, yard.z() + dz * k);
                    if (VoxelClearance.standable(game.world, c.x(), c.y(), c.z())
                            && UrbanSlicePlan.route(game.world, c, plan.camp().site().entry(), cell -> true) != null) {
                        stagePose(c, door, .9f);
                        return true;
                    }
                }
            }
            throw fail("no standing cell in the yard behind " + door);
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** A sidewalk corner far from both sites. */
    private final class PoseAway extends Task {
        PoseAway() { super("pose far from both sites"); }

        @Override boolean step(float dt) {
            Vec3i a = plan.away();
            game.world.ensureChunks(a.x(), a.z(), 4, 10_000);
            Vec3i at = new Vec3i(a.x(), game.world.urban.groundY(a.x(), a.z()) + 1, a.z());
            stagePose(at, at.offset(0, 0, -4), 1f);
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    // ------------------------------------------------------------------
    // Looking, talking and storage
    // ------------------------------------------------------------------

    /** Looks at a subject, checks the place the HUD names, then captures. */
    private final class View extends Task {
        private final String label;
        private final Supplier<Vec3i> subject;
        private final float height;
        private final Place expected;

        View(String label, Supplier<Vec3i> subject, float height, Place expected) {
            super("view " + label); this.label = label; this.subject = subject; this.height = height; this.expected = expected;
        }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            Vec3i s = subject.get();
            if (s == null) {
                if (age > 15) { throw fail("nothing to see at " + label); }
                return false;
            }
            lookAtBlock(s, height);
            if (age < .5f) { return false; }
            if (expected != null && game.player.place != expected) {
                throw fail("expected " + expected + " at " + label + " but the HUD names " + game.player.place);
            }
            views.add(label + "=" + game.player.locationLine());
            capture(label);
            return true;
        }
    }

    /** Inside the rear door: the camp is known, its place named and its people here. */
    private final class Arrived extends Task {
        Arrived() { super("arrive inside the camp"); }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            UrbanSite s = camp();
            Npc n = nearestLiveNpc(plan.camp().site().id());
            if (s == null || !s.discovered || n == null || game.player.place != Place.CAMP) {
                if (age > 15) {
                    throw fail("the camp was not met: registered=" + (s != null) + " discovered=" + (s != null && s.discovered)
                            + " live=" + live(plan.camp().site().id()).size() + " place=" + game.player.place);
                }
                return false;
            }
            look(n.pos.x, n.pos.y + 1.4f, n.pos.z);
            if (age < .6f) { return false; }
            campFound = true;
            views.add("02-inside-the-camp=" + game.player.locationLine() + " people=" + live(s.id).size());
            capture("02-inside-the-camp");
            return true;
        }
    }

    /** Walks up to a person until within reach with a clear line, then presses F until they talk. */
    private final class Talk extends Task {
        private final Supplier<Npc> who;
        private Walk walk;
        private Vec3i to;
        private float walkAge, wait;
        private int presses;

        Talk(String name, Supplier<Npc> who) { super("talk to " + name); this.who = who; }

        @Override boolean step(float dt) {
            Npc n = who.get();
            if (game.uiMode == Game.UiMode.NPC) {
                if (game.activeNpc == n && n != null) { return true; }
                press(GLFW_KEY_ESCAPE); // someone else answered: leave and step closer
                return false;
            }
            if (n == null) {
                if (age > 20) { throw fail("nobody to " + name); }
                return false;
            }
            if (distanceTo(n) > REACH || Math.abs(n.pos.y - game.player.pos.y) > 1.2f || !game.interactions.clearPathTo(n)) {
                Vec3i target = cell(n.pos.x, n.pos.y, n.pos.z);
                walkAge += dt;
                if (walk == null || walkAge > .75f && !target.equals(to)) {
                    walk = new Walk(target);
                    to = target;
                    walkAge = 0;
                }
                if (walk.step(dt)) { walk = null; }
                return false;
            }
            hold(GLFW_KEY_W, false);
            look(n.pos.x, n.pos.y + 1.3f, n.pos.z);
            if ((wait += dt) > .4f) {
                if (++presses > 10) { throw fail("no conversation with " + n.name + ": " + game.interactPrompt); }
                press(GLFW_KEY_F);
                wait = 0;
            }
            return false;
        }

        @Override boolean threatsInterrupt() { return game.uiMode == Game.UiMode.NONE; }
    }

    /** One conversation choice through the screen's own command; checks the answer, may capture. */
    private final class Say extends Task {
        private final int choice;
        private final String label;
        private final Predicate<String> expected;

        Say(int choice, String label, Predicate<String> expected) {
            super("say [" + choice + "]"); this.choice = choice; this.label = label; this.expected = expected;
        }

        @Override boolean step(float dt) {
            if (game.uiMode != Game.UiMode.NPC || game.activeNpc == null) { throw fail("the conversation closed before " + name); }
            if (age < .3f) { return false; }
            if (!game.npcScreen.performSiteChoice(game, game.activeNpc, choice)) { throw fail("choice " + choice + " is not offered"); }
            String said = game.npcScreen.lastResponse();
            deeds.add(choice + ":" + said);
            if (!expected.test(said)) { throw fail("unexpected answer to [" + choice + "]: " + said); }
            if (label != null) { capture(label); }
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** A trade with the camp's trader from the camp's own stores; the second offer if the first is out. */
    private final class Trade extends Task {
        private int step;

        Trade() { super("trade with the camp"); }

        @Override boolean step(float dt) {
            if (game.uiMode != Game.UiMode.NPC) { throw fail("the trader's conversation closed"); }
            if (age < .4f) { return false; }
            if (step == 0) {
                var offers = com.veylon.settlement.UrbanSiteDialogue.offers(game, game.activeNpc);
                if (offers.size() != 2) { throw fail("the trader keeps " + offers.size() + " offers"); }
                deeds.add("offers=" + offers.get(0).describe() + " | " + offers.get(1).describe());
                capture("03-trader-conversation");
                step = 1;
                return false;
            }
            if (age < 1f) { return false; }
            for (int choice : new int[]{4, 5}) {
                game.npcScreen.performSiteChoice(game, game.activeNpc, choice);
                String said = game.npcScreen.lastResponse();
                deeds.add(choice + ":" + said);
                if (said.equals("A fair trade.")) {
                    traded = true;
                    capture("04-a-fair-trade");
                    return true;
                }
            }
            throw fail("no trade went through: " + String.join(" | ", deeds));
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** Food and water given into the camp's stores until it counts the player a friend. */
    private final class Befriend extends Task {
        private float wait;
        private int gifts;

        Befriend() { super("give the camp food and water"); }

        @Override boolean step(float dt) {
            if (game.uiMode != Game.UiMode.NPC) { throw fail("the conversation closed while giving"); }
            UrbanSite s = camp();
            if (s.grantsAccess()) {
                if (age < gifts * .5f + .6f) { return false; }
                befriended = true;
                deeds.add("camp-trust=" + Math.round(s.localReputation) + " after " + gifts + " gifts");
                capture("05-the-camp-counts-you-a-friend");
                return true;
            }
            if ((wait += dt) < .5f) { return false; }
            wait = 0;
            if (++gifts > 6) { throw fail("the camp never warmed to the player: trust " + s.localReputation); }
            game.npcScreen.performSiteChoice(game, game.activeNpc, 2);
            String said = game.npcScreen.lastResponse();
            if (!said.equals("That helps. We won't forget it.")) { throw fail("the camp refused supplies: " + said); }
            return false;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** Escape until no screen is open. */
    private final class Leave extends Task {
        private float wait;

        Leave() { super("leave the screen"); }

        @Override boolean step(float dt) {
            if (game.uiMode == Game.UiMode.NONE) { return age > .2f; }
            if ((wait += dt) > .3f) {
                if (age > 5) { throw fail("the screen would not close: " + game.uiMode); }
                press(GLFW_KEY_ESCAPE);
                wait = 0;
            }
            return false;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /**
     * Walks to the cell in front of a site container, waits for nobody to stand within talking
     * reach (F would greet them first), opens it with F, waits out the search, checks the storage
     * screen's owner line, captures, may take one item through the screen's transfer command, and
     * closes it.
     */
    private final class OpenStore extends Task {
        private final String label, owner;
        private final Supplier<Vec3i> container, stand;
        private final boolean take;
        private Walk walk;
        private float wait;
        private int presses;
        private boolean captured;

        OpenStore(String label, Supplier<Vec3i> container, Supplier<Vec3i> stand, String owner, boolean take) {
            super("open storage for " + label); this.label = label; this.container = container; this.stand = stand;
            this.owner = owner; this.take = take;
        }

        @Override boolean step(float dt) {
            Vec3i box = container.get();
            if (game.uiMode == Game.UiMode.CRATE) {
                if (game.crateSearching()) { return false; }
                if (!captured) {
                    String status = game.crateStatus();
                    if (!status.contains(owner)) { throw fail("the storage says '" + status + "', expected '" + owner + "'"); }
                    views.add(label + "=" + status);
                    capture(label);
                    captured = true;
                    if (take) { takeOne(); }
                    return false;
                }
                press(GLFW_KEY_ESCAPE);
                return false;
            }
            if (captured) {
                if (label.startsWith("06")) { shared = true; } else { leftToYou = true; }
                return true;
            }
            if (game.uiMode == Game.UiMode.NPC) { // somebody stepped in: leave them be and try again
                press(GLFW_KEY_ESCAPE);
                return false;
            }
            if (walk == null) { walk = new Walk(stand.get()); }
            if (!walk.step(dt)) { return false; }
            lookAtBlock(box, .5f);
            for (Npc n : game.entities.npcs) {
                if (!n.dead && n.distSqTo(game.player) < 3.4f * 3.4f) {
                    if (age > 40) { throw fail("someone always stood by the storage at " + box); }
                    return false;
                }
            }
            if ((wait += dt) > .4f) {
                if (++presses > 10) { throw fail("the storage at " + box + " never opened: " + game.interactPrompt); }
                press(GLFW_KEY_F);
                wait = 0;
            }
            return false;
        }

        /** One item out through the storage screen's own transfer command: no theft from a shelf left to you. */
        private void takeOne() {
            UrbanSite s = refuge();
            float trust = s.localReputation;
            int owed = s.owed;
            Object mark = logMark();
            Inventory open = game.openCrate;
            for (int i = 0; open != null && i < open.size(); i++) {
                if (open.get(i) == null) { continue; }
                String what = open.get(i).type.displayName;
                if (game.transferCrateItemToPlayer(i, 1) != 1) { throw fail("could not take " + what + " from the shelf"); }
                List<String> since = loggedAfter(mark);
                if (since.stream().anyMatch(l -> l.contains("take their supplies") || l.contains("know who took them"))
                        || s.localReputation != trust || s.owed != owed) {
                    throw fail("taking from a shelf left to you was theft: " + since);
                }
                deeds.add("took " + what + " from the shelf left behind");
                return;
            }
            deeds.add("the shelf left behind was bare");
        }

        @Override boolean threatsInterrupt() { return game.uiMode == Game.UiMode.NONE && !captured; }
    }

    // ------------------------------------------------------------------
    // The refuge
    // ------------------------------------------------------------------

    /** In the basement hall, looking at the plaster: the people behind it are heard before anything is found. */
    private final class Listen extends Task {
        private int mark = -1;

        Listen() { super("listen at the basement wall"); }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            lookAtBlock(plan.refuge().panel(), .9f);
            if (mark < 0) { mark = Math.max(0, game.eventLog.all().size() - 1); }
            UrbanSite s = refuge();
            if (s != null && s.discovered) { throw fail("the refuge was found before its panel moved"); }
            boolean logged = game.eventLog.all().stream().anyMatch(l -> l.endsWith(HEARD));
            if (!logged) {
                if (age > 30) { throw fail("nobody was heard behind the wall: live=" + live(plan.refuge().site().id()).size()); }
                return false;
            }
            heard = true;
            views.add("07-a-sound-behind-the-wall=" + game.player.locationLine());
            capture("07-a-sound-behind-the-wall");
            return true;
        }
    }

    /** Aims at the loose panel and slides it aside with F: the refuge is found. */
    private final class Slide extends Task {
        private float wait;
        private int presses;

        Slide() { super("slide the loose panel"); }

        @Override boolean step(float dt) {
            Vec3i panel = plan.refuge().panel();
            hold(GLFW_KEY_W, false);
            lookAtBlock(panel, .9f);
            if (game.world.getBlock(panel.x(), panel.y(), panel.z()) == BlockType.HIDDEN_PANEL_OPEN) {
                UrbanSite s = refuge();
                if (s == null || !s.discovered || !game.world.urban.discovery.discovered(plan.refuge().site().room())) {
                    throw fail("the panel moved but the refuge was not found");
                }
                if (age < .8f) { return false; }
                refugeFound = true;
                capture("08-panel-slid-aside");
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

    /**
     * Word of the camp, and while they answer that they do not know the player well enough,
     * supplies: until they agree to move.
     */
    private final class Guide extends Task {
        private static final String WARY = "We don't know you well enough to follow you anywhere.";
        private float wait;
        private int gifts;
        private boolean asked;

        Guide() { super("help the civilians and tell them of the camp"); }

        @Override boolean step(float dt) {
            if (game.uiMode != Game.UiMode.NPC) { throw fail("the civilian's conversation closed"); }
            if ((wait += dt) < .5f) { return false; }
            wait = 0;
            UrbanSite s = refuge();
            if (!asked) {
                if (!game.npcScreen.siteOptions(game, game.activeNpc).contains("[4] Tell them about the camp you found")) {
                    throw fail("the conversation offers no word of the camp: " + game.npcScreen.siteOptions(game, game.activeNpc));
                }
                game.npcScreen.performSiteChoice(game, game.activeNpc, 4);
                String said = game.npcScreen.lastResponse();
                deeds.add("4:" + said);
                if (s.relocation == UrbanSite.Relocation.DEPARTING) {
                    if (s.relocatedTo != plan.camp().site().id()) { throw fail("they chose another camp"); }
                    guided = true;
                    capture("11-they-agree-to-move");
                    return true;
                }
                if (!said.equals(WARY)) { throw fail("they would not move: " + said); }
                asked = true;
                return false;
            }
            if (++gifts > 6) { throw fail("the civilians never trusted the player: " + s.localReputation); }
            game.npcScreen.performSiteChoice(game, game.activeNpc, 2);
            deeds.add("2:" + game.npcScreen.lastResponse());
            asked = false;
            return false;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    // ------------------------------------------------------------------
    // Save, load, dormancy and the night
    // ------------------------------------------------------------------

    private final class Record extends Task {
        Record() { super("record who is who"); }

        @Override boolean step(float dt) {
            before = identities();
            System.out.println("[urban-survivors] before-save " + before);
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

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
            if (!loggedSince(mark, "Game saved.")) { throw fail("quick-save failed"); }
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    private final class Load extends Task {
        private boolean pressed;
        private Object mark, world;

        Load() { super("quick-load over the live world"); }

        @Override boolean step(float dt) {
            if (!pressed) {
                world = game.world;
                mark = logMark();
                press(GLFW_KEY_F9);
                pressed = true;
                return false;
            }
            if (!loggedSince(mark, "Game loaded.") || game.world == world) {
                throw fail("quick-load did not replace the live world");
            }
            world = null;
            fighting = null;
            approach = null;
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** After the load: the same people, needs, standing, debts, stores and plans. */
    private final class Reloaded extends Task {
        Reloaded() { super("the same people after the load"); }

        @Override boolean step(float dt) {
            String after = identities();
            if (!after.equals(before)) {
                throw fail("the sites changed across the save:\n before " + before + "\n after  " + after);
            }
            Npc n = nearestLiveNpc(plan.refuge().site().id());
            if (n == null) {
                if (age > 15) { throw fail("the civilians did not come back after the load"); }
                return false;
            }
            look(n.pos.x, n.pos.y + 1.3f, n.pos.z);
            if (age < .8f) { return false; }
            if (refuge().relocation != UrbanSite.Relocation.DEPARTING || !camp().grantsAccess()) {
                throw fail("the plans and friendship were lost: " + refuge().relocation + " " + camp().localReputation);
            }
            reloaded = true;
            capture("12-after-reload");
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** Far away: both sites go dormant and the civilians set out unseen. */
    private final class Dormant extends Task {
        Dormant() { super("wait far from both sites"); }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            UrbanSite r = refuge();
            if (!live(plan.camp().site().id()).isEmpty() || !live(plan.refuge().site().id()).isEmpty()
                    || r.relocation != UrbanSite.Relocation.TRAVELLING) {
                if (age > 45) {
                    throw fail("the sites never went dormant: camp=" + live(plan.camp().site().id()).size()
                            + " refuge=" + live(plan.refuge().site().id()).size() + " " + r.relocation);
                }
                return false;
            }
            dormant = true;
            deeds.add(String.format(Locale.ROOT, "set-out minute=%.0f arrive=%.0f", game.time.totalMinutes, r.arriveMinute));
            return true;
        }
    }

    /**
     * At night in the camp: its own people at their beds and the lookout at the post. The player
     * walks in to the nearest sleeper and sets a torch down with a right click to see them.
     */
    private final class Bedtime extends Task {
        private Npc sleeper;
        private Walk walk;
        private Vec3i to, floor;
        private float walkAge, wait, settle;
        private int clicks;

        Bedtime() { super("the camp at night"); }

        @Override boolean step(float dt) {
            UrbanSite s = camp();
            if (sleeper == null) {
                hold(GLFW_KEY_W, false);
                int settled = 0, alive = 0;
                Npc nearest = null;
                for (Settlement.Resident base : s.residents) {
                    UrbanSite.Resident r = (UrbanSite.Resident) base;
                    if (!r.alive) { continue; }
                    alive++;
                    Npc n = r.live;
                    if (n == null || n.dead) { continue; }
                    Vec3i want = r.role == SurvivorSites.Role.LOOKOUT ? s.plan.post() : s.plan.resident(r.slot).bed();
                    if (Math.hypot(n.pos.x - (want.x() + .5f), n.pos.z - (want.z() + .5f)) < 1.6f) {
                        settled++;
                        if (r.role != SurvivorSites.Role.LOOKOUT
                                && (nearest == null || n.distSqTo(game.player) < nearest.distSqTo(game.player))) { nearest = n; }
                    }
                }
                if (settled < alive || nearest == null) {
                    if (age > 40) { throw fail("the camp never settled for the night: " + settled + "/" + alive); }
                    return false;
                }
                sleeper = nearest;
                deeds.add("night: " + settled + "/" + alive + " at their beds and the post");
            }
            if (sleeper.dead || !game.entities.npcs.contains(sleeper)) { throw fail(sleeper.name + " is gone"); }
            if (floor == null && (distanceTo(sleeper) > 2.6f || Math.abs(sleeper.pos.y - game.player.pos.y) > 1.2f
                    || !game.interactions.clearPathTo(sleeper))) {
                Vec3i target = cell(sleeper.pos.x, sleeper.pos.y, sleeper.pos.z);
                walkAge += dt;
                if (walk == null || walkAge > .75f && !target.equals(to)) {
                    walk = new Walk(target);
                    to = target;
                    walkAge = 0;
                }
                if (walk.step(dt)) { walk = null; }
                return false;
            }
            hold(GLFW_KEY_W, false);
            if (floor == null) {
                floor = torchFloor(s);
                if (floor == null) { throw fail("no free floor for a torch beside " + feet()); }
            }
            if (game.world.getBlock(floor.x(), floor.y() + 1, floor.z()) != BlockType.TORCH) {
                if (!select(ItemType.TORCH)) { throw fail("no torch in the kit"); }
                lookAtBlock(floor, 1f);
                if ((wait += dt) > .5f) {
                    if (++clicks > 8) { throw fail("the torch was never placed above " + floor); }
                    rightClick();
                    wait = 0;
                }
                return false;
            }
            look(sleeper.pos.x, sleeper.pos.y + .3f, sleeper.pos.z); // a sleeper lies on their back on the floor
            if ((settle += dt) < .8f) { return false; }
            bedtime = true;
            views.add("13-the-camp-at-night=" + game.player.locationLine() + " " + sleeper.name);
            capture("13-the-camp-at-night");
            return true;
        }

        /**
         * A free floor cell beside the player for the torch: open above, solid below, nobody on
         * it and none of the camp's planned cells, nearest the sleeper.
         */
        private Vec3i torchFloor(UrbanSite s) {
            Vec3i f = feet(), best = null;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Vec3i c = f.offset(dx, 0, dz);
                    if (dx == 0 && dz == 0 || game.world.getBlock(c.x(), c.y(), c.z()) != BlockType.AIR
                            || game.world.getBlock(c.x(), c.y() + 1, c.z()) != BlockType.AIR
                            || !game.world.getBlock(c.x(), c.y() - 1, c.z()).solid || planned(s.plan, c) || occupied(c)) {
                        continue;
                    }
                    if (best == null || Math.hypot(c.x() + .5f - sleeper.pos.x, c.z() + .5f - sleeper.pos.z)
                            < Math.hypot(best.x() + .5f - sleeper.pos.x, best.z() + .5f - sleeper.pos.z)) { best = c; }
                }
            }
            return best == null ? null : best.offset(0, -1, 0);
        }

        private boolean planned(SurvivorSites.Site plan, Vec3i c) {
            if (c.equals(plan.entry()) || c.equals(plan.safe()) || c.equals(plan.post()) || plan.meals().contains(c)
                    || plan.guestBeds().contains(c)) {
                return true;
            }
            for (SurvivorSites.Resident r : plan.residents()) {
                if (c.equals(r.bed()) || c.equals(r.day())) { return true; }
            }
            return false;
        }

        private boolean occupied(Vec3i c) {
            for (Npc n : game.entities.npcs) {
                if (!n.dead && Math.hypot(n.pos.x - (c.x() + .5f), n.pos.z - (c.z() + .5f)) < .9f
                        && Math.abs(n.pos.y - c.y()) < 1.5f) { return true; }
            }
            return false;
        }
    }

    /** Up to the spare beds: the civilians arrived, the same people, inside the camp. */
    private final class Guests extends Task {
        private Walk walk;
        private Vec3i to;
        private float walkAge;

        Guests() { super("visit the civilians in the camp"); }

        @Override boolean step(float dt) {
            UrbanSite r = refuge();
            Npc n = r.relocation == UrbanSite.Relocation.ARRIVED ? nearestLiveNpc(r.id) : null;
            if (n == null) {
                if (age > 40) { throw fail("the civilians never arrived: " + r.relocation + " live=" + live(r.id).size()); }
                return false;
            }
            if (distanceTo(n) > REACH + .6f || Math.abs(n.pos.y - game.player.pos.y) > 1.2f || !game.interactions.clearPathTo(n)) {
                Vec3i target = cell(n.pos.x, n.pos.y, n.pos.z);
                walkAge += dt;
                if (walk == null || walkAge > .75f && !target.equals(to)) {
                    walk = new Walk(target);
                    to = target;
                    walkAge = 0;
                }
                if (walk.step(dt)) { walk = null; }
                return false;
            }
            hold(GLFW_KEY_W, false);
            look(n.pos.x, n.pos.y + 1f, n.pos.z);
            Vec3i f = cell(n.pos.x, n.pos.y, n.pos.z);
            boolean known = false;
            for (SurvivorSites.Resident planned : plan.refuge().site().residents()) { known |= planned.name().equals(n.name); }
            if (!known || !plan.camp().site().holds(f.x(), f.y(), f.z())) {
                throw fail(n.name + " at " + f + " is not one of the civilians, at the camp");
            }
            guestsSeen = true;
            views.add("14-civilians-in-the-spare-beds=" + game.player.locationLine() + " " + n.name);
            capture("14-civilians-in-the-spare-beds");
            return true;
        }
    }

    /** Back in the cellar by day: nobody hides there now. */
    private final class Emptied extends Task {
        Emptied() { super("the empty refuge"); }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            lookAtBlock(plan.refuge().site().safe(), .6f);
            if (age < .8f) { return false; }
            for (Npc n : live(plan.refuge().site().id())) {
                Vec3i f = cell(n.pos.x, n.pos.y, n.pos.z);
                if (plan.refuge().site().holds(f.x(), f.y(), f.z())) { throw fail(n.name + " is still in the cellar"); }
            }
            if (game.player.place != Place.HIDDEN_CELLAR) { throw fail("the HUD names " + game.player.place + " in the cellar"); }
            emptied = true;
            views.add("15-the-empty-refuge=" + game.player.locationLine());
            capture("15-the-empty-refuge");
            return true;
        }
    }

    private final class Report extends Task {
        Report() { super("report"); }

        @Override boolean step(float dt) {
            releaseAll();
            if (!campFound || !traded || !befriended || !shared || !heard || !refugeFound || !guided || !reloaded || !dormant
                    || !bedtime || !guestsSeen || !emptied || !leftToYou || wallFrames > 0 || strayFrames > 0
                    || residentFrames == 0 || captures != CAPTURES) {
                throw fail("tour incomplete: camp=" + campFound + " traded=" + traded + " friend=" + befriended
                        + " shared=" + shared + " heard=" + heard + " refuge=" + refugeFound + " guided=" + guided
                        + " reloaded=" + reloaded + " dormant=" + dormant + " night=" + bedtime + " guests=" + guestsSeen
                        + " emptied=" + emptied + " leftToYou=" + leftToYou + " walls=" + wallFrames + " " + wallNote
                        + " strays=" + strayFrames + " " + strayNote + " residentFrames=" + residentFrames
                        + " captures=" + captures);
            }
            passed = true;
            reported = true;
            System.out.printf(Locale.ROOT, "[urban-survivors] pass=true mode=%s seed=%d poses=%d captures=%d"
                            + " residentFrames=%d wallFrames=0 strayFrames=0 engaged=%s defeated=%s health=%.1f routeSeconds=%.0f%n",
                    game.gameMode().name().toLowerCase(Locale.ROOT), game.world.seed, poses, captures, residentFrames,
                    engaged, defeated, game.player.health, elapsed);
            System.out.println("[urban-survivors] after " + identities());
            System.out.println("[urban-survivors] deeds " + String.join(" | ", deeds));
            System.out.println("[urban-survivors] views " + String.join(" | ", views));
            System.out.println("[urban-survivors] timeline " + String.join(" ", timeline));
            game.automation.finishScriptedRun();
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** The newest log line, by identity: the log keeps only its latest lines, so indices drift. */
    private Object logMark() {
        var all = game.eventLog.all();
        return all.isEmpty() ? null : all.getLast();
    }

    /** Lines logged after a mark (all of them when the mark has rolled out or the log was cleared). */
    private List<String> loggedAfter(Object mark) {
        var all = game.eventLog.all();
        int i = all.size();
        while (i > 0 && all.get(i - 1) != mark) { i--; }
        return all.subList(i, all.size());
    }

    private boolean loggedSince(Object mark, String text) {
        return loggedAfter(mark).stream().anyMatch(l -> l.contains(text));
    }

    boolean survival() { return game.gameMode() == GameMode.SURVIVAL; }
}
