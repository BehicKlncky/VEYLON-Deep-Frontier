package com.veylon;

import com.veylon.ai.CompoundResidentAI;
import com.veylon.ai.InfectedAI;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.settlement.CompoundAccess;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.UrbanSite;
import com.veylon.settlement.UrbanSiteDirector;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.FixtureStates;
import com.veylon.world.Fixtures;
import com.veylon.world.VoxelClearance;
import com.veylon.world.urban.CompoundSites;
import com.veylon.world.urban.CompoundSites.Zone;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanPlaces.Place;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The milestone-17 stronghold tour ({@code VEYLON_SCENE=urban_strongholds}) on a generator-16 world
 * from {@code VEYLON_GENERATOR=urban-dev}, in either game mode, walked with ordinary controls (W,
 * F, Escape, left Control, F5, F9 and mouse look through the production input seam):
 * <ol>
 *   <li>by day, the guarded stronghold nearest the spawn: its gate and the sign over it, the
 *       gatekeeper's rules, leave to come in as a visitor, the gate opened for the player and shut
 *       and locked behind them, the yard, a trade from the stronghold's own stores, and the
 *       clinic's medic;</li>
 *   <li>a restricted route: through a barracks door off the yard, where the guard who sees a
 *       visitor there warns them out, and back to the yard with the pass kept (a Creative player
 *       is perceived by nobody, so nobody warns them);</li>
 *   <li>a threat at the gate: one infected staged on the street, met and put down by the
 *       gatekeeper while the player only watches; the gate never opens, and the gatekeeper goes
 *       back to the porch;</li>
 *   <li>the raider yard nearest the spawn, avoided: crouched in through its back way's street
 *       door to the unlocked rear door into the yard, unnoticed;</li>
 *   <li>F5 and F9: the same compounds, passes, people and stores;</li>
 *   <li>a street far from both until both go dormant; then at night the stronghold again, its
 *       living guards back once each and the watch at the gate, and a stranger's knock answered
 *       through the shut gate (unheard in Creative).</li>
 * </ol>
 * Every frame, every compound person on screen must stand clear of solid blocks and inside their
 * compound or its porch, or, for a guard or raider, within its leash of its post. Conversation
 * choices go through the conversation screen's own command. The poses between legs, the clock
 * (only ever forward), the weather, one infected body and a kit of a spear, trade goods, food and
 * water are staged and printed as staged. No block is staged. Exactly sixteen labelled captures.
 * Failures throw.
 */
final class UrbanStrongholdQaScene extends UrbanPeopleScene {
    static final double DEADLINE_SECONDS = 900;
    static final int CAPTURES = 16;
    static final String SIGN = "Visitors by day: ask at the gate.";
    static final String RULES = "keep to the yard and the clinic";
    static final String ADMITTED = "In you come. Keep to the yard and the clinic, and be out by dark.";
    static final String WARNING = "\"Visitors keep to the yard and the clinic. Out of there.\"";
    static final String TURNED_AWAY = "calls through the gate: \"We don't open after dark.";
    /** How far from the raiders' rear door the tour goes crouched. */
    static final float SNEAK = 10f;
    /** How long the tour waits for a compound's people to wake: three of the slow ticks they wake on. */
    static final float AWAKE_SECONDS = 30f;

    private UrbanStrongholdTourPlan plan;
    private boolean passed, signRead, admitted, inYard, clinicSeen, warned, obeyed, threatMet, threatDown,
            backWay, unnoticed, reloaded, dormant, nightGate, knocked;
    private int residentFrames, wallFrames, strayFrames;
    private float trustBeforeBarracks, healthAtRaiders;
    private String wallNote = "", strayNote = "", before = "";
    private Set<Long> aliveAtLoad = Set.of();

    UrbanStrongholdQaScene(Game game) { super(game); }

    @Override protected String tag() { return "urban-strongholds"; }
    @Override protected double deadline() { return DEADLINE_SECONDS; }

    boolean passed() { return passed; }
    UrbanStrongholdTourPlan plan() { return plan; }
    int residentFrames() { return residentFrames; }

    /** Plans the tour on the current generated world and stages the kit; no block is changed. */
    void stage() {
        plan = UrbanStrongholdTourPlan.of(game.world);
        Inventory inv = game.player.inventory;
        inv.clear();
        inv.set(7, new ItemStack(ItemType.IRON_SPEAR, 1));
        // One of every trader's wanted goods, and food and water for the day.
        inv.add(ItemType.SCRAP, 4);
        inv.add(ItemType.PLANK, 2);
        inv.add(ItemType.FIBER, 4);
        inv.add(ItemType.TORCH, 4);
        inv.add(ItemType.MEDICINE, 1);
        inv.add(ItemType.DRIED_BERRY, 6);
        inv.add(ItemType.WATERSKIN_CLEAN, 2);
        System.out.println("[urban-strongholds] stage seed=" + game.world.seed + " generator=" + game.world.generatorVersion
                + " mode=" + game.gameMode().name().toLowerCase(Locale.ROOT)
                + " evidence=walked-legs(W,F,Esc,LeftCtrl,F5,F9,mouse-look); conversation-choices(NpcScreen.performSiteChoice);"
                + " staged-poses-between-legs,staged-clock(forward),staged-infected(one,at-the-gate),"
                + "staged-kit(spear,trade-goods,food,water); generated-geometry; normal-simulation");
        System.out.println("[urban-strongholds] plan " + plan);
        var h = plan.stronghold();
        CompoundSites.Gate gate = h.compound().gate();
        long id = h.compound().site().id();
        Predicate<Vec3i> visitor = plan::visitorMay;
        add(new Clock("day", 10 * 60), new Pose(h.street(), gate.first()), new Wait("stream", 1f),
                new Walk(h.signStand(), visitor), new ReadSign(), new Awake(id),
                new Talk("the gatekeeper", () -> liveAt(id, 0), visitor),
                new Say(1, "02-the-gatekeeper-explains", said -> said.contains(RULES)),
                new Admit(), new Leave(), new Walk(gate.laneInside(), visitor), new Walk(h.yard(), visitor), new InYard(),
                new Talk("the trader", () -> liveAt(id, 4), visitor),
                new Trade("trade with the stronghold", "05-the-stronghold-trader", "06-a-fair-trade"), new Leave(),
                new Walk(h.clinicYard(), visitor), new Walk(h.clinicInside(), visitor),
                new Talk("the medic", () -> liveAt(id, 5), visitor), new Clinic(), new Leave(),
                new Walk(h.clinicYard(), visitor), new Walk(h.barracksYard(), visitor), new Walk(h.barracksInside()),
                new Barracks(), new Walk(h.barracksYard()), new Obeyed());
        add(new Pose("pose on the porch", gate::laneOutside, h::threat), new Wait("settle", .5f), new Threat());
        var r = plan.raiders();
        add(new Pose(r.street(), r.streetDoor()), new Wait("stream", 1f), new Raiders(),
                new Sneak(), new BackDoor(),
                new Record(), new Save(), new Load(), new Reloaded(), new Crouch(false));
        add(new PoseAway(), new Dormant(), new Clock("night", 23 * 60 + 15), new Wait("night", 1f),
                new Pose("pose at the gate at night", gate::laneOutside, gate::first), new Wait("stream", 1f),
                new NightGate(), new Knock(), new Report());
        staged = true;
    }

    // ------------------------------------------------------------------
    // The compounds
    // ------------------------------------------------------------------

    private UrbanSite site(long id) {
        return UrbanSiteDirector.registered(game.world, id);
    }

    private UrbanSite stronghold() { return site(plan.stronghold().compound().site().id()); }

    private UrbanSite raiders() { return site(plan.raiders().compound().site().id()); }

    private Npc liveAt(long id, int slot) {
        UrbanSite s = site(id);
        UrbanSite.Resident r = s == null ? null : s.resident(slot);
        return r != null && r.live != null && !r.live.dead && game.entities.npcs.contains(r.live) ? r.live : null;
    }

    private int liveCount(UrbanSite s) {
        int n = 0;
        for (Settlement.Resident r : s.residents) {
            if (r.live != null && !r.live.dead && game.entities.npcs.contains(r.live)) { n++; }
        }
        return n;
    }

    private boolean gateShutAndLocked() {
        for (Vec3i leaf : plan.stronghold().compound().gate().leaves()) {
            for (int y = leaf.y(); y <= leaf.y() + 1; y++) {
                if (game.world.getBlock(leaf.x(), y, leaf.z()) != BlockType.METAL_DOOR
                        || game.world.fixtureLockAt(leaf.x(), y, leaf.z()) != FixtureStates.LOCK_KEYED) { return false; }
            }
        }
        return true;
    }

    private Zone zone(Vec3i cell) {
        return plan.stronghold().compound().zoneAt(cell.x(), cell.y(), cell.z());
    }

    /** What the compounds remember, who lives there, how they are and what their stores hold: compared across the save. */
    private String identities() {
        StringBuilder b = new StringBuilder();
        for (UrbanSite s : new UrbanSite[]{stronghold(), raiders()}) {
            if (s == null) { b.append("unregistered;"); continue; }
            b.append(UrbanIds.format(s.id)).append(" trust=").append(Math.round(s.localReputation))
                    .append(" fear=").append(Math.round(s.fear)).append(" hostile=").append(s.hostile())
                    .append(String.format(Locale.ROOT, " pass=%.1f wanted=%.1f seen=%.1f cooldown=%.1f",
                            s.passUntil, s.wantedUntil, s.lastSeenInside, s.huntCooldownUntil))
                    .append(" missing=").append(s.missing).append(" cleared=").append(s.cleared)
                    .append(" rewarded=").append(s.rewarded).append(" stash=").append(stash(s)).append(" [");
            for (Settlement.Resident base : s.residents) {
                UrbanSite.Resident r = (UrbanSite.Resident) base;
                b.append(UrbanIds.format(r.id)).append(':').append(r.name).append(r.alive ? "" : "+")
                        .append(String.format(Locale.ROOT, " %.1f/%.1f/%.1f, ", r.health, r.hunger, r.thirst));
            }
            b.append("];");
        }
        return b.toString();
    }

    /** Items in a compound's stash containers, as their inventories hold them. */
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
    // Every frame: nobody in a wall, nobody out of their compound
    // ------------------------------------------------------------------

    @Override
    protected void observe() {
        if (plan == null) { return; }
        for (CompoundSites.Compound c : List.of(plan.stronghold().compound(), plan.raiders().compound())) {
            UrbanSite s = site(c.site().id());
            if (s == null) { continue; }
            for (Settlement.Resident base : s.residents) {
                UrbanSite.Resident r = (UrbanSite.Resident) base;
                Npc n = r.live;
                if (n == null || n.dead || !game.entities.npcs.contains(n)) { continue; }
                residentFrames++;
                Vec3i f = cell(n.pos.x, n.pos.y, n.pos.z);
                if (VoxelClearance.collides(game.world, n.pos.x, n.pos.y + .05f, n.pos.z, .5f, 1.6f, false)) {
                    if (wallFrames++ == 0) { wallNote = r.name + "@" + f; }
                }
                boolean home = c.zoneAt(f.x(), f.y(), f.z()) != Zone.OUTSIDE || c.zoneAt(f.x(), f.y() - 1, f.z()) != Zone.OUTSIDE;
                Vec3i post = s.plan.resident(r.slot).day();
                boolean leashed = CompoundResidentAI.defender(r.role)
                        && Math.hypot(f.x() - post.x(), f.z() - post.z()) <= CompoundResidentAI.LEASH + 1.5f;
                Vec3i gate = c.gate().outside();
                boolean hunting = (s.hunters >> r.slot & 1) != 0
                        && Math.hypot(f.x() - gate.x(), f.z() - gate.z()) <= CompoundAccess.HUNT_RANGE + 2f;
                if (!home && !leashed && !hunting && strayFrames++ == 0) {
                    strayNote = r.name + "@" + f + " compound=" + UrbanIds.format(c.site().id());
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Staging
    // ------------------------------------------------------------------

    /** Holds or releases left Control: a crouch, which makes no footsteps and is harder to see. */
    private final class Crouch extends Task {
        private final boolean down;

        Crouch(boolean down) { super(down ? "crouch" : "stand up"); this.down = down; }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_LEFT_CONTROL, down);
            return age > .3f;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** A sidewalk corner far from both compounds. */
    private final class PoseAway extends Task {
        PoseAway() { super("pose far from both compounds"); }

        @Override boolean step(float dt) {
            Vec3i a = plan.away();
            stagePose(a, a.offset(0, 0, -4), 1f);
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    // ------------------------------------------------------------------
    // The stronghold by day
    // ------------------------------------------------------------------

    /** Waits for a compound's people to be on screen: they wake on the ten-second slow tick. */
    private final class Awake extends Task {
        private final long id;

        Awake(long id) { super("wait for the compound's people"); this.id = id; }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            UrbanSite s = site(id);
            if (s == null || liveCount(s) == 0) {
                if (age > AWAKE_SECONDS) { throw fail("the compound " + UrbanIds.format(id) + " never woke"); }
                return false;
            }
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** On the porch, looking up at the sign over the gate post: the prompt reads its words. */
    private final class ReadSign extends Task {
        ReadSign() { super("read the stronghold's sign"); }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            lookAtBlock(plan.stronghold().compound().sign(), .5f);
            if (age < .6f) { return false; }
            String prompt = game.interactPrompt == null ? "" : game.interactPrompt;
            if (!prompt.contains(SIGN)) {
                if (age > 6) { throw fail("the sign over the gate reads '" + prompt + "'"); }
                return false;
            }
            if (!gateShutAndLocked()) { throw fail("the gate stood open before anyone asked"); }
            signRead = true;
            views.add("01-the-stronghold-gate=" + prompt);
            capture("01-the-stronghold-gate");
            return true;
        }
    }

    /** Asks the gatekeeper to be let in: a visitor's pass until dusk and the gate opened. */
    private final class Admit extends Task {
        Admit() { super("ask to be let in"); }

        @Override boolean step(float dt) {
            if (game.uiMode != Game.UiMode.NPC || game.activeNpc == null) { throw fail("the gatekeeper's conversation closed"); }
            if (age < .4f) { return false; }
            UrbanSite s = stronghold();
            if (!game.npcScreen.siteOptions(game, game.activeNpc).contains("[4] Ask to be let in")) {
                throw fail("the gatekeeper offers no way in: " + game.npcScreen.siteOptions(game, game.activeNpc));
            }
            game.npcScreen.performSiteChoice(game, game.activeNpc, 4);
            String said = game.npcScreen.lastResponse();
            deeds.add("4:" + said);
            Vec3i leaf = plan.stronghold().compound().gate().first();
            if (!said.equals(ADMITTED) || !CompoundAccess.hasPass(game, s)
                    || game.world.getBlock(leaf.x(), leaf.y(), leaf.z()) != BlockType.METAL_DOOR_OPEN) {
                throw fail("not let in: '" + said + "' pass=" + CompoundAccess.hasPass(game, s)
                        + " leaf=" + game.world.getBlock(leaf.x(), leaf.y(), leaf.z()));
            }
            admitted = true;
            deeds.add(String.format(Locale.ROOT, "pass-until=%.0f", s.passUntil));
            capture("03-let-in-as-a-visitor");
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** In the yard: the HUD names the stronghold, the gate is shut and locked behind, and nobody minds. */
    private final class InYard extends Task {
        InYard() { super("stand in the stronghold's yard"); }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            var h = plan.stronghold();
            Vec3i market = h.compound().market();
            lookAtBlock(market, 1.2f);
            if (age < .6f || !gateShutAndLocked()) {
                if (age > 15) { throw fail("the gate was never shut and locked behind the visitor"); }
                return false;
            }
            UrbanSite s = stronghold();
            if (game.player.place != Place.STRONGHOLD || zone(feet()) != Zone.YARD) {
                throw fail("expected the stronghold's yard but the HUD names " + game.player.place + " in " + zone(feet()));
            }
            if (CompoundAccess.wanted(game, s) || s.warnTimer > 0f || s.hostile()) {
                throw fail("a visitor in the yard was treated as a trespasser");
            }
            inYard = true;
            views.add("04-inside-the-stronghold=" + game.player.locationLine() + " people=" + liveCount(s));
            capture("04-inside-the-stronghold");
            return true;
        }
    }

    /** The medic, on the clinic's ground floor: where a visitor may go. */
    private final class Clinic extends Task {
        Clinic() { super("see the medic"); }

        @Override boolean step(float dt) {
            if (game.uiMode != Game.UiMode.NPC || game.activeNpc == null) { throw fail("the medic's conversation closed"); }
            if (age < .4f) { return false; }
            UrbanSite s = stronghold();
            if (zone(feet()) != Zone.GUEST) { throw fail("the medic is not met on the clinic's ground floor: " + zone(feet())); }
            game.npcScreen.performSiteChoice(game, game.activeNpc, 1);
            String said = game.npcScreen.lastResponse();
            deeds.add("medic 1:" + said);
            if (said.isEmpty() || CompoundAccess.wanted(game, s) || s.warnTimer > 0f) {
                throw fail("a visitor at the clinic was turned away: '" + said + "'");
            }
            clinicSeen = true;
            capture("07-the-clinic-medic");
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /**
     * Just inside a barracks door off the yard, looking out: in Survival, the first guard who
     * sees the visitor there warns them out; a Creative player is perceived by nobody, so a guard
     * passes within the three blocks where facing no longer matters, in plain sight, and says
     * nothing.
     */
    private final class Barracks extends Task {
        private boolean started;
        private Object mark;
        private String chance;
        private float since;

        Barracks() { super("a visitor in the barracks"); }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            UrbanSite s = stronghold();
            if (!started) {
                started = true;
                mark = logMark();
                trustBeforeBarracks = s.localReputation;
            }
            lookAtBlock(plan.stronghold().barracksYard(), 1.3f);
            if (zone(feet()) != Zone.RESTRICTED) { throw fail("the barracks door leads to " + zone(feet())); }
            boolean warning = loggedSince(mark, WARNING);
            if (chance == null) { chance = guardInSight(s); }
            if (survival()) {
                if (!warning) {
                    if (age > 90) { throw fail("no guard ever saw the visitor in the barracks"); }
                    return false;
                }
                if (CompoundAccess.wanted(game, s) || !CompoundAccess.hasPass(game, s)) { throw fail("a first sighting cost the pass"); }
                deeds.add("warned at " + feet() + " after " + Math.round(age) + "s; nearest guard in sight " + guardInSight(s));
            } else {
                if (warning || s.warnTimer > 0f) { throw fail("a Creative player was warned: nobody perceives them"); }
                if (chance == null) {
                    if (age > 90) { throw fail("no guard came within sight of the barracks door"); }
                    return false;
                }
                if ((since += dt) < 1.5f) { return false; }
                deeds.add("unperceived at " + feet() + " by " + chance);
            }
            warned = true;
            capture("08-a-visitor-in-the-barracks");
            return true;
        }

        /** A stronghold guard within three blocks with a clear line to the player's eyes, or null. */
        private String guardInSight(UrbanSite s) {
            for (Settlement.Resident base : s.residents) {
                UrbanSite.Resident r = (UrbanSite.Resident) base;
                Npc n = r.live;
                if (n == null || n.dead || !CompoundResidentAI.defender(r.role) || n.distSqTo(game.player) >= 9) { continue; }
                if (InfectedAI.clearSight(game.world, n.pos.x, n.pos.y + 1.4f, n.pos.z,
                        game.player.pos.x, game.player.pos.y + 1.4f, game.player.pos.z)) {
                    return n.name + String.format(Locale.ROOT, "@%.1f", Math.sqrt(n.distSqTo(game.player)));
                }
            }
            return null;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** Back in the yard after the warning: the pass kept, nobody wanting the player gone. */
    private final class Obeyed extends Task {
        Obeyed() { super("back in the yard"); }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            lookAtBlock(plan.stronghold().barracksInside(), 1f);
            if (age < 1f) { return false; }
            UrbanSite s = stronghold();
            if (zone(feet()) != Zone.YARD) { throw fail("not back in the yard: " + zone(feet())); }
            if (!CompoundAccess.hasPass(game, s) || CompoundAccess.wanted(game, s) || s.hostile()
                    || s.localReputation != trustBeforeBarracks) {
                throw fail("a warning heeded still cost the visitor: pass=" + CompoundAccess.hasPass(game, s)
                        + " wanted=" + CompoundAccess.wanted(game, s) + " trust " + trustBeforeBarracks + "->" + s.localReputation);
            }
            if (survival() != s.warnTimer > 0f) { throw fail("the warning is " + (survival() ? "forgotten" : "remembered")); }
            obeyed = true;
            capture("09-back-in-the-yard");
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /**
     * One infected staged on the street before the gate (printed as staged), met by the
     * gatekeeper and put down while the player watches from the porch and never strikes; the gate
     * stays shut and locked, and the gatekeeper goes back to the porch.
     */
    private final class Threat extends Task {
        private Npc body;
        private float since;

        Threat() { super("an infected at the gate"); }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            UrbanSite s = stronghold();
            var gate = plan.stronghold().compound().gate();
            if (body == null) {
                Vec3i at = plan.stronghold().threat();
                body = game.entities.spawnNpc(game.world, "Veilbound", at.x() + .5f, at.y() + .02f, at.z() + .5f);
                body.archetype = NpcArchetype.VEILBOUND;
                body.health = body.maxHealth = body.archetype.maxHealth;
                System.out.println("[urban-strongholds] staged-infected at " + at);
                return false;
            }
            if (!gateShutAndLocked()) { throw fail("the gate opened while an infected stood before it"); }
            if (!body.dead) {
                look(body.pos.x, body.pos.y + 1.2f, body.pos.z);
                if (age > 60) { throw fail("the infected at the gate was never put down: health " + body.health); }
                if (!threatMet && body.health < body.maxHealth) {
                    Npc guard = defenderNear(s, body, 2.6f);
                    if (guard != null) {
                        threatMet = true;
                        deeds.add("met by " + guard.name + " at " + cell(guard.pos.x, guard.pos.y, guard.pos.z));
                        capture("10-the-gatekeeper-meets-it");
                    }
                }
                return false;
            }
            if (!threatMet) { throw fail("the infected died before a guard met it"); }
            Npc keeper = liveAt(s.id, 0);
            since += dt;
            if (keeper != null) {
                look(keeper.pos.x, keeper.pos.y + 1.3f, keeper.pos.z);
                Vec3i post = gate.outside();
                if (Math.hypot(keeper.pos.x - post.x() - .5f, keeper.pos.z - post.z() - .5f) > 1.5f) {
                    if (since > 25) { throw fail(keeper.name + " never went back to the porch"); }
                    return false;
                }
            }
            if (since < .8f) { return false; }
            threatDown = true;
            deeds.add("put down; gatekeeper " + (keeper == null ? "fell" : "back at the porch"));
            capture("11-the-street-held");
            return true;
        }

        private Npc defenderNear(UrbanSite s, Npc target, float radius) {
            for (Settlement.Resident base : s.residents) {
                UrbanSite.Resident r = (UrbanSite.Resident) base;
                if (r.live != null && !r.live.dead && CompoundResidentAI.defender(r.role)
                        && r.live.distSqTo(target) < radius * radius) { return r.live; }
            }
            return null;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    // ------------------------------------------------------------------
    // The raider yard
    // ------------------------------------------------------------------

    /** The back way's street door, from the street: an ordinary unheld building beside the raiders' row. */
    private final class Raiders extends Task {
        Raiders() { super("the raiders' back way"); }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            lookAtBlock(plan.raiders().streetDoor(), .9f);
            UrbanSite r = raiders();
            if (r == null || liveCount(r) == 0) {
                if (age > AWAKE_SECONDS) { throw fail("the raider yard never woke: registered=" + (r != null)); }
                return false;
            }
            if (age < .8f) { return false; }
            if (r.cleared || !r.hostile()) { throw fail("the raider yard is " + (r.cleared ? "taken" : "not hostile")); }
            healthAtRaiders = game.player.health;
            views.add("12-the-raiders-back-way=" + game.player.locationLine() + " raiders=" + liveCount(r));
            capture("12-the-raiders-back-way");
            return true;
        }
    }

    /**
     * In through the back way's street door and down its hall, crouched (no footsteps) for the
     * last {@value #SNEAK} blocks before the rear door: the raiders are beyond it. A crouched F on
     * a door bars it, so the street door is opened standing.
     */
    private final class Sneak extends Task {
        private Walk walk;

        Sneak() { super("sneak to the back door"); }

        @Override boolean step(float dt) {
            if (walk == null) { walk = new Walk(plan.raiders().rearInside()); }
            Vec3i door = plan.raiders().rearDoor();
            hold(GLFW_KEY_LEFT_CONTROL, Math.hypot(game.player.pos.x - door.x() - .5f, game.player.pos.z - door.z() - .5f) < SNEAK);
            return walk.step(dt);
        }
    }

    /** Crouched inside the back way at its rear door: unlocked, the yard beyond it, and nobody the wiser. */
    private final class BackDoor extends Task {
        BackDoor() { super("at the back door into the yard"); }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            Vec3i door = plan.raiders().rearDoor();
            lookAtBlock(door, .9f);
            if (age < .8f) { return false; }
            BlockType block = game.world.getBlock(door.x(), door.y(), door.z());
            String prompt = game.interactPrompt == null ? "" : game.interactPrompt;
            if (!Fixtures.isClosedDoor(block) || game.world.fixtureLockAt(door.x(), door.y(), door.z()) != FixtureStates.LOCK_NONE
                    || !prompt.startsWith("[F]")) {
                throw fail("the back way's rear door is not an unlocked way in: " + block + " '" + prompt + "'");
            }
            UrbanSite r = raiders();
            if (r.provoked || r.hunters != 0 || game.player.health < healthAtRaiders || game.player.place == Place.RAIDER_YARD) {
                throw fail("the raiders noticed: provoked=" + r.provoked + " hunters=" + r.hunters
                        + " health=" + game.player.health + " place=" + game.player.place);
            }
            backWay = unnoticed = true;
            views.add("13-the-back-door-into-the-yard=" + prompt);
            capture("13-the-back-door-into-the-yard");
            return true;
        }
    }

    // ------------------------------------------------------------------
    // Save, load, dormancy and the night
    // ------------------------------------------------------------------

    private final class Record extends Task {
        Record() { super("record what the compounds remember"); }

        @Override boolean step(float dt) {
            before = identities();
            System.out.println("[urban-strongholds] before-save " + before);
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** After the load: the same compounds, memories, people and stores. */
    private final class Reloaded extends Task {
        Reloaded() { super("the same compounds after the load"); }

        @Override boolean step(float dt) {
            String after = identities();
            if (!after.equals(before)) {
                throw fail("the compounds changed across the save:\n before " + before + "\n after  " + after);
            }
            lookAtBlock(plan.raiders().rearDoor(), .9f);
            if (age < .8f) { return false; }
            Set<Long> alive = new HashSet<>();
            for (Settlement.Resident r : stronghold().residents) { if (r.alive) { alive.add(((UrbanSite.Resident) r).id); } }
            aliveAtLoad = alive;
            reloaded = true;
            capture("14-after-reload");
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** Far away: both compounds go dormant. */
    private final class Dormant extends Task {
        Dormant() { super("wait far from both compounds"); }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            if (liveCount(stronghold()) > 0 || liveCount(raiders()) > 0) {
                if (age > 45) {
                    throw fail("the compounds never went dormant: stronghold=" + liveCount(stronghold()) + " raiders=" + liveCount(raiders()));
                }
                return false;
            }
            dormant = true;
            return true;
        }
    }

    /**
     * At night before the gate: every living guard back once and nobody twice, the dead still
     * dead, the watch at the gate and the gate shut and locked.
     */
    private final class NightGate extends Task {
        NightGate() { super("the stronghold at night"); }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            lookAtBlock(plan.stronghold().compound().sign(), .5f);
            UrbanSite s = stronghold();
            int alive = 0, bodies = 0, claimed = 0;
            Set<Long> living = new HashSet<>();
            for (Settlement.Resident r : s.residents) {
                if (r.alive) { alive++; living.add(((UrbanSite.Resident) r).id); }
                if (r.live != null && !r.live.dead && game.entities.npcs.contains(r.live)) { bodies++; }
            }
            for (Npc n : game.entities.npcs) {
                if (!n.dead && UrbanSiteDirector.siteOf(game.world, n) == s) { claimed++; }
            }
            if (claimed > bodies) { throw fail("a stronghold person walks twice: " + claimed + " bodies for " + bodies + " people"); }
            if (!living.equals(aliveAtLoad)) { throw fail("the living changed while the stronghold slept: " + living + " vs " + aliveAtLoad); }
            Npc watch = liveAt(s.id, 1);
            Vec3i post = plan.stronghold().compound().gate().inside();
            boolean watching = s.resident(1) == null || !s.resident(1).alive
                    || watch != null && Math.hypot(watch.pos.x - post.x() - .5f, watch.pos.z - post.z() - .5f) < 1.5f;
            if (bodies < alive || !watching || !gateShutAndLocked()) {
                if (age > 40) {
                    throw fail("the stronghold never settled for the night: " + bodies + "/" + alive + " watching=" + watching
                            + " gate=" + gateShutAndLocked());
                }
                return false;
            }
            if (age < 1f) { return false; }
            nightGate = true;
            deeds.add("night: " + bodies + "/" + alive + " back, watch at the gate");
            capture("15-the-gate-at-night");
            return true;
        }
    }

    /** F on the shut gate at night: a stranger is answered through it, and it stays shut. Unheard in Creative. */
    private final class Knock extends Task {
        private boolean pressed;
        private Object mark;

        Knock() { super("knock at the gate at night"); }

        @Override boolean step(float dt) {
            hold(GLFW_KEY_W, false);
            Vec3i leaf = plan.stronghold().compound().gate().first();
            lookAtBlock(leaf, .9f);
            if (age < .4f) { return false; }
            if (!pressed) {
                mark = logMark();
                press(GLFW_KEY_F);
                pressed = true;
                return false;
            }
            boolean answered = loggedSince(mark, TURNED_AWAY);
            if (!gateShutAndLocked()) { throw fail("the gate opened for a stranger at night"); }
            if (survival() && !answered) {
                if (age > 10) { throw fail("nobody answered the knock: " + loggedAfter(mark)); }
                return false;
            }
            if (!survival()) {
                if (answered) { throw fail("the watch answered a Creative player nobody can perceive"); }
                if (age < 8) { return false; }
            }
            knocked = true;
            deeds.add(answered ? "answered through the gate" : "the knock went unheard");
            capture("16-knocking-at-night");
            return true;
        }
    }

    private final class Report extends Task {
        Report() { super("report"); }

        @Override boolean step(float dt) {
            releaseAll();
            if (!signRead || !admitted || !inYard || !traded || !clinicSeen || !warned || !obeyed || !threatMet || !threatDown
                    || !backWay || !unnoticed || !reloaded || !dormant || !nightGate || !knocked || wallFrames > 0 || strayFrames > 0
                    || residentFrames == 0 || captures != CAPTURES) {
                throw fail("tour incomplete: sign=" + signRead + " admitted=" + admitted + " yard=" + inYard + " traded=" + traded
                        + " clinic=" + clinicSeen + " warned=" + warned + " obeyed=" + obeyed + " met=" + threatMet
                        + " down=" + threatDown + " backWay=" + backWay + " unnoticed=" + unnoticed + " reloaded=" + reloaded
                        + " dormant=" + dormant + " night=" + nightGate + " knocked=" + knocked + " walls=" + wallFrames + " "
                        + wallNote + " strays=" + strayFrames + " " + strayNote + " residentFrames=" + residentFrames
                        + " captures=" + captures);
            }
            UrbanSite s = stronghold();
            if (s.hostile() || CompoundAccess.wanted(game, s)) { throw fail("the tour turned the stronghold against the player"); }
            passed = true;
            reported = true;
            System.out.printf(Locale.ROOT, "[urban-strongholds] pass=true mode=%s seed=%d poses=%d captures=%d"
                            + " residentFrames=%d wallFrames=0 strayFrames=0 engaged=%s defeated=%s health=%.1f routeSeconds=%.0f%n",
                    game.gameMode().name().toLowerCase(Locale.ROOT), game.world.seed, poses, captures, residentFrames,
                    engaged, defeated, game.player.health, elapsed);
            System.out.println("[urban-strongholds] after " + identities());
            System.out.println("[urban-strongholds] deeds " + String.join(" | ", deeds));
            System.out.println("[urban-strongholds] views " + String.join(" | ", views));
            System.out.println("[urban-strongholds] timeline " + String.join(" ", timeline));
            game.automation.finishScriptedRun();
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    boolean survival() { return game.gameMode() == GameMode.SURVIVAL; }
}
