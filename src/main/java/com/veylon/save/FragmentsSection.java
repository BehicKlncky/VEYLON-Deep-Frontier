package com.veylon.save;

import com.veylon.Game;
import com.veylon.entity.BodyFragment;
import com.veylon.entity.BodyFragmentConstants;
import com.veylon.entity.RagdollConstants;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.veylon.save.BodiesSection.MAX_HORIZONTAL;
import static com.veylon.save.BodiesSection.MAX_VERTICAL;
import static com.veylon.save.BodiesSection.readAppearance;
import static com.veylon.save.BodiesSection.readFinite;
import static com.veylon.save.BodiesSection.writeAppearance;
import static com.veylon.save.SaveSystem.readCount;

/**
 * Optional v3 state: the pieces of people blown apart, lying where they came
 * to rest.
 *
 * <p>One record per settled {@link BodyFragment}, in the order
 * {@code BodyFragmentSystem.settled} holds them, oldest first, so after a load
 * the settled cap evicts the same piece it would have evicted before the save.
 * A record is what the renderer and the rot clock read: which piece, where its
 * box centre is, how it is turned, how long it has left and whose look it
 * carries — the last written exactly as {@link BodiesSection} writes a
 * corpse's. Velocity, spin and the settle counters are zero or spent once a
 * piece is at rest, and its sweep box follows from the orientation, so none
 * of them is stored.
 *
 * <p>An absent section is what every save written before this feature means:
 * the world holds no pieces. It is a new stable ID rather than a new version
 * of {@code world.bodies}, so the frozen v3 body and the bodies layout are
 * untouched, and an older build skips it by its length instead of refusing
 * the whole save.
 *
 * <p><b>Pieces in flight are deliberately not persisted.</b> Like falling
 * ragdolls they are transient. A save settles every flying piece first, so a
 * save taken mid-flight writes the piece where it lands rather than losing
 * it; loading clears both lists through the {@code newWorld} reset path
 * before this section is read.
 *
 * <p>The record is entirely numeric for the reason {@link BodiesSection}
 * gives: the piece and the archetype travel as bounds-checked ordinals. A
 * piece the reader would refuse is left out on write, so one bad piece of
 * debris can cost that piece, never the save.
 */
final class FragmentsSection {

    static final String ID = "world.fragments";
    static final int VERSION = 1;
    /** The most pieces the world can hold at rest. */
    static final int MAX_FRAGMENTS = BodyFragmentConstants.MAX_SETTLED_FRAGMENTS;
    /** A unit quaternion's components lie in [-1, 1]; the rest is headroom for float drift. */
    private static final float MAX_QUATERNION_COMPONENT = 2f;
    /** Below this squared length a quaternion has no direction worth normalising. */
    private static final float MIN_QUATERNION_LENGTH_SQUARED = 1e-6f;

    private FragmentsSection() {
    }

    static byte[] write(Game game) throws IOException {
        List<BodyFragment> settled = game.fragments.settled;
        if (settled.size() > MAX_FRAGMENTS) {
            throw new IOException("too many settled body fragments: " + settled.size());
        }
        List<BodyFragment> kept = new ArrayList<>(settled.size());
        for (BodyFragment f : settled) {
            if (readable(f)) {
                kept.add(f);
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(VERSION);
            out.writeInt(kept.size());
            for (BodyFragment f : kept) {
                out.writeInt(f.piece.ordinal());
                out.writeFloat(f.pos.x);
                out.writeFloat(f.pos.y);
                out.writeFloat(f.pos.z);
                out.writeFloat(f.orientation.x);
                out.writeFloat(f.orientation.y);
                out.writeFloat(f.orientation.z);
                out.writeFloat(f.orientation.w);
                out.writeFloat(f.decay);
                writeAppearance(out, f.appearance);
            }
        }
        return bytes.toByteArray();
    }

    static void read(byte[] payload, Game game) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            int version = in.readInt();
            if (version < 1 || version > VERSION) {
                throw new IOException("unsupported fragments section version " + version);
            }
            int count = readCount(in, "body fragments");
            if (count > MAX_FRAGMENTS) {
                throw new IOException("too many body fragments: " + count);
            }
            BodyFragment.Piece[] pieces = BodyFragment.Piece.values();
            for (int i = 0; i < count; i++) {
                int piece = in.readInt();
                if (piece < 0 || piece >= pieces.length) {
                    throw new IOException("invalid body fragment piece ordinal: " + piece);
                }
                BodyFragment f = new BodyFragment(pieces[piece]);
                float x = readFinite(in, "fragment x", -MAX_HORIZONTAL, MAX_HORIZONTAL);
                float y = readFinite(in, "fragment y", -MAX_VERTICAL, MAX_VERTICAL);
                float z = readFinite(in, "fragment z", -MAX_HORIZONTAL, MAX_HORIZONTAL);
                f.pos.set(x, y, z);
                float qx = readQuaternion(in, "x");
                float qy = readQuaternion(in, "y");
                float qz = readQuaternion(in, "z");
                float qw = readQuaternion(in, "w");
                if (qx * qx + qy * qy + qz * qz + qw * qw < MIN_QUATERNION_LENGTH_SQUARED) {
                    throw new IOException("zero-length fragment orientation");
                }
                f.orientation.set(qx, qy, qz, qw).normalize();
                f.decay = readFinite(in, "fragment decay", 0f, RagdollConstants.CORPSE_DECAY);
                readAppearance(in, f.appearance, "fragment");
                game.fragments.restoreSettled(f);
            }
            if (in.available() != 0) {
                throw new IOException("unexpected bytes after fragments section");
            }
        }
    }

    private static float readQuaternion(DataInputStream in, String axis) throws IOException {
        return readFinite(in, "fragment orientation " + axis,
                -MAX_QUATERNION_COMPONENT, MAX_QUATERNION_COMPONENT);
    }

    /** Whether {@link #read} would accept this piece's record. */
    private static boolean readable(BodyFragment f) {
        float qx = f.orientation.x, qy = f.orientation.y, qz = f.orientation.z;
        float qw = f.orientation.w;
        return inRange(f.pos.x, MAX_HORIZONTAL) && inRange(f.pos.y, MAX_VERTICAL)
                && inRange(f.pos.z, MAX_HORIZONTAL)
                && inRange(qx, MAX_QUATERNION_COMPONENT) && inRange(qy, MAX_QUATERNION_COMPONENT)
                && inRange(qz, MAX_QUATERNION_COMPONENT) && inRange(qw, MAX_QUATERNION_COMPONENT)
                && qx * qx + qy * qy + qz * qz + qw * qw >= MIN_QUATERNION_LENGTH_SQUARED
                && Float.isFinite(f.decay) && f.decay >= 0f
                && f.decay <= RagdollConstants.CORPSE_DECAY;
    }

    private static boolean inRange(float value, float max) {
        return Float.isFinite(value) && value >= -max && value <= max;
    }
}
