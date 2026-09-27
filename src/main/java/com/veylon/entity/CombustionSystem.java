package com.veylon.entity;

import com.veylon.Game;
import com.veylon.simulation.FastTickSystem;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;

import static com.veylon.entity.CombustionConstants.AFTERBURN_DPS_CREATURE;
import static com.veylon.entity.CombustionConstants.AFTERBURN_DPS_NPC;
import static com.veylon.entity.CombustionConstants.AFTERBURN_DPS_PLAYER;
import static com.veylon.entity.CombustionConstants.AFTERBURN_FLASH;
import static com.veylon.entity.CombustionConstants.BURN_INJURY_AFTER_SECONDS;
import static com.veylon.entity.CombustionConstants.CONTACT_DPS_CREATURE;
import static com.veylon.entity.CombustionConstants.CONTACT_DPS_NPC;
import static com.veylon.entity.CombustionConstants.CONTACT_DPS_PLAYER;
import static com.veylon.entity.CombustionConstants.HEAT_DECAY_PER_SECOND;
import static com.veylon.entity.CombustionConstants.IMMERSION_FRACTION;
import static com.veylon.entity.CombustionConstants.MAX_BURN_SECONDS;
import static com.veylon.entity.CombustionConstants.RAIN_EXTINGUISH_SECONDS;
import static com.veylon.entity.CombustionConstants.SCORCH_SECONDS;
import static com.veylon.entity.CombustionConstants.SHALLOW_WATER_DRAIN;
import static com.veylon.entity.CombustionConstants.TIMER_EPSILON;

/**
 * Living bodies on fire: the one owner of the rules for every {@link Player},
 * {@link Npc} and {@link Creature} of any species. The state itself is each
 * body's embedded {@link BodyCombustion}.
 *
 * <p><b>Commands.</b> A flame source reports a contact with {@link #expose};
 * the next fast tick applies the strongest contact each body received. A
 * direct hit sets a body alight at once with {@link #ignite}. {@link
 * #extinguish} puts one out and {@link #clear} forgets a body's fire for a new
 * life. A dead body, a Creative player and a body whose torso is under water
 * refuse a flame.
 *
 * <p><b>Sources.</b> Each fast tick first sweeps every living body's flame box
 * ({@link BodySweep}) from where the last tick sampled it to where it is now
 * and asks the world's flames whether they touch it: burning liquid
 * ({@code LiquidFireSystem.exposeContacts}), and burning blocks, fueled
 * campfires and placed torches ({@code FireSystem.exposeContacts}). A fire
 * bomb breaking on a body calls {@link #ignite} from the projectile step.
 * Nothing else hurts a body for touching a flame.
 *
 * <p><b>Attacks.</b> When the player's flame lights or takes over a person's
 * fire, that person is attacked once per bottle (the contact's source id):
 * the settlement is told, and the person looks to where the flame touched
 * them if the player can be perceived. Flames nobody threw never count.
 *
 * <p><b>Cadence.</b> Only {@link #fastTick} advances a fire, one fixed
 * {@link SimulationScheduler#FAST_DT} step per call, from {@code Game.fastTick}
 * after the player's needs and afflictions and before AI, physics and death
 * processing, so AI reads this tick's fire and a body the fire kills is
 * removed in the same tick. No medium or slow tick touches a fire, so nothing
 * advances twice. Equal simulated time under any frame partition gives the
 * same fires, whatever renders or not. The player has moved by the frame
 * before the tick samples it; people and animals move in the entity step
 * after it, so their sweep covers the previous tick's move: either way every
 * move is swept exactly once.
 *
 * <p><b>One tick of one body</b>, in order: a dead body is not ticked; a
 * Creative player's fire is cleared; the flames its sweep touched offer their
 * contacts; a torso under water puts the fire out and ignores every contact;
 * the tick's candidate contact lights the body
 * (immediately for liquid and direct hits, by heat for the rest) or refreshes
 * its fire; exposed rain on the head soaks and at last puts it out; damage;
 * afterburn fuel drains when nothing touched it. Damage per tick is
 *
 * <pre>
 *   (contact this tick ? CONTACT_DPS : AFTERBURN_DPS)[player | NPC | creature] x intensity x dt
 * </pre>
 *
 * dealt through {@link Entity#hurt} with the fire owner's attribution (the
 * player's own fire is never the player's credit), so the ignition tick costs
 * contact damage and a burn death is an ordinary whole-body death.
 */
public final class CombustionSystem implements FastTickSystem {

    /** Episodes started, and the three ways they end. Diagnostics only. */
    public int totalIgnitions;
    public int totalBurnouts;
    public int totalDoused;
    public int totalRainedOut;

    /** Reused for every body's contact sampling. */
    private final BodySweep sweep = new BodySweep();

    @Override
    public void reset() {
        totalIgnitions = 0;
        totalBurnouts = 0;
        totalDoused = 0;
        totalRainedOut = 0;
    }

    /** Whether the body is a living body: the player, a person or an animal of any species. */
    public static boolean isLivingBody(Entity e) {
        return e instanceof Player || e instanceof Npc || e instanceof Creature;
    }

    /** Whether the body can catch fire now: alive, living, and not the Creative player. */
    public static boolean canBurn(Entity e) {
        if (e == null || e.dead || !isLivingBody(e)) {
            return false;
        }
        return !(e instanceof Player p) || !p.abilities.invulnerable();
    }

    /**
     * Reports that a flame touches the body now. Applied on the next fast
     * tick, together with every other contact the body receives before it:
     * only the strongest counts, so contacts never add up.
     *
     * @param intensity the flame's strength, clamped to at most 1
     * @param sourceId  a stable id of the source: for the player's flame, the
     *                  bottle it came from (its spill id), which is what an
     *                  attack is counted by; otherwise an id within its kind
     *                  (a packed cell). Also breaks ties between equal contacts
     * @param x         where the flame touches the body
     * @return false when the body cannot burn or the contact is not a flame
     */
    public boolean expose(Entity e, CombustionSource kind, float intensity, boolean byPlayer,
                          int sourceId, float x, float y, float z) {
        if (!acceptsFlame(e, kind, intensity, x, y, z)) {
            return false;
        }
        e.combustion.offer(kind, Math.min(1f, intensity), byPlayer, sourceId, x, y, z);
        return true;
    }

    /**
     * Sets the body alight now, whatever heat it had: a burning bottle
     * breaking on it. The hit also counts as the next fast tick's contact, so
     * the first tick burns at the contact rate. A body already burning is
     * refreshed and taken over, never lit a second time.
     *
     * @return false when the body cannot burn, or its torso is under water
     */
    public boolean ignite(Game g, Entity e, CombustionSource kind, float intensity,
                          boolean byPlayer, int sourceId, float x, float y, float z) {
        if (!acceptsFlame(e, kind, intensity, x, y, z) || torsoUnderWater(e)) {
            return false;
        }
        float strength = Math.min(1f, intensity);
        BodyCombustion b = e.combustion;
        if (b.burning) {
            b.refresh(kind, strength, byPlayer, sourceId);
        } else {
            b.ignite(kind, strength, byPlayer, sourceId);
            totalIgnitions++;
            announce(g, e, "You're on fire! Deep water or open rain will put it out.");
        }
        b.touchedAt(x, y, z);
        b.offer(kind, strength, byPlayer, sourceId, x, y, z);
        return true;
    }

    /** Puts the body's flames out now; its scorch stays. Returns whether it was burning. */
    public boolean extinguish(Entity e) {
        if (e == null) {
            return false;
        }
        boolean was = e.combustion.burning;
        e.combustion.extinguish();
        return was;
    }

    /** Forgets the body's fire entirely, scorch included, for a body that lives again. */
    public void clear(Entity e) {
        if (e != null) {
            e.combustion.clear();
        }
    }

    /** Whether the body is a living body on fire; false once it is dead. */
    public boolean isBurning(Entity e) {
        return e != null && !e.dead && e.combustion.burning;
    }

    /** Living bodies on fire now, the player included. */
    public int burningBodies(Game g) {
        int n = isBurning(g.player) ? 1 : 0;
        for (int i = 0; i < g.entities.creatures.size(); i++) {
            if (isBurning(g.entities.creatures.get(i))) {
                n++;
            }
        }
        for (int i = 0; i < g.entities.npcs.size(); i++) {
            if (isBurning(g.entities.npcs.get(i))) {
                n++;
            }
        }
        return n;
    }

    /**
     * Advances every living body's fire by one fast step. A non-finite or
     * non-positive {@code dt} is ignored and a longer one advances a single
     * {@link SimulationScheduler#FAST_DT}, so a stall never burns a burst.
     */
    @Override
    public void fastTick(Game g, float dt) {
        if (!(dt > 0) || !Float.isFinite(dt)) {
            return;
        }
        float step = Math.min(dt, SimulationScheduler.FAST_DT);
        if (g.player != null) {
            tick(g, g.player, step);
        }
        for (int i = 0; i < g.entities.creatures.size(); i++) {
            tick(g, g.entities.creatures.get(i), step);
        }
        for (int i = 0; i < g.entities.npcs.size(); i++) {
            tick(g, g.entities.npcs.get(i), step);
        }
    }

    private void tick(Game g, Entity e, float dt) {
        BodyCombustion b = e.combustion;
        if (e.dead) {
            // A corpse is not a living body: its fire is kept as it was for
            // whatever shows the death, but never advanced or fed.
            b.pending = null;
            b.contact = false;
            return;
        }
        if (!canBurn(e)) {
            b.clear();
            return;
        }
        sampleFlames(g, e, b);
        if (!b.burning && b.heat == 0f && b.pending == null) {
            return;
        }

        int cx = (int) Math.floor(e.pos.x);
        int cz = (int) Math.floor(e.pos.z);
        boolean loaded = columnLoaded(e, cx, cz);
        float wet = loaded ? immersion(e, cx, cz) : 0f;
        if (wet >= IMMERSION_FRACTION) {
            if (b.burning) {
                totalDoused++;
                announce(g, e, "The water puts the flames out.");
            }
            b.extinguish();
            return;
        }

        CombustionSource kind = b.pending;
        b.contact = kind != null;
        if (kind != null) {
            boolean byPlayer = b.pendingByPlayer;
            int sourceId = b.pendingSourceId;
            b.touchedAt(b.pendingX, b.pendingY, b.pendingZ);
            if (b.burning) {
                b.refresh(kind, b.pendingIntensity, byPlayer, sourceId);
            } else {
                b.heat = kind.ignitesOnContact() ? 1f
                        : Math.min(1f, b.heat + kind.heatGainPerSecond * b.pendingIntensity * dt);
                if (b.heat + TIMER_EPSILON >= 1f) {
                    b.ignite(kind, b.pendingIntensity, byPlayer, sourceId);
                    totalIgnitions++;
                    announce(g, e, "You're on fire! Deep water or open rain will put it out.");
                }
            }
            b.pending = null;
            if (b.burning && byPlayer && e instanceof Npc n) {
                reportAttack(g, n, b, sourceId);
            }
        } else if (!b.burning) {
            b.heat = Math.max(0f, b.heat - HEAT_DECAY_PER_SECOND * dt);
        }
        if (!b.burning) {
            return;
        }

        if (loaded && g.fire.isPrecipitationReaching(g, cx, headCell(e), cz)) {
            b.soak += dt;
            if (b.soak + TIMER_EPSILON >= RAIN_EXTINGUISH_SECONDS) {
                totalRainedOut++;
                announce(g, e, "The rain puts the flames out.");
                b.extinguish();
                return;
            }
        } else {
            b.soak = Math.max(0f, b.soak - dt);
        }

        float intensity = b.intensity();
        burn(e, b, (b.contact ? contactDps(e) : afterburnDps(e)) * intensity * dt, intensity);
        if (e.dead) {
            return;
        }

        float before = b.burnSeconds;
        b.burnSeconds = Math.min(MAX_BURN_SECONDS, before + dt);
        if (e instanceof Player p && before + TIMER_EPSILON < BURN_INJURY_AFTER_SECONDS
                && b.burnSeconds + TIMER_EPSILON >= BURN_INJURY_AFTER_SECONDS) {
            p.inflictBurnInjury(g);
        }
        b.scorch = Math.min(1f, b.scorch + intensity * dt / SCORCH_SECONDS);

        if (!b.contact) {
            b.fuel -= dt * (wet > 0f ? SHALLOW_WATER_DRAIN : 1f);
            if (b.fuel <= TIMER_EPSILON) {
                totalBurnouts++;
                announce(g, e, "The flames on you burn out.");
                b.extinguish();
            }
        }
    }

    /**
     * Asks every flame in the world whether it touched the body on its way
     * here since the last tick; each one that did offers a contact.
     */
    private void sampleFlames(Game g, Entity e, BodyCombustion b) {
        sweep.set(e, b.sampled, b.sampleX, b.sampleY, b.sampleZ);
        b.sampledAt(sweep.endX(), sweep.endY(), sweep.endZ());
        g.liquidFire.exposeContacts(g, e, sweep);
        g.fire.exposeContacts(g, e, sweep);
    }

    /**
     * The player's flame has just lit or taken over a person's fire: the
     * first time for this bottle, the person looks to where the flame
     * touched them if the player can be perceived, and a settlement counts
     * the attack (its reputation cost does not wait on perception).
     */
    private static void reportAttack(Game g, Npc n, BodyCombustion b, int bottle) {
        if (!b.firstReportOf(bottle)) {
            return;
        }
        if (g.player == null || g.player.isPerceivableByAi()) {
            n.lastKnown.set(b.exposureX, b.exposureY, b.exposureZ);
            n.lastKnownAge = 0f;
        }
        g.settlementManager.onNpcAttackedByPlayer(g, n);
    }

    private static void burn(Entity e, BodyCombustion b, float damage, float intensity) {
        if (e instanceof Player p) {
            p.hurt(damage, false);
            p.damageFlash = Math.max(p.damageFlash, b.contact ? 1f : AFTERBURN_FLASH * intensity);
        } else {
            e.hurt(damage, b.ownerByPlayer);
        }
    }

    /** Every creature species shares one rate: no species is left out or singled out. */
    private static float contactDps(Entity e) {
        return e instanceof Player ? CONTACT_DPS_PLAYER
                : e instanceof Npc ? CONTACT_DPS_NPC : CONTACT_DPS_CREATURE;
    }

    private static float afterburnDps(Entity e) {
        return e instanceof Player ? AFTERBURN_DPS_PLAYER
                : e instanceof Npc ? AFTERBURN_DPS_NPC : AFTERBURN_DPS_CREATURE;
    }

    private static boolean acceptsFlame(Entity e, CombustionSource kind, float intensity,
                                        float x, float y, float z) {
        return canBurn(e) && kind != null && intensity > 0f && Float.isFinite(intensity)
                && Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z);
    }

    private static boolean torsoUnderWater(Entity e) {
        int cx = (int) Math.floor(e.pos.x);
        int cz = (int) Math.floor(e.pos.z);
        return columnLoaded(e, cx, cz) && immersion(e, cx, cz) >= IMMERSION_FRACTION;
    }

    /**
     * Share of the body's height inside water cells of the column under its
     * centre, 0..1: at most three lookups for the tallest body. Only a loaded
     * column says anything, since an unloaded one reads as air.
     */
    static float immersion(Entity e, int x, int z) {
        float bottom = e.pos.y;
        float top = e.pos.y + e.height;
        float wet = 0f;
        for (int y = (int) Math.floor(bottom); y < top; y++) {
            if (e.world.getBlock(x, y, z) == BlockType.WATER) {
                wet += Math.min(top, y + 1f) - Math.max(bottom, y);
            }
        }
        return e.height > 0f ? wet / e.height : 0f;
    }

    /** The cell the top of the head is in, where rain reaches a body. */
    private static int headCell(Entity e) {
        return (int) Math.floor(e.pos.y + e.height - 0.01f);
    }

    private static boolean columnLoaded(Entity e, int x, int z) {
        return e.world.getChunk(Math.floorDiv(x, Chunk.SX), Math.floorDiv(z, Chunk.SZ)) != null;
    }

    private static void announce(Game g, Entity e, String message) {
        if (e instanceof Player) {
            g.log(message);
        }
    }
}
