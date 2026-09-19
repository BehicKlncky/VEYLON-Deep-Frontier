package com.veylon.entity;

import com.veylon.Game;
import com.veylon.entity.BodyFragment.Piece;
import com.veylon.simulation.SimulationSystem;
import com.veylon.world.World;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Rigid-body flight for a person blown apart at the joints.
 *
 * <p>{@link #spawnFromNpc} turns a dying {@link Npc} into ten independent
 * {@link BodyFragment}s placed exactly where the living model drew them, each
 * thrown away from the blast in inverse proportion to its mass and spun about
 * the body's mass centre. The step then mirrors {@link RagdollSystem}: a fixed
 * 1/60 s step drained from a clamped accumulator, the same gravity, water, drag
 * and friction numbers, and the same voxel sweep, so a severed limb falls
 * exactly like the body it came from.
 *
 * <p>Each piece sweeps the world-axis box of its <em>turned</em> collision box,
 * so a limb that lands lying down rests on its lowest corner instead of
 * hovering at its standing height or sinking into the floor. Turning is
 * refused when the bigger box would not fit, which is how a wall stops a piece
 * rotating into it, and a grounded piece feels gravity's torque about its
 * lowest corner, which is what tips it off an edge onto a face. Nothing else
 * steers it.
 *
 * <p>No generator: scatter and spin that the blast does not decide come from a
 * hash of the piece's position, the way {@code RagdollSystem.positionBias}
 * picks a side. Blood is presentation only and uses the already-seeded
 * {@code ParticleSystem}. All scratch is preallocated; only spawning and
 * settling (which may grow a list) allocate, never a step.
 */
public class BodyFragmentSystem implements SimulationSystem {

    /** Push-out probe order: up first (a buried foot, a closed-in floor), then sideways. */
    private static final float[] PUSH_X = {0, 1, -1, 0, 0};
    private static final float[] PUSH_Y = {1, 0, 0, 0, 0};
    private static final float[] PUSH_Z = {0, 0, 0, 1, -1};

    /** Pieces in flight, oldest first. */
    public final List<BodyFragment> live = new ArrayList<>();
    /** Pieces at rest, oldest first. */
    public final List<BodyFragment> settled = new ArrayList<>();

    private final RagdollCollision collision = new RagdollCollision();
    private final Matrix3f basis = new Matrix3f();
    private final Quaternionf previous = new Quaternionf();

    private double accumulator;

    /** Diagnostics for the debug overlay and the QA scene. */
    public long totalSpawned;
    public long totalSettled;
    public int stepsLastUpdate;

    @Override
    public void reset() {
        live.clear();
        settled.clear();
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
     * Blows a person apart at the joints.
     *
     * <p>Each piece starts where the standing model drew it, turned to the
     * person's heading, and leaves with the person's own velocity plus
     * {@code dir × IMPULSE_BASE × strength × falloff × inverseMass}, where
     * {@code dir} points from the blast centre to the piece and
     * {@code falloff = clamp(1 − d / (strength × FALLOFF_RANGE), MIN_FALLOFF, 1)},
     * plus {@link BodyFragmentConstants#UPWARD_BIAS} straight up.
     *
     * @param strength blast strength; an explosion's power
     * @return the ten new pieces, torso first
     */
    public List<BodyFragment> spawnFromNpc(Game g, Npc n,
                                           float blastX, float blastY, float blastZ,
                                           float strength) {
        Piece[] pieces = Piece.values();
        List<BodyFragment> spawned = new ArrayList<>(pieces.length);
        float yaw = (float) Math.toRadians(-n.yaw);
        float power = strength > 0 && Float.isFinite(strength) ? strength : 0;
        float reach = Math.max(1e-3f, power * BodyFragmentConstants.FALLOFF_RANGE);

        // The body's mass centre, which the blast spins every piece about.
        Vector3f massCentre = new Vector3f();
        float totalMass = 0;
        for (Piece p : pieces) {
            massCentre.add(p.centreX * p.mass, p.centreY * p.mass, p.centreZ * p.mass);
            totalMass += p.mass;
        }
        massCentre.div(totalMass);
        new Quaternionf().rotationY(yaw).transform(massCentre).add(n.pos);

        Vector3f dir = new Vector3f();
        Vector3f lever = new Vector3f();
        Vector3f tangent = new Vector3f();
        Vector3f bitangent = new Vector3f();
        Vector3f joint = new Vector3f();
        for (Piece p : pieces) {
            BodyFragment f = new BodyFragment(p);
            f.appearance.capture(n);
            f.orientation.rotationY(yaw);
            f.orientation.transform(p.centreX, p.centreY, p.centreZ, f.pos).add(n.pos);
            fitExtents(f);
            pushFree(g.world, f);

            dir.set(f.pos).sub(blastX, blastY, blastZ);
            float distance = dir.length();
            if (distance < BodyFragmentConstants.DEGENERATE_DISTANCE || !Float.isFinite(distance)) {
                dir.set(0, 1, 0);
                distance = 0;
            } else {
                dir.div(distance);
            }
            float falloff = Math.clamp(1f - distance / reach, BodyFragmentConstants.MIN_FALLOFF, 1f);
            float blastSpeed = BodyFragmentConstants.IMPULSE_BASE * power * falloff * f.inverseMass;

            // Scatter strictly across the blast direction, so it can spread the
            // pieces but never turn one back towards the blast.
            tangent.set(dir).cross(0, 1, 0);
            if (tangent.lengthSquared() < 1e-6f) {
                tangent.set(dir).cross(1, 0, 0);
            }
            tangent.normalize();
            bitangent.set(dir).cross(tangent);
            int salt = p.ordinal() * 8;
            float scatter = blastSpeed * BodyFragmentConstants.TANGENT_JITTER;
            f.vel.set(n.vel)
                    .fma(blastSpeed, dir)
                    .fma(scatter * hash(f.pos, salt), tangent)
                    .fma(scatter * hash(f.pos, salt + 1), bitangent)
                    .add(0, BodyFragmentConstants.UPWARD_BIAS, 0);
            clampLength(f.vel, RagdollConstants.MAX_POINT_SPEED);

            lever.set(f.pos).sub(massCentre).cross(dir).mul(BodyFragmentConstants.SPIN_GAIN);
            f.angularVelocity.set(lever).add(
                    BodyFragmentConstants.SPIN_JITTER * hash(f.pos, salt + 2),
                    BodyFragmentConstants.SPIN_JITTER * hash(f.pos, salt + 3),
                    BodyFragmentConstants.SPIN_JITTER * hash(f.pos, salt + 4));
            clampLength(f.angularVelocity, RagdollConstants.MAX_ANGULAR_SPEED);
            // Stagger the drips so ten pieces do not shed in lockstep.
            f.dripTimer = RagdollConstants.DRIP_INTERVAL * p.ordinal() / pieces.length;

            if (live.size() >= BodyFragmentConstants.MAX_LIVE_FRAGMENTS) {
                settleOldest(g);
            }
            live.add(f);
            spawned.add(f);
            totalSpawned++;

            if (p.severed) {
                f.modelToWorld(p.pivotX, p.pivotY, p.pivotZ, joint);
                float speed = f.vel.length();
                if (speed > 1e-4f) {
                    g.particles.bloodBurst(joint.x, joint.y, joint.z, f.vel.x / speed,
                            f.vel.y / speed, f.vel.z / speed, BodyFragmentConstants.JOINT_BURST_POWER);
                } else {
                    g.particles.bloodBurst(joint.x, joint.y, joint.z, 0, 1, 0,
                            BodyFragmentConstants.JOINT_BURST_POWER);
                }
            }
        }
        return spawned;
    }

    /** Deterministic value in [-1, 1) from a position; stands in for a generator. */
    static float hash(Vector3f at, int salt) {
        int h = Float.floatToIntBits(at.x) * 31
                + Float.floatToIntBits(at.y) * 17
                + Float.floatToIntBits(at.z) * 13
                + salt * 0x9E3779B9;
        h ^= h >>> 16;
        h *= 0x7FEB352D;
        h ^= h >>> 15;
        h *= 0x846CA68B;
        h ^= h >>> 16;
        return (h >>> 8) * (2f / (1 << 24)) - 1f;
    }

    private static void clampLength(Vector3f v, float max) {
        float sq = v.lengthSquared();
        if (sq > max * max) {
            v.mul(max / (float) Math.sqrt(sq));
        }
    }

    // ------------------------------------------------------------------
    // Per-frame simulation
    // ------------------------------------------------------------------

    /**
     * Drains accumulated frame time in whole fixed steps. Called from the
     * per-frame bucket inside the simulate gate, beside the ragdolls, so a
     * paused game never advances a piece.
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
                BodyFragment f = live.get(i);
                step(g, f, RagdollConstants.FIXED_STEP);
                if (f.settled) {
                    live.remove(i);
                    rest(g, f);
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

    private void step(Game g, BodyFragment f, float dt) {
        World world = g.world;
        f.age = Math.min(BodyFragmentConstants.SETTLE_TIMEOUT, f.age + dt);
        // A world edit can close around a piece mid-flight.
        if (collision.blocked(world, f.pos.x, f.pos.y, f.pos.z, f.halfWidth, f.halfHeight)) {
            pushFree(world, f);
        }
        spin(f, dt);
        turn(world, f, dt);
        fly(world, f, dt);
        drip(g, f, dt);

        f.energy = f.vel.lengthSquared() + f.angularVelocity.lengthSquared();
        f.quietSteps = f.grounded && f.energy < BodyFragmentConstants.SETTLE_ENERGY
                ? f.quietSteps + 1 : 0;
        boolean tooFar = g.player != null && f.distSqTo(g.player.pos.x, g.player.pos.y,
                g.player.pos.z) > RagdollConstants.DESPAWN_DISTANCE * RagdollConstants.DESPAWN_DISTANCE;
        if (f.quietSteps >= BodyFragmentConstants.SETTLE_STEPS
                || f.age >= BodyFragmentConstants.SETTLE_TIMEOUT
                || tooFar) {
            f.settled = true;
        }
    }

    /**
     * Angular damping, plus gravity's torque about the lowest corner while the
     * piece is on the ground. With the box's axes as the columns {@code c_j}
     * and half sizes {@code h_j}, the centre sits at
     * {@code w = Σ sign(c_j.y) h_j c_j} from that corner, and the weight there
     * turns it at {@code g × (w.z, 0, −w.x) / inertia}. The sign is softened
     * inside {@link BodyFragmentConstants#FLAT_BAND}, where the piece rests on
     * an edge or a face and has no single lowest corner to pivot on.
     */
    private void spin(BodyFragment f, float dt) {
        Vector3f omega = f.angularVelocity;
        if (!f.grounded) {
            omega.mul(1f - Math.min(1f, BodyFragmentConstants.AIR_ANGULAR_DAMPING * dt));
            return;
        }
        f.orientation.get(basis);
        float sx = side(basis.m01), sy = side(basis.m11), sz = side(basis.m21);
        float leverX = sx * f.halfX * basis.m00 + sy * f.halfY * basis.m10 + sz * f.halfZ * basis.m20;
        float leverZ = sx * f.halfX * basis.m02 + sy * f.halfY * basis.m12 + sz * f.halfZ * basis.m22;
        float inertia = BodyFragmentConstants.TOPPLE_INERTIA
                * (f.halfX * f.halfX + f.halfY * f.halfY + f.halfZ * f.halfZ);
        float gain = RagdollConstants.GRAVITY_AIR * dt / inertia;
        omega.add(leverZ * gain, 0, -leverX * gain);
        clampLength(omega, RagdollConstants.MAX_ANGULAR_SPEED);
    }

    private static float side(float up) {
        return Math.clamp(up / BodyFragmentConstants.FLAT_BAND, -1f, 1f);
    }

    /**
     * Turns the piece by its world-axis angular velocity and refits the sweep
     * box. A box that grew into the face it rests on is lifted by the growth;
     * one that still does not fit, against a wall or under a ceiling, is not
     * allowed to turn and its spin rebounds like a velocity on a face.
     */
    private void turn(World world, BodyFragment f, float dt) {
        Vector3f w = f.angularVelocity;
        float speed = w.length();
        if (speed < 1e-6f) {
            return;
        }
        float half = 0.5f * speed * dt;
        float s = (float) Math.sin(half) / speed;
        previous.set(f.orientation);
        float oldWidth = f.halfWidth;
        float oldHeight = f.halfHeight;
        f.orientation.premul(w.x * s, w.y * s, w.z * s, (float) Math.cos(half)).normalize();
        fitExtents(f);
        if (!collision.blocked(world, f.pos.x, f.pos.y, f.pos.z, f.halfWidth, f.halfHeight)) {
            return;
        }
        float lift = f.halfHeight - oldHeight;
        if (lift > 0 && !collision.blocked(world, f.pos.x, f.pos.y + lift + BodyFragmentConstants.LIFT_SKIN,
                f.pos.z, f.halfWidth, f.halfHeight)) {
            f.pos.y += lift + BodyFragmentConstants.LIFT_SKIN;
            return;
        }
        f.orientation.set(previous);
        f.halfWidth = oldWidth;
        f.halfHeight = oldHeight;
        w.mul(-BodyFragmentConstants.BOUNCE_FRAGMENT);
    }

    /** World-axis half extents of the turned box, horizontal ones merged. */
    private void fitExtents(BodyFragment f) {
        f.orientation.get(basis);
        float ex = Math.abs(basis.m00) * f.halfX + Math.abs(basis.m10) * f.halfY
                + Math.abs(basis.m20) * f.halfZ;
        float ey = Math.abs(basis.m01) * f.halfX + Math.abs(basis.m11) * f.halfY
                + Math.abs(basis.m21) * f.halfZ;
        float ez = Math.abs(basis.m02) * f.halfX + Math.abs(basis.m12) * f.halfY
                + Math.abs(basis.m22) * f.halfZ;
        f.halfWidth = Math.max(BodyFragmentConstants.MIN_HALF_EXTENT, Math.max(ex, ez));
        f.halfHeight = Math.max(BodyFragmentConstants.MIN_HALF_EXTENT, ey);
    }

    private void fly(World world, BodyFragment f, float dt) {
        Vector3f v = f.vel;
        boolean water = collision.inWater(world, f.pos.x, f.pos.y, f.pos.z);
        v.y -= (water ? RagdollConstants.GRAVITY_WATER : RagdollConstants.GRAVITY_AIR) * dt;
        if (water) {
            v.y = Math.max(v.y, RagdollConstants.WATER_MAX_DESCENT);
        }
        v.mul(1f - RagdollConstants.AIR_DRAG);
        clampLength(v, RagdollConstants.MAX_POINT_SPEED);

        // Water halves horizontal travel rather than velocity, as for ragdolls.
        float drag = water ? RagdollConstants.WATER_DRAG : 1f;
        collision.move(world, f.pos.x, f.pos.y, f.pos.z,
                v.x * dt * drag, v.y * dt, v.z * dt * drag, f.halfWidth, f.halfHeight);
        f.pos.set(collision.x, collision.y, collision.z);
        f.grounded = collision.grounded;
        if (collision.hitX) {
            v.x = -v.x * BodyFragmentConstants.BOUNCE_FRAGMENT;
        }
        if (collision.hitZ) {
            v.z = -v.z * BodyFragmentConstants.BOUNCE_FRAGMENT;
        }
        if (collision.hitY) {
            boolean landing = v.y < 0;
            v.y = -v.y * BodyFragmentConstants.BOUNCE_FRAGMENT;
            if (landing) {
                float keep = 1f - RagdollConstants.GROUND_FRICTION;
                v.x *= keep;
                v.z *= keep;
                f.angularVelocity.mul(1f - Math.min(1f,
                        BodyFragmentConstants.GROUND_ANGULAR_DAMPING * dt));
            }
        }
    }

    private void drip(Game g, BodyFragment f, float dt) {
        f.dripTimer -= dt;
        if (f.dripTimer > 0
                || f.vel.lengthSquared() < RagdollConstants.DRIP_SPEED * RagdollConstants.DRIP_SPEED) {
            return;
        }
        f.dripTimer = RagdollConstants.DRIP_INTERVAL;
        g.particles.bloodDrip(f.pos.x, f.pos.y, f.pos.z, f.vel.x, f.vel.y, f.vel.z);
    }

    /**
     * Moves a piece that overlaps geometry to the nearest free spot, probing
     * upward first and then sideways at growing distances. Bounded; a piece
     * that cannot be freed (inside ungenerated space, say) stays put and
     * settles where it is.
     */
    private boolean pushFree(World world, BodyFragment f) {
        if (!collision.blocked(world, f.pos.x, f.pos.y, f.pos.z, f.halfWidth, f.halfHeight)) {
            return true;
        }
        // The first probe is a hair, for a foot rounding left microns deep.
        for (int i = 0; i <= BodyFragmentConstants.PUSH_FREE_STEPS; i++) {
            float d = i == 0 ? BodyFragmentConstants.PUSH_FREE_SKIN
                    : i * BodyFragmentConstants.PUSH_FREE_STEP;
            for (int k = 0; k < PUSH_X.length; k++) {
                float x = f.pos.x + PUSH_X[k] * d;
                float y = f.pos.y + PUSH_Y[k] * d;
                float z = f.pos.z + PUSH_Z[k] * d;
                if (!collision.blocked(world, x, y, z, f.halfWidth, f.halfHeight)) {
                    f.pos.set(x, y, z);
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Settling, decay and culling
    // ------------------------------------------------------------------

    /** Freezes every live piece at once. Used by the save path. */
    public void settleAll(Game g) {
        for (int i = 0; i < live.size(); i++) {
            BodyFragment f = live.get(i);
            f.settled = true;
            rest(g, f);
        }
        live.clear();
    }

    /**
     * Lays a piece read from a save back down exactly as it was written: no
     * push-out and no drop, because {@link #rest} did both before the save.
     * Only the sweep box is derived, refit to the saved orientation. The
     * settled cap holds here as it does in {@link #rest}.
     */
    public void restoreSettled(BodyFragment f) {
        f.settled = true;
        fitExtents(f);
        if (settled.size() >= BodyFragmentConstants.MAX_SETTLED_FRAGMENTS) {
            settled.removeFirst();
        }
        settled.add(f);
    }

    private void settleOldest(Game g) {
        if (live.isEmpty()) {
            return;
        }
        BodyFragment f = live.removeFirst();
        f.settled = true;
        rest(g, f);
    }

    /**
     * Lays a piece down for good. It is pushed clear of anything a world edit
     * wrapped around it and swept down to whatever is below, so a piece frozen
     * in mid-air by the cap, a save or the timeout rests on the ground rather
     * than floating there until it rots.
     */
    private void rest(Game g, BodyFragment f) {
        totalSettled++;
        pushFree(g.world, f);
        collision.move(g.world, f.pos.x, f.pos.y, f.pos.z, 0,
                -BodyFragmentConstants.FORCED_SETTLE_DROP, 0, f.halfWidth, f.halfHeight);
        f.pos.set(collision.x, collision.y, collision.z);
        f.grounded = collision.grounded || f.grounded;
        f.vel.zero();
        f.angularVelocity.zero();
        f.decay = RagdollConstants.CORPSE_DECAY;
        if (settled.size() >= BodyFragmentConstants.MAX_SETTLED_FRAGMENTS) {
            settled.removeFirst();
        }
        settled.add(f);
    }

    /**
     * Rots settled pieces on the corpse clock and drops those past the radius
     * corpses despawn at. Called from the slow tick beside the corpse loop.
     */
    public void slowTick(Game g, float dt) {
        float far = RagdollConstants.DESPAWN_DISTANCE * RagdollConstants.DESPAWN_DISTANCE;
        for (int i = settled.size() - 1; i >= 0; i--) {
            BodyFragment f = settled.get(i);
            f.decay -= dt;
            boolean gone = f.decay <= 0;
            if (!gone && g.player != null) {
                float dx = f.pos.x - g.player.pos.x;
                float dz = f.pos.z - g.player.pos.z;
                gone = dx * dx + dz * dz > far;
            }
            if (gone) {
                settled.remove(i);
            }
        }
    }

    public int liveCount() {
        return live.size();
    }

    public int settledCount() {
        return settled.size();
    }
}
