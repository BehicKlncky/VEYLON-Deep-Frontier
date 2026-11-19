# VEYLON 0.9.1 "Safe Saves" — Progress

One section per milestone of the 0.9.1 prompt pack (`build/release-roadmap/v0.9.1-safe-saves/`,
ignored by Git). Decisions, scope and the defect ledger are in [DESIGN.md](DESIGN.md). Resume at
the first milestone below that is not complete.

| Milestone | Status | Commits |
| --- | --- | --- |
| 01 Release plan and defect audit | Complete (documentation only) | `95d09c5`, `1aadd19` |
| 02 Test reliability and continuous integration | Complete; the verify workflow's first run waits for a push | `91975e9`, `ec4d960`, `6517ef0`, `0252fbd`, `bfb0176`, `8b87cd6` |
| 03 Current-version save epoch | Complete | `ce4686d`, `843dce8`, `14ca773`, `3d98b70`, `6bae538`, `f3fe760`, `13486ca`, `2600134`, `0fbc816`, `71972aa`, `f3aed69`, `369d65e`, then the records commit that adds this section; 04 records its hash |
| 04 Three save slots and backups | Not started | |
| 05 Autosave | Not started | |
| 06 Session and control comfort | Not started | |
| 07 Forgotten defects sweep | Not started | |
| 08 Day temperature curve | Not started | |
| 09 Integration and release preparation | Not started | |

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
| the records commit | docs(roadmap): record milestone 03 of 0.9.1 |

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
