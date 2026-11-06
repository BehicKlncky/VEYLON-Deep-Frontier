package com.veylon.ai;

import com.veylon.Game;
import com.veylon.UrbanFixtureArena;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.settlement.NpcArchetype;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.FixtureStates;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Breacher, urban milestone 15's infected variant, through production ticks on the package-04
 * fixture house: blind but sharp of hearing, it shoulders unlocked doors, hammers a locked or
 * barred wooden one down with loud blows, is held by a metal one, and strikes through the
 * player's damage gates. The Veilbound beside it in the same rooms keeps its own rules.
 */
class BreacherBehaviorTest {
    private static final float DT = .05f;

    static Npc spawn(Game g, NpcArchetype archetype, Vec3i cell) {
        Npc n = g.entities.spawnNpc(g.world, archetype.displayName, cell.x() + .5f, cell.y(), cell.z() + .5f);
        n.archetype = archetype;
        n.health = n.maxHealth = archetype.maxHealth;
        return n;
    }

    private static void run(Game g, float seconds) {
        for (int i = 0; i < Math.round(seconds / DT); i++) {
            g.entities.fastTick(g, DT);
            g.noise.update(DT);
        }
    }

    private static void lock(Game g, Vec3i door) {
        for (int y = door.y(); y <= door.y() + 1; y++) {
            assertTrue(g.world.fixtures.set(door.x(), y, door.z(), FixtureStates.LOCK_KEYED));
        }
        assertEquals(FixtureStates.LOCK_KEYED, g.world.fixtureLockAt(door.x(), door.y(), door.z()));
    }

    @Test
    void blindToASilentPlayerInPlainSightButItHearsFootsteps() {
        var arena = UrbanFixtureArena.create(GameMode.SURVIVAL);
        Game g = arena.game;
        var house = arena.house;
        Npc b = spawn(g, NpcArchetype.BREACHER, house.westRoom());
        arena.standAt(house.westRoom().offset(-2, 0, 2));
        assertFalse(InfectedAI.seesPlayer(g, b) && b.archetype.viewRange > 0, "it has no sight at all");
        run(g, 3f);
        assertEquals(0, b.searchTimer, "a still, silent player in the same room goes unnoticed");
        assertEquals(g.player.maxHealth, g.player.health);
        assertTrue(g.emitPlayerFootstepNoise(false, false));
        run(g, 0.5f);
        assertTrue(b.searchTimer > 0, "a footstep is enough");
        assertEquals(g.player.pos.x, b.lastKnown.x, 0.01f);
    }

    @Test
    void itHammersALockedWoodenDoorDownWithBlowsTheOthersCanHearAndAVeilboundWaits() {
        for (NpcArchetype archetype : new NpcArchetype[]{NpcArchetype.BREACHER, NpcArchetype.VEILBOUND}) {
            var arena = UrbanFixtureArena.create(GameMode.SURVIVAL);
            Game g = arena.game;
            var house = arena.house;
            Vec3i door = house.innerDoor();
            lock(g, door);
            Npc n = spawn(g, archetype, house.westRoom());
            arena.standAt(house.eastRoom().offset(2, 0, 0));
            int hammerEvents = 0;
            float firstBlow = -1, forcedAt = -1;
            for (int tick = 0; tick < 600 && forcedAt < 0; tick++) {
                if (tick % 40 == 0) {
                    // The player keeps moving about on the far side: real footstep events.
                    g.emitPlayerFootstepNoise(false, false);
                }
                g.entities.fastTick(g, DT);
                int hammering = g.noise.countCategory("door-hammer");
                if (hammering > 0 && firstBlow < 0) {
                    firstBlow = tick * DT;
                }
                hammerEvents = Math.max(hammerEvents, hammering);
                g.noise.update(DT);
                if (g.world.getBlock(door.x(), door.y(), door.z()) == BlockType.BROKEN_DOOR) {
                    forcedAt = tick * DT;
                }
            }
            if (archetype == NpcArchetype.VEILBOUND) {
                assertEquals(BlockType.WOOD_DOOR, g.world.getBlock(door.x(), door.y(), door.z()),
                        "a Veilbound stops at a locked door");
                assertEquals(0, hammerEvents);
                continue;
            }
            assertTrue(forcedAt > 0, "the Breacher forced the locked door");
            assertEquals(BlockType.BROKEN_DOOR, g.world.getBlock(door.x(), door.y() + 1, door.z()),
                    "the whole opening is broken, as a player's forced entry leaves it");
            assertEquals(FixtureStates.LOCK_NONE, g.world.fixtureLockAt(door.x(), door.y(), door.z()));
            assertTrue(hammerEvents >= 1, "its blows are heard");
            assertTrue(forcedAt - firstBlow >= (BreacherAI.BLOWS - 1) * BreacherAI.BLOW_SECONDS - 0.06f,
                    "a door holds for its blows: " + (forcedAt - firstBlow));
            run(g, 8f);
            assertTrue(n.pos.x > door.x() + 0.5f, "and it came through: " + n.pos);
        }
    }

    @Test
    void aLockedMetalDoorHoldsAgainstIt() {
        var arena = UrbanFixtureArena.create(GameMode.SURVIVAL);
        Game g = arena.game;
        var house = arena.house;
        Vec3i door = house.frontDoor();
        lock(g, door);
        Npc b = spawn(g, NpcArchetype.BREACHER, house.outsideFront());
        arena.standAt(house.westRoom());
        for (int tick = 0; tick < 400; tick++) {
            if (tick % 40 == 0) {
                g.emitPlayerFootstepNoise(true, false);
            }
            g.entities.fastTick(g, DT);
            g.noise.update(DT);
        }
        assertEquals(BlockType.METAL_DOOR, g.world.getBlock(door.x(), door.y(), door.z()));
        assertTrue(b.pos.z < door.z(), "still outside: " + b.pos);
        assertNull(b.breachDoor);
    }

    @Test
    void itsBlowGoesThroughTheDamageGatesAndCreativeForgetsItAtOnce() {
        for (GameMode mode : GameMode.values()) {
            var arena = UrbanFixtureArena.create(mode);
            Game g = arena.game;
            Npc b = spawn(g, NpcArchetype.BREACHER, arena.house.westRoom());
            arena.standAt(arena.house.westRoom().offset(1, 0, 0));
            float before = g.player.health;
            run(g, BreacherAI.WINDUP + 0.3f);
            if (mode == GameMode.CREATIVE) {
                assertEquals(-1, b.strikeWindup);
                assertEquals(before, g.player.health);
                assertEquals(0, b.searchTimer);
            } else {
                assertTrue(g.player.health < before, "touch is enough for a blind ram");
                assertEquals(before - b.archetype.meleeDamage, g.player.health, 0.6f);
            }
        }
    }
}
