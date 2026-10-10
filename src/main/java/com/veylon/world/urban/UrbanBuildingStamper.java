package com.veylon.world.urban;

import com.veylon.world.BlockType;
import com.veylon.world.Chunk;

/**
 * Generator-seven chunk slices of whole, already validated furnished plans.
 * Parcels own their footprint only: the proposal's paving halo is clearance
 * context, never an instruction to overwrite a neighbouring parcel or street.
 */
public final class UrbanBuildingStamper {
    private UrbanBuildingStamper() { }

    /** Bounded by one region's parcels and at most 256 columns of owned geometry. */
    public static void stamp(Chunk chunk, DistrictPlan district, UrbanWorldState urban) {
        if (!urban.hasGeneratedBuildings()) {
            throw new IllegalArgumentException("building stamping requires the furnished layout");
        }
        int x0 = chunk.cx * Chunk.SX, z0 = chunk.cz * Chunk.SZ;
        for (int x = x0; x < x0 + Chunk.SX; x++) {
            for (int z = z0; z < z0 + Chunk.SZ; z++) {
                chunk.set(x - x0, UrbanDatum.STREET_Y, z - z0,
                        surface(district.surfaceAt(x, z)));
            }
        }
        for (ParcelPlan parcel : district.parcels()) {
            int ax = Math.max(x0, parcel.x0()), bx = Math.min(x0 + Chunk.SX - 1, parcel.x1());
            int az = Math.max(z0, parcel.z0()), bz = Math.min(z0 + Chunk.SZ - 1, parcel.z1());
            if (ax > bx || az > bz) { continue; }
            BuildingPlan building = urban.buildingAt(ax, az);
            PlanVolume volume = building.voxels();
            for (int y = volume.minY; y <= volume.maxY; y++) {
                for (int z = az; z <= bz; z++) {
                    for (int x = ax; x <= bx; x++) {
                        chunk.set(x - x0, y, z - z0, volume.getBlock(x, y, z));
                    }
                }
            }
        }
    }

    private static BlockType surface(UrbanSurface surface) {
        return switch (surface) {
            case ROADWAY -> BlockType.ASPHALT;
            case SIDEWALK, PASSAGE, PLAZA, DATUM, ENTRANCE -> BlockType.PAVING;
            case LOT_EDGE, STAIR_CORE -> BlockType.CONCRETE;
            default -> UrbanSurfaceMaterials.blockFor(surface);
        };
    }
}
