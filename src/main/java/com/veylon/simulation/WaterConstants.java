package com.veylon.simulation;

/**
 * Cellular water tuning: the per-tick work budget and rain refill rate.
 *
 * <p>Extracted verbatim from {@link WaterSystem}; changing one is a gameplay
 * change, not a refactor.
 */
public final class WaterConstants {

    private WaterConstants() {
    }

    /**
     * Cells drained from the queue per medium tick. This is what stops a burst
     * flood from stalling the frame: excess work stays queued for later ticks
     * rather than running to completion in one.
     */
    public static final int BUDGET_PER_TICK = 80;

    /** Depressions the rain tries to fill per medium tick. */
    public static final int RAIN_FILL_ATTEMPTS = 4;
    /** Blocks either side of the player that rain refill samples. */
    public static final int RAIN_FILL_RADIUS = 32;
    /** Width of the sampled square, i.e. 2 * radius + 1. */
    public static final int RAIN_FILL_SPAN = 2 * RAIN_FILL_RADIUS + 1;
}
