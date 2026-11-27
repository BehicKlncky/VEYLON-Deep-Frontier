package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.input.ControlSettings;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.save.LoadResult;
import com.veylon.save.SaveHeader;
import com.veylon.save.SaveSlots;
import com.veylon.save.SaveSummary;
import com.veylon.simulation.TimeConstants;
import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.ui.SlotPickerScreen;
import com.veylon.util.AppPaths;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.WorldSeed;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.UrbanContainers;
import com.veylon.world.urban.UrbanIds;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

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
 *
 * <p>{@code VEYLON_FRONTEND=route-slots} (0.9.1 milestone 04) plays every state of the picker in
 * one launch from an empty isolated directory: a new city in slot 1 saved with F5 and the pause
 * menu naming it; a Creative wilderness in slot 2 saved with F5; then, staged between title
 * visits, slot 1's save damaged on the disk and slot 3 given a save of another version (0.9.0's
 * file when it is there, else slot 2's save under a later epoch). LOAD FRONTIER then shows a damaged
 * slot with its backup, a ready slot and another version; Enter on slot 3 says why it does not
 * open; R and Y restore slot 1 from its backup, which must load the world its first save held and
 * set the damaged save aside unchanged. Back at the title, NEW FRONTIER's picker asks before
 * replacing slot 2 and before deleting it, and both are cancelled with N. Every state and both
 * confirmations are captured. {@code SlotPickerRoutingTest} drives the same paths headlessly.
 *
 * <p>{@code VEYLON_FRONTEND=route-session} (0.9.1 milestone 06) needs an empty isolated directory
 * and plays the session: NEW FRONTIER showing the seed it offers, the seed typed as words with E
 * and rolled again with R, then typed once more and a city of it started in slot 1; the pause menu
 * naming the city and its seed; C and the controls set to 170% and invert-Y, applied with F5,
 * which must write the settings file; R and the way back to the title, cancelled with Esc, then
 * taken with Enter, which must save slot 1 first; L and the slot again; R and N, which must leave
 * slot 1 byte for byte; and NEW FRONTIER once more with nothing typed carried over.
 * {@code route-session-relaunch} then must find those mouse settings read at launch and shows them
 * on the title's controls screen. {@code NewFrontierSeedTest}, {@code ReturnToTitleRoutingTest}
 * and {@code ControlsOptionsRoutingTest} drive the same paths headlessly.
 *
 * <p>{@code VEYLON_FRONTEND=route-release} (0.9.1 milestone 09) plays the release's acceptance
 * scenario in the session's world kind ({@code VEYLON_GENERATOR}) and mode ({@code VEYLON_GAME_MODE}).
 * It needs {@code VEYLON_AUTOSAVE=on} and an isolated directory whose slots are empty and which holds
 * VEYLON 0.9.0's {@code saves/veylon.sav}. A world of a typed seed starts in slot 1; in a city a refuge
 * cupboard is searched; at a staged night the survivor sleeps in a bed (the refuge's, or a bedroll
 * staged beside them in the wilderness) and the waking's autosave must show SAVING... and write slot 1;
 * the pause menu's R and N leave it. A second world starts in slot 2, walks, opens the map and saves
 * with F5, and R and Enter save it again on the way to the title, so F5's save becomes slot 2's
 * newest backup. LOAD FRONTIER then opens slot 1 and slot 2, each of which must hold exactly what was
 * saved into it: the same durable state, the file unchanged since, and the clock and play time of its
 * summary. 0.9.0's file is copied into slot 3 and refused, both files unchanged; then the title's
 * controls are set to 170% and invert-Y and applied. What each slot holds is kept beside the slots.
 * {@code route-release-relaunch} must find those mouse settings read at launch and opens both slots
 * again; {@code route-release-restore}, run in a copy of the directory, damages slot 2's save on the
 * disk and restores it from its newest backup, which must bring back the world F5 saved.
 */
final class FrontendRouteQa {
    static final String ROUTE = "route";
    static final String ROUTE_LOAD = "route-load";
    static final String ROUTE_REFUSAL = "route-refusal";
    static final String ROUTE_SLOTS = "route-slots";
    static final String ROUTE_SESSION = "route-session";
    static final String ROUTE_SESSION_RELAUNCH = "route-session-relaunch";
    static final String ROUTE_RELEASE = "route-release";
    static final String ROUTE_RELEASE_RELAUNCH = "route-release-relaunch";
    static final String ROUTE_RELEASE_RESTORE = "route-release-restore";
    /** The words {@code route-session} types as a seed. */
    static final String TYPED_SEED = "deep frontier";
    /** The words {@code route-release} types as its first world's seed. */
    static final String RELEASE_SEED = "safe saves";
    /** Game minutes a loaded world's clock may have run past its save's summary when it is checked. */
    private static final double LOADED_CLOCK_SLACK = 1.0;
    /** The mouse sensitivity {@code route-session} applies, with invert-Y, and its relaunch must find. */
    static final int ROUTE_SENSITIVITY = 170;
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
    private String damagedHash = "";
    private List<String> firstSave = List.of();
    /** The seeds {@code route-session} saw offered and rolled. */
    private long offeredSeed, rolledSeed;
    /** What {@code route-release} saved into each slot, by key, as its later launches read it back. */
    private final Map<String, List<String>> kept = new LinkedHashMap<>();
    /** Autosaves written before the release route's sleep. */
    private int autosavesBefore;
    /** The bed the release route sleeps in. */
    private Vec3i bed;

    /** Whether a {@code VEYLON_FRONTEND} screen names this route. */
    static boolean names(String screen) {
        return ROUTE.equals(screen) || ROUTE_LOAD.equals(screen) || ROUTE_REFUSAL.equals(screen)
                || ROUTE_SLOTS.equals(screen) || ROUTE_SESSION.equals(screen) || ROUTE_SESSION_RELAUNCH.equals(screen)
                || ROUTE_RELEASE.equals(screen) || ROUTE_RELEASE_RELAUNCH.equals(screen)
                || ROUTE_RELEASE_RESTORE.equals(screen);
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
            case ROUTE_SLOTS -> "slots";
            case ROUTE_SESSION -> "session";
            case ROUTE_SESSION_RELAUNCH -> "relaunch";
            case ROUTE_RELEASE -> "release";
            case ROUTE_RELEASE_RELAUNCH -> "release-relaunch";
            case ROUTE_RELEASE_RESTORE -> "release-restore";
            default -> "new";
        };
        this.mode = mode;
        this.digest = slots().directory().resolve(launch.startsWith("release") ? "release-route.digest"
                : "frontend-route.digest");
        switch (launch) {
            case "refusal" -> planRefusalLaunch();
            case "load" -> planLoadLaunch();
            case "slots" -> planSlotsLaunch();
            case "session" -> planSessionLaunch();
            case "relaunch" -> planRelaunch();
            case "release" -> planReleaseLaunch();
            case "release-relaunch" -> planReleaseRelaunch();
            case "release-restore" -> planReleaseRestore();
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

    /**
     * Every state of the picker, both confirmations and a restore, from an empty isolated directory.
     * Each world is left through the pause menu's R and N, the way back to the title that leaves its
     * slot as it is (0.9.1 milestone 06).
     */
    private void planSlotsLaunch() {
        step("every slot is empty", check(() -> states().stream().allMatch(s -> s == SaveSlots.State.EMPTY)));
        step("title", wait(1.5));
        step("title capture", capture("01-title"));
        step("N", press(GLFW_KEY_N));
        step("new frontier", await(() -> game.appState == Game.AppState.TITLE_NEW_WORLD, 3));
        step("Enter", press(GLFW_KEY_ENTER));
        step("the picker for a new world", await(() -> picking(SlotPickerScreen.Purpose.NEW), 3));
        step("on the empty slot 1", check(() -> game.frontend.slotPicker.selected() == 1));
        step("picker settle", wait(0.5));
        step("empty capture", capture("02-new-all-empty"));
        step("Enter starts in slot 1", press(GLFW_KEY_ENTER));
        step("a city in slot 1", await(() -> game.appState == Game.AppState.PLAYING, 90));
        step("written at once", check(() -> game.world.isUrban() && game.saves.activeSlot() == 1
                && slots().describe(1).state() == SaveSlots.State.READY));
        step("remember its first save", run(() -> firstSave = SaveAuditQaScene.durable(game)));
        step("meshes", wait(3.0));
        step("F5", press(GLFW_KEY_F5));
        step("saved to slot 1", await(() -> logged(SaveSession.savedLine(1)), 3));
        step("its first save is the newest backup", check(() -> slots().newestIntactBackup(1) != null));
        step("Esc", press(GLFW_KEY_ESCAPE));
        step("pause", await(() -> game.uiMode == Game.UiMode.PAUSE, 2));
        step("pause settle", wait(0.5));
        step("pause capture", capture("03-pause-slot-1"));
        leaveWithoutSaving("slot 1");
        step("N again", press(GLFW_KEY_N));
        step("new frontier again", await(() -> game.appState == Game.AppState.TITLE_NEW_WORLD, 3));
        step("W", press(GLFW_KEY_W));
        step("C", press(GLFW_KEY_C));
        step("a Creative wilderness chosen", await(() -> !game.newFrontierScreen.showsCity()
                && game.newFrontierScreen.selectedMode() == GameMode.CREATIVE, 2));
        step("Enter", press(GLFW_KEY_ENTER));
        step("the picker on slot 2", await(() -> picking(SlotPickerScreen.Purpose.NEW)
                && game.frontend.slotPicker.selected() == 2, 3));
        step("picker settle 2", wait(0.5));
        step("one used capture", capture("04-new-one-used"));
        step("Enter starts in slot 2", press(GLFW_KEY_ENTER));
        step("a wilderness in slot 2", await(() -> game.appState == Game.AppState.PLAYING, 90));
        step("in Creative", check(() -> !game.world.isUrban() && game.gameMode() == GameMode.CREATIVE
                && game.saves.activeSlot() == 2));
        step("meshes 2", wait(2.0));
        step("F5 into slot 2", press(GLFW_KEY_F5));
        step("saved to slot 2", await(() -> logged(SaveSession.savedLine(2)), 3));
        step("Esc in slot 2", press(GLFW_KEY_ESCAPE));
        step("pause in slot 2", await(() -> game.uiMode == Game.UiMode.PAUSE, 2));
        leaveWithoutSaving("slot 2");
        step("damage slot 1 on the disk", run(() -> damage(1)));
        step("another version into slot 3", run(this::otherVersionIntoSlotThree));
        step("L", press(GLFW_KEY_L));
        step("the picker to load", await(() -> picking(SlotPickerScreen.Purpose.LOAD), 3));
        step("every state shows", check(() -> shown(1) == SaveSlots.State.DAMAGED
                && game.frontend.slotPicker.slots().getFirst().backup() != null
                && shown(2) == SaveSlots.State.READY && shown(3) == SaveSlots.State.OTHER_VERSION
                && game.frontend.slotPicker.selected() == 2));
        step("states settle", wait(0.6));
        step("states capture", capture("05-load-states"));
        step("3", press(GLFW_KEY_3));
        step("Enter on slot 3", press(GLFW_KEY_ENTER));
        step("it says why", await(() -> !game.frontend.slotPicker.notice().isBlank(), 2));
        step("other settle", wait(0.5));
        step("other version capture", capture("06-other-version"));
        step("1", press(GLFW_KEY_1));
        step("R", press(GLFW_KEY_R));
        step("the restore asks", await(() -> game.frontend.slotPicker.asking() == SlotPickerScreen.Ask.RESTORE, 2));
        step("ask settle", wait(0.5));
        step("restore capture", capture("07-restore-confirm"));
        step("Y restores", press(GLFW_KEY_Y));
        step("restored", await(() -> game.appState == Game.AppState.PLAYING, 90));
        step("its first save's world", check(() -> game.saves.activeSlot() == 1
                && SaveAuditQaScene.durable(game).equals(firstSave)));
        step("the damaged save set aside", check(() -> damagedHash.equals(hash(slots().setAside(1, 1)))
                && slots().describe(1).state() == SaveSlots.State.READY));
        step("restored meshes", wait(3.0));
        step("restored capture", capture("08-restored"));
        step("Esc in the restored world", press(GLFW_KEY_ESCAPE));
        step("pause in the restored world", await(() -> game.uiMode == Game.UiMode.PAUSE, 2));
        leaveWithoutSaving("the restored slot 1");
        step("N a third time", press(GLFW_KEY_N));
        step("new frontier a third time", await(() -> game.appState == Game.AppState.TITLE_NEW_WORLD, 3));
        step("Enter to the picker", press(GLFW_KEY_ENTER));
        step("no empty slot left", await(() -> picking(SlotPickerScreen.Purpose.NEW), 3));
        step("2", press(GLFW_KEY_2));
        step("Enter on slot 2", press(GLFW_KEY_ENTER));
        step("the replacement asks", await(() -> game.frontend.slotPicker.asking() == SlotPickerScreen.Ask.REPLACE, 2));
        step("replace settle", wait(0.5));
        step("replace capture", capture("09-replace-confirm"));
        step("N keeps it", press(GLFW_KEY_N));
        step("still picking", await(() -> game.frontend.slotPicker.asking() == SlotPickerScreen.Ask.NONE
                && picking(SlotPickerScreen.Purpose.NEW), 2));
        step("Del", press(GLFW_KEY_DELETE));
        step("the delete asks", await(() -> game.frontend.slotPicker.asking() == SlotPickerScreen.Ask.DELETE, 2));
        step("delete settle", wait(0.5));
        step("delete capture", capture("10-delete-confirm"));
        step("N keeps it again", press(GLFW_KEY_N));
        step("slot 2 is kept", await(() -> game.frontend.slotPicker.asking() == SlotPickerScreen.Ask.NONE
                && slots().describe(2).state() == SaveSlots.State.READY, 2));
        step("Esc to the new frontier", press(GLFW_KEY_ESCAPE));
        step("back on the new frontier", await(() -> game.appState == Game.AppState.TITLE_NEW_WORLD, 2));
        step("Esc to the title", press(GLFW_KEY_ESCAPE));
        step("back at the title", await(() -> game.appState == Game.AppState.TITLE, 2));
        step("report", run(this::report));
        step("Q quits", press(GLFW_KEY_Q));
    }

    /** From the pause menu, R and N: back to the title without saving, the slot left as it is. */
    private void leaveWithoutSaving(String label) {
        step("R in " + label, press(GLFW_KEY_R));
        step("the question in " + label, await(() -> game.uiMode == Game.UiMode.RETURN_TO_TITLE, 2));
        step("N leaves " + label, press(GLFW_KEY_N));
        step("at the title from " + label, await(() -> game.appState == Game.AppState.TITLE && game.world == null, 5));
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

    /** Changes one byte in the middle of a slot's save, as damage on the disk would. */
    private void damage(int slot) {
        try {
            Path file = slots().current(slot);
            byte[] bytes = Files.readAllBytes(file);
            bytes[bytes.length / 2] ^= 0x5A;
            Files.write(file, bytes);
            damagedHash = hash(file);
            System.out.println("[frontend-route] damaged slot " + slot + " at byte " + bytes.length / 2
                    + " sha256=" + damagedHash);
        } catch (IOException e) {
            throw new UncheckedIOException("could not damage slot " + slot, e);
        }
    }

    /** Slot 3 gets VEYLON 0.9.0's file when it is there, else slot 2's save under a later epoch. */
    private void otherVersionIntoSlotThree() {
        copyLegacyInto(3);
        if (Files.exists(slots().current(3))) {
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(slots().current(2));
            ByteBuffer.wrap(bytes).putInt(Integer.BYTES * 2, SaveHeader.EPOCH + 1);
            Files.write(slots().current(3), bytes);
            System.out.println("[frontend-route] slot 3 holds slot 2's save under epoch " + (SaveHeader.EPOCH + 1));
        } catch (IOException e) {
            throw new UncheckedIOException("could not stage slot 3", e);
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

    /**
     * 0.9.1 milestone 06 from an empty isolated directory: NEW FRONTIER with the seed it offers,
     * the seed typed as words and rolled again; the words typed once more and a city of their seed
     * in slot 1; the pause menu naming it; the controls set to 170% and invert-Y and applied; the
     * way back to the title, cancelled, then taken with Save and return; LOAD FRONTIER and the
     * slot's world again; Return without saving, which leaves the slot byte for byte; and NEW
     * FRONTIER once more with nothing typed carried over.
     */
    private void planSessionLaunch() {
        long typedSeed = WorldSeed.of(TYPED_SEED);
        step("every slot is empty", check(() -> states().stream().allMatch(s -> s == SaveSlots.State.EMPTY)));
        step("the controls are the defaults", check(() -> game.controls.sensitivity() == ControlSettings.DEFAULT_SENSITIVITY
                && !game.controls.invertY));
        step("title", wait(1.5));
        step("title capture", capture("01-title"));
        step("N", press(GLFW_KEY_N));
        step("new frontier", await(() -> game.appState == Game.AppState.TITLE_NEW_WORLD, 3));
        step("it shows the seed it offers", check(() -> {
            offeredSeed = game.newFrontierScreen.seed();
            return game.newFrontierScreen.fieldText().equals(Long.toString(offeredSeed));
        }));
        step("offered settle", wait(0.6));
        step("offered capture", capture("02-new-frontier-offered"));
        typeSeed("typed");
        step("the words make the seed", check(() -> game.newFrontierScreen.seed() == typedSeed
                && game.newFrontierScreen.seedNote().contains(Long.toString(typedSeed))));
        step("typed settle", wait(0.6));
        step("typed capture", capture("03-new-frontier-typed"));
        step("R", press(GLFW_KEY_R));
        step("another seed", await(() -> game.newFrontierScreen.typedSeed().isEmpty()
                && game.newFrontierScreen.seed() != typedSeed && game.newFrontierScreen.seed() != offeredSeed, 2));
        step("remember it", run(() -> rolledSeed = game.newFrontierScreen.seed()));
        step("rolled settle", wait(0.6));
        step("rolled capture", capture("04-new-frontier-rerolled"));
        typeSeed("again");
        step("Enter", press(GLFW_KEY_ENTER));
        step("the picker on slot 1", await(() -> picking(SlotPickerScreen.Purpose.NEW)
                && game.frontend.slotPicker.selected() == 1, 3));
        step("Enter starts in slot 1", press(GLFW_KEY_ENTER));
        step("loading", await(() -> game.appState == Game.AppState.LOADING, 2));
        step("playing", await(() -> game.appState == Game.AppState.PLAYING, 90));
        step("a city of the typed seed in slot 1", check(() -> game.world.isUrban() && game.world.seed == typedSeed
                && game.saves.activeSlot() == 1 && slots().describe(1).state() == SaveSlots.State.READY));
        step("meshes", wait(3.0));
        step("Esc", press(GLFW_KEY_ESCAPE));
        step("pause", await(() -> game.uiMode == Game.UiMode.PAUSE, 2));
        step("pause settle", wait(0.5));
        step("pause capture", capture("05-pause-city"));
        step("C", press(GLFW_KEY_C));
        step("controls", await(() -> game.uiMode == Game.UiMode.CONTROLS_OPTIONS, 2));
        step("controls settle", wait(0.5));
        step("controls capture", capture("06-controls"));
        for (int n = 1; n <= (ROUTE_SENSITIVITY - ControlSettings.DEFAULT_SENSITIVITY) / ControlSettings.SENSITIVITY_STEP; n++) {
            step("Right " + n, press(GLFW_KEY_RIGHT));
        }
        step("Down", press(GLFW_KEY_DOWN));
        step("Enter inverts Y", press(GLFW_KEY_ENTER));
        step("170% and inverted", await(() -> game.controls.sensitivity() == ROUTE_SENSITIVITY && game.controls.invertY, 2));
        step("changed settle", wait(0.5));
        step("changed capture", capture("07-controls-changed"));
        step("F5 applies", press(GLFW_KEY_F5));
        step("back in the pause menu", await(() -> game.uiMode == Game.UiMode.PAUSE, 2));
        step("written for the next launch", check(this::controlsApplied));
        step("R", press(GLFW_KEY_R));
        step("the question", await(() -> game.uiMode == Game.UiMode.RETURN_TO_TITLE, 2));
        step("question settle", wait(0.5));
        step("question capture", capture("08-return-question"));
        step("Esc stays", press(GLFW_KEY_ESCAPE));
        step("the pause menu again", await(() -> game.uiMode == Game.UiMode.PAUSE && game.world != null, 2));
        step("hash slot 1", run(() -> slotBefore = hash(slots().current(1))));
        step("R again", press(GLFW_KEY_R));
        step("the question again", await(() -> game.uiMode == Game.UiMode.RETURN_TO_TITLE, 2));
        step("Enter saves and returns", press(GLFW_KEY_ENTER));
        step("the title", await(() -> game.appState == Game.AppState.TITLE && game.world == null, 5));
        step("slot 1 was written first", check(() -> "Saved to slot 1.".equals(game.titleScreen.notice())
                && !slotBefore.equals(hash(slots().current(1))) && slots().newestIntactBackup(1) != null));
        step("title settle", wait(0.6));
        step("saved capture", capture("09-title-saved"));
        step("L", press(GLFW_KEY_L));
        step("the picker to load", await(() -> picking(SlotPickerScreen.Purpose.LOAD), 3));
        step("Enter loads slot 1", press(GLFW_KEY_ENTER));
        step("loaded", await(() -> game.appState == Game.AppState.PLAYING, 90));
        step("the city of the typed seed", check(() -> game.world.seed == typedSeed && game.saves.activeSlot() == 1));
        step("loaded meshes", wait(2.0));
        step("Esc once more", press(GLFW_KEY_ESCAPE));
        step("pause once more", await(() -> game.uiMode == Game.UiMode.PAUSE, 2));
        step("hash slot 1 again", run(() -> slotBefore = hash(slots().current(1))));
        step("R once more", press(GLFW_KEY_R));
        step("the question once more", await(() -> game.uiMode == Game.UiMode.RETURN_TO_TITLE, 2));
        step("N returns without saving", press(GLFW_KEY_N));
        step("the title again", await(() -> game.appState == Game.AppState.TITLE && game.world == null, 5));
        step("slot 1 is byte for byte what it was", check(() -> slotBefore.equals(hash(slots().current(1)))
                && "Slot 1 keeps its last save.".equals(game.titleScreen.notice())));
        step("returned settle", wait(0.6));
        step("returned capture", capture("10-title-returned"));
        step("N", press(GLFW_KEY_N));
        step("new frontier anew", await(() -> game.appState == Game.AppState.TITLE_NEW_WORLD, 3));
        step("nothing typed carried over", check(() -> game.newFrontierScreen.typedSeed().isEmpty()
                && game.newFrontierScreen.seed() != typedSeed));
        step("Esc to the title", press(GLFW_KEY_ESCAPE));
        step("at the title", await(() -> game.appState == Game.AppState.TITLE, 2));
        step("report", run(this::report));
        step("Q quits", press(GLFW_KEY_Q));
    }

    /** E, the words typed in one frame, then Enter, which finishes the seed and stays on NEW FRONTIER. */
    private void typeSeed(String label) {
        typeSeed(label, TYPED_SEED);
    }

    private void typeSeed(String label, String words) {
        step("E " + label, press(GLFW_KEY_E));
        step("typing " + label, await(() -> game.newFrontierScreen.typingSeed(), 2));
        step("the words " + label, run(() -> words.codePoints().forEach(game.input::onTyped)));
        step("they are in the field " + label, await(() -> words.equals(game.newFrontierScreen.typedSeed()), 2));
        step("Enter finishes " + label, press(GLFW_KEY_ENTER));
        step("finished " + label, await(() -> !game.newFrontierScreen.typingSeed()
                && game.appState == Game.AppState.TITLE_NEW_WORLD, 2));
    }

    /** Whether the player's settings file holds what the session route applied. */
    private boolean controlsApplied() {
        ControlSettings read = ControlSettings.loadOrDefaults();
        return read.sensitivity() == ROUTE_SENSITIVITY && read.invertY;
    }

    /** The relaunch after {@code route-session}: the mouse settings it applied were read at launch. */
    private void planRelaunch() {
        step("read at launch", check(() -> game.controls.sensitivity() == ROUTE_SENSITIVITY && game.controls.invertY));
        step("title", wait(1.5));
        step("C", press(GLFW_KEY_C));
        step("controls", await(() -> game.appState == Game.AppState.TITLE_CONTROLS_OPTIONS, 2));
        step("controls settle", wait(0.6));
        step("persisted capture", capture("01-controls-persisted"));
        step("Esc", press(GLFW_KEY_ESCAPE));
        step("back at the title", await(() -> game.appState == Game.AppState.TITLE, 2));
        step("BACK changed nothing", check(this::controlsApplied));
        step("report", run(this::report));
        step("Q quits", press(GLFW_KEY_Q));
    }

    /**
     * The release's acceptance scenario (0.9.1 milestone 09) in the session's world kind and mode, from
     * an empty isolated directory that holds VEYLON 0.9.0's file: a world of a typed seed in slot 1, a
     * search in a city, a night's sleep and the waking's autosave, left with R and N; a second world in
     * slot 2 saved with F5 after a walk and again with R and Enter; both slots opened and compared;
     * 0.9.0's save refused from slot 3; the mouse settings applied for the next launch.
     */
    private void planReleaseLaunch() {
        if (!game.saves.autosave().permitted()) {
            throw new IllegalStateException(ROUTE_RELEASE + " needs VEYLON_AUTOSAVE=on: automation does not autosave otherwise");
        }
        boolean city = World.isUrbanGenerator(game.frontend.newWorldGenerator);
        long seedA = WorldSeed.of(RELEASE_SEED);
        long[] seedB = {0};
        String older = new LoadResult(LoadResult.Outcome.OLDER_VERSION, "").message();
        step("every slot is empty", check(() -> states().stream().allMatch(s -> s == SaveSlots.State.EMPTY)));
        step("0.9.0's file is beside them", check(() -> slots().holdsLegacySave()));
        step("the controls are the defaults", check(() -> game.controls.sensitivity() == ControlSettings.DEFAULT_SENSITIVITY
                && !game.controls.invertY));
        step("autosave is on", check(() -> game.saves.autosave().settings().enabled));
        step("title", wait(1.5));
        step("title capture", capture("01-title"));
        newFrontier("A", city);
        typeSeed("A", RELEASE_SEED);
        step("the words make the seed", check(() -> game.newFrontierScreen.seed() == seedA));
        step("typed settle", wait(0.6));
        step("typed capture", capture("02-new-frontier-typed"));
        startIn(1, city, () -> seedA, "03-world-a");
        if (city) {
            search("04-search");
        }
        sleepAndAutosave("slot1");
        step("Esc in world A", press(GLFW_KEY_ESCAPE));
        step("pause in world A", await(() -> game.uiMode == Game.UiMode.PAUSE, 2));
        step("pause settle A", wait(0.5));
        step("pause capture A", capture("08-pause-slot-1"));
        leaveWithoutSaving("world A");
        step("slot 1 keeps the autosave", check(() -> "Slot 1 keeps its last save.".equals(game.titleScreen.notice())
                && !kept("slot1.sha256").isEmpty() && hash(slots().current(1)).equals(kept("slot1.sha256"))));
        newFrontier("B", city);
        step("take the offered seed", run(() -> seedB[0] = game.newFrontierScreen.seed()));
        startIn(2, city, () -> seedB[0], "09-world-b");
        step("walk", hold(GLFW_KEY_W, 2.5));
        step("stand", wait(0.6));
        step("M", press(GLFW_KEY_M));
        step("map", await(() -> game.uiMode == Game.UiMode.MAP, 2));
        step("map settle", wait(0.5));
        step("map capture", capture("10-map"));
        step("Esc closes the map", press(GLFW_KEY_ESCAPE));
        step("map closed", await(() -> game.uiMode == Game.UiMode.NONE, 2));
        step("F5 into slot 2", press(GLFW_KEY_F5));
        step("saved to slot 2", savedAndKept(2, "slot2.f5"));
        step("saved settle", wait(0.4));
        step("saved capture", capture("11-saved-slot-2"));
        step("Esc in world B", press(GLFW_KEY_ESCAPE));
        step("pause in world B", await(() -> game.uiMode == Game.UiMode.PAUSE, 2));
        step("pause settle B", wait(0.5));
        step("pause capture B", capture("12-pause-slot-2"));
        step("R in world B", press(GLFW_KEY_R));
        step("the question in world B", await(() -> game.uiMode == Game.UiMode.RETURN_TO_TITLE, 2));
        step("question settle", wait(0.5));
        step("question capture", capture("13-return-question"));
        step("keep what the return saves", run(() -> kept.put("slot2", SaveAuditQaScene.durable(game))));
        step("Enter saves and returns", press(GLFW_KEY_ENTER));
        step("the title from world B", await(() -> game.appState == Game.AppState.TITLE && game.world == null, 5));
        step("F5's save is slot 2's newest backup", check(() -> "Saved to slot 2.".equals(game.titleScreen.notice())
                && !kept("slot2.f5.sha256").isEmpty() && hash(slots().backup(2, 1)).equals(kept("slot2.f5.sha256"))
                && !hash(slots().current(2)).equals(kept("slot2.f5.sha256"))));
        step("keep slot 2's file", run(() -> kept.put("slot2.sha256", List.of(hash(slots().current(2))))));
        step("title settle", wait(0.6));
        step("saved title capture", capture("14-title-saved"));
        step("L", press(GLFW_KEY_L));
        step("the picker to load", await(() -> picking(SlotPickerScreen.Purpose.LOAD), 3));
        step("two ready slots", check(() -> shown(1) == SaveSlots.State.READY && shown(2) == SaveSlots.State.READY
                && shown(3) == SaveSlots.State.EMPTY));
        step("picker settle", wait(0.6));
        step("picker capture", capture("15-load-two-slots"));
        step("Esc from the picker", press(GLFW_KEY_ESCAPE));
        step("the title before loading", await(() -> game.appState == Game.AppState.TITLE, 2));
        openAndCompare(1, "16-loaded-slot-1");
        openAndCompare(2, "17-loaded-slot-2");
        step("0.9.0's save into slot 3", run(() -> copyLegacyInto(3)));
        step("hash slot 3 and 0.9.0's file", run(() -> {
            slotBefore = hash(slots().current(3));
            legacyBefore = hash(slots().legacySave());
        }));
        step("slot 3 holds 0.9.0's save", check(() -> !slotBefore.isEmpty() && slotBefore.equals(legacyBefore)));
        step("L for slot 3", press(GLFW_KEY_L));
        step("the picker for slot 3", await(() -> picking(SlotPickerScreen.Purpose.LOAD), 3));
        step("3", press(GLFW_KEY_3));
        step("slot 3 is another version", check(() -> shown(3) == SaveSlots.State.OTHER_VERSION
                && game.frontend.slotPicker.selected() == 3));
        step("other settle", wait(0.6));
        step("other capture", capture("18-slot-3-other-version"));
        step("Enter on slot 3", press(GLFW_KEY_ENTER));
        step("the picker says why", await(() -> older.equals(game.frontend.slotPicker.notice()), 2));
        step("nothing was loaded", check(() -> game.world == null && game.appState == Game.AppState.TITLE_SLOTS));
        step("refused settle", wait(0.6));
        step("refused capture", capture("19-slot-3-refused"));
        step("Esc after the refusal", press(GLFW_KEY_ESCAPE));
        step("the title after the refusal", await(() -> game.appState == Game.AppState.TITLE, 2));
        step("hash both again", run(() -> {
            slotAfter = hash(slots().current(3));
            legacyAfter = hash(slots().legacySave());
        }));
        step("slot 3 and 0.9.0's file are untouched", check(() -> slotBefore.equals(slotAfter)
                && legacyBefore.equals(legacyAfter)));
        step("C", press(GLFW_KEY_C));
        step("controls", await(() -> game.appState == Game.AppState.TITLE_CONTROLS_OPTIONS, 2));
        step("controls settle", wait(0.5));
        step("controls capture", capture("20-controls"));
        for (int n = 1; n <= (ROUTE_SENSITIVITY - ControlSettings.DEFAULT_SENSITIVITY) / ControlSettings.SENSITIVITY_STEP; n++) {
            step("Right " + n, press(GLFW_KEY_RIGHT));
        }
        step("Down", press(GLFW_KEY_DOWN));
        step("Enter inverts Y", press(GLFW_KEY_ENTER));
        step("170% and inverted", await(() -> game.controls.sensitivity() == ROUTE_SENSITIVITY && game.controls.invertY, 2));
        step("changed settle", wait(0.5));
        step("changed capture", capture("21-controls-changed"));
        step("F5 applies", press(GLFW_KEY_F5));
        step("back at the title", await(() -> game.appState == Game.AppState.TITLE, 2));
        step("written for the next launch", check(this::controlsApplied));
        step("keep the slots beside them", run(this::writeKept));
        step("report", run(this::report));
        step("Q quits", press(GLFW_KEY_Q));
    }

    /** The relaunch after {@code route-release}: the mouse settings read at launch, both slots as saved. */
    private void planReleaseRelaunch() {
        step("read what was kept", run(this::readKept));
        step("read at launch", check(() -> game.controls.sensitivity() == ROUTE_SENSITIVITY && game.controls.invertY));
        step("title", wait(1.5));
        step("title capture", capture("01-title"));
        step("C", press(GLFW_KEY_C));
        step("controls", await(() -> game.appState == Game.AppState.TITLE_CONTROLS_OPTIONS, 2));
        step("controls settle", wait(0.6));
        step("persisted capture", capture("02-controls-persisted"));
        step("Esc", press(GLFW_KEY_ESCAPE));
        step("back at the title", await(() -> game.appState == Game.AppState.TITLE, 2));
        step("BACK changed nothing", check(this::controlsApplied));
        openAndCompare(1, "03-loaded-slot-1");
        openAndCompare(2, "04-loaded-slot-2");
        step("report", run(this::report));
        step("Q quits", press(GLFW_KEY_Q));
    }

    /**
     * In a copy of {@code route-release}'s directory: slot 2's save damaged on the disk, then restored
     * from its newest backup, which must bring back the world F5 saved and set the damaged save aside
     * unchanged.
     */
    private void planReleaseRestore() {
        step("read what was kept", run(this::readKept));
        step("slot 2 is ready", check(() -> slots().describe(2).state() == SaveSlots.State.READY));
        step("damage slot 2 on the disk", run(() -> damage(2)));
        step("title", wait(1.5));
        step("L", press(GLFW_KEY_L));
        step("the picker to load", await(() -> picking(SlotPickerScreen.Purpose.LOAD), 3));
        step("2", press(GLFW_KEY_2));
        step("slot 2 is damaged with a backup", check(() -> shown(2) == SaveSlots.State.DAMAGED
                && game.frontend.slotPicker.slots().get(1).backup() != null
                && game.frontend.slotPicker.selected() == 2));
        step("damaged settle", wait(0.6));
        step("damaged capture", capture("01-slot-2-damaged"));
        step("R", press(GLFW_KEY_R));
        step("the restore asks", await(() -> game.frontend.slotPicker.asking() == SlotPickerScreen.Ask.RESTORE, 2));
        step("ask settle", wait(0.5));
        step("restore capture", capture("02-restore-confirm"));
        step("Y restores", press(GLFW_KEY_Y));
        step("restored as F5 saved it", loadedAsSaved(2, "slot2.f5", 90));
        step("the damaged save set aside", check(() -> damagedHash.equals(hash(slots().setAside(2, 1)))
                && slots().describe(2).state() == SaveSlots.State.READY));
        step("restored meshes", wait(2.5));
        step("restored capture", capture("03-restored"));
        step("Esc in the restored world", press(GLFW_KEY_ESCAPE));
        step("pause in the restored world", await(() -> game.uiMode == Game.UiMode.PAUSE, 2));
        leaveWithoutSaving("the restored slot 2");
        step("report", run(this::report));
        step("Q quits", press(GLFW_KEY_Q));
    }

    /** N, NEW FRONTIER on the session's world kind, and C for a Creative session. */
    private void newFrontier(String label, boolean city) {
        step("N " + label, press(GLFW_KEY_N));
        step("new frontier " + label, await(() -> game.appState == Game.AppState.TITLE_NEW_WORLD, 3));
        step("on the session's world " + label, check(() -> game.newFrontierScreen.showsCity() == city));
        if (mode == GameMode.CREATIVE) {
            step("C " + label, press(GLFW_KEY_C));
            step("Creative " + label, await(() -> game.newFrontierScreen.selectedMode() == GameMode.CREATIVE, 1));
        }
    }

    /** Enter, the picker on {@code slot} as the first empty one, Enter: the world starts and is saved there. */
    private void startIn(int slot, boolean city, LongSupplier seed, String label) {
        step("Enter to slot " + slot, press(GLFW_KEY_ENTER));
        step("the picker on slot " + slot, await(() -> picking(SlotPickerScreen.Purpose.NEW)
                && game.frontend.slotPicker.selected() == slot, 3));
        step("Enter starts in slot " + slot, press(GLFW_KEY_ENTER));
        step("loading into slot " + slot, await(() -> game.appState == Game.AppState.LOADING, 2));
        step("playing in slot " + slot, await(() -> game.appState == Game.AppState.PLAYING, 90));
        step("the world of slot " + slot, check(() -> game.world.isUrban() == city
                && game.world.generatorVersion == (city ? World.DEFAULT_NEW_WORLD_GENERATOR : World.WILDERNESS_NEW_WORLD_GENERATOR)
                && game.gameMode() == mode && game.world.seed == seed.getAsLong() && game.saves.activeSlot() == slot
                && slots().describe(slot).state() == SaveSlots.State.READY));
        step("meshes in slot " + slot, wait(3.0));
        step("capture in slot " + slot, capture(label));
    }

    /**
     * A staged night and a bed: F sleeps until the morning, and the waking's autosave must show
     * SAVING... before it writes the active slot, whose save is kept under {@code key}.
     */
    private void sleepAndAutosave(String key) {
        step("night", run(this::stageNight));
        step("stand at a bed", run(this::standAtBed));
        step("bed settle", wait(0.6));
        step("the bed offers sleep", check(() -> game.interactPrompt != null
                && (game.interactPrompt.startsWith("[F] Sleep") || game.interactPrompt.startsWith("[F] Rest"))));
        step("count the autosaves", run(() -> autosavesBefore = game.saves.autosave().written()));
        step("F sleeps", press(GLFW_KEY_F));
        step("asleep", await(() -> game.sleeping, 3));
        step("asleep settle", wait(0.4));
        step("asleep capture", capture("05-asleep"));
        step("the morning", await(() -> !game.sleeping, 30));
        step("the waking's autosave", autosaved("06-saving", "07-autosaved", key));
    }

    /**
     * Waits for the waking's autosave: SAVING... must show before the slot is written, and the log
     * must say so; each is captured. What the save holds is kept under {@code key} in the frame it is
     * written, since the route runs at the end of that frame: a later frame's slow tick may already
     * register a site the save did not hold.
     */
    private Step autosaved(String saving, String written, String key) {
        boolean[] announced = {false};
        double[] at = {-1};
        return t -> {
            Autosave autosave = game.saves.autosave();
            if (at[0] < 0) {
                if (autosave.written() == autosavesBefore) {
                    if (autosave.announcing() && !announced[0]) {
                        announced[0] = true;
                        if (autosave.due() != Autosave.Trigger.WAKE) {
                            failures.add("failed: announced " + autosave.due() + ", not WAKE");
                        }
                        driver.requestCapture(saving);
                    }
                    if (t > 30) {
                        failures.add("timed out: " + label + " due=" + autosave.due() + " blocker=" + autosave.blocker());
                        return true;
                    }
                    return false;
                }
                if (!announced[0]) {
                    failures.add("failed: written with no SAVING... shown");
                }
                keep(key, game.saves.activeSlot());
                at[0] = t;
                System.out.println("[frontend-route] autosave wake slot=" + game.saves.activeSlot() + " "
                        + String.format(Locale.ROOT, "%.1fms", autosave.lastMillis()));
                return false;
            }
            if (t - at[0] < 0.8) {
                return false; // the log line drawn before the capture
            }
            if (!logged(Autosave.savedLine(game.saves.activeSlot()))) {
                failures.add("failed: no autosave line in the log");
            }
            driver.requestCapture(written);
            return true;
        };
    }

    /** The clock forward to the next 23:15 in clear weather, as the city tours stage a night. */
    private void stageNight() {
        double now = game.time.totalMinutes;
        double day = Math.floor(now / TimeConstants.MINUTES_PER_DAY) * TimeConstants.MINUTES_PER_DAY;
        double target = day + 23 * TimeConstants.MINUTES_PER_HOUR + 15;
        if (target <= now) {
            target += TimeConstants.MINUTES_PER_DAY;
        }
        game.time.totalMinutes = target;
        game.weather.current = game.weather.next = Weather.CLEAR;
        game.weather.blend = 1f;
        game.weather.changeTimer = 4000f;
        System.out.println("[frontend-route] staged-clock night advancedBy=" + Math.round(target - now) + "min");
    }

    /**
     * The staged pose for the night: at the starter refuge's nearest bed in a city; in the wilderness,
     * which has no refuges, beside a bedroll staged on the ground next to the survivor.
     */
    private void standAtBed() {
        var p = game.player.pos;
        if (game.world.isUrban()) {
            BuildingPlan refuge = game.world.urban.buildingById(game.world.urban.refugeBuilding);
            BuildingPlan.Fixture nearest = refuge.fixtures().stream().filter(f -> RefugeSystem.isBed(f.block()))
                    .min(Comparator.comparingDouble(f -> f.target().distSq(p.x, p.y, p.z))).orElse(null);
            if (nearest == null) {
                failures.add("no bed in the refuge");
                return;
            }
            bed = nearest.position();
            face(nearest.target(), bed, .4f);
        } else {
            Vec3i feet = new Vec3i((int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z));
            bed = null;
            for (int[] d : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {-2, -2}}) {
                int x = feet.x() + d[0], z = feet.z() + d[1];
                for (int dy = 1; dy >= -2 && bed == null && game.world.isColumnLoaded(x, z); dy--) {
                    int y = feet.y() + dy;
                    if (game.world.getBlock(x, y, z) == BlockType.AIR && game.world.getBlock(x, y - 1, z).solid) {
                        bed = new Vec3i(x, y, z);
                    }
                }
                if (bed != null) {
                    break;
                }
            }
            if (bed == null) {
                failures.add("no ground beside the survivor for a bedroll");
                return;
            }
            game.world.setBlock(bed.x(), bed.y(), bed.z(), BlockType.BEDROLL, true);
            face(feet, bed, .1f);
        }
        System.out.println("[frontend-route] staged bed " + bed + " " + game.world.getBlock(bed.x(), bed.y(), bed.z()));
    }

    /** LOAD FRONTIER opens {@code slot}, which must hold what was kept of it; R and N leave it as it is. */
    private void openAndCompare(int slot, String label) {
        String name = "slot " + slot;
        step("L for " + name, press(GLFW_KEY_L));
        step("the picker for " + name, await(() -> picking(SlotPickerScreen.Purpose.LOAD), 3));
        step(slot + " for " + name, press(GLFW_KEY_0 + slot));
        step("Enter loads " + name, press(GLFW_KEY_ENTER));
        step("loading " + name, await(() -> game.appState == Game.AppState.LOADING, 2));
        step(name + " holds exactly what was saved", loadedAsSaved(slot, "slot" + slot, 90));
        step("meshes " + name, wait(2.5));
        step("capture " + name, capture(label));
        step("Esc in " + name, press(GLFW_KEY_ESCAPE));
        step("pause in " + name, await(() -> game.uiMode == Game.UiMode.PAUSE, 2));
        leaveWithoutSaving("loaded " + name);
        step(name + " is unchanged by its load", check(() -> !kept("slot" + slot + ".sha256").isEmpty()
                && hash(slots().current(slot)).equals(kept("slot" + slot + ".sha256"))));
    }

    /**
     * Whether the live world is what was kept under {@code key}: the active slot, the same durable
     * state, the slot's file unchanged since, and the clock and play time of that file's summary, which
     * only the few frames since the load may have run past.
     */
    private boolean asSaved(int slot, String key) {
        List<String> durable = kept.get(key);
        SaveSummary.Inspection file = SaveSummary.inspect(slots().current(slot));
        if (durable == null || !file.intact() || kept(key + ".sha256").isEmpty()) {
            return false;
        }
        double clock = game.time.totalMinutes - file.summary().clockMinutes();
        double played = game.saves.playSeconds() - file.summary().playSeconds();
        System.out.println("[frontend-route] " + key + " loaded: clock +" + String.format(Locale.ROOT, "%.2f", clock)
                + " min, play +" + String.format(Locale.ROOT, "%.2f", played) + " s, place " + file.summary().place());
        List<String> loaded = SaveAuditQaScene.durable(game);
        if (!loaded.equals(durable)) {
            System.out.println("[frontend-route] " + key + " kept " + durable + " loaded " + loaded);
        }
        return game.saves.activeSlot() == slot && loaded.equals(durable)
                && hash(slots().current(slot)).equals(kept(key + ".sha256"))
                && clock >= 0 && clock < LOADED_CLOCK_SLACK
                && played >= 0 && played < LOADED_CLOCK_SLACK / TimeConstants.MINUTES_PER_REAL_SECOND;
    }

    /**
     * Waits for the loaded world to play, then checks at once, in that same frame, that it is what
     * was kept under {@code key}: a later frame's slow tick may register sites the save does not hold.
     */
    private Step loadedAsSaved(int slot, String key, double timeout) {
        return t -> {
            if (game.appState != Game.AppState.PLAYING) {
                if (t > timeout) {
                    failures.add("timed out: " + label);
                    return true;
                }
                return false;
            }
            boolean same = asSaved(slot, key);
            if (!same) {
                failures.add("failed: " + label);
            }
            System.out.println("[frontend-route] check " + label + " = " + same);
            return true;
        };
    }

    /** Waits for F5's line, then keeps what the save holds in that same frame. */
    private Step savedAndKept(int slot, String key) {
        return t -> {
            if (!logged(SaveSession.savedLine(slot))) {
                if (t > 3) {
                    failures.add("timed out: " + label);
                    return true;
                }
                return false;
            }
            keep(key, slot);
            return true;
        };
    }

    /** Keeps the live world's durable state and the SHA-256 of the slot it was just saved into. */
    private void keep(String key, int slot) {
        kept.put(key, SaveAuditQaScene.durable(game));
        kept.put(key + ".sha256", List.of(hash(slots().current(slot))));
    }

    /** The first line kept under {@code key}, or empty. */
    private String kept(String key) {
        List<String> lines = kept.get(key);
        return lines == null || lines.isEmpty() ? "" : lines.getFirst();
    }

    /** Writes what the release route kept of each slot beside the slots, for its later launches. */
    private void writeKept() {
        List<String> lines = new ArrayList<>();
        kept.forEach((key, values) -> values.forEach(value -> lines.add(key + "\t" + value)));
        try {
            Files.write(digest, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not keep the release route's slots beside them", e);
        }
        lines.forEach(line -> System.out.println("[frontend-route] kept " + line));
    }

    /** Reads what a release launch kept of each slot. */
    private void readKept() {
        try {
            for (String line : Files.readAllLines(digest, StandardCharsets.UTF_8)) {
                int tab = line.indexOf('\t');
                if (tab > 0) {
                    kept.computeIfAbsent(line.substring(0, tab), k -> new ArrayList<>()).add(line.substring(tab + 1));
                }
            }
        } catch (IOException missing) {
            failures.add("nothing kept by a release launch: " + digest);
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
        search("08-search");
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

    /** A refuge cupboard searched with the use button from an empty hand, and one stack taken. */
    private void search(String label) {
        step("stand at a refuge cupboard", run(this::standAtCupboard));
        step("an empty hand", press(GLFW_KEY_9));
        step("aim settle", wait(0.5));
        step("RMB opens it", click(GLFW_MOUSE_BUTTON_RIGHT));
        step("its screen", await(() -> game.uiMode == Game.UiMode.CRATE, 2));
        step("the search", await(this::cupboardSearched, 8));
        step("search settle", wait(0.4));
        step("search capture", capture(label));
        step("take a stack", check(this::takeAStack));
        step("Esc closes it", press(GLFW_KEY_ESCAPE));
        step("closed", await(() -> game.uiMode == Game.UiMode.NONE, 2));
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
        face(stand, cupboard.position(), .5f);
        System.out.println("[frontend-route] cupboard " + cupboard.position() + " from " + stand);
    }

    /** Sets the survivor down on {@code stand}, at rest, looking {@code height} above {@code look}'s floor. */
    private void face(Vec3i stand, Vec3i look, float height) {
        game.player.pos.set(stand.x() + .5f, stand.y() + .001f, stand.z() + .5f);
        game.player.vel.zero();
        float dx = look.x() + .5f - game.player.pos.x;
        float dy = look.y() + height - (game.player.pos.y + game.player.eyeHeight());
        float dz = look.z() + .5f - game.player.pos.z;
        game.camera.yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
        game.camera.pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
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
        if ("slots".equals(launch)) {
            System.out.println("[frontend-route] slots " + states() + " damaged set aside sha256=" + damagedHash
                    + " legacy=" + slots().holdsLegacySave());
        }
        if ("session".equals(launch) || "relaunch".equals(launch)) {
            ControlSettings read = ControlSettings.loadOrDefaults();
            System.out.println("[frontend-route] session offered=" + offeredSeed + " typed='" + TYPED_SEED + "'="
                    + WorldSeed.of(TYPED_SEED) + " rerolled=" + rolledSeed + " slots=" + states()
                    + " controls file=" + ControlSettings.file().getFileName() + " sensitivity=" + read.sensitivity()
                    + "% invertY=" + read.invertY);
        }
        if (launch.startsWith("release")) {
            ControlSettings read = ControlSettings.loadOrDefaults();
            System.out.println("[frontend-route] release world="
                    + (World.isUrbanGenerator(game.frontend.newWorldGenerator) ? "city" : "wilderness")
                    + " typed='" + RELEASE_SEED + "'=" + WorldSeed.of(RELEASE_SEED) + " slots=" + states()
                    + " slot1 sha256=" + hash(slots().current(1)) + " slot2 sha256=" + hash(slots().current(2))
                    + " slot2 backup-1 sha256=" + hash(slots().backup(2, 1))
                    + ("release".equals(launch) ? " slot3 sha256 before=" + slotBefore + " after=" + slotAfter
                            + " veylon.sav sha256 before=" + legacyBefore + " after=" + legacyAfter : "")
                    + ("release-restore".equals(launch) ? " damaged set aside sha256=" + damagedHash : "")
                    + " controls sensitivity=" + read.sensitivity() + "% invertY=" + read.invertY);
        }
        String keys = switch (launch) {
            case "refusal" -> "L,2,Enter,Esc,N,Enter,Enter,Esc,O,V,Q";
            case "load" -> "L,Enter,Esc,O,V,Q";
            case "slots" -> "N,Enter,Enter,F5,Esc,R,N,N,W,C,Enter,Enter,F5,Esc,R,N,L,3,Enter,1,R,Y,Esc,R,N,"
                    + "N,Enter,2,Enter,N,Del,N,Esc,Esc,Q";
            case "session" -> "N,E,typed,Enter,R,E,typed,Enter,Enter,Enter,Esc,C,Right*7,Down,Enter,F5,R,Esc,R,Enter,"
                    + "L,Enter,Esc,R,N,N,Esc,Q";
            case "relaunch" -> "C,Esc,Q";
            case "release" -> "N,[C],E,typed,Enter,Enter,Enter,[9,RMB,Esc],F,Esc,R,N,N,[C],Enter,Enter,W-held,M,Esc,"
                    + "F5,Esc,R,Enter,L,Esc,L,1,Enter,Esc,R,N,L,2,Enter,Esc,R,N,L,3,Enter,Esc,C,Right*7,Down,Enter,F5,Q";
            case "release-relaunch" -> "C,Esc,L,1,Enter,Esc,R,N,L,2,Enter,Esc,R,N,Q";
            case "release-restore" -> "L,2,R,Y,Esc,R,N,Q";
            default -> "N,W,C,Enter,Enter,9,RMB,Esc,W-held,M,Esc,F5,Enter|Space,F9,O,V,Q";
        };
        System.out.println("[frontend-route] launch=" + launch + " pass=" + failures.isEmpty()
                + " failures=" + failures + " generator=" + (game.world == null ? -1 : game.world.generatorVersion)
                + " mode=" + (game.world == null ? mode : game.gameMode()).name().toLowerCase(Locale.ROOT)
                + " slot=" + game.saves.activeSlot() + " evidence=keys(" + keys + ")"
                + " glErrors=" + game.window.glErrorCount() + " khrErrors=" + game.window.glDebugErrorCount());
    }
}
