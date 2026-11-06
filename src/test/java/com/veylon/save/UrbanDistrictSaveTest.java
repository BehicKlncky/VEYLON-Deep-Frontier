package com.veylon.save;

import com.veylon.Game;
import com.veylon.UrbanEncounterArena;
import com.veylon.entity.GameMode;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.DistrictMap;
import com.veylon.world.urban.DistrictPlan;
import com.veylon.world.urban.EncounterAnchors;
import com.veylon.world.urban.UrbanDatum;
import com.veylon.world.urban.UrbanDistrict;
import com.veylon.world.urban.UrbanOverlay;
import com.veylon.world.urban.UrbanRevision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Generator 12 (milestone 13): encounter catalogue three is frozen with it, and what a survivor
 * does to the districts' overlays (scraping contamination, breaching a cordon, draining a
 * flooded street) survives a save and a load as ordinary recorded edits.
 */
class UrbanDistrictSaveTest {
    private static final int Y = UrbanDatum.STREET_Y;
    @TempDir Path directory;

    @Test
    void generatorTwelveReadsOnlyEncounterCatalogueThree() throws Exception {
        Game g = new Game();
        g.newWorld(7, true, World.GEN_URBAN_DISTRICTS, GameMode.SURVIVAL);
        UrbanEncounterArena.activate(g, true);
        assertEquals(3, EncounterAnchors.catalogue(g.world));
        byte[] payload = UrbanPopulationSection.write(g);
        assertEquals(1, ByteBuffer.wrap(payload).getInt(0), "codec version is unchanged");
        assertEquals(3, ByteBuffer.wrap(payload).getInt(4));
        Path valid = directory.resolve("districts.sav");
        assertTrue(SaveSystem.save(g, valid));
        assertTrue(SaveSystem.load(g, valid));
        assertEquals(World.GEN_URBAN_DISTRICTS, g.world.generatorVersion);
        assertEquals(UrbanRevision.LAYOUT_DISTRICTS, g.world.urban.layoutRevision());
        assertEquals(1, g.entities.encounters.size());
        for (int catalogue : new int[]{1, 2, 4}) {
            byte[] other = payload.clone();
            ByteBuffer.wrap(other).putInt(4, catalogue);
            Path broken = directory.resolve("catalogue-" + catalogue + ".sav");
            Files.write(broken, CreativeSaveSections.replace(Files.readAllBytes(valid), UrbanPopulationSection.ID, other));
            var world = g.world;
            var player = g.player;
            assertFalse(SaveSystem.load(g, broken), "catalogue " + catalogue + " is not generator 12's");
            assertSame(world, g.world, "the live world survives a rejected load");
            assertSame(player, g.player);
        }
        Game eleven = new Game();
        eleven.newWorld(7, true, World.GEN_URBAN_CIVIC, GameMode.SURVIVAL);
        assertEquals(2, ByteBuffer.wrap(UrbanPopulationSection.write(eleven)).getInt(4), "generator 11 is unchanged");
    }

    @Test
    void scrapedContaminationABreachedCordonAndADrainedStreetStayThatWay() throws Exception {
        long seed = 20260731L;
        Game g = new Game();
        g.newWorld(seed, true, World.GEN_URBAN_DISTRICTS, GameMode.SURVIVAL);
        Vec3i tainted = first(g, seed, UrbanDistrict.QUARANTINE, UrbanOverlay.CONTAMINATED, Y);
        Vec3i fence = first(g, seed, UrbanDistrict.QUARANTINE, UrbanOverlay.FENCE, Y + 1);
        Vec3i water = first(g, seed, UrbanDistrict.FLOODED, UrbanOverlay.STANDING_WATER, Y + 1);
        assertSame(BlockType.CONTAMINATED_GROUND, g.world.getBlock(tainted.x(), tainted.y(), tainted.z()));
        assertSame(BlockType.SHEET_METAL, g.world.getBlock(fence.x(), fence.y(), fence.z()));
        assertSame(BlockType.WATER, g.world.getBlock(water.x(), water.y(), water.z()));
        g.world.setBlock(tainted.x(), tainted.y(), tainted.z(), BlockType.AIR, true);
        g.world.setBlock(fence.x(), fence.y(), fence.z(), BlockType.AIR, true);
        g.world.setBlock(fence.x(), fence.y() + 1, fence.z(), BlockType.AIR, true);
        g.world.setBlock(water.x(), water.y(), water.z(), BlockType.AIR, true);

        Path path = directory.resolve("edited.sav");
        assertTrue(SaveSystem.save(g, path));
        assertTrue(SaveSystem.load(g, path));
        for (Vec3i cell : new Vec3i[]{tainted, fence, water}) {
            g.world.getOrCreateChunk(Math.floorDiv(cell.x(), 16), Math.floorDiv(cell.z(), 16));
            assertSame(BlockType.AIR, g.world.getBlock(cell.x(), cell.y(), cell.z()), "still cleared at " + cell);
        }
        assertSame(BlockType.AIR, g.world.getBlock(fence.x(), fence.y() + 1, fence.z()));
        // The rest of the overlay regenerates exactly as planned.
        Vec3i other = first(g, seed, UrbanDistrict.QUARANTINE, UrbanOverlay.CONTAMINATED, Y, tainted);
        assertSame(BlockType.CONTAMINATED_GROUND, g.world.getBlock(other.x(), other.y(), other.z()));
    }

    private static Vec3i first(Game g, long seed, UrbanDistrict district, UrbanOverlay overlay, int y, Vec3i... skip) {
        for (int rz = -3; rz <= 3; rz++) {
            for (int rx = -3; rx <= 3; rx++) {
                if (DistrictMap.districtOf(seed, rx, rz) != district) {
                    continue;
                }
                DistrictPlan plan = g.world.urban.regionPlan(rx, rz);
                for (int z = plan.originZ(); z < plan.originZ() + 256; z++) {
                    for (int x = plan.originX(); x < plan.originX() + 256; x++) {
                        Vec3i cell = new Vec3i(x, y, z);
                        if (plan.overlayAt(x, z) != overlay || java.util.Arrays.asList(skip).contains(cell)) {
                            continue;
                        }
                        g.world.getOrCreateChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
                        return cell;
                    }
                }
            }
        }
        throw new AssertionError("no " + overlay + " in a " + district + " region");
    }
}
