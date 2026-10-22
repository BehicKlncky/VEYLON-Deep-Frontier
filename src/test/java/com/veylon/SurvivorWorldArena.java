package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.UrbanSite;
import com.veylon.settlement.UrbanSiteDirector;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Raycaster;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.SurvivorSites;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Generated generator-15 worlds for the milestone-16 tests: the planned camp and hidden refuge
 * nearest the spawn, and the production frame step. Only the player's pose is staged; residents,
 * needs, doors and storage go through the game's own ticks and commands.
 */
public final class SurvivorWorldArena {
    public static final long SEED = 20_260_731L;
    public static final float DT = 1f / 20f;

    private SurvivorWorldArena() {
    }

    public static Game session(long seed, GameMode mode) {
        Game g = new Game();
        g.newWorld(seed, true, World.GEN_URBAN_SURVIVORS, mode);
        g.appState = Game.AppState.PLAYING;
        return g;
    }

    /** The planned site of a kind whose anchor lies nearest the spawn, within two regions. */
    public static SurvivorSites.Site nearest(Game g, SurvivorSites.Kind kind) {
        List<SurvivorSites.Site> all = new ArrayList<>();
        for (int rz = -2; rz <= 2; rz++) {
            for (int rx = -2; rx <= 2; rx++) {
                for (SurvivorSites.Site s : g.world.urban.sites(rx, rz).sites()) {
                    if (s.kind() == kind) { all.add(s); }
                }
            }
        }
        int[] spawn = g.world.urban.spawnColumn();
        return all.stream().min(Comparator.comparingDouble(s -> s.anchor().distSq(spawn[0], s.anchor().y(), spawn[1])))
                .orElseThrow(() -> new AssertionError("no " + kind + " within two regions of the spawn"));
    }

    /** Loads a cell's neighbourhood and stands the player on it, at rest, facing along +x. */
    public static void standAt(Game g, Vec3i cell) {
        g.world.ensureChunks(cell.x(), cell.z(), 4, 10_000);
        g.player.pos.set(cell.x() + .5f, cell.y() + .001f, cell.z() + .5f);
        g.player.vel.zero();
        g.player.onGround = true;
        g.camera.yaw = 90;
    }

    /** Runs the world's own frame advance (clock, fast, medium and slow ticks) for a while. */
    public static void run(Game g, float seconds) {
        for (float t = 0; t < seconds; t += DT) {
            g.advanceWorld(DT);
        }
    }

    /** One settlement slow tick: registration, needs, activation and dormancy. */
    public static void slowTick(Game g) {
        g.settlementManager.slowTick(g, 10f);
    }

    /** The registered record of a planned site, or null. */
    public static UrbanSite site(Game g, SurvivorSites.Site plan) {
        return UrbanSiteDirector.registered(g.world, plan.id());
    }

    /** The live bodies of a site's people, in slot order. */
    public static List<Npc> live(UrbanSite s) {
        List<Npc> out = new ArrayList<>();
        for (Settlement.Resident r : s.residents) {
            if (r.live != null && !r.live.dead) { out.add(r.live); }
        }
        return out;
    }

    /** The building a site occupies. */
    public static BuildingPlan building(Game g, SurvivorSites.Site plan) {
        return g.world.urban.buildingAt(plan.anchor().x(), plan.anchor().z());
    }

    /** A refuge's loose panel (its lower cell). */
    public static Vec3i panel(Game g, SurvivorSites.Site refuge) {
        for (BuildingPlan.Fixture f : building(g, refuge).fixtures()) {
            if (f.block() == BlockType.HIDDEN_PANEL) { return f.position(); }
        }
        throw new AssertionError("refuge without a panel");
    }

    /** The basement hall cell in front of a refuge's loose panel. */
    public static Vec3i hall(Game g, SurvivorSites.Site refuge) {
        Vec3i p = panel(g, refuge), t = refuge.entry();
        return new Vec3i(2 * p.x() - t.x(), p.y(), 2 * p.z() - t.z());
    }

    /** Aims at a cell and presses F through the full interaction sweep. */
    public static void pressF(Game g, Vec3i cell) {
        g.targetHit = new Raycaster.Hit(cell.x(), cell.y(), cell.z(), 0, 0, -1, 2,
                g.world.getBlock(cell.x(), cell.y(), cell.z()));
        g.interactions.interact();
    }

    /** The feet cell of a body. */
    public static Vec3i feet(Npc n) {
        return new Vec3i((int) Math.floor(n.pos.x), (int) Math.floor(n.pos.y + .01f), (int) Math.floor(n.pos.z));
    }
}
