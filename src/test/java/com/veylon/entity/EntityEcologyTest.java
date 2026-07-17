package com.veylon.entity;

import com.veylon.Game;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Poi;
import com.veylon.world.World;
import com.veylon.world.WorldGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityEcologyTest {

    private static final Vec3i CAVERN = new Vec3i(200, 8, 200);

    @Test
    void gloomstalkerUsesNearbyDarkNestTerritory() {
        Game g = darkCavern(20260716L, 28);
        Vec3i nest = CAVERN;
        g.world.pois.add(new Poi(Poi.PoiType.STALKER_NEST, nest));
        g.player.pos.set(nest.x() + 25.5f, nest.y() + 0.1f, nest.z() + 0.5f);
        g.entities.setRandomSeed(7L);

        assertTrue(g.entities.trySpawnStalker(g, g.player.pos.x, g.player.pos.z));
        assertEquals(1, g.entities.creatures.stream()
                .filter(c -> c.type == Creature.CreatureType.STALKER).count());
        Creature stalker = g.entities.creatures.getLast();
        assertTrue(stalker.distSqTo(nest.x(), nest.y(), nest.z()) <= 12 * 12,
                "the predator materializes inside its registered nest territory");
        assertTrue(stalker.distSqTo(g.player) > 12 * 12,
                "nest spawning keeps the full player exclusion radius");
        assertFalse(stalker.collidesAt(stalker.pos.x, stalker.pos.y, stalker.pos.z),
                "the spawned predator has solid footing and two-cell clearance");
    }

    @Test
    void gloomstalkerRetreatsFromActualFireAndFromTheStrongLightSource() {
        Game g = darkCavern(42L, 24);
        g.player.pos.set(CAVERN.x() + 0.5f, CAVERN.y() + 0.1f, CAVERN.z() + 9.5f);
        Creature stalker = spawnStalker(g, CAVERN.x() + 0.5f, CAVERN.z() + 0.5f);
        Vec3i burningLog = CAVERN.offset(4, 0, 0);
        g.world.setBlock(burningLog.x(), burningLog.y(), burningLog.z(), BlockType.LOG, false);
        assertTrue(g.fire.ignite(g, burningLog.x(), burningLog.y(), burningLog.z()));
        assertTrue(g.world.blockLight((int) stalker.pos.x, (int) stalker.pos.y,
                        (int) stalker.pos.z) < 0.2f,
                "the burning simulation cell deliberately contributes no voxel light");

        g.entities.fastTick(g, 0.05f);

        assertEquals(Creature.CreatureState.FLEE, stalker.state);
        assertTrue(stalker.vel.x < -1f,
                "an actual fire east of the stalker drives it west even in darkness");

        g.entities.creatures.clear();
        g.fire.reset();
        g.world.setBlock(burningLog.x(), burningLog.y(), burningLog.z(), BlockType.AIR, false);
        g.player.pos.set(CAVERN.x() - 9.5f, CAVERN.y() + 0.1f, CAVERN.z() + 0.5f);
        stalker = spawnStalker(g, CAVERN.x() + 0.5f, CAVERN.z() + 0.5f);
        Vec3i torch = CAVERN.offset(2, 0, 0);
        g.world.setBlock(torch.x(), torch.y(), torch.z(), BlockType.TORCH, false);
        assertTrue(g.world.blockLight((int) stalker.pos.x, (int) stalker.pos.y,
                        (int) stalker.pos.z) > 0.42f);

        g.entities.fastTick(g, 0.05f);

        assertEquals(Creature.CreatureState.FLEE, stalker.state);
        assertTrue(stalker.vel.x < -1f,
                "retreat follows the light gradient, not away from the player west of it");
    }

    @Test
    void darknessProducesLateralStalkThenBoundedAmbushInsteadOfStraightChase() {
        Game g = darkCavern(999L, 28);
        g.player.pos.set(CAVERN.x() + 0.5f, CAVERN.y() + 0.1f, CAVERN.z() + 0.5f);
        Creature stalker = spawnStalker(g, CAVERN.x() + 14.5f, CAVERN.z() + 0.5f);
        float directX = g.player.pos.x - stalker.pos.x;
        float directZ = g.player.pos.z - stalker.pos.z;
        float directLength = (float) Math.sqrt(directX * directX + directZ * directZ);
        directX /= directLength;
        directZ /= directLength;

        g.entities.fastTick(g, 0.05f);

        assertEquals(Creature.CreatureState.STALK, stalker.state);
        float forward = stalker.vel.x * directX + stalker.vel.z * directZ;
        float lateral = Math.abs(stalker.vel.x * directZ - stalker.vel.z * directX);
        assertTrue(forward > 0.5f, "the stalk still closes distance");
        assertTrue(lateral > 1f, "the ranged approach is a genuine lateral flank");
        assertTrue(Math.hypot(stalker.vel.x, stalker.vel.z) < stalker.type.speed,
                "stalking is slower than ordinary locomotion");

        g.entities.creatures.clear();
        stalker = spawnStalker(g, CAVERN.x() + 4.5f, CAVERN.z() + 0.5f);
        g.entities.fastTick(g, 0.05f);
        assertEquals(Creature.CreatureState.HUNT, stalker.state,
                "reaching the ambush radius changes phase");
        assertTrue(Math.hypot(stalker.vel.x, stalker.vel.z) > stalker.type.speed,
                "the ambush is a brief burst rather than the same stalking speed");

        g.entities.creatures.clear();
        stalker = spawnStalker(g, CAVERN.x() + 1.8f, CAVERN.z() + 0.5f);
        float healthBefore = g.player.health;
        g.entities.fastTick(g, 0.05f);
        assertEquals(Creature.CreatureState.ATTACK, stalker.state);
        assertTrue(g.player.health < healthBefore,
                "the production entity tick resolves the close-range ambush hit");
    }

    @Test
    void illuminatedPreyIsNotPursuedUntilItReturnsToDarkness() {
        Game g = darkCavern(-1234567L, 24);
        g.player.pos.set(CAVERN.x() + 0.5f, CAVERN.y() + 0.1f, CAVERN.z() + 0.5f);
        Vec3i playerTorch = CAVERN.offset(1, 0, 0);
        g.world.setBlock(playerTorch.x(), playerTorch.y(), playerTorch.z(), BlockType.TORCH, false);
        Creature stalker = spawnStalker(g, CAVERN.x() + 8.5f, CAVERN.z() + 0.5f);
        assertTrue(g.world.blockLight((int) g.player.pos.x, (int) g.player.pos.y,
                (int) g.player.pos.z) > 0.32f);
        assertTrue(g.world.blockLight((int) stalker.pos.x, (int) stalker.pos.y,
                (int) stalker.pos.z) < 0.42f, "the predator itself remains outside strong light");

        g.entities.fastTick(g, 0.05f);

        assertEquals(Creature.CreatureState.FLEE, stalker.state);
        float towardPlayer = stalker.vel.x * (g.player.pos.x - stalker.pos.x)
                + stalker.vel.z * (g.player.pos.z - stalker.pos.z);
        assertTrue(towardPlayer < 0, "an illuminated target does not trigger pursuit");

        g.world.setBlock(playerTorch.x(), playerTorch.y(), playerTorch.z(), BlockType.AIR, false);
        g.entities.fastTick(g, 0.05f);
        assertEquals(Creature.CreatureState.STALK, stalker.state,
                "the same target becomes viable after its light is extinguished");
    }

    @Test
    void productionPopulationTickCapsGloomstalkersAtTwo() {
        Game g = darkCavern(987654321L, 28);
        g.world.pois.add(new Poi(Poi.PoiType.STALKER_NEST, CAVERN));
        g.player.pos.set(CAVERN.x() + 25.5f, CAVERN.y() + 0.1f, CAVERN.z() + 0.5f);
        g.entities.setRandomSeed(20260716L);

        int peak = 0;
        for (int i = 0; i < 240; i++) {
            g.entities.slowTick(g);
            int count = (int) g.entities.creatures.stream()
                    .filter(c -> c.type == Creature.CreatureType.STALKER).count();
            peak = Math.max(peak, count);
            assertTrue(count <= 2, "the production slow-tick cap is never exceeded");
        }
        assertEquals(2, peak, "the deterministic nest eventually fills both bounded slots");
    }

    @Test
    void stalkerSpawnRejectsThePlayerExclusionZoneAndSolidCells() {
        Game g = darkCavern(1L, 24);
        g.world.pois.add(new Poi(Poi.PoiType.STALKER_NEST, CAVERN));
        fillVolume(g, CAVERN, 22, BlockType.STONE);
        carveFloor(g, CAVERN, 7);
        g.player.pos.set(CAVERN.x() + 0.5f, CAVERN.y() + 0.1f, CAVERN.z() + 0.5f);
        g.entities.setRandomSeed(7L);

        assertFalse(g.entities.trySpawnStalker(g, g.player.pos.x, g.player.pos.z),
                "the only open nest cells are all inside the player exclusion radius");
        assertTrue(g.entities.creatures.isEmpty());

        fillVolume(g, CAVERN, 22, BlockType.STONE);
        g.player.pos.set(CAVERN.x() + 25.5f, CAVERN.y() + 0.1f, CAVERN.z() + 0.5f);
        g.entities.setRandomSeed(7L);
        assertFalse(g.entities.trySpawnStalker(g, g.player.pos.x, g.player.pos.z),
                "solid feet and head cells reject every nest and fallback attempt");
        assertTrue(g.entities.creatures.isEmpty());
    }

    @Test
    void generatedGloomstalkerNestContainsVisibleBoneEvidence() {
        World world = new World(987654321L, World.GEN_DEEP);
        Poi nest = null;
        for (int radius = 0; radius <= 10 && nest == null; radius++) {
            for (int cx = -radius; cx <= radius && nest == null; cx++) {
                for (int cz = -radius; cz <= radius && nest == null; cz++) {
                    if (Math.max(Math.abs(cx), Math.abs(cz)) != radius) {
                        continue;
                    }
                    world.getOrCreateChunk(cx, cz);
                    nest = world.pois.stream()
                            .filter(p -> p.type == Poi.PoiType.STALKER_NEST)
                            .findFirst().orElse(null);
                }
            }
        }

        assertNotNull(nest, "the fixed-seed search contains a generated Gloomstalker nest");
        int bones = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (world.getBlock(nest.pos.x() + dx, nest.pos.y(), nest.pos.z() + dz)
                        == BlockType.BONE_PILE) {
                    bones++;
                }
            }
        }
        assertTrue(bones >= 4, "the generated territory carries multiple visible bone piles");
    }

    @Test
    void generatedRootCaveDenSpawnsItsWolfUnderground() {
        Game game = new Game();
        game.newWorld(20260716L, true);
        Poi den = null;
        for (int radius = 0; radius <= 8 && den == null; radius++) {
            for (int cx = -radius; cx <= radius && den == null; cx++) {
                for (int cz = -radius; cz <= radius && den == null; cz++) {
                    if (Math.max(Math.abs(cx), Math.abs(cz)) != radius) {
                        continue;
                    }
                    game.world.getOrCreateChunk(cx, cz);
                    den = game.world.pois.stream()
                            .filter(p -> p.type == Poi.PoiType.PREDATOR_DEN
                                    && game.world.generator.caveZoneAt(
                                    p.pos.x(), p.pos.y(), p.pos.z())
                                    == WorldGenerator.CaveZone.ROOT
                                    && game.world.getBlock(
                                    p.pos.x(), p.pos.y(), p.pos.z())
                                    == BlockType.BONE_PILE)
                            .findFirst().orElse(null);
                }
            }
        }
        assertNotNull(den, "fixed-seed search must materialize a Root-Cave den");
        int denCx = Math.floorDiv(den.pos.x(), 16);
        int denCz = Math.floorDiv(den.pos.z(), 16);
        loadChunks(game, (denCx - 1) * 16, (denCx + 2) * 16 - 1,
                (denCz - 1) * 16, (denCz + 2) * 16 - 1);
        game.entities.creatures.clear();
        game.player.pos.set(den.pos.x() + 14.5f, den.pos.y() + 0.1f,
                den.pos.z() + 0.5f);
        game.entities.setRandomSeed(42L);

        boolean spawned = false;
        for (int i = 0; i < 12 && !spawned; i++) {
            spawned = game.entities.trySpawnUndergroundWolf(
                    game, game.player.pos.x, game.player.pos.z, den);
        }

        assertTrue(spawned, "the real den-spawn path must find bounded cave clearance");
        Creature wolf = game.entities.creatures.getLast();
        assertEquals(Creature.CreatureType.WOLF, wolf.type);
        assertTrue(Math.abs(wolf.pos.y - den.pos.y()) < 1f,
                "an underground den must not teleport its animal to surface height");
        assertTrue(wolf.distSqTo(game.player) > 10 * 10,
                "den spawning preserves the player exclusion radius");
    }

    private static Game darkCavern(long seed, int radius) {
        Game g = new Game();
        g.newWorld(seed, true);
        g.entities.creatures.clear();
        g.world.pois.clear();
        loadChunks(g, CAVERN.x() - radius, CAVERN.x() + radius,
                CAVERN.z() - radius, CAVERN.z() + radius);
        carveFloor(g, CAVERN, radius);
        g.fire.reset();
        return g;
    }

    private static Creature spawnStalker(Game g, float x, float z) {
        return g.entities.spawnCreature(g.world, Creature.CreatureType.STALKER,
                x, CAVERN.y() + 0.1f, z);
    }

    private static void carveFloor(Game g, Vec3i center, int radius) {
        g.world.beginBatch();
        for (int x = center.x() - radius; x <= center.x() + radius; x++) {
            for (int z = center.z() - radius; z <= center.z() + radius; z++) {
                g.world.setBlock(x, center.y() - 1, z, BlockType.STONE, false);
                g.world.setBlock(x, center.y(), z, BlockType.AIR, false);
                g.world.setBlock(x, center.y() + 1, z, BlockType.AIR, false);
            }
        }
        g.world.endBatch();
    }

    private static void fillVolume(Game g, Vec3i center, int radius, BlockType block) {
        g.world.beginBatch();
        for (int x = center.x() - radius; x <= center.x() + radius; x++) {
            for (int z = center.z() - radius; z <= center.z() + radius; z++) {
                for (int y = center.y() - 4; y <= center.y() + 4; y++) {
                    g.world.setBlock(x, y, z, block, false);
                }
            }
        }
        g.world.endBatch();
    }

    private static void loadChunks(Game g, int minX, int maxX, int minZ, int maxZ) {
        for (int cx = Math.floorDiv(minX, 16); cx <= Math.floorDiv(maxX, 16); cx++) {
            for (int cz = Math.floorDiv(minZ, 16); cz <= Math.floorDiv(maxZ, 16); cz++) {
                g.world.getOrCreateChunk(cx, cz);
            }
        }
    }
}
