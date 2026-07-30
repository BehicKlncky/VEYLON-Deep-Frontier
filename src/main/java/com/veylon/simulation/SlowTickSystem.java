package com.veylon.simulation;

import com.veylon.Game;

/**
 * A system driven at {@link SimulationScheduler#SLOW_DT} (once per 10 s): the
 * long-horizon world, such as plant growth, spoilage, world events and
 * settlement life.
 *
 * <p>Slow systems receive a large {@code dt} and are expected to be written for
 * it — a slow tick is one coarse step, not a replay of 200 fast ones.
 */
public interface SlowTickSystem extends SimulationSystem {

    void slowTick(Game g, float dt);
}
