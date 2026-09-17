package com.veylon.entity;

import com.veylon.Game;
import com.veylon.simulation.SimulationSystem;
import com.veylon.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Simulates bodies between the killing blow and the resting corpse.
 *
 * <h2>The solver</h2>
 *
 * <p>Position-based dynamics over an articulated chain, not a rigid-body
 * engine. Each body is one torso point carrying the orientation plus one point
 * mass per appendage bone. A step integrates every point against gravity and
 * voxel collision, relaxes the bones back onto their pivots a few times, and
 * derives velocity from the distance actually travelled — so a constraint that
 * moves a point also changes how fast it is moving, which is what keeps the
 * chain stable without a solver that can explode.
 *
 * <p>Orientation is three explicit angles rather than a quaternion or an
 * inertia tensor. The killing blow hands the body an angular kick, a snagged
 * limb torques it, and once it is on the ground a spring rolls it onto its
 * resting side. That is far less machinery than a real rigid-body solver and
 * it is all a two-second tumble seen from ten metres needs.
 *
 * <p>The step is fixed at {@link RagdollConstants#FIXED_STEP} and driven from
 * the per-frame bucket, so a body tumbles at the same rate at 30 fps as at 144
 * and a stalled frame clamps instead of teleporting bodies through walls.
 *
 * <h2>Settling</h2>
 *
 * <p>A body freezes when {@link Ragdoll#energy} — squared point speeds, squared
 * angular speeds and the squared error against its resting orientation — stays
 * under {@link RagdollConstants#SETTLE_ENERGY} for
 * {@link RagdollConstants#SETTLE_STEPS} consecutive steps, or when
 * {@link RagdollConstants#SETTLE_TIMEOUT} elapses. Folding the orientation
 * error into the measure is what stops a body freezing while still standing
 * upright; the timeout is what guarantees one can never stay unsettled.
 *
 * <h2>Determinism</h2>
 *
 * <p>There is no {@link java.util.Random} here and there deliberately never
 * will be. Everything a body does follows from the impulse it was killed with;
 * where a choice is genuinely free — which side it falls on when nothing pushed
 * it — the sign comes from a hash of the death position. The only randomness in
 * the feature lives in {@code ParticleSystem}, which is already seeded from the
 * world seed and is presentation-only.
 *
 * <h2>Persistence</h2>
 *
 * <p>Ragdolls are transient and are not saved. A save taken mid-fall settles
 * the body first, so it produces a corpse rather than losing one; the corpse
 * and its pose do persist.
 */
public class RagdollSystem implements SimulationSystem {

    /** Bodies currently falling, oldest first. */
    public final List<Ragdoll> live = new ArrayList<>();

    private final RagdollCollision collision = new RagdollCollision();
    private final Matrix4f frame = new Matrix4f();
    private final Matrix4f inverse = new Matrix4f();
    private final Vector3f offset = new Vector3f();
    private final Vector3f local = new Vector3f();

    private static final int MAX_POINTS = BodySkeleton.MAX_BONES + 1;
    private final float[] originX = new float[MAX_POINTS];
    private final float[] originY = new float[MAX_POINTS];
    private final float[] originZ = new float[MAX_POINTS];
    private final float[] wantVy = new float[MAX_POINTS];
    private final boolean[] floorHit = new boolean[MAX_POINTS];
    private final boolean[] resting = new boolean[MAX_POINTS];

    private float accumulator;

    /** Diagnostics for the debug overlay and the QA scene. */
    public long totalSpawned;
    public long totalSettled;
    public int stepsLastUpdate;

    @Override
    public void reset() {
        live.clear();
        collision.reset();
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

        for (int b = 0; b < s.boneCount; b++) {
            int p = b + 1;
            float lx = s.pivotX[b] + BodySkeleton.restX(s.axis[b]) * s.length[b];
            float ly = s.pivotY[b] + BodySkeleton.restY(s.axis[b]) * s.length[b] - s.torsoY;
            float lz = s.pivotZ[b] + BodySkeleton.restZ(s.axis[b]) * s.length[b];
            r.px[p] = tx + lx * cos + lz * sin;
            r.py[p] = ty + ly;
            r.pz[p] = tz - lx * sin + lz * cos;
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
        r.restRoll = r.human() ? 0f
                : sideSign(r.rollVel, e.pos) * RagdollConstants.CREATURE_REST_ROLL;
        r.restPitch = r.human()
                ? sideSign(r.pitchVel != 0 ? r.pitchVel : r.rollVel, e.pos)
                        * RagdollConstants.HUMAN_REST_PITCH
                : 0f;
    }

    private static float sideSign(float velocity, Vector3f at) {
        if (velocity > 1e-4f) {
            return 1f;
        }
        if (velocity < -1e-4f) {
            return -1f;
        }
        return positionBias(at.x, at.y, at.z);
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
        while (accumulator >= RagdollConstants.FIXED_STEP
                && stepsLastUpdate < RagdollConstants.MAX_STEPS_PER_FRAME) {
            accumulator -= RagdollConstants.FIXED_STEP;
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
        r.age += dt;
        integratePoints(world, r, dt);
        integrateAngles(r, dt);
        constrain(r);
        deriveVelocities(r, dt);
        writePose(r);
        drip(g, r, dt);

        r.energy = measureEnergy(r);
        if (r.energy < RagdollConstants.SETTLE_ENERGY && r.grounded) {
            r.quietSteps++;
        } else {
            r.quietSteps = 0;
        }
        boolean tooFar = g.player != null && r.distSqTo(g.player.pos.x, g.player.pos.y,
                g.player.pos.z) > RagdollConstants.DESPAWN_DISTANCE
                * RagdollConstants.DESPAWN_DISTANCE;
        if (r.quietSteps >= RagdollConstants.SETTLE_STEPS
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
                    ? r.skeleton.torsoRadius : RagdollConstants.POINT_RADIUS;
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
        if (r.grounded) {
            r.rollVel += shortest(r.restRoll - r.roll) * RagdollConstants.SETTLE_TORQUE * dt;
            r.pitchVel += shortest(r.restPitch - r.pitch) * RagdollConstants.SETTLE_TORQUE * dt;
        }
        float damping = r.grounded
                ? RagdollConstants.GROUND_ANGULAR_DAMPING : RagdollConstants.ANGULAR_DAMPING;
        float shed = Math.min(1f, damping * dt);
        r.yawVel -= r.yawVel * shed;
        r.pitchVel -= r.pitchVel * shed;
        r.rollVel -= r.rollVel * shed;
        r.yawVel = clampAngular(r.yawVel);
        r.pitchVel = clampAngular(r.pitchVel);
        r.rollVel = clampAngular(r.rollVel);
        r.yaw = wrap(r.yaw + r.yawVel * dt);
        r.pitch = wrap(r.pitch + r.pitchVel * dt);
        r.roll = wrap(r.roll + r.rollVel * dt);
    }

    private static float clampAngular(float w) {
        float max = RagdollConstants.MAX_ANGULAR_SPEED;
        return w > max ? max : (w < -max ? -max : w);
    }

    /** Shortest signed representation of an angle difference, in (-pi, pi]. */
    static float shortest(float angle) {
        return wrap(angle);
    }

    static float wrap(float angle) {
        float a = angle;
        while (a > (float) Math.PI) {
            a -= (float) (Math.PI * 2);
        }
        while (a <= -(float) Math.PI) {
            a += (float) (Math.PI * 2);
        }
        return a;
    }

    /**
     * Relaxes every bone back onto its pivot. Each pass pulls the point toward
     * the direction the model authored the bone along — a cheap stand-in for
     * real joint limits — then enforces the bone's length exactly, feeding part
     * of the correction back into the torso as both a shove and a torque so a
     * leg catching on a ledge actually tips the body over.
     */
    private void constrain(Ragdoll r) {
        BodySkeleton s = r.skeleton;
        if (s.boneCount == 0) {
            return;
        }
        for (int iteration = 0; iteration < RagdollConstants.RELAX_ITERATIONS; iteration++) {
            buildFrame(r);
            for (int b = 0; b < s.boneCount; b++) {
                int p = b + 1;
                float lx = s.pivotX[b];
                float ly = s.pivotY[b] - s.torsoY;
                float lz = s.pivotZ[b];

                // Rest handle, in world space.
                offset.set(lx + BodySkeleton.restX(s.axis[b]) * s.length[b],
                        ly + BodySkeleton.restY(s.axis[b]) * s.length[b],
                        lz + BodySkeleton.restZ(s.axis[b]) * s.length[b]);
                frame.transformDirection(offset);
                float pull = RagdollConstants.JOINT_REST_PULL
                        / RagdollConstants.RELAX_ITERATIONS;
                r.px[p] += (r.px[Ragdoll.TORSO] + offset.x - r.px[p]) * pull;
                r.py[p] += (r.py[Ragdoll.TORSO] + offset.y - r.py[p]) * pull;
                r.pz[p] += (r.pz[Ragdoll.TORSO] + offset.z - r.pz[p]) * pull;

                // Pivot, in world space, then the exact bone length.
                offset.set(lx, ly, lz);
                frame.transformDirection(offset);
                float dx = r.px[p] - (r.px[Ragdoll.TORSO] + offset.x);
                float dy = r.py[p] - (r.py[Ragdoll.TORSO] + offset.y);
                float dz = r.pz[p] - (r.pz[Ragdoll.TORSO] + offset.z);
                float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (length < 1e-4f) {
                    continue;
                }
                float correction = (length - s.length[b]) / length;
                float cx = dx * correction;
                float cy = dy * correction;
                float cz = dz * correction;
                float keep = 1f - RagdollConstants.TORSO_REACTION;
                r.px[p] -= cx * keep;
                r.py[p] -= cy * keep;
                r.pz[p] -= cz * keep;
                r.px[Ragdoll.TORSO] += cx * RagdollConstants.TORSO_REACTION;
                r.py[Ragdoll.TORSO] += cy * RagdollConstants.TORSO_REACTION;
                r.pz[Ragdoll.TORSO] += cz * RagdollConstants.TORSO_REACTION;

                // Torque = lever x force, both in the body's own frame.
                local.set(cx, cy, cz);
                inverse.transformDirection(local);
                r.pitchVel += clampTorque(RagdollConstants.TORQUE_GAIN
                        * (ly * local.z - lz * local.y));
                r.rollVel += clampTorque(RagdollConstants.TORQUE_GAIN
                        * (lx * local.y - ly * local.x));
            }
        }
    }

    private static float clampTorque(float t) {
        return t > 0.6f ? 0.6f : (t < -0.6f ? -0.6f : t);
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
            if (resting[p]) {
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
            linear += r.vx[p] * r.vx[p] + r.vy[p] * r.vy[p] + r.vz[p] * r.vz[p];
        }
        float pitchError = shortest(r.restPitch - r.pitch);
        float rollError = shortest(r.restRoll - r.roll);
        return linear / r.pointCount
                + r.yawVel * r.yawVel + r.pitchVel * r.pitchVel + r.rollVel * r.rollVel
                + pitchError * pitchError + rollError * rollError;
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
    // Pose
    // ------------------------------------------------------------------

    private void buildFrame(Ragdoll r) {
        frame.identity().rotateY(r.yaw).rotateX(r.pitch).rotateZ(r.roll);
        inverse.identity().rotateZ(-r.roll).rotateX(-r.pitch).rotateY(-r.yaw);
    }

    /**
     * Turns the point cloud back into model angles.
     *
     * <p>A bone is aimed with two rotations and no yaw of its own, which is
     * enough to point it anywhere: composing {@code rotateZ(c)} with
     * {@code rotateX(a)} sweeps the whole sphere, so the aim is exact rather
     * than approximated. Measuring both the target and the bone's authored rest
     * axis the same way means a bone that has not moved reads as zero and keeps
     * whatever pose its model builder gave it.
     */
    private void writePose(Ragdoll r) {
        BodySkeleton s = r.skeleton;
        r.pose.yaw = r.yaw;
        r.pose.pitch = r.pitch;
        r.pose.roll = r.roll;
        r.pose.pivotY = s.torsoY;
        r.pose.boneCount = s.boneCount;
        r.pose.solved = true;
        if (s.boneCount == 0) {
            return;
        }
        buildFrame(r);
        for (int b = 0; b < s.boneCount; b++) {
            int p = b + 1;
            offset.set(s.pivotX[b], s.pivotY[b] - s.torsoY, s.pivotZ[b]);
            frame.transformDirection(offset);
            local.set(r.px[p] - (r.px[Ragdoll.TORSO] + offset.x),
                    r.py[p] - (r.py[Ragdoll.TORSO] + offset.y),
                    r.pz[p] - (r.pz[Ragdoll.TORSO] + offset.z));
            float length = local.length();
            if (length < 1e-4f) {
                continue;
            }
            local.div(length);
            inverse.transformDirection(local);

            float dz = Math.max(-1f, Math.min(1f, local.z));
            float aim = (float) -Math.asin(dz);
            float horizontal = (float) Math.sqrt(Math.max(0f, 1f - dz * dz));
            r.pose.boneRotX[b] = wrap(aim - BodySkeleton.restAngle(s.axis[b]));
            if (horizontal > 1e-3f) {
                r.pose.boneRotZ[b] = (float) Math.atan2(local.x, -local.y);
            }
        }
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
        float x = r.px[Ragdoll.TORSO];
        float y = r.py[Ragdoll.TORSO];
        float z = r.pz[Ragdoll.TORSO];
        float half = r.skeleton.torsoRadius;
        for (int guard = 0; guard < 24
                && collision.blocked(g.world, x, y, z, half, half); guard++) {
            y += 0.2f;
        }
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
            lowest = Math.min(lowest, r.py[p] - RagdollConstants.POINT_RADIUS);
        }
        return lowest;
    }

    public int liveCount() {
        return live.size();
    }
}
