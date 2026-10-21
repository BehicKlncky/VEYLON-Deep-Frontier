package com.veylon.combat;

import com.veylon.Game;
import com.veylon.entity.Creature;
import com.veylon.entity.Entity;

import java.util.ArrayDeque;

/**
 * World-positioned perception events (gunshots, explosions, alarms, mining…).
 * Human AI hears these instead of reading the player's position telepathically;
 * wildlife gets a one-shot reaction on emit. Bounded and short-lived.
 */
public class WorldNoise {
    public static final int MAX_HEARING_SAMPLES = 4096;
    public static final int MAX_HEARING_QUERIES = 16;
    private int hearingSamples, hearingQueries;
    private long sequence;
    public void beginTick() { hearingSamples = hearingQueries = 0; }
    public int hearingSamples() { return hearingSamples; }
    public int hearingQueries() { return hearingQueries; }

    public static final class NoiseEvent {
        public long sequence;
        public final float x, y, z;
        /** Audible radius in blocks. */
        public final float radius;
        /** 0..1 alarm weight (how threatening the sound is). */
        public final float intensity;
        public final String category;
        public final boolean playerSource;
        public final Entity source;
        public float life;

        NoiseEvent(float x, float y, float z, float radius, float intensity,
                   String category, boolean playerSource, Entity source, float life) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.intensity = intensity;
            this.category = category;
            this.playerSource = playerSource;
            this.source = source;
            this.life = life;
        }

        public double distSq(float px, float py, float pz) {
            double dx = x - px, dy = y - py, dz = z - pz;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    /** Hard ceiling for short-lived perception events. */
    public static final int MAX_EVENTS = 64;
    public static final float EVENT_LIFE = 6f;

    private final ArrayDeque<NoiseEvent> events = new ArrayDeque<>();

    public void reset() {
        events.clear();
        // Keep event ordering monotonic even if live listeners survive a queue reset.
        beginTick();
    }

    /** Drops queued player-attributed events once the player stops being perceivable. */
    public void forgetPlayerSources() {
        events.removeIf(event -> event.playerSource);
    }

    /**
     * Emits a positioned sound; wildlife reacts immediately (one-shot).
     *
     * <p>This is the one gate for player-attributed perception events (R11):
     * while the player is not perceivable nothing is queued and no wildlife
     * reacts. Callers keep their physical effects -- audio, particles, damage
     * and reputation never pass through here.
     *
     * @return true when the event was queued
     */
    public boolean emit(Game g, float x, float y, float z, float radius, float intensity,
                        String category, boolean playerSource, Entity source) {
        if (playerSource && g != null && g.player != null && !g.player.isPerceivableByAi()) {
            return false;
        }
        NoiseEvent e = new NoiseEvent(x, y, z, radius, intensity, category,
                playerSource, source, EVENT_LIFE);
        e.sequence = ++sequence;
        events.addLast(e);
        while (events.size() > MAX_EVENTS) {
            events.removeFirst();
        }
        // Loud noises push wildlife: prey bolts, predators investigate.
        if (radius >= 40 && g != null) {
            for (Creature c : g.entities.creatures) {
                // A territorial organism answers only what reaches its own ground (VeilmawAI).
                if (c.dead || c.type.territorial() || c.distSqTo(x, y, z) > radius * radius) {
                    continue;
                }
                if (c.type.predator) {
                    c.state = Creature.CreatureState.TRACK;
                    c.target.set(x, c.pos.y, z);
                    c.hasTarget = true;
                    c.decideTimer = 4f;
                } else {
                    c.fear = 1f;
                    c.state = Creature.CreatureState.FLEE;
                    c.target.set(c.pos.x + (c.pos.x - x), c.pos.y, c.pos.z + (c.pos.z - z));
                    c.hasTarget = true;
                    c.decideTimer = 3f;
                }
            }
        }
        return true;
    }

    public void update(float dt) {
        for (var it = events.iterator(); it.hasNext(); ) {
            NoiseEvent e = it.next();
            e.life -= dt;
            if (e.life <= 0) {
                it.remove();
            }
        }
    }

    /** The most recent event audible from a listener position, or null. */
    public NoiseEvent loudestAudible(float x, float y, float z, float hearingBonus) {
        NoiseEvent best = null;
        double bestScore = 0;
        for (NoiseEvent e : events) {
            double d2 = e.distSq(x, y, z);
            double r = e.radius + hearingBonus;
            if (d2 > r * r) {
                continue;
            }
            double score = e.intensity * (1.0 - Math.sqrt(d2) / r) + e.life * 0.01;
            if (best == null || score > bestScore) {
                best = e;
                bestScore = score;
            }
        }
        return best;
    }

    public int count() {
        return events.size();
    }

    /**
     * Indoor hearing uses a bounded direct ray, with distance penalties per solid
     * cell: closed door 6, glass 4, other partition/slab 8 blocks. Open doors add
     * no loss; locks have the same acoustic policy as an otherwise closed door.
     * Unloaded columns and exhausted work are inaudible. This deliberately does
     * not simulate sound propagating around a whole building.
     */
    public NoiseEvent loudestAudible(com.veylon.world.World world, float x, float y, float z,
                                    float bonus, NoiseEvent after, Entity listener) {
        if (hearingQueries >= MAX_HEARING_QUERIES) { return null; }
        hearingQueries++;
        NoiseEvent best = null;
        double bestScore = -1;
        for (NoiseEvent e : events) {
            if (e.source == listener || after != null && e.sequence <= after.sequence
                    || !e.playerSource && e.intensity < .6f) { continue; }
            double distance = Math.sqrt(e.distSq(x, y, z));
            double radius = e.radius + bonus;
            if (distance > radius || distance > 128 || radius <= 0) { continue; }
            int steps = Math.max(1, (int) Math.ceil(distance * 2));
            int lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE, lastZ = Integer.MIN_VALUE;
            float loss = 0;
            for (int i = 0; i <= steps; i++) {
                if (hearingSamples >= MAX_HEARING_SAMPLES) { loss = Float.POSITIVE_INFINITY; break; }
                hearingSamples++;
                float f = i / (float) steps;
                int bx = (int) Math.floor(x + (e.x - x) * f);
                int by = (int) Math.floor(y + 1.4f + (e.y + .1f - y - 1.4f) * f);
                int bz = (int) Math.floor(z + (e.z - z) * f);
                if (!world.isColumnLoaded(bx, bz)) { loss = Float.POSITIVE_INFINITY; break; }
                if (bx == lastX && by == lastY && bz == lastZ) { continue; }
                lastX = bx; lastY = by; lastZ = bz;
                var block = world.getBlock(bx, by, bz);
                if (com.veylon.world.Fixtures.isClosedDoor(block)) { loss += 6; }
                else if (block == com.veylon.world.BlockType.WINDOW) { loss += 4; }
                else if (block.solid && !block.isClimbable()) { loss += 8; }
            }
            if (distance + loss > radius) { continue; }
            double score = e.intensity * (1 - (distance + loss) / radius) + e.life * .01;
            if (score > bestScore) { best = e; bestScore = score; }
        }
        return best;
    }

    public int countCategory(String category) {
        int count = 0;
        for (NoiseEvent event : events) {
            if (event.category.equals(category)) {
                count++;
            }
        }
        return count;
    }
}
