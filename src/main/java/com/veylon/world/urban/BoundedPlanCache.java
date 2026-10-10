package com.veylon.world.urban;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * A fixed-capacity, least-recently-used cache for derived urban plans.
 *
 * <p>Plans are pure functions of the seed, the revision and a stable id, so an
 * evicted plan is rebuilt identically on its next request: eviction bounds
 * memory without changing a single generated block, whatever order the world
 * asks in. That is the only kind of value this belongs to — persistent state
 * must never live in a cache that can drop it.
 *
 * <p>Owned per world through {@link UrbanWorldState} and touched only on the
 * main thread, like the rest of the world.
 *
 * @param <K> stable plan key, typically a packed {@link UrbanIds} id
 * @param <V> immutable plan
 */
public final class BoundedPlanCache<K, V> {

    private final int capacity;
    private final LinkedHashMap<K, V> entries;

    /**
     * @throws IllegalArgumentException unless {@code capacity} is positive
     */
    public BoundedPlanCache(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("plan cache capacity must be positive: " + capacity);
        }
        this.capacity = capacity;
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > BoundedPlanCache.this.capacity;
            }
        };
    }

    /**
     * The cached plan for {@code key}, built by {@code planner} on a miss.
     *
     * @throws NullPointerException if the planner returns null
     */
    public V get(K key, Function<? super K, ? extends V> planner) {
        Objects.requireNonNull(key, "key");
        V plan = entries.get(key);
        if (plan == null) {
            plan = Objects.requireNonNull(planner.apply(key), "planner returned no plan");
            entries.put(key, plan);
        }
        return plan;
    }

    public int size() {
        return entries.size();
    }

    public int capacity() {
        return capacity;
    }

    public void clear() {
        entries.clear();
    }
}
