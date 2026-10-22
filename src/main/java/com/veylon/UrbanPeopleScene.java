package com.veylon;

import com.veylon.entity.Npc;
import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.util.Vec3i;
import com.veylon.world.VoxelClearance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.lwjgl.glfw.GLFW.*;

/**
 * An urban QA route that meets people (milestones 16 and 17): the staged clock and poses between
 * legs, walking up to someone and talking with F, conversation choices through the conversation
 * screen's own command, a trade, Escape out of a screen, and F5 and F9. Subclasses supply the
 * legs; every capture is counted.
 */
abstract class UrbanPeopleScene extends UrbanRouteScene {
    /** How near the player stands to someone before pressing F to talk. */
    protected static final float REACH = 2.2f;

    protected int poses, captures;
    protected boolean traded;
    protected final List<String> views = new ArrayList<>(), deeds = new ArrayList<>();

    UrbanPeopleScene(Game game) { super(game); }

    @Override
    protected void capture(String label) {
        captures++;
        super.capture(label);
    }

    protected float distanceTo(Npc n) {
        return (float) Math.hypot(n.pos.x - game.player.pos.x, n.pos.z - game.player.pos.z);
    }

    // ------------------------------------------------------------------
    // Staging
    // ------------------------------------------------------------------

    /** Staged time of day, only ever forward, with clear weather pinned. */
    protected final class Clock extends Task {
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
            System.out.println("[" + tag() + "] staged-clock " + name.substring(6) + " minutes=" + minutes
                    + " advancedBy=" + Math.round(target - now));
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** Puts the player on a cell, at rest, looking at a point {@code height} above another cell. */
    protected void stagePose(Vec3i at, Vec3i facing, float height) {
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
        System.out.println("[" + tag() + "] staged-pose " + at);
    }

    /** A staged pose: feet on a leg's first cell, facing along it. */
    protected final class Pose extends Task {
        private final Supplier<Vec3i> at, facing;

        Pose(Vec3i at, Vec3i facing) { this("pose " + at, () -> at, () -> facing); }

        Pose(String name, Supplier<Vec3i> at, Supplier<Vec3i> facing) { super(name); this.at = at; this.facing = facing; }

        @Override boolean step(float dt) {
            stagePose(at.get(), facing.get(), 1f);
            return true;
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    // ------------------------------------------------------------------
    // Talking
    // ------------------------------------------------------------------

    /**
     * Walks up to a person until within reach with a clear line, then presses F until they talk.
     * F works an aimed door before it greets anyone, and greets the nearest person in reach, so F
     * is pressed only while the prompt names this one; otherwise the player steps to a free cell
     * beside the person, away from whoever F would greet instead. The walks may be kept to the
     * cells a predicate allows.
     */
    protected final class Talk extends Task {
        private static final float BLOCKED_SECONDS = 1.5f;
        private static final int DETOURS = 6;
        private final Supplier<Npc> who;
        private final Predicate<Vec3i> allowed;
        private final Set<Vec3i> tried = new HashSet<>();
        private Walk walk, aside;
        private Vec3i to;
        private float walkAge, wait, blocked;
        private int presses, detours;

        Talk(String name, Supplier<Npc> who) { this(name, who, cell -> true); }

        Talk(String name, Supplier<Npc> who, Predicate<Vec3i> allowed) {
            super("talk to " + name); this.who = who; this.allowed = allowed;
        }

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
            if (aside != null) { // stepping round someone in the way
                if (aside.step(dt)) { aside = null; }
                return false;
            }
            if (distanceTo(n) > REACH || Math.abs(n.pos.y - game.player.pos.y) > 1.2f || !game.interactions.clearPathTo(n)) {
                Vec3i target = cell(n.pos.x, n.pos.y, n.pos.z);
                walkAge += dt;
                if (walk == null || walkAge > .75f && !target.equals(to)) {
                    walk = new Walk(target, allowed);
                    to = target;
                    walkAge = 0;
                }
                if (walk.step(dt)) { walk = null; }
                return false;
            }
            hold(GLFW_KEY_W, false);
            look(n.pos.x, n.pos.y + 1.3f, n.pos.z);
            String prompt = game.interactPrompt == null ? "" : game.interactPrompt;
            if (!prompt.contains(n.name)) {
                if ((blocked += dt) > BLOCKED_SECONDS) {
                    blocked = 0;
                    Npc other = nearestOther(n);
                    Vec3i free = beside(n, other);
                    if (free == null || ++detours > DETOURS) {
                        throw fail("F never reached " + n.name + ": the prompt read '" + prompt + "'"
                                + (other == null ? "" : String.format(Locale.ROOT, " (%s %.1f, %s %.1f)",
                                other.name, distanceTo(other), n.name, distanceTo(n))));
                    }
                    tried.add(free);
                    deeds.add("stepped to " + free + " to reach " + n.name + "@" + cell(n.pos.x, n.pos.y, n.pos.z)
                            + (other == null ? " (the prompt read '" + prompt + "')"
                            : " past " + other.name + "@" + cell(other.pos.x, other.pos.y, other.pos.z)));
                    aside = new Walk(free, allowed);
                }
                return false;
            }
            blocked = 0;
            if ((wait += dt) > .4f) {
                if (++presses > 10) { throw fail("no conversation with " + n.name + ": " + game.interactPrompt); }
                press(GLFW_KEY_F);
                wait = 0;
            }
            return false;
        }

        /**
         * Whom F would speak to instead of {@code n}: the game's own pick of the nearest person in
         * reach (3.2 blocks), or null when it is {@code n} and something else outranks them, such
         * as an aimed door behind them.
         */
        private Npc nearestOther(Npc n) {
            Npc picked = game.nearestNpcForInteraction(3.2f);
            return picked != null && picked != n ? picked : null;
        }

        /**
         * A free standing cell within reach of the person and not tried before, the farther from
         * {@code other} than from them the better: nobody stands on it and the walks may use it.
         */
        private Vec3i beside(Npc n, Npc other) {
            Vec3i at = cell(n.pos.x, n.pos.y, n.pos.z), best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Vec3i c = at.offset(dx, 0, dz);
                    double toTarget = Math.hypot(c.x() + .5f - n.pos.x, c.z() + .5f - n.pos.z);
                    if (dx == 0 && dz == 0 || toTarget > REACH - .3f || tried.contains(c) || !allowed.test(c)
                            || !VoxelClearance.standable(game.world, c.x(), c.y(), c.z()) || occupied(c)) {
                        continue;
                    }
                    double toOther = other == null ? 99 : Math.hypot(c.x() + .5f - other.pos.x, c.z() + .5f - other.pos.z);
                    double score = toOther - toTarget;
                    if (score > bestScore) {
                        bestScore = score;
                        best = c;
                    }
                }
            }
            return best;
        }

        private boolean occupied(Vec3i c) {
            for (Npc o : game.entities.npcs) {
                if (!o.dead && Math.hypot(o.pos.x - c.x() - .5f, o.pos.z - c.z() - .5f) < .9f
                        && Math.abs(o.pos.y - c.y()) < 1.5f) { return true; }
            }
            return false;
        }

        @Override boolean threatsInterrupt() { return game.uiMode == Game.UiMode.NONE; }
    }

    /** One conversation choice through the screen's own command; checks the answer, may capture. */
    protected final class Say extends Task {
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

    /**
     * A trade with a site's trader from the site's own stores: captures the two standing offers,
     * then takes the first that goes through.
     */
    protected final class Trade extends Task {
        private final String offersLabel, tradeLabel;
        private int step;

        Trade(String name, String offersLabel, String tradeLabel) {
            super(name); this.offersLabel = offersLabel; this.tradeLabel = tradeLabel;
        }

        @Override boolean step(float dt) {
            if (game.uiMode != Game.UiMode.NPC) { throw fail("the trader's conversation closed"); }
            if (age < .4f) { return false; }
            if (step == 0) {
                var offers = com.veylon.settlement.UrbanSiteDialogue.offers(game, game.activeNpc);
                if (offers.size() != 2) { throw fail("the trader keeps " + offers.size() + " offers"); }
                deeds.add("offers=" + offers.get(0).describe() + " | " + offers.get(1).describe());
                capture(offersLabel);
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
                    capture(tradeLabel);
                    return true;
                }
            }
            throw fail("no trade went through: " + String.join(" | ", deeds));
        }

        @Override boolean threatsInterrupt() { return false; }
    }

    /** Escape until no screen is open. */
    protected final class Leave extends Task {
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

    // ------------------------------------------------------------------
    // Saving
    // ------------------------------------------------------------------

    protected final class Save extends Task {
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

    protected final class Load extends Task {
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

    // ------------------------------------------------------------------
    // The event log
    // ------------------------------------------------------------------

    /** The newest log line, by identity: the log keeps only its latest lines, so indices drift. */
    protected Object logMark() {
        var all = game.eventLog.all();
        return all.isEmpty() ? null : all.getLast();
    }

    /** Lines logged after a mark (all of them when the mark has rolled out or the log was cleared). */
    protected List<String> loggedAfter(Object mark) {
        var all = game.eventLog.all();
        int i = all.size();
        while (i > 0 && all.get(i - 1) != mark) { i--; }
        return all.subList(i, all.size());
    }

    protected boolean loggedSince(Object mark, String text) {
        return loggedAfter(mark).stream().anyMatch(l -> l.contains(text));
    }
}
