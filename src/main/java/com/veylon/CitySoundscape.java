package com.veylon;

import com.veylon.engine.AudioEnvironment;
import com.veylon.engine.CitySounds.Cue;
import com.veylon.entity.Creature;
import com.veylon.entity.Npc;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.urban.UrbanDistrict;

import java.util.Arrays;
import java.util.Random;

/**
 * The generated city's own sounds around the survivor (urban milestone 22). Far off, by district,
 * the city creaks and lets go: sheet metal grinding, shutters slapping, glass and masonry falling,
 * flood water slopping against walls. Indoors, floors settle and pipes knock. Closer, it says what
 * lives here, where it is: the wet breath of a Veilbound that has not noticed anyone, the throb of a
 * Veilmaw folded in its growth, the bark of a raider or guard who has just turned on the survivor,
 * the hum of a fed work lamp.
 *
 * <p>Presentation only. Distant and indoor sounds come from this class's own generator, seeded per
 * world with its own salt; the tells read loaded entity state and never their generators. Nothing
 * here emits {@code WorldNoise}, so no sound it plays can draw a threat or change a roll; the noise
 * threats hear comes only from what the survivor and the world actually do. Each pass examines at
 * most {@value #SCAN_LIMIT} people and {@value #SCAN_LIMIT} creatures, follows at most
 * {@value #TRACKED} of each for their timers and plays at most {@value #MAX_PER_PASS} sounds.
 */
final class CitySoundscape {
    static final int SCAN_LIMIT = 64, TRACKED = 8, MAX_PER_PASS = 2;
    /** Salt of this class's presentation generator, beside the ambience emitters' own. */
    static final long SALT = 0x43495459534341L;
    /** Blocks within which a resting Veilbound's breath, and a folded Veilmaw's throb, are played. */
    static final float RASP_RADIUS = 22f, PULSE_RADIUS = 24f;
    /** Blocks within which a person turning hostile barks, and a fed lamp hums. */
    static final float SHOUT_RADIUS = 40f, HUM_RADIUS = 6f;
    /** Distant sounds stand this far from the listener, in blocks. */
    static final float DISTANT_MIN = 26f, DISTANT_MAX = 46f;

    private final Random rng = new Random();
    /** How many times each cue was played this world, for QA reports and tests. */
    final int[] played = new int[Cue.values().length];
    private float distantWait, indoorWait, humWait;
    private int budget;

    // Bounded per-entity timers: who is being followed, and how long until their next sound.
    private final Object[] breathing = new Object[TRACKED];
    private final float[] breathWait = new float[TRACKED];
    private final Object[] hostile = new Object[TRACKED];
    private final float[] hostileHold = new float[TRACKED];
    private final boolean[] seen = new boolean[TRACKED];

    /** Forgets the outgoing world's timers and followed entities and reseeds from the new world. */
    void reseed(long seed) {
        rng.setSeed(seed ^ SALT);
        Arrays.fill(played, 0);
        Arrays.fill(breathing, null);
        Arrays.fill(hostile, null);
        Arrays.fill(breathWait, 0);
        Arrays.fill(hostileHold, 0);
        distantWait = 4f + rng.nextFloat() * 4f;
        indoorWait = 6f + rng.nextFloat() * 6f;
        humWait = 0;
    }

    /** One pass, {@code dt} seconds after the last, for a listener in {@code zone}; inert outside a generated city. */
    void update(Game game, float dt, AudioEnvironment.Zone zone, float wind) {
        if (game.world.urban == null || !game.world.urban.hasGeneratedBuildings() || game.player.dead) { return; }
        budget = MAX_PER_PASS;
        float x = game.player.pos.x, y = game.player.pos.y, z = game.player.pos.z;
        presence(game, dt, x, y, z);
        hostility(game, dt, x, y, z);
        hum(game, dt, x, y, z);
        distant(game, dt, zone, wind, x, z);
        indoor(game, dt, zone, x, y, z);
    }

    /** Whether a cue may still be played this pass; counts it when it may. */
    private boolean play(Game game, Cue cue, float px, float py, float pz, float gain) {
        if (budget <= 0) { return false; }
        budget--;
        played[cue.ordinal()]++;
        game.audio.playCity(cue, px, py, pz, gain);
        return true;
    }

    // ------------------------------------------------------------------
    // What lives here
    // ------------------------------------------------------------------

    /** A resting Veilbound breathes every five to ten seconds; a folded Veilmaw throbs every four to seven. */
    private void presence(Game game, float dt, float x, float y, float z) {
        Arrays.fill(seen, false);
        var npcs = game.entities.npcs;
        for (int i = 0, n = Math.min(SCAN_LIMIT, npcs.size()); i < n; i++) {
            Npc npc = npcs.get(i);
            if (!npc.infected() || npc.dead || npc.abstractTravel || !resting(game, npc)
                    || npc.distSqTo(x, y, z) > RASP_RADIUS * RASP_RADIUS) { continue; }
            tick(game, dt, npc, Cue.INFECTED_RASP, npc.pos.x, npc.pos.y + 1.5f, npc.pos.z, 5f, 5f);
        }
        var creatures = game.entities.creatures;
        for (int i = 0, n = Math.min(SCAN_LIMIT, creatures.size()); i < n; i++) {
            Creature c = creatures.get(i);
            if (c.type != Creature.CreatureType.VEILMAW || c.dead || c.state != Creature.CreatureState.FOLDED
                    || c.distSqTo(x, y, z) > PULSE_RADIUS * PULSE_RADIUS) { continue; }
            tick(game, dt, c, Cue.VEIL_PULSE, c.pos.x, c.pos.y + .4f, c.pos.z, 4f, 3f);
        }
        for (int s = 0; s < TRACKED; s++) {
            if (!seen[s]) { breathing[s] = null; }
        }
    }

    /** An infected that has not noticed the survivor: not hunting, winding up, searching or breaching. */
    private static boolean resting(Game game, Npc n) {
        return n.combatTarget != game.player && n.strikeWindup < 0 && n.searchTimer <= 0 && n.breachDoor == null;
    }

    /** Advances one followed entity's timer and plays its sound when it runs out; a first sighting waits a little. */
    private void tick(Game game, float dt, Object who, Cue cue, float px, float py, float pz, float min, float spread) {
        int slot = slot(breathing, who);
        if (slot < 0) {
            slot = slot(breathing, null);
            if (slot < 0) { return; } // already following as many as allowed
            breathing[slot] = who;
            breathWait[slot] = 0.5f + rng.nextFloat() * min * 0.5f;
        }
        seen[slot] = true;
        breathWait[slot] -= dt;
        if (breathWait[slot] <= 0 && play(game, cue, px, py, pz, 0.85f)) {
            breathWait[slot] = min + rng.nextFloat() * spread;
        }
    }

    /** A person turning on the survivor barks once, where they stand, before they come. */
    private void hostility(Game game, float dt, float x, float y, float z) {
        for (int s = 0; s < TRACKED; s++) {
            if (hostile[s] != null && (hostileHold[s] -= dt) <= 0) { hostile[s] = null; }
        }
        var npcs = game.entities.npcs;
        for (int i = 0, n = Math.min(SCAN_LIMIT, npcs.size()); i < n; i++) {
            Npc npc = npcs.get(i);
            if (npc.infected() || npc.dead || npc.abstractTravel || npc.combatTarget != game.player
                    || npc.distSqTo(x, y, z) > SHOUT_RADIUS * SHOUT_RADIUS) { continue; }
            int slot = slot(hostile, npc);
            if (slot >= 0) {
                hostileHold[slot] = 12f; // still after the survivor: no second bark
                continue;
            }
            slot = slot(hostile, null);
            if (slot < 0) { continue; }
            if (play(game, Cue.SHOUT, npc.pos.x, npc.pos.y + 1.6f, npc.pos.z, 1f)) {
                hostile[slot] = npc;
                hostileHold[slot] = 12f;
            }
        }
    }

    /** The nearest fed work lamp within {@value #HUM_RADIUS} blocks hums, one second at a time. */
    private void hum(Game game, float dt, float x, float y, float z) {
        if ((humWait -= dt) > 0) { return; }
        int ccx = Math.floorDiv((int) Math.floor(x), Chunk.SX), ccz = Math.floorDiv((int) Math.floor(z), Chunk.SZ);
        float best = HUM_RADIUS * HUM_RADIUS;
        int[] lamp = null;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Chunk c = game.world.getChunk(ccx + dx, ccz + dz);
                if (c == null) { continue; }
                for (int i = 0; i < c.lights.size(); i++) {
                    int[] l = c.lights.get(i);
                    float ex = l[0] + .5f - x, ey = l[1] + .5f - y, ez = l[2] + .5f - z;
                    float d = ex * ex + ey * ey + ez * ez;
                    if (d < best && game.world.getBlock(l[0], l[1], l[2]) == BlockType.ELECTRIC_LAMP) {
                        best = d;
                        lamp = l;
                    }
                }
            }
        }
        if (lamp != null && play(game, Cue.LAMP_HUM, lamp[0] + .5f, lamp[1] + .5f, lamp[2] + .5f, 0.5f)) {
            humWait = Cue.LAMP_HUM.seconds;
        }
    }

    // ------------------------------------------------------------------
    // The city around the survivor
    // ------------------------------------------------------------------

    /** Far off, every six to fifteen seconds (sooner in wind), a sound of the district underfoot. */
    private void distant(Game game, float dt, AudioEnvironment.Zone zone, float wind, float x, float z) {
        if (zone == AudioEnvironment.Zone.BASEMENT || zone == AudioEnvironment.Zone.TUNNEL) { return; }
        if ((distantWait -= dt * (1f + wind)) > 0) { return; }
        Cue cue = distantCue(game.player.district, rng.nextFloat());
        double angle = rng.nextDouble() * 2 * Math.PI;
        float r = DISTANT_MIN + rng.nextFloat() * (DISTANT_MAX - DISTANT_MIN);
        float py = 41f + rng.nextFloat() * 10f;
        if (play(game, cue, x + (float) Math.cos(angle) * r, py, z + (float) Math.sin(angle) * r,
                0.55f + rng.nextFloat() * 0.25f)) {
            distantWait = 6f + rng.nextFloat() * 9f;
        }
    }

    /**
     * Which distant sound a district makes, for a uniform draw {@code u}: shutters in the housing,
     * glass downtown, metal in the yards and along the cordon, water in the flooded quarter, and a
     * little falling masonry everywhere. Cities without districts sound like housing.
     */
    static Cue distantCue(UrbanDistrict district, float u) {
        Cue[] table = switch (district == null ? UrbanDistrict.RESIDENTIAL : district) {
            case RESIDENTIAL -> RESIDENTIAL_SOUNDS;
            case COMMERCIAL -> COMMERCIAL_SOUNDS;
            case INDUSTRIAL -> INDUSTRIAL_SOUNDS;
            case FLOODED -> FLOODED_SOUNDS;
            case CIVIC -> CIVIC_SOUNDS;
            case QUARANTINE -> QUARANTINE_SOUNDS;
        };
        return table[Math.min(table.length - 1, (int) (u * table.length))];
    }

    private static final Cue[] RESIDENTIAL_SOUNDS = {Cue.SHUTTER_BANG, Cue.SHUTTER_BANG, Cue.SHUTTER_BANG,
        Cue.SHUTTER_BANG, Cue.GLASS_TINKLE, Cue.MASONRY_FALL, Cue.METAL_GROAN};
    private static final Cue[] COMMERCIAL_SOUNDS = {Cue.GLASS_TINKLE, Cue.GLASS_TINKLE, Cue.GLASS_TINKLE,
        Cue.METAL_GROAN, Cue.METAL_GROAN, Cue.SHUTTER_BANG, Cue.MASONRY_FALL};
    private static final Cue[] INDUSTRIAL_SOUNDS = {Cue.METAL_GROAN, Cue.METAL_GROAN, Cue.METAL_GROAN,
        Cue.METAL_GROAN, Cue.METAL_GROAN, Cue.MASONRY_FALL, Cue.SHUTTER_BANG};
    private static final Cue[] FLOODED_SOUNDS = {Cue.WATER_LAP, Cue.WATER_LAP, Cue.WATER_LAP, Cue.WATER_LAP,
        Cue.SHUTTER_BANG, Cue.SHUTTER_BANG, Cue.MASONRY_FALL};
    private static final Cue[] CIVIC_SOUNDS = {Cue.SHUTTER_BANG, Cue.SHUTTER_BANG, Cue.METAL_GROAN,
        Cue.METAL_GROAN, Cue.GLASS_TINKLE, Cue.GLASS_TINKLE, Cue.MASONRY_FALL};
    private static final Cue[] QUARANTINE_SOUNDS = {Cue.METAL_GROAN, Cue.METAL_GROAN, Cue.METAL_GROAN,
        Cue.MASONRY_FALL, Cue.MASONRY_FALL, Cue.GLASS_TINKLE};

    /** Indoors a floor settles or a pipe knocks every ten to twenty-two seconds; below the street, pipes. */
    private void indoor(Game game, float dt, AudioEnvironment.Zone zone, float x, float y, float z) {
        boolean room = zone == AudioEnvironment.Zone.ROOM || zone == AudioEnvironment.Zone.HALL;
        boolean below = zone == AudioEnvironment.Zone.BASEMENT || zone == AudioEnvironment.Zone.TUNNEL;
        if (!room && !below) { return; }
        if ((indoorWait -= dt) > 0) { return; }
        Cue cue = below || rng.nextFloat() < 0.3f ? Cue.PIPE_KNOCK : Cue.WOOD_CREAK;
        double angle = rng.nextDouble() * 2 * Math.PI;
        float r = 2f + rng.nextFloat() * 3f;
        float py = y + (cue == Cue.WOOD_CREAK ? 3.2f : 1.2f); // a creak in the floor above, a pipe at the ceiling
        if (play(game, cue, x + (float) Math.cos(angle) * r, py, z + (float) Math.sin(angle) * r,
                0.5f + rng.nextFloat() * 0.3f)) {
            indoorWait = (below ? 9f : 10f) + rng.nextFloat() * (below ? 9f : 12f);
        }
    }

    /** The slot holding {@code who} (by identity), or -1. */
    private static int slot(Object[] slots, Object who) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == who) { return i; }
        }
        return -1;
    }

    /** Entities followed for their timers now, for the runtime budget snapshot. */
    int followed() {
        int n = 0;
        for (int i = 0; i < TRACKED; i++) {
            if (breathing[i] != null) { n++; }
            if (hostile[i] != null) { n++; }
        }
        return n;
    }
}
