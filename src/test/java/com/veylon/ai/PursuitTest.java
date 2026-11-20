package com.veylon.ai;

import com.veylon.Game;
import com.veylon.entity.Creature;
import com.veylon.entity.Creature.CreatureState;
import com.veylon.entity.Creature.CreatureType;
import com.veylon.entity.Npc;
import com.veylon.entity.Npc.NpcState;
import com.veylon.settlement.NpcArchetype;
import com.veylon.util.Vec3i;
import com.veylon.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What counts as going for the player (0.9.1 milestone 05): only the states the brains already
 * keep, within {@link Pursuit#RADIUS} blocks. The bodies are placed and given a state; no AI runs,
 * so each answer is the rule's own.
 */
class PursuitTest {

    private Game g;

    @BeforeEach
    void wilderness() {
        g = new Game();
        g.newWorld(20_261_120L, true, World.WILDERNESS_NEW_WORLD_GENERATOR);
        g.entities.creatures.clear();
        g.entities.npcs.clear();
    }

    private Creature creature(CreatureType type, float dx, CreatureState state) {
        Creature c = g.entities.spawnCreature(g.world, type, g.player.pos.x + dx, g.player.pos.y, g.player.pos.z);
        c.state = state;
        return c;
    }

    private Npc person(float dx) {
        Npc n = g.entities.spawnNpc(g.world, "Scav", g.player.pos.x + dx, g.player.pos.y, g.player.pos.z);
        n.state = NpcState.IDLE;
        n.searchTimer = 0;
        return n;
    }

    private Npc infected(float dx) {
        Npc n = person(dx);
        n.archetype = NpcArchetype.VEILBOUND;
        return n;
    }

    @Test
    void nothingAroundMeansNothingPursues() {
        assertFalse(Pursuit.hunted(g));
        creature(CreatureType.DEER, 5, CreatureState.GRAZE);
        person(5);
        assertFalse(Pursuit.hunted(g), "a grazing deer and an idle person are after nobody");
    }

    @Test
    void aStalkOrAChargeIsAlwaysOnThePlayerWithinTheRadius() {
        Creature wolf = creature(CreatureType.WOLF, 20, CreatureState.STALK);
        assertTrue(Pursuit.pursues(g, wolf));
        Creature thornhorn = creature(CreatureType.THORNHORN, 30, CreatureState.CHARGE);
        assertTrue(Pursuit.pursues(g, thornhorn));
        Creature far = creature(CreatureType.WOLF, Pursuit.RADIUS + 4, CreatureState.STALK);
        assertFalse(Pursuit.pursues(g, far), "beyond the radius it cannot reach the player soon");
        wolf.dead = true;
        assertFalse(Pursuit.pursues(g, wolf), "the dead pursue nothing");
    }

    @Test
    void aHuntOrAnAttackMayBeOnPreySoItCountsOnlyClose() {
        Creature onPrey = creature(CreatureType.WOLF, 20, CreatureState.HUNT);
        assertFalse(Pursuit.pursues(g, onPrey), "a wolf hunting a deer twenty blocks off");
        Creature close = creature(CreatureType.STALKER, 4, CreatureState.HUNT);
        assertTrue(Pursuit.pursues(g, close), "a Gloomstalker's ambush burst");
        Creature biting = creature(CreatureType.WOLF, 1.5f, CreatureState.ATTACK);
        assertTrue(Pursuit.pursues(g, biting));
        Creature tracking = creature(CreatureType.WOLF, 3, CreatureState.TRACK);
        assertFalse(Pursuit.pursues(g, tracking), "tracking is following a meal's trail");
    }

    @Test
    void aVeilmawCountsWhileItStirsOrLungesAtThePlayer() {
        Creature maw = creature(CreatureType.VEILMAW, 8, CreatureState.STIRRING);
        maw.targetIsPlayer = false;
        assertFalse(Pursuit.pursues(g, maw), "stirring at a lamp or a deer");
        maw.targetIsPlayer = true;
        assertTrue(Pursuit.pursues(g, maw));
        maw.state = CreatureState.LUNGING;
        assertTrue(Pursuit.pursues(g, maw));
        maw.state = CreatureState.RECOILING;
        assertFalse(Pursuit.pursues(g, maw));
    }

    @Test
    void aPersonGoesForThePlayerAsTheCitySoundscapeJudgesIt() {
        Npc guard = person(10);
        guard.combatTarget = g.player;
        assertTrue(Pursuit.goingFor(g, guard) && Pursuit.pursues(g, guard), "the player is their target");

        Npc raider = person(10);
        raider.state = NpcState.ATTACK;
        assertTrue(Pursuit.pursues(g, raider), "attacking with no target of their own");
        raider.combatTarget = guard;
        assertFalse(Pursuit.pursues(g, raider), "fighting someone else");

        Npc searching = person(25);
        searching.searchTimer = 6;
        assertTrue(Pursuit.pursues(g, searching), "still searching for the player");

        Npc travelling = person(10);
        travelling.combatTarget = g.player;
        travelling.abstractTravel = true;
        assertFalse(Pursuit.pursues(g, travelling), "a party moving off the map is not here");
    }

    @Test
    void anInfectedWindingUpOrBreakingThroughIsPursuing() {
        Npc body = infected(2);
        assertFalse(Pursuit.pursues(g, body), "an infected that has noticed nothing");
        body.strikeWindup = 0.3f;
        assertTrue(Pursuit.pursues(g, body), "winding up a strike at the player");
        body.combatTarget = person(3);
        assertFalse(Pursuit.pursues(g, body), "winding up at a guard who is fighting it");
        body.strikeWindup = -1;
        body.combatTarget = null;
        body.breachDoor = new Vec3i(0, 0, 0);
        assertTrue(Pursuit.pursues(g, body), "breaking a door down toward the player");
    }

    @Test
    void forgettingThePlayerEndsEveryPursuit() {
        creature(CreatureType.WOLF, 12, CreatureState.STALK);
        Npc searching = person(12);
        searching.searchTimer = 10;
        Npc guard = person(8);
        guard.combatTarget = g.player;
        assertTrue(Pursuit.hunted(g));
        PlayerAwareness.forgetPlayer(g); // what becoming imperceptible does
        assertFalse(Pursuit.hunted(g), "nothing pursues an imperceptible player");
    }

    @Test
    void aDeadPlayerIsHuntedByNothing() {
        creature(CreatureType.WOLF, 5, CreatureState.STALK);
        assertTrue(Pursuit.hunted(g));
        g.player.dead = true;
        assertFalse(Pursuit.hunted(g));
    }
}
