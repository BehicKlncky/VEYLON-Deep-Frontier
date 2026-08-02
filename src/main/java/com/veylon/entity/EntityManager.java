package com.veylon.entity;

import com.veylon.Game;
import com.veylon.ai.CreatureAI;
import com.veylon.ai.NpcAI;
import com.veylon.item.ItemType;
import com.veylon.world.Biome;
import com.veylon.world.Poi;
import com.veylon.world.World;
import com.veylon.world.WorldGenerator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

public class EntityManager {

    private static final int MAX_TRACKS = 420;

    public final List<Creature> creatures = new ArrayList<>();
    public final List<Npc> npcs = new ArrayList<>();
    public final List<Carcass> carcasses = new ArrayList<>();
    /** Footprints and blood marks, oldest first. */
    public final ArrayDeque<Track> tracks = new ArrayDeque<>();
    private final Random rng = new Random();
    private final Random creatureAiRng = new Random();
    private final Random npcAiRng = new Random();
    private final Random settledNpcAiRng = new Random();

    public void fastTick(Game g, float dt) {
        for (Iterator<Creature> it = creatures.iterator(); it.hasNext(); ) {
            Creature c = it.next();
            CreatureAI.update(g, c, dt);
            c.applyPhysics(dt, !c.type.flying);
            updateTrail(c, dt);
            if (c.dead) {
                onCreatureDied(g, c);
                it.remove();
            }
        }
        for (Iterator<Npc> it = npcs.iterator(); it.hasNext(); ) {
            Npc n = it.next();
            NpcAI.update(g, n, dt);
            if (!n.abstractTravel) {
                n.applyPhysics(dt, true);
            }
            if (n.dead) {
                if (n.isTrader && n.partyMissionId != null && !n.partyMissionId.isBlank()) {
                    g.faction.failMission(n.partyMissionId, "the escorted trader was lost");
                }
                if (n.lastHitByPlayer && n.faction != null) {
                    g.faction.addTrust(g, -40, "You killed " + n.name + "!");
                }
                if (n.health <= 0 && !n.raider && !n.hostileToPlayer()) {
                    g.log(n.name + " has died.");
                }
                if (n.raider && n.lastHitByPlayer) {
                    g.player.inventory.add(ItemType.SCRAP, 1 + rng.nextInt(2));
                    if (rng.nextFloat() < 0.4f) {
                        g.player.inventory.add(ItemType.BANDAGE, 1);
                    }
                    g.log("You looted the fallen scavenger.");
                    g.onRaiderKilled();
                }
                if (n.settled() || n.warParty) {
                    onSettledNpcDied(g, n);
                }
                it.remove();
            }
        }
    }

    /** Settlement/war-party NPC died: resident bookkeeping + archetype loot. */
    private void onSettledNpcDied(Game g, Npc n) {
        // Mission survivor state changes for every real combat death, regardless
        // of whether the player or an outpost defender landed the final blow.
        if (n.warParty && n.partyKind == Npc.PartyKind.COUNTERATTACK) {
            g.settlementManager.counterattacks.onMemberDied(n);
        }
        if (n.warParty && !n.lastHitByPlayer
                && n.partyMission == Npc.PartyMission.RETURNING
                && g.entities.npcs.stream().noneMatch(other -> other != n && !other.dead
                && n.partyMissionId.equals(other.partyMissionId))) {
            g.faction.failMission(n.partyMissionId, "the hostile party escaped");
        }
        if (n.lastHitByPlayer) {
            if (n.settled()) {
                g.settlementManager.onNpcKilledByPlayer(g, n);
            }
            // Cutting down raiders near a community earns its gratitude.
            if (n.warParty) {
                g.faction.onWarPartyKill(g, n.partyMissionId, n.originSettlementId,
                        n.partyTargetSettlementId, n.partyFactionId,
                        n.partyMemberId == null || n.partyMemberId.isBlank()
                                ? n.partyMissionId + ":" + n.name + ":" + n.hashCode()
                                : n.partyMemberId);
                g.settlementManager.onSettlementDefended(g, n.partyTargetSettlementId,
                        n.pos.x, n.pos.z);
            }
            if (n.hostileToPlayer() && n.archetype != null) {
                switch (n.archetype) {
                    case SCOUT -> g.player.inventory.add(ItemType.ARROW, 2 + rng.nextInt(4));
                    case POWDERMAN -> {
                        g.player.inventory.add(ItemType.BLACK_POWDER, 1 + rng.nextInt(2));
                        if (rng.nextFloat() < 0.5f) {
                            g.player.inventory.add(ItemType.MUSKET_BALL, 1 + rng.nextInt(3));
                        }
                    }
                    case LEADER -> {
                        g.player.inventory.add(ItemType.IRON_INGOT, 1 + rng.nextInt(2));
                        g.player.inventory.add(ItemType.SCRAP, 2 + rng.nextInt(3));
                    }
                    default -> {
                        if (rng.nextFloat() < 0.6f) {
                            g.player.inventory.add(ItemType.SCRAP, 1 + rng.nextInt(2));
                        }
                    }
                }
                g.log("You search the fallen " + n.jobName().toLowerCase() + ".");
            }
        } else if (n.settled()) {
            // Died to other causes: keep the resident record consistent.
            var s = g.world.settlements.get(n.settlementId);
            if (s != null && n.residentIndex >= 0 && n.residentIndex < s.residents.size()) {
                var resident = s.residents.get(n.residentIndex);
                if (!resident.routed && !resident.surrendered) {
                    resident.alive = false;
                }
                resident.live = null;
            }
        }
    }

    private void onCreatureDied(Game g, Creature c) {
        if (c.lastHitByPlayer) {
            g.onCreatureKilled(c);
        }
        if (c.type == Creature.CreatureType.BIRD) {
            // Too small to leave a carcass.
            if (c.lastHitByPlayer) {
                g.player.inventory.add(ItemType.RAW_MEAT, 1);
                g.log("Hunted a " + c.type.displayName + " (+1 raw meat)");
            }
            return;
        }
        Carcass carcass = new Carcass(c.type, c.pos.x, c.pos.y, c.pos.z);
        carcass.stuckArrows = c.stuckArrows;
        carcass.stuckArrowType = c.stuckArrowType;
        carcasses.add(carcass);
        if (c.lastHitByPlayer) {
            g.log("The " + c.type.displayName + " is down. Harvest the carcass with [F]"
                    + (g.playerHasKnife() ? "." : " (a knife would yield far more)."));
        }
    }

    /** Footprints while moving; blood drips while wounded. */
    private void updateTrail(Creature c, float dt) {
        if (c.type.flying || !c.onGround) {
            return;
        }
        boolean moving = Math.abs(c.vel.x) > 0.4f || Math.abs(c.vel.z) > 0.4f;
        c.bleedTimer = Math.max(0, c.bleedTimer - dt);
        c.trackTimer -= dt;
        if (c.trackTimer <= 0 && moving) {
            c.trackTimer = c.bleedTimer > 0 ? 1.0f : 2.2f;
            addTrack(new Track(c.pos.x, c.pos.y + 0.02f, c.pos.z, c.yaw, c.type, c.bleedTimer > 0));
        }
    }

    public void addTrack(Track t) {
        tracks.addLast(t);
        while (tracks.size() > MAX_TRACKS) {
            tracks.removeFirst();
        }
    }

    /** Ages tracks and rots carcasses; called on the slow tick. */
    public void tickWorldDetritus(Game g, float dt) {
        for (Iterator<Track> it = tracks.iterator(); it.hasNext(); ) {
            Track t = it.next();
            t.age += dt;
            // Rain washes tracks away quickly.
            if (g.weather.isPrecip()) {
                t.age += dt * 2.5f;
            }
            if (t.age > Track.MAX_AGE) {
                it.remove();
            }
        }
        for (Iterator<Carcass> it = carcasses.iterator(); it.hasNext(); ) {
            Carcass c = it.next();
            c.decay -= dt;
            if (c.decay <= 0 || c.empty()) {
                it.remove();
            }
        }
    }

    public Track nearestTrack(float x, float y, float z, float range) {
        Track best = null;
        double bestD = range * range;
        for (Track t : tracks) {
            double dx = t.x - x, dy = t.y - y, dz = t.z - z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = t;
            }
        }
        return best;
    }

    public Carcass nearestCarcass(float x, float y, float z, float range) {
        Carcass best = null;
        double bestD = range * range;
        for (Carcass c : carcasses) {
            double dx = c.pos.x - x, dy = c.pos.y - y, dz = c.pos.z - z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    /** Population control: spawn/despawn wildlife around the player. */
    public void slowTick(Game g) {
        float px = g.player.pos.x, pz = g.player.pos.z;

        creatures.removeIf(c -> c.distSqTo(px, c.pos.y, pz) > 170 * 170);

        int deer = 0, wolves = 0, birds = 0, hares = 0, thornhorns = 0, stalkers = 0;
        for (Creature c : creatures) {
            switch (c.type) {
                case DEER -> deer++;
                case WOLF -> wolves++;
                case BIRD -> birds++;
                case HARE -> hares++;
                case THORNHORN -> thornhorns++;
                case STALKER -> stalkers++;
            }
        }
        float seasonMul = g.seasons.spawnMul(g.time);
        int wolfCap = 4 + g.events.wolfCapBonus();
        boolean night = g.time.isNight();
        if (deer < 10 * seasonMul && rng.nextFloat() < 0.6f) {
            trySpawn(g, Creature.CreatureType.DEER, px, pz);
        }
        if (hares < 8 * seasonMul && rng.nextFloat() < 0.55f) {
            trySpawn(g, Creature.CreatureType.HARE, px, pz);
        }
        if (thornhorns < 3 * seasonMul && rng.nextFloat() < 0.3f) {
            trySpawn(g, Creature.CreatureType.THORNHORN, px, pz);
        }
        if (wolves < wolfCap && rng.nextFloat() < (night ? 0.5f : 0.25f)) {
            trySpawnWolf(g, px, pz);
        }
        if (birds < 6 && rng.nextFloat() < 0.4f) {
            trySpawn(g, Creature.CreatureType.BIRD, px, pz);
        }
        // Gloomstalkers are the stronger Basalt-depth predator. Root Caves
        // retain ordinary wildlife and do not inherit this spawn pressure.
        int playerSurface = g.world.surfaceHeight((int) px, (int) pz);
        WorldGenerator.CaveZone playerZone = g.world.generator.caveZoneAt(
                (int) px, (int) g.player.pos.y, (int) pz);
        boolean playerInBasalt = g.world.generatorVersion >= World.GEN_CAVE_IDENTITIES
                ? (playerZone == WorldGenerator.CaveZone.BASALT
                || playerZone == WorldGenerator.CaveZone.RESONANT)
                : g.player.pos.y < playerSurface - 6;
        if (playerInBasalt && stalkers < 2 && rng.nextFloat() < 0.4f) {
            trySpawnStalker(g, px, pz);
        }
    }

    /** Wolves prefer to appear near their dens when one is nearby. */
    private void trySpawnWolf(Game g, float px, float pz) {
        for (Poi poi : g.world.pois) {
            if (poi.type == Poi.PoiType.PREDATOR_DEN
                    && poi.pos.distSq(px, poi.pos.y(), pz) < 90 * 90
                    && rng.nextFloat() < 0.6f) {
                int denSurface = g.world.generator.heightAt(poi.pos.x(), poi.pos.z());
                boolean underground = poi.pos.y() <= denSurface - 5;
                if (underground) {
                    if (Math.abs(g.player.pos.y - poi.pos.y()) <= 10
                            && trySpawnUndergroundWolf(g, px, pz, poi)) {
                        return;
                    }
                    continue;
                }
                int x = poi.pos.x() + rng.nextInt(9) - 4;
                int z = poi.pos.z() + rng.nextInt(9) - 4;
                if (g.world.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16)) != null) {
                    int y = g.world.surfaceHeight(x, z) + 1;
                    if (y > World.SEA_LEVEL) {
                        Creature wolf = spawnCreature(g.world, Creature.CreatureType.WOLF,
                                x + 0.5f, y + 0.1f, z + 0.5f);
                        wolf.yaw = rng.nextFloat() * 360;
                        return;
                    }
                }
            }
        }
        trySpawn(g, Creature.CreatureType.WOLF, px, pz);
    }

    boolean trySpawnUndergroundWolf(Game g, float px, float pz, Poi den) {
        for (int attempt = 0; attempt < 16; attempt++) {
            int x = den.pos.x() + rng.nextInt(9) - 4;
            int y = den.pos.y();
            int z = den.pos.z() + rng.nextInt(9) - 4;
            double dx = x + 0.5 - px, dz = z + 0.5 - pz;
            if (dx * dx + dz * dz <= 10 * 10
                    || g.world.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16)) == null
                    || !g.world.getBlock(x, y, z).isAir()
                    || !g.world.getBlock(x, y + 1, z).isAir()
                    || !g.world.getBlock(x, y - 1, z).solid) {
                continue;
            }
            Creature wolf = spawnCreature(g.world, Creature.CreatureType.WOLF,
                    x + 0.5f, y + 0.1f, z + 0.5f);
            wolf.yaw = rng.nextFloat() * 360;
            return true;
        }
        return false;
    }

    /** Gloomstalkers prefer an established nest, then any nearby dark pocket. */
    public boolean trySpawnStalker(Game g, float px, float pz) {
        for (Poi poi : g.world.pois) {
            if (poi.type != Poi.PoiType.STALKER_NEST
                    || poi.pos.distSq(px, poi.pos.y(), pz) > 90 * 90) {
                continue;
            }
            for (int attempt = 0; attempt < 16; attempt++) {
                int x = poi.pos.x() + rng.nextInt(15) - 7;
                int y = poi.pos.y();
                int z = poi.pos.z() + rng.nextInt(15) - 7;
                if (spawnStalkerIfValid(g, px, pz, x, y, z)) {
                    return true;
                }
            }
        }
        // Find a dark air pocket underground near the player.
        for (int attempt = 0; attempt < 10; attempt++) {
            int x = (int) (px + rng.nextInt(41) - 20);
            int z = (int) (pz + rng.nextInt(41) - 20);
            int y = (int) (g.player.pos.y + rng.nextInt(9) - 4);
            if (spawnStalkerIfValid(g, px, pz, x, y, z)) {
                return true;
            }
        }
        return false;
    }

    private boolean spawnStalkerIfValid(Game g, float px, float pz, int x, int y, int z) {
        if (y < 3 || g.world.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16)) == null) {
            return false;
        }
        double dx = x - px, dz = z - pz;
        if (dx * dx + dz * dz <= 12 * 12
                || !g.world.getBlock(x, y, z).isAir()
                || !g.world.getBlock(x, y + 1, z).isAir()
                || !g.world.getBlock(x, y - 1, z).solid
                || g.world.skyLight(x, y, z) >= 0.3f
                || g.world.blockLight(x, y, z) >= 0.2f) {
            return false;
        }
        spawnCreature(g.world, Creature.CreatureType.STALKER, x + 0.5f, y + 0.1f, z + 0.5f);
        return true;
    }

    public void setRandomSeed(long seed) {
        rng.setSeed(seed);
    }

    /** Seeds the three independent AI decision streams for this game instance. */
    public void setAiRandomSeed(long worldSeed) {
        creatureAiRng.setSeed(worldSeed ^ 0x435245415455L);
        npcAiRng.setSeed(worldSeed ^ 0x4c454741434eL);
        settledNpcAiRng.setSeed(worldSeed ^ 0x5345544e5043L);
    }

    public float nextCreatureAiFloat() {
        return creatureAiRng.nextFloat();
    }

    public int nextCreatureAiInt(int bound) {
        return creatureAiRng.nextInt(bound);
    }

    public float nextNpcAiFloat() {
        return npcAiRng.nextFloat();
    }

    public double nextNpcAiDouble() {
        return npcAiRng.nextDouble();
    }

    public int nextNpcAiInt(int bound) {
        return npcAiRng.nextInt(bound);
    }

    public float nextSettledNpcAiFloat() {
        return settledNpcAiRng.nextFloat();
    }

    public int nextSettledNpcAiInt(int bound) {
        return settledNpcAiRng.nextInt(bound);
    }

    private void trySpawn(Game g, Creature.CreatureType type, float px, float pz) {
        double ang = rng.nextDouble() * Math.PI * 2;
        double dist = 40 + rng.nextDouble() * 60;
        int x = (int) (px + Math.cos(ang) * dist);
        int z = (int) (pz + Math.sin(ang) * dist);
        World world = g.world;
        if (world.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16)) == null) {
            return;
        }
        Biome biome = world.biomeAt(x, z);
        if (rng.nextFloat() > biome.animalChance) {
            return;
        }
        int y = world.surfaceHeight(x, z) + 1;
        if (y <= World.SEA_LEVEL) {
            return;
        }
        Creature c = new Creature(world, type);
        c.pos.set(x + 0.5f, y + (type.flying ? 8 : 0.1f), z + 0.5f);
        c.yaw = rng.nextFloat() * 360;
        creatures.add(c);
    }

    public Creature spawnCreature(World world, Creature.CreatureType type, float x, float y, float z) {
        Creature c = new Creature(world, type);
        c.pos.set(x, y, z);
        creatures.add(c);
        return c;
    }

    public Npc spawnNpc(World world, String name, float x, float y, float z) {
        Npc n = new Npc(world, name);
        n.pos.set(x, y, z);
        npcs.add(n);
        return n;
    }

    public Creature nearestCreature(float x, float y, float z, float range, Predicate<Creature> filter) {
        Creature best = null;
        double bestD = range * range;
        for (Creature c : creatures) {
            if (c.dead || !filter.test(c)) {
                continue;
            }
            double d = c.distSqTo(x, y, z);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    public Npc nearestNpc(float x, float y, float z, float range) {
        Npc best = null;
        double bestD = range * range;
        for (Npc n : npcs) {
            if (n.dead) {
                continue;
            }
            double d = n.distSqTo(x, y, z);
            if (d < bestD) {
                bestD = d;
                best = n;
            }
        }
        return best;
    }

    public int creatureCount() {
        return creatures.size();
    }

    public int npcCount() {
        return npcs.size();
    }
}
