package com.veylon.entity;

/** A footprint or blood mark left in the world; fades with age. */
public class Track {

    public static final float MAX_AGE = 180f;

    public final float x, y, z;
    /** Heading of the creature when the mark was left, in degrees. */
    public final float yaw;
    /** Creature that left it; null for blood marks. */
    public final Creature.CreatureType type;
    public final boolean blood;
    public float age;

    public Track(float x, float y, float z, float yaw, Creature.CreatureType type, boolean blood) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.type = type;
        this.blood = blood;
    }

    public boolean fresh() {
        return age < 60f;
    }

    public String describe() {
        String what = blood ? "Blood trail" : type.displayName + " tracks";
        String freshness = age < 45 ? "fresh" : (age < 120 ? "recent" : "old");
        return what + " (" + freshness + ") heading " + compass();
    }

    private String compass() {
        // yaw 0 faces -Z (north).
        float a = ((yaw % 360) + 360) % 360;
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return dirs[Math.round(a / 45f) % 8];
    }
}
