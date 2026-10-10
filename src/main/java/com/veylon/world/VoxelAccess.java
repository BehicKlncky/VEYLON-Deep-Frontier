package com.veylon.world;

/** Read-only collision input, shared by live worlds and immutable building plans. */
public interface VoxelAccess {
    BlockType getBlock(int x, int y, int z);

    boolean isColumnLoaded(int x, int z);

    default int fixtureLockAt(int x, int y, int z) {
        return FixtureStates.LOCK_NONE;
    }
}
