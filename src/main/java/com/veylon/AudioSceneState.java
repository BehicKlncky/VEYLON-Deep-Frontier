package com.veylon;

import com.veylon.engine.AudioEnvironment;
import com.veylon.engine.MusicMood;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementPlanner;
import com.veylon.settlement.SettlementType;
import com.veylon.world.Biome;
import com.veylon.world.BlockType;

/** Read-only acoustic observations, using existing loaded state without planning or simulation RNG. */
final class AudioSceneState {
    /** Maximum entries examined per medium-tick threat query, regardless of list size. */
    private static final int THREAT_SCAN_LIMIT = 64;
    /** Blocks around the listener that can drive music; combat targets carry slightly farther. */
    private static final float THREAT_RADIUS = 24, COMBAT_RADIUS = 48, BEACON_RADIUS = 36;
    /** Blocks below the local surface selecting deep-cave music. */
    private static final float MUSIC_CAVE_DEPTH = 18;
    /** Floor area, in cells, from which a generated room rings like a hall rather than a room. */
    static final int HALL_AREA = 48;
    private AudioSceneState() { }

    /**
     * A generated city's acoustics (urban milestone 22), from the bounded observations the medium
     * tick already makes: the place underfoot, sky exposure, the shelter evaluation, depth below
     * the street datum and the one immutable building plan around the listener. A transit tunnel
     * or service passage rings long; a station concourse and any room of {@value #HALL_AREA} cells
     * or more is a hall; anything below the street is a basement; a covered porch is a shelter;
     * every other indoor room is a room; the street and the roofs are open.
     */
    static AudioEnvironment.Zone urbanZone(Game game, int x, int z, float depth) {
        var player = game.player;
        var place = player.place;
        if (place == com.veylon.world.urban.UrbanPlaces.Place.TUNNEL
                || place == com.veylon.world.urban.UrbanPlaces.Place.SERVICE_PASSAGE) {
            return AudioEnvironment.Zone.TUNNEL;
        }
        if (place == com.veylon.world.urban.UrbanPlaces.Place.STATION) { return AudioEnvironment.Zone.HALL; }
        if (player.exposedToSky) { return AudioEnvironment.Zone.OPEN; }
        if (depth > 0.5f) { return AudioEnvironment.Zone.BASEMENT; }
        if (!player.shelter.indoor()) { return AudioEnvironment.Zone.SHELTER; }
        var location = game.world.urbanLocation(x, (int) Math.floor(player.pos.y + .01f), z);
        var room = location == null ? null : location.room();
        if (room == null) { return AudioEnvironment.Zone.ROOM; }
        var r = room.interior();
        int area = (Math.abs(r.x1() - r.x0()) + 1) * (Math.abs(r.z1() - r.z0()) + 1);
        return area >= HALL_AREA ? AudioEnvironment.Zone.HALL : AudioEnvironment.Zone.ROOM;
    }

    /**
     * How much of the weather outside reaches a listener in a generated city, 0 to 1 (urban
     * milestone 22): all of it in the open, most of it under a porch, a little deep inside a room
     * and more beside a window ({@code daylight} is the sky light at the ear, 0.12 deep inside, up
     * to 0.72 at a pane), a trace in a basement and almost none in a tunnel.
     */
    static float weatherReach(AudioEnvironment.Zone zone, float daylight) {
        return switch (zone) {
            case OPEN -> 1f;
            case SHELTER -> 0.6f;
            case ROOM, HALL -> 0.16f + 0.39f * Math.max(0f, Math.min(1f, (daylight - 0.12f) / 0.6f));
            case BASEMENT -> 0.06f;
            case TUNNEL -> 0.03f;
            default -> 0.45f;
        };
    }

    static MusicMood music(Game game, float depth) {
        var player = game.player;
        if (game.world.urban != null && game.world.urban.hasGeneratedBuildings()) {
            depth = game.world.environmentDepth((int) Math.floor(player.pos.x), player.pos.y,
                    (int) Math.floor(player.pos.z));
        }
        for (int i = 0, count = Math.min(THREAT_SCAN_LIMIT, game.entities.creatures.size()); i < count; i++) {
            var creature = game.entities.creatures.get(i);
            float radius = creature.targetEntity == player ? COMBAT_RADIUS : THREAT_RADIUS;
            if (!creature.dead && (creature.type.predator || creature.targetEntity == player)
                    && creature.distSqTo(player) < radius * radius) return MusicMood.THREAT;
        }
        for (int i = 0, count = Math.min(THREAT_SCAN_LIMIT, game.entities.npcs.size()); i < count; i++) {
            var npc = game.entities.npcs.get(i);
            float radius = npc.combatTarget == player ? COMBAT_RADIUS : THREAT_RADIUS;
            if (!npc.dead && !npc.abstractTravel && (npc.hostileToPlayer() || npc.combatTarget == player)
                    && npc.distSqTo(player) < radius * radius) return MusicMood.THREAT;
        }
        if (game.world.beaconStage >= 3 && game.world.beaconPos != null
                && game.world.beaconPos.distSq(player.pos.x, player.pos.y, player.pos.z) < BEACON_RADIUS * BEACON_RADIUS)
            return MusicMood.BEACON;
        if (depth >= MUSIC_CAVE_DEPTH) return MusicMood.DEEP_CAVE;
        if (game.time.isNight()) return game.time.day() == 1 ? MusicMood.FIRST_NIGHT : MusicMood.NIGHT;
        return MusicMood.CALM;
    }

    static AudioEnvironment.Zone environment(Game game, float depth) {
        int x = (int) Math.floor(game.player.pos.x), z = (int) Math.floor(game.player.pos.z);
        if (game.world.urban != null && game.world.urban.hasGeneratedBuildings()) {
            return urbanZone(game, x, z, game.world.environmentDepth(x, game.player.pos.y, z));
        }
        Settlement settlement = game.world.settlements.get(Settlement.packId(
                SettlementPlanner.regionOfBlock(x), SettlementPlanner.regionOfBlock(z)));
        boolean stone = settlement != null && settlement.containsBlock(x, z)
                && (settlement.type == SettlementType.CASTLE || settlement.type == SettlementType.FORTRESS);
        BlockType floor = game.world.getBlock(x, (int) Math.floor(game.player.pos.y) - 1, z);
        stone |= floor == BlockType.RUIN_STONE || floor == BlockType.STONE_BRICK;
        return AudioEnvironment.classify(depth, game.player.exposedToSky,
                game.player.biome == Biome.PINE_FOREST, stone);
    }
}
