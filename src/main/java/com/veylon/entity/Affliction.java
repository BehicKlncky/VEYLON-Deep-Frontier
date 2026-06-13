package com.veylon.entity;

/**
 * Medical conditions the player can suffer from. Each active affliction tracks
 * remaining seconds; effects are applied in {@link Player#tickNeeds}.
 */
public enum Affliction {
    BLEEDING("Bleeding", 0.85f, 0.15f, 0.15f,
            "Losing blood. Apply a bandage."),
    INFECTION("Infection", 0.65f, 0.75f, 0.25f,
            "Fevered wound. Use medicine or a poultice."),
    SPRAIN("Sprained Leg", 0.9f, 0.7f, 0.3f,
            "Can't sprint. Apply a splint or rest."),
    BURN("Burns", 1f, 0.45f, 0.15f,
            "Burned skin. Apply a herbal poultice."),
    FOOD_POISONING("Food Poisoning", 0.55f, 0.8f, 0.35f,
            "Bad food or water. Use medicine."),
    SICKNESS("Sickness", 0.45f, 0.85f, 0.5f,
            "Feeling ill. Rest warm and dry, or use medicine."),
    SMOKE("Smoke Inhalation", 0.6f, 0.6f, 0.6f,
            "Coughing on smoke. Get to fresh air.");

    public final String displayName;
    public final float r, g, b;
    public final String hint;

    Affliction(String displayName, float r, float g, float b, String hint) {
        this.displayName = displayName;
        this.r = r;
        this.g = g;
        this.b = b;
        this.hint = hint;
    }
}
