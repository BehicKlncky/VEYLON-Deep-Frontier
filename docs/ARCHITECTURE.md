# Architecture

How VEYLON: Deep Frontier is wired together — initialization, the per-frame
flow, how data moves through save/load and world edits, and where new features
plug in.

The **package layout**, **tick rates** and **save format contents** are in
[README.md](../README.md#architecture); this document covers control flow and
the contracts between systems rather than repeating that inventory.

---

## 1. System overview

`Game` is the composition root. It owns one instance of every subsystem as a
public final field and passes *itself* to each of them:

```
Main.main
  └── new Game().run()
        ├── engine     Window, Input, Camera, Renderer, UiRenderer, AudioManager, ParticleSystem
        ├── world      World (+ Chunk, WorldGenerator), and Game as its BlockListener
        ├── entity     Player, EntityManager, PlayerMovementSystem, PlayerTreatmentSystem
        ├── combat     ProjectileSystem, ExplosionSystem, WorldNoise
        ├── simulation SimulationScheduler + Time/Weather/Temperature/Water/Fire/Plant/
        │              Event/Season/ItemCondition
        ├── settlement SettlementManager (+ planner, builder, counterattack director)
        ├── ai         FactionSystem
        ├── ui         Hud + one instance per screen, EventLog
        └── collaborators split out of Game (all in com.veylon)
              QaHarness              opt-in benchmark/capture scaffolding
              PlayerCombatSystem     melee, bow, firearm, thrown, reload, durability
              PlayerBlockActions     hold-to-mine, break outcomes, placement
              WorldInteractions      F/RMB routing, NPCs, stations, beacon endgame
              PlayerConsumables      eat, drink, treat, equip
              CrateTransactionSystem crate transfers and theft attribution
              InteractPromptBuilder  read-only HUD interaction hint
              AmbienceSystem         ambient particles and audio mix
              SleepSystem            sleep eligibility, quality, night effects
```

Most systems take `Game g` as their first method parameter rather than holding a
reference. This is a deliberate trade: it keeps every system constructible with
a no-arg constructor (so tests can build one in isolation), at the cost of
giving each system reach over the whole object graph. The nine collaborators
above hold a `Game` field instead, because they are extractions *from* `Game`
and share its lifetime exactly.

Each of those keeps its public commands on `Game` as one-line delegates. That
is not ceremony: native input, the HUD, `EntityManager`, `CrateScreen` and the
gameplay tests all reach these through the `Game` instance, so moving the rules
out without moving the entry points keeps every existing caller working.

### Dependency direction

```
                    ┌─────────┐
                    │  Game   │  orchestrator; knows everything
                    └────┬────┘
       ┌─────────────────┼─────────────────┐
       ▼                 ▼                 ▼
   simulation         entity           settlement
       │                 │                 │
       └────────┬────────┴────────┬────────┘
                ▼                 ▼
              world             item
                │                 │
                └────────┬────────┘
                         ▼
                       util
```

`util` depends on nothing. `world` and `item` are leaf domains. `engine` and
`gfx` sit beside this tree and depend only on LWJGL plus `world`/`entity` for
the data they draw. Nothing below `Game` should import `Game` *for state* — the
`Game g` parameter is for calling back out (`g.log`, `g.audio`, cross-system
notifications), not for reaching sideways into an unrelated subsystem.

---

## 2. Initialization flow

`Game.run()`, in order:

1. `qa.applyResolutionOverride()` — reads `VEYLON_RESOLUTION`, `VEYLON_UI_SCALE`,
   `VEYLON_VSYNC`, `VEYLON_FULLSCREEN`, `VEYLON_SHADOWS`. No-ops when unset.
2. `window.setInitialWindowedSize(...)` then `window.create(...)` — creates the
   GLFW window and the OpenGL 3.3 core context.
3. `renderer.init()` — compiles shaders, builds procedural textures and the
   material/icon atlases. **Requires a live GL context.**
4. `window.setVsync(...)` / `setFullscreen(...)`, `ui.init()`, `audio.init()`.
5. `sessionSeed = qa.configuredSeed()` — honours `VEYLON_SEED`, else random.
6. Branch on environment:
   - **Automated** (`VEYLON_SMOKE` / `VEYLON_SCENE` / `VEYLON_SHOT` set):
     `newWorld(seed, true)`, then `qa.applyBenchmarkScene(scene)`, then straight
     into `PLAYING` with the cursor captured.
   - **Normal**: `appState = TITLE`; the world is not created until the player
     picks New Game or Load.
7. Enter the main loop until `window.shouldClose()`.
8. On exit: `renderer.delete()`, `ui.delete()`, `audio.shutdown()`,
   `window.destroy()`. A smoke run throws if any release gate failed.

### World creation

`newWorld(seed, fresh, generatorVersion)` is the single path that produces a
playable world, used by New Game, by save loading and by the QA harness. It
**resets before it constructs**, in this order:

1. Release GPU meshes of the outgoing world (`releaseWorldMeshes`).
2. Reset every cross-world queue: scheduler, fire, water, events, plants, item
   conditions, noise, projectiles, explosions, settlements.
3. `reseedSimulation(seed)` — seeds all 15 simulation generators, each with a
   distinct salt so their streams stay independent. Without this, a second
   world in the same process inherits RNG state from the first, which is what
   made a settlement test intermittently fail. Presentation-only randomness
   (`AudioManager`, `NpcScreen`) is deliberately excluded.
4. Construct `World` and `Player`; register `Game` as `world.listener`.
5. Clear entities and the event log; reset faction standing and the clock.
6. Clear player action state: UI mode, mining, bow draw, reload, sleep, theft
   event bookkeeping.
7. Generate chunks around spawn synchronously, find dry land, place the camp.

`GameLoopIntegrationTest.newWorldClearsSimulationQueuesAndPlayerActionState`
pins steps 2–6. If you add a system with cross-world state, add its `reset()`
here **and** an assertion there.

---

## 3. Runtime flow (per frame)

```
while (!window.shouldClose())
  ├─ dt = min(0.1, now - last)          frame delta is clamped
  ├─ frameProfiler.record(rawDt)
  ├─ qa.recordFortressApproach(...)     inert outside a smoke run
  ├─ window.poll()                      GLFW events -> Input edge state
  ├─ qa.updateShowcases(elapsed)        inert unless a scene is selected
  ├─ frame(dt)                          ← everything below
  ├─ qa.sampleRenderStats()
  ├─ screenshot capture if scheduled
  ├─ window.swap(); input.endFrame()    endFrame clears per-frame edges
  └─ FPS accounting, smoke phase machine
```

`frame(dt)`:

1. **Frontend short-circuit** — in `TITLE`, `TITLE_OPTIONS` or `LOADING`, call
   `frameFrontend(dt)` and return. No world exists in these states.
2. **State machine** — `DEATH`/`VICTORY` handle Esc/Enter; otherwise
   `handleGlobalKeys()` (inventory, crafting, map, pause, debug overlays).
3. `simulate = appState == PLAYING && uiMode not PAUSE/OPTIONS && !simPaused`.
4. Animation timers decay; sleep advances if sleeping.
5. **Player input** — when no screen is open and `simulate`: `updateMouseLook`,
   `updateMovement`, `updateActions`. `updateActions` raycasts the target,
   refreshes the prompt, and routes to `PlayerInteractionSystem`, which calls
   back through `interactionCommands` into combat/mining/interact.
6. **Simulation** — when `simulate`: `time.advance`, then
   `scheduler.update(dt, this)` which drives the three tick buckets, then
   per-frame systems (particles, projectiles, explosion fuses, noise decay,
   ambient emitters).
7. **Camera and audio** follow the player eye.
8. **Streaming** — `world.ensureChunks(...)` around the player, then
   `renderer.buildDirtyMeshes(...)` on a per-frame budget.
9. **Render** — `renderer.render(...)`, then the UI pass: HUD, the active
   screen, debug overlays, death/victory overlay, `ui.end()`.

### Tick buckets

`SimulationScheduler` owns three accumulators and drains them in order:

| Bucket | Period | Drives |
|---|---|---|
| fast | 1/20 s | player needs, entity AI and movement, settlement fast tick |
| medium | 1/2 s | weather, temperature, water, fire, shelter, smoke, ambience |
| slow | 10 s | plants, events, faction, spawning, item spoilage, settlements |

Two invariants, both pinned by `GameLoopIntegrationTest`:

- **Fast drains first.** Coarse ticks never run before a fast tick has advanced
  state in the same update, so weather and spoilage always read current needs.
- **Stalls clamp, they don't spiral.** `frameDt` is clamped to 0.25 s and fast
  catch-up is capped at 10 iterations. A 30-second stall (GC, window drag,
  breakpoint) advances the world 0.25 s — below the medium threshold — rather
  than replaying 600 ticks or teleporting weather forward.

---

## 4. Data flow

### World edits

Every block change funnels through `World.setBlock(x, y, z, type, notify)`.
When `notify` is true it calls `world.listener.onBlockChanged(...)`, which is
`Game`. That hook is how lighting, meshing, fire, water and settlement systems
learn about a change without polling. **Bulk edits pass `notify = false` and
fix up heightmaps and lights once at the end** — see the QA staging code and
`ExplosionSystem` for the pattern.

```
player action ─┐
explosion ─────┼─→ World.setBlock ─→ chunk data + heightmap + dirty flag
fire spread ───┤                  └─→ Game.onBlockChanged ─→ dependent systems
QA staging ────┘                       (skipped when notify == false)
```

### Save / load

`SaveSystem.save(Game)` walks the live object graph and writes binary v3;
`load` reconstructs it. Load **goes through `newWorld` first**, so a load always
inherits the full reset above rather than merging into a live world
(`GameLoopIntegrationTest.loadingOverALiveWorldReplacesItRatherThanMerging`).

Structure is *re-derived*, not stored: settlement layouts and terrain come back
from the seed plus the generator version. Only deltas and dynamic state are
serialized. This is why `world.generatorVersion` is part of the save — pinning
it is what lets v2 saves load with identical terrain instead of silently
regenerating under a newer generator.

Transient state is deliberately **not** serialized: an in-flight reload is
cancelled on load, and bow draw resets. If you add state, decide explicitly
which side of that line it sits on.

### Entity updates

`EntityManager` owns the creature/NPC/carcass/track lists and ticks them. It
calls back into `Game` for consequences that cross system boundaries —
`onCreatureKilled`, `onRaiderKilled`, `playerHasKnife` — which `Game` forwards
to `PlayerCombatSystem`.

---

## 5. Thread model

**Single-threaded.** Everything — input, simulation, meshing, rendering, audio
submission — runs on the main thread inside `Game.run()`.

This is a hard constraint of the GLFW/OpenGL setup: the GL context is current on
the thread that created the window, and GLFW event polling must happen on the
main thread. Chunk generation and meshing are kept responsive by *budgeting*
(`ensureChunks(..., budget)`, `buildDirtyMeshes(..., budget)`) rather than by
moving work off-thread.

Consequences for contributors:

- No synchronization exists anywhere. Adding a background thread that touches
  world, entity or GL state will corrupt it.
- Anything slow must take a budget parameter and spread across frames.
- Tests construct `Game` directly and never start the loop, so they run
  headless — but they must not touch `renderer`/`ui`/`audio` methods that
  require a GL context.

---

## 6. Extension points

| To add… | Touch | Also update |
|---|---|---|
| a block | `BlockType` (append only) | `MaterialRegistry`, `ChunkMesher` if a new shape, drops/hardness on the enum |
| an item | `ItemType` (append only) + `ItemProps` | `IconAtlas`, a `Recipe` in `CraftingSystem` |
| a recipe | `CraftingSystem` | the `Station` it needs |
| a creature | `Creature.CreatureType` | `CreatureModels`, spawn rules in `EntityManager`, `CreatureAI` if behaviour differs |
| an NPC role | `NpcArchetype` | `SettledNpcAI` job schedule, `NpcModels` |
| a biome | `Biome` | `WorldGenerator` selection, surface/subsurface blocks, spawn tables |
| a settlement type | `SettlementType` | `SettlementPlanner` placement, `SettlementBuilder` layout |
| an affliction | `Affliction` | `PlayerConstants` damage rate, `Player.tickAfflictions`, `PlayerTreatmentSystem` cure |
| a weather state | `WeatherSystem.Weather` | `SkyRenderer`, `Environment`, `TemperatureSystem` |
| a simulation system | new class in `simulation/` | a tick call in `Game.fastTick`/`mediumTick`/`slowTick`, a `reset()` call in `newWorld`, save/load if it holds state |

**Enum ordinals are part of the save format.** `SerializedEnumOrderTest` guards
this: append new constants at the end, never reorder or delete. Reordering
`ItemType` silently turns every saved iron pickaxe into something else.

### Where gameplay rules live now

After the 0.4.0 debt work, `Game` is orchestration only — the loop, the app
state machine, world setup, input routing and tick wiring. It went from 4,381
lines to 1,462. Every gameplay rule that used to be interleaved with it lives
in a named collaborator:

| Class | Owns |
|---|---|
| `PlayerCombatSystem` | all damage the player deals, durability, kill reputation |
| `PlayerBlockActions` | hold-to-mine, break drops/spill/vandalism, placement |
| `WorldInteractions` | F and RMB routing, NPC talk/rescue, stations, beacon endgame |
| `PlayerConsumables` | eat, drink, treat wounds, equip gear |
| `CrateTransactionSystem` | crate transfers and theft attribution |
| `InteractPromptBuilder` | the `[F] …` hint (pure read — cannot change what F does) |
| `AmbienceSystem` | ambient particles and the audio mix |
| `SleepSystem` | sleep eligibility, quality scoring, night effects |
| `QaHarness` | benchmark scenes, showcases, release smoke gate |

Tuning values live in `PlayerConstants` plus one constants class per
simulation system — `TimeConstants`, `TemperatureConstants`, `WaterConstants`,
`WeatherConstants`, `FireConstants`, `PlantConstants`, `EventConstants` — and
as named constants inside each collaborator above.

Two conventions worth knowing when you edit them:

- **Per-state values belong on the enum, not in a switch.**
  `WeatherSystem.Weather` carries its own intensity, light, grayness, fog
  distances and temperature offset. That replaced six parallel switches, and it
  means the compiler now rejects a new weather state that forgets a value.
- **Constant extraction must not regroup float arithmetic.** IEEE754
  multiplication is not associative, so hoisting a shared subexpression out of
  two probability thresholds can shift them by an ULP. `PlantSystem` carries a
  comment where this bit.
