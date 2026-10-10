package com.veylon.world.urban;

import com.veylon.world.urban.UrbanIds.Kind;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Urban ids are persisted, so their packing is a save format: these tests pin
 * the bit layout, prove every field survives the round trip at its limits, and
 * prove a damaged or mistyped id is refused instead of naming another object.
 */
class UrbanIdsTest {

    @Test
    void thePackedLayoutIsFrozen() {
        // Hand-computed from the documented layout. Moving one of these breaks
        // every saved reference to an urban object.
        assertEquals(0x1000000000000000L, UrbanIds.district(0, 0));
        assertEquals(0x13FFFF0000800000L, UrbanIds.district(-1, 2));
        assertEquals(0x63FFFD000303C1FFL, fixture(-3, 12, 7, 2, -2, 31, 15));
        assertEquals(0x701000FC003FFFFFL, UrbanIds.resident(
                building(4096, -4096, 127, 3), 31, 255));
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7),
                List.of(Kind.values()).stream().map(kind -> kind.code).toList(),
                "kind codes are stored, append-only and never ordinals");
    }

    @Test
    void everyFieldRoundTripsAtItsLimits() {
        int[][] regions = {{0, 0}, {-1, -1}, {UrbanIds.MIN_REGION, UrbanIds.MAX_REGION},
                {UrbanIds.MAX_REGION, UrbanIds.MIN_REGION}, {4096, -4096}};
        for (int[] region : regions) {
            for (int floor : new int[]{UrbanDatum.MIN_FLOOR, -1, 0, UrbanDatum.MAX_FLOOR}) {
                long id = fixture(region[0], region[1], UrbanIds.MAX_PARCEL,
                        UrbanIds.MAX_BUILDING, floor, UrbanIds.MAX_ROOM, UrbanIds.MAX_SLOT);
                assertEquals(Kind.FIXTURE, UrbanIds.kind(id));
                assertEquals(region[0], UrbanIds.regionX(id));
                assertEquals(region[1], UrbanIds.regionZ(id));
                assertEquals(UrbanIds.MAX_PARCEL, UrbanIds.parcelIndex(id));
                assertEquals(UrbanIds.MAX_BUILDING, UrbanIds.buildingIndex(id));
                assertEquals(floor, UrbanIds.floorIndex(id));
                assertEquals(UrbanIds.MAX_ROOM, UrbanIds.roomIndex(id));
                assertEquals(UrbanIds.MAX_SLOT, UrbanIds.slotIndex(id));
                assertEquals(id, UrbanIds.parse(UrbanIds.format(id)));
            }
            long resident = UrbanIds.resident(building(region[0], region[1], 0, 0),
                    UrbanIds.MAX_RESIDENT_SLOT, UrbanIds.MAX_RESIDENT_GENERATION);
            assertEquals(region[0], UrbanIds.regionX(resident));
            assertEquals(region[1], UrbanIds.regionZ(resident));
            assertEquals(UrbanIds.MAX_RESIDENT_SLOT, UrbanIds.residentSlot(resident));
            assertEquals(UrbanIds.MAX_RESIDENT_GENERATION, UrbanIds.residentGeneration(resident));
            assertEquals(resident, UrbanIds.parse(UrbanIds.format(resident)));
        }
    }

    @Test
    void parentsWalkTheHierarchyToTheDistrict() {
        long fixture = fixture(-3, 12, 7, 2, -2, 31, 15);
        long room = UrbanIds.parent(fixture);
        long floor = UrbanIds.parent(room);
        long building = UrbanIds.parent(floor);
        long parcel = UrbanIds.parent(building);
        long district = UrbanIds.parent(parcel);

        assertEquals(UrbanIds.room(UrbanIds.floor(building(-3, 12, 7, 2), -2), 31), room);
        assertEquals(UrbanIds.floor(building(-3, 12, 7, 2), -2), floor);
        assertEquals(building(-3, 12, 7, 2), building);
        assertEquals(UrbanIds.parcel(UrbanIds.district(-3, 12), 7), parcel);
        assertEquals(UrbanIds.district(-3, 12), district);
        assertEquals(UrbanIds.NONE, UrbanIds.parent(district));
        assertEquals(building, UrbanIds.parent(UrbanIds.resident(building, 4, 1)),
                "a resident belongs to the building its site occupies");
    }

    @Test
    void distinctPlanPositionsNeverShareAnId() {
        // Index 0 is a real parcel, room and slot, so kinds must not collide
        // either: a building and its first floor's first room are different ids.
        Set<Long> ids = new HashSet<>();
        int expected = 0;
        for (int rx = -2; rx <= 2; rx++) {
            for (int rz = -2; rz <= 2; rz++) {
                long district = UrbanIds.district(rx, rz);
                expected += addAll(ids, district);
                for (int p = 0; p <= 2; p++) {
                    long parcel = UrbanIds.parcel(district, p);
                    expected += addAll(ids, parcel);
                    for (int b = 0; b <= 1; b++) {
                        long building = UrbanIds.building(parcel, b);
                        expected += addAll(ids, building, UrbanIds.resident(building, 0, 0),
                                UrbanIds.resident(building, 0, 1));
                        for (int f = UrbanDatum.MIN_FLOOR; f <= UrbanDatum.MAX_FLOOR; f += 7) {
                            long floor = UrbanIds.floor(building, f);
                            expected += addAll(ids, floor);
                            for (int r = 0; r <= 1; r++) {
                                long room = UrbanIds.room(floor, r);
                                expected += addAll(ids, room, UrbanIds.fixture(room, 0),
                                        UrbanIds.fixture(room, 1));
                            }
                        }
                    }
                }
            }
        }
        assertEquals(expected, ids.size());
        assertFalse(ids.contains(UrbanIds.NONE));
    }

    @Test
    void corruptOrMistypedIdsAreRefused() {
        long district = UrbanIds.district(5, -5);
        long parcel = UrbanIds.parcel(district, 3);
        long building = UrbanIds.building(parcel, 1);
        long room = UrbanIds.room(UrbanIds.floor(building, 0), 2);
        long[] invalid = {
                UrbanIds.NONE,
                8L << 60,                                   // reserved kind code
                0xF000000000000000L,                        // reserved kind code
                -1L,
                district | (1L << 58),                      // reserved bits
                district | (1L << 15),                      // a district with a parcel
                parcel | 1L,                                // a parcel with a slot
                (building & ~(0xfL << 60)) | (4L << 60) | (15L << 9), // floor 13
                room | 1L                                   // a room with a slot
        };
        for (long id : invalid) {
            assertFalse(UrbanIds.isValid(id), () -> "accepted " + Long.toHexString(id));
            assertThrows(IllegalArgumentException.class, () -> UrbanIds.requireValid(id));
        }
        assertThrows(IllegalArgumentException.class, () -> UrbanIds.requireKind(room, Kind.FIXTURE));
        assertThrows(IllegalArgumentException.class, () -> UrbanIds.floorIndex(building),
                "a building has no floor field");
        assertThrows(IllegalArgumentException.class, () -> UrbanIds.room(building, 0),
                "a room's parent must be a floor");
        assertThrows(IllegalArgumentException.class,
                () -> UrbanIds.residentSlot(UrbanIds.fixture(room, 0)));
    }

    @Test
    void constructionRejectsOutOfRangeFields() {
        long building = building(0, 0, 0, 0);
        long floor = UrbanIds.floor(building, 0);
        List<Runnable> outOfRange = List.of(
                () -> UrbanIds.district(UrbanIds.MAX_REGION + 1, 0),
                () -> UrbanIds.district(0, UrbanIds.MIN_REGION - 1),
                () -> UrbanIds.parcel(UrbanIds.district(0, 0), UrbanIds.MAX_PARCEL + 1),
                () -> UrbanIds.parcel(UrbanIds.district(0, 0), -1),
                () -> UrbanIds.building(UrbanIds.parent(building), UrbanIds.MAX_BUILDING + 1),
                () -> UrbanIds.floor(building, UrbanDatum.MIN_FLOOR - 1),
                () -> UrbanIds.floor(building, UrbanDatum.MAX_FLOOR + 1),
                () -> UrbanIds.room(floor, UrbanIds.MAX_ROOM + 1),
                () -> UrbanIds.fixture(UrbanIds.room(floor, 0), UrbanIds.MAX_SLOT + 1),
                () -> UrbanIds.resident(building, UrbanIds.MAX_RESIDENT_SLOT + 1, 0),
                () -> UrbanIds.resident(building, 0, UrbanIds.MAX_RESIDENT_GENERATION + 1));
        for (Runnable construction : outOfRange) {
            assertThrows(IllegalArgumentException.class, construction::run);
        }
    }

    @Test
    void theTextFormIsCanonicalAndStrict() {
        assertEquals("urban:district:-1,2", UrbanIds.format(UrbanIds.district(-1, 2)));
        assertEquals("urban:fixture:-3,12:7:2:-2:31:15",
                UrbanIds.format(fixture(-3, 12, 7, 2, -2, 31, 15)));
        assertEquals("urban:resident:4096,-4096:127:3:31:255", UrbanIds.format(
                UrbanIds.resident(building(4096, -4096, 127, 3), 31, 255)));
        String longest = UrbanIds.format(fixture(UrbanIds.MIN_REGION, UrbanIds.MIN_REGION,
                UrbanIds.MAX_PARCEL, UrbanIds.MAX_BUILDING, UrbanDatum.MAX_FLOOR,
                UrbanIds.MAX_ROOM, UrbanIds.MAX_SLOT));
        assertTrue(longest.length() <= UrbanIds.MAX_TEXT_LENGTH, longest);

        String[] rejected = {
                "", "urban:", "urban:district", "urban:district:1", "urban:district:1,2:",
                "URBAN:district:1,2", "urban:District:1,2", "urban:widget:1,2",
                "urban:district:01,2", "urban:district:+1,2", "urban:district:-0,2",
                "urban:district: 1,2", "urban:district:1,2,3", "urban:district:1;2",
                "urban:district:131072,0", "urban:district:0,-131073",
                "urban:district:99999999999,0", "urban:parcel:1,2:128",
                "urban:floor:0,0:0:0:13", "urban:floor:0,0:0:0:-3",
                "urban:fixture:0,0:0:0:0:0:16", "urban:fixture:0,0:0:0:0:0",
                "urban:resident:0,0:0:0:32:0", "urban:resident:0,0:0:0:0:256",
                "storage:1:2:3:4", "urban:district:1,2" + " ".repeat(UrbanIds.MAX_TEXT_LENGTH)};
        for (String text : rejected) {
            assertThrows(IllegalArgumentException.class, () -> UrbanIds.parse(text),
                    () -> "accepted '" + text + "'");
        }
        assertThrows(IllegalArgumentException.class, () -> UrbanIds.parse(null));
    }

    // ------------------------------------------------------------------

    private static long building(int rx, int rz, int parcel, int building) {
        return UrbanIds.building(UrbanIds.parcel(UrbanIds.district(rx, rz), parcel), building);
    }

    private static long fixture(int rx, int rz, int parcel, int building, int floor, int room,
                                int slot) {
        return UrbanIds.fixture(UrbanIds.room(UrbanIds.floor(
                building(rx, rz, parcel, building), floor), room), slot);
    }

    private static int addAll(Set<Long> ids, long... added) {
        for (long id : added) {
            assertTrue(UrbanIds.isValid(id));
            ids.add(id);
        }
        return added.length;
    }
}
