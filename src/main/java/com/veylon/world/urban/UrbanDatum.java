package com.veylon.world.urban;

/**
 * The vertical datum every urban generator builds on (urban-survival decision
 * D-05).
 *
 * <p>A chunk is 96 blocks tall, so the city is laid out in fixed bands rather
 * than on a terrain height: solid fill up to y 31, a second basement whose slab
 * is y 32, a first basement at y 36, the street and every ground-floor slab at
 * y 40, then one storey every four blocks — a slab and three clear cells, the
 * headroom the pathfinder needs to step. Every planned void therefore sits at
 * y 33 or above, clear of the band where {@code WaterSystem} spreads water
 * sideways.
 *
 * <p>Floor indices are the same numbers {@link UrbanIds} stores: -2 and -1 are
 * the basements, 0 the ground floor, and floor {@code n} of an {@code n}-storey
 * building is its roof.
 */
public final class UrbanDatum {

    /** Pavement and ground-floor slab; feet stand at {@code STREET_Y + 1}. */
    public static final int STREET_Y = 40;
    /** One slab plus three clear cells per storey. */
    public static final int STOREY_PITCH = 4;
    /** The second basement level (B2), slab at y 32. */
    public static final int MIN_FLOOR = -2;
    /** The roof of the tallest permitted building, twelve storeys, slab at y 88. */
    public static final int MAX_FLOOR = 12;

    private UrbanDatum() {
    }

    /** True for a floor index the datum defines, {@value #MIN_FLOOR} to {@value #MAX_FLOOR}. */
    public static boolean isFloor(int floor) {
        return floor >= MIN_FLOOR && floor <= MAX_FLOOR;
    }

    /**
     * The y of a floor's slab.
     *
     * @throws IllegalArgumentException for a floor outside the datum
     */
    public static int slabY(int floor) {
        if (!isFloor(floor)) {
            throw new IllegalArgumentException("urban floor " + floor + " is outside "
                    + MIN_FLOOR + ".." + MAX_FLOOR);
        }
        return STREET_Y + STOREY_PITCH * floor;
    }

    /**
     * The floor whose slab or clear cells hold a block y: the slab at
     * {@link #slabY} and the three cells above it belong to one floor.
     *
     * @throws IllegalArgumentException for a y below the B2 slab or above the
     *         tallest roof's clear band
     */
    public static int floorContaining(int y) {
        int floor = Math.floorDiv(y - STREET_Y, STOREY_PITCH);
        if (!isFloor(floor)) {
            throw new IllegalArgumentException("y " + y + " is outside the urban floors "
                    + MIN_FLOOR + ".." + MAX_FLOOR);
        }
        return floor;
    }
}
