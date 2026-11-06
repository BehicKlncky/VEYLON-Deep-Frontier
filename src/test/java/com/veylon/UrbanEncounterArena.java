package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.world.World;
import com.veylon.world.urban.EncounterAnchors;
import java.util.ArrayList;
import java.util.List;

/** Generated geometry and production admission; only the observer pose is staged. */
public final class UrbanEncounterArena {
    private UrbanEncounterArena() { }
    public static Game session(long seed) {
        Game g = new Game(); g.newWorld(seed, true, World.GEN_URBAN_ENCOUNTERS, GameMode.SURVIVAL);
        return g;
    }
    public static List<EncounterAnchors.Anchor> anchors(Game g) {
        var anchors = new ArrayList<EncounterAnchors.Anchor>();
        for (int z = -1; z <= 1; z++) {
            for (int x = -1; x <= 1; x++) {
                for (var parcel : g.world.urban.regionPlan(x, z).parcels()) {
                    anchors.addAll(EncounterAnchors.forParcel(g.world, parcel));
                }
            }
        }
        return anchors;
    }
    public static void observeAway(Game g, EncounterAnchors.Anchor a) {
        g.world.ensureChunks(a.cell().x(), a.cell().z(), 3, 10_000);
        g.player.pos.set(a.cell().x() + 24.5f, a.cell().y(), a.cell().z() + .5f);
        g.player.vel.zero(); g.camera.yaw = 90;
    }
    public static Npc activate(Game g, boolean indoor) {
        for (var a : anchors(g)) {
            if (a.indoor() != indoor || g.entities.encounters.resident(a.id()) != null) { continue; }
            observeAway(g, a);
            Npc n = g.entities.encounters.tryActivate(g, a);
            if (n != null) { return n; }
        }
        throw new AssertionError("no supported generated " + (indoor ? "indoor" : "street") + " encounter");
    }
}
