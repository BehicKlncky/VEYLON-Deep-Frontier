package com.veylon.engine;

/** EFX standard-reverb tuning: seconds for decay, all other entries are linear unitless factors. */
final class ReverbPresets {
    /** Order: decay seconds, overall gain, high-frequency gain, HF decay ratio, density, diffusion. */
    private static final float[][] VALUES = {
            {0.35f, 0.06f, 0.95f, 0.85f, 0.35f, 0.50f}, // open terrain
            {0.75f, 0.16f, 0.45f, 0.50f, 0.80f, 0.85f}, // absorptive forest
            {1.65f, 0.28f, 0.60f, 0.65f, 0.85f, 0.90f}, // shallow rock
            {3.80f, 0.40f, 0.50f, 0.55f, 1.00f, 1.00f}, // deep cave
            {0.65f, 0.22f, 0.35f, 0.50f, 0.60f, 0.70f}, // small shelter
            {2.60f, 0.35f, 0.70f, 0.80f, 1.00f, 1.00f}, // large stone structure
            // Urban milestone 22: short and soft among furniture; long and bright off hard walls;
            // a dark concrete cellar; a long tube that flutters rather than blooms.
            {0.45f, 0.20f, 0.55f, 0.60f, 0.55f, 0.70f}, // furnished room
            {1.40f, 0.28f, 0.65f, 0.70f, 0.85f, 0.90f}, // hall: shop floor, warehouse, office, concourse
            {1.05f, 0.30f, 0.45f, 0.55f, 0.90f, 0.85f}, // basement or cellar
            {2.30f, 0.34f, 0.55f, 0.65f, 1.00f, 0.75f}  // transit tunnel or service passage
    };
    /** Per-second convergence speed; avoids abrupt changes at zone boundaries. */
    static final float BLEND_SPEED = 1.2f;
    /** Seconds between bounded native parameter submissions. */
    static final float SUBMIT_INTERVAL = 0.05f;

    private ReverbPresets() { }

    static float value(AudioEnvironment.Zone zone, int parameter) { return VALUES[zone.ordinal()][parameter]; }

    static void blend(float[] current, AudioEnvironment.Zone zone, float dt) {
        float amount = (float) -Math.expm1(-Math.max(0, dt) * BLEND_SPEED);
        for (int i = 0; i < current.length; i++) current[i] += (value(zone, i) - current[i]) * amount;
    }
}
