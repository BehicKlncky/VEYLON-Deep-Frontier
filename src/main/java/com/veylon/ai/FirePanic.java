package com.veylon.ai;

import com.veylon.Game;
import com.veylon.entity.BodyCombustion;
import com.veylon.entity.Creature;
import com.veylon.entity.Creature.CreatureState;
import com.veylon.entity.Entity;
import com.veylon.entity.Npc;
import com.veylon.entity.Npc.NpcState;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.World;

import static com.veylon.ai.PanicConstants.AFTER_CONTACT_AWAY_WEIGHT;
import static com.veylon.ai.PanicConstants.ARRIVE;
import static com.veylon.ai.PanicConstants.BLOCKED_EXCLUSION_DEGREES;
import static com.veylon.ai.PanicConstants.CANDIDATE_STEP_DEGREES;
import static com.veylon.ai.PanicConstants.CLIMB_MAX;
import static com.veylon.ai.PanicConstants.CLIMB_MIN;
import static com.veylon.ai.PanicConstants.CREATURE_SPEED_MUL;
import static com.veylon.ai.PanicConstants.FLIGHT_CEILING_ABOVE_GROUND;
import static com.veylon.ai.PanicConstants.FLIGHT_DISTANCE_MAX;
import static com.veylon.ai.PanicConstants.FLIGHT_DISTANCE_MIN;
import static com.veylon.ai.PanicConstants.GOAL_DISTANCE_MAX;
import static com.veylon.ai.PanicConstants.GOAL_DISTANCE_MIN;
import static com.veylon.ai.PanicConstants.GOAL_INTERVAL;
import static com.veylon.ai.PanicConstants.GOAL_INTERVAL_JITTER;
import static com.veylon.ai.PanicConstants.LEGACY_PERSON_SPEED;
import static com.veylon.ai.PanicConstants.MIN_STRIDE;
import static com.veylon.ai.PanicConstants.NPC_SPEED_MUL;
import static com.veylon.ai.PanicConstants.RECOVERY_SECONDS;
import static com.veylon.ai.PanicConstants.RECOVERY_STRIDE;
import static com.veylon.ai.PanicConstants.REPLAN_COOLDOWN;
import static com.veylon.ai.PanicConstants.SPREAD_DEGREES;
import static com.veylon.ai.PanicConstants.STUCK_PROGRESS;
import static com.veylon.ai.PanicConstants.STUCK_SECONDS;
import static com.veylon.ai.PanicConstants.TIMER_EPSILON;
import static com.veylon.ai.PanicConstants.TURN_RATE_DEGREES;

/**
 * Burning people and animals drop whatever they were doing and flee
 * (all-living combat and fire contract, section 12).
 *
 * <p>It is the first decision of every NPC and creature AI tick, straight
 * after the dead guard and the per-tick timers: before a conversation holds a
 * person still, before a captive's idling, a trader's or raider's errand, a
 * resident's duty, sleep or combat, and before any species' own choices, so
 * nothing a panicking body did before can move it or let it strike. It lasts
 * while {@code CombustionSystem.isBurning} and for
 * {@link PanicConstants#RECOVERY_SECONDS} after the flames go out; then the
 * body's intent is cleared and its ordinary AI decides afresh in that same
 * tick, from the world as it is. A medical burn is not a fire and never
 * panics anyone; the player never panics at all.
 *
 * <p>A panicking body runs toward a goal a few blocks off, drawn from the
 * panic stream ({@code EntityManager.nextPanicFloat}) every
 * {@link PanicConstants#GOAL_INTERVAL} plus a seeded jitter: away from where
 * the flame last touched it, blended with the way it is already running once
 * it is out of the flames, turned by a seeded angle. Each goal is checked
 * along a straight line with the pathfinder's own footing rules (one-block
 * steps, drops of at most {@link Pathfinder#MAX_DROP}, no fire cells, loaded
 * columns only); a blocked line turns the goal round in fixed steps, and a
 * body with no way out struggles a block at a time where it is. Movement is
 * the shared {@link Steering} at a limited turn rate, so jumps, ladders,
 * water and the fire sidestep behave as ever, and collision keeps every body
 * out of walls, bars and fires; people shoulder gates open, animals do not.
 * Birds fly their escape, climbing, with the same checks in three
 * dimensions. Blocked progress replans, at most once per
 * {@link PanicConstants#REPLAN_COOLDOWN}.
 *
 * <p>The player's position, perceivability and camera are never read: panic
 * is local heat avoidance, so it can neither reveal a Creative player nor
 * aim at one. Nothing here allocates except opening a gate.
 */
public final class FirePanic {

    private static final float TURN_RATE = (float) Math.toRadians(TURN_RATE_DEGREES);
    private static final float SPREAD = (float) Math.toRadians(SPREAD_DEGREES);
    private static final float CANDIDATE_STEP = (float) Math.toRadians(CANDIDATE_STEP_DEGREES);
    private static final float BLOCKED_COS = (float) Math.cos(Math.toRadians(BLOCKED_EXCLUSION_DEGREES));
    /** Directions a goal tries: the drawn one, three turns to either side, then straight back. */
    private static final int CANDIDATES = 8;
    /** How far past its half width a runner checks the ground ahead, blocks. */
    private static final float LOOKAHEAD = 0.35f;
    /** A heading component above this share counts as moving along that axis (about 12 degrees off the other). */
    private static final float AXIS_SHARE = 0.2f;
    /** How far ahead of itself a body aims its steering, blocks. */
    private static final float AIM = 2f;
    /** Steepest a bird climbs or dives: this rise over {@link #AIM} blocks of run. */
    private static final float MAX_FLIGHT_RISE = 2f;
    /** Room a flier's line keeps above and below its body, blocks. */
    private static final float FLIGHT_CLEARANCE = 0.1f;
    /** Closer than this to the last flame contact, horizontally, gives no direction to run from. */
    private static final float MIN_AWAY = 0.05f;
    private static final int NO_FOOTING = Integer.MIN_VALUE;

    private enum Phase {
        /** Not burning and not recovering: the ordinary AI decides. */
        CALM,
        /** Alight or recovering: panic decides. */
        PANIC,
        /** The recovery ended this tick: the ordinary AI decides afresh. */
        RECOVERED
    }

    private FirePanic() {
    }

    /**
     * A person's panic for this tick. True when it decided the tick, so the
     * caller does nothing else; false when the person is calm, including the
     * tick its recovery ends, after which it starts from a clean slate.
     */
    static boolean update(Game g, Npc n, float dt) {
        PanicIntent p = n.panic;
        Phase phase = advance(p, g.combustion.isBurning(n), dt);
        if (phase == Phase.CALM) {
            return false;
        }
        if (phase == Phase.RECOVERED) {
            settle(n);
            return false;
        }
        n.state = NpcState.FLEE;
        // No conversation holds a burning person, and none resumes after.
        n.interactFreeze = 0f;
        if (g.uiMode == Game.UiMode.NPC && g.activeNpc == n) {
            g.closeScreens();
            g.log(n.name + " breaks away, on fire!");
        }
        // What the person knows keeps its age; nothing they meant to do runs.
        n.lastKnownAge += dt;
        n.repathCooldown -= dt;
        if (!columnLoaded(n.world(), n.pos.x, n.pos.z)) {
            Steering.stop(n);
            return true;
        }
        // Panic is physical: an abstract traveller in a loaded column is moved by its body again.
        n.abstractTravel = false;
        float walk = n.archetype != null ? n.archetype.speed : LEGACY_PERSON_SPEED;
        run(g, n, p, dt, walk * NPC_SPEED_MUL, true);
        return true;
    }

    /** An animal's panic for this tick; true when it decided the tick, as for a person. */
    static boolean update(Game g, Creature c, float dt) {
        PanicIntent p = c.panic;
        Phase phase = advance(p, g.combustion.isBurning(c), dt);
        if (phase == Phase.CALM) {
            return false;
        }
        if (phase == Phase.RECOVERED) {
            settle(c);
            return false;
        }
        c.state = CreatureState.FLEE;
        float speed = c.type.speed * CREATURE_SPEED_MUL;
        if (!columnLoaded(c.world(), c.pos.x, c.pos.z)) {
            halt(c);
        } else if (c.type.flying) {
            fly(g, c, p, dt, speed);
        } else {
            run(g, c, p, dt, speed, false);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Start, recovery and the fresh decision after
    // ------------------------------------------------------------------

    private static Phase advance(PanicIntent p, boolean burning, float dt) {
        if (burning) {
            if (!p.active) {
                p.clear();
                p.active = true;
            }
            p.alight = true;
            p.recovery = RECOVERY_SECONDS;
            return Phase.PANIC;
        }
        if (!p.active) {
            return Phase.CALM;
        }
        p.alight = false;
        p.recovery -= dt;
        if (p.recovery > TIMER_EPSILON) {
            return Phase.PANIC;
        }
        p.clear();
        return Phase.RECOVERED;
    }

    /**
     * Forgets every plan a person had before the fire, so the ordinary AI
     * that runs next judges targets, settlement, perception and path from the
     * world as it is now rather than resuming a stale one.
     */
    private static void settle(Npc n) {
        n.state = NpcState.IDLE;
        n.hasTarget = false;
        n.targetBlock = null;
        n.path = null;
        n.pathIndex = 0;
        n.combatTarget = null;
        n.decideTimer = 0f;
        n.workTimer = 0f;
        n.repathCooldown = 0f;
        n.interactFreeze = 0f;
        Steering.stop(n);
    }

    /** An animal's counterpart of {@link #settle(Npc)}: no charge, hunt or meal is resumed. */
    private static void settle(Creature c) {
        c.state = CreatureState.WANDER;
        c.hasTarget = false;
        c.targetEntity = null;
        c.decideTimer = 0f;
        c.eatTimer = 0f;
        halt(c);
    }

    private static void halt(Creature c) {
        if (c.type.flying) {
            c.vel.set(0f, 0f, 0f);
        } else {
            Steering.stop(c);
        }
    }

    // ------------------------------------------------------------------
    // Running and flying
    // ------------------------------------------------------------------

    private static void run(Game g, Entity e, PanicIntent p, float dt, float speed, boolean person) {
        trackProgress(p, e, dt, false);
        p.goalTimer -= dt;
        p.replanCooldown -= dt;
        boolean blocked = p.stuckSeconds >= STUCK_SECONDS;
        boolean arrived = p.hasGoal && flatDistSq(e, p.goalX, p.goalZ) < ARRIVE * ARRIVE;
        if ((!p.hasGoal || p.goalTimer <= 0f || arrived || blocked) && p.replanCooldown <= 0f) {
            chooseGoal(g, e, p, blocked, false, person);
        }
        turnTowardGoal(p, e, dt);
        World w = e.world();
        if (!footingAhead(w, e, p.headingX, p.headingZ)) {
            // A drop too deep, or the edge of the loaded world: stop at it and look for another way.
            Steering.stop(e);
            p.stuckSeconds = STUCK_SECONDS;
            remember(p, e, 0f);
            return;
        }
        if (person) {
            shoulderGateAhead(g, e, p);
        }
        float step = speed * stride(p);
        Steering.moveToward(e, e.pos.x + p.headingX * AIM, e.pos.z + p.headingZ * AIM, step);
        remember(p, e, step * dt);
    }

    private static void fly(Game g, Creature c, PanicIntent p, float dt, float speed) {
        trackProgress(p, c, dt, true);
        p.goalTimer -= dt;
        p.replanCooldown -= dt;
        boolean blocked = p.stuckSeconds >= STUCK_SECONDS;
        boolean arrived = p.hasGoal && c.distSqTo(p.goalX, p.goalY, p.goalZ) < ARRIVE * ARRIVE;
        if ((!p.hasGoal || p.goalTimer <= 0f || arrived || blocked) && p.replanCooldown <= 0f) {
            chooseGoal(g, c, p, blocked, true, false);
        }
        turnTowardGoal(p, c, dt);
        if (!loadedAhead(c.world(), c, p.headingX, p.headingZ)) {
            // Terrain that has not streamed in reads as air: never fly into it.
            c.vel.set(0f, 0f, 0f);
            p.stuckSeconds = STUCK_SECONDS;
            remember(p, c, 0f);
            return;
        }
        float step = speed * stride(p);
        // Climb along the line the goal was checked on, not faster, so the body stays in the air that was clear.
        float toGoal = (float) Math.sqrt(flatDistSq(c, p.goalX, p.goalZ));
        float rise = (p.goalY - c.pos.y) * AIM / Math.max(toGoal, AIM);
        rise = Math.clamp(rise, -MAX_FLIGHT_RISE, MAX_FLIGHT_RISE);
        Steering.flyToward(c, c.pos.x + p.headingX * AIM, c.pos.y + rise, c.pos.z + p.headingZ * AIM, step);
        remember(p, c, step * dt);
    }

    /** Share of the panic speed now: the goal's stride, easing off through the recovery. */
    private static float stride(PanicIntent p) {
        if (p.alight) {
            return p.stride;
        }
        float left = Math.clamp(p.recovery / RECOVERY_SECONDS, 0f, 1f);
        return p.stride * (RECOVERY_STRIDE + (1f - RECOVERY_STRIDE) * left);
    }

    /**
     * Counts time without progress: the body's physics ran after its last AI
     * tick, so the distance since then is what that tick's step achieved. A
     * walker's progress is horizontal (jumping at a wall is not progress); a
     * flier's is in three dimensions.
     */
    private static void trackProgress(PanicIntent p, Entity e, float dt, boolean flying) {
        if (p.lastStep <= 0f) {
            return;
        }
        float dx = e.pos.x - p.lastX;
        float dy = flying ? e.pos.y - p.lastY : 0f;
        float dz = e.pos.z - p.lastZ;
        float moved = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        p.stuckSeconds = moved < p.lastStep * STUCK_PROGRESS ? p.stuckSeconds + dt : 0f;
    }

    private static void remember(PanicIntent p, Entity e, float step) {
        p.lastX = e.pos.x;
        p.lastY = e.pos.y;
        p.lastZ = e.pos.z;
        p.lastStep = step;
    }

    /** Turns the heading toward the goal by no more than the turn rate allows in {@code dt}. */
    private static void turnTowardGoal(PanicIntent p, Entity e, float dt) {
        float dx = p.goalX - e.pos.x;
        float dz = p.goalZ - e.pos.z;
        if (dx * dx + dz * dz < 1e-6f) {
            return;
        }
        float current = (float) Math.atan2(p.headingZ, p.headingX);
        float turn = wrap((float) Math.atan2(dz, dx) - current);
        float limit = TURN_RATE * dt;
        turn = Math.clamp(turn, -limit, limit);
        p.headingX = (float) Math.cos(current + turn);
        p.headingZ = (float) Math.sin(current + turn);
    }

    /**
     * Whether a walker may take its next step along the heading. The columns
     * just past the leading faces of its box (each axis it moves along, and
     * their corner) must be loaded and must offer footing no deeper than the
     * pathfinder's longest drop below its feet; a wall or step there is left
     * to steering and collision. On a ladder or swimming only the columns are
     * checked. So neither the edge of the loaded world nor a cliff is ever
     * under any part of a panicking body.
     */
    private static boolean footingAhead(World w, Entity e, float hx, float hz) {
        float reach = e.width * 0.5f + LOOKAHEAD;
        float ox = hx > AXIS_SHARE ? reach : hx < -AXIS_SHARE ? -reach : 0f;
        float oz = hz > AXIS_SHARE ? reach : hz < -AXIS_SHARE ? -reach : 0f;
        return (ox == 0f || footingAt(w, e, ox, 0f))
                && (oz == 0f || footingAt(w, e, 0f, oz))
                && (ox == 0f || oz == 0f || footingAt(w, e, ox, oz));
    }

    private static boolean footingAt(World w, Entity e, float ox, float oz) {
        int x = (int) Math.floor(e.pos.x + ox);
        int z = (int) Math.floor(e.pos.z + oz);
        if (!columnLoaded(w, x, z)) {
            return false;
        }
        // Checked in the air too, from where the feet are now: a body that stepped
        // off a drop it may take does not carry on over a second one below it.
        if (e.onLadder || e.inWater) {
            return true;
        }
        int y = (int) Math.floor(e.pos.y + 0.01f);
        if (w.getBlock(x, y, z).solid) {
            return true;
        }
        for (int drop = 0; drop <= Pathfinder.MAX_DROP; drop++) {
            BlockType at = w.getBlock(x, y - drop, z);
            if (at == BlockType.WATER || at.isClimbable() || w.getBlock(x, y - drop - 1, z).solid) {
                return true;
            }
        }
        return false;
    }

    /** Whether the columns past a flier's leading faces are loaded, as {@link #footingAhead} checks them. */
    private static boolean loadedAhead(World w, Entity e, float hx, float hz) {
        float reach = e.width * 0.5f + LOOKAHEAD;
        float ox = hx > AXIS_SHARE ? reach : hx < -AXIS_SHARE ? -reach : 0f;
        float oz = hz > AXIS_SHARE ? reach : hz < -AXIS_SHARE ? -reach : 0f;
        return columnLoaded(w, e.pos.x + ox, e.pos.z) && columnLoaded(w, e.pos.x, e.pos.z + oz)
                && columnLoaded(w, e.pos.x + ox, e.pos.z + oz);
    }

    /** People shove a closed gate in their way open, as they do on a path. */
    private static void shoulderGateAhead(Game g, Entity e, PanicIntent p) {
        World w = e.world();
        float reach = e.width * 0.5f + LOOKAHEAD;
        int x = (int) Math.floor(e.pos.x + p.headingX * reach);
        int z = (int) Math.floor(e.pos.z + p.headingZ * reach);
        int y = (int) Math.floor(e.pos.y + 0.01f);
        for (int dy = 0; dy <= 1; dy++) {
            if (w.getBlock(x, y + dy, z) == BlockType.GATE) {
                g.settlementManager.openGate(g, new Vec3i(x, y + dy, z));
            }
        }
    }

    // ------------------------------------------------------------------
    // Goals
    // ------------------------------------------------------------------

    /**
     * Draws a new goal. The same number of draws is taken whatever the
     * terrain, so one body's surroundings never shift the stream for the
     * bodies after it; a walker takes four and a bird five.
     */
    private static void chooseGoal(Game g, Entity e, PanicIntent p, boolean blocked,
                                   boolean flying, boolean opensGates) {
        ensureHeading(p, e);
        float spreadDraw = g.entities.nextPanicFloat();
        float distanceDraw = g.entities.nextPanicFloat();
        float intervalDraw = g.entities.nextPanicFloat();
        float strideDraw = g.entities.nextPanicFloat();
        float climbDraw = flying ? g.entities.nextPanicFloat() : 0f;

        World w = e.world();
        float spread = (spreadDraw * 2f - 1f) * SPREAD;
        float first = escapeAngle(e, p) + spread;
        float side = spread >= 0f ? 1f : -1f;
        float distance = flying
                ? FLIGHT_DISTANCE_MIN + distanceDraw * (FLIGHT_DISTANCE_MAX - FLIGHT_DISTANCE_MIN)
                : GOAL_DISTANCE_MIN + distanceDraw * (GOAL_DISTANCE_MAX - GOAL_DISTANCE_MIN);
        float climb = flying ? flightRise(w, e, CLIMB_MIN + climbDraw * (CLIMB_MAX - CLIMB_MIN)) : 0f;

        // The first direction with a clear run wins; failing that, the one that gets furthest.
        float bestX = 0f, bestZ = 0f, bestRise = 0f, bestShare = -1f;
        for (int i = 0; i < CANDIDATES && bestShare < 1f; i++) {
            float angle = first + candidateTurn(i) * side;
            float ux = (float) Math.cos(angle);
            float uz = (float) Math.sin(angle);
            if (blocked && ux * p.headingX + uz * p.headingZ > BLOCKED_COS) {
                continue;
            }
            // A bird tries its climb first, then level flight under whatever roofs it in.
            int levels = flying && climb != 0f ? 2 : 1;
            for (int level = 0; level < levels && bestShare < 1f; level++) {
                float rise = level == 0 ? climb : 0f;
                float full = flying ? (float) Math.sqrt(distance * distance + rise * rise) : distance;
                float reach = flying ? flightReach(w, e, ux, uz, distance, rise)
                        : groundReach(w, e, ux, uz, distance, opensGates);
                float share = reach >= full - 1e-3f ? 1f : reach / full;
                if (share > bestShare + 1e-4f) {
                    bestShare = share;
                    bestX = ux;
                    bestZ = uz;
                    bestRise = rise;
                }
            }
        }

        // No way out at all (a cage, a pit): push a block against it and try again soon.
        float along = bestShare * distance;
        if (along < 1f) {
            p.trappedGoals++;
            along = 1f;
        }
        p.goalX = e.pos.x + bestX * along;
        p.goalZ = e.pos.z + bestZ * along;
        p.goalY = flying ? e.pos.y + bestRise * along / distance : e.pos.y;
        p.hasGoal = true;
        p.goalTimer = GOAL_INTERVAL + intervalDraw * GOAL_INTERVAL_JITTER;
        p.replanCooldown = REPLAN_COOLDOWN;
        p.stride = MIN_STRIDE + strideDraw * (1f - MIN_STRIDE);
        p.goals++;
        if (blocked) {
            p.replans++;
        }
        p.stuckSeconds = 0f;
    }

    /** The {@code i}th direction tried, as a turn from the drawn one toward its side first. */
    private static float candidateTurn(int i) {
        if (i == CANDIDATES - 1) {
            return (float) Math.PI;
        }
        int steps = (i + 1) / 2;
        return (i % 2 == 1 ? steps : -steps) * CANDIDATE_STEP;
    }

    /**
     * The direction to run: straight away from where a flame touches the body
     * while one does; afterwards half that and half the way it is already
     * running. Without a usable contact point (a pool right under the body's
     * centre), the way it is running.
     */
    private static float escapeAngle(Entity e, PanicIntent p) {
        float bx = p.headingX;
        float bz = p.headingZ;
        BodyCombustion b = e.combustion;
        if (b.hasExposure()) {
            float ax = e.pos.x - b.exposureX();
            float az = e.pos.z - b.exposureZ();
            float len = (float) Math.sqrt(ax * ax + az * az);
            if (len > MIN_AWAY) {
                float away = b.inContact() ? 1f : AFTER_CONTACT_AWAY_WEIGHT;
                float x = ax / len * away + p.headingX * (1f - away);
                float z = az / len * away + p.headingZ * (1f - away);
                if (x * x + z * z > 1e-4f) {
                    bx = x;
                    bz = z;
                }
            }
        }
        return (float) Math.atan2(bz, bx);
    }

    /** A new panic starts running the way the body faces ({@code yaw} 0 faces -z). */
    private static void ensureHeading(PanicIntent p, Entity e) {
        if (p.headingX * p.headingX + p.headingZ * p.headingZ > 0.5f) {
            return;
        }
        double yaw = Math.toRadians(e.yaw);
        p.headingX = (float) Math.sin(yaw);
        p.headingZ = (float) -Math.cos(yaw);
    }

    /**
     * How far a walker gets from where it stands along {@code (ux, uz)}, up to
     * {@code distance}, stepping cell to cell on the pathfinder's footing: the
     * same level, one up with headroom, or down at most
     * {@link Pathfinder#MAX_DROP}; never into an unloaded column or a fire.
     */
    static float groundReach(World w, Entity e, float ux, float uz, float distance, boolean opensGates) {
        int y = (int) Math.floor(e.pos.y + 0.01f);
        int px = (int) Math.floor(e.pos.x);
        int pz = (int) Math.floor(e.pos.z);
        float reached = 0f;
        int steps = (int) Math.ceil(distance);
        for (int i = 1; i <= steps; i++) {
            float along = Math.min(i, distance);
            int x = (int) Math.floor(e.pos.x + ux * along);
            int z = (int) Math.floor(e.pos.z + uz * along);
            if (x != px || z != pz) {
                int next = footing(w, px, y, pz, x, z, opensGates);
                if (next == NO_FOOTING) {
                    return reached;
                }
                y = next;
                px = x;
                pz = z;
            }
            reached = along;
        }
        return reached;
    }

    /** The feet level a walker at {@code (fromX, y, fromZ)} would stand at in column {@code (x, z)}. */
    private static int footing(World w, int fromX, int y, int fromZ, int x, int z, boolean opensGates) {
        if (!opensGates && (w.getBlock(x, y, z) == BlockType.GATE || w.getBlock(x, y + 1, z) == BlockType.GATE)) {
            return NO_FOOTING;
        }
        if (Pathfinder.standable(w, x, y, z)) {
            return y;
        }
        if (Pathfinder.standable(w, x, y + 1, z) && Pathfinder.passable(w, fromX, y + 2, fromZ)) {
            return y + 1;
        }
        for (int drop = 1; drop <= Pathfinder.MAX_DROP; drop++) {
            if (!Pathfinder.passable(w, x, y - drop + 1, z)) {
                break;
            }
            if (Pathfinder.standable(w, x, y - drop, z)) {
                return y - drop;
            }
        }
        return NO_FOOTING;
    }

    /**
     * How far a flier gets from its middle toward a point {@code distance}
     * along {@code (ux, uz)} and {@code rise} up, through open air in loaded
     * columns under the top of the world, sampled every half block at the
     * top and the bottom of its body, so a climb that would graze a roof's
     * edge or a dive that would clip a ledge counts as blocked there.
     */
    static float flightReach(World w, Entity e, float ux, float uz, float distance, float rise) {
        float ox = e.pos.x;
        float oy = e.pos.y + e.height * 0.5f;
        float oz = e.pos.z;
        float half = e.height * 0.5f + FLIGHT_CLEARANCE;
        float length = (float) Math.sqrt(distance * distance + rise * rise);
        float reached = 0f;
        int steps = (int) Math.ceil(length * 2f);
        for (int i = 1; i <= steps; i++) {
            float along = Math.min(i * 0.5f, length);
            float f = along / length;
            int x = (int) Math.floor(ox + ux * distance * f);
            float y = oy + rise * f;
            int z = (int) Math.floor(oz + uz * distance * f);
            if (!openAir(w, x, (int) Math.floor(y - half), z) || !openAir(w, x, (int) Math.floor(y + half), z)) {
                return reached;
            }
            reached = along;
        }
        return reached;
    }

    /** A cell a flier may pass through: loaded, inside the world, no block, water or fire. */
    private static boolean openAir(World w, int x, int y, int z) {
        if (y < 1 || y >= Chunk.SY - 1 || !columnLoaded(w, x, z)) {
            return false;
        }
        BlockType t = w.getBlock(x, y, z);
        return !t.solid && t != BlockType.WATER && t != BlockType.TORCH && t != BlockType.CAMPFIRE;
    }

    /**
     * A bird's climb, held under {@link PanicConstants#FLIGHT_CEILING_ABOVE_GROUND}
     * over the ground beneath it; a bird already above that glides down toward it.
     */
    private static float flightRise(World w, Entity e, float climb) {
        int x = (int) Math.floor(e.pos.x);
        int z = (int) Math.floor(e.pos.z);
        Chunk chunk = w.getChunk(Math.floorDiv(x, Chunk.SX), Math.floorDiv(z, Chunk.SZ));
        if (chunk == null) {
            return 0f;
        }
        float ceiling = chunk.height(Math.floorMod(x, Chunk.SX), Math.floorMod(z, Chunk.SZ))
                + FLIGHT_CEILING_ABOVE_GROUND;
        return Math.clamp(ceiling - e.pos.y, -climb, climb);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static boolean columnLoaded(World w, float x, float z) {
        return columnLoaded(w, (int) Math.floor(x), (int) Math.floor(z));
    }

    private static boolean columnLoaded(World w, int x, int z) {
        return w.getChunk(Math.floorDiv(x, Chunk.SX), Math.floorDiv(z, Chunk.SZ)) != null;
    }

    private static float flatDistSq(Entity e, float x, float z) {
        float dx = e.pos.x - x;
        float dz = e.pos.z - z;
        return dx * dx + dz * dz;
    }

    /** An angle in radians brought into (-pi, pi]. */
    private static float wrap(float a) {
        float twoPi = (float) (Math.PI * 2);
        a %= twoPi;
        if (a > Math.PI) {
            a -= twoPi;
        } else if (a <= -Math.PI) {
            a += twoPi;
        }
        return a;
    }
}
