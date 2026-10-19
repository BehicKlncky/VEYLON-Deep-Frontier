package com.veylon;

import com.veylon.entity.Affliction;
import com.veylon.entity.GameMode;
import com.veylon.simulation.EventSystem;
import com.veylon.world.Biome;
import com.veylon.world.World;
import com.veylon.world.urban.DistrictMap;
import com.veylon.world.urban.DistrictPlan;
import com.veylon.world.urban.DistrictRules;
import com.veylon.world.urban.UrbanDatum;
import com.veylon.world.urban.UrbanDistrict;
import com.veylon.world.urban.UrbanOverlay;
import com.veylon.world.urban.UrbanSurface;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Milestone 13: the player knows which district they stand in, crossing into another one says
 * what it means, its climate follows it, and toxic fog is the quarantine's hazard.
 */
class UrbanDistrictPresenceTest {
    private static final long SEED = 20260731L;

    @Test
    void crossingIntoADistrictNamesItAndItsClimateFollows() {
        Game g = district();
        standOnSidewalk(g, UrbanDistrict.RESIDENTIAL);
        g.player.tickNeeds(g, 0.05f);
        assertEquals(UrbanDistrict.RESIDENTIAL, g.player.district);
        assertEquals("District / Reclaimed Residential", g.player.locationLine());
        int lines = g.eventLog.all().size();
        g.player.tickNeeds(g, 0.05f);
        assertEquals(lines, g.eventLog.all().size(), "staying put announces nothing");

        for (UrbanDistrict next : new UrbanDistrict[]{UrbanDistrict.FLOODED, UrbanDistrict.QUARANTINE,
                UrbanDistrict.INDUSTRIAL}) {
            standOnSidewalk(g, next);
            g.player.tickNeeds(g, 0.05f);
            DistrictRules.Profile rules = DistrictRules.of(next);
            assertEquals(next, g.player.district);
            assertSame(rules.climate(), g.player.biome, "district climate (D-08)");
            assertEquals(rules.displayName(), g.player.terrainLabel());
            String last = g.eventLog.recent(1).getFirst();
            assertTrue(last.contains("Entering " + rules.displayName() + ".") && last.contains(rules.arrival()), last);
        }
    }

    @Test
    void toxicFogDriftsOverTheCityButOnlySickensInsideTheQuarantine() {
        Game g = district();
        g.events.active.add(new EventSystem.ActiveEvent(EventSystem.EventType.TOXIC_FOG, 10_000, 1));
        standOnSidewalk(g, UrbanDistrict.COMMERCIAL);
        g.player.tickNeeds(g, 0.05f);
        assertTrue(g.player.exposedToSky);
        for (int i = 0; i < 512; i++) {
            assertFalse(g.player.tickToxicFogExposure(g), "harmless outside the cordon");
        }
        standOnSidewalk(g, UrbanDistrict.QUARANTINE);
        g.player.tickNeeds(g, 0.05f);
        boolean sick = false;
        for (int i = 0; i < 512 && !sick; i++) {
            sick = g.player.tickToxicFogExposure(g);
        }
        assertTrue(sick && g.player.has(Affliction.SICKNESS), "inside the cordon the fog sickens");
    }

    @Test
    void otherWorldsKeepTheirBiomeLineAndNoDistrict() {
        for (int generator : new int[]{World.GEN_CAVE_IDENTITIES, World.GEN_URBAN_CIVIC}) {
            Game g = new Game();
            g.newWorld(SEED, true, generator, GameMode.SURVIVAL);
            g.player.tickNeeds(g, 0.05f);
            assertNull(g.player.district);
            assertEquals("Biome / " + g.player.biome.displayName, g.player.locationLine());
            assertEquals(g.player.biome.displayName, g.player.terrainLabel());
        }
        // Generator 11 keeps its frozen climates: residential meadow, noise climate elsewhere.
        Game eleven = new Game();
        eleven.newWorld(SEED, true, World.GEN_URBAN_CIVIC, GameMode.SURVIVAL);
        assertSame(Biome.MEADOW, eleven.world.biomeAt(8, 8));
        int x = 1 * 256 + 128, z = -1 * 256 + 128;
        if (eleven.world.urban.districtAt(x, z) != UrbanDistrict.RESIDENTIAL) {
            assertSame(eleven.world.generator.biomeAt(x, z), eleven.world.biomeAt(x, z));
        }
    }

    private static Game district() {
        Game g = new Game();
        g.newWorld(SEED, true, World.GEN_URBAN_DISTRICTS, GameMode.SURVIVAL);
        return g;
    }

    /** Puts the player on a clear sidewalk cell of the first region of a district. */
    private static void standOnSidewalk(Game g, UrbanDistrict district) {
        for (int rz = -3; rz <= 3; rz++) {
            for (int rx = -3; rx <= 3; rx++) {
                if (DistrictMap.districtOf(SEED, rx, rz) != district) {
                    continue;
                }
                DistrictPlan plan = g.world.urban.regionPlan(rx, rz);
                for (int z = plan.originZ() + 40; z < plan.originZ() + 216; z++) {
                    for (int x = plan.originX() + 40; x < plan.originX() + 216; x++) {
                        if (plan.surfaceAt(x, z) == UrbanSurface.SIDEWALK && plan.overlayAt(x, z) == UrbanOverlay.NONE) {
                            g.world.getOrCreateChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
                            g.player.pos.set(x + 0.5f, UrbanDatum.STREET_Y + 1.001f, z + 0.5f);
                            g.player.vel.zero();
                            g.player.onGround = true;
                            return;
                        }
                    }
                }
            }
        }
        throw new AssertionError("no sidewalk in a " + district + " region");
    }
}
