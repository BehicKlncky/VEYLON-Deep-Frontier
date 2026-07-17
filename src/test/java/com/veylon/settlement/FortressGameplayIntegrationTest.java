package com.veylon.settlement;

import com.veylon.Game;
import com.veylon.ai.FactionSystem;
import com.veylon.ai.Quest;
import com.veylon.entity.Npc;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.save.SaveSystem;
import com.veylon.ui.NpcScreen;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Production-path coverage for the fortress objective and its tactical routes. */
class FortressGameplayIntegrationTest {

    private static final long[] REQUIRED_SEEDS = {
            1L, 42L, 999L, -1_234_567L, 20_260_716L, 987_654_321L
    };

    @Test
    void everyRequiredSeedBuildsDeterministicGatePosternAlarmPrisonMagazineAndCommandRoutes() {
        for (long seed : REQUIRED_SEEDS) {
            World firstWorld = new World(seed, World.GEN_DEEP);
            Settlement first = findFortress(firstWorld, true);
            assertNotNull(first, "hostile fortress for seed " + seed);
            SettlementBuilder.Layout layout = firstWorld.layoutFor(first);

            World secondWorld = new World(seed, World.GEN_DEEP);
            Settlement second = secondWorld.settlementForRegion(first.regionX, first.regionZ);
            assertNotNull(second);
            SettlementBuilder.Layout repeated = secondWorld.layoutFor(second);
            assertEquals(layout.blocks, repeated.blocks,
                    "fortress block plan is seed-deterministic for " + seed);
            assertEquals(layout.infiltrationPoints, repeated.infiltrationPoints);

            // Direct assault: outer and inner two-wide/two-high gates are real weak blocks.
            assertEquals(2, first.gates.size(), "outer and inner defensive zones");
            for (Vec3i gate : first.gates) {
                assertBlock(layout, gate, BlockType.GATE);
                assertBlock(layout, gate.offset(1, 0, 0), BlockType.GATE);
                assertBlock(layout, gate.offset(0, 1, 0), BlockType.GATE);
                assertBlock(layout, gate.offset(1, 1, 0), BlockType.GATE);
                assertTrue(com.veylon.combat.ExplosionSystem.blastResistance(BlockType.GATE)
                                < com.veylon.combat.ExplosionSystem.blastResistance(BlockType.STONE_BRICK),
                        "the gate, not its reinforced wall, is the intended breach point");
            }

            // Side/rear infiltration: an intentional standing-height postern crosses the wall.
            assertEquals(1, layout.infiltrationPoints.size());
            Vec3i postern = layout.infiltrationPoints.getFirst();
            assertBlock(layout, postern, BlockType.AIR);
            assertBlock(layout, postern.offset(0, 1, 0), BlockType.AIR);
            int outside = firstWorld.generator.heightAt(postern.x(), postern.z() - 1);
            int inside = firstWorld.generator.heightAt(postern.x(), postern.z() + 1);
            assertTrue(Math.abs(outside - inside) <= 2,
                    "postern approach is terrain-reachable for seed " + seed);

            // Alarm suppression and captive route are physical, mineable objectives.
            assertNotNull(first.alarmBell);
            assertBlock(layout, first.alarmBell, BlockType.ALARM_BELL);
            assertNotNull(first.prisonPos);
            Vec3i cageBar = first.prisonPos.offset(-1, 1, 0);
            assertBlock(layout, cageBar, BlockType.CAGE_BARS);
            assertTrue(BlockType.CAGE_BARS.hardness > 0
                    && BlockType.CAGE_BARS.preferredTool == com.veylon.item.ToolKind.PICKAXE);

            // Powder-magazine approach: three independent kegs surround its marked center.
            assertNotNull(first.magazinePos);
            assertBlock(layout, first.magazinePos.offset(-1, 0, -1), BlockType.POWDER_KEG);
            assertBlock(layout, first.magazinePos.offset(1, 0, -1), BlockType.POWDER_KEG);
            assertBlock(layout, first.magazinePos.offset(-1, 0, 1), BlockType.POWDER_KEG);

            // Patrol attrition and command stage have distinct outer/inner routes and a leader.
            assertTrue(first.patrolPoints.size() >= 8, "outer and inner patrol circuits");
            assertNotNull(first.leaderPost);
            assertTrue(first.residents.stream().anyMatch(r -> r.archetype.leader));
            long ladders = layout.blocks.values().stream()
                    .filter(id -> BlockType.byId(id) == BlockType.LADDER).count();
            assertTrue(ladders >= 8, "the leader floor has a real ladder route");
            assertFalse(layout.campfires.isEmpty(), "central control uses a generated campfire");
        }
    }

    @Test
    void everyRequiredSeedDiscoversAndTraversesGeneratedFortressTacticalRoutesThroughPlayerClearance() {
        boolean coveredNegativeWorldCoordinates = false;
        for (long seed : REQUIRED_SEEDS) {
            Game game = new Game();
            game.newWorld(seed, true);
            Settlement fortress = findFortress(game.world, true);
            assertNotNull(fortress, "hostile fortress for seed " + seed);
            SettlementBuilder.Layout layout = game.world.layoutFor(fortress);
            game.world.ensureChunks(fortress.center.x(), fortress.center.z(), 5, 10_000);
            coveredNegativeWorldCoordinates |= fortress.center.x() < 0
                    || fortress.center.z() < 0;

            TraversalBounds fortBounds = new TraversalBounds(
                    fortress.center.x() - 33, fortress.center.x() + 33,
                    Math.max(1, fortress.center.y() - 18), fortress.center.y() + 24,
                    fortress.center.z() - 33, fortress.center.z() + 33);

            // Discover from the normal exterior approach rather than marking map state.
            Vec3i postern = layout.infiltrationPoints.getFirst();
            Vec3i posternOutside = standableNear(game.world,
                    postern.offset(0, 0, -3), 2, 8);
            Vec3i posternInside = standableNear(game.world,
                    postern.offset(0, 0, 3), 2, 8);
            assertNotNull(posternOutside, "rear exterior footing for seed " + seed);
            assertNotNull(posternInside, "rear courtyard footing for seed " + seed);
            assertFalse(fortress.discovered, "remote fort begins undiscovered for seed " + seed);
            game.player.pos.set(posternOutside.x() + 0.5f, posternOutside.y(),
                    posternOutside.z() + 0.5f);
            game.settlementManager.slowTick(game, 0.1f);
            assertTrue(fortress.discovered,
                    "normal proximity discovery marks the infiltrated fort for seed " + seed);

            TraversalBounds posternCorridor = corridorBounds(
                    posternOutside, posternInside, 3, 8);
            assertTrue(playerReachable(game.world, posternOutside, posternCorridor, 4_000)
                            .contains(posternInside),
                    "standing-height rear postern crosses the generated wall for seed " + seed);

            Vec3i outerGate = fortress.gates.getFirst();
            Vec3i innerGate = fortress.gates.get(1);
            Vec3i courtyard = standableNear(game.world,
                    innerGate.offset(0, 0, 4), 3, 8);
            assertNotNull(courtyard, "courtyard footing for seed " + seed);
            assertTrue(playerReachable(game.world, posternInside, fortBounds, 80_000)
                            .contains(courtyard),
                    "rear infiltration joins the courtyard route for seed " + seed);

            // A hostile gate is genuinely closed to player clearance. Breach a
            // two-high column through the same mining outcome command used after
            // hold-to-mine completes, then prove the direct approach crosses it.
            Vec3i gateOutside = standableNear(game.world,
                    outerGate.offset(0, 0, 3), 2, 8);
            Vec3i gateInside = standableNear(game.world,
                    outerGate.offset(0, 0, -3), 2, 8);
            assertNotNull(gateOutside);
            assertNotNull(gateInside);
            TraversalBounds outerCorridor = corridorBounds(
                    gateOutside, gateInside, 2, 8);
            assertFalse(playerReachable(game.world, gateOutside, outerCorridor, 4_000)
                            .contains(gateInside),
                    "closed hostile outer gate blocks the direct route for seed " + seed);

            game.player.inventory.set(0, new ItemStack(ItemType.IRON_AXE, 1));
            game.player.hotbarSel = 0;
            breachGateColumn(game, outerGate, seed);
            Set<Vec3i> directRoute = playerReachable(
                    game.world, gateOutside, outerCorridor, 4_000);
            assertTrue(directRoute.contains(gateInside),
                    "normal gate breach opens the direct courtyard route for seed " + seed
                            + " (gate " + outerGate + ", outside " + gateOutside
                            + ", inside " + gateInside + ", reachable "
                            + directRoute.size() + ")");

            Vec3i innerOutside = standableNear(game.world,
                    innerGate.offset(0, 0, 3), 2, 8);
            Vec3i innerInside = standableNear(game.world,
                    innerGate.offset(0, 0, -3), 2, 8);
            assertNotNull(innerOutside);
            assertNotNull(innerInside);
            TraversalBounds innerCorridor = corridorBounds(
                    innerOutside, innerInside, 2, 8);
            assertFalse(playerReachable(game.world, innerOutside, innerCorridor, 4_000)
                            .contains(innerInside),
                    "closed inner command gate blocks the keep route for seed " + seed);
            breachGateColumn(game, innerGate, seed);
            assertTrue(playerReachable(game.world, innerOutside, innerCorridor, 4_000)
                            .contains(innerInside),
                    "normal inner breach opens the command route for seed " + seed);

            Set<Vec3i> reachable = playerReachable(
                    game.world, courtyard, fortBounds, 100_000);
            assertReachableNear(game.world, reachable, fortress.alarmBell, 2, 5,
                    "alarm suppression route for seed " + seed);
            assertReachableNear(game.world, reachable,
                    fortress.prisonPos.offset(-2, 0, 0), 1, 5,
                    "captive cage approach for seed " + seed);
            assertReachableNear(game.world, reachable, fortress.magazinePos, 1, 5,
                    "powder-magazine route for seed " + seed);
            assertReachableNear(game.world, reachable, fortress.leaderPost, 0, 1,
                    "ladder-connected leader command route for seed " + seed);
            Vec3i centralObjective = layout.campfires.keySet().iterator().next();
            assertReachableNear(game.world, reachable, centralObjective, 2, 5,
                    "central-control objective route for seed " + seed);
            for (Vec3i patrolPoint : fortress.patrolPoints) {
                assertReachableNear(game.world, reachable, patrolPoint, 0, 1,
                        "generated patrol circuit point " + patrolPoint + " for seed " + seed);
            }
        }
        assertTrue(coveredNegativeWorldCoordinates,
                "required-seed traversal includes a generated fortress at negative coordinates");
    }

    @Test
    void providerTargetedCaptureQuestCompletesThroughRescueMeleeAlarmAndCampfireThenPersists(
            @TempDir Path dir) {
        Game game = new Game();
        game.newWorld(20_260_716L, true);
        Settlement fortress = findFortress(game.world, true);
        assertNotNull(fortress);
        SettlementBuilder.Layout layout = game.world.layoutFor(fortress);
        game.world.ensureChunks(fortress.center.x(), fortress.center.z(), 5, 10_000);

        // Keep the provider deterministic: this is the only hostile fort in its offer pool.
        game.world.settlements.entrySet().removeIf(e -> e.getKey() != fortress.id
                && e.getValue().hostile());
        Settlement providerHome = providerSettlement(fortress);
        game.world.settlements.put(providerHome.id, providerHome);
        Npc provider = providerNpc(game, providerHome);
        game.faction.makeQuestOfferAvailable();
        Quest quest = game.faction.offerSettlementQuestOfType(
                game, provider, providerHome, Quest.Type.CAPTURE_FORT);
        assertNotNull(quest, "a normal settlement provider offers the capture request");
        assertEquals(fortress.id, quest.targetSettlementId);
        assertEquals(Quest.Status.ACTIVE, quest.status);

        // Activate the real garrison, including the protected prisoner.
        game.player.health = 10_000f;
        game.player.pos.set(fortress.center.x() + 0.5f, fortress.center.y() + 1f,
                fortress.center.z() + 0.5f);
        game.settlementManager.slowTick(game, 1f);
        Npc captive = game.entities.npcs.stream()
                .filter(n -> n.settlementId == fortress.id
                        && n.archetype == NpcArchetype.CAPTIVE)
                .findFirst().orElse(null);
        assertNotNull(captive);

        // The fortress rescue path uses the generated cage, mining command and F action.
        Vec3i cageBar = fortress.prisonPos.offset(-1, 1, 0);
        game.player.pos.set(cageBar.x() - 0.6f, fortress.prisonPos.y(),
                fortress.prisonPos.z() + 0.5f);
        assertEquals(Game.NpcInteraction.RESCUE_BLOCKED, game.npcInteraction(captive));
        game.player.inventory.set(0, new ItemStack(ItemType.IRON_PICKAXE, 1));
        game.player.hotbarSel = 0;
        assertTrue(game.completePlayerBlockBreak(cageBar));
        assertEquals(Game.NpcInteraction.RESCUE_READY, game.npcInteraction(captive));
        assertTrue(game.interactWithNearbyNpc());
        assertFalse(game.entities.npcs.contains(captive));

        // Defeat the leader and attrit actual live defenders until survivors rout.
        game.player.inventory.set(0, new ItemStack(ItemType.IRON_SPEAR, 1));
        Npc leader = game.entities.npcs.stream()
                .filter(n -> n.settlementId == fortress.id && n.archetype != null
                        && n.archetype.leader)
                .findFirst().orElse(null);
        assertNotNull(leader);
        defeatWithMeleeCommand(game, leader);
        int patrolsDefeated = 0;
        while (fortress.residents.stream().noneMatch(r -> r.routed || r.surrendered)) {
            Npc defender = game.entities.npcs.stream()
                    .filter(n -> !n.dead && n.settlementId == fortress.id
                            && n.residentIndex >= 0
                            && fortress.countsAsDefender(
                            fortress.residents.get(n.residentIndex)))
                    .findFirst().orElse(null);
            assertNotNull(defender, "morale must resolve before the active garrison vanishes");
            defeatWithMeleeCommand(game, defender);
            patrolsDefeated++;
            assertTrue(patrolsDefeated < fortress.residents.size(), "bounded attrition");
        }
        assertTrue(patrolsDefeated > 0);
        assertTrue(fortress.commandNeutralized, "real leader death neutralizes command");
        assertFalse(fortress.cleared, "alarm and central control still gate clearing");

        // Mine the generated alarm, then interact with the generated command campfire.
        assertEquals(BlockType.ALARM_BELL, game.world.getBlock(
                fortress.alarmBell.x(), fortress.alarmBell.y(), fortress.alarmBell.z()));
        assertTrue(game.completePlayerBlockBreak(fortress.alarmBell));
        assertTrue(fortress.alarmNeutralized);
        assertFalse(fortress.cleared);
        Vec3i commandFire = layout.campfires.keySet().iterator().next();
        assertTrue(game.interactWithBlockAt(commandFire));
        assertTrue(fortress.centralObjectiveControlled);
        assertTrue(fortress.cleared, "staged production events clear the fortress");

        // Supply/claim through F at the same fire, completing the exact targeted quest.
        game.player.inventory.add(ItemType.COOKED_MEAT, SettlementManager.OCCUPY_FOOD);
        game.player.inventory.add(ItemType.LOG, SettlementManager.OCCUPY_WOOD);
        assertTrue(game.interactWithBlockAt(commandFire));
        assertTrue(fortress.occupied);
        assertEquals(Quest.Status.READY_TO_TURN_IN, quest.status);
        assertEquals(1, quest.progress);
        String turnIn = new NpcScreen().performSettlementQuestAction(
                game, provider, providerHome);
        assertTrue(turnIn.contains("thanks"), turnIn);
        assertNull(game.faction.quest, "the matching provider pays the reward exactly once");

        // The synthetic provider is not part of the procedural world snapshot.
        game.world.settlements.remove(providerHome.id);
        Path save = dir.resolve("fortress-objective.sav");
        assertTrue(SaveSystem.save(game, save));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save));
        Settlement restored = loaded.world.settlements.get(fortress.id);
        assertNotNull(restored);
        assertTrue(restored.cleared && restored.occupied);
        assertTrue(restored.commandNeutralized && restored.alarmNeutralized
                && restored.centralObjectiveControlled);
        assertTrue(restored.residents.stream().anyMatch(r -> r.routed || r.surrendered));
        assertEquals(BlockType.AIR, loaded.world.getBlock(cageBar.x(), cageBar.y(), cageBar.z()));
        assertEquals(BlockType.AIR, loaded.world.getBlock(fortress.alarmBell.x(),
                fortress.alarmBell.y(), fortress.alarmBell.z()));
    }

    @Test
    void placedAndIgnitedKegFuseSurvivesSaveThenBreachesGeneratedHostileGate(
            @TempDir Path dir) {
        Game game = new Game();
        game.newWorld(987_654_321L, true);
        Settlement fortress = findFortress(game.world, true);
        assertNotNull(fortress);
        game.world.layoutFor(fortress);
        game.world.ensureChunks(fortress.center.x(), fortress.center.z(), 5, 10_000);
        Vec3i gate = fortress.gates.getFirst();
        assertEquals(BlockType.GATE, game.world.getBlock(gate.x(), gate.y(), gate.z()));
        // The stepped wall follows its own terrain height, which need not match
        // the gate column's base Y.
        Vec3i reinforcedFlank = new Vec3i(gate.x() - 2,
                game.world.generator.heightAt(gate.x() - 2, gate.z()) + 1, gate.z());
        assertEquals(BlockType.STONE_BRICK, game.world.getBlock(
                reinforcedFlank.x(), reinforcedFlank.y(), reinforcedFlank.z()));

        Vec3i keg = placeableKegCell(game, gate);
        assertNotNull(keg, "the generated outer approach has a placeable breach cell");
        game.player.pos.set(keg.x() + 4.5f, keg.y(), keg.z() + 4.5f);
        game.player.inventory.set(0, new ItemStack(ItemType.POWDER_KEG, 1));
        game.player.hotbarSel = 0;
        assertTrue(game.placeSelectedBlockAt(keg.x(), keg.y(), keg.z()),
                "RMB gameplay placement command");
        assertEquals(BlockType.POWDER_KEG,
                game.world.getBlock(keg.x(), keg.y(), keg.z()));
        assertTrue(game.interactWithBlockAt(keg), "F gameplay interaction lights the fuse");
        assertEquals(5f, game.world.kegFuses.get(keg), 0.001f);

        game.explosions.tickFuses(game, 1.75f);
        Path armed = dir.resolve("armed-gate-keg.sav");
        assertTrue(SaveSystem.save(game, armed));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, armed));
        Settlement restoredFortress = loaded.world.settlements.get(fortress.id);
        assertNotNull(restoredFortress);
        assertEquals(BlockType.POWDER_KEG, loaded.world.getBlock(keg.x(), keg.y(), keg.z()));
        assertEquals(3.25f, loaded.world.kegFuses.get(keg), 0.02f,
                "remaining fuse time survives save/load");

        float localBefore = restoredFortress.localReputation;
        float factionBefore = loaded.world.factionReputation
                .getOrDefault(restoredFortress.factionId, 0f);
        loaded.explosions.tickFuses(loaded, 3.5f);
        assertEquals(BlockType.AIR, loaded.world.getBlock(keg.x(), keg.y(), keg.z()));
        assertTrue(loaded.world.getBlock(gate.x(), gate.y(), gate.z()) == BlockType.AIR
                        || loaded.world.getBlock(gate.x() + 1, gate.y(), gate.z()) == BlockType.AIR,
                "the real generated weak gate is breached");
        assertEquals(BlockType.STONE_BRICK, loaded.world.getBlock(
                reinforcedFlank.x(), reinforcedFlank.y(), reinforcedFlank.z()),
                "reinforced generated walls survive the focused gate charge");
        assertEquals(localBefore, restoredFortress.localReputation, 0.001f,
                "breaching an enemy gate does not punish unrelated local reputation");
        assertEquals(factionBefore, loaded.world.factionReputation
                .getOrDefault(restoredFortress.factionId, 0f), 0.001f);
        assertFalse(loaded.world.kegFuses.containsKey(keg));
        assertTrue(loaded.world.changedBlocks.containsKey(keg));
        assertTrue(loaded.world.changedBlocks.containsKey(gate)
                        || loaded.world.changedBlocks.containsKey(gate.offset(1, 0, 0)),
                "batched blast edits are persisted as changed blocks");
    }

    private static void defeatWithMeleeCommand(Game game, Npc npc) {
        int swings = 0;
        while (!npc.dead) {
            game.player.pos.set(npc.pos.x - 1f, npc.pos.y, npc.pos.z);
            game.advancePlayerAttackCooldown(0.5f);
            assertTrue(game.performPlayerAttack(npc));
            assertTrue(++swings < 20, "melee resolution remains bounded for " + npc.name);
        }
        game.entities.fastTick(game, 0f);
        assertFalse(game.entities.npcs.contains(npc));
    }

    private static Vec3i placeableKegCell(Game game, Vec3i gate) {
        for (int[] offset : new int[][]{{0, 0, 1}, {1, 0, 1}, {0, 0, -1}, {1, 0, -1}}) {
            Vec3i p = gate.offset(offset[0], offset[1], offset[2]);
            if (game.world.getBlock(p.x(), p.y(), p.z()).isReplaceable()
                    && game.world.getBlock(p.x(), p.y() - 1, p.z()).solid) {
                return p;
            }
        }
        return null;
    }

    private static void breachGateColumn(Game game, Vec3i gate, long seed) {
        assertEquals(BlockType.GATE,
                game.world.getBlock(gate.x(), gate.y(), gate.z()),
                "lower gate block before breach for seed " + seed);
        assertEquals(BlockType.GATE,
                game.world.getBlock(gate.x(), gate.y() + 1, gate.z()),
                "upper gate block before breach for seed " + seed);
        assertTrue(game.completePlayerBlockBreak(gate),
                "normal lower gate mining completion for seed " + seed);
        assertTrue(game.completePlayerBlockBreak(gate.offset(0, 1, 0)),
                "normal upper gate mining completion for seed " + seed);
    }

    private record TraversalBounds(int minX, int maxX, int minY, int maxY,
                                   int minZ, int maxZ) {
        boolean contains(Vec3i p) {
            return p.x() >= minX && p.x() <= maxX
                    && p.y() >= minY && p.y() <= maxY
                    && p.z() >= minZ && p.z() <= maxZ;
        }
    }

    private static TraversalBounds corridorBounds(Vec3i a, Vec3i b,
                                                   int horizontalMargin,
                                                   int verticalMargin) {
        return new TraversalBounds(
                Math.min(a.x(), b.x()) - horizontalMargin,
                Math.max(a.x(), b.x()) + horizontalMargin,
                Math.max(1, Math.min(a.y(), b.y()) - verticalMargin),
                Math.max(a.y(), b.y()) + verticalMargin,
                Math.min(a.z(), b.z()) - horizontalMargin,
                Math.max(a.z(), b.z()) + horizontalMargin);
    }

    /**
     * Bounded geometry traversal using the production walk rules: two-block
     * player clearance, one-block step-up, three-block safe drop and ladder
     * vertical motion. Unlike settled-NPC pathing, a closed gate remains solid.
     */
    private static Set<Vec3i> playerReachable(World world, Vec3i start,
                                               TraversalBounds bounds,
                                               int expansionBudget) {
        Set<Vec3i> visited = new HashSet<>();
        if (start == null || !bounds.contains(start) || !playerStandable(world, start)) {
            return visited;
        }
        ArrayDeque<Vec3i> open = new ArrayDeque<>();
        visited.add(start);
        open.add(start);
        int expansions = 0;
        while (!open.isEmpty() && expansions++ < expansionBudget) {
            Vec3i current = open.removeFirst();
            if (world.getBlock(current.x(), current.y(), current.z()).isClimbable()) {
                enqueueIfStandable(world, bounds, visited, open,
                        current.offset(0, 1, 0));
                enqueueIfStandable(world, bounds, visited, open,
                        current.offset(0, -1, 0));
            }
            for (int[] direction : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = current.x() + direction[0];
                int z = current.z() + direction[1];
                Vec3i same = new Vec3i(x, current.y(), z);
                if (playerStandable(world, same)) {
                    enqueue(bounds, visited, open, same);
                    continue;
                }
                Vec3i stepUp = new Vec3i(x, current.y() + 1, z);
                if (playerStandable(world, stepUp)
                        && playerPassable(world, current.x(), current.y() + 2,
                        current.z())) {
                    enqueue(bounds, visited, open, stepUp);
                    continue;
                }
                for (int drop = 1; drop <= 3; drop++) {
                    if (!playerPassable(world, x, current.y() - drop + 1, z)) {
                        break;
                    }
                    Vec3i stepDown = new Vec3i(x, current.y() - drop, z);
                    if (playerStandable(world, stepDown)) {
                        enqueue(bounds, visited, open, stepDown);
                        break;
                    }
                }
            }
        }
        return visited;
    }

    private static void enqueueIfStandable(World world, TraversalBounds bounds,
                                             Set<Vec3i> visited, ArrayDeque<Vec3i> open,
                                             Vec3i candidate) {
        if (playerStandable(world, candidate)) {
            enqueue(bounds, visited, open, candidate);
        }
    }

    private static void enqueue(TraversalBounds bounds, Set<Vec3i> visited,
                                ArrayDeque<Vec3i> open, Vec3i candidate) {
        if (bounds.contains(candidate) && visited.add(candidate)) {
            open.addLast(candidate);
        }
    }

    private static boolean playerStandable(World world, Vec3i p) {
        if (world.getChunk(Math.floorDiv(p.x(), 16), Math.floorDiv(p.z(), 16)) == null
                || !playerPassable(world, p.x(), p.y(), p.z())
                || !playerPassable(world, p.x(), p.y() + 1, p.z())) {
            return false;
        }
        BlockType below = world.getBlock(p.x(), p.y() - 1, p.z());
        BlockType feet = world.getBlock(p.x(), p.y(), p.z());
        return below.solid || feet.isClimbable() || feet == BlockType.WATER;
    }

    private static boolean playerPassable(World world, int x, int y, int z) {
        BlockType block = world.getBlock(x, y, z);
        return !block.solid || block.isClimbable();
    }

    private static Vec3i standableNear(World world, Vec3i nominal,
                                       int horizontalRadius, int verticalRadius) {
        Vec3i best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
            for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                for (int dy = -verticalRadius; dy <= verticalRadius; dy++) {
                    Vec3i candidate = nominal.offset(dx, dy, dz);
                    if (!playerStandable(world, candidate)) {
                        continue;
                    }
                    int distance = Math.abs(dx) + Math.abs(dz) + Math.abs(dy);
                    if (distance < bestDistance) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    private static void assertReachableNear(World world, Set<Vec3i> reachable,
                                            Vec3i target, int horizontalRadius,
                                            int verticalRadius, String message) {
        Vec3i standable = standableNear(
                world, target, horizontalRadius, verticalRadius);
        assertNotNull(standable, message + " has standable target");
        assertTrue(reachable.contains(standable),
                message + " reaches " + standable);
    }

    private static Settlement providerSettlement(Settlement fortress) {
        int regionX = 10_001;
        int regionZ = 10_002;
        return new Settlement(Settlement.packId(regionX, regionZ), regionX, regionZ,
                SettlementType.VILLAGE,
                new Vec3i(fortress.center.x() + 100, fortress.center.y(),
                        fortress.center.z() + 100),
                HumanFaction.FRONTIER, Settlement.Alignment.FRIENDLY);
    }

    private static Npc providerNpc(Game game, Settlement home) {
        Npc provider = new Npc(game.world, "Fortress Liaison");
        provider.archetype = NpcArchetype.TRADER;
        provider.isTrader = true;
        provider.settlementId = home.id;
        provider.residentIndex = 0;
        return provider;
    }

    private static Settlement findFortress(World world, boolean hostile) {
        for (int radius = 3; radius <= 22; radius++) {
            for (int rx = -radius; rx <= radius; rx++) {
                for (int rz : new int[]{-radius, radius}) {
                    Settlement candidate = world.settlementForRegion(rx, rz);
                    if (candidate != null && candidate.type == SettlementType.FORTRESS
                            && (!hostile || candidate.hostile())) {
                        return candidate;
                    }
                }
            }
            for (int rz = -radius + 1; rz < radius; rz++) {
                for (int rx : new int[]{-radius, radius}) {
                    Settlement candidate = world.settlementForRegion(rx, rz);
                    if (candidate != null && candidate.type == SettlementType.FORTRESS
                            && (!hostile || candidate.hostile())) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private static void assertBlock(SettlementBuilder.Layout layout, Vec3i pos,
                                    BlockType expected) {
        Byte id = layout.blocks.get(SettlementBuilder.pack(pos.x(), pos.y(), pos.z()));
        assertNotNull(id, "layout entry at " + pos);
        assertEquals(expected, BlockType.byId(id), "block at " + pos);
    }
}
