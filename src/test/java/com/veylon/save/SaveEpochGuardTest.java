package com.veylon.save;

import com.veylon.Game;
import com.veylon.ai.Quest;
import com.veylon.combat.ProjectileSystem;
import com.veylon.entity.Affliction;
import com.veylon.entity.BodyFamily;
import com.veylon.entity.BodyFragment;
import com.veylon.entity.Carcass;
import com.veylon.entity.Creature;
import com.veylon.entity.GameMode;
import com.veylon.entity.HumanCorpse;
import com.veylon.entity.Npc;
import com.veylon.entity.Player;
import com.veylon.item.EquipSlot;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.settlement.CounterattackMission;
import com.veylon.settlement.HumanFaction;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementType;
import com.veylon.settlement.UrbanSite;
import com.veylon.simulation.EventSystem;
import com.veylon.simulation.WeatherSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.RackBatch;
import com.veylon.world.World;
import com.veylon.world.WorldFingerprints;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanContainers;
import com.veylon.world.urban.UrbanMissions;
import com.veylon.world.urban.UrbanRevision;
import com.veylon.world.urban.UrbanSpecies;
import com.veylon.world.urban.UrbanUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The guard of the save epoch (0.9.1 D-17). Two builds share an epoch only if each opens every save
 * the other writes, into the same world and state. What decides that is digested here, input by
 * input:
 *
 * <ul>
 *   <li>{@code layout.wilderness}, {@code layout.city}: a sample save of each world kind, written by
 *       {@link SaveSystem#save} from a fixed state, with the epoch masked. It carries the header, the
 *       core body with a record of each core list, the extension tail and every section with its
 *       version, and the city's sections as a new city writes them.</li>
 *   <li>{@code enums}: every enum table a save stores by ordinal, name, code or id, and the ids of
 *       the human factions and of each body family's pieces.</li>
 *   <li>{@code generation.wilderness}, {@code generation.city}: what each world kind's new-world
 *       generator makes from its seed, through the fingerprints {@code WorldGeneratorFingerprintTest}
 *       pins ({@link WorldFingerprints}): terrain and POIs, the column queries and the planned
 *       settlements of the wilderness; the city's plans, routes, ecology, encounter anchors, sites,
 *       compounds, wrecks and relay, and what its containers hold on their first search. Both list
 *       the generators a load accepts and the city revisions each of them names.</li>
 * </ul>
 *
 * <p>The current epoch's inputs are recorded in {@link #RECORDED}. While no release has shipped the
 * epoch, a change to any input fails this test with the new values to record: re-recording is
 * allowed, and the failure is the prompt to check that the change was meant. Once a release has
 * shipped the epoch ({@link #RELEASED}), any change fails with the instruction to raise
 * {@link SaveHeader#EPOCH}, because saves of the released epoch would otherwise open into another
 * world or state. Appending a constant to a stored enum counts as a change: a build of the same
 * epoch would meet a value it does not know. A released epoch's digest is never edited.
 */
class SaveEpochGuardTest {

    /**
     * The digest of each epoch a release has shipped, as {@link #digest} gave it when the release was
     * prepared. Milestone 09 adds epoch 1 when 0.9.1 is released. Never edit or remove an entry.
     */
    private static final Map<Integer, String> RELEASED = Map.of();

    /** The current epoch's inputs as last recorded. */
    private static final Map<String, String> RECORDED = Map.of(
            "enums", "b2c78dd0ff6223d61b2ae0e4fc5069ffbc2c525e7ec97ec472952f4199e6294e",
            "generation.city", "b6c505f9d1e284630f5e0410b0eb50a918c05a713953b8e30556d245c1ae81f8",
            "generation.wilderness", "f1f93ab3af72f72c56e872f4121969025b4712a055b046c8ede382c88530df29",
            "layout.city", "68e2c2392bc8293b47aa4a8e63255b792a353e3b3013eb3fdc997656b1dc55b5",
            "layout.wilderness", "de3c10b4c132ecc19ecc7febe18ef6f1af2a379b2d5e052680507e88e5f67b78");

    /** The seeds {@code WorldGeneratorFingerprintTest} pins. */
    private static final long[] SEEDS = {20_260_731L, -4_242L, 7L};
    private static final long SAMPLE_SEED = 20_261_119L;

    @TempDir
    Path directory;

    @Test
    void theCurrentEpochsInputsAreTheRecordedOnes() throws IOException {
        Map<String, String> inputs = inputs(directory);
        String verdict = verdict(SaveHeader.EPOCH, RECORDED, inputs, RELEASED);
        assertNull(verdict, verdict);
        System.out.println("[save-epoch] epoch " + SaveHeader.EPOCH + " digest " + digest(RECORDED)
                + (RELEASED.containsKey(SaveHeader.EPOCH) ? " (released)" : " (unreleased)"));
    }

    @Test
    void releasedEpochsAreNeverEditedNorOvertakenBackwards() {
        for (int released : RELEASED.keySet()) {
            assertTrue(released >= 1 && released <= SaveHeader.EPOCH,
                    "released epoch " + released + " is not at or below the build's epoch " + SaveHeader.EPOCH);
        }
        String shipped = RELEASED.get(SaveHeader.EPOCH);
        if (shipped != null) {
            assertEquals(shipped, digest(RECORDED), "epoch " + SaveHeader.EPOCH + " has shipped: its "
                    + "recorded inputs are never edited; a change needs a new epoch");
        }
    }

    @Test
    void theSampleSavesAreTheSameEveryTimeTheyAreWritten() throws IOException {
        assertEquals(sha256(masked(wildernessSample(directory, "a"))), sha256(masked(wildernessSample(directory, "b"))));
        assertEquals(sha256(masked(citySample(directory, "a"))), sha256(masked(citySample(directory, "b"))));
    }

    @Test
    void theVerdictSaysWhatAChangeNeeds() {
        Map<String, String> recorded = Map.of("enums", "a", "layout.city", "b");
        Map<String, String> moved = Map.of("enums", "a", "layout.city", "c");
        assertNull(verdict(1, recorded, recorded, Map.of()), "nothing changed");
        assertNull(verdict(1, recorded, recorded, Map.of(1, digest(recorded))), "nothing changed since the release");

        String unreleased = verdict(1, recorded, moved, Map.of());
        assertNotNull(unreleased);
        assertTrue(unreleased.contains("unreleased") && unreleased.contains("layout.city")
                && unreleased.contains("Re-record") && unreleased.contains("\"layout.city\", \"c\""), unreleased);

        String released = verdict(1, recorded, moved, Map.of(1, digest(recorded)));
        assertNotNull(released);
        assertTrue(released.contains("Raise SaveHeader.EPOCH to 2") && released.contains("layout.city")
                && released.contains("Never edit epoch 1"), released);

        String edited = verdict(1, moved, moved, Map.of(1, digest(recorded)));
        assertNotNull(edited);
        assertTrue(edited.contains("never edited"), edited);

        assertNull(verdict(2, moved, moved, Map.of(1, digest(recorded))),
                "a new epoch records its own inputs; the released one stays as it shipped");
    }

    // ------------------------------------------------------------------
    // The verdict
    // ------------------------------------------------------------------

    /**
     * What a change to the inputs of {@code epoch} needs, or null when nothing changed.
     *
     * @param recorded the inputs recorded for {@code epoch}
     * @param computed the inputs this build computes
     * @param released the digest of each epoch a release has shipped
     */
    static String verdict(int epoch, Map<String, String> recorded, Map<String, String> computed,
                          Map<Integer, String> released) {
        String shipped = released.get(epoch);
        if (shipped != null && !shipped.equals(digest(recorded))) {
            return "The recorded inputs of epoch " + epoch + " no longer give the digest its release "
                    + "recorded (" + shipped + "). A released epoch's record is never edited: restore it, "
                    + "and give a change a new epoch.";
        }
        Set<String> names = new TreeSet<>(recorded.keySet());
        names.addAll(computed.keySet());
        List<String> changed = new ArrayList<>();
        for (String name : names) {
            if (!Objects.equals(recorded.get(name), computed.get(name))) {
                changed.add(name);
            }
        }
        if (changed.isEmpty()) {
            return null;
        }
        List<String> entries = new ArrayList<>();
        for (Map.Entry<String, String> input : new TreeMap<>(computed).entrySet()) {
            entries.add("            \"" + input.getKey() + "\", \"" + input.getValue() + "\"");
        }
        String record = "\n" + String.join(",\n", entries);
        if (shipped != null) {
            return "Save-relevant inputs changed under epoch " + epoch + ", which a release has shipped: "
                    + changed + ". Saves of that epoch would open into another world or state. Raise "
                    + "SaveHeader.EPOCH to " + (epoch + 1) + " and record its inputs in RECORDED:" + record
                    + "\nNever edit epoch " + epoch + "'s entry in RELEASED.";
        }
        return "Save-relevant inputs changed while epoch " + epoch + " is unreleased: " + changed
                + ". Re-record them in RECORDED, which is allowed until a release ships the epoch:" + record
                + "\nIf the change was not meant to alter saves, find out why it did.";
    }

    /** One digest over every recorded input, the form {@link #RELEASED} keeps. */
    static String digest(Map<String, String> inputs) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> input : new TreeMap<>(inputs).entrySet()) {
            text.append(input.getKey()).append('=').append(input.getValue()).append('\n');
        }
        return sha256(text.toString().getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------
    // The inputs
    // ------------------------------------------------------------------

    static Map<String, String> inputs(Path directory) throws IOException {
        Map<String, String> inputs = new TreeMap<>();
        inputs.put("enums", sha256(enumTables().getBytes(StandardCharsets.UTF_8)));
        inputs.put("generation.city", sha256(cityGeneration().getBytes(StandardCharsets.UTF_8)));
        inputs.put("generation.wilderness", sha256(wildernessGeneration().getBytes(StandardCharsets.UTF_8)));
        inputs.put("layout.city", sha256(masked(citySample(directory, "city"))));
        inputs.put("layout.wilderness", sha256(masked(wildernessSample(directory, "wilderness"))));
        return inputs;
    }

    /** The epoch itself is no input to what decides it. */
    private static byte[] masked(byte[] save) {
        byte[] copy = save.clone();
        for (int i = 8; i < 12; i++) {
            copy[i] = 0;
        }
        return copy;
    }

    // ---- Enum tables ----

    /** Every table a save stores a constant of, by ordinal, name, code or id. */
    private static final List<Class<? extends Enum<?>>> STORED_ENUMS = List.of(
            BlockType.class, ItemType.class, EquipSlot.class, Affliction.class, WeatherSystem.Weather.class,
            EventSystem.EventType.class, Creature.CreatureType.class, Quest.Type.class, Quest.Status.class,
            NpcArchetype.class, SettlementType.class, Settlement.Alignment.class, Npc.PartyMission.class,
            Npc.PartyKind.class, CounterattackMission.Phase.class, CounterattackMission.Outcome.class,
            ProjectileSystem.Kind.class, GameMode.class, BodyFamily.class, BodyFragment.Piece.class,
            SurvivorSites.Kind.class, UrbanContainers.Owner.class, UrbanContainers.State.class,
            UrbanMissions.Kind.class, UrbanMissions.Status.class, UrbanMissions.Failure.class,
            UrbanSite.Relocation.class, UrbanSpecies.class, UrbanUtilities.Kind.class);

    static String enumTables() {
        StringBuilder text = new StringBuilder();
        for (Class<? extends Enum<?>> type : STORED_ENUMS) {
            text.append(type.getName()).append(':');
            for (Enum<?> constant : type.getEnumConstants()) {
                text.append(' ').append(constant.name()).append(stableField(type, constant, "code", '='))
                        .append(stableField(type, constant, "id", '#'));
            }
            text.append('\n');
        }
        for (BodyFamily family : BodyFamily.values()) {
            text.append("pieces ").append(family.name()).append(':');
            family.anatomy().pieces.forEach(piece -> text.append(' ').append(piece.name));
            text.append('\n');
        }
        text.append("factions: ").append(String.join(" ", HumanFaction.ALL)).append('\n');
        return text.toString();
    }

    /** The value of a constant's {@code code} or {@code id} field, which a section stores instead of the ordinal. */
    private static String stableField(Class<?> type, Enum<?> constant, String name, char mark) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return mark + String.valueOf(field.get(constant));
        } catch (NoSuchFieldException absent) {
            return "";
        } catch (IllegalAccessException impossible) {
            throw new AssertionError(impossible);
        }
    }

    // ---- Generation ----

    static String wildernessGeneration() {
        int generator = World.WILDERNESS_NEW_WORLD_GENERATOR;
        StringBuilder text = new StringBuilder(loadableGenerators());
        text.append("wilderness generator ").append(generator).append('\n');
        for (long seed : SEEDS) {
            text.append(seed).append(" terrain ").append(WorldFingerprints.terrain(seed, generator))
                    .append(" columns ").append(WorldFingerprints.columnQueries(seed, generator)).append('\n');
            World world = new World(seed, generator);
            for (int rz = -3; rz <= 3; rz++) {
                for (int rx = -3; rx <= 3; rx++) {
                    Settlement s = world.settlementForRegion(rx, rz);
                    if (s == null) {
                        continue;
                    }
                    text.append(seed).append(" settlement ").append(rx).append(',').append(rz).append(' ')
                            .append(s.id).append(' ').append(s.type).append(' ').append(s.alignment).append(' ')
                            .append(s.factionId).append(' ').append(s.foodStock).append('/').append(s.woodStock)
                            .append('/').append(s.medStock).append('/').append(s.metalStock).append(" [");
                    for (Settlement.Resident r : s.residents) {
                        text.append(' ').append(r.name).append('/').append(r.archetype.id).append('/')
                                .append(r.bedIndex).append('/').append(r.dutyIndex);
                    }
                    text.append(" ]\n");
                }
            }
        }
        return text.toString();
    }

    static String cityGeneration() {
        int generator = World.DEFAULT_NEW_WORLD_GENERATOR;
        StringBuilder text = new StringBuilder(loadableGenerators());
        text.append("city generator ").append(generator).append('\n');
        for (long seed : SEEDS) {
            text.append(seed)
                    .append(" terrain ").append(WorldFingerprints.terrain(seed, generator))
                    .append(" streets ").append(WorldFingerprints.streetPlans(seed, generator))
                    .append(" buildings ").append(WorldFingerprints.buildingPlans(seed, generator))
                    .append(" furnished ").append(WorldFingerprints.furnishedBuildings(seed, generator))
                    .append(" districts ").append(WorldFingerprints.districts(seed, generator))
                    .append(" routes ").append(WorldFingerprints.routes(seed, generator))
                    .append(" ecology ").append(WorldFingerprints.ecologyPlans(seed, generator))
                    .append(" anchors ").append(WorldFingerprints.ecologyAnchors(seed, generator))
                    .append(" sites ").append(WorldFingerprints.survivorSites(seed, generator))
                    .append(" compounds ").append(WorldFingerprints.compounds(seed, generator))
                    .append(" compound-chunks ").append(WorldFingerprints.compoundChunks(seed, generator))
                    .append(" wrecks ").append(WorldFingerprints.vehicles(seed, generator))
                    .append(" wreck-chunks ").append(WorldFingerprints.vehicleChunks(seed, generator))
                    .append(" relay ").append(WorldFingerprints.relay(seed, generator))
                    .append(" relay-chunk ").append(WorldFingerprints.relayChunk(seed, generator))
                    .append(" first-searches ").append(firstSearches(seed, generator))
                    .append('\n');
        }
        return text.toString();
    }

    /**
     * What every container of three regions holds when it is first searched: its table and the roll,
     * starter reservation or stash the game would put in it. Rolled, not stamped, so no terrain
     * fingerprint sees it.
     */
    private static long firstSearches(long seed, int generator) {
        World world = new World(seed, generator);
        world.urban.resolveContentRevision(UrbanRevision.contentFor(generator));
        long hash = 0xcbf29ce484222325L;
        for (int[] region : new int[][]{{0, 0}, {-1, -1}, {1, -1}}) {
            for (ParcelPlan parcel : world.urban.regionPlan(region[0], region[1]).parcels()) {
                BuildingPlan building = world.urban.buildingAt(parcel.x0(), parcel.z0());
                if (building == null) {
                    continue;
                }
                for (BuildingPlan.Fixture fixture : building.fixtures()) {
                    if (fixture.kind() != BuildingPlan.FixtureKind.CONTAINER) {
                        continue;
                    }
                    UrbanContainers.Planned plan = UrbanContainers.planned(world, fixture.position());
                    if (plan == null) {
                        continue;
                    }
                    hash = WorldFingerprints.mix(hash, fixture.id());
                    hash = WorldFingerprints.mix(hash, plan.table().ordinal());
                    Inventory held = UrbanContainers.firstRoll(world, plan);
                    for (int slot = 0; held != null && slot < held.size(); slot++) {
                        ItemStack stack = held.get(slot);
                        if (stack != null) {
                            hash = WorldFingerprints.mix(hash, stack.type.ordinal() * 1_000_003L + stack.count);
                            hash = WorldFingerprints.mix(hash, Float.floatToIntBits(stack.durability));
                            hash = WorldFingerprints.mix(hash, Float.floatToIntBits(stack.freshness));
                            hash = WorldFingerprints.mix(hash, stack.charge);
                        }
                    }
                }
            }
        }
        return hash;
    }

    /** The generators a load accepts, and the layout, template and content revision each city names. */
    private static String loadableGenerators() {
        StringBuilder text = new StringBuilder("loadable");
        for (int generator = 0; generator <= World.MAX_SUPPORTED_GENERATOR + 8; generator++) {
            if (!World.isSupportedGenerator(generator)) {
                continue;
            }
            text.append(' ').append(generator);
            if (World.isUrbanGenerator(generator)) {
                text.append('(').append(UrbanRevision.layoutRevisionOf(generator)).append('/')
                        .append(UrbanRevision.buildingTemplateRevisionOf(generator)).append('/')
                        .append(UrbanRevision.contentFor(generator)).append(')');
            }
        }
        return text.append('\n').toString();
    }

    // ---- Sample saves ----

    /**
     * A wilderness save holding one record of every core list, a lit lantern, a lit keg, a quest and
     * one planned settlement with a trespass cooldown. What a new world's start decides (the kit, the
     * clock, the player's state) is set to fixed values first, so the sample moves with the layout,
     * not with gameplay tuning.
     */
    static byte[] wildernessSample(Path directory, String name) throws IOException {
        Game g = new Game();
        g.newWorld(SAMPLE_SEED, true, World.WILDERNESS_NEW_WORLD_GENERATOR);
        stageCore(g);
        World w = g.world;
        Player p = g.player;

        Vec3i lantern = new Vec3i((int) Math.floor(p.pos.x) + 2, (int) Math.floor(p.pos.y) + 2,
                (int) Math.floor(p.pos.z));
        w.setBlock(lantern.x(), lantern.y(), lantern.z(), BlockType.LANTERN, true);
        assertTrue(w.restoreLanternState(lantern, 300f, true), "precondition: the lantern stands");
        w.changedBlocks.clear();
        w.changedBlocks.put(new Vec3i(3, 41, 3), BlockType.PLANK.id());
        w.campfireFuel.clear();
        w.campfireFuel.put(new Vec3i(4, 41, 4), 120f);
        w.crateContents.clear();
        Inventory crate = new Inventory(12);
        crate.set(0, stack(ItemType.BANDAGE, 3));
        w.crateContents.put(new Vec3i(5, 41, 5), crate);
        w.rackBatches.clear();
        RackBatch batch = new RackBatch(ItemType.RAW_MEAT, 2);
        batch.progress = 0.5f;
        w.rackBatches.put(new Vec3i(6, 41, 6), batch);
        w.collectorWater.clear();
        w.collectorWater.put(new Vec3i(7, 41, 7), 0.75f);
        w.discoveredPois.clear();
        w.discoveredPois.add(new Vec3i(8, 41, 8));
        w.beaconPos = new Vec3i(9, 41, 9);
        w.beaconStage = 1;
        w.factionReputation.clear();
        w.factionReputation.put(HumanFaction.HEADHUNTERS, -12f);
        w.factionBounty.clear();
        w.factionBounty.put(HumanFaction.HEADHUNTERS, 4f);
        w.kegFuses.clear();
        w.kegFusePlayerAttribution.clear();
        Vec3i keg = new Vec3i(10, 41, 10);
        w.kegFuses.put(keg, 2.5f);
        w.kegFusePlayerAttribution.put(keg, true);
        w.gateTimers.clear();
        w.gateTimers.put(new Vec3i(11, 41, 11), 1.5f);

        Settlement kept = null;
        for (int rx = -6; rx <= 6 && kept == null; rx++) {
            for (int rz = -6; rz <= 6 && kept == null; rz++) {
                kept = w.settlementForRegion(rx, rz);
            }
        }
        assertNotNull(kept, "precondition: a settlement is planned near the spawn");
        long keptId = kept.id;
        w.settlements.keySet().removeIf(id -> id != keptId);
        kept.trespassCooldown = 5f;

        g.entities.npcs.clear();
        Npc n = g.entities.spawnNpc(w, "Sample", 12.5f, 41f, 12.5f);
        n.yaw = 90f;
        n.health = 25f;
        n.hunger = 30f;
        n.mood = 40f;
        n.isTrader = false;
        n.raider = true;
        n.sick = false;
        n.sickTimer = 0f;
        n.campIndex = 1;
        n.leaveTimer = 0f;
        n.faction = null;
        n.archetype = NpcArchetype.SCAVENGER;
        n.settlementId = keptId;
        n.residentIndex = 0;
        n.loadedAmmo = 0;
        n.warParty = false;
        n.partyFactionId = HumanFaction.HEADHUNTERS;
        n.originSettlementId = keptId;
        n.partyMission = Npc.PartyMission.values()[0];
        n.partyDestination.set(20.5f, 41f, 20.5f);
        n.partyMissionTimer = 15f;
        n.partyContact = false;
        n.partyKind = Npc.PartyKind.PATROL;
        n.partyMissionId = "";
        n.partyMemberId = "";
        n.partyTargetSettlementId = 0;

        g.entities.creatures.clear();
        Creature deer = g.entities.spawnCreature(w, Creature.CreatureType.DEER, 14.5f, 41f, 14.5f);
        deer.health = 20f;
        deer.hunger = 10f;
        deer.bleedTimer = 0f;
        g.entities.carcasses.clear();
        Carcass hare = new Carcass(Creature.CreatureType.HARE, 15.5f, 41f, 15.5f);
        hare.meatLeft = 1;
        hare.hideLeft = 1;
        hare.decay = 90f;
        g.entities.carcasses.add(hare);
        g.entities.corpses.clear();
        HumanCorpse corpse = new HumanCorpse(16.5f, 41f, 16.5f);
        corpse.decay = 100f;
        g.entities.corpses.add(corpse);
        g.fragments.reset();
        g.events.active.clear();
        g.events.active.add(new EventSystem.ActiveEvent(EventSystem.EventType.values()[0], 120f, 0.5f));
        return bytesOf(g, directory, "wilderness-" + name + ".sav");
    }

    /**
     * A new city's save, its start fixed as in the wilderness sample, with one container of the
     * starter refuge searched open: a row in {@code urban.containers} and what the core holds for it.
     */
    static byte[] citySample(Path directory, String name) throws IOException {
        Game g = new Game();
        g.newWorld(SAMPLE_SEED, true, World.DEFAULT_NEW_WORLD_GENERATOR);
        stageCore(g);
        BuildingPlan refuge = g.world.urban.buildingById(g.world.urban.refugeBuilding);
        BuildingPlan.Fixture cupboard = refuge.fixtures().stream()
                .filter(f -> f.kind() == BuildingPlan.FixtureKind.CONTAINER)
                .min(Comparator.comparingLong(BuildingPlan.Fixture::id)).orElseThrow();
        Vec3i at = cupboard.position();
        g.world.getOrCreateChunk(Math.floorDiv(at.x(), 16), Math.floorDiv(at.z(), 16));
        assertNotNull(g.world.urban.containers.touch(g.world, at), "precondition: the refuge's container opens");
        return bytesOf(g, directory, "city-" + name + ".sav");
    }

    private static void stageCore(Game g) {
        g.time.totalMinutes = 600.0;
        WeatherSystem.Weather[] weathers = WeatherSystem.Weather.values();
        g.weather.current = weathers[0];
        g.weather.next = weathers[weathers.length - 1];
        g.weather.blend = 0.5f;
        g.weather.changeTimer = 30f;
        g.camera.yaw = 30f;
        g.camera.pitch = -10f;
        Player p = g.player;
        p.health = 80f;
        p.hunger = 70f;
        p.thirst = 60f;
        p.stamina = 50f;
        p.bodyTemp = 37f;
        p.fatigue = 10f;
        p.wetness = 0.25f;
        p.protein = 40f;
        p.vitamins = 45f;
        p.smokeExposure = 0.5f;
        p.woundClean = true;
        p.hotbarSel = 2;
        p.afflictions.clear();
        p.afflictions.put(Affliction.values()[0], 30f);
        p.blueprints.clear();
        p.blueprints.add("rain_cloak");
        for (int i = 0; i < p.inventory.size(); i++) {
            p.inventory.set(i, null);
        }
        p.inventory.set(0, stack(ItemType.SPEAR, 1));
        p.inventory.set(1, stack(ItemType.BANDAGE, 3));
        for (int i = 0; i < p.equipment.length; i++) {
            p.equipment[i] = null;
        }
        p.equipment[0] = stack(ItemType.FUR_HOOD, 1);
        g.faction.trust = 40f;
        g.faction.alert = 5f;
        g.faction.foodStock = 10;
        g.faction.woodStock = 8;
        g.faction.hostile = false;
        g.faction.upgradeStage = 1;
        g.faction.alliedGiftGiven = true;
        g.faction.quest = new Quest(Quest.Type.FETCH, ItemType.BANDAGE, 3, 600f, 5, ItemType.MEDICINE, 1,
                "Sample giver").bind("quest:sample:1", "camp", Quest.NO_SETTLEMENT, "camp");
        g.eventLog.clear();
        g.eventLog.add("[D1 10:00] The save epoch's sample.");
    }

    /** A stack with fixed durability and freshness, so no item table's defaults reach the sample. */
    private static ItemStack stack(ItemType type, int count) {
        ItemStack stack = new ItemStack(type, count);
        stack.durability = type.hasDurability() ? 20f : -1f;
        stack.freshness = type.spoils() ? 50f : -1f;
        stack.charge = 0;
        return stack;
    }

    private static byte[] bytesOf(Game g, Path directory, String name) throws IOException {
        Path file = directory.resolve(name);
        assertTrue(SaveSystem.save(g, file), "precondition: the sample saves");
        byte[] bytes = Files.readAllBytes(file);
        Files.delete(file);
        return bytes;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
