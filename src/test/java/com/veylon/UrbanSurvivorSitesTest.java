package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.settlement.HumanFaction;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.UrbanSite;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.VoxelClearance;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanContainers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static com.veylon.SurvivorWorldArena.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Urban milestone 16 on a generated generator-15 city, through the production ticks and commands:
 * the people of a camp and of a hidden refuge appear on their planned cells inside the resident
 * budget, keep to them, go dormant without losing health or needs, are found only through play,
 * and answer theft, aid and harm with lasting consequences in both modes.
 */
class UrbanSurvivorSitesTest {

    @Test
    void campPeopleAppearOnTheirPlannedCellsAndKeepTheirHealthWhileDormant() {
        Game g = session(SEED, GameMode.SURVIVAL);
        SurvivorSites.Site plan = nearest(g, SurvivorSites.Kind.CAMP);
        standAt(g, plan.entry());
        slowTick(g);
        UrbanSite s = site(g, plan);
        assertNotNull(s, "a site near the player is registered");
        assertTrue(s.discovered, "standing inside a camp finds it");
        List<Npc> people = live(s);
        assertEquals(plan.residents().size(), people.size(), "everyone is home and inside the budget");
        for (Npc n : people) {
            UrbanSite.Resident r = s.resident(n.residentIndex);
            assertTrue(n.settled());
            assertEquals(s.id, n.settlementId);
            assertEquals(plan.resident(n.residentIndex).name(), n.name);
            assertEquals(UrbanSite.archetypeOf(r.role), n.archetype);
            Vec3i at = feet(n);
            assertTrue(plan.holds(at.x(), at.y(), at.z()), "they appear inside their own site: " + at);
        }
        Npc hurt = people.get(people.size() - 1);
        int slot = hurt.residentIndex;
        hurt.health = 11f;

        standAt(g, plan.anchor().offset(320, 0, 0));
        slowTick(g);
        assertTrue(live(s).isEmpty(), "far away, everyone is dormant");
        assertFalse(g.entities.npcs.contains(hurt));
        assertEquals(11f, s.resident(slot).health, 1e-4f, "a dormant body keeps its injuries");
        assertTrue(s.resident(slot).alive);

        standAt(g, plan.entry());
        slowTick(g);
        List<Npc> back = live(s);
        assertEquals(plan.residents().size(), back.size(), "the same people come back");
        Npc again = s.resident(slot).live;
        assertNotNull(again);
        assertEquals(plan.resident(slot).name(), again.name);
        assertEquals(11f, again.health, 1e-4f, "with the same injuries");
    }

    @Test
    void campPeopleKeepADailyRoutineOnTheirPlannedCells() {
        Game g = session(SEED, GameMode.SURVIVAL);
        SurvivorSites.Site plan = nearest(g, SurvivorSites.Kind.CAMP);
        standAt(g, plan.anchor().offset(0, 0, -40));
        setClock(g, 23 * 60);
        slowTick(g);
        UrbanSite s = site(g, plan);
        run(g, 45f);
        for (Npc n : live(s)) {
            UrbanSite.Resident r = s.resident(n.residentIndex);
            Vec3i goal = r.role == SurvivorSites.Role.LOOKOUT ? plan.post() : plan.resident(r.slot).bed();
            assertTrue(near(n, goal), n.name + " (" + r.role + ") should be at " + goal + " by night, is at " + n.pos);
        }
        setClock(g, 7 * 60);
        run(g, 25f);
        int atStash = 0;
        for (Npc n : live(s)) {
            for (Vec3i meal : plan.meals()) { if (near(n, meal)) { atStash++; break; } }
        }
        assertTrue(atStash >= 1, "at breakfast people gather at the stash");
        setClock(g, 10 * 60);
        run(g, 45f);
        for (Npc n : live(s)) {
            Vec3i goal = plan.resident(n.residentIndex).day();
            assertTrue(near(n, goal), n.name + " should keep the day cell " + goal + ", is at " + n.pos);
        }
    }

    private static void setClock(Game g, int minutes) {
        long day = (long) (g.time.totalMinutes / (24 * 60));
        g.time.totalMinutes = day * 24 * 60 + minutes;
    }

    private static boolean near(Npc n, Vec3i cell) {
        float dx = n.pos.x - cell.x() - .5f, dz = n.pos.z - cell.z() - .5f;
        return dx * dx + dz * dz < 1.2f * 1.2f && Math.abs(n.pos.y - cell.y()) < .6f;
    }

    @Test
    void hiddenCiviliansStayInTheirCellarAndAreFoundOnlyBySlidingThePanelAside() {
        Game g = session(SEED, GameMode.SURVIVAL);
        SurvivorSites.Site plan = nearest(g, SurvivorSites.Kind.REFUGE);
        standAt(g, hall(g, plan));
        slowTick(g);
        UrbanSite s = site(g, plan);
        assertNotNull(s);
        List<Npc> people = live(s);
        assertEquals(plan.residents().size(), people.size());
        assertFalse(s.discovered, "being near is not finding them");
        assertTrue(s.simMinute < 0, "nobody's needs count before they are met");
        for (Npc n : people) {
            assertEquals(Game.NpcInteraction.NONE, g.npcInteraction(n), "nobody is spoken to through the wall");
        }
        watchThemStay(g, s, plan, 45f);
        assertFalse(s.discovered, "a sealed cellar is never found by its people being seen or heard");

        Vec3i panel = panel(g, plan);
        pressF(g, panel);
        assertSame(BlockType.HIDDEN_PANEL_OPEN, g.world.getBlock(panel.x(), panel.y(), panel.z()));
        assertTrue(g.world.urban.discovery.discovered(plan.room()));
        assertTrue(s.discovered, "sliding the panel aside finds them");
        assertTrue(s.simMinute >= 0, "and from now on their needs count");
        assertTrue(g.eventLog.all().stream().anyMatch(l -> l.contains("huddle at the back of the cellar")),
                String.join("\n", g.eventLog.all()));
        // The panel stays open and the stranger stands in the hall: they still do not come out.
        watchThemStay(g, s, plan, 45f);
    }

    /** Runs the world and checks every live civilian, every half second, stands in the sealed room unclipped. */
    private static void watchThemStay(Game g, UrbanSite s, SurvivorSites.Site plan, float seconds) {
        int frames = 0;
        for (float t = 0; t < seconds; t += DT) {
            g.advanceWorld(DT);
            if (++frames % 10 != 0) { continue; }
            for (Npc n : live(s)) {
                Vec3i at = feet(n);
                assertTrue(plan.holds(at.x(), at.y(), at.z()), n.name + " left the cellar for " + at);
                assertFalse(VoxelClearance.collides(g.world, n.pos.x, n.pos.y + .01f, n.pos.z, n.width, n.height * .9f, false),
                        n.name + " stands inside a wall at " + n.pos);
            }
        }
    }

    @Test
    void needsRunTheSameWhetherThePeopleAreOnScreenOrDormant() {
        Game near = session(SEED, GameMode.SURVIVAL), far = session(SEED, GameMode.SURVIVAL);
        SurvivorSites.Site plan = nearest(near, SurvivorSites.Kind.REFUGE);
        for (Game g : List.of(near, far)) {
            standAt(g, hall(g, plan));
            slowTick(g);
            g.settlementManager.urbanSites.discover(g, site(g, plan));
        }
        standAt(far, plan.anchor().offset(-320, 0, 0));
        slowTick(far);
        assertTrue(live(site(far, plan)).isEmpty(), "precondition: one world's refuge is dormant");
        assertFalse(live(site(near, plan)).isEmpty(), "precondition: the other's is on screen");
        for (int hour = 0; hour < 96; hour++) {
            for (Game g : List.of(near, far)) {
                g.time.totalMinutes += 60;
                slowTick(g);
            }
        }
        UrbanSite a = site(near, plan), b = site(far, plan);
        assertEquals(a.simMinute, b.simMinute);
        for (int i = 0; i < a.residents.size(); i++) {
            UrbanSite.Resident x = a.resident(i), y = b.resident(i);
            assertEquals(x.alive, y.alive, "slot " + i);
            assertEquals(x.health, y.health, 1e-4f, "slot " + i);
            assertEquals(x.hunger, y.hunger, 1e-4f);
            assertEquals(x.thirst, y.thirst, 1e-4f);
        }
        for (Vec3i p : plan.stash()) {
            assertEquals(contents(near, p), contents(far, p), "the same stash left at " + p);
        }
    }

    @Test
    void anEmptyStashStarvesItsPeopleSlowlyAndTheDeadNeverReturn() {
        Game g = session(SEED, GameMode.SURVIVAL);
        SurvivorSites.Site plan = nearest(g, SurvivorSites.Kind.REFUGE);
        standAt(g, hall(g, plan));
        slowTick(g);
        UrbanSite s = site(g, plan);
        g.settlementManager.urbanSites.discover(g, s);
        for (Vec3i p : plan.stash()) {
            g.world.urban.containers.touch(g.world, p);
            Inventory inv = g.world.crateContents.get(p);
            if (inv != null) { inv.clear(); }
        }
        standAt(g, plan.anchor().offset(320, 0, 0));
        slowTick(g);
        advanceHours(g, 30);
        for (Settlement.Resident r : s.residents) {
            assertTrue(r.alive, "thirst takes a day before it does harm, and harm takes longer: " + r.name);
        }
        advanceHours(g, 30);
        for (Settlement.Resident r : s.residents) {
            assertFalse(r.alive, r.name + " starved");
            assertEquals(0f, r.health);
        }
        assertTrue(g.eventLog.all().stream().anyMatch(l -> l.contains("has died of thirst.")));
        standAt(g, hall(g, plan));
        slowTick(g);
        assertTrue(live(s).isEmpty(), "the dead never come back");
    }

    private static void advanceHours(Game g, int hours) {
        for (int i = 0; i < hours; i++) {
            g.time.totalMinutes += 60;
            slowTick(g);
        }
    }

    @Test
    void takingFromACampsStoreIsTheftOnceAndPuttingItBackEarnsNothing() {
        Game g = session(SEED, GameMode.SURVIVAL);
        SurvivorSites.Site plan = nearest(g, SurvivorSites.Kind.CAMP);
        Vec3i stash = plan.stash().getFirst();
        standAt(g, plan.meals().getFirst());
        slowTick(g);
        UrbanSite s = site(g, plan);
        assertTrue(s.discovered);
        float trust = s.localReputation;
        float standing = g.world.factionReputation.getOrDefault(HumanFaction.FREE_SETTLERS, 0f);

        assertTrue(g.crates.openCrateAt(stash));
        assertTrue(g.crates.storageStatus().startsWith("Camp storage: taking supplies is theft"), g.crates.storageStatus());
        for (float t = 0; t < 4f; t += DT) { g.crates.fastTick(DT); }
        ItemType kind = g.openCrate.get(0).type;
        int taken = g.crates.transferCrateItemToPlayer(0);
        assertTrue(taken > 0);
        assertEquals(trust - Math.min(15f, 4f + taken), s.localReputation, 1e-3f, "theft costs trust once");
        assertEquals(standing - Math.min(12, 3 + taken),
                g.world.factionReputation.get(HumanFaction.FREE_SETTLERS), 1e-3f, "and the Free Settlers' standing, as before");
        assertEquals(taken, s.owed);

        float afterTheft = s.localReputation;
        int slot = slotOf(g.player.inventory, kind);
        assertEquals(taken, g.crates.transferPlayerItemToCrate(slot, taken), "everything taken goes back");
        assertEquals(afterTheft, s.localReputation, 1e-3f, "putting back what was taken earns nothing");
        assertEquals(0, s.owed);

        g.player.inventory.add(ItemType.WATERSKIN_CLEAN, 2);
        assertEquals(2, g.crates.transferPlayerItemToCrate(slotOf(g.player.inventory, ItemType.WATERSKIN_CLEAN), 2));
        assertEquals(afterTheft + 6f, s.localReputation, 1e-3f, "new water is aid");
    }

    private static int slotOf(Inventory inv, ItemType type) {
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s != null && s.type == type) { return i; }
        }
        throw new AssertionError("no " + type + " carried");
    }

    private static List<String> contents(Game g, Vec3i p) {
        List<String> out = new ArrayList<>();
        Inventory inv = g.world.crateContents.get(p);
        if (inv == null) { return out; }
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s != null) { out.add(i + ":" + s.type + "x" + s.count); }
        }
        return out;
    }

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"SURVIVAL", "CREATIVE"})
    void harmingACampTurnsItHostileAndItsLookoutStrikesOnlyThroughTheDamageGates(GameMode mode) {
        Game g = session(SEED, mode);
        SurvivorSites.Site plan = nearest(g, SurvivorSites.Kind.CAMP);
        standAt(g, plan.entry());
        slowTick(g);
        UrbanSite s = site(g, plan);
        Npc trader = s.resident(1).live;
        assertNotNull(trader);
        g.npcScreen.performAttack(g, trader);
        assertFalse(s.hostile(), "one blow alarms them");
        assertTrue(g.eventLog.all().stream().anyMatch(l -> l.contains("You attacked " + trader.name)));
        g.npcScreen.performAttack(g, trader);
        assertTrue(s.hostile(), "a second turns the camp hostile in " + mode);
        assertTrue(trader.hostileToPlayer());

        Npc lookout = s.resident(0).live;
        assertNotNull(lookout);
        standAt(g, plan.post().equals(plan.entry()) ? plan.post() : plan.entry());
        float health = g.player.health;
        run(g, 8f);
        if (mode == GameMode.SURVIVAL) {
            assertTrue(g.player.health < health, "the lookout strikes a player it perceives");
        } else {
            assertEquals(health, g.player.health, 1e-4f, "Creative is neither perceived nor hurt");
        }
    }

    @Test
    void aRefugesStashBelongsToItsPeople() {
        Game g = session(SEED, GameMode.SURVIVAL);
        SurvivorSites.Site plan = nearest(g, SurvivorSites.Kind.REFUGE);
        standAt(g, hall(g, plan));
        Vec3i stash = plan.stash().getFirst();
        UrbanContainers.Entry entry = g.world.urban.containers.touch(g.world, stash);
        assertEquals(UrbanContainers.Owner.SURVIVOR, entry.owner);
        assertEquals(plan.id(), entry.ownerId);
        Inventory inv = g.world.crateContents.get(stash);
        int people = plan.residents().size(), stashes = plan.stash().size();
        int each = (2 * people + 1) / stashes + ((2 * people + 1) % stashes > 0 ? 1 : 0);
        assertEquals(each, inv.count(ItemType.DRIED_BERRY), "rations for a few days");
        assertEquals(each, inv.count(ItemType.WATERSKIN_CLEAN));
    }
}
