package com.veylon;

import com.veylon.entity.Creature;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.entity.Player;
import com.veylon.item.ItemType;
import com.veylon.save.SaveSystem;
import com.veylon.settlement.UrbanSite;
import com.veylon.settlement.UrbanSiteDirector;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.UrbanStarterSite;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Package 25: the integrated urban fixture ({@link IntegratedCityFixture}) through complete round
 * trips. Every irreversible fact comes back exactly and a second save writes the same sections;
 * revisits in either order and repeated dormancy refill, resurrect, duplicate and pay nothing; a load
 * over a populated live world replaces it whole; the city regenerates the same blocks in any order
 * and after its chunks are dropped; a new world inherits nothing; a failed load leaves the live
 * world and the last good save as they were; and the order sections appear in changes nothing.
 */
class UrbanIntegratedSaveTest {
    @TempDir Path directory;

    private static IntegratedCityFixture.Facts facts;
    private static byte[] saved;
    private static List<String> described;
    private static List<String> core;
    private static Map<Long, String> builtChunks;

    @BeforeAll
    static void buildTheFixtureOnce(@TempDir Path dir) throws Exception {
        IntegratedCityFixture.Built built = IntegratedCityFixture.build();
        Path file = dir.resolve("integrated.sav");
        assertTrue(SaveSystem.save(built.game(), file));
        saved = Files.readAllBytes(file);
        facts = built.facts();
        described = IntegratedCityFixture.describe(built.game(), facts);
        core = coreDigest(built.game());
        builtChunks = new LinkedHashMap<>();
        for (Chunk c : built.game().world.loadedChunks()) { builtChunks.put(World.key(c.cx, c.cz), hash(c)); }
        System.out.println("[integrated] " + saved.length + " bytes, sections " + SaveEnvelope.parse(saved).ids());
        System.out.println("[integrated] " + String.join("\n[integrated] ", described));
    }

    @Test
    void everyFactComesBackAndASecondSaveWritesTheSameSections() throws Exception {
        Game first = load(saved);
        assertEquals(described, IntegratedCityFixture.describe(first, facts));
        assertEquals(core, coreDigest(first));
        byte[] again = save(first);
        assertSameSave(saved, again);
        Game second = load(again);
        assertEquals(described, IntegratedCityFixture.describe(second, facts), "and again after a second load");
        assertEquals(core, coreDigest(second));
    }

    @Test
    void revisitsInEitherOrderAndRepeatedDormancyRefillResurrectDuplicateAndPayNothing() throws Exception {
        for (boolean campFirst : new boolean[]{true, false}) {
            Game g = load(saved);
            UrbanSite camp = site(g, facts.camp()), ward = site(g, facts.stronghold());
            for (int round = 0; round < 3; round++) {
                away(g);
                assertTrue(UrbanSiteDirector.sites(g.world).stream().allMatch(s -> SurvivorWorldArena.live(s).isEmpty()),
                        "far away, everyone is dormant");
                if (campFirst) { visitCamp(g); visitFlat(g); } else { visitFlat(g); visitCamp(g); }
                visitCompounds(g);
                visitAnchors(g);
            }
            assertEquals(durable(described), durable(IntegratedCityFixture.describe(g, facts)),
                    campFirst ? "camp first" : "flat first");
            assertNoDuplicates(g);
            assertSame(camp, site(g, facts.camp()), "a dormant site keeps its one record");
            assertSame(ward, site(g, facts.stronghold()));

            // The rewards already paid are not paid again; asking the camp again may only offer a new mission.
            float campTrust = camp.localReputation, wardTrust = ward.localReputation;
            meet(g, facts.camp());
            g.npcScreen.performSiteChoice(g, camp.resident(0).live, 10);
            assertEquals(campTrust, camp.localReputation, 1e-4f, "no report pays twice");
            meetCompound(g, facts.stronghold());
            assertFalse(g.npcScreen.performSiteChoice(g, ward.resident(0).live, 8), "the Wardens are grateful once");
            assertEquals(wardTrust, ward.localReputation, 1e-4f);

            List<String> now = durable(IntegratedCityFixture.describe(g, facts));
            Game again = load(save(g));
            assertEquals(now, durable(IntegratedCityFixture.describe(again, facts)), "and after another save and load");
        }
    }

    @Test
    void loadingOverAPopulatedLiveWorldReplacesItWholeAndMergesNothing() throws Exception {
        Path file = write(saved);
        // The same city, carried on: the half-done search finished, another door opened, more water drawn.
        Game same = load(saved);
        BuildingPlan flat = same.world.urban.buildingById(facts.flat());
        SurvivorWorldArena.standAt(same, facts.interrupted());
        assertTrue(same.openCrateAt(facts.interrupted()));
        SurvivorWorldArena.run(same, 4f);
        same.closeScreens();
        BuildingPlan.Fixture tap = UtilityWorldArena.basins(flat).getFirst();
        UtilityWorldArena.giveEmptySkins(same, 1);
        SurvivorWorldArena.standAt(same, tap.target());
        SurvivorWorldArena.pressF(same, tap.position());
        assertNotEquals(described, IntegratedCityFixture.describe(same, facts), "precondition: the live city moved on");
        // Another seed's economy city with a searched cupboard of its own, and a legacy world.
        Game other = IntegratedCityFixture.sessionOf(IntegratedCityFixture.SEED + 1);
        BuildingPlan refuge = UrbanStarterSite.of(other.world.urban).refuge();
        BuildingPlan.Fixture cupboard = RefugeWorldArena.piece(refuge, 0, BlockType.CABINET);
        SurvivorWorldArena.standAt(other, cupboard.target());
        assertTrue(other.openCrateAt(cupboard.position()));
        SurvivorWorldArena.run(other, 4f);
        other.closeScreens();
        Game legacy = new Game();
        legacy.newWorld(4_242L, true);
        legacy.player.inventory.add(ItemType.IRON_INGOT, 3);

        for (Game live : List.of(same, other, legacy)) {
            World before = live.world;
            Player player = live.player;
            assertTrue(SaveSystem.load(live, file));
            assertNotSame(before, live.world, "the live world is replaced, never merged into");
            assertNotSame(player, live.player);
            assertEquals(described, IntegratedCityFixture.describe(live, facts));
            assertEquals(core, coreDigest(live));
            assertSameSave(saved, save(live));
        }
    }

    @Test
    void theCityRegeneratesTheSameBlocksInAnyOrderAndAfterItsChunksAreDropped() throws Exception {
        List<long[]> area = new ArrayList<>();
        for (Vec3i cell : List.of(facts.looted(), facts.barricade(), facts.generator(), facts.wall())) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    long[] c = {Math.floorDiv(cell.x(), 16) + dx, Math.floorDiv(cell.z(), 16) + dz};
                    if (area.stream().noneMatch(a -> a[0] == c[0] && a[1] == c[1])) { area.add(c); }
                }
            }
        }
        Map<Long, String> forward = generate(load(saved), area);
        List<long[]> reversed = new ArrayList<>(area);
        Collections.reverse(reversed);
        assertEquals(forward, generate(load(saved), reversed), "the reverse order");
        List<long[]> shuffled = new ArrayList<>(area);
        Collections.shuffle(shuffled, new Random(25));
        Game dropped = load(saved);
        generate(dropped, shuffled);
        // First what retention may drop; then the chunks its pins keep (a running generator, people,
        // spoiling food), dropped directly as a load drops every chunk.
        Set<Long> pinned = dropped.streaming.retention.pinnedChunks();
        int unloaded = 0;
        for (long[] c : area) {
            if (!pinned.contains(World.key((int) c[0], (int) c[1])) && dropped.world.unloadChunk((int) c[0], (int) c[1])) {
                unloaded++;
            }
        }
        assertTrue(unloaded > 0, "retention may drop some of the fixture's chunks");
        Collections.shuffle(shuffled, new Random(2025));
        assertEquals(forward, generate(dropped, shuffled), "dropped by retention and generated again in another order");
        for (long[] c : area) { assertTrue(dropped.world.unloadChunk((int) c[0], (int) c[1])); }
        Collections.shuffle(shuffled, new Random(2026));
        assertEquals(forward, generate(dropped, shuffled), "every chunk dropped and generated again");
        assertEquals(described, IntegratedCityFixture.describe(dropped, facts), "with every fact intact");
        for (Map.Entry<Long, String> e : forward.entrySet()) {
            if (builtChunks.containsKey(e.getKey())) {
                assertEquals(builtChunks.get(e.getKey()), e.getValue(), "the world the facts were made in");
            }
        }
    }

    @Test
    void aNewWorldAfterTheIntegratedCityInheritsNothing() throws Exception {
        for (long seed : new long[]{IntegratedCityFixture.SEED, IntegratedCityFixture.SEED + 1}) {
            Game g = load(saved);
            g.newWorld(seed, true, IntegratedCityFixture.GENERATOR, GameMode.SURVIVAL);
            Game pristine = IntegratedCityFixture.sessionOf(seed);
            assertEquals(ledgers(pristine), ledgers(g), "seed " + seed);
            assertEquals(IntegratedCityFixture.describe(pristine, facts), IntegratedCityFixture.describe(g, facts));
            assertEquals(coreDigest(pristine), coreDigest(g));
            assertSameSave(save(pristine), save(g));
        }
    }

    @Test
    void aFailedLoadKeepsThePopulatedWorldAndTheLastGoodSaveAsTheyWere() throws Exception {
        Path good = write(saved);
        Game live = load(saved);
        World world = live.world;
        Player player = live.player;
        byte[] missions = SaveEnvelope.parse(saved).section("urban.missions").payload();
        for (byte[] bad : List.of(java.util.Arrays.copyOf(saved, saved.length - 1),
                SaveEnvelope.replace(saved, "urban.missions", java.util.Arrays.copyOf(missions, missions.length - 1)),
                SaveEnvelope.replace(saved, "urban.containers", null))) {
            assertFalse(SaveSystem.load(live, write(bad)));
            assertSame(world, live.world, "nothing is merged into the live world");
            assertSame(player, live.player);
            assertEquals(described, IntegratedCityFixture.describe(live, facts));
            assertEquals(core, coreDigest(live));
        }
        assertArrayEquals(saved, Files.readAllBytes(good), "a failed load never touches the last good save");
        assertSameSave(saved, save(live));
    }

    @Test
    void theOrderSectionsAppearInChangesNothingALoadRestores() throws Exception {
        SaveEnvelope.Parsed parsed = SaveEnvelope.parse(saved);
        List<List<SaveEnvelope.Section>> orders = new ArrayList<>();
        List<SaveEnvelope.Section> reversed = new ArrayList<>(parsed.sections());
        Collections.reverse(reversed);
        orders.add(reversed);
        Random random = new Random(25);
        for (int i = 0; i < 3; i++) {
            List<SaveEnvelope.Section> shuffled = new ArrayList<>(parsed.sections());
            Collections.shuffle(shuffled, random);
            orders.add(shuffled);
        }
        for (List<SaveEnvelope.Section> order : orders) {
            Game g = load(SaveEnvelope.rebuild(saved, parsed, order));
            String which = order.stream().map(SaveEnvelope.Section::id).toList().toString();
            assertEquals(described, IntegratedCityFixture.describe(g, facts), which);
            assertEquals(core, coreDigest(g), which);
            assertSameSave(saved, save(g));
        }
    }

    // ------------------------------------------------------------------
    // Visits
    // ------------------------------------------------------------------

    /** Far from every site and anchor of the fixture, long enough for the slow tick to put them to sleep. */
    private static void away(Game g) {
        SurvivorWorldArena.standAt(g, new Vec3i(facts.looted().x() + 900, 41, facts.looted().z() + 900));
        SurvivorWorldArena.run(g, 11f);
    }

    private static void visitFlat(Game g) {
        SurvivorWorldArena.standAt(g, g.world.urban.refuges.home().sleep);
        SurvivorWorldArena.run(g, 11f);
    }

    /** The camp and the hidden civilians wake again: every living resident gets back exactly one body. */
    private static void visitCamp(Game g) {
        for (long id : new long[]{facts.camp(), facts.hiddenRefuge()}) {
            UrbanSite s = meet(g, id);
            long alive = s.residents.stream().filter(r -> r.alive).count();
            assertEquals(alive, SurvivorWorldArena.live(s).size(), "awake again: " + s.plan.kind());
        }
    }

    private static void visitCompounds(Game g) {
        meetCompound(g, facts.yard());
        assertTrue(SurvivorWorldArena.live(site(g, facts.yard())).isEmpty(), "nobody comes back to the yard");
        meetCompound(g, facts.stronghold());
    }

    /** Beside the dead infected's and the dead Veilmaw's anchors: neither comes back. */
    private static void visitAnchors(Game g) {
        for (long id : new long[]{facts.deadInfected(), facts.deadAlien()}) {
            var anchor = com.veylon.world.urban.EncounterAnchors.resolve(g.world, id);
            EcologyWorldArena.observeAway(g, anchor);
            SurvivorWorldArena.run(g, 11f);
            assertTrue(g.entities.encounters.resident(id).dead(), "still dead");
            assertNull(g.entities.encounters.resident(id).live);
            assertNull(g.entities.encounters.resident(id).creature);
            if (id == facts.deadInfected()) {
                assertNull(g.entities.encounters.tryActivate(g, anchor), "no resurrection");
            } else {
                assertNull(g.entities.encounters.tryActivateCreature(g, anchor), "no resurrection");
            }
        }
    }

    private static UrbanSite meet(Game g, long site) {
        var plan = com.veylon.world.urban.SurvivorSites.resolve(g.world.urban, site);
        SurvivorWorldArena.standAt(g, plan.entry());
        SurvivorWorldArena.run(g, 11f);
        return site(g, site);
    }

    private static void meetCompound(Game g, long site) {
        var plan = com.veylon.world.urban.SurvivorSites.resolve(g.world.urban, site);
        for (int rz = -3; rz <= 3; rz++) {
            for (int rx = -3; rx <= 3; rx++) {
                for (var c : g.world.urban.sites(rx, rz).compounds()) {
                    if (c.site().id() == plan.id()) {
                        CompoundWorldArena.standFacing(g, c, CompoundWorldArena.street(c, 2), c.gate().outside());
                        SurvivorWorldArena.run(g, 11f);
                        return;
                    }
                }
            }
        }
        throw new AssertionError("no compound " + site);
    }

    private static UrbanSite site(Game g, long id) {
        return UrbanSiteDirector.registered(g.world, id);
    }

    /** No person is two bodies and no planned encounter has two. */
    private static void assertNoDuplicates(Game g) {
        Set<String> people = new java.util.HashSet<>();
        for (Npc n : g.entities.npcs) {
            if (n.dead) { continue; }
            if (n.settled()) { assertTrue(people.add(n.settlementId + ":" + n.residentIndex), "one body per resident"); }
            if (n.encounterId != 0) { assertTrue(people.add("e" + n.encounterId), "one body per encounter"); }
        }
        for (Creature c : g.entities.creatures) {
            if (c.encounterId != 0 && !c.dead) { assertTrue(people.add("e" + c.encounterId), "one body per encounter"); }
        }
    }

    /**
     * The described facts without what moves on with time alone: a running generator's fuel and wear,
     * a site's trust, fear and needs clock, and the pack (a revisit opens nothing, but the frame step
     * spoils food).
     */
    private static List<String> durable(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            if (line.startsWith("pack ")) { continue; }
            if (line.startsWith("device ")) { line = line.replaceAll(" fuel=\\S+ condition=\\S+", ""); }
            if (line.startsWith("site ")) { line = line.replaceAll(" trust=\\S+ fear=\\S+", "").replaceAll(" clock=\\S+", ""); }
            out.add(line);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Saves, digests and chunks
    // ------------------------------------------------------------------

    private Path write(byte[] bytes) throws Exception {
        Path file = Files.createTempFile(directory, "integrated-", ".sav");
        Files.write(file, bytes);
        return file;
    }

    private Game load(byte[] bytes) throws Exception {
        Game g = new Game();
        assertTrue(SaveSystem.load(g, write(bytes)), "the save loads");
        g.appState = Game.AppState.PLAYING;
        return g;
    }

    private byte[] save(Game g) throws Exception {
        Path file = Files.createTempFile(directory, "resaved-", ".sav");
        assertTrue(SaveSystem.save(g, file));
        return Files.readAllBytes(file);
    }

    /**
     * Two saves of the same state: the same length and every extension section byte for byte. The core's
     * maps iterate in hash order, which a load may change, so the core is compared through
     * {@link #coreDigest} instead.
     */
    static void assertSameSave(byte[] expected, byte[] actual) {
        assertEquals(expected.length, actual.length, "the same entries");
        SaveEnvelope.Parsed a = SaveEnvelope.parse(expected), b = SaveEnvelope.parse(actual);
        assertEquals(a.ids(), b.ids());
        for (int i = 0; i < a.sections().size(); i++) {
            assertArrayEquals(a.sections().get(i).payload(), b.sections().get(i).payload(), a.sections().get(i).id());
        }
    }

    /** What the core holds, in an order no map's iteration decides. */
    static List<String> coreDigest(Game g) {
        List<String> out = new ArrayList<>();
        World w = g.world;
        out.add("seed=" + w.seed + " generator=" + w.generatorVersion + " minutes=" + g.time.totalMinutes);
        out.add("weather=" + g.weather.current + ">" + g.weather.next + " " + g.weather.blend + " " + g.weather.changeTimer);
        Player p = g.player;
        out.add("player " + p.pos + " " + p.health + " " + p.hunger + " " + p.thirst + " " + p.stamina + " " + p.bodyTemp
                + " " + p.fatigue + " " + p.wetness + " " + p.hotbarSel + " " + p.afflictions + " " + p.blueprints);
        out.add("pack " + IntegratedCityFixture.contents(p.inventory) + " equipment " + java.util.Arrays.toString(p.equipment));
        w.changedBlocks.entrySet().stream().map(e -> "edit " + e.getKey() + "=" + e.getValue()).sorted().forEach(out::add);
        w.crateContents.entrySet().stream().map(e -> "crate " + e.getKey() + "=" + IntegratedCityFixture.contents(e.getValue()))
                .sorted().forEach(out::add);
        for (Npc n : g.entities.npcs) {
            out.add("npc " + n.name + " " + n.pos + " " + n.health + " " + n.archetype + " " + n.settlementId + " "
                    + n.residentIndex + " " + n.encounterId + " " + n.dead);
        }
        for (Creature c : g.entities.creatures) {
            out.add("creature " + c.type + " " + c.pos + " " + c.health + " " + c.encounterId);
        }
        g.entities.carcasses.forEach(c -> out.add("carcass " + c.type + " " + c.pos + " " + c.meatLeft + " " + c.decay));
        g.entities.corpses.forEach(c -> out.add("corpse " + c.pos));
        out.add("log " + g.eventLog.recent(30));
        return out;
    }

    /** Every ledger's size, and the people and encounters the world registered. */
    private static List<String> ledgers(Game g) {
        var u = g.world.urban;
        return List.of("containers=" + u.containers.size(), "claims=" + u.refuges.size(), "home=" + u.refuges.homeFloor(),
                "damaged=" + u.fortifications.damagedCount(), "braces=" + u.fortifications.braceCount(),
                "tapped=" + u.utilities.tappedCount(), "devices=" + u.utilities.deviceCount(),
                "missions=" + u.missions.missions().size(), "rumoured=" + u.missions.comms.rumoured,
                "discovered=" + u.discovery.size(), "visits=" + u.visits.districts() + u.visits.buildings(),
                "fixtures=" + g.world.fixtures.size(), "edits=" + g.world.changedBlocks.size(),
                "sites=" + UrbanSiteDirector.sites(g.world).size(), "encounters=" + g.entities.encounters.size());
    }

    private static Map<Long, String> generate(Game g, List<long[]> order) throws Exception {
        Map<Long, String> out = new java.util.TreeMap<>();
        for (long[] c : order) { g.world.getOrCreateChunk((int) c[0], (int) c[1]); }
        for (long[] c : order) { out.put(World.key((int) c[0], (int) c[1]), hash(g.world.getChunk((int) c[0], (int) c[1]))); }
        return out;
    }

    private static String hash(Chunk c) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (int y = 0; y < Chunk.SY; y++) {
            for (int z = 0; z < Chunk.SZ; z++) {
                for (int x = 0; x < Chunk.SX; x++) { digest.update(c.get(x, y, z).id()); }
            }
        }
        for (int h : c.heightMap) { digest.update((byte) h); }
        return HexFormat.of().formatHex(digest.digest());
    }
}
