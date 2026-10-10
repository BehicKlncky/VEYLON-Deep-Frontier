package com.veylon;

import com.veylon.world.World;
import com.veylon.world.urban.UrbanWorldState;

/**
 * The urban branch of {@link WorldBootstrap}.
 *
 * <p>An urban world gets no crash camp, no camp NPCs and no camp faction
 * track: {@code setupCamp} flattens a pad from the column top, which in a city
 * is a roof (decision D-22). The spawn comes from the plan, never from the
 * column top: on the street network it is the courtyard walk in front of the
 * starter refuge's door, and on the bare datum of generator 4 the fixed street
 * column it always was. Package 08 turns the reserved refuge into the real
 * starter refuge and respawn anchor.
 */
final class UrbanBootstrap {

    /** Shown instead of the camp hint on the bare street datum (generator 4). */
    static final String DATUM_NOTICE =
            "Urban development world: placeholder streets only, no city yet.";
    /** Shown instead of the camp hint on the street network (generator 5). */
    static final String STREETS_NOTICE =
            "Urban development world: streets and reserved lots only; no buildings yet.";

    /** Chunks generated around the spawn before the first frame. */
    private static final int SPAWN_RADIUS_CHUNKS = 5;
    /** Camera yaw that looks east along the courtyard from the refuge door. */
    private static final float SPAWN_YAW = 90;

    private UrbanBootstrap() {
    }

    /** Generates around the spawn and stands the player on the planned ground. */
    static void placePlayerOnStreet(Game game) {
        World world = game.world;
        UrbanWorldState urban = world.urban;
        int[] column = urban.spawnColumn();
        world.ensureChunks(column[0], column[1], SPAWN_RADIUS_CHUNKS, 10_000);
        // Same clearance as the legacy dry-land spawn: feet 0.2 above the ground.
        game.spawnPos.set(column[0] + 0.5f, urban.groundY(column[0], column[1]) + 1.2f, column[1] + 0.5f);
        game.player.pos.set(game.spawnPos);
        game.camera.yaw = urban.hasCityPlan() ? SPAWN_YAW : 35;
        game.camera.pitch = 8;
    }

    static String developmentNotice(World world) {
        return world.urban.hasCityPlan() ? STREETS_NOTICE : DATUM_NOTICE;
    }
}
