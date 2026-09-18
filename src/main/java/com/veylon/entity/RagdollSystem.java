package com.veylon.entity;

import com.veylon.Game;
import com.veylon.simulation.SimulationSystem;
import com.veylon.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixed-step PBD death simulation. Independent endpoint inertia, parent-relative
 * limited joints and sampled voxel contacts determine the pose. Torso contact
 * lever arms produce rotation; no resting attitude is prescribed.
 *
 * <p>The position hash supplies only a destabilizing launch impulse. Frame time
 * is clamped and accumulated, never consulted by the solver. All scratch is
 * preallocated; only spawning and emitting a corpse allocate.
 *
 * <p>Quiet grounded bodies freeze after consecutive low-energy steps, with a
 * hard timeout as a backstop. Saves and the population cap also freeze bodies,
 * preserving appearance, arrows, blood and the solved joint pose.
 */
public class RagdollSystem implements SimulationSystem {

    /** Bodies currently falling, oldest first. */
    public final List<Ragdoll> live = new ArrayList<>();

    private final RagdollCollision collision = new RagdollCollision();
    private final RagdollJoints joints = new RagdollJoints();

    private static final int MAX_POINTS = BodySkeleton.MAX_BONES + 1;
    private final float[] originX = new float[MAX_POINTS];
    private final float[] originY = new float[MAX_POINTS];
    private final float[] originZ = new float[MAX_POINTS];
    private final float[] wantVy = new float[MAX_POINTS];
    private final boolean[] floorHit = new boolean[MAX_POINTS];
    private final boolean[] resting = new boolean[MAX_POINTS];

    private double accumulator;

    /** Diagnostics for the debug overlay and the QA scene. */
    public long totalSpawned;
    public long totalSettled;
    public int stepsLastUpdate;

    @Override
    public void reset() {
        live.clear();
        collision.reset();
        joints.reset();
        accumulator = 0;
        totalSpawned = 0;
        totalSettled = 0;
        stepsLastUpdate = 0;
    }

    // ------------------------------------------------------------------
    // Spawning
    // ------------------------------------------------------------------

    /**
     * Starts a body for a creature that actually died. Birds are too small to
     * leave anything, so theirs tumbles briefly and disappears.
     */
    public Ragdoll spawn(Game g, Creature c) {
        BodySkeleton skeleton = BodySkeleton.of(c.type);
        Ragdoll r = new Ragdoll(skeleton, c.type,
                c.type != Creature.CreatureType.BIRD);
        r.stuckArrows = c.stuckArrows;
        r.stuckArrowType = c.stuckArrowType;
        return begin(g, r, c, Math.max(0.6f, c.type.height));
    }

    /** Starts a body for a person who actually died. */
    public Ragdoll spawn(Game g, Npc n) {
        Ragdoll r = new Ragdoll(BodySkeleton.humanoid(), null, true);
        r.appearance.capture(n);
        return begin(g, r, n, 1.2f);
    }

    private Ragdoll begin(Game g, Ragdoll r, Entity e, float burstPower) {
        if (live.size() >= RagdollConstants.MAX_LIVE) {
            settleOldest(g);
        }
        BodySkeleton s = r.skeleton;
        r.yaw = (float) Math.toRadians(-e.yaw);
        float cos = (float) Math.cos(r.yaw);
        float sin = (float) Math.sin(r.yaw);

        float tx = e.pos.x;
        float ty = e.pos.y + s.torsoY;
        float tz = e.pos.z;
        r.px[Ragdoll.TORSO] = tx;
        r.py[Ragdoll.TORSO] = ty;
        r.pz[Ragdoll.TORSO] = tz;
        // The three damage paths all knock back after hurt() and removal only
        // happens on the next fast tick, so the entity's velocity is still the
        // launch velocity of the blow that killed it.
        r.vx[Ragdoll.TORSO] = e.vel.x;
        r.vy[Ragdoll.TORSO] = e.vel.y;
        r.vz[Ragdoll.TORSO] = e.vel.z;

        joints.initialize(r);
        for (int p = 1; p < r.pointCount; p++) {
            r.vx[p] = e.vel.x;
            r.vy[p] = e.vel.y;
            r.vz[p] = e.vel.z;
        }

        applyAngularKick(r, e, cos, sin);
        live.add(r);
        totalSpawned++;
        burst(g, r, e, burstPower);
        return r;
    }

    /**
     * Turns the killing blow into spin. A shove along the body's own forward
     * axis pitches it over; a shove across that axis rolls it. Where nothing
     * pushed — an animal that starved, someone who succumbed to illness — the
     * side it falls on comes from a hash of the position rather than a
     * generator, so the world stays reproducible without another RNG stream.
     */
    private void applyAngularKick(Ragdoll r, Entity e, float cos, float sin) {
        float forward = e.vel.x * -sin + e.vel.z * -cos;
        float right = e.vel.x * cos + e.vel.z * -sin;
        float speed = (float) Math.sqrt(forward * forward + right * right);
        float gain = RagdollConstants.ANGULAR_KICK
                * Math.min(1.2f, Math.max(0.35f, speed * 0.30f));
        if (speed < 0.05f) {
            // Nothing pushed: topple sideways, deterministically.
            int bias = positionBias(e.pos.x, e.pos.y, e.pos.z);
            r.rollVel = -bias * gain;
            r.pitchVel = 0;
        } else {
            r.pitchVel = -forward * gain * 0.55f;
            r.rollVel = -right * gain * 0.55f;
        }
        r.yawVel = right * 0.18f;
        // An unactuated straight leg is a singular chain. A one-off angular
        // impulse breaks that balance; it does not prescribe a landing angle.
        float tilt = (float) Math.sqrt(r.pitchVel * r.pitchVel + r.rollVel * r.rollVel);
        if (tilt < RagdollConstants.MIN_TOPPLE_SPEED) {
            if (tilt < 1e-4f) r.rollVel = positionBias(e.pos.x, e.pos.y, e.pos.z)
                    * RagdollConstants.MIN_TOPPLE_SPEED;
            else {
                r.pitchVel *= RagdollConstants.MIN_TOPPLE_SPEED / tilt;
                r.rollVel *= RagdollConstants.MIN_TOPPLE_SPEED / tilt;
            }
        }
    }

    /** Deterministic +1/-1 from a position; stands in for a coin flip. */
    private static int positionBias(float x, float y, float z) {
        int h = Float.floatToIntBits(x) * 31
                + Float.floatToIntBits(y) * 17
                + Float.floatToIntBits(z) * 13;
        h ^= h >>> 15;
        return (h & 1) == 0 ? 1 : -1;
    }

    private void burst(Game g, Ragdoll r, Entity e, float power) {
        float dx = e.vel.x;
        float dy = e.vel.y;
        float dz = e.vel.z;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.4f) {
            dx = 0;
            dy = 1;
            dz = 0;
        } else {
            dx /= len;
            dy /= len;
            dz /= len;
        }
        g.particles.bloodBurst(r.px[Ragdoll.TORSO], r.py[Ragdoll.TORSO], r.pz[Ragdoll.TORSO],
                dx, dy, dz, power);
    }

    // ------------------------------------------------------------------
    // Per-frame simulation
    // ------------------------------------------------------------------

    /**
     * Drains accumulated frame time in whole fixed steps. Called from the
     * per-frame bucket inside the simulate gate, so a paused game never
     * advances a body.
     */
    public void update(Game g, float dt) {
        stepsLastUpdate = 0;
        if (live.isEmpty()) {
            accumulator = 0;
            return;
        }
        if (!(dt > 0) || !Float.isFinite(dt)) {
            return;
        }
        accumulator += Math.min(dt,
                RagdollConstants.FIXED_STEP * RagdollConstants.MAX_STEPS_PER_FRAME);
        while (accumulator + 1e-7 >= RagdollConstants.FIXED_STEP
                && stepsLastUpdate < RagdollConstants.MAX_STEPS_PER_FRAME) {
            accumulator = Math.max(0, accumulator - RagdollConstants.FIXED_STEP);
            stepsLastUpdate++;
            for (int i = live.size() - 1; i >= 0; i--) {
                Ragdoll r = live.get(i);
                step(g, r, RagdollConstants.FIXED_STEP);
                if (r.settled) {
                    emit(g, r);
                    live.remove(i);
                }
            }
            if (live.isEmpty()) {
                break;
            }
        }
        // A stall clamps rather than replaying a hundred steps next frame.
        if (accumulator > RagdollConstants.FIXED_STEP) {
            accumulator = 0;
        }
    }

    private void step(Game g, Ragdoll r, float dt) {
        World world = g.world;
        r.age = Math.min(RagdollConstants.SETTLE_TIMEOUT, r.age + dt);
        joints.remember(r);
        integratePoints(world, r, dt);
        integrateAngles(r, dt);
        float energyBudget = measureEnergy(r);
        joints.solve(world, r);
        r.torsoGrounded = joints.groundContact[0];
        deriveVelocities(r, dt);
        joints.deriveAngularVelocity(r, dt);
        if (r.torsoGrounded) {
            float keep = 1f - RagdollConstants.GROUND_FRICTION;
            r.pitchVel *= keep;
            r.yawVel *= keep;
            r.rollVel *= keep;
        }
        dissipateProjectionEnergy(r, energyBudget);
        joints.writePose(r);
        drip(g, r, dt);

        r.energy = measureEnergy(r);
        updateQuietWindow(r);
        boolean tooFar = g.player != null && r.distSqTo(g.player.pos.x, g.player.pos.y,
                g.player.pos.z) > RagdollConstants.DESPAWN_DISTANCE
                * RagdollConstants.DESPAWN_DISTANCE;
        if ((r.quietSteps >= RagdollConstants.SETTLE_STEPS && r.energy < RagdollConstants.SETTLE_ENERGY)
                || r.quietSteps >= RagdollConstants.SUPPORT_SLEEP_STEPS
                || r.age >= RagdollConstants.SETTLE_TIMEOUT
                || tooFar) {
            r.settled = true;
        }
    }

    private void integratePoints(World world, Ragdoll r, float dt) {
        boolean anyGround = false;
        for (int p = 0; p < r.pointCount; p++) {
            originX[p] = r.px[p];
            originY[p] = r.py[p];
            originZ[p] = r.pz[p];

            float half = p == Ragdoll.TORSO
                    ? r.skeleton.torsoRadius : r.skeleton.radius[p - 1];
            boolean water = collision.inWater(world, r.px[p], r.py[p], r.pz[p]);
            r.vy[p] -= (water ? RagdollConstants.GRAVITY_WATER
                    : RagdollConstants.GRAVITY_AIR) * dt;
            if (water) {
                r.vy[p] = Math.max(r.vy[p], RagdollConstants.WATER_MAX_DESCENT);
            }
            float keep = 1f - RagdollConstants.AIR_DRAG;
            r.vx[p] *= keep;
            r.vy[p] *= keep;
            r.vz[p] *= keep;
            clampSpeed(r, p);
            wantVy[p] = r.vy[p];

            // VoxelPhysics halves horizontal travel in water rather than
            // velocity; matching that keeps a body's drift through a pond
            // identical to the animal's a moment earlier.
            float drag = water ? RagdollConstants.WATER_DRAG : 1f;
            collision.move(world, r.px[p], r.py[p], r.pz[p],
                    r.vx[p] * dt * drag, r.vy[p] * dt, r.vz[p] * dt * drag, half, half);
            r.px[p] = collision.x;
            r.py[p] = collision.y;
            r.pz[p] = collision.z;
            floorHit[p] = collision.hitY;
            resting[p] = collision.grounded;
            anyGround |= collision.grounded;
        }
        r.grounded = anyGround;
    }

    /** Sleep measures the entire chain, not just its centre or an authored pose. */
    private static void updateQuietWindow(Ragdoll r) {
        if (!r.torsoGrounded) { r.quietSteps = 0; return; }
        boolean moved = r.quietSteps == 0;
        float distance = RagdollConstants.SLEEP_DISTANCE;
        for (int p = 0; p < r.pointCount && !moved; p++) {
            float x = r.px[p] - r.sleepX[p], y = r.py[p] - r.sleepY[p], z = r.pz[p] - r.sleepZ[p];
            moved = x * x + y * y + z * z > distance * distance;
        }
        float angle = RagdollConstants.SLEEP_ANGLE;
        moved |= Math.abs(r.orientation.dot(r.sleepOrientation)) < Math.cos(angle * 0.5f);
        if (moved) {
            System.arraycopy(r.px, 0, r.sleepX, 0, r.pointCount);
            System.arraycopy(r.py, 0, r.sleepY, 0, r.pointCount);
            System.arraycopy(r.pz, 0, r.sleepZ, 0, r.pointCount);
            r.sleepOrientation.set(r.orientation);
            r.quietSteps = 1;
        } else r.quietSteps++;
    }

    private static void clampSpeed(Ragdoll r, int p) {
        float sq = r.vx[p] * r.vx[p] + r.vy[p] * r.vy[p] + r.vz[p] * r.vz[p];
        float max = RagdollConstants.MAX_POINT_SPEED;
        if (sq > max * max) {
            float scale = max / (float) Math.sqrt(sq);
            r.vx[p] *= scale;
            r.vy[p] *= scale;
            r.vz[p] *= scale;
        }
    }

    private void integrateAngles(Ragdoll r, float dt) {
        float damping = r.torsoGrounded
                ? RagdollConstants.GROUND_ANGULAR_DAMPING : RagdollConstants.ANGULAR_DAMPING;
        float shed = Math.min(1f, damping * dt);
        r.yawVel -= r.yawVel * shed;
        r.pitchVel -= r.pitchVel * shed;
        r.rollVel -= r.rollVel * shed;
        r.yawVel = clampAngular(r.yawVel);
        r.pitchVel = clampAngular(r.pitchVel);
        r.rollVel = clampAngular(r.rollVel);
        r.orientation.rotateLocalX(r.pitchVel * dt).rotateLocalY(r.yawVel * dt)
                .rotateLocalZ(r.rollVel * dt).normalize();
    }

    private static float clampAngular(float w) {
        float max = RagdollConstants.MAX_ANGULAR_SPEED;
        return w > max ? max : (w < -max ? -max : w);
    }

    /**
     * Velocity is what the body actually moved, not what it was asked to move.
     * Reading it back from the travelled distance is what makes the constraint
     * pass stable: a bone yanked back on also slows down.
     */
    private void deriveVelocities(Ragdoll r, float dt) {
        float inv = 1f / dt;
        for (int p = 0; p < r.pointCount; p++) {
            r.vx[p] = (r.px[p] - originX[p]) * inv;
            r.vy[p] = (r.py[p] - originY[p]) * inv;
            r.vz[p] = (r.pz[p] - originZ[p]) * inv;
            if (floorHit[p] && wantVy[p] < 0) {
                r.vy[p] = -wantVy[p] * RagdollConstants.BOUNCE;
            }
            if (joints.groundContact[p] && wantVy[p] < 0) {
                // Positional penetration repair must not create a fresh bounce.
                r.vy[p] = Math.min(r.vy[p], -wantVy[p] * RagdollConstants.BOUNCE);
            }
            if (resting[p] || joints.contact[p]) {
                float keep = 1f - RagdollConstants.GROUND_FRICTION;
                r.vx[p] *= keep;
                r.vz[p] *= keep;
            }
            clampSpeed(r, p);
        }
    }

    private float measureEnergy(Ragdoll r) {
        float linear = 0;
        for (int p = 0; p < r.pointCount; p++) {
            linear = Math.max(linear, r.vx[p] * r.vx[p] + r.vy[p] * r.vy[p] + r.vz[p] * r.vz[p]);
        }
        return linear
                + r.yawVel * r.yawVel + r.pitchVel * r.pitchVel + r.rollVel * r.rollVel;
    }

    /**
     * Iterated contacts at a voxel corner may repair position more than once.
     * That repair is not an impulse: do not let it manufacture kinetic energy.
     * Contact friction dissipates velocity separately; gravity still supplies
     * energy on the next fixed step and unsupported limbs can continue falling.
     */
    private void dissipateProjectionEnergy(Ragdoll r, float budget) {
        float energy = measureEnergy(r);
        if (energy <= budget || energy < 1e-10f) return;
        float scale = (float) Math.sqrt(budget / energy);
        for (int p = 0; p < r.pointCount; p++) {
            r.vx[p] *= scale;
            r.vy[p] *= scale;
            r.vz[p] *= scale;
        }
        r.pitchVel *= scale;
        r.yawVel *= scale;
        r.rollVel *= scale;
    }

    private void drip(Game g, Ragdoll r, float dt) {
        r.dripTimer -= dt;
        if (r.dripTimer > 0
                || r.torsoSpeedSq() < RagdollConstants.DRIP_SPEED * RagdollConstants.DRIP_SPEED) {
            return;
        }
        r.dripTimer = RagdollConstants.DRIP_INTERVAL;
        g.particles.bloodDrip(r.px[Ragdoll.TORSO], r.py[Ragdoll.TORSO], r.pz[Ragdoll.TORSO],
                r.vx[Ragdoll.TORSO], r.vy[Ragdoll.TORSO], r.vz[Ragdoll.TORSO]);
    }

    // ------------------------------------------------------------------
    // Settling
    // ------------------------------------------------------------------

    /** Freezes every live body at once. Used by the cap and by save/load. */
    public void settleAll(Game g) {
        for (int i = 0; i < live.size(); i++) {
            Ragdoll r = live.get(i);
            r.settled = true;
            emit(g, r);
        }
        live.clear();
    }

    private void settleOldest(Game g) {
        if (live.isEmpty()) {
            return;
        }
        Ragdoll r = live.removeFirst();
        r.settled = true;
        emit(g, r);
    }

    /**
     * Converts a settled body into what it leaves behind: a carcass for an
     * animal, a corpse for a person, nothing at all for a bird. The body is
     * pushed clear of anything a world edit may have wrapped around it first,
     * so nothing ever comes to rest inside a solid voxel.
     */
    private void emit(Game g, Ragdoll r) {
        totalSettled++;
        joints.clearWorld(g.world, r);
        joints.writePose(r);
        float x = r.px[Ragdoll.TORSO];
        float y = r.py[Ragdoll.TORSO];
        float z = r.pz[Ragdoll.TORSO];
        float groundY = lowestPoint(r);
        if (!r.leavesBody) {
            return;
        }
        if (r.human()) {
            HumanCorpse corpse = new HumanCorpse(x, y, z);
            corpse.appearance.copyFrom(r.appearance);
            corpse.pose.copyFrom(r.pose);
            g.entities.corpses.add(corpse);
        } else {
            Carcass carcass = new Carcass(r.creatureType, x, y, z);
            carcass.stuckArrows = r.stuckArrows;
            carcass.stuckArrowType = r.stuckArrowType;
            carcass.pose.copyFrom(r.pose);
            g.entities.carcasses.add(carcass);
        }
        // A kill leaves a mark. Track.describe only names a creature type on
        // the non-blood branch, so a null type is safe for a person.
        g.entities.addTrack(new Track(x, groundY + 0.02f, z,
                (float) Math.toDegrees(-r.yaw), r.creatureType, true));
    }

    private float lowestPoint(Ragdoll r) {
        float lowest = r.py[Ragdoll.TORSO] - r.skeleton.torsoRadius;
        for (int p = 1; p < r.pointCount; p++) {
            lowest = Math.min(lowest, r.py[p] - r.skeleton.radius[p - 1]);
        }
        return lowest;
    }

    public int liveCount() {
        return live.size();
    }
}
