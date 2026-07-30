package com.veylon.simulation;

/**
 * Common interface for all simulation systems.
 * Enables uniform tick management and testing.
 *
 * <p>The contract is deliberately narrow: a simulation system owns state that
 * belongs to one world, and must be able to drop it. Anything a system caches
 * about the world — queues, grids, per-world counters — has to be cleared here,
 * because {@code Game.newWorld} reuses the same system instances for every
 * world in the process. State that leaks across that boundary is the classic
 * source of "the second world I load behaves oddly" bugs.
 *
 * <p>Systems declare their cadence by implementing one of the sub-interfaces —
 * {@link FastTickSystem}, {@link MediumTickSystem} or {@link SlowTickSystem} —
 * and a system may implement more than one when it genuinely has work at
 * several rates. Pure query helpers deliberately do not implement this at all:
 * {@link SeasonSystem} derives everything from {@link TimeSystem} and
 * {@link ShelterSystem} is a stateless scan, so neither has anything to reset.
 * {@link SimulationScheduler} is the driver rather than a system, and keeps its
 * own {@link SimulationScheduler.Ticks} callback.
 */
public interface SimulationSystem {

    /** Reset the system for a new world or load. */
    void reset();
}
