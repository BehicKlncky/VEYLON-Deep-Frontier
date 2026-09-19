package com.veylon;

import com.veylon.engine.AudioManager;

/**
 * Lets tests outside {@code com.veylon} build a headless game that records
 * its sound requests through the package-private {@code Game(AudioManager)}.
 */
public final class HeadlessGames {

    private HeadlessGames() {
    }

    /** A game whose every sound request goes to {@code audio}. */
    public static Game withAudio(AudioManager audio) {
        return new Game(audio);
    }
}
