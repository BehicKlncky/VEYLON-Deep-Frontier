package com.veylon.ai;

import com.veylon.Game;
import com.veylon.entity.Creature;
import com.veylon.entity.Npc;
import com.veylon.entity.Npc.NpcState;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;

import java.util.Random;

/**
 * Camp NPC behavior: jobs (guard, hunter, gatherer, medic), warmth, sleep,
 * defense, building, trading - plus hostile scavenger raiders.
 */
public final class NpcAI {

    private static final Random RNG = new Random();

    private NpcAI() {
    }

    public static void update(Game g, Npc n, float dt) {
        n.decideTimer -= dt;
        n.attackCooldown -= dt;
        n.hunger = Math.min(100, n.hunger + 0.03f * dt);
        n.bobPhase += dt * (Math.abs(n.vel.x) + Math.abs(n.vel.z)) * 2.2f;

        if (n.interactFreeze > 0) {
            n.interactFreeze -= dt;
            Steering.stop(n);
            faceEntity(n, g.player.pos.x, g.player.pos.z);
            return;
        }

        if (n.isTrader) {
            updateTrader(g, n, dt);
            return;
        }
        if (n.raider) {
            updateRaider(g, n, dt);
            return;
        }

        Vec3i camp = n.faction != null ? n.faction.campPos : g.world.campPos;
        if (camp == null) {
            Steering.stop(n);
            return;
        }

        // Threat response first.
        if (n.health < n.maxHealth * 0.30f) {
            n.state = NpcState.FLEE;
            Creature threat = g.entities.nearestCreature(n.pos.x, n.pos.y, n.pos.z, 14, c -> c.type.predator);
            float fx = threat != null ? threat.pos.x : g.player.pos.x;
            float fz = threat != null ? threat.pos.z : g.player.pos.z;
            Steering.moveToward(n, n.pos.x + (n.pos.x - fx), n.pos.z + (n.pos.z - fz), 4.4f);
            return;
        }

        if (n.hostileToPlayer() && Math.sqrt(n.distSqTo(g.player)) < 12 && !g.player.dead) {
            n.state = NpcState.ATTACK;
            double d = Math.sqrt(n.distSqTo(g.player));
            if (d < 1.8) {
                Steering.stop(n);
                faceEntity(n, g.player.pos.x, g.player.pos.z);
                if (n.attackCooldown <= 0) {
                    n.attackCooldown = 1.4f;
                    g.player.hurtPhysical(g, 5, true);
                    g.player.knockback(n.pos.x, n.pos.z, 3f);
                    g.audio.playHit();
                    g.log(n.name + " strikes you!");
                }
            } else {
                Steering.moveToward(n, g.player.pos.x, g.player.pos.z, 3.6f);
            }
            return;
        }

        // Defend the camp against raiders.
        Npc raiderThreat = nearestRaider(g, camp, 14);
        if (raiderThreat != null) {
            n.state = NpcState.ATTACK;
            n.combatTarget = raiderThreat;
            if (n.faction != null) {
                n.faction.alert = Math.min(100, n.faction.alert + 10 * dt);
            }
            double d = Math.sqrt(n.distSqTo(raiderThreat));
            if (d < 1.8) {
                Steering.stop(n);
                if (n.attackCooldown <= 0) {
                    n.attackCooldown = 1.1f;
                    raiderThreat.hurt(7, false);
                    raiderThreat.knockback(n.pos.x, n.pos.z, 2.5f);
                    g.audio.playHit();
                }
            } else {
                Steering.moveToward(n, raiderThreat.pos.x, raiderThreat.pos.z, 3.9f);
            }
            return;
        }

        // Defend against predators.
        Creature predator = g.entities.nearestCreature(camp.x(), camp.y(), camp.z(), 13, c -> c.type.predator);
        if (predator != null) {
            n.state = NpcState.ATTACK;
            n.combatTarget = predator;
            if (n.faction != null) {
                n.faction.alert = Math.min(100, n.faction.alert + 10 * dt);
            }
            double d = Math.sqrt(n.distSqTo(predator));
            if (d < 1.8) {
                Steering.stop(n);
                if (n.attackCooldown <= 0) {
                    n.attackCooldown = 1.1f;
                    predator.hurt(8, false);
                    predator.bleedTimer = 15f;
                }
            } else {
                Steering.moveToward(n, predator.pos.x, predator.pos.z, 3.8f);
            }
            return;
        }

        boolean night = g.time.isNight();
        float envTemp = g.temperature.envTempAt(g, n.pos.x, n.pos.y, n.pos.z);

        // Sick NPCs rest by the fire all day.
        if (n.sick) {
            n.state = NpcState.WARM_BY_FIRE;
            goNear(n, camp, 2.4f, 1.8f);
            return;
        }
        if (night) {
            n.state = NpcState.SLEEP;
            goNear(n, camp, 3.0f, 2.4f);
            return;
        }
        if (envTemp < 1f) {
            n.state = NpcState.WARM_BY_FIRE;
            goNear(n, camp, 2.2f, 3.0f);
            return;
        }

        // Construction work takes priority while an upgrade is in progress.
        if (n.faction != null && n.faction.buildTimer > 0 && n.campIndex % 4 == 2) {
            n.state = NpcState.BUILD;
            goNear(n, new Vec3i(camp.x() + 4, camp.y(), camp.z() - 4), 1.6f, 2.4f);
            return;
        }

        // Job assignment by camp index.
        if (n.state == NpcState.IDLE || n.state == NpcState.SLEEP || n.state == NpcState.WARM_BY_FIRE
                || n.state == NpcState.ATTACK || n.state == NpcState.FLEE || n.state == NpcState.TRADE
                || n.state == NpcState.BUILD) {
            n.state = switch (n.campIndex % 4) {
                case 0 -> NpcState.GUARD;
                case 1 -> NpcState.HUNT;
                case 2 -> (n.faction == null || n.faction.foodStock < n.faction.woodStock)
                        ? NpcState.GATHER_FOOD : NpcState.GATHER_WOOD;
                default -> NpcState.HEAL;
            };
            n.targetBlock = null;
        }

        switch (n.state) {
            case GUARD -> {
                if (n.decideTimer <= 0) {
                    n.decideTimer = 3 + RNG.nextFloat() * 4;
                    double ang = RNG.nextDouble() * Math.PI * 2;
                    n.target.set(camp.x() + (float) Math.cos(ang) * 6, camp.y(),
                            camp.z() + (float) Math.sin(ang) * 6);
                    n.hasTarget = true;
                }
                walkOrStop(n, 1.8f, 1.4f);
            }
            case HUNT -> huntJob(g, n, camp, dt);
            case GATHER_WOOD -> gatherJob(g, n, camp, BlockType.LOG, dt);
            case GATHER_FOOD -> gatherJob(g, n, camp, BlockType.BERRY_BUSH, dt);
            case HEAL -> medicJob(g, n, camp, dt);
            default -> Steering.stop(n);
        }
    }

    private static Npc nearestRaider(Game g, Vec3i camp, float range) {
        Npc best = null;
        double bestD = range * range;
        for (Npc o : g.entities.npcs) {
            if (!o.raider || o.dead) {
                continue;
            }
            double d = o.distSqTo(camp.x(), camp.y(), camp.z());
            if (d < bestD) {
                bestD = d;
                best = o;
            }
        }
        return best;
    }

    /** Hunter: chases small game near the camp and turns kills into food stock. */
    private static void huntJob(Game g, Npc n, Vec3i camp, float dt) {
        Creature prey = g.entities.nearestCreature(camp.x(), camp.y(), camp.z(), 26,
                c -> c.type == Creature.CreatureType.DEER || c.type == Creature.CreatureType.HARE);
        if (prey == null) {
            // Patrol hunting grounds.
            if (n.decideTimer <= 0) {
                n.decideTimer = 4 + RNG.nextFloat() * 4;
                n.target.set(camp.x() + RNG.nextInt(31) - 15, camp.y(), camp.z() + RNG.nextInt(31) - 15);
                n.hasTarget = true;
            }
            walkOrStop(n, 2.4f, 1.5f);
            return;
        }
        double d = Math.sqrt(n.distSqTo(prey));
        if (d < 1.7) {
            Steering.stop(n);
            if (n.attackCooldown <= 0) {
                n.attackCooldown = 1.1f;
                prey.hurt(7, false);
                prey.fear = 1f;
                prey.bleedTimer = 15f;
                if (prey.dead && n.faction != null) {
                    n.faction.foodStock += 2;
                }
            }
        } else {
            Steering.moveToward(n, prey.pos.x, prey.pos.z, 3.4f);
        }
    }

    /** Medic: tends hurt or sick campmates; patches up trusted players too. */
    private static void medicJob(Game g, Npc n, Vec3i camp, float dt) {
        Npc patient = null;
        for (Npc o : g.entities.npcs) {
            if (o != n && !o.isTrader && !o.raider && !o.dead
                    && (o.sick || o.health < o.maxHealth * 0.8f)) {
                patient = o;
                break;
            }
        }
        if (patient != null) {
            double d = Math.sqrt(n.distSqTo(patient));
            if (d < 1.6) {
                Steering.stop(n);
                faceEntity(n, patient.pos.x, patient.pos.z);
                n.workTimer += dt;
                if (n.workTimer > 2.5f) {
                    n.workTimer = 0;
                    patient.health = Math.min(patient.maxHealth, patient.health + 4);
                    if (patient.sick && RNG.nextFloat() < 0.06f) {
                        patient.sick = false;
                        g.log(n.name + " has nursed " + patient.name + " back to health.");
                    }
                }
            } else {
                Steering.moveToward(n, patient.pos.x, patient.pos.z, 2.8f);
            }
            return;
        }
        // Heal a trusted, wounded player visiting the camp.
        if (n.faction != null && n.faction.campPrivileges()
                && g.player.health < g.player.maxHealth * 0.7f
                && g.player.distSqTo(camp.x(), camp.y(), camp.z()) < 12 * 12) {
            double d = Math.sqrt(n.distSqTo(g.player));
            if (d < 1.8) {
                Steering.stop(n);
                faceEntity(n, g.player.pos.x, g.player.pos.z);
                n.workTimer += dt;
                if (n.workTimer > 3f) {
                    n.workTimer = 0;
                    g.player.health = Math.min(g.player.maxHealth, g.player.health + 5);
                    g.log(n.name + " treats your wounds (+5 HP).");
                }
            } else {
                Steering.moveToward(n, g.player.pos.x, g.player.pos.z, 2.6f);
            }
            return;
        }
        goNear(n, camp, 3.5f, 1.6f);
    }

    private static void gatherJob(Game g, Npc n, Vec3i camp, BlockType wanted, float dt) {
        if (n.targetBlock == null && n.decideTimer <= 0) {
            n.decideTimer = 2f;
            n.targetBlock = findBlock(g.world, camp.x(), camp.z(), 22, wanted);
            if (n.targetBlock == null) {
                // Nothing nearby; wander a bit.
                n.target.set(camp.x() + RNG.nextInt(17) - 8, camp.y(), camp.z() + RNG.nextInt(17) - 8);
                n.hasTarget = true;
            }
        }
        if (n.targetBlock != null) {
            Vec3i t = n.targetBlock;
            double d2 = n.distSqTo(t.x() + 0.5f, n.pos.y, t.z() + 0.5f);
            if (d2 > 2.6 * 2.6) {
                Steering.moveToward(n, t.x() + 0.5f, t.z() + 0.5f, 2.6f);
                n.workTimer = 0;
            } else {
                Steering.stop(n);
                faceEntity(n, t.x() + 0.5f, t.z() + 0.5f);
                n.workTimer += dt;
                if (n.workTimer > 3f) {
                    n.workTimer = 0;
                    if (g.world.getBlock(t.x(), t.y(), t.z()) == wanted) {
                        if (wanted == BlockType.LOG) {
                            g.world.setBlock(t.x(), t.y(), t.z(), BlockType.AIR, true);
                            if (n.faction != null) {
                                n.faction.woodStock += 2;
                            }
                        } else {
                            g.world.setBlock(t.x(), t.y(), t.z(), BlockType.BERRY_BUSH_EMPTY, true);
                            if (n.faction != null) {
                                n.faction.foodStock += 2;
                            }
                        }
                    }
                    n.targetBlock = null;
                    n.state = NpcState.IDLE;
                }
            }
        } else {
            walkOrStop(n, 2.2f, 1.2f);
        }
    }

    /** Scavenger raider: hits the camp, steals food, then withdraws. */
    private static void updateRaider(Game g, Npc n, float dt) {
        n.leaveTimer -= dt;
        Vec3i camp = g.world.campPos;
        if (n.leaveTimer <= 0 || camp == null || n.health < n.maxHealth * 0.3f) {
            n.state = NpcState.FLEE;
            if (camp != null) {
                Steering.moveToward(n, n.pos.x + (n.pos.x - camp.x()), n.pos.z + (n.pos.z - camp.z()), 4.6f);
            }
            if (n.leaveTimer < -8 || camp == null) {
                n.dead = true;
                n.lastHitByPlayer = false;
            }
            return;
        }

        n.state = NpcState.RAID;
        // Pick the nearest target: defender NPC or the player.
        Npc defender = null;
        double bestD = 12 * 12;
        for (Npc o : g.entities.npcs) {
            if (o.raider || o.isTrader || o.dead) {
                continue;
            }
            double d = n.distSqTo(o);
            if (d < bestD) {
                bestD = d;
                defender = o;
            }
        }
        double playerD = n.distSqTo(g.player);
        if (!g.player.dead && playerD < bestD && playerD < 10 * 10) {
            // Attack the player.
            double d = Math.sqrt(playerD);
            if (d < 1.8) {
                Steering.stop(n);
                faceEntity(n, g.player.pos.x, g.player.pos.z);
                if (n.attackCooldown <= 0) {
                    n.attackCooldown = 1.3f;
                    g.player.hurtPhysical(g, 6, true);
                    g.player.knockback(n.pos.x, n.pos.z, 3f);
                    g.audio.playHit();
                    g.log("A scavenger slashes at you!");
                }
            } else {
                Steering.moveToward(n, g.player.pos.x, g.player.pos.z, 3.8f);
            }
            return;
        }
        if (defender != null) {
            double d = Math.sqrt(n.distSqTo(defender));
            if (d < 1.8) {
                Steering.stop(n);
                if (n.attackCooldown <= 0) {
                    n.attackCooldown = 1.3f;
                    defender.hurt(6, false);
                    defender.knockback(n.pos.x, n.pos.z, 2.5f);
                    g.audio.playHit();
                }
            } else {
                Steering.moveToward(n, defender.pos.x, defender.pos.z, 3.8f);
            }
            return;
        }
        // No one in the way: go loot the camp stores.
        double dCamp = Math.sqrt(n.distSqTo(camp.x() + 0.5f, n.pos.y, camp.z() + 0.5f));
        if (dCamp > 2.5) {
            Steering.moveToward(n, camp.x() + 0.5f, camp.z() + 0.5f, 3.6f);
        } else {
            Steering.stop(n);
            n.workTimer += dt;
            if (n.workTimer > 4f && g.faction.foodStock > 0) {
                n.workTimer = 0;
                g.faction.foodStock = Math.max(0, g.faction.foodStock - 2);
                g.log("A scavenger makes off with camp food supplies!");
                n.leaveTimer = Math.min(n.leaveTimer, 10);
            }
        }
    }

    private static void updateTrader(Game g, Npc n, float dt) {
        n.leaveTimer -= dt;
        if (n.leaveTimer <= 0) {
            n.dead = true;
            n.lastHitByPlayer = false;
            g.log("The wandering trader has moved on.");
            return;
        }
        double pd = Math.sqrt(n.distSqTo(g.player));
        if (pd > 10) {
            Steering.moveToward(n, g.player.pos.x, g.player.pos.z, 2.8f);
        } else if (n.decideTimer <= 0) {
            n.decideTimer = 3 + RNG.nextFloat() * 3;
            n.target.set(g.player.pos.x + RNG.nextInt(13) - 6, n.pos.y, g.player.pos.z + RNG.nextInt(13) - 6);
            n.hasTarget = true;
        } else {
            walkOrStop(n, 1.6f, 1.5f);
        }
    }

    private static void walkOrStop(Npc n, float speed, float arriveDist) {
        if (n.hasTarget && n.distSqTo(n.target.x, n.pos.y, n.target.z) > arriveDist * arriveDist) {
            Steering.moveToward(n, n.target.x, n.target.z, speed);
        } else {
            n.hasTarget = false;
            Steering.stop(n);
        }
    }

    private static void goNear(Npc n, Vec3i spot, float within, float speed) {
        double d2 = n.distSqTo(spot.x() + 0.5f, n.pos.y, spot.z() + 0.5f);
        if (d2 > within * within) {
            Steering.moveToward(n, spot.x() + 0.5f + RNG.nextFloat() - 0.5f,
                    spot.z() + 0.5f + RNG.nextFloat() - 0.5f, speed);
        } else {
            Steering.stop(n);
        }
    }

    private static void faceEntity(Npc n, float tx, float tz) {
        n.yaw = (float) Math.toDegrees(Math.atan2(tx - n.pos.x, -(tz - n.pos.z)));
    }

    /** Scans columns around a point for the first matching surface-ish block. */
    public static Vec3i findBlock(World world, int cx, int cz, int radius, BlockType wanted) {
        Vec3i best = null;
        int bestD = Integer.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                int x = cx + dx, z = cz + dz;
                if (world.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16)) == null) {
                    continue;
                }
                int h = world.surfaceHeight(x, z);
                for (int y = Math.max(2, h - 1); y <= h + 7; y++) {
                    if (world.getBlock(x, y, z) == wanted) {
                        int d = dx * dx + dz * dz;
                        if (d < bestD) {
                            bestD = d;
                            best = new Vec3i(x, y, z);
                        }
                        break;
                    }
                }
            }
        }
        return best;
    }
}
