package com.veylon.save;

import com.veylon.Game;
import com.veylon.entity.BodyPose;
import com.veylon.entity.BodySkeleton;
import com.veylon.entity.Carcass;
import com.veylon.entity.HumanCorpse;
import com.veylon.entity.NpcAppearance;
import com.veylon.settlement.NpcArchetype;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static com.veylon.save.SaveSystem.readCount;

/**
 * Optional v3 state: how every body in the world is lying.
 *
 * <p>Two things go in here. First, one {@link BodyPose} per {@link Carcass},
 * positional and in the order the v3 body wrote them — the same index-aligned
 * side-car the party-state section uses, and the reason a count mismatch is a
 * hard failure rather than something to patch up. Second, the human corpses,
 * which have no representation in the base v3 layout at all.
 *
 * <p>An absent section is exactly what every save written before this feature
 * means: carcasses keep the fixed keeled-over pose their constructor assigns
 * and the world holds no human corpses. Nothing here changes the frozen v3
 * body, so the byte walk in {@code CorruptSaveResilienceTest} is untouched.
 *
 * <p><b>In-flight ragdolls are deliberately not persisted.</b> Like bow draw
 * and reload progress they are transient. A save settles every falling body
 * first, so a save taken mid-fall produces a settled corpse rather than losing
 * one; loading clears the list through the {@code newWorld} reset path.
 *
 * <p>The record is entirely numeric. A stray UTF field here would move the
 * byte anchors the migration tests locate fixtures with, so the archetype
 * travels as a bounds-checked ordinal instead of its stable string id.
 */
final class BodiesSection {

    static final String ID = "world.bodies";
    private static final int VERSION = 2;
    /** Far above the corpse despawn radius could ever sustain. */
    private static final int MAX_CORPSES = 4096;
    private static final float MAX_ANGLE = 40f;
    /** Position bounds; {@link FragmentsSection} holds its pieces to the same ones. */
    static final float MAX_HORIZONTAL = 100_000_000f;
    static final float MAX_VERTICAL = 10_000f;

    private BodiesSection() {
    }

    static byte[] write(Game game) throws IOException {
        if (game.entities.corpses.size() > MAX_CORPSES) {
            throw new IOException("too many human corpses: " + game.entities.corpses.size());
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(VERSION);
            out.writeInt(game.entities.carcasses.size());
            for (Carcass carcass : game.entities.carcasses) {
                writePose(out, carcass.pose);
            }
            out.writeInt(game.entities.corpses.size());
            for (HumanCorpse corpse : game.entities.corpses) {
                out.writeFloat(corpse.pos.x);
                out.writeFloat(corpse.pos.y);
                out.writeFloat(corpse.pos.z);
                out.writeFloat(corpse.decay);
                writeAppearance(out, corpse.appearance);
                writePose(out, corpse.pose);
            }
        }
        return bytes.toByteArray();
    }

    static void read(byte[] payload, Game game) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            int version = in.readInt();
            if (version < 1 || version > VERSION) {
                throw new IOException("unsupported bodies section version " + version);
            }
            int poses = readCount(in, "carcass poses");
            if (poses != game.entities.carcasses.size()) {
                throw new IOException("carcass pose count mismatch: " + poses
                        + " for " + game.entities.carcasses.size() + " carcasses");
            }
            for (Carcass carcass : game.entities.carcasses) {
                readPose(in, carcass.pose, version, BodySkeleton.of(carcass.type));
            }

            int count = readCount(in, "human corpses");
            if (count > MAX_CORPSES) {
                throw new IOException("too many human corpses: " + count);
            }
            game.entities.corpses.clear();
            for (int i = 0; i < count; i++) {
                HumanCorpse corpse = new HumanCorpse(
                        readFinite(in, "corpse x", -MAX_HORIZONTAL, MAX_HORIZONTAL),
                        readFinite(in, "corpse y", -MAX_VERTICAL, MAX_VERTICAL),
                        readFinite(in, "corpse z", -MAX_HORIZONTAL, MAX_HORIZONTAL));
                corpse.decay = readFinite(in, "corpse decay", 0f, MAX_VERTICAL);
                readAppearance(in, corpse.appearance, "corpse");
                readPose(in, corpse.pose, version, BodySkeleton.humanoid());
                game.entities.corpses.add(corpse);
            }
            if (in.available() != 0) {
                throw new IOException("unexpected bytes after bodies section");
            }
        }
    }

    /**
     * The look of the person a body came from. Shared with
     * {@link FragmentsSection}, so a piece of a person is recorded exactly as
     * the whole corpse is.
     */
    static void writeAppearance(DataOutputStream out, NpcAppearance appearance)
            throws IOException {
        // Archetype by ordinal + 1, so zero stays available for "none".
        out.writeInt(appearance.archetype == null ? 0 : appearance.archetype.ordinal() + 1);
        out.writeBoolean(appearance.raider);
        out.writeBoolean(appearance.trader);
        out.writeBoolean(appearance.sick);
        out.writeInt(appearance.campIndex);
    }

    static void readAppearance(DataInputStream in, NpcAppearance appearance, String owner)
            throws IOException {
        NpcArchetype[] archetypes = NpcArchetype.values();
        int archetype = in.readInt();
        if (archetype < 0 || archetype > archetypes.length) {
            throw new IOException("invalid " + owner + " archetype ordinal: " + archetype);
        }
        appearance.archetype = archetype == 0 ? null : archetypes[archetype - 1];
        appearance.raider = in.readBoolean();
        appearance.trader = in.readBoolean();
        appearance.sick = in.readBoolean();
        appearance.campIndex = in.readInt();
    }

    private static void writePose(DataOutputStream out, BodyPose pose) throws IOException {
        out.writeFloat(pose.yaw);
        out.writeFloat(pose.pitch);
        out.writeFloat(pose.roll);
        out.writeFloat(pose.lift);
        out.writeFloat(pose.pivotY);
        out.writeBoolean(pose.solved);
        out.writeInt(pose.boneCount);
        for (int b = 0; b < pose.boneCount; b++) {
            out.writeFloat(pose.boneRotX[b]);
            out.writeFloat(pose.boneRotY[b]);
            out.writeFloat(pose.boneRotZ[b]);
        }
    }

    private static void readPose(DataInputStream in, BodyPose pose, int version,
                                 BodySkeleton skeleton) throws IOException {
        pose.yaw = readFinite(in, "pose yaw", -MAX_ANGLE, MAX_ANGLE);
        pose.pitch = readFinite(in, "pose pitch", -MAX_ANGLE, MAX_ANGLE);
        pose.roll = readFinite(in, "pose roll", -MAX_ANGLE, MAX_ANGLE);
        pose.lift = readFinite(in, "pose lift", -4f, 4f);
        pose.pivotY = readFinite(in, "pose pivot", -4f, 4f);
        pose.solved = in.readBoolean();
        int bones = in.readInt();
        if (bones < 0 || bones > (version == 1 ? 6 : skeleton.boneCount)) {
            throw new IOException("invalid pose bone count: " + bones);
        }
        pose.boneCount = bones;
        for (int b = 0; b < bones; b++) {
            int target = version == 1 ? legacyBone(skeleton, b) : b;
            float x = readFinite(in, "bone rotX", -MAX_ANGLE, MAX_ANGLE);
            float y = version == 1 ? 0 : readFinite(in, "bone rotY", -MAX_ANGLE, MAX_ANGLE);
            float z = readFinite(in, "bone rotZ", -MAX_ANGLE, MAX_ANGLE);
            if (target >= 0) {
                pose.boneRotX[target] = x;
                pose.boneRotY[target] = y;
                pose.boneRotZ[target] = z;
            }
        }
        if (version == 1 && pose.solved) pose.boneCount = skeleton.boneCount;
    }

    /** Version 1 stored flat bones in a different order, never child rotations. */
    private static int legacyBone(BodySkeleton skeleton, int old) {
        String name;
        if (skeleton == BodySkeleton.humanoid()) {
            name = switch (old) {
                case 0 -> "head"; case 1 -> "arm_l"; case 2 -> "arm_r";
                case 3 -> "leg_l"; case 4 -> "leg_r"; default -> "";
            };
        } else if (skeleton == BodySkeleton.of(com.veylon.entity.Creature.CreatureType.BIRD)) {
            name = old == 0 ? "head" : old == 1 ? "tail" : "";
        } else {
            name = switch (old) {
                case 0 -> "leg_fl"; case 1 -> "leg_fr"; case 2 -> "leg_bl"; case 3 -> "leg_br";
                case 4 -> skeleton == BodySkeleton.of(com.veylon.entity.Creature.CreatureType.HARE)
                        ? "head" : "neck";
                case 5 -> "tail"; default -> "";
            };
        }
        for (int b = 0; b < skeleton.boneCount; b++) {
            if (skeleton.part[b].equals(name)) return b;
        }
        return -1;
    }

    /**
     * A NaN here would load, and then no comparison against it would ever be
     * true again — the same failure the player-scalar guard exists for. The
     * bounded equivalents in SaveSystem and V3ExtensionSections are private;
     * {@link FragmentsSection} reads through this one.
     */
    static float readFinite(DataInputStream in, String label, float min, float max)
            throws IOException {
        float value = in.readFloat();
        if (!Float.isFinite(value) || value < min || value > max) {
            throw new IOException("invalid " + label + ": " + value);
        }
        return value;
    }
}
