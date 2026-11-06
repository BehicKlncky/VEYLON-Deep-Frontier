package com.veylon.world.urban;

import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.World;

/**
 * Live, bounded observations for generated urban interiors. Light and weather
 * read only loaded voxels and derived column bounds, never building-plan caches.
 * The optional location query reads immutable plans and does not create chunks.
 */
public final class UrbanIndoorQueries {
    /** Daylight travels at most this far from a visible window or opening. */
    public static final int DAYLIGHT_RADIUS = 8;
    private static final int[][] CARDINAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final float GLASS_TRANSMISSION = 0.8f;

    /** Immutable spatial metadata; room is null on a partition or unassigned core cell. */
    public record Location(BuildingPlan building, BuildingPlan.Floor floor, BuildingPlan.Room room) { }

    private UrbanIndoorQueries() { }

    /** True only when the loaded column has no opaque roof or closed glass above this cell. */
    public static boolean isSkyExposed(World world, int x, int y, int z) {
        Chunk chunk = loaded(world, x, z);
        return chunk != null && y >= chunk.precipitationHeight(Math.floorMod(x, Chunk.SX),
                Math.floorMod(z, Chunk.SZ));
    }

    /**
     * Urban sky light: direct sky, glass transmission, bounded straight daylight
     * through windows/openings, then a small diffuse interior term. Indirect
     * light stays below the precipitation threshold used by fire and collectors.
     * No result is cached, so edits and newly loaded neighbors are visible at once.
     */
    public static float skyLight(World world, int x, int y, int z) {
        Chunk chunk = loaded(world, x, z);
        if (chunk == null || y < 0) { return 0.04f; }
        float direct = directSky(chunk, x, y, z);
        if (direct > 0) { return direct; }
        float best = y >= UrbanDatum.STREET_Y ? 0.12f : 0.075f;
        // Below the city's playable basement bands retain the deep-fill darkness.
        if (y < UrbanDatum.slabY(-1)) { return 0.04f; }
        boolean aboveClear = y + 1 < Chunk.SY && !chunk.get(Math.floorMod(x, Chunk.SX),
                y + 1, Math.floorMod(z, Chunk.SZ)).opaque;
        for (int[] direction : CARDINAL) {
            best = Math.max(best, openingLight(world, x, y, z, direction[0], direction[1]));
            // Windows sit above feet height; a clear adjacent cell receives that daylight too.
            if (aboveClear) {
                best = Math.max(best, openingLight(world, x, y + 1, z, direction[0], direction[1]) * 0.9f);
            }
        }
        return best;
    }

    private static float openingLight(World world, int x, int y, int z, int dx, int dz) {
        float transmission = 1f;
        for (int distance = 1; distance <= DAYLIGHT_RADIUS; distance++) {
            int bx = x + dx * distance, bz = z + dz * distance;
            Chunk chunk = loaded(world, bx, bz);
            if (chunk == null) { break; }
            BlockType block = chunk.get(Math.floorMod(bx, Chunk.SX), y, Math.floorMod(bz, Chunk.SZ));
            if (block.opaque) { break; }
            if (block == BlockType.WINDOW) { transmission *= GLASS_TRANSMISSION; }
            float sky = directSky(chunk, bx, y, bz);
            if (sky > 0) {
                return 0.72f * transmission * sky * (1f - distance / (DAYLIGHT_RADIUS + 1f));
            }
        }
        return 0;
    }

    /** Cells a light ray may cross; a lamp's full range (11.25 blocks) needs at most 33. */
    static final int LIGHT_RAY_CELLS = 48;

    /**
     * Whether a block light at one cell reaches another along the straight line between their
     * centres without crossing an opaque cell (urban milestone 22). The two end cells never block,
     * glass and open leaves pass light, closed doors and walls stop it. The traversal visits each
     * crossed cell once, one axis step at a time, so a ray cannot slip between two blocks that
     * meet at an edge; it reads loaded voxels only, at most {@value #LIGHT_RAY_CELLS} of them.
     */
    public static boolean lightReaches(World world, int lx, int ly, int lz, int x, int y, int z) {
        int dx = x - lx, dy = y - ly, dz = z - lz;
        int sx = Integer.signum(dx), sy = Integer.signum(dy), sz = Integer.signum(dz);
        // Parametric distance, centre to centre, to the first boundary on each axis and between boundaries.
        double tx = dx == 0 ? Double.MAX_VALUE : 0.5 / Math.abs(dx), stepX = dx == 0 ? 0 : 1.0 / Math.abs(dx);
        double ty = dy == 0 ? Double.MAX_VALUE : 0.5 / Math.abs(dy), stepY = dy == 0 ? 0 : 1.0 / Math.abs(dy);
        double tz = dz == 0 ? Double.MAX_VALUE : 0.5 / Math.abs(dz), stepZ = dz == 0 ? 0 : 1.0 / Math.abs(dz);
        int cx = lx, cy = ly, cz = lz;
        // A ray rarely leaves its chunk: keep the one it is in rather than looking it up per cell.
        int chunkX = Integer.MIN_VALUE, chunkZ = Integer.MIN_VALUE;
        Chunk chunk = null;
        for (int i = 0; i < LIGHT_RAY_CELLS; i++) {
            if (cx == x && cy == y && cz == z) { return true; }
            if (tx < ty && tx < tz) { cx += sx; tx += stepX; }
            else if (ty < tz) { cy += sy; ty += stepY; }
            else { cz += sz; tz += stepZ; }
            if (cx == x && cy == y && cz == z) { return true; }
            int ncx = Math.floorDiv(cx, Chunk.SX), ncz = Math.floorDiv(cz, Chunk.SZ);
            if (ncx != chunkX || ncz != chunkZ) {
                chunkX = ncx;
                chunkZ = ncz;
                chunk = world.getChunk(ncx, ncz);
            }
            if (chunk != null && chunk.get(Math.floorMod(cx, Chunk.SX), cy, Math.floorMod(cz, Chunk.SZ)).opaque) {
                return false;
            }
        }
        return false;
    }

    private static float directSky(Chunk chunk, int x, int y, int z) {
        int lx = Math.floorMod(x, Chunk.SX), lz = Math.floorMod(z, Chunk.SZ);
        if (y < chunk.height(lx, lz)) { return 0; }
        return y >= chunk.precipitationHeight(lx, lz) ? 1f : GLASS_TRANSMISSION;
    }

    /**
     * Building/floor/room containing the cell; unloaded or outside generated buildings is null.
     * In layout ten a transit station is a building too, but only below the street: its one
     * floor (index -2) holds every station cell from the track bed to the stair's last tread
     * under the pavement, so a body on the avenue above it is never inside.
     */
    public static Location location(World world, int x, int y, int z) {
        if (world.urban == null || !world.urban.hasGeneratedBuildings() || loaded(world, x, z) == null) {
            return null;
        }
        BuildingPlan building = world.urban.buildingAt(x, z);
        if (building == null) { return station(world, x, y, z); }
        for (BuildingPlan.Floor floor : building.floors()) {
            if (y < floor.slabY() || y >= floor.slabY() + UrbanDatum.STOREY_PITCH) { continue; }
            BuildingPlan.Room found = null;
            for (BuildingPlan.Room room : floor.rooms()) {
                if (room.interior().contains(x, z)) { found = room; break; }
            }
            return new Location(building, floor, found);
        }
        return null;
    }

    private static Location station(World world, int x, int y, int z) {
        if (y <= TransitNetwork.SLAB_Y || y >= UrbanDatum.STREET_Y) { return null; }
        TransitStation station = world.urban.stationAt(x, z);
        if (station == null) { return null; }
        BuildingPlan.Floor floor = station.plan().floors().getFirst();
        for (BuildingPlan.Room room : floor.rooms()) {
            if (room.interior().contains(x, z)) { return new Location(station.plan(), floor, room); }
        }
        return new Location(station.plan(), floor, null);
    }

    private static Chunk loaded(World world, int x, int z) {
        Chunk chunk = world.getChunk(Math.floorDiv(x, Chunk.SX), Math.floorDiv(z, Chunk.SZ));
        return chunk != null && chunk.generated ? chunk : null;
    }
}
