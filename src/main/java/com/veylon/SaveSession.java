package com.veylon;

import com.veylon.entity.Player;
import com.veylon.ui.CityMap;
import com.veylon.world.World;
import com.veylon.world.urban.DistrictRules;
import com.veylon.world.urban.UrbanDistrict;

/**
 * What this session's saves need beyond the world itself: how long the live world has been played,
 * and the words a save's summary uses for where the survivor stands (0.9.1 milestone 04). Neither
 * changes anything the simulation does.
 */
public final class SaveSession {

    private final Game game;
    /** Real seconds the live world has been simulated, over every session that played it. */
    private double playSeconds;

    SaveSession(Game game) {
        this.game = game;
    }

    /** Real seconds the live world has been played; what a save's summary records. */
    public double playSeconds() {
        return playSeconds;
    }

    /** Called by a load with what the save's summary recorded. */
    public void restorePlayTime(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0) {
            throw new IllegalArgumentException("invalid play time: " + seconds);
        }
        playSeconds = seconds;
    }

    /** A simulated frame of {@code realSeconds}. */
    void countPlay(double realSeconds) {
        playSeconds += realSeconds;
    }

    /** A new or loaded world starts its count again; a load then restores the saved one. */
    void resetPlayTime() {
        playSeconds = 0;
    }

    /**
     * Where the survivor stands, for a save's summary: in a city its district and the HUD's line for
     * the building or ground ("Clinic · floor 2", "Street"); elsewhere the district or the biome.
     * Read from the world, not from the per-tick trackers, so a world saved before its first frame
     * still names its place.
     */
    public String placeName() {
        World world = game.world;
        Player player = game.player;
        if (world == null || player == null) {
            return "";
        }
        int x = (int) Math.floor(player.pos.x), z = (int) Math.floor(player.pos.z);
        if (CityMap.applies(world)) {
            String district = CityMap.districtName(world, x, z);
            return (district == null ? "Beyond the city plan" : district) + " · " + CityMap.hereLine(game);
        }
        if (world.urban != null && world.urban.hasDistricts()) {
            UrbanDistrict district = world.urban.districtAt(x, z);
            if (district != null) {
                return DistrictRules.of(district).displayName();
            }
        }
        return world.biomeAt(x, z).displayName;
    }
}
