package com.veylon;

import com.veylon.world.BlockType;
import org.joml.Vector3f;

/** Player death and respawn rules; Game retains the app-state loop and command delegates. */
final class PlayerLifeCycle {
    /**
     * Where the camera watches a lethal blast's remains from while the death card shows (0.9.1
     * B13): this far back along the heading and this far up from the eye, or as much of the way
     * there as is clear of blocks.
     */
    static final float VIEW_BACK = 2.4f, VIEW_UP = 1.3f;
    /** How far below the eye that view is aimed: about the middle of the body as it died. */
    static final float VIEW_AIM_DROP = 0.8f;
    /** The steps the way out to the view is checked in, and the room kept round the camera. */
    private static final int VIEW_STEPS = 24;
    private static final float VIEW_CLEARANCE = 0.2f;
    /** How far from the eye a view must get to be clear of the remains' head piece. */
    static final float VIEW_MIN_DISTANCE = 1f;

    private final Game game;
    private final Vector3f view = new Vector3f();
    private boolean watchingRemains;
    private float viewYaw, viewPitch, yawAtDeath, pitchAtDeath;

    PlayerLifeCycle(Game game) { this.game = game; }

    /** The one death transition, including a lethal blast's player remains. */
    void enterDeathIfDue() {
        var player = game.player;
        if (player.dead && !player.abilities.invulnerable() && game.appState == Game.AppState.PLAYING) {
            game.appState = Game.AppState.DEATH;
            game.deathTimer = 3f;
            watchingRemains = !game.fragments.spawnPlayerRemains(game, player).isEmpty();
            if (watchingRemains) {
                chooseView();
            }
            game.closeScreens();
        }
    }

    /**
     * Puts the camera where this frame sees from: the player's eye, or, while the death card shows
     * over the remains a lethal blast left, the clear view of them chosen at the death. The eye is
     * inside the remains' head piece, which the card used to look out of (0.9.1 B13).
     */
    void placeCamera() {
        var p = game.player;
        if (watchingRemains && game.appState == Game.AppState.DEATH) {
            game.camera.position.set(view);
            game.camera.yaw = viewYaw;
            game.camera.pitch = viewPitch;
        } else {
            game.camera.position.set(p.pos.x, p.pos.y + p.eyeHeight(), p.pos.z);
        }
    }

    /**
     * While the death card shows over a lethal blast's remains, the world stays paused but the
     * bodies the blast made fall and come to rest, with the blood they shed (B13); they used to
     * hang where they were thrown from until the player woke.
     */
    void settleRemains(float dt) {
        if (watchingRemains && game.appState == Game.AppState.DEATH && !game.simPaused) {
            game.particles.update(dt, game.world);
            game.ragdolls.update(game, dt);
            game.fragments.update(game, dt);
        }
    }

    /** Back and up from the eye, as far as it is clear, aimed at the body as it died. */
    private void chooseView() {
        var p = game.player;
        float ex = p.pos.x, ey = p.pos.y + p.eyeHeight(), ez = p.pos.z;
        yawAtDeath = game.camera.yaw;
        pitchAtDeath = game.camera.pitch;
        double heading = Math.toRadians(yawAtDeath);
        float bx = (float) -Math.sin(heading) * VIEW_BACK, bz = (float) Math.cos(heading) * VIEW_BACK;
        float back = walkOut(ex, ey, ez, bx, VIEW_UP, bz);
        if (back < VIEW_MIN_DISTANCE) {
            // Walled in behind: straight up, unless that gets no further.
            float upX = view.x, upY = view.y, upZ = view.z;
            if (walkOut(ex, ey, ez, 0f, VIEW_UP * 1.5f, 0f) < back) {
                view.set(upX, upY, upZ);
            }
        }
        float dx = ex - view.x, dy = ey - VIEW_AIM_DROP - view.y, dz = ez - view.z;
        float flat = (float) Math.sqrt(dx * dx + dz * dz);
        viewYaw = flat < 1e-3f ? yawAtDeath : (float) Math.toDegrees(Math.atan2(dx, -dz));
        viewPitch = (float) Math.toDegrees(Math.atan2(-dy, Math.max(flat, 1e-3f)));
    }

    /**
     * Puts {@link #view} at the eye, then moves it out along the offset for as long as it stays
     * clear; returns how far from the eye it got.
     */
    private float walkOut(float ex, float ey, float ez, float ox, float oy, float oz) {
        view.set(ex, ey, ez);
        for (int i = 1; i <= VIEW_STEPS; i++) {
            float t = i / (float) VIEW_STEPS;
            float x = ex + ox * t, y = ey + oy * t, z = ez + oz * t;
            if (!clear(x, y, z)) {
                break;
            }
            view.set(x, y, z);
        }
        return view.distance(ex, ey, ez);
    }

    /** Whether a small box round the point is free of every block that stops or hides the view. */
    private boolean clear(float x, float y, float z) {
        for (int corner = 0; corner < 8; corner++) {
            float cx = x + ((corner & 1) == 0 ? -VIEW_CLEARANCE : VIEW_CLEARANCE);
            float cy = y + ((corner & 2) == 0 ? -VIEW_CLEARANCE : VIEW_CLEARANCE);
            float cz = z + ((corner & 4) == 0 ? -VIEW_CLEARANCE : VIEW_CLEARANCE);
            BlockType t = game.world.getBlock((int) Math.floor(cx), (int) Math.floor(cy), (int) Math.floor(cz));
            if (t.solid || t.opaque) {
                return false;
            }
        }
        return true;
    }

    void respawn() {
        var player = game.player;
        if (player.abilities.invulnerable()) {
            player.restoreCreativeBody();
            return;
        }
        if (game.world.urban != null && game.world.urban.hasStarter()) {
            if (!UrbanBootstrap.restoreRespawn(game)) { return; }
            game.log("You return to the city refuge. Your carried supplies remain with you.");
        } else {
            game.log("You died. The frontier reclaims you... (respawned at the crash site)");
        }
        player.dead = false;
        player.clearBlastDeath();
        game.combustion.clear(player);
        player.health = 55;
        player.hunger = Math.max(player.hunger, 50);
        player.thirst = Math.max(player.thirst, 50);
        player.bodyTemp = 37;
        player.afflictions.clear();
        player.smokeExposure = 0;
        player.protein = Math.max(player.protein, 40);
        player.vitamins = Math.max(player.vitamins, 40);
        player.vel.set(0, 0, 0);
        player.pos.set(game.spawnPos);
        game.sleeping = false;
        if (watchingRemains) {
            watchingRemains = false; // the survivor wakes looking the way they looked when they died
            game.camera.yaw = yawAtDeath;
            game.camera.pitch = pitchAtDeath;
        }
    }
}
