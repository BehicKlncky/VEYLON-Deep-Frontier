package com.veylon.ai;

import com.veylon.entity.Entity;

/** Shared movement helpers for creatures and NPCs. */
public final class Steering {

    private Steering() {
    }

    /** Walks the entity toward (tx, tz), jumping over single-block obstacles. */
    public static void moveToward(Entity e, float tx, float tz, float speed) {
        float dx = tx - e.pos.x;
        float dz = tz - e.pos.z;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 0.05f) {
            e.vel.x = 0;
            e.vel.z = 0;
            return;
        }
        e.vel.x = dx / len * speed;
        e.vel.z = dz / len * speed;
        e.yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
        if (e.horizontalCollision && e.onGround) {
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
