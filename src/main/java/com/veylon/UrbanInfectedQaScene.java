package com.veylon;

import com.veylon.ai.InfectedAI;
import com.veylon.combat.WeaponRegistry;
import com.veylon.entity.CombustionSource;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.save.SaveSystem;
import com.veylon.world.VoxelClearance;
import com.veylon.world.urban.EncounterAnchors;
import com.veylon.world.urban.UrbanIds;

/** Scripted observer/combat inputs on generated geometry; normal scheduler owns all outcomes. */
final class UrbanInfectedQaScene {
    private final Game game;
    private Npc subject;
    private EncounterAnchors.Anchor anchor;
    private int phase;
    private float x, y, z, playerHealth, beforeFire;
    private boolean windup, pain, panic, damage, passed;

    UrbanInfectedQaScene(Game game) { this.game = game; }

    void stage(boolean indoor) {
        if (game.world.urban == null || !game.world.urban.hasEncounters() || game.gameMode() != GameMode.SURVIVAL) {
            throw new IllegalStateException("infected QA requires urban-dev Survival");
        }
        for (var parcel : game.world.urban.regionPlan(0, 0).parcels()) {
            for (var a : EncounterAnchors.forParcel(game.world, parcel)) {
                if (a.indoor() != indoor) { continue; }
                game.world.ensureChunks(a.cell().x(), a.cell().z(), 3, 10_000);
                game.player.pos.set(a.cell().x() + 24.5f, a.cell().y(), a.cell().z() + .5f);
                game.camera.yaw = 90;
                Npc n = game.entities.encounters.tryActivate(game, a);
                if (n == null) { continue; }
                subject = n; anchor = a;
                if (standNear(3)) { break; }
                game.entities.removeNpc(game, n); subject = null;
            }
            if (subject != null) { break; }
        }
        if (subject == null) { throw new IllegalStateException("no supported generated infected QA lane"); }
        subject.yaw = (float) Math.toDegrees(Math.atan2(x - subject.pos.x, -(z - subject.pos.z)));
        game.player.inventory.set(game.player.hotbarSel, null); // instrumented unarmed pain hit, no supplied loot
        playerHealth = game.player.health; phase = 1; hold();
        System.out.println("[urban-infected] stage=" + (indoor ? "ground-floor-apartment" : "street")
                + " id=" + UrbanIds.format(anchor.id()) + " feet=" + anchor.cell()
                + " evidence=scripted-observer-and-combat-inputs; generated-anchor-and-geometry; normal-simulation");
    }

    private boolean standNear(float distance) {
        for (int direction = 0; direction < 16; direction++) {
            double angle = direction * Math.PI / 8;
            float tx = subject.pos.x + (float) Math.cos(angle) * distance;
            float tz = subject.pos.z + (float) Math.sin(angle) * distance;
            // Physics rests just below the slab boundary; the observer needs exact clearance.
            float ty = (float) Math.ceil(subject.pos.y - .01f);
            int bx = (int) Math.floor(tx), by = (int) Math.floor(ty), bz = (int) Math.floor(tz);
            if (!game.world.isColumnLoaded(bx, bz) || !game.world.getBlock(bx, by - 1, bz).solid
                    || VoxelClearance.collides(game.world, tx, ty, tz, .6f, 1.8f, true)
                    || !InfectedAI.clearSight(game.world, tx, ty + 1.4f, tz,
                    subject.pos.x, ty + 1.4f, subject.pos.z)) { continue; }
            if (anchor.indoor() && phase == 0) {
                var location = game.world.urbanLocation(bx, by, bz);
                if (location == null || location.room() == null
                        || UrbanIds.roomIndex(location.room().id()) != UrbanIds.roomIndex(anchor.id())) { continue; }
            }
            x = tx; y = ty; z = tz; return true;
        }
        return false;
    }

    private void hold() {
        game.player.pos.set(x, y, z); game.player.vel.zero(); game.player.onGround = true;
        game.camera.yaw = (float) Math.toDegrees(Math.atan2(subject.pos.x - x, -(subject.pos.z - z)));
        game.camera.pitch = 7;
    }

    void update(double elapsed) {
        if (phase == 0 || passed) { return; }
        hold();
        windup |= subject.strikeWindup > 0;
        damage |= game.player.health < playerHealth;
        pain |= subject.painTimer > 0;
        panic |= subject.panic.active();
        if (phase == 1 && elapsed >= 4 && damage && windup) {
            if (!standNear(1.2f)) { throw new IllegalStateException("lost supported pain-hit lane"); }
            hold();
            if (!game.performPlayerAttack(subject)) { throw new IllegalStateException("pain attack rejected"); }
            pain |= subject.painTimer > 0;
            phase = 2;
            System.out.println("[urban-infected] attack windup=" + windup + " survivalDamage="
                    + (playerHealth - game.player.health) + " pain=" + pain);
        }
        if (phase == 2 && elapsed >= 6) {
            beforeFire = subject.health;
            if (!game.combustion.ignite(game, subject, CombustionSource.DIRECT_HIT, .55f,
                    true, game.liquidFire.nextSpillId(), subject.pos.x, subject.pos.y + 1, subject.pos.z)) {
                throw new IllegalStateException("generated infected cannot burn");
            }
            phase = 3;
        }
        if (phase == 3 && elapsed >= 10 && panic && subject.health < beforeFire) {
            game.combustion.extinguish(subject);
            if (!standNear(1.2f)) { throw new IllegalStateException("lost supported projectile lane"); }
            hold();
            float dx = subject.pos.x - x, dz = subject.pos.z - z;
            float length = (float) Math.sqrt(dx * dx + dz * dz);
            game.projectiles.fire(game, game.player, true, x, y + 1.66f, z,
                    dx / length, 0, dz / length, WeaponRegistry.byId("relic_carbine"), null);
            phase = 4;
            System.out.println("[urban-infected] fire panic=" + panic + " damage=" + (beforeFire - subject.health)
                    + " finalInput=instrumented-head-projectile");
        }
        if (phase == 4 && elapsed >= 12 && subject.dead && !game.entities.npcs.contains(subject)) {
            if (!SaveSystem.save(game) || !SaveSystem.load(game)) { throw new IllegalStateException("infected QA save/load failed"); }
            passed = windup && damage && pain && panic && game.entities.encounters.resident(anchor.id()).dead()
                    && game.entities.encounters.tryActivate(game, anchor) == null
                    && game.entities.corpses.stream().anyMatch(c -> c.appearance.archetype == subject.archetype)
                    && game.entities.carcasses.isEmpty();
            System.out.println("[urban-infected] pass=" + passed + " savedDead=true respawned=false humanoidRemains="
                    + game.entities.corpses.size() + " active=" + game.entities.encounters.activeCount(game)
                    + " limitation=same-floor-pursuit; multi-floor-routing-pending-10");
            if (!passed) { throw new IllegalStateException("infected native acceptance failed"); }
        }
        if (elapsed >= 16 && !passed) { throw new IllegalStateException("infected native simulation deadline, phase=" + phase); }
    }

    boolean passed() { return passed; }
}
