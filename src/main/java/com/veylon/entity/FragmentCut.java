package com.veylon.entity;

/**
 * A wound on one face of a piece's collision box, where the body was cut at a
 * joint. Every cut joint leaves two: one on the piece it was cut from and one
 * on the severed piece's own end.
 *
 * <p>The wound is a flat rectangle lying on the face: {@link #axis} and
 * {@link #side} name the face, the centre lies on its plane and the size along
 * {@link #axis} is zero. How far it stands proud of the surface, and over what
 * clothing, is the renderer's business. Coordinates are rest-pose model space,
 * like {@link FragmentPiece#centreX}; subtracting the piece's centre gives the
 * wound's place in the piece's own box frame, which is how a piece posed at
 * death carries it.
 */
public final class FragmentCut {

    /** Id of the piece whose box carries this wound. */
    public final int piece;
    /** Id of the piece that was cut away here; {@link #piece} itself for its own severed end. */
    public final int severed;
    /** Index of the cut joint in the anatomy: the severed piece's root. */
    public final int joint;
    /** True for the severed piece's own end, false where a child piece was cut off. */
    public final boolean ownEnd;
    /** The face: 0, 1 or 2 for x, y or z of the piece's box. */
    public final int axis;
    /** +1 for the face on the positive side of {@link #axis}, -1 for the negative one. */
    public final float side;
    /** Centre of the wound on the face, rest-pose model space. */
    public final float centreX, centreY, centreZ;
    /** Extent across the face along each axis; zero along {@link #axis}. */
    public final float sizeX, sizeY, sizeZ;

    FragmentCut(int piece, int severed, int joint, int axis, float side, float[] centre, float[] size) {
        this.piece = piece;
        this.severed = severed;
        this.joint = joint;
        this.ownEnd = piece == severed;
        this.axis = axis;
        this.side = side;
        this.centreX = centre[0];
        this.centreY = centre[1];
        this.centreZ = centre[2];
        this.sizeX = size[0];
        this.sizeY = size[1];
        this.sizeZ = size[2];
    }
}
