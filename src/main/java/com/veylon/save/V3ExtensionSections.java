package com.veylon.save;

import com.veylon.Game;
import com.veylon.ai.Quest;
import com.veylon.combat.ExplosionSystem;
import com.veylon.combat.ProjectileSystem;
import com.veylon.entity.Npc;
import com.veylon.settlement.CounterattackDirector;
import com.veylon.settlement.CounterattackMission;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementManager;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.veylon.save.SaveSystem.readCount;
import static com.veylon.save.SaveSystem.readVec;
import static com.veylon.save.SaveSystem.requireFaction;
import static com.veylon.save.SaveSystem.writeVec;

/**
 * Owns the optional stable-ID envelope independently of the immutable v3 body.
 * Keeping section codecs here leaves room for additive state without growing
 * the save transaction and legacy migration orchestrator.
 */
final class V3ExtensionSections {

    private V3ExtensionSections() {
    }

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

    /**
     * Extension v2 keeps the v1 payload intact, then adds stable-ID, length-prefixed
     * sections. New systems can append a section without coupling their byte layout
     * to lanterns or requiring another top-level save-format version.
     */
    static void write(DataOutputStream out, Game g)
            throws IOException {
        List<V3Section> sections = new ArrayList<>();
        sections.add(writeLanternSection(g));
        sections.add(writePartyStateSection(g));
        sections.add(writeCounterattackSection(g));
        sections.add(writeQuestTargetSection(g));
        sections.add(writeReputationActionsSection(g));
        sections.add(writeActiveExplosivesSection(g));
        sections.add(writeKegFuseAttributionSection(g));
        sections.add(new V3Section(GameModeSection.ID, GameModeSection.write(g)));
        sections.add(new V3Section(CreativeControlsSection.ID, CreativeControlsSection.write(g)));
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

    static void read(DataInputStream in, Game g) throws IOException {
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
            } else if (GameModeSection.ID.equals(id)) {
                GameModeSection.read(payload, g);
            } else if (CreativeControlsSection.ID.equals(id)) {
                CreativeControlsSection.read(payload, g);
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

}
