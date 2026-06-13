package com.veylon.world;

public enum Biome {
    MEADOW("Meadow", 16f, 0.55f, 0.010f, 0.060f, 0.60f, BlockType.GRASS, BlockType.DIRT),
    PINE_FOREST("Pine Forest", 8f, 0.62f, 0.055f, 0.030f, 0.55f, BlockType.GRASS, BlockType.DIRT),
    ROCKY_HIGHLANDS("Rocky Highlands", 3f, 0.35f, 0.006f, 0.012f, 0.25f, BlockType.STONE, BlockType.STONE),
    MARSH("Wet Marsh", 14f, 0.90f, 0.018f, 0.090f, 0.45f, BlockType.GRASS, BlockType.CLAY),
    COLD_RIDGE("Cold Ridge", -9f, 0.50f, 0.014f, 0.008f, 0.18f, BlockType.SNOW, BlockType.DIRT),
    SCRUBLAND("Dry Scrubland", 27f, 0.15f, 0.003f, 0.028f, 0.35f, BlockType.SAND, BlockType.DIRT);

    public final String displayName;
    /** Baseline air temperature in degrees C. */
    public final float baseTemp;
    /** 0..1 baseline soil moisture. */
    public final float moisture;
    public final float treeDensity;
    public final float plantDensity;
    public final float animalChance;
    public final BlockType surface;
    public final BlockType subsurface;

    Biome(String displayName, float baseTemp, float moisture, float treeDensity,
          float plantDensity, float animalChance, BlockType surface, BlockType subsurface) {
        this.displayName = displayName;
        this.baseTemp = baseTemp;
        this.moisture = moisture;
        this.treeDensity = treeDensity;
        this.plantDensity = plantDensity;
        this.animalChance = animalChance;
        this.surface = surface;
        this.subsurface = subsurface;
    }
}
