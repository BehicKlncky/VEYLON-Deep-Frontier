package com.veylon.save;

import java.util.Objects;

/**
 * What one attempt to load a save found: whether its world is now the live one and, when it is
 * not, why, once in the player's words ({@link #message()}) and once in the log's ({@link #reason()}).
 *
 * @param outcome what happened
 * @param reason  the technical cause, for the log; empty when the save loaded
 */
public record LoadResult(Outcome outcome, String reason) {

    /** What a load attempt found. */
    public enum Outcome {
        /** The save was read in full and its world is now the live one. */
        LOADED("Game loaded."),
        /** No file at the save's path. */
        NO_SAVE("No save found."),
        /** A save of an earlier format or epoch: VEYLON 0.9.0 and every earlier release wrote one. */
        OLDER_VERSION("This save was written by an older version of VEYLON. Start a new world."),
        /** A save of a later format or epoch than this build opens. */
        NEWER_VERSION("This save was written by a newer version of VEYLON. Update the game."),
        /** The file does not start like a VEYLON save at all. */
        NOT_A_SAVE("This file is not a VEYLON save."),
        /** A save of this build's format and epoch that cannot be read: cut short, corrupt or too large. */
        DAMAGED("This save is damaged and cannot be opened."),
        /** The file exists but could not be read. */
        UNREADABLE("The save could not be read.");

        private final String message;

        Outcome(String message) {
            this.message = message;
        }
    }

    /** A loaded save. */
    public static final LoadResult LOADED = new LoadResult(Outcome.LOADED, "");

    public LoadResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(reason, "reason");
    }

    static LoadResult damaged(String reason) {
        return new LoadResult(Outcome.DAMAGED, reason);
    }

    /** True when the save's world is now the live one. */
    public boolean loaded() {
        return outcome == Outcome.LOADED;
    }

    /**
     * True when the save was refused for the version that wrote it, before anything was read
     * past its header.
     */
    public boolean otherVersion() {
        return outcome == Outcome.OLDER_VERSION || outcome == Outcome.NEWER_VERSION;
    }

    /** One short line for the player: the title screen's notice or the in-game log. */
    public String message() {
        return outcome.message;
    }
}
