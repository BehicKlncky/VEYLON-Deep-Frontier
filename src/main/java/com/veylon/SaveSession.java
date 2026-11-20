package com.veylon;

import com.veylon.entity.Player;
import com.veylon.save.LoadResult;
import com.veylon.save.SaveSlots;
import com.veylon.ui.CityMap;
import com.veylon.world.World;
import com.veylon.world.urban.DistrictRules;
import com.veylon.world.urban.UrbanDistrict;

import java.util.Objects;

/**
 * This session's saves (0.9.1 milestone 04): the three slots it reads and writes, the one the live
 * world belongs to, how long that world has been played, and the words a save's summary uses for
 * where the survivor stands. The active slot is the one a world was loaded from or started in;
 * F5 saves into it and F9 opens it. None of this changes anything the simulation does.
 */
public final class SaveSession {

    private final Game game;
    /** Real seconds the live world has been simulated, over every session that played it. */
    private double playSeconds;
    /** The player's slots, or a test's or QA run's own folder of them. */
    private SaveSlots slots = SaveSlots.inDataDirectory();
    /** The slot the live world belongs to. */
    private int active = 1;

    SaveSession(Game game) {
        this.game = game;
    }

    /** The slots this session reads and writes. */
    public SaveSlots slots() {
        return slots;
    }

    /** Points the session at another folder of slots: a test's, or the release smoke's. */
    void useSlots(SaveSlots slots) {
        this.slots = Objects.requireNonNull(slots, "slots");
    }

    /** The slot the live world belongs to, which F5 saves into and F9 opens. */
    public int activeSlot() {
        return active;
    }

    /** Makes {@code slot} the active one without saving or loading anything. */
    void activate(int slot) {
        slots.current(slot); // refuses a slot that does not exist
        active = slot;
    }

    /** F5: saves the live world into the active slot. */
    boolean saveActive() {
        return slots.save(game, active);
    }

    /** F9: opens the active slot over the live world, which a refused or failed load keeps. */
    LoadResult openActive() {
        return slots.open(game, active);
    }

    /** LOAD FRONTIER: opens {@code slot}; once it has loaded, it is the active slot. */
    LoadResult load(int slot) {
        LoadResult result = slots.open(game, slot);
        if (result.loaded()) {
            active = slot;
        }
        return result;
    }

    /** Restores a damaged slot from one of its backups; once it has loaded, it is the active slot. */
    LoadResult restore(int slot, int generation) {
        LoadResult result = slots.restore(game, slot, generation);
        if (result.loaded()) {
            active = slot;
        }
        return result;
    }

    /**
     * NEW FRONTIER: the new live world belongs to {@code slot} from now on and is saved into it at
     * once, so F9 never brings back what the slot held before and its old save is the newest backup.
     *
     * @return whether the first save was written
     */
    boolean startIn(int slot) {
        activate(slot);
        return slots.save(game, slot);
    }

    /** F5's line after a save. */
    static String savedLine(int slot) {
        return "Saved to slot " + slot + ".";
    }

    /** F5's line when the save failed; the console says why. */
    static String saveFailedLine(int slot) {
        return "Could not save to slot " + slot + ".";
    }

    /** F9's line after a load. */
    static String loadedLine(int slot) {
        return "Loaded slot " + slot + ".";
    }

    /** F9's line when the load did not happen: the slot is empty, or the result's own short line. */
    static String notLoadedLine(int slot, LoadResult result) {
        return result.outcome() == LoadResult.Outcome.NO_SAVE ? "Slot " + slot + " is empty." : result.logLine();
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
