package com.veylon.testsupport;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** How parallel forks of one Gradle test task are kept apart (0.9.1 milestone 02). */
class WorkerDataDirectoryTest {

    private static final Path TASK = Path.of("build", "test-data", "test").toAbsolutePath().normalize();

    @Test
    void twoWorkersOfOneTaskNeverShareADirectory() {
        Path first = WorkerDataDirectory.forWorker(TASK.toString(), "7").orElseThrow();
        Path second = WorkerDataDirectory.forWorker(TASK.toString(), "8").orElseThrow();

        assertNotEquals(first, second);
        assertEquals(TASK.resolve("worker-7"), first, "a direct child of the task's directory");
        assertEquals(TASK, second.getParent());
    }

    @Test
    void anUnexpectedWorkerIdCannotLeaveTheTasksDirectory() {
        Path odd = WorkerDataDirectory.forWorker(TASK.toString(), "../../player").orElseThrow();

        assertEquals(TASK, odd.getParent(), odd + " stays inside " + TASK);
    }

    @Test
    void withoutAWorkerIdOrATaskDirectoryNothingChanges() {
        assertEquals(Optional.empty(), WorkerDataDirectory.forWorker(TASK.toString(), null),
                "outside Gradle the task's directory is kept");
        assertEquals(Optional.empty(), WorkerDataDirectory.forWorker(TASK.toString(), " "));
        assertEquals(Optional.empty(), WorkerDataDirectory.forWorker(null, "7"),
                "no isolated directory is invented when the build set none");
    }
}
