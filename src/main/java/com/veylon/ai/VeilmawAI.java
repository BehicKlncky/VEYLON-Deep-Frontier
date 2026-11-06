package com.veylon.ai;

import com.veylon.Game;
import com.veylon.combat.WorldNoise;
import com.veylon.engine.EcologySounds.Cue;
import com.veylon.entity.Affliction;
import com.veylon.entity.Creature;
import com.veylon.entity.Creature.CreatureState;
import com.veylon.world.urban.Habitat;
import com.veylon.entity.Player;
import com.veylon.simulation.FireSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;

/**
 * The Veilmaw's brain (urban milestone 15): a territorial ambush organism bound to one growth
 * node. Where a Veilbound hunts by sound and follows the player through buildings, the Veilmaw
 * never pursues: it lies folded beside its node and wakes only to what reaches its territory,
 * telegraphs, lunges once, recoils and walks home.
 *
 * <ul>
 *   <li><b>Senses</b>, every quarter second while it is folded, walking home or creeping: the
 *       perceivable player inside its territory within {@value #TOUCH} blocks (touch, whatever
 *       the light), or standing in block light of at least {@value #PLAYER_LIGHT} in plain sight;
 *       the loudest audible noise, through the shared occluded hearing query, whose source lies
 *       in the territory; and its own cell lit to {@value #LURE_LIGHT} or more by a placed light.
 *       Daylight is never light to it, and its node's glow is texture, not light.</li>
 *   <li><b>Telegraph:</b> {@value #WINDUP} s (after a blow, {@value #HURT_WINDUP} s) with its
 *       hood rising and its veins brightening, and a rising call. When the telegraph ends it
 *       lunges only if its target is still within {@value #LUNGE_RANGE} blocks; a player who
 *       backed out of reach is let go.</li>
 *   <li><b>Lunge:</b> a straight dash at the point it chose when it committed, at
 *       {@value #LUNGE_SPEED} blocks/s for at most {@value #LUNGE_SECONDS} s, and at most one
 *       strike: {@value #STRIKE_DAMAGE} physical damage through the player's armour and bleeding
 *       gate when the player is within {@value #STRIKE_REACH} blocks, and the existing sickness
 *       on a seeded roll. Then it recoils, exposed, and walks home.</li>
 *   <li><b>Lure:</b> a lamp lighting its ground draws it: it creeps toward the brightest placed
 *       light inside its territory and smothers a torch it reaches. A light it cannot reach, or
 *       one shining in from outside, is ignored for {@value #LURE_COOLDOWN} s.</li>
 *   <li><b>Node:</b> a torch, lantern or campfire beside the node sets it alight, and when the
 *       node is gone, burnt or broken, the Veilmaw withers. Folded beside a living node it heals
 *       {@value #HEAL_PER_SECOND} health a second; badly hurt, it retreats there.</li>
 *   <li><b>Leash:</b> every point it moves toward is clamped inside its territory, and outside
 *       it, it walks home. Only the shared fire panic of a burning body overrides that.</li>
 * </ul>
 *
 * <p>All of it is transient intent: a save keeps only the body's health and position (the
 * frozen creature record and the encounter ledger), and a load or reactivation starts it folded.
 * Rolls use the creature AI stream, so presentation never changes an outcome.
 */
public final class VeilmawAI {
    /** Radius of a territory around its node, in blocks. */
    public static final float TERRITORY = com.veylon.world.urban.EcologySites.TERRITORY;
    public static final float WINDUP = 1.1f;
    public static final float HURT_WINDUP = 0.55f;
    public static final float LUNGE_SPEED = 7f;
    public static final float LUNGE_SECONDS = 0.6f;
    public static final float LUNGE_RANGE = 6f;
    public static final float STRIKE_REACH = 1.35f;
    public static final float STRIKE_DAMAGE = 7f;
    public static final float SICKNESS_CHANCE = 0.4f;
    public static final float SICKNESS_SECONDS = 60f;
    public static final float RECOIL_SECONDS = 1.3f;
    /** Seconds from the start of one lunge before another telegraph may begin. */
    public static final float COOLDOWN = 3f;
    public static final float TOUCH = 1.8f;
    public static final float PLAYER_LIGHT = 0.4f;
    public static final float LURE_LIGHT = 0.2f;
    public static final float CREEP_SPEED = 1.8f;
    public static final float CREEP_SECONDS = 9f;
    public static final float LURE_COOLDOWN = 20f;
    /** How close it must come to a light to smother it. */
    public static final float SMOTHER_REACH = 1.4f;
    public static final float WALK_SPEED = 1.6f;
    public static final float RETURN_SECONDS = 12f;
    public static final float HEAL_PER_SECOND = 0.6f;
    /** Below this share of its health it stops answering anything but touch and walks home. */
    public static final float RETREAT_HEALTH = 0.35f;
    /** How near a fire must burn before it shies away from it. */
    public static final float FIRE_SHY = 2.5f;
    private static final float SENSE_INTERVAL = 0.25f;
    private static final float WARN_INTERVAL = 15f;
    private static final int VERTICAL = 3;

    private VeilmawAI() {
    }

    public static void update(Game g, Creature c, float dt) {
        Habitat home = c.home;
        if (home == null || !home.territorial()) {
            // A staged body with no planned node keeps a territory where it was put down.
            home = c.home = Habitat.around(c.pos.x, c.pos.y, c.pos.z, TERRITORY);
        }
        c.attackCooldown -= dt;
        c.decideTimer -= dt;
        c.lureCooldown = Math.max(0f, c.lureCooldown - dt);
        c.warnCooldown = Math.max(0f, c.warnCooldown - dt);
        boolean hurt = c.lastHealth >= 0f && c.health < c.lastHealth - 1e-4f;
        if (hurt) {
            g.audio.playEcology(Cue.PAIN, c.pos.x, c.pos.y, c.pos.z);
        }

        if (home.node() != null) {
            // Every tick (seven cell reads): a flame set beside the node catches before the
            // Veilmaw can smother it, and from then on the fire keeps it away.
            if (nodeGone(g.world, home.node())) {
                wither(g, c);
                return;
            }
            igniteFromAdjacentFlame(g, home.node());
        }

        // Fire shies it away, inside its ground; a burning body has already run in FirePanic.
        Vec3i fire = g.fire.nearestBurning(c.pos.x, c.pos.y, c.pos.z, FIRE_SHY);
        if (fire != null && c.state != CreatureState.LUNGING) {
            c.state = CreatureState.FLEE;
            c.windup = -1f;
            c.target.set(c.pos.x * 2 - (fire.x() + 0.5f), c.pos.y, c.pos.z * 2 - (fire.z() + 0.5f));
            home.clamp(c.target);
            Steering.moveToward(c, c.target.x, c.target.z, WALK_SPEED * 1.6f);
            c.lastHealth = c.health;
            return;
        }
        if (c.state == CreatureState.FLEE) {
            startReturn(c);
        }

        if (hurt && c.state != CreatureState.LUNGING) {
            Player p = g.player;
            if (c.health < c.maxHealth * RETREAT_HEALTH) {
                startReturn(c);
            } else if (p.isPerceivableByAi() && !p.dead && home.outside(p.pos.x, p.pos.z) <= 4f) {
                startStir(g, c, p.pos.x, p.pos.y, p.pos.z, true, HURT_WINDUP);
            }
        }
        c.lastHealth = c.health;

        switch (c.state) {
            case STIRRING -> stir(g, c, home, dt);
            case LUNGING -> lunge(g, c, home, dt);
            case RECOILING -> {
                Steering.stop(c);
                if ((c.actionTimer -= dt) <= 0f) {
                    startReturn(c);
                }
            }
            case CREEPING -> creep(g, c, home, dt);
            case RETURNING -> walkHome(g, c, home, dt);
            default -> folded(g, c, home, dt);
        }
    }

    // ------------------------------------------------------------------
    // States
    // ------------------------------------------------------------------

    private static void folded(Game g, Creature c, Habitat home, float dt) {
        c.state = CreatureState.FOLDED;
        Steering.stop(c);
        if (home.outside(c.pos.x, c.pos.z) > 1.5f) {
            startReturn(c);
            return;
        }
        Vec3i rest = home.rest();
        if (rest != null && horizontal(c, rest.x() + 0.5f, rest.z() + 0.5f) <= 1.5f) {
            c.health = Math.min(c.maxHealth, c.health + HEAL_PER_SECOND * dt);
            c.lastHealth = c.health;
        }
        sense(g, c, home);
    }

    private static void walkHome(Game g, Creature c, Habitat home, float dt) {
        Vec3i rest = home.rest();
        float tx = rest.x() + 0.5f, tz = rest.z() + 0.5f;
        if (horizontal(c, tx, tz) <= 0.5f || (c.actionTimer -= dt) <= 0f) {
            Steering.stop(c);
            c.state = CreatureState.FOLDED;
            return;
        }
        Steering.moveToward(c, tx, tz, WALK_SPEED);
        if (home.outside(c.pos.x, c.pos.z) <= 0f) {
            sense(g, c, home);
        }
    }

    private static void stir(Game g, Creature c, Habitat home, float dt) {
        Steering.stop(c);
        Player p = g.player;
        if (c.targetIsPlayer) {
            if (!p.isPerceivableByAi() || p.dead) {
                // Visibility controls forget player-derived intent at once.
                startReturn(c);
                return;
            }
            c.target.set(p.pos);
        }
        face(c, c.target.x, c.target.z);
        if ((c.windup -= dt) > 0f) {
            return;
        }
        c.windup = -1f;
        boolean inReach = horizontal(c, c.target.x, c.target.z) <= LUNGE_RANGE
                && Math.abs(c.target.y - c.pos.y) <= 2f && home.outside(c.target.x, c.target.z) <= 1f
                && (!c.targetIsPlayer || p.isPerceivableByAi() && !p.dead);
        if (!inReach) {
            startReturn(c);
            return;
        }
        home.clamp(c.target);
        c.state = CreatureState.LUNGING;
        c.actionTimer = LUNGE_SECONDS;
        c.attackCooldown = COOLDOWN;
        c.hasTarget = false; // set once a strike lands: one strike per lunge
        g.audio.playEcology(Cue.LUNGE, c.pos.x, c.pos.y, c.pos.z);
    }

    private static void lunge(Game g, Creature c, Habitat home, float dt) {
        Player p = g.player;
        if (!c.hasTarget && p.isPerceivableByAi() && !p.dead
                && horizontal(c, p.pos.x, p.pos.z) <= STRIKE_REACH && Math.abs(p.pos.y - c.pos.y) < 1.2f) {
            c.hasTarget = true;
            strike(g, c, p);
            startRecoil(c);
            return;
        }
        if ((c.actionTimer -= dt) <= 0f || horizontal(c, c.target.x, c.target.z) <= 0.4f
                || c.horizontalCollision && c.actionTimer < LUNGE_SECONDS - 0.15f) {
            startRecoil(c);
            return;
        }
        Steering.moveToward(c, c.target.x, c.target.z, LUNGE_SPEED);
        if (home.outside(c.pos.x, c.pos.z) > 0.5f) {
            startRecoil(c); // the edge of its ground ends any lunge
        }
    }

    private static void creep(Game g, Creature c, Habitat home, float dt) {
        if ((c.actionTimer -= dt) <= 0f || !emitsLight(g.world.getBlock(cell(c.target.x), cell(c.target.y),
                cell(c.target.z)))) {
            c.lureCooldown = LURE_COOLDOWN;
            startReturn(c);
            return;
        }
        if (horizontal(c, c.target.x + 0.5f, c.target.z + 0.5f) <= SMOTHER_REACH
                && Math.abs(c.target.y - c.pos.y) <= 1.5f) {
            smother(g, c);
            return;
        }
        float tx = c.target.x + 0.5f, tz = c.target.z + 0.5f;
        float ox = c.target.x, oy = c.target.y, oz = c.target.z;
        c.target.set(tx, c.pos.y, tz);
        home.clamp(c.target);
        float cx = c.target.x, cz = c.target.z;
        c.target.set(ox, oy, oz);
        if (horizontal(c, cx, cz) <= 0.3f) {
            // The light stands beyond the edge of its ground: it cannot reach it.
            c.lureCooldown = LURE_COOLDOWN;
            startReturn(c);
            return;
        }
        Steering.moveToward(c, cx, cz, CREEP_SPEED);
        sensePlayer(g, c, home);
    }

    // ------------------------------------------------------------------
    // Senses
    // ------------------------------------------------------------------

    /** Folded or walking home: the player first, then noise, then a lure. */
    private static void sense(Game g, Creature c, Habitat home) {
        if (c.decideTimer > 0f || c.attackCooldown > 0f) {
            return;
        }
        c.decideTimer = SENSE_INTERVAL;
        if (sensePlayer(g, c, home)) {
            return;
        }
        if (c.health < c.maxHealth * RETREAT_HEALTH) {
            return; // badly hurt: only touch still wakes it
        }
        WorldNoise.NoiseEvent heard = g.noise.loudestAudible(g.world, c.pos.x, c.pos.y, c.pos.z, 0f,
                c.lastHeard, c);
        if (heard != null && heard != c.lastHeard && heard.source != c) {
            c.lastHeard = heard;
            float ground = home.floorY() + VERTICAL;
            if (home.outside(heard.x, heard.z) <= 2f && Math.abs(heard.y - ground) <= VERTICAL + 2) {
                startStir(g, c, heard.x, heard.y, heard.z, false, WINDUP);
                return;
            }
        }
        if (c.lureCooldown <= 0f && light(g.world, c.pos.x, c.pos.y, c.pos.z) >= LURE_LIGHT) {
            Vec3i lamp = brightestLamp(g.world, home, c);
            if (lamp == null) {
                c.lureCooldown = LURE_COOLDOWN; // shining in from outside its ground
                return;
            }
            c.state = CreatureState.CREEPING;
            c.actionTimer = CREEP_SECONDS;
            c.target.set(lamp.x(), lamp.y(), lamp.z());
            g.audio.playEcology(Cue.STIR, c.pos.x, c.pos.y, c.pos.z);
        }
    }

    /** Touch, or a lit player in plain sight, inside the territory. */
    private static boolean sensePlayer(Game g, Creature c, Habitat home) {
        Player p = g.player;
        if (!p.isPerceivableByAi() || p.dead || home.outside(p.pos.x, p.pos.z) > 1f
                || Math.abs(p.pos.y - c.pos.y) > VERTICAL) {
            return false;
        }
        boolean touch = horizontal(c, p.pos.x, p.pos.z) <= TOUCH && Math.abs(p.pos.y - c.pos.y) < 1.5f;
        boolean lit = !touch && light(g.world, p.pos.x, p.pos.y + 1f, p.pos.z) >= PLAYER_LIGHT
                && sees(g.world, c, home.node(), p.pos.x, p.pos.y + 1.2f, p.pos.z);
        if (!touch && !lit) {
            return false;
        }
        if (c.health < c.maxHealth * RETREAT_HEALTH && !touch) {
            return false;
        }
        startStir(g, c, p.pos.x, p.pos.y, p.pos.z, true, WINDUP);
        return true;
    }

    private static void startStir(Game g, Creature c, float x, float y, float z, boolean player, float windup) {
        c.state = CreatureState.STIRRING;
        c.windup = windup;
        c.target.set(x, y, z);
        c.targetIsPlayer = player;
        c.targetEntity = player ? g.player : null;
        Steering.stop(c);
        g.audio.playEcology(Cue.STIR, c.pos.x, c.pos.y, c.pos.z);
        if (c.warnCooldown <= 0f && g.player.isPerceivableByAi()
                && c.distSqTo(g.player) < 24 * 24) {
            c.warnCooldown = WARN_INTERVAL;
            g.log("The membrane ripples: something on it is waking.");
        }
    }

    private static void startRecoil(Creature c) {
        Steering.stop(c);
        c.state = CreatureState.RECOILING;
        c.actionTimer = RECOIL_SECONDS;
        c.targetEntity = null;
    }

    private static void startReturn(Creature c) {
        c.state = CreatureState.RETURNING;
        c.actionTimer = RETURN_SECONDS;
        c.windup = -1f;
        c.targetEntity = null;
        c.targetIsPlayer = false;
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    /** The one blow of a lunge, through the player's own damage and affliction gates. */
    private static void strike(Game g, Creature c, Player p) {
        boolean exposed = !p.abilities.invulnerable();
        p.hurtPhysical(g, STRIKE_DAMAGE, true);
        if (g.entities.nextCreatureAiFloat() < SICKNESS_CHANCE) {
            boolean ill = p.has(Affliction.SICKNESS);
            p.addAffliction(Affliction.SICKNESS, SICKNESS_SECONDS);
            if (exposed && !ill) {
                g.log("The membrane's sting leaves you feverish: SICKNESS. Rest warm and dry, or take medicine.");
            }
        }
        p.knockback(c.pos.x, c.pos.z, 4f);
        g.noise.emit(g, c.pos.x, c.pos.y, c.pos.z, 20f, 0.65f, "creature-attack", false, c);
        if (exposed) {
            g.audio.playHit();
            g.audio.playHurt();
            g.log("The Veilmaw's hood slaps over you! (-" + (int) STRIKE_DAMAGE + " HP)");
        }
    }

    /** Puts out the torch it crept to; lanterns and fires only make it recoil. */
    private static void smother(Game g, Creature c) {
        int x = cell(c.target.x), y = cell(c.target.y), z = cell(c.target.z);
        if (g.world.getBlock(x, y, z) == BlockType.TORCH) {
            g.world.setBlock(x, y, z, BlockType.AIR, true);
            g.particles.smoke(x + 0.5f, y + 0.6f, z + 0.5f, 6);
            g.audio.playEcology(Cue.SMOTHER, x + 0.5f, y + 0.5f, z + 0.5f);
            if (g.player.distSqTo(x + 0.5f, y, z + 0.5f) < 32 * 32) {
                g.log("The Veilmaw smothers the torch under its hood.");
            }
        } else {
            c.lureCooldown = LURE_COOLDOWN;
        }
        startRecoil(c);
    }

    private static void wither(Game g, Creature c) {
        if (g.player.distSqTo(c) < 40 * 40) {
            g.log("The Veilmaw convulses and withers with its node.");
        }
        c.killBy(false);
    }

    /** A flame beside a living node sets it alight; burning, it chars to ash and the Veilmaw withers. */
    private static void igniteFromAdjacentFlame(Game g, Vec3i node) {
        World w = g.world;
        if (w.getBlock(node.x(), node.y(), node.z()) != BlockType.GROWTH_NODE) {
            return;
        }
        int[][] around = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}};
        for (int[] d : around) {
            BlockType b = w.getBlock(node.x() + d[0], node.y() + d[1], node.z() + d[2]);
            if (b == BlockType.TORCH || b == BlockType.LANTERN || b == BlockType.CAMPFIRE) {
                g.fire.ignite(g, node.x(), node.y(), node.z(), true, FireSystem.NO_ORIGIN);
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /** True once a loaded node cell holds anything but a node: burnt to ash, broken or dug out. */
    public static boolean nodeGone(World w, Vec3i node) {
        return w.isColumnLoaded(node.x(), node.z())
                && w.getBlock(node.x(), node.y(), node.z()) != BlockType.GROWTH_NODE;
    }

    /** Block light only: lamps, fires and torches. Daylight never wakes it. */
    static float light(World w, float x, float y, float z) {
        int bx = cell(x), bz = cell(z);
        return w.isColumnLoaded(bx, bz) ? w.blockLight(bx, cell(y), bz) : 0f;
    }

    private static boolean emitsLight(BlockType b) {
        return b == BlockType.TORCH || b == BlockType.LANTERN || b == BlockType.CAMPFIRE;
    }

    /**
     * The brightest placed light inside the territory that the Veilmaw can see, or null. Block
     * light passes through walls, so a lamp behind one is not a lure. One bounded scan of the
     * territory's cells (at most 19 x 7 x 19), run only when a lure first draws it.
     */
    static Vec3i brightestLamp(World w, Habitat home, Creature c) {
        Vec3i best = null;
        int bestLight = 0;
        int floor = (int) Math.floor(home.floorY());
        for (int x = home.x0(); x <= home.x1(); x++) {
            for (int z = home.z0(); z <= home.z1(); z++) {
                if (!home.contains(x + 0.5f, z + 0.5f) || !w.isColumnLoaded(x, z)) {
                    continue;
                }
                for (int y = floor; y <= floor + VERTICAL * 2; y++) {
                    BlockType b = w.getBlock(x, y, z);
                    if (emitsLight(b) && b.light > bestLight && sees(w, c, home.node(), x + 0.5f, y + 0.5f, z + 0.5f)) {
                        bestLight = b.light;
                        best = new Vec3i(x, y, z);
                    }
                }
            }
        }
        return best;
    }

    /**
     * A loaded voxel ray from its raised hood. The node is its own organ, so it never blocks the
     * view; any other solid or unloaded cell does.
     */
    private static boolean sees(World w, Creature c, Vec3i node, float tx, float ty, float tz) {
        float x = c.pos.x, y = c.pos.y + 0.8f, z = c.pos.z;
        float dx = tx - x, dy = ty - y, dz = tz - z;
        int steps = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) * 5));
        if (steps > 640) {
            return false;
        }
        for (int i = 0; i <= steps; i++) {
            float f = i / (float) steps;
            int bx = cell(x + dx * f), by = cell(y + dy * f), bz = cell(z + dz * f);
            if (!w.isColumnLoaded(bx, bz)) {
                return false;
            }
            boolean own = node != null && bx == node.x() && by == node.y() && bz == node.z();
            if (!own && w.getBlock(bx, by, bz).solid) {
                return false;
            }
        }
        return true;
    }

    private static float horizontal(Creature c, float x, float z) {
        float dx = x - c.pos.x, dz = z - c.pos.z;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private static void face(Creature c, float x, float z) {
        c.yaw = (float) Math.toDegrees(Math.atan2(x - c.pos.x, -(z - c.pos.z)));
    }

    private static int cell(float v) {
        return (int) Math.floor(v);
    }
}
