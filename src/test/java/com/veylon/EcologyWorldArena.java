package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.world.World;
import com.veylon.world.urban.EncounterAnchors;
import com.veylon.world.urban.UrbanSpecies;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Generated generator-14 worlds for the milestone-15 population and save tests: the planned
 * anchors of every species near the spawn, and the production admission gate. Only the
 * observer's pose is staged.
 */
public final class EcologyWorldArena {
    public static final long SEED = 20_260_731L;

    private EcologyWorldArena() {
    }

    public static Game session(long seed, GameMode mode) {
        Game g = new Game();
        g.newWorld(seed, true, World.GEN_URBAN_ECOLOGY, mode);
        return g;
    }

    /** Every catalogue-five anchor within three regions of the spawn: buildings, stations and ecology. */
    public static List<EncounterAnchors.Anchor> anchors(Game g) {
        List<EncounterAnchors.Anchor> out = new ArrayList<>();
        for (int rz = -3; rz <= 3; rz++) {
            for (int rx = -3; rx <= 3; rx++) {
                for (var parcel : g.world.urban.regionPlan(rx, rz).parcels()) {
                    out.addAll(EncounterAnchors.forParcel(g.world, parcel));
                }
                out.addAll(EncounterAnchors.forStation(g.world, rx, rz));
                for (int sb = 0; sb < 16; sb++) {
                    out.addAll(EncounterAnchors.forEcology(g.world, rx, rz, sb));
                }
            }
        }
        return out;
    }

    public static List<EncounterAnchors.Anchor> anchors(Game g, Predicate<EncounterAnchors.Anchor> keep) {
        return anchors(g).stream().filter(keep).toList();
    }

    public static EncounterAnchors.Anchor first(Game g, UrbanSpecies species) {
        return anchors(g, a -> a.species() == species).stream().findFirst()
                .orElseThrow(() -> new AssertionError("no planned " + species + " near the spawn"));
    }

    /**
     * Loads the anchor's neighbourhood and stands the observer 24 blocks east of it, facing
     * further east, so admission's breathing-room and view guards are met by the pose alone.
     */
    public static void observeAway(Game g, EncounterAnchors.Anchor a) {
        g.world.ensureChunks(a.cell().x(), a.cell().z(), 3, 10_000);
        g.player.pos.set(a.cell().x() + 24.5f, a.cell().y(), a.cell().z() + .5f);
        g.player.vel.zero();
        g.camera.yaw = 90;
    }
}
