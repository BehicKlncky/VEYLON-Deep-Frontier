package com.veylon.combat;

import com.veylon.Game;
import com.veylon.entity.Creature;
import com.veylon.entity.Entity;
import com.veylon.entity.Npc;
import com.veylon.settlement.Settlement;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Reusable explosion resolution: entity damage with distance falloff and
 * line-of-sight reduction, blast-resistance-gated block destruction through a
 * single batched world edit, bounded chain reactions between powder kegs,
 * optional fire ignition, world noise and reputation consequences.
 */
public class ExplosionSystem {

    /** Maximum kegs that can chain from one initial detonation. */
    public static final int MAX_CHAIN = 8;
    /**
     * Hard ceiling for simultaneously armed, persisted powder kegs. A placed
     * keg is cheap; an armed keg participates in the per-tick simulation and
     * can fan out into particles, fire, noise and block edits, so admission is
     * bounded separately from the per-explosion chain budget.
     */
    public static final int MAX_ACTIVE_FUSES = 64;
    /** Hard cap on blocks destroyed per explosion. */
    public static final int MAX_BLOCKS = 900;
    /** Hard cap on fires started per explosion. */
    public static final int MAX_FIRES = 10;

    private final Random rng = new Random();
    /** Kegs waiting to sympathetically detonate (bounded, no recursion). */
    private final ArrayDeque<Vec3i> chainQueue = new ArrayDeque<>();
    private int chainBudget = MAX_CHAIN;
    /** Crime attribution is collected across the whole sympathetic chain. */
    private final Set<Npc> playerHitNpcs = java.util.Collections.newSetFromMap(
            new IdentityHashMap<>());
    private final Map<Settlement, PropertyDamage> playerPropertyDamage = new HashMap<>();
    private boolean playerDamagedLegacyCamp;

    public void reset() {
        chainQueue.clear();
        chainBudget = MAX_CHAIN;
        playerHitNpcs.clear();
        playerPropertyDamage.clear();
        playerDamagedLegacyCamp = false;
    }

    /**
     * Arms one real placed keg if the global fuse budget has capacity.
     * Gameplay interaction and every fire-ignition route share this admission
     * seam so a loop can never bypass the persistent runtime ceiling.
     *
     * @return true only when a new fuse was admitted
     */
    public boolean tryArmKeg(Game g, Vec3i keg, float fuseSeconds,
                             boolean byPlayer) {
        if (g == null || keg == null || !Float.isFinite(fuseSeconds) || fuseSeconds <= 0
                || g.world.getBlock(keg.x(), keg.y(), keg.z()) != BlockType.POWDER_KEG
                || g.world.kegFuses.containsKey(keg)
                || g.world.kegFuses.size() >= MAX_ACTIVE_FUSES) {
            return false;
        }
        g.world.kegFuses.put(keg, fuseSeconds);
        g.world.kegFusePlayerAttribution.put(keg, byPlayer);
        return true;
    }

    /**
     * Compatibility overload for callers that have no player source. Unknown
     * attribution must fail safe as environmental, never as player-caused.
     */
    public boolean tryArmKeg(Game g, Vec3i keg, float fuseSeconds) {
        return tryArmKeg(g, keg, fuseSeconds, false);
    }

    /**
     * Blast resistance in "power units". Progression-critical and ancient
     * blocks effectively cannot be blasted; reinforced stone resists smaller
     * charges, so explosives never trivially replace mining.
     */
    public static float blastResistance(BlockType t) {
        return switch (t) {
            case AIR, WATER -> 0f;
            case BEACON, BEACON_LIT, RUIN_CORE, RUIN_STONE, POD_HULL -> 1000f;
            case STONE_BRICK -> 5.5f;
            case BASALT -> 4.5f;
            case STONE, COAL_ORE, COPPER_ORE, IRON_ORE, SULFUR_ORE, SALTPETER_ORE,
                 FURNACE, ANVIL -> 3.6f;
            case CAGE_BARS -> 2.8f;
            case ICE -> 2.0f;
            default -> t.hardness < 0 ? 1000f : Math.max(0.5f, t.hardness * 0.55f);
        };
    }

    /**
     * Detonates an explosion.
     *
     * @param power        blast power (~radius in blocks for block damage)
     * @param entityDamage max damage at the center
     * @param igniteChance 0..1 chance to ignite exposed flammable blocks
     * @param byPlayer     attribution for reputation and stats
     */
    public void explode(Game g, float x, float y, float z, float power,
                        float entityDamage, float igniteChance, boolean byPlayer) {
        chainBudget = MAX_CHAIN;
        playerHitNpcs.clear();
        playerPropertyDamage.clear();
        playerDamagedLegacyCamp = false;
        try {
            detonate(g, x, y, z, power, entityDamage, igniteChance, byPlayer);
            // Sympathetic detonations run from a bounded queue, never recursively.
            while (!chainQueue.isEmpty() && chainBudget > 0) {
                chainBudget--;
                Vec3i keg = chainQueue.poll();
                if (g.world.getBlock(keg.x(), keg.y(), keg.z()) == BlockType.POWDER_KEG) {
                    g.world.setBlock(keg.x(), keg.y(), keg.z(), BlockType.AIR, true);
                    g.world.kegFuses.remove(keg);
                    detonate(g, keg.x() + 0.5f, keg.y() + 0.5f, keg.z() + 0.5f,
                            3.8f, 30f, 0.35f, byPlayer);
                }
            }
            applyPlayerConsequences(g, byPlayer);
        } finally {
            chainQueue.clear();
            playerHitNpcs.clear();
            playerPropertyDamage.clear();
            playerDamagedLegacyCamp = false;
        }
    }

    private void detonate(Game g, float x, float y, float z, float power,
                          float entityDamage, float igniteChance, boolean byPlayer) {
        int r = (int) Math.ceil(power);

        // --- Entities: falloff + occlusion + knockback ---
        List<Entity> victims = new ArrayList<>();
        for (Creature c : g.entities.creatures) {
            victims.add(c);
        }
        victims.addAll(g.entities.npcs);
        if (g.player != null) {
            victims.add(g.player);
        }
        float entityRange = power * 2.4f;
        for (Entity e : victims) {
            if (e.dead) {
                continue;
            }
            double d = Math.sqrt(e.distSqTo(x, y, z));
            if (d > entityRange) {
                continue;
            }
            float falloff = (float) (1.0 - d / entityRange);
            // Walls between the blast and the target absorb most of the hit.
            float exposure = exposure(g, x, y, z,
                    e.pos.x, e.pos.y + e.height * 0.5f, e.pos.z);
            float dmg = entityDamage * falloff * (0.15f + 0.85f * exposure);
            if (dmg < 0.5f) {
                continue;
            }
            if (e instanceof com.veylon.entity.Player p) {
                p.hurtPhysical(g, dmg, true);
                g.renderer.addShake(Math.min(1f, falloff));
            } else {
                e.hurt(dmg, byPlayer);
                if (e instanceof Npc n) {
                    if (byPlayer && n.settled()) {
                        playerHitNpcs.add(n);
                    }
                    n.lastKnown.set(x, y, z);
                    n.lastKnownAge = 0;
                }
                if (e instanceof Creature c) {
                    c.fear = 1f;
                    c.bleedTimer = Math.max(c.bleedTimer, 12f);
                }
            }
            e.knockback(x, z, 3f + falloff * 5f);
        }

        // --- Blocks: batched destruction with resistance and occlusion ---
        g.world.beginBatch();
        int destroyed = 0;
        int fires = 0;
        List<Vec3i> scorch = new ArrayList<>();
        for (int dx = -r; dx <= r && destroyed < MAX_BLOCKS; dx++) {
            for (int dy = -r; dy <= r && destroyed < MAX_BLOCKS; dy++) {
                for (int dz = -r; dz <= r && destroyed < MAX_BLOCKS; dz++) {
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist > power) {
                        continue;
                    }
                    int bx = (int) Math.floor(x) + dx;
                    int by = (int) Math.floor(y) + dy;
                    int bz = (int) Math.floor(z) + dz;
                    BlockType t = g.world.getBlock(bx, by, bz);
                    if (t == BlockType.AIR || t == BlockType.WATER) {
                        continue;
                    }
                    if (t == BlockType.POWDER_KEG) {
                        if (chainQueue.size() < MAX_CHAIN) {
                            chainQueue.add(new Vec3i(bx, by, bz));
                        }
                        continue;
                    }
                    float strength = (float) (power * (1.0 - dist / (power + 0.5)) * 2.2);
                    if (strength < blastResistance(t)) {
                        continue;
                    }
                    g.world.setBlock(bx, by, bz, BlockType.AIR, true);
                    collectBlockConsequence(g, bx, by, bz, t, byPlayer);
                    destroyed++;
                    if (destroyed <= 24 && rng.nextFloat() < 0.4f) {
                        g.particles.blockDust(t, bx + 0.5f, by + 0.5f, bz + 0.5f, 3);
                    }
                    if (t.flammable && fires < MAX_FIRES && rng.nextFloat() < igniteChance) {
                        scorch.add(new Vec3i(bx, by, bz));
                        fires++;
                    }
                }
            }
        }
        g.world.endBatch();

        // Fire ignition on surviving flammable neighbors (bounded).
        int ignited = 0;
        for (Vec3i s : scorch) {
            if (ignited >= MAX_FIRES) {
                break;
            }
            for (int[] off : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0},
                    {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                BlockType nb = g.world.getBlock(s.x() + off[0], s.y() + off[1], s.z() + off[2]);
                if (nb.flammable) {
                    g.fire.ignite(g, s.x() + off[0], s.y() + off[1], s.z() + off[2]);
                    ignited++;
                    break;
                }
            }
        }

        // Presentation + perception.
        g.particles.explosion(x, y, z, power);
        g.audio.playExplosion(x, y, z);
        g.renderer.addShake(0.6f);
        g.noise.emit(g, x, y, z, 120f, 1f, "explosion", byPlayer, byPlayer ? g.player : null);
    }

    /** Deterministic, bounded ignition used by fire bombs after their small blast. */
    public int igniteNearby(Game g, float x, float y, float z, int radius, int limit) {
        int ignited = 0;
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y), cz = (int) Math.floor(z);
        for (int dy = -radius; dy <= radius && ignited < limit; dy++) {
            for (int dx = -radius; dx <= radius && ignited < limit; dx++) {
                for (int dz = -radius; dz <= radius && ignited < limit; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) {
                        continue;
                    }
                    if (g.fire.ignite(g, cx + dx, cy + dy, cz + dz)) {
                        ignited++;
                    }
                }
            }
        }
        return ignited;
    }

    /** Collects fallout; applying it per block caused dozens of duplicate penalties. */
    private void collectBlockConsequence(Game g, int x, int y, int z,
                                         BlockType t, boolean byPlayer) {
        if (!byPlayer) {
            return;
        }
        var s = g.world.settlementAt(x, z);
        if (s != null && isSettlementProperty(t)) {
            PropertyDamage damage = playerPropertyDamage.computeIfAbsent(s,
                    ignored -> new PropertyDamage());
            damage.blocks++;
            damage.containerDestroyed |= t == BlockType.CRATE;
        }
        // Legacy starter camp structures still anger the camp.
        if (g.world.campPos != null && !g.faction.hostile
                && isSettlementProperty(t)
                && new Vec3i(x, y, z).distSq(g.world.campPos.x(), g.world.campPos.y(),
                g.world.campPos.z()) < 14 * 14) {
            playerDamagedLegacyCamp = true;
        }
    }

    private void applyPlayerConsequences(Game g, boolean byPlayer) {
        if (!byPlayer) {
            return;
        }
        for (Npc npc : playerHitNpcs) {
            g.settlementManager.onNpcAttackedByPlayer(g, npc);
        }
        for (Map.Entry<Settlement, PropertyDamage> entry : playerPropertyDamage.entrySet()) {
            PropertyDamage damage = entry.getValue();
            g.settlementManager.onExplosionPropertyDamaged(g, entry.getKey(),
                    damage.blocks, damage.containerDestroyed);
        }
        if (playerDamagedLegacyCamp) {
            g.faction.addTrust(g, -12, "Your explosion damaged the camp!");
        }
    }

    private static boolean isSettlementProperty(BlockType t) {
        return t == BlockType.WALL || t == BlockType.STONE_BRICK || t == BlockType.GATE
                || t == BlockType.GATE_OPEN || t == BlockType.CRATE
                || t == BlockType.CAMPFIRE || t == BlockType.CAMP_BED
                || t == BlockType.PLANK || t == BlockType.LOG || t == BlockType.TORCH
                || t == BlockType.LANTERN || t == BlockType.WORKBENCH
                || t == BlockType.FURNACE || t == BlockType.ANVIL
                || t == BlockType.HERB_STATION || t == BlockType.DRYING_RACK;
    }

    private static final class PropertyDamage {
        int blocks;
        boolean containerDestroyed;
    }

    /** Fraction of 5 sample rays from the blast center that reach the target. */
    private float exposure(Game g, float x, float y, float z, float tx, float ty, float tz) {
        int clear = 0;
        int samples = 5;
        float[][] jitter = {{0, 0, 0}, {0.4f, 0.3f, 0}, {-0.4f, 0.3f, 0},
                {0, 0.3f, 0.4f}, {0, 0.3f, -0.4f}};
        for (int i = 0; i < samples; i++) {
            if (rayClear(g, x + jitter[i][0], y + jitter[i][1], z + jitter[i][2], tx, ty, tz)) {
                clear++;
            }
        }
        return clear / (float) samples;
    }

    private boolean rayClear(Game g, float x, float y, float z, float tx, float ty, float tz) {
        float dx = tx - x, dy = ty - y, dz = tz - z;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(1, (int) (dist * 2));
        for (int i = 1; i < steps; i++) {
            float f = i / (float) steps;
            if (g.world.getBlock((int) Math.floor(x + dx * f), (int) Math.floor(y + dy * f),
                    (int) Math.floor(z + dz * f)).opaque) {
                return false;
            }
        }
        return true;
    }

    /** Ticks lit keg fuses; detonates them when the fuse runs out. */
    public void tickFuses(Game g, float dt) {
        if (g.world.kegFuses.isEmpty()) {
            g.world.kegFusePlayerAttribution.clear();
            return;
        }
        g.world.kegFusePlayerAttribution.keySet()
                .removeIf(pos -> !g.world.kegFuses.containsKey(pos));
        List<Vec3i> exploded = null;
        for (var it = g.world.kegFuses.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Vec3i p = e.getKey();
            if (g.world.getBlock(p.x(), p.y(), p.z()) != BlockType.POWDER_KEG) {
                it.remove();
                g.world.kegFusePlayerAttribution.remove(p);
                continue;
            }
            float left = e.getValue() - dt;
            e.setValue(left);
            // Sputtering fuse feedback.
            if (left > 0 && ((int) (left * 5)) != ((int) ((left + dt) * 5))) {
                g.particles.ember(p.x() + 0.5f, p.y() + 1.05f, p.z() + 0.5f);
            }
            if (left <= 0) {
                if (exploded == null) {
                    exploded = new ArrayList<>();
                }
                exploded.add(p);
            }
        }
        if (exploded != null) {
            for (Vec3i p : exploded) {
                boolean byPlayer = g.world.kegFusePlayerAttribution
                        .getOrDefault(p, false);
                if (g.world.getBlock(p.x(), p.y(), p.z()) == BlockType.POWDER_KEG) {
                    g.world.setBlock(p.x(), p.y(), p.z(), BlockType.AIR, true);
                    explode(g, p.x() + 0.5f, p.y() + 0.5f, p.z() + 0.5f,
                            3.8f, 30f, 0.35f, byPlayer);
                } else {
                    g.world.kegFuses.remove(p);
                    g.world.kegFusePlayerAttribution.remove(p);
                }
            }
        }
    }
}
