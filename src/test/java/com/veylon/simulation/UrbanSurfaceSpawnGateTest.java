package com.veylon.simulation;

import com.veylon.Game;
import com.veylon.entity.GameMode;
import com.veylon.simulation.EventSystem.EventType;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Urban worlds place no wildlife and no event at the column top, which in a
 * city is a roof or a tree crown (decision D-28); legacy worlds keep both.
 */
class UrbanSurfaceSpawnGateTest {

    /** Events that put wolves, a trader or a meteor crater at {@code surfaceHeight}. */
    private static final Set<EventType> SURFACE_PLACED = EnumSet.of(EventType.PREDATOR_MIGRATION,
            EventType.TRADER_VISIT, EventType.METEOR_SHOWER, EventType.METEOR_SHARD,
            EventType.PREDATOR_RAID, EventType.SCAVENGER_RAID);
    private static final int ROLLS = 3_000;

    @Test
    void urbanWorldsSpawnNoWildlifeAndRollNoSurfacePlacedEvent() {
        for (int generator : new int[]{World.GEN_URBAN, World.GEN_URBAN_STREETS, World.GEN_URBAN_PLANS}) {
            Game g = new Game();
            g.newWorld(4_242L, true, generator, GameMode.SURVIVAL);
            assertTrue(g.entities.creatures.isEmpty(), "bootstrap populated a city with wildlife");
            for (int i = 0; i < 300; i++) {
                g.entities.slowTick(g);
            }
            assertEquals(0, g.entities.creatureCount(), "generator " + generator);

            Set<EventType> rolled = roll(g);
            for (EventType type : SURFACE_PLACED) {
                assertFalse(rolled.contains(type), type + " fired in an urban world");
            }
            assertTrue(rolled.contains(EventType.TOXIC_FOG) && rolled.contains(EventType.ASHFALL),
                    "weather-like events still happen in a city: " + rolled);
            assertTrue(g.entities.npcs.isEmpty() && g.entities.creatures.isEmpty());
            assertTrue(g.world.changedBlocks.isEmpty(), "no crater was carved");
        }
    }

    @Test
    void aMeteorShowerRestoredIntoACityDropsNoShard() {
        // A development save written mid-shower before the urban gates existed
        // restores the active event; its continuation must not carve the street.
        Game g = new Game();
        g.newWorld(4_242L, true, World.GEN_URBAN_STREETS, GameMode.SURVIVAL);
        g.events.active.add(new EventSystem.ActiveEvent(EventType.METEOR_SHOWER, 90f, 1f));
        for (int i = 0; i < 12; i++) {
            g.events.slowTick(g, 10f);
        }
        assertTrue(g.world.changedBlocks.isEmpty(), "a restored shower carved a crater");
        assertEquals(0, g.fire.count(), "and lit no impact fire");

        Game legacy = new Game();
        legacy.newWorld(4_242L, true, World.GEN_CAVE_IDENTITIES, GameMode.SURVIVAL);
        legacy.events.active.add(new EventSystem.ActiveEvent(EventType.METEOR_SHOWER, 90f, 1f));
        for (int i = 0; i < 12; i++) {
            legacy.events.slowTick(legacy, 10f);
        }
        assertFalse(legacy.world.changedBlocks.isEmpty(), "a legacy shower still drops shards");
    }

    @Test
    void legacyWorldsStillRollTheGatedEvents() {
        Game g = new Game();
        g.newWorld(4_242L, true, World.GEN_CAVE_IDENTITIES, GameMode.SURVIVAL);
        Set<EventType> rolled = roll(g);
        // The two gated rungs whose only other condition is "not already active".
        assertTrue(rolled.containsAll(EnumSet.of(EventType.PREDATOR_MIGRATION,
                EventType.METEOR_SHOWER)), "legacy events: " + rolled);
    }

    @Test
    void campIllnessNeverChoosesAVeilboundInACity() {
        // An infected encounter is an NPC, but never a camp resident needing medicine.
        Game g = com.veylon.UrbanEncounterArena.session(7);
        var infected = com.veylon.UrbanEncounterArena.activate(g, false);
        int logged = g.eventLog.all().size();
        illness().effect().apply(g.events, g);
        assertFalse(infected.sick);
        assertFalse(g.events.isActive(EventType.NPC_ILLNESS));
        assertEquals(logged, g.eventLog.all().size(), "no camp-medicine message in a city without a camp");

        Game legacy = new Game();
        legacy.newWorld(4_242L, true, World.GEN_CAVE_IDENTITIES, GameMode.SURVIVAL);
        var resident = legacy.entities.npcs.stream().filter(n -> !n.isTrader && !n.raider).findFirst().orElseThrow();
        illness().effect().apply(legacy.events, legacy);
        assertTrue(resident.sick, "legacy camp residents still fall ill");
        assertTrue(legacy.events.isActive(EventType.NPC_ILLNESS));
    }

    private static EventDefinition illness() {
        return EventSystem.definitions().stream()
                .filter(d -> d.type() == EventType.NPC_ILLNESS).findFirst().orElseThrow();
    }

    private static Set<EventType> roll(Game g) {
        Set<EventType> rolled = EnumSet.noneOf(EventType.class);
        for (int i = 0; i < ROLLS; i++) {
            g.events.reset();
            g.time.reset();
            g.events.setRandomSeed(0x9e37_79b9_7f4a_7c15L * (i + 1));
            // Drains the cooldown so this slow tick performs a selection roll.
            g.events.slowTick(g, 5_000f);
            for (EventSystem.ActiveEvent event : g.events.active) {
                rolled.add(event.type);
            }
        }
        return rolled;
    }
}
