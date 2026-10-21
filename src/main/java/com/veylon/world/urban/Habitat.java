package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import org.joml.Vector3f;

/**
 * Where a city creature lives (urban milestone 15). Ground species keep to a rectangle of open
 * ground; flying ones also keep between {@code floorY} and {@code ceilingY}. A territorial
 * organism keeps within {@code radius} of its growth {@code node}, folding at {@code rest}, the
 * cell beside it.
 *
 * <p>Derived from the frozen plan when the encounter director admits a body, or from where a
 * staged body stands, and never saved: a load or a reactivation derives it again. Every
 * movement target the AI picks is clamped into it, so a habitat is a hard leash rather than a
 * preference.
 *
 * @param node the growth node a territorial organism guards; null for wildlife and for a staged
 *        organism with no node, which then never withers
 * @param rest the cell a territorial organism folds in; null for wildlife
 * @param radius the territory's horizontal radius around the node's centre, or zero for a
 *        rectangle
 */
public record Habitat(int x0, int z0, int x1, int z1, float floorY, float ceilingY,
                      Vec3i node, Vec3i rest, float radius) {

    /** Margin kept from a habitat's edge, so a body's own width stays inside it. */
    public static final float EDGE = 0.5f;

    /** A growth territory: a circle of {@code radius} around the node, folding at {@code rest}. */
    public static Habitat territory(Vec3i node, Vec3i rest, float radius) {
        int r = (int) Math.ceil(radius);
        return new Habitat(node.x() - r, node.z() - r, node.x() + r, node.z() + r,
                node.y() - 3, node.y() + 3, node, rest, radius);
    }

    /** Open ground (or, with a raised band, the air over it) between two corner cells, inclusive. */
    public static Habitat area(int x0, int z0, int x1, int z1, float floorY, float ceilingY) {
        return new Habitat(Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1),
                floorY, ceilingY, null, null, 0f);
    }

    /** A staged territorial body with no node: its territory centred where it stands. */
    public static Habitat around(float x, float y, float z, float radius) {
        Vec3i at = new Vec3i((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        int r = (int) Math.ceil(radius);
        return new Habitat(at.x() - r, at.z() - r, at.x() + r, at.z() + r, at.y() - 3, at.y() + 3,
                null, at, radius);
    }

    public boolean territorial() {
        return radius > 0;
    }

    public float centerX() {
        return territorial() ? (node != null ? node.x() : rest.x()) + 0.5f : (x0 + x1 + 1) * 0.5f;
    }

    public float centerZ() {
        return territorial() ? (node != null ? node.z() : rest.z()) + 0.5f : (z0 + z1 + 1) * 0.5f;
    }

    /** Horizontal distance from the territory's centre, or from the area's nearest edge (zero inside). */
    public float outside(float x, float z) {
        if (territorial()) {
            float dx = x - centerX(), dz = z - centerZ();
            return Math.max(0f, (float) Math.sqrt(dx * dx + dz * dz) - radius);
        }
        float ox = Math.max(Math.max(x0 + EDGE - x, x - (x1 + 1 - EDGE)), 0f);
        float oz = Math.max(Math.max(z0 + EDGE - z, z - (z1 + 1 - EDGE)), 0f);
        return (float) Math.sqrt(ox * ox + oz * oz);
    }

    /** True when a point lies within the habitat (on the ground plane). */
    public boolean contains(float x, float z) {
        return outside(x, z) == 0f;
    }

    /** Moves a target point into the habitat, keeping {@link #EDGE} from its border; y is left alone. */
    public void clamp(Vector3f target) {
        if (territorial()) {
            float cx = centerX(), cz = centerZ();
            float dx = target.x - cx, dz = target.z - cz;
            float limit = Math.max(0.5f, radius - EDGE);
            float d = (float) Math.sqrt(dx * dx + dz * dz);
            if (d > limit) {
                target.x = cx + dx / d * limit;
                target.z = cz + dz / d * limit;
            }
            return;
        }
        target.x = Math.max(x0 + EDGE, Math.min(x1 + 1 - EDGE, target.x));
        target.z = Math.max(z0 + EDGE, Math.min(z1 + 1 - EDGE, target.z));
    }

    /** A flying target's height kept inside the band. */
    public float clampY(float y) {
        return Math.max(floorY, Math.min(ceilingY, y));
    }
}
