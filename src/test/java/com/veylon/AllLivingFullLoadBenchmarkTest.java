package com.veylon;

import com.veylon.entity.BodyFamily;
import com.veylon.entity.BodyFragment;
import com.veylon.entity.Creature;
import com.veylon.entity.Entity;
import com.veylon.entity.Npc;
import com.veylon.entity.RagdollConstants;
import com.veylon.gfx.BodyFireLook;
import com.veylon.gfx.BodyFlames;
import com.veylon.gfx.model.BodyPosing;
import com.veylon.gfx.model.FlameAnchors;
import com.veylon.gfx.model.ModelPart;
import com.veylon.simulation.SimulationScheduler;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Random;
import java.util.function.ToLongFunction;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wall-clock cost of the all-living combat and fire work at the full load
 * {@code AllLivingEndToEndTest} builds (forty people and 35 animals burning
 * and fleeing, 220 block fires, 160 burning liquid cells, 120 pieces in the
 * air, a storm), split by the part that does it: the contact queries of the
 * combustion tick, the entity tick with every body panicking, the fragment
 * step, one emitter pass and one frame's flames. Rendering itself needs a GL
 * context; its CPU side (posing, anchor sampling and flame packing) is timed
 * here, the GPU side only in native QA.
 *
 * <p>The gates are the targets the implementation contract proposed for the
 * reference machine (section 16): the combustion tick within 0.2 ms with this
 * crowd and every fire cap full, the presentation within 0.5 ms a frame; the
 * fragment step within the 1 ms a fixed tick {@code RagdollAllocationTest}
 * allows a full field of bodies. The entity tick is reported, not gated: it
 * is most of the AI. Like the other performance suites, a figure from a
 * machine other than the reference one is evidence about that machine only.
 *
 * <p>Each figure is the fastest of {@link #SAMPLES} runs after
 * {@link #WARMUP} discarded ones, each run on a freshly built load so every
 * sample measures the load at its ceiling rather than a fire burning down.
 */
@Tag("performance")
class AllLivingFullLoadBenchmarkTest {

    private static final int WARMUP = 3;
    private static final int SAMPLES = 7;
    private static final float DT = SimulationScheduler.FAST_DT;
    private static final float FRAME = 1f / 60f;
    /** Fast ticks timed per run: one second. */
    private static final int TICKS = 20;
    /** Frames timed per run: one second. */
    private static final int FRAMES = 60;
    /** Emitter passes per second of frames. */
    private static final double PASSES_PER_FRAME = FRAME / 0.12;

    private static final double CONTACT_BUDGET_MS = 0.2;
    private static final double PRESENTATION_BUDGET_MS = 0.5;
    private static final double FRAGMENT_BUDGET_MS = 1.0;

    @Test
    void theWholeFeatureAtFullLoadStaysWithinTheContractsTargets() {
        double contact = perStep(TICKS, g -> {
            long start = System.nanoTime();
            for (int i = 0; i < TICKS; i++) {
                g.combustion.fastTick(g, DT);
            }
            return System.nanoTime() - start;
        });
        double entities = perStep(TICKS, g -> {
            long start = System.nanoTime();
            for (int i = 0; i < TICKS; i++) {
                g.entities.fastTick(g, DT);
            }
            return System.nanoTime() - start;
        });
        double fragments = perStep(FRAMES, g -> {
            long start = System.nanoTime();
            for (int i = 0; i < FRAMES; i++) {
                g.fragments.update(g, FRAME);
            }
            return System.nanoTime() - start;
        });
        Random presentation = new Random(3L);
        double emitterPass = perStep(8, g -> {
            long start = System.nanoTime();
            for (int i = 0; i < 8; i++) {
                g.ambience.bodyFire.update(0.12f, presentation);
            }
            return System.nanoTime() - start;
        });
        double flames = perStep(FRAMES, AllLivingFullLoadBenchmarkTest::buildFlames);
        double frame = perStep(FRAMES, g -> {
            long start = System.nanoTime();
            for (int i = 0; i < FRAMES; i++) {
                g.advanceWorld(FRAME);
            }
            return System.nanoTime() - start;
        });
        double presentationPerFrame = flames + emitterPass * PASSES_PER_FRAME;

        System.out.printf(Locale.ROOT, "benchmark all-living contact tick     %8.4f ms (budget %.2f)%n",
                contact, CONTACT_BUDGET_MS);
        System.out.printf(Locale.ROOT, "benchmark all-living entity tick      %8.4f ms (reported)%n", entities);
        System.out.printf(Locale.ROOT, "benchmark all-living fragment step    %8.4f ms (budget %.2f)%n",
                fragments, FRAGMENT_BUDGET_MS);
        System.out.printf(Locale.ROOT, "benchmark all-living emitter pass     %8.4f ms (reported)%n", emitterPass);
        System.out.printf(Locale.ROOT, "benchmark all-living flames per frame %8.4f ms (reported)%n", flames);
        System.out.printf(Locale.ROOT, "benchmark all-living presentation     %8.4f ms per frame (budget %.2f)%n",
                presentationPerFrame, PRESENTATION_BUDGET_MS);
        System.out.printf(Locale.ROOT, "benchmark all-living world frame      %8.4f ms (reported)%n", frame);

        assertTrue(contact <= CONTACT_BUDGET_MS, String.format(Locale.ROOT,
                "the combustion tick took %.4f ms at full load, over the contract's %.2f ms", contact,
                CONTACT_BUDGET_MS));
        assertTrue(fragments <= FRAGMENT_BUDGET_MS, String.format(Locale.ROOT,
                "the fragment step took %.4f ms with the live cap full, over %.2f ms", fragments,
                FRAGMENT_BUDGET_MS));
        assertTrue(presentationPerFrame <= PRESENTATION_BUDGET_MS, String.format(Locale.ROOT,
                "body-fire presentation took %.4f ms a frame, over the contract's %.2f ms",
                presentationPerFrame, PRESENTATION_BUDGET_MS));
    }

    /** One frame's flames on every burning body and piece, as {@code Renderer} builds them. */
    private static long buildFlames(Game g) {
        BodyFlames flames = new BodyFlames();
        BodyFireLook look = new BodyFireLook();
        Matrix4f frame = new Matrix4f();
        long total = 0;
        for (int f = 0; f < FRAMES; f++) {
            long start = System.nanoTime();
            float bodies = 0f;
            for (Npc n : g.entities.npcs) {
                bodies += g.combustion.isBurning(n) ? 1f : 0f;
            }
            for (Creature c : g.entities.creatures) {
                bodies += g.combustion.isBurning(c) ? 1f : 0f;
            }
            for (BodyFragment piece : g.fragments.live) {
                bodies += piece.burn != null && piece.burn.flame() > 0f ? piece.burnShare : 0f;
            }
            flames.begin(bodies, 1f, 1f);
            for (Npc n : g.entities.npcs) {
                draw(g, flames, look, n, BodyPosing.npc(n, f * FRAME, frame), BodyFamily.HUMANOID, frame);
            }
            for (Creature c : g.entities.creatures) {
                draw(g, flames, look, c, BodyPosing.creature(c, f * FRAME, frame), BodyFamily.of(c.type), frame);
            }
            for (BodyFragment piece : g.fragments.live) {
                if (piece.burn == null) {
                    continue;
                }
                ModelPart root = BodyPosing.fragment(piece, frame);
                flames.add(FlameAnchors.of(piece.definition.family), root, frame, look.remains(piece.burn),
                        piece.definition.id + 1, piece.burnShare, 12f, 0f, 0f);
            }
            total += System.nanoTime() - start;
        }
        return total;
    }

    private static void draw(Game g, BodyFlames flames, BodyFireLook look, Entity e, ModelPart root,
                             BodyFamily family, Matrix4f frame) {
        if (g.combustion.isBurning(e)) {
            flames.add(FlameAnchors.of(family), root, frame, look.living(e), 0, 1f, 12f, 0f, 0f);
        }
    }

    /** Milliseconds per step: the fastest of the samples, each on a fresh full load after one fast tick. */
    private static double perStep(int steps, ToLongFunction<Game> run) {
        long best = Long.MAX_VALUE;
        for (int i = 0; i < WARMUP + SAMPLES; i++) {
            Game g = AllLivingEndToEndTest.mixedLoad(1f);
            g.fastTick(DT);
            assertTrue(g.ragdolls.liveCount() <= RagdollConstants.MAX_LIVE);
            long nanos = run.applyAsLong(g);
            if (i >= WARMUP) {
                best = Math.min(best, nanos);
            }
        }
        return best / 1e6 / steps;
    }
}
