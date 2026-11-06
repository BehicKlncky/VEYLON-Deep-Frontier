package com.veylon.world.urban;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoundedPlanCacheTest {

    @Test
    void theCacheNeverHoldsMoreThanItsCapacity() {
        BoundedPlanCache<Long, String> cache = new BoundedPlanCache<>(16);
        for (long key = 0; key < 1_000; key++) {
            cache.get(key, BoundedPlanCacheTest::plan);
            assertEquals(Math.min(key + 1, 16), cache.size());
        }
        assertEquals(16, cache.capacity());
    }

    @Test
    void aHitReusesThePlanAndTheLeastRecentlyUsedIsEvictedFirst() {
        List<Long> built = new ArrayList<>();
        BoundedPlanCache<Long, String> cache = new BoundedPlanCache<>(2);
        cache.get(1L, key -> record(built, key));
        cache.get(2L, key -> record(built, key));
        cache.get(1L, key -> record(built, key));   // hit: 2 is now the eldest
        cache.get(3L, key -> record(built, key));   // evicts 2
        cache.get(1L, key -> record(built, key));   // still cached
        cache.get(2L, key -> record(built, key));   // rebuilt

        assertEquals(List.of(1L, 2L, 3L, 2L), built);
    }

    @Test
    void anEvictedPlanIsRebuiltIdentically() {
        // Eviction is invisible to generation only because plans are pure.
        BoundedPlanCache<Long, String> cache = new BoundedPlanCache<>(1);
        String first = cache.get(7L, BoundedPlanCacheTest::plan);
        cache.get(8L, BoundedPlanCacheTest::plan);
        assertEquals(first, cache.get(7L, BoundedPlanCacheTest::plan));
    }

    @Test
    void misuseFailsLoudly() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedPlanCache<Long, String>(0));
        BoundedPlanCache<Long, String> cache = new BoundedPlanCache<>(4);
        assertThrows(NullPointerException.class, () -> cache.get(1L, key -> null));
        assertThrows(NullPointerException.class, () -> cache.get(null, BoundedPlanCacheTest::plan));
        assertEquals(0, cache.size());
        cache.get(1L, BoundedPlanCacheTest::plan);
        cache.clear();
        assertEquals(0, cache.size());
    }

    private static String plan(Long key) {
        return "plan-" + key;
    }

    private static String record(List<Long> built, Long key) {
        built.add(key);
        return plan(key);
    }
}
