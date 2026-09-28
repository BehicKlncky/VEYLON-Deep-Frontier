package com.veylon.gfx;

import com.sun.management.ThreadMXBean;
import com.veylon.BodyFireArena;
import com.veylon.Game;
import com.veylon.entity.BodyFamily;
import com.veylon.entity.BodyFragment;
import com.veylon.entity.Creature;
import com.veylon.entity.Creature.CreatureType;
import com.veylon.entity.Npc;
import com.veylon.gfx.model.BodyPosing;
import com.veylon.gfx.model.FlameAnchors;
import com.veylon.gfx.model.ModelPart;
import com.veylon.settlement.NpcArchetype;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The flames drawn on a burning body stand on its alight anchors in the pose
 * it is drawn in, lean with the air past it, are as big as the body's faces,
 * thin out with distance and with a crowd — evenly, never below a few per
 * body — and never pass the frame's cap. A body in pieces draws no more fire
 * than it did whole. Building them allocates nothing.
 */
class BodyFlamesTest {

    private static final int F = com.veylon.gfx.ParticleRenderer.INSTANCE_FLOATS;

    @Test
    void everyTongueStandsOnAnAlightAnchorOfThePoseDrawn() {
        Game game = BodyFireArena.arena();
        Npc guard = burning(game, BodyFireArena.person(game, NpcArchetype.GUARD, 310.5f, 305.5f));
        BodyFireLook look = new BodyFireLook().living(guard);
        Matrix4f frame = new Matrix4f();
        ModelPart root = BodyPosing.npc(guard, 40.0, frame);
        float[] anchors = new float[FlameAnchors.MAX_ANCHORS * 4];
        int n = FlameAnchors.of(BodyFamily.HUMANOID).sample(root, frame, anchors);

        BodyFlames flames = new BodyFlames();
        flames.begin(1f, 1f, 1f);
        int added = flames.add(FlameAnchors.of(BodyFamily.HUMANOID), root, frame, look, 0, 1f, 10f, 0f, 0f);
        assertEquals(added, flames.count);
        assertEquals(2 * n + 1, added, "a tongue and a core on every anchor of a fully burning body, and its glow");
        for (int i = 0; i < flames.count; i++) {
            int o = i * F;
            if (flames.data[o + 8] == BodyFlames.SPRITE_GLOW) {
                assertEquals(flames.count - 1, i, "one glow, last");
                continue;
            }
            assertEquals(BodyFlames.SPRITE_FLAME, flames.data[o + 8], 0f);
            boolean onAnchor = false;
            for (int k = 0; k < n && !onAnchor; k++) {
                onAnchor = flames.data[o] == anchors[k * 4 + 1] && flames.data[o + 1] == anchors[k * 4 + 2]
                        && flames.data[o + 2] == anchors[k * 4 + 3];
            }
            assertTrue(onAnchor, "tongue " + i + " stands on an anchor of the drawn pose");
            assertTrue(flames.data[o + 7] > 0f && flames.data[o + 7] <= 1f);
        }
    }

    @Test
    void aBodyJustCaughtBurnsOnlyNearWhereTheFlameTouchedIt() {
        Game game = BodyFireArena.arena();
        Npc guard = BodyFireArena.person(game, NpcArchetype.GUARD, 310.5f, 305.5f);
        guard.maxHealth = guard.health = 500f;
        BodyFireArena.ignite(game, guard, 1);
        BodyFireArena.burn(game, 0.05f);
        BodyFireLook look = new BodyFireLook().living(guard);
        assertTrue(look.spreading);
        BodyFlames flames = new BodyFlames();
        flames.begin(1f, 1f, 1f);
        Matrix4f frame = new Matrix4f();
        ModelPart root = BodyPosing.npc(guard, 40.0, frame);
        flames.add(FlameAnchors.of(BodyFamily.HUMANOID), root, frame, look, 0, 1f, 10f, 0f, 0f);
        int tongues = 0;
        for (int i = 0; i < flames.count; i++) {
            if (flames.data[i * F + 8] != BodyFlames.SPRITE_FLAME) {
                continue;
            }
            tongues++;
            float dx = flames.data[i * F] - look.touchX, dy = flames.data[i * F + 1] - look.touchY;
            float dz = flames.data[i * F + 2] - look.touchZ;
            assertTrue(Math.sqrt(dx * dx + dy * dy + dz * dz) <= look.spread + 1e-4f, "tongue " + i
                    + " is within reach of the touch");
        }
        assertTrue(tongues > 0, "the flames have started where it was touched");
        BodyFireArena.burn(game, 1f);
        int later = drawnAt(guard, 10f);
        assertTrue(later > 2 * tongues, "and a second later cover the body: " + later + " vs " + tongues);
    }

    @Test
    void aBirdsFireIsSmallAndAThornhornsBroad() {
        Game game = BodyFireArena.arena();
        float bird = totalSize(game, burning(game, BodyFireArena.creature(game, CreatureType.BIRD, 305.5f, 305.5f)));
        float thornhorn = totalSize(game, burning(game, BodyFireArena.creature(game, CreatureType.THORNHORN, 315.5f, 305.5f)));
        assertTrue(thornhorn > 6f * bird, "a thornhorn burns with many times a bird's fire: " + thornhorn + " vs " + bird);
    }

    @Test
    void detailFallsWithDistanceAndStopsPastTheRange() {
        Game game = BodyFireArena.arena();
        Npc guard = burning(game, BodyFireArena.person(game, NpcArchetype.GUARD, 310.5f, 305.5f));
        int near = drawnAt(guard, 10f), mid = drawnAt(guard, 50f), far = drawnAt(guard, 80f);
        assertTrue(near > mid && mid > far && far > 0, "fewer with distance: " + near + ", " + mid + ", " + far);
        assertEquals(0, drawnAt(guard, BodyFlames.MAX_DISTANCE + 1f), "none past the range: it burns on regardless");
    }

    @Test
    void aCrowdSharesTheFlamesEvenlyAndNeverPassesTheCap() {
        Game game = BodyFireArena.arena();
        List<Npc> crowd = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            crowd.add(alight(game, BodyFireArena.person(game, NpcArchetype.GUARD, 292.5f + (i % 12) * 3f,
                    300.5f + (i / 12) * 3f)));
        }
        BodyFireArena.burn(game, 1.2f);
        BodyFlames flames = new BodyFlames();
        flames.begin(crowd.size(), 1f, 1f);
        Matrix4f frame = new Matrix4f();
        BodyFireLook look = new BodyFireLook();
        int least = Integer.MAX_VALUE, most = 0;
        for (Npc n : crowd) {
            ModelPart root = BodyPosing.npc(n, 40.0, frame);
            int added = flames.add(FlameAnchors.of(BodyFamily.HUMANOID), root, frame, look.living(n), 0, 1f, 12f, 0f, 0f);
            least = Math.min(least, added);
            most = Math.max(most, added);
            assertTrue(added <= flames.budgetPerBody(), "no body takes more than its share");
        }
        assertTrue(flames.count <= BodyFlames.MAX_INSTANCES);
        assertTrue(least >= BodyFlames.MIN_PER_BODY, "no burning body is left without flames: " + least);
        assertTrue(most - least <= 3, "the share is even, not first come first served: " + least + ".." + most);
        assertEquals(crowd.size(), flames.bodies);
    }

    /**
     * Pieces are drawn before the living in some frames and after in others;
     * either way each piece takes only its share of its body, so a field of
     * burning pieces cannot use up the frame's flames before a living body.
     */
    @Test
    void piecesTakeOnlyTheirBodysShareWhateverOrderTheyAreDrawnIn() {
        Game game = BodyFireArena.arena();
        List<Npc> living = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            living.add(alight(game, BodyFireArena.person(game, NpcArchetype.GUARD, 292.5f + (i % 10) * 3f,
                    290.5f + (i / 10) * 3f)));
        }
        List<Npc> doomed = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            doomed.add(alight(game, BodyFireArena.person(game, NpcArchetype.GUARD, 296.5f + (i % 10) * 4f,
                    330.5f + (i / 10) * 4f)));
        }
        BodyFireArena.burn(game, 1.2f);
        for (Npc n : doomed) {
            game.entities.npcs.remove(n);
            n.killBy(false);
            n.recordBlastDeath(n.pos.x, n.pos.y + 0.5f, n.pos.z + 1.5f, 2.6f);
            game.fragments.spawnFromNpc(game, n, game.fragments.deathPose(n), n.blastX, n.blastY, n.blastZ,
                    n.blastStrength);
        }
        float bodies = living.size();
        for (BodyFragment f : game.fragments.live) {
            bodies += f.burnShare;
        }
        BodyFlames flames = new BodyFlames();
        flames.begin(bodies, 1f, 1f);
        Matrix4f frame = new Matrix4f();
        BodyFireLook look = new BodyFireLook();
        for (BodyFragment f : game.fragments.live) {
            ModelPart root = BodyPosing.fragment(f, frame);
            flames.add(FlameAnchors.of(f.definition.family), root, frame, look.remains(f.burn),
                    f.definition.id + 1, f.burnShare, 10f, 0f, 0f);
        }
        int piecesDrew = flames.count;
        assertTrue(piecesDrew <= Math.round(doomed.size() * flames.budgetPerBody() * 1.5f),
                "twenty bodies in pieces draw about twenty bodies' flames: " + piecesDrew);
        for (Npc n : living) {
            ModelPart root = BodyPosing.npc(n, 40.0, frame);
            int added = flames.add(FlameAnchors.of(BodyFamily.HUMANOID), root, frame, look.living(n), 0, 1f, 10f, 0f, 0f);
            assertTrue(added >= BodyFlames.MIN_PER_BODY, "a living body drawn after them still burns: " + added);
        }
    }

    @Test
    void weakFlamesAreFewerAndSmallerThanFullOnes() {
        Game game = BodyFireArena.arena();
        Npc guard = burning(game, BodyFireArena.person(game, NpcArchetype.GUARD, 310.5f, 305.5f));
        BodyFireLook full = new BodyFireLook().living(guard);
        guard.maxHealth = guard.health = 500f;
        while (guard.combustion.intensity() > 0.4f) {
            BodyFireArena.burn(game, 0.05f);
        }
        BodyFireLook weak = new BodyFireLook().living(guard);
        float[] fullSizes = sizes(guard, full), weakSizes = sizes(guard, weak);
        assertTrue(weakSizes[0] < fullSizes[0] * 0.6f, "fewer tongues: " + weakSizes[0] + " vs " + fullSizes[0]);
        assertTrue(weakSizes[1] < fullSizes[1], "and smaller ones: " + weakSizes[1] + " vs " + fullSizes[1]);
    }

    @Test
    void theFlamesTrailARunningBody() {
        Game game = BodyFireArena.arena();
        Npc guard = burning(game, BodyFireArena.person(game, NpcArchetype.GUARD, 310.5f, 305.5f));
        BodyFlames flames = new BodyFlames();
        flames.begin(1f, 1f, 1f);
        Matrix4f frame = new Matrix4f();
        ModelPart root = BodyPosing.npc(guard, 40.0, frame);
        // Running at 4 m/s along +x in still air: the air goes past at -4.
        flames.add(FlameAnchors.of(BodyFamily.HUMANOID), root, frame, new BodyFireLook().living(guard), 0, 1f, 10f, -4f, 0f);
        for (int i = 0; i < flames.count - 1; i++) {
            assertTrue(flames.data[i * F + 10] < -0.3f, "tongue " + i + " leans back, away from where it runs");
            assertEquals(0f, flames.data[i * F + 12], 0f);
        }
    }

    @Test
    void nothingIsDrawnForABodyNotAlight() {
        Game game = BodyFireArena.arena();
        Npc guard = BodyFireArena.person(game, NpcArchetype.GUARD, 310.5f, 305.5f);
        assertEquals(0, drawnAt(guard, 5f));
    }

    @Test
    void aBodyInPiecesDrawsNoMoreFlamesThanItDidWhole() {
        Game game = BodyFireArena.arena();
        Creature deer = burning(game, BodyFireArena.creature(game, CreatureType.DEER, 310.5f, 305.5f));
        int whole = drawnAt(deer, 10f);
        deer.killBy(false);
        deer.recordBlastDeath(deer.pos.x, deer.pos.y + 0.5f, deer.pos.z + 1.5f, 2.6f);
        game.entities.creatures.remove(deer);
        game.fragments.spawnFromCreature(game, deer, game.fragments.deathPose(deer),
                deer.blastX, deer.blastY, deer.blastZ, deer.blastStrength);
        BodyFlames flames = new BodyFlames();
        flames.begin(1f, 1f, 1f);
        Matrix4f frame = new Matrix4f();
        BodyFireLook look = new BodyFireLook();
        int pieces = 0, burning = 0;
        for (BodyFragment f : game.fragments.live) {
            ModelPart root = BodyPosing.fragment(f, frame);
            int added = flames.add(FlameAnchors.of(f.definition.family), root, frame, look.remains(f.burn),
                    f.definition.id + 1, f.burnShare, 10f, 0f, 0f);
            pieces++;
            burning += added > 0 ? 1 : 0;
        }
        assertTrue(pieces > 5);
        assertTrue(burning >= 3, "the big pieces burn on: " + burning);
        int tongues = 0;
        for (int i = 0; i < flames.count; i++) {
            tongues += flames.data[i * F + 8] == BodyFlames.SPRITE_FLAME ? 1 : 0;
        }
        assertTrue(tongues <= whole, "the pieces together draw at most the whole body's flames: "
                + tongues + " vs " + whole);
    }

    @Test
    void theFlamesAtTheGripStayLowAndToTheSideOfTheView() {
        Game game = BodyFireArena.arena();
        BodyFireArena.ignite(game, game.player, 1);
        BodyFireArena.burn(game, 0.5f);
        BodyFireLook look = new BodyFireLook().living(game.player);
        // The held item's camera-space frame as the renderer builds it at rest.
        Matrix4f held = new Matrix4f().translate(0.26f, -0.50f, -0.96f).rotateZ(-0.18f).rotateY(-0.38f).rotateX(0.10f);
        float[] out = new float[8 * F];
        int n = BodyFlames.grip(held, look, out);
        assertTrue(n >= 2 && n <= 5, "a few small flames: " + n);
        for (int i = 0; i < n; i++) {
            assertTrue(out[i * F] > 0.1f, "right of the crosshair: " + out[i * F]);
            assertTrue(out[i * F + 1] < -0.3f, "low in the view: " + out[i * F + 1]);
            assertTrue(out[i * F + 3] < 0.1f, "small: " + out[i * F + 3]);
        }
        game.combustion.clear(game.player);
        assertEquals(0, BodyFlames.grip(held, new BodyFireLook().living(game.player), out), "none once out");
    }

    @Test
    void buildingAFrameOfFlamesAllocatesNothing() {
        Game game = BodyFireArena.arena();
        List<Npc> crowd = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            crowd.add(alight(game, BodyFireArena.person(game, NpcArchetype.GUARD, 292.5f + (i % 10) * 3f,
                    300.5f + (i / 10) * 3f)));
        }
        BodyFireArena.burn(game, 1.2f);
        BodyFlames flames = new BodyFlames();
        Matrix4f frame = new Matrix4f();
        BodyFireLook look = new BodyFireLook();
        FlameAnchors anchors = FlameAnchors.of(BodyFamily.HUMANOID);
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        long before = 0;
        for (int frameIndex = 0; frameIndex < 1_500; frameIndex++) {
            if (frameIndex == 500) {
                before = bean.getThreadAllocatedBytes(thread);
            }
            flames.begin(crowd.size(), 1f, 1f);
            for (int i = 0; i < crowd.size(); i++) {
                Npc n = crowd.get(i);
                ModelPart root = BodyPosing.npc(n, 40.0 + frameIndex * 0.016, frame);
                flames.add(anchors, root, frame, look.living(n), 0, 1f, 8f + i, -1f, 0.5f);
            }
        }
        long perFrame = (bean.getThreadAllocatedBytes(thread) - before) / 1_000;
        assertTrue(flames.count > 0);
        assertTrue(perFrame < 64, "a frame of 40 burning bodies allocated " + perFrame + " bytes");
    }

    // ------------------------------------------------------------------

    private static <T extends com.veylon.entity.Entity> T burning(Game game, T body) {
        alight(game, body);
        BodyFireArena.burn(game, 1.2f);
        return body;
    }

    /** Sets a body alight without burning anyone yet, for a crowd lit together. */
    private static <T extends com.veylon.entity.Entity> T alight(Game game, T body) {
        body.maxHealth = body.health = 500f;
        BodyFireArena.ignite(game, body, 1);
        return body;
    }

    private static int drawnAt(com.veylon.entity.Entity body, float distance) {
        BodyFlames flames = new BodyFlames();
        flames.begin(1f, 1f, 1f);
        Matrix4f frame = new Matrix4f();
        BodyFireLook look = new BodyFireLook().living(body);
        ModelPart root;
        FlameAnchors anchors;
        if (body instanceof Npc n) {
            root = BodyPosing.npc(n, 40.0, frame);
            anchors = FlameAnchors.of(BodyFamily.HUMANOID);
        } else {
            Creature c = (Creature) body;
            root = BodyPosing.creature(c, 40.0, frame);
            anchors = FlameAnchors.of(BodyFamily.of(c.type));
        }
        return flames.add(anchors, root, frame, look, 0, 1f, distance, 0f, 0f);
    }

    private static float totalSize(Game game, Creature c) {
        BodyFlames flames = new BodyFlames();
        flames.begin(1f, 1f, 1f);
        Matrix4f frame = new Matrix4f();
        ModelPart root = BodyPosing.creature(c, 40.0, frame);
        flames.add(FlameAnchors.of(BodyFamily.of(c.type)), root, frame, new BodyFireLook().living(c), 0, 1f, 10f, 0f, 0f);
        float total = 0f;
        for (int i = 0; i < flames.count; i++) {
            if (flames.data[i * F + 8] == BodyFlames.SPRITE_FLAME) {
                total += flames.data[i * F + 3] * flames.data[i * F + 3] * flames.data[i * F + 9];
            }
        }
        return total;
    }

    /** Tongues drawn and their mean width. */
    private static float[] sizes(Npc n, BodyFireLook look) {
        BodyFlames flames = new BodyFlames();
        flames.begin(1f, 1f, 1f);
        Matrix4f frame = new Matrix4f();
        ModelPart root = BodyPosing.npc(n, 40.0, frame);
        flames.add(FlameAnchors.of(BodyFamily.HUMANOID), root, frame, look, 0, 1f, 10f, 0f, 0f);
        float count = 0f, width = 0f;
        for (int i = 0; i < flames.count; i++) {
            if (flames.data[i * F + 8] == BodyFlames.SPRITE_FLAME) {
                count++;
                width += flames.data[i * F + 3];
            }
        }
        return new float[] {count, count > 0 ? width / count : 0f};
    }
}
