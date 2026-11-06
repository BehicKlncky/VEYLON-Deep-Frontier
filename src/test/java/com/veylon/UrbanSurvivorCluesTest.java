package com.veylon;

import com.veylon.engine.AudioManager;
import com.veylon.engine.SurvivorSounds;
import com.veylon.entity.GameMode;
import com.veylon.settlement.UrbanSite;
import com.veylon.settlement.UrbanSiteDirector;
import com.veylon.util.Vec3i;
import com.veylon.world.World;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanPlaces.Place;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static com.veylon.SurvivorWorldArena.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * How survivor sites are found in play (urban milestone 16), in both modes: people hiding in a
 * refuge are heard through its wall by a player standing near, never from across the city, never
 * while fear keeps them silent, and hearing them finds nothing by itself; a camp's held storeys
 * name themselves underfoot, and stepping in makes the camp known at once.
 */
class UrbanSurvivorCluesTest {
    private static final String HEARD = "Somewhere behind the wall, someone is trying very hard to be quiet.";

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"SURVIVAL", "CREATIVE"})
    void peopleHidingInARefugeCanBeHeardNearItsPanelButHearingFindsNothing(GameMode mode) {
        Ears ears = new Ears();
        Game g = session(SEED, mode, World.GEN_URBAN_SURVIVORS, ears);
        ears.game = g;
        SurvivorSites.Site refuge = nearest(g, SurvivorSites.Kind.REFUGE), camp = nearest(g, SurvivorSites.Kind.CAMP);

        standAt(g, hall(g, refuge));
        slowTick(g);
        UrbanSite r = site(g, refuge);
        assertFalse(live(r).isEmpty(), "the people are there, behind the wall");
        listen(g, 3 * UrbanSiteDirector.NOISE_PERIOD);
        assertTrue(ears.heard.size() >= 2, "small sounds come every few seconds: " + ears.heard.size());
        for (Heard h : ears.heard) {
            assertTrue(refuge.holds(h.at.x(), h.at.y(), h.at.z()), "each sound comes from inside the sealed room: " + h.at);
            assertTrue(h.distance <= UrbanSiteDirector.NOISE_RADIUS, "and only a short way off: " + h.distance);
        }
        assertEquals(SurvivorSounds.Cue.COUGH, ears.heard.getFirst().cue);
        assertNotEquals(ears.heard.get(0).cue, ears.heard.get(1).cue, "not the same sound over and over");
        assertEquals(1, logged(g, HEARD), "the log says it once, for a player without sound");
        assertFalse(r.discovered, "hearing them is a clue, not a discovery");
        assertFalse(g.world.urban.discovery.discovered(refuge.room()));

        // Fear keeps them silent.
        r.fear = 100f;
        ears.heard.clear();
        listen(g, 2 * UrbanSiteDirector.NOISE_PERIOD);
        assertTrue(ears.heard.isEmpty(), "the frightened hold their breath");
        r.fear = 0f;

        // From across the city, nothing.
        standAt(g, camp.entry());
        slowTick(g);
        ears.heard.clear();
        listen(g, 2 * UrbanSiteDirector.NOISE_PERIOD);
        assertTrue(ears.heard.isEmpty(), "a refuge is not heard from a camp's door");

        // Found and entered, nobody hides from the one standing with them.
        standAt(g, hall(g, refuge));
        slowTick(g);
        pressF(g, panel(g, refuge));
        assertTrue(r.discovered);
        standAt(g, refuge.entry());
        ears.heard.clear();
        listen(g, 2 * UrbanSiteDirector.NOISE_PERIOD);
        assertTrue(ears.heard.isEmpty(), "inside, they are met, not heard");
        assertEquals(1, logged(g, HEARD), "and the clue line is never repeated");
    }

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"SURVIVAL", "CREATIVE"})
    void steppingIntoACampNamesItAndMakesItKnownWhereEarlierCitiesHaveNone(GameMode mode) {
        Game g = session(SEED, mode);
        SurvivorSites.Site camp = nearest(g, SurvivorSites.Kind.CAMP), refuge = nearest(g, SurvivorSites.Kind.REFUGE);
        standAt(g, hall(g, refuge));
        frames(g, 4);
        assertNotSame(Place.CAMP, g.player.place, "a cellar is no camp");

        standAt(g, camp.entry());
        frames(g, 4);
        assertSame(Place.CAMP, g.player.place);
        assertTrue(g.player.locationLine().endsWith(" / Survivor camp"), g.player.locationLine());
        UrbanSite s = site(g, camp);
        assertNotNull(s, "stepping in registers the camp before any slow tick");
        assertTrue(s.discovered, "and makes it known");
        assertEquals(1, g.eventLog.recent(200).stream().filter(l -> l.contains("Survivors hold this building")).count());

        Game earlier = session(SEED, mode, World.GEN_URBAN_ECOLOGY, new AudioManager());
        standAt(earlier, camp.entry());
        frames(earlier, 4);
        assertNotSame(Place.CAMP, earlier.player.place, "generator 14 keeps the same building as it was");
        assertNull(UrbanSiteDirector.registered(earlier.world, camp.id()), "and registers nobody there");
    }

    /** Medium ticks for a while: the clock, ambience and the listening director, nothing else. */
    private static void listen(Game g, float seconds) {
        for (float t = 0; t < seconds; t += .5f) {
            g.settlementManager.urbanSites.listen(g, .5f);
        }
    }

    /** A few production frames: the player's own tick tracks the place underfoot. */
    private static void frames(Game g, int n) {
        for (int i = 0; i < n; i++) {
            g.advanceWorld(DT);
        }
    }

    private static long logged(Game g, String line) {
        return g.eventLog.recent(200).stream().filter(l -> l.endsWith(line)).count();
    }

    /** A sound: its cue, the feet cell of whoever made it, and how far the player's feet were. */
    private record Heard(SurvivorSounds.Cue cue, Vec3i at, double distance) { }

    /** Hears what the game asks the audio device to play, and where the player stood. */
    private static final class Ears extends AudioManager {
        final List<Heard> heard = new ArrayList<>();
        Game game;

        @Override
        public void playSurvivor(SurvivorSounds.Cue cue, float x, float y, float z) {
            float feet = y - 1.2f;
            double dx = x - game.player.pos.x, dy = feet - game.player.pos.y, dz = z - game.player.pos.z;
            heard.add(new Heard(cue, new Vec3i((int) Math.floor(x), (int) Math.floor(feet + .01f), (int) Math.floor(z)),
                    Math.sqrt(dx * dx + dy * dy + dz * dz)));
        }
    }
}
