package com.veylon.ai;

import com.veylon.Game;
import com.veylon.entity.Creature;
import com.veylon.entity.Npc;
import com.veylon.entity.Npc.NpcState;
import com.veylon.entity.Player;

/**
 * Whether anything is going for the player right now, read from the states the brains already keep
 * (0.9.1 milestone 05). Nothing here perceives: a body counts only while its own AI is chasing,
 * stalking, charging, searching for, winding up at or attacking the player, and within
 * {@value #RADIUS} blocks of them. {@link PlayerAwareness#forgetPlayer} clears every one of these
 * states when the player becomes imperceptible, so an unseen player is pursued by nothing.
 *
 * <ul>
 *   <li><b>People</b> go for the player as the city soundscape has always judged it: the player is
 *       their combat target, or they attack with no target of their own, as a stronghold's guard or
 *       a raider hunts. A person still searching ({@code searchTimer}) is pursuing too: every brain
 *       starts that search from the player, and an infected from what it heard.</li>
 *   <li><b>The infected</b> also pursue while winding up a strike at the player or breaking through
 *       a door toward them.</li>
 *   <li><b>Creatures</b> stalk ({@code STALK}) and charge ({@code CHARGE}) only the player. A hunt or
 *       an attack may be on prey, so it counts within {@value #CLOSE} blocks, where a wolf or a
 *       Gloomstalker on the player is. A Veilmaw counts while it stirs or lunges at the player.</li>
 * </ul>
 */
public final class Pursuit {

    /** Blocks within which a pursuer counts; one farther away cannot reach the player soon. */
    public static final float RADIUS = 48f;
    /** Blocks within which a creature's hunt or attack is taken to be on the player. */
    public static final float CLOSE = 6f;

    private Pursuit() {
    }

    /** Whether any living person or creature is going for the player. */
    public static boolean hunted(Game g) {
        Player p = g.player;
        if (p == null || p.dead) {
            return false;
        }
        for (Npc n : g.entities.npcs) {
            if (pursues(g, n)) {
                return true;
            }
        }
        for (Creature c : g.entities.creatures) {
            if (pursues(g, c)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A person going for the player: their combat target is the player, or they are attacking with
     * no target of their own. The city's soundscape barks for exactly these.
     */
    public static boolean goingFor(Game g, Npc n) {
        return n.combatTarget == g.player || n.state == NpcState.ATTACK && n.combatTarget == null;
    }

    /** Whether one person or infected within {@value #RADIUS} blocks is pursuing the player. */
    public static boolean pursues(Game g, Npc n) {
        if (n.dead || n.abstractTravel || n.distSqTo(g.player) > RADIUS * RADIUS) {
            return false;
        }
        if (goingFor(g, n) || n.searchTimer > 0) {
            return true;
        }
        return n.infected() && (n.strikeWindup >= 0 && !(n.combatTarget instanceof Npc) || n.breachDoor != null);
    }

    /** Whether one creature within {@value #RADIUS} blocks is pursuing the player. */
    public static boolean pursues(Game g, Creature c) {
        if (c.dead) {
            return false;
        }
        double d2 = c.distSqTo(g.player);
        if (d2 > RADIUS * RADIUS) {
            return false;
        }
        return switch (c.state) {
            case STALK, CHARGE -> true;
            case HUNT, ATTACK -> d2 <= CLOSE * CLOSE;
            case STIRRING, LUNGING -> c.targetIsPlayer;
            default -> false;
        };
    }
}
