package com.veylon.world;

import com.veylon.util.Vec3i;

/** A procedurally generated point of interest; discovered POIs show on the map. */
public class Poi {

    public enum PoiType {
        CRASH_DEBRIS("Crash Debris", 0.75f, 0.55f, 0.25f),
        RESEARCH_POD("Research Pod", 0.85f, 0.85f, 0.95f),
        ANCIENT_RUIN("Ancient Ruin", 0.45f, 0.55f, 0.95f),
        PREDATOR_DEN("Predator Den", 0.85f, 0.25f, 0.2f),
        SUPPLY_CACHE("Supply Cache", 0.35f, 0.8f, 0.45f),
        // ---- 0.3.0 deep-frontier additions (append-only) ----
        CAVE_MOUTH("Cave Mouth", 0.55f, 0.45f, 0.35f),
        ABANDONED_MINE("Abandoned Mine", 0.6f, 0.5f, 0.3f),
        SMUGGLER_CACHE("Smuggler Cache", 0.55f, 0.65f, 0.35f),
        HIDEOUT_CAVE("Headhunter Hideout", 0.8f, 0.3f, 0.3f),
        RESONANT_SHRINE("Resonant Shrine", 0.4f, 0.8f, 0.85f),
        STALKER_NEST("Gloomstalker Nest", 0.5f, 0.35f, 0.6f),
        EXPEDITION_CAMP("Lost Expedition", 0.75f, 0.7f, 0.5f);

        public final String displayName;
        public final float r, g, b;

        PoiType(String displayName, float r, float g, float b) {
            this.displayName = displayName;
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    public final PoiType type;
    public final Vec3i pos;
    public boolean discovered;

    public Poi(PoiType type, Vec3i pos) {
        this.type = type;
        this.pos = pos;
    }
}
