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
    /** Layout thirteen: how close a guard fighting a body must be for it to turn on that guard. */
    static final float ENGAGE = 6f;

    private InfectedAI() { }

    public static void update(Game g, Npc n, float dt) {
        if (n.archetype == com.veylon.settlement.NpcArchetype.BREACHER) {
            BreacherAI.update(g, n, dt);
            return;
        }
        if (!ready(g, n, dt)) {
            // Visibility controls forget the player, never a guard who is fighting this body.
            if (n.painTimer <= 0 && !n.panic.active()) { fightBack(g, n, dt); }
            return;
        }
        boolean sight = seesPlayer(g, n);
        if (n.decideTimer <= 0) {
            n.decideTimer = 0.25f + g.entities.nextSettledNpcAiFloat() * .1f;
            boolean quiet = n.searchTimer <= 0;
            if (sight) {
                n.lastKnown.set(g.player.pos);
                n.lastKnownAge = 0;
                n.searchTimer = 18;
            } else {
                WorldNoise.NoiseEvent heard = g.noise.loudestAudible(g.world,
                        n.pos.x, n.pos.y, n.pos.z, 0, n.lastHeard, n);
                if (heard != null && heard != n.lastHeard && heard.source != n
                        && (heard.playerSource || heard.intensity >= 0.6f)) {
                    n.lastHeard = heard;
                    n.lastKnown.set(heard.x, heard.y, heard.z);
                    n.lastKnownAge = 0;
                    n.searchTimer = 14;
                }
            }
            if (quiet && n.searchTimer > 0) {
                g.audio.playInfected(Cue.NOTICE, n.pos.x, n.pos.y, n.pos.z);
            }
        }
        boolean canStrike = sight && n.distSqTo(g.player) <= REACH * REACH
                && Math.abs(n.pos.y - g.player.pos.y) < 0.8f;
        if (n.combatTarget instanceof Npc || !canStrike && n.strikeWindup < 0 && assailant(g, n) != null) {
            fightBack(g, n, dt);
            return;
        }
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
            pursue(g, n, dt);
        } else {
            n.state = Npc.NpcState.IDLE;
            n.path = null;
            Steering.stop(n);
        }
    }

    /**
     * Timers, the pain cue, fire panic, pain and the visibility controls every infected brain
     * shares, in that order. False when this tick is spent: the body panics, flinches, or has
     * just forgotten an imperceptible player, with any pending strike, route or door.
     */
    static boolean ready(Game g, Npc n, float dt) {
        n.attackCooldown -= dt;
        n.painGuard = Math.max(0f, n.painGuard - dt);
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
            return false;
        }
        if (n.painTimer > 0) {
            n.painTimer -= dt;
            n.strikeWindup = -1;
            Steering.stop(n);
            return false;
        }
        if (!g.player.isPerceivableByAi() || g.player.dead) {
            // Visibility controls immediately forget player-derived intent, including a pending strike.
            n.searchTimer = 0;
            n.strikeWindup = -1;
            n.path = null;
            n.breachDoor = null;
            Steering.stop(n);
            return false;
        }
        return true;
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

    /**
     * Layout thirteen (milestone 17): a compound's guard or raider who is fighting this body, within
     * {@value #ENGAGE} blocks and in plain sight; null in every earlier world, where the infected
     * answer only the player. The body never picks a fight with the living on its own.
     */
    static Npc assailant(Game g, Npc n) {
        if (g.world.urban == null || !g.world.urban.hasStrongholds()) { return null; }
        Npc best = null;
        double bestD = ENGAGE * ENGAGE;
        for (Npc o : g.entities.npcs) {
            if (o == n || o.dead || o.combatTarget != n || com.veylon.settlement.UrbanSiteDirector.siteOf(g.world, o) == null) {
                continue;
            }
            double d = o.distSqTo(n);
            if (d <= bestD && Math.abs(o.pos.y - n.pos.y) < 0.8f
                    && clearSight(g.world, n.pos.x, n.pos.y + 1.4f, n.pos.z, o.pos.x, o.pos.y + 1.2f, o.pos.z)) {
                best = o;
                bestD = d;
            }
        }
        return best;
    }

    /**
     * Strikes back at an assailant with the same windup, reach, cadence and recheck at impact as a
     * strike at the player, through the living body's own damage. Clears the target when the fight
     * is over.
     */
    private static void fightBack(Game g, Npc n, float dt) {
        if (n.strikeWindup >= 0 && n.combatTarget instanceof Npc foe) {
            Steering.stop(n);
            n.state = Npc.NpcState.ATTACK;
            n.strikeWindup -= dt;
            if (n.strikeWindup > 0) { return; }
            n.strikeWindup = -1;
            n.attackCooldown = CADENCE;
            if (!foe.dead && foe.distSqTo(n) <= REACH * REACH && Math.abs(foe.pos.y - n.pos.y) < 0.8f
                    && clearSight(g.world, n.pos.x, n.pos.y + 1.4f, n.pos.z, foe.pos.x, foe.pos.y + 1.2f, foe.pos.z)) {
                foe.hurt(n.archetype.meleeDamage, false);
                foe.knockback(n.pos.x, n.pos.z, 2f);
                g.audio.playHit();
            }
            n.combatTarget = null;
            return;
        }
        n.combatTarget = null;
        Npc foe = assailant(g, n);
        if (foe == null) { return; }
        n.yaw = (float) Math.toDegrees(Math.atan2(foe.pos.x - n.pos.x, -(foe.pos.z - n.pos.z)));
        if (foe.distSqTo(n) > REACH * REACH) {
            // Knocked back out of reach: close on the one hitting it, by a supported route.
            n.state = Npc.NpcState.GUARD;
            if (!IndoorNavigation.moveTo(g, n, new Vec3i(cell(foe.pos.x), cell(foe.pos.y + .01f), cell(foe.pos.z)),
                    n.archetype.speed, REACH * 0.8f)) { Steering.stop(n); }
            return;
        }
        Steering.stop(n);
        if (n.attackCooldown <= 0 && n.strikeWindup < 0) {
            n.combatTarget = foe;
            n.strikeWindup = WINDUP;
            n.state = Npc.NpcState.ATTACK;
            g.audio.playInfected(Cue.STRIKE, n.pos.x, n.pos.y, n.pos.z);
        }
    }

    /** Conservative loaded voxel ray, also used by spawn visibility guards. */
    public static boolean clearSight(World world, float x, float y, float z, float tx, float ty, float tz) {
        float dx = tx - x, dy = ty - y, dz = tz - z;
        int steps = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) * 5));
        if (steps > 640) { return false; }
        for (int i = 0; i <= steps; i++) {
            float f = i / (float) steps;
            int bx = cell(x + dx * f), by = cell(y + dy * f), bz = cell(z + dz * f);
            if (!world.isColumnLoaded(bx, bz) || world.getBlock(bx, by, bz).solid) { return false; }
        }
        return true;
    }

    private static void pursue(Game g, Npc n, float dt) {
        if (!IndoorNavigation.moveTo(g, n, new Vec3i(cell(n.lastKnown.x), cell(n.lastKnown.y + .01f),
                cell(n.lastKnown.z)), n.archetype.speed, .65f)) { n.yaw += 35f * dt; }
    }
    private static int cell(float value) { return (int) Math.floor(value); }
}
