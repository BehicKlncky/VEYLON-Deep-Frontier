package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.save.SaveSystem;
import com.veylon.util.AppPaths;
import com.veylon.util.Vec3i;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.UrbanContainers;
import com.veylon.world.urban.UrbanIds;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Urban milestone 26: the default new-world path played natively from the title with ordinary
 * keys, over two launches as a player plays it ({@code VEYLON_FRONTEND=route}, then
 * {@code VEYLON_FRONTEND=route-load} with the same isolated data directory).
 *
 * <p>The first launch: the title; N opens NEW FRONTIER on the city, W shows the wilderness and W
 * returns, C chooses Creative when {@code VEYLON_GAME_MODE=creative}; Enter, the loading card and
 * the refuge; a ground-floor cupboard searched with the use button from an empty hand (the pose
 * beside it staged, as the tours stage theirs) and one stack taken through the screen's transfer
 * command; a few steps with W, the map with M, F5. In Survival the survivor then dies through the
 * player damage gate, the card says where they wake, Enter wakes them at the refuge with what they
 * carried, and F9 loads the save over the live world; in Creative a double-tapped Space flies first.
 * Then the pause menu with its graphics and audio screens, and Q quits. What F5 wrote
 * ({@link SaveAuditQaScene#durable(Game)}) is kept beside the slot.
 *
 * <p>The second launch: the title, L, the loading card, and the loaded world must hold exactly what
 * the first launch saved; then the pause menu and Q. Every screen is captured; a
 * {@code [frontend-route]} line reports each check. The route refuses to run without an isolated
 * data directory, since it writes the slot. Its screens draw only with a GL context, so it has no
 * headless twin; the headless default-path tests drive the same controller through its seams.
 */
final class FrontendRouteQa {
    static final String ROUTE = "route";
    static final String ROUTE_LOAD = "route-load";
    private static final double DEADLINE_SECONDS = 240;

    private interface Step {
        boolean tick(double t);
    }

    private final Game game;
    private final AutomatedRunDriver driver;
    private final boolean loadLaunch;
    private final GameMode mode;
    private final Path digest;
    private final ArrayDeque<String> labels = new ArrayDeque<>();
    private final ArrayDeque<Step> steps = new ArrayDeque<>();
    private final List<Integer> pressed = new ArrayList<>();
    private final List<Integer> clicked = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private Step current;
    private String label = "start";
    private double started = -1;
    private boolean reported;
    private List<String> saved = List.of();
    private String carried;
    private BuildingPlan.Fixture cupboard;

    /** Whether a {@code VEYLON_FRONTEND} screen names this route. */
    static boolean names(String screen) {
        return ROUTE.equals(screen) || ROUTE_LOAD.equals(screen);
    }

    FrontendRouteQa(Game game, AutomatedRunDriver driver, String screen, GameMode mode) {
        if (!AppPaths.isolatedFromPlayerData()) {
            throw new IllegalStateException("VEYLON_FRONTEND=" + screen + " saves into the slot: run with"
                    + " -Dveylon.dataDir=<dir> (Gradle: -PveylonDataDir=build/qa/<name>)");
        }
        this.game = game;
        this.driver = driver;
        this.loadLaunch = ROUTE_LOAD.equals(screen);
        this.mode = mode;
        this.digest = SaveSystem.SAVE_PATH.resolveSibling("frontend-route.digest");
        if (loadLaunch) {
            planLoadLaunch();
        } else {
            planNewLaunch();
        }
        System.out.println("[frontend-route] launch=" + (loadLaunch ? "load" : "new") + " mode="
                + mode.name().toLowerCase(Locale.ROOT) + " slot=" + SaveSystem.SAVE_PATH
                + " saveExisted=" + Files.exists(SaveSystem.SAVE_PATH));
    }

    private void planNewLaunch() {
        step("title", wait(1.5));
        step("title capture", capture("01-title"));
        step("N", press(GLFW_KEY_N));
        step("new frontier", await(() -> game.appState == Game.AppState.TITLE_NEW_WORLD, 3));
        step("opens on the city", check(() -> game.newFrontierScreen.showsCity()));
        step("settle", wait(0.5));
        step("city capture", capture("02-new-city"));
        step("W", press(GLFW_KEY_W));
        step("wilderness", await(() -> !game.newFrontierScreen.showsCity(), 1));
        step("wilderness capture", capture("03-new-wilderness"));
        step("W again", press(GLFW_KEY_W));
        step("back on the city", await(() -> game.newFrontierScreen.showsCity(), 1));
        if (mode == GameMode.CREATIVE) {
            step("C", press(GLFW_KEY_C));
            step("Creative chosen", await(() -> game.newFrontierScreen.selectedMode() == GameMode.CREATIVE, 1));
            step("creative capture", capture("04-new-creative"));
        }
        step("Enter", press(GLFW_KEY_ENTER));
        step("loading", await(() -> game.appState == Game.AppState.LOADING, 2));
        step("loading capture", capture("05-loading"));
        step("playing", await(() -> game.appState == Game.AppState.PLAYING, 90));
        step("the default world is the city", check(() -> game.world.generatorVersion == World.DEFAULT_NEW_WORLD_GENERATOR
                && game.world.isUrban() && game.gameMode() == mode && inRefuge()));
        step("meshes", wait(3.0));
        step("refuge capture", capture("06-refuge"));
        step("stand at a refuge cupboard", run(this::standAtCupboard));
        step("an empty hand", press(GLFW_KEY_9));
        step("aim settle", wait(0.5));
        step("RMB opens it", click(GLFW_MOUSE_BUTTON_RIGHT));
        step("its screen", await(() -> game.uiMode == Game.UiMode.CRATE, 2));
        step("the search", await(this::cupboardSearched, 8));
        step("search settle", wait(0.4));
        step("search capture", capture("07-search"));
        step("take a stack", check(this::takeAStack));
        step("Esc closes it", press(GLFW_KEY_ESCAPE));
        step("closed", await(() -> game.uiMode == Game.UiMode.NONE, 2));
        step("walk", hold(GLFW_KEY_W, 1.2));
        step("stand", wait(0.6));
        step("M", press(GLFW_KEY_M));
        step("map", await(() -> game.uiMode == Game.UiMode.MAP, 2));
        step("map settle", wait(0.5));
        step("map capture", capture("08-map"));
        step("Esc closes the map", press(GLFW_KEY_ESCAPE));
        step("map closed", await(() -> game.uiMode == Game.UiMode.NONE, 2));
        step("F5", press(GLFW_KEY_F5));
        step("saved", await(() -> logged("Game saved."), 3));
        step("remember the save", run(this::rememberSave));
        step("saved capture", capture("09-saved"));
        if (mode == GameMode.SURVIVAL) {
            step("a death through the damage gate", run(() -> {
                carried = SaveAuditQaScene.contents(game.player.inventory);
                game.player.hurt(1000f, false);
            }));
            step("death card", await(() -> game.appState == Game.AppState.DEATH, 3));
            step("card settle", wait(0.6));
            step("death capture", capture("10-death"));
            step("Enter wakes", press(GLFW_KEY_ENTER));
            step("awake", await(() -> game.appState == Game.AppState.PLAYING && !game.player.dead, 3));
            step("woke at the refuge with what was carried", check(() -> inRefuge()
                    && SaveAuditQaScene.contents(game.player.inventory).equals(carried)));
            step("wake settle", wait(1.2));
            step("wake capture", capture("11-wake"));
        } else {
            step("Space", press(GLFW_KEY_SPACE));
            step("between taps", wait(0.12));
            step("Space again", press(GLFW_KEY_SPACE));
            step("flying", await(() -> game.player.abilities.flying(), 2));
            step("rise", hold(GLFW_KEY_SPACE, 0.6));
            step("flight capture", capture("11-flying"));
        }
        step("F9 over the live world", press(GLFW_KEY_F9));
        step("loaded", await(() -> logged("Game loaded."), 5));
        step("F9 restores what F5 wrote", check(() -> SaveAuditQaScene.durable(game).equals(saved)));
        step("load settle", wait(1.5));
        step("load capture", capture("12-after-f9"));
        pauseScreens(13);
        step("report", run(this::report));
        step("Q quits", press(GLFW_KEY_Q));
    }

    private void planLoadLaunch() {
        step("read the first launch's save", run(() -> {
            try {
                saved = Files.readAllLines(digest, StandardCharsets.UTF_8);
            } catch (IOException missing) {
                failures.add("no digest from a first launch: " + digest);
            }
        }));
        step("title", wait(1.5));
        step("title capture", capture("01-title"));
        step("L", press(GLFW_KEY_L));
        step("loading", await(() -> game.appState == Game.AppState.LOADING, 2));
        step("loading capture", capture("02-loading"));
        step("playing", await(() -> game.appState == Game.AppState.PLAYING, 90));
        step("restores what the first launch saved", check(() -> !saved.isEmpty()
                && SaveAuditQaScene.durable(game).equals(saved)));
        step("meshes", wait(2.5));
        step("loaded capture", capture("03-loaded"));
        pauseScreens(4);
        step("report", run(this::report));
        step("Q quits", press(GLFW_KEY_Q));
    }

    /** Esc opens the pause menu; O and V its graphics and audio screens, Esc back from each. */
    private void pauseScreens(int first) {
        step("Esc", press(GLFW_KEY_ESCAPE));
        step("pause", await(() -> game.uiMode == Game.UiMode.PAUSE, 2));
        step("pause settle", wait(0.5));
        step("pause capture", capture(String.format(Locale.ROOT, "%02d-pause", first)));
        step("O", press(GLFW_KEY_O));
        step("graphics", await(() -> game.uiMode == Game.UiMode.OPTIONS, 2));
        step("graphics capture", capture(String.format(Locale.ROOT, "%02d-graphics", first + 1)));
        step("Esc from graphics", press(GLFW_KEY_ESCAPE));
        step("back to pause", await(() -> game.uiMode == Game.UiMode.PAUSE, 2));
        step("V", press(GLFW_KEY_V));
        step("audio", await(() -> game.uiMode == Game.UiMode.AUDIO_OPTIONS, 2));
        step("audio capture", capture(String.format(Locale.ROOT, "%02d-audio", first + 2)));
        step("Esc from audio", press(GLFW_KEY_ESCAPE));
        step("pause again", await(() -> game.uiMode == Game.UiMode.PAUSE, 2));
        step("settle before quitting", wait(0.6));
    }

    /** Runs once a frame, after input's end of frame, so a press lands on the next frame. */
    void advance(double elapsed) {
        for (int key : pressed) {
            game.input.onKey(key, GLFW_RELEASE);
        }
        pressed.clear();
        for (int button : clicked) {
            game.input.onMouseButton(button, GLFW_RELEASE);
        }
        clicked.clear();
        if (reported && steps.isEmpty() && current == null) {
            return;
        }
        if (elapsed > DEADLINE_SECONDS && !reported) {
            failures.add("deadline at " + label);
            report();
            game.window.requestClose();
            return;
        }
        if (current == null) {
            current = steps.poll();
            label = labels.poll();
            started = elapsed;
            if (current == null) {
                return;
            }
        }
        if (current.tick(elapsed - started)) {
            current = null;
        }
    }

    private void step(String name, Step step) {
        labels.add(name);
        steps.add(step);
    }

    private Step wait(double seconds) {
        return t -> t >= seconds;
    }

    private Step press(int key) {
        return t -> {
            game.input.onKey(key, GLFW_PRESS);
            pressed.add(key);
            return true;
        };
    }

    private Step hold(int key, double seconds) {
        boolean[] down = {false};
        return t -> {
            if (!down[0]) {
                game.input.onKey(key, GLFW_PRESS);
                down[0] = true;
            }
            if (t < seconds) {
                return false;
            }
            game.input.onKey(key, GLFW_RELEASE);
            return true;
        };
    }

    private Step await(BooleanSupplier condition, double timeout) {
        return t -> {
            if (condition.getAsBoolean()) {
                return true;
            }
            if (t > timeout) {
                failures.add("timed out: " + label);
                return true;
            }
            return false;
        };
    }

    private Step check(BooleanSupplier condition) {
        return t -> {
            if (!condition.getAsBoolean()) {
                failures.add("failed: " + label);
            }
            System.out.println("[frontend-route] check " + label + " = " + !failures.contains("failed: " + label));
            return true;
        };
    }

    private Step click(int button) {
        return t -> {
            game.input.onMouseButton(button, GLFW_PRESS);
            clicked.add(button);
            return true;
        };
    }

    /**
     * The staged pose of the route: the survivor set down where the refuge's nearest ground-floor
     * cupboard is reached and looking at it, as the tours stage the pose between their legs.
     */
    private void standAtCupboard() {
        BuildingPlan refuge = game.world.urban.buildingById(game.world.urban.refugeBuilding);
        var p = game.player.pos;
        cupboard = refuge.fixtures().stream().filter(f -> f.kind() == BuildingPlan.FixtureKind.CONTAINER)
                .filter(f -> UrbanIds.floorIndex(f.id()) == 0)
                .min(Comparator.comparingDouble(f -> f.target().distSq(p.x, p.y, p.z))).orElse(null);
        if (cupboard == null) {
            failures.add("no ground-floor container in the refuge");
            return;
        }
        Vec3i stand = cupboard.target();
        game.player.pos.set(stand.x() + .5f, stand.y() + .001f, stand.z() + .5f);
        game.player.vel.zero();
        float dx = cupboard.position().x() + .5f - game.player.pos.x;
        float dy = cupboard.position().y() + .5f - (game.player.pos.y + game.player.eyeHeight());
        float dz = cupboard.position().z() + .5f - game.player.pos.z;
        game.camera.yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
        game.camera.pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        System.out.println("[frontend-route] cupboard " + cupboard.position() + " from " + stand);
    }

    private boolean cupboardSearched() {
        if (cupboard == null) {
            return true;
        }
        UrbanContainers.Entry entry = game.world.urban.containers.byId(cupboard.id());
        return entry != null && (entry.state == UrbanContainers.State.SEARCHED || entry.state == UrbanContainers.State.EMPTY);
    }

    /** One stack out of the searched cupboard through the screen's own transfer command. */
    private boolean takeAStack() {
        Inventory held = cupboard == null ? null : game.world.crateContents.get(cupboard.position());
        if (held == null) {
            return false;
        }
        for (int slot = 0; slot < held.size(); slot++) {
            ItemStack stack = held.get(slot);
            if (stack != null) {
                int moved = game.transferCrateItemToPlayer(slot);
                System.out.println("[frontend-route] took " + moved + " " + stack.type + " from " + cupboard.position());
                return moved > 0;
            }
        }
        System.out.println("[frontend-route] the cupboard held nothing to take");
        return true;
    }

    private Step capture(String name) {
        return t -> {
            driver.requestCapture(name);
            return true;
        };
    }

    private Step run(Runnable action) {
        return t -> {
            action.run();
            return true;
        };
    }

    private boolean logged(String suffix) {
        return game.eventLog.recent(6).stream().anyMatch(line -> line.endsWith(suffix));
    }

    /** Whether the survivor stands in the starter refuge's parcel. */
    private boolean inRefuge() {
        var p = game.player.pos;
        ParcelPlan parcel = game.world.urban.parcelAt((int) Math.floor(p.x), (int) Math.floor(p.z));
        return parcel != null && parcel.use() == ParcelPlan.Use.REFUGE;
    }

    private void rememberSave() {
        saved = SaveAuditQaScene.durable(game);
        try {
            Files.write(digest, saved, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not keep the route's digest beside the slot", e);
        }
        for (String line : saved) {
            System.out.println("[frontend-route] saved " + line);
        }
    }

    private void report() {
        reported = true;
        if (game.world != null && loadLaunch) {
            for (String line : SaveAuditQaScene.durable(game)) {
                System.out.println("[frontend-route] loaded " + line);
            }
        }
        System.out.println("[frontend-route] launch=" + (loadLaunch ? "load" : "new") + " pass=" + failures.isEmpty()
                + " failures=" + failures + " generator=" + (game.world == null ? -1 : game.world.generatorVersion)
                + " mode=" + (game.world == null ? mode : game.gameMode()).name().toLowerCase(Locale.ROOT)
                + " evidence=keys(" + (loadLaunch ? "L,Esc,O,V,Q"
                : "N,W,C,Enter,9,RMB,Esc,W-held,M,Esc,F5,Enter|Space,F9,O,V,Q") + ")"
                + " glErrors=" + game.window.glErrorCount() + " khrErrors=" + game.window.glDebugErrorCount());
    }
}
