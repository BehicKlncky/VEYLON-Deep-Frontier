package com.veylon.save;

import com.veylon.Game;
import com.veylon.ai.Quest;
import com.veylon.combat.ExplosionSystem;
import com.veylon.combat.ProjectileSystem;
import com.veylon.entity.Affliction;
import com.veylon.entity.Carcass;
import com.veylon.entity.Creature;
import com.veylon.entity.Npc;
import com.veylon.item.EquipSlot;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.CounterattackDirector;
import com.veylon.settlement.CounterattackMission;
import com.veylon.settlement.HumanFaction;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementManager;
import com.veylon.settlement.SettlementType;
import com.veylon.simulation.EventSystem;
import com.veylon.simulation.WeatherSystem;
import com.veylon.util.AppPaths;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Poi;
import com.veylon.world.RackBatch;
import com.veylon.world.World;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Binary save format v3. Stores the seed, the world-generator version, every
 * delta from the generated world (changed blocks, structures, item state,
 * equipment, afflictions, blueprints, racks, collectors, POI discovery,
 * beacon progress, faction quests/upgrades, NPCs, creatures, carcasses,
 * weather, time, active events) plus the 0.3.0 expansion state: settlements
 * (alignment, stocks, capture, residents), per-faction reputation/bounty,
 * NPC residency/loadout, lit powder-keg fuses and in-flight fused throwables.
 *
 * <p><b>v2 saves still load</b>: an explicit migration path reads the old
 * layout, pins the world to the legacy terrain generator (no settlements or
 * deep caves — those need a new world) and defaults all new state.</p>
 *
 * <p>Version 1 saves (pre-overhaul prototype) are not migrated; loading one
 * fails with a clear console message and the game keeps running.</p>
 */
public final class SaveSystem {

    private static final int MAGIC = 0x5645594C; // "VEYL"
    private static final int VERSION = 3;
    private static final int MIN_SUPPORTED = 2;
    /** Optional tail added to v3 without invalidating already-shipped v3 files. */
    private static final int V3_EXTENSION_MAGIC = 0x57334558; // "W3EX"
    private static final int V3_EXTENSION_VERSION = 2;
    private static final int MAX_SERIALIZED_ENTRIES = 100_000;
    private static final int MAX_V3_EXTENSION_SECTIONS = 256;
    private static final int MAX_V3_SECTION_BYTES = 16 * 1024 * 1024;
    private static final int V3_SECTION_ENVELOPE_MAGIC = 0x53334543; // "S3EC"
    private static final String V3_SECTION_LANTERNS = "world.lanterns";
    private static final int LANTERN_SECTION_VERSION = 1;
    private static final String V3_SECTION_PARTY_STATE = "npc.party-state";
    private static final int PARTY_STATE_SECTION_VERSION = 1;
    private static final String V3_SECTION_COUNTERATTACKS = "missions.counterattacks";
    private static final int COUNTERATTACK_SECTION_VERSION = 1;
    private static final String V3_SECTION_QUEST_TARGET = "quest.target-state";
    private static final int QUEST_TARGET_SECTION_VERSION = 1;
    private static final String V3_SECTION_REPUTATION_ACTIONS = "settlement.reputation-state";
    private static final int REPUTATION_ACTIONS_SECTION_VERSION = 1;
    private static final String V3_SECTION_ACTIVE_EXPLOSIVES = "combat.active-explosives";
    private static final int ACTIVE_EXPLOSIVES_SECTION_VERSION = 1;
    private static final String V3_SECTION_KEG_FUSE_ATTRIBUTION =
            "combat.keg-fuse-attribution";
    private static final int KEG_FUSE_ATTRIBUTION_SECTION_VERSION = 1;

    public static final Path SAVE_PATH = AppPaths.dataDirectory().resolve("saves/veylon.sav");

    private SaveSystem() {
    }

    public static boolean save(Game g) {
        return save(g, SAVE_PATH);
    }

    public static boolean save(Game g, Path savePath) {
        Objects.requireNonNull(savePath, "savePath");
        try {
            Path parent = savePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(savePath)))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeLong(g.world.seed);
                out.writeInt(g.world.generatorVersion);
                out.writeDouble(g.time.totalMinutes);

                // Weather.
                out.writeInt(g.weather.current.ordinal());
                out.writeInt(g.weather.next.ordinal());
                out.writeFloat(g.weather.blend);
                out.writeFloat(g.weather.changeTimer);

                // Player.
                var p = g.player;
                out.writeFloat(p.pos.x);
                out.writeFloat(p.pos.y);
                out.writeFloat(p.pos.z);
                out.writeFloat(g.camera.yaw);
                out.writeFloat(g.camera.pitch);
                out.writeFloat(p.health);
                out.writeFloat(p.hunger);
                out.writeFloat(p.thirst);
                out.writeFloat(p.stamina);
                out.writeFloat(p.bodyTemp);
                out.writeFloat(p.fatigue);
                out.writeFloat(p.wetness);
                out.writeFloat(p.protein);
                out.writeFloat(p.vitamins);
                out.writeFloat(p.smokeExposure);
                out.writeBoolean(p.woundClean);
                out.writeInt(p.hotbarSel);

                // Afflictions.
                out.writeInt(p.afflictions.size());
                for (var e : p.afflictions.entrySet()) {
                    out.writeInt(e.getKey().ordinal());
                    out.writeFloat(e.getValue());
                }

                // Blueprints.
                out.writeInt(p.blueprints.size());
                for (String id : p.blueprints) {
                    out.writeUTF(id);
                }

                writeInventory(out, p.inventory);
                for (int i = 0; i < EquipSlot.values().length; i++) {
                    writeStack(out, p.equipment[i]);
                }

                // Changed blocks.
                out.writeInt(g.world.changedBlocks.size());
                for (Map.Entry<Vec3i, Byte> e : g.world.changedBlocks.entrySet()) {
                    writeVec(out, e.getKey());
                    out.writeByte(e.getValue());
                }

                // Campfire fuel.
                out.writeInt(g.world.campfireFuel.size());
                for (Map.Entry<Vec3i, Float> e : g.world.campfireFuel.entrySet()) {
                    writeVec(out, e.getKey());
                    out.writeFloat(e.getValue());
                }

                // Crates.
                out.writeInt(g.world.crateContents.size());
                for (Map.Entry<Vec3i, Inventory> e : g.world.crateContents.entrySet()) {
                    writeVec(out, e.getKey());
                    writeInventory(out, e.getValue());
                }

                // Drying racks.
                out.writeInt(g.world.rackBatches.size());
                for (Map.Entry<Vec3i, RackBatch> e : g.world.rackBatches.entrySet()) {
                    writeVec(out, e.getKey());
                    out.writeInt(e.getValue().input.ordinal());
                    out.writeInt(e.getValue().count);
                    out.writeFloat(e.getValue().progress);
                }

                // Rain collectors.
                out.writeInt(g.world.collectorWater.size());
                for (Map.Entry<Vec3i, Float> e : g.world.collectorWater.entrySet()) {
                    writeVec(out, e.getKey());
                    out.writeFloat(e.getValue());
                }

                // Discovered POIs.
                out.writeInt(g.world.discoveredPois.size());
                for (Vec3i pos : g.world.discoveredPois) {
                    writeVec(out, pos);
                }

                // Beacon.
                out.writeBoolean(g.world.beaconPos != null);
                if (g.world.beaconPos != null) {
                    writeVec(out, g.world.beaconPos);
                }
                out.writeInt(g.world.beaconStage);

                // Faction.
                out.writeFloat(g.faction.trust);
                out.writeFloat(g.faction.alert);
                out.writeInt(g.faction.foodStock);
                out.writeInt(g.faction.woodStock);
                out.writeBoolean(g.faction.hostile);
                out.writeInt(g.faction.upgradeStage);
                out.writeBoolean(g.faction.alliedGiftGiven);

                // Active quest.
                Quest q = g.faction.quest;
                out.writeBoolean(q != null);
                if (q != null) {
                    out.writeInt(q.type.ordinal());
                    out.writeInt(q.item == null ? -1 : q.item.ordinal());
                    out.writeInt(q.required);
                    out.writeInt(q.progress);
                    out.writeFloat(q.timeLeft);
                    out.writeInt(q.trustReward);
                    out.writeInt(q.rewardItem == null ? -1 : q.rewardItem.ordinal());
                    out.writeInt(q.rewardCount);
                    out.writeUTF(q.giverName);
                }

                // NPCs (v3 appends residency/loadout fields per entry).
                List<Npc> npcs = g.entities.npcs;
                out.writeInt(npcs.size());
                for (Npc n : npcs) {
                    out.writeUTF(n.name);
                    out.writeFloat(n.pos.x);
                    out.writeFloat(n.pos.y);
                    out.writeFloat(n.pos.z);
                    out.writeFloat(n.yaw);
                    out.writeFloat(n.health);
                    out.writeFloat(n.hunger);
                    out.writeFloat(n.mood);
                    out.writeBoolean(n.isTrader);
                    out.writeBoolean(n.raider);
                    out.writeBoolean(n.sick);
                    out.writeFloat(n.sickTimer);
                    out.writeInt(n.campIndex);
                    out.writeFloat(n.leaveTimer);
                    out.writeBoolean(n.faction != null);
                    out.writeUTF(n.archetype == null ? "" : n.archetype.id);
                    out.writeLong(n.settlementId);
                    out.writeInt(n.residentIndex);
                    out.writeInt(n.loadedAmmo);
                    out.writeBoolean(n.warParty);
                }

                // Creatures.
                List<Creature> creatures = g.entities.creatures;
                out.writeInt(creatures.size());
                for (Creature c : creatures) {
                    out.writeInt(c.type.ordinal());
                    out.writeFloat(c.pos.x);
                    out.writeFloat(c.pos.y);
                    out.writeFloat(c.pos.z);
                    out.writeFloat(c.health);
                    out.writeFloat(c.hunger);
                    out.writeFloat(c.bleedTimer);
                }

                // Carcasses.
                out.writeInt(g.entities.carcasses.size());
                for (Carcass c : g.entities.carcasses) {
                    out.writeInt(c.type.ordinal());
                    out.writeFloat(c.pos.x);
                    out.writeFloat(c.pos.y);
                    out.writeFloat(c.pos.z);
                    out.writeInt(c.meatLeft);
                    out.writeInt(c.hideLeft);
                    out.writeFloat(c.decay);
                }

                // Active events.
                out.writeInt(g.events.active.size());
                for (EventSystem.ActiveEvent e : g.events.active) {
                    out.writeInt(e.type.ordinal());
                    out.writeFloat(e.remaining);
                    out.writeFloat(e.severity);
                }

                // Event log tail.
                List<String> log = g.eventLog.recent(30);
                out.writeInt(log.size());
                for (String s : log) {
                    out.writeUTF(s);
                }

                // ---- v3 sections ----
                writeSettlements(out, g);
                writeFactionState(out, g);

                // Lit powder-keg fuses (so an armed keg survives save/load).
                if (g.world.kegFuses.size() > ExplosionSystem.MAX_ACTIVE_FUSES) {
                    throw new IOException("active powder-keg fuse limit exceeded: "
                            + g.world.kegFuses.size());
                }
                out.writeInt(g.world.kegFuses.size());
                for (Map.Entry<Vec3i, Float> e : g.world.kegFuses.entrySet()) {
                    writeVec(out, e.getKey());
                    out.writeFloat(e.getValue());
                }
                writeV3Extension(out, g, npcs);
            }
            return true;
        } catch (IOException ex) {
            System.err.println("Save failed: " + ex);
            return false;
        }
    }

    public static boolean load(Game g) {
        return load(g, SAVE_PATH);
    }

    public static boolean load(Game g, Path savePath) {
        Objects.requireNonNull(savePath, "savePath");
        if (!Files.exists(savePath)) {
            return false;
        }
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(savePath)))) {
            if (in.readInt() != MAGIC) {
                System.err.println("Save file has wrong magic");
                return false;
            }
            int version = in.readInt();
            if (version < MIN_SUPPORTED || version > VERSION) {
                System.err.println("Save is version " + version + "; this build reads versions "
                        + MIN_SUPPORTED + "-" + VERSION
                        + ". Old prototype saves can't be migrated - start a new world.");
                return false;
            }
            long seed = in.readLong();
            // v2 worlds keep their legacy terrain forever; v3 records the version.
            int generatorVersion = version >= 3 ? in.readInt() : com.veylon.world.World.GEN_LEGACY;
            g.newWorld(seed, false, generatorVersion);
            if (version == 2) {
                System.out.println("[save] migrating v2 save: legacy terrain preserved; "
                        + "settlements and deep caves need a new world.");
            }

            g.time.totalMinutes = in.readDouble();

            WeatherSystem.Weather[] weathers = WeatherSystem.Weather.values();
            g.weather.current = weathers[in.readInt()];
            g.weather.next = weathers[in.readInt()];
            g.weather.blend = in.readFloat();
            g.weather.changeTimer = in.readFloat();

            var p = g.player;
            p.pos.set(in.readFloat(), in.readFloat(), in.readFloat());
            g.camera.yaw = in.readFloat();
            g.camera.pitch = in.readFloat();
            p.health = in.readFloat();
            p.hunger = in.readFloat();
            p.thirst = in.readFloat();
            p.stamina = in.readFloat();
            p.bodyTemp = in.readFloat();
            p.fatigue = in.readFloat();
            p.wetness = in.readFloat();
            p.protein = in.readFloat();
            p.vitamins = in.readFloat();
            p.smokeExposure = in.readFloat();
            p.woundClean = in.readBoolean();
            p.hotbarSel = in.readInt();

            p.afflictions.clear();
            int nAfflictions = in.readInt();
            Affliction[] afflictionTypes = Affliction.values();
            for (int i = 0; i < nAfflictions; i++) {
                int ord = in.readInt();
                float secs = in.readFloat();
                if (ord >= 0 && ord < afflictionTypes.length) {
                    p.afflictions.put(afflictionTypes[ord], secs);
                }
            }

            p.blueprints.clear();
            int nBlueprints = in.readInt();
            for (int i = 0; i < nBlueprints; i++) {
                p.blueprints.add(in.readUTF());
            }

            readInventory(in, p.inventory, version);
            for (int i = 0; i < EquipSlot.values().length; i++) {
                p.equipment[i] = readStack(in, version);
            }

            // Changed blocks: ensure target chunks exist, then apply.
            int nBlocks = in.readInt();
            List<int[]> blocks = new ArrayList<>(nBlocks);
            for (int i = 0; i < nBlocks; i++) {
                blocks.add(new int[]{in.readInt(), in.readInt(), in.readInt(), in.readByte()});
            }
            for (int[] b : blocks) {
                g.world.getOrCreateChunk(Math.floorDiv(b[0], 16), Math.floorDiv(b[2], 16));
                g.world.setBlock(b[0], b[1], b[2], BlockType.byId((byte) b[3]), false);
                g.world.changedBlocks.put(new Vec3i(b[0], b[1], b[2]), (byte) b[3]);
            }

            // Campfire fuel.
            g.world.campfireFuel.clear();
            int nFires = in.readInt();
            for (int i = 0; i < nFires; i++) {
                g.world.campfireFuel.put(readVec(in), in.readFloat());
            }

            // Crates.
            Map<Vec3i, Inventory> crates = new HashMap<>();
            int nCrates = in.readInt();
            for (int i = 0; i < nCrates; i++) {
                Vec3i pos = readVec(in);
                Inventory inv = new Inventory(12);
                readInventory(in, inv, version);
                crates.put(pos, inv);
            }
            g.world.crateContents.clear();
            g.world.crateContents.putAll(crates);

            // Drying racks.
            g.world.rackBatches.clear();
            int nRacks = in.readInt();
            ItemType[] itemTypes = ItemType.values();
            for (int i = 0; i < nRacks; i++) {
                Vec3i pos = readVec(in);
                int ord = in.readInt();
                int count = in.readInt();
                float progress = in.readFloat();
                if (ord >= 0 && ord < itemTypes.length) {
                    RackBatch batch = new RackBatch(itemTypes[ord], count);
                    batch.progress = progress;
                    g.world.rackBatches.put(pos, batch);
                }
            }

            // Rain collectors.
            g.world.collectorWater.clear();
            int nCollectors = in.readInt();
            for (int i = 0; i < nCollectors; i++) {
                g.world.collectorWater.put(readVec(in), in.readFloat());
            }

            // Discovered POIs (re-flag the ones regenerated with the world).
            g.world.discoveredPois.clear();
            int nDiscovered = in.readInt();
            for (int i = 0; i < nDiscovered; i++) {
                g.world.discoveredPois.add(readVec(in));
            }
            for (Poi poi : g.world.pois) {
                poi.discovered = g.world.discoveredPois.contains(poi.pos);
            }

            // Beacon.
            if (in.readBoolean()) {
                g.world.beaconPos = readVec(in);
            } else {
                g.world.beaconPos = null;
            }
            g.world.beaconStage = in.readInt();

            // Faction.
            g.faction.trust = in.readFloat();
            g.faction.alert = in.readFloat();
            g.faction.foodStock = in.readInt();
            g.faction.woodStock = in.readInt();
            g.faction.hostile = in.readBoolean();
            g.faction.upgradeStage = in.readInt();
            g.faction.alliedGiftGiven = in.readBoolean();

            if (in.readBoolean()) {
                Quest.Type[] questTypes = Quest.Type.values();
                int typeOrd = in.readInt();
                int itemOrd = in.readInt();
                int required = in.readInt();
                int progress = in.readInt();
                float timeLeft = in.readFloat();
                int trustReward = in.readInt();
                int rewardOrd = in.readInt();
                int rewardCount = in.readInt();
                String giver = in.readUTF();
                if (typeOrd < 0 || typeOrd >= questTypes.length
                        || itemOrd < -1 || itemOrd >= itemTypes.length
                        || rewardOrd < -1 || rewardOrd >= itemTypes.length
                        || required <= 0 || required > 100_000 || progress < 0
                        || progress > required || !Float.isFinite(timeLeft)
                        || timeLeft < -10_000 || timeLeft > 10_000_000
                        || rewardCount < 0 || rewardCount > 100_000) {
                    throw new IOException("invalid serialized quest core state");
                }
                Quest q = new Quest(questTypes[typeOrd],
                        itemOrd >= 0 ? itemTypes[itemOrd] : null, required, timeLeft,
                        trustReward, rewardOrd >= 0 ? itemTypes[rewardOrd] : null, rewardCount, giver);
                q.progress = progress;
                g.faction.quest = q;
            } else {
                g.faction.quest = null;
            }

            // NPCs.
            g.entities.npcs.clear();
            int nNpcs = in.readInt();
            for (int i = 0; i < nNpcs; i++) {
                String name = in.readUTF();
                Npc n = new Npc(g.world, name);
                n.pos.set(in.readFloat(), in.readFloat(), in.readFloat());
                n.yaw = in.readFloat();
                n.health = in.readFloat();
                n.hunger = in.readFloat();
                n.mood = in.readFloat();
                n.isTrader = in.readBoolean();
                n.raider = in.readBoolean();
                n.sick = in.readBoolean();
                n.sickTimer = in.readFloat();
                n.campIndex = in.readInt();
                n.leaveTimer = in.readFloat();
                boolean hasFaction = in.readBoolean();
                if (hasFaction) {
                    n.faction = g.faction;
                }
                if (version >= 3) {
                    String archetypeId = in.readUTF();
                    n.archetype = archetypeId.isEmpty() ? null : requireArchetype(archetypeId);
                    n.settlementId = in.readLong();
                    n.residentIndex = in.readInt();
                    n.loadedAmmo = in.readInt();
                    n.warParty = in.readBoolean();
                }
                g.entities.npcs.add(n);
            }

            // Creatures.
            g.entities.creatures.clear();
            Creature.CreatureType[] types = Creature.CreatureType.values();
            int nCreatures = in.readInt();
            for (int i = 0; i < nCreatures; i++) {
                Creature c = new Creature(g.world, types[in.readInt()]);
                c.pos.set(in.readFloat(), in.readFloat(), in.readFloat());
                c.health = in.readFloat();
                c.hunger = in.readFloat();
                c.bleedTimer = in.readFloat();
                g.entities.creatures.add(c);
            }

            // Carcasses.
            g.entities.carcasses.clear();
            int nCarcasses = in.readInt();
            for (int i = 0; i < nCarcasses; i++) {
                Carcass c = new Carcass(types[in.readInt()],
                        in.readFloat(), in.readFloat(), in.readFloat());
                c.meatLeft = in.readInt();
                c.hideLeft = in.readInt();
                c.decay = in.readFloat();
                g.entities.carcasses.add(c);
            }

            // Events.
            g.events.active.clear();
            EventSystem.EventType[] eventTypes = EventSystem.EventType.values();
            int nEvents = in.readInt();
            for (int i = 0; i < nEvents; i++) {
                g.events.active.add(new EventSystem.ActiveEvent(
                        eventTypes[in.readInt()], in.readFloat(), in.readFloat()));
            }

            // Event log.
            g.eventLog.clear();
            int nLog = in.readInt();
            for (int i = 0; i < nLog; i++) {
                g.eventLog.add(in.readUTF());
            }

            // ---- v3 sections (v2 saves: defaults already in place) ----
            g.world.kegFuses.clear();
            g.world.kegFusePlayerAttribution.clear();
            if (version >= 3) {
                readSettlements(in, g);
                readFactionState(in, g);
                int nKegs = readCount(in, "powder-keg fuse");
                for (int i = 0; i < nKegs; i++) {
                    Vec3i keg = readVec(in);
                    float fuse = in.readFloat();
                    if (!Float.isFinite(fuse)) {
                        throw new IOException("invalid powder-keg fuse time");
                    }
                    // Pre-limit v3 saves remain readable: consume every entry,
                    // retain a bounded prefix, and let non-positive legacy
                    // timers resolve on the next simulation tick.
                    if (g.world.kegFuses.size() < ExplosionSystem.MAX_ACTIVE_FUSES) {
                        g.world.kegFuses.put(keg, fuse);
                    }
                }
                if (in.available() > 0) {
                    readV3Extension(in, g);
                }
            }
            g.settlementManager.onWorldLoaded(g);
            return true;
        } catch (IOException ex) {
            System.err.println("Load failed: " + ex);
            return false;
        }
    }

    // ------------------------------------------------------------------
    // v3 sections: settlements & faction state
    // ------------------------------------------------------------------

    private static void writeSettlements(DataOutputStream out, Game g) throws IOException {
        var settlements = g.world.settlements;
        out.writeInt(settlements.size());
        for (Settlement s : settlements.values()) {
            out.writeInt(s.regionX);
            out.writeInt(s.regionZ);
            out.writeUTF(s.type.name());
            out.writeUTF(s.factionId);
            out.writeUTF(s.alignment.name());
            out.writeInt(s.foodStock);
            out.writeInt(s.woodStock);
            out.writeInt(s.medStock);
            out.writeInt(s.metalStock);
            out.writeFloat(s.alertLevel);
            out.writeFloat(s.morale);
            out.writeFloat(s.localReputation);
            out.writeBoolean(s.discovered);
            out.writeBoolean(s.cleared);
            out.writeBoolean(s.occupied);
            out.writeFloat(s.counterattackTimer);
            out.writeFloat(s.replenishTimer);
            out.writeInt(s.residents.size());
            for (Settlement.Resident r : s.residents) {
                out.writeUTF(r.name);
                out.writeUTF(r.archetype.id);
                out.writeFloat(r.health);
                out.writeBoolean(r.alive);
                out.writeInt(r.bedIndex);
                out.writeInt(r.dutyIndex);
                out.writeBoolean(r.rescued);
            }
        }
    }

    private static void readSettlements(DataInputStream in, Game g) throws IOException {
        int n = readCount(in, "settlements");
        for (int i = 0; i < n; i++) {
            int rx = in.readInt();
            int rz = in.readInt();
            String typeName = in.readUTF();
            String factionId = in.readUTF();
            String alignment = in.readUTF();
            int food = in.readInt();
            int wood = in.readInt();
            int med = in.readInt();
            int metal = in.readInt();
            float alert = in.readFloat();
            float morale = in.readFloat();
            float localRep = in.readFloat();
            boolean discovered = in.readBoolean();
            boolean cleared = in.readBoolean();
            boolean occupied = in.readBoolean();
            float counter = in.readFloat();
            float replenish = in.readFloat();
            int residents = readCount(in, "settlement residents");

            SettlementType savedType = requireSettlementType(typeName);
            String savedFaction = requireFaction(factionId);
            Settlement.Alignment savedAlignment = requireAlignment(alignment);

            // Re-derive the static plan from the seed, then overlay saved state.
            Settlement s = g.world.settlementForRegion(rx, rz);
            if (s != null) {
                if (s.type != savedType) {
                    throw new IOException("settlement type mismatch at region " + rx + "," + rz
                            + ": save=" + savedType + ", generated=" + s.type);
                }
                s.factionId = savedFaction;
                s.alignment = savedAlignment;
                s.foodStock = food;
                s.woodStock = wood;
                s.medStock = med;
                s.metalStock = metal;
                s.alertLevel = alert;
                s.morale = morale;
                s.localReputation = localRep;
                s.discovered = discovered;
                s.cleared = cleared;
                s.occupied = occupied;
                s.counterattackTimer = counter;
                s.replenishTimer = replenish;
                s.residents.clear();
            } else {
                throw new IOException("saved settlement no longer has a deterministic plan at region "
                        + rx + "," + rz);
            }
            for (int r = 0; r < residents; r++) {
                String name = in.readUTF();
                String archetypeId = in.readUTF();
                float health = in.readFloat();
                boolean alive = in.readBoolean();
                int bedIndex = in.readInt();
                int dutyIndex = in.readInt();
                boolean rescued = in.readBoolean();
                if (s != null) {
                    Settlement.Resident res = new Settlement.Resident(
                            name, requireArchetype(archetypeId));
                    res.health = health;
                    res.alive = alive;
                    res.bedIndex = bedIndex;
                    res.dutyIndex = dutyIndex;
                    res.rescued = rescued;
                    s.residents.add(res);
                }
            }
        }
    }

    private static void writeFactionState(DataOutputStream out, Game g) throws IOException {
        out.writeInt(g.world.factionReputation.size());
        for (Map.Entry<String, Float> e : g.world.factionReputation.entrySet()) {
            out.writeUTF(e.getKey());
            out.writeFloat(e.getValue());
        }
        out.writeInt(g.world.factionBounty.size());
        for (Map.Entry<String, Float> e : g.world.factionBounty.entrySet()) {
            out.writeUTF(e.getKey());
            out.writeFloat(e.getValue());
        }
    }

    private static void readFactionState(DataInputStream in, Game g) throws IOException {
        g.world.factionReputation.clear();
        int nRep = readCount(in, "faction reputation entries");
        for (int i = 0; i < nRep; i++) {
            g.world.factionReputation.put(requireFaction(in.readUTF()), in.readFloat());
        }
        g.world.factionBounty.clear();
        int nBounty = readCount(in, "faction bounty entries");
        for (int i = 0; i < nBounty; i++) {
            g.world.factionBounty.put(requireFaction(in.readUTF()), in.readFloat());
        }
    }

    /**
     * Optional versioned v3 tail. Keeping this after every original v3 field
     * preserves byte-for-byte readability of older v3 saves: EOF simply means
     * the newly introduced runtime state uses safe defaults.
     */
    private static void writeV3Extension(DataOutputStream out, Game g, List<Npc> npcs)
            throws IOException {
        out.writeInt(V3_EXTENSION_MAGIC);
        out.writeInt(V3_EXTENSION_VERSION);

        out.writeInt(g.world.gateTimers.size());
        for (Map.Entry<Vec3i, Float> entry : g.world.gateTimers.entrySet()) {
            writeVec(out, entry.getKey());
            out.writeFloat(entry.getValue());
        }

        out.writeInt(g.world.settlements.size());
        for (Settlement s : g.world.settlements.values()) {
            out.writeLong(s.id);
            out.writeBoolean(s.commandNeutralized);
            out.writeBoolean(s.alarmNeutralized);
            out.writeBoolean(s.centralObjectiveControlled);
            out.writeBoolean(s.rumored);
            out.writeLong(s.dormantStep);
            out.writeFloat(s.dormantAccumulator);
            out.writeInt(s.residents.size());
            for (Settlement.Resident resident : s.residents) {
                out.writeBoolean(resident.routed);
                out.writeBoolean(resident.surrendered);
                out.writeBoolean(resident.sick);
                out.writeFloat(resident.sicknessTimer);
                out.writeFloat(resident.hunger);
            }
        }

        out.writeInt(npcs.size());
        for (Npc npc : npcs) {
            out.writeUTF(npc.partyFactionId == null ? "" : npc.partyFactionId);
            out.writeLong(npc.originSettlementId);
            out.writeUTF(npc.partyMission.name());
            out.writeFloat(npc.partyDestination.x);
            out.writeFloat(npc.partyDestination.y);
            out.writeFloat(npc.partyDestination.z);
            out.writeFloat(npc.partyMissionTimer);
            out.writeBoolean(npc.partyContact);
        }
        writeV3ExtensionSections(out, g);
    }

    private static void readV3Extension(DataInputStream in, Game g) throws IOException {
        if (in.available() < 8) {
            throw new IOException("truncated v3 extension");
        }
        if (in.readInt() != V3_EXTENSION_MAGIC) {
            throw new IOException("unknown trailing v3 save data");
        }
        int extensionVersion = in.readInt();
        if (extensionVersion < 1 || extensionVersion > V3_EXTENSION_VERSION) {
            throw new IOException("unsupported v3 extension version " + extensionVersion);
        }

        g.world.gateTimers.clear();
        int gateCount = readCount(in, "gate timers");
        for (int i = 0; i < gateCount; i++) {
            Vec3i pos = readVec(in);
            float timer = in.readFloat();
            if (timer > 0 && g.world.getBlock(pos.x(), pos.y(), pos.z()) == BlockType.GATE_OPEN) {
                g.world.gateTimers.put(pos, timer);
            }
        }

        int settlementCount = readCount(in, "extended settlements");
        for (int i = 0; i < settlementCount; i++) {
            long id = in.readLong();
            Settlement s = g.world.settlements.get(id);
            if (s == null) {
                throw new IOException("extended state references unknown settlement " + id);
            }
            s.commandNeutralized = in.readBoolean();
            s.alarmNeutralized = in.readBoolean();
            s.centralObjectiveControlled = in.readBoolean();
            s.rumored = in.readBoolean();
            s.dormantStep = in.readLong();
            s.dormantAccumulator = in.readFloat();
            int residentCount = readCount(in, "extended residents");
            if (residentCount != s.residents.size()) {
                throw new IOException("resident extension count mismatch for settlement " + id);
            }
            for (Settlement.Resident resident : s.residents) {
                resident.routed = in.readBoolean();
                resident.surrendered = in.readBoolean();
                resident.sick = in.readBoolean();
                resident.sicknessTimer = in.readFloat();
                resident.hunger = in.readFloat();
            }
        }

        int npcCount = readCount(in, "extended NPCs");
        if (npcCount != g.entities.npcs.size()) {
            throw new IOException("NPC extension count mismatch");
        }
        for (Npc npc : g.entities.npcs) {
            String faction = in.readUTF();
            npc.partyFactionId = faction.isEmpty() ? null : requireFaction(faction);
            npc.originSettlementId = in.readLong();
            npc.partyMission = requirePartyMission(in.readUTF());
            npc.partyDestination.set(in.readFloat(), in.readFloat(), in.readFloat());
            npc.partyMissionTimer = in.readFloat();
            npc.partyContact = in.readBoolean();
        }
        if (extensionVersion >= 2) {
            readV3ExtensionSections(in, g);
        }
        if (in.available() != 0) {
            throw new IOException("unexpected bytes after v3 extension");
        }
    }

    /**
     * Extension v2 keeps the v1 payload intact, then adds stable-ID, length-prefixed
     * sections. New systems can append a section without coupling their byte layout
     * to lanterns or requiring another top-level save-format version.
     */
    private static void writeV3ExtensionSections(DataOutputStream out, Game g)
            throws IOException {
        List<V3Section> sections = new ArrayList<>();
        sections.add(writeLanternSection(g));
        sections.add(writePartyStateSection(g));
        sections.add(writeCounterattackSection(g));
        sections.add(writeQuestTargetSection(g));
        sections.add(writeReputationActionsSection(g));
        sections.add(writeActiveExplosivesSection(g));
        sections.add(writeKegFuseAttributionSection(g));
        out.writeInt(V3_SECTION_ENVELOPE_MAGIC);
        out.writeInt(sections.size());
        for (V3Section section : sections) {
            if (section.payload.length > MAX_V3_SECTION_BYTES) {
                throw new IOException("v3 extension section too large: " + section.id);
            }
            out.writeUTF(section.id);
            out.writeInt(section.payload.length);
            out.write(section.payload);
        }
    }

    private static V3Section writeLanternSection(Game g) throws IOException {
        List<Map.Entry<Vec3i, World.LanternState>> entries =
                g.world.lanterns.entrySet().stream()
                        .filter(entry -> g.world.getBlock(entry.getKey().x(), entry.getKey().y(),
                                entry.getKey().z()) == BlockType.LANTERN)
                        .sorted(Comparator
                                .comparingInt((Map.Entry<Vec3i, World.LanternState> e)
                                        -> e.getKey().x())
                                .thenComparingInt(e -> e.getKey().y())
                                .thenComparingInt(e -> e.getKey().z()))
                        .toList();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream section = new DataOutputStream(bytes)) {
            section.writeInt(LANTERN_SECTION_VERSION);
            section.writeInt(entries.size());
            for (Map.Entry<Vec3i, World.LanternState> entry : entries) {
                writeVec(section, entry.getKey());
                section.writeFloat(entry.getValue().fuelSeconds());
                section.writeBoolean(entry.getValue().lit());
            }
        }
        return new V3Section(V3_SECTION_LANTERNS, bytes.toByteArray());
    }

    private static V3Section writePartyStateSection(Game g) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream section = new DataOutputStream(bytes)) {
            section.writeInt(PARTY_STATE_SECTION_VERSION);
            section.writeInt(g.entities.npcs.size());
            for (Npc npc : g.entities.npcs) {
                section.writeUTF((npc.partyKind == null
                        ? Npc.PartyKind.PATROL : npc.partyKind).name());
                section.writeUTF(npc.partyMissionId == null ? "" : npc.partyMissionId);
                section.writeUTF(npc.partyMemberId == null ? "" : npc.partyMemberId);
                section.writeLong(npc.partyTargetSettlementId);
            }
        }
        return new V3Section(V3_SECTION_PARTY_STATE, bytes.toByteArray());
    }

    private static V3Section writeCounterattackSection(Game g) throws IOException {
        List<CounterattackMission> missions = new ArrayList<>(
                g.settlementManager.counterattacks.missions.values());
        missions.sort(Comparator.comparing(mission -> mission.id));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream section = new DataOutputStream(bytes)) {
            section.writeInt(COUNTERATTACK_SECTION_VERSION);
            section.writeInt(missions.size());
            for (CounterattackMission mission : missions) {
                section.writeUTF(mission.id);
                section.writeLong(mission.originSettlementId);
                section.writeLong(mission.targetSettlementId);
                section.writeUTF(mission.attackerFactionId);
                writeVec(section, mission.origin);
                writeVec(section, mission.target);
                section.writeLong(mission.resolutionSeed);
                section.writeInt(mission.initialAttackers);
                section.writeUTF(mission.phase.name());
                section.writeUTF(mission.outcome.name());
                section.writeFloat(mission.phaseTimer);
                section.writeInt(mission.survivors);
                section.writeFloat(mission.x);
                section.writeFloat(mission.y);
                section.writeFloat(mission.z);
                section.writeBoolean(mission.outcomeApplied);
            }
        }
        return new V3Section(V3_SECTION_COUNTERATTACKS, bytes.toByteArray());
    }

    private static V3Section writeQuestTargetSection(Game g) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream section = new DataOutputStream(bytes)) {
            section.writeInt(QUEST_TARGET_SECTION_VERSION);
            section.writeLong(g.faction.questSequence());
            Quest quest = g.faction.quest;
            section.writeBoolean(quest != null);
            if (quest != null) {
                section.writeUTF(quest.instanceId);
                section.writeUTF(quest.status.name());
                section.writeUTF(quest.giverId);
                section.writeLong(quest.giverSettlementId);
                section.writeUTF(quest.rewardProviderId);
                section.writeLong(quest.targetSettlementId);
                section.writeUTF(quest.targetFactionId);
                section.writeUTF(quest.targetMissionId);
                section.writeUTF(quest.targetCaptiveId);
                section.writeUTF(quest.targetPoiId);
                section.writeUTF(quest.destinationId);
                section.writeUTF(quest.failureReason);
                section.writeBoolean(quest.rewardClaimed);
                section.writeInt(quest.creditedEvents.size());
                for (String event : quest.creditedEvents) {
                    section.writeUTF(event);
                }
            }
        }
        return new V3Section(V3_SECTION_QUEST_TARGET, bytes.toByteArray());
    }

    private static V3Section writeReputationActionsSection(Game g) throws IOException {
        List<Settlement> settlements = g.world.settlements.values().stream()
                .filter(s -> s.trespassCooldown > 0f || s.restrictedStorageCooldown > 0f)
                .sorted(Comparator.comparingLong(s -> s.id))
                .toList();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream section = new DataOutputStream(bytes)) {
            section.writeInt(REPUTATION_ACTIONS_SECTION_VERSION);
            section.writeInt(settlements.size());
            for (Settlement settlement : settlements) {
                section.writeLong(settlement.id);
                section.writeFloat(settlement.trespassCooldown);
                section.writeFloat(settlement.restrictedStorageCooldown);
            }
        }
        return new V3Section(V3_SECTION_REPUTATION_ACTIONS, bytes.toByteArray());
    }

    private static V3Section writeActiveExplosivesSection(Game g) throws IOException {
        List<ProjectileSystem.Projectile> explosives = g.projectiles.live.stream()
                .filter(projectile -> ProjectileSystem.isExplosive(projectile.kind)
                        && !projectile.detonated && projectile.fuse > 0 && projectile.life > 0)
                .toList();
        if (explosives.size() > ProjectileSystem.MAX_LIVE) {
            throw new IOException("too many active explosives: " + explosives.size());
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream section = new DataOutputStream(bytes)) {
            section.writeInt(ACTIVE_EXPLOSIVES_SECTION_VERSION);
            section.writeInt(explosives.size());
            for (ProjectileSystem.Projectile projectile : explosives) {
                validateActiveExplosiveForWrite(projectile);
                section.writeUTF(projectile.kind.name());
                section.writeFloat(projectile.x);
                section.writeFloat(projectile.y);
                section.writeFloat(projectile.z);
                section.writeFloat(projectile.vx);
                section.writeFloat(projectile.vy);
                section.writeFloat(projectile.vz);
                section.writeFloat(projectile.gravity);
                section.writeFloat(projectile.damage);
                section.writeFloat(projectile.life);
                section.writeFloat(projectile.fuse);
                section.writeBoolean(projectile.fromPlayer);
                section.writeBoolean(projectile.impactedEntity);
            }
        }
        return new V3Section(V3_SECTION_ACTIVE_EXPLOSIVES, bytes.toByteArray());
    }

    private static V3Section writeKegFuseAttributionSection(Game g) throws IOException {
        List<Vec3i> positions = g.world.kegFuses.keySet().stream()
                .sorted(Comparator.comparingInt(Vec3i::x)
                        .thenComparingInt(Vec3i::y)
                        .thenComparingInt(Vec3i::z))
                .toList();
        if (positions.size() > ExplosionSystem.MAX_ACTIVE_FUSES) {
            throw new IOException("too many attributed powder-keg fuses: "
                    + positions.size());
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream section = new DataOutputStream(bytes)) {
            section.writeInt(KEG_FUSE_ATTRIBUTION_SECTION_VERSION);
            section.writeInt(positions.size());
            for (Vec3i position : positions) {
                writeVec(section, position);
                section.writeBoolean(g.world.kegFusePlayerAttribution
                        .getOrDefault(position, false));
            }
        }
        return new V3Section(V3_SECTION_KEG_FUSE_ATTRIBUTION, bytes.toByteArray());
    }

    private static void readV3ExtensionSections(DataInputStream in, Game g) throws IOException {
        if (in.available() < 8 || in.readInt() != V3_SECTION_ENVELOPE_MAGIC) {
            throw new IOException("invalid v3 extension section envelope");
        }
        int sectionCount = readCount(in, "v3 extension sections");
        if (sectionCount > MAX_V3_EXTENSION_SECTIONS) {
            throw new IOException("too many v3 extension sections: " + sectionCount);
        }
        Set<String> seen = new HashSet<>();
        boolean restoredLanterns = false;
        for (int i = 0; i < sectionCount; i++) {
            String id = in.readUTF();
            if (id.isBlank() || !seen.add(id)) {
                throw new IOException("invalid or duplicate v3 extension section: " + id);
            }
            int length = in.readInt();
            if (length < 0 || length > MAX_V3_SECTION_BYTES || length > in.available()) {
                throw new IOException("invalid v3 extension section length for " + id + ": " + length);
            }
            byte[] payload = in.readNBytes(length);
            if (payload.length != length) {
                throw new IOException("truncated v3 extension section: " + id);
            }
            if (V3_SECTION_LANTERNS.equals(id)) {
                readLanternSection(payload, g);
                restoredLanterns = true;
            } else if (V3_SECTION_PARTY_STATE.equals(id)) {
                readPartyStateSection(payload, g);
            } else if (V3_SECTION_COUNTERATTACKS.equals(id)) {
                readCounterattackSection(payload, g);
            } else if (V3_SECTION_QUEST_TARGET.equals(id)) {
                readQuestTargetSection(payload, g);
            } else if (V3_SECTION_REPUTATION_ACTIONS.equals(id)) {
                readReputationActionsSection(payload, g);
            } else if (V3_SECTION_ACTIVE_EXPLOSIVES.equals(id)) {
                readActiveExplosivesSection(payload, g);
            } else if (V3_SECTION_KEG_FUSE_ATTRIBUTION.equals(id)) {
                readKegFuseAttributionSection(payload, g);
            }
            // Unknown stable IDs are intentionally skipped using their bounded length.
        }
        if (restoredLanterns) {
            g.world.refreshLoadedLights();
        }
    }

    private static void readActiveExplosivesSection(byte[] payload, Game g) throws IOException {
        try (DataInputStream section = new DataInputStream(new ByteArrayInputStream(payload))) {
            int sectionVersion = section.readInt();
            if (sectionVersion != ACTIVE_EXPLOSIVES_SECTION_VERSION) {
                throw new IOException("unsupported active-explosives section version "
                        + sectionVersion);
            }
            int count = readCount(section, "active explosives");
            if (count > ProjectileSystem.MAX_LIVE) {
                throw new IOException("too many active explosives: " + count);
            }
            for (int i = 0; i < count; i++) {
                ProjectileSystem.Projectile projectile = new ProjectileSystem.Projectile();
                projectile.kind = requireExplosiveKind(section.readUTF());
                projectile.x = readFinite(section, "explosive x", -100_000_000f, 100_000_000f);
                projectile.y = readFinite(section, "explosive y", -10_000f, 10_000f);
                projectile.z = readFinite(section, "explosive z", -100_000_000f, 100_000_000f);
                projectile.vx = readFinite(section, "explosive vx", -1_000f, 1_000f);
                projectile.vy = readFinite(section, "explosive vy", -1_000f, 1_000f);
                projectile.vz = readFinite(section, "explosive vz", -1_000f, 1_000f);
                projectile.gravity = readFinite(section, "explosive gravity", 0f, 1_000f);
                projectile.damage = readFinite(section, "explosive damage", 0f, 100_000f);
                projectile.life = readFinite(section, "explosive life", 0.0001f, 300f);
                projectile.fuse = readFinite(section, "explosive fuse", 0.0001f, 60f);
                projectile.fromPlayer = section.readBoolean();
                projectile.impactedEntity = section.readBoolean();
                if (projectile.fuse > projectile.life
                        || !g.projectiles.restoreExplosive(projectile,
                        projectile.fromPlayer ? g.player : null)) {
                    throw new IOException("invalid active explosive state at index " + i);
                }
            }
            if (section.available() != 0) {
                throw new IOException("unexpected bytes after active-explosives section");
            }
        }
    }

    private static void readKegFuseAttributionSection(byte[] payload, Game g)
            throws IOException {
        try (DataInputStream section = new DataInputStream(new ByteArrayInputStream(payload))) {
            int sectionVersion = section.readInt();
            if (sectionVersion != KEG_FUSE_ATTRIBUTION_SECTION_VERSION) {
                throw new IOException("unsupported keg-fuse-attribution section version "
                        + sectionVersion);
            }
            int count = readCount(section, "powder-keg fuse attributions");
            if (count > ExplosionSystem.MAX_ACTIVE_FUSES) {
                throw new IOException("too many powder-keg fuse attributions: " + count);
            }
            Set<Vec3i> seen = new HashSet<>();
            for (int i = 0; i < count; i++) {
                Vec3i position = readVec(section);
                boolean byPlayer = section.readBoolean();
                if (!seen.add(position)) {
                    throw new IOException("duplicate powder-keg fuse attribution at "
                            + position);
                }
                if (!g.world.kegFuses.containsKey(position)) {
                    throw new IOException("powder-keg fuse attribution has no timer at "
                            + position);
                }
                g.world.kegFusePlayerAttribution.put(position, byPlayer);
            }
            if (section.available() != 0) {
                throw new IOException(
                        "unexpected bytes after keg-fuse-attribution section");
            }
        }
    }

    private static void readLanternSection(byte[] payload, Game g) throws IOException {
        try (DataInputStream section = new DataInputStream(new ByteArrayInputStream(payload))) {
            int sectionVersion = section.readInt();
            if (sectionVersion != LANTERN_SECTION_VERSION) {
                throw new IOException("unsupported lantern section version " + sectionVersion);
            }
            int count = readCount(section, "lantern states");
            Set<Vec3i> seen = new HashSet<>();
            for (int i = 0; i < count; i++) {
                Vec3i pos = readVec(section);
                float fuel = section.readFloat();
                boolean lit = section.readBoolean();
                if (!seen.add(pos)) {
                    throw new IOException("duplicate lantern state at " + pos);
                }
                if (!Float.isFinite(fuel) || fuel < 0
                        || fuel > World.LANTERN_MAX_FUEL) {
                    throw new IOException("invalid lantern fuel at " + pos + ": " + fuel);
                }
                // Stale state cannot resurrect or load a chunk; only a real changed block accepts it.
                g.world.restoreLanternState(pos, fuel, lit);
            }
            if (section.available() != 0) {
                throw new IOException("unexpected bytes after lantern section");
            }
        }
    }

    private static void readPartyStateSection(byte[] payload, Game g) throws IOException {
        try (DataInputStream section = new DataInputStream(new ByteArrayInputStream(payload))) {
            int sectionVersion = section.readInt();
            if (sectionVersion != PARTY_STATE_SECTION_VERSION) {
                throw new IOException("unsupported party-state section version " + sectionVersion);
            }
            int count = readCount(section, "party-state NPCs");
            if (count != g.entities.npcs.size()) {
                throw new IOException("party-state NPC count mismatch");
            }
            for (Npc npc : g.entities.npcs) {
                npc.partyKind = requirePartyKind(section.readUTF());
                String missionId = section.readUTF();
                if (missionId.length() > 512) {
                    throw new IOException("party mission id is too long");
                }
                npc.partyMissionId = missionId;
                String memberId = section.readUTF();
                if (memberId.length() > 640) {
                    throw new IOException("party member id is too long");
                }
                npc.partyMemberId = memberId;
                npc.partyTargetSettlementId = section.readLong();
                if (npc.partyTargetSettlementId != 0
                        && !g.world.settlements.containsKey(npc.partyTargetSettlementId)) {
                    throw new IOException("party targets unknown settlement "
                            + npc.partyTargetSettlementId);
                }
            }
            if (section.available() != 0) {
                throw new IOException("unexpected bytes after party-state section");
            }
        }
    }

    private static void readCounterattackSection(byte[] payload, Game g) throws IOException {
        try (DataInputStream section = new DataInputStream(new ByteArrayInputStream(payload))) {
            int sectionVersion = section.readInt();
            if (sectionVersion != COUNTERATTACK_SECTION_VERSION) {
                throw new IOException("unsupported counterattack section version " + sectionVersion);
            }
            int count = readCount(section, "counterattack missions");
            if (count > CounterattackDirector.MAX_MISSIONS) {
                throw new IOException("too many counterattack missions: " + count);
            }
            g.settlementManager.counterattacks.reset();
            Set<String> ids = new HashSet<>();
            Set<Long> targets = new HashSet<>();
            for (int i = 0; i < count; i++) {
                String id = section.readUTF();
                if (!id.startsWith("counterattack:") || id.length() > 512 || !ids.add(id)) {
                    throw new IOException("invalid or duplicate counterattack id: " + id);
                }
                long originId = section.readLong();
                long targetId = section.readLong();
                if (!targets.add(targetId) || !g.world.settlements.containsKey(targetId)) {
                    throw new IOException("invalid counterattack target " + targetId);
                }
                if (originId != CounterattackMission.REGIONAL_FORCE_ORIGIN
                        && !g.world.settlements.containsKey(originId)) {
                    throw new IOException("invalid counterattack origin " + originId);
                }
                String factionId = requireFaction(section.readUTF());
                Vec3i origin = readVec(section);
                Vec3i target = readVec(section);
                long resolutionSeed = section.readLong();
                int initial = section.readInt();
                CounterattackMission.Phase phase = requireCounterattackPhase(section.readUTF());
                CounterattackMission.Outcome outcome = requireCounterattackOutcome(section.readUTF());
                float timer = section.readFloat();
                int survivors = section.readInt();
                float x = section.readFloat();
                float y = section.readFloat();
                float z = section.readFloat();
                boolean applied = section.readBoolean();
                if (initial <= 0 || initial > SettlementManager.MAX_ACTIVE_COUNTERATTACKERS
                        || survivors < 0 || survivors > initial
                        || !Float.isFinite(timer) || timer < -2f || timer > 10_000f
                        || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                        || (outcome == CounterattackMission.Outcome.UNRESOLVED && applied)) {
                    throw new IOException("invalid counterattack state for " + id);
                }
                CounterattackMission mission = new CounterattackMission(id, originId, targetId,
                        factionId, origin, target, resolutionSeed, initial);
                mission.phase = phase;
                mission.outcome = outcome;
                mission.phaseTimer = timer;
                mission.survivors = survivors;
                mission.x = x;
                mission.y = y;
                mission.z = z;
                mission.outcomeApplied = applied;
                g.settlementManager.counterattacks.missions.put(id, mission);
            }
            if (section.available() != 0) {
                throw new IOException("unexpected bytes after counterattack section");
            }
        }
    }

    private static void readQuestTargetSection(byte[] payload, Game g) throws IOException {
        try (DataInputStream section = new DataInputStream(new ByteArrayInputStream(payload))) {
            int sectionVersion = section.readInt();
            if (sectionVersion != QUEST_TARGET_SECTION_VERSION) {
                throw new IOException("unsupported quest-target section version " + sectionVersion);
            }
            long sequence = section.readLong();
            if (sequence < 0) {
                throw new IOException("invalid quest sequence " + sequence);
            }
            g.faction.restoreQuestSequence(sequence);
            boolean hasTarget = section.readBoolean();
            Quest quest = g.faction.quest;
            if (hasTarget != (quest != null)) {
                throw new IOException("quest target/core presence mismatch");
            }
            if (quest != null) {
                quest.instanceId = readBoundedUtf(section, "quest instance id", 512);
                quest.status = requireQuestStatus(readBoundedUtf(section, "quest status", 64));
                if (quest.status != Quest.Status.LEGACY_UNBOUND
                        && !quest.instanceId.startsWith("quest:")) {
                    throw new IOException("invalid quest instance id " + quest.instanceId);
                }
                quest.giverId = readBoundedUtf(section, "quest giver id", 512);
                quest.giverSettlementId = section.readLong();
                quest.rewardProviderId = readBoundedUtf(section, "quest provider id", 512);
                quest.targetSettlementId = section.readLong();
                quest.targetFactionId = readBoundedUtf(section, "quest target faction", 128);
                quest.targetMissionId = readBoundedUtf(section, "quest mission id", 512);
                quest.targetCaptiveId = readBoundedUtf(section, "quest captive id", 512);
                quest.targetPoiId = readBoundedUtf(section, "quest POI id", 512);
                quest.destinationId = readBoundedUtf(section, "quest destination id", 512);
                quest.failureReason = readBoundedUtf(section, "quest failure reason", 512);
                quest.rewardClaimed = section.readBoolean();
                validateQuestSettlement(g, quest.giverSettlementId, "giver");
                validateQuestSettlement(g, quest.targetSettlementId, "target");
                if (!quest.targetFactionId.isBlank()) {
                    requireFaction(quest.targetFactionId);
                }
                if (quest.status != Quest.Status.LEGACY_UNBOUND
                        && !(quest.rewardProviderId.equals("camp")
                        || quest.rewardProviderId.startsWith("settlement:"))) {
                    throw new IOException("invalid quest reward provider " + quest.rewardProviderId);
                }
                int events = readCount(section, "credited quest events");
                if (events > 64) {
                    throw new IOException("too many credited quest events: " + events);
                }
                quest.creditedEvents.clear();
                for (int i = 0; i < events; i++) {
                    String event = readBoundedUtf(section, "credited quest event", 640);
                    if (event.isBlank() || !quest.creditedEvents.add(event)) {
                        throw new IOException("invalid or duplicate credited quest event");
                    }
                }
                if (quest.type == Quest.Type.RESCUE_CAPTIVE && !quest.targetCaptiveId.isBlank()) {
                    Settlement settlement = g.world.settlements.get(quest.targetSettlementId);
                    boolean found = false;
                    if (settlement != null) {
                        for (int i = 0; i < settlement.residents.size(); i++) {
                            if (quest.targetCaptiveId.equals(
                                    com.veylon.ai.FactionSystem.captiveId(settlement, i))) {
                                found = true;
                                break;
                            }
                        }
                    }
                    if (!found) {
                        throw new IOException("quest references unknown captive "
                                + quest.targetCaptiveId);
                    }
                }
            }
            if (section.available() != 0) {
                throw new IOException("unexpected bytes after quest-target section");
            }
        }
    }

    private static void readReputationActionsSection(byte[] payload, Game g)
            throws IOException {
        try (DataInputStream section = new DataInputStream(new ByteArrayInputStream(payload))) {
            int sectionVersion = section.readInt();
            if (sectionVersion != REPUTATION_ACTIONS_SECTION_VERSION) {
                throw new IOException("unsupported reputation-state section version "
                        + sectionVersion);
            }
            int count = readCount(section, "settlement reputation states");
            Set<Long> ids = new HashSet<>();
            for (int i = 0; i < count; i++) {
                long id = section.readLong();
                float trespass = section.readFloat();
                float storage = section.readFloat();
                Settlement settlement = g.world.settlements.get(id);
                if (settlement == null || !ids.add(id) || !Float.isFinite(trespass)
                        || !Float.isFinite(storage) || trespass < 0f || storage < 0f
                        || trespass > SettlementManager.TRESPASS_INTERVAL
                        || storage > SettlementManager.RESTRICTED_STORAGE_INTERVAL) {
                    throw new IOException("invalid settlement reputation state " + id);
                }
                settlement.trespassCooldown = trespass;
                settlement.restrictedStorageCooldown = storage;
            }
            if (section.available() != 0) {
                throw new IOException("unexpected bytes after reputation-state section");
            }
        }
    }

    private static void validateQuestSettlement(Game g, long id, String label) throws IOException {
        if (id != Quest.NO_SETTLEMENT && !g.world.settlements.containsKey(id)) {
            throw new IOException("quest references unknown " + label + " settlement " + id);
        }
    }

    private static String readBoundedUtf(DataInputStream in, String label, int maxChars)
            throws IOException {
        String value = in.readUTF();
        if (value.length() > maxChars) {
            throw new IOException(label + " is too long");
        }
        return value;
    }

    private record V3Section(String id, byte[] payload) {
    }

    private static int readCount(DataInputStream in, String label) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_SERIALIZED_ENTRIES) {
            throw new IOException("invalid " + label + " count: " + count);
        }
        return count;
    }

    private static NpcArchetype requireArchetype(String id) throws IOException {
        for (NpcArchetype archetype : NpcArchetype.values()) {
            if (archetype.id.equals(id)) {
                return archetype;
            }
        }
        throw new IOException("unknown NPC archetype id: " + id);
    }

    private static SettlementType requireSettlementType(String name) throws IOException {
        for (SettlementType type : SettlementType.values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        throw new IOException("unknown settlement type id: " + name);
    }

    private static Settlement.Alignment requireAlignment(String name) throws IOException {
        for (Settlement.Alignment alignment : Settlement.Alignment.values()) {
            if (alignment.name().equals(name)) {
                return alignment;
            }
        }
        throw new IOException("unknown settlement alignment id: " + name);
    }

    private static String requireFaction(String id) throws IOException {
        if (HumanFaction.ALL.contains(id)) {
            return id;
        }
        throw new IOException("unknown human faction id: " + id);
    }

    private static Npc.PartyMission requirePartyMission(String name) throws IOException {
        for (Npc.PartyMission mission : Npc.PartyMission.values()) {
            if (mission.name().equals(name)) {
                return mission;
            }
        }
        throw new IOException("unknown patrol mission id: " + name);
    }

    private static Npc.PartyKind requirePartyKind(String name) throws IOException {
        for (Npc.PartyKind kind : Npc.PartyKind.values()) {
            if (kind.name().equals(name)) {
                return kind;
            }
        }
        throw new IOException("unknown party kind id: " + name);
    }

    private static CounterattackMission.Phase requireCounterattackPhase(String name)
            throws IOException {
        for (CounterattackMission.Phase phase : CounterattackMission.Phase.values()) {
            if (phase.name().equals(name)) {
                return phase;
            }
        }
        throw new IOException("unknown counterattack phase id: " + name);
    }

    private static CounterattackMission.Outcome requireCounterattackOutcome(String name)
            throws IOException {
        for (CounterattackMission.Outcome outcome : CounterattackMission.Outcome.values()) {
            if (outcome.name().equals(name)) {
                return outcome;
            }
        }
        throw new IOException("unknown counterattack outcome id: " + name);
    }

    private static ProjectileSystem.Kind requireExplosiveKind(String name) throws IOException {
        try {
            ProjectileSystem.Kind kind = ProjectileSystem.Kind.valueOf(name);
            if (ProjectileSystem.isExplosive(kind)) {
                return kind;
            }
        } catch (IllegalArgumentException ignored) {
            // Report all unknown/non-explosive IDs through the same stable-ID error.
        }
        throw new IOException("unknown active explosive kind id: " + name);
    }

    private static float readFinite(DataInputStream in, String label, float min, float max)
            throws IOException {
        float value = in.readFloat();
        if (!Float.isFinite(value) || value < min || value > max) {
            throw new IOException("invalid " + label + ": " + value);
        }
        return value;
    }

    private static void validateActiveExplosiveForWrite(ProjectileSystem.Projectile projectile)
            throws IOException {
        boolean valid = inRange(projectile.x, -100_000_000f, 100_000_000f)
                && inRange(projectile.y, -10_000f, 10_000f)
                && inRange(projectile.z, -100_000_000f, 100_000_000f)
                && inRange(projectile.vx, -1_000f, 1_000f)
                && inRange(projectile.vy, -1_000f, 1_000f)
                && inRange(projectile.vz, -1_000f, 1_000f)
                && inRange(projectile.gravity, 0f, 1_000f)
                && inRange(projectile.damage, 0f, 100_000f)
                && inRange(projectile.life, 0.0001f, 300f)
                && inRange(projectile.fuse, 0.0001f, 60f)
                && projectile.fuse <= projectile.life;
        if (!valid) {
            throw new IOException("invalid active explosive runtime state");
        }
    }

    private static boolean inRange(float value, float min, float max) {
        return Float.isFinite(value) && value >= min && value <= max;
    }

    private static Quest.Status requireQuestStatus(String name) throws IOException {
        for (Quest.Status status : Quest.Status.values()) {
            if (status.name().equals(name)) {
                return status;
            }
        }
        throw new IOException("unknown quest status id: " + name);
    }

    private static void writeVec(DataOutputStream out, Vec3i v) throws IOException {
        out.writeInt(v.x());
        out.writeInt(v.y());
        out.writeInt(v.z());
    }

    private static Vec3i readVec(DataInputStream in) throws IOException {
        return new Vec3i(in.readInt(), in.readInt(), in.readInt());
    }

    private static void writeStack(DataOutputStream out, ItemStack s) throws IOException {
        if (s == null) {
            out.writeInt(-1);
            out.writeInt(0);
            out.writeFloat(0);
            out.writeFloat(0);
            out.writeInt(0);
        } else {
            out.writeInt(s.type.ordinal());
            out.writeInt(s.count);
            out.writeFloat(s.durability);
            out.writeFloat(s.freshness);
            out.writeInt(s.charge);
        }
    }

    private static ItemStack readStack(DataInputStream in, int version) throws IOException {
        int ord = in.readInt();
        int count = in.readInt();
        float durability = in.readFloat();
        float freshness = in.readFloat();
        int charge = version >= 3 ? in.readInt() : 0;
        ItemType[] types = ItemType.values();
        if (ord < 0 || ord >= types.length || count <= 0) {
            return null;
        }
        ItemStack s = new ItemStack(types[ord], count);
        s.durability = durability;
        s.freshness = freshness;
        s.charge = charge;
        return s;
    }

    private static void writeInventory(DataOutputStream out, Inventory inv) throws IOException {
        out.writeInt(inv.size());
        for (int i = 0; i < inv.size(); i++) {
            writeStack(out, inv.get(i));
        }
    }

    private static void readInventory(DataInputStream in, Inventory inv, int version)
            throws IOException {
        int size = in.readInt();
        for (int i = 0; i < size; i++) {
            ItemStack stack = readStack(in, version);
            if (i < inv.size()) {
                inv.set(i, stack);
            }
        }
    }
}
