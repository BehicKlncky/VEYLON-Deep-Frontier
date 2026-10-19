package com.veylon.gfx;

import com.veylon.world.BlockType;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps every visible BlockType to a {@link BlockMaterial} by stable string id
 * and owns the block texture array. Never keyed by enum ordinal, so visual
 * data can evolve freely without touching save-relevant ordering.
 *
 * <p>Registration builds a {@link Catalog} on the CPU; {@link #init} then
 * uploads it to the GL texture array. {@link #census()} builds a throwaway
 * catalog without GL and without touching the live one, so headless tests can
 * hold the layer budget ({@link #MAX_LAYERS}, shared with the chunk shader's
 * {@code uLayerProps} array) and the "every block has a material" rule.
 */
public final class MaterialRegistry {

    public enum TintMode {
        NONE, GRASS_TOP, FOLIAGE
    }

    public static final class BlockMaterial {
        public final String id;
        public final TintMode tint;
        public final boolean cutout;
        public final float emissive;
        public final float roughness;
        public final int variants;
        /** Resolved texture-array layers, one per variant. */
        public final int[] topLayer;
        public final int[] sideLayer;
        public final int[] bottomLayer;

        BlockMaterial(String id, TintMode tint, boolean cutout, float emissive, float roughness,
                      int variants, int[] top, int[] side, int[] bottom) {
            this.id = id;
            this.tint = tint;
            this.cutout = cutout;
            this.emissive = emissive;
            this.roughness = roughness;
            this.variants = variants;
            this.topLayer = top;
            this.sideLayer = side;
            this.bottomLayer = bottom;
        }

        public int variantOf(int x, int y, int z) {
            if (variants <= 1) {
                return 0;
            }
            int h = x * 374761393 + y * 668265263 + z * 1274126177;
            h = (h ^ (h >>> 13)) * 1103515245;
            return Math.floorMod(h >> 8, variants);
        }
    }

    /** What a full registration produces, without GL: for budget and coverage checks. */
    public record Census(int materials, int layers, List<String> errors) {
    }

    public static final int TILE_SIZE = 64;
    public static final int MAX_LAYERS = 160;

    /** The registration currently in use; empty until {@link #init}. */
    private static Catalog current = new Catalog();
    private static TextureArray array;

    private MaterialRegistry() {
    }

    /** Builds all materials + the GL texture array. Call once with a live GL context. */
    public static void init(boolean nearestFiltering) {
        Catalog catalog = Catalog.build();
        current = catalog;
        array = new TextureArray(catalog.tilePixels, TILE_SIZE, true, nearestFiltering);
        System.out.println("[materials] " + catalog.byId.size() + " materials, "
                + catalog.tilePixels.size() + " of " + MAX_LAYERS + " texture layers"
                + (catalog.errors.isEmpty() ? "" : ", " + catalog.errors.size()
                + " PROBLEMS (see above)"));
    }

    /** Runs the full registration on the CPU and reports it; the live registry is untouched. */
    public static Census census() {
        Catalog catalog = Catalog.build();
        return new Census(catalog.byId.size(), catalog.tilePixels.size(), List.copyOf(catalog.errors));
    }

    /** One complete registration: materials, tiles and their per-layer shader properties. */
    private static final class Catalog {
        final Map<BlockType, BlockMaterial> byBlock = new HashMap<>();
        final Map<String, BlockMaterial> byId = new HashMap<>();
        /** tileKey ("id#variant") -> layer index. */
        final Map<String, Integer> layerOf = new LinkedHashMap<>();
        final List<int[]> tilePixels = new ArrayList<>();
        /** Per-layer (emissive, roughness) for the shader. */
        final float[] layerProps = new float[MAX_LAYERS * 2];
        final List<String> errors = new ArrayList<>();
        BlockMaterial fallback;

        static Catalog build() {
            Catalog c = new Catalog();
            for (int i = 0; i < c.layerProps.length; i += 2) {
                c.layerProps[i] = 0f;
                c.layerProps[i + 1] = 1f;
            }
            // Layer 0 is the missing-texture checker so anything unresolved is obvious.
            c.tilePixels.add(checkerTile());
            c.layerOf.put("missing#0", 0);
            c.fallback = new BlockMaterial("missing", TintMode.NONE, false, 0f, 1f, 1,
                    new int[]{0}, new int[]{0}, new int[]{0});
            c.registerAll();
            c.validate();
            return c;
        }

        private void registerAll() {
            // id, block, top/side/bottom tiles, variants, tint, cutout, emissive, roughness
            register("grass", BlockType.GRASS, "grass_top", "grass_side", "dirt", 3, TintMode.GRASS_TOP, false, 0f, 0.95f);
            register("dirt", BlockType.DIRT, "dirt", "dirt", "dirt", 2, TintMode.NONE, false, 0f, 0.95f);
            register("stone", BlockType.STONE, "stone", "stone", "stone", 3, TintMode.NONE, false, 0f, 0.9f);
            register("sand", BlockType.SAND, "sand", "sand", "sand", 2, TintMode.NONE, false, 0f, 0.9f);
            register("gravel", BlockType.GRAVEL, "gravel", "gravel", "gravel", 1, TintMode.NONE, false, 0f, 0.92f);
            register("clay", BlockType.CLAY, "clay", "clay", "clay", 1, TintMode.NONE, false, 0f, 0.75f);
            register("snow", BlockType.SNOW, "snow", "snow", "snow", 1, TintMode.NONE, false, 0f, 0.7f);
            register("ice", BlockType.ICE, "ice", "ice", "ice", 1, TintMode.NONE, false, 0f, 0.15f);
            register("log", BlockType.LOG, "log_end", "log_side", "log_end", 1, TintMode.NONE, false, 0f, 0.95f);
            register("leaves", BlockType.LEAVES, "leaves", "leaves", "leaves", 2, TintMode.FOLIAGE, false, 0f, 0.9f);
            register("bush", BlockType.BUSH, "bush", "bush", "bush", 1, TintMode.FOLIAGE, true, 0f, 0.9f);
            register("berry_bush", BlockType.BERRY_BUSH, "berry_bush", "berry_bush", "berry_bush", 1, TintMode.NONE, true, 0f, 0.9f);
            register("berry_bush_empty", BlockType.BERRY_BUSH_EMPTY, "berry_bush_empty", "berry_bush_empty", "berry_bush_empty", 1, TintMode.FOLIAGE, true, 0f, 0.9f);
            register("coal_ore", BlockType.COAL_ORE, "coal_ore", "coal_ore", "coal_ore", 1, TintMode.NONE, false, 0f, 0.85f);
            register("copper_ore", BlockType.COPPER_ORE, "copper_ore", "copper_ore", "copper_ore", 1, TintMode.NONE, false, 0f, 0.8f);
            register("iron_ore", BlockType.IRON_ORE, "iron_ore", "iron_ore", "iron_ore", 1, TintMode.NONE, false, 0f, 0.8f);
            register("plank", BlockType.PLANK, "plank", "plank", "plank", 1, TintMode.NONE, false, 0f, 0.9f);
            register("wall", BlockType.WALL, "wall", "wall", "wall", 1, TintMode.NONE, false, 0f, 0.9f);
            register("campfire", BlockType.CAMPFIRE, "campfire_wood", "campfire_wood", "campfire_wood", 1, TintMode.NONE, false, 0f, 0.95f);
            register("torch", BlockType.TORCH, "torch_head", "log_side", "log_side", 1, TintMode.NONE, false, 0f, 0.95f);
            register("workbench", BlockType.WORKBENCH, "workbench_top", "workbench_side", "plank", 1, TintMode.NONE, false, 0f, 0.9f);
            register("crate", BlockType.CRATE, "crate_top", "crate_side", "crate_top", 1, TintMode.NONE, false, 0f, 0.9f);
            register("ash", BlockType.ASH, "ash", "ash", "ash", 1, TintMode.NONE, false, 0.06f, 0.95f);
            register("sapling", BlockType.SAPLING, "sapling", "sapling", "sapling", 1, TintMode.FOLIAGE, true, 0f, 0.9f);
            register("tall_grass", BlockType.TALL_GRASS, "tall_grass", "tall_grass", "tall_grass", 2, TintMode.FOLIAGE, true, 0f, 0.9f);
            register("herb_plant", BlockType.HERB_PLANT, "herb", "herb", "herb", 1, TintMode.NONE, true, 0.05f, 0.9f);
            register("scrap", BlockType.SCRAP_BLOCK, "scrap", "scrap", "scrap", 1, TintMode.NONE, false, 0f, 0.35f);
            register("pod_hull", BlockType.POD_HULL, "pod_hull", "pod_hull", "pod_hull", 1, TintMode.NONE, false, 0f, 0.3f);
            register("ruin_stone", BlockType.RUIN_STONE, "ruin_stone", "ruin_stone", "ruin_stone", 1, TintMode.NONE, false, 0f, 0.85f);
            register("ruin_core", BlockType.RUIN_CORE, "ruin_core", "ruin_core", "ruin_core", 1, TintMode.NONE, false, 0.85f, 0.4f);
            register("bone_pile", BlockType.BONE_PILE, "bone", "bone", "bone", 1, TintMode.NONE, false, 0f, 0.8f);
            register("drying_rack", BlockType.DRYING_RACK, "log_side", "log_side", "log_side", 1, TintMode.NONE, false, 0f, 0.95f);
            register("furnace", BlockType.FURNACE, "furnace_side", "furnace_front", "furnace_side", 1, TintMode.NONE, false, 0.2f, 0.9f);
            register("anvil", BlockType.ANVIL, "anvil_metal", "anvil_metal", "anvil_metal", 1, TintMode.NONE, false, 0f, 0.25f);
            register("tannery", BlockType.TANNERY, "fabric", "log_side", "log_side", 1, TintMode.NONE, false, 0f, 0.9f);
            register("herb_station", BlockType.HERB_STATION, "herb_station", "herb_station", "plank", 1, TintMode.NONE, false, 0f, 0.9f);
            register("map_table", BlockType.MAP_TABLE, "map_table", "plank", "plank", 1, TintMode.NONE, false, 0f, 0.85f);
            register("rain_collector", BlockType.RAIN_COLLECTOR, "scrap", "scrap", "scrap", 1, TintMode.NONE, false, 0f, 0.35f);
            register("bedroll", BlockType.BEDROLL, "fabric", "fabric", "fabric", 1, TintMode.NONE, false, 0f, 0.95f);
            register("camp_bed", BlockType.CAMP_BED, "fabric_red", "fabric_red", "fabric_red", 1, TintMode.NONE, false, 0f, 0.95f);
            register("beacon", BlockType.BEACON, "beacon_side", "beacon_side", "beacon_side", 1, TintMode.NONE, false, 0.15f, 0.3f);
            register("beacon_lit", BlockType.BEACON_LIT, "beacon_lit", "beacon_lit", "beacon_lit", 1, TintMode.NONE, false, 0.9f, 0.3f);

            // ---- Deep Frontier expansion blocks ----
            register("basalt", BlockType.BASALT, "basalt", "basalt", "basalt", 2, TintMode.NONE, false, 0f, 0.9f);
            register("sulfur_ore", BlockType.SULFUR_ORE, "sulfur_ore", "sulfur_ore", "sulfur_ore", 1, TintMode.NONE, false, 0.06f, 0.85f);
            register("saltpeter_ore", BlockType.SALTPETER_ORE, "saltpeter_ore", "saltpeter_ore", "saltpeter_ore", 1, TintMode.NONE, false, 0f, 0.85f);
            register("glow_fungus", BlockType.GLOW_FUNGUS, "glow_fungus", "glow_fungus", "glow_fungus", 1, TintMode.NONE, true, 0.75f, 0.9f);
            register("ladder", BlockType.LADDER, "ladder", "ladder", "ladder", 1, TintMode.NONE, true, 0f, 0.95f);
            register("lantern", BlockType.LANTERN, "lantern_glass", "lantern_glass", "lantern_glass", 1, TintMode.NONE, false, 0.85f, 0.4f);
            register("trail_marker", BlockType.TRAIL_MARKER, "marker_paint", "marker_paint", "marker_paint", 1, TintMode.NONE, false, 0.7f, 0.9f);
            register("powder_keg", BlockType.POWDER_KEG, "keg_top", "keg_side", "keg_top", 1, TintMode.NONE, false, 0f, 0.9f);
            register("gate", BlockType.GATE, "gate", "gate", "gate", 1, TintMode.NONE, false, 0f, 0.9f);
            register("gate_open", BlockType.GATE_OPEN, "gate", "gate", "gate", 1, TintMode.NONE, false, 0f, 0.9f);
            register("stone_brick", BlockType.STONE_BRICK, "stone_brick", "stone_brick", "stone_brick", 2, TintMode.NONE, false, 0f, 0.9f);
            register("alarm_bell", BlockType.ALARM_BELL, "bell_bronze", "bell_bronze", "bell_bronze", 1, TintMode.NONE, false, 0.1f, 0.35f);
            register("cage_bars", BlockType.CAGE_BARS, "cage_bars", "cage_bars", "cage_bars", 1, TintMode.NONE, false, 0f, 0.4f);

            // ---- Urban materials and fixtures (package 04): see UrbanTextures ----
            // Weathered and damaged looks are position-hash variants of one id.
            register("asphalt", BlockType.ASPHALT, "asphalt", "asphalt", "asphalt", 2, TintMode.NONE, false, 0f, 0.85f);
            register("paving", BlockType.PAVING, "paving", "paving", "paving", 2, TintMode.NONE, false, 0f, 0.8f);
            register("concrete", BlockType.CONCRETE, "concrete", "concrete", "concrete", 3, TintMode.NONE, false, 0f, 0.85f);
            register("brick", BlockType.BRICK, "brick", "brick", "brick", 2, TintMode.NONE, false, 0f, 0.88f);
            register("plaster", BlockType.PLASTER, "plaster", "plaster", "plaster", 3, TintMode.NONE, false, 0f, 0.9f);
            register("sheet_metal", BlockType.SHEET_METAL, "sheet_metal", "sheet_metal", "sheet_metal", 1, TintMode.NONE, false, 0f, 0.35f);
            register("rubble", BlockType.RUBBLE, "rubble", "rubble", "rubble", 1, TintMode.NONE, false, 0f, 0.95f);
            register("wood_door", BlockType.WOOD_DOOR, "plank", "door_wood_lower", "plank", 1, TintMode.NONE, false, 0f, 0.85f);
            register("wood_door_open", BlockType.WOOD_DOOR_OPEN, "plank", "door_wood_lower", "plank", 1, TintMode.NONE, false, 0f, 0.85f);
            register("metal_door", BlockType.METAL_DOOR, "sheet_metal", "door_metal_lower", "sheet_metal", 1, TintMode.NONE, false, 0f, 0.4f);
            register("metal_door_open", BlockType.METAL_DOOR_OPEN, "sheet_metal", "door_metal_lower", "sheet_metal", 1, TintMode.NONE, false, 0f, 0.4f);
            register("broken_door", BlockType.BROKEN_DOOR, "door_broken", "door_broken", "door_broken", 1, TintMode.NONE, true, 0f, 0.9f);
            register("window", BlockType.WINDOW, "window_glass", "window_glass", "window_glass", 1, TintMode.NONE, true, 0f, 0.12f);
            register("window_open", BlockType.WINDOW_OPEN, "window_glass", "window_glass", "window_glass", 1, TintMode.NONE, true, 0f, 0.12f);
            register("broken_window", BlockType.BROKEN_WINDOW, "window_broken", "window_broken", "window_broken", 1, TintMode.NONE, true, 0f, 0.3f);
            register("stairs", BlockType.STAIRS, "stair_tread", "stair_riser", "concrete", 1, TintMode.NONE, false, 0f, 0.85f);
            register("shelf", BlockType.SHELF, "shelf_top", "shelf_side", "shelf_top", 1, TintMode.NONE, false, 0f, 0.9f);
            register("cabinet", BlockType.CABINET, "cabinet_top", "cabinet_front", "cabinet_top", 1, TintMode.NONE, false, 0f, 0.8f);
            register("locker", BlockType.LOCKER, "locker_top", "locker_front", "locker_top", 1, TintMode.NONE, false, 0f, 0.35f);
            register("table", BlockType.TABLE, "table_top", "plank", "table_top", 1, TintMode.NONE, false, 0f, 0.85f);
            register("debris", BlockType.DEBRIS, "debris", "debris", "debris", 1, TintMode.NONE, true, 0f, 0.95f);
            // Direction changes shape only; all four share the existing stair tiles.
            register("stairs_north", BlockType.STAIRS_NORTH, "stair_tread", "stair_riser", "concrete", 1, TintMode.NONE, false, 0f, 0.85f);
            register("stairs_south", BlockType.STAIRS_SOUTH, "stair_tread", "stair_riser", "concrete", 1, TintMode.NONE, false, 0f, 0.85f);
            register("stairs_west", BlockType.STAIRS_WEST, "stair_tread", "stair_riser", "concrete", 1, TintMode.NONE, false, 0f, 0.85f);
            register("stairs_east", BlockType.STAIRS_EAST, "stair_tread", "stair_riser", "concrete", 1, TintMode.NONE, false, 0f, 0.85f);
            // Milestone 12 frontage sign: two weathered lettering layouts by position.
            register("sign", BlockType.SIGN, "sign_edge", "sign_board", "sign_edge", 2, TintMode.NONE, false, 0f, 0.8f);
            // Milestone 13 district hazard: an oily crust with sickly blooms, glossy where it seeps.
            register("contaminated", BlockType.CONTAMINATED_GROUND, "contaminated", "contaminated", "contaminated",
                    2, TintMode.NONE, false, 0f, 0.55f);
            // Milestone 14 routes: a cutout guard rail, and a loose panel that wears the plaster coat
            // with a seam, scuffs and, on one layout, a chalk tally.
            register("railing", BlockType.RAILING, "railing_top", "railing", "railing_top", 1, TintMode.NONE, true, 0f, 0.4f);
            register("hidden_panel", BlockType.HIDDEN_PANEL, "plaster", "hidden_panel", "plaster", 2, TintMode.NONE, false, 0f, 0.9f);
            register("hidden_panel_open", BlockType.HIDDEN_PANEL_OPEN, "plaster", "hidden_panel", "plaster", 1, TintMode.NONE, false, 0f, 0.9f);

            // Standalone prop tiles used by special shapes (torch flame, campfire, basin water...).
            propTile("torch_head", 1.0f, 0.9f);
            propTile("campfire_wood", 0f, 0.95f);
            propTile("fabric", 0f, 0.95f);
            propTile("fabric_red", 0f, 0.95f);
            propTile("water_still", 0f, 0.1f);
            propTile("bone", 0f, 0.8f);
            propTile("log_side", 0f, 0.95f);
            propTile("plank", 0f, 0.9f);
            propTile("lantern_glass", 0.85f, 0.4f);
            propTile("lantern_glass_unlit", 0f, 0.55f);
            propTile("marker_paint", 0.7f, 0.9f);
            propTile("bell_bronze", 0.1f, 0.35f);
            propTile("anvil_metal", 0f, 0.25f);
            // Upper door panels, chosen by the mesher when the cell below is the same door.
            propTile("door_wood_upper", 0f, 0.85f);
            propTile("door_metal_upper", 0f, 0.4f);
        }

        private void register(String id, BlockType block, String top, String side, String bottom,
                              int variants, TintMode tint, boolean cutout, float emissive, float roughness) {
            int[] t = new int[variants];
            int[] s = new int[variants];
            int[] b = new int[variants];
            for (int v = 0; v < variants; v++) {
                t[v] = layerFor(top, v, emissive, roughness);
                s[v] = layerFor(side, v, emissive, roughness);
                b[v] = layerFor(bottom, v, emissive, roughness);
            }
            BlockMaterial m = new BlockMaterial(id, tint, cutout, emissive, roughness, variants, t, s, b);
            byId.put(id, m);
            byBlock.put(block, m);
        }

        /** Registers a tile not tied to a block face, for special-shape props. */
        private int propTile(String tileId, float emissive, float roughness) {
            return layerFor(tileId, 0, emissive, roughness);
        }

        private int layerFor(String tileId, int variant, float emissive, float roughness) {
            String key = tileId + "#" + variant;
            Integer existing = layerOf.get(key);
            if (existing != null) {
                return existing;
            }
            int[] pixels = loadTile(tileId, variant);
            if (pixels == null) {
                errors.add("No texture for tile '" + tileId + "' variant " + variant
                        + " (no PNG override, no procedural case) -> checker");
                layerOf.put(key, 0);
                return 0;
            }
            int layer = tilePixels.size();
            if (layer >= MAX_LAYERS) {
                errors.add("Texture array overflow at tile '" + tileId + "' (max " + MAX_LAYERS + ")");
                return 0;
            }
            tilePixels.add(pixels);
            layerOf.put(key, layer);
            layerProps[layer * 2] = emissive;
            layerProps[layer * 2 + 1] = roughness;
            return layer;
        }

        /** PNG override first (assets/textures/blocks/), procedural generator second. */
        private int[] loadTile(String tileId, int variant) {
            String file = variant == 0 ? tileId + ".png" : tileId + "_" + (variant + 1) + ".png";
            String path = "textures/blocks/" + file;
            if (ResourceManager.exists(path)) {
                int[] png = decodePng(path);
                if (png != null) {
                    return png;
                }
            }
            return ProceduralTextures.generate(tileId, variant, TILE_SIZE);
        }

        private int[] decodePng(String path) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer data = ResourceManager.readBuffer(path);
                IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), n = stack.mallocInt(1);
                ByteBuffer px = STBImage.stbi_load_from_memory(data, w, h, n, 4);
                if (px == null) {
                    errors.add("PNG decode failed for assets/" + path + ": " + STBImage.stbi_failure_reason());
                    return null;
                }
                int sw = w.get(0), sh = h.get(0);
                int[] out = new int[TILE_SIZE * TILE_SIZE];
                for (int y = 0; y < TILE_SIZE; y++) {
                    for (int x = 0; x < TILE_SIZE; x++) {
                        // Nearest resample so any square source works.
                        int sx = x * sw / TILE_SIZE, sy = y * sh / TILE_SIZE;
                        int i = (sy * sw + sx) * 4;
                        int r = px.get(i) & 0xFF, g = px.get(i + 1) & 0xFF,
                                b = px.get(i + 2) & 0xFF, a = px.get(i + 3) & 0xFF;
                        out[y * TILE_SIZE + x] = (a << 24) | (r << 16) | (g << 8) | b;
                    }
                }
                STBImage.stbi_image_free(px);
                System.out.println("[materials] loaded override assets/" + path);
                return out;
            } catch (RuntimeException e) {
                errors.add("PNG load failed for assets/" + path + ": " + e.getMessage());
                return null;
            }
        }

        private void validate() {
            for (BlockType t : BlockType.values()) {
                if (t.shape == BlockType.Shape.NONE || t.shape == BlockType.Shape.LIQUID) {
                    continue;
                }
                if (!byBlock.containsKey(t)) {
                    errors.add("BlockType." + t.name() + " has no material -> checker fallback");
                }
            }
            for (String e : errors) {
                System.err.println("[materials] PROBLEM: " + e);
            }
        }
    }

    private static int[] checkerTile() {
        int[] px = new int[TILE_SIZE * TILE_SIZE];
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                boolean m = ((x / 8) + (y / 8)) % 2 == 0;
                px[y * TILE_SIZE + x] = m ? 0xFFFF00FF : 0xFF101010;
            }
        }
        return px;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /** The material of a block; the checker fallback for an unregistered one, null before {@link #init}. */
    public static BlockMaterial of(BlockType t) {
        BlockMaterial m = current.byBlock.get(t);
        return m != null ? m : current.fallback;
    }

    public static BlockMaterial byId(String id) {
        BlockMaterial m = current.byId.get(id);
        return m != null ? m : current.fallback;
    }

    public static int layer(String tileId) {
        Integer l = current.layerOf.get(tileId + "#0");
        return l != null ? l : 0;
    }

    /** CPU pixels of a layer (valid after init; used for icon compositing). */
    public static int[] layerPixels(int layer) {
        List<int[]> tiles = current.tilePixels;
        return layer >= 0 && layer < tiles.size() ? tiles.get(layer) : tiles.get(0);
    }

    public static TextureArray textureArray() {
        return array;
    }

    /** Interleaved (emissive, roughness) per layer for the chunk shader. */
    public static float[] layerProps() {
        return current.layerProps;
    }

    public static int layerCount() {
        return current.tilePixels.size();
    }

    public static int materialCount() {
        return current.byId.size();
    }

    public static List<String> errors() {
        return current.errors;
    }

    public static void delete() {
        if (array != null) {
            array.delete();
            array = null;
        }
        current = new Catalog();
    }
}
