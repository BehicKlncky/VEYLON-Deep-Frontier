package com.veylon.world;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The world's chunk table agrees with a plain map through growth, removal and probe runs that wrap. */
class ChunkMapTest {

    @Test
    void randomInsertsRemovalsAndLookupsMatchAReferenceMap() {
        ChunkMap table = new ChunkMap();
        Map<Long, Chunk> reference = new HashMap<>();
        Random random = new Random(24);
        for (int step = 0; step < 200_000; step++) {
            // A walk-sized window of coordinates, negative ones included, so runs collide and wrap.
            int cx = random.nextInt(97) - 48, cz = random.nextInt(97) - 48;
            long key = World.key(cx, cz);
            switch (random.nextInt(3)) {
                case 0 -> {
                    Chunk c = new Chunk(cx, cz);
                    table.put(key, c);
                    reference.put(key, c);
                }
                case 1 -> assertSame(reference.remove(key), table.remove(key));
                default -> assertSame(reference.get(key), table.get(key));
            }
            assertEquals(reference.size(), table.size());
        }
        Set<Chunk> listed = new HashSet<>(table.values());
        assertEquals(reference.size(), listed.size());
        assertTrue(listed.containsAll(reference.values()));
        for (Map.Entry<Long, Chunk> e : reference.entrySet()) { assertSame(e.getValue(), table.get(e.getKey())); }
    }

    @Test
    void aDiagonalOfChunksNoLongerSharesOneSlot() {
        ChunkMap table = new ChunkMap();
        for (int i = -500; i <= 500; i++) { table.put(World.key(i, i), new Chunk(i, i)); }
        for (int i = -500; i <= 500; i++) { assertEquals(i, table.get(World.key(i, i)).cx); }
        assertNull(table.get(World.key(1, 2)));
        for (int i = -500; i <= 500; i += 2) { table.remove(World.key(i, i)); }
        for (int i = -499; i <= 499; i += 2) { assertEquals(i, table.get(World.key(i, i)).cz); }
        assertEquals(500, table.size());
    }
}
