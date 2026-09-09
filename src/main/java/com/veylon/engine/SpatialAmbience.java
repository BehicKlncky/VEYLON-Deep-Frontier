package com.veylon.engine;

/** Fixed emitter layout; three independently synthesized directions per weather layer. */
final class SpatialAmbience {
    /** Buffer name, layer index and listener-relative unit position. Fire uses world coordinates. */
    record Emitter(String name, int layer, float x, float y, float z, boolean relative) { }

    static final Emitter[] EMITTERS = {
            new Emitter("Rain", 0, 0, 0.5f, -1, true),
            new Emitter("Wind", 1, 0, 0, -1, true),
            new Emitter("Fire", 2, 0, 0, 0, false),
            new Emitter("Cave", 3, 0, 0, -1, true),
            new Emitter("Crickets", 4, 0, 0, 1, true),
            new Emitter("Beacon", 5, 0, 0, 0, true),
            new Emitter("RainHigh", 6, 0, 0.5f, -1, true),
            new Emitter("WindHigh", 7, 0, 0, -1, true),
            new Emitter("RainLeft", 0, -1, 0.3f, 0.5f, true),
            new Emitter("RainRight", 0, 1, 0.3f, 0.5f, true),
            new Emitter("WindLeft", 1, -1, 0, 0.5f, true),
            new Emitter("WindRight", 1, 1, 0, 0.5f, true),
            new Emitter("RainHighLeft", 6, -1, 0.3f, 0.5f, true),
            new Emitter("RainHighRight", 6, 1, 0.3f, 0.5f, true),
            new Emitter("WindHighLeft", 7, -1, 0, 0.5f, true),
            new Emitter("WindHighRight", 7, 1, 0, 0.5f, true)
    };

    /** Constant-power allocation for three decorrelated weather emitters. */
    static final float WEATHER_GAIN = (float) (1 / Math.sqrt(3));
    /** Fire reference radius in blocks; world-derived heat already supplies distance gain. */
    static final float FIRE_REFERENCE_DISTANCE = 6;

    private SpatialAmbience() { }

    static boolean weather(int layer) { return layer == 0 || layer == 1 || layer >= 6; }
    static int channel(int layer) { return layer < 6 ? layer : layer - 6; }
    static float allocation(int layer) { return weather(layer) ? WEATHER_GAIN : 1; }
}
