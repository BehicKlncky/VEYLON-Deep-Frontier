package com.veylon;

import com.veylon.ai.InfectedAI;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.settlement.NpcArchetype;
import com.veylon.util.Vec3i;
import com.veylon.world.urban.BuildingPlan;
import org.joml.Vector3f;

/** Scripted target poses/noise on unchanged generated geometry; the ordinary scheduler moves the pursuer. */
final class UrbanNavigationQaScene {
    private final Game game;
    private Npc subject;
    private Vec3i target, escape;
    private Vector3f evidence;
    private double nextNoise, escapedAt;
    private int phase, maxQueries, maxExpansions, maxHearing;

    UrbanNavigationQaScene(Game game) { this.game = game; }

    void stage() {
        if (game.world.urban == null || !game.world.urban.hasEncounters()
                || game.gameMode() != GameMode.SURVIVAL) {
            throw new IllegalStateException("navigation QA requires urban-dev Survival");
        }
        BuildingPlan plan = game.world.urban.regionPlan(0, 0).parcels().stream()
                .map(p -> game.world.urban.buildingAt(p.x0(), p.z0()))
                .filter(p -> p.basement() && p.storeys() >= 2).findFirst().orElseThrow();
        game.world.ensureChunks(plan.parcel().x0(), plan.parcel().z0(), 3, 10_000);
        target = plan.floors().stream().filter(f -> f.index() == 2).findFirst().orElseThrow()
                .rooms().stream().filter(r -> r.functions().contains(BuildingPlan.Function.BEDROOM))
                .findFirst().orElseThrow().anchor();
        escape = plan.floors().stream().filter(f -> f.index() == -1).findFirst().orElseThrow()
                .rooms().getFirst().anchor();
        Vec3i start = plan.approaches().getFirst();
        subject = game.entities.spawnNpc(game.world, "Navigation QA Veilbound",
                start.x() + .5f, start.y(), start.z() + .5f);
        if (subject == null) { throw new IllegalStateException("navigation QA admission failed"); }
        subject.archetype = NpcArchetype.VEILBOUND;
        subject.health = subject.maxHealth = subject.archetype.maxHealth;
        phase = 1; hold(target);
        System.out.println("[urban-navigation] stage building=" + plan.id() + " from=" + start + " target=" + target
                + " evidence=scripted-target-poses-and-positioned-noise; unchanged-generated-geometry; normal-AI-and-physics");
    }

    private void hold(Vec3i at) {
        game.player.pos.set(at.x() + .5f, at.y(), at.z() + .5f);
        game.player.vel.zero(); game.player.onGround = true;
        game.camera.yaw = (float) Math.toDegrees(Math.atan2(subject.pos.x - game.player.pos.x,
                -(subject.pos.z - game.player.pos.z)));
        game.camera.pitch = 10;
    }

    void update(double elapsed) {
        if (phase == 0 || phase == 3) { return; }
        maxQueries = Math.max(maxQueries, game.world.navigation.budget.queries());
        maxExpansions = Math.max(maxExpansions, game.world.navigation.budget.expansions());
        maxHearing = Math.max(maxHearing, game.noise.hearingSamples());
        if (subject.collidesAt(subject.pos.x, subject.pos.y + .01f, subject.pos.z) || subject.pos.y < 40) {
            throw new IllegalStateException("unsafe native navigation body=" + subject.pos);
        }
        if (phase == 1) {
            hold(target);
            if (elapsed >= nextNoise) {
                nextNoise = elapsed + 1;
                game.noise.emit(game, game.player.pos.x, game.player.pos.y, game.player.pos.z,
                        128, .9f, "gunshot", true, game.player);
            }
            if (subject.distSqTo(game.player) < 3 && Math.abs(subject.pos.y - target.y()) < .1f) {
                evidence = new Vector3f(subject.lastKnown);
                phase = 2; escapedAt = elapsed; game.noise.reset(); hold(escape);
                if (InfectedAI.seesPlayer(game, subject)) { throw new IllegalStateException("escape pose is visible"); }
                System.out.println("[urban-navigation] reachedTwoFloors=true elapsed=" + elapsed
                        + " body=" + subject.pos + " escape=scripted-basement-relocation evidence=" + evidence);
            }
        } else {
            hold(escape);
            if (elapsed > escapedAt + 20) {
                boolean pass = evidence.distanceSquared(subject.lastKnown) < .01f && subject.searchTimer == 0
                        && subject.path == null && subject.vel.x == 0 && subject.vel.z == 0;
                System.out.println("[urban-navigation] pass=" + pass + " lostSight=true expired=true body=" + subject.pos
                        + " peakQueries=" + maxQueries + " peakExpansions=" + maxExpansions
                        + " peakHearingSamples=" + maxHearing);
                if (!pass) { throw new IllegalStateException("native navigation evidence did not expire"); }
                phase = 3;
            }
        }
        if (elapsed > 85 && phase != 3) { throw new IllegalStateException("native navigation deadline phase=" + phase); }
    }
}
