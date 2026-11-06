package com.veylon.world;

import com.veylon.util.Vec3i;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Recorded block deltas with a chunk index. Reapplying a generated slice visits
 * only its own edits, rather than scanning the entire explored world's log.
 * The public map contract remains usable by the existing save codec.
 */
public final class ChunkBlockEdits extends AbstractMap<Vec3i, Byte> {
    private final Map<Vec3i, Byte> entries = new HashMap<>();
    private final Map<Long, Map<Vec3i, Byte>> chunks = new HashMap<>();

    private static long key(Vec3i pos) {
        return World.key(Math.floorDiv(pos.x(), Chunk.SX), Math.floorDiv(pos.z(), Chunk.SZ));
    }

    @Override
    public Byte put(Vec3i pos, Byte block) {
        if (pos == null || block == null) {
            throw new NullPointerException("block edits require a position and block id");
        }
        chunks.computeIfAbsent(key(pos), ignored -> new HashMap<>()).put(pos, block);
        return entries.put(pos, block);
    }

    @Override
    public Byte get(Object pos) { return entries.get(pos); }

    @Override
    public boolean containsKey(Object pos) { return entries.containsKey(pos); }

    @Override
    public int size() { return entries.size(); }

    @Override
    public Byte remove(Object pos) {
        Byte old = entries.remove(pos);
        if (old != null) { unindex((Vec3i) pos); }
        return old;
    }

    private void unindex(Vec3i pos) {
        long key = key(pos);
        Map<Vec3i, Byte> bucket = chunks.get(key);
        bucket.remove(pos);
        if (bucket.isEmpty()) { chunks.remove(key); }
    }

    @Override
    public void clear() { entries.clear(); chunks.clear(); }

    /** Applies last-write deltas directly before height/light finalisation. */
    public void applyTo(Chunk chunk) {
        Map<Vec3i, Byte> bucket = chunks.get(World.key(chunk.cx, chunk.cz));
        if (bucket == null) { return; }
        for (Map.Entry<Vec3i, Byte> entry : bucket.entrySet()) {
            Vec3i p = entry.getKey();
            chunk.set(Math.floorMod(p.x(), Chunk.SX), p.y(), Math.floorMod(p.z(), Chunk.SZ),
                    BlockType.byId(entry.getValue()));
        }
    }

    @Override
    public Set<Entry<Vec3i, Byte>> entrySet() {
        return new AbstractSet<>() {
            @Override public int size() { return entries.size(); }
            @Override public void clear() { ChunkBlockEdits.this.clear(); }
            @Override public Iterator<Entry<Vec3i, Byte>> iterator() {
                Iterator<Entry<Vec3i, Byte>> it = entries.entrySet().iterator();
                return new Iterator<>() {
                    private Vec3i current;
                    @Override public boolean hasNext() { return it.hasNext(); }
                    @Override public Entry<Vec3i, Byte> next() {
                        Entry<Vec3i, Byte> entry = it.next();
                        current = entry.getKey();
                        return new SimpleEntry<>(entry) {
                            @Override public Byte setValue(Byte value) {
                                Byte old = ChunkBlockEdits.this.put(getKey(), value);
                                super.setValue(value);
                                return old;
                            }
                        };
                    }
                    @Override public void remove() {
                        it.remove();
                        unindex(current);
                    }
                };
            }
        };
    }
}
