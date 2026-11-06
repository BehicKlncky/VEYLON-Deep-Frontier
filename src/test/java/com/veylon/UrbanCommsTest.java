package com.veylon;

import com.veylon.engine.AudioManager;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.settlement.UrbanComms;
import com.veylon.settlement.UrbanSite;
import com.veylon.util.Vec3i;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingFurnishing;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.CommsComponents;
import com.veylon.world.urban.CompoundSites;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.RelaySites;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanMissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The communications objective of a layout-sixteen city (urban milestone 20), learned and gathered
 * for through the game's own seams: a survivor speaks of the relay, never naming the building; the
 * relay found by standing on its roof counts as hearing of it; each kind of part lies in the first
 * roll of its real source containers and is recovered when taken; the codes come from a friendly
 * stronghold or trusting hidden civilians, and nobody else. Earlier cities have no radio to ask about.
 */
class UrbanCommsTest {
    static final long SEED = SurvivorWorldArena.SEED;

    static Game session(GameMode mode) {
        return SurvivorWorldArena.session(SEED, mode, World.GEN_URBAN_MISSIONS, new AudioManager());
    }

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"SURVIVAL", "CREATIVE"})
    void aSurvivorSpeaksOfTheRelayButNeverNamesItsBuilding(GameMode mode) {
        Game g = session(mode);
        assertEquals("Comms / ask survivors about the old radio relay", UrbanComms.hudLine(g));
        assertNull(UrbanComms.target(g), "no marker before anyone speaks of it");
        SurvivorSites.Site plan = SurvivorWorldArena.nearest(g, SurvivorSites.Kind.CAMP);
        SurvivorWorldArena.standAt(g, plan.entry());
        SurvivorWorldArena.slowTick(g);
        UrbanSite camp = SurvivorWorldArena.site(g, plan);
        Npc lookout = camp.resident(0).live;
        assertTrue(g.npcScreen.siteOptions(g, lookout).contains("[9] Ask about the old radio relay"));
        assertTrue(g.npcScreen.performSiteChoice(g, lookout, 9));
        String said = g.npcScreen.lastResponse();
        RelaySites.Site relay = g.world.urban.relay();
        UrbanMissions.Comms c = g.world.urban.missions.comms;
        assertTrue(c.rumoured && c.rumourFrom == camp.id, said);
        assertTrue(said.contains("Civic Precinct"), said);
        assertFalse(said.contains(UrbanIds.format(relay.building())), "never the building: " + said);
        assertFalse(c.relayFound, "a rumour is not a find");
        UrbanComms.Target t = UrbanComms.target(g);
        assertNotNull(t);
        assertFalse(t.exact(), "a rumour marks the district, not the roof");
        assertTrue(Math.abs(t.position().x() - relay.relay().x()) < 256 && Math.abs(t.position().z() - relay.relay().z()) < 256);
        assertEquals("Comms / recover the relay's parts: 0 of 3", UrbanComms.hudLine(g));
        // Asked again, they speak of the parts instead of repeating the rumour; nothing is learned twice.
        assertTrue(g.npcScreen.performSiteChoice(g, lookout, 9));
        assertTrue(g.npcScreen.lastResponse().contains("transmitter"), g.npcScreen.lastResponse());
        assertEquals(camp.id, c.rumourFrom);
    }

    @Test
    void standingAtTheRelayFindsItAndAMarkerFarAwayDoesNot() {
        Game g = session(GameMode.SURVIVAL);
        RelaySites.Site relay = g.world.urban.relay();
        // The roof of another building of the same precinct finds nothing.
        SurvivorWorldArena.standAt(g, relay.stand().offset(12, 0, 0));
        UrbanComms.mediumTick(g, 0.25f);
        assertFalse(g.world.urban.missions.comms.relayFound);
        // Below the cabinet, on the storey under the roof, nothing either: the body must be up there.
        SurvivorWorldArena.standAt(g, relay.stand().offset(0, -4, 0));
        UrbanComms.mediumTick(g, 0.25f);
        assertFalse(g.world.urban.missions.comms.relayFound, "a storey below the roof is not on it");
        SurvivorWorldArena.standAt(g, relay.stand());
        UrbanComms.mediumTick(g, 0.25f);
        UrbanMissions.Comms c = g.world.urban.missions.comms;
        assertTrue(c.relayFound && c.rumoured, "standing at the relay counts as hearing of it");
        assertEquals(UrbanIds.NONE, c.rumourFrom, "nobody told the survivor");
        assertTrue(UrbanComms.target(g).exact());
        assertEquals(relay.relay(), UrbanComms.target(g).position());
    }

    @Test
    void eachPartLiesInTheFirstRollOfItsRealSourcesAndIsRecoveredWhenTaken() {
        Game g = session(GameMode.SURVIVAL);
        Source module = source(g, BuildingPlan.Family.SECURITY, BuildingFurnishing.ContainerUse.ARMORY_LOCKER, false);
        Source cells = source(g, BuildingPlan.Family.WAREHOUSE, BuildingFurnishing.ContainerUse.TOOL_CHEST, false);
        Source clinic = source(g, BuildingPlan.Family.CLINIC, BuildingFurnishing.ContainerUse.UTILITY_LOCKER, false);
        Source cable = source(g, BuildingPlan.Family.OFFICE, BuildingFurnishing.ContainerUse.UTILITY_LOCKER, false);
        assertEquals(CommsComponents.Kind.TRANSMITTER, CommsComponents.sourceOf(g.world.urban, module.b, module.f));
        assertEquals(CommsComponents.Kind.CELLS, CommsComponents.sourceOf(g.world.urban, cells.b, cells.f));
        assertEquals(CommsComponents.Kind.CELLS, CommsComponents.sourceOf(g.world.urban, clinic.b, clinic.f));
        assertEquals(CommsComponents.Kind.ANTENNA, CommsComponents.sourceOf(g.world.urban, cable.b, cable.f));
        for (Source s : new Source[]{module, cells, cable}) {
            int before = g.player.inventory.count(s.kind().item);
            take(g, s);
            assertEquals(before + 1, g.player.inventory.count(s.kind().item), "the part was in its first roll: " + s);
        }
        UrbanMissions.Comms c = g.world.urban.missions.comms;
        assertEquals(CommsComponents.ALL, c.recovered, "every kind recovered");
        assertEquals(3, c.partsRecovered());
        // Nothing is put back: the emptied container holds no second part on a second search.
        g.openCrateAt(module.f.position());
        SurvivorWorldArena.run(g, 4f);
        Inventory again = g.world.crateContents.get(module.f.position());
        assertTrue(again == null || again.count(ItemType.TRANSMITTER_MODULE) == 0, "no refill");
        g.closeScreens();
        // Only the first container of its use holds the part.
        BuildingPlan.Fixture second = second(cells.b, cells.f, BuildingFurnishing.ContainerUse.TOOL_CHEST);
        if (second != null) {
            assertNull(CommsComponents.sourceOf(g.world.urban, cells.b, second), "one part a building");
        }
    }

    @Test
    void aStrongholdKeepsAModuleInARestrictedStashAndEarlierCitiesKeepNoParts() {
        Game g = session(GameMode.SURVIVAL);
        CompoundSites.Compound c = CompoundWorldArena.nearest(g, SurvivorSites.Kind.STRONGHOLD);
        CompoundWorldArena.load(g, c);
        Vec3i cell = CommsComponents.strongholdModuleCell(g.world.urban, c);
        assertNotNull(cell);
        BuildingPlan holder = g.world.urban.buildingAt(cell.x(), cell.z());
        long id = CommsComponents.strongholdModule(g.world.urban, c.site().id());
        Vec3i front = holder.fixtures().stream().filter(f -> f.id() == id).findFirst().orElseThrow().target();
        assertEquals(CompoundSites.Zone.RESTRICTED, c.zoneAt(front.x(), front.y(), front.z()),
                "only friends may stand before it");
        assertTrue(holder.id() != c.clinic(), "not the clinic visitors may enter");
        assertNotNull(g.world.urban.containers.touch(g.world, cell));
        Inventory stash = g.world.crateContents.get(cell);
        assertNotNull(stash);
        assertEquals(1, stash.count(ItemType.TRANSMITTER_MODULE), "the Wardens keep one module");
        // Generator 18 keeps the same buildings and containers without any part.
        Source module = source(g, BuildingPlan.Family.SECURITY, BuildingFurnishing.ContainerUse.ARMORY_LOCKER, false);
        Game old = SurvivorWorldArena.session(SEED, GameMode.SURVIVAL, World.GEN_URBAN_UTILITIES, new AudioManager());
        BuildingPlan oldPost = old.world.urban.buildingById(module.b.id());
        assertEquals(module.b, oldPost, "the same security post");
        assertNull(CommsComponents.sourceOf(old.world.urban, oldPost, module.f));
        old.world.ensureChunks(module.f.position().x(), module.f.position().z(), 2, 10_000);
        old.world.urban.containers.touch(old.world, module.f.position());
        Inventory oldRoll = old.world.crateContents.get(module.f.position());
        assertTrue(oldRoll == null || oldRoll.count(ItemType.TRANSMITTER_MODULE) == 0, "generator 18 rolls no part");
        assertEquals(0, CommsComponents.strongholdModule(old.world.urban, c.site().id()));
    }

    @Test
    void theCodesComeFromAFriendlyStrongholdOrTrustingCiviliansAndNobodyElse() {
        Game g = session(GameMode.SURVIVAL);
        CompoundSites.Compound c = CompoundWorldArena.nearest(g, SurvivorSites.Kind.STRONGHOLD);
        CompoundWorldArena.load(g, c);
        CompoundWorldArena.standFacing(g, c, CompoundWorldArena.street(c, 2), c.gate().outside());
        SurvivorWorldArena.slowTick(g);
        UrbanSite ward = CompoundWorldArena.site(g, c);
        Npc keeper = CompoundWorldArena.live(ward).getFirst();
        UrbanComms.askAboutRadio(g, keeper); // the rumour first
        UrbanMissions.Comms comms = g.world.urban.missions.comms;
        assertTrue(comms.rumoured);
        String refused = UrbanComms.askAboutRadio(g, keeper);
        assertEquals(UrbanMissions.CODES_NONE, comms.codes, "a stranger gets no codes: " + refused);
        assertTrue(refused.contains("friend"), refused);
        ward.localReputation = UrbanSite.FRIEND_TRUST + 5;
        ward.settleAlignment();
        assertTrue(ward.grantsAccess());
        UrbanComms.askAboutRadio(g, keeper);
        assertEquals(UrbanMissions.CODES_STRONGHOLD, comms.codes);
        assertEquals(ward.id, comms.codesFrom);

        // Hidden civilians share them only once they trust the survivor as far as they would follow them.
        Game h = session(GameMode.SURVIVAL);
        SurvivorSites.Site refuge = SurvivorWorldArena.nearest(h, SurvivorSites.Kind.REFUGE);
        SurvivorWorldArena.standAt(h, SurvivorWorldArena.hall(h, refuge));
        SurvivorWorldArena.slowTick(h);
        SurvivorWorldArena.pressF(h, SurvivorWorldArena.panel(h, refuge));
        SurvivorWorldArena.standAt(h, refuge.entry());
        UrbanSite r = SurvivorWorldArena.site(h, refuge);
        Npc civilian = r.resident(0).live;
        assertNotNull(civilian);
        UrbanComms.askAboutRadio(h, civilian);
        assertTrue(h.world.urban.missions.comms.rumoured, "hidden civilians know of the relay too");
        UrbanComms.askAboutRadio(h, civilian);
        assertEquals(UrbanMissions.CODES_NONE, h.world.urban.missions.comms.codes, "wary people keep their codes");
        r.localReputation = UrbanComms.CODES_TRUST;
        UrbanComms.askAboutRadio(h, civilian);
        assertEquals(UrbanMissions.CODES_REFUGE, h.world.urban.missions.comms.codes);
        assertEquals(r.id, h.world.urban.missions.comms.codesFrom);

        // A camp keeps no codes, only the knowledge of who does.
        Game k = session(GameMode.SURVIVAL);
        SurvivorSites.Site campPlan = SurvivorWorldArena.nearest(k, SurvivorSites.Kind.CAMP);
        SurvivorWorldArena.standAt(k, campPlan.entry());
        SurvivorWorldArena.slowTick(k);
        UrbanSite camp = SurvivorWorldArena.site(k, campPlan);
        camp.localReputation = 90;
        camp.settleAlignment();
        UrbanComms.askAboutRadio(k, camp.resident(0).live);
        k.world.urban.missions.comms.recovered = CommsComponents.ALL;
        String hint = UrbanComms.askAboutRadio(k, camp.resident(0).live);
        assertEquals(UrbanMissions.CODES_NONE, k.world.urban.missions.comms.codes);
        assertTrue(hint.contains("Wardens"), hint);
    }

    @Test
    void earlierCitiesHaveNoRadioToAskAbout() {
        Game g = SurvivorWorldArena.session(SEED, GameMode.SURVIVAL, World.GEN_URBAN_UTILITIES, new AudioManager());
        SurvivorSites.Site plan = SurvivorWorldArena.nearest(g, SurvivorSites.Kind.CAMP);
        SurvivorWorldArena.standAt(g, plan.entry());
        SurvivorWorldArena.slowTick(g);
        Npc lookout = SurvivorWorldArena.site(g, plan).resident(0).live;
        assertFalse(g.npcScreen.siteOptions(g, lookout).stream().anyMatch(o -> o.startsWith("[9]")));
        assertFalse(g.npcScreen.performSiteChoice(g, lookout, 9));
        assertNull(UrbanComms.hudLine(g));
        assertNull(UrbanComms.target(g));
        UrbanComms.onTaken(g, ItemType.TRANSMITTER_MODULE, 1); // inert, never throws
        assertNull(g.world.urban.relay());
    }

    // ------------------------------------------------------------------

    record Source(BuildingPlan b, BuildingPlan.Fixture f) {
        CommsComponents.Kind kind() {
            return switch (b.family()) {
                case SECURITY -> CommsComponents.Kind.TRANSMITTER;
                case WAREHOUSE, CLINIC -> CommsComponents.Kind.CELLS;
                default -> CommsComponents.Kind.ANTENNA;
            };
        }
    }

    /**
     * The first container of a use in the unheld, not picked-clean building of a family nearest the
     * spawn, outside every compound.
     */
    static Source source(Game g, BuildingPlan.Family family, BuildingFurnishing.ContainerUse use, boolean roof) {
        Source best = null;
        double bestD = Double.MAX_VALUE;
        for (int rz = -2; rz <= 2; rz++) {
            for (int rx = -2; rx <= 2; rx++) {
                for (ParcelPlan p : g.world.urban.regionPlan(rx, rz).parcels()) {
                    BuildingPlan b = g.world.urban.buildingAt(p.x0(), p.z0());
                    if (b == null || b.family() != family || b.variant() == BuildingPlan.Variant.OCCUPIED
                            || b.variant() == BuildingPlan.Variant.PICKED_CLEAN
                            || SurvivorSites.owner(g.world.urban, b.id()) != null) {
                        continue;
                    }
                    for (BuildingPlan.Fixture f : b.fixtures()) {
                        if (f.kind() != BuildingPlan.FixtureKind.CONTAINER || BuildingFurnishing.containerUse(b, f) != use
                                || (UrbanIds.floorIndex(f.id()) == b.storeys()) != roof
                                || CommsComponents.sourceOf(g.world.urban, b, f) == null) {
                            continue;
                        }
                        double d = (double) p.x0() * p.x0() + (double) p.z0() * p.z0();
                        if (d < bestD) {
                            bestD = d;
                            best = new Source(b, f);
                        }
                    }
                }
            }
        }
        assertNotNull(best, "a " + family + " " + use + " within two regions of the spawn");
        return best;
    }

    /** The next container of the same use after a source's, or null. */
    static BuildingPlan.Fixture second(BuildingPlan b, BuildingPlan.Fixture first, BuildingFurnishing.ContainerUse use) {
        for (BuildingPlan.Fixture f : b.fixtures()) {
            if (f.kind() == BuildingPlan.FixtureKind.CONTAINER && f.id() > first.id()
                    && UrbanIds.floorIndex(f.id()) != b.storeys() && BuildingFurnishing.containerUse(b, f) == use) {
                return f;
            }
        }
        return null;
    }

    /** Searches a source container through the storage screen and takes its part out of it. */
    static void take(Game g, Source s) {
        Vec3i at = s.f.target();
        SurvivorWorldArena.standAt(g, at);
        assertTrue(g.openCrateAt(s.f.position()), "the container opens: " + s);
        SurvivorWorldArena.run(g, 4f);
        Inventory crate = g.world.crateContents.get(s.f.position());
        assertNotNull(crate, "searched: " + s);
        for (int i = 0; i < crate.size(); i++) {
            ItemStack stack = crate.get(i);
            if (stack != null && stack.type == s.kind().item) {
                assertTrue(g.transferCrateItemToPlayer(i) > 0, "taken: " + s);
                g.closeScreens();
                return;
            }
        }
        throw new AssertionError("no " + s.kind().noun + " in " + s);
    }

}
