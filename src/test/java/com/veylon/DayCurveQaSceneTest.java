package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.simulation.WeatherSystem;
import com.veylon.world.Biome;
import com.veylon.world.World;
import com.veylon.world.urban.UrbanDistrict;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code VEYLON_SCENE=day_curve} (0.9.1 milestone 08, B14) headlessly: in the wilderness it stands on
 * the meadow site and in a city at the industrial yards' tour stop, under the sky, shows the debug
 * overlay, holds clear weather and reads the player's air at noon of the first day and at 03:00 of
 * the second, and noon is the warmer.
 */
class DayCurveQaSceneTest {

    private static final long SEED = 20_260_731L;

    @Test
    void inTheWildernessTheSceneReadsTheMeadowAtNoonAndBeforeDawn() {
        Game g = new Game();
        g.newWorld(SEED, true, World.WILDERNESS_NEW_WORLD_GENERATOR, GameMode.SURVIVAL);
        DayCurveQaScene scene = play(g);
        assertEquals("meadow", scene.place());
        assertEquals(Biome.MEADOW, g.world.biomeAt(feetX(g), feetZ(g)));
        readAtNoonAndBeforeDawn(g, scene);
    }

    @Test
    void inTheCityTheSceneReadsTheIndustrialYardsUnderTheSky() {
        Game g = new Game();
        g.newWorld(SEED, true, World.DEFAULT_NEW_WORLD_GENERATOR, GameMode.SURVIVAL);
        DayCurveQaScene scene = play(g);
        assertEquals("industrial yards", scene.place());
        assertEquals(UrbanDistrict.INDUSTRIAL, g.world.urban.districtAt(feetX(g), feetZ(g)));
        assertEquals(Biome.SCRUBLAND, g.world.biomeAt(feetX(g), feetZ(g)), "the yards' climate");
        readAtNoonAndBeforeDawn(g, scene);
    }

    private static DayCurveQaScene play(Game g) {
        g.appState = Game.AppState.PLAYING;
        DayCurveQaScene scene = new DayCurveQaScene(g);
        scene.stage();
        assertTrue(g.debugShown, "the debug overlay shows the air");
        float dt = 1f / 30f;
        for (float t = 0; t <= DayCurveQaScene.NIGHT_REPORT + 0.1f; t += dt) {
            g.playStep(dt, dt);
            scene.update(t);
        }
        assertTrue(scene.finished(), "both readings were taken");
        return scene;
    }

    private static void readAtNoonAndBeforeDawn(Game g, DayCurveQaScene scene) {
        assertTrue(g.world.isSkyExposed(feetX(g), (int) Math.floor(g.player.pos.y + 0.01f) + 1, feetZ(g)),
                "the reading is taken under the sky");
        assertEquals(WeatherSystem.Weather.CLEAR, g.weather.effective(), "clear weather throughout");
        assertTrue(g.events.active.isEmpty(), "no world event warms or chills the reading");
        assertEquals(12.0, scene.noonAt() / 60, 0.1, "the first reading is at noon of the first day");
        assertEquals(27.0, scene.nightAt() / 60, 0.1, "the second is at 03:00 of the next day");
        assertEquals(g.player.envTemp, scene.night(), 0.05f, "the reading is the air the overlay shows");
        assertTrue(Float.isFinite(scene.noon()), "the noon reading");
        assertTrue(scene.passed(), "noon (" + scene.noon() + ") is warmer than before dawn (" + scene.night()
                + "), 0.9.1 B14");
    }

    private static int feetX(Game g) {
        return (int) Math.floor(g.player.pos.x);
    }

    private static int feetZ(Game g) {
        return (int) Math.floor(g.player.pos.z);
    }
}
