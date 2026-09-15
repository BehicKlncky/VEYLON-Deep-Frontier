package com.veylon.save;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Edits real saved section envelopes for absence/corruption compatibility tests. */
final class CreativeSaveSections {
    private static final int ENVELOPE_MAGIC = 0x53334543;

    private CreativeSaveSections() {
    }

    private record Section(String id, byte[] payload) {
    }

    static byte[] replace(byte[] save, String id, byte[] replacement) throws IOException {
        int envelope = -1;
        for (int i = 0; i <= save.length - 8; i++) {
            if ((save[i] & 255) == 0x53 && (save[i + 1] & 255) == 0x33
                    && (save[i + 2] & 255) == 0x45 && (save[i + 3] & 255) == 0x43) {
                envelope = i;
                break;
            }
        }
        if (envelope < 0) throw new IOException("fixture has no v3 section envelope");
        List<Section> sections = new ArrayList<>();
        boolean found = false;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(
                save, envelope, save.length - envelope))) {
            if (in.readInt() != ENVELOPE_MAGIC) throw new IOException("invalid fixture envelope");
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                String sectionId = in.readUTF();
                int length = in.readInt();
                byte[] payload = in.readNBytes(length);
                if (payload.length != length) throw new IOException("truncated fixture");
                if (sectionId.equals(id)) {
                    found = true;
                    if (replacement != null) sections.add(new Section(id, replacement));
                } else sections.add(new Section(sectionId, payload));
            }
            if (in.available() != 0) throw new IOException("unexpected fixture tail");
        }
        if (!found) throw new IOException("fixture missing section " + id);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.write(save, 0, envelope);
            out.writeInt(ENVELOPE_MAGIC);
            out.writeInt(sections.size());
            for (Section section : sections) {
                out.writeUTF(section.id);
                out.writeInt(section.payload.length);
                out.write(section.payload);
            }
        }
        return bytes.toByteArray();
    }
}
