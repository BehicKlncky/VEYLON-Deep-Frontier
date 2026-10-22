package com.veylon.settlement;

import com.veylon.Game;
import com.veylon.combat.WorldNoise;
import com.veylon.entity.Npc;
import com.veylon.util.Vec3i;
import com.veylon.world.urban.CompoundSites;
import com.veylon.world.urban.CompoundSites.Zone;
import com.veylon.world.urban.SurvivorSites.Role;

/**
 * Who may be where in a compound, and what follows from what its people actually perceive (urban
 * milestone 17). Every consequence here starts from their own senses: what a guard sees with the
 * shared sight test or hears, and for a theft what any of them sees; a crime nobody perceived is
 * noticed only as missing stores, and blamed only on someone guards saw inside not long before.
 *
 * <ul>
 *   <li><b>Admission.</b> A stronghold's gatekeeper lets visitors in by day: a visitor's pass
 *       lasts until {@value #DUSK}:00 and keeps them to the yard and the clinic's ground floor.
 *       Friends come and go at any hour, anywhere; at night a friend knocks and the watch opens.
 *       Nobody is let in while the stronghold is hostile or wants them gone.</li>
 *   <li><b>Warnings.</b> A guard who sees a visitor where visitors may not go warns them once; seen
 *       there again {@value #GRACE} seconds or more later, the visitor loses the pass and is wanted
 *       for {@value #WANTED_HOURS} game hours, and guards drive a wanted player out of the
 *       compound. Anyone seen inside without leave is an intruder: wanted at once.</li>
 *   <li><b>Theft.</b> Taken in sight of any of the stronghold's people, it is caught: its trust
 *       falls, the thief is wanted and the alarm goes up. Taken unseen, it is only missing
 *       stores, which the stronghold notices at its next hour; it suspects the player only if a
 *       guard saw them inside within the last {@value #SUSPICION_HOURS} hours.</li>
 *   <li><b>Sounds.</b> The player's footsteps, doors and knock where the rules let them be are
 *       everyday life and alarm nobody, unless the stronghold has turned on them or wants them
 *       gone; the same sounds anywhere else, and every other sound, raise its alert as any
 *       site's.</li>
 *   <li><b>Responses.</b> A sound of forced entry, an intruder or a theft in sight raises an alarm:
 *       at most {@value #RESPONDERS} guards, never the gate's own, go to look for
 *       {@value #RESPONSE_SECONDS} seconds, and no new response starts while one is out.</li>
 *   <li><b>Raiders</b> who lose a player who killed one of them or robbed them in sight send at
 *       most {@value #HUNTERS} of their own after the player, no farther than
 *       {@value #HUNT_RANGE} blocks from their gate, for {@value #HUNT_SECONDS} seconds, and then
 *       not again for {@value #HUNT_COOLDOWN_HOURS} game hours. A raider yard whose people are all
 *       dead is taken: its stores are nobody's, and the first stronghold told of it is grateful,
 *       once.</li>
 * </ul>
 * Passes, wanted status, the evidence of the last sighting, missing stores, the hunt cooldown and
 * the reward are saved ({@code urban.compounds}); warnings, responses and hunts in progress are
 * not, and a load or dormancy ends them.
 */
public final class CompoundAccess {
    public static final int DUSK = 22, DAWN = 6;
    public static final float GRACE = 8f;
    /** How long a warning is remembered: a second offence within it costs the pass. */
    public static final float WARNING_MEMORY = 60f;
    public static final int WANTED_HOURS = 12, SUSPICION_HOURS = 2;
    public static final int RESPONDERS = 3;
    public static final float RESPONSE_SECONDS = 30f;
    public static final int HUNTERS = 2;
    public static final float HUNT_RANGE = 48f, HUNT_SECONDS = 90f;
    public static final int HUNT_COOLDOWN_HOURS = 24;
    /** Seconds raiders must have lost sight of a player who provoked them before they hunt. */
    public static final float LOST_FOR = 10f;
    /** Trust a stronghold gives for the news that a raider yard is taken. */
    public static final float REWARD = 25f;
    static final float TRESPASS = 8f, INTRUSION = 10f;
    /** How near the gate a knocking player must stand for its guard to open it. */
    public static final float GATE_REACH = 2.6f;

    private CompoundAccess() {
    }

    // ---- leave ------------------------------------------------------------------------------

    static double now(Game g) {
        return g.time.totalMinutes;
    }

    public static boolean night(Game g) {
        double h = g.time.hourF();
        return h >= DUSK || h < DAWN;
    }

    /** The game minute today's dusk falls at. */
    static double dusk(Game g) {
        double day = Math.floor(now(g) / (24 * 60)) * 24 * 60;
        return day + DUSK * 60;
    }

    public static boolean hasPass(Game g, UrbanSite s) {
        return s.passUntil > now(g);
    }

    public static boolean wanted(Game g, UrbanSite s) {
        return s.wantedUntil > now(g);
    }

    /** Whether the rules let the player stand in a zone of this site's compound now. */
    public static boolean allowed(Game g, UrbanSite s, Zone zone) {
        if (zone == Zone.OUTSIDE || zone == Zone.PORCH) { return true; }
        if (!s.stronghold() || s.hostile() || wanted(g, s)) { return false; }
        if (s.grantsAccess()) { return true; }
        return hasPass(g, s) && (zone == Zone.YARD || zone == Zone.GUEST);
    }

    /** Whether a stronghold's gate guards open the gate for the player now. */
    public static boolean letThrough(Game g, UrbanSite s) {
        return s.stronghold() && !s.hostile() && !wanted(g, s)
                && (s.grantsAccess() || hasPass(g, s) && !night(g));
    }

    /**
     * The gatekeeper's answer to a request to come in. A friend is let in at once; by day a stranger
     * is given a visitor's pass until dusk; the gate opens for whoever is let in.
     */
    public static String admit(Game g, UrbanSite s, CompoundSites.Compound c, Npc keeper) {
        if (!s.stronghold()) { return "..."; }
        if (s.hostile()) { return "Turn around. You're not welcome here."; }
        if (wanted(g, s)) { return "Not today. You know what you did."; }
        if (s.grantsAccess()) {
            CompoundGate.openFor(g, s, c, CompoundGate.HOLD_SECONDS * 2);
            return "Come in, friend. Come and go as you please.";
        }
        if (night(g)) { return "We don't open after dark. Come back at first light."; }
        if (s.frightened()) { return "Not now. Not with things as they are."; }
        boolean renewing = hasPass(g, s);
        s.passUntil = dusk(g);
        CompoundGate.openFor(g, s, c, CompoundGate.HOLD_SECONDS * 2);
        if (!renewing) {
            g.log(keeper.name + " lets you in as a visitor until dusk: the yard and the clinic, nothing else.");
        }
        return "In you come. Keep to the yard and the clinic, and be out by dark.";
    }

    // ---- what guards see and hear ---------------------------------------------------------

    /**
     * A stronghold's guard sees the player at a feet cell: the evidence every rule below follows
     * from. Inside the gate a sighting is remembered; in a zone the player may not be, it is a
     * warning, then an offence; inside without any leave, an intrusion.
     */
    public static void sighted(Game g, UrbanSite s, CompoundSites.Compound c, Npc guard, Vec3i feet) {
        if (!s.stronghold() || s.hostile()) { return; }
        Zone zone = c.zoneAt(feet.x(), feet.y(), feet.z());
        if (zone == Zone.OUTSIDE || zone == Zone.PORCH) { return; }
        s.lastSeenInside = now(g);
        if (allowed(g, s, zone) || wanted(g, s)) { return; }
        if (!hasPass(g, s)) {
            intrusion(g, s, c, guard, feet);
            return;
        }
        if (s.warnTimer <= 0f) {
            s.warnTimer = WARNING_MEMORY;
            g.log(guard.name + ": \"Visitors keep to the yard and the clinic. Out of there.\"");
            g.audio.playClick();
        } else if (WARNING_MEMORY - s.warnTimer >= GRACE) {
            s.warnTimer = 0f;
            s.passUntil = -1;
            s.wantedUntil = now(g) + WANTED_HOURS * 60;
            UrbanSiteRelations.harm(g, s, TRESPASS, null);
            g.log(guard.name + ": \"That's enough. Out, and don't come back today.\"");
        }
    }

    /** Seen inside the gate without leave: wanted at once, and the alarm is raised where they stand. */
    private static void intrusion(Game g, UrbanSite s, CompoundSites.Compound c, Npc guard, Vec3i feet) {
        s.wantedUntil = now(g) + WANTED_HOURS * 60;
        UrbanSiteRelations.harm(g, s, INTRUSION, "You were caught inside the stronghold without leave.");
        g.log(guard.name + ": \"Intruder! Get out!\"");
        alarm(g, s, c, feet, 100f);
    }

    /**
     * A sound a stronghold's guard heard: forced entry inside or against the compound raises the
     * alarm where it was; a rattle at the shut gate is someone knocking. Whose noise it was the
     * guard cannot tell.
     */
    public static void heard(Game g, UrbanSite s, CompoundSites.Compound c, Npc guard, WorldNoise.NoiseEvent e) {
        if (!s.stronghold()) { return; }
        Vec3i where = new Vec3i((int) Math.floor(e.x), (int) Math.floor(e.y), (int) Math.floor(e.z));
        var b = c.site().bounds();
        boolean against = where.x() >= b.x0() - 2 && where.x() <= b.x1() + 2 && where.z() >= b.z0() - 2 && where.z() <= b.z1() + 2;
        if (("door-forced".equals(e.category) || "glass-break".equals(e.category)) && against) {
            if (s.responseTimer <= 0f) { g.log("Shouts inside the stronghold: someone is breaking in."); }
            alarm(g, s, c, where, 60f);
        } else if ("door-rattle".equals(e.category) && nearGate(c, e.x, e.z)) {
            s.knockTimer = 6f;
        }
    }

    /** The sounds of everyday life inside a stronghold: walking, doors and a knock at the gate. */
    private static final java.util.Set<String> EVERYDAY = java.util.Set.of("footstep", "sprint", "door", "door-rattle");

    /**
     * Whether a sound a stronghold's guard hears is everyday life, which raises no alarm: the
     * player's footsteps, doors or knock, made where the rules let the player be, while the
     * stronghold has nothing against them. The same sounds where the player may not be or from a
     * player it wants gone, and every other sound, are not.
     */
    public static boolean everyday(Game g, UrbanSite s, CompoundSites.Compound c, WorldNoise.NoiseEvent e) {
        if (!s.stronghold() || s.hostile() || wanted(g, s) || e == null || !e.playerSource || !EVERYDAY.contains(e.category)) {
            return false;
        }
        Zone zone = c.zoneAt((int) Math.floor(e.x), (int) Math.floor(e.y + .01f), (int) Math.floor(e.z));
        return allowed(g, s, zone);
    }

    private static boolean nearGate(CompoundSites.Compound c, float x, float z) {
        for (Vec3i leaf : c.gate().leaves()) {
            float dx = x - leaf.x() - .5f, dz = z - leaf.z() - .5f;
            if (dx * dx + dz * dz <= 3f * 3f) { return true; }
        }
        return false;
    }

    /**
     * Raises a compound's alarm at a cell and sends at most {@value #RESPONDERS} of its defenders,
     * never the gate's own two, nearest first, to look there; nothing new while a response is out.
     */
    public static void alarm(Game g, UrbanSite s, CompoundSites.Compound c, Vec3i where, float level) {
        s.alertLevel = Math.min(100f, s.alertLevel + level);
        if (s.responseTimer > 0f) { return; }
        int mask = 0, chosen = 0;
        for (int pick = 0; pick < RESPONDERS; pick++) {
            int best = -1;
            double bestD = Double.MAX_VALUE;
            for (Settlement.Resident base : s.residents) {
                UrbanSite.Resident r = (UrbanSite.Resident) base;
                if (!r.alive || r.live == null || r.live.dead || (mask & (1 << r.slot)) != 0
                        || !com.veylon.ai.CompoundResidentAI.defender(r.role) || gateGuard(s, r)) {
                    continue;
                }
                double d = r.live.distSqTo(where.x() + .5f, where.y(), where.z() + .5f);
                if (d < bestD) {
                    best = r.slot;
                    bestD = d;
                }
            }
            if (best < 0) { break; }
            mask |= 1 << best;
            chosen++;
        }
        if (chosen == 0) { return; }
        s.responders = mask;
        // A sound outside the wall is answered from inside it, at the post or patrol point nearest it.
        s.responseAt = c.inside(where.x(), where.y(), where.z()) ? where : nearestInside(c, where);
        s.responseTimer = RESPONSE_SECONDS;
    }

    private static Vec3i nearestInside(CompoundSites.Compound c, Vec3i where) {
        Vec3i best = null;
        java.util.List<Vec3i> candidates = new java.util.ArrayList<>(c.posts());
        candidates.addAll(c.patrol());
        for (Vec3i p : candidates) {
            if (!c.inside(p.x(), p.y(), p.z())) { continue; }
            if (best == null || distance(p, where) < distance(best, where)) { best = p; }
        }
        return best == null ? c.gate().inside() : best;
    }

    private static int distance(Vec3i a, Vec3i b) {
        return Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z());
    }

    /** The two guards who keep a gate: a stronghold's gatekeeper and inner sentry, a raider yard's sentries. */
    public static boolean gateGuard(UrbanSite s, UrbanSite.Resident r) {
        return s.stronghold() ? r.slot == 0 || r.slot == 1 : r.slot == 1 || r.slot == 3;
    }

    // ---- theft --------------------------------------------------------------------------

    /**
     * Supplies taken from a stronghold's stores without leave. Seen, the theft is caught; unseen, the
     * stores are simply short until the stronghold's next hour. Returns true once handled.
     */
    public static boolean taken(Game g, UrbanSite s, int count, boolean seen) {
        if (!s.stronghold()) { return false; }
        if (seen) {
            s.wantedUntil = now(g) + WANTED_HOURS * 60;
            UrbanSiteRelations.harm(g, s, Math.min(UrbanSiteRelations.THEFT_MAX, UrbanSiteRelations.THEFT_BASE + count),
                    "The guards caught you taking from the stronghold's stores!");
            CompoundSites.Compound c = s.compoundPlan(g.world.urban);
            if (c != null) {
                alarm(g, s, c, new Vec3i((int) Math.floor(g.player.pos.x), (int) Math.floor(g.player.pos.y + .01f),
                        (int) Math.floor(g.player.pos.z)), 100f);
            }
            UrbanSiteRelations.seeAttacker(g, s);
        } else {
            s.missing = Math.min(UrbanSiteRelations.MAX_OWED, s.missing + Math.max(0, count));
            g.log("Nobody saw you take it.");
        }
        return true;
    }

    /**
     * A stronghold's hour (the coarse model, on screen or not): stores taken unseen are found short.
     * The alarm goes up; the player is suspected, and wanted, only when a guard saw them inside within
     * the last {@value #SUSPICION_HOURS} hours of this hour.
     */
    static void reckon(Game g, UrbanSite s) {
        if (!s.stronghold() || s.missing <= 0) { return; }
        int missing = s.missing;
        s.missing = 0;
        s.alertLevel = Math.min(100f, s.alertLevel + 30f);
        boolean suspected = s.lastSeenInside >= 0 && s.lastSeenInside >= s.simMinute - SUSPICION_HOURS * 60;
        if (suspected) {
            s.wantedUntil = Math.max(s.wantedUntil, s.simMinute + WANTED_HOURS * 60);
            UrbanSiteRelations.harm(g, s, Math.min(UrbanSiteRelations.THEFT_MAX, UrbanSiteRelations.THEFT_BASE + missing),
                    "The stronghold has found its stores short, and they suspect you.");
        }
    }

    // ---- raiders --------------------------------------------------------------------------

    /** A raider killed by the player or a theft they saw: they want to hunt the player down. */
    static void provoked(UrbanSite s) {
        if (s.raiders()) { s.provoked = true; }
    }

    /**
     * The fast-tick side of a compound: warnings fade, a response and a hunt run out, and raiders
     * who lost a player who provoked them send hunters once the cooldown allows.
     */
    static void tick(Game g, UrbanSite s, CompoundSites.Compound c, float dt) {
        s.warnTimer = Math.max(0f, s.warnTimer - dt);
        s.knockTimer = Math.max(0f, s.knockTimer - dt);
        if (s.responseTimer > 0f) {
            s.responseTimer = Math.max(0f, s.responseTimer - dt);
            if (s.responseTimer == 0f) { s.responders = 0; }
        }
        if (s.huntTimer > 0f) {
            s.huntTimer = Math.max(0f, s.huntTimer - dt);
            if (s.huntTimer == 0f) { s.hunters = 0; }
        }
        if (!s.raiders() || !s.provoked || s.hunters != 0 || s.cleared || g.player == null || g.player.dead
                || !g.player.isPerceivableByAi()) {
            return;
        }
        if (s.huntCooldownUntil > now(g)) {
            s.provoked = false;
            return;
        }
        float lostFor = Float.MAX_VALUE;
        Npc tracker = null;
        for (Settlement.Resident r : s.residents) {
            if (r.live == null || r.live.dead) { continue; }
            if (r.live.lastKnownAge < lostFor) {
                lostFor = r.live.lastKnownAge;
                tracker = r.live;
            }
        }
        if (tracker == null || lostFor < LOST_FOR) { return; }
        int mask = 0, chosen = 0;
        for (Role wanted : new Role[]{Role.RAIDER, Role.PATROL, Role.SENTRY}) {
            for (Settlement.Resident base : s.residents) {
                UrbanSite.Resident r = (UrbanSite.Resident) base;
                if (chosen >= HUNTERS || r.role != wanted || !r.alive || r.live == null || r.live.dead) { continue; }
                mask |= 1 << r.slot;
                chosen++;
            }
        }
        s.provoked = false;
        if (chosen == 0) { return; }
        s.hunters = mask;
        s.huntTimer = HUNT_SECONDS;
        s.huntFrom = new Vec3i((int) Math.floor(tracker.lastKnown.x), (int) Math.floor(tracker.lastKnown.y + .01f),
                (int) Math.floor(tracker.lastKnown.z));
        s.huntCooldownUntil = now(g) + HUNT_COOLDOWN_HOURS * 60;
        g.log("Behind you, the raiders have come out of their yard after you.");
    }

    // ---- capture and reward ----------------------------------------------------------------

    /** A raider yard whose people are all dead is taken: nobody holds it, and nothing refills it. */
    static void settleCapture(Game g, UrbanSite s, boolean announce) {
        if (!s.raiders() || s.cleared) { return; }
        for (Settlement.Resident r : s.residents) {
            if (r.alive) { return; }
        }
        s.cleared = true;
        s.hunters = 0;
        s.responders = 0;
        if (announce) { g.log("The raider yard is taken. Whatever its stores still hold is nobody's now."); }
    }

    /** A taken raider yard no stronghold has yet been told of, or null. */
    public static UrbanSite unpaidYard(Game g) {
        for (UrbanSite y : UrbanSiteDirector.sites(g.world)) {
            if (y.raiders() && y.cleared && !y.rewarded) { return y; }
        }
        return null;
    }

    /** A stronghold hears that a raider yard is taken: grateful once for each yard, whoever tells them. */
    public static String reward(Game g, UrbanSite s) {
        UrbanSite yard = unpaidYard(g);
        if (!s.stronghold() || yard == null) { return "We've heard nothing of the sort."; }
        if (s.hostile()) { return "Whatever you've done out there, it changes nothing here."; }
        yard.rewarded = true;
        UrbanSiteRelations.trust(g, s, REWARD, "The stronghold is grateful: the raider yard will trouble no one now.");
        return "The Wreckers' yard? Taken? Then the streets round it are safe again. You've done us a service.";
    }
}
