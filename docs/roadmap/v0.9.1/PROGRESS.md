# VEYLON 0.9.1 "Safe Saves" — Progress

One section per milestone of the 0.9.1 prompt pack (`build/release-roadmap/v0.9.1-safe-saves/`,
ignored by Git). Decisions, scope and the defect ledger are in [DESIGN.md](DESIGN.md). Resume at
the first milestone below that is not complete.

| Milestone | Status | Commits |
| --- | --- | --- |
| 01 Release plan and defect audit | Complete (documentation only) | `95d09c5`, then the records commit that adds this file; 02 records its hash |
| 02 Test reliability and continuous integration | Not started | |
| 03 Current-version save epoch | Not started | |
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
