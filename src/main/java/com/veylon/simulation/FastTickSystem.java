package com.veylon.simulation;

import com.veylon.Game;

/**
 * A system driven at {@link SimulationScheduler#FAST_DT} (20 Hz): everything
 * the player can perceive frame to frame, such as entity movement and needs.
 *
 * <p>Fast ticks are drained before the coarser buckets in the same update, so
 * a medium or slow system always reads state that is current.
 */
public interface FastTickSystem extends SimulationSystem {

    void fastTick(Game g, float dt);
}
