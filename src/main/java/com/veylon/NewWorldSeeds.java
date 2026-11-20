package com.veylon;

import java.util.Random;

/**
 * The seeds NEW FRONTIER offers (0.9.1 milestone 06, decision D-33). In a player's session every
 * opening of the screen and every reroll draws a new seed. A session that names one with
 * {@code VEYLON_SEED} opens on it every time and draws its rerolls from it, so an automated run
 * that walks NEW FRONTIER stays deterministic; a seed the player types still replaces it for that
 * world.
 *
 * <p>The draws choose which world is built, not anything that happens in one, so
 * {@link WorldBootstrap} does not reseed them: every stream of the world itself is seeded from the
 * seed chosen.
 */
final class NewWorldSeeds {

    /** Keeps a session's rerolls apart from the streams its worlds draw from the same number. */
    private static final long REROLL_SALT = 0x4e45575345454453L;

    private final Random draws;
    private final boolean fixed;
    private final long seed;

    private NewWorldSeeds(Random draws, boolean fixed, long seed) {
        this.draws = draws;
        this.fixed = fixed;
        this.seed = seed;
    }

    /** A player's session: a new seed for every opening and every reroll. */
    static NewWorldSeeds random() {
        return new NewWorldSeeds(new Random(), false, 0);
    }

    /** A session that names {@code seed}: every opening offers it, and the rerolls follow from it. */
    static NewWorldSeeds fixed(long seed) {
        return new NewWorldSeeds(new Random(seed ^ REROLL_SALT), true, seed);
    }

    /** Whether the session named its seed. */
    boolean isFixed() {
        return fixed;
    }

    /** The seed NEW FRONTIER opens on. */
    long opening() {
        return fixed ? seed : draws.nextLong();
    }

    /** R on NEW FRONTIER: a seed other than {@code current}. */
    long reroll(long current) {
        long next = draws.nextLong();
        while (next == current) {
            next = draws.nextLong();
        }
        return next;
    }
}
