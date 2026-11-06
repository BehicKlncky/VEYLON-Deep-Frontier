package com.veylon.world.urban;

import com.veylon.gfx.MaterialRegistry;
import com.veylon.gfx.ProceduralTextures;
import com.veylon.world.BlockType;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The city's dressing (urban milestone 22): district grades and wall decals are pure, sparse,
 * placed where their district and surface allow, continuous where they climb, and never tell a
 * loose panel from the plaster it hides in.
 */
class UrbanDressingTest {
    private static final int FOOT = UrbanDatum.STREET_Y + 1;

    @Test
    void decalsAreSparseAndKeepToTheirDistricts() {
        for (UrbanDistrict district : UrbanDistrict.values()) {
            Map<UrbanDressing.Decal, Integer> outside = count(district, BlockType.BRICK, true);
            Map<UrbanDressing.Decal, Integer> inside = count(district, BlockType.PLASTER, false);
            int faces = 64 * 64 * 4 * 4;
            int total = outside.values().stream().mapToInt(Integer::intValue).sum();
            assertTrue(total < faces * 0.12, district + " dresses too many outer walls: " + total + " of " + faces);
            boolean quarantine = district == UrbanDistrict.QUARANTINE;
            assertEquals(quarantine, outside.containsKey(UrbanDressing.Decal.CORDON_MARK), district + " cordon marks");
            assertEquals(district == UrbanDistrict.RESIDENTIAL || district == UrbanDistrict.FLOODED
                    || district == UrbanDistrict.CIVIC, outside.containsKey(UrbanDressing.Decal.IVY), district + " ivy");
            assertEquals(quarantine, outside.containsKey(UrbanDressing.Decal.VEIL_CREEP),
                    district + ": the veil climbs walls only inside the cordon");
            assertFalse(outside.containsKey(UrbanDressing.Decal.PHOTO) || outside.containsKey(UrbanDressing.Decal.MOULD),
                    "personal traces and mould are indoors");
            boolean housing = district == UrbanDistrict.RESIDENTIAL || district == UrbanDistrict.FLOODED;
            assertEquals(housing, inside.containsKey(UrbanDressing.Decal.PHOTO), district + " photographs in flats");
            assertFalse(inside.containsKey(UrbanDressing.Decal.IVY) || inside.containsKey(UrbanDressing.Decal.POSTER),
                    "ivy and posters are outdoors");
        }
    }

    @Test
    void ivyClimbsFromThePavementInUnbrokenColumns() {
        int columns = 0;
        for (int x = -200; x < 200; x++) {
            for (int z = -8; z < 8; z++) {
                for (int face = 2; face < 6; face++) {
                    UrbanDressing.Decal foot = decal(UrbanDistrict.RESIDENTIAL, BlockType.BRICK, face, x, FOOT, z, true, true);
                    boolean grown = false;
                    for (int y = FOOT + 1; y <= FOOT + 3; y++) {
                        UrbanDressing.Decal above = decal(UrbanDistrict.RESIDENTIAL, BlockType.BRICK, face, x, y, z, true, true);
                        if (above != null && above != UrbanDressing.Decal.POSTER && above != UrbanDressing.Decal.NOTICE) {
                            assertEquals(UrbanDressing.Decal.IVY_TOP, above);
                            UrbanDressing.Decal below = decal(UrbanDistrict.RESIDENTIAL, BlockType.BRICK, face, x, y - 1, z, true, true);
                            assertTrue(below == UrbanDressing.Decal.IVY || below == UrbanDressing.Decal.IVY_TOP,
                                    "ivy never floats above a gap at " + x + "," + y + "," + z);
                            grown = true;
                        }
                    }
                    if (foot == UrbanDressing.Decal.IVY) { columns++; }
                    assertFalse(grown && foot != UrbanDressing.Decal.IVY, "a column of ivy has its root");
                    for (int y = FOOT; y <= FOOT + 3; y++) {
                        UrbanDressing.Decal unrooted = decal(UrbanDistrict.RESIDENTIAL, BlockType.BRICK, face, x, y, z, true, false);
                        assertTrue(unrooted != UrbanDressing.Decal.IVY && unrooted != UrbanDressing.Decal.IVY_TOP,
                                "no ivy without pavement or soil to climb from");
                    }
                }
            }
        }
        assertTrue(columns > 100, "the housing is overgrown: " + columns);
    }

    @Test
    void aLoosePanelIsDressedExactlyAsThePlasterItHidesIn() {
        float[] plaster = new float[3], panel = new float[3];
        for (UrbanDistrict district : UrbanDistrict.values()) {
            for (int face = 0; face < 6; face++) {
                for (int y = 30; y < 60; y++) {
                    for (boolean exterior : new boolean[]{true, false}) {
                        UrbanDressing.grade(district, BlockType.PLASTER, face, y, exterior, plaster);
                        UrbanDressing.grade(district, BlockType.HIDDEN_PANEL, face, y, exterior, panel);
                        assertArrayEquals(plaster, panel, district + " grade at y " + y);
                        for (int x = -20; x < 20; x++) {
                            assertEquals(decal(district, BlockType.PLASTER, face, x, y, 3, exterior, true),
                                    decal(district, BlockType.HIDDEN_PANEL, face, x, y, 3, exterior, true));
                        }
                    }
                }
            }
        }
    }

    @Test
    void gradesAreSubtleAndOnlyTouchCitySurfaces() {
        float[] out = new float[3];
        for (UrbanDistrict district : UrbanDistrict.values()) {
            for (BlockType block : BlockType.values()) {
                for (int y = 30; y < 60; y++) {
                    UrbanDressing.grade(district, block, 2, y, true, out);
                    for (float c : out) { assertTrue(c >= 0.69f && c < 1.1f, district + " " + block + " " + c); }
                    if (!UrbanDressing.dressed(block)) { assertArrayEquals(new float[]{1, 1, 1}, out); }
                }
            }
        }
        UrbanDressing.grade(UrbanDistrict.FLOODED, BlockType.PLASTER, 3, FOOT, false, out);
        float[] dry = new float[3];
        UrbanDressing.grade(UrbanDistrict.FLOODED, BlockType.PLASTER, 3, FOOT + 4, false, dry);
        assertTrue(out[0] < dry[0] - 0.1f, "the flood's tide line marks the ground floor's walls inside too");
    }

    @Test
    void decalsNeverDressGlassDoorsFloorsOrCeilings() {
        for (UrbanDistrict district : UrbanDistrict.values()) {
            for (BlockType block : new BlockType[]{BlockType.WINDOW, BlockType.WOOD_DOOR, BlockType.METAL_DOOR,
                    BlockType.SHELF, BlockType.SIGN, BlockType.ASPHALT, BlockType.GRASS}) {
                for (int y = FOOT; y < FOOT + 3; y++) {
                    for (int x = 0; x < 64; x++) {
                        assertNull(decal(district, block, 2, x, y, 0, true, true), block.name());
                    }
                }
            }
            for (int x = 0; x < 256; x++) {
                assertNull(decal(district, BlockType.BRICK, 0, x, FOOT, 0, true, true), "never on a top face");
                assertNull(decal(district, BlockType.BRICK, 1, x, FOOT, 0, true, true), "never on a bottom face");
            }
        }
    }

    @Test
    void everyDecalTileIsPaintedDeterministicallyWithinTheLayerBudget() {
        for (UrbanDressing.Decal decal : UrbanDressing.Decal.values()) {
            int[] a = ProceduralTextures.generate(decal.tile(), 0, MaterialRegistry.TILE_SIZE);
            assertNotNull(a, decal.tile());
            assertArrayEquals(a, ProceduralTextures.generate(decal.tile(), 0, MaterialRegistry.TILE_SIZE));
            int opaque = 0;
            for (int px : a) { if ((px >>> 24) > 127) { opaque++; } }
            assertTrue(opaque > a.length / 50 && opaque < a.length * 0.8, decal + " is a cutout over its wall: " + opaque);
        }
        // Paper and frames are pinned up inside their tile: a clear border all round stays
        // see-through, so nothing but the sheet itself ever shows on the wall.
        int n = MaterialRegistry.TILE_SIZE, border = 4;
        for (String tile : new String[]{"decal_poster", "decal_notice", "decal_photo", "decal_drawing"}) {
            int[] a = ProceduralTextures.generate(tile, 0, n);
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    if (x < border || y < border || x >= n - border || y >= n - border) {
                        assertEquals(0, a[y * n + x] >>> 24, tile + " pixel " + x + "," + y + " is see-through");
                    }
                }
            }
        }
        MaterialRegistry.Census census = MaterialRegistry.census();
        assertTrue(census.errors().isEmpty(), census.errors().toString());
        assertTrue(census.layers() <= MaterialRegistry.MAX_LAYERS, census.layers() + " layers");
    }

    private static Map<UrbanDressing.Decal, Integer> count(UrbanDistrict district, BlockType block, boolean exterior) {
        Map<UrbanDressing.Decal, Integer> counts = new EnumMap<>(UrbanDressing.Decal.class);
        for (int x = 0; x < 64; x++) {
            for (int z = 0; z < 64; z++) {
                for (int face = 2; face < 6; face++) {
                    for (int y = FOOT; y < FOOT + 4; y++) {
                        UrbanDressing.Decal d = decal(district, block, face, x, y, z, exterior, true);
                        if (d != null) { counts.merge(d, 1, Integer::sum); }
                    }
                }
            }
        }
        return counts;
    }

    private static UrbanDressing.Decal decal(UrbanDistrict district, BlockType block, int face, int x, int y, int z,
                                             boolean exterior, boolean rooted) {
        UrbanDressing.Decal first = UrbanDressing.decal(district, block, face, x, y, z, exterior, rooted);
        assertEquals(first, UrbanDressing.decal(district, block, face, x, y, z, exterior, rooted), "pure");
        return first;
    }
}
