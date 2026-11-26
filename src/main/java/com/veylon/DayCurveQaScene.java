package com.veylon;

import com.veylon.simulation.WeatherSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.Biome;
import com.veylon.world.urban.UrbanDistrict;

import java.util.Locale;

/**
 * Opt-in capture scene for the day's temperature (0.9.1 B14). Inert in an ordinary world.
 *
 * <p>{@code day_curve} stands the player where the debug overlay (F3, shown by the scene) reads
 * the day's swing: in a city at the industrial yards' district-tour stop, the gateway of a fenced
 * yard in the warmest district; in the wilderness on the meadow site of the {@code day} scene. It
 * holds clear weather and no world event, sets the clock to noon of the first day and at
 * {@link #NIGHT_AT} moves it on to 03:00 of the second, both in the first season. The console
 * prints the air the overlay shows at {@link #NOON_REPORT} and {@link #NIGHT_REPORT}, the moments to
 * capture, and ends with {@code pass=true} when noon is the warmer.
 */
final class DayCurveQaScene {

    /** Seconds into the scene of the noon reading, the move to 03:00 and the night reading. */
    static final double NOON_REPORT = 2.0, NIGHT_AT = 2.5, NIGHT_REPORT = 4.0;
    /** Noon of the first day and 03:00 of the second, in game minutes; the clock only moves on. */
    private static final double NOON = 12 * 60, BEFORE_DAWN = 27 * 60;

    private final Game game;
    private boolean active;
    private boolean movedOn;
    private boolean finished;
    private boolean passed;
    private String place = "";
    private float noon = Float.NaN;
    private float night = Float.NaN;
    private double noonAt = -1;
    private double nightAt = -1;

    DayCurveQaScene(Game game) {
        this.game = game;
    }

    void stage() {
        if (game.world.urban != null && game.world.urban.hasDistricts()) {
            UrbanDistrictTourPlan.Stop stop =
                    UrbanDistrictTourPlan.of(game.world).stops().get(UrbanDistrict.INDUSTRIAL);
            Vec3i at = stop.stand(), subject = stop.subject();
            game.world.ensureChunks(at.x(), at.z(), 4, 10_000);
            game.player.pos.set(at.x() + .5f, at.y() + .001f, at.z() + .5f);
            game.player.vel.zero();
            game.player.onGround = true;
            float dx = subject.x() + .5f - game.player.pos.x, dz = subject.z() + .5f - game.player.pos.z;
            float dy = subject.y() + stop.height() - (game.player.pos.y + game.player.eyeHeight());
            game.camera.yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
            game.camera.pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
            place = "industrial yards";
        } else {
            BenchmarkSite.move(game, Biome.MEADOW);
            place = "meadow";
        }
        game.time.totalMinutes = NOON;
        hold();
        game.debugShown = true;
        active = true;
        System.out.printf(Locale.ROOT, "[day-curve] %s at %.1f, %.1f, %.1f, biome %s%n", place,
                game.player.pos.x, game.player.pos.y, game.player.pos.z,
                game.world.biomeAt((int) Math.floor(game.player.pos.x), (int) Math.floor(game.player.pos.z)).displayName);
    }

    void update(double elapsed) {
        if (!active || finished) {
            return;
        }
        hold();
        if (Float.isNaN(noon) && elapsed >= NOON_REPORT) {
            noonAt = game.time.totalMinutes;
            noon = report("noon");
        }
        if (!movedOn && elapsed >= NIGHT_AT) {
            movedOn = true;
            game.time.totalMinutes = BEFORE_DAWN;
        }
        if (movedOn && elapsed >= NIGHT_REPORT) {
            nightAt = game.time.totalMinutes;
            night = report("before dawn");
            finished = true;
            passed = noon > night;
            System.out.printf(Locale.ROOT, "[day-curve] %s noon %.1f C, before dawn %.1f C, pass=%b%n", place, noon,
                    night, passed);
        }
    }

    /** Where the scene stands: "industrial yards" or "meadow"; empty before {@link #stage}. */
    String place() {
        return place;
    }

    /** The air read at noon, NaN until read. */
    float noon() {
        return noon;
    }

    /** The air read before dawn, NaN until read. */
    float night() {
        return night;
    }

    /** The game minute of each reading, -1 until read. */
    double noonAt() {
        return noonAt;
    }

    double nightAt() {
        return nightAt;
    }

    boolean finished() {
        return finished;
    }

    boolean passed() {
        return passed;
    }

    /** Clear weather that will not turn, and no world event to warm or chill the reading. */
    private void hold() {
        game.weather.current = WeatherSystem.Weather.CLEAR;
        game.weather.next = WeatherSystem.Weather.CLEAR;
        game.weather.blend = 1f;
        game.weather.changeTimer = 4000f;
        game.events.active.clear();
    }

    private float report(String moment) {
        float air = game.player.envTemp;
        System.out.printf(Locale.ROOT, "[day-curve] %s %s: %s, air %.1f C, body %.1f C%n", place, moment,
                game.time.timeString(), air, game.player.bodyTemp);
        return air;
    }
}
