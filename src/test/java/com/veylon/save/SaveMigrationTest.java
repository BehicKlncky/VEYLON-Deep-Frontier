package com.veylon.save;

import com.veylon.Game;
import com.veylon.item.ItemType;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Save compatibility: a byte-exact v2 fixture must load and migrate to valid
 * v3 runtime state (legacy terrain, no settlements), and v3 must round-trip
 * settlements, capture state, residents and faction reputation.
 */
class SaveMigrationTest {

    private static final long SEED = 1122334455L;

    /** Writes a minimal but complete v2 save exactly as the 0.2.0 format did. */
    private static void writeV2Fixture(Path path, long seed) throws IOException {
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(path)))) {
            out.writeInt(0x5645594C);       // magic "VEYL"
            out.writeInt(2);                // version
            out.writeLong(seed);
            out.writeDouble(9 * 60);        // time
            out.writeInt(0);                // weather current CLEAR
            out.writeInt(0);                // weather next
            out.writeFloat(1f);             // blend
            out.writeFloat(100f);           // changeTimer
            // Player.
            out.writeFloat(20.5f);          // pos x
            out.writeFloat(40f);            // pos y
            out.writeFloat(21.5f);          // pos z
            out.writeFloat(35f);            // yaw
            out.writeFloat(8f);             // pitch
            out.writeFloat(77f);            // health
            out.writeFloat(80f);            // hunger
            out.writeFloat(70f);            // thirst
            out.writeFloat(90f);            // stamina
            out.writeFloat(37f);            // bodyTemp
            out.writeFloat(10f);            // fatigue
            out.writeFloat(0f);             // wetness
            out.writeFloat(60f);            // protein
            out.writeFloat(60f);            // vitamins
            out.writeFloat(0f);             // smokeExposure
            out.writeBoolean(false);        // woundClean
            out.writeInt(2);                // hotbarSel
            out.writeInt(0);                // afflictions
            out.writeInt(0);                // blueprints
            // Inventory: one stack + one empty slot (v2 stack = 2 ints + 2 floats).
            out.writeInt(2);
            out.writeInt(ItemType.SPEAR.ordinal());
            out.writeInt(1);
            out.writeFloat(55f);
            out.writeFloat(-1f);
            out.writeInt(-1);
            out.writeInt(0);
            out.writeFloat(0f);
            out.writeFloat(0f);
            // Equipment: 5 empty slots.
            for (int i = 0; i < 5; i++) {
                out.writeInt(-1);
                out.writeInt(0);
                out.writeFloat(0f);
                out.writeFloat(0f);
            }
            out.writeInt(0);                // changed blocks
            out.writeInt(0);                // campfire fuel
            out.writeInt(0);                // crates
            out.writeInt(0);                // racks
            out.writeInt(0);                // collectors
            out.writeInt(0);                // discovered POIs
            out.writeBoolean(false);        // beacon pos
            out.writeInt(-1);               // beacon stage
            out.writeFloat(41f);            // trust
            out.writeFloat(0f);             // alert
            out.writeInt(9);                // food stock
            out.writeInt(7);                // wood stock
            out.writeBoolean(false);        // hostile
            out.writeInt(1);                // upgrade stage
            out.writeBoolean(false);        // allied gift
            out.writeBoolean(false);        // quest
            // One camp NPC (v2 layout: no archetype/settlement fields).
            out.writeInt(1);
            out.writeUTF("Maro");
            out.writeFloat(30f);
            out.writeFloat(40f);
            out.writeFloat(30f);
            out.writeFloat(0f);             // yaw
            out.writeFloat(35f);            // health
            out.writeFloat(20f);            // hunger
            out.writeFloat(65f);            // mood
            out.writeBoolean(false);        // trader
            out.writeBoolean(false);        // raider
            out.writeBoolean(false);        // sick
            out.writeFloat(0f);             // sickTimer
            out.writeInt(0);                // campIndex
            out.writeFloat(0f);             // leaveTimer
            out.writeBoolean(true);         // has faction
            out.writeInt(0);                // creatures
            out.writeInt(0);                // carcasses
            out.writeInt(0);                // events
            out.writeInt(0);                // log
        }
    }

    @Test
    void v2SaveLoadsAndMigratesToLegacyGenerator(@TempDir Path dir) throws IOException {
        Path save = dir.resolve("v2.sav");
        writeV2Fixture(save, SEED);

        Game g = new Game();
        assertTrue(SaveSystem.load(g, save), "v2 save must not be rejected");
        assertEquals(World.GEN_LEGACY, g.world.generatorVersion,
                "v2 worlds keep legacy terrain generation");
        assertTrue(g.world.settlements.isEmpty(), "no settlements planned in legacy worlds");
        assertEquals(77f, g.player.health, 0.01f);
        assertEquals(2, g.player.hotbarSel);
        assertEquals(41f, g.faction.trust, 0.01f);
        assertEquals(1, g.entities.npcs.size());
        assertEquals("Maro", g.entities.npcs.getFirst().name);
        assertEquals(1, g.player.inventory.count(ItemType.SPEAR));
        // New v3 fields defaulted safely.
        assertFalse(g.entities.npcs.getFirst().settled());
        assertTrue(g.world.kegFuses.isEmpty());
        assertTrue(g.world.factionReputation.isEmpty());
    }

    @Test
    void v3RoundTripPreservesSettlementsFactionsAndGenerator(@TempDir Path dir) {
        Path save = dir.resolve("v3.sav");
        Game g = new Game();
        g.newWorld(SEED, true);
        assertEquals(World.CURRENT_GENERATOR, g.world.generatorVersion);

        // Force-plan a hostile settlement and mutate capture/resident state.
        Settlement hostile = null;
        outer:
        for (int rx = -6; rx <= 6; rx++) {
            for (int rz = -6; rz <= 6; rz++) {
                Settlement s = g.world.settlementForRegion(rx, rz);
                if (s != null && s.alignment == Settlement.Alignment.HOSTILE) {
                    hostile = s;
                    break outer;
                }
            }
        }
        assertNotNull(hostile, "expected a hostile settlement within 13x13 regions");
        hostile.cleared = true;
        hostile.occupied = true;
        hostile.factionId = com.veylon.settlement.HumanFaction.FRONTIER;
        hostile.foodStock = 123;
        hostile.alertLevel = 55f;
        hostile.discovered = true;
        hostile.commandNeutralized = true;
        hostile.alarmNeutralized = true;
        hostile.centralObjectiveControlled = true;
        hostile.rumored = true;
        hostile.dormantStep = 17;
        hostile.dormantAccumulator = 22.5f;
        if (!hostile.residents.isEmpty()) {
            hostile.residents.getFirst().alive = false;
            hostile.residents.getFirst().routed = true;
            hostile.residents.getFirst().sick = true;
            hostile.residents.getFirst().sicknessTimer = 720f;
            hostile.residents.getFirst().hunger = 88f;
        }
        g.world.factionReputation.put(com.veylon.settlement.HumanFaction.HEADHUNTERS, -60f);
        g.world.factionBounty.put(com.veylon.settlement.HumanFaction.HEADHUNTERS, 35f);
        g.world.kegFuses.put(new com.veylon.util.Vec3i(10, 40, 10), 3.5f);

        // A settlement NPC with residency and a loaded musket.
        var npc = g.entities.spawnNpc(g.world, "Ashfang", 100, 40, 100);
        npc.archetype = NpcArchetype.POWDERMAN;
        npc.settlementId = hostile.id;
        npc.residentIndex = 0;
        npc.loadedAmmo = 1;
        npc.warParty = true;
        npc.partyFactionId = com.veylon.settlement.HumanFaction.HEADHUNTERS;
        npc.originSettlementId = hostile.id;
        npc.partyMission = com.veylon.entity.Npc.PartyMission.RETURNING;
        npc.partyDestination.set(91.5f, 42f, -13.5f);
        npc.partyMissionTimer = 44f;
        npc.partyContact = true;

        Vec3i openGate = new Vec3i(12, 44, 12);
        g.world.setBlock(openGate.x(), openGate.y(), openGate.z(), BlockType.GATE_OPEN, true);
        g.world.gateTimers.put(openGate, 2.75f);

        long hostileId = hostile.id;
        int residentCount = hostile.residents.size();
        assertTrue(SaveSystem.save(g, save));

        Game g2 = new Game();
        assertTrue(SaveSystem.load(g2, save));
        assertEquals(World.CURRENT_GENERATOR, g2.world.generatorVersion,
                "generator version survives");
        Settlement restored = g2.world.settlements.get(hostileId);
        assertNotNull(restored, "settlement must be re-registered on load");
        assertTrue(restored.cleared, "cleared state survives reload");
        assertTrue(restored.occupied, "occupied state survives reload");
        assertEquals(com.veylon.settlement.HumanFaction.FRONTIER, restored.factionId);
        assertEquals(123, restored.foodStock);
        assertEquals(55f, restored.alertLevel, 0.01f);
        assertTrue(restored.discovered);
        assertEquals(residentCount, restored.residents.size(), "residents survive reload");
        assertFalse(restored.residents.getFirst().alive, "dead resident stays dead");
        assertTrue(restored.residents.getFirst().routed, "rout state survives reload");
        assertTrue(restored.residents.getFirst().sick, "dormant sickness survives reload");
        assertEquals(720f, restored.residents.getFirst().sicknessTimer, 0.01f);
        assertEquals(88f, restored.residents.getFirst().hunger, 0.01f);
        assertTrue(restored.commandNeutralized);
        assertTrue(restored.alarmNeutralized);
        assertTrue(restored.centralObjectiveControlled);
        assertTrue(restored.rumored);
        assertEquals(17, restored.dormantStep);
        assertEquals(22.5f, restored.dormantAccumulator, 0.01f);
        assertEquals(-60f, g2.world.factionReputation.get(
                com.veylon.settlement.HumanFaction.HEADHUNTERS), 0.01f);
        assertEquals(35f, g2.world.factionBounty.get(
                com.veylon.settlement.HumanFaction.HEADHUNTERS), 0.01f);
        assertEquals(3.5f, g2.world.kegFuses.get(new com.veylon.util.Vec3i(10, 40, 10)), 0.01f);

        // The settlement NPC kept home, archetype and loaded weapon.
        var restoredNpc = g2.entities.npcs.stream()
                .filter(n -> "Ashfang".equals(n.name)).findFirst().orElseThrow();
        assertEquals(NpcArchetype.POWDERMAN, restoredNpc.archetype);
        assertEquals(hostileId, restoredNpc.settlementId);
        assertEquals(1, restoredNpc.loadedAmmo);
        assertEquals(com.veylon.settlement.HumanFaction.HEADHUNTERS,
                restoredNpc.partyFactionId);
        assertEquals(hostileId, restoredNpc.originSettlementId);
        assertEquals(com.veylon.entity.Npc.PartyMission.RETURNING, restoredNpc.partyMission);
        assertEquals(91.5f, restoredNpc.partyDestination.x, 0.01f);
        assertEquals(44f, restoredNpc.partyMissionTimer, 0.01f);
        assertTrue(restoredNpc.partyContact);
        assertEquals(2.75f, g2.world.gateTimers.get(openGate), 0.01f);
        assertEquals(BlockType.GATE_OPEN,
                g2.world.getBlock(openGate.x(), openGate.y(), openGate.z()));
    }

    @Test
    void preExtensionV3StillLoadsWithSafeDefaults(@TempDir Path dir) throws IOException {
        Path save = dir.resolve("old-v3.sav");
        Game original = new Game();
        original.newWorld(SEED, true, World.GEN_DEEP);
        Vec3i oldFuse = new Vec3i(40, 44, 40);
        original.world.getOrCreateChunk(
                Math.floorDiv(oldFuse.x(), 16), Math.floorDiv(oldFuse.z(), 16));
        original.world.setBlock(oldFuse.x(), oldFuse.y(), oldFuse.z(),
                BlockType.POWDER_KEG, true);
        assertTrue(original.explosions.tryArmKeg(original, oldFuse, 3.5f, true));
        assertTrue(SaveSystem.save(original, save));

        byte[] bytes = Files.readAllBytes(save);
        int extension = lastIndexOf(bytes, new byte[]{0x57, 0x33, 0x45, 0x58});
        assertTrue(extension > 0, "test fixture contains the optional v3 extension");
        Files.write(save, Arrays.copyOf(bytes, extension));

        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save), "an original v3 EOF remains valid");
        assertEquals(World.GEN_DEEP, loaded.world.generatorVersion,
                "pre-identity v3 worlds retain generator id 2");
        assertTrue(loaded.world.gateTimers.isEmpty());
        assertTrue(loaded.world.settlements.values().stream().noneMatch(s -> s.rumored));
        assertEquals(3.5f, loaded.world.kegFuses.get(oldFuse), 0.001f);
        assertFalse(loaded.world.kegFusePlayerAttribution
                        .getOrDefault(oldFuse, false),
                "an old v3 timer with no attribution tail defaults safely to non-player");

        World expectedGeneratorTwo = new World(SEED, World.GEN_DEEP);
        Chunk expected = expectedGeneratorTwo.getOrCreateChunk(-12, -9);
        Chunk actual = loaded.world.getOrCreateChunk(-12, -9);
        for (int lx = 0; lx < Chunk.SX; lx++) {
            for (int lz = 0; lz < Chunk.SZ; lz++) {
                for (int y = 0; y < Chunk.SY; y++) {
                    assertEquals(expected.get(lx, y, lz), actual.get(lx, y, lz),
                            "old v3 unexplored terrain changed at "
                                    + lx + "," + y + "," + lz);
                }
            }
        }
    }

    @Test
    void corruptStableArchetypeIdFailsInsteadOfBecomingVillager(@TempDir Path dir)
            throws IOException {
        Path save = dir.resolve("corrupt-id.sav");
        Game original = new Game();
        original.newWorld(SEED, true);
        var npc = original.entities.spawnNpc(original.world, "CorruptMe", 8, 40, 8);
        npc.archetype = NpcArchetype.POWDERMAN;
        assertTrue(SaveSystem.save(original, save));

        byte[] bytes = Files.readAllBytes(save);
        byte[] valid = "powderman".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int at = lastIndexOf(bytes, valid);
        assertTrue(at > 0);
        byte[] invalid = "bad-id-xx".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        System.arraycopy(invalid, 0, bytes, at, invalid.length);
        Files.write(save, bytes);

        Game loaded = new Game();
        assertFalse(SaveSystem.load(loaded, save));
        assertTrue(loaded.entities.npcs.stream().noneMatch(n -> "CorruptMe".equals(n.name)
                && n.archetype == NpcArchetype.VILLAGER));
    }

    @Test
    void corruptStableFactionIdFailsInsteadOfChangingOwnership(@TempDir Path dir)
            throws IOException {
        Path save = dir.resolve("corrupt-faction.sav");
        Game original = new Game();
        original.newWorld(SEED, true);
        original.world.factionReputation.put(
                com.veylon.settlement.HumanFaction.HEADHUNTERS, -12f);
        assertTrue(SaveSystem.save(original, save));

        byte[] bytes = Files.readAllBytes(save);
        byte[] valid = "headhunters".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int at = lastIndexOf(bytes, valid);
        assertTrue(at > 0);
        byte[] invalid = "unknownside".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        System.arraycopy(invalid, 0, bytes, at, invalid.length);
        Files.write(save, bytes);

        assertFalse(SaveSystem.load(new Game(), save));
    }

    private static int lastIndexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = haystack.length - needle.length; i >= 0; i--) {
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
