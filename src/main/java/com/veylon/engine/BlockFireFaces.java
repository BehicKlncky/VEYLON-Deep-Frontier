package com.veylon.engine;

import com.veylon.util.Vec3i;
import com.veylon.world.World;

import java.util.Random;

/**
 * The faces a burning block's fire is seen on (0.9.1 B09). Every block that burns is opaque, so
 * a fire drawn inside it, as its glow and its flames and embers were, is hidden by the block
 * itself: a burning trunk or canopy showed little more than smoke. The fire shows on the faces
 * open to the air instead: the ambience starts its flames, embers and smoke just outside them, and
 * the renderer lays its glow on them. The bottom face is never one: fire climbs.
 *
 * <p>Presentation only: the faces read the world and the particles take the emitter's own
 * random stream, so nothing here touches the simulation. Allocation free.
 */
public final class BlockFireFaces {

    /** Faces by index: east (+x), west (−x), south (+z), north (−z) and the top. */
    public static final int EAST = 0, WEST = 1, SOUTH = 2, NORTH = 3, UP = 4;
    /** The top's bit in a mask of open faces. */
    public static final int TOP = 1 << UP;
    /** What {@link #emit} starts. */
    public static final int SMOKE = 0, FLAME = 1, EMBER = 2;
    /** Each face's outward normal, by face index. */
    private static final int[] NX = {1, -1, 0, 0, 0}, NY = {0, 0, 0, 0, 1}, NZ = {0, 0, 1, -1, 0};
    /** How far outside its face a particle starts. */
    static final float OUT = 0.06f;
    /** How far a particle is scattered along its face, and across it: never back into the block. */
    static final float ALONG = 0.42f, ACROSS = 0.03f;

    private BlockFireFaces() {
    }

    /** The faces of the block at x, y, z whose neighbour is not opaque, as a mask of face bits. */
    public static int open(World world, int x, int y, int z) {
        int mask = 0;
        for (int face = 0; face <= UP; face++) {
            if (!world.getBlock(x + NX[face], y + NY[face], z + NZ[face]).opaque) {
                mask |= 1 << face;
            }
        }
        return mask;
    }

    /** One of the open faces in {@code open}, each as likely; {@code open} must not be empty. */
    public static int pick(int open, Random rng) {
        int k = rng.nextInt(Integer.bitCount(open));
        for (int face = 0; face < UP; face++) {
            if ((open & (1 << face)) != 0 && k-- == 0) {
                return face;
            }
        }
        return UP;
    }

    /** A face's outward normal along x, y or z. */
    public static int normalX(int face) {
        return NX[face];
    }

    public static int normalY(int face) {
        return NY[face];
    }

    public static int normalZ(int face) {
        return NZ[face];
    }

    /**
     * Starts one particle of {@code kind} just outside face {@code face} of the burning block at
     * {@code cell}: on the top, scattered over it; on a side, at a height from low on it to its
     * middle (the fire climbs), scattered along the face and only a little across it.
     */
    public static void emit(ParticleSystem particles, int kind, int face, Vec3i cell, Random rng) {
        float x, y, z, spreadX, spreadZ;
        if (face == UP) {
            x = cell.x() + 0.5f;
            y = cell.y() + 1f + OUT;
            z = cell.z() + 0.5f;
            spreadX = ALONG;
            spreadZ = ALONG;
        } else {
            x = cell.x() + 0.5f + NX[face] * (0.5f + OUT);
            y = cell.y() + 0.1f + rng.nextFloat() * 0.5f;
            z = cell.z() + 0.5f + NZ[face] * (0.5f + OUT);
            spreadX = NX[face] != 0 ? ACROSS : ALONG;
            spreadZ = NZ[face] != 0 ? ACROSS : ALONG;
        }
        switch (kind) {
            case SMOKE -> particles.smoke(x, face == UP ? y : y + 0.4f, z, 1f, spreadX, spreadZ);
            case FLAME -> particles.flame(x, y, z, spreadX, spreadZ);
            default -> particles.ember(x, y, z, spreadX, spreadZ);
        }
    }
}
