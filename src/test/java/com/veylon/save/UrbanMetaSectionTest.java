package com.veylon.save;

import com.veylon.Game;
import com.veylon.entity.GameMode;
import com.veylon.entity.Player;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.World;
import com.veylon.world.urban.UrbanDatum;
import com.veylon.world.urban.UrbanRevision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The urban generator and its revisions survive a save through the production
 * path, legacy saves stay free of urban state, and revision metadata this
 * build cannot honour fails the load before the live world is touched.
 */
class UrbanMetaSectionTest {

    private static final long SEED = 20_261_007L;
    private static final int ENVELOPE_MAGIC = 0x53334543; // "S3EC"

    @TempDir
    Path dir;

    @Test
    void anUrbanWorldRoundTripsItsGeneratorAndRevisionsThroughProductionBootstrap()
            throws IOException {
        Game original = urbanWorld(SEED);
        assertEquals(World.GEN_URBAN, original.world.generatorVersion);
        assertEquals(UrbanRevision.STARTER_CONTENT, original.world.urban.contentRevision(),
                "a fresh world records its generator's content revision before generating");
        Vec3i edit = new Vec3i(10, UrbanDatum.STREET_Y + 1, 10);
        original.world.setBlock(edit.x(), edit.y(), edit.z(), BlockType.PLANK, true);
        Path save = save(original, "urban.sav");

        Map<String, byte[]> sections = sections(Files.readAllBytes(save));
        assertTrue(sections.containsKey(UrbanMetaSection.ID));
        assertEquals(List.of(1, UrbanRevision.LAYOUT_DATUM_PLACEHOLDER,
                        UrbanRevision.STARTER_CONTENT),
                ints(sections.get(UrbanMetaSection.ID)), "urban.meta v1: version, layout, content");

        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save));
        assertEquals(World.GEN_URBAN, loaded.world.generatorVersion);
        assertEquals(UrbanRevision.LAYOUT_DATUM_PLACEHOLDER, loaded.world.urban.layoutRevision());
        assertEquals(UrbanRevision.STARTER_CONTENT, loaded.world.urban.contentRevision());
        assertEquals(BlockType.PLANK, loaded.world.getBlock(edit.x(), edit.y(), edit.z()));
        assertEquals(BlockType.STONE_BRICK,
                loaded.world.getBlock(-20, UrbanDatum.STREET_Y, -20), "geometry re-derived");
        assertChunksMatch(original.world, loaded.world, -1, -1);
        assertTrue(loaded.world.settlements.isEmpty());
        assertNull(loaded.world.campPos);
        assertNull(loaded.faction.campPos);
        assertTrue(loaded.entities.npcs.isEmpty(), "an urban load raises no crash camp");
    }

    @Test
    void aStreetNetworkWorldRoundTripsItsLayoutGeometryAndSpawn() throws IOException {
        Game original = new Game();
        original.newWorld(SEED, true, World.GEN_URBAN_STREETS, GameMode.SURVIVAL);
        assertEquals(UrbanRevision.LAYOUT_STREET_NETWORK, original.world.urban.layoutRevision());
        int[] spawn = original.world.urban.spawnColumn();
        Vec3i edit = new Vec3i(spawn[0], UrbanDatum.STREET_Y + 1, spawn[1] + 1);
        original.world.setBlock(edit.x(), edit.y(), edit.z(), BlockType.PLANK, true);
        Path save = save(original, "streets.sav");
        assertEquals(List.of(1, UrbanRevision.LAYOUT_STREET_NETWORK, UrbanRevision.STARTER_CONTENT),
                ints(sections(Files.readAllBytes(save)).get(UrbanMetaSection.ID)));

        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save));
        assertEquals(World.GEN_URBAN_STREETS, loaded.world.generatorVersion);
        assertEquals(UrbanRevision.LAYOUT_STREET_NETWORK, loaded.world.urban.layoutRevision());
        assertEquals(BlockType.PLANK, loaded.world.getBlock(edit.x(), edit.y(), edit.z()));
        assertEquals(List.of(spawn[0], spawn[1]), List.of(loaded.world.urban.spawnColumn()[0],
                loaded.world.urban.spawnColumn()[1]), "the spawn is re-derived from the plan");
        int spawnChunkX = Math.floorDiv(spawn[0], Chunk.SX);
        int spawnChunkZ = Math.floorDiv(spawn[1], Chunk.SZ);
        assertChunksMatch(original.world, loaded.world, -1, -1);
        assertChunksMatch(original.world, loaded.world, 0, 0);
        assertChunksMatch(original.world, loaded.world, spawnChunkX + 3, spawnChunkZ - 2);
        assertEquals(original.world.urban.parcelAt(spawn[0] - 2, spawn[1]),
                loaded.world.urban.parcelAt(spawn[0] - 2, spawn[1]), "the refuge parcel keeps its identity");
        assertTrue(loaded.entities.creatures.isEmpty(), "no surface wildlife after a load either");

        // urban.meta that names the datum layout under the street generator is refused.
        byte[] bytes = Files.readAllBytes(save);
        Game live = new Game();
        live.newWorld(SEED + 9, true, World.GEN_CAVE_IDENTITIES);
        World liveWorld = live.world;
        assertFalse(SaveSystem.load(live, write("mismatch.sav", withPayload(bytes,
                meta(1, UrbanRevision.LAYOUT_DATUM_PLACEHOLDER, UrbanRevision.STARTER_CONTENT)))));
        assertSame(liveWorld, live.world);
    }

    @Test
    void buildingTemplateRevisionIsSelectedBeforeLoadBootstrapAndRecomputesStableReservations() throws IOException {
        Game original = new Game();
        original.newWorld(SEED, true, World.GEN_URBAN_PLANS, GameMode.SURVIVAL);
        var parcel = original.world.urban.regionPlan(0, 0).refuge();
        var plan = original.world.urban.buildingAt(parcel.x0(), parcel.z0());
        assertEquals(1, original.world.urban.buildingTemplateRevision());
        Path file = save(original, "building-plans.sav");
        byte[] bytes = Files.readAllBytes(file);
        assertEquals(List.of(1, UrbanRevision.LAYOUT_BUILDING_PLANS, UrbanRevision.STARTER_CONTENT),
                ints(sections(bytes).get(UrbanMetaSection.ID)), "existing meta layout, no new codec bytes");
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, file));
        assertEquals(1, loaded.world.urban.buildingTemplateRevision());
        assertEquals(0, loaded.world.urban.cachedBuildingPlans(), "load never needs to stamp or restore a plan cache");
        assertEquals(plan, loaded.world.urban.buildingAt(parcel.x1(), parcel.z1()));
        assertChunksMatch(original.world, loaded.world, Math.floorDiv(parcel.x0(), 16), Math.floorDiv(parcel.z0(), 16));

        World preserved = loaded.world;
        assertFalse(SaveSystem.load(loaded, write("wrong-plan-layout.sav", withPayload(bytes,
                meta(1, UrbanRevision.LAYOUT_STREET_NETWORK, UrbanRevision.STARTER_CONTENT)))));
        assertSame(preserved, loaded.world);
        assertTrue(SaveSystem.load(loaded, write("plans-absent-meta.sav", withoutSection(bytes, UrbanMetaSection.ID))));
        assertEquals(1, loaded.world.urban.buildingTemplateRevision(), "generator pins template even without optional meta");
        assertEquals(plan, loaded.world.urban.buildingAt(parcel.x0(), parcel.z0()));

        loaded.newWorld(SEED + 1, true, World.GEN_URBAN_PLANS, GameMode.CREATIVE);
        assertEquals(0, loaded.world.urban.cachedBuildingPlans(), "a new world owns a fresh derived cache");
        loaded.newWorld(SEED, true, World.GEN_URBAN_STREETS, GameMode.SURVIVAL);
        assertEquals(0, loaded.world.urban.buildingTemplateRevision());
        assertNull(loaded.world.urban.buildingAt(parcel.x0(), parcel.z0()), "generator 5 is never silently upgraded");
    }

    @Test
    void loadingOverALiveWorldRehearsesTheUrbanStateAndReplacesTheWorld() throws IOException {
        Path save = save(urbanWorld(SEED), "urban.sav");
        Game live = new Game();
        live.newWorld(SEED + 1, true, World.GEN_CAVE_IDENTITIES);
        assertTrue(live.entities.npcs.size() > 0, "fixture: the legacy camp has residents");

        assertTrue(SaveSystem.load(live, save));
        assertTrue(live.world.isUrban());
        assertEquals(UrbanRevision.STARTER_CONTENT, live.world.urban.contentRevision());
        assertNull(live.faction.campPos, "the legacy camp does not follow the player");
        assertTrue(live.entities.npcs.isEmpty());
    }

    @Test
    void aLegacySaveCarriesNoUrbanStateInEitherDirection() throws IOException {
        Game legacy = new Game();
        legacy.newWorld(SEED, true, World.GEN_CAVE_IDENTITIES);
        assertNull(legacy.world.urban);
        Path save = save(legacy, "legacy.sav");
        assertFalse(sections(Files.readAllBytes(save)).containsKey(UrbanMetaSection.ID),
                "legacy saves keep the sections older builds already read");

        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save));
        assertEquals(World.GEN_CAVE_IDENTITIES, loaded.world.generatorVersion);
        assertNull(loaded.world.urban);
    }

    @Test
    void anUrbanSaveWithoutUrbanMetaTakesTheDocumentedDefault() throws IOException {
        byte[] bytes = Files.readAllBytes(save(urbanWorld(SEED), "urban.sav"));
        Path stripped = write("stripped.sav", withoutSection(bytes, UrbanMetaSection.ID));

        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, stripped));
        assertTrue(loaded.world.urban.contentRevisionResolved(),
                "the loader resolves the revision even when the section is absent");
        assertEquals(UrbanRevision.ABSENT_CONTENT, loaded.world.urban.contentRevision());
    }

    @Test
    void revisionMetadataThisBuildCannotHonourFailsTheLoadAndKeepsTheLiveWorld()
            throws IOException {
        byte[] urban = Files.readAllBytes(save(urbanWorld(SEED), "urban.sav"));
        Game legacyGame = new Game();
        legacyGame.newWorld(SEED + 2, true, World.GEN_CAVE_IDENTITIES);
        byte[] legacy = Files.readAllBytes(save(legacyGame, "legacy.sav"));
        int layout = UrbanRevision.LAYOUT_DATUM_PLACEHOLDER;
        int content = UrbanRevision.STARTER_CONTENT;

        Map<String, byte[]> damaged = new LinkedHashMap<>();
        damaged.put("newer section version", withPayload(urban, meta(2, layout, content)));
        damaged.put("layout of another generator", withPayload(urban, meta(1, layout + 1, content)));
        damaged.put("layout zero", withPayload(urban, meta(1, 0, content)));
        damaged.put("newer content revision", withPayload(urban, meta(1, layout, UrbanRevision.CURRENT_CONTENT + 1)));
        damaged.put("the economy's content revision in an earlier layout",
                withPayload(urban, meta(1, layout, UrbanRevision.ECONOMY_CONTENT)));
        damaged.put("content revision zero", withPayload(urban, meta(1, layout, 0)));
        damaged.put("negative content revision", withPayload(urban, meta(1, layout, -1)));
        damaged.put("trailing bytes", withPayload(urban, meta(1, layout, content, 0)));
        damaged.put("truncated", withPayload(urban, meta(1, layout)));
        damaged.put("urban.meta in a legacy world",
                withExtraSection(legacy, UrbanMetaSection.ID, meta(1, layout, content)));
        damaged.put("generator above the supported maximum",
                withInt(urban, 16, World.MAX_SUPPORTED_GENERATOR + 1));
        damaged.put("generator zero", withInt(urban, 16, 0));

        Game live = new Game();
        live.newWorld(SEED + 3, true, World.GEN_CAVE_IDENTITIES);
        World liveWorld = live.world;
        Player livePlayer = live.player;
        for (Map.Entry<String, byte[]> entry : damaged.entrySet()) {
            Path file = write("damaged.sav", entry.getValue());
            assertFalse(SaveSystem.load(live, file), entry.getKey() + " must fail the load");
            assertSame(liveWorld, live.world, entry.getKey() + " replaced the live world");
            assertSame(livePlayer, live.player, entry.getKey() + " replaced the live player");
        }
        // The same damage refused on the title screen, where there is no rehearsal.
        Game title = new Game();
        assertFalse(SaveSystem.load(title, write("newer.sav",
                withPayload(urban, meta(1, layout, UrbanRevision.CURRENT_CONTENT + 1)))));
    }

    /**
     * The economy city (generator 20, milestone 23) records content revision 4, the only one its layout
     * accepts: a save that names any other is refused before the live world is touched, never rolled
     * against tables it was not created with.
     */
    @Test
    void anEconomyCityKeepsContentRevisionFourAndRefusesAnyOther() throws IOException {
        Game original = new Game();
        original.newWorld(SEED, true, World.GEN_URBAN_ECONOMY, GameMode.SURVIVAL);
        assertEquals(UrbanRevision.ECONOMY_CONTENT, original.world.urban.contentRevision());
        byte[] bytes = Files.readAllBytes(save(original, "economy.sav"));
        assertEquals(List.of(1, UrbanRevision.LAYOUT_ECONOMY, UrbanRevision.ECONOMY_CONTENT),
                ints(sections(bytes).get(UrbanMetaSection.ID)));

        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, write("economy-copy.sav", bytes)));
        assertEquals(World.GEN_URBAN_ECONOMY, loaded.world.generatorVersion);
        assertEquals(UrbanRevision.ECONOMY_CONTENT, loaded.world.urban.contentRevision());

        World live = loaded.world;
        for (int other : new int[]{UrbanRevision.ABSENT_CONTENT, UrbanRevision.STARTER_CONTENT}) {
            assertFalse(SaveSystem.load(loaded, write("economy-" + other + ".sav", withPayload(bytes,
                    meta(1, UrbanRevision.LAYOUT_ECONOMY, other)))), "content " + other + " in the economy city");
            assertSame(live, loaded.world, "a refused revision leaves the live world alone");
        }
        assertFalse(SaveSystem.load(loaded, write("economy-no-meta.sav", withoutSection(bytes, UrbanMetaSection.ID))),
                "without urban.meta the documented default (content 1) is no economy revision either");
        assertSame(live, loaded.world);
    }

    @Test
    void aLoadingWorldCannotReadItsContentRevisionUntilTheSaveRestoresIt() {
        Game loading = new Game();
        loading.newWorld(SEED, false, World.GEN_URBAN);
        assertFalse(loading.world.urban.contentRevisionResolved());
        assertThrows(IllegalStateException.class, () -> loading.world.urban.contentRevision(),
                "nothing may roll urban content while a load is still restoring its revision");
        assertFalse(SaveSystem.save(loading, dir.resolve("unresolved.sav")),
                "an unresolved revision is never written as if it were known");

        Game fresh = urbanWorld(SEED);
        assertEquals(UrbanRevision.STARTER_CONTENT, fresh.world.urban.contentRevision());
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static Game urbanWorld(long seed) {
        Game game = new Game();
        game.newWorld(seed, true, World.GEN_URBAN, GameMode.SURVIVAL);
        return game;
    }

    private Path save(Game game, String name) {
        Path path = dir.resolve(name);
        assertTrue(SaveSystem.save(game, path));
        return path;
    }

    private Path write(String name, byte[] bytes) throws IOException {
        Path path = dir.resolve(name);
        Files.write(path, bytes);
        return path;
    }

    private static void assertChunksMatch(World a, World b, int cx, int cz) {
        Chunk left = a.getOrCreateChunk(cx, cz);
        Chunk right = b.getOrCreateChunk(cx, cz);
        for (int y = 0; y < Chunk.SY; y++) {
            for (int z = 0; z < Chunk.SZ; z++) {
                for (int x = 0; x < Chunk.SX; x++) {
                    assertEquals(left.get(x, y, z), right.get(x, y, z));
                }
            }
        }
    }

    private static byte[] meta(int... values) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            for (int value : values) {
                out.writeInt(value);
            }
        }
        return bytes.toByteArray();
    }

    private static List<Integer> ints(byte[] payload) {
        List<Integer> values = new ArrayList<>();
        for (int at = 0; at + 4 <= payload.length; at += 4) {
            values.add(readInt(payload, at));
        }
        return values;
    }

    // ------------------------------------------------------------------
    // The S3EC section envelope that ends every v3 save written by this build
    // ------------------------------------------------------------------

    private record Entry(String id, int start, int payloadStart, int end) {
    }

    private record Envelope(int countOffset, List<Entry> entries) {
        Entry find(String id) {
            return entries.stream().filter(entry -> entry.id.equals(id)).findFirst()
                    .orElseThrow(() -> new AssertionError("no section " + id));
        }
    }

    /**
     * Locates the envelope by its magic and accepts only a parse that accounts
     * for every byte to the end of the file, so a coincidental magic inside
     * earlier data cannot be mistaken for it.
     */
    private static Envelope envelope(byte[] bytes) {
        for (int at = bytes.length - 8; at >= 0; at--) {
            if (readInt(bytes, at) != ENVELOPE_MAGIC) {
                continue;
            }
            int count = readInt(bytes, at + 4);
            int pos = at + 8;
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < count && count <= 256 && pos + 2 <= bytes.length; i++) {
                int idLength = ((bytes[pos] & 0xff) << 8) | (bytes[pos + 1] & 0xff);
                int lengthAt = pos + 2 + idLength;
                if (lengthAt + 4 > bytes.length) {
                    break;
                }
                int length = readInt(bytes, lengthAt);
                if (length < 0 || lengthAt + 4 + length > bytes.length) {
                    break;
                }
                String id = new String(bytes, pos + 2, idLength, StandardCharsets.UTF_8);
                entries.add(new Entry(id, pos, lengthAt + 4, lengthAt + 4 + length));
                pos = lengthAt + 4 + length;
            }
            if (entries.size() == count && pos == bytes.length) {
                return new Envelope(at + 4, entries);
            }
        }
        throw new AssertionError("no v3 section envelope found");
    }

    private static Map<String, byte[]> sections(byte[] bytes) {
        Map<String, byte[]> sections = new LinkedHashMap<>();
        for (Entry entry : envelope(bytes).entries()) {
            byte[] payload = new byte[entry.end() - entry.payloadStart()];
            System.arraycopy(bytes, entry.payloadStart(), payload, 0, payload.length);
            sections.put(entry.id(), payload);
        }
        return sections;
    }

    private static byte[] withPayload(byte[] bytes, byte[] payload) {
        Entry entry = envelope(bytes).find(UrbanMetaSection.ID);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(bytes, 0, entry.payloadStart() - 4);
        out.writeBytes(intBytes(payload.length));
        out.writeBytes(payload);
        out.write(bytes, entry.end(), bytes.length - entry.end());
        return out.toByteArray();
    }

    private static byte[] withoutSection(byte[] bytes, String id) {
        Envelope envelope = envelope(bytes);
        Entry entry = envelope.find(id);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(bytes, 0, entry.start());
        out.write(bytes, entry.end(), bytes.length - entry.end());
        return withInt(out.toByteArray(), envelope.countOffset(), envelope.entries().size() - 1);
    }

    private static byte[] withExtraSection(byte[] bytes, String id, byte[] payload)
            throws IOException {
        Envelope envelope = envelope(bytes);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(bytes);
        try (DataOutputStream section = new DataOutputStream(out)) {
            section.writeUTF(id);
            section.writeInt(payload.length);
            section.write(payload);
        }
        return withInt(out.toByteArray(), envelope.countOffset(), envelope.entries().size() + 1);
    }

    private static byte[] withInt(byte[] bytes, int offset, int value) {
        byte[] copy = bytes.clone();
        System.arraycopy(intBytes(value), 0, copy, offset, 4);
        return copy;
    }

    private static byte[] intBytes(int value) {
        return new byte[]{(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8),
                (byte) value};
    }

    private static int readInt(byte[] bytes, int at) {
        return ((bytes[at] & 0xff) << 24) | ((bytes[at + 1] & 0xff) << 16)
                | ((bytes[at + 2] & 0xff) << 8) | (bytes[at + 3] & 0xff);
    }
}
