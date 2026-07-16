package com.veylon.gfx;

import com.veylon.item.ItemType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Guards the string-keyed icon namespace independently of enum ordinals. */
class VisualIdTest {

    @Test
    void itemVisualIdsAreStableReadableAndUnique() {
        assertEquals("item/berry", IconAtlas.idFor(ItemType.BERRY));
        assertEquals("item/iron_pickaxe", IconAtlas.idFor(ItemType.IRON_PICKAXE));
        assertEquals("item/beacon_frame", IconAtlas.idFor(ItemType.BEACON_FRAME));

        Set<String> ids = Arrays.stream(ItemType.values())
                .map(IconAtlas::idFor)
                .collect(Collectors.toSet());
        assertEquals(ItemType.values().length, ids.size());
    }
}
