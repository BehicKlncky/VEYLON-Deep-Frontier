package com.veylon.world.urban;

import com.veylon.world.BlockType;
import com.veylon.world.VoxelAccess;

import java.util.Arrays;

/** Immutable, bounded voxel proposal. Reads never create chunks or touch a live world. */
public final class PlanVolume implements VoxelAccess {
    public static final int MAX_CELLS = 34 * 34 * 64;
    public final int x0, z0, width, depth, minY, maxY;
    private final byte[] blocks;

    private PlanVolume(Builder b) {
        x0 = b.x0; z0 = b.z0; width = b.width; depth = b.depth;
        minY = b.minY; maxY = b.maxY;
        blocks = b.blocks.clone();
    }

    public int cellCount() { return blocks.length; }

    @Override
    public boolean isColumnLoaded(int x, int z) {
        return x >= x0 && x < x0 + width && z >= z0 && z < z0 + depth;
    }

    @Override
    public BlockType getBlock(int x, int y, int z) {
        if (!isColumnLoaded(x, z) || y < minY || y > maxY) {
            return BlockType.AIR;
        }
        return BlockType.byId(blocks[index(x, y, z)]);
    }

    private int index(int x, int y, int z) {
        return ((y - minY) * depth + z - z0) * width + x - x0;
    }

    /** A copy for constrained overlays and deliberately invalid validation fixtures. */
    public Builder edit() { return new Builder(this); }

    public long fingerprint() {
        long h = 0xcbf29ce484222325L;
        for (byte block : blocks) { h = (h ^ (block & 255)) * 0x100000001b3L; }
        return h;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PlanVolume v && x0 == v.x0 && z0 == v.z0 && width == v.width
                && depth == v.depth && minY == v.minY && maxY == v.maxY && Arrays.equals(blocks, v.blocks);
    }

    @Override
    public int hashCode() { return Long.hashCode(fingerprint()); }

    /** Scratch owned by one planning call. Out-of-volume writes are rejected, never clipped. */
    public static final class Builder {
        private final int x0, z0, width, depth, minY, maxY;
        private final byte[] blocks;

        public Builder(int x0, int z0, int width, int depth, int minY, int maxY) {
            long cells = (long) width * depth * (maxY - minY + 1);
            if (width < 1 || width > 34 || depth < 1 || depth > 34 || minY < 0
                    || maxY >= 96 || maxY < minY || cells > MAX_CELLS) {
                throw new IllegalArgumentException("building proposal exceeds voxel bounds");
            }
            this.x0 = x0; this.z0 = z0; this.width = width; this.depth = depth;
            this.minY = minY; this.maxY = maxY;
            blocks = new byte[(int) cells];
        }

        private Builder(PlanVolume v) {
            x0 = v.x0; z0 = v.z0; width = v.width; depth = v.depth;
            minY = v.minY; maxY = v.maxY; blocks = v.blocks.clone();
        }

        public Builder set(int x, int y, int z, BlockType block) {
            if (x < x0 || x >= x0 + width || z < z0 || z >= z0 + depth || y < minY || y > maxY) {
                throw new IllegalArgumentException("voxel outside building proposal: " + x + "," + y + "," + z);
            }
            blocks[((y - minY) * depth + z - z0) * width + x - x0] = (byte) block.ordinal();
            return this;
        }

        public PlanVolume build() { return new PlanVolume(this); }
    }
}
