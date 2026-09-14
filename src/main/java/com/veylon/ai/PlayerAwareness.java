package com.veylon.ai;

import com.veylon.Game;
import com.veylon.entity.Creature;
import com.veylon.entity.Creature.CreatureState;
import com.veylon.entity.Npc;
import com.veylon.entity.Npc.NpcState;

import java.util.EnumSet;
import java.util.Set;

/**
 * Retires what wildlife and settlers remember about the player when the player
 * becomes imperceptible (R4, R11). Perception gates stop new observations; this
 * one method clears the old ones, so a chase, charge, search or alarm run that
 * began in Survival cannot continue on the next tick.
 *
 * <p>World state that does not depend on perception is kept (D3): reputation,
 * settlement alert decay, ownership, quests, dead residents and counterattack
 * missions, which target settlements rather than the player. A scout party's
 * earlier contact still reports home. Nothing here draws a random number or
 * outlives the world it runs in.
 */
public final class PlayerAwareness {

    /** Creature states that can aim at, flee from or follow the player; all re-derive next tick. */
    private static final Set<CreatureState> PURSUIT_STATES = EnumSet.of(
            CreatureState.FLEE, CreatureState.FLEE_HURT, CreatureState.TRACK,
            CreatureState.HUNT, CreatureState.STALK, CreatureState.ATTACK, CreatureState.CHARGE);
    /** The same "never seen" age settlers spawn with. */
    private static final float FORGOTTEN_AGE = 999f;
    /** Return window used when a patrol's own search expires. */
    private static final float RETURN_TIMER_SECONDS = 180f;
    /** patrolIndex marker for a scout running to ring the alarm bell. */
    private static final int ALARM_RUN_MARKER = -2;

    private PlayerAwareness() {
    }

    public static void forgetPlayer(Game g) {
        for (Creature c : g.entities.creatures) {
            if (PURSUIT_STATES.contains(c.state)) {
                c.state = CreatureState.WANDER;
                c.hasTarget = false;
                c.targetEntity = null;
                Steering.stop(c);
            }
        }
        for (Npc n : g.entities.npcs) {
            n.lastKnownAge = FORGOTTEN_AGE;
            n.searchTimer = 0;
            if (n.combatTarget == g.player) {
                n.combatTarget = null;
            }
            if (n.state == NpcState.ATTACK) {
                n.state = NpcState.IDLE;
                n.path = null;
                Steering.stop(n);
            }
            if (n.patrolIndex == ALARM_RUN_MARKER) {
                n.patrolIndex = 0;
            }
            boolean followsPlayer = n.partyKind == Npc.PartyKind.BOUNTY_HUNTER || n.partyContact;
            if (n.warParty && followsPlayer && n.partyMission != Npc.PartyMission.RETURNING) {
                n.partyMission = Npc.PartyMission.RETURNING;
                n.partyMissionTimer = RETURN_TIMER_SECONDS;
                n.path = null;
            }
        }
        g.noise.forgetPlayerSources();
        if (g.player != null) {
            g.player.noise = 0;
            g.player.scent = 0;
        }
    }
}
