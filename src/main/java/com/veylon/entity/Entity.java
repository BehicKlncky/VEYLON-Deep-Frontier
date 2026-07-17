package com.veylon.entity;

import com.veylon.world.BlockType;
import com.veylon.world.World;
import org.joml.Vector3f;

/** Base for all moving things: AABB voxel collision, gravity, water drag, fall damage. */
public abstract class Entity {

    public final Vector3f pos = new Vector3f();
    public final Vector3f vel = new Vector3f();
    public float width = 0.6f;
    public float height = 1.8f;
    /** Degrees, 0 = facing -Z. */
    public float yaw;
    public float health = 20;
    public float maxHealth = 20;
    public boolean onGround;
    public boolean inWater;
    /** Overlapping a climbable block (rope ladder). */
    public boolean onLadder;
    public boolean dead;
    public boolean horizontalCollision;
    /** True if the most recent damage came from the player (controls drops). */
    public boolean lastHitByPlayer;

    protected float fallDist;
    protected final World world;

    protected Entity(World world) {
        this.world = world;
    }

    public boolean collidesAt(float px, float py, float pz) {
        if (py < 0) {
            return true;
        }
        float hw = width / 2f;
        int x0 = (int) Math.floor(px - hw);
        int x1 = (int) Math.floor(px + hw - 1e-4f);
        int y0 = (int) Math.floor(py);
        int y1 = (int) Math.floor(py + height - 1e-4f);
        int z0 = (int) Math.floor(pz - hw);
        int z1 = (int) Math.floor(pz + hw - 1e-4f);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    if (world.isSolid(x, y, z)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean moveAxis(int axis, float d) {
        if (d == 0) {
            return false;
        }
        float remaining = d;
        float sign = Math.signum(d);
        while (Math.abs(remaining) > 1e-7f) {
            float step = sign * Math.min(0.2f, Math.abs(remaining));
            remaining -= step;
            switch (axis) {
                case 0 -> pos.x += step;
                case 1 -> pos.y += step;
                default -> pos.z += step;
            }
            if (collidesAt(pos.x, pos.y, pos.z)) {
                switch (axis) {
                    case 0 -> pos.x -= step;
                    case 1 -> pos.y -= step;
                    default -> pos.z -= step;
                }
                return true;
            }
        }
        return false;
    }

    /** Integrates velocity with gravity and voxel collision. */
    public void applyPhysics(float dt, boolean gravity) {
        int ex = (int) Math.floor(pos.x);
        int ey = (int) Math.floor(pos.y + height * 0.5f);
        int ez = (int) Math.floor(pos.z);
        inWater = world.getBlock(ex, ey, ez) == BlockType.WATER
                || world.getBlock(ex, (int) Math.floor(pos.y + 0.1f), ez) == BlockType.WATER;
        onLadder = world.getBlock(ex, ey, ez).isClimbable()
                || world.getBlock(ex, (int) Math.floor(pos.y + 0.1f), ez).isClimbable();

        if (gravity) {
            if (onLadder) {
                // Rope ladders arrest falls; entities slide down slowly unless climbing.
                if (vel.y < -1.6f) {
                    vel.y = -1.6f;
                }
                fallDist = 0;
            } else {
                float g = inWater ? 7f : 26f;
                vel.y -= g * dt;
            }
            if (inWater && vel.y < -2.2f) {
                vel.y = -2.2f;
            }
            if (vel.y < -52f) {
                vel.y = -52f;
            }
        }

        float drag = inWater ? 0.5f : 1f;
        boolean hitX = moveAxis(0, vel.x * dt * drag);
        boolean hitZ = moveAxis(2, vel.z * dt * drag);
        horizontalCollision = hitX || hitZ;
        boolean hitY = moveAxis(1, vel.y * dt);
        if (hitY) {
            if (vel.y < 0) {
                onGround = true;
                if (fallDist > 3.5f && !inWater) {
                    onLanded(fallDist);
                }
                fallDist = 0;
            }
            vel.y = 0;
        } else {
            if (vel.y < -0.01f) {
                onGround = false;
                fallDist += -vel.y * dt;
            } else if (vel.y > 0.01f) {
                onGround = false;
                fallDist = 0;
            }
        }
        if (inWater) {
            fallDist = 0;
        }
    }

    protected void onLanded(float fall) {
    }

    public void hurt(float dmg, boolean byPlayer) {
        if (dead) {
            return;
        }
        health -= dmg;
        lastHitByPlayer = byPlayer;
        if (health <= 0) {
            dead = true;
        }
    }

    public void knockback(float fromX, float fromZ, float strength) {
        float dx = pos.x - fromX;
        float dz = pos.z - fromZ;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01f) {
            return;
        }
        vel.x += dx / len * strength;
        vel.z += dz / len * strength;
        vel.y += strength * 0.45f;
    }

    public double distSqTo(float x, float y, float z) {
        double dx = pos.x - x, dy = pos.y - y, dz = pos.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distSqTo(Entity other) {
        return distSqTo(other.pos.x, other.pos.y, other.pos.z);
    }
}
