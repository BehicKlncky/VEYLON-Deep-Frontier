package com.veylon;

import com.veylon.engine.UtilitySounds;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.item.ToolKind;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.UrbanIndoorQueries;
import com.veylon.world.urban.UrbanUtilities;
import com.veylon.world.urban.UrbanUtilities.Device;
import com.veylon.world.urban.UrbanUtilities.Kind;

import java.util.List;
import java.util.Locale;

/**
 * The water and power of a layout-fifteen city's refuges (urban milestone 19), as the player works
 * them. The world's side, what is left, what was drawn and every device's state, is
 * {@link UrbanUtilities}.
 *
 * <h2>Taps</h2>
 *
 * <p>A planned basin is a tap on its building's pipes. F at one with an empty waterskin in the pack
 * fills it with a litre of stale water, untreated: it must be boiled at a lit fire before it is
 * safe. With no waterskin, F drinks from the tap as from any untreated water, and risks the same
 * sickness. Either way the building's pipes hold one litre less, and once they are dry every tap
 * of the building is. Taps on a storey survivors hold are theirs, and do not run for the player.
 *
 * <h2>Power</h2>
 *
 * <p>A generator rebuilt from salvage is put down inside a building, whose wiring it feeds; work
 * lamps put down in the same building light while it runs (see {@link UrbanUtilities} for the
 * circuit). The use button pours a fuel can into it or, with a pry tool in the pack, rebuilds its
 * worn parts with spare parts; F starts and stops it and switches a lamp. While it runs:
 * <ul>
 *   <li><b>Noise.</b> Every {@value #NOISE_INTERVAL} seconds it is a real {@code WorldNoise} event of
 *       {@value #RUN_NOISE_RADIUS} blocks, starting it louder still, losing through walls as any
 *       sound does: whatever hears it comes to look. A Creative builder's is heard by nobody.</li>
 *   <li><b>Cost.</b> It burns fuel and wears for every second it runs, and stops when the tank is
 *       dry or it seizes; its lamps go dark at once.</li>
 *   <li><b>Time.</b> It runs only while the world does: never while paused or in a pausing
 *       screen, by the world's own seconds through a sleep, and not at all while it is more than
 *       {@value #DORMANT_RANGE} blocks from the player or its column is not loaded, when it, its
 *       fuel and its lamps hold still. Nothing is caught up afterwards, and a load restores exactly
 *       what was saved.</li>
 * </ul>
 *
 * <h2>Treated water</h2>
 *
 * <p>A water purifier is put down on a building's wiring like a lamp, with its filter spent: the
 * use button with fibre packs it, its repair, which every litre treated wears again. With a dirty
 * waterskin the use button pours a litre into its untreated tank, with an empty one it fills the
 * skin from its clean tank, and F switches it. Switched on and fed, its pump draws its building's
 * stale tap water a litre at a time while the untreated tank has room, and it treats what is there
 * into clean water, both by the seconds its generator runs, so power, filter and fuel are what
 * clean water costs. Water only moves between the pipes, its two tanks and the player's skins;
 * taking it apart spills what it held.
 *
 * <p>Creative pours, rebuilds, packs and puts down without spending anything and needs no tool; the
 * world's fuel, wear and water run as in Survival.
 */
final class UtilitySystem {
    /** Seconds between a running generator's noise events. */
    static final float NOISE_INTERVAL = 3f;
    /** A running generator's noise. */
    static final float RUN_NOISE_RADIUS = 28f, RUN_NOISE = 0.5f;
    /** A generator pulled into life. */
    static final float START_NOISE_RADIUS = 34f, START_NOISE = 0.6f;
    /** Working on one with a pry tool. */
    static final float REPAIR_NOISE_RADIUS = 16f, REPAIR_NOISE = 0.35f;
    /** Blocks from the player beyond which a circuit holds still. */
    static final float DORMANT_RANGE = 128f;
    /** Condition one spare part puts back into a generator. */
    static final float PART_REPAIR = 50f;
    /** Rounding slack for litres moved a sliver at a time. */
    static final float LITRE_SLACK = 1e-3f;

    private final Game game;
    private final RefugeSystem refuges;

    UtilitySystem(Game game, RefugeSystem refuges) {
        this.game = game;
        this.refuges = refuges;
    }

    void reset() {
    }

    /** True in a world whose rules include refuge utilities: layout fifteen. */
    static boolean applies(World w) {
        return w != null && w.urban != null && w.urban.hasUtilities();
    }

    private UrbanUtilities ledger() {
        return game.world.urban.utilities;
    }

    // ------------------------------------------------------------------
    // Taps
    // ------------------------------------------------------------------

    /**
     * F at a cell that may be a tap: fill an empty waterskin with stale water, or drink from it.
     *
     * @return true when the cell is a tap, whether or not water ran
     */
    boolean tap(Vec3i pos) {
        BuildingPlan b = UrbanUtilities.tapBuilding(game.world, pos.x(), pos.y(), pos.z());
        if (b == null) { return false; }
        String refusal = tapRefusal(b, pos);
        if (refusal != null) {
            game.log(refusal + ".");
            return true;
        }
        UrbanUtilities u = ledger();
        if (!u.canRecord(b)) {
            game.log("The taps will not run: the world's water record is full.");
            return true;
        }
        if (game.player.inventory.count(ItemType.WATERSKIN_EMPTY) > 0) {
            if (!SurvivalTransfers.exchange(game, ItemType.WATERSKIN_EMPTY, ItemType.WATERSKIN_DIRTY)) { return true; }
            u.draw(b);
            game.audio.playDrink();
            game.log("Filled a waterskin with stale water from the pipes (" + u.left(b)
                    + " L left in the building). Boil it before you drink it.");
            return true;
        }
        u.draw(b);
        game.interactions.drinkUntreated("stale water from the tap");
        return true;
    }

    /** Why a tap gives nothing, or null when it runs. */
    private String tapRefusal(BuildingPlan b, Vec3i pos) {
        UrbanIndoorQueries.Location here = UrbanIndoorQueries.location(game.world, pos.x(), pos.y(), pos.z());
        if (here != null && refuges.heldBy(b, here.floor()) != null) {
            return "These taps are the survivors' who hold this building";
        }
        if (ledger().left(b) <= 0) {
            return "The tap is dry: the stale water left in this building's pipes is gone";
        }
        return null;
    }

    /** The HUD line for an aimed tap, or null when the cell is no tap. */
    String tapPrompt(Vec3i pos) {
        BuildingPlan b = UrbanUtilities.tapBuilding(game.world, pos.x(), pos.y(), pos.z());
        if (b == null) { return null; }
        String refusal = tapRefusal(b, pos);
        if (refusal != null) {
            return refusal.startsWith("The tap is dry") ? "Tap: dry, its building's pipes are empty"
                    : "Tap: the survivors' water";
        }
        int left = ledger().left(b);
        return game.player.inventory.count(ItemType.WATERSKIN_EMPTY) > 0
                ? "[F] Fill a waterskin: stale tap water, " + left + " L left"
                : "[F] Drink stale tap water (untreated, " + left + " L left)";
    }

    // ------------------------------------------------------------------
    // Putting devices down
    // ------------------------------------------------------------------

    /** Why a device may not be put down at a cell, or null when it may (or the block is none). */
    String placementRefusal(Vec3i pos, BlockType t) {
        return applies(game.world) ? ledger().placementRefusal(game.world, pos, t) : null;
    }

    /** Registers a device just put down and says how to get it going. */
    void placed(Vec3i pos, BlockType t) {
        if (!applies(game.world) || !UrbanUtilities.isDevice(t)) { return; }
        Device d = ledger().add(game.world, pos, t);
        if (d == null) { return; }
        game.log(switch (d.kind) {
            case GENERATOR -> "Generator put down. Pour in a fuel can, then press [F] to start it.";
            case LAMP -> d.powered() ? "Work lamp put down on the running generator's wiring."
                    : "Work lamp put down: it lights while a generator in this building runs.";
            case PURIFIER -> "Water purifier put down. Its filter is spent: pack it with "
                    + UrbanUtilities.FILTER_FIBRE + " fibre.";
        });
    }

    // ------------------------------------------------------------------
    // F: switches
    // ------------------------------------------------------------------

    /**
     * F on a device: start or stop a generator, switch a lamp.
     *
     * @return true when the cell is a device of a utility city, whatever came of it
     */
    boolean toggle(Vec3i pos) {
        if (!applies(game.world) || !UrbanUtilities.isDevice(game.world.getBlock(pos.x(), pos.y(), pos.z()))) {
            return false;
        }
        Device d = ledger().ensure(game.world, pos);
        if (d == null) {
            game.log("It is not wired to anything.");
            return true;
        }
        switch (d.kind) {
            case GENERATOR -> generatorSwitch(d);
            case LAMP -> {
                d.on = !d.on;
                ledger().settle(game.world);
                game.audio.playClick();
                game.log(!d.on ? "You switch the work lamp off."
                        : d.powered() ? "The work lamp comes on." : "You switch the work lamp on, but nothing feeds it.");
            }
            case PURIFIER -> {
                d.on = !d.on;
                ledger().settle(game.world);
                game.audio.playClick();
                game.log(!d.on ? "You switch the water purifier off."
                        : d.condition <= 0 ? "You switch the water purifier on, but its filter is spent."
                        : d.powered() ? "The water purifier hums into work."
                        : "You switch the water purifier on, but nothing feeds it.");
            }
        }
        return true;
    }

    private void generatorSwitch(Device g) {
        float x = g.pos.x() + .5f, y = g.pos.y() + .5f, z = g.pos.z() + .5f;
        if (g.on) {
            g.on = false;
            ledger().settle(game.world);
            game.audio.playUtility(UtilitySounds.Cue.STOP, x, y, z);
            game.log("You switch the generator off.");
            return;
        }
        if (g.condition <= 0) {
            game.log("The generator is seized: rebuild it with spare parts and a pry tool.");
            return;
        }
        if (g.fuel <= 0) {
            game.log("The generator's tank is dry: pour in a fuel can.");
            return;
        }
        g.on = true;
        g.noiseTimer = NOISE_INTERVAL;
        g.soundTimer = UtilitySounds.seconds(UtilitySounds.Cue.START);
        ledger().settle(game.world);
        game.audio.playUtility(UtilitySounds.Cue.START, x, y, z);
        game.particles.smoke(x, y + .7f, z, 1f);
        game.noise.emit(game, x, y, z, START_NOISE_RADIUS, START_NOISE, "generator", true, game.player);
        game.log("The generator coughs into life (" + cans(g) + " in the tank).");
    }

    /** The HUD line for an aimed device, or null for anything else. */
    String devicePrompt(Vec3i pos) {
        if (!applies(game.world)) { return null; }
        BlockType t = game.world.getBlock(pos.x(), pos.y(), pos.z());
        if (!UrbanUtilities.isDevice(t)) { return null; }
        Device d = ledger().device(pos);
        if (d == null) { return "[F] Wire it up"; }
        return switch (d.kind) {
            case GENERATOR -> d.on ? "[F] Switch off: running, " + cans(d) + " left"
                    : d.condition <= 0 ? "Generator seized: rebuild it with spare parts"
                    : d.fuel <= 0 ? "Generator: tank dry, pour in a fuel can"
                    : "[F] Start the generator (" + cans(d) + ", " + percent(d.condition) + ")";
            case LAMP -> d.on ? "[F] Switch the work lamp off" + (d.powered() ? "" : " (no power)")
                    : "[F] Switch the work lamp on" + (d.powered() ? "" : " (no power)");
            case PURIFIER -> d.condition <= 0 ? "Purifier: filter spent, pack it with " + UrbanUtilities.FILTER_FIBRE + " fibre"
                    : d.on ? "[F] Switch off: " + (d.treating() ? "treating" : d.powered() ? "pumping" : "no power")
                    + ", " + litres(d.untreated) + " in, " + litres(d.clean) + " clean"
                    : "[F] Switch on (" + litres(d.untreated) + " in, " + litres(d.clean) + " clean)";
        };
    }

    // ------------------------------------------------------------------
    // The use button: fuel and repairs
    // ------------------------------------------------------------------

    /**
     * The use button with an item held, aimed at a device: pour fuel into a generator or rebuild its
     * worn parts; pour untreated water into a purifier, fill a waterskin from it or pack its filter.
     *
     * @return true when the press was a utility action, done or refused with a reason
     */
    boolean rightClick(ItemStack held) {
        Device d = aimed(true);
        if (d == null || held == null) { return false; }
        if (d.kind == Kind.GENERATOR && held.type == ItemType.FUEL_CAN) {
            refuel(d);
            return true;
        }
        if (d.kind == Kind.GENERATOR && held.type == ItemType.SPARE_PARTS) {
            repair(d);
            return true;
        }
        if (d.kind == Kind.PURIFIER) {
            switch (held.type) {
                case WATERSKIN_DIRTY -> pour(d);
                case WATERSKIN_EMPTY -> fill(d);
                case FIBER -> packFilter(d);
                default -> { return false; }
            }
            return true;
        }
        return false;
    }

    /** What the use button would do with the held item at the aimed device, or null. */
    String rightClickPrompt(ItemStack held) {
        Device d = aimed(false);
        if (d == null || held == null) { return null; }
        if (d.kind == Kind.GENERATOR && held.type == ItemType.FUEL_CAN) {
            return d.fuel + UrbanUtilities.CAN_SECONDS > UrbanUtilities.TANK_SECONDS + 1e-3f
                    ? "The generator's tank is full (" + cans(d) + ")"
                    : "[RMB] Pour in the fuel can (" + cans(d) + " in the tank)";
        }
        if (d.kind == Kind.GENERATOR && held.type == ItemType.SPARE_PARTS) {
            if (d.condition >= UrbanUtilities.SOUND) { return null; }
            return !game.player.abilities.unlimitedItems() && !hasPryTool()
                    ? "Rebuilding it needs a pry tool in your pack"
                    : "[RMB] Rebuild its worn parts (" + percent(d.condition) + ")";
        }
        if (d.kind == Kind.PURIFIER) {
            return switch (held.type) {
                case WATERSKIN_DIRTY -> d.untreated > UrbanUtilities.PURIFIER_LITRES - 1f + LITRE_SLACK
                        ? "The purifier's untreated tank is full"
                        : "[RMB] Pour in untreated water (" + litres(d.untreated) + " of "
                        + litres(UrbanUtilities.PURIFIER_LITRES) + ")";
                case WATERSKIN_EMPTY -> d.clean + LITRE_SLACK >= 1f
                        ? "[RMB] Fill a waterskin with clean water (" + litres(d.clean) + ")" : null;
                case FIBER -> d.condition >= UrbanUtilities.SOUND ? null
                        : !game.player.abilities.unlimitedItems()
                        && game.player.inventory.count(ItemType.FIBER) < UrbanUtilities.FILTER_FIBRE
                        ? "Packing the filter takes " + UrbanUtilities.FILTER_FIBRE + " fibre"
                        : "[RMB] Pack the filter with " + UrbanUtilities.FILTER_FIBRE + " fibre (filter "
                        + Math.round(d.condition) + "%)";
                default -> null;
            };
        }
        return null;
    }

    /**
     * The device the player aims at in a utility city, or null; an action registers a device block
     * that has no record yet, a prompt only reads.
     */
    private Device aimed(boolean register) {
        if (!applies(game.world) || game.targetHit == null) { return null; }
        Vec3i pos = new Vec3i(game.targetHit.x(), game.targetHit.y(), game.targetHit.z());
        return UrbanUtilities.isDevice(game.world.getBlock(pos.x(), pos.y(), pos.z()))
                ? register ? ledger().ensure(game.world, pos) : ledger().device(pos) : null;
    }

    private void refuel(Device g) {
        if (g.fuel + UrbanUtilities.CAN_SECONDS > UrbanUtilities.TANK_SECONDS + 1e-3f) {
            game.log("The generator's tank is full.");
            return;
        }
        g.fuel = Math.min(UrbanUtilities.TANK_SECONDS, g.fuel + UrbanUtilities.CAN_SECONDS);
        spend();
        game.audio.playDrink();
        game.log("You pour a fuel can into the generator (" + cans(g) + " in the tank).");
    }

    private void repair(Device g) {
        if (g.condition >= UrbanUtilities.SOUND) {
            game.log("The generator runs sound.");
            return;
        }
        boolean free = game.player.abilities.unlimitedItems();
        if (!free && !hasPryTool()) {
            game.log("You need a pry tool in your pack to strip and rebuild it.");
            return;
        }
        boolean seized = g.condition <= 0;
        g.condition = Math.min(UrbanUtilities.SOUND, g.condition + PART_REPAIR);
        spend();
        float x = g.pos.x() + .5f, y = g.pos.y() + .5f, z = g.pos.z() + .5f;
        game.audio.playBlockHit(BlockType.GENERATOR, x, y, z);
        game.noise.emit(game, x, y, z, REPAIR_NOISE_RADIUS, REPAIR_NOISE, "repair", true, game.player);
        game.log((seized ? "You free the seized generator" : "You rebuild the generator's worn parts")
                + " (" + percent(g.condition) + ").");
    }

    /** A dirty waterskin emptied into a purifier's untreated tank. */
    private void pour(Device p) {
        if (p.untreated > UrbanUtilities.PURIFIER_LITRES - 1f + LITRE_SLACK) {
            game.log("The purifier's untreated tank is full.");
            return;
        }
        if (!SurvivalTransfers.exchange(game, ItemType.WATERSKIN_DIRTY, ItemType.WATERSKIN_EMPTY)) { return; }
        p.untreated = Math.min(UrbanUtilities.PURIFIER_LITRES, p.untreated + 1f);
        ledger().settle(game.world);
        game.audio.playDrink();
        game.log("You pour the untreated water into the purifier (" + litres(p.untreated) + " to treat).");
    }

    /** An empty waterskin filled from a purifier's clean tank. */
    private void fill(Device p) {
        if (p.clean + LITRE_SLACK < 1f) {
            game.log("Nothing treated yet: the purifier has " + litres(p.clean) + " of clean water.");
            return;
        }
        if (!SurvivalTransfers.exchange(game, ItemType.WATERSKIN_EMPTY, ItemType.WATERSKIN_CLEAN)) { return; }
        p.clean = Math.max(0f, p.clean - 1f);
        ledger().settle(game.world);
        game.audio.playDrink();
        game.log("You fill a waterskin with clean water from the purifier (" + litres(p.clean) + " left).");
    }

    /** A spent or worn filter packed with fresh fibre: the purifier's repair. */
    private void packFilter(Device p) {
        if (p.condition >= UrbanUtilities.SOUND) {
            game.log("The purifier's filter is fresh.");
            return;
        }
        boolean free = game.player.abilities.unlimitedItems();
        if (!free && game.player.inventory.count(ItemType.FIBER) < UrbanUtilities.FILTER_FIBRE) {
            game.log("Packing the filter takes " + UrbanUtilities.FILTER_FIBRE + " fibre.");
            return;
        }
        if (!free) { game.player.inventory.remove(ItemType.FIBER, UrbanUtilities.FILTER_FIBRE); }
        p.condition = UrbanUtilities.SOUND;
        ledger().settle(game.world);
        game.audio.playClick();
        game.log("You pack the purifier's filter with fresh fibre.");
    }

    /** "3.0 L". */
    static String litres(float l) {
        return String.format(Locale.ROOT, "%.1f L", l);
    }

    /**
     * What taking a purifier apart spills, for the log, or null: its tanks' water runs away with it.
     * Read before the piece goes.
     */
    String spillNote(Vec3i pos) {
        if (!applies(game.world)) { return null; }
        Device d = ledger().device(pos);
        float water = d == null || d.kind != Kind.PURIFIER ? 0f : d.untreated + d.clean;
        return water > LITRE_SLACK ? "Its " + litres(water) + " of water run away." : null;
    }

    private boolean hasPryTool() {
        var inv = game.player.inventory;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s != null && s.type.tool == ToolKind.PICKAXE) { return true; }
        }
        return false;
    }

    /** One of the held item, unless Creative's unlimited items keep it. */
    private void spend() {
        if (!game.player.abilities.unlimitedItems()) { game.player.inventory.shrink(game.player.hotbarSel, 1); }
    }

    static String cans(Device g) {
        float cans = g.fuel / UrbanUtilities.CAN_SECONDS;
        return String.format(Locale.ROOT, "%.1f %s", cans, Math.abs(cans - 1f) < .05f ? "can" : "cans");
    }

    static String percent(float condition) {
        return "condition " + Math.round(condition) + "%";
    }

    // ------------------------------------------------------------------
    // The medium tick
    // ------------------------------------------------------------------

    /**
     * Runs every generator that is on and awake for one medium tick: its fuel and wear, its noise
     * and sound, and stopping it when the tank is dry or it seizes.
     */
    void mediumTick(float dt) {
        if (!applies(game.world)) { return; }
        UrbanUtilities u = ledger();
        List<Device> all = u.devices();
        boolean changed = false;
        for (int i = 0; i < all.size(); i++) {
            Device g = all.get(i);
            if (!g.running() || dormant(g)) { continue; }
            float rate = 1f + UrbanUtilities.LOAD_BURN * u.load(g);
            float run = Math.min(dt, g.fuel / rate);
            g.fuel -= rate * run;
            if (g.fuel < 1e-3f) { g.fuel = 0; }
            g.condition = Math.max(0f, g.condition - run / UrbanUtilities.WEAR_SECONDS);
            if (feedPurifiers(u, g, run)) { changed = true; }
            float x = g.pos.x() + .5f, y = g.pos.y() + .5f, z = g.pos.z() + .5f;
            if ((g.noiseTimer -= dt) <= 0) {
                g.noiseTimer += NOISE_INTERVAL;
                game.noise.emit(game, x, y, z, RUN_NOISE_RADIUS, RUN_NOISE, "generator", true, game.player);
                game.particles.smoke(x, y + .7f, z, .4f);
            }
            if ((g.soundTimer -= dt) <= 0) {
                g.soundTimer += UtilitySounds.seconds(UtilitySounds.Cue.RUN);
                game.audio.playUtility(UtilitySounds.Cue.RUN, x, y, z);
            }
            if (g.fuel <= 0) {
                g.on = false;
                changed = true;
                game.audio.playUtility(UtilitySounds.Cue.STOP, x, y, z);
                game.log("The generator sputters and dies: its tank is dry.");
            } else if (g.condition <= 0) {
                g.on = false;
                changed = true;
                game.audio.playUtility(UtilitySounds.Cue.STOP, x, y, z);
                game.log("The generator seizes: it needs rebuilding with spare parts.");
            }
        }
        if (changed) { u.settle(game.world); }
    }

    /**
     * The purifiers a running generator feeds work for exactly the seconds it ran this tick: each
     * pumps its building's stale water into its untreated tank while there is room, and treats what
     * is there into its clean tank, wearing its filter for every litre. Water only moves: nothing is
     * made or lost on the way.
     *
     * @return true when a filter clogged, so the circuits settle again
     */
    private boolean feedPurifiers(UrbanUtilities u, Device g, float run) {
        boolean clogged = false;
        List<Device> all = u.devices();
        for (int i = 0; i < all.size(); i++) {
            Device p = all.get(i);
            if (p.kind != Kind.PURIFIER || p.feeder() != g || !p.working()) { continue; }
            if (p.untreated <= UrbanUtilities.PURIFIER_LITRES - 1f + LITRE_SLACK
                    && (p.pumped += run) >= UrbanUtilities.PUMP_SECONDS) {
                p.pumped -= UrbanUtilities.PUMP_SECONDS;
                pump(u, p);
            }
            if (!p.treating()) { continue; }
            float litres = Math.min(run / UrbanUtilities.TREAT_SECONDS,
                    Math.min(p.untreated, UrbanUtilities.PURIFIER_LITRES - p.clean));
            litres = Math.min(litres, p.condition / UrbanUtilities.FILTER_WEAR);
            p.untreated -= litres;
            p.clean += litres;
            p.condition = Math.max(0f, p.condition - litres * UrbanUtilities.FILTER_WEAR);
            if (p.condition <= LITRE_SLACK) {
                p.condition = 0;
                clogged = true;
                game.log("The water purifier's filter clogs: pack it with fresh fibre.");
            }
        }
        return clogged;
    }

    /**
     * One litre of a purifier's building's stale water drawn up into its untreated tank, as a tap
     * would give it; dry pipes, or pipes on a storey survivors hold, give nothing and the pump runs
     * dry.
     */
    private void pump(UrbanUtilities u, Device p) {
        BuildingPlan b = game.world.urban.buildingById(p.building);
        if (b == null || u.left(b) <= 0 || !u.canRecord(b)) { return; }
        UrbanIndoorQueries.Location here = UrbanIndoorQueries.location(game.world, p.pos.x(), p.pos.y(), p.pos.z());
        if (here != null && refuges.heldBy(b, here.floor()) != null) { return; }
        if (u.draw(b)) { p.untreated = Math.min(UrbanUtilities.PURIFIER_LITRES, p.untreated + 1f); }
    }

    /** A circuit holds still while its generator is far from the player or its column is not loaded. */
    boolean dormant(Device g) {
        if (!game.world.isColumnLoaded(g.pos.x(), g.pos.z())) { return true; }
        float dx = g.pos.x() + .5f - game.player.pos.x, dz = g.pos.z() + .5f - game.player.pos.z;
        return dx * dx + dz * dz > DORMANT_RANGE * DORMANT_RANGE;
    }
}
