package com.veylon.entity;

import com.veylon.Game;
import com.veylon.simulation.SimulationSystem;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;

import java.util.ArrayList;
import java.util.List;

import static com.veylon.entity.CombustionConstants.MAX_BURN_RESIDUES;
import static com.veylon.entity.CombustionConstants.RAIN_EXTINGUISH_SECONDS;
import static com.veylon.entity.CombustionConstants.RESIDUE_FLAME_SECONDS;
import static com.veylon.entity.CombustionConstants.RESIDUE_MAX_STEP;
import static com.veylon.entity.CombustionConstants.RESIDUE_SMOKE_SECONDS;

/**
 * Hands a dying body's fire on to its remains ({@link BurnResidue}) and ages
 * the flames and smoke those remains show.
 *
 * <p><b>Capture.</b> The one moment a residue is made is the death
 * transition, where the body's representation is chosen: {@link RagdollSystem}
 * for a body that falls whole, {@link BodyFragmentSystem} for one a blast
 * blows apart, the player's remains included. {@link #capture} copies the
 * body's scorch, and if it was alight its intensity and afterburn left; a
 * body with neither leaves none. Nothing captures from a body that merely
 * left the world, so a trader going home or a resident whose settlement went
 * dormant leaves nothing behind.
 *
 * <p><b>Ageing.</b> {@link #update} runs once per simulated frame beside the
 * ragdoll and fragment steps, so a paused game or a closed world ages nothing,
 * and ages each residue once however many pieces share it. Flames burn down
 * over at most {@link CombustionConstants#RESIDUE_FLAME_SECONDS}, water at the
 * core of the remains puts them out at once and open rain after {@link
 * CombustionConstants#RAIN_EXTINGUISH_SECONDS} (the living body's rules), and
 * smoke thins over {@link CombustionConstants#RESIDUE_SMOKE_SECONDS}. Then the
 * residue is let go; its scorch stays on the remains for as long as they last.
 *
 * <p><b>Bounds.</b> At most {@link CombustionConstants#MAX_BURN_RESIDUES} are
 * aged at once; a new one past that ends the oldest's flames and smoke. When
 * every ragdoll, corpse, carcass or piece carrying one has left the world, it
 * is let go at the next update. Nothing here refers to a body, living or
 * dead, so nothing is kept alive by it.
 *
 * <p><b>Transient.</b> Never saved. A save settles falling bodies with their
 * residue still burning; a load or a new world {@link #reset}s this and builds
 * remains without one.
 */
public final class BurnResidueSystem implements SimulationSystem {

    /** Residues still flaming or smoking, oldest first. */
    private final List<BurnResidue> tracked = new ArrayList<>(MAX_BURN_RESIDUES);

    /** Residues made with flames, and those ended early by the cap. Diagnostics only. */
    public long totalCaptured;
    public long totalEvicted;

    @Override
    public void reset() {
        for (int i = 0; i < tracked.size(); i++) {
            tracked.get(i).tracked = false;
        }
        tracked.clear();
        totalCaptured = 0;
        totalEvicted = 0;
    }

    /**
     * The residue a body leaves as it dies: its scorch, and if it was alight
     * flames at the strength it burned with that go on for its afterburn left
     * (at most {@link CombustionConstants#RESIDUE_FLAME_SECONDS}). Null for a
     * body that was neither burning nor scorched. Only the death transition
     * calls this, before the body leaves the world; the caller holds the
     * residue on whatever remains it chose.
     */
    public BurnResidue capture(Entity e) {
        BodyCombustion b = e.combustion;
        if (!b.burning && b.scorch <= 0f) {
            return null;
        }
        float flame = b.burning ? b.intensity() : 0f;
        float seconds = b.burning ? Math.clamp(b.fuel, 0f, RESIDUE_FLAME_SECONDS) : 0f;
        BurnResidue r = new BurnResidue(b.scorch, flame, seconds,
                e.pos.x, e.pos.y + e.height * 0.5f, e.pos.z);
        // Rain already on the body counts on: dying does not restart the soak.
        r.soak = b.burning ? b.soak : 0f;
        if (flame > 0f) {
            if (tracked.size() >= MAX_BURN_RESIDUES) {
                tracked.removeFirst().tracked = false;
                totalEvicted++;
            }
            r.tracked = true;
            tracked.add(r);
            totalCaptured++;
        }
        return r;
    }

    /**
     * Ages every residue by one frame: flames burn down, water or open rain at
     * the core of the remains puts them out, smoke thins; a residue that is
     * spent, or that nothing carries any more, is let go. A non-finite or
     * non-positive {@code dt} ages nothing; a stall ages at most {@link
     * CombustionConstants#RESIDUE_MAX_STEP}.
     */
    public void update(Game g, float dt) {
        if (tracked.isEmpty() || !(dt > 0) || !Float.isFinite(dt)) {
            return;
        }
        float step = Math.min(dt, RESIDUE_MAX_STEP);
        for (int i = tracked.size() - 1; i >= 0; i--) {
            BurnResidue r = tracked.get(i);
            if (r.holders <= 0) {
                release(i);
                continue;
            }
            r.age += step;
            if (r.age < r.flameSeconds) {
                weather(g, r, step);
            }
            if (r.age >= r.flameSeconds + RESIDUE_SMOKE_SECONDS) {
                release(i);
            }
        }
    }

    /** Water at the core puts the flames out at once; open rain soaks them out as it does a living body. */
    private static void weather(Game g, BurnResidue r, float dt) {
        int x = (int) Math.floor(r.x);
        int y = (int) Math.floor(r.y);
        int z = (int) Math.floor(r.z);
        if (g.world == null
                || g.world.getChunk(Math.floorDiv(x, Chunk.SX), Math.floorDiv(z, Chunk.SZ)) == null) {
            return;
        }
        if (g.world.getBlock(x, y, z) == BlockType.WATER) {
            r.douse();
            return;
        }
        if (g.fire.isPrecipitationReaching(g, x, y, z)) {
            r.soak += dt;
            if (r.soak + CombustionConstants.TIMER_EPSILON >= RAIN_EXTINGUISH_SECONDS) {
                r.douse();
            }
        } else {
            r.soak = Math.max(0f, r.soak - dt);
        }
    }

    private void release(int index) {
        tracked.remove(index).tracked = false;
    }

    /** Residues still flaming or smoking now. */
    public int trackedCount() {
        return tracked.size();
    }
}
