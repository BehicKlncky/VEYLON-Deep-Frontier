package com.veylon.world;

/**
 * A world seed from the text a person gives (0.9.1 milestone 06, decision D-33). NEW FRONTIER's
 * seed field and {@code VEYLON_SEED} read text the same way: the spaces around it are trimmed; a
 * whole number in the range of a {@code long}, with an optional sign, is the seed itself; any other
 * text is hashed with {@link String#hashCode()}, so the same words always make the same world.
 */
public final class WorldSeed {

    private WorldSeed() {
    }

    /**
     * The seed {@code text} names.
     *
     * @throws IllegalArgumentException when the text is blank, which names no seed
     */
    public static long of(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("a blank text names no seed");
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException words) {
            return trimmed.hashCode();
        }
    }

    /** Whether {@code text} is a seed's own number rather than words hashed into one. */
    public static boolean isNumber(String text) {
        try {
            Long.parseLong(text.trim());
            return true;
        } catch (NumberFormatException words) {
            return false;
        }
    }
}
