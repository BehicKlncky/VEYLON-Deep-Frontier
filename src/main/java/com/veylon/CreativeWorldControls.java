package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.simulation.TimePreset;
import com.veylon.simulation.WeatherSystem;
import com.veylon.ui.WorldControlsScreen;

import java.util.Objects;

/**
 * R25: the builder's controls over time, weather and wildlife spawning.
 *
 * <p>Three flags and two immediate commands, all Creative-only. They are held
 * here rather than on the systems they gate so that one object owns the whole
 * feature: the reset contract, the save round trip, the screen and the release
 * that happens when a world leaves Creative. The gated systems only ask.
 *
 * <p>Nothing here tests the game mode. {@link #clear()} runs on every mode
 * switch and on every new world, and a load restores the flags only into a
 * Creative world, so a flag can be true only while the world is Creative. That
 * keeps the three read sites in the simulation to a plain boolean question.
 *
 * <p>Every control is transient in the sense that gameplay never sets one:
 * only the paused screen does. They are persisted, because a builder who
 * froze noon and locked clear weather expects to find them that way.
 */
final class CreativeWorldControls {

    private final Game game;
    private boolean daylightFrozen;
    private boolean weatherLocked;
    private boolean spawningPaused;

    CreativeWorldControls(Game game) {
        this.game = game;
    }

    boolean daylightFrozen() { return daylightFrozen; }
    boolean weatherLocked() { return weatherLocked; }
    boolean spawningPaused() { return spawningPaused; }

    /** True when any control is holding the world, so the HUD and save can say so. */
    boolean anyActive() { return daylightFrozen || weatherLocked || spawningPaused; }

    /** Releases every control: a new world, a mode switch, or a Survival load. */
    void clear() {
        daylightFrozen = false;
        weatherLocked = false;
        spawningPaused = false;
    }

    /**
     * Applies loaded flags into a Creative world, and releases them in any
     * other. A Survival world can only ever have written false, because
     * {@link #clear()} runs on every switch and every new world; the mode test
     * here is what stops a hand-edited save from handing a Survival world a
     * frozen clock it has no screen to unfreeze with. The game-mode section is
     * written first, so the mode is already restored when this runs.
     */
    void restore(boolean frozen, boolean locked, boolean spawnsPaused) {
        if (game.gameMode() != GameMode.CREATIVE) {
            clear();
            return;
        }
        daylightFrozen = frozen;
        weatherLocked = locked;
        spawningPaused = spawnsPaused;
    }

    /**
     * Moves the clock forward to the next occurrence of a preset. Day counters,
     * seasons and every timer assume monotonic time, so this never rewinds:
     * choosing the hour it is already gives the same hour tomorrow.
     */
    void setTime(TimePreset preset) {
        Objects.requireNonNull(preset, "preset");
        int day = game.time.day();
        game.time.advanceToNextHour(preset.hour);
        game.log(game.time.day() == day
                ? "Time set to " + preset.label + "."
                : "Time set to " + preset.label + " - day " + game.time.day() + ".");
    }

    /** Freezing stops only the clock; every other system keeps ticking. */
    void toggleDaylightFreeze() {
        daylightFrozen = !daylightFrozen;
        game.log(daylightFrozen
                ? "Daylight cycle frozen at " + game.time.timeString() + "."
                : "Daylight cycle running again.");
    }

    /** Sets the weather with the transition already complete. */
    void setWeather(WeatherSystem.Weather weather) {
        Objects.requireNonNull(weather, "weather");
        game.weather.current = weather;
        game.weather.next = weather;
        game.weather.blend = 1f;
        game.log("Weather set to " + weather.displayName + ".");
    }

    /** The lock stops new transitions; the weather's own effects continue. */
    void toggleWeatherLock() {
        weatherLocked = !weatherLocked;
        game.log(weatherLocked
                ? "Weather locked on " + game.weather.effective().displayName + "."
                : "Weather changes on its own again.");
    }

    /** Pauses natural spawning only; the creatures already here stay and still despawn. */
    void toggleSpawning() {
        spawningPaused = !spawningPaused;
        game.log(spawningPaused ? "Wildlife spawning paused." : "Wildlife spawning resumed.");
    }

    /** Pause [T] (R25): Creative worlds only. */
    void openScreen() {
        game.worldControlsScreen.open();
        game.uiMode = Game.UiMode.WORLD_CONTROLS;
    }

    /** Applies one screen command; CLOSE returns to the pause menu. */
    void handleScreen(WorldControlsScreen.Action action) {
        switch (action.kind()) {
            case NONE -> { }
            case CLOSE -> game.uiMode = Game.UiMode.PAUSE;
            case SET_TIME -> setTime(action.time());
            case SET_WEATHER -> setWeather(action.weather());
            case TOGGLE_FREEZE -> toggleDaylightFreeze();
            case TOGGLE_LOCK -> toggleWeatherLock();
            case TOGGLE_SPAWNING -> toggleSpawning();
        }
    }
}
