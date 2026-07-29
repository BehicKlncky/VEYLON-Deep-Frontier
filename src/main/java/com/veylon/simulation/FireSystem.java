package com.veylon.simulation;

import com.veylon.Game;
import com.veylon.entity.Entity;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static com.veylon.simulation.FireConstants.*;

/**
 * Tick-based fire: burning blocks spread to flammable neighbors, damage nearby
 * entities, and finally turn to ash/air. Rain suppresses, drought accelerates.
 */
public class FireSystem {

    /** Hard ceiling for simultaneously burning world cells. */
    public static final int MAX_ACTIVE_FIRES = 220;

    private final Map<Vec3i, Float> burning = new HashMap<>();
    private final Random rng = new Random();
    public int totalIgnitions = 0;

    public Set<Vec3i> burningCells() {
        return burning.keySet();
    }

    public int count() {
        return burning.size();
    }

    public void reset() {
        burning.clear();
        totalIgnitions = 0;
    }

    /** Deterministic QA hook; normal gameplay retains organic fire variation. */
    public void setRandomSeed(long seed) {
        rng.setSeed(seed);
    }

    public boolean ignite(Game g, int x, int y, int z) {
        BlockType t = g.world.getBlock(x, y, z);
        if (!t.flammable) {
            return false;
        }
        Vec3i p = new Vec3i(x, y, z);
        if (burning.containsKey(p)) {
            return false;
        }
        if (burning.size() >= MAX_ACTIVE_FIRES) {
            return false;
        }
        burning.put(p, BURN_SECONDS_MIN + rng.nextFloat() * BURN_SECONDS_RANGE);
        totalIgnitions++;
        return true;
    }

    public Vec3i nearestBurning(float x, float y, float z, float range) {
        Vec3i best = null;
        double bestD = range * range;
        for (Vec3i p : burning.keySet()) {
            double d = p.distSq(x, y, z);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    public void mediumTick(Game g, float dt) {
        if (burning.isEmpty()) {
            tickCampfires(g, dt);
            tickLanterns(g, dt);
            return;
        }
        float rainFactor = g.weather.isPrecip() ? RAIN_SPREAD_FACTOR : 1f;
        float spreadMul = g.events.fireSpreadMul() * rainFactor;
        // Spread targets are collected first; igniting while iterating would
        // modify the map under the iterator.
        java.util.List<Vec3i> spreadTargets = new java.util.ArrayList<>();

        for (Iterator<Map.Entry<Vec3i, Float>> it = burning.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Vec3i, Float> e = it.next();
            Vec3i p = e.getKey();
            BlockType t = g.world.getBlock(p.x(), p.y(), p.z());
            if (!t.flammable) {
                it.remove();
                continue;
            }
            // Rain extinguishes exposed fires quickly.
            float burnRate = dt;
            if (g.weather.isPrecip()
                    && g.world.skyLight(p.x(), p.y() + 1, p.z()) > RAIN_EXPOSURE_SKYLIGHT) {
                burnRate = dt * RAIN_BURN_RATE_MULT;
            }
            e.setValue(e.getValue() - burnRate);

            // Spread.
            if (rng.nextFloat() < SPREAD_CHANCE * spreadMul) {
                int dx = rng.nextInt(3) - 1;
                int dy = rng.nextInt(3) - 1;
                int dz = rng.nextInt(3) - 1;
                spreadTargets.add(new Vec3i(p.x() + dx, p.y() + dy, p.z() + dz));
            }

            // Damage entities standing in/next to fire.
            damageNear(g, p, dt);
            armAdjacentKegs(g, p);

            if (e.getValue() <= 0) {
                BlockType result = switch (t) {
                    case LOG, PLANK, WALL, WORKBENCH, CRATE -> BlockType.ASH;
                    default -> BlockType.AIR;
                };
                g.world.setBlock(p.x(), p.y(), p.z(), result, true);
                it.remove();
            }
        }
        for (Vec3i t : spreadTargets) {
            ignite(g, t.x(), t.y(), t.z());
        }
        tickCampfires(g, dt);
        tickLanterns(g, dt);
    }

    private void damageNear(Game g, Vec3i p, float dt) {
        float cx = p.x() + 0.5f, cy = p.y() + 0.5f, cz = p.z() + 0.5f;
        if (g.player.distSqTo(cx, cy, cz) < CONTACT_RANGE_SQ) {
            g.player.hurt(PLAYER_BURN_DPS * dt, false);
            g.player.damageFlash = 1f;
            if (!g.player.has(com.veylon.entity.Affliction.BURN)
                    && rng.nextFloat() < BURN_AFFLICTION_CHANCE) {
                g.player.addAffliction(com.veylon.entity.Affliction.BURN,
                        BURN_AFFLICTION_SECONDS_MIN
                                + rng.nextFloat() * BURN_AFFLICTION_SECONDS_RANGE);
                g.log("The flames sear you - BURNS! Treat them with a herbal poultice.");
            }
        }
        for (Entity c : g.entities.creatures) {
            if (c.distSqTo(cx, cy, cz) < CONTACT_RANGE_SQ) {
                c.hurt(CREATURE_BURN_DPS * dt, false);
            }
        }
        for (Entity n : g.entities.npcs) {
            if (n.distSqTo(cx, cy, cz) < CONTACT_RANGE_SQ) {
                n.hurt(NPC_BURN_DPS * dt, false);
            }
        }
    }

    /** Campfires burn fuel over time; when out they collapse to ash. */
    private void tickCampfires(Game g, float dt) {
        for (Iterator<Map.Entry<Vec3i, Float>> it = g.world.campfireFuel.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Vec3i, Float> e = it.next();
            Vec3i p = e.getKey();
            if (g.world.getBlock(p.x(), p.y(), p.z()) != BlockType.CAMPFIRE) {
                it.remove();
                continue;
            }
            float fuel = e.getValue() - dt * (g.weather.isPrecip()
                    && g.world.skyLight(p.x(), p.y() + 1, p.z()) > RAIN_EXPOSURE_SKYLIGHT
                    ? RAIN_CAMPFIRE_DRAIN_MULT : 1f);
            if (fuel <= 0) {
                it.remove();
                g.world.setBlock(p.x(), p.y(), p.z(), BlockType.ASH, true);
                g.log("A campfire burned out.");
            } else {
                e.setValue(fuel);
                armAdjacentKegs(g, p);
            }
        }
    }

    /** Lit lanterns consume their own finite reservoirs; extinguished ones do not. */
    private void tickLanterns(Game g, float dt) {
        for (Vec3i pos : g.world.tickLanterns(dt)) {
            if (pos.distSq(g.player.pos.x, g.player.pos.y, g.player.pos.z)
                    < LANTERN_NOTICE_RANGE * LANTERN_NOTICE_RANGE) {
                g.log("A lantern sputtered out of fuel.");
            }
        }
    }

    /** Adjacent open flame gives powder kegs a short, visible fuse. */
    private void armAdjacentKegs(Game g, Vec3i flame) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    Vec3i keg = flame.offset(dx, dy, dz);
                    if (g.explosions.tryArmKeg(g, keg, KEG_FUSE_SECONDS, false)) {
                        g.audio.playFuse(keg.x() + 0.5f, keg.y() + 0.5f, keg.z() + 0.5f);
                        g.noise.emit(g, keg.x(), keg.y(), keg.z(), KEG_FUSE_NOISE_RADIUS,
                                KEG_FUSE_NOISE_STRENGTH, "fuse", false, null);
                        g.log("Flame catches a powder-keg fuse!");
                    }
                }
            }
        }
    }
}
