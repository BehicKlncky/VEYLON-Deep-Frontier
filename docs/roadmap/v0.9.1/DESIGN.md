# VEYLON 0.9.1 "Safe Saves" — Design

Written by milestone 01 on 2026-11-19 against `main` at `95d09c5` (build version 0.9.0).
Milestone 02 added D-10 to D-15 and closed B02 and B16 the same day. Milestone 03 added D-16 to
D-20, closed B11 and settled §8 on 2026-11-20. Source line numbers are as
of `95d09c5`: re-verify one before relying on it. Milestone
records are in [PROGRESS.md](PROGRESS.md); the index of all versions and the owner decisions
O1–O9 is [the roadmap index](../README.md).

## 1. Intent

0.9.0 shipped the abandoned city. Before more content, the owner wants a short stabilization
release: a player's progress must be hard to lose, every defect that earlier work recorded and
left behind must be closed, and development must become faster and more reliable so that later
releases move quicker.

## 2. Owner decisions

Only the owner can change these.

| Id | Decision |
| --- | --- |
| O1 | Only saves written by the current version must open. Saves from earlier releases (0.2.0 to 0.9.0) and worlds of the development generators 4–19 may be refused, and a refused save is never modified or deleted. The authoritative text is the [save compatibility policy](../../ARCHITECTURE.md#save-compatibility-policy-owner-decision-2026-11-06) (2026-11-06). |
| O2 | Forgotten defects are closed: anything recorded as pre-existing, not fixed or left for later that is still present gets fixed, unless the owner explicitly accepts it. §5 is the list. |
| O3 | Three save slots. |

The 2026-11-19 decisions O6–O9 bind later releases. For 0.9.1 they mean one rule: fix defects
in the city layout code that 0.9.3 replaces, but do not polish or extend it.

## 3. Decisions

D-01 to D-07 are the defaults the prompt pack adopted for the owner; the owner may change any of
them. D-08 onward are this release's own.

| Id | Decision | Milestone |
| --- | --- | --- |
| D-01 | **Save epoch.** The save header carries an integer epoch. A build opens only saves of its own epoch and refuses every other before it touches the live world, leaving the file byte-identical. 0.9.1 starts at epoch 1, so every save written by 0.9.0 or earlier is refused. | 03 |
| D-02 | **Slots.** Three numbered slots, each holding one current save and up to two rotating backups. The 0.9.0 single file `saves/veylon.sav` is neither imported (O1) nor touched. | 04 |
| D-03 | **Autosave.** On by default. It writes to the active slot after waking from sleep, after claiming a refuge bed and every 10 real minutes when it is safe; never while dead, on an end card, burning, falling, hunted or with a pausing screen open. The player can turn it off or change the interval. | 05 |
| D-04 | **Return to title.** The pause menu offers Save and return, Return without saving, and Cancel. | 06 |
| D-05 | **Seed.** NEW FRONTIER shows the seed, accepts typed digits or text (text is hashed the way `VEYLON_SEED` is) and rerolls on a key. `VEYLON_SEED` still wins for QA. This answers urban P-06. | 06 |
| D-06 | **Mouse.** A sensitivity setting whose default equals today's fixed `0.115`, and an invert-Y toggle, persisted with the other settings. | 06 |
| D-07 | **Temperature.** The inverted day curve (urban P-15, B14) is fixed in every world, so the day is warmest in the afternoon: O1 removes the reason it was left alone. | 08 |
| D-08 | **Ledger classes.** A *defect* is behaviour or text that contradicts the game's own design, documentation or decisions, including a documented rule the code breaks and a guard a decision left untested. A *design limit* is a limit the documents present as deliberate. An *owner question* needs a decision only the owner can make. Defects are fixed in 0.9.1 (O2) or accepted by the owner; limits and questions are listed, not fixed. Each defect names one owning milestone, and 07 owns any that no closer milestone owns. | 01 |
| D-09 | **Baseline method.** The before-figure for 02 is one full `build --rerun-tasks --console=plain` at `4b6bed9`. The three commits after 0.9.0 changed only documents, so a plain `build` could have reused up-to-date task results; forcing every task matches the 2026-11-06 snapshot and 02's own acceptance runs. | 01 |
| D-10 | **Temp-directory cleanup (B02).** The cause is outside the JVM. On the owner's host a process with administrator rights writes a copy, `NAME.tmp`, beside a freshly saved file when a test deletes it, and removes the copy within about 6 ms. By every sign that process is the on-access antivirus: Kaspersky is the only active one, and Defender is off. JUnit 5.11.4 deletes a directory's files and then the directory at once, so it meets a directory that is not empty, or a copy it may not delete. Nothing in the JVM holds a file. The game is unaffected, because replacing a save makes no copy. The fix keeps JUnit's cleanup and gives it a head start. The suite's default `@TempDir` factory (`TrackedTempDirFactory`) records every directory that JUnit always cleans. An after-each and after-all extension (`TempDirCleanup`) deletes it first, with up to eight attempts over about 1.9 s, and logs each retry. A directory still present after that is left to JUnit's own cleanup, which reports it as before. `TempDirFactory` is an experimental JUnit API, so a JUnit upgrade must recheck it. | 02 |
| D-11 | **A data directory per test worker.** Each test JVM uses `build/test-data/<task>/worker-<id>`, where `<id>` is Gradle's `org.gradle.test.worker`. A launcher session listener (`WorkerDataDirectory`) sets it before any test class is discovered, so static paths such as `SaveSystem.SAVE_PATH` resolve inside it. The task's directory is emptied when the task starts, because worker ids keep growing for as long as a Gradle daemon lives. | 02 |
| D-12 | **Parallel forks and their heap.** `test` runs in two thirds of the logical processors' worth of worker JVMs, at least one, each with a 1 GiB heap; `-PveylonTestForks=N` overrides the count. On the owner's 12-thread host 8, 10 and 12 workers finished the suite in 263–268 s, 6 took 290–296 s, 4 took 334 s and 3 took 424 s, so 8 is where it stops getting faster. Gradle's default 512 MiB heap left the heaviest city worker at its ceiling in every run, with evacuation failures and compacting full collections; 1 GiB removed both at the same speed. The calibrated `performanceTest`, `rainPerformanceTest` and `cityPerformanceTest` keep one worker each and their own settings. | 02 |
| D-13 | **Continuous integration.** `.github/workflows/verify.yml` runs `build` on Windows x64 for every push and pull request; `release-recovery/**` pushes are left to the release workflow, which builds them already. macOS arm64 runs for pull requests to `main`, every Monday at 04:00 UTC and on demand, not on every push: a hosted macOS runner is slower and scarcer, and the release workflow already gives its full build 30 minutes. A failed run uploads the test reports. `release-build.yml` is unchanged. The first run waits until the owner pushes. | 02 |
| D-14 | **No quick test task.** The milestone left one optional. With D-12 the whole test task takes about four and a half minutes. A task that skipped the heaviest classes would need a tag on each of them and a rule for tagging new ones, and it would be a second suite that could be mistaken for the first. `--tests` filters remain the quick path while iterating, and `build` runs everything. | 02 |
| D-15 | **Foreign files in save folders.** D-10's antivirus copies can also appear in a player's save folder when the game deletes a file it has just written, such as a rotated backup or a failed save's temporary file. Slot listing and backup rotation (04) must ignore files the game did not write, and must not fail when a directory is briefly not empty. | 04 |
| D-16 | **The header, and refusal from it.** Format 4 begins `VEYL`, the format, the save epoch, the seed and the generator id (`save.SaveHeader`); every later format keeps the magic, the format and the epoch at those offsets, so any build tells a save it must refuse from a damaged one. Formats 2 and 3 (0.1.0 to 0.9.0) carry no epoch and are older by format. The header carries no game version: the epoch alone decides, older and newer follow from comparing it, a development run has no version to write (the jar manifest holds it), and 04 can keep whatever a slot summary shows in its own record. `SaveSystem.open` reads the header first; another format or epoch builds no world, never reaches the isolated verifier pass, leaves the live world as it was and never touches the file. Its `LoadResult` says why for the player (older version, newer version, not a VEYLON save, damaged, unreadable, no save): a sentence LOAD FRONTIER shows under the title menu, and a line of at most 32 characters that F9 writes to the log, since the HUD's log panel cut the sentence short in the native check. The technical reason goes to the console. | 03 |
| D-17 | **What an epoch means, and its guard.** Two builds share an epoch only if each opens every save the other writes, into the same world and state. `SaveEpochGuardTest` digests what decides that as five recorded inputs: a sample save of each world kind (header, core body with one record of each list, the extension tail and every section's version), the enum tables a save stores by ordinal, name, code or id (with each body family's pieces and the faction ids), and the generation of both world kinds through the shared `WorldFingerprints` (with the settlements the wilderness plans, what city containers hold on their first search, the generators a load accepts and the city revisions each names). While no release has shipped the current epoch, a changed input is re-recorded. Once `RELEASED` names it, any change fails with the instruction to raise `SaveHeader.EPOCH`; a released entry is never edited. An appended enum constant counts as a change, because a build of the same epoch must read every save of that epoch; appending is still preferred, since it keeps ordinals stable while an epoch is developed. `RELEASED` stays empty until 09 adds epoch 1 with the digest the passing test prints. | 03, 09 |
| D-18 | **Strict reading within an epoch.** The reader accepts exactly what the writer writes. Refused: a save without the extension tail or with its version 1; a section id the build does not know; a save missing any of the eleven sections every save holds (lanterns, party state, counterattacks, quest target, settlement reputation, active explosives, keg attribution, game mode, Creative controls, bodies, remains); `world.bodies` version 1; a body family, piece id or joint table `world.remains` does not have; a city save without `urban.meta`, or naming another content revision than `UrbanRevision.contentFor` its generator; a city that manages containers without `urban.containers`; an affliction or drying-rack ordinal past its enum; more lit kegs than the explosion system holds. `world.fixtures`, `urban.discovery` and `urban.visits` are written only when they hold something, so their absence means "nothing". The runtime rules for earlier content revisions (content 1's empty markers, content 2 in tests) stay until 0.9.2 retires generators 4–19, as §4 asks; only their save-loading code went. | 03 |
| D-19 | **One record of the settled pieces.** The writer no longer writes `world.fragments` beside `world.remains`: it existed so that 0.8.0 could open a newer save, which O1 does not ask for. `FragmentsSection` is gone, and a save that still holds the section is refused as unknown. | 03 |
| D-20 | **Evidence without fixtures.** The authentic saves of 0.2.0, 0.3.0, 0.8.1 and urban packages 06 and 20, and every test that loaded them, are gone. Refusal tests put 0.9.0's header, format 2, later formats, other epochs and cut headers on a save this build wrote (`ForeignSaves`); the native check writes a real 0.9.0 save with the tagged build. The quest constructor and `Quest.Status.LEGACY_UNBOUND` stay, since offers are built through the one and the HUD's QA scene stages the other; the two quest event overloads without context had no caller and went. | 03 |

## 4. What 0.9.1 does not include

- 0.9.2: retiring generators 1, 2 and 4–19 with every layout, template and content revision;
  world kinds; moving QA tooling out of the shipped game; the look-ahead planner; faster saves
  for large cities (urban D-246); the playtest journal.
- 0.9.3 and 0.9.4: the rebuilt city, its countryside and natural land, and canon names in
  player-facing text (O6–O8).
- 0.10.0: infection, from the forearm bite to the antiserum (O4).
- 0.11.0: graphics and render distance (O5).
- Balance retuning beyond what a ledger fix needs; 08 re-measures the economy after D-07.
- Merging, tagging, pushing and publishing. Only milestone 09 bumps the version and writes the
  release notes.

## 5. Defect ledger

Source paths are under `src/main/java/com/veylon/` unless another root is named. A *record* is
the document that reported the defect. Every entry was checked against current source on
2026-11-19.

| Id | Defect | Evidence | Status | Owner |
| --- | --- | --- | --- | --- |
| B01 | A city's pause menu reads "The abandoned city / development build". Urban M26 removed only NEW FRONTIER's development notice. | `ui/PauseMenu.java:61` (the constant), `:41-43` (drawn for every `world.isUrban()`). Record: [urban PROGRESS](../../urban-survival/PROGRESS.md) lines 3765-3767. | Confirmed | 06 |
| B02 | JUnit's `@TempDir` cleanup fails now and then on Windows (`DirectoryNotEmptyException`), failing an otherwise green build. 84 of 331 test files use `@TempDir`; nothing configures its cleanup. | Snapshot log `build/roadmap-review-build.log` (ignored; 2026-11-06): `UrbanEcologySaveTest` failed only on cleanup. Records: [urban VALIDATION](../../urban-survival/VALIDATION.md) lines 3538, 3543 and 3558-3562 (M22), 3911-3914 (M24), 4128-4133 (M25). | Fixed in 02 (`91975e9`). The cause is outside the JVM (D-10); PROGRESS records the reproduction | 02 |
| B03 | Captives, leaders and the residents of a bedless settlement spawn 0.4 above the floor. In a fort's cage the head enters the roof bars, and the person can never move. | `settlement/SettlementManager.java:697-699` (`specialPost ? 0.4f : 0.02f`). Record: [ALL_LIVING_COMBAT_FIRE_PROGRESS](../../engineering/ALL_LIVING_COMBAT_FIRE_PROGRESS.md) lines 1041-1047 and 1609. | Confirmed | 07 |
| B04 | `ModelPart.resetPose` zeroes the build-time rotation of the quiver, the slung bow and the slung gun on every pose, so they hang straight. Only the raider's spear gets its angle back. | `gfx/model/ModelPart.java:127-129`; `gfx/model/NpcModels.java:83, 90, 97`; `gfx/model/Animator.java:317` (reset in every pose) and `:482-484` (the spear re-angled). | Confirmed. Only the 0.8.0 rendering work's session notes report it; no repository document does | 07 |
| B05 | A whole carcass loses its lodged arrows on save and load. Only a fragmented carcass's remains link keeps them. | `save/SaveSystem.java:307-317` (write) and `:664-674` (read) store no arrows; `save/RemainsSection.java:200-201` and `:284-285` carry them for links. Records: ALL_LIVING_COMBAT_FIRE_PROGRESS lines 668-669; [the contract](../../engineering/ALL_LIVING_COMBAT_FIRE_CONTRACT.md) lines 1037-1039. | Confirmed. The fix changes the core carcass record, which O1 allows inside the new epoch (D-01) | 07 |
| B06 | The HUD's target label shows through the death and victory cards. | `ui/Hud.java:347-350`, drawn by `Game.java:494` before the cards at `Game.java:520-524`; their backdrops are translucent (`ui/PresentationOverlay.java:42`, alpha 0.72; `:63`, alpha 0.88). Record: urban VALIDATION lines 4231-4233. | Confirmed | 07 |
| B07 | Leg pieces (deer, gloomstalker) come to rest standing on end: a piece balanced on its end gets no topple torque inside `FLAT_BAND`. | `entity/BodyFragmentSystem.java:479-498` (`side()` softens the lever to zero); `entity/BodyFragmentConstants.java:103`. Record: ALL_LIVING_COMBAT_FIRE_PROGRESS lines 569-574. | Confirmed. Independent of the torso-speed question Q-01 | 07 |
| B08 | While the game is paused, living bodies' idle animation follows the wall clock, so a paused burning body breathes under frozen flames. | `Game.java:362` (`totalTime` is `glfwGetTime()`); `engine/Renderer.java:517, 531` pose living bodies with it, while flames use the paused clock `engine/ParticleSystem.java:97`. Record: ALL_LIVING_COMBAT_FIRE_PROGRESS lines 1443-1445. | Confirmed | 07 |
| B09 | A burning log or leaf block barely shows: the block-fire glow cube and its flame and ember particles sit inside the opaque block. | `engine/Renderer.java:548-555` (the cube is at most one block); `AmbienceSystem.java:174-183` (flame at y + 0.2, ember at y + 0.6; only the smoke at y + 1 is outside). Record: [COMBAT_LETHALITY_AND_MOLOTOV](../../engineering/COMBAT_LETHALITY_AND_MOLOTOV.md) lines 303-305. | Confirmed | 07 |
| B10 | `ItemConditionSystem` keeps its own rained-on test (a literal `0.9f`) for drying racks and rain collectors, against the documented one-predicate rule. Behaviour is identical today. | `simulation/ItemConditionSystem.java:110, 135`; the shared rule is `simulation/FireSystem.java:142-156` with `RAIN_EXPOSURE_SKYLIGHT` (`simulation/FireConstants.java:42`). Rule: [DEVELOPING](../../DEVELOPING.md) lines 1168-1171; [ARCHITECTURE](../../ARCHITECTURE.md) line 1718. | Confirmed (maintenance) | 07 |
| B11 | The save writer's 128 MiB guard has no test. | `save/SaveSystem.java:107` and `:349-354`; only the read cap is tested (`src/test/java/com/veylon/save/CorruptSaveResilienceTest.java:246-262`). Record: [urban DECISIONS](../../urban-survival/DECISIONS.md) D-249 (line 339). | Fixed in 03 (`ce4686d`): the writer takes its limit through a package-private seam, and a test proves a snapshot one byte over it is refused while the last save stays byte for byte | 03 |
| B12 | A resident killed by a projectile in a frame without a fast tick is written back as dead without the death's bookkeeping (trust, mission failure, loot, log) if its settlement deactivates on that frame's slow tick. | `Game.java:584-586` (the ticks run before `projectiles.update`); `simulation/SimulationScheduler.java:21-52` (separate accumulators); `settlement/SettlementManager.java:640-646` and `:758-771` (`r.alive = !n.dead`, then `removeNpc`); the bookkeeping is in the fast tick, `entity/EntityManager.java:79` onward. Record: ALL_LIVING_COMBAT_FIRE_PROGRESS lines 1214-1217. | Confirmed by reading; not yet reproduced | 07 |
| B13 | On the paused death card after a lethal blast, the camera sits inside the player's rest-pose head piece, and the remains fall only after respawn. | `PlayerLifeCycle.java:10-17` spawns the remains on entering DEATH; `entity/BodyFragmentSystem.java:183-194` places them in the rest pose; `Game.java:560` keeps the camera at the eye; `Game.java:576-578` stops `advanceWorld` outside PLAYING. Record: ALL_LIVING_COMBAT_FIRE_PROGRESS lines 438-442. | Confirmed in source; never captured natively | 07 |
| B14 | The day's temperature curve is inverted in every world: 14:00 is the coldest hour and 02:00 the warmest. | `simulation/TimeSystem.java:99-104`. Records: urban DECISIONS P-15 (line 410); [README](../../../README.md) lines 575-577. | Confirmed | 08 (D-07) |
| B15 | After an overkill hit the HUD prints negative health: the city's death card read −900 after the QA route's 1,000-damage kill. | `entity/Entity.java:130-139` (health is not clamped); `ui/Hud.java:152-162` prints `(int) value`. Record: urban VALIDATION lines 4231-4233. New to this ledger. | Confirmed. Clamp what is shown, not health itself: `health <= 0` is the death test | 07 |
| B16 | `docs/DEVELOPING.md` says `./gradlew test` runs "886 deterministic tests, headless, ~4 min". The same file gives 2,365 tests at 0.9.0, and the build took 17 min 48 s. | DEVELOPING line 16 against line 1003; the line dates from `b36bce4` (2026-09-20). New to this ledger. | Fixed in 02 (`bfb0176`): the running section states no count | 02 |

### Already fixed or not a defect

| Item | Record | Finding |
| --- | --- | --- |
| Giving supplies to a stronghold logged "The camp takes in your supplies." | urban PROGRESS lines 2895-2897 ("left for 21's text") | Fixed in `2749358` (2026-10-30): `settlement/UrbanSiteRelations.java:193` says "The Wardens take in your supplies." |
| `docs/ARCHITECTURE.md` described `setBlock(..., notify)` | urban VALIDATION line 143; urban PROGRESS line 114 ("not fixed") | Fixed in `bdecf6f` (2026-10-10): ARCHITECTURE line 240 names `World.setBlock(x, y, z, type, record)` |
| The event probability table is documented twice | DEVELOPING "Known rough edges" | A design limit. The JavaDoc's bounds match `EventConstants` today (0.06 to 0.43 checked) |

## 6. Design limits

Listed, not fixed. Sources: [README](../../../README.md) "Known limitations" (lines 564-627),
DEVELOPING "Known rough edges" (lines 1007-1023), and the engineering and urban records. A later
release that takes one on is named.

- **Engine.** Sky-column lighting baked at mesh time, not flood-fill (0.11.0); face-culled
  meshing and cuboid models without skeletal animation; full-cell water without levels; no
  dropped-item entities.
- **Saves.** One save slot (04, D-02); no autosave (05, D-03); save and load time grow with the
  places a city names (urban D-246; 0.9.2). The README's downgrade notes for 0.7.x and 0.8.1 and
  "v2 worlds never gain new content", which O1 made moot, were removed by 03.
- **City.** First-touch planning hitches in fast travel (urban D-247; 0.9.2's look-ahead
  planner); no gardening (0.12.0); the city is generous in total, so its pressure comes from
  distance, risk, time and spoilage (urban DESIGN §16.6; the 0.10.0 balance pass).
- **Creative.** The permanent mark, flight with collision and no noclip, the twelve catalog
  exclusions.
- **People, animals and movement.** Steering wildlife and bounded A* people; crouching does not
  shrink the collision box; sleep fast-forwards the clock; gates are block swaps; no
  aim-down-sights; slot changes and save/load cancel a reload; residents respawn at their beds.
- **Combat and fire.** Kegs catch only from adjacent flames, fuses and explosions; only a lethal
  blast tears a body apart and only it leaves the player's remains; fires, pools, burning
  bodies, panic and scorch are not saved; a burning body does not spread fire or light its
  surroundings; chest wounds are forgotten when a person leaves the world; a fire bomb breaking
  more than three blocks above the ground spills nothing; a rained-on flame still arms kegs and
  burns until the rain finishes it (COMBAT_LETHALITY_AND_MOLOTOV lines 306-308).
- **Tooling.** Performance budgets are calibrated on one reference machine, and the
  `performanceTest` durable-save gate fails on the owner's host regardless of the code; cadence
  interfaces are contracts, not dependency injection.

## 7. Open owner questions

Carried from the 0.8.x series. Until the owner answers, the current behaviour stands.

| Id | Question | Record |
| --- | --- | --- |
| Q-01 | **Blast torso speed.** Impulse is divided by mass and mass follows volume, so at scrap-bomb strength torsos and heads barely leave the spot while limbs fly metres. Weight the impulse by area instead? | COMBAT_LETHALITY_AND_MOLOTOV lines 287-290; ALL_LIVING_COMBAT_FIRE_PROGRESS lines 1536 and 1605-1606 |
| Q-02 | **Self-blast.** A Survival player whose body centre is within 3.9 blocks of their own scrap bomb, or 5.7 of a keg, dies outright. Keep it? | The contract §8, item 6 (line 530); ALL_LIVING_COMBAT_FIRE_PROGRESS lines 1607-1608 |
| Q-03 | **Iron arrow against bullet.** A non-lethal torso hit costs `max(damage, share × maximum health)`. Against people with maximum health under 32 (the captive's 24; the villager's, farmer's and scout's 30) an iron arrow's 16 therefore costs more than a relic-rifle bullet's 11 or a blunderbuss pellet's 7, although an arrow wound counts for less. Accept it? | COMBAT_LETHALITY_AND_MOLOTOV lines 55-64; `combat/WeaponRegistry.java` (bow 11 plus the iron arrow's 5) |
| Q-04 | **Fire tuning.** Every combustion, panic and presentation number of the all-living series is proposed, not signed off. | The contract §15 (line 1210) |

## 8. Save code and tests that O1 makes dead

Input for milestone 03, which settled every row; the outcome follows the table. Nothing here is
deleted by 01. "Dead" means that only a save written
before epoch 1 can reach it once D-01 lands, or that it exists only to keep such saves or older
builds working. 03 removes each item or turns it into a refusal test; the docs listed last
follow.

| Item | Where | Why it dies |
| --- | --- | --- |
| The v2 core reader | `save/SaveSystem.java`: `MIN_SUPPORTED = 2` (line 94) and its check (429-432); the v2 pin to `World.GEN_LEGACY` (436-437) and the migration branch (445 onward); every `version >= 3` branch (638, 693-696, 1139); the class Javadoc "v2 saves still load" (60-65) | The epoch check replaces it; no 0.9.1 save is v2 |
| The v1 extension payload | `save/SaveSystem.java:940` (accepts extension 1–2) and `:993` (`extensionVersion >= 2`) | Only builds older than the current writer wrote extension 1 |
| `world.bodies` v1 | `save/BodiesSection.java:83`, `:169-188` (`legacyBone`); `BodiesSectionTest.versionOneFlatBonesMigrateByNameWithoutBendingTheNewChildLinks` | The current writer writes v2 |
| `world.fragments` beside `world.remains` | `save/V3ExtensionSections.java:411-428` (`readSettledPieces` restores fragments only when remains is absent); `save/FragmentsSection.java`; people are written twice for 0.8.0 readers; `RemainsSectionTest.aSaveWithoutTheRemainsSectionLoadsAsV080Would` and `.aLiteralVersion1SectionStillLoadsAndIsStillWrittenByteForByte`; `FragmentsSectionTest` | O1 promises no downgrade and no 0.8.0 save; one section can carry the pieces |
| Core-only historical quests | `ai/Quest.java:100-104` (compatibility constructor); `ai/FactionSystem.java:430-433` (`onCaptiveRescued(Game)` overload) | Only pre-extension saves lack the quest target state; 03 verifies the callers first |
| Absence defaults and forward-compatibility skips | `save/UrbanMetaSection.java:82-86` (`ABSENT_CONTENT` for a city without `urban.meta`); unknown stable-id sections skipped by length; `RemainsSection`'s skip of unknown families and pieces | Within one epoch a section the writer always writes cannot be absent, and an unknown id is malformed; 03 decides between removing and refusing |
| Historical fixtures and their tests | `HistoricalV020SaveCompatibilityTest` with `v0.2.0-authentic.sav.b64` (also `GameModeSectionTest.authenticV020FixtureLoadsAsSurvivalWithLegacyTerrain`, lines 77-86); `HistoricalV3SaveCompatibilityTest` with `v0.3.0-authentic.sav.b64` and `v0.8.1-{survival,creative}-authentic.sav.b64` (also `SaveAuditQaSceneTest` lines 45-46); `SaveMigrationTest`; `UrbanEarlierRevisionSaveTest` with `urban-gen19-m20-authentic.{sav.b64,describe.txt,facts.txt}`; `UrbanContainersSectionTest` line 67 with `saves/urban-preloot-m06.sav` and its `README.md`. Tests are under `src/test/java/com/veylon/save/` (`SaveAuditQaSceneTest` under `com/veylon/`), fixtures under `src/test/resources/com/veylon/save/` and `src/test/resources/saves/` | O1 keeps no historical fixture or compatibility test; a refusal test can use a synthesized pre-epoch header instead |
| Earlier urban revisions in saves | `world/urban/UrbanWorldState.java:215-233` (`resolveContentRevision` pairing), `UrbanRevision.requireSupportedContent`, the layout check in `save/UrbanMetaSection.java:68-72`, per-generator section versions such as `save/UrbanRefugesSection.java:34-35` | Their save-loading role ends with D-01. The code stays while tests and QA can still build generators 4–19; 0.9.2 retires it with them, and 03 must not polish it |
| Documents that describe the old behaviour | `docs/ARCHITECTURE.md` "Save / load" (the v2 migration, and the policy's "Status" paragraph); README "Known limitations" lines 573-574, 590-595 and 601-602; `.agents/skills/veylon-save-compatibility/SKILL.md` | 03 rewrites the skill and the save documents; 09 updates the README. The urban records stay historical: their R10 note (urban DESIGN line 34) already points to the policy |

Outcome of milestone 03 (2026-11-20), row by row:

- **The v2 core reader:** gone. `SaveHeader` replaced `MIN_SUPPORTED` and the version branches,
  and the stack and inventory readers lost their version parameter (`843dce8`).
- **The v1 extension payload:** refused; the extension tail is required, in version 2 only
  (`3d98b70`).
- **`world.bodies` v1:** refused, and `legacyBone` and its test are gone (`3d98b70`).
- **`world.fragments` beside `world.remains`:** no longer written or read (D-19, `14ca773`).
  `FragmentsSectionTest`'s tests through the whole save live on as `PersonPiecesSaveTest`.
- **Core-only historical quests:** the constructor and `LEGACY_UNBOUND` stay, because they are
  live outside saves; the two event overloads without context had no caller and went (D-20,
  `13486ca`).
- **Absence defaults and forward-compatibility skips:** refused rather than removed (D-18,
  `3d98b70`): an unknown section id, a missing always-written section, a city without
  `urban.meta`, and an unknown body family, piece or joint table in `world.remains`.
  `UrbanRevision.ABSENT_CONTENT` and the containers' adoption of content-1 inventories are gone.
- **Historical fixtures and their tests:** all removed (`843dce8`), replaced by refusal tests
  on synthesized headers (D-20). `SaveMigrationTest` kept its round trip and corruption cases as
  `CoreSaveRoundTripTest`; the save audit's headless test tours current wilderness saves instead.
- **Earlier urban revisions in saves:** a load now accepts only the content revision a new world
  of the save's generator records (`3d98b70`); the runtime rules for earlier revisions stay until
  0.9.2 retires their generators, as the row says.
- **Documents:** the save sections of ARCHITECTURE, DEVELOPING, the README and the changelog, and
  the save compatibility skill, describe the epoch (the documentation commit of 03).
