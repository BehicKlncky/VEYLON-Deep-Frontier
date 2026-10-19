package com.veylon.world.urban;

import com.veylon.world.BlockType;
import com.veylon.world.Chunk;

/**
 * Stamps one chunk's slice of a layout-nine region's canal basins and {@link UrbanOverlay}
 * raster (milestone 13), after the streets and buildings. Like every urban stamper it writes
 * only inside the chunk being generated, from the pure plan, so a region's overlays are the
 * same whichever chunk generates first; work is 256 columns.
 *
 * <p>Water surfaces all lie at the same height: the top of the block above the street datum,
 * one block deep on flooded streets and lawns and two deep in a canal basin, whose bed is the
 * datum fill one block lower. Water above sea level never spreads sideways or refills, so
 * none of it moves until a player edits around it.
 */
public final class UrbanOverlayStamper {
    private static final int GROUND = UrbanDatum.STREET_Y;

    private UrbanOverlayStamper() {
    }

    public static void stamp(Chunk chunk, DistrictPlan district) {
        if (!district.hasOverlays()) {
            return;
        }
        int x0 = chunk.cx * Chunk.SX;
        int z0 = chunk.cz * Chunk.SZ;
        for (int lz = 0; lz < Chunk.SZ; lz++) {
            for (int lx = 0; lx < Chunk.SX; lx++) {
                int x = x0 + lx;
                int z = z0 + lz;
                if (district.surfaceAt(x, z) == UrbanSurface.CANAL) {
                    chunk.set(lx, GROUND, lz, BlockType.WATER);
                    chunk.set(lx, GROUND + 1, lz, BlockType.WATER);
                }
                switch (district.overlayAt(x, z)) {
                    case NONE -> { }
                    case STANDING_WATER -> chunk.set(lx, GROUND + 1, lz, BlockType.WATER);
                    case DUCKBOARD -> chunk.set(lx, GROUND + 1, lz, BlockType.PLANK);
                    case FENCE -> {
                        chunk.set(lx, GROUND + 1, lz, BlockType.SHEET_METAL);
                        chunk.set(lx, GROUND + 2, lz, BlockType.SHEET_METAL);
                    }
                    case CHECKPOINT -> chunk.set(lx, GROUND, lz, BlockType.CLAY);
                    case CONTAMINATED -> chunk.set(lx, GROUND, lz, BlockType.CONTAMINATED_GROUND);
                    case OVERGROWN -> chunk.set(lx, GROUND, lz, BlockType.GRASS);
                    case PLANTS -> chunk.set(lx, GROUND + 1, lz,
                            UrbanHash.unit(district.seed, UrbanRevision.LAYOUT_DISTRICTS, UrbanSalts.OVERLAY_PLANT,
                                    x, z, 1) < 0.7 ? BlockType.TALL_GRASS : BlockType.BUSH);
                    case KEG -> chunk.set(lx, GROUND + 1, lz, BlockType.POWDER_KEG);
                }
            }
        }
    }
}
