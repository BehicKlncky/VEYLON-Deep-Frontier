package com.veylon.settlement;

import com.veylon.Game;
import com.veylon.entity.Npc;
import com.veylon.util.MathUtil;

import java.util.Locale;

/**
 * How a survivor site's people feel about the player (urban milestone 16), and what changes it.
 * Every rule is a deed, never talk: help raises trust, harm and theft lower it, and fear and alarm
 * rise with violence near them and fade by the hour ({@link UrbanSiteNeeds}).
 *
 * <ul>
 *   <li><b>Help:</b> supplies left in their stores (by hand or through a conversation), medicine
 *       for the hurt, and infected put down near them. Returning what was taken only pays back
 *       what is owed ({@link UrbanSite#owed}); it earns nothing.</li>
 *   <li><b>Theft:</b> taking from their storage without a camp's leave, at most once per
 *       logical theft, as the container ledger reports it. A camp's Free Settlers standing pays
 *       too, exactly as milestone 07's survivor storage did.</li>
 *   <li><b>Harm:</b> an attack costs 25 trust and alarms them; a killing costs 60. A camp that
 *       falls to {@value UrbanSite#HOSTILE_TRUST} fights back; hidden civilians never fight, they
 *       cower.</li>
 *   <li><b>Betrayal:</b> any harm, theft or threat while they trusted the player marks the site
 *       betrayed: trust never climbs above {@value UrbanSite#BETRAYED_CAP} again.</li>
 * </ul>
 * Consequences reach every mode; only the alarm needs a player the residents can perceive.
 */
final class UrbanSiteRelations {
    static final float ATTACK = 25f, KILL = 60f, THEFT_BASE = 4f, THEFT_MAX = 15f;
    static final float AID_PER_ITEM = 3f, AID_MEDICINE = 6f, AID_MAX = 18f, DEFENDED = 4f, WRECKED = 20f;
    static final float DEFEND_RADIUS = 16f;
    /** Free Settlers standing a camp's theft costs, as survivor storage always has (D-74). */
    static final int FACTION_THEFT_BASE = 3, FACTION_THEFT_MAX = 12, FACTION_WRECK = 15;
    static final int MAX_OWED = 9_999;

    private UrbanSiteRelations() {
    }

    static String name(UrbanSite s) {
        return "the " + s.plan.kind().label.toLowerCase(Locale.ROOT);
    }

    /** Moves trust, re-derives the alignment and reports a change of heart. */
    static void trust(Game g, UrbanSite s, float delta, String reason) {
        Settlement.Alignment was = s.alignment;
        s.localReputation = MathUtil.clamp(s.localReputation + delta, -100f,
                s.betrayed ? UrbanSite.BETRAYED_CAP : 100f);
        s.settleAlignment();
        if (reason != null && Math.abs(delta) >= 2f) { g.log(reason); }
        if (was == s.alignment) { return; }
        if (s.alignment == Settlement.Alignment.HOSTILE) {
            g.log(capital(name(s)) + " turns on you!");
        } else if (s.alignment == Settlement.Alignment.FRIENDLY) {
            g.log(capital(name(s)) + " counts you as a friend now."
                    + (s.camp() ? " Their storage is shared with you, their trader gives a friend's price and"
                    + " their medic will dress your wounds."
                    : s.stronghold() ? " Their gate opens for you day and night, their stores are shared with you"
                    + " and their medic will dress your wounds." : ""));
            g.audio.playQuest();
        } else if (was == Settlement.Alignment.HOSTILE) {
            g.log(capital(name(s)) + " lowers its guard.");
        }
    }

    /** Harm while they trusted the player is a betrayal; then the trust falls. */
    static void harm(Game g, UrbanSite s, float penalty, String reason) {
        if (!s.betrayed && s.localReputation >= UrbanSite.FRIEND_TRUST) {
            s.betrayed = true;
            g.log("You betrayed their trust. They will not forget it.");
        }
        trust(g, s, -penalty, reason);
    }

    static void alarm(Game g, UrbanSite s, float amount) {
        if (g.player == null || g.player.isPerceivableByAi()) {
            s.alertLevel = Math.min(100f, s.alertLevel + amount);
        }
    }

    static void frighten(UrbanSite s, float amount) {
        s.fear = MathUtil.clamp(s.fear + amount, 0f, 100f);
    }

    /** A blow, shot, blast or flame the player landed on one of them. */
    static void attacked(Game g, UrbanSite s, Npc n) {
        harm(g, s, ATTACK, "You attacked " + n.name + "!");
        frighten(s, 40f);
        alarm(g, s, 100f);
        seeAttacker(g, s);
        CompoundAccess.provoked(s);
    }

    /** One of them died by the player's hand: a permanent tombstone and lasting grief. */
    static void killed(Game g, UrbanSite s, Npc n) {
        if (n.residentIndex >= 0 && n.residentIndex < s.residents.size()) {
            Settlement.Resident r = s.residents.get(n.residentIndex);
            r.alive = false;
            r.health = 0f;
            r.live = null;
        }
        harm(g, s, KILL, "You killed " + n.name + "!");
        frighten(s, 100f);
        alarm(g, s, 100f);
        seeAttacker(g, s);
        CompoundAccess.provoked(s);
        CompoundAccess.settleCapture(g, s, true);
    }

    /** One of them died of something else: the others are shaken, and a raider yard may be left empty. */
    static void died(Game g, UrbanSite s) {
        frighten(s, 30f);
        CompoundAccess.settleCapture(g, s, true);
    }

    /** Every living one of them who can see the player now knows where the attacker stands. */
    static void seeAttacker(Game g, UrbanSite s) {
        if (g.player == null || !g.player.isPerceivableByAi()) { return; }
        for (Settlement.Resident r : s.residents) {
            if (r.live != null && !r.live.dead) {
                r.live.lastKnown.set(g.player.pos);
                r.live.lastKnownAge = 0f;
                r.live.searchTimer = 20f;
            }
        }
    }

    /**
     * Supplies taken from their storage: theft, once per logical event, unless a camp gave the
     * player leave. What was taken is owed either way.
     */
    static void taken(Game g, UrbanSite s, int count) {
        s.owed = Math.min(MAX_OWED, s.owed + Math.max(0, count));
        if (s.grantsAccess()) { return; }
        boolean seen = anyoneSees(g, s);
        if (s.raiders()) {
            // Raiders have no standing to lose, and nobody who did not see it knows who took what.
            alarm(g, s, seen ? 100f : 15f);
            if (seen) {
                seeAttacker(g, s);
                CompoundAccess.provoked(s);
            }
            g.log(seen ? "The raiders saw you take their supplies!" : "You take the raiders' supplies unseen.");
            return;
        }
        // A stronghold judges by what its guards saw (CompoundAccess); a camp and a refuge as ever.
        if (CompoundAccess.taken(g, s, count, seen)) { return; }
        harm(g, s, Math.min(THEFT_MAX, THEFT_BASE + count), seen ? "They saw you take their supplies!"
                : "Their stores are short, and they know who took them.");
        frighten(s, s.refuge() ? 15f : 5f);
        alarm(g, s, 15f);
        if (s.camp()) {
            g.settlementManager.addReputation(g, HumanFaction.FREE_SETTLERS,
                    -Math.min(FACTION_THEFT_MAX, FACTION_THEFT_BASE + count), "Taking survivor supplies harms your standing.");
        }
    }

    /** Their storage wrecked by the player's hand, blast or flame. */
    static void wrecked(Game g, UrbanSite s) {
        if (s.raiders()) {
            alarm(g, s, anyoneSees(g, s) ? 100f : 25f);
            return;
        }
        harm(g, s, WRECKED, "You wrecked their stores!");
        frighten(s, 20f);
        alarm(g, s, 25f);
        if (s.camp()) {
            g.settlementManager.addReputation(g, HumanFaction.FREE_SETTLERS, -FACTION_WRECK,
                    "Destroying survivor storage harms your standing.");
        }
    }

    /**
     * Supplies left in their stores: rations and water count {@value #AID_PER_ITEM} each and
     * medicine {@value #AID_MEDICINE}, up to {@value #AID_MAX} at a time, after what is owed.
     * Returns the trust gained.
     */
    static float aided(Game g, UrbanSite s, int rations, int medicine) {
        int repaid = Math.min(s.owed, rations + medicine);
        s.owed -= repaid;
        int newRations = Math.max(0, rations - repaid);
        int newMedicine = Math.max(0, medicine - Math.max(0, repaid - rations));
        // Layout sixteen: supplies beyond anything owed count toward a mission to help these people (urban 20).
        UrbanMissionBoard.onAided(g, s, newRations + newMedicine);
        float gain = Math.min(AID_MAX, newRations * AID_PER_ITEM + newMedicine * AID_MEDICINE);
        if (gain <= 0f) {
            if (repaid > 0) { g.log("You put back what was taken. It earns no thanks."); }
            return 0f;
        }
        frighten(s, -10f);
        trust(g, s, gain, null);
        g.log(s.refuge() ? "They take the supplies with shaking hands."
                : s.stronghold() ? "The Wardens take in your supplies." : "The camp takes in your supplies.");
        return gain;
    }

    /** An infected the player put down near people who saw it. */
    static void defended(Game g, UrbanSite s, Npc infected) {
        trust(g, s, DEFENDED, capital(name(s)) + " saw you put down the " + infected.archetype.displayName + ".");
    }

    /**
     * Whether any of their live people can see the player now. A compound's people (milestone 17)
     * see only through the fair sight test their guards use: range by light, closer for a
     * crouching player, in front of them or very near, and in plain sight.
     */
    static boolean anyoneSees(Game g, UrbanSite s) {
        if (g.player == null || !g.player.isPerceivableByAi()) { return false; }
        for (Settlement.Resident r : s.residents) {
            Npc n = r.live;
            if (n == null || n.dead) { continue; }
            if (s.compound() ? com.veylon.ai.InfectedAI.seesPlayer(g, n) : n.distSqTo(g.player) < 24 * 24
                    && com.veylon.ai.InfectedAI.clearSight(g.world, n.pos.x, n.pos.y + 1.55f, n.pos.z,
                    g.player.pos.x, g.player.pos.y + 1.4f, g.player.pos.z)) {
                return true;
            }
        }
        return false;
    }

    static String capital(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
