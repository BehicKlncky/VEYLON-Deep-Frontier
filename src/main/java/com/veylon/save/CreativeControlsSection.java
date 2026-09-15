package com.veylon.save;

import com.veylon.Game;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Optional v3 state: the Creative world controls (R25). An absent section means
 * every control is off, which is what a Survival world and every save written
 * before 0.7.0 mean.
 *
 * <p>Written after {@link GameModeSection} so a load restores the mode first;
 * the mode's restore releases the controls again if the world is Survival.
 */
final class CreativeControlsSection {
    static final String ID = "world.creative-controls";
    private static final int VERSION = 1;

    private CreativeControlsSection() {
    }

    static byte[] write(Game game) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(VERSION);
            out.writeBoolean(game.daylightFrozen());
            out.writeBoolean(game.weatherLocked());
            out.writeBoolean(game.spawningPaused());
        }
        return bytes.toByteArray();
    }

    static void read(byte[] payload, Game game) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            int version = in.readInt();
            if (version != VERSION) {
                throw new IOException("unsupported creative-controls section version " + version);
            }
            boolean frozen = in.readBoolean();
            boolean locked = in.readBoolean();
            boolean spawningPaused = in.readBoolean();
            if (in.available() != 0) {
                throw new IOException("unexpected bytes after creative-controls section");
            }
            game.restoreCreativeControls(frozen, locked, spawningPaused);
        }
    }
}
