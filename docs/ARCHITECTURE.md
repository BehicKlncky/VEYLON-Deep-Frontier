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
        │              counterattack director, urban site director)
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
              PlayerLifeCycle        death transition, remains and respawn
              CrateTransactionSystem timed searches, crate transfers and theft attribution
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

`newWorld(seed, fresh, generatorVersion, mode)` is the single path that produces a
playable world, used by New Game, by save loading and by the QA harness. The
rules live in `WorldBootstrap`; `Game.newWorld` and its shorter overloads are the
entry points every caller still uses. Ordinary new worlds take
`World.DEFAULT_NEW_WORLD_GENERATOR`; a load passes the version its save recorded.
An unsupported version is refused before anything is reset. It **resets before it
constructs**, in this order:

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
   A fresh urban world records its content revision here, before any chunk generates.
5. Clear entities and the event log; reset faction standing, the camp position and the clock.
6. Clear player action state: UI mode, mining, bow draw, reload, sleep, theft
   event bookkeeping.
7. Generate chunks around spawn synchronously, find dry land, place the camp. An
   urban world instead stands the player on its planned ground (`UrbanBootstrap`)
   and raises no crash camp: on the street network, the courtyard walk in front of
   the starter refuge's door; on the bare street datum of generator 4, a fixed
   street column.

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
   `handleGlobalKeys()` (inventory, crafting, map, pause, debug overlays). When F
   closes a crate or NPC screen the press is consumed, so the same frame cannot
   reopen what the player faces. Steps 3–7 are `Game.playStep`, which headless
   routes such as `UrbanSliceRouteTest` run in the same order.
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

Runtime block changes funnel through `World.setBlock(x, y, z, type, record)`.
The boolean controls persistence in `changedBlocks`, not notifications. Every
actual change calls `world.listener.onBlockChanged(...)`, which currently routes
to the water system through `Game`; heightmaps, mesh dirtiness and light-emitter
updates are handled in `World`. Fixture toggles pass `record = false` because
`world.fixtures` owns their saved state, while still notifying the listener and
dirtying meshes. Bulk edits use `beginBatch` / `endBatch` to defer heightmap and
light rebuilds, and still notify the listener for each change.

```
player action ─┐
explosion ─────┼─→ World.setBlock ─→ chunk data + heightmap + dirty flag
fire spread ───┤                  └─→ Game.onBlockChanged ─→ dependent systems
QA staging ────┘                       (independent of record)
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

Generator versions form an explicit set (`World.isSupportedGenerator`): 1 legacy,
2 deep, 3 cave identities, 4 the urban street datum, 5 the urban street network,
6 the same street geometry plus frozen building plans (layout 3, template 1),
7 generated furnished apartments and shops (layout 4, template 2), and
8 the same buildings with shaped stairs (layout 5, template 3), and 9 the starter
neighbourhood (layout 6, template 4), and 10 adds encounter catalogue 1 on that
same geometry (layout 7, template 4), and 11 adds the commercial and civic catalogue
(layout 8, template 5) with encounter catalogue 2, and 12 plans the six districts with
their overlays (layout 9, template 6) and encounter catalogue 3, and 13 adds basements,
transit and rooftop routes to those districts (layout 10, template 7) with encounter
catalogue 4, and 14 adds the quarantine's growth sites and the city's wildlife
habitats (layout 11, template 7) with encounter catalogue 5, and 15 adds the
survivor camps and hidden refuges (layout 12, template 7, catalogue 5), and 16
adds guarded strongholds and raider yards (layout 13, template 7, catalogue 5), and 17
adds burnt-out cars to the local streets and the refuge, salvage and fortification
rules (layout 14, template 7, catalogue 5). The development selector creates 17. New
worlds use
`World.DEFAULT_NEW_WORLD_GENERATOR` (3, until the urban rollout); a load accepts any
supported version and rejects the rest, including anything above
`MAX_SUPPORTED_GENERATOR`, before it replaces the live world. Feature gates name
their versions (`hasDeepFrontier`, `hasCaveIdentities`, `isUrbanGenerator`) instead
of comparing with `>=`, so a new generator inherits nothing by accident, and
`WorldGeneratorFingerprintTest` pins versions 1–19 absolutely. `ShapedStairGenerationTest`
checks version 8 against frozen version 7 outside stair cells and across chunk orders.

An urban world's layout revision follows from its generator, so it is known
before bootstrap generates a chunk; a geometry change means a new generator
version, never a change under an old one (`UrbanRevision`). Its content revision
(the rolled loot, encounter and trade tables) is saved in `urban.meta`, which
only urban worlds write. It is optional for older layouts and required for
generators 9–19. Until that section, or its
absent default, has been read, a loading world's content revision cannot be read
at all. Fresh worlds use content revision 3; revisions 1 and 2 remain supported.
Revision 1 is the absent-metadata default. The tables and their hash inputs are frozen
under each revision. Generators 9–19 require content 3. Encounter catalogues are
additive and versioned separately in `urban.population` (1 for generator 10, 2 for
11, 3 for 12, 4 for 13, 5 for 14–19, which write codec 2); they do not reroll
content-3 loot or add encounters to older generators. Generators 13–19 also write
`urban.discovery` v1 (a count capped at 4,096,
then ascending observed site ids, each checked against the plan); absence means
nothing found. Generators 15–19 also require `urban.sites` v1, their survivor
sites and people, generators 16–19 `urban.compounds` v1, what their compounds
remember, generators 17–19 `urban.refuges` v2, their claimed refuges, and
`urban.fortifications` v1, their damaged barricades and braces, generators 18 and 19
`urban.utilities` v1, their taps' ledger and devices, and generator 19
`urban.missions` v1, what its survivor learned and did toward the city's
communications. Generator 19 also writes `urban.visits` v1 (package 21): the districts its
survivor walked and the buildings they entered, as ascending stable ids capped at
1,024 and 4,096; it is optional, absence (a package-20 save) means nothing visited, and
an older build skips it as unknown. Compatibility directions:

- this build, a legacy save: unchanged, and no urban state is created;
- this build, an urban save: the same geometry through its generator, the content
  revision from `urban.meta` (absence means revision 1 only for older layouts);
- an older build, a newer urban save: refused when its generator or content
  revision is unsupported, before the live world is touched; optional unknown
  sections alone do not make downgrading safe;
- an `urban.meta` in a non-urban world, a newer section version, a layout
  revision that does not match the generator, an unsupported content revision or
  trailing bytes: the whole load fails.

Urban ids (`world.urban.UrbanIds`) are packed from plan coordinates, never from
visit or list order. `urban.containers` validates generated fixture ids against
their immutable reservation and survivor owner building. An invalid, wrong-kind
or mismatched id fails the whole load, never resolves to another object.

### Urban city plans

Generator 5 (layout revision 2) builds the city from pure plans rather than
from terrain noise. `world.urban.CityPlanner.plan(seed, layoutRevision, rx, rz)`
plans one 256 × 256-block region and reads nothing else: no world state, no
other region and no visit order. Every decision is a `UrbanHash` derivation of
the seed, the layout revision, stable coordinates and one salt from
`UrbanSalts`; there is no `Random` stream, so `reseedSimulation` is unaffected.

- **Streets** (`StreetNetwork`): avenues 12 wide on every region edge, each region
  stamping its half, and three north-south and three east-west local streets per
  region, 8 wide. A north-south street's x depends only on the region column and
  an east-west street's z only on the region row, so streets continue straight
  across every avenue and the lattice is connected everywhere. An avenue
  segment's profile (plain or boulevard) is a function of the edge and the
  segment, so both regions agree without seeing each other.
- **Superblocks and parcels** (`DistrictPlan`, `SuperblockPlan`, `ParcelPlan`): 16
  superblocks per region, each courtyard housing (six parcels around a courtyard
  reached through two passages), a shop row (parcels back to back along a service
  alley), a park or a plaza. A parcel is 12–32 blocks a side, has index
  `superblock * 8 + slot` and a packed `UrbanIds` parcel id, a primary street
  entrance, a rear entrance onto its courtyard or alley, a reserved stair-core
  footprint and a storey envelope. Region (0, 0) reserves the starter refuge and a
  corner shop on one block, and the spawn anchor in front of the refuge door.
- **Overlap priority**: streets, then alleys and passages, then parcels, then open
  space, then tree beds. A cell is never taken from an earlier claimant; a
  collision is counted, not resolved.
- **Stamping** (`UrbanChunkStamper`): a chunk writes only its own blocks, from the
  plan of the one region that contains it, so `pendingGen` stays empty. Every
  surface is flush at the street datum (y 40) over stone fill; parcels are flat
  reserved lots until building generation arrives under a later generator. The
  blocks are a temporary mapping to existing ids (`UrbanSurfaceMaterials`).
- **Queries** (`UrbanWorldState`): `groundY`, `plannedRoofY` (a parcel's storey
  envelope, not a built roof), `surfaceAt`, `parcelAt`, `districtAt` and
  `spawnColumn` answer from the plan and never generate chunks. Use them, not
  `World.surfaceHeight`, which reports a tree crown today and a roof once
  buildings stand. `World.isColumnLoaded` tells an unloaded column from air
  without generating it.
- **Bounds**: at most `UrbanWorldState.MAX_REGION_PLANS` (16) plans are cached, in
  `BoundedPlanCache`; an evicted plan is rebuilt identically. Plans hold nothing
  persistent. `RuntimeBudgetSnapshot` reports the cache against its cap.
- **Diagnostics**: `CityPlanDiagnostics.inspect` checks a region against its four
  neighbours (overlaps, parcel rules, routes, seams) with a bounded message list;
  the urban QA scenes print it.
- Regions beyond the range `UrbanIds` can name (about 33.5 million blocks out) are
  the flat street datum.

Urban worlds spawn no surface wildlife and roll no event that places wolves, a
trader or a meteor at the column top (`EntityManager.slowTick`, `EventSystem`), since
in a city that is a roof. Legacy worlds keep both unchanged.

### Building plans and physical accessibility

Generator 6 pins `BuildingTemplates` revision 1 before bootstrap through
`UrbanRevision.buildingTemplateRevisionOf`. Its city fabric explicitly comes
from layout 2; it never changes generator 5's plans or stamps interiors. Generator
7 stamps furnished template 2 on the same city fabric. `urban.meta` v1 and the frozen v3 base
layout are unchanged. A build supporting at most generator 5 rejects a generator-6
save; this build still re-derives generators 1–6 under their original contracts.

`BuildingPlanner` derives a whole immutable `BuildingPlan` from seed, template
revision and parcel. Nested floor/room/fixture/stair records carry stable packed
ids and an immutable, bounded `PlanVolume` carries the proposed geometry.
`UrbanWorldState.buildingAt` never creates a chunk, fixture state, container or
loot. Its per-world LRU holds at most 256 buildings and is recreated on new/load.
There is no persistent state in the cache; eviction recomputes identical plans.

`AccessibilityValidator` uses `VoxelClearance`, which owns the unchanged AABB
body collision shared by `Entity.collidesAt` and the passability/standing policy
shared by `Pathfinder`. It opens only physically declared unlocked doors in a
private proposal, keeps locks solid, and sweeps cardinal moves with step/drop
clearance. Directed forward/reverse floods prove access and escape for room
interiors, landings, roofs and fixture/container/quest interaction reservations.
Stair routes also need two-wide flights and reversible swept transitions.
Validation has fixed volume/metadata/edge/message limits, and a failed proposal
has at most three deterministic candidate/alternate/minimal attempts. Exhaustion
fails explicitly. No world or random stream is touched.

Occupied front-door locks have a proven unlatched rear route. A plan's
`fixtureStateAt` gives saved touched entries precedence over defaults, including
an explicit saved unlock. Generator 7 resolves these defaults through
`World.fixtureLockAt` without populating the touched-state map. Edited/replaced
doors do not regain a generated lock. No keyed-tool dependency is
invented here. See [urban floor examples](urban-survival/BUILDING_PLANS.md) and
[decisions D-58–D-61](urban-survival/DECISIONS.md#1-decision-log).

### Generated apartments and indoor observations

`BuildingFurnishing` overlays frozen template 1 to produce template 2 before any
chunk geometry is written. It adds facade-side beds, basins, prep/living tables,
storage, varied floors, sparse clutter, broken partition panels and restrained
growth. Search reservations have derived room/container functions and stable
ids; no inventory or loot is generated. Shops and service parcels also have
real rooms. The same production validator checks all playable room cells,
interaction targets and reversible stairs after furnishings; budgets remain six
fixtures/three containers per room and three to six containers per flat.

`BuildingStairShapes` overlays template 2 to produce template 3 under generator
8. It changes only the old stair cells: four two-wide directional cells per
flight, with full concrete landings. Variant/basement selection deliberately
uses revision two's inputs, preserving all room, fixture and building identities.
Generators 6/7 keep their original plans and block bytes; saves are never
automatically upgraded. The saved generator selects the layout before bootstrap;
`urban.meta` remains version 1 and validates that matching layout.

`UrbanBuildingStamper` intersects whole plans with each chunk and parcel. It
writes only owned footprint cells, including AIR to carve B1, never the proposal's
paving halo or a neighbour's chunk. Generator 7 also maps streets to the real
urban materials. Generation has no contents, fixture-state or cross-chunk edit
side effects. `AccessibilityValidator.inspectGenerated` reads loaded actual
blocks, including approach context, and applies the same physical proof without
mistaking surrounding structures for geometry owned by the inspected plan.

`ChunkBlockEdits` keeps the public delta-map contract with a per-chunk index.
Generation restores that chunk's recorded edits after structure and before
saved fixture overrides, then finalises heights/lights once and dirties adjacent
meshes. The index adds transient memory proportional to the edit log; CPU chunks
are still retained (eviction remains package 24). No codec or enum changes.

`UrbanIndoorQueries` observes loaded voxels, never generating columns. A derived
precipitation-height map (1 KiB/chunk) treats opaque roofs and closed glass as
cover. Direct sky/glass transmission and bounded eight-block cardinal opening
rays provide daylight; diffuse interior light stays below the fire/collector
rain threshold. Roof/window edits refresh exposure and adjacent mesh lighting,
including diagonal vertex samples. This is a bounded approximation; block-light
wall leakage and presentation polish remain package 22.

Infrequent `World.urbanLocation` resolves building/floor/room metadata.
Room-aware shelter still checks live enclosure, so damage is observed. Generated
urban temperature/audio use depth below the street datum rather than below the
roof. Covered rooms retain seasons, buffer weather/day-night variation and
reduce altitude cooling; ground and upper floors no longer become deep caves.
Generators 1–6 retain their original light/weather/environment behavior. No
new simulation random stream or persistent indoor state is introduced.

### Commercial and civic catalogue

Generator 11 stamps building template 5. `CivicCatalogue` picks each parcel's
family from the seed, its district plan and stable parcel ids (its own salts, no
random stream); the starter superblock and every walk-up are exactly template 4.
`CivicTemplates` runs the shared walk-up skeleton (`BuildingTemplates.Builder`)
under a family `FloorProgram`, so halls, walls, the stair core, entrances and
reservation ids keep the proven geometry, then overlays fittings in lanes, islands
and racks, a secured room, a frontage `SIGN` and the family facade, and finally
the frozen stair shapes. `BuildingPlanner.plan(seed, revision, parcel, district)`
is the revision-five entry point; `UrbanWorldState.buildingAt` supplies the
parcel's cached district plan.

`AccessibilityValidator` treats a locked door of a secured room (dispensary or
armoury) as its only entrance: with locks solid the room must be unreachable, the
door's corridor side must be on an ordinary route, and a forced phase with the
door broken must reach and leave every reservation inside. Every other lock stays
solid as before, so older templates validate exactly as they did.
`BuildingPlan.survivorOwned` is the single ownership rule: whole occupied
walk-ups, but only the held street floor and basement of a civic building. The
container ledger, theft and destruction attribution, loot table selection and
the `urban.containers` plan check all use it. Encounter catalogue 2
(`EncounterAnchors.catalogue`) keeps catalogue 1 for walk-ups and adds lobby
guards to clinics and security posts; `urban.population` writes the generator's
catalogue and rejects the other. Diagnostics (`BuildingPlanner.identity`,
`BuildingPlanDiagnostics.catalogue` and `familyMatrix`) name a failing template.

### Urban districts

Generator 12 plans layout 9. `DistrictMap.districtOf(seed, rx, rz)` is pure: the
spawn region is residential, its neighbours residential or commercial, and rings
two and three always hold an industrial, a flooded, a civic and a quarantine region.
Residential and commercial regions keep the layout-2 plan exactly; `CityPlanner`
gives the other four their own superblocks (depot rows and fenced yards, canal
basins with causeways, a fenced civic square, a cordoned quarantine) on the same
street lattice, so seams are unchanged. `DistrictOverlays` adds a separate
per-column raster (`UrbanOverlay`: standing water, duckboards, fences,
checkpoints, contaminated ground, overgrowth, plants and powder kegs) that keeps two
cells from every parcel, off walkways, alleys, causeways and the starter
superblock, with avenues dry and clean. `UrbanOverlayStamper` writes it after
streets and buildings, chunk by chunk from the plan; `CityPlanDiagnostics` checks it.
Every water surface is the top of y 41 on a solid bed, so `WaterSystem` never moves it.

`DistrictRules` gives each district one existing climate `Biome`, which
`World.biomeAt` and chunk soil moisture report, so temperature, weather, fog tint and
plants follow without new climate code and no biome ordinal changes. It also holds
a presentation-only fog visibility scale, the threat budget and the arrival line.
`DistrictCatalogue` (building template 6) picks proven template-4 and template-5
plans by district. Encounter catalogue 3 spends each region's budget on unheld
buildings. The player tracks the district underfoot for the HUD and the arrival
line. `CONTAMINATED_GROUND` and the quarantine-only toxic fog roll sickness on the
player's existing seeded generator, and only on that ground or inside a quarantine
district, so other worlds draw nothing new. Overlays add no saved state: player
edits to them are ordinary block edits.

### Underground and rooftop routes

Generator 13 plans layout 10: layout 9's districts plus two pure route layers.
`TransitNetwork` puts tunnels only under boundary avenues, where nothing else is
built. Each segment of a candidate avenue is a function of its name (axis, line,
index), the seed and its two bordering plans, so both regions stamp mirror
halves in any chunk order. Every region owns one segment, and a segment exists
only with its `TransitStation`. A station is also a `BuildingPlan` of family
`TRANSIT`, so containers, room location, encounter anchors and the portal graph
handle it like a building; `TransitValidator` proves it before it is sited.
`RouteCatalogue` derives a region's service passages, hidden cellar, fire
escapes and roof connectors from what every plan of a parcel shares, and
`RouteTemplates` (building template 7) applies them to the district catalogue's
plans along with roof rails, stair bulkheads and the stair-well rail.
`UrbanRouteStamper` writes tunnels and the outside parts of routes after streets,
buildings and overlays, only where every building a route joins declared its end.
`RouteProof` is the bounded walking proof for anything that is not one building.
A rope ladder there is climbed up only, as the player's controller allows.

`UrbanPlaces` names the route a body stands in for the HUD, shelter and sound;
`UrbanDiscovery` records stations and cellars only when the player observes them
and is saved as `urban.discovery`. The loose panel over a hidden cellar is a
door kind (`Fixtures.Kind.HIDDEN_PANEL`) that AI never routes through.
Encounter catalogue 4 adds station slots inside each district's budget.

### Alien ecology and city wildlife

Generator 14 plans layout 11: layout 10 plus `EcologySites`, a pure per-region
plan of growth sites (quarantine courtyards and fenced squares only) and
wildlife habitats (parks, residential courtyards, canal basins).
`UrbanEcologyStamper` writes each site's `GROWTH_NODE` and `VEIL_MEMBRANE` carpet
after the routes; wildlife stamps nothing. A `world.urban.Habitat` is where a
city creature may go: a territory circle round a node, or a rectangle with a
height band for birds. `CreatureAI` clamps every movement target of a homed
creature into it. `VeilmawAI` is the alien's territorial brain (senses inside
its territory, telegraph, one lunge, lure and smother, and withering when its
node is gone); `BreacherAI` is the blind infected variant that hammers wooden
doors, planning through them with `Pathfinder.findBreach` in a separate
`NavigationCache` key space. Encounter catalogue 5 tags each anchor with an
`UrbanSpecies` (stable string id) and its habitat: ecology slots borrow parcel
slot 6 of their superblock, and `EncounterAnchors.forEcology` adds roof flocks
over tall walk-ups. `UrbanEncounterDirector` admits creatures beside infected
under separate caps (4 alien organisms, 12 wildlife) and tombstones a Veilmaw
whose node is gone. `urban.population` codec 2 stores the species and binds a
row to the NPC or creature list.

### Survivor camps and hidden refuges

Generator 15 plans layout 12: layout 11 plus `SurvivorSites`, a pure per-region
plan of at most one camp (the reclaimed ground floor of a held walk-up, outside the
quarantine) and one hidden refuge (the region's sealed cellar). Each site lists its
stable residents (`UrbanIds.resident`), their roles, beds and day cells, the way
in, the safe cell, the lookout's post, its stash containers and, for a camp, spare
beds upstairs. Sites stamp no block; `SurvivorStash` overrides the loot roll of a
stash container so its first touch holds the people's rations and water.

At runtime `SettlementManager.urbanSites` (`UrbanSiteDirector`) owns them inside the
settlement slow tick. A site is registered as an `UrbanSite`, a `Settlement`
subclass keyed by its building id in `world.settlements`, the first time the player
comes near, steps onto its storeys or finds its cellar; every legacy settlement
path (planner, codecs, slow tick, trade and gift hooks, camp illness, `nearest()`)
skips these records, while attack, kill and reputation hooks route to the director.
The director activates and retires bodies in three dimensions from the home anchor,
records discovery only from sight, entry or the `urban.discovery` ledger, and steps
every met site's needs hour by hour in lockstep (`UrbanSiteNeeds`), active or
dormant, from the real stash containers. `UrbanSiteRelations` turns deeds into
trust, fear and alarm; `UrbanSiteDialogue` is the conversation's command seam
(fixed lines, trade from finite stores, the medic, threats, word of a camp);
`UrbanSiteRelocation` moves hidden civilians to a camp's spare beds while unseen.
`UrbanResidentAI` keeps the people to planned cells over bounded indoor routes, so
they never walk into walls or leave hiding for a street- or roof-height target; the
conversation and melee need a clear line to them. In the medium tick the director
also lets hiding civilians be heard near their panel (`SurvivorSounds`), a
presentation clue that discovers nothing. `urban.sites` v1 holds every registered
site and its people (D-150 in the urban decisions); it is required for generator
15 and read after the population, binding active bodies by core NPC index.

### Guarded strongholds and raider yards

Generator 16 plans layout 13: layout 12 plus guarded compounds. `CompoundClaims`, a
pure function of the seed and the district plan, claims a civic courtyard block for
a stronghold and an industrial depot row for a raider yard before any building is
planned, and `RouteCatalogue.of(seed, plan, claims)` plans the claimed parcels held.
`CompoundSites` then derives each compound from the frozen building plans (the
gate, barricade lines, porch and yard, posts, patrol, stash, beds and people), and
`UrbanCompoundStamper` builds its barricades from existing blocks; the gate's keyed
lock is a plan default read through `World.fixtureLockAt`. `SurvivorSites.sites()`
lists compounds beside camps and refuges, so the same director registers,
activates and steps them as `UrbanSite` records of kind `STRONGHOLD` or `RAIDERS`,
on the urban sides `UrbanFactions.WARDENS` and `WRECKERS`, which never enter the
base faction map.

`CompoundResidentAI` gives a compound's people their duty: posts and a patrol by
day, a night watch, travel that passes the gate only through `CompoundGate` (which
swings, shuts and locks it), sight through `InfectedAI.seesPlayer` and occluded
hearing, and fights with infected inside a leash, and with the player for raiders or
a stronghold that has turned on them. `CompoundAccess` holds the rules and what
follows from what guards perceive: visitor's passes, warnings, intrusion and
wanted status, everyday sounds, theft by evidence (stores taken unseen are found
short at the stronghold's hourly step), bounded responses, raider hunts with a
cooldown, capture and a stronghold's single reward. The director's fast tick runs
the gate and these timers only while a compound has bodies or a held gate, and
clears the transient ones on dormancy. `urban.compounds` v1, required for generator
16 and read after `urban.sites`, holds what each compound remembers.

Scripted urban QA routes extend `UrbanRouteScene`: a task queue played through the
production input seam, mouse-look turns, bounded walking that opens doors with F,
threat handling, forcing and captures. `urban_slice`, `urban_catalogue`,
`urban_districts`, `urban_underground`, `urban_ecology`, `urban_survivors`,
`urban_strongholds`, `urban_refuge`, `urban_utilities`, `urban_missions` and `urban_onboarding` use it; the last six extend
`UrbanPeopleScene`, which adds the staged clock and poses, walking up to someone and
talking, conversation choices through the screen's command, a trade and F5 and F9.

### Refuges, salvage and fortification

Generator 17 plans layout 14: layout 13 exactly, plus the burnt-out cars of the local
streets (`VehicleSites`, a pure function of the seed, layout and region with at most
six a region, stamped as `CAR_WRECK` cells by `UrbanVehicleStamper` after the
compounds) and the rules below, each gated on `UrbanWorldState.hasRefuges()`, so
earlier layouts are unchanged.

`SalvageTable` says what can be taken apart: per block kind, and per part of a wreck,
the seconds of work, the tool that halves them, whether that tool is required
(metal), and what a sound piece gives. Wear (sound, worn, wrecked) is a pure function
of the building's variant and the cell, so it is never saved. `Dismantling`, owned by
`PlayerBlockActions`, runs the hold: crouched for anything a standing hold carries
away whole, any hold for pieces with no item form, each blow a `WorldNoise` event.
Its completion is the single transfer: what the piece gives and whatever a container
still holds must fit before anything moves; a container's contents leave
`World.crateContents` once and its `UrbanContainers` identity is retired; the cell
becomes a recorded edit, so depletion persists with the other block edits.
Survivors' pieces raise the existing wrecked-stores consequence. A Creative break
removes the piece and gives no materials.

`Fortification` owns the use button on openings: a plank or sheet-metal barricade
(`BARRICADE`, `BARRICADE_METAL`, solid and opaque pane blocks) goes into a broken
frame or the cell before the aimed face; a door brace bars a closed door from the
player's side and reinforces the bar; planks and scrap repair; a salvaged door or
window refills a broken frame. `UrbanFortifications`, on `UrbanWorldState`, keeps
only damaged barricade cells (at most 2,048; a blow on one more breaks it) and braces
(at most 256), forgetting a cell that is no longer a barricade (`World.setBlock`
tells it) or a brace whose door is no longer barred. A Breacher plans through plank
barricades with `Pathfinder.breachAccess` (step cost 10, beside 8 for a locked
wooden door) and hammers them through `Fortification.breacherBlow` (15 a blow, a
brace before its door); every other body treats a barricade as a wall.
`urban.fortifications` v1, required for generator 17, restores the damage once the
block edits are in (`UrbanFortificationsSection.finishLoad`).

`UrbanRefuges`, on `UrbanWorldState`, holds at most three claims, each a storey's
floor id with its sleeping cell and the minute it was claimed, and which is home.
Claims are plan ids, never a transient object, so edits inside one leave it whole.
`RefugeSystem`, a `Game` field reset by `WorldBootstrap`, applies the rules: claiming
at a bed (`WorldInteractions.rest`), the refusals (survivors' rooms through
`SurvivorSites` and `UrbanSiteDirector.abandoned`, something hostile on the storey,
roofs, stations and unloaded parcels), giving up, the bed prompt, home, the respawn
`UrbanBootstrap` asks for before the starter anchor, and the starter claim of a fresh
world. `UrbanEncounterDirector.safeSpawn` lets nothing new appear inside a claimed
storey; `UrbanPlaces` names it "Your refuge", the map marks the claims, and
`CrateTransactionSystem` labels storage there that nobody else owns.
`RefugeExposure` reads a claim's ways in (the building's outer and roof doors and the
claimed storey's windows) and its light from the live world. `SleepSystem` asks
`RefugeSystem.settleForNight` once as sleep begins, which may send the nearest real
infected to an open way and sets the minute it wakes the sleeper, and
`RefugeSystem.keepsOut` lets a refuge shut tight keep out a body beyond its walls.
`urban.refuges` v2 holds the claims and home for generators 17–19; generators 9–16
keep version 1.

### Refuge utilities

Generator 18 plans layout 15: layout 14 exactly, every block, plan and id, with the
rules below gated on `UrbanWorldState.hasUtilities()` (`hasRefuges()` and every
earlier predicate also hold for it). `UrbanUtilities`, on `UrbanWorldState`, is the
world's side: pure plan queries and the persistent ledger.

- **Taps.** `UrbanUtilities.tapBuilding` makes a planned basin, standing where the plan
  put it with no recorded edit, a tap on its building's pipes; `capacity` derives the
  building's stale litres from its planned basins and variant. The ledger keeps the
  litres drawn per building id (at most 1,024). `UtilitySystem.tap`, reached from the
  rain-collector branch of `WorldInteractions`, fills an empty waterskin with untreated
  water or drinks it through `WorldInteractions.drinkUntreated`, the open-water rule
  and its seeded stream. `WaterSystem` is untouched: no voxel water moves.
- **Devices and circuits.** A `Device` (generator, work lamp or purifier, with a
  stable kind code) is keyed by its cell and remembers its building, read from the
  plan over its column. `placementRefusal` keeps devices inside buildings, one
  generator to a building and 64 in the world; `PlayerBlockActions` asks it before a
  placement and registers the device after. `settle` recomputes every circuit, the
  four nearest consumers of a running generator's building within 16 blocks, after
  any change of device, switch, fuel or wear, and refreshes the light of each lamp
  whose state changed. `World.setBlock` drops a device whenever its block is
  replaced, by any path, and settles at once.
- **Light.** `World.emitterOn` answers for every light block: a lantern by its fuel,
  a work lamp by `UrbanUtilities.lampLit`, anything else always. `Chunk.rebuildLights`
  and `Chunk.updateLightAt` ask it, so a lamp is in its chunk's light list exactly
  while it is switched on and fed, and `ChunkMesher` draws its dome lit or dark.
- **The game side.** `UtilitySystem`, owned by `RefugeSystem` and reset with it, holds
  the actions and prompts: F switches a device (`WorldInteractions.interactWithBlockAt`),
  the use button pours fuel, rebuilds a generator with spare parts and a pry tool,
  pours and draws water and packs a purifier's filter (`WorldInteractions.rightClick`
  asks it first), and `InteractPromptBuilder` reads its lines. Its medium tick, one
  line in `Game.mediumTick`, burns each running generator's fuel by time and load,
  wears it, emits its player-attributed `WorldNoise` and its `UtilitySounds` cues,
  runs the purifiers it feeds for exactly the seconds it ran (the pump draws the
  building's tap water into the untreated tank, the filter treats it into the clean
  tank), and stops it when its tank is dry or it seizes. It runs only while the world
  is simulated, and a generator more than 128 blocks from the player or in an
  unloaded column is dormant: nothing about its circuit changes.
- **Salvage.** `SalvageTable` gives the tank cell of a car that did not burn a fuel
  can, and takes devices apart for most of their parts and a generator's whole cans;
  device blocks have no drop, so nothing carries one away whole.

`urban.utilities` v1, required for generators 18 and 19 and rejected elsewhere (a
radio row only for generator 19), holds the tap ledger and the devices in placement order, validated against the block edits and the
plans once every other section is in (`UrbanUtilitiesSection.finishLoad` rejects a
generator-18 save without it). The noise and sound timers and the pump's part-litre
are transient.

### Missions and the communications objective

Generator 19 plans layout 16: layout 15 plus one relay mast, with the rules below gated
on `UrbanWorldState.hasMissions()` (`hasUtilities()` and every earlier predicate also
hold for it). The world's side is pure plans and one ledger:

- **The relay and the parts.** `RelaySites` plans the one relay from the seed and the
  frozen plans (a roof cell, a stand cell beside it and a three-cell guard-rail mast),
  cached once per world on `UrbanWorldState.relay()`; its stamp writes only inside
  the chunk being generated. `CommsComponents` names the containers whose first roll
  holds a part, and `UrbanContainers.touch` adds the part to that roll only.
- **The ledger.** `UrbanMissions`, on `UrbanWorldState`, keeps the objective's facts
  (`Comms`: rumoured, found, parts recovered and fitted, codes, radio built, seconds
  broadcast, complete) and the mission records, at most 256, four in hand. The stage a
  player sees is derived from the facts, never stored.

The game side reaches the ledger through the seams the screens and tests share:

- **`UrbanComms`** (settlement) answers "[9] Ask about the old radio relay", shares
  the codes, counts parts taken into the pack, finds the relay in the refuge
  system's medium tick once the survivor's own body stands beside it in a loaded
  column, and gives the HUD line and the map target of the objective's next step.
- **`UrbanMissionBoard`** (settlement) answers "[0]": offers, accepts, reports and the
  Wardens' price. Evidence arrives through hooks on the deed itself:
  `CrateTransactionSystem` after a storage-screen transfer, `UrbanSiteRelations.aided`,
  `UrbanSiteRelocation.guide`, `UtilitySystem` as a purifier treats water, and the
  survivor's own position on the relay roof in its medium tick, which also checks
  failures. Its journal line and target feed `Hud` and `QuestObjectiveView` before
  the objective's.
- **`CommsObjective`**, owned by `RefugeSystem` beside `UtilitySystem` and reset with
  it, fits parts at the relay (`WorldInteractions.rightClick` asks it first), gives the
  relay's and the radio's prompts, and in the refuge system's medium tick, the one
  line in `Game.mediumTick`, counts the broadcast, emits the radio's player-attributed
  `WorldNoise` and completes the objective once, which sets `AppState.VICTORY`. The
  radio itself is the fourth `UrbanUtilities` device kind, so power, load and wiring
  are `UtilitySystem`'s. On the ending card `Game` returns to play on Enter, and in a
  city on Escape too; the legacy beacon's card keeps Escape for the title.

`urban.missions` v1, required for generator 19 and rejected elsewhere, holds the facts
and the records, validated against the plans and each other
(`UrbanMissionsSection.finishLoad` rejects a generator-19 save without it). The noise
timer and the once-per-tick broadcast guard are transient.

### The city map, place lines and onboarding

Urban package 21 adds no system to `Game`. `Player.trackPlace`, when the feet cell
changes, also calls `UrbanVisits.observe`, which records in a generator-19 city the
district underfoot and a building only from inside one of its rooms, plus a transient
memory of the rooms entered on the last 64 floors (`urban.visits` v1 keeps the first
two, never the rooms). `MapScreen.update` hands a generator-19 world to `CityMapView`,
which draws what `CityMap` and `FloorSketch` answer: pure reads of plans, loaded
chunks, the visits and discovery ledgers, registered sites, refuge claims and the
mission ledger, never creating a chunk, rolling a container or registering a site. The
HUD's field panel reads `CityMap.hereLine`; `CrateTransactionSystem.containerPrompt`
gives the container line the prompt builder shows. The map, storage, conversation and
inventory screens keep the world running; only the pause menu and its screens pause it.

### The ruined city's look and sound

Urban package 22 adds no system to `Game`; it changes what existing owners derive in
a generated city (`UrbanWorldState.hasGeneratedBuildings`), and nothing it adds is
saved. `World.blockLight` traces a bounded ray (`UrbanIndoorQueries.lightReaches`, at
most 48 cells) for a light that would brighten a cell, so walls, floors and closed
doors stop lamp and fire light; the mesher and every AI light read share it.
`ChunkMesher` grades city surfaces by district and lays at most 384 wall decals per
chunk, as thin quads with the face's own light, from the pure rules of
`world.urban.UrbanDressing` and the site-plan marks of `world.urban.UrbanTraces`;
`InteractPromptBuilder` reads a mark's words. `Environment.city` tells the terrain
shader to wet and frost only open sky and to bounce a little daylight into rooms, and
makes city water murky. `Player.toxicFogHarmfulHere` decides both the toxic-fog roll
and whether the screen draws the fog as harmful.

`AudioSceneState.urbanZone` classifies a city listener into the appended room, hall,
basement and tunnel zones, and `weatherReach` scales rain and wind by where they
stand. `AmbienceSystem` owns `CitySoundscape`, which runs on the emitter pass with
its own presentation generator (reseeded with the world) and plays
`engine.CitySounds` through `AudioManager.playCity`: distant and indoor scenery by
district, and the tells of resting infected, folded Veilmaws, people turning hostile
and fed lamps. It examines at most 64 people and 64 creatures, follows 8 of each kind
of tell and starts 2 sounds per pass, and never emits `WorldNoise`. Footsteps go
through `engine.FootstepSurface`, whose loudness follows what the AI hears of a step.
`VEYLON_SCENE=urban_atmosphere` holds the camera at fixed places for before-and-after
captures and prints what a listener there is given.

### The survival economy

Urban package 23 adds no system to `Game`. Generator 20 plans layout 17, layout 16's
city exactly, and the rules below gate on `UrbanWorldState.hasEconomy()` (every
earlier predicate also holds for it). A fresh world resolves its generator's content
revision (`UrbanRevision.contentFor`: 4 for generator 20, 3 for every earlier urban
generator), and `UrbanWorldState.resolveContentRevision` refuses content 4 outside
layout 17 and any other revision inside it, so a load never rolls a world against
tables it was not created with. `UrbanContainers.firstRoll` is the first-touch roll
that `touch` commits (the content revision's starter reservation, then a site's
stash, then the table, then the relay's part) and that censuses read without
touching the ledger; `LootTables` keeps content 4's tables beside the frozen ones,
with a district's bias as a percentage of each chance, and `UrbanStarterSite.economySupplies`
holds content 4's starter reservations. In layout 17 `SalvageTable.yields` caps a piece
the player set down by the recipe that makes it (`recipeFor`), and `Dismantling`
takes a worn or wrecked piece apart under any hold. `UrbanSiteDialogue.keepsBack` is a
trader's reserve for the people its stash feeds; `Npc.painGuard`, run down by
`InfectedAI.ready`, is an infected body's transient guard after a flinch; and
`EncounterAnchors` freezes catalogue 6, catalogue 5 with one starter apartment's
slots. `VEYLON_SCENE=urban_economy` plays two and a half real-time days.

### Doors, windows and urban fixtures

Urban package 04 appended 21 block ids (`ASPHALT` … `DEBRIS`): restrained urban
materials whose weathered and damaged looks are position-hash material variants,
not more ids; doors, windows and stairs; furniture. The rules live in
`world.Fixtures` (families and openings), `world.FixtureStates` (state no id
expresses), `world.FixtureGeometry` (thin boxes) and `FixtureActions` (what the
player and residents do). They apply in every world, legacy included, though only
Creative or salvage can bring one into a legacy world.

- **Families.** A door or window kind has a closed, an open and a broken id. Closed
  retains a solid full-cell collider (glass is solid but not opaque), open is a non-solid leaf
  against a jamb, broken is a non-solid frame in the wall plane. Open paired doors
  use opposite outer jambs: a structural neighbour on the high side, with none on
  the low side, puts that leaf on the high jamb. This also preserves its side when
  its partner is broken or removed. Doors supported on both sides and casements
  retain the low-side leaf. A closed door renders as a 0.125-block panel centred
  in the wall plane, with visible jamb faces and matching targeting. Its existing
  collision and world-light blocking remain unchanged. Collision,
  pathing and meshing read the ids, so they agree; the targeting raycast and the
  mesher share `FixtureGeometry`, so the crosshair passes an open doorway and hits
  only the drawn leaf. An opening is a vertical stack of one kind, at most three
  cells; a door acts as a whole, a window pane shatters alone.
  Placement requires both door cells inside the world in a loaded column, with
  replaceable space and no body occupying either cell.
- **State and persistence.** Toggling swaps the id with `record = false` and writes
  the state to `World.fixtures`, keyed by block position: open, a keyed lock (both
  sides) or a bar (one side, with its axis and side). `world.fixtures` v1 saves it,
  only when non-empty, sorted, capped at `FixtureStates.MAX_ENTRIES` on both write
  and read. On load an entry turns the edited block into its variant; an entry whose
  chunk does not exist yet waits for `World.getOrCreateChunk`, which applies saved
  state after generation and drops stale entries. Forcing, shattering and
  dismantling are ordinary recorded edits, and `World.setBlock` drops the state of a
  cell that stops being an operable fixture. An absent section retains generated
  or edited defaults: generator 7 still derives occupied-door locks from its plan,
  while an explicit saved unlock overrides them. Earlier generators default to
  unlocked. An older build skips the section and loads doors and windows closed;
  before package 04 a build reads their ids as air. A malformed section fails the
  whole load.
- **Player.** F on an aimed door or window comes before nearby people
  (`WorldInteractions.interact`, `InteractPromptBuilder`, one decision). Crouch and F
  bars a door or latches a window from the player's side; in Creative it toggles a
  keyed lock. A Survival break forces a closed door into a broken doorway, shatters a
  pane (a bare hand is cut through `Player.hurtPhysical`), or dismantles an open
  fixture into its one item; Creative removes the block. Opening, closing, failed
  entry, forcing and shattering emit `WorldNoise` through its Creative gate.
- **Residents.** `Pathfinder` treats a closed, unlocked door as passable at the gate's
  cost and a locked or barred one as a wall; residents open doors on their path and
  `FixtureActions.tickResidentDoors` (settlement fast tick) shuts them behind,
  never on a body. Those timers are transient and bounded (`MAX_TRANSIT`); the save
  writes such a door shut. In urban worlds `Npc` steps up one full block, so
  residents climb full-block stairs by walking. `VoxelPhysics` first tries a
  half-height rise when it fits, retaining the full-block allowance.

- **Shaped stairs** (`StairGeometry`): four appended ids 78–81 encode the high
  end, with two half-height treads. Meshing, targeting, ordinary body collision,
  ragdoll contacts and projectile samples use the same bounds. The integer
  pathfinder conservatively routes above their high end; reversible generated
  flights are tested with both pathfinding and actual player movement in both
  modes and every frontage. Placement chooses direction from camera yaw. All
  directions share the existing stair item, icon, textures and one-item drop;
  direction persists through ordinary changed-block bytes. The catalogue uses
  82 of 128 non-negative byte ids. Historical `STAIRS` remains a full cube.
  Voxel lighting treats shaped stairs as non-opaque to retain adjacent faces;
  it does not resolve light through individual tread subvolumes. Older binaries
  were not executed: those supporting at most generator 7 reject generator 8;
  downgrading an older-generator world containing newly placed stair ids is not
  supported. The v3 base and extension layouts are unchanged.
- **Containers and salvage.** Generated buildings (generators 7/8) use the sparse
  per-world `UrbanContainers` ledger described below. A hand break first commits
  any unrolled contents, refuses if contents plus salvage would not fit, then
  transfers them once and retires the identity as MOVED. Salvage is at most the
  one item that places the block. Blast/fire retire it as DESTROYED and destroy
  its contents. `World.setBlock` reserves ledger capacity before mutation and
  creates an empty player identity for replacement furniture. Legacy worlds
  retain their crate rules. Open screens revalidate the live fixture, ledger
  entry and authoritative inventory before transfers; destruction closes them.

### Contextual storage and searches

`world.urban.LootTables` maps nine furniture functions to twelve contextual
tables, with six district biases. Pure rolls use seed, saved content revision,
stable fixture id and distinct presence/count/condition salts. Kitchens and
pantries supply food/water; bathrooms and civic treatment cabinets supply
treatment; wardrobes supply clothing; utility/stockroom storage supplies tools
and repair materials. Scarce firearms/ammunition occur only in occupied secured
utility/stockroom tables. Picked-clean sites roll empty. Initial quantities are
not the package-23 economy tuning or package-08 starter guarantees.

Lazy generation commits a record on first search or consequential mutation.
`UrbanContainers` holds at most 32,768 entries including permanent tombstones;
there is no eviction that could refill a fixture. Generated ids are packed
fixture ids; player storage uses a disjoint negative monotonic sequence. States
are UNSEARCHED, SEARCHING, SEARCHED, EMPTY, MOVED and DESTROYED with explicit
stable codes. Ownership is ABANDONED, SURVIVOR (stable building id) or PLAYER.
Only `World.crateContents` owns inventories: the ledger stores metadata, never
another item copy. Untouched furniture allocates neither record nor inventory;
content-2 empty records allocate no inventory. At capacity, new touches and
mutations needing a record fail without changing blocks or moving supplies.

`CrateTransactionSystem` advances 1.5/2.5/3.5-second searches on normal fast ticks
after needs, entities and settlements. Rummaging emits actual bounded
`WorldNoise` events and audio. Closing, damage, death, range over five blocks,
unloaded columns or fixture replacement interrupt the screen. Damage means a
discrete blow, fall, burn or blast counted by `Player.harmEvents`; cold, bleeding,
hunger and other over-time drains do not interrupt a search. Elapsed search
progress remains durable. Opening a screen never pauses simulation. Left click
transfers a stack, right click one item. Full destinations preserve the source;
partial transfers remove only delivered quantities. Stack merging preserves
durability/charge and weighted food freshness. Existing spoilage visits only
materialized core inventories; emptied content-2 inventories are removed.

Taking from abandoned/player storage bypasses legacy proximity theft. Taking
survivor storage applies the existing Free Settlers faction reputation penalty
(3 + count, capped at 12) once per logical theft event, with callback deduplication.
Player hand/blast/fire destruction costs 15 reputation; natural fire does not.
Legacy camp trust is separate. Guard responses and urban residents remain 16/17.

`urban.containers` v1 has a 20-byte header (version, content revision, next player
id, count), then 36-byte rows (id, cell, block, owner code/building, state code,
search progress, authoritative-inventory presence). Its maximum payload is
1,179,668 bytes, below the 16 MiB section limit; at most 32,768 core inventories
fit below the frozen core's 100,000-entry reader cap. Inventory bytes occur only
in that existing core map. The generated-urban core reader additionally requires
12 slots, unique cells and valid item counts, ordinals, condition, freshness and
charge. The section checks exact length, identities, owners, progress, active
cells and inventory references. Known unsupported versions fail; unknown ids
retain the existing skip policy.

Load applies this section after `urban.meta`, regardless of section order.
Content 2 requires it, including a zero-entry ledger: absence cannot refill loot.
For authentic pre-loot content-1 saves, absence adopts core inventories (including
empties) and tombstones edited generated reservations. Touched content-1 empties
retain a core empty marker so dropping the optional section cannot refill them;
untouched decorative furniture still allocates nothing. New/load replaces the
world-owned ledger; open targets and noise timers reset, saved search progress
does not. Save preparation binds/compacts the core map before the existing
sibling-temp, flush, force and atomic replacement path. Invalid loads preserve
the live session; failed writes preserve the previous save. Older binaries were
not executed, and no downgrade guarantee is made for new content-2 saves.

### Urban starter progression

Generator 9 reserves the origin courtyard neighbourhood through pure
`CityPlanner.planStarter` constraints. `UrbanStarterSite` supplies the workbench,
light and courtyard fixtures, and content-3 ground-floor container reservations.
Ordinary search, ownership and depletion remain authoritative; the kit and
initial 3-litre collector / 240-second fire are granted only by fresh bootstrap.
There is no wilderness camp, NPC population or protected radius. Shelter uses
the existing room/portal queries; a courtyard fire does not count as indoor smoke.
Sleep checks loaded support and any hostile body within 12 blocks (in a layout-14
refuge shut tight, not one beyond its walls) as well as the existing conditions and
predator gate.

`CraftingSystem.recipesFor` selects fifteen repair/reuse recipes for the starter
generators, retaining the original list for older worlds; layout 11 adds the ecology
recipes, and layout 14 (`REFUGE_RECIPES`) drops the carried-table plank recipe and
adds the two barricades and the door brace. Recovered wood,
metal and textiles cover the first reinforced spear, stations, treatment,
containers, light, bedroll and tool/weapon repair. Crafting, cooking, boiling,
refilling and Survival drinking check the complete output transaction before
consuming ingredients or water. Failed capacity leaves the source intact.

`urban.refuges` v1 is required for generators 9–16: version + strict presence byte,
then optional generated refuge id and integer anchor (5 or 25 bytes). It validates
the pure ground-floor plan. Load replaces it without restocking, and
missing/corrupt sections reject before publication. Respawn checks actual loaded
colliders/support at y=41 in at most 4,225 cells within 32 blocks of the saved
anchor, refusing if none is safe. It retains the existing carried-inventory death
policy. Generators 17 and 18 write version 2, which adds the claims and home of
milestone 18, and wakes the survivor at home first (see "Refuges, salvage and
fortification"); utilities are under "Refuge utilities".

### Urban infected encounters

`EntityManager.encounters` owns `UrbanEncounterDirector`, reset by bootstrap.
Generator 10 enables frozen `EncounterAnchors`: a selected residential building
reserves street and ground-floor room slots, excluding occupied buildings.
Packed kind 8 identifies the room/slot; it is unrelated to settlement residency.
`NpcArchetype.VEILBOUND` is an append-only humanoid appearance/combat profile
with its own `InfectedAI`, no faction/jobs/feeding/harvest, and shared weapon,
hit-zone, combustion, ragdoll and fragment handling. `Player.hurtPhysical` and
perception controls remain authoritative. Audio uses independent procedural cues.

Admission requires loaded whole footprints, supported clear bodies, breathing
room 16, refuge sanctuary 24 and no direct camera view or occupied property.
Caps: 12 active, two slots/building, two admissions/slow tick, 16 parcel checks
and 16 dormant snapshot checks/tick, 32,768 durable records. Shared NPC caps and
counterattack reservation apply. Activation/deactivation distances are 96/130.
An unloaded footprint freezes physics until dormancy, preserving its floor.
Same-floor A* admits two 400-expansion queries/fast tick and refuses unsafe
strides or closed doors. Multi-floor portals and sound attenuation remain 10.

`urban.population` v1/catalogue 1 is required even when empty for generator 10.
Its 12-byte header and 47-byte rows preserve stable species/id, health, position,
yaw, torso wounds and an active frozen-core NPC index (−1 otherwise). Health zero
is a permanent death; dormancy pauses without healing, migration or replacement.
Saturation stops new encounters without evicting tombstones. A save commits
pending infected deaths before settling remains. Read waits until `urban.meta`,
validates planned anchors, duplicates, bounds and one-to-one core bindings, and
rejects missing/unknown/corrupt required state before publishing. Earlier worlds
remain encounter-free. Older builds reject generator 10 or the unknown body id.
Generator 14 writes codec 2: each row names its species' stable id and binds to
the core NPC list (humanoids) or the creature list (the Veilmaw and wildlife); a
creature's yaw lives only in the ledger. Codec 1 is unchanged for generators
10–13, and each generator rejects the other codec.

Transient state is deliberately **not** serialized: an in-flight reload is
cancelled on load, and bow draw resets. Combat and fire add more of it. An
ordinary human NPC's torso wound count and the shot id that last wounded it, the blast
record a lethal explosion leaves on the body it killed, a living body's
fire (`Entity.combustion`) and a burning person's or animal's panic
(`Npc.panic`, `Creature.panic`) live on the entity and die with it — a person who
leaves the world, through a save, a load or a settlement going dormant, comes
back unwounded, not burning and calm at their stored health. Infected retain
their torso wounds through the population ledger; the last shot id remains transient.
Saving leaves a body
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

### Indoor navigation and evidence

`World.navigation` owns a transient `NavigationCache` and `PathBudget`.
`EntityManager.fastTick` resets the shared allowance once before any population
runs: four A* queries and 2,800 expansions, at most 700 per query. `Pathfinder`
enforces the allowance even for direct callers during a tick; cached hits use a
query and no expansions. Standalone diagnostics have the same local ceiling.
`PortalGraph` derives entry/floor/room waypoints from immutable building plans;
long loaded exterior legs advance through supported local anchors. Local A*
still validates every transition through the shared swept-body clearance proof.
Exhausted local searches may return a safe connected prefix, cached only to its
actual endpoint. This preserves progress through complicated legacy settlements
without expanding the allowance or pretending that the final goal was reached.

`IndoorNavigation` is the common resident/infected follower. It opens unlocked
doors/gates through existing actions, sweeps the live body, checks column/support
limits and waits when blocked or deferred. Replans are staggered by 0.8–1.5 s.
Up to 64 routes of 512 cells are retained. `World.setBlock` and fixture lock
notifications invalidate intersecting route envelopes; ordinary unlocked door
swings preserve equivalent routes. No path or search state is serialized.
New/load creates a fresh world/cache, and departures clear live pursuit references.

WorldNoise indoor listeners consume event identities once and use the actual
event position. Sixteen hearing queries / 4,096 ray samples are shared each fast
tick; closed doors, glass and other solids add distance losses of 6, 4 and 8 per
cell. Unloaded/exhausted rays fail closed. Perception decisions are staggered.
Veilbound retain sound for fourteen seconds and sight for eighteen, search the
remembered place and clear paths on expiry or Creative invisibility. Resident
melee rechecks loaded occlusion/elevation instead of tracking an unseen live
player coordinate. `RuntimeBudgetSnapshot` exposes cache/work/failure occupancy.

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
| a creature | `Creature.CreatureType` | `CreatureModels`, `BodySkeleton`, a `BodyFamily` and its `FragmentAnatomy` table (append only, pinned in `SerializedEnumOrderTest`), spawn rules in `EntityManager`, `CreatureAI` if behaviour differs (after `FirePanic`) |
| an NPC role | `NpcArchetype` | `SettledNpcAI` job schedule, `NpcModels` |
| a biome | `Biome` | `WorldGenerator` selection, surface/subsurface blocks, spawn tables |
| a settlement type | `SettlementType` | `SettlementPlanner` placement, `SettlementBuilder` layout |
| an affliction | `Affliction` | `PlayerConstants` damage rate, `Player.tickAfflictions`, `PlayerTreatmentSystem` cure |
| a weather state | `WeatherSystem.Weather` | `SkyRenderer`, `Environment`, `TemperatureSystem` |
| a world event | `EventType`, `EventConstants`, `EventSystem.DEFINITIONS` | JavaDoc rung table, modifier query, `EventRollLadderTest` |
| a simulation system | new class + cadence interface in `simulation/` | matching `Game` tick, `newWorld` reset, `SimulationSystemContractTest`, save/load if it holds state |
| a flame that sets bodies alight | an `exposeContacts` adapter in the system that owns the flame, called from `CombustionSystem.sampleFlames`; a `CombustionSource` kind if none fits | `FlameSourceIgnitionTest`, `CombustionAllocationTest`, the contract's §4.1 source table |

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
| `PlayerLifeCycle` | death transition and respawn; Game keeps its existing command delegates |
| `CrateTransactionSystem` | simulation-timed searches, crate transfers and theft attribution |
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

## Lethal combat, body fragments and body fire (0.8.0, then every living body)

0.8.0 introduced hit zones, lethal blasts that tore people apart, and the molotov.
The all-living combat and fire work that followed (not yet in a release) blows
every living body apart — each species at its own joints, every kind of person
and a Survival player — and gives every living body a fire of its own, a panic
for people and animals, and flames that carry over to its remains. The
paragraphs below describe the code as it is now; the
[engineering record](engineering/ALL_LIVING_COMBAT_FIRE.md) summarises the work
and its validation, and the
[combat and fire contract](engineering/ALL_LIVING_COMBAT_FIRE_CONTRACT.md) holds
every rule and number.

The rules meet in `EntityManager.fastTick`, which still fires every consequence
of a death where it always did and then decides what the body becomes.

**One order in every frame.** A simulated frame (`Game.advanceWorld`) runs the
fast ticks first, each in this order: the player's needs; `CombustionSystem`
(every living body's swept box asks the flames in the world for contacts, the
strongest one is applied, water and rain are checked, the fire is lit, refreshed
or burns down, and damage goes through `Entity.hurt`); `EntityManager` (per body:
fire panic before any other decision, physics, then death routing — every
consequence of the death once, then one body: pieces if a lethal blast killed
it, else a ragdoll); settlements. Then at most one medium tick (block and liquid
fire spread, burn out, meet the rain and arm kegs; they no longer hurt anyone)
and one slow tick (rot, and departures through `EntityManager.depart`). Then the
frame's own systems: projectiles (a bottle breaking on a body lights it, a bomb
whose fuse ends explodes), ragdolls, pieces, remains' flames, keg fuses, noise
and the ambient emitters, which give off the bodies' sparks and smoke. The
player's death transition comes after the world step. So in any frame a burn
death is processed before a blast, a body the fire killed is never blown apart,
and drawing reads a finished step.

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
