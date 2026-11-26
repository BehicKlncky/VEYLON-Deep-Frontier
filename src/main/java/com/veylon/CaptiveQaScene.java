package com.veylon;

import com.veylon.entity.CombustionSource;
import com.veylon.entity.Npc;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.simulation.WeatherSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;

import java.util.Locale;

/**
 * Opt-in capture scene for a generated settlement's captive (0.9.1 B03). Inert in an ordinary
 * world.
 *
 * <p>{@code fort_captive} finds the nearest generated hostile settlement that keeps a captive in
 * its cage and puts the player outside the cage, on the side that has room, looking in through a
 * window of two broken bars (the bars are opaque blocks). The settlement's own slow tick then
 * brings its people to life, so the captive stands wherever the activation puts it. At
 * {@link #IGNITE_AT} the captive is set alight (made too sturdy to die), and the fire panic should
 * make it struggle round its cage. The scene needs a Creative player
 * ({@code VEYLON_GAME_MODE=creative}), whom no guard perceives, so the garrison stays calm.
 *
 * <p>The console reports the captive's feet and head against the cage floor and roof, whether its
 * body overlaps a block, and how far it has moved, ending with {@code pass=true} when it stood clear
 * of the roof and moved.
 */
final class CaptiveQaScene {

    /** Seconds into the scene the captive is set alight. */
    private static final double IGNITE_AT = 2.0;
    /** Seconds into the scene the first and the final reports are printed. */
    private static final double FIRST_REPORT = 1.0, FINAL_REPORT = 6.0;
    /** Planned regions searched around the origin for a hostile settlement with a captive. */
    private static final int SEARCH_REGIONS = 12;
    private static final float STURDY = 1_000f;
    /** The camera's distance from the cage centre, its feet above the cage floor and its pitch. */
    private static final float CAMERA_BACK = 4.2f, CAMERA_RISE = 0.3f, CAMERA_PITCH = 10f;

    private final Game game;
    private boolean active;
    private boolean ignited;
    private boolean reported;
    private boolean finished;
    private boolean passed;
    private Settlement fort;
    private Npc captive;
    private float path;
    private float lastX, lastZ;

    CaptiveQaScene(Game game) {
        this.game = game;
    }

    void stage() {
        if (game.player.abilities.perceivableByAi()) {
            throw new IllegalStateException("fort_captive needs VEYLON_GAME_MODE=creative, so the garrison stays calm");
        }
        fort = nearestCaptiveSettlement();
        if (fort == null) {
            throw new IllegalStateException("fort_captive: no hostile settlement with a caged captive within "
                    + SEARCH_REGIONS + " regions");
        }
        game.time.totalMinutes = 12 * 60;
        game.weather.current = WeatherSystem.Weather.CLEAR;
        game.weather.next = WeatherSystem.Weather.CLEAR;
        game.weather.blend = 1f;
        game.weather.changeTimer = 4000f;

        Vec3i floor = fort.prisonPos; // the cage's inner corner cell, on its floor
        float cx = floor.x() + 1f, cz = floor.z() + 1f; // the cage's centre
        game.world.ensureChunks((int) cx, (int) cz, 4, 10_000);
        float[][] sides = {{0, 1, 0}, {1, 0, -90}, {-1, 0, 90}, {0, -1, 180}}; // dx, dz, yaw looking back
        float[] side = sides[0];
        for (float[] s : sides) {
            if (clearView(cx, cz, floor.y(), s)) {
                side = s;
                break;
            }
        }
        // The bars are opaque blocks, so the camera looks in through a window: the two bars of
        // that side at the captive's height, as a player who breaks them to free it would.
        for (int along = 0; along <= 1; along++) {
            int x = side[0] > 0 ? floor.x() + 2 : side[0] < 0 ? floor.x() - 1 : floor.x() + along;
            int z = side[1] > 0 ? floor.z() + 2 : side[1] < 0 ? floor.z() - 1 : floor.z() + along;
            for (int y = floor.y(); y <= floor.y() + 1; y++) {
                game.world.setBlock(x, y, z, BlockType.AIR, true);
            }
        }
        game.player.abilities.setFlying(true);
        game.player.hotbarSel = 8; // an empty hand keeps the view clear
        game.player.inventory.set(8, null);
        game.player.pos.set(cx + side[0] * CAMERA_BACK, floor.y() + CAMERA_RISE, cz + side[1] * CAMERA_BACK);
        game.player.vel.zero();
        game.camera.yaw = side[2];
        game.camera.pitch = CAMERA_PITCH;

        game.settlementManager.slowTick(game, 1f); // the ordinary activation brings the garrison
        for (Settlement.Resident r : fort.residents) {
            if (r.archetype == NpcArchetype.CAPTIVE && r.live != null) {
                captive = r.live;
            }
        }
        if (captive == null) {
            throw new IllegalStateException("fort_captive: " + fort.label() + " materialized no captive");
        }
        lastX = captive.pos.x;
        lastZ = captive.pos.z;
        System.out.printf(Locale.ROOT, "[captive] %s: cage floor y=%d, roof y=%d, camera yaw %.0f%n",
                fort.label(), floor.y(), floor.y() + 2, side[2]);
        active = true;
    }

    void update(double elapsed) {
        if (!active || finished) {
            return;
        }
        path += (float) Math.hypot(captive.pos.x - lastX, captive.pos.z - lastZ);
        lastX = captive.pos.x;
        lastZ = captive.pos.z;
        if (!reported && elapsed >= FIRST_REPORT) {
            reported = true;
            report("standing");
        }
        if (!ignited && elapsed >= IGNITE_AT) {
            ignited = true;
            float wound = captive.maxHealth - captive.health;
            captive.maxHealth = STURDY;
            captive.health = STURDY - wound;
            game.combustion.ignite(game, captive, CombustionSource.DIRECT_HIT, 1f, false, 1,
                    captive.pos.x - 0.2f, captive.pos.y + 1f, captive.pos.z);
            path = 0f;
            System.out.printf(Locale.ROOT, "[captive] alight at %.1f s%n", elapsed);
        }
        if (elapsed >= FINAL_REPORT) {
            finished = true;
            boolean clear = report("burning");
            passed = clear && path > 0.3f;
            System.out.printf(Locale.ROOT, "[captive] fort_captive pass=%b%n", passed);
        }
    }

    /** The captive the settlement's activation put in its cage; null before {@link #stage}. */
    Npc captive() {
        return captive;
    }

    /** The settlement whose cage the scene looks into; null before {@link #stage}. */
    Settlement settlement() {
        return fort;
    }

    /** Whether the final report found the captive clear of every block and moving; false before it. */
    boolean passed() {
        return passed;
    }

    /** Prints where the captive is against its cage; true when it stands clear of every block. */
    private boolean report(String moment) {
        int roof = fort.prisonPos.y() + 2;
        boolean overlapping = captive.collidesAt(captive.pos.x, captive.pos.y, captive.pos.z);
        System.out.printf(Locale.ROOT, "[captive] %s: feet y=%.2f head y=%.2f roof y=%d overlapping=%b moved=%.2f m panic=%b%n",
                moment, captive.pos.y, captive.pos.y + captive.height, roof, overlapping, path, captive.panic.active());
        return !overlapping && captive.pos.y + captive.height < roof;
    }

    /** Whether the line from the cage's side out to the camera is open at the captive's height. */
    private boolean clearView(float cx, float cz, int floorY, float[] side) {
        for (float d = 2.2f; d <= CAMERA_BACK + 0.4f; d += 0.5f) {
            int x = (int) Math.floor(cx + side[0] * d), z = (int) Math.floor(cz + side[1] * d);
            for (int y = floorY; y <= floorY + 2; y++) {
                BlockType t = game.world.getBlock(x, y, z);
                if (t.solid || t.opaque) {
                    return false;
                }
            }
        }
        return true;
    }

    private Settlement nearestCaptiveSettlement() {
        Settlement best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int rx = -SEARCH_REGIONS; rx <= SEARCH_REGIONS; rx++) {
            for (int rz = -SEARCH_REGIONS; rz <= SEARCH_REGIONS; rz++) {
                Settlement s = game.world.settlementForRegion(rx, rz);
                if (s == null || !s.hostile() || s.residents.stream().noneMatch(
                        r -> r.archetype == NpcArchetype.CAPTIVE && r.alive && !r.rescued)) {
                    continue;
                }
                game.world.layoutFor(s);
                double distance = s.distSqTo(game.player.pos.x, game.player.pos.z);
                if (s.prisonPos != null && distance < bestDistance) {
                    bestDistance = distance;
                    best = s;
                }
            }
        }
        return best;
    }
}
