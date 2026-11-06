package com.veylon.world;

/** Body collision: solid cells, shaped stair treads, and passable open fixture leaves. */
public final class VoxelClearance {
    /** Shared humanoid dimensions and navigation drop limit; changing them is gameplay. */
    public static final float BODY_WIDTH = 0.6f;
    public static final float BODY_HEIGHT = 1.8f;
    public static final int MAX_DROP = 3;
    private VoxelClearance() { }

    public static boolean collides(VoxelAccess voxels, float px, float py, float pz,
                                   float width, float height, boolean flamesBlock) {
        if (py < 0) {
            return true;
        }
        float hw = width / 2f;
        int x0 = (int) Math.floor(px - hw);
        int x1 = (int) Math.floor(px + hw - 1e-4f);
        int y0 = (int) Math.floor(py);
        int y1 = (int) Math.floor(py + height - 1e-4f);
        int z0 = (int) Math.floor(pz - hw);
        int z1 = (int) Math.floor(pz + hw - 1e-4f);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    BlockType t = voxels.getBlock(x, y, z);
                    if (StairGeometry.isStair(t)) {
                        if (StairGeometry.collides(t, x, y, z, px - hw, py, pz - hw,
                                px + hw, py + height, pz + hw)) { return true; }
                        continue;
                    }
                    if (t.solid || flamesBlock && (t == BlockType.TORCH || t == BlockType.CAMPFIRE)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Pathfinder's exact passability policy, including doors, flames and ladders. */
    public static boolean passable(VoxelAccess w, int x, int y, int z) {
        BlockType t = w.getBlock(x, y, z);
        if (t == BlockType.GATE) {
            return true;
        }
        if (Fixtures.isClosedDoor(t)) {
            return w.fixtureLockAt(x, y, z) == FixtureStates.LOCK_NONE;
        }
        return t != BlockType.TORCH && t != BlockType.CAMPFIRE && (!t.solid || t.isClimbable());
    }

    /** Pathfinder's standing policy; unloaded space never counts as clearance. */
    public static boolean standable(VoxelAccess w, int x, int y, int z) {
        if (!w.isColumnLoaded(x, z) || !passable(w, x, y, z) || !passable(w, x, y + 1, z)) {
            return false;
        }
        BlockType feet = w.getBlock(x, y, z);
        return w.getBlock(x, y - 1, z).solid || feet.isClimbable() || feet == BlockType.WATER;
    }
}
