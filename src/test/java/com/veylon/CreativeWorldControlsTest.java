package com.veylon;

import com.veylon.engine.Input;
import com.veylon.entity.GameMode;
import com.veylon.save.SaveSystem;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.simulation.TimeConstants;
import com.veylon.simulation.TimePreset;
import com.veylon.simulation.WeatherSystem;
import com.veylon.ui.WorldControlsScreen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/** R25: Creative-only time, weather and spawning controls, and their release. */
class CreativeWorldControlsTest {
    @TempDir Path temporary;
    private Game game;

    @BeforeEach
    void setUp() { game = CreativeTestArena.create(GameMode.CREATIVE); }

    @Test
    void tOpensTheScreenOnlyFromPauseInCreativeAndItOwnsEveryKey() throws Exception {
        HotkeyRouter router = new HotkeyRouter(game);
        route(router, GLFW_KEY_T);
        assertEquals(Game.UiMode.NONE, game.uiMode, "R25: T is not a gameplay key");

        Game survival = CreativeTestArena.create(GameMode.SURVIVAL);
        HotkeyRouter survivalRouter = new HotkeyRouter(survival);
        survival.uiMode = Game.UiMode.PAUSE;
        for (int key : new int[]{GLFW_KEY_T}) pressed(survival)[key] = true;
        survivalRouter.update();
        survival.input.endFrame();
        assertEquals(Game.UiMode.PAUSE, survival.uiMode, "R25: Survival has no world controls");

        game.uiMode = Game.UiMode.PAUSE;
        route(router, GLFW_KEY_T);
        assertEquals(Game.UiMode.WORLD_CONTROLS, game.uiMode, "R25: T opens the screen from pause");
        assertTrue(game.uiMode.pausesSimulation(), "R25: the screen pauses the world");

        int logLines = game.eventLog.recent(200).size();
        route(router, GLFW_KEY_ESCAPE, GLFW_KEY_F5, GLFW_KEY_F9, GLFW_KEY_Q,
                GLFW_KEY_O, GLFW_KEY_V, GLFW_KEY_E, GLFW_KEY_G, GLFW_KEY_1);
        assertEquals(Game.UiMode.WORLD_CONTROLS, game.uiMode,
                "R25: Escape, F5, F9, Q, O, V, E, G and the hotbar never reach the router");
        assertEquals(logLines, game.eventLog.recent(200).size(), "R25: F5 did not save and F9 did not load");

        game.creativeControls.handleScreen(press(GLFW_KEY_ESCAPE));
        assertEquals(Game.UiMode.PAUSE, game.uiMode, "R25: Escape returns to the pause menu");
    }

    @Test
    void timePresetsAlwaysMoveForwardAndCarryTheDayOverMidnight() {
        game.time.totalMinutes = 10 * TimeConstants.MINUTES_PER_HOUR; // day 1, 10:00
        game.creativeControls.setTime(TimePreset.DUSK);
        assertEquals(1, game.time.day());
        assertEquals(17, game.time.hour(), "R25: Dusk later today");

        game.creativeControls.setTime(TimePreset.NOON);
        assertEquals(2, game.time.day(), "R25: a preset already passed today lands tomorrow");
        assertEquals(12, game.time.hour());

        game.creativeControls.setTime(TimePreset.MIDNIGHT);
        assertEquals(3, game.time.day(), "R25: midnight is the start of the next day");
        assertEquals(0, game.time.hour());

        double before = game.time.totalMinutes;
        game.creativeControls.setTime(TimePreset.MIDNIGHT);
        assertEquals(before + TimeConstants.MINUTES_PER_DAY, game.time.totalMinutes,
                "R25: choosing the current hour moves a whole day forward, never backward");
        for (TimePreset preset : TimePreset.values()) {
            double now = game.time.totalMinutes;
            game.creativeControls.setTime(preset);
            assertTrue(game.time.totalMinutes > now, "R25: " + preset + " only moves the clock forward");
        }
    }

    @Test
    void freezingHoldsTheClockWhileEveryOtherSystemKeepsTicking() {
        game.creativeControls.toggleDaylightFreeze();
        assertTrue(game.daylightFrozen());
        double held = game.time.totalMinutes;
        int weatherTicks = 0;
        for (int i = 0; i < 200; i++) {
            game.advanceClock(0.25);
            game.mediumTick(SimulationScheduler.MEDIUM_DT);
            weatherTicks++;
        }
        assertEquals(held, game.time.totalMinutes, "R25: a frozen cycle never advances the clock");
        assertEquals(200, weatherTicks, "precondition: the other systems were driven");
        assertTrue(game.player.envTemp != 0f || game.player.biome != null,
                "R25: freezing the clock does not stop the environment");

        game.creativeControls.toggleDaylightFreeze();
        game.advanceClock(1.0);
        assertTrue(game.time.totalMinutes > held, "R25: unfreezing starts the clock again");
    }

    @Test
    void sleepingIsRefusedWhileTheDaylightCycleIsFrozen() {
        game.creativeControls.toggleDaylightFreeze();
        game.startSleep(false);
        assertFalse(game.sleeping, "R25: sleeping would fast-forward the very clock that is frozen");
        assertTrue(game.eventLog.recent(1).getFirst().contains("frozen"),
                "R25: the refusal says why");
        game.creativeControls.toggleDaylightFreeze();
        game.time.totalMinutes = 23 * TimeConstants.MINUTES_PER_HOUR;
        game.startSleep(false);
        assertTrue(game.sleeping, "R25: unfreezing restores ordinary sleeping");
    }

    @Test
    void aLockedSkyRollsNoNewWeatherButKeepsTheWeatherItHas() {
        game.creativeControls.setWeather(WeatherSystem.Weather.STORM);
        assertEquals(WeatherSystem.Weather.STORM, game.weather.effective(),
                "R25: a preset applies immediately with the blend complete");
        assertEquals(1f, game.weather.blend);
        game.creativeControls.toggleWeatherLock();
        int strikes = game.weather.lightningStrikes;
        for (int i = 0; i < 600; i++) {
            game.mediumTick(SimulationScheduler.MEDIUM_DT);
        }
        assertEquals(WeatherSystem.Weather.STORM, game.weather.effective(),
                "R25: the lock holds the sky across many medium ticks");
        assertTrue(game.weather.lightningStrikes >= strikes,
                "R25: the locked weather's own effects still happen");

        game.creativeControls.toggleWeatherLock();
        assertFalse(game.weatherLocked(), "R25: unlocking releases the sky");
    }

    @Test
    void pausedSpawningStopsNewWildlifeWithoutTouchingTheCreaturesAlreadyHere() {
        // Real terrain, because spawning needs loaded surface around the player.
        Game free = creativeWorld();
        Game held = creativeWorld();
        int start = held.entities.creatureCount();
        assertEquals(start, free.entities.creatureCount(), "precondition: identical starting wildlife");

        held.creativeControls.toggleSpawning();
        assertTrue(held.spawningPaused());
        // Drive the gated path itself. The whole slow bucket would also run the
        // scripted wolf-migration event, which R25 deliberately leaves alone.
        for (int i = 0; i < 40; i++) {
            free.entities.slowTick(free);
            held.entities.slowTick(held);
        }
        assertTrue(free.entities.creatureCount() > start,
                "precondition: the same run spawns wildlife without the control");
        assertEquals(start, held.entities.creatureCount(),
                "R25: paused spawning adds no wildlife and removes none");

        held.creativeControls.toggleSpawning();
        for (int i = 0; i < 40; i++) held.entities.slowTick(held);
        assertTrue(held.entities.creatureCount() > start, "R25: resuming spawns again");
    }

    @Test
    void withEveryControlOffBothModesAdvanceTimeWeatherAndSpawningIdentically() {
        Game survival = new Game();
        survival.newWorld(20260910L, true, GameMode.SURVIVAL);
        Game creative = creativeWorld();
        assertFalse(creative.daylightFrozen() || creative.weatherLocked() || creative.spawningPaused(),
                "R25: Creative starts with every control off");
        for (int i = 0; i < 40; i++) {
            survival.advanceClock(0.5);
            creative.advanceClock(0.5);
            survival.mediumTick(SimulationScheduler.MEDIUM_DT);
            creative.mediumTick(SimulationScheduler.MEDIUM_DT);
            survival.entities.slowTick(survival);
            creative.entities.slowTick(creative);
        }
        assertEquals(survival.time.totalMinutes, creative.time.totalMinutes,
                "R25/R7: the clock advances identically with no control engaged");
        assertEquals(survival.weather.current, creative.weather.current, "R25/R7: same weather");
        assertEquals(survival.weather.next, creative.weather.next, "R25/R7: same weather target");
        assertEquals(survival.weather.blend, creative.weather.blend, "R25/R7: same transition");
        assertEquals(survival.weather.changeTimer, creative.weather.changeTimer,
                "R25/R7: the same weather RNG draws happened in both modes");
        assertEquals(survival.entities.creatureCount(), creative.entities.creatureCount(),
                "R25/R7: natural spawning is untouched when the control is off");
    }

    private static Game creativeWorld() {
        Game world = new Game();
        world.newWorld(20260910L, true, GameMode.CREATIVE);
        return world;
    }

    @Test
    void leavingCreativeReleasesEveryControlAndTheSaveRestoresThemInCreative() {
        game.creativeControls.toggleDaylightFreeze();
        game.creativeControls.toggleWeatherLock();
        game.creativeControls.toggleSpawning();
        Path save = temporary.resolve("controls.sav");
        assertTrue(SaveSystem.save(game, save));

        Game loaded = new Game();
        loaded.newWorld(4422, true);
        assertTrue(SaveSystem.load(loaded, save), "R25: a world with controls loads");
        assertTrue(loaded.daylightFrozen() && loaded.weatherLocked() && loaded.spawningPaused(),
                "R25: every control survives the round trip");

        assertTrue(loaded.switchGameMode(GameMode.SURVIVAL));
        assertFalse(loaded.daylightFrozen() || loaded.weatherLocked() || loaded.spawningPaused(),
                "R25: leaving Creative releases every control");
        assertTrue(loaded.switchGameMode(GameMode.CREATIVE));
        assertFalse(loaded.daylightFrozen() || loaded.weatherLocked() || loaded.spawningPaused(),
                "R25: returning to Creative starts from no controls");

        Path survivalSave = temporary.resolve("survival.sav");
        assertTrue(loaded.switchGameMode(GameMode.SURVIVAL));
        assertTrue(SaveSystem.save(loaded, survivalSave));
        Game survival = new Game();
        assertTrue(SaveSystem.load(survival, survivalSave));
        assertFalse(survival.daylightFrozen() || survival.weatherLocked() || survival.spawningPaused(),
                "R25: a Survival world never carries a control");
    }

    @Test
    void aNewWorldStartsWithNoControlsWhicheverModeItIs() {
        game.creativeControls.toggleDaylightFreeze();
        game.creativeControls.toggleSpawning();
        game.newWorld(20260910L, true, GameMode.CREATIVE);
        assertFalse(game.daylightFrozen() || game.weatherLocked() || game.spawningPaused(),
                "R25: cross-world control state must not survive newWorld");
    }

    @Test
    void everyControlKeyAndTheScreenCommandsAgree() throws Exception {
        assertEquals(WorldControlsScreen.Action.Kind.CLOSE, press(GLFW_KEY_T).kind(),
                "R25: T also closes the screen");
        assertEquals(TimePreset.DAWN, press(GLFW_KEY_1).time());
        assertEquals(TimePreset.MIDNIGHT, press(GLFW_KEY_4).time());
        assertEquals(WeatherSystem.Weather.CLEAR, press(GLFW_KEY_5).weather());
        assertEquals(WeatherSystem.Weather.SNOW, press(GLFW_KEY_0).weather());
        assertEquals(WorldControlsScreen.Action.Kind.TOGGLE_FREEZE, press(GLFW_KEY_F).kind());
        assertEquals(WorldControlsScreen.Action.Kind.TOGGLE_LOCK, press(GLFW_KEY_L).kind());
        assertEquals(WorldControlsScreen.Action.Kind.TOGGLE_SPAWNING, press(GLFW_KEY_K).kind());
        assertEquals(WorldControlsScreen.Action.Kind.NONE, press(GLFW_KEY_M).kind(),
                "R25: an unbound key does nothing");
    }

    private WorldControlsScreen.Action press(int... keys) throws ReflectiveOperationException {
        for (int key : keys) pressed(game)[key] = true;
        WorldControlsScreen.Action action = game.worldControlsScreen.handleKeys(game.input);
        game.input.endFrame();
        return action;
    }

    private void route(HotkeyRouter router, int... keys) throws ReflectiveOperationException {
        for (int key : keys) pressed(game)[key] = true;
        router.update();
        game.input.endFrame();
    }

    private static boolean[] pressed(Game game) throws ReflectiveOperationException {
        var field = Input.class.getDeclaredField("keyPressed");
        field.setAccessible(true);
        return (boolean[]) field.get(game.input);
    }
}
