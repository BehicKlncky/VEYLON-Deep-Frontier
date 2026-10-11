package com.veylon;

import com.veylon.world.World;
import com.veylon.world.urban.UrbanWorldState;
import com.veylon.world.urban.UrbanStarterSite;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.VoxelClearance;
import com.veylon.util.Vec3i;

/**
 * The urban branch of {@link WorldBootstrap}.
 *
 * <p>An urban world gets no crash camp, no camp NPCs and no camp faction
 * track: {@code setupCamp} flattens a pad from the column top, which in a city
 * is a roof (decision D-22). The spawn comes from the plan, never from the
 * column top: generator 9 uses a supported ground-floor bedroom and saved
 * refuge anchor. Earlier street networks keep their courtyard walk and the
 * bare datum of generator 4 keeps its fixed street column.
 */
final class UrbanBootstrap {

    /** Shown instead of the camp hint on the bare street datum (generator 4). */
    static final String DATUM_NOTICE =
            "Urban development world: placeholder streets only, no city yet.";
    /** Shown instead of the camp hint on the street network (generator 5). */
    static final String STREETS_NOTICE =
            "Urban development world: streets and reserved lots only; no buildings yet.";
    /** Furnished geometry is explorable; contextual loot and the refuge loop follow later. */
    static final String APARTMENTS_NOTICE =
            "Urban development world: explorable apartments and shops; contextual supplies follow later.";

    /** Chunks generated around the spawn before the first frame. */
    private static final int SPAWN_RADIUS_CHUNKS = 5;
    /** Camera yaw that looks east along the courtyard from the refuge door. */
    private static final float SPAWN_YAW = 90;

    private UrbanBootstrap() {
    }

    /** Generates around the spawn and stands the player on the planned ground. */
    static void placePlayerOnStreet(Game game, boolean fresh) {
        World world = game.world;
        UrbanWorldState urban = world.urban;
        int[] column = urban.spawnColumn();
        world.ensureChunks(column[0], column[1], SPAWN_RADIUS_CHUNKS, 10_000);
        if (urban.hasStarter()) {
            var site = UrbanStarterSite.of(urban);
            Vec3i anchor = site.refuge().fixtures().stream()
                    .filter(f -> f.position().y() == 41 && f.block() == com.veylon.world.BlockType.CAMP_BED)
                    .map(BuildingPlan.Fixture::target).findFirst().orElseThrow();
            if (!safe(world, anchor)) { throw new IllegalStateException("unsafe planned starter refuge"); }
            game.spawnPos.set(anchor.x() + .5f, anchor.y() + .05f, anchor.z() + .5f);
            game.player.pos.set(game.spawnPos);
            game.camera.yaw = 90; game.camera.pitch = 4;
            if (fresh) {
                urban.refugeBuilding = site.refuge().id(); urban.refugeAnchor = anchor;
                world.campfireFuel.put(site.fire(), 240f);
                world.collectorWater.put(site.collector(), 3f);
            }
            return;
        }
        // Same clearance as the legacy dry-land spawn: feet 0.2 above the ground.
        game.spawnPos.set(column[0] + 0.5f, urban.groundY(column[0], column[1]) + 1.2f, column[1] + 0.5f);
        game.player.pos.set(game.spawnPos);
        game.camera.yaw = urban.hasCityPlan() ? SPAWN_YAW : 35;
        game.camera.pitch = 8;
    }

    static boolean safe(World world, Vec3i p) {
        return p.y() == 41 && world.isColumnLoaded(p.x(), p.z())
                && world.getBlock(p.x(), p.y() - 1, p.z()).solid
                && !VoxelClearance.collides(world, p.x() + .5f, p.y() + .05f, p.z() + .5f, .6f, 1.8f, true);
    }

    /** Only loaded street/ground-floor cells, bounded to 4,225 candidates, never roof-height search. */
    static boolean restoreRespawn(Game game) {
        var urban = game.world.urban;
        if (urban == null || !urban.hasStarter()) { return false; }
        Vec3i anchor = urban.refugeAnchor;
        if (anchor == null) {
            int[] column = urban.spawnColumn();
            anchor = new Vec3i(column[0], 41, column[1]);
        }
        game.world.ensureChunks(anchor.x(), anchor.z(), 2, 10_000);
        for (int r = 0; r <= 32; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) { continue; }
                    Vec3i p = anchor.offset(dx, 0, dz);
                    if (safe(game.world, p)) {
                        game.spawnPos.set(p.x() + .5f, p.y() + .05f, p.z() + .5f);
                        return true;
                    }
                }
            }
        }
        game.log("No safe loaded ground-floor respawn remains near the refuge.");
        return false;
    }

    static String developmentNotice(World world) {
        if (world.urban.hasGeneratedBuildings()) { return APARTMENTS_NOTICE; }
        return world.urban.hasCityPlan() ? STREETS_NOTICE : DATUM_NOTICE;
    }
}
