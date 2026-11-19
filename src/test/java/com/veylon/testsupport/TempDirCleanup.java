package com.veylon.testsupport;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Deletes every {@code @TempDir} directory that {@link TrackedTempDirFactory} created, after the
 * test or class that owns it, with a bounded number of logged retries (0.9.1 defect B02).
 *
 * <p>Why: on the owner's Windows host a process with administrator rights, by every sign the
 * on-access antivirus, writes a transient copy ({@code NAME.tmp}) beside a freshly saved file
 * the moment a test deletes it and removes the copy a few milliseconds later. JUnit deletes a
 * directory's files and then the directory at once, so it can meet the copy and fail a green test
 * with {@code DirectoryNotEmptyException} or a sharing violation. Nothing in the JVM holds the
 * file: the cause is outside the process, so a retry, not a fix in the game, is the remedy.
 *
 * <p>An after-each or after-all callback runs before JUnit closes the context's store, where its
 * own cleanup lives, so this extension gets the first attempt. A directory it still cannot delete
 * after the last retry stays where it is, logged, for JUnit's unchanged cleanup to delete or to
 * report as a failure exactly as before. Cleanup failures are never swallowed. The extension is
 * registered for the whole suite through {@code META-INF/services} and extension autodetection.
 */
public final class TempDirCleanup implements AfterEachCallback, AfterAllCallback {

    /**
     * Pauses before each retry, about two seconds in all: hundreds of times the few milliseconds
     * the antivirus copy was seen to live, yet short enough that a genuinely held file fails fast.
     */
    static final long[] RETRY_DELAYS_MILLIS = {10, 20, 50, 100, 200, 500, 1000};

    private static final String LOG_PREFIX = "[temp-dir cleanup] ";

    /** Directories still to delete, keyed by the unique id of the context that owns them. */
    private static final Map<String, List<Path>> TRACKED = new ConcurrentHashMap<>();

    /** Deletes a directory tree once; throws whatever stopped it. */
    @FunctionalInterface
    interface Deleter {
        void delete(Path directory) throws IOException;
    }

    /** Waits before a retry. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    static void track(ExtensionContext owner, Path directory) {
        TRACKED.computeIfAbsent(owner.getUniqueId(), id -> new CopyOnWriteArrayList<>()).add(directory);
    }

    static boolean isTracked(Path directory) {
        return TRACKED.values().stream().anyMatch(directories -> directories.contains(directory));
    }

    @Override
    public void afterEach(ExtensionContext context) {
        deleteTrackedBy(context);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        deleteTrackedBy(context);
    }

    private static void deleteTrackedBy(ExtensionContext context) {
        List<Path> directories = TRACKED.remove(context.getUniqueId());
        if (directories == null) {
            return;
        }
        for (Path directory : directories) {
            deleteWithRetries(directory, TempDirCleanup::deleteTree, Thread::sleep, System.err::println);
        }
    }

    /**
     * Deletes {@code directory}, retrying after each pause of {@link #RETRY_DELAYS_MILLIS} while
     * it fails, and logs every failed attempt and the outcome. Never throws.
     *
     * @return the attempt that deleted the directory (1 when no retry was needed), or -1 when it
     *         was still present after the last retry or the wait was interrupted
     */
    static int deleteWithRetries(Path directory, Deleter deleter, Sleeper sleeper, Consumer<String> log) {
        for (int attempt = 1; ; attempt++) {
            IOException failure;
            try {
                deleter.delete(directory);
                if (Files.notExists(directory, LinkOption.NOFOLLOW_LINKS)) {
                    if (attempt > 1) {
                        log.accept(LOG_PREFIX + directory + ": deleted on attempt " + attempt);
                    }
                    return attempt;
                }
                failure = new IOException("still present after deletion");
            } catch (IOException e) {
                failure = e;
            }
            if (attempt > RETRY_DELAYS_MILLIS.length) {
                log.accept(LOG_PREFIX + directory + ": still present after " + attempt
                        + " attempts (" + failure + "); JUnit's own cleanup decides");
                return -1;
            }
            long pause = RETRY_DELAYS_MILLIS[attempt - 1];
            log.accept(LOG_PREFIX + directory + ": attempt " + attempt + " failed (" + failure
                    + "); left " + remainingNames(directory) + "; retrying in " + pause + " ms");
            try {
                sleeper.sleep(pause);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                log.accept(LOG_PREFIX + directory + ": interrupted; JUnit's own cleanup decides");
                return -1;
            }
        }
    }

    /**
     * Deletes a directory tree without ever following a link: a symbolic link is removed as an
     * entry, and a directory that resolves outside the tree (a junction or a linked directory)
     * is unlinked without visiting what it points to.
     */
    static void deleteTree(Path root) throws IOException {
        if (Files.notExists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Path realRoot = root.toRealPath();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                if (!directory.equals(root) && !resolvesInside(directory, realRoot)) {
                    Files.delete(directory);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                allowDeletion(directory, true);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (!attributes.isSymbolicLink()) {
                    allowDeletion(file, false);
                }
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException failure) throws IOException {
                if (failure instanceof NoSuchFileException) {
                    return FileVisitResult.CONTINUE;
                }
                throw failure;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.deleteIfExists(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean resolvesInside(Path directory, Path realRoot) {
        try {
            return directory.toRealPath().startsWith(realRoot);
        } catch (IOException brokenLink) {
            return false;
        }
    }

    /** Clears what would make a test's own file undeletable, as JUnit's cleanup does. */
    private static void allowDeletion(Path path, boolean directory) {
        File file = path.toFile();
        file.setReadable(true);
        file.setWritable(true);
        if (directory) {
            file.setExecutable(true);
        }
    }

    private static List<String> remainingNames(Path directory) {
        List<String> names = new ArrayList<>();
        try (Stream<Path> entries = Files.list(directory)) {
            entries.limit(8).forEach(entry -> names.add(entry.getFileName().toString()));
        } catch (IOException | RuntimeException unreadable) {
            names.add("<" + unreadable.getClass().getSimpleName() + ">");
        }
        return names;
    }
}
