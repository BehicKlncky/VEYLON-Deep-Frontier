package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.simulation.TimeConstants;
import com.veylon.simulation.TimeSystem;
import com.veylon.simulation.WeatherSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.Biome;
import com.veylon.world.World;
import com.veylon.world.urban.UrbanDistrict;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.9.1 B14: every world is warmest in the afternoon and coldest in the night before dawn, and the
 * day moves every district and biome alike, so their climates keep their places relative to each
 * other (urban D-111). Until 0.9.1 the curve was inverted and 14:00 was the coldest hour everywhere.
 */
class DayTemperatureCurveTest {
    private static final long SEED = 20_260_731L;
    private static final int AFTERNOON = 14;
    private static final int BEFORE_DAWN = 2;

    @Test
    void theClockIsWarmestInTheAfternoonAndColdestInTheNightBeforeDawn() {
        TimeSystem time = new TimeSystem();
        float warmest = -Float.MAX_VALUE, coldest = Float.MAX_VALUE;
        double warmestAt = -1, coldestAt = -1;
        for (int minute = 0; minute < TimeConstants.MINUTES_PER_DAY; minute++) {
            time.totalMinutes = minute;
            float t = time.tempOffset();
            if (t > warmest) { warmest = t; warmestAt = minute / 60.0; }
            if (t < coldest) { coldest = t; coldestAt = minute / 60.0; }
        }
        assertEquals(TimeConstants.WARMEST_HOUR, warmestAt, 1e-9, "the warmest minute of the day");
        assertTrue(night(coldestAt), "the coldest minute falls in the night before dawn, not at " + coldestAt);

        float coolestAfternoon = Float.MAX_VALUE, warmestNight = -Float.MAX_VALUE;
        for (int h = 0; h < 24; h++) {
            time.totalMinutes = h * TimeConstants.MINUTES_PER_HOUR;
            float t = time.tempOffset();
            if (h >= 12 && h < TimeConstants.DUSK_START_HOUR) { coolestAfternoon = Math.min(coolestAfternoon, t); }
            if (night(h)) { warmestNight = Math.max(warmestNight, t); }
        }
        assertTrue(coolestAfternoon > warmestNight, "every afternoon hour (" + coolestAfternoon
                + " at its coolest) is warmer than every night hour (" + warmestNight + " at its warmest)");
    }

    @Test
    void everyCityDistrictIsWarmestInTheAfternoonAndARoofKeepsTheExtremesOff() {
        Game g = new Game();
        g.newWorld(SEED, true, World.DEFAULT_NEW_WORLD_GENERATOR, GameMode.SURVIVAL);
        Vec3i refuge = new Vec3i((int) Math.floor(g.player.pos.x), (int) Math.floor(g.player.pos.y + 0.01f),
                (int) Math.floor(g.player.pos.z));
        UrbanDistrictTourPlan plan = UrbanDistrictTourPlan.of(g.world);
        Map<UrbanDistrict, float[]> streets = new EnumMap<>(UrbanDistrict.class);
        for (UrbanDistrict district : UrbanDistrict.values()) {
            Vec3i at = plan.stops().get(district).stand();
            g.world.ensureChunks(at.x(), at.z(), 1, 10_000);
            assertTrue(g.world.isSkyExposed(at.x(), at.y() + 1, at.z()), district + "'s stop is under the sky");
            float[] day = day(g, at);
            assertWarmestInTheAfternoon(district.id, day);
            streets.put(district, day);
        }
        assertMoveAlike(streets);
        for (int h = 0; h < 24; h++) {
            for (UrbanDistrict district : UrbanDistrict.values()) {
                assertTrue(streets.get(UrbanDistrict.INDUSTRIAL)[h] >= streets.get(district)[h],
                        "the industrial yards stay the warmest district at " + h + ":00");
                assertTrue(streets.get(UrbanDistrict.QUARANTINE)[h] <= streets.get(district)[h],
                        "the quarantine stays the coldest district at " + h + ":00");
            }
        }

        assertEquals(UrbanDistrict.RESIDENTIAL, g.world.urban.districtAt(refuge.x(), refuge.z()));
        assertFalse(g.world.isSkyExposed(refuge.x(), refuge.y() + 1, refuge.z()), "the refuge is roofed");
        float[] room = day(g, refuge);
        float[] street = streets.get(UrbanDistrict.RESIDENTIAL);
        assertWarmestInTheAfternoon("the refuge", room);
        assertTrue(room[BEFORE_DAWN] > street[BEFORE_DAWN], "the refuge (" + room[BEFORE_DAWN]
                + ") is warmer than the street (" + street[BEFORE_DAWN] + ") before dawn");
        assertTrue(room[AFTERNOON] < street[AFTERNOON], "the refuge (" + room[AFTERNOON]
                + ") is cooler than the street (" + street[AFTERNOON] + ") in the afternoon");
    }

    @Test
    void everyWildernessBiomeIsWarmestInTheAfternoonAndKeepsItsClimate() {
        Game g = new Game();
        g.newWorld(SEED, true, World.WILDERNESS_NEW_WORLD_GENERATOR, GameMode.SURVIVAL);
        Map<Biome, float[]> sites = new EnumMap<>(Biome.class);
        int ox = (int) Math.floor(g.player.pos.x), oz = (int) Math.floor(g.player.pos.z);
        for (Biome biome : Biome.values()) {
            int[] column = nearest(g.world, ox, oz, biome);
            assertNotNull(column, "a " + biome + " column near the spawn");
            g.world.ensureChunks(column[0], column[1], 1, 10_000);
            float[] day = day(g, new Vec3i(column[0], g.world.surfaceHeight(column[0], column[1]) + 1, column[1]));
            assertWarmestInTheAfternoon(biome.displayName, day);
            sites.put(biome, day);
        }
        assertMoveAlike(sites);
        for (int h = 0; h < 24; h++) {
            for (Biome biome : Biome.values()) {
                assertTrue(sites.get(Biome.SCRUBLAND)[h] >= sites.get(biome)[h], "the scrubland stays the warmest at " + h);
                assertTrue(sites.get(Biome.COLD_RIDGE)[h] <= sites.get(biome)[h], "the cold ridge stays the coldest at " + h);
            }
        }
    }

    /** The air on the hour through one clear day of the first season, with no world event. */
    private static float[] day(Game g, Vec3i at) {
        g.weather.current = WeatherSystem.Weather.CLEAR;
        g.weather.next = WeatherSystem.Weather.CLEAR;
        g.weather.blend = 1f;
        g.events.active.clear();
        float[] air = new float[24];
        for (int h = 0; h < 24; h++) {
            g.time.totalMinutes = h * TimeConstants.MINUTES_PER_HOUR;
            air[h] = g.temperature.envTempAt(g, at.x() + 0.5f, at.y(), at.z() + 0.5f);
        }
        return air;
    }

    private static void assertWarmestInTheAfternoon(String place, float[] air) {
        int warmest = 0, coldest = 0;
        for (int h = 1; h < 24; h++) {
            if (air[h] > air[warmest]) { warmest = h; }
            if (air[h] < air[coldest]) { coldest = h; }
        }
        assertEquals(AFTERNOON, warmest, place + " is warmest at 14:00");
        assertTrue(night(coldest), place + " is coldest in the night before dawn, not at " + coldest + ":00");
    }

    /** The day moves every place under the sky by the same degrees, hour for hour. */
    private static <K> void assertMoveAlike(Map<K, float[]> places) {
        float[] first = places.values().iterator().next();
        for (var place : places.entrySet()) {
            for (int h = 1; h < 24; h++) {
                assertEquals(first[h] - first[0], place.getValue()[h] - place.getValue()[0], 1e-3f,
                        place.getKey() + " changes with the day as every other place does, at " + h + ":00");
            }
        }
    }

    private static boolean night(double hour) {
        return hour >= TimeConstants.NIGHT_START_HOUR || hour < TimeConstants.DAWN_START_HOUR;
    }

    private static int[] nearest(World world, int ox, int oz, Biome biome) {
        for (int radius = 0; radius <= 1024; radius += 8) {
            for (int dx = -radius; dx <= radius; dx += 8) {
                for (int dz : radius == 0 ? new int[]{0} : new int[]{-radius, radius}) {
                    if (world.biomeAt(ox + dx, oz + dz) == biome) { return new int[]{ox + dx, oz + dz}; }
                }
            }
            for (int dz = -radius + 8; dz <= radius - 8; dz += 8) {
                for (int dx : new int[]{-radius, radius}) {
                    if (world.biomeAt(ox + dx, oz + dz) == biome) { return new int[]{ox + dx, oz + dz}; }
                }
            }
        }
        return null;
    }
}
