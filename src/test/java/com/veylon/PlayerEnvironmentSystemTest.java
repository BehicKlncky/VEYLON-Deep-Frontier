package com.veylon;

import com.veylon.simulation.EventSystem;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.Poi;
import com.veylon.world.World;
import com.veylon.world.WorldGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The medium-tick coupling between the world and the player's body.
 *
 * <p>The fumarole tests exist because v0.5.0 reordered the predicates in
 * {@code World.isBasaltFumarole}. Every term is pure, so the reordering is
 * exactly equivalent — but "exactly equivalent" is the kind of claim that
 * deserves a test rather than an argument, and there was none: the generation
 * tests cover fumaroles being *placed*, and nothing covered them being *found*.
 *
 * <p>A fumarole is deliberately not a block type. It is exposed sulfur under
 * two cells of air with an ash rim, inside the basalt depth band — a shape,
 * recognised by query, so that adding it needed no new serialized block id and
 * so that ordinary sulfur veins stay safe to mine. Every clause below is part
 * of that definition and each one is tested for on its own.
 */
class PlayerEnvironmentSystemTest {

    /** Depth below the surface that lands inside the basalt cave-zone band. */
    private static final int BASALT_DEPTH = 20;

    @Test
    void aBuiltFumaroleIsFound() {
        Game game = worldAt(880_001L);
        Vec3i vent = buildFumarole(game, 6, 6);

        Vec3i found = game.world.nearestBasaltFumarole(
                vent.x() + 0.5f, vent.y() + 0.5f, vent.z() + 0.5f, 5);

        assertEquals(vent, found, "a well-formed vent must be found from on top of it");
    }

    @Test
    void theNearestOfTwoVentsWins() {
        Game game = worldAt(880_002L);
        Vec3i near = buildFumarole(game, 6, 6);
        Vec3i far = buildFumarole(game, 9, 6);

        Vec3i found = game.world.nearestBasaltFumarole(
                near.x() + 0.5f, near.y() + 0.5f, near.z() + 0.5f, 6);

        assertEquals(near, found, "the search must return the closest vent, not the first");
        assertTrue(game.world.isBasaltFumarole(far.x(), far.y(), far.z()),
                "precondition: the second vent is also well-formed");
    }

    @Test
    void anOrdinarySulfurVeinIsNotAFumarole() {
        // This is the whole reason the shape has an ash rim: mining sulfur must
        // not be gated behind hazard handling.
        Game game = worldAt(880_003L);
        Vec3i vent = buildFumarole(game, 6, 6);
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            game.world.setBlock(vent.x() + dir[0], vent.y() - 1, vent.z() + dir[1],
                    BlockType.BASALT, true);
        }

        assertFalse(game.world.isBasaltFumarole(vent.x(), vent.y(), vent.z()),
                "sulfur without an ash rim is a vein, not a vent");
        assertNull(game.world.nearestBasaltFumarole(
                        vent.x() + 0.5f, vent.y() + 0.5f, vent.z() + 0.5f, 5),
                "and the search must not find it either");
    }

    @Test
    void aBuriedOrCappedVentIsNotAFumarole() {
        Game game = worldAt(880_004L);
        Vec3i vent = buildFumarole(game, 6, 6);

        game.world.setBlock(vent.x(), vent.y() + 1, vent.z(), BlockType.BASALT, true);
        assertFalse(game.world.isBasaltFumarole(vent.x(), vent.y(), vent.z()),
                "a vent needs two cells of headroom to vent into");

        game.world.setBlock(vent.x(), vent.y() + 1, vent.z(), BlockType.AIR, true);
        game.world.setBlock(vent.x(), vent.y(), vent.z(), BlockType.BASALT, true);
        assertFalse(game.world.isBasaltFumarole(vent.x(), vent.y(), vent.z()),
                "a vent plugged at the mouth is not venting");
    }

    @Test
    void aVentOutsideTheBasaltBandIsNotAFumarole() {
        Game game = worldAt(880_005L);
        // Same shape, eight blocks under the surface: root-cave depth, which
        // runs from five down to fourteen, above the basalt band.
        Vec3i shallow = buildFumaroleAtDepth(game, 6, 6, 8);

        assertEquals(WorldGenerator.CaveZone.ROOT,
                game.world.generator.caveZoneAt(shallow.x(), shallow.y(), shallow.z()),
                "precondition: this cell is above the basalt band");
        assertFalse(game.world.isBasaltFumarole(shallow.x(), shallow.y(), shallow.z()),
                "the hazard belongs to the basalt depths");
    }

    @Test
    void standingOverAVentBuildsSmokeExposure() {
        Game game = worldAt(880_006L);
        Vec3i vent = buildFumarole(game, 6, 6);
        game.player.pos.set(vent.x() + 0.5f, vent.y(), vent.z() + 0.5f);
        game.player.smokeExposure = 0;

        game.mediumTick(SimulationScheduler.MEDIUM_DT);

        assertTrue(game.player.smokeExposure > 0,
                "a fumarole underfoot must fill the player's lungs");
    }

    @Test
    void walkingIntoAPoiDiscoversItAndRecordsThePosition() {
        Game game = worldAt(880_007L);
        Vec3i at = new Vec3i((int) game.player.pos.x + 3, (int) game.player.pos.y,
                (int) game.player.pos.z + 3);
        Poi poi = new Poi(Poi.PoiType.SUPPLY_CACHE, at);
        game.world.pois.add(poi);
        assertFalse(poi.discovered, "precondition: undiscovered");

        game.mediumTick(SimulationScheduler.MEDIUM_DT);

        assertTrue(poi.discovered, "a POI within range must be discovered");
        assertTrue(game.world.discoveredPois.contains(at),
                "discovery is recorded by position so it survives regeneration");
    }

    @Test
    void aDistantPoiIsNotDiscovered() {
        Game game = worldAt(880_008L);
        Poi poi = new Poi(Poi.PoiType.SUPPLY_CACHE,
                new Vec3i((int) game.player.pos.x + 400, 40,
                        (int) game.player.pos.z + 400));
        game.world.pois.add(poi);

        game.mediumTick(SimulationScheduler.MEDIUM_DT);

        assertFalse(poi.discovered, "a POI four hundred blocks away is not discovered");
    }

    @Test
    void theCampIllnessEventOutlastsTheLastPatientAndNotAMomentLonger() {
        Game game = worldAt(880_009L);
        assertFalse(game.entities.npcs.isEmpty(), "precondition: the camp is populated");
        game.events.active.add(new EventSystem.ActiveEvent(
                EventSystem.EventType.NPC_ILLNESS, 10_000f, 1f));
        game.entities.npcs.get(0).sick = true;

        game.slowTick(SimulationScheduler.SLOW_DT);
        assertTrue(game.events.isActive(EventSystem.EventType.NPC_ILLNESS),
                "the event must persist while anyone is still sick");

        game.entities.npcs.forEach(n -> n.sick = false);
        game.slowTick(SimulationScheduler.SLOW_DT);

        assertFalse(game.events.isActive(EventSystem.EventType.NPC_ILLNESS),
                "and end when the last patient recovers");
    }

    // ------------------------------------------------------------------

    private static Game worldAt(long seed) {
        Game game = new Game();
        game.newWorld(seed, true);
        return game;
    }

    /** Carves the vent shape at basalt depth, offset from spawn by (dx, dz). */
    private static Vec3i buildFumarole(Game game, int dx, int dz) {
        return buildFumaroleAtDepth(game, dx, dz, BASALT_DEPTH);
    }

    /**
     * Builds the shape {@code isBasaltFumarole} recognises: exposed sulfur with
     * two cells of air above it and an ash rim on at least two sides.
     *
     * <p>Depth is measured from {@code generator.heightAt}, not from
     * {@code surfaceHeight}. The two differ wherever a cave has been carved
     * under the column, and the cave-zone band the query tests is derived from
     * the generator height — so measuring from the heightmap would place the
     * vent in a different band on some seeds and not others.
     */
    private static Vec3i buildFumaroleAtDepth(Game game, int dx, int dz, int depth) {
        World world = game.world;
        int x = (int) game.player.pos.x + dx;
        int z = (int) game.player.pos.z + dz;
        world.ensureChunks(x, z, 1, 10_000);
        int y = world.generator.heightAt(x, z) - depth;
        assertTrue(y > 3 && y < Chunk.SY - 3, "the test vent must fit in the column");

        world.setBlock(x, y - 1, z, BlockType.SULFUR_ORE, true);
        world.setBlock(x, y, z, BlockType.AIR, true);
        world.setBlock(x, y + 1, z, BlockType.AIR, true);
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            world.setBlock(x + dir[0], y - 1, z + dir[1], BlockType.ASH, true);
            world.setBlock(x + dir[0], y, z + dir[1], BlockType.AIR, true);
        }
        if (depth == BASALT_DEPTH) {
            assertTrue(world.isBasaltFumarole(x, y, z),
                    "precondition: a basalt-depth vent built this way is recognised");
        }
        return new Vec3i(x, y, z);
    }
}
