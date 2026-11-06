package com.veylon.gfx;

import com.veylon.Game;
import com.veylon.entity.GameMode;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Urban milestone 22: a generated city tells the terrain shader it is one (so rain and frost mark
 * only open sky and rooms get a little daylight) and its standing water is murky; other worlds
 * keep the clear lake water and the old shading.
 */
class CityEnvironmentTest {
    @Test
    void aGeneratedCityIsFlaggedAndItsWaterIsMurky() {
        Environment env = new Environment();
        Game city = new Game();
        city.newWorld(20260731L, true, World.GEN_URBAN_MISSIONS, GameMode.SURVIVAL);
        env.update(city, 0.05f);
        assertEquals(1f, env.city);
        assertTrue(env.waterDeep.z < 0.12f && env.waterShallow.z < 0.3f, "still, murky city water: " + env.waterShallow);

        Game wild = new Game();
        wild.newWorld(20260731L, true, World.GEN_CAVE_IDENTITIES, GameMode.SURVIVAL);
        env.update(wild, 0.05f);
        assertEquals(0f, env.city, "the wilderness keeps the old shading");
        assertEquals(0.28f, env.waterDeep.z, 1e-6f, "and its clear lake water");
        assertEquals(0.45f, env.waterShallow.z, 1e-6f);

        Game early = new Game();
        early.newWorld(20260731L, true, World.GEN_URBAN_PLANS, GameMode.SURVIVAL);
        env.update(early, 0.05f);
        assertEquals(0f, env.city, "a city without generated buildings is not dressed");
    }
}
