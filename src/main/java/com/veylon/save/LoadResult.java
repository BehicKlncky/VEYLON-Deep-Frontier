package com.veylon.save;

import java.util.Objects;

/**
 * What one attempt to load a save found: whether its world is now the live one and, when it is
 * not, why, in the player's words ({@link #message()} on the title, {@link #logLine()} in the
 * game's log) and in the console's ({@link #reason()}).
 *
 * @param outcome what happened
 * @param reason  the technical cause, for the console; empty when the save loaded
 */
public record LoadResult(Outcome outcome, String reason) {

    /** The longest {@link #logLine()}: the HUD's log panel cuts a longer line short at 1280 x 720. */
    public static final int LOG_LINE_LIMIT = 32;

    /** What a load attempt found. */
    public enum Outcome {
        /** The save was read in full and its world is now the live one. */
        LOADED("Game loaded.", "Game loaded."),
        /** No file at the save's path. */
        NO_SAVE("No save found.", "No save found."),
        /** A save of an earlier format or epoch: VEYLON 0.9.0 and every earlier release wrote one. */
        OLDER_VERSION("This save was written by an older version of VEYLON. Start a new world.",
                "Save is from an older version."),
        /** A save of a later format or epoch than this build opens. */
        NEWER_VERSION("This save was written by a newer version of VEYLON. Update the game.",
                "Save is from a newer version."),
        /** The file does not start like a VEYLON save at all. */
        NOT_A_SAVE("This file is not a VEYLON save.", "Not a VEYLON save."),
        /** A save of this build's format and epoch that cannot be read: cut short, corrupt or too large. */
        DAMAGED("This save is damaged and cannot be opened.", "The save is damaged."),
        /** The file exists but could not be read. */
        UNREADABLE("The save could not be read.", "The save could not be read.");

        private final String message;
        private final String logLine;

        Outcome(String message, String logLine) {
            this.message = message;
            this.logLine = logLine;
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

    /** One line for the player under the title menu, saying what to do where it can. */
    public String message() {
        return outcome.message;
    }

    /** The same news short enough for the game's log, where F9 reports it. */
    public String logLine() {
        return outcome.logLine;
    }
}
