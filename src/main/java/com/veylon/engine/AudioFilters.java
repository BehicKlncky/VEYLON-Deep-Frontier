package com.veylon.engine;

/** One-pole cutoffs in Hz; migrated from the measured 22.05 kHz pole time constants. */
final class AudioFilters {
    /** Hz pole frequency for deep rumble; legacy alpha .035. */
    static final float DEEP_RUMBLE_HZ = 125.028824f;
    /** Hz pole frequency for stave low; legacy alpha .04. */
    static final float STAVE_LOW_HZ = 143.259340f;
    /** Hz pole frequency for thunder; legacy alpha .045. */
    static final float THUNDER_HZ = 161.585055f;
    /** Hz pole frequency for boom; legacy alpha .05. */
    static final float BOOM_HZ = 180.006969f;
    /** Hz pole frequency for growl; legacy alpha .06. */
    static final float GROWL_HZ = 217.143469f;
    /** Hz pole frequency for swish low; legacy alpha .1. */
    static final float SWISH_LOW_HZ = 369.748664f;
    /** Hz pole frequency for snow; legacy alpha .12. */
    static final float SNOW_HZ = 448.614151f;
    /** Hz pole frequency for stave high; legacy alpha .14. */
    static final float STAVE_HIGH_HZ = 529.292796f;
    /** Hz pole frequency for grass; legacy alpha .18. */
    static final float GRASS_HZ = 696.437075f;
    /** Hz pole frequency for wing; legacy alpha .2. */
    static final float WING_HZ = 783.092503f;
    /** Hz pole frequency for soft hit; legacy alpha .22. */
    static final float SOFT_HIT_HZ = 871.941970f;
    /** Hz pole frequency for pour; legacy alpha .25. */
    static final float POUR_HZ = 1009.581826f;
    /** Hz pole frequency for cough; legacy alpha .28. */
    static final float COUGH_HZ = 1152.841166f;
    /** Hz pole frequency for water; legacy alpha .3. */
    static final float WATER_HZ = 1251.703098f;
    /** Hz pole frequency for wood; legacy alpha .35. */
    static final float WOOD_HZ = 1511.775132f;
    /** Hz pole frequency for impact; legacy alpha .4. */
    static final float IMPACT_HZ = 1792.674329f;
    /** Hz pole frequency for place; legacy alpha .45. */
    static final float PLACE_HZ = 2098.029140f;
    /** Hz pole frequency for debris; legacy alpha .5. */
    static final float DEBRIS_HZ = 2432.507492f;
    /** Hz pole frequency for grit; legacy alpha .55. */
    static final float GRIT_HZ = 2802.256155f;
    /** Hz pole frequency for swish high; legacy alpha .6. */
    static final float SWISH_HIGH_HZ = 3215.599994f;
    /** Hz pole frequency for stone; legacy alpha .7. */
    static final float STONE_HZ = 4225.181821f;
    /** Hz pole frequency for bullet; legacy alpha .75. */
    static final float BULLET_HZ = 4865.014983f;
    /** Hz pole frequency for pistol; legacy alpha .85. */
    static final float PISTOL_HZ = 6657.689312f;
    /** Hz of the original gulp modulation, formerly 0.01 radians per sample. */
    static final float GULP_MODULATION_HZ = 35.093665f;
    /** Seconds of the original 1,400-sample ambience edge fade. */
    static final float LEGACY_FADE_SECONDS = 1400f / 22050;
    /** Events per second for the original fuse, preserved during rate migration. */
    static final float FUSE_EVENTS_PER_SECOND = 44.1f;
    /** Events per second in the old rain recipe, removed by the next milestone. */
    static final float RAIN_EVENTS_PER_SECOND = 13.23f;
    /** White-noise amplitude compensation for unchanged energy per Hz at 44.1 kHz. */
    static final float NOISE_SCALE = (float) Math.sqrt(ProceduralAudio.RATE / 22050.0);

    private AudioFilters() { }

    /** Matched-pole coefficient, with frequency expressed in Hz rather than samples. */
    static float alpha(float hz) {
        return alpha(hz, ProceduralAudio.RATE);
    }

    static float alpha(float hz, int rate) {
        return (float) -Math.expm1(-2 * Math.PI * hz / rate);
    }
}
