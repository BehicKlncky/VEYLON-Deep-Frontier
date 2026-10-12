package com.veylon.ai;

import com.veylon.Game;
import com.veylon.UrbanFixtureArena;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.settlement.NpcArchetype;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.FixtureStates;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.ParcelPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class IndoorNavigationTest {
    private static final float DT = .05f;

    static Npc infected(Game g, Vec3i cell) {
        Npc n = g.entities.spawnNpc(g.world, "Navigation subject", cell.x() + .5f, cell.y(), cell.z() + .5f);
        assertNotNull(n);
        n.archetype = NpcArchetype.VEILBOUND;
        n.health = n.maxHealth = n.archetype.maxHealth;
        return n;
    }

    @ParameterizedTest @EnumSource(GameMode.class)
    void generatedApartmentPursuitClimbsTwoFloorsThenSearchesEvidenceAndGivesUp(GameMode mode) {
        Game g = new Game();
        g.newWorld(20260731, true, World.GEN_URBAN_ENCOUNTERS, mode);
        g.entities.npcs.clear();
        BuildingPlan plan = g.world.urban.regionPlan(0, 0).parcels().stream()
                .map(p -> g.world.urban.buildingAt(p.x0(), p.z0()))
                .filter(p -> p.storeys() >= 2).findFirst().orElseThrow();
        g.world.ensureChunks(plan.parcel().x0(), plan.parcel().z0(), 3, 10_000);
        Vec3i start = plan.approaches().getFirst();
        Vec3i target = plan.floors().stream().filter(f -> f.index() == 2).findFirst().orElseThrow()
                .rooms().stream().filter(r -> r.functions().contains(BuildingPlan.Function.BEDROOM))
                .findFirst().orElseThrow().anchor();
        Npc n = infected(g, start);
        g.player.pos.set(target.x() + .5f, target.y(), target.z() + .5f);
        if (mode == GameMode.CREATIVE) {
            assertFalse(g.emitPlayerFootstepNoise(true, false));
            for (int i = 0; i < 100; i++) { g.entities.fastTick(g, DT); }
            assertEquals(0, n.searchTimer); assertNull(n.path);
            assertEquals(start.x() + .5f, n.pos.x); assertEquals(start.z() + .5f, n.pos.z);
            return;
        }
        assertFalse(InfectedAI.seesPlayer(g, n), "slabs and partitions occlude the other floor");
        int peakQueries = 0, peakExpansions = 0;
        boolean arrived = false;
        for (int tick = 0; tick < 1800; tick++) {
            if (tick % 20 == 0) {
                g.noise.emit(g, g.player.pos.x, g.player.pos.y, g.player.pos.z, 128, .9f,
                        "gunshot", true, g.player);
            }
            g.noise.update(DT);
            g.entities.fastTick(g, DT);
            g.settlementManager.fastTick(g, DT);
            peakQueries = Math.max(peakQueries, g.world.navigation.budget.queries());
            peakExpansions = Math.max(peakExpansions, g.world.navigation.budget.expansions());
            assertFalse(n.collidesAt(n.pos.x, n.pos.y + .01f, n.pos.z), "body=" + n.pos);
            assertTrue(n.pos.y > 40, "no stair-side plunge");
            if (n.distSqTo(g.player) < 3 && Math.abs(n.pos.y - target.y()) < .1f) { arrived = true; break; }
        }
        assertTrue(arrived, "actual=" + n.pos + " target=" + target + " known=" + n.lastKnown
                + " path=" + n.path + " failures=" + g.world.navigation.budget.failures());
        var remembered = new org.joml.Vector3f(n.lastKnown);
        // Teleport/flight is not a new observation: the old evidence stays at its real position.
        g.player.pos.add(1000, 20, 1000);
        g.noise.reset();
        for (int i = 0; i < 420; i++) { g.entities.fastTick(g, DT); }
        assertEquals(remembered, n.lastKnown);
        assertEquals(0, n.searchTimer);
        assertNull(n.path);
        assertEquals(0, n.vel.x); assertEquals(0, n.vel.z);
        System.out.println("[indoor-work] mode=" + mode + " peakQueries=" + peakQueries
                + " peakExpansions=" + peakExpansions + " routes=" + g.world.navigation.size());
    }

    @Test void editedDoorInvalidatesOnlyIntersectingRoutesAndBlocksSafelyUntilReopened() {
        var a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        var w = a.game.world;
        Vec3i from = a.house.outsideFront(), to = a.house.westRoom(), door = a.house.frontDoor();
        var inside = Pathfinder.find(w, from.x(), from.y(), from.z(), to.x(), to.y(), to.z(), 700);
        assertNotNull(inside);
        Vec3i far = new Vec3i(from.x() + 10, from.y(), from.z() - 4);
        var other = Pathfinder.find(w, far.x(), far.y(), far.z(), far.x() + 2, far.y(), far.z(), 700);
        assertNotNull(other);
        w.fixtures.set(door.x(), door.y(), door.z(), FixtureStates.LOCK_BARRED);
        w.fixtures.set(door.x(), door.y() + 1, door.z(), FixtureStates.LOCK_BARRED);
        assertFalse(w.navigation.valid(inside));
        assertTrue(w.navigation.valid(other));
        Npc n = infected(a.game, from);
        for (int i = 0; i < 100; i++) {
            w.navigation.budget.beginTick(); n.repathCooldown -= DT;
            IndoorNavigation.moveTo(a.game, n, to, 2, .5f); n.applyPhysics(DT, true);
        }
        assertTrue(n.pos.z < door.z());
        assertEquals(0, n.vel.z);
        w.fixtures.set(door.x(), door.y(), door.z(), FixtureStates.LOCK_NONE);
        w.fixtures.set(door.x(), door.y() + 1, door.z(), FixtureStates.LOCK_NONE);
        boolean arrived = false;
        for (int i = 0; i < 500; i++) {
            w.navigation.budget.beginTick(); n.repathCooldown -= DT;
            arrived = !IndoorNavigation.moveTo(a.game, n, to, 2, .5f);
            n.applyPhysics(DT, true);
            if (arrived) { break; }
        }
        assertTrue(arrived, "unlocked door is opened through the production fixture action");
    }

    @Test void populationsShareOneAllowanceAndBlockedWorkIsDeferred() {
        var a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        var w = a.game.world;
        Vec3i from = a.house.outsideFront();
        Vec3i door = a.house.frontDoor();
        w.fixtures.set(door.x(), door.y(), door.z(), FixtureStates.LOCK_BARRED);
        w.fixtures.set(door.x(), door.y() + 1, door.z(), FixtureStates.LOCK_BARRED);
        w.navigation.budget.beginTick();
        for (int i = 0; i < 24; i++) {
            Npc n = new Npc(w, "Budget subject");
            n.archetype = i < 12 ? NpcArchetype.VEILBOUND : NpcArchetype.GUARD;
            n.pos.set(from.x() + .5f, from.y(), from.z() + .5f);
            IndoorNavigation.moveTo(a.game, n, a.house.westRoom(), 2, .5f);
            assertEquals(0, n.vel.x); assertEquals(0, n.vel.z);
        }
        assertEquals(4, w.navigation.budget.queries());
        assertEquals(20, w.navigation.budget.deferred());
        assertTrue(w.navigation.budget.expansions() <= PathBudget.MAX_EXPANSIONS);
        assertEquals(4, w.navigation.budget.failures());
        assertEquals(PathBudget.MAX_EXPANSIONS, w.navigation.budget.expansions());
        System.out.println("[indoor-contention] bodies=24 queries=" + w.navigation.budget.queries()
                + " expansions=" + w.navigation.budget.expansions() + " deferred=" + w.navigation.budget.deferred());
    }

    @Test void aNearbyUnloadedColumnIsNeverAnAirBridgeAndSupportEditsStopCachedMovement() {
        Game g = new Game();
        g.world = new World(20260731, World.GEN_URBAN_STREETS);
        g.world.getOrCreateChunk(0, 0);
        for (int x = 0; x < 16; x++) { g.world.setBlock(x, 79, 8, BlockType.CONCRETE, true); }
        assertNull(Pathfinder.find(g.world, 14, 80, 8, 16, 80, 8, 700));
        assertFalse(InfectedAI.clearSight(g.world, 14.5f, 81, 8.5f, 16.5f, 81, 8.5f));
        Npc n = new Npc(g.world, "Boundary subject"); n.pos.set(14.5f, 80, 8.5f);
        IndoorNavigation.moveTo(g, n, new Vec3i(16, 80, 8), 2, .5f);
        assertEquals(0, n.vel.x); assertNull(n.path);
        n.repathCooldown = 0;
        IndoorNavigation.moveTo(g, n, new Vec3i(15, 80, 8), 2, .5f);
        assertNotNull(n.path);
        g.world.setBlock(15, 79, 8, BlockType.AIR, true);
        IndoorNavigation.moveTo(g, n, new Vec3i(15, 80, 8), 2, .5f);
        assertNull(n.path); assertEquals(0, n.vel.x);
        assertEquals(1, g.world.loadedChunks().size());
    }

    @Test void unloadedBoundaryAndUnsupportedFlightTargetsCannotCreateRoutesOrSteering() {
        var a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        Vec3i from = a.house.outsideFront();
        assertNull(Pathfinder.find(a.game.world, from.x(), from.y(), from.z(),
                from.x() + 500, from.y(), from.z(), 700));
        Npc n = infected(a.game, from);
        IndoorNavigation.moveTo(a.game, n, new Vec3i(from.x(), from.y() + 8, from.z()), 2, 1);
        assertNull(n.path); assertEquals(0, n.vel.x); assertEquals(0, n.vel.z);
        assertFalse(InfectedAI.clearSight(a.game.world, from.x(), from.y(), from.z(),
                from.x() + 500, from.y(), from.z()));
    }

    @Test void longLoadedRouteUsesLocalLegsInsteadOfExpandingAnUnboundedSearch() {
        Game g = new Game(); g.world = new World(42, World.GEN_URBAN_STREETS);
        for (int cx = 0; cx < 7; cx++) { g.world.getOrCreateChunk(cx, 0); }
        for (int x = 0; x <= 100; x++) { g.world.setBlock(x, 79, 8, BlockType.CONCRETE, true); }
        Npc n = new Npc(g.world, "Long route subject"); n.pos.set(.5f, 80, 8.5f);
        Vec3i goal = new Vec3i(100, 80, 8);
        boolean arrived = false; int queries = 0;
        for (int i = 0; i < 1600; i++) {
            g.world.navigation.budget.beginTick(); n.repathCooldown -= DT;
            arrived = !IndoorNavigation.moveTo(g, n, goal, 3, .5f);
            queries += g.world.navigation.budget.queries();
            n.applyPhysics(DT, true);
            if (arrived) { break; }
        }
        assertTrue(arrived, "body=" + n.pos);
        assertTrue(queries > 1 && queries < 10, "several bounded legs, not a single enlarged A*");
        assertEquals(80, n.pos.y, .02f);
    }

    @Test void residentFollowsGeneratedSwitchbacksBothWaysAcrossAllFrontagesAndRoofBasementBands() {
        Game g = new Game(); g.newWorld(20260731, true, World.GEN_URBAN_ENCOUNTERS, GameMode.SURVIVAL);
        g.entities.npcs.clear();
        for (ParcelPlan.Side side : ParcelPlan.Side.values()) {
            BuildingPlan plan = g.world.urban.regionPlan(0, 0).parcels().stream()
                    .filter(p -> p.frontage() == side)
                    .map(p -> g.world.urban.buildingAt(p.x0(), p.z0()))
                    .filter(p -> p.basement() && p.storeys() >= 2).findFirst().orElseThrow();
            g.world.ensureChunks(plan.parcel().x0(), plan.parcel().z0(), 2, 10_000);
            for (var flight : plan.stairs()) {
                for (int direction = 0; direction < 2; direction++) {
                    Vec3i start = direction == 0 ? flight.route().getFirst() : flight.route().getLast();
                    Vec3i goal = direction == 0 ? flight.route().getLast() : flight.route().getFirst();
                    Npc n = new Npc(g.world, "Resident stair subject");
                    n.pos.set(start.x() + .5f, start.y(), start.z() + .5f);
                    g.entities.npcs.add(n);
                    boolean arrived = false;
                    for (int i = 0; i < 500; i++) {
                        g.world.navigation.budget.beginTick(); n.repathCooldown -= DT;
                        arrived = !IndoorNavigation.moveTo(g, n, goal, 2.2f, .25f);
                        n.applyPhysics(DT, true);
                        assertFalse(n.collidesAt(n.pos.x, n.pos.y + .01f, n.pos.z));
                        if (arrived) { break; }
                    }
                    assertTrue(arrived, side + " fromFloor=" + flight.fromFloor() + " direction=" + direction
                            + " body=" + n.pos + " goal=" + goal);
                    g.entities.removeNpc(g, n);
                }
            }
        }
    }

    @Test void supportedLadderIsFollowableAndExcessiveDropsAreRejected() {
        Game g = new Game(); g.world = new World(42, World.GEN_URBAN_STREETS);
        g.world.getOrCreateChunk(0, 0);
        g.world.setBlock(8, 79, 8, BlockType.CONCRETE, true);
        g.world.setBlock(9, 83, 8, BlockType.CONCRETE, true);
        for (int y = 80; y <= 85; y++) { g.world.setBlock(8, y, 8, BlockType.LADDER, true); }
        Npc n = new Npc(g.world, "Ladder subject"); n.pos.set(8.5f, 80, 8.5f);
        boolean arrived = false;
        for (int i = 0; i < 400; i++) {
            g.world.navigation.budget.beginTick(); n.repathCooldown -= DT;
            arrived = !IndoorNavigation.moveTo(g, n, new Vec3i(9, 84, 8), 2, .3f);
            n.applyPhysics(DT, true);
            if (arrived) { break; }
        }
        assertTrue(arrived, "ladder body=" + n.pos);
        g.world.navigation.budget.beginTick();
        g.world.setBlock(4, 79, 8, BlockType.CONCRETE, true);
        g.world.setBlock(5, 75, 8, BlockType.CONCRETE, true);
        assertNull(Pathfinder.find(g.world, 4, 80, 8, 5, 76, 8, 700), "four-block drop is unsafe");
    }

    @Test void exhaustedLocalSearchReturnsAConnectedPrefixWithoutCachingItAsACompleteRoute() {
        World w = new World(42, World.GEN_URBAN_STREETS); w.getOrCreateChunk(0, 0);
        for (int x = 0; x < 16; x++) { w.setBlock(x, 79, 8, BlockType.CONCRETE, true); }
        w.navigation.budget.beginTick();
        var prefix = Pathfinder.findSegment(w, 0, 80, 8, 15, 80, 8, 3);
        assertNotNull(prefix);
        assertEquals(new Vec3i(0, 80, 8), prefix.getFirst());
        assertEquals(new Vec3i(2, 80, 8), prefix.getLast());
        assertEquals(3, w.navigation.budget.expansions());
        for (int i = 1; i < prefix.size(); i++) {
            assertTrue(com.veylon.world.urban.AccessibilityValidator.canMove(w, prefix.get(i - 1), prefix.get(i)));
        }
        assertNull(Pathfinder.find(w, 0, 80, 8, 15, 80, 8, 1), "prefix is never reused as a complete route");
    }
}
