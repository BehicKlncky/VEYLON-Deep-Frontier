package com.veylon.entity;

import com.veylon.world.Chunk;
import com.veylon.world.World;

/**
 * Interprets gameplay movement commands and advances player voxel physics.
 * It depends only on player/world state, so collision, swimming, ladder and
 * Creative flight behavior can be tested without GLFW, OpenGL or OpenAL.
 */
public final class PlayerMovementSystem {

    public static final float CROUCH_SPEED = 2.1f;
    public static final float SPRINT_SPEED = 6.7f;
    public static final float WALK_SPEED = 4.3f;
    public static final float STEP_HEIGHT = 1f;
    public static final float SWIM_ASCEND_SPEED = 3f;
    public static final float LADDER_ASCEND_SPEED = 2.6f;
    public static final float LADDER_FORWARD_SPEED = 2.2f;
    /** R13: a second Space press within this many seconds toggles Creative flight. */
    public static final float FLIGHT_TOGGLE_WINDOW_SECONDS = 0.30f;
    /** R14: cruise speed in blocks per second, chosen against measured chunk streaming. */
    public static final float FLY_SPEED = 10.9f;
    /** R14: Left Shift flight speed, still inside the measured streaming budget. */
    public static final float FLY_FAST_SPEED = 21.6f;
    /** R13: Space rises and Left Ctrl descends at this speed. */
    public static final float FLY_VERTICAL_SPEED = 7.5f;
    /** R14: highest feet position while flying, 32 blocks above the voxel ceiling. */
    public static final float FLIGHT_CEILING_Y = Chunk.SY + 32f;

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
        updateFlightToggle(player, command, dt);
        if (player.abilities.flying()) {
            updateFlight(player, command, dt, result);
            return;
        }
        player.crouching = command.crouchHeld;
        boolean wantsSprint = command.sprintHeld && command.forward && !player.crouching;
        player.sprinting = wantsSprint && player.canSprint();

        float speed = (player.crouching ? CROUCH_SPEED
                : player.sprinting ? SPRINT_SPEED : WALK_SPEED) * player.moveSpeedMul();
        result.horizontalIntent = applyHorizontalIntent(player, command, speed);

        if (player.sprinting) {
            if (!player.abilities.invulnerable()) {
                player.stamina = Math.max(0, player.stamina - 9f * dt);
            }
            player.noise = Math.min(1f, player.noise + 0.8f * dt);
        }

        if (command.jumpHeld) {
            if (player.inWater) {
                player.vel.y = Math.max(player.vel.y, SWIM_ASCEND_SPEED);
            } else if (!applyClimbCommand(player, true, command.forward)
                    && player.onGround && (player.abilities.invulnerable() || player.stamina >= 3)
                    && command.jumpPressed) {
                player.vel.y = !player.abilities.invulnerable() && player.has(Affliction.SPRAIN) ? 6.2f : 8.2f;
                if (!player.abilities.invulnerable()) player.stamina -= 3;
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

    /** Converts WASD intent into horizontal velocity; true when any direction is held. */
    private static boolean applyHorizontalIntent(Player player, Command command, float speed) {
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
        boolean intent = length > 0.01f;
        if (intent) {
            player.vel.x = moveX / length * speed;
            player.vel.z = moveZ / length * speed;
        } else {
            player.vel.x = 0;
            player.vel.z = 0;
        }
        return intent;
    }

    /**
     * R13: measures time since the last Space press and toggles flight on a
     * second press inside the window. A body that may not fly accumulates
     * nothing, so Survival movement is untouched. A toggle consumes both
     * presses, and either transition restarts fall accounting at this height.
     */
    private static void updateFlightToggle(Player player, Command command, float dt) {
        if (!player.abilities.mayFly()) {
            player.secondsSinceJumpTap = Float.POSITIVE_INFINITY;
            return;
        }
        player.secondsSinceJumpTap += dt;
        if (!command.jumpPressed) {
            return;
        }
        if (player.secondsSinceJumpTap <= FLIGHT_TOGGLE_WINDOW_SECONDS) {
            player.abilities.setFlying(!player.abilities.flying());
            player.secondsSinceJumpTap = Float.POSITIVE_INFINITY;
            player.resetFallState();
            player.vel.y = 0;
        } else {
            player.secondsSinceJumpTap = 0;
        }
    }

    /**
     * R13/R14: flight keeps voxel collision but ignores gravity, stamina,
     * swimming and ladders. Ctrl descends without crouching, so the camera
     * stays at standing height; sprinting stays off, so flight never drains
     * stamina, makes sprint noise or plays footsteps.
     */
    private static void updateFlight(Player player, Command command, float dt, FrameResult result) {
        player.crouching = false;
        player.sprinting = false;
        float speed = command.sprintHeld ? FLY_FAST_SPEED : FLY_SPEED;
        result.horizontalIntent = applyHorizontalIntent(player, command, speed);
        boolean rise = command.jumpHeld && !command.crouchHeld;
        boolean descend = command.crouchHeld && !command.jumpHeld;
        player.vel.y = rise ? FLY_VERTICAL_SPEED : descend ? -FLY_VERTICAL_SPEED : 0f;

        boolean wasInWater = player.inWater;
        if (VoxelPhysics.integrateFlight(player, dt, FLIGHT_CEILING_Y)) {
            // Touching the ground while descending lands.
            player.abilities.setFlying(false);
        }
        result.enteredWater = !wasInWater && player.inWater;
    }
}
