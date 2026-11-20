package com.veylon.testsupport;

import com.veylon.util.AppPaths;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Gives every Gradle test worker its own data directory (0.9.1 milestone 02).
 *
 * <p>{@code build.gradle} sets {@value AppPaths#DATA_DIR_PROPERTY} to
 * {@code build/test-data/<task>} for every {@code Test} task, so the parallel forks of one task
 * would otherwise share a save slot, settings files and QA saves. When this JVM's launcher
 * session opens, before any test class is discovered and before production code reads the
 * property (some paths, such as the settings files, are resolved once when their class
 * initializes), the property moves to {@code build/test-data/<task>/worker-<id>}, where
 * {@code <id>} is the worker id Gradle sets in {@value #GRADLE_WORKER_PROPERTY}. Without either
 * property, as outside Gradle, nothing changes. Registered through {@code META-INF/services}.
 */
public final class WorkerDataDirectory implements LauncherSessionListener {

    static final String GRADLE_WORKER_PROPERTY = "org.gradle.test.worker";

    private static boolean applied;

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        synchronized (WorkerDataDirectory.class) {
            if (applied) {
                return;
            }
            applied = true;
            forWorker(System.getProperty(AppPaths.DATA_DIR_PROPERTY), System.getProperty(GRADLE_WORKER_PROPERTY))
                    .ifPresent(directory -> System.setProperty(AppPaths.DATA_DIR_PROPERTY, directory.toString()));
        }
    }

    /**
     * The directory of one worker inside its task's directory, or empty when either is unknown.
     * Characters outside {@code [A-Za-z0-9._-]} in the id become {@code _}, so the result is
     * always a direct child of the task's directory.
     */
    public static Optional<Path> forWorker(String taskDirectory, String workerId) {
        if (taskDirectory == null || taskDirectory.isBlank() || workerId == null || workerId.isBlank()) {
            return Optional.empty();
        }
        String name = "worker-" + workerId.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        return Optional.of(Path.of(taskDirectory).toAbsolutePath().normalize().resolve(name));
    }
}
