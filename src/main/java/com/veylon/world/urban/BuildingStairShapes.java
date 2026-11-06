package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.StairGeometry;

/** Frozen revision-three overlay: preserve rooms, fixtures and ids; shape only flights and landings. */
final class BuildingStairShapes {
    private BuildingStairShapes() { }

    static BuildingPlan apply(BuildingPlan original) {
        PlanVolume volume = original.voxels();
        PlanVolume.Builder out = volume.edit();
        // The old stair cubes on the switchback landing remain full-height slabs.
        for (int x = volume.x0; x < volume.x0 + volume.width; x++) {
            for (int z = volume.z0; z < volume.z0 + volume.depth; z++) {
                for (int y = volume.minY; y <= volume.maxY; y++) {
                    if (volume.getBlock(x, y, z) == BlockType.STAIRS) { out.set(x, y, z, BlockType.CONCRETE); }
                }
            }
        }
        boolean alongX = original.parcel().frontage().dx == 0;
        int ux = alongX ? 1 : 0, uz = alongX ? 0 : 1;
        for (BuildingPlan.Stair stair : original.stairs()) {
            // Route indices are part of the frozen walk-up switchback contract.
            for (int i : new int[]{1, 2, 6, 7}) {
                Vec3i at = stair.route().get(i), from = stair.route().get(i - 1);
                BlockType type = StairGeometry.ascending(at.x() - from.x(), at.z() - from.z());
                out.set(at.x(), at.y() - 1, at.z(), type);
                out.set(at.x() + ux, at.y() - 1, at.z() + uz, type);
            }
        }
        return new BuildingPlan(original.id(), UrbanRevision.BUILDING_TEMPLATE_SHAPED_STAIRS,
                original.template() + "-shaped-stairs", original.family(), original.variant(), original.parcel(),
                original.storeys(), original.basement(), original.floors(), original.fixtures(), original.approaches(),
                original.stairs(), out.build());
    }
}
