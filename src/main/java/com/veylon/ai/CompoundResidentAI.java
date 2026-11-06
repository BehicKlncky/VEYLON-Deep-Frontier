package com.veylon.ai;

import com.veylon.Game;
import com.veylon.combat.WorldNoise;
import com.veylon.entity.Npc;
import com.veylon.entity.Npc.NpcState;
import com.veylon.entity.Player;
import com.veylon.settlement.CompoundAccess;
import com.veylon.settlement.CompoundGate;
import com.veylon.settlement.UrbanSite;
import com.veylon.util.Vec3i;
import com.veylon.world.urban.CompoundSites;
import com.veylon.world.urban.CompoundSites.Zone;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.SurvivorSites.Role;

import java.util.List;

/**
 * The people of a guarded stronghold or a raider yard (urban milestone 17). Like everyone of a
 * survivor site they keep to planned cells and walk only bounded routes ({@link IndoorNavigation});
 * what they add is duty.
 *
 * <ul>
 *   <li><b>Posts and patrols.</b> Guards keep their posts by day: a stronghold's gatekeeper on
 *       the porch outside the gate, its sentries inside the gate and at the barricade; a raider
 *       yard's sentries outside and inside its gate. A patrol walks its waypoints round, pausing
 *       {@value #DWELL} seconds at each. By night the inner sentry and the patrol keep watch and
 *       everyone else sleeps.</li>
 *   <li><b>The gate.</b> Nobody walks through a shut gate: a guard bound for the other side goes
 *       to the gate's lane, opens it and passes, and it shuts and locks behind them
 *       ({@link CompoundGate}).</li>
 *   <li><b>Senses.</b> A guard notices the player only as an infected would: in sight, within its
 *       profile's range scaled by light, closer when the player crouches, in front of it or very
 *       near ({@link InfectedAI#seesPlayer}), or by a sound it actually hears. A stronghold's
 *       guards take the player's footsteps, doors and knock where the rules let them be for
 *       everyday life ({@link CompoundAccess#everyday}).</li>
 *   <li><b>Raiders</b> are hostile: any of them who notices a Survival player goes at them,
 *       striking through the player's damage gates, never farther than {@value #LEASH} blocks
 *       from their post, and gives up {@value #FORGET} seconds after losing them. A stronghold's
 *       guards do the same only once it has turned on the player.</li>
 *   <li><b>Infected</b> in sight within {@value #THREAT_RANGE} blocks: guards and raiders fight
 *       them inside their leash; traders, medics and workers retreat to the safe cell.</li>
 * </ul>
 */
public final class CompoundResidentAI {
    public static final float LEASH = 18f, THREAT_RANGE = 12f, DWELL = 4f, FORGET = 14f;
    private static final float EYE = 1.55f, REACH = 1.9f;

    private CompoundResidentAI() {
    }

    public static void update(Game g, Npc n, UrbanSite s, CompoundSites.Compound c, float dt) {
        UrbanSite.Resident r = s.resident(n.residentIndex);
        n.lastKnownAge += dt;
        n.repathCooldown -= dt;
        n.workTimer -= dt;
        n.hunger = r.hunger;
        Npc threat = perceive(g, n, s, c);
        boolean defender = defender(r.role);
        boolean hunter = (s.hunters >> r.slot & 1) != 0 && s.huntTimer > 0f;
        Player p = g.player;
        if (defender && enemy(g, s, c) && p != null && !p.dead && p.isPerceivableByAi() && n.lastKnownAge < FORGET
                && (hunter ? withinHunt(c, n.lastKnown.x, n.lastKnown.z) : withinLeash(g, s, c, r, n.lastKnown.x, n.lastKnown.z))) {
            fightPlayer(g, n, s, c, r);
            return;
        }
        if (threat != null) {
            if (!defender) {
                n.state = NpcState.FLEE;
                travel(g, n, s, c, s.home(g.world.urban).safe(), n.archetype.speed * 1.1f);
                return;
            }
            if (withinLeash(g, s, c, r, threat.pos.x, threat.pos.z)) {
                fightInfected(g, n, s, c, threat);
                return;
            }
        }
        if (!defender && (s.frightened() || s.alertLevel >= 60f)) {
            n.state = NpcState.FLEE;
            travel(g, n, s, c, s.home(g.world.urban).safe(), n.archetype.speed * 1.1f);
            return;
        }
        if (hunter && s.huntFrom != null) {
            // On the trail: to where the player was last seen, looking about there.
            n.state = NpcState.GUARD;
            if (!travel(g, n, s, c, s.huntFrom, n.archetype.speed)) { n.yaw += 60f * dt; }
            return;
        }
        if ((s.responders >> r.slot & 1) != 0 && s.responseTimer > 0f && s.responseAt != null) {
            // Answering an alarm: to the place it came from, and a look round there.
            n.state = NpcState.GUARD;
            if (!travel(g, n, s, c, s.responseAt, n.archetype.speed)) { n.yaw += 60f * dt; }
            return;
        }
        keepGate(g, n, s, c, r);
        Vec3i goal = scheduledCell(g, s, c, r, n);
        n.state = stateFor(g, r, goal);
        boolean arrived = !travel(g, n, s, c, goal, n.archetype.speed * (defender ? 0.9f : 0.8f));
        if (arrived && patrolling(g, r)) {
            // At a waypoint: look about, then walk on to the next.
            if (n.workTimer <= 0f) {
                n.workTimer = DWELL;
            } else if (n.workTimer <= dt) {
                n.patrolIndex = Math.floorMod(n.patrolIndex + 1, c.patrol().size());
            }
            n.yaw += 40f * dt;
        } else if (arrived && defender) {
            watch(n, c, goal);
        }
    }

    /**
     * A guard at a post watches what it guards: the street from the porch, the gate from inside it,
     * the barricade from its post; sight is a cone, so where a guard looks is what it can see.
     */
    private static void watch(Npc n, CompoundSites.Compound c, Vec3i post) {
        ParcelPlan.Side out = c.gate().outward();
        if (post.equals(c.gate().outside()) || post.equals(c.gate().inside())) {
            n.yaw = (float) Math.toDegrees(Math.atan2(out.dx, -out.dz));
            return;
        }
        for (CompoundSites.Barricade line : c.barricades()) {
            Vec3i mid = line.cells().get(1);
            if (!line.gated() && Math.abs(mid.x() - post.x()) + Math.abs(mid.z() - post.z()) <= 24) {
                face(n, mid.x() + .5f, mid.z() + .5f);
                return;
            }
        }
    }

    /**
     * Whom a compound's defenders fight: anyone, for raiders; the player once a stronghold has turned
     * hostile; and a player a stronghold wants gone, while they are still inside its gate.
     */
    static boolean enemy(Game g, UrbanSite s, CompoundSites.Compound c) {
        if (s.hostile()) { return true; }
        if (!CompoundAccess.wanted(g, s) || g.player == null) { return false; }
        Vec3i f = feet(g.player);
        return c.inside(f.x(), f.y(), f.z());
    }

    private static boolean withinHunt(CompoundSites.Compound c, float x, float z) {
        Vec3i gate = c.gate().outside();
        float dx = x - gate.x() - .5f, dz = z - gate.z() - .5f;
        return dx * dx + dz * dz <= CompoundAccess.HUNT_RANGE * CompoundAccess.HUNT_RANGE;
    }

    /**
     * A knock at a stronghold's shut gate (F on a leaf, from either side) is heard by the guard who
     * keeps it: someone it lets through (a friend at any hour, a visitor with a pass by day) has the
     * gate opened for them; a stranger at night is answered through the gate. Standing near the gate
     * asks for nothing.
     */
    private static void keepGate(Game g, Npc n, UrbanSite s, CompoundSites.Compound c, UrbanSite.Resident r) {
        Player p = g.player;
        if (!s.stronghold() || !CompoundAccess.gateGuard(s, r) || p == null || p.dead || s.knockTimer <= 0f) { return; }
        boolean atGate = n.distSqTo(c.gate().inside().x() + .5f, n.pos.y, c.gate().inside().z() + .5f) <= 3.5f * 3.5f
                || n.distSqTo(c.gate().outside().x() + .5f, n.pos.y, c.gate().outside().z() + .5f) <= 3.5f * 3.5f;
        if (!atGate) { return; }
        s.knockTimer = 0f;
        if (!CompoundAccess.letThrough(g, s)) {
            if (CompoundAccess.night(g)) {
                g.log(n.name + " calls through the gate: \"We don't open after dark. Come back at first light.\"");
            }
            return;
        }
        if (CompoundGate.open(g, c) || !CompoundGate.shuttable(g, c) || !atLane(p, c)) { return; }
        CompoundGate.openFor(g, s, c, CompoundGate.HOLD_SECONDS);
        g.log(n.name + " opens the gate for you.");
    }

    private static boolean atLane(Player p, CompoundSites.Compound c) {
        for (Vec3i lane : List.of(c.gate().laneOutside(), c.gate().laneInside(), c.gate().outside(), c.gate().inside())) {
            float dx = p.pos.x - lane.x() - .5f, dz = p.pos.z - lane.z() - .5f;
            if (dx * dx + dz * dz <= CompoundAccess.GATE_REACH * CompoundAccess.GATE_REACH) { return true; }
        }
        return false;
    }

    // ---- the schedule ----------------------------------------------------------------------

    /** Guards, sentries, patrols and raiders of every role stand and fight; traders, medics and workers do not. */
    public static boolean defender(Role role) {
        return role == Role.GATEKEEPER || role == Role.SENTRY || role == Role.PATROL
                || role == Role.BOSS || role == Role.RAIDER;
    }

    /** Whether a resident is on watch through the night: the inner gate sentry and the first patrol. */
    static boolean nightWatch(Role role, int slot, boolean stronghold) {
        if (role == Role.PATROL) { return slot == 2; }
        return role == Role.SENTRY && slot == (stronghold ? 1 : 3);
    }

    static boolean patrolling(Game g, UrbanSite.Resident r) {
        return r.role == Role.PATROL && (!UrbanResidentAI.night(g.time.hourF()) || r.slot == 2);
    }

    /** Where a compound resident's duty or rest wants them now. */
    public static Vec3i scheduledCell(Game g, UrbanSite s, CompoundSites.Compound c, UrbanSite.Resident r, Npc n) {
        double hour = g.time.hourF();
        boolean night = UrbanResidentAI.night(hour);
        if (patrolling(g, r)) {
            int index = n == null ? 0 : Math.floorMod(n.patrolIndex, c.patrol().size());
            return c.patrol().get(index);
        }
        if (night) {
            if (nightWatch(r.role, r.slot, c.stronghold())) { return c.gate().inside(); }
            return s.plan.resident(r.slot).bed();
        }
        if (!defender(r.role) && UrbanResidentAI.meal(hour)) {
            List<Vec3i> meals = s.plan.meals();
            if (!meals.isEmpty()) { return meals.get(r.slot % meals.size()); }
        }
        return s.plan.resident(r.slot).day();
    }

    private static NpcState stateFor(Game g, UrbanSite.Resident r, Vec3i goal) {
        boolean night = UrbanResidentAI.night(g.time.hourF());
        if (defender(r.role)) { return NpcState.GUARD; }
        if (night) { return NpcState.SLEEP; }
        return switch (r.role) {
            case TRADER -> NpcState.TRADE;
            case MEDIC -> NpcState.HEAL;
            default -> UrbanResidentAI.meal(g.time.hourF()) ? NpcState.WARM_BY_FIRE : NpcState.IDLE;
        };
    }

    // ---- moving: the gate ------------------------------------------------------------------

    /**
     * Walks a bounded route to a cell, passing the gate when the cell lies on its other side.
     * False on arrival.
     */
    static boolean travel(Game g, Npc n, UrbanSite s, CompoundSites.Compound c, Vec3i goal, float speed) {
        if (goal == null) {
            Steering.stop(n);
            return false;
        }
        boolean fromOutside = outside(c, feet(n)), toOutside = outside(c, goal);
        if (fromOutside != toOutside && !CompoundGate.open(g, c) && CompoundGate.shuttable(g, c)) {
            Vec3i lane = fromOutside ? c.gate().laneOutside() : c.gate().laneInside();
            if (n.distSqTo(lane.x() + .5f, lane.y(), lane.z() + .5f) <= 1.6f * 1.6f) {
                CompoundGate.openFor(g, s, c, CompoundGate.HOLD_SECONDS);
            } else {
                go(g, n, lane, speed);
                return true;
            }
        }
        return go(g, n, goal, speed);
    }

    private static boolean outside(CompoundSites.Compound c, Vec3i cell) {
        Zone z = c.zoneAt(cell.x(), cell.y(), cell.z());
        return z == Zone.OUTSIDE || z == Zone.PORCH;
    }

    private static boolean go(Game g, Npc n, Vec3i goal, float speed) {
        if (!IndoorNavigation.moveTo(g, n, goal, speed, 0.6f)) {
            Steering.stop(n);
            return false;
        }
        return true;
    }

    // ---- senses and fighting ---------------------------------------------------------------

    /**
     * Sight of the player through the fair test every hostile shares, hearing, and the nearest
     * infected in sight. What a stronghold's guard sees or hears is what its rules act on
     * ({@link CompoundAccess}). Runs on a staggered interval; returns the infected threat, if any.
     */
    private static Npc perceive(Game g, Npc n, UrbanSite s, CompoundSites.Compound c) {
        if (n.decideTimer > 0) { return n.combatTarget instanceof Npc t && !t.dead ? t : null; }
        n.decideTimer = UrbanResidentAI.PERCEPTION_INTERVAL + g.entities.nextSettledNpcAiFloat() * 0.15f;
        n.perceptionChecks++;
        Player p = g.player;
        if (p != null && !p.dead) {
            boolean seen = n.distSqTo(p) < 24 * 24
                    && InfectedAI.clearSight(g.world, n.pos.x, n.pos.y + EYE, n.pos.z, p.pos.x, p.pos.y + p.eyeHeight(), p.pos.z);
            if (seen) { g.settlementManager.urbanSites.onResidentSeen(g, s); }
            if (InfectedAI.seesPlayer(g, n)) {
                n.lastKnown.set(p.pos);
                n.lastKnownAge = 0;
                if (s.stronghold() && defender(s.resident(n.residentIndex).role)) {
                    CompoundAccess.sighted(g, s, c, n, feet(p));
                }
            }
        }
        WorldNoise.NoiseEvent heard = g.noise.loudestAudible(g.world, n.pos.x, n.pos.y, n.pos.z,
                n.archetype.hearRange * 0.3f, n.lastHeard, n);
        if (heard != null && (heard.playerSource || heard.intensity > 0.5f)) {
            n.lastHeard = heard;
            // A visitor walking the yard is no alarm; the same steps in the barracks are.
            if (!CompoundAccess.everyday(g, s, c, heard)) { s.alertLevel = Math.min(100f, s.alertLevel + heard.intensity * 20f); }
            if (s.stronghold()) { CompoundAccess.heard(g, s, c, n, heard); }
            if (heard.playerSource && enemy(g, s, c) && p != null && p.isPerceivableByAi()) {
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
            s.fear = Math.min(100f, s.fear + 3f);
        }
        return threat;
    }

    /** A post's leash: a defender goes no farther than this from where its duty keeps it. */
    private static boolean withinLeash(Game g, UrbanSite s, CompoundSites.Compound c, UrbanSite.Resident r, float x, float z) {
        Vec3i post = s.plan.resident(r.slot).day();
        float dx = x - post.x() - .5f, dz = z - post.z() - .5f;
        return dx * dx + dz * dz <= LEASH * LEASH;
    }

    private static void fightPlayer(Game g, Npc n, UrbanSite s, CompoundSites.Compound c, UrbanSite.Resident r) {
        Player p = g.player;
        n.state = NpcState.ATTACK;
        double dist = Math.sqrt(n.distSqTo(p));
        if (dist < REACH && Math.abs(n.pos.y - p.pos.y) < .8f
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
        travel(g, n, s, c, cell(n.lastKnown.x, n.lastKnown.y, n.lastKnown.z), n.archetype.speed);
    }

    private static void fightInfected(Game g, Npc n, UrbanSite s, CompoundSites.Compound c, Npc target) {
        n.state = NpcState.ATTACK;
        double d = Math.sqrt(n.distSqTo(target));
        if (d < REACH && Math.abs(n.pos.y - target.pos.y) < 1f) {
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
        travel(g, n, s, c, cell(target.pos.x, target.pos.y, target.pos.z), n.archetype.speed * 1.1f);
    }

    private static void face(Npc n, float tx, float tz) {
        n.yaw = (float) Math.toDegrees(Math.atan2(tx - n.pos.x, -(tz - n.pos.z)));
    }

    private static Vec3i feet(com.veylon.entity.Entity n) {
        return cell(n.pos.x, n.pos.y, n.pos.z);
    }

    private static Vec3i cell(float x, float y, float z) {
        return new Vec3i((int) Math.floor(x), (int) Math.floor(y + .01f), (int) Math.floor(z));
    }
}
