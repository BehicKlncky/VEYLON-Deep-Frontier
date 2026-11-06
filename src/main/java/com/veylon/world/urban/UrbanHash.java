package com.veylon.world.urban;

/**
 * The pure derivation behind every urban plan decision.
 *
 * <p>A value depends on the world seed, the layout revision, one salt from
 * {@link UrbanSalts} and up to three stable coordinates — never on a shared
 * {@code Random}, a visit order or anything cached. Asking twice, in any
 * order, from any chunk, gives the same answer; that is what lets a chunk build
 * its slice of a plan without its neighbours existing.
 *
 * <p>The mixing is SplitMix64's finaliser applied to each input in turn, which
 * is cheap and spreads a one-bit change across the whole word.
 */
public final class UrbanHash {

    private UrbanHash() {
    }

    /** A well-mixed 64-bit value for one decision. */
    public static long mix(long seed, int layoutRevision, long salt, long a, long b, long c) {
        long h = scramble(seed ^ 0x9e37_79b9_7f4a_7c15L);
        h = scramble(h ^ layoutRevision);
        h = scramble(h ^ salt);
        h = scramble(h ^ a);
        h = scramble(h ^ b);
        return scramble(h ^ c);
    }

    /** A value in [0, 1) for one decision. */
    public static double unit(long seed, int layoutRevision, long salt, long a, long b, long c) {
        return (mix(seed, layoutRevision, salt, a, b, c) >>> 11) * 0x1.0p-53;
    }

    /**
     * A value in {@code lo..hi}, both inclusive, for one decision.
     *
     * @throws IllegalArgumentException if {@code hi < lo}
     */
    public static int range(int lo, int hi, long seed, int layoutRevision, long salt,
                            long a, long b, long c) {
        if (hi < lo) {
            throw new IllegalArgumentException("empty range " + lo + ".." + hi);
        }
        long span = (long) hi - lo + 1;
        return (int) (lo + Math.floorMod(mix(seed, layoutRevision, salt, a, b, c), span));
    }

    private static long scramble(long z) {
        z += 0x9e37_79b9_7f4a_7c15L;
        z = (z ^ (z >>> 30)) * 0xbf58_476d_1ce4_e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d0_49bb_1331_11ebL;
        return z ^ (z >>> 31);
    }
}
