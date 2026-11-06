package com.veylon;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and rebuilds the stable-id section envelope at the end of a real v3 save, for the package-25
 * audit's ordering, absence and corruption cases. The envelope is found as the one occurrence of
 * its magic from which the whole rest of the file parses as sections, so bytes of the core that
 * happen to spell the magic cannot be mistaken for it.
 */
public final class SaveEnvelope {
    private static final int MAGIC = 0x53334543; // "S3EC"

    private SaveEnvelope() {
    }

    public record Section(String id, byte[] payload) {
        public Section {
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }

    /** The envelope's offset in the file and its sections, in file order. */
    public record Parsed(int offset, List<Section> sections) {
        public Parsed {
            sections = List.copyOf(sections);
        }

        public List<String> ids() {
            return sections.stream().map(Section::id).toList();
        }

        public Section section(String id) {
            return sections.stream().filter(s -> s.id().equals(id)).findFirst()
                    .orElseThrow(() -> new AssertionError("the save has no section " + id));
        }
    }

    public static Parsed parse(byte[] save) {
        Parsed found = null;
        for (int i = 0; i <= save.length - 8; i++) {
            if ((save[i] & 255) != 0x53 || (save[i + 1] & 255) != 0x33 || (save[i + 2] & 255) != 0x45
                    || (save[i + 3] & 255) != 0x43) {
                continue;
            }
            List<Section> sections = sectionsAt(save, i);
            if (sections != null) {
                if (found != null) { throw new AssertionError("two section envelopes parse to the end of the save"); }
                found = new Parsed(i, sections);
            }
        }
        if (found == null) { throw new AssertionError("the save has no section envelope"); }
        return found;
    }

    /** The save with its envelope replaced by these sections, in this order. */
    public static byte[] rebuild(byte[] save, Parsed parsed, List<Section> sections) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.write(save, 0, parsed.offset());
            out.writeInt(MAGIC);
            out.writeInt(sections.size());
            for (Section s : sections) {
                out.writeUTF(s.id());
                out.writeInt(s.payload().length);
                out.write(s.payload());
            }
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
        return bytes.toByteArray();
    }

    /** The save with one section's payload replaced, or the section removed when {@code payload} is null. */
    public static byte[] replace(byte[] save, String id, byte[] payload) {
        Parsed parsed = parse(save);
        List<Section> out = new ArrayList<>();
        for (Section s : parsed.sections()) {
            if (!s.id().equals(id)) { out.add(s); }
            else if (payload != null) { out.add(new Section(id, payload)); }
        }
        if (out.size() == parsed.sections().size() && payload == null) {
            throw new AssertionError("the save has no section " + id);
        }
        return rebuild(save, parsed, out);
    }

    private static List<Section> sectionsAt(byte[] save, int offset) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(save, offset, save.length - offset))) {
            if (in.readInt() != MAGIC) { return null; }
            int count = in.readInt();
            if (count < 0 || count > 256) { return null; }
            List<Section> sections = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String id = in.readUTF();
                int length = in.readInt();
                if (length < 0 || length > in.available()) { return null; }
                sections.add(new Section(id, in.readNBytes(length)));
            }
            return in.available() == 0 ? sections : null;
        } catch (IOException | RuntimeException notAnEnvelope) {
            return null;
        }
    }
}
