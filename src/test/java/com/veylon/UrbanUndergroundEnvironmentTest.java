package com.veylon;

import com.veylon.engine.AudioEnvironment;
import com.veylon.util.Vec3i;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.RouteCatalogue;
import com.veylon.world.urban.TransitNetwork;
import com.veylon.world.urban.TransitStation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Milestone 14: the production shelter, weather and acoustic observations classify the new
 * places. Stations and tunnels are dark, roofed underground spaces with the shallow
 * underground's acoustics; a basement is a sheltered stone structure; a roof is open sky; and
 * the stair bulkhead keeps the rain out of the stairwell it covers.
 */
class UrbanUndergroundEnvironmentTest {

    private static void observe(Game g, Vec3i feet) {
        UrbanDiscoveryTest.standAt(g, feet);
        g.mediumTick(0.5f);
    }

    @Test
    void stationsAndTunnelsAreDarkRoofedShallowUnderground() {
        Game g = UrbanDiscoveryTest.game(World.GEN_URBAN_UNDERGROUND);
        TransitStation station = g.world.urban.segment(true, 0, 0).station();
        Vec3i platform = station.plan().floors().getFirst().landing();
        observe(g, platform);
        assertFalse(g.player.exposedToSky);
        assertTrue(g.player.shelter.roofed() && g.player.shelter.indoor(), "a station is indoors: " + g.player.shelter);
        assertSame(AudioEnvironment.Zone.SHALLOW, AudioSceneState.environment(g, 0));
        assertTrue(g.world.skyLight(platform.x(), platform.y(), platform.z()) <= 0.12f, "no daylight on the platform");

        Vec3i track = new Vec3i(-2, TransitNetwork.FEET_Y, station.low() - 3);
        observe(g, track);
        assertTrue(g.player.shelter.roofed());
        assertSame(AudioEnvironment.Zone.SHALLOW, AudioSceneState.environment(g, 0));
        assertEquals(0.04f, g.world.skyLight(track.x(), track.y(), track.z()), 1e-6f, "the tunnel is dark");
    }

    @Test
    void aBasementIsShelteredStoneARoofIsOpenAndTheBulkheadKeepsRainOutOfTheStairs() {
        Game g = UrbanDiscoveryTest.game(World.GEN_URBAN_UNDERGROUND);
        RouteCatalogue.Corridor c = g.world.urban.routes(0, 0).corridors().getFirst();
        BuildingPlan plan = g.world.urban.buildingAt(c.west().first().x(), c.west().first().z());
        observe(g, c.west().firstInside());
        assertFalse(g.player.exposedToSky);
        assertSame(AudioEnvironment.Zone.STONE_STRUCTURE, AudioSceneState.environment(g, 0),
                "a basement hall is a stone structure: " + g.player.shelter);

        // The roof's own reservation cell, out in the open beyond the bulkhead.
        Vec3i roof = plan.fixtures().stream().filter(f -> f.kind() == BuildingPlan.FixtureKind.QUEST
                && com.veylon.world.urban.UrbanIds.floorIndex(f.id()) == plan.storeys()).findFirst().orElseThrow().position();
        observe(g, roof);
        assertTrue(g.player.exposedToSky);
        assertSame(AudioEnvironment.Zone.OPEN, AudioSceneState.environment(g, 0));

        // The stair head under the bulkhead roof: dry here, and wet under generator 12's open well.
        Vec3i head = plan.stairs().getLast().route().getLast();
        assertFalse(g.world.isSkyExposed(head.x(), head.y(), head.z()), "rain stops at the bulkhead roof");
        Game twelve = UrbanDiscoveryTest.game(World.GEN_URBAN_DISTRICTS);
        BuildingPlan old = twelve.world.urban.buildingAt(head.x(), head.z());
        Vec3i oldHead = old.stairs().getLast().route().getLast();
        twelve.world.ensureChunks(oldHead.x(), oldHead.z(), 2, 2_000);
        assertTrue(twelve.world.isSkyExposed(oldHead.x(), oldHead.y(), oldHead.z()),
                "generator 12's stairwell is open to the sky");
    }
}
