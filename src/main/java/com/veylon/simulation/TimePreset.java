package com.veylon.simulation;

/**
 * The four times of day a Creative builder can jump to (R25).
 *
 * <p>Each names an hour on {@link TimeSystem}'s clock, so the screen that
 * offers them never does clock arithmetic and the controls collaborator never
 * hard-codes an hour. Dawn and Dusk reuse the phase boundaries from
 * {@link TimeConstants}, so a preset always lands in the phase it is named
 * after even if those boundaries move.
 */
public enum TimePreset {
    DAWN("Dawn", TimeConstants.DAWN_START_HOUR),
    NOON("Noon", 12),
    DUSK("Dusk", TimeConstants.DUSK_START_HOUR),
    MIDNIGHT("Midnight", 0);

    public final String label;
    /** Hour of the day, 0-24, that the clock moves forward to. */
    public final double hour;

    TimePreset(String label, double hour) {
        this.label = label;
        this.hour = hour;
    }
}
