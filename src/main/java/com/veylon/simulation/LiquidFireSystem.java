package com.veylon.simulation;

import com.veylon.Game;
import com.veylon.entity.Affliction;
import com.veylon.entity.Creature;
import com.veylon.entity.Entity;
import com.veylon.entity.Npc;
import com.veylon.entity.Player;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static com.veylon.simulation.LiquidFireConstants.*;

/**
 * Burning liquid from a shattered fire bomb. A spill finds the ground under
 * the point where the bottle broke and runs over it as a set of patches, one
 * per surface cell: the air cell directly above a solid block, where the
 * liquid lies on the top face. It runs sideways and down, never up, and never
 * into a solid or water cell; tall grass, bushes and saplings soak it up.
 *
 * <p>Each medium tick a patch burns whoever stands in it, may set flammable
 * blocks in and beside its cell alight through {@link FireSystem#ignite},
 * lights the fuse of a neighbouring powder keg, and burns down. Block fires it
 * starts spread through {@link FireSystem} on their own. Rain, storm and snow
 * put out a patch open to the sky after {@link LiquidFireConstants#RAIN_EXTINGUISH_SECONDS};
 * it neither burns nor ignites anything meanwhile.
 *
 * <p>Patches are not saved, like burning blocks: a save taken mid-burn loads
 * with the pool gone. A fire bomb still in the air is saved with the other
 * explosives and shatters when it lands.
 */
public class LiquidFireSystem implements MediumTickSystem {

    /** Returned by the ground search when there is no cell the liquid can lie in. */
    private static final int NO_CELL = Integer.MIN_VALUE;
    /**
     * Every admitted cell pushes at most its four neighbours, so a fill can
     * never push more than this many entries, popped or not.
     */
    private static final int FRONTIER_CAPACITY = MAX_PATCHES_PER_SPILL * 4 + 1;
    /** Horizontal fill directions, in the order neighbours are pushed. */
    private static final int[] STEP_X = {1, -1, 0, 0};
    private static final int[] STEP_Z = {0, 0, 1, -1};
    /** The cell's own block, then its six face neighbours, ground block included. */
    private static final int[][] TOUCHED = {{0, 0, 0}, {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    /** One cell of burning liquid. Coordinates are those of the air cell it lies in. */
    public static final class Patch {
        public final int x, y, z;
        float burnLeft;
        float wet;
        float intensity;
        boolean byPlayer;
        float age;
        int spillId;

        Patch(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        /** Seconds until this patch burns out. */
        public float burnLeft() {
            return burnLeft;
        }

        /** Seconds of rain soaked up; above zero the patch neither burns nor ignites. */
        public float wet() {
            return wet;
        }

        /** 1 at the centre of a spill, falling towards the rim. */
        public float intensity() {
            return intensity;
        }

        public boolean byPlayer() {
            return byPlayer;
        }

        /** Seconds since the liquid landed here, or was last topped up. */
        public float age() {
            return age;
        }

        /** The bottle this liquid came from; later bottles have larger ids. */
        public int spillId() {
            return spillId;
        }
    }

    /**
     * Live patches, oldest spill first. A spill appends its patches, and a
     * cell a later spill tops up moves to the end, so the head is always the
     * oldest liquid, which is what admission evicts.
     */
    private final List<Patch> patches = new ArrayList<>(MAX_PATCHES);
    private final List<Patch> patchView = Collections.unmodifiableList(patches);
    private final Random rng = new Random();
    private int nextSpillId;
    public int totalSpills;
    public int totalPatchIgnitions;

    // Fill scratch, reused by every spill.
    private final int[] frontierX = new int[FRONTIER_CAPACITY];
    private final int[] frontierY = new int[FRONTIER_CAPACITY];
    private final int[] frontierZ = new int[FRONTIER_CAPACITY];
    private final float[] frontierDist = new float[FRONTIER_CAPACITY];
    private final float[] frontierScore = new float[FRONTIER_CAPACITY];
    private int frontierSize;
    private final int[] filledX = new int[MAX_PATCHES_PER_SPILL];
    private final int[] filledY = new int[MAX_PATCHES_PER_SPILL];
    private final int[] filledZ = new int[MAX_PATCHES_PER_SPILL];
    private int filledCount;

    /** Entities already burned this tick, however many patches they stand on. */
    private final Set<Entity> burnedThisTick =
            Collections.newSetFromMap(new IdentityHashMap<>());
    /**
     * (NPC, spill) pairs already burned, so an NPC counts as attacked once
     * per bottle. Oldest first; pairs go when their spill has burned out.
     */
    private final Npc[] trackedNpc = new Npc[MAX_TRACKED_NPC_SPILLS];
    private final int[] trackedSpill = new int[MAX_TRACKED_NPC_SPILLS];
    private int trackedCount;

    @Override
    public void reset() {
        patches.clear();
        nextSpillId = 0;
        totalSpills = 0;
        totalPatchIgnitions = 0;
        burnedThisTick.clear();
        Arrays.fill(trackedNpc, null);
        trackedCount = 0;
        frontierSize = 0;
        filledCount = 0;
    }

    /** Seeded per world so a given world seed replays identically. */
    public void setRandomSeed(long seed) {
        rng.setSeed(seed);
    }

    /** Live patches, oldest spill first. Read-only. */
    public List<Patch> patches() {
        return patchView;
    }

    public int count() {
        return patches.size();
    }

    /** Remembered (NPC, spill) pairs; bounded by {@link LiquidFireConstants#MAX_TRACKED_NPC_SPILLS}. */
    public int trackedNpcSpills() {
        return trackedCount;
    }

    /**
     * Spills one bottle of burning liquid where it broke: {@code (x, y, z)}
     * is a point in open air where it shattered, and {@code (dirX, dirZ)} the
     * way it was travelling, which the pool runs further along. The direction
     * need not be normalised; zero means no bias.
     *
     * @param byPlayer whether the player threw it, for attribution
     * @return how many surface cells the liquid covers, new or topped up;
     *         zero when it fell in water or found no ground within reach
     */
    public int spill(Game g, float x, float y, float z, float dirX, float dirZ,
                     boolean byPlayer) {
        totalSpills++;
        int spillId = nextSpillId++;
        g.noise.emit(g, x, y, z, SPILL_NOISE_RADIUS, SPILL_NOISE_INTENSITY, "molotov",
                byPlayer, byPlayer ? g.player : null);

        int ox = (int) Math.floor(x);
        int oz = (int) Math.floor(z);
        int oy = (int) Math.floor(y);
        // A point on a block's top face rounds into the block below it.
        if (g.world.getBlock(ox, oy, oz).solid) {
            oy++;
        }
        oy = landing(g.world, ox, oy, oz, SURFACE_SEARCH_DEPTH);
        if (oy == NO_CELL) {
            return 0;
        }

        float len = (float) Math.sqrt(dirX * dirX + dirZ * dirZ);
        float bx = len < 1e-4f ? 0f : dirX / len;
        float bz = len < 1e-4f ? 0f : dirZ / len;
        frontierSize = 0;
        filledCount = 0;
        push(ox, oy, oz, 0f, 0f);
        while (frontierSize > 0 && filledCount < MAX_PATCHES_PER_SPILL) {
            int best = 0;
            for (int i = 1; i < frontierSize; i++) {
                if (frontierScore[i] < frontierScore[best]) {
                    best = i;
                }
            }
            int cx = frontierX[best], cy = frontierY[best], cz = frontierZ[best];
            float dist = frontierDist[best];
            int tail = frontierSize - best - 1;
            if (tail > 0) {
                // Shift rather than swap, so equal scores keep push order.
                System.arraycopy(frontierX, best + 1, frontierX, best, tail);
                System.arraycopy(frontierY, best + 1, frontierY, best, tail);
                System.arraycopy(frontierZ, best + 1, frontierZ, best, tail);
                System.arraycopy(frontierDist, best + 1, frontierDist, best, tail);
                System.arraycopy(frontierScore, best + 1, frontierScore, best, tail);
            }
            frontierSize--;

            float edge = dist / SPILL_RADIUS;
            float burn = BURN_SECONDS_MIN + BURN_SECONDS_RANGE * (1f - edge)
                    + rng.nextFloat() * BURN_SECONDS_JITTER;
            if (!admit(cx, cy, cz, spillId, byPlayer, burn, 1f - RIM_INTENSITY_LOSS * edge)) {
                break;
            }
            filledX[filledCount] = cx;
            filledY[filledCount] = cy;
            filledZ[filledCount] = cz;
            filledCount++;

            for (int d = 0; d < STEP_X.length; d++) {
                int nx = cx + STEP_X[d];
                int nz = cz + STEP_Z[d];
                int dx = nx - ox, dz = nz - oz;
                float nd = (float) Math.sqrt(dx * dx + dz * dz);
                if (nd > SPILL_RADIUS) {
                    continue;
                }
                int ny = landing(g.world, nx, cy, nz, MAX_DROP);
                if (ny == NO_CELL || known(nx, ny, nz)) {
                    continue;
                }
                push(nx, ny, nz, nd, nd - DIRECTION_BIAS * (bx * dx + bz * dz));
            }
        }
        return filledCount;
    }

    /**
     * The cell at or at most {@code maxDrop} blocks below {@code (x, y, z)}
     * where liquid comes to rest on solid ground, or {@link #NO_CELL} when a
     * solid block or water is in the way first, or no ground is within reach.
     */
    private static int landing(World world, int x, int y, int z, int maxDrop) {
        for (int drop = 0; drop <= maxDrop; drop++) {
            BlockType cell = world.getBlock(x, y - drop, z);
            if (cell.solid || cell == BlockType.WATER) {
                return NO_CELL;
            }
            if (world.isSolid(x, y - drop - 1, z)) {
                return y - drop;
            }
        }
        return NO_CELL;
    }

    private void push(int x, int y, int z, float dist, float score) {
        frontierX[frontierSize] = x;
        frontierY[frontierSize] = y;
        frontierZ[frontierSize] = z;
        frontierDist[frontierSize] = dist;
        frontierScore[frontierSize] = score;
        frontierSize++;
    }

    /** Whether this fill has already covered or queued the cell. */
    private boolean known(int x, int y, int z) {
        for (int i = 0; i < filledCount; i++) {
            if (filledX[i] == x && filledY[i] == y && filledZ[i] == z) {
                return true;
            }
        }
        for (int i = 0; i < frontierSize; i++) {
            if (frontierX[i] == x && frontierY[i] == y && frontierZ[i] == z) {
                return true;
            }
        }
        return false;
    }

    /**
     * Puts liquid in a cell. A cell that already burns is topped up and joins
     * this spill rather than being duplicated. At the cap the oldest patch of
     * the oldest spill makes room; this spill's own patches never do.
     */
    private boolean admit(int x, int y, int z, int spillId, boolean byPlayer,
                          float burn, float intensity) {
        for (int i = 0; i < patches.size(); i++) {
            Patch p = patches.get(i);
            if (p.x == x && p.y == y && p.z == z) {
                patches.remove(i);
                p.burnLeft = Math.max(p.burnLeft, burn);
                p.intensity = Math.max(p.intensity, intensity);
                p.byPlayer |= byPlayer;
                p.spillId = spillId;
                p.age = 0f;
                p.wet = 0f;
                patches.add(p);
                return true;
            }
        }
        if (patches.size() >= MAX_PATCHES) {
            // One spill is far below the cap, so the head is always older liquid.
            if (patches.getFirst().spillId == spillId) {
                return false;
            }
            patches.removeFirst();
        }
        Patch p = new Patch(x, y, z);
        p.burnLeft = burn;
        p.intensity = intensity;
        p.byPlayer = byPlayer;
        p.spillId = spillId;
        patches.add(p);
        return true;
    }

    @Override
    public void mediumTick(Game g, float dt) {
        if (patches.isEmpty()) {
            forgetEndedSpills();
            return;
        }
        burnedThisTick.clear();
        int kept = 0;
        for (int i = 0; i < patches.size(); i++) {
            Patch p = patches.get(i);
            if (tick(g, p, dt)) {
                patches.set(kept++, p);
            }
        }
        for (int i = patches.size() - 1; i >= kept; i--) {
            patches.remove(i);
        }
        burnedThisTick.clear();
        forgetEndedSpills();
    }

    /** Advances one patch; false when it has gone out. */
    private boolean tick(Game g, Patch p, float dt) {
        p.age += dt;
        BlockType cell = g.world.getBlock(p.x, p.y, p.z);
        if (cell.solid || cell == BlockType.WATER || !g.world.isSolid(p.x, p.y - 1, p.z)) {
            // Built over, flooded, or the ground under it burned away.
            return false;
        }
        if (g.fire.isRainedOn(g, p.x, p.y, p.z)) {
            p.wet += dt;
            if (p.wet >= RAIN_EXTINGUISH_SECONDS) {
                g.particles.smoke(p.x + 0.5f, p.y + 0.2f, p.z + 0.5f, EXTINGUISH_SMOKE);
                return false;
            }
        } else {
            p.wet = Math.max(0f, p.wet - dt);
        }
        p.burnLeft -= dt;
        if (p.burnLeft <= 0f) {
            return false;
        }
        if (p.wet > 0f) {
            return true;
        }
        igniteTouching(g, p);
        burnOccupants(g, p, dt);
        return true;
    }

    /** Rolls for every flammable block in or beside the cell, and lights neighbouring kegs. */
    private void igniteTouching(Game g, Patch p) {
        float chance = IGNITE_CHANCE_PER_TICK * p.intensity;
        for (int[] o : TOUCHED) {
            int bx = p.x + o[0], by = p.y + o[1], bz = p.z + o[2];
            BlockType t = g.world.getBlock(bx, by, bz);
            if (t == BlockType.POWDER_KEG) {
                Vec3i keg = new Vec3i(bx, by, bz);
                if (g.explosions.tryArmKeg(g, keg, FireConstants.KEG_FUSE_SECONDS, p.byPlayer)) {
                    g.audio.playFuse(bx + 0.5f, by + 0.5f, bz + 0.5f);
                    g.noise.emit(g, bx, by, bz, FireConstants.KEG_FUSE_NOISE_RADIUS,
                            FireConstants.KEG_FUSE_NOISE_STRENGTH, "fuse", false, null);
                    g.log("Flame catches a powder-keg fuse!");
                }
            } else if (t.flammable && rng.nextFloat() < chance && g.fire.ignite(g, bx, by, bz)) {
                totalPatchIgnitions++;
            }
        }
    }

    /** Burns everyone standing in the patch who has not been burned this tick. */
    private void burnOccupants(Game g, Patch p, float dt) {
        Player player = g.player;
        if (player != null && !player.dead && !player.abilities.invulnerable()
                && standsIn(player, p) && burnedThisTick.add(player)) {
            player.hurt(ENTITY_DPS_PLAYER * dt, false);
            player.damageFlash = 1f;
            if (!player.has(Affliction.BURN)
                    && rng.nextFloat() < FireConstants.BURN_AFFLICTION_CHANCE) {
                player.addAffliction(Affliction.BURN, FireConstants.BURN_AFFLICTION_SECONDS_MIN
                        + rng.nextFloat() * FireConstants.BURN_AFFLICTION_SECONDS_RANGE);
                g.log("The flames sear you - BURNS! Treat them with a herbal poultice.");
            }
        }
        for (Creature c : g.entities.creatures) {
            if (!c.dead && standsIn(c, p) && burnedThisTick.add(c)) {
                c.hurt(ENTITY_DPS_CREATURE * dt, p.byPlayer);
            }
        }
        for (Npc n : g.entities.npcs) {
            if (!n.dead && standsIn(n, p) && burnedThisTick.add(n)) {
                n.hurt(ENTITY_DPS_NPC * dt, p.byPlayer);
                if (firstBurn(n, p.spillId)) {
                    onNpcFirstBurned(g, n, p);
                }
            }
        }
    }

    /**
     * Whether the entity's feet, its footprint at {@code pos.y}, overlap the
     * patch cell within the contact height. A body straddling two cells
     * stands in both.
     */
    private static boolean standsIn(Entity e, Patch p) {
        float feet = e.pos.y;
        if (feet < p.y - CONTACT_BELOW || feet > p.y + CONTACT_HALF_HEIGHT) {
            return false;
        }
        float hw = e.width * 0.5f;
        return e.pos.x + hw > p.x && e.pos.x - hw < p.x + 1
                && e.pos.z + hw > p.z && e.pos.z - hw < p.z + 1;
    }

    /**
     * What an explosion does to an NPC it hurts, once per bottle: the NPC
     * looks to where the fire is, and a player's bottle counts as an attack.
     * The look is perception of the thrower, so it waits on
     * {@link Player#isPerceivableByAi()} for a player's bottle; the attack's
     * reputation cost does not, and applies to settled NPCs only.
     */
    private static void onNpcFirstBurned(Game g, Npc n, Patch p) {
        if (!p.byPlayer || g.player == null || g.player.isPerceivableByAi()) {
            n.lastKnown.set(p.x + 0.5f, p.y, p.z + 0.5f);
            n.lastKnownAge = 0f;
        }
        if (p.byPlayer && n.settled()) {
            g.settlementManager.onNpcAttackedByPlayer(g, n);
        }
    }

    /** True the first time this NPC is burned by this spill, and remembers it. */
    private boolean firstBurn(Npc n, int spillId) {
        for (int i = 0; i < trackedCount; i++) {
            if (trackedNpc[i] == n && trackedSpill[i] == spillId) {
                return false;
            }
        }
        if (trackedCount == MAX_TRACKED_NPC_SPILLS) {
            // Forget the oldest pair; at worst that NPC counts twice for an old bottle.
            System.arraycopy(trackedNpc, 1, trackedNpc, 0, trackedCount - 1);
            System.arraycopy(trackedSpill, 1, trackedSpill, 0, trackedCount - 1);
            trackedCount--;
        }
        trackedNpc[trackedCount] = n;
        trackedSpill[trackedCount] = spillId;
        trackedCount++;
        return true;
    }

    /** Drops remembered pairs whose spill has no patch left burning. */
    private void forgetEndedSpills() {
        int kept = 0;
        for (int i = 0; i < trackedCount; i++) {
            if (spillBurning(trackedSpill[i])) {
                trackedNpc[kept] = trackedNpc[i];
                trackedSpill[kept] = trackedSpill[i];
                kept++;
            }
        }
        for (int i = kept; i < trackedCount; i++) {
            trackedNpc[i] = null;
        }
        trackedCount = kept;
    }

    private boolean spillBurning(int spillId) {
        for (int i = 0; i < patches.size(); i++) {
            if (patches.get(i).spillId == spillId) {
                return true;
            }
        }
        return false;
    }
}
