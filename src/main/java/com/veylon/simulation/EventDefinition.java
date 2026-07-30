package com.veylon.simulation;

import com.veylon.Game;

/**
 * One rung of {@link EventSystem}'s selection ladder: the roll bound that can
 * select it, the season that widens that bound, the world state it requires,
 * and what happens when it fires.
 *
 * <p>Definitions are held in an ordered table and tested in order against a
 * single roll. The first definition whose bound and precondition both accept
 * the roll wins; a definition that rejects it hands the same roll to the next
 * one, which is what makes an ineligible event turn into the one above it
 * rather than into nothing. Order is therefore part of the odds — see
 * {@link EventSystem#slowTick(Game, float)}.
 *
 * @param type         the event this rung starts, used for the "not already
 *                     active" guard most preconditions apply
 * @param bound        cumulative upper bound on the roll, from
 *                     {@link EventConstants}; this rung's own share of the
 *                     probability space is the gap to the previous bound
 * @param biasSeason   season that widens {@code bound}, or null for a rung no
 *                     season favours
 * @param seasonBias   how much {@code biasSeason} adds to {@code bound}
 * @param precondition the complete world-state test, including the
 *                     already-active guard; nothing is left implicit
 * @param effect       what firing does, including starting the event itself
 */
record EventDefinition(EventSystem.EventType type, float bound,
                       SeasonSystem.Season biasSeason, float seasonBias,
                       EventDefinition.Precondition precondition,
                       EventDefinition.Effect effect) {

    /** Whether the world currently allows this event to start. Side-effect free. */
    @FunctionalInterface
    interface Precondition {
        boolean test(EventSystem events, Game g);
    }

    /**
     * What firing the event does. May legitimately do nothing: camp illness
     * finds its victim here rather than in the precondition, so an empty camp
     * consumes the roll instead of letting it fall through.
     */
    @FunctionalInterface
    interface Effect {
        void apply(EventSystem events, Game g);
    }

    /** A rung no season favours. */
    static EventDefinition of(EventSystem.EventType type, float bound,
                              Precondition precondition, Effect effect) {
        return new EventDefinition(type, bound, null, 0f, precondition, effect);
    }

    /** A rung a season widens, at the expense of whatever follows it. */
    static EventDefinition seasonal(EventSystem.EventType type, float bound,
                                    SeasonSystem.Season biasSeason, float seasonBias,
                                    Precondition precondition, Effect effect) {
        return new EventDefinition(type, bound, biasSeason, seasonBias, precondition, effect);
    }

    /** The roll bound in effect this season. */
    float boundIn(SeasonSystem.Season season) {
        return season == biasSeason ? bound + seasonBias : bound;
    }

    /** Whether this rung accepts {@code roll} in the current world and season. */
    boolean accepts(EventSystem events, Game g, float roll, SeasonSystem.Season season) {
        return roll < boundIn(season) && precondition.test(events, g);
    }
}
