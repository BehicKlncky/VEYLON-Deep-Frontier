package com.veylon;

import com.veylon.engine.AudioEnvironment.Zone;
import com.veylon.entity.GameMode;
import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.util.Vec3i;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.TransitNetwork;
import com.veylon.world.urban.TransitStation;
import com.veylon.world.urban.UrbanDistrict;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What a listener hears at the places of the milestone-22 tour, on the design city: each space
 * rings as what it is, the night's insects keep to the overgrown housing outdoors, and rain is
 * loudest in the street, softer indoors and a trace in the cellar.
 */
class UrbanAcousticsTest {
    private static final long SEED = 20260731L;
    private static Game game;
    private static UrbanAtmosphereTourPlan plan;

    @BeforeAll
    static void city() {
        game = new Game();
        game.newWorld(SEED, true, World.GEN_URBAN_MISSIONS, GameMode.CREATIVE);
        game.appState = Game.AppState.PLAYING;
        plan = UrbanAtmosphereTourPlan.of(game.world);
    }

    @Test
    void eachPlaceRingsAsTheSpaceItIs() {
        assertEquals(Zone.OPEN, listen(street(UrbanDistrict.RESIDENTIAL), 12, Weather.CLEAR));
        assertEquals(1f, game.ambience.lastReach);
        assertEquals(Zone.ROOM, listen(plan.apartment().get("kitchen"), 12, Weather.CLEAR));
        assertEquals(Zone.ROOM, listen(plan.apartment().get("upper-floor"), 12, Weather.CLEAR));
        assertEquals(Zone.BASEMENT, listen(plan.apartment().get("basement"), 12, Weather.CLEAR));
        assertTrue(game.ambience.lastMix[3] > 0f, "a cellar has its low room tone");
        assertEquals(Zone.BASEMENT, listen(plan.cellar(), 12, Weather.CLEAR));

        TransitStation station = game.world.urban.segment(true, 0, 0).station();
        Vec3i platform = station.plan().floors().getFirst().landing();
        assertEquals(Zone.HALL, listen(new UrbanAtmosphereTourPlan.Stop(platform, platform, 1f), 12, Weather.CLEAR),
                "a station concourse is a hall");
        Vec3i track = new Vec3i(-2, TransitNetwork.FEET_Y, station.low() - 3);
        assertEquals(Zone.TUNNEL, listen(new UrbanAtmosphereTourPlan.Stop(track, track, 1f), 12, Weather.CLEAR));

        Vec3i hall = largeRoom();
        assertEquals(Zone.HALL, listen(new UrbanAtmosphereTourPlan.Stop(hall, hall, 1f), 12, Weather.CLEAR),
                "a ground-floor room of at least " + AudioSceneState.HALL_AREA + " cells rings like a hall");
    }

    @Test
    void weatherReachesAListenerAsTheirShelterLetsIt() {
        float open = AudioSceneState.weatherReach(Zone.OPEN, 1f), porch = AudioSceneState.weatherReach(Zone.SHELTER, .5f);
        float window = AudioSceneState.weatherReach(Zone.ROOM, .7f), deep = AudioSceneState.weatherReach(Zone.ROOM, .12f);
        float cellar = AudioSceneState.weatherReach(Zone.BASEMENT, .075f), tunnel = AudioSceneState.weatherReach(Zone.TUNNEL, .04f);
        assertTrue(open > porch && porch > window && window > deep && deep > cellar && cellar > tunnel && tunnel > 0,
                open + " " + porch + " " + window + " " + deep + " " + cellar + " " + tunnel);
        assertEquals(deep, AudioSceneState.weatherReach(Zone.HALL, .12f), "a hall is a room to the weather");
    }

    @Test
    void theNightsInsectsKeepToTheOvergrownHousingOutdoors() {
        listen(street(UrbanDistrict.RESIDENTIAL), 23, Weather.CLEAR);
        float outdoors = game.ambience.lastMix[4];
        assertTrue(outdoors > 0.5f, "insects in the housing's courtyards: " + outdoors);
        listen(plan.apartment().get("kitchen"), 23, Weather.CLEAR);
        float kitchen = game.ambience.lastMix[4];
        assertTrue(kitchen > 0 && kitchen < outdoors * 0.5f, "faint through the kitchen's window: " + kitchen);
        listen(plan.cellar(), 23, Weather.CLEAR);
        assertEquals(0f, game.ambience.lastMix[4], "none in the hidden cellar");
        listen(street(UrbanDistrict.COMMERCIAL), 23, Weather.CLEAR);
        assertEquals(0f, game.ambience.lastMix[4], "none on the downtown asphalt");
    }

    @Test
    void rainIsLoudestInTheStreetSofterIndoorsAndATraceInTheCellar() {
        listen(street(UrbanDistrict.RESIDENTIAL), 14, Weather.RAIN);
        float street = game.ambience.lastMix[0];
        assertTrue(street > 0.3f, "rain in the street: " + street);
        listen(plan.apartment().get("upper-floor"), 14, Weather.RAIN);
        float upstairs = game.ambience.lastMix[0];
        assertTrue(upstairs > 0 && upstairs < street * 0.6f, "softer behind the walls: " + upstairs);
        listen(plan.apartment().get("basement"), 14, Weather.RAIN);
        float cellar = game.ambience.lastMix[0];
        assertTrue(cellar > 0 && cellar < street * 0.1f, "a trace below the street: " + cellar);
    }

    private static UrbanAtmosphereTourPlan.Stop street(UrbanDistrict d) {
        return plan.streets().get(d);
    }

    /** Stands at a stop at an hour and in a weather for a second and a half of play; the zone the mix chose. */
    private static Zone listen(UrbanAtmosphereTourPlan.Stop stop, int hour, Weather weather) {
        long day = (long) (game.time.totalMinutes / (24 * 60));
        game.time.totalMinutes = day * 24 * 60 + hour * 60;
        game.weather.current = game.weather.next = weather;
        game.weather.blend = 1f;
        game.weather.changeTimer = 4000f;
        Vec3i at = stop.stand();
        game.world.ensureChunks(at.x(), at.z(), 3, 10_000);
        game.player.pos.set(at.x() + .5f, at.y() + .001f, at.z() + .5f);
        game.player.vel.zero();
        for (int i = 0; i < 30; i++) {
            game.player.pos.set(at.x() + .5f, at.y() + .001f, at.z() + .5f);
            game.playStep(0.05f, 0.05f);
            game.input.endFrame();
        }
        return game.ambience.lastZone;
    }

    /** The anchor of the first ground-floor room of at least the hall area near the origin. */
    private static Vec3i largeRoom() {
        for (int rz = -1; rz <= 1; rz++) {
            for (int rx = -1; rx <= 1; rx++) {
                for (var parcel : game.world.urban.regionPlan(rx, rz).parcels()) {
                    BuildingPlan b = game.world.urban.buildingAt(parcel.x0(), parcel.z0());
                    if (b == null) { continue; }
                    for (BuildingPlan.Floor floor : b.floors()) {
                        if (floor.index() != 0) { continue; }
                        for (BuildingPlan.Room room : floor.rooms()) {
                            var r = room.interior();
                            int area = (Math.abs(r.x1() - r.x0()) + 1) * (Math.abs(r.z1() - r.z0()) + 1);
                            if (area >= AudioSceneState.HALL_AREA) { return room.anchor(); }
                        }
                    }
                }
            }
        }
        throw new IllegalStateException("no ground-floor hall near the origin");
    }
}
