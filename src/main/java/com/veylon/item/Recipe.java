package com.veylon.item;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Recipe {

    public final String name;
    /** Null for special recipes (blueprint decoding) that have no item output. */
    public final ItemType result;
    public final int resultCount;
    public final Station station;
    /** Blueprint id that must be known before this recipe shows as craftable; null = always known. */
    public final String blueprint;
    public final Map<ItemType, Integer> ingredients;

    public Recipe(String name, ItemType result, int resultCount, Station station, Object... pairs) {
        this(name, result, resultCount, station, null, pairs);
    }

    public Recipe(String name, ItemType result, int resultCount, Station station,
                  String blueprint, Object... pairs) {
        this.name = name;
        this.result = result;
        this.resultCount = resultCount;
        this.station = station;
        this.blueprint = blueprint;
        this.ingredients = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            ingredients.put((ItemType) pairs[i], (Integer) pairs[i + 1]);
        }
    }
}
