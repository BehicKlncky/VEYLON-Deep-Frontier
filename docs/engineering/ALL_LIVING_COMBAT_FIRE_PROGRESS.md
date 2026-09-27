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
| 02 | `52ff25b05069cd229dab711cf47239842a1c996f` — `feat(entity): define where every kind of body comes apart`; `63de0ddb70edf982814ab78a9a70cb1faac85fbf` — `test(entity): pin where every body comes apart and that it reassembles`; `eb151dc7ab003206485b0b5e6f837c1ea96edcf0` — `docs(combat): record species fragment anatomy` |
| 03 | `86ed85c14184ed22d710edbc80d1dda2cb7348c1` — `feat(combat): blow apart every living body a lethal blast kills`; `6bd61f8e1a9bc06e2de07b0edabbb967f5aa90d6` — `test(combat): pin lethal blasts and one body for every living thing`; `7d6c4aec43df0fcd2fb6af94cc8031cc64ff0d17` — `docs(combat): record all-living blast deaths` |
| 04 | `290c388d017bfb5371d40d181837fd7c52f5aff0` — `feat(gfx): draw every body's pieces as its own anatomy`; `4baef52c995b2dcd956a19dd4eb64d47fa6ce500` — `feat(qa): stage every kind of body blown apart for captures`; `42d9572f6feb4edab950618271c01fda3e711430` — `test(gfx): pin every body's pieces as drawn, and the capture scenes`; `a203df9ad3900c2a93c1d213b96d0c2d0251e75f` — `docs(combat): record species fragment rendering` |
| 05 | `f3c553d356f767e0b7bd04c3205ab536d7c10dbd` — `feat(save): keep every body's remains and harvest record in a save`; `08f1b0d9d3ad0cb9c0bb5c5dc1a4aecd55d57db8` — `test(save): pin how every body's remains survive a save`; `e8b825a0570e4785ebd828064c71ef906f0f557f` — `docs(combat): record remains persistence` |
| 06 | `f863a66e669215d1313c3e7782c051f925c2cf49` — `feat(fire): give every living body one fire on its own clock`; `fae46fa4fa8f7b42490a5c7362c466982df637e5` — `test(fire): pin one fire per living body for every kind of body`; `a5882236a95ad4cc1e6589c7a5686d56d39e60f0` — `docs(combat): record shared body combustion` |
| 07 | reported in the milestone 07 handoff; record here in 08 |

## Status

| # | Milestone | Status |
| --- | --- | --- |
| 01 | Source audit, baseline, contract | **Complete** (documentation only) |
| 02 | Species-aware fragment anatomy | **Complete** (definitions and pose API; no new gameplay) |
| 03 | All-living blast deaths | **Complete** (gameplay; animal pieces not drawn or saved until 04/05) |
| 04 | Species fragment rendering | **Complete** (presentation; animal pieces still not saved until 05) |
| 05 | Fragment save compatibility | **Complete** (`world.remains` v1; `world.fragments` v1 unchanged) |
| 06 | Shared body combustion | **Complete** (state, rules, fast-tick wiring; no production source connected until 07) |
| 07 | Connect all fire sources | **Complete** (every real flame and a bottle breaking on a body set bodies alight; legacy contact damage gone; people and animals keep out of torches and campfires) |
| 08 | NPC and animal fire panic | Not started |
| 09 | Combustion lifecycle | Not started |
| 10 | Body fire presentation | Not started |
| 11 | End-to-end validation | Not started |
| 12 | Merge and push | Not started |

Since 03, a lethal blast kills and blows apart every living body (all six species, every NPC
family, a Survival player) in the simulation; since 04 every piece is drawn as its own body's
anatomy; since 05 every settled piece, the pose its body died in and an animal's harvest record
survive a save (`world.remains`). Since 06 every living body can carry one fire
(`Entity.combustion`, advanced by `CombustionSystem` on the fast tick); since 07 burning liquid,
burning blocks, fueled campfires, placed torches and a fire bomb breaking on a body set it
alight through one swept contact path, and nothing else burns a body.

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

## Milestone 03 — all-living blast deaths (2026-09-26)

### Start state

- Branch `feature/all-living-dismemberment-combustion`, tip `eb151dc` (milestone 02); the three 02
  checkpoints are recorded in the table above. Working tree clean apart from the user's
  untracked `.agents/` and `AGENTS.md`, which stay untracked.
- Same host and portable Temurin 25.0.4.1+1 JDK; Gradle 9.1.0, `--no-daemon --console=plain`.

### Commands and results

All from the repository root in PowerShell with the portable JDK on `PATH`. Logs are in the
git-ignored `build/` directory.

| Command | Result |
| --- | --- |
| `.\gradlew.bat compileJava compileTestJava` | BUILD SUCCESSFUL (`build/all-living-m03-compile.log`). |
| `.\gradlew.bat test --tests *BlastLethalityTest --tests *BodyFragment* --tests *Fragment* --tests *DeathRagdollTest --tests *CombatFireIntegrationTest --tests *GameLoopIntegrationTest --tests *RuntimeBoundsTest --tests *SimulationSystemContractTest --tests *MolotovTest --tests *FireWeatherTest --tests *CreativeHazardsTest --tests *EntityEcologyTest` (production code changed, old tests untouched) | 233 tests, **1 failed**: `BlastLethalityTest.creaturesAndPlayerKeepTheExistingExplosionModel`, the assertion contract §17 said 03 must replace. Every other existing fragment, ragdoll, blast, fire, save and game-loop test passed unmodified (`build/all-living-m03-legacy.log`). |
| `.\gradlew.bat test --tests *SpeciesFragmentPhysicsTest` | First attempt did not compile (a test message typed as a non-String); fixed. Then **43 tests, 0 failures** (`build/all-living-m03-species.log`). |
| `.\gradlew.bat test --tests *AllLivingBlastDeathTest --tests *BlastLethalityTest --tests *RuntimeBoundsTest --tests *SpeciesFragmentPhysicsTest` | First attempt did not compile (the same kind of typo in `BlastLethalityTest`); fixed. Then **126 tests, 0 failures**: 68 + 11 + 4 + 43 (`build/all-living-m03-new.log`). |
| Mutation check: `MIN_LAUNCH_MASS` set to 0.01 and the settled-cap exemption disabled, then `test --tests *SpeciesFragmentPhysicsTest` | **9 of 43 failed as intended** (six launch-floor cases, the centred-blast case, the settled-cap case, the anchored-cap case) (`build/all-living-m03-mutation.log`). Both files were restored from copies and checked. |
| `.\gradlew.bat build` | **BUILD SUCCESSFUL in 4 m 26 s. 1090 tests in 125 classes, 0 failures, 0 errors, 0 skipped**; `javadoc` and `check` executed (`build/all-living-m03-build.log`). Base after 02 was 979 in 123. |

Not run: `performanceTest` (the per-step change is one null check; the per-frame allocation
tests and `RuntimeBoundsTest`'s fragment allocation budget passed) and **native QA**. The only
renderer changes are two skips (fragmented carcasses; pieces with no human id); no scene stages
an animal blast yet and nothing new is drawn, so there was nothing to capture. The skips
themselves are not exercised by headless tests.

Launch evidence printed by `noPieceIsThrownFasterThanTheLaunchFloorAllows` (point-blank keg, 0.3
blocks from the body; speeds include the 4.5 m/s upward bias and the sideways scatter; the
blast-speed ceiling `9 × 3.8 / 1.25` is 27.4 m/s): fastest piece deer 27.0, wolf 26.3, bird
27.4, hare 26.5, thornhorn 25.8, gloomstalker 27.6 m/s. Without the floor every piece under about
0.56 kg would have left at the 42 m/s clamp.

### What was built

Decisions and exact symbols are in contract §8.1 and §9.1.

| Path | Change |
| --- | --- |
| `entity/Entity.java` | Blast record moved here from `Npc` (`dismemberOnDeath`, `blastX/Y/Z`, `blastStrength`); `killBy(boolean)` moved here; new `recordBlastDeath(x, y, z, strength)` (refuses unless really dead and unrecorded) and `clearBlastDeath()`. |
| `entity/Npc.java` | Record and `killBy` removed (inherited). |
| `combat/ExplosionSystem.java` | `lethalToHumans` → `lethalToLiving`; gate, `insideLethalRadius` and `killOutright` typed on `Entity`; per-kind consequences; the Creative skip still runs first. Numbers unchanged. |
| `entity/BodyFragmentSystem.java` | `DeathPoses` interface, `setDeathPoses`, `deathPose(Creature)`, `deathPose(Npc)`; `spawnFromNpc(g, n, pose, …)` overload (the old signature is the rest pose); `spawnFromCreature(g, c, pose, …)`; `spawnPlayerRemains(g, player)`; generic private `launch`; launch-mass floor; one anchored carcass per harvestable animal (`anchorHarvest`, public static `anchoredRemains(List<Carcass>)`); the record follows the torso; the settled cap and slow tick keep anchored torsos; `MAX_ANCHORED_REMAINS` eviction removes record and torso together. |
| `entity/BodyFragmentConstants.java` | `MIN_LAUNCH_MASS` 1.25, `MAX_ANCHORED_REMAINS` 60; cap comments. |
| `entity/BodyFragment.java` | `harvest` (carcass link). |
| `entity/Carcass.java` | `remains` (torso link), `fragmented()`, `atRest()`. |
| `entity/Creature.java` | `CreatureType.leavesCarcass()` (all but BIRD); `RagdollSystem` uses it. |
| `entity/EntityManager.java` | Creature and NPC blast deaths routed to fragments with the death pose; `forgetTarget` clears stale `combatTarget`/`targetEntity`; `nearestCarcass` skips remains still in flight. |
| `entity/NpcAppearance.java` | `NEUTRAL_CAMP_INDEX`, `setNeutral()`. |
| `entity/Player.java` | `restoreCreativeBody` clears the blast record. |
| `gfx/model/AnatomyModels.java` | `deathPoses(DoubleSupplier clock)`. |
| `Game.java` | Constructor wires the pose source (`totalTime`); death transition extracted to package-private `enterDeathIfDue()` (spawns player remains); `respawn()` package-private and clears the record. 980 lines of the 1000-line budget. |
| `InteractPromptBuilder.java` | "The *species* is still falling" for remains in flight. |
| `engine/Renderer.java` | Skips fragmented carcasses; **temporarily** skips pieces with `piece == null`. |
| `save/FragmentsSection.java` | **Temporarily** leaves animal pieces out of v1. |
| `qa/RuntimeBudgetSnapshot.java` | `anchoredRemains` component, hard limit and both summaries (smoke output gains `anchoredRemains=`). |
| `src/test/.../AllLivingBlastDeathTest.java` (new) | 68 tests: per target (6 species, camp member, wandering trader, raider, villager, settlement trader, captive, patrol, bounty hunter, counterattacker, Survival player) inside and once only, the inclusive boundary against the adjacent float outside with a non-lethal twin, cover, Creative; a thrown bomb vs a wolf (credit, record, target clearing, harvest once), chained kegs with the player's death transition and respawn, an airborne bird, non-blast deaths. |
| `src/test/.../entity/SpeciesFragmentPhysicsTest.java` (new) | 43 tests: per species pieces and placement, launch floor, floor, wall, water; one record per body, harvestable only at rest, settled cap, anchored cap, release and rot, despawn radius. |
| `BlastLethalityTest` | Superseded test replaced by `creaturesAndThePlayerInsideTheLethalRadiusDieAndComeApartToo`; helper parameter renamed. |
| `RuntimeBoundsTest` | The stress fills the anchored cap; the final despawn assertion now keeps exactly the anchored torsos. |
| docs | contract §8.1, §9.1, §15, §17, §18; `ARCHITECTURE.md` lethal-blast and fragment paragraphs; `DEVELOPING.md` (renamed flag, `Entity.killBy`, `recordBlastDeath`). |

### Limitations

- **Animal remains are invisible** in the running game until 04 (`drawFragment` skip), and the
  fragmented carcass is never drawn, so an animal blown apart vanishes apart from its blood
  bursts; the F prompt and harvest still work at the torso's resting place.
- **Animal remains are not saved** until 05. A save keeps the record as an ordinary carcass,
  which loads as a whole carcass in the fixed sprawl at the torso's ground point: one body,
  reward and arrows kept, pieces lost. Human pieces and player remains are saved in
  `world.fragments` v1 as before (the player's with the neutral look), in the rest pose: box
  placement is exact, the captured pose and breathing scale (≤ 1.2 %) are not kept.
- NPC deaths now start from the captured pose. The existing human draw path still draws
  rest-pose geometry oriented by the piece's `orientation` (which includes the pose rotation),
  so each box is where its collision box is; only intra-piece articulation and breathing scale
  are not shown. 04 replaces the path.
- **Player remains while dead:** the world stays paused behind the death overlay (unchanged
  gate), so the remains wait at the death spot and fall once play resumes after respawn. During
  the overlay the camera stays at the player's eye, inside the rest-pose head piece. The remains
  are distance-culled like any piece if the player respawns more than 170 blocks away. Not
  visually checked; 04/10 should capture it.
- The neutral look still shows the friendly badge (`Animator.applyAppearance` shows it for any
  non-hostile look). No field marks player remains apart from an NPC's pieces.
- Heavy torsos still barely move (a thornhorn torso gets about 0.14 m/s from a scrap bomb before
  the upward bias); the floor only caps light pieces. This is the open v0.8.0 tuning question.
- `CreatureAI.update` still has no `dead` guard (assigned to 06/09): a creature killed by a blast
  in the frame gets one more AI and physics step before its pieces spawn where it then stands.
- Contract §8.6 now applies: a Survival player inside 3.9 blocks of their own scrap bomb (5.7 of
  a keg) dies and comes apart.
- Release-facing texts still say "Only people are dismembered" (`README.md` known limitations,
  `COMBAT_LETHALITY_AND_MOLOTOV.md`, `docs/releases/v0.8.0.md`); 11 owns them once 04 and 05
  have landed.

### Handoff to milestone 04

- Draw every piece from its definition: replace the `f.piece == null` early return in
  `Renderer.drawFragment` with the sequence in the 02 handoff (`AnatomyModels.modelOf`,
  `resetPose`, appearance for the humanoid, `applyPose(m, f.pose)`, `isolatePart`,
  `f.rootTransform`), and move human pieces onto it so the captured pose shows.
- Keep `renderCarcasses` skipping `c.fragmented()`. The anchored torso is `carcass.remains`
  (piece 0); the record lies at `(torso.x, torso.y − halfHeight, torso.z)`.
- Player remains are humanoid pieces with `NpcAppearance.setNeutral()` (archetype null,
  campIndex `NEUTRAL_CAMP_INDEX` 2). Decide whether the badge should show; if a marker is
  needed, add it to `BodyFragment` and have 05 persist it.
- Add a QA scene that blows up each species and a Survival player through the production entry
  points: `explosions.explode(..., true)`, then `entities.fastTick` / `Game.enterDeathIfDue`.

### Handoff to milestone 05

- Persist in the new optional section (contract §7): animal pieces (family ordinal, piece id,
  position, orientation, decay; pose optional) and the record link (`Carcass.remains` ↔
  `BodyFragment.harvest`, both object references now, so a stable remains id is needed), and
  restore the anchored torso's exemptions. `restoreSettled` already applies the settled cap
  through `admitSettled`, which passes over anchored torsos.
- Until then `FragmentsSection.readable` returns false for `piece == null`; remove that once the
  new section writes animal pieces. `world.fragments` v1 must stay byte-identical for people.
- `SaveSystem.write` settles ragdolls and fragments before it writes carcasses, so a record's
  saved position is always its rested torso's ground point.

## Milestone 04 — species fragment rendering (2026-09-26)

### Start state

- Branch `feature/all-living-dismemberment-combustion`, tip `7d6c4ae` (milestone 03); the three
  03 checkpoints are recorded in the table above. `git fetch origin`: `main` = `origin/main` =
  `3704f4d`, nothing incoming. Working tree clean apart from the user's untracked `.agents/` and
  `AGENTS.md`, which stay untracked.
- Same host and portable Temurin 25.0.4.1+1 JDK; Gradle 9.1.0, `--no-daemon --console=plain`.

### Commands and results

All from the repository root in PowerShell with the portable JDK on `PATH`. Logs are in the
git-ignored `build/` directory.

| Command | Result |
| --- | --- |
| `.\gradlew.bat compileJava compileTestJava` | BUILD SUCCESSFUL (`build/all-living-m04-compile.log`). |
| `.\gradlew.bat test --tests *BodyFragment* --tests *Fragment* --tests *DeathRagdollTest --tests *BlastLethalityTest --tests *AllLivingBlastDeathTest --tests *SerializedEnumOrderTest --tests *CreativeBodyTest` (existing tests, `FragmentGeometryTest` moved to the new API) | 292 tests, **1 failed**: `FragmentGeometryTest.everyPieceIsDrawnWhereTheSimulationHasItTurnedTheWayItTurned`, 3 mm off. The first `contactDrop` read the piece's stored sweep height, which that test (like any caller that turns a piece without refitting) leaves stale. Fixed by working the sweep height out from the orientation (`build/all-living-m04-legacy.log`, the failing run). |
| `.\gradlew.bat test --tests *SpeciesFragmentDrawTest --tests *FragmentGeometryTest --tests *FragmentsSectionTest --tests *FragmentIsolationTest` | First run: 7 of 62 failed, all in the new test: six wound-rigidity checks off by ≈ 1.2e-4 (float rounding at world coordinates near 310; the check now runs near the origin) and the bird's separation check (its lowered wing was drawn inside the floor and 03's spawn push-out lifted it 5 cm; the fixture now stands bodies clear of the floor). Then **all green; draw preparation 0 bytes/frame** for 120 pieces of every family (`build/all-living-m04-new.log`). |
| Mutation check (scratch script: one mutation at a time in `Animator`/`FragmentModels`, then `test --tests *SpeciesFragmentDrawTest --tests *FragmentsSectionTest`, file restored and diffed) | **All six caught**: pose not applied (separation and bound tests fail for all 7 families); no contact drop (ground-contact test fails for deer, bird, hare, gloomstalker); no `resetPose` (reset, separation and ownership tests); badge rule removed (both player-remains tests); animal shells ignored (hare tail stump test); humanoid-style bound (box + 0.3 m) — only a reach test that mirrored the implementation failed, so that test was dropped; the containment test holds for the derived bound (`build/all-living-m04-mutation.log`). |
| `.\gradlew.bat test --tests *SpeciesDismemberQaSceneTest --tests *SpeciesFragmentDrawTest` | **45 tests, 0 failures**; each scene logs `8 bodies, 81 pieces {HUMANOID=20, DEER=11, WOLF=12, BIRD=7, HARE=7, THORNHORN=12, STALKER=12}, 5 harvest records on torsos, 0 carcasses drawn whole` (`build/all-living-m04-scene.log`). |
| Temporary probe `com.veylon.M04UprightProbeTest` (deleted, not committed) | Settled pieces of `dismember_species_close`, see limitations (`build/all-living-m04-upright-probe.log`). |
| `.\gradlew.bat installDist`, then five native capture runs (below) | BUILD SUCCESSFUL; 14 captures, every capture `glErrors=0 khrErrors=0`. |
| `.\gradlew.bat build` | **BUILD SUCCESSFUL in 7 m 46 s. 1136 tests in 127 classes, 0 failures, 0 errors, 0 skipped**; `javadoc` and `check` executed (`build/all-living-m04-build.log`). Base after 03 was 1090 in 125. |

Not run: `performanceTest` (the per-frame preparation is held at 0 bytes by the new allocation
test; wall-clock cost was not measured and this host is not the reference machine).

### What was built

Decisions are recorded in contract §7.2.

| Path | Change |
| --- | --- |
| `gfx/model/Animator.java` | `poseFragment(BodyFragment)` resolves the family's model; `poseFragment(EntityModel, BodyFragment)` refuses another model and runs `resetPose` → look (humanoid) → `AnatomyModels.applyPose` → `isolatePart`. `applyAppearance(EntityModel, NpcAppearance)` overload. The camp badge needs `campIndex >= 0`. |
| `gfx/model/FragmentModels.java` | Rewritten for every family, keyed by `FragmentPiece`: `rootFrame(f, dest)`, `pieceFrame(f, dest)`, `cutFrame(p, i, pieceFrame, dest)`, `cutCount/cutCentre/cutSize(p, …)`, `radius(p)`, `radius(f)`, `contactDrop(f)`, `rot`, `tint`; new `SHELL_REACH` 0.03, `CONTACT_SPEED` 1 m/s. Removed the `Piece`-keyed `anchor`, `rootTransform`, `pieceTransform`, `radius`, `cut*` and `CULL_MARGIN`. |
| `engine/Renderer.java` | `drawFragment` draws every family through the above; culls the full-geometry sphere about the drawn centre; `drawBody` uses the appearance overload. |
| `entity/NpcAppearance.java` | `NEUTRAL_CAMP_INDEX` 2 → −2 (no camp, same vest). |
| `SpeciesDismemberQaScene.java` (new), `QaHarness.java` | Scenes `dismember_species`, `dismember_species_wall`, `dismember_species_close`. `QaHarness` is 1479 of its 1500-line budget. |
| `src/test/.../gfx/SpeciesFragmentDrawTest.java` (new) | 40 tests: model and part ownership per family, species palette kept, wrong model refused; first frame through the production spawn equals the living body box for box (7 families × 4 headings × 5 creature poses or 16 person poses); wounds rigid on their box faces through tumbles; limb stumps cover their joints in every pose; wing and neck wound sizes, hare haunch; bound contains every box and wound in every pose, look and tumble; settled pieces on the floor and the harvest record under the drawn torso (6 species); live bodies after pieces, corpse after a piece, piece after piece, wolf vs deer models; player remains look; allocation. |
| `src/test/.../SpeciesDismemberQaSceneTest.java` (new) | 5 tests: every body comes apart once per scene, five fragmented records and none whole, camera player untouched, wall stops the pieces; two runs match exactly. |
| `FragmentGeometryTest`, `FragmentsSectionTest` | Moved to the `FragmentPiece` API with every human assertion kept; new `thePlayersRemainsKeepTheirPlainCamplessLookThroughTheUnchangedFormat`. |
| docs | contract §7.1 pointer, §7.2 (new), §9.1, §15, §18; `ARCHITECTURE.md` fragment paragraph; `DEVELOPING.md` scene list, QA section, focused test command, creature step 4, contributor rule. |

### Visual QA

- **Environment.** Windows 11 Pro x64 10.0.26200; NVIDIA GeForce RTX 3060 Ti, driver 610.88,
  OpenGL 3.3.0 with KHR_debug; framebuffer 1280 × 720, default graphics settings.
- **Commands.** After `.\gradlew.bat installDist`, per run in PowerShell:
  `$env:VEYLON_SEED='20260919'; $env:VEYLON_SCENE='<scene>'; $env:VEYLON_SHOT='<shots>';
  $env:VEYLON_CAPTURE_TAG='<tag>'; java --enable-native-access=ALL-UNNAMED -Xmx2G
  '-Dveylon.dataDir=build/qa/all-living-m04' -cp 'build/install/veylon/lib/*' com.veylon.Main`
  with (`dismember_species`, `0.5,1,3,8`, `species_open`), (`dismember_species_wall`,
  `0.5,1,3,8`, `species_wall`), (`dismember_species_close`, `0.5,1,3,8`, `species_close`),
  (`dismember_species_wall`, `1.3`, `species_wall_flight`), (`dismember_species_close`, `1.3`,
  `species_close_flight`). The isolated data directory keeps saves and settings away from the
  user's.
- **Captures** (git-ignored, not committed): `build/qa/all-living-m04/screenshots/` —
  `species_open_{0,1,3,8}s.png`, `species_wall_{0,1,3,8}s.png`, `species_close_{0,1,3,8}s.png`,
  `species_wall_flight_1s.png`, `species_close_flight_1s.png`; run logs beside them. A first
  round placed the hare and bird behind the left HUD panels and hid the wall scene's pieces
  behind a ledge; the row was moved right and the ledge replaced by a step that rises away from
  the camera before the captures above were taken.
- **Inspected in the images:**
  - *No duplicate limbs or intact bodies:* every 1 s shot shows each body once, then only
    pieces; the scene logs 0 carcasses drawn whole.
  - *No rest-pose pop:* the 0.5 s and 1 s shots show the same poses (bird mid-beat in the air,
    hare mid-hop, gloomstalker crouched, guard mid-stride, lunging wolf, grazing deer, tossing
    thornhorn); the 1 s shot adds only blood and the player's remains.
  - *Wings and tails:* the bird's wings fly as thin boards in `species_close_flight_1s`; tails
    are too small to tell apart at these distances (headless coverage only).
  - *Ground contact:* settled pieces lie on the grass and the step top, with none seen
    hovering or sunk; exact contact is headless evidence.
  - *Cut surfaces:* dark red faces are visible on human heads and limbs, the hare and
    gloomstalker torsos, and the thornhorn torso.
  - *Species look:* the deer keeps its glow spots and antlers, the gloomstalker its quills, and
    each species its colours.
  - *Player's remains:* plain vest with no badge, beside a guard wearing the teal badge.
  - *Long pieces:* none disappeared, but culling at the frustum edge was not exercised on
    purpose; the bound is headless evidence.
- **Not visually verified:** individual wound faces at large scale (thornhorn neck stump, hare
  tail stump over the haunch, wing sockets), the deer tail's contact lowering, the real player's
  death transition and the view from inside the death overlay (the scene uses a stand-in),
  macOS.

### Limitations

- **Upright legs (physics, not drawing).** In `dismember_species_close` all 8 deer leg pieces
  and one gloomstalker upper leg came to rest standing on end (long axis within 0.01 of
  vertical); human and most gloomstalker legs lie flat. `BodyFragmentSystem.spin` gives a piece
  standing exactly on its end no toppling torque inside `FLAT_BAND`, and a grazing deer's legs
  start vertical with little spin. They are supported, not floating. Left for a physics tuning
  pass (with the torso-speed question), not changed here.
- A piece the living model drew inside a block (a bird flapping close to the ground) is pushed
  out by 03's spawn push-out, so it can move a few centimetres in its first frame.
- Accessories are outside the collision box (contract §7): antlers, horns or a slung spear can
  dip into the ground when a piece lies on them.
- Horizontal sweep inflation is not compensated: a thin piece against a wall may stand up to
  2.5 cm off it.
- Wounds keep the rest table's place on their box: a head hung off a box-less neck keeps its
  stump under the head rather than exactly at the neck joint of a posed death.
- A v0.8.0 build loading a save with player remains draws their camp badge (vest unchanged).
- The QA scenes stage each death through the kill, record and spawn entry points, not
  `ExplosionSystem` (one explosion per body would kill neighbours first and leave craters);
  the blast gate itself is covered by `AllLivingBlastDeathTest`.
- Animal pieces are still not saved (05); release-facing "Only people are dismembered" texts
  are unchanged (11).

### Handoff to milestone 05

- Everything the renderer needs from a restored piece: `f.definition` (family + piece id),
  `f.pose` (the family's `restPose()` is drawn correctly; saving the captured pose would also
  keep intra-piece articulation — 7 floats per joint through `FragmentPose.Recorder`),
  `f.appearance` (humanoid only), `pos`, `orientation`, `decay`, `settled`, and the `harvest` link.
  `contactDrop` and the bounds are derived, so no presentation state needs saving.
  `restoreSettled` refits the sweep box.
- Player remains already round-trip through `world.fragments` v1 with `NEUTRAL_CAMP_INDEX` −2
  (`FragmentsSectionTest.thePlayersRemainsKeepTheirPlainCamplessLookThroughTheUnchangedFormat`).
- `SpeciesDismemberQaScene` stages 81 pieces of all seven families and five anchored records in
  a headless `Game` (`SpeciesDismemberQaSceneTest.arena`), a ready fixture for a round-trip test.
- Keep green: `SpeciesFragmentDrawTest`, `SpeciesDismemberQaSceneTest`, `FragmentsSectionTest`,
  `FragmentGeometryTest`, `FragmentIsolationTest`, `FragmentAnatomyModelTest`.

### Handoff to milestone 10

- Attach body-fire presentation to pieces with `FragmentModels.pieceFrame` (collision box
  frame) and `FragmentModels.radius(f)` about `(pos.x, pos.y − contactDrop(f), pos.z)`.
- Any new draw from a shared model must start with `resetPose` (and a person's look), as
  `Animator.poseFragment` does.

## Milestone 05 — remains persistence (2026-09-26)

### Start state

- Branch `feature/all-living-dismemberment-combustion`, tip `a203df9` (milestone 04); the four
  04 checkpoints are recorded in the table above. Working tree clean apart from the user's
  untracked `.agents/` and `AGENTS.md`, which stay untracked.
- Same host and portable Temurin 25.0.4.1+1 JDK; Gradle 9.1.0, `--no-daemon --console=plain`.
- Read: `AGENTS.md`, the pack's `00_BASE_KNOWLEDGE.md` and `05_FRAGMENT_SAVE_COMPATIBILITY.md`,
  the `veylon-save-compatibility` skill, contract §7, §9, §14, and this file.
- Predecessors checked in source before editing: `BodyFamily`, `FragmentAnatomy`,
  `FragmentPose.Recorder` (02); `Carcass.remains` ↔ `BodyFragment.harvest`, `spawnFromCreature`,
  `spawnPlayerRemains`, `SaveSystem.save` settling fragments first, and
  `FragmentsSection.readable` leaving animal pieces out (03); `NEUTRAL_CAMP_INDEX` −2 (04).

### Commands and results

All from the repository root in PowerShell with the portable JDK on `PATH`. Logs are in the
git-ignored `build/` directory.

| Command | Result |
| --- | --- |
| `.\gradlew.bat compileJava compileTestJava` | BUILD SUCCESSFUL (`build/all-living-m05-compile.log`). |
| `.\gradlew.bat test --tests com.veylon.save.* --tests *BodyFragment* --tests *Fragment* --tests *SerializedEnumOrderTest --tests *GameLoopIntegrationTest --tests *SpeciesDismemberQaSceneTest --tests *AllLivingBlastDeathTest --tests *RuntimeBoundsTest` (production code changed, existing tests untouched) | 344 tests, **1 failed**: `FragmentsSectionTest.saveWithoutTheSectionLoadsWithNoFragments`, which imitated a save from before 0.8.0 by removing `world.fragments` only; `world.remains` then restored the pieces. Its fixture now removes both sections, as such a save has neither; its assertions are unchanged. Every other save, migration, fragment, blast and game-loop test passed unmodified (`build/all-living-m05-legacy.log`). |
| `.\gradlew.bat test --tests *RemainsSectionTest --tests *RemainsPersistenceTest --tests *FragmentsSectionTest --tests *SerializedEnumOrderTest` | **26 tests, 0 failures** (9 + 2 + 8 + 7) on the final code (`build/all-living-m05-new.log`). The new tests passed at their first run; the mutation checks below are the evidence that they can fail. |
| Mutation check (scratch PowerShell script: one mutation at a time in `RemainsSection` / `V3ExtensionSections`, then the remains, persistence and fragment test classes; each file restored and its hash compared with a backup) | **All 15 caught** (`build/all-living-m05-mutation.log`, `…-mutation2.log`): reader ignores `world.remains` (6 tests fail), records never re-tied (6), writer drops captured poses (2), arrows not restored (3), unknown family read as a person (1), no unit-quaternion check, no piece-scale check, no duplicate-link check, no leaves-a-carcass check, no torso/species check (each fails the malformed-section test; its cases were rewritten so each breaks exactly one rule), `world.fragments` not validated beside `world.remains` (`FragmentsSectionTest.corruptOrOversizedSectionIsRejected`), writer writes no links (5), another joint table dropped or read as this build's (1 each). |
| `.\gradlew.bat build` | **BUILD SUCCESSFUL in 5 m 6 s. 1149 tests in 129 classes, 0 failures, 0 errors, 0 skipped**; `javadoc` and `check` executed (`build/all-living-m05-build.log`). Base after 04 was 1136 in 127. |

Not run: `performanceTest` (no per-frame path changed; a save writes one more section, 16 bytes
in a world without remains; this host is not the reference machine and its durable-save gate
fails regardless) and native QA (nothing is drawn differently; the tests compare every loaded
piece's `FragmentModels.rootFrame` — the frame the renderer draws it in — with the saved
piece's, to 1e-5).

### What was built

Decisions, the byte layout and the compatibility matrix are in contract §14.1.

| Path | Change |
| --- | --- |
| `save/RemainsSection.java` (new) | `world.remains` v1: a pose table (one entry per pose object, rest = 0 joints), piece records (pose index, piece id, position, unit orientation, decay, look), harvest links (piece index, carcass index, lodged arrows and their kind). Bounded reader that validates everything before touching the world; malformed → `IOException`; unknown family or piece id → skipped; another build's joint table → the family's rest pose. |
| `save/FragmentsSection.java` | `parse(byte[])` validates without restoring (`read` = parse + restore); `placementReadable` and `readQuaternion` shared with `RemainsSection`. The v1 bytes are unchanged. |
| `save/V3ExtensionSections.java` | Writes `world.remains` after `world.fragments`; collects both payloads and resolves them after the envelope loop in `readSettledPieces` (remains when present, v1 otherwise; v1 always validated). |
| `entity/BodyFragmentSystem.java` | Public `restoreSettled(BodyFragment torso, Carcass record)`: ties a loaded torso to its record, refits, admits and puts the record under the torso; `IllegalArgumentException` unless the piece is the core of the record's species and both sides are free. |
| `entity/Carcass.java` | `remains` Javadoc: now saved. |
| `src/test/.../save/RemainsSectionTest.java` (new) | 9 tests: every species, a person and the player's remains round-trip with family, piece, place, orientation, rot, look, every pose joint, box size, drawn frame and rot tint equal, one pose per body, records re-tied with meat, hide, rot and arrows (deer partly taken), and a second save of the loaded world writes identical bytes; a save without `world.remains` loads as v0.8.0 would; a hand-composed literal v0.8.0 `world.fragments` payload loads (directly and through the whole reader) and the writer still produces it byte for byte; a mid-flight save (deer, wolf, person); 600 pieces with 60 anchored records; loading another world and then the first again; the writer's rest-pose fallback; unknown families, pieces and joint tables; 44 malformed payloads, each rejected directly and by the full load with the live world, its pieces, records and links untouched. |
| `src/test/.../RemainsPersistenceTest.java` (new) | 2 production-path tests: a keg kills a wolf (three iron arrows lodged) and a deer through `ExplosionSystem` and `EntityManager.fastTick`; the wolf is torn by hand after one load and skinned after another (`WorldInteractions.interactWithNearbyCarcass`), three repeated loads in between: exactly one wolf's meat, hide and arrows in total, and the emptied record releases its torso. The player killed by a keg, saved during the death screen and after respawning: the remains stay at the death spot with the plain look, the loaded dead player does not come apart again, and the player comes back as respawned. |
| `SerializedEnumOrderTest` | Pins `CreatureType` (persisted by the base body but never pinned), `BodyFamily` and every family's piece names; the humanoid list equals the v1 `Piece` order. |
| `FragmentsSectionTest` | `saveWithoutTheSectionLoadsWithNoFragments` removes both sections (see above). |
| docs | contract §7.1 pointer, §9.1, §14.1 (new), §18; `ARCHITECTURE.md` transient-state paragraph, persistence table and section paragraph (also: the blast record lives on the entity since 03, not the `Npc`); `DEVELOPING.md` creature step 4 and the enum pitfall. |

### Limitations

- **A v0.8.0 build reading a new save** was not run (no v0.8.0 binary in the suite): by its
  source it skips `world.remains` by length and loads people in the rest pose (a player's remains
  with a camp badge), no animal pieces, and each record as a whole carcass without its arrows.
- A **whole** carcass still loses its lodged arrows on save, as it always has: the base carcass
  record never stored them, and only the anchored record's link carries them now.
- People are written twice (v1 and `world.remains`): at most 600 × 47 bytes of duplication.
- A pose outside the reader's bounds is saved as the rest pose; none of the live animation's
  poses comes near them (angles within a turn, offsets ≤ 0.62 m, scale ≈ 1 ± 0.012).
- Loading keeps a record's saved meat, hide and rot and re-derives its position from the torso
  (the same floats, since the save laid the torso down first).
- Release-facing texts (README save-format paragraph, CHANGELOG, "Only people are
  dismembered") are unchanged; 11 owns them.

### Handoff to milestone 06

- Nothing in 05 touches combustion. Keep new combustion state out of every save section
  (contract §14, R6): `SaveSystem.save` writes base v3 plus the sections in
  `V3ExtensionSections.write`; add nothing there. A load runs `Game.newWorld` → `WorldBootstrap`
  resets before any section is read, so a system reset there is cleared on load too.
- Remains and records after a load are ordinary settled pieces and carcasses:
  `BodyFragment.settled` is true, `Carcass.atRest()` is true, `Carcass.remains`/`BodyFragment.harvest`
  are re-tied. Dead bodies and remains never burn as living bodies (contract §10).
- Keep green: `RemainsSectionTest`, `RemainsPersistenceTest`, `FragmentsSectionTest`,
  `SerializedEnumOrderTest`, and the save package (`com.veylon.save.*`).

### Handoff to milestone 09

- Loading a save written while the player was dead restores the remains once; the blast record
  is transient, so the death transition after the load spawns nothing
  (`RemainsPersistenceTest.thePlayersRemainsStayWhereThePlayerFellAndNeverComeApartTwice`).
  Death-time visual residue must stay out of the save like combustion.
- `BodyFragmentSystem.restoreSettled(f)` and `restoreSettled(torso, record)` are the only load
  entry points for pieces; a new per-piece field that must persist needs a `world.remains`
  version bump (older builds reject a newer version of a known section and with it the save) or a
  new section ID beside it (older builds skip it) — see contract §14.1.

## Milestone 06 — shared body combustion (2026-09-26)

### Start state

- Branch `feature/all-living-dismemberment-combustion`, tip `e8b825a` (milestone 05); the three 05
  checkpoints are now recorded in the table above. Working tree clean apart from the user's
  untracked `.agents/` and `AGENTS.md`, which stay untracked.
- Same host and portable Temurin 25.0.4.1+1 JDK; Gradle 9.1.0, `--no-daemon --console=plain`.
- Read: `AGENTS.md`, the pack's `00_BASE_KNOWLEDGE.md` and `06_SHARED_BODY_COMBUSTION.md` (and
  07–10 for the interfaces they expect), contract §4–§6, §10–§16, and this file.
- Predecessors checked in source: 05's `RemainsSection` and `V3ExtensionSections` write nothing
  about combustion (none added); 03's `Entity.recordBlastDeath` refuses a body that is not dead
  with no health left, so a burn death cannot acquire a blast record; no combustion class existed.
- Source anchors inspected before choosing the owner: `Entity.hurt`, `Player.hurt` (Creative
  gate), `Player.tickNeeds`/`tickAfflictions` (BURN 0.18/s, direct `health -=`),
  `PlayerTreatmentSystem` (poultice cures `BURN`), `FireSystem.damageNear`/`isRainedOn`,
  `LiquidFireSystem.burnOccupants`/`standsIn`, `SimulationScheduler`, `Game.fastTick`/`mediumTick`/
  `respawn`/`enterDeathIfDue`, `SleepSystem.tickSleep` (no extra ticks; a red flash above 0.5
  wakes the player), `WorldBootstrap`, `VoxelPhysics.refreshEnvironment`, `World.skyLight`
  (1.0 for an unloaded column, heightmap = top opaque block), `EntityManager.fastTick`.

### Commands and results

All from the repository root in PowerShell with the portable JDK on `PATH`. Logs are in the
git-ignored `build/` directory.

| Command | Result |
| --- | --- |
| `.\gradlew.bat compileJava compileTestJava` | BUILD SUCCESSFUL (`build/all-living-m06-compile.log`). |
| `.\gradlew.bat test` (production wiring in place, every existing test unmodified) | **BUILD SUCCESSFUL in 4 m 47 s: 1149 tests in 129 classes, 0 failures** (`build/all-living-m06-legacy.log`): the fast-tick insertion, the `CreatureAI` dead guard, `isRainedOn` delegating and the two snapshot components changed no existing outcome. |
| `.\gradlew.bat test --tests com.veylon.entity.BodyCombustionTest` | 121 tests, 0 failures, at the first run (`build/all-living-m06-body.log`). |
| `.\gradlew.bat test --tests com.veylon.CombustionIntegrationTest --tests com.veylon.simulation.SimulationSystemContractTest --tests com.veylon.entity.BodyCombustionTest` | 6 + 3 + 121 tests, 0 failures (`build/all-living-m06-new.log`), after one compile fix in the new test (`long` ragdoll counter). |
| Mutation check (scratch PowerShell script, one production mutation at a time, the three classes above, each file restored and its hash compared with a backup) | **All 21 caught** (`build/all-living-m06-mutation.log`; every file's hash matched its backup afterwards): BURN still hurting while alight (1 test fails), Creative keeping the fire (1), the `CreatureAI` dead guard removed (1), the combustion tick unwired from `Game.fastTick` (5), refresh adding fuel on top (27), environment outranking the player (1), no point tie-break (1), any water putting the fire out (7), head rain asked through `isRainedOn` (9), dead bodies ticked (13), no `dt` clamp (1), the injury every tick after a second (1), the player credited with their own fire (1), heat never decaying (13), a Creative player able to burn (13), afterburn at the contact rate (15), respawn keeping the fire (1), a new world keeping the tallies (1), `ignite` not refusing a torso under water (13), fuel draining during contact (53), shallow water draining like dry ground (7). |
| `.\gradlew.bat test --tests com.veylon.entity.CombustionAllocationTest --tests com.veylon.entity.BodyCombustionTest -i` | Passed. **Nobody burning: 0 bytes per fast tick; 40 people and 35 animals all burning: 1280 bytes per fast tick** (`build/all-living-m06-alloc.log`). A throwaway probe (deleted, not committed) split that: the feed loop 0, the rain predicate 0, and each `World.getChunk` cache miss 80 bytes (its `Map<Long, Chunk>` boxes the key) — 16 chunk changes between consecutive bodies per tick. That lookup is what every entity's physics already does per tick; the allowance is the ragdoll and fragment steps' 4 KB. A first draft with a 256-byte allowance failed on exactly this. |
| `.\gradlew.bat build` | **BUILD SUCCESSFUL in 5 m 24 s. 1278 tests in 132 classes, 0 failures, 0 errors, 0 skipped**; `javadoc` (doclint) and `check` executed (`build/all-living-m06-build.log`). Base after 05 was 1149 in 129. |

Not run: `performanceTest` (this host is not the reference machine and its durable-save gate
fails regardless; no wall-clock number for the combustion tick exists — contract §16's 0.2 ms
target is unmeasured) and native QA (nothing is drawn differently: body flames are milestone 10).

### What was built

Rules, API, cadence, damage equation and the reasons for each change from the proposals are in
contract §10.1 (with §11 and §13 notes).

| Path | Change |
| --- | --- |
| `entity/BodyCombustion.java` (new) | Per-body state: fire, fuel, peak, heat, soak, episode seconds, scorch, contact, owner, last exposure point, one pending candidate. Public getters, package-private mutators; derived `intensity()`. |
| `entity/CombustionSource.java` (new) | `DIRECT_HIT`, `LIQUID`, `BLOCK_FIRE`, `CAMPFIRE`, `TORCH` in dominance order, each with heat gain, fuel granted and nominal intensity. |
| `entity/CombustionConstants.java` (new) | Contract §15 values plus `IMMERSION_FRACTION`, `SCORCH_SECONDS`, `AFTERBURN_FLASH`, `MAX_BURN_SECONDS`, `TIMER_EPSILON`. |
| `entity/CombustionSystem.java` (new) | `FastTickSystem`: `expose`, `ignite`, `extinguish`, `clear`, `isBurning`, `burningBodies`, `isLivingBody`, `canBurn`, `fastTick`; immersion and head-rain rules; diagnostic counters. |
| `entity/Entity.java` | `public final BodyCombustion combustion`. |
| `entity/Player.java` | `inflictBurnInjury(Game)` (duration from the player's affliction stream); `tickAfflictions` skips `BURN` while alight; `restoreCreativeBody` clears the fire. |
| `Game.java` | `combustion` field; `fastTick` runs it between needs and entities; `respawn` clears the player's fire. 983 of 1000 lines. |
| `WorldBootstrap.java` | `game.combustion.reset()` in `resetForNewWorld`. |
| `ai/CreatureAI.java` | `update` returns at once for a dead creature (contract §2.4 gap). |
| `simulation/FireSystem.java` | New shared predicate `isPrecipitationReaching(g, x, y, z)`; `isRainedOn` delegates to it for the cell above (behaviour unchanged). |
| `qa/RuntimeBudgetSnapshot.java` | `burningBodies`, `livingBodies`, hard limit `burningBodies <= livingBodies`, smoke `burning=N/M`. |
| `src/test/.../entity/BodyCombustionTest.java` (new) | 121 tests: 8 cases × 13 families (6 species from `CreatureType.values()`, camp member, wandering trader, raider, settlement resident, captive, war party, Survival player), Creative immunity × the 12 non-player families, and 5 single tests. Cases: liquid ignition and contact rate, afterburn length/fade/budget/burnout, refresh without stacking within the cap and a weaker flame taking over, reignition as a new episode, heat build-up and graze decay, torso-deep versus hip-deep water, open versus roofed rain, burn death. Plus: Creative switch forgets the fire and Survival starts clear, all 120 orders of five contacts give one owner/point/damage, refused inputs, dt clamp, 3000-tick randomized bounds, a full particle budget, and 90 frames of identical movement for a burning and an unburned player. |
| `src/test/.../CombustionIntegrationTest.java` (new) | 6 tests through `Game`: equal fires and health under two frame partitions of 3.125 s (tolerance 1e-4; a hare dies in both), medium/slow ticks never advance a fire and the first fast tick burns at the contact rate, a burn death falls whole that tick without a last AI step, the player's burn death spawns no remains and respawn clears the fire, delayed kill credit (three birds: player-lit, wild, taken over), the medical burn budget and the poultice, the runtime budget count. |
| `src/test/.../entity/CombustionAllocationTest.java` (new) | An idle crowd at the NPC cap plus 35 animals costs under 64 bytes per fast tick (measured 0); the same crowd all burning — contact, afterburn, shallow water under a roof, a herd doused by rain and relit — stays under 4 KB (measured 1280, all chunk-key boxing). |
| `SimulationSystemContractTest` | Combustion is a fast-tick system, resets through the interface, and a new world carries no burning body or tally. |
| docs | contract §5, §6, §10.1 (new), §11, §13, §15, §16, §18; `ARCHITECTURE.md` reset list, tick table, transient state, persistence table and a paragraph on body fire. |

### Evidence of the decisions

- **Immersion at 0.6 of body height.** Water is whole cells and `VoxelPhysics` samples the
  body-centre cell. A person (1.75, torso box from 0.86 m) standing on the floor of a one-cell
  pool has the centre (0.875 m) under water but only legs and hips wet; the proposal would have
  put them out. The test pins: persons and the player keep burning in one cell of water with a 3×
  drain; every species is put out there; everyone is put out and cannot be lit in two cells.
- **Rain at the head cell through a new predicate.** `isRainedOn(x, y, z)` samples the cell above
  a block, and the column top is always lit, so asking it of a head cell under a two-high ceiling
  reports rain. Mutation 09 (asking `isRainedOn` of the head cell) fails the roofed-rain case for
  every family.
- **Package `entity`.** Package-private mutators make the read-only view a compiler fact, not a
  convention; nothing outside `entity` can set a fire except through the commands.

### Limitations

- **No production source ignites a body yet** (07). `FireSystem.damageNear` and
  `LiquidFireSystem.burnOccupants` still hurt on the medium tick and roll `BURN` at 50 %; 07 must
  remove or reroute them as it connects the sources, or damage doubles.
- Attack/crime notification per burn episode is not implemented (07 owns it; contract §10
  "Attribution and crime").
- Panic does not exist; `hasExposure()`/`exposureX/Y/Z()` are ready for it (08).
- Death leaves the fire state frozen on the dead body (`burning()` keeps its last value;
  `isBurning` is false); nothing transfers it to ragdolls or fragments yet (09). Settlement
  deactivation and load create fresh bodies, so their fires are gone, but no explicit
  deactivation policy has been audited (09).
- A body in exposed rain in constant contact with a flame that the rain does not stop (only a
  campfire could be one, in 07) is put out every 1.5 s and relit by heat: contact does not reset
  `soak`.
- `scorch`, `AFTERBURN_FLASH` and the log lines are placeholders for 10's presentation.

### Handoff to milestone 07

- Report contacts with `g.combustion.expose(entity, CombustionSource.KIND, intensity, byPlayer,
  sourceId, x, y, z)` from inside the fast tick, **before** `CombustionSystem` resolves them: add
  the source sampling at the start of `CombustionSystem.fastTick` (or a call just before it in
  `Game.fastTick`, which has 17 lines left), never from the medium tick, so contact is sampled at
  20 Hz. A contact reported during a frame waits for the next fast tick.
- Direct molotov hits: `g.combustion.ignite(g, victim, CombustionSource.DIRECT_HIT, 1f,
  p.fromPlayer, <id>, x, y, z)` in `ProjectileSystem.onEntityHit`; it refuses a torso under water
  (returns false) and lights at once, the next fast tick dealing contact damage.
- Use `CombustionSource.X.nominalIntensity` except for liquid patches (`Patch.intensity()`); pass
  a stable `sourceId` (spill id, packed cell) because it breaks ties.
- Remove the `hurt`, `damageFlash` and `BURN` roll from `FireSystem.damageNear` and
  `LiquidFireSystem.burnOccupants` when their sources move to `expose`; the combustion tick now
  owns all burn damage and the injury (`Player.inflictBurnInjury`). Contract §17 lists the tests
  that assert the legacy medium-tick numbers.
- `CombustionSystem.isLivingBody`/`canBurn` decide eligibility; do not add species lists.
- Attack notifications: once per burn episode per NPC — a new field on `BodyCombustion` reset in
  its `ignite` is the natural place (package `entity`).
- Keep green: `BodyCombustionTest`, `CombustionIntegrationTest`, `CombustionAllocationTest`,
  `SimulationSystemContractTest`, `MolotovTest`, `FireWeatherTest`, `CombatFireIntegrationTest`.

### Handoff to milestones 08–10

- 08 (panic): read `g.combustion.isBurning(n)` (alive and burning), `n.combustion.intensity()`,
  `inContact()`, and `hasExposure()` + `exposureX/Y/Z()` for the flee direction. AI runs after the
  combustion tick in the same fast tick, so it sees this tick's fire. There is no recovery timer
  on the state: keep panic intent on the actor (contract §12).
- 09 (lifecycle): a dead body's `combustion` keeps `burning()`, `intensity()`, `scorch()` and
  `burnSeconds()` as they were when it died (the tick never touches a dead body) — the natural
  residue snapshot source. `CombustionSystem.clear(e)` forgets everything; `extinguish(e)` keeps
  the scorch. `Game.respawn` and `Player.restoreCreativeBody` already clear the player.
- 10 (presentation): read-only getters only; `scorch()` grows with `intensity × dt / 10 s` and never
  falls. The player's red flash (1 on contact ticks, ≥ 0.5 × intensity in afterburn) is the only
  feedback so far.

## Milestone 07 — fire sources connected (2026-09-27)

### Start state

- Branch `feature/all-living-dismemberment-combustion`, tip `a588223` (milestone 06); the three 06
  checkpoints are now recorded in the table above. Working tree clean apart from the user's
  untracked `.agents/` and `AGENTS.md`, which stay untracked.
- Same host and portable Temurin 25.0.4.1+1 JDK; Gradle 9.1.0, `--no-daemon --console=plain`.
- Read: `AGENTS.md`, the pack's `00_BASE_KNOWLEDGE.md` and `07_CONNECT_ALL_FIRE_SOURCES.md` (and
  08–11 for what they expect from 07), contract §4, §6, §10, §11, §15–§18, and this file.
- Predecessors checked in source: 06's `CombustionSystem` API and fast-tick order as recorded;
  `FireSystem.damageNear` and `LiquidFireSystem.burnOccupants` still hurting on the medium tick
  with their 50 % `BURN` rolls, as 06 left them; no production caller of `expose`/`ignite`.
- Source anchors inspected: `ProjectileSystem.onEntityHit`/`shatter`/`step`/`entityAt`,
  `LiquidFireSystem.spill`/`tick`/`burnOccupants`/`standsIn`/`igniteTouching`,
  `FireSystem.ignite`/`mediumTick`/`damageNear`/`tickCampfires`/`isRainedOn`,
  `ExplosionSystem.detonate` (block ignition, attack reporting), `WeatherSystem`/`EventSystem`
  ignition, `BlockType`, `World.getBlock`/`getChunk`/`campfireFuel`, `AmbienceSystem` (campfires
  drawn from `campfireFuel`), `PlayerBlockActions.placeSelectedBlockAt`, `SettlementManager`
  (`onNpcAttackedByPlayer`, `residentSleepPosition`, `spawnPointFor`), `NpcAI`/`SettledNpcAI`
  movement, `Pathfinder`, `Steering`, `VoxelPhysics`, `Entity.collidesAt`, `CreatureModels`
  bird wings and `FragmentAnatomy` tables, and every test that drove fire damage through a
  medium tick.

### Commands and results

All from the repository root in PowerShell with the portable JDK on `PATH`. Logs are in the
git-ignored `build/` directory.

| Command | Result |
| --- | --- |
| `.\gradlew.bat compileJava`, then `compileTestJava` | Main compiled; tests failed to compile exactly where they named the removed legacy symbols (`ENTITY_DPS_*`, `MAX_TRACKED_NPC_SPILLS`, `trackedNpcSpills()`) in `MolotovTest`, `CombatFireIntegrationTest`, `RuntimeBoundsTest`, `SimulationSystemContractTest`; adapted as contract §17 records. |
| `.\gradlew.bat test` with `MolotovTest`, `FireWeatherTest`, `CombatFireIntegrationTest`, `BlastLethalityTest`, `CreativeHazardsTest`, `SurvivalCreativeParityTest`, `SimulationSystemContractTest`, `RuntimeBoundsTest`, `BodyCombustionTest`, `CombustionIntegrationTest`, `CombustionAllocationTest`, `CombatSystemsTest` | 229 tests, 2 failures at the first run (`build/all-living-m07-focused.log`): an assertion of mine that the player's own pool is not "player-owned" (it is; only the credit is withheld, as 06 built), and the four-patch test's bodies no longer touching the pool's centre once damage scales with the liquid's strength. Both fixed; green since. |
| `.\gradlew.bat test --tests com.veylon.entity.FlameSourceIgnitionTest` | First run 45 tests, 3 failures, all fixture mistakes (two walking lanes outside the flattened arena, so the player fell; an unsturdy deer burned to death); green after moving them. Now 46 tests (`build/all-living-m07-new.log`). |
| Throwaway settlement probe (a scratch test, deleted; four seeds, first three settlements each, 3 min per visit at 20 in-game minutes per second, 577 000 NPC ticks) | With the sources connected and nothing else: **21 residents caught fire in normal life** (`build/all-living-m07-settlement-probe.log`). After the pathfinder rule and the halved campfire rate: 14 (`probe2`); with steering round fires: 3 (`probe3`); a push-out attempt made it 8 (`probe5`, reverted); with fires as obstacles in physics: **0 contacts, 0 ignitions** (`probe6`), and again with the final code (`build/all-living-m07-settlement-probe-final.log`). |
| `.\gradlew.bat test --tests com.veylon.entity.CombustionAllocationTest --tests com.veylon.CombustionIntegrationTest -i` | Passed. Nobody burning **0** bytes per fast tick, everybody burning **0** (06 measured 1280, all chunk-key boxing, now cached), every flame at its cap around a moving crowd **96** (the campfire fuel map's key; 160 before a boxed default was removed). |
| `.\gradlew.bat test` | **BUILD SUCCESSFUL in 5 m 32 s: 1326 tests in 133 classes, 0 failures, 0 errors, 0 skipped** (`build/all-living-m07-test.log`). 06 ended at 1278 in 132. |
| Mutation check (scratch PowerShell script, one production mutation at a time; `FlameSourceIgnitionTest`, `MolotovTest`, `CombustionIntegrationTest`, `CombatFireIntegrationTest`; each file restored in `finally` and its hash compared) | Baseline green. **17 of 20 caught** at the first pass (`build/all-living-m07-mutation.log`): a direct hit that no longer ignites (19 failing), no sweep, liquid reaching 50 blocks up, a rained-on pool still touching, no attack de-duplication, environmental fires reporting attacks, spread or pool-lit fires losing their thrower, paths through campfires, people and animals walking into fires, no steering round them, flames placed into bodies, no wing reach, jumps swept, the direct hit counted as another bottle, no sampling at all (49 failing), torches and campfires never touching. Survived: a rained-on burning block still touching, an unfueled campfire burning, a burning block reaching 1.5 blocks sideways — all three because "never touched" was asserted as `heat() == 0`, which a body that caught also shows. The tests now assert no flame was ever applied (`hasExposure()` false, not alight); a re-run of the three (`build/all-living-m07-mutation-rerun.log`) **caught all three**. Every file's hash matched its backup afterwards. |
| `.\gradlew.bat build` (the tree committed below) | **BUILD SUCCESSFUL in 5 m 42 s: 1326 tests in 133 classes, 0 failures, 0 errors, 0 skipped**; `javadoc` (doclint) and `check` executed (`build/all-living-m07-build-final.log`). An earlier `build` before the last test-assertion and comment fixes was also green (5 m 38 s). |

Not run: `performanceTest` (this host is not the reference machine and its durable-save gate
fails regardless; contract §16's 0.2 ms combustion target is still unmeasured) and native QA
(nothing is drawn differently: body flames are milestone 10; the sources' own visuals are
unchanged).

### What was built

The as-built source table, sweep, ownership, attack, rain and keep-out rules and every change
from the proposals are in contract §4.1; budgets in §16; replaced tests in §17.

| Path | Change |
| --- | --- |
| `entity/BodySweep.java` (new) | A body's flame box swept from its last sample to now: exact open-interval slab test with contact point, `MAX_SWEEP` 2, flier wing reach from `FragmentAnatomy`, `overlapsNow` for placement. |
| `entity/CombustionSystem.java` | `sampleFlames` at the start of each body's tick (asks `LiquidFireSystem` and `FireSystem`), `reportAttack` once per (person, bottle). |
| `entity/BodyCombustion.java` | Sample position (`sampled`, `sampleX/Y/Z`, `sampledAt`), reported-bottle memory (`firstReportOf`); both cleared by `clear()`. |
| `entity/CombustionConstants.java`, `CombustionSource.java` | `REPORTED_BOTTLES` 4; `CAMPFIRE` heat gain 1 /s. |
| `simulation/LiquidFireSystem.java`, `LiquidFireConstants.java` | `exposeContacts`, `nextSpillId()`, `liquidCanLie`; pool-lit block fires carry `byPlayer` and the spill id; `burnOccupants`, `standsIn`, the (NPC, spill) memory, `ENTITY_DPS_*`, `MAX_TRACKED_NPC_SPILLS` removed. |
| `simulation/FireSystem.java`, `FireConstants.java` | `exposeContacts` (burning blocks by face, fueled campfires, torches), `ignite(g, x, y, z, byPlayer, origin)`, `Burn` keeps its cell, owner and bottle in a list beside the map, spread inherits, `NO_ORIGIN`, `flameWouldTouch`, `standingFlameAhead`/`NO_FLAME`; flame-shape constants; `damageNear` and its constants removed. |
| `combat/ProjectileSystem.java` | A fire bomb breaking on a body ignites it (`DIRECT_HIT`, the bottle's spill id) before it shatters. |
| `entity/Entity.java`, `Npc.java`, `Creature.java`, `VoxelPhysics.java` | `keepsOutOfFlames` (people and animals), `flamesBlock` in `collidesAt`, `flameCell`/`inFlameCell`, walking out of a torch or campfire cell; `world()` accessor. |
| `ai/Steering.java`, `ai/Pathfinder.java` | Steering sidesteps a torch or campfire cell ahead; the pathfinder refuses those cells. |
| `PlayerBlockActions.java` | A torch or campfire cannot be placed with its flame in a body that can burn. |
| `world/World.java` | `getChunk`: 64-entry direct-mapped cache behind the one-entry cache (no boxing across an 8 × 8 window). |
| `src/test/.../entity/FlameSourceIgnitionTest.java` (new) | 46 tests (two parameterized over 13 bodies, 20 single). Real bottles broken on each of 13 living bodies (6 species from `CreatureType.values()`, 6 NPC families, Survival player; people throw at the player) then combustion and fire ticks: alight at once, the pool takes over at the contact rate, afterburn away from every flame. Creative player hit and standing in the pool while a neighbour and a deer burn. A bird six blocks up: no pool, burns to death in the real game tick under its own AI, the thrower's kill and meat. Water policy: animal in one cell, person to the hips (burns, faster drain), person under water. Crossing a one-cell patch at 12 blocks/s between two medium ticks for all 13 bodies; a dash between two fast ticks (hare, bird, deer, person) and the same dash a hair aside; a 4-block jump; liquid height (a bird a block over the pool against one skimming it); wing reach at a torch against a hare. The player walking/running through a pool, burning bushes (and past them a hand's width away), a campfire (brushing through vs standing in it) and a torch (walking through vs lingering), always burning on after leaving; a hare under a torch. Warmth, a held torch, lantern, trail marker, glow fungus, alarm bell, furnace, unfueled campfire: no heat. A buried log and a slab over a log: no heat; standing on a burning log and a burning crown at head height: catch. A pulled-up bush and a flooded patch; rain on a bush and a pool against a roofed bush; a campfire in the rain. Three overlapping flames in two orders: one owner, one contact's damage, the same point. A molotov-lit grass fire spread clear of the pool: the walker's burn is the thrower's attack (once per bottle) and kill; a wild fire's is nobody's. Four bottles on one resident: four attacks, none more over three seconds in the pools; Creative pays unseen. Walking round a campfire and a torch (no heat, no hop), the pathfinder's route, a shove at a campfire, one set down in it walks out. Flame placement per mode. Sampling at the edge of the loaded area loads no chunk. |
| `src/test/.../CombustionIntegrationTest.java` | The player's own throw command, the projectile and `Game.fastTick`/`mediumTick` over a buried burning log: exactly the contact damage after four medium ticks. |
| `src/test/.../entity/CombustionAllocationTest.java` | Every flame at its cap around a moving crowd, under the 4 KB allowance (measured 96). |
| `MolotovTest`, `CombatFireIntegrationTest`, `BlastLethalityTest`, `CreativeHazardsTest`, `SurvivalCreativeParityTest`, `RuntimeBoundsTest`, `SimulationSystemContractTest` | Adapted to contact on the fast tick (contract §17). |
| docs | contract §4.1 (new), §6, §10.1, §11, §15, §16, §17, §18; `ARCHITECTURE.md` molotov, rain and body-fire paragraphs; a superseded-in-part note on `COMBAT_LETHALITY_AND_MOLOTOV.md`. |

### Evidence of the decisions

- **Fires as obstacles for people and animals.** The probe above is the reason; each weaker
  rule left residents burning (walking through campfires at a camp guard's 1.8 blocks/s; a
  guard sleeping on the tile a barracks torch stands on, reached by direct steering; a guard
  pushed from a torch into a wall; a brute on a ledge treading in a torch's tip). Treating the
  cells as solid for bodies that keep out of fires is the one rule that holds whatever the AI
  chose, and it cannot be circumvented by a shove.
- **Per-bottle attacks.** `MolotovTest` already pinned "one attack per NPC per bottle" (−36 then
  −72 for two bottles on two residents); a per-episode rule would have given −36 twice.
- **Rain at contact time.** Without it a pool spilled into rain lit people for up to half a
  second before its first medium tick began to soak it (the rain test now covers that window).

### Limitations

- The flame box of a quadruped is its square entity box: a deer's head and tail reach past it
  along its length, unmodelled. A flier's wing envelope is a symmetric approximation.
- Heat is credited a whole tick for any contact in it (at most 0.05 s too much).
- A body standing in a campfire in open rain is put out every 1.5 s and relit by heat (§10.1).
- Fires lit by blasts, lightning and meteors are environmental.
- `Steering.flyToward` does not sidestep fires; collision still keeps birds out of their cells.
- A person hit by more than four distinct bottles can have the oldest count again.
- People and animals can no longer be knocked or shoved into a torch or campfire; they burn from
  one only if it is lit where they stand, and then walk out.
- `CreatureAI.panicFromFire` still flees only burning blocks; nothing flees a burning body's own
  flames yet (08).
- Wall-clock cost unmeasured; no native captures (nothing new is drawn).

### Handoff to milestone 08

- Every production flame now reaches `CombustionSystem`; a person or animal ignites for real
  from a bottle (direct or pool), a burning block, or a campfire or torch they were put in.
  Read `g.combustion.isBurning(e)`, `e.combustion.inContact()`, `hasExposure()` and
  `exposureX/Y/Z()` (the contact point of the flame that last touched the body) for panic and
  flight direction. AI runs after the combustion tick in the same fast tick.
- People and animals treat torch and campfire cells as solid (`Entity.keepsOutOfFlames`, applied
  in `VoxelPhysics.integrate`), and `Steering.moveToward` sidesteps them; a panicking body using
  `Steering` inherits both. Liquid pools and burning bushes are not obstacles: running through
  them is how panicking bodies catch again, which is intended.
- A burning person the player's flame lit has already been reported once for that bottle
  (`reportAttack`); panic must not report again.
- Keep green: `FlameSourceIgnitionTest`, `BodyCombustionTest`, `CombustionIntegrationTest`,
  `CombustionAllocationTest`, `MolotovTest`, `FireWeatherTest`, `CombatFireIntegrationTest`.

### Handoff to milestones 09–11

- 09: `BodyCombustion.clear()` also forgets the sample position (the next tick samples only
  where the body is) and the reported bottles; a body that leaves and comes back (settlement
  dormancy, load) is a new entity with neither. No collection outside the body holds a body.
- 10: sources draw as before; a body's fire is still only its red flash. `exposureX/Y/Z` is
  where a flame touched the body, if flames should start there.
- 11: the source matrix of contract §4.1 is covered here headlessly; native captures of bodies
  catching at each source belong with 10's presentation.
