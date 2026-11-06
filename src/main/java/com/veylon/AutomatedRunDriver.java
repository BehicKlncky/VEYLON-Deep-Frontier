package com.veylon;

import com.veylon.gfx.ScreenshotUtil;
import com.veylon.entity.GameMode;
import com.veylon.item.ItemType;
import com.veylon.save.SaveSystem;
import com.veylon.simulation.WeatherSystem;
import com.veylon.util.AppPaths;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.UrbanRevision;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Drives the {@code VEYLON_*} automated sessions: benchmark scenes, timed
 * screenshot captures, front-end captures and the release smoke gate.
 *
 * <p>All of it is inert unless the corresponding environment variable is set,
 * which is the whole reason it can live outside the play loop. A normal run
 * constructs this, calls {@link #configure()}, and every per-frame hook after
 * that returns immediately.
 *
 * <p>It sits in {@code com.veylon} rather than {@code com.veylon.qa} for the
 * same reason {@link QaHarness} does: it drives {@code Game.appState} and the
 * screen fields, which are package-private on purpose.
 *
 * <h2>The smoke gate</h2>
 *
 * <p>{@code VEYLON_SMOKE=<seconds>} runs a scripted session and then throws if
 * anything went wrong, so a broken build fails packaging rather than shipping.
 * The script exercises the parts most likely to break silently: a world edit
 * and an equip, an isolated save, a load of that save, an ignited log stack
 * under a forced storm to stress fire and weather together, and a fortress
 * approach. Its phases are wall-clock scheduled rather than frame-counted, so
 * the same script covers the same ground on fast and slow machines.
 */
final class AutomatedRunDriver {

    private static final double MIN_SMOKE_SECONDS = 6;

    private final Game game;

    // Session configuration, read once from the environment.
    private boolean smoke;
    /** Optional device exercise emits sound only; it cannot damage entities or change terrain. */
    private boolean audioQa;
    private double nextAudioProbe;
    private String scene;
    private String frontendScreen;
    private boolean frontendQa;
    private double smokeSeconds = MIN_SMOKE_SECONDS;
    private double[] shotMarks = new double[0];
    private String capturePrefix = "shot";

    // Session progress.
    /** Wall-clock origin of the session, so phases can be scheduled against it. */
    private double sessionStart;
    private int shotIndex;
    private int smokePhase;
    private boolean smokeSaveOk;
    private boolean smokeLoadOk;
    private String smokeGateFailure;
    /** Labelled captures a scripted scene requested, written after their frames render. */
    private final ArrayDeque<String> sceneCaptures = new ArrayDeque<>();
    private static final int MAX_SCENE_CAPTURES = 16;
    private boolean sceneSession;
    private boolean finishAfterCaptures;
    /** The native default-path route ({@code VEYLON_FRONTEND=route}), or null. */
    private FrontendRouteQa frontendRoute;
    private final Path smokeSave =
            AppPaths.dataDirectory().resolve("build/qa/smoke-save.dat");

    AutomatedRunDriver(Game game) {
        this.game = game;
    }

    /**
     * Reads the environment and puts the game into its starting state.
     *
     * @return true when this session needs suppressed mouse look; start-only
     *         manual apartment QA retains ordinary player controls
     */
    boolean configure() {
        smoke = System.getenv("VEYLON_SMOKE") != null;
        audioQa = "1".equals(System.getenv("VEYLON_AUDIO_QA"));
        scene = System.getenv("VEYLON_SCENE");
        String shotEnv = System.getenv("VEYLON_SHOT");
        frontendScreen = System.getenv("VEYLON_FRONTEND");
        frontendQa = frontendScreen != null && !frontendScreen.isBlank();
        boolean automated = smoke || (scene != null && !scene.isBlank())
                || (shotEnv != null && !shotEnv.isBlank()) || frontendQa;
        String selector = System.getenv("VEYLON_GENERATOR");
        int generator = configuredGenerator(selector, AppPaths.isolatedFromPlayerData());
        game.frontend.newWorldGenerator = generator;

        sceneSession = automated && !frontendQa;
        if (sceneSession) {
            generator = sceneGenerator(scene, selector, generator, AppPaths.isolatedFromPlayerData());
        }
        if (sceneSession) {
            game.newWorld(game.sessionSeed, true, generator,
                    UrbanApartmentQaScene.manualNavigation(scene) ? GameMode.SURVIVAL
                            : configuredGameMode(System.getenv("VEYLON_GAME_MODE")));
            game.qa.applyBenchmarkScene(scene);
            game.appState = Game.AppState.PLAYING;
            game.window.captureCursor(true, game.input);
        } else {
            game.appState = Game.AppState.TITLE;
            game.window.captureCursor(false, game.input);
            if (frontendQa) {
                String screen = frontendScreen.trim().toLowerCase(Locale.ROOT);
                if (FrontendRouteQa.names(screen)) {
                    frontendRoute = new FrontendRouteQa(game, this, screen,
                            configuredGameMode(System.getenv("VEYLON_GAME_MODE")));
                } else {
                    game.frontend.openForQa(screen);
                }
            }
        }

        if (smoke) {
            try {
                smokeSeconds = Math.max(MIN_SMOKE_SECONDS,
                        Double.parseDouble(System.getenv("VEYLON_SMOKE")));
            } catch (NumberFormatException ignored) {
                // An unparseable duration means "just run the default length".
            }
        }
        shotMarks = parseShotMarks(shotEnv);
        capturePrefix = resolveCapturePrefix();
        return automated && !UrbanApartmentQaScene.manualNavigation(scene);
    }

    /** Strict, locale-independent parsing is only invoked for automated worlds. */
    static GameMode configuredGameMode(String configured) {
        if (configured == null) return GameMode.SURVIVAL;
        try {
            return GameMode.fromId(configured.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("VEYLON_GAME_MODE must be survival or creative: " + configured,
                    invalid);
        }
    }

    /** The {@code VEYLON_GENERATOR} value that selects the urban development generator. */
    static final String URBAN_DEVELOPMENT_GENERATOR = "urban-dev";
    /** The {@code VEYLON_GENERATOR} value that selects the wilderness, NEW FRONTIER's legacy world. */
    static final String WILDERNESS_GENERATOR = "wilderness";

    /**
     * The QA selector of this session's new world, which NEW FRONTIER opens on and automated worlds
     * build: unset is the ordinary default, the abandoned city; {@code wilderness} the legacy world;
     * {@code urban-dev} the newest urban layout, today the default city itself.
     *
     * <p>{@code urban-dev} refuses to run unless {@code veylon.dataDir} points away from the
     * player's data directory, so a development layout can never be saved over a real save.
     * Invalid values fail explicitly, like {@code VEYLON_GAME_MODE}.
     *
     * @param isolatedDataDirectory {@link AppPaths#isolatedFromPlayerData()}
     */
    static int configuredGenerator(String configured, boolean isolatedDataDirectory) {
        if (configured == null || configured.isBlank()) {
            return World.DEFAULT_NEW_WORLD_GENERATOR;
        }
        String value = configured.trim().toLowerCase(Locale.ROOT);
        if (WILDERNESS_GENERATOR.equals(value)) {
            return World.WILDERNESS_NEW_WORLD_GENERATOR;
        }
        if (!URBAN_DEVELOPMENT_GENERATOR.equals(value)) {
            throw new IllegalArgumentException("VEYLON_GENERATOR must be " + WILDERNESS_GENERATOR + ", "
                    + URBAN_DEVELOPMENT_GENERATOR + " or unset: " + configured);
        }
        if (!isolatedDataDirectory) {
            throw new IllegalStateException("VEYLON_GENERATOR=" + URBAN_DEVELOPMENT_GENERATOR
                    + " needs an isolated data directory so it cannot replace a real save: run"
                    + " with -Dveylon.dataDir=<dir> (Gradle: -PveylonDataDir=build/qa/<name>)");
        }
        return UrbanRevision.DEVELOPMENT_GENERATOR;
    }

    /**
     * The world a scripted scene plays. A city tour ({@code urban_*}) and the save audit play the
     * session's world, and only from an isolated data directory, since they press F5 into the
     * ordinary slot path, as they always did behind {@code urban-dev}. Every other scene was made
     * for the wilderness and keeps it unless {@code VEYLON_GENERATOR} names a world.
     *
     * @param generator the session's world from {@link #configuredGenerator}
     */
    static int sceneGenerator(String scene, String selector, int generator, boolean isolatedDataDirectory) {
        if (scene == null || scene.isBlank()) {
            return generator;
        }
        String name = scene.trim().toLowerCase(Locale.ROOT);
        if (name.startsWith("urban_") || name.equals("save_audit")) {
            if (World.isUrbanGenerator(generator) && !isolatedDataDirectory) {
                throw new IllegalStateException("VEYLON_SCENE=" + name + " plays a city and saves into the"
                        + " slot: run with -Dveylon.dataDir=<dir> (Gradle: -PveylonDataDir=build/qa/<name>)");
            }
            return generator;
        }
        return selector == null || selector.isBlank() ? World.WILDERNESS_NEW_WORLD_GENERATOR : generator;
    }

    /** {@code VEYLON_SHOT="5,10"} captures the framebuffer at those elapsed seconds. */
    private static double[] parseShotMarks(String shotEnv) {
        if (shotEnv == null || shotEnv.isBlank()) {
            return new double[0];
        }
        String[] parts = shotEnv.split(",");
        double[] marks = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                marks[i] = Double.parseDouble(parts[i].trim());
            } catch (NumberFormatException e) {
                marks[i] = -1;
            }
        }
        return marks;
    }

    private String resolveCapturePrefix() {
        String tag = System.getenv("VEYLON_CAPTURE_TAG");
        if (tag != null && !tag.isBlank()) {
            return tag.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        }
        if (scene != null && !scene.isBlank()) {
            return scene.equals("urban_apartments") || scene.equals("urban_apartments_night")
                    || scene.equals("urban_apartments_lit_night")
                    ? scene + "_scripted_camera" : scene;
        }
        if (frontendQa) {
            return "frontend_" + frontendScreen.trim().toLowerCase(Locale.ROOT);
        }
        return "shot";
    }

    /** A scripted scene asks for a labelled capture of the frame about to render; inert headless. */
    void requestCapture(String label) {
        if ((sceneSession || frontendRoute != null) && sceneCaptures.size() < MAX_SCENE_CAPTURES) {
            sceneCaptures.add(label.replaceAll("[^A-Za-z0-9._-]", "_"));
        }
    }

    /** Closes a scripted scene's session once its queued captures are written; inert headless. */
    void finishScriptedRun() {
        finishAfterCaptures = sceneSession;
    }

    /** Captures any screenshot due by {@code now}, after the frame has rendered. */
    void captureDueScreenshots(double now) {
        if (!sceneCaptures.isEmpty()) {
            capture(capturePrefix + "_" + sceneCaptures.remove());
        } else if (finishAfterCaptures) {
            game.window.requestClose();
        }
        double elapsed = now - sessionStart;
        if (shotIndex >= shotMarks.length || elapsed < shotMarks[shotIndex]) {
            return;
        }
        capture(capturePrefix + "_" + (int) shotMarks[shotIndex] + "s");
        shotIndex++;
        if (shotIndex >= shotMarks.length && !smoke) {
            game.window.requestClose();
        }
    }

    private void capture(String name) {
        ScreenshotUtil.capture(game.window.framebufferWidth(), game.window.framebufferHeight(), name);
        System.out.println("[capture] name=" + name + " tag=" + capturePrefix
                + " scene=" + (scene == null ? "frontend" : scene)
                + " framebuffer=" + game.window.framebufferWidth()
                + "x" + game.window.framebufferHeight()
                + " particles=" + game.renderer.particlesDrawn
                + " particleSubmissions=" + game.renderer.particleDrawCalls
                + " drawCalls=" + (game.renderer.drawCalls + game.ui.drawCallsLastFrame())
                + " uiSubmissions=" + game.ui.drawCallsLastFrame()
                + " uiScale=" + game.ui.uiScale()
                + " triangles=" + game.renderer.trianglesRendered
                + " glErrors=" + game.window.glErrorCount()
                + " khrErrors=" + game.window.glDebugErrorCount());
    }

    /** Advances the scripted smoke session; inert outside a smoke run. */
    void advanceSmokeRun(double now) {
        double elapsed = now - sessionStart;
        if (frontendRoute != null) {
            frontendRoute.advance(elapsed);
        }
        if (smoke) game.creativeQa.sampleBody();
        if (!smoke || game.world == null || game.appState != Game.AppState.PLAYING) {
            return;
        }
        game.creativeQa.updateSmokeFlight(elapsed);
        if (audioQa && elapsed > nextAudioProbe) {
            nextAudioProbe = elapsed + 1;
            game.audio.scheduleThunder(game.player.pos.x + 60, game.player.pos.y, game.player.pos.z);
            for (int i = 0; i < 24; i++) game.audio.playFootstep(com.veylon.world.BlockType.GRASS, false);
            game.audio.playExplosion(game.player.pos.x + 12, game.player.pos.y - 3, game.player.pos.z);
            for (int i = 0; i < 3; i++) game.audio.playGunshot(i == 0,
                    game.player.pos.x + 12 + i, game.player.pos.y - 3, game.player.pos.z);
        }
        if (smokePhase == 0 && elapsed > 2.5) {
            smokePhase = 1;
            game.world.setBlock((int) game.player.pos.x + 2, (int) game.player.pos.y + 1,
                    (int) game.player.pos.z + 2, BlockType.TORCH, true);
            game.player.inventory.add(ItemType.HIDE_COAT, 1);
            game.qa.equipFromInventoryFirst(ItemType.HIDE_COAT);
            game.creativeQa.beforeSave();
            smokeSaveOk = SaveSystem.save(game, smokeSave);
            System.out.println("[smoke] isolated save=" + smokeSaveOk + " path=" + smokeSave);
        }
        if (smokePhase == 1 && elapsed > 4.0) {
            smokePhase = 2;
            if (audioQa) game.audio.scheduleThunder(game.player.pos.x + 500, game.player.pos.y, game.player.pos.z);
            smokeLoadOk = SaveSystem.load(game, smokeSave);
            game.creativeQa.afterLoad(smokeLoadOk);
            if (audioQa && game.audio.pendingWeatherSounds() != 0) {
                throw new IllegalStateException("Old-world weather audio survived load");
            }
            System.out.println("[smoke] isolated load=" + smokeLoadOk + " path=" + smokeSave);
        }
        if (smokePhase == 2 && elapsed > 5.0) {
            smokePhase = 3;
            igniteStressStack();
        }
        if (smokePhase == 3 && elapsed > 7.0) {
            smokePhase = 4;
            game.qa.beginSmokeFortressApproach(now);
        }
        if (elapsed > smokeSeconds) {
            game.window.pollGlErrors("smoke-gate");
            smokeGateFailure = game.qa.emitSmokeReport(smokeSeconds, smokeSaveOk, smokeLoadOk);
            game.window.requestClose();
        }
    }

    /** Seconds since the session began; drives showcase and capture scheduling. */
    double elapsed(double now) {
        return now - sessionStart;
    }

    /** Marks the wall-clock origin every scheduled phase is measured from. */
    void beginSession(double now) {
        sessionStart = now;
        game.qa.resetSmokeRun();
    }

    /** Stresses fire and storm together: a small wooden stack, then a torch to it. */
    private void igniteStressStack() {
        int fx = (int) game.player.pos.x + 4;
        int fz = (int) game.player.pos.z + 4;
        // A city's column top may be a tree crown; its plan knows the ground.
        int fy = (game.world.isUrban() ? game.world.urban.groundY(fx, fz)
                : game.world.surfaceHeight(fx, fz)) + 1;
        game.world.setBlock(fx, fy, fz, BlockType.LOG, true);
        game.world.setBlock(fx, fy + 1, fz, BlockType.LOG, true);
        game.world.setBlock(fx + 1, fy, fz, BlockType.PLANK, true);
        game.fire.ignite(game, fx, fy, fz);
        System.out.println("[smoke] ignited log stack at " + fx + "," + fy + "," + fz);
        game.weather.next = WeatherSystem.Weather.STORM;
        game.weather.blend = 0.6f;
    }

    /**
     * Fails the build if the smoke session found anything wrong. Called after
     * resource teardown, so GL errors raised by deletion are counted too — that
     * is a real class of leak and the last chance to see it.
     */
    void assertSmokeGatePassed() {
        if (!smoke) {
            return;
        }
        if (game.window.glErrorCount() != 0 || game.window.glDebugErrorCount() != 0) {
            smokeGateFailure = smokeGateFailure == null
                    ? "OpenGL errors observed during resource cleanup"
                    : smokeGateFailure + "OpenGL errors observed during resource cleanup; ";
        }
        if (smokeGateFailure != null) {
            throw new IllegalStateException("Graphics smoke gate failed: " + smokeGateFailure);
        }
    }
}
