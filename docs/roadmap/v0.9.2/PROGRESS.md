# VEYLON 0.9.2 "Clean Slate" — Progress

One section per milestone of the 0.9.2 prompt pack (`build/release-roadmap/v0.9.2-clean-slate/`,
ignored by Git). Decisions, the retirement inventory and its plan are in [DESIGN.md](DESIGN.md).
Resume at the first milestone below that is not complete.

| Milestone | Status | Commits |
| --- | --- | --- |
| 01 Retirement audit and plan | Complete (documentation only) | `6fb68a2` |
| 02 One wilderness generator | Complete | `a0b6244`, `cb81cbb`, `4007e02`, `b77012d`, `5c28eed`, `e1cbfd9`, `cf9629b`, the records commit |
| 03 One city generator | Not started | |
| 04 World kinds and generation pins | Not started | |
| 05 QA tooling out of the shipped game | Not started | |
| 06 Smooth travel and faster saves | Not started | |
| 07 Playtest kit | Not started | |
| 08 Integration and release preparation | Not started | |

## Milestone 01 — Retirement audit and plan

**Status:** complete on 2026-11-27. Documentation only: no production or test code changed.
**Base:** `main` at `2f78150`, clean apart from an untracked `.vscode/`, equal to the remote `main`,
and tagged `v0.9.1`.

### What was done

1. **The 0.9.1 baseline**, verified against the pack's expectations (below). 0.9.1 is complete and
   released.
2. **The retirement inventory** (DESIGN §5): every generator constant and gate, every
   generator-dependent branch, the fourteen city predicates and their 127 call sites outside
   `UrbanWorldState`, every revision and the four parts a revision number plays, the save code that
   names a revision, the `urban-dev` selector, and the tests and fixtures pinned to a generator
   other than 3 or 20, each with an action.
3. **The removal plan** (DESIGN §6), in dependency order, with what proves identity at each step
   (D-12). Measuring those references needed a throwaway probe (below).
4. **The QA inventory for 05** (DESIGN §7): what the smoke needs, what moves, the production seams,
   the package-private access and the tests that use QA classes.
5. **The before-figures for 06** (below; DESIGN §8).
6. **Records:** this file and DESIGN, linked from [the roadmap index](../README.md).
7. **The pack** was revised where it no longer fits (DESIGN §10, D-19); it is ignored by Git.

### The 0.9.1 baseline

| 00 §2 expects | Found on `2f78150` |
| --- | --- |
| The save epoch and a released-epoch registry | `SaveHeader.EPOCH = 1` (`save/SaveHeader.java:39`); `SaveEpochGuardTest.RELEASED` holds epoch 1, digest `6feb385a093f33b59e74590e5844da16fcaf1c323218962c46449f6a6d93d5b8` (`:99-100`) |
| Three slots with backups | `save/SaveSlots.java` (0.9.1 D-21 to D-25) |
| Autosave | `Autosave.java`, owned by `SaveSession` (0.9.1 D-28 to D-32) |
| Per-world seeds, return to title | `NewWorldSeeds`, `ui/ReturnToTitlePrompt` (0.9.1 D-33, D-35) |
| Parallel tests | `build.gradle:140-158`: two thirds of the logical processors, 1 GiB each |
| CI | `.github/workflows/verify.yml`: Windows x64 on every push and pull request, macOS arm64 for pull requests, weekly and on demand. Run 45949574294 (#5, push) on `2f78150` succeeded, 06:04–06:19 UTC; 0.9.1's records end before it |
| 0.9.1 complete | Its milestone 09 is recorded complete. The owner tagged `v0.9.1` on `2f78150` (annotated tag `fac326a`, "VEYLON 0.9.1 - Safe Saves", 10:11 +03:00), and the release workflow's run 45956730162 (#22) succeeded, 07:11–07:38 UTC, publishing the release "VEYLON: Deep Frontier 0.9.1" with the Windows x64, macOS arm64 and macOS x64 archives and `SHA256SUMS.txt` |

What differs from the pack's expectations, each handled in DESIGN:

- The release workflow does not run the smoke; release preparation does, by hand (D-16).
- The epoch guard digests the generators a load accepts, so the retirement changes a released input
  (D-11, Q-01).
- Generator 20 has no pin of its own for ten of its fifteen fingerprint kinds (D-13).
- Most urban tests build an older city through a shared arena (D-14).
- The snapshot counts of 00 §2 were re-measured (DESIGN §5): 103 test files name a generator 4–19
  (00: 104); 50 QA files hold 18,729 lines (00: 45 and about 16,700); `QaHarness` is at 1,425 of
  1,500 lines (00: 1,492).

### Identity references (the probe)

The plan's identity checks (D-12, D-13) need values 0.9.1's own tree gives. A throwaway test,
`save/RetirementIdentityProbeTest` (kept outside the tree as
`build/v092-m01/probe/RetirementIdentityProbeTest.java`, never committed), was copied into
`src/test/java/com/veylon/save/`, run, and deleted before any other measurement:

`./gradlew.bat test --tests "com.veylon.save.RetirementIdentityProbeTest" -PveylonTestForks=1 --console=plain`

BUILD SUCCESSFUL in 1m 28s, one test passed (`build/v092-m01/probe.log`; its printed lines in
`build/v092-m01/probe-output.txt`).

- Its full digests of `SaveEpochGuardTest.cityGeneration()` and `wildernessGeneration()` are the
  recorded `generation.city` (`b6c505f9…`) and `generation.wilderness` (`f1f93ab3…`), so it computes
  what the guard computes.
- Both texts begin with the same line, the generators a load accepts:
  `loadable 1 2 3 4(1/0/3) 5(2/0/3) 6(3/1/3) 7(4/2/3) 8(5/3/3) 9(6/4/3) 10(7/4/3) 11(8/5/3) 12(9/6/3) 13(10/7/3) 14(11/7/3) 15(12/7/3) 16(13/7/3) 17(14/7/3) 18(15/7/3) 19(16/7/3) 20(17/7/4)`
  (SHA-256 of the line with its newline `ed3b3c439cff320564df3c0604bf99c8e331d97f150a95d627c1b7320bd90935`).
- Without that line the city's text digests to
  `6eb9e20cc2c825216b3daede0990e341857f3028599bc99954a5fb34a5338da8` and the wilderness's to
  `0d671ebdb5a76c49210e4e89f1133886612cee2134f138153560d810a7f76eb0`: D-12's targets.
- Every fingerprint kind of `WorldFingerprints` for the seeds 20260731, −4242 and 7, the values
  D-13's pins take:

| Kind | Generator 20 | Pinned today for generator 20 by |
| --- | --- | --- |
| terrain | `0xb9036588174436d2L`, `0x80223cc179da0afbL`, `0x2aba8a39e5c77757L` | the arrays of generator 14 |
| street plans | `0xac689e14183008bcL`, `0x7a13452a8e7ed9f9L`, `0x2f837887b9bbb30cL` | nothing |
| building plans | `0xb80763b64ac57e1eL`, `0x736ab4750d631341L`, `0xfe117dbe676a8e66L` | nothing (equal to generator 13's pin) |
| furnished buildings | `0xe92dd73e70f86936L`, `0x1bb603fbcef21c85L`, `0xdf916de722bfbb8dL` | nothing |
| districts | `0xdb419af656c21f7fL`, `0xe39ff9511602d687L`, `0x9e70a89c1415ac4bL` | nothing |
| routes | `0x1ef3497d881038faL`, `0xe3ff041873124fd6L`, `0x81faa688c0675baeL` | nothing (equal to generator 13's pin) |
| ecology plans | `0xd597caa1a7b79d04L`, `0x1c85c2c0ef3d9a1eL`, `0xf8e5c6fe62b7addeL` | nothing (equal to generator 14's pin) |
| encounter anchors | `0x941fd7236ab1ddf2L`, `0xbd5abadd6a7d9f9cL`, `0xa892bc58c9e374edL` | nothing |
| survivor sites | `0xd3ea364591b8b1adL`, `0xff4a13963b7e3d69L`, `0x68b0910c940f93a8L` | nothing |
| compounds | `0x8bd36bfeca2d66b8L`, `0x215cd4c07258f90aL`, `0x35f0cd95a186be60L` | nothing (equal to generator 16's pin) |
| compound chunks | `0xefccb539249bc24cL`, `0x92dadb31446b4242L`, `0xc444381a488ff68aL` | nothing (equal to generator 16's pin) |
| wrecks | `0x9d00f321b44c234eL`, `0xe04266f32dd7abdbL`, `0xb83f5b8e2f88b2ecL` | the arrays of generator 17 |
| wreck chunks | `0x239a0a97d395c20dL`, `0x8e19509e968a91f3L`, `0x61c8f5407890e29cL` | the arrays of generator 17 |
| relay | `0xa69545d61a0ae1f7L`, `0x900bb3142bc8a524L`, `0x81ca4e455a8f8685L` | the arrays of generator 19 |
| relay chunk | `0x6c583644a421997fL`, `0xf7d3584d05da8759L`, `0xc9142dbd24c3d5c3L` | the arrays of generator 19 |
| column queries | `0xc7e914449117bdd9L`, `0x82273c0c999fcdc6L`, `0xde29e867023f323cL` | nothing (the same noise as the wilderness's) |

Generator 3's terrain (`0xd9696cfbb47f6234L`, `0x677f1e5e437f686eL`, `0xee26b953c30e802dL`) and
column queries (the last row's values) equal its recorded pins. The probe also printed generators
10–19, which show where the city's figures last changed: the encounter anchors move at generators
14, 16 and 20 (catalogue six eases the starter block), the survivor sites at 16 (compounds join
them) and the furnished buildings of seed 7 at 17 (a wreck in a pinned chunk); every other kind of
generator 20 equals generator 19's.

### Before-figures for 06

All on the owner's host (AMD Ryzen 5 5600X, 12 threads; NVIDIA GeForce RTX 3060 Ti, driver 621.01,
OpenGL 3.3.0; Windows 11 Pro 10.0.26200; Temurin 25.0.4.1+1 from `build/tools/jdk25`), on the code
of `2f78150` after the probe was deleted (`git status` clean), with no other build running. In Git
Bash with `JAVA_HOME` set to `build/tools/jdk25/jdk-25.0.4.1+1`:

```sh
./gradlew.bat cityPerformanceTest --console=plain > build/v092-m01/cityperf-1.log 2>&1   # then -2
VEYLON_GENERATOR=urban-dev VEYLON_SEED=20260731 VEYLON_SCENE=urban_performance \
  VEYLON_RESOLUTION=1280x720 VEYLON_VSYNC=0 VEYLON_GAME_MODE=survival \
  ./gradlew.bat run -PveylonDataDir=build/qa/v092-m01-perf-survival --console=plain \
  > build/v092-m01/perf-tour-survival.log 2>&1
```

The two profile runs (20:16:28–20:17:20 +03:00) each passed 5 of 5 tests in 26 and 24 s. The
tour (20:18:06–20:25:45) printed `pass=true`, `glGetError=0`, 0 KHR errors (four shader
recompilation notices) and 0 OpenAL errors.

**First-touch planning** (`cityPerformanceTest`, economy city, seed 20260731; run 1 / run 2):

| District (region) | Region plan | Buildings | Sites and routes |
| --- | --- | --- | --- |
| Residential (0, 0) | 2.78 / 2.83 ms | 94 in 170.6 / 151.8 ms | 0.98 / 1.05 ms |
| Commercial (−1, −1) | 2.28 / 2.30 ms | 72 in 202.4 / 192.8 ms | 0.52 / 0.53 ms |
| Industrial (0, −2) | 1.68 / 2.01 ms | 64 in 100.5 / 98.5 ms | 6.07 / 6.70 ms |
| Flooded (−3, 3) | 3.07 / 2.90 ms | 90 in 128.2 / 129.3 ms | 0.69 / 0.67 ms |
| Civic (1, 2) | 1.92 / 1.54 ms | 82 in 172.5 / 171.8 ms | 5.14 / 5.16 ms |
| Quarantine (−3, −1) | 1.55 / 1.50 ms | 84 in 148.7 / 145.9 ms | 1.73 / 1.59 ms |

A cold `newWorld` took 789.6 and 540.5 ms (121 chunks, one region plan, 51 building plans; 0.9.1's
release run read 470.6 ms), three warm ones 145.4 and 144.9 ms on average.

**Streaming** (60 s at each pace; run 1 / run 2; the simulation's maximum is one frame per run):

| Pace | Chunks generated, meshed | Simulation step p99 / max | CPU frame p99 / max |
| --- | --- | --- | --- |
| Walk, 4.3 blocks/s | 344, 398 | 0.154 / 89.2; 0.158 / 82.8 ms | 7.03 / 97.3; 6.71 / 90.6 ms |
| Sprint, 6.7 blocks/s | 479, 518 | 0.051 / 82.2; 0.041 / 80.5 ms | 7.11 / 89.4; 6.86 / 87.5 ms |
| Flight, 21.6 blocks/s | 1,314, 1,220 | 0.047 / 82.7; 0.048 / 80.8 ms | 8.27 / 89.9; 8.00 / 87.7 ms |

The dense scenes' slow ticks reached at most 6.47 / 6.72 ms (the street) and 4.56 / 4.43 ms (the
apartment basement); every other scene stayed under 2.1 ms.

**The 8 km journey** (fast flight, one building's containers searched every kilometre, a save
through slot 1 and a load from it at each kilometre; run 1 / run 2):

| km | Save size | Save | Load (chunks regenerated) | Step and stream max over the kilometre | Retained chunks, heap (run 1) |
| ---: | ---: | --- | --- | --- | --- |
| 1 | 12,505 B | 102.2 / 27.9 ms | 142.3 / 141.9 ms (130) | 78.3 / 84.6 ms | 1,155, 55.4 MiB |
| 2 | 19,265 B | 33.4 / 34.1 ms | 180.4 / 187.5 ms (138) | 27.6 / 28.4 ms | 1,837, 73.0 MiB |
| 3 | 23,396 B | 43.3 / 43.3 ms | 212.6 / 204.2 ms (143) | 24.3 / 25.0 ms | 2,000, 77.5 MiB |
| 4 | 36,134 B | 64.2 / 74.6 ms | 324.9 / 333.6 ms (150) | 17.1 / 16.9 ms | 1,916, 75.7 MiB |
| 5 | 39,815 B | 85.7 / 87.7 ms | 371.6 / 384.5 ms (158) | 19.8 / 19.9 ms | 1,818, 72.4 MiB |
| 6 | 39,747 B | 108.9 / 108.5 ms | 387.4 / 400.2 ms (164) | 27.9 / 28.8 ms | 1,987, 76.6 MiB |
| 7 | 41,855 B | 104.1 / 107.0 ms | 402.4 / 413.1 ms (168) | 27.7 / 28.4 ms | 1,893, 74.7 MiB |
| 8 | 43,822 B | 122.6 / 129.6 ms | 465.3 / 478.2 ms (180) | 28.0 / 29.7 ms | 1,803, 73.0 MiB |

Run 1's first save, 102.2 ms, stands alone: run 2 read 27.9 ms and 0.9.1 milestone 09 28.1–29.8 ms
for the same save. Otherwise the two runs agree within 5 %, but for the 4 km save (64.2 against
74.6 ms). Save time grows with the explored city, from about 28 to 126 ms, and load time from 142
to 472 ms, as urban D-246 recorded.

**The native tour** (Survival, 1280 × 720, render distance 6, shadows 1, bloom and FXAA on, VSync
off, seed 20260731; 16 legs in 453 s; heap 38.6 MiB after collection at the end):

| Leg | Average FPS | Frame p99 / max | Simulation step max |
| --- | ---: | --- | --- |
| Street walk across the region seam, 28 s | 1,077.7 | 1.48 / 33.2 ms | 15.0 ms |
| Apartment kitchen / upper floor / basement | 966.5 / 956.5 / 1,008.0 | ≤ 1.87 / ≤ 10.9 ms | ≤ 3.5 ms |
| Transit platform / tunnel | 1,024.6 / 1,013.6 | ≤ 1.54 / ≤ 9.2 ms | 8.4 / 4.1 ms |
| Stronghold yard | 1,028.0 | 1.55 / 25.8 ms | 21.5 ms |
| Quarantine growth | 1,045.7 | 1.51 / 22.1 ms | 19.9 ms |
| Storm with a building fire | 934.2 | 1.77 / 10.3 ms | 7.3 ms |
| Fast travel, minutes 1–5 (21.6 blocks/s above the roofs) | 891.2, 949.9, 855.1, 1,009.4, 1,049.8 | 3.85, 3.08, 3.91, 2.52, 2.28 / 30.6, 38.4, 29.8, 34.6, 29.9 ms | 28.1, 32.3, 28.1, 33.7, 28.9 ms |
| After the journey | 1,977 chunks retained, 81.5 MiB after collection | | |
| F5, then F9, on the street | | 189.7 ms, then 821.4 ms | |

The travel's slow ticks are a little above the 23–30 ms urban package 24 recorded on 2026-11-05,
and F5 and F9 slower than its 104.4 and 546.4 ms; since then 0.9.1 added the slot save's copy and
rotation and the checksum (0.9.1 D-21, D-22). The tour reports each minute's maximum, not how many
ticks are slow, so 06 should count the slow ticks above its target as well.

### Changed paths

- New: `docs/roadmap/v0.9.2/DESIGN.md`, `docs/roadmap/v0.9.2/PROGRESS.md`.
- `docs/roadmap/README.md`: the 0.9.2 row links the records.
- Ignored by Git: the pack's prompts (`build/release-roadmap/v0.9.2-clean-slate/`, DESIGN §10); the
  evidence in `build/v092-m01/` (the probe's source, its log and output, both profile logs and the
  tour's log) and the tour's data directory `build/qa/v092-m01-perf-survival/`.

### Validation

- `git status` and `git log` at the start; `git ls-remote origin` returned `2f78150` for `main` and
  for `v0.9.1^{}`. The workflow runs and the release were read from the repository's public API.
- Every relative link and anchor in the three records resolves, and every source and test path
  DESIGN names exists (`module-info.java` is named because there is none), checked by a script
  over the files that a deliberately broken link and anchor made fail; `git diff --check` is clean.
  The new records are LF, like the 0.9.1 records.
- The probe and the measurements above ran the code of `2f78150` unchanged.
- Not run: the portable build, since no build input changed (AGENTS.md: documentation-only changes
  need link, path and diff checks), and `graphify update`, which AGENTS.md skips for
  documentation-only work.

### Commits

| Commit | Subject |
| --- | --- |
| `6fb68a2` | docs(roadmap): record the 0.9.2 retirement audit and plan |

No commit carries a co-author line. At the owner's request it is pushed to `origin/main`.

### Limitations and pending gates

- **Q-01 is open** (DESIGN §11): whether 0.9.1's saves should open in 0.9.2. Until the owner
  answers, 02 raises the epoch (D-11); the choice can be reversed until 08.
- Source line numbers are as of `2f78150`.
- The test decisions are per file, with the methods that differ named. 03 may revise one when a
  retargeted test shows a difference that D-14 does not explain, recording why.
- The before-figures are two headless runs and one native run on one afternoon; 0.9.1 measured the
  host drifting by 4–5 % over three weeks, so 06 measures its own before and after side by side.

### Next: milestone 02

Start with step 1 of DESIGN §6: in `SaveEpochGuardTest`, move the loadable line into its own
`loadable` input and, unless the owner has answered Q-01 otherwise, raise `SaveHeader.EPOCH` to 2,
recording epoch 2's inputs with `generation.*`, `enums` and `layout.*` at D-12's values; move
`SaveEpochRefusalTest:65` and `ui/SlotPickerScreenTest:158-160` with the epoch. Then retarget the
fifteen generator-1 and generator-2 tests of DESIGN §5.9 while both generators still exist.

## Milestone 02 — One wilderness generator

**Status:** complete on 2026-11-27.
**Base:** `main` at `6fb68a2`, equal to the remote `main`, clean apart from an untracked `.vscode/`.
The Verify workflow's run 46043427912 (#6, push) on `6fb68a2` succeeded: Windows x64, 17:38–17:53
UTC.

### What was done

The steps of DESIGN §6 for 02. Each revision was built and tested on its own tree before it was
committed.

1. **Epoch 2** (D-11; `a0b6244`). `SaveHeader.EPOCH` is 2. This build writes and opens epoch 2
   alone: it refuses every save of epoch 1, 0.9.1's included, as written by an older version, before
   it touches the live world and without changing the file. 0.9.1's header check, which this build
   left unchanged but for the constant, refuses an epoch-2 save as newer (read from the code, not
   run). The owner has not answered Q-01, so D-11's default applies; the choice
   stays open until 08. `SaveEpochRefusalTest`'s epoch assertion and the slot picker's "save epoch"
   line moved with the epoch; the refusal tests that derive another epoch from `SaveHeader.EPOCH`
   now refuse epoch 1 as older.
2. **The guard's `loadable` input** (D-12; `cb81cbb`). `SaveEpochGuardTest` digests the generators
   a load accepts as an input of its own, and `generation.*` digest only what the two generators
   make.
3. **Retargets** (`4007e02`), while generators 1 and 2 still existed. The eleven test classes of
   DESIGN §5.9 that used generator 1 or 2 as a convenient world build generator 3: `PathfinderTest`,
   `PlayerMovementSystemTest`, `PlayerTreatmentSystemTest`, `EnvironmentalSimulationTest`,
   `RaycasterTest`, `WorldCoordinateContractTest`, seven tests of `DeepCaveGenTest`,
   `SettlementPlannerTest`, `FortressGameplayIntegrationTest`, `EntityEcologyTest` and
   `UrbanRefugesSectionTest`. The pathfinder and movement arenas generate every chunk before they
   flatten any, so no neighbour's tree reaches into them.
4. **Retirement** (`b77012d`). `GEN_LEGACY` and `GEN_DEEP` are gone and `World.isSupportedGenerator`
   accepts 3 and 4–20: the `World` constructor throws for 1 and 2, and a save whose header names
   either is refused as damaged before any world is built. Their pins went with them (two tests of
   `WorldGeneratorFingerprintTest` with their arrays, `DeepCaveGenTest.legacyWorldsKeepLegacyTerrain`).
   `UrbanGeneratorDispatchTest` and `UrbanRevisionTest` name 1 and 2 only as refused ids,
   `CorruptSaveResilienceTest` proves the refusal with its reason, and `UrbanIndoorEnvironmentTest`'s
   loop over the generators without buildings starts at 3. `loadable` was re-recorded. B19's `World`
   comments were corrected.
5. **Collapse** (D-07; `5c28eed`). `World.hasDeepFrontier` and `hasCaveIdentities` are gone. Where
   they also ran in a city, they became "not a city": `World.settlementForRegion` and
   `isBasaltFumarole`, `EntityManager`'s Gloomstalker depth rule, and the smoke's fortress approach,
   snapshot and search in `QaHarness`. Inside `WorldGenerator` they became true, and `carveCaves`,
   generator 2's depth thresholds with `RESONANT_TOP` and the conditions around the powder ores, the
   settlement clearance, the POI reservations, the relic loot and the underground POIs, cave mouths
   and settlements went. `BASALT_TOP` stays: the sulfur veins form below it. Every surviving branch
   keeps its text (`git diff -w` shows only removed conditions), and the underground POI plan keeps
   generator 3's branch, which draws no random number for a nest's or a shrine's height.
6. **Documents** (`e1cbfd9`, `cf9629b`). ARCHITECTURE (the save policy's status, the guard's inputs,
   the generator list, the gates, the fingerprint pins and two notes on generators 3–6), DEVELOPING
   (the guard and the generation rule), the save skill, and CHANGELOG [Unreleased], which tells
   players that 0.9.1's saves do not open.
7. **Records:** this section, the status table, 01's commit hash, and DESIGN's B19 and Q-01 rows.

**Order.** DESIGN §6 lists the collapse (its step 3) before the removal of the constants (its
step 4); they ran the other way round. With the refusal first, every revision either builds
generators 1 and 2 with their own code or refuses them, whereas the collapse first would have left
a revision in which generator 3's code built a world that named generator 1 or 2. 03's plan
already refuses (its step 3) before it collapses (its step 4).

### Identity (D-12)

The guard's inputs at each revision, by their first eight hex digits (the full values are in
`SaveEpochGuardTest.RECORDED`):

| Input | Epoch 1 (0.9.1) | `a0b6244` | `cb81cbb` | `b77012d` | `5c28eed` |
| --- | --- | --- | --- | --- | --- |
| `enums` | `b2c78dd0` | `b2c78dd0` | `b2c78dd0` | `b2c78dd0` | `b2c78dd0` |
| `generation.city` | `b6c505f9` | `b6c505f9` | `6eb9e20c` | `6eb9e20c` | `6eb9e20c` |
| `generation.wilderness` | `f1f93ab3` | `f1f93ab3` | `0d671ebd` | `0d671ebd` | `0d671ebd` |
| `layout.city` | `d92581c6` | `d92581c6` | `d92581c6` | `d92581c6` | `d92581c6` |
| `layout.wilderness` | `2aa9698e` | `2aa9698e` | `2aa9698e` | `2aa9698e` | `2aa9698e` |
| `loadable` | inside `generation.*` | inside `generation.*` | `ed3b3c43` | `15958036` | `15958036` |
| Digest | `6feb385a`, released | `6feb385a`, epoch 2 | `b07d3d02` | `be60041f` | `be60041f` |

- From `cb81cbb` on, `generation.city` and `generation.wilderness` are D-12's targets, the values
  0.9.1's tree gives for the same texts. `enums` and both layouts never moved. From `b77012d` only
  `loadable` moved, to `loadable 3 4(1/0/3) … 20(17/7/4)`; the collapse moved nothing.
- `WorldGeneratorFingerprintTest`'s generator-3 pins (terrain and column queries) are untouched and
  pass, as do `ChunkMesherFingerprintTest`, `WorldSeedDeterminismTest` and `DeepCaveGenTest`'s
  chunk-order cases, now on generator 3.

### Findings

- **The stalker rule's surface lookup.** `EntityManager.slowTick` looked up the surface under the
  player for every world, although only generators 1 and 2 read it; the collapse removed it. The
  lookup generated the player's chunk when a slow tick found it missing. The frame's streaming step,
  which runs after the simulation and generates the nearest missing chunk first, now generates it
  later in the same frame. `spawnStalkerIfValid` already refuses unloaded columns, and no test or
  native run showed a difference.
- **The smoke's fortress snapshot** no longer prints `generator=legacy`, which only generator 1
  printed; a city still prints `{available=false,generator=urban}`.
- **B19** is fixed in `World` (`b77012d`); its `UrbanWorldState` part stays with 03.
- **The fifteen files of DESIGN §5.9.** Eleven were retargeted. The other four changed with the
  retirement, because their subject was generators 1 and 2 themselves: `WorldGeneratorFingerprintTest`,
  `UrbanGeneratorDispatchTest`, `UrbanRevisionTest` and `UrbanIndoorEnvironmentTest`. DESIGN put the
  last one's historical test in 03; its loop had to start at 3 here because the constant went, and
  03 still removes its generators 4–6. Three tests were deleted with their subject;
  `legacyWorldsKeepLegacyTerrain`'s "never plans settlements" check stays proven for the city by
  `UrbanGeneratorDispatchTest.noWildernessFeatureReachesAnUrbanWorld`.
- **The README** describes 0.9.1, which wrote epoch 1, and stays true for that release; 08 writes
  0.9.2's paragraph and its known limitation.

### Native evidence

`build/v092-m02-native.sh <checkout> <phase>` runs the wilderness release smoke from a checkout
(`VEYLON_GENERATOR=wilderness VEYLON_SMOKE=30`, with its fortress approach), with a capture at 1 s,
before the smoke changes anything. Seed 20261127, 1280 × 720, Survival and Creative, each run in its
own data directory `build/qa/v092-m02-<phase>-wild-<mode>`. The 0.9.1 side ran from a detached
worktree of `v0.9.1` (`build/v092-m02-v091`, at `2f78150`), this side from `5c28eed`. VSync was on,
so frame rates sit at the monitor's 144 Hz. The first two 0.9.1 runs overlapped the portable build
of `5c28eed`, which lowered their frame rate; they were repeated on an idle machine.

| Run | Survival approach | Creative approach |
| --- | --- | --- |
| 0.9.1, during the build | 1,660 frames, 138.3 FPS | 1,489 frames, 124.1 FPS |
| 0.9.1, idle | 1,727 frames, 143.8 FPS | 1,729 frames, 144.0 FPS |
| `5c28eed` | 1,728 frames, 143.9 FPS | 1,727 frames, 143.8 FPS |
| `5c28eed` again (control) | 1,728 frames, 143.9 FPS | 1,729 frames, 144.0 FPS |

All eight runs exited 0 (the smoke throws on any failed gate), saved and loaded through their
isolated slot, completed the fortress approach, and reported no GL, KHR or `glGetError` errors.
Every run planned the same fortress: id −25769803771, region −6,5, centre −2134,2022, 2,940 blocks
from the spawn, registered.

The spawn captures, compared pixel by pixel (921,600 pixels; those differing by more than 8 of 255
in a channel):

| Pair | Survival | Creative |
| --- | --- | --- |
| 0.9.1 (idle) against `5c28eed` | 496 (0.054 %) | 3,444 (0.374 %) |
| `5c28eed` against itself (control) | 633 (0.069 %) | 1,768 (0.192 %) |
| 0.9.1 (during the build) against `5c28eed` | 3,566 (0.387 %) | 3,187 (0.346 %) |

The differences are swaying plants and an even, pixel-wide speckle over textured faces (masks in
`build/v092-m02/`), the same as between two runs of one build; no block differs. The binding proof
of identical generation is the guard and the pins above.

### Changed paths

- Production: `save/SaveHeader.java`, `world/World.java`, `world/WorldGenerator.java`,
  `entity/EntityManager.java`, `QaHarness.java`.
- Tests: `save/SaveEpochGuardTest`, `save/SaveEpochRefusalTest`, `save/CorruptSaveResilienceTest`,
  `save/UrbanRefugesSectionTest`, `ui/SlotPickerScreenTest`, `world/WorldGeneratorFingerprintTest`,
  `world/DeepCaveGenTest`, `world/UrbanGeneratorDispatchTest`, `world/RaycasterTest`,
  `world/WorldCoordinateContractTest`, `world/urban/UrbanRevisionTest`, `ai/PathfinderTest`,
  `entity/PlayerMovementSystemTest`, `entity/PlayerTreatmentSystemTest`, `entity/EntityEcologyTest`,
  `settlement/SettlementPlannerTest`, `settlement/FortressGameplayIntegrationTest`,
  `simulation/EnvironmentalSimulationTest`, `UrbanIndoorEnvironmentTest`.
- Documents: `docs/ARCHITECTURE.md`, `docs/DEVELOPING.md`,
  `.agents/skills/veylon-save-compatibility/SKILL.md`, `CHANGELOG.md`, this file and DESIGN.
- Ignored by Git: the logs, comparison masks and counting script in `build/v092-m02/`, the native
  script `build/v092-m02-native.sh`, the worktree `build/v092-m02-v091`, the data directories
  `build/qa/v092-m02-*` and the pack's status line for 02.

### Validation

In Git Bash with `JAVA_HOME` set to `build/tools/jdk25/jdk-25.0.4.1+1`:

| Revision | Check | Result |
| --- | --- | --- |
| `a0b6244` | `./gradlew.bat test --tests "com.veylon.save.*" --tests "com.veylon.ui.SlotPickerScreenTest" --tests "com.veylon.SlotPickerRoutingTest" --tests "com.veylon.SaveRefusalRoutingTest" --console=plain` | 181 tests in 40 classes passed |
| `cb81cbb` | `SaveEpochGuardTest` | 4 tests passed |
| `4007e02` | the eleven retargeted classes | 80 tests passed |
| `b77012d` | `./gradlew.bat build --console=plain` | BUILD SUCCESSFUL in 5m 44s: 2,523 tests in 345 classes passed, 2 skipped (both need symbolic links, which this host refuses); Javadoc and doclint passed |
| `5c28eed` | the D-12 checks and the wilderness suites: `WorldGeneratorFingerprintTest`, `WorldSeedDeterminismTest`, `ChunkMesherFingerprintTest`, `SaveEpochGuardTest`, `DeepCaveGenTest`, `CaveIdentityGenerationTest`, `CaveToolsGameplayIntegrationTest`, `SettlementPlannerTest`, `FortressGameplayIntegrationTest`, `EntityEcologyTest`, `UrbanGeneratorDispatchTest`, `BeaconEndgameIntegrationTest` | 68 tests passed; the guard's digest unchanged |
| `5c28eed` | `./gradlew.bat build --console=plain` | BUILD SUCCESSFUL in 5m 39s: 2,523 tests in 345 classes passed, 2 skipped as above |
| `e1cbfd9`, `cf9629b` | a link checker over the changed documents; `git diff --cached --check` | every relative link and anchor resolves; clean |

2,523 tests are 0.9.1's 2,526 less the three deleted ones. After the code, `graphify update .
--no-cluster` rebuilt the graph: 13,084 nodes, 55,164 edges. Not run: the performance tasks (nothing
on a measured path changed but removed conditions and one lookup) and the packaged builds (08).

### Commits

| Commit | Subject |
| --- | --- |
| `a0b6244` | feat(save): open only saves of epoch 2 |
| `cb81cbb` | test(save): digest the generators a load accepts as an input of their own |
| `4007e02` | test(world): build the tests that used generators 1 and 2 on the wilderness |
| `b77012d` | refactor(world): retire generators 1 and 2 |
| `5c28eed` | refactor(world): collapse the deep-frontier gates only the wilderness passed |
| `e1cbfd9` | docs(save): describe save epoch 2 and the guard's loadable input |
| `cf9629b` | docs(world): describe the wilderness as the only generator before the cities |
| the records commit | docs(roadmap): record milestone 02 of 0.9.2 |

No commit carries a co-author line. At the owner's request they are pushed to `origin/main`.

### Limitations and pending gates

- **Q-01 is open** (DESIGN §11). Under D-11's default a build of `main` refuses 0.9.1's saves. If the
  owner keeps epoch 1, 08 reverts the epoch, adds the guard's subset rule (D-11) and opens a 0.9.1
  save natively.
- The Verify workflow's run on the pushed commits had not finished when this was written.
- DESIGN's source line numbers are as of `2f78150`; `World`, `WorldGenerator`, `EntityManager` and
  `QaHarness` changed in 02.

### Next: milestone 03

03 must record this records commit's hash in the table above. Then step 1 of DESIGN §6 for 03: add
the direct generator-20 pins of D-13, with the values in milestone 01's section, in a commit of
their own; then move the test arenas to the city newest first (D-14). Through 03 the guard's
`generation.*`, `enums` and `layout.*` keep the values above; only `loadable` may move.
