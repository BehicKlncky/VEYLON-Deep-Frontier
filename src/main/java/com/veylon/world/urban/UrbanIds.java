package com.veylon.world.urban;

import java.util.Locale;

/**
 * Stable identities for urban plan objects: districts, parcels, buildings,
 * floors, rooms, fixtures, residents and encounters.
 *
 * <p>An id names a place in the plan hierarchy and nothing else. It is built
 * from plan coordinates — region, parcel index, building slot, floor, room,
 * slot — and never from visit, activation or list order, so the same object has
 * the same id however the world was explored. Doors, windows and containers are
 * all fixtures: a container's id is the id of the fixture that holds it.
 * Residents hang off the building their site occupies, with a slot and a
 * generation counter for replacements, never a list index.
 *
 * <h2>Packed form</h2>
 *
 * <p>Saves store the 64-bit packed form, so this layout is frozen. Bit 63 is the
 * most significant:
 *
 * <table>
 *   <caption>Packed urban id</caption>
 *   <tr><th>Bits</th><th>Field</th><th>Range</th></tr>
 *   <tr><td>63–60</td><td>kind code ({@link Kind#code})</td><td>1–8; 0 is never valid</td></tr>
 *   <tr><td>59–58</td><td>reserved</td><td>always 0</td></tr>
 *   <tr><td>57–40</td><td>region x, 18-bit two's complement</td>
 *       <td>{@value #MIN_REGION}..{@value #MAX_REGION}</td></tr>
 *   <tr><td>39–22</td><td>region z, same</td><td>same</td></tr>
 *   <tr><td>21–15</td><td>parcel</td><td>0..{@value #MAX_PARCEL}</td></tr>
 *   <tr><td>14–13</td><td>building slot</td><td>0..{@value #MAX_BUILDING}</td></tr>
 *   <tr><td>12–9</td><td>floor + 2</td><td>floor {@value UrbanDatum#MIN_FLOOR}..{@value UrbanDatum#MAX_FLOOR}</td></tr>
 *   <tr><td>8–4</td><td>room</td><td>0..{@value #MAX_ROOM}</td></tr>
 *   <tr><td>3–0</td><td>slot</td><td>0..{@value #MAX_SLOT}</td></tr>
 * </table>
 *
 * <p>A resident reuses bits 12–0 for its resident slot (12–8,
 * 0..{@value #MAX_RESIDENT_SLOT}) and generation (7–0,
 * 0..{@value #MAX_RESIDENT_GENERATION}). Every field a kind does not use must be
 * zero; {@link #isValid} checks that, so a corrupt or mistyped id is rejected
 * rather than resolved to some other object.
 *
 * <h2>Text form</h2>
 *
 * <p>For mission targets and logs, following the {@code storage:<id>:x:y:z}
 * precedent: {@code urban:<kind>:<rx>,<rz>} followed by one {@code :}-separated
 * index per level, for example {@code urban:fixture:-3,12:7:0:2:5:1}. A resident
 * ends {@code :<slot>:<generation>}. {@link #parse} accepts only the canonical
 * form {@link #format} produces.
 */
public final class UrbanIds {

    /**
     * What an id names. The {@link #code} is what saves store — never the
     * ordinal — and codes are append-only: 9 to 15 are reserved for later kinds.
     */
    public enum Kind {
        DISTRICT(1, "district"),
        PARCEL(2, "parcel"),
        BUILDING(3, "building"),
        FLOOR(4, "floor"),
        ROOM(5, "room"),
        FIXTURE(6, "fixture"),
        RESIDENT(7, "resident"),
        ENCOUNTER(8, "encounter");

        /** Stored code, bits 63–60 of a packed id. */
        public final int code;
        /** Stable text id, used by {@link #format} and {@link #parse}. */
        public final String id;

        Kind(int code, String id) {
            this.code = code;
            this.id = id;
        }

        static Kind ofCode(int code) {
            for (Kind kind : values()) {
                if (kind.code == code) {
                    return kind;
                }
            }
            return null;
        }

        static Kind ofId(String id) {
            for (Kind kind : values()) {
                if (kind.id.equals(id)) {
                    return kind;
                }
            }
            return null;
        }
    }

    /** "No id". Kind code 0 is never valid, so no real object can collide with it. */
    public static final long NONE = 0L;

    public static final int MIN_REGION = -(1 << 17);
    public static final int MAX_REGION = (1 << 17) - 1;
    public static final int MAX_PARCEL = 127;
    public static final int MAX_BUILDING = 3;
    public static final int MAX_ROOM = 31;
    public static final int MAX_SLOT = 15;
    public static final int MAX_RESIDENT_SLOT = 31;
    public static final int MAX_RESIDENT_GENERATION = 255;
    /** Longest text form {@link #parse} reads; the longest canonical id is 44 characters. */
    public static final int MAX_TEXT_LENGTH = 64;

    private static final String PREFIX = "urban:";
    private static final int KIND_SHIFT = 60;
    private static final long RESERVED_MASK = 0b11L << 58;
    private static final int REGION_BITS = 18;
    private static final long REGION_MASK = (1L << REGION_BITS) - 1;
    private static final int RX_SHIFT = 40;
    private static final int RZ_SHIFT = 22;
    private static final int PARCEL_SHIFT = 15;
    private static final long PARCEL_MASK = 0x7f;
    private static final int BUILDING_SHIFT = 13;
    private static final long BUILDING_MASK = 0x3;
    private static final long TAIL_MASK = (1L << BUILDING_SHIFT) - 1;
    private static final int FLOOR_SHIFT = 9;
    private static final long FLOOR_MASK = 0xf;
    private static final int FLOOR_BIAS = -UrbanDatum.MIN_FLOOR;
    private static final int ROOM_SHIFT = 4;
    private static final long ROOM_MASK = 0x1f;
    private static final long SLOT_MASK = 0xf;
    private static final int RESIDENT_SLOT_SHIFT = 8;
    private static final long RESIDENT_SLOT_MASK = 0x1f;
    private static final long GENERATION_MASK = 0xff;

    private UrbanIds() {
    }

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    /** The district of planning region {@code (rx, rz)}. */
    public static long district(int rx, int rz) {
        requireRange(rx, MIN_REGION, MAX_REGION, "region x");
        requireRange(rz, MIN_REGION, MAX_REGION, "region z");
        return withKind(Kind.DISTRICT, ((rx & REGION_MASK) << RX_SHIFT)
                | ((rz & REGION_MASK) << RZ_SHIFT));
    }

    public static long parcel(long districtId, int parcel) {
        requireKind(districtId, Kind.DISTRICT);
        requireRange(parcel, 0, MAX_PARCEL, "parcel");
        return relabel(districtId, Kind.PARCEL) | ((long) parcel << PARCEL_SHIFT);
    }

    public static long building(long parcelId, int building) {
        requireKind(parcelId, Kind.PARCEL);
        requireRange(building, 0, MAX_BUILDING, "building slot");
        return relabel(parcelId, Kind.BUILDING) | ((long) building << BUILDING_SHIFT);
    }

    public static long floor(long buildingId, int floor) {
        requireKind(buildingId, Kind.BUILDING);
        requireRange(floor, UrbanDatum.MIN_FLOOR, UrbanDatum.MAX_FLOOR, "floor");
        return relabel(buildingId, Kind.FLOOR) | ((long) (floor + FLOOR_BIAS) << FLOOR_SHIFT);
    }

    public static long room(long floorId, int room) {
        requireKind(floorId, Kind.FLOOR);
        requireRange(room, 0, MAX_ROOM, "room");
        return relabel(floorId, Kind.ROOM) | ((long) room << ROOM_SHIFT);
    }

    /** A door, window, container or other fixture slot of a room. */
    public static long fixture(long roomId, int slot) {
        requireKind(roomId, Kind.ROOM);
        requireRange(slot, 0, MAX_SLOT, "fixture slot");
        return relabel(roomId, Kind.FIXTURE) | slot;
    }

    /** One immutable infected resident at a room/street anchor; slots never change on reactivation. */
    public static long encounter(long roomId, int slot) {
        requireKind(roomId, Kind.ROOM);
        requireRange(slot, 0, MAX_SLOT, "encounter slot");
        return relabel(roomId, Kind.ENCOUNTER) | slot;
    }

    /** A resident of the site occupying a building, in a stable slot. */
    public static long resident(long buildingId, int slot, int generation) {
        requireKind(buildingId, Kind.BUILDING);
        requireRange(slot, 0, MAX_RESIDENT_SLOT, "resident slot");
        requireRange(generation, 0, MAX_RESIDENT_GENERATION, "resident generation");
        return relabel(buildingId, Kind.RESIDENT)
                | ((long) slot << RESIDENT_SLOT_SHIFT) | generation;
    }

    // ------------------------------------------------------------------
    // Validation and fields
    // ------------------------------------------------------------------

    /**
     * True for an id this layout can produce: a known kind, zero reserved bits,
     * every field in range and every field the kind does not use zero.
     */
    public static boolean isValid(long id) {
        Kind kind = Kind.ofCode((int) (id >>> KIND_SHIFT));
        if (kind == null || (id & RESERVED_MASK) != 0) {
            return false;
        }
        long parcelBits = (id >>> PARCEL_SHIFT) & PARCEL_MASK;
        long buildingBits = (id >>> BUILDING_SHIFT) & BUILDING_MASK;
        long tail = id & TAIL_MASK;
        long floorBits = (tail >>> FLOOR_SHIFT) & FLOOR_MASK;
        long roomBits = (tail >>> ROOM_SHIFT) & ROOM_MASK;
        long slotBits = tail & SLOT_MASK;
        return switch (kind) {
            case DISTRICT -> parcelBits == 0 && buildingBits == 0 && tail == 0;
            case PARCEL -> buildingBits == 0 && tail == 0;
            case BUILDING -> tail == 0;
            case FLOOR -> floorBits + UrbanDatum.MIN_FLOOR <= UrbanDatum.MAX_FLOOR
                    && roomBits == 0 && slotBits == 0;
            case ROOM -> floorBits + UrbanDatum.MIN_FLOOR <= UrbanDatum.MAX_FLOOR
                    && slotBits == 0;
            case FIXTURE, ENCOUNTER -> floorBits + UrbanDatum.MIN_FLOOR <= UrbanDatum.MAX_FLOOR;
            case RESIDENT -> true;
        };
    }

    /**
     * @throws IllegalArgumentException unless {@link #isValid} holds
     */
    public static long requireValid(long id) {
        if (!isValid(id)) {
            throw new IllegalArgumentException("invalid urban id " + hex(id));
        }
        return id;
    }

    /**
     * @throws IllegalArgumentException for an invalid id or one of another kind
     */
    public static long requireKind(long id, Kind kind) {
        if (kind(id) != kind) {
            throw new IllegalArgumentException("urban id " + hex(id) + " is a "
                    + kind(id).id + ", not a " + kind.id);
        }
        return id;
    }

    /**
     * @throws IllegalArgumentException for an invalid id
     */
    public static Kind kind(long id) {
        requireValid(id);
        return Kind.ofCode((int) (id >>> KIND_SHIFT));
    }

    public static int regionX(long id) {
        kind(id);
        return signExtend(id >>> RX_SHIFT);
    }

    public static int regionZ(long id) {
        kind(id);
        return signExtend(id >>> RZ_SHIFT);
    }

    public static int parcelIndex(long id) {
        requireDepth(id, Kind.PARCEL);
        return (int) ((id >>> PARCEL_SHIFT) & PARCEL_MASK);
    }

    public static int buildingIndex(long id) {
        requireDepth(id, Kind.BUILDING);
        return (int) ((id >>> BUILDING_SHIFT) & BUILDING_MASK);
    }

    public static int floorIndex(long id) {
        requireDepth(id, Kind.FLOOR);
        return (int) ((id >>> FLOOR_SHIFT) & FLOOR_MASK) - FLOOR_BIAS;
    }

    public static int roomIndex(long id) {
        requireDepth(id, Kind.ROOM);
        return (int) ((id >>> ROOM_SHIFT) & ROOM_MASK);
    }

    public static int slotIndex(long id) {
        requireDepth(id, Kind.FIXTURE);
        return (int) (id & SLOT_MASK);
    }

    public static int residentSlot(long id) {
        requireKind(id, Kind.RESIDENT);
        return (int) ((id >>> RESIDENT_SLOT_SHIFT) & RESIDENT_SLOT_MASK);
    }

    public static int residentGeneration(long id) {
        requireKind(id, Kind.RESIDENT);
        return (int) (id & GENERATION_MASK);
    }

    /**
     * The id one level up: fixture to room to floor to building to parcel to
     * district, and resident to building. A district has no parent.
     */
    public static long parent(long id) {
        return switch (kind(id)) {
            case DISTRICT -> NONE;
            case PARCEL -> relabel(id & ~(PARCEL_MASK << PARCEL_SHIFT), Kind.DISTRICT);
            case BUILDING -> relabel(id & ~(BUILDING_MASK << BUILDING_SHIFT), Kind.PARCEL);
            case FLOOR, RESIDENT -> relabel(id & ~TAIL_MASK, Kind.BUILDING);
            case ROOM -> relabel(id & ~((ROOM_MASK << ROOM_SHIFT) | SLOT_MASK), Kind.FLOOR);
            case FIXTURE, ENCOUNTER -> relabel(id & ~SLOT_MASK, Kind.ROOM);
        };
    }

    // ------------------------------------------------------------------
    // Text form
    // ------------------------------------------------------------------

    public static String format(long id) {
        Kind kind = kind(id);
        StringBuilder text = new StringBuilder(MAX_TEXT_LENGTH).append(PREFIX).append(kind.id)
                .append(':').append(regionX(id)).append(',').append(regionZ(id));
        if (kind == Kind.DISTRICT) {
            return text.toString();
        }
        text.append(':').append(parcelIndex(id));
        if (kind == Kind.PARCEL) {
            return text.toString();
        }
        text.append(':').append(buildingIndex(id));
        if (kind == Kind.RESIDENT) {
            return text.append(':').append(residentSlot(id))
                    .append(':').append(residentGeneration(id)).toString();
        }
        if (kind == Kind.BUILDING) {
            return text.toString();
        }
        text.append(':').append(floorIndex(id));
        if (kind == Kind.FLOOR) {
            return text.toString();
        }
        text.append(':').append(roomIndex(id));
        if (kind == Kind.ROOM) {
            return text.toString();
        }
        return text.append(':').append(slotIndex(id)).toString();
    }

    /**
     * Reads the canonical text form back to the packed id.
     *
     * @throws IllegalArgumentException for anything {@link #format} would not
     *         have produced, including out-of-range indices, leading zeros,
     *         signs other than a single leading minus, whitespace and extra fields
     */
    public static long parse(String text) {
        if (text == null || text.length() > MAX_TEXT_LENGTH || !text.startsWith(PREFIX)) {
            throw new IllegalArgumentException("not an urban id: " + abbreviate(text));
        }
        String[] parts = text.substring(PREFIX.length()).split(":", -1);
        Kind kind = Kind.ofId(parts[0]);
        int expectedParts = kind == null ? -1 : switch (kind) {
            case DISTRICT -> 2;
            case PARCEL -> 3;
            case BUILDING -> 4;
            case FLOOR -> 5;
            case ROOM, RESIDENT -> 6;
            case FIXTURE, ENCOUNTER -> 7;
        };
        if (parts.length != expectedParts) {
            throw new IllegalArgumentException("malformed urban id: " + text);
        }
        String[] region = parts[1].split(",", -1);
        if (region.length != 2) {
            throw new IllegalArgumentException("malformed urban id region: " + text);
        }
        long id = district(parseIndex(region[0], text), parseIndex(region[1], text));
        if (kind != Kind.DISTRICT) {
            id = parcel(id, parseIndex(parts[2], text));
        }
        if (kind != Kind.DISTRICT && kind != Kind.PARCEL) {
            id = building(id, parseIndex(parts[3], text));
        }
        if (kind == Kind.RESIDENT) {
            id = resident(id, parseIndex(parts[4], text), parseIndex(parts[5], text));
        } else {
            if (parts.length > 4) {
                id = floor(id, parseIndex(parts[4], text));
            }
            if (parts.length > 5) {
                id = room(id, parseIndex(parts[5], text));
            }
            if (parts.length > 6) {
                id = kind == Kind.ENCOUNTER ? encounter(id, parseIndex(parts[6], text))
                        : fixture(id, parseIndex(parts[6], text));
            }
        }
        if (!format(id).equals(text)) {
            throw new IllegalArgumentException("non-canonical urban id: " + text);
        }
        return id;
    }

    // ------------------------------------------------------------------

    private static long withKind(Kind kind, long fields) {
        return ((long) kind.code << KIND_SHIFT) | fields;
    }

    private static long relabel(long id, Kind kind) {
        return withKind(kind, id & ~(0xfL << KIND_SHIFT));
    }

    private static void requireDepth(long id, Kind level) {
        Kind kind = kind(id);
        boolean has = kind == Kind.RESIDENT
                ? level.code <= Kind.BUILDING.code
                : kind.code >= level.code;
        if (!has) {
            throw new IllegalArgumentException("a " + kind.id + " id has no " + level.id + " field");
        }
    }

    private static int signExtend(long bits) {
        int raw = (int) (bits & REGION_MASK);
        return (raw << (Integer.SIZE - REGION_BITS)) >> (Integer.SIZE - REGION_BITS);
    }

    private static void requireRange(int value, int min, int max, String label) {
        if (value < min || value > max) {
            throw new IllegalArgumentException("urban " + label + " " + value + " is outside "
                    + min + ".." + max);
        }
    }

    private static int parseIndex(String token, String text) {
        if (!token.matches("-?(0|[1-9][0-9]{0,6})") || token.equals("-0")) {
            throw new IllegalArgumentException("malformed urban id index '" + token + "' in " + text);
        }
        return Integer.parseInt(token);
    }

    private static String hex(long id) {
        return String.format(Locale.ROOT, "0x%016x", id);
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "null";
        }
        return text.length() <= MAX_TEXT_LENGTH ? text : text.substring(0, MAX_TEXT_LENGTH) + "...";
    }
}
