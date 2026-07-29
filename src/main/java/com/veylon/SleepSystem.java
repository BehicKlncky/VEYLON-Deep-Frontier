package com.veylon;

import com.veylon.entity.Affliction;
import com.veylon.entity.Creature;
import com.veylon.simulation.ShelterSystem;

/**
 * Sleeping: whether the player may, how restful it is, and what wakes them.
 *
 * <p>Sleep quality is scored once when it starts, from shelter coverage, bed
 * type, nearby fire, wetness and cold. That score then drives fatigue recovery,
 * whether body temperature keeps falling, and the odds of waking up ill — so a
 * bad camp is punished across the whole night rather than at a single check.
 *
 * <p>{@code sleeping} and {@code sleepFade} stay on {@link Game} because the
 * renderer reads them to drive the screen fade.
 */
final class SleepSystem {

    /** Predators within this radius make sleeping impossible. */
    private static final float PREDATOR_WATCH_RADIUS = 12f;
    private static final float NIGHT_START_HOUR = 19f;
    private static final float NIGHT_END_HOUR = 5f;
    /** Daytime naps need at least this much fatigue. */
    private static final float MIN_DAYTIME_FATIGUE = 55f;

    // Sleep quality scoring.
    private static final float BASE_QUALITY = 0.35f;
    private static final float FULL_COVERAGE_BONUS = 0.3f;
    private static final float CAMP_BED_BONUS = 0.2f;
    private static final float NEARBY_FIRE_BONUS = 0.15f;
    /** Fire heat in degrees that counts as "beside a fire". */
    private static final float FIRE_HEAT_MIN = 3f;
    private static final float WET_PENALTY = 0.25f;
    private static final float WET_THRESHOLD = 0.5f;
    private static final float FREEZING_PENALTY = 0.2f;
    private static final float FREEZING_ENV_TEMP = 0f;
    private static final float MIN_QUALITY = 0.1f;
    /** Quality above which the player settles down comfortably. */
    private static final float COMFORTABLE_QUALITY = 0.7f;
    /** Quality below which the night is miserable and can cause sickness. */
    private static final float MISERABLE_QUALITY = 0.4f;

    // While asleep.
    private static final float FADE_PER_SECOND = 1.5f;
    /** In-game minutes that pass per real second of sleep. */
    private static final float MINUTES_PER_SECOND = 170f;
    private static final float FATIGUE_RECOVERY_PER_SECOND = 9f;
    private static final float SLEEP_HUNGER_DRAIN_PER_SECOND = 0.10f;
    private static final float SLEEP_THIRST_DRAIN_PER_SECOND = 0.14f;
    /** Degrees lost per second when the night is miserable. */
    private static final float POOR_SLEEP_TEMP_LOSS_PER_SECOND = 0.25f;

    // Waking.
    private static final float WAKE_HOUR_MIN = 5.5f;
    private static final float WAKE_HOUR_MAX = 9f;
    /** In-game minutes before a morning wake-up is allowed. */
    private static final float MIN_SLEEP_MINUTES = 90f;
    /** In-game minutes after which full rest ends the night. */
    private static final float RESTED_SLEEP_MINUTES = 120f;
    private static final float FULLY_RESTED_FATIGUE = 1f;
    /** Damage flash above which the player is judged to be under attack. */
    private static final float ATTACKED_DAMAGE_FLASH = 0.5f;
    private static final double POOR_SLEEP_SICKNESS_CHANCE = 0.45;
    private static final float SICKNESS_SECONDS = 150f;

    private final Game game;
    private float sleepQuality;
    private float sleptMinutes;

    SleepSystem(Game game) {
        this.game = game;
    }

    /** Attempts to begin sleeping; logs the reason and does nothing when refused. */
    void startSleep(boolean campBed) {
        Creature threat = game.entities.nearestCreature(game.player.pos.x, game.player.pos.y,
                game.player.pos.z, PREDATOR_WATCH_RADIUS, c -> c.type.predator);
        if (threat != null) {
            game.log("Too dangerous to sleep - a predator prowls nearby!");
            return;
        }
        boolean night = game.time.hourF() >= NIGHT_START_HOUR
                || game.time.hourF() < NIGHT_END_HOUR;
        if (!night && game.player.fatigue < MIN_DAYTIME_FATIGUE) {
            game.log("You aren't tired enough to sleep (wait for night or fatigue "
                    + (int) MIN_DAYTIME_FATIGUE + "+).");
            return;
        }
        sleepQuality = scoreSleepQuality(campBed);
        game.sleeping = true;
        sleptMinutes = 0;
        game.audio.playSleep();
        game.log("You settle down to sleep"
                + (sleepQuality > COMFORTABLE_QUALITY ? " comfortably."
                : (sleepQuality < MISERABLE_QUALITY ? " - cold, wet and uneasy." : ".")));
    }

    /** Scores the resting conditions once, at the moment sleep begins. */
    private float scoreSleepQuality(boolean campBed) {
        ShelterSystem.Shelter sh = ShelterSystem.evaluate(game.world,
                game.player.pos.x, game.player.pos.y, game.player.pos.z);
        float quality = BASE_QUALITY;
        quality += sh.coverage() * FULL_COVERAGE_BONUS;
        if (campBed) {
            quality += CAMP_BED_BONUS;
        }
        if (game.player.nearFireHeat(game) > FIRE_HEAT_MIN) {
            quality += NEARBY_FIRE_BONUS;
        }
        if (game.player.wetness > WET_THRESHOLD) {
            quality -= WET_PENALTY;
        }
        if (game.player.envTemp < FREEZING_ENV_TEMP) {
            quality -= FREEZING_PENALTY;
        }
        return Math.max(MIN_QUALITY, Math.min(1f, quality));
    }

    /** Advances an active sleep, fast-forwarding the clock and needs. */
    void tickSleep(float dt) {
        game.sleepFade = Math.min(1f, game.sleepFade + dt * FADE_PER_SECOND);
        float minutes = dt * MINUTES_PER_SECOND;
        game.time.totalMinutes += minutes;
        sleptMinutes += minutes;

        // Reduced needs while asleep, faster fatigue recovery with quality.
        game.player.fatigue = Math.max(0,
                game.player.fatigue - dt * FATIGUE_RECOVERY_PER_SECOND * sleepQuality);
        game.player.hunger = Math.max(0,
                game.player.hunger - dt * SLEEP_HUNGER_DRAIN_PER_SECOND);
        game.player.thirst = Math.max(0,
                game.player.thirst - dt * SLEEP_THIRST_DRAIN_PER_SECOND);
        if (sleepQuality < MISERABLE_QUALITY) {
            game.player.bodyTemp -= dt * POOR_SLEEP_TEMP_LOSS_PER_SECOND;
        }

        boolean morning = game.time.hourF() >= WAKE_HOUR_MIN && game.time.hourF() < WAKE_HOUR_MAX
                && sleptMinutes > MIN_SLEEP_MINUTES;
        boolean rested = game.player.fatigue <= FULLY_RESTED_FATIGUE
                && sleptMinutes > RESTED_SLEEP_MINUTES;
        boolean attacked = game.player.damageFlash > ATTACKED_DAMAGE_FLASH;
        if (morning || rested || attacked) {
            wake(attacked);
        }
    }

    private void wake(boolean attacked) {
        game.sleeping = false;
        if (attacked) {
            game.log("You are attacked in your sleep!");
            return;
        }
        game.log("You wake after " + (int) (sleptMinutes / 60f * 10) / 10f + " hours. Fatigue "
                + (int) game.player.fatigue + ".");
        if (sleepQuality < MISERABLE_QUALITY && Math.random() < POOR_SLEEP_SICKNESS_CHANCE) {
            game.player.addAffliction(Affliction.SICKNESS, SICKNESS_SECONDS);
            game.log("That miserable night left you SICK. Sleep warm, dry and sheltered.");
        }
    }
}
