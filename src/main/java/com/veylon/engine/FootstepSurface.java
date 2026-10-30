package com.veylon.engine;

import com.veylon.world.BlockType;
import com.veylon.world.World;

/**
 * What a footstep sounds like on what the player stands on and in. Every block of the wilderness
 * keeps the step it had before urban milestone 22; the city's metal (sheet metal, metal doors,
 * rails, wrecks, lockers and machines) now clanks, rubble or debris underfoot crunches, and its
 * shaped stair flights sound like the concrete they are.
 */
public enum FootstepSurface {
    GRASS, STONE, WOOD, SNOW, WATER, METAL, RUBBLE;

    /** Gain of an ordinary walking step, as before milestone 22. */
    static final float WALK_GAIN = 0.35f;

    /**
     * The surface of a step: the block underfoot, or the debris the feet stand in. {@code feet}
     * may be null where only the block underfoot is known.
     */
    public static FootstepSurface of(BlockType under, BlockType feet) {
        if (feet == BlockType.DEBRIS) { return RUBBLE; }
        if (under == null) { return GRASS; }
        return switch (under) {
            case SHEET_METAL, METAL_DOOR, METAL_DOOR_OPEN, RAILING, CAR_WRECK, BARRICADE_METAL, GENERATOR,
                 WATER_PURIFIER, LOCKER, RELAY, RADIO -> METAL;
            case RUBBLE, DEBRIS -> RUBBLE;
            case STONE, GRAVEL, COAL_ORE, COPPER_ORE, IRON_ORE, RUIN_STONE, FURNACE, ASH,
                 SCRAP_BLOCK, POD_HULL, ICE, STONE_BRICK, BASALT, ASPHALT, PAVING, CONCRETE,
                 BRICK, PLASTER, STAIRS, HIDDEN_PANEL,
                 // The shaped flights of package 06 were missed and stepped like grass until 22.
                 STAIRS_NORTH, STAIRS_SOUTH, STAIRS_WEST, STAIRS_EAST -> STONE;
            case PLANK, LOG, WORKBENCH, CRATE, WALL, MAP_TABLE, HERB_STATION, WOOD_DOOR, SHELF,
                 CABINET, TABLE, SIGN, BARRICADE -> WOOD;
            case SNOW -> SNOW;
            default -> GRASS;
        };
    }

    /** The surface of a step with the feet at {@code (x, y, z)}: the block underfoot and any debris stood in. */
    public static FootstepSurface underfoot(World world, float x, float y, float z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        return of(world.getBlock(bx, (int) Math.floor(y - 0.1f), bz), world.getBlock(bx, (int) Math.floor(y + 0.05f), bz));
    }

    /**
     * How loud a step is relative to walking, matching what the AI hears of it: a crouched step
     * is faint and reaches no listener, a walk is heard 10 blocks away, a sprint 22.
     */
    public static float loudness(boolean sprinting, boolean crouching) {
        return crouching ? 0.45f : sprinting ? 1.45f : 1f;
    }
}
