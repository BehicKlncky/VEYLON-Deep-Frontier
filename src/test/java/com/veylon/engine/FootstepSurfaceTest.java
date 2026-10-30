package com.veylon.engine;

import com.veylon.world.BlockType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Footsteps on the city's materials (urban milestone 22), and every wilderness step as it was. */
class FootstepSurfaceTest {
    /** The step each block had before 22, for the blocks that existed before the city (ids below ASPHALT). */
    private static FootstepSurface before(BlockType b) {
        Set<BlockType> stone = EnumSet.of(BlockType.STONE, BlockType.GRAVEL, BlockType.COAL_ORE, BlockType.COPPER_ORE,
                BlockType.IRON_ORE, BlockType.RUIN_STONE, BlockType.FURNACE, BlockType.ASH, BlockType.SCRAP_BLOCK,
                BlockType.POD_HULL, BlockType.ICE, BlockType.STONE_BRICK, BlockType.BASALT);
        Set<BlockType> wood = EnumSet.of(BlockType.PLANK, BlockType.LOG, BlockType.WORKBENCH, BlockType.CRATE,
                BlockType.WALL, BlockType.MAP_TABLE, BlockType.HERB_STATION);
        return stone.contains(b) ? FootstepSurface.STONE : wood.contains(b) ? FootstepSurface.WOOD
                : b == BlockType.SNOW ? FootstepSurface.SNOW : FootstepSurface.GRASS;
    }

    @Test
    void everyWildernessBlockKeepsItsStep() {
        int checked = 0;
        for (BlockType b : BlockType.values()) {
            if (b.ordinal() >= BlockType.ASPHALT.ordinal()) { continue; }
            assertEquals(before(b), FootstepSurface.of(b, null), b.name());
            assertEquals(before(b), FootstepSurface.of(b, BlockType.AIR), b.name());
            checked++;
        }
        assertTrue(checked > 50, "the wilderness's blocks were all checked: " + checked);
    }

    @Test
    void theCityClanksOnMetalCrunchesOnRubbleAndItsStairsAreConcrete() {
        for (BlockType metal : new BlockType[]{BlockType.SHEET_METAL, BlockType.METAL_DOOR, BlockType.RAILING,
                BlockType.CAR_WRECK, BlockType.BARRICADE_METAL, BlockType.GENERATOR, BlockType.WATER_PURIFIER,
                BlockType.LOCKER, BlockType.RELAY, BlockType.RADIO}) {
            assertEquals(FootstepSurface.METAL, FootstepSurface.of(metal, null), metal.name());
        }
        assertEquals(FootstepSurface.RUBBLE, FootstepSurface.of(BlockType.RUBBLE, null));
        assertEquals(FootstepSurface.RUBBLE, FootstepSurface.of(BlockType.CONCRETE, BlockType.DEBRIS),
                "debris on a concrete floor crunches");
        for (BlockType stair : new BlockType[]{BlockType.STAIRS, BlockType.STAIRS_NORTH, BlockType.STAIRS_SOUTH,
                BlockType.STAIRS_WEST, BlockType.STAIRS_EAST, BlockType.ASPHALT, BlockType.PAVING, BlockType.CONCRETE,
                BlockType.BRICK, BlockType.PLASTER}) {
            assertEquals(FootstepSurface.STONE, FootstepSurface.of(stair, null), stair.name());
        }
        assertEquals(FootstepSurface.WOOD, FootstepSurface.of(BlockType.BARRICADE, null));
        assertEquals(FootstepSurface.GRASS, FootstepSurface.of(null, null), "nothing known underfoot is a soft step");
    }

    @Test
    void aStepIsAsLoudAsWhatTheInfectedHearOfIt() {
        float crouch = FootstepSurface.loudness(false, true), walk = FootstepSurface.loudness(false, false);
        float sprint = FootstepSurface.loudness(true, false);
        assertEquals(1f, walk, "a walk is as loud as every step was before");
        assertTrue(crouch < walk && walk < sprint, "crouched steps are faint, sprinted ones loud");
        assertEquals(crouch, FootstepSurface.loudness(true, true), "crouching wins, as it does for the noise");
    }
}
