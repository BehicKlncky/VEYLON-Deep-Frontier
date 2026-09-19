package com.veylon.simulation;

import com.veylon.Game;
import com.veylon.entity.Entity;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static com.veylon.simulation.FireConstants.*;

/**
 * Tick-based fire: burning blocks spread to flammable neighbors, damage nearby
 * entities, and finally turn to ash/air. Precipitation puts out fires open to
 * the sky and leaves their block unburnt; sheltered fires keep burning. Drought
 * accelerates spread.
 */
public class FireSystem implements MediumTickSystem {

    /** Hard ceiling for simultaneously burning world cells. */
    public static final int MAX_ACTIVE_FIRES = 220;

    /**
     * One burning cell. Mutated in place, so a tick allocates nothing for a
     * cell that is already burning.
     */
    private static final class Burn {
        /** Seconds of fuel left before the block is consumed. */
        float time;
        /** Seconds of rain soaked up; dries off at the same rate. */
        float wet;

        Burn(float time) {
            this.time = time;
        }
    }

    private final Map<Vec3i, Burn> burning = new HashMap<>();
    private final Random rng = new Random();
    /** Spread candidates around the cell being processed; the 3x3x3 cube minus its centre. */
    private final Vec3i[] candidates = new Vec3i[26];
    public int totalIgnitions = 0;
    /** Fires rain has put out, as opposed to ones that burned down. */
    public int totalExtinguished = 0;

    public Set<Vec3i> burningCells() {
        return burning.keySet();
    }

    public int count() {
        return burning.size();
    }

    @Override
    public void reset() {
        burning.clear();
        totalIgnitions = 0;
        totalExtinguished = 0;
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
        // A rained-on cell still lights, so a thrown flame can flare briefly;
        // mediumTick then puts it out.
        burning.put(p, new Burn(BURN_SECONDS_MIN + rng.nextFloat() * BURN_SECONDS_RANGE));
        totalIgnitions++;
        return true;
    }

    /**
     * Whether precipitation is falling on the cell at x,y,z: it is raining,
     * storming or snowing, and the cell above is open to the sky. Campfires
     * and burning blocks share this test, and so should any other flame.
     */
    public boolean isRainedOn(Game g, int x, int y, int z) {
        return g.weather.isPrecip() && g.world.skyLight(x, y + 1, z) > RAIN_EXPOSURE_SKYLIGHT;
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

    @Override
    public void mediumTick(Game g, float dt) {
        if (burning.isEmpty()) {
            tickCampfires(g, dt);
            tickLanterns(g, dt);
            return;
        }
        float rainFactor = g.weather.isPrecip() ? RAIN_SPREAD_FACTOR : 1f;
        float spreadMul = g.events.fireSpreadMul() * rainFactor;
        float spreadChance = SPREAD_CHANCE * spreadMul;
        // Spread targets are collected first; igniting while iterating would
        // modify the map under the iterator.
        List<Vec3i> spreadTargets = new ArrayList<>();

        for (Iterator<Map.Entry<Vec3i, Burn>> it = burning.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Vec3i, Burn> e = it.next();
            Vec3i p = e.getKey();
            Burn burn = e.getValue();
            BlockType t = g.world.getBlock(p.x(), p.y(), p.z());
            if (!t.flammable) {
                it.remove();
                continue;
            }
            if (isRainedOn(g, p.x(), p.y(), p.z())) {
                // Rain holds the flame where it is - no burning down, no
                // spreading - until it has soaked long enough to put it out.
                burn.wet += dt;
                if (burn.wet >= RAIN_EXTINGUISH_SECONDS) {
                    it.remove();
                    totalExtinguished++;
                    g.particles.smoke(p.x() + 0.5f, p.y() + 1f, p.z() + 0.5f, EXTINGUISH_SMOKE);
                    g.particles.smoke(p.x() + 0.5f, p.y() + 1f, p.z() + 0.5f, EXTINGUISH_SMOKE);
                    continue;
                }
            } else {
                burn.wet = Math.max(0f, burn.wet - dt);
                burn.time -= dt;
                trySpread(g, p, spreadChance, spreadTargets);
            }

            // Damage entities standing in/next to fire.
            damageNear(g, p, dt);
            armAdjacentKegs(g, p);

            if (burn.time <= 0) {
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

    /**
     * One roll per tick, and at most one ignition from it: the target is
     * chosen uniformly among the flammable neighbours that are not burning
     * yet, and one in the layer above wins {@link FireConstants#UPWARD_SPREAD_BIAS}
     * times as often. Picking only real fuel is what lets a fire walk up a bare
     * trunk, where the log above is the only unburnt fuel among the 26 cells
     * around a burning log; a blind pick from the cube found it once in 27 tries.
     */
    private void trySpread(Game g, Vec3i p, float chance, List<Vec3i> targets) {
        float roll = rng.nextFloat();
        float climbChance = chance * UPWARD_SPREAD_BIAS;
        // No pick can beat the better of the two thresholds, so a roll above
        // it needs no neighbour scan.
        if (roll >= Math.max(chance, climbChance)) {
            return;
        }
        int n = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0
                            || !g.world.getBlock(p.x() + dx, p.y() + dy, p.z() + dz).flammable) {
                        continue;
                    }
                    Vec3i q = p.offset(dx, dy, dz);
                    if (!burning.containsKey(q)) {
                        candidates[n++] = q;
                    }
                }
            }
        }
        if (n == 0) {
            return;
        }
        Vec3i target = candidates[rng.nextInt(n)];
        if (roll < (target.y() > p.y() ? climbChance : chance)) {
            targets.add(target);
        }
    }

    private void damageNear(Game g, Vec3i p, float dt) {
        float cx = p.x() + 0.5f, cy = p.y() + 0.5f, cz = p.z() + 0.5f;
        if (!g.player.abilities.invulnerable() && g.player.distSqTo(cx, cy, cz) < CONTACT_RANGE_SQ) {
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
            float fuel = e.getValue() - dt * (isRainedOn(g, p.x(), p.y(), p.z())
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
