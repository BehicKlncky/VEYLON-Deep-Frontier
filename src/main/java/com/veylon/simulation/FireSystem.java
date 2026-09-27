package com.veylon.simulation;

import com.veylon.Game;
import com.veylon.entity.BodySweep;
import com.veylon.entity.CombustionSource;
import com.veylon.entity.Entity;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static com.veylon.simulation.FireConstants.*;

/**
 * Tick-based fire: burning blocks spread to flammable neighbors and finally
 * turn to ash/air. Precipitation puts out fires open to the sky and leaves
 * their block unburnt; sheltered fires keep burning. Drought accelerates
 * spread.
 *
 * <p>This is also where the flames that stand in the world touch living
 * bodies ({@link #exposeContacts}, asked by the body-fire fast tick): burning
 * blocks, fueled campfires and placed torches. Each has a flame volume, and
 * only a body whose flame box reaches into it catches; warmth near a fire,
 * light, a lantern's enclosed flame and a torch in someone's hand are not
 * flames here. None of them does damage of its own; the body's fire does.
 *
 * <p>A burning cell remembers whether the player lit it and from which bottle,
 * so a fire a player's molotov started, and every cell it spreads to, burns a
 * body for the player's account. Fires from blasts, lightning and meteors,
 * and fires spreading from those, are nobody's.
 */
public class FireSystem implements MediumTickSystem {

    /** Hard ceiling for simultaneously burning world cells. */
    public static final int MAX_ACTIVE_FIRES = 220;
    /** Origin of a fire nobody threw: lightning, a blast, a meteor, or a test. */
    public static final int NO_ORIGIN = -1;

    /**
     * One burning cell. Mutated in place, so a tick allocates nothing for a
     * cell that is already burning.
     */
    private static final class Burn {
        final Vec3i cell;
        /** Seconds of fuel left before the block is consumed. */
        float time;
        /** Seconds of rain soaked up; dries off at the same rate. */
        float wet;
        /** Whether the player's flame lit it, directly or by spreading. */
        final boolean byPlayer;
        /** The player's bottle behind it, or {@link #NO_ORIGIN}. */
        final int origin;
        /** Set when the cell stops burning, until the list of burning cells is compacted. */
        boolean out;

        Burn(Vec3i cell, float time, boolean byPlayer, int origin) {
            this.cell = cell;
            this.time = time;
            this.byPlayer = byPlayer;
            this.origin = origin;
        }
    }

    private final Map<Vec3i, Burn> burning = new HashMap<>();
    /** The same burns as {@link #burning}, for contact sampling without an iterator. */
    private final List<Burn> burns = new ArrayList<>(MAX_ACTIVE_FIRES);
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
        burns.clear();
        totalIgnitions = 0;
        totalExtinguished = 0;
    }

    /** Deterministic QA hook; normal gameplay retains organic fire variation. */
    public void setRandomSeed(long seed) {
        rng.setSeed(seed);
    }

    /** Sets a flammable block alight as nobody's fire. */
    public boolean ignite(Game g, int x, int y, int z) {
        return ignite(g, x, y, z, false, NO_ORIGIN);
    }

    /**
     * Sets a flammable block alight. The cap, the one-fire-per-cell rule and
     * the burn time are the same whoever lights it; the first flame to light
     * a cell owns it.
     *
     * @param byPlayer whether the player's flame lit it
     * @param origin   the player's bottle behind it, or {@link #NO_ORIGIN}
     */
    public boolean ignite(Game g, int x, int y, int z, boolean byPlayer, int origin) {
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
        Burn burn = new Burn(p, BURN_SECONDS_MIN + rng.nextFloat() * BURN_SECONDS_RANGE,
                byPlayer, byPlayer ? origin : NO_ORIGIN);
        burning.put(p, burn);
        burns.add(burn);
        totalIgnitions++;
        return true;
    }

    /**
     * Whether precipitation is falling on the cell at x,y,z: it is raining,
     * storming or snowing, and the cell above is open to the sky. Campfires
     * and burning blocks share this test, and so should any other flame.
     */
    public boolean isRainedOn(Game g, int x, int y, int z) {
        return isPrecipitationReaching(g, x, y + 1, z);
    }

    /**
     * Whether precipitation reaches into the open cell at x,y,z: it is
     * raining, storming or snowing and nothing opaque stands above the cell.
     * {@link #isRainedOn} asks it of the cell above a block; a burning body
     * asks it of the cell its head is in, so a roof over the head shelters it.
     * An unloaded column reads as open sky, so callers deciding anything about
     * a place check that its column is loaded.
     */
    public boolean isPrecipitationReaching(Game g, int x, int y, int z) {
        return g.weather.isPrecip() && g.world.skyLight(x, y, z) > RAIN_EXPOSURE_SKYLIGHT;
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
        // Spread targets are collected first, each with the fire it spreads
        // from; igniting while iterating would modify the map under the iterator.
        List<Vec3i> spreadTargets = new ArrayList<>();
        List<Burn> spreadFrom = new ArrayList<>();

        for (Iterator<Map.Entry<Vec3i, Burn>> it = burning.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Vec3i, Burn> e = it.next();
            Vec3i p = e.getKey();
            Burn burn = e.getValue();
            BlockType t = g.world.getBlock(p.x(), p.y(), p.z());
            if (!t.flammable) {
                it.remove();
                burn.out = true;
                continue;
            }
            if (isRainedOn(g, p.x(), p.y(), p.z())) {
                // Rain holds the flame where it is - no burning down, no
                // spreading - until it has soaked long enough to put it out.
                burn.wet += dt;
                if (burn.wet >= RAIN_EXTINGUISH_SECONDS) {
                    it.remove();
                    burn.out = true;
                    totalExtinguished++;
                    g.particles.smoke(p.x() + 0.5f, p.y() + 1f, p.z() + 0.5f, EXTINGUISH_SMOKE);
                    g.particles.smoke(p.x() + 0.5f, p.y() + 1f, p.z() + 0.5f, EXTINGUISH_SMOKE);
                    continue;
                }
            } else {
                burn.wet = Math.max(0f, burn.wet - dt);
                burn.time -= dt;
                int spreads = spreadTargets.size();
                trySpread(g, p, spreadChance, spreadTargets);
                if (spreadTargets.size() > spreads) {
                    spreadFrom.add(burn);
                }
            }

            armAdjacentKegs(g, p);

            if (burn.time <= 0) {
                BlockType result = switch (t) {
                    case LOG, PLANK, WALL, WORKBENCH, CRATE -> BlockType.ASH;
                    default -> BlockType.AIR;
                };
                g.world.setBlock(p.x(), p.y(), p.z(), result, true);
                it.remove();
                burn.out = true;
            }
        }
        burns.removeIf(b -> b.out);
        for (int i = 0; i < spreadTargets.size(); i++) {
            Vec3i t = spreadTargets.get(i);
            Burn from = spreadFrom.get(i);
            ignite(g, t.x(), t.y(), t.z(), from.byPlayer, from.origin);
        }
        tickCampfires(g, dt);
        tickLanterns(g, dt);
    }

    /**
     * Offers the body a contact with every flame standing in the world that
     * its sweep touched this fast tick: burning blocks, fueled campfires and
     * placed torches. Looks only at the cells around the body and at the
     * burning cells (at most {@link #MAX_ACTIVE_FIRES}); never loads a chunk.
     */
    public void exposeContacts(Game g, Entity e, BodySweep s) {
        for (int i = 0; i < burns.size(); i++) {
            touchBurningBlock(g, e, s, burns.get(i));
        }
        touchFlameBlocks(g, e, s);
    }

    /**
     * A burning block's flames fill the block itself when it is not solid (a
     * bush, grass), reach {@link FireConstants#FLAME_FACE_REACH} out of each
     * side and bottom face into an open cell, and rise
     * {@link FireConstants#FLAME_PLUME_HEIGHT} above it when the cell above is
     * open. A face against a solid block gives nothing: heat does not cross a
     * wall or a floor, or come up through the ground. A cell the rain is
     * soaking or falling on (it only flares until the rain puts it out), or
     * that stopped being fuel since its last medium tick, touches nobody.
     */
    private void touchBurningBlock(Game g, Entity e, BodySweep s, Burn burn) {
        int x = burn.cell.x(), y = burn.cell.y(), z = burn.cell.z();
        if (burn.wet > 0f
                || s.maxX() <= x - FLAME_FACE_REACH || s.minX() >= x + 1 + FLAME_FACE_REACH
                || s.maxY() <= y - FLAME_FACE_REACH || s.minY() >= y + 1 + FLAME_PLUME_HEIGHT
                || s.maxZ() <= z - FLAME_FACE_REACH || s.minZ() >= z + 1 + FLAME_FACE_REACH) {
            return;
        }
        World world = g.world;
        BlockType block = world.getBlock(x, y, z);
        if (!block.flammable || isRainedOn(g, x, y, z)) {
            return;
        }
        float r = FLAME_FACE_REACH;
        boolean touched = !block.solid && s.touches(x, y, z, x + 1, y + 1, z + 1)
                || !world.isSolid(x, y + 1, z)
                && s.touches(x, y + 1, z, x + 1, y + 1 + FLAME_PLUME_HEIGHT, z + 1)
                || !world.isSolid(x + 1, y, z) && s.touches(x + 1, y, z, x + 1 + r, y + 1, z + 1)
                || !world.isSolid(x - 1, y, z) && s.touches(x - r, y, z, x, y + 1, z + 1)
                || !world.isSolid(x, y, z + 1) && s.touches(x, y, z + 1, x + 1, y + 1, z + 1 + r)
                || !world.isSolid(x, y, z - 1) && s.touches(x, y, z - r, x + 1, y + 1, z)
                || !world.isSolid(x, y - 1, z) && s.touches(x, y - r, z, x + 1, y, z + 1);
        if (touched) {
            g.combustion.expose(e, CombustionSource.BLOCK_FIRE,
                    CombustionSource.BLOCK_FIRE.nominalIntensity, burn.byPlayer,
                    burn.byPlayer ? burn.origin : cellId(x, y, z),
                    s.contactX(), s.contactY(), s.contactZ());
        }
    }

    /**
     * Torches and fueled campfires in the cells the sweep passed through.
     * Neither has a weather rule of its own, so rain does not stop them
     * touching; a campfire with no fuel left is out.
     */
    private static void touchFlameBlocks(Game g, Entity e, BodySweep s) {
        World world = g.world;
        int x0 = (int) Math.floor(s.minX()), x1 = (int) Math.floor(s.maxX());
        int z0 = (int) Math.floor(s.minZ()), z1 = (int) Math.floor(s.maxZ());
        int y0 = (int) Math.floor(s.minY() - Math.max(TORCH_FLAME_TOP, CAMPFIRE_FLAME_TOP));
        int y1 = (int) Math.floor(s.maxY() - Math.min(TORCH_FLAME_BOTTOM, CAMPFIRE_FLAME_BOTTOM));
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = y0; y <= y1; y++) {
                    BlockType t = world.getBlock(x, y, z);
                    if (t == BlockType.TORCH) {
                        if (touchesFlame(s, t, x, y, z)) {
                            g.combustion.expose(e, CombustionSource.TORCH,
                                    CombustionSource.TORCH.nominalIntensity, false, cellId(x, y, z),
                                    s.contactX(), s.contactY(), s.contactZ());
                        }
                    } else if (t == BlockType.CAMPFIRE && touchesFlame(s, t, x, y, z)
                            && hasFuel(world.campfireFuel.get(new Vec3i(x, y, z)))) {
                        g.combustion.expose(e, CombustionSource.CAMPFIRE,
                                CombustionSource.CAMPFIRE.nominalIntensity, false, cellId(x, y, z),
                                s.contactX(), s.contactY(), s.contactZ());
                    }
                }
            }
        }
    }

    /** A campfire's fuel entry; a campfire with none, or none left, is out. */
    private static boolean hasFuel(Float fuel) {
        return fuel != null && fuel > 0f;
    }

    private static boolean touchesFlame(BodySweep s, BlockType t, int x, int y, int z) {
        float half = t == BlockType.TORCH ? TORCH_FLAME_HALF_WIDTH : CAMPFIRE_FLAME_HALF_WIDTH;
        float bottom = t == BlockType.TORCH ? TORCH_FLAME_BOTTOM : CAMPFIRE_FLAME_BOTTOM;
        float top = t == BlockType.TORCH ? TORCH_FLAME_TOP : CAMPFIRE_FLAME_TOP;
        return s.touches(x + 0.5f - half, y + bottom, z + 0.5f - half,
                x + 0.5f + half, y + top, z + 0.5f + half);
    }

    /**
     * Whether a torch or campfire placed at {@code (x, y, z)} would put its
     * flame inside {@code e} where it stands: placing a flame into someone,
     * the placer included, is refused like placing a block into them.
     */
    public static boolean flameWouldTouch(BlockType t, int x, int y, int z, Entity e) {
        if (!isStandingFlame(t)) {
            return false;
        }
        float half = t == BlockType.TORCH ? TORCH_FLAME_HALF_WIDTH : CAMPFIRE_FLAME_HALF_WIDTH;
        float bottom = t == BlockType.TORCH ? TORCH_FLAME_BOTTOM : CAMPFIRE_FLAME_BOTTOM;
        float top = t == BlockType.TORCH ? TORCH_FLAME_TOP : CAMPFIRE_FLAME_TOP;
        return BodySweep.overlapsNow(e, x + 0.5f - half, y + bottom, z + 0.5f - half,
                x + 0.5f + half, y + top, z + 0.5f + half);
    }

    /** What {@link #standingFlameAhead} returns for a clear step. */
    public static final long NO_FLAME = Long.MIN_VALUE;

    /**
     * The column of a torch or campfire cell that {@code e} would walk into,
     * or further into, by stepping {@code (dx, dz)} from where it stands,
     * packed as {@code x << 32 | z}; {@link #NO_FLAME} when the step is clear.
     * A step out of a cell the body is in is clear. Walkers steer round what
     * this finds ({@code ai.Steering}), since people and animals keep out of
     * those cells like walls ({@code Entity.keepsOutOfFlames}). Reads at most
     * the few cells under the stepped box.
     */
    public static long standingFlameAhead(World world, Entity e, float dx, float dz) {
        float x = e.pos.x + dx;
        float z = e.pos.z + dz;
        float hw = e.width * 0.5f;
        int x0 = (int) Math.floor(x - hw), x1 = (int) Math.floor(x + hw);
        int z0 = (int) Math.floor(z - hw), z1 = (int) Math.floor(z + hw);
        int y0 = (int) Math.floor(e.pos.y), y1 = (int) Math.floor(e.pos.y + e.height - 1e-4f);
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                for (int cy = y0; cy <= y1; cy++) {
                    if (!isStandingFlame(world.getBlock(cx, cy, cz))
                            || !cellOverlaps(x, z, hw, cx, cz)) {
                        continue;
                    }
                    if (cellOverlaps(e.pos.x, e.pos.z, hw, cx, cz)) {
                        float fx = cx + 0.5f, fz = cz + 0.5f;
                        float now = (e.pos.x - fx) * (e.pos.x - fx) + (e.pos.z - fz) * (e.pos.z - fz);
                        float then = (x - fx) * (x - fx) + (z - fz) * (z - fz);
                        if (then >= now) {
                            continue;
                        }
                    }
                    return (long) cx << 32 | cz & 0xffffffffL;
                }
            }
        }
        return NO_FLAME;
    }

    private static boolean cellOverlaps(float x, float z, float hw, int cx, int cz) {
        return x + hw > cx && x - hw < cx + 1 && z + hw > cz && z - hw < cz + 1;
    }

    private static boolean isStandingFlame(BlockType t) {
        return t == BlockType.TORCH || t == BlockType.CAMPFIRE;
    }

    /** A stable id for a flame in a cell, to break ties between equal contacts. */
    private static int cellId(int x, int y, int z) {
        return (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
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
