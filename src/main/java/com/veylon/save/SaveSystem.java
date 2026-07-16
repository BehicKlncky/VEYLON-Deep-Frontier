package com.veylon.save;

import com.veylon.Game;
import com.veylon.ai.Quest;
import com.veylon.entity.Affliction;
import com.veylon.entity.Carcass;
import com.veylon.entity.Creature;
import com.veylon.entity.Npc;
import com.veylon.item.EquipSlot;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.simulation.EventSystem;
import com.veylon.simulation.WeatherSystem;
import com.veylon.util.AppPaths;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Poi;
import com.veylon.world.RackBatch;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Binary save format v2. Stores the seed plus every delta from the generated
 * world: changed blocks, structures, item state (durability/freshness),
 * equipment, afflictions, blueprints, racks, collectors, POI discovery,
 * beacon progress, faction quests/upgrades, NPCs, creatures, carcasses,
 * weather, time and active events.
 *
 * <p>Version 1 saves (pre-overhaul prototype) are not migrated; loading one
 * fails with a clear console message and the game keeps running.</p>
 */
public final class SaveSystem {

    private static final int MAGIC = 0x5645594C; // "VEYL"
    private static final int VERSION = 2;

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

                // NPCs.
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
            if (version != VERSION) {
                System.err.println("Save is version " + version + "; this build reads version "
                        + VERSION + ". Old prototype saves can't be migrated - start a new world.");
                return false;
            }
            long seed = in.readLong();
            g.newWorld(seed, false);

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

            readInventory(in, p.inventory);
            for (int i = 0; i < EquipSlot.values().length; i++) {
                p.equipment[i] = readStack(in);
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
                readInventory(in, inv);
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
            return true;
        } catch (IOException ex) {
            System.err.println("Load failed: " + ex);
            return false;
        }
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
        } else {
            out.writeInt(s.type.ordinal());
            out.writeInt(s.count);
            out.writeFloat(s.durability);
            out.writeFloat(s.freshness);
        }
    }

    private static ItemStack readStack(DataInputStream in) throws IOException {
        int ord = in.readInt();
        int count = in.readInt();
        float durability = in.readFloat();
        float freshness = in.readFloat();
        ItemType[] types = ItemType.values();
        if (ord < 0 || ord >= types.length || count <= 0) {
            return null;
        }
        ItemStack s = new ItemStack(types[ord], count);
        s.durability = durability;
        s.freshness = freshness;
        return s;
    }

    private static void writeInventory(DataOutputStream out, Inventory inv) throws IOException {
        out.writeInt(inv.size());
        for (int i = 0; i < inv.size(); i++) {
            writeStack(out, inv.get(i));
        }
    }

    private static void readInventory(DataInputStream in, Inventory inv) throws IOException {
        int size = in.readInt();
        for (int i = 0; i < size; i++) {
            ItemStack stack = readStack(in);
            if (i < inv.size()) {
                inv.set(i, stack);
            }
        }
    }
}
