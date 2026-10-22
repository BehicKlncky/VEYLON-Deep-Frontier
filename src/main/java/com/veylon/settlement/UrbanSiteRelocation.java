package com.veylon.settlement;

import com.veylon.Game;
import com.veylon.util.Vec3i;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanIds;

/**
 * A safe move for hidden civilians (urban milestone 16): the player vouches for a camp they know,
 * and the people of a refuge go to live there. The move is persistent state, never a teleport in
 * view and never a citywide escort:
 *
 * <ol>
 *   <li><b>Departing:</b> they agree, leave their stash to the player and wait to go unseen;</li>
 *   <li><b>Travelling:</b> once the player is gone and they are dormant, they are on the road for
 *       {@value #MINUTES_PER_BLOCK} game minutes a block (at least {@value #MIN_TRAVEL_MINUTES});</li>
 *   <li><b>Arrived:</b> if the camp still stands and will have them, they live in its spare beds
 *       upstairs and eat from its stores; otherwise they go back into hiding at home.</li>
 * </ol>
 * They only agree when they trust the player ({@value #TRUST_TO_MOVE}), are not terrified, the camp
 * is known to the player, not hostile, never betrayed, not wary of the player and has beds for all
 * of them. The camp is told on arrival, and trusts the player more for it.
 */
final class UrbanSiteRelocation {
    static final float TRUST_TO_MOVE = 30f, CAMP_TRUST_TO_TAKE = 0f, MOVED = 15f, TAKEN_IN = 10f;
    static final double MIN_TRAVEL_MINUTES = 60, MINUTES_PER_BLOCK = 0.5;

    private UrbanSiteRelocation() {
    }

    /** Why the people of a refuge would not move to a camp now, or null when they would. */
    static String refusal(Game g, UrbanSite refuge, UrbanSite camp) {
        if (!refuge.refuge() || refuge.relocation != UrbanSite.Relocation.HOME) { return "We are already leaving."; }
        if (refuge.betrayed || refuge.localReputation < TRUST_TO_MOVE) {
            return "We don't know you well enough to follow you anywhere.";
        }
        if (refuge.frightened()) { return "Not now. Not like this."; }
        if (camp == null || !camp.camp() || !camp.discovered) { return "We don't know of anywhere safe."; }
        if (camp.hostile() || camp.betrayed || camp.present() == 0 || camp.localReputation < CAMP_TRUST_TO_TAKE) {
            return "Those people would never take us in.";
        }
        if (freeBeds(g, camp) < refuge.present()) { return "They have no room for all of us."; }
        return null;
    }

    /** Starts the move. True when they agreed. */
    static boolean guide(Game g, UrbanSite refuge, UrbanSite camp) {
        if (refusal(g, refuge, camp) != null) { return false; }
        refuge.relocation = UrbanSite.Relocation.DEPARTING;
        refuge.relocatedTo = camp.id;
        refuge.stashReleased = true;
        if (refuge.simMinute < 0) { refuge.simMinute = g.time.totalMinutes; }
        UrbanSiteRelations.trust(g, refuge, MOVED, null);
        g.log("They will go to the " + SurvivorSites.Kind.CAMP.label.toLowerCase(java.util.Locale.ROOT)
                + " once it is quiet, and leave what is on their shelf to you.");
        return true;
    }

    /** Departing people slip away once nobody is watching: when their site has gone dormant. */
    static void depart(Game g, UrbanSiteDirector director, UrbanSite s) {
        if (s.relocation != UrbanSite.Relocation.DEPARTING) { return; }
        for (Settlement.Resident r : s.residents) {
            if (r.live != null && !r.live.dead) { return; }
        }
        UrbanSite camp = UrbanSiteDirector.registered(g.world, s.relocatedTo);
        Vec3i from = s.plan.anchor(), to = camp == null ? from : camp.plan.anchor();
        double blocks = Math.sqrt(from.distSq(to.x(), to.y(), to.z()));
        if (s.simMinute < 0) { s.simMinute = g.time.totalMinutes; }
        s.relocation = UrbanSite.Relocation.TRAVELLING;
        s.arriveMinute = s.simMinute + Math.max(MIN_TRAVEL_MINUTES, blocks * MINUTES_PER_BLOCK);
    }

    /**
     * A travelling refuge never steps ahead of its camp: a camp lagging behind (its stash unloaded)
     * holds the travellers back until it has caught up, so an arrival sees the camp of that hour.
     */
    static boolean mayStep(Game g, UrbanSite s) {
        UrbanSite camp = UrbanSiteDirector.registered(g.world, s.relocatedTo);
        return camp == null || camp.simMinute < 0 || camp.simMinute >= s.simMinute;
    }

    /** On the hour they reach the camp: taken in, or turned back home. */
    static void arrive(Game g, UrbanSite s) {
        if (s.relocation != UrbanSite.Relocation.TRAVELLING || s.simMinute < s.arriveMinute) { return; }
        UrbanSite camp = UrbanSiteDirector.registered(g.world, s.relocatedTo);
        if (camp != null && !camp.hostile() && camp.present() > 0) {
            s.relocation = UrbanSite.Relocation.ARRIVED;
            UrbanSiteRelations.trust(g, camp, TAKEN_IN, null);
            g.log("Word reaches you: the people from the hidden cellar reached the camp safely.");
        } else {
            s.relocation = UrbanSite.Relocation.HOME;
            s.relocatedTo = UrbanIds.NONE;
            g.log("Word reaches you: the camp would not take the cellar's people in. They went back into hiding.");
        }
    }

    /** Spare beds a camp has left once every refuge moving or moved there is counted. */
    static int freeBeds(Game g, UrbanSite camp) {
        int taken = 0;
        for (UrbanSite s : UrbanSiteDirector.sites(g.world)) {
            if (s.relocation != UrbanSite.Relocation.HOME && s.relocatedTo == camp.id) { taken += living(s); }
        }
        return camp.plan.guestBeds().size() - taken;
    }

    /**
     * The spare bed an arrived guest sleeps in: after the guests of refuges with a lower id, in
     * slot order among the living of their own refuge.
     */
    static Vec3i guestBed(Game g, UrbanSite guest, UrbanSite.Resident r) {
        UrbanSite camp = UrbanSiteDirector.registered(g.world, guest.relocatedTo);
        if (camp == null || camp.plan.guestBeds().isEmpty()) { return guest.plan.resident(r.slot).bed(); }
        int index = 0;
        for (UrbanSite s : UrbanSiteDirector.sites(g.world)) {
            if (s.id >= guest.id) { break; }
            if (s.relocation == UrbanSite.Relocation.ARRIVED && s.relocatedTo == camp.id) { index += living(s); }
        }
        for (Settlement.Resident other : guest.residents) {
            if (other.alive && ((UrbanSite.Resident) other).slot < r.slot) { index++; }
        }
        return camp.plan.guestBeds().get(index % camp.plan.guestBeds().size());
    }

    private static int living(UrbanSite s) {
        int n = 0;
        for (Settlement.Resident r : s.residents) {
            if (r.alive) { n++; }
        }
        return n;
    }
}
