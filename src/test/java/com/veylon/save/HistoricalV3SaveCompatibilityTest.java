package com.veylon.save;

import com.veylon.Game;
import com.veylon.SaveEnvelope;
import com.veylon.ai.Quest;
import com.veylon.entity.Affliction;
import com.veylon.entity.Creature;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.item.ItemType;
import com.veylon.settlement.HumanFaction;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.simulation.WeatherSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Urban package 25: saves written by the released v3 builds load under this build with their rules,
 * terrain and settlements unchanged. Every release from v0.3.0 to v0.8.1 wrote save format v3 with
 * generator 3; these fixtures come from the first and the last of them.
 *
 * <p>Each fixture was produced on 2026-11-05 by compiling the archived release in isolation (detached
 * worktrees of tag {@code v0.3.0}, commit {@code bc110209f7b27dd6a21a03e05590dc43da0c9535}, and tag
 * {@code v0.8.1}, commit {@code 615c146de6eab78d50a3ab3bd89db9f22eddadfe}, with JDK 25.0.4.1) and
 * running a small writer against those classes alone, under an isolated data directory, that staged
 * the state below through each release's own API and called its {@code SaveSystem.save(Game, Path)}.
 * The same runs printed the chunk hashes and settlement identities pinned here. The writers and
 * their logs are kept in the ignored {@code build/m25-historical/}. The binary saves are Base64 text so
 * they can be reviewed; their decoded SHA-256 is asserted before loading. Never regenerate them.
 */
class HistoricalV3SaveCompatibilityTest {

    /** What a writer staged in a Survival world, as that release placed it around its spawn. */
    private record Survival(String resource, String sha256, long seed, int sx, int sy, int sz, Vec3i poi,
                            String questId, int questRequired, ItemType questReward, int questRewardCount,
                            String settlementsSha256, String label, float alert, String[] chunkSha256) { }

    private static final int[][] FRESH_CHUNKS = {{37, -29}, {-41, 33}, {80, 80}};

    private static final Survival V030 = new Survival("v0.3.0-authentic.sav.b64",
            "b149f4611908dbe5620f6f495b9de49a2ff65ea9f7c1be22b715191a732576a3", 30_017_717L, 40, 33, 36,
            new Vec3i(2, 14, 26), "quest:8c33610f8d541503:1", 6, ItemType.BANDAGE, 2,
            "c296f54754f9ec29e73044113dd8a8ea1de5848f315132225139ff6807076a74", "v0.3.0",
            // That writer ran the release's faction tick to open its quest offer, and the tick let the alarm fall from 12.
            0f, new String[]{
            "02085464c8e69d9c53fb653b069b61f1c51401f7ed1f777629432acec20f366a",
            "f975d90e4c66c294971c7f8295faa81cf1fb9a4cfac2a4455c78a5f397f392e6",
            "10bdb56b80b426081b8631d60c59663a36300369dc239c9c5cf51bf81309aa32"});

    private static final Survival V081 = new Survival("v0.8.1-survival-authentic.sav.b64",
            "2a82e1f5a580ea6223b581d40e5bd5d5efd706e9427876962e388974e3f207c3", 81_081_928L, 8, 35, 8,
            new Vec3i(10, 35, 7), "quest:e34a144c6fd725a1:1", 1, ItemType.IRON_INGOT, 1,
            "c50c719239baca5b5f0fc0539ac2a3d1ae17966503a49ddb2eb9c02602ec6a4e", "v0.8.1", 12f, new String[]{
            "c966095716da474869f2959659298e7054457782285366ad2ec02eedfcbae09a",
            "0c7de3e2b4990b9ae0f91aa0cb75cbafe5e884f3cac16522b45afa65dfc41962",
            "8a125c641667edb62f503c45e495f591dc0a26ebcdaff286a8dc8c6def892974"});

    private static final long HOSTILE = -21_474_836_485L;

    @Test
    void theFirstV3ReleaseSaveLoadsWithItsRulesTerrainAndSettlements(@TempDir Path dir) throws Exception {
        assertSurvival(V030, dir);
    }

    @Test
    void theLastLegacyReleaseSaveLoadsWithItsRulesTerrainAndSettlements(@TempDir Path dir) throws Exception {
        assertSurvival(V081, dir);
    }

    @Test
    void theLastReleaseCreativeSaveKeepsItsModeFlightAndWorldControls(@TempDir Path dir) throws Exception {
        byte[] fixture = fixture("v0.8.1-creative-authentic.sav.b64",
                "0d1f33df0cd8a3ef5f6b9c0aecd54abb54ea39f43463d459e7cf4eafa53da7d3");
        Path save = dir.resolve("creative.sav");
        Files.write(save, fixture);
        for (int cycle = 0; cycle < 2; cycle++) {
            Game g = new Game();
            assertTrue(SaveSystem.load(g, save), "cycle " + cycle);
            assertEquals(81_081_929L, g.world.seed);
            assertEquals(World.GEN_CAVE_IDENTITIES, g.world.generatorVersion);
            assertEquals(GameMode.CREATIVE, g.gameMode());
            assertTrue(g.creativeMarked(), "the permanent Creative mark");
            assertTrue(g.player.abilities.flying());
            assertTrue(g.daylightFrozen() && g.weatherLocked() && g.spawningPaused(), "every world control held");
            assertEquals(18 * 60 + 40, g.time.totalMinutes, 1e-6);
            assertTrue(g.player.inventory.count(ItemType.IRON_INGOT) >= 64);
            List<Vec3i> planks = g.world.changedBlocks.entrySet().stream()
                    .filter(e -> BlockType.byId(e.getValue()) == BlockType.PLANK).map(Map.Entry::getKey).toList();
            assertEquals(4, planks.size(), "the four planks the Creative player placed");
            assertEquals(1, planks.stream().map(Vec3i::y).distinct().count());
            assertTrue(g.eventLog.recent(30).stream().anyMatch(l -> l.contains("Authentic v0.8.1 Creative compatibility fixture")));
            assertNull(g.world.urban);
            assertFreshChunks(g, new String[]{
                    "94feea0524da52b67bc49c9ed16b8af36f946131d8d58aa7c64f3b97820d06b1",
                    "936c5df9c36b83cfe3b134749a2d70b4c6beaf3d196763ac5d6b1e9af59d5126",
                    "d092fe34b793509c7eb7c6900fbc6d232397c991f478e4d3dcec2f91b41f24cc"});
            Path again = dir.resolve("creative-" + cycle + ".sav");
            assertTrue(SaveSystem.save(g, again));
            assertLegacySections(fixture, Files.readAllBytes(again));
            save = again;
        }
    }

    private static void assertSurvival(Survival f, Path dir) throws Exception {
        byte[] fixture = fixture(f.resource(), f.sha256());
        try (DataInputStream header = new DataInputStream(new java.io.ByteArrayInputStream(fixture))) {
            assertEquals(0x5645594C, header.readInt(), "VEYL magic");
            assertEquals(3, header.readInt(), "the release wrote format v3");
            assertEquals(f.seed(), header.readLong());
            assertEquals(World.GEN_CAVE_IDENTITIES, header.readInt(), "generator 3, the release default");
        }
        Path save = dir.resolve(f.label() + ".sav");
        Files.write(save, fixture);
        for (int cycle = 0; cycle < 2; cycle++) {
            Game g = new Game();
            g.newWorld(91_340L, true);
            World live = g.world;
            assertTrue(SaveSystem.load(g, save), f.label() + " cycle " + cycle + " loads over a live world");
            assertNotSame(live, g.world);
            assertSurvivalState(f, g);
            if (cycle == 0) {
                assertEquals(f.settlementsSha256(), settlementIdentities(g), "every planned settlement keeps its identity");
                assertFreshChunks(g, f.chunkSha256());
            }
            Path again = dir.resolve(f.label() + "-" + cycle + ".sav");
            assertTrue(SaveSystem.save(g, again), "this build saves the old world on");
            assertLegacySections(fixture, Files.readAllBytes(again));
            save = again;
        }
    }

    private static void assertSurvivalState(Survival f, Game g) {
        String where = f.label();
        World w = g.world;
        assertEquals(f.seed(), w.seed);
        assertEquals(World.GEN_CAVE_IDENTITIES, w.generatorVersion);
        assertNull(w.urban, where + ": a legacy world carries no city state");
        assertFalse(w.evictsChunks(), "legacy terrain keeps every chunk");
        assertEquals(GameMode.SURVIVAL, g.gameMode());
        assertEquals(3 * 24 * 60 + 13 * 60 + 25, g.time.totalMinutes, 1e-6);
        assertEquals(WeatherSystem.Weather.values()[1], g.weather.current);
        assertEquals(WeatherSystem.Weather.values()[2], g.weather.next);
        assertEquals(0.25f, g.weather.blend, 1e-6f);
        assertEquals(75f, g.weather.changeTimer, 1e-6f);
        var p = g.player;
        assertEquals(61.5f, p.health, 1e-4f);
        assertEquals(42f, p.hunger, 1e-4f);
        assertEquals(37.25f, p.thirst, 1e-4f);
        assertEquals(80f, p.stamina, 1e-4f);
        assertEquals(12.5f, p.fatigue, 1e-4f);
        assertEquals(3, p.hotbarSel);
        assertEquals(Set.of("rain_cloak", "iron_armor"), p.blueprints, "progression toward the beacon");
        assertEquals(45f, p.afflictions.get(Affliction.values()[0]), 1e-4f);
        assertTrue(p.inventory.count(ItemType.IRON_INGOT) >= 7);
        assertTrue(p.inventory.count(ItemType.BLUEPRINT_FRAGMENT) >= 2);
        Vec3i crate = new Vec3i(f.sx() + 3, f.sy(), f.sz());
        assertEquals(BlockType.CRATE, w.getBlock(crate.x(), crate.y(), crate.z()));
        assertEquals(3, w.crateContents.get(crate).count(ItemType.BANDAGE));
        Vec3i fire = new Vec3i(f.sx() - 3, f.sy(), f.sz());
        assertEquals(BlockType.CAMPFIRE, w.getBlock(fire.x(), fire.y(), fire.z()));
        assertEquals(321f, w.campfireFuel.get(fire), 1e-4f);
        assertEquals(BlockType.AIR, w.getBlock(f.sx(), f.sy() - 1, f.sz() + 4), "the dug block stays dug");
        assertEquals(new Vec3i(f.sx() + 6, f.sy(), f.sz() + 6), w.beaconPos);
        assertEquals(1, w.beaconStage, "beacon progress");
        assertTrue(w.discoveredPois.contains(f.poi()));
        assertEquals(47f, g.faction.trust, 1e-4f);
        assertEquals(f.alert(), g.faction.alert, 1e-4f);
        assertEquals(13, g.faction.foodStock);
        assertEquals(9, g.faction.woodStock);
        assertEquals(1, g.faction.upgradeStage);
        assertTrue(g.faction.alliedGiftGiven);
        Quest q = g.faction.quest;
        assertNotNull(q, where + ": the camp's request");
        assertEquals(Quest.Type.FETCH, q.type);
        assertEquals(f.questId(), q.instanceId);
        assertEquals(f.questRequired(), q.required);
        assertEquals(f.questReward(), q.rewardItem);
        assertEquals(f.questRewardCount(), q.rewardCount);
        Settlement hostile = w.settlements.get(HOSTILE);
        assertNotNull(hostile);
        assertTrue(hostile.cleared && hostile.occupied && hostile.discovered && hostile.rumored);
        assertEquals(HumanFaction.FRONTIER, hostile.factionId);
        assertEquals(Settlement.Alignment.HOSTILE, hostile.alignment);
        assertEquals(123, hostile.foodStock);
        assertEquals(55f, hostile.alertLevel, 1e-4f);
        assertEquals(17, hostile.dormantStep);
        assertEquals(22.5f, hostile.dormantAccumulator, 1e-4f);
        assertEquals(12, hostile.residents.size());
        assertFalse(hostile.residents.getFirst().alive, "the dead stay dead");
        assertEquals(88f, hostile.residents.getFirst().hunger, 1e-4f);
        assertEquals(-60f, w.factionReputation.get(HumanFaction.HEADHUNTERS), 1e-4f);
        assertEquals(35f, w.factionBounty.get(HumanFaction.HEADHUNTERS), 1e-4f);
        Npc ashfang = g.entities.npcs.stream().filter(n -> "Ashfang".equals(n.name)).findFirst().orElseThrow();
        assertEquals(NpcArchetype.POWDERMAN, ashfang.archetype);
        assertEquals(HOSTILE, ashfang.settlementId);
        assertEquals(1, ashfang.residentIndex);
        assertEquals(1, ashfang.loadedAmmo);
        assertTrue(g.entities.creatures.stream().anyMatch(c -> c.type == Creature.CreatureType.DEER));
        assertTrue(g.entities.carcasses.stream().anyMatch(c -> c.type == Creature.CreatureType.HARE));
        assertFalse(g.events.active.isEmpty());
        assertTrue(g.eventLog.recent(30).stream().anyMatch(l -> l.contains("Authentic " + where + " compatibility fixture")));
    }

    /**
     * The settlements of regions -6..6, as the release's own planner printed them before the writer changed
     * one: id, type, alignment and every resident's name and role. The faction is left out, since the
     * writer gave one settlement a new owner.
     */
    private static String settlementIdentities(Game g) throws Exception {
        List<String> lines = new ArrayList<>();
        for (int rx = -6; rx <= 6; rx++) {
            for (int rz = -6; rz <= 6; rz++) {
                Settlement s = g.world.settlementForRegion(rx, rz);
                if (s == null) { continue; }
                List<String> people = new ArrayList<>();
                for (Settlement.Resident r : s.residents) { people.add(r.name + "/" + r.archetype.id); }
                lines.add("settlement " + rx + "," + rz + " " + s.id + " " + s.type + " " + s.alignment + " ["
                        + String.join(" ", people) + "]");
            }
        }
        return sha256(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * A release's world saved again by this build keeps every section the release wrote, in its order,
     * adds only the optional sections later releases introduced, and never a city section.
     */
    private static void assertLegacySections(byte[] release, byte[] resaved) {
        List<String> before = SaveEnvelope.parse(release).ids(), after = SaveEnvelope.parse(resaved).ids();
        assertEquals(before, after.stream().filter(before::contains).toList(), "every section the release wrote");
        for (String id : after) {
            assertFalse(id.startsWith("urban.") || id.equals("world.fixtures"), "a legacy world writes no " + id);
            assertTrue(before.contains(id) || Set.of("player.game-mode", "world.creative-controls", "world.bodies",
                    "world.fragments", "world.remains", "combat.keg-fuse-attribution").contains(id), "unexpected " + id);
        }
    }

    /** Chunks nothing has generated yet come out exactly as the release's generator made them. */
    private static void assertFreshChunks(Game g, String[] expected) throws Exception {
        for (int i = 0; i < FRESH_CHUNKS.length; i++) {
            int cx = FRESH_CHUNKS[i][0], cz = FRESH_CHUNKS[i][1];
            assertNull(g.world.getChunk(cx, cz), "a chunk the load has not generated");
            Chunk c = g.world.getOrCreateChunk(cx, cz);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int y = 0; y < Chunk.SY; y++) {
                for (int z = 0; z < Chunk.SZ; z++) {
                    for (int x = 0; x < Chunk.SX; x++) { digest.update(c.get(x, y, z).id()); }
                }
            }
            assertEquals(expected[i], HexFormat.of().formatHex(digest.digest()), "terrain at " + cx + "," + cz);
        }
    }

    private static byte[] fixture(String resource, String sha256) throws Exception {
        try (InputStream in = Objects.requireNonNull(HistoricalV3SaveCompatibilityTest.class.getResourceAsStream(
                "/com/veylon/save/" + resource), resource)) {
            byte[] bytes = Base64.getDecoder().decode(new String(in.readAllBytes(), StandardCharsets.US_ASCII).trim());
            assertEquals(sha256, sha256(bytes), resource + " must stay the bytes the release wrote");
            return bytes;
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
