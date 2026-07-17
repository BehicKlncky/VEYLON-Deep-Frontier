package com.veylon.ai;

import com.veylon.Game;
import com.veylon.entity.Carcass;
import com.veylon.entity.Creature;
import com.veylon.entity.Creature.CreatureState;
import com.veylon.entity.Creature.CreatureType;
import com.veylon.entity.Player;
import com.veylon.entity.Track;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;

import java.util.Random;

/**
 * Wildlife AI: finite states driven by a perception model. Predators hear
 * noise (sprinting, mining), smell blood and raw meat, lose track of crouched
 * prey, follow blood trails and scavenge carcasses. Fire and light are feared.
 */
public final class CreatureAI {

    private static final Random RNG = new Random();

    /** Re-seeds decision jitter so a fixed world seed replays identically. */
    public static void reseed(long seed) {
        RNG.setSeed(seed);
    }

    private static final float STALKER_STRONG_LIGHT = 0.42f;
    private static final float STALKER_DARK_PREY_LIGHT = 0.32f;
    private static final float STALKER_AMBUSH_RANGE = 5.25f;

    private CreatureAI() {
    }

    /**
     * Distance at which a predator notices the player, shaped by stance,
     * recent noise and carried scent.
     */
    public static float detectionRange(Game g, float base) {
        Player p = g.player;
        float range = base;
        if (p.crouching) {
            range *= 0.5f;
        }
        range += p.noise * 8f;
        range += p.scent * 9f;
        return range;
    }

    public static void update(Game g, Creature c, float dt) {
        c.decideTimer -= dt;
        c.attackCooldown -= dt;
        c.hunger = Math.min(100, c.hunger + 0.06f * dt);
        c.fear = Math.max(0, c.fear - 0.1f * dt);
        c.bobPhase += dt * (Math.abs(c.vel.x) + Math.abs(c.vel.z)) * 2.2f;

        switch (c.type) {
            case BIRD -> updateBird(g, c, dt);
            case HARE -> updateHare(g, c, dt);
            case THORNHORN -> updateThornhorn(g, c, dt);
            case STALKER -> updateStalker(g, c, dt);
            case WOLF -> updatePredatorCommon(g, c, dt);
            case DEER -> updateDeer(g, c, dt);
        }
    }

    private static boolean panicFromFire(Game g, Creature c) {
        Vec3i fire = g.fire.nearestBurning(c.pos.x, c.pos.y, c.pos.z, 8);
        if (fire != null) {
            fleeFrom(c, fire.x() + 0.5f, fire.z() + 0.5f);
            Steering.moveToward(c, c.target.x, c.target.z, c.type.speed * 1.5f);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Herbivores
    // ------------------------------------------------------------------

    private static void updateDeer(Game g, Creature c, float dt) {
        if (panicFromFire(g, c)) {
            return;
        }
        Player p = g.player;
        double playerDist = Math.sqrt(c.distSqTo(p));
        Creature wolf = g.entities.nearestCreature(c.pos.x, c.pos.y, c.pos.z, 11, x -> x.type.predator);

        // Storms and being hurt make deer skittish; crouching players get closer.
        float panicRange = g.weather.isStormy() ? 12f : 7f;
        if (c.health < c.maxHealth || c.fear > 0.5f) {
            panicRange = 16f;
        }
        if (p.crouching) {
            panicRange *= 0.55f;
        }
        panicRange += p.noise * 6f;

        if (wolf != null) {
            c.state = CreatureState.FLEE;
            fleeFrom(c, wolf.pos.x, wolf.pos.z);
        } else if (playerDist < panicRange) {
            if (c.state != CreatureState.FLEE) {
                g.audio.playDeerCall(c.pos.x, c.pos.y, c.pos.z);
            }
            c.state = CreatureState.FLEE;
            fleeFrom(c, p.pos.x, p.pos.z);
        } else if (c.decideTimer <= 0) {
            c.decideTimer = 1.5f + RNG.nextFloat() * 2.5f;
            if (g.time.isNight() && RNG.nextFloat() < 0.6f) {
                c.state = CreatureState.REST;
            } else if (c.hunger > 55) {
                c.state = CreatureState.GRAZE;
                pickNearbyPoint(c, 9);
            } else if (RNG.nextFloat() < 0.12f) {
                c.state = CreatureState.SEEK_WATER;
                pickNearbyPoint(c, 14);
            } else {
                c.state = CreatureState.WANDER;
                pickNearbyPoint(c, 12);
            }
        }

        switch (c.state) {
            case FLEE -> Steering.moveToward(c, c.target.x, c.target.z, c.type.speed * 1.6f);
            case REST -> Steering.stop(c);
            case GRAZE -> grazeBehavior(g, c, dt);
            default -> {
                if (c.hasTarget && c.distSqTo(c.target.x, c.pos.y, c.target.z) > 1.5) {
                    Steering.moveToward(c, c.target.x, c.target.z, c.type.speed * 0.75f);
                } else {
                    Steering.stop(c);
                }
            }
        }
    }

    private static void grazeBehavior(Game g, Creature c, float dt) {
        if (c.hasTarget && c.distSqTo(c.target.x, c.pos.y, c.target.z) > 1.2) {
            Steering.moveToward(c, c.target.x, c.target.z, c.type.speed * 0.6f);
        } else {
            Steering.stop(c);
            c.eatTimer += dt;
            if (c.eatTimer > 2.5f) {
                c.eatTimer = 0;
                c.hunger = Math.max(0, c.hunger - 40);
                // Herbivores actually consume plants.
                int bx = (int) Math.floor(c.pos.x), bz = (int) Math.floor(c.pos.z);
                int by = (int) Math.floor(c.pos.y);
                for (int dy = 0; dy <= 1; dy++) {
                    BlockType t = g.world.getBlock(bx, by + dy, bz);
                    if (t == BlockType.TALL_GRASS || t == BlockType.BUSH) {
                        g.world.setBlock(bx, by + dy, bz, BlockType.AIR, true);
                        break;
                    }
                }
                c.state = CreatureState.WANDER;
            }
        }
    }

    private static void updateHare(Game g, Creature c, float dt) {
        if (panicFromFire(g, c)) {
            return;
        }
        Player p = g.player;
        double playerDist = Math.sqrt(c.distSqTo(p));
        Creature threat = g.entities.nearestCreature(c.pos.x, c.pos.y, c.pos.z, 9, x -> x.type.predator);
        float spook = p.crouching ? 4.5f : 8f;
        if (threat != null) {
            c.state = CreatureState.FLEE;
            fleeFrom(c, threat.pos.x, threat.pos.z);
            Steering.moveToward(c, c.target.x, c.target.z, c.type.speed * 1.6f);
            return;
        }
        if (playerDist < spook + p.noise * 5f) {
            c.state = CreatureState.FLEE;
            fleeFrom(c, p.pos.x, p.pos.z);
            Steering.moveToward(c, c.target.x, c.target.z, c.type.speed * 1.5f);
            return;
        }
        if (c.decideTimer <= 0) {
            c.decideTimer = 1f + RNG.nextFloat() * 2f;
            if (c.hunger > 50) {
                c.state = CreatureState.GRAZE;
            } else {
                c.state = CreatureState.WANDER;
            }
            pickNearbyPoint(c, 7);
        }
        if (c.state == CreatureState.GRAZE) {
            grazeBehavior(g, c, dt);
        } else if (c.hasTarget && c.distSqTo(c.target.x, c.pos.y, c.target.z) > 1) {
            Steering.moveToward(c, c.target.x, c.target.z, c.type.speed * 0.7f);
        } else {
            Steering.stop(c);
        }
    }

    private static void updateThornhorn(Game g, Creature c, float dt) {
        if (panicFromFire(g, c)) {
            return;
        }
        Player p = g.player;
        double playerDist = Math.sqrt(c.distSqTo(p));

        // A wounded thornhorn charges its attacker instead of fleeing.
        if (c.health < c.maxHealth && c.fear > 0.4f && playerDist < 16 && !p.dead) {
            c.state = CreatureState.CHARGE;
        }
        if (c.state == CreatureState.CHARGE) {
            if (playerDist < 1.9) {
                Steering.stop(c);
                if (c.attackCooldown <= 0) {
                    c.attackCooldown = 1.6f;
                    p.hurtPhysical(g, 9, false);
                    p.knockback(c.pos.x, c.pos.z, 6.5f);
                    g.audio.playHit();
                    g.noise.emit(g, c.pos.x, c.pos.y, c.pos.z,
                            24f, 0.7f, "creature-attack", false, c);
                    g.log("The Thornhorn gores you! (-9 HP)");
                }
            } else if (playerDist > 20 || p.dead) {
                c.state = CreatureState.WANDER;
                c.fear = 0;
            } else {
                Steering.moveToward(c, p.pos.x, p.pos.z, c.type.speed * 1.7f);
            }
            return;
        }

        if (c.decideTimer <= 0) {
            c.decideTimer = 2f + RNG.nextFloat() * 3f;
            c.state = c.hunger > 50 ? CreatureState.GRAZE : CreatureState.WANDER;
            pickNearbyPoint(c, 10);
        }
        if (c.state == CreatureState.GRAZE) {
            grazeBehavior(g, c, dt);
        } else if (c.hasTarget && c.distSqTo(c.target.x, c.pos.y, c.target.z) > 1.6) {
            Steering.moveToward(c, c.target.x, c.target.z, c.type.speed * 0.6f);
        } else {
            Steering.stop(c);
        }
    }

    // ------------------------------------------------------------------
    // Predators
    // ------------------------------------------------------------------

    private static void updatePredatorCommon(Game g, Creature c, float dt) {
        Player p = g.player;
        double playerDist = Math.sqrt(c.distSqTo(p));

        if (panicFromFire(g, c)) {
            return;
        }

        // Wolves avoid light/campfires.
        float lightHere = g.world.blockLight((int) c.pos.x, (int) c.pos.y, (int) c.pos.z);
        if (lightHere > 0.35f) {
            c.state = CreatureState.FLEE;
            fleeFrom(c, c.pos.x + RNG.nextFloat() - 0.5f, c.pos.z + RNG.nextFloat() - 0.5f);
            Vec3i camp = g.world.campPos;
            if (camp != null) {
                fleeFrom(c, camp.x(), camp.z());
            }
            Steering.moveToward(c, c.target.x, c.target.z, c.type.speed);
            return;
        }

        if (c.health < c.maxHealth * 0.3f) {
            c.state = CreatureState.FLEE_HURT;
            fleeFrom(c, p.pos.x, p.pos.z);
            Steering.moveToward(c, c.target.x, c.target.z, c.type.speed * 1.5f);
            return;
        }

        boolean hungry = c.hunger > 45;

        // Scavenge nearby carcasses when hungry.
        if (hungry) {
            Carcass meal = g.entities.nearestCarcass(c.pos.x, c.pos.y, c.pos.z, 30);
            if (meal != null && meal.meatLeft > 0) {
                double d = Math.sqrt(c.distSqTo(meal.pos.x, meal.pos.y, meal.pos.z));
                if (d < 1.6) {
                    Steering.stop(c);
                    c.eatTimer += dt;
                    if (c.eatTimer > 3f) {
                        c.eatTimer = 0;
                        meal.meatLeft--;
                        c.hunger = Math.max(0, c.hunger - 50);
                    }
                } else {
                    c.state = CreatureState.TRACK;
                    Steering.moveToward(c, meal.pos.x, meal.pos.z, c.type.speed * 0.95f);
                }
                return;
            }
        }

        float baseRange = g.time.isNight() ? 15 : 9;
        boolean stalkPlayer = hungry && playerDist < detectionRange(g, baseRange) && !p.dead;

        Creature prey = g.entities.nearestCreature(c.pos.x, c.pos.y, c.pos.z, 26,
                x -> x.type == CreatureType.DEER || x.type == CreatureType.HARE);

        if (stalkPlayer && (prey == null || c.distSqTo(prey) > playerDist * playerDist)) {
            CreatureState old = c.state;
            c.state = playerDist < 2.2 ? CreatureState.ATTACK : CreatureState.STALK;
            if (c.state == CreatureState.STALK && old != CreatureState.STALK
                    && old != CreatureState.ATTACK) {
                g.audio.playGrowl(c.pos.x, c.pos.y, c.pos.z);
            }
            c.targetEntity = null;
            if (c.state == CreatureState.ATTACK) {
                Steering.stop(c);
                if (c.attackCooldown <= 0) {
                    c.attackCooldown = 1.3f;
                    p.hurtPhysical(g, 6, true);
                    p.knockback(c.pos.x, c.pos.z, 3.5f);
                    g.audio.playHit();
                    g.audio.playHurt();
                    g.noise.emit(g, c.pos.x, c.pos.y, c.pos.z,
                            20f, 0.65f, "creature-attack", false, c);
                    g.log("An Ashwolf bit you! Check for bleeding.");
                }
            } else {
                Steering.moveToward(c, p.pos.x, p.pos.z, c.type.speed * 1.25f);
            }
            return;
        }

        if (hungry && prey != null) {
            double d = Math.sqrt(c.distSqTo(prey));
            if (d < 1.6) {
                c.state = CreatureState.ATTACK;
                Steering.stop(c);
                if (c.attackCooldown <= 0) {
                    c.attackCooldown = 1.0f;
                    prey.hurt(7, false);
                    prey.fear = 1f;
                    prey.bleedTimer = 20f;
                    g.noise.emit(g, c.pos.x, c.pos.y, c.pos.z,
                            18f, 0.65f, "creature-attack", false, c);
                    if (prey.dead) {
                        c.hunger = 0;
                    }
                }
            } else {
                c.state = d < 10 ? CreatureState.HUNT : CreatureState.TRACK;
                Steering.moveToward(c, prey.pos.x, prey.pos.z,
                        c.type.speed * (c.state == CreatureState.HUNT ? 1.35f : 0.9f));
            }
            return;
        }

        // No visible prey: hungry wolves follow fresh blood trails.
        if (hungry) {
            Track blood = nearestBloodTrack(g, c, 25);
            if (blood != null) {
                c.state = CreatureState.TRACK;
                Steering.moveToward(c, blood.x, blood.z, c.type.speed * 0.9f);
                if (c.distSqTo(blood.x, c.pos.y, blood.z) < 2) {
                    blood.age = Track.MAX_AGE + 1; // consumed the scent
                }
                return;
            }
        }

        if (c.decideTimer <= 0) {
            c.decideTimer = 2f + RNG.nextFloat() * 3f;
            c.state = CreatureState.WANDER;
            pickNearbyPoint(c, 16);
            // Night chorus.
            if (g.time.isNight() && RNG.nextFloat() < 0.12f) {
                g.audio.playHowl(c.pos.x, c.pos.y, c.pos.z);
            }
        }
        if (c.hasTarget && c.distSqTo(c.target.x, c.pos.y, c.target.z) > 2) {
            Steering.moveToward(c, c.target.x, c.target.z, c.type.speed * 0.7f);
        } else {
            Steering.stop(c);
        }
    }

    private static Track nearestBloodTrack(Game g, Creature c, float range) {
        Track best = null;
        double bestD = range * range;
        for (Track t : g.entities.tracks) {
            if (!t.blood || !t.fresh()) {
                continue;
            }
            double dx = t.x - c.pos.x, dz = t.z - c.pos.z;
            double d = dx * dx + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = t;
            }
        }
        return best;
    }

    private static void updateStalker(Game g, Creature c, float dt) {
        Player p = g.player;
        double playerDist = Math.sqrt(c.distSqTo(p));

        // Burning cells are not voxel light emitters. Check the actual fire
        // simulation first so a burning log or spreading fire is still a hard
        // deterrent in an otherwise dark cave.
        Vec3i fire = g.fire.nearestBurning(c.pos.x, c.pos.y, c.pos.z, 10);
        if (fire != null) {
            c.state = CreatureState.FLEE;
            fleeFrom(c, fire.x() + 0.5f, fire.z() + 0.5f);
            Steering.moveToward(c, c.target.x, c.target.z, c.type.speed * 1.6f);
            return;
        }

        // Strong torch, lantern, fungus, or daylight exposure makes a stalker
        // retreat down the local light gradient. This avoids the old behavior
        // where it could run toward a torch merely because the player happened
        // to stand on the opposite side.
        float lightHere = lightExposure(g, c.pos.x, c.pos.y, c.pos.z);
        if (lightHere > STALKER_STRONG_LIGHT) {
            c.state = CreatureState.FLEE;
            retreatDownLightGradient(g, c, p);
            Steering.moveToward(c, c.target.x, c.target.z, c.type.speed * 1.4f);
            return;
        }

        if (c.health < c.maxHealth * 0.25f) {
            c.state = CreatureState.FLEE_HURT;
            fleeFrom(c, p.pos.x, p.pos.z);
            Steering.moveToward(c, c.target.x, c.target.z, c.type.speed * 1.5f);
            return;
        }

        float range = detectionRange(g, 18);
        if (playerDist < range && !p.dead) {
            // Gloomstalkers do not convert every detection into a straight-line
            // chase. Illumination around the prey makes them give ground and
            // wait for darkness instead.
            float preyLight = lightExposure(g, p.pos.x, p.pos.y, p.pos.z);
            if (preyLight > STALKER_DARK_PREY_LIGHT) {
                c.state = CreatureState.FLEE;
                fleeFrom(c, p.pos.x, p.pos.z);
                Steering.moveToward(c, c.target.x, c.target.z, c.type.speed * 0.8f);
                return;
            }

            if (playerDist < 2.0) {
                c.state = CreatureState.ATTACK;
                Steering.stop(c);
                if (c.attackCooldown <= 0) {
                    c.attackCooldown = 1.2f;
                    p.hurtPhysical(g, 8, true);
                    p.knockback(c.pos.x, c.pos.z, 3f);
                    g.audio.playHit();
                    g.audio.playHurt();
                    g.noise.emit(g, c.pos.x, c.pos.y, c.pos.z,
                            22f, 0.7f, "creature-attack", false, c);
                    g.log("A Gloomstalker rakes you from the dark!");
                }
            } else if (playerDist <= STALKER_AMBUSH_RANGE) {
                // Once a successful stalk reaches striking distance, commit to
                // a short, fast ambush burst.
                if (c.state != CreatureState.HUNT && c.state != CreatureState.STALK) {
                    g.audio.playGrowl(c.pos.x, c.pos.y, c.pos.z);
                }
                c.state = CreatureState.HUNT;
                c.hasTarget = false;
                Steering.moveToward(c, p.pos.x, p.pos.z, c.type.speed * 1.5f);
            } else {
                // At range, advance obliquely through the darker flank instead
                // of homing directly onto the player. A persistent target keeps
                // the choice stable for a short interval and prevents jitter.
                boolean enteringStalk = c.state != CreatureState.STALK;
                c.state = CreatureState.STALK;
                if (enteringStalk || !c.hasTarget || c.decideTimer <= 0
                        || c.distSqTo(c.target.x, c.pos.y, c.target.z) < 1.5) {
                    pickDarkFlank(g, c, p, playerDist);
                    c.decideTimer = 1.25f;
                }
                if (enteringStalk) {
                    g.audio.playGrowl(c.pos.x, c.pos.y, c.pos.z);
                }
                Steering.moveToward(c, c.target.x, c.target.z, c.type.speed * 0.68f);
            }
            return;
        }

        if (c.decideTimer <= 0) {
            c.decideTimer = 2f + RNG.nextFloat() * 4f;
            c.state = CreatureState.WANDER;
            pickNearbyPoint(c, 10);
        }
        if (c.hasTarget && c.distSqTo(c.target.x, c.pos.y, c.target.z) > 2) {
            Steering.moveToward(c, c.target.x, c.target.z, c.type.speed * 0.6f);
        } else {
            Steering.stop(c);
        }
    }

    /** Combined dynamic/sky exposure used by the cave predator's decisions. */
    private static float lightExposure(Game g, float x, float y, float z) {
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        float block = g.world.blockLight(bx, by, bz);
        float sky = g.world.skyLight(bx, by, bz) * (float) g.time.dayLight();
        return Math.max(block, sky);
    }

    /** Finds the uphill light direction with four cheap samples and flees it. */
    private static void retreatDownLightGradient(Game g, Creature c, Player p) {
        float step = 1.5f;
        float gx = lightExposure(g, c.pos.x + step, c.pos.y, c.pos.z)
                - lightExposure(g, c.pos.x - step, c.pos.y, c.pos.z);
        float gz = lightExposure(g, c.pos.x, c.pos.y, c.pos.z + step)
                - lightExposure(g, c.pos.x, c.pos.y, c.pos.z - step);
        float lengthSq = gx * gx + gz * gz;
        if (lengthSq > 0.0001f) {
            fleeFrom(c, c.pos.x + gx, c.pos.z + gz);
        } else {
            // A perfectly even source (or broad daylight) has no useful local
            // gradient; the player is the safest deterministic fallback.
            fleeFrom(c, p.pos.x, p.pos.z);
        }
    }

    /**
     * Chooses between two lateral approaches, preferring the darker sample.
     * The deterministic tie-break prevents every stalker from circling in the
     * same direction without adding save state or changing serialized enums.
     */
    private static void pickDarkFlank(Game g, Creature c, Player p, double playerDist) {
        float dx = p.pos.x - c.pos.x;
        float dz = p.pos.z - c.pos.z;
        float len = (float) Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
        float nx = dx / len;
        float nz = dz / len;
        float advance = Math.min(3.5f, Math.max(1.5f, (float) playerDist - STALKER_AMBUSH_RANGE));
        float lateral = Math.min(4.5f, 1.5f + (float) playerDist * 0.22f);

        float leftX = c.pos.x + nx * advance - nz * lateral;
        float leftZ = c.pos.z + nz * advance + nx * lateral;
        float rightX = c.pos.x + nx * advance + nz * lateral;
        float rightZ = c.pos.z + nz * advance - nx * lateral;
        float leftLight = lightExposure(g, leftX, c.pos.y, leftZ);
        float rightLight = lightExposure(g, rightX, c.pos.y, rightZ);

        boolean left;
        if (Math.abs(leftLight - rightLight) > 0.01f) {
            left = leftLight < rightLight;
        } else {
            int cellHash = (int) Math.floor(c.pos.x) * 73428767
                    ^ (int) Math.floor(c.pos.z) * 912931;
            left = (cellHash & 1) == 0;
        }
        c.target.set(left ? leftX : rightX, c.pos.y, left ? leftZ : rightZ);
        c.hasTarget = true;
    }

    private static void updateBird(Game g, Creature c, float dt) {
        double playerDist = Math.sqrt(c.distSqTo(g.player));
        if (playerDist < 6) {
            // Startled: fly up and away.
            float fx = c.pos.x + (c.pos.x - g.player.pos.x);
            float fz = c.pos.z + (c.pos.z - g.player.pos.z);
            Steering.flyToward(c, fx, c.pos.y + 6, fz, c.type.speed * 1.5f);
            g.audio.playBirdFlap(c.pos.x, c.pos.y, c.pos.z);
            return;
        }
        if (c.decideTimer <= 0) {
            c.decideTimer = 2f + RNG.nextFloat() * 4f;
            int x = (int) (c.pos.x + RNG.nextInt(31) - 15);
            int z = (int) (c.pos.z + RNG.nextInt(31) - 15);
            float groundY = 40;
            if (g.world.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16)) != null) {
                groundY = g.world.surfaceHeight(x, z);
            }
            c.target.set(x, groundY + 5 + RNG.nextFloat() * 8, z);
            c.hasTarget = true;
            if (!g.time.isNight() && RNG.nextFloat() < 0.25f) {
                g.audio.playChirp(c.pos.x, c.pos.y, c.pos.z);
            }
        }
        if (c.hasTarget) {
            Steering.flyToward(c, c.target.x, c.target.y, c.target.z, c.type.speed * 0.8f);
            if (c.distSqTo(c.target.x, c.target.y, c.target.z) < 2) {
                c.hasTarget = false;
                c.vel.set(0, 0, 0);
            }
        }
    }

    private static void fleeFrom(Creature c, float fx, float fz) {
        float dx = c.pos.x - fx;
        float dz = c.pos.z - fz;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 0.05f) {
            dx = 1;
            dz = 0;
            len = 1;
        }
        c.target.set(c.pos.x + dx / len * 12, c.pos.y, c.pos.z + dz / len * 12);
        c.hasTarget = true;
        c.fear = 1f;
    }

    private static void pickNearbyPoint(Creature c, int radius) {
        c.target.set(
                c.pos.x + RNG.nextInt(radius * 2 + 1) - radius,
                c.pos.y,
                c.pos.z + RNG.nextInt(radius * 2 + 1) - radius);
        c.hasTarget = true;
    }
}
