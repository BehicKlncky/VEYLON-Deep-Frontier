# All-living dismemberment and combustion — progress

Running record for the twelve-milestone task. Design decisions live in
[ALL_LIVING_COMBAT_FIRE_CONTRACT.md](ALL_LIVING_COMBAT_FIRE_CONTRACT.md); this file records
status, commands actually run, results, evidence and handoffs. Each milestone appends its own
section and updates the status table. Checkpoint SHAs of a milestone's own commits are reported
in its handoff message and recorded by the next milestone, so no commit has to contain its own
SHA.

## Branch record

| Item | Value |
| --- | --- |
| Task branch | `feature/all-living-dismemberment-combustion` (local; not yet pushed) |
| Original base | `3704f4dea3b7870777b20656813937368a83a171` — `merge: release v0.8.0` (2026-09-20) |
| Base provenance | Branch already existed at milestone 01 start, created from `HEAD` at the base (reflog: "branch: Created from HEAD"), with no commits of its own. Verified and resumed, not recreated. |
| `main` / `origin/main` at start | both `3704f4d` after `git fetch origin` on 2026-09-26 (no incoming commits) |
| Remote | `origin` = `git@github.com:BehicKlncky/VEYLON-Deep-Frontier.git`; no same-name remote branch yet |
| Commit identity | configured Git user `BehicKlncky`; no AI co-author trailers (repository rule) |

Checkpoints by milestone (filled in by the following milestone):

| Milestone | Commits |
| --- | --- |
| 01 | `8f7c50fb24b4621a9dc00f3786720f4fa9495fa0` — `docs(combat): record all-living combat and fire contract` |
| 02 | reported in the milestone 02 handoff; record here in 03 |

## Status

| # | Milestone | Status |
| --- | --- | --- |
| 01 | Source audit, baseline, contract | **Complete** (documentation only) |
| 02 | Species-aware fragment anatomy | **Complete** (definitions and pose API; no new gameplay) |
| 03 | All-living blast deaths | Not started |
| 04 | Species fragment rendering | Not started |
| 05 | Fragment save compatibility | Not started |
| 06 | Shared body combustion | Not started |
| 07 | Connect all fire sources | Not started |
| 08 | NPC and animal fire panic | Not started |
| 09 | Combustion lifecycle | Not started |
| 10 | Body fire presentation | Not started |
| 11 | End-to-end validation | Not started |
| 12 | Merge and push | Not started |

No new gameplay behaviour exists yet: creatures and the player still fall whole, and a human
blast death spawns the same ten pieces as before. Nothing in this file claims otherwise.

## Milestone 01 — source audit and contract (2026-09-26)

### Environment

- Host: Windows 11 Pro x64 (10.0.26200). Not the performance reference machine.
- Java: no system JDK on `PATH`/`JAVA_HOME`; used a portable Temurin **25.0.4.1+1**
  (`OpenJDK 64-Bit Server VM Temurin-25.0.4.1+1`) from an earlier session's scratch directory,
  via `JAVA_HOME`. Gradle wrapper 9.1.0, single-use daemon (`--no-daemon`).
- Working tree at start: clean apart from two untracked user files, `.agents/` and
  `AGENTS.md`. They are not part of this task and were left untracked and uncommitted.
- Instructions read: `AGENTS.md` (invariants, validation, commit rules), the milestone pack's
  `00_BASE_KNOWLEDGE.md` and `01_SOURCE_AUDIT_AND_CONTRACT.md`, and the downstream prompts
  02–12 for the handoff. Project skills available: `veylon-performance`, `veylon-release`,
  `veylon-save-compatibility` (not needed for a documentation milestone; 05 must use the save
  skill).

### Commands and results

All from the repository root in PowerShell with the portable JDK on `PATH`.

| Command | Result |
| --- | --- |
| `git fetch origin` | exit 0; `origin/main` = `main` = `3704f4d` |
| `.\gradlew.bat test --tests *BlastLethalityTest --tests *BodyFragment* --tests *Fragment* --tests *MolotovTest --tests *FireWeatherTest --tests *CombatFireIntegrationTest --no-daemon --console=plain` (the focused baseline from `00_BASE_KNOWLEDGE.md`) | **BUILD SUCCESSFUL in 31 s. 81 tests in 10 classes, 0 failures, 0 errors, 0 skipped.** `compileJava`/`compileTestJava` were up to date (Gradle input fingerprints unchanged since an earlier build of this revision); `test` executed. Log: `build/all-living-m01-baseline.log` (git-ignored). |
| `.\gradlew.bat build --no-daemon --console=plain` | **BUILD SUCCESSFUL in 3 m 49 s. 886 tests in 121 classes, 0 failures, 0 errors, 0 skipped.** Compilation and Javadoc tasks were up to date; `test` and `check` executed. Log: `build/all-living-m01-build.log` (git-ignored). |
| `.\gradlew.bat test --tests com.veylon.M01AuditProbeTest --no-daemon --console=plain -i` (three runs, temporary probe) | BUILD SUCCESSFUL each time; output quoted below. The probe class was deleted afterwards and is not in any commit. |

Focused baseline per class: `CombatFireIntegrationTest` 8, `combat.BlastLethalityTest` 11,
`entity.BodyFragmentAllocationTest` 2, `entity.BodyFragmentPhysicsTest` 15,
`gfx.FragmentGeometryTest` 8, `gfx.FragmentIsolationTest` 5, `qa.SerializedEnumOrderTest` 1
(its method name matches `*Fragment*`), `save.FragmentsSectionTest` 7,
`simulation.FireWeatherTest` 9, `simulation.MolotovTest` 15.

No environment failure occurred. `performanceTest` and native QA scenes were not run: this
milestone changes no code or rendering.

### Headless probe (evidence, not a committed test)

A temporary JUnit class in package `com.veylon` printed observations and asserted nothing. Its
arena copied `MolotovTest.arena`: `newWorld(777, true)`, chunks 18–21 filled with stone up to
y = 39 (liquid lies at y = 40), creatures and NPCs cleared, `world.campPos = null` so legacy
NPCs stand still, projectile/liquid/fire seeds 23/29/31, clear weather.

- **A, D** used the production command `PlayerCombatSystem.updateThrownWeaponCommand` from the
  player's eye (`camera.position`, eye height 1.62) and stepped the frame's simulation order:
  `scheduler.update` → `projectiles.update` → `ragdolls.update` → `fragments.update` →
  `explosions.tickFuses` at 1/60 s. AI ran.
- **B** created a real pool (`ProjectileSystem.fire` straight down onto (330.5, 330.5)), moved a
  runner NPC kinematically along x at a constant speed on a given lane, and called
  `liquidFire.mediumTick(0.5)` on a 0.5 s clock starting at the listed phase. "Contact" is the
  time the feet footprint overlapped a patch (the `standsIn` rule).
- **C** fired a bottle from 2 blocks at a bird held in the air (its AI not stepped), then ran
  six fire and liquid medium ticks.
- **E, F** placed blocks, lit them with `FireSystem.ignite` or `campfireFuel`, and ran
  `FireSystem.mediumTick` / `LiquidFireSystem.mediumTick` directly.

Output (verbatim):

```text
PROBE A direct-hit NPC: thrown=true spills=1 firstPatch=330,40,330 health 35.0 -> 25.0 after 1.0 s in pool (lastHitByPlayer=true) -> 25.0 after 5 s away from the pool precip=false
PROBE B crossing: speed 4.4 lane z=330.5 [phase 0.0: contact 1.27 s, damaging ticks 3, damage 15.0] [phase 0.1: contact 1.27 s, damaging ticks 2, damage 10.0] [phase 0.2: contact 1.27 s, damaging ticks 2, damage 10.0] [phase 0.3: contact 1.27 s, damaging ticks 2, damage 10.0] [phase 0.4: contact 1.27 s, damaging ticks 3, damage 15.0]
PROBE B crossing: speed 4.4 lane z=331.9 [phase 0.0: contact 1.03 s, damaging ticks 2, damage 10.0] [phase 0.1: contact 1.03 s, damaging ticks 2, damage 10.0] [phase 0.2: contact 1.03 s, damaging ticks 2, damage 10.0] [phase 0.3: contact 1.03 s, damaging ticks 2, damage 10.0] [phase 0.4: contact 1.03 s, damaging ticks 2, damage 10.0]
PROBE B crossing: speed 7.0 lane z=330.5 [phase 0.0: contact 0.78 s, damaging ticks 2, damage 10.0] [phase 0.1: contact 0.78 s, damaging ticks 2, damage 10.0] [phase 0.2: contact 0.78 s, damaging ticks 1, damage 5.0] [phase 0.3: contact 0.78 s, damaging ticks 1, damage 5.0] [phase 0.4: contact 0.78 s, damaging ticks 2, damage 10.0]
PROBE B crossing: speed 7.0 lane z=331.9 [phase 0.0: contact 0.65 s, damaging ticks 2, damage 10.0] [phase 0.1: contact 0.65 s, damaging ticks 2, damage 10.0] [phase 0.2: contact 0.65 s, damaging ticks 1, damage 5.0] [phase 0.3: contact 0.65 s, damaging ticks 1, damage 5.0] [phase 0.4: contact 0.65 s, damaging ticks 1, damage 5.0]
PROBE B crossing: speed 12.0 lane z=330.5 [phase 0.0: contact 0.45 s, damaging ticks 0, damage 0.0] [phase 0.1: contact 0.45 s, damaging ticks 1, damage 5.0] [phase 0.2: contact 0.45 s, damaging ticks 1, damage 5.0] [phase 0.3: contact 0.45 s, damaging ticks 1, damage 5.0] [phase 0.4: contact 0.45 s, damaging ticks 1, damage 5.0]
PROBE B crossing: speed 12.0 lane z=331.9 [phase 0.0: contact 0.37 s, damaging ticks 0, damage 0.0] [phase 0.1: contact 0.37 s, damaging ticks 0, damage 0.0] [phase 0.2: contact 0.37 s, damaging ticks 1, damage 5.0] [phase 0.3: contact 0.37 s, damaging ticks 1, damage 5.0] [phase 0.4: contact 0.37 s, damaging ticks 1, damage 5.0]
PROBE C airborne bird: feet y=46.0 (floor top 40, 6.0 blocks up): spills=1 patches=0 bird health 4.0 -> 4.0
PROBE C airborne bird: feet y=43.0 (floor top 40, 3.0 blocks up): spills=1 patches=22 bird health 4.0 -> 4.0
PROBE D scrap bomb: blast at 330.86832,40.00051,330.38385 deer body-centre distance at detonation=11.513202 player=2.5732067 (lethal radius 3.9)
PROBE D scrap bomb: thrown=true thornhorn dead=false health=36.609344 deer dead=false health=14.0 brute dead=true fragments live+settled=10 ragdolls live=0 carcasses=0 player 100.0 -> 82.92464 dead=false
PROBE E block fire: buried log lit=true: NPC above solid stone floor 35.0 -> 31.0; burning leaves at head height beside NPC 35.0 -> 35.0; NPC between three burning logs 35.0 -> 23.0 in one 0.5 s tick (NPC_BURN_DPS*dt = 4 per cell)
PROBE F campfire/torch: NPC standing in a fueled campfire cell 35.0 -> 35.0, in a torch cell 35.0 -> 35.0 over 5 s of medium ticks; fire cells=0
```

A lane at z = 332.4 missed the 22-cell pool entirely at every speed (0 contact) and is omitted.
In D the brute (70 health) stood about 2.5 blocks from the blast beside the thornhorn; the
Survival player took falloff damage inside the lethal radius and lived.

### Verified gaps (details and citations in contract §2)

1. The lethal blast gate, death record and fragment pipeline are `Npc`-only; creatures and the
   Survival player inside the radius take falloff damage and fall whole.
2. There is no burning state on any body: pool and block-fire damage stop as soon as contact
   stops, and contact is sampled only every 0.5 s.
3. A direct molotov hit does nothing to the struck body; a high hit finds no ground and ignites
   nobody; airborne bodies never touch a pool.
4. Block-fire contact is a feet distance with no occlusion (through floors), no
   de-duplication (stacking per cell), environmental attribution only, and a feet bias (flames
   at head height do nothing).
5. Fueled campfires and torches never burn anyone.
6. No NPC has any fire response; birds have none; animal avoidance only sees block fires.
7. No player body exists for any death; player blast remains are new work.
8. `CreatureAI.update` has no `dead` guard, so a creature killed between fast ticks gets one
   more AI and physics step.

### Deliverables

- `docs/engineering/ALL_LIVING_COMBAT_FIRE_CONTRACT.md` (new): diagnosis, target matrix,
  anatomy table, fire-source inventory, data owners, tick order, fragment identity, lethal-blast
  rules, harvest ownership, combustion model and damage equation, medical BURN rule, panic
  policy, persistence policy, proposed tuning, budgets, tests to replace, dependency handoff.
- `docs/engineering/ALL_LIVING_COMBAT_FIRE_PROGRESS.md` (new): this record.

### Limitations

- Every tuning value and budget in the contract is proposed; none is measured yet.
- The probe is evidence from this revision only and is not in the repository. Milestones 07 and
  11 must add real production-path tests for the same scenarios.
- The player-death balance consequence of R1 (dying inside 3.9 blocks of one's own scrap bomb)
  is recorded as a decision (contract §8.6); it is flagged for the user rather than softened.

### Handoff to milestone 02

Continue on `feature/all-living-dismemberment-combustion`; verify this commit is its tip. Build
from contract §3.2 and §7.

Real symbols to consume (verified at the base):

- `entity/BodySkeleton`: `of(CreatureType)`, `humanoid()`, arrays `part`, `parent`, `joint`,
  `pivotX/Y/Z`, `restX/Y/Z`, `length`, `radius`, `boneCount`, `torsoY`, `halfX/Y/Z`, `TORSO`,
  `MAX_BONES` = 12.
- `gfx/model/CreatureModels.of(CreatureType)`, `gfx/model/NpcModels.get()`: shared mutable
  `EntityModel`s; `EntityModel.root`, `part(String)`, `resetPose()`.
- `gfx/model/ModelPart`: `name`, `pivotX/Y/Z`, `boxX/Y/Z`, `sizeX/Y/Z`, `children`, `visible`,
  `forceSplitDraw`, `split(String, boolean)`, `isSplitTip(ModelPart)`, `drawsWholeBox()`,
  `find(String)`, pose fields `rotX/Y/Z`, `poseX/Y/Z`, `scale`.
- `entity/BodyFragment.Piece` (ten human pieces, ordinals 0–9 = `world.fragments` v1 ids) and
  its nested `Model` constants; `gfx/model/FragmentModels` (humanoid-only tables);
  `gfx/model/Animator.poseFragment` / `isolatePart`.
- `entity/BodyFragmentConstants.MIN_HALF_EXTENT` 0.05, `DENSITY` 260.

Tests that must stay green: `BodyFragmentPhysicsTest`, `BodyFragmentAllocationTest`,
`FragmentGeometryTest`, `FragmentIsolationTest`, `FragmentsSectionTest`,
`SerializedEnumOrderTest`, `DeathRagdollTest`, `BlastLethalityTest`.

Decisions 02 owns and must record here: the concrete definition classes and field names, the
death-pose snapshot API, confirmation or revision of the proposed cut sets and the 0.10 m
`MIN_SEPARATE_PIECE` rule, and the documented geometry tolerances.

## Milestone 02 — species-aware fragment anatomy (2026-09-26)

### Start state

- Branch `feature/all-living-dismemberment-combustion`, tip `8f7c50f` (milestone 01), which is
  the 01 checkpoint recorded above. Working tree clean apart from the user's untracked
  `.agents/` and `AGENTS.md`, which stay untracked.
- Same host and portable Temurin 25.0.4.1+1 JDK as milestone 01; Gradle 9.1.0, `--no-daemon`.

### Commands and results

All from the repository root in PowerShell with the portable JDK on `PATH`, `--no-daemon
--console=plain`. Logs are in the git-ignored `build/` directory.

| Command | Result |
| --- | --- |
| `.\gradlew.bat compileJava compileTestJava` | BUILD SUCCESSFUL (`build/all-living-m02-compile.log`). |
| `.\gradlew.bat test --tests *BodyFragment* --tests *Fragment* --tests *DeathRagdollTest --tests *BlastLethalityTest --tests *SerializedEnumOrderTest` | First run: 42 of 70 failed with `ExceptionInInitializerError` (a static-initialiser ordering bug in `FragmentAnatomy`, fixed). Rerun: **70 tests in 8 classes, 0 failures** (`build/all-living-m02-legacy.log`). |
| `.\gradlew.bat test --tests *FragmentAnatomyTest --tests *FragmentAnatomyModelTest` | Two failing iterations exposed the scaled-rotation and scaled-collision-box defects described below (6 of 93 failed each time). Final run: **93 tests in 2 classes, 0 failures**; posed-piece read allocation **0 bytes/frame** for 120 pieces (`build/all-living-m02-new.log`). |
| `.\gradlew.bat build` | **BUILD SUCCESSFUL in 3 m 52 s. 979 tests in 123 classes, 0 failures, 0 errors, 0 skipped**; `javadoc` executed (`build/all-living-m02-build.log`). Base was 886 in 121. |
| `jshell` on `build/classes/java/main` | Legacy human centre floats compared with the table's, and piece masses printed (below). Scratch scripts only. |

Not run: `performanceTest` (no per-frame path changed; the new read path is covered by the
allocation test) and native QA scenes (nothing new is drawn yet; rendering is milestone 04).

### What was built

Every body family now has an immutable fragment table, and a body's death-time pose can be
captured into an immutable snapshot that places each piece where the living model drew it.
**Nothing uses the species tables at runtime yet**: `EntityManager` still sends creatures to
the ragdoll, and `spawnFromNpc` spawns the same ten human pieces in the rest pose. Decisions
are recorded in contract §7.1.

| Path | Symbols |
| --- | --- |
| `entity/BodyFamily.java` (new) | enum `HUMANOID, DEER, WOLF, BIRD, HARE, THORNHORN, STALKER`; `creature` (null for the humanoid); `of(CreatureType)`; `anatomy()`. Ordinal is append only; not persisted yet. |
| `entity/FragmentAnatomy.java` (new) | `of(BodyFamily)`, `of(CreatureType)`, `humanoid()`; `family`; `pieces` (`List<FragmentPiece>`, id = index); `piece(int)`, `piece(String)`; `jointCount()`, `joint(int)` → `FragmentAnatomy.Joint` (`index, name, parent, pivotX/Y/Z, boxed, boxX/Y/Z, sizeX/Y/Z, splitTip, piece`); `jointIndex(String)` (-1 if none); `restPose()`; `builder(BodyFamily)` → `Builder.joint(..)`, `.part(..)`, `.split(name, tip, alongY)`, `.piece(name, root, box)`, `.build()` (throws `IllegalArgumentException` naming the body and the problem); `MAX_JOINTS` = 24, `MAX_PIECES` = 12. The seven tables are built once in the static initialiser. |
| `entity/FragmentPiece.java` (new) | `anatomy, family, id, name, rootJoint, rootPart, boxJoint, boxPart, parent` (-1 for the core), `severed, ownedParts, excludedParts, mergedParts, anchorX/Y/Z, pivotX/Y/Z, centreX/Y/Z, boxOffsetX/Y/Z, halfX/Y/Z, halfWidth, halfHeight, volume, mass, inverseMass, cuts`. Rest-pose model space. |
| `entity/FragmentCut.java` (new) | `piece, severed, joint, ownEnd, axis, side, centreX/Y/Z, sizeX/Y/Z`: a flat wound rectangle on a face of the piece's box (size 0 along `axis`), rest-pose model space. A severed piece lists its own end first, then one wound per piece cut from it. |
| `entity/FragmentPose.java` (new) | Immutable. `anatomy`; `rest(FragmentAnatomy)`; `isRest()`; per joint `rotX/rotY/rotZ/poseX/poseY/poseZ/scale(int)`; `jointFrame(int, Matrix4f)`, `mulJointFrame(int, Matrix4f)`, `jointOrigin(int, Vector3f)`; per piece `pieceCentre(int, Vector3f)`, `pieceRotation(int, Quaternionf)`, `pieceScale(int)`. `FragmentPose.Recorder(anatomy).set(joint, rotX, rotY, rotZ, poseX, poseY, poseZ, scale).snapshot()` copies and rejects non-finite values and non-positive scales. |
| `entity/BodyFragment.java` | `Piece` now reads every number from `FragmentAnatomy.humanoid()` (new field `Piece.definition`; the private `Model` constants are gone; the constructor refuses a name/id mismatch). New fields `definition`, `pose`, `poseCentre`, `poseRotation`; `piece` is null for non-human pieces. New constructor `BodyFragment(FragmentPiece, FragmentPose)` (`BodyFragment(Piece)` = rest pose). New `placeAt(x, y, z, yawRadians)`, `jointToWorld(int, Vector3f)`, `modelTransform(Matrix4f)`, `rootTransform(Matrix4f)`. `modelToWorld` now maps death-pose model space (identical to the old rule in the rest pose). `halfX/Y/Z` are the table's times `pose.pieceScale`. |
| `entity/BodyFragmentSystem.java` | `spawnFromNpc` iterates the humanoid table with its rest pose (`placeAt`, `jointToWorld`, salts from `id`). |
| `entity/BodyFragmentConstants.java` | `MIN_SEPARATE_PIECE` = 0.10, `CUT_INSET` = 0.8. |
| `gfx/model/AnatomyModels.java` (new) | `modelOf(BodyFamily)` (validates once per family), `validate(FragmentAnatomy, EntityModel)` (throws `IllegalStateException`), `captureCreature(Creature, double time)`, `captureNpc(Npc, double time)`, `record(EntityModel, FragmentAnatomy)`, `applyPose(EntityModel, FragmentPose)`, `MATCH_TOLERANCE` = 1e-6. Captures leave the shared model reset. |
| `gfx/model/FragmentModels.java` | Anchors and wounds come from the humanoid table; `standProud` only adds depth over the vest. `CUT_INSET` aliases the constant. Public API unchanged. |
| `src/test/.../entity/FragmentAnatomyTest.java` (new) | 55 tests: explicit per-family ownership tables (`@EnumSource(BodyFamily)`), extents/mass, the split rule, wounds, legacy ids, rest pose, hand-computed quadruped/hare/bird geometry and posed legs/wings, snapshot immutability, validation failures. |
| `src/test/.../gfx/FragmentAnatomyModelTest.java` (new) | 38 tests: tables against models, each box drawn by exactly one isolated piece, ragdoll bones versus joints, animation completeness, reassembly at 4 headings × 7 creature poses (per species) or × 6 human poses in 5 looks, capture freezing, validator failures, allocation. |
| docs | contract §5, §7, §7.1, §18; `DEVELOPING.md` creature step 4; `RAGDOLL_JOINTS.md` "Adding a species". |

### Tolerances and evidence

- Tables against models (`AnatomyModels.MATCH_TOLERANCE`): 1e-6 m. All seven match.
- Reassembly (`FragmentAnatomyModelTest.TOLERANCE`): 1e-4 m on every box corner, cut joint and
  collision-box corner, at world coordinates near (12, 42, −5); float rounding only. The
  living geometry is rebuilt from the real part tree, not from the tables.
- Hand-summed spot values (`FragmentAnatomyTest`, 1e-5 m): e.g. deer front-left thigh centre
  (−0.1344, 0.435, −0.323), deer head (0, 1.136, −0.568) cut at the neck (0, 0.916, −0.408),
  wolf tail tip (0, 0.69, 0.665), bird wing0_l centre (−0.24, 0.23, −0.06) half
  (0.15, 0.01, 0.05); a deer leg swung 0.6 rad and a bird wing beating 0.9 rad over a rolled,
  bobbing body are checked against closed-form trigonometry.
- Human pieces are numerically unchanged: every legacy centre from the old constants equals the
  table's bit for bit (checked in `jshell` with the build's float arithmetic), and all 70
  existing fragment, ragdoll, blast, save and enum-order tests pass unmodified.
- The reassembly test found two real defects during development, both fixed before commit:
  JOML `getNormalizedRotation` skewed the rotation of a breathing (scaled) body (now
  `getUnnormalizedRotation`), and the collision box ignored the captured scale.

Piece masses (`volume × DENSITY` 260, kg), for 03's launch tuning:

| Family | Core / head | Limbs | Smallest | Total |
| --- | --- | --- | --- | --- |
| Humanoid | 19.28 / 4.57 | arm halves 1.39, leg halves 3.22 | 1.39 | 42.3 |
| Deer | 38.98 / 4.67 | leg halves 0.91 | tail 0.20 | 51.2 |
| Wolf | 24.05 / 3.57 | leg halves 0.55 | tail halves 0.44 | 32.9 |
| Thornhorn | 168.48 / 14.98 | leg halves 2.86 | tail halves 0.66 | 207.7 |
| Gloomstalker | 12.30 / 2.25 | leg halves 0.34 | tail halves 0.13 | 17.5 |
| Hare | 3.89 / 0.94 | whole legs 0.078 | tail 0.076 | 5.2 |
| Bird | 1.75 / 0.34 | wings 0.156 | tail 0.056 | 2.8 |

With the existing launch rule (`IMPULSE_BASE × strength × falloff / mass`), a scrap bomb at
full falloff throws anything under about 0.56 kg past `MAX_POINT_SPEED` (42 m/s), so every
hare, bird, gloomstalker and wolf limb would leave at the clamp, while the thornhorn torso
would get about 0.14 m/s from the blast on top of the fixed 4.5 m/s `UPWARD_BIAS`. This is the
v0.8.0 torso tuning concern made concrete for animals; the physics was not changed here.

### Limitations

- No creature or player blast death uses these tables yet (03), no species piece is drawn (04)
  and none is saved (05).
- `BodyFragment.piece` is null for non-human pieces, and three callers still read it:
  `Renderer.drawFragment`, `FragmentModels` (keyed by `Piece`) and `FragmentsSection.write`.
  Headless tests do not exercise `Renderer`, so a species piece reaching it would crash only the
  running game.
- A merged split half is assumed straight in the collision box. The living animation never bends
  one (asserted by the reassembly test); a pose that did would draw the half bent inside a box
  sized for a straight one.
- The collision box follows the captured scale; mass stays the table's rest mass.
- The tables repeat the model builders' numbers. `AnatomyModels.validate` (run by
  `modelOf` on first use and by the tests) turns any drift into a clear failure rather than
  misplaced pieces.
- The capture clock is a recommendation (contract §7.1); nothing captures a death pose in
  production yet.

### Handoff to milestone 03

Continue on the same branch; the 02 commit is reported in the handoff message.

- Spawning any body: `FragmentAnatomy a = FragmentAnatomy.of(type)` (or `humanoid()`), then for
  each `FragmentPiece p : a.pieces`: `new BodyFragment(p, pose).placeAt(e.pos.x, e.pos.y,
  e.pos.z, (float) Math.toRadians(-e.yaw))`, refit the sweep box, mass centre from
  `pose.pieceCentre(p.id, v)` × `p.mass`, blood at `f.jointToWorld(p.rootJoint, v)` when
  `p.severed`, hash salts from `p.id`. Piece 0 is always the core (`parent == -1`); anchor a
  harvest record to it. At most 12 pieces per body, so `MAX_LIVE_FRAGMENTS` 120 holds ten bodies.
- Death pose: `AnatomyModels.captureCreature(c, g.totalTime)` / `captureNpc(n, g.totalTime)`.
  `entity` must not import `gfx`, so route the call through `Game` (which already knows both),
  for example a delegate or a function field on `BodyFragmentSystem` set when `Game` is built.
  The player has no living model: use `FragmentAnatomy.humanoid().restPose()` for player remains.
  Keep headless determinism by leaving `totalTime` fixed in tests.
- Before a species piece can exist at runtime, make `Renderer.drawFragment` and
  `FragmentsSection.write` safe for `f.piece == null` (skip, or migrate to `f.definition`),
  because 04 and 05 land later.
- Replace `BlastLethalityTest.creaturesAndPlayerKeepTheExistingExplosionModel` as contract §17
  says; human counts stay `Piece.values().length`, species counts come from `a.pieces.size()`.

### Handoff to milestone 04

- Draw a piece: `EntityModel m = AnatomyModels.modelOf(f.definition.family)`; `m.resetPose()`;
  for the humanoid `Animator.applyAppearance(m, …f.appearance…)`;
  `AnatomyModels.applyPose(m, f.pose)`; `ModelPart top = Animator.isolatePart(m, f.rootPart,
  f.definition.excludedParts)`; draw `top` with parent matrix `f.rootTransform(matrix)`.
  `FragmentAnatomyModelTest.reassemble` is this exact sequence, headless.
- Wounds: `f.definition.cuts`, flat rectangles in rest-pose model space; a wound's offset in the
  piece's own box frame is its centre minus `definition.centre`, then placed by
  `translate(pos) × rotate(orientation)` (scale it by `f.pose.pieceScale` to follow breathing).
  Depth and clothing proudness are `FragmentModels.standProud`'s job (humanoid vest only today).
- Culling radius and every other `FragmentModels` lookup are keyed by the human `Piece`; species
  pieces need bounds from their full geometry (antlers, horns, raider spear).
- `FragmentModels.pieceTransform` assumes the rest pose; `BodyFragment.modelTransform` is its
  posed generalization.
