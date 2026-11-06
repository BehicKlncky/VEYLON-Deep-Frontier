package com.veylon.save;

import com.veylon.Game;
import com.veylon.IntegratedCityFixture;
import com.veylon.SaveEnvelope;
import com.veylon.util.Vec3i;
import com.veylon.world.Chunk;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Urban package 25: a city saved by an earlier urban revision loads under this build with every fact
 * as that revision recorded it and its city regenerated block for block.
 *
 * <p>The fixture was produced on 2026-11-05 by the package-20 build itself: a detached worktree of
 * commit {@code e764168} (generator 19, content revision 3, before package 21 added {@code urban.visits}),
 * its main and test sources compiled alone with JDK 25.0.4.1, and the integrated fixture builder of
 * package 25 ({@link IntegratedCityFixture}, its first-roll preview replaced by searching the containers
 * in turn, which package 20 lacked) run against those classes under an isolated data directory. That
 * run wrote the save with package 20's {@code SaveSystem.save}, the fixture's fact locations and package
 * 20's own description of every fact, and printed the chunk hashes pinned here. The writer and its log
 * are kept in the ignored {@code build/m25-historical/m20/}. Never regenerate these resources.
 */
class UrbanEarlierRevisionSaveTest {
    private static final String SHA256 = "8678b640e2600491e4ebd939fbd2dcaf6749603f0b2dffad04c611c468671ad1";
    private static final int[][] FRESH_CHUNKS = {{200, 200}, {-180, 150}, {150, -220}};
    private static final String[] CHUNK_SHA256 = {
            "a1fe59b68bae40403671a7192d8ce03f0d155fb0c8ecb18951c335951e9ad853",
            "56d4da19da477aada80140f86a42b1d37741d29ab991acfe0fb08a5d32bcd9a5",
            "bd07ed31eb965f85f34e48cf2ebc01bf4da9bc1205cdc18d381c94cfb8ac9727"};

    @Test
    void aPackageTwentyCityComesBackFactForFactAndBlockForBlock(@TempDir Path dir) throws Exception {
        byte[] fixture = Base64.getDecoder().decode(text("urban-gen19-m20-authentic.sav.b64").trim());
        assertEquals(SHA256, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fixture)),
                "the fixture stays the bytes package 20 wrote");
        IntegratedCityFixture.Facts facts = facts(text("urban-gen19-m20-authentic.facts.txt"));
        List<String> described = text("urban-gen19-m20-authentic.describe.txt").lines().toList();
        List<String> sections = SaveEnvelope.parse(fixture).ids();
        assertFalse(sections.contains("urban.visits"), "package 20 had no visits ledger");

        Path save = dir.resolve("m20.sav");
        Files.write(save, fixture);
        for (int cycle = 0; cycle < 2; cycle++) {
            Game g = cycle == 0 ? new Game() : IntegratedCityFixture.sessionOf(IntegratedCityFixture.SEED + 7);
            assertTrue(SaveSystem.load(g, save), "cycle " + cycle + (cycle == 1 ? ", over a live economy city" : ""));
            assertEquals(World.GEN_URBAN_MISSIONS, g.world.generatorVersion);
            assertEquals(3, g.world.urban.contentRevision(), "its content revision comes from its urban.meta");
            assertEquals(described, IntegratedCityFixture.describe(g, facts), "every fact as package 20 recorded it");
            assertTrue(g.world.urban.visits.districts().isEmpty() && g.world.urban.visits.buildings().isEmpty(),
                    "without the section nothing was walked or entered");
            if (cycle == 0) {
                for (int i = 0; i < FRESH_CHUNKS.length; i++) {
                    assertNull(g.world.getChunk(FRESH_CHUNKS[i][0], FRESH_CHUNKS[i][1]));
                    assertEquals(CHUNK_SHA256[i], hash(g.world.getOrCreateChunk(FRESH_CHUNKS[i][0], FRESH_CHUNKS[i][1])),
                            "generator 19 as package 20 built it");
                }
            }
            Path again = dir.resolve("m20-" + cycle + ".sav");
            assertTrue(SaveSystem.save(g, again), "this build saves the earlier city on");
            List<String> resaved = SaveEnvelope.parse(Files.readAllBytes(again)).ids();
            List<String> expected = new ArrayList<>(sections);
            expected.add("urban.visits");
            assertEquals(expected, resaved, "the same sections, and the visits ledger this build adds");
            save = again;
        }
    }

    private static IntegratedCityFixture.Facts facts(String text) {
        Map<String, String> v = new HashMap<>();
        text.lines().forEach(line -> v.put(line.substring(0, line.indexOf('=')), line.substring(line.indexOf('=') + 1)));
        return new IntegratedCityFixture.Facts(Long.parseLong(v.get("flat")),
                cell(v.get("looted")), id(v.get("looted")), cell(v.get("interrupted")), id(v.get("interrupted")),
                cell(v.get("destroyed")), id(v.get("destroyed")), cell(v.get("openDoor")), cell(v.get("barredDoor")),
                cell(v.get("window")), cell(v.get("wall")), cell(v.get("barricade")), Long.parseLong(v.get("claimFloor")),
                cell(v.get("generator")), cell(v.get("lamp")), Long.parseLong(v.get("deadInfected")),
                Long.parseLong(v.get("deadAlien")), Long.parseLong(v.get("hiddenRefuge")),
                Long.parseLong(v.get("hiddenRoom")), Long.parseLong(v.get("camp")), Long.parseLong(v.get("yard")),
                Long.parseLong(v.get("stronghold")));
    }

    private static Vec3i cell(String value) {
        String[] xyz = value.split(" ")[0].split(",");
        return new Vec3i(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
    }

    private static long id(String value) {
        return Long.parseLong(value.split(" ")[1]);
    }

    private static String text(String resource) throws Exception {
        try (InputStream in = Objects.requireNonNull(UrbanEarlierRevisionSaveTest.class.getResourceAsStream(
                "/com/veylon/save/" + resource), resource)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String hash(Chunk c) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (int y = 0; y < Chunk.SY; y++) {
            for (int z = 0; z < Chunk.SZ; z++) {
                for (int x = 0; x < Chunk.SX; x++) { digest.update(c.get(x, y, z).id()); }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
