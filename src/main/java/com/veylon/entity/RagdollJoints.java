package com.veylon.entity;

import com.veylon.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Bounded, allocation-free joint and contact projection. Endpoints retain inertia;
 * only a violated length, hinge plane or swing limit produces a correction.
 * Root contacts act at their lever arms, so support determines the resting attitude.
 */
final class RagdollJoints {
    private final RagdollCollision collision = new RagdollCollision();
    private final Vector3f v = new Vector3f(), rest = new Vector3f(), direction = new Vector3f();
    private final Vector3f lever = new Vector3f(), angles = new Vector3f();
    private final Quaternionf local = new Quaternionf(), delta = new Quaternionf();
    private final Quaternionf previousOrientation = new Quaternionf();
    private final float[] oldX = new float[BodySkeleton.MAX_BONES + 1];
    private final float[] oldY = new float[BodySkeleton.MAX_BONES + 1];
    private final float[] oldZ = new float[BodySkeleton.MAX_BONES + 1];
    private final float[] oldPivotX = new float[BodySkeleton.MAX_BONES];
    private final float[] oldPivotY = new float[BodySkeleton.MAX_BONES];
    private final float[] oldPivotZ = new float[BodySkeleton.MAX_BONES];
    final boolean[] contact = new boolean[BodySkeleton.MAX_BONES + 1];
    final boolean[] groundContact = new boolean[BodySkeleton.MAX_BONES + 1];

    void reset() { collision.reset(); }

    void initialize(Ragdoll r) {
        r.orientation.rotationY(r.yaw);
        forward(r);
        writePose(r);
    }

    void remember(Ragdoll r) {
        previousOrientation.set(r.orientation);
        System.arraycopy(r.px, 0, oldX, 0, r.pointCount);
        System.arraycopy(r.py, 0, oldY, 0, r.pointCount);
        System.arraycopy(r.pz, 0, oldZ, 0, r.pointCount);
        System.arraycopy(r.jointX, 0, oldPivotX, 0, r.skeleton.boneCount);
        System.arraycopy(r.jointY, 0, oldPivotY, 0, r.skeleton.boneCount);
        System.arraycopy(r.jointZ, 0, oldPivotZ, 0, r.skeleton.boneCount);
        java.util.Arrays.fill(contact, false);
        java.util.Arrays.fill(groundContact, false);
    }

    private Quaternionf parentFrame(Ragdoll r, int b) {
        int parent = r.skeleton.parent[b];
        return parent < 0 ? r.orientation : r.worldRotation[parent];
    }

    private void pivot(Ragdoll r, int b) {
        BodySkeleton s = r.skeleton;
        int parent = s.parent[b];
        v.set(s.pivotX[b], s.pivotY[b] - (parent < 0 ? s.torsoY : 0), s.pivotZ[b]);
        parentFrame(r, b).transform(v);
        r.jointX[b] = v.x + (parent < 0 ? r.px[0] : r.jointX[parent]);
        r.jointY[b] = v.y + (parent < 0 ? r.py[0] : r.jointY[parent]);
        r.jointZ[b] = v.z + (parent < 0 ? r.pz[0] : r.jointZ[parent]);
    }

    private void endpoint(Ragdoll r, int b) {
        BodySkeleton s = r.skeleton;
        parentFrame(r, b).mul(r.jointRotation[b], r.worldRotation[b]).normalize();
        v.set(s.restX[b], s.restY[b], s.restZ[b]).mul(s.length[b]);
        r.worldRotation[b].transform(v);
        r.px[b + 1] = r.jointX[b] + v.x;
        r.py[b + 1] = r.jointY[b] + v.y;
        r.pz[b + 1] = r.jointZ[b] + v.z;
    }

    void forward(Ragdoll r) {
        for (int b = 0; b < r.skeleton.boneCount; b++) {
            pivot(r, b);
            endpoint(r, b);
        }
    }

    void solve(World world, Ragdoll r) {
        for (int i = 0; i < RagdollConstants.RELAX_ITERATIONS; i++) {
            project(r, true);
            support(world, r);
            collideLimbs(world, r);
        }
        // Final parent-first projection gives the renderer exactly the solved
        // lengths and limits. Clearance translates the whole chain, never breaks it.
        project(r, false);
        clearWorld(world, r);
    }

    private void project(Ragdoll r, boolean reaction) {
        BodySkeleton s = r.skeleton;
        for (int b = 0; b < s.boneCount; b++) {
            pivot(r, b);
            int p = b + 1;
            float wantedX = r.px[p], wantedY = r.py[p], wantedZ = r.pz[p];
            direction.set(wantedX - r.jointX[b], wantedY - r.jointY[b], wantedZ - r.jointZ[b]);
            parentFrame(r, b).transformInverse(direction);
            rest.set(s.restX[b], s.restY[b], s.restZ[b]);
            if (direction.lengthSquared() < 1e-10f) direction.set(rest);
            else direction.normalize();
            if (s.joint[b] == BodySkeleton.Joint.HINGE) {
                float angle = (float) Math.atan2(rest.y * direction.z - rest.z * direction.y,
                        rest.y * direction.y + rest.z * direction.z);
                r.jointRotation[b].rotationX(Math.clamp(angle, s.minAngle[b], s.maxAngle[b]));
            } else {
                float cosine = Math.clamp(rest.dot(direction), -1f, 1f);
                float angle = (float) Math.acos(cosine);
                if (angle > s.maxAngle[b]) {
                    // The antipodal case needs a deterministic perpendicular.
                    direction.fma(-cosine, rest);
                    if (direction.lengthSquared() < 1e-10f) {
                        direction.set(Math.abs(rest.x) < 0.8f ? 1 : 0, 0, Math.abs(rest.x) < 0.8f ? 0 : 1);
                        direction.fma(-direction.dot(rest), rest);
                    }
                    direction.normalize().mul((float) Math.sin(s.maxAngle[b]))
                            .fma((float) Math.cos(s.maxAngle[b]), rest);
                }
                r.jointRotation[b].rotationTo(rest, direction).normalize();
            }
            endpoint(r, b);
            if (reaction) {
                float cx = wantedX - r.px[p], cy = wantedY - r.py[p], cz = wantedZ - r.pz[p];
                int parent = s.parent[b] + 1;
                float weight = parent == 0 ? RagdollConstants.ROOT_INVERSE_MASS : RagdollConstants.JOINT_REACTION;
                r.px[parent] += cx * weight;
                r.py[parent] += cy * weight;
                r.pz[parent] += cz * weight;
                if (parent == 0) {
                    lever.set(r.jointX[b] - r.px[0], r.jointY[b] - r.py[0], r.jointZ[b] - r.pz[0]);
                    direction.set(cx, cy, cz).mul(weight);
                    lever.cross(direction, v).mul(RagdollConstants.SUPPORT_INERTIA);
                    delta.set(v.x * 0.5f, v.y * 0.5f, v.z * 0.5f, 1).normalize();
                    delta.mul(r.orientation, r.orientation).normalize();
                }
            }
        }
    }

    private void support(World world, Ragdoll r) {
        BodySkeleton s = r.skeleton;
        // Eight corners, six face centres and the centre catch terrain and world edits.
        for (int i = 0; i < 15; i++) {
            supportOffset(s, i, lever);
            v.set(lever);
            previousOrientation.transform(v);
            float ox = oldX[0] + v.x, oy = oldY[0] + v.y, oz = oldZ[0] + v.z;
            r.orientation.transform(lever);
            float x = r.px[0] + lever.x, y = r.py[0] + lever.y, z = r.pz[0] + lever.z;
            sample(world, ox, oy, oz, x, y, z, RagdollConstants.CONTACT_SKIN);
            float cx = collision.x - x, cy = collision.y - y, cz = collision.z - z;
            if (cx * cx + cy * cy + cz * cz < 1e-12f) continue;
            contact[0] = true;
            groundContact[0] |= cy > 0;
            r.grounded |= cy > 0;
            // Effective mass includes rotation about the actual contact lever.
            direction.set(cx, cy, cz);
            float length = direction.length();
            direction.div(length);
            lever.cross(direction, v);
            float gain = RagdollConstants.SUPPORT_INERTIA;
            float correction = length / (1 + gain * v.lengthSquared());
            r.px[0] += direction.x * correction;
            r.py[0] += direction.y * correction;
            r.pz[0] += direction.z * correction;
            v.mul(correction * gain);
            delta.set(v.x * 0.5f, v.y * 0.5f, v.z * 0.5f, 1).normalize();
            delta.mul(r.orientation, r.orientation).normalize();
        }
    }

    private static void supportOffset(BodySkeleton s, int i, Vector3f out) {
        if (i == 14) { out.zero(); return; }
        if (i < 8) {
            out.set((i & 1) == 0 ? -s.halfX : s.halfX,
                    (i & 2) == 0 ? -s.halfY : s.halfY, (i & 4) == 0 ? -s.halfZ : s.halfZ);
        } else {
            int a = (i - 8) / 2;
            float sign = (i & 1) == 0 ? -1 : 1;
            out.set(a == 0 ? sign * s.halfX : 0, a == 1 ? sign * s.halfY : 0,
                    a == 2 ? sign * s.halfZ : 0);
        }
    }

    private void sample(World world, float ox, float oy, float oz,
                        float x, float y, float z, float radius) {
        collision.move(world, ox, oy, oz, x - ox, y - oy, z - oz, radius, radius);
        collision.project(world, collision.x, collision.y, collision.z, radius);
        // Coulomb friction at the actual contact point also resists rotation.
        // Velocity damping alone cannot stop tangential drift introduced by PBD.
        float nx = collision.x - x, ny = collision.y - y, nz = collision.z - z;
        float normal = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (normal > 1e-6f) {
            nx /= normal; ny /= normal; nz /= normal;
            float dx = x - ox, dy = y - oy, dz = z - oz;
            float dot = dx * nx + dy * ny + dz * nz;
            dx -= dot * nx; dy -= dot * ny; dz -= dot * nz;
            float tangent = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (tangent > 1e-6f) {
                float friction = Math.min(1, RagdollConstants.CONTACT_FRICTION * normal / tangent);
                collision.project(world, collision.x - dx * friction, collision.y - dy * friction,
                        collision.z - dz * friction, radius);
            }
        }
    }

    private void collideLimbs(World world, Ragdoll r) {
        BodySkeleton s = r.skeleton;
        for (int b = 0; b < s.boneCount; b++) {
            int p = b + 1;
            int samples = samples(s, b);
            for (int j = 1; j <= samples; j++) {
                float t = j / (float) samples;
                float x = lerp(r.jointX[b], r.px[p], t);
                float y = lerp(r.jointY[b], r.py[p], t);
                float z = lerp(r.jointZ[b], r.pz[p], t);
                sample(world, lerp(oldPivotX[b], oldX[p], t), lerp(oldPivotY[b], oldY[p], t),
                        lerp(oldPivotZ[b], oldZ[p], t), x, y, z, s.radius[b]);
                float cx = collision.x - x, cy = collision.y - y, cz = collision.z - z;
                if (cx * cx + cy * cy + cz * cz > 1e-12f) {
                    contact[p] = true;
                    groundContact[p] |= cy > 0;
                    r.grounded |= cy > 0;
                    moveSegment(r, b, t, cx, cy, cz);
                }
                // Preserve the authored socket overlap. A fixed exemption fraction
                // creates unsatisfiable contacts for short, inset legs (the hare).
                if (t <= s.selfCollisionStart[b] || s.part[b].equals("neck") || s.part[b].equals("head")
                        || s.part[b].equals("head_joint")) continue;
                v.set(x - r.px[0], y - r.py[0], z - r.pz[0]);
                r.orientation.transformInverse(v);
                float hx = s.halfX + s.radius[b], hy = s.halfY + s.radius[b], hz = s.halfZ + s.radius[b];
                if (Math.abs(v.x) >= hx || Math.abs(v.y) >= hy || Math.abs(v.z) >= hz) continue;
                float dx = hx - Math.abs(v.x), dy = hy - Math.abs(v.y), dz = hz - Math.abs(v.z);
                direction.zero();
                if (dx <= dy && dx <= dz) direction.x = Math.copySign(dx, v.x);
                else if (dy <= dz) direction.y = Math.copySign(dy, v.y);
                else direction.z = Math.copySign(dz, v.z);
                r.orientation.transform(direction);
                moveSegment(r, b, t, direction.x, direction.y, direction.z);
                // Self contact is internal: apply the opposite impulse to the
                // torso, otherwise an overlapping shoulder propels the whole body.
                int parent = s.parent[b] + 1;
                float mass = parent == 0 ? RagdollConstants.ROOT_INVERSE_MASS : 1;
                float reaction = RagdollConstants.ROOT_INVERSE_MASS
                        / (t * t + (1 - t) * (1 - t) * mass);
                r.px[0] -= direction.x * reaction;
                r.py[0] -= direction.y * reaction;
                r.pz[0] -= direction.z * reaction;
            }
        }
    }

    private static void moveSegment(Ragdoll r, int b, float t, float x, float y, float z) {
        int parent = r.skeleton.parent[b] + 1, p = b + 1;
        float parentWeight = parent == 0 ? RagdollConstants.ROOT_INVERSE_MASS : 1;
        float divisor = t * t + (1 - t) * (1 - t) * parentWeight;
        float end = t / divisor, start = (1 - t) * parentWeight / divisor;
        r.px[p] += x * end; r.py[p] += y * end; r.pz[p] += z * end;
        r.px[parent] += x * start; r.py[parent] += y * start; r.pz[parent] += z * start;
    }

    private static int samples(BodySkeleton s, int b) {
        return Math.max(2, (int) Math.ceil(s.length[b] /
                Math.min(RagdollConstants.CONTACT_SPACING, s.radius[b] * 1.8f)));
    }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    /**
     * Contacts and angular limits can conflict at a voxel corner. Finish with a
     * rigid translation of the projected chain; this preserves every joint and
     * ensures even a timed-out/save-frozen body has no sample in solid terrain.
     */
    void clearWorld(World world, Ragdoll r) {
        if (!collision.loaded(world, r.px[0], r.pz[0])) return;
        for (int pass = 0; pass < 12; pass++) {
            boolean moved = false;
            for (int b = -1; b < r.skeleton.boneCount; b++) {
                int count = b < 0 ? 15 : samples(r.skeleton, b);
                for (int j = 0; j < (b < 0 ? count : count + 1); j++) {
                    float radius;
                    if (b < 0) {
                        supportOffset(r.skeleton, j, v);
                        r.orientation.transform(v);
                        v.add(r.px[0], r.py[0], r.pz[0]);
                        radius = RagdollConstants.CONTACT_SKIN;
                    } else {
                        float t = j / (float) count;
                        v.set(lerp(r.jointX[b], r.px[b + 1], t), lerp(r.jointY[b], r.py[b + 1], t),
                                lerp(r.jointZ[b], r.pz[b + 1], t));
                        radius = r.skeleton.radius[b];
                    }
                    collision.project(world, v.x, v.y, v.z, radius);
                    float cx = collision.x - v.x, cy = collision.y - v.y, cz = collision.z - v.z;
                    if (collision.blocked(world, v.x, v.y - RagdollConstants.SUPPORT_SLOP,
                            v.z, radius, radius)) {
                        r.grounded = true;
                        contact[b + 1] = true;
                        groundContact[b + 1] = true;
                    }
                    if (cx * cx + cy * cy + cz * cz < 1e-12f) continue;
                    translate(r, cx, cy, cz);
                    moved = true;
                    r.grounded |= cy > 0;
                }
            }
            if (!moved) return;
        }
        // Only world edits can enclose an already valid chain. Internal voxel
        // faces then have no local solution: search upward, bounded by world height.
        for (int attempt = 0; attempt < com.veylon.world.Chunk.SY * 4 + 16; attempt++) {
            if (!overlaps(world, r)) return;
            translate(r, 0, 0.25f, 0);
        }
    }

    private boolean overlaps(World world, Ragdoll r) {
        for (int b = -1; b < r.skeleton.boneCount; b++) {
            int count = b < 0 ? 15 : samples(r.skeleton, b);
            for (int j = 0; j <= count; j++) {
                float radius;
                if (b < 0) {
                    if (j == count) break;
                    supportOffset(r.skeleton, j, v);
                    r.orientation.transform(v);
                    v.add(r.px[0], r.py[0], r.pz[0]);
                    radius = RagdollConstants.CONTACT_SKIN;
                } else {
                    float t = j / (float) count;
                    v.set(lerp(r.jointX[b], r.px[b + 1], t), lerp(r.jointY[b], r.py[b + 1], t),
                            lerp(r.jointZ[b], r.pz[b + 1], t));
                    radius = r.skeleton.radius[b];
                }
                if (collision.loaded(world, v.x, v.z)
                        && collision.blocked(world, v.x, v.y, v.z, radius, radius)) return true;
            }
        }
        return false;
    }

    private static void translate(Ragdoll r, float x, float y, float z) {
        for (int p = 0; p < r.pointCount; p++) {
            r.px[p] += x; r.py[p] += y; r.pz[p] += z;
        }
        for (int b = 0; b < r.skeleton.boneCount; b++) {
            r.jointX[b] += x; r.jointY[b] += y; r.jointZ[b] += z;
        }
    }

    void deriveAngularVelocity(Ragdoll r, float dt) {
        previousOrientation.conjugate(local);
        r.orientation.mul(local, delta).normalize();
        if (delta.w < 0) delta.set(-delta.x, -delta.y, -delta.z, -delta.w);
        float scale = 2 / dt;
        r.pitchVel = Math.clamp(delta.x * scale, -RagdollConstants.MAX_ANGULAR_SPEED, RagdollConstants.MAX_ANGULAR_SPEED);
        r.yawVel = Math.clamp(delta.y * scale, -RagdollConstants.MAX_ANGULAR_SPEED, RagdollConstants.MAX_ANGULAR_SPEED);
        r.rollVel = Math.clamp(delta.z * scale, -RagdollConstants.MAX_ANGULAR_SPEED, RagdollConstants.MAX_ANGULAR_SPEED);
    }

    void writePose(Ragdoll r) {
        r.orientation.getEulerAnglesYXZ(angles);
        r.pose.yaw = r.yaw = angles.y;
        r.pose.pitch = r.pitch = angles.x;
        r.pose.roll = r.roll = angles.z;
        r.pose.pivotY = r.skeleton.torsoY;
        r.pose.boneCount = r.skeleton.boneCount;
        r.pose.solved = true;
        for (int b = 0; b < r.skeleton.boneCount; b++) {
            r.jointRotation[b].getEulerAnglesZYX(angles);
            r.pose.boneRotX[b] = angles.x;
            r.pose.boneRotY[b] = angles.y;
            r.pose.boneRotZ[b] = angles.z;
        }
    }
}
