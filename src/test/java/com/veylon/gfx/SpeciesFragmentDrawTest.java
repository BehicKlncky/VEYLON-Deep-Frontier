package com.veylon.gfx;

import com.sun.management.ThreadMXBean;
import com.veylon.Game;
import com.veylon.entity.BodyFamily;
import com.veylon.entity.BodyFragment;
import com.veylon.entity.BodyFragmentConstants;
import com.veylon.entity.BodySkeleton;
import com.veylon.entity.BodyPose;
import com.veylon.entity.Carcass;
import com.veylon.entity.Creature;
import com.veylon.entity.Creature.CreatureState;
import com.veylon.entity.Creature.CreatureType;
import com.veylon.entity.Entity;
import com.veylon.entity.FragmentAnatomy;
import com.veylon.entity.FragmentCut;
import com.veylon.entity.FragmentPiece;
import com.veylon.entity.FragmentPose;
import com.veylon.entity.Npc;
import com.veylon.entity.NpcAppearance;
import com.veylon.gfx.model.AnatomyModels;
import com.veylon.gfx.model.Animator;
import com.veylon.gfx.model.CreatureModels;
import com.veylon.gfx.model.EntityModel;
import com.veylon.gfx.model.FragmentModels;
import com.veylon.gfx.model.ModelPart;
import com.veylon.gfx.model.NpcModels;
import com.veylon.settlement.NpcArchetype;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.World;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every body blown apart is drawn as its own anatomy: each piece from its own
 * family's model, cut down to the parts that piece owns, in the pose the body
 * died in, where the simulation has it; wounds that tumble with their piece on
 * the faces of its box; a culling bound that holds antlers and horns; thin
 * pieces lying on the ground; and shared models that carry nothing from one
 * draw to the next.
 *
 * <p>Headless. "Drawn" is {@code ModelPart.render}'s own rule — start at a
 * part, stop at any hidden one, draw a straight split's whole box and skip its
 * tip — rebuilt here without the GL calls, from the frames
 * {@link FragmentModels} hands {@code Renderer.drawFragment}. Pieces come from
 * the production spawn path wherever a moment of a real death matters.
 */
class SpeciesFragmentDrawTest {

    /** Float rounding through a chain of part transforms at world coordinates in the hundreds. */
    private static final float TOLERANCE = 2e-4f;
    private static final float GROUND = 40f;
    private static final float X = 310.5f, Z = 310.5f;
    /** A scrap bomb's power. */
    private static final float BOMB = 2.6f;
    private static final float[] HEADINGS = {0f, 117f, -63.5f, 200f};
    private static final Quaternionf[] TUMBLES = {
            new Quaternionf(),
            new Quaternionf().rotationXYZ(0.3f, 1.1f, -0.7f),
            new Quaternionf().rotationAxis(2.0f, new Vector3f(1, 1, 0).normalize()),
            new Quaternionf().rotationXYZ((float) Math.PI, 0.2f, 0f),
            new Quaternionf().rotationZ((float) Math.PI * 0.5f),
    };

    /** A living creature: state, share of the species' speed, gait phase, clock. */
    private record CreatureScene(String label, CreatureState state, float run, float bobPhase, double time) {
    }

    private static final CreatureScene[] CREATURE_SCENES = {
            new CreatureScene("standing", CreatureState.WANDER, 0f, 0f, 0.0),
            new CreatureScene("mid-stride", CreatureState.FLEE, 1f, 0.49f, 1.3),
            new CreatureScene("attacking", CreatureState.ATTACK, 0.3f, 0.2f, 0.77),
            new CreatureScene("grazing", CreatureState.GRAZE, 0.1f, 0.3f, 5.1),
            new CreatureScene("wounded", CreatureState.FLEE_HURT, 1f, 1.7f, 0.41),
    };

    private record NpcScene(String label, Npc.NpcState state, float speed, float bobPhase, double time) {
    }

    private static final NpcScene[] NPC_SCENES = {
            new NpcScene("idle", Npc.NpcState.IDLE, 0f, 0f, 0.5),
            new NpcScene("running", Npc.NpcState.IDLE, 3.5f, 0.49f, 1.1),
            new NpcScene("attacking", Npc.NpcState.ATTACK, 1f, 0.3f, 0.3),
            new NpcScene("healing", Npc.NpcState.HEAL, 0f, 0f, 4.2),
    };

    private static final NpcAppearance[] LOOKS = {
            FragmentIsolationTest.look(NpcArchetype.GUARD, false, false),
            FragmentIsolationTest.look(NpcArchetype.SCAVENGER, true, false),
            FragmentIsolationTest.look(NpcArchetype.TRADER, false, false),
            FragmentIsolationTest.look(NpcArchetype.LEADER, false, false),
    };

    // ------------------------------------------------------------------
    // Which model, which parts
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(BodyFamily.class)
    void everyPieceIsDrawnFromItsOwnBodysModelAndOnlyItsOwnParts(BodyFamily family) {
        EntityModel own = family == BodyFamily.HUMANOID ? NpcModels.get() : CreatureModels.of(family.creature);
        Map<String, float[]> palette = palette(own.root);
        FragmentAnatomy anatomy = family.anatomy();
        Set<String> drawnOverall = new HashSet<>();
        for (FragmentPiece p : anatomy.pieces) {
            BodyFragment f = new BodyFragment(p, anatomy.restPose());
            ModelPart top = Animator.poseFragment(f);
            String where = p.toString();
            assertSame(own.root.find(p.rootPart), top, where + " is drawn from its own body's model");
            if (family != BodyFamily.HUMANOID) {
                assertFalse(contains(NpcModels.get().root, top), where + " is not drawn from the humanoid");
            }

            Set<String> expected = subtree(own.root.find(p.rootPart));
            for (String excluded : p.excludedParts) {
                expected.removeAll(subtree(own.root.find(excluded)));
            }
            Set<String> drawn = FragmentIsolationTest.drawn(top);
            if (family == BodyFamily.HUMANOID) {
                assertTrue(expected.containsAll(drawn), where + " draws only its own parts: " + drawn);
            } else {
                assertEquals(expected, drawn, where + ": every part it owns, antlers and horns too, and no other");
            }
            for (String name : drawn) {
                ModelPart part = own.part(name);
                if (part.drawsWholeBox()) {
                    for (ModelPart child : part.children) {
                        if (part.isSplitTip(child)) {
                            assertTrue(drawn.contains(child.name), where + ": " + name
                                    + " draws its whole box only when its tip is merged into the same piece");
                        }
                    }
                }
                if (family != BodyFamily.HUMANOID) {
                    float[] was = palette.get(name);
                    assertEquals(was[0], part.r, 0f, where + ": " + name + " keeps its species colour");
                    assertEquals(was[1], part.g, 0f, where + ": " + name + " keeps its species colour");
                    assertEquals(was[2], part.b, 0f, where + ": " + name + " keeps its species colour");
                    assertEquals(was[3], part.emissive, 0f, where + ": " + name + " keeps its glow");
                }
            }
            drawnOverall.addAll(drawn);
            assertEquals(p.cuts.size(), FragmentModels.cutCount(p), where + " draws exactly its own wounds");
        }
        Set<String> boxes = new HashSet<>();
        collectBoxed(own.root, boxes);
        assertTrue(drawnOverall.containsAll(family == BodyFamily.HUMANOID ? Set.of("torso", "head") : boxes),
                family + ": between them the pieces draw the whole body");
        own.resetPose();
    }

    @Test
    void aModelFromAnotherBodyIsRefused() {
        BodyFragment wing = new BodyFragment(FragmentAnatomy.of(CreatureType.BIRD).piece("wing0_l"),
                FragmentAnatomy.of(CreatureType.BIRD).restPose());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Animator.poseFragment(NpcModels.get(), wing));
        assertTrue(e.getMessage().contains("BIRD.wing0_l"), e.getMessage());
        assertNotSame(NpcModels.get(), AnatomyModels.modelOf(BodyFamily.BIRD));
    }

    // ------------------------------------------------------------------
    // Where: the moment of separation
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(BodyFamily.class)
    void piecesLeaveTheBodyExactlyWhereItWasLastDrawn(BodyFamily family) {
        Game game = arena(20260926L);
        EntityModel model = AnatomyModels.modelOf(family);
        int compared = 0;
        for (Object[] living : livingBodies(game.world, family)) {
            for (float heading : HEADINGS) {
                compared += separate(game, model, family, living, heading);
            }
        }
        assertTrue(compared > 0);
        model.resetPose();
    }

    /**
     * Draws a living body as the renderer draws it, blows it apart through the
     * production spawn path on the same clock, and holds every box each piece
     * draws in its first frame to the box the living body drew there.
     */
    private int separate(Game game, EntityModel model, BodyFamily family, Object[] living, float heading) {
        Entity body = (Entity) living[0];
        double time = (double) living[1];
        String scene = family + " " + living[2] + " at " + heading + "°";
        // Clear of the floor: a piece drawn inside a block would be pushed out as it spawns.
        body.pos.set(X, GROUND + 0.6f, Z);
        body.yaw = heading;
        game.fragments.reset();
        game.entities.carcasses.clear();

        // The living body's last frame.
        pose(model, living);
        Matrix4f world = new Matrix4f().translate(body.pos).rotateY((float) Math.toRadians(-body.yaw));
        Map<String, Matrix4f> whole = new HashMap<>();
        Map<String, Matrix4f> halves = new HashMap<>();
        draw(model.root, world, false, whole, new HashMap<>());
        draw(model.root, world, true, halves, new HashMap<>());

        // The death, on the clock the living body was drawn with.
        game.totalTime = time;
        float bx = body.pos.x - 1.2f, by = body.pos.y + body.height * 0.5f, bz = body.pos.z + 0.3f;
        List<BodyFragment> pieces = body instanceof Npc n
                ? game.fragments.spawnFromNpc(game, n, game.fragments.deathPose(n), bx, by, bz, BOMB)
                : game.fragments.spawnFromCreature(game, (Creature) body,
                        game.fragments.deathPose((Creature) body), bx, by, bz, BOMB);

        Set<String> covered = new HashSet<>();
        int compared = 0;
        for (BodyFragment f : pieces) {
            String where = scene + ", " + f.definition.name;
            assertEquals(0f, FragmentModels.contactDrop(f), 0f, where + " leaves at speed, so it is not lowered");
            ModelPart top = Animator.poseFragment(f);
            Map<String, Matrix4f> drawn = new LinkedHashMap<>();
            Map<String, Boolean> merged = new HashMap<>();
            draw(top, FragmentModels.rootFrame(f, new Matrix4f()), false, drawn, merged);
            for (Map.Entry<String, Matrix4f> box : drawn.entrySet()) {
                String name = box.getKey();
                boolean asWhole = merged.get(name);
                Matrix4f want = (asWhole ? whole : halves).get(name);
                assertNotNull(want, where + " draws " + name + ", which the living body did not");
                assertBox(want, box.getValue(), where + " box " + name + (asWhole ? " (whole)" : ""));
                assertTrue(covered.add(name), where + " draws " + name + " a second time");
                if (asWhole) {
                    for (ModelPart at = model.part(name); tipOf(at) != null; at = tipOf(at)) {
                        assertTrue(covered.add(tipOf(at).name), where + " draws " + tipOf(at).name + " twice");
                    }
                }
                compared++;
            }
        }
        assertEquals(halves.keySet(), covered, scene + ": every box the living body drew, and nothing else");
        return compared;
    }

    // ------------------------------------------------------------------
    // Wounds
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(BodyFamily.class)
    void woundsTumbleWithTheirPieceOnTheFacesOfItsBox(BodyFamily family) {
        FragmentAnatomy anatomy = family.anatomy();
        List<FragmentPose> poses = poses(family);
        Matrix4f piece = new Matrix4f();
        Matrix4f cube = new Matrix4f();
        Matrix4f toBox = new Matrix4f();
        Vector3f joint = new Vector3f();
        Vector3f lo = new Vector3f();
        Vector3f hi = new Vector3f();
        int wounds = 0;
        for (FragmentPose pose : poses) {
            for (FragmentPiece p : anatomy.pieces) {
                float[][] reference = null;
                for (int t = 0; t < TUMBLES.length; t++) {
                    BodyFragment f = new BodyFragment(p, pose).placeAt(1.3f, 2.1f, -0.7f, t * 0.9f);
                    f.orientation.premul(TUMBLES[t]);
                    f.vel.set(0, 8f, 0); // in flight: drawn at its simulated centre
                    float scale = pose.pieceScale(p.id);
                    FragmentModels.pieceFrame(f, piece);
                    // World to the collision box's own frame, in rest-pose model units.
                    toBox.translation(f.pos).rotate(f.orientation).scale(scale).invert();
                    float[][] local = new float[p.cuts.size()][];
                    for (int i = 0; i < p.cuts.size(); i++) {
                        FragmentCut cut = p.cuts.get(i);
                        FragmentModels.cutFrame(p, i, piece, cube);
                        cube.transformPosition(-0.5f, 0f, -0.5f, lo);
                        cube.transformPosition(0.5f, 1f, 0.5f, hi);
                        toBox.transformPosition(lo);
                        toBox.transformPosition(hi);
                        float[] min = {Math.min(lo.x, hi.x), Math.min(lo.y, hi.y), Math.min(lo.z, hi.z)};
                        float[] max = {Math.max(lo.x, hi.x), Math.max(lo.y, hi.y), Math.max(lo.z, hi.z)};
                        float[] half = {p.halfX, p.halfY, p.halfZ};
                        String where = p + " wound " + i + " at tumble " + t;
                        int a = cut.axis;
                        float inner = cut.side > 0 ? min[a] : max[a];
                        float outer = cut.side > 0 ? max[a] : min[a];
                        assertEquals(cut.side * half[a], inner, 1e-4f, where + " starts on its face of the box");
                        assertTrue(cut.side * (outer - inner) >= FragmentModels.CUT_PROUD - 1e-4f,
                                where + " stands proud of the face");
                        assertTrue(cut.side * (outer - inner) <= FragmentModels.CUT_PROUD
                                + FragmentModels.SHELL_REACH + 1e-4f, where + " is a thin face, not a cube");
                        for (int b = 0; b < 3; b++) {
                            if (b != a) {
                                assertTrue(min[b] >= -half[b] - 1e-4f && max[b] <= half[b] + 1e-4f,
                                        where + " stays on its face across axis " + b);
                            }
                        }
                        // The joint it was cut at, in the same frame.
                        f.jointToWorld(cut.joint, joint);
                        toBox.transformPosition(joint);
                        local[i] = new float[] {min[0], min[1], min[2], max[0], max[1], max[2],
                                joint.x, joint.y, joint.z};
                        wounds++;
                    }
                    if (reference == null) {
                        reference = local;
                        continue;
                    }
                    for (int i = 0; i < local.length; i++) {
                        for (int k = 0; k < 9; k++) {
                            assertEquals(reference[i][k], local[i][k], 1e-4f, p + " wound " + i
                                    + ": wound and joint turn together, whatever way the piece tumbles");
                        }
                    }
                }
            }
        }
        assertTrue(wounds > 0);
    }

    @Test
    void aLimbsStumpCoversTheJointItWasCutAtInEveryPose() {
        Matrix4f toModel = new Matrix4f();
        Vector3f joint = new Vector3f();
        Vector3f centre = new Vector3f();
        Vector3f size = new Vector3f();
        int checked = 0;
        for (BodyFamily family : BodyFamily.values()) {
            for (FragmentPose pose : poses(family)) {
                for (FragmentPiece p : family.anatomy().pieces) {
                    // A head cut at a box-less neck hangs off that connector
                    // rather than turning on the joint, so it is not centred on it.
                    if (!p.severed || !p.rootPart.equals(p.boxPart)) {
                        continue;
                    }
                    FragmentCut own = p.cuts.getFirst();
                    assertTrue(own.ownEnd, p + " lists its own end first");
                    BodyFragment f = new BodyFragment(p, pose).placeAt(1.3f, 2.1f, -0.7f, 0.4f);
                    f.orientation.premul(TUMBLES[2]);
                    f.vel.set(0, 8f, 0);
                    // The cut joint as the piece has it now, back in the table's rest-pose model space.
                    f.jointToWorld(p.rootJoint, joint);
                    FragmentModels.pieceFrame(f, toModel).invert().transformPosition(joint);
                    FragmentModels.cutCentre(p, 0, centre);
                    FragmentModels.cutSize(p, 0, size);
                    float[] j = {joint.x, joint.y, joint.z};
                    float[] c = {centre.x, centre.y, centre.z};
                    float[] s = {size.x, size.y, size.z};
                    for (int a = 0; a < 3; a++) {
                        if (a != own.axis) {
                            assertTrue(Math.abs(j[a] - c[a]) <= s[a] * 0.5f + 1e-4f,
                                    p + ": the stump covers its joint across axis " + a);
                        }
                    }
                    checked++;
                }
            }
        }
        assertTrue(checked > 100);
    }

    @Test
    void thinWingsAndConnectorsGetWoundsTheirOwnSizeNotAPersons() {
        FragmentAnatomy bird = FragmentAnatomy.of(CreatureType.BIRD);
        Vector3f size = new Vector3f();
        for (String name : List.of("wing0_l", "wing0_r", "wing1_l", "wing1_r")) {
            FragmentPiece wing = bird.piece(name);
            FragmentModels.cutSize(wing, 0, size);
            assertTrue(size.y < 2f * wing.halfY, name + ": the stump is thinner than the 2 cm wing board: " + size);
            assertTrue(size.z < 2f * wing.halfZ, name + ": and narrower than the wing: " + size);
            assertEquals(FragmentModels.CUT_PROUD, size.x, 1e-6f, name + ": a 1 cm face, not a wound cube");
        }
        FragmentPiece torso = bird.piece("torso");
        int onFlanks = 0;
        for (int i = 0; i < FragmentModels.cutCount(torso); i++) {
            FragmentCut cut = torso.cuts.get(i);
            if (bird.piece(cut.severed).name.startsWith("wing")) {
                FragmentModels.cutSize(torso, i, size);
                assertTrue(size.y <= 0.02f + 1e-6f, "a wing's socket on the body is the wing's thickness: " + size);
                onFlanks++;
            }
        }
        assertEquals(4, onFlanks, "four wing sockets on the bird's body");

        // The hare's tail stump stands proud of the haunch that covers the back of the body.
        FragmentAnatomy hare = FragmentAnatomy.of(CreatureType.HARE);
        FragmentPiece body = hare.piece("torso");
        int tail = hare.piece("tail").id;
        for (int i = 0; i < FragmentModels.cutCount(body); i++) {
            if (body.cuts.get(i).severed == tail) {
                Vector3f at = FragmentModels.cutCentre(body, i, new Vector3f());
                FragmentModels.cutSize(body, i, size);
                EntityModel model = CreatureModels.of(CreatureType.HARE);
                ModelPart haunch = model.part("haunch");
                float haunchBack = model.part("body").pivotZ + haunch.pivotZ + haunch.boxZ + haunch.sizeZ * 0.5f;
                assertTrue(haunchBack > body.centreZ + body.halfZ, "precondition: the haunch overhangs the back");
                assertEquals(haunchBack + FragmentModels.CUT_PROUD, at.z + size.z * 0.5f, 1e-5f,
                        "the hare's tail stump must clear the haunch, not vanish inside it");
            }
        }

        // A box-less neck leaves a head-sized stump no bigger than the head's own face.
        for (CreatureType type : CreatureType.values()) {
            FragmentAnatomy a = FragmentAnatomy.of(type);
            FragmentPiece head = a.piece("head");
            FragmentModels.cutSize(head, 0, size);
            float[] s = {size.x, size.y, size.z};
            float[] h = {head.halfX, head.halfY, head.halfZ};
            FragmentCut cut = head.cuts.getFirst();
            for (int ax = 0; ax < 3; ax++) {
                if (ax != cut.axis) {
                    assertTrue(s[ax] <= 2f * h[ax] * FragmentModels.CUT_INSET + 1e-5f,
                            type + " head stump fits its own face across axis " + ax);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Culling
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(BodyFamily.class)
    void theCullingBoundHoldsEverythingAPieceDrawsInEveryPoseAndTumble(BodyFamily family) {
        FragmentAnatomy anatomy = family.anatomy();
        List<FragmentPose> poses = poses(family);
        List<NpcAppearance> looks = family == BodyFamily.HUMANOID
                ? FragmentIsolationTest.everyLook() : List.of(new NpcAppearance());
        Vector3f corner = new Vector3f();
        Vector3f centre = new Vector3f();
        Matrix4f piece = new Matrix4f();
        Matrix4f cube = new Matrix4f();
        for (FragmentPose pose : poses) {
            for (NpcAppearance look : pose.isRest() ? looks : List.of(looks.getFirst())) {
                for (FragmentPiece p : anatomy.pieces) {
                    for (int t = 0; t < TUMBLES.length; t++) {
                        BodyFragment f = new BodyFragment(p, pose).placeAt(X, GROUND + 2f, Z, t * 1.3f);
                        f.appearance.copyFrom(look);
                        f.orientation.premul(TUMBLES[t]);
                        float radius = FragmentModels.radius(f);
                        centre.set(f.pos.x, f.pos.y - FragmentModels.contactDrop(f), f.pos.z);
                        String where = p + " of " + look.archetype + " at tumble " + t;
                        Map<String, Matrix4f> drawn = new HashMap<>();
                        draw(Animator.poseFragment(f), FragmentModels.rootFrame(f, new Matrix4f()), false,
                                drawn, new HashMap<>());
                        for (Map.Entry<String, Matrix4f> box : drawn.entrySet()) {
                            for (int c = 0; c < 8; c++) {
                                box.getValue().transformPosition((c & 1) - 0.5f, ((c >> 1) & 1) - 0.5f,
                                        ((c >> 2) & 1) - 0.5f, corner);
                                assertTrue(corner.distance(centre) <= radius + 1e-4f, where + ": " + box.getKey()
                                        + " reaches " + corner.distance(centre) + " past radius " + radius);
                            }
                        }
                        FragmentModels.pieceFrame(f, piece);
                        for (int i = 0; i < FragmentModels.cutCount(p); i++) {
                            FragmentModels.cutFrame(p, i, piece, cube);
                            for (int c = 0; c < 8; c++) {
                                cube.transformPosition((c & 1) - 0.5f, (c >> 1) & 1, ((c >> 2) & 1) - 0.5f, corner);
                                assertTrue(corner.distance(centre) <= radius + 1e-4f,
                                        where + ": wound " + i + " inside the bound");
                            }
                        }
                    }
                }
            }
        }
        AnatomyModels.modelOf(family).resetPose();
    }

    // ------------------------------------------------------------------
    // Ground contact and the one harvest record
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(CreatureType.class)
    void settledPiecesLieOnTheGroundAndTheHarvestRecordLiesUnderTheDrawnTorso(CreatureType type) {
        Game game = arena(7L + type.ordinal());
        Creature c = game.entities.spawnCreature(game.world, type, X, GROUND, Z);
        c.yaw = 35f;
        c.vel.zero();
        game.entities.creatures.remove(c);
        game.totalTime = 0.7;
        c.killBy(true);
        List<BodyFragment> pieces = game.fragments.spawnFromCreature(game, c, game.fragments.deathPose(c),
                c.pos.x - 1.2f, c.pos.y + type.height * 0.5f, c.pos.z, BOMB);
        for (int i = 0; i < 1500 && game.fragments.liveCount() > 0; i++) {
            game.fragments.update(game, 1f / 60f);
        }
        assertEquals(0, game.fragments.liveCount(), "precondition: every piece has settled");

        float lifted = 0f;
        int onFloor = 0;
        for (BodyFragment f : pieces) {
            if (Math.abs(f.pos.y - f.halfHeight - GROUND) > 1e-3f) {
                continue; // propped on an edge of another block, not lying on the floor
            }
            onFloor++;
            float bottom = lowestDrawnCorner(f);
            assertEquals(GROUND, bottom, 2e-3f, f.definition + " must lie on the floor, not hover or sink");
            lifted = Math.max(lifted, FragmentModels.contactDrop(f));
        }
        assertTrue(onFloor >= pieces.size() / 2, type + ": most pieces rest on the open floor");
        if (type == CreatureType.BIRD || type == CreatureType.HARE || type == CreatureType.STALKER) {
            assertTrue(lifted > 0.005f, type + " has pieces thinner than the sweep box: the drawing is lowered");
        }

        if (!type.leavesCarcass()) {
            assertTrue(game.entities.carcasses.isEmpty(), "a bird leaves no carcass");
            return;
        }
        assertEquals(1, game.entities.carcasses.size(), "one harvest record, never a second whole body");
        Carcass record = game.entities.carcasses.getFirst();
        assertTrue(record.fragmented(), "the record is the remains', which the renderer never draws whole");
        BodyFragment torso = record.remains;
        assertSame(pieces.getFirst(), torso, "the record is tied to the torso piece");
        assertEquals(torso.pos.x, record.pos.x, 1e-5f);
        assertEquals(torso.pos.z, record.pos.z, 1e-5f);
        assertEquals(lowestDrawnCorner(torso), record.pos.y, 2e-3f,
                "the harvest prompt points at the ground under the torso as it is drawn");
        assertSame(record, game.entities.nearestCarcass(record.pos.x, record.pos.y, record.pos.z, 2f),
                "and the remains can be harvested there");
    }

    /** The lowest corner of the collision box as drawn, in world y. */
    private static float lowestDrawnCorner(BodyFragment f) {
        Matrix4f frame = FragmentModels.pieceFrame(f, new Matrix4f());
        Vector3f corner = new Vector3f();
        float lowest = Float.POSITIVE_INFINITY;
        for (int c = 0; c < 8; c++) {
            frame.transformPosition(f.restCentreX + ((c & 1) == 0 ? -f.definition.halfX : f.definition.halfX),
                    f.restCentreY + (((c >> 1) & 1) == 0 ? -f.definition.halfY : f.definition.halfY),
                    f.restCentreZ + (((c >> 2) & 1) == 0 ? -f.definition.halfZ : f.definition.halfZ), corner);
            lowest = Math.min(lowest, corner.y);
        }
        return lowest;
    }

    // ------------------------------------------------------------------
    // Shared models
    // ------------------------------------------------------------------

    @Test
    void aLiveBodyDrawnAfterAPieceInheritsNothingFromIt() {
        Creature deer = new Creature(null, CreatureType.DEER);
        deer.state = CreatureState.WANDER;
        deer.vel.set(1.5f, 0, 0);
        deer.bobPhase = 0.3f;
        EntityModel deerModel = AnatomyModels.modelOf(BodyFamily.DEER);
        Animator.poseCreature(deerModel, deer, 1.7);
        Map<String, List<Float>> alone = state(deerModel.root);

        FragmentPose grazing = AnatomyModels.captureCreature(grazingDeer(), 3.3);
        FragmentAnatomy anatomy = FragmentAnatomy.of(CreatureType.DEER);
        for (String name : List.of("upper_leg_fl", "head", "torso", "tail", "lower_leg_br")) {
            Animator.poseFragment(new BodyFragment(anatomy.piece(name), grazing));
            assertFalse(state(deerModel.root).equals(alone), "precondition: the piece changed the shared model");
            Animator.poseCreature(deerModel, deer, 1.7);
            assertEquals(alone, state(deerModel.root),
                    "a live deer drawn after its " + name + " piece has every leg, pose and colour of its own");
            for (String leg : List.of("leg_fl", "leg_fr", "leg_bl", "leg_br")) {
                assertTrue(deerModel.part(leg).drawsWholeBox(), leg + " is one whole straight box again");
            }
        }

        Npc guard = new Npc(null, "Guard");
        guard.archetype = NpcArchetype.GUARD;
        guard.state = Npc.NpcState.IDLE;
        EntityModel human = AnatomyModels.modelOf(BodyFamily.HUMANOID);
        Animator.poseNpc(human, guard, 0.8);
        Map<String, List<Float>> guardAlone = state(human.root);

        Npc raider = new Npc(null, "Raider");
        raider.raider = true;
        raider.archetype = NpcArchetype.SCAVENGER;
        raider.state = Npc.NpcState.ATTACK;
        FragmentPose swing = AnatomyModels.captureNpc(raider, 0.3);
        BodyFragment raiderTorso = new BodyFragment(FragmentAnatomy.humanoid().piece("torso"), swing);
        raiderTorso.appearance.capture(raider);
        BodyFragment remains = new BodyFragment(FragmentAnatomy.humanoid().piece("upper_arm_r"),
                FragmentAnatomy.humanoid().restPose());
        remains.appearance.setNeutral();
        for (BodyFragment piece : List.of(raiderTorso, remains)) {
            Animator.poseFragment(piece);
            Animator.poseNpc(human, guard, 0.8);
            assertEquals(guardAlone, state(human.root), "a guard drawn after a " + piece.definition.name
                    + " piece wears no raider kit, has both arms and the guard's own colours");
        }

        // A corpse, drawn the renderer's way, after a trader's head.
        NpcAppearance trader = FragmentIsolationTest.look(NpcArchetype.TRADER, false, true);
        BodyPose flat = new BodyPose();
        Animator.poseBody(human, BodySkeleton.humanoid(), flat);
        Animator.applyAppearance(human, trader);
        Map<String, List<Float>> corpseAlone = state(human.root);
        BodyFragment head = new BodyFragment(FragmentAnatomy.humanoid().piece("head"), swing);
        head.appearance.copyFrom(FragmentIsolationTest.look(NpcArchetype.LEADER, false, false));
        Animator.poseFragment(head);
        Animator.poseBody(human, BodySkeleton.humanoid(), flat);
        Animator.applyAppearance(human, trader);
        assertEquals(corpseAlone, state(human.root), "a corpse after a leader's head wears only its own look");

        // One piece after another, of different bodies of one family.
        FragmentAnatomy wolf = FragmentAnatomy.of(CreatureType.WOLF);
        EntityModel wolfModel = AnatomyModels.modelOf(BodyFamily.WOLF);
        FragmentPose lunge = AnatomyModels.captureCreature(attacking(CreatureType.WOLF), 0.77);
        BodyFragment torso = new BodyFragment(wolf.piece("torso"), wolf.restPose());
        Animator.poseFragment(torso);
        Map<String, List<Float>> torsoAlone = state(wolfModel.root);
        Animator.poseFragment(new BodyFragment(wolf.piece("lower_leg_fl"), lunge));
        Map<String, List<Float>> deerBefore = state(deerModel.root);
        Animator.poseFragment(torso);
        assertEquals(torsoAlone, state(wolfModel.root), "a piece drawn after another shows none of the first");
        assertEquals(deerBefore, state(deerModel.root), "posing a wolf's pieces never touches the deer");
        deerModel.resetPose();
        human.resetPose();
        wolfModel.resetPose();
    }

    @Test
    void thePlayersRemainsWearThePlainLookWithoutACampBadge() {
        Game game = arena(99L);
        game.player.pos.set(X, GROUND, Z);
        game.player.killBy(false);
        assertTrue(game.player.recordBlastDeath(X + 1f, GROUND + 0.5f, Z, BOMB));
        List<BodyFragment> remains = game.fragments.spawnPlayerRemains(game, game.player);
        assertEquals(FragmentAnatomy.humanoid().pieces.size(), remains.size());

        EntityModel human = NpcModels.get();
        assertEquals(Set.of("torso", "vest"), FragmentIsolationTest.drawn(Animator.poseFragment(remains.getFirst())),
                "the player's torso: a plain vest, no camp badge, no kit");
        int vest = 0x6a5a44;
        assertEquals(((vest >> 16) & 0xFF) / 255f, human.part("vest").r, 1e-6f, "the gatherer's plain vest");
        assertEquals(((vest >> 8) & 0xFF) / 255f, human.part("vest").g, 1e-6f, "the gatherer's plain vest");
        assertEquals(Set.of("neck", "head"), FragmentIsolationTest.drawn(Animator.poseFragment(remains.get(1))));

        // A camp gatherer has the same vest and wears the camp's badge.
        BodyFragment gatherer = new BodyFragment(FragmentAnatomy.humanoid().piece("torso"),
                FragmentAnatomy.humanoid().restPose());
        gatherer.appearance.copyFrom(FragmentIsolationTest.look(null, false, false));
        gatherer.appearance.campIndex = 2;
        assertEquals(Set.of("torso", "vest", "friendlyBadge"),
                FragmentIsolationTest.drawn(Animator.poseFragment(gatherer)));
        human.resetPose();
    }

    // ------------------------------------------------------------------
    // Cost
    // ------------------------------------------------------------------

    @Test
    void preparingAFullFieldOfEveryFamilysPiecesAllocatesNothingPerFrame() {
        List<BodyFragment> field = new ArrayList<>();
        BodyFamily[] families = BodyFamily.values();
        Map<BodyFamily, FragmentPose> poses = new HashMap<>();
        for (BodyFamily family : families) {
            poses.put(family, capture(livingBodies(null, family).get(1)));
        }
        for (int i = 0; i < BodyFragmentConstants.MAX_LIVE_FRAGMENTS; i++) {
            FragmentAnatomy anatomy = families[i % families.length].anatomy();
            FragmentPiece p = anatomy.piece(i % anatomy.pieces.size());
            BodyFragment f = new BodyFragment(p, poses.get(anatomy.family)).placeAt(i * 0.7f, 40f, -i * 0.3f, i * 0.4f);
            f.appearance.copyFrom(LOOKS[i % LOOKS.length]);
            f.vel.set(0, (i % 3) * 0.4f, 0);
            f.settled = i % 2 == 0;
            field.add(f);
        }
        Matrix4f root = new Matrix4f();
        Matrix4f piece = new Matrix4f();
        Matrix4f cut = new Matrix4f();
        Vector3f tint = new Vector3f();
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        float sink = 0;
        for (int i = 0; i < 1_000; i++) {
            sink += frame(field, root, piece, cut, tint);
        }
        long before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 2_000; i++) {
            sink += frame(field, root, piece, cut, tint);
        }
        long bytes = (bean.getThreadAllocatedBytes(thread) - before) / 2_000;
        System.out.println("species fragment draw preparation allocation: " + bytes + " bytes/frame (" + sink + ")");
        assertTrue(bytes < 4_096, "preparing " + field.size() + " pieces allocated " + bytes + " bytes/frame");
        for (BodyFamily family : families) {
            AnatomyModels.modelOf(family).resetPose();
        }
    }

    /** Everything {@code Renderer.drawFragment} does per piece, minus the GL calls. */
    private static float frame(List<BodyFragment> field, Matrix4f root, Matrix4f piece, Matrix4f cut, Vector3f tint) {
        float sum = 0;
        for (int i = 0; i < field.size(); i++) {
            BodyFragment f = field.get(i);
            sum += FragmentModels.radius(f) + f.pos.y - FragmentModels.contactDrop(f);
            FragmentModels.tint(f, tint);
            ModelPart top = Animator.poseFragment(f);
            FragmentModels.rootFrame(f, root);
            sum += top.visible ? root.m31() : 0;
            FragmentModels.pieceFrame(f, piece);
            for (int c = 0; c < FragmentModels.cutCount(f.definition); c++) {
                FragmentModels.cutFrame(f.definition, c, piece, cut);
                sum += cut.m30() + tint.x;
            }
        }
        return sum;
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    /** {entity, clock, label} for the living poses of a family, in {@code world} (null: none). */
    private static List<Object[]> livingBodies(World world, BodyFamily family) {
        List<Object[]> out = new ArrayList<>();
        if (family == BodyFamily.HUMANOID) {
            for (NpcScene scene : NPC_SCENES) {
                for (NpcAppearance look : LOOKS) {
                    Npc n = new Npc(world, "Test");
                    n.archetype = look.archetype;
                    n.raider = look.raider;
                    n.isTrader = look.trader;
                    n.state = scene.state;
                    n.vel.set(scene.speed, 0, scene.speed * 0.3f);
                    n.bobPhase = scene.bobPhase;
                    out.add(new Object[] {n, scene.time, scene.label + " " + look.archetype});
                }
            }
        } else {
            for (CreatureScene scene : CREATURE_SCENES) {
                Creature c = new Creature(world, family.creature);
                c.state = scene.state;
                c.vel.set(scene.run * family.creature.speed, 0, 0);
                c.bobPhase = scene.bobPhase;
                out.add(new Object[] {c, scene.time, scene.label});
            }
        }
        return out;
    }

    /** The rest pose and every captured living pose of a family. */
    private static List<FragmentPose> poses(BodyFamily family) {
        List<FragmentPose> poses = new ArrayList<>();
        poses.add(family.anatomy().restPose());
        for (Object[] living : livingBodies(null, family)) {
            poses.add(capture(living));
        }
        return poses;
    }

    private static FragmentPose capture(Object[] living) {
        return living[0] instanceof Npc n ? AnatomyModels.captureNpc(n, (double) living[1])
                : AnatomyModels.captureCreature((Creature) living[0], (double) living[1]);
    }

    private static void pose(EntityModel model, Object[] living) {
        if (living[0] instanceof Npc n) {
            Animator.poseNpc(model, n, (double) living[1]);
        } else {
            Animator.poseCreature(model, (Creature) living[0], (double) living[1]);
        }
    }

    private static Creature grazingDeer() {
        Creature deer = new Creature(null, CreatureType.DEER);
        deer.state = CreatureState.GRAZE;
        deer.bobPhase = 0.6f;
        return deer;
    }

    private static Creature attacking(CreatureType type) {
        Creature c = new Creature(null, type);
        c.state = CreatureState.ATTACK;
        c.vel.set(1f, 0, 0);
        c.bobPhase = 0.2f;
        return c;
    }

    /** A flat stone arena far from the camp, nothing alive, pieces cleared. */
    private static Game arena(long seed) {
        Game game = new Game();
        game.newWorld(seed, true);
        for (int cx = 18; cx <= 21; cx++) {
            for (int cz = 18; cz <= 21; cz++) {
                Chunk c = game.world.getOrCreateChunk(cx, cz);
                for (int lx = 0; lx < Chunk.SX; lx++) {
                    for (int lz = 0; lz < Chunk.SZ; lz++) {
                        for (int y = 0; y < Chunk.SY; y++) {
                            c.set(lx, y, lz, y < GROUND ? BlockType.STONE : BlockType.AIR);
                        }
                    }
                }
                c.recomputeAllHeights();
                c.rebuildLights();
            }
        }
        game.player.pos.set(X, GROUND + 0.1f, Z);
        game.player.vel.zero();
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        game.entities.carcasses.clear();
        game.entities.corpses.clear();
        game.ragdolls.reset();
        game.fragments.reset();
        game.particles.density = 0f;
        return game;
    }

    /**
     * {@code ModelPart.render}'s traversal: the model matrix it submits for
     * every visible box — a straight split's whole box, its tip skipped —
     * and whether each was drawn whole. With {@code halves} every part draws
     * its own half instead, which is how the living geometry is compared with
     * a piece cut at the split.
     */
    private static void draw(ModelPart part, Matrix4f parent, boolean halves,
                             Map<String, Matrix4f> boxes, Map<String, Boolean> whole) {
        draw(part, parent, halves, false, boxes, whole);
    }

    private static void draw(ModelPart part, Matrix4f parent, boolean halves, boolean skipBox,
                             Map<String, Matrix4f> boxes, Map<String, Boolean> whole) {
        if (!part.visible) {
            return;
        }
        Matrix4f local = new Matrix4f(parent)
                .translate(part.pivotX + part.poseX, part.pivotY + part.poseY, part.pivotZ + part.poseZ)
                .rotateZ(part.rotZ).rotateY(part.rotY).rotateX(part.rotX);
        if (part.scale != 1f) {
            local.scale(part.scale);
        }
        boolean merged = !halves && part.drawsWholeBox();
        if (part.sizeX > 0 && !skipBox) {
            boxes.put(part.name, merged ? wholeBox(part, local)
                    : new Matrix4f(local).translate(part.boxX, part.boxY, part.boxZ)
                    .scale(part.sizeX, part.sizeY, part.sizeZ));
            whole.put(part.name, merged);
        }
        for (ModelPart child : part.children) {
            draw(child, local, halves, merged && part.isSplitTip(child), boxes, whole);
        }
    }

    /** The unsplit box a straight split draws: its own half and every straight tip below it. */
    private static Matrix4f wholeBox(ModelPart part, Matrix4f frame) {
        float[] min = {part.boxX - part.sizeX / 2, part.boxY - part.sizeY / 2, part.boxZ - part.sizeZ / 2};
        float[] max = {part.boxX + part.sizeX / 2, part.boxY + part.sizeY / 2, part.boxZ + part.sizeZ / 2};
        float ox = 0, oy = 0, oz = 0;
        for (ModelPart tip = tipOf(part); tip != null; tip = tipOf(tip)) {
            ox += tip.pivotX;
            oy += tip.pivotY;
            oz += tip.pivotZ;
            float[] c = {ox + tip.boxX, oy + tip.boxY, oz + tip.boxZ};
            float[] s = {tip.sizeX, tip.sizeY, tip.sizeZ};
            for (int a = 0; a < 3; a++) {
                min[a] = Math.min(min[a], c[a] - s[a] / 2);
                max[a] = Math.max(max[a], c[a] + s[a] / 2);
            }
        }
        return new Matrix4f(frame).translate((min[0] + max[0]) / 2, (min[1] + max[1]) / 2, (min[2] + max[2]) / 2)
                .scale(max[0] - min[0], max[1] - min[1], max[2] - min[2]);
    }

    private static ModelPart tipOf(ModelPart part) {
        for (ModelPart child : part.children) {
            if (part.isSplitTip(child)) {
                return child;
            }
        }
        return null;
    }

    private static void assertBox(Matrix4f want, Matrix4f got, String where) {
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        for (int c = 0; c < 8; c++) {
            float sx = (c & 1) - 0.5f, sy = ((c >> 1) & 1) - 0.5f, sz = ((c >> 2) & 1) - 0.5f;
            want.transformPosition(sx, sy, sz, a);
            got.transformPosition(sx, sy, sz, b);
            assertEquals(0f, a.distance(b), TOLERANCE, where + " corner " + c + ": " + a + " vs " + b);
        }
    }

    /** True when {@code part} is this very part or one below it. */
    private static boolean contains(ModelPart at, ModelPart part) {
        if (at == part) {
            return true;
        }
        for (ModelPart child : at.children) {
            if (contains(child, part)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> subtree(ModelPart part) {
        Set<String> out = new HashSet<>();
        out.add(part.name);
        for (ModelPart child : part.children) {
            out.addAll(subtree(child));
        }
        return out;
    }

    private static void collectBoxed(ModelPart part, Set<String> out) {
        if (part.sizeX > 0) {
            out.add(part.name);
        }
        for (ModelPart child : part.children) {
            collectBoxed(child, out);
        }
    }

    private static Map<String, float[]> palette(ModelPart part) {
        Map<String, float[]> out = new HashMap<>();
        out.put(part.name, new float[] {part.r, part.g, part.b, part.emissive});
        for (ModelPart child : part.children) {
            out.putAll(palette(child));
        }
        return out;
    }

    /** Everything a draw can leave on a part: visibility, split drawing, pose, colour, glow. */
    private static Map<String, List<Float>> state(ModelPart part) {
        Map<String, List<Float>> out = new HashMap<>();
        out.put(part.name, List.of(part.visible ? 1f : 0f, part.forceSplitDraw ? 1f : 0f,
                part.drawsWholeBox() ? 1f : 0f, part.rotX, part.rotY, part.rotZ, part.poseX, part.poseY,
                part.poseZ, part.scale, part.r, part.g, part.b, part.emissive));
        for (ModelPart child : part.children) {
            out.putAll(state(child));
        }
        return out;
    }
}
