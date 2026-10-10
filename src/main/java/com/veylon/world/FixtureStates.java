package com.veylon.world;

import com.veylon.util.Vec3i;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Door and window state that no block id expresses, keyed by block position
 * (decision D-48, urban package 04).
 *
 * <p>Toggling a door or window swaps its block id with {@code record = false}
 * and writes the new state here instead of into {@code World.changedBlocks}.
 * The map is saved in the {@code world.fixtures} section and is the authority
 * for every opening it names: when a chunk is generated, and when a save is
 * loaded, an entry turns the generated or edited block into the variant it
 * describes. An absent entry means "as generated or edited, unlocked".
 * Irreversible changes (a forced door, a shattered pane, a dismantled
 * fixture) are ordinary recorded block edits, and {@code World.setBlock}
 * drops the entry of a cell that stops being an operable fixture.
 *
 * <p>Positions, not plan fixture ids, are the key: geometry is frozen per
 * generator version, so a position names the same fixture in every session,
 * and player-placed doors have no plan id at all.
 *
 * <p>State bits: {@link #OPEN}; a lock field ({@link #LOCK_NONE},
 * {@link #LOCK_KEYED} for a lock that holds from both sides,
 * {@link #LOCK_BARRED} for a bar that can be lifted only from the side it was
 * set from); for a bar, {@link #BAR_AXIS_Z} names the axis the opening faces
 * along and {@link #BAR_POSITIVE} the side of that axis it was set from. An
 * open fixture is never locked.
 *
 * <p>Residents' door openings are transient: {@link #transitTimers()} closes
 * them again and the save writes them closed.
 */
public final class FixtureStates {

    /** Most fixture cells with saved state; the reader accepts no more. */
    public static final int MAX_ENTRIES = 32_768;
    /** Most doors a resident has opened that are waiting to swing shut. */
    public static final int MAX_TRANSIT = 256;

    public static final int OPEN = 1;
    public static final int LOCK_SHIFT = 1;
    public static final int LOCK_MASK = 0b11 << LOCK_SHIFT;
    public static final int LOCK_NONE = 0;
    public static final int LOCK_KEYED = 1 << LOCK_SHIFT;
    public static final int LOCK_BARRED = 2 << LOCK_SHIFT;
    public static final int BAR_POSITIVE = 1 << 3;
    public static final int BAR_AXIS_Z = 1 << 4;
    private static final int VALID_BITS = OPEN | LOCK_MASK | BAR_POSITIVE | BAR_AXIS_Z;

    private final Map<Long, Map<Vec3i, Byte>> byChunk = new HashMap<>();
    private final Map<Vec3i, Float> transit = new HashMap<>();
    private int size;

    /** True for a state byte a save may hold. */
    public static boolean isValid(int state) {
        if ((state & ~VALID_BITS) != 0) {
            return false;
        }
        int lock = state & LOCK_MASK;
        if (lock == LOCK_MASK) {
            return false; // reserved
        }
        if ((state & OPEN) != 0 && lock != LOCK_NONE) {
            return false;
        }
        return lock == LOCK_BARRED || (state & (BAR_POSITIVE | BAR_AXIS_Z)) == 0;
    }

    public static boolean isOpen(int state) {
        return (state & OPEN) != 0;
    }

    public static int lock(int state) {
        return state & LOCK_MASK;
    }

    private static long chunkKey(int x, int z) {
        return World.key(Math.floorDiv(x, Chunk.SX), Math.floorDiv(z, Chunk.SZ));
    }

    /** The saved state of a cell, or -1 when it has none. */
    public int stateAt(int x, int y, int z) {
        Map<Vec3i, Byte> bucket = byChunk.get(chunkKey(x, z));
        if (bucket == null) {
            return -1;
        }
        Byte b = bucket.get(new Vec3i(x, y, z));
        return b == null ? -1 : b;
    }

    /** The lock field of a cell; {@link #LOCK_NONE} when it has no state. */
    public int lockAt(int x, int y, int z) {
        int s = stateAt(x, y, z);
        return s < 0 ? LOCK_NONE : lock(s);
    }

    /**
     * Stores a cell's state.
     *
     * @return false, changing nothing, for an invalid state or for a new cell
     *         once {@link #MAX_ENTRIES} cells already hold state
     */
    public boolean set(int x, int y, int z, int state) {
        if (!isValid(state)) {
            return false;
        }
        Map<Vec3i, Byte> bucket = byChunk.get(chunkKey(x, z));
        Vec3i pos = new Vec3i(x, y, z);
        if (bucket == null || !bucket.containsKey(pos)) {
            if (size >= MAX_ENTRIES) {
                return false;
            }
            if (bucket == null) {
                bucket = new HashMap<>();
                byChunk.put(chunkKey(x, z), bucket);
            }
            size++;
        }
        bucket.put(pos, (byte) state);
        return true;
    }

    /** True when a cell already has state or another cell could still get some. */
    public boolean canHold(int x, int y, int z) {
        return size < MAX_ENTRIES || stateAt(x, y, z) >= 0;
    }

    public void remove(int x, int y, int z) {
        long key = chunkKey(x, z);
        Map<Vec3i, Byte> bucket = byChunk.get(key);
        Vec3i pos = new Vec3i(x, y, z);
        if (bucket != null && bucket.remove(pos) != null) {
            size--;
            if (bucket.isEmpty()) {
                byChunk.remove(key);
            }
        }
        transit.remove(pos);
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** Visits every cell with state, in no particular order. */
    public void forEach(BiConsumer<Vec3i, Integer> visitor) {
        for (Map<Vec3i, Byte> bucket : byChunk.values()) {
            for (Map.Entry<Vec3i, Byte> e : bucket.entrySet()) {
                visitor.accept(e.getKey(), (int) e.getValue());
            }
        }
    }

    /**
     * Applies the stored state to a freshly generated chunk, before its heights
     * and lights are computed. An entry whose cell no longer holds an operable
     * fixture is stale and dropped.
     */
    void applyToNewChunk(Chunk c) {
        Map<Vec3i, Byte> bucket = byChunk.get(World.key(c.cx, c.cz));
        if (bucket == null) {
            return;
        }
        Iterator<Map.Entry<Vec3i, Byte>> it = bucket.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Vec3i, Byte> e = it.next();
            Vec3i p = e.getKey();
            int lx = Math.floorMod(p.x(), Chunk.SX), lz = Math.floorMod(p.z(), Chunk.SZ);
            Fixtures.Kind kind = Fixtures.kindOf(c.get(lx, p.y(), lz));
            if (kind == null) {
                it.remove();
                size--;
                continue;
            }
            c.set(lx, p.y(), lz, kind.variant(isOpen(e.getValue())));
        }
        if (bucket.isEmpty()) {
            byChunk.remove(World.key(c.cx, c.cz));
        }
    }

    /**
     * Doors residents have opened, keyed by the bottom cell of the opening,
     * with the seconds until they swing shut. Transient: never saved, and the
     * save writes these openings closed.
     */
    public Map<Vec3i, Float> transitTimers() {
        return transit;
    }

    /**
     * The state a save records for a cell: an opening a resident holds open
     * only to pass through is written closed, its resting state.
     */
    public int savedState(World w, Vec3i pos, int state) {
        Fixtures.Kind kind = Fixtures.kindOf(w.getBlock(pos.x(), pos.y(), pos.z()));
        if (kind != null && isOpen(state)) {
            int bottom = Fixtures.stackBottom(w, pos.x(), pos.y(), pos.z(), kind);
            if (transit.containsKey(new Vec3i(pos.x(), bottom, pos.z()))) {
                return state & ~OPEN;
            }
        }
        return state;
    }
}
