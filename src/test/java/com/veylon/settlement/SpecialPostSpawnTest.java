package com.veylon.settlement;

import com.veylon.Game;
import com.veylon.entity.CombustionSource;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 0.9.1 B03: the people a settlement keeps at a special post, a captive in its cage and a leader at
 * the top of the keep, are put down standing on the post's floor by the ordinary activation. A
 * captive spawned 0.4 above its cage floor had its head in the roof bars (the floor + 3) and could
 * not take a single step, so it could neither turn nor struggle when it burned.
 */
class SpecialPostSpawnTest {

    private static final long SEED = 424242L;
    private static final float DT = 1f / 20f;
    private static final int FAST_PER_MEDIUM = 10;

    @Test
    void aGeneratedCagesCaptiveStandsOnItsFloorClearOfTheRoofAndStrugglesWhenItBurns() {
        Game g = new Game();
        g.newWorld(SEED, true, World.GEN_CAVE_IDENTITIES, GameMode.CREATIVE); // nobody fights a Creative visitor
        Settlement fort = hostileSettlement(g, true, false);
        assertNotNull(fort, "a generated hostile settlement with a captive");
        Npc captive = activate(g, fort, NpcArchetype.CAPTIVE);
        Vec3i floor = fort.prisonPos;
        int roof = floor.y() + 2; // the cage: floor below prisonPos, bars at +0 and +1, roof at +2
        assertEquals(BlockType.CAGE_BARS, g.world.getBlock(floor.x(), roof, floor.z()), "the cage roof");

        assertFalse(captive.collidesAt(captive.pos.x, captive.pos.y, captive.pos.z),
                "the captive's body overlaps no block: feet " + captive.pos.y + ", floor " + floor.y());
        assertTrue(captive.pos.y + captive.height < roof, "its head is under the roof bars");
        assertEquals(floor.y(), captive.pos.y, 0.05f, "it stands on the cage floor");

        // The struggle the fire panic gives a burning captive, through the game's own ticks.
        float wound = captive.maxHealth - captive.health;
        captive.maxHealth = 1_000f;
        captive.health = 1_000f - wound;
        g.combustion.ignite(g, captive, CombustionSource.DIRECT_HIT, 1f, false, 1,
                captive.pos.x - 0.2f, captive.pos.y + 1f, captive.pos.z);
        float path = 0f;
        for (int tick = 1; tick <= 80; tick++) {
            float px = captive.pos.x, pz = captive.pos.z;
            g.fastTick(DT);
            if (tick % FAST_PER_MEDIUM == 0) {
                g.mediumTick(FAST_PER_MEDIUM * DT);
            }
            path += (float) Math.hypot(captive.pos.x - px, captive.pos.z - pz);
            assertTrue(captive.pos.x > floor.x() - 1e-3f && captive.pos.x < floor.x() + 2 + 1e-3f
                    && captive.pos.z > floor.z() - 1e-3f && captive.pos.z < floor.z() + 2 + 1e-3f,
                    "still inside the bars at tick " + tick);
        }
        assertTrue(captive.panic.active(), "the fire panics it");
        assertTrue(path > 0.3f, "it moves about its cage: " + path);
        assertFalse(fort.residents.get(captive.residentIndex).rescued, "panic freed nobody");
        assertTrue(g.settlementManager.canRescueCaptive(g, captive), "and the player still can");
    }

    @Test
    void aKeepsLeaderStandsOnTheTopFloorOfItsPost() {
        Game g = new Game();
        g.newWorld(SEED, true, World.GEN_CAVE_IDENTITIES, GameMode.CREATIVE);
        Settlement keep = hostileSettlement(g, false, true);
        assertNotNull(keep, "a generated hostile settlement with a keep");
        Npc leader = activate(g, keep, null);
        Vec3i post = keep.leaderPost;
        assertFalse(leader.collidesAt(leader.pos.x, leader.pos.y, leader.pos.z), "the leader overlaps no block");
        assertEquals(post.y(), leader.pos.y, 0.05f, "and stands on the post's floor");
        for (int tick = 0; tick < 40; tick++) {
            g.fastTick(DT);
        }
        assertEquals(post.y(), leader.pos.y, 0.05f, "where it stays: nothing pushes it out of a block");
    }

    /** Puts the player beside the settlement and lets the slow tick materialize its people. */
    private static Npc activate(Game g, Settlement s, NpcArchetype archetype) {
        g.world.layoutFor(s);
        Vec3i post = archetype == NpcArchetype.CAPTIVE ? s.prisonPos : s.leaderPost;
        g.world.ensureChunks(post.x(), post.z(), 4, 10_000);
        g.player.pos.set(post.x() - 3.5f, post.y() + 8f, post.z() + 0.5f);
        g.player.abilities.setFlying(true);
        g.settlementManager.slowTick(g, 1f);
        for (Settlement.Resident r : s.residents) {
            boolean wanted = archetype == null ? r.archetype.leader : r.archetype == archetype;
            if (wanted && r.live != null) {
                return r.live;
            }
        }
        return fail("nobody was materialized at the post of " + s.label());
    }

    private static Settlement hostileSettlement(Game g, boolean captive, boolean keep) {
        for (int rx = -10; rx <= 10; rx++) {
            for (int rz = -10; rz <= 10; rz++) {
                Settlement s = g.world.settlementForRegion(rx, rz);
                if (s == null || !s.hostile()) {
                    continue;
                }
                g.world.layoutFor(s);
                boolean hasCaptive = s.prisonPos != null && s.residents.stream()
                        .anyMatch(r -> r.archetype == NpcArchetype.CAPTIVE && r.alive && !r.rescued);
                boolean hasKeep = s.leaderPost != null && s.residents.stream()
                        .anyMatch(r -> r.archetype.leader && r.alive);
                if ((!captive || hasCaptive) && (!keep || hasKeep)) {
                    return s;
                }
            }
        }
        return null;
    }
}
