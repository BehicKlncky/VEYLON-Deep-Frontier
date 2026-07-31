package com.veylon.world;

/**
 * Direct-mapped memo for a pure {@code (x, z)} terrain query.
 *
 * <p>The generator's per-column queries — surface height, mountain factor,
 * biome — are functions of the world seed and the column alone, and the same
 * column is asked for repeatedly within one chunk generation and again by
 * settlement planning, spawn placement, plant growth and temperature. Each one
 * is a multi-octave fBm, so the repeats dominate a world load.
 *
 * <p>Deliberately the simplest cache that works:
 *
 * <ul>
 *   <li><b>Direct-mapped, no eviction policy.</b> A colliding column overwrites
 *       its predecessor and the next lookup recomputes. The result is the value
 *       the query would have produced anyway, so a miss costs time and never
 *       correctness — which is what lets this be an optimization rather than a
 *       behaviour change.</li>
 *   <li><b>No thread safety.</b> The whole engine is single-threaded by design;
 *       see the thread model in {@code docs/ARCHITECTURE.md}. Sharing a
 *       generator across threads would corrupt the world long before it
 *       corrupted this.</li>
 *   <li><b>An explicit occupancy flag</b> rather than a reserved key, because
 *       every packed column value is a legitimate column — including zero,
 *       which is spawn.</li>
 * </ul>
 *
 * <p>Callers take a slot once and reuse it for the hit test and the store, so a
 * miss hashes the column once rather than twice:
 *
 * <pre>{@code
 * int slot = memo.slot(x, z);
 * if (memo.holds(slot, x, z)) {
 *     return memo.value(slot);
 * }
 * return memo.store(slot, x, z, expensiveQuery(x, z));
 * }</pre>
 */
abstract class ColumnMemo {

    /**
     * 4,096 columns, which comfortably covers a chunk's own 256 plus the
     * neighbouring columns decoration and POI placement reach into, at roughly
     * 50 KB per memo per world.
     */
    private static final int CAPACITY_BITS = 12;
    static final int CAPACITY = 1 << CAPACITY_BITS;
    private static final int MASK = CAPACITY - 1;

    private final long[] keys = new long[CAPACITY];
    private final boolean[] occupied = new boolean[CAPACITY];

    /** Slot for a column. Cheap integer mixing; the payoff is skipping an fBm. */
    final int slot(int x, int z) {
        return (int) ((x * 0x9E3779B1L + z * 0x85EBCA6BL) >>> 17) & MASK;
    }

    /** True when {@code slot} currently holds this exact column. */
    final boolean holds(int slot, int x, int z) {
        return occupied[slot] && keys[slot] == key(x, z);
    }

    final void claim(int slot, int x, int z) {
        keys[slot] = key(x, z);
        occupied[slot] = true;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    /** Surface height. */
    static final class OfInt extends ColumnMemo {
        private final int[] values = new int[CAPACITY];

        int value(int slot) {
            return values[slot];
        }

        int store(int slot, int x, int z, int value) {
            values[slot] = value;
            claim(slot, x, z);
            return value;
        }
    }

    /** Mountain factor and the other normalized noise fields. */
    static final class OfDouble extends ColumnMemo {
        private final double[] values = new double[CAPACITY];

        double value(int slot) {
            return values[slot];
        }

        double store(int slot, int x, int z, double value) {
            values[slot] = value;
            claim(slot, x, z);
            return value;
        }
    }

    /** Biome selection, which is three noise fields plus a height query. */
    static final class OfBiome extends ColumnMemo {
        private final Biome[] values = new Biome[CAPACITY];

        Biome value(int slot) {
            return values[slot];
        }

        Biome store(int slot, int x, int z, Biome value) {
            values[slot] = value;
            claim(slot, x, z);
            return value;
        }
    }
}
