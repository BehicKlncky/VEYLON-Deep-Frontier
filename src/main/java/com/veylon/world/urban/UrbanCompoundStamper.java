package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;

/**
 * Stamps one chunk's slice of layout thirteen's compounds (urban milestone 17), after the
 * streets, buildings, overlays and routes. Like every urban stamper it writes only inside the
 * chunk being generated and reads only pure plans, so a compound is the same whichever chunk
 * generates first.
 *
 * <p>Each barricade line is three courses high across a passage or alley mouth: a stronghold's of
 * concrete under sheet metal, a raider yard's of rubble under sheet metal. In the gate's line two
 * metal door leaves stand two cells high under a sheet-metal lintel beside the solid post, whose
 * top course is the painted sign, with a torch on top lighting both sides. The leaves are ordinary
 * door fixtures: their planned lock is {@link UrbanWorldState#compoundLockAt}. Every block is an
 * existing id; no block, item or ordinal is added. Work is bounded: two compounds of two lines of
 * three columns each.
 */
public final class UrbanCompoundStamper {
    private UrbanCompoundStamper() { }

    public static void stamp(Chunk chunk, SurvivorSites sites) {
        for (CompoundSites.Compound c : sites.compounds()) {
            BlockType base = c.stronghold() ? BlockType.CONCRETE : BlockType.RUBBLE;
            for (CompoundSites.Barricade line : c.barricades()) {
                for (Vec3i cell : line.cells()) {
                    for (int h = 0; h < CompoundSites.HEIGHT; h++) {
                        int y = cell.y() + h;
                        BlockType block;
                        if (c.gate().leaf(cell.x(), y, cell.z())) {
                            block = BlockType.METAL_DOOR;
                        } else if (c.sign().x() == cell.x() && c.sign().y() == y && c.sign().z() == cell.z()) {
                            block = BlockType.SIGN;
                        } else {
                            block = h == 0 && !c.gate().column(cell.x(), cell.z()) ? base : BlockType.SHEET_METAL;
                        }
                        set(chunk, cell.x(), y, cell.z(), block);
                    }
                }
            }
            set(chunk, c.sign().x(), c.sign().y() + 1, c.sign().z(), BlockType.TORCH);
        }
    }

    private static void set(Chunk chunk, int x, int y, int z, BlockType block) {
        int lx = x - chunk.cx * Chunk.SX, lz = z - chunk.cz * Chunk.SZ;
        if (lx >= 0 && lx < Chunk.SX && lz >= 0 && lz < Chunk.SZ) { chunk.set(lx, y, lz, block); }
    }
}
