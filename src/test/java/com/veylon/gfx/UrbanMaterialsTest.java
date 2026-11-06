package com.veylon.gfx;

import com.veylon.world.BlockType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Urban package 04: every block has a material, the texture array stays
 * inside the layer budget the chunk shader shares, and the urban tiles are
 * deterministic with transparency only where the shader's cutout expects it.
 */
class UrbanMaterialsTest {

    private static final List<String> OPAQUE_TILES = List.of(
            "asphalt", "paving", "concrete", "brick", "plaster", "sheet_metal", "rubble",
            "door_wood_lower", "door_wood_upper", "door_metal_lower", "door_metal_upper",
            "stair_tread", "stair_riser", "shelf_side", "shelf_top", "cabinet_front", "cabinet_top",
            "locker_front", "locker_top", "table_top", "sign_board", "sign_edge", "contaminated",
            "hidden_panel");
    private static final List<String> CUTOUT_TILES = List.of(
            "window_glass", "window_broken", "door_broken", "debris", "railing", "railing_top");

    @Test
    void theFullRegistrationFitsTheLayerBudgetWithEveryBlockMaterialised() {
        MaterialRegistry.Census census = MaterialRegistry.census();
        assertTrue(census.errors().isEmpty(), census.errors().toString());
        assertTrue(census.layers() <= MaterialRegistry.MAX_LAYERS,
                census.layers() + " layers against " + MaterialRegistry.MAX_LAYERS);
        System.out.println("[materials] census: " + census.materials() + " materials, "
                + census.layers() + " of " + MaterialRegistry.MAX_LAYERS + " layers");
        assertEquals(0, MaterialRegistry.layerCount(), "the census never installs itself");
    }

    @Test
    void urbanTilesAreCompleteAndDeterministic() {
        for (String tile : concat(OPAQUE_TILES, CUTOUT_TILES)) {
            for (int variant = 0; variant < 3; variant++) {
                int[] a = ProceduralTextures.generate(tile, variant, MaterialRegistry.TILE_SIZE);
                assertNotNull(a, tile);
                assertEquals(MaterialRegistry.TILE_SIZE * MaterialRegistry.TILE_SIZE, a.length);
                assertArrayEquals(a, ProceduralTextures.generate(tile, variant, MaterialRegistry.TILE_SIZE),
                        tile + " is deterministic");
            }
        }
    }

    @Test
    void damagedLooksAreVariantsOfOneMaterialNotNewBlocks() {
        for (String tile : List.of("concrete", "plaster")) {
            int[] clean = ProceduralTextures.generate(tile, 0, MaterialRegistry.TILE_SIZE);
            int[] damaged = ProceduralTextures.generate(tile, 2, MaterialRegistry.TILE_SIZE);
            assertTrue(!java.util.Arrays.equals(clean, damaged), tile + " has a distinct damaged variant");
        }
    }

    /**
     * A loose panel has to pass for the plaster wall it sits in from across a room, and still
     * be a different surface up close: its seam and scuffs change pixels, not the overall coat.
     */
    @Test
    void aLoosePanelWearsThePlasterCoatButIsNotTheSameSurface() {
        int[] wall = ProceduralTextures.generate("plaster", 0, MaterialRegistry.TILE_SIZE);
        for (int variant = 0; variant < 2; variant++) {
            int[] panel = ProceduralTextures.generate("hidden_panel", variant, MaterialRegistry.TILE_SIZE);
            assertFalse(java.util.Arrays.equals(wall, panel), "the panel shows its seam");
            float[] a = meanRgb(wall), b = meanRgb(panel);
            for (int c = 0; c < 3; c++) {
                assertEquals(a[c], b[c], 14f, "channel " + c + " of panel layout " + variant);
            }
        }
    }

    private static float[] meanRgb(int[] px) {
        float[] sum = new float[3];
        for (int p : px) {
            sum[0] += (p >> 16) & 255;
            sum[1] += (p >> 8) & 255;
            sum[2] += p & 255;
        }
        for (int c = 0; c < 3; c++) { sum[c] /= px.length; }
        return sum;
    }

    @Test
    void onlyGlassFramesAndLitterHaveSeeThroughTexels() {
        for (String tile : OPAQUE_TILES) {
            int[] px = ProceduralTextures.generate(tile, 0, MaterialRegistry.TILE_SIZE);
            for (int p : px) {
                assertTrue((p >>> 24) >= 0x80, tile + " must be opaque everywhere");
            }
        }
        for (String tile : CUTOUT_TILES) {
            int[] px = ProceduralTextures.generate(tile, 0, MaterialRegistry.TILE_SIZE);
            int clear = 0, solid = 0;
            for (int p : px) {
                if ((p >>> 24) < 0x80) {
                    clear++;
                } else {
                    solid++;
                }
            }
            assertTrue(clear > px.length / 3, tile + " is mostly see-through");
            assertTrue(solid > 0, tile + " still shows something");
        }
        // The window frame is opaque all round, so a pane reads at a distance and at night.
        int[] glass = ProceduralTextures.generate("window_glass", 0, MaterialRegistry.TILE_SIZE);
        int n = MaterialRegistry.TILE_SIZE;
        for (int i = 0; i < n; i++) {
            assertTrue((glass[i] >>> 24) == 0xFF && (glass[(n - 1) * n + i] >>> 24) == 0xFF);
            assertTrue((glass[i * n] >>> 24) == 0xFF && (glass[i * n + n - 1] >>> 24) == 0xFF);
        }
    }

    @Test
    void glassIsSolidButLetsLightAndNeighbourFacesThrough() {
        assertTrue(BlockType.WINDOW.solid);
        assertTrue(!BlockType.WINDOW.opaque, "neighbour faces and sky light pass a window");
        assertTrue(!BlockType.TABLE.opaque, "a table's legs leave the floor around it visible");
    }

    private static List<String> concat(List<String> a, List<String> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
    }
}
