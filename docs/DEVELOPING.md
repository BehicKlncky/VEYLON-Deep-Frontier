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
./gradlew test           # 266 deterministic tests, headless, ~80 s
./gradlew performanceTest # 4 opt-in wall-clock benchmarks; reference PC only
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
| `VEYLON_SMOKE=<seconds>` | Release smoke gate: save/load, fire, storm, a fortress approach, then a pass/fail report. Throws on failure |
| `VEYLON_SCENE=<name>` | Stage a deterministic benchmark scene (`day`, `pinefog`, `nightfire`, `ruin`, `toxic`, `ao_shadow`, `phase4`, `ashwolf`, `silhouette30`, `vfx_blood`, `vfx_mining`, `vfx_beacon`, `movement`, `inventory`, `ui_cycle`, `held_*`) |
| `VEYLON_SHOT="5,10"` | Capture screenshots at those elapsed seconds |
| `VEYLON_FRONTEND=<name>` | Pin a front-end screen (`options`, `loading`, `death`, `victory`, `glyphs`) |
| `VEYLON_RESOLUTION=1920x1080` | Framebuffer override |
| `VEYLON_UI_SCALE`, `VEYLON_VSYNC`, `VEYLON_FULLSCREEN`, `VEYLON_SHADOWS` | Settings overrides |
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
3. Add a `Recipe` in `CraftingSystem` and pick its `Station`.
4. Equipment also needs an `EquipSlot`; food needs a `FoodGroup`.

### A creature

1. Append to `Creature.CreatureType` (health, damage, speed, `predator`,
   `flying`, drops).
2. Build its model in `CreatureModels`.
3. Add spawn rules to `EntityManager` (biome, time of day, density cap).
4. Only touch `CreatureAI` if it needs behaviour the existing states don't
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
4. If it holds a `Random`, give it `setRandomSeed(long)` and seed it from
   `Game.reseedSimulation` with a salt no other system uses.
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

**Tests must not touch GL.** `Game` is constructible headless, but `renderer`,
`ui` and `audio` methods that allocate GPU or OpenAL resources will fail without
a context. Drive gameplay through the tick methods and the command seams
(`updateBowCommand`, `performPlayerAttack`, `completePlayerBlockBreak`,
`interactWithBlockAt`, …), which exist precisely so tests don't have to
synthesize GLFW input.

**Any new `Random` must be seeded from the world seed.**
`Game.reseedSimulation` seeds all 15 simulation generators, each with a
distinct salt so their streams stay independent. Add yours there.
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
