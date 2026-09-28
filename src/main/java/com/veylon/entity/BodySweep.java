package com.veylon.entity;

/**
 * The space one living body swept through during one fast tick, for flame
 * contact: its flame box moved in a straight line from where the previous
 * fast tick sampled it to where it stands now. A flame source asks
 * {@link #touches} of its own flame volume, so a body that crosses a small
 * flame entirely between two samples still touches it. Nothing along the
 * body's path is skipped; in time, flames are looked at once per
 * {@link com.veylon.simulation.SimulationScheduler#FAST_DT}.
 *
 * <p><b>Flame box.</b> A walking or swimming body's is its ordinary entity box,
 * {@code width} square around its position and {@code height} tall from its
 * feet, the box its physics, projectiles and the old pool contact already
 * use. A flying creature's reaches out to its wingtips: half its width grows
 * to the widest lateral reach of its anatomy table's rest pose, and the box
 * grows by the same wing length below its feet and above its back, the
 * envelope a flapping wing sweeps. Nothing else is inflated: warmth at a
 * distance is not contact.
 *
 * <p><b>Sweep.</b> The motion between the two samples is taken as linear:
 * within a twentieth of a second any turn or collision slide bends it by a
 * few centimetres at most. A move longer than {@link #MAX_SWEEP} between two
 * samples is a teleport (respawn, a load, a debug move) and only its end is
 * sampled, never the line through whatever lay between.
 *
 * <p>One instance is reused for every body, every tick: no allocation.
 */
public final class BodySweep {

    /** Longest move between two samples that is swept rather than taken as a jump. */
    public static final float MAX_SWEEP = 2f;

    /** Lateral wing reach by {@link Creature.CreatureType} ordinal, 0 for a creature that does not fly. */
    private static final float[] WING_REACH = new float[Creature.CreatureType.values().length];

    static {
        for (Creature.CreatureType type : Creature.CreatureType.values()) {
            WING_REACH[type.ordinal()] = type.flying ? lateralReach(FragmentAnatomy.of(type)) : 0f;
        }
    }

    /** Bottom centre of the flame box where the sweep starts and where it ends. */
    private float x0, y0, z0, x1, y1, z1;
    private float halfWidth, height;
    private float minX, minY, minZ, maxX, maxY, maxZ;
    private float contactX, contactY, contactZ;

    /**
     * Sweeps {@code e}'s flame box from the bottom centre it had at the
     * previous sample to the one it has now; {@code fromPrevious} false, or a
     * jump longer than {@link #MAX_SWEEP}, samples only where it is now.
     */
    void set(Entity e, boolean fromPrevious, float prevX, float prevY, float prevZ) {
        halfWidth = halfWidth(e);
        height = height(e);
        x1 = e.pos.x;
        y1 = e.pos.y - below(e);
        z1 = e.pos.z;
        float dx = x1 - prevX, dy = y1 - prevY, dz = z1 - prevZ;
        if (fromPrevious && dx * dx + dy * dy + dz * dz <= MAX_SWEEP * MAX_SWEEP) {
            x0 = prevX;
            y0 = prevY;
            z0 = prevZ;
        } else {
            x0 = x1;
            y0 = y1;
            z0 = z1;
        }
        minX = Math.min(x0, x1) - halfWidth;
        maxX = Math.max(x0, x1) + halfWidth;
        minY = Math.min(y0, y1);
        maxY = Math.max(y0, y1) + height;
        minZ = Math.min(z0, z1) - halfWidth;
        maxZ = Math.max(z0, z1) + halfWidth;
    }

    /** Where the bottom centre of this tick's flame box ended: the next sweep's start. */
    float endX() {
        return x1;
    }

    float endY() {
        return y1;
    }

    float endZ() {
        return z1;
    }

    /**
     * Whether the flame box overlaps the box {@code [ax, bx] x [ay, by] x
     * [az, bz]} at some moment of the sweep. Touching faces do not overlap.
     * When it does, the contact point is set to the centre of the two boxes'
     * common volume halfway through the time they overlap.
     */
    public boolean touches(float ax, float ay, float az, float bx, float by, float bz) {
        // The bottom centre overlaps the box while it lies inside it grown by
        // the flame box's own extents: one slab per axis, open intervals.
        float t0 = 0f;
        float t1 = 1f;
        float enter;
        float leave;
        float d = x1 - x0;
        if (d == 0f) {
            if (!(x0 > ax - halfWidth && x0 < bx + halfWidth)) {
                return false;
            }
        } else {
            enter = (ax - halfWidth - x0) / d;
            leave = (bx + halfWidth - x0) / d;
            t0 = Math.max(t0, Math.min(enter, leave));
            t1 = Math.min(t1, Math.max(enter, leave));
            if (!(t0 < t1)) {
                return false;
            }
        }
        d = y1 - y0;
        if (d == 0f) {
            if (!(y0 > ay - height && y0 < by)) {
                return false;
            }
        } else {
            enter = (ay - height - y0) / d;
            leave = (by - y0) / d;
            t0 = Math.max(t0, Math.min(enter, leave));
            t1 = Math.min(t1, Math.max(enter, leave));
            if (!(t0 < t1)) {
                return false;
            }
        }
        d = z1 - z0;
        if (d == 0f) {
            if (!(z0 > az - halfWidth && z0 < bz + halfWidth)) {
                return false;
            }
        } else {
            enter = (az - halfWidth - z0) / d;
            leave = (bz + halfWidth - z0) / d;
            t0 = Math.max(t0, Math.min(enter, leave));
            t1 = Math.min(t1, Math.max(enter, leave));
            if (!(t0 < t1)) {
                return false;
            }
        }
        float t = (t0 + t1) * 0.5f;
        float cx = x0 + (x1 - x0) * t;
        float cy = y0 + (y1 - y0) * t;
        float cz = z0 + (z1 - z0) * t;
        contactX = (Math.max(cx - halfWidth, ax) + Math.min(cx + halfWidth, bx)) * 0.5f;
        contactY = (Math.max(cy, ay) + Math.min(cy + height, by)) * 0.5f;
        contactZ = (Math.max(cz - halfWidth, az) + Math.min(cz + halfWidth, bz)) * 0.5f;
        return true;
    }

    /** Bounds of everything the flame box swept this tick, for a cheap first rejection. */
    public float minX() {
        return minX;
    }

    public float minY() {
        return minY;
    }

    public float minZ() {
        return minZ;
    }

    public float maxX() {
        return maxX;
    }

    public float maxY() {
        return maxY;
    }

    public float maxZ() {
        return maxZ;
    }

    /** Where the last successful {@link #touches} met the body. */
    public float contactX() {
        return contactX;
    }

    public float contactY() {
        return contactY;
    }

    public float contactZ() {
        return contactZ;
    }

    /**
     * Whether {@code e}'s flame box, where it stands now, overlaps the box
     * {@code [ax, bx] x [ay, by] x [az, bz]}. For decisions that are not a
     * tick's contact, such as refusing to place a flame inside someone.
     */
    public static boolean overlapsNow(Entity e, float ax, float ay, float az,
                                      float bx, float by, float bz) {
        float hw = halfWidth(e);
        float bottom = e.pos.y - below(e);
        return e.pos.x - hw < bx && e.pos.x + hw > ax
                && bottom < by && bottom + height(e) > ay
                && e.pos.z - hw < bz && e.pos.z + hw > az;
    }

    /** Half the flame box's width: the entity's, or a flier's wing reach if that is wider. */
    static float halfWidth(Entity e) {
        return Math.max(e.width * 0.5f, wingReach(e));
    }

    /** How far the flame box reaches below the feet: a flier's wing length, else nothing. */
    static float below(Entity e) {
        return Math.max(0f, wingReach(e) - e.width * 0.5f);
    }

    /** Height of the flame box: the entity's, plus a flier's wing length below and above. */
    static float height(Entity e) {
        return e.height + 2f * below(e);
    }

    private static float wingReach(Entity e) {
        return e instanceof Creature c ? WING_REACH[c.type.ordinal()] : 0f;
    }

    /**
     * Widest distance from the body's centre line to the side of any box in
     * the rest pose: a pivot is in its parent's frame, so the chain is summed.
     */
    private static float lateralReach(FragmentAnatomy anatomy) {
        float reach = 0f;
        for (int i = 0; i < anatomy.jointCount(); i++) {
            FragmentAnatomy.Joint joint = anatomy.joint(i);
            if (!joint.boxed) {
                continue;
            }
            float x = joint.boxX;
            for (int j = i; j >= 0; j = anatomy.joint(j).parent) {
                x += anatomy.joint(j).pivotX;
            }
            reach = Math.max(reach, Math.abs(x) + joint.sizeX * 0.5f);
        }
        return reach;
    }
}
