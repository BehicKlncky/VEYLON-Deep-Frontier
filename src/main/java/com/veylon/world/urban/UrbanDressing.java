package com.veylon.world.urban;

import com.veylon.world.BlockType;

/**
 * How a generated city's surfaces show their district and their years (urban milestone 22): a
 * grade on walls and ground by district (warm brick and a mossy foot in the housing, grime
 * downtown, soot and rust in the yards, a damp cast and a tide line in the flooded quarter, a
 * cleaner precinct, a sickly wash along the quarantine), and sparse decals laid over wall faces
 * (ivy climbing from the pavement, or inside the cordon the veil's own growth; paper notices; the
 * quarantine's cordon mark; mould at the foot of damp rooms; a photograph or a child's drawing
 * left on a flat's wall).
 *
 * <p>Presentation only. Everything is derived from the district plan, the block and its position,
 * like the material variants: nothing is saved, placed or collided with, so dressing can never
 * change saved geometry or narrow a route. A loose panel over a hidden cellar is graded and
 * dressed exactly as the plaster it hides in, so the dressing never gives a secret away. Cities
 * planned before the six districts were housing, and are dressed as housing.
 */
public final class UrbanDressing {
    /** A wall decal; its tile is {@link #tile()}. */
    public enum Decal {
        IVY("decal_ivy"), IVY_TOP("decal_ivy_top"), POSTER("decal_poster"), NOTICE("decal_notice"),
        CORDON_MARK("decal_cordon"), MOULD("decal_mould"), PHOTO("decal_photo"), DRAWING("decal_drawing"),
        VEIL_CREEP("decal_veil"), VEIL_CREEP_TOP("decal_veil_top");

        private final String tile;

        Decal(String tile) {
            this.tile = tile;
        }

        public String tile() {
            return tile;
        }
    }

    /** The first cell above the pavement: where ivy roots and the flood's tide line sits. */
    static final int FOOT_Y = UrbanDatum.STREET_Y + 1;
    /** Decals laid in one chunk mesh at most; the mesh never grows with dressing beyond this. */
    public static final int MAX_DECALS_PER_CHUNK = 384;

    private UrbanDressing() { }

    /** Whether a block is a city surface the district grade and wall decals may touch. */
    public static boolean dressed(BlockType block) {
        return switch (block) {
            case BRICK, PLASTER, HIDDEN_PANEL, CONCRETE, ASPHALT, PAVING, SHEET_METAL, RUBBLE, STAIRS,
                 STAIRS_NORTH, STAIRS_SOUTH, STAIRS_WEST, STAIRS_EAST -> true;
            default -> false;
        };
    }

    private static boolean wall(BlockType block) {
        return block == BlockType.BRICK || block == BlockType.PLASTER || block == BlockType.HIDDEN_PANEL
                || block == BlockType.CONCRETE;
    }

    /**
     * Writes a face's district grade, RGB multipliers near 1, into {@code out}. {@code face} is the
     * mesher's direction (0 up, 1 down, 2..5 the sides); {@code exterior} is whether the open sky
     * reaches the cell in front of it. Outdoors every district has its cast; indoors only the
     * flooded quarter's water damage and the yards' soot show.
     */
    public static void grade(UrbanDistrict district, BlockType block, int face, int y, boolean exterior, float[] out) {
        out[0] = out[1] = out[2] = 1f;
        if (!dressed(block)) { return; }
        UrbanDistrict d = district == null ? UrbanDistrict.RESIDENTIAL : district;
        boolean side = face >= 2;
        if (d == UrbanDistrict.FLOODED && side && (y == FOOT_Y || y == FOOT_Y + 1)) {
            // The tide line: the water stood here, inside and out.
            set(out, y == FOOT_Y ? 0.76f : 0.88f, y == FOOT_Y ? 0.82f : 0.91f, y == FOOT_Y ? 0.72f : 0.84f);
            return;
        }
        if (d == UrbanDistrict.FLOODED && !exterior && y < UrbanDatum.STREET_Y) {
            set(out, 0.84f, 0.89f, 0.82f); // cellars that flooded and never dried
            return;
        }
        if (!exterior) {
            if (d == UrbanDistrict.INDUSTRIAL) { set(out, 0.92f, 0.89f, 0.85f); }
            return;
        }
        boolean foot = side && y == FOOT_Y;
        switch (d) {
            case RESIDENTIAL -> {
                if (foot) { set(out, 0.88f, 0.94f, 0.80f); } else { set(out, 1.0f, 0.99f, 0.96f); }
            }
            case COMMERCIAL -> set(out, 0.93f, 0.93f, 0.95f);
            case INDUSTRIAL -> set(out, 0.86f, 0.82f, 0.76f);
            case FLOODED -> set(out, 0.92f, 0.96f, 0.96f);
            case CIVIC -> set(out, 1.02f, 1.02f, 1.04f);
            case QUARANTINE -> {
                if (foot) { set(out, 0.84f, 0.92f, 0.70f); } else { set(out, 0.90f, 0.95f, 0.84f); }
            }
        }
    }

    private static void set(float[] out, float r, float g, float b) {
        out[0] = r;
        out[1] = g;
        out[2] = b;
    }

    /**
     * The decal on one side face of a wall, or null. {@code exterior} as for {@link #grade};
     * {@code rooted} is whether solid pavement or soil lies at the street under the column in
     * front, where ivy can climb from. Sparse by design: a few faces in a hundred at most.
     */
    public static Decal decal(UrbanDistrict district, BlockType block, int face, int x, int y, int z,
                              boolean exterior, boolean rooted) {
        if (face < 2) { return null; }
        UrbanDistrict d = district == null ? UrbanDistrict.RESIDENTIAL : district;
        if (block == BlockType.SHEET_METAL) {
            // The cordon's fences carry its mark at eye height, nearly one panel in three.
            return exterior && d == UrbanDistrict.QUARANTINE && y == FOOT_Y + 1
                    && hash(x, y, z, face, 11) < 0.3f ? Decal.CORDON_MARK : null;
        }
        if (!wall(block)) { return null; }
        if (exterior) {
            Decal ivy = ivy(d, face, x, y, z, rooted);
            if (ivy != null) { return ivy; }
            if (y == FOOT_Y + 1) {
                float h = hash(x, y, z, face, 13);
                float notices = switch (d) {
                    case COMMERCIAL -> 0.10f;
                    case CIVIC -> 0.06f;
                    case RESIDENTIAL -> 0.03f;
                    default -> 0f;
                };
                if (d == UrbanDistrict.QUARANTINE && h < 0.10f) { return Decal.CORDON_MARK; }
                if (h < notices) { return hash(x, y, z, face, 17) < 0.5f ? Decal.POSTER : Decal.NOTICE; }
            }
            return null;
        }
        boolean housing = d == UrbanDistrict.RESIDENTIAL || d == UrbanDistrict.FLOODED;
        if (!housing || block == BlockType.CONCRETE) { return null; }
        int level = Math.floorMod(y - UrbanDatum.STREET_Y, UrbanDatum.STOREY_PITCH);
        if (level == 1 && hash(x, y, z, face, 19) < (d == UrbanDistrict.FLOODED ? 0.12f : 0.04f)) { return Decal.MOULD; }
        if (level == 2 && hash(x, y, z, face, 23) < 0.025f) {
            return hash(x, y, z, face, 29) < 0.5f ? Decal.PHOTO : Decal.DRAWING;
        }
        return null;
    }

    /**
     * Ivy on a facade column, rooted at the pavement and one to three cells tall; inside the
     * quarantine the veil's growth climbs the walls instead, the cordon's mark of what lives there.
     */
    private static Decal ivy(UrbanDistrict d, int face, int x, int y, int z, boolean rooted) {
        float chance = switch (d) {
            case RESIDENTIAL -> 0.14f;
            case FLOODED -> 0.08f;
            case CIVIC -> 0.04f;
            case QUARANTINE -> 0.12f;
            default -> 0f;
        };
        if (!rooted || y < FOOT_Y || y > FOOT_Y + 2) { return null; }
        if (hash(x, 0, z, face, 31) >= chance) { return null; }
        int height = 1 + (int) (hash(x, 0, z, face, 37) * 3);
        if (y >= FOOT_Y + height) { return null; }
        boolean veil = d == UrbanDistrict.QUARANTINE;
        if (y == FOOT_Y) { return veil ? Decal.VEIL_CREEP : Decal.IVY; }
        return veil ? Decal.VEIL_CREEP_TOP : Decal.IVY_TOP;
    }

    /** A stable value in [0, 1) for a face, independent of every other face and of any generator. */
    static float hash(int x, int y, int z, int face, int salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L
                ^ (face * 31L + salt) * 0xD6E8FEB86659FD93L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return (h >>> 40) / (float) (1L << 24);
    }
}
