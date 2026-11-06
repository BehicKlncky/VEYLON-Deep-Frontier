package com.veylon;

import com.veylon.engine.AudioManager;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.settlement.NpcArchetype;
import com.veylon.util.Vec3i;
import com.veylon.world.World;
import com.veylon.world.urban.UrbanStarterSite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Melee against the infected in the economy city (urban milestone 23, D-97's finding): a Veilbound
 * flinches from a blow, and in generator 20 then shrugs off pain for a moment, so a rain of blows no
 * longer locks it in pain. A weak tool lets its strike land; the reinforced spear, the first upgrade,
 * still puts a lone body down before it strikes. Generator 19 keeps the old flinch on every blow. Every
 * blow goes through the production melee command, its reach and cooldown, and every strike through the
 * player's damage gate.
 */
class UrbanEconomyCombatTest {
    private static final long SEED = 20_260_731L;
    private static final float DT = 0.05f;

    /** Damage the survivor takes holding ground against one Veilbound, swinging whenever the cooldown allows. */
    private record Bout(float damageTaken, float seconds, boolean killed, int blows) { }

    private static Bout fight(int generator, ItemType weapon) {
        Game g = SurvivorWorldArena.session(SEED, GameMode.SURVIVAL, generator, new AudioManager());
        Vec3i cell = UrbanStarterSite.of(g.world.urban).collector().offset(3, 0, 0);
        SurvivorWorldArena.standAt(g, cell);
        g.time.totalMinutes = Math.floor(g.time.totalMinutes / 1440) * 1440 + 12 * 60; // noon: it sees the survivor
        g.player.inventory.set(0, new ItemStack(weapon, 1));
        g.player.hotbarSel = 0;
        Npc n = g.entities.spawnNpc(g.world, "Veilbound", g.player.pos.x, g.player.pos.y, g.player.pos.z - 1.2f);
        n.archetype = NpcArchetype.VEILBOUND;
        n.health = n.maxHealth = n.archetype.maxHealth;
        n.yaw = 180;
        float hp = g.player.health;
        int blows = 0;
        float t = 0;
        for (; t < 6f && !n.dead; t += DT) {
            g.entities.fastTick(g, DT);
            g.combat.advancePlayerAttackCooldown(DT);
            if (g.performPlayerAttack(n)) { blows++; }
            // The survivor holds ground and keeps the body within reach, as a player pressing in does.
            if (n.distSqTo(g.player.pos.x, g.player.pos.y, g.player.pos.z) > 1.4f * 1.4f) {
                n.pos.set(g.player.pos.x, g.player.pos.y, g.player.pos.z - 1.2f);
                n.vel.zero();
            }
        }
        return new Bout(hp - g.player.health, t, n.dead, blows);
    }

    @Test
    void aRainOfBlowsNoLongerLocksAVeilboundInPainButTheSpearStillWins() {
        Bout lockedTool = fight(World.GEN_URBAN_MISSIONS, ItemType.IRON_PICKAXE);
        Bout guardedTool = fight(World.GEN_URBAN_ECONOMY, ItemType.IRON_PICKAXE);
        Bout guardedSpear = fight(World.GEN_URBAN_ECONOMY, ItemType.IRON_SPEAR);
        System.out.println("[economy-combat] pry tool, generator 19: " + lockedTool);
        System.out.println("[economy-combat] pry tool, generator 20: " + guardedTool);
        System.out.println("[economy-combat] reinforced spear, generator 20: " + guardedSpear);
        assertTrue(lockedTool.killed() && guardedTool.killed() && guardedSpear.killed(), "every bout ends with the body down");
        assertEquals(0f, lockedTool.damageTaken(), 1e-3f, "generator 19 keeps the flinch on every blow: no strike lands");
        assertTrue(guardedTool.damageTaken() > 0f, "generator 20: a strike wound up under the blows lands");
        assertEquals(0f, guardedSpear.damageTaken(), 1e-3f, "the reinforced spear still puts a lone body down first");
        assertTrue(guardedSpear.blows() < guardedTool.blows(), "the first upgrade is worth having");
    }
}
