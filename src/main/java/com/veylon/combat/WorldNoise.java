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

    public static final class NoiseEvent {
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
    }

    /** Emits a positioned sound; wildlife reacts immediately (one-shot). */
    public void emit(Game g, float x, float y, float z, float radius, float intensity,
                     String category, boolean playerSource, Entity source) {
        NoiseEvent e = new NoiseEvent(x, y, z, radius, intensity, category,
                playerSource, source, EVENT_LIFE);
        events.addLast(e);
        while (events.size() > MAX_EVENTS) {
            events.removeFirst();
        }
        // Loud noises push wildlife: prey bolts, predators investigate.
        if (radius >= 40 && g != null) {
            for (Creature c : g.entities.creatures) {
                if (c.dead || c.distSqTo(x, y, z) > radius * radius) {
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
