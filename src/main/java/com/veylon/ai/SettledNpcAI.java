package com.veylon.ai;

import com.veylon.Game;
import com.veylon.combat.WeaponDefinition;
import com.veylon.combat.WeaponRegistry;
import com.veylon.combat.WorldNoise;
import com.veylon.entity.Creature;
import com.veylon.entity.Npc;
import com.veylon.entity.Npc.NpcState;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.CounterattackMission;
import com.veylon.settlement.Settlement;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;

import java.util.List;

/**
 * Behavior for settlement residents and traveling war parties: daily routines
 * (sleep, work, patrol, guard), perception (occluded sight, hearing via world
 * noise events), alarm/search/return-to-duty, ranged and melee combat with
 * friendly-fire awareness, morale-driven flight, and gate handling — all on
 * top of bounded A* with steering fallback.
 */
public final class SettledNpcAI {

    /** Seconds between perception (LOS) evaluations per NPC. */
    public static final float PERCEPTION_INTERVAL = 0.3f;

    /** Seconds between repath attempts. */
    private static final float REPATH_COOLDOWN = 1.6f;

    private SettledNpcAI() {
    }

    public static void update(Game g, Npc n, float dt) {
        Settlement s = n.settled() ? g.world.settlements.get(n.settlementId) : null;
        NpcArchetype a = n.archetype;
        n.lastKnownAge += dt;
        n.repathCooldown -= dt;
        n.reloadTimer = Math.max(0, n.reloadTimer - dt);

        // Captives wait in their cage until rescued via interaction.
        if (a == NpcArchetype.CAPTIVE) {
            Steering.stop(n);
            if (g.player != null && n.distSqTo(g.player) < 6 * 6) {
                faceToward(n, g.player.pos.x, g.player.pos.z);
            }
            n.state = NpcState.IDLE;
            return;
        }

        boolean hostileToPlayer = n.hostileToPlayer();
        perceive(g, n, s, hostileToPlayer, dt);

        // Threat responses.
        if (hostileToPlayer && n.lastKnownAge < 14f && !g.player.dead) {
            combat(g, n, s, a, dt);
            return;
        }

        if (n.warParty && partyTravel(g, n, a, dt)) {
            return;
        }

        // Friendly/neutral: defend the settlement against hostile humans & predators.
        if (s != null && !s.hostile()) {
            Npc intruder = nearestHostileNpc(g, s.center, 22);
            if (intruder != null && n.combatant()) {
                engageMelee(g, n, intruder, a, dt);
                return;
            }
            Creature predator = g.entities.nearestCreature(
                    s.center.x(), s.center.y(), s.center.z(), 18, c -> c.type.predator);
            if (predator != null && n.combatant()) {
                engageCreature(g, n, predator, a, dt);
                return;
            }
            // Non-combatants hide from nearby danger.
            if ((intruder != null || s.alertLevel > 70) && !n.combatant()) {
                n.state = NpcState.FLEE;
                moveTo(g, n, homePos(g, s, n), a.speed * 1.2f, 1.8f);
                return;
            }
        }

        // Investigate recent suspicious sounds (both alignments).
        if (n.searchTimer > 0) {
            n.searchTimer = Math.max(0f, n.searchTimer - dt);
            if (n.searchTimer > 0 && n.lastKnownAge < 25f) {
                n.state = NpcState.GUARD;
                Vec3i target = new Vec3i((int) n.lastKnown.x,
                        (int) n.lastKnown.y, (int) n.lastKnown.z);
                if (!moveTo(g, n, target, a.speed, 2.2f)) {
                    // Arrived: look around a moment, then give up.
                    Steering.stop(n);
                    n.yaw += dt * 60f;
                }
                return;
            }
            // Once the remembered position is stale, retire the investigation
            // explicitly instead of leaving a non-zero timer frozen forever.
            n.searchTimer = 0f;
        }

        dailyLife(g, n, s, a, dt);
    }

    // ------------------------------------------------------------------
    // Perception
    // ------------------------------------------------------------------

    private static void perceive(Game g, Npc n, Settlement s, boolean hostile, float dt) {
        // NpcAI owns the once-per-tick timer decrement before dispatching here.
        // Decrementing again halved the documented perception interval for every
        // active settled NPC and synchronized unnecessary LOS queries.
        if (n.decideTimer > 0) {
            return;
        }
        n.decideTimer = PERCEPTION_INTERVAL;
        n.perceptionChecks++;
        var p = g.player;
        if (p == null || p.dead) {
            return;
        }

        // Sight: range scaled by crouch + darkness, 220-degree cone, occluded.
        if (hostile || (s != null && s.alertLevel > 60)) {
            float range = n.archetype.viewRange;
            if (p.crouching) {
                range *= 0.5f;
            }
            float dayLight = g.time.isNight() ? 0.25f : 1f;
            float light = Math.max(g.world.skyLight((int) p.pos.x, (int) p.pos.y, (int) p.pos.z)
                    * dayLight, g.world.blockLight((int) p.pos.x, (int) p.pos.y, (int) p.pos.z));
            range *= 0.45f + 0.55f * Math.min(1f, light + 0.15f);
            double d2 = n.distSqTo(p);
            if (d2 < range * range) {
                float fx = (float) Math.sin(Math.toRadians(n.yaw));
                float fz = -(float) Math.cos(Math.toRadians(n.yaw));
                float dx = p.pos.x - n.pos.x, dz = p.pos.z - n.pos.z;
                float len = Math.max(0.01f, (float) Math.sqrt(dx * dx + dz * dz));
                boolean inCone = (fx * dx + fz * dz) / len > -0.35f || d2 < 3 * 3;
                if (inCone && hasLineOfSight(g, n, p.pos.x, p.pos.y + 1.4f, p.pos.z)) {
                    boolean firstContact = n.lastKnownAge > 12f;
                    n.lastKnown.set(p.pos);
                    n.lastKnownAge = 0;
                    n.searchTimer = Math.max(n.searchTimer, 14f);
                    if (n.warParty && n.partyKind != Npc.PartyKind.COUNTERATTACK) {
                        n.partyContact = true;
                        n.partyMission = Npc.PartyMission.SEARCHING;
                        n.partyDestination.set(p.pos);
                        n.partyMissionTimer = Math.max(n.partyMissionTimer, 30f);
                    }
                    if (firstContact && s != null && hostile) {
                        raiseAlarm(g, n, s);
                    }
                }
            }
        }

        // Hearing: world noise events.
        WorldNoise.NoiseEvent heard = g.noise.loudestAudible(
                n.pos.x, n.pos.y, n.pos.z, n.archetype.hearRange * 0.3f);
        if (heard != null && (heard.playerSource || heard.intensity > 0.6f)) {
            if (heard.playerSource && hostile) {
                n.lastKnown.set(heard.x, heard.y, heard.z);
                n.lastKnownAge = Math.min(n.lastKnownAge, 6f);
            }
            n.searchTimer = Math.max(n.searchTimer, 8f + heard.intensity * 8f);
            if (n.searchTimer > 12f && s != null) {
                s.alertLevel = Math.min(100, s.alertLevel + heard.intensity * 25f);
            }
            if (n.lastKnownAge > 10f) {
                n.lastKnown.set(heard.x, heard.y, heard.z);
            }
        }

        // Trackers read the player's traces: tracks, blood, carcasses.
        if (hostile && n.archetype == NpcArchetype.TRACKER && n.lastKnownAge > 10f) {
            var track = g.entities.nearestTrack(n.pos.x, n.pos.y, n.pos.z, 14f);
            if (track != null) {
                n.lastKnown.set(track.x, track.y, track.z);
                n.searchTimer = Math.max(n.searchTimer, 10f);
                // Trackers share what they find.
                for (Npc ally : g.entities.npcs) {
                    if (ally != n && ally.settlementId == n.settlementId
                            && ally.distSqTo(n) < 18 * 18) {
                        ally.lastKnown.set(track.x, track.y, track.z);
                        ally.searchTimer = Math.max(ally.searchTimer, 8f);
                    }
                }
            }
        }
    }

    private static boolean hasLineOfSight(Game g, Npc n, float tx, float ty, float tz) {
        float ox = n.pos.x, oy = n.pos.y + 1.55f, oz = n.pos.z;
        float dx = tx - ox, dy = ty - oy, dz = tz - oz;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(1, (int) (dist * 1.6f));
        for (int i = 1; i < steps; i++) {
            float f = i / (float) steps;
            if (g.world.getBlock((int) Math.floor(ox + dx * f), (int) Math.floor(oy + dy * f),
                    (int) Math.floor(oz + dz * f)).opaque) {
                return false;
            }
        }
        return true;
    }

    /** First contact: scouts sprint for the bell; others shout (noise event). */
    private static void raiseAlarm(Game g, Npc n, Settlement s) {
        if (s.alertLevel > 70) {
            return;
        }
        s.alertLevel = Math.min(100, s.alertLevel + 45);
        if (n.archetype == NpcArchetype.SCOUT && s.alarmBell != null) {
            n.state = NpcState.GUARD;
            n.searchTimer = 0;
            // The scout heads for the bell; ringing happens when they arrive
            // (see combat retreat logic). Mark intent via patrolIndex = -2.
            n.patrolIndex = -2;
        }
        // The shout itself is audible.
        g.noise.emit(g, n.pos.x, n.pos.y, n.pos.z, 30f, 0.7f, "alarm-shout", false, n);
    }

    // ------------------------------------------------------------------
    // Combat
    // ------------------------------------------------------------------

    private static void combat(Game g, Npc n, Settlement s, NpcArchetype a, float dt) {
        var p = g.player;
        double dist = Math.sqrt(n.distSqTo(p));
        boolean seen = n.lastKnownAge < 1.0f;

        // Morale: flee when badly hurt or the settlement broke.
        float moraleFloor = s != null ? s.morale : 50;
        if (n.health < n.maxHealth * 0.25f || moraleFloor < 15) {
            n.state = NpcState.FLEE;
            Vec3i refuge = s != null ? s.center
                    : new Vec3i((int) (n.pos.x + (n.pos.x - p.pos.x) * 3), (int) n.pos.y,
                    (int) (n.pos.z + (n.pos.z - p.pos.z) * 3));
            moveTo(g, n, refuge, a.speed * 1.25f, 2f);
            if (n.warParty) {
                n.partyMission = Npc.PartyMission.RETURNING;
                n.partyMissionTimer = 120f;
            }
            return;
        }

        // Scouts sprint to the alarm bell when outmatched / on first contact.
        if (a == NpcArchetype.SCOUT && s != null && s.alarmBell != null
                && s.alertLevel < 95 && n.patrolIndex == -2) {
            if (n.distSqTo(s.alarmBell.x(), s.alarmBell.y(), s.alarmBell.z()) < 3 * 3) {
                g.settlementManager.triggerAlarm(g, s);
                n.patrolIndex = 0;
            } else {
                moveTo(g, n, s.alarmBell, a.speed * 1.2f, 1.5f);
                return;
            }
        }

        n.state = NpcState.ATTACK;
        WeaponDefinition weapon = WeaponRegistry.of(a.weapon);

        if (weapon != null && a.ranged()) {
            rangedCombat(g, n, s, a, weapon, dist, seen, dt);
        } else {
            meleeCombatPlayer(g, n, a, dist, dt);
        }
    }

    /** Outbound -> local search -> return/report patrol lifecycle. */
    private static boolean partyTravel(Game g, Npc n, NpcArchetype a, float dt) {
        if (n.partyKind == Npc.PartyKind.COUNTERATTACK) {
            return counterattackTravel(g, n, a, dt);
        }
        Settlement origin = g.world.settlements.get(n.originSettlementId);
        if (origin == null || origin.cleared) {
            n.dead = true;
            n.lastHitByPlayer = false;
            return true;
        }

        if (n.partyMission == Npc.PartyMission.SEARCHING) {
            boolean loaded = g.world.getChunk(Math.floorDiv((int) n.pos.x, 16),
                    Math.floorDiv((int) n.pos.z, 16)) != null;
            boolean farFromPlayer = n.distSqTo(g.player) > 130 * 130;
            n.abstractTravel = !loaded || farFromPlayer;
            n.partyMissionTimer -= dt;
            n.state = NpcState.GUARD;
            if (n.partyMissionTimer <= 0) {
                n.partyMission = Npc.PartyMission.RETURNING;
                n.partyMissionTimer = 180f;
                n.path = null;
            } else if (n.abstractTravel) {
                // Search time still advances off-screen, but no physics, path
                // request or chunk generation is needed to represent it.
                Steering.stop(n);
            } else {
                wanderNear(g, n, (int) n.partyDestination.x, (int) n.partyDestination.z,
                        12, a.speed * 0.75f, dt);
            }
            return true;
        }

        Vec3i destination;
        float arrive;
        if (n.partyMission == Npc.PartyMission.RETURNING) {
            g.world.layoutFor(origin);
            destination = origin.gates.isEmpty() ? origin.center : origin.gates.getFirst();
            arrive = 5f;
        } else {
            // Party destinations are stored at block centers. Java's float-to-int
            // cast truncates toward zero, which shifts every negative-coordinate
            // mission one block east/south; floor recovers the original block.
            destination = new Vec3i((int) Math.floor(n.partyDestination.x),
                    (int) Math.floor(n.partyDestination.y),
                    (int) Math.floor(n.partyDestination.z));
            arrive = 8f;
        }

        double dx = n.pos.x - destination.x() - 0.5;
        double dz = n.pos.z - destination.z() - 0.5;
        if (dx * dx + dz * dz <= arrive * arrive) {
            n.abstractTravel = false;
            // Arrival is evaluated before EntityManager applies this tick's
            // physics. Clear the previous travel velocity so the member cannot
            // coast straight through the objective after changing phase.
            Steering.stop(n);
            if (n.partyMission == Npc.PartyMission.OUTBOUND) {
                n.partyMission = Npc.PartyMission.SEARCHING;
                n.partyMissionTimer = 30f;
                n.path = null;
            } else {
                if (n.partyContact && (a == NpcArchetype.SCOUT || a == NpcArchetype.TRACKER)) {
                    g.world.factionBounty.merge(n.partyFactionId, 6f, Float::sum);
                    g.log("A hostile scout returned home with word of your position.");
                }
                n.dead = true;
                n.lastHitByPlayer = false;
            }
            return true;
        }

        // Beyond active simulation range, advance at a fixed bounded rate and
        // never request chunks or A* paths. The party materializes normally as
        // it approaches the player or its home gate.
        boolean loaded = g.world.getChunk(Math.floorDiv((int) n.pos.x, 16),
                Math.floorDiv((int) n.pos.z, 16)) != null;
        boolean farFromPlayer = n.distSqTo(g.player) > 130 * 130;
        if (!loaded || farFromPlayer) {
            n.abstractTravel = true;
            Steering.stop(n);
            n.abstractTravelTick += dt;
            if (n.abstractTravelTick >= 1f) {
                float elapsed = Math.min(n.abstractTravelTick, 2f);
                n.abstractTravelTick = 0;
                float len = (float) Math.sqrt(dx * dx + dz * dz);
                float step = Math.min(len, a.speed * elapsed);
                n.pos.x += (destination.x() + 0.5f - n.pos.x) / len * step;
                n.pos.z += (destination.z() + 0.5f - n.pos.z) / len * step;
                n.pos.y = nonLoadingSurfaceHeight(g, (int) n.pos.x, (int) n.pos.z) + 1.4f;
            }
            return true;
        }

        n.abstractTravel = false;
        n.state = NpcState.GUARD;
        moveTo(g, n, destination, a.speed, arrive);
        return true;
    }

    /**
     * A counterattack NPC is a materialized member of group mission state. It
     * never derives mission validity from the occupied (and therefore cleared)
     * target, and losing player contact does not replace its outpost objective.
     */
    private static boolean counterattackTravel(Game g, Npc n, NpcArchetype a, float dt) {
        CounterattackMission mission = g.settlementManager.counterattacks.get(n.partyMissionId);
        if (mission == null || mission.phase == CounterattackMission.Phase.CLEANUP) {
            n.dead = true;
            n.lastHitByPlayer = false;
            return true;
        }

        if (mission.phase == CounterattackMission.Phase.ASSAULT) {
            n.abstractTravel = false;
            Npc defender = nearestCounterattackDefender(g, n, mission.targetSettlementId, 34f);
            if (defender != null) {
                engageMelee(g, n, defender, a, dt);
            } else {
                n.state = NpcState.GUARD;
                Steering.stop(n);
            }
            return true;
        }

        Vec3i destination = mission.phase == CounterattackMission.Phase.RETREAT
                ? mission.origin : mission.target;
        double dx = destination.x() + 0.5 - n.pos.x;
        double dz = destination.z() + 0.5 - n.pos.z;
        double distanceSq = dx * dx + dz * dz;
        if (distanceSq <= 4 * 4) {
            n.abstractTravel = false;
            n.state = NpcState.GUARD;
            Steering.stop(n);
            return true;
        }

        boolean loaded = g.world.getChunk(Math.floorDiv((int) n.pos.x, 16),
                Math.floorDiv((int) n.pos.z, 16)) != null;
        boolean farFromPlayer = n.distSqTo(g.player) > 130 * 130;
        if (!loaded || farFromPlayer) {
            // Usually the director de-materializes this member on the same
            // frame. This fallback still advances without requesting terrain.
            n.abstractTravel = true;
            Steering.stop(n);
            float length = (float) Math.sqrt(distanceSq);
            float step = Math.min(length, a.speed * Math.min(dt, 2f));
            n.pos.x += dx / length * step;
            n.pos.z += dz / length * step;
            return true;
        }
        n.abstractTravel = false;
        n.state = NpcState.GUARD;
        moveTo(g, n, destination, a.speed, 4f);
        return true;
    }

    private static Npc nearestCounterattackDefender(Game g, Npc attacker,
                                                     long targetSettlementId, float range) {
        Npc best = null;
        double bestDistance = range * range;
        for (Npc candidate : g.entities.npcs) {
            if (candidate == attacker || candidate.dead || !candidate.settled()
                    || candidate.settlementId != targetSettlementId || !candidate.combatant()
                    || attacker.alliedWith(candidate)) {
                continue;
            }
            double distance = attacker.distSqTo(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /** Height query for abstract parties that never materializes a missing chunk. */
    private static int nonLoadingSurfaceHeight(Game g, int x, int z) {
        var chunk = g.world.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        if (chunk == null) {
            return g.world.generator.heightAt(x, z);
        }
        return chunk.height(Math.floorMod(x, 16), Math.floorMod(z, 16));
    }

    private static void rangedCombat(Game g, Npc n, Settlement s, NpcArchetype a,
                                     WeaponDefinition weapon, double dist, boolean seen, float dt) {
        var p = g.player;
        float ideal = weapon.range * 0.55f;
        boolean los = seen && hasLineOfSight(g, n, p.pos.x, p.pos.y + 1.2f, p.pos.z);

        if (!los) {
            // Reposition toward the last known spot.
            moveTo(g, n, new Vec3i((int) n.lastKnown.x, (int) n.lastKnown.y, (int) n.lastKnown.z),
                    a.speed, 2.5f);
            return;
        }
        faceToward(n, p.pos.x, p.pos.z);
        if (dist < 4) {
            // Too close: back off while keeping aim.
            Steering.moveToward(n, n.pos.x + (n.pos.x - p.pos.x), n.pos.z + (n.pos.z - p.pos.z),
                    a.speed * 0.8f);
        } else if (dist > ideal + 6) {
            moveTo(g, n, new Vec3i((int) p.pos.x, (int) p.pos.y, (int) p.pos.z), a.speed, ideal);
        } else {
            Steering.stop(n);
        }

        if (n.attackCooldown > 0) {
            n.attackCooldown -= dt;
            return;
        }
        if (n.reloadTimer > 0) {
            return;
        }
        boolean firearm = weapon.category == WeaponDefinition.Category.FIREARM;
        if (firearm && n.loadedAmmo <= 0) {
            n.reloadTimer = weapon.reloadTime;
            n.loadedAmmo = weapon.magazine;
            return;
        }
        if (dist > weapon.range * 1.1f) {
            return;
        }
        // Friendly-fire check along the firing line.
        if (allyInLine(g, n, p.pos.x, p.pos.y + 1.0f, p.pos.z)) {
            Steering.moveToward(n, n.pos.x + g.entities.nextSettledNpcAiFloat() * 4 - 2,
                    n.pos.z + g.entities.nextSettledNpcAiFloat() * 4 - 2, a.speed * 0.6f);
            return;
        }

        // Fire using the shared projectile rules.
        float ox = n.pos.x, oy = n.pos.y + 1.5f, oz = n.pos.z;
        float dx = p.pos.x - ox, dy = (p.pos.y + 1.1f) - oy, dz = p.pos.z - oz;
        // Simple ballistic compensation for arrows.
        if (weapon.category == WeaponDefinition.Category.BOW) {
            dy += (float) dist * (float) dist * weapon.projectileGravity
                    / (2f * weapon.projectileSpeed * weapon.projectileSpeed) * 8f;
        }
        g.projectiles.fire(g, n, false, ox, oy, oz, dx, dy, dz, weapon,
                weapon.ammo == null ? null : weapon.ammo);
        if (firearm) {
            n.loadedAmmo--;
            g.audio.playGunshot(weapon.item == com.veylon.item.ItemType.FLINTLOCK_PISTOL,
                    ox, oy, oz);
            g.particles.muzzleFlash(ox, oy, oz, dx / (float) dist, dy / (float) dist,
                    dz / (float) dist);
            g.noise.emit(g, ox, oy, oz, weapon.noiseRadius, 0.9f, "gunshot", false, n);
            n.attackCooldown = weapon.attackInterval + 0.4f
                    + g.entities.nextSettledNpcAiFloat() * 0.5f;
        } else {
            g.audio.playBowRelease(ox, oy, oz);
            g.noise.emit(g, ox, oy, oz, weapon.noiseRadius, 0.3f, "bow", false, n);
            n.attackCooldown = weapon.drawTime + 0.8f
                    + g.entities.nextSettledNpcAiFloat() * 0.8f;
        }
        if (s != null) {
            s.alertLevel = 100;
        }
    }

    private static boolean allyInLine(Game g, Npc shooter, float tx, float ty, float tz) {
        float ox = shooter.pos.x, oy = shooter.pos.y + 1.5f, oz = shooter.pos.z;
        float dx = tx - ox, dy = ty - oy, dz = tz - oz;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        for (Npc ally : g.entities.npcs) {
            if (ally == shooter || ally.dead || !ally.alliedWith(shooter)) {
                continue;
            }
            // Project ally onto the firing segment.
            float ax = ally.pos.x - ox, ay = ally.pos.y + 0.9f - oy, az = ally.pos.z - oz;
            float t = (ax * dx + ay * dy + az * dz) / (dist * dist);
            if (t < 0.05f || t > 0.95f) {
                continue;
            }
            float cx = ox + dx * t - ally.pos.x;
            float cy = oy + dy * t - (ally.pos.y + 0.9f);
            float cz = oz + dz * t - ally.pos.z;
            if (cx * cx + cy * cy + cz * cz < 0.8f * 0.8f) {
                return true;
            }
        }
        return false;
    }

    private static void meleeCombatPlayer(Game g, Npc n, NpcArchetype a, double dist, float dt) {
        var p = g.player;
        n.attackCooldown -= dt;
        if (dist < 1.9) {
            Steering.stop(n);
            faceToward(n, p.pos.x, p.pos.z);
            if (n.attackCooldown <= 0) {
                n.attackCooldown = a == NpcArchetype.BRUTE ? 1.8f : 1.2f;
                p.hurtPhysical(g, a.meleeDamage, true);
                p.knockback(n.pos.x, n.pos.z, a == NpcArchetype.BRUTE ? 5f : 3f);
                g.audio.playHit();
                g.log(n.name + " strikes you!");
            }
        } else if (n.lastKnownAge < 1.0f) {
            moveTo(g, n, new Vec3i((int) p.pos.x, (int) p.pos.y, (int) p.pos.z),
                    a.speed * 1.1f, 1.6f);
        } else {
            moveTo(g, n, new Vec3i((int) n.lastKnown.x, (int) n.lastKnown.y,
                    (int) n.lastKnown.z), a.speed, 2f);
        }
    }

    private static void engageMelee(Game g, Npc n, Npc target, NpcArchetype a, float dt) {
        n.state = NpcState.ATTACK;
        n.attackCooldown -= dt;
        double d = Math.sqrt(n.distSqTo(target));
        if (d < 1.9) {
            Steering.stop(n);
            if (n.attackCooldown <= 0) {
                n.attackCooldown = 1.2f;
                target.hurt(a.meleeDamage, false);
                target.knockback(n.pos.x, n.pos.z, 2.5f);
                g.audio.playHit();
            }
        } else {
            moveTo(g, n, new Vec3i((int) target.pos.x, (int) target.pos.y,
                    (int) target.pos.z), a.speed * 1.1f, 1.6f);
        }
    }

    private static void engageCreature(Game g, Npc n, Creature target, NpcArchetype a, float dt) {
        n.state = NpcState.ATTACK;
        n.attackCooldown -= dt;
        double d = Math.sqrt(n.distSqTo(target));
        if (d < 1.9) {
            Steering.stop(n);
            if (n.attackCooldown <= 0) {
                n.attackCooldown = 1.2f;
                target.hurt(a.meleeDamage, false);
                target.bleedTimer = 12f;
                target.fear = 1f;
                g.audio.playHit();
            }
        } else if (d < 14) {
            moveTo(g, n, new Vec3i((int) target.pos.x, (int) target.pos.y,
                    (int) target.pos.z), a.speed * 1.1f, 1.6f);
        }
    }

    private static Npc nearestHostileNpc(Game g, Vec3i around, float range) {
        Npc best = null;
        double bestD = range * range;
        for (Npc o : g.entities.npcs) {
            if (o.dead || !o.hostileToPlayer()) {
                continue;
            }
            double d = o.distSqTo(around.x(), around.y(), around.z());
            if (d < bestD) {
                bestD = d;
                best = o;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Daily life
    // ------------------------------------------------------------------

    private static void dailyLife(Game g, Npc n, Settlement s, NpcArchetype a, float dt) {
        if (s == null) {
            // Orphaned war-party members head home... or fade at the edge.
            n.leaveTimer -= dt;
            if (n.leaveTimer < -300) {
                n.dead = true;
                n.lastHitByPlayer = false;
            }
            wanderNear(g, n, (int) n.pos.x, (int) n.pos.z, 8, a.speed * 0.7f, dt);
            return;
        }

        if (n.hunger >= 65 && eatFromSettlement(g, n, s, a, dt)) {
            return;
        }

        if (n.health < n.maxHealth * 0.55f && a != NpcArchetype.MEDIC) {
            Npc medic = null;
            for (Npc other : g.entities.npcs) {
                if (!other.dead && other.settlementId == n.settlementId
                        && other.archetype == NpcArchetype.MEDIC) {
                    medic = other;
                    break;
                }
            }
            if (medic != null) {
                n.state = NpcState.HEAL;
                moveTo(g, n, new Vec3i((int) medic.pos.x, (int) medic.pos.y,
                        (int) medic.pos.z), a.speed, 1.5f);
                return;
            }
        }

        boolean night = g.time.isNight();
        if (night && !a.hostileArchetype()) {
            // Sleep at the assigned bed.
            n.state = NpcState.SLEEP;
            moveTo(g, n, homePos(g, s, n), a.speed * 0.8f, 1.4f);
            return;
        }

        switch (a) {
            case GUARD, ARCHER, HUNTER, TRACKER, SCOUT -> patrol(g, n, s, a);
            case BRUTE -> {
                n.state = NpcState.GUARD;
                Vec3i post = s.gates.isEmpty() ? s.center : s.gates.getFirst();
                if (!moveTo(g, n, post, a.speed * 0.8f, 2.5f)) {
                    Steering.stop(n);
                }
            }
            case POWDERMAN -> {
                n.state = NpcState.GUARD;
                Vec3i post = s.magazinePos != null ? s.magazinePos
                        : (s.leaderPost != null ? s.leaderPost : s.center);
                if (!moveTo(g, n, post, a.speed * 0.8f, 3f)) {
                    Steering.stop(n);
                }
            }
            case LEADER -> {
                n.state = NpcState.GUARD;
                Vec3i post = s.leaderPost != null ? s.leaderPost : s.center;
                if (!moveTo(g, n, post, a.speed * 0.8f, 2.5f)) {
                    Steering.stop(n);
                    n.yaw += dt * 20f;
                }
            }
            case MEDIC -> medicDuty(g, n, s, a, dt);
            default -> workDuty(g, n, s, a, dt);
        }
    }

    private static boolean eatFromSettlement(Game g, Npc n, Settlement s,
                                             NpcArchetype a, float dt) {
        Vec3i meal = s.center;
        n.state = NpcState.WARM_BY_FIRE;
        if (n.distSqTo(meal.x() + 0.5f, n.pos.y, meal.z() + 0.5f) > 3 * 3) {
            moveTo(g, n, meal, a.speed * 0.8f, 2f);
            return true;
        }
        Steering.stop(n);
        n.workTimer += dt;
        if (n.workTimer >= 2f) {
            n.workTimer = 0;
            if (s.foodStock > 0) {
                s.foodStock--;
                n.hunger = Math.max(0, n.hunger - 75);
                n.mood = Math.min(100, n.mood + 3);
            } else {
                n.mood = Math.max(0, n.mood - 3);
            }
        }
        return true;
    }

    private static void patrol(Game g, Npc n, Settlement s, NpcArchetype a) {
        n.state = NpcState.GUARD;
        List<Vec3i> route = s.patrolPoints;
        if (route.isEmpty()) {
            wanderNear(g, n, s.center.x(), s.center.z(), s.radius - 2,
                    a.speed * 0.7f, 0);
            return;
        }
        if (n.patrolIndex < 0 || n.patrolIndex >= route.size()) {
            n.patrolIndex = Math.floorMod(n.residentIndex, route.size());
        }
        Vec3i wp = route.get(n.patrolIndex);
        if (!moveTo(g, n, wp, a.speed * 0.75f, 2.2f)) {
            n.patrolIndex = (n.patrolIndex + 1) % route.size();
        }
    }

    private static void medicDuty(Game g, Npc n, Settlement s, NpcArchetype a, float dt) {
        // Tend the most wounded live resident.
        Npc patient = null;
        for (Npc o : g.entities.npcs) {
            if (o != n && o.settlementId == n.settlementId && !o.dead
                    && o.health < o.maxHealth * 0.8f) {
                patient = o;
                break;
            }
        }
        if (patient != null) {
            if (Math.sqrt(n.distSqTo(patient)) < 1.8) {
                Steering.stop(n);
                faceToward(n, patient.pos.x, patient.pos.z);
                n.workTimer += dt;
                if (n.workTimer > 2.5f) {
                    n.workTimer = 0;
                    if (s.medStock > 0) {
                        s.medStock--;
                        patient.health = Math.min(patient.maxHealth, patient.health + 8);
                        patient.sick = false;
                        patient.sickTimer = 0;
                    }
                }
            } else {
                moveTo(g, n, new Vec3i((int) patient.pos.x, (int) patient.pos.y,
                        (int) patient.pos.z), a.speed, 1.5f);
            }
            return;
        }
        workDuty(g, n, s, a, dt);
    }

    private static void workDuty(Game g, Npc n, Settlement s, NpcArchetype a, float dt) {
        n.state = a == NpcArchetype.TRADER ? NpcState.TRADE : NpcState.IDLE;
        Vec3i duty = dutyPos(s, n);
        double d2 = n.distSqTo(duty.x() + 0.5f, n.pos.y, duty.z() + 0.5f);
        if (d2 > 3.5 * 3.5) {
            moveTo(g, n, duty, a.speed * 0.8f, 2.5f);
        } else {
            // Social idle: shuffle near the duty point, sometimes face a neighbor.
            n.workTimer += dt;
            if (n.workTimer >= 30f) {
                n.workTimer = 0;
                switch (a) {
                    case FARMER -> s.foodStock = Math.min(999, s.foodStock + 1);
                    case SMITH -> {
                        if (s.woodStock > 0) {
                            s.woodStock--;
                            s.metalStock = Math.min(999, s.metalStock + 1);
                        }
                    }
                    case MEDIC -> {
                        if (s.foodStock > 0) {
                            s.foodStock--;
                            s.medStock = Math.min(999, s.medStock + 1);
                        }
                    }
                    case VILLAGER -> s.woodStock = Math.min(999, s.woodStock + 1);
                    default -> {
                    }
                }
            }
            wanderNear(g, n, duty.x(), duty.z(), 3, a.speed * 0.5f, dt);
        }
    }

    private static Vec3i homePos(Game g, Settlement s, Npc n) {
        if (n.residentIndex >= 0 && n.residentIndex < s.residents.size()) {
            Settlement.Resident r = s.residents.get(n.residentIndex);
            if (r.bedIndex >= 0 && !s.beds.isEmpty()) {
                return g.settlementManager.residentSleepPosition(g, s, r);
            }
        }
        return s.center;
    }

    private static Vec3i dutyPos(Settlement s, Npc n) {
        if (!s.dutyPoints.isEmpty()) {
            int idx = n.residentIndex >= 0 && n.residentIndex < s.residents.size()
                    ? s.residents.get(n.residentIndex).dutyIndex : 0;
            return s.dutyPoints.get(Math.floorMod(idx, s.dutyPoints.size()));
        }
        return s.center;
    }

    private static void wanderNear(Game g, Npc n, int cx, int cz, int radius,
                                   float speed, float dt) {
        if (!n.hasTarget || n.distSqTo(n.target.x, n.pos.y, n.target.z) < 1.4f) {
            if (g.entities.nextSettledNpcAiFloat() < 0.02f || !n.hasTarget) {
                n.target.set(cx + g.entities.nextSettledNpcAiInt(radius * 2 + 1) - radius,
                        n.pos.y,
                        cz + g.entities.nextSettledNpcAiInt(radius * 2 + 1) - radius);
                n.hasTarget = true;
            } else {
                Steering.stop(n);
                return;
            }
        }
        Steering.moveToward(n, n.target.x, n.target.z, speed);
    }

    // ------------------------------------------------------------------
    // Movement: cached A* with steering fallback and gate handling
    // ------------------------------------------------------------------

    /**
     * Moves toward a target using the cached path; returns false when arrived.
     * Falls back to direct steering when pathing fails or the target is close.
     */
    private static boolean moveTo(Game g, Npc n, Vec3i target, float speed, float arrive) {
        double flat = Math.sqrt((n.pos.x - target.x() - 0.5) * (n.pos.x - target.x() - 0.5)
                + (n.pos.z - target.z() - 0.5) * (n.pos.z - target.z() - 0.5));
        if (flat < arrive && Math.abs(n.pos.y - target.y()) < 3) {
            Steering.stop(n);
            n.path = null;
            return false;
        }

        // Validate / advance the cached path.
        if (n.path != null) {
            if (n.pathIndex >= n.path.size()) {
                n.path = null;
            } else {
                Vec3i last = n.path.getLast();
                if (last.distSq(target.x() + 0.5, target.y() + 0.5, target.z() + 0.5) > 6 * 6) {
                    n.path = null; // target moved too far from the planned goal
                }
            }
        }

        if (n.path == null && n.repathCooldown <= 0) {
            n.repathCooldown = REPATH_COOLDOWN
                    + g.entities.nextSettledNpcAiFloat() * 0.7f;
            n.path = Pathfinder.find(g.world,
                    (int) Math.floor(n.pos.x), (int) Math.floor(n.pos.y), (int) Math.floor(n.pos.z),
                    target.x(), target.y(), target.z(), Pathfinder.DEFAULT_BUDGET);
            n.pathIndex = 0;
        }

        if (n.path != null && n.pathIndex < n.path.size()) {
            Vec3i cell = n.path.get(n.pathIndex);
            // Gates along the path get shoved open.
            BlockType inCell = g.world.getBlock(cell.x(), cell.y(), cell.z());
            BlockType headCell = g.world.getBlock(cell.x(), cell.y() + 1, cell.z());
            if (inCell == BlockType.GATE) {
                g.settlementManager.openGate(g, cell);
            }
            if (headCell == BlockType.GATE) {
                g.settlementManager.openGate(g, new Vec3i(cell.x(), cell.y() + 1, cell.z()));
            }
            double cd = n.distSqTo(cell.x() + 0.5f, n.pos.y, cell.z() + 0.5f);
            if (cd < 0.55 * 0.55 && Math.abs(n.pos.y - cell.y()) < 1.6f) {
                n.pathIndex++;
            } else {
                Steering.moveToward(n, cell.x() + 0.5f, cell.z() + 0.5f, speed);
                // Climb ladders along the path.
                if (cell.y() > n.pos.y + 0.6f && n.onLadder) {
                    n.vel.y = 2.4f;
                }
            }
            return true;
        }

        // Fallback: direct steering (existing behavior).
        Steering.moveToward(n, target.x() + 0.5f, target.z() + 0.5f, speed);
        return true;
    }

    private static void faceToward(Npc n, float tx, float tz) {
        n.yaw = (float) Math.toDegrees(Math.atan2(tx - n.pos.x, -(tz - n.pos.z)));
    }
}
