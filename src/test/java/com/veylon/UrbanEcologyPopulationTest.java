package com.veylon;

import com.veylon.entity.Creature;
import com.veylon.entity.Creature.CreatureState;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.entity.UrbanEncounterDirector;
import com.veylon.qa.RuntimeBudgetSnapshot;
import com.veylon.settlement.NpcArchetype;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.world.BlockType;
import com.veylon.world.urban.EcologySites;
import com.veylon.world.urban.EncounterAnchors;
import com.veylon.world.urban.UrbanSpecies;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Encounter catalogue five on generated generator-14 worlds (urban milestone 15): the production
 * gate admits the quarantine's Veilmaws, the city's wildlife and the Breacher from their planned
 * anchors, under their own caps; dormancy keeps injuries; deaths are permanent; a Veilmaw whose
 * node is gone withers instead of returning; wildlife keeps to its habitat and hunting it is finite.
 */
class UrbanEcologyPopulationTest {
    private static final float DT = SimulationScheduler.FAST_DT;

    @Test
    void aGrowthSiteAdmitsItsVeilmawFoldedBesideItsNodeWithItsTerritory() {
        Game g = EcologyWorldArena.session(EcologyWorldArena.SEED, GameMode.SURVIVAL);
        var a = EcologyWorldArena.first(g, UrbanSpecies.VEILMAW);
        EcologyWorldArena.observeAway(g, a);
        Creature c = g.entities.encounters.tryActivateCreature(g, a);
        assertNotNull(c, "the planned Veilmaw appears");
        assertEquals(Creature.CreatureType.VEILMAW, c.type);
        assertEquals(CreatureState.FOLDED, c.state);
        assertEquals(a.id(), c.encounterId);
        assertNotNull(c.home);
        assertNotNull(c.home.node());
        assertSame(BlockType.GROWTH_NODE, g.world.getBlock(c.home.node().x(), c.home.node().y(), c.home.node().z()));
        assertEquals(a.cell().x() + .5f, c.pos.x);
        assertEquals(a.cell().y(), c.pos.y);
        var r = g.entities.encounters.resident(a.id());
        assertEquals(UrbanSpecies.VEILMAW, r.species);
        assertSame(c, r.creature);
        assertNull(g.entities.encounters.tryActivateCreature(g, a), "one body per record");
        assertNull(g.entities.encounters.tryActivate(g, a), "the humanoid gate never admits a creature");
    }

    @Test
    void everyKindKeepsItsOwnCapAndTheScanStaysBounded() {
        Game g = EcologyWorldArena.session(EcologyWorldArena.SEED, GameMode.SURVIVAL);
        var director = g.entities.encounters;
        for (var a : EcologyWorldArena.anchors(g)) {
            EcologyWorldArena.observeAway(g, a);
            director.activate(g, a);
            assertTrue(director.activeAliens(g) <= UrbanEncounterDirector.MAX_ACTIVE_ALIENS);
            assertTrue(director.activeWildlife(g) <= UrbanEncounterDirector.MAX_ACTIVE_WILDLIFE);
            assertTrue(director.activeCount(g) <= UrbanEncounterDirector.MAX_ACTIVE);
        }
        assertTrue(director.activeAliens(g) > 0 && director.activeWildlife(g) > 0 && director.activeCount(g) > 0);
        assertTrue(RuntimeBudgetSnapshot.capture(g).withinHardLimits(), RuntimeBudgetSnapshot.capture(g).occupancySummary());
        Game fresh = EcologyWorldArena.session(EcologyWorldArena.SEED, GameMode.SURVIVAL);
        var a = EcologyWorldArena.first(fresh, UrbanSpecies.VEILMAW);
        EcologyWorldArena.observeAway(fresh, a);
        for (int i = 0; i < 30; i++) {
            fresh.entities.encounters.slowTick(fresh);
            assertTrue(fresh.entities.encounters.lastParcels <= UrbanEncounterDirector.MAX_PARCELS_PER_TICK);
            assertTrue(fresh.entities.encounters.lastSpawns <= UrbanEncounterDirector.MAX_SPAWNS_PER_TICK);
            assertTrue(fresh.entities.encounters.lastSnapshots <= UrbanEncounterDirector.MAX_SNAPSHOTS_PER_TICK);
        }
        assertTrue(fresh.entities.encounters.activeAliens(fresh) > 0, "normal slow ticks admit growth-site Veilmaws");
    }

    @Test
    void dormancyKeepsInjuryDeathIsPermanentAndNothingReplacesIt() {
        Game g = EcologyWorldArena.session(EcologyWorldArena.SEED, GameMode.SURVIVAL);
        var a = EcologyWorldArena.first(g, UrbanSpecies.VEILMAW);
        EcologyWorldArena.observeAway(g, a);
        Creature c = g.entities.encounters.tryActivateCreature(g, a);
        c.hurt(10, true);
        g.player.pos.x += 300;
        g.entities.encounters.slowTick(g);
        assertFalse(g.entities.creatures.contains(c), "far away it goes dormant");
        var r = g.entities.encounters.resident(a.id());
        assertNull(r.creature);
        assertEquals(24f, r.health);
        EcologyWorldArena.observeAway(g, a);
        Creature back = g.entities.encounters.tryActivateCreature(g, a);
        assertNotNull(back);
        assertEquals(24f, back.health, "no healing while dormant");
        back.hurt(100, true);
        g.entities.fastTick(g, DT);
        assertTrue(r.dead(), "a death is a tombstone");
        assertNull(g.entities.encounters.tryActivateCreature(g, a));
        int records = g.entities.encounters.size();
        for (int i = 0; i < 10; i++) { g.entities.encounters.slowTick(g); }
        assertTrue(g.entities.creatures.stream().noneMatch(x -> x.encounterId == a.id()), "never respawned");
        assertTrue(g.entities.encounters.size() >= records);
    }

    @Test
    void aVeilmawWhoseNodeIsGoneWithersAndNeverReturnsWhetherActiveOrNot() {
        Game g = EcologyWorldArena.session(EcologyWorldArena.SEED, GameMode.SURVIVAL);
        var veilmaws = EcologyWorldArena.anchors(g, x -> x.species() == UrbanSpecies.VEILMAW);
        assertTrue(veilmaws.size() >= 2);
        // Active: its node is broken out from under it.
        var a = veilmaws.get(0);
        EcologyWorldArena.observeAway(g, a);
        Creature c = g.entities.encounters.tryActivateCreature(g, a);
        var node = c.home.node();
        g.world.setBlock(node.x(), node.y(), node.z(), BlockType.AIR, true);
        for (int i = 0; i < 20 && !c.dead; i++) { g.entities.fastTick(g, DT); }
        assertTrue(c.dead);
        g.entities.fastTick(g, DT);
        assertTrue(g.entities.encounters.resident(a.id()).dead());
        // Never admitted yet: the site's node is gone first. Its record is created as a tombstone.
        var b = veilmaws.get(1);
        EcologyWorldArena.observeAway(g, b);
        var otherNode = b.habitat().node();
        g.world.setBlock(otherNode.x(), otherNode.y(), otherNode.z(), BlockType.ASH, true);
        assertNull(g.entities.encounters.tryActivateCreature(g, b));
        assertNotNull(g.entities.encounters.resident(b.id()));
        assertTrue(g.entities.encounters.resident(b.id()).dead(), "explicit migration: withered, not respawned");
    }

    @Test
    void wildlifeKeepsToItsHabitatAndHuntingItIsFinite() {
        Game g = EcologyWorldArena.session(EcologyWorldArena.SEED, GameMode.SURVIVAL);
        var a = EcologyWorldArena.first(g, UrbanSpecies.MURKHARE);
        EcologyWorldArena.observeAway(g, a);
        Creature hare = g.entities.encounters.tryActivateCreature(g, a);
        assertNotNull(hare);
        assertEquals(Creature.CreatureType.HARE, hare.type);
        // The player strolls through its habitat, startling it, for a minute.
        float worst = 0;
        for (int i = 0; i < 1200; i++) {
            g.player.pos.set(hare.pos.x + 3, hare.pos.y, hare.pos.z + (i % 200 < 100 ? 2 : -2));
            g.entities.fastTick(g, DT);
            worst = Math.max(worst, hare.home.outside(hare.pos.x, hare.pos.z));
        }
        assertTrue(worst <= 1.5f, "it stays in its park or courtyard: " + worst);
        hare.hurt(100, true);
        g.entities.fastTick(g, DT);
        for (int i = 0; i < 200 && g.entities.carcasses.isEmpty(); i++) { g.ragdolls.update(g, DT); }
        assertFalse(g.entities.carcasses.isEmpty(), "a hunted hare leaves its carcass");
        assertEquals(Creature.CreatureType.HARE.meatYield, g.entities.carcasses.getFirst().meatLeft);
        assertTrue(g.entities.encounters.resident(a.id()).dead());
        EcologyWorldArena.observeAway(g, a);
        assertNull(g.entities.encounters.tryActivateCreature(g, a), "and it never comes back");
    }

    @Test
    void theBreacherAppearsFromItsPlannedStreetSlotAndCreativePausesEveryKind() {
        Game g = EcologyWorldArena.session(EcologyWorldArena.SEED, GameMode.SURVIVAL);
        var a = EcologyWorldArena.first(g, UrbanSpecies.BREACHER);
        EcologyWorldArena.observeAway(g, a);
        Npc b = g.entities.encounters.tryActivate(g, a);
        assertNotNull(b);
        assertEquals(NpcArchetype.BREACHER, b.archetype);
        assertEquals(44f, b.maxHealth);
        assertTrue(b.infected() && !b.settled());
        assertEquals(UrbanSpecies.BREACHER, g.entities.encounters.resident(a.id()).species);

        Game c = EcologyWorldArena.session(EcologyWorldArena.SEED, GameMode.SURVIVAL);
        c.gameModes.switchTo(GameMode.CREATIVE);
        c.creativeControls.toggleSpawning();
        assertTrue(c.spawningPaused());
        for (UrbanSpecies species : new UrbanSpecies[]{UrbanSpecies.VEILMAW, UrbanSpecies.MURKHARE, UrbanSpecies.BREACHER}) {
            var anchor = EcologyWorldArena.first(c, species);
            EcologyWorldArena.observeAway(c, anchor);
            assertNull(c.entities.encounters.activate(c, anchor), species + " waits while spawning is paused");
        }
        c.entities.encounters.slowTick(c);
        assertEquals(0, c.entities.encounters.activeAliens(c) + c.entities.encounters.activeWildlife(c));
        assertEquals(EcologySites.ROOM_GROWTH, com.veylon.world.urban.UrbanIds.roomIndex(
                EcologyWorldArena.first(c, UrbanSpecies.VEILMAW).id()));
        assertEquals(EncounterAnchors.catalogue(c.world), 5);
    }
}
