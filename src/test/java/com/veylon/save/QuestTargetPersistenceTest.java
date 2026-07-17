package com.veylon.save;

import com.veylon.Game;
import com.veylon.ai.Quest;
import com.veylon.entity.Npc;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestTargetPersistenceTest {

    @Test
    void targetedQuestIdentityAndProviderRoundTripInOptionalV3Section(@TempDir Path dir) {
        Game game = targetedRescueGame();
        Quest before = game.faction.quest;
        Path save = dir.resolve("targeted-quest.sav");
        assertTrue(SaveSystem.save(game, save));

        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save));
        Quest after = loaded.faction.quest;
        assertNotNull(after);
        assertEquals(before.instanceId, after.instanceId);
        assertEquals(before.type, after.type);
        assertEquals(Quest.Status.ACTIVE, after.status);
        assertEquals(before.giverId, after.giverId);
        assertEquals(before.giverSettlementId, after.giverSettlementId);
        assertEquals(before.rewardProviderId, after.rewardProviderId);
        assertEquals(before.targetSettlementId, after.targetSettlementId);
        assertEquals(before.targetFactionId, after.targetFactionId);
        assertEquals(before.targetCaptiveId, after.targetCaptiveId);
        assertEquals(before.targetPoiId, after.targetPoiId);
        assertEquals(before.destinationId, after.destinationId);
        assertTrue(loaded.faction.questSequence() >= game.faction.questSequence());
    }

    @Test
    void corruptQuestTargetSettlementFailsSafelyInsteadOfRebinding(@TempDir Path dir)
            throws Exception {
        Game game = targetedRescueGame();
        Path save = dir.resolve("corrupt-target.sav");
        assertTrue(SaveSystem.save(game, save));
        byte[] bytes = Files.readAllBytes(save);
        int targetOffset = questTargetSettlementOffset(bytes);
        ByteBuffer.wrap(bytes, targetOffset, Long.BYTES).putLong(Long.MAX_VALUE - 7);
        Files.write(save, bytes);

        Game loaded = new Game();
        assertFalse(SaveSystem.load(loaded, save),
                "an unknown stable target must fail without selecting another settlement");
    }

    private static Game targetedRescueGame() {
        Game game = new Game();
        game.newWorld(20260716L, true);
        Settlement providerHome = null;
        boolean captiveExists = false;
        for (int rx = -10; rx <= 10; rx++) {
            for (int rz = -10; rz <= 10; rz++) {
                Settlement settlement = game.world.settlementForRegion(rx, rz);
                if (settlement == null) {
                    continue;
                }
                if (!settlement.hostile() && providerHome == null) {
                    providerHome = settlement;
                }
                captiveExists |= settlement.hostile() && settlement.residents.stream()
                        .anyMatch(r -> r.archetype == NpcArchetype.CAPTIVE
                                && r.alive && !r.rescued);
            }
        }
        assertNotNull(providerHome);
        assertTrue(captiveExists);
        Npc provider = new Npc(game.world, "Regional rescue coordinator");
        provider.archetype = NpcArchetype.TRADER;
        provider.isTrader = true;
        provider.settlementId = providerHome.id;
        provider.residentIndex = 0;
        game.faction.makeQuestOfferAvailable();
        Quest quest = game.faction.offerSettlementQuestOfType(game, provider, providerHome,
                Quest.Type.RESCUE_CAPTIVE);
        assertNotNull(quest);
        assertFalse(quest.targetCaptiveId.isBlank());
        return game;
    }

    private static int questTargetSettlementOffset(byte[] save) throws Exception {
        byte[] id = "quest.target-state".getBytes(StandardCharsets.UTF_8);
        int idStart = indexOf(save, id);
        if (idStart < 2) {
            throw new AssertionError("quest target section not found");
        }
        int payloadStart = idStart + id.length + Integer.BYTES;
        int payloadLength = ByteBuffer.wrap(save, idStart + id.length, Integer.BYTES).getInt();
        ByteArrayInputStream raw = new ByteArrayInputStream(save, payloadStart, payloadLength);
        try (DataInputStream in = new DataInputStream(raw)) {
            in.readInt(); // section version
            in.readLong(); // sequence
            assertTrue(in.readBoolean());
            in.readUTF(); // instance
            in.readUTF(); // status
            in.readUTF(); // giver id
            in.readLong(); // giver settlement
            in.readUTF(); // provider
            return payloadStart + payloadLength - raw.available();
        }
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
