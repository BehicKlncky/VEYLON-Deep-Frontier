package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.FixtureStates;

import java.util.List;

/** Frozen building contract, including the exact proposed collision geometry, before stamping. */
public record BuildingPlan(long id, int templateRevision, String template, Family family,
                           Variant variant, ParcelPlan parcel, int storeys, boolean basement,
                           List<Floor> floors, List<Fixture> fixtures, List<Vec3i> approaches,
                           List<Stair> stairs, PlanVolume voxels) {
    /**
     * Walk-ups first, then the commercial and civic catalogue of template revision five (D-98).
     * Appended only: names appear in plan fingerprints, never as saved ordinals.
     */
    public enum Family {
        APARTMENT, SHOP, GROCERY, CLINIC, SCHOOL, OFFICE, SECURITY, WAREHOUSE;

        /** The frozen walk-up families of template revisions one to four. */
        public boolean walkUp() { return this == APARTMENT || this == SHOP; }
    }
    public enum Variant { INTACT, DAMAGED, PICKED_CLEAN, OCCUPIED }
    /** Stable names are plan metadata; these enums are never serialized as ordinals. */
    public enum Function {
        ENTRY, KITCHEN, LIVING, BEDROOM, BATHROOM, STORAGE, UTILITY,
        CORRIDOR_STAIRS, SHOP_FLOOR, STOCKROOM, BASEMENT_STORAGE, BASEMENT_UTILITY,
        // Template revision five (milestone 12). PHARMACY and SECURITY rooms are the secured ones.
        CLINIC, PHARMACY, CLASSROOM, OFFICE, SECURITY, WAREHOUSE, WORKSHOP;

        /** Specialist stock behind a forcible lock: dispensary medicine, armoury ammunition. */
        public boolean secured() { return this == PHARMACY || this == SECURITY; }
    }
    public enum FixtureKind { DOOR, WINDOW, CONTAINER, FURNITURE, QUEST }

    /** Inclusive interior rectangle; every clear standing cell must have a return route. */
    public record Room(long id, List<Function> functions, ParcelPlan.Rect interior, Vec3i anchor) {
        public Room { functions = List.copyOf(functions); }
    }
    public record Floor(long id, int index, int slabY, List<Room> rooms, Vec3i landing) {
        public Floor { rooms = List.copyOf(rooms); }
    }
    /** Target is the interaction face's standing cell, not the solid container's cell. */
    public record Fixture(long id, FixtureKind kind, Vec3i position, int height, BlockType block,
                          Vec3i target, boolean locked) { }
    /** Consecutive centre cells of a two-wide switchback, including both landings. */
    public record Stair(int fromFloor, List<Vec3i> route) {
        public Stair { route = List.copyOf(route); }
    }

    public BuildingPlan {
        floors = List.copyOf(floors); fixtures = List.copyOf(fixtures);
        approaches = List.copyOf(approaches); stairs = List.copyOf(stairs);
    }

    public BuildingPlan withVoxels(PlanVolume replacement) {
        return new BuildingPlan(id, templateRevision, template, family, variant, parcel, storeys,
                basement, floors, fixtures, approaches, stairs, replacement);
    }

    public BuildingPlan withFixtures(List<Fixture> replacement) {
        return new BuildingPlan(id, templateRevision, template, family, variant, parcel, storeys,
                basement, floors, replacement, approaches, stairs, voxels);
    }

    /** Roof is a playable reservation too; the switchback continues to it. */
    public int roofY() { return UrbanDatum.slabY(storeys); }

    /**
     * Whether survivors own a fixture's contents. An occupied walk-up is owned throughout; an
     * occupied commercial or civic building is held only at street level and below, while its
     * upper storeys stay abandoned (partial occupation, D-100).
     */
    public boolean survivorOwned(Fixture fixture) {
        return variant == Variant.OCCUPIED && (family.walkUp() || UrbanIds.floorIndex(fixture.id()) <= 0);
    }

    /** Plan default for a fixture cell; touched saved entries take precedence, including explicit unlock. */
    public int fixtureStateAt(FixtureStates overrides, int x, int y, int z) {
        int saved = overrides.stateAt(x, y, z);
        if (saved >= 0) { return saved; }
        for (Fixture fixture : fixtures) {
            Vec3i p = fixture.position();
            if (fixture.kind() == FixtureKind.DOOR && p.x() == x && p.z() == z
                    && y >= p.y() && y < p.y() + fixture.height()) {
                return fixture.locked() ? FixtureStates.LOCK_KEYED : FixtureStates.LOCK_NONE;
            }
        }
        return FixtureStates.LOCK_NONE;
    }
}
