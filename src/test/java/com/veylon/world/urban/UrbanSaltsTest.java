package com.veylon.world.urban;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every urban plan decision has its own salt (decision D-38). */
class UrbanSaltsTest {

    @Test
    void everySaltIsDistinctAndNonZero() throws IllegalAccessException {
        Map<Long, String> seen = new HashMap<>();
        int count = 0;
        for (Field field : UrbanSalts.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers) || field.getType() != long.class) {
                continue;
            }
            assertTrue(Modifier.isPublic(modifiers) && Modifier.isFinal(modifiers),
                    field.getName() + " must be a public constant");
            long salt = field.getLong(null);
            assertNotEquals(0L, salt, field.getName());
            assertNull(seen.put(salt, field.getName()),
                    () -> field.getName() + " repeats the salt of " + seen.get(salt));
            count++;
        }
        assertTrue(count >= 17, "the salt class lost its declarations: " + count);
    }

    @Test
    void theDerivationIsPureAndEveryInputMatters() {
        long base = UrbanHash.mix(7L, 2, UrbanSalts.DISTRICT, 1, 2, 3);
        assertEquals(base, UrbanHash.mix(7L, 2, UrbanSalts.DISTRICT, 1, 2, 3));
        assertNotEquals(base, UrbanHash.mix(8L, 2, UrbanSalts.DISTRICT, 1, 2, 3), "seed");
        assertNotEquals(base, UrbanHash.mix(7L, 3, UrbanSalts.DISTRICT, 1, 2, 3), "revision");
        assertNotEquals(base, UrbanHash.mix(7L, 2, UrbanSalts.TREE, 1, 2, 3), "salt");
        assertNotEquals(base, UrbanHash.mix(7L, 2, UrbanSalts.DISTRICT, 2, 1, 3), "coordinate order");
        assertNotEquals(base, UrbanHash.mix(7L, 2, UrbanSalts.DISTRICT, 1, 2, 4), "last coordinate");
        for (int i = 0; i < 2_000; i++) {
            double unit = UrbanHash.unit(i, 2, UrbanSalts.TREE, i, -i, 0);
            assertTrue(unit >= 0 && unit < 1);
            int ranged = UrbanHash.range(-5, 5, i, 2, UrbanSalts.LINE_JITTER_X, i, 1, 0);
            assertTrue(ranged >= -5 && ranged <= 5);
        }
    }
}
