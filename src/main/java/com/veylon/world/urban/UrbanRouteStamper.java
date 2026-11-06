package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;

/**
 * Stamps one chunk's slice of layout ten's routes (milestone 14), after the streets, buildings
 * and overlays. Like every urban stamper it writes only inside the chunk being generated and
 * reads only pure plans, so the routes are the same whichever chunk generates first.
 *
 * <ul>
 *   <li>Transit: every column of a candidate avenue band gets its segment's tunnel or station
 *       cells ({@link TransitNetwork#blockAt}); most chunks touch no band and skip at once.</li>
 *   <li>Outside parts of the region's routes, built only where every building they join
 *       declared its end: a service passage's corridor where both cross halls have their
 *       service doors, a fire escape's ladder and the rail cage round its head where its
 *       building opened the parapet gap, and a
 *       bridge's deck and rails where both roofs opened their parapets.</li>
 * </ul>
 * Work is bounded: 256 columns of at most a dozen cells, plus the region's few dozen routes.
 */
public final class UrbanRouteStamper {
    private static final int TOP = UrbanDatum.STREET_Y + 3;
    private static final int B1_SLAB = UrbanDatum.slabY(-1);

    private UrbanRouteStamper() { }

    public static void stamp(Chunk chunk, DistrictPlan district, UrbanWorldState urban) {
        int x0 = chunk.cx * Chunk.SX, z0 = chunk.cz * Chunk.SZ;
        if (TransitNetwork.touches(chunk.cx, chunk.cz)) {
            for (int lz = 0; lz < Chunk.SZ; lz++) {
                for (int lx = 0; lx < Chunk.SX; lx++) {
                    for (int y = TransitNetwork.SLAB_Y; y <= TOP; y++) {
                        BlockType block = TransitNetwork.blockAt(urban::segment, x0 + lx, y, z0 + lz);
                        if (block != null) { chunk.set(lx, y, lz, block); }
                    }
                }
            }
        }
        RouteCatalogue routes = urban.routes(district.rx, district.rz);
        boolean flooded = district.district == UrbanDistrict.FLOODED;
        for (RouteCatalogue.Corridor c : routes.corridors()) {
            if (!overlaps(chunk, c.x0(), c.z0() - 1, c.x1(), c.z1() + 1)
                    || !declaresDoor(urban, c.west()) || !declaresDoor(urban, c.east())) { continue; }
            for (int x = c.x0(); x <= c.x1(); x++) {
                for (int z = c.z0() - 1; z <= c.z1() + 1; z++) {
                    boolean wall = z < c.z0() || z > c.z1();
                    set(chunk, x, B1_SLAB, z, BlockType.CONCRETE);
                    for (int y = B1_SLAB + 1; y < UrbanDatum.STREET_Y; y++) {
                        set(chunk, x, y, z, wall ? BlockType.CONCRETE
                                : y == B1_SLAB + 1 && flooded ? BlockType.WATER : BlockType.AIR);
                    }
                }
            }
        }
        for (RouteCatalogue.FireEscape f : routes.fireEscapes()) {
            Vec3i top = f.ladderTop(), gap = f.gap();
            if (!overlaps(chunk, top.x() - 1, top.z() - 1, top.x() + 1, top.z() + 1) || !declaresRoute(urban, gap)) { continue; }
            for (int y = UrbanDatum.STREET_Y + 1; y <= top.y(); y++) { set(chunk, top.x(), y, top.z(), BlockType.LADDER); }
            // A rail cage round the ladder head: the ladder is climbed up, and a body that steps onto
            // it from the roof hangs there (the controller has no descent), so its three open sides
            // are railed two courses high and the only way off is back through the gap.
            int dx = gap.x() - top.x(), dz = gap.z() - top.z();
            int[][] sides = {{-dx, -dz}, {dz, dx}, {-dz, -dx}};
            for (int[] s : sides) {
                for (int h = 0; h < 2; h++) { set(chunk, top.x() + s[0], top.y() + h, top.z() + s[1], BlockType.RAILING); }
            }
        }
        for (RouteCatalogue.Connector c : routes.connectors()) {
            if (!c.bridge() || !overlaps(chunk, c.x0(), c.z0() - 1, c.x1(), c.z1() + 1)
                    || !declaresRoute(urban, c.a().first()) || !declaresRoute(urban, c.b().first())) { continue; }
            for (int x = c.x0(); x <= c.x1(); x++) {
                for (int z = c.z0() - 1; z <= c.z1() + 1; z++) {
                    boolean rail = z < c.z0() || z > c.z1();
                    set(chunk, x, c.deckY(), z, BlockType.PLANK);
                    set(chunk, x, c.deckY() + 1, z, rail ? BlockType.RAILING : BlockType.AIR);
                    set(chunk, x, c.deckY() + 2, z, rail ? BlockType.RAILING : BlockType.AIR);
                }
            }
        }
    }

    /** The building at a service door's wall declared that door: the corridor has two ends. */
    static boolean declaresDoor(UrbanWorldState urban, RouteCatalogue.ServiceDoor door) {
        BuildingPlan plan = urban.buildingAt(door.first().x(), door.first().z());
        if (plan == null) { return false; }
        for (BuildingPlan.Fixture f : plan.fixtures()) {
            if (f.kind() == BuildingPlan.FixtureKind.DOOR && f.position().equals(door.first())) { return true; }
        }
        return false;
    }

    /** The building whose parapet holds a cell declared a route head there. */
    static boolean declaresRoute(UrbanWorldState urban, Vec3i cell) {
        BuildingPlan plan = urban.buildingAt(cell.x(), cell.z());
        if (plan == null) { return false; }
        for (BuildingPlan.Fixture f : plan.fixtures()) {
            if (f.kind() == BuildingPlan.FixtureKind.ROUTE && f.position().equals(cell)) { return true; }
        }
        return false;
    }

    private static boolean overlaps(Chunk chunk, int x0, int z0, int x1, int z1) {
        int cx0 = chunk.cx * Chunk.SX, cz0 = chunk.cz * Chunk.SZ;
        return x0 < cx0 + Chunk.SX && x1 >= cx0 && z0 < cz0 + Chunk.SZ && z1 >= cz0;
    }

    private static boolean inside(Chunk chunk, int x, int z) {
        return Math.floorDiv(x, Chunk.SX) == chunk.cx && Math.floorDiv(z, Chunk.SZ) == chunk.cz;
    }

    private static void set(Chunk chunk, int x, int y, int z, BlockType block) {
        if (inside(chunk, x, z)) { chunk.set(Math.floorMod(x, Chunk.SX), y, Math.floorMod(z, Chunk.SZ), block); }
    }
}
