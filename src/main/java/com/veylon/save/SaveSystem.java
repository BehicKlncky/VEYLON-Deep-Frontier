package com.veylon.save;

import com.veylon.Game;
import com.veylon.ai.Quest;
import com.veylon.combat.ExplosionSystem;
import com.veylon.combat.ProjectileSystem;
import com.veylon.entity.Affliction;
import com.veylon.entity.Carcass;
import com.veylon.entity.Creature;
import com.veylon.entity.Npc;
import com.veylon.entity.PlayerConstants;
import com.veylon.item.EquipSlot;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.settlement.NpcArchetype;
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
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
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
 *
 * <h2>Reading untrusted bytes</h2>
 *
 * <p>"The game keeps running" is a contract, not a hope, and it applies to
 * every malformed file — not only to recognisably old ones. Save writes go to
 * a sibling temporary file and replace the destination only after the complete
 * payload has been flushed. A load over a live game is first proved readable
 * against an isolated game instance, so malformed bytes never release or
 * partially replace the world the player is currently in.</p>
 *
 * <p>Values that can allocate a collection, select an index, or poison core
 * player state are validated before they are used:</p>
 * <ul>
 *   <li>entry counts go through {@link #readCount}, because an unchecked one
 *       reaches {@code new ArrayList<>(n)};</li>
 *   <li>enum ordinals and the hotbar index go through {@link #readOrdinal},
 *       because an unchecked one indexes {@code values()};</li>
 *   <li>player scalars go through {@link #readFinite}, because NaN health or
 *       position is loaded state nothing can recover from;</li>
 *   <li>and {@link #load} catches {@link RuntimeException} as well as
 *       {@link IOException}, so a future unguarded read degrades to a failed
 *       load instead of a crash.</li>
 * </ul>
 */
public final class SaveSystem {

    private static final int MAGIC = 0x5645594C; // "VEYL"
    private static final int VERSION = 3;
    private static final int MIN_SUPPORTED = 2;
    /** Optional tail added to v3 without invalidating already-shipped v3 files. */
    private static final int V3_EXTENSION_MAGIC = 0x57334558; // "W3EX"
    private static final int V3_EXTENSION_VERSION = 2;
    private static final int MAX_SERIALIZED_ENTRIES = 100_000;
    /**
     * Block edits get their own, far looser ceiling: they are the one section
     * a long-lived world can legitimately grow without bound, and rejecting a
     * real save is worse than accepting an implausible one. Four million edits
     * is ~52 MB of deltas — beyond any played world, and still finite.
     */
    private static final int MAX_SERIALIZED_BLOCK_EDITS = 4_000_000;
    /** Hard cap before a file is retained in memory for a transaction-safe load. */
    private static final int MAX_SAVE_BYTES = 128 * 1024 * 1024;
    public static final Path SAVE_PATH = AppPaths.dataDirectory().resolve("saves/veylon.sav");

    private SaveSystem() {
    }

    public static boolean save(Game g) {
        return save(g, SAVE_PATH);
    }

    public static boolean save(Game g, Path savePath) {
        Objects.requireNonNull(g, "g");
        Objects.requireNonNull(savePath, "savePath");
        Path target = savePath.toAbsolutePath();
        Path temporary = null;
        try {
            Path parent = target.getParent();
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, "veylon-save-", ".tmp");
            try (FileChannel channel = FileChannel.open(temporary,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                 DataOutputStream out = new DataOutputStream(
                         new BufferedOutputStream(Channels.newOutputStream(channel)))) {
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
                out.flush();
                channel.force(true);
            }
            replaceAtomically(temporary, target);
            temporary = null;
            return true;
        } catch (IOException | RuntimeException ex) {
            System.err.println("Save failed: " + ex);
            return false;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException cleanupFailure) {
                    System.err.println("Could not remove incomplete save " + temporary
                            + ": " + cleanupFailure);
                }
            }
        }
    }

    private static void replaceAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            // Same-directory replacement still keeps the completed temporary
            // payload separate from the destination until this final step.
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static boolean load(Game g) {
        return load(g, SAVE_PATH);
    }

    public static boolean load(Game g, Path savePath) {
        Objects.requireNonNull(g, "g");
        Objects.requireNonNull(savePath, "savePath");
        if (!Files.exists(savePath)) {
            return false;
        }
        final byte[] payload;
        try (BufferedInputStream source = new BufferedInputStream(Files.newInputStream(savePath))) {
            payload = source.readNBytes(MAX_SAVE_BYTES + 1);
            if (payload.length > MAX_SAVE_BYTES) {
                System.err.println("Load failed: save exceeds " + MAX_SAVE_BYTES + " bytes");
                return false;
            }
        } catch (IOException ex) {
            System.err.println("Load failed: " + ex);
            return false;
        }

        // Loading is destructive because newWorld releases the current GPU
        // meshes and resets long-lived systems. Prove the immutable payload on
        // an isolated graph before touching a live session. Title-screen loads
        // have no state to protect and keep the one-pass fast path.
        if (g.world != null || g.player != null) {
            Game verifier = new Game();
            if (!loadPayload(verifier, payload)) {
                return false;
            }
        }
        return loadPayload(g, payload);
    }

    private static boolean loadPayload(Game g, byte[] payload) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
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
            if (generatorVersion < World.GEN_LEGACY
                    || generatorVersion > World.CURRENT_GENERATOR) {
                throw new IOException("unsupported world generator version " + generatorVersion);
            }
            g.newWorld(seed, false, generatorVersion);
            if (version == 2) {
                System.out.println("[save] migrating v2 save: legacy terrain preserved; "
                        + "settlements and deep caves need a new world.");
            }

            g.time.totalMinutes = in.readDouble();

            WeatherSystem.Weather[] weathers = WeatherSystem.Weather.values();
            g.weather.current = weathers[readOrdinal(in, "current weather", weathers.length)];
            g.weather.next = weathers[readOrdinal(in, "next weather", weathers.length)];
            g.weather.blend = in.readFloat();
            g.weather.changeTimer = in.readFloat();

            var p = g.player;
            p.pos.set(readFinite(in, "player x"), readFinite(in, "player y"),
                    readFinite(in, "player z"));
            g.camera.yaw = readFinite(in, "camera yaw");
            g.camera.pitch = readFinite(in, "camera pitch");
            p.health = readFinite(in, "health");
            p.hunger = readFinite(in, "hunger");
            p.thirst = readFinite(in, "thirst");
            p.stamina = readFinite(in, "stamina");
            p.bodyTemp = readFinite(in, "body temperature");
            p.fatigue = readFinite(in, "fatigue");
            p.wetness = readFinite(in, "wetness");
            p.protein = readFinite(in, "protein");
            p.vitamins = readFinite(in, "vitamins");
            p.smokeExposure = readFinite(in, "smoke exposure");
            p.woundClean = in.readBoolean();
            // The hotbar index reaches the inventory on the first frame after a
            // load, so an out-of-range one crashes play rather than loading.
            p.hotbarSel = readOrdinal(in, "hotbar slot", PlayerConstants.HOTBAR_SLOTS);

            p.afflictions.clear();
            int nAfflictions = readCount(in, "affliction");
            Affliction[] afflictionTypes = Affliction.values();
            for (int i = 0; i < nAfflictions; i++) {
                int ord = in.readInt();
                float secs = in.readFloat();
                if (ord >= 0 && ord < afflictionTypes.length) {
                    p.afflictions.put(afflictionTypes[ord], secs);
                }
            }

            p.blueprints.clear();
            int nBlueprints = readCount(in, "blueprint");
            for (int i = 0; i < nBlueprints; i++) {
                p.blueprints.add(in.readUTF());
            }

            readInventory(in, p.inventory, version);
            for (int i = 0; i < EquipSlot.values().length; i++) {
                p.equipment[i] = readStack(in, version);
            }

            // Changed blocks: ensure target chunks exist, then apply. The
            // capacity hint is clamped separately from the bound, so a hostile
            // length cannot reserve gigabytes before the first read fails.
            int nBlocks = readCount(in, "changed block", MAX_SERIALIZED_BLOCK_EDITS);
            List<int[]> blocks = new ArrayList<>(Math.min(nBlocks, 4096));
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
            int nFires = readCount(in, "campfire");
            for (int i = 0; i < nFires; i++) {
                g.world.campfireFuel.put(readVec(in), in.readFloat());
            }

            // Crates.
            Map<Vec3i, Inventory> crates = new HashMap<>();
            int nCrates = readCount(in, "crate");
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
            int nRacks = readCount(in, "drying rack");
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
            int nCollectors = readCount(in, "rain collector");
            for (int i = 0; i < nCollectors; i++) {
                g.world.collectorWater.put(readVec(in), in.readFloat());
            }

            // Discovered POIs (re-flag the ones regenerated with the world).
            g.world.discoveredPois.clear();
            int nDiscovered = readCount(in, "discovered POI");
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
            int nNpcs = readCount(in, "NPC");
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
            int nCreatures = readCount(in, "creature");
            for (int i = 0; i < nCreatures; i++) {
                Creature c = new Creature(g.world,
                        types[readOrdinal(in, "creature type", types.length)]);
                c.pos.set(in.readFloat(), in.readFloat(), in.readFloat());
                c.health = in.readFloat();
                c.hunger = in.readFloat();
                c.bleedTimer = in.readFloat();
                g.entities.creatures.add(c);
            }

            // Carcasses.
            g.entities.carcasses.clear();
            int nCarcasses = readCount(in, "carcass");
            for (int i = 0; i < nCarcasses; i++) {
                Carcass c = new Carcass(types[readOrdinal(in, "carcass type", types.length)],
                        in.readFloat(), in.readFloat(), in.readFloat());
                c.meatLeft = in.readInt();
                c.hideLeft = in.readInt();
                c.decay = in.readFloat();
                g.entities.carcasses.add(c);
            }

            // Events.
            g.events.active.clear();
            EventSystem.EventType[] eventTypes = EventSystem.EventType.values();
            int nEvents = readCount(in, "active event");
            for (int i = 0; i < nEvents; i++) {
                g.events.active.add(new EventSystem.ActiveEvent(
                        eventTypes[readOrdinal(in, "event type", eventTypes.length)],
                        in.readFloat(), in.readFloat()));
            }

            // Event log.
            g.eventLog.clear();
            int nLog = readCount(in, "event log line");
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
        } catch (RuntimeException ex) {
            // Defence in depth for the validation above. Every known bad-input
            // path now throws IOException, but an unchecked exception escaping
            // here would leave Game.run with no world and take the process with
            // it, which is strictly worse than reporting a failed load.
            System.err.println("Load failed on malformed save data: " + ex);
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
        V3ExtensionSections.write(out, g);
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
            V3ExtensionSections.read(in, g);
        }
        if (in.available() != 0) {
            throw new IOException("unexpected bytes after v3 extension");
        }
    }

    static int readCount(DataInputStream in, String label) throws IOException {
        return readCount(in, label, MAX_SERIALIZED_ENTRIES);
    }

    /**
     * Reads an entry count that a damaged file may have chosen freely.
     *
     * <p>The bound matters more than its exact value: without one, a corrupt
     * length reaches {@code new ArrayList<>(n)} and throws
     * {@link IllegalArgumentException} or {@link OutOfMemoryError} — neither of
     * which is an {@link IOException}, so neither is caught as a failed load.
     */
    static int readCount(DataInputStream in, String label, int max) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > max) {
            throw new IOException("invalid " + label + " count: " + count);
        }
        return count;
    }

    /**
     * Reads a serialized enum ordinal, rejecting anything outside the enum.
     *
     * <p>Indexing {@code values()} with an unchecked ordinal throws
     * {@link ArrayIndexOutOfBoundsException}, which escapes the load guard and
     * takes the process with it. Every ordinal in the core body goes through
     * here so a bad one is an ordinary failed load.
     */
    private static int readOrdinal(DataInputStream in, String label, int count)
            throws IOException {
        int ordinal = in.readInt();
        if (ordinal < 0 || ordinal >= count) {
            throw new IOException("invalid " + label + " ordinal: " + ordinal);
        }
        return ordinal;
    }

    /** Reads a scalar that must be a real number; NaN player state is unplayable. */
    private static float readFinite(DataInputStream in, String label) throws IOException {
        float value = in.readFloat();
        if (!Float.isFinite(value)) {
            throw new IOException("non-finite " + label + ": " + value);
        }
        return value;
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

    static String requireFaction(String id) throws IOException {
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

    static void writeVec(DataOutputStream out, Vec3i v) throws IOException {
        out.writeInt(v.x());
        out.writeInt(v.y());
        out.writeInt(v.z());
    }

    static Vec3i readVec(DataInputStream in) throws IOException {
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
        int size = readCount(in, "inventory slot", PlayerConstants.INVENTORY_SLOTS);
        for (int i = 0; i < size; i++) {
            ItemStack stack = readStack(in, version);
            if (i < inv.size()) {
                inv.set(i, stack);
            }
        }
    }
}
