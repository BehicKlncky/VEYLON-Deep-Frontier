package com.veylon.testsupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** The retry policy that keeps an antivirus's transient copies from failing the build (0.9.1 B02). */
class TempDirCleanupTest {

    @TempDir Path parent;

    @Test
    void aDirectoryTheFirstAttemptsCannotDeleteIsDeletedOnARetry() throws IOException {
        Path victim = saveDirectory();
        List<Long> pauses = new ArrayList<>();
        List<String> log = new ArrayList<>();
        int[] calls = {0};

        int attempt = TempDirCleanup.deleteWithRetries(victim, directory -> {
            if (++calls[0] <= 2) {
                // What JUnit met: the files are gone, but a foreign copy still holds the directory.
                throw new DirectoryNotEmptyException(directory.toString());
            }
            TempDirCleanup.deleteTree(directory);
        }, pauses::add, log::add);

        assertEquals(3, attempt, "deleted by the second retry");
        assertTrue(Files.notExists(victim));
        assertEquals(List.of(10L, 20L), pauses, "the first two pauses of the schedule");
        assertEquals(3, log.size(), "one line per failed attempt and one for the outcome: " + log);
        assertTrue(log.get(0).contains("attempt 1 failed") && log.get(0).contains("DirectoryNotEmptyException"),
                log.get(0));
        assertTrue(log.get(2).contains("deleted on attempt 3"), log.get(2));
    }

    @Test
    void aDirectoryThatNeverYieldsIsLeftForJUnitsCleanupAfterABoundedWait() throws IOException {
        Path victim = saveDirectory();
        List<Long> pauses = new ArrayList<>();
        List<String> log = new ArrayList<>();

        int attempt = TempDirCleanup.deleteWithRetries(victim, directory -> {
            throw new FileSystemException(directory.toString(), null,
                    "The process cannot access the file because it is being used by another process");
        }, pauses::add, log::add);

        assertEquals(-1, attempt, "gives up instead of throwing");
        assertTrue(Files.exists(victim), "left in place for JUnit's cleanup to delete or report");
        assertEquals(Arrays.stream(TempDirCleanup.RETRY_DELAYS_MILLIS).boxed().toList(), pauses);
        long waited = pauses.stream().mapToLong(Long::longValue).sum();
        assertTrue(waited <= 2_000, "a held file fails within about two seconds, not never: " + waited + " ms");
        assertTrue(log.getLast().contains("JUnit's own cleanup decides"), log.getLast());
    }

    @Test
    void theTreeIsDeletedWithoutFollowingALinkOutOfIt() throws IOException {
        Path outside = Files.createDirectories(parent.resolve("outside"));
        Path kept = Files.writeString(outside.resolve("kept.txt"), "not part of the tree");
        Path victim = saveDirectory();
        try {
            Files.createSymbolicLink(victim.resolve("link-to-outside"), outside);
        } catch (IOException | UnsupportedOperationException | SecurityException noLinks) {
            assumeTrue(false, "this host cannot create symbolic links: " + noLinks);
        }

        TempDirCleanup.deleteTree(victim);

        assertTrue(Files.notExists(victim, LinkOption.NOFOLLOW_LINKS), "the tree and the link are gone");
        assertTrue(Files.exists(kept), "what the link pointed to is untouched");
    }

    /** A directory as the save tests leave it: a nested folder with a written save. */
    private Path saveDirectory() throws IOException {
        Path victim = Files.createDirectories(parent.resolve("victim"));
        Files.createDirectories(victim.resolve("saves"));
        Files.write(victim.resolve("saves").resolve("veylon.sav"), new byte[] {1, 2, 3});
        return victim;
    }
}
