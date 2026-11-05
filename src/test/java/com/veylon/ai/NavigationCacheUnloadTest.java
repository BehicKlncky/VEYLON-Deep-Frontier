package com.veylon.ai;

import com.veylon.util.Vec3i;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A dropped chunk takes every cached route through its columns with it, and only those (urban milestone 24). */
class NavigationCacheUnloadTest {

    @Test
    void unloadingColumnsForgetsTheRoutesThatCrossThem() {
        NavigationCache cache = new NavigationCache();
        Vec3i a = new Vec3i(2, 41, 2), b = new Vec3i(30, 41, 2), c = new Vec3i(100, 41, 100), d = new Vec3i(104, 41, 100);
        cache.put(a, b, List.of(a, new Vec3i(16, 41, 2), b));
        cache.put(c, d, List.of(c, d));
        cache.unloaded(16, 0, 31, 15);
        assertNull(cache.get(a, b), "the route through the dropped chunk is forgotten");
        assertNotNull(cache.get(c, d), "a route elsewhere is kept");
        assertEquals(1, cache.size());
    }
}
