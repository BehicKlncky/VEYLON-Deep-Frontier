package com.veylon;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import org.joml.Vector3f;

/** Read-only, fixed-volume fire localization; never generates chunks or walks the save-wide fuel map. */
final class FireAudioLocator {
    /** Blocks: matches the maximum range of the existing heat-based ambience gain. */
    static final int RANGE = 6;
    /** Cell queries per medium pass are bounded by the 13-cubed local volume. */
    static final int MAX_CELL_QUERIES = (RANGE * 2 + 1) * (RANGE * 2 + 1) * (RANGE * 2 + 1);
    /** The fire system already caps its live set at this many cells. */
    static final int MAX_BURNING_CHECKS = 220;

    private FireAudioLocator() { }

    static boolean nearest(World world, Iterable<Vec3i> burning, float x, float y, float z, Vector3f out) {
        float nearest = RANGE * RANGE;
        boolean found = false;
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y), cz = (int) Math.floor(z);
        for (int dx = -RANGE; dx <= RANGE; dx++) {
            for (int dy = -RANGE; dy <= RANGE; dy++) {
                for (int dz = -RANGE; dz <= RANGE; dz++) {
                    float px = cx + dx + 0.5f, py = cy + dy + 0.5f, pz = cz + dz + 0.5f;
                    float distance = distanceSq(px, py, pz, x, y, z);
                    if (distance >= nearest) continue;
                    if (world.getBlock(cx + dx, cy + dy, cz + dz) != BlockType.CAMPFIRE) continue;
                    if (world.campfireFuel.getOrDefault(new Vec3i(cx + dx, cy + dy, cz + dz), 0f) <= 0) continue;
                    nearest = distance;
                    out.set(px, py, pz);
                    found = true;
                }
            }
        }
        int checked = 0;
        for (Vec3i fire : burning) {
            if (checked++ == MAX_BURNING_CHECKS) break;
            float px = fire.x() + 0.5f, py = fire.y() + 0.5f, pz = fire.z() + 0.5f;
            float distance = distanceSq(px, py, pz, x, y, z);
            if (distance >= nearest) continue;
            nearest = distance;
            out.set(px, py, pz);
            found = true;
        }
        return found;
    }

    private static float distanceSq(float ax, float ay, float az, float bx, float by, float bz) {
        float x = ax - bx, y = ay - by, z = az - bz;
        return x * x + y * y + z * z;
    }
}
