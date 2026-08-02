package com.veylon;

import com.veylon.entity.Npc;
import com.veylon.item.Station;
import com.veylon.simulation.ShelterSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.Chunk;
import com.veylon.world.Poi;

import java.util.EnumSet;
import java.util.Set;

/**
 * Where the player's body meets the world: shelter, smoke, volcanic vents,
 * discovery, and the crafting stations within reach.
 *
 * <p>These are the parts of the tick that are neither a self-contained
 * simulation system nor a player action — they read world state near the player
 * and push consequences onto the player, so they never fitted in
 * {@code simulation/} and were left inline in {@code Game}.
 *
 * <p>The cadence split is deliberate and matches the cost of each query.
 * Shelter and vent proximity change as fast as the player walks, so they run on
 * the medium tick; POI discovery is a cheap distance check over a small list, so
 * it rides along. Station scanning is not on a tick at all — it answers the
 * crafting screen, which only asks while it is open.
 */
final class PlayerEnvironmentSystem {

    /** Smoke absorbed per second under a roof with a fire going. */
    private static final float INDOOR_SMOKE_PER_SECOND = 9f;
    /** Fire heat under a roof that counts as an unventilated fire. */
    private static final float INDOOR_FIRE_HEAT_THRESHOLD = 3f;
    /** Exposure at which visible smoke starts pooling around the player's head. */
    private static final float VISIBLE_SMOKE_EXPOSURE = 30f;
    /** Smoke absorbed per second while standing over a basalt fumarole. */
    private static final float FUMAROLE_SMOKE_PER_SECOND = 14f;
    /** Blocks of radius searched for a fumarole. */
    private static final int FUMAROLE_SEARCH_RADIUS = 5;
    /** Blocks from a point of interest at which it is discovered. */
    private static final float DISCOVERY_RANGE = 15f;
    /** Half-extents of the box searched for crafting stations, in blocks. */
    private static final int STATION_RANGE_XZ = 3;
    private static final int STATION_RANGE_Y = 2;
    /** Chunks beyond the render radius whose GPU meshes are freed. */
    private static final int MESH_KEEP_MARGIN = 3;

    /** POIs whose discovery is also a caving milestone for the camp. */
    private static final Set<Poi.PoiType> UNDERGROUND_POIS = EnumSet.of(
            Poi.PoiType.ABANDONED_MINE, Poi.PoiType.SMUGGLER_CACHE,
            Poi.PoiType.HIDEOUT_CAVE, Poi.PoiType.RESONANT_SHRINE,
            Poi.PoiType.STALKER_NEST, Poi.PoiType.EXPEDITION_CAMP);

    private final Game game;

    PlayerEnvironmentSystem(Game game) {
        this.game = game;
    }

    void mediumTick(float dt) {
        updateShelterAndSmoke(dt);
        game.player.tickToxicFogExposure(game);
        discoverNearbyPois();
    }

    void slowTick() {
        clearCampIllnessWhenRecovered();
        evictDistantChunkMeshes();
    }

    /** Roofed fires and volcanic vents both fill the player's lungs. */
    private void updateShelterAndSmoke(float dt) {
        var player = game.player;
        player.shelter = ShelterSystem.evaluate(game.world,
                player.pos.x, player.pos.y, player.pos.z);
        if (player.shelter.indoor() && player.nearFireHeat(game) > INDOOR_FIRE_HEAT_THRESHOLD) {
            player.smokeExposure += INDOOR_SMOKE_PER_SECOND * dt;
            if (player.smokeExposure > VISIBLE_SMOKE_EXPOSURE) {
                game.particles.smoke(player.pos.x, player.pos.y + 1.9f, player.pos.z, 0.4f);
            }
        }
        Vec3i fumarole = game.world.nearestBasaltFumarole(player.pos.x,
                player.pos.y + 0.5f, player.pos.z, FUMAROLE_SEARCH_RADIUS);
        if (fumarole != null) {
            player.smokeExposure += FUMAROLE_SMOKE_PER_SECOND * dt;
            game.particles.smoke(fumarole.x() + 0.5f, fumarole.y() + 0.35f,
                    fumarole.z() + 0.5f, 0.75f);
        }
    }

    private void discoverNearbyPois() {
        var player = game.player;
        for (Poi poi : game.world.pois) {
            if (poi.discovered
                    || poi.pos.distSq(player.pos.x, player.pos.y, player.pos.z)
                            >= DISCOVERY_RANGE * DISCOVERY_RANGE) {
                continue;
            }
            poi.discovered = true;
            // Recorded by position so discovery survives world regeneration.
            game.world.discoveredPois.add(poi.pos);
            game.audio.playDiscover();
            game.log("DISCOVERED: " + poi.type.displayName + " (marked on your map)");
            if (poi.type == Poi.PoiType.PREDATOR_DEN) {
                game.log("Bones and claw marks everywhere... wolves den here.");
            }
            game.faction.onPoiDiscovered(game, poi);
            if (UNDERGROUND_POIS.contains(poi.type)) {
                game.faction.onCaveExplored(game, poi);
            }
        }
    }

    /** The camp illness event ends when the last sick resident recovers. */
    private void clearCampIllnessWhenRecovered() {
        for (Npc n : game.entities.npcs) {
            if (n.sick && !n.dead) {
                return;
            }
        }
        game.events.onCampCured();
    }

    /**
     * Frees GPU meshes for chunks the player has walked away from. Block data
     * stays resident, so walking back rebuilds the mesh rather than the world.
     */
    private void evictDistantChunkMeshes() {
        int pcx = Math.floorDiv((int) Math.floor(game.player.pos.x), Chunk.SX);
        int pcz = Math.floorDiv((int) Math.floor(game.player.pos.z), Chunk.SZ);
        int keep = game.renderer.renderRadius() + MESH_KEEP_MARGIN;
        for (Chunk c : game.world.loadedChunks()) {
            if (c.meshOpaque != null
                    && Math.max(Math.abs(c.cx - pcx), Math.abs(c.cz - pcz)) > keep) {
                c.deleteMeshes();
                game.chunkMeshDeletes++;
            }
        }
    }

    /** Crafting stations within working distance of the player. */
    Set<Station> nearbyStations() {
        EnumSet<Station> found = EnumSet.of(Station.HAND);
        int px = (int) Math.floor(game.player.pos.x);
        int py = (int) Math.floor(game.player.pos.y);
        int pz = (int) Math.floor(game.player.pos.z);
        for (int dx = -STATION_RANGE_XZ; dx <= STATION_RANGE_XZ; dx++) {
            for (int dy = -STATION_RANGE_Y; dy <= STATION_RANGE_Y; dy++) {
                for (int dz = -STATION_RANGE_XZ; dz <= STATION_RANGE_XZ; dz++) {
                    addStationAt(found, px + dx, py + dy, pz + dz);
                }
            }
        }
        return found;
    }

    private void addStationAt(EnumSet<Station> found, int x, int y, int z) {
        switch (game.world.getBlock(x, y, z)) {
            case WORKBENCH -> found.add(Station.WORKBENCH);
            // An unlit campfire is a fire pit, not a cooking station.
            case CAMPFIRE -> {
                if (game.world.campfireFuel.getOrDefault(new Vec3i(x, y, z), 0f) > 0) {
                    found.add(Station.CAMPFIRE);
                }
            }
            case FURNACE -> found.add(Station.FURNACE);
            case ANVIL -> found.add(Station.ANVIL);
            case TANNERY -> found.add(Station.TANNERY);
            case HERB_STATION -> found.add(Station.HERB_STATION);
            case MAP_TABLE -> found.add(Station.MAP_TABLE);
            default -> {
            }
        }
    }
}
