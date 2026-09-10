package com.veylon;

import com.veylon.gfx.ScreenshotUtil;
import com.veylon.item.ItemType;
import com.veylon.save.SaveSystem;
import com.veylon.simulation.WeatherSystem;
import com.veylon.util.AppPaths;
import com.veylon.world.BlockType;

import java.nio.file.Path;
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
    private final Path smokeSave =
            AppPaths.dataDirectory().resolve("build/qa/smoke-save.dat");

    AutomatedRunDriver(Game game) {
        this.game = game;
    }

    /**
     * Reads the environment and puts the game into its starting state.
     *
     * @return true when this is an automated session, which suppresses mouse
     *         look so a capture is not steered by a stray cursor
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

        if (automated && !frontendQa) {
            game.newWorld(game.sessionSeed, true);
            game.qa.applyBenchmarkScene(scene);
            game.appState = Game.AppState.PLAYING;
            game.window.captureCursor(true, game.input);
        } else {
            game.appState = Game.AppState.TITLE;
            game.window.captureCursor(false, game.input);
            if (frontendQa) {
                game.frontend.openForQa(frontendScreen.trim().toLowerCase(Locale.ROOT));
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
        return automated;
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
            return scene;
        }
        if (frontendQa) {
            return "frontend_" + frontendScreen.trim().toLowerCase(Locale.ROOT);
        }
        return "shot";
    }

    /** Captures any screenshot due by {@code now}, after the frame has rendered. */
    void captureDueScreenshots(double now) {
        double elapsed = now - sessionStart;
        if (shotIndex >= shotMarks.length || elapsed < shotMarks[shotIndex]) {
            return;
        }
        ScreenshotUtil.capture(game.window.framebufferWidth(),
                game.window.framebufferHeight(),
                capturePrefix + "_" + (int) shotMarks[shotIndex] + "s");
        System.out.println("[capture] tag=" + capturePrefix
                + " scene=" + (scene == null ? "frontend" : scene)
                + " framebuffer=" + game.window.framebufferWidth()
                + "x" + game.window.framebufferHeight()
                + " particles=" + game.renderer.particlesDrawn
                + " particleSubmissions=" + game.renderer.particleDrawCalls
                + " drawCalls=" + (game.renderer.drawCalls + game.ui.drawCallsLastFrame())
                + " triangles=" + game.renderer.trianglesRendered
                + " glErrors=" + game.window.glErrorCount()
                + " khrErrors=" + game.window.glDebugErrorCount());
        shotIndex++;
        if (shotIndex >= shotMarks.length && !smoke) {
            game.window.requestClose();
        }
    }

    /** Advances the scripted smoke session; inert outside a smoke run. */
    void advanceSmokeRun(double now) {
        double elapsed = now - sessionStart;
        if (!smoke || game.world == null || game.appState != Game.AppState.PLAYING) {
            return;
        }
        if (audioQa && elapsed > nextAudioProbe) {
            nextAudioProbe = elapsed + 1;
            for (int i = 0; i < 3; i++) game.audio.playGunshot(i == 0,
                    game.player.pos.x + 12 + i, game.player.pos.y - 3, game.player.pos.z);
        }
        if (smokePhase == 0 && elapsed > 2.5) {
            smokePhase = 1;
            game.world.setBlock((int) game.player.pos.x + 2, (int) game.player.pos.y + 1,
                    (int) game.player.pos.z + 2, BlockType.TORCH, true);
            game.player.inventory.add(ItemType.HIDE_COAT, 1);
            game.qa.equipFromInventoryFirst(ItemType.HIDE_COAT);
            smokeSaveOk = SaveSystem.save(game, smokeSave);
            System.out.println("[smoke] isolated save=" + smokeSaveOk + " path=" + smokeSave);
        }
        if (smokePhase == 1 && elapsed > 4.0) {
            smokePhase = 2;
            smokeLoadOk = SaveSystem.load(game, smokeSave);
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
        int fy = game.world.surfaceHeight(fx, fz) + 1;
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
