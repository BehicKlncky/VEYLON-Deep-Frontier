package com.veylon.ai;

import com.veylon.FixtureActions;
import com.veylon.Game;
import com.veylon.combat.WorldNoise;
import com.veylon.engine.EcologySounds;
import com.veylon.engine.InfectedSounds.Cue;
import com.veylon.entity.Npc;
import com.veylon.entity.Player;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;

/**
 * The Breacher (urban milestone 15), the one infected variant: a Veilbound whose membrane has
 * sealed over its face and fused its right forearm into a heavy ram. Where the Veilbound sees
 * the player in light and stops at a locked door, the Breacher changes what the player has to
 * do about it:
 *
 * <ul>
 *   <li><b>Blind:</b> light, darkness and line of sight mean nothing to it. It notices the player
 *       only by touch (within {@value #TOUCH} blocks) or by sound, so a crouched, silent player
 *       can walk round it in plain view.</li>
 *   <li><b>Hearing:</b> the shared occluded hearing query with {@value #HEARING_BONUS} extra
 *       blocks of range; it searches a sound's position for {@value #SEARCH_SECONDS} s.</li>
 *   <li><b>Doors:</b> it shoulders unlocked doors open like anyone, and when no ordinary route
 *       reaches the sound it plans through closed wooden doors ({@link Pathfinder#findBreach}).
 *       At a locked or barred one it hammers: a blow every {@value #BLOW_SECONDS} s, each one a
 *       loud noise other infected answer, and the {@value #BLOWS}th forces the door through the
 *       same recorded edits as a player's forced entry. Barring a door therefore buys time, not
 *       safety; metal doors hold. In a layout-fourteen city (urban milestone 18) a door's brace
 *       takes blows before the door does, and it plans through plank barricades too, each blow
 *       taking {@value com.veylon.Fortification#BREACHER_BLOW} of a barricade's strength until it
 *       gives way; sheet-metal barricades hold like metal doors.</li>
 *   <li><b>Strike:</b> slow and heavy: {@value #WINDUP} s windup, {@value #CADENCE} s between
 *       blows, its archetype's melee damage through the player's damage gates.</li>
 * </ul>
 *
 * <p>It shares the Veilbound's timers, pain, fire panic and visibility controls
 * ({@link InfectedAI}), and all of its intent is transient: a load or reactivation forgets a
 * half-hammered door, which stays whole until the last blow.
 */
public final class BreacherAI {
    public static final float WINDUP = 0.9f;
    public static final float CADENCE = 2.4f;
    public static final float REACH = 1.6f;
    public static final float TOUCH = 1.6f;
    public static final float HEARING_BONUS = 6f;
    public static final float SEARCH_SECONDS = 16f;
    public static final float BLOW_SECONDS = 1.2f;
    public static final int BLOWS = 4;
    /** Each blow is heard like a shout: other infected investigate it. */
    public static final float BLOW_NOISE_RADIUS = 16f;
    public static final float BLOW_NOISE = 0.6f;
    /** Farther than this from its door it has been pushed away, and lets the door be. */
    private static final float DOOR_REACH = 2.2f;

    private BreacherAI() {
    }

    public static void update(Game g, Npc n, float dt) {
        if (!InfectedAI.ready(g, n, dt)) {
            return;
        }
        Player p = g.player;
        boolean touch = touches(n, p);
        if (n.decideTimer <= 0) {
            n.decideTimer = 0.25f + g.entities.nextSettledNpcAiFloat() * .1f;
            boolean quiet = n.searchTimer <= 0;
            if (touch) {
                n.lastKnown.set(p.pos);
                n.lastKnownAge = 0;
                n.searchTimer = SEARCH_SECONDS;
            } else {
                WorldNoise.NoiseEvent heard = g.noise.loudestAudible(g.world,
                        n.pos.x, n.pos.y, n.pos.z, HEARING_BONUS, n.lastHeard, n);
                if (heard != null && heard != n.lastHeard && heard.source != n
                        && (heard.playerSource || heard.intensity >= 0.6f)) {
                    n.lastHeard = heard;
                    n.lastKnown.set(heard.x, heard.y, heard.z);
                    n.lastKnownAge = 0;
                    n.searchTimer = SEARCH_SECONDS;
                }
            }
            if (quiet && n.searchTimer > 0) {
                g.audio.playInfected(Cue.NOTICE, n.pos.x, n.pos.y, n.pos.z);
            }
        }
        if (n.strikeWindup >= 0) {
            Steering.stop(n);
            n.state = Npc.NpcState.ATTACK;
            n.strikeWindup -= dt;
            if (n.strikeWindup <= 0) {
                n.strikeWindup = -1;
                n.attackCooldown = CADENCE;
                // Range and mode are checked again at impact, so stepping away works.
                if (touches(n, p) && n.distSqTo(p) <= REACH * REACH) {
                    p.hurtPhysical(g, n.archetype.meleeDamage, true);
                    if (!p.abilities.invulnerable()) {
                        p.knockback(n.pos.x, n.pos.z, 3f);
                        g.audio.playHit();
                    }
                }
            }
            return;
        }
        if (touch && n.attackCooldown <= 0) {
            n.strikeWindup = WINDUP;
            n.state = Npc.NpcState.ATTACK;
            n.breachDoor = null;
            n.yaw = (float) Math.toDegrees(Math.atan2(p.pos.x - n.pos.x, -(p.pos.z - n.pos.z)));
            g.audio.playInfected(Cue.STRIKE, n.pos.x, n.pos.y, n.pos.z);
            Steering.stop(n);
            return;
        }
        if (n.breachDoor != null) {
            hammer(g, n, dt);
            return;
        }
        if (n.searchTimer > 0 && !touch) {
            n.state = Npc.NpcState.GUARD;
            Vec3i goal = new Vec3i(cell(n.lastKnown.x), cell(n.lastKnown.y + .01f), cell(n.lastKnown.z));
            if (!IndoorNavigation.moveTo(g, n, goal, n.archetype.speed, .65f, true)) {
                n.yaw += 35f * dt;
            }
        } else if (!touch) {
            n.state = Npc.NpcState.IDLE;
            n.path = null;
            Steering.stop(n);
        } else {
            Steering.stop(n);
        }
    }

    /**
     * Blows on the door or barricade its route stopped at. A door's brace takes blows before the
     * door does (urban milestone 18); the door's last blow forces it, and a barricade gives way
     * when its strength is gone.
     */
    private static void hammer(Game g, Npc n, float dt) {
        Vec3i door = n.breachDoor;
        float dx = door.x() + 0.5f - n.pos.x, dz = door.z() + 0.5f - n.pos.z;
        BlockType target = g.world.getBlock(door.x(), door.y(), door.z());
        if (target != BlockType.WOOD_DOOR && target != BlockType.BARRICADE
                || n.searchTimer <= 0 || dx * dx + dz * dz > DOOR_REACH * DOOR_REACH) {
            // Opened, forced or broken meanwhile; lost interest; or pushed away from it.
            n.breachDoor = null;
            n.breachBlows = 0;
            n.breachStruck = 0;
            n.path = null;
            return;
        }
        Steering.stop(n);
        n.state = Npc.NpcState.ATTACK;
        n.yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
        n.searchTimer = Math.max(n.searchTimer, 2f); // it does not drift off mid-swing
        if ((n.breachTimer -= dt) > 0) {
            return;
        }
        n.breachTimer = BLOW_SECONDS;
        n.breachStruck++;
        float x = door.x() + 0.5f, y = door.y() + 1f, z = door.z() + 0.5f;
        com.veylon.Fortification.Blow blow = com.veylon.Fortification.breacherBlow(g, door, n);
        if (blow == com.veylon.Fortification.Blow.GAVE_WAY) {
            n.breachDoor = null;
            n.breachBlows = 0;
            n.breachStruck = 0;
            n.path = null;
            n.repathCooldown = 0;
            return;
        }
        if (blow == com.veylon.Fortification.Blow.NONE) {
            n.breachBlows++;
        }
        if (n.breachBlows >= BLOWS) {
            FixtureActions.breacherForces(g, door, n);
            if (g.player.isPerceivableByAi() && g.player.distSqTo(x, y, z) < 40 * 40) {
                g.log("A door gives way with a crash.");
            }
            n.breachDoor = null;
            n.breachBlows = 0;
            n.breachStruck = 0;
            n.path = null;
            n.repathCooldown = 0;
            return;
        }
        g.audio.playEcology(EcologySounds.Cue.HAMMER, x, y, z);
        g.particles.blockDust(target, x, y, z, 4);
        g.noise.emit(g, x, y, z, BLOW_NOISE_RADIUS, BLOW_NOISE, "door-hammer", false, n);
        if (n.breachStruck == 1 && g.player.isPerceivableByAi() && g.player.distSqTo(x, y, z) < 40 * 40) {
            g.log(target == BlockType.BARRICADE ? "Something hammers at a barricade nearby."
                    : "Something hammers at a door nearby.");
        }
    }

    /** Touch: the perceivable player close enough to bump into, on its own floor. */
    private static boolean touches(Npc n, Player p) {
        if (!p.isPerceivableByAi() || p.dead || Math.abs(n.pos.y - p.pos.y) >= 0.8f) {
            return false;
        }
        float dx = p.pos.x - n.pos.x, dz = p.pos.z - n.pos.z;
        return dx * dx + dz * dz <= TOUCH * TOUCH;
    }

    private static int cell(float value) {
        return (int) Math.floor(value);
    }
}
