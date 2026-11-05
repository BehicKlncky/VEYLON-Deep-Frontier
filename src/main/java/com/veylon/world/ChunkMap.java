package com.veylon.world;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * The world's chunk table: open addressing on the packed chunk key ({@link World#key}) with a
 * multiplicative hash and linear probing (urban milestone 24).
 *
 * <p>A {@code HashMap<Long, Chunk>} hashed every key to {@code cx ^ cz}, so the chunks along each
 * diagonal shared one bucket. A long journey treeified those buckets and every lookup of a chunk
 * the world does not hold (the mesher's light sampling at the streaming edge, a route probing an
 * unloaded column) walked a red-black tree, boxing a key on the way. Here a lookup is a few array
 * probes and allocates nothing. Removal shifts the following run back, so no tombstones build up
 * as chunks are evicted and regenerated.
 */
final class ChunkMap {
    private static final int INITIAL_CAPACITY = 1024;

    private long[] keys = new long[INITIAL_CAPACITY];
    private Chunk[] values = new Chunk[INITIAL_CAPACITY];
    private int shift = 64 - Integer.numberOfTrailingZeros(INITIAL_CAPACITY);
    private int size;
    private final Collection<Chunk> view = new Values();

    private int slot(long key) {
        return (int) ((key * 0x9E3779B97F4A7C15L) >>> shift);
    }

    int size() { return size; }

    Chunk get(long key) {
        int mask = values.length - 1;
        for (int i = slot(key); ; i = (i + 1) & mask) {
            Chunk c = values[i];
            if (c == null || keys[i] == key) { return c; }
        }
    }

    /** Adds or replaces the chunk under a key. */
    void put(long key, Chunk chunk) {
        if (chunk == null) { throw new NullPointerException("chunk"); }
        if ((size + 1) * 2 > values.length) { grow(); }
        int mask = values.length - 1;
        for (int i = slot(key); ; i = (i + 1) & mask) {
            if (values[i] == null) {
                keys[i] = key;
                values[i] = chunk;
                size++;
                return;
            }
            if (keys[i] == key) {
                values[i] = chunk;
                return;
            }
        }
    }

    /** Removes and returns the chunk under a key, or null. */
    Chunk remove(long key) {
        int mask = values.length - 1;
        int i = slot(key);
        while (values[i] != null && keys[i] != key) { i = (i + 1) & mask; }
        Chunk removed = values[i];
        if (removed == null) { return null; }
        // Backward shift: pull each later entry of the run into the gap unless its home slot
        // lies cyclically after the gap, so every remaining key stays reachable from its slot.
        int gap = i;
        for (int j = (gap + 1) & mask; values[j] != null; j = (j + 1) & mask) {
            int home = slot(keys[j]);
            boolean stays = gap <= j ? gap < home && home <= j : gap < home || home <= j;
            if (!stays) {
                keys[gap] = keys[j];
                values[gap] = values[j];
                gap = j;
            }
        }
        values[gap] = null;
        size--;
        return removed;
    }

    /** A live view of the chunks, in table order; not to be modified while iterated. */
    Collection<Chunk> values() { return view; }

    private void grow() {
        long[] oldKeys = keys;
        Chunk[] oldValues = values;
        keys = new long[oldKeys.length * 2];
        values = new Chunk[oldValues.length * 2];
        shift--;
        size = 0;
        for (int i = 0; i < oldValues.length; i++) {
            if (oldValues[i] != null) { put(oldKeys[i], oldValues[i]); }
        }
    }

    private final class Values extends AbstractCollection<Chunk> {
        @Override public int size() { return size; }

        @Override public Iterator<Chunk> iterator() {
            return new Iterator<>() {
                private final Chunk[] table = values;
                private int next = advance(0);

                private int advance(int from) {
                    while (from < table.length && table[from] == null) { from++; }
                    return from;
                }

                @Override public boolean hasNext() { return next < table.length; }

                @Override public Chunk next() {
                    if (next >= table.length) { throw new NoSuchElementException(); }
                    Chunk c = table[next];
                    next = advance(next + 1);
                    return c;
                }
            };
        }
    }
}
