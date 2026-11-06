package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The refuge utilities of a layout-fifteen city (urban milestone 19), as the world keeps them.
 *
 * <h2>Taps</h2>
 *
 * <p>The mains died with the city. What is left in a building's pipes and roof tank is a little
 * stale water, untreated, shared by every tap of the building: the basins its plan puts in
 * kitchens, bathrooms and clinics, standing where the plan put them. A basin the player sets down
 * is a rain collector, as it always was. The building's utility id is its stable building id.
 * <ul>
 *   <li><b>How much.</b> {@value #LITRES_PER_BASIN} litres for each planned basin of an intact or
 *       a held building; one for each in a picked-clean building, where others drew most of it
 *       first; one for every two in a damaged building, whose burst pipes drained the rest. A
 *       building without a planned basin has no taps.</li>
 *   <li><b>No refill.</b> Nothing ever puts water back: no rain, no time, no load and no
 *       regeneration. Each litre drawn is recorded against the building, and a building whose taps
 *       were never opened has no row.</li>
 *   <li><b>Bounded.</b> At most {@value #MAX_TAPPED} buildings' rows; once the ledger is full a
 *       new building's taps refuse to run, so nothing drawn is ever forgotten.</li>
 * </ul>
 *
 * <h2>Devices and circuits</h2>
 *
 * <p>Generators, work lamps and water purifiers are put down by the player from salvage, inside a
 * building: a  * device's building is its circuit, the building's wiring. A circuit has at most one generator,
 * and a running generator feeds the {@value #MAX_LOAD} consumers of its building nearest to it
 * within {@value #REACH} blocks; anything farther, outside or beyond that load is dark. There is no
 * wire, cable or network to lay or solve: the building is the service area.
 * <ul>
 *   <li><b>Fuel.</b> A tank holds {@value #TANK_CANS} cans of {@value #CAN_SECONDS} seconds at
 *       idle; every consumer drawing adds a quarter to the burn. Out of fuel it stops.</li>
 *   <li><b>Wear.</b> Running wears it a point every {@value #WEAR_SECONDS} seconds from
 *       {@value #SOUND}; at none it seizes and stops until rebuilt with spare parts.</li>
 *   <li><b>Light.</b> A work lamp gives light only while it is switched on and fed; its light is
 *       registered in its chunk exactly then, and every change of feed refreshes the lamp's light
 *       and the meshes its glow reaches.</li>
 *   <li><b>Water.</b> A purifier is built with its filter spent; packed with fibre, switched on
 *       and fed, it treats a litre of untreated water every {@value #TREAT_SECONDS} seconds from
 *       its untreated tank into its clean one, each {@value #PURIFIER_LITRES} litres, and its pump
 *       draws a litre of its building's stale tap water every {@value #PUMP_SECONDS} seconds while the
 *       untreated tank has room. Every litre treated wears {@value #FILTER_WEAR} points off the filter.
 *       Nothing is made: what goes in is what comes out.</li>
 *   <li><b>Bounded.</b> At most {@value #MAX_DEVICES} devices in the world.</li>
 * </ul>
 * Persistent through {@code urban.utilities} version 1; a new or loaded world gets a fresh ledger
 * with its world. Main thread only.
 */
public final class UrbanUtilities {
    /** Litres of stale water each planned basin of an intact building still holds. */
    public static final int LITRES_PER_BASIN = 2;
    /** Buildings whose taps have been drawn from, kept at once. */
    public static final int MAX_TAPPED = 1_024;

    /** Generators, lamps and purifiers kept at once in the whole world. */
    public static final int MAX_DEVICES = 64;
    /** Consumers one generator feeds. */
    public static final int MAX_LOAD = 4;
    /** Blocks a generator's leads reach through its building's wiring. */
    public static final float REACH = 16f;
    /** Seconds one fuel can runs a generator at idle. */
    public static final float CAN_SECONDS = 300f;
    /** Fuel cans a generator's tank holds. */
    public static final int TANK_CANS = 3;
    /** Seconds of fuel a full tank holds at idle. */
    public static final float TANK_SECONDS = CAN_SECONDS * TANK_CANS;
    /** A device in perfect order: full condition. */
    public static final float SOUND = 100f;
    /** Seconds of running that wear a generator by one point of condition. */
    public static final float WEAR_SECONDS = 15f;
    /** Extra burn, as a share of idle, for each consumer drawing from a generator. */
    public static final float LOAD_BURN = 0.25f;
    /** Litres a purifier's untreated tank and its clean tank each hold. */
    public static final float PURIFIER_LITRES = 8f;
    /** Seconds of power a purifier takes to treat one litre. */
    public static final float TREAT_SECONDS = 20f;
    /** Seconds of power its pump takes to draw one litre of its building's stale water. */
    public static final float PUMP_SECONDS = 10f;
    /** Filter condition one litre treated wears away: a fresh filter treats twenty-five litres. */
    public static final float FILTER_WEAR = 4f;
    /** Fibre packed into a spent filter to renew it. */
    public static final int FILTER_FIBRE = 2;

    /** What a device is. The code is the save's; never reuse one. */
    public enum Kind {
        GENERATOR(1, BlockType.GENERATOR, "generator"),
        LAMP(2, BlockType.ELECTRIC_LAMP, "work lamp"),
        PURIFIER(3, BlockType.WATER_PURIFIER, "water purifier"),
        /** Layout sixteen (milestone 20): a radio workstation, a load while it is switched on. */
        RADIO(4, BlockType.RADIO, "radio workstation");

        public final int code;
        public final BlockType block;
        public final String noun;

        Kind(int code, BlockType block, String noun) {
            this.code = code;
            this.block = block;
            this.noun = noun;
        }

        /** The kind a block is, or null. */
        public static Kind of(BlockType t) {
            for (Kind k : values()) {
                if (k.block == t) { return k; }
            }
            return null;
        }

        /** The kind a save names, or null. */
        public static Kind byCode(int code) {
            for (Kind k : values()) {
                if (k.code == code) { return k; }
            }
            return null;
        }
    }

    /** One device put down in the world. */
    public static final class Device {
        public final Kind kind;
        public final Vec3i pos;
        /** The building whose wiring it is on. */
        public final long building;
        /** Its switch: a generator running, a lamp or a purifier switched on. */
        public boolean on;
        /** A generator's wear or a purifier's filter, from {@link #SOUND} down to none. */
        public float condition = SOUND;
        /** A generator's fuel, in seconds at idle. */
        public float fuel;
        /** A purifier's untreated and clean water, in litres. */
        public float untreated, clean;
        /** Transient: the running generator feeding it this moment, or null. */
        Device feeder;
        /** Transient: whether its chunk's light list shows it lit. */
        boolean shownLit;
        /** Transient: seconds to a running generator's next noise and sound. */
        public float noiseTimer, soundTimer;
        /** Transient: seconds of power a purifier's pump has run toward its next litre. */
        public float pumped;

        public Device(Kind kind, Vec3i pos, long building) {
            this.kind = kind;
            this.pos = pos;
            this.building = building;
        }

        /** A generator that is switched on and can run. */
        public boolean running() {
            return kind == Kind.GENERATOR && on && fuel > 0 && condition > 0;
        }

        /** A consumer that a running generator feeds. */
        public boolean powered() {
            return feeder != null;
        }

        /** The generator feeding it, or null. */
        public Device feeder() {
            return feeder;
        }

        /** A work lamp giving light. */
        public boolean lit() {
            return kind == Kind.LAMP && on && feeder != null;
        }

        /** A radio workstation switched on and fed. */
        public boolean transmitting() {
            return kind == Kind.RADIO && on && feeder != null;
        }

        /** A purifier switched on, fed and with a filter that still works. */
        public boolean working() {
            return kind == Kind.PURIFIER && on && feeder != null && condition > 0;
        }

        /** A working purifier with untreated water to treat and room for it. */
        public boolean treating() {
            return working() && untreated > 0 && clean < PURIFIER_LITRES;
        }

        /**
         * True while this consumer draws on its feeder: a lamp lit, a purifier working with water
         * to treat or room to pump into.
         */
        public boolean drawing() {
            return lit() || transmitting()
                    || working() && (untreated > 0 && clean < PURIFIER_LITRES || untreated <= PURIFIER_LITRES - 1f);
        }
    }

    /** Litres drawn from each building's taps, by building id. */
    private final Map<Long, Integer> drawn = new HashMap<>();
    private final Map<Vec3i, Device> devices = new HashMap<>();
    /** The devices in the order they were put down, for stable circuits and iteration. */
    private final List<Device> order = new ArrayList<>();
    private boolean sectionRestored;

    // ------------------------------------------------------------------
    // Taps
    // ------------------------------------------------------------------

    /**
     * The building whose taps a cell is one of, or null: a planned basin of a layout-fifteen
     * building, still standing where its plan put it with no recorded edit. Reads plans and the
     * loaded block only.
     */
    public static BuildingPlan tapBuilding(World w, int x, int y, int z) {
        if (w == null || w.urban == null || !w.urban.hasUtilities()
                || w.getBlock(x, y, z) != BlockType.RAIN_COLLECTOR) {
            return null;
        }
        Vec3i p = new Vec3i(x, y, z);
        if (w.changedBlocks.containsKey(p)) { return null; }
        BuildingPlan b = w.urban.buildingAt(x, z);
        if (b == null) { return null; }
        for (BuildingPlan.Fixture f : b.fixtures()) {
            if (isBasin(f) && f.position().equals(p)) { return b; }
        }
        return null;
    }

    private static boolean isBasin(BuildingPlan.Fixture f) {
        return f.kind() == BuildingPlan.FixtureKind.FURNITURE && f.block() == BlockType.RAIN_COLLECTOR;
    }

    /** The stale water a building's pipes held when the city fell, in litres. */
    public static int capacity(BuildingPlan b) {
        int basins = 0;
        for (BuildingPlan.Fixture f : b.fixtures()) {
            if (isBasin(f)) { basins++; }
        }
        return switch (b.variant()) {
            case INTACT, OCCUPIED -> basins * LITRES_PER_BASIN;
            case PICKED_CLEAN -> basins;
            case DAMAGED -> basins / 2;
        };
    }

    /** Litres already drawn from a building's taps. */
    public int drawn(long building) {
        return drawn.getOrDefault(building, 0);
    }

    /** Litres a building's taps can still give. */
    public int left(BuildingPlan b) {
        return Math.max(0, capacity(b) - drawn(b.id()));
    }

    /** True when the ledger can record one more litre from this building. */
    public boolean canRecord(BuildingPlan b) {
        return drawn.containsKey(b.id()) || drawn.size() < MAX_TAPPED;
    }

    /**
     * Draws one litre from a building's taps.
     *
     * @return false, changing nothing, when they are dry or the ledger cannot record it
     */
    public boolean draw(BuildingPlan b) {
        if (left(b) <= 0 || !canRecord(b)) { return false; }
        drawn.merge(b.id(), 1, Integer::sum);
        return true;
    }

    /** Every building's litres drawn, for the save. */
    public Map<Long, Integer> drawnRows() {
        return Collections.unmodifiableMap(drawn);
    }

    public int tappedCount() {
        return drawn.size();
    }

    // ------------------------------------------------------------------
    // Devices
    // ------------------------------------------------------------------

    /** True for a block that is a device. */
    public static boolean isDevice(BlockType t) {
        return Kind.of(t) != null;
    }

    /**
     * The building a cell is wired into, or {@link UrbanIds#NONE}: the planned building over its
     * column, from its lowest storey to the top of its roof. Plans only, so it holds for a cell
     * whose chunk is not generated.
     */
    public static long buildingOf(World w, Vec3i p) {
        if (w.urban == null) { return UrbanIds.NONE; }
        BuildingPlan b = w.urban.buildingAt(p.x(), p.z());
        if (b == null || b.floors().isEmpty()) { return UrbanIds.NONE; }
        int bottom = Integer.MAX_VALUE, top = Integer.MIN_VALUE;
        for (BuildingPlan.Floor f : b.floors()) {
            bottom = Math.min(bottom, f.slabY());
            top = Math.max(top, f.slabY() + UrbanDatum.STOREY_PITCH);
        }
        return p.y() > bottom && p.y() < top ? b.id() : UrbanIds.NONE;
    }

    /** The device at a cell, or null. */
    public Device device(Vec3i p) {
        return devices.get(p);
    }

    /** Every device, in the order they were put down. */
    public List<Device> devices() {
        return Collections.unmodifiableList(order);
    }

    public int deviceCount() {
        return order.size();
    }

    /** The generator wired into a building, or null. */
    public Device generatorOf(long building) {
        for (Device d : order) {
            if (d.kind == Kind.GENERATOR && d.building == building) { return d; }
        }
        return null;
    }

    /** Why a device may not be put down at a cell, or null when it may. Changes nothing. */
    public String placementRefusal(World w, Vec3i p, BlockType t) {
        Kind kind = Kind.of(t);
        if (kind == null) { return null; }
        long building = buildingOf(w, p);
        if (building == UrbanIds.NONE) {
            return "A " + kind.noun + " needs a building's wiring: put it down inside";
        }
        if (order.size() >= MAX_DEVICES) {
            return "Too many devices are wired up already (" + MAX_DEVICES + ")";
        }
        if (kind == Kind.GENERATOR && generatorOf(building) != null) {
            return "This building's wiring already has a generator";
        }
        if (kind == Kind.RADIO && (w.urban == null || !w.urban.hasMissions())) {
            return "Nothing on the air in this city answers a radio";
        }
        return null;
    }

    /**
     * Registers a device just put down, switched off: a sound generator with an empty tank, a lamp,
     * or a purifier with empty tanks and its filter spent, to be packed with fibre before it works.
     *
     * @return the device, or null when its placement is refused
     */
    public Device add(World w, Vec3i p, BlockType t) {
        if (placementRefusal(w, p, t) != null || devices.containsKey(p)) { return null; }
        Device d = new Device(Kind.of(t), p, buildingOf(w, p));
        if (d.kind == Kind.PURIFIER) { d.condition = 0f; }
        devices.put(p, d);
        order.add(d);
        settle(w);
        return d;
    }

    /**
     * The device at a cell, registering one for a device block that has none (set down by some
     * other path), or null when the cell holds no device or the ledger refuses it.
     */
    public Device ensure(World w, Vec3i p) {
        Device d = devices.get(p);
        if (d != null) { return d; }
        BlockType t = w.getBlock(p.x(), p.y(), p.z());
        return isDevice(t) ? add(w, p, t) : null;
    }

    /** The world's hook when a device's block is replaced by anything: the device is gone. */
    public void removed(World w, Vec3i p) {
        Device d = devices.remove(p);
        if (d == null) { return; }
        order.remove(d);
        settle(w);
    }

    /**
     * Recomputes every circuit after a change of switch, fuel, wear or devices, and refreshes the
     * light of each lamp whose light changed. Bounded by the device cap; generates no chunk.
     */
    public void settle(World w) {
        for (Device d : order) { d.feeder = null; }
        for (Device g : order) {
            if (!g.running()) { continue; }
            for (int fed = 0; fed < MAX_LOAD; fed++) {
                Device best = null;
                double bestD = REACH * REACH;
                for (Device c : order) {
                    if (c.kind == Kind.GENERATOR || c.feeder != null || c.building != g.building) { continue; }
                    double d = c.pos.distSq(g.pos.x() + .5f, g.pos.y() + .5f, g.pos.z() + .5f);
                    if (d <= bestD && (best == null || d < bestD || before(c.pos, best.pos))) {
                        best = c;
                        bestD = d;
                    }
                }
                if (best == null) { break; }
                best.feeder = g;
            }
        }
        for (Device d : order) {
            if (d.kind == Kind.LAMP && d.lit() != d.shownLit) {
                d.shownLit = d.lit();
                w.refreshLightsAt(d.pos);
            }
        }
    }

    private static boolean before(Vec3i a, Vec3i b) {
        if (a.x() != b.x()) { return a.x() < b.x(); }
        if (a.y() != b.y()) { return a.y() < b.y(); }
        return a.z() < b.z();
    }

    /** Consumers a generator feeds that draw on it this moment. */
    public int load(Device generator) {
        int load = 0;
        for (Device c : order) {
            if (c.feeder == generator && c.drawing()) { load++; }
        }
        return load;
    }

    /** Whether the work lamp at a cell gives light. */
    public boolean lampLit(int x, int y, int z) {
        if (order.isEmpty()) { return false; }
        Device d = devices.get(new Vec3i(x, y, z));
        return d != null && d.lit();
    }

    // ------------------------------------------------------------------
    // Saving
    // ------------------------------------------------------------------

    public boolean sectionRestored() {
        return sectionRestored;
    }

    /** Load only: the validated rows of {@code urban.utilities}. */
    public void restore(World w, Map<Long, Integer> rows, List<Device> saved) {
        if (rows.size() > MAX_TAPPED) { throw new IllegalArgumentException("too many tapped buildings"); }
        if (saved.size() > MAX_DEVICES) { throw new IllegalArgumentException("too many devices"); }
        drawn.clear();
        drawn.putAll(rows);
        devices.clear();
        order.clear();
        for (Device d : saved) {
            devices.put(d.pos, d);
            order.add(d);
        }
        sectionRestored = true;
        settle(w);
    }

    /** True for a cell inside the chunk's vertical range. */
    public static boolean inRange(Vec3i p) {
        return p.y() >= 0 && p.y() < Chunk.SY;
    }
}
