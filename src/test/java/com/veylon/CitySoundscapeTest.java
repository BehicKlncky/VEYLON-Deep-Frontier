package com.veylon;

import com.veylon.engine.AudioEnvironment.Zone;
import com.veylon.engine.CitySounds.Cue;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.entity.Player;
import com.veylon.settlement.NpcArchetype;
import com.veylon.world.World;
import com.veylon.world.urban.UrbanDistrict;
import com.veylon.world.urban.UrbanRevision;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The city's own sounds (urban milestone 22): which a district makes, the tells of what lives
 * here, their bounds, and that none of it, however its presentation generator falls, changes what
 * the simulation does.
 */
class CitySoundscapeTest {
    private static final long SEED = 20260731L;
    private static final float PASS = 0.12f;

    @Test
    void eachDistrictSoundsLikeItself() {
        assertEquals(Cue.SHUTTER_BANG, mostCommon(UrbanDistrict.RESIDENTIAL));
        assertEquals(Cue.GLASS_TINKLE, mostCommon(UrbanDistrict.COMMERCIAL));
        assertEquals(Cue.METAL_GROAN, mostCommon(UrbanDistrict.INDUSTRIAL));
        assertEquals(Cue.WATER_LAP, mostCommon(UrbanDistrict.FLOODED));
        assertEquals(Cue.METAL_GROAN, mostCommon(UrbanDistrict.QUARANTINE));
        assertEquals(CitySoundscape.distantCue(UrbanDistrict.RESIDENTIAL, .3f), CitySoundscape.distantCue(null, .3f),
                "a city without districts sounds like housing");
        for (UrbanDistrict d : UrbanDistrict.values()) {
            for (float u = 0; u < 1f; u += 0.01f) {
                Cue cue = CitySoundscape.distantCue(d, u);
                assertTrue(cue.ordinal() <= Cue.WATER_LAP.ordinal(), d + " plays only distant sounds: " + cue);
                assertTrue(d == UrbanDistrict.FLOODED || cue != Cue.WATER_LAP, "only the flooded quarter laps");
            }
        }
    }

    @Test
    void aRestingVeilboundBreathesWhereItStandsAndStopsWhenItHunts() {
        Game game = scene();
        CitySoundscape sounds = new CitySoundscape();
        sounds.reseed(SEED);
        Npc n = infected(game, 6f);
        int breaths = run(game, sounds, 12f, Cue.INFECTED_RASP);
        assertTrue(breaths >= 1 && breaths <= 3, "one breath every five to ten seconds: " + breaths);
        n.combatTarget = game.player;
        assertEquals(0, run(game, sounds, 12f, Cue.INFECTED_RASP), "a hunting Veilbound has its own cues");
        n.combatTarget = null;
        n.pos.x += 30;
        assertEquals(0, run(game, sounds, 12f, Cue.INFECTED_RASP), "beyond 22 blocks nothing is played");
    }

    @Test
    void aPersonTurningOnTheSurvivorBarksOnceWhileTheyStayHostile() {
        Game game = scene();
        CitySoundscape sounds = new CitySoundscape();
        sounds.reseed(SEED);
        Npc raider = game.entities.spawnNpc(game.world, "Raider", game.player.pos.x + 8, game.player.pos.y,
                game.player.pos.z);
        raider.archetype = NpcArchetype.GUARD;
        assertEquals(0, run(game, sounds, 2f, Cue.SHOUT));
        raider.combatTarget = game.player;
        assertEquals(1, run(game, sounds, 10f, Cue.SHOUT), "one bark as they turn, none while they come");
        raider.combatTarget = null;
        run(game, sounds, 13f, Cue.SHOUT);
        raider.combatTarget = game.player;
        assertEquals(1, run(game, sounds, 2f, Cue.SHOUT), "turning again after a while barks again");
    }

    /**
     * A stronghold's guard or a raider hunts by attacking with no infected in its sights rather than
     * by naming the survivor its target (CompoundResidentAI); it barks as it goes for them too, and
     * a defender fighting an infected does not.
     */
    @Test
    void aGuardOrRaiderGoingForTheSurvivorBarksAndOneFightingAnInfectedDoesNot() {
        Game game = scene();
        CitySoundscape sounds = new CitySoundscape();
        sounds.reseed(SEED);
        Npc guard = game.entities.spawnNpc(game.world, "Warden", game.player.pos.x + 9, game.player.pos.y,
                game.player.pos.z);
        guard.archetype = NpcArchetype.GUARD;
        Npc infected = infected(game, 30f);
        guard.state = Npc.NpcState.ATTACK;
        guard.combatTarget = infected;
        assertEquals(0, run(game, sounds, 2f, Cue.SHOUT), "fighting an infected is no bark at the survivor");
        guard.combatTarget = null;
        assertEquals(1, run(game, sounds, 6f, Cue.SHOUT), "one bark as the hunt begins");
        guard.state = Npc.NpcState.IDLE;
        assertEquals(0, run(game, sounds, 2f, Cue.SHOUT));
    }

    @Test
    void theSoundscapeStaysWithinItsBoundsAndForgetsTheOutgoingWorld() {
        Game game = scene();
        CitySoundscape sounds = new CitySoundscape();
        sounds.reseed(SEED);
        for (int i = 0; i < 20; i++) { infected(game, 3f + i * 0.5f); }
        for (int pass = 0; pass < 200; pass++) {
            int before = Arrays.stream(sounds.played).sum();
            sounds.update(game, PASS, Zone.OPEN, 1f);
            assertTrue(Arrays.stream(sounds.played).sum() - before <= CitySoundscape.MAX_PER_PASS);
            assertTrue(sounds.followed() <= 2 * CitySoundscape.TRACKED);
        }
        assertTrue(sounds.played[Cue.INFECTED_RASP.ordinal()] > 0 && sounds.followed() > 0);
        sounds.reseed(SEED + 1);
        assertEquals(0, Arrays.stream(sounds.played).sum());
        assertEquals(0, sounds.followed());
        Game wilderness = new Game();
        wilderness.newWorld(SEED, true, World.GEN_CAVE_IDENTITIES, GameMode.SURVIVAL);
        for (int pass = 0; pass < 200; pass++) { sounds.update(wilderness, PASS, Zone.OPEN, 1f); }
        assertEquals(0, Arrays.stream(sounds.played).sum(), "the wilderness has no city sounds");
    }

    /**
     * Two sessions of the same city whose soundscapes draw different presentation streams play
     * different sounds at different times, and their simulations stay identical, frame by frame:
     * no sound of the city is a noise any AI hears, and none takes a simulation roll.
     */
    @Test
    void whateverTheCityPlaysTheSimulationRunsTheSame() {
        Game a = session(), b = session();
        b.ambience.soundscape.reseed(SEED ^ 0x5eedL);
        StringBuilder noiseA = new StringBuilder(), noiseB = new StringBuilder();
        StringBuilder heardA = new StringBuilder(), heardB = new StringBuilder();
        for (int frame = 0; frame < 800; frame++) {
            step(a);
            step(b);
            if (frame % 20 == 0) {
                noiseA.append(a.noise.count()).append(',');
                noiseB.append(b.noise.count()).append(',');
                heardA.append(Arrays.stream(a.ambience.soundscape.played).sum()).append(',');
                heardB.append(Arrays.stream(b.ambience.soundscape.played).sum()).append(',');
            }
        }
        int[] playedA = a.ambience.soundscape.played;
        assertTrue(Arrays.stream(playedA).sum() >= 2, "the city made itself heard: " + Arrays.toString(playedA));
        assertNotEquals(heardA.toString(), heardB.toString(), "the two presentation streams really differ");
        assertEquals(noiseA.toString(), noiseB.toString(), "the noise the AI hears is the same in every frame");
        assertEquals(fingerprint(a), fingerprint(b));
    }

    private static Game session() {
        Game game = new Game();
        game.newWorld(SEED, true, World.GEN_URBAN_MISSIONS, GameMode.SURVIVAL);
        game.appState = Game.AppState.PLAYING;
        return game;
    }

    private static void step(Game game) {
        game.playStep(0.05f, 0.05f);
        game.world.ensureChunks((int) game.player.pos.x, (int) game.player.pos.z, 4, 2);
        game.input.endFrame();
    }

    private static String fingerprint(Game game) {
        StringBuilder s = new StringBuilder(String.format(Locale.ROOT, "player=%.3f,%.3f,%.3f hp=%.2f food=%.3f noise=%d",
                game.player.pos.x, game.player.pos.y, game.player.pos.z, game.player.health, game.player.hunger,
                game.noise.count()));
        for (Npc n : game.entities.npcs) {
            s.append(String.format(Locale.ROOT, " %s@%.3f,%.3f,%.3f/%.1f/%s", n.name, n.pos.x, n.pos.y, n.pos.z, n.health, n.state));
        }
        for (var c : game.entities.creatures) {
            s.append(String.format(Locale.ROOT, " %s@%.3f,%.3f,%.3f/%.1f/%s", c.type, c.pos.x, c.pos.y, c.pos.z, c.health, c.state));
        }
        return s.append(" changed=").append(game.world.changedBlocks.size()).toString();
    }

    /** Passes of {@code seconds} and the number of times {@code cue} was played in them. */
    private static int run(Game game, CitySoundscape sounds, float seconds, Cue cue) {
        int before = sounds.played[cue.ordinal()];
        for (float t = 0; t < seconds; t += PASS) { sounds.update(game, PASS, Zone.OPEN, 0f); }
        return sounds.played[cue.ordinal()] - before;
    }

    private static Npc infected(Game game, float east) {
        Npc n = game.entities.spawnNpc(game.world, "Veilbound", game.player.pos.x + east, game.player.pos.y,
                game.player.pos.z);
        n.archetype = NpcArchetype.VEILBOUND;
        n.health = n.maxHealth = n.archetype.maxHealth;
        return n;
    }

    private static Cue mostCommon(UrbanDistrict district) {
        Map<Cue, Integer> counts = new EnumMap<>(Cue.class);
        for (float u = 0; u < 1f; u += 0.001f) { counts.merge(CitySoundscape.distantCue(district, u), 1, Integer::sum); }
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
    }

    private static Game scene() {
        Game game = new Game();
        game.world = new World(SEED, World.GEN_URBAN_APARTMENTS);
        game.world.urban.resolveContentRevision(UrbanRevision.contentFor(game.world.generatorVersion));
        game.player = new Player(game.world);
        game.player.pos.set(8.5f, 41f, 8.5f);
        return game;
    }
}
