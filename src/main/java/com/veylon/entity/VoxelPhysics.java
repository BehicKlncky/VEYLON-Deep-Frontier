package com.veylon.entity;

import com.veylon.world.BlockType;

/**
 * Allocation-free voxel-body integration shared by ordinary entities and the
 * player movement system. Horizontal movement is sub-stepped to prevent
 * tunnelling; an optional one-block step is attempted from the pre-collision
 * position and accepted only when the complete body and horizontal move fit.
 */
final class VoxelPhysics {

    private static final float MAX_AXIS_SUBSTEP = 0.2f;
    private static final float MIN_REMAINING_MOVE = 1e-7f;
    private static final int HORIZONTAL_HIT = 1;
    private static final int HORIZONTAL_STEPPED = 1 << 1;

    private VoxelPhysics() {
    }

    static void integrate(Entity entity, float dt, boolean gravity, float stepHeight) {
        refreshEnvironment(entity);

        if (gravity) {
            if (entity.onLadder) {
                entity.vel.y = Math.max(entity.vel.y, -1.6f);
                entity.fallDist = 0;
            } else {
                entity.vel.y -= (entity.inWater ? 7f : 26f) * dt;
            }
            if (entity.inWater) {
                entity.vel.y = Math.max(entity.vel.y, -2.2f);
            }
            entity.vel.y = Math.max(entity.vel.y, -52f);
        }

        float drag = entity.inWater ? 0.5f : 1f;
        int moveX = moveHorizontal(entity, 0, entity.vel.x * dt * drag, stepHeight);
        // X and Z are resolved independently for wall sliding, but they share one
        // step allowance. Otherwise a diagonal frame can climb one ledge on X and
        // a second ledge on Z, effectively accepting a two-block step.
        float remainingStep = (moveX & HORIZONTAL_STEPPED) == 0 ? stepHeight : 0f;
        int moveZ = moveHorizontal(entity, 2, entity.vel.z * dt * drag, remainingStep);
        entity.horizontalCollision = ((moveX | moveZ) & HORIZONTAL_HIT) != 0;

        boolean hitY = moveAxis(entity, 1, entity.vel.y * dt);
        if (hitY) {
            if (entity.vel.y < 0) {
                entity.onGround = true;
                if (entity.fallDist > 3.5f && !entity.inWater) {
                    entity.onLanded(entity.fallDist);
                }
                entity.fallDist = 0;
            }
            entity.vel.y = 0;
        } else if (entity.vel.y < -0.01f) {
            entity.onGround = false;
            entity.fallDist += -entity.vel.y * dt;
        } else if (entity.vel.y > 0.01f) {
            entity.onGround = false;
            entity.fallDist = 0;
        }
        if (entity.inWater) {
            entity.fallDist = 0;
        }
    }

    private static void refreshEnvironment(Entity entity) {
        int ex = (int) Math.floor(entity.pos.x);
        int ey = (int) Math.floor(entity.pos.y + entity.height * 0.5f);
        int ez = (int) Math.floor(entity.pos.z);
        BlockType center = entity.world.getBlock(ex, ey, ez);
        BlockType feet = entity.world.getBlock(
                ex, (int) Math.floor(entity.pos.y + 0.1f), ez);
        entity.inWater = center == BlockType.WATER || feet == BlockType.WATER;
        entity.onLadder = center.isClimbable() || feet.isClimbable();
    }

    private static int moveHorizontal(Entity entity, int axis, float distance,
                                      float stepHeight) {
        if (distance == 0) {
            return 0;
        }
        float startX = entity.pos.x;
        float startY = entity.pos.y;
        float startZ = entity.pos.z;
        boolean hit = moveAxis(entity, axis, distance);
        if (!hit || stepHeight <= 0 || !entity.onGround) {
            return hit ? HORIZONTAL_HIT : 0;
        }

        float blockedX = entity.pos.x;
        float blockedZ = entity.pos.z;
        entity.pos.set(startX, startY + stepHeight, startZ);
        if (!entity.collidesAt(entity.pos.x, entity.pos.y, entity.pos.z)
                && !moveAxis(entity, axis, distance)) {
            entity.onGround = true;
            return HORIZONTAL_STEPPED;
        }

        // The step was too tall or led into another obstruction. Keep the
        // original axis-slide result rather than losing valid movement up to it.
        entity.pos.set(blockedX, startY, blockedZ);
        return HORIZONTAL_HIT;
    }

    private static boolean moveAxis(Entity entity, int axis, float distance) {
        if (distance == 0) {
            return false;
        }
        float remaining = distance;
        float sign = Math.signum(distance);
        while (Math.abs(remaining) > MIN_REMAINING_MOVE) {
            float step = sign * Math.min(MAX_AXIS_SUBSTEP, Math.abs(remaining));
            remaining -= step;
            switch (axis) {
                case 0 -> entity.pos.x += step;
                case 1 -> entity.pos.y += step;
                default -> entity.pos.z += step;
            }
            if (entity.collidesAt(entity.pos.x, entity.pos.y, entity.pos.z)) {
                switch (axis) {
                    case 0 -> entity.pos.x -= step;
                    case 1 -> entity.pos.y -= step;
                    default -> entity.pos.z -= step;
                }
                return true;
            }
        }
        return false;
    }
}
