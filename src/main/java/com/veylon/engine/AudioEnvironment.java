package com.veylon.engine;

/** Pure classification of a listener's acoustic surroundings. No world queries or native calls. */
public final class AudioEnvironment {
    /**
     * Acoustic zones, independent of persisted biome and world enums. The last four are a generated
     * city's (urban milestone 22): a furnished room, a large hall, a basement or cellar below the
     * street, and a transit tunnel or service passage.
     */
    public enum Zone { OPEN, FOREST, SHALLOW, DEEP_CAVE, SHELTER, STONE_STRUCTURE, ROOM, HALL, BASEMENT, TUNNEL }
    /** Blocks below the surface where the underground acoustic zone starts. */
    public static final float SHALLOW_DEPTH = 5;
    /** Blocks below the surface where the long cave decay takes over. */
    public static final float DEEP_DEPTH = 18;

    private AudioEnvironment() { }

    /** Classifies synthetic or real observations, with underground depth taking precedence. */
    public static Zone classify(float depth, boolean exposed, boolean forest, boolean stoneStructure) {
        if (depth >= DEEP_DEPTH) return Zone.DEEP_CAVE;
        if (depth > SHALLOW_DEPTH) return Zone.SHALLOW;
        if (stoneStructure) return Zone.STONE_STRUCTURE;
        if (!exposed) return Zone.SHELTER;
        return forest ? Zone.FOREST : Zone.OPEN;
    }
}
