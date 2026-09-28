package com.veylon.save;

import com.veylon.Game;
import com.veylon.entity.BodyFamily;
import com.veylon.entity.BodyFragment;
import com.veylon.entity.BodyFragmentConstants;
import com.veylon.entity.Carcass;
import com.veylon.entity.FragmentAnatomy;
import com.veylon.entity.FragmentPiece;
import com.veylon.entity.FragmentPose;
import com.veylon.entity.NpcAppearance;
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
 * Optional v3 state: the remains of every body blown apart — a person's, an
 * animal's or the player's — lying where they came to rest, in the pose each
 * body died in, with the harvest record an animal's torso carries.
 *
 * <p>This is the authoritative record of the settled pieces. It holds every one
 * of them, people included, in the order {@code BodyFragmentSystem.settled}
 * holds them, so after a load the settled cap evicts the piece it would have
 * evicted before the save. {@link FragmentsSection} version 1 is still written
 * beside it, unchanged, for builds that do not know this section: when both
 * are present this one is restored and that one only validated.
 *
 * <p>It is a new stable ID rather than version 2 of {@code world.fragments}
 * because the version 1 reader refuses any other version of its section and
 * with it the whole save, while every reader skips an ID it does not know by
 * its length. A v0.8.0 build therefore opens a newer save with its people,
 * their pieces in the standing rest pose, and without the animals' pieces;
 * an animal's harvest record, which lives in the frozen v3 body, then loads
 * as an ordinary whole carcass: one body, the same meat and hide.
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
 * person's piece id is its {@code world.fragments} version 1 ordinal. The
 * pieces of one body share one pose, stored once.
 *
 * <p><b>Policy for what this build cannot read.</b> Every count, index and
 * number is bounds checked, and anything malformed — a count over its cap, an
 * index out of range, a non-finite or out-of-bounds number, a quaternion that
 * is not of unit length, a pose that would draw a piece at an implausible
 * scale, two links to one torso or one record, a record on a limb, on a body
 * that leaves no carcass or on another species, trailing bytes — fails the
 * whole load, which leaves the live world untouched. A body this build does
 * not know — a family ordinal past {@link BodyFamily}, or a piece id past the
 * family's list — is <em>skipped</em>: its records are fixed in size, so they
 * are read, checked and dropped, and a link to such a piece leaves its
 * carcass a whole one, arrows included. A known family whose pose lists
 * another joint count than this build's table — a table from another build —
 * keeps its pieces in the family's rest pose, as version 1 people load.
 * Nothing unknown is ever read as a person's piece.
 *
 * <p>On write, a piece the reader would refuse is left out, as in version 1,
 * and a pose it would refuse is written as the family's rest pose: the piece
 * keeps its place, not its articulation. Pieces in flight are never written;
 * {@code SaveSystem.save} settles them first.
 */
final class RemainsSection {

    static final String ID = "world.remains";
    static final int VERSION = 1;
    /** The most pieces the world can hold at rest. */
    static final int MAX_PIECES = BodyFragmentConstants.MAX_SETTLED_FRAGMENTS;
    /** At most one pose per piece. */
    static final int MAX_POSES = MAX_PIECES;
    /** Every harvest record rides on an anchored torso. */
    static final int MAX_LINKS = BodyFragmentConstants.MAX_ANCHORED_REMAINS;
    /**
     * Joints a pose entry may list: headroom over {@link FragmentAnatomy#MAX_JOINTS},
     * so a newer build's pose for a body this one does not know can be skipped.
     */
    static final int MAX_POSE_JOINTS = 64;
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

    private static final int STRIDE = 7;
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
            if (FragmentsSection.placementReadable(f)) {
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
            if (version < 1 || version > VERSION) {
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
            harvest[link.piece] = pieces[link.piece] == null ? null : link.record;
        }
        for (int i = 0; i < pieces.length; i++) {
            if (pieces[i] == null) {
                continue;
            }
            if (harvest[i] != null) {
                game.fragments.restoreSettled(pieces[i], harvest[i]);
            } else {
                game.fragments.restoreSettled(pieces[i]);
            }
        }
    }

    /** @return one pose per entry; null for a body family this build does not know */
    private static FragmentPose[] readPoses(DataInputStream in) throws IOException {
        int count = readCount(in, "remains poses", MAX_POSES);
        FragmentPose[] poses = new FragmentPose[count];
        for (int p = 0; p < count; p++) {
            int family = in.readInt();
            if (family < 0) {
                throw new IOException("invalid remains body family ordinal: " + family);
            }
            int joints = in.readInt();
            if (joints < 0 || joints > MAX_POSE_JOINTS) {
                throw new IOException("invalid remains pose joint count: " + joints);
            }
            float[] values = new float[joints * STRIDE];
            for (int j = 0; j < joints; j++) {
                int o = j * STRIDE;
                for (int k = 0; k < 3; k++) {
                    values[o + k] = readFinite(in, "pose angle", -MAX_POSE_ANGLE, MAX_POSE_ANGLE);
                }
                for (int k = 3; k < 6; k++) {
                    values[o + k] = readFinite(in, "pose offset", -MAX_POSE_OFFSET, MAX_POSE_OFFSET);
                }
                values[o + 6] = readFinite(in, "pose scale", MIN_POSE_SCALE, MAX_POSE_SCALE);
            }
            if (family >= FAMILIES.length) {
                continue; // a body a newer build knows; its pieces are skipped
            }
            FragmentAnatomy anatomy = FAMILIES[family].anatomy();
            if (joints == 0) {
                poses[p] = anatomy.restPose();
            } else if (joints == anatomy.jointCount()) {
                FragmentPose.Recorder recorder = new FragmentPose.Recorder(anatomy);
                for (int j = 0; j < joints; j++) {
                    int o = j * STRIDE;
                    recorder.set(j, values[o], values[o + 1], values[o + 2],
                            values[o + 3], values[o + 4], values[o + 5], values[o + 6]);
                }
                FragmentPose pose = recorder.snapshot();
                if (!piecesToScale(pose)) {
                    throw new IOException("a " + anatomy.family + " pose draws a piece out of scale");
                }
                poses[p] = pose;
            } else {
                // A joint table from another build: the pieces and where they
                // lie are known, how their joints were turned is not.
                poses[p] = anatomy.restPose();
            }
        }
        return poses;
    }

    /** @return one piece per record; null where the record's body or piece is unknown here */
    private static BodyFragment[] readPieces(DataInputStream in, FragmentPose[] poses) throws IOException {
        int count = readCount(in, "remains pieces", MAX_PIECES);
        BodyFragment[] pieces = new BodyFragment[count];
        for (int i = 0; i < count; i++) {
            int poseIndex = in.readInt();
            if (poseIndex < 0 || poseIndex >= poses.length) {
                throw new IOException("remains piece refers to missing pose " + poseIndex);
            }
            int id = in.readInt();
            if (id < 0) {
                throw new IOException("invalid remains piece id: " + id);
            }
            float x = readFinite(in, "remains x", -MAX_HORIZONTAL, MAX_HORIZONTAL);
            float y = readFinite(in, "remains y", -MAX_VERTICAL, MAX_VERTICAL);
            float z = readFinite(in, "remains z", -MAX_HORIZONTAL, MAX_HORIZONTAL);
            float qx = FragmentsSection.readQuaternion(in, "x");
            float qy = FragmentsSection.readQuaternion(in, "y");
            float qz = FragmentsSection.readQuaternion(in, "z");
            float qw = FragmentsSection.readQuaternion(in, "w");
            float length = qx * qx + qy * qy + qz * qz + qw * qw;
            if (Math.abs(length - 1f) > QUATERNION_TOLERANCE) {
                throw new IOException("remains orientation is not a unit quaternion: " + length);
            }
            float decay = readFinite(in, "remains decay", 0f, RagdollConstants.CORPSE_DECAY);
            BodyFragment scratch = null;
            FragmentPose pose = poses[poseIndex];
            if (pose != null && id < pose.anatomy.pieces.size()) {
                FragmentPiece definition = pose.anatomy.piece(id);
                scratch = new BodyFragment(definition, pose);
                scratch.pos.set(x, y, z);
                scratch.orientation.set(qx, qy, qz, qw).normalize();
                scratch.decay = decay;
            }
            // Read even for a skipped piece: the record's size does not depend on its body.
            readAppearance(in, scratch != null ? scratch.appearance
                    : new NpcAppearance(), "remains");
            pieces[i] = scratch;
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
            if (f != null && (f.definition.parent != -1 || f.definition.family.creature != record.type)) {
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
