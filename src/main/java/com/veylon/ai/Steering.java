package com.veylon.ai;

import com.veylon.entity.Entity;
import com.veylon.simulation.FireSystem;

/** Shared movement helpers for creatures and NPCs. */
public final class Steering {

    /** How far ahead a walker looks for a torch or campfire to step round. */
    private static final float FLAME_LOOKAHEAD = 0.45f;

    private Steering() {
    }

    /**
     * Walks the entity toward (tx, tz), jumping over single-block obstacles
     * and stepping round a torch or campfire, whose cell people and animals
     * keep out of: when the way ahead holds one, it walks sideways, away from
     * the fire's side, until the way is clear, rather than bumping into it and
     * hopping over.
     */
    public static void moveToward(Entity e, float tx, float tz, float speed) {
        float dx = tx - e.pos.x;
        float dz = tz - e.pos.z;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 0.05f) {
            e.vel.x = 0;
            e.vel.z = 0;
            return;
        }
        float ux = dx / len;
        float uz = dz / len;
        long flame = FireSystem.standingFlameAhead(e.world(), e, ux * FLAME_LOOKAHEAD, uz * FLAME_LOOKAHEAD);
        if (flame != FireSystem.NO_FLAME) {
            // Turn a quarter away from the side the fire is on.
            float fx = (int) (flame >> 32) + 0.5f - e.pos.x;
            float fz = (int) flame + 0.5f - e.pos.z;
            float side = ux * fz - uz * fx >= 0f ? -1f : 1f;
            float sx = -uz * side;
            float sz = ux * side;
            if (FireSystem.standingFlameAhead(e.world(), e, sx * FLAME_LOOKAHEAD, sz * FLAME_LOOKAHEAD)
                    != FireSystem.NO_FLAME) {
                sx = -sx;
                sz = -sz;
            }
            ux = sx;
            uz = sz;
        }
        e.vel.x = ux * speed;
        e.vel.z = uz * speed;
        e.yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
        if (e.onLadder && e.horizontalCollision) {
            e.vel.y = 2.4f;
        } else if (e.horizontalCollision && e.onGround) {
            e.vel.y = 7.4f;
        }
        if (e.inWater) {
            e.vel.y = Math.max(e.vel.y, 2.2f);
        }
    }

    public static void stop(Entity e) {
        e.vel.x = 0;
        e.vel.z = 0;
    }

    /** Direct 3D steering for flying creatures. */
    public static void flyToward(Entity e, float tx, float ty, float tz, float speed) {
        float dx = tx - e.pos.x;
        float dy = ty - e.pos.y;
        float dz = tz - e.pos.z;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.2f) {
            e.vel.set(0, 0, 0);
            return;
        }
        e.vel.set(dx / len * speed, dy / len * speed, dz / len * speed);
        e.yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
    }
}
