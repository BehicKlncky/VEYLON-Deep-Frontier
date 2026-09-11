package com.veylon.engine;

import java.util.Random;

/** Single-frame-thread phrase scheduler; silence is the dominant state. */
final class MusicDirector {
    /** Seconds per phrase, before at least two minutes of silence. */
    static final float PHRASE_SECONDS = 12, SILENCE_MIN = 120, SILENCE_RANGE = 60;
    /** Initial quiet period after world entry, allowing the environment to establish itself. */
    static final float INITIAL_SILENCE = 12;
    /** Seconds for a state to settle and for the old phrase to release. */
    static final float STABLE_SECONDS = 2, RELEASE_SECONDS = 2;
    /** Phrase entry/exit envelopes in seconds; source level remains beneath action cues. */
    static final float ATTACK_SECONDS = 2, TAIL_SECONDS = 3, SOURCE_GAIN = 0.22f;
    /** Per-second convergence when a nonzero music slider changes. */
    static final float VOLUME_BLEND = 8;
    interface Backend {
        void start(MusicMood mood);
        void gain(float gain);
        void stop();
    }

    private final Random rng;
    private final Backend backend;
    private MusicMood requested = MusicMood.CALM, playing;
    private boolean sceneActive, firstNightPlayed, releasing;
    private float quiet = INITIAL_SILENCE, stable, age, releaseAge, releaseFrom, volume, output;
    int phrases;

    MusicDirector(Random rng, Backend backend) { this.rng = rng; this.backend = backend; }

    void scene(MusicMood mood) {
        if (mood == null) return;
        sceneActive = true;
        if (requested != mood) { requested = mood; stable = 0; }
    }

    void update(float dt, float level) {
        dt = Math.max(0, dt);
        stable += dt;
        boolean allowed = sceneActive && level > 0;
        MusicMood desired = requested == MusicMood.FIRST_NIGHT && firstNightPlayed && playing != MusicMood.FIRST_NIGHT
                ? MusicMood.NIGHT : requested;
        if (playing == null) {
            if (!allowed) return;
            quiet -= dt;
            if (quiet > 0) return;
            playing = desired; age = output = 0; volume = level; releasing = false;
            if (playing == MusicMood.FIRST_NIGHT) firstNightPlayed = true;
            phrases++; backend.gain(0); backend.start(playing);
            return;
        }
        if (!releasing && (!allowed || stable >= STABLE_SECONDS && desired != playing)) {
            releasing = true; releaseAge = 0; releaseFrom = output;
        }
        if (releasing) {
            releaseAge += dt;
            output = releaseFrom * cosineRelease(releaseAge / RELEASE_SECONDS);
            backend.gain(output);
            if (releaseAge >= RELEASE_SECONDS) finish();
            return;
        }
        age += dt;
        volume += (level - volume) * (float) -Math.expm1(-VOLUME_BLEND * dt);
        output = SOURCE_GAIN * volume * envelope(age);
        backend.gain(output);
        if (age >= PHRASE_SECONDS) finish();
    }

    static float envelope(float age) {
        return (1 - cosineRelease(age / ATTACK_SECONDS)) * cosineRelease((age - PHRASE_SECONDS + TAIL_SECONDS) / TAIL_SECONDS);
    }

    private static float cosineRelease(float fraction) {
        return (float) (0.5 + 0.5 * Math.cos(Math.PI * Math.max(0, Math.min(1, fraction))));
    }

    private void finish() {
        backend.gain(0); backend.stop(); output = 0; playing = null;
        quiet = SILENCE_MIN + rng.nextFloat() * SILENCE_RANGE;
    }

    void reset() {
        backend.gain(0); backend.stop();
        playing = null; requested = MusicMood.CALM; sceneActive = firstNightPlayed = releasing = false;
        stable = age = releaseAge = releaseFrom = volume = output = 0; quiet = INITIAL_SILENCE;
    }
}
