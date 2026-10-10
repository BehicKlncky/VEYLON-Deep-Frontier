package com.veylon.world.urban;

import com.veylon.world.Chunk;

/** Per-world urban revisions and the deterministic street-datum foundation. */
public final class UrbanWorldState {

    /** Region plans kept at once (DESIGN §10, package 03). */
    public static final int MAX_REGION_PLANS = 16;

    /** Spawn column of the street-datum placeholder (layout revision 1). */
    static final int PLACEHOLDER_SPAWN_X = 8;
    static final int PLACEHOLDER_SPAWN_Z = 8;

    private static final int UNRESOLVED = 0;

    private final long seed;
    private final int layoutRevision;
    private int contentRevision = UNRESOLVED;

    /**
     * @throws IllegalArgumentException for a generator that is not urban
     */
    public UrbanWorldState(long seed, int generatorVersion) {
        this.seed = seed;
        this.layoutRevision = UrbanRevision.layoutRevisionOf(generatorVersion);
    }

    public int layoutRevision() {
        return layoutRevision;
    }

    /** True for a layout with streets, parcels and districts; false for the bare datum. */
    public boolean hasCityPlan() {
        return false;
    }

    public boolean contentRevisionResolved() {
        return contentRevision != UNRESOLVED;
    }

    /**
     * @throws IllegalStateException while a load has not yet restored the revision
     */
    public int contentRevision() {
        if (contentRevision == UNRESOLVED) {
            throw new IllegalStateException("urban content revision is not resolved yet");
        }
        return contentRevision;
    }

    /**
     * Fixes this world's content revision. A world resolves it exactly once.
     *
     * @throws IllegalArgumentException for a revision this build cannot honour
     * @throws IllegalStateException if the revision was already resolved
     */
    public void resolveContentRevision(int revision) {
        UrbanRevision.requireSupportedContent(revision);
        if (contentRevision != UNRESOLVED) {
            throw new IllegalStateException("urban content revision is already resolved ("
                    + contentRevision + ")");
        }
        contentRevision = revision;
    }

    /** Generates the street-datum placeholder without consulting other chunks. */
    public void stamp(Chunk chunk, float soilMoisture) {
        UrbanPlaceholderTerrain.stamp(chunk, soilMoisture);
    }

    /** No district plans exist in the foundation layout. */
    public int cachedRegionPlans() {
        return 0;
    }

    /** The walkable street slab; feet stand one block above it. */
    public int groundY(int x, int z) {
        return UrbanDatum.STREET_Y;
    }

    /** Fixed development spawn for the street datum. */
    public int[] spawnColumn() {
        return new int[]{PLACEHOLDER_SPAWN_X, PLACEHOLDER_SPAWN_Z};
    }
}
