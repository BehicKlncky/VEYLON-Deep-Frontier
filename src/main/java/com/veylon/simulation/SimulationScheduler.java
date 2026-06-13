package com.veylon.simulation;

/**
 * Layered tick scheduler: fast (20 Hz), medium (2 Hz) and slow (every 10 s)
 * buckets so expensive systems never run per frame.
 */
public class SimulationScheduler {

    public static final float FAST_DT = 1f / 20f;
    public static final float MEDIUM_DT = 0.5f;
    public static final float SLOW_DT = 10f;

    public interface Ticks {
        void fastTick(float dt);

        void mediumTick(float dt);

        void slowTick(float dt);
    }

    private double fastAcc, mediumAcc, slowAcc;

    public void update(double frameDt, Ticks ticks) {
        // Clamp to avoid a spiral of death after stalls (window drag, GC, etc.).
        frameDt = Math.min(frameDt, 0.25);
        fastAcc += frameDt;
        mediumAcc += frameDt;
        slowAcc += frameDt;

        int guard = 0;
        while (fastAcc >= FAST_DT && guard++ < 10) {
            fastAcc -= FAST_DT;
            ticks.fastTick(FAST_DT);
        }
        if (mediumAcc >= MEDIUM_DT) {
            mediumAcc -= MEDIUM_DT;
            if (mediumAcc > MEDIUM_DT) {
                mediumAcc = 0;
            }
            ticks.mediumTick(MEDIUM_DT);
        }
        if (slowAcc >= SLOW_DT) {
            slowAcc -= SLOW_DT;
            if (slowAcc > SLOW_DT) {
                slowAcc = 0;
            }
            ticks.slowTick(SLOW_DT);
        }
    }
}
