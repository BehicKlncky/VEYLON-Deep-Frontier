package com.veylon.simulation;

import com.veylon.Game;

/**
 * A system driven at {@link SimulationScheduler#MEDIUM_DT} (2 Hz): environment
 * that changes visibly but not per frame, such as weather, temperature, water
 * flow and fire spread.
 */
public interface MediumTickSystem extends SimulationSystem {

    void mediumTick(Game g, float dt);
}
