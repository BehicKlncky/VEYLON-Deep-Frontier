package com.veylon.ai;

import com.veylon.Game;
import com.veylon.combat.WorldNoise;
import com.veylon.engine.InfectedSounds.Cue;
import com.veylon.entity.Npc;
import com.veylon.util.Vec3i;
import com.veylon.world.World;

/** Hearing-led Veilbound brain. No jobs, feeding, allegiance, magic doors or distant-player tracking. */
public final class InfectedAI {
    public static final float WINDUP = 0.65f;
    public static final float CADENCE = 1.8f;
    public static final float REACH = 1.55f;

    private InfectedAI() { }

    public static void update(Game g, Npc n, float dt) {
        n.attackCooldown -= dt;
        n.repathCooldown -= dt;
        n.decideTimer -= dt;
        n.searchTimer = Math.max(0, n.searchTimer - dt);
        n.lastKnownAge += dt;
        n.bobPhase += dt * (Math.abs(n.vel.x) + Math.abs(n.vel.z)) * 2.2f;
        if (n.painSound) {
            g.audio.playInfected(Cue.PAIN, n.pos.x, n.pos.y, n.pos.z);
            n.painSound = false;
        }
        if (FirePanic.update(g, n, dt)) {
            n.strikeWindup = -1;
            return;
        }
        if (n.painTimer > 0) {
            n.painTimer -= dt;
            n.strikeWindup = -1;
            Steering.stop(n);
            return;
        }
        if (!g.player.isPerceivableByAi() || g.player.dead) {
            // Visibility controls immediately forget player-derived intent, including a pending strike.
            n.searchTimer = 0;
            n.strikeWindup = -1;
            n.path = null;
            Steering.stop(n);
            return;
        }
        boolean sight = seesPlayer(g, n);
        if (n.decideTimer <= 0) {
            n.decideTimer = 0.25f;
            boolean quiet = n.searchTimer <= 0;
            if (sight) {
                n.lastKnown.set(g.player.pos);
                n.lastKnownAge = 0;
                n.searchTimer = 10;
            } else {
                WorldNoise.NoiseEvent heard = g.noise.loudestAudible(n.pos.x, n.pos.y, n.pos.z, 0);
                if (heard != null && heard != n.lastHeard && heard.source != n
                        && (heard.playerSource || heard.intensity >= 0.6f)) {
                    n.lastHeard = heard;
                    n.lastKnown.set(heard.x, heard.y, heard.z);
                    n.searchTimer = 8;
                    n.path = null;
                }
            }
            if (quiet && n.searchTimer > 0) {
                g.audio.playInfected(Cue.NOTICE, n.pos.x, n.pos.y, n.pos.z);
            }
        }
        boolean canStrike = sight && n.distSqTo(g.player) <= REACH * REACH
                && Math.abs(n.pos.y - g.player.pos.y) < 0.8f;
        if (n.strikeWindup >= 0) {
            Steering.stop(n);
            n.state = Npc.NpcState.ATTACK;
            n.strikeWindup -= dt;
            if (n.strikeWindup <= 0) {
                n.strikeWindup = -1;
                n.attackCooldown = CADENCE;
                // Recheck range, occlusion and mode at impact, so dodging/closing a door works.
                if (canStrike) {
                    g.player.hurtPhysical(g, n.archetype.meleeDamage, true);
                    if (!g.player.abilities.invulnerable()) {
                        g.player.knockback(n.pos.x, n.pos.z, 2f);
                        g.audio.playHit();
                    }
                }
            }
            return;
        }
        if (canStrike && n.attackCooldown <= 0) {
            n.strikeWindup = WINDUP;
            n.state = Npc.NpcState.ATTACK;
            n.yaw = (float) Math.toDegrees(Math.atan2(g.player.pos.x - n.pos.x,
                    -(g.player.pos.z - n.pos.z)));
            g.audio.playInfected(Cue.STRIKE, n.pos.x, n.pos.y, n.pos.z);
            Steering.stop(n);
        } else if (n.searchTimer > 0 && !canStrike) {
            n.state = Npc.NpcState.GUARD;
            pursue(g, n);
        } else {
            n.state = Npc.NpcState.IDLE;
            Steering.stop(n);
        }
    }

    public static boolean seesPlayer(Game g, Npc n) {
        float light = Math.max(g.world.skyLight(cell(g.player.pos.x), cell(g.player.pos.y),
                cell(g.player.pos.z)) * (g.time.isNight() ? 0.15f : 1f),
                g.world.blockLight(cell(g.player.pos.x), cell(g.player.pos.y), cell(g.player.pos.z)));
        float range = n.archetype.viewRange * (0.35f + 0.65f * Math.min(1, light));
        if (g.player.crouching) { range *= 0.6f; }
        float dx = g.player.pos.x - n.pos.x, dz = g.player.pos.z - n.pos.z;
        double d2 = n.distSqTo(g.player);
        float dot = (float) Math.sin(Math.toRadians(n.yaw)) * dx
                - (float) Math.cos(Math.toRadians(n.yaw)) * dz;
        return g.player.isPerceivableByAi() && !g.player.dead && d2 < range * range
                && (d2 < 9 || dot > 0.25f * Math.sqrt(dx * dx + dz * dz))
                && clearSight(g.world, n.pos.x, n.pos.y + 1.4f, n.pos.z,
                g.player.pos.x, g.player.pos.y + 1.4f, g.player.pos.z);
    }

    /** Conservative loaded voxel ray, also used by spawn visibility guards. */
    public static boolean clearSight(World world, float x, float y, float z, float tx, float ty, float tz) {
        float dx = tx - x, dy = ty - y, dz = tz - z;
        int steps = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) * 5));
        for (int i = 0; i <= steps; i++) {
            float f = i / (float) steps;
            int bx = cell(x + dx * f), by = cell(y + dy * f), bz = cell(z + dz * f);
            if (!world.isColumnLoaded(bx, bz) || world.getBlock(bx, by, bz).solid) { return false; }
        }
        return true;
    }

    private static void pursue(Game g, Npc n) {
        if (n.distSqTo(n.lastKnown.x, n.lastKnown.y, n.lastKnown.z) < 0.5f) {
            Steering.stop(n);
            return;
        }
        if (n.repathCooldown <= 0 && g.entities.takeInfectedPathQuery()) {
            n.repathCooldown = 1.5f;
            n.path = Pathfinder.find(g.world, cell(n.pos.x), cell(n.pos.y), cell(n.pos.z),
                    cell(n.lastKnown.x), cell(n.lastKnown.y), cell(n.lastKnown.z), 400);
            n.pathIndex = 0;
        }
        if (n.path == null || n.pathIndex >= n.path.size()) { Steering.stop(n); return; }
        Vec3i step = n.path.get(n.pathIndex);
        // Existing A* can propose an unswept stair-side drop. Refuse it; portal routing belongs to 10.
        if (!safeStride(g.world, n, step)) { n.path = null; Steering.stop(n); return; }
        if (n.distSqTo(step.x() + 0.5f, n.pos.y, step.z() + 0.5f) < 0.18f) {
            n.pathIndex++;
            Steering.stop(n);
        } else {
            Steering.moveToward(n, step.x() + 0.5f, step.z() + 0.5f, n.archetype.speed);
        }
    }

    private static boolean safeStride(World w, Npc n, Vec3i step) {
        if (Math.abs(step.y() - n.pos.y) > 0.6f) { return false; }
        float dx = step.x() + 0.5f - n.pos.x, dz = step.z() + 0.5f - n.pos.z;
        for (int i = 1; i <= 5; i++) {
            float x = n.pos.x + dx * i / 5, z = n.pos.z + dz * i / 5;
            if (!w.isColumnLoaded(cell(x), cell(z)) || n.collidesAt(x, n.pos.y, z)
                    || !w.getBlock(cell(x), cell(n.pos.y) - 1, cell(z)).solid) { return false; }
        }
        return true;
    }

    private static int cell(float value) { return (int) Math.floor(value); }
}
