package com.veylon.entity;

import java.util.List;

/**
 * One piece a body comes apart into: which model parts it owns, where it was
 * cut from its neighbour, and the rigid box it flies as.
 *
 * <p>Built once by {@link FragmentAnatomy} and never changed. The {@link #id}
 * is the piece's stable identity inside its {@link BodyFamily}: its index in
 * {@link FragmentAnatomy#pieces}, append only. Humanoid ids are the ordinals of
 * {@link BodyFragment.Piece}, which is what {@code world.fragments} version 1
 * stores.
 *
 * <p>Every position is rest-pose model space: the model standing with no
 * animation, feet at the origin, facing -Z. The collision box is the box of
 * {@link #boxPart}, grown by the split halves merged into it, never a bone
 * radius.
 */
public final class FragmentPiece {

    public final FragmentAnatomy anatomy;
    public final BodyFamily family;
    /** Stable id within the family; index into {@link FragmentAnatomy#pieces}. */
    public final int id;
    /** Stable lowercase name, unique within the family. */
    public final String name;

    /** Index of the joint this piece was cut at; its subtree is what the piece draws. */
    public final int rootJoint;
    /** The model part at {@link #rootJoint}. */
    public final String rootPart;
    /** Index of the joint whose box is the collision box: the first box below the cut. */
    public final int boxJoint;
    /** The model part at {@link #boxJoint}. */
    public final String boxPart;
    /** Id of the piece this one was cut from, or -1 for the core every other piece hangs off. */
    public final int parent;
    /** True for every piece but the core: its root is a joint a blast cut through. */
    public final boolean severed;

    /**
     * The joints this piece owns, in table order: its root, everything below it
     * down to the next piece's root, and for the core also the box-less model
     * root. Accessories without a joint belong to their parent part and are not
     * listed; they ride along in the model subtree.
     */
    public final List<String> ownedParts;
    /** Children of owned parts that other pieces draw: what isolating this piece hides. */
    public final List<String> excludedParts;
    /** Split halves merged into the collision box because they are too small to fly alone. */
    public final List<String> mergedParts;

    /** Origin of the frame the root part hangs in: the pivots above it, summed. */
    public final float anchorX, anchorY, anchorZ;
    /** The cut joint: the root part's pivot. */
    public final float pivotX, pivotY, pivotZ;
    /** Collision box centre. */
    public final float centreX, centreY, centreZ;
    /** Collision box centre in the box part's own frame, where a posed piece is measured from. */
    public final float boxOffsetX, boxOffsetY, boxOffsetZ;
    /** Collision box half sizes along the box part's own axes; always positive. */
    public final float halfX, halfY, halfZ;
    /**
     * The sweep pair of the rest orientation: {@code max(halfX, halfZ)} and
     * {@code halfY}, each at least {@link BodyFragmentConstants#MIN_HALF_EXTENT}.
     * A piece refits its sweep box whenever it turns; this is the value before
     * the first turn.
     */
    public final float halfWidth, halfHeight;
    /** Volume of the boxes that make up the collision box. */
    public final float volume;
    /** {@code volume × DENSITY}, as for every piece of every body. */
    public final float mass;
    public final float inverseMass;
    /**
     * The wounds on this piece: for a severed piece its own end first, then one
     * for every piece cut from it, in piece order.
     */
    public final List<FragmentCut> cuts;

    FragmentPiece(FragmentAnatomy anatomy, int id, String name, int rootJoint, int boxJoint, int parent,
                  List<String> ownedParts, List<String> excludedParts, List<String> mergedParts,
                  float[] anchor, float[] pivot, float[] centre, float[] boxOffset, float[] half,
                  float volume, List<FragmentCut> cuts) {
        this.anatomy = anatomy;
        this.family = anatomy.family;
        this.id = id;
        this.name = name;
        this.rootJoint = rootJoint;
        this.rootPart = anatomy.joint(rootJoint).name;
        this.boxJoint = boxJoint;
        this.boxPart = anatomy.joint(boxJoint).name;
        this.parent = parent;
        this.severed = parent >= 0;
        this.ownedParts = List.copyOf(ownedParts);
        this.excludedParts = List.copyOf(excludedParts);
        this.mergedParts = List.copyOf(mergedParts);
        this.anchorX = anchor[0];
        this.anchorY = anchor[1];
        this.anchorZ = anchor[2];
        this.pivotX = pivot[0];
        this.pivotY = pivot[1];
        this.pivotZ = pivot[2];
        this.centreX = centre[0];
        this.centreY = centre[1];
        this.centreZ = centre[2];
        this.boxOffsetX = boxOffset[0];
        this.boxOffsetY = boxOffset[1];
        this.boxOffsetZ = boxOffset[2];
        this.halfX = half[0];
        this.halfY = half[1];
        this.halfZ = half[2];
        this.halfWidth = Math.max(BodyFragmentConstants.MIN_HALF_EXTENT, Math.max(halfX, halfZ));
        this.halfHeight = Math.max(BodyFragmentConstants.MIN_HALF_EXTENT, halfY);
        this.volume = volume;
        this.mass = volume * BodyFragmentConstants.DENSITY;
        this.inverseMass = 1f / mass;
        this.cuts = List.copyOf(cuts);
    }

    @Override
    public String toString() {
        return family + "." + name;
    }
}
