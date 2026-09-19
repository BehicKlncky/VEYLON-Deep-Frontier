package com.veylon.combat;

import com.veylon.Game;
import com.veylon.entity.Creature;
import com.veylon.entity.Entity;
import com.veylon.entity.Npc;
import com.veylon.item.ItemType;
import com.veylon.world.BlockType;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Shared projectile simulation for player and NPC ranged attacks: arrows,
 * musket balls, pellets and thrown bombs. Substepped segment sweeps against
 * blocks and entity AABBs; strict caps so projectiles can never leak. A bullet
 * or arrow that hits an NPC is judged by the {@link HitZone} it entered through
 * ({@link ProjectileLethality}); every other hit deals plain impact damage.
 */
public class ProjectileSystem {

    public static final int MAX_LIVE = 96;
    public static final int MAX_STUCK = 40;
    /** Stuck arrows despawn after this many seconds if not recovered. */
    public static final float STUCK_LIFE = 150f;

    public enum Kind {
        ARROW, BULLET, BOMB, FIRE_BOMB
    }

    public static final class Projectile {
        public Kind kind;
        public float x, y, z;
        public float vx, vy, vz;
        public float gravity;
        public float damage;
        public float life;
        public boolean fromPlayer;
        public Entity owner;
        /** Recoverable ammo item for arrows. */
        public ItemType ammoItem;
        /** Bomb fuse seconds; explodes when it reaches zero. */
        public float fuse;
        /** Arrows stuck in terrain wait for pickup. */
        public boolean stuck;
        public float stuckTime;
        /** Bombs stop after their first entity impact and ignore further bodies. */
        public boolean impactedEntity;
        /** Guards exactly-once fuse resolution. */
        public boolean detonated;
        /** Facing derived from velocity for rendering. */
        public float yaw, pitch;
        /**
         * The trigger pull or bow release this came from: every pellet of one
         * {@link ProjectileSystem#fire} call shares it, which is how a
         * blunderbuss shot counts once against a torso. Not persisted; only
         * explosives survive a save and they never read it.
         */
        public int shotId;
    }

    /** Horizontal padding of the projectile hit box beyond an entity's half-width. */
    private static final float HIT_BOX_PAD_XZ = 0.1f;
    /** How far below an entity's feet its projectile hit box reaches. */
    private static final float HIT_BOX_BELOW = 0.05f;
    /** How far above an entity's height its projectile hit box reaches. */
    private static final float HIT_BOX_ABOVE = 0.1f;

    public final List<Projectile> live = new ArrayList<>();
    public final List<Projectile> stuck = new ArrayList<>();
    private static final int MAX_POOL = MAX_LIVE + MAX_STUCK;
    private final ArrayDeque<Projectile> pool = new ArrayDeque<>(MAX_POOL);
    private final Random rng = new Random();
    /**
     * Id the next {@link #fire} call hands its projectiles. It starts at 0, so
     * the -1 an {@code Npc} holds before its first torso wound is never a real
     * id until the counter wraps, about four billion shots into one world.
     * Ids are only ever compared for equality, so the wrap itself is harmless;
     * at worst the one shot that draws -1 counts as a repeat pellet against an
     * unwounded torso.
     */
    private int nextShotId;
    /**
     * Non-persisted deterministic diagnostic: the zone of the most recent
     * bullet or arrow hit on an NPC, null until one lands. Cleared with the
     * world.
     */
    public HitZone lastNpcHitZone;

    public void reset() {
        for (Projectile projectile : live) {
            release(projectile);
        }
        for (Projectile projectile : stuck) {
            release(projectile);
        }
        live.clear();
        stuck.clear();
        nextShotId = 0;
        lastNpcHitZone = null;
    }

    /**
     * Fires one shot (all pellets) from an entity's eye toward a direction.
     *
     * @return how many projectiles this shot actually put in the air, which is
     *         fewer than {@code def.pellets} once {@link #MAX_LIVE} is reached
     *         and zero at the cap. Callers that adjust what they just fired
     *         must use this rather than assuming the newest live projectile is
     *         theirs: at the cap it belongs to an earlier shot.
     */
    public int fire(Game g, Entity owner, boolean fromPlayer, float ox, float oy, float oz,
                    float dx, float dy, float dz, WeaponDefinition def, ItemType ammoUsed) {
        int pellets = Math.max(1, def.pellets);
        int spawned = 0;
        int shotId = nextShotId++;
        for (int i = 0; i < pellets; i++) {
            if (live.size() >= MAX_LIVE) {
                return spawned;
            }
            Projectile p = acquire();
            p.shotId = shotId;
            p.kind = switch (def.category) {
                case BOW -> Kind.ARROW;
                case FIREARM -> Kind.BULLET;
                case THROWN -> def.item == ItemType.FIRE_BOMB ? Kind.FIRE_BOMB : Kind.BOMB;
            };
            p.fromPlayer = fromPlayer;
            p.owner = owner;
            p.ammoItem = def.category == WeaponDefinition.Category.BOW ? ammoUsed : null;
            p.damage = def.damage + WeaponRegistry.ammoDamageBonus(ammoUsed);
            p.gravity = def.projectileGravity;
            p.life = Math.max(2f, def.range * 1.5f / Math.max(1f, def.projectileSpeed)) + 1.5f;
            p.fuse = p.kind == Kind.BOMB || p.kind == Kind.FIRE_BOMB ? 2.4f : 0f;
            p.x = ox;
            p.y = oy;
            p.z = oz;
            // Cone spread.
            float spreadRad = (float) Math.toRadians(def.spread);
            float sx = dx + (rng.nextFloat() * 2 - 1) * spreadRad;
            float sy = dy + (rng.nextFloat() * 2 - 1) * spreadRad;
            float sz = dz + (rng.nextFloat() * 2 - 1) * spreadRad;
            float len = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (len < 1e-5f) {
                len = 1f;
                sx = 1f;
            }
            p.vx = sx / len * def.projectileSpeed;
            p.vy = sy / len * def.projectileSpeed;
            p.vz = sz / len * def.projectileSpeed;
            updateFacing(p);
            live.add(p);
            spawned++;
        }
        return spawned;
    }

    /**
     * Scales the velocity of the {@code count} most recently fired projectiles,
     * which are the tail of {@link #live}. Paired with the count {@link #fire}
     * returns, so a shot can only ever slow its own projectiles.
     */
    public void scaleNewestVelocities(int count, float scale) {
        if (count <= 0 || scale == 1f) {
            return;
        }
        for (int i = live.size() - Math.min(count, live.size()); i < live.size(); i++) {
            Projectile p = live.get(i);
            p.vx *= scale;
            p.vy *= scale;
            p.vz *= scale;
            updateFacing(p);
        }
    }

    private static void updateFacing(Projectile p) {
        p.yaw = (float) Math.toDegrees(Math.atan2(p.vx, -p.vz));
        float horiz = (float) Math.sqrt(p.vx * p.vx + p.vz * p.vz);
        p.pitch = (float) Math.toDegrees(Math.atan2(p.vy, horiz));
    }

    /** True for the finite-fuse projectiles that must survive a save boundary. */
    public static boolean isExplosive(Kind kind) {
        return kind == Kind.BOMB || kind == Kind.FIRE_BOMB;
    }

    /**
     * Restores a validated in-flight explosive from persistent state. The same
     * global projectile cap used by live firing is enforced here, and derived
     * render/transient fields are rebuilt rather than serialized.
     */
    public boolean restoreExplosive(Projectile p, Entity owner) {
        if (p == null || !isExplosive(p.kind) || p.fuse <= 0 || p.life <= 0
                || live.size() >= MAX_LIVE) {
            return false;
        }
        p.owner = owner;
        p.ammoItem = null;
        p.stuck = false;
        p.stuckTime = 0;
        p.detonated = false;
        p.shotId = 0;
        updateFacing(p);
        live.add(p);
        return true;
    }

    public void update(Game g, float dt) {
        for (Iterator<Projectile> it = live.iterator(); it.hasNext(); ) {
            Projectile p = it.next();
            p.life -= dt;
            if (p.life <= 0) {
                it.remove();
                release(p);
                continue;
            }
            if (p.fuse > 0) {
                p.fuse -= dt;
                if (p.fuse <= 0) {
                    detonate(g, p);
                    it.remove();
                    release(p);
                    continue;
                }
            }
            p.vy -= p.gravity * dt;
            updateFacing(p);

            float nx = p.x + p.vx * dt;
            float ny = p.y + p.vy * dt;
            float nz = p.z + p.vz * dt;
            if (step(g, p, nx, ny, nz)) {
                it.remove();
                if (!p.stuck) {
                    release(p);
                }
            }
        }

        for (Iterator<Projectile> it = stuck.iterator(); it.hasNext(); ) {
            Projectile p = it.next();
            p.stuckTime += dt;
            if (p.stuckTime > STUCK_LIFE) {
                it.remove();
                release(p);
            }
        }
    }

    /** Sweeps the projectile from its position to (nx,ny,nz); true = consumed. */
    private boolean step(Game g, Projectile p, float nx, float ny, float nz) {
        float dx = nx - p.x, dy = ny - p.y, dz = nz - p.z;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(1, (int) Math.ceil(dist / 0.45f));
        float sx = dx / steps, sy = dy / steps, sz = dz / steps;
        for (int i = 0; i < steps; i++) {
            float px = p.x + sx, py = p.y + sy, pz = p.z + sz;

            // Entity hit.
            Entity hit = entityAt(g, p, px, py, pz);
            if (hit != null) {
                // People are hurt by where a bullet or arrow went in, and this
                // sample can be up to a sub-step past that point.
                HitZone zone = null;
                if ((p.kind == Kind.BULLET || p.kind == Kind.ARROW) && hit instanceof Npc n) {
                    zone = HitZone.classify(n, entryY(n, p.x, p.y, p.z, px, py, pz));
                }
                if (onEntityHit(g, p, hit, zone, px, py, pz)) {
                    return true;
                }
                return false;
            }

            // Block hit.
            BlockType t = g.world.getBlock((int) Math.floor(px), (int) Math.floor(py),
                    (int) Math.floor(pz));
            if (t.solid) {
                onBlockHit(g, p, px, py, pz, t);
                return p.kind != Kind.BOMB && p.kind != Kind.FIRE_BOMB;
            }
            p.x = px;
            p.y = py;
            p.z = pz;
        }
        return false;
    }

    private Entity entityAt(Game g, Projectile p, float px, float py, float pz) {
        if ((p.kind == Kind.BOMB || p.kind == Kind.FIRE_BOMB) && p.impactedEntity) {
            return null;
        }
        for (Creature c : g.entities.creatures) {
            if (!c.dead && inAabb(c, px, py, pz)) {
                return c;
            }
        }
        for (Npc n : g.entities.npcs) {
            if (!n.dead && n != p.owner
                    && (!(p.owner instanceof Npc owner) || !n.alliedWith(owner))
                    && inAabb(n, px, py, pz)) {
                return n;
            }
        }
        if (!p.fromPlayer && g.player != null && !g.player.dead
                && inAabb(g.player, px, py, pz)) {
            return g.player;
        }
        return null;
    }

    private static boolean inAabb(Entity e, float px, float py, float pz) {
        float hw = e.width / 2f + HIT_BOX_PAD_XZ;
        return px > e.pos.x - hw && px < e.pos.x + hw
                && py > e.pos.y - HIT_BOX_BELOW && py < e.pos.y + e.height + HIT_BOX_ABOVE
                && pz > e.pos.z - hw && pz < e.pos.z + hw;
    }

    /**
     * World Y at which the segment {@code (x0,y0,z0) -> (x1,y1,z1)} enters
     * {@code e}'s hit box, the box {@link #inAabb} tests, by the slab method.
     * A start already inside the box is its own entry point. The end is the
     * sample {@link #inAabb} just matched, so the segment always reaches the
     * box; the result is clamped to the segment regardless.
     *
     * <p>{@link #step} samples a path every 0.45 blocks, so the first sample
     * inside a body can be that far past the point the projectile actually
     * went in. A steep shot through the top of a head would otherwise be
     * judged by a point in the chest.
     */
    static float entryY(Entity e, float x0, float y0, float z0, float x1, float y1, float z1) {
        float hw = e.width / 2f + HIT_BOX_PAD_XZ;
        float dy = y1 - y0;
        float enter = Math.max(slabEntry(x0, x1 - x0, e.pos.x - hw, e.pos.x + hw),
                Math.max(slabEntry(y0, dy, e.pos.y - HIT_BOX_BELOW,
                                e.pos.y + e.height + HIT_BOX_ABOVE),
                        slabEntry(z0, z1 - z0, e.pos.z - hw, e.pos.z + hw)));
        float t = Math.min(1f, Math.max(0f, enter));
        return y0 + dy * t;
    }

    /**
     * Segment parameter at which a coordinate moving from {@code start} by
     * {@code delta} enters {@code [lo, hi]}; negative when it starts inside.
     * No movement on this axis means the whole segment shares the end's
     * coordinate, which is inside, so the axis sets no bound.
     */
    private static float slabEntry(float start, float delta, float lo, float hi) {
        if (delta == 0f) {
            return Float.NEGATIVE_INFINITY;
        }
        return Math.min((lo - start) / delta, (hi - start) / delta);
    }

    /**
     * @param zone where a bullet or arrow entered an NPC victim; null for any
     *             other projectile or victim, which keep plain impact damage
     * @return true when the projectile is consumed by the hit.
     */
    private boolean onEntityHit(Game g, Projectile p, Entity victim, HitZone zone,
                                float px, float py, float pz) {
        if (p.kind == Kind.BOMB || p.kind == Kind.FIRE_BOMB) {
            // Bombs thud off targets and drop at their feet, still fused.
            p.x = px;
            p.y = Math.max(victim.pos.y + 0.05f, py);
            p.z = pz;
            p.vx = 0;
            p.vz = 0;
            p.vy = Math.min(p.vy, 0);
            p.impactedEntity = true;
            return false;
        }
        if (victim instanceof com.veylon.entity.Player player) {
            if (player.abilities.invulnerable()) return true;
            player.hurtPhysical(g, p.damage, true);
            g.audio.playHurt();
            g.log("You are hit by " + (p.kind == Kind.ARROW ? "an arrow!" : "a shot!"));
        } else {
            if (zone != null && victim instanceof Npc person) {
                lastNpcHitZone = zone;
                ProjectileLethality.applyHit(person, zone, p);
            } else {
                victim.hurt(p.damage, p.fromPlayer);
            }
            victim.knockback(p.x - p.vx, p.z - p.vz, 2.2f);
            if (victim instanceof Creature c) {
                c.fear = 1f;
                c.bleedTimer = Math.max(c.bleedTimer, 16f);
                if (p.kind == Kind.ARROW && rng.nextFloat() < 0.55f) {
                    c.stuckArrows++;
                    c.stuckArrowType = p.ammoItem == null ? ItemType.ARROW : p.ammoItem;
                }
            }
            if (victim instanceof Npc n) {
                onNpcShot(g, n, p);
            }
        }
        g.particles.blood(victim.pos.x, victim.pos.y + victim.height * 0.6f, victim.pos.z);
        g.audio.playArrowImpact(victim.pos.x, victim.pos.y, victim.pos.z);
        return true;
    }

    private void onNpcShot(Game g, Npc n, Projectile p) {
        if (p.fromPlayer) {
            g.settlementManager.onNpcAttackedByPlayer(g, n);
        }
        // Being shot reveals roughly where the shooter stood, unless the shooter
        // is a player no one can perceive (R11); reputation above still applies.
        if (p.owner != null && !(p.owner instanceof com.veylon.entity.Player shooter
                && !shooter.isPerceivableByAi())) {
            n.lastKnown.set(p.owner.pos);
            n.lastKnownAge = 0f;
            n.searchTimer = 12f;
        }
    }

    private void onBlockHit(Game g, Projectile p, float px, float py, float pz, BlockType t) {
        switch (p.kind) {
            case ARROW -> {
                g.audio.playArrowImpact(px, py, pz);
                // Recoverable arrows stick in the surface (bounded pool).
                if (rng.nextFloat() < 0.6f && stuck.size() < MAX_STUCK) {
                    p.stuck = true;
                    p.stuckTime = 0;
                    stuck.add(p);
                }
                g.particles.blockDust(t, px, py, pz, 2);
            }
            case BULLET -> {
                g.particles.blockDust(t, px, py, pz, 3);
                g.audio.playBulletImpact(px, py, pz);
            }
            case BOMB, FIRE_BOMB -> {
                // Bombs stop against surfaces and keep cooking.
                p.vx *= 0.1f;
                p.vz *= 0.1f;
                p.vy = 0;
            }
        }
    }

    private void detonate(Game g, Projectile p) {
        if (p.detonated) {
            return;
        }
        p.detonated = true;
        if (p.kind == Kind.FIRE_BOMB) {
            g.explosions.explode(g, p.x, p.y, p.z, 1.6f, 4f, 0.9f, p.fromPlayer);
            g.explosions.igniteNearby(g, p.x, p.y, p.z, 3, 6);
        } else {
            g.explosions.explode(g, p.x, p.y, p.z, 2.6f, 14f, 0f, p.fromPlayer);
        }
    }

    /** Deterministic QA hook; normal gameplay keeps organic spread. */
    public void setRandomSeed(long seed) {
        rng.setSeed(seed);
    }

    /** Nearest recoverable stuck arrow within reach, or null. */
    public Projectile nearestStuckArrow(float x, float y, float z, float range) {
        Projectile best = null;
        double bestD = range * range;
        for (Projectile p : stuck) {
            double dx = p.x - x, dy = p.y - y, dz = p.z - z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    public void pickUp(Game g, Projectile p) {
        if (!stuck.remove(p)) {
            return;
        }
        ItemType item = p.ammoItem == null ? ItemType.ARROW : p.ammoItem;
        g.player.inventory.add(item, 1);
        g.audio.playClick();
        g.log("Recovered 1 " + item.displayName + ".");
        release(p);
    }

    public int liveCount() {
        return live.size();
    }

    /** Current retained projectile capacity, exposed for deterministic budget QA. */
    public int pooledCount() {
        return pool.size();
    }

    private Projectile acquire() {
        Projectile projectile = pool.pollFirst();
        if (projectile == null) {
            projectile = new Projectile();
        }
        resetState(projectile);
        return projectile;
    }

    private void release(Projectile projectile) {
        if (projectile == null || pool.size() >= MAX_POOL) {
            return;
        }
        resetState(projectile);
        pool.addFirst(projectile);
    }

    /** Every field is reset so pooled ownership and persisted restoration never leak state. */
    private static void resetState(Projectile projectile) {
        projectile.kind = null;
        projectile.x = projectile.y = projectile.z = 0;
        projectile.vx = projectile.vy = projectile.vz = 0;
        projectile.gravity = 0;
        projectile.damage = 0;
        projectile.life = 0;
        projectile.fromPlayer = false;
        projectile.owner = null;
        projectile.ammoItem = null;
        projectile.fuse = 0;
        projectile.stuck = false;
        projectile.stuckTime = 0;
        projectile.impactedEntity = false;
        projectile.detonated = false;
        projectile.yaw = projectile.pitch = 0;
        projectile.shotId = 0;
    }
}
