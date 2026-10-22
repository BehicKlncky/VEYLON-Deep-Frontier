package com.veylon.ai;

import com.veylon.Game;
import com.veylon.combat.WorldNoise;
import com.veylon.entity.Npc;
import com.veylon.entity.Npc.NpcState;
import com.veylon.entity.Player;
import com.veylon.settlement.UrbanSite;
import com.veylon.settlement.UrbanSiteDirector;
import com.veylon.util.Vec3i;
import com.veylon.world.urban.SurvivorSites;

import java.util.List;

/**
 * The people of survivor camps and hidden refuges (urban milestone 16). Every place they go is a
 * planned cell of their own site ({@link SurvivorSites}): a bed by night, a work or resting cell by
 * day, the stash at meal times, the cell farthest from the way in when frightened, and for a
 * camp's lookout a post inside the rear door. They walk only along bounded indoor routes
 * ({@link IndoorNavigation}), never by steering at a point, so nobody walks into a wall, out
 * through a hidden panel or toward a street or roof height they do not live at.
 *
 * <ul>
 *   <li><b>Day:</b> 06:00 to 22:00 at their day cell; an hour's meal at 07:00, 12:30 and 18:30 at the
 *       stash.</li>
 *   <li><b>Night:</b> asleep at their bed; a camp's lookout keeps the post.</li>
 *   <li><b>Fear and alarm:</b> noise they hear raises alarm, violence near them fear. The
 *       frightened and the alarmed gather at the safe cell; a lookout holds the post.</li>
 *   <li><b>Threats:</b> an infected in sight within {@value #THREAT_RANGE} blocks sends the
 *       lookout at it, as far as {@value #LEASH} blocks from the post, and everyone else to the
 *       safe cell.</li>
 *   <li><b>A hostile camp</b> (trust fallen too far) has its lookout strike a player it can
 *       perceive, through the player's damage gates; the others hide. Hidden civilians never
 *       fight.</li>
 * </ul>
 * A camp member the player can see from within {@value UrbanSiteDirector#DISCOVERY_SIGHT} blocks
 * makes the camp known. The people of layout thirteen's compounds keep duty instead
 * ({@link CompoundResidentAI}).
 */
public final class UrbanResidentAI {
    public static final float PERCEPTION_INTERVAL = 0.35f;
    public static final float THREAT_RANGE = 10f, LEASH = 14f;
    private static final float EYE = 1.55f;

    private UrbanResidentAI() {
    }

    public static void update(Game g, Npc n, float dt) {
        UrbanSite s = UrbanSiteDirector.siteOf(g.world, n);
        if (s == null) {
            Steering.stop(n);
            return;
        }
        if (s.compound()) {
            // A stronghold's guards and a raider yard's people keep duty (milestone 17).
            com.veylon.world.urban.CompoundSites.Compound c = s.compoundPlan(g.world.urban);
            if (c != null) {
                CompoundResidentAI.update(g, n, s, c, dt);
            } else {
                Steering.stop(n);
            }
            return;
        }
        UrbanSite.Resident r = s.resident(n.residentIndex);
        n.lastKnownAge += dt;
        n.repathCooldown -= dt;
        n.hunger = r.hunger;
        Npc threat = perceive(g, n, s);
        boolean lookout = r.role == SurvivorSites.Role.LOOKOUT;
        Player p = g.player;

        if (s.hostile() && p != null && !p.dead && p.isPerceivableByAi() && n.lastKnownAge < 14f) {
            if (lookout) {
                fightPlayer(g, n, s, r);
            } else {
                retreat(g, n, s);
            }
            return;
        }
        if (threat != null) {
            if (lookout && withinLeash(g, s, r, threat)) {
                fightInfected(g, n, threat);
            } else {
                retreat(g, n, s);
            }
            return;
        }
        if (s.frightened() || s.alertLevel >= 60f) {
            if (lookout) {
                n.state = NpcState.GUARD;
                go(g, n, dayOf(g, s, r), n.archetype.speed);
            } else {
                retreat(g, n, s);
            }
            return;
        }
        Vec3i goal = scheduledCell(g, s, r);
        n.state = stateFor(g, s, r);
        go(g, n, goal, n.archetype.speed * 0.8f);
    }

    // ---- the schedule ----------------------------------------------------------------------

    /** Where the schedule wants a resident now: bed, day cell or the stash at meals. */
    public static Vec3i scheduledCell(Game g, UrbanSite s, UrbanSite.Resident r) {
        if (s.compound()) {
            com.veylon.world.urban.CompoundSites.Compound c = s.compoundPlan(g.world.urban);
            if (c != null) { return CompoundResidentAI.scheduledCell(g, s, c, r, r.live); }
        }
        double hour = g.time.hourF();
        if (night(hour)) {
            return r.role == SurvivorSites.Role.LOOKOUT && s.relocation != UrbanSite.Relocation.ARRIVED
                    ? dayOf(g, s, r) : bedOf(g, s, r);
        }
        if (meal(hour)) {
            List<Vec3i> meals = s.home(g.world.urban).meals();
            if (!meals.isEmpty()) { return meals.get(r.slot % meals.size()); }
        }
        return dayOf(g, s, r);
    }

    private static NpcState stateFor(Game g, UrbanSite s, UrbanSite.Resident r) {
        double hour = g.time.hourF();
        if (night(hour)) {
            return r.role == SurvivorSites.Role.LOOKOUT ? NpcState.GUARD : NpcState.SLEEP;
        }
        if (meal(hour)) { return NpcState.WARM_BY_FIRE; }
        return switch (r.role) {
            case LOOKOUT -> NpcState.GUARD;
            case TRADER -> NpcState.TRADE;
            case MEDIC -> NpcState.HEAL;
            default -> NpcState.IDLE;
        };
    }

    static boolean night(double hour) {
        return hour >= 22 || hour < 6;
    }

    static boolean meal(double hour) {
        return hour >= 7 && hour < 8 || hour >= 12.5 && hour < 13.5 || hour >= 18.5 && hour < 19.5;
    }

    /** A resident's bed: their own, or a spare bed of the camp that took them in. */
    public static Vec3i bedOf(Game g, UrbanSite s, UrbanSite.Resident r) {
        if (s.relocation == UrbanSite.Relocation.ARRIVED) { return guestBed(g, s, r); }
        return s.plan.resident(r.slot).bed();
    }

    /** A resident's day cell; guests keep to their spare beds. */
    public static Vec3i dayOf(Game g, UrbanSite s, UrbanSite.Resident r) {
        if (s.relocation == UrbanSite.Relocation.ARRIVED) { return guestBed(g, s, r); }
        return s.plan.resident(r.slot).day();
    }

    private static Vec3i guestBed(Game g, UrbanSite s, UrbanSite.Resident r) {
        return com.veylon.settlement.UrbanSiteDirector.guestBed(g, s, r);
    }

    // ---- perception --------------------------------------------------------------------------

    /**
     * Sight of the player (for a hostile camp, and for the player finding a camp), hearing, and the
     * nearest infected in sight. Runs on a staggered interval; returns the infected threat, if any.
     */
    private static Npc perceive(Game g, Npc n, UrbanSite s) {
        if (n.decideTimer > 0) { return n.combatTarget instanceof Npc t && !t.dead ? t : null; }
        n.decideTimer = PERCEPTION_INTERVAL + g.entities.nextSettledNpcAiFloat() * 0.15f;
        n.perceptionChecks++;
        Player p = g.player;
        if (p != null && !p.dead) {
            boolean sees = n.distSqTo(p) < UrbanSiteDirector.DISCOVERY_SIGHT * UrbanSiteDirector.DISCOVERY_SIGHT
                    && InfectedAI.clearSight(g.world, n.pos.x, n.pos.y + EYE, n.pos.z, p.pos.x, p.pos.y + p.eyeHeight(), p.pos.z);
            if (sees) {
                // The player can see this person: that is how a camp is found.
                g.settlementManager.urbanSites.onResidentSeen(g, s);
                if (p.isPerceivableByAi() && (s.hostile() || s.alertLevel > 50f)) {
                    n.lastKnown.set(p.pos);
                    n.lastKnownAge = 0;
                }
            }
        }
        WorldNoise.NoiseEvent heard = g.noise.loudestAudible(g.world, n.pos.x, n.pos.y, n.pos.z,
                n.archetype.hearRange * 0.3f, n.lastHeard, n);
        if (heard != null && (heard.playerSource || heard.intensity > 0.5f)) {
            n.lastHeard = heard;
            s.alertLevel = Math.min(100f, s.alertLevel + heard.intensity * 20f);
            if (heard.intensity > 0.6f) { s.fear = Math.min(100f, s.fear + (s.refuge() ? 15f : 8f)); }
            if (heard.playerSource && s.hostile()) {
                n.lastKnown.set(heard.x, heard.y, heard.z);
                n.lastKnownAge = Math.min(n.lastKnownAge, 6f);
            }
        }
        Npc threat = null;
        double best = THREAT_RANGE * THREAT_RANGE;
        for (Npc o : g.entities.npcs) {
            if (!o.infected() || o.dead) { continue; }
            double d = o.distSqTo(n);
            if (d < best && InfectedAI.clearSight(g.world, n.pos.x, n.pos.y + EYE, n.pos.z, o.pos.x, o.pos.y + 1.2f, o.pos.z)) {
                best = d;
                threat = o;
            }
        }
        n.combatTarget = threat;
        if (threat != null) {
            s.alertLevel = Math.max(s.alertLevel, 70f);
            s.fear = Math.min(100f, s.fear + 5f);
        }
        return threat;
    }

    // ---- actions -----------------------------------------------------------------------------

    private static void retreat(Game g, Npc n, UrbanSite s) {
        n.state = NpcState.FLEE;
        go(g, n, s.home(g.world.urban).safe(), n.archetype.speed * 1.1f);
    }

    private static boolean withinLeash(Game g, UrbanSite s, UrbanSite.Resident r, Npc threat) {
        Vec3i post = dayOf(g, s, r);
        return threat.distSqTo(post.x() + .5f, post.y(), post.z() + .5f) <= LEASH * LEASH;
    }

    /** A hostile camp's lookout: strikes a perceived player in reach, otherwise holds or closes in. */
    private static void fightPlayer(Game g, Npc n, UrbanSite s, UrbanSite.Resident r) {
        Player p = g.player;
        n.state = NpcState.ATTACK;
        double dist = Math.sqrt(n.distSqTo(p));
        if (dist < 1.9 && Math.abs(n.pos.y - p.pos.y) < .8f
                && InfectedAI.clearSight(g.world, n.pos.x, n.pos.y + EYE, n.pos.z, p.pos.x, p.pos.y + 1.4f, p.pos.z)) {
            Steering.stop(n);
            face(n, p.pos.x, p.pos.z);
            if (n.attackCooldown <= 0 && !p.abilities.invulnerable()) {
                n.attackCooldown = 1.3f;
                p.hurtPhysical(g, n.archetype.meleeDamage, true);
                p.knockback(n.pos.x, n.pos.z, 3f);
                g.audio.playHit();
                g.log(n.name + " strikes you!");
            }
            return;
        }
        Vec3i post = dayOf(g, s, r);
        Vec3i seen = new Vec3i((int) Math.floor(n.lastKnown.x), (int) Math.floor(n.lastKnown.y + .01f),
                (int) Math.floor(n.lastKnown.z));
        boolean near = seen.distSq(post.x(), post.y(), post.z()) <= LEASH * LEASH;
        go(g, n, near ? seen : post, n.archetype.speed);
    }

    private static void fightInfected(Game g, Npc n, Npc target) {
        n.state = NpcState.ATTACK;
        double d = Math.sqrt(n.distSqTo(target));
        if (d < 1.9 && Math.abs(n.pos.y - target.pos.y) < 1f) {
            Steering.stop(n);
            face(n, target.pos.x, target.pos.z);
            if (n.attackCooldown <= 0) {
                n.attackCooldown = 1.2f;
                target.hurt(n.archetype.meleeDamage, false);
                target.knockback(n.pos.x, n.pos.z, 2.5f);
                g.audio.playHit();
            }
            return;
        }
        go(g, n, new Vec3i((int) Math.floor(target.pos.x), (int) Math.floor(target.pos.y + .01f),
                (int) Math.floor(target.pos.z)), n.archetype.speed * 1.1f);
    }

    /** Walks a bounded indoor route to a cell; arrived, stands still. */
    private static void go(Game g, Npc n, Vec3i goal, float speed) {
        if (goal == null || !IndoorNavigation.moveTo(g, n, goal, speed, 0.6f)) {
            Steering.stop(n);
        }
    }

    private static void face(Npc n, float tx, float tz) {
        n.yaw = (float) Math.toDegrees(Math.atan2(tx - n.pos.x, -(tz - n.pos.z)));
    }

    /** True for a settled person whose home is a survivor site; they use this brain, not the settlement one. */
    public static boolean handles(Game g, Npc n) {
        return UrbanSiteDirector.siteOf(g.world, n) != null;
    }
}
