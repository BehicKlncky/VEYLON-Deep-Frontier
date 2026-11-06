package com.veylon.world;

/** Persisted stair direction; two disjoint tread boxes shared by all spatial consumers. */
public final class StairGeometry {
    private StairGeometry() { }

    public static boolean isStair(BlockType type) { return type != null && type.shape == BlockType.Shape.STAIRS; }

    /** The high end points in the supplied horizontal direction. */
    public static BlockType ascending(double dx, double dz) {
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? BlockType.STAIRS_EAST : BlockType.STAIRS_WEST;
        }
        return dz > 0 ? BlockType.STAIRS_SOUTH : BlockType.STAIRS_NORTH;
    }

    public static float minX(BlockType t, int part) {
        return t == BlockType.STAIRS_EAST && part == 1 || t == BlockType.STAIRS_WEST && part == 0 ? .5f : 0f;
    }
    public static float maxX(BlockType t, int part) {
        return t == BlockType.STAIRS_EAST && part == 0 || t == BlockType.STAIRS_WEST && part == 1 ? .5f : 1f;
    }
    public static float minZ(BlockType t, int part) {
        return t == BlockType.STAIRS_SOUTH && part == 1 || t == BlockType.STAIRS_NORTH && part == 0 ? .5f : 0f;
    }
    public static float maxZ(BlockType t, int part) {
        return t == BlockType.STAIRS_SOUTH && part == 0 || t == BlockType.STAIRS_NORTH && part == 1 ? .5f : 1f;
    }
    public static float height(int part) { return part == 0 ? .5f : 1f; }

    /** Shared internal face of a tread, using the mesher's +Y,-Y,-Z,+Z,-X,+X order. */
    static int internalFace(BlockType t, int part) {
        int high = switch (t) {
            case STAIRS_NORTH -> 2;
            case STAIRS_SOUTH -> 3;
            case STAIRS_WEST -> 4;
            case STAIRS_EAST -> 5;
            default -> throw new IllegalArgumentException("not a shaped stair");
        };
        return part == 0 ? high : (high ^ 1);
    }

    public static boolean collides(BlockType t, int x, int y, int z,
                            float x0, float y0, float z0, float x1, float y1, float z1) {
        for (int part = 0; part < 2; part++) {
            if (x1 > x + minX(t, part) + 1e-4f && x0 < x + maxX(t, part) - 1e-4f
                    && z1 > z + minZ(t, part) + 1e-4f && z0 < z + maxZ(t, part) - 1e-4f
                    && y1 > y + 1e-4f && y0 < y + height(part) - 1e-4f) {
                return true;
            }
        }
        return false;
    }

    /** Point collision for projectile samples, expressed in block-local coordinates. */
    public static boolean contains(BlockType t, float x, float y, float z) {
        for (int part = 0; part < 2; part++) {
            if (x >= minX(t, part) && x <= maxX(t, part) && z >= minZ(t, part)
                    && z <= maxZ(t, part) && y >= 0 && y <= height(part)) { return true; }
        }
        return false;
    }

    static boolean raycast(BlockType t, int x, int y, int z,
                           double ox, double oy, double oz, double dx, double dy, double dz,
                           double maxDist, Raycaster.MutableHit result) {
        boolean hit = false;
        for (int part = 0; part < 2; part++) {
            hit |= Raycaster.intersectBox(x, y, z, t, ox, oy, oz, dx, dy, dz,
                    x + minX(t, part), y, z + minZ(t, part),
                    x + maxX(t, part), y + height(part), z + maxZ(t, part), maxDist, result);
        }
        return hit;
    }
}
