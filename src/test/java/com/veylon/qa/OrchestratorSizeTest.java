package com.veylon.qa;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The composition root stays a composition root.
 *
 * <p>{@code docs/ARCHITECTURE.md} describes {@code Game} as orchestration only —
 * the loop, the app state machine, world setup, input routing and tick wiring —
 * and records that the 0.4.0 debt work took it from 4,381 lines to 1,462. That
 * is written down as a property of the design, and nothing was stopping it from
 * drifting back.
 *
 * <p>It drifts in one direction and for one reason. {@code Game} is the only
 * object that can reach everything, so any new feature that spans two systems
 * is easiest to write inline in a tick, and each one is individually small. The
 * 4,381-line version was not written by anyone deciding to write a 4,381-line
 * class.
 *
 * <h2>What to do when this fails</h2>
 *
 * <p>Extract, do not raise the ceiling. Every collaborator listed in
 * ARCHITECTURE.md came out of exactly this situation, and each kept its public
 * commands on {@code Game} as one-line delegates so native input, the HUD,
 * {@code EntityManager}, {@code CrateScreen} and the gameplay tests all keep
 * working. That is the pattern; the budget exists to make you follow it.
 *
 * <p>The limit is not sacred, but changing it is a design decision that belongs
 * in a commit message alongside a reason — not a number nudged up to make a
 * build green.
 */
class OrchestratorSizeTest {

    /**
     * Line budgets. {@code Game} is the one with a stated architectural
     * contract; the others are the largest classes in the codebase, held at
     * roughly their present size so the next one to sprawl is noticed while it
     * is still a refactor rather than a rewrite.
     */
    private static final Map<String, Integer> BUDGETS = new LinkedHashMap<>(Map.of(
            "com/veylon/Game.java", 1_000,
            "com/veylon/QaHarness.java", 1_500,
            "com/veylon/save/SaveSystem.java", 1_800,
            "com/veylon/settlement/SettlementManager.java", 1_500,
            "com/veylon/ai/FactionSystem.java", 1_400,
            "com/veylon/world/WorldGenerator.java", 1_300));

    @Test
    void theOrchestratorAndTheLargestClassesStayWithinTheirLineBudgets() throws IOException {
        Path sourceRoot = sourceRoot();
        List<String> over = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : BUDGETS.entrySet()) {
            Path file = sourceRoot.resolve(entry.getKey());
            assertTrue(Files.exists(file), () -> "budgeted file is missing: " + file
                    + " — if it moved or was split, update the budget map to match");
            long lines = countLines(file);
            if (lines > entry.getValue()) {
                over.add(String.format("%s: %d lines against a %d budget",
                        entry.getKey(), lines, entry.getValue()));
            }
        }
        assertTrue(over.isEmpty(), () -> """
                These classes are over their line budgets:
                  %s

                Extract; do not raise the ceiling. Game in particular is documented
                in docs/ARCHITECTURE.md as orchestration only, and every collaborator
                listed there came out of exactly this situation -- each keeping its
                public commands on Game as one-line delegates so every existing
                caller kept working.

                If a budget genuinely should move, that is a design decision and
                belongs in a commit message with a reason.""".formatted(
                String.join("\n  ", over)));
    }

    @Test
    void theBudgetsPointAtRealFiles() {
        // A budget map full of typos would pass the test above by checking
        // nothing, so prove the paths resolve and the counter counts.
        Path game = sourceRoot().resolve("com/veylon/Game.java");
        assertTrue(Files.exists(game), "Game.java must be where the budget says it is");
        assertTrue(countLinesUnchecked(game) > 200,
                "the line counter is not counting: " + game);
    }

    /**
     * Locates {@code src/main/java} from the working directory, which Gradle
     * sets to the project root but an IDE may not.
     */
    private static Path sourceRoot() {
        Path candidate = Path.of("src", "main", "java");
        if (Files.isDirectory(candidate)) {
            return candidate;
        }
        Path fromModule = Path.of("..").resolve(candidate);
        assertTrue(Files.isDirectory(fromModule),
                "cannot find src/main/java from " + Path.of("").toAbsolutePath());
        return fromModule;
    }

    private static long countLines(Path file) throws IOException {
        try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return lines.count();
        }
    }

    private static long countLinesUnchecked(Path file) {
        try {
            return countLines(file);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + file, e);
        }
    }
}
