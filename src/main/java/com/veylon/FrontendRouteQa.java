package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.save.LoadResult;
import com.veylon.save.SaveSlots;
import com.veylon.ui.SlotPickerScreen;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Urban milestone 26: the default new-world path played natively from the title with ordinary
 * keys, over two launches as a player plays it ({@code VEYLON_FRONTEND=route}, then
 * {@code VEYLON_FRONTEND=route-load} with the same isolated data directory).
 *
 * <p>The first launch needs an empty slot 1: the title; N opens NEW FRONTIER on the city, W shows
 * the wilderness and W returns, C chooses Creative when {@code VEYLON_GAME_MODE=creative}; Enter
 * opens the slot picker on the first empty slot and Enter starts the world in it (0.9.1 milestone
 * 04); the loading card and the refuge; a ground-floor cupboard searched with the use button from an
 * empty hand (the pose beside it staged, as the tours stage theirs) and one stack taken through the
 * screen's transfer command; a few steps with W, the map with M, F5. In Survival the survivor then
 * dies through the player damage gate, the card says where they wake, Enter wakes them at the
 * refuge with what they carried, and F9 loads the slot over the live world; in Creative a
 * double-tapped Space flies first. Then the pause menu, which names the slot, with its graphics and
 * audio screens, and Q quits. What F5 wrote ({@link SaveAuditQaScene#durable(Game)}) is kept beside
 * the slots.
 *
 * <p>The second launch: the title, L, the slot picker on the slot saved last, Enter, the loading
 * card, and the loaded world must hold exactly what the first launch saved; then the pause menu and
 * Q. Every screen is captured; a {@code [frontend-route]} line reports each check. The routes refuse
 * to run without an isolated data directory, since they write slots. Their screens draw only with a
 * GL context, so they have no headless twin; the headless default-path tests drive the same
 * controller through its seams.
 *
 * <p>{@code VEYLON_FRONTEND=route-refusal} (0.9.1 milestones 03 and 04) plays slot 2 holding a save
 * of another version. When slot 2 is empty and VEYLON 0.9.0's {@code saves/veylon.sav} is there, as
 * the 0.9.0 build's first launch leaves it, the route copies it into slot 2 first, reading the old
 * file only. Then the SHA-256 of both; L, the picker with slot 2 as another version and the old
 * file's note; 2 and Enter, and the reason under the cards; Esc, then N, Enter and Enter into a new
 * city in slot 1; both hashes again, which must be unchanged; then the pause menu and Q. It never
 * presses F5. {@code SaveRefusalRoutingTest} drives the same paths headlessly.
 */
final class FrontendRouteQa {
    static final String ROUTE = "route";
    static final String ROUTE_LOAD = "route-load";
    static final String ROUTE_REFUSAL = "route-refusal";
    private static final double DEADLINE_SECONDS = 240;

    private interface Step {
        boolean tick(double t);
    }

    private final Game game;
    private final AutomatedRunDriver driver;
    private final String launch;
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
    private String slotBefore = "";
    private String slotAfter = "";
    private String legacyBefore = "";
    private String legacyAfter = "";

    /** Whether a {@code VEYLON_FRONTEND} screen names this route. */
    static boolean names(String screen) {
        return ROUTE.equals(screen) || ROUTE_LOAD.equals(screen) || ROUTE_REFUSAL.equals(screen);
    }

    FrontendRouteQa(Game game, AutomatedRunDriver driver, String screen, GameMode mode) {
        if (!AppPaths.isolatedFromPlayerData()) {
            throw new IllegalStateException("VEYLON_FRONTEND=" + screen + " saves into the slots: run with"
                    + " -Dveylon.dataDir=<dir> (Gradle: -PveylonDataDir=build/qa/<name>)");
        }
        this.game = game;
        this.driver = driver;
        this.launch = switch (screen) {
            case ROUTE_REFUSAL -> "refusal";
            case ROUTE_LOAD -> "load";
            default -> "new";
        };
        this.mode = mode;
        this.digest = slots().directory().resolve("frontend-route.digest");
        switch (launch) {
            case "refusal" -> planRefusalLaunch();
            case "load" -> planLoadLaunch();
            default -> planNewLaunch();
        }
        System.out.println("[frontend-route] launch=" + launch + " mode=" + mode.name().toLowerCase(Locale.ROOT)
                + " slots=" + slots().directory() + " states=" + states());
    }

    private SaveSlots slots() {
        return game.saves.slots();
    }

    /** Every slot's state, in order, as the picker would show it. */
    private List<SaveSlots.State> states() {
        return slots().describeAll().stream().map(SaveSlots.Slot::state).toList();
    }

    /** What the open picker shows for {@code slot}. */
    private SaveSlots.State shown(int slot) {
        return game.frontend.slotPicker.slots().get(slot - 1).state();
    }

    private boolean picking(SlotPickerScreen.Purpose purpose) {
        return game.appState == Game.AppState.TITLE_SLOTS && game.frontend.slotPicker.purpose() == purpose;
    }

    /**
     * Slot 2 holding another version's save: refused from the picker with the reason under the cards,
     * the file never changed, VEYLON 0.9.0's own file neither. Nothing here writes slot 2.
     */
    private void planRefusalLaunch() {
        String older = new LoadResult(LoadResult.Outcome.OLDER_VERSION, "").message();
        Path slot2 = slots().current(2), legacy = slots().legacySave();
        step("0.9.0's save into slot 2", run(() -> copyLegacyInto(2)));
        step("hash slot 2 and 0.9.0's file", run(() -> {
            slotBefore = hash(slot2);
            legacyBefore = hash(legacy);
        }));
        step("a save of another version is in slot 2", check(() -> !slotBefore.isEmpty()));
        step("slot 1 is empty", check(() -> slots().describe(1).state() == SaveSlots.State.EMPTY));
        step("title", wait(1.5));
        step("title capture", capture("01-title"));
        step("L", press(GLFW_KEY_L));
        step("the slot picker", await(() -> picking(SlotPickerScreen.Purpose.LOAD), 3));
        step("2", press(GLFW_KEY_2));
        step("slot 2 is another version", check(() -> shown(2) == SaveSlots.State.OTHER_VERSION
                && game.frontend.slotPicker.selected() == 2));
        step("picker settle", wait(0.6));
        step("picker capture", capture("02-slots"));
        step("Enter on slot 2", press(GLFW_KEY_ENTER));
        step("the picker says why", await(() -> older.equals(game.frontend.slotPicker.notice()), 2));
        step("nothing was loaded", check(() -> game.world == null && game.appState == Game.AppState.TITLE_SLOTS));
        step("notice settle", wait(0.6));
        step("refused capture", capture("03-slot-refused"));
        step("Esc", press(GLFW_KEY_ESCAPE));
        step("back at the title", await(() -> game.appState == Game.AppState.TITLE, 2));
        step("N", press(GLFW_KEY_N));
        step("new frontier", await(() -> game.appState == Game.AppState.TITLE_NEW_WORLD, 3));
        step("Enter", press(GLFW_KEY_ENTER));
        step("the slot picker for the new world", await(() -> picking(SlotPickerScreen.Purpose.NEW), 3));
        step("it offers the empty slot 1", check(() -> game.frontend.slotPicker.selected() == 1));
        step("Enter again", press(GLFW_KEY_ENTER));
        step("loading", await(() -> game.appState == Game.AppState.LOADING, 2));
        step("playing", await(() -> game.appState == Game.AppState.PLAYING, 90));
        step("a new city in slot 1", check(() -> game.world.isUrban() && game.world.changedBlocks.isEmpty()
                && game.saves.activeSlot() == 1 && slots().describe(1).state() == SaveSlots.State.READY));
        step("meshes", wait(3.0));
        step("city capture", capture("04-new-city"));
        step("hash both again", run(() -> {
            slotAfter = hash(slot2);
            legacyAfter = hash(legacy);
        }));
        step("slot 2 and 0.9.0's file are untouched", check(() -> slotBefore.equals(slotAfter)
                && legacyBefore.equals(legacyAfter)));
        pauseScreens(5);
        step("report", run(this::report));
        step("Q quits", press(GLFW_KEY_Q));
    }

    /** Copies VEYLON 0.9.0's file into an empty slot, reading the old file only. */
    private void copyLegacyInto(int slot) {
        try {
            if (!Files.exists(slots().current(slot)) && slots().holdsLegacySave()) {
                Files.copy(slots().legacySave(), slots().current(slot));
                System.out.println("[frontend-route] copied " + slots().legacySave().getFileName() + " into slot " + slot);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not stage slot " + slot, e);
        }
    }

    /** A file's SHA-256, or empty when there is none. */
    private static String hash(Path file) {
        try {
            if (!Files.isRegularFile(file)) {
                return "";
            }
            byte[] sha = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
            return HexFormat.of().formatHex(sha);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("could not hash " + file, e);
        }
    }

    private void planNewLaunch() {
        step("slot 1 is empty", check(() -> slots().describe(1).state() == SaveSlots.State.EMPTY));
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
        step("the slot picker", await(() -> picking(SlotPickerScreen.Purpose.NEW), 3));
        step("on the empty slot 1", check(() -> game.frontend.slotPicker.selected() == 1));
        step("picker settle", wait(0.5));
        step("slot capture", capture("05-slots"));
        step("Enter starts in slot 1", press(GLFW_KEY_ENTER));
        step("loading", await(() -> game.appState == Game.AppState.LOADING, 2));
        step("loading capture", capture("06-loading"));
        step("playing", await(() -> game.appState == Game.AppState.PLAYING, 90));
        step("the default world is the city", check(() -> game.world.generatorVersion == World.DEFAULT_NEW_WORLD_GENERATOR
                && game.world.isUrban() && game.gameMode() == mode && inRefuge()));
        step("saved into slot 1 at once", check(() -> game.saves.activeSlot() == 1
                && slots().describe(1).state() == SaveSlots.State.READY));
        step("meshes", wait(3.0));
        step("refuge capture", capture("07-refuge"));
        step("stand at a refuge cupboard", run(this::standAtCupboard));
        step("an empty hand", press(GLFW_KEY_9));
        step("aim settle", wait(0.5));
        step("RMB opens it", click(GLFW_MOUSE_BUTTON_RIGHT));
        step("its screen", await(() -> game.uiMode == Game.UiMode.CRATE, 2));
        step("the search", await(this::cupboardSearched, 8));
        step("search settle", wait(0.4));
        step("search capture", capture("08-search"));
        step("take a stack", check(this::takeAStack));
        step("Esc closes it", press(GLFW_KEY_ESCAPE));
        step("closed", await(() -> game.uiMode == Game.UiMode.NONE, 2));
        step("walk", hold(GLFW_KEY_W, 1.2));
        step("stand", wait(0.6));
        step("M", press(GLFW_KEY_M));
        step("map", await(() -> game.uiMode == Game.UiMode.MAP, 2));
        step("map settle", wait(0.5));
        step("map capture", capture("09-map"));
        step("Esc closes the map", press(GLFW_KEY_ESCAPE));
        step("map closed", await(() -> game.uiMode == Game.UiMode.NONE, 2));
        step("F5", press(GLFW_KEY_F5));
        step("saved", await(() -> logged(SaveSession.savedLine(1)), 3));
        step("remember the save", run(this::rememberSave));
        step("saved capture", capture("10-saved"));
        if (mode == GameMode.SURVIVAL) {
            step("a death through the damage gate", run(() -> {
                carried = SaveAuditQaScene.contents(game.player.inventory);
                game.player.hurt(1000f, false);
            }));
            step("death card", await(() -> game.appState == Game.AppState.DEATH, 3));
            step("card settle", wait(0.6));
            step("death capture", capture("11-death"));
            step("Enter wakes", press(GLFW_KEY_ENTER));
            step("awake", await(() -> game.appState == Game.AppState.PLAYING && !game.player.dead, 3));
            step("woke at the refuge with what was carried", check(() -> inRefuge()
                    && SaveAuditQaScene.contents(game.player.inventory).equals(carried)));
            step("wake settle", wait(1.2));
            step("wake capture", capture("12-wake"));
        } else {
            step("Space", press(GLFW_KEY_SPACE));
            step("between taps", wait(0.12));
            step("Space again", press(GLFW_KEY_SPACE));
            step("flying", await(() -> game.player.abilities.flying(), 2));
            step("rise", hold(GLFW_KEY_SPACE, 0.6));
            step("flight capture", capture("12-flying"));
        }
        step("F9 over the live world", press(GLFW_KEY_F9));
        step("loaded", await(() -> logged(SaveSession.loadedLine(1)), 5));
        step("F9 restores what F5 wrote", check(() -> SaveAuditQaScene.durable(game).equals(saved)));
        step("load settle", wait(1.5));
        step("load capture", capture("13-after-f9"));
        pauseScreens(14);
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
        step("the slot picker", await(() -> picking(SlotPickerScreen.Purpose.LOAD), 3));
        step("on the slot saved last", check(() -> game.frontend.slotPicker.selected() == 1
                && shown(1) == SaveSlots.State.READY));
        step("picker settle", wait(0.5));
        step("slot capture", capture("02-slots"));
        step("Enter loads it", press(GLFW_KEY_ENTER));
        step("loading", await(() -> game.appState == Game.AppState.LOADING, 2));
        step("loading capture", capture("03-loading"));
        step("playing", await(() -> game.appState == Game.AppState.PLAYING, 90));
        step("restores what the first launch saved", check(() -> !saved.isEmpty()
                && SaveAuditQaScene.durable(game).equals(saved) && game.saves.activeSlot() == 1));
        step("meshes", wait(2.5));
        step("loaded capture", capture("04-loaded"));
        pauseScreens(5);
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
        if (game.world != null && "load".equals(launch)) {
            for (String line : SaveAuditQaScene.durable(game)) {
                System.out.println("[frontend-route] loaded " + line);
            }
        }
        if ("refusal".equals(launch)) {
            System.out.println("[frontend-route] slot 2 sha256 before=" + slotBefore + " after=" + slotAfter
                    + " veylon.sav sha256 before=" + legacyBefore + " after=" + legacyAfter);
        }
        String keys = switch (launch) {
            case "refusal" -> "L,2,Enter,Esc,N,Enter,Enter,Esc,O,V,Q";
            case "load" -> "L,Enter,Esc,O,V,Q";
            default -> "N,W,C,Enter,Enter,9,RMB,Esc,W-held,M,Esc,F5,Enter|Space,F9,O,V,Q";
        };
        System.out.println("[frontend-route] launch=" + launch + " pass=" + failures.isEmpty()
                + " failures=" + failures + " generator=" + (game.world == null ? -1 : game.world.generatorVersion)
                + " mode=" + (game.world == null ? mode : game.gameMode()).name().toLowerCase(Locale.ROOT)
                + " slot=" + game.saves.activeSlot() + " evidence=keys(" + keys + ")"
                + " glErrors=" + game.window.glErrorCount() + " khrErrors=" + game.window.glDebugErrorCount());
    }
}
