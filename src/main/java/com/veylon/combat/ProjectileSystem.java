package com.veylon.combat;

import com.veylon.Game;
import com.veylon.entity.Creature;
import com.veylon.entity.Entity;
import com.veylon.entity.Npc;
import com.veylon.item.ItemType;
import com.veylon.world.BlockType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Shared projectile simulation for player and NPC ranged attacks: arrows,
 * musket balls, pellets and thrown bombs. Substepped segment sweeps against
 * blocks and entity AABBs; strict caps so projectiles can never leak.
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
    }

    public final List<Projectile> live = new ArrayList<>();
    public final List<Projectile> stuck = new ArrayList<>();
    private final Random rng = new Random();

    public void reset() {
        live.clear();
        stuck.clear();
    }

    /** Fires one shot (all pellets) from an entity's eye toward a direction. */
    public void fire(Game g, Entity owner, boolean fromPlayer, float ox, float oy, float oz,
                     float dx, float dy, float dz, WeaponDefinition def, ItemType ammoUsed) {
        int pellets = Math.max(1, def.pellets);
        for (int i = 0; i < pellets; i++) {
            if (live.size() >= MAX_LIVE) {
                return;
            }
            Projectile p = new Projectile();
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
                continue;
            }
            if (p.fuse > 0) {
                p.fuse -= dt;
                if (p.fuse <= 0) {
                    detonate(g, p);
                    it.remove();
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
            }
        }

        for (Iterator<Projectile> it = stuck.iterator(); it.hasNext(); ) {
            Projectile p = it.next();
            p.stuckTime += dt;
            if (p.stuckTime > STUCK_LIFE) {
                it.remove();
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
                if (onEntityHit(g, p, hit, px, py, pz)) {
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
        float hw = e.width / 2f + 0.1f;
        return px > e.pos.x - hw && px < e.pos.x + hw
                && py > e.pos.y - 0.05f && py < e.pos.y + e.height + 0.1f
                && pz > e.pos.z - hw && pz < e.pos.z + hw;
    }

    /** @return true when the projectile is consumed by the hit. */
    private boolean onEntityHit(Game g, Projectile p, Entity victim,
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
            player.hurtPhysical(g, p.damage, true);
            g.audio.playHurt();
            g.log("You are hit by " + (p.kind == Kind.ARROW ? "an arrow!" : "a shot!"));
        } else {
            victim.hurt(p.damage, p.fromPlayer);
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
        // Being shot reveals roughly where the shooter stood.
        if (p.owner != null) {
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
        stuck.remove(p);
        ItemType item = p.ammoItem == null ? ItemType.ARROW : p.ammoItem;
        g.player.inventory.add(item, 1);
        g.audio.playClick();
        g.log("Recovered 1 " + item.displayName + ".");
    }

    public int liveCount() {
        return live.size();
    }
}
