package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.UrbanStarterSite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/** The advertised F close key, routed through the same frame step as native play. */
class ContainerCloseKeyRoutingTest {
    private static final float DT = 1f / 60f;

    @Test
    void fClosesAFacedContainerWithoutReopeningItInTheSameFrame() {
        Game g = new Game();
        g.newWorld(7, true, World.GEN_URBAN_STARTER, GameMode.SURVIVAL);
        g.appState = Game.AppState.PLAYING;
        HotkeyRouter router = new HotkeyRouter(g);
        BuildingPlan.Fixture cupboard = UrbanStarterSite.of(g.world.urban).refuge().fixtures().stream()
                .filter(f -> f.kind() == BuildingPlan.FixtureKind.CONTAINER && f.position().y() == 41)
                .findFirst().orElseThrow();
        g.player.pos.set(cupboard.target().x() + .5f, cupboard.target().y(), cupboard.target().z() + .5f);
        frame(g, router);
        float dx = cupboard.position().x() + .5f - g.camera.position.x;
        float dy = cupboard.position().y() + .5f - g.camera.position.y;
        float dz = cupboard.position().z() + .5f - g.camera.position.z;
        g.camera.yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
        g.camera.pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));

        press(g, router, GLFW_KEY_F);
        assertEquals(Game.UiMode.CRATE, g.uiMode, "F on the faced cupboard opens it");
        for (int i = 0; i < 300 && g.crateSearching(); i++) { frame(g, router); }
        assertFalse(g.crateSearching());

        press(g, router, GLFW_KEY_F);
        assertEquals(Game.UiMode.NONE, g.uiMode, "[F or Esc] close: F must close a faced container");
        for (int i = 0; i < 5; i++) { frame(g, router); }
        assertEquals(Game.UiMode.NONE, g.uiMode);
        press(g, router, GLFW_KEY_F);
        assertEquals(Game.UiMode.CRATE, g.uiMode, "a fresh press still opens it");
        assertFalse(g.crateSearching(), "already searched");
    }

    private static void press(Game g, HotkeyRouter router, int key) {
        g.input.onKey(key, GLFW_PRESS);
        frame(g, router);
        g.input.onKey(key, GLFW_RELEASE);
    }

    private static void frame(Game g, HotkeyRouter router) {
        router.update();
        g.playStep(DT, DT);
        g.input.endFrame();
    }
}
