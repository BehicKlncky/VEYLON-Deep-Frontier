package com.veylon.save;

import com.veylon.Game;
import com.veylon.entity.GameMode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Optional v3 state: older saves default to Survival through world reset. */
final class GameModeSection {
    static final String ID = "player.game-mode";
    private static final int VERSION = 1;

    private GameModeSection() {
    }

    static byte[] write(Game game) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(VERSION);
            out.writeUTF(game.gameMode().id);
            out.writeBoolean(game.creativeMarked());
            out.writeBoolean(game.player.abilities.flying());
        }
        return bytes.toByteArray();
    }

    static void read(byte[] payload, Game game) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            int version = in.readInt();
            if (version != VERSION) throw new IOException("unsupported game-mode section version " + version);
            GameMode mode;
            try {
                mode = GameMode.fromId(in.readUTF());
            } catch (IllegalArgumentException invalid) {
                throw new IOException("invalid game-mode section ID", invalid);
            }
            boolean marked = in.readBoolean();
            boolean flying = in.readBoolean();
            if (in.available() != 0) throw new IOException("unexpected bytes after game-mode section");
            game.restoreGameMode(mode, marked, flying);
        }
    }
}
