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
    /** Least speed at which a body walks out of a torch or campfire cell, blocks per second. */
    private static final float FLAME_STEP_OUT_SPEED = 1.5f;
    /** A flame cell's four neighbours, in the order a tie is settled. */
    private static final int[][] NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private VoxelPhysics() {
    }

    /**
     * One step of a walking, swimming or low-flying body. A body that keeps
     * out of fires ({@link Entity#keepsOutOfFlames}) treats torch and campfire
     * cells as solid for the step; one that began the step in such a cell (it
     * was set down, spawned or a fire was lit there) instead walks out of it.
     */
    static void integrate(Entity entity, float dt, boolean gravity, float stepHeight) {
        refreshEnvironment(entity);
        entity.flamesBlock = entity.keepsOutOfFlames() && !walkOutOfFlameCell(entity);
        integrateBody(entity, dt, gravity, stepHeight);
        entity.flamesBlock = false;
    }

    /**
     * When a body that keeps out of fires stands in a torch or campfire cell,
     * sets its horizontal velocity toward the nearest of that cell's four
     * neighbours it can stand in, at no less than
     * {@link #FLAME_STEP_OUT_SPEED}, whatever its AI chose. Walled in, it keeps
     * its own velocity. True when it was in such a cell.
     */
    private static boolean walkOutOfFlameCell(Entity e) {
        if (!e.keepsOutOfFlames()) {
            return false;
        }
        long cell = e.flameCell();
        if (cell == Entity.NO_FLAME_CELL) {
            return false;
        }
        int fx = (int) (cell >> 32);
        int fz = (int) cell;
        int y = (int) Math.floor(e.pos.y + 0.01f);
        float bestX = 0f, bestZ = 0f, best = Float.MAX_VALUE;
        for (int[] d : NEIGHBOURS) {
            int nx = fx + d[0], nz = fz + d[1];
            if (!standable(e, nx, y, nz)) {
                continue;
            }
            float tx = nx + 0.5f - e.pos.x, tz = nz + 0.5f - e.pos.z;
            float dist = tx * tx + tz * tz;
            if (dist < best) {
                best = dist;
                bestX = tx;
                bestZ = tz;
            }
        }
        if (best != Float.MAX_VALUE) {
            float len = (float) Math.sqrt(best);
            float speed = Math.max(FLAME_STEP_OUT_SPEED,
                    (float) Math.sqrt(e.vel.x * e.vel.x + e.vel.z * e.vel.z));
            e.vel.x = bestX / len * speed;
            e.vel.z = bestZ / len * speed;
        }
        return true;
    }

    /** Open at the feet and the head, no fire in either, solid underfoot. */
    private static boolean standable(Entity e, int x, int y, int z) {
        BlockType feet = e.world.getBlock(x, y, z);
        BlockType head = e.world.getBlock(x, y + 1, z);
        return !feet.solid && !head.solid && !Entity.isFlameCell(feet) && !Entity.isFlameCell(head)
                && e.world.isSolid(x, y - 1, z);
    }

    private static void integrateBody(Entity entity, float dt, boolean gravity, float stepHeight) {
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

    /**
     * Creative flight (R13, R14): no gravity or step-up and the same voxel
     * collision, plus two walls. A chunk column that is not loaded reads as air,
     * so entering it would skip terrain that has not streamed in; the altitude
     * ceiling blocks only upward movement, so a body above it can still descend.
     *
     * @return true when a descending body touched the ground
     */
    static boolean integrateFlight(Entity entity, float dt, float ceilingY) {
        refreshEnvironment(entity);
        boolean hitX = moveFlightAxis(entity, 0, entity.vel.x * dt, ceilingY);
        boolean hitZ = moveFlightAxis(entity, 2, entity.vel.z * dt, ceilingY);
        entity.horizontalCollision = hitX || hitZ;
        boolean hitY = moveFlightAxis(entity, 1, entity.vel.y * dt, ceilingY);
        boolean landed = hitY && entity.vel.y < 0;
        if (hitY) {
            entity.vel.y = 0;
        }
        entity.onGround = landed;
        entity.fallDist = 0;
        return landed;
    }

    private static boolean moveFlightAxis(Entity entity, int axis, float distance, float ceilingY) {
        if (distance == 0) {
            return false;
        }
        float remaining = distance;
        float sign = Math.signum(distance);
        while (Math.abs(remaining) > MIN_REMAINING_MOVE) {
            float step = sign * Math.min(MAX_AXIS_SUBSTEP, Math.abs(remaining));
            remaining -= step;
            offset(entity, axis, step);
            if (entity.collidesAt(entity.pos.x, entity.pos.y, entity.pos.z)
                    || flightBoundary(entity, axis, step, ceilingY)) {
                offset(entity, axis, -step);
                return true;
            }
        }
        return false;
    }

    private static void offset(Entity entity, int axis, float step) {
        switch (axis) {
            case 0 -> entity.pos.x += step;
            case 1 -> entity.pos.y += step;
            default -> entity.pos.z += step;
        }
    }

    /** The ceiling stops rising; an unloaded column under any body corner stops horizontal moves. */
    private static boolean flightBoundary(Entity entity, int axis, float step, float ceilingY) {
        if (axis == 1) {
            return step > 0 && entity.pos.y > ceilingY;
        }
        float hw = entity.width / 2f;
        return !columnLoaded(entity, entity.pos.x - hw, entity.pos.z - hw)
                || !columnLoaded(entity, entity.pos.x + hw, entity.pos.z - hw)
                || !columnLoaded(entity, entity.pos.x - hw, entity.pos.z + hw)
                || !columnLoaded(entity, entity.pos.x + hw, entity.pos.z + hw);
    }

    private static boolean columnLoaded(Entity entity, float x, float z) {
        return entity.world.getChunk(
                Math.floorDiv((int) Math.floor(x), com.veylon.world.Chunk.SX),
                Math.floorDiv((int) Math.floor(z), com.veylon.world.Chunk.SZ)) != null;
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
