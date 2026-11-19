package com.veylon.save;

import com.veylon.Game;
import com.veylon.entity.BodyFamily;
import com.veylon.entity.BodyFragment;
import com.veylon.entity.BodyFragmentConstants;
import com.veylon.entity.Carcass;
import com.veylon.entity.FragmentAnatomy;
import com.veylon.entity.FragmentPiece;
import com.veylon.entity.FragmentPose;
import com.veylon.entity.RagdollConstants;
import com.veylon.item.ItemType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static com.veylon.save.BodiesSection.MAX_HORIZONTAL;
import static com.veylon.save.BodiesSection.MAX_VERTICAL;
import static com.veylon.save.BodiesSection.readAppearance;
import static com.veylon.save.BodiesSection.readFinite;
import static com.veylon.save.BodiesSection.writeAppearance;
import static com.veylon.save.SaveSystem.readCount;

/**
 * The remains of every body blown apart — a person's, an animal's or the
 * player's — lying where they came to rest, in the pose each body died in,
 * with the harvest record an animal's torso carries.
 *
 * <p>This is the only record of the settled pieces. It holds every one of
 * them, people included, in the order {@code BodyFragmentSystem.settled}
 * holds them, so after a load the settled cap evicts the piece it would have
 * evicted before the save. Until 0.9.1 the people were also written to
 * {@code world.fragments} version 1 for builds that did not know this
 * section; no build of this epoch reads that section.
 *
 * <p>Layout, all numeric (see {@link BodiesSection} for why):
 * <pre>
 * int version (1)
 * int poses; per pose:  int family ordinal, int joints (0 = the family's rest pose),
 *                       joints x 7 floats (rotX, rotY, rotZ, poseX, poseY, poseZ, scale)
 * int pieces; per piece: int pose index, int piece id, 3 floats position,
 *                       4 floats orientation (a unit quaternion), float decay,
 *                       the appearance exactly as {@link BodiesSection} writes it
 * int links; per link:  int piece index, int carcass index (the v3 body's order),
 *                       int arrows lodged, int arrow item (0 = none, else ordinal + 1)
 * </pre>
 * A piece's identity is (family, piece id), both append only
 * ({@link BodyFamily}, each family's {@link FragmentAnatomy} piece list); a
 * person's piece id is its {@link BodyFragment.Piece} ordinal. The pieces of
 * one body share one pose, stored once.
 *
 * <p><b>Policy for what this build cannot read.</b> Every count, index and
 * number is bounds checked, and anything malformed — a count over its cap, an
 * index out of range, a non-finite or out-of-bounds number, a quaternion that
 * is not of unit length, a pose that would draw a piece at an implausible
 * scale, two links to one torso or one record, a record on a limb, on a body
 * that leaves no carcass or on another species, trailing bytes — fails the
 * whole load, which leaves the live world untouched. So does anything only
 * another build could have written (0.9.1 D-18): a family ordinal past
 * {@link BodyFamily}, a piece id past the family's list, or a pose whose joint
 * count is neither zero nor the family's. Nothing unknown is ever read as a
 * person's piece.
 *
 * <p>On write, a piece the reader would refuse is left out, so one bad piece
 * of debris can cost that piece, never the save, and a pose it would refuse
 * is written as the family's rest pose: the piece keeps its place, not its
 * articulation. Pieces in flight are never written; {@code SaveSystem.save}
 * settles them first.
 */
final class RemainsSection {

    static final String ID = "world.remains";
    static final int VERSION = 1;
    /** The most pieces the world can hold at rest. */
    static final int MAX_PIECES = BodyFragmentConstants.MAX_SETTLED_FRAGMENTS;
    /** A unit quaternion's components lie in [-1, 1]; the rest is headroom for float drift. */
    private static final float MAX_QUATERNION_COMPONENT = 2f;
    /** Below this squared length a quaternion has no direction worth normalising. */
    private static final float MIN_QUATERNION_LENGTH_SQUARED = 1e-6f;
    /** At most one pose per piece. */
    static final int MAX_POSES = MAX_PIECES;
    /** Every harvest record rides on an anchored torso. */
    static final int MAX_LINKS = BodyFragmentConstants.MAX_ANCHORED_REMAINS;
    /** Radians; the animation's joints stay within a turn. */
    static final float MAX_POSE_ANGLE = 40f;
    /** Metres; the largest pose offset the animation sets is 0.62. */
    static final float MAX_POSE_OFFSET = 4f;
    /** A joint's scale, and the scale it draws a piece at; breathing moves it about a percent. */
    static final float MIN_POSE_SCALE = 0.5f, MAX_POSE_SCALE = 2f;
    /** How far from 1 the squared length of a stored orientation may be. */
    static final float QUATERNION_TOLERANCE = 1e-3f;
    /** Far more arrows than one animal's health lets lodge in it. */
    static final int MAX_STUCK_ARROWS = 1024;

    private static final BodyFamily[] FAMILIES = BodyFamily.values();
    private static final ItemType[] ITEMS = ItemType.values();

    private RemainsSection() {
    }

    // ------------------------------------------------------------------
    // Writing
    // ------------------------------------------------------------------

    static byte[] write(Game game) throws IOException {
        List<BodyFragment> settled = game.fragments.settled;
        if (settled.size() > MAX_PIECES) {
            throw new IOException("too many settled body fragments: " + settled.size());
        }
        List<BodyFragment> kept = new ArrayList<>(settled.size());
        for (BodyFragment f : settled) {
            if (placementReadable(f)) {
                kept.add(f);
            }
        }

        // Each body's pieces share one pose object; store it once.
        Map<FragmentPose, Integer> poseIndex = new IdentityHashMap<>();
        List<FragmentPose> poses = new ArrayList<>();
        int[] poseOf = new int[kept.size()];
        for (int i = 0; i < kept.size(); i++) {
            FragmentPose pose = storable(kept.get(i).pose);
            Integer index = poseIndex.get(pose);
            if (index == null) {
                index = poses.size();
                poseIndex.put(pose, index);
                poses.add(pose);
            }
            poseOf[i] = index;
        }

        Map<Carcass, Integer> carcassIndex = new IdentityHashMap<>();
        List<Carcass> carcasses = game.entities.carcasses;
        for (int i = 0; i < carcasses.size(); i++) {
            carcassIndex.put(carcasses.get(i), i);
        }
        int links = 0;
        for (BodyFragment f : kept) {
            if (linked(f, carcassIndex)) {
                links++;
            }
        }
        if (links > MAX_LINKS) {
            throw new IOException("too many anchored remains: " + links);
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(VERSION);
            out.writeInt(poses.size());
            for (FragmentPose pose : poses) {
                writePose(out, pose);
            }
            out.writeInt(kept.size());
            for (int i = 0; i < kept.size(); i++) {
                BodyFragment f = kept.get(i);
                out.writeInt(poseOf[i]);
                out.writeInt(f.definition.id);
                out.writeFloat(f.pos.x);
                out.writeFloat(f.pos.y);
                out.writeFloat(f.pos.z);
                // Stored normalised: the reader holds it to unit length.
                float length = (float) Math.sqrt(f.orientation.lengthSquared());
                out.writeFloat(f.orientation.x / length);
                out.writeFloat(f.orientation.y / length);
                out.writeFloat(f.orientation.z / length);
                out.writeFloat(f.orientation.w / length);
                out.writeFloat(f.decay);
                writeAppearance(out, f.appearance);
            }
            out.writeInt(links);
            for (int i = 0; i < kept.size(); i++) {
                BodyFragment f = kept.get(i);
                if (!linked(f, carcassIndex)) {
                    continue;
                }
                Carcass record = f.harvest;
                out.writeInt(i);
                out.writeInt(carcassIndex.get(record));
                out.writeInt(Math.clamp(record.stuckArrows, 0, MAX_STUCK_ARROWS));
                out.writeInt(record.stuckArrowType == null ? 0 : record.stuckArrowType.ordinal() + 1);
            }
        }
        return bytes.toByteArray();
    }

    /** A torso whose record is still in the world and tied back to it. */
    private static boolean linked(BodyFragment f, Map<Carcass, Integer> carcassIndex) {
        Carcass record = f.harvest;
        return record != null && record.remains == f && carcassIndex.containsKey(record)
                && f.definition.parent == -1 && f.definition.family.creature == record.type;
    }

    /** The pose to store: a rest pose as the family's own, one the reader would refuse as rest too. */
    private static FragmentPose storable(FragmentPose pose) {
        FragmentAnatomy anatomy = pose.anatomy;
        if (pose.isRest()) {
            return anatomy.restPose();
        }
        for (int j = 0; j < anatomy.jointCount(); j++) {
            if (!withinPose(pose.rotX(j), MAX_POSE_ANGLE) || !withinPose(pose.rotY(j), MAX_POSE_ANGLE)
                    || !withinPose(pose.rotZ(j), MAX_POSE_ANGLE)
                    || !withinPose(pose.poseX(j), MAX_POSE_OFFSET)
                    || !withinPose(pose.poseY(j), MAX_POSE_OFFSET)
                    || !withinPose(pose.poseZ(j), MAX_POSE_OFFSET)
                    || !scaleReadable(pose.scale(j))) {
                return anatomy.restPose();
            }
        }
        return piecesToScale(pose) ? pose : anatomy.restPose();
    }

    private static void writePose(DataOutputStream out, FragmentPose pose) throws IOException {
        FragmentAnatomy anatomy = pose.anatomy;
        out.writeInt(anatomy.family.ordinal());
        if (pose == anatomy.restPose()) {
            out.writeInt(0);
            return;
        }
        out.writeInt(anatomy.jointCount());
        for (int j = 0; j < anatomy.jointCount(); j++) {
            out.writeFloat(pose.rotX(j));
            out.writeFloat(pose.rotY(j));
            out.writeFloat(pose.rotZ(j));
            out.writeFloat(pose.poseX(j));
            out.writeFloat(pose.poseY(j));
            out.writeFloat(pose.poseZ(j));
            out.writeFloat(pose.scale(j));
        }
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    /** One harvest link as read: which piece, which record, and the record's arrows. */
    private record Link(int piece, Carcass record, int arrows, ItemType arrowType) {
    }

    /**
     * Reads and validates the whole section first, then lays its pieces down
     * oldest first, re-tying each torso to its harvest record. The world is
     * untouched until every record has been checked.
     */
    static void read(byte[] payload, Game game) throws IOException {
        List<Carcass> carcasses = game.entities.carcasses;
        BodyFragment[] pieces;
        List<Link> links;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            int version = in.readInt();
            if (version != VERSION) {
                throw new IOException("unsupported remains section version " + version);
            }
            FragmentPose[] poses = readPoses(in);
            pieces = readPieces(in, poses);
            links = readLinks(in, pieces, carcasses);
            if (in.available() != 0) {
                throw new IOException("unexpected bytes after remains section");
            }
        }

        Carcass[] harvest = new Carcass[pieces.length];
        for (Link link : links) {
            link.record.stuckArrows = link.arrows;
            link.record.stuckArrowType = link.arrowType;
            harvest[link.piece] = link.record;
        }
        for (int i = 0; i < pieces.length; i++) {
            if (harvest[i] != null) {
                game.fragments.restoreSettled(pieces[i], harvest[i]);
            } else {
                game.fragments.restoreSettled(pieces[i]);
            }
        }
    }

    /** @return one pose per entry */
    private static FragmentPose[] readPoses(DataInputStream in) throws IOException {
        int count = readCount(in, "remains poses", MAX_POSES);
        FragmentPose[] poses = new FragmentPose[count];
        for (int p = 0; p < count; p++) {
            int family = in.readInt();
            if (family < 0 || family >= FAMILIES.length) {
                throw new IOException("invalid remains body family ordinal: " + family);
            }
            FragmentAnatomy anatomy = FAMILIES[family].anatomy();
            int joints = in.readInt();
            if (joints == 0) {
                poses[p] = anatomy.restPose();
                continue;
            }
            if (joints != anatomy.jointCount()) {
                throw new IOException("a " + anatomy.family + " pose lists " + joints
                        + " joints; the family has " + anatomy.jointCount());
            }
            FragmentPose.Recorder recorder = new FragmentPose.Recorder(anatomy);
            for (int j = 0; j < joints; j++) {
                float rotX = readFinite(in, "pose angle", -MAX_POSE_ANGLE, MAX_POSE_ANGLE);
                float rotY = readFinite(in, "pose angle", -MAX_POSE_ANGLE, MAX_POSE_ANGLE);
                float rotZ = readFinite(in, "pose angle", -MAX_POSE_ANGLE, MAX_POSE_ANGLE);
                float poseX = readFinite(in, "pose offset", -MAX_POSE_OFFSET, MAX_POSE_OFFSET);
                float poseY = readFinite(in, "pose offset", -MAX_POSE_OFFSET, MAX_POSE_OFFSET);
                float poseZ = readFinite(in, "pose offset", -MAX_POSE_OFFSET, MAX_POSE_OFFSET);
                float scale = readFinite(in, "pose scale", MIN_POSE_SCALE, MAX_POSE_SCALE);
                recorder.set(j, rotX, rotY, rotZ, poseX, poseY, poseZ, scale);
            }
            FragmentPose pose = recorder.snapshot();
            if (!piecesToScale(pose)) {
                throw new IOException("a " + anatomy.family + " pose draws a piece out of scale");
            }
            poses[p] = pose;
        }
        return poses;
    }

    /** @return one piece per record */
    private static BodyFragment[] readPieces(DataInputStream in, FragmentPose[] poses) throws IOException {
        int count = readCount(in, "remains pieces", MAX_PIECES);
        BodyFragment[] pieces = new BodyFragment[count];
        for (int i = 0; i < count; i++) {
            int poseIndex = in.readInt();
            if (poseIndex < 0 || poseIndex >= poses.length) {
                throw new IOException("remains piece refers to missing pose " + poseIndex);
            }
            FragmentPose pose = poses[poseIndex];
            int id = in.readInt();
            if (id < 0 || id >= pose.anatomy.pieces.size()) {
                throw new IOException("invalid " + pose.anatomy.family + " piece id: " + id);
            }
            float x = readFinite(in, "remains x", -MAX_HORIZONTAL, MAX_HORIZONTAL);
            float y = readFinite(in, "remains y", -MAX_VERTICAL, MAX_VERTICAL);
            float z = readFinite(in, "remains z", -MAX_HORIZONTAL, MAX_HORIZONTAL);
            float qx = readQuaternion(in, "x");
            float qy = readQuaternion(in, "y");
            float qz = readQuaternion(in, "z");
            float qw = readQuaternion(in, "w");
            float length = qx * qx + qy * qy + qz * qz + qw * qw;
            if (Math.abs(length - 1f) > QUATERNION_TOLERANCE) {
                throw new IOException("remains orientation is not a unit quaternion: " + length);
            }
            float decay = readFinite(in, "remains decay", 0f, RagdollConstants.CORPSE_DECAY);
            FragmentPiece definition = pose.anatomy.piece(id);
            BodyFragment piece = new BodyFragment(definition, pose);
            piece.pos.set(x, y, z);
            piece.orientation.set(qx, qy, qz, qw).normalize();
            piece.decay = decay;
            readAppearance(in, piece.appearance, "remains");
            pieces[i] = piece;
        }
        return pieces;
    }

    private static List<Link> readLinks(DataInputStream in, BodyFragment[] pieces,
                                        List<Carcass> carcasses) throws IOException {
        int count = readCount(in, "harvest links", MAX_LINKS);
        List<Link> links = new ArrayList<>(count);
        boolean[] pieceTaken = new boolean[pieces.length];
        boolean[] recordTaken = new boolean[carcasses.size()];
        for (int l = 0; l < count; l++) {
            int piece = in.readInt();
            if (piece < 0 || piece >= pieces.length) {
                throw new IOException("harvest link refers to missing piece " + piece);
            }
            int carcass = in.readInt();
            if (carcass < 0 || carcass >= carcasses.size()) {
                throw new IOException("harvest link refers to missing carcass " + carcass);
            }
            int arrows = in.readInt();
            if (arrows < 0 || arrows > MAX_STUCK_ARROWS) {
                throw new IOException("invalid lodged arrow count: " + arrows);
            }
            int item = in.readInt();
            if (item < 0 || item > ITEMS.length) {
                throw new IOException("invalid lodged arrow item ordinal: " + item);
            }
            if (pieceTaken[piece] || recordTaken[carcass]) {
                throw new IOException("a torso carries one harvest record and a record one torso");
            }
            pieceTaken[piece] = true;
            recordTaken[carcass] = true;
            Carcass record = carcasses.get(carcass);
            if (!record.type.leavesCarcass()) {
                throw new IOException("a " + record.type + " leaves no carcass to harvest");
            }
            BodyFragment f = pieces[piece];
            if (f.definition.parent != -1 || f.definition.family.creature != record.type) {
                throw new IOException("the " + f.definition + " piece cannot carry a " + record.type
                        + " carcass");
            }
            links.add(new Link(piece, record, arrows, item == 0 ? null : ITEMS[item - 1]));
        }
        return links;
    }

    // ------------------------------------------------------------------
    // Shared bounds
    // ------------------------------------------------------------------

    private static float readQuaternion(DataInputStream in, String axis) throws IOException {
        return readFinite(in, "remains orientation " + axis,
                -MAX_QUATERNION_COMPONENT, MAX_QUATERNION_COMPONENT);
    }

    /** Whether a piece's position, orientation and rot clock are within what the reader accepts. */
    private static boolean placementReadable(BodyFragment f) {
        float qx = f.orientation.x, qy = f.orientation.y, qz = f.orientation.z;
        float qw = f.orientation.w;
        return withinPose(f.pos.x, MAX_HORIZONTAL) && withinPose(f.pos.y, MAX_VERTICAL)
                && withinPose(f.pos.z, MAX_HORIZONTAL)
                && withinPose(qx, MAX_QUATERNION_COMPONENT) && withinPose(qy, MAX_QUATERNION_COMPONENT)
                && withinPose(qz, MAX_QUATERNION_COMPONENT) && withinPose(qw, MAX_QUATERNION_COMPONENT)
                && qx * qx + qy * qy + qz * qz + qw * qw >= MIN_QUATERNION_LENGTH_SQUARED
                && Float.isFinite(f.decay) && f.decay >= 0f
                && f.decay <= RagdollConstants.CORPSE_DECAY;
    }

    private static boolean withinPose(float value, float max) {
        return Float.isFinite(value) && value >= -max && value <= max;
    }

    private static boolean scaleReadable(float scale) {
        return Float.isFinite(scale) && scale >= MIN_POSE_SCALE && scale <= MAX_POSE_SCALE;
    }

    /** Whether the pose draws every piece of its body at a plausible size. */
    private static boolean piecesToScale(FragmentPose pose) {
        for (FragmentPiece p : pose.anatomy.pieces) {
            if (!scaleReadable(pose.pieceScale(p.id))) {
                return false;
            }
        }
        return true;
    }
}
