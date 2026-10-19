package com.veylon.world.urban;

import com.veylon.world.BlockType;

/**
 * Temporary block mapping for the street-network layout (revision 2).
 *
 * <p>Urban materials — asphalt, concrete, kerbs, paving — are package 04's
 * block budget (DECISIONS §3). Until they exist, each surface stamps an
 * existing block chosen to read clearly from the street: dark basalt roadway,
 * stone-brick sidewalks, a sand centre line, clay crosswalk stripes, gravel
 * alleys and paths, dirt lots inside a stone outline, plank entrance markers
 * and a ruin-stone stair-core pad. No block id is added, so no save ordinal
 * changes.
 *
 * <p>The mapping is part of generator 5's frozen output. A later package that
 * gives streets their real materials does so under a new generator version;
 * it never edits this table (decision D-04).
 */
public final class UrbanSurfaceMaterials {

    /** Fill under every urban column, y 0 to the street datum. */
    public static final BlockType FILL = BlockType.STONE;
    public static final BlockType TRUNK = BlockType.LOG;
    public static final BlockType CANOPY = BlockType.LEAVES;

    private UrbanSurfaceMaterials() {
    }

    /** The block a surface stamps at the street datum. */
    public static BlockType blockFor(UrbanSurface surface) {
        return switch (surface) {
            case ROADWAY -> BlockType.BASALT;
            case LANE_MARK -> BlockType.SAND;
            case CROSSWALK -> BlockType.CLAY;
            case SIDEWALK, PASSAGE, PLAZA, DATUM -> BlockType.STONE_BRICK;
            case MEDIAN, COURTYARD, PARK, TREE_BED -> BlockType.GRASS;
            case ALLEY, COURTYARD_PATH, PARK_PATH -> BlockType.GRAVEL;
            case LOT -> BlockType.DIRT;
            case LOT_EDGE -> BlockType.STONE;
            case ENTRANCE -> BlockType.PLANK;
            case STAIR_CORE -> BlockType.RUIN_STONE;
            // Layout revision 9 only; layout 2 never plans them, so generator 5 is unchanged.
            case CANAL -> BlockType.WATER;
            case BRIDGE -> BlockType.STONE_BRICK;
            case YARD -> BlockType.GRAVEL;
            // A finished plan has no void; fill it solid rather than leave a hole.
            case VOID -> FILL;
        };
    }
}
