# Architecture

How VEYLON: Deep Frontier is wired together — initialization, the per-frame
flow, how data moves through save/load and world edits, and where new features
plug in.

The **package layout**, **tick rates** and **save format contents** are in
[README.md](../README.md#architecture); this document covers control flow and
the contracts between systems rather than repeating that inventory.

---

## 1. System overview

`Game` is the composition root. It owns one instance of every subsystem and
passes *itself* to each of them. Runtime systems used across packages remain
public; UI screens and debug toggles that never leave `com.veylon` are
package-private:

```
Main.main
  └── new Game().run()
        ├── engine     Window, Input, Camera, Renderer, UiRenderer, AudioManager, ParticleSystem
        ├── world      World (+ Chunk, WorldGenerator), and Game as its BlockListener
        ├── entity     Player, EntityManager, PlayerMovementSystem, PlayerTreatmentSystem,
        │              RagdollSystem, BodyFragmentSystem
        ├── combat     ProjectileSystem, ExplosionSystem, WorldNoise
        ├── simulation SimulationScheduler + cadence interfaces +
        │              Time/Weather/Temperature/Water/Fire/LiquidFire/Plant/
        │              Event definitions/Season/ItemCondition
        ├── settlement SettlementManager (+ dormant simulation, planner, builder,
        │              counterattack director)
        ├── ai         FactionSystem
        ├── ui         Hud + one instance per screen, EventLog
        └── collaborators split out of Game (all in com.veylon)
              QaHarness              opt-in benchmark/capture scaffolding
              AutomatedRunDriver     VEYLON_* sessions and the release smoke gate
              WorldBootstrap         reset, reseed, construct, spawn, camp
              FrontendController     title / options / loading, and transitions
              HotkeyRouter           global keys, screens, quick save/load
              PlayerEnvironmentSystem shelter, smoke, vents, discovery, stations
              PlayerCombatSystem     melee, bow, firearm, thrown, reload, durability
              PlayerBlockActions     hold-to-mine, break outcomes, placement
              WorldInteractions      F/RMB routing, NPCs, stations, beacon endgame
              PlayerConsumables      eat, drink, treat, equip
              CrateTransactionSystem crate transfers and theft attribution
              InteractPromptBuilder  read-only HUD interaction hint
              AmbienceSystem         ambient particles and audio mix, and (BodyFireEffects)
                                     what burning bodies give off and how they sound
              BodyFireQaScene        opt-in burning-body capture scenes
              AudioSceneState        bounded read-only environment/music observations
              FireAudioLocator       nearest loaded audible fire
              SleepSystem            sleep eligibility, quality, night effects
```

Most systems take `Game g` as their first method parameter rather than holding a
reference. This is a deliberate trade: it keeps every system constructible with
a no-arg constructor (so tests can build one in isolation), at the cost of
giving each system reach over the whole object graph. The collaborators above
hold a `Game` field instead, because they are extractions *from* `Game` and
share its lifetime exactly.

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
playable world, used by New Game, by save loading and by the QA harness. The
rules live in `WorldBootstrap`; `Game.newWorld` is the entry point every caller
still uses. It **resets before it constructs**, in this order:

1. Release GPU meshes of the outgoing world (`releaseWorldMeshes`).
2. Reset every cross-world system: scheduler, audio, time, weather, temperature, fire,
   liquid fire, water, events, plants, item conditions, noise, projectiles, explosions,
   settlements, ragdolls, body fragments, body combustion (its tallies; each body's fire
   lives on the body and goes with it) and burn residues (the flames remains died with).
3. `reseedSimulation(seed)` — seeds every simulation and player-outcome
   generator, eighteen of them, each with a distinct salt so their streams stay
   independent (the entity manager's AI call seeds four decision streams:
   creatures, camp NPCs, settled NPCs and the fire panic). Without this, a second
   world in the same process inherits RNG state from the first, which is what
   made a settlement test intermittently fail. Presentation-only randomness
   (`AudioManager`, `NpcScreen`) is deliberately excluded.
4. Construct `World`, bind it to audio occlusion, then construct `Player`; register `Game` as `world.listener`.
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

1. **Frontend short-circuit** — in `TITLE`, `TITLE_OPTIONS`, `TITLE_AUDIO_OPTIONS` or `LOADING`, call
   `frameFrontend(dt)` and return. No world exists in these states.
2. **State machine** — `DEATH`/`VICTORY` handle Esc/Enter; otherwise
   `handleGlobalKeys()` (inventory, crafting, map, pause, debug overlays).
3. `simulate = simulates()`: `appState == PLAYING`, no pausing screen (`UiMode.pausesSimulation`:
   the pause menu and the options, audio, game-mode and world-controls screens opened from it)
   and not `simPaused`. The inventory, crafting, map, crate, catalog and NPC screens do not pause.
4. Animation timers decay; sleep advances if sleeping.
5. **Player input** — when no screen is open and `simulate`: `updateMouseLook`,
   `updateMovement`, `updateActions`. `updateActions` raycasts the target,
   refreshes the prompt, and routes to `PlayerInteractionSystem`, which calls
   back through `interactionCommands` into combat/mining/interact.
6. **Simulation** — when `simulate`, `advanceWorld(dt)`: `time.advance`, then
   `scheduler.update(dt, this)` which drives the three tick buckets, then
   per-frame systems (particles, projectiles, ragdolls, body fragments, burn residues,
   explosion fuses, noise decay, ambient emitters). Nothing else advances the world, so a
   paused frame burns, panics and settles nothing.
7. **Camera and audio** follow the player eye; set the listener before `audio.update(dt)`.
8. **Streaming** — `world.ensureChunks(...)` around the player, then
   `renderer.buildDirtyMeshes(...)` on a per-frame budget.
9. **Render** — `renderer.render(...)`, then the UI pass: HUD, the active
   screen, debug overlays, death/victory overlay, `ui.end()`.

### Tick buckets

`SimulationScheduler` owns three accumulators and drains them in order:

| Bucket | Period | Drives |
|---|---|---|
| fast | 1/20 s | player needs, living bodies on fire, entity AI and movement, settlement fast tick |
| medium | 1/2 s | weather, temperature, water, fire, liquid fire, shelter, smoke, ambience |
| slow | 10 s | plants, events, faction, spawning, item spoilage, settlements, world detritus (carcasses, corpses, body fragments) |

Stateful systems implement `SimulationSystem`; scheduled systems additionally
declare `FastTickSystem`, `MediumTickSystem` or `SlowTickSystem`. These interfaces
pin cadence and the cross-world `reset()` contract. They do not hide the existing
`Game` coordination parameter.

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
cancelled on load, and bow draw resets. Combat and fire add more of it. An
NPC's torso wound count and the shot id that last wounded it, the blast
record a lethal explosion leaves on the body it killed, a living body's
fire (`Entity.combustion`) and a burning person's or animal's panic
(`Npc.panic`, `Creature.panic`) live on the entity and die with it — a person who
leaves the world, through a save, a load or a settlement going dormant, comes
back unwounded, not burning and calm at their stored health. Saving leaves a body
that is burning in the live world burning. The flames and scorch a body dies with,
carried by its ragdoll, corpse, carcass or pieces (`BurnResidue`), are not saved
either: loaded remains show neither. Pools
of burning liquid and burning blocks are not saved either: a save taken with
the world alight loads with the fires out. What does survive is the remains of
every body blown apart once they have settled — people, animals and the
player, in the pose each died in, with an animal's harvest record still tied
to its torso (`world.remains`, beside the older `world.fragments`) — and a fire
bomb still in the air, which is saved with the other explosives and shatters
where it lands. If you add state, decide explicitly which side of that line it
sits on.

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
| a world event | `EventType`, `EventConstants`, `EventSystem.DEFINITIONS` | JavaDoc rung table, modifier query, `EventRollLadderTest` |
| a simulation system | new class + cadence interface in `simulation/` | matching `Game` tick, `newWorld` reset, `SimulationSystemContractTest`, save/load if it holds state |

**Enum ordinals are part of the save format.** `SerializedEnumOrderTest` guards
this: append new constants at the end, never reorder or delete. Reordering
`ItemType` silently turns every saved iron pickaxe into something else.

### Where gameplay rules live now

`Game` is orchestration only — the loop, the app state machine, world setup,
input routing and tick wiring. It went from 4,381 lines to 1,462 in 0.4.0, and
to 886 in 0.5.0. Every gameplay rule that used to be interleaved with it lives
in a named collaborator:

| Class | Owns |
|---|---|
| `WorldBootstrap` | reset, reseed, construct, dry-land spawn, starter camp |
| `AutomatedRunDriver` | `VEYLON_*` scenes and captures, the smoke script and gate |
| `FrontendController` | title, graphics options, two-frame loading, return to title |
| `HotkeyRouter` | global keys: screens, debug toggles, quick save/load, hotbar |
| `PlayerEnvironmentSystem` | shelter, smoke, fumaroles, POI discovery, station scan, mesh eviction |
| `PlayerCombatSystem` | all damage the player deals, durability, kill reputation |
| `PlayerBlockActions` | hold-to-mine, break drops/spill/vandalism, placement |
| `WorldInteractions` | F and RMB routing, NPC talk/rescue, stations, beacon endgame |
| `PlayerConsumables` | eat, drink, treat wounds, equip gear |
| `CrateTransactionSystem` | crate transfers and theft attribution |
| `InteractPromptBuilder` | the `[F] …` hint (pure read — cannot change what F does) |
| `AmbienceSystem` | ambient particles and the audio mix; `BodyFireEffects`, what burning bodies give off and their sound |
| `SleepSystem` | sleep eligibility, quality scoring, night effects |
| `QaHarness` | benchmark scenes, showcases, release smoke gate |

`OrchestratorSizeTest` holds `Game` under 1,000 lines, plus a budget on each of
the next-largest classes. It drifts in one direction and for one reason: `Game`
is the only object that can reach everything, so a feature spanning two systems
is always easiest to write inline in a tick, and each one is individually small.
The fix when it fails is to extract, keeping the public command on `Game` as a
one-line delegate — not to raise the ceiling.

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

## Audio ownership and frame work (0.6.0)

`AudioManager` owns the device/context, 122 generated PCM buffers, 24 one-shot
sources, 16 ambience emitters and one music source. `ProceduralAudio`,
`AudioFilters`, `AmbienceBeds`, `MusicPhrases` and `PcmAudio` are pure DSP; the
runtime streams one generated array at a time into mono signed-16 OpenAL buffers
and retains no float catalog. No sample assets, decoder dependency or worker
thread is involved.

`VoicePool` owns bounded admission, priority/quietness/age ordering and pending
steal releases; `VariantBank` advances only at actual playback. `AmbientEvents`
schedules four independent details and irregular gusts. `ThunderScheduler` owns
at most 16 transient strikes. `MusicDirector` schedules a 12-second phrase with
120-180 seconds of silence afterward on one independent source. These share the
presentation-only AudioManager RNG, deliberately excluded from reseedSimulation.

`EfxProcessor` feature-detects ALC_EXT_EFX and owns one reverb effect/slot with six
smooth presets. `AcousticSources` owns at most 40 direct low-pass filters, shared
four-ray/256-probe frame accounting and air absorption. Dry devices skip all EFX
calls; absent audio devices skip all playback/scheduling/native calls. Scene
classification reads loaded state and never plans a settlement or generates a
chunk. Music threat queries examine at most 64 entries in each entity list.

The existing six ambience gain calculations remain in AmbienceSystem. They also
supply weather intensity, nearest fire position, shelter state, environment and
music mood. The medium tick derives targets; AudioManager.update handles live
bus gains, smoothing, timers, source filters and native error/timing diagnostics
on the frame thread. Game is 889 lines, below its 1,000-line gate.

Before a new world is constructed, resetWorld cancels thunder and pending steals,
stops active one-shots/music, clears the occlusion world reference and reverb tail,
and silences/resets ambience. Successful loads use that same newWorld path;
return-to-title and failed frontend loads reset too. The native audio smoke
explicitly queues distant thunder before load and asserts no event survived.
Headless policy tests cover queue/director/pool reset without requiring OpenAL.
Audio schedules and the first-night marker are transient, never save fields.

AudioSettings mirrors graphics properties in AppPaths. Title and pause share
AudioOptionsScreen; it edits the same mix live, persists on Apply, and restores
its snapshot on Back. Its input owns Escape/F5 and pause options stop simulation.

## Game modes and abilities (0.7.0)

Every world is `SURVIVAL` or `CREATIVE` (`entity/GameMode`, stable ids
`survival`/`creative`, `fromId` rejects anything else) and carries a permanent
`creativeMarked` flag that is set the first time a world becomes Creative and is
never cleared. `GameModeController` is the single owner: `reset(mode)` runs in
`WorldBootstrap` before the new `Player` exists, `applyToPlayer` runs immediately
after it is constructed, `switchTo` applies the player-visible side effects of a
deliberate switch, and `restore` applies loaded state with no log line, no
healing and no perception effect. `Game` holds one-line delegates only.

**Gameplay code reads abilities, never the mode.** `entity/PlayerAbilities`
derives `invulnerable`, `mayFly`, `instantBuild`, `unlimitedItems` and
`perceivableByAi` from the mode in exactly one place; `flying` is the only
non-derived value and is forced off whenever `mayFly` is false. A grep for
`GameMode.CREATIVE` outside the controller, the save codec, the QA entry points
and the screens should find nothing.

Where the gates live:

- **Damage and needs** sit in `Player`: `hurt` and `hurtPhysical` return before
  armour wear, feedback and the bleed roll; `addAffliction` is a no-op;
  `onLanded` drops queued fall damage; `tickNeeds` keeps its observation refresh
  (biome, `envTemp`, `exposedToSky`, flash and noise decay) and then holds needs
  at their maxima instead of draining. Values other systems accumulate —
  `smokeExposure`, `fallDist`, wetness — are neutralised every tick so leaving
  Creative cannot apply them all at once.
- **Perception** has exactly one predicate, `Player.isPerceivableByAi()`, used at
  every detection, targeting, hearing and scent site in `ai/`, `settlement/` and
  `combat/`, and as the gate on player-sourced `WorldNoise` events. Proximity
  simulation, friendly interaction and every non-perception consequence
  (reputation, ownership, theft, vandalism, bounty) are untouched.
- **Items** read `unlimitedItems()` at each use-up site — placement, bow shots,
  firearm reloads, thrown bombs, eating, drinking, treatment, every
  `consumeDurability` path and the carried-inventory pass of
  `ItemConditionSystem`. Transforming, trading and moving items keep Survival
  rules everywhere.
- **World controls** (`CreativeWorldControls`) gate three places and no more:
  `Game.advanceClock` (the frame's only clock step), `WeatherSystem.mediumTick`
  (no new transition target while locked) and `EntityManager.slowTick` (no
  natural spawning, after the despawn pass). `SleepSystem` refuses to sleep while
  the clock is frozen, because sleeping fast-forwards that clock.

The save format stays binary v3. Two optional, versioned, length-prefixed
sections are appended to the v3 extension envelope and dispatched by
`save/V3ExtensionSections`: `player.game-mode` (mode id, mark, flying) and
`world.creative-controls` (frozen, locked, spawning paused), written in that
order so a load restores the mode first. Each codec rejects an unsupported
version, a truncated payload and trailing bytes, so a corrupt section fails the
whole load and the verifier leaves the live world intact. Unknown ids are still
skipped, so older builds load a Creative save as an unmarked Survival world.

Reset and restore contract: every control and every transient timer is released
in the `newWorld` path and asserted in
`GameLoopIntegrationTest.newWorldClearsSimulationQueuesAndPlayerActionState`.
`clear()` on the world controls also runs on every mode switch in both
directions, and a load applies control flags only into a Creative world, so a
flag can only be true while the world is Creative.

| State | Persisted | Where |
| --- | --- | --- |
| Game mode | yes | `player.game-mode` |
| Creative mark | yes | `player.game-mode` |
| Flying | yes, restored only in Creative | `player.game-mode` |
| Other abilities | no, derived from the mode | — |
| World controls | yes, restored only in Creative | `world.creative-controls` |
| Double-tap timer, break-repeat timer, catalog query/tab/scroll/focus, pending confirmation, chosen new-world mode | no | reset with the world or the screen |

`AppState` gains the worldless `TITLE_NEW_WORLD`; `UiMode` gains `GAME_MODE`,
`CREATIVE_CATALOG` and `WORLD_CONTROLS`. `GAME_MODE` and `WORLD_CONTROLS` pause
the simulation like the options screens and are in the `HotkeyRouter` early
return, so they own Escape, F5, F9, Q and every other key while open; the
catalog behaves like the inventory screen and owns its keys from the opening
frame. No thread, asset, dependency or outcome-affecting `Random` was added, and
the hot paths gained only boolean reads. Game is 959 lines, below its 1,000-line
gate.

## Physical precipitation (0.7.1)

`AmbienceSystem` owns a `RainField` that emits over a 22-block radius in loaded
columns, using its existing world-seeded cosmetic RNG. The heightmap is only a
sky-entry bound; shelter exposure still drives gameplay and audio, never the
rain field. Per-column ceiling rejection limits deep-cave work. Drops remain in
world space as the camera moves; precipitation beyond 32 blocks or 42 vertical
blocks is recycled, including after teleportation.

`ParticleSystem.update(dt, world)` integrates the existing structure-of-arrays
pool. Rain relaxes toward a size-dependent terminal speed and smooth wind.
`RainCollision` traverses each old-to-new position segment with voxel DDA and
returns the first solid or water contact and its face normal. Unloaded space,
invalid segments and starts inside geometry retire particles without an impact.
Water uses the rendered 0.88 height; non-solid decorations retain world solidity
semantics. Exact contact alone creates 2–4 density-scaled ballistic droplets,
which collide and expire without recursively splashing. No collision writes
world state. The no-world overload is for ordinary effects and retires rain.

Descending packed-array iteration processes every original particle once even
when removal swaps a tail and impacts append droplets. New droplets first move
on the next update. The unchanged 4,000-slot pool admits rain below 2,400 total
particles and splash below 2,800, preserving at least 1,200 slots for other
emitters. Collision reuses one result and a 32-entry direct-mapped column cache:
World retains voxel columns for its lifetime, references expose edits immediately,
and changing World invalidates the cache. Missing columns are never cached.

`ParticleRenderer` packs 13 floats per instance: position3, size1, RGBA4,
sprite/stretch2, velocity3 (attribute 5 at byte 40, stride 52). The shader aligns
a trailing streak with world velocity transformed into view space, with a short
fallback for end-on/zero motion and a close-camera fade. Depth testing and the
alpha/additive passes remain unchanged. HUD precipitation is snow-only.

## Death ragdolls and blood (0.7.2)

A body that dies is no longer removed and replaced in the same tick. `EntityManager.fastTick`
fires every consequence of the death where it always did — reputation, loot,
mission failure, `counterattacks.onMemberDied`, resident bookkeeping, the log
lines — and then hands the body to `RagdollSystem` instead of building the
carcass. The carcass, or for a person the new `HumanCorpse`, is created where
the body actually comes to rest. Quest credit and trust therefore still land on
the tick of the kill; only the object left behind waits.

`Entity.dead` means both "health hit 0" and "remove me from the world", and
seven sites use the second meaning with health untouched: an expired trader, a
raider fading at the camp edge, four war-party stand-downs and routed survivors.
`EntityManager.reallyDied` gates on `health <= 0`, the discriminator `fastTick`
already used for its "has died" line, so none of those drop dead on the road.
Death by illness goes through health and is a real death.

`RagdollSystem` is position-based dynamics over an articulated joint tree, not a
rigid-body solver and not a physics library. A body is a rigid torso box with a
quaternion orientation plus one endpoint mass per joint named in `BodySkeleton`:
at most twelve, listed parent-first, each a hinge or a cone with its own limits.
Humans have ten, quadrupeds twelve and birds six. A step integrates every point
with the same numbers ordinary entities use — 26 m/s² in air, 7 in water, a
−2.2 m/s descent clamp, 0.5 horizontal water drag, 0.2-block axis sub-steps —
runs six parent-first projection passes over segment lengths, joint limits,
limb-versus-torso contact and sampled segment sweeps through loaded voxels, and
then reads velocity back out of the distance actually travelled, which is what
keeps the chain stable without a solver that can explode. The killing blow
supplies an angular kick, raised to a one-time minimum toppling speed so a body
killed standing still falls over. After that only contacts, applied at their
lever arms, and joint reactions turn the torso. There is no rest-pose spring and
no roll or pitch target: what a body hits decides how it lies.

This replaced the 0.7.2 solver in 0.7.4. That solver had at most six free bones,
three relaxation passes, a pull toward each bone's rest offset and a spring that
rolled a grounded body onto a preset side. The authoring contract and every
tuning value are in [joint authoring](engineering/RAGDOLL_JOINTS.md); the
measurements behind them are in the
[validation record](engineering/RAGDOLL_VALIDATION.md).

The solver step is fixed at 1/60 s and driven per frame from the `simulate`
gate beside `particles.update` and `projectiles.update`, not from the 20 Hz
bucket. Creatures are drawn without interpolation and a tumbling body turns far
faster than a walking one, so 20 Hz strobes; the fixed sub-step keeps behaviour
frame-rate independent and a stalled frame clamps to four steps rather than
replaying hundreds. The accumulator is a double with a small tolerance, so a
death at 30 fps and the same death at 144 fps run identical steps and come to
the same rest. Only a body whose torso is supported counts quiet steps, and any
endpoint moving more than 4 cm or the root turning more than 0.06 rad from the
sleep snapshot restarts the count. A body settles after twelve quiet steps with
squared point and angular speeds under `SETTLE_ENERGY` (0.25), after thirty
quiet steps of bounded contact jitter, or on the 6-second timeout. Requiring
torso support is what stops a body freezing while it still stands on its legs;
measuring every endpoint is what stops a resting torso freezing a limb that is
still swinging; the timeout is what guarantees one can never stay unsettled.
Live bodies are capped at twelve and the oldest settles immediately over the
cap.

`World.getBlock` answers `AIR` for an ungenerated column, so `RagdollCollision`
treats an absent column as solid and a body killed at the streaming frontier
settles on the spot instead of falling forever. It caches chunk references in
the same direct-mapped 32-entry table `RainCollision` uses, and for the same
reasons; absent columns are never cached. The per-frame solver allocates
nothing.

**No new `Random` was added and none will be.** Everything a body does follows
from the impulse that killed it; where the choice is genuinely free — which side
a body falls on when nothing pushed it — the sign comes from a hash of the death
position. All the feature's randomness is presentation-only and lives in the
already-seeded `ParticleSystem`.

Rendering reuses the existing `entityShader` block: `renderCorpses` and
`renderRagdolls` sit beside `renderCarcasses`, stamp the body's own `BodyPose`
into the species' shared cached `EntityModel` through `Animator.poseBody`, and
draw through the same `setEntityLight` / `entityVisible` / `drawModel` path. No
new shader, mesh or draw-call class, and no model is cloned per body. Each joint
is posed by three parent-relative angles that its part composes as Rz·Ry·Rx, so
an unmoved joint reads as zero and keeps the pose its builder gave it. Forearms,
shins, lower legs and tail tips are real child parts: `ModelPart.split` halves
an existing box and, while the child is straight, still draws the original single
cuboid. Living animation never bends those children, which is why living models
render pixel-identical to 0.7.3. The humanoid is a superset model whose
accessories `resetPose` makes visible again, so `Animator.applyAppearance` —
extracted from `poseNpc` and taking primitives rather than an `Npc` — runs for
every body, and a corpse keeps the look of the person who died without holding
the entity the world already removed.

Orientation lives on the draw transform rather than the model root, because
applied after the heading `rotateZ` is a roll about the body's own spine.
`Carcass` gained a `BodyPose`; the positional-hash yaw the renderer used to
invent is now a stored default assigned at construction, so a carcass restored
from an older save or staged by QA looks exactly as it always did while a
settled one is drawn in the pose it really came to rest in.

`ParticleSystem.bloodBurst` throws a radial spray biased along the killing blow
plus a little fast-fading mist — at most 22 droplets and 5 puffs, every count
through `scaled(...)` so `particleDensity = 0` emits nothing. A body drips one
droplet every 0.12 s while it is moving faster than 1.6 m/s, and settling adds a
blood `Track`. Blood stops at `BLOOD_LIMIT` (3,600), and the worst case twelve
bodies can emit fits inside the 1,200 slots weather already reserves.

| State | Persisted | Where |
| --- | --- | --- |
| Settled carcass pose (yaw/pitch/roll/lift/pivot + three angles per joint) | yes | `world.bodies`, positional, one per carcass |
| Human corpses (position, decay, appearance, pose) | yes | `world.bodies` |
| Bodies still falling | no — a save settles them first, so a save taken mid-fall comes back as a corpse rather than a lost body | — |
| Solver accumulator, settle timers, lifetime counters | no | reset with the world |

The base v3 layout is untouched; `world.bodies` is an optional stable-ID section
in the v3 extension envelope, entirely numeric (the archetype travels as a
bounds-checked ordinal) so no byte anchor the migration tests rely on moves. An
absent section means what every save written before 0.7.2 means: every carcass
keeps its constructed pose and there are no human corpses. 0.7.4 moved the
section to version 2, which writes three floats per joint; version 1 still
loads, with its flat bone indices mapped by name into the new tree and new child
joints left straight. 0.7.2 and 0.7.3 accept only version 1, so they refuse a
0.7.4 save as a whole rather than load part of it.

A human corpse is deliberately not an `Npc` and never enters `entities.npcs`, so
it is not a perception or combat target, costs nothing against
`SettlementManager.MAX_ACTIVE_NPCS`, and cannot disturb `residentIndex`
bookkeeping. It rots on the slow tick beside carcasses and is despawned past the
same 170 m radius wildlife uses. Birds tumble and leave nothing, as they always
have.

## Lethal combat, body fragments and molotov fire (0.8.0)

Three rules changed what combat leaves behind, and they meet in
`EntityManager.fastTick`, which still fires every consequence of a death where
it always did and then decides what the body becomes.

**Where a shot lands decides what it costs.** A bullet or an arrow that enters
an `Npc` is judged by `HitZone`, the height above the feet at which the
projectile's segment crossed into the hit box — the slab intersection, not the
sub-step sample, because `step` samples every 0.45 blocks and a steep shot
through the top of a head would otherwise be judged by a point in the chest. A
sleeping NPC lies down, so every hit on one is a torso hit. `ProjectileLethality`
holds the whole table: head kills outright, a torso hit adds three wound units
for a bullet and two for an arrow, six kills, and a non-lethal torso hit costs
at least half the victim's maximum health for a bullet and a third for an arrow.
Wounds count once per trigger pull — every pellet of one blunderbuss shot
carries the same `shotId` — and everything goes through `Entity.hurt`, so
attribution, reputation and the death pipeline see an ordinary hit. The switch
over `ProjectileSystem.Kind` has no default: a new projectile kind does not
compile until it is given a row or listed among the kinds that deal no impact
damage. The player and creatures keep the plain damage model.

**A lethal blast kills and dismembers.** `ExplosionSystem.explode` takes a
`lethalToLiving` flag; a scrap bomb and every powder keg, including one set off
by a blast that is not itself lethal, pass it. Every living body — NPC,
creature, and the player unless invulnerable (Creative), which is checked first —
whose body centre is within `power × LETHAL_RADIUS_FACTOR` (1.5, so 3.9 blocks
for a scrap bomb and 5.7 for a keg) dies through the ordinary damage path
(`Entity.killBy`) whatever its health and whatever stands between, and carries a
transient record of the blast that killed it (`Entity.recordBlastDeath`, first
fatal blast only). Everyone further out takes the existing falloff damage. A
fire bomb is not a bomb for this rule.

**`BodyFragmentSystem` is the ragdoll solver's rigid-body twin.** A body with a
blast record is split at its joints into its family's `FragmentAnatomy` pieces —
ten for a person, seven to twelve for an animal — instead of being handed to
`RagdollSystem`: NPCs and creatures in `EntityManager`'s death routing, the
player at `Game`'s death transition. An animal that leaves a carcass gets
exactly one, at once, tied to its torso piece; that torso outlasts the settled
cap and the despawn radius while the carcass does. Each piece
starts where the living model drew it, leaves with an impulse inversely
proportional to its mass (never less than `MIN_LAUNCH_MASS`), and then falls with the same numbers a ragdoll uses,
stepped from the same per-frame `simulate` gate at the same fixed 1/60 s. It
sweeps the world-axis box of its *turned* collision box, so a limb that lands
lying down rests on its lowest corner, and a grounded piece feels gravity's
torque about that corner. No generator was added: what the blast does not decide
comes from a hash of the piece's position, the way `RagdollSystem.positionBias`
picks a side. At most 120 pieces fly and 600 lie about; over either cap the
oldest gives way. Pieces rot on the corpse clock beside carcasses and corpses in
`EntityManager.tickWorldDetritus`, and the step allocates nothing. Each piece is
drawn from its own body's shared model — the humanoid for a person or the
player's remains (a plain, campless look), the species' model for an animal —
posed as the body died and cut down to its own parts by `Animator.poseFragment`,
with its wounds on the faces of its collision box (`FragmentModels`). An animal's
harvest record is never drawn as a whole carcass; its torso piece is the body.

**A fire bomb is a molotov.** It shatters on the first block or body it touches,
its fuse only a fallback for one that never lands, and makes no blast, no blast
damage and no broken blocks. `LiquidFireSystem` runs the pool it spills on the
medium tick beside `FireSystem`: a priority flood from the cell the bottle broke
over, biased along the throw, that only ever runs sideways and down, never into
a solid or water cell, at most 22 cells per bottle and 160 in the world. Each
patch lights the fuse of a neighbouring keg and rolls to set the flammable blocks
it touches alight **through `FireSystem.ignite`** — which is what keeps one
ceiling over both kinds of flame: block fires from bottles, blasts, lightning and
spread all compete for the same `FireSystem.MAX_ACTIVE_FIRES` (220), while the
pools have their own cap. A block fire a bottle lights remembers the bottle and
passes it on as it spreads, so it burns bodies for the thrower. A bottle that
breaks on a body sets that body alight before it spills, so a bird hit in the air
burns though the liquid finds no ground.

**Rain is one predicate.** `FireSystem.isRainedOn` — precipitation falling and
sky light above `RAIN_EXPOSURE_SKYLIGHT` in the cell above — decides for burning
blocks, campfires and pools alike. An exposed burning block holds still and goes
out after two seconds without being consumed; an exposed pool goes out after
one. Shelter needs headroom: the sky light is sampled one cell up, and the top
of a column is always fully lit, so a roof resting directly on a block does not
shelter it. A patch under cover burns on, but does not light a block the rain is
falling on, which the rain would only put out again. A pool or a burning block
the rain is reaching, or that is still wet from it, sets no body alight; a
campfire has no weather rule beyond faster fuel drain, so it does, and the rain on
the body's head then puts it out.

**A living body burns on its own clock.** Every `Entity` carries one
`BodyCombustion`, read-only outside the `entity` package; `CombustionSystem`
(`Game.combustion`) is its only writer and runs on the fast tick between the
player's needs and entity AI, so AI reads this tick's fire and a body the fire
kills is routed whole in the same tick (a dead creature no longer gets an AI step,
as a dead NPC never did). Each tick first sweeps every living body's box from
where it was sampled last to where it is now (`BodySweep`) and asks the flames
standing in the world whether they touch it — patches of burning liquid
(`LiquidFireSystem.exposeContacts`), burning blocks, fueled campfires and placed
torches (`FireSystem.exposeContacts`) — so a body crossing a small flame between
two samples still touches it; a fire bomb breaking on a body calls `ignite`.
These are the only ways a flame hurts a body: the block and liquid fires' medium
ticks no longer damage anyone. Each body keeps only the strongest contact of a
tick, so flames never stack, and one fire with one afterburn timer that later
contacts refresh up to a cap. When the player's flame lights or takes over a
person's fire, that is one attack per bottle, never per patch or tick. Damage
goes through `Entity.hurt` with the fire owner's credit, at a contact rate while
a flame touches the body and a lower afterburn rate as the fire fades; a burn
death falls whole. Water over most of the body
puts it out, water to the hips only shortens it, and rain puts it out when it
reaches the head — `FireSystem.isPrecipitationReaching` asked of the head's cell,
the same predicate `isRainedOn` asks of the cell above a block, so any roof over
the head shelters. The Survival player's medical burn injury is separate: it
comes once per fire, after a second alight, and neither hurts nor heals while
the flames burn. A Creative player never catches. People and animals keep out of
torch and campfire cells as they keep out of walls (`Entity.keepsOutOfFlames`,
applied in `VoxelPhysics`, steered round by `Steering.moveToward`, routed round by
`Pathfinder`), and walk out of one they find themselves in, so residents living
beside their fires do not set themselves alight; the player walks where they
choose, and cannot place a torch or campfire into a body that can burn. The
rules, numbers and API are in
[the combat and fire contract](engineering/ALL_LIVING_COMBAT_FIRE_CONTRACT.md) §10.1 and §4.1.

**Burning people and animals panic; the player never does.** `ai/FirePanic` is
the first decision of `NpcAI.update` — before a conversation holds a person
still and before every family's own brain, `SettledNpcAI` included (which
repeats the call for direct callers) — and of `CreatureAI.update`, before any
species' choice. While a body is alight, and for a second and a half after, it
drops what it was doing: no attack, shot, reload, trade, work, meal or sleep. It
runs toward goals a few blocks off, redrawn every 0.6–1.2 s from its own seeded
stream (`EntityManager.nextPanicFloat`): away from where the flame touched it,
turned by a seeded angle, each checked along a straight line with the
pathfinder's footing rules. It turns at a bounded rate, moves with the shared
`Steering`, stops short of a drop deeper than three blocks or the edge of the
loaded world, replans when blocked (never more than four goals a second), and a
bird flies its escape, climbing. Walls, cage bars and fires stop it as they stop
anyone. An open NPC screen closes when its speaker catches, and nobody offers to
talk while panicking. When the recovery ends, the body's plans are forgotten and
its ordinary AI decides afresh from the world as it is. Panic reads neither the
player nor the camera, so it cannot find a Creative player; the player's own fire
is damage and presentation only, and their controls are untouched. The intent
(`Npc.panic`, `Creature.panic`) is transient. Details in the contract §12.1.

**A fire goes on on the body a death leaves, and nowhere else.** A dead body's fire
is never ticked, it never panics, and the first lethal cause decides the death: the
fire's fast tick runs before the frame's projectiles and fuses, and a body already
dead neither takes a blast record nor gives its kill back to its fire's owner. At the
death transition — `RagdollSystem.spawn` for a body that falls whole,
`BodyFragmentSystem`'s launch for one blown apart, the player's remains included —
`BurnResidueSystem.capture` (`Game.burnResidues`) copies the body's scorch and, if it
was alight, the strength it burned with and its afterburn left into a `BurnResidue`
that refers to nothing. The ragdoll hands that same object to the corpse or carcass
it settles into; every piece of a body blown apart shares it, each by its mass
(`BodyFragment.burnShare`). Its flames die down within four seconds, sooner in water
or open rain, and smoke for three; at most 32 do at once, and whatever carries one
leaving the world lets it go. It is presentation only: nothing samples it as a flame.
A body that leaves the world without dying — a settlement going dormant, a party going
abstract or retired, a captive rescued, a routed fighter, a despawned animal — goes
through `EntityManager.depart` (`removeNpc`, `removeNpcs`, the entity tick's
administrative `dead` path): its fire and panic are forgotten, no AI keeps it as a
target, a conversation with it closes, and it leaves no body or residue; a dormant
resident keeps the health it left with. The player cannot fall asleep while alight,
and a fire wakes a sleeper. Details in the contract §14.2.

**How a burning body looks and sounds.** Presentation reads the fire and never
writes it. `gfx/BodyFireLook` turns a living body's `BodyCombustion` (including
presentation-only fields the fire keeps for it: how long ago and how its last fire
went out, how strongly it burned then, a flicker number per fire, and where the
flame touched it relative to its feet) or a `BurnResidue` into one look — flames,
how much of the body they cover, the swell and climb of catching, smoke, steam,
embers, scorch, glow and light — so every presentation tells the same story.
`gfx/model/BodyPosing` is the only way a body is posed for drawing (living,
falling, dead, a piece), and `gfx/model/FlameAnchors` finds bounded points on that
family's real boxes, spread by area, and resolves them through the same part
transforms the renderer draws with. Each frame `Renderer` draws the living first,
then the remains; for each burning one it sets the char, ember glow and firelight
uniforms of `entity.frag` and adds flame tongues on the alight anchors to
`gfx/BodyFlames` (≤ 1,024 a frame, shared evenly over the burning bodies in range,
a piece by its share of its body), which `ParticleRenderer` draws in one extra
premultiplied pass after the ordinary particles. On the ambience cadence
`BodyFireEffects` (inside `AmbienceSystem`, so it only runs in simulated frames)
gives off licks of flame, embers, smoke and steam from the same alight anchors with
the body's velocity, carried off by the wind (`ParticleSystem`'s per-particle air
response), and plays the nearest four burning bodies' crackle, catch and douse
sounds through `AudioManager`. The player's own fire is drawn at the edges of the
view by `post_final.frag` (`Environment.vigBurn`) and round the grip of a held
item, never inside the camera. Nothing here reads a simulation random stream,
allocates per frame, or changes a burn; flames flicker by the particle clock, which
stands still while the game is paused. Details, budgets and QA in the contract §16.1.

| State | Persisted | Where |
| --- | --- | --- |
| Settled pieces of every body (family, piece, position, orientation, decay, appearance, the pose it died in) | yes | `world.remains`, one record per piece, oldest first; one pose per body |
| An animal's harvest record tied to its torso, and the arrows lodged in it | yes | the carcass in the v3 body; the tie and the arrows in `world.remains` |
| People's settled pieces, for older builds | yes, unchanged | `world.fragments` version 1, read only when `world.remains` is absent |
| Pieces still in flight | no — a save settles them first, so they come to rest rather than being lost | — |
| Pools of burning liquid, burning blocks | no — a save taken mid-burn loads with the fires out | — |
| A fire bomb still in the air | yes, with the other explosives; it shatters where it lands | active explosives |
| Torso wounds, the last shot id, the blast record, a living body's fire and panic | no — they live on the entity and die with it | — |
| The flames and scorch remains died with (`BurnResidue`) | no — a save leaves them burning; a load builds remains without them | — |

`world.fragments` (0.8.0) and `world.remains` are optional stable-ID sections in
the v3 extension envelope, so the frozen v3 body and the bodies layout are
untouched. Only people had pieces in 0.8.0, and its reader refuses any version
of `world.fragments` but 1 — and with it the whole save — so the pieces of every
body went into a new ID instead: `world.fragments` is still written, byte for byte
as before, and a 0.8.0 build skips `world.remains` by its length and opens the
world with its people's pieces in the standing rest pose, without the animals'
pieces, and with each animal's record as an ordinary whole carcass. This build
reads `world.remains` when it is there and `world.fragments` otherwise,
whichever order they come in. Records are entirely numeric: a piece is its body
family's ordinal plus its id in that family's list, both append only
(`SerializedEnumOrderTest` pins them), and a person's id is its `world.fragments`
ordinal. A piece the reader would refuse is left out on write, so one bad piece
of debris can never cost the save; a malformed section fails the whole load,
while a body family this build does not know is skipped rather than read as
anything else. The exact layout and policy are in the `RemainsSection` class
comment and in
[the combat and fire contract](engineering/ALL_LIVING_COMBAT_FIRE_CONTRACT.md) §14.1.

Drawing reuses what the bodies already use. A piece is the shared humanoid model
with everything outside its own part subtree hidden, drawn from that part
because a hidden part draws nothing below it, plus one dark cut face per severed
end. `ModelPart.split` draws a straight limb as its original single box, so
hiding a forearm would still have drawn the whole arm; `forceSplitDraw`, cleared
by `resetPose`, makes a part draw only its own half. A pool is a thin emissive
sheet per cell with its own shimmer phase, and its flames, embers, smoke and
steam come from the existing particle emitters, under the same splash ceiling
rain uses, so a pool can never crowd out blood or blast debris.
