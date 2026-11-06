package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;

/**
 * Stamps one chunk's slice of layout fourteen's burnt-out cars (urban milestone 18), after the
 * streets, buildings, overlays, routes and compounds. Like every urban stamper it writes only
 * inside the chunk being generated and reads only the pure {@link VehicleSites} plan, so a wreck
 * straddling a chunk border is whole whichever chunk generates first. Every cell is a
 * {@link BlockType#CAR_WRECK}; what part of the car it is comes from the plan. Work is bounded:
 * at most {@value VehicleSites#MAX_PER_REGION} wrecks of twelve cells each.
 */
public final class UrbanVehicleStamper {
    private UrbanVehicleStamper() { }

    public static void stamp(Chunk chunk, VehicleSites vehicles) {
        for (VehicleSites.Wreck wreck : vehicles.wrecks()) {
            for (Vec3i cell : wreck.cells()) {
                int lx = cell.x() - chunk.cx * Chunk.SX, lz = cell.z() - chunk.cz * Chunk.SZ;
                if (lx >= 0 && lx < Chunk.SX && lz >= 0 && lz < Chunk.SZ) {
                    chunk.set(lx, cell.y(), lz, BlockType.CAR_WRECK);
                }
            }
        }
    }
}
