package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanTraces;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Urban milestone 22: the marks survivors leave beside street doors stand on real wall cells of
 * the right buildings, say what they are in a line the HUD can show, are read by looking at them,
 * and exist only in cities with survivor sites.
 */
class UrbanTracesTest {
    private static final long SEED = 20260731L;
    private static final int[][] OUTWARD = {{0, 0}, {0, 0}, {0, -1}, {0, 1}, {-1, 0}, {1, 0}};

    @Test
    void eachInhabitedSiteHasItsMarkOnTheWallBesideItsStreetDoor() {
        Game game = city();
        int checked = 0;
        for (int rz = -1; rz <= 1; rz++) {
            for (int rx = -1; rx <= 1; rx++) {
                List<UrbanTraces.Trace> traces = new ArrayList<>();
                UrbanTraces.collect(game.world.urban, rx, rz, Integer.MIN_VALUE, Integer.MIN_VALUE,
                        Integer.MAX_VALUE, Integer.MAX_VALUE, traces);
                for (SurvivorSites.Site site : game.world.urban.sites(rx, rz).sites()) {
                    UrbanTraces.Trace t = traces.stream().filter(tr -> belongsTo(game, tr, site)).findFirst().orElse(null);
                    if (site.kind() == SurvivorSites.Kind.STRONGHOLD) {
                        assertNull(t, "a stronghold keeps its own sign");
                        continue;
                    }
                    assertNotNull(t, site.kind() + " " + site.id() + " has its mark");
                    assertEquals(switch (site.kind()) {
                        case REFUGE -> UrbanTraces.Kind.KNOCK_MARK;
                        case CAMP -> UrbanTraces.Kind.CAMP_ARROW;
                        default -> UrbanTraces.Kind.RAIDER_WARNING;
                    }, t.kind());
                    game.world.ensureChunks(t.x(), t.z(), 1, 2_000);
                    BlockType wall = game.world.getBlock(t.x(), t.y(), t.z());
                    assertTrue(wall == BlockType.BRICK || wall == BlockType.PLASTER || wall == BlockType.CONCRETE,
                            "the mark is painted on a wall: " + wall);
                    int[] out = OUTWARD[t.face()];
                    assertFalse(game.world.getBlock(t.x() + out[0], t.y(), t.z() + out[1]).opaque,
                            "it faces the open street");
                    assertEquals(t, UrbanTraces.at(game.world.urban, t.x(), t.y(), t.z(), t.face()));
                    assertNull(UrbanTraces.at(game.world.urban, t.x(), t.y(), t.z(), t.face() ^ 1),
                            "only the face toward the street carries it");
                    checked++;
                }
            }
        }
        assertTrue(checked >= 3, "camps, refuges and raider yards near the spawn have marks: " + checked);
    }

    @Test
    void everyMarkReadsWholeOnThePromptLine() {
        for (UrbanTraces.Kind kind : UrbanTraces.Kind.values()) {
            assertTrue(kind.text().length() <= 56, kind + " fits the one-line prompt: " + kind.text().length());
            assertFalse(kind.text().isBlank());
        }
    }

    @Test
    void lookingAtTheRefugesMarkReadsIt() {
        Game game = city();
        UrbanSurvivorTourPlan.Refuge refuge = UrbanSurvivorTourPlan.of(game.world).refuge();
        BuildingPlan building = game.world.urban.buildingAt(refuge.site().anchor().x(), refuge.site().anchor().z());
        UrbanTraces.Trace mark = null;
        for (int rz = -2; rz <= 2 && mark == null; rz++) {
            for (int rx = -2; rx <= 2 && mark == null; rx++) {
                List<UrbanTraces.Trace> traces = new ArrayList<>();
                UrbanTraces.collect(game.world.urban, rx, rz, building.parcel().x0() - 1, building.parcel().z0() - 1,
                        building.parcel().x1() + 1, building.parcel().z1() + 1, traces);
                if (!traces.isEmpty()) { mark = traces.getFirst(); }
            }
        }
        assertNotNull(mark, "the refuge's building carries its knock mark");
        int[] out = OUTWARD[mark.face()];
        Vec3i stand = new Vec3i(mark.x() + 2 * out[0], 41, mark.z() + 2 * out[1]);
        game.world.ensureChunks(stand.x(), stand.z(), 2, 2_000);
        game.appState = Game.AppState.PLAYING;
        for (int i = 0; i < 6; i++) {
            game.player.pos.set(stand.x() + .5f, stand.y() + .001f, stand.z() + .5f);
            game.player.vel.zero();
            float dx = mark.x() + .5f - game.player.pos.x, dz = mark.z() + .5f - game.player.pos.z;
            float dy = mark.y() + .5f - (game.player.pos.y + game.player.eyeHeight());
            game.camera.yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
            game.camera.pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
            game.playStep(0.05f, 0.05f);
            game.input.endFrame();
        }
        assertEquals(UrbanTraces.Kind.KNOCK_MARK.text(), game.interactPrompt);
    }

    @Test
    void citiesBeforeTheSurvivorSitesHaveNoMarks() {
        Game game = new Game();
        game.newWorld(SEED, true, World.GEN_URBAN_ECOLOGY, GameMode.SURVIVAL);
        List<UrbanTraces.Trace> traces = new ArrayList<>();
        UrbanTraces.collect(game.world.urban, 0, 0, Integer.MIN_VALUE, Integer.MIN_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, traces);
        assertTrue(traces.isEmpty());
        assertNull(UrbanTraces.at(game.world.urban, 0, UrbanTraces.EYE_Y, 0, 2));
    }

    private static boolean belongsTo(Game game, UrbanTraces.Trace t, SurvivorSites.Site site) {
        BuildingPlan b = game.world.urban.buildingAt(site.anchor().x(), site.anchor().z());
        var p = b.parcel();
        return t.x() >= p.x0() && t.x() <= p.x1() && t.z() >= p.z0() && t.z() <= p.z1();
    }

    private static Game city() {
        Game game = new Game();
        game.newWorld(SEED, true, World.GEN_URBAN_MISSIONS, GameMode.CREATIVE);
        return game;
    }
}
