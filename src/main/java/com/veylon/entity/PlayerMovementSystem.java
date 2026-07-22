package com.veylon.entity;

import com.veylon.world.World;

/**
 * Interprets gameplay movement commands and advances player voxel physics.
 * It depends only on player/world state, so collision, swimming and ladder
 * behavior can be tested without GLFW, OpenGL or OpenAL.
 */
public final class PlayerMovementSystem {

    public static final float CROUCH_SPEED = 2.1f;
    public static final float SPRINT_SPEED = 6.7f;
    public static final float WALK_SPEED = 4.3f;
    public static final float STEP_HEIGHT = 1f;
    public static final float SWIM_ASCEND_SPEED = 3f;
    public static final float LADDER_ASCEND_SPEED = 2.6f;
    public static final float LADDER_FORWARD_SPEED = 2.2f;

    /** Caller-owned command buffer; reuse it for allocation-free frame updates. */
    public static final class Command {
        public float yawDegrees;
        public boolean forward;
        public boolean backward;
        public boolean left;
        public boolean right;
        public boolean sprintHeld;
        public boolean crouchHeld;
        public boolean jumpHeld;
        public boolean jumpPressed;

        public Command set(float yawDegrees, boolean forward, boolean backward,
                           boolean left, boolean right, boolean sprintHeld,
                           boolean crouchHeld, boolean jumpHeld, boolean jumpPressed) {
            this.yawDegrees = yawDegrees;
            this.forward = forward;
            this.backward = backward;
            this.left = left;
            this.right = right;
            this.sprintHeld = sprintHeld;
            this.crouchHeld = crouchHeld;
            this.jumpHeld = jumpHeld;
            this.jumpPressed = jumpPressed;
            return this;
        }
    }

    /** Caller-owned observations used by presentation/audio orchestration. */
    public static final class FrameResult {
        public boolean horizontalIntent;
        public boolean enteredWater;
    }

    /** Advances one player movement frame without allocating temporary objects. */
    public void update(Player player, World world, Command command, float dt,
                       FrameResult result) {
        if (player == null || world == null || command == null || result == null) {
            throw new IllegalArgumentException("movement arguments must be non-null");
        }
        if (player.world != world) {
            throw new IllegalArgumentException("player belongs to a different world");
        }
        player.crouching = command.crouchHeld;
        boolean wantsSprint = command.sprintHeld && command.forward && !player.crouching;
        player.sprinting = wantsSprint && player.canSprint();

        float speed = (player.crouching ? CROUCH_SPEED
                : player.sprinting ? SPRINT_SPEED : WALK_SPEED) * player.moveSpeedMul();
        float yaw = (float) Math.toRadians(command.yawDegrees);
        float forwardX = (float) Math.sin(yaw);
        float forwardZ = -(float) Math.cos(yaw);
        float rightX = (float) Math.cos(yaw);
        float rightZ = (float) Math.sin(yaw);
        float moveX = 0;
        float moveZ = 0;
        if (command.forward) {
            moveX += forwardX;
            moveZ += forwardZ;
        }
        if (command.backward) {
            moveX -= forwardX;
            moveZ -= forwardZ;
        }
        if (command.right) {
            moveX += rightX;
            moveZ += rightZ;
        }
        if (command.left) {
            moveX -= rightX;
            moveZ -= rightZ;
        }
        float length = (float) Math.sqrt(moveX * moveX + moveZ * moveZ);
        result.horizontalIntent = length > 0.01f;
        if (result.horizontalIntent) {
            player.vel.x = moveX / length * speed;
            player.vel.z = moveZ / length * speed;
        } else {
            player.vel.x = 0;
            player.vel.z = 0;
        }

        if (player.sprinting) {
            player.stamina = Math.max(0, player.stamina - 9f * dt);
            player.noise = Math.min(1f, player.noise + 0.8f * dt);
        }

        if (command.jumpHeld) {
            if (player.inWater) {
                player.vel.y = Math.max(player.vel.y, SWIM_ASCEND_SPEED);
            } else if (!applyClimbCommand(player, true, command.forward)
                    && player.onGround && player.stamina >= 3 && command.jumpPressed) {
                player.vel.y = player.has(Affliction.SPRAIN) ? 6.2f : 8.2f;
                player.stamina -= 3;
                player.onGround = false;
            }
        } else {
            applyClimbCommand(player, false, command.forward);
        }

        boolean wasInWater = player.inWater;
        VoxelPhysics.integrate(player, dt, true, STEP_HEIGHT);
        result.enteredWater = !wasInWater && player.inWater;
    }

    /** Applies rope-ladder intent using the same rules as the native input path. */
    public boolean applyClimbCommand(Player player, boolean ascendHeld,
                                     boolean forwardHeld) {
        if (player == null || !player.onLadder) {
            return false;
        }
        if (ascendHeld) {
            player.vel.y = LADDER_ASCEND_SPEED;
            return true;
        }
        if (forwardHeld && player.horizontalCollision) {
            player.vel.y = LADDER_FORWARD_SPEED;
            return true;
        }
        return false;
    }
}
