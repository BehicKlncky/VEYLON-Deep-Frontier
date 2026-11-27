# VEYLON 0.9.1 "Safe Saves" — Progress

One section per milestone of the 0.9.1 prompt pack (`build/release-roadmap/v0.9.1-safe-saves/`,
ignored by Git). Decisions, scope and the defect ledger are in [DESIGN.md](DESIGN.md). Resume at
the first milestone below that is not complete.

| Milestone | Status | Commits |
| --- | --- | --- |
| 01 Release plan and defect audit | Complete (documentation only) | `95d09c5`, `1aadd19` |
| 02 Test reliability and continuous integration | Complete; the verify workflow's first Windows run passed with the push of 2026-11-20 (recorded in 05) | `91975e9`, `ec4d960`, `6517ef0`, `0252fbd`, `bfb0176`, `8b87cd6` |
| 03 Current-version save epoch | Complete | `ce4686d`, `843dce8`, `14ca773`, `3d98b70`, `6bae538`, `f3fe760`, `13486ca`, `2600134`, `0fbc816`, `71972aa`, `f3aed69`, `369d65e`, `3063d13` |
| 04 Three save slots and backups | Complete | `25e899e`, `142aace`, `51956a2`, `048e2d9`, `896340a`, `88d00ed`, `7357680`, `8dcab51`, `73f53c8` |
| 05 Autosave | Complete | `2b9ce42`, `efc99ec`, `e487e6a`, `828a184`, `0a22a7c`, `1a11340`, `90834e0`, `759b3e7`, `687f3d3` |
| 06 Session and control comfort | Complete | `91e576d`, `5909555`, `f9ea0e8`, `1573313`, `c652200`, `68a0b5f`, `c36ed1b`, `3cb57cf`, `72534e8`, `bb17c31` |
| 07 Forgotten defects sweep | Complete | `dea5189`, `323ee57`, `77517ce`, `1508aef`, `3958deb`, `422e2a9`, `9b485dd`, `42d3e53`, `79aee28`, `9b17061`, `56648d9`, `df85b5d`, `ebff3a8`, `010a60d`, `b181761`, `1de9ae8`, `dc3ea84`, `5e5044e`, `a7a2695` |
| 08 Day temperature curve | Complete | `3fc3570`, `132ff76`, `64b20a6`, `bbc497f`, `5694a20`, `a7ddf7b`, `23e50bc` |
| 09 Integration and release preparation | Complete: 0.9.1 is prepared for release; merging, tagging and publishing are the owner's | `388d087`, `bc6e81a`, `06675dc`, `ab11925`, `8c37958`, `1ffc375`, `a565ae3`, `079954b`, `1c96e10`, then the records commit that adds this section |

## Milestone 01 — Release plan and defect audit

**Status:** complete on 2026-11-19. Documentation only: no production or test code changed.
**Base:** `main` at `4b6bed9`, clean and equal to the remote `main`.

### What was done

1. **The owner's edits.** The save policy (`b1d4127`, 2026-11-06), the canon draft (`98e6e3d`)
   and its approval (`4b6bed9`, both 2026-11-19) were already committed and on the remote
   `main`, so nothing of theirs needed committing. As its own change, the "Read when relevant"
   table in `AGENTS.md` gained a row that sends anything a player reads to
   `docs/lore/CANON.md` (`95d09c5`).
2. **Records.** Created [the roadmap index](../README.md) with O1–O9, [DESIGN.md](DESIGN.md)
   and this file.
3. **Ledger.** B01–B14 are all still present in current source (DESIGN §5); none was already
   fixed. B10 is a maintenance defect and B11 a coverage gap; B13 is confirmed in source but
   was never captured. The search added B15 (negative health on the HUD after an overkill hit,
   owner 07) and B16 (DEVELOPING's stale "886 tests, ~4 min", owner 02), and found two recorded
   leftovers already fixed (`2749358`, `bdecf6f`).
4. **Search.** README "Known limitations", DEVELOPING "Known rough edges", and the phrases
   "pre-existing", "not fixed", "left for" and "still open" in `docs/urban-survival/` and
   `docs/engineering/`, widened to "predate", "seen but not changed", "not changed here",
   "unchanged here" and "open finding". Each item was classified under D-08: defects went to
   the ledger, limits to DESIGN §6, and the 0.8.x questions to DESIGN §7 as Q-01–Q-04.
5. **Baseline build**, below.
6. **Save code O1 makes dead**, listed for 03 in DESIGN §8; nothing was deleted.

### Baseline build: the before-figure for 02

- **Command**, from the repository root in Git Bash with `JAVA_HOME` set to
  `build/tools/jdk25/jdk-25.0.4.1+1` (Temurin 25.0.4.1):
  `./gradlew.bat build --rerun-tasks --console=plain > build/v091-m01-baseline-build.log 2>&1`.
  D-09 explains `--rerun-tasks`. The source tree was `4b6bed9`'s; the documentation commits
  made during the run change no build input.
- **Result:** BUILD SUCCESSFUL in 17m 50s (wall clock 1,070 s, 18:13:46 to 18:31:36 +03:00).
  All 10 actionable tasks ran.
- **Tests:** 2,365 in 305 classes. 0 failures, 0 errors, 1 skipped: the assumption in
  `util.AppPathsTest.aLinkToThePlayersDirectoryIsNotIsolated`, because this host lacks the
  privilege to create symbolic links. No `@TempDir` cleanup failure occurred. B02 is
  intermittent, so one clean run neither reproduces nor clears it.
- **Where the time goes:**
  - The `test` task ran from 18:14:10 to the end of the build in one worker, and the per-class
    times add up to 1,040 s.
  - One class takes over 60 s, 23 take 10–60 s and 281 take under 10 s.
  - The slowest are `save.UrbanSaveCorruptionTest` (60.3 s), `UrbanSliceRouteTest` (54.6 s),
    `UrbanEconomyAuditTest` (45.8 s), `entity.BodyCombustionTest` (31.4 s),
    `world.urban.CivicCatalogueTest` (25.7 s), `AllLivingBlastDeathTest` (25.6 s),
    `UrbanCatalogueQaSceneTest` (25.5 s) and `UrbanCommsTest` (21.8 s).
- **Host:** the owner's Windows 11 machine, 12 logical processors, Gradle 9.1.0 with
  `org.gradle.jvmargs=-Xmx2G`.
- **Comparison:** at `41dbfd6` on 2026-11-06 the same command took 17m 48s with the same counts
  and failed on one cleanup (`build/roadmap-review-build.log`).

### Changed paths

- `AGENTS.md`: one row in "Read when relevant" (`95d09c5`).
- New: `docs/roadmap/README.md`, `docs/roadmap/v0.9.1/DESIGN.md`,
  `docs/roadmap/v0.9.1/PROGRESS.md`.
- Ignored evidence: `build/v091-m01-baseline-build.log`.

### Validation

- `git status` and `git log` at the start. `git ls-remote origin refs/heads/main` returned
  `4b6bed9`, and `git merge-base --is-ancestor` placed `b1d4127` and `98e6e3d` under it.
- Every relative link and anchor in the three records resolves. Every source path DESIGN names
  exists, and every cited documentation line range was re-read.
- `git diff --check` is clean. `AGENTS.md` keeps its CRLF working copy, and the new records are
  LF like `docs/lore/CANON.md`.
- The baseline build above. No native run was needed, because nothing is drawn differently.
- Not run: `graphify update`, which AGENTS.md skips for documentation-only work.

### Limitations

- The ledger's line numbers are as of `95d09c5`.
- B04 has no repository record; it comes from the 0.8.0 rendering work's session notes, and
  the source confirms it.
- B12 is confirmed by reading only, so 07 should reproduce it in a test before fixing it.
- B13 was never captured, so 07 needs a native capture of the death card after a lethal blast.
- B02 did not reproduce in the single baseline run.

### Next: milestone 02

Start with B02: reproduce the cleanup failure and find its cause.

- **Failures so far.** 84 test files use `@TempDir`, and nothing configures its cleanup. Every
  recorded failure was in a `com.veylon.save` class (`UrbanEcologySaveTest`,
  `UrbanEarlierRevisionSaveTest`, `FixtureStatesSectionTest`, `UrbanContainersSectionTest`),
  mostly while other builds shared the machine.
- **Data directory.** `build.gradle` lines 103-111 give each `Test` task one `veylon.dataDir`
  (`build/test-data/<task>`). `util/AppPaths.java:17` reads it, `save/SaveSystem.java:108`
  resolves the static `SAVE_PATH` from it, and `SaveSlotIsolationTest` pins it.
- **Forks.** `maxParallelForks` is unset, so the suite runs in one worker.
- **CI.** The only workflow is `.github/workflows/release-build.yml`, which runs on `v*` tags,
  `release-recovery/v*` branches and manual dispatch.
- **B16.** Fix it when DEVELOPING's running-tests text is rewritten.
- **Measurement.** Compare 02's three `build --rerun-tasks` runs with the 17m 50s above.

## Milestone 02 — Test reliability and continuous integration

**Status:** complete on 2026-11-19. One gate is pending: the verify workflow's first run waits
until the owner pushes.
**Base:** `main` at `1aadd19`, clean. 01's two commits (`95d09c5`, `1aadd19`) are local and not
pushed. No production code changed.

### What was done

1. **B02 traced outside the JVM.** The cleanup failure was reproduced without JUnit or the game.
   It comes from a process with administrator rights, by every sign the antivirus, that copies a
   freshly saved file as it is deleted (below; D-10).
2. **B02 fixed.** A bounded, logged retry runs before JUnit's own cleanup, which still runs and
   still reports (D-10).
3. **A data directory per worker** (D-11), extending `SaveSlotIsolationTest`.
4. **Parallel forks** with a 1 GiB heap, chosen from measurement (D-12).
5. **No quick task** (D-14).
6. **A verify workflow** for pushes and pull requests (D-13). `release-build.yml` is unchanged.
7. **Documents.** [DEVELOPING](../../DEVELOPING.md) states no test count (B16) and has a new
   section, "Test workers, isolation and CI". DESIGN gains D-10 to D-15, and D-15 tells 04 about
   foreign files in save folders.

### B02: reproduction and cause

The probes and their outputs are in `build/v091-m02-b02/` (ignored): `TempDirStress.java` with
`stress.log`, `ExclusiveProbe.java` with `exclusive.log`, `ForeignFileProbe.java` with
`foreign.log`, and `RmProbe.java` with `restart-manager.log`. All are plain JDK programs, run
with the build's Temurin 25.

- **The pattern.** Each iteration makes a directory and saves into it as `SaveSystem.save` does:
  a sibling temporary file, `FileChannel.force`, then an atomic move over the target. Then it
  deletes the directory with a copy of JUnit 5.11.4's algorithm, which deletes the files and
  then the directory at once.
- **Results** (400 iterations, one thread):

  | Variant | Directories that failed to delete |
  | --- | --- |
  | The save tests' pattern: three flushed saves to one path, reads and a plain write | 12, in the system temporary directory; 12 under `build/tmp` |
  | One flushed save | 1 |
  | Two flushed saves to one path | 30 |
  | Two flushed saves to two names | 23 |
  | Two flushed saves, then the save moved out before deleting | 20: 18 directories refused deletion, and in 2 more the walk met a file this process never wrote |
  | Plain writes, or two moves over one path without a flush | 0 |
  | The save tests' pattern with a 30 ms pause before deleting | 0 of 200 |

  Apart from the two foreign-file cases, each failure was a `DirectoryNotEmptyException` on
  the directory itself, and its listing was empty a moment later. An immediate retry succeeded
  in all but three cases, and those succeeded within 5 ms. The foreign files were
  `SAVE.SAV.tmp` and `VEYLON-SAVE-<n>.TMP.tmp`: this process's file names, upper-cased, with
  `.tmp` added. Deleting them failed because "the process cannot access the file because it is
  being used by another process".
- **Not this JVM.** Production and test code close every stream they open, and nothing starts a
  thread. Right after two saves, an exclusive open of the save, which allows no sharing,
  succeeded in 300 of 300 iterations. Yet in 7 of them the directory still refused deletion for
  up to 0.8 ms after its files were gone.
- **What appears.** After two saves, a watcher saw no foreign file in 30 ms in 300 of 300
  iterations. Deleting the save then made `SAVE.SAV.tmp` appear beside it in 269 of 300
  iterations, and in 256 of 300 after a single save. The printed samples first saw it 0.3–5.6 ms
  after the delete, and no copy stayed longer than 5.9 ms. When its owner could be read, it was
  `BUILTIN\Administrators`; the files this process writes belong to the user.
- **Who.** The Restart Manager named no user-mode process holding the save, which fits a
  kernel-mode file-system filter. Kaspersky (`avp.exe`) is the host's only active antivirus,
  and Windows Defender's real-time protection is off. The filter itself was not identified,
  since that needs administrator tools.
- **Why the save tests.** They leave freshly saved files in their temporary directories, and
  JUnit deletes those the moment a test ends, inside that window. Load probably lengthens the
  window: the recorded failures came while several builds shared the machine.
- **In the suite.** The eight measured runs below needed the retry once. At three workers,
  `entity.BodyFragmentPhysicsTest` met an empty directory that could not be deleted, and the
  second attempt deleted it. Before this milestone that run would have failed.

### The fix (D-10)

- `src/test/java/com/veylon/testsupport/TrackedTempDirFactory.java` is the suite's default
  `@TempDir` factory (`junit.jupiter.tempdir.factory.default` in
  `src/test/resources/junit-platform.properties`). It creates each directory as JUnit's
  standard factory does, and records it when its cleanup mode is `ALWAYS`.
- `TempDirCleanup` is an after-each and after-all extension, registered through
  `META-INF/services` and extension autodetection. These callbacks run before JUnit closes the
  context's store, where its cleanup lives. The extension deletes the recorded directories with
  up to seven retries after pauses of 10, 20, 50, 100, 200, 500 and 1,000 ms. Each failed
  attempt is logged as `[temp-dir cleanup]` to the test's standard error, with what is left in
  the directory. A directory still present is left to JUnit's unchanged cleanup, which deletes
  or reports it. The deletion never follows a symbolic link, and it unlinks a junction without
  entering it: on this host a real junction to an outside directory was removed and its
  target's file kept.
- `TempDirCleanupTest` pins the retry, the give-up and the link rule; the link case is skipped
  on this host, which cannot create symbolic links. `TempDirCleanupWiringTest` proves that the
  factory and the extension are in force for the whole suite.

### A data directory per worker (D-11)

`testsupport/WorkerDataDirectory.java` is a JUnit launcher session listener. When Gradle opens
the session in a worker, before discovery, it moves `veylon.dataDir` from
`build/test-data/<task>` to `build/test-data/<task>/worker-<id>`. `build.gradle` empties the
task's directory when the task starts. `SaveSlotIsolationTest` now also checks that the running
worker uses its own directory and that `SaveSystem.SAVE_PATH` resolves inside it.
`WorkerDataDirectoryTest` checks that two workers never share a directory and that an odd id
cannot leave the task's.

### Forks and heap (D-12)

`.\gradlew.bat test --rerun -PveylonTestForks=N --console=plain`, one run at a time on the idle
host. The GC logs come from an init script in the session scratchpad (`-Xlog:gc,gc+init`) and
are in `build/v091-m02-gc/`; the logs are `build/v091-m02-forks-*.log`, and the summary is
`build/v091-m02-forks-summary.txt`.

| Workers | Heap | Test task | GC pressure |
| --- | --- | --- | --- |
| 1 (01's baseline) | 512 MiB | 17 min 26 s, inside the 17m 50s build | not measured |
| 3 | 512 MiB | 7 min 4 s | one worker at the ceiling |
| 4 | 512 MiB | 5 min 34 s | evacuation failures in two workers; one at the ceiling |
| 6 | 512 MiB | 4 min 55 s | evacuation failures in two workers; one at the ceiling |
| 6 | 1 GiB | 4 min 49 s | none |
| 8 | 512 MiB | 4 min 25 s | evacuation failures in three workers; one at the ceiling |
| 8 | 1 GiB | 4 min 27 s | none |
| 10 | 512 MiB | 4 min 22 s | evacuation failures in two workers; one at the ceiling |
| 12 | 512 MiB | 4 min 28 s | evacuation failures in two workers; one at the ceiling |

- Every run had 2,375 tests in 308 classes, no failure, and the two symbolic-link skips.
- "At the ceiling" means the worker filled its 512 MiB, with G1 evacuation failures and two or
  three compacting full collections. At 1 GiB no worker had either, and the heaviest peaked at
  0.72–0.75 GiB after a young collection.
- Contention grows with the worker count. The class times add up to 1,068 s at three workers
  and 1,728 s at eight, and the slowest class goes from 55 s to 95 s.
- Gradle deals the classes to the workers in turn, so the busiest worker sets the time. At eight
  workers they ran between 167 and 264 s.
- The default is two thirds of the logical processors, 8 here, with a 1 GiB heap.
  `-PveylonTestForks=0` and `=abc` fail the build with
  `veylonTestForks must be a positive whole number`, and `=3` configures.

### The verify workflow (D-13)

`.github/workflows/verify.yml` was parsed with SnakeYAML 2.0 from the Gradle distribution, with
duplicate keys refused. The check covered the triggers, the cron, each job's `runs-on`, `if`,
timeout and steps, and each step's single `uses` or `run`. Both jobs run
`gradlew build --console=plain`, the real portable build. `release-build.yml` parses unchanged.
The first run on GitHub is pending, because nothing is pushed. GitHub's standard hosted runners
for a public repository have four processors on Windows and three on macOS arm64, which gives
two workers each.

### Acceptance builds

`./gradlew.bat build --rerun-tasks --console=plain`, three times in a row on the idle host, from
the repository root in Git Bash with `JAVA_HOME` set to `build/tools/jdk25/jdk-25.0.4.1+1`. The
tree was `6517ef0`'s; the later commits change no build input. The logs are
`build/v091-m02-acceptance-{1,2,3}.log` and the summary is `build/v091-m02-acceptance-summary.txt`.

| Run | Clock (+03:00) | Result | Against 01's 17m 50s |
| --- | --- | --- | --- |
| 1 | 20:25:58–20:30:56 | BUILD SUCCESSFUL in 4m 54s | 3.6 times as fast |
| 2 | 20:30:56–20:35:45 | BUILD SUCCESSFUL in 4m 47s | 3.7 times as fast |
| 3 | 20:35:45–20:40:09 | BUILD SUCCESSFUL in 4m 21s | 4.1 times as fast |

- Each run executed all 10 actionable tasks. Each ran 2,375 tests in 308 classes, with no failure
  or error and the two symbolic-link skips.
- No run had a cleanup failure, and none needed a retry.
- Each started from an emptied `build/test-data/test`. Afterwards the directory held only that
  run's worker directories, for example `worker-10`, `-11`, `-12`, `-14` and `-17` after run 2.
  Only five of the eight workers had written a file.
- B02 was intermittent. Three clean runs cannot prove it gone; the reproduction above and the
  retry seen at three workers are the evidence for the fix.

### Changed paths

- New, test code: `src/test/java/com/veylon/testsupport/` (`TrackedTempDirFactory`,
  `TempDirCleanup`, `WorkerDataDirectory` and their three test classes),
  `src/test/resources/junit-platform.properties`, and two service files under
  `src/test/resources/META-INF/services/`.
- Changed: `src/test/java/com/veylon/save/SaveSlotIsolationTest.java`; `build.gradle` (the
  launcher dependency, the data-directory reset, the fork count and the heap).
- New: `.github/workflows/verify.yml`.
- Documents: `docs/DEVELOPING.md`, `docs/roadmap/v0.9.1/DESIGN.md` and this file.
- Ignored evidence under `build/`: `v091-m02-b02/`, `v091-m02-gc/`, `v091-m02-forks-*`,
  `v091-m02-c1-build.log`, `v091-m02-c2-build.log`, `v091-m02-acceptance-*`,
  `v091-m02-focused-*.log`, `v091-m02-save-isolation.log`, `v091-m02-calibrated.log` and
  `v091-m02-graphify-update.log`.

### Validation

- **Focused checks while iterating**, `.\gradlew.bat test --tests "<class>" --console=plain`:
  the `testsupport` classes, `save.SaveSlotIsolationTest`, `util.AppPathsTest`,
  `SaveAuditQaSceneTest` and `save.UrbanEcologySaveTest`, all passing
  (`build/v091-m02-focused-{1,2,3}.log`).
- **The fork-count property.** `test --dry-run -PveylonTestForks=0`, and `=abc`, fail with
  `veylonTestForks must be a positive whole number`; `=3` configures.
- **The intermediate trees.** Each was built in a detached worktree holding its exact tree, and
  the tree hash was compared with the main index before committing. Neither has forks, so both
  ran in one worker; they ran side by side.
  - `91975e9`: `./gradlew.bat build --console=plain`, BUILD SUCCESSFUL in 18m 9s, 2,371 tests
    in 307 classes, two skips (`build/v091-m02-c1-build.log`).
  - `ec4d960`: BUILD SUCCESSFUL in 18m 20s, 2,375 tests in 308 classes, two skips. Its tests
    wrote only to the worktree's own `worker-1` directory (`build/v091-m02-c2-build.log`).
  - Both worktrees were removed afterwards.
- **The fork measurements** and **the three acceptance builds**, above.
- **The save and isolation classes** on the final tree: `.\gradlew.bat test --tests
  "com.veylon.save.*" --tests "com.veylon.util.AppPathsTest" --tests
  "com.veylon.SaveAuditQaSceneTest" --tests "com.veylon.testsupport.*" --console=plain`. BUILD
  SUCCESSFUL in 1m 28s: 157 tests in 38 classes, two skips (`build/v091-m02-save-isolation.log`).
- **The calibrated tasks:** `.\gradlew.bat rainPerformanceTest cityPerformanceTest --info
  --console=plain`, BUILD SUCCESSFUL in 27s; rain passed 2 of 2 and the city profile 5 of 5. Each
  ran in one worker (`Gradle Test Executor 34` and `35`): rain at Gradle's default `-Xmx512m`,
  the city at its own `-Xmx2g`, each with its task's `veylon.dataDir`
  (`build/v091-m02-calibrated.log`). `performanceTest` was not run, because its durable-save
  gate fails on this host whatever the code (DESIGN §6).
- **The workflow**, parsed and checked as above; not run on GitHub.
- **The B02 probes**, above, and the junction check by hand.
- **Documents:** `git diff --check` is clean. Every relative link in DESIGN, this file and
  DEVELOPING's new section resolves, and every path named exists.
- `graphify update . --no-cluster` after the code commits: 12,273 nodes. `graphify-out/` is
  ignored by Git.
- **Not run:** a native game session, since nothing a player sees changed; the GitHub workflow.

### Commits

| Commit | Subject | Content |
| --- | --- | --- |
| `91975e9` | fix(qa): retry temp-directory cleanup past antivirus copies | B02 (D-10) |
| `ec4d960` | test(qa): give every test worker its own data directory | D-11 |
| `6517ef0` | build(qa): run the portable suite in parallel workers | D-12 |
| `0252fbd` | build(ci): verify every push and pull request | D-13 |
| `bfb0176` | docs(developing): describe test workers, cleanup retries and CI | B16 and the new section |
| the records commit | docs(roadmap): record milestone 02 of 0.9.1 | This section and DESIGN; 03 records its hash |

No commit carries a co-author line. Nothing is pushed. With the records commit, `main` is eight
commits ahead of the remote `main` (`4b6bed9`): 01's two and these six.

### Limitations and pending gates

- **Pending:** the first run of `verify.yml` on GitHub, both jobs, until the owner pushes. Its
  macOS run time is not measured yet.
- The antivirus is identified by inference, as above. A copy that outlived the 1.9 s of retries
  would still fail the test, reported by JUnit as before.
- `TempDirFactory` is an experimental JUnit API, so a JUnit upgrade must recheck D-10.
- Two tests skip on this host, which cannot create symbolic links: `AppPathsTest`'s and
  `TempDirCleanupTest`'s.
- Gradle deals classes to workers without regard to their length. Run times can therefore vary
  with the class mix, as the acceptance builds show.

### Next: milestone 03

Start from DESIGN §8, the save code and tests that O1 makes dead, and B11, the writer cap's
missing test, then the epoch (D-01). D-15 is for 04.

## Milestone 03 — Current-version save epoch

**Status:** complete on 2026-11-20.
**Base:** `main` at `8b87cd6`, clean. 01's and 02's eight commits are local and not pushed.

### What was done

1. **The header and refusal (D-16).** Saves begin with format 4: `VEYL`, the format, the save
   epoch (1), the seed and the generator (`save.SaveHeader`). `SaveSystem.open` judges those
   bytes first, so another format or epoch builds no world, never reaches the verifier pass,
   leaves the live world alone and never touches the file. `LoadResult` gives the player a
   sentence on the title and a short line for F9's log, and the console the technical reason.
2. **Strict reading within the epoch (D-18).** The extension tail, the eleven sections every
   save holds and `urban.meta` are required, each section in the one version the writer
   writes; an unknown section id, `world.bodies` version 1, an unknown body family, piece or
   joint table, another content revision and an unknown affliction or rack ordinal are refused.
3. **One record of the settled pieces (D-19):** `world.fragments` is no longer written or read.
4. **Old-save code and fixtures removed (D-20, DESIGN §8 outcome):** the v2 migration, the
   version branches, `legacyBone`, the content-1 absence default and adoption, the two quest
   overloads without context, and the authentic saves of 0.2.0, 0.3.0, 0.8.1 and urban packages
   06 and 20 with every test that loaded them. Refusal tests build the headers in code.
5. **B11:** the writer's size limit goes through a package-private seam, and a test proves a
   snapshot one byte over it is refused while the last save stays byte for byte.
6. **The epoch guard (D-17).** `SaveEpochGuardTest` digests five inputs: a sample save of each
   world kind, the stored enum tables, and both world kinds' generation through
   `WorldFingerprints`, which `WorldGeneratorFingerprintTest` now shares. Epoch 1 is recorded,
   unreleased, with the digest `6a9644da92c446dcf8bbade5b05803e049760f85feb79d31aead2d5618e65408`;
   09 adds it to `RELEASED`. The fingerprint test's advice follows O1.
7. **Native refusal route:** `VEYLON_FRONTEND=route-refusal` (below). Its first run found F9's
   sentence cut short in the HUD's log panel, which `2600134` fixed with a log line of at most
   32 characters.
8. **Documents:** ARCHITECTURE's save section ("The save epoch", "Saves of other versions"),
   DEVELOPING ("Saves and the save epoch"), the README's save format and known limitations,
   CHANGELOG [Unreleased] and the save compatibility skill, rewritten for the epoch.

### The epoch guard's checks

`.\gradlew.bat test --tests "com.veylon.save.SaveEpochGuardTest" --console=plain` with one
temporary change at a time, each reverted (`build/v091-m03-guard-mutations.txt`):

| Change | Inputs that moved | Verdict |
| --- | --- | --- |
| None, with epoch 1 marked released | none | passes |
| `world.lanterns` version 1 to 2 | `layout.city`, `layout.wilderness` | re-record (unreleased) |
| `Npc.PartyKind` reordered | `enums` | re-record (unreleased) |
| One economy loot chance, 60 to 61 | `generation.city` | re-record (unreleased) |
| The wilderness's tunnel threshold, 0.065 to 0.066 | `generation.wilderness` | re-record (unreleased) |
| `Npc.PartyKind` reordered, epoch 1 marked released | `enums` | "Raise SaveHeader.EPOCH to 2" |

Appending a constant to `Npc.PartyKind` did not compile (two exhaustive switches), so the
enum case uses a reorder; the guard compares the whole table, so an append moves it the same
way. The guard passed with the same digest in six separate JVMs, three of them inside full builds
beside seven other workers; alone it takes about 13 s.

### Native evidence

All in one isolated directory, `build/qa/v091-m03-refusal/` (ignored), on the owner's host at
1280 x 720.

- **A real 0.9.0 save.** A worktree at tag `v0.9.0` (`41dbfd6`), run with
  `VEYLON_SEED=20261120 VEYLON_FRONTEND=route VEYLON_CAPTURE_TAG=v090-route ./gradlew.bat run
  -PveylonDataDir=<that directory>`: `[frontend-route] launch=new pass=true`, generator 20,
  Survival. Its F5 wrote `saves/veylon.sav`: 2,477 bytes beginning `56 45 59 4c 00 00 00 03`
  (format 3), seed 20,261,120, generator 20; SHA-256
  `10c47c051ddb774a3fdc83bbf0a590f4dc126e9c5a4ce8d8a60475bf62e88521`, modified 01:20:38 +03:00
  (`build/v091-m03-native-hash-before.txt`).
- **This build on it,** `VEYLON_FRONTEND=route-refusal`, twice. Both runs printed
  `launch=refusal pass=true`, logged `Load refused: save format 3 (VEYLON 0.9.0 or earlier);
  this build reads format 4 only` for L and for F9, kept the new city live after F9, and printed
  the same SHA-256 before and after. The title capture (`v091-refusal_02-title-refused`) shows
  "This save was written by an older version of VEYLON. Start a new world." under the menu. In
  the first run the F9 capture showed the log line cut to "This save was written by an older
  versi…"; after `2600134` the second run's (`v091-refusal-2_04-f9-refused`) shows "Save is from
  an older version." whole. Logs: `build/v091-m03-native-v090-route.log`,
  `build/v091-m03-native-refusal.log`, `build/v091-m03-native-refusal-2.log`.
- **The file afterwards:** the same SHA-256, size and modification time
  (`build/v091-m03-native-hash-after.txt`).

### Changed paths

- Production: `save/SaveHeader.java` and `save/LoadResult.java` (new), `save/SaveSystem.java`,
  `save/V3ExtensionSections.java`, `save/BodiesSection.java`, `save/RemainsSection.java`,
  `save/UrbanMetaSection.java`, `save/UrbanContainersSection.java`, `save/FragmentsSection.java`
  (deleted), `world/urban/UrbanRevision.java`, `world/urban/UrbanContainers.java`,
  `FrontendController.java`, `HotkeyRouter.java`, `ui/TitleScreen.java`, `FrontendRouteQa.java`,
  `ai/FactionSystem.java`, `ai/Quest.java`, and the save notes in `entity/BodyFragment.java`,
  `FragmentAnatomy.java`, `FragmentPiece.java` and `NpcAppearance.java`.
- Tests: new `save/SaveEpochRefusalTest`, `save/ForeignSaves`, `save/SaveEpochGuardTest`,
  `SaveRefusalRoutingTest`, `world/WorldFingerprints`; `SaveMigrationTest` became
  `CoreSaveRoundTripTest` and `FragmentsSectionTest` became `PersonPiecesSaveTest`; deleted
  `HistoricalV020SaveCompatibilityTest`, `HistoricalV3SaveCompatibilityTest` and
  `UrbanEarlierRevisionSaveTest` with their fixtures under `src/test/resources/com/veylon/save/`
  and `src/test/resources/saves/`; updated the save, section, corruption, fingerprint, enum,
  lantern, revision and save audit tests.
- Documents: `docs/ARCHITECTURE.md`, `docs/DEVELOPING.md`, `README.md`, `CHANGELOG.md`,
  `.agents/skills/veylon-save-compatibility/SKILL.md`, DESIGN and this file.

### Validation

Every build ran from the repository root in Git Bash with `JAVA_HOME` set to
`build/tools/jdk25/jdk-25.0.4.1+1`. Each code commit's tree was built whole in a detached
worktree (`build/v091-m03-wt-1`, `-wt-2`) after its focused checks:

| Commit | Focused checks | `build --console=plain` on its tree |
| --- | --- | --- |
| `ce4686d` | `CorruptSaveResilienceTest`, 17 tests; the new test fails with the guard disabled | 4m 37s, 2,376 tests in 308 classes |
| `843dce8` | the save package and seven routing, loop and quest classes, 181 tests in 38 classes | 4m 55s, 2,379 tests in 307 classes |
| `14ca773` | the save package, enum order, anatomy, remains, all-living and fingerprint classes, 366 tests in 37 classes | 5m 0s, 2,373 tests in 307 classes |
| `3d98b70` | the save package and ten more classes, 181 tests in 41 classes; then the whole `test` task, 2,370 tests | 5m 29s, 2,370 tests in 307 classes |
| `6bae538` | `WorldGeneratorFingerprintTest`, 23 tests against the unchanged pins | 5m 10s, 2,370 tests in 307 classes |
| `f3fe760` | the fingerprint test and the guard, 27 tests; the mutation table above | 4m 48s, 2,374 tests in 308 classes |
| `13486ca` | the quest, quest-target, map and HUD scene classes, 15 tests | the final build only |
| `2600134` | the refusal, routing, mode selection and default-city classes, 26 tests; native run 2 | the final build only |
| `0fbc816` | `compileJava`; both native runs | the final build only |

Every build passed, with the two symbolic-link skips and no failure. The documentation commits
(`71972aa`, `f3aed69`, `369d65e` and the records) had link, anchor, path and
`git diff --check` checks: 68 relative links and anchors resolve.

**The final tree**, `369d65e`: `./gradlew.bat build --rerun-tasks --console=plain`, BUILD SUCCESSFUL in
5m 32s with all 10 tasks run: 2,375 tests in 308 classes, no failure, the two symbolic-link
skips, and no temporary-directory retry (`build/v091-m03-final-build.log`). The guard printed
the recorded digest again. `graphify update . --no-cluster` then rebuilt the graph: 12,299
nodes. The three worktrees, including the one at `v0.9.0`, were removed afterwards.

### Commits

| Commit | Subject |
| --- | --- |
| `ce4686d` | test(save): prove the writer refuses a save no load would read |
| `843dce8` | feat(save): open only saves of this build's format and epoch |
| `14ca773` | refactor(save): keep settled pieces in world.remains alone |
| `3d98b70` | feat(save): read only what a save of this epoch can hold |
| `6bae538` | refactor(test): share the world fingerprints |
| `f3fe760` | test(save): guard the save epoch's inputs |
| `13486ca` | refactor(quest): drop the event overloads kept for core-only quests |
| `2600134` | fix(save): keep F9's refusal line short enough for the HUD log |
| `0fbc816` | feat(qa): play a refused save natively from the title and F9 |
| `71972aa` | docs(save): describe the save epoch |
| `f3aed69` | docs(skills): rewrite the save skill for the save epoch |
| `369d65e` | docs(developing): ask for a section version when its rows change |
| `3063d13` | docs(roadmap): record milestone 03 of 0.9.1 |

No commit carries a co-author line. Nothing is pushed.

### Limitations and pending gates

- **Pending, from 02:** the first run of `verify.yml` on GitHub, until the owner pushes.
- 0.9.0 refusing a 0.9.1 save was read in 0.9.0's source (its format check runs before
  `newWorld`), not run natively.
- The city sample carries most city sections without rows; a row change is guarded through the
  section's version, which DEVELOPING now asks for (`369d65e`).
- The 32-character log line is a proxy for the HUD panel's pixel width, checked natively at
  1280 x 720 only.
- Content revisions 1 and 2 keep their runtime rules until 0.9.2 retires generators 4–19;
  no save of this epoch can carry them.
- `RELEASED` is empty until 09 ships epoch 1.

### Next: milestone 04

Three save slots and backups (D-02), with D-15's rule for foreign files in save folders.
`SaveHeader` can judge a slot from its first bytes without loading it, and `LoadResult` already
says why a slot does not open.

## Milestone 04 — Three save slots and backups

**Status:** complete on 2026-11-20.
**Base:** `main` at `3063d13`, clean apart from an untracked `.vscode/`. 01's to 03's 21 commits
were local; the owner asked for this milestone's commits to be pushed with them.

### What was done

1. **The checksum and the summary (D-22).** After the header every save carries a CRC-32C of the
   whole file (`save.SaveChecksum`) and a summary of at most 1 KiB (`save.SaveSummary`): when
   it was saved and by which build, real seconds played, the mode and the Creative mark, the
   in-game clock and where the survivor stood. `SaveSummary.inspect` reads the header, the
   summary and the checksum of a file without building anything. A load restores the play time
   from the summary, refuses a summary that disagrees with the body, and checks the checksum
   last. `SaveSession` counts play time in `Game.advanceWorld` and supplies the place name.
2. **Slots and backups (D-21, D-25).** `save.SaveSlots`: three slots in `saves/`, each
   `slot-N.sav` with `slot-N.backup-1.sav` and `slot-N.backup-2.sav`. The new save is complete
   before the current one is copied aside and replaced, and the backups move along only after
   the replacement. `describe` reports empty, ready, another version or damaged with the newest
   intact backup; `restore` loads a backup first and then puts a copy of it in place, setting the
   damaged save aside as `slot-N.damaged.sav`; `delete` removes the slot's save and backups only.
   Files the game did not write, and 0.9.0's `veylon.sav`, are never touched (D-15).
3. **The frontend (D-23, D-24, D-26).** `ui.SlotPickerScreen` in the new `TITLE_SLOTS` state:
   LOAD FRONTIER opens it to load, restore or delete; NEW FRONTIER's Enter opens it to choose
   where the new world goes, which is then saved there at once. Replacing, deleting and
   restoring each ask first and name what the slot holds; keyboard and mouse both work. The
   active slot drives F5 and F9 and is named in the pause menu. NEW FRONTIER's replace notice,
   `SaveSystem.SAVE_PATH` and the one-argument save and load are gone.
4. **QA (D-27).** The QA scenes that press F5 and F9 check the slot lines; `save_audit` and the
   infected tour use the active slot; the release smoke saves and loads slot 1 of
   `build/qa/smoke-slots`; `route`, `route-load` and `route-refusal` go through the picker;
   the new `route-slots` plays every picker state, a restore and both confirmations; the
   `slots` and `slots-new` screens open the picker statically.
5. **Documents:** README (controls, save format, known limitations), ARCHITECTURE ("The checksum
   and the summary", "Slots and backups", the collaborator tables), DEVELOPING (QA variables,
   isolation, the routes, save rules), HUD_VALIDATION (a 0.9.1 section), CHANGELOG
   [Unreleased], the save compatibility skill, DESIGN D-21 to D-27.

### Owner-facing behaviour

- LOAD FRONTIER lists the three slots without loading anything; Enter loads a ready slot, and on
  any other slot the reason appears under the cards.
- NEW FRONTIER asks which slot the new world goes into (the first empty one is offered) and asks
  again before replacing a slot that holds a save.
- F5 and F9 use the world's own slot; the pause menu reads "[F5] Save to slot N   [F9] Load slot
  N   [Q] Quit".
- A damaged slot offers its newest good backup; restoring keeps the damaged file beside it.
- The 0.9.0 file `saves/veylon.sav` is named under the cards and never opened or changed.

### The epoch guard

Format 4's layout changed inside unreleased epoch 1 (the checksum and the summary), so
`layout.city` and `layout.wilderness` were re-recorded; the other three inputs did not move. The
guard's samples now use a fixed summary and mask the checksum. Epoch 1's digest is
`f923a1296ea3e846b4e262dacb1e3531e584a6ee7e8209c86b4158640af143dc` (unreleased; 09 adds it to
`RELEASED`).

### Native evidence

All on the owner's host (NVIDIA GeForce RTX 3060 Ti, driver 621.01, OpenGL 3.3.0, windowed), each
in a new isolated directory `build/qa/v091-m04-<tag>/` (ignored), seed 20261120, from the final
code of `048e2d9` (the routes) on top of `51956a2`. The script and every log are in `build/`
(`build/v091-m04-native-summary.txt`, `build/v091-m04-native-<tag>.log`); 91 captures in all.

| Tag | Route | Framebuffer, UI scale | Result |
| --- | --- | --- | --- |
| `slots-720-s1` | `route-slots`, 0.9.0's `veylon.sav` in the directory | 1280×720, 1 | `pass=true`, 19 s |
| `slots-720-s075` | `route-slots` | 1280×720, 0.75 | `pass=true`, 19 s |
| `slots-720-s15` | `route-slots` | 1280×720, 1.5 (applied 1) | `pass=true`, 18 s |
| `slots-1080-s075` | `route-slots` | 1920×1080, 0.75 | `pass=true`, 20 s |
| `slots-1080-s1` | `route-slots`, with `veylon.sav` | 1920×1080, 1 | `pass=true`, 19 s |
| `slots-1080-s15` | `route-slots` | 1920×1080, 1.5 | `pass=true`, 19 s |
| `screen-slots`, `screen-slots-new` | the static picker on `slots-720-s1`'s slots | 1280×720, 1 | one capture each at 2 s |
| `route-720`, `route-load-720` | the default path over two launches | 1280×720, 1 | `launch=new pass=true`, `launch=load pass=true` |
| `refusal-720` | `route-refusal`, with `veylon.sav` | 1280×720, 1 | `launch=refusal pass=true` |

- Every run's shutdown summary read `glGetError=0` and 0 KHR errors (MEDIUM pixel-transfer notes
  only); every route printed `glErrors=0 khrErrors=0`.
- Each `route-slots` run captured the picker with every slot empty, the pause menu naming slot 1
  ("[F5] Save to slot 1   [F9] Load slot 1   [Q] Quit", and "Saved to slot 1." in the log), the
  picker with one slot used, a damaged slot with its backup beside a ready slot and another
  version, the reason under the cards, the restore confirmation, the restored city (the world of
  slot 1's first save, checked against what the route remembered), and the replace and delete
  confirmations, each cancelled with N. Each printed the slots' states
  `[READY, READY, OTHER_VERSION]` and the SHA-256 of the damaged file it set aside, which the
  route checked against the file it had damaged. With `veylon.sav` present slot 3 was 0.9.0's
  file ("written by an older version") and the note under the cards showed; without it slot 3
  was slot 2's save under epoch 2 ("written by a newer version").
- `route-refusal` copied 0.9.0's `veylon.sav` (the save 03's v0.9.0 worktree wrote) into slot 2.
  Slot 2 and `veylon.sav` hashed `10c47c051ddb774a3fdc83bbf0a590f4dc126e9c5a4ce8d8a60475bf62e88521`
  before and after: the picker showed slot 2 as another version, Enter on it gave "This save was
  written by an older version of VEYLON. Start a new world." under the cards and loaded nothing,
  and the new city went into slot 1.
- `route` started the city in slot 1 from the picker (written at once), saved with F5, died, woke
  and loaded slot 1 with F9 ("Loaded slot 1."); `route-load` opened the picker on slot 1, the slot
  saved last, and the loaded world held exactly what the first launch saved.
- The first round of these runs, on the code before the last two picker fixes, also passed; its
  captures showed a dimmed button with a red cast and, at 0.75 on 1080p, the block at the top of
  the screen. Both were fixed and every run above was repeated on fresh directories.

### Changed paths

- Production: new `save/SaveChecksum.java`, `save/SaveStamp.java`, `save/SaveSummary.java`,
  `save/SaveSlots.java`, `SaveSession.java`, `ui/SlotPickerScreen.java`; changed
  `save/SaveSystem.java`, `Game.java`, `WorldBootstrap.java`, `FrontendController.java`,
  `HotkeyRouter.java`, `ui/NewFrontierScreen.java`, `ui/PauseMenu.java`,
  `AutomatedRunDriver.java`, `FrontendRouteQa.java`, `SaveAuditQaScene.java`,
  `UrbanInfectedQaScene.java`, `UrbanEcologyQaScene.java`, `UrbanPeopleScene.java`,
  `UrbanSliceQaScene.java`.
- Tests: new `save/SaveSummaryTest`, `save/SaveSlotsTest`, `save/SaveSeal`, `SavePlayTimeTest`,
  `SlotPickerRoutingTest`, `ui/SlotPickerScreenTest`; changed the corruption, guard, envelope,
  integrated save, chunk retention, isolation, routing, acceptance, refusal, save audit,
  selector, hints and onboarding classes and `testsupport/WorkerDataDirectory`.
- Documents: `README.md`, `CHANGELOG.md`, `docs/ARCHITECTURE.md`, `docs/DEVELOPING.md`,
  `docs/graphics/HUD_VALIDATION.md`, `.agents/skills/veylon-save-compatibility/SKILL.md`, DESIGN
  and this file.

### Validation

Every build ran from the repository root in Git Bash with `JAVA_HOME` set to
`build/tools/jdk25/jdk-25.0.4.1+1`. Each code commit's tree was built whole in a detached worktree
(`build/v091-m04-wt-a`, removed afterwards) after its focused checks:

| Commit | Focused checks | `build --console=plain` on its tree |
| --- | --- | --- |
| `25e899e` | the save package and `SavePlayTimeTest`, 145 tests in 34 classes; the guard's re-record (only `layout.city` and `layout.wilderness` moved) | 5m 25s, 2,384 tests in 310 classes |
| `142aace` | the save package with `SaveSlotsTest` and the isolation test, 155 tests | 5m 26s, 2,395 tests in 311 classes |
| `51956a2` | the picker, routing, acceptance, refusal, selector, hints, onboarding, save audit and isolation classes, 51 tests; `ui.*` and `SlotPickerRoutingTest`, 89 tests; the whole `test` task on the working tree, 2,407 tests | 5m 24s, 2,407 tests in 313 classes |
| `048e2d9` | `compileJava` and `javadoc --rerun`; the native runs above | the final build |
| `896340a` | `CityOnboardingScreensTest` three times with `--rerun` | the final build |

Every build passed with the two symbolic-link skips and no failure or temporary-directory retry.
While iterating, the first full `test` run of `25e899e`'s changes failed five tests that compared
two saves of one state byte for byte (`ChunkRetentionTest`, `UrbanIntegratedSaveTest`): the
summary's time and place differ between such saves, so they now compare them without it
(`SaveSeal.withoutSummary`). One focused run crashed its test JVM in `lwjgl_stb`: the new picker
test measured text through a font buffer only a returned helper held; the face is now held by
the class, and `896340a` fixes the same pattern in `CityOnboardingScreensTest`. The documentation
commits had link, anchor, path and `git diff --check` checks: 72 relative links and anchors in
the changed documents resolve.

**The final tree**, `896340a` (the documentation commits change no build input):
`./gradlew.bat build --rerun-tasks --console=plain`, BUILD SUCCESSFUL in 5m 40s with all 10 tasks
run: 2,407 tests in 313 classes, no failure, the two symbolic-link skips, no temporary-directory
retry (`build/v091-m04-final-build.log`). The guard printed epoch 1's digest
`f923a1296ea3e846b4e262dacb1e3531e584a6ee7e8209c86b4158640af143dc` (unreleased). `graphify update
. --no-cluster` then rebuilt the graph: 12,528 nodes.

### Commits

| Commit | Subject |
| --- | --- |
| `25e899e` | feat(save): describe every save and seal it with a checksum |
| `142aace` | feat(save): keep three slots with two rotating backups each |
| `51956a2` | feat(frontend): choose a slot to load, start, restore or delete |
| `048e2d9` | feat(qa): play every state of the slot picker natively |
| `896340a` | test(ui): keep the measured font alive in the onboarding screens test |
| `88d00ed` | docs(save): describe the save slots, backups and summary |
| `7357680` | docs(skills): add slots and the checksum to the save skill |
| `8dcab51` | docs(hud): record the slot picker's native captures |
| the records commit | docs(roadmap): record milestone 04 of 0.9.1 |

No commit carries a co-author line. At the owner's request (2026-11-20) the commits of 01 to 04
are pushed to `origin/main` once this records commit is made.

### Limitations and pending gates

- **Pending, from 02:** the first run of `verify.yml` happens with this push; its result is not
  recorded here.
- A backup is offered only for a damaged or missing save; a player who wants an older backup of
  a good save copies the file by hand. Damaged saves set aside by a restore stay until removed.
- The place name of a world saved before its first frame lacks the HUD's route place (such as
  "your refuge"), which the player's tracker adds on the first tick.
- The checksum catches damage; it is no defence against a deliberately edited file, which the
  reader still judges field by field.
- The picker's text is English with the working names (D-26); 0.9.3 brings the canon's names.
- No return to the title from the pause menu yet (06); `route-slots` stages its title visits.

### Next: milestone 05

Autosave (D-03) into the active slot at the designed moments, through `SaveSession` and
`SaveSlots.save`, so every autosave also keeps the save it replaces as the newest backup.

## Milestone 05 — Autosave

**Status:** complete on 2026-11-20.
**Base:** `main` at `73f53c8`, the records commit of 04, pushed with 01–04; clean apart from an
untracked `.vscode/`.

### What was done

1. **What asks for an autosave (D-28).** `Autosave`, owned by `SaveSession`, writes the live
   world into its slot through `SaveSession.saveActive`, the path F5 takes, so every autosave
   keeps the save it replaces as the newest backup and the save format does not change (the
   epoch guard's inputs did not move). `SleepSystem` asks on every waking (`wake`, and the new
   `wakeEarly` that Escape now calls); `RefugeSystem.atBed` asks once a refuge is claimed; the
   interval asks once its real seconds of simulated play have passed since the slot was last
   written. On by default, every 10 minutes, or 5, 15 or 30.
2. **When it waits (D-29).** `Autosave.frame`, at the end of `Game.playStep`, writes a due
   autosave at the first safe frame, which `Autosave.blocker` judges: not dead or on an end
   card, not paused, asleep, searching or behind a screen, not burning, on the ground, no body
   still tumbling, nothing going for the player, and no hit for ten seconds. "Going for the
   player" is the new `ai.Pursuit`, read from the AI states that exist; the city soundscape's
   rule for a person going for the survivor moved into it.
3. **Who may autosave (D-30).** `AutomatedRunDriver.configure` decides it before any scene
   stages: a player's session may, an automated one only with `VEYLON_AUTOSAVE=on`. A game
   that never ran autosaves only once permitted, and nothing autosaves a world in no slot
   (`SaveSession.bound`).
4. **SAVING... (D-31).** The HUD shows SAVING... for 0.35 s before each write, in the gap the
   simulation-pause panel uses; the log then says "Autosaved to slot N." or "Could not
   autosave to slot N.".
5. **Settings (D-32).** `save.AutosaveSettings` in `veylon_autosave.properties`;
   `ui.AutosaveOptionsScreen` from AUTOSAVE on the title (`A`) and `A` in the pause menu,
   whose save line names it.
6. **QA.** `VEYLON_SCENE=urban_autosave` plays every trigger natively into slot 1 of an
   isolated directory, with a staged hunt that must hold the timer's autosave;
   `VEYLON_FRONTEND=autosave` pins the options screen. The city profile's 8 km journey now
   saves through a slot.
7. **Documents:** README (controls, save format, the Creative section's stale "one save slot"
   sentence that 04 missed, known limitations), CHANGELOG [Unreleased], ARCHITECTURE
   ("Autosave", the collaborator tables), DEVELOPING (`VEYLON_AUTOSAVE`, the scene and screen,
   "Autosave QA", the save and AI rules), PERFORMANCE_BENCHMARKS ("Autosave cost"),
   HUD_VALIDATION (a milestone 05 section), DESIGN D-28 to D-32 and §6.

### Owner-facing behaviour

- After waking, after claiming a refuge and every 10 minutes of play the world is saved into
  its own slot, with SAVING... at the top of the screen for a moment and a log line after.
- An autosave never happens while you are hurt, hunted, falling, swimming, flying, burning,
  asleep or in a menu; it happens as soon as that is over.
- `A` on the title or in the pause menu turns it off or sets 5, 10, 15 or 30 minutes; the choice
  is kept for the next launch.
- The save an autosave replaces is the slot's newest backup, as with F5, and F5 starts the
  interval again.

### The pending gate from 02

The first run of `verify.yml` came with the push of 01–04 on 2026-11-20: its Windows x64 job on
`73f53c8` (run 44739102951) **passed**, as checked that day. The macOS arm64 job runs for pull
requests, weekly and on demand, not on a push, so it has not run yet.

### Native evidence

All on the owner's host (NVIDIA GeForce RTX 3060 Ti, driver 621.01, OpenGL 3.3.0, windowed),
seed 20261120, each in a new isolated directory `build/qa/v091-m05-<tag>/` (ignored). The scripts
and logs are in `build/` (`build/v091-m05-native*.sh`, `build/v091-m05-native-summary.txt`,
`build/v091-m05-native-<tag>.log`); 41 captures.

| Tag | Run | Result |
| --- | --- | --- |
| `autosave-survival-1280x720` | `urban_autosave`, `VEYLON_AUTOSAVE=on` | `pass=true`, 29 s; the claim's autosave 16.3 ms, the waking's 13.2, the timer's 14.0, the timer's after the hunt 13.6 |
| `autosave-survival-1920x1080` | the same | `pass=true`, 26 s; 16.5, 14.1, 13.9, 13.2 ms |
| `autosave-survival-1920x1080-s075` | the same at UI scale 0.75 | `pass=true`, 26 s; 16.5, 12.5, 14.6, 13.1 ms |
| `autosave-creative-1280x720` | the same, `VEYLON_GAME_MODE=creative` | `pass=true`, 19 s; 16.4, 13.7, 15.0 ms (no hunt: nothing perceives a Creative player) |
| `refused-without-switch` | `urban_autosave` without the switch | refused before staging: "urban_autosave needs VEYLON_AUTOSAVE=on" |
| `screen-autosave-720`, `screen-autosave-1080-s075`, `screen-pause-720`, `screen-title-720` | the options screen, the pause menu in a city, the title | one capture each |
| `smoke-720` | `VEYLON_SMOKE=30` | passed: save and load, `glErrors=0`, and "[autosave] automated session: autosave off" |

- Every run's shutdown summary read `glGetError=0` and 0 KHR errors.
- Each Survival tour claimed the flat beside the starter refuge ("You claim the first-floor flat
  as a refuge (2 of 3)"), whose autosave first waited on `airborne` for a moment after the
  crouched press; slept from 23:16 and woke after 2.0 hours; ran a staged timer out; and with a
  staged infected hunting 11.8 blocks off printed "[autosave] timer waits: pursued", held the
  due autosave for 4.0 s until the body was taken away, then wrote it. F9 then opened slot 1
  holding exactly what the last autosave wrote, and the slot kept two backups.
- The first round of the four tours failed at once: `configure` decided the autosave session
  after the scene had staged, so the scene saw autosave off. The decision moved before the
  staging, and every run above, the screens and the smoke included, was repeated on the fixed
  code in fresh directories.

### Measured cost

On the 8 km journey of `cityPerformanceTest`, the first save through a slot after each kilometre
(an autosave's) took 26.9–28.4 ms at 1 km (12.5 KB) and 113.7–127.3 ms at 8 km (43.7 KB) over two
runs; natively a fresh city's autosaves took 12.5–16.5 ms. A city autosave therefore stalls one
to eight frames at 60 fps, which is why every autosave is announced (D-31). The figures, and why
a save timed straight after another one reads too low, are in
[PERFORMANCE_BENCHMARKS](../../PERFORMANCE_BENCHMARKS.md#autosave-cost-091-milestone-05-2026-11-20).

### Changed paths

- Production: new `Autosave.java`, `AutosaveQaScene.java`, `ai/Pursuit.java`,
  `save/AutosaveSettings.java`, `ui/AutosaveOptionsScreen.java`; changed `SaveSession.java`,
  `Game.java`, `WorldBootstrap.java`, `SleepSystem.java`, `RefugeSystem.java`,
  `HotkeyRouter.java`, `FrontendController.java`, `AutomatedRunDriver.java`,
  `CitySoundscape.java`, `UrbanQaScenes.java`, `QaHarness.java`, `ui/Hud.java`,
  `ui/PauseMenu.java`, `ui/TitleScreen.java`.
- Tests: new `AutosaveTest`, `AutosaveOptionsRoutingTest`, `AutosaveQaSceneTest`,
  `ai/PursuitTest`, `save/AutosaveSettingsTest`, `ui/AutosaveOptionsScreenTest`; changed
  `CombustionLifecycleTest` (the screens that pause the world), `save/SaveSlotIsolationTest`,
  `UrbanCityPerformanceTest`.
- Documents: `README.md`, `CHANGELOG.md`, `docs/ARCHITECTURE.md`, `docs/DEVELOPING.md`,
  `docs/PERFORMANCE_BENCHMARKS.md`, `docs/graphics/HUD_VALIDATION.md`, DESIGN and this file.

### Validation

Every build ran from the repository root in Git Bash with `JAVA_HOME` set to
`build/tools/jdk25/jdk-25.0.4.1+1`. Each code commit's tree was built whole in a detached worktree
(`build/v091-m05-wt`, removed afterwards) after its focused checks:

| Commit | Focused checks | `build --console=plain` on its tree |
| --- | --- | --- |
| `2b9ce42` | `ai.PursuitTest`, 8 tests | 5m 13s, 2,415 tests in 314 classes |
| `efc99ec` | `AutosaveTest` (27), `save.AutosaveSettingsTest` (5), `save.SaveSlotIsolationTest` | 5m 9s, 2,448 tests in 316 classes |
| `e487e6a` | `ui.AutosaveOptionsScreenTest`, `AutosaveOptionsRoutingTest`, the `ui` package, `SlotPickerRoutingTest` | 4m 52s, 2,453 tests in 318 classes |
| `828a184` | `AutosaveQaSceneTest` (both modes and the refusal); the native tours above | 5m 32s, 2,456 tests in 319 classes |
| `0a22a7c` | `cityPerformanceTest` twice, every profile test passing | the final build |

Every build passed with the two symbolic-link skips and no failure or temporary-directory
retry. While iterating, the first whole `test` run of the working tree failed one test:
`CombustionLifecycleTest` lists the screens that pause the world, and the autosave options are
a new one; the list now names it, in `e487e6a`. A first version of `AutosaveTest` put a burning
player out with `combustion.reset()`, which keeps the body's own fire; it uses `extinguish`.
`javadoc` ran clean after `Autosave`'s documented constants became public.

**The final tree**, `0a22a7c` (the documentation commits change no build input):
`./gradlew.bat build --rerun-tasks --console=plain`, BUILD SUCCESSFUL in 5m 13s with all 10 tasks
run: 2,456 tests in 319 classes, no failure, the two symbolic-link skips, no temporary-directory
retry (`build/v091-m05-final-build.log`). The epoch guard printed epoch 1's digest
`f923a1296ea3e846b4e262dacb1e3531e584a6ee7e8209c86b4158640af143dc`, unchanged since 04, as an
autosave changes nothing in a save. `graphify update . --no-cluster` then rebuilt the graph:
12,727 nodes. The documentation commits had link, anchor and `git diff --check` checks; every
relative link and anchor in the changed documents resolves.

### Commits

| Commit | Subject |
| --- | --- |
| `2b9ce42` | feat(ai): tell whether anything is going for the player |
| `efc99ec` | feat(save): autosave into the active slot when it is safe |
| `e487e6a` | feat(ui): set autosave from the title and the pause menu |
| `828a184` | feat(qa): play every autosave trigger in a city natively |
| `0a22a7c` | test(perf): save the long journey through a slot, as an autosave does |
| `1a11340` | docs(save): describe autosave and its switch |
| `90834e0` | docs(performance): record what a city autosave costs |
| `759b3e7` | docs(hud): record the autosave strip and options captures |
| the records commit | docs(roadmap): record milestone 05 of 0.9.1 |

No commit carries a co-author line. Nothing was pushed.

### Limitations and pending gates

- **Pending, from 02:** the macOS arm64 job of `verify.yml` has not run yet.
- An autosave waits as long as the unsafe state lasts: a long fight, a long swim or a Creative
  flight holds it, by design.
- A failed autosave is logged and not retried until the next interval; F5 can try at once.
- The interval counts sleep's frames, which fast-forward the night, like any other play.
- The hunted capture does not show the staged body, which stood behind walls; the console's
  "waits: pursued" line and the held time are the evidence.
- Persisted autosave settings were checked headlessly (`AutosaveSettingsTest`), not by a native
  relaunch.

### Next: milestone 06

Seed entry and reroll, return to title (Save and return goes through `SaveSession.saveActive`,
which also restarts the autosave interval), mouse sensitivity and invert-Y (decide where they
live; the autosave screen has `A`), and B01.

## Milestone 06 — Session and control comfort

**Status:** complete on 2026-11-20.
**Base:** `main` at `687f3d3`, the records commit of 05, local and not pushed (the remote `main`
was `73f53c8`); clean apart from an untracked `.vscode/`.

### What was done

1. **Seeds (D-33).** NEW FRONTIER shows the seed its world will be built from and builds that
   one. `NewWorldSeeds` offers a new seed at every opening and every reroll (`R`), so
   `Game.sessionSeed` no longer fixes every new world of a process; `E` or a click types a seed,
   a number or any words, read by `world.WorldSeed` as `VEYLON_SEED` always was (a whole number is
   the seed, other text its `String.hashCode`). A session with `VEYLON_SEED` offers it at every
   opening and rolls from it, so routes stay deterministic; a typed seed still makes that world.
2. **B01 and the pause menu (D-34).** Every world's pause menu names it with its seed under the
   mode, without "development build"; the keys take three lines, the last naming `R` and `Q`.
3. **Releasing a world (D-36).** `WorldBootstrap.releaseWorld` runs a new world's resets without
   the reseed and drops the world; the death card's way back, the new one and a load that failed
   halfway all use it, and the renderer's shake is part of every reset now.
4. **Return to title (D-35).** `R` in the pause menu asks: Enter saves into the slot and returns,
   `N` returns without saving, Escape stays; a failed save keeps the world and the question.
5. **Mouse settings (D-37).** `input.ControlSettings` (`veylon_controls.properties`) with 10–300%
   sensitivity and invert-Y, `input.MouseLook` for the turn (100% is the old 0.115 bit for bit),
   and the CONTROLS screen from the title and the pause menu (`C`).
6. **QA.** `VEYLON_FRONTEND=route-session` and `route-session-relaunch` play all of it natively;
   `route-slots` leaves its worlds through the pause menu; `VEYLON_FRONTEND=controls` pins the
   screen.
7. **Documents:** README (game modes, controls, save format), CHANGELOG [Unreleased],
   ARCHITECTURE (initialization, world creation, returning to the title, seeds, the collaborator
   tables), DEVELOPING (`VEYLON_SEED`, `VEYLON_FRONTEND`, isolated data, "Session QA", the slot
   route), HUD_VALIDATION (a milestone 06 section), DESIGN D-33 to D-37 and B01.

### Owner-facing behaviour

- NEW FRONTIER shows the seed; `E` types your own (a number, or words that always make the same
  world), `R` rolls another. Each opening offers a new one.
- The pause menu names the world and its seed, so a world can be shared.
- `R` in the pause menu returns to the title, asking first: Enter saves and returns, `N` returns
  without saving, Escape stays. `Q` still quits.
- CONTROLS on the title and `C` in the pause menu set mouse sensitivity (10–300%) and invert-Y,
  kept for the next launch.

### Native evidence

All on the owner's host (NVIDIA GeForce RTX 3060 Ti, driver 621.01, OpenGL 3.3.0, windowed),
seed 20261120, each in a new isolated directory `build/qa/v091-m06-<tag>/` (a relaunch in its
launch's). The script is `build/v091-m06-native.sh`; the summary `build/v091-m06-native-summary.txt`
and the logs `build/v091-m06-native-<tag>.log`. 69 captures.

| Tag | Run | Result |
| --- | --- | --- |
| `session-720`, `-relaunch` | `route-session`, then `route-session-relaunch` | `pass=true` in 19 s and 7 s; 10 and 1 captures |
| `session-1080`, `-relaunch` | the same at 1920×1080 | `pass=true` in 20 s and 6 s |
| `session-1080-s075`, `-relaunch` | the same at UI scale 0.75 | `pass=true` in 19 s and 6 s |
| `slots-720` | `route-slots`, each world left with `R` and `N` | `pass=true` in 20 s; 10 captures |
| `route-720`, `route-720-load` | the default path's two launches | `pass=true` in 20 s and 10 s; 15 and 7 captures |
| `screen-pause-wild-720`, `screen-pause-creative-720`, `screen-newworld-720`, `screen-controls-1080-s075` | static captures | one each |
| `smoke-720` | `VEYLON_SMOKE=30` | passed: isolated save and load, `glErrors=0` |

- Every run's shutdown summary read `glGetError=0` and 0 KHR errors.
- Each session launch saw NEW FRONTIER offer 20261120, typed "deep frontier" (seed 287000769),
  rolled 4110970681813779, built the city of 287000769 in slot 1, applied 170% and invert-Y (the
  file read back), returned with Save and return ("Saved to slot 1.", the slot rewritten with a
  backup), loaded the slot, returned with `N` ("Slot 1 keeps its last save.", the slot's SHA-256
  unchanged) and found NEW FRONTIER offering 20261120 again with nothing typed. Each relaunch read
  170% and On at launch, showed them, and Back changed nothing.
- A first trial run of the session route (`build/qa/v091-m06-try`) passed the same way before
  the series.

### Changed paths

- Production: new `NewWorldSeeds.java`, `world/WorldSeed.java`, `ui/ReturnToTitlePrompt.java`,
  `input/ControlSettings.java`, `input/MouseLook.java`, `ui/ControlsOptionsScreen.java`; changed
  `FrontendController.java`, `QaHarness.java`, `ui/NewFrontierScreen.java`, `ui/PauseMenu.java`,
  `WorldBootstrap.java`, `engine/Renderer.java`, `Game.java`, `HotkeyRouter.java`, `Autosave.java`,
  `ui/TitleScreen.java`, `FrontendRouteQa.java`.
- Tests: new `NewFrontierSeedTest`, `world/WorldSeedTest`, `ReturnToTitleTest`,
  `ReturnToTitleRoutingTest`, `ui/ReturnToTitlePromptTest`, `ControlsOptionsRoutingTest`,
  `input/ControlSettingsTest`, `input/MouseLookTest`, `ui/ControlsOptionsScreenTest`; changed
  `DefaultCityAcceptanceTest`, `ModeSelectionRoutingTest`, `SlotPickerRoutingTest`,
  `UrbanDevelopmentSelectorTest` (they name the session's seed as `VEYLON_SEED` does),
  `ui/CityOnboardingScreensTest`, `CombustionLifecycleTest` (the screens that pause the world),
  `save/SaveSlotIsolationTest`.
- Documents: `README.md`, `CHANGELOG.md`, `docs/ARCHITECTURE.md`, `docs/DEVELOPING.md`,
  `docs/graphics/HUD_VALIDATION.md`, DESIGN and this file.

### Validation

Every build ran from the repository root in Git Bash with `JAVA_HOME` set to
`build/tools/jdk25/jdk-25.0.4.1+1`. Each code commit's tree was built whole in a detached worktree
(`build/v091-m06-wt`) after its focused checks, with its logs in `build/v091-m06/`:

| Commit | Focused checks | `build --console=plain` on its tree |
| --- | --- | --- |
| `91e576d` | `world.WorldSeedTest`, `NewFrontierSeedTest`, `ui.CityOnboardingScreensTest`, `ModeSelectionRoutingTest`, `SlotPickerRoutingTest`, `DefaultCityAcceptanceTest`, `UrbanDevelopmentSelectorTest`, `WorldSeedDeterminismTest`, `SaveRefusalRoutingTest` | 5m 33s, 2,466 tests in 321 classes |
| `5909555` | `ui.CityOnboardingScreensTest` | 5m 11s, 2,467 tests in 321 classes |
| `f9ea0e8` | `ReturnToTitleTest`, which failed three of three with the old way back, `GameLoopIntegrationTest`, `DefaultCityAcceptanceTest`, `SaveRefusalRoutingTest` | 5m 30s, 2,470 tests in 322 classes |
| `1573313` | `ReturnToTitleRoutingTest`, `ui.ReturnToTitlePromptTest`, `CombustionLifecycleTest`, `AutosaveTest` and the slot and autosave routing tests | 5m 38s, 2,478 tests in 324 classes |
| `c652200` | `input.ControlSettingsTest`, `input.MouseLookTest`, `ControlsOptionsRoutingTest`, `ui.ControlsOptionsScreenTest`, `save.SaveSlotIsolationTest`, `CombustionLifecycleTest`, `qa.OrchestratorSizeTest` | 6m 40s beside the native runs, 2,490 tests in 328 classes |
| `68a0b5f` | the native session routes above | 6m 9s, 2,490 tests in 328 classes |
| `c36ed1b` | the native slot route above | the final build |

Every build passed with the two symbolic-link skips and no failure or temporary-directory retry.
While iterating, `CityOnboardingScreensTest` first assumed that 32 W's overflow the seed field;
they fit, so the test now measures that the widest letters fit whole and proves the tail with a
wider face. `ControlsOptionsRoutingTest` first reached a package-private step of the screen; it
uses the settings' own steps. `Game.java` stays at 993 lines and `QaHarness.java` is at 1,493.

**The final tree**, `c36ed1b` (the documentation commits change no build input):
`./gradlew.bat build --rerun-tasks --console=plain` in the worktree, BUILD SUCCESSFUL in 6m 55s
with all 10 tasks run: 2,490 tests in 328 classes, no failure, the two symbolic-link skips, no
temporary-directory retry (`build/v091-m06/c6-final-build.log`). The epoch guard printed epoch 1's digest
`f923a1296ea3e846b4e262dacb1e3531e584a6ee7e8209c86b4158640af143dc`, unchanged since 04: nothing in
a save changed. `graphify update . --no-cluster` then rebuilt the graph: 12,903 nodes. The
documentation commits had link, anchor and `git diff --check` checks.

### Commits

| Commit | Subject |
| --- | --- |
| `91e576d` | feat(ui): choose a new world's seed on NEW FRONTIER |
| `5909555` | fix(ui): name the world and its seed in the pause menu |
| `f9ea0e8` | fix(frontend): leave nothing of a world behind on the way to the title |
| `1573313` | feat(ui): return to the title from the pause menu |
| `c652200` | feat(input): set mouse sensitivity and invert-Y |
| `68a0b5f` | feat(qa): play the session's seeds, the way back and the controls natively |
| `c36ed1b` | feat(qa): leave the slot route's worlds through the pause menu |
| `3cb57cf` | docs(frontend): describe seeds, the way back to the title and the mouse settings |
| `72534e8` | docs(hud): record the seed field, the way back and the controls captures |
| the records commit | docs(roadmap): record milestone 06 of 0.9.1 |

No commit carries a co-author line. Nothing was pushed: 05's and 06's commits are local.

### Limitations and pending gates

- **Pending, from 02:** the macOS arm64 job of `verify.yml` has not run yet.
- **For the owner, D-33:** `VEYLON_SEED` wins over NEW FRONTIER's random roll, not over a seed
  typed there. The pack's "still wins" can also be read as "over everything"; the reading taken
  lets the native route type a seed under a fixed `VEYLON_SEED`, and one line in
  `FrontendController.applyNewFrontier` changes it if the owner wants the other.
- Mouse look is off in every automated session, so the native runs prove the settings and their
  persistence, not a turn; `MouseLookTest` proves the turn, bit for bit at 100%.
- The death card's Escape still returns to the title without asking, as before 06.
- A typed seed holds 32 characters; the slot cards do not show the seed (the pause menu does).
- At 720 rows the pause panel, one line taller, reaches to within a few units of the hotbar's
  top under the menu's dim.
- The relaunch's report line prints the session's seed fields as 0, since it never opens NEW
  FRONTIER; its controls fields are the evidence.

### Next: milestone 07

The forgotten-defects sweep: B03 to B10, B12, B13 and B15 (DESIGN §5), each fixed with a
regression check or native evidence, or accepted by the owner.

## Milestone 07 — Forgotten defects sweep

**Status:** complete on 2026-11-26.
**Base:** `main` at `bb17c31`, the records commit of 06, pushed with 05 and 06 on 2026-11-20;
clean apart from an untracked `.vscode/`.

### What was done

Every ledger defect 07 owns was reproduced first, then fixed, one defect to a change, each with
a regression test that fails against the old behaviour; the visual ones were captured natively
before and after. One new defect turned up on the way and was fixed after its sibling (B17).

| Defect | Reproduced by | Fix | Commit |
| --- | --- | --- | --- |
| B15 overkill health | the route's death card read "-900 / 100" (`before-route-720`); `ui.HudVitalsTest` fails on the old figure and pulse | the vitals and the low-health edge read health clamped at 0; health itself is not clamped | `dea5189` |
| B06 the target label | "Brick" under the death card and "Radio Workstation" under the city's answer (`before-route-720`, `before-missions-720`); `HudTargetLabelTest` fails on the old condition | the label is drawn only while no end card is up and no pausing screen is open (`Game.showsEndCard`) | `323ee57` |
| B10 the rained-on test | by reading: the literal matched the shared constant | racks and collectors ask `FireSystem.isRainedOn`; `simulation.RainShelterAgreementTest` holds them to the flames' outcome under four shelters, and fails when the rack asks about the wrong cell | `77517ce` |
| B03 captives in cages | `settlement.SpecialPostSpawnTest` on `bb17c31`: the captive's body overlapped the roof (feet 34.4, floor 34) and never moved; natively `fort_captive` printed feet 32.40, head 34.15 under a roof at 34, `moved=0.00 m` while burning | every post spawns 0.02 above its floor cell; natively feet 32.02, head 33.77, 15.98 m moved while burning, `pass=true` | `1508aef` (the scene), `3958deb` |
| B04 slung gear | `gfx.RestAngleTest` with the old reset: every angle 0 | `ModelPart.rest` angles survive `resetPose`; the held bow's tips and bomb's fuse had the same fault | `422e2a9` |
| B08 breathing while paused | `PausedBodiesTest` with death poses on the wall clock: the poses differ | the living, their flames and their death poses use the particle clock (D-40) | `9b485dd` |
| B05 a whole carcass's arrows | `save.CarcassArrowsSaveTest` with the arrows dropped on load: 0 of 3 come back | the core carcass record holds every carcass's arrows (D-38) | `42d3e53` |
| B12 the projectile-kill race | `settlement.DeathBeforeDormancyTest` on `bb17c31`: the killing cost no reputation and left no log line or body | the death is committed before the write-back (D-42) | `79aee28` |
| B17 a save after a killing blow (new) | a probe saved straight after a village resident's and a deer's killing blows: both loaded standing at −1 health (`build/v091-m07-b12-save-probe.log`); `save.PendingDeathSaveTest` fails on `bb17c31` | every pending death is committed before a save (D-42) | `9b17061` |
| B07 legs on end | `dismember_species_close` headlessly: 27 of 72 long pieces of every species stood on end; `entity.EndToppleTest` and `SpeciesRestTest` fail without the topple | long pieces are tipped off their ends for at most 2 s (D-39); one is left, wedged against the step | `56648d9` |
| B13 the death card's view | `dismember_player` on the commit before the fix: the card's camera 0.00 from the eye, the pieces frozen (`before-player-blast-720`); `DeathViewTest` fails with the eye camera | a clear view of the remains while they settle behind the card (D-43); natively 2.73 from the eye | `df85b5d` (the scene), `ebff3a8` |
| B09 burning blocks | `molotov_tree` showed only smoke over the canopy (`before-molotov-tree-720`); `BlockFireVisibilityTest` fails with the particles started inside the block | fire on the faces open to the air (D-41) | `010a60d` |

### Owner-facing behaviour

- A captive in a fort's cage stands on the floor, turns to you and struggles if it burns; set
  alight behind broken bars, it runs out.
- Quivers, slung bows and guns, a held bow's tips and a bomb's fuse are drawn at their angles.
- Skinning a carcass after a save gives back the arrows in it.
- No block name shows through the death card, the city's answer or the pause menu, and the death
  card reads 0 health after an overkill.
- Limbs blown off no longer stand on their ends; one wedged against a wall may.
- Nothing alive moves while the game is paused.
- A burning tree, wall or floor shows flames and glow on its open faces.
- A person shot dead as you leave their settlement counts as killed, and a save right after a
  killing shot keeps the body or carcass instead of bringing the dead back.
- Blown apart by your own bomb, the death card shows your remains falling from just behind and
  above where you stood.

### Native evidence

All on the owner's host (NVIDIA GeForce RTX 3060 Ti, driver 621.01, OpenGL 3.3.0, windowed),
seed 20261126, each in a new isolated directory `build/qa/v091-m07-<phase>-<tag>/` (ignored), by
`build/v091-m07-native.sh <checkout> <phase> <sets>`; the summary is
`build/v091-m07-native-summary.txt` and the logs `build/v091-m07-<phase>-<tag>.log`. "Before" ran
on a worktree at `bb17c31` (with the captive scene copied in for B03) or, for B13, on `df85b5d`;
"after" on the tree of `010a60d`. Every run's shutdown summary read `glGetError=0` and 0 KHR
errors.

| Tag | Before | After |
| --- | --- | --- |
| `route-720` (`VEYLON_FRONTEND=route`) | `pass=true`; `11-death`: "-900 / 100", solid red edges, "Brick" under the card | `pass=true`; "0 / 100", no label |
| `missions-720` (`urban_missions`) | `pass=true`; `12-the-city-answers`: "Radio Workstation" under the card | `pass=true`; no label |
| `captive-720` (`fort_captive`, Creative, shots 1, 2.4, 3, 6) | `pass=false`: the head in the roof bars, `moved=0.00 m` | `pass=true`: on the floor, struggling, out through the window by 6 s |
| `species-close-720` (`dismember_species_close`, shots 0.5, 1, 3, 8) | legs standing on end; every piece at rest 1.933 s after the blast | the legs lie down, one wedged against the step stands; at rest 2.217 s after |
| `molotov-tree-720` (`molotov_tree`, shots 1, 4, 10, 20) | smoke over the canopy, the pool's sheets on the ground, no fire on the tree | flames and glow on the trunk's and the canopy's open faces |
| `player-blast-720` (`dismember_player`, shots 0.9, 1.3, 2, 3.5) | the card's camera 0.00 from the eye, inside a red piece, nothing moving | 2.73 from the eye, the pieces flying, then at rest in the crater |

The HUD captures are recorded in [HUD_VALIDATION](../../graphics/HUD_VALIDATION.md), milestone 07.

### Changed paths

- Production: new `CaptiveQaScene.java`, `engine/BlockFireFaces.java`; changed `ui/Hud.java`,
  `Game.java`, `simulation/ItemConditionSystem.java`, `QaHarness.java`,
  `settlement/SettlementManager.java`, `gfx/model/ModelPart.java`, `gfx/model/NpcModels.java`,
  `gfx/model/HeldItemModels.java`, `engine/Renderer.java`, `BodyFireEffects.java`,
  `gfx/model/AnatomyModels.java`, `BodyFireQaScene.java`, `SpeciesDismemberQaScene.java`,
  `RagdollQaScene.java`, `save/SaveSystem.java`, `save/RemainsSection.java`,
  `entity/EntityManager.java`, `entity/BodyFragment.java`, `entity/BodyFragmentConstants.java`,
  `entity/BodyFragmentSystem.java`, `DismemberQaScene.java`, `PlayerLifeCycle.java`,
  `AmbienceSystem.java`, `engine/ParticleSystem.java`.
- Tests: new `ui/HudVitalsTest`, `HudTargetLabelTest`, `simulation/RainShelterAgreementTest`,
  `CaptiveQaSceneTest`, `settlement/SpecialPostSpawnTest`, `gfx/RestAngleTest`,
  `PausedBodiesTest`, `save/CarcassArrowsSaveTest`, `settlement/DeathBeforeDormancyTest`,
  `save/PendingDeathSaveTest`, `entity/EndToppleTest`, `SpeciesRestTest`,
  `DismemberPlayerSceneTest`, `DeathViewTest`, `BlockFireVisibilityTest`; changed
  `gfx/FragmentAnatomyModelTest` (the reset pose is the built one), `gfx/SpeciesFragmentDrawTest`
  and `save/RemainsSectionTest` (the death-pose clock; the link's layout),
  `save/CorruptSaveResilienceTest` (the carcass record's width), `save/SaveEpochGuardTest`.
- Documents: `CHANGELOG.md`, `docs/ARCHITECTURE.md`, `docs/DEVELOPING.md`,
  `docs/graphics/HUD_VALIDATION.md`, DESIGN and this file.

### Validation

Every build ran in Git Bash with `JAVA_HOME` set to `build/tools/jdk25/jdk-25.0.4.1+1`. Each code
commit's tree was built whole in a detached worktree (`build/v091-m07-wt`) after its focused
checks, logs in `build/v091-m07/`. Each defect's regression test was also run against the old
behaviour, on `bb17c31` (`build/v091-m07-before-reproduction.log`) or with the fix reverted in
place (`build/v091-m07-*-mutation.log`), and failed there; B10's, whose old behaviour equalled the
new, failed against a rack asking about the wrong cell instead.

| Commit | Focused checks | `build --console=plain` on its tree |
| --- | --- | --- |
| `dea5189` | `ui.HudVitalsTest` | 6m 27s, 2,492 tests in 329 classes |
| `323ee57` | `HudTargetLabelTest` | 5m 57s, 2,495 tests in 330 classes |
| `77517ce` | `simulation.RainShelterAgreementTest` | 6m 18s, 2,496 tests in 331 classes |
| `1508aef` | `CaptiveQaSceneTest`; the native "before" run | 5m 55s, 2,498 tests in 332 classes |
| `3958deb` | `settlement.SpecialPostSpawnTest`, `CaptiveQaSceneTest`; the native "after" run | 5m 37s, 2,501 tests in 333 classes |
| `422e2a9` | `gfx.RestAngleTest` and the `gfx` packages | 6m 48s, 2,504 tests in 334 classes |
| `9b485dd` | `PausedBodiesTest` | 7m 12s, 2,505 tests in 335 classes |
| `42d3e53` | `save.CarcassArrowsSaveTest`, `RemainsSectionTest`, `CorruptSaveResilienceTest`, `SaveEpochGuardTest` | 9m 30s beside another full test run, 2,507 tests in 336 classes |
| `79aee28` | `settlement.DeathBeforeDormancyTest` | 5m 37s, 2,508 tests in 337 classes |
| `9b17061` | `save.PendingDeathSaveTest` | 5m 11s, 2,510 tests in 338 classes |
| `56648d9` | `entity.EndToppleTest`, `SpeciesRestTest` | 5m 34s, 2,514 tests in 340 classes |
| `df85b5d` | `DismemberPlayerSceneTest`; the native "before" run | 5m 33s, 2,516 tests in 341 classes |
| `ebff3a8` | `DeathViewTest`, `DismemberPlayerSceneTest`; the native "after" run | 5m 35s, 2,518 tests in 342 classes |
| `010a60d` | `BlockFireVisibilityTest`; every native "after" run | 5m 32s, 2,521 tests in 343 classes |

Every build passed with the two symbolic-link skips and no failure or temporary-directory retry.
The whole suite also ran once on the combined working tree before the series was split (9m 32s
beside a worktree build, 2,521 tests in 343 classes). While iterating:
`FragmentAnatomyModelTest` pinned the defective all-zero reset pose and now compares against the
built model; three tests and three capture scenes set the wall clock to stage a death pose and
now set the particle clock; the first `DeathViewTest` expected every piece at rest within the
card's three seconds, which a piece rolling in the blast's crater may outlast, so it checks that
every piece moved and most rest, then that all rest with the card held; the first view choice
accepted a step or two of clearance in a walled-in shaft and now falls back to straight up.
`Game.java` is at 998 of its 1,000 lines and `QaHarness.java` at 1,497 of 1,500.

**The final tree**, `010a60d` (the documentation commits change no build input):
`./gradlew.bat build --rerun-tasks --console=plain` in the worktree, BUILD SUCCESSFUL in 5m 40s
with all 10 tasks run: 2,521 tests in 343 classes, no failure, the two symbolic-link skips, no
temporary-directory retry (`build/v091-m07/final-build.log`). The epoch guard printed epoch 1's digest
`6feb385a093f33b59e74590e5844da16fcaf1c323218962c46449f6a6d93d5b8`, re-recorded by `42d3e53`
(D-38) and unchanged by every later commit. `graphify update . --no-cluster` then rebuilt the graph:
13,036 nodes. The documentation commits had link, anchor and `git diff --check` checks.

### Commits

| Commit | Subject |
| --- | --- |
| `dea5189` | fix(hud): show no health below zero after an overkill hit |
| `323ee57` | fix(hud): hide the aimed block's name under end cards and the pause menu |
| `77517ce` | fix(items): judge rain on racks and collectors by the fire's own rule |
| `1508aef` | feat(qa): look into a generated fort's cage at its captive |
| `3958deb` | fix(settlement): stand captives and leaders on their posts' floors |
| `422e2a9` | fix(gfx): keep the angles parts are built at through every pose |
| `9b485dd` | fix(gfx): pose the living on the clock that stops when the game pauses |
| `42d3e53` | fix(save): keep the arrows lodged in a whole carcass |
| `79aee28` | fix(settlement): book a resident's death before the settlement sleeps |
| `9b17061` | fix(save): commit every death a save would catch half done |
| `56648d9` | fix(entity): tip a long body piece off its end so it lies down |
| `df85b5d` | feat(qa): blow the Survival player apart with their own bomb |
| `ebff3a8` | fix(death): watch a lethal blast's remains fall from clear of them |
| `010a60d` | fix(fire): show a burning block's fire on its faces open to the air |
| `b181761` | docs(architecture): record the rules the defect sweep changed |
| `1de9ae8` | docs(qa): describe the captive and player-blast scenes and rest angles |
| `dc3ea84` | docs(changelog): list the defects 0.9.1 milestone 07 fixed |
| `5e5044e` | docs(hud): record the end cards without the aimed block or negative health |
| the records commit | docs(roadmap): record milestone 07 of 0.9.1 |

No commit carries a co-author line. Nothing was pushed.

### Limitations and pending gates

- **Pending, from 02:** the macOS arm64 job of `verify.yml` has not run yet. The push of 05 and 06
  on 2026-11-20 started verify run 44808396706, whose Windows x64 job passed as checked that day;
  it was not re-checked here.
- **For the owner, D-43:** behind the death card after a lethal blast the bodies and the particles
  now move while the rest of the world stays paused; a burning body there flickers while the card
  shows. The milestone allowed this "if it fits the death flow"; the alternative is to move only
  the camera and keep the remains frozen.
- **For the owner, D-41:** the burning blocks' glow is a set of flat sheets in the pool's colours,
  rectangular like everything else in the voxel style; their size and coverage are proposed, not
  signed off, like the rest of the fire tuning (Q-04).
- A burning captive behind bars the player has broken runs out through them, as anyone flees a
  fire; it stays a captive, can still be rescued, and is back in its cage when the fort next wakes.
- A long piece wedged upright against a wall or a step stays upright (`dismember_species_close`
  keeps one against its step), by design.
- The B13 view is chosen once, at the death; pieces thrown far from where the body stood can leave
  the frame. The card's first frame still shows the eye, since the view is placed from the next.
- B10 changed no behaviour: the literal it replaced equalled the shared constant.

### Next: milestone 08

The day temperature curve (P-15, B14, D-07): warmest in the afternoon in every world, with the
economy re-measured.

## Milestone 08 — Day temperature curve

**Status:** complete on 2026-11-27.
**Base:** `main` at `a7a2695`, the records commit of 07, local only (19 commits ahead of
`origin/main` at `bb17c31`); clean apart from an untracked `.vscode/`.

### What was done

1. **The before-figure.** A measurement probe (`build/v091-m08/probe/DayTemperatureProbe.java`,
   run as a test class and not committed) read the air on the hour through production
   `TemperatureSystem.envTempAt` at every district's tour stop and a ground-floor room of each
   district in the default city, and on a surface column of every wilderness biome, both of seed
   20260731, in clear weather with no world event; then it stood a survivor from full needs
   through a waking day (08:00 to 21:00) and a night (21:00 to 08:00) in each place and season
   through `Player.tickNeeds`, and scored sleep, bedtime air and a campfire's worth. The figures are
   `build/v091-m08/probe-before.txt`, on the old curve (run last on the refactor's tree, whose
   simulation is `a7a2695`'s), and `probe-after.txt`.
2. **The fix** (D-44): the sign of `TimeSystem.tempOffset`, its Javadoc and a line on
   `TimeConstants.WARMEST_HOUR`. Nothing else in the game changed.
3. **The re-measure** (D-45): the probe again, the economy suites and route before and after, and
   the whole suite. No test pinned the inverted curve: the suite passed unchanged with the fix, and
   no expectation or tuning value was changed.
4. **The epoch digest** stayed `6feb385a093f33b59e74590e5844da16fcaf1c323218962c46449f6a6d93d5b8`,
   as temperature is not saved.
5. **Tooling.** `QaHarness` was at 1,497 of its 1,500 lines, so its benchmark biome site moved,
   word for word, to `BenchmarkSite` (1,421 lines after); `VEYLON_SCENE=day_curve` is the native
   evidence.
6. **Records.** Urban DECISIONS P-15 closed with a note pointing to this release, and D-239, urban
   DESIGN §16.1 and §19.4 noted; README "Known limitations", ARCHITECTURE, DEVELOPING, CHANGELOG,
   DESIGN and this file.

### The day, before and after

Air in clear weather of the Mild Season (day 1), °C, at the coldest and warmest hour of each
place. The hourly tables are in the probe's files; every place changed by the same offsets, twelve
hours apart.

| Place (biome; feet y) | Before: 02:00 / 14:00 | After: 02:00 / 14:00 |
| --- | --- | --- |
| Residential, commercial and civic streets (Meadow; 41) | 22.7 / 8.7 | 8.7 / 22.7 |
| The refuge and the rooms of those districts, covered | 20.2 / 11.1 | 11.1 / 20.2 |
| Industrial yards' street (Dry Scrubland) / a room | 33.7 / 19.7, 31.2 / 22.1 | 19.7 / 33.7, 22.1 / 31.2 |
| Flooded street (Wet Marsh) / a room | 20.7 / 6.7, 18.2 / 9.1 | 6.7 / 20.7, 9.1 / 18.2 |
| Quarantine street (Rocky Highlands) / a room | 9.7 / −4.3, 7.2 / −1.9 | −4.3 / 9.7, −1.9 / 7.2 |
| Wilderness meadow (35) | 23.0 / 9.0 | 9.0 / 23.0 |
| Pine forest (45) | 13.6 / −0.4 | −0.4 / 13.6 |
| Rocky highlands (66) | 2.7 / −11.3 | −11.3 / 2.7 |
| Wet marsh (34) | 21.0 / 7.0 | 7.0 / 21.0 |
| Cold ridge (37) | −2.0 / −16.0 | −16.0 / −2.0 |
| Dry scrubland (37) | 34.0 / 20.0 | 20.0 / 34.0 |

`TimeSystem.tempOffset` itself, scanned minute by minute: before, coldest −8.00 at 14:00 and
warmest +6.00 at 02:00; after, the reverse; the mean −1.000 both times. The industrial yards stay
the warmest district and the quarantine the coldest at every hour, the three Meadow districts read
alike, and the wilderness keeps its order from the scrubland down to the cold ridge.

### What the corrected day changes

From full needs, standing still, no clothing (the new world's kit has none), clear weather. Thirst
and hunger are what the part of the day used; "hot" is seconds of air above 30 °C, "overheated"
and "freezing" seconds of body temperature above 40.5 °C or below 33 °C.

| Measure | Before | After |
| --- | --- | --- |
| A temperate street, the refuge or any room of those districts, waking 08:00–21:00, every season | 36.6 thirst and 21.9 hunger; the body never froze or overheated | the same |
| Industrial street, waking, Mild Season | 19.7–28.5 °C, 36.6 thirst, health 100 | 24.9–33.7 °C, 310 s hot, 211 s overheated, 71.6 thirst, health 97.3 at the lowest |
| Industrial room, waking, Mild Season | 36.6 thirst | 208 s hot, 45.9 thirst, never overheated |
| Industrial street, waking, Dry Season | 53.0 thirst, health 95.7 | thirst emptied (100), health 35.2 |
| Industrial street, night 21:00–08:00, Mild Season | 310 s hot, 66.0 thirst, health 98.0 | 31.0 thirst, health 100 |
| Quarantine street, waking, Mild Season | −4.3–4.5 °C, 228 s freezing, health 91.7 | 0.9–9.7 °C, none, health 100 |
| Quarantine street, night, Mild Season | none | 228 s freezing, health 91.7 |
| Wilderness cold ridge, waking, Mild Season | health 0 | health 72.5 (the night now kills, as the day did) |
| Wilderness scrubland, waking, Mild Season | 36.6 thirst | 73.8 thirst, health 96.2 |
| Sleep in the refuge on a camp bed, Frost Season, clear | 0.85 at 21:00, 23:00 and 01:00 | 0.85 (air 1.2 °C at 01:00) |
| The same on a snowy night | 0.85 | 0.85 at 21:00, 0.65 from 23:00 (air −0.4 °C) |
| A quarantine room's bedtime air below 0 °C, Mild Season | never | from about 22:30 |
| A roof against the street (refuge, residential) | 2.5 °C colder at 02:00, 2.4 °C warmer at 14:00 | 2.4 °C warmer at 02:00, 2.5 °C cooler at 14:00 |
| A campfire two blocks off, meadow, Mild Season | +3.0 °C at 02:00, +17.0 °C at 14:00 | +17.0 °C at 02:00, +3.0 °C at 14:00 |
| The same, Frost Season | +13.0 / +26.3 | +26.3 / +13.0 |

**The economy route** (`UrbanEconomyRouteTest`, seed 20260731), its four days after the city
answers, before → after: health 100 every evening both times; food 20, 19, 20, 21 servings and
clean water 12, 14, 16, 17 both times; 15, 8, 8 and 10 containers searched both times; the refuge's
taps 8 L both times; thirst 61, 97, 92, 89 both times; hunger 71, 80, 76, 71 → 71, 80, 75, 70; the
refuge's air at about 20:30 15.0, 15.1, 16.2, 3.5 °C → 14.8, 14.1, 15.0, 2.3 °C, the evening now
on the cooling side of the day. The route spends its days in the residential district, mostly
indoors, and its survivor's thirst and health crossed no threshold either way. All eight
`UrbanEconomy*Test` classes passed before and after; the census and the audit count loot and
recipes, which no temperature touches.

### Owner-facing behaviour

- The day is warmest in the afternoon and coldest in the small hours, in the city and the
  wilderness.
- The industrial yards are hot by day: standing in the sun there makes you thirsty, as their
  arrival line warns, and in the Dry Season it is dangerous without water.
- Nights are colder everywhere, so the refuge, a roof and a fire count for more after dark; on a
  snowy Frost Season night even the refuge's air falls below freezing late in the evening.
- The quarantine, the pine forests and the cold heights are milder by day and harsher by night.

### Native evidence

All on the owner's host (NVIDIA GeForce RTX 3060 Ti, driver 621.01, OpenGL 3.3.0, windowed),
seed 20260731, 1280×720, each in a new isolated directory `build/qa/v091-m08-<phase>-<place>-720/`
(ignored), by `build/v091-m08-native.sh <checkout> <phase>`; the summary is
`build/v091-m08-native-summary.txt` and the logs `build/v091-m08-<phase>-<place>-720.log`. "Before"
ran on the scene's commit `132ff76`, whose curve is still inverted, and "after" on the fix's
`64b20a6`. Every run's shutdown summary read `glGetError=0` and 0 KHR errors, and each wrote its two
captures (`_2s`, `_4s`).

| Tag (`VEYLON_SCENE=day_curve`) | Before | After |
| --- | --- | --- |
| `meadow-720` (the wilderness meadow at −15.5, 35.2, 64.5) | 12:03 9.9 °C, 03:02 22.7 °C, `pass=false` | 12:03 22.1 °C, 03:02 9.3 °C, `pass=true` |
| `yards-720` (with `VEYLON_GENERATOR=urban-dev`: the industrial yards' gateway at 31.5, 41.0, −448.5, Dry Scrubland) | 12:03 20.6 °C, 03:02 33.5 °C, `pass=false` | 12:03 32.8 °C, 03:02 20.0 °C, `pass=true` |

In every capture the overlay's EnvTemp line and the HUD's Air read the figure the console printed;
the body stayed at 36.9–37.3 °C, as four seconds allow.

### Changed paths

- Production: new `BenchmarkSite.java`, `DayCurveQaScene.java`; changed `QaHarness.java`,
  `simulation/TimeSystem.java`, `simulation/TimeConstants.java`.
- Tests: new `DayTemperatureCurveTest`, `DayCurveQaSceneTest`.
- Documents: `CHANGELOG.md`, `README.md`, `docs/ARCHITECTURE.md`, `docs/DEVELOPING.md`,
  `docs/urban-survival/DECISIONS.md`, `docs/urban-survival/DESIGN.md`, DESIGN and this file.

### Validation

Every build ran in Git Bash with `JAVA_HOME` set to `build/tools/jdk25/jdk-25.0.4.1+1`. Each code
commit's tree was built whole in a detached worktree (`build/v091-m08-wt`) after its focused
checks; logs in `build/v091-m08/`.

| Commit | Focused checks | `build --console=plain` on its tree |
| --- | --- | --- |
| `3fc3570` | the moved method compared line for line with the one it replaced | 5m 36s, 2,521 tests in 343 classes |
| `132ff76` | `DayCurveQaSceneTest`, `qa.OrchestratorSizeTest`; the native "before" runs | 5m 36s, 2,523 tests in 344 classes |
| `64b20a6` | `DayTemperatureCurveTest`, `DayCurveQaSceneTest`, `simulation.*`, `UrbanEconomy*Test`, `UrbanRefugeNightTest`, `AutosaveTest`, `CombustionLifecycleTest`, `CreativeHazardsTest`, `CreativeWorldControlsTest`, `UrbanStarterProgressionTest`, `UrbanIndoorEnvironmentTest`, `UrbanUndergroundEnvironmentTest`, `PlayerOutcomeDeterminismTest`, `qa.TickProfileTest`, `HudQaSceneTest` and `save.SaveEpochGuardTest`: 198 tests in 29 classes, 1m 11s; the native "after" runs | 5m 19s, 2,526 tests in 345 classes |

Every build passed with the two symbolic-link skips and no failure. In the first one temporary
directory, and in the second two, were deleted on their cleanup's second attempt (B02's retry at
work); the third needed none.

Against the old behaviour: `DayTemperatureCurveTest`, with the sign reverted in place, failed all
three tests (the warmest minute at 2.0 h, the meadow and the residential street warmest at 02:00;
`build/v091-m08/curve-test-mutation.log`), and `DayCurveQaSceneTest` as the fix leaves it failed both
tests on `132ff76` (noon 9.9 against 22.7 °C and 20.6 against 33.5 °C;
`build/v091-m08/c3-scene-test-on-old-curve.log`). Before the series was split, the whole suite ran on
the working tree with only the fix and its test: 2,524 tests in 344 classes passed unchanged (5m 31s,
`build/v091-m08/full-test-1.log`), so no existing test pinned the inverted curve. The economy suites
ran on `a7a2695` (52 s, 8 classes, `build/v091-m08/economy-before.log`) and on the fix, and the probe on
both curves.

**The final tree**, `a7ddf7b` (the documentation commits change no build input):
`./gradlew.bat build --rerun-tasks --console=plain` in the worktree, BUILD SUCCESSFUL in 5m 31s
with all 10 tasks run: 2,526 tests in 345 classes, no failure, the two symbolic-link skips, no
temporary-directory retry (`build/v091-m08/final-build.log`). The epoch guard printed epoch 1's
digest `6feb385a093f33b59e74590e5844da16fcaf1c323218962c46449f6a6d93d5b8` on every tree, as before
the milestone. `graphify update . --no-cluster` then rebuilt the graph: 13,072 nodes. The
documentation commits had link, path and `git diff --check` checks.

### Commits

| Commit | Subject |
| --- | --- |
| `3fc3570` | refactor(qa): move the benchmark scenes' biome site out of QaHarness |
| `132ff76` | feat(qa): read the day's air at noon and before dawn off the debug overlay |
| `64b20a6` | fix(simulation): make the afternoon the warmest part of the day |
| `bbc497f` | docs(simulation): describe the day's corrected temperature curve |
| `5694a20` | docs(qa): describe the day-curve scene |
| `a7ddf7b` | docs(urban): close the inverted-day question in the city's records |
| the records commit | docs(roadmap): record milestone 08 of 0.9.1 |

No commit carries a co-author line. Nothing was pushed.

### Limitations and pending gates

- **Pending, from 02:** the macOS arm64 job of `verify.yml` has not run yet; 07 and 08 are local.
- **For the owner, D-44:** the coldest hour is 02:00, twelve hours after the warmest, because the
  curve stays a pure cosine. A low just before sunrise (about 05:00) would need an uneven curve, a
  new shape the milestone did not ask for; it can be added if wanted.
- **For the owner, D-45:** the industrial yards in the Dry Season now cost a careless survivor real
  health by day (about 65 for a whole day standing in the sun without water), where before they did
  so only at night. Every threshold is unchanged; this is the yards' design ("hot, so thirst
  rises") meeting a day that is finally warm in the afternoon.
- The probe stands a survivor still and unclothed; real days mix places, shade and clothing, so
  its figures bound the change rather than predict a playthrough. How the warm afternoons and cold
  nights feel is for the owner's play.
- Sleep fast-forwards the night, so a survivor in bed spends seconds, not hours, in the cold
  half of the day; the colder night bites mostly on those who stay up or travel.

### Next: milestone 09

Integration and release preparation: full evidence, documents, version 0.9.1 and its release
notes, ready for the owner.

## Milestone 09 — Integration and release preparation

**Status:** complete on 2026-11-27. Every gate of this milestone is met and 0.9.1 is prepared for
release; merging, tagging and publishing are the owner's and were not done.
**Base:** `main` at `23e50bc`, the records commit of 08, pushed with 07 that morning; clean apart
from an untracked `.vscode/`.

### What was done

1. **The audit** of the acceptance contract (00 §4) and the ledger, below: every item is met. The
   release smoke found one more defect, B18, which was fixed here.
2. **The final acceptance scenario**, natively, in Survival and Creative, in the city and the
   wilderness: a new route plays the pack's whole scenario over three launches (`388d087`, D-46),
   against genuine saves the tagged 0.9.0 build wrote. The reverse direction, 0.9.0 opening a 0.9.1
   save, was run natively too.
3. **The ledger's native evidence** and the corrected day, run again on the release tree.
4. **The release smoke** in the city, the Creative city and the wilderness, the calibrated
   performance tasks, and the same smokes and the city profile on the tagged 0.9.0 build the same
   afternoon, to tell a change from the host's drift.
5. **Epoch 1 released:** `SaveEpochGuardTest`'s `RELEASED` holds epoch 1 and its digest
   `6feb385a093f33b59e74590e5844da16fcaf1c323218962c46449f6a6d93d5b8` (`bc6e81a`).
6. **B18** (new): the Creative smoke's flight in a city starts above the roofs (`8c37958`, D-47).
7. **Documents:** the README's 0.9.1 paragraph, where Windows keeps the slots (its run section still
   named 0.9.0's `saves\veylon.sav`) and the artifact names; ARCHITECTURE, DEVELOPING and the save skill
   on the released epoch; DEVELOPING's "Release acceptance QA" and the smoke's flight; the release
   figures in PERFORMANCE_BENCHMARKS; HUD_VALIDATION's milestone 09 section; DESIGN D-46, D-47 and
   B18.
8. **Version and notes:** `build.gradle` and `ReleaseVersionTest` at 0.9.1, CHANGELOG's
   `[0.9.1] - 2026-11-27` with a Compatibility section and `[Unreleased]` above it, and
   `docs/releases/v0.9.1.md` with tag-pinned links, which says plainly that saves of 0.9.0 and
   earlier do not open, why, and that the file is left untouched.
9. **The release artifacts:** `releaseArtifacts --rerun-tasks --no-daemon`, the Windows archive
   checked as the tag workflow checks it, and the release smoke from the fat JAR and the unpacked
   `Veylon.exe`.

### The acceptance contract

| Item (00 §4) | Evidence | Status |
| --- | --- | --- |
| 1. A 0.9.1 save opens; every other epoch is refused before the live world is touched, with a message, and the file stays byte-identical | `SaveEpochRefusalTest`, `SaveRefusalRoutingTest` (03); natively, in each world kind and mode a save the tagged 0.9.0 build wrote was refused from slot 3 with "This save was written by an older version of VEYLON. Start a new world.", and it and its copy hashed the same before and after; the tagged 0.9.0 build refused a 0.9.1 save ("Save is version 4; this build reads versions 2-3", "No compatible save was found.") and left it unchanged | Met |
| 2. Three slots: a new world into an empty or confirmed slot, any slot loads from the title, F5/F9 on the active slot, a damaged slot recovered from its backup, summaries without loading | `SaveSlotsTest`, `SlotPickerRoutingTest` (04); natively two worlds in slots 1 and 2, both loaded and compared from the title at launch and after a relaunch, F5 into slot 2, and a damaged slot 2 restored from its newest backup in every world kind and mode | Met |
| 3. Autosave at the designed moments, never at an unsafe one, and it can be turned off | `AutosaveTest` (05); natively the waking's autosave, announced by SAVING..., wrote slot 1 in every world kind and mode; `urban_autosave` (05) holds the hunted case | Met |
| 4. NEW FRONTIER accepts and rerolls a seed; the pause menu returns to the title with a save choice; mouse sensitivity and invert-Y persist | `NewFrontierSeedTest`, `ReturnToTitleRoutingTest`, `ControlsOptionsRoutingTest` (06); natively a typed seed, both ways back to the title, and 170 % with invert-Y read at a relaunch | Met |
| 5. Every ledger defect fixed with a regression test or native evidence, or accepted; the day warmest in the afternoon | B01–B18 fixed (DESIGN §5); their tests ran in this milestone's builds; their native evidence and `day_curve` were run again below | Met |
| 6. The build passes without flaky failures across runs and is faster than the snapshot; a push and pull-request workflow exists | 17m 50s at the snapshot, 5–7 min for a build alone since 02, this milestone's included; `verify.yml`'s Windows job passed on all three pushes, the last (run 45941592409, #4) on `23e50bc`; its macOS arm64 job passed on the weekly run of 2026-11-23 (run 45191368017, #3, on `bb17c31`, 18 min), which closes 02's pending gate | Met |

### The final scenario, natively

All on the owner's host (AMD Ryzen 5 5600X, NVIDIA GeForce RTX 3060 Ti, driver 621.01, OpenGL
3.3.0, windowed), 1280 × 720, seed 20261127. **The 0.9.0 saves:** a detached worktree at tag
`v0.9.0` (`41dbfd6`), by `build/v091-m09-v090-saves.sh`, wrote one save per world kind and mode,
each in its own directory under `build/qa/v091-m09-v090-*` (format 3, header `56 45 59 4c 00 00 00
03`): the city's from its `route` (`saves/veylon.sav`, 3,414 bytes in Survival, SHA-256
`7d74b588…dc1c0c`, and 3,355 in Creative, `35b669fe…f33aed`), the wilderness's from its 30-second
smoke (`build/qa/smoke-save.dat`, 5,625 bytes in Survival, `c9158ed9…a74dcf`, and 5,865 in Creative,
`26cbaf6d…86021e`); every run passed. A first pass with a 10-second smoke ended before the smoke's
fortress approach could finish, so its gate failed after the saves were written; it was repeated
at 30 s.

**The release route,** by `build/v091-m09-native.sh`, which copies the matching 0.9.0 save into a
new directory `build/qa/v091-m09-<tag>/` as `saves/veylon.sav`, runs `route-release` there with
`VEYLON_AUTOSAVE=on`, then `route-release-relaunch`, then copies the directory to `…-restore/` and
runs `route-release-restore` in the copy. Summary `build/v091-m09-native-summary.txt`; logs
`build/v091-m09-native-<tag>[-relaunch|-restore].log`; the captures are in HUD_VALIDATION,
milestone 09.

| Tag | `route-release` | `-relaunch` | `-restore` | 0.9.0's file |
| --- | --- | --- | --- | --- |
| `city-survival` | `pass=true`, 35 s, 21 captures | `pass=true`, 11 s, 4 | `pass=true`, 8 s, 3 | unchanged |
| `city-creative` | `pass=true`, 34 s, 21 | `pass=true`, 11 s, 4 | `pass=true`, 9 s, 3 | unchanged |
| `wild-survival` | `pass=true`, 30 s, 20 | `pass=true`, 10 s, 4 | `pass=true`, 8 s, 3 | unchanged |
| `wild-creative` | `pass=true`, 31 s, 20 | `pass=true`, 11 s, 4 | `pass=true`, 8 s, 3 | unchanged |

- Every launch printed `glErrors=0 khrErrors=0`, and every shutdown summary read `glGetError=0` and
  0 KHR errors.
- Each first launch typed "safe saves" (seed 1788257347) and started that world in slot 1; in a
  city it searched a refuge cupboard and took a stack from it; at a staged 23:15 it slept, in the
  refuge's camp bed or on a bedroll staged beside the survivor in the wilderness, woke after 2.0
  hours, and the waking's autosave showed SAVING... and wrote slot 1 in 14.5–17.2 ms; `R` and `N`
  left slot 1 byte for byte. A world of the offered seed, 20261127, started in slot 2, walked,
  opened the map and saved with F5; `R` and `Enter` saved it again, after which F5's save was
  slot 2's newest backup. LOAD FRONTIER then loaded slot 1 and slot 2: each held the durable digest
  kept when it was written, its file was unchanged, and its clock and play time were those of its
  summary (+0.00 minutes and +0.00 s, read in the frame the load began to play). 0.9.0's save was
  copied into slot 3, shown as another version and refused, both files unchanged; 170 % and
  invert-Y were applied on the title's CONTROLS.
- Each relaunch read 170 % and invert-Y at launch and loaded both slots again as saved, the files
  unchanged by the loads. Each restore damaged one byte of slot 2's save in the copy, saw slot 2
  damaged with its backup, restored it, and found the world F5 had saved, the slot's file now byte
  for byte that backup and the damaged file set aside unchanged.
- The first two runs of the Survival city failed one check each, "slot 1 holds exactly what was
  saved" (in its first launch and its relaunch alike): the digest had been kept 0.8 s after the
  autosave, by when a slow tick had registered a city site and a block had been edited, neither of
  which the save held (`sites=1 edits=1` kept, `sites=0 edits=0` loaded), and the loaded side was
  read a frame after the load. Kept in the frame
  of the write and read in the frame of the load (D-46), every comparison matched; the four
  directories were run afresh on that code.
- **The reverse direction:** the tagged 0.9.0 build, given slot 1 of `city-survival` as its
  `saves/veylon.sav` and its `route-load` (`build/v091-m09-v090-reads-091.log`), printed "Save is
  version 4; this build reads versions 2-3. Old prototype saves can't be migrated - start a new
  world.", stayed on its title with "No compatible save was found." and left the file's SHA-256
  as it was (`836c2d3e…3c37`). Its route reported its own load check failed, as it must.

The route runs no smoke code, so B18's fix, made after these runs, does not touch them; neither
does the version, which a run from Gradle reports as "a development build".

### The ledger on the release tree

Every ledger defect is closed (DESIGN §5), and each one's regression test ran in this milestone's
builds. The native evidence of 07 and 08 was run again on the release tree by
`build/v091-m09-ledger.sh`, each run in a new directory `build/qa/v091-m09-ledger-<tag>/`, summary
`build/v091-m09-ledger-summary.txt`; every run ended with `glGetError=0`.

| Run | Defects | Result |
| --- | --- | --- |
| `route-720`, `route-creative-720` (`VEYLON_FRONTEND=route`, seed 20261126) | B15, B06, and the default path | `pass=true` both, 15 captures each: the Survival death card reads 0 health with no label |
| `missions-720` (`urban_missions`) | B06 | `pass=true`, 15 captures |
| `species-close-720` (`dismember_species_close`) | B07 | 4 captures: the legs lie down |
| `molotov-tree-720` (`molotov_tree`) | B09 | 4 captures: fire on the trunk's and canopy's open faces |
| `captive-720` (`fort_captive`, Creative) | B03 | `pass=true` |
| `player-blast-720` (`dismember_player`) | B13 | 4 captures: the card watches the remains from clear of them |
| `day-meadow-720`, `day-yards-720` (`day_curve`, seed 20260731) | B14 | `pass=true` both: the meadow 22.1 °C at 12:03 and 9.3 °C at 03:02; the industrial yards 32.8 and 20.0 °C, as 08 measured |

B01 (the pause menu's world line), B02 (the temporary-directory retry, which deleted three
directories on their second attempt in the builds of `388d087` and `8c37958`), B04, B05, B08, B10, B11, B12, B16
and B17 rest on their tests and records.

### B18: the Creative smoke's flight in a city

The release smoke flies a Creative player east from 5.2 to 6.8 s and fails unless the flight
crosses a loaded chunk. Since urban package 26 the smoke plays the city, where the player starts in
the refuge: the flight climbs into its ceiling and flies into its walls. With seed 20261127 it
crossed no chunk, and the Creative city smoke failed ("Creative smoke flight did not cross loaded
terrain"); the tagged 0.9.0 build failed the same way on that seed, and seeds 20260910 and 20261120
passed only by crossing one chunk. Now, in a city, the flight starts two blocks above the highest
loaded column within 48 blocks east of the player (`[smoke] staged city flight start above the
roofs east: y 41.0 -> 55.0` on all three seeds) and crossed two chunks on each; the Creative
wilderness smoke, which starts in the open and is unchanged, crossed two as before. The smoke has
no headless twin, so the evidence is native: before, `build/v091-m09-ledger-smoke-creative-720.log`
and `build/v091-m09-v090-smoke-creative.log`; after, `build/v091-m09-b18-smoke-*.log`.

### Release smoke and performance

Measured with no build running beside them; the tables, budgets and the 0.9.0 comparison are in
[PERFORMANCE_BENCHMARKS](../../PERFORMANCE_BENCHMARKS.md#release-run-v091-2026-11-27).

- **Smokes**, seed 20260910, 1280 × 720, VSync off: the city 995.1 FPS, the Creative city 1,012.7
  (before B18's fix; 1,038.8 after it, from another view), the wilderness 1,419.1 with its fortress
  approach at 1,493.8. The tagged 0.9.0 build on the same afternoon: 988.8, 1,005.8, 1,417.2 and
  1,490.7. Every run saved and loaded, kept every hard limit and read zero GL and KHR errors.
- **`performanceTest`** (`build/v091-m09-performance*.log`): nine of ten benchmarks pass (chunk tick
  0.608 ms, entity tick 0.698, settlement tick 0.019, presentation 0.0969); the durable `save` gate
  fails at 5.255 ms against 2.20, this host's gap since 0.7.4 (0.9.0: 5.295). The rain gates pass.
  The city profile passed 5 of 5 in two of three runs; in the first, the flight leg's CPU frame p99
  was 8.366 ms against the half-frame gate of 8.33 ms, then 8.102, 7.591 and 8.091 ms, while the
  tagged 0.9.0 build read 7.891 and 7.800 ms (`build/v091-m09-v090-cityperf-*.log`). The gate sits at
  this host's edge for both builds; the miss is recorded and nothing was relaxed.

### The release artifacts

`./gradlew.bat releaseArtifacts --rerun-tasks --no-daemon --console=plain` ran on the working tree
whose build inputs are exactly `1c96e10`'s (the code of `8c37958` with the version at 0.9.1; every
later change is a document): BUILD SUCCESSFUL in 5m 56s with all 15 tasks run
(`build/v091-m09-release-artifacts.log`), 2,526 tests in 345 classes, no failure, the two
symbolic-link skips and no temporary-directory retry, then Javadoc, the fat JAR
`build/libs/veylon-0.9.1-all.jar` (9,565,619 bytes) and `build/distributions/veylon-0.9.1-windows-x64.zip`
(56,921,039 bytes, SHA-256 `81d4e2e36d0b1e9dbc04eafabf9f7ebd315d5a8f6a9f40101e9fdf017dd22a48`). The
epoch guard printed epoch 1's digest as released.

`build/v091-m09-package.sh` (summary `build/v091-m09-package-summary.txt`) then checked the archive
as `release-build.yml` checks it: over 1 MiB, it unpacks, `Veylon/Veylon.exe`, the bundled runtime
(`JAVA_VERSION="25.0.4.1"`), and `app/Veylon.cfg` naming the `natives-windows` jar and
`--enable-native-access=ALL-UNNAMED` (beside `-Djpackage.app-version=0.9.1` and `-Xmx2G`); the
versioned `veylon-0.9.1.jar` and the license are there too. It ran the release smoke
(`VEYLON_SMOKE=30`, seed 20260910, VSync off) from the fat JAR (`java --enable-native-access=ALL-UNNAMED
-Xmx2G -jar`) and from the unpacked `Veylon.exe`, each with its working directory, which is the
data directory on Windows, set to a new `build/qa/v091-m09-fatjar-smoke/` or `…-exe-smoke/`: 1,007.9
and 991.2 FPS, save and load through `build/qa/smoke-slots/slot-1.sav` inside it, 139 of 139 item
icons, zero GL and KHR errors (0.9.0: 1,026.1 and 1,015.5). A first run of the script took the
first archive in `build/distributions`, 0.7.3's, which older releases left there, and smoked that;
it was discarded, and the script now names the version's files.

### Changed paths

- Production: `FrontendRouteQa.java`, `CreativeQaScenes.java`.
- Tests: `save/SaveEpochGuardTest.java`, `qa/ReleaseVersionTest.java`.
- Build: `build.gradle` (version 0.9.1).
- Documents: `README.md`, `CHANGELOG.md`, `docs/releases/v0.9.1.md` (new), `docs/ARCHITECTURE.md`,
  `docs/DEVELOPING.md`, `docs/PERFORMANCE_BENCHMARKS.md`, `docs/graphics/HUD_VALIDATION.md`,
  `.agents/skills/veylon-save-compatibility/SKILL.md`, DESIGN and this file.

### Validation

Every build ran in Git Bash with `JAVA_HOME` set to `build/tools/jdk25/jdk-25.0.4.1+1`. The code
commits were built in a detached worktree (`build/v091-m09-wt`), logs in `build/v091-m09/`:

| Commit | Focused checks | Build on its tree |
| --- | --- | --- |
| `388d087` | `compileJava`; the twelve native launches above | `build --console=plain`: 5m 45s, 2,526 tests in 345 classes |
| `bc6e81a` | `save.SaveEpochGuardTest` (4 tests, "epoch 1 … (released)"); with `ReleaseVersionTest` and `qa.OrchestratorSizeTest` on the working tree first | the final build |
| `8c37958` | the B18 smokes above | `build --console=plain`: 5m 38s, 2,526 tests in 345 classes |

**The final tree**, `1c96e10` (the records commit changes no build input): its build is the
`releaseArtifacts` run under "The release artifacts". `graphify update . --no-cluster` then rebuilt
the graph: 13,092 nodes. The documentation commits had link, anchor, path and `git diff --check`
checks: every relative link and anchor in the changed documents resolves, and so do the release
notes' tag-pinned links against the working tree.

### Commits

| Commit | Subject |
| --- | --- |
| `388d087` | feat(qa): play the release's acceptance scenario from the title |
| `bc6e81a` | test(save): record epoch 1 as released with 0.9.1 |
| `06675dc` | docs(save): record epoch 1 as released |
| `ab11925` | docs(readme): say where Windows keeps the save slots |
| `8c37958` | fix(qa): start a city's Creative smoke flight above the roofs |
| `1ffc375` | docs(qa): describe the release acceptance route and the city smoke flight |
| `a565ae3` | docs(hud): record the release route's captures |
| `079954b` | docs(performance): record the 0.9.1 release measurements |
| `1c96e10` | chore(release): prepare v0.9.1 |
| the records commit | docs(roadmap): record milestone 09 of 0.9.1 |

No commit carries a co-author line. At the owner's request every commit of 09 is pushed to
`origin/main` once this records commit is made; nothing is tagged or published.

### Limitations and pending gates

- **For the owner, the release itself:** `main` holds the prepared 0.9.1. Releasing it is an
  annotated tag `v0.9.1` ("VEYLON 0.9.1 - Safe Saves") on the pushed release commit, pushed to
  `origin`; the tag workflow then builds, checks and publishes the Windows x64, macOS Apple Silicon
  and macOS Intel archives with `docs/releases/v0.9.1.md` as the release body, and it refuses a tag
  that is not `v` plus `build.gradle`'s version. 0.9.1 was developed on `main`, so there is no
  release branch to merge. Not done here: the milestone does not authorize it.
- **macOS packaging** is checked only by the tag workflow; no macOS archive was built or run here.
  The macOS arm64 build and tests passed in `verify.yml`'s weekly run on `bb17c31`.
- **Readings the owner may want to change**, none of which blocks the release: D-33 (`VEYLON_SEED`
  wins over NEW FRONTIER's roll, not over a typed seed), D-41 (the burning blocks' glow sheets,
  with the rest of the fire tuning, Q-04), D-43 (bodies settle behind the death card while the
  world stays paused), D-44 (the coldest hour is 02:00, not just before dawn) and D-45 (the
  industrial yards in the Dry Season cost a careless survivor real health by day). Q-01 to Q-04
  stay open.
- The city profile's flight gate and the durable-save gate sit at or past this host's edge; the
  reference machine has not run 0.9.1.
- `FrontendRouteQa` holds every frontend route and is now 1,431 lines; it has no line budget, but
  a later route belongs in a class of its own.

### Next

The owner tags `v0.9.1`, then 0.9.2 "Clean Slate", milestone 01.
