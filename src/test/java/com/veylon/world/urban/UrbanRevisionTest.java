package com.veylon.world.urban;

import com.veylon.world.Chunk;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The revision policy, the per-world revision state and the vertical datum. */
class UrbanRevisionTest {

    @Test
    void eachUrbanGeneratorBuildsExactlyOneLayoutRevision() {
        assertEquals(UrbanRevision.LAYOUT_DATUM_PLACEHOLDER,
                UrbanRevision.layoutRevisionOf(World.GEN_URBAN));
        assertEquals(UrbanRevision.LAYOUT_STREET_NETWORK,
                UrbanRevision.layoutRevisionOf(World.GEN_URBAN_STREETS));
        assertEquals(UrbanRevision.LAYOUT_BUILDING_PLANS,
                UrbanRevision.layoutRevisionOf(World.GEN_URBAN_PLANS));
        assertEquals(UrbanRevision.LAYOUT_GENERATED_APARTMENTS,
                UrbanRevision.layoutRevisionOf(World.GEN_URBAN_APARTMENTS));
        assertEquals(UrbanRevision.LAYOUT_SHAPED_STAIRS,
                UrbanRevision.layoutRevisionOf(World.GEN_URBAN_SHAPED_STAIRS));
        assertEquals(UrbanRevision.LAYOUT_STARTER, UrbanRevision.layoutRevisionOf(World.GEN_URBAN_STARTER));
        assertEquals(UrbanRevision.LAYOUT_ENCOUNTERS, UrbanRevision.layoutRevisionOf(World.GEN_URBAN_ENCOUNTERS));
        assertEquals(UrbanRevision.LAYOUT_CIVIC, UrbanRevision.layoutRevisionOf(World.GEN_URBAN_CIVIC));
        assertEquals(UrbanRevision.BUILDING_TEMPLATE_CIVIC, UrbanRevision.buildingTemplateRevisionOf(World.GEN_URBAN_CIVIC));
        assertEquals(UrbanRevision.LAYOUT_DISTRICTS, UrbanRevision.layoutRevisionOf(World.GEN_URBAN_DISTRICTS));
        assertEquals(UrbanRevision.BUILDING_TEMPLATE_DISTRICTS,
                UrbanRevision.buildingTemplateRevisionOf(World.GEN_URBAN_DISTRICTS));
        assertEquals(UrbanRevision.LAYOUT_UNDERGROUND, UrbanRevision.layoutRevisionOf(World.GEN_URBAN_UNDERGROUND));
        assertEquals(UrbanRevision.BUILDING_TEMPLATE_ROUTES,
                UrbanRevision.buildingTemplateRevisionOf(World.GEN_URBAN_UNDERGROUND));
        assertEquals(UrbanRevision.LAYOUT_ECOLOGY, UrbanRevision.layoutRevisionOf(World.GEN_URBAN_ECOLOGY));
        assertEquals(UrbanRevision.BUILDING_TEMPLATE_ROUTES,
                UrbanRevision.buildingTemplateRevisionOf(World.GEN_URBAN_ECOLOGY), "layout eleven keeps template seven");
        assertEquals(UrbanRevision.LAYOUT_SURVIVORS, UrbanRevision.layoutRevisionOf(World.GEN_URBAN_SURVIVORS));
        assertEquals(UrbanRevision.BUILDING_TEMPLATE_ROUTES,
                UrbanRevision.buildingTemplateRevisionOf(World.GEN_URBAN_SURVIVORS), "layout twelve keeps template seven");
        assertEquals(UrbanRevision.LAYOUT_STRONGHOLDS, UrbanRevision.layoutRevisionOf(World.GEN_URBAN_STRONGHOLDS));
        assertEquals(UrbanRevision.BUILDING_TEMPLATE_ROUTES,
                UrbanRevision.buildingTemplateRevisionOf(World.GEN_URBAN_STRONGHOLDS), "layout thirteen keeps template seven");
        assertEquals(World.GEN_URBAN_STRONGHOLDS, UrbanRevision.DEVELOPMENT_GENERATOR,
                "the development selector builds the newest layout (decision D-35)");
        for (int notUrban : new int[]{0, World.GEN_LEGACY, World.GEN_DEEP,
                World.GEN_CAVE_IDENTITIES, World.MAX_SUPPORTED_GENERATOR + 1}) {
            assertThrows(IllegalArgumentException.class,
                    () -> UrbanRevision.layoutRevisionOf(notUrban));
        }
        assertTrue(World.isUrbanGenerator(UrbanRevision.DEVELOPMENT_GENERATOR));
        assertTrue(World.isSupportedGenerator(UrbanRevision.DEVELOPMENT_GENERATOR));
    }

    @Test
    void contentRevisionsOutsideTheSupportedRangeAreRefusedWithAReason() {
        UrbanRevision.requireSupportedContent(UrbanRevision.CURRENT_CONTENT);
        UrbanRevision.requireSupportedContent(UrbanRevision.MIN_SUPPORTED_CONTENT);
        UrbanRevision.requireSupportedContent(UrbanRevision.ABSENT_CONTENT);

        IllegalArgumentException newer = assertThrows(IllegalArgumentException.class,
                () -> UrbanRevision.requireSupportedContent(UrbanRevision.CURRENT_CONTENT + 1));
        assertTrue(newer.getMessage().contains("newer than this build"), newer.getMessage());
        for (int retired : new int[]{UrbanRevision.MIN_SUPPORTED_CONTENT - 1, -1,
                Integer.MIN_VALUE}) {
            IllegalArgumentException old = assertThrows(IllegalArgumentException.class,
                    () -> UrbanRevision.requireSupportedContent(retired));
            assertTrue(old.getMessage().contains("not supported"), old.getMessage());
        }
    }

    @Test
    void aWorldsContentRevisionIsUnreadableUntilResolvedAndThenFixed() {
        UrbanWorldState state = new UrbanWorldState(1L, World.GEN_URBAN);
        assertEquals(UrbanRevision.LAYOUT_DATUM_PLACEHOLDER, state.layoutRevision());
        assertFalse(state.contentRevisionResolved());
        assertThrows(IllegalStateException.class, state::contentRevision,
                "content must not be rolled against a revision a load is about to replace");

        assertThrows(IllegalArgumentException.class,
                () -> state.resolveContentRevision(UrbanRevision.CURRENT_CONTENT + 1));
        assertFalse(state.contentRevisionResolved(), "a refused revision leaves it unresolved");

        state.resolveContentRevision(UrbanRevision.CURRENT_CONTENT);
        assertEquals(UrbanRevision.CURRENT_CONTENT, state.contentRevision());
        assertThrows(IllegalStateException.class,
                () -> state.resolveContentRevision(UrbanRevision.CURRENT_CONTENT),
                "a world resolves its content revision once");
        assertThrows(IllegalArgumentException.class,
                () -> new UrbanWorldState(1L, World.GEN_CAVE_IDENTITIES));
    }

    @Test
    void theDatumMatchesTheRecordedBandsAndFitsTheChunk() {
        assertEquals(32, UrbanDatum.slabY(UrbanDatum.MIN_FLOOR), "B2 slab");
        assertEquals(36, UrbanDatum.slabY(-1), "B1 slab");
        assertEquals(UrbanDatum.STREET_Y, UrbanDatum.slabY(0));
        assertEquals(88, UrbanDatum.slabY(UrbanDatum.MAX_FLOOR), "roof of twelve storeys");
        // Every planned void starts above the band where WaterSystem spreads
        // water sideways (y <= SEA_LEVEL).
        int lowestVoid = UrbanDatum.slabY(UrbanDatum.MIN_FLOOR) + 1;
        assertEquals(33, lowestVoid);
        assertTrue(lowestVoid > World.SEA_LEVEL);
        // Roof structures up to five above the top slab stay below the last two layers.
        assertTrue(UrbanDatum.slabY(UrbanDatum.MAX_FLOOR) + 5 <= Chunk.SY - 3);
        assertThrows(IllegalArgumentException.class,
                () -> UrbanDatum.slabY(UrbanDatum.MIN_FLOOR - 1));
        assertThrows(IllegalArgumentException.class,
                () -> UrbanDatum.slabY(UrbanDatum.MAX_FLOOR + 1));
    }

    @Test
    void everyCellOfTheDatumBelongsToExactlyOneFloor() {
        for (int floor = UrbanDatum.MIN_FLOOR; floor <= UrbanDatum.MAX_FLOOR; floor++) {
            int slab = UrbanDatum.slabY(floor);
            for (int y = slab; y < slab + UrbanDatum.STOREY_PITCH; y++) {
                assertEquals(floor, UrbanDatum.floorContaining(y), "y " + y);
            }
        }
        assertEquals(0, UrbanDatum.floorContaining(UrbanDatum.STREET_Y + 1), "feet on the street");
        assertThrows(IllegalArgumentException.class,
                () -> UrbanDatum.floorContaining(UrbanDatum.slabY(UrbanDatum.MIN_FLOOR) - 1));
        assertThrows(IllegalArgumentException.class,
                () -> UrbanDatum.floorContaining(UrbanDatum.slabY(UrbanDatum.MAX_FLOOR)
                        + UrbanDatum.STOREY_PITCH));
    }
}
