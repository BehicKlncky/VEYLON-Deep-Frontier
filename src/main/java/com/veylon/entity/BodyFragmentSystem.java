package com.veylon.entity;

import com.veylon.Game;
import com.veylon.simulation.SimulationSystem;
import com.veylon.world.World;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Rigid-body flight for a body blown apart at the joints.
 *
 * <p>A dying person, animal or player becomes the pieces of its body family's
 * {@link FragmentAnatomy} — ten for a person, seven to twelve for an animal —
 * each placed exactly where the living model drew it in the pose it died in,
 * thrown away from the blast in inverse proportion to its mass (with a floor
 * on that mass, so a wing is not fired like a bullet) and spun about the
 * body's mass centre. The step then mirrors {@link RagdollSystem}: a fixed
 * 1/60 s step drained from a clamped accumulator, the same gravity, water, drag
 * and friction numbers, and the same voxel sweep, so a severed limb falls
 * exactly like the body it came from.
 *
 * <p>An animal that leaves a carcass leaves exactly one, created with its
 * pieces and tied to its torso ({@link Carcass#remains},
 * {@link BodyFragment#harvest}): the record lies where the torso lies, the
 * torso stays while the record does, and the record's rot takes the torso
 * with it.
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

    /**
     * Where the pose a body died in comes from. The models the living are
     * drawn with live outside this package, so {@code Game} supplies it;
     * without one every body comes apart in its rest pose.
     */
    public interface DeathPoses {
        /** The pose {@code c} is drawn in now. */
        FragmentPose of(Creature c);

        /** The pose {@code n} is drawn in now. */
        FragmentPose of(Npc n);
    }

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
    /** Wiring, not state: survives {@link #reset}. */
    private DeathPoses deathPoses;

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

    /** Supplies the pose bodies die in; see {@link DeathPoses}. */
    public void setDeathPoses(DeathPoses poses) {
        deathPoses = poses;
    }

    /** The pose {@code c} dies in: as last drawn, or its rest pose without a source. */
    public FragmentPose deathPose(Creature c) {
        return deathPoses != null ? deathPoses.of(c) : FragmentAnatomy.of(c.type).restPose();
    }

    /** The pose {@code n} dies in: as last drawn, or the standing rest pose without a source. */
    public FragmentPose deathPose(Npc n) {
        return deathPoses != null ? deathPoses.of(n) : FragmentAnatomy.humanoid().restPose();
    }

    /**
     * Blows a person apart at the joints in the standing rest pose, as a QA
     * scene or a fixture stages it. A real death goes through the pose the
     * person died in ({@link #deathPose(Npc)}).
     *
     * @param strength blast strength; an explosion's power
     * @return the ten new pieces, torso first
     */
    public List<BodyFragment> spawnFromNpc(Game g, Npc n,
                                           float blastX, float blastY, float blastZ,
                                           float strength) {
        return spawnFromNpc(g, n, FragmentAnatomy.humanoid().restPose(),
                blastX, blastY, blastZ, strength);
    }

    /**
     * Blows a person apart at the joints: the humanoid table's pieces in
     * {@code pose}, each carrying its own copy of the person's look.
     *
     * @return the ten new pieces, torso first
     * @throws IllegalArgumentException when {@code pose} is not a person's
     */
    public List<BodyFragment> spawnFromNpc(Game g, Npc n, FragmentPose pose,
                                           float blastX, float blastY, float blastZ,
                                           float strength) {
        requireFamily(pose, BodyFamily.HUMANOID);
        List<BodyFragment> spawned = launch(g, n, pose, blastX, blastY, blastZ, strength);
        for (int i = 0; i < spawned.size(); i++) {
            spawned.get(i).appearance.capture(n);
        }
        return spawned;
    }

    /**
     * Blows an animal apart at its own joints, in {@code pose}. An animal
     * that leaves a carcass leaves it now, exactly once: one record with the
     * species' whole meat and hide yield and every arrow lodged in the
     * animal, tied to the torso piece. The limbs carry nothing.
     *
     * @return the new pieces, torso first
     * @throws IllegalArgumentException when {@code pose} is not this species'
     */
    public List<BodyFragment> spawnFromCreature(Game g, Creature c, FragmentPose pose,
                                                float blastX, float blastY, float blastZ,
                                                float strength) {
        requireFamily(pose, BodyFamily.of(c.type));
        List<BodyFragment> spawned = launch(g, c, pose, blastX, blastY, blastZ, strength);
        if (c.type.leavesCarcass()) {
            anchorHarvest(g, c, spawned.getFirst());
        }
        return spawned;
    }

    /**
     * The player's remains after a lethal blast, spawned by the death
     * transition: the humanoid's pieces in the standing rest pose (the player
     * has no body model to take a pose from) with the neutral look, thrown
     * from the recorded blast. The pieces copy what they need and never refer
     * to the player, who lives on as the same object after respawning.
     *
     * <p>Spends the record, so the same death never comes apart twice. Does
     * nothing, and returns no pieces, for a player with no record, one who
     * is not really dead, or one who is invulnerable.
     */
    public List<BodyFragment> spawnPlayerRemains(Game g, Player p) {
        if (!p.dismemberOnDeath || !p.dead || p.health > 0 || p.abilities.invulnerable()) {
            return List.of();
        }
        List<BodyFragment> spawned = launch(g, p, FragmentAnatomy.humanoid().restPose(),
                p.blastX, p.blastY, p.blastZ, p.blastStrength);
        for (int i = 0; i < spawned.size(); i++) {
            spawned.get(i).appearance.setNeutral();
        }
        p.clearBlastDeath();
        return spawned;
    }

    private static void requireFamily(FragmentPose pose, BodyFamily family) {
        if (pose.anatomy.family != family) {
            throw new IllegalArgumentException("a " + pose.anatomy.family
                    + " pose cannot blow apart a " + family + " body");
        }
    }

    /**
     * Places every piece of {@code pose}'s body where the living model drew
     * it, turned to the body's heading, and throws it.
     *
     * <p>Each piece leaves with the body's own velocity plus
     * {@code dir × IMPULSE_BASE × strength × falloff / max(mass, MIN_LAUNCH_MASS)},
     * where {@code dir} points from the blast centre to the piece and
     * {@code falloff = clamp(1 − d / (strength × FALLOFF_RANGE), MIN_FALLOFF, 1)},
     * plus {@link BodyFragmentConstants#UPWARD_BIAS} straight up.
     */
    private List<BodyFragment> launch(Game g, Entity body, FragmentPose pose,
                                      float blastX, float blastY, float blastZ, float strength) {
        List<FragmentPiece> pieces = pose.anatomy.pieces;
        List<BodyFragment> spawned = new ArrayList<>(pieces.size());
        float yaw = (float) Math.toRadians(-body.yaw);
        float power = strength > 0 && Float.isFinite(strength) ? strength : 0;
        float reach = Math.max(1e-3f, power * BodyFragmentConstants.FALLOFF_RANGE);

        // The body's mass centre, which the blast spins every piece about.
        Vector3f massCentre = new Vector3f();
        Vector3f centre = new Vector3f();
        float totalMass = 0;
        for (FragmentPiece p : pieces) {
            pose.pieceCentre(p.id, centre);
            massCentre.add(centre.x * p.mass, centre.y * p.mass, centre.z * p.mass);
            totalMass += p.mass;
        }
        massCentre.div(totalMass);
        new Quaternionf().rotationY(yaw).transform(massCentre).add(body.pos);

        Vector3f dir = new Vector3f();
        Vector3f lever = new Vector3f();
        Vector3f tangent = new Vector3f();
        Vector3f bitangent = new Vector3f();
        Vector3f joint = new Vector3f();
        for (FragmentPiece p : pieces) {
            BodyFragment f = new BodyFragment(p, pose);
            f.placeAt(body.pos.x, body.pos.y, body.pos.z, yaw);
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
            float launchInverseMass = Math.min(f.inverseMass, 1f / BodyFragmentConstants.MIN_LAUNCH_MASS);
            float blastSpeed = BodyFragmentConstants.IMPULSE_BASE * power * falloff * launchInverseMass;

            // Scatter strictly across the blast direction, so it can spread the
            // pieces but never turn one back towards the blast.
            tangent.set(dir).cross(0, 1, 0);
            if (tangent.lengthSquared() < 1e-6f) {
                tangent.set(dir).cross(1, 0, 0);
            }
            tangent.normalize();
            bitangent.set(dir).cross(tangent);
            int salt = p.id * 8;
            float scatter = blastSpeed * BodyFragmentConstants.TANGENT_JITTER;
            f.vel.set(body.vel)
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
            f.dripTimer = RagdollConstants.DRIP_INTERVAL * p.id / pieces.size();

            if (live.size() >= BodyFragmentConstants.MAX_LIVE_FRAGMENTS) {
                settleOldest(g);
            }
            live.add(f);
            spawned.add(f);
            totalSpawned++;

            if (p.severed) {
                f.jointToWorld(p.rootJoint, joint);
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
    // The one harvest record of an animal blown apart
    // ------------------------------------------------------------------

    /**
     * Creates the animal's one carcass on its torso piece, first removing the
     * oldest record and its torso together if {@link
     * BodyFragmentConstants#MAX_ANCHORED_REMAINS} are already in the world.
     */
    private void anchorHarvest(Game g, Creature c, BodyFragment torso) {
        List<Carcass> carcasses = g.entities.carcasses;
        if (anchoredRemains(carcasses) >= BodyFragmentConstants.MAX_ANCHORED_REMAINS) {
            for (int i = 0; i < carcasses.size(); i++) {
                Carcass oldest = carcasses.get(i);
                if (oldest.fragmented()) {
                    carcasses.remove(i);
                    removePiece(oldest.remains);
                    break;
                }
            }
        }
        Carcass carcass = new Carcass(c.type, torso.pos.x, torso.pos.y - torso.halfHeight, torso.pos.z);
        carcass.stuckArrows = c.stuckArrows;
        carcass.stuckArrowType = c.stuckArrowType;
        carcass.remains = torso;
        torso.harvest = carcass;
        carcasses.add(carcass);
    }

    /**
     * Keeps a torso's harvest record where the torso lies: under its centre,
     * on whatever it rests on — the point a carcass drawn without a solved
     * pose stands on, and within reach wherever the torso can be seen.
     */
    private static void followTorso(BodyFragment torso) {
        torso.harvest.pos.set(torso.pos.x, torso.pos.y - torso.halfHeight, torso.pos.z);
    }

    /** Harvest records in {@code carcasses} that belong to remains rather than a whole body. */
    public static int anchoredRemains(List<Carcass> carcasses) {
        int count = 0;
        for (int i = 0; i < carcasses.size(); i++) {
            if (carcasses.get(i).fragmented()) {
                count++;
            }
        }
        return count;
    }

    private void removePiece(BodyFragment f) {
        f.harvest = null;
        if (!live.remove(f)) {
            settled.remove(f);
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
        if (f.harvest != null) {
            followTorso(f);
        }

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
        admitSettled(f);
    }

    /**
     * Adds a piece to the ground, first removing the oldest piece that
     * carries no harvest record when the settled cap is full.
     */
    private void admitSettled(BodyFragment f) {
        if (settled.size() >= BodyFragmentConstants.MAX_SETTLED_FRAGMENTS) {
            evictOldestSettled();
        }
        settled.add(f);
    }

    private void evictOldestSettled() {
        for (int i = 0; i < settled.size(); i++) {
            if (settled.get(i).harvest == null) {
                settled.remove(i);
                return;
            }
        }
        // Unreachable while MAX_ANCHORED_REMAINS < MAX_SETTLED_FRAGMENTS. Were
        // it reached, the record would stay and fall back to a whole carcass
        // rather than be lost or be left tied to nothing.
        BodyFragment oldest = settled.removeFirst();
        oldest.harvest.remains = null;
        oldest.harvest = null;
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
        if (f.harvest != null) {
            followTorso(f);
        }
        admitSettled(f);
    }

    /**
     * Rots settled pieces on the corpse clock and drops those past the radius
     * corpses despawn at. Called from the slow tick beside the corpse loop,
     * after the carcasses have rotted and been emptied.
     *
     * <p>A torso carrying a harvest record is neither rotted nor dropped by
     * distance while its record is in the world; it takes the record's rot
     * clock instead, so it looks exactly as rotten. When the record has left
     * the world the torso goes with it if the record rotted away, and is
     * otherwise released to rot as an ordinary piece (harvested empty, or
     * cleared by other code).
     */
    public void slowTick(Game g, float dt) {
        float far = RagdollConstants.DESPAWN_DISTANCE * RagdollConstants.DESPAWN_DISTANCE;
        for (int i = settled.size() - 1; i >= 0; i--) {
            BodyFragment f = settled.get(i);
            if (f.harvest != null) {
                Carcass record = f.harvest;
                if (g.entities.carcasses.contains(record)) {
                    f.decay = Math.min(f.decay, record.decay);
                    continue;
                }
                f.harvest = null;
                if (record.decay <= 0) {
                    settled.remove(i);
                    continue;
                }
            }
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
