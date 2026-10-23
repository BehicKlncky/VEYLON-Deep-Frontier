package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.item.ItemType;
import com.veylon.settlement.NpcArchetype;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.FixtureStates;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.RefugeExposure;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanRefuges;
import com.veylon.world.urban.UrbanStarterSite;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.veylon.RefugeWorldArena.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The night rule of a claimed refuge (urban milestone 18), applied by the production sleep command
 * in the starter refuge: shut tight it draws nothing and rests better; a way in left open or
 * unbarred draws the nearest infected that is really out there, which wakes the sleeper sooner
 * the nearer it was; with nothing out there the night passes quietly and nothing appears; a light
 * showing at night draws from farther, until the windows are boarded; a Creative sleeper draws
 * nothing; and outside a claim nothing changes.
 */
class UrbanRefugeNightTest {
    private static final float DT = 0.05f;

    private static UrbanRefuges.Claim home(Game g) {
        return g.world.urban.refuges.home();
    }

    private static RefugeExposure.Assessment exposure(Game g) {
        return RefugeExposure.assess(g.world, home(g));
    }

    /** Lies down on the starter refuge's bed at an hour of the night. */
    private static void lieDown(Game g, double hour) {
        BuildingPlan starter = UrbanStarterSite.of(g.world.urban).refuge();
        standAt(g, g.world.urban.refugeAnchor);
        g.player.fatigue = 90; // tired enough that only morning, or something out there, ends the night
        g.time.advanceToNextHour(hour);
        BuildingPlan.Fixture bed = fixture(starter, 0, f -> f.block() == BlockType.CAMP_BED);
        aim(g, bed.position());
        g.player.crouching = false;
        g.interactions.interact();
    }

    /** Bars every door that admits a body and boards every way left open, except those kept. */
    private static void shutTight(Game g, List<Vec3i> keepOpen) {
        for (RefugeExposure.Way way : exposure(g).ways()) {
            if (!way.admits() || keepOpen.contains(way.cell())) { continue; }
            Vec3i c = way.cell();
            if (way.state() == RefugeExposure.State.UNBARRED) {
                for (int y = 0; y < 2; y++) { assertTrue(g.world.fixtures.set(c.x(), c.y() + y, c.z(), FixtureStates.LOCK_BARRED)); }
            } else {
                g.world.setBlock(c.x(), c.y(), c.z(), BlockType.BARRICADE, true);
            }
        }
    }

    private static Npc infected(Game g, Vec3i at) {
        Npc n = g.entities.spawnNpc(g.world, "Veilbound", at.x() + .5f, at.y(), at.z() + .5f);
        n.archetype = NpcArchetype.VEILBOUND;
        n.health = n.maxHealth = NpcArchetype.VEILBOUND.maxHealth;
        return n;
    }

    /** The first door way of the refuge that admits a body. */
    private static RefugeExposure.Way openDoor(Game g) {
        return exposure(g).ways().stream().filter(w -> w.door() && w.admits()).findFirst().orElseThrow();
    }

    /** A cell some distance straight out from a way's outside cell, at street height. */
    private static Vec3i outFrom(RefugeExposure.Way way, int blocks) {
        Vec3i o = way.outside(), c = way.cell();
        int dx = Integer.signum(o.x() - c.x()), dz = Integer.signum(o.z() - c.z());
        return new Vec3i(o.x() + dx * blocks, 41, o.z() + dz * blocks);
    }

    /** Sleeps on through the production sleep tick until the sleeper wakes; returns the game minutes slept. */
    private static double sleepThrough(Game g) {
        double start = g.time.totalMinutes;
        for (int i = 0; i < 20_000 && g.sleeping; i++) { g.sleep.tickSleep(DT); }
        assertFalse(g.sleeping, "the sleeper woke");
        return g.time.totalMinutes - start;
    }

    private static String lastLog(Game g) {
        return g.eventLog.all().getLast();
    }

    @Test
    void aRefugeShutTightDrawsNothingAndRestsBetterThanOneLeftUnbarred() {
        Game tight = session(GameMode.SURVIVAL);
        tight.entities.npcs.clear();
        shutTight(tight, List.of());
        assertTrue(exposure(tight).shutTight());
        Npc outside = infected(tight, outFrom(openDoorOf(tight), 12));
        lieDown(tight, 23);
        assertTrue(tight.sleeping);
        assertTrue(tight.eventLog.all().stream().anyMatch(l -> l.endsWith("Your refuge is shut tight.")));
        assertEquals(0f, outside.searchTimer, "nothing is drawn to a refuge shut tight");
        double slept = sleepThrough(tight);
        assertTrue(slept > 5 * 60, "a whole night's sleep: " + slept);
        assertTrue(lastLog(tight).contains("You wake after"), lastLog(tight));

        Game loose = session(GameMode.SURVIVAL);
        loose.entities.npcs.clear();
        shutTight(loose, List.of(openDoor(loose).cell()));
        assertFalse(exposure(loose).shutTight());
        lieDown(loose, 23);
        assertEquals(Math.min(1f, loose.sleep.quality() + SleepSystem.SHUT_TIGHT_BONUS), tight.sleep.quality(), 1e-4f,
                "shut tight rests better, and nothing else differs");
        assertTrue(tight.sleep.quality() > loose.sleep.quality());
        assertTrue(loose.eventLog.all().stream().anyMatch(l -> l.endsWith("Your refuge: 1 way in open.")));
    }

    /** The cell of a door way, whatever its state now: the first door the refuge has. */
    private static RefugeExposure.Way openDoorOf(Game g) {
        return exposure(g).ways().stream().filter(RefugeExposure.Way::door).findFirst().orElseThrow();
    }

    @Test
    void anUnbarredDoorDrawsTheNearestInfectedOutThereAndItWakesTheSleeperSooner() {
        Game g = session(GameMode.SURVIVAL);
        g.entities.npcs.clear();
        RefugeExposure.Way door = openDoor(g);
        shutTight(g, List.of(door.cell()));
        Npc near = infected(g, outFrom(door, 10)), far = infected(g, outFrom(door, 25));
        lieDown(g, 23);
        assertTrue(g.sleeping);
        Vec3i o = door.outside();
        assertEquals(o.x() + .5f, near.lastKnown.x, 1e-4f, "drawn to the unbarred door");
        assertEquals(o.z() + .5f, near.lastKnown.z, 1e-4f);
        assertTrue(near.searchTimer >= RefugeSystem.DRAWN_SEARCH_SECONDS, "it comes to look");
        assertEquals(0f, far.searchTimer, "only the nearest body is drawn");
        double slept = sleepThrough(g);
        assertTrue(slept < 60, "woken well before morning: " + slept);
        assertTrue(slept >= RefugeSystem.MIN_WAKE_MINUTES - 1, "but not at once: " + slept);
        assertTrue(lastLog(g).endsWith("You wake with a start: something out there has found your refuge."), lastLog(g));
    }

    @Test
    void withNothingOutThereAnExposedRefugeSleepsQuietlyAndNothingAppears() {
        Game g = session(GameMode.SURVIVAL);
        g.entities.npcs.clear();
        assertFalse(exposure(g).shutTight(), "its doors stand unbarred");
        lieDown(g, 23);
        assertTrue(g.sleeping);
        double slept = sleepThrough(g);
        assertTrue(slept > 5 * 60, "a whole night: no raid is conjured: " + slept);
        assertTrue(lastLog(g).contains("You wake after"), lastLog(g));
        assertTrue(g.entities.npcs.isEmpty(), "and nobody was made to appear");
    }

    @Test
    void aLightShowingAtNightDrawsFromFartherUntilTheWindowsAreBoarded() {
        Game g = session(GameMode.SURVIVAL);
        g.entities.npcs.clear();
        shutTight(g, List.of());
        Vec3i torch = freeCell(g);
        g.world.setBlock(torch.x(), torch.y(), torch.z(), BlockType.TORCH, true);
        RefugeExposure.Assessment lit = exposure(g);
        assertTrue(lit.lit() && lit.lightShows(), "a torch inside shows through the glass");
        RefugeExposure.Way window = lit.ways().stream().filter(w -> !w.door()).findFirst().orElseThrow();
        Npc watcher = infected(g, outFrom(window, 40));
        standAt(g, g.world.urban.refugeAnchor);
        g.time.advanceToNextHour(12);
        assertTrue(g.refuges.settleForNight().shutTight(), "by day the torch shows nobody the way");
        assertEquals(0f, watcher.searchTimer);
        lieDown(g, 23);
        assertTrue(watcher.searchTimer > 0, "a light at night draws from beyond the open-door distance");

        // Shatter and board up every window of the storey, as a player would: no light shows, and
        // the refuge is shut tight again.
        Game boarded = session(GameMode.SURVIVAL);
        boarded.entities.npcs.clear();
        shutTight(boarded, List.of());
        boarded.world.setBlock(torch.x(), torch.y(), torch.z(), BlockType.TORCH, true);
        BuildingPlan starter = UrbanStarterSite.of(boarded.world.urban).refuge();
        for (BuildingPlan.Fixture f : starter.fixtures()) {
            if (f.kind() != BuildingPlan.FixtureKind.WINDOW || UrbanIds.floorIndex(f.id()) != 0) { continue; }
            standAt(boarded, f.target());
            for (int h = 0; h < Math.max(1, f.height()); h++) {
                Vec3i pane = f.position().offset(0, h, 0);
                if (boarded.world.getBlock(pane.x(), pane.y(), pane.z()) == BlockType.WINDOW) {
                    assertTrue(boarded.blockActions.completePlayerBlockBreak(pane), "shattered " + pane);
                }
                holdItem(boarded, ItemType.BARRICADE, 1);
                aim(boarded, pane);
                boarded.interactions.rightClick();
                assertSame(BlockType.BARRICADE, boarded.world.getBlock(pane.x(), pane.y(), pane.z()), "boarded at " + pane);
            }
        }
        assertTrue(exposure(boarded).shutTight());
        assertFalse(exposure(boarded).lightShows());
        Npc blind = infected(boarded, outFrom(window, 40));
        lieDown(boarded, 23);
        assertEquals(0f, blind.searchTimer, "boarded windows show no light");
    }

    /** An empty, supported cell on the refuge's ground floor that is not the bed's. */
    private static Vec3i freeCell(Game g) {
        BuildingPlan starter = UrbanStarterSite.of(g.world.urban).refuge();
        Vec3i sleep = g.world.urban.refugeAnchor;
        for (BuildingPlan.Floor f : starter.floors()) {
            if (f.index() != 0) { continue; }
            for (BuildingPlan.Room room : f.rooms()) {
                Vec3i a = room.anchor();
                if (!a.equals(sleep) && g.world.getBlock(a.x(), a.y(), a.z()) == BlockType.AIR
                        && g.world.getBlock(a.x(), a.y() - 1, a.z()).solid) { return a; }
            }
        }
        throw new AssertionError("no free cell for a torch");
    }

    @Test
    void aCreativeSleeperDrawsNothingAndOutsideAClaimNothingChanges() {
        Game creative = session(GameMode.CREATIVE);
        creative.entities.npcs.clear();
        RefugeExposure.Way door = openDoor(creative);
        Npc near = infected(creative, outFrom(door, 8));
        lieDown(creative, 23);
        assertEquals(0f, near.searchTimer, "no threat perceives a Creative sleeper");

        Game g = session(GameMode.SURVIVAL);
        g.entities.npcs.clear();
        BuildingPlan b = neighbour(g);
        BuildingPlan.Fixture bed = fixture(b, 1, f -> f.block() == BlockType.CAMP_BED);
        Npc nearby = infected(g, new Vec3i(b.parcel().x0() + 4, 41, b.parcel().z0() - 14));
        standAt(g, bed.target());
        g.time.advanceToNextHour(23);
        assertNull(g.refuges.settleForNight(), "not in a claim");
        aim(g, bed.position());
        g.interactions.interact();
        assertTrue(g.sleeping);
        assertEquals(0f, nearby.searchTimer, "outside a claim the night draws nothing new");
    }

    @Test
    void theBedSaysHowTheRefugeStands() {
        Game g = session(GameMode.SURVIVAL);
        BuildingPlan starter = UrbanStarterSite.of(g.world.urban).refuge();
        BuildingPlan.Fixture bed = fixture(starter, 0, f -> f.block() == BlockType.CAMP_BED);
        standAt(g, bed.target());
        aim(g, bed.position());
        g.updatePrompt();
        int open = exposure(g).count(RefugeExposure.State.OPEN) + exposure(g).count(RefugeExposure.State.UNBARRED);
        assertEquals("[F] Sleep in your refuge  (" + open + (open == 1 ? " way in open" : " ways in open")
                + "; crouch: give it up)", g.interactPrompt);
        shutTight(g, List.of());
        g.updatePrompt();
        assertEquals("[F] Sleep in your refuge  (shut tight; crouch: give it up)", g.interactPrompt);
    }
}
