# Developing VEYLON: Deep Frontier

Practical guide for working on the codebase. Read
[ARCHITECTURE.md](ARCHITECTURE.md) first if you need the control flow; this
document is about getting things done.

Setup requirements (JDK 25, OpenGL 3.3, the proxy workaround) are in
[README.md](../README.md#requirements).

---

## Running

```bash
./gradlew run            # play the game
./gradlew test           # 302 deterministic tests, headless, ~90 s
./gradlew performanceTest # 5 wall-clock benchmarks + the per-system tick
                          # profile; reference PC only
./gradlew build          # compile + test
./gradlew fatJar         # self-contained JAR in build/libs/
```

On Windows use `.\gradlew.bat`. Tests need no display or GPU — they construct
`Game` directly and never enter the render loop.

### Running a single test

```bash
./gradlew test --tests "com.veylon.GameLoopIntegrationTest"
./gradlew test --tests "*.newWorldClears*"
```

### QA and capture runs

The game reads `VEYLON_*` environment variables for reproducible, unattended
runs. All are inert when unset. Everything below is handled by `QaHarness`;
`VEYLON_AO` (renderer ambient-occlusion toggle) and `VEYLON_GL_DIAGNOSTICS`
(GL debug output) are read by `Renderer` and `Window` instead.

| Variable | Effect |
|---|---|
| `VEYLON_SEED` | Fixed world seed (a non-numeric value is hashed) |
| `VEYLON_GAME_MODE=survival\|creative` | Initial mode for automated world runs only; absent means Survival. Invalid values fail explicitly. Ignored for normal title sessions and frontend-only captures. |
| `VEYLON_SMOKE=<seconds>` | Release smoke gate: save/load, fire, storm, a fortress approach, then a pass/fail report. Throws on failure |
| `VEYLON_SCENE=<name>` | Stage a deterministic benchmark scene (`day`, `pinefog`, `nightfire`, `ruin`, `toxic`, `ao_shadow`, `phase4`, `ashwolf`, `silhouette30`, `vfx_blood`, `vfx_mining`, `vfx_beacon`, `death_ragdoll_showcase`, `death_ragdoll_sequence`, `movement`, `inventory`, `ui_cycle`, `held_*`, and `creative_flight` with `VEYLON_GAME_MODE=creative`, which flies east at Shift speed and prints a `[flight]` streaming report at 29 s) |
| `VEYLON_SHOT="5,10"` | Capture screenshots at those elapsed seconds |
| `VEYLON_FRONTEND=<name>` | Pin a front-end screen (`options`, `audio`, `loading`, `death`, `victory`, `glyphs`, `newworld`). `newworld-save` shows the replace-save notice without writing a save. `pause` and `gamemode` stage a Survival world with the pause menu or the mode confirmation open; `pause-creative`, `gamemode-creative` and `victory-creative` stage a Creative world; `catalog`, `catalog-tools`, `catalog-search` (query "iron") and `catalog-inventory` open the Creative catalog; `worldcontrols` and `worldcontrols-held` open the Creative world controls, the second with dusk, a frozen clock, a locked storm and paused spawning already applied |
| `VEYLON_RESOLUTION=1920x1080` | Framebuffer override |
| `VEYLON_UI_SCALE`, `VEYLON_VSYNC`, `VEYLON_FULLSCREEN`, `VEYLON_SHADOWS` | Settings overrides |
| `VEYLON_AUDIO_QA=1` | Add three positional audio reports per second to the smoke, without gameplay effects |
| `VEYLON_NO_EFX=1` | Force the documented dry audio fallback for device QA |
| `VEYLON_MIN_FPS` | Stricter FPS gate layered over the mandatory 60 |
| `VEYLON_CAPTURE_TAG` | Filename prefix for captures |
| `VEYLON_QA_SET_OPTIONS="fov=85,bloom=false"` | Mutate and save settings through the real APPLY path, to prove persistence across relaunch |

Example:

```bash
VEYLON_SEED=20260716 VEYLON_SCENE=day VEYLON_SHOT=6 ./gradlew run
```

---

## How to add…

### A block

1. Append a constant to `BlockType` — **never insert or reorder**, ordinals are
   the save format. Set hardness, `requiresTool`, `preferredTool`, `drop`,
   `dropCount`.
2. Give it a material in `MaterialRegistry` (texture, tint, light emission).
3. If it is not a full cube, handle its shape in `ChunkMesher`.
4. If breaking it should have side effects, add a case to the right helper in
   `PlayerBlockActions`: `spillContainerContents` for stored contents,
   `awardDrops` for yields, `applyVandalismConsequences` for reputation.
5. If it is interactable, add a prompt case in `InteractPromptBuilder` and a
   handler case in `WorldInteractions.rightClick` /
   `WorldInteractions.interactWithTargetBlock`.

### An item

1. Append to `ItemType` (same ordinal rule) and fill in `ItemProps`.
2. Add an icon in `IconAtlas` — the smoke gate fails if any item lacks one.
   A placeable item gets a block projection for free from `MaterialRegistry`.
3. Add a `Recipe` in `CraftingSystem` and pick its `Station`.
4. Equipment also needs an `EquipSlot`; food needs a `FoodGroup`.
5. **Give it a Creative catalog category.** `CreativeCatalog.classify` decides
   membership in one place and `CreativeCatalogTest` fails if any item lands in
   none or in two. A Creative-only block form also belongs in `CreativePalette`,
   whose test fails if a `BlockType` is neither buildable nor given a written
   exclusion reason.

### A new way to hurt the player

Route it through `Player.hurt`, `Player.hurtPhysical` or `Player.addAffliction`
rather than writing `health` directly. Those three are where Creative
invulnerability is enforced, and a direct subtraction bypasses it silently.
`tickNeeds` is the exception that proves the rule: it subtracted health in
several helpers and each one needed its own gate.

### A new way for AI to notice the player

Ask `Player.isPerceivableByAi()` before the detection, and gate the player-sourced
`WorldNoise.emit` or scent write the same way. It is the single predicate behind
R11, so a new sight cone, hearing check or targeting pass that skips it will make
a Creative player visible again. Consequences that do not depend on someone
noticing — reputation, ownership, theft, vandalism, bounty — stay ungated.

### A new way to consume an item

Decide which side of D4 it is on. Using an item up *for its own effect* (placing,
firing, throwing, eating, drinking, treating, wearing out) checks
`PlayerAbilities.unlimitedItems()` at the call site and skips the `shrink`,
`remove` or `consumeDurability`. *Transforming, trading or moving* it (crafting,
cooking, fuelling, drying, smelting, trade, gifts, quest delivery, crate
transfers, equipping) keeps the Survival rule in both modes and needs no gate.

### A creature

1. Append to `Creature.CreatureType` (health, damage, speed, `predator`,
   `flying`, drops).
2. Build its model in `CreatureModels`.
3. Map its bones in `BodySkeleton.buildCreature` so a dead one ragdolls instead
   of dropping as a single rigid block. An entry names real `ModelPart` names,
   the pivot each hangs from **in the model root frame**, the axis it is authored
   along, and how far out along that axis its handle sits — all readable straight
   off the model builder. A type with no entry falls through to
   `BodySkeleton.rigid` and tumbles as one piece, which is a legitimate choice
   for something tiny rather than an oversight. Only bones authored along Y or Z
   can be aimed — the two-angle parameterisation cannot reach a sideways rest
   axis — which is why the skitterwing's wings stay at rest.
4. Add spawn rules to `EntityManager` (biome, time of day, density cap).
5. Only touch `CreatureAI` if it needs behaviour the existing states don't
   cover.

### An NPC role

1. Append to `NpcArchetype` (loadout, job, dialogue).
2. Add its daily schedule to `SettledNpcAI`.
3. Add visual distinction in `NpcModels`.
4. If it should be placed by settlement generation, update
   `SettlementBuilder`'s resident roster.

### A biome

1. Append to `Biome` with surface/subsurface blocks and display name.
2. Add selection to `WorldGenerator` (temperature/moisture thresholds).
3. Add its vegetation pass and creature spawn table.
4. Check `SettlementBuilder` adapts — layouts are biome-aware.

### A settlement type

1. Append to `SettlementType`.
2. Add placement rules to `SettlementPlanner` (`plan(seed, gen, rx, rz)` must
   stay a pure function of its arguments — see the invariant below).
3. Add a layout to `SettlementBuilder`.
4. Add its resident roster and defensive behaviour to `SettlementManager`.

### An affliction

1. Append to `Affliction` with display name.
2. Add its damage rate to `PlayerConstants` and a case in
   `Player.tickAfflictions`.
3. Add a cure path in `PlayerTreatmentSystem` and the item that applies it.

### A world event

1. Append to `EventSystem.EventType`.
2. Add a roll bound and a duration to `EventConstants`. The bounds are
   cumulative and tested in ascending order, so inserting one means shifting
   every bound after it — only the gaps between them are meaningful.
3. Add an `EventDefinition` to `EventSystem.DEFINITIONS`, in bound order. Keep
   eligibility in its side-effect-free precondition and mutations in its effect;
   a rejected definition deliberately hands the same roll to the next rung.
4. Update the hand-maintained rung table in `EventSystem.slowTick`'s JavaDoc and
   extend `EventRollLadderTest`'s characterization/invariant coverage.
5. Add whatever modifier query other systems need (`growthMul`, `thirstMul`,
   `wolfCapBonus`, …) rather than having them check `isActive` directly.

### A simulation system

1. Add a class in `simulation/`, with a `reset()` and a constants class beside it.
2. Implement `FastTickSystem`, `MediumTickSystem` or `SlowTickSystem` — pick the
   *slowest* cadence that still feels responsive. A stateful system without a
   scheduler bucket implements the base `SimulationSystem`.
3. Call its tick from the matching `Game` bucket and its `reset()` from
   `Game.newWorld`. Add it to `SimulationSystemContractTest`, including a
   behavior-level assertion for every per-world cache or counter it clears.
4. If it holds a `Random`, keep it on an instance reachable from `Game`, give
   that owner a seed hook and seed it from `WorldBootstrap` with a salt no other
   stream uses. Never put outcome randomness in a static field.
5. If it holds state that must survive a save, add a v3 extension section in
   `SaveSystem` — do not change the base v3 layout.

---

## Common pitfalls

**Reordering an enum breaks every save.** `BlockType`, `ItemType`, `Affliction`,
`Biome`, `SettlementType`, `NpcArchetype` and the creature/NPC state enums are
persisted by ordinal. Append only. `SerializedEnumOrderTest` will catch you.

**Bulk block edits with `notify = true` are quadratic.** Each notified
`setBlock` triggers listener work. For anything larger than a few blocks, pass
`notify = false` and fix up heightmaps and lights once at the end.

**Settlement planning must stay order-independent.**
`SettlementPlanner.plan(seed, generator, rx, rz)` must return the same result
regardless of which regions were planned before it. Caching intermediate state
across calls will make worlds diverge depending on the player's walking route.

**Never start a thread.** The renderer, world and entity state have no
synchronization and the GL context is bound to the main thread. Budget the work
across frames instead — see `ensureChunks(..., budget)`.

**Case-insensitive text needs `Locale.ROOT`.** The host may run a Turkish default
locale, where `"IRON".toLowerCase()` is `"ıron"` and the Creative catalog stops
finding iron. Every `toLowerCase`/`toUpperCase` in comparison code passes
`Locale.ROOT`; `CreativeCatalogTest` sets a Turkish default and restores it.

**`World.getBlock` returns `AIR` for unloaded chunks.** That is not "there is
nothing there", it is "nobody has generated it yet". Anything that moves or
places by block lookup — Creative flight in particular — must check that the
chunk column is loaded first, or it will happily fly into ungenerated space.

**Tests must not touch GL.** `Game` is constructible headless, but `renderer`,
`ui` and `audio` methods that allocate GPU or OpenAL resources will fail without
a context. Drive gameplay through the tick methods and the command seams
(`updateBowCommand`, `performPlayerAttack`, `completePlayerBlockBreak`,
`interactWithBlockAt`, …), which exist precisely so tests don't have to
synthesize GLFW input.

**Any new `Random` must be seeded from the world seed.**
`WorldBootstrap` seeds every simulation and AI stream with a distinct salt so
their streams stay independent. Add yours there.
`WorldSeedDeterminismTest` reflects over every `Random` reachable from `Game`
and fails with your field's name if you forget — that test exists because ten
generators were previously missed, which made a settlement test intermittently
fail depending on what ran before it. Presentation-only randomness
(`AudioManager`, `NpcScreen`) is exempt and listed in that test.

**Adding transient player state?** Decide explicitly whether it survives a save.
Reload progress and bow draw deliberately do not.

---

## Testing conventions

- The default `test` suite is deterministic and headless; a fixed seed goes in
  the test.
- Wall-clock budgets are tagged `performance`, run only through
  `performanceTest`, and are calibrated for the machine recorded in
  `docs/PERFORMANCE_BENCHMARKS.md`. Do not add them to portable release
  packaging or re-baseline them on an arbitrary CI runner.
- Build an isolated arena rather than relying on generated terrain when the test
  is about mechanics — see `CombatSystemsTest.setUp`, which flattens four chunks
  to stone at y=40.
- Drive production entry points, not reimplementations of them. If a test needs
  a seam that doesn't exist, add the seam to the production class rather than
  making the test reach into private state.
- Name the test after the behaviour it pins, not the method it calls.
- Assertion messages should say what invariant broke.
- A Creative behaviour is pinned twice: the Creative case and its Survival twin
  from the same fixture. `SurvivalCreativeParityTest` and the `Creative*Test`
  classes are built in pairs for exactly that reason — a gate that accidentally
  fires in Survival is the failure mode worth catching.
- The suite is 704 tests across 105 classes at 0.7.2.

---

## Known rough edges

Tracked honestly so nobody rediscovers them:

- **The event probability table is documented twice.** `EventSystem.DEFINITIONS`
  is executable authority, while the `slowTick` JavaDoc table is maintained by
  hand for readers. `EventRollLadderTest` protects the executable ordering and
  distribution, but cannot prove that the prose table was updated.

- **Performance budgets are single-machine.** `performanceTest` catches large
  simulation/save regressions on the recorded reference PC, not portable timing
  differences. Rendering still requires the fixed-seed OpenGL smoke gate.

- **Cadence interfaces are contracts, not dependency injection.** Systems still
  receive `Game` for cross-system coordination. Introducing a service locator
  or one facade per system would add indirection without narrowing that
  dependency and remains intentionally out of scope.

Audio preferences use `veylon_audio.properties` in the same AppPaths directory as graphics preferences. Use V from the title or pause menu, or the title AUDIO button. `VEYLON_FRONTEND=audio` with `VEYLON_SHOT=2` captures the audio editor without a world; Apply saves and Back cancels live edits.

## Physical rain QA and contributor rules (0.7.1)

Use `VEYLON_SCENE=physical_rain`, `VEYLON_GAME_MODE=creative`, `VEYLON_SEED=711`
and `VEYLON_SHOT=4,9,14,19,24,29,34,39,44,49,54,59` for the 60-second native
sequence. Five-second phases cover ordinary rain, storm, shelter entrance,
elevated roof view, uneven terrain/foliage/water, moving at running speed, rapid
turning, 25% density, full density, clear-to-rain, stopping rain and snow. Captures
are wall-clock scheduled, so particle pixels vary with frame cadence; the stage,
seed and phase inputs are repeatable. Headless tests use fixed time steps.

Run `gradlew test --tests '*PhysicalRainTest'` and the reference-machine
`gradlew performanceTest --tests '*RainPerformanceTest'` during development.
Keep new environmental physics in the packed particle arrays, use world-aware
updates in gameplay/QA, and keep all hot-loop scratch bounded. An impact must
come from loaded voxel traversal, never a predicted heightmap splash. Keep
cosmetic randomness out of simulation RNG, reserve shared particle space, and
update instance-packing tests whenever GPU attributes change. Cached columns
must follow World lifetime rules; never cache voxel contents across edits.

For a close inspection of one causal impact, use `VEYLON_SCENE=rain_impact` with
`VEYLON_SHOT=1,3,5,7`. It recreates the same seeded drop and freezes four physics
snapshots: falling, contact, ballistic scatter, expired. This scene deliberately
uses fixed simulation steps each frame so the short spray can be inspected in
native captures; the ordinary rain sequence exercises live continuous updates.

## Death ragdoll QA and contributor rules (0.7.2)

Use `VEYLON_SCENE=death_ragdoll_showcase`, `VEYLON_SEED=20260918` and
`VEYLON_SHOT=1,2,3,5,8` for the staged meadow: four animals and one settler
killed from known directions on one frame, then frozen at 0, 0.25, 0.8, 2.0 and
5.0 seconds of solver time. `death_ragdoll_sequence` does the same to a single
wolf, side-on and close, for reading one body frame by frame. Both keep the
simulation paused and advance the solver in fixed steps tied to elapsed
wall-clock seconds, the way `rain_impact` does, because a two-second tumble is
too short to inspect at live frame cadence and a wall-clock screenshot would
otherwise land somewhere different every run.

Run `gradlew test --tests '*Ragdoll*' --tests '*DeathRagdoll*' --tests
'*OverloadedDeadFlag*' --tests '*BloodBurst*' --tests '*BodiesSection*'` while
working on this.

Contributor rules this feature adds:

- **Gate anything that happens on death on `health <= 0`, never on
  `Entity.dead`.** The flag also means "remove me from the world" at seven sites
  where health is untouched — expired traders, fading raiders, stood-down war
  parties, routed survivors. `EntityManager.reallyDied` is the predicate;
  `OverloadedDeadFlagTest` drives the real AI paths that set the flag and fails
  if a despawn starts leaving a body.
- **Consequences fire on the tick of death; only the object left behind waits.**
  Reputation, loot, mission failure and resident bookkeeping that lagged a second
  behind the kill would read as a bug.
- **A body is positioned by its torso and drawn through its own `BodyPose`.**
  Orientation belongs on the draw transform, not the model root, or a roll
  becomes a rotation about world Z instead of the body's spine. Models stay
  shared singletons — never clone one per body.
- **Anything that poses the humanoid must run `Animator.applyAppearance`.**
  `resetPose` makes all 22 archetype accessories visible again, so a body posed
  without it wears a trader's pack, a raider's hood and a leader's mantle at once.
- **Keep the per-frame solver allocation-free.** `RagdollAllocationTest` holds it
  at zero with a 4 KB allowance, in the default suite rather than the
  performance one, because what it pins is a property of the code.
